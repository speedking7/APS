import path from "node:path";
import { assertNoExtraArgs, requireOption, takeFlag } from "../utils/cli";
import { ensureExists } from "../utils/paths";
import { readText } from "../utils/fs";
import { quotedCommand, repoNodeCommand, runChecked } from "../utils/exec";
import { loadScaffoldConfig, resolveConfigPath } from "../config/scaffold-config";

export async function startNextFeatureCommand(args: string[], context: { repoRoot: string }): Promise<void> {
  const taskId = requireOption(args, "--task-id");
  const slug = requireOption(args, "--slug");
  const title = requireOption(args, "--title");
  const scaffold = takeFlag(args, "--scaffold");
  assertNoExtraArgs(args);

  if (!/^T\d+\.\d+$/u.test(taskId)) {
    throw new Error(`TaskId must look like T1.14. Actual: '${taskId}'`);
  }

  if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/u.test(slug)) {
    throw new Error(`Slug must use lowercase letters, numbers, and hyphens. Actual: '${slug}'`);
  }

  const config = loadScaffoldConfig(context.repoRoot);
  const featuresRoot = resolveConfigPath(context.repoRoot, config.featureRoot);
  const nextNumberFile = path.join(featuresRoot, "NEXT_FEATURE_NUMBER.txt");
  ensureExists(nextNumberFile, "NEXT_FEATURE_NUMBER.txt");

  const rawNumber = readText(nextNumberFile).trim();
  if (!/^\d+$/u.test(rawNumber)) {
    throw new Error(`NEXT_FEATURE_NUMBER.txt must contain an integer. Actual: '${rawNumber}'`);
  }

  const featureNumber = Number.parseInt(rawNumber, 10);
  const featureCode = `F${featureNumber.toString().padStart(3, "0")}`;
  const featureDirName = `${featureCode}-${slug}`;
  const featureDirRelative = `${config.featureRoot.replace(/\\/gu, "/").replace(/\/$/u, "")}/${featureDirName}`;
  const planFileRelative = `${featureDirRelative}/plan.md`;

  const initFeature = repoNodeCommand(context.repoRoot, path.join("tools", "ai-scaffold", "dist", "cli.js"), [
    "init-feature",
    "--slug",
    slug,
    "--title",
    title,
  ]);
  const gateBase = repoNodeCommand(context.repoRoot, path.join("tools", "ai-scaffold", "dist", "cli.js"), [
    "gate",
    "--feature-dir",
    featureDirRelative,
  ]);

  console.log("Next feature preparation");
  console.log(`Task ID: ${taskId}`);
  console.log(`Feature code: ${featureCode}`);
  console.log(`Feature dir: ${featureDirRelative}`);
  console.log(`Plan file: ${planFileRelative}`);
  console.log(`Title: ${title}`);
  console.log(`Slug: ${slug}`);
  console.log("");
  console.log("Commands:");
  console.log(`1. ${quotedCommand(initFeature.filePath, initFeature.args)}`);
  console.log(`2. Draft ${planFileRelative} and stop for human approval.`);
  console.log(`3. ${quotedCommand(initFeature.filePath, ["archive-planning-artifacts", featureDirRelative, "--stage", "deep-interview"])}    # Right after deep-interview`);
  console.log(`4. ${quotedCommand(initFeature.filePath, ["archive-planning-artifacts", featureDirRelative, "--stage", "ralplan"])}    # Right after ralplan`);
  console.log(`5. bash scripts/check-plan-approved.sh ${featureDirRelative}`);
  console.log(`6. bash scripts/check-feature-artifacts.sh ${featureDirRelative}`);
  console.log(`7. ${quotedCommand(gateBase.filePath, gateBase.args)}`);
  console.log(`8. ${quotedCommand(gateBase.filePath, [...gateBase.args, "--run-e2e"])}    # Use when frontend/ changed`);

  if (scaffold) {
    runChecked(initFeature.filePath, initFeature.args, {
      cwd: context.repoRoot,
      errorMessage: "Failed to create feature scaffold.",
    });
    console.log("");
    console.log("Scaffold created.");
    console.log(`Next action: draft ${planFileRelative} and wait for approval before implementation.`);
  }
}
