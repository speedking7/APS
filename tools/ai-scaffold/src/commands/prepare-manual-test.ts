import fs from "node:fs";
import path from "node:path";
import { assertNoExtraArgs, takeFlag, takeOption } from "../utils/cli";
import { ensureDir, writeText } from "../utils/fs";
import { runCapture, runChecked, startDetachedProcess } from "../utils/exec";
import { ensureFrontendDependencies } from "../utils/frontend-deps";
import { sleep } from "../utils/time";
import {
  commandOrDefault,
  expandCommandArgs,
  loadScaffoldConfig,
  resolveCommand,
  resolveConfigPath,
  type CommandSpec,
  type ScaffoldConfig,
} from "../config/scaffold-config";

type AppMode = "auto" | "local" | "docker";
type FrontendMode = "preview" | "dev";

export async function prepareManualTestCommand(args: string[], context: { repoRoot: string }): Promise<void> {
  const appMode = (takeOption(args, "--app-mode") ?? "auto") as AppMode;
  const frontendMode = (takeOption(args, "--frontend-mode") ?? "preview") as FrontendMode;
  const skipPlaywrightInstall = takeFlag(args, "--skip-playwright-install");
  const skipSmokeCheck = takeFlag(args, "--skip-smoke-check");
  assertNoExtraArgs(args);

  if (!["auto", "local", "docker"].includes(appMode)) {
    throw new Error(`Invalid --app-mode: ${appMode}`);
  }
  if (!["preview", "dev"].includes(frontendMode)) {
    throw new Error(`Invalid --frontend-mode: ${frontendMode}`);
  }

  const repoRoot = context.repoRoot;
  const config = loadScaffoldConfig(repoRoot);
  const composeFile = resolveConfigPath(repoRoot, config.manualTest?.composeFile ?? "backend/docker-compose.yml");
  const backendDir = resolveConfigPath(repoRoot, config.manualTest?.backendPath ?? config.backend.path);
  const frontendDir = resolveConfigPath(repoRoot, config.manualTest?.frontendPath ?? config.frontends[0]?.path ?? "frontend");
  const stateDir = path.join(repoRoot, ".codex", "tasks", "tmp", "manual-test");
  const logsDir = path.join(stateDir, "logs");
  const stateFile = path.join(stateDir, "session.json");
  const backendPort = config.manualTest?.backendPort ?? 8080;
  const frontendPort = frontendMode === "preview" ? config.manualTest?.frontendPreviewPort ?? 4173 : config.manualTest?.frontendDevPort ?? 5173;
  const backendBaseUrl = `http://127.0.0.1:${backendPort}`;
  const frontendBaseUrl = `http://127.0.0.1:${frontendPort}`;
  const backendHealthUrl = `${backendBaseUrl}${config.manualTest?.backendHealthPath ?? "/actuator/health"}`;
  const loginUrl = `${backendBaseUrl}${config.manualTest?.loginPath ?? "/api/v1/auth/login"}`;
  const backendLog = path.join(logsDir, "backend.log");
  const backendErr = path.join(logsDir, "backend.err.log");
  const frontendLog = path.join(logsDir, "frontend.log");
  const frontendErr = path.join(logsDir, "frontend.err.log");
  const databasePatchSql = config.manualTest?.databasePatchSql
    ? resolveConfigPath(repoRoot, config.manualTest.databasePatchSql)
    : undefined;

  ensureDir(stateDir);
  ensureDir(logsDir);
  stopStalePreparedProcesses(stateFile);
  ensureInfra(composeFile, config);
  if (databasePatchSql) {
    applyDatabasePatch(databasePatchSql, config);
  }

  ensureFrontendDependencies(frontendDir, { label: config.manualTest?.frontendPath ?? config.frontends[0]?.path ?? "frontend" });

  if (!skipPlaywrightInstall && config.manualTest?.playwrightInstallCommand) {
    console.log("Ensuring Playwright browsers are installed ...");
    runConfiguredCommand(config.manualTest.playwrightInstallCommand, frontendDir, "Playwright browser install failed.", {});
  }

  const javaHomeCandidate = getPreferredJavaHome();
  let runtimeState:
    | {
        runtimeMode: "local" | "docker";
        frontendMode: FrontendMode | "preview";
        stoppedContainers: string[];
        dockerContainers?: string[];
        processes?: {
          backend: { pid: number; stdout: string; stderr: string };
          frontend: { pid: number; stdout: string; stderr: string };
        };
      };

  if (appMode === "local") {
    runtimeState = await startLocalApps({
      composeFile,
      backendDir,
      frontendDir,
      frontendMode,
      backendLog,
      backendErr,
      frontendLog,
      frontendErr,
      backendHealthUrl,
      frontendBaseUrl,
      config,
    });
  } else if (appMode === "docker") {
    runtimeState = await startDockerApps(composeFile, backendHealthUrl, config);
  } else if (javaHomeCandidate) {
    try {
      runtimeState = await startLocalApps({
        composeFile,
        backendDir,
        frontendDir,
        frontendMode,
        backendLog,
        backendErr,
        frontendLog,
        frontendErr,
        backendHealthUrl,
        frontendBaseUrl,
        config,
      });
    } catch (error) {
      console.warn(`Local app startup failed, falling back to Docker app mode. Reason: ${error instanceof Error ? error.message : String(error)}`);
      stopStalePreparedProcesses(stateFile);
      runtimeState = await startDockerApps(composeFile, backendHealthUrl, config);
    }
  } else {
    runtimeState = await startDockerApps(composeFile, backendHealthUrl, config);
  }

  if (!skipSmokeCheck) {
    console.log("Running smoke checks (backend health + admin login + frontend root) ...");
    await invokeLoginSmokeCheck(loginUrl);
    if (runtimeState.runtimeMode === "local") {
      await waitHttpReady(frontendBaseUrl, 15);
    }
  }

  const state = {
    preparedAt: new Date().toISOString(),
    runtimeMode: runtimeState.runtimeMode,
    frontendMode: runtimeState.frontendMode,
    urls: {
      frontend: frontendBaseUrl,
      backend: backendBaseUrl,
      health: backendHealthUrl,
      login: loginUrl,
    },
    credentials: {
      username: "admin",
      password: config.e2e.password ?? "change-me",
      tenantCode: config.e2e.tenantCode ?? "default",
    },
    ...(runtimeState.processes ? { processes: runtimeState.processes } : {}),
    ...(runtimeState.stoppedContainers ? { stoppedContainers: runtimeState.stoppedContainers } : {}),
    ...(runtimeState.dockerContainers ? { dockerContainers: runtimeState.dockerContainers } : {}),
  };

  writeText(stateFile, `${JSON.stringify(state, null, 2)}\n`);

  console.log("");
  console.log("Manual test environment is ready.");
  console.log(`Mode     : ${state.runtimeMode}`);
  console.log(`Frontend : ${frontendBaseUrl}`);
  console.log(`Backend  : ${backendBaseUrl}`);
  console.log(`Health   : ${backendHealthUrl}`);
  console.log(`Login    : ${config.e2e.username} / ${config.e2e.password ?? "(set E2E_PASSWORD)"} / tenantCode=${config.e2e.tenantCode ?? "default"}`);
  console.log(`State    : ${stateFile}`);
  console.log(`Logs     : ${logsDir}`);
  console.log("");
  console.log("When finished, run:");
  console.log("node tools/ai-scaffold/dist/cli.js stop-manual-test");
}

