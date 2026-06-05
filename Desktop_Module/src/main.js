const { app, BrowserWindow, dialog, ipcMain, shell, session } = require("electron");
const path = require("node:path");
const {
  buildRuntimeEnv,
  getJavaLaunchCommand,
} = require("./runtime/paths");
const {
  buildJavaArguments,
  buildJavaProcessOptions,
  ensureRuntimeDirectories,
  spawnManagedProcess,
  stopManagedProcess,
  waitForServiceReady,
} = require("./runtime/launcher");

const {
  createWindowOptions,
  getRendererUrl,
  getPackagedRendererUrl,
  resolveDesktopRuntimePaths,
} = require("./desktop-config");

let mainWindow = null;
let runtimeState = null;
let isQuitting = false;

function normalizeDialogResult(result) {
  if (result.canceled || !result.filePaths || result.filePaths.length === 0) {
    return { canceled: true, filePath: "", fileName: "" };
  }

  const filePath = result.filePaths[0];
  return {
    canceled: false,
    filePath,
    fileName: filePath.split(/[\\/]/).pop() || "",
  };
}

async function pickSendFile() {
  const window = mainWindow || BrowserWindow.getFocusedWindow();
  const result = await dialog.showOpenDialog(window, {
    properties: ["openFile"],
  });
  return normalizeDialogResult(result);
}

async function pickPrivateKeyFile() {
  const window = mainWindow || BrowserWindow.getFocusedWindow();
  const result = await dialog.showOpenDialog(window, {
    properties: ["openFile"],
    filters: [
      { name: "Key Files", extensions: ["pem", "key", "txt"] },
      { name: "All Files", extensions: ["*"] },
    ],
  });
  return normalizeDialogResult(result);
}

function showLoadError(url, error) {
  dialog.showErrorBox(
    "界面加载失败",
    [
      "Electron 已启动，但没有加载到 Vue 页面。",
      "",
      `目标地址: ${url}`,
      "",
      "开发阶段请先在 UI_Module 运行 npm run dev。",
      `错误信息: ${error.message}`,
    ].join("\n"),
  );
}

function appendRendererVersion(url) {
  const separator = url.includes("?") ? "&" : "?";
  return `${url}${separator}v=${encodeURIComponent(app.getVersion())}`;
}

async function clearRendererCache() {
  if (!app.isPackaged) {
    return;
  }

  try {
    await session.defaultSession.clearCache();
    await session.defaultSession.clearStorageData({
      storages: ["cachestorage", "serviceworkers"],
    });
  } catch (error) {
    console.warn("Failed to clear renderer cache", error);
  }
}

function currentRuntimePaths() {
  return runtimeState?.runtimePaths || resolveDesktopRuntimePaths({
    isPackaged: app.isPackaged,
    resourcesPath: app.isPackaged ? process.resourcesPath : undefined,
  });
}

async function openDevTools() {
  const window = mainWindow || BrowserWindow.getFocusedWindow();
  if (!window) {
    throw new Error("No active window is available");
  }
  window.webContents.openDevTools({ mode: "detach" });
  return { success: true };
}

async function openLogsFolder() {
  const runtimePaths = currentRuntimePaths();
  await ensureRuntimeDirectories(runtimePaths);
  const error = await shell.openPath(runtimePaths.logDir);
  if (error) {
    throw new Error(error);
  }
  return { success: true, path: runtimePaths.logDir };
}

async function openSystemStatus() {
  const runtimePaths = currentRuntimePaths();
  await shell.openExternal(runtimePaths.healthUrl);
  return { success: true, url: runtimePaths.healthUrl };
}

async function getDebugInfo() {
  const runtimePaths = currentRuntimePaths();
  return {
    version: app.getVersion(),
    isPackaged: app.isPackaged,
    platform: process.platform,
    arch: process.arch,
    healthUrl: runtimePaths.healthUrl,
    logDir: runtimePaths.logDir,
    userDataDir: runtimePaths.userDataDir,
    downloadDir: runtimePaths.downloadDir,
    runtimeRoot: runtimePaths.runtimeRoot,
    javaPid: runtimeState?.javaProcess?.pid || null,
    cryptoPid: runtimeState?.cryptoProcess?.pid || null,
  };
}

