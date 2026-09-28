/* Bus value chips (N-15, D-151; v1 C-08, D-079): the value on each bus (a
   net two bits wide or more) beside its longest wire, as the label chips
   are placed -- never over a wire, a part or another chip.  A named bus
   (its tunnel, or the pin that drives it) shows its name and range too
   (`ALUResult[31:0] = 0x0000000c`); an unnamed one only its value, and only
   when its longest wire is 60 units or more.  One bit needs no chip (the
   wire's colour says it); nor does a bus that floats entirely.  The place
   is found with the widest value the radix can show, so the chip does not
   move as values change; it keeps WIRE_GAP from every wire -- also from
   the bands of the active path and the fields (UI checklist 12) -- and,
   with nowhere free, it is left out (the value shows on hover).  The radix
   (Hex, Dec, Signed, Off) is the Wire Colors panel's, for this run. */

import type { Point } from '../../../main/protocol.ts';
import type { CanvasOverlay, OverlayDraw } from '../canvas.ts';
import { CLEAR, type Measure, Obstacles, WIRE_GAP } from '../labels.ts';
import { FONTS, MIN_TEXT_PX } from '../paint.ts';
import type { Scene } from '../scene.ts';
import type { Box } from '../shapes.ts';
import type { Segment } from '../wires.ts';
import { GROUP_COLORS, type BusMode, busTemplate, busText, netName, segmentMeetsBox } from './logic.ts';

export { WIRE_GAP };               // circuit units from a wire's centre line (labels.ts, v1 LabelOverlay.WIRE_GAP, X-04)
export const BUS_TEXT = 10;
const PAD_X = 3.5, H = 14;

export interface BusChip { net: string; wire: string; prefix: string; width: number; box: Box; anchor: Point }

const len = (s: Segment) => Math.abs(s.a[0] - s.b[0]) + Math.abs(s.a[1] - s.b[1]);

export function nearestOn(s: Pick<Segment, 'a' | 'b'>, p: Point): Point {
  const x0 = Math.min(s.a[0], s.b[0]), x1 = Math.max(s.a[0], s.b[0]), y0 = Math.min(s.a[1], s.b[1]), y1 = Math.max(s.a[1], s.b[1]);
  return [Math.max(x0, Math.min(p[0], x1)), Math.max(y0, Math.min(p[1], y1))];
}

// How far a box is from a wire (circuit units; 0 when they meet).
export function boxToWire(b: Box, s: Pick<Segment, 'a' | 'b'>): number {
  const dx = Math.max(0, b.x0 - Math.max(s.a[0], s.b[0]), Math.min(s.a[0], s.b[0]) - b.x1);
  const dy = Math.max(0, b.y0 - Math.max(s.a[1], s.b[1]), Math.min(s.a[1], s.b[1]) - b.y1);
  return Math.hypot(dx, dy);
}

// The chip's leader: from its anchor on the wire towards the chip's middle (what is drawn is the part up to the
// chip's edge, whatever the text's width).
export const leaderOf = (c: Pick<BusChip, 'anchor' | 'box'>): [Point, Point] => [c.anchor, [(c.box.x0 + c.box.x1) / 2, (c.box.y0 + c.box.y1) / 2]];

/* A chip reads as its own wire's: no other net's wire is as near to it as its own, and none crosses its leader
   (UI review: RR2's value stood on WR's side, its leader over the WR bus). */
export function ownSide(box: Box, anchor: Point, own: string | null, segments: Segment[]): boolean {
  let mine = Infinity;
  for (const g of segments) if (g.net === own) mine = Math.min(mine, boxToWire(box, g));
  const mid: Point = [(box.x0 + box.x1) / 2, (box.y0 + box.y1) / 2];
  for (const g of segments) {
    if (g.net === own) continue;
    if (boxToWire(box, g) <= mine) return false;
    const gb = { x0: Math.min(g.a[0], g.b[0]) - 0.5, y0: Math.min(g.a[1], g.b[1]) - 0.5, x1: Math.max(g.a[0], g.b[0]) + 0.5, y1: Math.max(g.a[1], g.b[1]) + 0.5 };
    if (segmentMeetsBox(anchor, mid, gb)) return false;
  }
  return true;
}

