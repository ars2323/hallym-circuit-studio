/* The fake engine's mips.* (docs/engine-api.md "mips", N-16, D-147): the
   real engine's answers for a few executable images, read from
   tests/fixtures/programs.json (written from the real engine by engine/
   ProgramFixtureTest, which checks on every CI run that it still says the
   same) and found by the SHA-256 of the file's bytes -- so a file exported
   again (its bytes changed) is another answer.  Parts in the fixture are
   names ("@Instruction Memory"); here they become this fake engine's ids.

     mips.load       the fixture's answer; several Instruction Memories and no
                     pick: "choose"; a .s: the fact and nothing loaded
     mips.facts      Program, PC ≠ entry at cycle 0 (the fixture's), a .s path
                     (assemblySource), an old Stack (separateStack), a failed reload
     mips.console    the fixture's output once the file has run cycles after a
                     load; Reset empties it
     mips.disasm     the fixture's lines
     watching        a loaded .hmx is looked at every 200 ms: another answer
                     from the fixture loads again (mips.reloaded ok), a file
                     with no loadable answer is a failure that keeps the program
                     on show (mips.reloaded, the failure in mips.facts)

   Times come from a made-up clock (13:47:44 KST, then a second per load) so
   every run gives the same times.  FAKE_ENGINE_NOW (ms) holds it still at
   that moment instead: the screenshots' fixed clock, 10:00:00
   (tests/e2e/screen-conditions.ts, D-167). */

import { createHash } from 'node:crypto';
import { readFileSync, statSync } from 'node:fs';
import path from 'node:path';

type Json = Record<string, unknown>;
export interface Comp { id: string; name: string; loc: [number, number]; attrs: Record<string, string> }
export interface Circ { circuitId: string; name: string; comps: Comp[] }
export interface MipsFile {
  fileId: string;
  path: string | null;
  circuits: Circ[];
  main: string;
  cycle: number;
  mips: MipsState;
}

interface Fixture {
  file: string;
  load: Json & { loaded: boolean; problems?: unknown[] };
  facts?: Json[];
  program?: Json;
  disasm?: Json;
  cycles?: number;
  console?: { consoles: { name: string; text: string; exited: boolean }[] };
}
interface Program { file: string; path: string; source: string; fixture: Fixture; loadedAt: number }
export interface MipsState {
  program: Program | null;
  failure: Json | null;
  stamp: string;
  timer: NodeJS.Timeout | null;
  console: { text: string; exited: boolean };
}

const FIXTURES: Record<string, Fixture> = (() => {
  try {
    return (JSON.parse(readFileSync(path.join(import.meta.dirname, '../fixtures/programs.json'), 'utf8')) as { programs: Record<string, Fixture> }).programs;
  } catch { return {}; }
})();

const FACT_S = {
  en: 'This file points to a .s file. Load the file exported with Export executable image (.hmx) in Hallym MIPS.',
  ko: '이 파일은 .s 파일을 가리킵니다. Hallym MIPS에서 Export executable image (.hmx) 단추로 내보낸 파일을 불러오세요.',
};
const STACK = { en: 'This circuit uses a separate Stack part. New Data Memory parts also hold the stack region.', ko: '이 회로는 따로 된 Stack 부품을 씁니다. 새 Data Memory는 스택 영역을 함께 맡습니다.' };

let tick = 0;
const HELD = process.env.FAKE_ENGINE_NOW ? Number(process.env.FAKE_ENGINE_NOW) : null;
const BASE = Date.parse('2026-09-28T13:47:44+09:00');
export const fakeClock = (held: number | null = HELD) => (held !== null && Number.isFinite(held) ? held : BASE + 1000 * tick++);
const clock = () => fakeClock();

export const newState = (): MipsState => ({ program: null, failure: null, stamp: '', timer: null, console: { text: '', exited: false } });

const comps = (f: MipsFile, name: string) => f.circuits.flatMap((c) => c.comps.filter((k) => k.name === name).map((k) => ({ c, k })));
const hasConsole = (f: MipsFile) => comps(f, 'Console').length > 0;

