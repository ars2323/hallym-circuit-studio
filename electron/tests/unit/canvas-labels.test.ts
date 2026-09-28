/* What is written around the parts (labels.ts): chips off the wires, the
   S-12 rule, value chips of wide registers, splitter arm ranges, bus widths,
   port names, tunnel colours (v1's palette and rule). */

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { test } from 'node:test';

import type { Component, Snapshot } from '../../src/main/protocol.ts';
import { layoutChips, MIN_GAP_PX, NEAR, paletteIndex, portNames, roughMeasure, sceneTunnelColors, tunnelColors } from '../../src/renderer/canvas/labels.ts';
import { wirePx } from '../../src/renderer/canvas/paint.ts';
import { Scene } from '../../src/renderer/canvas/scene.ts';
import { boxesMeet } from '../../src/renderer/canvas/shapes.ts';
import { TUNNEL_PALETTE } from '../../src/renderer/canvas/tokens.ts';
import { wireMarks } from '../../src/renderer/canvas/wires.ts';
import { rendererFor } from '../../src/renderer/canvas/registry.ts';

const geometry = (JSON.parse(readFileSync(path.join(import.meta.dirname, '../fixtures/geometry.json'), 'utf8')) as { cases: { case: string; component: Component }[] }).cases;
const part = (name: string): Component => structuredClone(geometry.find((c) => c.case === name)!.component);
const place = (c: Component, id: string, dx: number, dy: number): Component => ({
  ...c, id, loc: [c.loc[0] + dx, c.loc[1] + dy], bounds: [c.bounds[0] + dx, c.bounds[1] + dy, c.bounds[2], c.bounds[3]],
  ports: c.ports.map((q) => ({ ...q, loc: [q.loc[0] + dx, q.loc[1] + dy] })),
});
const scene = (components: Component[], wires: Snapshot['wires'] = [], nets: Snapshot['nets'] = []) =>
  new Scene('f1', { circuitId: 'c1', name: 'main', components, wires, nets, junctions: [] });

test('tunnel colours: v1\'s palette and hash (the same colour for a name in every run), different names nearby differ', () => {
  assert.equal(TUNNEL_PALETTE.length, 12);
  assert.equal(new Set(TUNNEL_PALETTE).size, 12);
  // FNV-1a of "a" is 0xE40C292C (v1 LabelsTest.tunnelColorsAreDeterministic)
  assert.equal(paletteIndex('a'), (((0xe40c292c | 0) % 12) + 12) % 12);
  const names = ['clk', 'PC', 'ALUResult', 'RegWrite', 'MemRead', 'rs', 'rt', 'rd', 'imm', 'funct', 'zero', 'branch', 'jump', 'op'];
  assert.ok(new Set(names.map(paletteIndex)).size >= 5);
  const c = tunnelColors([{ name: 'pc', at: [100, 100] }, { name: 'pc', at: [3000, 3000] }, { name: 'four', at: [160, 100] }, { name: 'pc4', at: [220, 100] }, { name: 'far', at: [5000, 100] }]);
  assert.equal(new Set([c.get('pc'), c.get('four'), c.get('pc4')]).size, 3);
  const crowd = tunnelColors(Array.from({ length: 12 }, (_, i) => ({ name: `s${i}`, at: [100 + 20 * i, 100] as [number, number] })));
  assert.equal(new Set(crowd.values()).size, 12, 'twelve names on one screen: twelve colours');
  assert.equal(tunnelColors([{ name: 'x', at: [0, 0] }, { name: 'y', at: [NEAR * 3, 0] }]).size, 2);
});

