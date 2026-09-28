/* The Canvas's overlays without a screen (N-15, D-151; canvas/overlays/):
   the bus values' text and places, a net's name, the influence's steps,
   the Signal Flow's time, dashes, arcs and labels, where a click starts a
   flow, the memos' boxes, the colours, the words; and the fake engine's
   overlay answers, which are the real engine's (tests/fixtures/flow/). */

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { test } from 'node:test';

import type { Component, FlowPath, InfluenceResult, Snapshot } from '../../src/main/protocol.ts';
import { layoutChips, roughMeasure, WIRE_GAP as LABEL_WIRE_GAP } from '../../src/renderer/canvas/labels.ts';
import { boxToWire, layoutBusChips, leaderOf, nearestOn, ownSide, WIRE_GAP } from '../../src/renderer/canvas/overlays/busvalues.ts';
import { COLOR_NAMES, MEMO_HINT, netInfoLines } from '../../src/renderer/canvas/overlays/dialogs.ts';
import { dashWidth, labelled } from '../../src/renderer/canvas/overlays/flow.ts';
import { placesText } from '../../src/renderer/canvas/overlays/influence.ts';
import {
  arcBlockers, arcControl, arcRise, BAND, busTemplate, busText, chooseArc, CHIP_ZOOM, groupBand, influenceBand, dashes, FIELD_COLORS, fieldColor, FLOW_SPEED_PX, flowTarget, GROUP_COLORS,
  hexOf, labelPlace, litLength, memoStart, netName, outputNear, placesAt, quad, ringRadius, segmentMeetsBox, widen,
} from '../../src/renderer/canvas/overlays/logic.ts';
import { darker, MEMO_PAD, memoAt, memoColor, wordBox, wordPlace } from '../../src/renderer/canvas/overlays/memos.ts';
import { influenceStatus } from '../../src/renderer/canvas/overlays/words.ts';
import { Scene } from '../../src/renderer/canvas/scene.ts';
import { boxesMeet } from '../../src/renderer/canvas/shapes.ts';
import { TUNNEL_PALETTE } from '../../src/renderer/canvas/tokens.ts';
import { wireMarks } from '../../src/renderer/canvas/wires.ts';

interface Fixture { main: string; circuits: Snapshot[]; watch: Record<string, { nets: Record<string, string> }[]> }
const circuits = JSON.parse(readFileSync(path.join(import.meta.dirname, '../fixtures/circuits/demo-datapath.json'), 'utf8')) as Fixture;
interface FlowFixture { influence: Record<string, InfluenceResult>; flow: Record<string, FlowPath>; net: Record<string, unknown>; activePath: { muxes: { input: number; segments: unknown[] }[] }[]; fieldPaths: { fields: Record<string, string[]> }[] }
const flowFx = JSON.parse(readFileSync(path.join(import.meta.dirname, '../fixtures/flow/demo-datapath.json'), 'utf8')) as FlowFixture;
const demo = () => {
  const s = new Scene('f1', circuits.circuits.find((c) => c.circuitId === circuits.main)!);
  s.applyValues({ fileId: 'f1', circuitId: circuits.main, nets: circuits.watch[circuits.main][1].nets });
  return s;
};
const byLabel = (s: Scene, name: string, label: string): Component => [...s.components.values()].find((c) => c.name === name && c.attrs.label === label)!;

// ---- bus values (C-08) ----------------------------------------------------------------------

test('bus values: v1\'s text in each radix, Logisim\'s hex for x and E, nothing for one bit, off or all floating', () => {
  assert.equal(busText('00000000000000000000000000001100', 'hex'), '0x0000000c');
  assert.equal(busText('00000000000000000000000000001100', 'dec'), '12');
  assert.equal(busText('11111111111111111111111111111100', 'signed'), '-4');
  assert.equal(busText('11111111111111111111111111111100', 'dec'), '4294967292');
  assert.equal(busText('01100', 'hex'), '0x0c', 'five bits: two digits');
  assert.equal(busText('1', 'hex'), null, 'one bit: the wire\'s colour says it');
  assert.equal(busText('0101', 'off'), null);
  assert.equal(busText('xxxx', 'hex'), null, 'all floating: no chip');
  assert.equal(busText(undefined, 'hex'), null);
  assert.equal(busText('01x1', 'dec'), '0xx', 'not all 0/1: Logisim\'s hex whatever the radix');
  assert.equal(busText('E0011111', 'hex'), '0xEf');
  assert.equal(hexOf('x0101111'), 'x' + 'f');
  assert.equal(hexOf('1x00E000'), 'xE', 'each nibble from its top bit down: the first x or E');
  assert.equal(hexOf('0E0x'), 'E', 'E before x in the nibble');
  assert.equal(busTemplate(32, 'hex'), '0x00000000');
  assert.equal(busTemplate(32, 'dec'), '0000000000', '4294967295');
  assert.equal(busTemplate(32, 'signed'), '-0000000000', '-2147483648');
  assert.equal(busTemplate(5, 'hex'), '0x00');
});

