/* Starting the real app for the end-to-end tests and the screen captures
   (derived from Hallym MIPS v2.3.0 electron/tests/e2e/harness.ts):
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
  await r.page.locator('.filebar .ptab', { hasText: path.basename(file) }).waitFor();
}

// A new circuit from the first screen (바로 시작 → 새 회로).
export async function newCircuit(r: Running): Promise<void> {
  await r.page.getByRole('button', { name: /바로 시작/ }).click();
  await r.page.getByRole('button', { name: /새 회로/ }).click();
  await r.page.locator('.filebar .ptab').first().waitFor();
}

export const statusText = (page: Page) => page.locator('.status').innerText();

// The university's characters visible anywhere on the page (none may be, next to an error).
export const visibleCharacters = (page: Page): Promise<number> => page.evaluate(() =>
  [...document.querySelectorAll('img.char')].filter((e) => e.checkVisibility({ visibilityProperty: true })).length);
