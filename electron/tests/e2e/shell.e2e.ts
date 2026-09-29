/* The window's shell (N-17, D-158): the » rule on the toolbar and the
   status bar (every command and every fact on the bar or in its » list,
   none just hidden), the tight window's Canvas / Panels, the folds for the
   room (the Canvas keeps half the height), Preferences (this run only) and
   the keys the student can change, the menu (File › … Help ›, Examples read-
   only, Open Recent), the caption patch under the tutorial's shade, the
   Changed chip.  The fake engine (tests/fake-engine). */

import { expect, test, type Page } from '@playwright/test';
import path from 'node:path';

import { overlayColor } from '../../src/renderer/shared/overlay.ts';
import { answerSave, DATAPATH, launch, openFile, recordCalls, resize, sample, sentCalls, type Running } from './harness.ts';
import { click, partMiddle } from './overlay-helpers.ts';

const HALF = { width: 960, height: 1032 };
const TIGHT = { width: 683, height: 700 };      // half a 1366 screen

async function withDatapath(size: { width: number; height: number } = { width: 1920, height: 1032 }, switches: string[] = []): Promise<Running> {
  const r = await launch(size, { switches });
  await openFile(r, sample(r.dir, DATAPATH));
  await r.page.locator('.canvas-view canvas').waitFor();
  return r;
}
const units = (page: Page, sel = '') => page.locator(`.toolbar [data-unit]${sel}`).evaluateAll((els) => els.map((e) => (e as HTMLElement).dataset.unit ?? ''));
// Every fact's words, in the bar and in its » list (the status bar's DOM keeps them all).
const facts = (page: Page) => page.evaluate(() => {
  const bar = [...document.querySelectorAll('footer.status > :not(.grow):not(.moreb):not(.statusmore)')].map((e) => e.textContent ?? '');
  const more = [...document.querySelectorAll('.statusmore .statusrow')].map((e) => e.textContent ?? '');
  return { bar, more };
});
const fitsWindow = (page: Page) => page.evaluate(() => {
  const s = document.querySelector('footer.status') as HTMLElement;
  const t = document.querySelector('.titlebar') as HTMLElement;
  const end = t.getBoundingClientRect().right - parseFloat(getComputedStyle(t).paddingRight);
  return { status: s.scrollWidth <= s.clientWidth + 1, title: (t.querySelector('.tools') as HTMLElement).getBoundingClientRect().right <= end + 0.5 };
});

test('the » rule on the toolbar: at half a screen and at 683 px every command is on the bar or on the » menu, with its key and state; the menu runs them', async () => {
  const r = await withDatapath(HALF);
  const { page } = r;
  try {
    await expect.poll(() => fitsWindow(page)).toEqual({ status: true, title: true });
    const all = await units(page);
    expect(all).toHaveLength(17);
    const over = await units(page, '[data-over]');
    expect(over.length).toBeGreaterThan(0);
    // The least needed go first, from the right: Signal Flow before the tools, the clock's speed before Run.
    expect(over).toContain('Signal Flow');
    expect(over).not.toContain('Run');
    expect(over).not.toContain('1 Cycle');
    await expect(page.locator('.toolbar .more')).toBeVisible();
    for (const u of await page.locator('.toolbar [data-unit]:not([data-over])').all()) await expect(u).toBeVisible();
    for (const u of await page.locator('.toolbar [data-unit][data-over]').all()) await expect(u).toBeHidden();
    // The menu: the same commands in the bar's order, a tool ticked as its button, the speed a submenu.
    await page.locator('.toolbar .more').click();
    const menu = page.locator('.ovmenu.barmenu');
    await expect(menu.locator('> button .label')).toHaveText(over);
    await menu.getByRole('menuitem', { name: /Clock Speed/ }).click();
    await page.locator('.ovmenu').last().getByRole('menuitemradio', { name: '64 Hz' }).click();
    await expect(page.locator('select[aria-label="Clock speed"]')).toHaveValue('64');
    await page.locator('.toolbar .more').click();
    await page.locator('.ovmenu.barmenu').getByRole('menuitem', { name: /N Cycles/ }).click();
    await expect(page.locator('dialog.cycles')).toBeVisible();
    await page.keyboard.press('Escape');
    // A tool from the menu is the tool in hand, and the menu says so next time.
    if (over.includes('Wire')) {
      await page.locator('.toolbar .more').click();
      await page.locator('.ovmenu.barmenu').getByRole('menuitemradio', { name: 'Wire' }).click();
      await expect(page.locator('.toolbar [data-unit="Wire"]')).toHaveAttribute('aria-checked', 'true');
      await page.locator('.toolbar .more').click();
      await expect(page.locator('.ovmenu.barmenu').getByRole('menuitemradio', { name: 'Wire' })).toHaveAttribute('aria-checked', 'true');
      await page.keyboard.press('Escape');
    }
    // 683 px (half a 1366 screen): Run, 1 Cycle, Reset and Load Program still on the bar (the program's name went first),
    // Save on the menu with its key.
    await resize(r, TIGHT);
    await expect.poll(() => units(page, '[data-over]')).toContain('Save');
    await expect.poll(() => fitsWindow(page)).toEqual({ status: true, title: true });
    for (const name of ['Run', '1 Cycle', 'Reset']) await expect(page.locator(`.toolbar [data-unit="${name}"]`)).toBeVisible();
    await expect(page.locator('.titlebar .appname')).toBeHidden();
    await expect(page.locator('.titlebar .logo')).toBeVisible();
    await page.locator('.toolbar .more').click();
    await expect(page.locator('.ovmenu.barmenu').getByRole('menuitem', { name: /^Save/ }).locator('.keys')).toHaveText('Ctrl+S');
    await page.keyboard.press('Escape');
    // Wide again: everything back on the bar, no » button.
    await resize(r, { width: 1920, height: 1032 });
    await expect.poll(() => units(page, '[data-over]')).toEqual([]);
    await expect(page.locator('.toolbar .more')).toBeHidden();
  } finally {
    await r.close();
  }
});

