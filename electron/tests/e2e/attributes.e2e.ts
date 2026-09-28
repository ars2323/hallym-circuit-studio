/* The Attributes panel, Quick Attributes and the right-click menus (N-10,
   D-157; docs/interaction-parity.md I-20, I-53, I-54, I-83, I-85..I-105,
   I-174, I-186, I-190, I-211; v1-feature-parity B-03, B-04, S-04, S-25,
   Y-05), with the fake engine: its rows and menu facts are the real
   engine's (tests/fixtures/attributes.json), its values its own; what the
   window sends is read back.  The real engine's own rules on the model:
   real-engine-attributes.e2e.ts. */

import { expect, type Page, test } from '@playwright/test';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';

import { centerOn, pagePoint, partBy } from './canvas-points.ts';
import { drag, hover, parts, placeTool, selected, tool, where, wires } from './edit-points.ts';
import { answerOpen, answerSave, canvasSettled, DATAPATH, launch, newCircuit, openFile, recordCalls, type Running, sample, sentCalls, visibleCharacters } from './harness.ts';

type P = [number, number];
const mid = (k: { bounds: number[] }): P => [k.bounds[0] + k.bounds[2] / 2, k.bounds[1] + k.bounds[3] / 2];
const sent = async (r: Running, method: string) => (await sentCalls(r.app, method)).map((c) => c.params);
const click = (page: Page, p: P, modifiers: ('Shift' | 'Alt' | 'Control')[] = []) => drag(page, p, [p], { modifiers });
async function rightClick(page: Page, p: P): Promise<void> {
  const q = await pagePoint(page, p);
  await page.mouse.click(q.x, q.y, { button: 'right' });
}
const menu = (page: Page) => page.locator('.ovmenu').first();
const item = (page: Page, name: string) => page.locator('.ovmenu button', { hasText: name }).first();

// A new circuit with parts placed the way the Components list holds them (the fake's 30 × 30 boxes).
async function place(r: Running, list: [string, string, P, Record<string, string>?][]): Promise<Record<string, { id: string; bounds: number[] }>> {
  const { page } = r;
  await newCircuit(r);
  await recordCalls(r.app);
  await tool(page, 'Pin').click();
  await page.locator('.canvas-view canvas').waitFor();
  const w = await where(page);
  const out: Record<string, { id: string; bounds: number[] }> = {};
  for (const [lib, name, at, attrs] of list) {
    const before = (await parts(page, name)).length;
    expect(await placeTool(page, { ...w, lib, name, source: 'components', ...(attrs ? { attrs } : {}) })).toBe(true);
    await hover(page, at);
    await page.mouse.down();
    await page.mouse.up();
    await expect.poll(async () => (await parts(page, name)).length).toBe(before + 1);
    const all = await parts(page, name);
    out[`${name}${before ? before + 1 : ''}`] = all.find((k) => k.loc[0] === at[0] && k.loc[1] === at[1]) ?? all[all.length - 1];
  }
  return out;
}

