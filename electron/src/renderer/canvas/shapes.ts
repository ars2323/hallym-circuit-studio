/* The vector language of the part drawings (N-05, D-137): what a renderer
   in the registry (registry.ts) returns for one component -- outlines,
   curves and arcs, port marks, text -- in circuit coordinates, like an SVG
   fragment.  The Canvas painter (paint.ts) draws it on screen and the SVG
   writer (svg.ts) turns the very same list into picture export, so the two
   cannot drift apart; the geometry check (N-06) reads it too.

   Colours are names of tokens (tokens.ts), or the colour of a value (the
   characters the engine sends for a net: '0', '1', 'x', 'E', high bit
   first), resolved by whoever draws.  Sizes are circuit units: 10 is one
   grid step, and 100 % zoom draws one unit as one CSS pixel.  Text never
   turns with the part: a renderer places it upright. */

export type Point = [number, number];

/* A token name (tokens.ts THEME), a value's colour, or a literal colour
   (the colour attributes of LEDs and the 7-segment display, a tunnel's
   palette colour). */
export type Paint = string | { value: string } | { rgb: string; alpha?: number };

export type Seg =
  | ['M', number, number]
  | ['L', number, number]
  | ['Q', number, number, number, number]                       // control, end
  | ['C', number, number, number, number, number, number]       // control 1, control 2, end
  | ['A', number, number, number, number, number, boolean]      // centre, radius, from, to (radians), anticlockwise
  | ['Z'];

// What a shape is for: the painter and the checks treat them by role.
//   body   the part's outline and inside (inside the engine's bounds)
//   stub   a line from a port to the body (inside the bounds, touches the port)
//   deco   marks inside the body (numbers, symbols, the clock triangle)
//   port   a port mark (exactly at the engine's port location)
export type Role = 'body' | 'stub' | 'deco' | 'port';

interface Common {
  role?: Role;
  stroke?: Paint;
  fill?: Paint;
  width?: number;          // stroke width, circuit units
  dash?: number[];
  cap?: 'round' | 'butt' | 'square';
  bits?: number;           // a stub: the width of the signal it carries (a bus is drawn wider)
  grows?: boolean;         // extends past the engine's bounds on the side away from the ports (a tunnel's long name)
}

export interface PathShape extends Common { k: 'path'; d: Seg[] }
export interface RectShape extends Common { k: 'rect'; x: number; y: number; w: number; h: number; r?: number }
export interface EllipseShape extends Common { k: 'ellipse'; cx: number; cy: number; rx: number; ry: number }
export interface TextShape extends Common {
  k: 'text';
  x: number; y: number;
  text: string;
  font: 'ui' | 'code';     // Pretendard | D2Coding
  size: number;            // circuit units (12 at 100 % is 12 px)
  weight?: number;         // 400 regular .. 700 bold
  anchor?: 'start' | 'middle' | 'end';
  baseline?: 'middle' | 'alphabetic' | 'top' | 'bottom';
  fit?: number;            // the widest it may be: a longer text is drawn smaller
  rotate?: number;         // radians around (x, y); only where the original turns it (a turned subcircuit's appearance)
}
export interface PortShape extends Common {
  k: 'port';
  x: number; y: number;
  i: number;               // the engine's port index
  dir: 'in' | 'out' | 'inout';
  bits: number;
  value?: string;          // the net's value at the port, when known
}

export type Shape = PathShape | RectShape | EllipseShape | TextShape | PortShape;

// ---- building paths -------------------------------------------------------------

export class Path {
  readonly d: Seg[] = [];
  M(x: number, y: number): this { this.d.push(['M', x, y]); return this; }
  L(x: number, y: number): this { this.d.push(['L', x, y]); return this; }
  Q(cx: number, cy: number, x: number, y: number): this { this.d.push(['Q', cx, cy, x, y]); return this; }
  C(c1x: number, c1y: number, c2x: number, c2y: number, x: number, y: number): this { this.d.push(['C', c1x, c1y, c2x, c2y, x, y]); return this; }
  A(cx: number, cy: number, r: number, from: number, to: number, anticlockwise = false): this { this.d.push(['A', cx, cy, r, from, to, anticlockwise]); return this; }
  Z(): this { this.d.push(['Z']); return this; }
  poly(points: Point[], close = true): this {
    points.forEach(([x, y], i) => (i === 0 ? this.M(x, y) : this.L(x, y)));
    return close ? this.Z() : this;
  }
}

