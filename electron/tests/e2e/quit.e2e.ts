/* Leaving with unsaved changes (N-19, D-152): the window's close button (and
   Alt+F4, the taskbar's Close: the window's close), Ctrl+Q and the PC
   shutting down ask once for each file with unsaved changes -- Save (the
   normal save; the Save As dialog for a file never saved) / Discard /
   Cancel, plain, no character -- and only then does the app quit (and the
   engine remove the recovery files).  Cancel, Esc or a cancelled save keep
   the app open with everything as it was.  Closing a file's tab asks the
   same.  With the fake engine; real-engine.e2e.ts quits the real one. */

import { expect, test, type ElectronApplication, type Page } from '@playwright/test';
import { existsSync, mkdtempSync, readdirSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';

import { answerSave, GATES, launch, openFile, sample, visibleCharacters, type Running } from './harness.ts';
import { call, circuitsOf, openFileIds } from './model.ts';

const TITLE = '저장하지 않은 변경이 있습니다';
const list = (dir: string) => readdirSync(dir).sort();

const closeWindow = (app: ElectronApplication) => app.evaluate(({ BrowserWindow }) => { BrowserWindow.getAllWindows()[0].close(); });
const windows = (app: ElectronApplication) => app.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows().length);

async function edit(page: Page, fileId: string, x: number): Promise<void> {
  const c = await circuitsOf(page, fileId);
  await call(page, 'edit.addComponent', { fileId, circuitId: Object.values(c)[0], lib: 'Gates', name: 'NOT Gate', loc: [x, 500] });
}

// gates.circ (saved on disk, edited: its recovery file beside it) and a new circuit (edited), gates.circ on show.
async function twoUnsaved(r: Running, work: string): Promise<{ gates: string; ids: string[] }> {
  const gates = sample(work, GATES);
  await r.page.getByTitle('New circuit (Ctrl+N)').click();
  await openFile(r, gates);
  const ids = await openFileIds(r.app);
  await edit(r.page, ids[0], 100);
  await edit(r.page, ids[1], 200);
  await expect(r.page.locator('.filebar .ptab')).toHaveText(['untitled.circ•', 'gates.circ•']);
  await expect.poll(() => list(work), { timeout: 10_000 }).toEqual(['gates.circ', 'gates.circ.hcs-recover']);
  return { gates, ids };
}

// After the test: the app gone (its quit awaited, or ended here), the scratch folder removed.
async function gone(r: Running, quit: Promise<unknown>): Promise<void> {
  const ended = await Promise.race([quit.then(() => true, () => true), new Promise((done) => setTimeout(() => done(false), 1_000))]);
  if (!ended) await r.app.close().catch(() => {});
  rmSync(r.dir, { recursive: true, force: true, maxRetries: 10, retryDelay: 300 });
}

test('the close button with unsaved files: the question for each (plain, Save / Discard / Cancel); Cancel and Esc keep everything; Ctrl+Q, Save, Save As for the new one, then the app quits', async () => {
  const work = mkdtempSync(path.join(tmpdir(), 'hcs-work-'));
  const r = await launch(undefined, { env: { HCS_RECOVERY_IDLE_MS: '100' } });
  const { page, app } = r;
  const quit = app.waitForEvent('close', { timeout: 60_000 });
  try {
    const { gates } = await twoUnsaved(r, work);
    const dialog = page.locator('dialog.ask');
    // The close button: asked about the file on show first, nothing closed yet.
    await closeWindow(app);
    await expect(dialog.locator('h2')).toHaveText(TITLE);
    await expect(dialog.locator('.askfile')).toHaveText('File: untitled.circ');
    await expect(dialog).toContainText('저장하지 않고 끝내면 이 파일의 변경은 사라집니다.');
    await expect(dialog.getByRole('button')).toHaveText(['Cancel', 'Discard', 'Save']);
    expect(await visibleCharacters(page)).toBe(0);
    await dialog.getByRole('button', { name: 'Cancel' }).click();
    await expect(dialog).toHaveCount(0);
    expect(await windows(app)).toBe(1);
    await expect(page.locator('.filebar .ptab')).toHaveText(['untitled.circ•', 'gates.circ•']);
    expect(list(work)).toEqual(['gates.circ', 'gates.circ.hcs-recover']);
    // Esc is Cancel.
    await closeWindow(app);
    await expect(dialog.locator('h2')).toHaveText(TITLE);
    await page.keyboard.press('Escape');
    await expect(dialog).toHaveCount(0);
    expect(await windows(app)).toBe(1);
    // Ctrl+Q: the same questions; Save for the new file (the Save As dialog), then gates.circ, then it quits.
    await answerSave(app, path.join(work, 'mine.circ'));
    await page.keyboard.press('Control+q');
    await expect(dialog.locator('.askfile')).toHaveText('File: untitled.circ');
    await dialog.getByRole('button', { name: 'Save' }).click();
    await expect(dialog.locator('.askfile')).toHaveText('File: gates.circ');
    await dialog.getByRole('button', { name: 'Save' }).click();
    await quit;
    expect(list(work)).toEqual(['gates.circ', 'mine.circ']);   // saved, and no recovery file left
    expect(existsSync(`${gates}.hcs-recover`)).toBe(false);
  } finally {
    await gone(r, quit);
    rmSync(work, { recursive: true, force: true });
  }
});

