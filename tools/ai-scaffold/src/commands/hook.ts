import fs from "node:fs";
import path from "node:path";
import readline from "node:readline/promises";
import { assertNoExtraArgs, requireOption } from "../utils/cli";
import { runChecked } from "../utils/exec";
import { ensureDir, writeText } from "../utils/fs";
import {
  commandOrDefault,
  expandCommandArgs,
  loadScaffoldConfig,
  normalizeRoot,
  resolveCommand,
  resolveConfigPath,
  type CommandSpec,
  type FrontendConfig,
  type ScaffoldConfig,
} from "../config/scaffold-config";

export async function hookCommand(args: string[], context: { repoRoot: string }): Promise<void> {
  const hookName = requireOption(args, "--hook");

  if (hookName === "pre-commit") {
    await runPreCommit(context.repoRoot);
    return;
  }
  if (hookName === "pre-push") {
    await runPrePush(context.repoRoot);
    return;
  }

  throw new Error(`Unsupported hook: ${hookName}`);
}

export async function installHooksCommand(args: string[], context: { repoRoot: string }): Promise<void> {
  assertNoExtraArgs(args);
  const gitHooksDir = path.join(context.repoRoot, ".git", "hooks");
  if (!fs.existsSync(path.join(context.repoRoot, ".git"))) {
    throw new Error("ERROR: Not a git repository. Please run this command from the project root.");
  }
  ensureDir(gitHooksDir);

  const preCommit = `#!/usr/bin/env sh
exec node tools/ai-scaffold/dist/cli.js hook --hook pre-commit "$@"
`;
  const prePush = `#!/usr/bin/env sh
exec node tools/ai-scaffold/dist/cli.js hook --hook pre-push "$@"
`;

  writeText(path.join(gitHooksDir, "pre-commit"), preCommit);
  writeText(path.join(gitHooksDir, "pre-push"), prePush);

  console.log("=== Git Hooks Installed Successfully ===");
  console.log("Installed hooks:");
  console.log("  - pre-commit: Requires human approval for deletions and runs quick tests");
  console.log("  - pre-push: Runs changed-scope checks before pushing");
}

async function runPreCommit(repoRoot: string): Promise<void> {
  const config = loadScaffoldConfig(repoRoot);
  console.log(`=== ${config.projectName} Quality Gate ===`);
  const deletedFiles = getStagedDeletedFiles(repoRoot);

  console.log("\n[1/3] Checking for deleted test files...");
  await requireHumanDeletionApproval(deletedFiles);
  const deletedTests = deletedFiles.filter((line) => /.*Test\.java$|.*Test\.ts$|.*Test\.tsx$|.*spec\.ts$|.*spec\.tsx$/u.test(line));
  if (deletedTests.length > 0) {
    throw new Error(`ERROR: The following test files are being deleted:\n${deletedTests.join("\n")}\n\nDeleting test files is not allowed. Please restore them or update existing tests.`);
  }
  console.log("No test files deleted.");

  console.log("\n[2/3] Checking backend changes...");
  const backendRoot = normalizeRoot(config.backend.changeRoot ?? config.backend.path);
  const backendChanged = captureGit(repoRoot, ["diff", "--cached", "--name-only"])
    .split(/\r?\n/u)
    .filter((line) => line.startsWith(backendRoot) && line.endsWith(".java"));
  if (backendChanged.length > 0) {
    console.log("Backend files changed. Running quick validation...");
    const backendDir = resolveConfigPath(repoRoot, config.backend.path);
    runConfiguredCommand(commandOrDefault(config.backend.commands?.compile, { command: "mvn", args: ["compile", "-q"] }), backendDir, "ERROR: Backend compilation failed. Please fix compilation errors.");
    console.log("Backend compilation successful.");
    const modulePrefix = normalizeRoot(config.backend.modulePathPrefix ?? config.backend.changeRoot ?? config.backend.path);
    const changedModules = [...new Set(
      captureGit(repoRoot, ["diff", "--cached", "--name-only"])
        .split(/\r?\n/u)
        .filter((line) => line.startsWith(modulePrefix))
        .map((line) => line.slice(modulePrefix.length).split("/")[0]!)
        .filter(Boolean),
    )];
    if (changedModules.length > 0) {
      console.log("Running tests for changed modules...");
      for (const moduleName of changedModules) {
        if (fs.existsSync(path.join(backendDir, moduleName))) {
          console.log(`Testing module: ${moduleName}`);
          runConfiguredCommand({ command: "mvn", args: ["test", "-pl", moduleName, "-DskipITs", "-q"] }, backendDir, `ERROR: Tests failed for ${moduleName}`);
          console.log(`Tests passed for ${moduleName}`);
        }
      }
    }
  } else {
    console.log("No backend changes detected.");
  }

  console.log("\n[3/3] Checking frontend changes...");
  const frontendChanged = captureGit(repoRoot, ["diff", "--cached", "--name-only"])
    .split(/\r?\n/u)
    .filter((line) => config.frontends.some((frontend) => line.startsWith(normalizeRoot(frontend.changeRoot ?? frontend.path))) && /\.(ts|tsx)$/u.test(line));
  if (frontendChanged.length > 0) {
    console.log("Frontend files changed. Running lint check...");
    for (const frontend of getChangedFrontends(config, frontendChanged)) {
      const frontendDir = resolveConfigPath(repoRoot, frontend.path);
      if (!fs.existsSync(path.join(frontendDir, "node_modules"))) {
        console.log(`Installing dependencies for ${frontend.path}...`);
        runChecked("npm", ["ci", "--quiet"], {
          cwd: frontendDir,
          errorMessage: `ERROR: npm ci failed for ${frontend.path}.`,
        });
      }
      runConfiguredCommand(commandOrDefault(frontend.commands?.lint, { command: "npm", args: ["run", "lint", "--silent"] }), frontendDir, `ERROR: Frontend lint check failed for ${frontend.path}.`);
      console.log(`Frontend lint check passed for ${frontend.path}.`);
    }
  } else {
    console.log("No frontend changes detected.");
  }

  console.log("\n=== Quality Gate Passed ===");
  console.log("Commit is ready to proceed.");
}

