/* The v2 performance goals (N-22, D-160; the v2 brief 3-5) and how a
   measurement is judged against them -- no Electron, no engine: the unit
   tests read this (tests/unit/perf-goals.test.ts), tools/perf.ts measures
   and prints what it says.

   Each goal has the brief's number (`goal`) and a CI limit with a margin
   (`limit`): a value within the goal is met; over the goal but within the
   limit is missed (reported, CI goes on: a shared runner's noise, e.g. the
   first start after an install); over the limit fails the run.  Where a
   runner is too noisy to fail on at all, the goal is report-only there, and
   the reason is printed with the row.  A measurement that could not be
   made is skipped, with the reason -- never counted as met. */

export type Where = 'linux' | 'windows' | 'windows-installed';
export type Status = 'met' | 'missed' | 'failed' | 'report' | 'skipped';

// Frame intervals (requestAnimationFrame to the next, ms): what the student sees; 16.7 ms is 60 fps.
export interface FrameStats {
  n: number;        // intervals
  mean: number;
  p95: number;
  max: number;      // the longest frame
  over: number;     // the share of intervals over 17 ms (a frame late)
  dropped: number;  // frames that should have been drawn and were not: sum of round(interval / 16.67) - 1
  script?: { mean: number; p95: number; max: number };   // the Canvas's own drawing per frame (canvas.ts lastFrameMs)
}

export const FRAME_MS = 1000 / 60;

const r2 = (v: number) => Math.round(v * 100) / 100;
const pct = (sorted: number[], p: number) => sorted.length ? sorted[Math.min(sorted.length - 1, Math.floor(sorted.length * p))] : 0;

export function frameStats(intervals: readonly number[], script: readonly number[] = []): FrameStats {
  const a = [...intervals].sort((x, y) => x - y);
  const mean = a.reduce((x, y) => x + y, 0) / Math.max(1, a.length);
  const dropped = a.reduce((d, x) => d + Math.max(0, Math.round(x / FRAME_MS) - 1), 0);
  const out: FrameStats = { n: a.length, mean: r2(mean), p95: r2(pct(a, 0.95)), max: r2(a[a.length - 1] ?? 0), over: r2(a.filter((x) => x > 17).length / Math.max(1, a.length)), dropped };
  if (script.length) {
    const s = [...script].sort((x, y) => x - y);
    out.script = { mean: r2(s.reduce((x, y) => x + y, 0) / s.length), p95: r2(pct(s, 0.95)), max: r2(s[s.length - 1]) };
  }
  return out;
}

export const median = (xs: readonly number[]): number | undefined => {
  if (!xs.length) return undefined;
  const s = [...xs].sort((a, b) => a - b);
  return s[Math.floor(s.length / 2)];
};

export interface StartTimes { windowMs: number; startScreenMs: number; engineReadyMs: number }

// What tools/perf.ts measured (and what the Windows job's install checks measured before it).
export interface Results {
  where: Where;
  platform: string;
  starts?: StartTimes[];                    // this tool's launches at 100 %, the first one cold
  firstAfterInstall?: StartTimes;           // the installed e2e's first start after the install (report/launch.json)
  openRefMipsMs?: number[];                 // Ctrl+O on ref-mips to its first drawn frame, one per launch
  frames?: Record<string, Record<'panFit' | 'pan100' | 'zoom', FrameStats>>;   // by scale: "100 %", "150 %"
  nCycles1000Ms?: number;                   // ref-mips with factorial.hmx loaded: Enter in N Cycles to "Cycle 1,000"
  nCyclesFrames?: FrameStats;               // the window's frames meanwhile
  run?: { hz: number; seconds: number; cycles: number; frames: FrameStats };   // Run at the fastest clock
  sizes?: { setupExeBytes?: number; installedBytes?: number; runtimeBytes?: number };
  skipped?: Record<string, string>;         // measurement -> why it was not made
}

export interface Row {
  id: string;
  what: string;
  value: string;
  goal: string;
  status: Status;
  note?: string;
}

/* v1's N Cycles 1000 on the same circuit (ref-mips with factorial.hmx loaded), measured once headlessly from app/
   (v1.0.3's CyclePacer and Recorder, no drawing -- faster than v1 as the student saw it) at its fastest clock
   (4 kHz; its 5 ms timer is the bound): D-160.  v2 must not be slower. */
export const V1_NCYCLES_1000_MS = 10_165;

// The goals: the brief's number and the CI limit (a margin over it) or report-only, by runner.
export const GOALS = {
  start: { goalMs: 4000, limitMs: 8000 },
  open: { goalMs: 2000, limitMs: 5000 },
  // 60 fps: the mean interval a frame (16.7 ms, +0.8 for the clock's jitter) and 95 % of frames on time.
  frames: { goalMeanMs: 17.5, goalP95Ms: 17.5, limitMeanMs: 25 },
  nCycles: { goalMs: V1_NCYCLES_1000_MS, limitMs: V1_NCYCLES_1000_MS },
  // Run does not stutter: no frame longer than 3 frames' time, at most 5 % of frames dropped.
  run: { goalMaxMs: 50, goalDroppedShare: 0.05, limitMaxMs: 250 },
  setupExe: { limitBytes: 160 * 1048576 },
} as const;

