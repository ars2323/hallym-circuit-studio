/* tools/perf-goals.ts (N-22, D-160): the v2 performance goals and how
   tools/perf.ts's measurements are judged -- frame statistics, met /
   missed (over the goal, within the CI limit) / failed (over the limit) /
   report-only (a noisy runner, with the reason) / skipped (not measured,
   with the reason, never met), the table, the install checks' reports. */

import assert from 'node:assert/strict';
import { test } from 'node:test';

import { failed, FRAME_MS, frameStats, GOALS, judge, markdown, median, readInstall, readLaunch, REPORT_ONLY, type Results, type Row, V1_NCYCLES_1000_MS } from '../../tools/perf-goals.ts';

const smooth = frameStats(Array(120).fill(FRAME_MS));
const start = (ms: number) => ({ windowMs: ms - 300, startScreenMs: ms - 10, engineReadyMs: ms });
const frames = (s = smooth) => ({ panFit: s, pan100: s, zoom: s });
const all = (over: Partial<Results> = {}): Results => ({
  where: 'linux', platform: 'linux-x64',
  starts: [start(900), start(800), start(850)], openRefMipsMs: [700, 750, 900],
  frames: { '100 %': frames(), '150 %': frames() },
  nCycles1000Ms: 3000, nCyclesFrames: smooth,
  run: { hz: 4096, seconds: 3, cycles: 1500, frames: smooth },
  sizes: { setupExeBytes: 123 * 1048576, installedBytes: 433 * 1048576, runtimeBytes: 116 * 1048576 },
  ...over,
});
const row = (rows: Row[], id: string) => { const r = rows.find((x) => x.id === id); assert.ok(r, `row ${id}`); return r; };

test('frameStats: mean, p95, the longest, the share late, the frames dropped', () => {
  const s = frameStats([16.7, 16.6, 16.7, 33.4, 100], [1, 2, 3]);
  assert.equal(s.n, 5);
  assert.equal(s.max, 100);
  assert.equal(s.over, 0.4);
  assert.equal(s.dropped, 1 + 5, 'a 33 ms frame drops one, a 100 ms frame five');
  assert.equal(s.mean, 36.68);
  assert.deepEqual(s.script, { mean: 2, p95: 3, max: 3 });
  assert.equal(smooth.dropped, 0);
  assert.equal(smooth.over, 0);
  assert.equal(frameStats([]).n, 0);
  assert.equal(frameStats([]).script, undefined);
  assert.equal(median([3, 1, 2]), 2);
  assert.equal(median([]), undefined);
});

test('all goals met: the rows in order, nothing failed', () => {
  const rows = judge(all());
  assert.deepEqual(rows.map((x) => x.id), ['start', 'start-warm', 'open',
    'frames-100-panFit', 'frames-100-pan100', 'frames-100-zoom', 'frames-150-panFit', 'frames-150-pan100', 'frames-150-zoom',
    'ncycles', 'run', 'setup-exe', 'installed', 'runtime']);
  assert.deepEqual(rows.filter((x) => x.status !== 'met').map((x) => [x.id, x.status]), [['installed', 'report'], ['runtime', 'report']]);
  assert.deepEqual(failed(rows), []);
  assert.equal(row(rows, 'start').value, '900 ms (window 600 ms, first screen 890 ms)', 'the first start is the cold one');
  assert.equal(row(rows, 'start-warm').value, '850 ms', 'the median of the others');
  assert.match(row(rows, 'open').value, /^750 ms \(all: 700, 750, 900\)$/);
  assert.match(row(rows, 'run').what, /Run at 4 kHz, 3 s \(500 cycles\/s\)/);
});

test('over the goal but within the CI limit: missed (CI goes on); over the limit: failed', () => {
  const missed = judge(all({ starts: [start(4500)], openRefMipsMs: [2500], nCycles1000Ms: V1_NCYCLES_1000_MS }));
  assert.equal(row(missed, 'start').status, 'missed');
  assert.equal(row(missed, 'open').status, 'missed');
  assert.equal(row(missed, 'ncycles').status, 'met', 'as fast as v1 is not slower');
  assert.deepEqual(failed(missed), []);
  const bad = judge(all({ starts: [start(GOALS.start.limitMs + 1)], openRefMipsMs: [GOALS.open.limitMs + 1], nCycles1000Ms: V1_NCYCLES_1000_MS + 1,
    sizes: { setupExeBytes: GOALS.setupExe.limitBytes + 1 } }));
  assert.deepEqual(failed(bad).map((x) => x.id), ['start', 'open', 'ncycles', 'setup-exe']);
});

