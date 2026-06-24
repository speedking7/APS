# Portable AI Development Scaffold

This directory is a portable AI development scaffold. An AI agent can follow this README to install Codex, install oh-my-codex, copy the scaffold into a target repository, initialize it, and verify that the target repo has a complete AI-assisted development workflow.

## Agent Objective

Install this scaffold into a target repository so the repo supports:

- Codex CLI operation
- oh-my-codex (`omx`) orchestration
- repo-local `AGENTS.md` and `project.md`
- Codex workflow surfaces under `.codex/workflows/`
- role briefs and skill injection under `.agents/` and `.codex/`
- feature/bugfix artifact workflows under `docs/features/` and `docs/bugfix/`
- scaffold CLI gates under `tools/ai-scaffold`
- a project adapter config at `ai-scaffold.config.json`

Do not migrate business code from the source repo. This bundle is the scaffold only.

## Inputs Required

Before execution, identify:

- `SCAFFOLD_DIR`: this directory.
- `TARGET_REPO`: the repository where the scaffold will be installed.
- Target backend path, frontend path(s), test commands, E2E commands, database/test account settings.

Example PowerShell variables:

```powershell
$SCAFFOLD_DIR = "C:\path\to\ai-scaffold-portable"
$TARGET_REPO = "C:\path\to\your-target-repo"
```

Example bash variables:

```bash
SCAFFOLD_DIR="/path/to/ai-scaffold-portable"
TARGET_REPO="/path/to/your-target-repo"
```

## Phase 1: Install Base Tooling

Verify Node.js and git are available:

```bash
node --version
npm --version
git --version
```

If Node.js is missing, install Node.js 20+ or 22+ first. Then install the AI tools:

```bash
npm install -g @openai/codex@latest
npm install -g oh-my-codex@latest
```

Verify:

```bash
codex --version
omx --version
```

Authenticate Codex:

```bash
codex login
```

Run basic OMX setup:

```bash
omx setup --scope user
omx doctor
```

If the target repo should keep project-local OMX/Codex overlays, run this after entering the target repo:

```bash
cd "$TARGET_REPO"
omx setup --scope project
```

PowerShell equivalent:

```powershell
Set-Location $TARGET_REPO
omx setup --scope project
```

## Phase 2: Copy Scaffold Into Target Repo

Recommended one-command copy (cross-platform, excludes runtime/cache outputs by default):

```bash
node "$SCAFFOLD_DIR/copy-scaffold.mjs" --target "$TARGET_REPO"
```

PowerShell equivalent:

```powershell
node (Join-Path $SCAFFOLD_DIR "copy-scaffold.mjs") --target $TARGET_REPO
```

After `tools/ai-scaffold` is built, the same copy logic is also available from the scaffold CLI:

```bash
node tools/ai-scaffold/dist/cli.js copy-scaffold --target "$TARGET_REPO"
```

The copy command transfers these reusable surfaces:

- `AGENTS.md`
- `project.md`
- `ai-scaffold.config.json`
- `.agents/`
- `.codex/`
- `docs/ai-scaffold-migration.md`
- `docs/features/` templates and `NEXT_FEATURE_NUMBER.txt`
- `docs/bugfix/` templates
- `tools/ai-scaffold/` source, package files, and tests
- `scripts/check-*`
- `copy-scaffold.mjs`
- `README.md` only if the target repo does not already have one; pass `--include-readme` to overwrite intentionally

It does not copy runtime or generated state unless explicitly requested:

- `.omx/`
- `.codex/tasks/tmp/`
- `.codex/worktrees/*` except `.codex/worktrees/README.md`
- `node_modules/`
- `dist/`
- `_temp/`
- sample `docs/features/Fxxx-*` directories

Useful options:

- `--dry-run`: show what would be copied
- `--include-readme`: overwrite/copy scaffold README into the target repo
- `--skip-gitignore`: do not append the scaffold runtime ignore block
- `--include-dist`: include prebuilt `tools/ai-scaffold/dist` if you need an offline copy
- `--include-node-modules`: include dependencies only for an intentionally frozen/offline bundle

