import unittest
from pathlib import Path


class StaticSortIntegrationTests(unittest.TestCase):
    def test_plan_result_removes_toolbar_actions_and_loads_sort_helper(self):
        html = Path(
            "aps-system/src/main/resources/static/08-plan-result.html"
        ).read_text(encoding="utf-8")
        self.assertNotIn("切换版本", html)
        self.assertNotIn("手工调整", html)
        self.assertIn("table-multisort.js", html)


if __name__ == "__main__":
    unittest.main()
