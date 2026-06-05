const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const rootDir = path.resolve(__dirname, "..");

test("preload exposes private key picker IPC to the renderer", () => {
  const preloadSource = readFileSync(path.join(rootDir, "src", "preload.js"), "utf8");

  assert.match(preloadSource, /pickPrivateKeyFile:\s*\(\)\s*=>\s*ipcRenderer\.invoke\("pickPrivateKeyFile"\)/);
});

test("main process registers the private key picker IPC handler", () => {
  const mainSource = readFileSync(path.join(rootDir, "src", "main.js"), "utf8");

  assert.match(mainSource, /async function pickPrivateKeyFile\(\)/);
  assert.match(mainSource, /ipcMain\.handle\("pickPrivateKeyFile",\s*pickPrivateKeyFile\)/);
});

test("packaged renderer clears stale cache and appends app version", () => {
  const mainSource = readFileSync(path.join(rootDir, "src", "main.js"), "utf8");

  assert.match(mainSource, /clearRendererCache/);
  assert.match(mainSource, /session\.defaultSession\.clearCache\(\)/);
  assert.match(mainSource, /appendRendererVersion/);
  assert.match(mainSource, /app\.getVersion\(\)/);
});

test("preload exposes desktop debug IPC to the renderer", () => {
  const preloadSource = readFileSync(path.join(rootDir, "src", "preload.js"), "utf8");

  assert.match(preloadSource, /openDevTools:\s*\(\)\s*=>\s*ipcRenderer\.invoke\("debug:openDevTools"\)/);
  assert.match(preloadSource, /openLogsFolder:\s*\(\)\s*=>\s*ipcRenderer\.invoke\("debug:openLogsFolder"\)/);
  assert.match(preloadSource, /getDebugInfo:\s*\(\)\s*=>\s*ipcRenderer\.invoke\("debug:getInfo"\)/);
});

test("main process registers desktop debug IPC handlers", () => {
  const mainSource = readFileSync(path.join(rootDir, "src", "main.js"), "utf8");

  assert.match(mainSource, /ipcMain\.handle\("debug:openDevTools",\s*openDevTools\)/);
  assert.match(mainSource, /ipcMain\.handle\("debug:openLogsFolder",\s*openLogsFolder\)/);
  assert.match(mainSource, /ipcMain\.handle\("debug:getInfo",\s*getDebugInfo\)/);
});