/* Report-only, by runner and goal (the reason goes with the row).  Windows CI (windows-2022, no GPU: Chromium
   draws through WARP, a software rasteriser, on a shared VM whose other programs run whenever they like) is too
   noisy to fail a frame or a start time on; Linux CI draws in Xvfb, also software, and is steady enough (D-160). */
export const REPORT_ONLY: Partial<Record<Where, Partial<Record<'start' | 'open' | 'frames' | 'run', string>>>> = {
  windows: { frames: 'Windows CI draws without a GPU (WARP) on a shared VM', run: 'Windows CI draws without a GPU (WARP) on a shared VM' },
  'windows-installed': { frames: 'Windows CI draws without a GPU (WARP) on a shared VM', run: 'Windows CI draws without a GPU (WARP) on a shared VM' },
};

const ms = (v: number) => `${Math.round(v).toLocaleString('en-US')} ms`;
const mb = (b: number) => `${(b / 1048576).toFixed(1)} MB`;

// value within goal: met; within limit: missed; over: failed; report-only (reason): report.
function grade(within: boolean, withinLimit: boolean, reportOnly: string | undefined): { status: Status; note?: string } {
  if (reportOnly) return { status: 'report', note: `report-only: ${reportOnly}${within ? '' : ' (over the goal)'}` };
  if (within) return { status: 'met' };
  return withinLimit ? { status: 'missed', note: 'over the goal, within the CI limit' } : { status: 'failed' };
}

function skippedRow(id: string, what: string, goal: string, r: Results, key: string): Row {
  return { id, what, value: '—', goal, status: 'skipped', note: r.skipped?.[key] ?? 'not measured' };
}

