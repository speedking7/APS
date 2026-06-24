import fs from "node:fs";
import path from "node:path";
import { assertNoExtraArgs } from "../utils/cli";
import { ensureDir, readText, writeText } from "../utils/fs";
import { toPosixRelative } from "../utils/paths";

const CANONICAL_BLOCK_START = "<!-- CODEX:CANONICAL:START -->";
const CANONICAL_BLOCK_END = "<!-- CODEX:CANONICAL:END -->";
const WORKFLOW_BLOCK_START = "<!-- CODEX:WORKFLOW:START -->";
const WORKFLOW_BLOCK_END = "<!-- CODEX:WORKFLOW:END -->";
const FREEZE_ADVICE =
  "Codex scaffold sources have moved to repo-local surfaces (`.agents/*`, `.codex/workflows/*`, and `project.md`). Keep workflow edits on the repo-local surfaces before re-running sync.";

export async function syncCodexCommand(args: string[], context: { repoRoot: string }): Promise<void> {
  assertNoExtraArgs(args);

  ensureDir(path.join(context.repoRoot, ".agents", "agents"));
  ensureDir(path.join(context.repoRoot, ".codex", "agents"));
  ensureDir(path.join(context.repoRoot, ".codex", "workflows"));
  ensureDir(path.join(context.repoRoot, ".codex", "skills"));
  ensureDir(path.join(context.repoRoot, ".codex", "agent-memory"));
  ensureDir(path.join(context.repoRoot, ".codex", "worktrees"));

  syncMarkdownSet({
    sourceDir: path.join(context.repoRoot, ".agents", "agents"),
    targetDir: path.join(context.repoRoot, ".codex", "agents"),
    kind: "agent",
    repoRoot: context.repoRoot,
  });
  syncMarkdownSet({
    sourceDir: path.join(context.repoRoot, ".codex", "workflows"),
    targetDir: path.join(context.repoRoot, ".codex", "workflows"),
    kind: "workflow",
    repoRoot: context.repoRoot,
  });

  regenerateCodexReadme(context.repoRoot);
  regenerateSkillBridges(context.repoRoot);
  regenerateAgentMemoryReadme(context.repoRoot);
  regenerateWorktreesReadme(context.repoRoot);

  assertCanonicalBlockPresent(path.join(context.repoRoot, ".codex", "README.md"));
  for (const entry of fs.readdirSync(path.join(context.repoRoot, ".codex", "workflows"))) {
    if (!entry.endsWith(".md")) continue;
    const fullPath = path.join(context.repoRoot, ".codex", "workflows", entry);
    assertCanonicalBlockPresent(fullPath);
    assertWorkflowBlockPresent(fullPath);
  }

  console.log("Codex scaffold sync complete.");
}

type SyncKind = "agent" | "workflow";

function syncMarkdownSet(input: { sourceDir: string; targetDir: string; kind: SyncKind; repoRoot: string }): void {
  for (const entry of fs.readdirSync(input.sourceDir, { withFileTypes: true })) {
    if (!entry.isFile() || !entry.name.endsWith(".md")) continue;
    const sourcePath = path.join(input.sourceDir, entry.name);
    const targetPath = path.join(input.targetDir, entry.name);
    let text = readText(sourcePath);
    text = rewriteCommonText(text);
    text = insertAdaptationNote(text, input.kind, toPosixRelative(input.repoRoot, sourcePath));
    if (input.kind === "workflow") {
      text = upsertCanonicalBlock(text, "workflow");
      text = upsertWorkflowBlock(text, entry.name);
    }
    writeText(targetPath, text);
  }
}

function insertAdaptationNote(text: string, kind: SyncKind, sourcePath: string): string {
  const note =
    kind === "agent"
      ? `> Codex role brief: Generated from "${sourcePath}". Use \`AGENTS.md\` and Codex child-agent routing as the execution authority. Treat legacy verbs like \`Read\`, \`Agent\`, \`SendMessage\`, and \`EnterWorktree\` as workflow instructions, not CLI-native commands. Ignore stale frontmatter fields that reference older runtime-specific surfaces.\n\n`
      : `> Codex workflow surface: Managed in this repository at "${sourcePath}". Use \`AGENTS.md\` plus the current Codex toolchain as the execution authority. Treat legacy verbs like \`Read\`, \`Agent\`, \`SendMessage\`, and \`EnterWorktree\` as workflow instructions, not CLI-native slash commands.\n\n`;

  const withFrontmatter = /^(---\r?\n.*?\r?\n---\r?\n)(?:\r?\n> (?:Codex adaptation|Codex role brief|Codex workflow surface):.*?\r?\n\r?\n)?/su;
  if (withFrontmatter.test(text)) {
    return text.replace(withFrontmatter, `$1\n${note}`);
  }

  return text.replace(/^(?:> (?:Codex adaptation|Codex role brief|Codex workflow surface):.*?\r?\n\r?\n)?/su, note);
}

