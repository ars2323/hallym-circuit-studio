/* Finding and placing (N-12, D-150) with the fake engine, whose library and
   Find answers are the real engine's (tests/fixtures/library.json,
   find.json): the Components list and its search (I-106, I-110, V-01), a
   part dragged onto the Canvas (I-62), the search palette (Ctrl+K, a letter
   on the Canvas: I-41, I-168..I-170), Find (Ctrl+F: I-171, I-172, S-09,
   S-28), Tunnels and Tunnel Color (I-175, V-08, B-08), the Minimap (I-176,
   E-08), the Splitter editor (I-189, B-14), and the events the Canvas's
   placement flow (N-08) meets them on (tool-events.ts). */

import { expect, test, type Page } from '@playwright/test';

import { canvasSettled, DATAPATH, launch, newCircuit, openFile, sample } from './harness.ts';

const BROKEN = 'electron/tests/fixtures/broken-datapath.circ';

type Comp = { id: string; name: string; lib: string | null; loc: [number, number]; bounds: [number, number, number, number]; attrs: Record<string, string>; ext?: { color?: string; arms?: string[] } };
type CanvasApi = {
  scene: { fileId: string; circuitId: string; components: Map<string, Comp> } | null;
  view: { x: number; y: number; zoom: number };
  canvas: HTMLCanvasElement;
  markedIds(): { components: string[]; tone: string } | null;
};

const comps = (page: Page) => page.evaluate(() => {
  const c = (window as unknown as { __hcsCanvas: CanvasApi }).__hcsCanvas;
  return c.scene ? [...c.scene.components.values()].map((x) => ({ id: x.id, name: x.name, lib: x.lib, loc: x.loc, bounds: x.bounds, attrs: x.attrs, ext: x.ext })) : [];
});
const marked = (page: Page) => page.evaluate(() => (window as unknown as { __hcsCanvas: CanvasApi }).__hcsCanvas.markedIds());
const view = (page: Page) => page.evaluate(() => ({ ...(window as unknown as { __hcsCanvas: CanvasApi }).__hcsCanvas.view }));
// A circuit point on the screen (window px).
const screenOf = (page: Page, p: [number, number]) => page.evaluate((q) => {
  const c = (window as unknown as { __hcsCanvas: CanvasApi }).__hcsCanvas;
  const r = c.canvas.getBoundingClientRect();
  return [r.left + (q[0] - c.view.x) * c.view.zoom, r.top + (q[1] - c.view.y) * c.view.zoom] as [number, number];
}, p);
const active = (page: Page) => page.evaluate(() => {
  const c = (window as unknown as { __hcsCanvas: CanvasApi }).__hcsCanvas;
  return { fileId: c.scene!.fileId, circuitId: c.scene!.circuitId };
});
// Every hcs:place-tool the page sends; `take`: a listener takes them (as N-08's placement flow will).
async function hearPlaceTool(page: Page, take = false): Promise<void> {
  await page.evaluate((t) => {
    const w = window as unknown as { placed: unknown[] };
    w.placed = [];
    window.addEventListener('hcs:place-tool', (e) => { w.placed.push((e as CustomEvent).detail); if (t) e.preventDefault(); });
  }, take);
}
const placedEvents = (page: Page) => page.evaluate(() => (window as unknown as { placed: Record<string, unknown>[] }).placed);
async function drawn(page: Page): Promise<void> {
  await page.locator('.canvas .canvas-view canvas').waitFor();
  await page.waitForFunction(() => !!(window as unknown as { __hcsCanvas?: CanvasApi }).__hcsCanvas?.scene);
  await canvasSettled(page); // its first view chosen and drawn: a point computed from the view is where it is on the page
}

