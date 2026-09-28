/* The geometry of the editing gestures (N-08, docs/interaction-parity.md
   I-01, I-15..I-17, I-23, I-44..I-51, I-55): Logisim 2.7.1's own rules,
   read from the tool code and written again here only to show what the
   gesture will do while it goes on (the green circle, the wire being
   drawn, the rectangle, the parts being dragged).  What the gesture does
   to the circuit is the engine's: the screen sends the intent (edit.*)
   and the engine runs the same tool code on the model. */

import type { Component, Point, Wire } from '../../main/protocol.ts';

export const GRID = 10;

// Canvas.snapXToGrid: to the nearest multiple of 10, halves away from zero.
export function snapCoord(v: number): number {
  return v < 0 ? -Math.floor((-v + 5) / GRID) * GRID : Math.floor((v + 5) / GRID) * GRID;
}
export const snapPoint = (p: Point): Point => [snapCoord(p[0]), snapCoord(p[1])];

// A pointer's logical point (Canvas.repairMouseEvent): whole circuit units.
export const logical = (p: readonly [number, number]): Point => [Math.round(p[0]), Math.round(p[1])];

const same = (a: Point, b: Point) => a[0] === b[0] && a[1] === b[1];

// Wire.contains: on the wire, 2 units either side (the ends count).
export function wireContains(w: Wire, q: Point): boolean {
  const x0 = Math.min(w.a[0], w.b[0]), x1 = Math.max(w.a[0], w.b[0]);
  const y0 = Math.min(w.a[1], w.b[1]), y1 = Math.max(w.a[1], w.b[1]);
  if (x0 === x1) return q[0] >= x0 - 2 && q[0] <= x0 + 2 && y0 <= q[1] && q[1] <= y1;
  return q[1] >= y0 - 2 && q[1] <= y0 + 2 && x0 <= q[0] && q[0] <= x1;
}
export const wireEndsAt = (w: Wire, q: Point): boolean => same(w.a, q) || same(w.b, q);
export const wireLength = (w: Wire): number => Math.abs(w.a[0] - w.b[0]) + Math.abs(w.a[1] - w.b[1]);

// What the gestures read of the circuit on show (read more than once: arrays, not one-pass iterators).
export interface Parts {
  components: readonly Component[];
  wires: readonly Wire[];
}

// Circuit.getComponents(loc): the parts and wires with an end there (a port, a wire's end).
export function endsAt(parts: Parts, q: Point): number {
  let n = 0;
  for (const c of parts.components) for (const p of c.ports) if (same(p.loc, q)) n++;
  for (const w of parts.wires) if (wireEndsAt(w, q)) n++;
  return n;
}

// Circuit.getWires(loc): the wires with an end there.
export function wiresEndingAt(parts: Parts, q: Point): Wire[] {
  return parts.wires.filter((w) => wireEndsAt(w, q));
}

/* EditTool.updateLocation + isWiringPoint: where a press of the Edit tool
   starts a wire (the green circle, I-44), or null (it selects).  The point
   is the grid point nearest the pointer, within 6 units (always with Alt).
   Alt turns it round (I-17): on a port or a wire it selects, elsewhere it
   draws.  On a selected wire, off its ends, a press selects (to drag it,
   I-16). */
export function wiringPoint(parts: Parts, selected: ReadonlySet<string>, raw: Point, alt: boolean): Point | null {
  const snap = snapPoint(raw);
  const dx = raw[0] - snap[0], dy = raw[1] - snap[1];
  if (!(dx * dx + dy * dy < 36 || alt)) return null;
  const wiring = !alt, select = alt;
  const hit = (() => {
    for (const w of parts.wires) if (selected.has(w.id) && wireContains(w, snap) && !wireEndsAt(w, snap)) return select;
    if (endsAt(parts, snap) > 0) return wiring;
    for (const w of parts.wires) if (wireContains(w, snap)) return wiring;
    return select;
  })();
  return hit ? snap : null;
}

// EditTool.isClick: released within 2 units of the press (a click, not a drag).
export const isClick = (press: Point, now: Point): boolean => {
  const dx = now[0] - press[0], dy = now[1] - press[1];
  return dx * dx + dy * dy <= 4;
};

/* The wire being drawn (WiringTool): a press at `start`, then every move.
   The first way the pointer leaves the start decides the L's bend
   (computeMove, I-47): horizontal first or vertical first, until the
   pointer comes back in line with the start.  Dragging along a wire from
   one of its ends shortens it (I-50). */
