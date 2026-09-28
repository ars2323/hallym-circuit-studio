/* Circuits, appearances, libraries and windows (N-11, D-153; the fake
   engine's plain records, tests/fake-engine/fake-circuits.ts -- the real
   engine's rules are in real-engine-circuits.e2e.ts and engine/):

     the Circuits panel      Add Circuit (the name's checks), Rename, Set As
                             Main, Move, Remove (and its refusals), Delete key
     Port Order, Auto Appearance   the question before connections break
     the appearance editor   a circuit tab's Appearance, the drawing tools,
                             Select, the attributes, Undo
     the instance band       a subcircuit on its own tab, Go to Instance in main
     Import Subcircuits, Load / Unload Library
     file tabs               same names by folder, Detach Tab / Attach Tab,
                             View Side by Side, a tab dropped on the Canvas,
                             Ctrl+W */

import { expect, type Page, test } from '@playwright/test';
import { mkdirSync } from 'node:fs';
import path from 'node:path';

import { answerOpen, launch, newCircuit, openFile, recordCalls, sample, sentCalls } from './harness.ts';

const SUB = 'tests/circ/subcircuit.circ';

async function circuitsTab(page: Page): Promise<void> {
  await page.locator('.panel.upper .ptab', { hasText: 'Circuits' }).click();
  await page.locator('.circlist').waitFor();
}
const circuitNames = (page: Page) => page.locator('.circlist li button .mono').allInnerTexts();
async function circuitMenu(page: Page, name: string, item: string): Promise<void> {
  await page.locator('.circlist li button', { hasText: name }).first().click({ button: 'right' });
  await page.locator('.ovmenu button', { hasText: item }).first().click();
}

test('the Circuits panel: Add Circuit checks the name, Rename, Set As Main, Move, Remove and its refusals, the Delete key', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await newCircuit(r);
    await circuitsTab(page);
    await expect.poll(() => circuitNames(page)).toEqual(['main']);
    // Add Circuit: an empty name and a name there already are refused under the field
    await page.locator('.circbar button', { hasText: 'Add Circuit' }).click();
    const dlg = page.locator('dialog.namedialog');
    await expect(dlg.locator('h2')).toHaveText('Add Circuit');
    await dlg.getByRole('button', { name: 'Add' }).click();
    await expect(dlg.locator('.hint.err')).toHaveText('회로 이름을 적으세요.');
    await dlg.locator('input').fill('main');
    await dlg.locator('input').press('Enter');
    await expect(dlg.locator('.hint.err')).toHaveText('이 파일에 같은 이름의 회로가 이미 있습니다.');
    await dlg.locator('input').fill('alu');
    await dlg.locator('input').press('Enter');
    await expect(dlg).toHaveCount(0);
    await expect.poll(() => circuitNames(page)).toEqual(['main', 'alu']);
    await expect(page.locator('.circuitbar .ptab.on')).toContainText('alu');   // the new circuit on show (original)
    // Rename: the list and the tab follow
    await circuitMenu(page, 'alu', 'Rename…');
    await page.locator('dialog.namedialog input').fill('alu32');
    await page.locator('dialog.namedialog').getByRole('button', { name: 'Rename' }).click();
    await expect.poll(() => circuitNames(page)).toEqual(['main', 'alu32']);
    await expect(page.locator('.circuitbar .ptab.on')).toContainText('alu32');
    // Set As Main: the house moves
    await circuitMenu(page, 'alu32', 'Set As Main Circuit');
    await expect(page.locator('.circlist li', { hasText: 'alu32' }).locator('.mainmark')).toHaveCount(1);
    await expect(page.locator('.circlist li', { hasText: /^main$/ }).locator('.mainmark')).toHaveCount(0);
    // Move Up, and Ctrl+Z puts it back
    await circuitMenu(page, 'alu32', 'Move Circuit Up');
    await expect.poll(() => circuitNames(page)).toEqual(['alu32', 'main']);
    await page.keyboard.press('Control+z');
    await expect.poll(() => circuitNames(page)).toEqual(['main', 'alu32']);
    // Remove: Delete on the list; the last one is refused
    await page.locator('.circlist li button', { hasText: /^main/ }).focus();
    await page.keyboard.press('Delete');
    await expect.poll(() => circuitNames(page)).toEqual(['alu32']);
    await expect(page.locator('.circuitbar .ptab')).toHaveCount(1);   // the removed circuit's tab closed
    await page.locator('.circlist li button', { hasText: 'alu32' }).focus();
    await page.keyboard.press('Delete');
    await expect(page.locator('.status')).toContainText('파일에는 회로가 하나는 있어야 해서 alu32 회로를 지우지 않았습니다');
    await expect.poll(() => circuitNames(page)).toEqual(['alu32']);
  } finally {
    await r.close();
  }
});

