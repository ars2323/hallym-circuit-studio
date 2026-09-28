/* The Minimap's model (v1 Minimap.Fit, S-11, E-08, I-176; D-150): the
   whole circuit in the panel -- everything drawn plus 20 units around it,
   scaled to fit with 8 px to spare and centred -- and the way between the
   panel's points and the circuit's. */

import type { Box } from '../../canvas/shapes.ts';

export const MARGIN = 8;         // panel px around the circuit
export const AROUND = 20;        // circuit units around what is drawn

export interface Fit { scale: number; ox: number; oy: number }

// The circuit's extent (an empty circuit: a small area at the origin, as v1).
export function fitMap(extent: Box, w: number, h: number): Fit {
  const empty = !Number.isFinite(extent.x0) || extent.x1 - extent.x0 <= 0 && extent.y1 - extent.y0 <= 0;
  const b = empty ? { x0: 0, y0: 0, x1: 200, y1: 150 } : extent;
  const x0 = b.x0 - AROUND, y0 = b.y0 - AROUND;
  const bw = Math.max(1, b.x1 - b.x0 + 2 * AROUND), bh = Math.max(1, b.y1 - b.y0 + 2 * AROUND);
  const scale = Math.max(1e-6, Math.min((w - 2 * MARGIN) / bw, (h - 2 * MARGIN) / bh));
  return { scale, ox: (w - bw * scale) / 2 - x0 * scale, oy: (h - bh * scale) / 2 - y0 * scale };
}

export const toMap = (f: Fit, p: [number, number]): [number, number] => [f.ox + p[0] * f.scale, f.oy + p[1] * f.scale];
export const fromMap = (f: Fit, m: [number, number]): [number, number] => [(m[0] - f.ox) / f.scale, (m[1] - f.oy) / f.scale];

// The Canvas's view as a rectangle on the map.
export function viewRect(f: Fit, view: Box): { x: number; y: number; w: number; h: number } {
  const [x0, y0] = toMap(f, [view.x0, view.y0]);
  const [x1, y1] = toMap(f, [view.x1, view.y1]);
  return { x: x0, y: y0, w: x1 - x0, h: y1 - y0 };
}
