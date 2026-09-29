/* The window: opened over the whole work area (maximised) at every start;
   the window's own dialogs are modal (a click outside does nothing, Esc is
   the safe answer, the keys stay inside), and the caption buttons' patch
   darkens with their backdrop; About with its two tabs and every notice. */

import { expect, test } from '@playwright/test';
import { readFileSync } from 'node:fs';
import path from 'node:path';

import { answerOpen, launch, newCircuit, openAbout, type Running } from './harness.ts';

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
    expect(await page.evaluate(() => Object.keys(window.app).sort())).toEqual([...['about', 'attach', 'call', 'closeCancelled', 'detach', 'editOriginal', 'engineStatus', 'importApply', 'importChoose', 'importPlan', 'leave', 'license', 'loadLibrary', 'loadProgram', 'memoryImage', 'onAdopt', 'onCloseRequest', 'onEngineRecovered', 'onEngineStatus', 'onFilesChanged', 'onLeave', 'onNotify', 'openCredits', 'openDropped', 'openFile', 'openFilesAll', 'openRecovery', 'openStartupFile', 'reportDirty', 'retryEngine', 'saveFile', 'setOverlay', 'startupFile', 'useOpenFile', 'windowClosed', 'windowRole'], 'examples', 'maximize', 'minimize', 'openExample', 'openRecent', 'recentFiles'].sort());
    for (const method of ['file.open', 'file.save', 'engine.shutdown', 'engine.hello', 'mips.load', 'file.recoverWrite', 'edit.importCircuits', 'edit.loadLibrary', 'file.peek', 'model.importPlan', 'file.originOf', 'edit.reloadLibrary', 'mem.loadImage', 'mem.saveImage']) {
      const answer = await page.evaluate((m) => window.app.call(m as never, { path: '/etc/passwd' }).then(() => 'answered', (e: { message: string }) => e.message), method);
      expect(answer, method).toBe(`not a method the window may call: ${method}`);
    }
    expect(await page.evaluate(() => window.app.call('file.new').then((f) => typeof (f as { fileId: string }).fileId))).toBe('string');
    // A recovery file's answer names only the question the main process asked (N-19): never a path.
    expect(await page.evaluate(() => window.app.openRecovery('/etc/passwd', 'recover'))).toBe(null);
    // An example and a recent file by the main process's names only (D-158): never a path.
    expect(await page.evaluate(() => window.app.openExample('/etc/passwd'))).toBe(null);
    expect(await page.evaluate(() => window.app.openExample('../../../../etc/passwd'))).toBe(null);
    expect(await page.evaluate(() => window.app.openRecent('/etc/passwd'))).toBe(null);
    expect(await page.evaluate(() => window.app.examples())).toEqual(['demo-datapath.circ', 'console-demo.circ', 'stack-demo.circ'].map((n) => ({ id: n, name: n })));
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
    // Tab and Shift+Tab go round inside (D-164): the focus never leaves the dialog, not even for a moment (Chromium
    // let Tab go past the last control: the window lost the focus and got it back a little later).
    await page.evaluate(() => {
      const w = window as unknown as { leftDialog: string[] };
      w.leftDialog = [];
      document.addEventListener('focusout', (e) => {
        const to = e.relatedTarget as Element | null;
        if (!to?.closest('dialog.ask')) w.leftDialog.push(`focusout to ${to ? to.tagName : 'nothing'}`);
      }, true);
      window.addEventListener('blur', () => w.leftDialog.push('window blur'));
    });
    const inside = () => page.evaluate(() => !!document.activeElement?.closest('dialog.ask'));
    for (const [i, key] of ['Tab', 'Tab', 'Tab', 'Shift+Tab', 'Shift+Tab'].entries()) {
      await page.keyboard.press(key);
      await expect.poll(inside, { message: `${key} ${i + 1}: focus inside` }).toBe(true);
    }
    await expect(dialog.locator('button.primary')).toBeFocused();
    expect(await page.evaluate(() => (window as unknown as { leftDialog: string[] }).leftDialog), 'the focus never left the dialog').toEqual([]);
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
    await openAbout(page);
    const about = page.locator('dialog.about');
    await expect(about).toBeVisible();
    await expect(about.getByRole('tab')).toHaveText(['About', 'Licenses']);
    const text = await about.innerText();
    const version = (JSON.parse(readFileSync(path.join(import.meta.dirname, '../../package.json'), 'utf8')) as { version: string }).version;
    for (const s of ['Hallym Circuit Studio', version, 'Based on Logisim 2.7.1 by Carl Burch (GNU GPL, version 2 or later)',
      'Hallym MIPS Simulator', 'Hallym University 소유', '상업적 사용을 금지합니다', 'Hallym University 공식 제품이 아닙니다', '시작 화면의 영상', '@HALLYMNEWS', '로고와 캐릭터는 원형 그대로 씁니다', '홍보 영상의 첫 장면 2.5초를 소리 없이 느리게 자른 것이고, 흐리게 하고 남색 층을 덮어 보입니다', 'fake-engine']) {
      expect(text).toContain(s);
    }
    await about.getByRole('tab', { name: 'Licenses' }).click();
    const items = about.locator('details');
    await expect(items).toHaveCount(10);
    for (let i = 0; i < 10; i += 1) {
      await items.nth(i).locator('summary').click();
      await expect(items.nth(i).locator('pre')).not.toBeEmpty();
    }
    await expect(items.nth(2).locator('pre')).toContainText('BSD 3-Clause License');
    await expect(items.nth(3).locator('pre')).toContainText('not an official product of Hallym University');
    await expect(items.nth(4).locator('summary')).toContainText('OpenJDK runtime (Eclipse Temurin 21.0.12)');
    await expect(items.nth(4).locator('pre')).toContainText('"CLASSPATH" EXCEPTION TO THE GPL');
    // 한림 only inside the video's own title, quoted in NOTICE and the marks' notes as Hallym MIPS NOTICE 8 has it (D-155).
    const TITLE = '"[Official Video] 한림대학교 홍보영상｜The New Hallym 대학의 내일을 열다"';
    const all = (await about.innerText()).replace(/\s+/g, ' ');
    expect(all).toContain(TITLE);
    expect(all.split(TITLE).join('')).not.toContain('한림');
    await about.getByRole('button', { name: 'Close' }).click();
    await expect(about).toBeHidden();
    expect(await page.locator('body').innerText()).not.toContain('한림');
  } finally {
    await r.close();
  }
});
