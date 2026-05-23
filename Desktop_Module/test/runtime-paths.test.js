const assert = require("node:assert/strict");
const path = require("node:path");
const test = require("node:test");

const {
  DEFAULT_CLIENT_HTTP_PORT,
  DEFAULT_CRYPTO_SERVICE_PORT,
  buildRuntimePaths,
  buildRuntimeEnv,
  getRendererUrlForMode,
  getJavaLaunchCommand,
} = require("../src/runtime/paths");

test("builds packaged runtime paths under resources and LocalAppData", () => {
  const runtimePaths = buildRuntimePaths({
    appName: "File Security Transmission",
    isPackaged: true,
    resourcesPath: "C:\\Program Files\\FileSecurityTransmissionDesktop\\resources",
    localAppData: "C:\\Users\\Alice\\AppData\\Local",
    userProfile: "C:\\Users\\Alice",
  });

  assert.equal(
    runtimePaths.jarPath,
    path.win32.join(
      "C:\\Program Files\\FileSecurityTransmissionDesktop\\resources",
      "runtime",
      "tcp-client",
      "app.jar",
    ),
  );
  assert.equal(
    runtimePaths.cryptoExecutable,
    path.win32.join(
      "C:\\Program Files\\FileSecurityTransmissionDesktop\\resources",
      "runtime",
      "crypto-service",
      "crypto-service.exe",
    ),
  );
  assert.equal(
    runtimePaths.userDataDir,
    path.win32.join("C:\\Users\\Alice\\AppData\\Local", "FileSecurityTransmission"),
  );
  assert.equal(
    runtimePaths.downloadDir,
    path.win32.join("C:\\Users\\Alice", "Downloads", "FileSecurityTransmission"),
  );
});

test("prefers bundled java runtime when packaged", () => {
  const runtimePaths = buildRuntimePaths({
    appName: "File Security Transmission",
    isPackaged: true,
    resourcesPath: "C:\\app\\resources",
    localAppData: "C:\\Users\\Alice\\AppData\\Local",
    userProfile: "C:\\Users\\Alice",
  });

  assert.equal(
    getJavaLaunchCommand(runtimePaths),
    path.win32.join("C:\\app\\resources", "runtime", "jre", "bin", "java.exe"),
  );
});

test("uses system java during development", () => {
  const runtimePaths = buildRuntimePaths({
    appName: "File Security Transmission",
    isPackaged: false,
    projectRoot: "D:\\repo\\Desktop_Module",
    localAppData: "C:\\Users\\Alice\\AppData\\Local",
    userProfile: "C:\\Users\\Alice",
  });

  assert.equal(getJavaLaunchCommand(runtimePaths), "java");
});

test("builds runtime environment overrides for java and crypto services", () => {
  const runtimePaths = buildRuntimePaths({
    appName: "File Security Transmission",
    isPackaged: true,
    resourcesPath: "C:\\app\\resources",
    localAppData: "C:\\Users\\Alice\\AppData\\Local",
    userProfile: "C:\\Users\\Alice",
  });

  const env = buildRuntimeEnv(runtimePaths, { PATH: "C:\\Windows\\System32" });

  assert.equal(env.CRYPTO_SERVICE_PORT, String(DEFAULT_CRYPTO_SERVICE_PORT));
  assert.equal(env.CLIENT_HTTP_PORT, String(DEFAULT_CLIENT_HTTP_PORT));
  assert.equal(env.CLIENT_SERVER_HOST, "82.156.228.71");
  assert.equal(env.NODE_AUTO_CONNECT, "true");
  assert.equal(env.APP_UI_OPEN_BROWSER, "false");
  assert.match(env.PATH, /crypto-service/i);
});

test("uses localhost java UI URL in packaged mode", () => {
  assert.equal(
    getRendererUrlForMode({
      isPackaged: true,
      env: {},
      clientHttpPort: DEFAULT_CLIENT_HTTP_PORT,
    }),
    `http://127.0.0.1:${DEFAULT_CLIENT_HTTP_PORT}/`,
  );
});