function ensureCommand(name: string): void {
  const result = runCapture(process.platform === "win32" ? "where" : "which", [name], { cwd: process.cwd() });
  if (result.status !== 0) {
    throw new Error(`Missing required command: ${name}`);
  }
}

function testContainerRunning(name: string): boolean {
  const result = runCapture("docker", ["ps", "--format", "{{.Names}}"], { cwd: process.cwd() });
  return result.status === 0 && result.stdout.split(/\r?\n/u).includes(name);
}

async function waitContainerHealthy(containerName: string, timeoutSeconds = 180): Promise<void> {
  const deadline = Date.now() + timeoutSeconds * 1000;
  while (Date.now() < deadline) {
    const result = runCapture(
      "docker",
      ["inspect", "--format", "{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}", containerName],
      { cwd: process.cwd() },
    );
    if (result.status === 0) {
      const status = result.stdout.trim();
      if (status === "healthy" || status === "running") {
        return;
      }
    }
    await sleep(2000);
  }
  throw new Error(`Container did not become ready in time: ${containerName}`);
}

function ensureInfra(composeFile: string, config: ScaffoldConfig): void {
  const services = config.manualTest?.infraServices ?? ["postgres", "redis"];
  if (services.length === 0) {
    console.log("No Docker infrastructure services configured; skipping infra startup.");
    return;
  }
  console.log(`Ensuring Docker infrastructure services are running (${services.join(", ")}) ...`);
  runChecked("docker", ["compose", "-f", composeFile, "up", "-d", ...services], {
    cwd: path.dirname(composeFile),
    errorMessage: "Failed to start postgres/redis via docker compose.",
  });
}

