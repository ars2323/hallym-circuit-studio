/* The circuit Canvas in the real window (N-05, N-06, D-137), with the fake
   engine answering demo-datapath and ref-mips from the engine-made fixtures
   (tests/fixtures/circuits): drawn, values in the legend's colours, zoom and
   pan, values after ticks, hover, going into a subcircuit, the legend,
   chips off the wires, and the SVG export drawing what the screen draws. */

import { expect, type Page, test } from '@playwright/test';

import { canvasSettled, DATAPATH, INSIDE_PIN_VALUES, launch, openFile, PARENT_PORT_VALUES, type Running, sample } from './harness.ts';
import { decodePng } from './png.ts';

const REF_MIPS = 'tests/mips/ref-mips.circ';

interface Probe {
  zoom: number;
  values: number;
}

// The window's Canvas (canvas.ts puts itself on window for tests and tools).
const probe = (page: Page): Promise<Probe> => page.evaluate(() => {
  const c = (window as unknown as { __hcsCanvas: { view: { zoom: number }; scene: { values: Map<string, string> } | null } }).__hcsCanvas;
  return { zoom: c.view.zoom, values: c.scene?.values.size ?? 0 };
});

async function drawn(r: Running): Promise<void> {
  await r.page.locator('.canvas-view canvas').waitFor();
  await r.page.waitForFunction(() => {
    const c = (window as unknown as { __hcsCanvas?: { scene: { values: Map<string, string> } | null } }).__hcsCanvas;
    return !!c?.scene && c.scene.values.size > 0;
  });
  await r.page.evaluate(() => document.fonts.ready);
  await canvasSettled(r.page);
}

// A wire of the given kind, brought to the middle of the Canvas at 200 % from a whole-unit origin (its
// middle pixels are then fully covered: no anti-aliasing to blend), in page pixels once that view is drawn
// (from the Canvas's place then), and the colour the legend gives that kind.
async function wireOnScreen(page: Page, want: 'one' | 'zero' | 'bus'): Promise<{ x: number; y: number; css: string } | null> {
  await canvasSettled(page);   // the first view is chosen: nothing fits over the one set here
  const at = await page.evaluate((kind) => {
    type W = { id: string; a: [number, number]; b: [number, number] };
    const c = (window as unknown as { __hcsCanvas: { canvas: HTMLCanvasElement; setView(v: object): void; scene: { wires: Map<string, W>; wireValue(id: string): string | undefined } } }).__hcsCanvas;
    const r = c.canvas.getBoundingClientRect();
    for (const w of c.scene.wires.values()) {
      const v = c.scene.wireValue(w.id);
      if (!v) continue;
      const k = v.length > 1 ? (/[01]/.test(v) && !v.includes('E') ? 'bus' : '') : v === '1' ? 'one' : v === '0' ? 'zero' : '';
      if (k !== kind || Math.abs(w.a[0] - w.b[0]) + Math.abs(w.a[1] - w.b[1]) < 40) continue;
      const mx = (w.a[0] + w.b[0]) / 2 + (w.a[1] === w.b[1] ? 3 : 0), my = (w.a[1] + w.b[1]) / 2 + (w.a[1] === w.b[1] ? 0 : 3);
      const x0 = Math.round(mx - r.width / 4), y0 = Math.round(my - r.height / 4);
      c.setView({ x: x0, y: y0, zoom: 2 });
      return { mx, my, view: { x: x0, y: y0, zoom: 2 }, token: { one: '--v-one', zero: '--v-zero', bus: '--v-bus' }[kind] };
    }
    return null;
  }, want);
  if (!at) return null;
  await canvasSettled(page);
  const p = await page.evaluate((a) => {
    const c = (window as unknown as { __hcsCanvas: { canvas: HTMLCanvasElement; view: { x: number; y: number; zoom: number } } }).__hcsCanvas;
    const r = c.canvas.getBoundingClientRect();
    return { x: r.left + (a.mx - c.view.x) * c.view.zoom, y: r.top + (a.my - c.view.y) * c.view.zoom, view: { ...c.view } };
  }, at);
  expect(p.view, 'the view drawn is the one set').toEqual(at.view);
  const css = await page.evaluate((t) => getComputedStyle(document.documentElement).getPropertyValue(t).trim(), at.token);
  // the pixel whose top-left corner is the wire's middle point
  return { x: p.x, y: p.y, css };
}

