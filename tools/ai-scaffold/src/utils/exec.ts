import { spawn, spawnSync, SpawnSyncOptionsWithStringEncoding } from "node:child_process";
import path from "node:path";

function normalizeExecutable(filePath: string): string {
  if (process.platform === "win32") {
    if (filePath === "npm") {
      return "npm.cmd";
    }
    if (filePath === "npx") {
      return "npx.cmd";
    }
    if (filePath === "mvn") {
      return "mvn.cmd";
    }
  }

  return filePath;
}

function shouldUseShell(command: string): boolean {
  return process.platform === "win32" && /\.(cmd|bat)$/iu.test(command);
}

export function runChecked(
  filePath: string,
  args: string[],
  options: {
    cwd: string;
    env?: NodeJS.ProcessEnv;
    errorMessage: string;
  },
): void {
  const command = normalizeExecutable(filePath);
  const result = spawnSync(command, args, {
    cwd: options.cwd,
    env: options.env ?? process.env,
    stdio: "inherit",
    shell: shouldUseShell(command),
  });

  if (result.error) {
    throw result.error;
  }

  if (result.status !== 0) {
    throw new Error(options.errorMessage);
  }
}

export function runCapture(
  filePath: string,
  args: string[],
  options: {
    cwd: string;
    env?: NodeJS.ProcessEnv;
  },
): { status: number | null; stdout: string; stderr: string } {
  const command = normalizeExecutable(filePath);
  const spawnOptions: SpawnSyncOptionsWithStringEncoding = {
    cwd: options.cwd,
    encoding: "utf8",
    env: options.env ?? process.env,
    shell: shouldUseShell(command),
  };
  const result = spawnSync(command, args, spawnOptions);
  return {
    status: result.status,
    stdout: result.stdout ?? "",
    stderr: result.stderr ?? "",
  };
}

export function quotedCommand(filePath: string, args: string[]): string {
  return [normalizeExecutable(filePath), ...args]
    .map((part) => {
      if (/[\s"]/u.test(part)) {
        return `"${part.replace(/"/gu, '\\"')}"`;
      }

      return part;
    })
    .join(" ");
}

export function repoNodeCommand(repoRoot: string, scriptRelativePath: string, args: string[]): { filePath: string; args: string[] } {
  return {
    filePath: process.execPath,
    args: [path.join(repoRoot, scriptRelativePath), ...args],
  };
}

export function startDetachedProcess(
  filePath: string,
  args: string[],
  options: {
    cwd: string;
    env?: NodeJS.ProcessEnv;
    stdoutPath?: string;
    stderrPath?: string;
  },
): number {
  const command = normalizeExecutable(filePath);
  const fs = require("node:fs") as typeof import("node:fs");
  const stdout = options.stdoutPath ? fs.openSync(options.stdoutPath, "a") : "ignore";
  const stderr = options.stderrPath ? fs.openSync(options.stderrPath, "a") : "ignore";
  const child = spawn(command, args, {
    cwd: options.cwd,
    env: options.env ?? process.env,
    detached: true,
    stdio: ["ignore", stdout, stderr],
    shell: shouldUseShell(command),
    windowsHide: true,
  });
  child.unref();
  return child.pid ?? 0;
}
