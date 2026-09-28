/* Editing parity at the screen (N-09, D-159): scenes of the N-01 goldens
   (tests/parity) done in the real window with the real engine through the
   gestures, keys and menus a student uses -- the Components list, the
   toolbar's Wire and Edit tools, clicks and drags on the Canvas, Delete,
   Ctrl+C/X/V/D, Ctrl+Z/Y, the digits and arrows of a held part, F2, the
   status bar's zoom menu -- and the saved .circ compared with the Swing
   golden by the D-006 rule.  This shows the screen sends the intents a user
   makes; EngineParityReplayTest shows the engine does with those intents
   what the Swing app did.

   A few intents have no control on the screen until the attribute table and
   the right-click menus (N-10): a tool's or a part's attribute no key sets
   (a label on a tool, Output?, Size, a constant's value), a circuit's
   attribute, Duplicate N, Align, Only Components/Wires.  Those go through
   the window's own bridge to the engine (`window.app.call`, what those
   controls will call; the journal records them the same), and the test
   lists them per scene -- it fails if anything else takes that way.

   Opt-in, as the other real-engine e2e:
     ./gradlew :engine:stage
     HCS_E2E_REAL_ENGINE=1 xvfb-run -a npx playwright test real-engine-parity */

import { type ElectronApplication, expect, type Page, test } from '@playwright/test';
import { copyFileSync, cpSync, existsSync, mkdirSync, readFileSync } from 'node:fs';
import path from 'node:path';

import { pagePoint } from './canvas-points.ts';
import { overlay, parts, selected, tool, wires } from './edit-points.ts';
import { answerOpen, launch, type LaunchOptions, recordCalls, repo, type Running, sentCalls } from './harness.ts';
import { call, circuitsOf } from './model.ts';

const JAR = path.join(repo, 'engine/build/stage/hcs-engine.jar');
const MIPS = path.join(repo, 'engine/build/stage/hcs-mips.jar');
const TEMPLATE = path.join(repo, 'app/resources/logisim/default.templ');
const PARITY = path.join(repo, 'tests/parity');
const real: LaunchOptions['env'] = { HCS_ENGINE_CMD: '', HCS_ENGINE_JAR: JAR };

test.skip(process.env.HCS_E2E_REAL_ENGINE !== '1', 'opt-in: HCS_E2E_REAL_ENGINE=1 with engine/build/stage built');

type P = [number, number];
type Intent = Record<string, unknown> & { method: string };

// ---- D-006: leading blanks and empty lines, and the order of a circuit's wires and parts, do not count ----

export function normalizeCirc(xml: string): string {
  const out: string[] = [];
  let block: string[] | null = null;
  let items: string[] | null = null;
  for (const raw of xml.split(/\r?\n/)) {
    const line = raw.trim();
    if (!line) continue;
    if (items === null) {
      out.push(line);
      if (line.startsWith('<circuit ')) items = [];
      continue;
    }
    if (block !== null) {
      block.push(line);
      if (line === '</comp>') { items.push(block.join('\n')); block = null; }
    } else if (line.startsWith('<wire ')) {
      items.push(line);
    } else if (line.startsWith('<comp ')) {
      if (line.endsWith('/>')) items.push(line); else block = [line];
    } else if (line === '</circuit>') {
      items.sort((a, b) => (a < b ? -1 : a > b ? 1 : 0));   // Java's String.compareTo: UTF-16 units, as here
      out.push(...items, line);
      items = null;
    } else {
      out.push(line);
    }
  }
  return `${out.join('\n')}\n`;
}

// ---- the keys Logisim's key configurators take (2.7.1's factories) ----
// BitWidthConfigurator(attr) is Alt+digits; the gates' inputs, the MUX's select and the splitter's fan out are digits.
// A splitter's Alt+digits set its bit width *and* fan out (ParallelConfigurator): no key sets the width alone.

const GATES = ['AND Gate', 'OR Gate', 'NAND Gate', 'NOR Gate', 'XOR Gate', 'XNOR Gate'];
function keyFor(name: string, attr: string): 'digits' | 'alt' | null {
  if (GATES.includes(name)) return attr === 'inputs' ? 'digits' : attr === 'width' ? 'alt' : null;
  if (name === 'Multiplexer') return attr === 'select' ? 'digits' : attr === 'width' ? 'alt' : null;
  if (name === 'Splitter') return attr === 'fanout' ? 'digits' : null;
  if (['Pin', 'Register', 'Tunnel', 'Adder', 'NOT Gate', 'Constant'].includes(name)) return attr === 'width' ? 'alt' : null;
  return null;
}
const ARROW: Record<string, string> = { east: 'ArrowRight', west: 'ArrowLeft', north: 'ArrowUp', south: 'ArrowDown' };