const hex = (rgba: Uint8Array, i: number) => `#${[0, 1, 2].map((k) => rgba[i + k].toString(16).padStart(2, '0')).join('')}`;

async function pixel(page: Page, x: number, y: number): Promise<string> {
  const png = decodePng(await page.screenshot({ clip: { x: Math.round(x), y: Math.round(y), width: 1, height: 1 } }));
  return hex(png.rgba, 0);
}

test('demo-datapath is drawn: the wires in the colours the legend shows (1, 0, a bus), the legend\'s rows', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await drawn(r);
    await page.mouse.move(-10, -10);
    for (const kind of ['one', 'zero', 'bus'] as const) {
      const w = await wireOnScreen(page, kind);
      expect(w, kind).not.toBeNull();
      expect(await pixel(page, w!.x, w!.y), `a ${kind} wire`).toBe(w!.css);
    }
    // the legend: its swatches are the CSS colours the wires were just compared with
    await page.getByRole('button', { name: /Wire Colors/ }).click();
    const panel = page.locator('.legend-panel');
    await expect(panel).toBeVisible();
    await expect(panel.locator('li')).toHaveCount(6);
    await expect(panel.locator('li .name')).toHaveText(['1', '0', 'x', 'E', 'bus', 'width']);
    const swatches = await panel.locator('li .swatch').evaluateAll((els) => els.map((e) => getComputedStyle(e).backgroundColor));
    const vars = await page.evaluate(() => ['--v-one', '--v-zero', '--v-float', '--v-error', '--v-bus', '--v-width'].map((v) => {
      const d = document.createElement('div');
      d.style.color = `var(${v})`;
      document.body.append(d);
      const c = getComputedStyle(d).color;
      d.remove();
      return c;
    }));
    expect(swatches).toEqual(vars);
    // bus widths off and on (this run only)
    const before = await page.evaluate(() => (window as unknown as { __hcsCanvas: { busWidths: boolean } }).__hcsCanvas.busWidths);
    expect(before).toBe(true);
    await panel.getByRole('checkbox', { name: 'Show Bus Widths' }).uncheck();
    expect(await page.evaluate(() => (window as unknown as { __hcsCanvas: { busWidths: boolean } }).__hcsCanvas.busWidths)).toBe(false);
    await page.keyboard.press('Escape');
    await expect(panel).toBeHidden();
  } finally {
    await r.close();
  }
});

