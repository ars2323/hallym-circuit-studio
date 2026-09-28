/* Wiring (Logisim 2.7.1): Splitter, Pin, Probe, Tunnel, Clock, Constant,
   Pull Resistor, Ground, Power, Bit Extender.

   A splitter is drawn like wires (its spine and arms in the value colours,
   a bus as wide as a bus); its arms' bit ranges are labels (labels.ts),
   not part of the body.  Pins keep Logisim's shapes -- an input a square,
   an output round -- and show their bits in Logisim's grid; a tunnel is
   its tag in the tunnel's colour with the name inside. */

import { Path, path, type Point, type Shape, turnAll } from '../shapes.ts';
import { valueKind } from '../tokens.ts';
import { parseFont } from './base.ts';
import { attr, bitsOf, boundsBox, dec, frame, hex, num, OUTLINE, type Part, type PartState, portMarks, roughWidth, stub, text, world } from './common.ts';

// ---- Splitter ------------------------------------------------------------------------------------

// Which bits each arm carries (arm index 0 … fanout−1), from the bit0 … bitN attributes.
export function splitterArms(p: Part): number[][] {
  const fanout = num(p, 'fanout', 2);
  const incoming = num(p, 'incoming', 2);
  const arms: number[][] = Array.from({ length: fanout }, () => []);
  for (let b = 0; b < incoming; b++) {
    const v = p.attrs[`bit${b}`];
    const a = v === undefined ? Math.min(fanout - 1, Math.floor((b * fanout) / incoming)) : v === 'none' ? -1 : Number(v);
    if (a >= 0 && a < fanout) arms[a].push(b);
  }
  return arms;
}

export function drawSplitter(p: Part, st: PartState): Shape[] {
  const f = frame(p);
  const arms = p.ports.slice(1).map((q) => f.port(q.i));
  if (!arms.length) return portMarks(p, st);
  const armX = arms[0][0];
  const spineX = armX / 2;
  const ys = [0, ...arms.map((a) => a[1])];
  const y0 = Math.min(...ys), y1 = Math.max(...ys);
  const bits = p.ports[0].width;
  const local: Shape[] = [
    stub([0, 0], [spineX, 0], st.value(0), bits),
    stub([spineX, y0], [spineX, y1], st.value(0), bits),
  ];
  arms.forEach((a, k) => local.push(stub([spineX, a[1]], a as Point, st.value(k + 1), p.ports[k + 1].width)));
  return [...turnAll(f.t, local), ...portMarks(p, st)];
}

// ---- Pin ---------------------------------------------------------------------------------------------

export function drawPin(p: Part, st: PartState): Shape[] {
  const output = attr(p, 'output') === 'true';
  const bits = bitsOf(p);
  const v = st.value(0);
  const [x, y, w, h] = p.bounds;
  const out: Shape[] = [];
  // an output is round: a circle for one bit, a rounded box for more (Logisim), an input square
  const r = output ? (bits === 1 ? Math.min(w, h) / 2 - OUTLINE / 2 : Math.min(9, h / 2 - OUTLINE / 2)) : 3;
  out.push({ k: 'rect', role: 'body', x: x + OUTLINE / 2, y: y + OUTLINE / 2, w: w - OUTLINE, h: h - OUTLINE, r, fill: 'body', stroke: output ? 'bodyStroke' : 'navy', width: OUTLINE });
  if (bits === 1) {
    const cx = x + w / 2, cy = y + h / 2;
    const kind = valueKind(v);
    out.push({ k: 'ellipse', role: 'deco', cx, cy, rx: 6.2, ry: 6.2, fill: kind === 'none' ? 'bodySoft' : { value: v ?? '' } });
    out.push(text(cx, cy + 0.4, v ?? '', { font: 'code', size: 9, weight: 700, fill: kind === 'zero' || kind === 'one' || kind === 'error' || kind === 'float' ? 'white' : 'ink' }));
  } else {
    // Logisim's grid: 8 bits a row, the high bits first, a digit per 10 units
    const s = (v ?? 'x'.repeat(bits)).padStart(bits, 'x');
    const cols = Math.min(8, bits);
    const rows = Math.ceil(bits / 8);
    const cellW = (w - 4) / cols, cellH = (h - 4) / rows;
    for (let i = 0; i < bits; i++) {
      const row = Math.floor(i / 8), col = i % 8;
      const ch = s[i];
      out.push(text(x + 2 + cellW * (col + 0.5), y + 2 + cellH * (row + 0.5) + 0.5, ch, {
        font: 'code', size: Math.min(10, cellH * 0.8), weight: 600,
        fill: ch === '1' ? 'vOne' : ch === '0' ? 'ink' : ch === 'E' ? 'vError' : 'vFloat',
      }));
    }
  }
  return [...out, ...portMarks(p, st)];
}

