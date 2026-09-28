/* Canvas 2D or SVG elements? (N-05, D-137; the v2 brief 3-4 and addendum
   item 3: measured once on ref-mips, the decision in DECISIONS).

   The real window opens tests/mips/ref-mips.circ (715 parts, 580 tunnels)
   at a 1920×1080 lab PC's size at 100 % and at 150 % (device scale 1 and
   1.5), and draws it two ways from the SAME registry definitions:
     (a) Canvas 2D -- the app's Canvas (canvas.ts),
     (b) SVG DOM -- an <svg> with an element per shape (svg.ts svgElement),
         the view a transform on its root group, values by replacing the
         changed parts' elements and setting the wires' strokes.
   For each: pan frames and zoom frames (120 each, one step per animation
   frame), and the values during clocking -- sim.values every 16 ms -- both
   the engine's own stream while N Cycles runs (the real engine) and a
   worst case where every net changes every 16 ms (synthetic, 3 s).
   Reported per case: the frame interval (requestAnimationFrame to the
   next: what the student sees; 16.7 ms is 60 fps) mean / p95 / worst and
   the share of frames over 17 ms, and the script time per frame.

     npm run build:ui
     xvfb-run -a -s '-screen 0 2400x1400x24' node tools/measure-canvas.ts
   With engine/build/stage/hcs-engine.jar and HCS_JAVA=<a Java 21>/bin/java
   the real engine runs (and the engine's stream is measured); otherwise
   the fake engine answers from the fixtures.  Writes build/measure-canvas.json. */

