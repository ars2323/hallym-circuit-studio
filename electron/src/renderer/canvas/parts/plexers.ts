/* Plexers (Logisim 2.7.1): Multiplexer, Demultiplexer, Decoder, Priority
   Encoder, Bit Selector.

   A multiplexer is a trapezoid, wide on the data side (Logisim's shape);
   the select and enable ports reach its slanted edges with short stubs.
   The input a multiplexer passes (or the output a demultiplexer or decoder
   drives) is traced inside the body when the select value is known --
   a quiet line in the value's colour, so the path through reads at a
   glance.  Sizes and ports are the engine's. */

import { type Box, Path, path, type Point, type Shape, turnAll } from '../shapes.ts';
import { frame, type Frame, OUTLINE, type Part, type PartState, portMarks, stub, text } from './common.ts';

const INSET = 10;   // how much narrower the trapezoid's narrow side is at each end

// The trapezoid in the east frame: data side at x = xa (wide), other side x = xb (narrow).
function trapezoid(b: Box, wideLeft: boolean): { shape: Path; edgeAt: (x: number, top: boolean) => number } {
  const x0 = b.x0 + OUTLINE / 2, x1 = b.x1 - OUTLINE / 2;
  const y0 = b.y0 + OUTLINE / 2, y1 = b.y1 - OUTLINE / 2;
  const ins = Math.min(INSET, (y1 - y0) / 4);
  const pts: Point[] = wideLeft
    ? [[x0, y0], [x1, y0 + ins], [x1, y1 - ins], [x0, y1]]
    : [[x0, y0 + ins], [x1, y0], [x1, y1], [x0, y1 - ins]];
  const edgeAt = (x: number, top: boolean) => {
    const t = (x - x0) / (x1 - x0);
    const d = wideLeft ? t * ins : (1 - t) * ins;
    return top ? y0 + d : y1 - d;
  };
  return { shape: new Path().poly(pts), edgeAt };
}

const bodyStyle = { role: 'body' as const, fill: 'body', stroke: 'bodyStroke', width: OUTLINE };

// Stubs from ports on the top or bottom side (select, enable) to the slanted edge.
function sideStubs(f: Frame, p: Part, st: PartState, idx: number[], edgeAt: (x: number, top: boolean) => number): Shape[] {
  const out: Shape[] = [];
  for (const i of idx) {
    if (i < 0 || i >= p.ports.length) continue;
    const [x, y] = f.port(i);
    const top = y <= (f.box.y0 + f.box.y1) / 2;
    const e = edgeAt(x, top);
    if (Math.abs(e - y) > 0.01) out.push(...turnAll(f.t, [stub([x, y], [x, e], st.value(i), p.ports[i].width)]));
  }
  return out;
}

const selected = (v: string | undefined): number | null => (v && /^[01]+$/.test(v) ? parseInt(v, 2) : null);

// The quiet line of the passed signal, inside the body.
function route(from: Point, to: Point, value: string | undefined): Shape {
  const mid = (from[0] + to[0]) / 2;
  const d = new Path().M(from[0], from[1]).C(mid, from[1], mid, to[1], to[0], to[1]);
  return path(d, { role: 'deco', stroke: { value: value ?? '' }, width: 1.2, dash: [3, 2.5] });
}

export function drawMux(p: Part, st: PartState): Shape[] {
  const f = frame(p);
  const n = p.ports.length;
  const hasEnable = n >= 4 && (p.ports[n - 2]?.name === 'en');
  const outIdx = n - 1;
  const selIdx = hasEnable ? n - 3 : n - 2;
  const inputs = selIdx;
  const { shape, edgeAt } = trapezoid(f.box, true);
  const local: Shape[] = [path(shape, bodyStyle)];
  const sel = selected(st.value(selIdx));
  if (sel !== null && sel < inputs) local.push(route([f.box.x0 + 3, f.port(sel)[1]], [f.box.x1 - 3, f.port(outIdx)[1]], st.value(sel)));
  if (inputs <= 16) local.push(text(f.box.x0 + 5, f.port(0)[1], '0', { size: 6.5, anchor: 'start', fill: 'muted', font: 'code' }));
  return [...sideStubs(f, p, st, hasEnable ? [selIdx, selIdx + 1] : [selIdx], edgeAt), ...turnAll(f.t, local), ...portMarks(p, st)];
}

export function drawDemux(p: Part, st: PartState, decoder = false): Shape[] {
  const f = frame(p);
  // Demultiplexer: outputs, sel, [en], in.  Decoder: outputs, sel, [en].
  const named = (name: string) => p.ports.findIndex((q) => q.name === name);
  const selIdx = named('sel');
  const enIdx = named('en');
  const inIdx = decoder ? -1 : named('in');
  const outputs = selIdx;
  const { shape, edgeAt } = trapezoid(f.box, false);
  const local: Shape[] = [path(shape, bodyStyle)];
  const sel = selected(st.value(selIdx));
  if (sel !== null && sel < outputs) {
    const fromY = inIdx >= 0 ? f.port(inIdx)[1] : (f.box.y0 + f.box.y1) / 2;
    local.push(route([f.box.x0 + 3, fromY], [f.box.x1 - 3, f.port(sel)[1]], decoder ? '1' : st.value(inIdx)));
  }
  if (outputs <= 16) local.push(text(f.box.x1 - 5, f.port(0)[1], '0', { size: 6.5, anchor: 'end', fill: 'muted', font: 'code' }));
  if (decoder && f.box.x1 - f.box.x0 >= 30) local.push(text((f.box.x0 + f.box.x1) / 2, (f.box.y0 + f.box.y1) / 2, 'DEC', { size: 8, weight: 600, fill: 'ink2' }));
  // The select (and enable) ports sit on the top or bottom; a decoder's may sit on its narrow side's corner.
  return [...sideStubs(f, p, st, enIdx >= 0 ? [selIdx, enIdx] : [selIdx], edgeAt), ...turnAll(f.t, local), ...portMarks(p, st)];
}
export const drawDecoder = (p: Part, st: PartState) => drawDemux(p, st, true);

export function drawPriorityEncoder(p: Part, st: PartState): Shape[] {
  const f = frame(p);
  const b = f.box;
  const local: Shape[] = [
    { k: 'rect', ...bodyStyle, x: b.x0 + OUTLINE / 2, y: b.y0 + OUTLINE / 2, w: b.x1 - b.x0 - OUTLINE, h: b.y1 - b.y0 - OUTLINE, r: 3 },
    text((b.x0 + b.x1) / 2, b.y0 + 9, 'Pri', { size: 8, weight: 600, fill: 'ink2' }),
  ];
  return [...turnAll(f.t, local), ...portMarks(p, st)];
}

export function drawBitSelector(p: Part, st: PartState): Shape[] {
  const f = frame(p);
  const { shape, edgeAt } = trapezoid(f.box, true);
  const local: Shape[] = [path(shape, bodyStyle), text((f.box.x0 + f.box.x1) / 2 - 2, 0, 'Sel', { size: 8, weight: 600, fill: 'ink2' })];
  const selIdx = p.ports.findIndex((q) => q.name === 'sel');
  return [...sideStubs(f, p, st, [selIdx], edgeAt), ...turnAll(f.t, local), ...portMarks(p, st)];
}
