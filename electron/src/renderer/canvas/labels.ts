/* What is written around the parts (N-05; v1 S-01, S-02, S-05, S-06, S-07,
   S-08, S-12, S-24, E-03) -- placed so that it never covers a wire, a part
   or another chip:

   - label chips: a part's Label attribute, on the side its label location
     says (Pin: west …; the others above), else the first free side;
   - value chips: a narrow part's value (a 32-bit register), below it or on
     the first free side;
   - a splitter's arm ranges ([7:0]) on the side of its spine away from the
     arm wires when that side is free, else just past the arm's end;
   - a bus's width (/32) beside its longest wire, dropped where it cannot
     stand free;
   - port names of the original parts, outside the part and off the way the
     wire leaves, on hover or from 200 % (computed per frame, portNames()).
   A pin or probe whose port has a tunnel of the same name on it shows the
   name once, on the tunnel (S-12).  Tunnel colours: v1's palette and rule
   (S-24): a colour per name, a different one for different names nearby. */

import type { Component } from '../../main/protocol.ts';
import { splitterArms } from './parts/wiring.ts';
import { ranges } from './parts/common.ts';
import { rendererFor } from './registry.ts';
import type { Scene } from './scene.ts';
import { type Box, boxesMeet } from './shapes.ts';
import { TUNNEL_PALETTE } from './tokens.ts';
import { wirePx } from './paint.ts';
import type { WireMarks } from './wires.ts';

export type Measure = (text: string, font: 'ui' | 'code', size: number, weight: number) => number;
// A rough measure (tests, no fonts): Pretendard ~0.56 em, D2Coding 0.5 em.
export const roughMeasure: Measure = (t, font, size) => t.length * size * (font === 'code' ? 0.5 : 0.56);

export type ChipKind = 'label' | 'value' | 'arm' | 'width';
export interface Chip {
  kind: ChipKind;
  owner: string;              // component id (or the net for a width mark)
  text: string;
  box: Box;                   // circuit units
  font: 'ui' | 'code';
  size: number;
  weight: number;
  port?: number;              // a value chip: the port whose value it shows
  slash?: [number, number, number, number];   // a width mark's slash across its wire
}

/* Chip spacing, circuit units (D-137; UI review of #425: a chip touched an open port's ring).  A chip
   stands CHIP_GAP from its own part -- past the port rings on the part's edge and a gap -- and keeps
   CLEAR from other parts (their rings), wires and other chips.  Chips are drawn from 70 % (MIN_TEXT_PX),
   where a ring is 3.3 units and the 3 px gap 4.3 units. */
export const CHIP_GAP = 8;
export const CLEAR = 3;
/* A label or value chip keeps this much from a wire's centre line when it can (N-15, UI checklist 12, v1
   X-04 LabelOverlay.WIRE_GAP): the band around a highlighted wire (the active path, the field colours, a
   Signal Flow's rails) is 5 units wide each side, and a chip must not touch it.  Where no place keeps it,
   CLEAR from the wire's stroke is enough (as before). */
export const WIRE_GAP = 8;
const RING = 4;
const PAD_X = 3.5, PAD_Y = 2;

// ---- the free-space index ---------------------------------------------------------------------

export class Obstacles {
  private cells = new Map<string, { box: Box; owner: string }[]>();
  private static readonly CELL = 50;
  add(box: Box, owner: string): void {
    for (const k of this.keys(box)) {
      const l = this.cells.get(k);
      if (l) l.push({ box, owner }); else this.cells.set(k, [{ box, owner }]);
    }
  }
  hits(box: Box, ignore?: string): boolean {
    for (const k of this.keys(box)) {
      for (const o of this.cells.get(k) ?? []) if (o.owner !== ignore && boxesMeet(o.box, box)) return true;
    }
    return false;
  }
  // How much of `box` the obstacles cover (each obstacle once).
  covered(box: Box): number {
    const seen = new Set<{ box: Box; owner: string }>();
    let area = 0;
    for (const k of this.keys(box)) {
      for (const o of this.cells.get(k) ?? []) {
        if (seen.has(o) || !boxesMeet(o.box, box)) continue;
        seen.add(o);
        area += (Math.min(box.x1, o.box.x1) - Math.max(box.x0, o.box.x0)) * (Math.min(box.y1, o.box.y1) - Math.max(box.y0, o.box.y0));
      }
    }
    return area;
  }
  private *keys(b: Box): Generator<string> {
    const C = Obstacles.CELL;
    for (let x = Math.floor(b.x0 / C); x <= Math.floor(b.x1 / C); x++) {
      for (let y = Math.floor(b.y0 / C); y <= Math.floor(b.y1 / C); y++) yield `${x},${y}`;
    }
  }
}