import { existsSync, mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';

import { launch, openFile, repo, root, sample } from '../tests/e2e/harness.ts';

const JAR = path.join(repo, 'engine/build/stage/hcs-engine.jar');
const real = existsSync(JAR) && !!process.env.HCS_JAVA;

interface Stats { mean: number; p95: number; max: number; over: number; n: number }
interface Case { frames: Stats; script: Stats }
type Result = Record<string, Case | number | string>;

async function measure(label: string, size: { width: number; height: number }, scale: number): Promise<Result> {
  const r = await launch(size, { switches: [`--force-device-scale-factor=${scale}`], env: real ? { HCS_ENGINE_CMD: '', HCS_ENGINE_JAR: JAR } : {} });
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, 'tests/mips/ref-mips.circ'));
    await page.locator('.canvas-view canvas').waitFor();
    await page.waitForFunction(() => ((window as unknown as { __hcsCanvas?: { scene: { values: Map<string, string> } | null } }).__hcsCanvas?.scene?.values.size ?? 0) > 0);
    await page.evaluate(() => document.fonts.ready);
    await page.mouse.move(-10, -10);
    await page.waitForTimeout(500);
    const out = await page.evaluate(async (useEngine) => {
      type View = { x: number; y: number; zoom: number };
      type Net = { id: string; width: number; wires: string[]; ports: [string, number][] };
      type Comp = { id: string; ports: { i: number }[] };
      interface Board {
        view: View; width: number; height: number; paused: boolean; canvas: HTMLCanvasElement; root: HTMLElement; lastFrameMs: number;
        setView(v: View): void; draw(): void; invalidate(): void; fitView(a: boolean): void;
        partElements(c: Comp, d: Document): SVGElement[]; wireColorOf(n: string | null): string;
        scene: { fileId: string; circuitId: string; nets: Net[]; values: Map<string, string>; valueVersion: number; components: Map<string, Comp>; wires: Map<string, { id: string; a: number[]; b: number[] }>;
          applyValues(v: { fileId: string; circuitId: string; nets: Record<string, string> }): void; wireNet(id: string): Net | undefined; netOf(c: string, i: number): Net | undefined };
      }
      const c = (window as unknown as { __hcsCanvas: Board }).__hcsCanvas;
      const s = c.scene;
      const stats = (t: number[]) => {
        const a = [...t].sort((x, y) => x - y);
        const mean = a.reduce((x, y) => x + y, 0) / Math.max(1, a.length);
        const r2 = (v: number) => Math.round(v * 100) / 100;
        return { mean: r2(mean), p95: r2(a[Math.floor(a.length * 0.95)] ?? 0), max: r2(a[a.length - 1] ?? 0), over: r2(a.filter((x) => x > 17).length / Math.max(1, a.length)), n: a.length };
      };
      // n animation frames, `step` run in each; the intervals between them and the script time of each
      const frames = (n: number, step: (i: number) => void) => new Promise<{ frames: ReturnType<typeof stats>; script: ReturnType<typeof stats> }>((done) => {
        const iv: number[] = [], sc: number[] = [];
        let last = 0, i = 0;
        const f = (now: number) => {
          if (last) iv.push(now - last);
          last = now;
          if (i >= n) { done({ frames: stats(iv), script: stats(sc) }); return; }
          const t = performance.now();
          step(i++);
          sc.push(performance.now() - t);
          requestAnimationFrame(f);
        };
        requestAnimationFrame(f);
      });
      // for `ms`, a callback every animation frame; the intervals
      const during = (ms: number, each: () => number) => new Promise<{ frames: ReturnType<typeof stats>; script: ReturnType<typeof stats> }>((done) => {
        const iv: number[] = [], sc: number[] = [];
        let last = 0;
        const end = performance.now() + ms;
        const f = (now: number) => {
          if (last) iv.push(now - last);
          last = now;
          sc.push(each());
          if (now < end) requestAnimationFrame(f); else done({ frames: stats(iv), script: stats(sc) });
        };
        requestAnimationFrame(f);
      });
      const res: Record<string, unknown> = {};
      c.fitView(false);
      const fit = { ...c.view };
      const at100: View = { zoom: 1, x: fit.x + c.width / 2 / fit.zoom - c.width / 2, y: fit.y + c.height / 2 / fit.zoom - c.height / 2 };
      const zoomAbout = (v: View, z: number): View => ({ zoom: z, x: v.x + c.width / 2 / v.zoom - c.width / 2 / z, y: v.y + c.height / 2 / v.zoom - c.height / 2 / z });
      const zoomOf = (i: number) => 0.25 * Math.pow(8, (i % 60) / 60);   // 25 % … 200 % and back

      // ---- (a) Canvas 2D ----
      c.setView(at100);
      res.canvasPan100 = await frames(120, (i) => { c.setView({ ...at100, x: at100.x + i * 8 }); c.draw(); });
      res.canvasPanFit = await frames(120, (i) => { c.setView({ ...fit, x: fit.x + (i * 8) / fit.zoom }); c.draw(); });
      res.canvasZoom = await frames(120, (i) => { c.setView(zoomAbout(fit, zoomOf(i))); c.draw(); });
      c.setView(at100);
      const nets = s.nets;
      const synthetic = (k: number) => {
        const v: Record<string, string> = {};
        for (const n of nets) v[n.id] = n.width === 1 ? String((k + n.id.length) & 1) : Array.from({ length: n.width }, (_, b) => String(((k >> (b % 5)) ^ b) & 1)).join('');
        s.applyValues({ fileId: s.fileId, circuitId: s.circuitId, nets: v });
      };
      let k = 0;
      let timer = setInterval(() => { synthetic(++k); c.invalidate(); }, 16);
      res.canvasValuesAll = await during(3000, () => c.lastFrameMs);
      clearInterval(timer);
      // the same with the whole circuit on screen (fitted: every part drawn again every frame)
      c.setView(fit);
      timer = setInterval(() => { synthetic(++k); c.invalidate(); }, 16);
      res.canvasValuesAllFit = await during(3000, () => c.lastFrameMs);
      clearInterval(timer);
      c.setView(at100);
      if (useEngine) {
        const app = (window as unknown as { app: { call(m: string, p: object): Promise<unknown> } }).app;
        await app.call('sim.reset', { fileId: s.fileId });
        const v0 = s.valueVersion;
        await app.call('sim.cycles', { fileId: s.fileId, n: 20000 });
        res.canvasValuesEngine = await during(3000, () => c.lastFrameMs);
        res.engineFrames = s.valueVersion - v0;
        await app.call('sim.reset', { fileId: s.fileId });
      }

      // ---- (b) SVG elements from the same definitions ----
      c.paused = true;
      const NS = 'http://www.w3.org/2000/svg';
      const t0 = performance.now();
      const svg = document.createElementNS(NS, 'svg');
      svg.setAttribute('style', 'position:absolute;inset:0;width:100%;height:100%;background:#fff');
      const g = document.createElementNS(NS, 'g');
      const wireEls = new Map<string, SVGElement>();
      for (const w of s.wires.values()) {
        const n = s.wireNet(w.id);
        const l = document.createElementNS(NS, 'line');
        for (const [a, v] of [['x1', w.a[0]], ['y1', w.a[1]], ['x2', w.b[0]], ['y2', w.b[1]]] as const) l.setAttribute(a, String(v));
        l.setAttribute('stroke', c.wireColorOf(n?.id ?? null));
        l.setAttribute('stroke-width', String(n && n.width > 1 ? 3.5 : 2));
        l.setAttribute('stroke-linecap', 'round');
        wireEls.set(w.id, l);
        g.append(l);
      }
      const partEls = new Map<string, SVGGElement>();
      for (const p of s.components.values()) {
        const pg = document.createElementNS(NS, 'g');
        pg.append(...c.partElements(p, document));
        partEls.set(p.id, pg);
        g.append(pg);
      }
      svg.append(g);
      c.root.append(svg);
      c.canvas.style.visibility = 'hidden';
      const setT = (v: View) => g.setAttribute('transform', `scale(${v.zoom}) translate(${-v.x} ${-v.y})`);
      setT(at100);
      await new Promise((d) => requestAnimationFrame(() => requestAnimationFrame(d)));
      res.svgBuildMs = Math.round(performance.now() - t0);
      res.svgElements = svg.querySelectorAll('*').length;
      res.svgPan100 = await frames(120, (i) => setT({ ...at100, x: at100.x + i * 8 }));
      res.svgPanFit = await frames(120, (i) => setT({ ...fit, x: fit.x + (i * 8) / fit.zoom }));
      res.svgZoom = await frames(120, (i) => setT(zoomAbout(fit, zoomOf(i))));
      setT(at100);
      // values: the changed nets' wires recoloured, the parts on them drawn again (the same definitions)
      const partsOn = new Map<string, string[]>();
      for (const n of nets) partsOn.set(n.id, [...new Set(n.ports.map(([cid]) => cid))]);
      let seen = new Map(s.values);
      const update = () => {
        const t = performance.now();
        const changed: string[] = [];
        for (const [id, v] of s.values) if (seen.get(id) !== v) changed.push(id);
        seen = new Map(s.values);
        const parts = new Set<string>();
        for (const id of changed) {
          const n = nets.find((x) => x.id === id);
          for (const w of n?.wires ?? []) wireEls.get(w)?.setAttribute('stroke', c.wireColorOf(id));
          for (const p of partsOn.get(id) ?? []) parts.add(p);
        }
        for (const id of parts) {
          const p = s.components.get(id);
          if (p) partEls.get(id)!.replaceChildren(...c.partElements(p, document));
        }
        return performance.now() - t;
      };
      let lastVersion = s.valueVersion;
      timer = setInterval(() => synthetic(++k), 16);
      res.svgValuesAll = await during(3000, () => { if (s.valueVersion === lastVersion) return 0; lastVersion = s.valueVersion; return update(); });
      clearInterval(timer);
      if (useEngine) {
        const app = (window as unknown as { app: { call(m: string, p: object): Promise<unknown> } }).app;
        await app.call('sim.cycles', { fileId: s.fileId, n: 20000 });
        res.svgValuesEngine = await during(3000, () => { if (s.valueVersion === lastVersion) return 0; lastVersion = s.valueVersion; return update(); });
        await app.call('sim.reset', { fileId: s.fileId });
      }
      svg.remove();
      c.canvas.style.visibility = '';
      c.paused = false;
      res.parts = s.components.size;
      res.nets = nets.length;
      return res;
    }, real);
    return { label, scale, ...(out as Result) };
  } finally {
    await r.close();
  }
}

