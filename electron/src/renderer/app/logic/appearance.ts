/* The appearance editor's gestures, as the original's drawing tools compute
   them (com.cburch.draw.tools; docs/interaction-parity.md I-195..I-205;
   logic only).  The page follows the pointer and draws what the tool
   would make; the shape itself is made by the engine with the original's
   code (edit.appearance, docs/engine-api.md) when the gesture ends.

     snap            AppearanceCanvas.snapX/Y: to the 10-grid, halves away from 0
     rectangle       RectangularTool.computeBounds: Ctrl snaps both corners,
                     Shift a square (circle), Alt from the middle
     line end        LineTool: Shift 45° (LineUtil.snapTo8Cardinals)
     move            SelectTool.setMouse MOVE_ALL: Ctrl snaps the top-left
                     handle, Shift keeps one axis; a drag starts after 2 units
     polygon closes  PolyTool: a click on the first point (2 units) closes it
     curve control   CurveTool: Shift mirrors the control, Alt puts the curve
                     through the point */

export type P = [number, number];

export const DRAG_TOLERANCE = 2;       // SelectTool: |dx| + |dy| must pass it
export const CLOSE_TOLERANCE = 2;      // PolyTool: a click this near the first point closes the polygon
export const HANDLE_SIZE = 8;          // SelectTool: handles, before the zoom

export function snap(v: number): number {
  return v < 0 ? -Math.floor((-v + 5) / 10) * 10 : Math.floor((v + 5) / 10) * 10;
}

export interface Mods { shift?: boolean; ctrl?: boolean; alt?: boolean }

// RectangularTool.computeBounds: [x, y, w, h], or null when nothing was dragged.
export function rectFromDrag(start: P, end: P, m: Mods): [number, number, number, number] | null {
  let [x0, y0] = start;
  let [x1, y1] = end;
  if (x0 === x1 && y0 === y1) return null;
  if (m.ctrl) { x0 = snap(x0); y0 = snap(y0); x1 = snap(x1); y1 = snap(y1); }
  if (m.alt) {
    if (m.shift) {
      const r = Math.min(Math.abs(x0 - x1), Math.abs(y0 - y1));
      x1 = x0 + r; y1 = y0 + r; x0 -= r; y0 -= r;
    } else {
      x0 -= x1 - x0; y0 -= y1 - y0;
    }
  } else if (m.shift) {
    const r = Math.min(Math.abs(x0 - x1), Math.abs(y0 - y1));
    y1 = y1 < y0 ? y0 - r : y0 + r;
    x1 = x1 < x0 ? x0 - r : x0 + r;
  }
  let x = x0, y = y0, w = x1 - x0, h = y1 - y0;
  if (w < 0) { x = x1; w = -w; }
  if (h < 0) { y = y1; h = -h; }
  if (w === 0 || h === 0) return null;   // the original adds a shape only with a width and a height
  return [x, y, w, h];
}

// LineUtil.snapTo8Cardinals: the end on the nearest of the eight directions from the start.
export function snap8(from: P, to: P): P {
  const [px, py] = from;
  const [mx, my] = to;
  if (mx === px || my === py) return to;
  const ang = Math.atan2(my - py, mx - px);
  const d45 = Math.trunc((Math.abs(mx - px) + Math.abs(my - py)) / 2);
  const d = Math.trunc((4 * ang) / Math.PI + 4.5);
  switch (d) {
    case 0: case 8: case 4: return [mx, py];
    case 2: case 6: return [px, my];
    case 1: return [px - d45, py - d45];
    case 3: return [px + d45, py - d45];
    case 5: return [px + d45, py + d45];
    case 7: return [px - d45, py + d45];
    default: return to;
  }
}

// LineTool.updateMouse (and PolyTool's last point from the one before): Shift 45° first, then Ctrl snaps.
export function lineEnd(start: P, end: P, m: Mods): P {
  const e: P = m.shift ? snap8(start, end) : end;
  return m.ctrl ? [snap(e[0]), snap(e[1])] : e;
}

// SelectTool: whether the pointer moved far enough for a drag.
export const dragged = (dx: number, dy: number) => Math.abs(dx) + Math.abs(dy) > DRAG_TOLERANCE;

// SelectTool MOVE_ALL: the offset, snapped by the top-left of the chosen shapes' handles (Ctrl), one axis (Shift).
export function moveDelta(dx: number, dy: number, handles: readonly P[], m: Mods): P {
  let x = dx, y = dy;
  if (m.ctrl && handles.length) {
    const minX = Math.min(...handles.map((h) => h[0]));
    const minY = Math.min(...handles.map((h) => h[1]));
    x = snap(minX + x) - minX;
    y = snap(minY + y) - minY;
  }
  if (m.shift) {
    if (Math.abs(x) > Math.abs(y)) y = 0; else x = 0;
  }
  return [x, y];
}

// SelectTool MOVE_HANDLE: Ctrl snaps the handle's new place.
export function handleDelta(handle: P, dx: number, dy: number, m: Mods): P {
  if (!m.ctrl) return [dx, dy];
  return [snap(handle[0] + dx) - handle[0], snap(handle[1] + dy) - handle[1]];
}

// The handle's size in circuit units at a zoom (SelectTool.getHandleSize: 8 / √zoom, rounded up).
export const handleSize = (zoom: number) => Math.ceil(HANDLE_SIZE / Math.sqrt(zoom > 0 ? zoom : 1));

// PolyTool.mouseReleased: with three points or more, letting go on the first point (Manhattan distance 2) ends it,
// that last point dropped (polylines too).
export function closes(points: readonly P[]): boolean {
  if (points.length < 3) return false;
  const a = points[0], b = points[points.length - 1];
  return Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) <= CLOSE_TOLERANCE;
}