test('the » rule on the status bar: at 683 px what does not fit is in its » list in the bar\'s order; no fact is left out; a fact there still works', async () => {
  const r = await withDatapath();
  const { page } = r;
  try {
    await page.keyboard.press('F10');
    await expect(page.locator('.status')).toContainText('Cycle 1');
    const wide = await facts(page);
    expect(wide.more).toEqual([]);
    await expect(page.locator('.status .moreb')).toBeHidden();
    await resize(r, TIGHT);
    await expect(page.locator('.status .moreb')).toBeVisible();
    const tight = await facts(page);
    expect(tight.more.length).toBeGreaterThan(0);
    // The same facts, all of them: on the bar or in the list (the zoom's words change with the window).
    const words = (f: { bar: string[]; more: string[] }) => [...f.bar, ...f.more].filter((t) => !/%$/.test(t)).sort();
    expect(words(tight)).toEqual(words(wide));
    await expect(page.locator('.status .moreb')).toHaveText(`» ${tight.more.length}`);
    await expect.poll(() => fitsWindow(page)).toEqual({ status: true, title: true });
    // The list opens above the bar, inside the window; a button in it works (the Messages count opens Messages).
    await page.locator('.status .moreb').click();
    const list = page.locator('.statusmore');
    await expect(list).toBeVisible();
    const box = (await list.boundingBox())!;
    expect(box.x).toBeGreaterThanOrEqual(0);
    expect(box.x + box.width).toBeLessThanOrEqual(TIGHT.width + 0.5);
    expect(box.y + box.height).toBeLessThanOrEqual((await page.locator('.status').boundingBox())!.y + 0.5);
    await page.keyboard.press('Escape');
    await expect(list).toBeHidden();
  } finally {
    await r.close();
  }
});

test('a tight window (683 px): Canvas / Panels one at a time in the title bar; a part picked, a circuit, a tunnel bring the Canvas back', async () => {
  const r = await withDatapath(TIGHT);
  const { page } = r;
  try {
    await expect(page.locator('.shell')).toHaveClass(/tight/);
    const views = page.locator('.titlebar .viewswitch');
    await expect(views).toBeVisible();
    await expect(views.getByRole('tab')).toHaveText(['Canvas', 'Panels']);
    await expect(page.locator('.canvaspanel')).toBeVisible();
    await expect(page.locator('.leftcol')).toBeHidden();
    // Panels: the left column the whole width, Attributes among its tabs
    await views.getByRole('tab', { name: 'Panels' }).click();
    await expect(page.locator('.leftcol')).toBeVisible();
    await expect(page.locator('.center')).toBeHidden();
    expect((await page.locator('.leftcol').boundingBox())!.width).toBeGreaterThan(TIGHT.width - 40);
    await expect(page.locator('.upper').getByRole('tab', { name: 'Attributes' })).toBeVisible();
    // A circuit from Circuits: the Canvas, that circuit on show
    await page.locator('.upper').getByRole('tab', { name: 'Circuits' }).click();
    await page.locator('.upper .pbody:visible .list > li', { hasText: 'alu' }).getByRole('button').click();
    await expect(page.locator('.canvaspanel')).toBeVisible();
    await expect(page.locator('.circuitbar .ptab.on')).toHaveText('alu');
    // A part from Components: held on the Canvas
    await views.getByRole('tab', { name: 'Panels' }).click();
    await page.locator('.upper').getByRole('tab', { name: 'Components' }).click();
    await page.locator('.upper .pbody:visible .libgroup summary', { hasText: 'Gates' }).click();
    await page.locator('.upper .pbody:visible .list li button', { hasText: /^AND Gate$/ }).first().click();
    await expect(page.locator('.canvaspanel')).toBeVisible();
    await expect(views.getByRole('tab', { name: 'Canvas' })).toHaveAttribute('aria-selected', 'true');
    // Wider: both sides again, no switch
    await resize(r, { width: 1280, height: 800 });
    await expect(views).toBeHidden();
    await expect(page.locator('.leftcol')).toBeVisible();
    await expect(page.locator('.canvaspanel')).toBeVisible();
  } finally {
    await r.close();
  }
});

