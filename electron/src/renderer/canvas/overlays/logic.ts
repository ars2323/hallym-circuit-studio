/* The overlays' rules without a screen (N-15, D-151), from v1's code and
   decisions: the bus values' text (C-08, D-079), a net's name, the
   influence's steps (P-01, D-062), the Signal Flow's time and dashes, the
   tunnel arc's shape (P-07, V-06, D-063, D-101), where the flow's labels
   and the "alu: 3 places" chips stand, and what a click on the Canvas
   starts a flow from.  The colours of the overlays are here too, so a test
   can hold them to Hallym MIPS's. */

import type { Component, FlowJump, FlowSegment, Net, Point, SignalGroup } from '../../../main/protocol.ts';
import { MIN_TEXT_PX, wirePx } from '../paint.ts';
import type { Scene } from '../scene.ts';
import { type Box, boxesMeet } from '../shapes.ts';

// ---- colours -------------------------------------------------------------------------------------

/* The instruction fields, as Hallym MIPS's Inspector colours them (shared/panels.css .f-*, N-14): the
   strong colour of each field (the panel's text colour) makes the band around a wire; the same name, the
   same colour in both programs.  Fields a MIPS32 word may have beyond these share the grey of funct. */
export const FIELD_COLORS: Record<string, string> = {
  opcode: '#00205b', fmt: '#00205b',          // --navy
  rs: '#0055a5',                              // --blue
  rt: '#00736f', ft: '#00736f',               // --teal-text
  rd: '#8a5a00', fs: '#8a5a00',               // --amber-text
  shamt: '#6b4c9a', fd: '#6b4c9a',            // --purple
  funct: '#4a5560',                           // --cp0-text
  immediate: '#1d5c63', target: '#1d5c63', offset: '#1d5c63',
};
export const fieldColor = (name: string): string => FIELD_COLORS[name] ?? '#4a5560';

/* Signal groups (v1 E-04, D-087): Okabe–Ito colours far from the value colours. */
export const GROUP_COLORS: Record<SignalGroup, string> = { control: '#e69f00', data: '#cc79a7', address: '#56b4e9' };
export const GROUP_NAMES: Record<SignalGroup, string> = { control: 'Control', data: 'Data', address: 'Address' };

export const ACTIVE_PATH_COLOR = '#00205b';   // v1 C-08: dark navy
export const INFLUENCE_FORWARD = '#0055a5';   // v1 P-01: blue ahead
export const INFLUENCE_BACKWARD = '#c77c00';  // amber behind
export const FLOW_ACCENT = '#00a9a5';         // v1 P-07: the accent (teal)
export const FLOW_ACCENT_DARK = '#00736f';

/* A band around a wire (v1 FieldOverlay, ActivePathOverlay): as wide as a grid step, so the bands of
   parallel wires do not meet; drawn under the wire, which stays in its value colour. */
export const BAND = 10;

/* The zoom from which the chips (10-unit words: labels, values, bus values) are drawn. From it on every band
   around a wire stays within BAND, so a chip at WIRE_GAP (8) from the wire's centre line keeps 3 from the band's
   edge (UI checklist 12); below it there are no chips to keep off and a band may be wider. */
export const CHIP_ZOOM = MIN_TEXT_PX / 10;
export const bandWithin = (width: number, zoom: number): number => (zoom >= CHIP_ZOOM ? Math.min(BAND, width) : width);

/* A signal group's border (v1 E-04, D-087): the group's colour on both sides of the wire with a pixel of paper
   between (`inner` is cleared), 2 px of colour each side -- thinner (1.2 px or more) where that would pass the
   band. Circuit units. */
export function groupBand(zoom: number, bits: number): { inner: number; outer: number } {
  const inner = wirePx(zoom, bits) / zoom + 2 / zoom;
  return { inner, outer: Math.max(inner + 2.4 / zoom, bandWithin(inner + 4 / zoom, zoom)) };
}