// ---- the scene in the window ----

class Screen {
  readonly byApi: string[] = [];            // the intents with no control yet (N-10), in order
  private held = '';                        // lib/name of the part in hand
  private symbols = new Map<string, { id: string; name: string; loc: P }>();
  private fileId = '';
  readonly r: Running;
  constructor(r: Running) { this.r = r; }
  get page(): Page { return this.r.page; }
  get app(): ElectronApplication { return this.r.app; }

  async open(file: string): Promise<void> {
    await answerOpen(this.app, file);
    await this.page.keyboard.press('Control+o');
    await this.page.locator('.filebar .ptab', { hasText: path.basename(file) }).waitFor();
    await recordCalls(this.app);
  }

  // The engine has done every call sent so far (it answers in order) and the window has its notifications.
  async settle(): Promise<void> {
    const ids = await this.app.evaluate(() => [...(globalThis as unknown as { __hcs: { recovery: { journal: { files: Map<string, unknown> } } } }).__hcs.recovery.journal.files.keys()]);
    this.fileId = ids[0];
    await call(this.page, 'file.dirty', { fileId: this.fileId });
    await this.page.evaluate(() => new Promise((done) => requestAnimationFrame(() => requestAnimationFrame(done))));
  }

  private async sent(method: string): Promise<number> { return (await sentCalls(this.app, method)).length; }

  // An intent sent by the gesture just made: waits for it, then for the engine.
  private async expectSent(method: string, before: number): Promise<void> {
    await expect.poll(() => this.sent(method), { message: `the screen sends ${method}` }).toBeGreaterThan(before);
    await this.settle();
  }

  // The window's bridge for an intent with no control yet; circuit names as ids.
  private async api(i: Intent, params: Record<string, unknown>): Promise<void> {
    this.byApi.push(i.method + (typeof i.attr === 'string' ? ` ${i.attr}` : ''));
    await call(this.page, i.method, { fileId: this.fileId, ...params });
    await this.settle();
  }

  private async circuitId(name?: unknown): Promise<string> {
    if (typeof name !== 'string') return (await this.where()).circuitId;
    const id = (await circuitsOf(this.page, this.fileId))[name];
    if (!id) throw new Error(`no circuit ${name}`);
    return id;
  }

  private where(): Promise<{ fileId: string; circuitId: string }> {
    return this.page.evaluate(() => {
      const s = (window as unknown as { __hcsCanvas: { scene: { fileId: string; circuitId: string } } }).__hcsCanvas.scene;
      return { fileId: s.fileId, circuitId: s.circuitId };
    });
  }

  // ---- the view: the zoom from the status bar's menu; the points in sight (panning is the view's only) ----

  // An empty circuit shows no Canvas (and no zoom) until a tool that puts something in is taken: the zoom waits for it.
  private pendingZoom: number | null = null;
  async zoom(factor: number): Promise<void> {
    if (!(await this.page.locator('.zoom-button').isVisible())) { this.pendingZoom = factor; return; }
    this.pendingZoom = null;
    await this.page.locator('.zoom-button').click();
    await this.page.locator('.zoom-menu').getByRole('menuitem', { name: `${Math.round(factor * 100)}%`, exact: true }).click();
    await expect.poll(() => this.page.evaluate(() => (window as unknown as { __hcsCanvas: { view: { zoom: number } } }).__hcsCanvas.view.zoom)).toBeCloseTo(factor, 3);
  }

  private async inSight(points: P[]): Promise<void> {
    await this.page.evaluate((pts) => {
      const c = (window as unknown as { __hcsCanvas: { canvas: HTMLCanvasElement; view: { x: number; y: number; zoom: number }; setView(v: { x: number; y: number; zoom: number }): void } }).__hcsCanvas;
      const r = c.canvas.getBoundingClientRect();
      const v = c.view, m = 40 / v.zoom;
      const w = r.width / v.zoom, h = r.height / v.zoom;
      const xs = pts.map((p) => p[0]), ys = pts.map((p) => p[1]);
      const fits = Math.min(...xs) - m >= v.x && Math.max(...xs) + m <= v.x + w && Math.min(...ys) - m >= v.y && Math.max(...ys) + m <= v.y + h;
      if (!fits) c.setView({ x: Math.min(...xs) - m * 2, y: Math.min(...ys) - m * 2, zoom: v.zoom });
    }, points);
    await this.page.evaluate(() => new Promise((done) => requestAnimationFrame(done)));
  }

