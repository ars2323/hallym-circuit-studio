/* Editing with the real engine (N-08, D-146): the tools on the Canvas send
   intents and the engine runs Logisim's own tool code.  The core flows end
   to end -- parts from the toolbar and hcs:place-tool, wires with the
   Wire tool (the L's bend by the first move) and from a port with the Edit
   tool, a part dragged with its wires following, Undo and Redo, Copy and
   a floating Paste dragged and dropped -- and the .circ saved after them
   is the one the engine saves for the same intents sent to a new engine
   (the window adds nothing of its own to the model).  Opt-in, as it needs
   the engine built:

     ./gradlew :engine:stage
     HCS_E2E_REAL_ENGINE=1 xvfb-run -a npx playwright test real-engine-edit */

import { expect, type Page, test } from '@playwright/test';
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';

import { answerSave, command, DATAPATH, launch, type LaunchOptions, newCircuit, openFile, recordCalls, repo, sample, sentCalls, visibleCharacters } from './harness.ts';
import { drag, hover, overlay, parts, placeTool, sameNet, segments, selected, tool, where, wires } from './edit-points.ts';
import { call, fileModel, killEngine, openFileIds } from './model.ts';

const JAR = path.join(repo, 'engine/build/stage/hcs-engine.jar');
const real: LaunchOptions['env'] = { HCS_ENGINE_CMD: '', HCS_ENGINE_JAR: JAR };

test.skip(process.env.HCS_E2E_REAL_ENGINE !== '1', 'opt-in: HCS_E2E_REAL_ENGINE=1 with engine/build/stage built');

// The file's lines, the wires and parts of each circuit in any order (Logisim writes them in a HashSet's order; D-006).
const normalized = (text: string): string[] => text.split('\n').map((l) => l.trim()).filter(Boolean).sort();

async function saveAs(r: { app: Parameters<typeof answerSave>[0]; page: Page }, file: string): Promise<string> {
  await answerSave(r.app, file);
  await r.page.keyboard.press('Control+s');
  await expect(r.page.locator('.status .ok')).toContainText(path.basename(file));
  return readFileSync(file, 'utf8');
}