// The influence's band (v1 P-01): 9 px, not under 6 units, within BAND where chips are drawn.
export const influenceBand = (zoom: number): number => bandWithin(Math.max(6, 9 / zoom), zoom);

// ---- bus values (C-08) --------------------------------------------------------------------------

export type BusMode = 'hex' | 'dec' | 'signed' | 'off';
export const BUS_MODES: BusMode[] = ['hex', 'dec', 'signed', 'off'];
export const BUS_MODE_NAMES: Record<BusMode, string> = { hex: 'Hex', dec: 'Dec', signed: 'Signed', off: 'Off' };

/* Logisim's hex: a digit a nibble; a nibble with an error bit (read from its top bit down) is E, with a
   floating one x -- whichever comes first. */
export function hexOf(v: string): string {
  const out: string[] = [];
  const n = Math.ceil(v.length / 4);
  for (let k = n - 1; k >= 0; k--) {
    const lo = v.length - 4 * (k + 1), hi = v.length - 4 * k;   // [lo, hi) in the high-first string
    let d = 0;
    let c = '';
    for (let i = Math.max(0, lo); i < hi; i++) {
      const b = v[i];
      if (b === 'E') { c = 'E'; break; }
      if (b !== '0' && b !== '1') { c = 'x'; break; }
      d = d * 2 + (b === '1' ? 1 : 0);
    }
    out.push(c || d.toString(16));
  }
  return out.join('');
}

/* A bus's value in a chip (v1 BusValues.format): nothing for one bit, when off, or when every bit floats;
   a value not all 0/1 in Logisim's hex whatever the radix. */
export function busText(v: string | undefined, mode: BusMode): string | null {
  if (mode === 'off' || !v || v.length < 2 || !/[01E]/.test(v)) return null;
  if (!/^[01]+$/.test(v)) return `0x${hexOf(v)}`;
  const u = BigInt(`0b${v}`);
  if (mode === 'dec') return u.toString();
  if (mode === 'signed') return (v[0] === '1' ? u - (1n << BigInt(v.length)) : u).toString();
  return `0x${u.toString(16).padStart(Math.ceil(v.length / 4), '0')}`;
}

// The widest text a bus of this width shows (D2Coding: every digit is as wide): the chip's place.
export function busTemplate(width: number, mode: BusMode): string {
  if (mode === 'dec') return '0'.repeat(((1n << BigInt(width)) - 1n).toString().length);
  if (mode === 'signed') return `-${'0'.repeat((1n << BigInt(width - 1)).toString().length)}`;
  return `0x${'0'.repeat(Math.ceil(width / 4))}`;
}

/* A net's name (v1 QuickProbe.netName): a tunnel's label (first by name), else a pin that drives it (an
   input pin), else any pin on it; '' when none. */
export function netName(scene: Scene, net: Net): string {
  const tunnels: string[] = [], drivers: string[] = [], pins: string[] = [];
  for (const [id, i] of net.ports) {
    const c = scene.components.get(id);
    const label = (c?.attrs.label ?? '').trim();
    if (!c || !label || c.lib !== 'Wiring') continue;
    if (c.name === 'Tunnel') tunnels.push(label);
    else if (c.name === 'Pin') (c.ports[i]?.dir === 'out' ? drivers : pins).push(label);
  }
  for (const s of [tunnels, drivers, pins]) if (s.length) return [...s].sort()[0];
  return '';
}

// ---- influence (P-01) -------------------------------------------------------------------------

/* One step more or less of the influence (v1 InfluenceOverlay.widen): from "all" the first step less is
   the deepest; never under 1; at the end or past it, all again (-1). */
export function widen(depth: number, max: number, delta: number): number {
  const now = depth < 0 ? max : depth;
  const next = Math.max(1, now + delta);
  return next >= max ? -1 : next;
}

// ---- Signal Flow (P-07) ------------------------------------------------------------------------