const inflate = (b: Box, d: number): Box => ({ x0: b.x0 - d, y0: b.y0 - d, x1: b.x1 + d, y1: b.y1 + d });
const boundsBox = (c: Component): Box => ({ x0: c.bounds[0], y0: c.bounds[1], x1: c.bounds[0] + c.bounds[2], y1: c.bounds[1] + c.bounds[3] });

function obstacles(scene: Scene, marks: WireMarks, extent?: (c: Component) => Box): Obstacles {
  const o = new Obstacles();
  // a part (as drawn: a grown tunnel's tag too) with its port rings; a wire with its stroke
  for (const c of scene.components.values()) if (c.bounds[2] > 0 || c.bounds[3] > 0) o.add(inflate(extent ? extent(c) : boundsBox(c), RING), c.id);
  for (const s of marks.segments) {
    o.add(inflate({ x0: Math.min(s.a[0], s.b[0]), y0: Math.min(s.a[1], s.b[1]), x1: Math.max(s.a[0], s.b[0]), y1: Math.max(s.a[1], s.b[1]) }, 2), `wire:${s.id}`);
  }
  return o;
}

// ---- tunnel colours (v1 TunnelColors) -------------------------------------------------------------

export const NEAR = 400;

// FNV-1a over the name's UTF-8 bytes: the same index in every run (v1's).
export function paletteIndex(name: string): number {
  let h = 0x811c9dc5;
  for (const b of new TextEncoder().encode(name)) {
    h ^= b;
    h = Math.imul(h, 0x01000193);
  }
  return ((h % TUNNEL_PALETTE.length) + TUNNEL_PALETTE.length) % TUNNEL_PALETTE.length;
}

// Name → colour for a circuit's tunnels: a colour the student picked (Tunnel Color, hcs:ext: every
// tunnel of that name has it) first, then the name's own colour, the next free one when a different
// name nearby (NEAR) already has it (v1 TunnelColorStore.colors).  Names in order; the same input,
// the same answer.
export function tunnelColors(tunnels: { name: string; at: [number, number]; color?: string }[]): Map<string, string> {
  const where = new Map<string, [number, number][]>();
  const chosen = new Map<string, string>();
  for (const t of tunnels) {
    if (!t.name) continue;
    where.set(t.name, [...(where.get(t.name) ?? []), t.at]);
    if (t.color) chosen.set(t.name, t.color.toLowerCase());
  }
  const names = [...where.keys()].sort();
  const picked = new Map<string, number>();
  for (const [n, c] of chosen) picked.set(n, (TUNNEL_PALETTE as readonly string[]).indexOf(c));
  const near = (a: [number, number][], b: [number, number][]) => a.some((p) => b.some((q) => Math.abs(p[0] - q[0]) <= NEAR && Math.abs(p[1] - q[1]) <= NEAR));
  for (const n of names) {
    if (picked.has(n)) continue;
    const used = new Set<number>();
    for (const [m, i] of picked) if (near(where.get(n)!, where.get(m)!)) used.add(i);
    const start = paletteIndex(n);
    let pick = start;
    for (let k = 0; k < TUNNEL_PALETTE.length; k++) {
      const c = (start + k) % TUNNEL_PALETTE.length;
      if (!used.has(c)) { pick = c; break; }
    }
    picked.set(n, pick);
  }
  return new Map(names.map((n) => [n, chosen.get(n) ?? TUNNEL_PALETTE[picked.get(n)!]]));
}

export function sceneTunnelColors(scene: Scene): Map<string, string> {
  const t: { name: string; at: [number, number]; color?: string }[] = [];
  for (const c of scene.components.values()) if (c.name === 'Tunnel' && c.lib === 'Wiring') t.push({ name: c.attrs.label ?? '', at: c.loc, color: c.ext?.color });
  return tunnelColors(t);
}

// ---- chips ------------------------------------------------------------------------------------------

type Side = 'north' | 'south' | 'east' | 'west';

