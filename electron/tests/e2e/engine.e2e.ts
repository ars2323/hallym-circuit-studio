/* The engine seen from the window: no engine -- the window's own dialog
   (no character anywhere: it is an error), a band, Try Again; the engine's
   notifications reach the status bar; a crash -- it starts again, the
   files come back (recovery.e2e.ts: with their unsaved edits), a dialog
   and a band say so, and the new one works. */

import { expect, test } from '@playwright/test';

import { DATAPATH, launch, newCircuit, openFile, sample, visibleCharacters } from './harness.ts';

test('no engine: the dialog says the engine could not start, with what was tried; no character; a band', async () => {
  const r = await launch(undefined, { env: { HCS_ENGINE_CMD: '', HCS_ENGINE_JAR: '/nowhere/hcs-engine.jar' } });
  const { page } = r;
  try {
    const dialog = page.locator('dialog.ask');
    await expect(dialog).toContainText('엔진을 시작하지 못했습니다');
    // The sentence names the file that was looked for, the line under it where.
    await expect(dialog.locator('.askdetail')).toHaveText('엔진 파일이 없습니다: hcs-engine.jar\n  /nowhere/hcs-engine.jar');
    await expect(dialog.getByRole('button')).toHaveText(['Close', 'Try Again']);
    expect(await visibleCharacters(page)).toBe(0);
    await dialog.getByRole('button', { name: 'Close' }).click();
    // The band stays while there is no engine: no character on screen all that time.
    expect(await visibleCharacters(page)).toBe(0);
    await expect(page.locator('.band')).toHaveText('엔진을 시작하지 못했습니다 · 회로를 만들거나 열 수 없습니다');
    await expect(page.locator('.band')).toHaveAttribute('data-kind', 'error');
    await expect(page.locator('.status .err')).toHaveText('엔진을 시작하지 못했습니다');
    // A way in: the dialog again, nothing opened.
    await page.getByRole('button', { name: /바로 시작/ }).click();
    await page.getByRole('button', { name: /새 회로/ }).click();
    await expect(dialog).toBeVisible();
    await dialog.getByRole('button', { name: 'Try Again' }).click();
    await expect(page.locator('dialog.ask')).toBeVisible(); // still no engine: asked again
    await page.keyboard.press('Escape');
    await expect(page.locator('.filebar .ptab')).toHaveCount(0);
  } finally {
    await r.close();
  }
});

test('an engine that exits at start: the same dialog, its last words in the detail', async () => {
  const r = await launch(undefined, { env: { FAKE_ENGINE_MODE: 'exit-at-start' } });
  try {
    const dialog = r.page.locator('dialog.ask');
    await expect(dialog).toContainText('엔진을 시작하지 못했습니다');
    await expect(dialog.locator('.askdetail')).toContainText('exiting at start');
  } finally {
    await r.close();
  }
});

test('the engine\'s notifications reach the window: 1 Cycle, Run, Reset in the status bar', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await newCircuit(r);
    await page.getByRole('button', { name: /1 Cycle/ }).click();
    await expect(page.locator('.status')).toContainText('Cycle 1');
    await page.keyboard.press('F10');
    await expect(page.locator('.status')).toContainText('Cycle 2');
    await page.getByRole('button', { name: /^Run/ }).click();
    await expect(page.locator('.status .run')).toHaveText('Running (1 Hz)');
    await expect(page.getByRole('button', { name: /^Stop/ })).toBeVisible();
    // A new speed while it runs goes to the engine at once.
    await page.getByRole('combobox', { name: 'Clock speed' }).selectOption('64');
    await expect(page.locator('.status .run')).toHaveText('Running (64 Hz)');
    await page.keyboard.press('F5');
    await expect(page.locator('.status .run')).toHaveCount(0);
    await page.getByRole('button', { name: /Reset/ }).click();
    await expect(page.locator('.status')).toContainText('Cycle 0');
  } finally {
    await r.close();
  }
});

test('an oscillation: the status bar says it in the window\'s words (not the engine\'s), until Reset', async () => {
  const r = await launch(undefined, { env: { FAKE_ENGINE_MODE: 'oscillate' } });
  const { page } = r;
  try {
    await newCircuit(r);
    await page.keyboard.press('F10');
    await expect(page.locator('.status')).toContainText('발진으로 시뮬레이션이 꺼졌습니다');
    await page.keyboard.press('F10');
    await expect(page.locator('.status .err').last()).toHaveText('1 Cycle: 회로가 발진해서 시뮬레이션이 꺼져 있습니다. 회로를 고친 뒤 Reset을 누르세요');
    expect(await page.locator('.status').innerText()).not.toMatch(/oscillat|simulation is off/);
    await page.getByRole('button', { name: /Reset/ }).click();
    await expect(page.locator('.status')).not.toContainText('꺼졌습니다');
    await expect(page.locator('.status')).toContainText('Cycle 0');
  } finally {
    await r.close();
  }
});

test('a crash: the engine starts again, the files come back in their tabs, a dialog and a band say so, and the new engine answers', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await page.getByTitle('New circuit (Ctrl+N)').click();
    await expect(page.locator('.filebar .ptab')).toHaveCount(2);
    const before = await r.app.evaluate(() => (globalThis as unknown as { __hcs: { engine: { pid: number } } }).__hcs.engine.pid);
    await r.app.evaluate(() => (globalThis as unknown as { __hcs: { engine: { kill(): void } } }).__hcs.engine.kill());
    await expect(page.locator('dialog.ask h2')).toHaveText('엔진이 멈췄다가 다시 시작했습니다');
    expect(await visibleCharacters(page)).toBe(0); // the dialog says something went wrong: no character
    await page.locator('dialog.ask').getByRole('button', { name: 'Close' }).click();
    await expect(page.locator('.band')).toHaveText('엔진이 멈춰서 다시 시작했습니다 · 파일 2개를 되살렸습니다 · 시뮬레이션은 Reset 상태입니다');
    expect(await visibleCharacters(page)).toBe(0); // nor while the band is up
    await expect(page.locator('.filebar .ptab')).toHaveText(['demo-datapath.circ', 'untitled.circ']);
    const after = await r.app.evaluate(() => (globalThis as unknown as { __hcs: { engine: { pid: number } } }).__hcs.engine.pid);
    expect(after).not.toBe(before);
    await page.locator('.filebar .ptab', { hasText: 'demo-datapath.circ' }).click();
    await expect(page.locator('.canvas h3')).toContainText('부품 35개');
    await openFile(r, sample(r.dir, 'tests/circ/gates.circ'));
    await expect(page.locator('.band')).toBeHidden();
    expect(await visibleCharacters(page)).toBe(1); // the Canvas's guide, the band gone
  } finally {
    await r.close();
  }
});
