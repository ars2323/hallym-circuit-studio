/* The order the parts are painted in (N-05, D-137; UI review of #425): no
   part may be hidden by another part's fill.  Every part's body fills go
   first, then everything else -- outlines, stubs, marks, text, ports -- so
   a part that lies inside another (a tunnel inside a comparator, as the
   original allows) keeps its outline and name on top of the other's
   fill, as in the original, which draws outlines only.  Within each pass
   the larger parts come first, so small parts' marks stay above large
   bodies.  The Canvas (canvas.ts) and the picture export (exportSvg) both
   paint in this order. */

import type { Component } from '../../main/protocol.ts';
import type { Shape } from './shapes.ts';

export interface Split { base: Shape[]; top: Shape[] }

// A part's shapes in the two passes: its body fills alone, then the rest (a filled and outlined
// body shape is split in two: its fill in the base, its outline on top).
export function split(shapes: Shape[]): Split {
  const base: Shape[] = [], top: Shape[] = [];
  for (const s of shapes) {
    if (s.role === 'body' && s.k !== 'text' && s.k !== 'port' && s.fill !== undefined && s.fill !== 'none') {
      base.push({ ...s, stroke: undefined });
      if (s.stroke !== undefined && s.stroke !== 'none') top.push({ ...s, fill: undefined });
    } else {
      top.push(s);
    }
  }
  return { base, top };
}

const area = (c: Component) => Math.max(0, c.bounds[2]) * Math.max(0, c.bounds[3]);

// The parts in painting order: larger first (the same order in both passes); equal sizes keep the snapshot's order.
export function byArea(parts: Component[]): Component[] {
  return parts.map((c, i) => ({ c, i })).sort((a, b) => area(b.c) - area(a.c) || a.i - b.i).map((x) => x.c);
}

// Every shape of the parts in the order they are painted, each with its part.
export function paintOrder(parts: Component[], shapesOf: (c: Component) => Split): { part: Component; shape: Shape; pass: 'base' | 'top' }[] {
  const ordered = byArea(parts);
  const out: { part: Component; shape: Shape; pass: 'base' | 'top' }[] = [];
  for (const c of ordered) for (const s of shapesOf(c).base) out.push({ part: c, shape: s, pass: 'base' });
  for (const c of ordered) for (const s of shapesOf(c).top) out.push({ part: c, shape: s, pass: 'top' });
  return out;
}
