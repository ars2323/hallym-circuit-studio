/* What the window says about the program in the circuit (N-16, D-147):
   the summary after Load Program, the status bar's facts, the band while a
   reload has failed.  Logic only; the engine's values as they came
   (docs/engine-api.md "mips").

   Names are English (Program, Entry, .text, reg, Load .hmx…); a sentence to
   the student is Korean, the engine's `ko` where it gives one, and never a
   particle right after a name (a file's name stands after a colon).  The
   tool writes no register and judges no working circuit: PC ≠ entry is a
   fact in the status bar, not a message (D-138). */

import type { LoadProblem, LoadSummary, MipsFacts, ProgramInfo } from '../../../main/protocol.ts';

// A time as Hallym MIPS's Assemble panel and band say it: 13:47:44.
export const clock = (ms: number): string =>
  new Date(ms).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false });

// At most this many problems in a dialog (track A's LoadProgramMenu.MAX_ERRORS), then how many more.
export const MAX_PROBLEMS = 12;

export function problemLines(problems: LoadProblem[], max = MAX_PROBLEMS): string[] {
  const out = problems.slice(0, max).map((p) => p.text.ko);
  if (problems.length > max) out.push(`… 그 밖에 ${problems.length - max}개`);
  return out;
}

// The band while a reload has failed (Hallym MIPS's error-kept band): what is
// on show, since when, and why the file was not loaded again.  null: no band.
export function keptBand(p: ProgramInfo | null | undefined): { text: string; title: string } | null {
  const f = p?.failure;
  if (!f) return null;
  const at = f.kept.loadedAt;
  const kept = at === null || at === undefined
    ? '지금 올라가 있는 것은 이 파일에 저장된 실행 이미지입니다'
    : `지금 올라가 있는 것은 마지막으로 불러온 실행 이미지입니다 (${clock(at)})`;
  const first = f.problems[0]?.text.ko ?? '';
  const more = f.problems.length > 1 ? ` (그 밖에 ${f.problems.length - 1}개)` : '';
  const text = `${kept} · 다시 불러오지 못했습니다: ${f.file} — ${first}${more}`;
  return { text, title: [`${kept} · 다시 불러오지 못했습니다: ${f.file}`, ...problemLines(f.problems)].join('\n') };
}

// One fact in the status bar.  `loadHmx`: a button that loads an .hmx for the old .s path.
export interface StatusFact {
  cls: '' | 'warn';
  text: string;
  title?: string;
  program?: string;           // the program's file name (the fact is "Program <name>")
  loadHmx?: string;           // the .s path to open next to
}

// The status bar's facts about the program: its name, PC ≠ entry at cycle 0,
// an old separate Stack, a memory that still points to a .s (D-140, D-141).
export function programFacts(m: MipsFacts | null | undefined): StatusFact[] {
  if (!m) return [];
  const out: StatusFact[] = [];
  if (m.program) out.push({ cls: '', text: `Program ${m.program.name}`, title: m.program.source ?? m.program.name, program: m.program.name });
  for (const f of m.facts) {
    if (f.id === 'pcEntry') out.push({ cls: 'warn', text: f.ko, title: f.ko });
    else if (f.id === 'assemblySource') out.push({ cls: 'warn', text: f.ko, title: [f.ko, ...(f.sources ?? []).map((s) => `Program: ${s}`)].join('\n'), loadHmx: f.sources?.[0] ?? '' });
    else out.push({ cls: '', text: f.ko, title: f.ko });
  }
  return out;
}

// One row of the summary: a name (English), a value (mono), a note.
export interface SummaryRow {
  name: string;
  value: string;
  note?: string;
  warn?: boolean;
}

// The summary dialog's rows: entry, each segment and where it went, the
// start values the file gives, the source check, the maker, the instructions.
export function summaryRows(s: LoadSummary): SummaryRow[] {
  const rows: SummaryRow[] = [];
  rows.push({ name: 'Entry', value: s.entry === null ? 'no entry' : s.entryLine.replace(/^entry /, '') });
  for (const seg of s.segments) rows.push({ name: `.${seg.kind}`, value: seg.text, note: seg.target });
  if (!s.segments.some((x) => x.kind === 'data')) {
    rows.push({ name: '.data', value: 'none', note: s.emptied ? `비움: ${s.emptied.target}` : undefined });
  }
  if (s.regs.length) {
    const base = s.stackBase[0];
    rows.push({
      name: 'reg', value: s.regs.map((r) => `${r.name} ${r.value}`).join(' · '),
      note: `파일에 적힌 시작 값입니다. 도구는 레지스터에 넣지 않습니다.${base ? ` 스택 깊이 기준: \`stack ${base.stack}\` (\`${base.part}\`)` : ''}`,
    });
  }
  if (s.source) rows.push({ name: 'Source', value: s.source.text.ko, warn: s.source.warn });
  if (s.producedBy || s.assembled) rows.push({ name: 'Made by', value: [s.producedBy, s.assembled].filter(Boolean).join(' · ') });
  rows.push({ name: 'Instructions', value: s.instructions.join(', ') });
  return rows;
}

// The summary's fact lines under the rows (no handler, jr $ra in the entry routine).
export const summaryFacts = (s: LoadSummary): string[] => s.facts.map((f) => f.text.ko);

// The chooser's sentence: which segment, what kind of memory.
export function chooseSentence(kind: 'text' | 'data', segment: string): string {
  const what = kind === 'text' ? 'Instruction Memory' : 'Data Memory';
  return `\`${segment}\` 구간을 담을 수 있는 ${what} 부품이 여럿입니다. 넣을 부품을 고르세요.`;
}
