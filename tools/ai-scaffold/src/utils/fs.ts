import fs from "node:fs";
import path from "node:path";

export function ensureDir(targetPath: string): void {
  fs.mkdirSync(targetPath, { recursive: true });
}

export function readText(targetPath: string): string {
  return fs.readFileSync(targetPath, "utf8");
}

export function writeText(targetPath: string, content: string): void {
  ensureDir(path.dirname(targetPath));
  fs.writeFileSync(targetPath, content, "utf8");
}

export function writeLines(targetPath: string, lines: string[]): void {
  writeText(targetPath, lines.join("\n") + "\n");
}

export function copyDir(sourceDir: string, targetDir: string): void {
  fs.cpSync(sourceDir, targetDir, {
    force: true,
    recursive: true,
  });
}

export function removeIfExists(targetPath: string): void {
  if (!fs.existsSync(targetPath)) {
    return;
  }

  fs.rmSync(targetPath, { force: true, recursive: true });
}

export function listFilesRecursive(rootDir: string, predicate?: (fullPath: string) => boolean): string[] {
  if (!fs.existsSync(rootDir)) {
    return [];
  }

  const results: string[] = [];
  const queue = [rootDir];
  while (queue.length > 0) {
    const current = queue.shift()!;
    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      const fullPath = path.join(current, entry.name);
      if (entry.isDirectory()) {
        queue.push(fullPath);
        continue;
      }

      if (!predicate || predicate(fullPath)) {
        results.push(fullPath);
      }
    }
  }

  return results;
}