test('the Attributes panel (I-99..I-101, I-20, I-53, I-54, I-83; B-04, Y-05): the original table in the Inspector form -- the selection\'s, the circuit\'s, the tool\'s -- an editor per kind, one intent per change, a refused value in red with a Korean sentence', async () => {
  const r = await launch();
  const { page } = r;
  try {
    const p = await place(r, [['Gates', 'AND Gate', [300, 200]], ['Gates', 'OR Gate', [300, 400]], ['Wiring', 'Constant', [500, 200]]]);
    const panel = page.locator('.pbody.attributes');
    await tool(page, 'Edit').click();
    // one part: its name and the facts (N-08) over the table
    await click(page, mid(p['AND Gate']));
    await expect(panel.locator('.aname')).toHaveText('AND Gate');
    await expect(panel.locator('.ahead .badge')).toHaveText('Selection');
    await expect(panel.locator('.afacts li')).toHaveText(['Location (300, 200)', 'Facing east']);
    await expect(panel.locator('.atable tbody th')).toHaveText(['Facing', 'Data Bits', 'Gate Size', 'Number Of Inputs', 'Output Value', 'Label', 'Label Font',
      'Negate 1 (Top)', 'Negate 2', 'Negate 3', 'Negate 4', 'Negate 5 (Bottom)']);
    // a list: at once, on the selection (edit.setAttr without ids: the engine's selection, AttrTableSelectionModel)
    await panel.getByLabel('Number Of Inputs').selectOption('3');
    await expect.poll(async () => (await sent(r, 'edit.setAttr')).at(-1)).toMatchObject({ attr: 'inputs', value: '3' });
    expect((await sent(r, 'edit.setAttr')).at(-1)).not.toHaveProperty('ids');
    await expect(panel.getByLabel('Number Of Inputs')).toHaveValue('3');
    // a field: Enter applies
    await panel.getByLabel('Label', { exact: true }).fill('G1');
    await panel.getByLabel('Label', { exact: true }).press('Enter');
    await expect.poll(async () => (await sent(r, 'edit.setAttr')).at(-1)).toMatchObject({ attr: 'label', value: 'G1' });
    // the font: family, style and size as the .circ's text
    await panel.getByLabel('Label Font style').selectOption('bold');
    await expect.poll(async () => (await sent(r, 'edit.setAttr')).at(-1)).toMatchObject({ attr: 'labelfont', value: 'SansSerif bold 12' });
    // a number the original's parse refuses: red, a Korean sentence, no character; Esc puts the value back
    await click(page, mid(p.Constant));
    await expect(panel.locator('.aname')).toHaveText('Constant');
    const value = panel.getByLabel('Value', { exact: true });
    await expect(value).toHaveValue('0x1');
    await value.fill('0xZZ');
    await value.press('Enter');
    await expect(panel.locator('.aerr')).toHaveText('이 값은 Value 속성에 넣을 수 없습니다. 16진수(0x1F), 10진수(31)로 적습니다.');
    await expect(panel.locator('tr.bad th')).toHaveText('Value');
    await expect(value).toHaveValue('0xZZ');
    expect(await visibleCharacters(page)).toBe(0);
    await panel.getByLabel('Value', { exact: true }).press('Escape');
    await expect(panel.locator('.aerr')).toHaveCount(0);
    await expect(panel.getByLabel('Value', { exact: true })).toHaveValue('0x1');
    await panel.getByLabel('Value', { exact: true }).fill('0x1f');
    await panel.getByLabel('Value', { exact: true }).press('Enter');
    await expect.poll(async () => (await sent(r, 'edit.setAttr')).at(-1)).toMatchObject({ attr: 'value', value: '0x1f' });
    // two kinds: only what both have, a value that differs empty (I-20); the title the original's
    await click(page, mid(p['AND Gate']));
    await click(page, mid(p['OR Gate']), ['Shift']);
    await expect(panel.locator('.aname')).toHaveText('2 components');
    await expect(panel.locator('.afacts li')).toHaveText(['G1 · AND Gate', 'OR Gate']);
    await expect(panel.getByLabel('Number Of Inputs')).toHaveValue('');   // 3 and 5
    await panel.getByLabel('Data Bits').selectOption('8');
    await expect.poll(async () => (await sent(r, 'edit.setAttr')).at(-1)).toMatchObject({ attr: 'width', value: '8' });
    // nothing selected: the circuit's attributes (the original's, Y-05: never an empty pane)
    await click(page, [700, 600]);
    await expect(panel.locator('.aname')).toHaveText('main');
    await expect(panel.locator('.ahead .badge')).toHaveText('Circuit');
    await expect(panel.locator('.atable tbody th')).toHaveText(['Circuit Name', 'Shared Label', 'Shared Label Facing', 'Shared Label Font']);
    await panel.getByLabel('Circuit Name').fill('  ');
    await panel.getByLabel('Circuit Name').press('Enter');
    await expect(panel.locator('.aerr')).toHaveText('회로 이름이 비어 있습니다. 이름을 적으세요.');
    expect(await sent(r, 'edit.setCircuitAttr')).toEqual([]);
    await panel.getByLabel('Circuit Name').fill('top');
    await panel.getByLabel('Circuit Name').press('Enter');
    await expect.poll(async () => (await sent(r, 'edit.setCircuitAttr')).at(-1)).toMatchObject({ attr: 'circuit', value: 'top' });
    // a part in hand: the tool's attributes (I-53, I-54), the next part's; its ghost asks again
    await tool(page, 'Pin').click();
    await expect(panel.locator('.ahead .badge')).toHaveText('Tool');
    await expect(panel.locator('.aname')).toHaveText('Pin');
    const ghosts = (await sent(r, 'model.tool')).length;
    await panel.getByLabel('Data Bits').selectOption('4');
    await expect.poll(async () => (await sent(r, 'edit.setToolAttr')).at(-1)).toMatchObject({ lib: 'Wiring', name: 'Pin', attr: 'width', value: '4' });
    await expect.poll(async () => (await sent(r, 'model.tool')).length).toBeGreaterThan(ghosts);
    await expect(panel.getByLabel('Data Bits')).toHaveValue('4');
    // the Text tool: a Label's attributes (I-83)
    await tool(page, 'Text').click();
    await expect(panel.locator('.aname')).toHaveText('Text Tool');
    await expect(panel.locator('.atable tbody th')).toHaveText(['Text', 'Font', 'Horizontal Alignment', 'Vertical Alignment']);
  } finally {
    await r.close();
  }
});