test('a net\'s name: its tunnel first (by name), else the pin that drives it, else any pin', () => {
  const s = demo();
  const pcNet = s.netOf(byLabel(s, 'Tunnel', 'pc').id, 0)!;
  assert.equal(netName(s, pcNet), 'pc');
  const halt = byLabel(s, 'Pin', 'halt');
  assert.equal(netName(s, s.netOf(halt.id, 0)!), 'halt', 'an output pin on it');
  const unnamed = s.nets.find((n) => n.width === 32 && netName(s, n) === '');
  assert.ok(unnamed, 'demo-datapath has unnamed buses');
  // one net with all three: the tunnel wins over the pins whatever their names, the driving pin over the other
  const part = (id: string, name: string, label: string, dir: 'in' | 'out') =>
    [id, { id, lib: 'Wiring', name, attrs: { label }, ports: [{ i: 0, dir, loc: [0, 0] }] } as unknown as Component] as const;
  const made = (...cs: (readonly [string, Component])[]) => ({ components: new Map(cs) }) as unknown as Scene;
  const net = { id: 'n', width: 8, wires: [], ports: [['t', 0], ['d', 0], ['p', 0]] } as unknown as Snapshot['nets'][number];
  assert.equal(netName(made(part('t', 'Tunnel', 'zeta', 'in'), part('d', 'Pin', 'alpha', 'out'), part('p', 'Pin', 'able', 'in')), net), 'zeta');
  assert.equal(netName(made(part('d', 'Pin', 'zulu', 'out'), part('p', 'Pin', 'able', 'in')), net), 'zulu', 'the pin that drives it');
  assert.equal(netName(made(part('p', 'Pin', 'able', 'in')), net), 'able');
});

test('bus chips stand WIRE_GAP off every wire, off the parts and the chips, and are left out where nothing is free', () => {
  const s = demo();
  const marks = wireMarks(s);
  const chips = layoutBusChips(s, marks.segments, [], roughMeasure, 'hex');
  assert.ok(chips.length >= 4, `${chips.length} chips`);
  for (const c of chips) {
    const n = s.net(c.net)!;
    assert.ok(n.width >= 2, 'buses only');
    for (const seg of marks.segments) {
      const near = { x0: Math.min(seg.a[0], seg.b[0]) - WIRE_GAP, y0: Math.min(seg.a[1], seg.b[1]) - WIRE_GAP, x1: Math.max(seg.a[0], seg.b[0]) + WIRE_GAP, y1: Math.max(seg.a[1], seg.b[1]) + WIRE_GAP };
      assert.ok(!boxesMeet(near, c.box), `chip of ${c.net} too near wire ${seg.id}`);
    }
    for (const k of s.components.values()) {
      const b = { x0: k.bounds[0] - 4, y0: k.bounds[1] - 4, x1: k.bounds[0] + k.bounds[2] + 4, y1: k.bounds[1] + k.bounds[3] + 4 };
      assert.ok(!boxesMeet(b, c.box), `chip of ${c.net} over ${k.name}`);
    }
    for (const o of chips) if (o !== c) assert.ok(!boxesMeet(o.box, c.box), 'chips apart');
    // the leader starts on its own wire
    const w = s.wires.get(c.wire)!;
    assert.deepEqual(nearestOn(w, c.anchor), c.anchor);
  }
  const named = chips.find((c) => c.prefix.startsWith('pc['));
  if (named) assert.equal(named.prefix, 'pc[31:0] = ');
  // the same model, the same places (the chip does not move as values change)
  assert.deepEqual(layoutBusChips(s, marks.segments, [], roughMeasure, 'hex'), chips);
  assert.deepEqual(layoutBusChips(s, marks.segments, [], roughMeasure, 'off'), []);
  // a chip already there keeps the bus chip off
  const blocked = layoutBusChips(s, marks.segments, chips.map((c) => c.box), roughMeasure, 'hex');
  for (const b of blocked) for (const c of chips) assert.ok(!boxesMeet(b.box, c.box));
});

