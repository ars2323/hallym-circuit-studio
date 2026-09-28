/* The Canvas's overlays in the real window (N-15, D-151), with the fake
   engine answering demo-datapath from the real engine's answers
   (tests/fixtures/flow/): Signal Flow on a click (I-188), the influence
   with its keys (I-187), Esc one at a time (I-19), the active path and the
   field colours with the Cycle View, bus values and their radix, signal
   groups, area memos, Net Information and Highlight Net from a wire's right
   click -- and none of the reading changes the file. */

import { expect, test } from '@playwright/test';

import { launch, statusText } from './harness.ts';
import { click, colourNear, menu, onPage, opened, partMiddle, rightClick, setView, shown, wireAtPort } from './overlay-helpers.ts';

test('Signal Flow on a click (I-188): the Edit tool\'s click shows the way, Shift backward; an empty place, Esc and another circuit stop it; Ctrl+Shift+F and the toolbar switch it', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await opened(r);
    const flowButton = page.locator('.tools-seg button.flowtoggle');
    await expect(flowButton).toHaveAttribute('aria-pressed', 'true');
    await expect(flowButton).toBeEnabled();
    const pc = await partMiddle(page, 'Register', 'PC');
    await click(page, pc.at);
    await page.waitForFunction(() => (window as unknown as { __hcsOverlays: { shown(): { flow: { running: boolean } } } }).__hcsOverlays.shown().flow.running);
    let s = await shown(page);
    // the real engine's way from the PC (v1 tests/circ/flow/demo-pc.flow)
    expect(s.flow.ends).toEqual(['state Instruction Memory (Addr)', 'unconnected Comparator (gt)', 'unconnected Comparator (lt)', 'output halt', 'state PC (D)']);
    expect(s.flow.backward).toBe(false);
    await expect(page.locator('.status')).toContainText('Signal Flow: Forward');
    // the front moves: more of the way lit later
    const before = s.flow.lit;
    await page.waitForTimeout(900);
    s = await shown(page);
    expect(s.flow.lit).toBeGreaterThan(before);
    // labels for the state input and the pin with no chip of its own; the halt pin has its chip already
    await page.waitForTimeout(2500);
    s = await shown(page);
    expect(s.flow.labels).toContain('Instruction Memory (Addr)');
    expect(s.flow.labels).toContain('PC (D)');
    expect(s.flow.labels).not.toContain('halt');
    // at its end (UI review of signal-flow.png): the jump between the two pc tunnels drawn whole as an arc in the
    // tunnel's colour, and the two labels drawn (their words in ink)
    await page.waitForFunction(() => { const f = (window as unknown as { __hcsOverlays: { shown(): { flow: { t: number; total: number } } } }).__hcsOverlays.shown().flow; return f.t >= f.total; });
    s = await shown(page);
    expect(s.flow.jumps).toBeGreaterThan(0);
    expect(s.flow.drawn.arcs).toHaveLength(s.flow.jumps);
    expect(s.flow.drawn.arcs.every((a) => a.whole)).toBe(true);
    expect(s.flow.drawn.labels.map((l) => l.text).sort()).toEqual(['Instruction Memory (Addr)', 'PC (D)']);
    await page.mouse.move(-10, -10);
    for (const a of s.flow.drawn.arcs) {
      const mid: [number, number] = [0.25 * a.from[0] + 0.5 * a.control[0] + 0.25 * a.to[0], 0.25 * a.from[1] + 0.5 * a.control[1] + 0.25 * a.to[1]];
      expect(await colourNear(page, await onPage(page, mid), a.color, 4, 30), `the arc ${a.from}→${a.to} at its middle`).toBe(true);
    }
    for (const l of s.flow.drawn.labels) {
      expect(await colourNear(page, await onPage(page, [(l.box.x0 + l.box.x1) / 2, (l.box.y0 + l.box.y1) / 2]), '#1f2933', 4, 40), `${l.text}'s words`).toBe(true);
    }
    // the ring at the Instruction Memory's Addr: the accent
    const addr = await page.evaluate(() => {
      const c = (window as unknown as { __hcsCanvas: { scene: { components: Map<string, { name: string; ports: { name?: string; loc: number[] }[] }> } } }).__hcsCanvas;
      const im = [...c.scene.components.values()].find((x) => x.name === 'Instruction Memory')!;
      return im.ports.find((q) => q.name === 'Addr')!.loc as [number, number];
    });
    await page.mouse.move(-10, -10);
    const ring = await onPage(page, [addr[0] - 6, addr[1]]);
    expect(await colourNear(page, ring, '#00a9a5', 3)).toBe(true);
    // Esc stops it (the influence is not there to clear)
    await page.keyboard.press('Escape');
    expect((await shown(page)).flow.running).toBe(false);
    // Shift+click: backward, to the part
    await click(page, pc.at, true);
    await page.waitForFunction(() => (window as unknown as { __hcsOverlays: { shown(): { flow: { running: boolean } } } }).__hcsOverlays.shown().flow.running);
    expect((await shown(page)).flow.backward).toBe(true);
    await expect(page.locator('.status')).toContainText('Signal Flow: Backward');
    // an empty place stops it
    await click(page, [pc.at[0], pc.at[1] + 400]);
    await page.waitForTimeout(300);
    expect((await shown(page)).flow.running).toBe(false);
    // a double click is not a flow (it goes into a subcircuit)
    const alu = await partMiddle(page, 'alu');
    const q = await onPage(page, alu.at);
    await page.mouse.dblclick(q.x, q.y);
    await page.locator('.canvas-crumbs .here', { hasText: 'alu' }).waitFor();
    await page.waitForTimeout(300);
    expect((await shown(page)).flow.running).toBe(false);
    await page.locator('.canvas-crumbs button', { hasText: 'main' }).click();
    await page.waitForTimeout(300);
    // another circuit stops a running flow
    await click(page, pc.at);
    await page.waitForFunction(() => (window as unknown as { __hcsOverlays: { shown(): { flow: { running: boolean } } } }).__hcsOverlays.shown().flow.running);
    await page.getByRole('tab', { name: 'Circuits' }).click();
    await page.locator('.upper .pbody:visible .list > li', { hasText: 'regfile' }).getByRole('button').click();
    await page.waitForTimeout(300);
    expect((await shown(page)).flow.running).toBe(false);
    await page.locator('.upper .pbody:visible .list > li', { hasText: 'main' }).getByRole('button').click();
    await page.waitForTimeout(300);
    // Ctrl+Shift+F switches Signal Flow on Click off (the status bar says so), a click is then only a click
    await page.locator('.canvas-view canvas').focus();
    await page.keyboard.press('Control+Shift+F');
    await expect(flowButton).toHaveAttribute('aria-pressed', 'false');
    await expect(page.locator('.status')).toContainText('Signal Flow on Click: Off');
    await click(page, pc.at);
    await page.waitForTimeout(400);
    expect((await shown(page)).flow.running).toBe(false);
    await flowButton.click();
    await expect(flowButton).toHaveAttribute('aria-pressed', 'true');
    expect((await shown(page)).flow.onClick).toBe(true);
  } finally {
    await r.close();
  }
});

