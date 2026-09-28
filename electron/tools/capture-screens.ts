/* The fixed set of screenshots (docs/screens/README.md), every one of them
   taken here -- none by hand -- and all of them again every round, under
   the same names (derived from Hallym MIPS v2.3.0
   electron/tools/capture-screens.ts: the PNG writer, the checks and shot();
   the scenes are this app's):

     xvfb-run -a -s '-screen 0 2400x1400x24' npm run screens

   The files and steps are written below, so a round's shots can be laid
   over the last round's.  The whole window at 1920x1032 (a 1920x1080 lab
   PC, maximised over its taskbar) unless the name says otherwise; no mouse
   cursor, hover or tooltip in any of them (the pointer is moved out of the
   window and checked).  The engine is the fake one
   (tests/fake-engine/fake-engine.ts): its answers are fixed, so the same
   code gives the same pixels.  Each PNG is written without its ancillary
   chunks (metadata), losslessly, and must stay within 1.5 MB.

   SCREENS_OUT: write somewhere else. */

import { copyFileSync, existsSync, mkdirSync, readFileSync, utimesSync, writeFileSync } from 'node:fs';
import path from 'node:path';

import { answerOpen, canvasSettled, DATAPATH, launch, openFile, repo, root, sample, type Running } from '../tests/e2e/harness.ts';
import { click, menu, partMiddle, rightClick, wireAtPort } from '../tests/e2e/overlay-helpers.ts';

const out = process.env.SCREENS_OUT ? path.resolve(process.env.SCREENS_OUT) : path.join(root, 'docs/screens');
mkdirSync(out, { recursive: true });
const MAX_BYTES = 1536 * 1024;
const FHD = { width: 1920, height: 1032 };

// PNG without its ancillary chunks: the signature, then IHDR, PLTE, tRNS,
// IDAT and IEND only.  The pixels are untouched.
export function stripPng(file: string): number {
  const b = readFileSync(file);
  const keep = new Set(['IHDR', 'PLTE', 'tRNS', 'IDAT', 'IEND']);
  const parts = [b.subarray(0, 8)];
  for (let at = 8; at < b.length;) {
    const end = at + 12 + b.readUInt32BE(at);
    if (keep.has(b.toString('latin1', at + 4, at + 8))) parts.push(b.subarray(at, end));
    at = end;
  }
  const png = Buffer.concat(parts);
  writeFileSync(file, png);
  return png.length;
}

function written(name: string): void {
  const file = path.join(out, `${name}.png`);
  const bytes = stripPng(file);
  console.log(`wrote ${path.relative(root, file)} (${Math.round(bytes / 1024)} KB)`);
  if (bytes > MAX_BYTES) throw new Error(`${name}.png is ${bytes} bytes, over ${MAX_BYTES}: crop it`);
}

// keepFocus: the scene is about a box that has the keys (the search palette closes when it loses them);
// its caret is hidden so the pixels do not depend on when it blinks.
async function shot(r: Running, name: string, o: { keepFocus?: boolean; keepPointer?: boolean } = {}): Promise<void> {
  const { page } = r;
  // out of the window: no hover, no tooltip -- but a gesture going on (a drag, a part held) keeps the pointer where it is
  if (!o.keepPointer) await page.mouse.move(-10, -10);
  if (o.keepFocus) await page.addStyleTag({ content: '* { caret-color: transparent !important; }' });
  else await page.evaluate(() => (document.activeElement as HTMLElement | null)?.blur());
  await page.evaluate(() => document.fonts.ready);
  await page.waitForFunction(() => [...document.images].every((i) => i.complete));
  await page.waitForTimeout(400);
  const hovered = await page.evaluate(() => document.querySelectorAll(':hover').length);
  if (hovered && !o.keepPointer) throw new Error(`${name}: ${hovered} elements still hovered`);
  await page.screenshot({ path: path.join(out, `${name}.png`) });
  written(name);
}

// The Canvas has drawn the circuit with the engine's values (N-05).
async function drawn(r: Running): Promise<void> {
  await r.page.locator('.canvas-view canvas').waitFor();
  await r.page.waitForFunction(() => {
    const c = (window as unknown as { __hcsCanvas?: { scene: { values: Map<string, string> } | null } }).__hcsCanvas;
    return !!c?.scene && c.scene.values.size > 0;
  });
  await r.page.evaluate(() => document.fonts.ready);
  await canvasSettled(r.page);
}

const kill = (r: Running) => r.app.evaluate(() => (globalThis as unknown as { __hcs: { engine: { kill(): void } } }).__hcs.engine.kill());