test('label and value chips keep WIRE_GAP from every wire where there is room: the bands of the overlays never touch them (UI checklist 12)', () => {
  assert.equal(LABEL_WIRE_GAP, WIRE_GAP);
  for (const name of ['demo-datapath', 'broken-datapath', 'ref-mips']) {
    const fx = JSON.parse(readFileSync(path.join(import.meta.dirname, `../fixtures/circuits/${name}.json`), 'utf8')) as Fixture;
    for (const snap of fx.circuits) {
      const s = new Scene('f1', snap);
      const m = wireMarks(s);
      for (const c of layoutChips(s, m, roughMeasure).filter((x) => x.kind === 'label' || x.kind === 'value')) {
        for (const g of m.segments) {
          const room = { x0: Math.min(g.a[0], g.b[0]) - WIRE_GAP, y0: Math.min(g.a[1], g.b[1]) - WIRE_GAP, x1: Math.max(g.a[0], g.b[0]) + WIRE_GAP, y1: Math.max(g.a[1], g.b[1]) + WIRE_GAP };
          assert.ok(!boxesMeet(room, c.box), `${name} ${snap.name}: the ${c.text} chip within ${WIRE_GAP} of a wire`);
        }
      }
    }
  }
  // a wire just past CLEAR from the PC's label chip (6 units above it): the chip moves off its band
  const snap = structuredClone(circuits.circuits.find((c) => c.name === 'main')!);
  const pcOf = (chips: ReturnType<typeof layoutChips>) => chips.find((c) => c.kind === 'label' && c.text === 'PC')!.box;
  const before = pcOf(layoutChips(new Scene('f1', snap), wireMarks(new Scene('f1', snap)), roughMeasure));
  const y = before.y0 - 6;
  snap.wires.push({ id: 'wx', a: [before.x0 - 20, y], b: [before.x1 + 20, y] } as Snapshot['wires'][number]);
  snap.nets.push({ id: 'nx', width: 1, wires: ['wx'], ports: [] } as unknown as Snapshot['nets'][number]);
  const s2 = new Scene('f1', snap);
  const after = pcOf(layoutChips(s2, wireMarks(s2), roughMeasure));
  const band = { x0: before.x0 - 20 - WIRE_GAP, y0: y - WIRE_GAP, x1: before.x1 + 20 + WIRE_GAP, y1: y + WIRE_GAP };
  assert.ok(boxesMeet(band, before), 'the old place is on the new wire\'s band');
  assert.ok(!boxesMeet(band, after), 'the PC chip off the band');
});

test('a bus chip reads as its own wire\'s: no other net\'s wire as near, none across its leader (UI review: RR2 by WR)', () => {
  for (const name of ['demo-datapath', 'broken-datapath', 'ref-mips']) {
    const fx = JSON.parse(readFileSync(path.join(import.meta.dirname, `../fixtures/circuits/${name}.json`), 'utf8')) as Fixture;
    for (const snap of fx.circuits) {
      const sc = new Scene('f1', snap);
      const segs = wireMarks(sc).segments;
      for (const c of layoutBusChips(sc, segs, [], roughMeasure, 'hex')) {
        const own = segs.filter((g) => g.net === c.net);
        const mine = Math.min(...own.map((g) => boxToWire(c.box, g)));
        const [a, b] = leaderOf(c);
        for (const g of segs) {
          if (g.net === c.net) continue;
          assert.ok(boxToWire(c.box, g) > mine, `${name} ${snap.name}: ${c.prefix}chip of ${c.net} as near to ${g.id}`);
          const gb = { x0: Math.min(g.a[0], g.b[0]) - 0.5, y0: Math.min(g.a[1], g.b[1]) - 0.5, x1: Math.max(g.a[0], g.b[0]) + 0.5, y1: Math.max(g.a[1], g.b[1]) + 0.5 };
          assert.ok(!segmentMeetsBox(a, b, gb), `${name} ${snap.name}: the leader of ${c.net} crosses ${g.id}`);
        }
      }
    }
  }
});

