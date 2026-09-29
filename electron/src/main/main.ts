/* Electron's main process: the host (derived from Hallym MIPS v2.5.0
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

   If the whole app dies, a file the student has saved at least once has a
   recovery file beside it (recovery-files.ts, N-19, D-152: written by the
   engine after edits, and by the engine as it ends when this process is
   gone; removed on save, close and quit).  Opening that file again asks
   first (Recover / Discard): openPath below, file:openRecovery.

   Leaving with unsaved changes -- the window's close button, Alt+F4, the PC
   shutting down, Ctrl+Q in the window -- asks first, a file at a time (Save
   / Discard / Cancel: the window's logic/unsaved.ts), and the window closes
   only when it says so ('app:leave'); the quit then ends the engine, which
   removes the recovery files.  A quit the program asks for itself
   (app.quit(), also how the test tools end it) does not ask.

   Nothing is kept from one run to the next -- lab PCs are shared, and every
   student starts from the same screen: the window's size, the panels, the
   files opened.  Chromium's profile is this run's folder in the temp folder
   (run-folder.ts), removed after quit; the engine runs in it too. */

import { app, BrowserWindow, dialog, ipcMain, Menu, screen, shell } from 'electron';
import { spawn } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';

import { EngineClient, EngineError, type EngineProcess } from './engine.ts';
import { EXAMPLES, examplesDir, isExample } from './examples.ts';
import { locateEngine } from './engine-locate.ts';
import { Supervisor, WINDOW } from './recovery.ts';
import { recoveryBeside, RecoveryWriter } from './recovery-files.ts';
import { LICENSES, paths, version } from './paths.ts';
import { IMAGE_FILTER, programDialogPath } from './program-path.ts';
import { registerCircuitFiles } from './circuit-files.ts';
import { FileWindows, type Handover, halves, offset } from './windows.ts';
import { WINDOW_METHODS, type EngineStatus, type LoadResult, type OpenResult, type RecoveryAsk, type SaveResult } from './protocol.ts';
import { circArgument, removeAfterExitScript, removeEarlierRuns, runDirName, RUN_PREFERENCES, runsDirFor } from './run-folder.ts';

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
// Chromium's preferences for this run: no spell checker at all (run-folder.ts RUN_PREFERENCES).
writeFileSync(path.join(runDir, 'Preferences'), JSON.stringify(RUN_PREFERENCES));
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
  // The engine keeps the recovery files beside the student's files (N-19, D-152); a restarted
  // engine's ids start above every id the window has seen (D-142).
  helloParams: () => ({ recoveryFiles: true, ...(supervisor?.helloParams() ?? {}) }),
  launch: (): EngineProcess => {
    if (!located.ok) throw new Error(`${located.reason}\n${located.looked.map((l) => `  ${l}`).join('\n')}`);
    const env = { ...process.env };
    delete env.ELECTRON_RUN_AS_NODE;
    // Windows: not in libuv's kill-on-close job object (detached), so that if this process is killed the engine
    // is not killed with it at once but sees its parent end (Main.watchParent) and first writes the recovery
    // files of the unsaved files (N-19, D-152).  Its stdio stay these pipes; it ends with this process all the same.
    return spawn(located.engine.command, located.engine.args, {
      cwd: located.engine.cwd, env, stdio: 'pipe', windowsHide: true, detached: process.platform === 'win32',
    });
  },
});
supervisor = new Supervisor(engine);
const recovery = supervisor;
// The recovery files beside the student's files (N-19): written after the window's edits, a file with a place only.
const envMs = (name: string): number | undefined => (process.env[name] ? Number(process.env[name]) : undefined);
const writer = new RecoveryWriter(engine, {
  idleMs: envMs('HCS_RECOVERY_IDLE_MS'), maxWaitMs: envMs('HCS_RECOVERY_MAX_MS'),
  hasPlace: (fileId) => recovery.journal.files.get(fileId)?.opened.kind === 'path',
  settled: () => recovery.settled(),
});
writer.on('failed', (fileId, message) => console.error(`recovery file of ${fileId}: ${message}`));
if (process.env.HCS_ENGINE_LOG === '1') engine.on('log', (line) => console.log(`[engine] ${line}`));
// For the e2e tests (app.evaluate): the engine, to end it as a crash would; the journal; the recovery files' writer.
(globalThis as { __hcs?: unknown }).__hcs = { engine, recovery, writer };