test('Components: the engine\'s library under the file\'s name first; Hallym MIPS before the file has it; a part picked is sent to the Canvas', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await newCircuit(r);
    const groups = page.locator('.upper .libgroup summary');
    await expect(groups.first()).toContainText('untitled.circ');
    await expect(groups.nth(1)).toContainText('Wiring');
    await expect(groups.last()).toContainText('Hallym MIPS');
    await expect(groups.last().locator('.dim')).toHaveText('not in the file yet');
    await expect(page.locator('.upper .libgroup.pending .list li')).toHaveText(['Instruction Memory', 'Data Memory', 'Console', 'Radix Probe']);
    await hearPlaceTool(page);
    await page.locator('.upper .libgroup', { hasText: 'Gates' }).locator('summary').click();
    await page.locator('.upper .list li', { hasText: /^AND Gate$/ }).getByRole('button').click();
    await expect(page.locator('.comptree li.on')).toHaveText('AND Gate');
    const sent = await placedEvents(page);
    expect(sent).toHaveLength(1);
    expect(sent[0]).toMatchObject({ lib: 'Gates', name: 'AND Gate', source: 'components' });
    expect(sent[0].at).toBeUndefined();
    // nobody took it (no placement flow yet): nothing is placed without a point
    await expect(page.locator('.status')).toContainText('0 components');
  } finally {
    await r.close();
  }
});

test('Components search: the palette\'s ranking without commands, ↓ to the list, Enter picks with the number, Esc clears; the circuit on show is listed too', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await hearPlaceTool(page);
    const box = page.getByRole('searchbox', { name: 'Search parts' });
    await box.fill('and 3');
    const rows = page.locator('.compresults li');
    await expect(rows.first()).toContainText('AND Gate');
    await expect(rows.first()).toContainText('Number Of Inputs 3');
    await expect(page.locator('.comptree')).toBeHidden();
    await box.press('ArrowDown');
    await expect(rows.first().getByRole('option')).toBeFocused();
    await page.keyboard.press('ArrowDown');
    await expect(rows.nth(1).getByRole('option')).toBeFocused();
    await page.keyboard.press('Enter');
    expect((await placedEvents(page)).at(-1)).toMatchObject({ lib: 'Gates', name: 'NAND Gate', attrs: { inputs: '3' } });
    await box.fill('reset');
    await expect(page.locator('.compnone')).toContainText('맞는 부품이 없습니다');
    await box.fill('main');
    await expect(rows.first()).toContainText('main');
    await box.fill('레지스터');
    await box.press('Enter');
    expect((await placedEvents(page)).at(-1)).toMatchObject({ lib: 'Memory', name: 'Register' });
    await box.press('Escape');
    await expect(box).toHaveValue('');
    await expect(page.locator('.comptree')).toBeVisible();
    // this file's circuit: a double click opens it (I-113)
    await page.locator('.comptree li', { hasText: /^regfile$/ }).getByRole('button').dblclick();
    await expect(page.locator('.circuitbar .ptab.on')).toHaveText('regfile');
  } finally {
    await r.close();
  }
});

test('a part dragged from Components to the Canvas is placed where it is dropped, on the grid; a Hallym MIPS part puts the library in the file (I-62, V-01)', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await newCircuit(r);
    await page.locator('.upper .libgroup', { hasText: 'Gates' }).locator('summary').click();
    const target = page.locator('.pbody.canvas');
    await page.locator('.upper .list li', { hasText: /^OR Gate$/ }).getByRole('button').dragTo(target, { targetPosition: { x: 203, y: 157 } });
    await expect(page.locator('.status')).toContainText('1 component');
    await drawn(page);
    const [or] = await comps(page);
    expect(or).toMatchObject({ lib: 'Gates', name: 'OR Gate', loc: [200, 160] });
    // the bundled library: not in the file until a part of it is placed
    await page.locator('.upper .libgroup.pending .list li', { hasText: 'Console' }).getByRole('button').dragTo(page.locator('.canvas .canvas-view canvas'), { targetPosition: { x: 400, y: 300 } });
    await expect(page.locator('.status')).toContainText('2 components');
    await expect(page.locator('.upper .libgroup.pending')).toHaveCount(0);
    await expect(page.locator('.upper .libgroup summary').last()).toContainText('Hallym MIPS');
  } finally {
    await r.close();
  }
});

