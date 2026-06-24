import fs from "node:fs";
import path from "node:path";
import { assertNoExtraArgs, takeFlag } from "../utils/cli";
import { runCapture } from "../utils/exec";

export async function repairOmxProviderLaunchCommand(args: string[], _context: { repoRoot: string }): Promise<void> {
  const checkOnly = takeFlag(args, "--check");
  assertNoExtraArgs(args);

  const packageRoot = getOmxInstallRoot();
  const indexPath = path.join(packageRoot, "dist", "cli", "index.js");
  if (!fs.existsSync(indexPath)) {
    throw new Error(`OMX launch file not found: ${indexPath}`);
  }

  let content = fs.readFileSync(indexPath, "utf8");
  const alreadyPatched =
    content.includes("return codexHome();") &&
    content.includes("function injectLaunchConfigOverrides(args, codexHomeOverride)") &&
    content.includes("const providerPrefix = `model_providers.${modelProvider}`;");

  if (checkOnly) {
    console.log(`${alreadyPatched ? "patched" : "not-patched"}: ${indexPath}`);
    return;
  }

  if (!content.includes('import { parse as parseToml } from "@iarna/toml";')) {
    content = assertReplaced(
      content,
      /import \{ execFileSync, spawn \} from "child_process";\r?\n/su,
      'import { execFileSync, spawn } from "child_process";\nimport { parse as parseToml } from "@iarna/toml";\n',
      "parseToml import",
    );
  }

  if (!content.includes('import { codexConfigPath, codexHome } from "../utils/paths.js";')) {
    content = assertReplaced(
      content,
      /import \{ codexConfigPath \} from "\.\.\/utils\/paths\.js";\r?\n/su,
      'import { codexConfigPath, codexHome } from "../utils/paths.js";\n',
      "codexHome import",
    );
  }

  if (!content.includes("return codexHome();")) {
    content = assertReplaced(
      content,
      /export function resolveCodexHomeForLaunch\(cwd, env = process\.env\) \{\r?\n.*?\r?\n\}/su,
      `export function resolveCodexHomeForLaunch(cwd, env = process.env) {
    if (env.CODEX_HOME && env.CODEX_HOME.trim() !== "")
        return env.CODEX_HOME;
    const persistedScope = readPersistedSetupScope(cwd);
    if (persistedScope === "project") {
        return join(cwd, ".codex");
    }
    return codexHome();
}`,
      "resolveCodexHomeForLaunch",
    );
  }

  if (!content.includes("function injectLaunchConfigOverrides(args, codexHomeOverride)")) {
    content = assertReplaced(
      content,
      /export function injectModelInstructionsBypassArgs\(cwd, args, env = process\.env, defaultFilePath\) \{\r?\n.*?\r?\n\}\r?\nexport function collectInheritableTeamWorkerArgs/su,
      `export function injectModelInstructionsBypassArgs(cwd, args, env = process.env, defaultFilePath) {
    const normalized = [...args];
    if (shouldBypassDefaultSystemPrompt(env) && !hasModelInstructionsOverride(normalized)) {
        normalized.push(CONFIG_FLAG, buildModelInstructionsOverride(cwd, env, defaultFilePath));
    }
    return injectLaunchConfigOverrides(normalized, env.CODEX_HOME || codexHome());
}
function normalizeConfigString(value) {
    if (typeof value !== "string")
        return undefined;
    const trimmed = value.trim();
    return trimmed.length > 0 ? trimmed : undefined;
}
function buildConfigOverrideValue(key, value) {
    return typeof value === "boolean"
        ? \`\${key}=\${value ? "true" : "false"}\`
        : \`\${key}="\${escapeTomlString(value)}"\`;
}
function hasConfigOverrideKey(args, key) {
    for (let i = 0; i < args.length; i += 1) {
        const arg = args[i];
        if (arg === CONFIG_FLAG || arg === LONG_CONFIG_FLAG) {
            const maybeValue = args[i + 1];
            if (typeof maybeValue === "string" &&
                new RegExp(\`^\${key.replace(/[.*+?^\\\${}()|[\\]\\\\]/g, "\\\\$&")}\\\\s*=\`).test(maybeValue.trim())) {
                return true;
            }
            continue;
        }
        if (arg.startsWith(\`\${LONG_CONFIG_FLAG}=\`)) {
            const inlineValue = arg.slice(\`\${LONG_CONFIG_FLAG}=\`.length);
            if (new RegExp(\`^\${key.replace(/[.*+?^\\\${}()|[\\]\\\\]/g, "\\\\$&")}\\\\s*=\`).test(inlineValue.trim())) {
                return true;
            }
        }
    }
    return false;
}
function readExplicitLaunchOverrides(codexHomeOverride) {
    const configPath = join(codexHomeOverride || codexHome(), "config.toml");
    if (!existsSync(configPath))
        return [];
    try {
        const parsed = parseToml(readFileSync(configPath, "utf-8"));
        if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
            return [];
        }
        const overrides = [];
        const modelProvider = normalizeConfigString(parsed.model_provider);
        if (modelProvider) {
            overrides.push(["model_provider", modelProvider]);
        }
        const preferredAuthMethod = normalizeConfigString(parsed.preferred_auth_method);
        if (preferredAuthMethod) {
            overrides.push(["preferred_auth_method", preferredAuthMethod]);
        }
        const providerConfig = modelProvider &&
            parsed.model_providers &&
            typeof parsed.model_providers === "object" &&
            !Array.isArray(parsed.model_providers)
            ? parsed.model_providers[modelProvider]
            : undefined;
        if (!providerConfig || typeof providerConfig !== "object" || Array.isArray(providerConfig)) {
            return overrides;
        }
        const providerPrefix = \`model_providers.\${modelProvider}\`;
        for (const key of ["name", "base_url", "wire_api", "env_key"]) {
            const configuredValue = normalizeConfigString(providerConfig[key]);
            if (configuredValue) {
                overrides.push([\`\${providerPrefix}.\${key}\`, configuredValue]);
            }
        }
        if (typeof providerConfig.requires_openai_auth === "boolean") {
            overrides.push([\`\${providerPrefix}.requires_openai_auth\`, providerConfig.requires_openai_auth]);
        }
        return overrides;
    }
    catch {
        return [];
    }
}
function injectLaunchConfigOverrides(args, codexHomeOverride) {
    const normalized = [...args];
    for (const [key, value] of readExplicitLaunchOverrides(codexHomeOverride)) {
        if (hasConfigOverrideKey(normalized, key)) {
            continue;
        }
        normalized.push(CONFIG_FLAG, buildConfigOverrideValue(key, value));
    }
    return normalized;
}
export function collectInheritableTeamWorkerArgs`,
      "launch override helpers",
    );
  }

  if (alreadyPatched) {
    console.log(`already patched: ${indexPath}`);
    return;
  }

  const backupPath = `${indexPath}.bak-${timestamp()}`;
  fs.copyFileSync(indexPath, backupPath);
  fs.writeFileSync(indexPath, content, "utf8");
  console.log(`patched: ${indexPath}`);
  console.log(`backup:  ${backupPath}`);
}

function getOmxInstallRoot(): string {
  const omx = runCapture("where", ["omx"], { cwd: process.cwd() });
  if (omx.status !== 0) {
    throw new Error("Unable to resolve omx command source.");
  }
  const scriptPath = omx.stdout.split(/\r?\n/u).find((line) => line.trim().length > 0)?.trim();
  if (!scriptPath) {
    throw new Error("Unable to resolve omx command source.");
  }
  const baseDir = path.dirname(scriptPath);
  const packageRoot = path.join(baseDir, "node_modules", "oh-my-codex");
  if (!fs.existsSync(packageRoot)) {
    throw new Error(`oh-my-codex package root not found at ${packageRoot}`);
  }
  return packageRoot;
}

function assertReplaced(content: string, pattern: RegExp, replacement: string, description: string): string {
  const updated = content.replace(pattern, replacement);
  if (updated === content) {
    throw new Error(`Failed to patch ${description}`);
  }
  return updated;
}

function timestamp(): string {
  return new Date().toISOString().replace(/[-:T]/gu, "").slice(0, 14);
}
