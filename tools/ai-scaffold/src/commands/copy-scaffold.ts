import fs from "node:fs";
import path from "node:path";
import { assertNoExtraArgs, takeFlag, takeOption } from "../utils/cli";
import { loadScaffoldConfig } from "../config/scaffold-config";

const COPY_ROOTS = [
  "AGENTS.md",
  "project.md",
  "ai-scaffold.config.json",
  ".agents",
  ".codex",
  "docs/ai-scaffold-migration.md",
  "docs/features",
  "docs/bugfix",
  "tools/ai-scaffold",
  "scripts",
  "copy-scaffold.mjs",
];

const GITIGNORE_MARKER = "# AI scaffold runtime and generated/cache outputs";
const GITIGNORE_BLOCK = `${GITIGNORE_MARKER}
.omx/
.codex/tasks/tmp/
.codex/worktrees/*
!.codex/worktrees/README.md
tools/ai-scaffold/node_modules/
tools/ai-scaffold/dist/
`;

export async function copyScaffoldCommand(args: string[], context: { repoRoot: string }): Promise<void> {
  const targetValue = takeOption(args, "--target") ?? takeOption(args, "-t");
  const includeReadmeFlag = takeFlag(args, "--include-readme");
  const skipGitignore = takeFlag(args, "--skip-gitignore");
  const includeDistFlag = takeFlag(args, "--include-dist");
  const includeNodeModulesFlag = takeFlag(args, "--include-node-modules");
  const includeSmokeFeatureFlag = takeFlag(args, "--include-smoke-feature");
  const dryRun = takeFlag(args, "--dry-run");
  assertNoExtraArgs(args);

  if (!targetValue) {
    throw new Error("Missing --target <repo>.");
  }

  const config = loadScaffoldConfig(context.repoRoot);
  const targetDir = path.resolve(process.cwd(), targetValue);
  if (path.resolve(targetDir) === path.resolve(context.repoRoot)) {
    throw new Error("Refusing to copy scaffold onto itself.");
  }

  const options = {
    includeDist: includeDistFlag || Boolean(config.portableCopy?.includeDist),
    includeNodeModules: includeNodeModulesFlag || Boolean(config.portableCopy?.includeNodeModules),
    includeSmokeFeature: includeSmokeFeatureFlag || Boolean(config.portableCopy?.includeSmokeFeature),
  };
  const includeReadme = includeReadmeFlag || Boolean(config.portableCopy?.includeReadme);
  const includeGitignore = !skipGitignore && config.portableCopy?.includeGitignore !== false;

  if (!dryRun) {
    fs.mkdirSync(targetDir, { recursive: true });
  }

  for (const root of COPY_ROOTS) {
    copyPath({
      sourceRoot: context.repoRoot,
      targetRoot: targetDir,
      relativePath: root,
      dryRun,
      includeReadme,
      options,
    });
  }
  copyPath({
    sourceRoot: context.repoRoot,
    targetRoot: targetDir,
    relativePath: "README.md",
    dryRun,
    includeReadme,
    options,
  });
  if (includeGitignore) {
    updateGitignore(targetDir, dryRun);
  }

  console.log("");
  console.log("AI scaffold copy complete.");
  console.log(`Target: ${targetDir}`);
  console.log(
    "Next: edit ai-scaffold.config.json, AGENTS.md, and project.md; then run npm --prefix tools/ai-scaffold ci && npm --prefix tools/ai-scaffold run build && node tools/ai-scaffold/dist/cli.js doctor",
  );
}

function copyPath(input: {
  sourceRoot: string;
  targetRoot: string;
  relativePath: string;
  dryRun: boolean;
  includeReadme: boolean;
  options: {
    includeDist: boolean;
    includeNodeModules: boolean;
    includeSmokeFeature: boolean;
  };
}): void {
  const sourcePath = path.join(input.sourceRoot, input.relativePath);
  if (!fs.existsSync(sourcePath)) {
    return;
  }

  const stat = fs.statSync(sourcePath);
  if (shouldSkip(input.relativePath, stat.isDirectory(), input.options)) {
    return;
  }

  if (stat.isDirectory()) {
    for (const entry of fs.readdirSync(sourcePath, { withFileTypes: true })) {
      copyPath({
        ...input,
        relativePath: path.join(input.relativePath, entry.name),
      });
    }
    return;
  }

  if (normalizePath(input.relativePath) === "README.md") {
    const targetReadme = path.join(input.targetRoot, "README.md");
    if (!input.includeReadme && fs.existsSync(targetReadme)) {
      console.log("skip existing README.md (use --include-readme to overwrite)");
      return;
    }
  }

  console.log(`${input.dryRun ? "would copy" : "copy"} ${normalizePath(input.relativePath)}`);
  if (input.dryRun) {
    return;
  }

  const destination = path.join(input.targetRoot, input.relativePath);
  fs.mkdirSync(path.dirname(destination), { recursive: true });
  fs.copyFileSync(sourcePath, destination);
}

function shouldSkip(
  relativePath: string,
  _isDirectory: boolean,
  options: { includeDist: boolean; includeNodeModules: boolean; includeSmokeFeature: boolean },
): boolean {
  const normalized = normalizePath(relativePath);
  if (!normalized || normalized === ".") {
    return false;
  }

  const segments = normalized.split("/");
  if (!options.includeNodeModules && segments.includes("node_modules")) {
    return true;
  }
  if (!options.includeDist && segments.includes("dist")) {
    return true;
  }
  if (segments.includes(".git")) {
    return true;
  }
  if (normalized === ".omx" || normalized.startsWith(".omx/")) {
    return true;
  }
  if (normalized === "_temp" || normalized.startsWith("_temp/")) {
    return true;
  }
  if (normalized === ".codex/tasks/tmp" || normalized.startsWith(".codex/tasks/tmp/")) {
    return true;
  }
  if (normalized.startsWith(".codex/worktrees/") && normalized !== ".codex/worktrees/README.md") {
    return true;
  }
  if (!options.includeSmokeFeature && /^docs\/features\/F\d{3}-/u.test(normalized)) {
    return true;
  }
  if (normalized === ".DS_Store" || normalized.endsWith("/.DS_Store") || normalized.endsWith("/Thumbs.db")) {
    return true;
  }
  return false;
}

function updateGitignore(targetRoot: string, dryRun: boolean): void {
  const gitignorePath = path.join(targetRoot, ".gitignore");
  const existing = fs.existsSync(gitignorePath) ? fs.readFileSync(gitignorePath, "utf8") : "";
  if (existing.includes(GITIGNORE_MARKER)) {
    console.log("gitignore already contains AI scaffold runtime block");
    return;
  }

  console.log(`${dryRun ? "would update" : "update"} .gitignore`);
  if (dryRun) {
    return;
  }

  const trimmed = existing.trimEnd();
  fs.mkdirSync(path.dirname(gitignorePath), { recursive: true });
  fs.writeFileSync(gitignorePath, `${trimmed}${trimmed ? "\n\n" : ""}${GITIGNORE_BLOCK}`, "utf8");
}

function normalizePath(value: string): string {
  return value.split(path.sep).join("/");
}