function applyDatabasePatch(sqlFile: string, config: ScaffoldConfig): void {
  if (!fs.existsSync(sqlFile)) {
    throw new Error(`Missing SQL patch file: ${sqlFile}`);
  }
  const container = config.database.dockerContainer ?? "postgres";
  const databaseName = config.e2eDocker?.applicationDatabase ?? config.database.testName;
  console.log(`Applying database patch: ${path.basename(sqlFile)}`);
  const containerPath = `/tmp/${path.basename(sqlFile)}`;
  runChecked("docker", ["cp", sqlFile, `${container}:${containerPath}`], {
    cwd: process.cwd(),
    errorMessage: `Failed to copy database patch into container: ${sqlFile}`,
  });
  runChecked("docker", ["exec", container, "psql", "-U", config.database.user, "-d", databaseName, "-f", containerPath], {
    cwd: process.cwd(),
    errorMessage: `Failed to apply database patch: ${sqlFile}`,
  });
}

function stopAppContainers(config: ScaffoldConfig): string[] {
  const stopped: string[] = [];
  for (const name of config.manualTest?.appContainers ?? ["backend", "frontend"]) {
    if (testContainerRunning(name)) {
      console.log(`Stopping Docker app container to free local ports: ${name}`);
      runChecked("docker", ["stop", name], {
        cwd: process.cwd(),
        errorMessage: `Failed to stop container: ${name}`,
      });
      stopped.push(name);
    }
  }
  return stopped;
}

function stopStalePreparedProcesses(stateFile: string): void {
  if (!fs.existsSync(stateFile)) return;
  let state: {
    processes?: { backend?: { pid?: number }; frontend?: { pid?: number } };
  };
  try {
    state = JSON.parse(fs.readFileSync(stateFile, "utf8"));
  } catch {
    return;
  }
  for (const processName of ["backend", "frontend"] as const) {
    const pid = state.processes?.[processName]?.pid;
    if (!pid) continue;
    try {
      process.kill(pid, "SIGKILL");
      console.log(`Stopping stale prepared process: ${processName} (${pid})`);
    } catch {
      // ignore
    }
  }
}

async function waitHttpReady(url: string, timeoutSeconds = 240): Promise<void> {
  const deadline = Date.now() + timeoutSeconds * 1000;
  while (Date.now() < deadline) {
    try {
      const response = await fetch(url, { method: "GET" });
      if (response.status >= 200 && response.status < 500) {
        return;
      }
    } catch {
      // ignore
    }
    await sleep(2000);
  }
  throw new Error(`HTTP endpoint did not become ready in time: ${url}`);
}

async function invokeLoginSmokeCheck(loginUrl: string): Promise<void> {
  const config = loadScaffoldConfig(findRepoRootFromCwd());
  const response = await fetch(loginUrl, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      username: config.e2e.username,
      password: config.e2e.password ?? process.env.E2E_PASSWORD ?? "change-me",
      tenantCode: config.e2e.tenantCode ?? "default",
    }),
  });
  const payload = (await response.json()) as { code?: string | number; success?: boolean };
  const codeValue = payload.code !== undefined ? String(payload.code) : "";
  if (!payload || (!payload.success && !["0", "200"].includes(codeValue))) {
    throw new Error("Login smoke check failed.");
  }
}