test('frames: 60 fps is met; late frames within the limit are missed; a slow mean fails -- on Linux', () => {
  const late = frameStats([...Array(90).fill(FRAME_MS), ...Array(30).fill(33.3)]);
  const slow = frameStats(Array(60).fill(33.3));
  const rows = judge(all({ frames: { '100 %': { panFit: late, pan100: slow, zoom: smooth } } }));
  assert.equal(row(rows, 'frames-100-panFit').status, 'missed');
  assert.equal(row(rows, 'frames-100-pan100').status, 'failed');
  assert.equal(row(rows, 'frames-100-zoom').status, 'met');
  assert.equal(row(rows, 'frames-150-zoom').status, 'skipped', 'no 150 % measured');
});

test('Run: a long frame or many dropped frames miss the goal; beyond the limit fails', () => {
  const stutter = frameStats([...Array(170).fill(FRAME_MS), 60]);
  assert.equal(row(judge(all({ run: { hz: 4096, seconds: 3, cycles: 1, frames: stutter } })), 'run').status, 'missed');
  const dropping = frameStats(Array(100).fill(33.4));
  const r = row(judge(all({ run: { hz: 4096, seconds: 3, cycles: 1, frames: dropping } })), 'run');
  assert.equal(r.status, 'missed');
  assert.match(r.value, /100 of 200 frames dropped \(50\.0 %\)/);
  const frozen = frameStats([...Array(100).fill(FRAME_MS), GOALS.run.limitMaxMs + 1]);
  assert.equal(row(judge(all({ run: { hz: 64, seconds: 3, cycles: 1, frames: frozen } })), 'run').status, 'failed');
});

test('Windows: frames and Run are report-only, with the reason; the rest is judged', () => {
  const slow = frameStats(Array(60).fill(40));
  for (const where of ['windows', 'windows-installed'] as const) {
    const rows = judge(all({ where, frames: { '100 %': frames(slow), '150 %': frames(slow) }, run: { hz: 4096, seconds: 3, cycles: 1, frames: slow },
      starts: [start(GOALS.start.limitMs + 1)] }));
    for (const x of rows.filter((y) => y.id.startsWith('frames-') || y.id === 'run')) {
      assert.equal(x.status, 'report', x.id);
      assert.match(x.note!, /^report-only: .*WARP.* \(over the goal\)$/);
    }
    assert.equal(row(rows, 'start').status, 'failed', 'the start is judged on Windows too');
  }
  assert.equal(REPORT_ONLY.linux, undefined, 'Linux CI judges everything');
});

test('the first start after the install (the Windows job) is the one judged; this tool\'s starts are the warm ones', () => {
  const rows = judge(all({ where: 'windows-installed', firstAfterInstall: start(2200), starts: [start(600), start(700)] }));
  assert.match(row(rows, 'start').what, /after install/);
  assert.match(row(rows, 'start').value, /^2,200 ms/);
  assert.equal(row(rows, 'start-warm').value, '700 ms', 'median of both of this tool\'s');
});

test('skipped: with the reason, never met; no measurement at all fails nothing', () => {
  const rows = judge({ where: 'linux', platform: 'linux-x64', skipped: { start: 'no real engine', frames: 'no real engine', 'setup-exe': 'no setup exe here' } });
  assert.ok(rows.every((x) => x.status === 'skipped'), JSON.stringify(rows.map((x) => [x.id, x.status])));
  assert.equal(row(rows, 'start').note, 'no real engine');
  assert.equal(row(rows, 'frames-150-zoom').note, 'no real engine');
  assert.equal(row(rows, 'ncycles').note, 'not measured');
  assert.equal(row(rows, 'setup-exe').note, 'no setup exe here');
  assert.deepEqual(failed(rows), []);
});

test('markdown: a table the job summary shows (pipes escaped)', () => {
  const md = markdown([{ id: 'x', what: 'a | b', value: '1 ms', goal: '≤ 2 ms', status: 'met' }], 'Performance');
  assert.equal(md, '### Performance\n\n| | measured | goal | | note |\n|---|---|---|---|---|\n| a \\| b | 1 ms | ≤ 2 ms | met |  |\n');
});

test('the install checks\' reports: report/install.json and report/launch.json', () => {
  assert.deepEqual(readInstall({ setupExeBytes: 123780808, installedBytes: 454355393, installedFiles: 179 }), { setupExeBytes: 123780808, installedBytes: 454355393 });
  assert.deepEqual(readInstall({ setupExeBytes: 'x' }), { setupExeBytes: undefined, installedBytes: undefined });
  assert.deepEqual(readInstall(null), { setupExeBytes: undefined, installedBytes: undefined });
  assert.deepEqual(readLaunch({ first: { windowMs: 2090, startScreenMs: 2186, engineReadyMs: 2198 }, second: {} }), { windowMs: 2090, startScreenMs: 2186, engineReadyMs: 2198 });
  assert.equal(readLaunch({ first: { windowMs: 1 } }), undefined);
  assert.equal(readLaunch({}), undefined);
});