function candidates(b: Box, w: number, h: number, first: Side, GAP = 3): Box[] {
  const cx = (b.x0 + b.x1) / 2, cy = (b.y0 + b.y1) / 2;
  const at = (x: number, y: number): Box => ({ x0: x, y0: y, x1: x + w, y1: y + h });
  const sides: Record<Side, Box[]> = {
    north: [at(cx - w / 2, b.y0 - GAP - h), at(b.x0, b.y0 - GAP - h), at(b.x1 - w, b.y0 - GAP - h)],
    south: [at(cx - w / 2, b.y1 + GAP), at(b.x0, b.y1 + GAP), at(b.x1 - w, b.y1 + GAP)],
    west: [at(b.x0 - GAP - w, cy - h / 2), at(b.x0 - GAP - w, b.y0 - h - GAP)],
    east: [at(b.x1 + GAP, cy - h / 2), at(b.x1 + GAP, b.y0 - h - GAP)],
  };
  const opposite: Record<Side, Side> = { north: 'south', south: 'north', east: 'west', west: 'east' };
  const order: Side[] = [first, opposite[first], ...(['north', 'south', 'east', 'west'] as Side[]).filter((s) => s !== first && s !== opposite[first])];
  return order.flatMap((s) => sides[s]);
}

// A one-port part whose port has a tunnel of its own name on it: its name shows once, on the tunnel (S-12).
function namedByTunnel(scene: Scene, c: Component, label: string): boolean {
  if (c.ports.length !== 1) return false;
  const [x, y] = c.ports[0].loc;
  for (const t of scene.components.values()) {
    if (t.name === 'Tunnel' && t.lib === 'Wiring' && t.loc[0] === x && t.loc[1] === y && (t.attrs.label ?? '') === label) return true;
  }
  return false;
}