function getJavaMajorVersion(javaExe: string): number {
  if (!fs.existsSync(javaExe)) return 0;
  const result = runCapture(javaExe, ["-version"], { cwd: process.cwd() });
  if (result.status !== 0) return 0;
  const line = result.stderr.split(/\r?\n/u)[0] || result.stdout.split(/\r?\n/u)[0] || "";
  const match = /"(?<version>\d+(?:\.\d+)*)/u.exec(line);
  if (!match?.groups?.version) return 0;
  const raw = match.groups.version;
  if (raw.startsWith("1.")) {
    return Number.parseInt(raw.split(".")[1]!, 10);
  }
  return Number.parseInt(raw.split(".")[0]!, 10);
}

function getPreferredJavaHome(): string | null {
  const candidates = new Set<string>();
  if (process.env.JAVA_HOME) candidates.add(process.env.JAVA_HOME);
  const patterns =
    process.platform === "win32"
      ? ["C:\\java", "C:\\Program Files\\Java", "C:\\Program Files\\Eclipse Adoptium", path.join(process.env.USERPROFILE ?? "", ".jdks")]
      : ["/usr/lib/jvm", "/Library/Java/JavaVirtualMachines"];
  for (const base of patterns) {
    if (!fs.existsSync(base)) continue;
    for (const entry of fs.readdirSync(base, { withFileTypes: true })) {
      if (entry.isDirectory() && entry.name.toLowerCase().startsWith("jdk")) {
        candidates.add(path.join(base, entry.name));
      }
    }
  }
  const userJdks = path.join(process.env.USERPROFILE ?? "", ".jdks");
  if (fs.existsSync(userJdks)) {
    for (const entry of fs.readdirSync(userJdks, { withFileTypes: true })) {
      if (entry.isDirectory()) {
        candidates.add(path.join(userJdks, entry.name));
      }
    }
  }
  const infos = [...candidates]
    .map((candidate) => {
      const exeSuffix = process.platform === "win32" ? ".exe" : "";
      const javaExe = path.join(candidate, "bin", `java${exeSuffix}`);
      const javacExe = path.join(candidate, "bin", `javac${exeSuffix}`);
      if (!fs.existsSync(javaExe) || !fs.existsSync(javacExe)) return undefined;
      const major = getJavaMajorVersion(javaExe);
      return major >= 17 ? { candidate, major } : undefined;
    })
    .filter((value): value is { candidate: string; major: number } => Boolean(value))
    .sort((left, right) => right.major - left.major);
  return infos[0]?.candidate ?? null;
}

