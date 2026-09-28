/* Circuits, appearances, libraries and windows, the rest (N-11, D-153; the
   fake engine's plain records, tests/fake-engine/fake-circuits.ts):

     the appearance editor's tools   Line (Shift: level), Oval, Polyline
                             (clicks, a double click ends), Polygon (a click
                             on its start closes), Curve (ends, then the
                             control), Text (Enter adds, Esc drops)
     the appearance editor's Select  a drag moves, a box chooses, Shift+click
                             leaves one out, Ctrl+↑ / Ctrl+Shift+↓, the right
                             click's Duplicate
     a subcircuit instance   its right click (View, Edit Appearance of, Port
                             Order), Ctrl+→ / Ctrl+←, a pin deleted inside
                             (model.portImpact)
     the Circuits list       a circuit dragged onto another
     another open file       its tab dropped on the Canvas: a library and its
                             main circuit there; saving it: the connections
                             it would break first, then " · Updated"
     hcs-mips.jar            Copy hcs-mips.jar Here after a save
     windows of their own    the main window's close asks each of them first */

import { expect, type Page, test } from '@playwright/test';

import { answerOpen, DATAPATH, launch, openFile, recordCalls, sample, type SentCall, sentCalls } from './harness.ts';
import { menu, rightClick } from './overlay-helpers.ts';

const SUB = 'tests/circ/subcircuit.circ';
const GATES = 'tests/circ/gates.circ';

async function circuitsTab(page: Page): Promise<void> {
  await page.locator('.panel.upper .ptab', { hasText: 'Circuits' }).click();
  await page.locator('.circlist').waitFor();
}
async function circuitMenu(page: Page, name: string, item: string): Promise<void> {
  await page.locator('.circlist li button', { hasText: name }).first().click({ button: 'right' });
  await page.locator('.ovmenu button', { hasText: item }).first().click();
}
const ops = async (app: Parameters<typeof sentCalls>[0], op: string): Promise<SentCall[]> =>
  (await sentCalls(app, 'edit.appearance')).filter((c) => c.params.op === op);
const shapeOf = (c: SentCall) => c.params.shape as { kind: string; points?: [number, number][]; text?: string; bounds?: number[] };

async function appearanceOf(page: Page, name: string): Promise<{ x: number; y: number; width: number; height: number }> {
  await circuitsTab(page);
  await circuitMenu(page, name, 'Edit Circuit Appearance');
  await page.locator('.appshapes .appshape').first().waitFor();
  return (await page.locator('.appsvg').boundingBox())!;
}
// A point of a part of that name that no other part covers (the Canvas takes the last part drawn there).
const onlyPart = (page: Page, name: string) => page.evaluate((n) => {
  const c = (window as unknown as { __hcsCanvas: { partAt(p: number[]): string | null; scene: { components: Map<string, { id: string; name: string; bounds: number[] }> } } }).__hcsCanvas;
  for (const k of c.scene.components.values()) {
    if (k.name !== n) continue;
    const [x, y, w, hh] = k.bounds;
    for (let dy = 1; dy < hh; dy += 2) for (let dx = 1; dx < w; dx += 2) if (c.partAt([x + dx, y + dy]) === k.id) return [x + dx, y + dy] as [number, number];
  }
  throw new Error(`no point of a ${n} alone`);
}, name);
const tool = (page: Page, name: string) => page.locator('.apptools').getByRole('radio', { name, exact: true }).click();

