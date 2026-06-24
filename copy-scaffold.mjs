#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scaffoldDir = path.dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);

function takeOption(name) {
  const index = args.indexOf(name);
  if (index < 0) return undefined;
  const value = args[index + 1];
  if (!value) throw new Error(`Missing value for ${name}`);
  args.splice(index, 2);
  return value;
}

function takeFlag(name) {
  const index = args.indexOf(name);
  if (index < 0) return false;
  args.splice(index, 1);
  return true;
}

const targetArg = takeOption("--target") ?? takeOption("-t");
const includeReadme = takeFlag("--include-readme");
const skipGitignore = takeFlag("--skip-gitignore");
const dryRun = takeFlag("--dry-run");
const help = takeFlag("--help") || takeFlag("-h");

if (help) {
  console.log(`Usage: node copy-scaffold.mjs --target <repo> [--include-readme] [--skip-gitignore] [--dry-run]

Copies the portable AI scaffold into a target repository while excluding runtime/cache outputs such as node_modules, dist, .omx, and temporary worktrees.

By default README.md is copied only when the target repo does not already have one.`);
  process.exit(0);
}

if (args.length > 0) {
  throw new Error(`Unexpected arguments: ${args.join(" ")}`);
}
if (!targetArg) {
  throw new Error("Missing --target <repo>.");
}

const targetDir = path.resolve(process.cwd(), targetArg);
if (path.resolve(targetDir) === path.resolve(scaffoldDir)) {
  throw new Error("Refusing to copy scaffold onto itself.");
}

const includeRoots = [
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

const runtimeIgnoreBlock = `# AI scaffold runtime and generated/cache outputs
.omx/
.codex/tasks/tmp/
.codex/worktrees/*
!.codex/worktrees/README.md
tools/ai-scaffold/node_modules/
tools/ai-scaffold/dist/
`;

function toPosix(value) {
  return value.split(path.sep).join("/");
}

function shouldSkip(relativePath, isDirectory) {
  const rel = toPosix(relativePath);
  if (!rel || rel === ".") return false;
  const segments = rel.split("/");
  if (segments.includes("node_modules") || segments.includes("dist")) return true;
  if (segments.includes(".git")) return true;
  if (rel === ".omx" || rel.startsWith(".omx/")) return true;
  if (rel === "_temp" || rel.startsWith("_temp/")) return true;
  if (rel === ".codex/tasks/tmp" || rel.startsWith(".codex/tasks/tmp/")) return true;
  if (rel.startsWith(".codex/worktrees/") && rel !== ".codex/worktrees/README.md") return true;
  if (/^docs\/features\/F\d{3}-/u.test(rel)) return true;
  if (rel === ".DS_Store" || rel.endsWith("/.DS_Store") || rel.endsWith("/Thumbs.db")) return true;
  return false;
}

function copyPath(relativeRoot) {
  const source = path.join(scaffoldDir, relativeRoot);
  if (!fs.existsSync(source)) return;
  const stat = fs.statSync(source);
  if (shouldSkip(relativeRoot, stat.isDirectory())) return;

  if (stat.isDirectory()) {
    const entries = fs.readdirSync(source, { withFileTypes: true });
    for (const entry of entries) {
      copyPath(path.join(relativeRoot, entry.name));
    }
    return;
  }

  if (relativeRoot === "README.md") {
    const targetReadme = path.join(targetDir, "README.md");
    if (!includeReadme && fs.existsSync(targetReadme)) {
      console.log(`skip existing README.md (use --include-readme to overwrite)`);
      return;
    }
  }

  const destination = path.join(targetDir, relativeRoot);
  console.log(`${dryRun ? "would copy" : "copy"} ${toPosix(relativeRoot)}`);
  if (dryRun) return;
  fs.mkdirSync(path.dirname(destination), { recursive: true });
  fs.copyFileSync(source, destination);
}

function updateGitignore() {
  if (skipGitignore) return;
  const gitignorePath = path.join(targetDir, ".gitignore");
  const marker = "# AI scaffold runtime and generated/cache outputs";
  const existing = fs.existsSync(gitignorePath) ? fs.readFileSync(gitignorePath, "utf8") : "";
  if (existing.includes(marker)) {
    console.log("gitignore already contains AI scaffold runtime block");
    return;
  }
  console.log(`${dryRun ? "would update" : "update"} .gitignore`);
  if (dryRun) return;
  fs.mkdirSync(path.dirname(gitignorePath), { recursive: true });
  const prefix = existing.trimEnd();
  fs.writeFileSync(gitignorePath, `${prefix}${prefix ? "\n\n" : ""}${runtimeIgnoreBlock}`, "utf8");
}

if (!dryRun) {
  fs.mkdirSync(targetDir, { recursive: true });
}

for (const relativeRoot of includeRoots) {
  copyPath(relativeRoot);
}
copyPath("README.md");
updateGitignore();

console.log("");
console.log("AI scaffold copy complete.");
console.log(`Target: ${targetDir}`);
console.log("Next: edit ai-scaffold.config.json, AGENTS.md, and project.md; then run npm --prefix tools/ai-scaffold ci && npm --prefix tools/ai-scaffold run build && node tools/ai-scaffold/dist/cli.js doctor");