// PolyTool.commit: the same point twice in a row is one.
export function poly(points: readonly P[]): P[] {
  const out: P[] = [];
  for (const p of points) if (!out.length || out[out.length - 1][0] !== p[0] || out[out.length - 1][1] !== p[1]) out.push(p);
  return out;
}

// CurveTool.updateMouse (CONTROL_DRAG): the control for a pointer at `at` -- Ctrl snaps it, Shift puts it on the
// ends' perpendicular bisector (a symmetric curve), Alt makes the curve pass through it (CurveUtil.interpolate).
export function curveControl(e0: P, e1: P, at: P, m: Mods): P {
  let [cx, cy] = m.ctrl ? [snap(at[0]), snap(at[1])] : at;
  if (m.shift) {
    const midx = (e0[0] + e1[0]) / 2, midy = (e0[1] + e1[1]) / 2;
    const dx = e1[0] - e0[0], dy = e1[1] - e0[1];
    const [nx, ny] = nearestOnLine([cx, cy], [midx, midy], [midx - dy, midy + dx]);
    cx = Math.round(nx); cy = Math.round(ny);
  }
  if (m.alt) {
    const [ix, iy] = interpolate(e0, e1, [cx, cy]);
    cx = Math.round(ix); cy = Math.round(iy);
  }
  return [cx, cy];
}

// LineUtil.nearestPointInfinite: the point of the line through a and b nearest to q.
function nearestOnLine(q: P, a: P, b: P): P {
  const dx = b[0] - a[0], dy = b[1] - a[1];
  const len2 = dx * dx + dy * dy;
  if (len2 < 1e-12) return [(a[0] + b[0]) / 2, (a[1] + b[1]) / 2];
  const t = ((q[0] - a[0]) * dx + (q[1] - a[1]) * dy) / len2;
  return [a[0] + t * dx, a[1] + t * dy];
}

// CurveUtil.interpolate: the control of the quadratic curve from e0 to e1 through mid.
function interpolate(e0: P, e1: P, mid: P): P {
  const d0 = Math.hypot(mid[0] - e0[0], mid[1] - e0[1]);
  const d1 = Math.hypot(mid[0] - e1[0], mid[1] - e1[1]);
  if (d0 < 1e-7 || d1 < 1e-7) return [(e0[0] + e1[0]) / 2, (e0[1] + e1[1]) / 2];
  const t = d0 / (d0 + d1);
  const u = 1 - t;
  const den = 2 * t * u;
  return [(mid[0] - u * u * e0[0] - t * t * e1[0]) / den, (mid[1] - u * u * e0[1] - t * t * e1[1]) / den];
}

// A point on a quadratic curve (for drawing the preview).
export function onCurve(e0: P, c: P, e1: P, t: number): P {
  const u = 1 - t;
  return [u * u * e0[0] + 2 * u * t * c[0] + t * t * e1[0], u * u * e0[1] + 2 * u * t * c[1] + t * t * e1[1]];
}

// The drawing tools' attributes (DrawingAttributeSet: the defaults, the ones each tool shows).
export const DRAW_DEFAULTS: Record<string, string> = {
  font: 'SansSerif plain 12', align: 'center', paintType: 'stroke', 'stroke-width': '1', stroke: '#000000', fill: '#ffffff', rx: '10',
};
export type DrawTool = 'Select' | 'Text' | 'Line' | 'Curve' | 'Polyline' | 'Rectangle' | 'Rounded Rectangle' | 'Oval' | 'Polygon';
export const DRAW_TOOLS: DrawTool[] = ['Select', 'Text', 'Line', 'Curve', 'Polyline', 'Rectangle', 'Rounded Rectangle', 'Oval', 'Polygon'];

// The attributes the tool's table shows (DrawAttr lists; the fill list follows the paint type).
export function toolAttributes(tool: DrawTool, paintType: string): string[] {
  const fill = paintType === 'stroke' ? ['paintType', 'stroke-width', 'stroke'] : paintType === 'fill' ? ['paintType', 'fill'] : ['paintType', 'stroke-width', 'stroke', 'fill'];
  switch (tool) {
    case 'Text': return ['font', 'align', 'fill'];
    case 'Line': return ['stroke-width', 'stroke'];
    case 'Curve': case 'Polyline': case 'Rectangle': case 'Oval': case 'Polygon': return fill;
    case 'Rounded Rectangle': return [...fill, 'rx'];
    default: return [];
  }
}

// The attributes an add sends: the tool's own (the text tool's colour is its fill).
export function toolAttrs(tool: DrawTool, values: Record<string, string>): Record<string, string> {
  const out: Record<string, string> = {};
  for (const k of toolAttributes(tool, values.paintType ?? 'stroke')) if (values[k] !== undefined) out[k] = values[k];
  if (tool === 'Rectangle' || tool === 'Oval' || tool === 'Curve' || tool === 'Rounded Rectangle') out.paintType = values.paintType ?? 'stroke';
  return out;
}

// Attribute names in the table (the original's, in English).
export const ATTR_NAMES: Record<string, string> = {
  font: 'Font', align: 'Alignment', paintType: 'Paint Type', 'stroke-width': 'Stroke Width', stroke: 'Stroke Color',
  fill: 'Fill Color', rx: 'Corner Radius', facing: 'Facing',
};
export const ALIGN_NAMES: Record<string, string> = { left: 'Left', center: 'Center', right: 'Right' };
export const PAINT_NAMES: Record<string, string> = { stroke: 'Border', fill: 'Fill', both: 'Border & Fill' };