// The files the engine has open, by id: the same file opened again goes to
// its tab.  They outlive a restart of the engine (recovery.ts opens them
// again under the same ids), except those that could not be opened again.
// Every open file and where it is saved (null: never), whichever window holds it.  Every window hears of a change
// (files:changed): files of one name are told apart by their folders in all of them (N-11, v1 V-05).
class OpenFiles extends Map<string, string | null> {
  onChange: (() => void) | null = null;
  override set(fileId: string, path: string | null): this { super.set(fileId, path); this.onChange?.(); return this; }
  override delete(fileId: string): boolean { const gone = super.delete(fileId); if (gone) this.onChange?.(); return gone; }
  list(): { fileId: string; path: string | null }[] { return [...this].map(([fileId, path]) => ({ fileId, path })); }
}
const openFiles = new OpenFiles();
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
  recovered?: boolean;  // opened from its recovery file: unsaved edits (N-19)
  readOnly?: boolean;   // an example, opened read-only: Save asks where (D-158)
}

// Files with a recovery file beside them, waiting for the window's answer (file:openRecovery), by id.
const asked = new Map<string, string>();
let nextAsk = 1;

// The files opened read-only (Help › Examples, D-158): Save asks where (Save As), and never writes over them.
const readOnlyFiles = new Set<string>();
// File › Open Recent (I-130): the files opened or saved in this run, the latest first -- this run only (N-19).
const recent: { id: string; path: string }[] = [];
let nextRecent = 1;
function remember(p: string): void {
  const at = recent.findIndex((r) => path.resolve(r.path) === path.resolve(p));
  const id = at >= 0 ? recent.splice(at, 1)[0].id : `f${nextRecent++}`;
  recent.unshift({ id, path: p });
  recent.length = Math.min(recent.length, 8);
}

// Opens a file: first, if it has a recovery file beside it (N-19, D-152), the window asks.
async function openPath(p: string, recovery?: 'recover' | 'discard', readOnly = false): Promise<Opened | RecoveryAsk> {
  for (const [fileId, open] of openFiles) {
    if (open !== null && path.resolve(open) === path.resolve(p)) {
      return { fileId, path: p, name: path.basename(p), circuits: [], main: '', libraries: [], already: true };
    }
  }
  const beside = recovery ? null : recoveryBeside(path.resolve(p));
  if (beside) {
    const id = `r${nextAsk++}`;
    asked.set(id, p);
    return { ask: { id, name: path.basename(p), recovery: path.basename(beside.path), modified: beside.modified } };
  }
  const r = await windowCall<OpenResult>('file.open', { path: path.resolve(p), ...(recovery ? { recovery } : {}), ...(readOnly ? { readOnly: true } : {}) });
  openFiles.set(r.fileId, p);
  if (readOnly) readOnlyFiles.add(r.fileId); else remember(p);
  // The tab shows the file's own name (with .circ), not Logisim's project name.
  return { ...r, name: path.basename(p), path: p, already: r.alreadyOpen === true, ...(recovery === 'recover' ? { recovered: true } : {}), ...(readOnly ? { readOnly: true } : {}) };
}

