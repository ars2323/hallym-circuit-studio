/* The Cycle View's model (logic only; ../cycleview.ts puts it on screen).
   The engine keeps the recording (docs/engine-api.md record.*, D-073,
   D-144); this file decides which cycles the table shows, what a cell
   says, the status bar's cycle facts and Run Until's words.

   A column is a cycle: its values are those at the cycle's end (the status
   bar's "Cycle c"), a 1-bit row's wave has two halves, after the rising
   edge and before the next one (D-074).  Nothing here judges a value. */

import type { RecordState, RunUntilDone, RunUntilKind } from '../../../main/protocol.ts';
import { hex } from './values.ts';

// Width of a cycle column and the least and most of the name column (CSS px; D-113).
export const COLUMN_PX = 124;
export const NAME_LEAST = 96;
export const NAME_MOST = 200;

// The name column: as wide as the longest name needs (7 px a character, 30 of margin), within limits.
export function nameWidth(names: string[]): number {
  const longest = names.reduce((m, n) => Math.max(m, n.length), 4);
  return Math.max(NAME_LEAST, Math.min(NAME_MOST, longest * 7 + 30));
}

// How many cycle columns fit in `width` px beside the name column (at least one).
export function columnsThatFit(width: number, nameCol: number): number {
  return Math.max(1, Math.floor((width - nameCol - 2) / COLUMN_PX));
}

export interface Window { from: number; to: number }

/* The cycles to show, `count` of them: the latest ones while the cycle on
   show is the latest (the table follows the clock); otherwise the window
   that was shown, if the cycle on show is still in it, else one with that
   cycle in the middle.  Always within [first, last]. */
export function columnWindow(first: number, last: number, cycle: number, count: number, before: Window | null): Window {
  const n = Math.max(1, count);
  const clamp = (from: number): Window => {
    const f = Math.max(first, Math.min(from, last - n + 1));
    return { from: f, to: Math.min(last, f + n - 1) };
  };
  if (last < first) return { from: first, to: first - 1 };
  if (cycle >= last) return clamp(last - n + 1);
  if (before && cycle >= before.from && cycle <= before.from + n - 1 && before.from >= first) return clamp(before.from);
  return clamp(cycle - Math.floor(n / 2));
}

// A bus cell's words: hex, the original's x and E digits for a partly defined one; '' not recorded.
export function cellText(value: string | null | undefined, width: number): string {
  if (value === null || value === undefined) return '';
  if (width === 1) return value;
  return hex(value);
}

// The same value as in the column before (shown quieter so that a change stands out).
export const sameAsBefore = (values: (string | null)[], i: number): boolean =>
  i > 0 && values[i] !== null && values[i] === values[i - 1];

// One bit of a bus row shown bit by bit: bit `bit` (0 = lowest) of each value.
export function bitOf(value: string | null | undefined, bit: number): string | null {
  if (value === null || value === undefined) return null;
  const i = value.length - 1 - bit;
  return i >= 0 ? value[i] : null;
}

/* A 1-bit row's wave in one column: the level in each half (1 high, 0 low,
   anything else a band), and whether the level changes at the column's
   start (after the previous column's second half) and in its middle. */
export interface Wave { a: string; b: string; edgeIn: boolean; edgeMid: boolean }
export function wave(halves: (string | null)[], values: (string | null)[], i: number): Wave {
  const a = halves[i] ?? '';
  const b = values[i] ?? '';
  const prev = i > 0 ? values[i - 1] ?? '' : a;
  return { a, b, edgeIn: i > 0 && prev !== a, edgeMid: a !== b };
}

// The status bar's cycle facts, in English (D-135): "Cycle 12", or "Cycle 5 / 12" on a past cycle; "PC 0x00400030".
export function cycleFacts(s: RecordState | null, simCycle: number | null): { cycle: string | null; pc: string | null; past: boolean } {
  if (!s || s.empty) return { cycle: simCycle === null ? null : `Cycle ${simCycle.toLocaleString('en-US')}`, pc: null, past: false };
  const c = s.cycle.toLocaleString('en-US');
  return {
    cycle: s.past ? `Cycle ${c} / ${s.last.toLocaleString('en-US')}` : `Cycle ${c}`,
    pc: s.pc ? `PC ${s.pc}` : null,
    past: s.past,
  };
}

// ---- Run Until (C-04, D-075) --------------------------------------------------