test('the search palette: Ctrl+K by the pointer, parts with the number, Enter places one there; tunnels, commands, favourites; Esc and a press outside close it', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await drawn(page);
    const before = (await comps(page)).length;
    const [x, y] = await screenOf(page, [300, 640]);
    await page.mouse.move(x, y);
    await page.keyboard.press('Control+k');
    const pal = page.getByRole('dialog', { name: 'Search' });
    await expect(pal).toBeVisible();
    const box = await pal.boundingBox();
    expect(Math.abs(box!.x - (x + 12))).toBeLessThanOrEqual(1);
    await expect(pal.locator('.palnote')).toContainText('and 3');
    await page.keyboard.type('and 3');
    await expect(pal.locator('.palrow').first()).toContainText('AND Gate');
    await expect(pal.locator('.palrow').first()).toContainText('Number Of Inputs 3');
    await expect(pal.locator('.palrow.on')).toHaveCount(1);
    await page.keyboard.press('Enter');
    await expect(pal).toBeHidden();
    await expect.poll(async () => (await comps(page)).length).toBe(before + 1);
    const and = (await comps(page)).find((c) => c.name === 'AND Gate')!;
    expect(and.loc).toEqual([300, 640]);
    expect(and.attrs.inputs).toBe('3');
    // a tunnel: it goes there
    await page.keyboard.press('Control+k');
    await page.keyboard.type('memw');
    await expect(pal.locator('.palrow').first()).toContainText('MemWrite');
    await expect(pal.locator('.palrow').first()).toContainText('2 tunnels');
    await page.keyboard.press('Enter');
    await expect.poll(async () => (await marked(page))?.tone).toBe('find');
    const tid = (await marked(page))!.components[0];
    const t = (await comps(page)).find((c) => c.id === tid)!;
    expect(t.attrs.label).toBe('MemWrite');
    // a command: 1 Cycle, then Reset Simulation
    await page.keyboard.press('Control+k');
    await page.keyboard.type('사이클');
    await expect(pal.locator('.palrow.on')).toContainText('1 Cycle');
    await page.keyboard.press('Enter');
    await expect(page.locator('.status')).toContainText('Cycle 1');
    await page.keyboard.press('Control+k');
    await page.keyboard.type('reset');
    await page.keyboard.press('Enter');
    await expect(page.locator('.status')).toContainText('Cycle 0');
    // a favourite: marked and first for this run
    await page.keyboard.press('Control+k');
    await page.keyboard.type('gate');
    await page.keyboard.press('ArrowDown');
    const second = (await pal.locator('.palrow').nth(1).locator('.palname').textContent())!;
    await page.keyboard.press('Alt+Enter');
    await expect(pal.locator('.palrow').first()).toContainText(second);
    await expect(pal.locator('.palrow').first().locator('.palfav')).toHaveText('★');
    await page.keyboard.press('Escape');
    await expect(pal).toBeHidden();
    // a press outside closes it; one at a time
    await page.keyboard.press('Control+k');
    await page.keyboard.press('Control+k');
    await expect(page.getByRole('dialog', { name: 'Search' })).toHaveCount(1);
    await page.mouse.click(10, 1000);
    await expect(pal).toBeHidden();
  } finally {
    await r.close();
  }
});

test('a letter typed on the Canvas with nothing selected opens the palette with it; a key another part of the window used does not', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await drawn(page);
    const [x, y] = await screenOf(page, [300, 700]);
    await page.mouse.click(x, y);   // empty space: nothing selected, the Canvas has the keys
    await page.keyboard.press('r');
    const pal = page.getByRole('dialog', { name: 'Search' });
    await expect(pal).toBeVisible();
    await expect(pal.getByRole('textbox')).toHaveValue('r');
    await page.keyboard.type('eg');
    await expect(pal.locator('.palrow').first()).toContainText('Register');
    await page.keyboard.press('Escape');
    // a key the Canvas's tools take (preventDefault, N-08/N-15): not the palette's
    await page.evaluate(() => (window as unknown as { __hcsCanvas: CanvasApi }).__hcsCanvas.canvas.addEventListener('keydown', (e) => { if (e.key === 'p') e.preventDefault(); }));
    await page.locator('.canvas .canvas-view canvas').focus();
    await page.keyboard.press('p');
    await page.waitForTimeout(100);
    await expect(pal).toBeHidden();
  } finally {
    await r.close();
  }
});

