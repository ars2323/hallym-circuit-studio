/* Electron's main process: the host (derived from Hallym MIPS v2.3.0
   electron/src/main/main.ts -- the window, the run's folder, the caption
   buttons' patch, About; the simulator is replaced by the engine).

   It owns the engine (engine.ts: a Java child process, JSON-RPC over
   stdio, docs/engine-api.md) and everything that touches the disk: the
   file dialogs, the paths given to the engine.  The window sees only
   window.app (preload.cjs) and never talks to the engine itself: its calls
   come through here, only the methods in WINDOW_METHODS, and the engine's
   notifications go back to it as they arrive.

   If the engine dies, it is started again and every open file comes back
   with its unsaved edits (recovery.ts: the journal of the window's edit
   intents, kept in memory only); the window's calls wait meanwhile, and
   then it is told what came back ('engine:recovered': a dialog and a band).
   Quitting ends the engine (engine.shutdown, then killed after 3 s); if this
   process itself is killed, the engine ends as its stdin closes.

   Nothing is kept from one run to the next -- lab PCs are shared, and every
   student starts from the same screen: the window's size, the panels, the
   files opened.  Chromium's profile is this run's folder in the temp folder
   (run-folder.ts), removed after quit; the engine runs in it too. */

import { app, BrowserWindow, dialog, ipcMain, Menu, screen, shell } from 'electron';
import { spawn } from 'node:child_process';
import { mkdirSync, readFileSync } from 'node:fs';
import path from 'node:path';

import { EngineClient, EngineError, type EngineProcess } from './engine.ts';
import { locateEngine } from './engine-locate.ts';
import { Supervisor, WINDOW } from './recovery.ts';
import { LICENSES, paths, version } from './paths.ts';
import { IMAGE_FILTER, programDialogPath } from './program-path.ts';
import { WINDOW_METHODS, type EngineStatus, type LoadResult, type OpenResult, type SaveResult } from './protocol.ts';
import { circArgument, removeAfterExitScript, removeEarlierRuns, runDirName, runsDirFor } from './run-folder.ts';

const APP_NAME = 'Hallym Circuit Studio';
app.setName(APP_NAME);
// The top bar's height in the window (src/renderer/shared/shared.css --titlebar).
const TITLE_BAR_HEIGHT = 40;
const NAVY = '#00205b';
if (process.platform === 'win32') app.setAppUserModelId('kr.ac.hallym.circuit-studio');

// ---- this run's folder, and nothing else on disk ------------------------------

const runsDir = runsDirFor(process.env);
const runDir = path.join(runsDir, runDirName(process.pid, Date.now()));
removeEarlierRuns(runsDir, process.pid);
mkdirSync(path.join(runDir, 'tmp'), { recursive: true });
// Linux: the libraries under Chromium keep caches in the user's own folders
// (fontconfig's font cache, the GPU driver's shaders, NSS's certificate
// store): this run's folder instead, set before any of them starts.
if (process.platform === 'linux') {
  process.env.XDG_CACHE_HOME = path.join(runDir, 'cache');
  process.env.XDG_DATA_HOME = path.join(runDir, 'data');
  process.env.XDG_CONFIG_HOME = path.join(runDir, 'config');
}
app.setPath('userData', runDir);
app.setPath('sessionData', runDir);
app.setPath('crashDumps', path.join(runDir, 'Crashpad'));
app.on('quit', () => {
  try {
    spawn(process.execPath, ['-e', removeAfterExitScript(process.pid, runDir)], {
      detached: true, stdio: 'ignore', windowsHide: true, env: { ...process.env, ELECTRON_RUN_AS_NODE: '1' },
    }).unref();
  } catch { /* the next start removes it */ }
});

// ---- the engine ---------------------------------------------------------------------

const located = locateEngine({ env: process.env, runDir, resources: app.isPackaged ? process.resourcesPath : null, repoRoot: paths.repoRoot });
let supervisor: Supervisor | null = null;
const engine = new EngineClient({
  client: { client: 'hallym-circuit-studio', version },
  // A restarted engine's ids start above every id the window has seen (D-142).
  helloParams: () => supervisor?.helloParams() ?? {},
  launch: (): EngineProcess => {
    if (!located.ok) throw new Error(`${located.reason}\n${located.looked.map((l) => `  ${l}`).join('\n')}`);
    const env = { ...process.env };
    delete env.ELECTRON_RUN_AS_NODE;
    return spawn(located.engine.command, located.engine.args, { cwd: located.engine.cwd, env, stdio: 'pipe', windowsHide: true });
  },
});
supervisor = new Supervisor(engine);
const recovery = supervisor;
if (process.env.HCS_ENGINE_LOG === '1') engine.on('log', (line) => console.log(`[engine] ${line}`));
// For the e2e tests (app.evaluate): the engine, to end it as a crash would; the journal.
(globalThis as { __hcs?: unknown }).__hcs = { engine, recovery };

