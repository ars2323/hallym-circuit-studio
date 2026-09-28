/* The window's only way out (derived from Hallym MIPS v2.5.0
   electron/src/main/preload.cjs).  CommonJS because a sandboxed preload
   cannot be an ES module.  Results come back as { ok, value } or
   { ok: false, error } and are unwrapped here, so the page sees ordinary
   promises.  A failure is a plain { name, message, code?, data? }: an
   Error crossing into the page loses everything but its message, and the
   page needs an engine error's code and data (docs/engine-api.md 2).
   The page never names a path: opening and saving go through the main
   process's dialogs, and the engine's methods it may call are a fixed list
   (src/main/protocol.ts WINDOW_METHODS). */
const { contextBridge, ipcRenderer, webUtils } = require('electron');

const unwrap = (r) => {
  if (r && r.ok === false) return Promise.reject({ ...r.error });
  return r && r.ok === true ? r.value : r;
};

contextBridge.exposeInMainWorld('app', {
  call: (method, params) => ipcRenderer.invoke('engine:call', method, params ?? {}).then(unwrap),
  engineStatus: () => ipcRenderer.invoke('engine:status'),
  retryEngine: () => ipcRenderer.invoke('engine:retry').then(unwrap),
  onEngineStatus: (listener) => ipcRenderer.on('engine:status', (_e, s) => listener(s)),
  onNotify: (listener) => ipcRenderer.on('engine:notify', (_e, method, params) => listener(method, params)),
  onEngineRecovered: (listener) => ipcRenderer.on('engine:recovered', (_e, report) => listener(report)),
  startupFile: () => ipcRenderer.invoke('file:startup'),
  openStartupFile: () => ipcRenderer.invoke('file:openStartup').then(unwrap),
  openFile: () => ipcRenderer.invoke('file:open').then(unwrap),
  openRecovery: (id, choice) => ipcRenderer.invoke('file:openRecovery', id, choice).then(unwrap),
  onLeave: (listener) => ipcRenderer.on('app:leave', () => listener()),
  leave: () => ipcRenderer.invoke('app:leave'),
  reportDirty: (dirty) => ipcRenderer.invoke('app:dirty', dirty),
  saveFile: (fileId, file) => ipcRenderer.invoke('file:save', fileId, file).then(unwrap),
  loadProgram: (fileId, options) => ipcRenderer.invoke('program:load', fileId, options ?? {}).then(unwrap),
  about: () => ipcRenderer.invoke('about:info'),
  license: (i) => ipcRenderer.invoke('about:license', i).then(unwrap),
  openCredits: () => ipcRenderer.invoke('about:openCredits').then(unwrap),
  setOverlay: (color) => ipcRenderer.invoke('win:overlay', color),
  // N-11: circuits from other files and libraries (the dialogs in the main process: src/main/circuit-files.ts)
  importChoose: (fileId) => ipcRenderer.invoke('circuits:importChoose', fileId).then(unwrap),
  importPlan: (fileId, circuits) => ipcRenderer.invoke('circuits:importPlan', fileId, circuits).then(unwrap),
  importApply: (fileId, circuits) => ipcRenderer.invoke('circuits:importApply', fileId, circuits).then(unwrap),
  loadLibrary: (fileId, kind, name) => ipcRenderer.invoke('library:load', fileId, kind, name).then(unwrap),
  useOpenFile: (fileId, otherFileId) => ipcRenderer.invoke('library:useOpenFile', fileId, otherFileId).then(unwrap),
  // every open file and its place, in any window (N-11: names told apart by their folders everywhere)
  openFilesAll: () => ipcRenderer.invoke('files:all'),
  onFilesChanged: (listener) => ipcRenderer.on('files:changed', (_e, list) => listener(list)),
  editOriginal: (fileId, circuitId) => ipcRenderer.invoke('file:editOriginal', fileId, circuitId).then(unwrap),
  // .circ files dropped from the desktop (I-181): their paths are found here, never handed to the page
  openDropped: (files) => {
    const paths = Array.from(files ?? []).map((f) => { try { return webUtils.getPathForFile(f); } catch { return ''; } }).filter((p) => p);
    return ipcRenderer.invoke('file:openDropped', paths).then(unwrap);
  },
  // N-11: a file tab in a window of its own (src/main/windows.ts)
  windowRole: () => ipcRenderer.invoke('win:role'),
  detach: (fileId, handover, how) => ipcRenderer.invoke('win:detach', fileId, handover, how).then(unwrap),
  attach: (handover) => ipcRenderer.invoke('win:attach', handover).then(unwrap),
  windowClosed: () => ipcRenderer.invoke('win:closed'),
  closeCancelled: () => ipcRenderer.send('win:closeCancelled'),
  onCloseRequest: (listener) => ipcRenderer.on('win:closeRequest', () => listener()),
  onAdopt: (listener) => ipcRenderer.on('win:adopt', (_e, handover) => listener(handover)),
});
