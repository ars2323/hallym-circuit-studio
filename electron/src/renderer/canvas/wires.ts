/* Where the wires meet and cross (N-05; v1 W-04 D-061, E-03 D-086).

   - Connection dots: the engine's junctions (a wire end where three or more
     wires or ports meet, Logisim's own dots) and every T: a wire end or a
     port lying inside another wire of the same net.
   - Jumps: a horizontal and a vertical wire crossing inside both, without
     connecting: the horizontal one hops over the vertical one (a half
     circle), so a crossing never reads as a connection.
   - Bus width marks: a short slash and the bit count on the longest wire
     of every bus.
   All computed once per model change from the scene; sizes on screen
   follow the zoom (the functions at the end). */

import type { Point } from '../../main/protocol.ts';
import type { Scene } from './scene.ts';

export interface Segment { id: string; a: Point; b: Point; horizontal: boolean; net: string | null; bits: number }
export interface Crossing { at: Point; over: string; under: string }        // over: the horizontal wire (it hops)
export interface WidthMark { at: Point; vertical: boolean; bits: number; net: string; wire: string }

export interface WireMarks {
  segments: Segment[];
  dots: Point[];
  crossings: Crossing[];
  widths: WidthMark[];
}

const key = (p: Point) => `${p[0]},${p[1]}`;

export function wireMarks(scene: Scene): WireMarks {
  const segments: Segment[] = [];
  for (const w of scene.wires.values()) {
    const n = scene.wireNet(w.id);
    segments.push({ id: w.id, a: w.a, b: w.b, horizontal: w.a[1] === w.b[1], net: n?.id ?? null, bits: n?.width ?? 1 });
  }
  const horizontal = segments.filter((s) => s.horizontal && s.a[0] !== s.b[0]);
  const vertical = segments.filter((s) => !s.horizontal);
  // rows and columns: the wires on each line
  const rows = new Map<number, Segment[]>(), cols = new Map<number, Segment[]>();
  for (const s of horizontal) rows.set(s.a[1], [...(rows.get(s.a[1]) ?? []), s]);
  for (const s of vertical) cols.set(s.a[0], [...(cols.get(s.a[0]) ?? []), s]);
  const inside = (s: Segment, p: Point): boolean => {
    if (s.horizontal) {
      const [x0, x1] = s.a[0] < s.b[0] ? [s.a[0], s.b[0]] : [s.b[0], s.a[0]];
      return p[1] === s.a[1] && p[0] > x0 && p[0] < x1;
    }
    const [y0, y1] = s.a[1] < s.b[1] ? [s.a[1], s.b[1]] : [s.b[1], s.a[1]];
    return p[0] === s.a[0] && p[1] > y0 && p[1] < y1;
  };

  // Dots: the engine's, and Ts (an end or a port inside a wire of the same net).
  const dots = new Map<string, Point>();
  for (const j of scene.junctions) dots.set(key(j), j);
  const ends: { at: Point; net: string | null }[] = [];
  for (const s of segments) ends.push({ at: s.a, net: s.net }, { at: s.b, net: s.net });
  for (const c of scene.components.values()) {
    for (const q of c.ports) ends.push({ at: q.loc, net: scene.netOf(c.id, q.i)?.id ?? null });
  }
  for (const e of ends) {
    if (e.net === null) continue;
    const onLine = [...(rows.get(e.at[1]) ?? []), ...(cols.get(e.at[0]) ?? [])];
    if (onLine.some((s) => s.net === e.net && inside(s, e.at))) dots.set(key(e.at), e.at);
  }

  // Crossings: inside both wires, and not a connection (no dot there).
  const crossings: Crossing[] = [];
  const colXs = [...cols.keys()].sort((a, b) => a - b);
  for (const h of horizontal) {
    const [x0, x1] = h.a[0] < h.b[0] ? [h.a[0], h.b[0]] : [h.b[0], h.a[0]];
    const y = h.a[1];
    // the columns strictly between the ends (binary search for the first)
    let lo = 0, hi = colXs.length;
    while (lo < hi) { const m = (lo + hi) >> 1; if (colXs[m] <= x0) lo = m + 1; else hi = m; }
    for (let i = lo; i < colXs.length && colXs[i] < x1; i++) {
      for (const v of cols.get(colXs[i])!) {
        const at: Point = [colXs[i], y];
        if (inside(v, at) && !dots.has(key(at))) crossings.push({ at, over: h.id, under: v.id });
      }
    }
  }

  // Bus widths: the longest wire (30 units at least) of every net wider than one bit.
  const longest = new Map<string, Segment>();
  const len = (s: Segment) => Math.abs(s.a[0] - s.b[0]) + Math.abs(s.a[1] - s.b[1]);
  for (const s of segments) {
    if (!s.net || s.bits <= 1 || len(s) < 30) continue;
    const best = longest.get(s.net);
    if (!best || len(s) > len(best) || (len(s) === len(best) && s.id < best.id)) longest.set(s.net, s);
  }
  const widths: WidthMark[] = [...longest.values()].map((s) => ({
    at: [(s.a[0] + s.b[0]) / 2, (s.a[1] + s.b[1]) / 2] as Point,
    vertical: !s.horizontal, bits: s.bits, net: s.net!, wire: s.id,
  }));

  return { segments, dots: [...dots.values()], crossings, widths };
}

// ---- sizes on screen (v1 D-061) -------------------------------------------------------------------

// A connection dot's diameter in circuit units: 7 px on screen, but never more than 16 units
// (at 25 % it would reach the next grid line's wire) nor less than the original's 8.
export const dotUnits = (zoom: number): number => Math.min(16, Math.max(8, 7 / zoom)) * 0.9;

// A jump's radius in circuit units: 5 units or 5 px, at most 8 units (half a grid step and less);
// null below about 44 %, where the hop would be under 3.5 px: then a plain crossing (the dots tell).
export function jumpUnits(zoom: number): number | null {
  const r = Math.min(8, Math.max(5, 5 / zoom));
  return r * zoom < 3.5 ? null : r;
}
