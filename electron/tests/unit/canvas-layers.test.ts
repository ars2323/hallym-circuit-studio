/* The order parts are painted in (layers.ts; UI review of #425): no part
   hidden by another part's fill.  On demo-datapath, where a `pc` tunnel
   sits inside the Comparator (the .circ has it so; the original draws
   outlines only and it shows), every tunnel's name is painted after every
   fill that covers it -- and the same for the tunnels' tags' outlines. */

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { test } from 'node:test';

import type { Component, Snapshot } from '../../src/main/protocol.ts';
import { sceneTunnelColors, tunnelRoom } from '../../src/renderer/canvas/labels.ts';
import { byArea, paintOrder, split } from '../../src/renderer/canvas/layers.ts';
import type { PartState } from '../../src/renderer/canvas/parts/common.ts';
import { tunnelTextSize } from '../../src/renderer/canvas/parts/wiring.ts';
import { rendererFor } from '../../src/renderer/canvas/registry.ts';
import { Scene } from '../../src/renderer/canvas/scene.ts';
import { boxesMeet, type Paint, type Shape, shapeBox } from '../../src/renderer/canvas/shapes.ts';
import { wireMarks } from '../../src/renderer/canvas/wires.ts';

interface Fixture { main: string; circuits: Snapshot[]; watch: Record<string, { nets: Record<string, string>; bodies: Record<string, Record<string, unknown>> }[]> }
const fixture = JSON.parse(readFileSync(path.join(import.meta.dirname, '../fixtures/circuits/demo-datapath.json'), 'utf8')) as Fixture;

// The main circuit drawn as the Canvas draws it (its values, tunnel colours, room to grow).
function drawn(): { scene: Scene; order: ReturnType<typeof paintOrder> } {
  const scene = new Scene('f1', fixture.circuits.find((c) => c.circuitId === fixture.main)!);
  const frame = fixture.watch[fixture.main][0];
  scene.applyValues({ fileId: 'f1', circuitId: fixture.main, nets: frame.nets, bodies: frame.bodies });
  const marks = wireMarks(scene);
  const colors = sceneTunnelColors(scene);
  const state = (c: Component): PartState => ({
    value: (i) => scene.portValue(c.id, i), body: scene.bodies.get(c.id),
    tunnelColor: colors.get(c.attrs.label ?? ''), grow: c.name === 'Tunnel' ? tunnelRoom(scene, marks, c) : undefined,
  });
  const cache = new Map<string, ReturnType<typeof split>>();
  const order = paintOrder([...scene.components.values()], (c) => {
    if (!cache.has(c.id)) cache.set(c.id, split(rendererFor(c).draw(c, state(c))));
    return cache.get(c.id)!;
  });
  return { scene, order };
}

// A fill that hides what is under it (a light tint, like a tunnel's own, does not).
const opaque = (p: Paint | undefined) => p !== undefined && p !== 'none' && !(typeof p === 'object' && 'alpha' in p && p.alpha !== undefined && p.alpha < 0.5);

test('two passes: a part\'s body fills first, its outline and everything else on top; larger parts first', () => {
  const body: Shape = { k: 'rect', role: 'body', x: 0, y: 0, w: 10, h: 10, fill: 'body', stroke: 'bodyStroke', width: 1.6 };
  const deco: Shape = { k: 'text', role: 'deco', x: 5, y: 5, text: 'a', font: 'ui', size: 8 };
  const s = split([body, deco]);
  assert.deepEqual(s.base.map((x) => [x.k, 'fill' in x && x.fill, 'stroke' in x && x.stroke]), [['rect', 'body', undefined]]);
  assert.deepEqual(s.top.map((x) => [x.k, x.k === 'rect' ? x.fill : null, x.k === 'rect' ? x.stroke : null]), [['rect', undefined, 'bodyStroke'], ['text', null, null]]);
  const big = { bounds: [0, 0, 40, 40] } as Component, small = { bounds: [0, 0, 20, 18] } as Component, same = { bounds: [5, 5, 40, 40] } as Component;
  assert.deepEqual(byArea([small, big, same]), [big, same, small]);
});

