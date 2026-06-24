import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const scaffoldRoot = path.resolve(__dirname, "..");
const repoRoot = path.resolve(scaffoldRoot, "..", "..");
const cliPath = path.join(scaffoldRoot, "dist", "cli.js");

const { initFeatureCommand } = require("../dist/commands/init-feature.js");
const { testReuseEvidence } = require("../dist/commands/gate.js");
const { getPrePushPlan } = require("../dist/commands/hook.js");
const { parseSkillsFromAgentBrief, renderAgentPrompt } = require("../dist/commands/render-agent-prompt.js");
const { loadScaffoldConfig } = require("../dist/config/scaffold-config.js");

function makeTempDir(prefix) {
  return fs.mkdtempSync(path.join(os.tmpdir(), prefix));
}

function writeFile(filePath, content) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  fs.writeFileSync(filePath, content, "utf8");
}

function readFile(filePath) {
  return fs.readFileSync(filePath, "utf8");
}

function runCli(args, input) {
  return spawnSync(process.execPath, [cliPath, ...args], {
    cwd: repoRoot,
    encoding: "utf8",
    input,
  });
}

function runGit(cwd, args) {
  const result = spawnSync("git", args, {
    cwd,
    encoding: "utf8",
  });
  if (result.status !== 0) {
    throw new Error(`git ${args.join(" ")} failed:\n${result.stderr || result.stdout}`);
  }
  return result;
}

test("feature templates do not use legacy {slug} placeholders", () => {
  const templatesRoot = path.join(repoRoot, "docs", "features");
  const templateFiles = [
    "plan-template.md",
    "TASK-template.md",
    "contract-template.md",
    "test-plan-template.md",
  ];
  const offenders = templateFiles
    .map((fileName) => path.join(templatesRoot, fileName))
    .filter((filePath) => readFile(filePath).includes("{slug}"))
    .map((filePath) => path.relative(repoRoot, filePath).split(path.sep).join("/"));

  assert.deepEqual(offenders, []);
});

test("portable bundle excludes business samples and generated dependency output", () => {
  const forbidden = [
    "docs/features/F001-scaffold-smoke",
  ];

  for (const relativePath of forbidden) {
    assert.equal(fs.existsSync(path.join(repoRoot, relativePath)), false, `${relativePath} should not be part of the portable template`);
  }
});

test("init-feature replaces scaffold tokens including legacy {slug}", async () => {
  const fixtureRoot = makeTempDir("ai-scaffold-init-");
  const featuresRoot = path.join(fixtureRoot, "docs", "features");
  fs.mkdirSync(featuresRoot, { recursive: true });
  writeFile(path.join(featuresRoot, "NEXT_FEATURE_NUMBER.txt"), "7\n");

  const templates = {
    "plan-template.md": [
      "---",
      "feature: F{nnn}-{feature-slug}",
      "title: {feature-title}",
      "owner: {agent-name}",
      "created_at: YYYY-MM-DD",
      "---",
      "# Plan {id}: {feature-name}",
      "Legacy slug: {slug}",
      "",
    ].join("\n"),
    "TASK-template.md": [
      "# Task: {feature-name}",
      "- Feature: F{nnn}-{feature-slug}",
      "- ID: TASK-{feature-slug}",
      "- SQL: docs/features/F{nnn}-{slug}/sql/",
      "",
    ].join("\n"),
    "contract-template.md": "# Contract {id}: {feature-name}\n",
    "test-plan-template.md": "# Tests TASK-{feature-slug} YYYY-MM-DD\n",
  };

  for (const [fileName, content] of Object.entries(templates)) {
    writeFile(path.join(featuresRoot, fileName), content);
  }

  await initFeatureCommand(["--slug", "cycle-count", "--title", "Cycle Count"], {
    repoRoot: fixtureRoot,
  });

  const featureDir = path.join(featuresRoot, "F007-cycle-count");
  assert.equal(readFile(path.join(featuresRoot, "NEXT_FEATURE_NUMBER.txt")), "8\n");
  assert.ok(fs.existsSync(path.join(featureDir, "reports", "planning")));
  assert.ok(fs.existsSync(path.join(featureDir, "sql")));

  const generated = ["plan.md", "TASK.md", "contract.md", "test-plan.md"]
    .map((fileName) => readFile(path.join(featureDir, fileName)))
    .join("\n");
  assert.match(generated, /F007-cycle-count/u);
  assert.match(generated, /TASK-cycle-count/u);
  assert.doesNotMatch(generated, /\{(?:nnn|slug|feature-slug|feature-name|feature-title|id|agent-name)\}|YYYY-MM-DD/u);
});

