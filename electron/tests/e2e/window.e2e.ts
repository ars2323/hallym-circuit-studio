/* The window: opened over the whole work area (maximised) at every start;
   the window's own dialogs are modal (a click outside does nothing, Esc is
   the safe answer, the keys stay inside), and the caption buttons' patch
   darkens with their backdrop; About with its two tabs and every notice. */

import { expect, test } from '@playwright/test';
import path from 'node:path';

import { answerOpen, launch, newCircuit, type Running } from './harness.ts';

const overlay = (r: Running) => r.app.evaluate(({ BrowserWindow }) => (BrowserWindow.getAllWindows()[0] as unknown as { overlayColor?: string }).overlayColor ?? '#ffffff');

test('opens over the whole work area, maximised where a window manager can', async () => {
  const r = await launch(null, { keepSize: true });
  try {
    const w = await r.app.evaluate(({ BrowserWindow, screen }) => {
      const b = BrowserWindow.getAllWindows()[0];
      return { maximized: b.isMaximized(), bounds: b.getBounds(), area: screen.getPrimaryDisplay().workArea };
    });
    if (!w.maximized) expect(w.bounds).toEqual(w.area);
    expect(w.bounds.width).toBeGreaterThanOrEqual(w.area.width);
  } finally {
    await r.close();
  }
});

test('security: context isolation, no Node in the page; the page may call only the listed engine methods, never a path', async () => {
  const r = await launch();
  const { page } = r;
  try {
    // No Node in the page, and none of the preload's own world (contextIsolation).
    expect(await page.evaluate(() => ['require', 'process', 'module', 'ipcRenderer', 'contextBridge']
      .map((n) => typeof (globalThis as Record<string, unknown>)[n]))).toEqual(['undefined', 'undefined', 'undefined', 'undefined', 'undefined']);
    expect(await page.evaluate(() => Object.isFrozen(window.app) || Object.getOwnPropertyDescriptor(window, 'app')?.writable === false)).toBe(true);
    expect(await page.evaluate(() => Object.keys(window.app).sort())).toEqual(['about', 'call', 'engineStatus', 'license', 'onEngineRecovered', 'onEngineStatus', 'onNotify',
      'openCredits', 'openFile', 'openStartupFile', 'retryEngine', 'saveFile', 'setOverlay', 'startupFile']);
    for (const method of ['file.open', 'file.save', 'engine.shutdown', 'engine.hello']) {
      const answer = await page.evaluate((m) => window.app.call(m as never, { path: '/etc/passwd' }).then(() => 'answered', (e: { message: string }) => e.message), method);
      expect(answer, method).toBe(`not a method the window may call: ${method}`);
    }
    expect(await page.evaluate(() => window.app.call('file.new').then((f) => typeof (f as { fileId: string }).fileId))).toBe('string');
  } finally {
    await r.close();
  }
});

test('the frameless bar: no system title bar; the bar drags the window, its buttons do not', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await newCircuit(r);
    expect(await page.locator('.titlebar').evaluate((e) => getComputedStyle(e).getPropertyValue('-webkit-app-region'))).toBe('drag');
    for (const b of await page.locator('.titlebar button, .titlebar select').all()) {
      expect(await b.evaluate((e) => getComputedStyle(e).getPropertyValue('-webkit-app-region'))).toBe('no-drag');
    }
    const frameless = await r.app.evaluate(({ BrowserWindow }) => {
      const w = BrowserWindow.getAllWindows()[0];
      return { size: w.getSize(), content: w.getContentSize() };
    });
    expect(frameless.content).toEqual(frameless.size); // no frame, no system title bar
  } finally {
    await r.close();
  }
});

test('a dialog: modal, a click outside (backdrop, toolbar, New) does nothing, Tab stays inside, Esc closes; the caption patch follows', async () => {
  const r = await launch();
  const { page } = r;
  try {
    expect(await overlay(r)).toBe('#ffffff');
    await answerOpen(r.app, path.join(r.dir, 'not-there.circ'));
    await page.keyboard.press('Control+o');
    const dialog = page.locator('dialog.ask');
    await expect(dialog).toBeVisible();
    expect(await dialog.evaluate((d) => d.matches(':modal'))).toBe(true);
    await expect.poll(() => overlay(r)).toBe('#a6b1c6'); // white under navy at 35 %
    expect(await dialog.evaluate((d) => getComputedStyle(d, '::backdrop').backgroundColor)).toBe('rgba(0, 32, 91, 0.35)');
    // Outside: the backdrop, the first screen's choice, the title bar's New -- nothing happens.
    const h = await page.evaluate(() => window.innerHeight);
    await page.mouse.click(10, h / 2);
    const choice = await page.locator('.action').first().boundingBox();
    await page.mouse.click(choice!.x + 20, choice!.y + 20);
    const newButton = await page.getByTitle('New circuit (Ctrl+N)').boundingBox();
    await page.mouse.click(newButton!.x + 10, newButton!.y + 10);
    await page.keyboard.press('Control+n');
    await page.waitForTimeout(300);
    await expect(dialog).toBeVisible();
    await expect(page.locator('.filebar .ptab')).toHaveCount(0);
    await expect(page.locator('.action').first()).toContainText('튜토리얼 보기');
    for (let i = 0; i < 3; i += 1) {
      await page.keyboard.press('Tab');
      expect(await page.evaluate(() => !!document.activeElement?.closest('dialog.ask')), `Tab ${i + 1}: focus inside`).toBe(true);
    }
    await page.keyboard.press('Escape');
    await expect(dialog).toHaveCount(0);
    await expect.poll(() => overlay(r)).toBe('#ffffff');
  } finally {
    await r.close();
  }
});

test('About: the version, Logisim 2.7.1 by Carl Burch, the marks\' owner, not official; every notice opens; never 한림', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await page.getByTitle('About').click();
    const about = page.locator('dialog.about');
    await expect(about).toBeVisible();
    await expect(about.getByRole('tab')).toHaveText(['About', 'Licenses']);
    const text = await about.innerText();
    for (const s of ['Hallym Circuit Studio', '2.0.0-alpha.0', 'Based on Logisim 2.7.1 by Carl Burch (GNU GPL, version 2 or later)',
      'Hallym MIPS Simulator', 'Hallym University의 소유', '상업적 사용을 금지합니다', 'Hallym University의 공식 제품이 아닙니다', 'fake-engine']) {
      expect(text).toContain(s);
    }
    await about.getByRole('tab', { name: 'Licenses' }).click();
    const items = about.locator('details');
    await expect(items).toHaveCount(9);
    for (let i = 0; i < 9; i += 1) {
      await items.nth(i).locator('summary').click();
      await expect(items.nth(i).locator('pre')).not.toBeEmpty();
    }
    await expect(items.nth(2).locator('pre')).toContainText('BSD 3-Clause License');
    await expect(items.nth(3).locator('pre')).toContainText('not an official product of Hallym University');
    expect(await about.innerText()).not.toContain('한림');
    await about.getByRole('button', { name: 'Close' }).click();
    await expect(about).toBeHidden();
    expect(await page.locator('body').innerText()).not.toContain('한림');
  } finally {
    await r.close();
  }
});