{
  const r = await launch(FHD);
  const { page } = r;
  await shot(r, 'start');
  await page.getByRole('button', { name: /튜토리얼 보기/ }).click();
  await shot(r, 'start-tutorial');
  await page.getByRole('button', { name: '← 처음으로' }).click();
  await page.getByRole('button', { name: /바로 시작/ }).click();
  await shot(r, 'start-2');
  await page.getByRole('button', { name: /새 회로/ }).click();
  await page.locator('.canvas h3').waitFor();
  await shot(r, 'new-circuit');
  await openFile(r, sample(r.dir, DATAPATH));
  await drawn(r);
  await shot(r, 'open-file');
  await page.getByRole('tab', { name: 'Circuits' }).click();
  await page.locator('.upper .pbody:visible .list > li', { hasText: 'regfile' }).getByRole('button').click();
  await page.getByRole('button', { name: /1 Cycle/ }).click();
  await page.getByRole('button', { name: /1 Cycle/ }).click();
  await page.locator('.status', { hasText: 'Cycle 2' }).waitFor();
  await shot(r, 'circuit-tabs');
  await page.getByTitle('About').click();
  await page.locator('dialog.about[open]').waitFor();
  await shot(r, 'about');
  await page.locator('dialog.about').getByRole('tab', { name: 'Licenses' }).click();
  await page.locator('dialog.about details').nth(2).locator('summary').click();
  await page.locator('dialog.about details').nth(2).locator('pre', { hasText: 'BSD' }).waitFor();
  await shot(r, 'about-licenses');
  await page.keyboard.press('Escape');
  await answerOpen(r.app, path.join(r.dir, 'lab3.circ'));
  await page.keyboard.press('Control+o');
  await page.locator('dialog.ask').waitFor();
  await shot(r, 'dialog-error');
  await page.keyboard.press('Escape');
  await kill(r);
  await page.locator('.band', { hasText: '다시 시작했습니다' }).waitFor();
  await shot(r, 'engine-restarted');
  await r.close();
}

// Messages (N-13): a broken circuit after one cycle, a message chosen, then the one with a cycle (the Cycle View
// comes forward, N-14); a circuit with nothing to say.
{
  const r = await launch(FHD);
  const { page } = r;
  await openFile(r, sample(r.dir, 'electron/tests/fixtures/broken-datapath.circ'));
  await page.locator('.msg').nth(1).waitFor();
  await page.keyboard.press('F10');
  await page.locator('.msg').nth(2).waitFor();
  await page.locator('.msg').nth(1).click();
  await page.locator('.msg.on').waitFor();
  await shot(r, 'messages-list');
  // the message found at cycle 0 chosen: the Cycle View at that cycle, the message's place pinned on top (N-14, V-03)
  // chosen from the keyboard: a mouse click would leave the (then hidden) Messages body hovered in Chromium
  await page.locator('.msggroup[data-code="X_WRITE_CONTROL"] .msg').focus();
  await page.keyboard.press('Enter');
  await page.locator('.ctable tr.crow.temp td.pin').waitFor();
  await shot(r, 'cycle-pinned');
  await page.locator('section.bottom').getByRole('tab', { name: 'Messages' }).click();
  await openFile(r, sample(r.dir, DATAPATH));
  await page.locator('.status .msgcount', { hasText: 'No messages' }).waitFor();
  await page.locator('.pbody.bottom .notice h3', { hasText: '메시지가 없습니다' }).waitFor();
  await shot(r, 'messages-empty');
  await r.close();
}