test('own side, made up: another net\'s wire nearer, or across the leader, is refused; the net\'s own second wire is a wire to keep off', () => {
  const seg = (id: string, net: string, a: [number, number], b: [number, number]) => ({ id, net, a, b, horizontal: a[1] === b[1], bits: 32 });
  const own = seg('w1', 'n1', [0, 100], [200, 100]);
  const box = { x0: 80, y0: 78, x1: 120, y1: 92 };               // 8 above the wire
  assert.equal(ownSide(box, [100, 100], 'n1', [own]), true);
  assert.equal(ownSide(box, [100, 100], 'n1', [own, seg('w2', 'n2', [0, 70], [200, 70])]), false, 'n2 8 above it: as near');
  const far = { x0: 80, y0: 58, x1: 120, y1: 72 };                 // 28 above, a leader up to it
  assert.equal(ownSide(far, [100, 100], 'n1', [own, seg('w3', 'n3', [60, 85], [140, 85])]), false, 'n3 across the leader, nearer');
  // the net's own wire beside the chip, the leader to the longest one across another net's wire farther off
  const beside = seg('w4', 'n1', [130, 50], [130, 80]);
  assert.equal(ownSide(far, [100, 100], 'n1', [own, beside]), true);
  assert.equal(ownSide(far, [100, 100], 'n1', [own, beside, seg('w5', 'n3', [60, 85], [120, 85])]), false, 'n3 across the leader');
  // a U of one net: the chip must not sit on its own upper wire either
  const snap = { circuitId: 'c1', name: 'main', components: [], junctions: [],
    wires: [{ id: 'wa', a: [0, 100], b: [300, 100] }, { id: 'wb', a: [0, 90], b: [0, 100] }, { id: 'wc', a: [0, 90], b: [280, 90] }],
    nets: [{ id: 'n1', width: 32, wires: ['wa', 'wb', 'wc'], ports: [] }] } as unknown as Snapshot;
  const sc = new Scene('f1', snap);
  const segs = wireMarks(sc).segments;
  for (const c of layoutBusChips(sc, segs, [], roughMeasure, 'hex')) {
    for (const g of segs) assert.ok(boxToWire(c.box, g) >= WIRE_GAP - 1e-9, `the chip ${WIRE_GAP} off ${g.id}`);
  }
});

test('the bands stay within BAND where chips are drawn: a chip at WIRE_GAP keeps 3 from a group border, the influence, the highlight', () => {
  assert.ok(BAND / 2 + 3 <= WIRE_GAP);
  for (const z of [CHIP_ZOOM, 0.81, 0.9, 1, 1.25, 1.5, 2, 3, 4]) {
    for (const bits of [1, 2, 5, 32]) {
      const g = groupBand(z, bits);
      assert.ok(g.outer / 2 + 3 <= WIRE_GAP + 1e-9, `group ${bits} bits at ${z}: ${g.outer}`);
      assert.ok(g.outer - g.inner >= 2 * (1.2 / z) - 1e-9, 'a visible border each side');
    }
    assert.ok(influenceBand(z) / 2 + 3 <= WIRE_GAP + 1e-9, `influence at ${z}`);
  }
  assert.ok(influenceBand(0.5) > BAND, 'under the chips\' zoom the band may be wider');
});

// ---- influence (P-01) -------------------------------------------------------------------------

test('influence steps: from all, one less is the deepest; never under one; past the end, all again', () => {
  assert.equal(widen(-1, 4, -1), 3);
  assert.equal(widen(3, 4, -1), 2);
  assert.equal(widen(1, 4, -1), 1);
  assert.equal(widen(3, 4, 1), -1);
  assert.equal(widen(2, 4, 1), 3);
  assert.equal(widen(-1, 4, 1), -1);
  assert.equal(widen(-1, 1, -1), -1, 'a single step: all');
  assert.equal(influenceStatus({ mode: 'forward', depth: -1, throughRegisters: false }), 'Influence: Forward · all steps');
  assert.equal(influenceStatus({ mode: 'backward', depth: 1, throughRegisters: true }), 'Influence: Backward · 1 step · through registers');
  assert.equal(influenceStatus({ mode: 'between', depth: 3, throughRegisters: false }), 'Influence: Path Between · 3 steps');
  assert.equal(placesText('alu', 1), 'alu: 1 place');
  assert.equal(placesText('alu', 3), 'alu: 3 places');
});