test('appearance tools: Line with Shift stays level, Oval, Polyline ends on a double click, Polygon closes on its start, Curve, Text (Enter adds, Esc drops)', async () => {
  const r = await launch();
  const { page, app } = r;
  try {
    await openFile(r, sample(r.dir, SUB));
    const box = await appearanceOf(page, 'half_adder');
    await recordCalls(app);
    const x0 = box.x + 40, y0 = box.y + box.height - 220;
    // Line, Shift held: level whatever the pointer's height
    await tool(page, 'Line');
    await page.keyboard.down('Shift');
    await page.mouse.move(x0, y0);
    await page.mouse.down();
    await page.mouse.move(x0 + 120, y0 + 12, { steps: 4 });
    await page.mouse.up();
    await page.keyboard.up('Shift');
    await expect.poll(async () => (await ops(app, 'add')).length).toBe(1);
    const line = shapeOf((await ops(app, 'add'))[0]);
    expect(line.kind).toBe('line');
    expect(line.points![0][1]).toBe(line.points![1][1]);
    expect(line.points![1][0]).toBeGreaterThan(line.points![0][0]);
    await expect(page.getByRole('radio', { name: 'Select', exact: true })).toHaveAttribute('aria-checked', 'true');
    // Oval: a drag
    await tool(page, 'Oval');
    await page.mouse.move(x0 + 160, y0);
    await page.mouse.down();
    await page.mouse.move(x0 + 220, y0 + 40, { steps: 4 });
    await page.mouse.up();
    await expect.poll(async () => (await ops(app, 'add')).map((c) => shapeOf(c).kind)).toEqual(['line', 'oval']);
    // Polyline: a click, another, a double click ends it
    await tool(page, 'Polyline');
    await page.mouse.click(x0, y0 + 60);
    await page.mouse.move(x0 + 40, y0 + 100, { steps: 2 });
    await page.mouse.click(x0 + 40, y0 + 100);
    await page.mouse.move(x0 + 80, y0 + 60, { steps: 2 });
    await page.mouse.dblclick(x0 + 80, y0 + 60);
    await expect.poll(async () => (await ops(app, 'add')).length).toBe(3);
    const pl = shapeOf((await ops(app, 'add'))[2]);
    expect(pl.kind).toBe('polyline');
    expect(pl.points!.length).toBe(3);
    // Polygon: three corners, then a click on the first closes it
    await tool(page, 'Polygon');
    await page.mouse.click(x0 + 120, y0 + 60);
    await page.mouse.move(x0 + 180, y0 + 60, { steps: 2 });
    await page.mouse.click(x0 + 180, y0 + 60);
    await page.mouse.move(x0 + 150, y0 + 110, { steps: 2 });
    await page.mouse.click(x0 + 150, y0 + 110);
    await page.mouse.move(x0 + 120, y0 + 60, { steps: 2 });
    await page.mouse.click(x0 + 120, y0 + 60);
    await expect.poll(async () => (await ops(app, 'add')).length).toBe(4);
    const pg = shapeOf((await ops(app, 'add'))[3]);
    expect(pg.kind).toBe('polygon');
    expect(pg.points!.length).toBe(3);
    // Curve: its ends by a drag, then its control by a click
    await tool(page, 'Curve');
    await page.mouse.move(x0, y0 + 140);
    await page.mouse.down();
    await page.mouse.move(x0 + 100, y0 + 140, { steps: 4 });
    await page.mouse.up();
    await page.mouse.move(x0 + 50, y0 + 180);
    await page.mouse.down();
    await page.mouse.up();
    await expect.poll(async () => (await ops(app, 'add')).length).toBe(5);
    const cv = shapeOf((await ops(app, 'add'))[4]);
    expect(cv.kind).toBe('curve');
    expect(cv.points!.length).toBe(3);
    // Text: a click opens the field; Esc drops it (nothing added), Enter adds
    await tool(page, 'Text');
    await page.mouse.click(x0 + 200, y0 + 150);
    const field = page.locator('input.apptext');
    await expect(field).toBeFocused();
    await field.fill('carry');
    await field.press('Escape');
    await expect(field).toHaveCount(0);
    await tool(page, 'Text');
    await page.mouse.click(x0 + 200, y0 + 150);
    await page.locator('input.apptext').fill('sum');
    await page.locator('input.apptext').press('Enter');
    await expect.poll(async () => (await ops(app, 'add')).length).toBe(6);
    const tx = shapeOf((await ops(app, 'add'))[5]);
    expect([tx.kind, tx.text]).toEqual(['text', 'sum']);
  } finally {
    await r.close();
  }
});

