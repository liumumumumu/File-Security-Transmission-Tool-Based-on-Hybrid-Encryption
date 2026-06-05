import { cp, mkdir, rm } from "node:fs/promises";
import { access } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptPath = fileURLToPath(import.meta.url);
const scriptDir = path.dirname(scriptPath);
const desktopRoot = path.resolve(scriptDir, "..");
const repoRoot = path.resolve(desktopRoot, "..");

export function normalizeTargetPlatform(platform = process.platform) {
  if (platform === "mac" || platform === "macos") {
    return "darwin";
  }
  if (platform === "windows") {
    return "win32";
  }
  return platform;
}

export function normalizeTargetArch(arch = process.arch) {
  if (arch === "x86_64" || arch === "amd64") {
    return "x64";
  }
  if (arch === "aarch64") {
    return "arm64";
  }
  return arch;
}

export function resolveCryptoRuntimeName({
  platform = process.platform,
  arch = process.arch,
} = {}) {
  const targetPlatform = normalizeTargetPlatform(platform);

  if (targetPlatform === "win32" || targetPlatform === "darwin" || targetPlatform === "linux") {
    return "crypto-service";
  }
  throw new Error(`Unsupported desktop runtime platform: ${targetPlatform}`);
}

function getCryptoRequiredFiles(platform = process.platform) {
  const targetPlatform = normalizeTargetPlatform(platform);

  if (targetPlatform === "win32") {
    return [
      { name: "crypto-service.exe", label: "Crypto service executable" },
    ];
  }

  if (targetPlatform === "darwin") {
    return [
      { name: "crypto-service", label: "Crypto service executable" },
      { name: "libssl.3.dylib", label: "Crypto service OpenSSL runtime" },
      { name: "libcrypto.3.dylib", label: "Crypto service libcrypto runtime" },
    ];
  }

  if (targetPlatform === "linux") {
    return [
      { name: "crypto-service", label: "Crypto service executable" },
      { name: "libssl.so.3", label: "Crypto service OpenSSL runtime" },
      { name: "libcrypto.so.3", label: "Crypto service libcrypto runtime" },
    ];
  }

  throw new Error(`Unsupported desktop runtime platform: ${targetPlatform}`);
}

export async function findSingleFile(directory, patterns, listFiles = defaultListFiles) {
  const files = await listFiles(directory);
  const match = files.find((file) => patterns.some((pattern) => pattern.test(file)));
  if (!match) {
    throw new Error(`No runtime artifact found in ${directory}`);
  }
  return path.join(directory, match);
}

async function defaultListFiles(directory) {
  const { readdir } = await import("node:fs/promises");
  return readdir(directory);
}

export function buildCopyPlan({
  repoRoot: rootDir,
  outputRoot,
  jarPath,
  cryptoDir,
  javaHome,
}) {
  return [
    {
      from: jarPath,
      to: path.join(outputRoot, "tcp-client", "app.jar"),
      type: "file",
    },
    {
      from: cryptoDir,
      to: path.join(outputRoot, "crypto-service"),
      type: "directory",
    },
    {
      from: path.join(
        rootDir,
        "TCP_Module",
        "src",
        "main",
        "resources",
        "application-client.yml",
      ),
      to: path.join(outputRoot, "config", "application-client.yml"),
      type: "file",
    },
    {
      from: javaHome,
      to: path.join(outputRoot, "jre"),
      type: "directory",
    },
  ];
}

export function listRequiredRuntimePaths({
  repoRoot: rootDir,
  jarPath,
  cryptoDir,
  javaHome,
  platform = process.platform,
}) {
  return [
    { path: jarPath, label: "TCP client jar" },
    ...getCryptoRequiredFiles(platform).map((item) => ({
      path: path.join(cryptoDir, item.name),
      label: item.label,
    })),
    {
      path: path.join(
        rootDir,
        "TCP_Module",
        "src",
        "main",
        "resources",
        "application-client.yml",
      ),
      label: "Client profile config",
    },
    { path: javaHome, label: "JAVA_HOME" },
  ];
}

async function assertExists(targetPath, label) {
  try {
    await access(targetPath);
  } catch (error) {
    throw new Error(`${label} not found: ${targetPath}`);
  }
}

async function copyPlanEntries(entries) {
  for (const entry of entries) {
    await mkdir(path.dirname(entry.to), { recursive: true });
    if (entry.type === "directory") {
      await cp(entry.from, entry.to, { recursive: true });
      continue;
    }
    await cp(entry.from, entry.to);
  }
}

export async function prepareRuntime({
  repoRoot: rootDir = repoRoot,
  outputRoot = path.join(desktopRoot, "build", "runtime"),
  javaHome = process.env.JAVA_HOME,
  platform = process.env.DESKTOP_TARGET_PLATFORM || process.platform,
  arch = process.env.DESKTOP_TARGET_ARCH || process.arch,
} = {}) {
  if (!javaHome) {
    throw new Error("JAVA_HOME is required to bundle the desktop JRE runtime.");
  }

  const targetPlatform = normalizeTargetPlatform(platform);
  const targetArch = normalizeTargetArch(arch);
  const jarPath = await findSingleFile(
    path.join(rootDir, "TCP_Module", "target"),
    [/\.jar$/i, /snapshot\.jar$/i],
  );
  const cryptoDir = path.join(
    rootDir,
    "Encryption_module",
    "dist",
    "crypto-service",
  );

  for (const item of listRequiredRuntimePaths({
    repoRoot: rootDir,
    jarPath,
    cryptoDir,
    javaHome,
    platform: targetPlatform,
  })) {
    await assertExists(item.path, item.label);
  }

  await rm(outputRoot, { recursive: true, force: true });

  const plan = buildCopyPlan({
    repoRoot: rootDir,
    outputRoot,
    jarPath,
    cryptoDir,
    javaHome,
  });

  await copyPlanEntries(plan);
  return plan;
}

function readCliOptions(argv) {
  const options = {};

  for (let index = 2; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg.startsWith("--platform=")) {
      options.platform = arg.slice("--platform=".length);
      continue;
    }
    if (arg === "--platform") {
      options.platform = argv[index + 1];
      index += 1;
      continue;
    }
    if (arg.startsWith("--arch=")) {
      options.arch = arg.slice("--arch=".length);
      continue;
    }
    if (arg === "--arch") {
      options.arch = argv[index + 1];
      index += 1;
    }
  }

  return options;
}

if (process.argv[1] === scriptPath) {
  prepareRuntime(readCliOptions(process.argv))
    .then((plan) => {
      console.log(`Prepared ${plan.length} runtime resources under ${path.join(desktopRoot, "build", "runtime")}`);
    })
    .catch((error) => {
      console.error(error.message);
      process.exitCode = 1;
    });
}