test('Find (Ctrl+F): groups with their places, a click opens a group, a place goes there -- into the instance it is in -- in the selection\'s look; Esc closes', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await drawn(page);
    await page.keyboard.press('Control+f');
    const win = page.getByRole('dialog', { name: 'Find' });
    await expect(win).toBeVisible();
    await expect(win.getByRole('searchbox', { name: 'Find' })).toBeFocused();
    await expect(win.locator('.findfoot')).toContainText('모든 회로에서');
    await page.keyboard.type('clk');
    const rows = win.locator('.findrow');
    await expect(rows).toHaveCount(3);
    await expect(rows.first()).toContainText('clk');
    await expect(rows.first()).toContainText('Tunnel');
    await expect(rows.first()).toContainText('main › clk');
    await expect(rows.first()).toContainText('4 places');
    await expect(win.locator('.findfoot')).toHaveText('3 results');
    await rows.first().click();
    await expect(rows).toHaveCount(3 + 4);
    await expect(rows.nth(1)).toHaveClass(/child/);
    await expect(rows.nth(1)).toContainText(/^next to main › /);
    await rows.nth(2).click();
    await expect.poll(async () => (await marked(page))?.tone).toBe('find');
    const tid = (await marked(page))!.components[0];
    const t = (await comps(page)).find((c) => c.id === tid)!;
    expect(t.name).toBe('Tunnel');
    expect(t.attrs.label).toBe('clk');
    // a name inside the register file instance: into it (the crumbs), the pin marked
    const box = win.getByRole('searchbox', { name: 'Find' });
    await box.fill('RR1');
    await expect(rows.first()).toContainText('main › regfile #1 › RR1');
    await box.press('Enter');
    await expect(page.locator('.canvas-crumbs')).toContainText('regfile');
    await expect.poll(async () => (await marked(page))?.components.length).toBe(1);
    const pid = (await marked(page))!.components[0];
    const pin = (await comps(page)).find((c) => c.id === pid)!;
    expect(pin).toMatchObject({ name: 'Pin', attrs: expect.objectContaining({ label: 'RR1' }) });
    // ↓ from the box chooses the next row; nothing found says so
    await box.fill('sel');
    await expect(rows).toHaveCount(3);
    await box.press('ArrowDown');
    await expect(rows.nth(1)).toHaveClass(/on/);
    await box.fill('zzz');
    await expect(win.locator('.findfoot')).toHaveText('찾는 이름과 맞는 것이 없습니다: zzz');
    await box.press('Escape');
    await expect(win).toBeHidden();
  } finally {
    await r.close();
  }
});