test('the core flows: parts placed, wires drawn, a part moved with its wires, Undo and Redo, Copy and a floating Paste; the saved .circ is the engine\'s own for those intents', async () => {
  expect(existsSync(JAR), `${JAR}: ./gradlew :engine:stage`).toBe(true);
  const r = await launch(undefined, { env: real });
  const { page } = r;
  try {
    await newCircuit(r);
    await recordCalls(r.app);
    // Pin from the toolbar: its ghost follows the pointer (model.tool), a click places it, then the Edit tool with it selected
    await command(page, 'Pin');
    await page.locator('.canvas-view canvas').waitFor();
    await hover(page, [104, 97]);
    await expect.poll(async () => ((await overlay(page)).ghost as { parts?: { name: string }[] } | undefined)?.parts?.[0]?.name).toBe('Pin');
    expect(((await overlay(page)).ghost as { dx: number; dy: number })).toMatchObject({ dx: 100, dy: 100, look: 'place' });
    await page.mouse.down();
    await page.mouse.up();
    await expect.poll(async () => (await parts(page, 'Pin')).length).toBe(1);
    await expect(tool(page, 'Edit')).toBeChecked();
    const pin1 = (await parts(page, 'Pin'))[0];
    expect(pin1.loc).toEqual([100, 100]);
    await expect.poll(() => selected(page)).toEqual([pin1.id]);
    await command(page, 'Pin');
    await hover(page, [100, 200]);
    await page.mouse.down();
    await page.mouse.up();
    await expect.poll(async () => (await parts(page, 'Pin')).length).toBe(2);
    const pin2 = (await parts(page, 'Pin')).find((k) => k.loc[1] === 200)!;
    // an AND Gate through hcs:place-tool (the Components list, N-12): held, then placed with a click
    const w = await where(page);
    expect(await placeTool(page, { ...w, lib: 'Gates', name: 'AND Gate', source: 'components' })).toBe(true);
    await hover(page, [300, 150]);
    await page.mouse.down();
    await page.mouse.up();
    await expect.poll(async () => (await parts(page, 'AND Gate')).length).toBe(1);
    const and = (await parts(page, 'AND Gate'))[0];
    const inputs = and.ports.filter((q) => q.dir === 'in').sort((a, b) => a.loc[1] - b.loc[1]);
    const top = inputs[0].loc, bottom = inputs[inputs.length - 1].loc;
    // the Wire tool: first move to the right, so the bend is at the start's height (horizontal first, I-47)
    await command(page, 'Wire');
    await drag(page, [100, 100], [[150, 100], [top[0], top[1]]]);
    await expect.poll(async () => segments(await wires(page))).toEqual(segments([
      { id: '', a: [100, 100], b: [top[0], 100] }, { id: '', a: [top[0], 100], b: [top[0], top[1]] },
    ]));
    // from the second pin with the Edit tool on its port (a wiring point, I-44): first move down, so vertical first
    await command(page, 'Edit');
    await drag(page, [100, 200], [[100, 190], [bottom[0], bottom[1]]]);
    await expect.poll(async () => (await wires(page)).length).toBe(4);
    expect(segments(await wires(page))).toContain(segments([{ id: '', a: [100, bottom[1]], b: [100, 200] }])[0]);
    expect(await sameNet(page, [pin1.id, 0], [and.id, inputs[0].i])).toBe(true);
    expect(await sameNet(page, [pin2.id, 0], [and.id, inputs[inputs.length - 1].i])).toBe(true);
    // drag the AND gate by its body: its wires follow (v1 SafeMove), still one net each
    const body: [number, number] = [and.bounds[0] + and.bounds[2] / 2, and.bounds[1] + and.bounds[3] / 2];
    await drag(page, body, [[body[0] + 30, body[1]], [body[0] + 50, body[1] + 20]]);
    await expect.poll(async () => (await parts(page, 'AND Gate'))[0]?.loc).toEqual([350, 170]);
    const moved = (await parts(page, 'AND Gate'))[0];
    const movedIn = moved.ports.filter((q) => q.dir === 'in').sort((a, b) => a.loc[1] - b.loc[1]);
    expect(await sameNet(page, [pin1.id, 0], [moved.id, movedIn[0].i])).toBe(true);
    expect(await sameNet(page, [pin2.id, 0], [moved.id, movedIn[movedIn.length - 1].i])).toBe(true);
    // Undo, Redo (the toolbar's keys)
    await page.keyboard.press('Control+z');
    await expect.poll(async () => (await parts(page, 'AND Gate'))[0]?.loc).toEqual([300, 150]);
    await page.keyboard.press('Control+y');
    await expect.poll(async () => (await parts(page, 'AND Gate'))[0]?.loc).toEqual([350, 170]);
    // Copy the gate, Paste: it floats (not in the circuit yet) until dragged, then it is in
    const again = (await parts(page, 'AND Gate'))[0];
    const mid: [number, number] = [again.bounds[0] + again.bounds[2] / 2, again.bounds[1] + again.bounds[3] / 2];
    await drag(page, mid, [mid]);   // a click on it: selected
    await expect.poll(() => selected(page)).toEqual([again.id]);
    await page.keyboard.press('Control+c');
    await page.keyboard.press('Control+v');
    await expect.poll(async () => ((await overlay(page)).ghost as { look?: string } | undefined)?.look).toBe('float');
    const floating = ((await overlay(page)).ghost as { parts: { bounds: number[] }[] }).parts[0];
    expect((await parts(page, 'AND Gate')).length).toBe(1);
    const fmid: [number, number] = [floating.bounds[0] + floating.bounds[2] / 2, floating.bounds[1] + floating.bounds[3] / 2];
    await drag(page, fmid, [[fmid[0], fmid[1] + 100], [fmid[0], fmid[1] + 150]]);
    await expect.poll(async () => (await parts(page, 'AND Gate')).length).toBe(2);
    const calls = await sentCalls(r.app);
    // the same intents in a new engine: the same file
    const saved = await saveAs(r, path.join(r.dir, 'drawn.circ'));
    const intents = calls.filter((c) => c.method.startsWith('edit.'));
    expect(intents.map((c) => c.method)).toEqual(expect.arrayContaining(['edit.addComponent', 'edit.addWire', 'edit.move', 'edit.undo', 'edit.redo', 'edit.select', 'edit.copy', 'edit.paste']));
    await r.close();
    const r2 = await launch(undefined, { env: real });
    try {
      await newCircuit(r2);
      await r2.page.locator('.canvas').waitFor();
      for (const c of intents) await call(r2.page, c.method, c.params);
      const replayed = await saveAs(r2, path.join(r2.dir, 'replayed.circ'));
      expect(normalized(saved.replaceAll('drawn', 'X'))).toEqual(normalized(replayed.replaceAll('replayed', 'X')));
    } finally {
      await r2.close();
    }
  } finally {
    await r.close().catch(() => {});
  }
});

