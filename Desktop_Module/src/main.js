const { app, BrowserWindow, dialog, ipcMain, shell } = require("electron");
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

function createMainWindow() {
  mainWindow = new BrowserWindow(createWindowOptions());
  const rendererUrl = app.isPackaged
    ? getPackagedRendererUrl()
    : getRendererUrl(process.env);

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
    },
  );

  const javaProcess = spawnManagedProcess(
    getJavaLaunchCommand(runtimePaths),
    buildJavaArguments(runtimePaths.jarPath),
    buildJavaProcessOptions(runtimePaths, env),
  );

  await waitForServiceReady({
    url: runtimePaths.healthUrl,
  });

  runtimeState = {
    cryptoProcess,
    javaProcess,
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
  return startDesktopRuntime()
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