test('a label chip goes to the side its label location says, and moves off a wire in its way (v1 S-05)', () => {
  const p = { ...place(part('Wiring/Pin facing=east'), 'k1', 0, 0), attrs: { ...part('Wiring/Pin facing=east').attrs, label: 'RegWrite', labelloc: 'west' } };
  const alone = layoutChips(scene([p]), wireMarks(scene([p])), roughMeasure);
  assert.equal(alone.length, 1);
  assert.ok(alone[0].box.x1 <= p.bounds[0], 'west of the pin');
  // a wire running along the west side: the chip moves elsewhere
  const y = p.loc[1];
  const wire = { id: 'w1', a: [p.bounds[0] - 120, y] as [number, number], b: [p.bounds[0], y] as [number, number] };
  const s = scene([p], [wire], [{ id: 'n0', width: 1, wires: ['w1'], ports: [] }]);
  const chips = layoutChips(s, wireMarks(s), roughMeasure);
  const wb = { x0: wire.a[0], y0: y - 2, x1: wire.b[0], y1: y + 2 };
  assert.equal(chips.length, 1);
  assert.ok(!boxesMeet(chips[0].box, wb), 'the chip leaves the wire free');
});

test('a pin whose port has a tunnel of its own name shows the name once, on the tunnel (v1 S-12)', () => {
  const p = { ...place(part('Wiring/Pin facing=east'), 'k1', 0, 0) };
  p.attrs = { ...p.attrs, label: 'RegWrite' };
  const t = place(part('Wiring/Tunnel facing=west label=RegWrite'), 'k2', p.loc[0] - part('Wiring/Tunnel facing=west label=RegWrite').loc[0], p.loc[1] - part('Wiring/Tunnel facing=west label=RegWrite').loc[1]);
  assert.equal(t.loc[0], p.loc[0]);
  assert.deepEqual(layoutChips(scene([p, t]), wireMarks(scene([p, t])), roughMeasure).filter((c) => c.kind === 'label'), []);
  const other = { ...t, attrs: { ...t.attrs, label: 'MemRead' } };
  assert.equal(layoutChips(scene([p, other]), wireMarks(scene([p, other])), roughMeasure).filter((c) => c.kind === 'label').length, 1);
  // tunnel colours by name
  assert.ok(sceneTunnelColors(scene([t])).get('RegWrite')?.startsWith('#'));
});

test('a 32-bit register\'s value is a chip beside it (v1 S-07); an 8-bit one keeps its value inside', () => {
  const r32 = place(part('Memory/Register width=32'), 'k1', 0, 0);
  const r8 = place(part('Memory/Register width=8'), 'k2', 400, 0);
  const chips = layoutChips(scene([r32, r8]), wireMarks(scene([r32, r8])), roughMeasure).filter((c) => c.kind === 'value');
  assert.deepEqual(chips.map((c) => [c.owner, c.text]), [['k1', '????????']]);
  assert.ok(chips[0].box.y0 >= r32.bounds[1] + r32.bounds[3], 'below it');
});

test('splitter arms: their bit ranges, on the spine\'s far side when free (v1 S-02), else past the arm\'s end', () => {
  const s32 = place(part('Wiring/Splitter fanout=3 incoming=32 bit0=0 bit1=1'), 'k1', 0, 0);
  const chips = layoutChips(scene([s32]), wireMarks(scene([s32])), roughMeasure).filter((c) => c.kind === 'arm');
  // bit0 → arm 0, bit1 → arm 1, bits 2–10 arm 0, 11–21 arm 1, 22–31 arm 2 (the engine's attributes)
  assert.deepEqual(chips.map((c) => c.text), ['[10:2,0]', '[21:11,1]', '[31:22]']);
  for (const c of chips) assert.ok(c.box.x1 <= s32.bounds[0], `${c.text} on the far side`);
});

test('an arm the student named in the Splitter editor shows its range and its name, once (N-12, S-22: no original "0-7" beside it)', () => {
  const s32 = { ...place(part('Wiring/Splitter fanout=3 incoming=32 bit0=0 bit1=1'), 'k1', 0, 0), ext: { arms: ['lo', '', 'hi'] } };
  const chips = layoutChips(scene([s32]), wireMarks(scene([s32])), roughMeasure).filter((c) => c.kind === 'arm');
  assert.deepEqual(chips.map((c) => c.text), ['[10:2,0] lo', '[21:11,1]', '[31:22] hi']);
  // the splitter's own drawing writes no bit numbers: the chips are the only ones
  const drawn = rendererFor(s32).draw(s32, { value: () => undefined, body: undefined, measure: roughMeasure });
  assert.deepEqual(drawn.filter((sh) => sh.k === 'text'), []);
});

