import test from "node:test";
import assert from "node:assert/strict";
import path from "node:path";

const moduleUrl = new URL("../scripts/prepare-runtime.mjs", import.meta.url);
const {
  findSingleFile,
  buildCopyPlan,
  listRequiredRuntimePaths,
  resolveCryptoRuntimeName,
} = await import(moduleUrl);

test("findSingleFile rejects when no matching file exists", async () => {
  await assert.rejects(
    findSingleFile("D:\\fake", [/\.jar$/i], async () => []),
    /No runtime artifact found/i,
  );
});

test("buildCopyPlan maps jar, crypto runtime, config, and jre directories", () => {
  const repoRoot = path.resolve("repo");
  const outputRoot = path.join(repoRoot, "Desktop_Module", "build", "runtime");
  const jarPath = path.join(repoRoot, "TCP_Module", "target", "client.jar");
  const cryptoDir = path.join(
    repoRoot,
    "Encryption_module",
    "dist",
    "crypto-service",
  );
  const javaHome = path.join(repoRoot, "jdk-21");

  const plan = buildCopyPlan({
    repoRoot,
    outputRoot,
    jarPath,
    cryptoDir,
    javaHome,
  });

  assert.deepEqual(plan, [
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
        repoRoot,
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
  ]);
});

test("listRequiredRuntimePaths includes Windows python crypto binary", () => {
  const repoRoot = path.resolve("repo");
  const jarPath = path.join(repoRoot, "TCP_Module", "target", "client.jar");
  const cryptoDir = path.join(
    repoRoot,
    "Encryption_module",
    "dist",
    "crypto-service",
  );
  const javaHome = path.join(repoRoot, "jdk-21");

  const checks = listRequiredRuntimePaths({
    repoRoot,
    jarPath,
    cryptoDir,
    javaHome,
    platform: "win32",
  });

  assert.deepEqual(checks, [
    {
      path: jarPath,
      label: "TCP client jar",
    },
    {
      path: path.join(cryptoDir, "crypto-service.exe"),
      label: "Crypto service executable",
    },
    {
      path: path.join(
        repoRoot,
        "TCP_Module",
        "src",
        "main",
        "resources",
        "application-client.yml",
      ),
      label: "Client profile config",
    },
    {
      path: javaHome,
      label: "JAVA_HOME",
    },
  ]);
});

test("listRequiredRuntimePaths includes macOS crypto binary and dylibs", () => {
  const repoRoot = path.resolve("repo");
  const jarPath = path.join(repoRoot, "TCP_Module", "target", "client.jar");
  const cryptoDir = path.join(
    repoRoot,
    "Encryption_module",
    "dist",
    "crypto-service",
  );
  const javaHome = path.join(repoRoot, "jdk-21");

  const checks = listRequiredRuntimePaths({
    repoRoot,
    jarPath,
    cryptoDir,
    javaHome,
    platform: "darwin",
  });

  assert.deepEqual(checks.slice(0, 4), [
    {
      path: jarPath,
      label: "TCP client jar",
    },
    {
      path: path.join(cryptoDir, "crypto-service"),
      label: "Crypto service executable",
    },
    {
      path: path.join(cryptoDir, "libssl.3.dylib"),
      label: "Crypto service OpenSSL runtime",
    },
    {
      path: path.join(cryptoDir, "libcrypto.3.dylib"),
      label: "Crypto service libcrypto runtime",
    },
  ]);
});

test("resolves platform-specific crypto runtime folder names", () => {
  assert.equal(
    resolveCryptoRuntimeName({ platform: "win32", arch: "x64" }),
    "crypto-service",
  );
  assert.equal(
    resolveCryptoRuntimeName({ platform: "darwin", arch: "arm64" }),
    "crypto-service",
  );
  assert.equal(
    resolveCryptoRuntimeName({ platform: "macos", arch: "x86_64" }),
    "crypto-service",
  );
});