async function main(): Promise<void> {
  Menu.setApplicationMenu(null); // no default zoom/reload accelerators; the window has its own keys
  await app.whenReady();
  void engine.start().catch(() => { /* status says so; the window shows the dialog */ });

  // Every start opens the window over the whole work area, maximised --
  // nothing of its size or place is kept.  (The bounds first: a display
  // with no window manager to maximise it still gets the work area.)
  const area = screen.getPrimaryDisplay().workArea;
  const win = makeWindow(area);
  mainWindow = win;
  wireWindow(win);

  // The engine's status as every window should see it (restarting until its files are back).
  const sendAll = (channel: string, ...args: unknown[]) => { for (const w of windows.all()) w.webContents.send(channel, ...args); };
  recovery.on('status', (s: EngineStatus) => sendAll('engine:status', s));
  // While a recovery replays, the engine's changes are of parts the window never had: held back.
  // A notification goes to the window that holds the file it names (windows.ts).
  engine.on('notification', (method, params) => {
    if (recovery.quiet()) return;
    const w = windows.route(params);
    if (w && !w.isDestroyed()) w.webContents.send('engine:notify', method, params);
  });
  recovery.on('recovered', (r) => sendAll('engine:recovered', r));
  openFiles.onChange = () => sendAll('files:changed', openFiles.list());
  // The window a call came from (a dialog's parent).
  const from = (e: Electron.IpcMainInvokeEvent): BrowserWindow => BrowserWindow.fromWebContents(e.sender) ?? win;
  WINDOW_OF = from;

  registerHandlers();

  // Maximised before it is shown, every start.  (maximize() shows a hidden
  // window; show() then gives it focus.)
  win.once('ready-to-show', () => { win.maximize(); win.show(); });
  await win.loadFile(paths.page);
}

// ---- the windows (N-11: a file tab in a window of its own, View Side by Side; windows.ts) ----

let mainWindow: BrowserWindow | null = null;
const windows = new FileWindows(() => mainWindow);
let WINDOW_OF: (e: Electron.IpcMainInvokeEvent) => BrowserWindow = () => mainWindow!;

function makeWindow(bounds: { x: number; y: number; width: number; height: number }): BrowserWindow {
  const w = new BrowserWindow({
    x: bounds.x, y: bounds.y, width: bounds.width, height: bounds.height,
    minWidth: 640,   // half a 1280 screen (v1 D-105); 683 px (half a 1366 one) is the tight layout (D-158)
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
      spellcheck: false,   // no spell checker: names are not words (and run-folder.ts RUN_PREFERENCES)
    },
  });
  return w;
}

function wireWindow(w: BrowserWindow): void {
  // No page zoom by a pinch (I-207): the Canvas zooms itself; Ctrl+wheel is the page's to stop (app.ts)
  void w.webContents.setVisualZoomLevelLimits(1, 1);
  // A file dropped where the page does not take it never replaces the page (I-181: .circ files are opened instead)
  w.webContents.on('will-navigate', (e) => e.preventDefault());
  w.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
}

// A file tab into a window of its own: 'window' (Detach Tab, a tab dragged out: +60, +60) or 'side' (View Side by Side).
async function detach(fileId: string, handover: Handover, how: 'window' | 'side'): Promise<boolean> {
  const main = mainWindow;
  if (!main || main.isDestroyed() || windows.detached().some((d) => d.fileId === fileId)) return false;
  const area = screen.getDisplayMatching(main.getBounds()).workArea;
  let bounds = offset(main.getBounds(), area);
  if (how === 'side') {
    const h = halves(area);
    if (main.isMaximized()) main.unmaximize();
    main.setBounds(h.left);
    bounds = h.right;
  }
  const w = makeWindow(bounds);
  const id = w.webContents.id;
  wireWindow(w);
  windows.adopt(fileId, w, handover);
  // Closing a window of its own closes its file: the page asks first (the save question), then says close.
  let closing = false;
  w.on('close', (e) => {
    if (closing || quitting || !windows.fileOf(w)) return;
    e.preventDefault();
    w.webContents.send('win:closeRequest');
  });
  w.on('closed', () => { windows.release(fileId); dirtyBy.delete(id); reportedDirty(); });
  (w as BrowserWindow & { hcsClose?: () => void }).hcsClose = () => { closing = true; w.close(); };
  w.once('ready-to-show', () => w.show());
  await w.loadFile(paths.page, { query: { detached: fileId } });
  return true;
}