  private async point(p: P): Promise<{ x: number; y: number }> { return pagePoint(this.page, p); }

  private async press(p: P, o: { shift?: boolean } = {}): Promise<void> {
    await this.inSight([p]);
    const q = await this.point(p);
    if (o.shift) await this.page.keyboard.down('Shift');
    await this.page.mouse.move(q.x, q.y, { steps: 2 });
    await this.page.mouse.down();
    await this.page.mouse.up();
    if (o.shift) await this.page.keyboard.up('Shift');
  }

  // shift: held from the press (Shift+rectangle); shiftWhileMoving: held after the press, while dragging
  // (Logisim's MoveGesture: no wires follow) -- a Shift press on a part would take it out of the selection.
  private async dragPath(points: P[], o: { shift?: boolean; shiftWhileMoving?: boolean } = {}): Promise<void> {
    await this.inSight(points);
    const qs = [];
    for (const p of points) qs.push(await this.point(p));
    if (o.shift) await this.page.keyboard.down('Shift');
    await this.page.mouse.move(qs[0].x, qs[0].y, { steps: 2 });
    await this.page.mouse.down();
    if (o.shiftWhileMoving) await this.page.keyboard.down('Shift');
    for (const q of qs.slice(1)) await this.page.mouse.move(q.x, q.y, { steps: 8 });
    await this.page.mouse.up();
    if (o.shift || o.shiftWhileMoving) await this.page.keyboard.up('Shift');
  }

  private async useTool(name: 'Edit' | 'Wire'): Promise<void> {
    const t = tool(this.page, name);
    if ((await t.getAttribute('aria-checked')) !== 'true') await t.click();
    this.held = '';
    await this.canvasShown();
  }

  private async canvasShown(): Promise<void> {
    await this.page.locator('.canvas-view canvas').waitFor();
    if (this.pendingZoom !== null) await this.zoom(this.pendingZoom);
  }

  // ---- parts and wires the intents name (tests/parity/README.md) ----

  private async resolve(ref: string): Promise<{ id: string; grab: P }> {
    const ps = await parts(this.page);
    const ws = await wires(this.page);
    const grabOf = (k: { id: string; bounds: number[]; ports: { loc: P }[] }): P => {
      // Logisim's press selects every part under the point: only this one may be there, and no wire
      const others = ps.filter((c) => c.id !== k.id).map((c) => c.bounds);
      const clear = (x: number, y: number) => !others.some((b) => x >= b[0] - 1 && x <= b[0] + b[2] + 1 && y >= b[1] - 1 && y <= b[1] + b[3] + 1)
        && !ws.some((w) => x >= Math.min(w.a[0], w.b[0]) - 4 && x <= Math.max(w.a[0], w.b[0]) + 4 && y >= Math.min(w.a[1], w.b[1]) - 4 && y <= Math.max(w.a[1], w.b[1]) + 4);
      // the point nearest the middle that is clear of the ports (a port's point starts a wire; the corners of a
      // MUX's or a gate's box are outside its shape), or else the one farthest from them
      const mid: P = [k.bounds[0] + k.bounds[2] / 2, k.bounds[1] + k.bounds[3] / 2];
      let best: P = mid, far = -1, near = 1e9;
      for (let x = k.bounds[0] + 2; x <= k.bounds[0] + k.bounds[2] - 2; x += 1) {
        for (let y = k.bounds[1] + 2; y <= k.bounds[1] + k.bounds[3] - 2; y += 1) {
          const d = Math.min(...k.ports.map((q) => Math.hypot(q.loc[0] - x, q.loc[1] - y)), 1e9);
          const c = Math.hypot(x - mid[0], y - mid[1]);
          if (!clear(x, y)) continue;
          if (d >= 9 && c < near) { near = c; best = [x, y]; far = 1e9; }
          else if (near === 1e9 && d > far) { far = d; best = [x, y]; }
        }
      }
      return best;
    };
    if (ref.startsWith('wire:')) {
      const n = ref.slice(5).split(',').map(Number);
      const ws = await wires(this.page);
      const w = n.length === 4
        ? ws.find((x) => (x.a[0] === n[0] && x.a[1] === n[1] && x.b[0] === n[2] && x.b[1] === n[3]) || (x.b[0] === n[0] && x.b[1] === n[1] && x.a[0] === n[2] && x.a[1] === n[3]))
        : ws.find((x) => Math.min(x.a[0], x.b[0]) <= n[0] && n[0] <= Math.max(x.a[0], x.b[0]) && Math.min(x.a[1], x.b[1]) <= n[1] && n[1] <= Math.max(x.a[1], x.b[1]));
      if (!w) throw new Error(`no wire ${ref}`);
      const at: P = n.length === 4 ? [(n[0] + n[2]) / 2, (n[1] + n[3]) / 2] : [n[0], n[1]];
      return { id: w.id, grab: at };
    }
    let k;
    if (ref.startsWith('label:')) k = ps.filter((x) => x.attrs.label === ref.slice(6));
    else if (ref.startsWith('at:')) {
      const [pt, name] = ref.slice(3).split('/');
      const [x, y] = pt.split(',').map(Number);
      k = ps.filter((c) => c.loc[0] === x && c.loc[1] === y && (!name || c.name === name));
    } else {
      const id = this.symbols.get(ref)?.id;
      k = ps.filter((c) => c.id === id);
    }
    if (!k || k.length !== 1) throw new Error(`${ref}: ${k?.length ?? 0} parts`);
    return { id: k[0].id, grab: grabOf(k[0]) };
  }