function getCanonicalBlock(surface: "readme" | "workflow"): string {
  const lines = [
    CANONICAL_BLOCK_START,
    "> Canonical execution contract",
    "> - `AGENTS.md` is the governing execution authority.",
    "> - `.codex/` is the user-facing entry and workflow surface.",
    "> - `project.md` provides subordinate engineering guidance and cannot override `AGENTS.md`.",
    "> - `.omx/` is the runtime ledger for context, state, specs, plans, and evidence pointers.",
    "> - `docs/features/*` and `docs/bugfix/*` remain the only formal delivery surfaces.",
    "> - `tools/ai-scaffold` is the primary pass/fail authority; shell and PowerShell scripts are compatibility wrappers.",
    "> - Marker blocks are generator-owned canonical sections, not user-maintained preserved patches.",
  ];
  if (surface === "workflow") {
    lines.push("> - `.omx/` runtime data for this workflow cannot replace plan approval, contracts, reports, QA evidence, or gate results.");
  }
  lines.push(CANONICAL_BLOCK_END);
  return `${lines.join("\n")}\n\n`;
}

function upsertCanonicalBlock(text: string, surface: "readme" | "workflow"): string {
  const block = getCanonicalBlock(surface);
  const pattern = /<!-- CODEX:CANONICAL:START -->.*?<!-- CODEX:CANONICAL:END -->\r?\n\r?\n?/su;
  if (pattern.test(text)) {
    return text.replace(pattern, block);
  }
  const frontmatterPattern = /^(---\r?\n.*?\r?\n---\r?\n)/su;
  if (frontmatterPattern.test(text)) {
    return text.replace(frontmatterPattern, `$1\n${block}`);
  }
  return block + text;
}

function assertCanonicalBlockPresent(filePath: string): void {
  const content = readText(filePath);
  if (!content.includes(CANONICAL_BLOCK_START) || !content.includes(CANONICAL_BLOCK_END)) {
    throw new Error(`Missing canonical block in ${filePath}. ${FREEZE_ADVICE}`);
  }
}