// ---- Probe --------------------------------------------------------------------------------------------

export function probeText(v: string | undefined, radix: string): string {
  if (!v) return '?';
  switch (radix) {
    case '16': return hex(v, v.length);
    case '10signed': return dec(v, true);
    case '10unsigned': return dec(v, false);
    case '8': {
      if (/[^01]/.test(v)) return v.includes('E') ? 'E' : 'x';
      return BigInt(`0b${v}`).toString(8);
    }
    default: return v;
  }
}

export function drawProbe(p: Part, st: PartState): Shape[] {
  const [x, y, w, h] = p.bounds;
  const v = st.value(0);
  const t = probeText(v, attr(p, 'radix', '2'));
  return [
    { k: 'rect', role: 'body', x: x + OUTLINE / 2, y: y + OUTLINE / 2, w: w - OUTLINE, h: h - OUTLINE, r: Math.min(8, h / 2), fill: 'bodySoft', stroke: 'dim', width: OUTLINE },
    text(x + w / 2, y + h / 2 + 0.5, t, { font: 'code', size: 10, weight: 600, fit: w - 6, fill: valueKind(v) === 'none' ? 'muted' : valueKind(v) === 'bus' ? 'ink' : { value: v ?? '' } }),
    ...portMarks(p, st),
  ];
}

// ---- Tunnel -----------------------------------------------------------------------------------------------

// A tunnel's name: one size for one font attribute (7/8 of the labelfont's size: 10.5 for Logisim's
// default 12), the same in every tunnel (UI review of #425).
export const tunnelTextSize = (p: Part): number => parseFont(attr(p, 'labelfont', 'SansSerif plain 12')).size * 0.875;
export const TUNNEL_WEIGHT = 600;

/* The engine sizes a tunnel from a guess (half the font size a character, the original before it paints);
   a name wider than that keeps its size and the tag grows away from the point -- by as much as there is
   room (st.grow) -- and only what is still missing is made up by drawing the name narrower. */
export function drawTunnel(p: Part, st: PartState): Shape[] {
  const f = frame(p);
  const b = f.box;
  const across = f.facing === 'north' || f.facing === 'south';   // the name runs across the tag
  const label = attr(p, 'label');
  const size = tunnelTextSize(p);
  const measure = st.measure ?? roughWidth;
  const need = label ? measure(label, 'ui', size, TUNNEL_WEIGHT) + 5 : 0;
  const W = -b.x0;                        // the tag lies west of its point (east frame)
  let top = b.y0 + 1, bottom = b.y1 - 1;
  const depth = Math.min(6, (bottom - top) / 2);
  const room = across ? bottom - top : W - 1 - depth - 0.5;
  const extra = Math.max(0, Math.min(need - room, st.grow ?? Infinity));
  let back = -W + 1;
  if (across) { top -= extra / 2; bottom += extra / 2; } else back -= extra;
  const tag = new Path().M(back, top).L(-depth - 0.5, top).L(0, 0).L(-depth - 0.5, bottom).L(back, bottom).Z();
  const color = st.tunnelColor ?? '#65707e';
  const local: Shape[] = [path(tag, { role: 'body', fill: { rgb: color, alpha: 0.16 }, stroke: { rgb: color }, width: 1.4, grows: extra > 0 || undefined })];
  if (label) {
    // upright text in the middle of the tag's rectangle part (east frame: between the back and the point's start)
    const [cx, cy] = world(f, (back - depth - 0.5) / 2, 0);
    local.push(text(cx, cy + 0.5, label, { size, weight: TUNNEL_WEIGHT, fit: Math.max(6, room + extra - 3), fill: 'ink' }));
  }
  return [...turnAll(f.t, local.filter((s) => s.k !== 'text')), ...local.filter((s) => s.k === 'text'), ...portMarks(p, st)];
}

// ---- Clock, Constant, Pull Resistor, Ground, Power -----------------------------------------------------------

export function drawClock(p: Part, st: PartState): Shape[] {
  const [x, y, w, h] = p.bounds;
  const v = st.value(0);
  const cx = x + w / 2, cy = y + h / 2;
  const wave = new Path().M(cx - 6, cy + 3).L(cx - 3, cy + 3).L(cx - 3, cy - 3).L(cx + 2, cy - 3).L(cx + 2, cy + 3).L(cx + 6, cy + 3);
  return [
    boundsBox(p, 3),
    path(wave, { role: 'deco', stroke: valueKind(v) === 'none' ? 'ink2' : { value: v ?? '' }, width: 1.6, cap: 'round' }),
    ...portMarks(p, st),
  ];
}

