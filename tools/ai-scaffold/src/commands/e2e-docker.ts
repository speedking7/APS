import { spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { assertNoExtraArgs, requireOption, takeFlag, takeOption } from "../utils/cli";
import { copyDir, ensureDir } from "../utils/fs";
import { runCapture, runChecked } from "../utils/exec";
import { resolveRepoPath } from "../utils/paths";
import { loadScaffoldConfig, resolveConfigPath, type ScaffoldConfig } from "../config/scaffold-config";

type DockerAction = "Run" | "Start" | "Stop" | "Reset" | "Status";

export async function e2eDockerCommand(args: string[], context: { repoRoot: string }): Promise<void> {
  const action = (takeOption(args, "--action") ?? "Run") as DockerAction;
  const featureDirValue = takeOption(args, "--feature-dir");
  const spec = takeOption(args, "--spec");
  const config = loadScaffoldConfig(context.repoRoot);
  const project = takeOption(args, "--project") ?? config.e2eDocker?.playwrightProject ?? "chromium";
  const workers = Number.parseInt(takeOption(args, "--workers") ?? "1", 10);
  const baseUrl = takeOption(args, "--base-url") ?? config.e2eDocker?.frontendBaseUrl ?? "http://127.0.0.1:4173";
  const username = takeOption(args, "--username") ?? config.e2e.username;
  const password = takeOption(args, "--password") ?? config.e2e.password ?? "change-me";
  const tenantCode = takeOption(args, "--tenant-code") ?? config.e2e.tenantCode ?? "default";
  const skipReset = takeFlag(args, "--skip-reset");
  const downAfterRun = takeFlag(args, "--down-after-run");
  assertNoExtraArgs(args);

  if (!["Run", "Start", "Stop", "Reset", "Status"].includes(action)) {
    throw new Error(`Invalid --action: ${action}`);
  }

  const backendDir = resolveConfigPath(context.repoRoot, config.e2eDocker?.backendComposeDir ?? config.backend.path);
  const frontendDir = resolveConfigPath(context.repoRoot, config.e2eDocker?.frontendPath ?? config.frontends[0]?.path ?? "frontend");
  const composeFiles = config.e2eDocker?.composeFiles ?? ["docker-compose.yml"];
  const dockerServices = config.e2eDocker?.services ?? ["postgres", "redis", "backend", "frontend"];
  const postgresContainer = config.e2eDocker?.postgresContainer ?? config.database.dockerContainer ?? "postgres";
  const healthUrl = config.e2eDocker?.backendHealthUrl ?? "http://127.0.0.1:8080/actuator/health";
  const sqlFiles = (config.e2eDocker?.postBootstrapSql ?? []).map((sqlPath) => resolveConfigPath(context.repoRoot, sqlPath));

  const resolvedFeatureDir = featureDirValue ? resolveRepoPath(context.repoRoot, featureDirValue) : undefined;

  switch (action) {
    case "Start":
      process.env.E2E_BACKEND_HEALTH_URL = healthUrl;
      startEnvironment({ backendDir, composeFiles, dockerServices, postgresContainer, sqlFiles, healthUrl, config });
      showStatus(postgresContainer);
      return;
    case "Stop":
      dockerCompose({ backendDir, composeFiles, args: ["down"] });
      return;
    case "Reset":
      dockerCompose({ backendDir, composeFiles, args: ["down", "-v"] });
      startEnvironment({ backendDir, composeFiles, dockerServices, postgresContainer, sqlFiles, healthUrl, config });
      showStatus(postgresContainer);
      return;
    case "Status":
      process.env.E2E_BACKEND_HEALTH_URL = healthUrl;
      showStatus(postgresContainer);
      return;
    case "Run":
      process.env.E2E_BACKEND_HEALTH_URL = healthUrl;
      if (skipReset) {
        startEnvironment({ backendDir, composeFiles, dockerServices, postgresContainer, sqlFiles, healthUrl, config });
      } else {
        dockerCompose({ backendDir, composeFiles, args: ["down", "-v"] });
        startEnvironment({ backendDir, composeFiles, dockerServices, postgresContainer, sqlFiles, healthUrl, config });
      }

      try {
        runPlaywright({
          frontendDir,
          spec,
          project,
          workers,
          baseUrl,
          username,
          password,
          tenantCode,
        });
        publishReport(frontendDir, resolvedFeatureDir);
      } finally {
        if (downAfterRun) {
          dockerCompose({ backendDir, composeFiles, args: ["down"] });
        }
      }
      return;
    default:
      throw new Error(`Unsupported action: ${action satisfies never}`);
  }
}

function dockerCompose(input: { backendDir: string; composeFiles: string[]; args: string[] }): void {
  const env = {
    ...process.env,
    DOCKER_BUILDKIT: "0",
    COMPOSE_DOCKER_CLI_BUILD: "0",
  };
  const dockerArgs = ["compose"];
  for (const composeFile of input.composeFiles) {
    const resolved = path.isAbsolute(composeFile) ? composeFile : path.join(input.backendDir, composeFile);
    if (pathExists(resolved)) {
      dockerArgs.push("-f", resolved);
    } else if (composeFile === input.composeFiles[0]) {
      dockerArgs.push("-f", composeFile);
    }
  }
  dockerArgs.push(...input.args);
  runChecked("docker", dockerArgs, {
    cwd: input.backendDir,
    env,
    errorMessage: `docker compose failed: ${input.args.join(" ")}`,
  });
}

function waitServiceReady(url: string, timeoutSeconds = 120): void {
  const deadline = Date.now() + timeoutSeconds * 1000;
  while (Date.now() < deadline) {
    const result = runCapture(
      process.execPath,
      [
        "-e",
        "fetch(process.argv[1]).then(r=>r.json()).then(j=>{if(j.status==='UP'){process.exit(0)}process.exit(1)}).catch(()=>process.exit(1))",
        url,
      ],
      { cwd: process.cwd() },
    );
    if (result.status === 0) {
      return;
    }

    Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 3000);
  }

  throw new Error(`Timed out waiting for service health at ${url}`);
}

