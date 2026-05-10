"""
APS 系统 E2E 测试 - 完整业务流程
录制视频保存至 /home/speedking/projects/APS/test-results/
"""
import time
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

BASE = "http://localhost:8080"
EXCEL = "/home/speedking/projects/APS/APS模板.xlsx"
VIDEO_DIR = Path("/home/speedking/projects/APS/test-results")
VIDEO_DIR.mkdir(parents=True, exist_ok=True)

PASSED = []
FAILED = []

def log(msg):
    PASSED.append(msg)
    print(f"  ✓ {msg}")

def fail(msg, e):
    FAILED.append(msg)
    print(f"  ✗ {msg}: {e}")


def run_e2e():
    with sync_playwright() as p:
        browser = p.chromium.launch(
            headless=False,
            slow_mo=200,
            args=["--no-sandbox", "--disable-dev-shm-usage"],
        )
        ctx = browser.new_context(
            viewport={"width": 1440, "height": 900},
            record_video_dir=str(VIDEO_DIR),
            record_video_size={"width": 1440, "height": 900},
        )
        page = ctx.new_page()

        # ──────────────────────────────────────────────
        # 1. 首页导航
        # ──────────────────────────────────────────────
        print("\n【1】首页导航")
        page.goto(f"{BASE}/index.html")
        page.wait_for_load_state("networkidle")
        expect(page.locator(".logo-mark").first).to_be_visible()
        page.screenshot(path=str(VIDEO_DIR / "01-index.png"))
        log("首页加载成功")
        time.sleep(1)

        # ──────────────────────────────────────────────
        # 2. 工作台 Dashboard
        # ──────────────────────────────────────────────
        print("\n【2】工作台 Dashboard")
        page.goto(f"{BASE}/01-dashboard.html")
        page.wait_for_load_state("networkidle")
        time.sleep(2)
        page.screenshot(path=str(VIDEO_DIR / "02-dashboard.png"))
        log("Dashboard 加载成功")

        # ──────────────────────────────────────────────
        # 3. 预测管理 - 批量导入 Excel
        # ──────────────────────────────────────────────
        print("\n【3】预测管理 - 批量导入 Excel")
        page.goto(f"{BASE}/02-forecast-list.html")
        page.wait_for_load_state("networkidle")
        time.sleep(1.5)
        page.screenshot(path=str(VIDEO_DIR / "03-forecast-before.png"))
        log("预测管理页面加载成功")

        # 点击"批量导入"按钮打开 modal
        page.click("text=批量导入")
        time.sleep(0.8)
        expect(page.locator("#importModal")).to_be_visible()
        log("批量导入弹窗打开")

        # 选择 Excel 文件（点击上传区域，触发 file input）
        with page.expect_file_chooser() as fc_info:
            page.click(".upload-zone")
        file_chooser = fc_info.value
        file_chooser.set_files(EXCEL)
        time.sleep(0.8)
        page.screenshot(path=str(VIDEO_DIR / "04-import-modal.png"))
        log(f"已选择文件：APS模板.xlsx")

        # 点击"确认导入"
        page.click("text=确认导入")
        # 等待 toast 出现（含"导入完成"）
        page.wait_for_selector("text=导入完成", timeout=15000)
        time.sleep(1.5)
        page.screenshot(path=str(VIDEO_DIR / "05-import-done.png"))
        log("Excel 全量导入成功")

        # ──────────────────────────────────────────────
        # 4. 计划计算 - 执行计算
        # ──────────────────────────────────────────────
        print("\n【4】计划计算 - 执行计算")
        page.goto(f"{BASE}/07-plan-calculate.html")
        page.wait_for_load_state("networkidle")
        time.sleep(2)
        page.screenshot(path=str(VIDEO_DIR / "06-plan-calc-ready.png"))
        log("计划计算页面就绪检查完成")

        # 点击"执行计算"
        page.click("#execBtn")
        # 等待 calcResult 出现（最多 20 秒）
        page.wait_for_function(
            """() => {
                const el = document.getElementById('calcResult');
                return el && el.style.display !== 'none' && el.innerText.trim().length > 0;
            }""",
            timeout=20000,
        )
        time.sleep(1)
        page.screenshot(path=str(VIDEO_DIR / "07-calc-done.png"))
        log("计划计算执行成功")

        # ──────────────────────────────────────────────
        # 5. 计划结果 - 查看数据 + 明细弹窗
        # ──────────────────────────────────────────────
        print("\n【5】计划结果")
        page.goto(f"{BASE}/08-plan-result.html")
        page.wait_for_load_state("networkidle")
        time.sleep(2)
        # 等待表格有数据
        page.wait_for_function(
            "() => document.querySelectorAll('#dataTbody tr').length > 0",
            timeout=10000,
        )
        row_count = page.locator("#dataTbody tr").count()
        page.screenshot(path=str(VIDEO_DIR / "08-plan-result.png"))
        log(f"计划结果页面加载成功（{row_count} 行）")

        # 点击第一行查看明细
        page.locator("#dataTbody tr").first.click()
        time.sleep(1)
        page.screenshot(path=str(VIDEO_DIR / "09-plan-detail.png"))
        log("计划结果明细弹窗打开")
        # 关闭弹窗
        page.locator(".modal-close").first.click()
        time.sleep(0.5)

        # ──────────────────────────────────────────────
        # 6. BOM 管理
        # ──────────────────────────────────────────────
        print("\n【6】BOM 管理")
        page.goto(f"{BASE}/03-bom-list.html")
        page.wait_for_load_state("networkidle")
        time.sleep(2)
        page.wait_for_function(
            "() => document.querySelectorAll('#dataTbody tr').length > 0",
            timeout=8000,
        )
        bom_count = page.locator("#dataTbody tr").count()
        page.screenshot(path=str(VIDEO_DIR / "10-bom.png"))
        log(f"BOM 管理页面加载成功（{bom_count} 行）")

        # ──────────────────────────────────────────────
        # 7. 物料参数
        # ──────────────────────────────────────────────
        print("\n【7】物料参数")
        page.goto(f"{BASE}/04-material-params.html")
        page.wait_for_load_state("networkidle")
        time.sleep(2)
        page.screenshot(path=str(VIDEO_DIR / "11-material-params.png"))
        log("物料参数页面加载成功")

        # ──────────────────────────────────────────────
        # 8. 稼动天数
        # ──────────────────────────────────────────────
        print("\n【8】稼动天数")
        page.goto(f"{BASE}/05-operating-days.html")
        page.wait_for_load_state("networkidle")
        time.sleep(2)
        page.screenshot(path=str(VIDEO_DIR / "12-operating-days.png"))
        log("稼动天数页面加载成功")

        # ──────────────────────────────────────────────
        # 9. 盘点数
        # ──────────────────────────────────────────────
        print("\n【9】盘点数")
        page.goto(f"{BASE}/06-inventory-count.html")
        page.wait_for_load_state("networkidle")
        time.sleep(2)
        page.wait_for_function(
            "() => document.querySelectorAll('#dataTbody tr').length > 0",
            timeout=8000,
        )
        inv_count = page.locator("#dataTbody tr").count()
        page.screenshot(path=str(VIDEO_DIR / "13-inventory-count.png"))
        log(f"盘点数页面加载成功（{inv_count} 行）")

        # ──────────────────────────────────────────────
        # 10. 人员需求报表
        # ──────────────────────────────────────────────
        print("\n【10】人员需求报表")
        page.goto(f"{BASE}/10-workforce-report.html")
        page.wait_for_load_state("networkidle")
        time.sleep(2)
        page.screenshot(path=str(VIDEO_DIR / "14-workforce.png"))
        log("人员需求报表加载成功")

        # ──────────────────────────────────────────────
        # 11. 设备负荷分析
        # ──────────────────────────────────────────────
        print("\n【11】设备负荷分析")
        page.goto(f"{BASE}/11-equipment-load.html")
        page.wait_for_load_state("networkidle")
        time.sleep(2)
        page.screenshot(path=str(VIDEO_DIR / "15-equipment-load.png"))
        log("设备负荷分析加载成功")

        time.sleep(1.5)
        ctx.close()
        browser.close()

    # ── 结果汇总 ──
    total = len(PASSED) + len(FAILED)
    print(f"\n{'='*55}")
    print(f"E2E 测试完成  {len(PASSED)}/{total} 步通过")
    if FAILED:
        print("失败步骤：")
        for f in FAILED:
            print(f"  ✗ {f}")
    print(f"\n视频：{VIDEO_DIR}/*.webm")
    print(f"截图：{VIDEO_DIR}/*.png")
    print("="*55)
    if FAILED:
        raise SystemExit(1)


if __name__ == "__main__":
    run_e2e()