test('an arm\'s name that would cover a part next to the splitter is left off: the range alone, where it stands free (no chip over a part or a wire)', () => {
  const s32 = { ...place(part('Wiring/Splitter fanout=3 incoming=32 bit0=0 bit1=1'), 'k1', 0, 0), ext: { arms: ['instruction', 'register', 'immediate'] } };
  // a part whose right edge is a little left of the spine: room for "[10:2,0]", not for "[10:2,0] instruction"
  const plain = layoutChips(scene([s32]), wireMarks(scene([s32])), roughMeasure).filter((c) => c.kind === 'arm');
  const rangeOnly = { ...s32, ext: undefined };
  const widest = Math.max(...layoutChips(scene([rangeOnly]), wireMarks(scene([rangeOnly])), roughMeasure).filter((c) => c.kind === 'arm').map((c) => c.box.x1 - c.box.x0));
  const block: Component = { ...place(part('Arithmetic/Adder width=8'), 'k2', 0, 0) };
  const [bx, by, bw, bh] = block.bounds;
  const dx = s32.bounds[0] - widest - 16 - (bx + bw), dy = s32.bounds[1] - by;
  const near = { ...block, loc: [block.loc[0] + dx, block.loc[1] + dy] as [number, number], bounds: [bx + dx, by + dy, bw, bh] as [number, number, number, number], ports: block.ports.map((q) => ({ ...q, loc: [q.loc[0] + dx, q.loc[1] + dy] as [number, number] })) };
  assert.deepEqual(plain.map((c) => c.text), ['[10:2,0] instruction', '[21:11,1] register', '[31:22] immediate'], 'alone: the names');
  const crowded = layoutChips(scene([s32, near]), wireMarks(scene([s32, near])), roughMeasure).filter((c) => c.kind === 'arm');
  assert.deepEqual(crowded.map((c) => c.text), ['[10:2,0]', '[21:11,1]', '[31:22]']);
  for (const c of crowded) assert.ok(c.box.x1 <= s32.bounds[0] && c.box.x0 > near.bounds[0] + near.bounds[2], `${c.text} between the part and the spine`);
});

test('tunnel colours: a colour the student picked (ext.color) is every tunnel of that name\'s, and a near name takes another', () => {
  const a = { ...place(part('Wiring/Tunnel facing=west'), 'k1', 0, 0), attrs: { ...part('Wiring/Tunnel facing=west').attrs, label: 'a' } };
  // (a's automatic colour is the palette's first, orange: pick another)
  assert.equal(sceneTunnelColors(scene([a])).get('a'), '#e69f00');
  const a2 = { ...place(a, 'k2', 100, 0), ext: { color: '#332288' } };
  const b = { ...place(a, 'k3', 0, 60), attrs: { ...a.attrs, label: 'b' } };
  const colors = sceneTunnelColors(scene([a, a2, b]));
  assert.equal(colors.get('a'), '#332288');
  assert.notEqual(colors.get('b'), '#332288', 'b is near a');
  const picked = tunnelColors([{ name: 'x', at: [0, 0], color: '#332288' }, { name: 'y', at: [10, 0] }]);
  assert.equal(picked.get('x'), '#332288');
  assert.notEqual(picked.get('y'), '#332288');
});