export const path = (d: Path | Seg[], style: Common = {}): PathShape => ({ k: 'path', d: d instanceof Path ? d.d : d, ...style });
export const line = (a: Point, b: Point, style: Common = {}): PathShape => path(new Path().M(a[0], a[1]).L(b[0], b[1]), style);

// ---- where an arc goes ------------------------------------------------------------

export function arcEnds(s: ['A', number, number, number, number, number, boolean]): { start: Point; end: Point } {
  const [, cx, cy, r, a0, a1] = s;
  return { start: [cx + r * Math.cos(a0), cy + r * Math.sin(a0)], end: [cx + r * Math.cos(a1), cy + r * Math.sin(a1)] };
}

// The arc's sweep in radians, in the direction it is drawn (Canvas's rule:
// clockwise on screen unless anticlockwise, a full turn at most).
export function arcSweep(s: ['A', number, number, number, number, number, boolean]): number {
  const [, , , , a0, a1, ccw] = s;
  const TAU = Math.PI * 2;
  if (!ccw) {
    if (a1 - a0 >= TAU) return TAU;
    let d = (a1 - a0) % TAU;
    if (d < 0) d += TAU;
    return d;
  }
  if (a0 - a1 >= TAU) return TAU;
  let d = (a0 - a1) % TAU;
  if (d < 0) d += TAU;
  return d;
}

// ---- points along a shape (for the checks, hit-testing and bounds) -----------------

// The shape's outline as polylines, curves sampled finely enough for the
// checks (a curve is split into 24 pieces, an arc into pieces of 3 degrees).
export function outline(s: Shape): Point[][] {
  switch (s.k) {
    case 'rect': {
      const { x, y, w, h } = s;
      const r = Math.min(s.r ?? 0, w / 2, h / 2);
      if (r <= 0) return [[[x, y], [x + w, y], [x + w, y + h], [x, y + h], [x, y]]];
      // rounded corners, as Canvas roundRect and SVG rx draw them
      const pts: Point[] = [];
      const corner = (cx: number, cy: number, a0: number) => {
        for (let i = 0; i <= 8; i++) {
          const a = a0 + (i / 8) * (Math.PI / 2);
          pts.push([cx + r * Math.cos(a), cy + r * Math.sin(a)]);
        }
      };
      corner(x + w - r, y + r, -Math.PI / 2);
      corner(x + w - r, y + h - r, 0);
      corner(x + r, y + h - r, Math.PI / 2);
      corner(x + r, y + r, Math.PI);
      pts.push(pts[0]);
      return [pts];
    }
    case 'ellipse': {
      const pts: Point[] = [];
      for (let i = 0; i <= 96; i++) {
        const a = (i / 96) * Math.PI * 2;
        pts.push([s.cx + s.rx * Math.cos(a), s.cy + s.ry * Math.sin(a)]);
      }
      return [pts];
    }
    case 'port':
      return [[[s.x, s.y]]];
    case 'text':
      return [];
    case 'path':
      return pathPolylines(s.d);
  }
}

