/* Drawing the registry's shapes (shapes.ts) with Canvas 2D (N-05, D-137).
   The context is already in circuit coordinates (the view's zoom and pan);
   widths that must look the same at every zoom -- wires, stubs, port
   marks -- are given in screen pixels and divided by the zoom here.
   svg.ts writes the same shapes as SVG; both resolve colours and widths
   through the functions below, so the screen and a picture agree. */

import type { Paint, Seg, Shape, TextShape } from './shapes.ts';
import { type Theme, valueColor, valueKind } from './tokens.ts';

// The part of CanvasRenderingContext2D the painter uses (a test records it).
export type Ctx2D = Pick<CanvasRenderingContext2D,
  'save' | 'restore' | 'translate' | 'rotate' | 'beginPath' | 'moveTo' | 'lineTo' | 'quadraticCurveTo' | 'bezierCurveTo' | 'arc' | 'closePath' |
  'rect' | 'roundRect' | 'ellipse' | 'fill' | 'stroke' | 'fillText' | 'measureText' | 'setLineDash' |
  'lineWidth' | 'lineCap' | 'lineJoin' | 'strokeStyle' | 'fillStyle' | 'font' | 'textAlign' | 'textBaseline' | 'globalAlpha'>;

export const FONTS = { ui: 'Pretendard, sans-serif', code: 'D2Coding, monospace' } as const;

/* The smallest text drawn (CSS px on screen, D-137): smaller text is not drawn at all -- body words,
   values, label chips and tunnel names alike.  The UI review of #425 found body words under about 7 px
   unreadable at 100 % on the lab PCs; an 8 px Pretendard letter's capitals are about 6 px high, so 8 px
   is the smallest size drawn.  The part's shape still shows; its small words come back as it grows
   (a register's "reg" at 6.5 units from 125 %). */
export const MIN_TEXT_PX = 8;

export interface Look {
  theme: Theme;
  zoom: number;                 // screen px per circuit unit (CSS px; the device ratio is the context's)
  minText?: number;             // text smaller than this on screen (px) is not drawn
}

// ---- widths --------------------------------------------------------------------------------

/* A wire's width on screen (px): one bit and a bus, growing with the zoom
   but kept readable when small and not heavy when large (v1 S-13: the
   widths at 25, 100 and 400 % are fixed by tests). */
export function wirePx(zoom: number, bits: number): number {
  const bus = bits > 1;
  const px = (bus ? 3.6 : 2) * Math.sqrt(zoom);
  return Math.round(Math.min(bus ? 7 : 4, Math.max(bus ? 2.25 : 1.25, px)) * 4) / 4;
}
// A port mark's radius on screen (px): a dot where something connects, a ring where nothing does.
export const portPx = (zoom: number): number => Math.min(3.5, Math.max(1.5, 2.2 * Math.sqrt(zoom)));

// ---- colours ---------------------------------------------------------------------------------

export function color(theme: Theme, p: Paint | undefined): string | null {
  if (p === undefined || p === 'none') return null;
  if (typeof p === 'string') return (theme as Record<string, string>)[p] ?? p;
  if ('value' in p) return valueColor(theme, p.value);
  return p.alpha === undefined ? p.rgb : rgba(p.rgb, p.alpha);
}

export function rgba(hex: string, alpha: number): string {
  const m = /^#([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})/i.exec(hex);
  if (!m) return hex;
  return `rgba(${parseInt(m[1], 16)},${parseInt(m[2], 16)},${parseInt(m[3], 16)},${alpha})`;
}

// The stroke width of a shape in circuit units at this zoom.
export function strokeUnits(s: Shape, zoom: number): number {
  if (s.role === 'stub') {
    const v = 'stroke' in s && s.stroke && typeof s.stroke === 'object' && 'value' in s.stroke ? s.stroke.value : '';
    return wirePx(zoom, s.bits ?? (v.length > 1 ? 2 : 1)) / zoom;
  }
  // A part's lines grow with the square root of the zoom, like the wires: at 400 % they are twice
  // as thick as at 100 %, not four times (v1 S-13), and at 25 % still half as thick, not a quarter.
  return (s.width ?? 1) / Math.sqrt(zoom);
}

export const cssFont = (t: TextShape, sizePx: number): string => `${t.weight ?? 400} ${sizePx}px ${FONTS[t.font]}`;

// ---- drawing ------------------------------------------------------------------------------------