test('appearance Select: a drag moves the chosen shape, a box chooses several, Shift+click leaves one out, Ctrl+↑ and Ctrl+Shift+↓, the right click\'s Duplicate', async () => {
  const r = await launch();
  const { page, app } = r;
  try {
    await openFile(r, sample(r.dir, SUB));
    const box = await appearanceOf(page, 'half_adder');
    const x0 = box.x + 40, y0 = box.y + box.height - 200;
    const draw = async (name: string, at: [number, number]) => {
      await tool(page, name);
      await page.mouse.move(at[0], at[1]);
      await page.mouse.down();
      await page.mouse.move(at[0] + 50, at[1] + 40, { steps: 4 });
      await page.mouse.up();
    };
    const shapes = page.locator('.appshapes .appshape');
    const n = await shapes.count();
    await draw('Rectangle', [x0, y0]);
    await draw('Oval', [x0 + 100, y0]);
    await expect(shapes).toHaveCount(n + 2);
    await recordCalls(app);
    // the oval is chosen (the tool's last shape): a drag on it moves it
    await page.mouse.move(x0 + 125, y0 + 20);
    await page.mouse.down();
    await page.mouse.move(x0 + 165, y0 + 60, { steps: 6 });
    await page.mouse.up();
    await expect.poll(async () => (await ops(app, 'move')).length).toBe(1);
    const mv = (await ops(app, 'move'))[0].params;
    expect(Number(mv.dx)).toBeGreaterThan(0);
    expect(Number(mv.dy)).toBeGreaterThan(0);
    expect((mv.shapes as number[]).length).toBe(1);
    // a box from empty ground around both: both chosen (Delete says so)
    await page.mouse.move(x0 - 20, y0 - 20);
    await page.mouse.down();
    await page.mouse.move(x0 + 260, y0 + 140, { steps: 8 });
    await page.mouse.up();
    await expect(page.locator('.appover .apphandle')).not.toHaveCount(0);
    // Shift+click on the rectangle's edge: out of the choice
    await page.keyboard.down('Shift');
    await page.mouse.click(x0 + 1, y0 + 20);
    await page.keyboard.up('Shift');
    // Ctrl+↑ raises what is chosen (the oval), Ctrl+Shift+↓ lowers it to the bottom
    await page.locator('.appsvg').focus();
    await page.keyboard.press('Control+ArrowUp');
    await page.keyboard.press('Control+Shift+ArrowDown');
    await expect.poll(async () => (await sentCalls(app, 'edit.appearance')).map((c) => c.params.op).filter((o) => o !== 'move')).toEqual(['raise', 'lowerBottom']);
    const raised = (await ops(app, 'raise'))[0].params.shapes as number[];
    expect(raised).toEqual(mv.shapes);
    // the right click: the Edit menu of the original, Duplicate
    const sb = (await page.locator('.appsvg').boundingBox())!;
    await page.mouse.click(sb.x + sb.width - 30, sb.y + 30, { button: 'right' });
    await page.locator('.ovmenu').waitFor();
    await expect(page.locator('.ovmenu button', { hasText: 'Raise to Top' })).toBeVisible();
    await expect(page.locator('.ovmenu button', { hasText: 'Add Vertex' })).toBeDisabled();
    await page.locator('.ovmenu button', { hasText: 'Duplicate' }).click();
    await expect.poll(async () => (await ops(app, 'duplicate')).length).toBe(1);
  } finally {
    await r.close();
  }
});

