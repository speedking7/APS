import fs from "node:fs";
import os from "node:os";
import { assertNoExtraArgs, takeFlag } from "../utils/cli";
import { ensureFrontendDependencies, describeFrontendDependencyHealth } from "../utils/frontend-deps";
import { runCapture } from "../utils/exec";
import { loadScaffoldConfig, resolveConfigPath } from "../config/scaffold-config";

type RuntimeKind = "windows-native" | "wsl" | "linux" | "macos" | "other";

export async function doctorCommand(args: string[], context: { repoRoot: string }): Promise<void> {
  const fixFrontendDeps = takeFlag(args, "--fix-frontend-deps");
  const asJson = takeFlag(args, "--json");
  assertNoExtraArgs(args);

  const config = loadScaffoldConfig(context.repoRoot);
  const runtimeKind = detectRuntimeKind();
  const frontendDirs = config.frontends
    .map((frontend) => ({ dir: resolveConfigPath(context.repoRoot, frontend.path), label: frontend.path }))
    .filter((entry) => fs.existsSync(entry.dir));

  const commandChecks = [
    "git",
    "node",
    "npm",
    ...commandNamesFromConfig(config),
    "docker",
  ]
    .filter((name, index, list) => list.indexOf(name) === index)
    .map((name) => ({
      name,
      ok: commandExists(name),
    }));

  const frontendChecks = frontendDirs.map((frontendDir) => {
    if (fixFrontendDeps) {
      ensureFrontendDependencies(frontendDir.dir, { label: frontendDir.label });
    }
    return {
      dir: frontendDir.label,
      ...describeFrontendDependencyHealth(frontendDir.dir),
    };
  });

  const report = {
    repoRoot: context.repoRoot,
    runtime: {
      kind: runtimeKind,
      node: process.version,
      platform: process.platform,
      arch: process.arch,
      shell: process.env.SHELL ?? process.env.ComSpec ?? "unknown",
      cwd: process.cwd(),
      hostname: os.hostname(),
    },
    scaffold: {
      projectName: config.projectName,
      featureRoot: config.featureRoot,
      bugfixRoot: config.bugfixRoot,
      backendPath: config.backend.path,
      frontendPaths: config.frontends.map((frontend) => frontend.path),
      codeLikeRoots: config.codeLikeRoots,
    },
    commands: commandChecks,
    frontends: frontendChecks,
    recommendations: buildRecommendations(runtimeKind, commandChecks, frontendChecks),
  };

  if (asJson) {
    console.log(JSON.stringify(report, null, 2));
    return;
  }

  console.log("=== Scaffold Doctor ===");
  console.log(`Repo root : ${report.repoRoot}`);
  console.log(`Runtime   : ${report.runtime.kind}`);
  console.log(`Node      : ${report.runtime.node}`);
  console.log(`Platform  : ${report.runtime.platform}/${report.runtime.arch}`);
  console.log(`Shell     : ${report.runtime.shell}`);
  console.log(`CWD       : ${report.runtime.cwd}`);
  console.log(`Project   : ${report.scaffold.projectName}`);
  console.log(`Feature   : ${report.scaffold.featureRoot}`);
  console.log(`Bugfix    : ${report.scaffold.bugfixRoot}`);
  console.log(`Backend   : ${report.scaffold.backendPath}`);
  console.log(`Frontends : ${report.scaffold.frontendPaths.join(", ") || "(none)"}`);
  console.log("");
  console.log("Commands:");
  for (const entry of report.commands) {
    console.log(`- ${entry.name}: ${entry.ok ? "OK" : "MISSING"}`);
  }
  console.log("");
  console.log("Frontend dependency health:");
  for (const entry of report.frontends) {
    console.log(`- ${entry.dir}: ${entry.ok ? "OK" : "NOT_READY"}${entry.reason ? ` (${entry.reason})` : ""}`);
  }
  console.log("");
  console.log("Recommendations:");
  for (const recommendation of report.recommendations) {
    console.log(`- ${recommendation}`);
  }
}

function detectRuntimeKind(): RuntimeKind {
  if (process.platform === "win32") {
    return "windows-native";
  }
  if (process.platform === "darwin") {
    return "macos";
  }
  if (process.platform === "linux") {
    if (process.env.WSL_DISTRO_NAME || process.env.WSL_INTEROP) {
      return "wsl";
    }
    try {
      const procVersion = fs.readFileSync("/proc/version", "utf8");
      if (/microsoft|wsl/iu.test(procVersion)) {
        return "wsl";
      }
    } catch {
      // ignore
    }
    return "linux";
  }
  return "other";
}

function commandExists(name: string): boolean {
  const locator = process.platform === "win32" ? "where" : "which";
  const result = runCapture(locator, [name], { cwd: process.cwd() });
  return result.status === 0;
}

function buildRecommendations(
  runtimeKind: RuntimeKind,
  commandChecks: Array<{ name: string; ok: boolean }>,
  frontendChecks: Array<{ dir: string; ok: boolean; reason: string }>,
): string[] {
  const recommendations: string[] = [];

  const missingCommands = commandChecks.filter((entry) => !entry.ok).map((entry) => entry.name);
  if (missingCommands.length > 0) {
    recommendations.push(`Install missing commands before running full gates: ${missingCommands.join(", ")}`);
  } else {
    recommendations.push("Required command-line dependencies look available.");
  }

  const brokenFrontends = frontendChecks.filter((entry) => !entry.ok);
  if (brokenFrontends.length > 0) {
    recommendations.push(
      `Run 'node tools/ai-scaffold/dist/cli.js ensure-frontend-deps' before frontend lint/test/build. Problem dirs: ${brokenFrontends
        .map((entry) => entry.dir)
        .join(", ")}`,
    );
  } else {
    recommendations.push("Frontend dependency installs look healthy for the current runtime.");
  }

  if (runtimeKind === "wsl") {
    recommendations.push(
      "WSL is supported, but full `gate` runs can be slower than Windows native. Prefer `ensure-frontend-deps` first and use focused commands when diagnosing failures.",
    );
  } else if (runtimeKind === "windows-native") {
    recommendations.push("Windows native is a recommended runtime for the full repository gate on this machine.");
  } else {
    recommendations.push("Use `node tools/ai-scaffold/dist/cli.js gate` as the primary cross-platform validation entrypoint.");
  }

  return recommendations;
}

function commandNamesFromConfig(config: ReturnType<typeof loadScaffoldConfig>): string[] {
  const names: string[] = [];
  const push = (command?: string) => {
    if (command) names.push(command);
  };
  for (const spec of Object.values(config.backend.commands ?? {})) {
    push(spec?.command);
  }
  for (const frontend of config.frontends) {
    for (const spec of Object.values(frontend.commands ?? {})) {
      push(spec?.command);
    }
  }
  push(config.manualTest?.backendCommand?.command);
  push(config.manualTest?.frontendPreviewCommand?.command);
  push(config.manualTest?.frontendDevCommand?.command);
  push(config.manualTest?.playwrightInstallCommand?.command);
  push(config.pdaShell?.testCommand?.command);
  push(config.pdaShell?.typecheckCommand?.command);
  return names;
}
