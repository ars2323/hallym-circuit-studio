/* The lab-PC rule: nothing is kept from one run to the next and nothing is
   written but the files the student saves (and, while a saved file has
   unsaved edits, its recovery file beside it: N-19, D-152).  After quit: the
   run's folder is gone, HOME (and its XDG folders) holds nothing new, the
   opened file is byte for byte as it was; the next start is the first
   screen over the whole work area, not the last run's window, and every
   setting (tests/e2e/settings.ts) is its default again. */

import { expect, test } from '@playwright/test';
import { createHash } from 'node:crypto';
import { existsSync, mkdtempSync, readdirSync, readFileSync, rmSync, statSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';

import { answerSave, DATAPATH, launch, openFile, sample } from './harness.ts';
import { call, circuitsOf, openFileIds } from './model.ts';
import { RUN_DEFAULTS } from '../../src/renderer/app/logic/run-settings.ts';
import { changeEverySetting, settingsNow } from './settings.ts';

// On Linux the libraries under Chromium keep their own caches in the user's
// ~/.cache before the app's code runs -- fontconfig's font cache and the GPU
// driver's shader caches (Mesa, NVIDIA): shared by every program that draws
// text or uses OpenGL, not the app's state.  The app points the rest (NSS's
// certificate store among them) at its run folder (src/main/main.ts).  The
// lab PCs are Windows; their check is N-19's.
const LIBRARY_CACHES = /^\.cache\/(fontconfig|mesa_shader_cache|nvidia)\//;
const written = (home: Record<string, string>) =>
  Object.keys(home).filter((f) => !(process.platform === 'linux' && LIBRARY_CACHES.test(f)));

// Every file under `dir`, with its SHA-256.
const tree = (dir: string): Record<string, string> => Object.fromEntries(readdirSync(dir, { recursive: true }).map(String)
  .filter((n) => statSync(path.join(dir, n)).isFile())
  .map((n) => [n, createHash('sha256').update(readFileSync(path.join(dir, n))).digest('hex')]));

test('nothing written: after quit no run folder, an empty HOME, the opened file untouched; the next start is new', async () => {
  const runs = mkdtempSync(path.join(tmpdir(), 'hcs-runs-'));
  const work = mkdtempSync(path.join(tmpdir(), 'hcs-work-'));
  const r = await launch(undefined, { userData: runs });
  const file = sample(work, DATAPATH);
  const before = tree(work);
  try {
    // A run: a file, a new circuit, a circuit tab, the panels moved, About, the window resized.
    await openFile(r, file);
    await r.page.getByTitle('New circuit (Ctrl+N)').click();
    await expect(r.page.locator('.filebar .ptab')).toHaveCount(2);
    await r.page.getByRole('button', { name: 'Collapse' }).click();
    const split = (await r.page.locator('.splitter').first().boundingBox())!;
    await r.page.mouse.move(split.x + 4, split.y + 100);
    await r.page.mouse.down();
    await r.page.mouse.move(split.x + 120, split.y + 100);
    await r.page.mouse.up();
    await r.page.getByTitle('About').click();
    await r.page.keyboard.press('Escape');
    await r.app.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows()[0].setContentSize(1000, 700));
    expect(readdirSync(runs)).toHaveLength(1); // this run's folder, while it runs
  } finally {
    await r.app.close();
  }
  // After quit: the run's folder goes (removed once the program has exited).
  await expect.poll(() => (existsSync(runs) ? readdirSync(runs) : []), { timeout: 20_000 }).toEqual([]);
  expect(written(tree(r.home))).toEqual([]);
  expect(tree(work)).toEqual(before);

  // The next start: the first screen, over the whole work area; nothing of the last run.
  const next = await launch(null, { userData: runs, keepSize: true });
  try {
    await expect(next.page.locator('.wcard')).toBeVisible();
    await expect(next.page.locator('.action').first()).toContainText('튜토리얼 보기');
    const w = await next.app.evaluate(({ BrowserWindow, screen }) => {
      const b = BrowserWindow.getAllWindows()[0];
      return { maximized: b.isMaximized(), bounds: b.getBounds(), area: screen.getPrimaryDisplay().workArea };
    });
    if (!w.maximized) expect(w.bounds).toEqual(w.area);
  } finally {
    await next.close();
  }
  rmSync(runs, { recursive: true, force: true });
  rmSync(work, { recursive: true, force: true });
});

