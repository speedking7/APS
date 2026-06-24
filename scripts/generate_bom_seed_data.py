from __future__ import annotations

import argparse
import csv
import io
import json
import math
import subprocess
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import date
from pathlib import Path
from typing import Iterable

from openpyxl import Workbook


DEFAULT_DB = "aps_db"
DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 3307
DEFAULT_USER = "root"
DEFAULT_PASSWORD = "root"
DEFAULT_VERSION = "20260331-1"
DEFAULT_CUSTOMER = "测试客户"
DEFAULT_BRAND = "TEST-BRAND"
DEFAULT_MONTHS = 3
TARGET_TABLES = [
    "t_bom",
    "t_demand",
    "t_part_master",
    "t_equipment_catalog",
    "t_operating_days",
    "t_safety_stock",
    "t_inventory_count",
    "t_shared_mold_rule",
]


@dataclass(frozen=True)
class DbConfig:
    host: str
    port: int
    user: str
    password: str
    database: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate repeatable test master data from current BOM."
    )
    parser.add_argument("--start-month", type=int, required=True, help="YYYYMM")
    parser.add_argument("--months", type=int, default=DEFAULT_MONTHS)
    parser.add_argument("--version", default=None)
    parser.add_argument("--customer", default=DEFAULT_CUSTOMER)
    parser.add_argument("--host", default=DEFAULT_HOST)
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    parser.add_argument("--user", default=DEFAULT_USER)
    parser.add_argument("--password", default=DEFAULT_PASSWORD)
    parser.add_argument("--database", default=DEFAULT_DB)
    parser.add_argument("--output-dir", default="docs/superpowers/generated")
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--replace-existing", action="store_true")
    return parser.parse_args()