export function layoutChips(scene: Scene, marks: WireMarks, measure: Measure, extent?: (c: Component) => Box): Chip[] {
  const obs = obstacles(scene, marks, extent);
  const bands = new Obstacles();   // the wires with the room of a band around them
  for (const s of marks.segments) {
    bands.add(inflate({ x0: Math.min(s.a[0], s.b[0]), y0: Math.min(s.a[1], s.b[1]), x1: Math.max(s.a[0], s.b[0]), y1: Math.max(s.a[1], s.b[1]) }, WIRE_GAP), `wire:${s.id}`);
  }
  const chips: Chip[] = [];
  const place = (c: Omit<Chip, 'box'>, w: number, h: number, around: Box, first: Side, force: boolean): void => {
    // next to the part first, then a step and two further out -- off the wires' bands when a place is,
    // else just off the wires; with nothing free, where least is covered
    const all = [CHIP_GAP, CHIP_GAP + 10, CHIP_GAP + 20].flatMap((gap) => candidates(around, w, h, first, gap));
    let chosen = all.find((b) => !obs.hits(inflate(b, CLEAR)) && !bands.hits(b)) ?? all.find((b) => !obs.hits(inflate(b, CLEAR))) ?? null;
    if (!chosen && !force) return;
    if (!chosen) {
      let best = Infinity;
      for (const b of all) {
        const a = obs.covered(inflate(b, CLEAR));
        if (a < best - 1e-9) { best = a; chosen = b; }
      }
    }
    chips.push({ ...c, box: chosen! });
    obs.add(chosen!, `chip:${chips.length}`);
  };
  const comps = [...scene.components.values()];
  // labels first (the student's own names), then values
  for (const c of comps) {
    const r = rendererFor(c);
    const label = c.attrs.label ?? '';
    if (r.label !== 'chip' || !label || namedByTunnel(scene, c, label)) continue;
    const size = 10, w = measure(label, 'ui', size, 500) + 2 * PAD_X, h = size + 2 * PAD_Y;
    const loc = (c.attrs.labelloc ?? 'north') as Side | 'center';
    place({ kind: 'label', owner: c.id, text: label, font: 'ui', size, weight: 500 }, w, h, boundsBox(c), loc === 'center' ? 'south' : loc, true);
  }
  for (const c of comps) {
    const r = rendererFor(c);
    if (!r.valueChip) continue;
    const v = r.valueChip(c, { value: () => undefined, body: undefined });
    if (!v) continue;
    const size = 10, w = measure(v.text, 'code', size, 600) + 2 * PAD_X, h = size + 2 * PAD_Y;
    const port = c.ports.findIndex((q) => q.name === 'Q' || q.name === 'out');
    place({ kind: 'value', owner: c.id, text: v.text, font: 'code', size, weight: 600, port }, w, h, boundsBox(c), 'south', true);
  }
  // splitter arms (v1 armRect: the spine's far side when free, else past the arm's end)
  for (const c of comps) {
    if (c.lib !== 'Wiring' || c.name !== 'Splitter') continue;
    const arms = splitterArms(c);
    const facing = c.facing ?? 'east';
    const size = 9.5;
    // "[31:26] op": the range and the arm's name the student gave it in the Splitter editor (hcs:ext, N-12),
    // where it stands free; where it would cover a part or a wire, the range alone (as before names).
    const names = c.ext?.arms ?? [];
    const armsShown = c.ports.slice(1).map((q, k) => {
      const r = ranges(arms[k] ?? []);
      return { q, range: r, named: r && names[k] ? `${r} ${names[k]}` : r };
    }).filter((a) => a.range);
    const b = boundsBox(c);
    const horizontal = facing === 'east' || facing === 'west';
    const farSide = (texts: string[]): Box[] => armsShown.map(({ q }, k) => {
      const w = measure(texts[k], 'ui', size, 700) + 2;
      const x = facing === 'west' ? b.x1 + 3 : b.x0 - w - 4;
      return { x0: x, y0: q.loc[1] - size / 2 - 0.5, x1: x + w, y1: q.loc[1] + size / 2 + 0.5 };
    });
    const isFree = (boxes: Box[]) => horizontal && boxes.every((r) => !obs.hits(r, c.id));
    const withNames = armsShown.map((a) => a.named);
    const plain = armsShown.map((a) => a.range);
    const namedBoxes = farSide(withNames);
    const plainBoxes = farSide(plain);
    const useNames = withNames.some((t, k) => t !== plain[k]) && isFree(namedBoxes);
    const free = useNames || isFree(plainBoxes);
    armsShown.forEach(({ q }, k) => {
      const text = useNames ? withNames[k] : plain[k];
      const w = measure(text, 'ui', size, 700) + 2;
      let box: Box;
      if (free) box = (useNames ? namedBoxes : plainBoxes)[k];
      else if (horizontal) {
        const x = facing === 'west' ? q.loc[0] - w - 2 : q.loc[0] + 2;
        box = { x0: x, y0: q.loc[1] - 2 - size, x1: x + w, y1: q.loc[1] - 2 };
      } else {
        const y = facing === 'north' ? q.loc[1] - 3 - size : q.loc[1] + 2;
        box = { x0: q.loc[0] + 3, y0: y, x1: q.loc[0] + 3 + w, y1: y + size };
      }
      chips.push({ kind: 'arm', owner: c.id, text, box, font: 'ui', size, weight: 700 });
      obs.add(box, `arm:${c.id}`);
    });
  }
  // bus widths: beside the wire, dropped when neither side is free (E-03)
  for (const m of marks.widths) {
    const text = `${m.bits}`;
    const size = 9, w = measure(text, 'code', size, 700) + 3, h = size + 2;
    const [x, y] = m.at;
    const sides: Box[] = m.vertical
      ? [{ x0: x + 5, y0: y - h / 2, x1: x + 5 + w, y1: y + h / 2 }, { x0: x - 5 - w, y0: y - h / 2, x1: x - 5, y1: y + h / 2 }]
      : [{ x0: x - w / 2, y0: y - 5 - h, x1: x + w / 2, y1: y - 5 }, { x0: x - w / 2, y0: y + 5, x1: x + w / 2, y1: y + 5 + h }];
    const box = sides.find((b) => !obs.hits(b, `wire:${m.wire}`));
    if (!box) continue;
    const slash: [number, number, number, number] = m.vertical ? [x - 4, y + 3, x + 4, y - 3] : [x - 3, y + 4, x + 3, y - 4];
    chips.push({ kind: 'width', owner: m.net, text, box, font: 'code', size, weight: 700, slash });
    obs.add(box, `width:${m.net}`);
  }
  return chips;
}

// ---- port names (v1 S-06: outside, off the wire's way; on hover or from 200 %) --------------------

export interface PortName { text: string; x: number; y: number; anchor: 'start' | 'end'; baseline: 'bottom' | 'top'; size: number }

// written: what the part's body already says (its D, Q …): those names are not written again.
/* The gap kept between written things and wires, port marks and each other (CSS px on screen, D-137):
   a name, a chip or a mark never touches a line it does not belong to (UI review of #425). */
export const MIN_GAP_PX = 3;

export interface WireLine { a: [number, number]; b: [number, number]; bits: number }