// A part's middle.
const mid = (k: { bounds: number[] }): [number, number] => [k.bounds[0] + k.bounds[2] / 2, k.bounds[1] + k.bounds[3] / 2];

test('the Edit tool\'s gestures and keys, the Wire and Text tools, a label in place, a pin\'s value; killed after them, the replayed model is the same', async () => {
  const r = await launch(undefined, { env: real });
  const { page } = r;
  try {
    await newCircuit(r);
    const place = async (lib: string, name: string, at: [number, number]) => {
      expect(await placeTool(page, { ...(await where(page).catch(() => ({ fileId: '', circuitId: '' }))), lib, name, source: 'components' })).toBe(true);
      await hover(page, at);
      await page.mouse.down();
      await page.mouse.up();
    };
    await command(page, 'Pin');
    await page.locator('.canvas-view canvas').waitFor();
    // a placing tool's keys (I-58, I-59): an arrow turns the ghost, a digit goes to its configurator
    await hover(page, [300, 300]);
    await page.keyboard.press('ArrowLeft');
    await expect.poll(async () => ((await overlay(page)).ghost as { parts: { facing: string }[] } | undefined)?.parts[0]?.facing).toBe('west');
    await page.keyboard.press('ArrowRight');
    await expect.poll(async () => ((await overlay(page)).ghost as { parts: { facing: string }[] } | undefined)?.parts[0]?.facing).toBe('east');
    await page.mouse.down();
    await page.mouse.up();
    await expect.poll(async () => (await parts(page, 'Pin')).length).toBe(1);
    await place('Gates', 'AND Gate', [500, 300]);
    await place('Gates', 'OR Gate', [500, 500]);
    await expect.poll(async () => (await parts(page)).length).toBe(3);
    const pin = (await parts(page, 'Pin'))[0], and = (await parts(page, 'AND Gate'))[0], or = (await parts(page, 'OR Gate'))[0];
    // click selects one; Shift+click adds (I-09, I-10); a click on nothing drops the selection (I-12)
    await drag(page, mid(and), [mid(and)]);
    await expect.poll(() => selected(page)).toEqual([and.id]);
    await drag(page, mid(or), [mid(or)], { modifiers: ['Shift'] });
    await expect.poll(() => selected(page)).toEqual([and.id, or.id].sort());
    await drag(page, mid(or), [mid(or)], { modifiers: ['Shift'] });
    await expect.poll(() => selected(page)).toEqual([and.id]);
    await drag(page, [700, 100], [[700, 100]]);
    await expect.poll(() => selected(page)).toEqual([]);
    // the rectangle (I-13): the rubber band while dragging, what is wholly inside after
    const a = await import('./canvas-points.ts').then((m) => m.pagePoint(page, [420, 250]));
    await page.mouse.move(a.x, a.y);
    await page.mouse.down();
    const b = await import('./canvas-points.ts').then((m) => m.pagePoint(page, [560, 560]));
    await page.mouse.move(b.x, b.y, { steps: 5 });
    await expect.poll(async () => (await overlay(page)).rubber).toEqual({ x0: 420, y0: 250, x1: 560, y1: 560 });
    await page.mouse.up();
    await expect.poll(() => selected(page)).toEqual([and.id, or.id].sort());
    // a digit and Alt+digit on the selection (I-38, I-39: the gates' inputs, then their bits), one undo step each
    await page.keyboard.press('Digit3');
    await expect.poll(async () => (await parts(page, 'AND Gate'))[0]?.attrs.inputs).toBe('3');
    expect((await parts(page, 'OR Gate'))[0].attrs.inputs).toBe('3');
    await page.keyboard.press('Alt+Digit8');
    await expect.poll(async () => (await parts(page, 'AND Gate'))[0]?.attrs.width).toBe('8');
    // R turns them clockwise, Shift+R back (I-29); the arrows move them one grid step (I-28)
    await page.keyboard.press('KeyR');
    await expect.poll(async () => (await parts(page, 'AND Gate'))[0]?.facing).toBe('south');
    await page.keyboard.press('Shift+KeyR');
    await expect.poll(async () => (await parts(page, 'AND Gate'))[0]?.facing).toBe('east');
    await page.keyboard.press('ArrowDown');
    await expect.poll(async () => (await parts(page, 'AND Gate'))[0]?.loc).toEqual([500, 310]);
    // Ctrl+D (I-32): copies of both, moved on by v1's SafeDuplicate until they touch nothing (so dropped into the
    // circuit already) and selected; Delete (I-30) takes the selected copies away again
    await page.keyboard.press('Control+d');
    await expect.poll(async () => (await parts(page, 'AND Gate')).length).toBe(2);
    expect((await parts(page, 'OR Gate')).length).toBe(2);
    await expect.poll(async () => (await selected(page)).length).toBe(2);
    expect(await selected(page)).not.toContain((await parts(page, 'AND Gate')).find((k) => k.loc[1] === 310)!.id);
    await page.keyboard.press('Delete');
    await expect.poll(async () => (await parts(page, 'AND Gate')).length).toBe(1);
    expect((await parts(page, 'OR Gate')).length).toBe(1);
    // the Wire tool: a press and a release without moving draws nothing (I-46); a drag draws; Backspace takes it back (I-31)
    await command(page, 'Wire');
    await drag(page, [100, 600], [[100, 600]]);
    await drag(page, [100, 600], [[200, 600]]);
    await expect.poll(async () => (await wires(page)).length).toBe(1);
    await page.keyboard.press('Backspace');
    await expect.poll(async () => (await wires(page)).length).toBe(0);
    await drag(page, [100, 600], [[250, 600]]);
    await expect.poll(async () => (await wires(page)).length).toBe(1);
    // shortening: from its end back along it (I-50) -- the rest stays; to its other end, it goes
    await drag(page, [250, 600], [[200, 600]]);
    await expect.poll(async () => segments(await wires(page))).toEqual(['100,600-200,600']);
    // the Edit tool: a click on the wire selects it (I-15), a drag across it moves it with no wire pulled from it
    await command(page, 'Edit');
    await drag(page, [150, 600], [[150, 600]]);
    await expect.poll(async () => (await selected(page)).length).toBe(1);
    // the Text tool: a click on nothing opens a field for a new Label (I-80), Enter adds it
    await command(page, 'Text');
    await drag(page, [300, 400], [[300, 400]]);
    const field = page.locator('.canvas-view .inline-field');
    await expect(field).toBeVisible();
    await field.fill('hello');
    await field.press('Enter');
    await expect.poll(async () => (await parts(page, 'Text'))[0]?.attrs.text).toBe('hello');
    // F2 on the selected pin: its label in place (I-42, I-105); Esc closes it without a change, Enter applies
    await command(page, 'Edit');
    await drag(page, mid(pin), [mid(pin)]);
    await expect.poll(() => selected(page)).toEqual([pin.id]);
    await page.locator('.canvas-view canvas').focus();
    await page.keyboard.press('F2');
    await expect(field).toBeVisible();
    await field.fill('nope');
    await field.press('Escape');
    await expect(field).toHaveCount(0);
    expect((await parts(page, 'Pin'))[0].attrs.label).toBe('');
    // a double click on the OR gate: its label field (the Edit tool does not go into anything, I-114)
    const orNow = (await parts(page, 'OR Gate'))[0];
    const q = await import('./canvas-points.ts').then((m) => m.pagePoint(page, mid(orNow)));
    await page.mouse.dblclick(q.x, q.y);
    await expect(field).toBeVisible();
    await field.fill('sum');
    await field.press('Enter');
    await expect.poll(async () => (await parts(page, 'OR Gate'))[0]?.attrs.label).toBe('sum');
    // a double click on the input pin: Set Pin Value (I-78); too wide is refused in the dialog, 1 is taken
    const p1 = await import('./canvas-points.ts').then((m) => m.pagePoint(page, mid(pin)));
    await page.mouse.dblclick(p1.x, p1.y);
    const dialog = page.locator('dialog.pinvalue');
    await expect(dialog).toBeVisible();
    await dialog.getByRole('textbox').fill('0x1F');
    await dialog.getByRole('button', { name: 'Set' }).click();
    await expect(dialog.locator('.hint.err')).toContainText('1 bits');
    expect(await visibleCharacters(page)).toBe(0);
    await dialog.getByRole('textbox').fill('1');
    await dialog.getByRole('button', { name: 'Set' }).click();
    await expect(dialog).toHaveCount(0);
    await expect.poll(async () => page.evaluate((id) => (window as unknown as { __hcsCanvas: { scene: { portValue(k: string, i: number): string | undefined } } }).__hcsCanvas.scene.portValue(id, 0), pin.id)).toBe('1');
    // Ctrl+click on the pin pokes it (I-77) as the Poke tool would: a new pin is three-state, so 1 → x; the selection stays
    const before = await selected(page);
    await page.keyboard.down('Control');
    await page.mouse.click(p1.x, p1.y);
    await page.keyboard.up('Control');
    await expect.poll(async () => page.evaluate((id) => (window as unknown as { __hcsCanvas: { scene: { portValue(k: string, i: number): string | undefined } } }).__hcsCanvas.scene.portValue(id, 0), pin.id)).toBe('x');
    expect(await selected(page)).toEqual(before);

    // killed now: every intent above is in the journal and the replayed model is the same (D-142)
    const [fileId] = await openFileIds(r.app);
    const model = await fileModel(page, fileId);
    await killEngine(r.app);
    await expect(page.locator('dialog.ask')).toContainText('다시 적용했습니다');
    await page.locator('dialog.ask').getByRole('button', { name: 'Close' }).click();
    expect(await fileModel(page, fileId)).toEqual(model);
  } finally {
    await r.close();
  }
});

