/* The editing tools from the window (N-08, D-146; docs/interaction-parity.md
   I-01..I-62, I-77..I-83, I-105), with the fake engine: what the screen
   shows while a gesture goes on and which intent it sends at the end (the
   engine's own rules on the model: real-engine-edit.e2e.ts).  The fake's
   parts are 30 × 30 boxes left of their point and have no ports;
   demo-datapath (the engine-made fixture) has real ports and wires. */

import { expect, type Page, test } from '@playwright/test';

import { pagePoint } from './canvas-points.ts';
import { drag, hover, overlay, parts, placeTool, selected, tool, where, wires } from './edit-points.ts';
import { DATAPATH, launch, newCircuit, openFile, recordCalls, type Running, sample, sentCalls, visibleCharacters } from './harness.ts';

type P = [number, number];
const mid = (k: { bounds: number[] }): P => [k.bounds[0] + k.bounds[2] / 2, k.bounds[1] + k.bounds[3] / 2];
const sent = async (r: Running, method: string) => (await sentCalls(r.app, method)).map((c) => c.params);

// A new circuit with two gates placed through the toolbar's way of holding a part (the fake's boxes).
async function twoGates(r: Running): Promise<{ and: { id: string; bounds: number[] }; or: { id: string; bounds: number[] } }> {
  await newCircuit(r);
  await recordCalls(r.app);
  const { page } = r;
  await tool(page, 'Pin').click();
  await page.locator('.canvas-view canvas').waitFor();
  const w = await where(page);
  for (const [name, at] of [['AND Gate', [300, 200]], ['OR Gate', [300, 400]]] as const) {
    expect(await placeTool(page, { ...w, lib: 'Gates', name, source: 'components' })).toBe(true);
    await hover(page, [at[0], at[1]]);
    await page.mouse.down();
    await page.mouse.up();
    await expect.poll(async () => (await parts(page, name)).length).toBe(1);
  }
  return { and: (await parts(page, 'AND Gate'))[0], or: (await parts(page, 'OR Gate'))[0] };
}

const click = (page: Page, p: P, modifiers: ('Shift' | 'Alt' | 'Control')[] = []) => drag(page, p, [p], { modifiers });

test('placing (I-53..I-57, I-01): the toolbar\'s Pin and hcs:place-tool hold a part; its ghost on the grid under the pointer; a click places it where the ghost is; then the Edit tool, the part selected; hcs:tool says each tool', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await newCircuit(r);
    await recordCalls(r.app);
    await page.evaluate(() => {
      const w = window as unknown as { tools: string[] };
      w.tools = [];
      window.addEventListener('hcs:tool', (e) => w.tools.push((e as CustomEvent<{ tool: string }>).detail.tool));
    });
    await expect(page.locator('.canvas h3')).toHaveText('빈 회로입니다');
    await tool(page, 'Pin').click();
    await expect(tool(page, 'Pin')).toBeChecked();
    // an empty circuit shows the Canvas while a part is held, its origin at the top-left
    await page.locator('.canvas-view canvas').waitFor();
    await hover(page, [123, 87]);
    await expect.poll(async () => (await overlay(page)).ghost).toMatchObject({ dx: 120, dy: 90, look: 'place', parts: [{ name: 'Pin', id: 'ghost' }] });
    expect((await sent(r, 'model.tool'))[0]).toMatchObject({ lib: 'Wiring', name: 'Pin', loc: [0, 0] });
    await page.mouse.down();
    await page.mouse.up();
    await expect.poll(async () => (await parts(page, 'Pin')).length).toBe(1);
    expect((await sent(r, 'edit.addComponent'))[0]).toMatchObject({ lib: 'Wiring', name: 'Pin', loc: [120, 90] });
    await expect(tool(page, 'Edit')).toBeChecked();
    await expect.poll(() => selected(page)).toEqual([(await parts(page, 'Pin'))[0].id]);
    // hcs:place-tool without a point: held; with a point: placed there at once (N-12's contract, I-62's drop)
    const w = await where(page);
    expect(await placeTool(page, { ...w, lib: 'Gates', name: 'AND Gate', attrs: { inputs: '3' }, source: 'palette' })).toBe(true);
    await expect(tool(page, 'Edit')).not.toBeChecked();
    await hover(page, [300, 300]);
    await expect.poll(async () => ((await overlay(page)).ghost as { parts: { attrs: Record<string, string> }[] } | undefined)?.parts[0]?.attrs.inputs).toBe('3');
    await page.mouse.down();
    await page.mouse.up();
    await expect.poll(async () => (await parts(page, 'AND Gate')).length).toBe(1);
    expect((await sent(r, 'edit.addComponent'))[1]).toMatchObject({ lib: 'Gates', name: 'AND Gate', loc: [300, 300], attrs: { inputs: '3' } });
    expect(await placeTool(page, { ...w, lib: 'Gates', name: 'OR Gate', at: [500, 500], source: 'drop' })).toBe(true);
    await expect.poll(async () => (await parts(page, 'OR Gate'))[0]?.loc).toEqual([500, 500]);
    // leaving the Canvas takes the ghost away (I-55)
    await tool(page, 'Probe').click();
    await hover(page, [200, 200]);
    await expect.poll(async () => (await overlay(page)).ghost).toBeTruthy();
    await page.mouse.move(2, 2);
    await expect.poll(async () => (await overlay(page)).ghost).toBeUndefined();
    await tool(page, 'Wire').click();
    await tool(page, 'Text').click();
    await tool(page, 'Edit').click();
    const tools = await page.evaluate(() => (window as unknown as { tools: string[] }).tools);
    expect(tools).toEqual(['Pin', 'Edit', 'AND Gate', 'Edit', 'Probe', 'Wire', 'Text', 'Edit']);   // the drop kept the Edit tool: no event
  } finally {
    await r.close();
  }
});

