/* Starting the real app for the end-to-end tests and the screen captures
   (derived from Hallym MIPS v2.5.0 electron/tests/e2e/harness.ts):
   Electron through Playwright's _electron.launch(), a fresh run folder and
   home every time, the fake engine (tests/fake-engine/fake-engine.ts)
   unless the test says otherwise, and the file dialogs answered from here
   (they are native windows Playwright cannot click). */

import { _electron, type ElectronApplication, type Page } from '@playwright/test';
import { copyFileSync, mkdirSync, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';

export const root = path.join(import.meta.dirname, '..', '..');
export const repo = path.join(root, '..');
export const FAKE_ENGINE = path.join(root, 'tests/fake-engine/fake-engine.ts');
export const fakeEngine = (): string => JSON.stringify([process.execPath, FAKE_ENGINE]);

export interface Running {
  app: ElectronApplication;
  page: Page;
  dir: string;          // a scratch directory, removed by close()
  home: string;         // the app's HOME (and XDG folders): nothing may be written there
  userData: string;     // HCS_USER_DATA: where the run folders go
  close(): Promise<void>;
}

// The lab PCs: 1920x1080 at 100, 125 and 150 %, maximised over a 48 px taskbar.
// HCS_E2E_SIZE=<width>x<height>: the window of every test that does not
// size its own -- tools/e2e-widths.ts runs them all at each size.
export const defaultSize = (() => {
  const m = /^(\d+)x(\d+)$/.exec(process.env.HCS_E2E_SIZE ?? '');
  return m ? { width: Number(m[1]), height: Number(m[2]) } : { width: 1920, height: 1032 };
})();

export interface LaunchOptions {
  args?: string[];              // after the main script: a .circ to open
  env?: Record<string, string>; // over the defaults (HCS_ENGINE_CMD is the fake engine's)
  switches?: string[];          // Chromium's, e.g. --force-device-scale-factor=1.25
  userData?: string;
  keepSize?: boolean;           // the window as the app opened it
  waitFor?: string;             // what shows the window is up (default: the first screen's card)
}

export async function launch(size: { width: number; height: number } | null = defaultSize, options: LaunchOptions = {}): Promise<Running> {
  const dir = mkdtempSync(path.join(tmpdir(), 'hcs-e2e-'));
  const home = path.join(dir, 'home');
  mkdirSync(home);
  const userData = options.userData ?? path.join(dir, 'user-data');
  const env = {
    ...process.env,
    HOME: home, XDG_CONFIG_HOME: path.join(home, '.config'), XDG_CACHE_HOME: path.join(home, '.cache'), XDG_DATA_HOME: path.join(home, '.local/share'),
    HCS_USER_DATA: userData, HCS_ENGINE_CMD: fakeEngine(),
    ...options.env,
  } as Record<string, string>;
  delete env.ELECTRON_RUN_AS_NODE; // set by some tools; Electron would run as plain Node
  for (const [k, v] of Object.entries(options.env ?? {})) if (v === '') delete env[k];
  const app = await _electron.launch({ args: [...(options.switches ?? []), path.join(root, 'src/main/main.ts'), ...(options.args ?? [])], env, cwd: root });
  const page = await app.firstWindow();
  const pageErrors: string[] = [];
  page.on('pageerror', (e) => pageErrors.push(e.message));
  await page.waitForSelector(options.waitFor ?? '.wcard');
  if (size && !options.keepSize) await resize({ app, page }, size);
  return {
    app, page, dir, home, userData,
    close: async () => {
      await app.close();
      rmSync(dir, { recursive: true, force: true });
      if (pageErrors.length) throw new Error(`errors in the window:\n${pageErrors.join('\n')}`);
    },
  };
}

export async function resize(r: { app: ElectronApplication; page: Page }, size: { width: number; height: number }): Promise<void> {
  // The window opens maximised over the work area, and a maximised window
  // keeps its size: restore it first.
  await r.app.evaluate(({ BrowserWindow }, s) => {
    const win = BrowserWindow.getAllWindows()[0];
    if (win.isMaximized()) win.unmaximize();
    win.setContentSize(s.width, s.height);
  }, size);
  // (At a fractional device scale the window can land a pixel off.)
  await r.page.waitForFunction((s) => Math.abs(window.innerWidth - s.width) <= 1 && Math.abs(window.innerHeight - s.height) <= 1, size);
}

// The next open dialog answers `file`; the next save dialog answers `file`.
export async function answerOpen(app: ElectronApplication, file: string): Promise<void> {
  await app.evaluate(({ dialog }, f) => {
    dialog.showOpenDialog = (async () => ({ canceled: false, filePaths: [f] })) as typeof dialog.showOpenDialog;
  }, file);
}
export async function answerSave(app: ElectronApplication, file: string): Promise<void> {
  await app.evaluate(({ dialog }, f) => {
    dialog.showSaveDialog = (async () => ({ canceled: false, filePath: f })) as typeof dialog.showSaveDialog;
  }, file);
}

// A copy of one of the repository's test circuits under `dir`.
export function sample(dir: string, from: string, name = path.basename(from)): string {
  const target = path.join(dir, name);
  copyFileSync(path.join(repo, from), target);
  return target;
}
export const DATAPATH = 'tests/circ/demo-datapath.circ';   // three circuits, tunnels
export const GATES = 'tests/circ/gates.circ';

// Opens `file` through the open dialog (Ctrl+O).
export async function openFile(r: Running, file: string): Promise<void> {
  await answerOpen(r.app, file);
  await r.page.keyboard.press('Control+o');
  try {
    await r.page.locator('.filebar .ptab', { hasText: path.basename(file) }).waitFor();
  } catch (e) {
    // what the window said instead (a notice, an error, the engine's state) -- for a failure seen only on CI
    const said = await r.page.evaluate(() => [...document.querySelectorAll('.status, .notice, .band, dialog[open], .wcard')]
      .map((x) => (x as HTMLElement).innerText.replace(/\s+/g, ' ').slice(0, 200)).join(' | ')).catch(() => '?');
    throw new Error(`${(e as Error).message}\nthe window said: ${said}`);
  }
}

// A new circuit from the first screen (the course → 바로 시작 → 새 회로; A-08). 컴퓨터구조 unless said: it shows everything.
export async function newCircuit(r: Running, course: '논리설계 및 실험' | '컴퓨터구조' = '컴퓨터구조'): Promise<void> {
  await r.page.getByRole('button', { name: new RegExp(course) }).click();
  await r.page.getByRole('button', { name: /바로 시작/ }).click();
  await r.page.getByRole('button', { name: /새 회로/ }).click();
  await r.page.locator('.filebar .ptab').first().waitFor();
}

export const statusText = (page: Page) => page.locator('.status').innerText();

// A toolbar command pressed the way a student does at this window's width (D-158): its button, or on the title bar's
// » menu when the bar had no room for it (tools/e2e-widths.ts runs every test at half a screen too).
export async function command(page: Page, name: string | RegExp): Promise<void> {
  const own = typeof name === 'string' ? page.locator(`.toolbar [data-unit="${name}"]`) : page.locator('.toolbar [data-unit]').filter({ hasText: name });
  if (await own.first().isVisible()) { await own.first().click(); return; }
  await page.locator('.toolbar .more').click();
  await page.locator('.ovmenu.barmenu > button').filter({ has: page.locator('.label', { hasText: name }) }).first().click();
}
// The toolbar's clock speed set to `label` (1 Hz … 4 kHz): its select, or the » menu's Clock Speed ›.
export async function clockSpeed(page: Page, label: string): Promise<void> {
  const select = page.locator('select[aria-label="Clock speed"]');
  if (await select.isVisible()) { await select.selectOption({ label }); return; }
  await page.locator('.toolbar .more').click();
  await page.locator('.ovmenu.barmenu').getByRole('menuitem', { name: /Clock Speed/ }).click();
  await page.locator('.ovmenu').last().getByRole('menuitemradio', { name: label, exact: true }).click();
}

// About, as Hallym MIPS opens it (D-158): the title bar's Preferences, then its About · Licenses.
export async function openAbout(page: Page): Promise<void> {
  await page.getByTitle('Preferences').click();
  await page.locator('dialog.prefs').getByRole('button', { name: 'About · Licenses' }).click();
  await page.locator('dialog.about[open]').waitFor();
}

/* The engine calls the window makes from now on, as the main process sends them (its EngineClient's
   call, wrapped): what an intent carried, e.g. a poke's point and press/release (N-07, N-08). */
export interface SentCall { method: string; params: Record<string, unknown> }
export async function recordCalls(app: ElectronApplication): Promise<void> {
  await app.evaluate(() => {
    type E = { call(method: string, params?: unknown, options?: unknown): Promise<unknown> };
    const g = globalThis as unknown as { __hcs: { engine: E }; __calls?: { method: string; params: unknown }[] };
    g.__calls = [];
    if ((g.__hcs.engine as unknown as { __wrapped?: boolean }).__wrapped) return;
    const call = g.__hcs.engine.call.bind(g.__hcs.engine);
    g.__hcs.engine.call = (method, params, options) => {
      g.__calls?.push({ method, params: JSON.parse(JSON.stringify(params ?? {})) });
      return call(method, params, options);
    };
    (g.__hcs.engine as unknown as { __wrapped?: boolean }).__wrapped = true;
  });
}
export const sentCalls = (app: ElectronApplication, method?: string): Promise<SentCall[]> => app.evaluate((_e, m) => {
  const g = globalThis as unknown as { __calls?: SentCall[] };
  return (g.__calls ?? []).filter((c) => !m || c.method === m);
}, method);

// The university's characters visible anywhere on the page (none may be, next to an error).
export const visibleCharacters = (page: Page): Promise<number> => page.evaluate(() =>
  [...document.querySelectorAll('img.char')].filter((e) => e.checkVisibility({ visibilityProperty: true })).length);

// The Canvas has drawn what it has (canvas.ts settled()): its scene on the page at its size, its first view
// chosen, no frame to come, the last frame this scene's model and values at this view.  Wait on this before
// computing a page point from the view (a double click) or reading a pixel -- never on a guess of how long
// a frame takes: a scene set while the Canvas was off the page (the next file's, loading) keeps the view it
// had until its size is known, and a screenshot taken before the frame shows the one before.
export async function canvasSettled(page: Page): Promise<void> {
  await page.waitForFunction(() => (window as unknown as { __hcsCanvas?: { settled(): boolean } }).__hcsCanvas?.settled() === true);
}

// Values at a subcircuit instance's ports, seen from the parent (the net at each port), by port name.
export const PARENT_PORT_VALUES = (instanceName: string) => {
  type Comp = { id: string; name: string; ports: { i: number; name?: string }[] };
  const c = (window as unknown as { __hcsCanvas: { scene: { components: Map<string, Comp>; portValue(id: string, i: number): string | undefined } } }).__hcsCanvas;
  const k = [...c.scene.components.values()].find((x) => x.name === instanceName)!;
  return Object.fromEntries(k.ports.map((q) => [q.name ?? String(q.i), c.scene.portValue(k.id, q.i) ?? null]));
};
// Values inside the circuit now drawn at its pins, by the pins' labels.
export const INSIDE_PIN_VALUES = () => {
  type Comp = { id: string; name: string; attrs: Record<string, string> };
  const c = (window as unknown as { __hcsCanvas: { scene: { components: Map<string, Comp>; portValue(id: string, i: number): string | undefined } } }).__hcsCanvas;
  return Object.fromEntries([...c.scene.components.values()].filter((x) => x.name === 'Pin' && x.attrs.label).map((x) => [x.attrs.label, c.scene.portValue(x.id, 0) ?? null]));
};