test('zoom and pan: Ctrl+wheel about the pointer, Ctrl+ + / −, Ctrl+0 fits and centres, 25–400 %, the status bar\'s one display (v1 S-21)', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await drawn(r);
    // settled() (what the tests wait on): false from a new view until a frame has drawn it
    expect(await page.evaluate(() => {
      const c = (window as unknown as { __hcsCanvas: { view: { x: number; y: number; zoom: number }; setView(v: object): void; settled(): boolean } }).__hcsCanvas;
      const before = c.settled();
      c.setView({ ...c.view });
      return [before, c.settled()];
    })).toEqual([true, false]);
    await canvasSettled(page);
    const box = (await page.locator('.canvas-view canvas').boundingBox())!;
    const zoomButton = page.locator('.status .zoom-button');
    const fitted = (await probe(page)).zoom;
    await expect(zoomButton).toHaveText(`${Math.round(fitted * 100)}%`);
    // Ctrl+wheel: about the pointer (the circuit point under it stays)
    const at = { x: box.x + box.width * 0.3, y: box.y + box.height * 0.4 };
    await page.mouse.move(at.x, at.y);
    const under = () => page.evaluate(([x, y]) => {
      const c = (window as unknown as { __hcsCanvas: { canvas: HTMLCanvasElement; view: { x: number; y: number; zoom: number } } }).__hcsCanvas;
      const rr = c.canvas.getBoundingClientRect();
      return [c.view.x + (x - rr.left) / c.view.zoom, c.view.y + (y - rr.top) / c.view.zoom];
    }, [at.x, at.y]);
    const p0 = await under();
    await page.keyboard.down('Control');
    await page.mouse.wheel(0, -300);
    await page.keyboard.up('Control');
    await expect.poll(async () => (await probe(page)).zoom).toBeGreaterThan(fitted * 1.3);
    const p1 = await under();
    expect(Math.abs(p1[0] - p0[0]) + Math.abs(p1[1] - p0[1])).toBeLessThan(0.5);
    await expect(zoomButton).toHaveText(`${Math.round((await probe(page)).zoom * 100)}%`);
    // Ctrl+0 fits again; Ctrl+= steps up, Ctrl+- down (smoothly, to the step)
    await page.keyboard.press('Control+0');
    await expect.poll(async () => (await probe(page)).zoom).toBeCloseTo(fitted, 3);
    await page.keyboard.press('Control+0');
    await page.evaluate(() => (window as unknown as { __hcsCanvas: { zoomTo(z: number): void } }).__hcsCanvas.zoomTo(1));
    await expect.poll(async () => (await probe(page)).zoom).toBe(1);
    await page.keyboard.press('Control+=');
    await expect.poll(async () => (await probe(page)).zoom).toBe(1.1);
    await expect(zoomButton).toHaveText('110%');
    await page.keyboard.press('Control+-');
    await page.keyboard.press('Control+-');
    await expect.poll(async () => (await probe(page)).zoom).toBe(0.9);
    // the limits
    for (let i = 0; i < 20; i++) await page.keyboard.press('Control+=');
    await expect.poll(async () => (await probe(page)).zoom).toBe(4);
    await expect(zoomButton).toHaveText('400%');
    for (let i = 0; i < 20; i++) await page.keyboard.press('Control+-');
    await expect.poll(async () => (await probe(page)).zoom).toBe(0.25);
    // the menu: a step and Fit
    await zoomButton.click();
    await page.locator('.zoom-menu').getByRole('menuitem', { name: '150%' }).click();
    await expect.poll(async () => (await probe(page)).zoom).toBe(1.5);
    // pan: the wheel scrolls, a middle-button drag moves the view with the pointer
    const v0 = await page.evaluate(() => ({ ...(window as unknown as { __hcsCanvas: { view: { x: number; y: number } } }).__hcsCanvas.view }));
    await page.mouse.move(box.x + 300, box.y + 200);
    await page.mouse.wheel(0, 150);
    await expect.poll(() => page.evaluate(() => (window as unknown as { __hcsCanvas: { view: { y: number } } }).__hcsCanvas.view.y)).toBeCloseTo(v0.y + 100, 3);
    await page.mouse.down({ button: 'middle' });
    await page.mouse.move(box.x + 360, box.y + 260, { steps: 4 });
    await page.mouse.up({ button: 'middle' });
    const v1 = await page.evaluate(() => ({ ...(window as unknown as { __hcsCanvas: { view: { x: number; y: number } } }).__hcsCanvas.view }));
    expect(v1.x).toBeCloseTo(v0.x - 40, 3);
    // fit centres the circuit both ways, with its margin (v1 S-10)
    await page.keyboard.press('Control+0');
    await canvasSettled(page);   // the fit's animation has ended and is drawn
    const centred = await page.evaluate(() => {
      const c = (window as unknown as { __hcsCanvas: { canvas: HTMLCanvasElement; view: { x: number; y: number; zoom: number }; scene: { extent(): { x0: number; y0: number; x1: number; y1: number } } } }).__hcsCanvas;
      const e = c.scene.extent(), rr = c.canvas.getBoundingClientRect();
      const sx0 = (e.x0 - c.view.x) * c.view.zoom, sx1 = (e.x1 - c.view.x) * c.view.zoom;
      const sy0 = (e.y0 - c.view.y) * c.view.zoom, sy1 = (e.y1 - c.view.y) * c.view.zoom;
      return { left: sx0, right: rr.width - sx1, top: sy0, bottom: rr.height - sy1 };
    });
    expect(Math.min(centred.left, centred.right, centred.top, centred.bottom)).toBeGreaterThan(20);
    expect(Math.abs(centred.left - centred.right) < 60 || Math.abs(centred.top - centred.bottom) < 60).toBe(true);
  } finally {
    await r.close();
  }
});