test('a held part\'s keys (I-58, I-59): an arrow turns it, a digit and Alt+digit go to its configurator, the ghost as the engine answers', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await newCircuit(r);
    await recordCalls(r.app);
    await tool(page, 'Pin').click();
    await page.locator('.canvas-view canvas').waitFor();
    await hover(page, [200, 200]);
    await page.keyboard.press('ArrowUp');
    await page.keyboard.press('Digit4');
    await page.keyboard.press('Alt+Digit8');
    await expect.poll(async () => (await sent(r, 'edit.keyConfig')).length).toBe(3);
    const keys = await sent(r, 'edit.keyConfig');
    expect(keys.map((k) => [k.lib, k.name, k.key, k.alt])).toEqual([['Wiring', 'Pin', 'ArrowUp', false], ['Wiring', 'Pin', '4', false], ['Wiring', 'Pin', '8', true]]);
    expect(keys[2].chain).toBe(true);
    await expect.poll(async () => ((await overlay(page)).ghost as { parts: { attrs: Record<string, string> }[] } | undefined)?.parts[0]?.attrs).toMatchObject({ facing: 'north', inputs: '4', width: '8' });
  } finally {
    await r.close();
  }
});

test('the Edit tool selects (I-09..I-15, I-18, I-19): a click, Shift+click, a click inside the selection, nothing, a rectangle and Shift+rectangle, Ctrl+A; Esc keeps it; hcs:selection each time', async () => {
  const r = await launch();
  const { page } = r;
  try {
    const { and, or } = await twoGates(r);
    await tool(page, 'Edit').click();
    await click(page, mid(and));
    await expect.poll(() => selected(page)).toEqual([and.id]);
    expect((await sent(r, 'edit.select')).at(-1)).toMatchObject({ at: [285, 200], toggle: false });
    await click(page, mid(or), ['Shift']);
    await expect.poll(() => selected(page)).toEqual([and.id, or.id].sort());
    await click(page, mid(and));
    await expect.poll(async () => (await sent(r, 'edit.select')).length).toBe(3);
    expect(await selected(page)).toEqual([and.id, or.id].sort());   // inside the selection: kept, to move it all (I-11)
    await page.keyboard.press('Escape');
    expect(await selected(page)).toEqual([and.id, or.id].sort());   // I-19
    await click(page, [600, 100]);
    await expect.poll(() => selected(page)).toEqual([]);
    // the rectangle: the rubber band, then what is wholly inside it
    const a = await pagePoint(page, [250, 150]), b = await pagePoint(page, [350, 250]);
    await page.mouse.move(a.x, a.y);
    await page.mouse.down();
    await page.mouse.move(b.x, b.y, { steps: 4 });
    expect((await overlay(page)).rubber).toEqual({ x0: 250, y0: 150, x1: 350, y1: 250 });
    await page.mouse.up();
    await expect.poll(() => selected(page)).toEqual([and.id]);
    expect((await sent(r, 'edit.select')).at(-1)).toMatchObject({ rect: [250, 150, 350, 250], add: false });
    await drag(page, [250, 350], [[350, 450]], { modifiers: ['Shift'] });
    await expect.poll(() => selected(page)).toEqual([and.id, or.id].sort());
    expect((await sent(r, 'edit.select')).at(-1)).toMatchObject({ rect: [250, 350, 350, 450], add: true });
    await click(page, [600, 100]);
    await page.keyboard.press('Control+a');
    await expect.poll(() => selected(page)).toEqual([and.id, or.id].sort());
    expect((await sent(r, 'edit.select')).at(-1)).toMatchObject({ all: true });
  } finally {
    await r.close();
  }
});

