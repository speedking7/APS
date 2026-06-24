import path from "node:path";
import { assertNoExtraArgs } from "../utils/cli";
import { testCodeReviewVerdict, getCodeReviewVerdict } from "./gate";
import { resolveRepoPath } from "../utils/paths";

export async function checkCodeReviewVerdictCommand(args: string[], context: { repoRoot: string }): Promise<void> {
  const featureDir = args[0];
  if (!featureDir) {
    throw new Error("❌ 请传入功能目录路径，或设置环境变量 FEATURE_DIR。");
  }
  assertNoExtraArgs(args.slice(1));
  const resolved = resolveRepoPath(context.repoRoot, featureDir);
  testCodeReviewVerdict(resolved);
  const reportFile = path.join(resolved, "reports", "code-review-report.md");
  const verdict = getCodeReviewVerdict(reportFile);
  console.log(`✅ Code Review 已放行: ${reportFile}`);
  console.log(`   Verdict: ${verdict}`);
}
