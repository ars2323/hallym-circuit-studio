/* Memory (Logisim 2.7.1): D, T, J-K and S-R flip-flops, Register, Counter,
   Shift Register, Random, RAM, ROM.

   Boxes with the clock's triangle on the clock port (a circle for a falling
   edge or an active-low level), the symbol's letters inside (D … Q), the
   state in the middle.  A register, counter or random generator is too
   narrow for a 32-bit value (v1 S-07): its value is a chip beside it
   (registry valueChip, labels.ts places it off the wires).  RAM and ROM
   show Logisim's four-row table, sent by the engine (sim.values bodies). */

import type { Point, Shape } from '../shapes.ts';
import { valueKind } from '../tokens.ts';
import { attr, boundsBox, clockMark, hex, num, type Part, type PartState, portMarks, text } from './common.ts';

// Which way is into the box from a port on its edge.
export function inward(p: Part, i: number): Point {
  const [x, y, w, h] = p.bounds;
  const [px, py] = p.ports[i].loc;
  if (px <= x) return [1, 0];
  if (px >= x + w) return [-1, 0];
  if (py <= y) return [0, 1];
  return [0, -1];
}

const clockIndex = (p: Part): number => (p.name === 'Shift Register' ? 2 : p.ports.findIndex((q) => q.name === 'clk'));

function clock(p: Part): Shape[] {
  const i = clockIndex(p);
  if (i < 0 || !p.ports[i]) return [];
  const trigger = attr(p, 'trigger', 'rising');
  return clockMark(p.ports[i].loc as Point, inward(p, i), trigger);
}

// A port's letter just inside the box (the symbol's own letters: D, Q, J, K …).
function letter(p: Part, i: number, s: string, over = false): Shape[] {
  const q = p.ports[i];
  if (!q) return [];
  const [ix, iy] = inward(p, i);
  const x = q.loc[0] + ix * 7, y = q.loc[1] + iy * 7;
  const out: Shape[] = [text(x, y + 0.5, s, { size: 9, weight: 600, fill: 'ink2', anchor: ix > 0 ? 'start' : ix < 0 ? 'end' : 'middle' })];
  if (over) {
    const x0 = ix < 0 ? x - 5.2 : x, x1 = ix < 0 ? x : x + 5.2;
    out.push({ k: 'path', role: 'deco', d: [['M', x0, y - 5], ['L', x1, y - 5]], stroke: 'ink2', width: 0.9 });
  }
  return out;
}

export function drawFlipFlop(p: Part, st: PartState): Shape[] {
  const [x, y, w, h] = p.bounds;
  const out: Shape[] = [boundsBox(p, 3), ...clock(p)];
  const qi = p.ports.findIndex((q) => q.name === 'Q');
  const qn = p.ports.findIndex((q) => q.name === 'Qn');
  for (const [i, q] of p.ports.entries()) {
    if (q.name && /^[DTJKSR]$/.test(q.name)) out.push(...letter(p, i, q.name));
  }
  if (qi >= 0) out.push(...letter(p, qi, 'Q'));
  if (qn >= 0) out.push(...letter(p, qn, 'Q', true));
  const v = qi >= 0 ? st.value(qi) : undefined;
  if (v) out.push(text(x + w / 2, y + h / 2 - 5, v, { font: 'code', size: 10, weight: 700, fill: { value: v } }));
  return [...out, ...portMarks(p, st)];
}

// Register, Counter, Random: a narrow box; the value goes in a side chip (registry).
export function drawRegisterLike(p: Part, st: PartState): Shape[] {
  const [x, y, w, h] = p.bounds;
  const out: Shape[] = [boundsBox(p, 3), ...clock(p)];
  const di = p.ports.findIndex((q) => q.name === 'D');
  const qi = p.ports.findIndex((q) => q.name === 'Q' || q.name === 'out');
  if (di >= 0) out.push(...letter(p, di, 'D'));
  if (qi >= 0) out.push(...letter(p, qi, 'Q'));
  const caption = p.name === 'Register' ? 'reg' : p.name === 'Counter' ? 'ctr' : 'rand';
  const bits = num(p, 'width', 8);
  const v = qi >= 0 ? st.value(qi) : undefined;
  if (bits <= 8 && v) out.push(text(x + w / 2, y + h / 2 + 8, hex(v, bits), { font: 'code', size: 9, weight: 700, fill: valueKind(v) === 'bus' ? 'ink' : { value: v } }));
  out.push(text(x + w / 2, y + 7, caption, { size: 6.5, weight: 600, fill: 'muted' }));
  return [...out, ...portMarks(p, st)];
}