export function pathPolylines(d: Seg[]): Point[][] {
  const out: Point[][] = [];
  let cur: Point[] = [];
  let at: Point = [0, 0];
  let start: Point = [0, 0];
  const flush = () => { if (cur.length) out.push(cur); cur = []; };
  for (const s of d) {
    switch (s[0]) {
      case 'M':
        flush();
        at = [s[1], s[2]]; start = at; cur = [at];
        break;
      case 'L':
        if (!cur.length) cur = [at];
        at = [s[1], s[2]]; cur.push(at);
        break;
      case 'Q': {
        if (!cur.length) cur = [at];
        const [x0, y0] = at;
        for (let i = 1; i <= 24; i++) {
          const t = i / 24, u = 1 - t;
          cur.push([u * u * x0 + 2 * u * t * s[1] + t * t * s[3], u * u * y0 + 2 * u * t * s[2] + t * t * s[4]]);
        }
        at = [s[3], s[4]];
        break;
      }
      case 'C': {
        if (!cur.length) cur = [at];
        const [x0, y0] = at;
        for (let i = 1; i <= 24; i++) {
          const t = i / 24, u = 1 - t;
          cur.push([u * u * u * x0 + 3 * u * u * t * s[1] + 3 * u * t * t * s[3] + t * t * t * s[5],
            u * u * u * y0 + 3 * u * u * t * s[2] + 3 * u * t * t * s[4] + t * t * t * s[6]]);
        }
        at = [s[5], s[6]];
        break;
      }
      case 'A': {
        const { start: a, end } = arcEnds(s);
        if (!cur.length) { cur = [a]; start = a; } else cur.push(a);
        const sweep = arcSweep(s);
        const n = Math.max(2, Math.ceil(sweep / (Math.PI / 60)));
        for (let i = 1; i <= n; i++) {
          const ang = s[4] + (s[6] ? -1 : 1) * sweep * (i / n);
          cur.push([s[1] + s[3] * Math.cos(ang), s[2] + s[3] * Math.sin(ang)]);
        }
        at = end;
        break;
      }
      case 'Z':
        if (cur.length) cur.push(start);
        at = start;
        flush();
        break;
    }
  }
  flush();
  return out;
}

export interface Box { x0: number; y0: number; x1: number; y1: number }
export const EMPTY_BOX: Box = { x0: Infinity, y0: Infinity, x1: -Infinity, y1: -Infinity };
export const boxUnion = (a: Box, b: Box): Box => ({ x0: Math.min(a.x0, b.x0), y0: Math.min(a.y0, b.y0), x1: Math.max(a.x1, b.x1), y1: Math.max(a.y1, b.y1) });
export const boxOf = (x: number, y: number, w: number, h: number): Box => ({ x0: x, y0: y, x1: x + w, y1: y + h });
export const boxesMeet = (a: Box, b: Box): boolean => a.x0 < b.x1 && b.x0 < a.x1 && a.y0 < b.y1 && b.y0 < a.y1;

// The geometry's extent (stroke widths not counted); text is measured by
// `textWidth` (a rough 0.6 em per character when nothing better is given).
export function shapeBox(s: Shape, textWidth: (t: TextShape) => number = roughWidth): Box {
  if (s.k === 'text') {
    const w = Math.min(textWidth(s), s.fit ?? Infinity);
    const x0 = s.anchor === 'middle' ? s.x - w / 2 : s.anchor === 'end' ? s.x - w : s.x;
    const b = s.baseline ?? 'alphabetic';
    const top = b === 'top' ? s.y : b === 'middle' ? s.y - s.size / 2 : b === 'bottom' ? s.y - s.size : s.y - s.size * 0.8;
    const box = { x0, y0: top, x1: x0 + w, y1: top + s.size };
    if (!s.rotate) return box;
    const c = Math.cos(s.rotate), n = Math.sin(s.rotate);
    let out = EMPTY_BOX;
    for (const [px, py] of [[box.x0, box.y0], [box.x1, box.y0], [box.x1, box.y1], [box.x0, box.y1]]) {
      const dx = px - s.x, dy = py - s.y;
      const q = { x0: s.x + dx * c - dy * n, y0: s.y + dx * n + dy * c };
      out = boxUnion(out, { ...q, x1: q.x0, y1: q.y0 });
    }
    return out;
  }
  let b = EMPTY_BOX;
  for (const pl of outline(s)) for (const [x, y] of pl) b = boxUnion(b, { x0: x, y0: y, x1: x, y1: y });
  return b;
}
export const roughWidth = (t: TextShape): number => t.text.length * t.size * (t.font === 'code' ? 0.5 : 0.56);

// ---- turning a part to its facing ---------------------------------------------------

/* Parts are drawn facing east (the output on the right, Logisim's
   convention) around their location and turned to their facing: north a
   quarter turn anticlockwise on screen, west a half turn, south a quarter
   turn clockwise.  Text anchors move with the part; text itself stays upright. */