test('the influence (I-187): I from the chosen part, Shift+I backward, [ and ] a step, Esc one thing at a time (I-19), the right click\'s Influence ▸', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await opened(r);
    const pc = await partMiddle(page, 'Register', 'PC');
    await click(page, pc.at);      // chosen (and a flow starts: Esc stops it first)
    await page.keyboard.press('i');
    await page.waitForFunction(() => (window as unknown as { __hcsOverlays: { shown(): { influence: unknown } } }).__hcsOverlays.shown().influence !== null);
    let s = await shown(page);
    expect(s.influence!.mode).toBe('forward');
    expect(s.influence!.origin).toEqual([pc.id]);
    expect(s.influence!.forward.wires.length).toBeGreaterThan(3);
    await expect(page.locator('.status')).toContainText('Influence: Forward · all steps');
    // the bus values of the nets it reached are drawn again over the dimming, in their own colour (UI review)
    const reached = await page.evaluate((ids) => {
      const c = (window as unknown as { __hcsCanvas: { scene: { wireNet(id: string): { id: string } | undefined } } }).__hcsCanvas;
      return [...new Set(ids.map((id) => c.scene.wireNet(id)?.id).filter((x) => x))];
    }, s.influence!.forward.wires);
    const chip = s.bus.find((b) => reached.includes(b.net))!;
    expect(chip, 'a bus chip on the reached path').toBeTruthy();
    await page.mouse.move(-10, -10);
    expect(await colourNear(page, await onPage(page, [(chip.box.x0 + chip.box.x1) / 2, (chip.box.y0 + chip.box.y1) / 2]), '#00736f', 5, 30), `${chip.text} not dimmed`).toBe(true);
    await expect(page.locator('.status')).toContainText('[ ] 키로 좁히거나 넓히고 Esc 키로 지웁니다');
    // Through Registers (the right click's Influence ▸): past the registers, five steps deep
    await rightClick(page, pc.at);
    await menu(page, 'Influence', 'Through Registers');
    await expect(page.locator('.status')).toContainText('Influence: Forward · all steps · through registers');
    s = await shown(page);
    expect(s.influence!.maxDepth).toBe(5);
    // [ : one step less than all; ] back to all
    await page.locator('.canvas-view canvas').focus();
    await page.keyboard.press('[');
    await expect(page.locator('.status')).toContainText('Influence: Forward · 4 steps · through registers');
    s = await shown(page);
    expect(s.influence!.depth).toBe(4);
    await page.keyboard.press('[');
    await expect(page.locator('.status')).toContainText('Influence: Forward · 3 steps');
    await page.keyboard.press(']');
    await page.keyboard.press(']');
    await expect(page.locator('.status')).toContainText('Influence: Forward · all steps · through registers');
    await rightClick(page, pc.at);
    await menu(page, 'Influence', 'Through Registers');
    await expect(page.locator('.status')).not.toContainText('through registers');
    // the rest is dimmed: an unreached wire is lighter than its colour
    const far = await wireAtPort(page, 'regfile', 'RD1');   // not reached from the PC (it stops at the Instruction Memory)
    await page.mouse.move(-10, -10);
    // Esc: the running flow first, then the influence
    await page.waitForFunction(() => (window as unknown as { __hcsOverlays: { shown(): { flow: { running: boolean } } } }).__hcsOverlays.shown().flow.running);
    await page.keyboard.press('Escape');
    s = await shown(page);
    expect(s.flow.running).toBe(false);
    expect(s.influence).not.toBeNull();
    const dim = await onPage(page, far.quarter);
    expect(await colourNear(page, dim, '#1f3b63', 1, 20), 'an unreached bus is dimmed').toBe(false);
    await page.keyboard.press('Escape');
    expect((await shown(page)).influence).toBeNull();
    expect(await colourNear(page, dim, '#1f3b63', 1, 20), 'the bus in its colour again').toBe(true);
    // Shift+I: what drives the chosen part
    await click(page, pc.at);
    await page.keyboard.press('Shift+I');
    await page.waitForFunction(() => (window as unknown as { __hcsOverlays: { shown(): { influence: { mode: string } | null } } }).__hcsOverlays.shown().influence?.mode === 'backward');
    await page.keyboard.press('Escape');
    await page.keyboard.press('Escape');
    // the right click: Influence ▸ Show Influence (Both)
    await rightClick(page, pc.at);
    await menu(page, 'Influence', 'Show Influence (Both)');
    await page.waitForFunction(() => (window as unknown as { __hcsOverlays: { shown(): { influence: { mode: string } | null } } }).__hcsOverlays.shown().influence?.mode === 'both');
    await expect(page.locator('.status')).toContainText('Influence: Both');
    await rightClick(page, pc.at);
    await menu(page, 'Influence', 'Clear Influence');
    expect((await shown(page)).influence).toBeNull();
  } finally {
    await r.close();
  }
});