function createMainWindow() {
  mainWindow = new BrowserWindow(createWindowOptions());
  const baseRendererUrl = app.isPackaged
    ? getPackagedRendererUrl()
    : getRendererUrl(process.env);
  const rendererUrl = app.isPackaged ? appendRendererVersion(baseRendererUrl) : baseRendererUrl;

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url);
    return { action: "deny" };
  });

  mainWindow.loadURL(rendererUrl).catch((error) => {
    showLoadError(rendererUrl, error);
  });

  mainWindow.on("closed", () => {
    mainWindow = null;
  });

  return mainWindow;
}

async function startDesktopRuntime() {
  if (!app.isPackaged) {
    return null;
  }

  const runtimePaths = resolveDesktopRuntimePaths({
    isPackaged: true,
    resourcesPath: process.resourcesPath,
  });
  const env = buildRuntimeEnv(runtimePaths, process.env);

  await ensureRuntimeDirectories(runtimePaths);

  const cryptoProcess = spawnManagedProcess(
    runtimePaths.cryptoExecutable,
    [
      "--host",
      env.CRYPTO_SERVICE_HOST,
      "--port",
      env.CRYPTO_SERVICE_PORT,
      "--key-dir",
      env.CRYPTO_SERVICE_KEY_DIR,
    ],
    {
      cwd: runtimePaths.cryptoDir,
      env,
      logFilePath: path.join(runtimePaths.logDir, "crypto-service.log"),
      logLabel: "crypto-service",
    },
  );

  const javaProcess = spawnManagedProcess(
    getJavaLaunchCommand(runtimePaths),
    buildJavaArguments(runtimePaths.jarPath),
    {
      ...buildJavaProcessOptions(runtimePaths, env),
      logFilePath: path.join(runtimePaths.logDir, "java-client.log"),
      logLabel: "java-client",
    },
  );

  await waitForServiceReady({
    url: runtimePaths.healthUrl,
  });

  runtimeState = {
    cryptoProcess,
    javaProcess,
    runtimePaths,
  };

  return runtimeState;
}

async function shutdownDesktopRuntime() {
  if (!runtimeState) {
    return;
  }

  const currentRuntime = runtimeState;
  runtimeState = null;
  await Promise.all([
    stopManagedProcess(currentRuntime.javaProcess),
    stopManagedProcess(currentRuntime.cryptoProcess),
  ]);
}

app.whenReady().then(() => {
  ipcMain.handle("pickSendFile", pickSendFile);
  ipcMain.handle("pickPrivateKeyFile", pickPrivateKeyFile);
  ipcMain.handle("debug:openDevTools", openDevTools);
  ipcMain.handle("debug:openLogsFolder", openLogsFolder);
  ipcMain.handle("debug:openSystemStatus", openSystemStatus);
  ipcMain.handle("debug:getInfo", getDebugInfo);
  return clearRendererCache()
    .then(() => startDesktopRuntime())
    .then(() => {
      createMainWindow();

      app.on("activate", () => {
        if (BrowserWindow.getAllWindows().length === 0) {
          createMainWindow();
        }
      });
    })
    .catch(async (error) => {
      dialog.showErrorBox(
        "Desktop Runtime Startup Failed",
        `Unable to start bundled services.\n\n${error.message}`,
      );
      await shutdownDesktopRuntime();
      app.quit();
    });
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") {
    app.quit();
  }
});

app.on("before-quit", (event) => {
  if (isQuitting) {
    return;
  }

  if (!runtimeState) {
    return;
  }

  event.preventDefault();
  isQuitting = true;
  shutdownDesktopRuntime().finally(() => {
    app.quit();
  });
});

module.exports = {
  createMainWindow,
  shutdownDesktopRuntime,
  startDesktopRuntime,
};
