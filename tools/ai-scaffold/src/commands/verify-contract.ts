import fs from "node:fs";
import path from "node:path";
import { assertNoExtraArgs } from "../utils/cli";
import { getContractStatus } from "./gate";
import { resolveRepoPath } from "../utils/paths";
import { loadScaffoldConfig, resolveConfigPath } from "../config/scaffold-config";

export async function verifyContractCommand(args: string[], context: { repoRoot: string }): Promise<void> {
  const featureDir = args[0];
  const config = loadScaffoldConfig(context.repoRoot);
  const featuresDir = resolveConfigPath(context.repoRoot, config.featureRoot);
  const contractFiles: string[] = [];

  if (featureDir) {
    assertNoExtraArgs(args.slice(1));
    const resolved = resolveRepoPath(context.repoRoot, featureDir);
    const contractFile = path.join(resolved, "contract.md");
    if (!fs.existsSync(contractFile)) {
      throw new Error(`ERROR: Missing contract.md: ${contractFile}`);
    }
    contractFiles.push(contractFile);
  } else {
    for (const entry of fs.readdirSync(featuresDir, { withFileTypes: true })) {
      if (!entry.isDirectory() || !/^F\d{3}-/u.test(entry.name)) continue;
      const contractFile = path.join(featuresDir, entry.name, "contract.md");
      if (fs.existsSync(contractFile)) {
        contractFiles.push(contractFile);
      }
    }
  }

  if (contractFiles.length === 0) {
    console.log(`No contract.md under ${featuresDir}.`);
    return;
  }

  console.log("=== Contract Verification ===");
  console.log("");
  console.log("Checking contract status ...");
  let errors = 0;
  for (const contractFile of contractFiles) {
    const filename = `${path.basename(path.dirname(contractFile))}/${path.basename(contractFile)}`;
    const status = getContractStatus(contractFile);
    if (!status) {
      console.log(`  ! ${filename} - No status field found`);
      errors += 1;
      continue;
    }
    if (status === "FROZEN") {
      console.log(`  + ${filename} - FROZEN (ready for development)`);
      continue;
    }
    if (status === "IMPLEMENTED") {
      console.log(`  + ${filename} - IMPLEMENTED (completed)`);
      continue;
    }
    if (status === "DRAFT") {
      console.log(`  x ${filename} - DRAFT (not ready for development)`);
      errors += 1;
      continue;
    }
    if (status === "REVIEW") {
      console.log(`  ! ${filename} - REVIEW (pending approval)`);
      errors += 1;
      continue;
    }
    console.log(`  ! ${filename} - Unknown status: ${status}`);
    errors += 1;
  }
  console.log("");
  console.log("=== Summary ===");
  console.log(`Total contracts: ${contractFiles.length}`);
  console.log(`Ready for development: ${contractFiles.length - errors}`);
  console.log(`Not ready: ${errors}`);
  if (errors > 0) {
    throw new Error("ERROR: Some contracts are not ready for development.");
  }
  console.log("");
  console.log("All checked contracts are ready for development.");
}