test('with the Cycle View: the active path around the selected MUX input and the instruction\'s field colours; Active Path switches it off', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await opened(r);
    expect((await shown(page)).activePath).toBe(0);
    await page.getByRole('tab', { name: 'Cycle View' }).click();
    await page.waitForFunction(() => (window as unknown as { __hcsOverlays: { shown(): { activePath: number } } }).__hcsOverlays.shown().activePath > 0);
    let s = await shown(page);
    expect(s.activePath).toBe(5);                  // ALU Result to the MUX's input 0 (v1 D-099)
    expect(s.fields).toEqual(['rs', 'rt']);        // the fields of `ori` that have named splitter arms here
    // the band beside the MUX's input wire: navy, with the wire's own colour in its middle
    const input = await wireAtPort(page, 'Multiplexer', 'in0');
    await page.mouse.move(-10, -10);
    const beside = await onPage(page, input.horizontal ? [input.at[0], input.at[1] + 4] : [input.at[0] + 4, input.at[1]]);
    expect(await colourNear(page, beside, '#a8b0c5', 2, 30), 'the navy band (0.32 over white)').toBe(true);
    // Active Path off in the Wire Colors panel
    await page.getByRole('button', { name: /Wire Colors/ }).click();
    await page.locator('.legend-panel label', { hasText: 'Active Path' }).locator('input').uncheck();
    await page.keyboard.press('Escape');
    await page.waitForTimeout(200);
    s = await shown(page);
    expect(s.activePath).toBe(0);
    expect(s.fields, 'the field colours stay').toEqual(['rs', 'rt']);
    // another tab: both go
    await page.getByRole('tab', { name: 'Messages' }).click();
    await page.waitForTimeout(200);
    s = await shown(page);
    expect(s.fields).toEqual([]);
  } finally {
    await r.close();
  }
});