test('the Attributes panel follows the selection with its facts (D-146): one part, several, pasted and not placed, none (the circuit\'s attributes, N-10)', async () => {
  const r = await launch();
  const { page } = r;
  try {
    const { and, or } = await twoGates(r);
    const panel = page.locator('.pbody.attributes');
    await tool(page, 'Edit').click();
    await click(page, [600, 100]);
    await expect(panel.locator('.aname')).toHaveText('main');
    await expect(panel.locator('.ahead .badge')).toHaveText('Circuit');
    await click(page, mid(and));
    await expect(panel.locator('.aname')).toHaveText('AND Gate');
    await expect(panel.locator('.afacts li')).toHaveText(['Location (300, 200)', 'Facing east']);
    await expect(panel.locator('.notice')).toHaveCount(0);
    await click(page, mid(or), ['Shift']);
    await expect(panel.locator('.aname')).toHaveText('2 components');
    await expect(panel.locator('.afacts li')).toHaveText(['AND Gate', 'OR Gate']);
    // copied and pasted: the paste floats (not in the circuit yet) and is what is selected
    await page.keyboard.press('Control+c');
    await page.keyboard.press('Control+v');
    await expect(panel.locator('.aname')).toHaveText('2 components');
    await click(page, [600, 100]);
    await expect(panel.locator('.aname')).toHaveText('main');
    await expect(panel.locator('.afacts')).toHaveCount(0);
  } finally {
    await r.close();
  }
});

test('the Edit tool moves (I-23..I-25): the parts dragged drawn where they go, the engine asked for the wires that would follow; one edit.move on release; Shift turns Keep Connections off', async () => {
  const r = await launch();
  const { page } = r;
  try {
    const { and } = await twoGates(r);
    await tool(page, 'Edit').click();
    await click(page, mid(and));
    await expect.poll(() => selected(page)).toEqual([and.id]);
    const a = await pagePoint(page, mid(and)), b = await pagePoint(page, [mid(and)[0] + 44, mid(and)[1] + 17]);
    await page.mouse.move(a.x, a.y);
    await expect(page.locator('.canvas-tip')).toHaveText('AND Gate');
    await page.mouse.down();
    await expect(page.locator('.canvas-tip')).toBeHidden();   // no tooltip over what is being dragged
    await page.mouse.move(b.x, b.y, { steps: 5 });
    await expect(page.locator('.canvas-tip')).toBeHidden();
    await expect.poll(async () => (await overlay(page)).ghost).toMatchObject({ dx: 40, dy: 20, look: 'move' });
    await expect.poll(async () => (await sent(r, 'model.movePreview')).at(-1)).toMatchObject({ dx: 40, dy: 20 });
    await page.mouse.up();
    await expect.poll(async () => (await sent(r, 'edit.move')).length).toBe(1);
    expect((await sent(r, 'edit.move'))[0]).toMatchObject({ dx: 40, dy: 20, connect: true });
    expect((await sent(r, 'edit.move'))[0].ids).toBeUndefined();   // the engine's selection
    await expect.poll(async () => (await parts(page, 'AND Gate'))[0]?.loc).toEqual([340, 220]);
    const moved = (await parts(page, 'AND Gate'))[0];
    await drag(page, mid(moved), [[mid(moved)[0] + 30, mid(moved)[1]]], { modifiers: ['Shift'] });
    await expect.poll(async () => (await sent(r, 'edit.move')).length).toBe(2);
    expect((await sent(r, 'edit.move'))[1]).toMatchObject({ dx: 30, dy: 0, connect: false });
  } finally {
    await r.close();
  }
});