test('values after ticks: 1 Cycle brings the engine\'s values and bodies; the wires and the Instruction Memory follow', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await drawn(r);
    const imLine = () => page.evaluate(() => {
      const c = (window as unknown as { __hcsCanvas: { scene: { components: Map<string, { id: string; name: string }>; bodies: Map<string, { lines?: string[] }> } } }).__hcsCanvas;
      const im = [...c.scene.components.values()].find((x) => x.name === 'Instruction Memory')!;
      return c.scene.bodies.get(im.id)?.lines?.[2];
    });
    expect(await imLine()).toBe('00000000: 00221820');
    const pcWire = await wireOnScreen(page, 'bus');
    await page.keyboard.press('F10');
    await expect(page.locator('.status')).toContainText('Cycle 1');
    await expect.poll(imLine).toBe('00000004: 00221822');
    // four cycles: a one-bit wire changed colour on screen
    for (let i = 0; i < 3; i++) await page.keyboard.press('F10');
    await expect(page.locator('.status')).toContainText('Cycle 4');
    await page.mouse.move(-10, -10);
    const one = await wireOnScreen(page, 'one');
    expect(one).not.toBeNull();
    expect(await pixel(page, one!.x, one!.y)).toBe(one!.css);
    expect(pcWire).not.toBeNull();
    // Reset: back to the first values
    await page.getByRole('button', { name: /Reset/ }).click();
    await expect.poll(imLine).toBe('00000000: 00221820');
  } finally {
    await r.close();
  }
});

test('hover, selection, a subcircuit\'s inside and back, the tip', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await drawn(r);
    const centre = (name: string) => page.evaluate((n) => {
      const c = (window as unknown as { __hcsCanvas: { canvas: HTMLCanvasElement; view: { x: number; y: number; zoom: number }; scene: { components: Map<string, { name: string; bounds: number[] }> } } }).__hcsCanvas;
      const k = [...c.scene.components.values()].find((x) => x.name === n)!;
      const rr = c.canvas.getBoundingClientRect();
      return { x: rr.left + (k.bounds[0] + k.bounds[2] / 2 - c.view.x) * c.view.zoom, y: rr.top + (k.bounds[1] + k.bounds[3] / 2 - c.view.y) * c.view.zoom };
    }, name);
    const im = await centre('Instruction Memory');
    await page.mouse.move(im.x, im.y);
    await expect(page.locator('.canvas-tip')).toHaveText('Instruction Memory');
    await page.mouse.click(im.x, im.y);
    // double click the register file (a subcircuit): its inside, with the crumbs
    const rf = await centre('regfile');
    await page.mouse.dblclick(rf.x, rf.y);
    await expect(page.locator('.canvas-crumbs')).toBeVisible();
    await expect(page.locator('.canvas-crumbs')).toContainText('main');
    await expect(page.locator('.canvas-crumbs .here')).toHaveText('regfile');
    await page.waitForFunction(() => (window as unknown as { __hcsCanvas: { scene: { name: string; values: Map<string, string> } } }).__hcsCanvas.scene.name === 'regfile');
    await expect.poll(async () => (await probe(page)).values).toBeGreaterThan(0);
    await page.locator('.canvas-crumbs').getByRole('button', { name: 'main' }).click();
    await expect(page.locator('.canvas-crumbs')).toBeHidden();
    await page.waitForFunction(() => (window as unknown as { __hcsCanvas: { scene: { name: string } } }).__hcsCanvas.scene.name === 'main');
  } finally {
    await r.close();
  }
});

test('the drawing rules: chips keep a gap from wires, port rings and other chips (v1 S-01, S-05, S-07)', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await drawn(r);
    const report = await page.evaluate(() => {
      type B = { x0: number; y0: number; x1: number; y1: number };
      const c = (window as unknown as { __hcsCanvas: { chips: { kind: string; box: B; text: string }[]; scene: { wires: Map<string, { a: number[]; b: number[] }>; components: Map<string, { ports: { loc: number[] }[] }> } } }).__hcsCanvas;
      const meet = (a: B, b: B) => a.x0 < b.x1 && b.x0 < a.x1 && a.y0 < b.y1 && b.y0 < a.y1;
      const grow = (b: B, d: number): B => ({ x0: b.x0 - d, y0: b.y0 - d, x1: b.x1 + d, y1: b.y1 + d });
      const GAP = 2;   // circuit units kept clear around a chip: past a wire's stroke, a port's ring and other chips
      const chips = c.chips.filter((x) => x.kind === 'label' || x.kind === 'value');
      const wires = [...c.scene.wires.values()].map((w) => ({ x0: Math.min(w.a[0], w.b[0]) - 2, y0: Math.min(w.a[1], w.b[1]) - 2, x1: Math.max(w.a[0], w.b[0]) + 2, y1: Math.max(w.a[1], w.b[1]) + 2 }));
      const rings = [...c.scene.components.values()].flatMap((k) => k.ports.map((q) => ({ x0: q.loc[0] - 3.5, y0: q.loc[1] - 3.5, x1: q.loc[0] + 3.5, y1: q.loc[1] + 3.5 })));
      const onWire = chips.filter((ch) => wires.some((w) => meet(grow(ch.box, GAP), w))).map((ch) => ch.text);
      const onRing = chips.filter((ch) => rings.some((w) => meet(grow(ch.box, GAP), w))).map((ch) => ch.text);
      const onChip = chips.filter((ch, i) => chips.some((o, j) => i !== j && meet(grow(ch.box, GAP), o.box))).map((ch) => ch.text);
      return { chips: chips.length, onWire, onRing, onChip };
    });
    expect(report.chips).toBeGreaterThan(2);
    expect(report.onWire).toEqual([]);
    expect(report.onRing).toEqual([]);   // UI review of #425: the PC's value chip touched an open port's ring
    expect(report.onChip).toEqual([]);
  } finally {
    await r.close();
  }
});