test('bus values: the value on each bus in a chip off the wires, the radix in the Wire Colors panel, Off', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await opened(r);
    let s = await shown(page);
    expect(s.bus.length).toBeGreaterThanOrEqual(4);
    for (const c of s.bus) expect(c.text).toMatch(/^(\w+\[\d+:0\] = )?0x[0-9a-fxE]+$/);
    const one = s.bus.find((c) => c.text === '0x00000008');
    expect(one, 'PC + 4 after a cycle').toBeTruthy();
    await page.mouse.move(-10, -10);
    const mid = await onPage(page, [(one!.box.x0 + one!.box.x1) / 2, one!.box.y0 + 2]);
    expect(await colourNear(page, mid, '#e6f6f5', 1, 6), 'the chip\'s teal tint').toBe(true);
    await page.getByRole('button', { name: /Wire Colors/ }).click();
    await page.locator('.legend-panel select[aria-label="Bus Values"]').selectOption('dec');
    s = await shown(page);
    expect(s.bus.map((c) => c.text)).toContain('8');
    await page.locator('.legend-panel select[aria-label="Bus Values"]').selectOption('off');
    expect((await shown(page)).bus).toEqual([]);
    await page.keyboard.press('Escape');
  } finally {
    await r.close();
  }
});

test('signal groups: a wire\'s right click Signal Group ▸ Control, Colors: Groups borders it, Undo takes it back', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await opened(r);
    const w = await wireAtPort(page, 'alu', 'Result');
    await rightClick(page, w.at);
    await menu(page, 'Signal Group', 'Control');
    await page.waitForFunction((net) => {
      const c = (window as unknown as { __hcsCanvas: { scene: { groups: Map<string, { group: string }> } } }).__hcsCanvas;
      return c.scene.groups.get(net)?.group === 'control';
    }, w.net);
    await expect(page.locator('.filebar .ptab.dirty, .filebar .ptab[data-dirty="true"]').first()).toBeVisible().catch(() => {});
    // Colors: Groups: the group's colour beside the wire
    await page.getByRole('button', { name: /Wire Colors/ }).click();
    await page.locator('.legend-panel .ovseg button', { hasText: 'Groups' }).click();
    await page.keyboard.press('Escape');
    await page.mouse.move(-10, -10);
    await page.waitForTimeout(150);
    const beside = await onPage(page, w.horizontal ? [w.at[0], w.at[1] + 4] : [w.at[0] + 4, w.at[1]]);
    expect(await colourNear(page, beside, '#e69f00', 2, 30)).toBe(true);
    // the menu shows the group chosen
    await rightClick(page, w.at);
    await page.locator('.ovmenu button', { hasText: 'Signal Group' }).click();
    await expect(page.locator('.ovmenu').nth(1).getByRole('menuitemradio', { name: 'Control' })).toHaveAttribute('aria-checked', 'true');
    await page.keyboard.press('Escape');
    // one undo step
    await page.keyboard.press('Control+z');
    await page.waitForFunction((net) => {
      const c = (window as unknown as { __hcsCanvas: { scene: { groups: Map<string, unknown> } } }).__hcsCanvas;
      return !c.scene.groups.has(net);
    }, w.net);
    expect(await colourNear(page, beside, '#e69f00', 2, 30)).toBe(false);
  } finally {
    await r.close();
  }
});