test('Tunnels: names with counts, a lone one in amber; each press the next tunnel; Tunnel Color sets every tunnel of the name, Automatic takes it back', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, BROKEN));
    await drawn(page);
    const rows = page.locator('.lower .list.tunnels li');
    await expect(rows.first()).toHaveText(/ALUOp\s*2/);
    const lone = rows.filter({ hasText: 'RegWirte' });
    await expect(lone.locator('.count')).toHaveText('(1)');
    await expect(lone.locator('.count')).toHaveClass(/lone/);
    await expect(lone.locator('.tname')).toHaveAttribute('title', 'RegWirte: 같은 이름의 터널이 하나뿐입니다.');
    // the next tunnel of the name on every press
    const clk = rows.filter({ hasText: /^clk/ });
    const seen: string[] = [];
    for (let i = 0; i < 3; i++) {
      await clk.locator('.tname').click();
      await expect.poll(async () => (await marked(page))?.components[0]).not.toBe(seen.at(-1));
      seen.push((await marked(page))!.components[0]);
    }
    expect(new Set(seen).size).toBe(3);
    // Tunnel Color: Orange for every "MemRead", then Automatic
    const memread = rows.filter({ hasText: 'MemRead' });
    await memread.locator('.tswatch').click();
    const menu = page.getByRole('menu', { name: 'Tunnel Color' });
    await expect(menu).toBeVisible();
    await expect(menu.getByRole('menuitemradio', { name: /Automatic/ })).toHaveAttribute('aria-checked', 'true');
    await menu.getByRole('menuitemradio', { name: 'Orange' }).click();
    await expect(menu).toBeHidden();
    await expect(memread.locator('.tswatch')).toHaveClass(/chosen/);
    await expect(memread.locator('.tswatch')).toHaveAttribute('style', '--c:#e69f00');
    const colours = (await comps(page)).filter((c) => c.name === 'Tunnel' && c.attrs.label === 'MemRead').map((c) => c.ext?.color);
    expect(colours).toEqual(['#E69F00', '#E69F00']);
    await expect(page.locator('.filebar .ptab .dirty')).toHaveCount(1);
    await memread.locator('.tswatch').click();
    await expect(menu.getByRole('menuitemradio', { name: 'Orange' })).toHaveAttribute('aria-checked', 'true');
    await menu.getByRole('menuitemradio', { name: /Automatic/ }).click();
    await expect(memread.locator('.tswatch')).not.toHaveClass(/chosen/);
    // Undo brings the colour back (one undo step)
    await page.keyboard.press('Control+z');
    await expect(memread.locator('.tswatch')).toHaveClass(/chosen/);
  } finally {
    await r.close();
  }
});

test('the Minimap: the whole circuit and the view; a press or a drag brings that point to the middle of the Canvas, the zoom kept', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await drawn(page);
    await page.getByRole('tab', { name: 'Minimap' }).click();
    const map = page.getByRole('img', { name: 'Minimap' }).or(page.locator('canvas.minimap'));
    await expect(map).toBeVisible();
    // something is drawn (not all one colour): the parts, the wires, the tunnels' colours, the view
    await expect.poll(() => page.evaluate(() => {
      const c = document.querySelector<HTMLCanvasElement>('canvas.minimap')!;
      const d = c.getContext('2d')!.getImageData(0, 0, c.width, c.height).data;
      const seen = new Set<number>();
      for (let i = 0; i < d.length; i += 4) seen.add((d[i] << 16) | (d[i + 1] << 8) | d[i + 2]);
      return seen.size;
    })).toBeGreaterThan(8);
    const box = (await map.boundingBox())!;
    const panel = (await page.locator('.lower .pbody:not([hidden])').boundingBox())!;
    expect(box.x + box.width).toBeLessThanOrEqual(panel.x + panel.width + 0.5);
    const z = (await view(page)).zoom;
    await map.click({ position: { x: box.width * 0.8, y: box.height * 0.5 } });
    const v1 = await view(page);
    expect(v1.zoom).toBe(z);
    await page.mouse.move(box.x + box.width * 0.8, box.y + box.height * 0.5);
    await page.mouse.down();
    await page.mouse.move(box.x + box.width * 0.3, box.y + box.height * 0.4, { steps: 4 });
    await page.mouse.up();
    const v2 = await view(page);
    expect(v2.x).toBeLessThan(v1.x);
    expect(v2.zoom).toBe(z);
  } finally {
    await r.close();
  }
});

