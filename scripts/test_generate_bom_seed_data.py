import pathlib
import sys
import tempfile
import unittest
from pathlib import Path


sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

from generate_bom_seed_data import build_seed_bundle, write_sql


class BuildSeedBundleTests(unittest.TestCase):
    def test_build_seed_bundle_generates_expected_scopes(self):
        bom_rows = [
            {
                "parent_code": "FP100",
                "child_code": "FP100-1",
                "usage_qty": 1.0,
                "process": "焊接",
                "equipment": "EQ-01",
                "manufacturing_department": "制造一课",
                "manufacturing_unit": "焊接",
                "version": "V1",
            },
            {
                "parent_code": "FP100-1",
                "child_code": "SEMI200",
                "usage_qty": 1.0,
                "process": "整理",
                "equipment": "",
                "manufacturing_department": "制造二课",
                "manufacturing_unit": "组立",
                "version": "V1",
            },
            {
                "parent_code": "SEMI200",
                "child_code": "PART300",
                "usage_qty": 2.0,
                "process": "压装",
                "equipment": "EQ-02",
                "manufacturing_department": "制造三课",
                "manufacturing_unit": "压装",
                "version": "V1",
            },
        ]

        bundle = build_seed_bundle(bom_rows, [202606, 202607, 202608], "V1")

        demand_item_codes = {row["item_code"] for row in bundle["demand_rows"]}
        self.assertEqual({"FP100"}, demand_item_codes)

        part_codes = {row["part_no"] for row in bundle["part_rows"]}
        self.assertIn("FP100-1", part_codes)
        self.assertIn("PART300", part_codes)

        safety_codes = {row["item_code"] for row in bundle["safety_rows"]}
        self.assertIn("SEMI200", safety_codes)
        self.assertNotIn("FP100-1", safety_codes)
        self.assertNotIn("PART300", safety_codes)

        equipment_keys = {
            (row["manufacturing_department"], row["equipment_model"])
            for row in bundle["equipment_rows"]
        }
        self.assertEqual(
            {("制造一课", "EQ-01"), ("制造三课", "EQ-02")},
            equipment_keys,
        )

        inventory_rows = [
            row for row in bundle["inventory_rows"] if row["item_code"] == "SEMI200"
        ]
        self.assertEqual(1, len(inventory_rows))
        self.assertEqual(202605, inventory_rows[0]["year_month"])

    def test_write_sql_defaults_to_non_destructive_mode(self):
        bundle = {
            "demand_rows": [{"customer": "测试客户", "item_code": "FP100", "year_month": 202606, "demand_qty": 100.0, "ending_inventory": 10.0, "min_safety_stock": 5.0, "net_demand": 95.0, "version": "V1"}],
            "part_rows": [{"part_no": "FP100", "product_name": "完成品-FP100", "product_no": "P-FP100", "project_name": "项目-FP10"}],
            "equipment_rows": [{"manufacturing_department": "制造一课", "equipment_category": "焊接设备", "equipment_brand": "TEST", "equipment_model": "EQ-01", "equipment_count": 1}],
            "safety_rows": [{"item_code": "SEMI200", "year_month": 202606, "daily_equivalent": 10.0, "safety_days": 3.0, "max_days": 7.0, "version": "V1"}],
            "inventory_rows": [{"item_code": "SEMI200", "year_month": 202605, "available_qty": 30.0, "version": "V1"}],
        }
        with tempfile.TemporaryDirectory() as tmp_dir:
            output = Path(tmp_dir) / "seed.sql"
            write_sql(bundle, output)
            sql = output.read_text(encoding="utf-8")
        self.assertNotIn("DELETE FROM t_demand;", sql)
        self.assertIn("INSERT INTO t_demand", sql)


if __name__ == "__main__":
    unittest.main()
