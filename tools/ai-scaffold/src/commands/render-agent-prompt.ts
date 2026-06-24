import fs from "node:fs";
import path from "node:path";
import { assertNoExtraArgs, requireOption, takeFlag, takeOption } from "../utils/cli";
import { readText, writeText } from "../utils/fs";

export type LoadedSkill = {
  name: string;
  sourcePath: string;
  content: string;
};

export type RenderedAgentPrompt = {
  role: string;
  agentPath: string;
  skillNames: string[];
  loadedSkills: LoadedSkill[];
  prompt: string;
};

type RenderAgentPromptInput = {
  role: string;
  task?: string;
  featureDir?: string;
  worktreePath?: string;
};

export async function renderAgentPromptCommand(args: string[], context: { repoRoot: string }): Promise<void> {
  const role = requireOption(args, "--role");
  const task = takeOption(args, "--task");
  const featureDir = takeOption(args, "--feature-dir");
  const worktreePath = takeOption(args, "--worktree");
  const outputPath = takeOption(args, "--output");
  const summary = takeFlag(args, "--summary");
  assertNoExtraArgs(args);

  const rendered = renderAgentPrompt(context.repoRoot, {
    role,
    task,
    featureDir,
    worktreePath,
  });

  if (outputPath) {
    writeText(path.resolve(context.repoRoot, outputPath), rendered.prompt);
  }

  if (summary) {
    printRenderedSummary(context.repoRoot, rendered, outputPath);
    return;
  }

  process.stdout.write(rendered.prompt);
  if (!rendered.prompt.endsWith("\n")) {
    process.stdout.write("\n");
  }
}

export function renderAgentPrompt(repoRoot: string, input: RenderAgentPromptInput): RenderedAgentPrompt {
  const role = input.role.replace(/\.md$/u, "");
  const agentPath = path.join(repoRoot, ".codex", "agents", `${role}.md`);
  if (!fs.existsSync(agentPath)) {
    throw new Error(`Missing agent brief: ${agentPath}`);
  }

  const projectPath = path.join(repoRoot, "project.md");
  if (!fs.existsSync(projectPath)) {
    throw new Error(`Missing project guide: ${projectPath}`);
  }

  const agentBrief = readText(agentPath);
  const skillNames = parseSkillsFromAgentBrief(agentBrief);
  const loadedSkills = skillNames.map((skillName) => loadSkill(repoRoot, skillName));
  const projectGuide = readText(projectPath);
  const taskContext = [
    "# Task Context",
    input.featureDir ? `Feature directory: ${input.featureDir}` : "Feature directory: not provided",
    input.worktreePath ? `Worktree path: ${input.worktreePath}` : "Worktree path: current repository/worktree",
    "",
    input.task ?? "No additional task text was provided.",
  ].join("\n");

  const prompt = [
    `你是 ${role}。`,
    "",
    "# Authority",
    "`AGENTS.md` is the governing execution authority. `project.md`, agent briefs, and skills are subordinate guidance. If any guidance conflicts, follow `AGENTS.md`.",
    "",
    "# B0 Project Guidance",
    projectGuide,
    "",
    "# B Agent Role Brief",
    agentBrief,
    "",
    "# S Skill Instructions",
    "The following skill bodies were loaded from the role frontmatter `skills: [...]`. You must follow the relevant skill instructions for this task, subject to `AGENTS.md` authority.",
    ...loadedSkills.flatMap((skill) => [
      "",
      `## Skill: ${skill.name}`,
      `Source: ${toPosixPath(path.relative(repoRoot, skill.sourcePath))}`,
      "",
      skill.content,
    ]),
    "",
    taskContext,
  ].join("\n");

  return {
    role,
    agentPath,
    skillNames,
    loadedSkills,
    prompt,
  };
}

export function parseSkillsFromAgentBrief(agentBrief: string): string[] {
  const frontmatter = /^---\r?\n(?<body>[\s\S]*?)\r?\n---/u.exec(agentBrief)?.groups?.body;
  if (!frontmatter) {
    return [];
  }

  const skillsLine = frontmatter.split(/\r?\n/u).find((line) => /^\s*skills\s*:/u.test(line));
  if (!skillsLine) {
    return [];
  }

  const bracketMatch = /\[(?<skills>[^\]]*)\]/u.exec(skillsLine);
  if (!bracketMatch?.groups?.skills) {
    throw new Error(`Unsupported skills frontmatter format: ${skillsLine}`);
  }

  return bracketMatch.groups.skills
    .split(",")
    .map((skill) => skill.trim().replace(/^['"]|['"]$/gu, ""))
    .filter(Boolean);
}

function loadSkill(repoRoot: string, skillName: string): LoadedSkill {
  const bridgePath = path.join(repoRoot, ".codex", "skills", skillName, "SKILL.md");
  const fallbackPath = path.join(repoRoot, ".agents", "skills", skillName, "SKILL.md");
  const sourcePath = resolveCanonicalSkillPath(repoRoot, bridgePath) ?? resolveCanonicalSkillPath(repoRoot, fallbackPath);

  if (!sourcePath) {
    throw new Error(`Missing skill body for "${skillName}". Checked: ${bridgePath} and ${fallbackPath}`);
  }

  return {
    name: skillName,
    sourcePath,
    content: readText(sourcePath),
  };
}

function resolveCanonicalSkillPath(repoRoot: string, candidatePath: string): string | undefined {
  if (!fs.existsSync(candidatePath)) {
    return undefined;
  }

  const content = readText(candidatePath);
  const canonicalValue = parseFrontmatterValue(content, "canonical");
  if (!canonicalValue) {
    return candidatePath;
  }

  const canonicalPath = path.resolve(repoRoot, canonicalValue);
  if (!fs.existsSync(canonicalPath)) {
    throw new Error(`Skill bridge points to missing canonical file: ${canonicalPath}`);
  }
  return canonicalPath;
}

function parseFrontmatterValue(content: string, key: string): string | undefined {
  const frontmatter = /^---\r?\n(?<body>[\s\S]*?)\r?\n---/u.exec(content)?.groups?.body;
  if (!frontmatter) {
    return undefined;
  }

  const line = frontmatter.split(/\r?\n/u).find((candidate) => candidate.trim().startsWith(`${key}:`));
  if (!line) {
    return undefined;
  }

  return line
    .replace(new RegExp(`^\\s*${key}\\s*:\\s*`, "u"), "")
    .trim()
    .replace(/^['"]|['"]$/gu, "");
}

function printRenderedSummary(repoRoot: string, rendered: RenderedAgentPrompt, outputPath?: string): void {
  console.log(`Role: ${rendered.role}`);
  console.log(`Agent brief: ${toPosixPath(path.relative(repoRoot, rendered.agentPath))}`);
  console.log(`Skills loaded: ${rendered.loadedSkills.length}`);
  for (const skill of rendered.loadedSkills) {
    console.log(`- ${skill.name}: ${toPosixPath(path.relative(repoRoot, skill.sourcePath))}`);
  }
  console.log(`Prompt characters: ${rendered.prompt.length}`);
  if (outputPath) {
    console.log(`Prompt written to: ${toPosixPath(outputPath)}`);
  }
}

function toPosixPath(value: string): string {
  return value.split(path.sep).join("/");
}
