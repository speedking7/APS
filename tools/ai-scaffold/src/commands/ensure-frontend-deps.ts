import path from "node:path";
import { assertNoExtraArgs, takeFlag, takeOption } from "../utils/cli";
import { describeFrontendDependencyHealth, ensureFrontendDependencies } from "../utils/frontend-deps";
import { loadScaffoldConfig, resolveConfigPath } from "../config/scaffold-config";

export async function ensureFrontendDepsCommand(args: string[], context: { repoRoot: string }): Promise<void> {
  const frontendDirOption = takeOption(args, "--dir");
  const force = takeFlag(args, "--force");
  assertNoExtraArgs(args);

  const config = loadScaffoldConfig(context.repoRoot);
  const frontendDirs = frontendDirOption
    ? [path.resolve(context.repoRoot, frontendDirOption)]
    : config.frontends.map((frontend) => resolveConfigPath(context.repoRoot, frontend.path));

  for (const frontendDir of frontendDirs) {
    const label = path.relative(context.repoRoot, frontendDir) || frontendDir;
    const before = describeFrontendDependencyHealth(frontendDir);
    if (!before.ok || force) {
      ensureFrontendDependencies(frontendDir, {
        forceReinstall: force,
        label,
      });
    }
    const after = describeFrontendDependencyHealth(frontendDir);
    if (!after.ok) {
      throw new Error(`Dependency repair failed for ${label}: ${after.reason}`);
    }
    console.log(`Frontend dependencies OK: ${label}`);
  }
}