test('the "N places" chip: the first of ten places that covers nothing, else the least covered', () => {
  const body = { x0: 100, y0: 100, x1: 200, y1: 160 };
  assert.deepEqual(placesAt(body, 40, 14, 6, []), { x0: 160, y0: 80, x1: 200, y1: 94 }, 'above, at the right');
  const right = placesAt(body, 40, 14, 6, [{ box: { x0: 150, y0: 70, x1: 210, y1: 96 }, weight: 10 }]);
  assert.deepEqual(right, { x0: 100, y0: 80, x1: 140, y1: 94 }, 'above, at the left');
  const all = [{ box: { x0: 0, y0: 0, x1: 400, y1: 400 }, weight: 3 }];
  assert.ok(placesAt(body, 40, 14, 6, all), 'covered everywhere: still a place');
});

// ---- Signal Flow (P-07) ------------------------------------------------------------------------

test('the flow\'s front: lit length, dashes moving the signal\'s way, speeds', () => {
  const s = { start: 100, length: 50 };
  assert.equal(litLength(s, 90), 0);
  assert.equal(litLength(s, 120), 20);
  assert.equal(litLength(s, 500), 50);
  assert.deepEqual(dashes(s, 90, 18, 8), []);
  const d1 = dashes(s, 120, 18, 8);
  const d2 = dashes(s, 125, 18, 8);
  assert.ok(d1.length > 0);
  for (const [a, b] of [...d1, ...d2]) assert.ok(a >= 0 && b <= 50 && b > a);
  // later, the same dash is further along (the signal's direction)
  assert.ok(d2[0][1] > d1[0][1] || d2[0][0] > d1[0][0]);
  assert.deepEqual(FLOW_SPEED_PX, { slow: 120, normal: 240, fast: 480 });
  assert.ok(dashWidth(1) >= 2 && dashWidth(32) <= 2.4 && dashWidth(32) > dashWidth(1));
  assert.equal(ringRadius(6, 1), 6);
  assert.equal(ringRadius(6, 0.5), 12);
  assert.ok(Math.abs(ringRadius(6, 4) * 4 - 13.5) < 0.1, 'about 13 px at 400 %');
});

test('a tunnel jump\'s arc: eight shapes in a fixed order, the one covering the fewest blockers, the first on a tie', () => {
  const j = { from: [100, 100] as [number, number], to: [300, 100] as [number, number] };
  assert.equal(arcRise(j), 50);
  assert.equal(arcRise({ from: [0, 0], to: [20, 0] }), 10);
  assert.equal(arcRise({ from: [0, 0], to: [1000, 0] }), 60);
  assert.deepEqual(arcControl(j, 0), [200, 50], 'above');
  assert.deepEqual(arcControl(j, 1), [200, 150], 'below');
  assert.deepEqual(arcControl(j, 4), [200, 0], 'above, twice as high');
  assert.deepEqual(chooseArc(j, []), [200, 50], 'nothing in the way: bulging up');
  const above = { x0: 150, y0: 60, x1: 250, y1: 90 };
  assert.deepEqual(chooseArc(j, [above]), [200, 150], 'a part above: below it');
  const m = quad(j.from, [200, 150], j.to, 0.5);
  assert.ok(!boxesMeet(above, { x0: m[0], y0: m[1], x1: m[0] + 0.1, y1: m[1] + 0.1 }));
  // the tunnels at either end are not blockers
  const tunnel = (x: number): Component => ({ id: `t${x}`, lib: 'Wiring', name: 'Tunnel', loc: [x, 100], bounds: [x - 30, 90, 30, 20], facing: 'east', attrs: {}, ports: [] });
  assert.deepEqual(arcBlockers(j, [tunnel(100), tunnel(300)], []), []);
});