export const UNTIL_KINDS: { kind: RunUntilKind; label: string; help: string }[] = [
  { kind: 'pc', label: 'PC Is', help: '16진 주소나 실행 이미지의 라벨을 적습니다. 예: `0x00400034`, `fact`' },
  { kind: 'instruction', label: 'Next Instruction Is', help: '명령어 이름을 적습니다. 예: `beq`, `jal`, `syscall`' },
  { kind: 'row', label: 'Row Changes', help: '표의 줄 하나를 고릅니다. 그 줄의 값이 바뀌는 사이클에서 멈춥니다.' },
  { kind: 'errorOrX', label: 'E or X Appears', help: '선 하나라도 오류 값(E)이 되거나, 정해져 있던 선에 정해지지 않은 값(X)이 생기면 멈춥니다.' },
  { kind: 'halt', label: 'Halt or Exit', help: '`halt` 출력 핀이 1이 되거나 Console 부품이 끝나면(exit) 멈춥니다.' },
];
export const DEFAULT_MAX_CYCLES = 10_000;

export interface UntilForm { kind: RunUntilKind; value: string; row: string; max: string }

// What to send (record.runUntil), or why it cannot be sent yet (a sentence for the dialog).
export function untilRequest(f: UntilForm): { ok: true; params: { kind: RunUntilKind; value?: string; maxCycles: number } } | { ok: false; why: string } {
  const max = f.max.trim() === '' ? DEFAULT_MAX_CYCLES : Number(f.max.trim());
  if (!Number.isInteger(max) || max < 1 || max > 1_000_000) return { ok: false, why: '최대 사이클 수는 1에서 1,000,000 사이의 정수입니다.' };
  const v = f.value.trim();
  switch (f.kind) {
    case 'pc':
      if (v === '') return { ok: false, why: 'PC 값이나 라벨을 적어 주세요.' };
      if (!/^(0x)?[0-9a-f]{1,8}$/i.test(v) && !/^[A-Za-z_.$][\w.$]*$/.test(v)) return { ok: false, why: `PC 값을 읽을 수 없습니다: ${v}` };
      return { ok: true, params: { kind: 'pc', value: v, maxCycles: max } };
    case 'instruction':
      if (!/^[a-z][a-z0-9.]*$/i.test(v)) return { ok: false, why: '명령어 이름을 적어 주세요. 예: beq, jal, syscall' };
      return { ok: true, params: { kind: 'instruction', value: v.toLowerCase(), maxCycles: max } };
    case 'row':
      if (f.row === '') return { ok: false, why: '표에 줄이 없습니다. 선을 오른쪽 클릭하고 Add to Cycle View 메뉴로 먼저 줄을 더합니다.' };
      return { ok: true, params: { kind: 'row', value: f.row, maxCycles: max } };
    default:
      return { ok: true, params: { kind: f.kind, maxCycles: max } };
  }
}

// Why it stopped, in the student's words (v1's, D-075): "사이클 12에서 멈췄습니다: PC 0x00400038".
export function untilResult(d: RunUntilDone, rowName: (id: string) => string | undefined): string {
  switch (d.result) {
    case 'met': return `사이클 ${d.cycle}에서 멈췄습니다: ${untilWhy(d.kind, d.value, rowName)}`;
    case 'limit': return `${(d.cycle - d.from).toLocaleString('en-US')}사이클을 돌았지만 조건을 만나지 않았습니다.`;
    case 'off': return `시뮬레이션이 꺼져서 사이클 ${d.cycle}에서 멈췄습니다.`;
    default: return `사이클 ${d.cycle}에서 멈췄습니다.`;
  }
}

export function untilWhy(kind: RunUntilKind, value: string | undefined, rowName: (id: string) => string | undefined): string {
  switch (kind) {
    case 'pc': return `PC ${value ?? ''}`;
    case 'instruction': return `다음 명령어 ${value ?? ''}`;
    case 'row': return `${rowName(value ?? '') ?? value ?? ''} 값이 바뀜`;
    case 'errorOrX': return 'E 또는 X 값이 생김';
    default: return 'halt 또는 exit';
  }
}

// The message whose rows are pinned went from the Messages list (D-114): the rows go too.
export const pinGone = (pinned: string | undefined, ids: readonly string[]): boolean => pinned !== undefined && !ids.includes(pinned);