test('a subcircuit instance: its right click (View, Edit Appearance of, Auto Appearance, Port Order…); Ctrl+→ goes into the first, Ctrl+← out', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await rightClick(page, await onlyPart(page, 'alu'));
    const labels = await page.locator('.ovmenu').first().locator('button .label').allInnerTexts();
    expect(labels.slice(0, 4)).toEqual(['View alu', 'Edit Appearance of alu', 'Auto Appearance', 'Port Order…']);
    await menu(page, 'Edit Appearance of alu');
    await expect(page.locator('.appeditor')).toBeVisible();
    await expect(page.locator('.circuitbar .ptab.on')).toContainText('alu');
    await page.locator('.modeswitch button', { hasText: 'Layout' }).click();
    // main again: Ctrl+→ into its first instance (crumbs), Ctrl+← out again
    await circuitsTab(page);
    await page.locator('.circlist li button', { hasText: /^main/ }).click();
    await page.locator('.canvas-view canvas').click({ position: { x: 5, y: 5 } });
    await page.keyboard.press('Control+ArrowRight');
    await expect(page.locator('.canvas-crumbs')).toContainText('main');
    await page.keyboard.press('Control+ArrowLeft');
    await expect(page.locator('.canvas-crumbs')).toBeHidden();
  } finally {
    await r.close();
  }
});

test('a pin deleted in a subcircuit that main uses: the status bar tells the instance connections it broke (model.portImpact)', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, SUB));
    await circuitsTab(page);
    const half = await page.locator('.circlist li button', { hasText: 'half_adder' }).getAttribute('data-circuit');
    await page.locator('.circlist li button', { hasText: 'half_adder' }).click();
    await page.evaluate(async (circuitId) => {
      const app = (window as unknown as { app: { call<T>(m: string, p: unknown): Promise<T> } }).app;
      const s = await app.call<{ components: { id: string; name: string }[] }>('model.circuit', { fileId: 'f1', circuitId });
      const pin = s.components.find((k) => k.name === 'Pin')!;
      await app.call('edit.delete', { fileId: 'f1', circuitId, ids: [pin.id] });
    }, half);
    await expect(page.locator('.status')).toContainText('half_adder 회로의 핀을 바꿔 인스턴스 연결 2개가 끊겼습니다');
  } finally {
    await r.close();
  }
});

test('the Circuits list: a circuit dragged onto another takes its place (one undo step)', async () => {
  const r = await launch();
  const { page, app } = r;
  try {
    await openFile(r, sample(r.dir, SUB));
    await circuitsTab(page);
    await recordCalls(app);
    await page.locator('.circlist li button', { hasText: 'half_adder' }).dragTo(page.locator('.circlist li button', { hasText: /^main/ }));
    await expect.poll(() => page.locator('.circlist li button .mono').allInnerTexts()).toEqual(['half_adder', 'main']);
    expect((await sentCalls(app, 'edit.moveCircuit')).map((c) => c.params.to)).toEqual([0]);
    await page.keyboard.press('Control+z');
    await expect.poll(() => page.locator('.circlist li button .mono').allInnerTexts()).toEqual(['main', 'half_adder']);
  } finally {
    await r.close();
  }
});