test('keys on the selection (I-28..I-32, I-38..I-40, I-42): arrows, R, Shift+R, digits in a row, Alt+digit, Alt+arrow, F2, Insert, Delete; Ctrl+C, X, V, D (I-33..I-35); Backspace after a wire only (I-31)', async () => {
  const r = await launch();
  const { page } = r;
  try {
    const { and } = await twoGates(r);
    await tool(page, 'Edit').click();
    await click(page, mid(and));
    await expect.poll(() => selected(page)).toEqual([and.id]);
    for (const k of ['ArrowRight', 'ArrowUp', 'KeyR', 'Shift+KeyR', 'Digit1', 'Digit2', 'Alt+Digit8', 'Alt+ArrowLeft', 'Insert']) await page.keyboard.press(k);
    await expect.poll(async () => (await sent(r, 'edit.duplicate')).length).toBe(1);
    expect((await sent(r, 'edit.move')).map((m) => [m.dx, m.dy, m.connect])).toEqual([[10, 0, true], [0, -10, true]]);
    expect((await sent(r, 'edit.rotate')).map((m) => m.clockwise)).toEqual([true, false]);
    const keys = await sent(r, 'edit.keyConfig');
    expect(keys.map((k) => [k.key, k.alt, k.chain])).toEqual([['1', false, false], ['2', false, true], ['8', true, true], ['ArrowLeft', true, true]]);
    expect(keys[0].name).toBeUndefined();
    // Insert's copy floats; Delete takes it away (I-30: a paste or duplicate not dropped just goes)
    await expect.poll(async () => ((await overlay(page)).ghost as { look?: string } | undefined)?.look).toBe('float');
    await page.keyboard.press('Delete');
    await expect.poll(async () => (await overlay(page)).ghost ?? null).toBeNull();
    // F2: the label in place
    await click(page, [600, 100]);
    const now = (await parts(page, 'AND Gate'))[0];
    await click(page, mid(now));
    await page.keyboard.press('F2');
    await expect(page.locator('.canvas-view .inline-field')).toBeVisible();
    await page.keyboard.press('Escape');
    // the menu's keys, then Delete
    await page.keyboard.press('Control+c');
    await page.keyboard.press('Control+x');
    await page.keyboard.press('Control+v');
    await expect.poll(async () => (await sent(r, 'edit.paste')).length).toBe(1);
    expect((await sent(r, 'edit.copy')).length).toBe(1);
    expect((await sent(r, 'edit.cut')).length).toBe(1);
    await expect.poll(async () => ((await overlay(page)).ghost as { look?: string } | undefined)?.look).toBe('float');
    await page.locator('.canvas-view canvas').focus();
    await page.keyboard.press('Delete');
    await expect.poll(async () => (await sent(r, 'edit.delete')).length).toBe(2);
    await expect.poll(async () => (await overlay(page)).ghost ?? null).toBeNull();
    // Backspace with nothing selected: nothing, unless the last edit was a wire the tool drew
    await page.keyboard.press('Backspace');
    expect((await sent(r, 'edit.undo')).length).toBe(0);
    await tool(page, 'Wire').click();
    await drag(page, [100, 600], [[200, 600]]);
    await expect.poll(async () => (await wires(page)).length).toBe(1);
    await page.keyboard.press('Backspace');
    await expect.poll(async () => (await sent(r, 'edit.undo')).length).toBe(1);
    await page.keyboard.press('Backspace');
    expect((await sent(r, 'edit.undo')).length).toBe(1);
    // Ctrl+V takes the Edit tool (Logisim's paste)
    await page.keyboard.press('Control+v');
    await expect(tool(page, 'Edit')).toBeChecked();
  } finally {
    await r.close();
  }
});

