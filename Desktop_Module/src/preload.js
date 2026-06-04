const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("desktopApi", {
  pickSendFile: () => ipcRenderer.invoke("pickSendFile"),
  pickPrivateKeyFile: () => ipcRenderer.invoke("pickPrivateKeyFile"),
  openDevTools: () => ipcRenderer.invoke("debug:openDevTools"),
  openLogsFolder: () => ipcRenderer.invoke("debug:openLogsFolder"),
  openSystemStatus: () => ipcRenderer.invoke("debug:openSystemStatus"),
  getDebugInfo: () => ipcRenderer.invoke("debug:getInfo"),
});
