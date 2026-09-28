/* Area memos (N-15, D-151; v1 E-08, D-088): a coloured box and a short word
   (IF, ID, EX …) the student puts over a part of the circuit, saved with it
   (hcs:ext; the original Logisim 2.7.1 skips it).  Drawn first, under the
   wires and parts: a light fill and a 2-unit border in the memo's colour
   (the tunnel palette), the word bold above the box's top left, outside
   it, so it covers nothing inside -- unless a chip, a part or a wire is
   there: then the first free place of above right, below left, below
   right, inside the corners (UI checklist 12: no word over another word
   or a wire).  Circuit units: it grows with the zoom, as a region drawn in
   a textbook does.  The picture export has it too. */

import type { AreaMemo } from '../../../main/protocol.ts';
import type { CanvasOverlay, CircuitCanvas, OverlayDraw } from '../canvas.ts';
import { FONTS, MIN_TEXT_PX, rgba } from '../paint.ts';
import { type Box, boxesMeet } from '../shapes.ts';
import { TUNNEL_PALETTE } from '../tokens.ts';

export const MEMO_TEXT = 14;      // circuit units (v1)
export const MEMO_PAD = 6;
const FILL_ALPHA = 28 / 255;

export const memoColor = (m: AreaMemo): string => TUNNEL_PALETTE[((m.color % TUNNEL_PALETTE.length) + TUNNEL_PALETTE.length) % TUNNEL_PALETTE.length];

// A darker shade of the memo's colour for its word (v1 Color.darker(): each channel × 0.7).
export function darker(hex: string): string {
  const n = parseInt(hex.slice(1), 16);
  const c = (v: number) => Math.floor(v * 0.7).toString(16).padStart(2, '0');
  return `#${c((n >> 16) & 255)}${c((n >> 8) & 255)}${c(n & 255)}`;
}

// The memo that holds the point, the smallest when they overlap (v1 AreaMemos.at).
export function memoAt(memos: AreaMemo[], p: [number, number]): AreaMemo | null {
  let best: AreaMemo | null = null;
  for (const m of memos) {
    if (p[0] < m.x || p[0] > m.x + m.w || p[1] < m.y || p[1] > m.y + m.h) continue;
    if (!best || m.w * m.h < best.w * best.h) best = m;
  }
  return best;
}

export const memoBox = (m: AreaMemo): Box => ({ x0: m.x, y0: m.y, x1: m.x + m.w, y1: m.y + m.h });

// The box a memo word takes with its baseline at (x, y) (bold Pretendard: cap height 0.8, descent 0.2).
export const wordBox = (x: number, y: number, w: number): Box => ({ x0: x, y0: y - 0.8 * MEMO_TEXT, x1: x + w, y1: y + 0.2 * MEMO_TEXT });

/* Where a memo's word stands (its baseline's left end), the word `w` wide: above the box's top left (v1),
   else the first place that meets none of the blockers (chips, parts' bodies, wires) -- above right, below
   left, below right, inside the top left, top right, bottom left, bottom right corners; none free: v1's. */
export function wordPlace(m: AreaMemo, w: number, blockers: Box[]): [number, number] {
  const left = m.x + MEMO_PAD, right = m.x + m.w - MEMO_PAD - w;
  const above = m.y - MEMO_PAD + 2, below = m.y + m.h + MEMO_PAD + 0.8 * MEMO_TEXT;
  const top = m.y + MEMO_PAD + 0.8 * MEMO_TEXT, bottom = m.y + m.h - MEMO_PAD - 0.2 * MEMO_TEXT;
  const tries: [number, number][] = [[left, above], [right, above], [left, below], [right, below], [left, top], [right, top], [left, bottom], [right, bottom]];
  for (const t of tries) {
    const b = wordBox(t[0], t[1], w);
    if (!blockers.some((o) => boxesMeet(o, b))) return t;
  }
  return tries[0];
}

// What a memo's word keeps off on this Canvas: the chips (the bus values' too), the parts' bodies, the wires.
function blockersOf(canvas: CircuitCanvas, more: Box[]): Box[] {
  const scene = canvas.scene;
  if (!scene) return [];
  const out: Box[] = [...canvas.chipBoxes(), ...more];
  for (const c of scene.components.values()) out.push({ x0: c.bounds[0], y0: c.bounds[1], x1: c.bounds[0] + c.bounds[2], y1: c.bounds[1] + c.bounds[3] });
  for (const g of canvas.wireSegments()) out.push({ x0: Math.min(g.a[0], g.b[0]) - 2, y0: Math.min(g.a[1], g.b[1]) - 2, x1: Math.max(g.a[0], g.b[0]) + 2, y1: Math.max(g.a[1], g.b[1]) + 2 });
  return out;
}

export class MemoOverlay implements CanvasOverlay {
  moreChips = (_d: OverlayDraw | null): Box[] => [];    // other chips on the circuit (the bus values)

  under(d: OverlayDraw): void {
    const { ctx, scene, zoom: z, shown } = d;
    if (!scene.memos.length) return;
    let blockers: Box[] | null = null;
    ctx.save();
    for (const m of scene.memos) {
      if (m.x > shown.x1 || m.y - 20 > shown.y1 || m.x + m.w < shown.x0 || m.y + m.h < shown.y0) continue;
      const c = memoColor(m);
      ctx.beginPath();
      ctx.roundRect(m.x, m.y, m.w, m.h, 6);
      ctx.fillStyle = rgba(c, FILL_ALPHA);
      ctx.fill();
      ctx.strokeStyle = c;
      ctx.lineWidth = 2;
      ctx.stroke();
      if (m.text && MEMO_TEXT * z >= MIN_TEXT_PX) {
        blockers ??= blockersOf(d.canvas, this.moreChips(d));
        const [x, y] = wordPlace(m, d.canvas.measure(m.text, 'ui', MEMO_TEXT, 700), blockers);
        ctx.font = `700 ${MEMO_TEXT}px ${FONTS.ui}`;
        ctx.fillStyle = darker(c);
        ctx.textAlign = 'left';
        ctx.textBaseline = 'alphabetic';
        ctx.fillText(m.text, x, y);
      }
    }
    ctx.restore();
  }

  svg(canvas: CircuitCanvas): string[] {
    const esc = (t: string) => t.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    const memos = canvas.scene?.memos ?? [];
    const blockers = memos.some((m) => m.text) ? blockersOf(canvas, this.moreChips(null)) : [];
    return memos.flatMap((m) => {
      const c = memoColor(m);
      const [x, y] = m.text ? wordPlace(m, canvas.measure(m.text, 'ui', MEMO_TEXT, 700), blockers) : [0, 0];
      return [`<rect x="${m.x}" y="${m.y}" width="${m.w}" height="${m.h}" rx="6" fill="${c}" fill-opacity="${FILL_ALPHA.toFixed(3)}" stroke="${c}" stroke-width="2"/>`,
        ...(m.text ? [`<text x="${x}" y="${y}" font-family="${FONTS.ui}" font-size="${MEMO_TEXT}" font-weight="700" fill="${darker(c)}">${esc(m.text)}</text>`] : [])];
    });
  }
}