test('an end\'s label: next to its ring if free, further out otherwise, never over an obstacle or across a label placed before', () => {
  const at: [number, number] = [100, 100];
  const first = labelPlace(at, 40, 14, 6, 1, [], [])!;
  assert.deepEqual(first, { x0: 109, y0: 77, x1: 149, y1: 91 }, 'upper right, next to the ring');
  const blocked = labelPlace(at, 40, 14, 6, 1, [{ x0: 105, y0: 60, x1: 160, y1: 95 }], [])!;
  assert.ok(blocked.x1 <= 105 || blocked.y0 >= 95, 'elsewhere');
  assert.equal(labelPlace(at, 40, 14, 6, 1, [{ x0: -1000, y0: -1000, x1: 1000, y1: 1000 }], []), null, 'nowhere free: no label');
  assert.ok(segmentMeetsBox([0, 0], [10, 10], { x0: 4, y0: 4, x1: 6, y1: 6 }));
  assert.ok(!segmentMeetsBox([0, 0], [10, 0], { x0: 4, y0: 4, x1: 6, y1: 6 }));
  const pin = { componentId: 'k1', port: 0, at, time: 0, path: [], circuitId: 'c1', label: 'Y' };
  assert.equal(labelled({ ...pin, kind: 'output' }, 'Pin'), true);
  assert.equal(labelled({ ...pin, kind: 'output' }, 'LED'), false, 'not a pin: the ring only');
  assert.equal(labelled({ ...pin, kind: 'unconnected' }, 'Comparator'), false);
  assert.equal(labelled({ ...pin, kind: 'state' }, 'Register'), true);
  assert.equal(labelled({ ...pin, kind: 'source' }, 'Pin'), true);
});

test('a click: a part (an output port within 5 units: that port), a wire (its grid point), a port just outside, or nothing', () => {
  const s = demo();
  const pc = byLabel(s, 'Register', 'PC');
  const q = pc.ports.find((x) => x.dir === 'out')!;
  const partAt = (p: [number, number]) => {
    for (const c of s.components.values()) if (p[0] >= c.bounds[0] && p[0] <= c.bounds[0] + c.bounds[2] && p[1] >= c.bounds[1] && p[1] <= c.bounds[1] + c.bounds[3]) return c.id;
    return null;
  };
  const wireAt = (p: [number, number]) => [...s.wires.values()].find((w) => p[0] >= Math.min(w.a[0], w.b[0]) - 3 && p[0] <= Math.max(w.a[0], w.b[0]) + 3 && p[1] >= Math.min(w.a[1], w.b[1]) - 3 && p[1] <= Math.max(w.a[1], w.b[1]) + 3)?.id ?? null;
  const mid: [number, number] = [pc.bounds[0] + pc.bounds[2] / 2, pc.bounds[1] + pc.bounds[3] / 2];
  assert.deepEqual(flowTarget(s, mid, partAt, wireAt), { componentId: pc.id, port: outputNear(pc, mid) });
  assert.equal(outputNear(pc, [q.loc[0] - 3, q.loc[1] + 2]), q.i);
  assert.ok(partAt([q.loc[0] - 3, q.loc[1] + 2]) === pc.id, 'inside the body');
  assert.deepEqual(flowTarget(s, [q.loc[0] - 3, q.loc[1] + 2], partAt, wireAt), { componentId: pc.id, port: q.i }, 'near its output: that port only');
  assert.equal(outputNear(pc, mid), -1, 'the body: all its outputs');
  const w = [...s.wires.values()].find((x) => x.a[1] === x.b[1] && Math.abs(x.a[0] - x.b[0]) > 40 && !partAt([(x.a[0] + x.b[0]) / 2, x.a[1]]))!;
  const t = flowTarget(s, [(w.a[0] + w.b[0]) / 2 + 1, w.a[1] + 1], partAt, wireAt) as { wire: string; at: [number, number] };
  assert.equal(t.wire, w.id);
  assert.equal(t.at[1], w.a[1]);
  assert.equal(flowTarget(s, [-5000, -5000], partAt, wireAt), null, 'an empty place');
});

// ---- memos, groups, fields, net information ---------------------------------------------------