  private async emptyPoint(): Promise<P> {
    const ps = await parts(this.page), ws = await wires(this.page);
    const v = await this.page.evaluate(() => ({ ...(window as unknown as { __hcsCanvas: { view: { x: number; y: number; zoom: number } } }).__hcsCanvas.view }));
    for (let y = Math.ceil(v.y / 10) * 10 + 30; ; y += 10) {
      for (let x = Math.ceil(v.x / 10) * 10 + 30; x < v.x + 500 / v.zoom; x += 10) {
        const nearPart = ps.some((c) => x >= c.bounds[0] - 15 && x <= c.bounds[0] + c.bounds[2] + 15 && y >= c.bounds[1] - 15 && y <= c.bounds[1] + c.bounds[3] + 15);
        const nearWire = ws.some((w) => x >= Math.min(w.a[0], w.b[0]) - 15 && x <= Math.max(w.a[0], w.b[0]) + 15 && y >= Math.min(w.a[1], w.b[1]) - 15 && y <= Math.max(w.a[1], w.b[1]) + 15);
        if (!nearPart && !nearWire) return [x, y];
      }
    }
  }

  // Selects what `ids` names as the Edit tool does: a click on the first, Shift+click on the rest.
  private async select(ids: string[], add = false): Promise<P | null> {
    await this.useTool('Edit');
    const before = await this.sent('edit.select');
    if (ids.length === 0) {
      await this.press(await this.emptyPoint());
      await this.expectSent('edit.select', before);
      await expect.poll(async () => (await selected(this.page)).length + ((await overlay(this.page)).ghost ? 1 : 0)).toBe(0);
      return null;
    }
    let first: P | null = null, n = before;
    const want: string[] = [];
    for (const [k, ref] of ids.entries()) {
      const { id, grab } = await this.resolve(ref);
      first ??= grab;
      want.push(id);
      await this.press(grab, { shift: add || k > 0 });
      await this.expectSent('edit.select', n);
      n = await this.sent('edit.select');
    }
    await expect.poll(async () => (await selected(this.page)).filter((x) => want.includes(x)).sort(),
      { message: `${ids} selected (${JSON.stringify(await parts(this.page))})` }).toEqual([...want].sort());
    return first;
  }

  // ---- a held part (the Components list) and its keys ----

  private async hold(lib: string, name: string): Promise<void> {
    const key = `${lib}/${name}`;
    if (this.held === key) return;
    const b = this.page.locator(`.comptree button.tool[data-tool="${key}"]`);
    const group = b.locator('xpath=ancestor::details[1]');
    if (!(await group.evaluate((d) => (d as HTMLDetailsElement).open))) await group.locator('summary').click();
    await b.scrollIntoViewIfNeeded();
    await b.click();
    this.held = key;
    await this.canvasShown();
    // the pointer over the Canvas: it takes the keys (I-02)
    const box = (await this.page.locator('.canvas-view canvas').boundingBox())!;
    await this.page.mouse.move(box.x + box.width - 60, box.y + box.height - 60, { steps: 2 });
  }

