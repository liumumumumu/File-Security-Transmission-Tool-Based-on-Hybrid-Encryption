const path = require("node:path");
const {
  DEFAULT_CLIENT_HTTP_PORT,
  buildRuntimePaths,
  getRendererUrlForMode,
} = require("./runtime/paths");

function getWindowIconPath() {
  return path.join(__dirname, "assets", "app-icon.png");
}

function getRendererUrl(env = process.env) {
  return getRendererUrlForMode({
    isPackaged: false,
    env,
    clientHttpPort: DEFAULT_CLIENT_HTTP_PORT,
  });
}

function getPackagedRendererUrl() {
  return getRendererUrlForMode({
    isPackaged: true,
    env: process.env,
    clientHttpPort: DEFAULT_CLIENT_HTTP_PORT,
  });
}

function resolveDesktopRuntimePaths(options = {}) {
  return buildRuntimePaths({
    appName: "File Security Transmission",
    ...options,
  });
}

function createWindowOptions() {
  return {
    width: 1280,
    height: 820,
    minWidth: 960,
    minHeight: 640,
    title: "File Security Transmission",
    backgroundColor: "#f4f8fb",
    icon: getWindowIconPath(),
    autoHideMenuBar: true,
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      preload: path.join(__dirname, "preload.js"),
    },
  };
}

module.exports = {
  createWindowOptions,
  getRendererUrl,
  getPackagedRendererUrl,
  getWindowIconPath,
  resolveDesktopRuntimePaths,
};
