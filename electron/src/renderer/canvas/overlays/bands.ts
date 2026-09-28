/* Bands around wires, drawn under them so each wire keeps its value colour
   (N-15, D-151):

   - signal groups (v1 E-04, D-087): in "Colors: Groups", a thin border of
     the group's colour on both sides of every wire of a grouped net;
   - instruction field colours (v1 C-07, D-078): while the Cycle View is
     shown, a band of each field's colour (Hallym MIPS's) around the wires
     of the splitter arms named after the fields of the instruction in the
     cycle on show (the engine's record.fieldPaths);
   - the active path (v1 C-08, V-04, D-079, D-099): while the Cycle View is
     shown, a navy band around the branch from the driver to the input each
     multiplexer selects (the engine's flow.activePath);
   - a net's highlight (v1 B-09, D-061): a translucent teal band.

   The parts are drawn after these, so a band's end never colours a part's
   body or outline (v1 left the bodies out of its bands).  Nothing here is
   saved: the groups themselves are the student's (hcs:ext), the rest is a
   view. */

import type { Point } from '../../../main/protocol.ts';
import type { CanvasOverlay, OverlayDraw } from '../canvas.ts';
import { rgba, wirePx } from '../paint.ts';
import { ACTIVE_PATH_COLOR, BAND, bandWithin, fieldColor, GROUP_COLORS, groupBand } from './logic.ts';

export class BandOverlay implements CanvasOverlay {
  showGroups = false;                          // Colors: Groups (for this run)
  activePath: [Point, Point][] = [];
  fields: { name: string; wires: string[] }[] = [];
  highlight: Set<string> | null = null;        // the highlighted net's wires

  under(d: OverlayDraw): void {
    const { ctx, scene, zoom: z } = d;
    const line = (a: Point, b: Point) => { ctx.moveTo(a[0], a[1]); ctx.lineTo(b[0], b[1]); };
    const wire = (id: string) => { const w = scene.wires.get(id); if (w) line(w.a, w.b); };
    ctx.save();
    ctx.lineJoin = 'round';
    // groups: a border of the group's colour, the wire's own width and a gap cleared in the middle (within the
    // band where chips are drawn: a chip keeps 3 from it)
    if (this.showGroups && scene.groups.size) {
      for (const g of scene.groups.values()) {
        const n = scene.net(g.net);
        if (!n || !n.wires.length) continue;
        const { inner, outer } = groupBand(z, n.width);
        ctx.beginPath();
        for (const id of n.wires) wire(id);
        ctx.lineCap = 'butt';
        ctx.strokeStyle = GROUP_COLORS[g.group];
        ctx.lineWidth = outer;
        ctx.stroke();
        ctx.strokeStyle = d.look.theme.paper;
        ctx.lineCap = 'square';
        ctx.lineWidth = inner;
        ctx.stroke();
      }
    }
    // the active path's band (a grid step wide: parallel wires' bands do not meet), then the field colours
    // a little narrower on top, so a wire that is both shows both
    if (this.activePath.length) {
      ctx.beginPath();
      for (const [a, b] of this.activePath) line(a, b);
      ctx.strokeStyle = rgba(ACTIVE_PATH_COLOR, 0.32);
      ctx.lineWidth = BAND;
      ctx.lineCap = 'round';
      ctx.stroke();
    }
    ctx.lineCap = 'butt';
    for (const f of this.fields) {
      ctx.beginPath();
      for (const id of f.wires) wire(id);
      ctx.strokeStyle = rgba(fieldColor(f.name), 0.55);
      ctx.lineWidth = BAND * 0.7;
      ctx.stroke();
    }
    if (this.highlight) {
      ctx.beginPath();
      let bits = 1;
      for (const id of this.highlight) { wire(id); bits = Math.max(bits, scene.wireNet(id)?.width ?? 1); }
      ctx.strokeStyle = 'rgba(0,169,165,0.3)';
      ctx.lineWidth = bandWithin((wirePx(z, bits) + 8) / z, z);
      ctx.lineCap = 'round';
      ctx.stroke();
    }
    ctx.restore();
  }
}