test('the Splitter editor: from the palette for the selected splitter -- its arms and names, a mistake said, a preset, the strip, Apply in one undo step; Esc changes nothing', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await drawn(page);
    const sp = (await comps(page)).find((c) => c.name === 'Splitter')!;
    const [x, y] = await screenOf(page, [sp.bounds[0] + sp.bounds[2] / 2, sp.bounds[1] + 8]);
    // its arm names on hover (the chips show them only where they stand free)
    await page.mouse.move(x, y);
    await expect(page.locator('.canvas-tip')).toHaveText('Splitter · op rs rt rd shamt funct');
    await page.mouse.click(x, y);
    await page.keyboard.press('Control+k');
    await page.keyboard.type('splitter');
    const pal = page.getByRole('dialog', { name: 'Search' });
    await expect(pal.locator('.palrow', { hasText: 'Edit Splitter…' })).toHaveCount(1);
    await pal.locator('.palrow', { hasText: 'Edit Splitter…' }).dblclick();
    const dlg = page.getByRole('dialog', { name: 'Edit Splitter' });
    await expect(dlg).toBeVisible();
    const ranges = dlg.getByRole('textbox', { name: 'Ranges' });
    await expect(ranges).toHaveValue('31:26 op, 25:21 rs, 20:16 rt, 15:11 rd, 10:6 shamt, 5:0 funct');
    await expect(dlg.locator('.sarmtag')).toHaveText(['Arm 0 [31:26]', 'Arm 1 [25:21]', 'Arm 2 [20:16]', 'Arm 3 [15:11]', 'Arm 4 [10:6]', 'Arm 5 [5:0]']);
    await expect(dlg.getByRole('textbox', { name: 'Arm 5 name' })).toHaveValue('funct');
    await expect(dlg.locator('.scell')).toHaveCount(32);
    // a text that cannot be read: said, and no Apply
    await ranges.fill('31:26, 26:0');
    await expect(dlg.locator('.sproblems')).toContainText('한 비트를 두 팔에 둘 수 없습니다: 26');
    await expect(dlg.getByRole('button', { name: 'Apply' })).toBeDisabled();
    // a preset: the arms and a fact about the wires (their widths now)
    await dlg.getByRole('combobox', { name: 'Preset' }).selectOption({ label: 'MIPS I-type: op rs rt imm' });
    await expect(dlg.locator('.sarmtag')).toHaveText(['Arm 0 [31:26]', 'Arm 1 [25:21]', 'Arm 2 [20:16]', 'Arm 3 [15:0]']);
    await expect(dlg.locator('.sproblems')).toContainText('이어진 선과 폭이 다릅니다');
    // the strip: the line between bits 16 and 15 joins rt and imm; again, splits them
    const strip = dlg.getByRole('group', { name: 'Bits' });
    const sb = (await strip.boundingBox())!;
    await page.mouse.click(sb.x + 1 + 16 * 16, sb.y + sb.height / 2);
    await expect(dlg.locator('.sarmtag')).toHaveText(['Arm 0 [31:26]', 'Arm 1 [25:21]', 'Arm 2 [20:0]']);
    await page.mouse.click(sb.x + 1 + 16 * 16, sb.y + sb.height / 2);
    await expect(dlg.locator('.sarmtag')).toHaveText(['Arm 0 [31:26]', 'Arm 1 [25:21]', 'Arm 2 [20:16]', 'Arm 3 [15:0]']);
    await dlg.getByRole('textbox', { name: 'Arm 3 name' }).fill('offset');
    await ranges.press('Enter');
    await expect(dlg).toBeHidden();
    const here = async () => (await comps(page)).find((c) => c.name === 'Splitter' && c.loc[0] === sp.loc[0] && c.loc[1] === sp.loc[1]);
    await expect.poll(async () => (await here())?.ext?.arms).toEqual(['op', 'rs', 'rt', 'offset']);
    expect((await here())!.attrs.fanout).toBe('4');
    expect((await here())!.attrs.bit0).toBe('3');
    // one undo step
    await page.keyboard.press('Control+z');
    await expect.poll(async () => (await here())?.attrs.fanout).toBe('6');
    // Esc: nothing changes
    const f = await active(page);
    await page.evaluate(([a, id]) => window.dispatchEvent(new CustomEvent('hcs:edit-splitter', { detail: { ...a, componentId: id } })), [f, (await here())!.id] as const);
    await expect(dlg).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(dlg).toBeHidden();
    expect((await here())!.attrs.fanout).toBe('6');
  } finally {
    await r.close();
  }
});

