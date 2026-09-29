/* The Attributes panel's and Quick Attributes' logic (N-10, D-157):
   which table to ask for, how the original table's title reads in the
   Inspector's head, the words when a value is refused, the font editor's
   parts, a circuit name that cannot be, and the buttons of the small bar
   by the selection (v1 QuickAttrs: at most five, in the part kind's
   registry order, read-only ones left out).  Logic only; the engine reads
   every value with the original's parse (edit.setAttr, setToolAttr,
   setCircuitAttr) and says when it cannot. */

import type { AttrOption, AttrRow, AttrTable, QuickFacts } from '../../../main/protocol.ts';

// What the panel shows: the tool in hand (a part to place, the Text tool), else the Canvas's selection
// (the circuit's attributes when nothing is selected: the engine answers so).
export type AttrRequest =
  | { kind: 'selection'; fileId: string; circuitId: string }
  | { kind: 'tool'; fileId: string; lib: string | null; name: string; attrs?: Record<string, string> };

// The tool in hand (the toolbar's name, the part held) → what the table is of.
export function requestFor(fileId: string, circuitId: string, tool: string, held: { lib: string | null; name: string; attrs?: Record<string, string> } | null): AttrRequest {
  if (tool === 'Place' && held) return { kind: 'tool', fileId, lib: held.lib, name: held.name, ...(held.attrs && Object.keys(held.attrs).length ? { attrs: { ...held.attrs } } : {}) };
  if (tool === 'Text') return { kind: 'tool', fileId, lib: 'Base', name: 'Text Tool' };
  return { kind: 'selection', fileId, circuitId };
}

export const requestKey = (r: AttrRequest | null): string =>
  !r ? '' : r.kind === 'tool' ? `tool ${r.fileId} ${r.lib ?? ''} ${r.name} ${JSON.stringify(r.attrs ?? {})}` : `sel ${r.fileId} ${r.circuitId}`;

// A part held with values of its own (the search palette's "and 3", Ctrl+2..9's toolbar tool): the tool's table shows
// what will be placed -- those values over the tool's (D-158, UI review of edit-ghost.png).
export function withHeld(t: AttrTable, attrs: Record<string, string>): AttrTable {
  return {
    ...t,
    rows: t.rows.map((r) => {
      const v = attrs[r.attr];
      if (v === undefined) return r;
      const text = r.options?.find((o) => o.value === v)?.display ?? v;
      return { ...r, value: v, text, mixed: false };
    }),
  };
}

// The original title "Selection: AND Gate × 2" as the Inspector's head: the name, and what it is of in a badge.
export function heading(t: AttrTable): { name: string; badge: string } {
  const m = /^(Selection|Circuit|Tool): (.*)$/s.exec(t.title);
  return m ? { name: m[2], badge: m[1] } : { name: t.title, badge: '' };
}

// What the row's value cell edits with (read-only rows and a file that cannot be changed: text only).
export type Editor = 'select' | 'field' | 'number' | 'font' | 'color' | 'contents' | 'text';
export function editorOf(row: AttrRow, editable: boolean): Editor {
  if (row.type === 'contents') return 'contents';          // the hex editor also reads (Save Image)
  if (row.readOnly || !editable) return 'text';
  switch (row.type) {
    case 'option': return 'select';
    case 'number': return 'number';
    case 'font': return 'font';
    case 'color': return 'color';
    default: return 'field';
  }
}

// The Korean sentence when the engine refused a value (the original's parse, badValue): what, then what to do.
export function badValueSentence(row: AttrRow | undefined, value: string): string {
  const name = row?.display ?? '이';
  const what = value.trim() === '' ? '빈 값은' : '이 값은';
  let todo = '';
  if (row?.type === 'number') {
    if (row.min !== undefined && row.max !== undefined) todo = ` 넣을 수 있는 수: ${row.min}–${row.max}.`;
    else if (row.radix === 16) todo = ' 16진수(0x1F), 10진수(31)로 적습니다.';
    else todo = ' 숫자로 적습니다.';
  } else if (row?.type === 'font') {
    todo = ' 글꼴 이름, 모양, 크기를 고릅니다.';
  } else if (row?.type === 'color') {
    todo = ' #rrggbb 꼴로 적습니다.';
  }
  return `${what} ${name} 속성에 넣을 수 없습니다.${todo}`;
}

