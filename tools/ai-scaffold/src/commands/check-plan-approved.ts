import path from "node:path";
import { assertNoExtraArgs } from "../utils/cli";
import { testPlanApproved, testPlanningEvidenceArchived } from "./gate";
import { resolveRepoPath } from "../utils/paths";

export async function checkPlanApprovedCommand(args: string[], context: { repoRoot: string }): Promise<void> {
  const featureDir = args[0];
  if (!featureDir) {
    throw new Error("❌ 请传入功能目录路径，或设置环境变量 FEATURE_DIR。");
  }
  assertNoExtraArgs(args.slice(1));
  const resolved = resolveRepoPath(context.repoRoot, featureDir);
  testPlanApproved(resolved);
  testPlanningEvidenceArchived(resolved);
  console.log(`✅ Plan 已批准且规划归档完整: ${path.join(resolved, "plan.md")}`);
  console.log(`   - reports/planning/deep-interview.md`);
  console.log(`   - reports/planning/prd.md`);
  console.log(`   - reports/planning/test-spec.md`);
}