function getWorkflowBlock(fileName: string): string | undefined {
  const map: Record<string, string[]> = {
    "plan-feature.md": [
      "## OMX 技能参与方式",
      "",
      "- 本 workflow 的**强制第一步**是 `$deep-interview`",
      "- `deep-interview` 完成后，**必须**基于其产出的 spec / interview 结论继续执行 `$ralplan`",
      "- 只有在 `deep-interview` 与 `$ralplan` 都已完成后，才允许进入正式 `plan.md` 起草",
      "- 典型运行时产物：",
      "  - `.omx/context/*`",
      "  - `.omx/interviews/*`",
      "  - `.omx/specs/deep-interview-*.md`",
      "  - `.omx/plans/prd-*.md`",
      "  - `.omx/plans/test-spec-*.md`",
      "- 按项目归档规则，必须同步把规划证据归档到对应 feature 目录：",
      "  - `docs/features/F{nnn}-{slug}/reports/planning/deep-interview.md`",
      "  - `docs/features/F{nnn}-{slug}/reports/planning/prd.md`",
      "  - `docs/features/F{nnn}-{slug}/reports/planning/test-spec.md`",
      "- 本 workflow 仍以 `docs/features/.../plan.md` 为唯一正式批准入口；OMX 技能负责澄清、规划和运行时账本，feature 目录负责正式归档与审阅证据",
      "",
      "## OMX 运行时最小合同",
      "",
      "- **Mandatory `.omx` artifacts**",
      "  - 创建或复用 `.omx/context/<slug>-<timestamp>.md`",
      "  - 创建或补齐 `.omx/interviews/*` 中与本 feature 对应的 interview 记录",
      "  - 创建或更新 `.omx/specs/deep-interview-<slug>.md`",
      "  - 创建或更新 `.omx/plans/prd-<slug>.md`",
      "  - 创建或更新 `.omx/plans/test-spec-<slug>.md`",
      "  - 更新对应 planning state（位于 `.omx/state/*` 或 session state）",
      "- **Recoverability**",
      "  - 若 `.omx/context/*` 或 planning state 缺失，可立即重建后继续",
      "  - 若 `.omx/interviews/*` 缺失但 `deep-interview` 规格仍可重建，则先补齐 interview 记录再继续",
      "  - 若 `.omx/specs/deep-interview-<slug>.md` 缺失，则阻塞 `plan.md` 正式产出",
      "  - 若 `.omx/plans/prd-<slug>.md` 或 `.omx/plans/test-spec-<slug>.md` 缺失，则阻塞 `plan.md` 正式产出，并先重新执行 `$ralplan`",
      "- **Formal authority**",
      "  - 唯一正式产物仍是 `docs/features/F{nnn}-{slug}/plan.md`",
      "  - 规划证据必须归档到 `docs/features/F{nnn}-{slug}/reports/planning/`",
      "  - `.omx/*` 不能替代 `plan_status: approved` 或 `approved_at`",
    ],
    "build-feature.md": [
      "## OMX 技能参与方式",
      "",
      "- 本 workflow 通常消费前序 `$deep-interview` / `$ralplan` 产物，而不是重新发明需求边界",
      "- 当 `plan.md` 已批准、formal docs 已齐备后：",
      "  - 单 owner 顺序推进可映射到 `$ralph`",
      "  - 多 lane 并行推进可映射到 `$team`",
      "- 典型读取产物：",
      "  - `.omx/specs/deep-interview-*.md`",
      "  - `.omx/plans/prd-*.md`",
      "  - `.omx/plans/test-spec-*.md`",
      "- 本 workflow 的正式交付面仍是 `docs/features/...`；`$ralph` / `$team` 只改变执行姿态，不改变 formal docs 和 gate 规则",
      "",
      "## OMX 运行时最小合同",
      "",
      "- **Mandatory `.omx` artifacts**",
      "  - 创建或更新 execution state（位于 `.omx/state/*` 或 session state）",
      "  - 读取已批准的 `plan.md` 作为 formal prerequisite",
      "- **Optional `.omx` reads**",
      "  - `.omx/specs/*`",
      "  - `.omx/plans/*`",
      "  - 相关 context snapshot",
      "- **Optional `.omx` writes**",
      "  - 阶段进度",
      "  - 验证证据指针",
      "  - resume metadata",
      "- **Recoverability**",
      "  - execution state 丢失可重建，不阻塞实现继续",
      "  - `.omx/specs/*` / `.omx/plans/*` 缺失可继续，只要 formal docs 完整",
      "  - approved `plan.md` 缺失或未批准必须停止",
      "- **Formal authority**",
      "  - `TASK.md`、`contract.md`、`test-plan.md`、`reports/*` 仍是唯一正式产物",
      "  - `.omx/*` 不能替代 plan approval、contract freeze、review verdict、QA evidence 或 gate pass/fail",
    ],
    "fix-bug.md": [
      "## OMX 技能参与方式",
      "",
      "- 小 bug 可以直接走本 workflow",
      "- 较复杂或边界不清的 bug，建议先走：",
      "  - `$deep-interview --quick`：快速澄清症状、边界和不做项",
      "  - `$ralplan`：当修复范围、契约影响或验证策略仍需共识时",
      "- 若 bugfix 后续需要持续串行推进，可交给 `$ralph`",
      "- 若 bugfix 涉及多条独立修复/验证线，可交给 `$team`",
      "- 无论采用哪种 OMX 技能，正式 bugfix 产物仍必须落在 `docs/bugfix/*` 或相关 `docs/features/*`",
      "",
      "## OMX 运行时最小合同",
      "",
      "- **Mandatory `.omx` artifacts**",
      "  - 创建 `.omx/context/<bug-slug>-<timestamp>.md`",
      "  - 创建或更新 bugfix state（位于 `.omx/state/*` 或 session state）",
      "- **Optional `.omx` artifacts**",
      "  - 当需要额外澄清时，写入 `.omx/specs/*`",
      "  - 写入验证证据指针和恢复元数据",
      "- **Recoverability**",
      "  - `.omx/context/*` 或 bugfix state 缺失可重建",
      "  - `.omx/*` 缺失不得阻塞正式 bugfix 文档与修复落地",
      "- **Formal authority**",
      "  - 正式 bugfix 文档和报告仍应位于 `docs/bugfix/*` 或相关 `docs/features/*`",
      "  - `.omx/*` 不能替代 bug 文档、契约校验、测试报告或 gate 结果",
    ],
    "run-review.md": [
      "## OMX 运行时最小合同",
      "",
      "- **Mandatory `.omx` artifacts**",
      "  - 创建或更新 review state（位于 `.omx/state/*` 或 session state）",
      "- **Optional `.omx` artifacts**",
      "  - findings index",
      "  - evidence pointers",
      "  - resume metadata",
      "- **Recoverability**",
      "  - review state 缺失可重建",
      "  - `.omx/*` 缺失不得阻塞正式联调报告落地",
      "- **Formal authority**",
      "  - 正式联调结论仍应落在对应 `reports/*`",
      "  - `.omx/*` 不能替代契约、联调报告、QA 结果或 gate 结果",
    ],
    "run-qa.md": [
      "## OMX 运行时最小合同",
      "",
      "- **Mandatory `.omx` artifacts**",
      "  - 创建或更新 QA run state（位于 `.omx/state/*` 或 session state）",
      "- **Optional `.omx` artifacts**",
      "  - artifact pointers（截图、日志、报告路径）",
      "  - rerun / resume metadata",
      "- **Recoverability**",
      "  - QA state 缺失可重建",
      "  - `.omx/*` 缺失不得替代 formal QA evidence",
      "- **Formal authority**",
      "  - 正式 QA 证据仍应落在对应 `reports/*`",
      "  - PASS 仍由 `node tools/ai-scaffold/dist/cli.js gate` 和等价 CI 结果决定",
      "  - `.omx/*` 不能替代 QA 证据、review verdict 或 gate pass/fail",
    ],
    "run-pipeline.md": [
      "## OMX 技能参与方式",
      "",
      "- 本 workflow 是 repo-local pipeline 表面；实际执行姿态可按任务形态映射到 OMX 模式：",
      "  - `$ralph`：一个任务接一个任务顺序推进",
      "  - `$team`：在依赖允许时拆成多 lane 并行推进",
      "- 当流水线中的任务描述本身仍然模糊时，应先回到 `$deep-interview` 或 `$ralplan`，而不是直接硬跑",
      "- 本 workflow 负责串起 formal task list 与 `.omx/state/*` 运行时账本，不替代 `docs/IMPLEMENTATION-PLAN.md` 或正式 gate",
      "",
      "## OMX 运行时最小合同",
      "",
      "- **Mandatory `.omx` artifacts**",
      "  - 创建或更新 pipeline state（位于 `.omx/state/*` 或 session state）",
      "- **Optional `.omx` artifacts**",
      "  - orchestration resume metadata",
      "  - evidence pointers",
      "  - context references to specs / plans when present",
      "- **Recoverability**",
      "  - pipeline state 缺失可重建",
      "  - `.omx/*` 缺失不得替代 formal artifacts 或 gate 校验",
      "- **Formal authority**",
      "  - 本流程默认不创建新的 formal artifact",
      "  - 只负责编排和验证现有 formal artifacts 与 gate",
      "  - `.omx/*` 不能替代任务完成状态、formal reports 或 gate pass/fail",
    ],
    "change-contract.md": [
      "## OMX 运行时最小合同",
      "",
      "- **Mandatory `.omx` artifacts**",
      "  - 创建或更新 contract-change state（位于 `.omx/state/*` 或 session state）",
      "- **Optional `.omx` artifacts**",
      "  - context snapshot",
      "  - clarification spec",
      "  - impact-analysis evidence pointers",
      "- **Recoverability**",
      "  - contract-change state 缺失可重建",
      "  - `.omx/*` 缺失不得阻塞 formal contract 变更文档落地",
      "- **Formal authority**",
      "  - 正式契约变更仍以目标 feature/bugfix 目录中的 `contract.md` 及相关正式文档为准",
      "  - `.omx/*` 不能替代契约审批、版本递增、`verify-contract.sh` 或 formal contract 文档",
    ],
  };
  const content = map[fileName];
  if (!content) return undefined;
  return `${WORKFLOW_BLOCK_START}\n${content.join("\n")}\n${WORKFLOW_BLOCK_END}\n\n`;
}

