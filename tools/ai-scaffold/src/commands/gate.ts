import fs from "node:fs";
import path from "node:path";
import { takeFlag, takeOption, assertNoExtraArgs } from "../utils/cli";
import { ensureDir, listFilesRecursive, readText, removeIfExists } from "../utils/fs";
import { runCapture, runChecked } from "../utils/exec";
import { getFrontmatterMetadata } from "../utils/markdown";
import { resolveRepoPath } from "../utils/paths";
import { ensureFrontendDependencies } from "../utils/frontend-deps";
import { e2eDockerCommand } from "./e2e-docker";
import {
  commandOrDefault,
  expandCommandArgs,
  loadScaffoldConfig,
  resolveCommand,
  resolveConfigPath,
  type CommandSpec,
  type FrontendConfig,
  type ScaffoldConfig,
} from "../config/scaffold-config";

const AC_PATTERN = /([A-Z0-9]+-)?AC-[0-9]+/gu;
const REUSE_EVIDENCE_PATTERN =
  /\b(?:backend\/|frontend\/|docs\/db\/|docs\/features\/|[A-Z][A-Za-z0-9]+(?:Service|Controller|Mapper|Repository|Request|Response|DTO|Dto|Panel|Store|Api|Page|Spec|Test)|\/api\/v[0-9]+\/|[a-z]+:[\w:-]+|(?:public|platform|masterdata|config|wms)\.[a-z][\w]*|(?:sys|wms|pda|inbound|outbound|inventory|receiving)_[a-z][\w]*|CREATE TABLE|\.spec\.ts|\.test\.(?:ts|tsx|java))\b/iu;
const TEMPLATE_PLACEHOLDER_PATTERN =
  /^\s*-?\s*(Existing (backend|frontend|SQL).*:|New seams allowed only if existing seams cannot be reused, because:|Existing services \/ components \/ DTOs \/ SQL \/ permissions \/ test fixtures:)\s*$/imu;