test('the opened file is not written by opening it; the only files written are the one the student saves and, while it has unsaved edits, its recovery file', async () => {
  const r = await launch(undefined, { env: { HCS_RECOVERY_IDLE_MS: '200' } });
  const work = mkdtempSync(path.join(tmpdir(), 'hcs-work-'));
  try {
    const file = sample(work, DATAPATH);
    const before = tree(work);
    await openFile(r, file);
    await r.page.getByRole('tab', { name: 'Circuits' }).click();
    await r.page.locator('.upper .pbody:visible .list > li', { hasText: 'alu' }).getByRole('button').click();
    expect(tree(work)).toEqual(before);
    // A new circuit, saved: the save dialog's file, and nothing else.
    await r.page.getByTitle('New circuit (Ctrl+N)').click();
    await answerSave(r.app, path.join(work, 'mine.circ'));
    await r.page.keyboard.press('Control+s');
    await expect(r.page.locator('.filebar .ptab.on')).toHaveText('mine.circ');
    await expect(r.page.locator('.status .ok')).toHaveText('저장했습니다 · mine.circ');
    expect(Object.keys(tree(work)).sort()).toEqual(['demo-datapath.circ', 'mine.circ']);
    expect(tree(work)['demo-datapath.circ']).toBe(before['demo-datapath.circ']);
    // An edit of the saved file: its recovery file beside it (N-19), until it is saved.
    const [, mine] = await openFileIds(r.app);
    await call(r.page, 'edit.addComponent', { fileId: mine, circuitId: (await circuitsOf(r.page, mine)).main, lib: 'Gates', name: 'OR Gate', loc: [300, 300] });
    await expect.poll(() => Object.keys(tree(work)).sort(), { timeout: 10_000 }).toEqual(['demo-datapath.circ', 'mine.circ', 'mine.circ.hcs-recover']);
    await r.page.keyboard.press('Control+s');
    await expect(r.page.locator('.filebar .ptab.on')).toHaveText('mine.circ');
    expect(Object.keys(tree(work)).sort()).toEqual(['demo-datapath.circ', 'mine.circ']);
  } finally {
    await r.close();
    rmSync(work, { recursive: true, force: true });
  }
});

test('every setting is for this run only: changed, quit, started again -- each is its default; nothing written', async () => {
  const runs = mkdtempSync(path.join(tmpdir(), 'hcs-runs-'));
  const work = mkdtempSync(path.join(tmpdir(), 'hcs-work-'));
  const file = sample(work, DATAPATH);
  const before = tree(work);
  const r = await launch(undefined, { userData: runs });
  let defaults;
  try {
    await openFile(r, file);
    defaults = await settingsNow(r.page);
    // the defaults (logic/run-settings.ts): 1 Hz, bus widths shown, the panels' first tabs, the main circuit only
    expect(defaults).toMatchObject({ hz: String(RUN_DEFAULTS.hz), busWidths: RUN_DEFAULTS.busWidths, bottomCollapsed: false,
      tabs: ['Components', 'Tunnels', 'Messages'], circuitTabs: ['main'] });
    await changeEverySetting(r.page, 'alu');
    const changed = await settingsNow(r.page);
    for (const k of Object.keys(defaults) as (keyof typeof defaults)[]) expect(changed[k], `${k} changed`).not.toEqual(defaults[k]);
    for (const k of Object.keys(defaults.sizes)) expect(changed.sizes[k], `${k} changed`).not.toEqual(defaults.sizes[k]);
  } finally {
    await r.app.close();
  }
  await expect.poll(() => (existsSync(runs) ? readdirSync(runs) : []), { timeout: 20_000 }).toEqual([]);
  expect(written(tree(r.home))).toEqual([]);
  expect(tree(work)).toEqual(before);
  rmSync(r.dir, { recursive: true, force: true });

  const next = await launch(undefined, { userData: runs });
  try {
    await openFile(next, file);
    await expect(next.page.locator('.zoom-button')).toBeVisible();
    expect(await settingsNow(next.page)).toEqual(defaults);
  } finally {
    await next.close();
  }
  rmSync(runs, { recursive: true, force: true });
  rmSync(work, { recursive: true, force: true });
});

test('no spell checker, no spelling language: nothing opened (Windows\' word lists) or downloaded (a Hunspell dictionary)', async () => {
  const r = await launch();
  try {
    // Off, and no language it could check: none of the list is a language it knows (Linux keeps "zz", Windows drops it).
    expect(await r.app.evaluate(({ session }) => {
      const known = session.defaultSession.availableSpellCheckerLanguages;
      return [session.defaultSession.isSpellCheckerEnabled(), session.defaultSession.getSpellCheckerLanguages().filter((l) => known.includes(l)), known.length > 10];
    })).toEqual([false, [], true]);
    // Left alone, Chromium downloads the OS language's dictionary into the run's folder (Dictionaries/) at once.
    await r.page.waitForTimeout(3000);
    const run = readdirSync(r.userData).find((n) => n.startsWith('run-'))!;
    const dicts = path.join(r.userData, run, 'Dictionaries');
    expect(existsSync(dicts) ? readdirSync(dicts) : []).toEqual([]);
  } finally {
    await r.close();
  }
});
