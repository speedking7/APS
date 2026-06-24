import path from "node:path";
import { assertNoExtraArgs, requireOption } from "../utils/cli";
import { ensureExists } from "../utils/paths";
import { ensureDir, readText, writeText } from "../utils/fs";
import { loadScaffoldConfig, resolveConfigPath } from "../config/scaffold-config";

export async function initFeatureCommand(args: string[], context: { repoRoot: string }): Promise<void> {
  const slug = requireOption(args, "--slug");
  const title = requireOption(args, "--title");
  assertNoExtraArgs(args);

  const config = loadScaffoldConfig(context.repoRoot);
  const featuresRoot = resolveConfigPath(context.repoRoot, config.featureRoot);
  const nextNumberFile = path.join(featuresRoot, "NEXT_FEATURE_NUMBER.txt");
  ensureExists(nextNumberFile, "NEXT_FEATURE_NUMBER.txt");

  const templateMap = new Map<string, string>([
    ["plan.md", path.join(featuresRoot, "plan-template.md")],
    ["TASK.md", path.join(featuresRoot, "TASK-template.md")],
    ["contract.md", path.join(featuresRoot, "contract-template.md")],
    ["test-plan.md", path.join(featuresRoot, "test-plan-template.md")],
  ]);

  for (const templatePath of templateMap.values()) {
    ensureExists(templatePath, "template");
  }

  const rawNumber = readText(nextNumberFile).trim();
  if (!/^\d+$/u.test(rawNumber)) {
    throw new Error(`NEXT_FEATURE_NUMBER.txt must contain an integer. Actual: '${rawNumber}'`);
  }

  const featureNumber = Number.parseInt(rawNumber, 10);
  const featureCode = `F${featureNumber.toString().padStart(3, "0")}`;
  const featureDirName = `${featureCode}-${slug}`;
  const featureDir = path.join(featuresRoot, featureDirName);
  if (pathExists(featureDir)) {
    throw new Error(`Feature directory already exists: ${featureDir}`);
  }

  ensureDir(featureDir);
  const reportsDir = path.join(featureDir, "reports");
  const planningReportsDir = path.join(reportsDir, "planning");
  const sqlDir = path.join(featureDir, "sql");
  ensureDir(reportsDir);
  ensureDir(planningReportsDir);
  ensureDir(sqlDir);

  const replacements = new Map<string, string>([
    ["{nnn}", featureNumber.toString().padStart(3, "0")],
    ["{slug}", slug],
    ["{feature-slug}", slug],
    ["{feature-name}", title],
    ["{feature-title}", title],
    ["{id}", featureCode],
    ["{agent-name}", "codex"],
    ["YYYY-MM-DD", new Date().toISOString().slice(0, 10)],
  ]);

  const renderedTemplates = new Map<string, string>();
  for (const [destinationName, templatePath] of templateMap) {
    let content = readText(templatePath);
    for (const [needle, replacement] of replacements) {
      content = content.split(needle).join(replacement);
    }
    const unresolvedTokens = [...replacements.keys()].filter((token) => content.includes(token));
    if (unresolvedTokens.length > 0) {
      throw new Error(
        `Template still contains unresolved scaffold token(s): ${unresolvedTokens.join(", ")}. Template: ${templatePath}`,
      );
    }
    renderedTemplates.set(destinationName, content);
  }

  for (const [destinationName, content] of renderedTemplates) {
    writeText(path.join(featureDir, destinationName), content);
  }

  const nextValue = String(featureNumber + 1);
  writeText(nextNumberFile, `${nextValue}\n`);

  console.log(`Created feature scaffold: ${featureDirName}`);
  console.log(`Next feature number: ${nextValue}`);
  console.log(`Remember to store feature SQL and test-data SQL under: ${sqlDir}`);
  console.log(`Planning artifacts should be archived under: ${planningReportsDir}`);
  console.log("Review and approve plan.md before implementation.");
}

function pathExists(targetPath: string): boolean {
  try {
    ensureExists(targetPath);
    return true;
  } catch {
    return false;
  }
}
