const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("desktopApi", {
  pickSendFile: () => ipcRenderer.invoke("pickSendFile"),
  pickPrivateKeyFile: () => ipcRenderer.invoke("pickPrivateKeyFile"),
});