// A circuit's new name (the Name row of the circuit's attributes): empty or another circuit's is refused here,
// as Add Circuit refuses them (the original table takes any text and a .circ with two same names cannot be read back).
export function circuitNameProblem(name: string, others: string[]): string | null {
  const n = name.trim();
  if (!n) return '회로 이름이 비어 있습니다. 이름을 적으세요.';
  if (others.includes(n)) return '같은 이름의 회로가 이미 있습니다. 다른 이름을 적으세요.';
  return null;
}

// A font's .circ text "SansSerif bold 12" (Logisim's FontAttribute) and back.
export interface FontParts { family: string; style: string; size: number }
export function fontParts(value: string | null): FontParts {
  const m = /^(.*?)\s+(plain|bold|italic|bolditalic)\s+(\d+)$/i.exec((value ?? '').trim());
  if (!m) return { family: 'SansSerif', style: 'plain', size: 12 };
  return { family: m[1], style: m[2].toLowerCase(), size: Number(m[3]) };
}
export const fontValue = (f: FontParts): string => `${f.family} ${f.style} ${Math.round(f.size)}`;

// A colour's .circ text (#rrggbb, or #rrggbbaa with its alpha) for the colour field (#rrggbb).
export const colorField = (value: string | null): string => (value && /^#[0-9a-f]{6}/i.test(value) ? value.slice(0, 7).toLowerCase() : '#000000');
// The field's colour back, keeping the alpha the value had.
export const colorValue = (field: string, before: string | null): string => (before && /^#[0-9a-f]{8}$/i.test(before) ? field + before.slice(7) : field);

// ---- Quick Attributes (v1 QuickBar, QuickAttrs; I-103, I-104) ------------------------------------------------

export const QUICK_MAX = 5;

export interface QuickButton {
  attr: string;
  name: string;             // the attribute's name ("Data Bits")
  text: string;             // its value as the table shows it, or "(none)"
  options?: AttrOption[];   // a list: a popup of choices; else a field
  label: boolean;           // the label of a single part: edited in place (F2's field)
}

// The bar's buttons: the kind's quick attributes the table has and can change, at most five.
export function quickButtons(t: AttrTable): QuickButton[] {
  const q = t.quick;
  if (!q || !t.editable) return [];
  const out: QuickButton[] = [];
  for (const attr of q.attrs) {
    const row = t.rows.find((r) => r.attr === attr);
    if (!row || row.readOnly || row.type === 'contents') continue;
    out.push({
      attr, name: row.display, text: row.text === '' ? '(none)' : row.text,
      options: row.type === 'option' ? (row.options ?? []).map((o) => ({ ...o, checked: row.value === o.value })) : undefined,
      label: attr === 'label' && q.count === 1,
    });
    if (out.length === QUICK_MAX) break;
  }
  return out;
}

// The line under the buttons: the original's number keys (found by trying the part's key configurator), R, F2.
export function hintLine(q: QuickFacts): string {
  const parts = q.hints.map((h) => `${h.keys}: ${h.display}`);
  if (q.rotate) parts.push('R: Rotate');
  if (q.label) parts.push('F2: Label');
  return parts.join('  ·  ');
}

// ---- where the bar goes (v1 QuickBar.placement, S-04) ---------------------------------------------------------

export interface Rect { x: number; y: number; w: number; h: number }

export const HARD_WEIGHT = 1000;          // covering a part or a label chip is this much worse than a wire
export const OVERLAY_WEIGHT = 100;        // covering what an overlay draws (a flow's arc, a lit wire, a chip)
export const FARTHER = [0, 20, 40, 60];   // tried further away when nothing near is free (screen px)
export const WIDER = [100, 160, 240, 320];   // then, when the best near place still covers a part or an overlay

const overlap = (a: Rect, rs: Rect[]): number => rs.reduce((sum, b) => {
  const w = Math.min(a.x + a.w, b.x + b.w) - Math.max(a.x, b.x);
  const h = Math.min(a.y + a.h, b.y + b.h) - Math.max(a.y, b.y);
  return w > 0 && h > 0 ? sum + w * h : sum;
}, 0);
const intersects = (a: Rect, b: Rect): boolean => a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h;

// Above the target (left-aligned, then right-aligned), below (the same two), right, left (top-aligned, then
// bottom-aligned); then all of it again 20, 40, 60 px further.  The first place inside the view that covers
// nothing; else the one covering the least (parts and chips a thousand times a wire, an overlay's drawing a hundred
// times), earlier on a tie.  When that one still covers a part, a label or an overlay (D-158 18, UI review of
// signal-flow.png), the search goes wider: further away, slid sideways, and the Canvas's corners and edges.
export function placement(target: Rect, bar: { w: number; h: number }, hard: Rect[], soft: Rect[], view: Rect, gap = 6, overlay: Rect[] = []): Rect {
  const near = around(target, bar, gap, FARTHER, [0]);
  const first = best(near, target, hard, soft, overlay, view);
  if (!first.covers) return first.at;                   // wires only (or nothing): the near place, as v1
  const edges: Rect[] = [];
  const m = 8;
  for (const y of [view.y + m, view.y + view.h - bar.h - m]) {
    for (const x of [target.x, target.x + target.w - bar.w, view.x + m, view.x + view.w - bar.w - m]) edges.push({ x, y, ...bar });
  }
  const wide = best([...around(target, bar, gap, WIDER, [0, -0.5, 0.5, -1, 1]), ...edges], target, hard, soft, overlay, view);
  return wide.score < first.score ? wide.at : first.at;
}

function around(target: Rect, bar: { w: number; h: number }, gap: number, distances: number[], slides: number[]): Rect[] {
  const cands: Rect[] = [];
  for (const far of distances) {
    for (const k of slides) {
      const dx = k * bar.w;
      const above = target.y - gap - far - bar.h;
      const below = target.y + target.h + gap + far;
      const rightAligned = target.x + target.w - bar.w;
      const right = target.x + target.w + gap + far;
      const left = target.x - gap - far - bar.w;
      cands.push({ x: target.x + dx, y: above, ...bar }, { x: rightAligned + dx, y: above, ...bar },
        { x: target.x + dx, y: below, ...bar }, { x: rightAligned + dx, y: below, ...bar },
        { x: right, y: target.y + dx / 4, ...bar }, { x: left, y: target.y + dx / 4, ...bar },
        { x: right, y: target.y + target.h - bar.h + dx / 4, ...bar }, { x: left, y: target.y + target.h - bar.h + dx / 4, ...bar });
    }
  }
  return cands;
}

function best(cands: Rect[], target: Rect, hard: Rect[], soft: Rect[], overlay: Rect[], view: Rect): { at: Rect; score: number; covers: boolean } {
  let at: Rect | null = null;
  let bestScore = Infinity;
  let covers = false;                                   // the best one covers a part, a label or an overlay's drawing
  for (const c of cands) {
    const r = { ...c };
    r.x = Math.max(view.x, Math.min(r.x, view.x + view.w - r.w));
    r.y = Math.max(view.y, Math.min(r.y, view.y + view.h - r.h));
    if (intersects(r, target)) continue;   // pushed back into the view, it came over the target
    const h = overlap(r, hard), o = overlap(r, overlay);
    const score = HARD_WEIGHT * h + OVERLAY_WEIGHT * o + overlap(r, soft);
    if (score === 0) return { at: r, score, covers: false };
    if (score < bestScore) { bestScore = score; at = r; covers = h > 0 || o > 0; }
  }
  return { at: at ?? { ...cands[0] }, score: bestScore, covers };
}