export type FlowSpeed = 'slow' | 'normal' | 'fast';
export const FLOW_SPEED_PX: Record<FlowSpeed, number> = { slow: 120, normal: 240, fast: 480 };
export const FLOW_SPEED_NAMES: Record<FlowSpeed, string> = { slow: 'Slow', normal: 'Normal', fast: 'Fast' };
export const JUMP = 30;            // v1 SignalFlowPath.JUMP
export const GLOW = 60;            // a part's outline glows this far (circuit units) after the flow passed
export const DASH_PX = 8, GAP_PX = 10;
export const LABEL_FAR = [0, 12, 28, 48, 72, 104];   // screen px, nearest first
export const RING_PX = 6;

// How much of a segment the flow has lit at time t (circuit units from its start).
export const litLength = (s: Pick<FlowSegment, 'start' | 'length'>, t: number): number => Math.max(0, Math.min(s.length, t - s.start));

/* The lit dashes of a segment at time t: [a, b] pieces along it (from its start), moving in the signal's
   direction; a point a is on when (t − start − a) mod period < dash. */
export function dashes(s: Pick<FlowSegment, 'start' | 'length'>, t: number, period: number, dash: number): [number, number][] {
  const lit = litLength(s, t);
  if (lit <= 0 || period <= 0) return [];
  const out: [number, number][] = [];
  const first = (((t - s.start) % period) + period) % period;
  for (let a0 = first - period; a0 < lit; a0 += period) {
    const lo = Math.max(0, a0 - dash), hi = Math.min(lit, a0);
    if (hi > lo) out.push([lo, hi]);
  }
  return out;
}

export const along = (from: Point, to: Point, a: number): Point => {
  const len = Math.abs(from[0] - to[0]) + Math.abs(from[1] - to[1]);
  if (len === 0) return [from[0], from[1]];
  return [from[0] + ((to[0] - from[0]) / len) * a, from[1] + ((to[1] - from[1]) / len) * a];
};

/* A tunnel jump's arc (v1 FlowPainter.control, V-06): eight control points in a fixed order -- above,
   below, left, right of the two tunnels, then each twice as high -- the rise a quarter of the distance
   (10 to 60 units). */
export function arcRise(j: Pick<FlowJump, 'from' | 'to'>): number {
  const d = Math.abs(j.from[0] - j.to[0]) + Math.abs(j.from[1] - j.to[1]);
  return Math.max(10, Math.min(60, Math.floor(d / 4)));
}
export function arcControl(j: Pick<FlowJump, 'from' | 'to'>, candidate: number): Point {
  const mx = (j.from[0] + j.to[0]) / 2, my = (j.from[1] + j.to[1]) / 2;
  const rise = arcRise(j) * (candidate >= 4 ? 2 : 1);
  switch (candidate % 4) {
    case 0: return [mx, Math.min(j.from[1], j.to[1]) - rise];
    case 1: return [mx, Math.max(j.from[1], j.to[1]) + rise];
    case 2: return [Math.min(j.from[0], j.to[0]) - rise, my];
    default: return [Math.max(j.from[0], j.to[0]) + rise, my];
  }
}
export const quad = (a: Point, c: Point, b: Point, u: number): Point =>
  [(1 - u) * (1 - u) * a[0] + 2 * (1 - u) * u * c[0] + u * u * b[0], (1 - u) * (1 - u) * a[1] + 2 * (1 - u) * u * c[1] + u * u * b[1]];
const inBox = (b: Box, p: Point) => p[0] >= b.x0 && p[0] <= b.x1 && p[1] >= b.y0 && p[1] <= b.y1;

/* The candidate that covers the fewest blockers (39 points along the arc); the first on a tie (the arc
   bulging up), and it stops at the first that covers none. */
