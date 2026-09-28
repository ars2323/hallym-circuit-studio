/* The simulation as the window says it (N-07, D-145; logic only): the
   status bar's facts -- names, in English (Simulation On, Cycle 12,
   Running (64 Hz), N Cycles · 340 left) -- the band's sentence while the
   simulation is off (Korean), the Run button's name, and the count the
   N Cycles dialog takes.

   Run is the clock (the engine's sim.run: Logisim's Ticks Enabled, as
   Hallym MIPS's Run runs the program); while N Cycles goes, Run shows Stop
   and stops it (the engine stops asking for ticks, D-145).  Simulation
   Enabled is Ctrl+E (sim.enable). */

import type { SimState } from '../../../main/protocol.ts';
import { count } from './facts.ts';

// The clock's speeds (v1's list; the engine's sim.run hz, docs/engine-api.md).
export const FREQUENCIES: readonly [string, number][] = [['1 Hz', 1], ['4 Hz', 4], ['16 Hz', 16], ['64 Hz', 64], ['256 Hz', 256], ['1 kHz', 1024], ['4 kHz', 4096]];

export const speedName = (hz: number | undefined): string | null => FREQUENCIES.find(([, f]) => f === hz)?.[0] ?? (hz ? `${hz} Hz` : null);

// The N Cycles dialog: v1's bounds and first value.
export const CYCLES_MIN = 1;
export const CYCLES_MAX = 100000;
export const CYCLES_DEFAULT = 10;

export interface SimFact { cls: '' | 'run' | 'err' | 'warn'; text: string }

// Something is going: the clock ticks, or N Cycles has cycles left.
export const going = (s: SimState | null | undefined): boolean => !!s && (s.ticking || (s.cyclesLeft ?? 0) > 0);

export const runLabel = (s: SimState | null | undefined): 'Run' | 'Stop' => (going(s) ? 'Stop' : 'Run');

// The status bar's facts for a file's simulation (null state: nothing yet).
export function simFacts(s: SimState | null | undefined, opts: { cycle?: boolean } = {}): SimFact[] {
  if (!s) return [];
  const out: SimFact[] = [];
  out.push(s.running ? { cls: '', text: 'Simulation On' } : { cls: 'err', text: 'Simulation Off' });
  if (opts.cycle !== false) out.push({ cls: '', text: `Cycle ${count(s.cycle)}` });
  const speed = speedName(s.hz);
  const left = s.cyclesLeft ?? 0;
  if (left > 0) out.push({ cls: 'run', text: `N Cycles · ${count(left)} left` });
  else if (s.ticking) out.push({ cls: 'run', text: speed ? `Running (${speed})` : 'Running' });
  else if (speed) out.push({ cls: '', text: speed });
  return out;
}

// The band over the work while the simulation is off (a sentence: Korean). Null while it is on.
export function simBand(s: SimState | null | undefined): string | null {
  if (!s || s.running) return null;
  if (s.oscillating) return '발진으로 시뮬레이션이 꺼졌습니다 · 값이 더 바뀌지 않습니다 · 회로를 고친 뒤 Reset을 누르면 다시 켜집니다';
  return '시뮬레이션이 꺼져 있어 값이 바뀌지 않습니다 · Ctrl+E 키로 다시 켭니다';
}

// The toolbar's Reset: after an oscillation it also turns the simulation on again (Messages'
// Reset Simulation does the same, D-143); switched off by Ctrl+E, it stays off (the student's choice).
export const resetTurnsOn = (s: SimState | null | undefined): boolean => !!s && !s.running && s.oscillating;

// The N Cycles dialog's text as a count: a whole number from 1 to 100000 (spaces and "," ignored), or why not.
export function parseCycles(text: string): { n: number } | { error: string } {
  const t = text.replace(/[\s,_]/g, '');
  if (!/^\d+$/.test(t)) return { error: '1부터 100000까지의 수를 적으세요' };
  const n = Number(t);
  if (n < CYCLES_MIN || n > CYCLES_MAX) return { error: '1부터 100000까지의 수를 적으세요' };
  return { n };
}