function ensureTestDatabase(postgresContainer: string, config: ScaffoldConfig): void {
  const postgresAdminDatabase = config.e2eDocker?.postgresAdminDatabase ?? "postgres";
  const check = runCapture("docker", ["exec", postgresContainer, "psql", "-U", config.database.user, "-d", postgresAdminDatabase, "-tAc", `SELECT 1 FROM pg_database WHERE datname='${config.database.testName}';`], {
    cwd: process.cwd(),
  });
  if (check.status !== 0) {
    throw new Error(`Failed to query postgres for ${config.database.testName}`);
  }
  if (check.stdout.trim() !== "1") {
    runChecked("docker", ["exec", postgresContainer, "psql", "-U", config.database.user, "-d", postgresAdminDatabase, "-c", `CREATE DATABASE ${config.database.testName};`], {
      cwd: process.cwd(),
      errorMessage: `Failed to create ${config.database.testName} database`,
    });
  }
}

function applyPostBootstrapSql(postgresContainer: string, sqlFiles: string[], config: ScaffoldConfig): void {
  for (const sqlPath of sqlFiles) {
    if (!pathExists(sqlPath)) {
      continue;
    }
    console.log(`Applying post-bootstrap E2E SQL -> ${sqlPath}`);
    const sql = fs.readFileSync(sqlPath, "utf8");
    const databaseName = config.e2eDocker?.applicationDatabase ?? config.database.testName;
    const result = spawnSync("docker", ["exec", "-i", postgresContainer, "psql", "-U", config.database.user, "-d", databaseName], {
      cwd: process.cwd(),
      input: sql,
      stdio: ["pipe", "inherit", "inherit"],
      encoding: "utf8",
    });
    if (result.status !== 0) {
      throw new Error(`Failed to apply post-bootstrap SQL: ${sqlPath}`);
    }
  }
}

function startEnvironment(input: {
  backendDir: string;
  composeFiles: string[];
  dockerServices: string[];
  postgresContainer: string;
  sqlFiles: string[];
  healthUrl: string;
  config: ScaffoldConfig;
}): void {
  dockerCompose({
    backendDir: input.backendDir,
    composeFiles: input.composeFiles,
    args: ["up", "-d", "--build", ...input.dockerServices],
  });
  waitServiceReady(input.healthUrl);
  ensureTestDatabase(input.postgresContainer, input.config);
  applyPostBootstrapSql(input.postgresContainer, input.sqlFiles, input.config);
}

function showStatus(postgresContainer: string): void {
  runChecked("docker", ["ps", "--format", "table {{.Names}}\t{{.Status}}\t{{.Ports}}"], {
    cwd: process.cwd(),
    errorMessage: "docker ps failed",
  });

  const health = runCapture(
    process.execPath,
    [
      "-e",
        "fetch(process.argv[1]).then(r=>r.json()).then(j=>console.log(j.status)).catch(()=>process.exit(1))",
        process.env.E2E_BACKEND_HEALTH_URL ?? "http://127.0.0.1:8080/actuator/health",
    ],
    { cwd: process.cwd() },
  );
  if (health.status === 0) {
    console.log("");
    console.log(`Backend health: ${health.stdout.trim()}`);
  } else {
    console.warn("Backend health endpoint is not ready.");
  }
}

function runPlaywright(input: {
  frontendDir: string;
  spec?: string;
  project?: string;
  workers: number;
  baseUrl: string;
  username: string;
  password: string;
  tenantCode: string;
}): void {
  const env = {
    ...process.env,
    E2E_BASE_URL: input.baseUrl,
    E2E_USERNAME: input.username,
    E2E_PASSWORD: input.password,
    E2E_TENANT_CODE: input.tenantCode,
    E2E_WORKERS: String(input.workers),
  };
  const args = ["node_modules/@playwright/test/cli.js", "test"];
  if (input.spec) {
    args.push(input.spec);
  }
  if (input.project) {
    args.push(`--project=${input.project}`);
  }
  runChecked(process.execPath, args, {
    cwd: input.frontendDir,
    env,
    errorMessage: "Playwright failed",
  });
}

function publishReport(frontendDir: string, featureDir?: string): void {
  if (!featureDir) {
    return;
  }

  const sourceDir = path.join(frontendDir, "playwright-report");
  if (!pathExists(sourceDir)) {
    console.warn(`Playwright report directory not found: ${sourceDir}`);
    return;
  }

  const destinationRoot = path.join(featureDir, "reports", "e2e");
  ensureDir(destinationRoot);
  const timestamp = new Date().toISOString().replace(/[:T]/gu, "-").slice(0, 15);
  const destinationDir = path.join(destinationRoot, `${timestamp}-playwright-report`);
  copyDir(sourceDir, destinationDir);
  console.log(`Archived Playwright report -> ${destinationDir}`);
}

function pathExists(targetPath: string): boolean {
  return fs.existsSync(targetPath);
}