export function chooseArc(j: Pick<FlowJump, 'from' | 'to'>, blockers: Box[]): Point {
  let best: Point | null = null, bestHits = Infinity;
  for (let c = 0; c < 8; c++) {
    const ctrl = arcControl(j, c);
    let hits = 0;
    for (let i = 1; i < 40; i++) {
      const p = quad(j.from, ctrl, j.to, i / 40);
      if (blockers.some((b) => inBox(b, p))) hits++;
    }
    if (hits < bestHits) { bestHits = hits; best = ctrl; }
    if (hits === 0) break;
  }
  return best!;
}

/* The parts' bodies an arc between two tunnels keeps off: every part (its bounds and 2) but the ones at
   either end, and the chips. */
export function arcBlockers(j: Pick<FlowJump, 'from' | 'to'>, parts: Component[], chips: Box[]): Box[] {
  const out: Box[] = [];
  for (const c of parts) {
    const [x, y, w, h] = c.bounds;
    const b = { x0: x - 2, y0: y - 2, x1: x + w + 2, y1: y + h + 2 };
    if (inBox(b, j.from) || inBox(b, j.to)) continue;
    out.push(b);
  }
  return [...out, ...chips];
}

// The ring's radius (circuit units): the screen size up to 100 %, a little bigger past it (v1).
export const ringRadius = (px: number, zoom: number): number => px / zoom + (zoom > 1 ? 2.5 * (1 - 1 / zoom) : 0);

// ---- placing chips -------------------------------------------------------------------------------

export interface Weighted { box: Box; weight: number }

const overlap = (a: Box, b: Box): number => Math.max(0, Math.min(a.x1, b.x1) - Math.max(a.x0, b.x0)) * Math.max(0, Math.min(a.y1, b.y1) - Math.max(a.y0, b.y0));

/* The "alu: 3 places" chip (v1 InfluenceOverlay.placesAtWeighted): ten places around the part in order;
   the first that covers nothing, else the one covering the least (area × weight: chips and links 10,
   wires and other parts 3). */
export function placesAt(body: Box, w: number, h: number, gap: number, obstacles: Weighted[]): Box {
  const right = body.x1 - w, left = body.x0, above = body.y0 - gap - h, below = body.y1 + gap;
  const tries: Point[] = [
    [right, above], [left, above], [right, below], [left, below],
    [right, above - h - gap], [left, above - h - gap], [right, below + h + gap], [left, below + h + gap],
    [body.x1 + gap, body.y0], [body.x0 - gap - w, body.y0],
  ];
  let best = tries[0], score = Infinity;
  for (const t of tries) {
    const r = { x0: t[0] - 2, y0: t[1] - 2, x1: t[0] + w + 2, y1: t[1] + h + 2 };
    let s = 0;
    for (const o of obstacles) s += overlap(r, o.box) * o.weight;
    if (s === 0) return { x0: t[0], y0: t[1], x1: t[0] + w, y1: t[1] + h };
    if (s < score) { score = s; best = t; }
  }
  return { x0: best[0], y0: best[1], x1: best[0] + w, y1: best[1] + h };
}

/* An endpoint's label (v1 FlowPainter.layout): eight places around the ring (upper right, lower right,
   upper left, lower left, above, below, right, left), nearest first, then further out; the first that
   covers no obstacle (parts, chips, labels placed before, wires) and whose leader line crosses no label
   placed before; none: no label (the ring alone). */
