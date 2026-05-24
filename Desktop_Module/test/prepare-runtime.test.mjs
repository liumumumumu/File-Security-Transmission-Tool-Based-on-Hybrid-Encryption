import test from "node:test";
import assert from "node:assert/strict";
import path from "node:path";

const moduleUrl = new URL("../scripts/prepare-runtime.mjs", import.meta.url);
const {
  findSingleFile,
  buildCopyPlan,
  listRequiredRuntimePaths,
} = await import(moduleUrl);

test("findSingleFile rejects when no matching file exists", async () => {
  await assert.rejects(
    findSingleFile("D:\\fake", [/\.jar$/i], async () => []),
    /No runtime artifact found/i,
  );
});

test("buildCopyPlan maps jar, crypto runtime, config, and jre directories", () => {
  const repoRoot = "D:\\repo";
  const outputRoot = "D:\\repo\\Desktop_Module\\build\\runtime";

  const plan = buildCopyPlan({
    repoRoot,
    outputRoot,
    jarPath: "D:\\repo\\TCP_Module\\target\\client.jar",
    cryptoDir:
      "D:\\repo\\Encryption_Module_OpenSSLversion\\Xcode_solution\\dist\\crypto-service-windows-x64",
    javaHome: "C:\\Java\\jdk-21",
  });

  assert.deepEqual(plan, [
    {
      from: "D:\\repo\\TCP_Module\\target\\client.jar",
      to: path.win32.join(outputRoot, "tcp-client", "app.jar"),
      type: "file",
    },
    {
      from:
        "D:\\repo\\Encryption_Module_OpenSSLversion\\Xcode_solution\\dist\\crypto-service-windows-x64",
      to: path.win32.join(outputRoot, "crypto-service"),
      type: "directory",
    },
    {
      from: path.win32.join(
        repoRoot,
        "TCP_Module",
        "src",
        "main",
        "resources",
        "application-client.yml",
      ),
      to: path.win32.join(outputRoot, "config", "application-client.yml"),
      type: "file",
    },
    {
      from: "C:\\Java\\jdk-21",
      to: path.win32.join(outputRoot, "jre"),
      type: "directory",
    },
  ]);
});

test("listRequiredRuntimePaths includes crypto binary and OpenSSL dlls", () => {
  const checks = listRequiredRuntimePaths({
    repoRoot: "D:\\repo",
    jarPath: "D:\\repo\\TCP_Module\\target\\client.jar",
    cryptoDir: "D:\\repo\\Encryption_Module_OpenSSLversion\\Xcode_solution\\dist\\crypto-service-windows-x64",
    javaHome: "C:\\Java\\jdk-21",
  });

  assert.deepEqual(checks, [
    {
      path: "D:\\repo\\TCP_Module\\target\\client.jar",
      label: "TCP client jar",
    },
    {
      path: "D:\\repo\\Encryption_Module_OpenSSLversion\\Xcode_solution\\dist\\crypto-service-windows-x64\\crypto-service.exe",
      label: "Crypto service executable",
    },
    {
      path: "D:\\repo\\Encryption_Module_OpenSSLversion\\Xcode_solution\\dist\\crypto-service-windows-x64\\libssl-3-x64.dll",
      label: "Crypto service OpenSSL runtime",
    },
    {
      path: "D:\\repo\\Encryption_Module_OpenSSLversion\\Xcode_solution\\dist\\crypto-service-windows-x64\\libcrypto-3-x64.dll",
      label: "Crypto service libcrypto runtime",
    },
    {
      path: "D:\\repo\\Encryption_Module_OpenSSLversion\\Xcode_solution\\dist\\crypto-service-windows-x64\\brotlicommon.dll",
      label: "Crypto service Brotli common runtime",
    },
    {
      path: "D:\\repo\\Encryption_Module_OpenSSLversion\\Xcode_solution\\dist\\crypto-service-windows-x64\\brotlidec.dll",
      label: "Crypto service Brotli decoder runtime",
    },
    {
      path: "D:\\repo\\Encryption_Module_OpenSSLversion\\Xcode_solution\\dist\\crypto-service-windows-x64\\brotlienc.dll",
      label: "Crypto service Brotli encoder runtime",
    },
    {
      path: "D:\\repo\\TCP_Module\\src\\main\\resources\\application-client.yml",
      label: "Client profile config",
    },
    {
      path: "C:\\Java\\jdk-21",
      label: "JAVA_HOME",
    },
  ]);
});