test('Discard for each: the app quits, the recovery files go with the changes; a cancelled Save As keeps the app', async () => {
  const work = mkdtempSync(path.join(tmpdir(), 'hcs-work-'));
  const r = await launch(undefined, { env: { HCS_RECOVERY_IDLE_MS: '100' } });
  const { page, app } = r;
  const quit = app.waitForEvent('close', { timeout: 60_000 });
  try {
    await twoUnsaved(r, work);
    const dialog = page.locator('dialog.ask');
    // Save for the new file, its Save As dialog cancelled: nothing more is asked, the app stays.
    await app.evaluate(({ dialog: d }) => { d.showSaveDialog = (async () => ({ canceled: true, filePath: '' })) as typeof d.showSaveDialog; });
    await closeWindow(app);
    await dialog.getByRole('button', { name: 'Save' }).click();
    await expect(dialog).toHaveCount(0);
    expect(await windows(app)).toBe(1);
    await expect(page.locator('.filebar .ptab')).toHaveText(['untitled.circ•', 'gates.circ•']);
    // Discard, Discard: it quits.
    await closeWindow(app);
    await dialog.getByRole('button', { name: 'Discard' }).click();
    await expect(dialog.locator('.askfile')).toHaveText('File: gates.circ');
    await dialog.getByRole('button', { name: 'Discard' }).click();
    await quit;
    expect(list(work)).toEqual(['gates.circ']);   // as saved; its recovery file removed by the quit
  } finally {
    await gone(r, quit);
    rmSync(work, { recursive: true, force: true });
  }
});

test('nothing unsaved: the close button quits at once, no question', async () => {
  const r = await launch();
  const quit = r.app.waitForEvent('close', { timeout: 30_000 });
  const work = mkdtempSync(path.join(tmpdir(), 'hcs-work-'));
  try {
    await openFile(r, sample(work, GATES));
    await closeWindow(r.app);
    await quit;
  } finally {
    await gone(r, quit);
    rmSync(work, { recursive: true, force: true });
  }
});

test('the PC shutting down (Windows query-session-end) with unsaved files: held back, and the window asks', async () => {
  const work = mkdtempSync(path.join(tmpdir(), 'hcs-work-'));
  const r = await launch(undefined, { env: { HCS_RECOVERY_IDLE_MS: '100' } });
  try {
    await twoUnsaved(r, work);
    const held = await r.app.evaluate(({ BrowserWindow }) => {
      let prevented = false;
      BrowserWindow.getAllWindows()[0].emit('query-session-end', { preventDefault: () => { prevented = true; }, reasons: ['shutdown'] });
      return prevented;
    });
    expect(held).toBe(true);
    await expect(r.page.locator('dialog.ask h2')).toHaveText(TITLE);
    await r.page.locator('dialog.ask').getByRole('button', { name: 'Cancel' }).click();
    expect(await windows(r.app)).toBe(1);
  } finally {
    await r.close();
    rmSync(work, { recursive: true, force: true });
  }
});

test('closing a file\'s tab with unsaved changes: Save / Discard / Cancel -- Cancel keeps it, Save saves and closes, Discard closes', async () => {
  const work = mkdtempSync(path.join(tmpdir(), 'hcs-work-'));
  const r = await launch(undefined, { env: { HCS_RECOVERY_IDLE_MS: '100' } });
  const { page } = r;
  try {
    await twoUnsaved(r, work);
    const dialog = page.locator('dialog.ask');
    await page.getByRole('button', { name: /^Close gates\.circ/ }).click();
    await expect(dialog).toContainText('저장하지 않고 닫으면 이 파일의 변경은 사라집니다.');
    await expect(dialog.getByRole('button')).toHaveText(['Cancel', 'Discard', 'Save']);
    await dialog.getByRole('button', { name: 'Cancel' }).click();
    await expect(page.locator('.filebar .ptab')).toHaveText(['untitled.circ•', 'gates.circ•']);
    await page.getByRole('button', { name: /^Close gates\.circ/ }).click();
    await dialog.getByRole('button', { name: 'Save' }).click();
    await expect(page.locator('.filebar .ptab')).toHaveText(['untitled.circ•']);
    expect(list(work)).toEqual(['gates.circ']);   // saved: no recovery file
    await page.getByRole('button', { name: /^Close untitled\.circ/ }).click();
    await dialog.getByRole('button', { name: 'Discard' }).click();
    await expect(page.locator('.filebar .ptab')).toHaveCount(0);
  } finally {
    await r.close();
    rmSync(work, { recursive: true, force: true });
  }
});