function upsertWorkflowBlock(text: string, fileName: string): string {
  const block = getWorkflowBlock(fileName);
  if (!block) return text;
  const pattern = /<!-- CODEX:WORKFLOW:START -->.*?<!-- CODEX:WORKFLOW:END -->\r?\n\r?\n?/su;
  if (pattern.test(text)) {
    return text.replace(pattern, block);
  }
  const anchor = "<!-- CODEX:CANONICAL:END -->";
  const anchorIndex = text.indexOf(anchor);
  if (anchorIndex < 0) {
    return text;
  }
  let insertIndex = anchorIndex + anchor.length;
  while (insertIndex < text.length && (text[insertIndex] === "\r" || text[insertIndex] === "\n")) {
    insertIndex += 1;
  }
  return `${text.slice(0, insertIndex)}\n\n${block}${text.slice(insertIndex)}`;
}

function assertWorkflowBlockPresent(filePath: string): void {
  const content = readText(filePath);
  if (!content.includes(WORKFLOW_BLOCK_START) || !content.includes(WORKFLOW_BLOCK_END)) {
    throw new Error(`Missing workflow block in ${filePath}. ${FREEZE_ADVICE}`);
  }
}

function rewriteCommonText(text: string): string {
  const pairs: Array<[string, string]> = [
    ["主编排 Agent", "主编排角色"],
    ["Subagent", "child agent"],
    ["子 Agent", "child agent"],
    ["子 agent", "child agent"],
    ["责任child agent", "责任 child agent"],
    ["要求child agent", "要求 child agent"],
    ["对应child agent", "对应 child agent"],
    ["SendMessage", "send_input"],
    ["EnterWorktree", "CreateWorktree"],
    ["ExitWorktree", "LeaveWorktree"],
    ["Agent Completion Report", "Role Completion Report"],
    ["### Agent:", "### Role:"],
    ["Cursor / Claude Code 的 Plan 模式", "当前 Codex 会话的 planning 阶段"],
    ["主 agent", "主编排角色"],
    ["TeamDelete", "close_agent / clean up the worktree"],
    ["Agent: subagent_type=", "spawn_agent(role="],
    ["Agent({", "spawn_agent({"],
    ["subagent_type:", "role:"],
    ["spawn_agent(role=contract-architect", "spawn_agent(agent_type=architect"],
    ["spawn_agent(role=test-designer", "spawn_agent(agent_type=test-engineer"],
    ["spawn_agent(role=backend-tdd-engineer", "spawn_agent(agent_type=executor"],
    ["spawn_agent(role=frontend-engineer", "spawn_agent(agent_type=executor"],
    ["spawn_agent(role=integration-checker", "spawn_agent(agent_type=verifier"],
    ["spawn_agent(role=code-reviewer", "spawn_agent(agent_type=code-reviewer"],
    ["spawn_agent(role=qa-tester", "spawn_agent(agent_type=test-engineer"],
    ['role: "contract-architect"', 'agent_type: "architect"'],
    ['role: "test-designer"', 'agent_type: "test-engineer"'],
    ['role: "backend-tdd-engineer"', 'agent_type: "executor"'],
    ['role: "frontend-engineer"', 'agent_type: "executor"'],
    ['role: "integration-checker"', 'agent_type: "verifier"'],
    ['role: "code-reviewer"', 'agent_type: "code-reviewer"'],
    ['role: "qa-tester"', 'agent_type: "test-engineer"'],
    ['Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"', ""],
    ["Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>", ""],
  ];

  let next = text;
  for (const [from, to] of pairs) {
    next = next.split(from).join(to);
  }

  next = next
    .replace("使用 CreateWorktree 创建隔离工作区：", "在 `.codex/worktrees/feature-{feature-name}` 创建隔离 worktree（可用 `git worktree add` 或等效方式）：")
    .replace('LeaveWorktree(action="keep") 退出沙盒', "退出隔离 worktree 上下文，保留需要的产物并准备汇总")
    .replace("使用 child agent 串行流程完成功能交付", "使用 child-agent 串行流程完成功能交付")
    .replace("读取并准备 child agent 定义", "读取并准备 child-agent 定义")
    .replace("结束 child agent 会话", "结束 child-agent 会话")
    .replace("必须使用 child agent 串行编排", "必须使用 child-agent 串行编排")
    .replace("编排 child agent 串行开发阶段与依赖", "编排 child-agent 串行开发阶段与依赖")
    .replace(
      "你是主编排角色 (tech-lead-orchestrator)，在**编写业务代码之前**完成「方案与范围」文档，并配合 **当前 Codex 会话的 planning 阶段**（先讨论、再落盘；本命令负责落盘与状态约定）。",
      "你是主编排角色（tech-lead-orchestrator），在**编写业务代码之前**完成「方案与范围」文档。先澄清范围，再把结论落到 `plan.md`；本 workflow 负责落盘与状态约定。",
    )
    .replace("你是主编排角色 (tech-lead-orchestrator)，严格按以下流程执行：", "你是主编排角色（tech-lead-orchestrator），严格按以下流程执行：")
    .replace("### 第二步: Spawn child agents", "### 第二步: 启动 child agents")
    .replace(/teammate/gu, "child agent")
    .replace("Spawn integration-checker agent", "Spawn 一个 `verifier` 子 agent（注入 `integration-checker` brief）")
    .replace("Spawn qa-tester agent", "Spawn 一个 `test-engineer` 子 agent（注入 `qa-tester` brief）")
    .replace("Spawn code-reviewer agent", "Spawn 一个 `code-reviewer` 子 agent")
    .replace("spawn 任何 agent", "spawn 任何原生子 agent")
    .replace("所有 Agent 必须传递工作目录", "所有原生子 agent 必须传递工作目录")
    .replace("原生child agent", "原生 child agent")
    .replace("### Role: backend-tdd-engineer", "### Role Brief: backend-tdd-engineer")
    .replace("### Role: frontend-engineer", "### Role Brief: frontend-engineer")
    .replace("### Role: contract-architect", "### Role Brief: contract-architect")
    .replace("### Role: test-designer", "### Role Brief: test-designer")
    .replace("### Role: tech-lead-orchestrator", "### Role Brief: tech-lead-orchestrator");

  next = next.replace(
    /CreateWorktree:\r?\n\s*name:\s*feature-\{feature-name\}/su,
    `推荐命令:\ngit worktree add .codex/worktrees/feature-{feature-name} HEAD`,
  );
  next = next.replace("Phase 0 的 CreateWorktree", "Phase 0 的 worktree 创建步骤");
  next = next.replace(/^model:.*\r?\n/gmu, "");
  next = next.replace(/^color:.*\r?\n/gmu, "");
  next = next.replace(/\r?\n{3,}/gu, "\n\n");
  return next;
}