// Every window of its own asks about its file; true when all of them closed (the main window may close then).
const cancelled = new Map<number, () => void>();   // a window of its own's webContents id → its question was cancelled
async function closeDetached(): Promise<boolean> {
  for (const d of windows.detached()) {
    const id = d.window.webContents.id;
    const closed = await new Promise<boolean>((done) => {
      d.window.once('closed', () => { cancelled.delete(id); done(true); });
      cancelled.set(id, () => { cancelled.delete(id); done(false); });
      d.window.webContents.send('win:closeRequest');
    });
    if (!closed) return false;
  }
  return true;
}

function registerHandlers(): void {
  const win = mainWindow!;
  const from = WINDOW_OF;
  const send = (channel: string, ...args: unknown[]) => { if (!win.isDestroyed()) win.webContents.send(channel, ...args); };

  const allowed = new Set<string>(WINDOW_METHODS);
  ipcMain.handle('engine:call', (_e, method: string, params: unknown) => answer(async () => {
    if (!allowed.has(method)) throw new Error(`not a method the window may call: ${method}`);
    const result = await windowCall(method, params);
    if (method === 'file.new') openFiles.set((result as { fileId: string }).fileId, null);
    if (method === 'file.close') { openFiles.delete((params as { fileId: string }).fileId); readOnlyFiles.delete((params as { fileId: string }).fileId); }
    return result;
  }));
  ipcMain.handle('engine:status', () => recovery.view(engine.status()));
  ipcMain.handle('files:all', () => openFiles.list());
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
  ipcMain.handle('file:open', (e) => answer(async () => {
    const r = await dialog.showOpenDialog(from(e), { filters: [{ name: 'Logisim circuit', extensions: ['circ'] }, { name: 'All files', extensions: ['*'] }] });
    if (r.canceled || r.filePaths.length === 0) return null;
    return openPath(r.filePaths[0]);
  }));
  // The answer to a recovery file's question (N-19): Recover, Discard, or null (Esc: nothing opened, the file left).
  ipcMain.handle('file:openRecovery', (_e, id: string, choice: 'recover' | 'discard' | null) => answer(async () => {
    const p = asked.get(id);
    asked.delete(id);
    if (p === undefined || (choice !== 'recover' && choice !== 'discard')) return null;
    return openPath(p, choice);
  }));
  ipcMain.handle('file:save', (e, fileId: string, file: { name: string; saveAs?: boolean }) => answer(async () => {
    if (!openFiles.has(fileId)) throw new Error(`no open file ${fileId}`);
    let target = openFiles.get(fileId) ?? null;
    // A file opened read-only (an example) is saved somewhere else: Save As (v1 D-102).
    if (target === null || file.saveAs || readOnlyFiles.has(fileId)) {
      const r = await dialog.showSaveDialog(from(e), { defaultPath: file.name, filters: [{ name: 'Logisim circuit', extensions: ['circ'] }] });
      if (r.canceled || !r.filePath) return null;
      target = r.filePath;
    }
    const saved = await windowCall<SaveResult>('file.save', { fileId, path: target });
    openFiles.set(fileId, saved.path || target);
    readOnlyFiles.delete(fileId);   // saving to a path makes it writable (docs/engine-api.md file.save)
    remember(saved.path || target);
    return { path: saved.path || target, name: path.basename(saved.path || target), bytes: saved.bytes, needsMipsJar: saved.needsMipsJar === true };
  }));

  // Load Program (N-16, D-147): the dialog (.hmx only) here, then the engine's
  // mips.load.  `again` loads the file picked last for this circuit (the
  // answer to "which memory?": `picks`); `forSource` opens next to an old .s.
  const programs = new Map<string, string>();
  ipcMain.handle('program:load', (e, fileId: string, o: { target?: string; picks?: Record<string, string>; again?: boolean; forSource?: string } = {}) => answer(async () => {
    if (!openFiles.has(fileId)) throw new Error(`no open file ${fileId}`);
    let file = o.again ? programs.get(fileId) : undefined;
    if (!file) {
      const r = await dialog.showOpenDialog(from(e), {
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

  // A RAM's or a ROM's image (N-10, D-157; the original MemMenu's Load Image… and Save Image…): the dialog here,
  // then the engine -- a RAM's contents are the simulation's (mem.loadImage), a ROM's its Contents attribute
  // (edit.memContents with the file: one undo step, journaled); Save Image… writes either (mem.saveImage).
  const images = new Map<string, string>();
  ipcMain.handle('memory:image', (_e, fileId: string, o: { circuitId: string; root: string; path: string[]; componentId: string; kind: 'ram' | 'rom'; mode: 'load' | 'save' }) => answer(async () => {
    if (!openFiles.has(fileId)) throw new Error(`no open file ${fileId}`);
    const title = `${o.mode === 'load' ? 'Load' : 'Save'} ${o.kind === 'ram' ? 'RAM' : 'ROM'} Image`;
    const last = images.get(`${fileId} ${o.componentId}`) ?? images.get(fileId);
    let file: string;
    if (o.mode === 'load') {
      const r = await dialog.showOpenDialog(win, { title, ...(last ? { defaultPath: last } : {}), properties: ['openFile'] });
      if (r.canceled || r.filePaths.length === 0) return null;
      file = r.filePaths[0];
    } else {
      const r = await dialog.showSaveDialog(win, { title, ...(last ? { defaultPath: last } : {}) });
      if (r.canceled || !r.filePath) return null;
      file = r.filePath;
    }
    file = path.resolve(file);
    images.set(`${fileId} ${o.componentId}`, file);
    images.set(fileId, file);
    const base = { fileId, circuitId: o.root, path: o.path, componentId: o.componentId };
    if (o.mode === 'save') return windowCall('mem.saveImage', { ...base, file });
    if (o.kind === 'ram') return windowCall('mem.loadImage', { ...base, file });
    return windowCall('edit.memContents', { fileId, circuitId: o.circuitId, id: o.componentId, file });
  }));
  // Help › Examples (V-07, D-158): opened read-only from the program's own folder.
  const exampleFolder = examplesDir(app.isPackaged ? process.resourcesPath : null, paths.repoRoot);
  ipcMain.handle('examples:list', () => (exampleFolder ? EXAMPLES.filter((n) => existsSync(path.join(exampleFolder, n))).map((n) => ({ id: n, name: n })) : []));
  ipcMain.handle('examples:open', (_e, id: string) => answer(async () => {
    if (!exampleFolder || !isExample(id)) return null;
    return openPath(path.join(exampleFolder, id), undefined, true);
  }));
  // File › Open Recent (I-130): names only for the window; the path stays here.
  ipcMain.handle('file:recent', () => recent.map((r) => ({ id: r.id, name: path.basename(r.path) })));
  ipcMain.handle('file:openRecent', (_e, id: string) => answer(async () => {
    const r = recent.find((x) => x.id === id);
    return r ? openPath(r.path) : null;
  }));
  // Window › Minimize (Ctrl+M), Maximize (I-155, I-157).
  ipcMain.handle('win:minimize', (e) => { from(e).minimize(); });
  ipcMain.handle('win:maximize', (e) => { const w = from(e); if (w.isMaximized()) w.unmaximize(); else w.maximize(); });

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
  ipcMain.handle('win:overlay', (e, color: string | null) => {
    const w = from(e);
    const c = color ?? '#ffffff';
    (w as BrowserWindow & { overlayColor?: string }).overlayColor = c;
    try { w.setTitleBarOverlay({ color: c, symbolColor: NAVY, height: TITLE_BAR_HEIGHT }); } catch { /* no title bar overlay on this platform */ }
  });

  // Leaving (N-19, D-152): the window asks about unsaved files first; it closes when the window says so.
  // The windows of their own first (N-11): each asks about its file and goes; then the main window's files.
  win.on('close', (e) => {
    if (!quitting && windows.detached().length > 0) {
      e.preventDefault();
      void closeDetached().then((ok) => { if (ok && !win.isDestroyed()) win.close(); });
      return;
    }
    if (leaveConfirmed || quitting || !windowListens || win.webContents.isCrashed()) return;
    e.preventDefault();
    send('app:leave');
  });
  // The PC shutting down (Windows): held back while there are unsaved files, and the window asks.
  win.on('query-session-end', (e) => {
    if (!windowDirty || leaveConfirmed || !windowListens) return;
    e.preventDefault();
    send('app:leave');
  });
  // Ctrl+Q in a window of its own: the whole app leaves, as the main window's close button (N-11).
  ipcMain.handle('app:leave', (e) => {
    if (!windows.isMain(from(e))) { if (!win.isDestroyed()) win.close(); return; }
    leaveConfirmed = true;
    if (!win.isDestroyed()) win.close();
  });
  // Every window tells whether its files have unsaved changes; the PC's shutdown asks when any has.
  ipcMain.handle('app:dirty', (e, dirty: boolean) => {
    if (windows.isMain(from(e))) windowListens = true;
    dirtyBy.set(e.sender.id, dirty === true);
    reportedDirty();
  });

  // Circuits from other files, libraries, Edit Original File (N-11): the dialogs here, the paths never in the page.
  registerCircuitFiles({
    dialog, windowCall, openFiles, openPath,
    parent: (e) => from(e),
    isMain: (e) => windows.isMain(from(e)),
    handle: (channel, f) => ipcMain.handle(channel, (e, ...args: unknown[]) => answer(() => f(e, ...args))),
  });

  // The windows (N-11): what a window of its own starts from, Detach / Side by Side / Attach, closing one.
  ipcMain.handle('win:role', (e) => ({ main: windows.isMain(from(e)), handover: windows.handoverFor(e.sender.id) }));
  ipcMain.handle('win:detach', (e, fileId: string, handover: Handover, how: 'window' | 'side') => answer(async () => {
    if (!windows.isMain(from(e)) || !openFiles.has(fileId)) return false;
    return detach(fileId, { ...handover, fileId, path: openFiles.get(fileId) ?? null }, how === 'side' ? 'side' : 'window');
  }));
  ipcMain.handle('win:attach', (e, handover: Handover) => answer(() => {
    const w = from(e);
    const fileId = windows.fileOf(w);
    if (!fileId || !mainWindow || mainWindow.isDestroyed()) return false;
    windows.release(fileId);
    mainWindow.webContents.send('win:adopt', { ...handover, fileId, path: openFiles.get(fileId) ?? null });
    if (mainWindow.isMinimized()) mainWindow.restore();
    // the last window of its own back: the main window over the whole work area again (v1: back where the group is)
    if (windows.detached().length === 0 && !mainWindow.isMaximized()) mainWindow.maximize();
    mainWindow.focus();
    (w as BrowserWindow & { hcsClose?: () => void }).hcsClose?.();
    return true;
  }));
  // A window of its own whose file was closed: it goes (hcsClose skips the question, it was asked).
  ipcMain.handle('win:closed', (e) => {
    const w = from(e);
    if (windows.isMain(w)) return false;
    windows.release(windows.fileOf(w) ?? '');
    (w as BrowserWindow & { hcsClose?: () => void }).hcsClose?.();
    return true;
  });
  // The question before closing a window of its own was cancelled.
  ipcMain.on('win:closeCancelled', (e) => { cancelled.get(e.sender.id)?.(); });
  // The windows, for the tests: how many, the files of their own.
  (globalThis as { __hcsWindows?: unknown }).__hcsWindows = windows;
}

// Leaving: the window's answer given (app:leave), a quit under way, the window listening, unsaved files in it.
let leaveConfirmed = false;
let quitting = false;
let windowListens = false;
let windowDirty = false;
const dirtyBy = new Map<number, boolean>();   // a window's webContents id → it has unsaved files (N-11: every window)
function reportedDirty(): void { windowDirty = [...dirtyBy.values()].some(Boolean); }

// Quitting ends the engine first (engine.shutdown, then its end).
let engineDown = false;
app.on('before-quit', (e) => {
  quitting = true;
  if (engineDown) return;
  e.preventDefault();
  void engine.shutdown().finally(() => { engineDown = true; app.quit(); });
});
app.on('window-all-closed', () => app.quit());
main().catch((e) => {
  console.error(e);
  app.exit(1);
});