export function labelPlace(at: Point, w: number, h: number, ring: number, zoom: number, hard: Box[], placed: Box[]): Box | null {
  for (const farPx of LABEL_FAR) {
    const r = ring + 3 / zoom + farPx / zoom;
    const cands: Point[] = [
      [at[0] + r, at[1] - r - h], [at[0] + r, at[1] + r], [at[0] - r - w, at[1] - r - h], [at[0] - r - w, at[1] + r],
      [at[0] - w / 2, at[1] - r - h], [at[0] - w / 2, at[1] + r], [at[0] + r, at[1] - h / 2], [at[0] - r - w, at[1] - h / 2],
    ];
    for (const c of cands) {
      const box = { x0: c[0], y0: c[1], x1: c[0] + w, y1: c[1] + h };
      if (hard.some((o) => boxesMeet(o, box)) || placed.some((o) => boxesMeet(o, box))) continue;
      const lx = Math.max(box.x0, Math.min(at[0], box.x1)), ly = Math.max(box.y0, Math.min(at[1], box.y1));
      if (Math.hypot(lx - at[0], ly - at[1]) > ring + 8 / zoom && placed.some((o) => segmentMeetsBox(at, [lx, ly], o))) continue;
      return box;
    }
  }
  return null;
}

// Whether the segment a–b passes through the box (sampled finely enough for chips).
export function segmentMeetsBox(a: Point, b: Point, box: Box): boolean {
  const n = Math.max(2, Math.ceil(Math.hypot(b[0] - a[0], b[1] - a[1])));
  for (let i = 0; i <= n; i++) {
    const p: Point = [a[0] + ((b[0] - a[0]) * i) / n, a[1] + ((b[1] - a[1]) * i) / n];
    if (inBox(box, p)) return true;
  }
  return false;
}

// ---- where a click starts a flow (v1 FlowController.target) ----------------------------------

// A component's output port within 5 units of the point (-1: none -- its body: all its outputs).
export function outputNear(c: Component, at: Point): number {
  for (const q of c.ports) if (q.dir !== 'in' && Math.abs(q.loc[0] - at[0]) <= 5 && Math.abs(q.loc[1] - at[1]) <= 5) return q.i;
  return -1;
}

export type FlowTarget = { componentId: string; port: number } | { wire: string; at: Point } | null;

/* A part under the point (an output port near: that port only), else a wire, else an output port just
   outside a body; nothing: an empty place (the flow stops). */
export function flowTarget(scene: Scene, at: Point, partAt: (p: Point) => string | null, wireAt: (p: Point) => string | null): FlowTarget {
  const part = partAt(at);
  if (part) {
    const c = scene.components.get(part)!;
    return { componentId: part, port: outputNear(c, at) };
  }
  const wire = wireAt(at);
  if (wire) return { wire, at: [Math.round(at[0] / 10) * 10, Math.round(at[1] / 10) * 10] };
  for (const c of scene.components.values()) {
    const i = outputNear(c, at);
    if (i >= 0) return { componentId: c.id, port: i };
  }
  return null;
}

// ---- area memos (E-08) ------------------------------------------------------------------------

// Where a new memo's box starts: around the chosen parts (a margin of 20, on the grid), else 200×120 at the point (v1).
export function memoStart(scene: Scene, at: [number, number], ids: string[]): { x: number; y: number; w: number; h: number } {
  let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity;
  for (const id of ids) {
    const c = scene.components.get(id);
    const w = scene.wires.get(id);
    if (c) { x0 = Math.min(x0, c.bounds[0]); y0 = Math.min(y0, c.bounds[1]); x1 = Math.max(x1, c.bounds[0] + c.bounds[2]); y1 = Math.max(y1, c.bounds[1] + c.bounds[3]); }
    if (w) { x0 = Math.min(x0, w.a[0], w.b[0]); y0 = Math.min(y0, w.a[1], w.b[1]); x1 = Math.max(x1, w.a[0], w.b[0]); y1 = Math.max(y1, w.a[1], w.b[1]); }
  }
  const snap = (v: number) => Math.floor(v / 10) * 10, up = (v: number) => Math.ceil(v / 10) * 10;
  if (!(x1 > x0 && y1 > y0)) return { x: snap(at[0] - 100), y: snap(at[1] - 60), w: 200, h: 120 };
  const x = snap(x0 - 20), y = snap(y0 - 20);
  return { x, y, w: up(x1 + 20) - x, h: up(y1 + 20) - y };
}

