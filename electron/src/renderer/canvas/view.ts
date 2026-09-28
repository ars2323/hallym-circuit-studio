/* Zoom and pan (N-05; v1 B-02, S-10, S-21): the circuit point at the
   Canvas's top-left corner and the zoom (screen px per circuit unit, CSS
   px).  25 % to 400 %; Ctrl+wheel zooms smoothly about the pointer, Ctrl +
   and Ctrl − step through the list below about the middle, Ctrl+0 fits the
   circuit with a margin and centres it both ways (v1 S-10). */

import type { Box } from './shapes.ts';

export const MIN_ZOOM = 0.25;
export const MAX_ZOOM = 4;
export const STEPS = [0.25, 0.33, 0.5, 0.67, 0.75, 0.8, 0.9, 1, 1.1, 1.25, 1.5, 1.75, 2, 2.5, 3, 4];
export const FIT_MARGIN = 40;          // screen px around the fitted circuit
export const FIT_MAX = 2;              // a small circuit is not blown up past 200 %

export interface View { x: number; y: number; zoom: number }   // x, y: the circuit point at the top-left

export const clampZoom = (z: number): number => Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, z));

export const toScreen = (v: View, p: [number, number]): [number, number] => [(p[0] - v.x) * v.zoom, (p[1] - v.y) * v.zoom];
export const toCircuit = (v: View, s: [number, number]): [number, number] => [v.x + s[0] / v.zoom, v.y + s[1] / v.zoom];

// Zoom to `zoom`, keeping the circuit point under the screen point `at` where it is.
export function zoomAt(v: View, zoom: number, at: [number, number]): View {
  const z = clampZoom(zoom);
  const [cx, cy] = toCircuit(v, at);
  return { zoom: z, x: cx - at[0] / z, y: cy - at[1] / z };
}

// The next step up or down from the zoom now (a zoom between steps goes to the neighbouring one).
export function step(zoom: number, dir: 1 | -1): number {
  if (dir > 0) return STEPS.find((s) => s > zoom + 1e-6) ?? MAX_ZOOM;
  return [...STEPS].reverse().find((s) => s < zoom - 1e-6) ?? MIN_ZOOM;
}

// Ctrl+wheel: a smooth factor per wheel delta (a notch of 100 is about 16 %).
export const wheelZoom = (zoom: number, deltaY: number): number => clampZoom(zoom * Math.exp(-deltaY * 0.0015));

// Fit `content` (circuit units) into a w × h screen area, centred both ways.
export function fit(content: Box, w: number, h: number): View {
  if (!Number.isFinite(content.x0) || content.x1 <= content.x0 && content.y1 <= content.y0) {
    return { zoom: 1, x: (Number.isFinite(content.x0) ? content.x0 : 0) - w / 2, y: (Number.isFinite(content.y0) ? content.y0 : 0) - h / 2 };
  }
  const cw = Math.max(1, content.x1 - content.x0), ch = Math.max(1, content.y1 - content.y0);
  const zoom = clampZoom(Math.min(FIT_MAX, (w - 2 * FIT_MARGIN) / cw, (h - 2 * FIT_MARGIN) / ch));
  // Centred on each axis where the circuit fits; where it does not even at 25 % (the floor), its top or
  // left edge with the margin shows, so the view starts where the circuit starts (D-137; the Minimap gives the whole)
  const along = (lo: number, hi: number, size: number) => ((hi - lo) * zoom + 2 * FIT_MARGIN <= size + 0.5
    ? (lo + hi) / 2 - size / 2 / zoom
    : lo - FIT_MARGIN / zoom);
  return { zoom, x: along(content.x0, content.x1, w), y: along(content.y0, content.y1, h) };
}

// The view's circuit rectangle for a w × h screen.
export const visible = (v: View, w: number, h: number): Box => ({ x0: v.x, y0: v.y, x1: v.x + w / v.zoom, y1: v.y + h / v.zoom });

// An eased step between two views (smooth zoom of Ctrl± and Fit): t in 0..1, about the same screen point.
export function between(a: View, b: View, t: number): View {
  const e = 1 - Math.pow(1 - t, 3);
  // interpolate the zoom geometrically so the motion feels even
  const zoom = a.zoom * Math.pow(b.zoom / a.zoom, e);
  return { zoom, x: a.x + (b.x - a.x) * e, y: a.y + (b.y - a.y) * e };
}

export const percent = (zoom: number): string => `${Math.round(zoom * 100)}%`;
