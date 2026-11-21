const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('mride', {
  version: () => ipcRenderer.invoke('app:version'),
  whatsnew: () => ipcRenderer.invoke('app:whatsnew'),
  checkUpdate: () => ipcRenderer.invoke('upd:check'),
  installUpdate: () => ipcRenderer.invoke('upd:install'),
  onProgress: cb => ipcRenderer.on('upd:progress', (_e, p) => cb(p)),
});
