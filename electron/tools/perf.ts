/* The v2 performance goals, measured (N-22, D-160; the v2 brief 3-5):
   the real window with the real engine, as a student starts and uses it.

     npm run build:ui
     xvfb-run -a -s '-screen 0 2400x1400x24' node tools/perf.ts [--runs 3] [--json <file>]
         the source tree: engine/build/stage's jars on HCS_JAVA or engine/build/runtime (the bundled runtime)
     node tools/perf.ts --exe <install folder>\HallymCircuitStudio.exe [--install-json report/install.json]
         [--launch-json report/launch.json] [--json report/perf.json]
         the installed program (CI setup-e2e), with the install checks' sizes and first start after the install

   It measures (tools/perf-goals.ts judges; the table goes to the log, the
   job summary and --json):
     start       launch to the window, to the first screen, to the engine's hello (--runs launches at 100 %;
                 the first is cold)
     open        Ctrl+O on tests/mips/ref-mips.circ to the Canvas's first frame of it (every launch)
     frames      requestAnimationFrame intervals while the wheel pans (the view the file opens with, fitted,
                 and at zoom 100 %) and Ctrl+wheel zooms 25–200 %, one wheel event a frame -- at display
                 100 % (1920×1032) and 150 % (1280×688 at device scale 1.5)
     ncycles     tests/hmx/mips/factorial.hmx loaded (Load Program…), N Cycles 1000: Enter to "Cycle 1,000"
     run         Run at the fastest clock (4 kHz) for 3 s: the frames, the longest, the dropped
     sizes       setup exe and installed folder (--install-json), the bundled runtime (source tree)
   A measurement it cannot make is skipped with the reason.  Exits 1 when a
   goal is over its CI limit. */

