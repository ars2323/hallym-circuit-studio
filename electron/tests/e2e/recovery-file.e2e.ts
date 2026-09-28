/* The whole app dies (N-19, D-152): a file the student has saved at least
   once has a recovery file beside it -- written by the engine after edits,
   and by the engine as it ends when the window's process is gone -- and
   opening that file again asks first (facts, no character): Recover gives
   the model as it was before the crash, as unsaved edits; Discard removes
   the recovery file; Esc opens nothing and leaves it.  A new file never
   saved leaves nothing anywhere.  Saving, closing and quitting remove it.
   With the fake engine (tests/fake-engine); real-engine.e2e.ts kills the
   real one's app the same way. */

import { expect, test, type ElectronApplication, type Page } from '@playwright/test';
import { existsSync, mkdtempSync, readdirSync, readFileSync, rmSync, statSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';

import { answerOpen, GATES, launch, openFile, sample, statusText, visibleCharacters, type LaunchOptions, type Running } from './harness.ts';
import { call, circuitsOf, enginePid, fileModel, killMainAndSeeEngineEnd, openFileIds } from './model.ts';

const TITLE = '저장하지 않은 편집이 있습니다';
const recoveryOf = (f: string) => `${f}.hcs-recover`;
const list = (dir: string) => readdirSync(dir).sort();

// A few edits of the student's in a file's main circuit (through the window's own calls: the journal and the writer see them).
async function edits(page: Page, fileId: string): Promise<void> {
  const c = await circuitsOf(page, fileId);
  const main = Object.values(c)[0];
  const and = await call<{ id: string }>(page, 'edit.addComponent', { fileId, circuitId: main, lib: 'Gates', name: 'AND Gate', loc: [600, 600] });
  await call(page, 'edit.setAttr', { fileId, circuitId: main, ids: [and.id], attr: 'inputs', value: '3' });
  await call(page, 'edit.addComponent', { fileId, circuitId: main, lib: 'Wiring', name: 'Pin', loc: [400, 700] });
  await call(page, 'edit.addWire', { fileId, circuitId: main, points: [[700, 700], [800, 700]] });
}

// Kills the window's process (kill -9) with unsaved edits; the model before it.  Nothing is
// written in HOME (Linux: but the libraries' caches under Chromium, labpc.e2e.ts): the recovery
// file is beside the student's file only.
async function killedWithEdits(r: Running, file: string): Promise<Record<string, unknown>> {
  const homeBefore = readdirSync(r.home, { recursive: true }).map(String);
  await openFile(r, file);
  const [fileId] = await openFileIds(r.app);
  await edits(r.page, fileId);
  const before = await fileModel(r.page, fileId);
  const engine = (await enginePid(r.app))!;
  expect(await killMainAndSeeEngineEnd(r.app, engine, 20_000)).toBe('both ended');
  await Promise.race([r.app.close().catch(() => {}), new Promise((done) => setTimeout(done, 5_000))]);
  const written = readdirSync(r.home, { recursive: true }).map(String).filter((f) => !homeBefore.includes(f) && !/^\.cache(\/|$)/.test(f));
  expect(written, 'nothing in HOME').toEqual([]);
  rmSync(r.dir, { recursive: true, force: true });
  return before;
}

async function openAgain(app: ElectronApplication, page: Page, file: string): Promise<void> {
  await answerOpen(app, file);
  await page.keyboard.press('Control+o');
}

// Recovery files written only after a long idle: the one there comes from the engine's end.
const noIdleWrites: LaunchOptions = { env: { HCS_RECOVERY_IDLE_MS: '600000', HCS_RECOVERY_MAX_MS: '600000' } };

test('the app killed with unsaved edits: the recovery file beside the file, nothing else; opened again, the question; Recover is the model before, unsaved; Ctrl+S removes it', async () => {
  const work = mkdtempSync(path.join(tmpdir(), 'hcs-work-'));
  const file = sample(work, GATES);
  const saved = readFileSync(file);
  const before = await killedWithEdits(await launch(undefined, noIdleWrites), file);
  expect(list(work)).toEqual(['gates.circ', 'gates.circ.hcs-recover']);
  expect(readFileSync(file)).toEqual(saved);
  const modified = statSync(recoveryOf(file)).mtime;

  const r = await launch();
  const { page } = r;
  try {
    await openAgain(r.app, page, file);
    const dialog = page.locator('dialog.ask');
    await expect(dialog.locator('h2')).toHaveText(TITLE);
    await expect(dialog.locator('.askfile')).toHaveText('File: gates.circ');
    const two = (n: number) => String(n).padStart(2, '0');
    const stamp = `${modified.getFullYear()}-${two(modified.getMonth() + 1)}-${two(modified.getDate())} ${two(modified.getHours())}:${two(modified.getMinutes())}`;
    // (the window keeps "다(" together: a word joiner, shared/dom.ts codeText)
    await expect(dialog).toContainText(`마지막으로 저장한 뒤의 편집이 복구 파일에 남아 있습니다\u2060(${stamp}).`);
    await expect(dialog.locator('.askdetail')).toHaveText('복구 파일: gates.circ.hcs-recover');
    await expect(dialog.getByRole('button')).toHaveText(['Discard', 'Recover']);
    expect(await visibleCharacters(page)).toBe(0);
    await expect(page.locator('.filebar .ptab')).toHaveCount(0);   // nothing opened before the answer
    await dialog.getByRole('button', { name: 'Recover' }).click();
    await expect(page.locator('.filebar .ptab')).toHaveText(['gates.circ•']);
    await expect(page.locator('.status')).toContainText('저장하지 않은 편집을 불러왔습니다 · gates.circ');
    const [fileId] = await openFileIds(r.app);
    expect(await fileModel(page, fileId)).toEqual(before);
    expect(list(work)).toEqual(['gates.circ', 'gates.circ.hcs-recover']);   // kept until saved: the app may die again
    await page.keyboard.press('Control+s');
    await expect(page.locator('.status .ok')).toContainText('저장했습니다 · gates.circ');
    await expect(page.locator('.filebar .ptab')).toHaveText(['gates.circ']);
    expect(list(work)).toEqual(['gates.circ']);
  } finally {
    await r.close();
    rmSync(work, { recursive: true, force: true });
  }
});

test('Discard: the file as saved, the recovery file removed; Esc: nothing opened, the recovery file left and asked about again', async () => {
  const work = mkdtempSync(path.join(tmpdir(), 'hcs-work-'));
  const file = sample(work, GATES);
  await killedWithEdits(await launch(undefined, noIdleWrites), file);
  const r = await launch();
  const { page } = r;
  try {
    const dialog = page.locator('dialog.ask');
    await openAgain(r.app, page, file);
    await expect(dialog.locator('h2')).toHaveText(TITLE);
    await page.keyboard.press('Escape');
    await expect(dialog).toHaveCount(0);
    await expect(page.locator('.wcard')).toBeVisible();
    await expect(page.locator('.filebar .ptab')).toHaveCount(0);
    expect(list(work)).toEqual(['gates.circ', 'gates.circ.hcs-recover']);

    await openAgain(r.app, page, file);
    await expect(dialog.locator('h2')).toHaveText(TITLE);
    await dialog.getByRole('button', { name: 'Discard' }).click();
    await expect(page.locator('.filebar .ptab')).toHaveText(['gates.circ']);   // as saved: no unsaved edits
    expect(list(work)).toEqual(['gates.circ']);
    const [fileId] = await openFileIds(r.app);
    const plain = await fileModel(page, fileId);
    // the file as it is on disk
    const s = await launch();
    try {
      await openFile(s, file);
      const [other] = await openFileIds(s.app);
      expect(await fileModel(s.page, other)).toEqual(plain);
    } finally {
      await s.close();
    }
  } finally {
    await r.close();
    rmSync(work, { recursive: true, force: true });
  }
});

test('a new file never saved: killed with edits, nothing is written anywhere -- no recovery file, nothing in HOME', async () => {
  const work = mkdtempSync(path.join(tmpdir(), 'hcs-work-'));
  const runs = mkdtempSync(path.join(tmpdir(), 'hcs-runs-'));
  const r = await launch(undefined, { userData: runs, env: { HCS_RECOVERY_IDLE_MS: '50' } });
  const home = r.home;
  const homeBefore = readdirSync(home, { recursive: true }).map(String).sort();
  await r.page.getByTitle('New circuit (Ctrl+N)').click();
  await expect(r.page.locator('.filebar .ptab')).toHaveText(['untitled.circ']);
  const [fileId] = await openFileIds(r.app);
  await edits(r.page, fileId);
  await r.page.waitForTimeout(300);
  expect(await r.app.evaluate(() => (globalThis as unknown as { __hcs: { writer: { pending(): string[] } } }).__hcs.writer.pending())).toEqual([]);
  const engine = (await enginePid(r.app))!;
  expect(await killMainAndSeeEngineEnd(r.app, engine, 20_000)).toBe('both ended');
  await Promise.race([r.app.close().catch(() => {}), new Promise((done) => setTimeout(done, 5_000))]);
  // Linux: the libraries under Chromium may start their caches in ~/.cache before the app's code runs (labpc.e2e.ts).
  const written = readdirSync(home, { recursive: true }).map(String).filter((f) => !homeBefore.includes(f) && !/^\.cache(\/|$)/.test(f));
  expect(written).toEqual([]);
  expect(list(work)).toEqual([]);
  // The killed run's folder is left in the temp folder until the next start removes it.
  expect(readdirSync(runs)).toHaveLength(1);
  const next = await launch(null, { userData: runs, keepSize: true });
  try {
    await expect(next.page.locator('.wcard')).toBeVisible();
    expect(readdirSync(runs)).toHaveLength(1);   // this run's only
  } finally {
    await next.app.close();
  }
  await expect.poll(() => (existsSync(runs) ? readdirSync(runs) : []), { timeout: 20_000 }).toEqual([]);
  rmSync(r.dir, { recursive: true, force: true });
  rmSync(next.dir, { recursive: true, force: true });
  rmSync(work, { recursive: true, force: true });
  rmSync(runs, { recursive: true, force: true });
});

test('while the app runs: written after the edits settle; closing without saving removes it, so does quitting', async () => {
  const work = mkdtempSync(path.join(tmpdir(), 'hcs-work-'));
  const file = sample(work, GATES);
  const r = await launch(undefined, { env: { HCS_RECOVERY_IDLE_MS: '200' } });
  const { page } = r;
  try {
    await openFile(r, file);
    const [fileId] = await openFileIds(r.app);
    await edits(page, fileId);
    await expect.poll(() => list(work), { timeout: 10_000 }).toEqual(['gates.circ', 'gates.circ.hcs-recover']);
    // Closing without saving (Discard in the window's question): the student chose so.
    await page.getByRole('button', { name: /^Close gates\.circ/ }).click();
    await page.locator('dialog.ask').getByRole('button', { name: 'Discard' }).click();
    await expect(page.locator('.filebar .ptab')).toHaveCount(0);
    expect(list(work)).toEqual(['gates.circ']);
    // Again, then quit.
    await openFile(r, file);
    const [again] = (await openFileIds(r.app)).slice(-1);
    await edits(page, again);
    await expect.poll(() => list(work), { timeout: 10_000 }).toEqual(['gates.circ', 'gates.circ.hcs-recover']);
    expect(await statusText(page)).not.toContain('복구');
  } finally {
    await r.close();
  }
  expect(list(work)).toEqual(['gates.circ']);
  rmSync(work, { recursive: true, force: true });
});

test('a file named on the command line with a recovery file beside it: the question first, then the file', async () => {
  const work = mkdtempSync(path.join(tmpdir(), 'hcs-work-'));
  const file = sample(work, GATES);
  const before = await killedWithEdits(await launch(undefined, noIdleWrites), file);
  const r = await launch(undefined, { args: [file], waitFor: 'dialog.ask' });
  const { page } = r;
  try {
    const dialog = page.locator('dialog.ask');
    await expect(dialog.locator('h2')).toHaveText(TITLE);
    await dialog.getByRole('button', { name: 'Recover' }).click();
    await expect(page.locator('.filebar .ptab')).toHaveText(['gates.circ•']);
    const [fileId] = await openFileIds(r.app);
    expect(await fileModel(page, fileId)).toEqual(before);
  } finally {
    await r.close();
    rmSync(work, { recursive: true, force: true });
  }
});