test('wires (I-44..I-51, I-15, I-17): the green circle on a port or a wire, Alt turns it round; the Wire tool\'s dot; the L bends by the first move; shortening hides the wire; a click on a wiring point selects', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await page.locator('.canvas-view canvas').waitFor();
    await recordCalls(r.app);
    const pc = (await parts(page, 'Register')).find((k) => k.attrs.label === 'PC')!;
    const q = pc.ports[0].loc;
    await page.evaluate(([x, y]) => (window as unknown as { __hcsCanvas: { setView(v: object): void } }).__hcsCanvas.setView({ x: x - 200, y: y - 200, zoom: 1 }), q);
    await page.waitForTimeout(60);
    await hover(page, [q[0] + 2, q[1] + 3]);
    await expect.poll(async () => (await overlay(page)).dot).toEqual(q);
    await page.keyboard.down('Alt');
    await hover(page, [q[0] + 2, q[1] + 2]);
    await expect.poll(async () => (await overlay(page)).dot ?? null).toBeNull();   // Alt on a port selects
    await page.keyboard.up('Alt');
    // a drag from the port with the Edit tool: the L, horizontal first (the first move is sideways)
    const a = await pagePoint(page, q);
    await page.mouse.move(a.x, a.y);
    await page.mouse.down();
    for (const p of [[q[0] + 20, q[1]], [q[0] + 60, q[1] + 40]] as P[]) { const s = await pagePoint(page, p); await page.mouse.move(s.x, s.y, { steps: 3 }); }
    expect((await overlay(page)).wire).toEqual([q, [q[0] + 60, q[1]], [q[0] + 60, q[1] + 40]]);
    await page.mouse.up();
    await expect.poll(async () => (await sent(r, 'edit.addWire')).length).toBe(1);
    expect((await sent(r, 'edit.addWire'))[0]).toMatchObject({ points: [q, [q[0] + 60, q[1]], [q[0] + 60, q[1] + 40]], tool: 'edit' });
    // a click on the port (no move): a selection, not a wire
    await click(page, q);
    await expect.poll(async () => (await sent(r, 'edit.select')).length).toBe(1);
    expect((await sent(r, 'edit.select'))[0]).toMatchObject({ at: q });
    // the Wire tool: the grey dot at the snapped pointer; vertical first; from a wire's end back along it: shortened
    await tool(page, 'Wire').click();
    await hover(page, [q[0] - 104, q[1] + 3]);
    await expect.poll(async () => (await overlay(page)).cursorDot).toEqual([q[0] - 100, q[1]]);
    // the wire just drawn from the port: its right end (where the L bends) dragged back 20 along it
    const w = (await wires(page)).find((x) => x.a[1] === q[1] && x.b[1] === q[1] && Math.min(x.a[0], x.b[0]) === q[0] && Math.max(x.a[0], x.b[0]) === q[0] + 60)!;
    const [end, other]: P[] = [[q[0] + 60, q[1]], q];
    const e0 = await pagePoint(page, end);
    await page.mouse.move(e0.x, e0.y);
    await page.mouse.down();
    const e1 = await pagePoint(page, [end[0] - 20, end[1]]);
    await page.mouse.move(e1.x, e1.y, { steps: 3 });
    const o = await overlay(page);
    // the wire shortened is one that ends where the drag started and runs under the pointer (the drawn one, or the
    // fixture's wire along the same row: the fake does not merge them), only what is left of it drawn
    expect((o.hidden as string[]).length).toBe(1);
    expect((o.wire as P[])[0]).toEqual([end[0] - 20, end[1]]);
    expect(w.id).toMatch(/^w/);
    expect(other).toEqual(q);
    await page.keyboard.press('Escape');
    await page.mouse.up();
  } finally {
    await r.close();
  }
});

test('the Text tool (I-79..I-82) and a label in place (I-42, I-105): the field where the engine says, Enter applies, Esc does not, leaving it applies; a double click on a part edits its label, not going inside (I-114)', async () => {
  const r = await launch();
  const { page } = r;
  try {
    const { and, or } = await twoGates(r);
    await tool(page, 'Text').click();
    await click(page, [120, 520]);
    const field = page.locator('.canvas-view .inline-field');
    await expect(field).toBeVisible();
    await expect(field).toBeFocused();
    await field.fill('hello');
    await field.press('Enter');
    await expect.poll(async () => (await sent(r, 'edit.text')).at(-1)).toMatchObject({ loc: [120, 520], text: 'hello' });
    await expect(field).toHaveCount(0);
    // on the gate (no label yet: anywhere on it), Esc: nothing sent
    await click(page, mid(and));
    await expect(field).toBeVisible();
    await field.fill('x');
    await field.press('Escape');
    await expect(field).toHaveCount(0);
    expect((await sent(r, 'edit.text')).length).toBe(1);
    // leaving the field (a press elsewhere) applies it
    await click(page, mid(and));
    await field.fill('g1');
    await click(page, [600, 600]);
    await expect.poll(async () => (await sent(r, 'edit.text')).length).toBeGreaterThanOrEqual(2);
    expect((await sent(r, 'edit.text'))[1]).toMatchObject({ id: and.id, text: 'g1' });
    // the Edit tool's double click: the label field at the part's middle; Enter applies (edit.setAttr label)
    await page.keyboard.press('Escape');
    await tool(page, 'Edit').click();
    const s = await pagePoint(page, mid(or));
    await page.mouse.dblclick(s.x, s.y);
    await expect(field).toBeVisible();
    await field.fill('sum');
    await field.press('Enter');
    await expect.poll(async () => (await sent(r, 'edit.setAttr')).at(-1)).toMatchObject({ ids: [or.id], attr: 'label', value: 'sum' });
    await expect(page.locator('.canvas-crumbs')).toBeHidden();
  } finally {
    await r.close();
  }
});