test('Quick Attributes (I-103, I-104, S-04): by one kind of part chosen with the Edit tool; a list\'s choices, the label in place, a field for several; hidden while pressed, after a drag, an arrow, a message; the panel\'s switch; covers no part', async () => {
  const r = await launch();
  const { page } = r;
  try {
    const p = await place(r, [['Gates', 'AND Gate', [300, 200]], ['Gates', 'AND Gate', [300, 300]], ['Gates', 'OR Gate', [500, 200]]]);
    const bar = page.locator('.quickbar');
    const one = p['AND Gate'];
    const two = p['AND Gate2'];
    await tool(page, 'Edit').click();
    await click(page, mid(one));
    await expect(bar).toBeVisible();
    await expect(bar.locator('.qbtn:not(.qlink)')).toHaveText(['Number Of Inputs 5', 'Data Bits 1', 'Gate Size Medium', 'Facing East', 'Label (none)']);
    await expect(bar.locator('.qlink')).toHaveText(['All Attributes']);
    await expect(bar.locator('.qhint')).toHaveText('0–9: Number Of Inputs  ·  Alt+0–9: Data Bits  ·  R: Rotate  ·  F2: Label');
    // covers no other part (v1 placement)
    const box = await bar.boundingBox();
    for (const k of [two, p['OR Gate']]) {
      const a = await pagePoint(page, [k.bounds[0], k.bounds[1]]);
      const b = await pagePoint(page, [k.bounds[0] + k.bounds[2], k.bounds[1] + k.bounds[3]]);
      expect(box!.x + box!.width <= a.x || b.x <= box!.x || box!.y + box!.height <= a.y || b.y <= box!.y).toBe(true);
    }
    // a list: its choices, the one in use checked; one intent on the selection
    await bar.locator('.qbtn', { hasText: 'Number Of Inputs' }).click();
    await expect(page.locator('.ovmenu [aria-checked="true"]')).toHaveText(/5/);
    await item(page, '3').click();
    await expect.poll(async () => (await sent(r, 'edit.setAttr')).at(-1)).toMatchObject({ attr: 'inputs', value: '3' });
    await expect(bar.locator('.qbtn', { hasText: 'Number Of Inputs' })).toHaveText('Number Of Inputs 3');
    // the buttons leave the keys with the Canvas: a digit goes to the original's configurator
    await expect(page.locator('.canvas-view canvas')).toBeFocused();
    // the label of one part: F2's field in place
    await bar.locator('.qbtn', { hasText: 'Label' }).click();
    await expect(page.locator('.canvas-view .inline-field')).toBeVisible();
    await page.keyboard.press('Escape');
    // hidden while the left button is down on the Canvas, back when it is let go
    const q = await pagePoint(page, mid(one));
    await page.mouse.move(q.x, q.y);
    await page.mouse.down();
    await expect(bar).toBeHidden();
    await page.mouse.up();
    await expect(bar).toBeVisible();
    // after a drag that moved it: away until the next press (S-04)
    await drag(page, mid(one), [[mid(one)[0] + 40, mid(one)[1]]]);
    await expect.poll(async () => (await sent(r, 'edit.move')).length).toBe(1);
    await page.waitForTimeout(200);
    await expect(bar).toBeHidden();
    const moved = (await parts(page, 'AND Gate')).find((k) => k.id === one.id) ?? (await parts(page, 'AND Gate'))[0];
    await click(page, mid(moved));
    await expect(bar).toBeVisible();
    // an arrow key: away too
    await page.keyboard.press('ArrowRight');
    await expect(bar).toBeHidden();
    await click(page, mid((await parts(page, 'AND Gate')).find((k) => k.id !== two.id)!));
    await expect(bar).toBeVisible();
    // a message chosen, a place found (hcs:reveal): the program's selection, not shown on it
    await page.evaluate(() => window.dispatchEvent(new CustomEvent('hcs:reveal', { detail: { fileId: '', messageId: null, circuitId: '', root: '', path: [], components: [], wires: [], nets: [], at: null, cycle: null } })));
    await expect(bar).toBeHidden();
    // two of one kind: shown; the label for both in a field under the bar
    await click(page, mid(two));
    await click(page, mid((await parts(page, 'AND Gate')).find((k) => k.id !== two.id)!), ['Shift']);
    await expect(bar).toBeVisible();
    await bar.locator('.qbtn', { hasText: 'Label' }).click();
    const field = page.locator('.canvas-view .inline-field');
    await field.fill('g');
    await field.press('Enter');
    await expect.poll(async () => (await sent(r, 'edit.setAttr')).at(-1)).toMatchObject({ attr: 'label', value: 'g' });
    // two kinds: none
    await click(page, mid(p['OR Gate']), ['Shift']);
    await expect(bar).toBeHidden();
    // the panel's switch (this run only)
    await click(page, [800, 600]);
    await click(page, mid(p['OR Gate']));
    await expect(bar).toBeVisible();
    await page.locator('.pbody.attributes .afoot input').uncheck();
    await expect(bar).toBeHidden();
    await page.locator('.pbody.attributes .afoot input').check();
    await expect(bar).toBeVisible();
    // another tool: none
    await tool(page, 'Wire').click();
    await expect(bar).toBeHidden();
  } finally {
    await r.close();
  }
});

