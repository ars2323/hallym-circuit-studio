/* The influence path (N-15, D-151; v1 P-01, D-062): what the chosen parts
   and wires drive (forward, blue) and what drives them (backward, amber),
   from the engine's trace.influence (v1 Influence: one connection engine,
   state parts stop it unless Through Registers).  Drawn over the circuit:
   the rest is dimmed, the wires reached get a band and are drawn again in
   their value colours with the parts reached; the start has a navy
   outline, the state parts it stopped at an amber dashed one, the
   subcircuit instances it went into a blue one and an "alu: 3 places"
   chip, the tunnels of a net reached a thin outline and -- when the net has
   exactly two -- a dotted line between them that never runs over a part's
   body or a chip.  While a Signal Flow runs it is drawn faint (40 %).
   Nothing is saved; an edit of the circuit clears it. */

import type { InfluenceResult, Point } from '../../../main/protocol.ts';
import type { CanvasOverlay, OverlayDraw } from '../canvas.ts';
import { FONTS, rgba } from '../paint.ts';
import type { Box } from '../shapes.ts';
import { INFLUENCE_BACKWARD, INFLUENCE_FORWARD, influenceBand, placesAt, segmentMeetsBox, type Weighted } from './logic.ts';

export const DIM = 'rgba(255,255,255,0.667)';
export const FADED = 0.4;

export const placesText = (name: string, n: number): string => `${name}: ${n} ${n === 1 ? 'place' : 'places'}`;

export class InfluenceOverlay implements CanvasOverlay {
  result: InfluenceResult | null = null;
  faded = (): boolean => false;                 // a Signal Flow runs (the controller says)
  moreChips = (_d: OverlayDraw): Box[] => [];    // other chips on the circuit (the bus values)
  drawChips = (_d: OverlayDraw, _nets: Set<string>): void => {};   // the bus values of these nets, again over the dimming

