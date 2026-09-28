/* Hallym MIPS parts (lib-mips, PLAN.md 6.2, D-140): Instruction Memory, Data
   Memory (data and stack together: both regions' ranges and their use), the
   old separate Stack (old circuits only), Console, Radix Probe.

   The same body as the track A jar draws in the original Logisim -- the
   title, the lines the engine sends (sim.values bodies: the region, the
   words, the word at the address, "data N words, stack peak N B"), the red
   status -- in the Canvas's fonts; port names inside, 14 units from the
   edge (v1 S-23), so a tunnel's name next to a port never meets them. */

import type { Shape } from '../shapes.ts';
import { attr, bin, boundsBox, clockMark, dec, hex, num, type Part, type PartState, portMarks, text } from './common.ts';

export const PORT_INSET = 14;
const TITLE = 11, LINE = 10;

const range = (lo: number, hi: number) => `${lo.toString(16).padStart(8, '0')}-${hi.toString(16).padStart(8, '0')}`;

// What the body says before the engine has sent anything (the attributes only).
export function staticLines(p: Part): string[] {
  const base = num(p, 'base', 0), size = num(p, 'size', 0);
  if (p.name === 'Stack') {
    const top = num(p, 'top', 0x7ffffffc);
    return [range(top + 4 - size, top + 3)];
  }
  const stackSize = num(p, 'stacksize', 0);
  if (p.name === 'Data Memory' && stackSize) {
    const top = num(p, 'stacktop', 0x7ffffffc);
    return [`data  ${range(base, base + size - 1)}`, `stack ${range(top + 4 - stackSize, top + 3)}`];
  }
  return [range(base, base + size - 1)];
}

function portNames(p: Part): Shape[] {
  const [x, y, w, h] = p.bounds;
  const out: Shape[] = [];
  for (const q of p.ports) {
    if (!q.name || q.name === 'clk') continue;
    const [px, py] = q.loc;
    const o = { size: 9, fill: 'ink2', font: 'ui' as const };
    if (px <= x) out.push(text(x + PORT_INSET, py + 0.5, q.name, { ...o, anchor: 'start' }));
    else if (px >= x + w) out.push(text(x + w - PORT_INSET, py + 0.5, q.name, { ...o, anchor: 'end' }));
    else if (py >= y + h) out.push(text(px, y + h - PORT_INSET + 4, q.name, { ...o, baseline: 'alphabetic' }));
    else out.push(text(px, y + PORT_INSET, q.name, { ...o, baseline: 'top' }));
  }
  return out;
}

function clk(p: Part): Shape[] {
  const i = p.ports.findIndex((q) => q.name === 'clk');
  if (i < 0) return [];
  const [x, y, w] = p.bounds;
  const [px, py] = p.ports[i].loc;
  const inward: [number, number] = px <= x ? [1, 0] : px >= x + w ? [-1, 0] : py <= y ? [0, 1] : [0, -1];
  return clockMark([px, py], inward);
}

export function drawMipsMemory(p: Part, st: PartState): Shape[] {
  const [x, y, w] = p.bounds;
  const cx = x + w / 2;
  const lines = (st.body?.lines as string[] | undefined) ?? staticLines(p);
  const status = st.body?.status as string | undefined;
  const out: Shape[] = [boundsBox(p, 5)];
  out.push(text(cx, y + 11, p.name, { size: TITLE, weight: 700, fill: 'navy' }));
  lines.forEach((line, i) => {
    if (line) out.push(text(cx, y + 30 + 12 * i, line, { font: 'code', size: LINE, baseline: 'alphabetic', fill: i < 2 && p.name === 'Data Memory' ? 'ink' : 'ink', fit: w - 2 * PORT_INSET - 60 }));
  });
  if (status) out.push(text(cx, y + 32 + 12 * lines.length, status, { size: 9.5, weight: 600, baseline: 'alphabetic', fill: 'error', fit: w - 40 }));
  return [...out, ...portNames(p), ...clk(p), ...portMarks(p, st)];
}

export const CONSOLE_LEFT = 72, CONSOLE_RIGHT = 48;

export function drawConsole(p: Part, st: PartState): Shape[] {
  const [x, y, w] = p.bounds;
  const lines = (st.body?.lines as string[] | undefined) ?? [];
  const status = st.body?.status as string | undefined;
  const error = st.body?.error === true;
  const ox = x + CONSOLE_LEFT - 4, ow = w - CONSOLE_LEFT - CONSOLE_RIGHT + 8;
  const out: Shape[] = [boundsBox(p, 5)];
  out.push(text(x + w / 2, y + 11, 'Console', { size: TITLE, weight: 700, fill: 'navy' }));
  out.push({ k: 'rect', role: 'deco', x: ox, y: y + 20, w: ow, h: 78, r: 3, fill: 'bodySoft', stroke: 'border', width: 1 });
  lines.forEach((line, i) => out.push(text(x + CONSOLE_LEFT, y + 32 + 11 * i, line, { font: 'code', size: 9, anchor: 'start', baseline: 'alphabetic', fill: 'ink', fit: ow - 8 })));
  if (status) out.push(text(ox + ow / 2, y + 110, status, { size: 9, weight: 600, baseline: 'alphabetic', fill: error ? 'error' : 'muted', fit: ow }));
  return [...out, ...portNames(p), ...clk(p), ...portMarks(p, st)];
}

// The Radix Probe's three lines when the engine has not sent them (the attributes' primary radix).
export function radixLines(v: string | undefined, bits: number, primary: number, signed: boolean): string[] {
  const all = [`0x${hex(v, bits)}`, dec(v, signed), bin(v, bits)];
  return [all[primary], ...all.filter((_, i) => i !== primary)];
}

export function drawRadixProbe(p: Part, st: PartState): Shape[] {
  const [x, y, w, h] = p.bounds;
  const bits = num(p, 'width', 32);
  const primary = ['hex', 'dec', 'bin'].indexOf(attr(p, 'radix', 'hex'));
  const lines = (st.body?.lines as string[] | undefined) ?? radixLines(st.value(0), bits, Math.max(0, primary), attr(p, 'signed', 'true') === 'true');
  const out: Shape[] = [
    { k: 'rect', role: 'body', x: x + 0.8, y: y + 0.8, w: w - 1.6, h: h - 1.6, r: 6, fill: 'body', stroke: 'bodyStroke', width: 1.6 },
    text(x + 10, y + 16, lines[0] ?? '', { font: 'code', size: 11, weight: 700, anchor: 'start', baseline: 'alphabetic', fill: 'ink', fit: w - 16 }),
    text(x + 10, y + 30, lines[1] ?? '', { font: 'code', size: 10, anchor: 'start', baseline: 'alphabetic', fill: 'ink2', fit: w - 16 }),
    text(x + 10, y + h - 7, lines[2] ?? '', { font: 'code', size: 10, anchor: 'start', baseline: 'alphabetic', fill: 'ink2', fit: w - 16 }),
  ];
  return [...out, ...portMarks(p, st)];
}
