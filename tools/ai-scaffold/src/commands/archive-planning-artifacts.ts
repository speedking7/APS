import fs from "node:fs";
import path from "node:path";
import { assertNoExtraArgs, takeOption } from "../utils/cli";
import { ensureDir, readText, writeText } from "../utils/fs";
import { resolveRepoPath } from "../utils/paths";

export async function archivePlanningArtifactsCommand(args: string[], context: { repoRoot: string }): Promise<void> {
  const featureDirArg = args[0];
  if (!featureDirArg) {
    throw new Error("❌ 请传入功能目录路径，例如 docs/features/F024-inspection-attribute-and-split-rules");
  }
  const stage = takeOption(args, "--stage") ?? "all";
  assertNoExtraArgs(args.slice(1));

  if (!["all", "deep-interview", "ralplan"].includes(stage)) {
    throw new Error(`❌ --stage 仅支持 all、deep-interview、ralplan。实际值: ${stage}`);
  }

  const featureDir = resolveRepoPath(context.repoRoot, featureDirArg);
  if (!fs.existsSync(featureDir)) {
    throw new Error(`❌ 功能目录不存在: ${featureDir}`);
  }

  const featureDirName = path.basename(featureDir);
  const match = /^F\d{3}-(.+)$/u.exec(featureDirName);
  if (!match) {
    throw new Error(`❌ 功能目录名不符合约定: ${featureDirName}`);
  }

  const featureSlug = match[1]!;
  const reportDir = path.join(featureDir, "reports", "planning");
  ensureDir(reportDir);

  const sourceEntries = getSourceEntries(context.repoRoot, reportDir, featureSlug, stage);

  const missing = sourceEntries.filter(([sourcePath]) => !fs.existsSync(sourcePath)).map(([sourcePath]) => sourcePath);
  if (missing.length > 0) {
    throw new Error(
      `❌ 缺少可归档的 OMX 规划产物:\n${missing.map((filePath) => ` - ${path.relative(context.repoRoot, filePath).split(path.sep).join("/")}`).join("\n")}`,
    );
  }

  const interviewTranscript = findLatestInterviewTranscript(context.repoRoot, featureSlug);
  for (const [sourcePath, destinationPath] of sourceEntries) {
    const sourceRelative = path.relative(context.repoRoot, sourcePath).split(path.sep).join("/");
    let content = readText(sourcePath).trimEnd();
    const metadataLines = [
      `> Archived by \`node tools/ai-scaffold/dist/cli.js archive-planning-artifacts --stage ${stage}\`.`,
      `> Source: \`${sourceRelative}\``,
    ];
    if (destinationPath.endsWith(`${path.sep}deep-interview.md`) && interviewTranscript) {
      metadataLines.push(`> Interview transcript: \`${interviewTranscript}\``);
    }
    content = `${metadataLines.join("\n")}\n\n${content}\n`;
    writeText(destinationPath, content);
  }

  console.log(`Archived planning artifacts for ${featureDirName} (stage: ${stage})`);
  for (const [, destinationPath] of sourceEntries) {
    console.log(` - ${path.relative(context.repoRoot, destinationPath).split(path.sep).join("/")}`);
  }
}

function findLatestInterviewTranscript(repoRoot: string, featureSlug: string): string | undefined {
  const interviewDir = path.join(repoRoot, ".omx", "interviews");
  if (!fs.existsSync(interviewDir)) {
    return undefined;
  }

  const matches = fs
    .readdirSync(interviewDir, { withFileTypes: true })
    .filter((entry) => entry.isFile() && entry.name.endsWith(".md") && entry.name.includes(featureSlug))
    .map((entry) => path.join(interviewDir, entry.name))
    .sort((left, right) => fs.statSync(right).mtimeMs - fs.statSync(left).mtimeMs);

  if (matches.length === 0) {
    return undefined;
  }

  return path.relative(repoRoot, matches[0]!).split(path.sep).join("/");
}

function getSourceEntries(repoRoot: string, reportDir: string, featureSlug: string, stage: string): Array<[string, string]> {
  if (stage === "deep-interview") {
    return [[path.join(repoRoot, ".omx", "specs", `deep-interview-${featureSlug}.md`), path.join(reportDir, "deep-interview.md")]];
  }

  if (stage === "ralplan") {
    return [
      [path.join(repoRoot, ".omx", "plans", `prd-${featureSlug}.md`), path.join(reportDir, "prd.md")],
      [path.join(repoRoot, ".omx", "plans", `test-spec-${featureSlug}.md`), path.join(reportDir, "test-spec.md")],
    ];
  }

  return [
    [path.join(repoRoot, ".omx", "specs", `deep-interview-${featureSlug}.md`), path.join(reportDir, "deep-interview.md")],
    [path.join(repoRoot, ".omx", "plans", `prd-${featureSlug}.md`), path.join(reportDir, "prd.md")],
    [path.join(repoRoot, ".omx", "plans", `test-spec-${featureSlug}.md`), path.join(reportDir, "test-spec.md")],
  ];
}