  over(d: OverlayDraw): void {
    const r = this.result;
    if (!r) return;
    const { ctx, scene, zoom: z, shown, canvas } = d;
    const px = (v: number) => v / z;
    ctx.save();
    // the rest dimmed (the dimming itself is not faded)
    ctx.fillStyle = DIM;
    ctx.fillRect(shown.x0, shown.y0, shown.x1 - shown.x0, shown.y1 - shown.y0);
    ctx.globalAlpha = this.faded() ? FADED : 1;
    const onlyBack = r.forward.wires.length === 0 && r.backward.wires.length > 0;
    const band = influenceBand(z);
    const bandOf = (ids: string[], color: string, width: number) => {
      ctx.beginPath();
      for (const id of ids) { const w = scene.wires.get(id); if (w) { ctx.moveTo(w.a[0], w.a[1]); ctx.lineTo(w.b[0], w.b[1]); } }
      ctx.strokeStyle = rgba(color, 110 / 255); ctx.lineWidth = width; ctx.lineCap = 'round'; ctx.lineJoin = 'round'; ctx.stroke();
    };
    bandOf(r.forward.wires, INFLUENCE_FORWARD, band);
    bandOf(r.backward.wires, INFLUENCE_BACKWARD, r.forward.wires.length ? band * 0.55 : band);
    // what it reached, clear again in its own colours
    canvas.paintWireIds([...r.forward.wires, ...r.backward.wires]);
    const parts = new Set([...r.forward.parts, ...r.backward.parts, ...r.stops, ...r.origin, ...r.inside.map((x) => x.componentId), ...r.tunnels]);
    for (const id of r.origin) if (scene.wires.has(id)) parts.delete(id);
    canvas.paintPartIds(parts, d.look, shown);
    // and their bus values (a chip keeps WIRE_GAP from its wire: off the band)
    const nets = new Set<string>();
    for (const id of [...r.forward.wires, ...r.backward.wires]) { const n = scene.wireNet(id); if (n) nets.add(n.id); }
    this.drawChips(d, nets);
    const lineColor = onlyBack ? INFLUENCE_BACKWARD : INFLUENCE_FORWARD;
    // the two tunnels of a net: a dotted line, left out over the parts' bodies and the chips
    const blockers: Box[] = [...scene.components.values()].map((c) => ({ x0: c.bounds[0] - 2, y0: c.bounds[1] - 2, x1: c.bounds[0] + c.bounds[2] + 2, y1: c.bounds[1] + c.bounds[3] + 2 }));
    const chips = [...canvas.chipBoxes(), ...this.moreChips(d)].map((b) => ({ x0: b.x0 - 3, y0: b.y0 - 3, x1: b.x1 + 3, y1: b.y1 + 3 }));
    // the wires: a dotted line running along or over one would read as part of it (UI review), so it leaves them out
    const clear = Math.max(3, px(4));
    const wires: Box[] = canvas.wireSegments().map((s) => ({ x0: Math.min(s.a[0], s.b[0]) - clear, y0: Math.min(s.a[1], s.b[1]) - clear, x1: Math.max(s.a[0], s.b[0]) + clear, y1: Math.max(s.a[1], s.b[1]) + clear }));
    ctx.strokeStyle = lineColor;
    ctx.lineWidth = Math.max(px(1.5), px(2));
    ctx.setLineDash([px(6), px(5)]);
    ctx.lineCap = 'round';
    for (const link of r.links) for (let i = 1; i < link.length; i++) this.freeLine(d, link[i - 1], link[i], [...blockers, ...chips, ...wires]);
    ctx.setLineDash([]);
    const outline = (id: string, color: string, width: number, dash?: number[]) => {
      const c = scene.components.get(id);
      if (!c) return;
      const g = px(4);
      ctx.beginPath();
      ctx.roundRect(c.bounds[0] - g, c.bounds[1] - g, c.bounds[2] + 2 * g, c.bounds[3] + 2 * g, px(6));
      ctx.strokeStyle = color; ctx.lineWidth = width;
      ctx.setLineDash(dash ?? []);
      ctx.stroke();
      ctx.setLineDash([]);
    };
    for (const id of r.tunnels) outline(id, lineColor, Math.max(px(1), px(1.5)));
    for (const id of r.origin) outline(id, d.look.theme.navy, Math.max(px(2), px(3)));
    for (const id of r.stops) outline(id, '#8a5a00', Math.max(px(1.5), px(2)), [px(4), px(3)]);
    const avoid: Weighted[] = [
      ...chips.map((box) => ({ box, weight: 10 })),
      ...d.canvas.wireSegments().map((s) => ({ box: { x0: Math.min(s.a[0], s.b[0]) - 2, y0: Math.min(s.a[1], s.b[1]) - 2, x1: Math.max(s.a[0], s.b[0]) + 2, y1: Math.max(s.a[1], s.b[1]) + 2 }, weight: 3 })),
    ];
    for (const x of r.inside) {
      const c = scene.components.get(x.componentId);
      if (!c) continue;
      outline(x.componentId, INFLUENCE_FORWARD, Math.max(px(1.5), px(2)));
      // the chip: 11 px words, not below 50 % (too big there next to the circuit; the outline tells)
      if (x.places <= 0 || z < 0.5) continue;
      const text = placesText(x.name, x.places);
      ctx.font = `700 ${px(11)}px ${FONTS.ui}`;
      const w = ctx.measureText(text).width + 2 * px(5), h = px(11) * 1.35 + 2 * px(2);
      const body = { x0: c.bounds[0], y0: c.bounds[1], x1: c.bounds[0] + c.bounds[2], y1: c.bounds[1] + c.bounds[3] };
      const others = [...scene.components.values()].filter((o) => o.id !== c.id).map((o) => ({ box: { x0: o.bounds[0], y0: o.bounds[1], x1: o.bounds[0] + o.bounds[2], y1: o.bounds[1] + o.bounds[3] }, weight: 3 }));
      const at = placesAt(body, w, h, px(6), [...avoid, ...others]);
      ctx.beginPath();
      ctx.roundRect(at.x0, at.y0, w, h, px(6));
      ctx.fillStyle = d.look.theme.white; ctx.fill();
      ctx.strokeStyle = INFLUENCE_FORWARD; ctx.lineWidth = Math.max(px(1), px(1)); ctx.stroke();
      ctx.fillStyle = INFLUENCE_FORWARD; ctx.textAlign = 'left'; ctx.textBaseline = 'middle';
      ctx.fillText(text, at.x0 + px(5), (at.y0 + at.y1) / 2);
      avoid.push({ box: at, weight: 10 });
    }
    ctx.restore();
  }

  // A line drawn only where it runs over none of the blockers (short pieces tested one by one).
  private freeLine(d: OverlayDraw, a: Point, b: Point, blockers: Box[]): void {
    const { ctx } = d;
    const n = Math.max(1, Math.ceil(Math.hypot(b[0] - a[0], b[1] - a[1]) / 4));
    ctx.beginPath();
    let open = false;
    for (let i = 0; i < n; i++) {
      const p: Point = [a[0] + ((b[0] - a[0]) * i) / n, a[1] + ((b[1] - a[1]) * i) / n];
      const q: Point = [a[0] + ((b[0] - a[0]) * (i + 1)) / n, a[1] + ((b[1] - a[1]) * (i + 1)) / n];
      const free = !blockers.some((x) => segmentMeetsBox(p, q, x));
      if (free) { if (!open) ctx.moveTo(p[0], p[1]); ctx.lineTo(q[0], q[1]); open = true; } else open = false;
    }
    ctx.stroke();
  }
}