// written: what the part's body already says (its D, Q ...): those names are not written again.
// wires: the lines near the part; a name keeps MIN_GAP_PX from their stroke at this zoom, or is left out.
export function portNames(c: Component, zoom: number, measure: Measure, taken: Box[], written: ReadonlySet<string> = new Set(),
                          wires: WireLine[] = []): PortName[] {
  const r = rendererFor(c);
  // a subcircuit drawn by the student names its ports itself; the original's default box does not (v1 S-08)
  if (r.portNames !== 'hover' || (c.appearance && !c.appearance.default)) return [];
  const size = Math.min(13, 9 * Math.sqrt(Math.max(1, zoom / 2))) / zoom;   // 9 px up to 200 %, a little more beyond
  const b = boundsBox(c);
  const out: PortName[] = [];
  const gap = MIN_GAP_PX / zoom;
  const half = (bits: number) => wirePx(zoom, bits) / 2 / zoom;           // a wire's half width, circuit units
  const clear = wires.map((w) => {                                        // each wire's stroke plus the gap
    const d = half(w.bits) + gap;
    return { x0: Math.min(w.a[0], w.b[0]) - d, y0: Math.min(w.a[1], w.b[1]) - d, x1: Math.max(w.a[0], w.b[0]) + d, y1: Math.max(w.a[1], w.b[1]) + d };
  });
  for (const q of c.ports) {
    if (!q.name || written.has(q.name)) continue;
    const [x, y] = q.loc;
    // off the way the wire leaves: its stroke's half width and the gap away from the port's line
    const off = half(q.width) + gap;
    let n: PortName;
    if (x <= b.x0) n = { text: q.name, x: x - gap, y: y - off, anchor: 'end', baseline: 'bottom', size };
    else if (y >= b.y1) n = { text: q.name, x: x + off, y: y + gap, anchor: 'start', baseline: 'top', size };
    else if (y <= b.y0) n = { text: q.name, x: x + off, y: y - gap, anchor: 'start', baseline: 'bottom', size };
    else n = { text: q.name, x: x + gap, y: y - off, anchor: 'start', baseline: 'bottom', size };
    const w = measure(n.text, 'ui', size, 500);
    const box = { x0: n.anchor === 'end' ? n.x - w : n.x, x1: n.anchor === 'end' ? n.x : n.x + w, y0: n.baseline === 'bottom' ? n.y - size : n.y, y1: n.baseline === 'bottom' ? n.y : n.y + size };
    if (taken.some((t) => boxesMeet(t, box))) continue;   // never over a chip or another name
    if (clear.some((t) => boxesMeet(t, box))) continue;   // never on or next to a wire
    taken.push(box);
    out.push(n);
  }
  return out;
}

// ---- room for a tunnel's long name (D-137) ------------------------------------------------------

/* How far a tunnel's tag may grow past its engine bounds, away from its point, before it would come
   within CLEAR of another part or a wire that is not its own (the wires at its point are): east or west
   facing, along the tag; north or south, across it (the name runs across), both sides alike, so the
   room is twice the smaller side's.  At most 80 units. */
export function tunnelRoom(scene: Scene, marks: WireMarks, c: Component): number {
  const [x0, y0, w, h] = c.bounds;
  const x1 = x0 + w, y1 = y0 + h;
  const facing = c.facing ?? 'east';
  const boxes: Box[] = [];
  for (const o of scene.components.values()) if (o.id !== c.id && (o.bounds[2] > 0 || o.bounds[3] > 0)) boxes.push(boundsBox(o));
  for (const s of marks.segments) {
    const own = (s.a[0] === c.loc[0] && s.a[1] === c.loc[1]) || (s.b[0] === c.loc[0] && s.b[1] === c.loc[1]);
    if (!own) boxes.push({ x0: Math.min(s.a[0], s.b[0]), y0: Math.min(s.a[1], s.b[1]), x1: Math.max(s.a[0], s.b[0]), y1: Math.max(s.a[1], s.b[1]) });
  }
  const MAX = 80;
  const inBand = (b: Box) => b.y1 > y0 && b.y0 < y1;
  const west = () => Math.min(MAX, ...boxes.filter((b) => inBand(b) && b.x1 <= x0).map((b) => x0 - b.x1 - CLEAR));
  const east = () => Math.min(MAX, ...boxes.filter((b) => inBand(b) && b.x0 >= x1).map((b) => b.x0 - x1 - CLEAR));
  const room = facing === 'east' ? west() : facing === 'west' ? east() : 2 * Math.min(west(), east());
  return Math.max(0, room);
}
