/* The Canvas's copy of a circuit (scene.ts), where wires meet and cross
   (wires.ts), and zoom and pan (view.ts). */

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { test } from 'node:test';

import type { Component, Snapshot } from '../../src/main/protocol.ts';
import { Scene } from '../../src/renderer/canvas/scene.ts';
import { between, clampZoom, fit, FIT_MARGIN, percent, step, toCircuit, toScreen, wheelZoom, zoomAt } from '../../src/renderer/canvas/view.ts';
import { dotUnits, jumpUnits, wireMarks } from '../../src/renderer/canvas/wires.ts';

interface Fixture { main: string; circuits: Snapshot[]; watch: Record<string, { nets: Record<string, string>; bodies: Record<string, unknown> }[]> }
const load = (n: string) => JSON.parse(readFileSync(path.join(import.meta.dirname, `../fixtures/circuits/${n}.json`), 'utf8')) as Fixture;

const pin = (id: string, x: number, y: number, width = 1): Component => ({
  id, lib: 'Wiring', name: 'Pin', loc: [x, y], bounds: [x - 20, y - 10, 20, 20], facing: 'east', attrs: { width: String(width) },
  ports: [{ i: 0, loc: [x, y], width, dir: 'out' }],
});

test('a scene from the engine\'s snapshot: nets of wires and ports, values, open ports, width mismatches', () => {
  const fx = load('demo-datapath');
  const snap = fx.circuits.find((c) => c.circuitId === fx.main)!;
  const s = new Scene('f1', snap);
  assert.equal(s.components.size, snap.components.length);
  assert.equal(s.wires.size, snap.wires.length);
  const w = snap.wires[0];
  const n = s.wireNet(w.id)!;
  assert.ok(n.wires.includes(w.id));
  assert.equal(s.wireValue(w.id), undefined, 'no values before the engine sends them');
  const frame = fx.watch[fx.main][0];
  s.applyValues({ fileId: 'f1', circuitId: fx.main, nets: frame.nets, bodies: frame.bodies as Record<string, Record<string, unknown>> });
  assert.equal(s.wireValue(w.id), frame.nets[n.id]);
  const [c, i] = n.ports[0];
  assert.equal(s.portValue(c, i), frame.nets[n.id]);
  assert.ok(s.bodies.size > 0, 'the Instruction and Data Memory bodies');
  assert.ok(Number.isFinite(s.extent().x0));
  // the adder's carry ports are open (nothing connects to them)
  const adder = snap.components.find((x) => x.name === 'Adder')!;
  assert.equal(s.isOpen(adder.id, 3), true);
  assert.equal(s.isOpen(adder.id, 0), false);
  assert.equal(s.snapshot().components.length, snap.components.length);
});

test('model.changed: removed first, added upserted, nets renumbered with their values carried over', () => {
  const s = new Scene('f1', {
    circuitId: 'c1', name: 'main', components: [pin('k1', 100, 100), pin('k2', 100, 200, 4)],
    wires: [{ id: 'w1', a: [100, 100], b: [200, 100] }],
    nets: [{ id: 'n0', width: 1, wires: ['w1'], ports: [['k1', 0]] }, { id: 'n1', width: 4, wires: [], ports: [['k2', 0]] }], junctions: [],
  });
  s.applyValues({ fileId: 'f1', circuitId: 'c1', nets: { n0: '1', n1: '0101' } });
  const v0 = s.modelVersion;
  s.applyChange({
    fileId: 'f1', circuitId: 'c1', removed: ['k2'], dirty: true,
    added: [{ id: 'w2', a: [200, 100], b: [200, 150] }, { ...pin('k1', 100, 100), attrs: { width: '1', label: 'a' } }],
    nets: [{ id: 'n7', width: 1, wires: ['w1', 'w2'], ports: [['k1', 0]] }], junctions: [],
  });
  assert.equal(s.components.has('k2'), false);
  assert.equal(s.components.get('k1')!.attrs.label, 'a');
  assert.equal(s.wires.size, 2);
  assert.equal(s.wireValue('w2'), '1', 'the value of the old net goes to the new one');
  assert.equal(s.modelVersion, v0 + 1);
  // a width mismatch: two ports of different widths on one net
  const m = new Scene('f1', { circuitId: 'c1', name: 'm', components: [pin('a', 0, 0, 1), pin('b', 0, 0, 8)], wires: [], nets: [{ id: 'n0', width: 8, wires: [], ports: [['a', 0], ['b', 0]] }], junctions: [] });
  assert.equal(m.widthMismatch(m.nets[0]), true);
});