// The Canvas (N-05, D-137): demo-datapath at 100, 150 and 400 % with the engine's values after a cycle, the
// legend, the inside of a subcircuit instance, ref-mips at a lab PC's size (fitted, and at 100 % over the
// Data Memory and the Console).
async function view(r: Running, v: { x: number; y: number; zoom: number }): Promise<void> {
  await r.page.evaluate((w) => (window as unknown as { __hcsCanvas: { setView(v: object): void } }).__hcsCanvas.setView(w), v);
  await canvasSettled(r.page);
}
{
  const r = await launch(FHD);
  const { page } = r;
  await openFile(r, sample(r.dir, DATAPATH));
  await drawn(r);
  await page.getByRole('button', { name: /1 Cycle/ }).click();
  await page.locator('.status', { hasText: 'Cycle 1' }).waitFor();
  await view(r, { x: 70, y: 40, zoom: 1 });
  await shot(r, 'canvas-100');
  await view(r, { x: 70, y: 50, zoom: 1.5 });
  await shot(r, 'canvas-150');
  await view(r, { x: 180, y: 60, zoom: 4 });
  await shot(r, 'canvas-400');
  await page.keyboard.press('Control+0');
  await canvasSettled(page);
  await page.getByRole('button', { name: /Wire Colors/ }).click();
  await page.locator('.legend-panel').waitFor();
  await shot(r, 'canvas-legend');
  await page.keyboard.press('Escape');
  await canvasSettled(page);
  const rf = await page.evaluate(() => {
    const c = (window as unknown as { __hcsCanvas: { canvas: HTMLCanvasElement; view: { x: number; y: number; zoom: number }; scene: { components: Map<string, { name: string; bounds: number[] }> } } }).__hcsCanvas;
    const k = [...c.scene.components.values()].find((x) => x.name === 'regfile')!;
    const rr = c.canvas.getBoundingClientRect();
    return { x: rr.left + (k.bounds[0] + k.bounds[2] / 2 - c.view.x) * c.view.zoom, y: rr.top + (k.bounds[1] + k.bounds[3] / 2 - c.view.y) * c.view.zoom };
  });
  await page.getByRole('radio', { name: 'Poke', exact: true }).click();   // the lens goes inside (I-75, I-114)
  await page.mouse.dblclick(rf.x, rf.y);
  await page.locator('.canvas-crumbs .here', { hasText: 'regfile' }).waitFor();
  await page.getByRole('radio', { name: 'Edit', exact: true }).click();
  await drawn(r);
  await shot(r, 'canvas-inside');
  await r.close();
}
// The simulation (N-07, D-145), with the real engine (engine/build/stage: ./gradlew :engine:stage; its facts
// are the circuit's own -- the fake engine's recording runs a program of its own, whose PC the status bar would
// show beside demo-datapath's): demo-datapath with the Poke tool in hand -- the lens on the subcircuits, a poked
// wire's value box -- and the clock running at 64 Hz (Run is Stop, the status bar's facts); then the N Cycles
// dialog; then the simulation turned off with Ctrl+E (the band, Simulation Off).
{
  const jar = path.join(repo, 'engine/build/stage/hcs-engine.jar');
  if (!existsSync(jar)) throw new Error(`${jar}: ./gradlew :engine:stage (the simulation's screens use the real engine)`);
  const r = await launch(FHD, { env: { HCS_ENGINE_CMD: '', HCS_ENGINE_JAR: jar } });
  const { page } = r;
  await openFile(r, sample(r.dir, DATAPATH));
  await drawn(r);
  await page.getByRole('button', { name: /1 Cycle/ }).click();
  await page.locator('.status', { hasText: 'Cycle 1' }).waitFor();
  await view(r, { x: 70, y: 40, zoom: 1 });
  await page.getByRole('radio', { name: 'Poke' }).click();
  const wire = await page.evaluate(() => {
    type W = { a: number[]; b: number[] };
    const c = (window as unknown as { __hcsCanvas: { canvas: HTMLCanvasElement; view: { x: number; y: number; zoom: number }; scene: { wires: Map<string, W>; wireValue(id: string): string | undefined } } }).__hcsCanvas;
    // a long level 32-bit wire in the upper left: the value box has room beside it
    const w = [...c.scene.wires.entries()].map(([id, x]) => ({ id, ...x }))
      .filter((x) => x.a[1] === x.b[1] && Math.abs(x.a[0] - x.b[0]) >= 60 && (c.scene.wireValue(x.id)?.length ?? 0) === 32)
      .sort((p, q) => p.a[1] - q.a[1] || p.a[0] - q.a[0])[0];
    const rr = c.canvas.getBoundingClientRect();
    const mx = (w.a[0] + w.b[0]) / 2, my = w.a[1];
    return { x: rr.left + (mx - c.view.x) * c.view.zoom, y: rr.top + (my - c.view.y) * c.view.zoom };
  });
  await page.mouse.click(wire.x, wire.y);
  await page.getByRole('combobox', { name: 'Clock speed' }).selectOption('64');
  await page.keyboard.press('F5');
  await page.locator('.status .run', { hasText: 'Running (64 Hz)' }).waitFor();
  // shot when the status bar's PC and the PC register on the Canvas show the same cycle (the clock runs on meanwhile)
  await page.waitForFunction(() => {
    const c = (window as unknown as { __hcsCanvas: { scene: { components: Map<string, { id: string; name: string; attrs: Record<string, string> }>; portValue(id: string, i: number): string | undefined } } }).__hcsCanvas;
    const pc = [...c.scene.components.values()].find((k) => k.name === 'Register' && k.attrs.label === 'PC');
    const v = pc ? c.scene.portValue(pc.id, 0) : undefined;
    const m = /PC 0x([0-9a-f]{8})/.exec(document.querySelector('.status')?.textContent ?? '');
    return !!v && /^[01]{32}$/.test(v) && !!m && parseInt(v, 2) === parseInt(m[1], 16);
  }, undefined, { polling: 5, timeout: 20_000 });
  await shot(r, 'sim-running');
  await page.keyboard.press('F5');
  await page.getByRole('button', { name: /Reset/ }).click();
  await page.locator('.status', { hasText: 'Cycle 0' }).waitFor();
  await page.getByRole('button', { name: /N Cycles/ }).click();
  await page.locator('dialog.cycles').waitFor();
  await shot(r, 'n-cycles');
  await page.keyboard.press('Escape');
  await page.getByRole('radio', { name: 'Edit' }).click();
  await page.keyboard.press('Control+e');
  await page.locator('.simband').waitFor();
  await page.locator('.status', { hasText: 'Simulation Off' }).waitFor();
  await shot(r, 'sim-off');
  await r.close();
}
// Editing (N-08, D-146), with the real engine (its gestures' answers are Logisim's): two parts selected and dragged
// (their picture where they go, the wires the engine says would follow); a wire drawn from an open port (the L
// bending where the first move went); a part held from the Components list (its ghost on the grid under the pointer).
{
  const jar = path.join(repo, 'engine/build/stage/hcs-engine.jar');
  const r = await launch(FHD, { env: { HCS_ENGINE_CMD: '', HCS_ENGINE_JAR: jar } });
  const { page } = r;
  await openFile(r, sample(r.dir, DATAPATH));
  await drawn(r);
  await view(r, { x: 60, y: 40, zoom: 1.5 });
  const onPage = (p: [number, number]) => page.evaluate((q) => {
    const c = (window as unknown as { __hcsCanvas: { canvas: HTMLCanvasElement; view: { x: number; y: number; zoom: number } } }).__hcsCanvas;
    const rr = c.canvas.getBoundingClientRect();
    return { x: rr.left + (q[0] - c.view.x) * c.view.zoom, y: rr.top + (q[1] - c.view.y) * c.view.zoom };
  }, p);
  const middleOf = (name: string, label?: string) => page.evaluate(([n, l]) => {
    const c = (window as unknown as { __hcsCanvas: { scene: { components: Map<string, { name: string; attrs: Record<string, string>; bounds: number[] }> } } }).__hcsCanvas;
    const k = [...c.scene.components.values()].find((x) => x.name === n && (!l || x.attrs.label === l))!;
    return [k.bounds[0] + k.bounds[2] / 2, k.bounds[1] + k.bounds[3] / 2] as [number, number];
  }, [name, label ?? ''] as const);
  // two parts: the PC register, then Shift+click the adder; a drag of both, held
  const pc = await onPage(await middleOf('Register', 'PC'));
  const adder = await onPage(await middleOf('Adder'));
  await page.mouse.click(pc.x, pc.y);
  await page.keyboard.down('Shift');
  await page.mouse.click(adder.x, adder.y);
  await page.keyboard.up('Shift');
  await page.waitForFunction(() => (window as unknown as { __hcsCanvas: { selection(): string[] } }).__hcsCanvas.selection().length === 2);
  await page.mouse.move(pc.x, pc.y);
  await page.mouse.down();
  await page.mouse.move(pc.x + 60, pc.y + 45, { steps: 8 });
  await page.waitForFunction(() => !!(window as unknown as { __hcsCanvas: { overlay: { moveWires?: unknown } } }).__hcsCanvas.overlay.moveWires);
  await shot(r, 'edit-select', { keepPointer: true });
  await page.keyboard.down('Escape');
  await page.mouse.move(pc.x, pc.y, { steps: 4 });   // back where it started: no move
  await page.mouse.up();
  await page.keyboard.up('Escape');
  // a wire from the adder's open carry-out port: first down, then right (vertical first)
  const cout = await page.evaluate(() => {
    const c = (window as unknown as { __hcsCanvas: { scene: { components: Map<string, { name: string; ports: { loc: number[]; name?: string }[] }> } } }).__hcsCanvas;
    const k = [...c.scene.components.values()].find((x) => x.name === 'Adder')!;
    return k.ports.find((q) => q.name === 'cout')!.loc as [number, number];
  });
  const a = await onPage(cout);
  await page.mouse.click(a.x + 300, a.y + 300);   // nothing selected
  await page.mouse.move(a.x, a.y);
  await page.mouse.down();
  const b1 = await onPage([cout[0], cout[1] + 20]);
  const b2 = await onPage([cout[0] + 70, cout[1] + 40]);
  await page.mouse.move(b1.x, b1.y, { steps: 4 });
  await page.mouse.move(b2.x, b2.y, { steps: 8 });
  await shot(r, 'edit-wire', { keepPointer: true });
  await page.keyboard.press('Escape');
  await page.mouse.move(a.x, a.y, { steps: 6 });   // back to the start: no wire
  await page.mouse.up();
  // an AND gate with three inputs held from the Components list, over an empty place
  const w = await page.evaluate(() => {
    const c = (window as unknown as { __hcsCanvas: { scene: { fileId: string; circuitId: string } } }).__hcsCanvas;
    return { fileId: c.scene.fileId, circuitId: c.scene.circuitId };
  });
  await page.evaluate((d) => window.dispatchEvent(new CustomEvent('hcs:place-tool', { detail: d, cancelable: true })),
    { ...w, lib: 'Gates', name: 'AND Gate', attrs: { inputs: '3' }, source: 'components' });
  const spot = await onPage([400, 360]);
  await page.mouse.move(spot.x, spot.y, { steps: 4 });
  await page.waitForFunction(() => !!(window as unknown as { __hcsCanvas: { overlay: { ghost?: unknown } } }).__hcsCanvas.overlay.ghost);
  await shot(r, 'edit-ghost', { keepPointer: true });
  await r.close();
}
{
  const r = await launch(FHD);
  await openFile(r, sample(r.dir, 'tests/mips/ref-mips.circ'));
  await drawn(r);
  await shot(r, 'ref-mips-fhd');
  await view(r, { x: 3700, y: 7420, zoom: 0.8 });
  await shot(r, 'ref-mips-memory');
  await r.close();
}

