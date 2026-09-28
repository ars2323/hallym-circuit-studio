/* Arithmetic (Logisim 2.7.1): Adder, Subtractor, Multiplier, Divider,
   Negator, Comparator, Shifter, Bit Adder, Bit Finder -- Logisim's 40 × 40
   box with the operation's sign in it.  The port names (a, b, c in …) are
   drawn outside on hover or from 200 % (labels.ts, v1 S-06). */

import type { Shape } from '../shapes.ts';
import { attr, boundsBox, type Part, type PartState, portMarks, text } from './common.ts';

const SIGN: Record<string, string> = {
  Adder: '+', Subtractor: '−', Multiplier: '×', Divider: '÷', Negator: '−x', BitAdder: '#1',
};
const SHIFT: Record<string, string> = { ll: '<<', lr: '>>', ar: '>>>', rl: 'rotl', rr: 'rotr' };
const FIND: Record<string, string> = { low1: 'low 1', high1: 'high 1', low0: 'low 0', high0: 'high 0' };

export function drawArithmetic(p: Part, st: PartState): Shape[] {
  const [x, y, w, h] = p.bounds;
  const cx = x + w / 2, cy = y + h / 2;
  const out: Shape[] = [boundsBox(p, 4)];
  if (p.name === 'Comparator') {
    // its three outputs: >, =, < (Logisim's order: gt, eq, lt)
    for (const [i, sign] of [[2, '>'], [3, '='], [4, '<']] as const) {
      const q = p.ports[i];
      if (q) out.push(text(x + w - 6, q.loc[1] + 0.5, sign, { font: 'code', size: 9, weight: 700, anchor: 'end', fill: 'ink2' }));
    }
    out.push(text(cx - 5, cy, 'cmp', { size: 9, weight: 600, fill: 'muted' }));
  } else if (p.name === 'Shifter') {
    out.push(text(cx, cy + 0.5, SHIFT[attr(p, 'shift', 'll')] ?? '<<', { font: 'code', size: 11, weight: 700, fit: w - 8 }));
  } else if (p.name === 'BitFinder') {
    out.push(text(cx, cy - 4, 'find', { size: 8, weight: 600, fill: 'muted' }));
    out.push(text(cx, cy + 6, FIND[attr(p, 'type', 'low1')] ?? '', { size: 8.5, weight: 600, fit: w - 6 }));
  } else {
    const sign = SIGN[p.name] ?? '?';
    out.push(text(cx, cy + 0.5, sign, { size: sign.length > 1 ? 12 : 17, weight: 600, fill: 'ink' }));
  }
  return [...out, ...portMarks(p, st)];
}