function regenerateCodexReadme(repoRoot: string): void {
  const text = `# Codex AI Development Scaffold

This directory is the Codex-first AI development scaffold for the repository. It gives Codex users the repo-local entry surface while OMX handles runtime state and orchestration underneath it.

## Structure

\`\`\`text
.codex/
|-- agents/        # Codex role briefs generated from \`.agents/agents/\`
|-- workflows/     # Repo-local workflow playbooks; current user-facing source
|-- skills/        # Bridge docs for the canonical repo-local skills that live under .agents/skills/
|-- agent-memory/  # Optional per-role memory location for Codex-oriented orchestration
|-- worktrees/     # Optional temporary worktrees referenced by workflow docs
|-- templates/     # Feature document templates reused by scripts and agents
|-- tasks/         # Optional working area for Codex session artifacts
\`-- docker-config/ # Environment helpers
\`\`\`

## Authority Stack

- \`AGENTS.md\` governs execution, orchestration, and verification.
- \`.codex/\` is the user-facing entry surface for workflows, role briefs, and operational notes.
- \`project.md\` provides subordinate engineering and project guidance.
- \`.omx/\` stores runtime context, state, specs, plans, resume data, and evidence pointers.
- \`docs/features/*\` and \`docs/bugfix/*\` remain the only formal delivery surfaces.
- Existing gate scripts remain the only pass/fail authority.
- Project-specific paths, commands, accounts, service/container names, E2E settings, and shell/PDA adapter artifacts belong in \`ai-scaffold.config.json\`, not in the reusable scaffold core.

## Source Mapping

- \`.agents/agents/*\` -> \`.codex/agents/*\`
- \`.codex/workflows/*\` -> current repo-local workflow source
- \`.agents/skills/*\` -> canonical runtime skills live under \`.agents/skills/*\`
- \`docs/features/TASK-template.md\` -> \`.codex/templates/TASK.md\`

## OMX Runtime Ledger

Use \`.omx/\` for:
- context snapshots
- interview/spec artifacts
- plan artifacts
- execution/review/qa state
- resume metadata
- evidence pointers

Do not treat \`.omx/\` as:
- a replacement for \`docs/features/*\` or \`docs/bugfix/*\`
- a replacement for \`reports/*\`
- a plan approval source
- a gate pass/fail source

## Workflow Invocation

Codex CLI may not register repo workflows like \`/build-feature\` as native slash commands. When the CLI says a slash command is unrecognized, invoke the workflow by asking Codex to read the matching file under \`.codex/workflows/\` and execute it.

Examples:
- \`Read .codex/workflows/plan-feature.md and execute it for F123-my-feature\`
- \`Read .codex/workflows/build-feature.md and carry out the workflow for docs/features/F123-my-feature/\`
- \`Use .codex/workflows/fix-bug.md to resolve this bug: ...\`

Each workflow may read or write \`.omx/\` runtime artifacts, but its formal outputs must still land in the matching \`docs/features/*\` or \`docs/bugfix/*\` directory.

## Skill Invocation

The same repo workflows are also available as repo-local skills:

- \`$plan-feature\`
- \`$build-feature\`
- \`$fix-bug\`

These skills are thin execution surfaces over the matching files in \`.codex/workflows/\` and the canonical instructions in \`.agents/skills/\`.

Examples:

- \`$plan-feature supplier-portal-sso\`
- \`$plan-feature docs/features/F021-mock-sso-login-validation\`
- \`$build-feature docs/features/F021-mock-sso-login-validation\`
- \`$fix-bug BUG-142 login callback loops after refresh\`
- \`$fix-bug docs/bugfix/BUG-142-login-callback-loop\`

Recommended usage:

1. Plan a new feature with \`$plan-feature <slug|idea|feature-dir>\`
2. Complete the required planning artifacts and approvals
3. Implement with \`$build-feature <feature-dir>\`
4. Handle tracked defects with \`$fix-bug <bug-id|description|bugfix-dir>\`

## Native Agent Mapping

Files under \`.codex/agents/\` are project role briefs, not native \`spawn_agent\` type names. Use them as injected instructions while routing the actual child agent to the closest native type:

| Project brief | Native \`agent_type\` |
| --- | --- |
| \`contract-architect\` | \`architect\` |
| \`test-designer\` | \`test-engineer\` |
| \`backend-tdd-engineer\` | \`executor\` |
| \`frontend-engineer\` | \`executor\` |
| \`integration-checker\` | \`verifier\` |
| \`code-reviewer\` | \`code-reviewer\` |
| \`qa-tester\` | \`test-engineer\` |
| \`tech-lead-orchestrator\` | leader/main thread |

Example: read \`.codex/agents/backend-tdd-engineer.md\`, then spawn \`agent_type=executor\` with that brief embedded in the task prompt.

## Recommended Start Points

- Migration guide: \`docs/ai-scaffold-migration.md\`
- Project adapter config: \`ai-scaffold.config.json\`
- Copy scaffold to another repo: \`node tools/ai-scaffold/dist/cli.js copy-scaffold --target <target-repo>\`
- New feature scaffold: \`node tools/ai-scaffold/dist/cli.js init-feature --slug <slug> --title <title>\`
- Sync scaffold: \`node tools/ai-scaffold/dist/cli.js sync-codex\`
- Quality gate: \`node tools/ai-scaffold/dist/cli.js gate\`
- Main rules: \`AGENTS.md\`
- Project guide: \`project.md\`

## Sync Ownership

\`node tools/ai-scaffold/dist/cli.js sync-codex\` regenerates \`.codex/README.md\`, \`.codex/agents/*\`, and bridge surfaces from repo-local sources. Workflow files remain repo-local source files under \`.codex/workflows/*\`; the sync command only normalizes their canonical metadata in place.

## Session Checklist

1. Read \`AGENTS.md\`
2. Read \`project.md\`
3. Read the relevant \`.codex/agents/*.md\` and \`.codex/workflows/*.md\`
4. Treat \`project.md\` as subordinate engineering guidance if it overlaps with \`AGENTS.md\`
5. Use the canonical repo-local skills under \`.agents/skills/\` when a workflow or task calls for a skill
6. Work inside an approved feature directory under \`docs/features/\` or a bugfix directory under \`docs/bugfix/\`
7. Run the platform-neutral scaffold CLI from \`tools/ai-scaffold/\`
8. Keep every test report under the active feature directory, including Playwright E2E output
9. Use the configured E2E account from \`ai-scaffold.config.json\`
10. Keep all executable SQL and test-data SQL under the active \`sql/\` directory
11. Use \`copy-scaffold --dry-run\` before copying this scaffold into a new repository
`;
  writeText(path.join(repoRoot, ".codex", "README.md"), upsertCanonicalBlock(text, "readme"));
}

