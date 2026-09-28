/* Base (Logisim 2.7.1): Text -- the student's words on the circuit, in the
   Canvas's font (Pretendard for Logisim's SansSerif and Serif, D2Coding for
   Monospaced), at the size and alignment the attributes say, fitted into
   the bounds the engine gives. */

import type { Shape } from '../shapes.ts';
import { attr, type Part, type PartState, text } from './common.ts';

// A Logisim font attribute ("SansSerif plain 12", "Monospaced bold 10").
export function parseFont(s: string): { font: 'ui' | 'code'; size: number; weight: number } {
  const m = /^(.*?)\s+(plain|bold|italic|bolditalic)\s+(\d+)$/.exec(s.trim());
  const family = m ? m[1] : 'SansSerif';
  const style = m ? m[2] : 'plain';
  const size = m ? Number(m[3]) : 12;
  return { font: /mono/i.test(family) ? 'code' : 'ui', size, weight: style.includes('bold') ? 700 : 400 };
}

export function drawText(p: Part, _st: PartState): Shape[] {
  const s = attr(p, 'text');
  if (!s) return [];
  const f = parseFont(attr(p, 'font', 'SansSerif plain 12'));
  const h = attr(p, 'halign', 'center');
  const v = attr(p, 'valign', 'base');
  const w = p.bounds[2];
  // Logisim's bounds of a baseline-aligned text end at the baseline: the descenders sit a
  // fifth of the size higher so they stay inside (0.2 × 12 = 2.4 units at the default size).
  const y = v === 'base' ? p.loc[1] - f.size * 0.2 : p.loc[1];
  return [text(p.loc[0], y, s, {
    role: 'body', font: f.font, size: f.size, weight: f.weight,
    anchor: h === 'left' ? 'start' : h === 'right' ? 'end' : 'middle',
    baseline: v === 'top' ? 'top' : v === 'bottom' ? 'bottom' : v === 'center' ? 'middle' : 'alphabetic',
    fit: Math.max(8, w), fill: 'ink',
  })];
}