test('the Canvas keeps half the height (v1 Y-01): a low window folds the bottom and the lower left panels to their heads; a tab opens one until the size changes', async () => {
  // A window lower than the app lets one be (min 480): what a low window with its bands on looks like.
  const r = await withDatapath({ width: 1280, height: 600 });
  const { page } = r;
  await r.app.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows()[0].setMinimumSize(640, 200));
  await resize(r, { width: 1280, height: 280 });
  try {
    const bottom = page.locator('section.bottom');
    const lower = page.locator('section.lower');
    await expect(bottom).toHaveClass(/collapsed/);
    await expect(lower).toHaveClass(/collapsed/);
    await expect(bottom.getByRole('button', { name: 'Expand' })).toBeVisible();
    const canvasH = async () => (await page.locator('.canvaspanel').boundingBox())!.height;
    const workH = async () => (await page.locator('main.work').boundingBox())!.height;
    expect(await canvasH()).toBeGreaterThan((await workH()) / 2);
    // A tab opens it (the student's wish: the room is the student's to give now)
    await bottom.getByRole('tab', { name: 'Cycle View' }).click();
    await expect(bottom).not.toHaveClass(/collapsed/);
    await expect(bottom.locator('.pbody:visible')).toHaveCount(1);
    await lower.getByRole('tab', { name: 'Minimap' }).click();
    await expect(lower).not.toHaveClass(/collapsed/);
    // The window's size changes: folded again for the room
    await resize(r, { width: 1280, height: 270 });
    await expect(bottom).toHaveClass(/collapsed/);
    await expect(lower).toHaveClass(/collapsed/);
    // Room again: both open by themselves, the Canvas still at least half
    await resize(r, { width: 1280, height: 900 });
    await expect(bottom).not.toHaveClass(/collapsed/);
    await expect(lower).not.toHaveClass(/collapsed/);
    expect(await canvasH()).toBeGreaterThan((await workH()) / 2);
    // The student's Collapse stays the student's, whatever the room
    await bottom.getByRole('button', { name: 'Collapse' }).click();
    await expect(bottom).toHaveClass(/collapsed/);
    await resize(r, { width: 1280, height: 1000 });
    await expect(bottom).toHaveClass(/collapsed/);
    await bottom.getByRole('button', { name: 'Expand' }).click();
    await expect(bottom).not.toHaveClass(/collapsed/);
  } finally {
    await r.close();
  }
});