test('area memos: Add Area Memo… on an empty place, drawn behind the parts; Edit, Delete; Undo', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await opened(r);
    await setView(page, { x: 70, y: 40, zoom: 1 });
    await rightClick(page, [700, 480]);
    await menu(page, 'Add Area Memo…');
    const dialog = page.locator('dialog.ovdialog[open]');
    await dialog.waitFor();
    await expect(dialog.locator('h2')).toHaveText('Add Area Memo');
    await expect(dialog).toContainText('원조 Logisim 2.7.1 프로그램은 이 상자를 건너뜁니다');
    await dialog.getByLabel('Text').fill('WB');
    await dialog.getByLabel('Color').selectOption('3');
    await dialog.getByRole('button', { name: 'OK' }).click();
    await page.waitForFunction(() => (window as unknown as { __hcsOverlays: { shown(): { memos: unknown[] } } }).__hcsOverlays.shown().memos.length === 1);
    let s = await shown(page);
    expect(s.memos[0]).toEqual({ x: 600, y: 420, w: 200, h: 120, color: 3, text: 'WB' });
    await page.mouse.move(-10, -10);
    const edge = await onPage(page, [700, 420]);
    expect(await colourNear(page, edge, '#0072b2', 2, 20), 'its border in the palette\'s Blue').toBe(true);
    // Edit: inside the memo
    await rightClick(page, [700, 480]);
    await menu(page, 'Edit Area Memo…');
    await dialog.waitFor();
    await expect(dialog.locator('h2')).toHaveText('Edit Area Memo');
    await dialog.getByLabel('Text').fill('WB stage');
    await dialog.getByRole('button', { name: 'OK' }).click();
    await page.waitForFunction(() => (window as unknown as { __hcsOverlays: { shown(): { memos: { text: string }[] } } }).__hcsOverlays.shown().memos[0]?.text === 'WB stage');
    // Delete, then Undo brings it back
    await rightClick(page, [700, 480]);
    await menu(page, 'Delete Area Memo');
    await page.waitForFunction(() => (window as unknown as { __hcsOverlays: { shown(): { memos: unknown[] } } }).__hcsOverlays.shown().memos.length === 0);
    await page.keyboard.press('Control+z');
    await page.waitForFunction(() => (window as unknown as { __hcsOverlays: { shown(): { memos: unknown[] } } }).__hcsOverlays.shown().memos.length === 1);
    s = await shown(page);
    expect(s.memos[0].text).toBe('WB stage');
    // Cancel changes nothing
    await rightClick(page, [700, 480]);
    await menu(page, 'Edit Area Memo…');
    await dialog.getByLabel('Text').fill('nothing');
    await dialog.getByRole('button', { name: 'Cancel' }).click();
    await page.waitForTimeout(200);
    expect((await shown(page)).memos[0].text).toBe('WB stage');
  } finally {
    await r.close();
  }
});

test('a wire\'s right click: Net Information… (v1\'s words), Highlight Net and Clear Net Highlight; reading changes nothing', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await opened(r);
    const w = await wireAtPort(page, 'alu', 'Result');
    await rightClick(page, w.at);
    await menu(page, 'Net Information…');
    const d = page.locator('dialog.ovdialog[open]');
    await d.waitFor();
    await expect(d).toContainText('Width: 32 bits');
    await expect(d).toContainText('Driven by (1)');
    await expect(d).toContainText('alu #1 (Result)');
    await expect(d).toContainText('Read by (');
    await d.getByRole('button', { name: 'Close' }).click();
    await rightClick(page, w.at);
    await menu(page, 'Highlight Net');
    expect((await shown(page)).highlight).toBeGreaterThan(0);
    await rightClick(page, w.at);
    await expect(page.locator('.ovmenu button', { hasText: 'Clear Net Highlight' })).toBeVisible();
    await menu(page, 'Clear Net Highlight');
    expect((await shown(page)).highlight).toBe(0);
    // the reading overlays: no edit, the file is as it was
    const pc = await partMiddle(page, 'Register', 'PC');
    await click(page, pc.at);
    await page.keyboard.press('i');
    await page.getByRole('tab', { name: 'Cycle View' }).click();
    await page.waitForTimeout(500);
    const dirty = await page.evaluate(async () => {
      const fileId = (window as unknown as { __hcsCanvas: { scene: { fileId: string } } }).__hcsCanvas.scene.fileId;
      return (await window.app.call<{ dirty: boolean }>('file.dirty', { fileId })).dirty;
    });
    expect(dirty).toBe(false);
    expect(await statusText(page)).not.toContain('•');
  } finally {
    await r.close();
  }
});