def shift_month(start_yyyymm: int, offset: int) -> int:
    year = start_yyyymm // 100
    month = start_yyyymm % 100
    month_index = (year * 12 + (month - 1)) + offset
    return (month_index // 12) * 100 + (month_index % 12) + 1


def build_months(start_month: int, count: int) -> list[int]:
    return [shift_month(start_month, offset) for offset in range(count)]


def mysql_exec(db: DbConfig, sql: str, *, capture_output: bool = True) -> subprocess.CompletedProcess[str]:
    command = [
        "wsl",
        "bash",
        "-lc",
        (
            f"mysql -N -B -u{db.user} -p{db.password} "
            f"-h {db.host} -P {db.port} -D {db.database}"
        ),
    ]
    return subprocess.run(
        command,
        input=sql,
        text=True,
        encoding="utf-8",
        errors="replace",
        capture_output=capture_output,
        check=True,
    )


def load_versions(db: DbConfig) -> list[str]:
    sql = "SELECT DISTINCT version FROM t_bom WHERE version IS NOT NULL AND version <> '' ORDER BY version;"
    result = mysql_exec(db, sql)
    return [line.strip() for line in result.stdout.splitlines() if line.strip()]


def load_bom_rows(db: DbConfig, version: str) -> list[dict[str, object]]:
    sql = f"""
SELECT parent_code, child_code, usage_qty, process, equipment,
       manufacturing_department, manufacturing_unit, mold_cavity,
       cycle_time, staff_count, takt_time, scrap_rate, version
FROM t_bom
WHERE version = '{escape_sql(version)}'
ORDER BY parent_code, child_code;
"""
    result = mysql_exec(db, sql)
    reader = csv.reader(io.StringIO(result.stdout), delimiter="\t")
    rows: list[dict[str, object]] = []
    for record in reader:
        if not record:
            continue
        rows.append(
            {
                "parent_code": record[0],
                "child_code": null_if_empty(record[1]),
                "usage_qty": float_or_none(record[2]),
                "process": null_if_empty(record[3]),
                "equipment": null_if_empty(record[4]),
                "manufacturing_department": null_if_empty(record[5]),
                "manufacturing_unit": null_if_empty(record[6]),
                "mold_cavity": int_or_none(record[7]),
                "cycle_time": float_or_none(record[8]),
                "staff_count": float_or_none(record[9]),
                "takt_time": float_or_none(record[10]),
                "scrap_rate": float_or_none(record[11]),
                "version": null_if_empty(record[12]) or version,
            }
        )
    return rows


def build_seed_bundle(
    bom_rows: list[dict[str, object]],
    months: list[int],
    version: str,
    customer: str = DEFAULT_CUSTOMER,
) -> dict[str, list[dict[str, object]]]:
    inventory_month = shift_month(months[0], -1)
    parent_codes = {str(row["parent_code"]) for row in bom_rows if row.get("parent_code")}
    child_codes_all = {
        str(row["child_code"]) for row in bom_rows if row.get("child_code")
    }
    child_codes_real = {code for code in child_codes_all if not code.endswith("-1")}

    root_codes = sorted(
        code
        for code in parent_codes
        if not code.endswith("-1") and code not in child_codes_real
    )
    semi_codes = sorted(
        code
        for code in parent_codes
        if not code.endswith("-1") and code in child_codes_all
    )
    all_codes = sorted(parent_codes | child_codes_all)

    occurrence_counter = Counter()
    equipment_keys: dict[tuple[str, str], dict[str, object]] = {}
    bom_by_parent: dict[str, list[dict[str, object]]] = defaultdict(list)
    for row in bom_rows:
        parent_code = str(row["parent_code"])
        bom_by_parent[parent_code].append(row)
        department = clean_text(row.get("manufacturing_department"))
        equipment = clean_text(row.get("equipment"))
        if department and equipment:
            occurrence_counter[(department, equipment)] += 1
            equipment_keys[(department, equipment)] = {
                "manufacturing_department": department,
                "equipment_category": infer_equipment_category(equipment, row.get("process")),
                "equipment_brand": DEFAULT_BRAND,
                "equipment_model": equipment,
            }

    demand_rows: list[dict[str, object]] = []
    for root_index, item_code in enumerate(root_codes, start=1):
        base_qty = 120 + root_index * 8
        for month_index, month in enumerate(months):
            demand_qty = base_qty + month_index * 12
            ending_inventory = round(demand_qty * 0.12, 2)
            min_safety_stock = round(demand_qty * 0.08, 2)
            net_demand = round(demand_qty - ending_inventory, 2)
            demand_rows.append(
                {
                    "customer": customer,
                    "item_code": item_code,
                    "year_month": month,
                    "demand_qty": float(demand_qty),
                    "ending_inventory": ending_inventory,
                    "min_safety_stock": min_safety_stock,
                    "net_demand": net_demand,
                    "version": version,
                }
            )

    part_rows = [
        {
            "part_no": code,
            "product_name": infer_product_name(code, root_codes, semi_codes),
            "product_no": f"P-{code}",
            "project_name": infer_project_name(code),
        }
        for code in all_codes
    ]

    equipment_rows = []
    for key, base in sorted(equipment_keys.items()):
        count = max(1, math.ceil(occurrence_counter[key] / 2))
        equipment_rows.append(
            {
                **base,
                "equipment_count": count,
            }
        )

    safety_rows: list[dict[str, object]] = []
    inventory_rows: list[dict[str, object]] = []
    for semi_index, item_code in enumerate(semi_codes, start=1):
        parent_count = len(bom_by_parent.get(item_code, []))
        daily_equivalent = round(14 + semi_index * 0.75 + parent_count, 2)
        safety_days = float(3 + (semi_index % 3))
        max_days = safety_days + 4.0
        available_qty = round(daily_equivalent * (safety_days + 1.5), 2)
        for month in months:
            safety_rows.append(
                {
                    "item_code": item_code,
                    "year_month": month,
                    "daily_equivalent": daily_equivalent,
                    "safety_days": safety_days,
                    "max_days": max_days,
                    "version": version,
                }
            )
        inventory_rows.append(
            {
                "item_code": item_code,
                "year_month": inventory_month,
                "available_qty": available_qty,
                "version": version,
            }
        )

    operating_days_rows = [
        {
            "year_month": month,
            "total_days": 26.0,
            "work_days": 21.0,
            "weekend_days": 5.0,
            "holiday_days": 0.0,
        }
        for month in months
    ]

    shared_mold_rows: list[dict[str, object]] = []
    for index in range(0, len(root_codes) - 1, 2):
        shared_mold_rows.append(
            {
                "product_a_code": root_codes[index],
                "product_b_code": root_codes[index + 1],
                "equipment_code": None,
                "mold_code": None,
                "enabled": 1,
                "remark": "自动生成测试规则",
            }
        )

    return {
        "bom_rows": bom_rows,
        "demand_rows": demand_rows,
        "part_rows": part_rows,
        "equipment_rows": equipment_rows,
        "operating_days_rows": operating_days_rows,
        "safety_rows": safety_rows,
        "inventory_rows": inventory_rows,
        "shared_mold_rows": shared_mold_rows,
        "root_codes": [{"item_code": code} for code in root_codes],
        "semi_codes": [{"item_code": code} for code in semi_codes],
    }


def infer_product_name(code: str, root_codes: Iterable[str], semi_codes: Iterable[str]) -> str:
    root_set = set(root_codes)
    semi_set = set(semi_codes)
    if code in root_set:
        return f"完成品-{code}"
    if code.endswith("-1"):
        return f"虚拟总成-{code}"
    if code in semi_set:
        return f"半成品-{code}"
    return f"零件-{code}"


def infer_project_name(code: str) -> str:
    prefix = code[:4] if len(code) >= 4 else code
    return f"项目-{prefix}"


def infer_equipment_category(equipment: str, process: object) -> str:
    process_text = clean_text(process)
    if process_text:
        return f"{process_text}设备"
    prefix = equipment.split("-")[0]
    return f"{prefix}设备"


def null_if_empty(value: str | None) -> str | None:
    if value is None:
        return None
    stripped = value.strip()
    if stripped.upper() == "NULL":
        return None
    return stripped or None


def clean_text(value: object) -> str:
    if value is None:
        return ""
    return str(value).strip()


def float_or_none(value: str | None) -> float | None:
    stripped = null_if_empty(value)
    return float(stripped) if stripped is not None else None


def int_or_none(value: str | None) -> int | None:
    stripped = null_if_empty(value)
    return int(float(stripped)) if stripped is not None else None


def escape_sql(value: object) -> str:
    return str(value).replace("\\", "\\\\").replace("'", "''")


def sql_literal(value: object) -> str:
    if value is None:
        return "NULL"
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        return str(value)
    return f"'{escape_sql(value)}'"


def build_insert_sql(table: str, columns: list[str], rows: list[dict[str, object]]) -> list[str]:
    statements: list[str] = []
    column_list = ", ".join(f"`{column}`" for column in columns)
    for row in rows:
        values = ", ".join(sql_literal(row[column]) for column in columns)
        statements.append(
            f"INSERT INTO {table} ({column_list}) VALUES ({values});"
        )
    return statements


def write_sql(
    bundle: dict[str, list[dict[str, object]]],
    output_path: Path,
    *,
    replace_existing: bool = False,
) -> None:
    sections = [
        ("t_bom", [
            "parent_code",
            "child_code",
            "usage_qty",
            "process",
            "equipment",
            "manufacturing_department",
            "manufacturing_unit",
            "mold_cavity",
            "cycle_time",
            "staff_count",
            "takt_time",
            "scrap_rate",
            "version",
        ], bundle["bom_rows"]),
        ("t_demand", ["customer", "item_code", "year_month", "demand_qty", "ending_inventory", "min_safety_stock", "net_demand", "version"], bundle["demand_rows"]),
        ("t_part_master", ["part_no", "product_name", "product_no", "project_name"], bundle["part_rows"]),
        ("t_equipment_catalog", ["manufacturing_department", "equipment_category", "equipment_brand", "equipment_model", "equipment_count"], bundle["equipment_rows"]),
        ("t_operating_days", ["year_month", "total_days", "work_days", "weekend_days", "holiday_days"], bundle["operating_days_rows"]),
        ("t_safety_stock", ["item_code", "year_month", "daily_equivalent", "safety_days", "max_days", "version"], bundle["safety_rows"]),
        ("t_inventory_count", ["item_code", "year_month", "available_qty", "version"], bundle["inventory_rows"]),
        ("t_shared_mold_rule", ["product_a_code", "product_b_code", "equipment_code", "mold_code", "enabled", "remark"], bundle["shared_mold_rows"]),
    ]

    lines = [
        f"-- Generated from BOM on {date.today().isoformat()}",
    ]
    if replace_existing:
        lines.append("SET FOREIGN_KEY_CHECKS = 0;")
        for table in TARGET_TABLES:
            lines.append(f"DELETE FROM {table};")
        lines.append("SET FOREIGN_KEY_CHECKS = 1;")
    lines.append("")

    for table, columns, rows in sections:
        lines.append(f"-- {table}")
        lines.extend(build_insert_sql(table, columns, rows))
        lines.append("")

    output_path.write_text("\n".join(lines), encoding="utf-8")


def write_workbook(bundle: dict[str, list[dict[str, object]]], output_path: Path) -> None:
    workbook = Workbook()
    workbook.remove(workbook.active)

    add_sheet(
        workbook,
        "完成品入库需求数",
        "说明：客户/存货编码/年月/需求数量/期末库存/最小安全库存/入库需求数/版本号",
        ["客户", "存货编码", "年月", "需求数量", "期末库存", "最小安全库存", "入库需求数", "版本号"],
        bundle["demand_rows"],
        ["customer", "item_code", "year_month", "demand_qty", "ending_inventory", "min_safety_stock", "net_demand", "version"],
    )
    add_sheet(
        workbook,
        "半成品期末盘点数",
        "说明：存货编码/年月（底）/可用量/版本号",
        ["存货编码", "年月（底）", "可用量", "版本号"],
        bundle["inventory_rows"],
        ["item_code", "year_month", "available_qty", "version"],
    )
    add_sheet(
        workbook,
        "半成品安全库存",
        "说明：存货编码/年月/日当量/安全天数/最大天数/版本号",
        ["存货编码", "年月", "日当量", "安全天数", "最大天数", "版本号"],
        bundle["safety_rows"],
        ["item_code", "year_month", "daily_equivalent", "safety_days", "max_days", "version"],
    )
    add_sheet(
        workbook,
        "零件主数据",
        "说明：零件编码/零件名称/零件番号/项目名称",
        ["零件编码", "零件名称", "零件番号", "项目名称"],
        bundle["part_rows"],
        ["part_no", "product_name", "product_no", "project_name"],
    )
    add_sheet(
        workbook,
        "设备清单",
        "说明：制造部门/设备大类/设备品牌/设备小类/台数",
        ["制造部门", "设备大类", "设备品牌", "设备小类", "台数"],
        bundle["equipment_rows"],
        ["manufacturing_department", "equipment_category", "equipment_brand", "equipment_model", "equipment_count"],
    )

    output_path.parent.mkdir(parents=True, exist_ok=True)
    workbook.save(output_path)


def add_sheet(
    workbook: Workbook,
    name: str,
    notice: str,
    headers: list[str],
    rows: list[dict[str, object]],
    keys: list[str],
) -> None:
    sheet = workbook.create_sheet(name)
    sheet.append([notice])
    sheet.append(headers)
    for row in rows:
        sheet.append([row.get(key) for key in keys])


def apply_sql(db: DbConfig, sql_path: Path) -> None:
    absolute = sql_path.resolve()
    absolute_str = str(absolute)
    drive, rest = absolute_str.split(":", 1)
    wsl_sql_path = f"/mnt/{drive.lower()}{rest.replace('\\', '/')}"
    command = [
        "wsl",
        "bash",
        "-lc",
        (
            f"mysql -u{db.user} -p{db.password} "
            f"-h {db.host} -P {db.port} -D {db.database} < {wsl_sql_path}"
        ),
    ]
    subprocess.run(
        command,
        text=True,
        encoding="utf-8",
        errors="replace",
        capture_output=True,
        check=True,
    )


def summarize_bundle(bundle: dict[str, list[dict[str, object]]]) -> dict[str, int]:
    return {
        "bom_rows": len(bundle["bom_rows"]),
        "demand_rows": len(bundle["demand_rows"]),
        "part_rows": len(bundle["part_rows"]),
        "equipment_rows": len(bundle["equipment_rows"]),
        "operating_days_rows": len(bundle["operating_days_rows"]),
        "safety_rows": len(bundle["safety_rows"]),
        "inventory_rows": len(bundle["inventory_rows"]),
        "shared_mold_rows": len(bundle["shared_mold_rows"]),
        "root_count": len(bundle["root_codes"]),
        "semi_count": len(bundle["semi_codes"]),
    }


def main() -> None:
    args = parse_args()
    months = build_months(args.start_month, args.months)
    db = DbConfig(
        host=args.host,
        port=args.port,
        user=args.user,
        password=args.password,
        database=args.database,
    )

    version = args.version
    if not version:
        versions = load_versions(db)
        if not versions:
            raise SystemExit("No BOM versions found in t_bom.")
        version = versions[-1]

    bom_rows = load_bom_rows(db, version)
    if not bom_rows:
        raise SystemExit(f"No BOM rows found for version {version}.")

    bundle = build_seed_bundle(bom_rows, months, version, args.customer)

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    stem = f"{date.today().isoformat()}-bom-seed-master-data"
    sql_path = output_dir / f"{stem}.sql"
    workbook_path = output_dir / f"{stem}.xlsx"
    summary_path = output_dir / f"{stem}.json"

    write_sql(bundle, sql_path, replace_existing=args.replace_existing)
    write_workbook(bundle, workbook_path)
    summary_path.write_text(
        json.dumps(
            {
                "version": version,
                "months": months,
                **summarize_bundle(bundle),
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )

    if args.apply:
        apply_sql(db, sql_path)

    print(json.dumps(
        {
            "version": version,
            "months": months,
            "sql": str(sql_path),
            "xlsx": str(workbook_path),
            "summary": str(summary_path),
            **summarize_bundle(bundle),
            "applied": bool(args.apply),
            "replace_existing": bool(args.replace_existing),
        },
        ensure_ascii=False,
        indent=2,
    ))


if __name__ == "__main__":
    main()