test('area memos: the smallest holding the point, its colour and darker word, where a new box starts', () => {
  const memos = [{ x: 0, y: 0, w: 400, h: 400, color: 0, text: 'outer' }, { x: 100, y: 100, w: 50, h: 50, color: 13, text: 'IF' }];
  assert.equal(memoAt(memos, [120, 120])?.text, 'IF');
  assert.equal(memoAt(memos, [10, 10])?.text, 'outer');
  assert.equal(memoAt(memos, [500, 500]), null);
  assert.equal(memoColor(memos[1]), TUNNEL_PALETTE[1], 'the palette wraps (v1 floorMod)');
  assert.equal(darker('#e69f00'), '#a16f00');
  const s = demo();
  assert.deepEqual(memoStart(s, [705, 505], []), { x: 600, y: 440, w: 200, h: 120 }, 'v1: 200×120 at the point');
  const pc = byLabel(s, 'Register', 'PC');
  const b = memoStart(s, [0, 0], [pc.id]);
  assert.ok(b.x <= pc.bounds[0] - 20 && b.y <= pc.bounds[1] - 20 && b.x % 10 === 0 && b.y % 10 === 0);
  assert.ok(b.x + b.w >= pc.bounds[0] + pc.bounds[2] + 20 && b.w % 10 === 0 && b.h % 10 === 0);
  assert.equal(COLOR_NAMES.length, TUNNEL_PALETTE.length);
});

test('a memo\'s word: above its top left (v1); a chip or a wire there: above right, then below, then inside; all taken: v1\'s', () => {
  const m = { x: 100, y: 100, w: 200, h: 120, color: 0, text: 'ID' };
  const w = 20;
  const v1: [number, number] = [100 + MEMO_PAD, 100 - MEMO_PAD + 2];
  assert.deepEqual(wordPlace(m, w, []), v1);
  const chip = { x0: 100, y0: 80, x1: 140, y1: 96 };           // a chip over the top left
  const p = wordPlace(m, w, [chip]);
  assert.deepEqual(p, [300 - MEMO_PAD - w, v1[1]], 'above right');
  assert.ok(!boxesMeet(chip, wordBox(p[0], p[1], w)));
  const wire = { x0: 90, y0: 88, x1: 320, y1: 92 };            // a wire along the whole top
  const q = wordPlace(m, w, [wire]);
  assert.ok(q[1] > 220, 'below the box');
  assert.ok(!boxesMeet(wire, wordBox(q[0], q[1], w)));
  const everywhere = { x0: 0, y0: 0, x1: 400, y1: 400 };
  assert.deepEqual(wordPlace(m, w, [everywhere]), v1);
});

