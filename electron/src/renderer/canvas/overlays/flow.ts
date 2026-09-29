/* The Signal Flow (N-15, D-151; v1 P-07, V-06, D-063, D-101): the way a
   signal goes from a clicked part or wire, animated.  The shape comes from
   the engine's flow.path (v1 SignalFlowPath: the influence's engine, with
   distances along the wires, a part costing 30, a tunnel jump 30, a
   subcircuit's border 20, a splitter 10); here it is drawn at time t:

   - thin teal rails along both sides of the lit part of each wire, and
     white dashes inside the wire moving the signal's way (the next cycle,
     past a register: shorter dashes, fainter rails) -- the wire keeps its
     value colour between them;
   - a tunnel jump as a dotted arc in the tunnel's colour, bulging the way
     that covers the fewest bodies and chips (eight shapes, V-06), faint
     where it cannot help crossing one;
   - a part's outline glowing as the flow passes it; a subcircuit's border
     staying lit, with an "alu: 3 places" chip counting where the flow went
     inside;
   - rings at the ends (an output pin, a state part's input, a source when
     backward, an open port smaller) and a short label for pins, state
     inputs and sources, placed off parts, chips, wires and other labels,
     with a thin leader when it stands off; no place: the ring alone;
   - the point clicked, and a ↻ where a loop closes or ? where a
     multiplexer's select is not known (Active Path Only).

   Time is circuit units; the flow moves at a fixed speed on screen
   (120/240/480 px/s), so zooming does not change it.  Reduce Motion shows
   the whole way at once with arrows and step numbers instead.  The rails
   are drawn under the wires, the dashes and rings over the wires but under
   the parts (a part's body hides what runs under it), the rest over the
   parts. */

import type { FlowEndpoint, FlowPath, FlowSegment, Point } from '../../../main/protocol.ts';
import type { CanvasOverlay, CircuitCanvas, OverlayDraw } from '../canvas.ts';
import { sceneTunnelColors } from '../labels.ts';
import { FONTS, rgba, wirePx } from '../paint.ts';
import { type Box, boxesMeet } from '../shapes.ts';
import { along, arcBlockers, chooseArc, dashes, DASH_PX, FLOW_ACCENT, FLOW_ACCENT_DARK, GAP_PX, GLOW, JUMP, labelPlace, litLength, quad, RING_PX, ringRadius } from './logic.ts';
import { placesText } from './influence.ts';

const RAIL = rgba(FLOW_ACCENT, 130 / 255);
const RAIL_NEXT = rgba(FLOW_ACCENT, 60 / 255);
const DASH_COLOR = 'rgba(255,255,255,0.92)';
const LABEL_TEXT = 11;       // screen px
const FAINT = 0.3;

// A bus's dashes are a little thicker (screen px; never wider than the wire).
export const dashWidth = (bits: number): number => Math.min(2.4, 2.0 + 0.08 * Math.log2(Math.max(1, bits)));

// The endpoints that get a written label (V-06): output pins, state inputs, sources; an open port or a
// non-pin output end has its ring only.
export function labelled(e: FlowEndpoint, name: string): boolean {
  if (e.kind === 'unconnected') return false;
  if (e.kind === 'output') return name === 'Pin' || e.path.length > 0;
  return true;
}

export class FlowOverlay implements CanvasOverlay {
  path: FlowPath | null = null;
  t = 0;
  speedPx = 240;
  reduceMotion = false;
  smooth = false;
  held: number | null = null;     // a fixed time (tools/capture-screens.ts: the same frame every round)
  private last = 0;
  private canvas: CircuitCanvas | null = null;
  private timer = 0;
  private cache: { key: string; arcs: Point[]; labels: Map<number, Box>; blockers: Box[]; bodies: Box[] } | null = null;
  // What the last frame drew over the parts (tests): the jumps' arcs and the ends' labels.
  drawn: { arcs: { from: Point; to: Point; control: Point; color: string; whole: boolean }[]; labels: { text: string; box: Box }[] } = { arcs: [], labels: [] };

