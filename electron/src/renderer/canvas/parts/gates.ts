/* Gates (Logisim 2.7.1 Gates library): AND, OR, NAND, NOR, XOR, XNOR, the
   parity gates, NOT, Buffer, Controlled Buffer and Inverter.

   The shapes are Logisim's "shaped" gates, redrawn: the same body sizes
   (the Size attribute, 30 / 50 / 70), negation bubbles, the XOR's second
   curve, the long back line or wings when the inputs outgrow the body, and
   an input line from every port to the body (Logisim's input lines).  All
   inside the engine's bounds; the ports are the engine's. */

import { Path, path, type Point, type Shape, turnAll } from '../shapes.ts';
import { attr, frame, type Frame, GATE_OUTLINE, num, type Part, type PartState, portMarks, stub, text } from './common.ts';

type Kind = 'and' | 'or' | 'xor' | 'odd' | 'even';

const GATES: Record<string, { kind: Kind; negOut: boolean }> = {
  'AND Gate': { kind: 'and', negOut: false },
  'NAND Gate': { kind: 'and', negOut: true },
  'OR Gate': { kind: 'or', negOut: false },
  'NOR Gate': { kind: 'or', negOut: true },
  'XOR Gate': { kind: 'xor', negOut: false },
  'XNOR Gate': { kind: 'xor', negOut: true },
  'Odd Parity': { kind: 'odd', negOut: false },
  'Even Parity': { kind: 'even', negOut: false },
};
export const GATE_NAMES = Object.keys(GATES);

const BUBBLE = 4.5;

// The OR shield's bulge (Logisim PainterShaped: control points 8, 13 and 20
// units in front of the back for sizes 30, 50 and 70).
const bulgeOf = (s: number) => (s < 40 ? 8 : s < 60 ? 13 : 20);

// How far the curved back (the main shield or its wings) is in front of x = −s
// at height y (east frame, the shield of an OR of size s whose inputs span h).
export function shieldAt(s: number, h: number, y: number): number {
  const half = s / 2;
  if (Math.abs(y) <= half) {
    const t = (y + half) / s;
    return 2 * t * (1 - t) * bulgeOf(s);
  }
  if (h <= s) return 0;
  const wing = (h - s) / 2;
  const dx = Math.min(20, wing / 4);
  const t = (Math.abs(y) - half) / wing; // 0 at the main shield, 1 at the far end
  const u = 1 - Math.min(1, Math.max(0, t));
  return 2 * u * (1 - u) * dx;
}

function shield(x: number, s: number, h: number): Path {
  const half = s / 2, b = bulgeOf(s);
  const p = new Path();
  if (h > s) {
    const dx = Math.min(20, (h - s) / 8);
    p.M(x, -h / 2).Q(x + dx, -(s + h) / 4, x, -half);
  } else {
    p.M(x, -half);
  }
  p.Q(x + b, 0, x, half);
  if (h > s) {
    const dx = Math.min(20, (h - s) / 8);
    p.Q(x + dx, (s + h) / 4, x, h / 2);
  }
  return p;
}

const bubble = (cx: number, cy: number): Shape => ({ k: 'ellipse', role: 'body', cx, cy, rx: BUBBLE, ry: BUBBLE, fill: 'body', stroke: 'bodyStroke', width: GATE_OUTLINE * 0.8 });