export async function gateCommand(args: string[], context: { repoRoot: string }): Promise<void> {
  const featureDirValue = takeOption(args, "--feature-dir");
  const runE2E = takeFlag(args, "--run-e2e");
  const useDockerE2E = takeFlag(args, "--use-docker-e2e");
  const e2eSpec = takeOption(args, "--e2e-spec");
  const e2eWorkers = Number.parseInt(takeOption(args, "--e2e-workers") ?? "1", 10);
  const skipBackendIntegration = takeFlag(args, "--skip-backend-integration");
  const skipCodeReviewVerdict = takeFlag(args, "--skip-code-review-verdict");
  assertNoExtraArgs(args);

  const featureDir = featureDirValue ? resolveRepoPath(context.repoRoot, featureDirValue) : undefined;
  const config = loadScaffoldConfig(context.repoRoot);
  if (featureDir) {
    runFeatureArtifactChecks(featureDir, { skipCodeReviewVerdict, repoRoot: context.repoRoot });
    console.log("");
  }

  console.log("Running quality gate (backend verify + frontend lint/test:ci/build).");

  const originalJavaHome = process.env.JAVA_HOME;
  const originalPath = process.env.Path ?? process.env.PATH ?? "";

  try {
    setDefaultEnv("SPRING_PROFILES_ACTIVE", "test");
    setDefaultEnv("DB_HOST", "localhost");
    setDefaultEnv("DB_PORT", "5432");
    setDefaultEnv("DB_NAME", config.database.testName);
    setDefaultEnv("DB_USER", config.database.user);
    setDefaultEnv("DB_PASSWORD", config.database.password);
    setDefaultEnv("REDIS_HOST", "localhost");
    setDefaultEnv("REDIS_PORT", "6379");
    setPreferredJavaHome(context.repoRoot);
    ensureTestDatabaseIfDockerAvailable(config);

    const backendDir = resolveConfigPath(context.repoRoot, config.backend.path);
    const frontendEntries = config.frontends
      .map((frontend) => ({ config: frontend, dir: resolveConfigPath(context.repoRoot, frontend.path) }))
      .filter((entry) => fs.existsSync(entry.dir));
    const mavenRepo = path.join(context.repoRoot, ".m2", "repository");
    if (fs.existsSync(backendDir)) {
      ensureDir(mavenRepo);
    }

    console.log("");
    console.log(`Backend: ${config.backend.path} ...`);
    if (!fs.existsSync(backendDir)) {
      console.log(`Backend path does not exist; skipping backend gate: ${config.backend.path}`);
    } else if (skipBackendIntegration) {
      runConfiguredCommand(
        commandOrDefault(config.backend.commands?.verifyWithoutIntegration, { command: "mvn", args: ["verify", "-Dtest=!*IntegrationTest*"] }),
        backendDir,
        "Backend verify failed.",
        { mavenRepo },
      );
    } else {
      runConfiguredCommand(commandOrDefault(config.backend.commands?.verify, { command: "mvn", args: ["verify"] }), backendDir, "Backend verify failed.", {
        mavenRepo,
      });
    }

    for (const { config: frontendConfig, dir: frontendDir } of frontendEntries) {
      const frontendName = path.relative(context.repoRoot, frontendDir);
      console.log("");
      console.log(`Frontend: ${frontendName} ...`);
      ensureFrontendDependencies(frontendDir, { label: frontendName });
      restoreJavaAndPath(originalJavaHome, originalPath);
      runConfiguredCommand(commandOrDefault(frontendConfig.commands?.lint, { command: "npm", args: ["run", "lint"] }), frontendDir, `Frontend lint failed for ${frontendName}.`);
      runConfiguredCommand(commandOrDefault(frontendConfig.commands?.test, { command: "npm", args: ["run", "test:ci"] }), frontendDir, `Frontend test:ci failed for ${frontendName}.`);
      invokeFrontendBuild(frontendDir, frontendConfig);

      if (runE2E && !useDockerE2E) {
        console.log("");
        console.log(`E2E: RUN_E2E -> Playwright (${frontendName}) ...`);
        setE2EDefaults(config);
        runConfiguredCommand(commandOrDefault(frontendConfig.commands?.e2e, { command: "npm", args: ["run", "e2e"] }), frontendDir, `Playwright E2E failed for ${frontendName}.`);
        publishE2EReport(frontendDir, featureDir);
      }
    }

    if (runE2E && useDockerE2E) {
      await e2eDockerCommand(
        [
          "--action",
          "Run",
          "--workers",
          String(e2eWorkers),
          ...(featureDir ? ["--feature-dir", featureDir] : []),
          ...(e2eSpec ? ["--spec", e2eSpec] : []),
        ],
        context,
      );
    }
  } finally {
    restoreJavaAndPath(originalJavaHome, originalPath);
  }

  console.log("");
  console.log("Quality gate passed.");
}

export function runFeatureArtifactChecks(featurePath: string, input: { skipCodeReviewVerdict: boolean; repoRoot: string }): void {
  console.log(`Checking feature artifacts for ${path.basename(featurePath)} ...`);
  testPlanApproved(featurePath);
  testPlanningEvidenceArchived(featurePath);
  testReuseEvidence(featurePath);
  testContractReady(featurePath);
  testTestPlanReferences(featurePath);
  if (!input.skipCodeReviewVerdict) {
    testCodeReviewVerdict(featurePath);
  }
  testTraceability(featurePath, input.repoRoot);
}

function requireSectionMatch(filePath: string, patterns: RegExp[], errorMessage: string): void {
  const content = readText(filePath);
  if (!patterns.some((pattern) => pattern.test(content))) {
    throw new Error(errorMessage);
  }
}