export class WireDrag {
  readonly start: Point;
  cur: Point;
  direction: 0 | 'h' | 'v' = 0;
  dragged = false;
  private readonly startShortening: boolean;
  private shortening: Wire | null = null;
  private readonly parts: Parts;

  constructor(parts: Parts, start: Point) {
    this.parts = parts;
    this.start = snapPoint(start);
    this.cur = this.start;
    this.startShortening = wiresEndingAt(parts, this.start).length > 0;
  }

  // computeMove + mouseDragged: false when the snapped point did not change.
  move(raw: Point): boolean {
    const n = snapPoint(raw);
    if (same(n, this.cur)) return false;
    const s = this.start;
    if (this.direction === 0) {
      if (n[0] !== s[0]) this.direction = 'h';
      else if (n[1] !== s[1]) this.direction = 'v';
    } else if (this.direction === 'h' && n[0] === s[0]) {
      this.direction = n[1] === s[1] ? 0 : 'v';
    } else if (this.direction === 'v' && n[1] === s[1]) {
      this.direction = n[0] === s[0] ? 0 : 'h';
    }
    this.dragged = true;
    this.cur = n;
    let shorten: Wire | null = null;
    if (this.startShortening) shorten = wiresEndingAt(this.parts, s).find((w) => wireContains(w, n)) ?? null;
    if (!shorten) shorten = wiresEndingAt(this.parts, n).find((w) => wireContains(w, s)) ?? null;
    this.shortening = shorten;
    return true;
  }

  // The points the intent gets (edit.addWire): a straight wire's two, an L's three (its corner in the middle).
  points(): Point[] {
    const s = this.start, c = this.cur;
    if (s[0] === c[0] || s[1] === c[1]) return [s, c];
    const m: Point = this.direction === 'h' ? [c[0], s[1]] : [s[0], c[1]];
    return [s, m, c];
  }

  /* willShorten + getShortenResult: the wire the drag shortens and what is
     left of it (null: all of it goes), for the picture; the engine decides
     for itself on release.  null: nothing is shortened. */
  shortened(): { wire: Wire; rest: [Point, Point] | null } | null {
    const w = this.shortening, s = this.start, c = this.cur;
    if (!w) return null;
    const other = (end: Point): Point => (same(w.a, end) ? w.b : w.a);
    let rest: [Point, Point];
    if (wireEndsAt(w, s)) rest = [c, other(s)];
    else if (wireEndsAt(w, c)) rest = [s, other(c)];
    else return null;
    return { wire: w, rest: same(rest[0], rest[1]) ? null : rest };
  }
}

// SelectTool.computeDxDy: not above or left of the page's origin, on the grid when a part snaps (a Label does not).
export function dragOffset(bounds: Box | null, snaps: boolean, dx: number, dy: number): Point {
  let x = dx, y = dy;
  if (bounds) { x = Math.max(x, -bounds.x0); y = Math.max(y, -bounds.y0); }
  return snaps ? [snapCoord(x), snapCoord(y)] : [x, y];
}

export interface Box { x0: number; y0: number; x1: number; y1: number }

// The box around parts and wires (Selection.getBounds): null when there is nothing.
export function boundsOf(comps: Iterable<Component>, wires: Iterable<Wire>): Box | null {
  let b: Box | null = null;
  const add = (x0: number, y0: number, x1: number, y1: number) => {
    b = b ? { x0: Math.min(b.x0, x0), y0: Math.min(b.y0, y0), x1: Math.max(b.x1, x1), y1: Math.max(b.y1, y1) } : { x0, y0, x1, y1 };
  };
  for (const c of comps) add(c.bounds[0], c.bounds[1], c.bounds[0] + c.bounds[2], c.bounds[1] + c.bounds[3]);
  for (const w of wires) add(Math.min(w.a[0], w.b[0]), Math.min(w.a[1], w.b[1]), Math.max(w.a[0], w.b[0]), Math.max(w.a[1], w.b[1]));
  return b;
}

// Selection.shouldSnap: a part that keeps to the grid (every one but a Label, Base › Text).
export const snapsToGrid = (c: Component): boolean => !(c.lib === 'Base' && c.name === 'Text');

// The rectangle of a rubber-band drag, from its two corners.
export const rectOf = (a: Point, b: Point): Box => ({ x0: Math.min(a[0], b[0]), y0: Math.min(a[1], b[1]), x1: Math.max(a[0], b[0]), y1: Math.max(a[1], b[1]) });