test('the Canvas\'s right-click menu (I-85..I-98, I-174, I-211; B-03, S-25): one registry, the summary line, a part\'s, a wire\'s, several parts\', an empty spot\'s items; keys; Ctrl+click and Shift+F10; the dialogs; the P key', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await canvasSettled(page);
    await recordCalls(r.app);
    const pc = (await partBy(page, 'PC'))!;
    await centerOn(page, mid(pc), 1.25);
    await canvasSettled(page);
    // a part: the engine's summary (v1 MenuLayout) and v1's items in v1's order, key hints on the right
    await rightClick(page, mid(pc));
    await expect(menu(page).locator('.mhead')).toHaveText('PC(Register) · 32 bits');
    await expect(menu(page).locator(':scope > button .label')).toHaveText(['Mark as PC', 'Influence', 'Signal Flow', 'Cut', 'Copy', 'Duplicate', 'Duplicate N…', 'Show in Attribute Panel', 'Delete']);
    await expect(item(page, 'Duplicate').locator('.keys')).toHaveText('Ctrl+D');
    // the keys: the first item has the focus, ↓ the next, Enter runs; Esc closes
    await expect(item(page, 'Mark as PC')).toBeFocused();
    await page.keyboard.press('ArrowDown');
    await expect(item(page, 'Influence')).toBeFocused();
    await page.keyboard.press('Escape');
    await expect(page.locator('.ovmenu')).toHaveCount(0);
    // Show in Attribute Panel: that part alone, the panel
    await rightClick(page, mid(pc));
    await item(page, 'Show in Attribute Panel').click();
    await expect.poll(() => selected(page)).toEqual([pc.id]);
    await expect(page.locator('.pbody.attributes .aname')).toHaveText('PC · Register');
    // Mark as PC (V-08)
    await rightClick(page, mid(pc));
    await item(page, 'Mark as PC').click();
    await expect.poll(async () => (await sent(r, 'record.markPc')).at(-1)).toMatchObject({ componentId: pc.id, on: true });
    // Shift+F10 at the selection; Ctrl+click on a part
    await page.locator('.canvas-view canvas').focus();
    await page.keyboard.press('Shift+F10');
    await expect(menu(page).locator('.mhead')).toHaveText('PC(Register) · 32 bits');
    await page.keyboard.press('Escape');
    await click(page, mid(pc), ['Control']);
    await expect(menu(page).locator('.mhead')).toHaveText('PC(Register) · 32 bits');
    await page.keyboard.press('Escape');
    // a wire: v1's order, the overlays' items in their places; Select Whole Net
    const q = pc.ports.find((x) => x.i === 0)!;
    const onWire: P = [q.loc[0] + 20, q.loc[1]];
    await rightClick(page, onWire);
    await expect(menu(page).locator('.mhead')).toHaveText('Net pc · 32 bits');
    await expect(menu(page).locator(':scope > button .label')).toHaveText(['Net Information…', 'Select Whole Net', 'Add to Cycle View', 'Signal Group', 'Find E/X Origin',
      'Highlight Net', 'Delete Net Wires', 'Replace Wire with Tunnels…', 'Split Bits…', 'Take One Bit', 'Attach Probe', 'Influence', 'Signal Flow', 'Delete']);
    await item(page, 'Select Whole Net').click();
    await expect.poll(async () => (await sent(r, 'edit.select')).at(-1)).toMatchObject({});
    const net = (await sent(r, 'edit.select')).at(-1)!.ids as string[];
    expect(net.length).toBeGreaterThan(1);
    await expect.poll(() => selected(page)).toEqual([...net].sort());
    // Replace Wire with Tunnels…: a name is needed; then one intent
    await rightClick(page, onWire);
    await item(page, 'Replace Wire with Tunnels…').click();
    const dlg = page.locator('dialog.menudlg');
    await expect(dlg.locator('h2')).toHaveText('Replace Wire with Tunnels');
    await dlg.getByRole('button', { name: 'OK' }).click();
    await expect(dlg.locator('.hint.err')).toHaveText('터널 이름을 적으세요.');
    await dlg.getByLabel('Tunnel Name').fill('pcx');
    await dlg.getByLabel('Tunnel Name').press('Enter');
    await expect(dlg).toHaveCount(0);
    expect((await sent(r, 'edit.wireToTunnels')).at(-1)).toMatchObject({ label: 'pcx' });
    await page.keyboard.press('Control+z');
    // the P key over a wire: a probe (1 bit: binary, more: hex -- the engine's choice)
    const w2 = (await wires(page)).find((w) => w.a[1] === w.b[1] && Math.abs(w.a[0] - w.b[0]) > 30)!;
    await page.locator('.canvas-view canvas').focus();
    await hover(page, [(w2.a[0] + w2.b[0]) / 2, w2.a[1]]);
    await page.keyboard.press('p');
    await expect.poll(async () => (await sent(r, 'edit.probe')).length).toBe(1);
    expect((await sent(r, 'edit.probe'))[0]).not.toHaveProperty('radix');
    await expect(page.locator('.palette')).toBeHidden();
    // an empty spot: its circuit, Paste, Fit to Window
    await rightClick(page, [mid(pc)[0] - 60, mid(pc)[1] + 90]);
    await expect(menu(page).locator('.mhead')).toHaveText('Empty spot · main');
    await expect(item(page, 'Paste').locator('.keys')).toHaveText('Ctrl+V');
    await item(page, 'Fit to Window').click();
    // several parts: "2 components" (S-25), v1's items; Duplicate N… asks and sends
    await centerOn(page, mid(pc), 1.25);
    await canvasSettled(page);
    const adder = (await partBy(page, '', 'Adder'))!;
    await click(page, mid(pc));
    await click(page, mid(adder), ['Shift']);
    await rightClick(page, mid(adder));
    await expect(menu(page).locator('.mhead')).toHaveText('2 components');
    await expect(menu(page).locator(':scope > button .label')).toContainText(['Duplicate N…', 'Align', 'Cut Selection', 'Copy Selection', 'Delete Selection']);
    await item(page, 'Duplicate N…').click();
    const dn = page.locator('dialog.menudlg');
    await expect(dn.locator('h2')).toHaveText('Duplicate N');
    await dn.getByLabel('Count').fill('99');
    await dn.getByRole('button', { name: 'OK' }).click();
    await expect(dn.locator('.hint.err')).toHaveText('복제할 수는 1–64 사이의 정수입니다.');
    await dn.getByLabel('Count').fill('2');
    await dn.getByLabel('Direction').selectOption('right');
    await dn.getByRole('button', { name: 'OK' }).click();
    await expect(dn).toHaveCount(0);
    expect((await sent(r, 'edit.duplicateN')).at(-1)).toMatchObject({ count: 2, direction: 'right', number: true });
    // Label… on a pin: its dialog with the label now; one intent, the selection kept (v1: keepSelection)
    const before = await selected(page);
    const pin = (await parts(page, 'Pin')).find((k) => k.attrs.label)!;
    await centerOn(page, mid(pin), 1.25);
    await canvasSettled(page);
    await rightClick(page, mid(pin));
    await item(page, 'Label…').click();
    const ld = page.locator('dialog.menudlg');
    await expect(ld.getByLabel('Label')).toHaveValue(pin.attrs.label);
    await ld.getByLabel('Label').fill('renamed');
    await ld.getByRole('button', { name: 'OK' }).click();
    await expect(ld).toHaveCount(0);
    expect((await sent(r, 'edit.setAttr')).at(-1)).toMatchObject({ ids: [pin.id], attr: 'label', value: 'renamed', keepSelection: true });
    expect(await selected(page)).toEqual(before);
    // the browser's own menu never shows (I-211): the page has only ours
    expect(await visibleCharacters(page)).toBe(0);
  } finally {
    await r.close();
  }
});

