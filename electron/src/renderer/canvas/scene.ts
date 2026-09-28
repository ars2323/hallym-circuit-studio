/* The Canvas's copy of one circuit (N-05): the engine's snapshot
   (model.circuit), kept up to date by its changes (model.changed: the
   removed ids first, then the added ones, upserted) and its values
   (sim.values: the nets that changed, and the bodies).  The engine is the
   authority; this only indexes what it says for drawing -- which net a
   wire or a port is on, the value there, where the parts and wires are. */

import type { AreaMemo, Component, GroupRef, ModelChanged, Net, Point, SimValues, Snapshot, Wire } from '../../main/protocol.ts';
import type { Body } from './parts/common.ts';
import { type Box, boxUnion, EMPTY_BOX } from './shapes.ts';

export class Scene {
  readonly fileId: string;
  circuitId: string;
  name: string;
  readonly components = new Map<string, Component>();
  readonly wires = new Map<string, Wire>();
  nets: Net[] = [];
  junctions: Point[] = [];
  readonly values = new Map<string, string>();        // net id → value
  readonly bodies = new Map<string, Body>();          // component id → body state
  // What the student put on the circuit (hcs:ext, N-15): signal groups by net id, area memos in file order.
  groups = new Map<string, GroupRef>();
  memos: AreaMemo[] = [];
  /* Bumped by model changes and by values: whoever caches drawings compares. */
  modelVersion = 0;
  valueVersion = 0;
  private netOfWire = new Map<string, Net>();
  private netOfPort = new Map<string, Net>();
  private netById = new Map<string, Net>();
  private openPorts = new Set<string>();

  constructor(fileId: string, s: Snapshot) {
    this.fileId = fileId;
    this.circuitId = s.circuitId;
    this.name = s.name;
    for (const c of s.components) this.components.set(c.id, c);
    for (const w of s.wires) this.wires.set(w.id, w);
    this.setExt(s.groups ?? [], s.memos ?? []);
    this.setNets(s.nets, s.junctions);
  }

  private setExt(groups: GroupRef[], memos: AreaMemo[]): void {
    this.groups = new Map(groups.map((g) => [g.net, g]));
    this.memos = memos;
  }

  private setNets(nets: Net[], junctions: Point[]): void {
    // Net ids are numbered anew at every change: carry each value over to the new net that
    // holds a wire or port of the old one, so colours do not blink until the next frame.
    const carried = new Map<string, string>();
    if (this.values.size) {
      for (const [k, n] of [...this.netOfWire, ...this.netOfPort]) {
        const v = this.values.get(n.id);
        if (v !== undefined) carried.set(k, v);
      }
    }
    this.nets = nets;
    this.junctions = junctions;
    this.netOfWire.clear();
    this.netOfPort.clear();
    this.netById.clear();
    this.openPorts.clear();
    for (const n of nets) {
      this.netById.set(n.id, n);
      for (const w of n.wires) this.netOfWire.set(w, n);
      for (const [c, i] of n.ports) this.netOfPort.set(`${c}:${i}`, n);
      if (n.wires.length === 0 && n.ports.length === 1) this.openPorts.add(`${n.ports[0][0]}:${n.ports[0][1]}`);
    }
    if (carried.size) {
      this.values.clear();
      for (const n of nets) {
        const key = n.wires.length ? n.wires[0] : n.ports.length ? `${n.ports[0][0]}:${n.ports[0][1]}` : '';
        const v = carried.get(key);
        if (v !== undefined && v.length === n.width) this.values.set(n.id, v);
      }
    }
    this.modelVersion++;
  }

  // model.changed for this circuit.
  applyChange(c: ModelChanged): void {
    for (const id of c.removed) {
      this.components.delete(id);
      this.wires.delete(id);
      this.bodies.delete(id);
    }
    for (const x of c.added) {
      if ('a' in x) this.wires.set(x.id, x);
      else this.components.set(x.id, x);
    }
    // the engine sends the circuit's whole lists with every change (an older one none: keep them)
    if (c.groups || c.memos) this.setExt(c.groups ?? [...this.groups.values()], c.memos ?? this.memos);
    this.setNets(c.nets, c.junctions);
  }

  // sim.values for this circuit.
  applyValues(v: SimValues): { nets: string[]; bodies: string[] } {
    const nets = Object.keys(v.nets);
    for (const id of nets) this.values.set(id, v.nets[id]);
    const bodies = Object.keys(v.bodies ?? {});
    for (const id of bodies) this.bodies.set(id, v.bodies![id]);
    if (nets.length || bodies.length) this.valueVersion++;
    return { nets, bodies };
  }

  net(id: string): Net | undefined { return this.netById.get(id); }
  netOf(componentId: string, port: number): Net | undefined { return this.netOfPort.get(`${componentId}:${port}`); }
  wireNet(wireId: string): Net | undefined { return this.netOfWire.get(wireId); }
  portValue(componentId: string, port: number): string | undefined {
    const n = this.netOfPort.get(`${componentId}:${port}`);
    return n ? this.values.get(n.id) : undefined;
  }
  wireValue(wireId: string): string | undefined {
    const n = this.netOfWire.get(wireId);
    return n ? this.values.get(n.id) : undefined;
  }
  // A port nothing connects to: no wire, no other port at its point.
  isOpen(componentId: string, port: number): boolean { return this.openPorts.has(`${componentId}:${port}`); }

  // A net whose ports disagree on the width (Logisim draws its wires orange).
  widthMismatch(n: Net): boolean {
    let w = -1;
    for (const [c, i] of n.ports) {
      const q = this.components.get(c)?.ports[i];
      if (!q || q.width === 0) continue;         // a probe takes any width
      if (w < 0) w = q.width;
      else if (q.width !== w) return true;
    }
    return false;
  }

  // The circuit as a snapshot again (the side panels read it: counts, tunnels).
  snapshot(): Snapshot {
    return {
      circuitId: this.circuitId, name: this.name, components: [...this.components.values()], wires: [...this.wires.values()], nets: this.nets, junctions: this.junctions,
      ...(this.groups.size ? { groups: [...this.groups.values()] } : {}), ...(this.memos.length ? { memos: this.memos } : {}),
    };
  }

  // Everything drawn (parts and wires), for fitting the view.
  extent(): Box {
    let b = EMPTY_BOX;
    for (const c of this.components.values()) {
      const [x, y, w, h] = c.bounds;
      if (w > 0 || h > 0) b = boxUnion(b, { x0: x, y0: y, x1: x + w, y1: y + h });
    }
    for (const w of this.wires.values()) {
      b = boxUnion(b, { x0: Math.min(w.a[0], w.b[0]), y0: Math.min(w.a[1], w.b[1]), x1: Math.max(w.a[0], w.b[0]), y1: Math.max(w.a[1], w.b[1]) });
    }
    // area memos (N-15) and their words above the box
    for (const m of this.memos) b = boxUnion(b, { x0: m.x, y0: m.y - 20, x1: m.x + m.w, y1: m.y + m.h });
    return b;
  }
}