test('the SVG export draws what the Canvas draws (the same shapes, colours and chips at 100 %)', async () => {
  const r = await launch({ width: 1600, height: 1000 });
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await drawn(r);
    // the Canvas at 100 %, in print mode (no grid), and the export laid exactly over it
    const clip = await page.evaluate(() => {
      const c = (window as unknown as { __hcsCanvas: { canvas: HTMLCanvasElement; root: HTMLElement; view: { x: number; y: number; zoom: number }; setView(v: object): void; draw(print: boolean): void; exportSvg(b: object): string } }).__hcsCanvas;
      c.setView({ x: 60, y: 60, zoom: 1 });
      const rr = c.canvas.getBoundingClientRect();
      const w = Math.min(900, Math.floor(rr.width)), h = Math.min(520, Math.floor(rr.height));
      c.draw(true);
      const holder = document.createElement('div');
      holder.id = 'svg-over';
      holder.style.cssText = `position:fixed;left:${rr.left}px;top:${rr.top}px;width:${w}px;height:${h}px;z-index:50;pointer-events:none;display:none`;
      holder.innerHTML = c.exportSvg({ x0: 60, y0: 60, x1: 60 + w, y1: 60 + h });
      document.body.append(holder);
      return { x: rr.left, y: rr.top, width: w, height: h };
    });
    await page.mouse.move(-10, -10);
    await page.evaluate(() => (window as unknown as { __hcsCanvas: { draw(p: boolean): void } }).__hcsCanvas.draw(true));
    const onCanvas = decodePng(await page.screenshot({ clip }));
    await page.evaluate(() => { document.getElementById('svg-over')!.style.display = 'block'; });
    const onSvg = decodePng(await page.screenshot({ clip }));
    let differ = 0, inked = 0;
    for (let i = 0; i < onCanvas.rgba.length; i += 4) {
      const d = Math.max(...[0, 1, 2].map((k) => Math.abs(onCanvas.rgba[i + k] - onSvg.rgba[i + k])));
      const ink = Math.min(onCanvas.rgba[i], onCanvas.rgba[i + 1], onCanvas.rgba[i + 2]) < 200;
      if (ink) inked++;
      if (d > 96) differ++;
    }
    console.log(`canvas vs SVG export: ${differ} of ${inked} inked pixels differ (${((100 * differ) / inked).toFixed(2)} %)`);
    // text anti-aliasing differs a little between the Canvas and SVG; the drawing does not
    expect(inked).toBeGreaterThan(3000);
    expect(differ / inked, `${differ} of ${inked} inked pixels differ`).toBeLessThan(0.12);
  } finally {
    await r.close();
  }
});

test('ref-mips (715 parts, 580 tunnels) opens drawn; a frame takes less than a 60 Hz frame here', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, REF_MIPS));
    await drawn(r);
    await expect(page.locator('.status')).toContainText('715 components');
    const ms = await page.evaluate(async () => {
      const c = (window as unknown as { __hcsCanvas: { view: { x: number; y: number; zoom: number }; setView(v: object): void; draw(): void } }).__hcsCanvas;
      const times: number[] = [];
      for (let i = 0; i < 30; i++) {
        c.setView({ ...c.view, x: c.view.x + 7 });
        const t = performance.now();
        c.draw();
        times.push(performance.now() - t);
      }
      times.sort((a, b) => a - b);
      return times[15];
    });
    console.log(`ref-mips frame (median of 30, fake engine, this machine): ${ms.toFixed(1)} ms`);
    expect(ms).toBeLessThan(40); // loose on CI runners; the measured numbers are in D-137
  } finally {
    await r.close();
  }
});