/* Where every bus's chip stands (circuit units), for a model and a radix. */
export function layoutBusChips(scene: Scene, segments: Segment[], chips: Box[], measure: Measure, mode: BusMode): BusChip[] {
  if (mode === 'off') return [];
  const obs = new Obstacles();
  for (const c of scene.components.values()) {
    const [x, y, w, h] = c.bounds;
    if (w > 0 || h > 0) obs.add({ x0: x - 4, y0: y - 4, x1: x + w + 4, y1: y + h + 4 }, c.id);
  }
  for (const s of segments) {
    obs.add({ x0: Math.min(s.a[0], s.b[0]) - WIRE_GAP, y0: Math.min(s.a[1], s.b[1]) - WIRE_GAP, x1: Math.max(s.a[0], s.b[0]) + WIRE_GAP, y1: Math.max(s.a[1], s.b[1]) + WIRE_GAP }, `wire:${s.id}`);
  }
  chips.forEach((b, i) => obs.add({ x0: b.x0 - CLEAR, y0: b.y0 - CLEAR, x1: b.x1 + CLEAR, y1: b.y1 + CLEAR }, `chip:${i}`));
  const longest = new Map<string, Segment>();
  for (const s of segments) {
    if (!s.net || s.bits < 2) continue;
    const best = longest.get(s.net);
    if (!best || len(s) > len(best) || (len(s) === len(best) && s.id < best.id)) longest.set(s.net, s);
  }
  const out: BusChip[] = [];
  for (const net of scene.nets) {
    const s = longest.get(net.id);
    if (!s || net.width < 2) continue;
    const name = netName(scene, net);
    if (!name && len(s) < 60) continue;
    const prefix = name ? `${name}[${net.width - 1}:0] = ` : '';
    const w = measure(prefix + busTemplate(net.width, mode), 'code', BUS_TEXT, 600) + 2 * PAD_X;
    // next to the wire first, at its middle, then along it, then a step further out (with a leader)
    const cands: Box[] = [];
    for (const gap of [WIRE_GAP, WIRE_GAP + 12]) {
      for (const f of [0.5, 0.3, 0.7, 0.15, 0.85]) {
        const x = s.a[0] + (s.b[0] - s.a[0]) * f, y = s.a[1] + (s.b[1] - s.a[1]) * f;
        if (s.horizontal) {
          cands.push({ x0: x - w / 2, y0: y - gap - H, x1: x + w / 2, y1: y - gap });
          cands.push({ x0: x - w / 2, y0: y + gap, x1: x + w / 2, y1: y + gap + H });
        } else {
          cands.push({ x0: x + gap, y0: y - H / 2, x1: x + gap + w, y1: y + H / 2 });
          cands.push({ x0: x - gap - w, y0: y - H / 2, x1: x - gap, y1: y + H / 2 });
        }
      }
    }
    const anchorOf = (b: Box) => nearestOn(s, [(b.x0 + b.x1) / 2, (b.y0 + b.y1) / 2]);
    const box = cands.find((b) => !obs.hits(b) && ownSide(b, anchorOf(b), s.net, segments));
    if (!box) continue;       // nowhere free (a tight bundle of buses): no chip rather than one over a wire or by another
    obs.add(box, `bus:${net.id}`);
    out.push({ net: net.id, wire: s.id, prefix, width: net.width, box, anchor: anchorOf(box) });
  }
  return out;
}

export class BusValueOverlay implements CanvasOverlay {
  mode: BusMode = 'hex';
  groups = false;                                // Colors: Groups (the chip's border takes the group's colour)
  private laid: { key: string; chips: BusChip[] } | null = null;

  // Where the chips stand now (the other overlays keep their lines and labels off them); none when off.
  boxes(d: Pick<OverlayDraw, 'canvas' | 'scene'>): Box[] {
    return this.mode === 'off' ? [] : this.chips(d).map((c) => c.box);
  }

  chips(d: Pick<OverlayDraw, 'canvas' | 'scene'>): BusChip[] {
    const key = `${d.scene.fileId} ${d.scene.circuitId} ${d.scene.modelVersion} ${this.mode}`;
    if (!this.laid || this.laid.key !== key) {
      this.laid = { key, chips: layoutBusChips(d.scene, d.canvas.wireSegments(), d.canvas.chipBoxes(), d.canvas.measure, this.mode) };
    }
    return this.laid.chips;
  }

  over(d: OverlayDraw): void { this.draw(d); }

  // The chips (only those `only` keeps): the influence draws the ones of the nets it reached again over its dimming.
  draw(d: OverlayDraw, only?: (c: BusChip) => boolean): void {
    if (this.mode === 'off' || BUS_TEXT * d.zoom < MIN_TEXT_PX) return;
    const { ctx, scene, zoom: z, shown } = d;
    const theme = d.look.theme;
    ctx.save();
    for (const c of this.chips(d)) {
      if (only && !only(c)) continue;
      const b = c.box;
      if (b.x1 < shown.x0 || b.x0 > shown.x1 || b.y1 < shown.y0 || b.y0 > shown.y1) continue;
      const value = busText(scene.values.get(c.net), this.mode);
      if (value === null) continue;
      const text = c.prefix + value;
      ctx.font = `600 ${BUS_TEXT}px ${FONTS.code}`;
      const w = Math.min(b.x1 - b.x0, ctx.measureText(text).width + 2 * PAD_X);
      const x0 = (b.x0 + b.x1) / 2 - w / 2;
      // the leader: from the nearest point of its wire, when the chip stands off it
      const ex = Math.max(x0, Math.min(c.anchor[0], x0 + w)), ey = Math.max(b.y0, Math.min(c.anchor[1], b.y1));
      if (Math.abs(ex - c.anchor[0]) + Math.abs(ey - c.anchor[1]) > WIRE_GAP + 2) {
        ctx.beginPath(); ctx.moveTo(c.anchor[0], c.anchor[1]); ctx.lineTo(ex, ey);
        ctx.strokeStyle = theme.dim; ctx.lineWidth = 1 / z; ctx.stroke();
      }
      const g = this.groups ? scene.groups.get(c.net) : undefined;
      ctx.beginPath();
      ctx.roundRect(x0, b.y0, w, b.y1 - b.y0, 3.5);
      ctx.fillStyle = theme.tealTint; ctx.fill();
      ctx.strokeStyle = g ? GROUP_COLORS[g.group] : theme.teal; ctx.lineWidth = (g ? 2 : 1) / z; ctx.stroke();
      ctx.fillStyle = theme.tealText;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(text, x0 + w / 2, (b.y0 + b.y1) / 2 + 0.4);
    }
    ctx.restore();
  }
}