// The program (N-16): the summary after Load Program (ref-mips, data.hmx beside its .s), the Console after
// the program ran to its exit, the band after the .hmx was exported again cut off.  The fake engine answers
// with the real engine's words (tests/fixtures/programs.json); its clock is fixed and the zone is Seoul's.
{
  const r = await launch(FHD, { env: { TZ: 'Asia/Seoul' } });
  const { page } = r;
  const circ = sample(r.dir, 'tests/mips/ref-mips.circ');
  sample(r.dir, 'tests/hmx/hallym-mips-v2.4.0/data.s');
  const hmx = sample(r.dir, 'tests/hmx/hallym-mips-v2.4.0/data.hmx');
  await openFile(r, circ);
  await drawn(r);
  await answerOpen(r.app, hmx);
  await page.getByRole('button', { name: /Load Program/ }).click();
  await page.locator('dialog.loadsummary').waitFor();
  await shot(r, 'load-summary');
  await page.locator('dialog.loadsummary').getByRole('button', { name: 'OK' }).click();
  await page.getByRole('tab', { name: 'Console' }).click();
  await page.keyboard.press('F10');
  await page.locator('pre.consoletext', { hasText: 'sum = 14' }).waitFor();
  await shot(r, 'console');
  writeFileSync(hmx, readFileSync(path.join(repo, 'tests/hmx/truncated.hmx')));
  await page.locator('.progband:not([hidden])').waitFor();
  await shot(r, 'reload-kept');
  await r.close();
}