function regenerateSkillBridges(repoRoot: string): void {
  const skillsRoot = path.join(repoRoot, ".codex", "skills");
  for (const entry of fs.readdirSync(skillsRoot, { withFileTypes: true })) {
    if (entry.isDirectory()) {
      fs.rmSync(path.join(skillsRoot, entry.name), { force: true, recursive: true });
    }
  }
  ensureDir(skillsRoot);

  const agentsSkillsRoot = path.join(repoRoot, ".agents", "skills");
  for (const entry of fs.readdirSync(agentsSkillsRoot, { withFileTypes: true })) {
    if (!entry.isDirectory()) continue;
    const targetDir = path.join(skillsRoot, entry.name);
    ensureDir(targetDir);
    const skillPath = path.join(agentsSkillsRoot, entry.name, "SKILL.md");
    const description = fs.existsSync(skillPath) ? getSkillDescription(readText(skillPath)) : `Bridge to the canonical ${entry.name} skill under .agents/skills/.`;
    let bridge = `---
name: ${entry.name}
description: '${escapeSingleQuote(description)}'
canonical: .agents/skills/${entry.name}/SKILL.md
---

# Codex Skill Bridge

This bridge keeps \`.codex/skills/\` aligned with the canonical \`.agents/skills/\` source without duplicating the real skill body.

- Canonical skill: \`.agents/skills/${entry.name}/SKILL.md\`
`;
    if (description) {
      bridge += `- Description: ${description}\n`;
    }
    bridge += "\nUse the canonical `.agents/skills/.../SKILL.md` file when the workflow needs the actual skill instructions.\n";
    writeText(path.join(targetDir, "SKILL.md"), bridge);
  }

  const skillsReadme = `# Codex Skills Bridge

This directory documents the Codex-side skill layout for the repository.

## Canonical Skill Location

The active repo-local skills live under \`.agents/skills/\`. They are the canonical skill source loaded in this workspace.

## Why This Directory Exists

- Keeps the \`.codex/\` scaffold structurally complete next to \`agents/\` and \`workflows/\`
- Keeps the Codex bridge explicit while \`.agents/skills/\` remains the single-write source
- Avoids duplicating the actual \`SKILL.md\` files in two repo-local locations

## Usage

When a task needs a skill, prefer the canonical path directly, for example:
- \`.agents/skills/feature-task-docs/SKILL.md\`
- \`.agents/skills/springboot-tdd/SKILL.md\`
- \`.agents/skills/e2e-testing/SKILL.md\`
`;
  writeText(path.join(skillsRoot, "README.md"), skillsReadme);
}