// The fixture's names as this file's ids: @main -> the main circuit, @Instruction Memory -> the first one.
function named(f: MipsFile, v: unknown, picks: Record<string, string> = {}): unknown {
  if (Array.isArray(v)) return v.map((x) => named(f, x, picks));
  if (v && typeof v === 'object') return Object.fromEntries(Object.entries(v as Json).map(([k, x]) => [k, named(f, x, picks)]));
  if (typeof v === 'string' && v.startsWith('@')) {
    if (v === '@main') return f.circuits.find((c) => c.name === f.main)?.circuitId ?? '';
    if (v === '@Instruction Memory' && picks.text) return picks.text;
    return comps(f, v.slice(1))[0]?.k.id ?? '';
  }
  return v;
}

function stampOf(p: string): string {
  try { const s = statSync(p); return `${s.mtimeMs} ${s.size}`; } catch { return 'missing'; }
}
const hashOf = (p: string): string | null => {
  try { return createHash('sha256').update(readFileSync(p)).digest('hex'); } catch { return null; }
};
const relative = (f: MipsFile, file: string) => (f.path ? path.relative(path.dirname(f.path), file).split(path.sep).join('/') : file);
const problem = (ko: string, en: string, line = 0) => ({ line, text: { en, ko } });

// mips.load
export function load(f: MipsFile, p: Json, notify: (m: string, x: unknown) => void): unknown {
  const file = path.resolve(String(p.path ?? ''));
  const name = path.basename(file);
  const out = { fileId: f.fileId, file: name };
  if (/\.(s|asm)\s*$/i.test(file)) return { ...out, loaded: false, problems: [{ line: 0, text: FACT_S }] };
  const hash = hashOf(file);
  const fx = hash ? FIXTURES[hash] : undefined;
  if (!fx) {
    return { ...out, loaded: false, problems: [problem(`파일을 읽을 수 없습니다. File: ${name}`, `Cannot read the file ${name}`)] };
  }
  if (!fx.load.loaded) return { ...out, loaded: false, problems: fx.load.problems };
  const ims = comps(f, 'Instruction Memory');
  const picks = (p.picks ?? {}) as Record<string, string>;
  if (ims.length === 0) {
    return { ...out, loaded: false, problems: [problem('.text 구간을 담는 Instruction Memory 부품이 없어 아무것도 불러오지 않았습니다. 회로에 Instruction Memory 부품이 없습니다.', 'No Instruction Memory covers .text, so nothing was loaded. The circuit has no Instruction Memory.')] };
  }
  const target = typeof p.target === 'string' ? p.target : undefined;
  if (ims.length > 1 && !target && !picks.text) {
    const seg = ((fx.load.summary as Json).segments as Json[])[0];
    return {
      ...out, loaded: false,
      choose: {
        kind: 'text', segment: `.text ${String(seg.range)}`,
        candidates: ims.map(({ c, k }) => ({ componentId: k.id, circuitId: c.circuitId, name: `${c.name} › ${k.attrs.label || 'Instruction Memory'} (00400000-004fffff)` })),
      },
    };
  }
  const chosen = { text: target ?? picks.text ?? ims[0].k.id };
  const st = f.mips;
  st.program = { file: name, path: file, source: relative(f, file), fixture: fx, loadedAt: clock() };
  st.failure = null;
  st.stamp = stampOf(file);
  watch(f, notify);
  f.cycle = 0;
  if (st.console.text || st.console.exited) {
    st.console = { text: '', exited: false };
    setImmediate(() => notify('mips.console', consoleOf(f)));
  }
  setImmediate(() => notify('mips.facts', facts(f)));
  return { ...(named(f, fx.load, chosen) as Json), fileId: f.fileId, source: st.program.source, loadedAt: st.program.loadedAt };
}