function getStagedDeletedFiles(repoRoot: string): string[] {
  return captureGit(repoRoot, ["diff", "--cached", "--name-only", "--diff-filter=D"])
    .split(/\r?\n/u)
    .map((line) => line.trim())
    .filter(Boolean);
}

async function requireHumanDeletionApproval(deletedFiles: string[]): Promise<void> {
  if (deletedFiles.length === 0) {
    return;
  }

  console.error("\nManual deletion approval required.");
  console.error("The following staged paths are being deleted:");
  for (const filePath of deletedFiles) {
    console.error(`  - ${filePath}`);
  }

  if (!process.stdin.isTTY || !process.stdout.isTTY) {
    throw new Error(
      [
        "ERROR: Staged deletions require an interactive human approval.",
        "Run the commit from an interactive terminal and type the exact approval phrase when prompted.",
        "Approval phrase: DELETE APPROVED",
      ].join("\n"),
    );
  }

  const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
  try {
    const answer = await rl.question('Type "DELETE APPROVED" to confirm these deletions were reviewed by a human: ');
    if (answer.trim() !== "DELETE APPROVED") {
      throw new Error("ERROR: Deletion approval phrase did not match. Commit aborted.");
    }
  } finally {
    rl.close();
  }
}

async function runPrePush(repoRoot: string): Promise<void> {
  const config = loadScaffoldConfig(repoRoot);
  console.log(`=== ${config.projectName} Pre-push Quality Gate ===`);
  const currentBranch = captureGit(repoRoot, ["rev-parse", "--abbrev-ref", "HEAD"]).trim();
  if (currentBranch === "gh-pages" || /^dependabot\//u.test(currentBranch)) {
    console.log(`Skipping pre-push checks for branch: ${currentBranch}`);
    return;
  }

  const changedFiles = getPrePushChangedFiles(repoRoot);
  const plan = getPrePushPlan(changedFiles, config);
  console.log(`Changed files in push range: ${changedFiles.length}`);

  if (plan.scaffold) {
    console.log("\n[1/4] Running AI scaffold self-tests...");
    runChecked("npm", ["test"], {
      cwd: path.join(repoRoot, "tools", "ai-scaffold"),
      errorMessage: "ERROR: AI scaffold tests failed. Push aborted.",
    });
    console.log("AI scaffold tests passed.");
  } else {
    console.log("\n[1/4] No scaffold changes detected.");
  }

  if (plan.backend) {
    console.log("\n[2/4] Running backend test suite...");
    runConfiguredCommand(commandOrDefault(config.backend.commands?.test, { command: "mvn", args: ["test", "-q"] }), resolveConfigPath(repoRoot, config.backend.path), "ERROR: Backend tests failed. Push aborted.");
    console.log("Backend tests passed.");
  } else {
    console.log("\n[2/4] No backend changes detected.");
  }

  if (plan.frontends.length > 0) {
    console.log("\n[3/4] Running frontend build checks...");
    for (const frontend of plan.frontends) {
      runFrontendBuildCheck(resolveConfigPath(repoRoot, frontend.path), frontend);
    }
  } else {
    console.log("\n[3/4] No frontend changes detected.");
  }

  console.log("\n=== All Quality Gates Passed ===");
  console.log("Pushing to remote...");
}

