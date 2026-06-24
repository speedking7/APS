import { assertNoExtraArgs, takeFlag } from "../utils/cli";
import { loadScaffoldConfig, normalizeRoot } from "../config/scaffold-config";

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/gu, "\\$&");
}

async function readStdinLines(): Promise<string[]> {
  return new Promise((resolve, reject) => {
    let content = "";
    process.stdin.setEncoding("utf8");
    process.stdin.on("data", (chunk: string) => {
      content += chunk;
    });
    process.stdin.on("error", reject);
    process.stdin.on("end", () => {
      resolve(content.split(/\r?\n/u).map((line) => line.trim()).filter(Boolean));
    });
  });
}

export async function checkWorkItemLinkCommand(args: string[], context: { repoRoot: string }): Promise<void> {
  const useStdin = takeFlag(args, "--stdin");
  assertNoExtraArgs(args);

  if (!useStdin) {
    throw new Error("ERROR: check-work-item-link requires --stdin with newline-delimited changed files.");
  }

  const changedFiles = await readStdinLines();
  const config = loadScaffoldConfig(context.repoRoot);
  const codeRoots = config.codeLikeRoots.map(normalizeRoot);
  const featureRoot = normalizeRoot(config.featureRoot);
  const bugfixRoot = normalizeRoot(config.bugfixRoot);
  const featurePattern = new RegExp(`^${escapeRegExp(featureRoot)}F[0-9]{3}-[^/]+/`, "u");
  const bugfixPattern = new RegExp(`^${escapeRegExp(bugfixRoot)}[^/]+/`, "u");
  const codeChanged = changedFiles.some((file) => codeRoots.some((root) => file.startsWith(root)));
  const workItemChanged = changedFiles.some((file) => featurePattern.test(file) || bugfixPattern.test(file));

  if (codeChanged && !workItemChanged) {
    throw new Error(
      [
        "Code changes must be tied to a feature or bugfix artifact directory.",
        `Changed code-like paths include ${codeRoots.join(", ")}, but no ${featureRoot}Fxxx-* or ${bugfixRoot}* directory changed.`,
        "Add or update the matching plan/TASK/test evidence so reuse decisions are reviewable.",
      ].join("\n"),
    );
  }

  if (codeChanged) {
    console.log("Work item link check passed: code changes are tied to feature/bugfix artifacts.");
  } else {
    console.log("Work item link check passed: no backend/frontend/docs-db changes detected.");
  }
}