function watch(f: MipsFile, notify: (m: string, x: unknown) => void): void {
  const st = f.mips;
  if (st.timer) clearInterval(st.timer);
  st.timer = setInterval(() => {
    const prog = st.program;
    if (!prog) return;
    const now = stampOf(prog.path);
    if (now === st.stamp) return;
    st.stamp = now;
    const hash = hashOf(prog.path);
    const fx = hash ? FIXTURES[hash] : undefined;
    if (fx && fx.load.loaded) {
      st.program = { ...prog, fixture: fx, loadedAt: clock() };
      st.failure = null;
      f.cycle = 0;
      st.console = { text: '', exited: false };
      notify('mips.reloaded', { fileId: f.fileId, ok: true, reason: 'changed', file: prog.file, source: prog.source, loadedAt: st.program.loadedAt, summary: named(f, (fx.load as Json).summary) });
      notify('sim.state', { fileId: f.fileId, running: true, ticking: false, cycle: 0, oscillating: false, hz: 1 });
      notify('mips.console', consoleOf(f));
    } else {
      const problems = fx ? fx.load.problems : hash === null
        ? [problem('실행 이미지 파일이 그 자리에 없습니다. Hallym MIPS에서 다시 내보내거나 Load Program… 단추로 파일을 고르세요.', 'The executable image file is not there. Export it again from Hallym MIPS, or choose a file with the Load Program… button.')]
        : [problem('1번째 줄: 첫 줄이 HALLYM-EXEC 1 머리 줄이 아니라서 실행 이미지 파일이 아닙니다. Hallym MIPS에서 다시 내보내세요.', 'Line 1: The first line is not the HALLYM-EXEC 1 header, so this is not an executable image. Export it again from Hallym MIPS.', 1)];
      st.failure = { at: clock(), file: prog.file, source: prog.source, reason: 'changed', problems, kept: { loadedAt: prog.loadedAt } };
      notify('mips.reloaded', { fileId: f.fileId, ok: false, reason: 'changed', file: prog.file, source: prog.source, problems, kept: { loadedAt: prog.loadedAt } });
    }
    notify('mips.facts', facts(f));
  }, 200);
  st.timer.unref();
}

// mips.facts
export function facts(f: MipsFile): unknown {
  const st = f.mips;
  const list: unknown[] = [];
  const stacks = comps(f, 'Stack');
  if (stacks.length) list.push({ id: 'separateStack', ...STACK, components: stacks.map((x) => x.k.id) });
  if (!st.program) {
    const s = f.circuits.flatMap((c) => c.comps).filter((k) => /\.(s|asm)\s*$/i.test(k.attrs.source ?? ''));
    if (s.length) list.push({ id: 'assemblySource', ...FACT_S, components: s.map((k) => k.id), sources: s.map((k) => k.attrs.source) });
  }
  if (st.program && f.cycle === 0) list.push(...(named(f, st.program.fixture.facts ?? []) as unknown[]));
  let program: unknown = null;
  if (st.program) {
    program = { ...(named(f, st.program.fixture.program ?? {}) as Json), name: st.program.file, source: st.program.source, loadedAt: st.program.loadedAt, failure: st.failure };
  }
  return { fileId: f.fileId, facts: list, program };
}

// mips.console
export const consoleOf = (f: MipsFile) => ({
  fileId: f.fileId,
  consoles: hasConsole(f) ? [{ name: 'Console', text: f.mips.console.text, exited: f.mips.console.exited }] : [],
});

// sim.cycles: once the program has run, the fixture's output (the real engine's after the same program ran to exit).
export function cycles(f: MipsFile, notify: (m: string, x: unknown) => void): void {
  const out = f.mips.program?.fixture.console?.consoles[0];
  if (out && hasConsole(f) && (out.text !== f.mips.console.text || out.exited !== f.mips.console.exited)) {
    f.mips.console = { text: out.text, exited: out.exited };
    setImmediate(() => notify('mips.console', consoleOf(f)));
  }
  if (f.mips.program) setImmediate(() => notify('mips.facts', facts(f)));
}

// sim.reset: the Console is empty again; the facts at cycle 0.
export function reset(f: MipsFile, notify: (m: string, x: unknown) => void): void {
  if (f.mips.console.text || f.mips.console.exited) {
    f.mips.console = { text: '', exited: false };
    setImmediate(() => notify('mips.console', consoleOf(f)));
  }
  if (f.mips.program) setImmediate(() => notify('mips.facts', facts(f)));
}

export function disasm(f: MipsFile, p: Json): unknown {
  const d = f.mips.program?.fixture.disasm;
  return { ...(d ? named(f, d) as Json : { words: 0, first: null, last: null, entry: null, symbols: false, lines: [] }), fileId: f.fileId, componentId: p.componentId };
}

export function close(f: MipsFile): void {
  if (f.mips.timer) clearInterval(f.mips.timer);
  f.mips.timer = null;
}