test('bus widths: beside the longest wire of a bus, left out where neither side is free (v1 E-03)', () => {
  const wires = [{ id: 'w1', a: [0, 0] as [number, number], b: [200, 0] as [number, number] }];
  const s = scene([], wires, [{ id: 'n0', width: 32, wires: ['w1'], ports: [] }]);
  const chips = layoutChips(s, wireMarks(s), roughMeasure).filter((c) => c.kind === 'width');
  assert.deepEqual(chips.map((c) => c.text), ['32']);
  assert.ok(chips[0].box.y1 <= 0, 'above the wire');
  // wires just above and below: no room
  const tight = [...wires, { id: 'w2', a: [0, -8] as [number, number], b: [200, -8] as [number, number] }, { id: 'w3', a: [0, 8] as [number, number], b: [200, 8] as [number, number] }];
  const t = scene([], tight, [{ id: 'n0', width: 32, wires: ['w1'], ports: [] }, { id: 'n1', width: 1, wires: ['w2'], ports: [] }, { id: 'n2', width: 1, wires: ['w3'], ports: [] }]);
  assert.deepEqual(layoutChips(t, wireMarks(t), roughMeasure).filter((c) => c.kind === 'width'), []);
});

test('port names: outside, off the wire\'s way, 9 px at every zoom, never over a chip (v1 S-06); none for a drawn appearance', () => {
  const adder = place(part('Arithmetic/Adder width=8'), 'k1', 0, 0);
  const names = portNames(adder, 2, roughMeasure, []);
  assert.deepEqual(names.map((n) => n.text), ['a', 'b', 'sum', 'cin', 'cout']);
  const a = names[0];
  assert.ok(a.x < adder.bounds[0] && a.anchor === 'end' && a.baseline === 'bottom', 'left of a port on the left edge, above its wire');
  assert.equal(a.size, 4.5);
  const taken = [{ x0: adder.bounds[0] - 30, y0: adder.bounds[1], x1: adder.bounds[0], y1: adder.bounds[1] + 20 }];
  assert.ok(!portNames(adder, 2, roughMeasure, taken).some((n) => n.text === 'a'), 'not over a chip');
  // what the body already writes (a register's D and Q) is not written again; a little larger past 200 %
  const reg = place(part('Memory/Register width=8'), 'k3', 0, 0);
  assert.deepEqual(portNames(reg, 2, roughMeasure, [], new Set(['D', 'Q'])).map((n) => n.text), ['clk', 'clr', 'en']);
  assert.ok(portNames(reg, 4, roughMeasure, [])[0].size * 4 > 12);
  // a name keeps MIN_GAP_PX from the wires' strokes at every zoom (UI review of #425: `sum` sat on its wire at 400 %)
  const sum = adder.ports[2];
  const wire = { a: sum.loc as [number, number], b: [sum.loc[0] + 60, sum.loc[1]] as [number, number], bits: 8 };
  for (const z of [1, 2, 4]) {
    const n = portNames(adder, z, roughMeasure, [], new Set(), [wire]).find((x) => x.text === 'sum');
    assert.ok(n, `sum at ${z * 100} %`);
    const stroke = wirePx(z, 8) / 2 / z;
    assert.ok(sum.loc[1] - stroke - n.y >= MIN_GAP_PX / z - 1e-9, `sum ${sum.loc[1] - stroke - n.y} units above the stroke at ${z * 100} %`);
  }
  // a wire right where the name would go: the name is left out rather than written on it
  const across = { a: [sum.loc[0] + 1, sum.loc[1] - 6] as [number, number], b: [sum.loc[0] + 40, sum.loc[1] - 6] as [number, number], bits: 1 };
  assert.ok(!portNames(adder, 2, roughMeasure, [], new Set(), [wire, across]).some((x) => x.text === 'sum'));
  const gate = place(part('Gates/AND Gate facing=east size=50 inputs=3'), 'k2', 0, 0);
  assert.deepEqual(portNames(gate, 2, roughMeasure, []), [], 'gates have none');
  const regfile = part('circuit/regfile facing=east');
  assert.deepEqual(portNames(regfile, 2, roughMeasure, []), [], 'the student\'s appearance names its ports itself');
  const half = part('circuit/half_adder facing=east');
  assert.equal(portNames(half, 2, roughMeasure, []).length, 4, 'the default box does not (v1 S-08)');
});