function regenerateAgentMemoryReadme(repoRoot: string): void {
  const content = `# Codex Agent Memory

This directory provides Codex-oriented per-role memory when a workflow genuinely needs it.

Use per-role subdirectories like \`.codex/agent-memory/backend-tdd-engineer/\` only when a workflow genuinely needs persistent role memory. Keep role memory small, task-relevant, and easy to prune.
`;
  writeText(path.join(repoRoot, ".codex", "agent-memory", "README.md"), content);
}

function regenerateWorktreesReadme(repoRoot: string): void {
  const content = `# Codex Worktrees

Some workflow docs reference temporary worktrees under \`.codex/worktrees/\` for isolated implementation lanes.

Create and remove worktrees only when the workflow benefits from strong isolation. Do not leave stale worktrees behind after the task is complete.
`;
  writeText(path.join(repoRoot, ".codex", "worktrees", "README.md"), content);
}

function getSkillDescription(content: string): string {
  const frontmatterMatch = /^---\r?\n(.*?)\r?\n---\r?\n/su.exec(content);
  if (!frontmatterMatch) return "";
  const frontmatter = frontmatterMatch[1]!;
  const blockMatch = /(^description:\s*[>|][+-]?\s*\r?\n((?:[ \t].*\r?\n?)*))/msu.exec(frontmatter);
  if (blockMatch?.[2]) {
    return blockMatch[2]
      .split(/\r?\n/u)
      .filter((line) => line.trim().length > 0)
      .map((line) => line.replace(/^[ \t]+/u, "").trim())
      .join(" ")
      .replace(/\s{2,}/gu, " ")
      .trim();
  }
  const inlineMatch = /^description:\s*(.+)$/mu.exec(frontmatter);
  return inlineMatch ? inlineMatch[1]!.trim().replace(/^["']|["']$/gu, "") : "";
}

function escapeSingleQuote(value: string): string {
  return value.replace(/'/gu, "''");
}