test('Preferences: this run only, said at the top; the toolbar\'s speed, the Wire Colors panel and the overlays show the same values; Reset Panel Sizes; About · Licenses', async () => {
  const r = await withDatapath();
  const { page } = r;
  try {
    await page.getByTitle('Preferences').click();
    const prefs = page.locator('dialog.prefs');
    await expect(prefs).toBeVisible();
    await expect(prefs.locator('.prefnote')).toHaveText('이번 실행에만 적용됩니다. 다음에 켜면 모두 기본값으로 돌아옵니다.');
    await expect(prefs.getByRole('tab')).toHaveText(['General', 'Keyboard']);
    await expect(prefs.locator('img.char')).toHaveCount(0);
    // The clock's speed: the toolbar's select follows
    await prefs.getByRole('combobox', { name: 'Clock Speed' }).selectOption({ label: '16 Hz' });
    await expect(page.locator('select[aria-label="Clock speed"]')).toHaveValue('16');
    // Show Bus Widths: the Wire Colors panel's box follows; Colors: Groups, the status bar says so
    await prefs.getByRole('checkbox', { name: 'Show Bus Widths' }).uncheck();
    await prefs.getByRole('checkbox', { name: 'Show Grid' }).uncheck();
    await prefs.getByRole('radio', { name: 'Groups' }).click();
    await prefs.getByRole('combobox', { name: 'Bus Values' }).selectOption({ label: 'Dec' });
    await prefs.getByRole('checkbox', { name: 'Signal Flow on Click' }).uncheck();
    await prefs.getByRole('radio', { name: 'Fast' }).click();
    await page.keyboard.press('Escape');
    await expect(prefs).toBeHidden();
    await expect(page.locator('.status .colorsb')).toHaveText('Colors: Groups');
    const ov = await page.evaluate(() => {
      const o = (window as unknown as { __hcsOverlays: { settings: { onClick: boolean; speed: string }; bus: { mode: string }; bands: { showGroups: boolean } } }).__hcsOverlays;
      return { onClick: o.settings.onClick, speed: o.settings.speed, bus: o.bus.mode, groups: o.bands.showGroups };
    });
    expect(ov).toEqual({ onClick: false, speed: 'fast', bus: 'dec', groups: true });
    await page.locator('.legend-button').click();
    await expect(page.locator('.legend-panel > label.legend-opt:not(.ovrow) input')).not.toBeChecked();
    await page.keyboard.press('Escape');
    // Show Grid: the Canvas without its dots, the zoom menu's item unticked
    expect(await page.evaluate(() => (window as unknown as { __hcsCanvas: { showGrid: boolean } }).__hcsCanvas.showGrid)).toBe(false);
    await page.locator('.zoom-button').click();
    await expect(page.locator('.zoom-menu').getByRole('menuitemcheckbox', { name: 'Show Grid' })).toHaveAttribute('aria-checked', 'false');
    await page.keyboard.press('Escape');
    // The status bar's Colors switches back
    await page.locator('.status .colorsb').click();
    await expect(page.locator('.status .colorsb')).toHaveText('Colors: Values');
    // Reset Panel Sizes: a dragged splitter back to its default, the Collapse undone
    const upper = page.locator('.upper');
    const w0 = (await upper.boundingBox())!.width;
    const split = (await page.locator('.shell > .splitter').first().boundingBox())!;
    await page.mouse.move(split.x + 4, split.y + 200);
    await page.mouse.down();
    await page.mouse.move(split.x + 84, split.y + 200, { steps: 4 });
    await page.mouse.up();
    await expect.poll(async () => Math.round((await upper.boundingBox())!.width - w0)).toBe(80);
    await page.locator('section.bottom').getByRole('button', { name: 'Collapse' }).click();
    await page.getByTitle('Preferences').click();
    await prefs.getByRole('button', { name: 'Reset Panel Sizes' }).click();
    await expect.poll(async () => (await upper.boundingBox())!.width).toBe(w0);
    await expect(page.locator('section.bottom')).not.toHaveClass(/collapsed/);
    // About · Licenses: Hallym MIPS's way to About
    await prefs.getByRole('button', { name: 'About · Licenses' }).click();
    await expect(prefs).toBeHidden();
    await expect(page.locator('dialog.about')).toBeVisible();
  } finally {
    await r.close();
  }
});