test('the colours: Hallym MIPS\'s field colours, the groups\' Okabe–Ito colours', () => {
  // shared/panels.css .f-* text colours (the Instruction panel), the same names as the engine's fields
  const css = readFileSync(path.join(import.meta.dirname, '../../src/renderer/shared/panels.css'), 'utf8');
  const shared = readFileSync(path.join(import.meta.dirname, '../../src/renderer/shared/shared.css'), 'utf8');
  const vars = new Map([...(shared + css).matchAll(/--([a-z0-9-]+):\s*(#[0-9a-f]{6})/gi)].map((m) => [m[1], m[2].toLowerCase()]));
  let checked = 0;
  for (const m of css.matchAll(/\.f-([a-z]+)(?:, \.f-[a-z]+)*\s*\{[^}]*[^-]color:\s*([^;]+);/g)) {
    const cls = m[1], color = m[2].trim();
    const want = color.startsWith('var(') ? vars.get(color.slice(6, -1)) : color.toLowerCase();
    checked++;
    assert.equal(fieldColor(cls), want, `.f-${cls}`);
  }
  assert.ok(checked >= 7, `${checked} field rules read`);
  assert.equal(Object.keys(FIELD_COLORS).length >= 12, true);
  assert.equal(fieldColor('sel'), '#4a5560', 'a field Hallym MIPS draws grey');
  assert.deepEqual(GROUP_COLORS, { control: '#e69f00', data: '#cc79a7', address: '#56b4e9' });
});

test('the scene keeps the groups and memos the engine sends, in the snapshot and in every change', () => {
  const snap = structuredClone(circuits.circuits.find((c) => c.circuitId === circuits.main)!);
  const net = snap.nets[0].id;
  const s = new Scene('f1', { ...snap, groups: [{ net, group: 'control', assigned: true }], memos: [{ x: 0, y: 0, w: 100, h: 50, color: 2, text: 'IF' }] });
  assert.equal(s.groups.get(net)?.group, 'control');
  assert.equal(s.memos.length, 1);
  assert.ok(s.extent().y0 <= -20, 'the memo\'s word above its box is part of the picture');
  s.applyChange({ fileId: 'f1', circuitId: snap.circuitId, removed: [], added: [], nets: snap.nets, junctions: snap.junctions, groups: [], memos: [], dirty: true });
  assert.equal(s.groups.size, 0);
  assert.equal(s.memos.length, 0);
  // an engine that says nothing of them keeps what there was
  const t = new Scene('f1', { ...snap, memos: [{ x: 0, y: 0, w: 100, h: 50, color: 2, text: 'IF' }] });
  t.applyChange({ fileId: 'f1', circuitId: snap.circuitId, removed: [], added: [], nets: snap.nets, junctions: snap.junctions, dirty: true });
  assert.equal(t.memos.length, 1);
  assert.deepEqual(t.snapshot().memos, t.memos);
});

test('net information in v1\'s words, the memo sentence in the window\'s rules', () => {
  const lines = netInfoLines({ netId: 'n1', width: 32, name: '', drivers: [{ componentId: 'k1', port: 2, text: 'alu #1 (Result)' }], readers: [], others: [{ componentId: 'k2', port: 0, text: 'Splitter #1' }] });
  assert.deepEqual(lines.map((l) => l.title), ['Driven by (1)', 'Read by (0)', 'Also on this net (1)']);
  assert.deepEqual(lines[0].items, ['alu #1 (Result)']);
  // a sentence to the student: Korean, no particle right after a name, no "하면 됩니다", no 한림
  const sentences = [MEMO_HINT, '[ ] 키로 좁히거나 넓히고 Esc 키로 지웁니다', 'Esc 키로 멈춥니다'];
  for (const t of sentences) {
    assert.doesNotMatch(t, /[A-Za-z0-9)\]][은는이가을를의에로와과도](?![가-힣])/, t);
    assert.doesNotMatch(t, /하면 됩니다|한림/, t);
  }
});

// ---- the fake engine's answers are the real engine's (tests/fixtures/flow/) ---------------------

test('the overlay fixture is the real engine\'s: v1\'s PC flow, the MUX branch, the field arms, net information', () => {
  const s = demo();
  const pc = byLabel(s, 'Register', 'PC');
  const p = flowFx.flow[`${circuits.main}|${pc.id}|-1|false|false|false`];
  assert.ok(p, 'the PC\'s flow');
  // v1 tests/circ/flow/demo-pc.flow (port 0 = the PC's output): the same ends and total
  const q = flowFx.flow[`${circuits.main}|${pc.id}|-1|false|false|false`];
  assert.deepEqual(q.endpoints.map((e) => `${e.kind.toUpperCase()} ${e.label} @${Math.round(e.time)}`),
    ['STATE Instruction Memory (Addr) @60', 'UNCONNECTED Comparator (gt) @400', 'UNCONNECTED Comparator (lt) @400', 'OUTPUT halt @460', 'STATE PC (D) @570']);
  assert.equal(Math.round(q.total), 570);
  for (const seg of q.segments) assert.ok(s.wires.size && seg.length >= 0);
  // every id in the fixture is the canvas fixture's
  for (const r of Object.values(flowFx.influence)) {
    for (const id of [...r.forward.wires, ...r.backward.wires]) assert.ok(s.wires.has(id), id);
    for (const id of [...r.forward.parts, ...r.stops, ...r.origin]) assert.ok(s.components.has(id) || s.wires.has(id), id);
  }
  // the MemtoReg MUX selects its input 0 (the MemtoReg pin is 0 from Reset on): ALU Result to the MUX (v1 D-099, five pieces)
  for (const a of flowFx.activePath) assert.deepEqual(a.muxes.map((x) => [x.input, x.segments.length]), [[0, 5]]);
  assert.deepEqual(Object.keys(flowFx.fieldPaths[1].fields), ['opcode', 'rs', 'rt', 'rd', 'shamt', 'funct']);
  for (const wires of Object.values(flowFx.fieldPaths[1].fields)) for (const id of wires) assert.ok(s.wires.has(id), id);
  assert.equal(Object.keys(flowFx.net).length, s.nets.length);
});
