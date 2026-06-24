import path from "node:path";
import { assertNoExtraArgs, takeFlag, takeOption } from "../utils/cli";
import { runCapture } from "../utils/exec";
import { loadScaffoldConfig, normalizeRoot } from "../config/scaffold-config";

type AddedSurface = {
  kind: "table" | "api" | "permission" | "config";
  value: string;
  file: string;
};

const CREATE_TABLE_PATTERN = /\bCREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?\s+([a-zA-Z_][\w]*(?:\.[a-zA-Z_][\w]*)?)/giu;
const API_PATTERN = /\/api\/v[0-9]+\/[A-Za-z0-9_./{}:-]+/gu;
const PERMISSION_PATTERN = /(?:^|[^A-Za-z0-9_:])((?:system|inbound|outbound|pda|platform|config|wms):[A-Za-z0-9:_-]+)/gu;
const CONFIG_KEY_PATTERN = /(?:^|[^A-Za-z0-9_.-])((?:receiving|workflow|business|fifo|batch|expiry|quality|pda|auth)\.[A-Za-z0-9_.-]+)/gu;

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

function normalizePath(value: string): string {
  return value.split(path.sep).join("/");
}

function diffRange(base: string, head: string, rangeMode: string): string {
  if (rangeMode === "three-dot") {
    return `${base}...${head}`;
  }
  if (rangeMode === "two-dot") {
    return `${base}..${head}`;
  }
  throw new Error(`Unsupported --range-mode: ${rangeMode}`);
}

function parseAddedSurfaces(diffText: string): AddedSurface[] {
  const surfaces: AddedSurface[] = [];
  let currentFile = "";

  for (const line of diffText.split(/\r?\n/u)) {
    if (line.startsWith("+++ b/")) {
      currentFile = line.slice("+++ b/".length);
      continue;
    }
    if (!line.startsWith("+") || line.startsWith("+++")) {
      continue;
    }

    const added = line.slice(1);
    for (const match of added.matchAll(CREATE_TABLE_PATTERN)) {
      surfaces.push({ kind: "table", value: match[1].toLowerCase(), file: currentFile });
    }
    for (const match of added.matchAll(API_PATTERN)) {
      surfaces.push({ kind: "api", value: match[0], file: currentFile });
    }
    for (const match of added.matchAll(PERMISSION_PATTERN)) {
      surfaces.push({ kind: "permission", value: match[1], file: currentFile });
    }
    for (const match of added.matchAll(CONFIG_KEY_PATTERN)) {
      surfaces.push({ kind: "config", value: match[1], file: currentFile });
    }
  }

  return surfaces;
}

function gitGrep(repoRoot: string, value: string): string[] {
  const result = runCapture("git", ["grep", "-n", "-F", value, "--", "."], { cwd: repoRoot });
  if (result.status !== 0) {
    return [];
  }
  return result.stdout.split(/\r?\n/u).filter(Boolean);
}

function isOutsideChangedFiles(matchLine: string, changedFiles: Set<string>): boolean {
  const filePath = normalizePath(matchLine.split(":", 1)[0] ?? "");
  return !changedFiles.has(filePath);
}

export async function checkReuseDuplicationCommand(args: string[], context: { repoRoot: string }): Promise<void> {
  const useStdin = takeFlag(args, "--stdin");
  const base = takeOption(args, "--base");
  const head = takeOption(args, "--head") ?? "HEAD";
  const rangeMode = takeOption(args, "--range-mode") ?? "two-dot";
  assertNoExtraArgs(args);

  if (!useStdin) {
    throw new Error("ERROR: check-reuse-duplication requires --stdin with newline-delimited changed files.");
  }
  if (!base) {
    throw new Error("ERROR: check-reuse-duplication requires --base.");
  }

  const config = loadScaffoldConfig(context.repoRoot);
  const reviewRoots = [
    ...config.codeLikeRoots,
    config.featureRoot,
    config.bugfixRoot,
  ].map(normalizeRoot);
  const changedFiles = (await readStdinLines()).map(normalizePath).filter((file) => reviewRoots.some((root) => file.startsWith(root)));
  if (changedFiles.length === 0) {
    console.log("Reuse duplication check passed: no relevant changed files.");
    return;
  }

  const result = runCapture("git", ["diff", "--unified=0", "--no-ext-diff", diffRange(base, head, rangeMode), "--", ...changedFiles], {
    cwd: context.repoRoot,
  });
  if (result.status !== 0) {
    throw new Error(`Unable to inspect changed surfaces:\n${result.stderr || result.stdout}`);
  }

  const changedSet = new Set(changedFiles);
  const surfaces = parseAddedSurfaces(result.stdout);
  const hardFailures: string[] = [];
  const warnings: string[] = [];

  for (const surface of surfaces) {
    const matches = gitGrep(context.repoRoot, surface.value);
    let outsideMatches = matches.filter((matchLine) => isOutsideChangedFiles(matchLine, changedSet));
    if (surface.kind === "table") {
      outsideMatches = outsideMatches.filter((matchLine) => /\bCREATE\s+TABLE\b/iu.test(matchLine));
    }
    if (outsideMatches.length === 0) {
      continue;
    }

    const message = `${surface.kind} "${surface.value}" added in ${surface.file} already appears outside this change.`;
    if (surface.kind === "table") {
      hardFailures.push(`${message}\n${outsideMatches.slice(0, 5).join("\n")}`);
    } else {
      warnings.push(`${message}\n${outsideMatches.slice(0, 5).join("\n")}`);
    }
  }

  for (const warning of warnings) {
    console.warn(`WARNING: ${warning}`);
  }

  if (hardFailures.length > 0) {
    throw new Error(`Potential duplicate SQL table surface detected:\n\n${hardFailures.join("\n\n")}`);
  }

  console.log("Reuse duplication check passed.");
}