// The Cycle View (N-14): the fake engine runs the recursive factorial, one instruction a cycle, in a file with an
// Instruction Memory (tests/fake-engine/fake-record.ts).  The bottom panel dragged taller; rows added with the
// engine call the Canvas's right-click (Add to Cycle View, N-05/N-10) will make.
{
  const r = await launch(FHD);
  const { page } = r;
  await openFile(r, sample(r.dir, DATAPATH));
  await drawn(r);
  const grip = (await page.locator('[role="separator"][aria-label="Messages"]').boundingBox())!;
  await page.mouse.move(grip.x + grip.width / 2, grip.y + grip.height / 2);
  await page.mouse.down();
  await page.mouse.move(grip.x + grip.width / 2, grip.y - 250, { steps: 5 });
  await page.mouse.up();
  await page.locator('section.bottom').getByRole('tab', { name: 'Cycle View' }).click();
  await page.evaluate(async () => {
    const app = (window as unknown as { app: { call(m: string, p: unknown): Promise<unknown> } }).app;
    for (const at of [[300, 200], [1240, 500], [100, 420], [1300, 500]]) await app.call('record.addRow', { fileId: 'f1', circuitId: 'c1', at });
  });
  for (let i = 0; i < 9; i += 1) await page.getByRole('button', { name: /1 Cycle/ }).click();
  await page.locator('.cbar .cpos', { hasText: 'Cycle 9' }).waitFor();
  await page.locator('.cside .rrow.chg').waitFor();
  await shot(r, 'registers');
  await page.locator('.ctable tr.hcycle th[data-cycle="7"]').click();
  await page.locator('.cbar .cpos', { hasText: 'Cycle 7 / 9' }).waitFor();
  await shot(r, 'cycle-view');
  await page.getByRole('button', { name: 'Latest Cycle' }).click();
  await page.locator('.cside').getByRole('tab', { name: 'Memory' }).click();
  await page.locator('.cside .data .dsec-stack').first().waitFor();
  await shot(r, 'memory');
  await page.locator('.cside').getByRole('tab', { name: 'Instruction' }).click();
  await page.locator('.cside .insp .bitgrid').waitFor();
  await shot(r, 'instruction');
  await page.getByRole('button', { name: 'Run Until…' }).click();
  await page.locator('dialog.ask').getByLabel('Value').fill('fact');
  await shot(r, 'run-until');
  await r.close();
}