test("check-work-item-link fails code changes without feature or bugfix artifacts", () => {
  const result = runCli(["check-work-item-link", "--stdin"], "backend/service/pom.xml\n");

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /Code changes must be tied to a feature or bugfix artifact directory/u);
});

test("check-work-item-link passes when code changes include a work item directory", () => {
  const result = runCli(
    ["check-work-item-link", "--stdin"],
    [
      "backend/service/pom.xml",
      "docs/features/F123-inventory-audit/TASK.md",
      "",
    ].join("\n"),
  );

  assert.equal(result.status, 0, result.stderr || result.stdout);
  assert.match(result.stdout, /Work item link check passed/u);
});

test("getPrePushPlan scopes local hook checks to changed areas", () => {
  const config = loadScaffoldConfig(repoRoot);
  assert.deepEqual(
    getPrePushPlan([
      "tools/ai-scaffold/src/commands/init-feature.ts",
      "docs/features/TASK-template.md",
    ], config),
    {
      backend: false,
      frontends: [],
      scaffold: true,
    },
  );

  const frontendPath = config.frontends[0].path;
  const plan = getPrePushPlan(
    [
      `${config.backend.path}/src/main/java/com/example/Service.java`,
      `${frontendPath}/src/App.tsx`,
    ],
    config,
  );
  assert.equal(plan.backend, true);
  assert.deepEqual(plan.frontends.map((frontend) => frontend.path), [frontendPath]);
  assert.equal(plan.scaffold, false);
});

