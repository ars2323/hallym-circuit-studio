/* src/renderer/app/logic/sim.ts (N-07, D-145): the status bar's facts, the
   band while the simulation is off, Run/Stop, Reset after an oscillation,
   the N Cycles count. */

import assert from 'node:assert/strict';
import { test } from 'node:test';

import type { SimState } from '../../src/main/protocol.ts';
import { CYCLES_DEFAULT, CYCLES_MAX, FREQUENCIES, going, parseCycles, resetTurnsOn, runLabel, simBand, simFacts, speedName } from '../../src/renderer/app/logic/sim.ts';

const st = (over: Partial<SimState> = {}): SimState => ({ fileId: 'f1', running: true, ticking: false, cycle: 0, oscillating: false, hz: 1, ...over });
const texts = (s: SimState) => simFacts(s).map((f) => f.text);

test('simFacts: Simulation On/Off, Cycle N, the clock -- English facts', () => {
  assert.deepEqual(texts(st()), ['Simulation On', 'Cycle 0', '1 Hz']);
  assert.deepEqual(texts(st({ cycle: 1234, hz: 64, ticking: true })), ['Simulation On', 'Cycle 1,234', 'Running (64 Hz)']);
  assert.deepEqual(texts(st({ hz: 1024 })), ['Simulation On', 'Cycle 0', '1 kHz']);
  assert.deepEqual(texts(st({ cyclesLeft: 340, cycle: 660 })), ['Simulation On', 'Cycle 660', 'N Cycles · 340 left']);
  assert.deepEqual(texts(st({ running: false, oscillating: true })), ['Simulation Off', 'Cycle 0', '1 Hz']);
  assert.equal(simFacts(st({ running: false })).find((f) => f.text === 'Simulation Off')?.cls, 'err');
  assert.equal(simFacts(st({ ticking: true })).find((f) => f.text.startsWith('Running'))?.cls, 'run');
  assert.deepEqual(simFacts(null), []);
  assert.deepEqual(simFacts(st(), { cycle: false }).map((f) => f.text), ['Simulation On', '1 Hz']);
  for (const f of simFacts(st({ running: false, ticking: true, cyclesLeft: 2 }))) assert.match(f.text, /^[A-Za-z0-9 ·(),]+$/, 'facts are English');
});

test('speeds: v1\'s seven, and a speed the list does not have', () => {
  assert.deepEqual(FREQUENCIES.map(([, hz]) => hz), [1, 4, 16, 64, 256, 1024, 4096]);
  assert.equal(speedName(4096), '4 kHz');
  assert.equal(speedName(2), '2 Hz');
  assert.equal(speedName(undefined), null);
});

test('Run is Stop while the clock ticks or N Cycles goes', () => {
  assert.equal(runLabel(st()), 'Run');
  assert.equal(runLabel(st({ ticking: true })), 'Stop');
  assert.equal(runLabel(st({ cyclesLeft: 1 })), 'Stop');
  assert.equal(runLabel(null), 'Run');
  assert.equal(going(st({ cyclesLeft: 0, ticking: false })), false);
});

test('simBand: a sentence only while off, the oscillation said as such (Korean, no particle after a key)', () => {
  assert.equal(simBand(st()), null);
  assert.equal(simBand(null), null);
  const osc = simBand(st({ running: false, oscillating: true }))!;
  assert.match(osc, /^발진으로 시뮬레이션이 꺼졌습니다/);
  assert.match(osc, /Reset을 누르면 다시 켜집니다/);
  const off = simBand(st({ running: false }))!;
  assert.match(off, /^시뮬레이션이 꺼져 있어 값이 바뀌지 않습니다/);
  assert.match(off, /Ctrl\+E 키로/, 'a key is followed by a noun, not a particle (GLOSSARY 3)');
});

test('resetTurnsOn: only after an oscillation', () => {
  assert.equal(resetTurnsOn(st({ running: false, oscillating: true })), true);
  assert.equal(resetTurnsOn(st({ running: false, oscillating: false })), false);
  assert.equal(resetTurnsOn(st()), false);
  assert.equal(resetTurnsOn(undefined), false);
});

test('parseCycles: 1 to 100000, spaces and separators ignored, anything else refused', () => {
  assert.deepEqual(parseCycles('10'), { n: 10 });
  assert.deepEqual(parseCycles(' 1,000 '), { n: 1000 });
  assert.deepEqual(parseCycles('100_000'), { n: CYCLES_MAX });
  assert.equal(CYCLES_DEFAULT, 10);
  for (const bad of ['', '0', '100001', '-3', '1.5', '1e3', 'ten', '0x10']) assert.ok('error' in parseCycles(bad), bad);
});