test('an input pin (I-77, I-78): Ctrl+click pokes it (press and release), a double click asks for its value -- refused values keep the dialog with the reason and no character; the selection stays', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await page.locator('.canvas-view canvas').waitFor();
    await recordCalls(r.app);
    const pin = (await parts(page, 'Pin')).find((k) => k.attrs.label === 'ALUOp')!;
    await page.evaluate(([x, y]) => (window as unknown as { __hcsCanvas: { setView(v: object): void } }).__hcsCanvas.setView({ x: x - 200, y: y - 200, zoom: 1 }), pin.loc);
    await page.waitForTimeout(60);
    const before = await selected(page);
    await click(page, mid(pin), ['Control']);
    await expect.poll(async () => (await sent(r, 'sim.poke')).map((p) => p.action)).toEqual(['press', 'release']);
    expect((await sent(r, 'sim.poke'))[0]).toMatchObject({ componentId: pin.id });
    expect((await sent(r, 'edit.select')).length).toBe(0);
    expect(await selected(page)).toEqual(before);
    const s = await pagePoint(page, mid(pin));
    await page.mouse.dblclick(s.x, s.y);
    const dialog = page.locator('dialog.pinvalue');
    await expect(dialog).toBeVisible();
    await expect(dialog.locator('h2')).toHaveText('Set Pin Value');
    await expect(dialog.locator('.fieldname')).toHaveText('Pin ALUOp');
    await dialog.getByRole('textbox').fill('0x1F');
    await page.keyboard.press('Enter');
    await expect(dialog.getByRole('alert')).toHaveText(`그 값은 이 핀(${pin.attrs.width} bits)에 넣을 수 없습니다.`);
    expect(await visibleCharacters(page)).toBe(0);
    await dialog.getByRole('textbox').fill('0b10');
    await page.keyboard.press('Enter');
    await expect(dialog).toHaveCount(0);
    expect((await sent(r, 'sim.pinValue')).map((p) => p.value)).toEqual(['0x1F', '0b10']);
  } finally {
    await r.close();
  }
});

