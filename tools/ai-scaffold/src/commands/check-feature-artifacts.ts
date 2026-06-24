import { assertNoExtraArgs, takeFlag } from "../utils/cli";
import { runFeatureArtifactChecks } from "./gate";
import { resolveRepoPath } from "../utils/paths";

export async function checkFeatureArtifactsCommand(args: string[], context: { repoRoot: string }): Promise<void> {
  const featureDir = args[0];
  if (!featureDir) {
    throw new Error("ERROR: Provide a feature directory path or set FEATURE_DIR.");
  }
  const skipCodeReviewVerdict = takeFlag(args, "--skip-code-review-verdict");
  assertNoExtraArgs(args.slice(1));
  const resolved = resolveRepoPath(context.repoRoot, featureDir);
  runFeatureArtifactChecks(resolved, {
    skipCodeReviewVerdict,
    repoRoot: context.repoRoot,
  });
  console.log("");
  console.log("Feature artifact check passed.");
}
