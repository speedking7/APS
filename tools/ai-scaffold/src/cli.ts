import { getRepoRoot } from "./utils/paths";
import type { CommandHandler } from "./utils/cli";
import { initFeatureCommand } from "./commands/init-feature";
import { gateCommand } from "./commands/gate";
import { syncCodexCommand } from "./commands/sync-codex";
import { startNextFeatureCommand } from "./commands/start-next-feature";
import { archivePlanningArtifactsCommand } from "./commands/archive-planning-artifacts";
import { e2eDockerCommand } from "./commands/e2e-docker";
import { pdaShellHandoffCommand } from "./commands/pda-shell-handoff";
import { verifyPdaShellReadinessCommand } from "./commands/verify-pda-shell-readiness";
import { toggleOmxNotifyCommand } from "./commands/toggle-omx-notify";
import { repairOmxProviderLaunchCommand } from "./commands/repair-omx-provider-launch";
import { prepareManualTestCommand } from "./commands/prepare-manual-test";
import { stopManualTestCommand } from "./commands/stop-manual-test";
import { checkPlanApprovedCommand } from "./commands/check-plan-approved";
import { verifyContractCommand } from "./commands/verify-contract";
import { checkTaskTraceabilityCommand } from "./commands/check-task-traceability";
import { checkCodeReviewVerdictCommand } from "./commands/check-code-review-verdict";
import { checkFeatureArtifactsCommand } from "./commands/check-feature-artifacts";
import { checkBuildFeaturePrereqsCommand } from "./commands/check-build-feature-prereqs";
import { checkWorkItemLinkCommand } from "./commands/check-work-item-link";
import { checkReuseDuplicationCommand } from "./commands/check-reuse-duplication";
import { renderAgentPromptCommand } from "./commands/render-agent-prompt";
import { hookCommand, installHooksCommand } from "./commands/hook";
import { ensureFrontendDepsCommand } from "./commands/ensure-frontend-deps";
import { doctorCommand } from "./commands/doctor";
import { copyScaffoldCommand } from "./commands/copy-scaffold";

const commands: Record<string, CommandHandler> = {
  "init-feature": initFeatureCommand,
  gate: gateCommand,
  "sync-codex": syncCodexCommand,
  "start-next-feature": startNextFeatureCommand,
  "archive-planning-artifacts": archivePlanningArtifactsCommand,
  "e2e-docker": e2eDockerCommand,
  "pda-shell-handoff": pdaShellHandoffCommand,
  "verify-pda-shell-readiness": verifyPdaShellReadinessCommand,
  "toggle-omx-notify": toggleOmxNotifyCommand,
  "repair-omx-provider-launch": repairOmxProviderLaunchCommand,
  "prepare-manual-test": prepareManualTestCommand,
  "stop-manual-test": stopManualTestCommand,
  "check-plan-approved": checkPlanApprovedCommand,
  "verify-contract": verifyContractCommand,
  "check-task-traceability": checkTaskTraceabilityCommand,
  "check-code-review-verdict": checkCodeReviewVerdictCommand,
  "check-feature-artifacts": checkFeatureArtifactsCommand,
  "check-build-feature-prereqs": checkBuildFeaturePrereqsCommand,
  "check-work-item-link": checkWorkItemLinkCommand,
  "check-reuse-duplication": checkReuseDuplicationCommand,
  "render-agent-prompt": renderAgentPromptCommand,
  hook: hookCommand,
  "install-hooks": installHooksCommand,
  "ensure-frontend-deps": ensureFrontendDepsCommand,
  "copy-scaffold": copyScaffoldCommand,
  doctor: doctorCommand,
  "env-report": doctorCommand,
};

async function main(): Promise<void> {
  const [, , commandName, ...args] = process.argv;
  if (!commandName || commandName === "--help" || commandName === "help") {
    console.log(
      "Usage: scaffold <init-feature|gate|sync-codex|start-next-feature|archive-planning-artifacts|e2e-docker|pda-shell-handoff|verify-pda-shell-readiness|toggle-omx-notify|repair-omx-provider-launch|prepare-manual-test|stop-manual-test|check-plan-approved|verify-contract|check-task-traceability|check-code-review-verdict|check-feature-artifacts|check-build-feature-prereqs|check-work-item-link|check-reuse-duplication|render-agent-prompt|hook|install-hooks|ensure-frontend-deps|copy-scaffold|doctor|env-report> [options]",
    );
    return;
  }

  const handler = commands[commandName];
  if (!handler) {
    throw new Error(`Unknown command: ${commandName}`);
  }

  await handler([...args], {
    repoRoot: getRepoRoot(),
  });
}

main().catch((error: unknown) => {
  const message = error instanceof Error ? error.message : String(error);
  console.error(message);
  process.exitCode = 1;
});
