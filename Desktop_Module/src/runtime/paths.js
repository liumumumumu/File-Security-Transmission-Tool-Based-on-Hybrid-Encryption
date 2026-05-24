const path = require("node:path");

const DEFAULT_CLIENT_HTTP_PORT = 20201;
const DEFAULT_CRYPTO_SERVICE_PORT = 20202;
const DEFAULT_SERVER_HOST = "82.156.228.71";
const DEFAULT_SERVER_PORT = 9000;
const USER_DATA_DIRNAME = "FileSecurityTransmission";

function selectPathModule(platform = process.platform) {
  return platform === "win32" ? path.win32 : path.posix;
}

function normalizeProjectRoot(projectRoot) {
  return projectRoot || path.resolve(__dirname, "..", "..");
}

function buildRuntimePaths(options = {}) {
  const platform = options.platform || process.platform;
  const pathModule = selectPathModule(platform);
  const appName = options.appName || "File Security Transmission";
  const isPackaged = Boolean(options.isPackaged);
  const projectRoot = normalizeProjectRoot(options.projectRoot);
  const resourcesPath = isPackaged
    ? options.resourcesPath || process.resourcesPath
    : projectRoot;
  const runtimeRoot = isPackaged
    ? pathModule.join(resourcesPath, "runtime")
    : pathModule.join(projectRoot, "build", "runtime");
  const localAppData =
    options.localAppData ||
    process.env.LOCALAPPDATA ||
    pathModule.join(options.userProfile || process.env.USERPROFILE || "", "AppData", "Local");
  const userProfile =
    options.userProfile ||
    process.env.USERPROFILE ||
    process.env.HOME ||
    localAppData;
  const userDataDir = pathModule.join(localAppData, USER_DATA_DIRNAME);
  const keyDir = pathModule.join(userDataDir, "crypto_keys");
  const logDir = pathModule.join(userDataDir, "logs");
  const downloadDir = pathModule.join(
    userProfile,
    "Downloads",
    USER_DATA_DIRNAME,
  );
  const tcpClientDir = pathModule.join(runtimeRoot, "tcp-client");
  const cryptoDir = pathModule.join(runtimeRoot, "crypto-service");
  const configDir = pathModule.join(runtimeRoot, "config");
  const jreDir = pathModule.join(runtimeRoot, "jre");
  const javaExecutable = isPackaged
    ? pathModule.join(jreDir, "bin", platform === "win32" ? "java.exe" : "java")
    : "java";

  return {
    appName,
    isPackaged,
    platform,
    projectRoot,
    resourcesPath,
    runtimeRoot,
    tcpClientDir,
    cryptoDir,
    configDir,
    jreDir,
    jarPath: pathModule.join(tcpClientDir, "app.jar"),
    cryptoExecutable: pathModule.join(
      cryptoDir,
      platform === "win32" ? "crypto-service.exe" : "crypto-service",
    ),
    javaExecutable,
    userDataDir,
    keyDir,
    logDir,
    downloadDir,
    healthUrl: `http://127.0.0.1:${DEFAULT_CLIENT_HTTP_PORT}/api/system/status`,
  };
}

function buildRuntimeEnv(runtimePaths, baseEnv = process.env) {
  const env = { ...baseEnv };
  const pathKey = Object.keys(env).find((key) => key.toUpperCase() === "PATH") || "PATH";
  const currentPath = env[pathKey] || "";

  env.CRYPTO_SERVICE_ADDRESS = "127.0.0.1";
  env.CRYPTO_SERVICE_HOST = "127.0.0.1";
  env.CRYPTO_SERVICE_PORT = String(DEFAULT_CRYPTO_SERVICE_PORT);
  env.CRYPTO_SERVICE_KEY_DIR = runtimePaths.keyDir;
  env.CLIENT_HTTP_ADDRESS = "127.0.0.1";
  env.CLIENT_HTTP_PORT = String(DEFAULT_CLIENT_HTTP_PORT);
  env.TRANSFER_RECEIVE_DIR = runtimePaths.downloadDir;
  env.TRANSFER_HISTORY_PATH = path.join(runtimePaths.userDataDir, "transfer-history.json");
  env.LOCAL_SQLITE_PATH = path.join(runtimePaths.userDataDir, "local-data.db");
  env.DEVICE_ID_PATH = path.join(runtimePaths.userDataDir, "device-id");
  env.STARTUP_STATE_PATH = path.join(runtimePaths.userDataDir, "startup-state.json");
  env.NODE_AUTO_CONNECT = "true";
  env.CLIENT_SERVER_HOST = DEFAULT_SERVER_HOST;
  env.CLIENT_SERVER_PORT = String(DEFAULT_SERVER_PORT);
  env.APP_UI_OPEN_BROWSER = "false";
  env[pathKey] = currentPath
    ? `${runtimePaths.cryptoDir}${path.delimiter}${currentPath}`
    : runtimePaths.cryptoDir;

  return env;
}

function getRendererUrlForMode({
  isPackaged,
  env = process.env,
  clientHttpPort = DEFAULT_CLIENT_HTTP_PORT,
}) {
  if (isPackaged) {
    return `http://127.0.0.1:${clientHttpPort}/`;
  }

  const configuredUrl = env.DESKTOP_RENDERER_URL;
  if (!configuredUrl || configuredUrl.trim().length === 0) {
    return "http://127.0.0.1:5173/";
  }
  return configuredUrl.trim();
}

function getJavaLaunchCommand(runtimePaths) {
  return runtimePaths.javaExecutable;
}

module.exports = {
  DEFAULT_CLIENT_HTTP_PORT,
  DEFAULT_CRYPTO_SERVICE_PORT,
  DEFAULT_SERVER_HOST,
  DEFAULT_SERVER_PORT,
  buildRuntimeEnv,
  buildRuntimePaths,
  getJavaLaunchCommand,
  getRendererUrlForMode,
};