  private async digits(value: string, alt: boolean): Promise<void> {
    for (const ch of value) await this.page.keyboard.press(`${alt ? 'Alt+' : ''}Digit${ch}`);
  }

  private async toolAttr(i: Intent): Promise<void> {
    const lib = String(i.lib), name = String(i.name), attr = String(i.attr), value = String(i.value);
    await this.hold(lib, name);
    const key = attr === 'facing' ? 'arrow' : keyFor(name, attr);
    if (key === null || !/^[0-9]+$/.test(value) && key !== 'arrow') {
      await this.api(i, { lib, name, attr, value });
      return;
    }
    const before = await this.sent('edit.keyConfig');
    if (key === 'arrow') await this.page.keyboard.press(ARROW[value]);
    else await this.digits(value, key === 'alt');
    // the same value as the tool has already: Logisim does nothing, as the Swing table did
    await expect.poll(() => this.sent('edit.keyConfig')).toBeGreaterThan(before);
    await this.settle();
  }

  private async place(i: Intent): Promise<void> {
    const lib = String(i.lib), name = String(i.name), loc = i.loc as P;
    await this.hold(lib, name);
    const before = await this.sent('edit.addComponent');
    const had = new Set((await parts(this.page)).map((k) => k.id));
    await this.press(loc);
    await this.expectSent('edit.addComponent', before);
    this.held = '';   // Logisim's default: the Edit tool after a part is placed
    const added = (await parts(this.page)).filter((k) => !had.has(k.id));
    expect(added.map((k) => [k.name, k.loc]), `${name} at ${loc}`).toEqual([[name, loc]]);
    if (typeof i.as === 'string') this.symbols.set(i.as, { id: added[0].id, name, loc });
  }

  /* After an intent: a symbol whose part Logisim replaced (a move puts new components in) follows it to the part
     of its kind at its place -- moved by (dx, dy) if the intent moved it (tests/parity/README.md "기호"). */
  async follow(i: Intent, moved: Set<string>): Promise<void> {
    const ps = await parts(this.page);
    const dx = i.method === 'edit.move' ? Number(i.dx) : 0, dy = i.method === 'edit.move' ? Number(i.dy) : 0;
    for (const [sym, s] of this.symbols) {
      const now = ps.find((c) => c.id === s.id);
      if (now) { s.loc = now.loc; continue; }
      const loc: P = moved.has(s.id) ? [s.loc[0] + dx, s.loc[1] + dy] : s.loc;
      const k = ps.filter((c) => c.name === s.name && c.loc[0] === loc[0] && c.loc[1] === loc[1]);
      if (k.length === 1) this.symbols.set(sym, { id: k[0].id, name: s.name, loc });
    }
  }

  // The parts an intent acts on, before it runs (the selection when it names none).
  async targets(i: Intent): Promise<Set<string>> {
    if (Array.isArray(i.ids)) {
      const out = new Set<string>();
      for (const x of i.ids as string[]) { try { out.add((await this.resolve(x)).id); } catch { /* checked when run */ } }
      return out;
    }
    return new Set(await selected(this.page));
  }

  // ---- an intent ----

