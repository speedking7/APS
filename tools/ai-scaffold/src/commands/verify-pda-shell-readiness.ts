import path from "node:path";
import { assertNoExtraArgs } from "../utils/cli";
import { ensureDir, writeLines } from "../utils/fs";
import { runChecked } from "../utils/exec";
import { pdaShellHandoffCommand } from "./pda-shell-handoff";
import { expandCommandArgs, loadScaffoldConfig, resolveCommand, resolveConfigPath } from "../config/scaffold-config";

export async function verifyPdaShellReadinessCommand(args: string[], context: { repoRoot: string }): Promise<void> {
  assertNoExtraArgs(args);
  const config = loadScaffoldConfig(context.repoRoot);
  if (!config.pdaShell?.frontendPath) {
    console.log("No pdaShell.frontendPath configured in ai-scaffold.config.json; skipping PDA shell readiness verification.");
    return;
  }

  const frontendDir = resolveConfigPath(context.repoRoot, config.pdaShell.frontendPath);
  const reportDir = resolveConfigPath(context.repoRoot, path.join(config.pdaShell.reportFeatureDir ?? config.featureRoot, "reports"));
  ensureDir(reportDir);
  const timestamp = new Date().toISOString().replace(/[:T]/gu, "").slice(0, 13);
  const reportPath = path.join(reportDir, `verify-pda-shell-readiness-${timestamp}.txt`);
  const reportLines: string[] = [`PDA shell readiness verification - ${timestamp}`, ""];

  console.log("Verifying PDA shell handoff artifacts...");
  reportLines.push("Verifying PDA shell handoff artifacts...");
  await pdaShellHandoffCommand([], context);

  console.log("");
  console.log("Running PDA frontend shell-readiness test suite...");
  reportLines.push("", "Running PDA frontend shell-readiness test suite...");
  const testCommand = config.pdaShell.testCommand ?? { command: "npx", args: ["vitest", "run"] };
  runChecked(resolveCommand(testCommand.command), expandCommandArgs(testCommand.args, {}), {
    cwd: frontendDir,
    errorMessage: "vitest verification failed",
  });
  reportLines.push("Vitest: PASS");

  console.log("");
  console.log("Running TypeScript diagnostics...");
  reportLines.push("TypeScript diagnostics: running");
  const typecheckCommand = config.pdaShell.typecheckCommand ?? { command: "npx", args: ["tsc", "--noEmit", "--pretty", "false", "--project", "tsconfig.json"] };
  runChecked(resolveCommand(typecheckCommand.command), expandCommandArgs(typecheckCommand.args, {}), {
    cwd: frontendDir,
    errorMessage: "TypeScript diagnostics failed",
  });
  reportLines.push("TypeScript diagnostics: PASS");

  console.log("");
  console.log("PDA shell readiness verification completed.");
  reportLines.push("", "Result: PASS", `Report path: ${reportPath}`);
  writeLines(reportPath, reportLines);
}