  attach(canvas: CircuitCanvas): void { this.canvas = canvas; }

  start(path: FlowPath): void {
    this.path = path;
    this.t = 0;
    this.last = 0;
    this.cache = null;
    this.canvas?.invalidate();
  }

  stop(): void {
    if (!this.path) return;
    this.path = null;
    this.drawn = { arcs: [], labels: [] };
    this.cache = null;
    clearTimeout(this.timer);
    this.canvas?.invalidate();
  }

  get running(): boolean { return this.path !== null; }

  // What the flow shows over the circuit, as boxes (circuit units): each jump's arc in short pieces, the ends'
  // labels.  Quick Attributes keeps off them (D-158, UI review of signal-flow.png).
  obstacles(fileId: string, circuitId: string): Box[] {
    const p = this.path;
    if (!p || p.circuitId !== circuitId || this.fileId !== fileId) return [];
    const out: Box[] = [];
    const pad = 4;
    p.jumps.forEach((j, i) => {
      if (j.path.length) return;
      const c: Point = this.cache?.arcs[i] ?? [(j.from[0] + j.to[0]) / 2, (j.from[1] + j.to[1]) / 2];
      let prev = j.from;
      for (let k = 1; k <= 16; k++) {
        const q = quad(j.from, c, j.to, k / 16);
        out.push({ x0: Math.min(prev[0], q[0]) - pad, y0: Math.min(prev[1], q[1]) - pad, x1: Math.max(prev[0], q[0]) + pad, y1: Math.max(prev[1], q[1]) + pad });
        prev = q;
      }
    });
    if (this.cache) out.push(...this.cache.labels.values());
    return out;
  }

  // Keep drawing while it flows: every frame when Smooth (60 fps), else about 30 a second.
  animating(): boolean {
    if (!this.path || this.reduceMotion) return false;
    if (this.smooth) return true;
    clearTimeout(this.timer);
    this.timer = window.setTimeout(() => this.canvas?.invalidate(false), 30);   // (the flow only: settled() stays)
    return false;
  }

  sceneChanged(): void { this.stop(); }   // another circuit or tab: the flow stops (v1)

  // The flow's time now (circuit units), advanced by the time since the last frame at this zoom.
  private advance(zoom: number): number {
    if (this.reduceMotion) return Number.MAX_VALUE / 4;
    if (this.held !== null) { this.t = this.held; this.last = 0; return this.t; }   // every layer at the same time
    const now = performance.now();
    if (this.last) this.t += (Math.min(100, now - this.last) / 1000) * (this.speedPx / zoom);
    this.last = now;
    return this.t;
  }

  private shown(d: OverlayDraw): FlowPath | null {
    const p = this.path;
    return p && p.circuitId === d.scene.circuitId && d.scene.fileId === this.fileId ? p : null;
  }
  fileId = '';
  moreChips = (_d: OverlayDraw): Box[] => [];    // other chips on the circuit (the bus values)

  under(d: OverlayDraw): void {
    const p = this.shown(d);
    if (!p) return;
    const t = this.advance(d.zoom);
    const { ctx, zoom: z } = d;
    ctx.save();
    const now = new Path2D(), next = new Path2D();
    const railW = 2 / z;
    for (const s of p.segments) {
      if (s.path.length) continue;
      const lit = litLength(s, t);
      if (lit <= 0) continue;
      const off = wirePx(z, s.width) / 2 / z;
      this.rail(s.cycle > 0 ? next : now, s, 0, lit, off, railW);
      this.rail(s.cycle > 0 ? next : now, s, 0, lit, -off - railW, railW);
    }
    ctx.fillStyle = RAIL; ctx.fill(now);
    ctx.fillStyle = RAIL_NEXT; ctx.fill(next);
    ctx.restore();
  }