// A circuit's items: the Circuits panel's own list (N-11's circuitItems), wherever the circuit is right-clicked.
const CIRCUIT_ITEMS = ['Edit Circuit Layout', 'Edit Circuit Appearance', 'Rename…', 'Set As Main Circuit', 'Port Order…', 'Auto Appearance',
  'Move Circuit Up', 'Move Circuit Down', 'Remove Circuit'];

test('the Components list\'s and the circuit tabs\' menus (I-109): from the same registry, the Circuits panel\'s items; Set As Main Circuit; a library\'s Unload Library; a library\'s tool has none', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await canvasSettled(page);
    await recordCalls(r.app);
    const tree = page.locator('.comptree');
    await tree.locator('[data-tool="/alu"]').click({ button: 'right' });
    await expect(menu(page).locator(':scope > button .label')).toHaveText(CIRCUIT_ITEMS);
    await item(page, 'Set As Main Circuit').click();
    await expect.poll(async () => (await sent(r, 'edit.setMainCircuit')).at(-1)).toMatchObject({});
    await tree.locator('[data-tool="/main"]').click({ button: 'right' });
    await item(page, 'Edit Circuit Layout').click();
    await expect(page.locator('.circuitbar .ptab.on')).toHaveText('main');
    await tree.locator('[data-tool="Wiring/Pin"]').click({ button: 'right' });
    await expect(page.locator('.ovmenu')).toHaveCount(0);
    // a library's head: Unload Library (N-11's, in the same registry); one menu, not two
    await tree.locator('summary', { hasText: 'Wiring' }).click({ button: 'right' });
    await expect(page.locator('.ovmenu')).toHaveCount(1);
    await expect(menu(page).locator(':scope > button .label')).toHaveText(['Unload Library (Wiring)']);
    await page.keyboard.press('Escape');
    // the circuit tabs: alu is the main circuit now, so main's tab offers it back
    await expect(page.locator('.upper .list li', { hasText: 'alu' }).locator('.mainmark')).toHaveCount(1);
    await page.locator('.circuitbar .ptab', { hasText: 'main' }).click({ button: 'right' });
    await expect(menu(page).locator(':scope > button .label')).toHaveText(CIRCUIT_ITEMS);
    await item(page, 'Set As Main Circuit').click();
    await expect.poll(async () => (await sent(r, 'edit.setMainCircuit')).length).toBe(2);
    await page.locator('.circuitbar .ptab', { hasText: 'main' }).click({ button: 'right' });
    await expect(item(page, 'Set As Main Circuit')).toBeDisabled();
  } finally {
    await r.close();
  }
});