test('Split Bits (the wire menu\'s event, N-10): the editor starts as the R-type fields on a 32-bit wire, LSB on top turns the arms, Apply puts a splitter on the wire; Take One Bit goes without it', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await drawn(page);
    const wire = await page.evaluate(() => {
      const c = (window as unknown as { __hcsCanvas: { scene: { wires: Map<string, { id: string; a: [number, number]; b: [number, number] }>; wireNet(id: string): { width: number } | undefined } } }).__hcsCanvas;
      const w = [...c.scene.wires.values()].find((x) => (c.scene.wireNet(x.id)?.width ?? 0) === 32 && x.a[1] === x.b[1] && Math.abs(x.a[0] - x.b[0]) >= 40)!;
      return { id: w.id, at: [Math.min(w.a[0], w.b[0]) + 20, w.a[1]] as [number, number] };
    });
    const f = await active(page);
    const splitters = async () => (await comps(page)).filter((c) => c.name === 'Splitter');
    const n = (await splitters()).length;
    await page.evaluate(([a, w]) => window.dispatchEvent(new CustomEvent('hcs:edit-splitter', { detail: { ...a, wire: w.id, at: w.at } })), [f, wire] as const);
    const split = page.getByRole('dialog', { name: 'Split Bits' });
    await expect(split.getByRole('textbox', { name: 'Ranges' })).toHaveValue('31:26 op, 25:21 rs, 20:16 rt, 15:11 rd, 10:6 shamt, 5:0 funct');
    await split.getByRole('radio', { name: 'LSB on top' }).check();
    await expect(split.locator('.sarmtag').first()).toHaveText('Arm 0 [5:0]');
    await expect(split.getByRole('textbox', { name: 'Arm 0 name' })).toHaveValue('funct');
    await split.getByRole('button', { name: 'Apply' }).click();
    await expect.poll(async () => (await splitters()).length).toBe(n + 1);
    const made = (await splitters()).find((c) => c.loc[0] === wire.at[0] && c.loc[1] === wire.at[1])!;
    expect(made.ext?.arms).toEqual(['funct', 'shamt', 'rd', 'rt', 'rs', 'op']);
    expect(made.attrs.bit0).toBe('0');
    // Take One Bit [5]: no editor, one arm of bit 5
    await page.evaluate(([a, w]) => window.dispatchEvent(new CustomEvent('hcs:edit-splitter', { detail: { ...a, wire: w.id, at: [w.at[0] + 10, w.at[1]], bit: 5 } })), [f, wire] as const);
    await expect.poll(async () => (await splitters()).length).toBe(n + 2);
    await expect(split).toBeHidden();
    const one = (await splitters()).find((c) => c.loc[0] === wire.at[0] + 10)!;
    expect(one.attrs.fanout).toBe('1');
    expect(one.attrs.bit5).toBe('0');
    expect(one.attrs.bit4).toBe('none');
  } finally {
    await r.close();
  }
});

test('the events: a part a listener takes (N-08\'s placement flow) is not placed here; the Canvas\'s selection goes out as hcs:selection', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await drawn(page);
    await hearPlaceTool(page, true);
    const before = (await comps(page)).length;
    const [x, y] = await screenOf(page, [300, 640]);
    await page.mouse.move(x, y);
    await page.keyboard.press('Control+k');
    await page.keyboard.type('xor');
    await page.keyboard.press('Enter');
    const sent = await placedEvents(page);
    expect(sent.at(-1)).toMatchObject({ lib: 'Gates', name: 'XOR Gate', at: [300, 640], source: 'palette' });
    await page.waitForTimeout(200);
    // N-08's placement flow (the editor's listener) took it and placed one at the point; not placed here too
    expect((await comps(page)).length).toBe(before + 1);
    // selection
    await page.evaluate(() => {
      const w = window as unknown as { selections: unknown[] };
      w.selections = [];
      window.addEventListener('hcs:selection', (e) => w.selections.push((e as CustomEvent).detail));
    });
    const reg = (await comps(page)).find((c) => c.name === 'Register')!;
    const [rx, ry] = await screenOf(page, [reg.bounds[0] + reg.bounds[2] / 2, reg.bounds[1] + reg.bounds[3] / 2]);
    await page.mouse.click(rx, ry);
    const sel = await page.evaluate(() => (window as unknown as { selections: Record<string, unknown>[] }).selections);
    expect(sel.at(-1)).toMatchObject({ ids: [reg.id], path: [] });
  } finally {
    await r.close();
  }
});