// The Canvas's overlays (N-15, D-151), on demo-datapath after a cycle, fitted (Ctrl+0): the Signal Flow from the PC held
// at a fixed time (every part of its way lit), the influence of the register file both ways, the active path and
// the instruction's fields with the Cycle View on show, the bus values at 125 %, signal groups set from a wire's
// right click with Colors: Groups (the Wire Colors panel open), and area memos around the stages (the engine
// call Add Area Memo… makes with the parts chosen).
{
  const r = await launch(FHD);
  const { page } = r;
  await openFile(r, sample(r.dir, DATAPATH));
  await drawn(r);
  await page.getByRole('button', { name: /1 Cycle/ }).click();
  await page.locator('.status', { hasText: 'Cycle 1' }).waitFor();
  const fit = async () => { await page.keyboard.press('Control+0'); await page.waitForTimeout(300); };
  await fit();
  type Ov = { __hcsOverlays: { flow: { held: number | null; state(): { total: number } | null }; shown(): { flow: { running: boolean }; influence: unknown; activePath: number } } };
  const pc = await partMiddle(page, 'Register', 'PC');
  await click(page, pc.at);
  await page.waitForFunction(() => (window as unknown as Ov).__hcsOverlays.shown().flow.running);
  await page.evaluate(() => { const f = (window as unknown as Ov).__hcsOverlays.flow; f.held = f.state()!.total + 10; });
  await shot(r, 'signal-flow');
  await page.keyboard.press('Escape');
  await page.evaluate(() => { (window as unknown as Ov).__hcsOverlays.flow.held = null; });
  await page.getByRole('button', { name: 'Signal Flow', exact: true }).click();   // off: a click only chooses
  const rf = await partMiddle(page, 'regfile');
  await click(page, rf.at);
  await rightClick(page, rf.at);
  await menu(page, 'Influence', 'Show Influence (Both)');
  await page.waitForFunction(() => (window as unknown as Ov).__hcsOverlays.shown().influence !== null);
  await shot(r, 'influence');
  await page.keyboard.press('Escape');
  await page.waitForFunction(() => (window as unknown as Ov).__hcsOverlays.shown().influence === null);
  await click(page, [640, 620]);   // an empty place: nothing chosen
  await page.getByRole('button', { name: 'Signal Flow', exact: true }).click();
  await page.locator('section.bottom').getByRole('tab', { name: 'Cycle View' }).click();
  await page.waitForFunction(() => (window as unknown as Ov).__hcsOverlays.shown().activePath > 0);
  await shot(r, 'active-path');
  await page.locator('section.bottom').getByRole('tab', { name: 'Messages' }).click();
  await view(r, { x: 640, y: 160, zoom: 1.25 });
  await shot(r, 'bus-values');
  await fit();
  for (const [part, port, group] of [['alu', 'Result', 'Data'], ['Register', 'Q', 'Address'], ['Data Memory', 'ReadData', 'Data'], ['Register', 'clk', 'Control'], ['regfile', 'RegWrite', 'Control']] as const) {
    const w = await wireAtPort(page, part, port);
    await rightClick(page, w.at);
    await menu(page, 'Signal Group', group);
    await page.waitForFunction((net) => (window as unknown as { __hcsCanvas: { scene: { groups: Map<string, unknown> } } }).__hcsCanvas.scene.groups.has(net), w.net);
  }
  await page.getByRole('button', { name: /Wire Colors/ }).click();
  await page.locator('.legend-panel').getByRole('button', { name: 'Groups' }).click();
  await shot(r, 'signal-groups');
  await page.locator('.legend-panel').getByRole('button', { name: 'Values' }).click();
  await page.keyboard.press('Escape');
  await page.evaluate(async () => {
    const app = (window as unknown as { app: { call(m: string, p: unknown): Promise<unknown> } }).app;
    const ids = (...names: string[]) => {
      const c = (window as unknown as { __hcsCanvas: { scene: { components: Map<string, { id: string; name: string; attrs: Record<string, string> }> } } }).__hcsCanvas;
      return names.map((n) => [...c.scene.components.values()].find((k) => k.name === n || k.attrs.label === n)!.id);
    };
    for (const [text, color, parts] of [['IF', 1, ids('PC', 'Adder', 'Instruction Memory')], ['ID', 3, ids('regfile')], ['EX', 5, ids('alu', 'Zero')], ['MEM', 8, ids('Data Memory')], ['WB', 10, ids('Multiplexer')]] as const) {
      await app.call('edit.areaMemo', { fileId: 'f1', circuitId: 'c1', at: [-500, -500], ids: parts, text, color });
    }
  });
  await page.waitForFunction(() => (window as unknown as { __hcsCanvas: { scene: { memos: unknown[] } } }).__hcsCanvas.scene.memos.length === 5);
  await shot(r, 'area-memo');
  await r.close();
}