test('RAM and ROM (I-96, I-102): Edit Contents… in the hex editor (a RAM\'s are the simulation\'s, a ROM\'s the file\'s), Clear Contents, Load Image…, Save Image…; the ROM\'s Contents row', async () => {
  const r = await launch();
  const { page } = r;
  try {
    const p = await place(r, [['Memory', 'RAM', [300, 200], { addrWidth: '4', dataWidth: '8' }], ['Memory', 'ROM', [300, 400], { addrWidth: '4', dataWidth: '8', contents: 'addr/data: 4 8\n1 2 3\n' }]]);
    await tool(page, 'Edit').click();
    await rightClick(page, mid(p.RAM));
    await expect(menu(page).locator(':scope > button .label')).toContainText(['Edit Contents…', 'Clear Contents', 'Load Image…', 'Save Image…']);
    await item(page, 'Edit Contents…').click();
    const hex = page.locator('dialog.hexedit');
    await expect(hex.locator('.hexname')).toContainText('RAM');
    await expect(hex.locator('.hexwhere')).toContainText('4-bit address · 8-bit data');
    await expect(hex.locator('.hexcell[data-addr]')).toHaveCount(16);
    await hex.locator('.hexgrid').focus();
    await page.keyboard.press('ArrowRight');
    await page.keyboard.type('a5');
    await expect(hex.locator('.hexcell.caret')).toHaveText('a5');
    await page.keyboard.press('Enter');
    await expect.poll(async () => (await sent(r, 'mem.write')).at(-1)).toMatchObject({ addr: 1, values: [0xa5] });
    await expect(hex.locator('.hexcell[data-addr="1"]')).toHaveText('a5');
    await expect(hex.locator('.hexcell.caret')).toHaveAttribute('data-addr', '2');
    await hex.getByRole('button', { name: 'Close' }).click();
    // Save Image…: the main process's dialog, then the engine writes it
    const img = path.join(r.dir, 'ram.txt');
    await answerSave(r.app, img);
    await rightClick(page, mid(p.RAM));
    await item(page, 'Save Image…').click();
    await expect.poll(() => existsSync(img)).toBe(true);
    expect(readFileSync(img, 'utf8')).toMatch(/^v2\.0 raw\n0 a5 /);
    // Load Image…
    writeFileSync(img, 'v2.0 raw\n9 8 7\n');
    await answerOpen(r.app, img);
    await rightClick(page, mid(p.RAM));
    await item(page, 'Load Image…').click();
    await expect.poll(async () => (await sent(r, 'mem.loadImage')).length).toBe(1);
    // Clear Contents: asked first (a RAM's is not undone)
    await rightClick(page, mid(p.RAM));
    await item(page, 'Clear Contents').click();
    await page.locator('dialog.ask').getByRole('button', { name: 'Clear' }).click();
    await expect.poll(async () => (await sent(r, 'mem.clear')).length).toBe(1);
    // the ROM's Contents row opens the same editor; a change is the file's (edit.memContents)
    await click(page, mid(p.ROM));
    await page.locator('.pbody.attributes').getByLabel('Contents').click();
    await expect(hex.locator('.hexname')).toContainText('ROM');
    await expect(hex.locator('.hexcell[data-addr="0"]')).toHaveText('01');
    await hex.locator('.hexgrid').focus();
    await page.keyboard.type('7');
    await page.keyboard.press('Tab');
    await expect.poll(async () => (await sent(r, 'edit.memContents')).at(-1)).toMatchObject({ id: p.ROM.id, addr: 0, values: [7] });
    await page.keyboard.press('Escape');
    await expect(hex).toHaveCount(0);
  } finally {
    await r.close();
  }
});