export function constantBits(p: Part): string {
  const bits = bitsOf(p);
  const raw = attr(p, 'value', '0x0');
  const n = raw.startsWith('0x') ? BigInt(raw) : BigInt(Number(raw) || 0);
  const mask = (1n << BigInt(bits)) - 1n;
  return (n & mask).toString(2).padStart(bits, '0');
}

export function drawConstant(p: Part, st: PartState): Shape[] {
  const [x, y, w, h] = p.bounds;
  const bits = bitsOf(p);
  const v = constantBits(p);
  const digits = hex(v, bits);
  const f = frame(p);
  // the digits end next to the port (Logisim: right-aligned toward the output)
  const anchor = f.facing === 'east' ? 'end' : f.facing === 'west' ? 'start' : 'middle';
  const tx = f.facing === 'east' ? x + w - 3 : f.facing === 'west' ? x + 3 : x + w / 2;
  return [
    { k: 'rect', role: 'body', x: x + OUTLINE / 2, y: y + OUTLINE / 2, w: w - OUTLINE, h: h - OUTLINE, r: 3, fill: 'bodySoft', stroke: 'border', width: OUTLINE },
    text(tx, y + h / 2 + 0.5, digits, { font: 'code', size: 11, weight: 600, anchor, fit: w - 5, fill: bits === 1 ? { value: v } : 'ink' }),
    ...portMarks(p, st),
  ];
}

export function drawPull(p: Part, st: PartState): Shape[] {
  const f = frame(p);
  const W = -f.box.x0;
  const zig = new Path().M(0, 0).L(-4, 0);
  const n = 6, len = W - 16, step = len / n;
  for (let i = 0; i < n; i++) zig.L(-4 - step * (i + 0.5), i % 2 === 0 ? -4 : 4);
  zig.L(-4 - len, 0);
  const local: Shape[] = [path(zig, { role: 'body', stroke: 'bodyStroke', width: 1.4 })];
  const pull = attr(p, 'pull', '0');
  const shapes = turnAll(f.t, local);
  const [tx, ty] = world(f, -W + 6, 0);
  shapes.push(text(tx, ty + 0.5, pull, { font: 'code', size: 9, weight: 700, fill: pull === '1' ? 'vOne' : pull === '0' ? 'vZero' : 'vFloat' }));
  return [...shapes, ...portMarks(p, st)];
}

export function drawGround(p: Part, st: PartState): Shape[] {
  const f = frame(p);
  const L = f.box.x1;   // the symbol lies east of the port (east frame)
  const local: Shape[] = [
    stub([0, 0], [L * 0.45, 0], st.value(0)),
    path(new Path().M(L * 0.45, -7).L(L * 0.45, 7).M(L * 0.7, -4.5).L(L * 0.7, 4.5).M(L - 1, -2).L(L - 1, 2), { role: 'body', stroke: 'bodyStroke', width: 1.8, cap: 'round' }),
  ];
  return [...turnAll(f.t, local), ...portMarks(p, st)];
}

export function drawPower(p: Part, st: PartState): Shape[] {
  const f = frame(p);
  const L = f.box.x1;
  const local: Shape[] = [
    stub([0, 0], [L * 0.5, 0], st.value(0)),
    path(new Path().M(L * 0.5, -7).L(L - 1, 0).L(L * 0.5, 7).Z(), { role: 'body', fill: 'body', stroke: 'bodyStroke', width: 1.6 }),
  ];
  return [...turnAll(f.t, local), ...portMarks(p, st)];
}

// ---- Bit Extender ------------------------------------------------------------------------------------------

export function drawBitExtender(p: Part, st: PartState): Shape[] {
  const [x, y, w, h] = p.bounds;
  const type = attr(p, 'type', 'zero');
  const inW = num(p, 'in_width', 8), outW = num(p, 'out_width', 16);
  return [
    boundsBox(p, 4),
    text(x + w / 2, y + h / 2 - 5, `${inW}→${outW}`, { font: 'code', size: 8.5, weight: 600, fit: w - 6 }),
    text(x + w / 2, y + h / 2 + 7, type, { size: 8, fill: 'muted', fit: w - 6 }),
    ...portMarks(p, st),
  ];
}