// The value beside a narrow register (registry valueChip): 32-bit values do not fit its box.
export function registerChip(p: Part, st: PartState): { text: string; value?: string } | null {
  const bits = num(p, 'width', 8);
  if (bits <= 8) return null;
  const qi = p.ports.findIndex((q) => q.name === 'Q' || q.name === 'out');
  const v = qi >= 0 ? st.value(qi) : undefined;
  return { text: hex(v, bits), value: v };
}

export function drawShiftRegister(p: Part, st: PartState): Shape[] {
  const [x, y, w, h] = p.bounds;
  const out: Shape[] = [boundsBox(p, 3), ...clock(p)];
  const stages = (st.body?.stages as string[] | undefined) ?? [];
  const parallel = attr(p, 'parallel') === 'true';
  const len = num(p, 'length', 8);
  if (parallel && stages.length) {
    for (let i = 0; i < Math.min(len, stages.length); i++) {
      const cx = x + 20 + 10 * i;
      if (cx > x + w - 5) break;
      out.push(text(cx, y + h / 2 + 0.5, stages[i], { font: 'code', size: 6.5, weight: 600, fit: 9 }));
    }
  } else {
    out.push(text(x + w / 2 + 3, y + h / 2 + 0.5, 'shift', { size: 8, weight: 600, fill: 'muted', fit: w - 12 }));
  }
  return [...out, ...portMarks(p, st)];
}

// ---- RAM, ROM -------------------------------------------------------------------------------------

interface Grid { columns: number; rows: { addr: string; words: string[] }[]; current?: string }

export function drawMemory(p: Part, st: PartState): Shape[] {
  const [x, y, w, h] = p.bounds;
  const aw = num(p, 'addrWidth', 8), dw = num(p, 'dataWidth', 8);
  const out: Shape[] = [boundsBox(p, 4), ...clock(p)];
  const ai = p.ports.findIndex((q) => q.name === 'A');
  const di = p.ports.findIndex((q) => q.name === 'D');
  if (ai >= 0) out.push(...letter(p, ai, 'A'));
  if (di >= 0) out.push(...letter(p, di, 'D'));
  const size = aw <= 16 ? `${2 ** aw}` : `2^${aw}`;
  out.push(text(x + w / 2, y + 9, `${p.name} ${size} × ${dw}`, { size: 8, weight: 600, fill: 'ink2', fit: w - 20 }));
  const grid = st.body as unknown as Grid | undefined;
  const top = y + 17, bottom = y + h - 14;
  const left = x + 20, right = x + w - 16;
  out.push({ k: 'rect', role: 'deco', x: left, y: top, w: right - left, h: bottom - top, r: 2, fill: 'bodySoft', stroke: 'border', width: 0.8 });
  if (grid?.rows?.length) {
    const rowH = (bottom - top) / 4;
    const addrW = 22;
    const cellsL = left + addrW;
    const cellW = (right - 2 - cellsL) / Math.max(1, grid.columns);
    grid.rows.forEach((r, k) => {
      const cy = top + rowH * (k + 0.5) + 0.3;
      out.push(text(cellsL - 3, cy, r.addr, { font: 'code', size: 8, anchor: 'end', fill: 'muted', fit: addrW - 3 }));
      r.words.forEach((word, c) => {
        const cx = cellsL + cellW * (c + 0.5);
        const addrN = parseInt(r.addr, 16) + c;
        const current = grid.current !== undefined && parseInt(grid.current, 16) === addrN;
        if (current) out.push({ k: 'rect', role: 'deco', x: cx - cellW / 2 + 0.5, y: top + rowH * k + 0.5, w: cellW - 1, h: rowH - 1, r: 1.5, fill: 'navy' });
        out.push(text(cx, cy, word, { font: 'code', size: 8, fill: current ? 'white' : 'ink', fit: cellW - 1.5 }));
      });
    });
  }
  return [...out, ...portMarks(p, st)];
}
