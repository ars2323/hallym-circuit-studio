/* The rest of v1's commands (N-21, D-162; logic only, no page): Undo
   History (v1 UndoHistory, E-05), Project › Analyze Circuit and Get
   Circuit Statistics (the original's), File › Create Submission… (v1
   Submission, E-06), File › Export Image… (v1 ImageExport, E-07) and
   File › Print… (the original's Print, I-134).

   Names are English (Start of History, Now; the original's action names:
   Add AND Gate); sentences to the student Korean, with no particle right
   after a name (D-135 14): "Probe 부품 2개", "Messages 3건". */

import type { AnalyzeProblem, Analysis, History, HistoryRow, Snapshot, Statistics, SubmissionPlan } from '../../../main/protocol.ts';

// ---- Undo History (E-05) ----

export interface HistoryLine { kind: HistoryRow['kind']; text: string; moves: number; title: string }

/* v1's list: "Start of History" (everything undone), the actions that can be undone (oldest first:
   a row takes the file back to just after it), "▶ Now", the actions that can be done again (the
   next one first: a row does them again up to it). */
export function historyLines(h: History | null): HistoryLine[] {
  if (!h) return [];
  return h.rows.map((r) => {
    switch (r.kind) {
      case 'start': return { kind: r.kind, text: 'Start of History', moves: r.moves, title: r.moves < 0 ? '누르면 모두 되돌립니다.' : '되돌릴 것이 없습니다.' };
      case 'now': return { kind: r.kind, text: 'Now', moves: 0, title: '지금 상태입니다.' };
      case 'undo': return { kind: r.kind, text: r.name ?? '', moves: r.moves, title: r.moves === 0 ? '지금 상태를 만든 마지막 동작입니다.' : '누르면 이 동작을 마친 상태까지 되돌립니다.' };
      case 'redo': return { kind: r.kind, text: r.name ?? '', moves: r.moves, title: '누르면 이 동작까지 다시 실행합니다.' };
    }
  });
}

export const HISTORY_HINT = '줄을 누르면 그 동작을 마친 상태까지 되돌리거나 다시 실행합니다.';

// ---- Analyze Circuit ----

// The original's refusals and stops (ProjectCircuitActions.doAnalyze), as facts and what to do.
export function analyzeProblemText(a: Analysis): string | null {
  const p: AnalyzeProblem | undefined = a.problem;
  if (!p) return null;
  switch (p) {
    case 'multibitInput': return `여러 비트 입력 핀이 있어 분석하지 않았습니다(핀 이름: ${a.pin ?? ''}). 조합 분석은 1비트 핀만 다룹니다. Splitter 부품으로 나눈 1비트 핀을 두면 분석합니다.`;
    case 'multibitOutput': return `여러 비트 출력 핀이 있어 분석하지 않았습니다(핀 이름: ${a.pin ?? ''}). 조합 분석은 1비트 핀만 다룹니다.`;
    case 'tooManyInputs': return `입력 핀이 ${a.inputs.length}개입니다. 조합 분석은 입력 핀 ${a.maxInputs}개까지 다룹니다.`;
    case 'tooManyOutputs': return `출력 핀이 ${a.outputs.length}개입니다. 조합 분석은 출력 핀 ${a.maxOutputs}개까지 다룹니다.`;
    case 'noInputs': return '이 회로에는 입력 핀이 없습니다. 진리표를 만들려면 입력 핀이 하나 이상 있어야 합니다.';
    case 'noOutputs': return '이 회로에는 출력 핀이 없습니다. 진리표를 만들려면 출력 핀이 하나 이상 있어야 합니다.';
  }
}

// How the table came (the original: an expression when the circuit is gates only, else simulated).
export function analyzeSourceText(a: Analysis): string | null {
  if (a.source === 'table') return '원조의 식 계산이 다루지 않는 부품이 있어, 입력의 모든 조합을 시뮬레이션해 진리표를 만들었습니다. 식은 그 진리표에서 만든 것입니다.';
  return null;
}

// A table entry's meaning (the original shows !! for both errors, with the reason on hover).
export const ENTRY_TITLES: Record<string, string> = {
  x: '상관없음(don’t care)', E: '출력 값이 서로 부딪칩니다', '!!': '회로가 진동합니다',
};

// The original's expression text (~a b + a ~b) with the NOT as an overline for the screen: runs of
// letters, digits and _ after ~ are one variable.
export interface ExprPart { text: string; not: boolean }
export function expressionParts(expr: string): ExprPart[] {
  const out: ExprPart[] = [];
  const re = /~([A-Za-z_][A-Za-z0-9_]*(?:\[\d+\])?)|([^~]+)/g;
  for (let m; (m = re.exec(expr)) !== null;) {
    if (m[1] !== undefined) out.push({ text: m[1], not: true });
    else if (m[2]) out.push({ text: m[2], not: false });
  }
  return out;
}

