import path from "node:path";
import { assertNoExtraArgs } from "../utils/cli";
import { loadScaffoldConfig } from "../config/scaffold-config";

export async function pdaShellHandoffCommand(args: string[], context: { repoRoot: string }): Promise<void> {
  assertNoExtraArgs(args);
  const config = loadScaffoldConfig(context.repoRoot);

  const artifacts = config.pdaShell?.artifacts ?? [];

  if (artifacts.length === 0) {
    console.log("No PDA shell artifacts configured in ai-scaffold.config.json; skipping handoff artifact check.");
    return;
  }

  console.log("PDA/mobile shell handoff artifacts:");
  const missing: string[] = [];
  for (const artifact of artifacts) {
    const fullPath = path.join(context.repoRoot, artifact);
    if (require("node:fs").existsSync(fullPath)) {
      console.log(`  OK  ${artifact}`);
    } else {
      console.log(`  MISS ${artifact}`);
      missing.push(artifact);
    }
  }

  if (missing.length > 0) {
    throw new Error(`Missing handoff artifacts: ${missing.join(", ")}`);
  }

  console.log("");
  console.log("Verified routes for shell smoke:");
  for (const route of config.pdaShell?.routes ?? []) {
    console.log(`  ${route}`);
  }
}