// The files the engine has open, by id: the same file opened again goes to
// its tab.  They outlive a restart of the engine (recovery.ts opens them
// again under the same ids), except those that could not be opened again.
const openFiles = new Map<string, string | null>();
recovery.on('recovered', (r) => { for (const c of r.closed) openFiles.delete(c.fileId); });

// A call of the window's: after any recovery under way, tagged so that the journal records it.
async function windowCall<T>(method: string, params: unknown): Promise<T> {
  await recovery.settled();
  return engine.call<T>(method, params, { tag: WINDOW });
}

// ---- answers --------------------------------------------------------------------------

type Result<T> = { ok: true; value: T } | { ok: false; error: { name: string; message: string; code?: number; data?: unknown } };
async function answer<T>(f: () => T | Promise<T>): Promise<Result<T>> {
  try {
    return { ok: true, value: await f() };
  } catch (e) {
    const err = e instanceof Error ? e : new Error(String(e));
    const extra = err instanceof EngineError ? { code: err.code, data: err.data } : {};
    return { ok: false, error: { name: err.name, message: err.message, ...extra } };
  }
}

export interface Opened extends OpenResult {
  path: string;
  already: boolean;     // it was open: the window goes to its tab
}

async function openPath(p: string): Promise<Opened> {
  for (const [fileId, open] of openFiles) {
    if (open !== null && path.resolve(open) === path.resolve(p)) {
      return { fileId, path: p, name: path.basename(p), circuits: [], main: '', libraries: [], already: true };
    }
  }
  const r = await windowCall<OpenResult>('file.open', { path: path.resolve(p) });
  openFiles.set(r.fileId, p);
  // The tab shows the file's own name (with .circ), not Logisim's project name.
  return { ...r, name: path.basename(p), path: p, already: r.alreadyOpen === true };
}