test('demo-datapath: the pc tunnel inside the Comparator is drawn over the Comparator\'s fill', () => {
  const { scene, order } = drawn();
  const cmp = [...scene.components.values()].find((c) => c.name === 'Comparator' && c.loc[0] === 360 && c.loc[1] === 460)!;
  const pc = [...scene.components.values()].find((c) => c.name === 'Tunnel' && c.attrs.label === 'pc' && c.loc[0] === 320 && c.loc[1] === 450)!;
  assert.ok(cmp && pc, 'the .circ has a pc tunnel inside a Comparator');
  const at = (part: Component, pick: (s: Shape) => boolean) => order.findIndex((o) => o.part === part && pick(o.shape));
  const cmpFill = at(cmp, (s) => s.role === 'body' && 'fill' in s && s.fill !== undefined);
  const pcName = at(pc, (s) => s.k === 'text' && s.text === 'pc');
  const pcOutline = at(pc, (s) => s.role === 'body' && 'stroke' in s && s.stroke !== undefined && !('fill' in s && s.fill));
  assert.ok(cmpFill >= 0 && pcName > cmpFill && pcOutline > cmpFill, `${cmpFill} ${pcName} ${pcOutline}`);
});

test('demo-datapath: no tunnel\'s name or tag outline is covered by an opaque fill painted after it', () => {
  const { order } = drawn();
  const covered: string[] = [];
  let tunnels = 0;
  order.forEach((o, i) => {
    if (o.part.name !== 'Tunnel') return;
    const s = o.shape;
    const isName = s.k === 'text';
    const isOutline = s.role === 'body' && 'stroke' in s && s.stroke !== undefined;
    if (!isName && !isOutline) return;
    if (isName) tunnels++;
    const box = shapeBox(s);
    for (const later of order.slice(i + 1)) {
      if (later.part === o.part || later.shape.k === 'text' || later.shape.k === 'port') continue;
      if (!opaque('fill' in later.shape ? later.shape.fill : undefined)) continue;
      if (boxesMeet(shapeBox(later.shape), box)) covered.push(`${o.part.attrs.label} @${o.part.loc} under ${later.part.name} @${later.part.loc}`);
    }
  });
  assert.ok(tunnels >= 15, `${tunnels} tunnel names`);
  assert.deepEqual(covered, []);
});

test('tunnel names: one size for one font attribute; a long name grows the tag (not smaller text), as far as there is room', () => {
  const geometry = (JSON.parse(readFileSync(path.join(import.meta.dirname, '../fixtures/geometry.json'), 'utf8')) as { cases: { case: string; component: Component }[] }).cases;
  const t = geometry.find((c) => c.case === 'Wiring/Tunnel facing=east label=RegWrite')!.component;
  assert.equal(tunnelTextSize(t), 10.5);
  const wide = (text: string) => text.length * 8;   // a font wider than the engine's guess
  const st = (grow?: number): PartState => ({ value: () => undefined, body: undefined, measure: wide, grow });
  const shapes = (grow?: number) => rendererFor(t).draw(t, st(grow));
  const name = (list: Shape[]) => list.find((s) => s.k === 'text')!;
  const tag = (list: Shape[]) => list.find((s) => s.k === 'path')!;
  const free = shapes();
  const n = name(free);
  assert.ok(n.k === 'text' && n.size === 10.5 && (n.fit ?? 0) >= wide('RegWrite'), 'the name keeps its size and fits');
  assert.equal(tag(free).grows, true);
  assert.ok(shapeBox(tag(free)).x0 < t.bounds[0], 'the tag grew west, away from its point (east)');
  assert.ok(shapeBox(tag(free)).x1 <= t.bounds[0] + t.bounds[2] + 0.01, 'not past its point');
  const none = shapes(0);
  assert.equal(tag(none).grows, undefined, 'no room: no growth');
  const m = name(none);
  assert.ok(m.k === 'text' && (m.fit ?? Infinity) < wide('RegWrite'), 'the rest is made up by drawing the name narrower');
  // two tunnels with the default labelfont: the same size
  const other = geometry.find((c) => c.case === 'Wiring/Tunnel facing=north label=RegWrite')!.component;
  const o = name(rendererFor(other).draw(other, st()));
  assert.ok(o.k === 'text' && o.size === n.size);
});