Manual copying is possible, but use the same exclusion rules above. Blind `Copy-Item -Recurse` / `cp -R tools` can accidentally copy `node_modules` and stale `dist` output.

## Phase 3: Configure Target Project Adapter

Edit `TARGET_REPO/ai-scaffold.config.json`.

Set at least:

- `projectName`
- `featureRoot`
- `bugfixRoot`
- `codeLikeRoots`
- `backend.path`
- `backend.commands.compile`
- `backend.commands.test`
- `backend.commands.verify`
- `frontends[].path`
- `frontends[].commands.lint`
- `frontends[].commands.test`
- `frontends[].commands.build`
- `frontends[].commands.e2e`
- `database.testName`
- `database.user`
- `database.password`
- `e2e.username`
- `e2e.password`
- `e2e.tenantCode`

Keep all project-specific paths, service names, database names, accounts, and commands in this config file. Do not hardcode them into `tools/ai-scaffold`.

Then update:

- `TARGET_REPO/AGENTS.md`: replace placeholder project layout and rules with target repo rules.
- `TARGET_REPO/project.md`: document target backend/frontend paths, domain conventions, build/test commands, permissions, SQL, and E2E rules.

## Phase 4: Initialize Scaffold CLI

From the target repo root:

```bash
npm --prefix tools/ai-scaffold ci
npm --prefix tools/ai-scaffold run build
node tools/ai-scaffold/dist/cli.js doctor
node tools/ai-scaffold/dist/cli.js copy-scaffold --help
```

Expected result:

- `doctor` prints the target repo root.
- `doctor` prints values from `ai-scaffold.config.json`.
- Required commands are reported as `OK` or actionable missing tools are listed.
- `copy-scaffold --help` prints available quick-copy options.

## Phase 5: Initialize Codex Surfaces

Regenerate Codex-facing bridge files:

```bash
node tools/ai-scaffold/dist/cli.js sync-codex
```

Verify skill injection:

```bash
node tools/ai-scaffold/dist/cli.js render-agent-prompt `
  --role backend-tdd-engineer `
  --feature-dir docs/features/F001-scaffold-smoke `
  --task "Smoke test prompt rendering" `
  --summary
```

Bash equivalent:

```bash
node tools/ai-scaffold/dist/cli.js render-agent-prompt \
  --role backend-tdd-engineer \
  --feature-dir docs/features/F001-scaffold-smoke \
  --task "Smoke test prompt rendering" \
  --summary
```

Expected result:

- The command exits 0.
- Output lists the agent brief.
- Output shows one or more skills loaded from `.agents/skills/.../SKILL.md`.

## Phase 6: Install Git Hooks

Install scaffold hooks:

```bash
node tools/ai-scaffold/dist/cli.js install-hooks
```

The hooks enforce quick local checks before commit/push. If the target repo already has hooks, merge the generated logic instead of blindly overwriting team-owned hooks.

## Phase 7: Smoke Test Feature Workflow

Create a smoke feature:

```bash
node tools/ai-scaffold/dist/cli.js init-feature --slug scaffold-smoke --title "Scaffold Smoke"
```

Expected result:

- `docs/features/F001-scaffold-smoke/plan.md`
- `docs/features/F001-scaffold-smoke/TASK.md`
- `docs/features/F001-scaffold-smoke/contract.md`
- `docs/features/F001-scaffold-smoke/test-plan.md`
- `docs/features/F001-scaffold-smoke/sql/`
- `docs/features/F001-scaffold-smoke/reports/planning/`

Check work-item enforcement:

```bash
printf "backend/src/App.java\n" | node tools/ai-scaffold/dist/cli.js check-work-item-link --stdin
```

This should fail because code changed without feature/bugfix docs.

Then check the passing case:

```bash
printf "backend/src/App.java\ndocs/features/F001-scaffold-smoke/TASK.md\n" | node tools/ai-scaffold/dist/cli.js check-work-item-link --stdin
```