// Registers with ten-digit and negative values (the fake's wide-registers: $s6 = -1, $s7 = 0x80000000; $sp, $fp, $ra
// as a program leaves them): the Saved, Pointers and Return address bands whole in Hex, Dec and Bin.
{
  const r = await launch(FHD, { env: { FAKE_ENGINE_MODE: 'wide-registers' } });
  const { page } = r;
  await openFile(r, sample(r.dir, DATAPATH));
  await drawn(r);
  const grip = (await page.locator('[role="separator"][aria-label="Messages"]').boundingBox())!;
  await page.mouse.move(grip.x + grip.width / 2, grip.y + grip.height / 2);
  await page.mouse.down();
  await page.mouse.move(grip.x + grip.width / 2, grip.y - 250, { steps: 5 });
  await page.mouse.up();
  await page.locator('section.bottom').getByRole('tab', { name: 'Cycle View' }).click();
  for (let i = 0; i < 12; i += 1) await page.getByRole('button', { name: /1 Cycle/ }).click();
  await page.locator('.cbar .cpos', { hasText: 'Cycle 12' }).waitFor();
  await page.locator('.cside .rrow[data-reg="$s6"] .dec', { hasText: '-1' }).waitFor();
  await page.locator('.cside .rrow[data-reg="$s4"]').evaluate((e) => e.scrollIntoView({ block: 'start' }));
  await shot(r, 'registers-pointers');
  await r.close();
}

// Finding and placing (N-12, D-150): the Components search, the search palette by the pointer, Find
// with a group opened and a place gone to, Tunnels with a lone name and Tunnel Color, the Minimap (the
// Canvas at 200 % over the register file), the Splitter editor of demo-datapath's instruction splitter.
const canvasPoint = (r: Running, p: [number, number]) => r.page.evaluate((q) => {
  const c = (window as unknown as { __hcsCanvas: { canvas: HTMLCanvasElement; view: { x: number; y: number; zoom: number } } }).__hcsCanvas;
  const rr = c.canvas.getBoundingClientRect();
  return { x: rr.left + (q[0] - c.view.x) * c.view.zoom, y: rr.top + (q[1] - c.view.y) * c.view.zoom };
}, p);
{
  const r = await launch(FHD);
  const { page } = r;
  await openFile(r, sample(r.dir, DATAPATH));
  await drawn(r);
  const search = page.getByRole('searchbox', { name: 'Search parts' });
  await search.fill('reg 32');
  await page.locator('.compresults li', { hasText: 'Register' }).first().waitFor();
  await shot(r, 'components-search');
  await search.fill('');
  const at = await canvasPoint(r, [300, 650]);
  await page.mouse.move(at.x, at.y);
  await page.keyboard.press('Control+k');
  await page.keyboard.type('and 3');
  await page.locator('.palette .palrow', { hasText: 'AND Gate' }).first().waitFor();
  await shot(r, 'palette', { keepFocus: true });
  await page.keyboard.press('Escape');
  await page.keyboard.press('Control+f');
  await page.keyboard.type('clk');
  const rows = page.locator('.findwin .findrow');
  await rows.nth(2).waitFor();
  await rows.first().click();
  await rows.nth(2).click();
  await page.waitForFunction(() => (window as unknown as { __hcsCanvas: { markedIds(): unknown } }).__hcsCanvas.markedIds() !== null);
  await page.waitForTimeout(300);
  await shot(r, 'find');
  await page.locator('.findwin .findclose').click();
  // the Splitter editor: the splitter selected, then Edit Splitter… from the palette
  const sp = await page.evaluate(() => {
    const c = (window as unknown as { __hcsCanvas: { scene: { components: Map<string, { name: string; bounds: number[] }> } } }).__hcsCanvas;
    const k = [...c.scene.components.values()].find((x) => x.name === 'Splitter')!;
    return [k.bounds[0] + k.bounds[2] / 2, k.bounds[1] + 8] as [number, number];
  });
  const spAt = await canvasPoint(r, sp);
  await page.mouse.click(spAt.x, spAt.y);
  await page.keyboard.press('Control+k');
  await page.keyboard.type('edit splitter');
  await page.keyboard.press('Enter');
  await page.getByRole('dialog', { name: 'Edit Splitter' }).waitFor();
  await shot(r, 'splitter-editor');
  await page.keyboard.press('Escape');
  await r.close();
}
{
  const r = await launch(FHD);
  const { page } = r;
  await openFile(r, sample(r.dir, 'electron/tests/fixtures/broken-datapath.circ'));
  await drawn(r);
  const memread = page.locator('.lower .list.tunnels li', { hasText: 'MemRead' });
  await memread.locator('.tswatch').click();
  await page.getByRole('menu', { name: 'Tunnel Color' }).getByRole('menuitemradio', { name: 'Vermillion' }).click();
  await memread.locator('.tswatch.chosen').waitFor();
  await page.locator('.lower .list.tunnels li', { hasText: 'clk' }).locator('.tswatch').click();
  await page.getByRole('menu', { name: 'Tunnel Color' }).waitFor();
  await shot(r, 'tunnels');
  await r.close();
}
{
  const r = await launch(FHD);
  const { page } = r;
  await openFile(r, sample(r.dir, DATAPATH));
  await drawn(r);
  await view(r, { x: 820, y: 230, zoom: 2 });
  await page.getByRole('tab', { name: 'Minimap' }).click();
  await page.waitForFunction(() => {
    const c = document.querySelector<HTMLCanvasElement>('canvas.minimap');
    return !!c && c.width > 10;
  });
  await shot(r, 'minimap');
  await r.close();
}