  // A rectangle along the segment from a to b (distance from its start), offset across it.
  private rail(into: Path2D, s: FlowSegment, a: number, b: number, offset: number, w: number): void {
    const p = along(s.from, s.to, a), q = along(s.from, s.to, b);
    if (s.from[0] === s.to[0]) into.rect(p[0] + offset, Math.min(p[1], q[1]), w, Math.abs(q[1] - p[1]));
    else into.rect(Math.min(p[0], q[0]), p[1] + offset, Math.abs(q[0] - p[0]), w);
  }

  overWires(d: OverlayDraw): void {
    const p = this.shown(d);
    if (!p) return;
    const { ctx, zoom: z } = d;
    const t = this.reduceMotion ? Number.MAX_VALUE / 4 : this.t;
    ctx.save();
    if (!this.reduceMotion) {
      const period = (DASH_PX + GAP_PX) / z;
      const pieces = new Path2D();
      for (const s of p.segments) {
        if (s.path.length) continue;
        const w = Math.min(wirePx(z, s.width) / z - 0.6 / z, dashWidth(s.width) / z);
        for (const [a, b] of dashes(s, t, period, (s.cycle > 0 ? DASH_PX / 2 : DASH_PX) / z)) this.rail(pieces, s, a, b, -w / 2, w);
      }
      ctx.fillStyle = DASH_COLOR;
      ctx.fill(pieces);
    }
    // rings at the ends reached (under the parts: a part's body hides the half inside it)
    for (const e of p.endpoints) {
      if (e.path.length || t < e.time) continue;
      const r = ringRadius(e.kind === 'unconnected' ? RING_PX * 0.6 : RING_PX, z);
      ctx.beginPath(); ctx.arc(e.at[0], e.at[1], r, 0, Math.PI * 2);
      ctx.strokeStyle = d.look.theme.white; ctx.lineWidth = 4 / z; ctx.stroke();
      ctx.strokeStyle = FLOW_ACCENT; ctx.lineWidth = 2 / z; ctx.stroke();
    }
    ctx.restore();
  }

  // What the arcs and labels keep off, and where they go: once per flow, circuit and zoom.
  private layout(d: OverlayDraw, p: FlowPath): NonNullable<FlowOverlay['cache']> {
    const { scene, zoom: z, canvas } = d;
    const more = this.moreChips(d);
    const key = `${z.toFixed(4)} ${scene.modelVersion} ${more.length}`;
    if (this.cache && this.cache.key === key) return this.cache;
    const chips = [...canvas.chipBoxes(), ...more];
    const parts = [...scene.components.values()];
    const bodies: Box[] = parts.map((c) => ({ x0: c.bounds[0] - 1, y0: c.bounds[1] - 1, x1: c.bounds[0] + c.bounds[2] + 1, y1: c.bounds[1] + c.bounds[3] + 1 }));
    const gap = 4 / z;
    const hard: Box[] = [...bodies, ...chips.map((b) => ({ x0: b.x0 - gap, y0: b.y0 - gap, x1: b.x1 + gap, y1: b.y1 + gap })),
      ...canvas.wireSegments().map((s) => ({ x0: Math.min(s.a[0], s.b[0]) - 2, y0: Math.min(s.a[1], s.b[1]) - 2, x1: Math.max(s.a[0], s.b[0]) + 2, y1: Math.max(s.a[1], s.b[1]) + 2 }))];
    const labels = new Map<number, Box>();
    const placed: Box[] = [];
    const ring = ringRadius(RING_PX, z);
    p.endpoints.forEach((e, i) => {
      if (e.path.length) return;
      const c = scene.components.get(e.componentId);
      if (!c || !labelled(e, c.name) || c.name === 'Splitter' || e.label === (c.attrs.label ?? '').trim()) return;
      const size = LABEL_TEXT / z;
      const w = canvas.measure(e.label, 'ui', size, 700) + 2 * (4 / z), h = size * 1.3 + 2 * (1.5 / z);
      const box = labelPlace(e.at, w, h, ring, z, hard, placed);
      if (box) { labels.set(i, box); placed.push(box); }
    });
    const blockersFor = (j: FlowPath['jumps'][number]) => arcBlockers(j, parts, [...chips, ...placed]);
    const arcs = p.jumps.map((j) => chooseArc(j, blockersFor(j)));
    this.cache = { key, arcs, labels, blockers: [...bodies, ...chips, ...placed], bodies };
    return this.cache;
  }