test('a paste still floating when another circuit is shown: dropped where it was pasted (the window\'s clear, an edit), the model told; killed after, the replayed model is the same (D-146)', async () => {
  const r = await launch(undefined, { env: real });
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await page.locator('.canvas-view canvas').waitFor();
    await expect.poll(async () => (await parts(page, 'Adder')).length).toBe(1);
    await recordCalls(r.app);
    const [fileId] = await openFileIds(r.app);
    const byName = async () => fileModel(page, fileId) as Promise<Record<string, { comps: string[] }>>;
    const adders = (m: Record<string, { comps: string[] }>, circuit: string) => m[circuit].comps.filter((c) => c.includes('Adder')).length;
    const before = await byName();
    await command(page, 'Edit');
    const adder = (await parts(page, 'Adder'))[0];
    await drag(page, mid(adder), [mid(adder)]);
    await expect.poll(() => selected(page)).toEqual([adder.id]);
    await page.keyboard.press('Control+c');
    await page.keyboard.press('Control+v');
    // the paste floats: nothing in the circuit yet, the Attributes panel says it is the selection
    await expect.poll(() => selected(page)).toEqual([]);
    await expect(page.locator('.pbody.attributes .aname')).toHaveText('Adder');
    expect(adders(await byName(), 'main')).toBe(adders(before, 'main'));
    // another circuit on show: the window drops the paste into main first
    await page.getByRole('tab', { name: 'Circuits' }).click();
    await page.locator('.upper .pbody:visible .list > li', { hasText: 'alu' }).getByRole('button').click();
    await expect(page.locator('.circuitbar .ptab.on')).toHaveText('alu');
    await expect.poll(async () => adders(await byName(), 'main')).toBe(adders(before, 'main') + 1);
    expect(adders(await byName(), 'alu')).toBe(adders(before, 'alu'));
    const sent = (await sentCalls(r.app)).map((c) => c.method);
    expect(sent.indexOf('edit.select', sent.indexOf('edit.paste'))).toBeLessThan(sent.lastIndexOf('sim.watch'));
    // killed now: the drop is in the journal (an edit), the replayed model is the same (D-142)
    const model = await fileModel(page, fileId);
    await killEngine(r.app);
    await expect(page.locator('dialog.ask')).toContainText('다시 적용했습니다');
    await page.locator('dialog.ask').getByRole('button', { name: 'Close' }).click();
    expect(await fileModel(page, fileId)).toEqual(model);
  } finally {
    await r.close();
  }
});