async function startLocalApps(input: {
  composeFile: string;
  backendDir: string;
  frontendDir: string;
  frontendMode: FrontendMode;
  backendLog: string;
  backendErr: string;
  frontendLog: string;
  frontendErr: string;
  backendHealthUrl: string;
  frontendBaseUrl: string;
  config: ScaffoldConfig;
}): Promise<{
  runtimeMode: "local";
  frontendMode: FrontendMode;
  stoppedContainers: string[];
  processes: {
    backend: { pid: number; stdout: string; stderr: string };
    frontend: { pid: number; stdout: string; stderr: string };
  };
}> {
  const javaHome = getPreferredJavaHome();
  if (!javaHome) {
    throw new Error("No JDK 17+ installation was found for local Spring Boot startup.");
  }

  const stoppedContainers = stopAppContainers(input.config);
  if (input.frontendMode === "preview") {
    console.log("Building frontend preview assets ...");
    const frontendConfig = input.config.frontends.find((frontend) => resolveConfigPath(process.cwd(), frontend.path) === input.frontendDir) ?? input.config.frontends[0];
    runConfiguredCommand(commandOrDefault(frontendConfig?.commands?.build, { command: "npm", args: ["run", "build"] }), input.frontendDir, "Frontend build failed.", {});
  }

  const envBase = {
    ...process.env,
    JAVA_HOME: javaHome,
    [process.platform === "win32" ? "Path" : "PATH"]: [
      path.join(javaHome, "bin"),
      ...(process.env[process.platform === "win32" ? "Path" : "PATH"] ?? "").split(path.delimiter).filter(Boolean),
    ].join(path.delimiter),
    DB_HOST: process.env.DB_HOST ?? "localhost",
    DB_PORT: process.env.DB_PORT ?? "5432",
    DB_NAME: process.env.DB_NAME ?? input.config.database.testName,
    DB_USER: process.env.DB_USER ?? input.config.database.user,
    DB_PASSWORD: process.env.DB_PASSWORD ?? input.config.database.password,
    REDIS_HOST: "localhost",
    REDIS_PORT: "6379",
    ...input.config.manualTest?.localEnv,
  };

  console.log("Starting local backend ...");
  const backendCommand = input.config.manualTest?.backendCommand ?? { command: "mvn", args: ["spring-boot:run"] };
  const backendPid = startDetachedProcess(resolveCommand(backendCommand.command), expandCommandArgs(backendCommand.args, {}), {
    cwd: input.backendDir,
    env: envBase,
    stdoutPath: input.backendLog,
    stderrPath: input.backendErr,
  });
  await waitHttpReady(input.backendHealthUrl);

  console.log(`Starting local frontend (${input.frontendMode}) ...`);
  const frontendCommand =
    input.frontendMode === "preview"
      ? input.config.manualTest?.frontendPreviewCommand ?? { command: "npm", args: ["run", "preview", "--", "--host", "127.0.0.1", "--port", "{port}"] }
      : input.config.manualTest?.frontendDevCommand ?? { command: "npm", args: ["run", "dev", "--", "--host", "127.0.0.1", "--port", "{port}"] };
  const frontendPid = startDetachedProcess(resolveCommand(frontendCommand.command), expandCommandArgs(frontendCommand.args, { port: String(input.frontendBaseUrl.split(":").pop() ?? "") }), {
    cwd: input.frontendDir,
    env: {
      ...process.env,
      VITE_API_PROXY_TARGET: "http://127.0.0.1:8080",
      VITE_WS_PROXY_TARGET: "ws://127.0.0.1:8080",
      ...input.config.manualTest?.frontendEnv,
    },
    stdoutPath: input.frontendLog,
    stderrPath: input.frontendErr,
  });
  await waitHttpReady(input.frontendBaseUrl);

  return {
    runtimeMode: "local",
    frontendMode: input.frontendMode,
    stoppedContainers,
    processes: {
      backend: { pid: backendPid, stdout: input.backendLog, stderr: input.backendErr },
      frontend: { pid: frontendPid, stdout: input.frontendLog, stderr: input.frontendErr },
    },
  };
}

async function startDockerApps(composeFile: string, backendHealthUrl: string, config?: ScaffoldConfig): Promise<{
  runtimeMode: "docker";
  frontendMode: "preview";
  stoppedContainers: string[];
  dockerContainers: string[];
}> {
  const containers = config?.manualTest?.appContainers ?? ["backend", "frontend"];
  const allRunning = containers.every((name) => testContainerRunning(name));
  if (allRunning) {
    console.log("Docker app containers are already running; reusing them for manual testing.");
  } else {
    console.log("Starting Docker app containers from current workspace ...");
    runChecked("docker", ["compose", "-f", composeFile, "up", "-d", "--build", ...(config?.manualTest?.appServices ?? containers)], {
      cwd: path.dirname(composeFile),
      errorMessage: "Failed to build/start Docker app containers.",
    });
  }
  for (const container of containers) {
    await waitContainerHealthy(container);
  }
  await waitHttpReady(backendHealthUrl);
  await waitHttpReady(`http://127.0.0.1:${config?.manualTest?.frontendPreviewPort ?? 4173}`);
  return {
    runtimeMode: "docker",
    frontendMode: "preview",
    stoppedContainers: [],
    dockerContainers: containers,
  };
}

function runConfiguredCommand(spec: CommandSpec, cwd: string, errorMessage: string, replacements: Record<string, string>): void {
  runChecked(resolveCommand(spec.command), expandCommandArgs(spec.args, replacements), {
    cwd,
    errorMessage,
  });
}

function findRepoRootFromCwd(): string {
  let current = process.cwd();
  while (true) {
    if (fs.existsSync(path.join(current, "ai-scaffold.config.json")) || fs.existsSync(path.join(current, "AGENTS.md"))) {
      return current;
    }
    const parent = path.dirname(current);
    if (parent === current) {
      return process.cwd();
    }
    current = parent;
  }
}