test('the keys (v1 E-09, I-43, I-183): every key in Preferences › Keyboard; Find given Ctrl+G works and Ctrl+F no longer does; a fixed key and another command\'s refused with the reason; Reset; ? opens the table', async () => {
  const r = await withDatapath();
  const { page } = r;
  try {
    await page.locator('.canvas-view canvas').hover();
    await page.keyboard.press('Shift+Slash');   // ?
    const prefs = page.locator('dialog.prefs');
    await expect(prefs.getByRole('tab', { name: 'Keyboard' })).toHaveAttribute('aria-selected', 'true');
    const row = (id: string) => prefs.locator(`tr[data-command="${id}"]`);
    await expect(prefs.locator('tr[data-command]')).toHaveCount(14);
    await expect(row('find').locator('kbd')).toHaveText('Ctrl+F');
    await expect(row('redo').locator('kbd')).toHaveText('Ctrl+Y');
    await expect(prefs.locator('tr.fixed', { hasText: 'Save As…' }).locator('kbd')).toHaveText('Ctrl+Shift+S');
    await expect(prefs.locator('tr.fixed', { hasText: 'Tool' }).locator('kbd')).toHaveText('Ctrl+2 … Ctrl+9');
    // Change… then a fixed key: refused with the reason, still waiting; another command's: refused
    await row('find').getByRole('button', { name: 'Change Find' }).click();
    await expect(row('find').locator('.kwait')).toBeVisible();
    await prefs.locator('h2').click();   // the focus off the button: the key is still the new key's
    await page.keyboard.press('Control+s');
    await expect(row('find').locator('.hint.err')).toHaveText('Ctrl+S: 바꿀 수 없는 키입니다(Save)');
    await page.keyboard.press('Control+k');
    await expect(row('find').locator('.hint.err')).toHaveText('Ctrl+K: 이미 쓰는 키입니다(Search)');
    await page.keyboard.press('Shift');   // a modifier alone is not a key
    await expect(row('find').locator('.kwait')).toBeVisible();
    await page.keyboard.press('Control+g');
    await expect(row('find').locator('kbd')).toHaveText('Ctrl+G');
    await expect(row('find')).toHaveClass(/changed/);
    await expect(prefs).toBeVisible();   // Esc while waiting cancels the wait, not the dialog
    await row('redo').getByRole('button', { name: 'Change Redo' }).click();
    await page.keyboard.press('Escape');
    await expect(prefs).toBeVisible();
    await expect(row('redo').locator('kbd')).toHaveText('Ctrl+Y');
    await page.keyboard.press('Escape');
    await expect(prefs).toBeHidden();
    // Ctrl+G opens Find; Ctrl+F does nothing now; the menu shows the new key
    await page.locator('.canvas-view canvas').hover();
    await page.keyboard.press('Control+f');
    await page.waitForTimeout(200);
    await expect(page.locator('.findwin')).toBeHidden();
    await page.keyboard.press('Control+g');
    await expect(page.locator('.findwin')).toBeVisible();
    await page.locator('.findwin .findclose').click();
    await page.getByTitle('Menu').click();
    await page.locator('.ovmenu').getByRole('menuitem', { name: /^Edit/ }).click();
    await expect(page.locator('.ovmenu').last().getByRole('menuitem', { name: /^Find…/ }).locator('.keys')).toHaveText('Ctrl+G');
    await page.keyboard.press('Escape');
    // A plain key: Rotate given T -- T turns the chosen part, R no longer does
    await recordCalls(r.app);
    await page.getByTitle('Preferences').click();
    await prefs.getByRole('tab', { name: 'Keyboard' }).click();
    await row('rotate').getByRole('button', { name: 'Change Rotate' }).click();
    await page.keyboard.press('t');
    await expect(row('rotate').locator('kbd')).toHaveText('T');
    await page.keyboard.press('Escape');
    const pc = await partMiddle(page, 'Register', 'PC');
    await click(page, pc.at);
    await expect.poll(() => page.evaluate(() => (window as unknown as { __hcsCanvas: { selection(): string[] } }).__hcsCanvas.selection().length)).toBe(1);
    await page.keyboard.press('r');
    await page.waitForTimeout(200);
    expect(await sentCalls(r.app, 'edit.rotate')).toHaveLength(0);
    await page.keyboard.press('t');
    await expect.poll(async () => (await sentCalls(r.app, 'edit.rotate')).length).toBe(1);
    // Reset All: the defaults again
    await page.getByTitle('Preferences').click();
    await prefs.getByRole('tab', { name: 'Keyboard' }).click();
    await prefs.getByRole('button', { name: 'Reset All' }).click();
    await expect(row('find').locator('kbd')).toHaveText('Ctrl+F');
    await expect(row('rotate').locator('kbd')).toHaveText('R');
    await expect(prefs.getByRole('button', { name: 'Reset All' })).toBeDisabled();
  } finally {
    await r.close();
  }
});