import { _electron, type ElectronApplication, type Page } from '@playwright/test';
import { copyFileSync, existsSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, rmSync, statSync, writeFileSync, appendFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';

import { failed, frameStats, judge, markdown, readInstall, readLaunch, type FrameStats, type Results, type StartTimes, type Where } from './perf-goals.ts';

const root = path.join(import.meta.dirname, '..');
const repo = path.join(root, '..');
const arg = (name: string) => { const i = process.argv.indexOf(name); return i > 0 ? process.argv[i + 1] : undefined; };
const exe = arg('--exe') ?? process.env.HCS_PERF_EXE;
const runs = Math.max(1, Number(arg('--runs') ?? 3));
const jsonOut = arg('--json') ?? path.join(root, 'build/perf.json');
const JAR = path.join(repo, 'engine/build/stage/hcs-engine.jar');
const javaExe = process.platform === 'win32' ? 'java.exe' : 'java';
const RUNTIME_JAVA = path.join(repo, 'engine/build/runtime/bin', javaExe);
const java = process.env.HCS_JAVA ?? (existsSync(RUNTIME_JAVA) ? RUNTIME_JAVA : undefined);

const where: Where = exe ? 'windows-installed' : process.platform === 'win32' ? 'windows' : 'linux';
const results: Results = { where, platform: `${process.platform}-${process.arch}`, skipped: {} };
const skip = (key: string, why: string) => { results.skipped![key] = why; console.log(`skipped ${key}: ${why}`); };

const SIZES = { '100 %': { size: { width: 1920, height: 1032 }, scale: 1 }, '150 %': { size: { width: 1280, height: 688 }, scale: 1.5 } } as const;
type Scale = keyof typeof SIZES;

// ---- launching --------------------------------------------------------------------------------------------

interface Running { app: ElectronApplication; page: Page; dir: string; times: StartTimes; close(): Promise<void> }

// The installed program's own environment: none of the tests' switches (HCS_*), Electron not as Node.
function studentEnv(): Record<string, string> {
  const env: Record<string, string> = {};
  for (const [k, v] of Object.entries(process.env)) if (v !== undefined && !/^HCS_|^ELECTRON_RUN_AS_NODE$/i.test(k)) env[k] = v;
  return env;
}

// The source tree's window with the real engine, a fresh home and run folder (tests/e2e/harness.ts's way).
function devEnv(dir: string): Record<string, string> {
  const home = path.join(dir, 'home');
  mkdirSync(home, { recursive: true });
  const env: Record<string, string> = {
    ...(process.env as Record<string, string>), HOME: home, XDG_CONFIG_HOME: path.join(home, '.config'), XDG_CACHE_HOME: path.join(home, '.cache'),
    XDG_DATA_HOME: path.join(home, '.local/share'), HCS_USER_DATA: path.join(dir, 'user-data'), HCS_ENGINE_JAR: JAR, HCS_JAVA: java!,
  };
  delete env.HCS_ENGINE_CMD;
  delete env.ELECTRON_RUN_AS_NODE;
  return env;
}

const engineHello = (app: ElectronApplication) => app.evaluate(() =>
  (globalThis as unknown as { __hcs: { engine: { status(): { hello: unknown } } } }).__hcs.engine.status().hello);

async function start(scale: Scale): Promise<Running> {
  const dir = mkdtempSync(path.join(tmpdir(), 'hcs-perf-'));
  const switches = [`--force-device-scale-factor=${SIZES[scale].scale}`];
  const t0 = performance.now();
  const app = exe
    ? await _electron.launch({ executablePath: exe, args: switches, env: studentEnv(), timeout: 60_000 })
    : await _electron.launch({ args: [...switches, path.join(root, 'src/main/main.ts')], env: devEnv(dir), cwd: root, timeout: 60_000 });
  const page = await app.firstWindow();
  const windowMs = performance.now() - t0;
  await page.waitForFunction(() => document.querySelector('.wcard') !== null, null, { polling: 10, timeout: 60_000 });
  const startScreenMs = performance.now() - t0;
  const until = Date.now() + 60_000;
  while (!(await engineHello(app))) {
    if (Date.now() > until) throw new Error('the engine did not answer engine.hello in 60 s');
    await new Promise((d) => setTimeout(d, 10));
  }
  const engineReadyMs = performance.now() - t0;
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  const round = (n: number) => Math.round(n);
  return {
    app, page, dir, times: { windowMs: round(windowMs), startScreenMs: round(startScreenMs), engineReadyMs: round(engineReadyMs) },
    close: async () => {
      await app.close();
      rmSync(dir, { recursive: true, force: true });
      if (errors.length) throw new Error(`errors in the window:\n${errors.join('\n')}`);
    },
  };
}

// The window at the lab PC's size for the scale (restored first: a maximised window keeps its size).
async function resize(r: Running, scale: Scale): Promise<string> {
  const s = SIZES[scale].size;
  await r.app.evaluate(({ BrowserWindow }, z) => {
    const w = BrowserWindow.getAllWindows()[0];
    if (w.isMaximized()) w.unmaximize();
    w.setContentSize(z.width, z.height);
  }, s);
  try {
    await r.page.waitForFunction((z) => Math.abs(window.innerWidth - z.width) <= 1 && Math.abs(window.innerHeight - z.height) <= 1, s, { timeout: 5000 });
  } catch { /* a screen smaller than the window: measured at the size it got (reported) */ }
  return r.page.evaluate(() => `${window.innerWidth}×${window.innerHeight} at ${window.devicePixelRatio}`);
}

const answerOpen = (app: ElectronApplication, file: string) => app.evaluate(({ dialog }, f) => {
  dialog.showOpenDialog = (async () => ({ canceled: false, filePaths: [f] })) as typeof dialog.showOpenDialog;
}, file);

// ---- in the window ---------------------------------------------------------------------------------------

type Canvas = {
  view: { x: number; y: number; zoom: number }; width: number; height: number; lastFrameMs: number; canvas: HTMLCanvasElement;
  settled(): boolean; fitView(animate: boolean): void; setView(v: { x: number; y: number; zoom: number }): void;
  scene: { fileId: string; components: Map<string, unknown> } | null;
};

// Ctrl+O on ref-mips to the Canvas's first frame of it (the page watches every animation frame).
async function openRefMips(r: Running): Promise<number> {
  const file = path.join(r.dir, 'ref-mips.circ');
  copyFileSync(path.join(repo, 'tests/mips/ref-mips.circ'), file);
  await answerOpen(r.app, file);
  const t0 = performance.now();
  await r.page.keyboard.press('Control+o');
  await r.page.waitForFunction(() => {
    const c = (window as unknown as { __hcsCanvas?: Canvas }).__hcsCanvas;
    return !!c?.scene && c.scene.components.size > 500 && c.settled();
  }, null, { polling: 'raf', timeout: 60_000 });
  return performance.now() - t0;
}

// One wheel event a frame on the Canvas (the app's own input: canvas.ts listen), `n` frames; the intervals.
async function wheelFrames(page: Page, kind: 'panFit' | 'pan100' | 'zoom'): Promise<FrameStats> {
  const raw = await page.evaluate(async (k) => {
    const c = (window as unknown as { __hcsCanvas: Canvas }).__hcsCanvas;
    c.fitView(false);
    if (k === 'pan100') {
      const f = c.view;
      c.setView({ zoom: 1, x: f.x + c.width / 2 / f.zoom - c.width / 2, y: f.y + c.height / 2 / f.zoom - c.height / 2 });
    }
    await new Promise((d) => requestAnimationFrame(() => requestAnimationFrame(d)));
    const box = c.canvas.getBoundingClientRect();
    const at = { clientX: box.left + box.width / 2, clientY: box.top + box.height / 2 };
    const n = 120;
    return new Promise<{ iv: number[]; sc: number[] }>((done) => {
      const iv: number[] = [], sc: number[] = [];
      let last = 0, i = 0;
      const f = (now: number) => {
        if (last) { iv.push(now - last); sc.push(c.lastFrameMs); }
        last = now;
        if (i >= n) { done({ iv, sc }); return; }
        const e = k === 'zoom'
          // 60 frames in (toward 200 %), 60 out; from the fitted view (about 25 %)
          ? new WheelEvent('wheel', { ...at, deltaY: i < n / 2 ? -40 : 40, ctrlKey: true, bubbles: true, cancelable: true })
          : new WheelEvent('wheel', { ...at, deltaX: i % 60 < 30 ? 24 : -24, deltaY: 12, bubbles: true, cancelable: true });
        c.canvas.dispatchEvent(e);
        i += 1;
        requestAnimationFrame(f);
      };
      requestAnimationFrame(f);
    });
  }, kind);
  return frameStats(raw.iv, raw.sc);
}

// A frame recorder in the page: started, then stopped -- the intervals and the Canvas's drawing time meanwhile.
async function startFrames(page: Page): Promise<void> {
  await page.evaluate(() => {
    const c = (window as unknown as { __hcsCanvas: Canvas }).__hcsCanvas;
    const rec = { iv: [] as number[], sc: [] as number[], on: true };
    (window as unknown as { __perfFrames: typeof rec }).__perfFrames = rec;
    let last = 0;
    const f = (now: number) => {
      if (!rec.on) return;
      if (last) { rec.iv.push(now - last); rec.sc.push(c.lastFrameMs); }
      last = now;
      requestAnimationFrame(f);
    };
    requestAnimationFrame(f);
  });
}
async function stopFrames(page: Page): Promise<FrameStats> {
  const raw = await page.evaluate(() => {
    const rec = (window as unknown as { __perfFrames: { iv: number[]; sc: number[]; on: boolean } }).__perfFrames;
    rec.on = false;
    return { iv: rec.iv, sc: rec.sc };
  });
  return frameStats(raw.iv, raw.sc);
}

// The engine's cycle count for the file on show (sim.state, as the window asks it).
const cycleOf = (page: Page): Promise<number> => page.evaluate(async () => {
  const c = (window as unknown as { __hcsCanvas: Canvas }).__hcsCanvas;
  const st = await (window as unknown as { app: { call(m: string, p: object): Promise<{ cycle: number }> } }).app.call('sim.state', { fileId: c.scene!.fileId });
  return st.cycle;
});

// factorial.hmx through Load Program…, then N Cycles 1000 (the dialog; Enter to the status bar's Cycle 1,000).
async function nCycles(r: Running): Promise<{ ms: number; frames: FrameStats }> {
  const hmx = path.join(r.dir, 'factorial.hmx');
  copyFileSync(path.join(repo, 'tests/hmx/mips/factorial.hmx'), hmx);
  await answerOpen(r.app, hmx);
  await r.page.getByRole('button', { name: /Load Program/ }).click();
  // loaded: the summary (program.ts summaryDialog), closed
  const summary = r.page.locator('dialog.loadsummary');
  await summary.waitFor({ timeout: 30_000 });
  await summary.getByRole('button', { name: 'OK' }).click();
  await summary.waitFor({ state: 'hidden' });
  // loading starts the simulation over (docs/engine-api.md mips.load)
  const until = Date.now() + 30_000;
  while ((await cycleOf(r.page)) !== 0) {
    if (Date.now() > until) throw new Error('the simulation did not start over after Load Program');
    await new Promise((d) => setTimeout(d, 50));
  }
  await r.page.getByRole('button', { name: /N Cycles/ }).click();
  await r.page.locator('dialog.cycles').getByRole('textbox').fill('1000');
  await startFrames(r.page);
  const t0 = performance.now();
  await r.page.keyboard.press('Enter');
  // (the status bar's parts are spans: "Cycle 1,000" runs into the next one in textContent)
  await r.page.waitForFunction(() => /Cycle 1,000(?![\d,])/.test(document.querySelector('.status')?.textContent ?? ''), null, { polling: 'raf', timeout: 60_000 });
  const ms = performance.now() - t0;
  return { ms, frames: await stopFrames(r.page) };
}

// Run at the fastest clock for `seconds`: the frames, and the cycles it made.
async function runFastest(r: Running, seconds: number): Promise<NonNullable<Results['run']>> {
  const speed = r.page.locator('select[aria-label="Clock speed"]');
  const hz = Math.max(...(await speed.locator('option').evaluateAll((os) => os.map((o) => Number((o as HTMLOptionElement).value)))));
  await speed.selectOption(String(hz));
  await r.page.keyboard.press('F5');
  await r.page.waitForFunction(() => /Running/.test(document.querySelector('.status')?.textContent ?? ''), null, { timeout: 10_000 });
  const c0 = await cycleOf(r.page);
  const t0 = performance.now();
  await startFrames(r.page);
  await new Promise((d) => setTimeout(d, seconds * 1000));
  const frames = await stopFrames(r.page);
  const c1 = await cycleOf(r.page);
  const took = (performance.now() - t0) / 1000;
  await r.page.keyboard.press('F5');
  await r.page.waitForFunction(() => !/Running/.test(document.querySelector('.status')?.textContent ?? ''), null, { timeout: 10_000 });
  return { hz, seconds: Math.round(took * 100) / 100, cycles: c1 - c0, frames };
}

// ---- the measurements ------------------------------------------------------------------------------------

function sizeOf(p: string): number {
  const s = statSync(p);
  if (!s.isDirectory()) return s.size;
  return readdirSync(p).reduce((n, e) => n + sizeOf(path.join(p, e)), 0);
}

if (!exe && (!existsSync(JAR) || !java)) {
  for (const k of ['start', 'open', 'frames', 'ncycles', 'run']) skip(k, `no real engine: ${existsSync(JAR) ? '' : 'engine/build/stage/hcs-engine.jar missing (./gradlew :engine:stage); '}${java ? '' : 'no Java 21 (HCS_JAVA or ./gradlew :engine:runtime)'}`);
} else {
  results.starts = [];
  results.openRefMipsMs = [];
  results.frames = {};
  for (let i = 0; i < runs; i += 1) {
    const r = await start('100 %');
    try {
      results.starts.push(r.times);
      const got = await resize(r, '100 %');
      results.openRefMipsMs.push(await openRefMips(r));
      console.log(`start ${i + 1}: window ${r.times.windowMs} ms, first screen ${r.times.startScreenMs} ms, engine ready ${r.times.engineReadyMs} ms; ref-mips open ${Math.round(results.openRefMipsMs.at(-1)!)} ms (${got})`);
      if (i === 0) {
        results.frames['100 %'] = { panFit: await wheelFrames(r.page, 'panFit'), pan100: await wheelFrames(r.page, 'pan100'), zoom: await wheelFrames(r.page, 'zoom') };
        await r.page.evaluate(() => (window as unknown as { __hcsCanvas: Canvas }).__hcsCanvas.fitView(false));
        const n = await nCycles(r);
        results.nCycles1000Ms = Math.round(n.ms);
        results.nCyclesFrames = n.frames;
        console.log(`N Cycles 1000 (ref-mips, factorial.hmx): ${results.nCycles1000Ms} ms`);
        results.run = await runFastest(r, 3);
        console.log(`Run at ${results.run.hz} Hz: ${results.run.cycles} cycles in ${results.run.seconds} s, longest frame ${results.run.frames.max} ms`);
      }
    } finally {
      await r.close();
    }
  }
  const r = await start('150 %');
  try {
    const got = await resize(r, '150 %');
    await openRefMips(r);
    results.frames['150 %'] = { panFit: await wheelFrames(r.page, 'panFit'), pan100: await wheelFrames(r.page, 'pan100'), zoom: await wheelFrames(r.page, 'zoom') };
    console.log(`frames at 150 % measured (${got})`);
  } finally {
    await r.close();
  }
}

results.sizes = {};
const installJson = arg('--install-json');
if (installJson && existsSync(installJson)) Object.assign(results.sizes, readInstall(JSON.parse(readFileSync(installJson, 'utf8'))));
else skip('setup-exe', exe ? `no install report (${installJson ?? '--install-json'})` : 'no setup exe here: the Windows job (setup-e2e) measures it');
if (results.sizes.installedBytes === undefined) {
  if (exe) results.sizes.installedBytes = sizeOf(path.dirname(exe));
  else skip('installed', 'not installed here: the Windows job (setup-e2e) measures it');
}
if (!exe && existsSync(path.join(repo, 'engine/build/runtime'))) results.sizes.runtimeBytes = sizeOf(path.join(repo, 'engine/build/runtime'));
const launchJson = arg('--launch-json');
if (launchJson && existsSync(launchJson)) {
  const first = readLaunch(JSON.parse(readFileSync(launchJson, 'utf8')));
  if (first) results.firstAfterInstall = first;
}

const rows = judge(results);
const title = `Performance (N-22): ${where}, ${results.platform}`;
const table = markdown(rows, title);
console.log(`\n${table}`);
mkdirSync(path.dirname(path.resolve(jsonOut)), { recursive: true });
writeFileSync(jsonOut, `${JSON.stringify({ results, rows }, null, 1)}\n`);
if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY, `\n${table}\n`);
const bad = failed(rows);
if (bad.length) {
  console.error(`over the CI limit: ${bad.map((x) => x.id).join(', ')}`);
  process.exitCode = 1;
}