export function getPrePushPlan(changedFiles: string[], config: ScaffoldConfig = loadScaffoldConfig(process.cwd())): {
  backend: boolean;
  frontends: FrontendConfig[];
  scaffold: boolean;
} {
  const backendRoot = normalizeRoot(config.backend.changeRoot ?? config.backend.path);
  return {
    backend: changedFiles.some((filePath) => filePath.startsWith(backendRoot)),
    frontends: getChangedFrontends(config, changedFiles),
    scaffold: changedFiles.some((filePath) => config.scaffoldRoots.some((root) => filePath.startsWith(normalizeRoot(root)) || filePath === root)),
  };
}

function getPrePushChangedFiles(repoRoot: string): string[] {
  const base = firstSuccessfulGitOutput(repoRoot, [
    ["merge-base", "HEAD", "origin/master"],
    ["merge-base", "HEAD", "origin/main"],
    ["merge-base", "HEAD", "@{upstream}"],
  ]).trim();

  const range = base ? `${base}..HEAD` : "HEAD~1..HEAD";
  return captureGit(repoRoot, ["diff", "--name-only", range])
    .split(/\r?\n/u)
    .map((line) => line.trim())
    .filter(Boolean);
}

function firstSuccessfulGitOutput(repoRoot: string, commands: string[][]): string {
  const { spawnSync } = require("node:child_process") as typeof import("node:child_process");
  for (const args of commands) {
    const result = spawnSync("git", args, {
      cwd: repoRoot,
      encoding: "utf8",
      shell: false,
    });
    if (result.status === 0) {
      return result.stdout ?? "";
    }
  }
  return "";
}

function runFrontendBuildCheck(frontendDir: string, frontend: FrontendConfig): void {
  if (!fs.existsSync(path.join(frontendDir, "node_modules"))) {
    console.log("Installing dependencies...");
    runChecked("npm", ["ci", "--quiet"], {
      cwd: frontendDir,
      errorMessage: `ERROR: npm ci failed for ${frontend.path}.`,
    });
  }
  runConfiguredCommand(commandOrDefault(frontend.commands?.build, { command: "npm", args: ["run", "build"] }), frontendDir, `ERROR: ${frontend.path} build failed. Push aborted.`);
  console.log(`${frontend.path} build passed.`);
}

function captureGit(repoRoot: string, args: string[]): string {
  const { spawnSync } = require("node:child_process") as typeof import("node:child_process");
  const result = spawnSync("git", args, {
    cwd: repoRoot,
    encoding: "utf8",
    shell: false,
  });
  if (result.status !== 0) {
    throw new Error(result.stderr || `git ${args.join(" ")} failed`);
  }
  return result.stdout ?? "";
}

function getChangedFrontends(config: ScaffoldConfig, changedFiles: string[]): FrontendConfig[] {
  return config.frontends.filter((frontend) => {
    const root = normalizeRoot(frontend.changeRoot ?? frontend.path);
    return changedFiles.some((filePath) => filePath.startsWith(root));
  });
}

function runConfiguredCommand(spec: CommandSpec, cwd: string, errorMessage: string): void {
  runChecked(resolveCommand(spec.command), expandCommandArgs(spec.args, {}), {
    cwd,
    errorMessage,
  });
}