function extractSection(content: string, headingPatterns: RegExp[]): string {
  const lines = content.split(/\r?\n/u);
  let startIndex = -1;
  let startLevel = 0;

  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index];
    const heading = /^(#{1,6})\s+(.+?)\s*$/u.exec(line);
    if (!heading) {
      continue;
    }

    if (headingPatterns.some((pattern) => pattern.test(line))) {
      startIndex = index + 1;
      startLevel = heading[1].length;
      break;
    }
  }

  if (startIndex < 0) {
    return "";
  }

  const sectionLines: string[] = [];
  for (let index = startIndex; index < lines.length; index += 1) {
    const heading = /^(#{1,6})\s+/.exec(lines[index]);
    if (heading && heading[1].length <= startLevel) {
      break;
    }
    sectionLines.push(lines[index]);
  }

  return sectionLines.join("\n");
}

function requireReuseEvidence(filePath: string, headingPatterns: RegExp[], errorMessage: string): void {
  const section = extractSection(readText(filePath), headingPatterns);
  const normalizedSection = section
    .split(/\r?\n/u)
    .filter((line) => !TEMPLATE_PLACEHOLDER_PATTERN.test(line))
    .join("\n");

  if (!REUSE_EVIDENCE_PATTERN.test(normalizedSection)) {
    throw new Error(errorMessage);
  }
}