test('Port Order and Auto Appearance: the columns, ▲▼, and the question before instance connections break (Cancel first)', async () => {
  const r = await launch();
  const { page, app } = r;
  try {
    await openFile(r, sample(r.dir, SUB));
    await circuitsTab(page);
    await recordCalls(app);
    await circuitMenu(page, 'half_adder', 'Port Order…');
    const dlg = page.locator('dialog.portorder');
    await expect(dlg.locator('.portcol h3')).toHaveText(['West', 'East']);
    const west = dlg.locator('.portcol').first().locator('.portrow');
    const before = await west.locator('.mono').allInnerTexts();
    expect(before.length).toBe(2);
    await west.first().getByRole('button', { name: /down/ }).click();
    await expect(west.locator('.mono')).toHaveText([before[1], before[0]]);
    await dlg.getByRole('button', { name: 'Apply' }).click();
    // two instances in main use it: the question, its places, Cancel first
    const q = page.locator('dialog.impact');
    await expect(q.locator('h2')).toHaveText('Port Order');
    await expect(q).toContainText('인스턴스 2개에서 연결 4곳이 끊어집니다');
    await expect(q.locator('pre')).toContainText('main › half_adder #1');
    await expect(q.getByRole('button', { name: 'Cancel' })).toBeFocused();
    await q.getByRole('button', { name: 'Cancel' }).click();
    expect((await sentCalls(app, 'edit.portOrder')).map((c) => c.params.confirm)).toEqual([false]);
    // Auto Appearance: the same question, Apply this time
    await circuitMenu(page, 'half_adder', 'Auto Appearance');
    await page.locator('dialog.impact').getByRole('button', { name: 'Apply' }).click();
    await expect.poll(async () => (await sentCalls(app, 'edit.autoAppearance')).map((c) => c.params.confirm)).toEqual([false, true]);
  } finally {
    await r.close();
  }
});

test('the appearance editor: a circuit tab\'s Appearance, a rectangle drawn, chosen, its attribute changed, Undo; Layout again', async () => {
  const r = await launch();
  const { page, app } = r;
  try {
    await openFile(r, sample(r.dir, SUB));
    await circuitsTab(page);
    await circuitMenu(page, 'half_adder', 'Edit Circuit Appearance');
    await expect(page.locator('.appeditor')).toBeVisible();
    await expect(page.locator('.modeswitch button', { hasText: 'Appearance' })).toHaveAttribute('aria-checked', 'true');
    await expect(page.locator('.circuitbar .ptab.on')).toContainText('Appearance');
    const shapes = page.locator('.appshapes .appshape');
    await expect(shapes).not.toHaveCount(0);
    const n = await shapes.count();
    await expect(page.locator('.appfacts')).toContainText('Default appearance');
    await recordCalls(app);
    // the Rectangle tool: a drag makes one, the tool is Select again and the rectangle is chosen
    await page.getByRole('radio', { name: 'Rectangle', exact: true }).click();
    await expect(page.locator('.attrs .attrtitle')).toHaveText('Rectangle Tool');
    const box = (await page.locator('.appsvg').boundingBox())!;
    await page.mouse.move(box.x + 40, box.y + 40);
    await page.mouse.down();
    await page.mouse.move(box.x + 120, box.y + 90, { steps: 4 });
    await page.mouse.up();
    await expect(shapes).toHaveCount(n + 1);
    await expect(page.getByRole('radio', { name: 'Select', exact: true })).toHaveAttribute('aria-checked', 'true');
    await expect(page.locator('.appover .apphandle')).toHaveCount(4);
    const add = (await sentCalls(app, 'edit.appearance')).find((c) => c.params.op === 'add')!;
    expect((add.params.shape as { kind: string }).kind).toBe('rect');
    await expect(page.locator('.appfacts')).toContainText('Custom appearance');
    // its attributes: Stroke Width
    await expect(page.locator('.attrs .attrtitle')).toHaveText('Rectangle');
    await page.locator('.attrtable input[aria-label="Stroke Width"]').fill('3');
    await page.locator('.attrtable input[aria-label="Stroke Width"]').press('Enter');
    await expect.poll(async () => (await sentCalls(app, 'edit.appearance')).some((c) => c.params.op === 'setAttr' && c.params.value === '3')).toBe(true);
    // Delete removes it; Ctrl+Z brings it back
    await page.locator('.appsvg').focus();
    await page.keyboard.press('Delete');
    await expect(shapes).toHaveCount(n);
    await page.keyboard.press('Control+z');
    await expect(shapes).toHaveCount(n + 1);
    // Layout: the Canvas again
    await page.locator('.modeswitch button', { hasText: 'Layout' }).click();
    await expect(page.locator('.appeditor')).toHaveCount(0);
  } finally {
    await r.close();
  }
});