export function judge(r: Results): Row[] {
  const rows: Row[] = [];
  const only = REPORT_ONLY[r.where] ?? {};
  const g = GOALS;

  // 1. the first start (with the engine): the first after the install where there is one, else this tool's first (cold)
  const first = r.firstAfterInstall ?? r.starts?.[0];
  const startGoal = `≤ ${ms(g.start.goalMs)} (CI limit ${ms(g.start.limitMs)})`;
  if (first) {
    const v = first.engineReadyMs;
    rows.push({ id: 'start', what: r.firstAfterInstall ? 'first start after install: engine ready' : 'first start: engine ready', value: `${ms(v)} (window ${ms(first.windowMs)}, first screen ${ms(first.startScreenMs)})`,
      goal: startGoal, ...grade(v <= g.start.goalMs, v <= g.start.limitMs, only.start) });
  } else rows.push(skippedRow('start', 'first start: engine ready', startGoal, r, 'start'));
  if (r.starts && r.starts.length > (r.firstAfterInstall ? 0 : 1)) {
    const rest = r.firstAfterInstall ? r.starts : r.starts.slice(1);
    const m = median(rest.map((s) => s.engineReadyMs))!;
    rows.push({ id: 'start-warm', what: `start again (median of ${rest.length}): engine ready`, value: ms(m), goal: startGoal, ...grade(m <= g.start.goalMs, m <= g.start.limitMs, only.start) });
  }

  // 2. ref-mips open
  const openGoal = `≤ ${ms(g.open.goalMs)} (CI limit ${ms(g.open.limitMs)})`;
  const open = r.openRefMipsMs?.length ? median(r.openRefMipsMs) : undefined;
  if (open !== undefined) {
    rows.push({ id: 'open', what: `ref-mips open to its first frame (median of ${r.openRefMipsMs!.length})`, value: `${ms(open)} (all: ${r.openRefMipsMs!.map((x) => Math.round(x)).join(', ')})`,
      goal: openGoal, ...grade(open <= g.open.goalMs, open <= g.open.limitMs, only.open) });
  } else rows.push(skippedRow('open', 'ref-mips open to its first frame', openGoal, r, 'open'));

  // 3. pan and zoom at 100 % and 150 %
  const frameGoal = `60 fps: mean ≤ ${g.frames.goalMeanMs} ms, p95 ≤ ${g.frames.goalP95Ms} ms (CI limit mean ${g.frames.limitMeanMs} ms)`;
  for (const scale of ['100 %', '150 %']) {
    const f = r.frames?.[scale];
    for (const [k, name] of [['panFit', 'pan, fitted'], ['pan100', 'pan at zoom 100 %'], ['zoom', 'zoom 25–200 %']] as const) {
      const id = `frames-${scale.replace(/\D/g, '')}-${k}`;
      const what = `ref-mips ${name}, display ${scale}`;
      const s = f?.[k];
      if (!s) { rows.push(skippedRow(id, what, frameGoal, r, 'frames')); continue; }
      rows.push({ id, what, value: `mean ${s.mean} / p95 ${s.p95} / worst ${s.max} ms, ${Math.round(s.over * 100)} % late${s.script ? `; drawing ${s.script.mean} ms` : ''}`,
        goal: frameGoal, ...grade(s.mean <= g.frames.goalMeanMs && s.p95 <= g.frames.goalP95Ms, s.mean <= g.frames.limitMeanMs, only.frames) });
    }
  }

  // 4. N Cycles 1000
  const nGoal = `≤ v1 ${ms(g.nCycles.goalMs)}`;
  if (r.nCycles1000Ms !== undefined) {
    const v = r.nCycles1000Ms;
    rows.push({ id: 'ncycles', what: 'N Cycles 1000, ref-mips with factorial.hmx', value: `${ms(v)}${r.nCyclesFrames ? ` (frames meanwhile: worst ${r.nCyclesFrames.max} ms)` : ''}`,
      goal: nGoal, ...grade(v <= g.nCycles.goalMs, v <= g.nCycles.limitMs, undefined) });
  } else rows.push(skippedRow('ncycles', 'N Cycles 1000, ref-mips with factorial.hmx', nGoal, r, 'ncycles'));

  // 5. Run at the fastest clock
  const runGoal = `longest frame ≤ ${g.run.goalMaxMs} ms, ≤ ${g.run.goalDroppedShare * 100} % dropped (CI limit ${g.run.limitMaxMs} ms)`;
  if (r.run) {
    const f = r.run.frames;
    const expected = f.n + f.dropped;
    const share = expected ? f.dropped / expected : 0;
    rows.push({ id: 'run', what: `Run at ${r.run.hz >= 1024 ? `${r.run.hz / 1024} kHz` : `${r.run.hz} Hz`}, ${r.run.seconds} s (${Math.round(r.run.cycles / r.run.seconds).toLocaleString('en-US')} cycles/s)`,
      value: `longest ${f.max} ms, ${f.dropped} of ${expected} frames dropped (${(share * 100).toFixed(1)} %)`, goal: runGoal,
      ...grade(f.max <= g.run.goalMaxMs && share <= g.run.goalDroppedShare, f.max <= g.run.limitMaxMs, only.run) });
  } else rows.push(skippedRow('run', 'Run at the fastest clock', runGoal, r, 'run'));

  // 6. sizes: reported; the setup exe has a ceiling (a regression guard, not a brief number)
  const s = r.sizes ?? {};
  const exeGoal = `reported (CI limit ${mb(g.setupExe.limitBytes)})`;
  if (s.setupExeBytes !== undefined) {
    rows.push({ id: 'setup-exe', what: 'setup exe', value: mb(s.setupExeBytes), goal: exeGoal, ...(s.setupExeBytes <= g.setupExe.limitBytes ? { status: 'met' as const } : { status: 'failed' as const }) });
  } else rows.push(skippedRow('setup-exe', 'setup exe', exeGoal, r, 'setup-exe'));
  if (s.installedBytes !== undefined) rows.push({ id: 'installed', what: 'installed folder', value: mb(s.installedBytes), goal: 'reported', status: 'report' });
  else rows.push(skippedRow('installed', 'installed folder', 'reported', r, 'installed'));
  if (s.runtimeBytes !== undefined) rows.push({ id: 'runtime', what: 'bundled Java runtime (on disk)', value: mb(s.runtimeBytes), goal: 'reported', status: 'report' });
  return rows;
}

export const failed = (rows: readonly Row[]): Row[] => rows.filter((x) => x.status === 'failed');

export function markdown(rows: readonly Row[], title: string): string {
  const esc = (s: string) => s.replace(/\|/g, '\\|');
  return [`### ${title}`, '', '| | measured | goal | | note |', '|---|---|---|---|---|',
    ...rows.map((x) => `| ${esc(x.what)} | ${esc(x.value)} | ${esc(x.goal)} | ${x.status} | ${esc(x.note ?? '')} |`), ''].join('\n');
}

// The install checks' reports (CI setup-e2e): report/install.json and report/launch.json.
export function readInstall(json: unknown): { setupExeBytes?: number; installedBytes?: number } {
  const o = (json ?? {}) as Record<string, unknown>;
  const n = (v: unknown) => (typeof v === 'number' && Number.isFinite(v) ? v : undefined);
  return { setupExeBytes: n(o.setupExeBytes), installedBytes: n(o.installedBytes) };
}
export function readLaunch(json: unknown): StartTimes | undefined {
  const f = ((json ?? {}) as Record<string, unknown>).first as Record<string, unknown> | undefined;
  if (!f || [f.windowMs, f.startScreenMs, f.engineReadyMs].some((v) => typeof v !== 'number')) return undefined;
  return { windowMs: f.windowMs as number, startScreenMs: f.startScreenMs as number, engineReadyMs: f.engineReadyMs as number };
}
