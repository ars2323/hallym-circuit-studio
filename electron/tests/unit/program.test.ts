/* The program in the circuit (N-16, D-147): src/renderer/app/logic/program.ts
   (the summary rows, the status bar's facts, the band while a reload has
   failed), src/renderer/app/logic/console.ts (the Console tab's text) and
   src/main/program-path.ts (where Load Program's dialog opens).  The values
   are the real engine's, from tests/fixtures/programs.json. */

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { test } from 'node:test';

import { fileName, IMAGE_FILTER, imageName, programDialogPath } from '../../src/main/program-path.ts';
import type { LoadSummary, MipsFacts, ProgramInfo } from '../../src/main/protocol.ts';
import { applyConsole, consoleEmpty, consoleText } from '../../src/renderer/app/logic/console.ts';
import { chooseSentence, clock, keptBand, MAX_PROBLEMS, problemLines, programFacts, summaryFacts, summaryRows } from '../../src/renderer/app/logic/program.ts';

interface Fixture { file: string; load: { loaded: boolean; summary?: LoadSummary; problems?: { line: number; text: { en: string; ko: string } }[] }; facts?: unknown[]; program?: ProgramInfo }
const FIXTURES = Object.values((JSON.parse(readFileSync(path.join(import.meta.dirname, '../fixtures/programs.json'), 'utf8')) as
  { programs: Record<string, Fixture> }).programs);
const fixture = (file: string): Fixture => FIXTURES.find((f) => f.file === file)!;

// ---- the summary -------------------------------------------------------------------

test('summaryRows: entry, each segment and where it went, reg start values, the source check, the maker, the instructions', () => {
  const rows = summaryRows(fixture('data.hmx').load.summary!);
  assert.deepEqual(rows.map((r) => r.name), ['Entry', '.text', '.data', 'reg', 'Source', 'Made by', 'Instructions']);
  assert.equal(rows[0].value, '0x00400024 (main)');
  assert.deepEqual([rows[1].value, rows[1].note], ['27 words (0x00400000–0x00400068)', 'main › Instruction Memory (00400000-004fffff)']);
  assert.equal(rows[2].value, '28 bytes = 7 words (0x10010000–0x1001001b)');
  assert.equal(rows[3].value, '$sp 0x7fffffe4 · $gp 0x10008000');
  assert.equal(rows[3].note!, '파일에 적힌 시작 값입니다. 도구는 레지스터에 넣지 않습니다. 스택 깊이 기준: `stack 7ffc0000-7fffffff` (`main › Data Memory`)');
  assert.deepEqual([rows[4].value, rows[4].warn], ['원본 파일 data.s: 내보낸 때와 같음.', false]);
  assert.equal(rows[5].value, 'Hallym MIPS 2.4.0 · 2026-09-27T19:05+09:00');
  assert.match(rows[6].value, /^add, addi, .*syscall$/);
  assert.deepEqual(summaryFacts(fixture('data.hmx').load.summary!), []);
});

test('summaryRows: no .data empties the Data Memory; no handler is a fact; a changed source is the yellow row', () => {
  const nh = fixture('no-handler.hmx').load.summary!;
  const rows = summaryRows(nh);
  assert.equal(rows[0].value, '0x00400000 (__start)');
  const data = rows.find((r) => r.name === '.data')!;
  assert.deepEqual([data.value, data.note], ['none', '비움: main › Data Memory (10000000-100fffff)']);
  assert.deepEqual(summaryFacts(nh), ['예외 처리기 없이 어셈블한 이미지: 시작 코드 없음, 진입점 = 프로그램의 __start.']);
  const changed = summaryRows(fixture('source-changed.hmx').load.summary!).find((r) => r.name === 'Source')!;
  assert.equal(changed.warn, true);
  assert.match(changed.value, /내보낸 뒤 바뀜\. Hallym MIPS에서 다시 내보내세요\.$/);
  const noEntry = summaryRows({ ...nh, entry: null, entryLine: 'no entry' });
  assert.equal(noEntry[0].value, 'no entry');
});