test('another open file\'s tab dropped on the Canvas: that file a library, its main circuit there; saving it asks about the connections it breaks, then the other tab says Updated', async () => {
  const r = await launch();
  const { page, app } = r;
  try {
    await openFile(r, sample(r.dir, GATES, 'adder.circ'));
    await answerOpen(app, sample(r.dir, SUB, 'lab.circ'));
    await page.keyboard.press('Control+o');
    await expect(page.locator('.filebar .ptab')).toHaveCount(2);
    await expect(page.locator('.filebar .ptab.on')).toContainText('lab.circ');
    await page.locator('.canvas-view canvas').waitFor();
    await recordCalls(app);
    await page.locator('.filebar .ptab', { hasText: 'adder.circ' }).dragTo(page.locator('.canvas-view canvas'), { targetPosition: { x: 300, y: 300 } });
    await expect.poll(async () => (await sentCalls(app, 'edit.loadLibrary')).map((c) => [c.params.kind, String(c.params.path).endsWith('adder.circ')])).toEqual([['circ', true]]);
    await expect.poll(async () => (await sentCalls(app, 'edit.addComponent')).map((c) => [c.params.lib, c.params.name])).toEqual([['adder', 'main']]);
    await expect(page.locator('.filebar .ptab.on')).toContainText('lab.circ');   // the file on show stays
    // adder.circ saved: lab.circ's part would lose its connections -- Cancel first, then Save Anyway
    await page.locator('.filebar .ptab', { hasText: 'adder.circ' }).click();
    await page.keyboard.press('Control+s');
    const q = page.locator('dialog.impact');
    await expect(q.locator('h2')).toHaveText('저장하면 다른 파일의 연결이 끊깁니다');
    await expect(q.locator('pre')).toHaveText('lab.circ: main — 2');
    await expect(q.getByRole('button', { name: 'Cancel' })).toBeFocused();
    await q.getByRole('button', { name: 'Cancel' }).click();
    expect(await sentCalls(app, 'file.save')).toEqual([]);
    await page.keyboard.press('Control+s');
    await page.locator('dialog.impact').getByRole('button', { name: 'Save Anyway' }).click();
    await expect.poll(async () => (await sentCalls(app, 'file.save')).length).toBe(1);
    await expect(page.locator('.filebar .ptab', { hasText: 'lab.circ' }).locator('.tabnote')).toContainText('Updated');
    // chosen: the note once, and the tab's word goes
    await page.locator('.filebar .ptab', { hasText: 'lab.circ' }).click();
    await expect(page.locator('.filebar .ptab', { hasText: 'lab.circ' }).locator('.tabnote')).toHaveCount(0);
  } finally {
    await r.close();
  }
});

test('a save that needs hcs-mips.jar beside the file: Copy hcs-mips.jar Here copies it on the student\'s word', async () => {
  const r = await launch(undefined, { env: { FAKE_ENGINE_MODE: 'needs-mips' } });
  const { page, app } = r;
  try {
    await openFile(r, sample(r.dir, GATES));
    await recordCalls(app);
    await page.keyboard.press('Control+s');
    const b = page.locator('.status button', { hasText: 'Copy hcs-mips.jar Here' });
    await expect(b).toBeVisible();
    expect(await sentCalls(app, 'file.copyMipsJar')).toEqual([]);
    await b.click();
    await expect(page.locator('.status')).toContainText('복사했습니다 · hcs-mips.jar');
    expect((await sentCalls(app, 'file.copyMipsJar')).length).toBe(1);
  } finally {
    await r.close();
  }
});

test('closing the main window: a window of its own asks about its unsaved file first, then the app leaves', async () => {
  const r = await launch();
  const { page, app } = r;
  try {
    await openFile(r, sample(r.dir, GATES));
    await answerOpen(app, sample(r.dir, SUB));
    await page.keyboard.press('Control+o');
    await expect(page.locator('.filebar .ptab')).toHaveCount(2);
    await page.locator('.filebar .ptab', { hasText: 'subcircuit.circ' }).click({ button: 'right' });
    await page.locator('.ovmenu button', { hasText: 'Detach Tab' }).click();
    await expect.poll(() => app.windows().length).toBe(2);
    const own = app.windows().find((w) => w !== page)!;
    await own.locator('.filebar .ptab', { hasText: 'subcircuit.circ' }).waitFor();
    // an edit in its own window: unsaved
    await circuitsTab(own);
    await own.locator('.circbar button', { hasText: 'Add Circuit' }).click();
    await own.locator('dialog.namedialog input').fill('extra');
    await own.locator('dialog.namedialog input').press('Enter');
    await expect(own.locator('.filebar .ptab', { hasText: 'subcircuit.circ' })).toContainText('•');
    // the main window's close button: that window's question first
    await app.evaluate(({ BrowserWindow }) => {
      const g = globalThis as unknown as { __hcsWindows: { isMain(w: unknown): boolean } };
      BrowserWindow.getAllWindows().find((w) => g.__hcsWindows.isMain(w))!.close();
    });
    const ask = own.locator('dialog.ask');
    await expect(ask).toContainText('subcircuit.circ');
    await ask.getByRole('button', { name: 'Discard' }).click();
    await app.waitForEvent('close');
  } finally {
    await r.close();
  }
});
