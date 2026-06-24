import fs from "node:fs";
import path from "node:path";
import { assertNoExtraArgs } from "../utils/cli";
import { resolveRepoPath } from "../utils/paths";
import { testPlanApproved, testPlanningEvidenceArchived } from "./gate";

export async function checkBuildFeaturePrereqsCommand(args: string[], context: { repoRoot: string }): Promise<void> {
  const featureDir = args[0];
  if (!featureDir) {
    throw new Error("❌ 请传入功能目录路径，或设置环境变量 FEATURE_DIR。");
  }
  assertNoExtraArgs(args.slice(1));
  const resolved = resolveRepoPath(context.repoRoot, featureDir);
  if (!fs.existsSync(resolved)) {
    throw new Error(`❌ 目录不存在: ${resolved}`);
  }
  const featureBasename = path.basename(resolved);
  const match = /^F\d{3}-(.+)$/u.exec(featureBasename);
  if (!match) {
    throw new Error(`❌ 功能目录名不符合约定: ${featureBasename}`);
  }
  const featureSlug = match[1]!;
  const required = [
    path.join(context.repoRoot, ".omx", "specs", `deep-interview-${featureSlug}.md`),
    path.join(context.repoRoot, ".omx", "plans", `prd-${featureSlug}.md`),
    path.join(context.repoRoot, ".omx", "plans", `test-spec-${featureSlug}.md`),
  ];

  testPlanApproved(resolved);
  testPlanningEvidenceArchived(resolved);
  console.log("");
  const missing = required.filter((filePath) => !fs.existsSync(filePath));
  if (missing.length > 0) {
    const relative = missing.map((filePath) => path.relative(context.repoRoot, filePath).split(path.sep).join("/"));
    throw new Error(`❌ build-feature 前置的 OMX 产物不完整。\n   功能目录: ${resolved}\n   缺失产物:\n   - ${relative.join("\n   - ")}`);
  }

  console.log(`✅ build-feature 前置门禁通过: ${featureBasename}`);
  console.log("   - plan.md 已批准");
  console.log("   - reports/planning 归档已存在");
  console.log("   - deep-interview 规格已存在");
  console.log("   - ralplan PRD / test-spec 已存在");
}
