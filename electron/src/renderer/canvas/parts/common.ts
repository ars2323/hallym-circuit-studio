/* What every part renderer shares (N-05): the part as the engine sends it,
   the state it is drawn in, turning a drawing made facing east to the
   part's facing, port marks, and a few recurring bits (a box, a stub from a
   port to the body, a clock triangle, a value in hexadecimal). */

import type { Component } from '../../../main/protocol.ts';
import { apply, type Box, type Facing, type Paint, Path, path, type Point, type PortShape, type Shape, type TextShape, turn, type Turn } from '../shapes.ts';

export type Part = Component;

// A body state (docs/engine-api.md sim.values.bodies): what the part shows
// that no net carries (a RAM's table, the Console's output …).
export type Body = Record<string, unknown>;

export interface PartState {
  value(port: number): string | undefined;   // the net's value at the port ('0' '1' 'x' 'E', high bit first)
  body: Body | undefined;
  tunnelColor?: string;                       // a tunnel's colour (labels.ts tunnelColors)
  // Text widths (circuit units) in the Canvas's fonts; without one, a rough 0.56 em a character.
  measure?: (text: string, font: 'ui' | 'code', size: number, weight: number) => number;
  // How far a tunnel's tag may grow past the engine's bounds, away from its point, before it would meet
  // a wire or another part (labels.ts tunnelRoom); without one, as far as its name needs.
  grow?: number;
}

export const roughWidth = (text: string, font: 'ui' | 'code', size: number): number => text.length * size * (font === 'code' ? 0.5 : 0.56);
export const NO_STATE: PartState = { value: () => undefined, body: undefined };

// Stroke widths (circuit units).
export const OUTLINE = 1.6;
export const GATE_OUTLINE = 2;
export const THIN = 1;

export const attr = (p: Part, name: string, fallback = ''): string => p.attrs[name] ?? fallback;
export const num = (p: Part, name: string, fallback: number): number => {
  const v = p.attrs[name];
  if (v === undefined) return fallback;
  const n = v.startsWith('0x') ? parseInt(v.slice(2), 16) : Number(v);
  return Number.isFinite(n) ? n : fallback;
};
export const bitsOf = (p: Part, name = 'width', fallback = 1): number => num(p, name, fallback);

// ---- the east-facing frame ------------------------------------------------------------

/* A part drawn facing east around its location: `t` turns the drawing to the
   part's facing, `box` is the engine's bounds seen from that frame, `port(i)`
   a port seen from it. */
export interface Frame { t: Turn; box: Box; facing: Facing; port(i: number): Point }

export function frame(p: Part, facing: Facing | null = p.facing): Frame {
  const f: Facing = facing ?? 'east';
  const t = turn(f, p.loc[0], p.loc[1]);
  const back = (x: number, y: number): Point => {
    // the inverse turn: (x, y) − loc, turned back
    const dx = x - p.loc[0], dy = y - p.loc[1];
    return [Math.round(dx * t.cos + dy * t.sin), Math.round(-dx * t.sin + dy * t.cos)];
  };
  const [bx, by, bw, bh] = p.bounds;
  const a = back(bx, by), b = back(bx + bw, by + bh);
  return {
    t, facing: f,
    box: { x0: Math.min(a[0], b[0]), y0: Math.min(a[1], b[1]), x1: Math.max(a[0], b[0]), y1: Math.max(a[1], b[1]) },
    port: (i) => { const q = p.ports[i]; return q ? back(q.loc[0], q.loc[1]) : [0, 0]; },
  };
}

export const world = (f: Frame, x: number, y: number): Point => apply(f.t, x, y);

// ---- port marks --------------------------------------------------------------------------

export function portMarks(p: Part, st: PartState): PortShape[] {
  return p.ports.map((q) => ({ k: 'port', role: 'port', x: q.loc[0], y: q.loc[1], i: q.i, dir: q.dir, bits: q.width, value: st.value(q.i) }));
}

// A line from a port to the body, in the port's value colour (drawn as wide as a wire of `bits`).
export function stub(from: Point, to: Point, value: string | undefined, bits?: number): Shape {
  return path(new Path().M(from[0], from[1]).L(to[0], to[1]), { role: 'stub', stroke: { value: value ?? '' }, cap: 'butt', bits: bits ?? value?.length ?? 1 });
}