test('a subcircuit on its own tab: the band says its values are not main\'s; Go to Instance in main goes into one of its instances', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, SUB));
    await circuitsTab(page);
    await page.locator('.circlist li button', { hasText: 'half_adder' }).click();
    const band = page.locator('.instband');
    await expect(band).toBeVisible();
    await expect(band).toContainText('half_adder 회로를 따로 열었습니다');
    await band.getByRole('button', { name: /Go to Instance in main/ }).click();
    await page.locator('.ovmenu button').first().click();   // two instances: the paths to choose from
    await expect(page.locator('.circuitbar .ptab.on')).toContainText('main');
    await expect(band).toBeHidden();
  } finally {
    await r.close();
  }
});

test('Import Subcircuits: the file\'s circuits, the plan, the import (one undo step); Load and Unload Library', async () => {
  const r = await launch();
  const { page, app } = r;
  try {
    await newCircuit(r);
    await circuitsTab(page);
    await recordCalls(app);
    await answerOpen(app, sample(r.dir, SUB));
    await page.locator('.circbar button', { hasText: 'Import' }).click();
    const dlg = page.locator('dialog.importdialog');
    await expect(dlg).toContainText('subcircuit.circ');
    await dlg.locator('.checkrow', { hasText: /^main/ }).locator('input').uncheck();
    await dlg.getByRole('button', { name: 'Next' }).click();
    await expect(page.locator('dialog.importdialog pre')).toContainText('half_adder');
    await page.locator('dialog.importdialog').getByRole('button', { name: 'Import' }).click();
    await expect.poll(() => circuitNames(page)).toEqual(['main', 'half_adder']);
    await expect(page.locator('.status')).toContainText('가져왔습니다');
    await page.keyboard.press('Control+z');
    await expect.poll(() => circuitNames(page)).toEqual(['main']);
    // Unload Libraries…: I/O out; Built-in Library…: I/O back
    await page.locator('.circbar button', { hasText: 'Libraries' }).click();
    await page.locator('.ovmenu button', { hasText: 'Unload Libraries…' }).click();
    const un = page.locator('dialog.libdialog');
    await un.locator('.checkrow', { hasText: 'Input/Output' }).locator('input').check();
    await un.getByRole('button', { name: 'Unload' }).click();
    await expect.poll(async () => (await sentCalls(app, 'edit.unloadLibrary')).length).toBe(1);
    await page.locator('.circbar button', { hasText: 'Libraries' }).click();
    await page.locator('.ovmenu button', { hasText: 'Load Library' }).hover();
    await page.locator('.ovmenu button', { hasText: 'Built-in Library…' }).click();
    const load = page.locator('dialog.libdialog');
    await load.locator('.checkrow', { hasText: 'Input/Output' }).locator('input').check();
    await load.getByRole('button', { name: 'Load' }).click();
    await expect.poll(async () => (await sentCalls(app, 'edit.loadLibrary')).map((c) => c.params.name)).toEqual(['I/O']);
  } finally {
    await r.close();
  }
});

