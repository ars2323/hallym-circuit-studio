/* What the tutorial's practice steps look for (N-18, D-161): facts read from
   what the engine sent -- a circuit's snapshot (its parts, wires and nets:
   the engine's own netlist, docs/engine-api.md model.circuit), the values
   on its nets, the messages.  Nothing here judges whether a circuit is
   right (CLAUDE.md 2.6): a step asks "is A's wire at an AND gate's input",
   the facts the step told the student to make.  Pure: the tests feed the
   real engine's snapshots (tests/fixtures/tutorial/, TutorialExamplesTest). */

import type { Component, DiagMessage, Net, Snapshot } from '../../../main/protocol.ts';

export type Box = [number, number, number, number];   // x0, y0, x1, y1 in circuit units

export const parts = (s: Snapshot | null, name: string): Component[] => (s ? s.components.filter((c) => c.name === name) : []);
export const labelled = (s: Snapshot | null, name: string, label: string): Component | undefined =>
  parts(s, name).find((c) => (c.attrs.label ?? '') === label);

export function netOf(s: Snapshot | null, componentId: string, port: number): Net | undefined {
  return s?.nets.find((n) => n.ports.some(([id, i]) => id === componentId && i === port));
}

// The other ends on a port's net: [component, port] pairs other than the port itself.
export function othersAt(s: Snapshot | null, componentId: string, port: number): [Component, number][] {
  const n = netOf(s, componentId, port);
  if (!s || !n) return [];
  return n.ports.filter(([id, i]) => !(id === componentId && i === port))
    .map(([id, i]) => [s.components.find((c) => c.id === id), i] as [Component | undefined, number])
    .filter((x): x is [Component, number] => x[0] !== undefined);
}

// L3: an AND gate in the circuit.
export const andGate = (s: Snapshot | null): Component | undefined => parts(s, 'AND Gate')[0];

// L4: A and B at two inputs of one AND gate, its output at Y (port 0 of a gate is its output).
export function andWired(s: Snapshot | null): boolean {
  const a = labelled(s, 'Pin', 'A'), b = labelled(s, 'Pin', 'B'), y = labelled(s, 'Pin', 'Y');
  if (!a || !b || !y) return false;
  return parts(s, 'AND Gate').some((g) => {
    const ina = othersAt(s, a.id, 0).find(([c, i]) => c.id === g.id && i >= 1);
    const inb = othersAt(s, b.id, 0).find(([c, i]) => c.id === g.id && i >= 1);
    return !!ina && !!inb && ina[1] !== inb[1] && othersAt(s, y.id, 0).some(([c, i]) => c.id === g.id && i === 0);
  });
}

// L6: the AND gate's label.
export const andLabelled = (s: Snapshot | null, label: string): boolean => parts(s, 'AND Gate').some((g) => (g.attrs.label ?? '') === label);

// L8: a half_adder in the circuit with both inputs on the wires waiting for them (each input's net has another port).
export function halfAdderPlaced(s: Snapshot | null, name = 'half_adder'): boolean {
  return parts(s, name).some((k) => k.ports.filter((p) => p.dir !== 'out' && othersAt(s, k.id, p.i).length > 0).length >= 2);
}

// L12: the Clock's output at the clock input (port 2) of the Register labelled `label`.
export function clockWired(s: Snapshot | null, label = 'count'): boolean {
  const r = labelled(s, 'Register', label);
  return !!r && othersAt(s, r.id, 2).some(([c, i]) => c.name === 'Clock' && i === 0);
}

// C5: the tunnel of that name.
export const tunnel = (s: Snapshot | null, label: string): Component | undefined => labelled(s, 'Tunnel', label);

export const messagesOf = (list: DiagMessage[] | null, code: string): DiagMessage[] => (list ?? []).filter((m) => m.code === code);

// The box around some parts (their bounds), grown by `margin` units.
export function boxOf(list: (Component | undefined)[], margin = 10): Box | null {
  const cs = list.filter((c): c is Component => !!c);
  if (!cs.length) return null;
  const x0 = Math.min(...cs.map((c) => c.bounds[0])), y0 = Math.min(...cs.map((c) => c.bounds[1]));
  const x1 = Math.max(...cs.map((c) => c.bounds[0] + c.bounds[2])), y1 = Math.max(...cs.map((c) => c.bounds[1] + c.bounds[3]));
  return [x0 - margin, y0 - margin, x1 + margin, y1 + margin];
}

// Two boxes as one.
export const union = (a: Box | null, b: Box | null): Box | null =>
  (!a ? b : !b ? a : [Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[2], b[2]), Math.max(a[3], b[3])]);

// A memo's box (hcs:ext area memo), by its word.
export function memoBox(s: Snapshot | null, text: string): Box | null {
  const m = s?.memos?.find((x) => x.text === text);
  return m ? [m.x, m.y, m.x + m.w, m.y + m.h] : null;
}

// The wires of a port's net (the ones to click for a Signal Flow).
export const wiresAt = (s: Snapshot | null, componentId: string, port: number): string[] => netOf(s, componentId, port)?.wires ?? [];

// A wire's box, grown so that it can be pointed at and clicked.
export function wireBox(s: Snapshot | null, wireId: string, grow = 6): Box | null {
  const w = s?.wires.find((x) => x.id === wireId);
  if (!w) return null;
  return [Math.min(w.a[0], w.b[0]) - grow, Math.min(w.a[1], w.b[1]) - grow, Math.max(w.a[0], w.b[0]) + grow, Math.max(w.a[1], w.b[1]) + grow];
}

// The longest wire of a set (the easiest to click), or undefined.
export function longest(s: Snapshot | null, wires: string[]): string | undefined {
  const len = (id: string) => { const w = s?.wires.find((x) => x.id === id); return w ? Math.abs(w.a[0] - w.b[0]) + Math.abs(w.a[1] - w.b[1]) : -1; };
  return [...wires].sort((a, b) => len(b) - len(a))[0];
}

// L13, C12: a Signal Flow started from this net -- one of its wires, or a part with a port on it (the click's target).
export const onNet = (n: Net | undefined, id: string | null): boolean => !!n && !!id && (n.wires.includes(id) || n.ports.some(([c]) => c === id));