This should pass.

PowerShell equivalent:

```powershell
@("backend/src/App.java") | node tools\ai-scaffold\dist\cli.js check-work-item-link --stdin
@("backend/src/App.java", "docs/features/F001-scaffold-smoke/TASK.md") | node tools\ai-scaffold\dist\cli.js check-work-item-link --stdin
```

## Phase 8: Run Scaffold Self-Tests

Run:

```bash
npm --prefix tools/ai-scaffold test
node tools/ai-scaffold/dist/cli.js copy-scaffold --target "$TEMP_TARGET_REPO" --dry-run
```

Expected result:

- TypeScript build passes.
- Scaffold tests pass.
- `render-agent-prompt` tests prove skills are loaded.
- work-item and reuse checks pass their test expectations.
- copy dry-run lists only reusable scaffold files and excludes `node_modules`, `dist`, `.omx`, `_temp`, and sample feature directories.

## Phase 9: Optional CI Setup

If the target repo uses GitHub Actions, add or adapt `.github/workflows/ci.yml`.

Keep these invariant checks:

- `npm --prefix tools/ai-scaffold test`
- changed feature artifact checks
- work-item link check
- reuse duplication check
- target backend test command
- target frontend lint/test/build command
- target E2E command when frontend changes

Project-specific CI parts must match `ai-scaffold.config.json`.

## Phase 10: Start Using The Scaffold

Typical feature flow:

```text
1. Ask Codex/OMX to execute `.codex/workflows/plan-feature.md`.
2. Approve `docs/features/Fxxx-*/plan.md`.
3. Ask Codex/OMX to execute `.codex/workflows/build-feature.md`.
4. Require child-agent prompts to be generated with `render-agent-prompt`.
5. Run review and QA workflows.
6. Run `node tools/ai-scaffold/dist/cli.js gate --feature-dir docs/features/Fxxx-*`.
7. Merge only when local gates or CI are green.
```

Typical Codex launch:

```bash
codex -C "$TARGET_REPO"
```

Typical OMX launch:

```bash
omx
```

For autonomous project work:

```bash
omx --yolo
```

Use destructive or full-access modes only inside an externally safe workspace.

## Completion Checklist

The installation is complete when all items are true:

- `codex --version` works.
- `omx --version` works.
- `codex login` has completed.
- `omx doctor` passes or reports only understood non-blockers.
- `node tools/ai-scaffold/dist/cli.js doctor` shows the target project adapter values.
- `node tools/ai-scaffold/dist/cli.js sync-codex` exits 0.
- `node tools/ai-scaffold/dist/cli.js copy-scaffold --target <temp-dir> --dry-run` exits 0.
- `npm --prefix tools/ai-scaffold test` exits 0.
- `init-feature` creates a complete feature directory.
- `render-agent-prompt --summary` loads role skills.
- `check-work-item-link` fails without docs and passes with feature/bugfix docs.
- `AGENTS.md` and `project.md` describe the target repo, not this temporary scaffold directory.
- Runtime state directories are not committed.
- The portable bundle does not contain business documents, sample feature directories, `node_modules`, or stale `dist` outputs unless intentionally included for offline distribution.

## Recovery Notes

- If `codex` is missing, rerun `npm install -g @openai/codex@latest`.
- If `omx` is missing, rerun `npm install -g oh-my-codex@latest`.
- If `render-agent-prompt` cannot find skills, run `node tools/ai-scaffold/dist/cli.js sync-codex` and confirm `.agents/skills/<skill>/SKILL.md` exists.
- If tests still mention source-project paths, replace that assertion with values from `ai-scaffold.config.json`.
- If `gate` fails because backend/frontend paths do not exist, fix `ai-scaffold.config.json` before editing scaffold core.
- If `copy-scaffold` copies too much, check `ai-scaffold.config.json` `portableCopy` options and avoid `--include-dist` / `--include-node-modules` unless creating an offline bundle.