test('the menu: File › Edit › Project › Simulate › Window › Help with their keys; Project runs the Circuits panel\'s commands; Edit › Tool › Menu Tool (I-84); Help › Examples opens one read-only and Save asks where; Open Recent has this run\'s files; Window lists them', async () => {
  const r = await withDatapath();
  const { page } = r;
  try {
    await page.getByTitle('Menu').click();
    const top = page.locator('.ovmenu.barmenu');
    await expect(top.locator('> button .label')).toHaveText(['File', 'Edit', 'Project', 'Simulate', 'Window', 'Help']);
    const sub = async (name: string) => {
      await page.getByTitle('Menu').click();
      await page.locator('.ovmenu.barmenu').getByRole('menuitem', { name: new RegExp(`^${name}`) }).click();
      return page.locator('.ovmenu').last();
    };
    await page.keyboard.press('Escape');
    let m = await sub('File');
    await expect(m.getByRole('menuitem', { name: /^Save As…/ }).locator('.keys')).toHaveText('Ctrl+Shift+S');
    await expect(m.getByRole('menuitem', { name: /^Close/ }).locator('.keys')).toHaveText('Ctrl+W');
    await page.keyboard.press('Escape');
    // Project: the circuit on show (the main one); Add Circuit… is the Circuits panel's dialog
    m = await sub('Project');
    await expect(m.getByRole('menuitem', { name: /^Set As Main Circuit/ })).toBeDisabled();
    await expect(m.getByRole('menuitem', { name: /^Edit Circuit Layout/ })).toBeEnabled();
    await m.getByRole('menuitem', { name: /^Add Circuit…/ }).click();
    await expect(page.getByRole('dialog', { name: /Add Circuit/ })).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(page.getByRole('dialog', { name: /Add Circuit/ })).toBeHidden();
    // Edit › Tool › Menu Tool: a press opens the menu of what is under it, the selection kept (the original's MenuTool)
    m = await sub('Edit');
    await m.getByRole('menuitem', { name: /^Tool/ }).click();
    await page.locator('.ovmenu').last().getByRole('menuitemradio', { name: 'Menu Tool' }).click();
    const pcPart = await partMiddle(page, 'Register', 'PC');
    await click(page, pcPart.at);
    await expect(page.locator('.ovmenu').first()).toContainText('PC');
    expect(await page.evaluate(() => (window as unknown as { __hcsCanvas: { selection(): string[] } }).__hcsCanvas.selection())).toEqual([]);
    await page.keyboard.press('Escape');
    m = await sub('Edit');
    await m.getByRole('menuitem', { name: /^Tool/ }).click();
    await expect(page.locator('.ovmenu').last().getByRole('menuitemradio', { name: 'Menu Tool' })).toHaveAttribute('aria-checked', 'true');
    await page.locator('.ovmenu').last().getByRole('menuitemradio', { name: 'Edit Tool' }).click();
    m = await sub('Simulate');
    await expect(m.getByRole('menuitemcheckbox', { name: /Simulation Enabled/ })).toHaveAttribute('aria-checked', 'true');
    await expect(m.getByRole('menuitem', { name: /^Step Simulation/ })).toBeDisabled();   // only while off
    await page.keyboard.press('Escape');
    // Help › Examples › console-demo.circ: read-only, the status bar says so; Save asks where
    m = await sub('Help');
    await m.getByRole('menuitem', { name: /^Examples/ }).click();
    await page.locator('.ovmenu').last().getByRole('menuitem', { name: 'console-demo.circ' }).click();
    await expect(page.locator('.filebar .ptab', { hasText: 'console-demo.circ' })).toBeVisible();
    await expect(page.locator('.status')).toContainText('Read-only');
    await expect(page.locator('.status')).toContainText('예제를 읽기 전용으로 열었습니다');
    const copy = path.join(r.dir, 'my-console.circ');
    await answerSave(r.app, copy);
    await recordCalls(r.app);
    await page.keyboard.press('Control+s');
    await expect(page.locator('.filebar .ptab', { hasText: 'my-console.circ' })).toBeVisible();
    expect((await sentCalls(r.app, 'file.save'))[0].params.path).toBe(copy);
    await expect(page.locator('.status')).not.toContainText('Read-only');
    // Open Recent: this run's files (the example itself is not one; the file saved from it is)
    m = await sub('File');
    await m.getByRole('menuitem', { name: /^Open Recent/ }).click();
    await expect(page.locator('.ovmenu').last().locator('> button .label')).toHaveText(['my-console.circ', 'demo-datapath.circ']);
    await page.keyboard.press('Escape');
    // Window: the open files, the one on show ticked; another one chosen goes to its tab
    m = await sub('Window');
    await expect(m.getByRole('menuitemradio', { name: 'my-console.circ' })).toHaveAttribute('aria-checked', 'true');
    await m.getByRole('menuitemradio', { name: 'demo-datapath.circ' }).click();
    await expect(page.locator('.filebar .ptab.on')).toHaveText('demo-datapath.circ');
    // Ctrl+W closes the file tab on show
    await page.keyboard.press('Control+w');
    await expect(page.locator('.filebar .ptab')).toHaveText(['my-console.circ']);
  } finally {
    await r.close();
  }
});