test('the words keep the rule: no particle right after a name, no "하면 됩니다"', () => {
  const texts = [
    ...summaryRows(fixture('data.hmx').load.summary!).flatMap((r) => [r.value, r.note ?? '']),
    chooseSentence('text', '.text 0x00400000–0x00400068'), chooseSentence('data', '.data 0x10010000–0x1001001b'),
    keptBand({ ...fixture('data.hmx').program!, failure: failure(1000) })!.text,
  ];
  for (const t of texts) {
    assert.doesNotMatch(t, /하면 됩니다/);
    // A name (Latin letters, digits, a closing bracket) followed at once by a Korean particle.
    assert.doesNotMatch(t, /[A-Za-z0-9)\]`](은|는|이|가|을|를|의|에|로|으로|와|과)(\s|$)/, t);
  }
  assert.equal(chooseSentence('data', '.data 0x10010000'), '`.data 0x10010000` 구간을 담을 수 있는 Data Memory 부품이 여럿입니다. 넣을 부품을 고르세요.');
});

// ---- problems and the band -------------------------------------------------------------

const failure = (loadedAt: number | null, n = 1) => ({
  at: 5000, file: 'data.hmx', source: 'prog/data.hmx', reason: 'changed' as const, kept: { loadedAt },
  problems: Array.from({ length: n }, (_, i) => fixture('truncated.hmx').load.problems!.map((p) => ({ ...p, line: p.line + i }))[0]),
});

test('problemLines: the Korean lines, at most twelve and then how many more', () => {
  const p = fixture('truncated.hmx').load.problems!;
  assert.deepEqual(problemLines(p), ['8번째 줄: .text 줄에는 워드 14개라고 적혀 있지만 실제로는 13개입니다. 파일이 잘렸을 수 있습니다. Hallym MIPS에서 다시 내보내세요.']);
  const many = Array.from({ length: 15 }, (_, i) => ({ line: i + 1, text: { en: `e${i}`, ko: `k${i}` } }));
  const lines = problemLines(many);
  assert.equal(lines.length, MAX_PROBLEMS + 1);
  assert.equal(lines[MAX_PROBLEMS], '… 그 밖에 3개');
  assert.equal(problemLines(many, 15).length, 15);
});

test('keptBand: nothing without a failure; the last load\'s time and the first problem; the saved program', () => {
  const p = fixture('data.hmx').program!;
  assert.equal(keptBand(null), null);
  assert.equal(keptBand({ ...p, failure: null }), null);
  const at = Date.parse('2026-09-28T13:47:44+09:00');
  const b = keptBand({ ...p, failure: failure(at) })!;
  assert.equal(b.text, `지금 올라가 있는 것은 마지막으로 불러온 실행 이미지입니다 (${clock(at)}) · 다시 불러오지 못했습니다: data.hmx — 8번째 줄: .text 줄에는 워드 14개라고 적혀 있지만 실제로는 13개입니다. 파일이 잘렸을 수 있습니다. Hallym MIPS에서 다시 내보내세요.`);
  assert.match(b.title, /\n8번째 줄: /);
  const saved = keptBand({ ...p, failure: failure(null, 3) })!;
  assert.match(saved.text, /^지금 올라가 있는 것은 이 파일에 저장된 실행 이미지입니다 · 다시 불러오지 못했습니다: data\.hmx — 8번째 줄: .* \(그 밖에 2개\)$/);
  assert.equal(saved.title.split('\n').length, 4);
});

test('clock: hours, minutes and seconds, 24-hour', () => {
  assert.match(clock(Date.now()), /^\d\d:\d\d:\d\d$/);
});

// ---- the status bar ---------------------------------------------------------------------

test('programFacts: Program x.hmx, PC ≠ entry, a .s path with its button, an old Stack', () => {
  const later = fixture('main-later.hmx');
  const m: MipsFacts = { fileId: 'f1', facts: later.facts as MipsFacts['facts'], program: { ...later.program!, failure: null } };
  const f = programFacts(m);
  assert.deepEqual(f.map((x) => x.text), ['Program main-later.hmx', 'PC 0x00400024 · 실행 이미지 진입점 0x0040002c']);
  assert.equal(f[0].program, 'main-later.hmx');
  assert.equal(f[1].cls, 'warn');
  const old = programFacts({ fileId: 'f1', program: null, facts: [
    { id: 'assemblySource', en: 'e', ko: '이 파일은 .s 파일을 가리킵니다.', components: ['k1'], sources: ['prog/sum.s'] },
    { id: 'separateStack', en: 'e', ko: '이 회로는 따로 된 Stack 부품을 씁니다.', components: ['k2'] },
  ] });
  assert.equal(old[0].loadHmx, 'prog/sum.s');
  assert.match(old[0].title!, /\nProgram: prog\/sum\.s$/);
  assert.deepEqual([old[1].cls, old[1].loadHmx], ['', undefined]);
  assert.deepEqual(programFacts(null), []);
});

// ---- the Console tab ------------------------------------------------------------------------

test('applyConsole: text replaces, append adds, a Console not in the update is gone', () => {
  let s = applyConsole([], [{ name: 'Console', text: 'sum', exited: false }]);
  s = applyConsole(s, [{ name: 'Console', append: ' = 14', exited: true }]);
  assert.deepEqual(s, [{ name: 'Console', text: 'sum = 14', exited: true }]);
  assert.deepEqual(applyConsole(s, [{ name: 'Console', text: '', exited: false }]), [{ name: 'Console', text: '', exited: false }], 'Reset');
  assert.deepEqual(applyConsole(s, []), []);
  assert.deepEqual(applyConsole([], [{ name: 'io › Console', append: 'x', exited: false }]), [{ name: 'io › Console', text: 'x', exited: false }]);
});

test('consoleText: v1\'s tab -- a name line between Consoles, -- exit -- on its own line', () => {
  assert.equal(consoleText([{ name: 'Console', text: 'sum = 14', exited: true }]), 'sum = 14\n-- exit --\n');
  assert.equal(consoleText([{ name: 'Console', text: 'a\n', exited: true }]), 'a\n-- exit --\n');
  assert.equal(consoleText([{ name: 'Console', text: 'hi', exited: false }]), 'hi');
  assert.equal(consoleText([{ name: 'A', text: 'x', exited: false }, { name: 'B', text: '', exited: true }]), '── A ──\nx\n── B ──\n-- exit --\n');
  assert.equal(consoleText([{ name: 'Console', text: '', exited: true }]), '-- exit --\n');
  assert.equal(consoleEmpty([]), true);
  assert.equal(consoleEmpty([{ name: 'Console', text: '', exited: false }]), true);
  assert.equal(consoleEmpty([{ name: 'Console', text: '', exited: true }]), false);
});

// ---- Load Program's dialog ------------------------------------------------------------------

test('programDialogPath: next to the .circ; for an old .s, its folder and the .hmx of the same name', () => {
  const circ = path.join('/lab', 'week4', 'cpu.circ');
  assert.equal(programDialogPath(circ, null), path.join('/lab', 'week4'));
  assert.equal(programDialogPath(null, null), undefined);
  assert.equal(programDialogPath(circ, 'prog/sum.s'), path.join('/lab', 'week4', 'prog', 'sum.hmx'));
  assert.equal(programDialogPath(circ, 'prog\\SUM.ASM'), path.join('/lab', 'week4', 'prog', 'SUM.hmx'));
  assert.equal(programDialogPath(circ, '/abs/lab04.s'), path.join('/abs', 'lab04.hmx'));
  assert.equal(programDialogPath(null, 'prog/sum.s'), undefined, 'a relative .s with no .circ folder');
  assert.equal(programDialogPath(circ, '   '), path.join('/lab', 'week4'));
  assert.deepEqual([fileName('a\\b/c.s'), imageName('lab/sum.s'), imageName('noext')], ['c.s', 'sum.hmx', 'noext.hmx']);
  assert.deepEqual(IMAGE_FILTER.extensions, ['hmx'], 'executable images only (D-141)');
});