test('Electron\'s own keys and mouse do nothing (I-206..I-213): no reload, no developer tools, no page zoom by keys or Ctrl+wheel; the middle button, Space and the right button stay the Canvas\'s; Hangul being composed is no key', async () => {
  const r = await launch();
  const { page, app } = r;
  try {
    const { and } = await twoGates(r);
    await tool(page, 'Edit').click();
    await click(page, mid(and));
    await expect.poll(() => selected(page)).toEqual([and.id]);
    await page.evaluate(() => { (window as unknown as { marker: number }).marker = 42; });
    for (const k of ['Control+Shift+R', 'Control+Shift+I', 'F12', 'Control+0', 'Control+Equal', 'Control+Minus', 'Alt']) await page.keyboard.press(k);
    const wc = () => app.evaluate(({ BrowserWindow, Menu }) => {
      const w = BrowserWindow.getAllWindows()[0].webContents;
      return { zoom: w.getZoomFactor(), devtools: w.isDevToolsOpened(), menu: Menu.getApplicationMenu() === null };
    });
    expect(await wc()).toEqual({ zoom: 1, devtools: false, menu: true });
    expect(await page.evaluate(() => (window as unknown as { marker?: number }).marker)).toBe(42);   // not reloaded
    // Ctrl+wheel over a panel (not the Canvas): the page stays at 100 %
    const side = await page.locator('.panel.upper').boundingBox();
    await page.mouse.move(side!.x + 40, side!.y + 80);
    await page.keyboard.down('Control');
    await page.mouse.wheel(0, -400);
    await page.keyboard.up('Control');
    expect((await wc()).zoom).toBe(1);
    expect(await page.evaluate(() => window.visualViewport?.scale ?? 1)).toBe(1);
    // the middle button, Space and the right button on the Canvas: the browser's own actions never happen
    // (what reaches the document after the Canvas had it: prevented)
    await page.evaluate(() => {
      const w = window as unknown as { seen: Record<string, boolean> };
      w.seen = {};
      document.addEventListener('pointerdown', (e) => { if (e.button === 1) w.seen.middle = e.defaultPrevented; });
      document.addEventListener('keydown', (e) => { if (e.code === 'Space') w.seen.space = e.defaultPrevented; });
      document.addEventListener('contextmenu', (e) => { w.seen.menu = e.defaultPrevented; });
    });
    const box = (await page.locator('.canvas-view canvas').boundingBox())!;
    const spot = { x: box.x + 30, y: box.y + 30 };
    await page.mouse.move(spot.x, spot.y);
    await page.mouse.down({ button: 'middle' });
    await page.mouse.up({ button: 'middle' });
    await page.keyboard.down('Space');
    await page.keyboard.up('Space');
    await page.mouse.click(spot.x, spot.y, { button: 'right' });
    const events = await page.evaluate(() => (window as unknown as { seen: Record<string, boolean> }).seen);
    expect(events).toEqual({ middle: true, space: true, menu: true });
    // a key while Hangul is being composed is no key (I-212): the selection is not deleted
    await page.evaluate(() => {
      const c = document.querySelector('.canvas-view canvas') as HTMLCanvasElement;
      c.dispatchEvent(new KeyboardEvent('keydown', { key: 'Delete', code: 'Delete', isComposing: true, bubbles: true, cancelable: true }));
    });
    await page.waitForTimeout(100);
    expect((await sent(r, 'edit.delete')).length).toBe(0);
  } finally {
    await r.close();
  }
});

test('the view\'s keys (I-122..I-125): Ctrl+1 is 100 %, Ctrl+0 fits, F fits the selection, Space with a drag pans', async () => {
  const r = await launch();
  const { page } = r;
  try {
    const { and } = await twoGates(r);
    await tool(page, 'Edit').click();
    const view = () => page.evaluate(() => ({ ...(window as unknown as { __hcsCanvas: { view: { x: number; y: number; zoom: number } } }).__hcsCanvas.view }));
    await page.keyboard.press('Control+0');
    await expect.poll(async () => (await view()).zoom).not.toBe(1);
    await page.keyboard.press('Control+1');
    await expect.poll(async () => (await view()).zoom).toBe(1);
    await click(page, mid(and));
    await expect.poll(() => selected(page)).toEqual([and.id]);
    await page.keyboard.press('KeyF');
    // fitted to the one gate: at most 200 %, the gate in the middle
    await expect.poll(async () => (await view()).zoom).toBe(2);
    const box = (await page.locator('.canvas-view canvas').boundingBox())!;
    const v = await view();
    expect(Math.abs(v.x + box.width / 2 / v.zoom - mid(and)[0])).toBeLessThan(1);
    // Space held, a left-button drag: the view follows the pointer
    const before = await view();
    await page.keyboard.down('Space');
    await page.mouse.move(box.x + 300, box.y + 300);
    await page.mouse.down();
    await page.mouse.move(box.x + 200, box.y + 250, { steps: 4 });
    await page.mouse.up();
    await page.keyboard.up('Space');
    const after = await view();
    expect(after.x).toBeCloseTo(before.x + 100 / before.zoom, 3);
    expect(after.y).toBeCloseTo(before.y + 50 / before.zoom, 3);
    expect(await selected(page)).toEqual([and.id]);   // panning did not select anything
  } finally {
    await r.close();
  }
});

test('the Canvas takes the keys when the pointer comes over it (I-02), not from a field being typed in', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await page.locator('.canvas-view canvas').waitFor();
    await page.getByRole('button', { name: /Load Program/ }).focus();
    await hover(page, [100, 100]);
    await expect(page.locator('.canvas-view canvas')).toBeFocused();
    await page.evaluate(() => { const i = document.createElement('input'); i.id = 'probe-field'; document.body.append(i); i.focus(); });
    await page.mouse.move(2, 2);
    await hover(page, [120, 120]);
    await expect(page.locator('#probe-field')).toBeFocused();
  } finally {
    await r.close();
  }
});