test('the caption patch: white; under the tutorial\'s shade (N-18\'s hook) the colour white takes under it; with a dialog too, both', async () => {
  const r = await withDatapath();
  const { page } = r;
  const patch = () => r.app.evaluate(({ BrowserWindow }) => (BrowserWindow.getAllWindows()[0] as unknown as { overlayColor?: string }).overlayColor ?? '#ffffff');
  try {
    expect(await patch()).toBe('#ffffff');
    await page.evaluate(() => document.documentElement.toggleAttribute('data-tutorial-shade', true));
    await expect.poll(patch).toBe(overlayColor(true, false));
    await page.getByTitle('Preferences').click();
    await expect.poll(patch).toBe(overlayColor(true, true));
    await page.keyboard.press('Escape');
    await expect.poll(patch).toBe(overlayColor(true, false));
    await page.evaluate(() => document.documentElement.toggleAttribute('data-tutorial-shade', false));
    await expect.poll(patch).toBe('#ffffff');
  } finally {
    await r.close();
  }
});

test('the Changed chip: the registers the cycle on show changed, three names at most; none while the clock runs', async () => {
  const r = await withDatapath();
  const { page } = r;
  try {
    for (let i = 0; i < 3; i += 1) await page.keyboard.press('F10');
    await expect(page.locator('.status')).toContainText('Cycle 3');
    const chip = page.locator('.status .changed');
    await expect(chip).toBeVisible();
    await expect(chip).toHaveText(/^Changed \$\w+/);
    expect((await chip.locator('.mono').count())).toBeLessThanOrEqual(3);
    await page.keyboard.press('F5');   // running: no chip
    await expect(page.locator('.status .run')).toBeVisible();
    await expect(chip).toHaveCount(0);
    await page.keyboard.press('F5');
  } finally {
    await r.close();
  }
});

test('the lab PCs at 125 % and 150 %: the whole bar and every fact at once, nothing on a » list', async () => {
  for (const [size, scale] of [[{ width: 1536, height: 816 }, '1.25'], [{ width: 1280, height: 672 }, '1.5']] as const) {
    const r = await withDatapath(size, [`--force-device-scale-factor=${scale}`]);
    try {
      await r.page.keyboard.press('F10');
      await expect(r.page.locator('.status')).toContainText('Cycle 1');
      expect(await units(r.page, '[data-over]'), scale).toEqual([]);
      expect((await facts(r.page)).more, scale).toEqual([]);
      expect(await fitsWindow(r.page)).toEqual({ status: true, title: true });
    } finally {
      await r.close();
    }
  }
});

test('Find beside what it found (#433\'s UI review): at half a screen the place is shown in the Canvas\'s part the Find window does not cover, with a halo', async () => {
  const r = await withDatapath(HALF);
  const { page } = r;
  try {
    await page.keyboard.press('Control+f');
    await page.keyboard.type('regfile');
    const rows = page.locator('.findwin .findrow');
    await rows.first().waitFor();
    await rows.first().dblclick();
    await expect.poll(() => page.evaluate(() => (window as unknown as { __hcsCanvas: { markedIds(): { components: string[] } | null } }).__hcsCanvas.markedIds()?.components.length ?? 0)).toBeGreaterThan(0);
    await page.waitForFunction(() => (window as unknown as { __hcsCanvas: { settled(): boolean } }).__hcsCanvas.settled());
    const place = await page.evaluate(() => {
      type C = { canvas: HTMLCanvasElement; view: { x: number; y: number; zoom: number }; scene: { components: Map<string, { bounds: number[] }> }; markedIds(): { components: string[] } | null };
      const c = (window as unknown as { __hcsCanvas: C }).__hcsCanvas;
      const k = c.scene.components.get(c.markedIds()!.components[0])!;
      const rr = c.canvas.getBoundingClientRect();
      return { left: rr.left + (k.bounds[0] - c.view.x) * c.view.zoom, right: rr.left + (k.bounds[0] + k.bounds[2] - c.view.x) * c.view.zoom };
    });
    const win = (await page.locator('.findwin').boundingBox())!;
    expect(place.right).toBeLessThanOrEqual(win.x);
  } finally {
    await r.close();
  }
});

