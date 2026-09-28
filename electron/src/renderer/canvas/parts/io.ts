/* Input/Output (Logisim 2.7.1): Button, LED, 7-Segment Display, Hex Digit
   Display.  Their colour attributes (color, offcolor, bg) are the
   student's and are drawn as they are. */

import type { Shape } from '../shapes.ts';
import { valueKind } from '../tokens.ts';
import { attr, OUTLINE, type Part, type PartState, portMarks } from './common.ts';

// '#rrggbb' or '#rrggbbaa' (Logisim writes the alpha last) → a paint.
function rgb(c: string, fallback: string): { rgb: string; alpha?: number } {
  const m = /^#([0-9a-f]{6})([0-9a-f]{2})?$/i.exec(c);
  if (!m) return { rgb: fallback };
  return m[2] === undefined ? { rgb: `#${m[1]}` } : { rgb: `#${m[1]}`, alpha: parseInt(m[2], 16) / 255 };
}

export function drawButton(p: Part, st: PartState): Shape[] {
  const [x, y, w, h] = p.bounds;
  const pressed = st.value(0) === '1';
  const face = rgb(attr(p, 'color', '#ffffff'), '#ffffff');
  const d = pressed ? 2.5 : 0;
  return [
    { k: 'rect', role: 'body', x: x + OUTLINE / 2, y: y + OUTLINE / 2, w: w - OUTLINE, h: h - OUTLINE, r: 4, fill: 'bodySoft', stroke: 'bodyStroke', width: OUTLINE },
    { k: 'rect', role: 'deco', x: x + 3.5 + d / 2, y: y + 3 + d, w: w - 7 - d, h: h - 7.5 - d, r: 3, fill: pressed ? 'selectTint' : face, stroke: pressed ? 'blue' : 'dim', width: 1.1 },
    ...portMarks(p, st),
  ];
}

export function drawLed(p: Part, st: PartState): Shape[] {
  const [x, y, w, h] = p.bounds;
  const v = st.value(0);
  const active = attr(p, 'active', 'true') === 'true';
  const on = valueKind(v) === (active ? 'one' : 'zero');
  const lit = rgb(attr(p, 'color', '#f00000'), '#f00000');
  const off = rgb(attr(p, 'offcolor', '#404040'), '#404040');
  const r = Math.min(w, h) / 2 - OUTLINE / 2;
  const bad = valueKind(v) === 'error' || valueKind(v) === 'float';
  const out: Shape[] = [{ k: 'ellipse', role: 'body', cx: x + w / 2, cy: y + h / 2, rx: r, ry: r, fill: on ? lit : off, stroke: bad ? { value: v ?? '' } : 'bodyStroke', width: OUTLINE, dash: bad ? [2.5, 2] : undefined }];
  if (on) out.push({ k: 'ellipse', role: 'deco', cx: x + w / 2 - r * 0.32, cy: y + h / 2 - r * 0.32, rx: r * 0.28, ry: r * 0.22, fill: { rgb: '#ffffff', alpha: 0.55 } });
  return [...out, ...portMarks(p, st)];
}

// ---- seven segments -----------------------------------------------------------------------------------

// Segments a … g of a digit in a box (x, y, w, h), as rounded bars.
function segments(x: number, y: number, w: number, h: number): Record<string, [number, number, number, number]> {
  const t = Math.max(2.5, w * 0.16);          // thickness
  const l = x, r = x + w, top = y, mid = y + h / 2, bot = y + h;
  const hw = w - t * 1.2;                       // horizontal bar length
  const vh = h / 2 - t * 1.2;                   // vertical bar length
  return {
    a: [l + t * 0.6, top, hw, t],
    g: [l + t * 0.6, mid - t / 2, hw, t],
    d: [l + t * 0.6, bot - t, hw, t],
    f: [l, top + t * 0.6, t, vh],
    b: [r - t, top + t * 0.6, t, vh],
    e: [l, mid + t * 0.6, t, vh],
    c: [r - t, mid + t * 0.6, t, vh],
  };
}

// Hex digits' segments (a … g).
export const HEX_SEGMENTS = ['abcdef', 'bc', 'abdeg', 'abcdg', 'bcfg', 'acdfg', 'acdefg', 'abc', 'abcdefg', 'abcdfg', 'abcefg', 'cdefg', 'adef', 'bcdeg', 'adefg', 'aefg'];

function display(p: Part, lit: Set<string>, dp: boolean, unknown: boolean): Shape[] {
  const [x, y, w, h] = p.bounds;
  const on = rgb(attr(p, 'color', '#f00000'), '#f00000');
  const off = rgb(attr(p, 'offcolor', '#dcdcdc'), '#dcdcdc');
  const bg = rgb(attr(p, 'bg', '#ffffff00'), '#ffffff');
  const out: Shape[] = [
    { k: 'rect', role: 'body', x: x + OUTLINE / 2, y: y + OUTLINE / 2, w: w - OUTLINE, h: h - OUTLINE, r: 3, fill: bg.alpha === 0 ? 'body' : bg, stroke: 'bodyStroke', width: OUTLINE },
  ];
  const dx = x + 8, dy = y + 8, dw = w - 19, dh = h - 16;
  for (const [name, [sx, sy, sw, sh]] of Object.entries(segments(dx, dy, dw, dh))) {
    out.push({ k: 'rect', role: 'deco', x: sx, y: sy, w: sw, h: sh, r: Math.min(sw, sh) / 2, fill: lit.has(name) ? on : off });
  }
  out.push({ k: 'ellipse', role: 'deco', cx: x + w - 6.5, cy: y + h - 9, rx: 2.2, ry: 2.2, fill: dp ? on : off });
  if (unknown) out.push({ k: 'rect', role: 'deco', x: dx - 2, y: dy - 2, w: dw + 4, h: dh + 4, r: 2, stroke: 'vFloat', width: 1, dash: [2.5, 2] });
  return out;
}

export function drawSevenSegment(p: Part, st: PartState): Shape[] {
  const active = attr(p, 'active', 'true') === 'true';
  const want = active ? '1' : '0';
  const names = ['a', 'b', 'c', 'd', 'e', 'f', 'g'];
  const lit = new Set(names.filter((_, i) => st.value(i) === want));
  const unknown = names.some((_, i) => { const k = valueKind(st.value(i)); return k === 'float' || k === 'error'; });
  return [...display(p, lit, st.value(7) === want, unknown), ...portMarks(p, st)];
}

export function drawHexDigit(p: Part, st: PartState): Shape[] {
  const v = st.value(0);
  const known = v !== undefined && /^[01]+$/.test(v);
  const lit = new Set(known ? HEX_SEGMENTS[parseInt(v, 2) & 15] : '');
  const unknown = v !== undefined && !known;
  return [...display(p, lit, st.value(1) === '1', unknown), ...portMarks(p, st)];
}