export function testReuseEvidence(featurePath: string): void {
  const planFile = path.join(featurePath, "plan.md");
  const taskFile = path.join(featurePath, "TASK.md");
  const reuseHeadingPatterns = [/^(##|###)\s*(Reuse Strategy|Reuse Plan|复用策略|复用方案)\s*$/mu, /^(##|###)\s*Reuse\s*:\s*$/mu];

  if (!fs.existsSync(planFile)) {
    throw new Error(`Missing plan.md: ${planFile}`);
  }
  if (!fs.existsSync(taskFile)) {
    throw new Error(`Missing TASK.md: ${taskFile}`);
  }

  requireSectionMatch(
    planFile,
    reuseHeadingPatterns,
    `plan.md must include a dedicated reuse section (Reuse Strategy / 复用策略). File: ${planFile}`,
  );
  requireSectionMatch(
    planFile,
    [/Must Reuse|Duplication Rejected|Approved New Seams/iu, /优先复用|禁止复制|平行实现|新增抽象/u],
    `plan.md reuse section must capture reuse targets and non-reuse rationale. File: ${planFile}`,
  );
  requireReuseEvidence(
    planFile,
    reuseHeadingPatterns,
    `plan.md reuse section must name concrete reusable seams such as code paths, services, DTOs, SQL tables, permissions, API paths, or tests. File: ${planFile}`,
  );

  requireSectionMatch(
    taskFile,
    reuseHeadingPatterns,
    `TASK.md must include a dedicated reuse section (Reuse Plan / 复用方案). File: ${taskFile}`,
  );
  requireReuseEvidence(
    taskFile,
    reuseHeadingPatterns,
    `TASK.md reuse section must name concrete reusable seams such as code paths, services, DTOs, SQL tables, permissions, API paths, or tests. File: ${taskFile}`,
  );
  requireSectionMatch(
    taskFile,
    [/复用审查已完成/u, /reuse review/iu],
    `TASK.md Definition of Done must include the reuse-review requirement. File: ${taskFile}`,
  );
}

export function testPlanApproved(featurePath: string): void {
  const planFile = path.join(featurePath, "plan.md");
  if (!fs.existsSync(planFile)) {
    throw new Error(`Missing plan.md: ${planFile}`);
  }
  const metadata = getFrontmatterMetadata(planFile);
  if (metadata.plan_status !== "approved") {
    throw new Error(`plan.md must have plan_status: approved. File: ${planFile}`);
  }
  if (!metadata.approved_at) {
    throw new Error(`plan.md must include approved_at. File: ${planFile}`);
  }
}

export function testPlanningEvidenceArchived(featurePath: string): void {
  const planningDir = path.join(featurePath, "reports", "planning");
  const requiredPlanningFiles = [
    path.join(planningDir, "deep-interview.md"),
    path.join(planningDir, "prd.md"),
    path.join(planningDir, "test-spec.md"),
  ];
  const missing = requiredPlanningFiles.filter((filePath) => !fs.existsSync(filePath));
  if (missing.length > 0) {
    throw new Error(
      `Missing planning archive file(s) under reports/planning:\n${missing.map((filePath) => ` - ${filePath}`).join("\n")}`,
    );
  }
}

export function getContractStatus(contractFile: string): string {
  for (const line of readText(contractFile).split(/\r?\n/u)) {
    if (/^\s*-\s*(\*\*)?[Ss]tatus(\*\*)?\s*:|^\s*[Ss]tatus\s*:/u.test(line)) {
      return line.replace(/^[^:]*:\s*/u, "").replace(/\*\*/gu, "").trim().replace(/\s+/gu, "").toUpperCase();
    }
  }
  return "";
}

export function testContractReady(featurePath: string): void {
  const contractFile = path.join(featurePath, "contract.md");
  if (!fs.existsSync(contractFile)) {
    throw new Error(`Missing contract.md: ${contractFile}`);
  }
  const status = getContractStatus(contractFile);
  if (!["FROZEN", "IMPLEMENTED"].includes(status)) {
    throw new Error(`contract.md must have status frozen or implemented. File: ${contractFile}`);
  }
}

export function getCodeReviewVerdict(reportFile: string): string {
  for (const line of readText(reportFile).split(/\r?\n/u)) {
    if (/^\s*[-*]\s*(\*\*)?[Vv]erdict(\*\*)?\s*:/u.test(line)) {
      return line.replace(/^[^:]*:\s*/u, "").replace(/\*\*/gu, "").trim();
    }
  }
  return "";
}

export function testCodeReviewVerdict(featurePath: string): void {
  const reportFile = path.join(featurePath, "reports", "code-review-report.md");
  if (!fs.existsSync(reportFile)) {
    throw new Error(`Missing code review report: ${reportFile}`);
  }
  const verdict = getCodeReviewVerdict(reportFile);
  if (!verdict) {
    throw new Error(`Missing Verdict line in code review report: ${reportFile}`);
  }
  if (/CHANGES_REQUIRED|^FAIL$|^REJECT(ED)?$/u.test(verdict)) {
    throw new Error(`Code review verdict is not passing: ${verdict}`);
  }
}

export function getAcIds(filePath: string): string[] {
  if (!fs.existsSync(filePath)) {
    return [];
  }
  return Array.from(readText(filePath).matchAll(AC_PATTERN), (match) => match[0]).sort();
}

export function testTestPlanReferences(featurePath: string): void {
  const taskFile = path.join(featurePath, "TASK.md");
  const testPlanFile = path.join(featurePath, "test-plan.md");
  if (!fs.existsSync(taskFile)) {
    throw new Error(`Missing TASK.md: ${taskFile}`);
  }
  if (!fs.existsSync(testPlanFile)) {
    throw new Error(`Missing test-plan.md: ${testPlanFile}`);
  }
  const testPlanContent = readText(testPlanFile);
  const missingAcs = getAcIds(taskFile).filter((ac) => !testPlanContent.includes(ac));
  if (missingAcs.length > 0) {
    throw new Error(`test-plan.md missing AC reference(s): ${missingAcs.join(", ")}`);
  }
}

export function getTaggedTestFiles(repoRoot: string, featureName: string, featureSlug: string): string[] {
  const traceTag = `TASK-${featureSlug}`;
  const results = new Set<string>();
  const config = loadScaffoldConfig(repoRoot);

  const backendFiles = listFilesRecursive(resolveConfigPath(repoRoot, config.backend.path), (fullPath) =>
    fullPath.endsWith(".java") && fullPath.includes(`${path.sep}src${path.sep}test${path.sep}`),
  );
  for (const filePath of backendFiles) {
    const content = readText(filePath);
    if (content.includes(traceTag) || content.includes(featureName)) {
      results.add(filePath);
    }
  }

  const frontendRoots = config.frontends.flatMap((frontend) => [
    path.join(resolveConfigPath(repoRoot, frontend.path), "src"),
    path.join(resolveConfigPath(repoRoot, frontend.path), "e2e"),
  ]);
  for (const root of frontendRoots) {
    const files = listFilesRecursive(root, (fullPath) => /\.(test|spec)\.tsx?$/u.test(fullPath));
    for (const filePath of files) {
      const content = readText(filePath);
      if (content.includes(traceTag) || content.includes(featureName)) {
        results.add(filePath);
      }
    }
  }

  return [...results].sort();
}

export function testTraceability(featurePath: string, repoRoot: string): void {
  const taskFile = path.join(featurePath, "TASK.md");
  const acIds = getAcIds(taskFile);
  if (acIds.length === 0) {
    return;
  }
  const featureName = path.basename(featurePath);
  const featureSlug = featureName.slice(featureName.indexOf("-") + 1);
  const taggedFiles = getTaggedTestFiles(repoRoot, featureName, featureSlug);
  if (taggedFiles.length === 0) {
    throw new Error(`No tagged automated tests found for ${featureName}. Add TASK-${featureSlug} to relevant test files.`);
  }
  const missingAcs = acIds.filter((ac) => !taggedFiles.some((filePath) => readText(filePath).includes(ac)));
  if (missingAcs.length > 0) {
    throw new Error(`${featureName} traceability missing AC reference(s) in tagged tests: ${missingAcs.join(", ")}`);
  }
}

function setDefaultEnv(name: string, value: string): void {
  if (!process.env[name]) {
    process.env[name] = value;
  }
}

function getJavaMajorVersion(javaExe: string): number {
  const versionResult = runCapture(javaExe, ["-version"], { cwd: process.cwd() });
  if (versionResult.status !== 0) {
    return 0;
  }
  const versionOutput = versionResult.stderr.split(/\r?\n/u)[0] ?? versionResult.stdout.split(/\r?\n/u)[0] ?? "";
  const match = /"(?<version>\d+(?:\.\d+)*)/u.exec(versionOutput);
  if (!match?.groups?.version) {
    return 0;
  }
  const rawVersion = match.groups.version;
  if (rawVersion.startsWith("1.")) {
    return Number.parseInt(rawVersion.split(".")[1]!, 10);
  }
  return Number.parseInt(rawVersion.split(".")[0]!, 10);
}

function setPreferredJavaHome(repoRoot: string): void {
  const candidates: string[] = [];
  if (process.env.JAVA_HOME) {
    candidates.push(process.env.JAVA_HOME);
  }
  const javaRoots = process.platform === "win32"
    ? ["C:\\java", "C:\\Program Files\\Java", "C:\\Program Files\\Eclipse Adoptium", path.join(process.env.USERPROFILE ?? "", ".jdks")]
    : ["/usr/lib/jvm", "/Library/Java/JavaVirtualMachines"];
  for (const javaRoot of javaRoots) {
    if (!fs.existsSync(javaRoot)) continue;
    for (const entry of fs.readdirSync(javaRoot, { withFileTypes: true })) {
      if (entry.isDirectory() && /^jdk|java|temurin|zulu/iu.test(entry.name)) {
        candidates.push(path.join(javaRoot, entry.name));
      }
    }
  }
  const infos = candidates
    .filter((candidate, index) => candidates.indexOf(candidate) === index)
    .map((candidate) => {
      const exeSuffix = process.platform === "win32" ? ".exe" : "";
      const javaExe = path.join(candidate, "bin", `java${exeSuffix}`);
      const javacExe = path.join(candidate, "bin", `javac${exeSuffix}`);
      if (!fs.existsSync(javaExe) || !fs.existsSync(javacExe)) {
        return undefined;
      }
      const major = getJavaMajorVersion(javaExe);
      return major >= 17 ? { candidate, major } : undefined;
    })
    .filter((value): value is { candidate: string; major: number } => Boolean(value))
    .sort((left, right) => right.major - left.major);

  const preferred = infos.find((info) => info.major === 21) ?? infos.find((info) => info.major >= 17 && info.major <= 24) ?? infos[0];
  if (!preferred) {
    return;
  }
  process.env.JAVA_HOME = preferred.candidate;
  const pathName = process.platform === "win32" ? "Path" : "PATH";
  const delimiter = path.delimiter;
  const currentPath = (process.env[pathName] ?? "").split(delimiter).filter(Boolean);
  const filtered = currentPath.filter(
    (entry) => !/\\Java\\|\\jdk-|Oracle\\Java\\javapath/u.test(entry),
  );
  process.env[pathName] = [path.join(preferred.candidate, "bin"), ...filtered].join(delimiter);
}

function ensureTestDatabaseIfDockerAvailable(config: ScaffoldConfig): void {
  if (!config.database.dockerContainer) {
    return;
  }
  const dockerCheck = runCapture("docker", ["ps", "--format", "{{.Names}}"], { cwd: process.cwd() });
  if (dockerCheck.status !== 0) {
    return;
  }
  if (!dockerCheck.stdout.split(/\r?\n/u).includes(config.database.dockerContainer)) {
    return;
  }
  const check = runCapture(
    "docker",
    [
      "exec",
      config.database.dockerContainer,
      "psql",
      "-U",
      config.database.user,
      "-d",
      "postgres",
      "-tAc",
      `SELECT 1 FROM pg_database WHERE datname='${config.database.testName}';`,
    ],
    {
      cwd: process.cwd(),
    },
  );
  if (check.status !== 0 || check.stdout.trim() === "1") {
    return;
  }
  console.log(`Ensuring test database ${config.database.testName} exists via Docker postgres ...`);
  runChecked(
    "docker",
    ["exec", config.database.dockerContainer, "psql", "-U", config.database.user, "-d", "postgres", "-c", `CREATE DATABASE ${config.database.testName};`],
    {
      cwd: process.cwd(),
      errorMessage: `Failed to create ${config.database.testName} database`,
    },
  );
}

function invokeFrontendBuild(workingDirectory: string, frontendConfig: FrontendConfig): void {
  const distDir = path.join(workingDirectory, "dist");
  const indexFile = path.join(distDir, "index.html");
  removeIfExists(distDir);
  runConfiguredCommand(commandOrDefault(frontendConfig.commands?.build, { command: "npm", args: ["run", "build"] }), workingDirectory, "Frontend build failed.");
  if (!fs.existsSync(indexFile)) {
    throw new Error("Frontend build failed.");
  }
}

function setE2EDefaults(config: ScaffoldConfig): void {
  process.env.E2E_USERNAME = config.e2e.username;
  if (config.e2e.password && !process.env.E2E_PASSWORD) {
    process.env.E2E_PASSWORD = config.e2e.password;
  }
  if (config.e2e.tenantCode && !process.env.E2E_TENANT_CODE) {
    process.env.E2E_TENANT_CODE = config.e2e.tenantCode;
  }
}

function publishE2EReport(frontendPath: string, featurePath?: string): void {
  if (!featurePath) {
    return;
  }
  const sourceReportDir = path.join(frontendPath, "playwright-report");
  if (!fs.existsSync(sourceReportDir)) {
    console.warn(`Playwright report directory not found: ${sourceReportDir}`);
    return;
  }
  const featureName = path.basename(featurePath);
  const frontendName = path.basename(frontendPath);
  const destinationRoot = path.join(featurePath, "reports", "e2e");
  ensureDir(destinationRoot);
  const timestamp = new Date().toISOString().replace(/[:T]/gu, "").slice(0, 13);
  const destinationDir = path.join(destinationRoot, `${timestamp}-${frontendName}-playwright-report`);
  fs.cpSync(sourceReportDir, destinationDir, { force: true, recursive: true });
  console.log(`Archived Playwright report for ${featureName} -> ${destinationDir}`);
}

function restoreJavaAndPath(javaHome: string | undefined, originalPath: string): void {
  process.env.JAVA_HOME = javaHome;
  process.env[process.platform === "win32" ? "Path" : "PATH"] = originalPath;
}

function runConfiguredCommand(spec: CommandSpec, cwd: string, errorMessage: string, replacements: Record<string, string> = {}): void {
  runChecked(resolveCommand(spec.command), expandCommandArgs(spec.args, replacements), {
    cwd,
    errorMessage,
  });
}