test('wires: the engine\'s dots and every T, jumps where wires cross without connecting, bus widths on the longest wire', () => {
  const s = new Scene('f1', {
    circuitId: 'c1', name: 'main', components: [pin('p', 300, 60, 8)],
    wires: [
      { id: 'h', a: [100, 100], b: [300, 100] },       // horizontal
      { id: 'v', a: [200, 50], b: [200, 150] },         // crosses h at (200,100), another net: a jump
      { id: 't', a: [150, 100], b: [150, 180] },        // ends inside h, same net: a T dot
      { id: 'b', a: [300, 60], b: [300, 20] },          // a bus from the pin
      { id: 'b2', a: [300, 20], b: [240, 20] },
    ],
    nets: [
      { id: 'n0', width: 1, wires: ['h', 't'], ports: [] },
      { id: 'n1', width: 1, wires: ['v'], ports: [] },
      { id: 'n2', width: 8, wires: ['b', 'b2'], ports: [['p', 0]] },
    ],
    junctions: [[300, 20]],
  });
  const m = wireMarks(s);
  assert.deepEqual(m.crossings, [{ at: [200, 100], over: 'h', under: 'v' }]);
  assert.ok(m.dots.some(([x, y]) => x === 150 && y === 100), 'the T');
  assert.ok(m.dots.some(([x, y]) => x === 300 && y === 20), 'the engine\'s junction');
  assert.equal(m.dots.length, 2);
  assert.deepEqual(m.widths.map((w) => [w.net, w.bits, w.wire, w.at]), [['n2', 8, 'b2', [270, 20]]]);
  // a crossing where the wires connect (a dot) is not a jump
  const joined = new Scene('f1', { ...s.snapshot(), junctions: [[200, 100], [300, 20]] });
  assert.equal(wireMarks(joined).crossings.length, 0);
});

test('dots and jumps on screen (v1 D-061): dots 7 px within 8–16 units, no hop below about 44 %', () => {
  assert.equal(dotUnits(1), 8 * 0.9);
  assert.equal(dotUnits(0.25), 16 * 0.9);
  assert.equal(jumpUnits(1), 5);
  assert.equal(jumpUnits(0.5), 8);
  assert.equal(jumpUnits(0.4), null);
  assert.equal(jumpUnits(4), 5);
});

test('zoom and pan: 25 to 400 %, steps, the wheel, zoom about a point, fit centred both ways with a margin (v1 S-10)', () => {
  assert.equal(clampZoom(10), 4);
  assert.equal(clampZoom(0.01), 0.25);
  assert.equal(step(1, 1), 1.1);
  assert.equal(step(1, -1), 0.9);
  assert.equal(step(4, 1), 4);
  assert.equal(step(0.25, -1), 0.25);
  assert.equal(step(1.3, 1), 1.5);
  assert.ok(wheelZoom(1, -100) > 1 && wheelZoom(1, 100) < 1);
  assert.equal(wheelZoom(4, -1000), 4);
  const v = { x: 100, y: 50, zoom: 1 };
  const at: [number, number] = [300, 200];
  const z = zoomAt(v, 2, at);
  assert.deepEqual(toCircuit(z, at), toCircuit(v, at), 'the point under the pointer stays');
  assert.deepEqual(toScreen(v, toCircuit(v, [12, 34])), [12, 34]);
  const f = fit({ x0: 0, y0: 0, x1: 1000, y1: 200 }, 1200, 800);
  assert.equal(f.zoom, (1200 - 2 * FIT_MARGIN) / 1000);
  const [cx, cy] = toScreen(f, [500, 100]);
  assert.ok(Math.abs(cx - 600) < 1e-9 && Math.abs(cy - 400) < 1e-9, 'centred both ways');
  assert.equal(fit({ x0: 0, y0: 0, x1: 10, y1: 10 }, 1200, 800).zoom, 2, 'a tiny circuit is not blown up past 200 %');
  // too big even at 25 % (ref-mips): the floor, the top-left corner with the margin where it does not fit,
  // centred where it does (D-137)
  const huge = fit({ x0: 294, y0: 320, x1: 7056, y1: 9240 }, 1290, 628);
  assert.equal(huge.zoom, 0.25);
  assert.deepEqual(toScreen(huge, [294, 320]), [FIT_MARGIN, FIT_MARGIN]);
  const wideOnly = fit({ x0: 0, y0: 0, x1: 100000, y1: 400 }, 1200, 800);
  assert.equal(wideOnly.zoom, 0.25);
  assert.deepEqual(toScreen(wideOnly, [0, 200]), [FIT_MARGIN, 400], 'left edge shown, centred up and down');
  const mid = between({ x: 0, y: 0, zoom: 1 }, { x: 100, y: 0, zoom: 4 }, 1);
  assert.deepEqual(mid, { x: 100, y: 0, zoom: 4 });
  assert.equal(percent(1.5), '150%');
});