// ---- Get Circuit Statistics ----

export interface StatLine { component: string; library: string; simple: number; unique: number; recursive: number; total?: boolean }
export function statisticsLines(s: Statistics): StatLine[] {
  return [
    ...s.rows.map((r) => ({ ...r })),
    { component: "TOTAL (without project's subcircuits)", library: '', ...s.without, total: true },
    { component: 'TOTAL (with subcircuits)', library: '', ...s.with, total: true },
  ];
}
export const STATISTICS_HINT = 'Simple 칸: 이 회로에 바로 놓인 수. Unique 칸: 이 회로와 그 안의 서브회로 정의에 놓인 수(정의마다 한 번). Recursive 칸: 서브회로 인스턴스를 모두 펼친 수.';

// ---- Create Submission (E-06) ----

export interface Check { ok: boolean; text: string }

// v1's checks (they say, they never stop the student).
export function submissionChecks(p: SubmissionPlan): Check[] {
  return [
    { ok: p.saved && !p.dirty, text: !p.saved ? '파일을 저장한 적이 없습니다.' : p.dirty ? '저장하지 않은 변경이 있습니다. 압축 파일에는 마지막으로 저장한 파일이 들어갑니다.' : '파일을 저장했습니다.' },
    { ok: p.messages === 0, text: p.messages === 0 ? 'Messages 0건입니다.' : `Messages ${p.messages}건이 남아 있습니다. 회로가 동작하지 않을 수 있습니다.` },
    { ok: p.probes === 0, text: p.probes === 0 ? '남은 Probe 부품이 없습니다.' : `Probe 부품 ${p.probes}개가 남아 있습니다. 제출하기 전에 지울지 정합니다.` },
    {
      ok: p.missing.length === 0,
      text: p.missing.length === 0 ? '회로가 가리키는 파일이 모두 들어갑니다. 압축을 풀면 원조 Logisim 2.7.1 프로그램과 이 도구에서 그대로 열립니다.'
        : '찾지 못했거나 .circ 파일의 폴더 밖에 있어 넣지 못한 파일이 있습니다. 그 파일을 .circ 파일 옆 폴더로 옮기고 다시 불러오면 함께 들어갑니다.',
    },
  ];
}
export const SUBMISSION_HINT = '점검은 알리기만 합니다. 제출할지는 직접 정합니다.';

// ---- Export Image (E-07) and Print (I-134) ----

export type PictureFormat = 'png' | 'svg' | 'pdf';
export const FORMATS: readonly [PictureFormat, string][] = [['png', 'PNG'], ['svg', 'SVG'], ['pdf', 'PDF']];
export const SCALES: readonly number[] = [1, 2, 3, 4];

// v1's name: <file>-<circuit>.<ext> (the file's name without .circ; a name the system refuses loses its bad letters).
export function pictureName(file: string, circuit: string, format: PictureFormat): string {
  const clean = (s: string) => s.replace(/[\\/:*?"<>|\u0000-\u001f]/g, '_').trim();
  return `${clean(file.replace(/\.circ$/i, '')) || 'circuit'}-${clean(circuit) || 'circuit'}.${format}`;
}

/* The picture of a selection (v1: Selection only): the chosen parts and wires, the nets cut down to
   them (their ids kept: the values on show still colour them), the dots where a kept wire ends. */
export function subSnapshot(s: Snapshot, ids: Iterable<string>): Snapshot {
  const keep = new Set(ids);
  const components = s.components.filter((c) => keep.has(c.id));
  const wires = s.wires.filter((w) => keep.has(w.id));
  const parts = new Set(components.map((c) => c.id));
  const kept = new Set(wires.map((w) => w.id));
  const ends = new Set(wires.flatMap((w) => [`${w.a}`, `${w.b}`]));
  return {
    circuitId: s.circuitId, name: s.name, components, wires,
    nets: s.nets.map((n) => ({ ...n, wires: n.wires.filter((w) => kept.has(w)), ports: n.ports.filter(([c]) => parts.has(c)) }))
      .filter((n) => n.wires.length > 0 || n.ports.length > 0),
    junctions: s.junctions.filter((j) => ends.has(`${j}`)),
  };
}

export const PRINT_HEADER = '%n (%p of %P)';    // the original's default header
export const EXPORT_SENTENCE = '보이는 회로를 Canvas 그림 그대로 내보냅니다. SVG·PDF 형식은 벡터라 크게 해도 선명하고, PNG 형식은 고른 배율로 그립니다.';
export const PRINT_SENTENCE = '고른 회로를 한 쪽에 하나씩 인쇄합니다. 머리글 자리 표시: `%n` 회로 이름, `%p` 쪽 번호, `%P` 전체 쪽 수.';
export const PRINT_NONE = '인쇄할 회로를 하나 이상 고르세요.';
