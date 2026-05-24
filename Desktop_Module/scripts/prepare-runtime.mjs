import { cp, mkdir, rm } from "node:fs/promises";
import { access } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptPath = fileURLToPath(import.meta.url);
const scriptDir = path.dirname(scriptPath);
const desktopRoot = path.resolve(scriptDir, "..");
const repoRoot = path.resolve(desktopRoot, "..");

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
}) {
  return [
    { path: jarPath, label: "TCP client jar" },
    {
      path: path.join(cryptoDir, "crypto-service.exe"),
      label: "Crypto service executable",
    },
    {
      path: path.join(cryptoDir, "libssl-3-x64.dll"),
      label: "Crypto service OpenSSL runtime",
    },
    {
      path: path.join(cryptoDir, "libcrypto-3-x64.dll"),
      label: "Crypto service libcrypto runtime",
    },
    {
      path: path.join(cryptoDir, "brotlicommon.dll"),
      label: "Crypto service Brotli common runtime",
    },
    {
      path: path.join(cryptoDir, "brotlidec.dll"),
      label: "Crypto service Brotli decoder runtime",
    },
    {
      path: path.join(cryptoDir, "brotlienc.dll"),
      label: "Crypto service Brotli encoder runtime",
    },
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
} = {}) {
  if (!javaHome) {
    throw new Error("JAVA_HOME is required to bundle the desktop JRE runtime.");
  }

  const jarPath = await findSingleFile(
    path.join(rootDir, "TCP_Module", "target"),
    [/\.jar$/i, /snapshot\.jar$/i],
  );
  const cryptoDir = path.join(
    rootDir,
    "Encryption_Module_OpenSSLversion",
    "Xcode_solution",
    "dist",
    "crypto-service-windows-x64",
  );

  for (const item of listRequiredRuntimePaths({
    repoRoot: rootDir,
    jarPath,
    cryptoDir,
    javaHome,
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

if (process.argv[1] === scriptPath) {
  prepareRuntime()
    .then((plan) => {
      console.log(`Prepared ${plan.length} runtime resources under ${path.join(desktopRoot, "build", "runtime")}`);
    })
    .catch((error) => {
      console.error(error.message);
      process.exitCode = 1;
    });
}