test("renderAgentPrompt loads canonical skill bodies declared by an agent", () => {
  const rendered = renderAgentPrompt(repoRoot, {
    role: "backend-tdd-engineer",
    featureDir: "docs/features/F123-demo",
    worktreePath: ".codex/worktrees/feature-demo",
    task: "Implement the backend slice.",
  });

  assert.deepEqual(rendered.skillNames, [
    "springboot-tdd",
    "springboot-patterns",
    "jpa-patterns",
    "java-coding-standards",
  ]);
  assert.equal(rendered.loadedSkills.length, 4);
  assert.ok(rendered.loadedSkills.every((skill) => skill.sourcePath.includes(`${path.sep}.agents${path.sep}skills${path.sep}`)));
  assert.match(rendered.prompt, /# S Skill Instructions/u);
  assert.match(rendered.prompt, /# Spring Boot TDD Workflow/u);
  assert.match(rendered.prompt, /# B Agent Role Brief/u);
  assert.match(rendered.prompt, /Backend TDD Engineer/u);
  assert.match(rendered.prompt, /Feature directory: docs\/features\/F123-demo/u);
});

test("render-agent-prompt summary proves skills are loaded", () => {
  const result = runCli([
    "render-agent-prompt",
    "--role",
    "frontend-engineer",
    "--feature-dir",
    "docs/features/F123-demo",
    "--task",
    "Implement the frontend slice.",
    "--summary",
  ]);

  assert.equal(result.status, 0, result.stderr || result.stdout);
  assert.match(result.stdout, /Role: frontend-engineer/u);
  assert.match(result.stdout, /Skills loaded: 2/u);
  assert.match(result.stdout, /frontend-patterns: \.agents\/skills\/frontend-patterns\/SKILL\.md/u);
  assert.match(result.stdout, /coding-standards: \.agents\/skills\/coding-standards\/SKILL\.md/u);
});

test("copy-scaffold dry-run excludes generated and runtime state", () => {
  const targetRoot = makeTempDir("ai-scaffold-copy-target-");
  const result = runCli(["copy-scaffold", "--target", targetRoot, "--dry-run"]);

  assert.equal(result.status, 0, result.stderr || result.stdout);
  assert.match(result.stdout, /would copy AGENTS\.md/u);
  assert.match(result.stdout, /would copy tools\/ai-scaffold\/src\/cli\.ts/u);
  assert.match(result.stdout, /would update \.gitignore/u);
  assert.doesNotMatch(result.stdout, /node_modules/u);
  assert.doesNotMatch(result.stdout, /would copy tools\/ai-scaffold\/dist/u);
  assert.doesNotMatch(result.stdout, /\.omx/u);
  assert.doesNotMatch(result.stdout, /docs\/features\/F\d{3}-/u);
});

test("parseSkillsFromAgentBrief reads frontmatter skill names", () => {
  const skills = parseSkillsFromAgentBrief([
    "---",
    "name: sample",
    "skills: [api-design, 'coding-standards']",
    "---",
    "# Body",
  ].join("\n"));

  assert.deepEqual(skills, ["api-design", "coding-standards"]);
});

test("testReuseEvidence rejects template-only reuse sections", () => {
  const featureDir = makeTempDir("ai-scaffold-reuse-");
  writeFile(
    path.join(featureDir, "plan.md"),
    [
      "# Plan",
      "## Reuse Strategy",
      "### Must Reuse",
      "- Existing services / components / DTOs / SQL / permissions / test fixtures:",
      "### Duplication Rejected",
      "- Parallel implementations that must not be introduced:",
      "### Approved New Seams",
      "- New abstractions allowed only when reuse is not viable, with reason:",
      "",
    ].join("\n"),
  );
  writeFile(
    path.join(featureDir, "TASK.md"),
    [
      "# Task",
      "## Reuse Plan",
      "- Existing backend seams to reuse:",
      "- Existing frontend seams to reuse:",
      "- Existing SQL / permissions / test fixtures to reuse:",
      "- New seams allowed only if existing seams cannot be reused, because:",
      "## Definition of Done",
      "- reuse review completed",
      "",
    ].join("\n"),
  );

  assert.throws(
    () => testReuseEvidence(featureDir),
    /reuse section must name concrete reusable seams/u,
  );
});

test("testReuseEvidence accepts concrete reusable seam references", () => {
  const featureDir = makeTempDir("ai-scaffold-reuse-");
  const backendSeam = "backend/service/src/main/java/com/example/app/ReceivingService.java";
  writeFile(
    path.join(featureDir, "plan.md"),
    [
      "# Plan",
      "## Reuse Strategy",
      "### Must Reuse",
      `- Reuse ${backendSeam} and /api/v1/receiving/orders.`,
      "### Duplication Rejected",
      "- Do not add a parallel receiving service.",
      "### Approved New Seams",
      "- None.",
      "",
    ].join("\n"),
  );
  writeFile(
    path.join(featureDir, "TASK.md"),
    [
      "# Task",
      "## Reuse Plan",
      `- Existing backend seams to reuse: ${backendSeam}`,
      "- Existing frontend seams to reuse: frontend/web/src/pages/receiving/ReceivingPage.tsx",
      "- Existing SQL / permissions / test fixtures to reuse: inbound:receiving:view",
      "## Definition of Done",
      "- 复用审查已完成",
      "",
    ].join("\n"),
  );

  assert.doesNotThrow(() => testReuseEvidence(featureDir));
});

test("check-reuse-duplication fails duplicate SQL table surfaces", () => {
  const fixtureRoot = makeTempDir("ai-scaffold-dup-");
  runGit(fixtureRoot, ["init"]);
  runGit(fixtureRoot, ["config", "user.email", "test@example.com"]);
  runGit(fixtureRoot, ["config", "user.name", "AI Scaffold Test"]);

  writeFile(path.join(fixtureRoot, "docs", "db", "base.sql"), "CREATE TABLE wms_duplicate_table (id int);\n");
  runGit(fixtureRoot, ["add", "."]);
  runGit(fixtureRoot, ["commit", "-m", "base"]);

  writeFile(path.join(fixtureRoot, "docs", "db", "feature.sql"), "CREATE TABLE wms_duplicate_table (id int);\n");
  runGit(fixtureRoot, ["add", "."]);
  runGit(fixtureRoot, ["commit", "-m", "feature"]);

  const modulePath = path.join(scaffoldRoot, "dist", "commands", "check-reuse-duplication.js");
  const script = [
    "const { checkReuseDuplicationCommand } = require(process.argv[2]);",
    "checkReuseDuplicationCommand(['--stdin', '--base', 'HEAD~1', '--head', 'HEAD', '--range-mode', 'two-dot'], { repoRoot: process.argv[1] })",
    "  .then(() => process.exit(0))",
    "  .catch((error) => { console.error(error.message); process.exit(1); });",
  ].join("\n");

  const result = spawnSync(process.execPath, ["-e", script, fixtureRoot, modulePath], {
    cwd: fixtureRoot,
    encoding: "utf8",
    input: "docs/db/feature.sql\n",
  });

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /Potential duplicate SQL table surface detected/u);
  assert.match(result.stderr, /wms_duplicate_table/u);
});