test('file tabs: two files of one name show their folders; Detach Tab makes a window of its own, Attach Tab brings it back; Ctrl+W closes the focused tab', async () => {
  const r = await launch();
  const { page, app } = r;
  try {
    mkdirSync(path.join(r.dir, 'hw1'));
    mkdirSync(path.join(r.dir, 'hw2'));
    await openFile(r, sample(r.dir, SUB, 'hw1/lab.circ'));
    await answerOpen(app, sample(r.dir, SUB, 'hw2/lab.circ'));
    await page.keyboard.press('Control+o');
    await expect(page.locator('.filebar .ptab')).toHaveCount(2);
    await expect(page.locator('.filebar .ptab .tabnote')).toHaveText(['— hw1', '— hw2']);
    // Detach Tab: the second file in its own window
    await page.locator('.filebar .ptab').nth(1).click({ button: 'right' });
    await page.locator('.ovmenu button', { hasText: 'Detach Tab' }).click();
    await expect.poll(() => app.windows().length).toBe(2);
    await expect(page.locator('.filebar .ptab')).toHaveCount(1);
    const own = app.windows().find((w) => w !== page)!;
    await own.locator('.filebar .ptab', { hasText: 'lab.circ' }).waitFor();
    await expect(own.locator('.filebar .ptab .tabnote')).toContainText('Window');
    // the main window's Components: that file under Open Files by its name, not its id
    await expect(page.locator('.upper .libgroup summary', { hasText: 'Open Files' })).toHaveText([/Open Files · lab\.circ/]);
    // Attach Tab: back to the main window, the window closes
    await own.locator('.filebar .ptab').click({ button: 'right' });
    await own.locator('.ovmenu button', { hasText: 'Attach Tab' }).click();
    await expect.poll(() => app.windows().length).toBe(1);
    await expect(page.locator('.filebar .ptab')).toHaveCount(2);
    // Ctrl+W: the file tab on show
    await page.keyboard.press('Control+w');
    await expect(page.locator('.filebar .ptab')).toHaveCount(1);
  } finally {
    await r.close();
  }
});

test('View Side by Side: the main window left half, the file\'s own right half; closing that window closes its file', async () => {
  const r = await launch();
  const { page, app } = r;
  try {
    await openFile(r, sample(r.dir, SUB));
    await newCircuit2(r.page);
    await page.locator('.filebar .ptab').first().click({ button: 'right' });
    await page.locator('.ovmenu button', { hasText: 'View Side by Side' }).click();
    await expect.poll(() => app.windows().length).toBe(2);
    const bounds = await app.evaluate(({ BrowserWindow, screen }) => ({
      area: screen.getPrimaryDisplay().workArea,
      wins: BrowserWindow.getAllWindows().map((w) => w.getBounds()),
    }));
    const xs = bounds.wins.map((b) => b.x).sort((a, b) => a - b);
    expect(xs[0]).toBe(bounds.area.x);
    expect(Math.abs(xs[1] - (bounds.area.x + Math.floor(bounds.area.width / 2)))).toBeLessThanOrEqual(2);
    const own = app.windows().find((w) => w !== page)!;
    await own.locator('.filebar .ptab').waitFor();
    // its close button (the system's): the file closes with it (nothing unsaved: no question)
    await app.evaluate(() => {
      const g = globalThis as unknown as { __hcsWindows: { detached(): { window: { close(): void } }[] } };
      g.__hcsWindows.detached()[0].window.close();
    });
    await expect.poll(() => app.windows().length).toBe(1);
    await expect(page.locator('.filebar .ptab')).toHaveCount(1);
  } finally {
    await r.close();
  }
});

async function newCircuit2(page: Page): Promise<void> {
  await page.keyboard.press('Control+n');
  await expect(page.locator('.filebar .ptab')).toHaveCount(2);
}