function trace(ctx: Ctx2D, d: Seg[]): void {
  for (const s of d) {
    switch (s[0]) {
      case 'M': ctx.moveTo(s[1], s[2]); break;
      case 'L': ctx.lineTo(s[1], s[2]); break;
      case 'Q': ctx.quadraticCurveTo(s[1], s[2], s[3], s[4]); break;
      case 'C': ctx.bezierCurveTo(s[1], s[2], s[3], s[4], s[5], s[6]); break;
      case 'A': ctx.arc(s[1], s[2], s[3], s[4], s[5], s[6]); break;
      case 'Z': ctx.closePath(); break;
    }
  }
}

export function paintShapes(ctx: Ctx2D, shapes: Shape[], look: Look): void {
  for (const s of shapes) paintShape(ctx, s, look);
}

export function paintShape(ctx: Ctx2D, s: Shape, look: Look): void {
  const { theme, zoom } = look;
  if (s.k === 'port') { paintPort(ctx, s.x, s.y, s.value, look, false); return; }
  if (s.k === 'text') { paintText(ctx, s, look); return; }
  const fill = color(theme, s.fill);
  const stroke = color(theme, s.stroke);
  ctx.beginPath();
  if (s.k === 'path') trace(ctx, s.d);
  else if (s.k === 'rect') {
    if (s.r) ctx.roundRect(s.x, s.y, s.w, s.h, s.r);
    else ctx.rect(s.x, s.y, s.w, s.h);
  } else ctx.ellipse(s.cx, s.cy, s.rx, s.ry, 0, 0, Math.PI * 2);
  if (fill) { ctx.fillStyle = fill; ctx.fill(); }
  if (stroke) {
    ctx.strokeStyle = stroke;
    ctx.lineWidth = strokeUnits(s, zoom);
    ctx.lineCap = s.cap ?? 'round';
    ctx.lineJoin = 'round';
    if (s.dash) ctx.setLineDash(s.dash);
    ctx.stroke();
    if (s.dash) ctx.setLineDash([]);
  }
}

/* A port mark: a dot in the value's colour where a wire or another port
   meets it, a ring where nothing does (a connection still to make). */
export function paintPort(ctx: Ctx2D, x: number, y: number, value: string | undefined, look: Look, open: boolean): void {
  const r = portPx(look.zoom) / look.zoom;
  ctx.beginPath();
  ctx.arc(x, y, open ? r * 1.25 : r, 0, Math.PI * 2);
  if (open) {
    ctx.fillStyle = look.theme.paper; ctx.fill();
    ctx.strokeStyle = valueKind(value) === 'none' ? look.theme.muted : valueColor(look.theme, value);
    ctx.lineWidth = 1.25 / look.zoom;
    ctx.stroke();
  } else {
    ctx.fillStyle = valueKind(value) === 'none' ? look.theme.bodyStroke : valueColor(look.theme, value);
    ctx.fill();
  }
}

// Measured text widths (px at 100 px font size), per font: measuring is the slow part of text.
const widths = new Map<string, number>();
export function measure(ctx: Pick<Ctx2D, 'measureText' | 'font'>, t: TextShape): number {
  const k = `${t.font}|${t.weight ?? 400}|${t.text}`;
  let w = widths.get(k);
  if (w === undefined) {
    ctx.font = cssFont(t, 100);
    w = ctx.measureText(t.text).width;
    widths.set(k, w);
  }
  return (w / 100) * t.size;
}

// The text's size once fitted into `fit` (circuit units).
export function fittedSize(ctx: Pick<Ctx2D, 'measureText' | 'font'>, t: TextShape): number {
  if (!t.fit) return t.size;
  const w = measure(ctx, t);
  return w > t.fit ? Math.max(t.size * 0.55, (t.size * t.fit) / w) : t.size;
}

export function paintText(ctx: Ctx2D, t: TextShape, look: Look): void {
  const size = fittedSize(ctx, t);
  if (size * look.zoom < (look.minText ?? MIN_TEXT_PX)) return;   // too small to read: not drawn (and cheaper)
  ctx.font = cssFont(t, size);
  ctx.textAlign = t.anchor === 'middle' ? 'center' : t.anchor === 'end' ? 'right' : 'left';
  ctx.textBaseline = t.baseline ?? 'alphabetic';
  const fill = color(look.theme, t.fill ?? 'ink');
  if (!fill) return;
  ctx.fillStyle = fill;
  if (t.rotate) {
    ctx.save();
    ctx.translate(t.x, t.y);
    ctx.rotate(t.rotate);
    ctx.fillText(t.text, 0, 0);
    ctx.restore();
  } else {
    ctx.fillText(t.text, t.x, t.y);
  }
}