export type Facing = 'east' | 'west' | 'north' | 'south';

export interface Turn { cos: number; sin: number; ox: number; oy: number }
export function turn(facing: Facing | null | undefined, ox: number, oy: number): Turn {
  switch (facing) {
    case 'north': return { cos: 0, sin: -1, ox, oy };
    case 'west': return { cos: -1, sin: 0, ox, oy };
    case 'south': return { cos: 0, sin: 1, ox, oy };
    default: return { cos: 1, sin: 0, ox, oy };
  }
}
export const apply = (t: Turn, x: number, y: number): Point =>
  [t.ox + Math.round((x * t.cos - y * t.sin) * 1e6) / 1e6, t.oy + Math.round((x * t.sin + y * t.cos) * 1e6) / 1e6];

export function turned(t: Turn, s: Shape): Shape {
  const a = (x: number, y: number) => apply(t, x, y);
  switch (s.k) {
    case 'path':
      return { ...s, d: s.d.map((g): Seg => {
        switch (g[0]) {
          case 'M': case 'L': { const [x, y] = a(g[1], g[2]); return [g[0], x, y]; }
          case 'Q': { const [cx, cy] = a(g[1], g[2]); const [x, y] = a(g[3], g[4]); return ['Q', cx, cy, x, y]; }
          case 'C': { const [c1x, c1y] = a(g[1], g[2]); const [c2x, c2y] = a(g[3], g[4]); const [x, y] = a(g[5], g[6]); return ['C', c1x, c1y, c2x, c2y, x, y]; }
          case 'A': {
            const [cx, cy] = a(g[1], g[2]);
            const rot = Math.atan2(t.sin, t.cos);
            return ['A', cx, cy, g[3], g[4] + rot, g[5] + rot, g[6]];
          }
          default: return g;
        }
      }) };
    case 'rect': {
      const [x0, y0] = a(s.x, s.y);
      const [x1, y1] = a(s.x + s.w, s.y + s.h);
      return { ...s, x: Math.min(x0, x1), y: Math.min(y0, y1), w: Math.abs(x1 - x0), h: Math.abs(y1 - y0) };
    }
    case 'ellipse': {
      const [cx, cy] = a(s.cx, s.cy);
      const swap = Math.abs(t.sin) > 0.5;
      return { ...s, cx, cy, rx: swap ? s.ry : s.rx, ry: swap ? s.rx : s.ry };
    }
    case 'text': {
      const [x, y] = a(s.x, s.y);
      return { ...s, x, y };
    }
    case 'port': {
      const [x, y] = a(s.x, s.y);
      return { ...s, x, y };
    }
  }
}

export const turnAll = (t: Turn, shapes: Shape[]): Shape[] => shapes.map((s) => turned(t, s));

// ---- distances (the checks and hit-testing) -------------------------------------------

export function segmentDistance(p: Point, a: Point, b: Point): number {
  const dx = b[0] - a[0], dy = b[1] - a[1];
  const len2 = dx * dx + dy * dy;
  let t = len2 === 0 ? 0 : ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / len2;
  t = Math.max(0, Math.min(1, t));
  return Math.hypot(p[0] - (a[0] + t * dx), p[1] - (a[1] + t * dy));
}

// How far p is from the shape's outline (a filled shape's inside counts as 0).
export function distanceTo(p: Point, s: Shape): number {
  if (s.k === 'text') {
    const b = shapeBox(s);
    return p[0] >= b.x0 && p[0] <= b.x1 && p[1] >= b.y0 && p[1] <= b.y1 ? 0 : Infinity;
  }
  if (s.k === 'port') return Math.hypot(p[0] - s.x, p[1] - s.y);
  let best = Infinity;
  for (const pl of outline(s)) {
    if (pl.length === 1) best = Math.min(best, Math.hypot(p[0] - pl[0][0], p[1] - pl[0][1]));
    for (let i = 1; i < pl.length; i++) best = Math.min(best, segmentDistance(p, pl[i - 1], pl[i]));
  }
  return best;
}