  async run(i: Intent): Promise<void> {
    const page = this.page;
    const ids = Array.isArray(i.ids) ? (i.ids as string[]) : null;
    switch (i.method) {
      case 'view.zoom': return this.zoom(Number(i.factor));
      case 'edit.setToolAttr': return this.toolAttr(i);
      case 'edit.addComponent': return this.place(i);
      case 'edit.addWire': {
        await this.useTool('Wire');
        const before = await this.sent('edit.addWire');
        await this.dragPath(i.points as P[]);
        return this.expectSent('edit.addWire', before);
      }
      case 'edit.select': {
        if (typeof i.filter === 'string') return this.api(i, { circuitId: await this.circuitId(i.circuit), filter: i.filter });
        if (Array.isArray(i.rect)) {
          await this.useTool('Edit');
          const [x0, y0, x1, y1] = i.rect as number[];
          const before = await this.sent('edit.select');
          await this.dragPath([[x0, y0], [x1, y1]], { shift: i.add === true });
          return this.expectSent('edit.select', before);
        }
        await this.select(ids ?? [], i.add === true);
        return;
      }
      case 'edit.move': {
        let grab: P | null = null;
        if (ids) grab = await this.select(ids);
        else {
          await this.useTool('Edit');
          const g = (await overlay(page)).ghost as { parts?: { bounds: number[] }[] } | undefined;
          const float = g?.parts?.[0];
          if (float) grab = [float.bounds[0] + float.bounds[2] / 2, float.bounds[1] + float.bounds[3] / 2];
          else {
            const s = await selected(page);
            const ps = await parts(page);
            const k = ps.find((x) => x.id === s[0]);
            if (!k) throw new Error('nothing selected to move');
            grab = (await this.resolve(`at:${k.loc[0]},${k.loc[1]}/${k.name}`)).grab;
          }
        }
        const dx = Number(i.dx), dy = Number(i.dy);
        const before = await this.sent('edit.move');
        await this.dragPath([grab!, [grab![0] + dx / 2, grab![1] + dy / 2], [grab![0] + dx, grab![1] + dy]], { shiftWhileMoving: i.connect === false });
        return this.expectSent('edit.move', before);
      }
      case 'edit.delete': case 'edit.copy': case 'edit.cut': case 'edit.duplicate': {
        if (ids) await this.select(ids);
        else await this.useTool('Edit');
        const key = { 'edit.delete': 'Delete', 'edit.copy': 'Control+c', 'edit.cut': 'Control+x', 'edit.duplicate': 'Control+d' }[i.method]!;
        const before = await this.sent(i.method);
        await page.locator('.canvas-view canvas').focus();
        await page.keyboard.press(key);
        return this.expectSent(i.method, before);
      }
      case 'edit.paste': case 'edit.undo': case 'edit.redo': {
        const key = { 'edit.paste': 'Control+v', 'edit.undo': 'Control+z', 'edit.redo': 'Control+y' }[i.method]!;
        const before = await this.sent(i.method);
        await page.locator('.canvas-view canvas').focus();
        await page.keyboard.press(key);
        if (i.method !== 'edit.paste') this.held = '';
        return this.expectSent(i.method, before);
      }
      case 'edit.setAttr': {
        const attr = String(i.attr), value = String(i.value);
        const targets = ids ? await Promise.all(ids.map((x) => this.resolve(x))) : [];
        const ps = await parts(page);
        const names = new Set(targets.map((t) => ps.find((k) => k.id === t.id)?.name ?? ''));
        const key = names.size === 1 ? keyFor([...names][0], attr) : null;
        if (attr === 'label' && targets.length === 1) {
          await this.select(ids!);
          await page.locator('.canvas-view canvas').focus();
          await page.keyboard.press('F2');
          const field = page.locator('.canvas-view .inline-field');
          await field.waitFor();
          const before = await this.sent('edit.setAttr');
          await field.fill(value);
          await field.press('Enter');
          return this.expectSent('edit.setAttr', before);
        }
        if (key && /^[0-9]+$/.test(value)) {
          await this.select(ids!);
          await page.locator('.canvas-view canvas').focus();
          const before = await this.sent('edit.keyConfig');
          await this.digits(value, key === 'alt');
          await expect.poll(() => this.sent('edit.keyConfig')).toBeGreaterThan(before);
          return this.settle();
        }
        return this.api(i, { circuitId: await this.circuitId(i.circuit), ids: targets.map((t) => t.id), attr, value });
      }
      case 'edit.setCircuitAttr':
        return this.api(i, { circuitId: await this.circuitId(i.target), attr: i.attr, value: i.value });
      case 'edit.duplicateN': case 'edit.align': case 'edit.distribute': {
        const targets = ids ? (await Promise.all(ids.map((x) => this.resolve(x)))).map((t) => t.id) : undefined;
        const { method: _m, ids: _i, circuit: _c, ...rest } = i;
        return this.api(i, { circuitId: await this.circuitId(i.circuit), ...(targets ? { ids: targets } : {}), ...rest });
      }
      default:
        throw new Error(`no screen way for ${i.method} in this test`);
    }
  }

  async save(file: string): Promise<string> {
    const before = await this.sent('file.save');
    await this.page.keyboard.press('Control+s');
    await expect.poll(() => this.sent('file.save')).toBeGreaterThan(before);
    await expect(this.page.locator('.status .ok')).toContainText(path.basename(file));
    return readFileSync(file, 'utf8');
  }
}

