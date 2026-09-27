/* The engine seen from the window: no engine -- the window's own dialog
   (no character: it is an error), a band, 다시 시도; the engine's
   notifications reach the status bar; a crash -- it starts again, the
   files of the old one are closed, a band says so, and the new one works. */

import { expect, test } from '@playwright/test';
import path from 'node:path';

import { DATAPATH, launch, newCircuit, openFile, sample } from './harness.ts';

test('no engine: the dialog says the engine could not start, with what was tried; no character; a band', async () => {
  const r = await launch(undefined, { env: { HCS_ENGINE_CMD: '', HCS_ENGINE_JAR: '/nowhere/engine.jar' } });
  const { page } = r;
  try {
    const dialog = page.locator('dialog.ask');
    await expect(dialog).toContainText('엔진을 시작하지 못했습니다');
    await expect(dialog.locator('.askdetail')).toContainText('/nowhere/engine.jar');
    await expect(dialog.locator('img.char')).toHaveCount(0);
    await expect(dialog.getByRole('button')).toHaveText(['닫기', '다시 시도']);
    await dialog.getByRole('button', { name: '닫기' }).click();
    await expect(page.locator('.band')).toHaveText('엔진을 시작하지 못했습니다 · 회로를 만들거나 열 수 없습니다');
    await expect(page.locator('.band')).toHaveAttribute('data-kind', 'error');
    await expect(page.locator('.status .err')).toHaveText('엔진을 시작하지 못했습니다');
    // A way in: the dialog again, nothing opened.
    await page.getByRole('button', { name: /바로 시작/ }).click();
    await page.getByRole('button', { name: /새 회로/ }).click();
    await expect(dialog).toBeVisible();
    await dialog.getByRole('button', { name: '다시 시도' }).click();
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
    await expect(page.locator('.status .run')).toHaveText('실행 중 (1 Hz)');
    await expect(page.getByRole('button', { name: /^Stop/ })).toBeVisible();
    // A new speed while it runs goes to the engine at once.
    await page.getByRole('combobox', { name: 'Clock speed' }).selectOption('64');
    await expect(page.locator('.status .run')).toHaveText('실행 중 (64 Hz)');
    await page.keyboard.press('F5');
    await expect(page.locator('.status .run')).toHaveCount(0);
    await page.getByRole('button', { name: /Reset/ }).click();
    await expect(page.locator('.status')).toContainText('Cycle 0');
  } finally {
    await r.close();
  }
});

test('the engine\'s warnings reach the status bar: an oscillation turns the simulation off until Reset', async () => {
  const r = await launch(undefined, { env: { FAKE_ENGINE_MODE: 'oscillate' } });
  const { page } = r;
  try {
    await newCircuit(r);
    await page.keyboard.press('F10');
    await expect(page.locator('.status')).toContainText('회로가 발진해서 시뮬레이션을 껐습니다');
    await expect(page.locator('.status')).toContainText('발진으로 시뮬레이션이 꺼졌습니다');
    await page.keyboard.press('F10');
    await expect(page.locator('.status .err').last()).toHaveText('1 Cycle — simulation off');
    await page.getByRole('button', { name: /Reset/ }).click();
    await expect(page.locator('.status')).not.toContainText('꺼졌습니다');
    await expect(page.locator('.status')).toContainText('Cycle 0');
  } finally {
    await r.close();
  }
});

test('a crash: the engine starts again, the old engine\'s files close, a band says so, and the new one opens files', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await page.getByTitle('New circuit (Ctrl+N)').click();
    await expect(page.locator('.filebar .ptab')).toHaveCount(2);
    const before = await r.app.evaluate(() => (globalThis as unknown as { __hcs: { engine: { pid: number } } }).__hcs.engine.pid);
    await r.app.evaluate(() => (globalThis as unknown as { __hcs: { engine: { kill(): void } } }).__hcs.engine.kill());
    await expect(page.locator('.band')).toHaveText('엔진이 멈춰서 다시 시작했습니다 · 열려 있던 파일 2개를 닫았습니다');
    await expect(page.locator('.wcard')).toBeVisible();
    await expect(page.locator('.filebar .ptab')).toHaveCount(0);
    const after = await r.app.evaluate(() => (globalThis as unknown as { __hcs: { engine: { pid: number } } }).__hcs.engine.pid);
    expect(after).not.toBe(before);
    await openFile(r, path.join(r.dir, 'demo-datapath.circ'));
    await expect(page.locator('.band')).toBeHidden();
    await expect(page.locator('.canvas h3')).toContainText('부품 35개');
  } finally {
    await r.close();
  }
});
