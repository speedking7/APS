import path from "node:path";
import { assertNoExtraArgs } from "../utils/cli";
import { testTraceability, getAcIds } from "./gate";
import { resolveRepoPath } from "../utils/paths";
import fs from "node:fs";
import { loadScaffoldConfig, resolveConfigPath } from "../config/scaffold-config";

export async function checkTaskTraceabilityCommand(args: string[], context: { repoRoot: string }): Promise<void> {
  const featureDir = args[0];
  const taskFiles: string[] = [];
  if (featureDir) {
    assertNoExtraArgs(args.slice(1));
    const resolved = resolveRepoPath(context.repoRoot, featureDir);
    const taskFile = path.join(resolved, "TASK.md");
    if (!fs.existsSync(taskFile)) {
      throw new Error(`ERROR: Missing TASK.md: ${taskFile}`);
    }
    taskFiles.push(taskFile);
  } else {
    const config = loadScaffoldConfig(context.repoRoot);
    const featuresDir = resolveConfigPath(context.repoRoot, config.featureRoot);
    for (const entry of fs.readdirSync(featuresDir, { withFileTypes: true })) {
      if (!entry.isDirectory() || !/^F\d{3}-/u.test(entry.name)) continue;
      const taskFile = path.join(featuresDir, entry.name, "TASK.md");
      if (fs.existsSync(taskFile)) taskFiles.push(taskFile);
    }
  }

  if (taskFiles.length === 0) {
    console.log("No docs/features/F{nnn}-*/TASK.md; skip AC traceability.");
    return;
  }

  for (const taskFile of taskFiles) {
    const featureDirPath = path.dirname(taskFile);
    const featureName = path.basename(featureDirPath);
    const acIds = getAcIds(taskFile);
    if (acIds.length === 0) {
      console.log(`No AC ids in ${featureName}/TASK.md; skip.`);
      continue;
    }
    testTraceability(featureDirPath, context.repoRoot);
  }

  console.log("AC traceability check passed.");
}