test('Ctrl+2 … Ctrl+9: the file\'s toolbar (.circ <toolbar>) second … ninth tool, as the original\'s; Ctrl+1 stays 100 % (I-112)', async () => {
  const r = await withDatapath();
  const { page } = r;
  try {
    await recordCalls(r.app);
    await page.locator('.canvas-view canvas').hover();
    await page.keyboard.press('Control+5');   // demo-datapath's fifth: the output pin
    await expect(page.locator('.toolbar [data-unit="Pin"]')).toHaveAttribute('aria-checked', 'true');
    await expect.poll(async () => (await sentCalls(r.app, 'model.tool')).length).toBeGreaterThan(0);
    const held = (await sentCalls(r.app, 'model.tool')).at(-1)!.params;
    expect([held.lib, held.name, (held.attrs as Record<string, string>).output]).toEqual(['Wiring', 'Pin', 'true']);
    await page.keyboard.press('Control+2');   // the second: Edit Tool
    await expect(page.locator('.toolbar [data-unit="Edit"]')).toHaveAttribute('aria-checked', 'true');
    await page.keyboard.press('Control+7');   // the seventh: AND Gate
    await expect.poll(async () => (await sentCalls(r.app, 'model.tool')).at(-1)?.params.name).toBe('AND Gate');
    expect((await sentCalls(r.app, 'model.toolbar')).length).toBe(1);   // asked once for the file
    await page.keyboard.press('Control+1');
    await expect(page.locator('.status .zoom-button')).toHaveText('100%');
  } finally {
    await r.close();
  }
});

test('the window\'s title names the file and the circuit on show (E-12); the top rows are the title bar, the file tabs, the circuit tabs, then the Canvas (S-20)', async () => {
  const r = await withDatapath();
  const { page } = r;
  const winTitle = () => r.app.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows()[0].getTitle());
  try {
    await expect(page).toHaveTitle('demo-datapath.circ — Hallym Circuit Studio');
    await expect.poll(winTitle).toBe('demo-datapath.circ — Hallym Circuit Studio');
    await page.getByRole('tab', { name: 'Circuits' }).click();
    await page.locator('.upper .pbody:visible .list > li', { hasText: 'regfile' }).getByRole('button').click();
    await expect(page).toHaveTitle('demo-datapath.circ › regfile — Hallym Circuit Studio');
    await expect.poll(winTitle).toBe('demo-datapath.circ › regfile — Hallym Circuit Studio');
    // No toolbar row under the title bar (D-158: the » rule instead); the file tabs right under it, then the circuit tabs, then the Canvas
    await expect(page.locator('.toolrow')).toHaveCount(0);
    const bottom = async (sel: string) => { const b = (await page.locator(sel).boundingBox())!; return b.y + b.height; };
    const top = async (sel: string) => (await page.locator(sel).boundingBox())!.y;
    expect(await top('.canvaspanel .filebar') - await bottom('.titlebar')).toBeLessThanOrEqual(10);
    expect(Math.abs(await top('.circuitbar') - await bottom('.canvaspanel .filebar'))).toBeLessThanOrEqual(1);
    // (the circuit's Layout / Appearance row of N-11 may stand between the circuit tabs and the Canvas)
    const gap = await top('.canvas-view') - await bottom('.circuitbar');
    expect(gap).toBeGreaterThanOrEqual(-1);
    expect(gap).toBeLessThanOrEqual(40);
  } finally {
    await r.close();
  }
});

test('a file just opened keeps the Canvas\'s own fit while the window settles; a view the student moved stays (D-158 16)', async () => {
  const r = await withDatapath();
  const { page } = r;
  type B = { view: { zoom: number; x: number; y: number }; fitView(a: boolean): void };
  const view = () => page.evaluate(() => ({ ...(window as unknown as { __hcsCanvas: B }).__hcsCanvas.view }));
  try {
    const first = await view();
    // a new size before anything moved the view: fitted again to the new size (the same as Ctrl+0 there)
    await resize(r, { width: 1300, height: 800 });
    await expect.poll(async () => (await view()).zoom).not.toBe(first.zoom);
    const shown = await view();
    const fit = await page.evaluate(() => { const c = (window as unknown as { __hcsCanvas: B }).__hcsCanvas; c.fitView(false); return { ...c.view }; });
    expect(shown).toEqual(fit);
    // the student zooms (100 %): the next size keeps that
    await page.locator('.canvas-view canvas').hover();
    await page.keyboard.press('Control+1');
    await expect.poll(async () => (await view()).zoom).toBe(1);
    await resize(r, { width: 1500, height: 900 });
    await page.waitForFunction(() => window.innerWidth === 1500);
    await page.waitForTimeout(200);
    expect((await view()).zoom).toBe(1);
  } finally {
    await r.close();
  }
});