async function main(): Promise<void> {
  Menu.setApplicationMenu(null); // no default zoom/reload accelerators; the window has its own keys
  await app.whenReady();
  void engine.start().catch(() => { /* status says so; the window shows the dialog */ });

  // Every start opens the window over the whole work area, maximised --
  // nothing of its size or place is kept.  (The bounds first: a display
  // with no window manager to maximise it still gets the work area.)
  const area = screen.getPrimaryDisplay().workArea;
  const win = new BrowserWindow({
    x: area.x, y: area.y, width: area.width, height: area.height,
    minWidth: 760,
    minHeight: 480,
    show: false,
    title: APP_NAME,
    backgroundColor: '#f5f7fa',
    icon: paths.icon ?? undefined,
    // No system title bar: the window's own top bar carries the logo, the
    // file and the toolbar.  The caption buttons stay the system's own
    // (titleBarOverlay), so Windows 11's snap layouts keep working, as do
    // double-click to maximise and dragging on the bar's drag region.
    titleBarStyle: 'hidden',
    titleBarOverlay: { color: '#ffffff', symbolColor: NAVY, height: TITLE_BAR_HEIGHT },
    webPreferences: {
      preload: paths.preload,
      contextIsolation: true,
      sandbox: true,
      nodeIntegration: false,
    },
  });

  const send = (channel: string, ...args: unknown[]) => { if (!win.isDestroyed()) win.webContents.send(channel, ...args); };
  // The engine's status as the window should see it (restarting until its files are back).
  recovery.on('status', (s: EngineStatus) => send('engine:status', s));
  // While a recovery replays, the engine's changes are of parts the window never had: held back.
  engine.on('notification', (method, params) => { if (!recovery.quiet()) send('engine:notify', method, params); });
  recovery.on('recovered', (r) => send('engine:recovered', r));

  const allowed = new Set<string>(WINDOW_METHODS);
  ipcMain.handle('engine:call', (_e, method: string, params: unknown) => answer(async () => {
    if (!allowed.has(method)) throw new Error(`not a method the window may call: ${method}`);
    const result = await windowCall(method, params);
    if (method === 'file.new') openFiles.set((result as { fileId: string }).fileId, null);
    if (method === 'file.close') openFiles.delete((params as { fileId: string }).fileId);
    return result;
  }));
  ipcMain.handle('engine:status', () => recovery.view(engine.status()));
  ipcMain.handle('engine:retry', () => answer(async () => {
    if (engine.status().state === 'failed') await engine.start().catch(() => {});
    await recovery.settled();
    return recovery.view(engine.status());
  }));

  // The file named on the command line: opened instead of the first screen.
  const startup = circArgument(process.argv, process.cwd());
  let startupTaken = false;
  ipcMain.handle('file:startup', () => (startup ? { name: path.basename(startup) } : null));
  ipcMain.handle('file:openStartup', () => answer(async () => {
    if (!startup || startupTaken) return null;
    startupTaken = true;
    return openPath(startup);
  }));
  ipcMain.handle('file:open', () => answer(async () => {
    const r = await dialog.showOpenDialog(win, { filters: [{ name: 'Logisim circuit', extensions: ['circ'] }, { name: 'All files', extensions: ['*'] }] });
    if (r.canceled || r.filePaths.length === 0) return null;
    return openPath(r.filePaths[0]);
  }));
  ipcMain.handle('file:save', (_e, fileId: string, file: { name: string; saveAs?: boolean }) => answer(async () => {
    if (!openFiles.has(fileId)) throw new Error(`no open file ${fileId}`);
    let target = openFiles.get(fileId) ?? null;
    if (target === null || file.saveAs) {
      const r = await dialog.showSaveDialog(win, { defaultPath: file.name, filters: [{ name: 'Logisim circuit', extensions: ['circ'] }] });
      if (r.canceled || !r.filePath) return null;
      target = r.filePath;
    }
    const saved = await windowCall<SaveResult>('file.save', { fileId, path: target });
    openFiles.set(fileId, saved.path || target);
    return { path: saved.path || target, name: path.basename(saved.path || target), bytes: saved.bytes, needsMipsJar: saved.needsMipsJar === true };
  }));

  // Load Program (N-16, D-147): the dialog (.hmx only) here, then the engine's
  // mips.load.  `again` loads the file picked last for this circuit (the
  // answer to "which memory?": `picks`); `forSource` opens next to an old .s.
  const programs = new Map<string, string>();
  ipcMain.handle('program:load', (_e, fileId: string, o: { target?: string; picks?: Record<string, string>; again?: boolean; forSource?: string } = {}) => answer(async () => {
    if (!openFiles.has(fileId)) throw new Error(`no open file ${fileId}`);
    let file = o.again ? programs.get(fileId) : undefined;
    if (!file) {
      const r = await dialog.showOpenDialog(win, {
        title: 'Load Program', defaultPath: programDialogPath(openFiles.get(fileId) ?? null, o.forSource ?? null),
        filters: [IMAGE_FILTER], properties: ['openFile'],
      });
      if (r.canceled || r.filePaths.length === 0) return null;
      file = r.filePaths[0];
      programs.set(fileId, file);
    }
    // The window's call (after any recovery under way; the journal keeps it: recovery.ts journaled)
    return windowCall<LoadResult>('mips.load', {
      fileId, path: path.resolve(file), ...(o.target ? { target: o.target } : {}), ...(o.picks ? { picks: o.picks } : {}),
    });
  }));

  ipcMain.handle('about:info', () => ({
    version, electron: process.versions.electron, chrome: process.versions.chrome, node: process.versions.node,
    engine: engine.status().hello, licenses: LICENSES.map((l) => l.title),
  }));
  ipcMain.handle('about:license', (_e, i: number) => answer(() => {
    if (i === LICENSES.length) return readFileSync(paths.electronLicense(), 'utf8');
    return readFileSync(paths.license(LICENSES[i].name), 'utf8');
  }));
  ipcMain.handle('about:openCredits', () => answer(async () => {
    const error = await shell.openPath(paths.chromiumCredits());
    if (error) throw new Error(error);
  }));
  // The caption buttons' patch (titleBarOverlay, drawn by the system) takes
  // the colour the page asks for while it is covered (a dialog's backdrop:
  // src/renderer/shared/overlay.ts); null is white again.  Kept on the
  // window for the tests to read (Electron has no getter for it).
  ipcMain.handle('win:overlay', (_e, color: string | null) => {
    const c = color ?? '#ffffff';
    (win as BrowserWindow & { overlayColor?: string }).overlayColor = c;
    try { win.setTitleBarOverlay({ color: c, symbolColor: NAVY, height: TITLE_BAR_HEIGHT }); } catch { /* no title bar overlay on this platform */ }
  });

  // Maximised before it is shown, every start.  (maximize() shows a hidden
  // window; show() then gives it focus.)
  win.once('ready-to-show', () => { win.maximize(); win.show(); });
  await win.loadFile(paths.page);
}

// Quitting ends the engine first (engine.shutdown, then its end).
let engineDown = false;
app.on('before-quit', (e) => {
  if (engineDown) return;
  e.preventDefault();
  void engine.shutdown().finally(() => { engineDown = true; app.quit(); });
});
app.on('window-all-closed', () => app.quit());
main().catch((e) => {
  console.error(e);
  app.exit(1);
});