// The lab PCs at 125 % and 150 % (1536x816 and 1280x672 CSS px), and half a screen.
for (const [name, size, scale] of [
  ['lab-125', { width: 1536, height: 816 }, '1.25'],
  ['lab-150', { width: 1280, height: 672 }, '1.5'],
  ['narrow', { width: 960, height: 1032 }, '1'],
] as const) {
  const r = await launch(size, { switches: [`--force-device-scale-factor=${scale}`] });
  await openFile(r, sample(r.dir, DATAPATH));
  await drawn(r);
  if (name === 'narrow') await r.page.locator('.upper').getByRole('tab', { name: 'Attributes' }).click();
  await shot(r, name);
  await r.close();
}

// A recovery file beside the file being opened (N-19, D-152): the question before it opens, over the file on
// show.  The recovery file's time is fixed (2026-09-28 14:05, Seoul) so the same code gives the same pixels.
{
  const r = await launch(FHD, { env: { TZ: 'Asia/Seoul' } });
  const { page } = r;
  await openFile(r, sample(r.dir, DATAPATH));
  await drawn(r);
  const lab3 = sample(r.dir, 'tests/circ/gates.circ', 'lab3.circ');
  copyFileSync(lab3, `${lab3}.hcs-recover`);
  const at = Date.parse('2026-09-28T14:05:31+09:00') / 1000;
  utimesSync(lab3, at - 3600, at - 3600);
  utimesSync(`${lab3}.hcs-recover`, at, at);
  await answerOpen(r.app, lab3);
  await page.keyboard.press('Control+o');
  await page.locator('dialog.ask', { hasText: '저장하지 않은 편집이 있습니다' }).waitFor();
  await shot(r, 'recover-dialog');
  await r.close();
}

// Leaving with unsaved changes (N-19): the close button asks for each file first -- Save / Discard / Cancel, plain.
{
  const r = await launch(FHD);
  const { page } = r;
  await openFile(r, sample(r.dir, DATAPATH));
  await drawn(r);
  await page.evaluate(async () => {
    const app = (window as unknown as { app: { call(m: string, p: unknown): Promise<unknown> } }).app;
    await app.call('edit.addComponent', { fileId: 'f1', circuitId: 'c1', lib: 'Gates', name: 'NOT Gate', loc: [700, 640] });
  });
  await page.locator('.filebar .ptab', { hasText: 'demo-datapath.circ•' }).waitFor();
  await r.app.evaluate(({ BrowserWindow }) => { BrowserWindow.getAllWindows()[0].close(); });
  await page.locator('dialog.ask', { hasText: '저장하지 않고 끝내면' }).waitFor();
  await shot(r, 'leave-dialog');
  await page.locator('dialog.ask').getByRole('button', { name: 'Discard' }).click();
  await r.app.waitForEvent('close').catch(() => {});
}

// No engine: the dialog (no character: an error) over the first screen and its band.
{
  const r = await launch(FHD, { env: { HCS_ENGINE_CMD: '', HCS_ENGINE_JAR: '/opt/hcs/hcs-engine.jar' } });
  await r.page.locator('dialog.ask').waitFor();
  await shot(r, 'engine-failed');
  await r.close();
}