function intents(scene: string): Intent[] {
  return readFileSync(path.join(PARITY, `${scene}.intents`), 'utf8').split('\n').map((l) => l.trim())
    .filter((l) => l && !l.startsWith('#')).map((l) => JSON.parse(l) as Intent);
}

// The work folder as the Swing harness had it: inputs/, the bundled hcs-mips.jar, the scene's start file.
function prepare(dir: string, scene: string, script: Intent[]): string {
  const work = path.join(dir, 'work');
  mkdirSync(work, { recursive: true });
  cpSync(path.join(PARITY, 'inputs'), path.join(work, 'inputs'), { recursive: true });
  copyFileSync(MIPS, path.join(work, 'hcs-mips.jar'));
  const circ = path.join(work, `${scene}.circ`);
  copyFileSync(script[0]?.method === 'file.open' ? path.join(PARITY, String(script[0].path)) : TEMPLATE, circ);
  return circ;
}

/* The scenes at the screen: placing (01), wires (02), a move with the wires following (03), copy, cut, paste,
   duplicate (05), attributes (07), the undo and redo chain with copy and paste (10), the same edits at 200 %,
   50 % and 150 % (15). */
export const SCREEN_SCENES = ['01-place-parts', '02-wires', '03-move-following', '05-copy-paste-duplicate', '07-attributes', '10-undo-redo', '15-zoom'];

// What may go by the bridge until N-10 gives it a control: an attribute no key sets, a circuit's attribute, the
// right-click menu's Duplicate N, Align, Distribute, Only Components/Wires.
// (A tool's facing has the arrows; a label F2; inputs, select and bit widths the digits and Alt+digits: not here.)
const NO_CONTROL_YET = /^(edit\.setToolAttr (label|output|size|negate\d+|value|incoming)|edit\.setAttr (facing|size|output|labelloc|selloc|value)|edit\.setCircuitAttr \w+|edit\.duplicateN|edit\.align|edit\.distribute|edit\.select)$/;

// How many intents of each scene go by the bridge (D-159, tests/parity/README.md): one more fails.
const BRIDGED: Record<string, number> = {
  '01-place-parts': 10, '02-wires': 1, '03-move-following': 7, '05-copy-paste-duplicate': 4, '07-attributes': 10, '10-undo-redo': 2, '15-zoom': 0,
};

for (const scene of SCREEN_SCENES) {
  test(`the screen makes the golden: ${scene} (D-159)`, async () => {
    test.setTimeout(240_000);
    expect(existsSync(JAR), `${JAR}: ./gradlew :engine:stage`).toBe(true);
    const r = await launch({ width: 1920, height: 1200 }, { env: real });
    try {
      const script = intents(scene);
      const circ = prepare(r.dir, scene, script);
      const s = new Screen(r);
      await s.open(circ);
      await s.settle();
      let n = 0;
      for (const i of script) {
        n++;
        if (i.method === 'file.open') continue;
        try {
          const moved = i.method === 'edit.move' ? await s.targets(i) : new Set<string>();
          await s.run(i);
          await s.follow(i, moved);
        } catch (e) {
          throw new Error(`${scene} intent ${n} ${JSON.stringify(i)}: ${(e as Error).message}`);
        }
      }
      const saved = await s.save(circ);
      expect(normalizeCirc(saved), `${scene}: the window's save differs from the Swing app's golden`)
        .toBe(normalizeCirc(readFileSync(path.join(PARITY, `${scene}.circ`), 'utf8')));
      // only what has no control yet went by the bridge (listed in the report)
      test.info().annotations.push({ type: 'bridged (no control until N-10)', description: s.byApi.join(', ') || 'none' });
      if (process.env.PARITY_DEBUG) console.log(`BRIDGED ${scene}: ${s.byApi.length}/${script.length} ${s.byApi.join(', ')}`);
      expect(s.byApi.filter((x) => !NO_CONTROL_YET.test(x))).toEqual([]);
      expect(s.byApi.length, `${scene}: intents by the bridge (${s.byApi.join(', ')})`).toBe(BRIDGED[scene]);
      expect(s.byApi.filter((x) => x.startsWith('edit.select')).length, 'only Only Components/Wires is a bridged select').toBe(script.filter((i) => i.method === 'edit.select' && typeof i.filter === 'string').length);
    } finally {
      await r.close();
    }
  });
}