const results: Result[] = [];
for (const [label, size, scale] of [['1920x1080 100 %', { width: 1920, height: 1032 }, 1], ['1920x1080 150 %', { width: 1280, height: 688 }, 1.5]] as const) {
  results.push(await measure(label, size, scale));
}
mkdirSync(path.join(root, 'build'), { recursive: true });
writeFileSync(path.join(root, 'build/measure-canvas.json'), `${JSON.stringify({ engine: real ? 'real' : 'fake', results }, null, 1)}\n`);
const row = (name: string, x: Case | undefined) => (x ? `| ${name} | ${x.frames.mean} | ${x.frames.p95} | ${x.frames.max} | ${Math.round(x.frames.over * 100)}% | ${x.script.mean} | ${x.script.p95} |` : '');
for (const r of results) {
  console.log(`\n### ${r.label} (engine: ${real ? 'real' : 'fake'}; ${r.parts} parts, ${r.nets} nets; SVG: ${r.svgElements} elements, built in ${r.svgBuildMs} ms${r.engineFrames !== undefined ? `; engine stream: ${r.engineFrames} value frames in 3 s` : ''})`);
  console.log('| case | frame mean ms | p95 | worst | frames > 17 ms | script mean ms | script p95 |');
  console.log('| --- | --- | --- | --- | --- | --- | --- |');
  for (const [k, name] of [['canvasPan100', 'Canvas pan 100 %'], ['svgPan100', 'SVG pan 100 %'], ['canvasPanFit', 'Canvas pan fit'], ['svgPanFit', 'SVG pan fit'],
    ['canvasZoom', 'Canvas zoom 25–200 %'], ['svgZoom', 'SVG zoom 25–200 %'], ['canvasValuesAll', 'Canvas values: every net each 16 ms'], ['canvasValuesAllFit', 'Canvas values: every net each 16 ms, fitted (all parts on screen)'], ['svgValuesAll', 'SVG values: every net each 16 ms'],
    ['canvasValuesEngine', 'Canvas values: engine stream (N Cycles running)'], ['svgValuesEngine', 'SVG values: engine stream (N Cycles running)']] as const) {
    const line = row(name, r[k] as Case | undefined);
    if (line) console.log(line);
  }
}
