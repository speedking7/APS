import fs from "node:fs";
import path from "node:path";

export function getRepoRoot(): string {
  return path.resolve(__dirname, "..", "..", "..", "..");
}

export function resolveRepoPath(repoRoot: string, value: string): string {
  if (path.isAbsolute(value)) {
    return path.resolve(value);
  }

  return path.resolve(repoRoot, value);
}

export function ensureExists(targetPath: string, label?: string): void {
  if (!fs.existsSync(targetPath)) {
    throw new Error(`Missing ${label ?? "path"}: ${targetPath}`);
  }
}

export function toPosixRelative(repoRoot: string, targetPath: string): string {
  return path.relative(repoRoot, targetPath).split(path.sep).join("/");
}