test('Undo and Redo on another circuit\'s tab: the circuit on show goes with them (circuitId), an id-less key after; killed, the replayed model is the same (D-146)', async () => {
  const r = await launch(undefined, { env: real });
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await page.locator('.canvas-view canvas').waitFor();
    await expect.poll(async () => (await parts(page, 'Adder')).length).toBe(1);
    await recordCalls(r.app);
    const [fileId] = await openFileIds(r.app);
    await tool(page, 'Edit').click();
    const adder = (await parts(page, 'Adder'))[0];
    await drag(page, mid(adder), [mid(adder)]);
    await expect.poll(() => selected(page)).toEqual([adder.id]);
    await page.locator('.canvas-view canvas').focus();
    await page.keyboard.press('ArrowDown');   // edit.move without ids: the engine's selection
    await expect.poll(async () => (await parts(page, 'Adder'))[0]?.loc[1]).toBe(adder.loc[1] + 10);
    const mainId = (await where(page)).circuitId;
    // the alu tab: Ctrl+Z and Ctrl+Y there send the circuit on show
    await page.getByRole('tab', { name: 'Circuits' }).click();
    await page.locator('.upper .pbody:visible .list > li', { hasText: 'alu' }).getByRole('button').click();
    await expect(page.locator('.circuitbar .ptab.on')).toHaveText('alu');
    // the Canvas's scene follows the tab a moment later (its snapshot on its way)
    await expect.poll(async () => (await where(page)).circuitId).not.toBe(mainId);
    const aluId = (await where(page)).circuitId;
    await page.keyboard.press('Control+z');
    await expect.poll(async () => (await sentCalls(r.app, 'edit.undo')).length).toBe(1);
    await page.keyboard.press('Control+y');
    await expect.poll(async () => (await sentCalls(r.app, 'edit.redo')).length).toBe(1);
    expect((await sentCalls(r.app, 'edit.undo'))[0].params).toMatchObject({ fileId, circuitId: aluId });
    expect((await sentCalls(r.app, 'edit.redo'))[0].params).toMatchObject({ fileId, circuitId: aluId });
    // back on main: the undo on the alu tab dropped the selection (Logisim clears it when the circuit changes);
    // the adder chosen again, R turns the engine's selection (no ids)
    await page.locator('.upper .pbody:visible .list > li', { hasText: 'main' }).getByRole('button').click();
    await expect(page.locator('.circuitbar .ptab.on')).toHaveText('main');
    await expect.poll(async () => (await where(page)).circuitId).toBe(mainId);
    await expect.poll(() => selected(page)).toEqual([]);
    const now = (await parts(page, 'Adder'))[0];
    await drag(page, mid(now), [mid(now)]);
    await expect.poll(() => selected(page)).toEqual([now.id]);
    await page.locator('.canvas-view canvas').focus();
    await page.keyboard.press('KeyR');
    await expect.poll(async () => (await sentCalls(r.app, 'edit.rotate')).length).toBe(1);
    expect((await sentCalls(r.app, 'edit.rotate'))[0].params).not.toHaveProperty('ids');
    // killed now: the undo and redo carry their circuit in the journal; the replayed model is the same (D-142)
    const model = await fileModel(page, fileId);
    await killEngine(r.app);
    await expect(page.locator('dialog.ask')).toContainText('다시 적용했습니다');
    await page.locator('dialog.ask').getByRole('button', { name: 'Close' }).click();
    expect(await fileModel(page, fileId)).toEqual(model);
  } finally {
    await r.close();
  }
});