// ---- recurring bits --------------------------------------------------------------------------

export const box = (x: number, y: number, w: number, h: number, r = 3, fill: Paint = 'body', stroke: Paint = 'bodyStroke', width = OUTLINE): Shape =>
  ({ k: 'rect', role: 'body', x, y, w, h, r, fill, stroke, width });

// A box that fills the engine's bounds, its outline inside them.
export function boundsBox(p: Part, r = 3, fill: Paint = 'body'): Shape {
  const [x, y, w, h] = p.bounds;
  return box(x + OUTLINE / 2, y + OUTLINE / 2, w - OUTLINE, h - OUTLINE, r, fill);
}

export function text(x: number, y: number, s: string, o: Partial<TextShape> = {}): TextShape {
  return { k: 'text', role: 'deco', x, y, text: s, font: 'ui', size: 10, anchor: 'middle', baseline: 'middle', fill: 'ink', ...o };
}

/* The clock input's mark on a box edge, at `at` on the edge, `inward` a unit
   vector into the box: a triangle for a rising edge (Logisim's mark), a
   triangle and a small circle past its tip for a falling edge, and for a
   level trigger no triangle -- only the circle when it is active low. */
export function clockMark(at: Point, inward: Point, trigger = 'rising'): Shape[] {
  const [x, y] = at, [ix, iy] = inward;
  const px = -iy, py = ix; // along the edge
  const out: Shape[] = [];
  if (trigger === 'rising' || trigger === 'falling') {
    const s = 5;
    const tri = new Path().M(x + px * s, y + py * s).L(x + ix * 6, y + iy * 6).L(x - px * s, y - py * s);
    out.push(path(tri, { role: 'deco', stroke: 'bodyStroke', width: 1.2 }));
  }
  if (trigger === 'falling' || trigger === 'low') {
    const d = trigger === 'falling' ? 8.5 : 3;
    out.push({ k: 'ellipse', role: 'deco', cx: x + ix * d, cy: y + iy * d, rx: 2.2, ry: 2.2, stroke: 'bodyStroke', width: 1.2, fill: 'body' });
  }
  return out;
}

// ---- values as text ---------------------------------------------------------------------------

// Hexadecimal, as many digits as the bits need; a digit with a floating bit
// is 'x', with an error bit 'E' (Logisim's rule).
export function hex(v: string | undefined, bits: number): string {
  if (!v) return '?'.repeat(Math.max(1, Math.ceil(bits / 4)));
  const padded = v.padStart(Math.ceil(v.length / 4) * 4, '0');
  let out = '';
  for (let i = 0; i < padded.length; i += 4) {
    const nib = padded.slice(i, i + 4);
    if (nib.includes('E')) out += 'E';
    else if (/[^01]/.test(nib)) out += 'x';
    else out += parseInt(nib, 2).toString(16);
  }
  return out;
}

export function dec(v: string | undefined, signed: boolean): string {
  if (!v) return '?';
  if (v.includes('E')) return 'E';
  if (/[^01]/.test(v)) return 'x';
  let n = BigInt(`0b${v}`);
  if (signed && v.length > 1 && v[0] === '1') n -= 1n << BigInt(v.length);
  return n.toString();
}

export function bin(v: string | undefined, bits: number): string {
  const s = v ?? '?'.repeat(bits);
  let out = '';
  for (let i = 0; i < s.length; i++) {
    if (i > 0 && (s.length - i) % 4 === 0) out += ' ';
    out += s[i];
  }
  return out;
}

// The bit ranges an arm of a splitter carries, as [hi:lo] (v1's arm labels).
export function ranges(bits: number[]): string {
  if (!bits.length) return '';
  const sorted = [...bits].sort((a, b) => b - a);
  const parts: string[] = [];
  let hi = sorted[0], lo = sorted[0];
  for (const b of sorted.slice(1)) {
    if (b === lo - 1) lo = b;
    else { parts.push(hi === lo ? `${hi}` : `${hi}:${lo}`); hi = b; lo = b; }
  }
  parts.push(hi === lo ? `${hi}` : `${hi}:${lo}`);
  return `[${parts.join(',')}]`;
}