export function drawGate(p: Part, st: PartState): Shape[] {
  const g = GATES[p.name] ?? { kind: 'and' as Kind, negOut: false };
  const f = frame(p);
  const H = f.box.y1 - f.box.y0;
  const s = num(p, 'size', 50);
  const inputs = p.ports.length - 1;
  const negated = Array.from({ length: inputs }, (_, i) => attr(p, `negate${i}`) === 'true');
  const out = g.negOut ? 10 : 0;

  const front = -out;
  const back = front - s;                      // the main body's back (x)
  const half = s / 2;
  const tall = H > s;
  const local: Shape[] = [];
  const style = { role: 'body' as const, fill: 'body', stroke: 'bodyStroke', width: GATE_OUTLINE };

  // Where an input line ends: the body's back at that height.
  let backAt: (y: number) => number;
  if (g.kind === 'and') {
    const body = new Path().M(back + half, -half).L(back, -half).L(back, half).L(back + half, half).A(back + half, 0, half, Math.PI / 2, -Math.PI / 2, true).Z();
    local.push(path(body, style));
    if (tall) local.push(path(new Path().M(back, -H / 2 + 1).L(back, H / 2 - 1), { role: 'body', stroke: 'bodyStroke', width: GATE_OUTLINE }));
    backAt = () => back;
  } else if (g.kind === 'odd' || g.kind === 'even') {
    const top = -H / 2 + GATE_OUTLINE / 2, bottom = H / 2 - GATE_OUTLINE / 2;
    local.push({ k: 'rect', ...style, x: back, y: top, w: s - GATE_OUTLINE / 2, h: bottom - top, r: 3 });
    local.push(text(back + s / 2, 0, g.kind === 'odd' ? '2k+1' : '2k', { size: Math.min(11, s / 3.2), weight: 600, fill: 'ink2' }));
    backAt = () => back;
  } else {
    // OR: two curves from the tip to the back corners and the shield between
    // (Logisim PainterShaped PATH_NARROW / MEDIUM / WIDE)
    const tipCtl = s < 40 ? 10 : s < 60 ? 20 : 25;
    const body = new Path().M(front, 0).Q(front - tipCtl, -half, back, -half)
      .Q(back + bulgeOf(s), 0, back, half).Q(front - tipCtl, half, front, 0).Z();
    local.push(path(body, style));
    if (tall) local.push(path(shield(back, s, H), { role: 'body', stroke: 'bodyStroke', width: GATE_OUTLINE }));
    backAt = (y) => back + shieldAt(s, H, y);
    if (g.kind === 'xor') {
      local.push(path(shield(back - 10, s, H), { role: 'body', stroke: 'bodyStroke', width: GATE_OUTLINE }));
      backAt = (y) => back - 10 + shieldAt(s, H, y);
    }
  }
  // Input lines from the ports to the back (the bubble in the last 10 units when negated).
  const shapes: Shape[] = [];
  const reach = Math.max(half, tall ? H / 2 - 1 : half);   // how far up and down the back goes
  for (let i = 0; i < inputs; i++) {
    const [px, py0] = f.port(i + 1);
    const v = st.value(i + 1), bits = p.ports[i + 1].width;
    // Logisim puts the fourth input of a size-70 gate below its own bounds: a short line up to the back
    const py = Math.max(-reach, Math.min(reach, py0));
    if (py !== py0) shapes.push(stubLocal(f, [px, py0], [px, py], v, bits));
    const end = Math.min(0, Math.max(px, backAt(py) - (negated[i] ? 2 * BUBBLE + 1 : 0)));
    if (end > px + 0.01) shapes.push(stubLocal(f, [px, py], [end, py], v, bits));
    if (negated[i]) local.push(bubble(end + BUBBLE, py));
  }
  if (g.negOut) local.push(bubble(-BUBBLE, 0));
  return [...shapes, ...turnAll(f.t, local), ...portMarks(p, st)];
}

function stubLocal(f: Frame, a: Point, b: Point, v: string | undefined, bits: number): Shape {
  return turnAll(f.t, [stub(a, b, v, bits)])[0];
}

// ---- NOT, Buffer, the controlled ones ---------------------------------------------------------

export function drawNot(p: Part, st: PartState, inverter = true, control = false): Shape[] {
  const f = frame(p);
  const W = -f.box.x0;
  const r = W >= 30 ? BUBBLE : 3.5;
  const tip = inverter ? -2 * r : 0;
  const h = W >= 30 ? 8 : control ? 7 : 6.5;
  const local: Shape[] = [];
  local.push(path(new Path().M(-W, -h).L(tip, 0).L(-W, h).Z(), { role: 'body', fill: 'body', stroke: 'bodyStroke', width: GATE_OUTLINE }));
  if (inverter) local.push({ k: 'ellipse', role: 'body', cx: -r, cy: 0, rx: r, ry: r, fill: 'body', stroke: 'bodyStroke', width: GATE_OUTLINE * 0.8 });
  const shapes: Shape[] = [];
  if (control && p.ports.length >= 3) {
    // the control port (the last) to the triangle's edge on its side
    const i = p.ports.length - 1;
    const [cx, cy] = f.port(i);
    const along = Math.min(1, Math.max(0, (cx + W) / (tip + W)));
    const edge = Math.sign(cy) * h * (1 - along);
    shapes.push(stubLocal(f, [cx, cy], [cx, edge], st.value(i), p.ports[i].width));
  }
  return [...shapes, ...turnAll(f.t, local), ...portMarks(p, st)];
}

export const drawBuffer = (p: Part, st: PartState) => drawNot(p, st, false);
export const drawControlledBuffer = (p: Part, st: PartState) => drawNot(p, st, false, true);
export const drawControlledInverter = (p: Part, st: PartState) => drawNot(p, st, true, true);