  over(d: OverlayDraw): void {
    const p = this.shown(d);
    if (!p) return;
    const { ctx, scene, zoom: z } = d;
    const t = this.reduceMotion ? Number.MAX_VALUE / 4 : this.t;
    const looping = t >= p.total;
    const L = this.layout(d, p);
    const px = (v: number) => v / z;
    ctx.save();
    // tunnel jumps: dotted arcs in the tunnel's colour, faint over a body or a chip
    const colors = sceneTunnelColors(scene);
    const tunnelAt = (q: Point) => [...scene.components.values()].find((c) => c.name === 'Tunnel' && c.lib === 'Wiring' && c.loc[0] === q[0] && c.loc[1] === q[1]);
    ctx.lineWidth = px(2);
    ctx.setLineDash([px(4), px(4)]);
    ctx.lineCap = 'round';
    const drawn: FlowOverlay['drawn'] = { arcs: [], labels: [] };
    this.drawn = drawn;
    p.jumps.forEach((j, i) => {
      if (j.path.length || t < j.start) return;
      const f = Math.min(1, (t - j.start) / JUMP);
      const tun = tunnelAt(j.from);
      const color = (tun && colors.get(tun.attrs.label ?? '')) ?? FLOW_ACCENT;
      drawn.arcs.push({ from: j.from, to: j.to, control: L.arcs[i], color, whole: f >= 1 });
      const ends = L.blockers.filter((b) => !(j.from[0] >= b.x0 && j.from[0] <= b.x1 && j.from[1] >= b.y0 && j.from[1] <= b.y1)
        && !(j.to[0] >= b.x0 && j.to[0] <= b.x1 && j.to[1] >= b.y0 && j.to[1] <= b.y1));
      const n = 24;
      let offset = 0;
      for (let k = 0; k < n; k++) {
        const u0 = (f * k) / n, u1 = (f * (k + 1)) / n;
        const a = quad(j.from, L.arcs[i], j.to, u0), b = quad(j.from, L.arcs[i], j.to, u1);
        const m = quad(j.from, L.arcs[i], j.to, (u0 + u1) / 2);
        const over = ends.some((x) => m[0] >= x.x0 && m[0] <= x.x1 && m[1] >= x.y0 && m[1] <= x.y1);
        ctx.globalAlpha = over ? FAINT : 1;
        ctx.lineDashOffset = -offset;
        ctx.beginPath(); ctx.moveTo(a[0], a[1]); ctx.lineTo(b[0], b[1]);
        ctx.strokeStyle = color; ctx.stroke();
        offset += Math.hypot(b[0] - a[0], b[1] - a[1]);
      }
    });
    ctx.globalAlpha = 1;
    ctx.setLineDash([]);
    ctx.lineDashOffset = 0;
    // parts glowing as the flow passes; a subcircuit's border stays lit
    const inside = new Map<string, number>();
    for (const x of p.passes) {
      if (x.path.length) { if (t >= x.time) inside.set(x.path[0], (inside.get(x.path[0]) ?? 0) + 1); continue; }
      const since = t - x.time;
      if (since < 0) continue;
      let alpha: number;
      if (this.reduceMotion || x.boundary) alpha = 160 / 255;
      else if (looping || since > GLOW) continue;
      else alpha = (200 / 255) * Math.max(0, 1 - since / GLOW);
      const c = scene.components.get(x.componentId);
      if (!c) continue;
      const g = px(3);
      ctx.beginPath();
      ctx.roundRect(c.bounds[0] - g, c.bounds[1] - g, c.bounds[2] + 2 * g, c.bounds[3] + 2 * g, px(6));
      ctx.strokeStyle = rgba(FLOW_ACCENT, alpha); ctx.lineWidth = px(2.5); ctx.stroke();
    }
    // "alu: 3 places": where the flow went inside a subcircuit instance
    const avoid = [...L.blockers];
    for (const [id, n] of inside) {
      const c = scene.components.get(id);
      if (!c || z < 0.5) continue;
      const text = placesText(c.name, n);
      ctx.font = `700 ${px(11)}px ${FONTS.ui}`;
      const w = ctx.measureText(text).width + px(10), h = px(11) * 1.35 + px(4);
      const [bx, by, bw, bh] = c.bounds, gap = px(6);
      const cands: Box[] = [[bx + bw - w, by - gap - h], [bx, by - gap - h], [bx + bw - w, by + bh + gap], [bx, by + bh + gap], [bx + bw / 2 - w / 2, by - gap - h]]
        .map(([x, y]) => ({ x0: x, y0: y, x1: x + w, y1: y + h }));
      const at = cands.find((b) => !avoid.some((o) => boxesMeet(o, b))) ?? cands[0];
      avoid.push(at);
      ctx.beginPath(); ctx.roundRect(at.x0, at.y0, w, h, px(6));
      ctx.fillStyle = d.look.theme.white; ctx.fill();
      ctx.strokeStyle = FLOW_ACCENT_DARK; ctx.lineWidth = px(1); ctx.stroke();
      ctx.fillStyle = FLOW_ACCENT_DARK; ctx.textAlign = 'left'; ctx.textBaseline = 'middle';
      ctx.fillText(text, at.x0 + px(5), (at.y0 + at.y1) / 2);
    }
    // the ends' labels, with a thin leader when they stand off the ring
    p.endpoints.forEach((e, i) => {
      const box = L.labels.get(i);
      if (!box || t < e.time) return;
      const ring = ringRadius(RING_PX, z);
      const lx = Math.max(box.x0, Math.min(e.at[0], box.x1)), ly = Math.max(box.y0, Math.min(e.at[1], box.y1));
      const gapTo = Math.hypot(lx - e.at[0], ly - e.at[1]);
      if (gapTo > ring + px(8)) {
        // a thin leader, left out over the parts' bodies (it never runs over a part's words)
        const ux = (lx - e.at[0]) / gapTo, uy = (ly - e.at[1]) / gapTo;
        const a: Point = [e.at[0] + ux * ring, e.at[1] + uy * ring];
        const n = Math.max(1, Math.ceil(Math.hypot(lx - a[0], ly - a[1]) / 3));
        ctx.beginPath();
        for (let k = 0; k < n; k++) {
          const p0: Point = [a[0] + ((lx - a[0]) * k) / n, a[1] + ((ly - a[1]) * k) / n];
          const p1: Point = [a[0] + ((lx - a[0]) * (k + 1)) / n, a[1] + ((ly - a[1]) * (k + 1)) / n];
          const m: Point = [(p0[0] + p1[0]) / 2, (p0[1] + p1[1]) / 2];
          if (L.bodies.some((b) => m[0] > b.x0 && m[0] < b.x1 && m[1] > b.y0 && m[1] < b.y1)) continue;
          ctx.moveTo(p0[0], p0[1]); ctx.lineTo(p1[0], p1[1]);
        }
        ctx.strokeStyle = FLOW_ACCENT; ctx.lineWidth = px(1); ctx.stroke();
      }
      ctx.beginPath(); ctx.roundRect(box.x0, box.y0, box.x1 - box.x0, box.y1 - box.y0, px(5));
      ctx.fillStyle = d.look.theme.white; ctx.fill();
      ctx.strokeStyle = FLOW_ACCENT; ctx.lineWidth = px(1); ctx.stroke();
      drawn.labels.push({ text: e.label, box });
      ctx.font = `700 ${px(LABEL_TEXT)}px ${FONTS.ui}`;
      ctx.fillStyle = d.look.theme.ink; ctx.textAlign = 'left'; ctx.textBaseline = 'middle';
      ctx.fillText(e.label, box.x0 + px(4), (box.y0 + box.y1) / 2 + px(0.5));
    });
    // the point clicked
    if (p.click) {
      ctx.beginPath(); ctx.arc(p.click[0], p.click[1], px(4), 0, Math.PI * 2);
      ctx.fillStyle = d.look.theme.navy; ctx.fill();
    }
    // ↻ where a loop closes, ? where a select is not known: off the part's top-left corner
    const badge = (id: string, text: string) => {
      const c = scene.components.get(id);
      if (!c) return;
      const r = px(7), cx = c.bounds[0] - r, cy = c.bounds[1] - r;
      ctx.beginPath(); ctx.arc(cx, cy, r, 0, Math.PI * 2);
      ctx.fillStyle = d.look.theme.white; ctx.fill();
      ctx.strokeStyle = FLOW_ACCENT_DARK; ctx.lineWidth = px(1.2); ctx.stroke();
      ctx.font = `700 ${px(10)}px ${FONTS.ui}`;
      ctx.fillStyle = FLOW_ACCENT_DARK; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
      ctx.fillText(text, cx, cy + px(0.5));
    };
    for (const id of p.loops) badge(id, '↻');
    for (const id of p.undetermined) badge(id, '?');
    // Reduce Motion: an arrow on each long segment and the parts' order
    if (this.reduceMotion) {
      ctx.fillStyle = FLOW_ACCENT_DARK;
      for (const s of p.segments) {
        if (s.path.length || s.length < 20) continue;
        const m: Point = [(s.from[0] + s.to[0]) / 2, (s.from[1] + s.to[1]) / 2];
        const dx = Math.sign(s.to[0] - s.from[0]), dy = Math.sign(s.to[1] - s.from[1]), a = px(5);
        ctx.beginPath();
        ctx.moveTo(m[0] + dx * a, m[1] + dy * a);
        ctx.lineTo(m[0] - dx * a - dy * a, m[1] - dy * a + dx * a);
        ctx.lineTo(m[0] - dx * a + dy * a, m[1] - dy * a - dx * a);
        ctx.closePath(); ctx.fill();
      }
      let n = 1;
      for (const x of p.passes) {
        if (x.path.length || x.boundary) continue;
        const c = scene.components.get(x.componentId);
        if (!c) continue;
        const r = px(7), cx = c.bounds[0] + c.bounds[2] + r, cy = c.bounds[1] - r;
        ctx.beginPath(); ctx.arc(cx, cy, r, 0, Math.PI * 2);
        ctx.fillStyle = FLOW_ACCENT_DARK; ctx.fill();
        ctx.font = `700 ${px(10)}px ${FONTS.ui}`;
        ctx.fillStyle = d.look.theme.white; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
        ctx.fillText(String(n++), cx, cy + px(0.5));
        ctx.fillStyle = FLOW_ACCENT_DARK;
      }
    }
    ctx.restore();
  }

  // What the flow shows now (tests): its time, the segments the front has reached, the labels placed, the
  // jumps of this circuit, and what the last frame drew (the arcs, the labels).
  state(): { t: number; total: number; lit: number; labels: string[]; jumps: number; drawn: FlowOverlay['drawn'] } | null {
    const p = this.path;
    if (!p) return null;
    const t = this.reduceMotion ? Number.MAX_VALUE / 4 : this.t;
    return {
      t, total: p.total,
      lit: p.segments.filter((s) => !s.path.length && litLength(s, t) > 0).length,
      labels: [...(this.cache?.labels.keys() ?? [])].map((i) => p.endpoints[i].label),
      jumps: p.jumps.filter((j) => !j.path.length).length,
      drawn: this.drawn,
    };
  }
}
