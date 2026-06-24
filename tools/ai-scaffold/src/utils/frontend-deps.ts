import fs from "node:fs";
import path from "node:path";
import { runChecked } from "./exec";

type EnsureOptions = {
  label?: string;
  forceReinstall?: boolean;
};

export function ensureFrontendDependencies(frontendDir: string, options: EnsureOptions = {}): void {
  const packageJsonPath = path.join(frontendDir, "package.json");
  const lockfilePath = path.join(frontendDir, "package-lock.json");
  const nodeModulesDir = path.join(frontendDir, "node_modules");
  const label = options.label ?? path.basename(frontendDir);

  if (!fs.existsSync(packageJsonPath)) {
    throw new Error(`Missing package.json for ${label}: ${packageJsonPath}`);
  }
  if (!fs.existsSync(lockfilePath)) {
    throw new Error(`Missing package-lock.json for ${label}: ${lockfilePath}`);
  }

  if (options.forceReinstall || !fs.existsSync(nodeModulesDir)) {
    reinstallFrontendDependencies(frontendDir, label, options.forceReinstall ? "forced reinstall requested" : "node_modules missing");
    return;
  }

  const packageJson = JSON.parse(fs.readFileSync(packageJsonPath, "utf8")) as {
    dependencies?: Record<string, string>;
    devDependencies?: Record<string, string>;
  };
  const declared = [
    ...Object.keys(packageJson.dependencies ?? {}),
    ...Object.keys(packageJson.devDependencies ?? {}),
  ];

  const missingDirect = declared.filter((name) => !packageExists(nodeModulesDir, name));
  if (missingDirect.length > 0) {
    reinstallFrontendDependencies(frontendDir, label, `missing direct dependency packages: ${missingDirect.join(", ")}`);
    return;
  }

  const missingPlatformPackages = [
    ...getMissingOptionalRuntimePackages(nodeModulesDir, "rollup"),
    ...getMissingOptionalRuntimePackages(nodeModulesDir, "esbuild"),
  ];
  if (missingPlatformPackages.length > 0) {
    reinstallFrontendDependencies(frontendDir, label, `missing platform package(s): ${missingPlatformPackages.join(", ")}`);
  }
}

export function describeFrontendDependencyHealth(frontendDir: string): { ok: boolean; reason: string } {
  const packageJsonPath = path.join(frontendDir, "package.json");
  const lockfilePath = path.join(frontendDir, "package-lock.json");
  const nodeModulesDir = path.join(frontendDir, "node_modules");

  if (!fs.existsSync(packageJsonPath)) {
    return { ok: false, reason: `missing package.json: ${packageJsonPath}` };
  }
  if (!fs.existsSync(lockfilePath)) {
    return { ok: false, reason: `missing package-lock.json: ${lockfilePath}` };
  }
  if (!fs.existsSync(nodeModulesDir)) {
    return { ok: false, reason: `missing node_modules: ${nodeModulesDir}` };
  }

  const packageJson = JSON.parse(fs.readFileSync(packageJsonPath, "utf8")) as {
    dependencies?: Record<string, string>;
    devDependencies?: Record<string, string>;
  };
  const declared = [
    ...Object.keys(packageJson.dependencies ?? {}),
    ...Object.keys(packageJson.devDependencies ?? {}),
  ];
  const missingDirect = declared.filter((name) => !packageExists(nodeModulesDir, name));
  if (missingDirect.length > 0) {
    return { ok: false, reason: `missing direct dependency packages: ${missingDirect.join(", ")}` };
  }

  const missingPlatformPackages = [
    ...getMissingOptionalRuntimePackages(nodeModulesDir, "rollup"),
    ...getMissingOptionalRuntimePackages(nodeModulesDir, "esbuild"),
  ];
  if (missingPlatformPackages.length > 0) {
    return { ok: false, reason: `missing platform package(s): ${missingPlatformPackages.join(", ")}` };
  }

  return { ok: true, reason: "dependency tree looks healthy" };
}

function reinstallFrontendDependencies(frontendDir: string, label: string, reason: string): void {
  console.log(`Reinstalling frontend dependencies for ${label} (${reason}) ...`);
  runChecked("npm", ["ci"], {
    cwd: frontendDir,
    errorMessage: `npm ci failed for ${label}.`,
  });
}

function packageExists(nodeModulesDir: string, packageName: string): boolean {
  const packageJsonPath =
    packageName.startsWith("@")
      ? path.join(nodeModulesDir, ...packageName.split("/"), "package.json")
      : path.join(nodeModulesDir, packageName, "package.json");
  return fs.existsSync(packageJsonPath);
}

function getMissingOptionalRuntimePackages(nodeModulesDir: string, packageName: "rollup" | "esbuild"): string[] {
  const packageJsonPath = path.join(nodeModulesDir, packageName, "package.json");
  if (!fs.existsSync(packageJsonPath)) {
    return [];
  }

  const packageJson = JSON.parse(fs.readFileSync(packageJsonPath, "utf8")) as {
    optionalDependencies?: Record<string, string>;
  };
  const optionalDeps = Object.keys(packageJson.optionalDependencies ?? {});
  if (optionalDeps.length === 0) {
    return [];
  }

  const candidates = getExpectedOptionalPackages(optionalDeps);
  return candidates.filter((candidate) => !packageExists(nodeModulesDir, candidate));
}

function getExpectedOptionalPackages(optionalDeps: string[]): string[] {
  const arch = process.arch;
  const platform = process.platform;

  if (platform === "win32") {
    return optionalDeps.filter((name) => name.includes(`win32-${arch}`));
  }

  if (platform === "darwin") {
    return optionalDeps.filter((name) => name.includes(`darwin-${arch}`));
  }

  if (platform === "linux") {
    const libcFlavor = getLinuxLibcFlavor();
    const exact = optionalDeps.filter((name) => name.includes(`linux-${arch}-${libcFlavor}`));
    if (exact.length > 0) {
      return exact;
    }
    return optionalDeps.filter((name) => name.includes(`linux-${arch}`));
  }

  return [];
}

function getLinuxLibcFlavor(): "gnu" | "musl" {
  const report = typeof process.report?.getReport === "function"
    ? (process.report.getReport() as { header?: { glibcVersionRuntime?: string } })
    : undefined;
  const glibcVersion = report?.header?.glibcVersionRuntime;
  return glibcVersion ? "gnu" : "musl";
}