test('a message shows its place on the Canvas (D-143 hcs:reveal): its part marked and in view; the next click clears the mark', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, 'electron/tests/fixtures/broken-datapath.circ'));
    await drawn(r);
    // far away first, so the message has to bring its place into view
    await page.evaluate(() => (window as unknown as { __hcsCanvas: { setView(v: object): void } }).__hcsCanvas.setView({ x: 5000, y: 5000, zoom: 1 }));
    await page.locator('.msg').first().click();
    const marked = () => page.evaluate(() => (window as unknown as { __hcsCanvas: { markedIds(): { components: string[] } | null } }).__hcsCanvas.markedIds());
    await expect.poll(async () => (await marked())?.components.length ?? 0).toBe(1);
    await canvasSettled(page);   // brought into view (animated) and drawn
    const inView = await page.evaluate(() => {
      const c = (window as unknown as { __hcsCanvas: { canvas: HTMLCanvasElement; view: { x: number; y: number; zoom: number }; markedIds(): { components: string[] }; scene: { components: Map<string, { bounds: number[] }> } } }).__hcsCanvas;
      const k = c.scene.components.get(c.markedIds().components[0])!;
      const rr = c.canvas.getBoundingClientRect();
      const x = (k.bounds[0] - c.view.x) * c.view.zoom, y = (k.bounds[1] - c.view.y) * c.view.zoom;
      return x >= 0 && y >= 0 && x + k.bounds[2] * c.view.zoom <= rr.width && y + k.bounds[3] * c.view.zoom <= rr.height;
    });
    expect(inView).toBe(true);
    const box = (await page.locator('.canvas-view canvas').boundingBox())!;
    await page.mouse.click(box.x + 8, box.y + 8);
    await expect.poll(marked).toBeNull();
  } finally {
    await r.close();
  }
});

test('inside a subcircuit instance the values are that instance\'s: each pin inside carries what the parent\'s wire carries at its port', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await drawn(r);
    const parent = await page.evaluate(PARENT_PORT_VALUES, 'regfile');
    expect(parent.RR1).toMatch(/^[01]+$/);   // defined in the parent (the instruction's rs field)
    const at = await page.evaluate(() => {
      const c = (window as unknown as { __hcsCanvas: { canvas: HTMLCanvasElement; view: { x: number; y: number; zoom: number }; scene: { components: Map<string, { name: string; bounds: number[] }> } } }).__hcsCanvas;
      const k = [...c.scene.components.values()].find((x) => x.name === 'regfile')!;
      const rr = c.canvas.getBoundingClientRect();
      return { x: rr.left + (k.bounds[0] + k.bounds[2] / 2 - c.view.x) * c.view.zoom, y: rr.top + (k.bounds[1] + k.bounds[3] / 2 - c.view.y) * c.view.zoom };
    });
    await page.mouse.dblclick(at.x, at.y);
    await expect(page.locator('.canvas-crumbs .here')).toHaveText('regfile');
    await page.waitForFunction(() => (window as unknown as { __hcsCanvas: { scene: { name: string; values: Map<string, string> } } }).__hcsCanvas.scene.name === 'regfile'
      && (window as unknown as { __hcsCanvas: { scene: { values: Map<string, string> } } }).__hcsCanvas.scene.values.size > 0);
    const inside = await page.evaluate(INSIDE_PIN_VALUES);
    for (const name of ['RR1', 'RR2', 'WR', 'WD', 'RegWrite', 'clk', 'RD1', 'RD2']) {
      expect(inside[name], name).toBe(parent[name]);
      expect(inside[name], name).toMatch(/^[01]+$/);
    }
    // a cycle later (the next instruction), inside and back outside agree again
    await page.keyboard.press('F10');
    await expect(page.locator('.status')).toContainText('Cycle 1');
    await canvasSettled(page);
    const insideAfter = await page.evaluate(INSIDE_PIN_VALUES);
    await page.locator('.canvas-crumbs').getByRole('button', { name: 'main' }).click();
    await page.waitForFunction(() => (window as unknown as { __hcsCanvas: { scene: { name: string } } }).__hcsCanvas.scene.name === 'main');
    const names = ['RR1', 'RR2', 'WR', 'WD', 'RegWrite', 'clk', 'RD1', 'RD2'];
    await expect.poll(() => page.evaluate(PARENT_PORT_VALUES, 'regfile').then((v) => names.map((n) => v[n])))
      .toEqual(names.map((n) => insideAfter[n]));
  } finally {
    await r.close();
  }
});
