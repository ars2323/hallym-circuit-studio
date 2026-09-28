/* N-06 geometry parity (D-137): for every part kind × representative
   attributes (tests/fixtures/geometry.json, written by the engine through
   its API: ./gradlew :engine:canvasFixtures, regenerated and compared in
   CI), the renderer registry's drawing
     - puts a port mark exactly where the engine says each port is (and no other),
     - keeps the body inside the engine's bounds (a tunnel's tag may grow for a long name, only away from its point),
     - touches every port with its body or a stub (a wire ending at the port meets the part),
   with no values and with every port at 1, so what the values draw stays inside too.
   Then a circuit drawn here and opened in the original Logisim 2.7.1 has
   its wires meeting the same points. */

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { test } from 'node:test';

import type { Component } from '../../src/main/protocol.ts';
import { NO_STATE, type PartState } from '../../src/renderer/canvas/parts/common.ts';
import { FALLBACK_KINDS, kindOf, REGISTRY, rendererFor } from '../../src/renderer/canvas/registry.ts';
import { distanceTo, type Shape, shapeBox } from '../../src/renderer/canvas/shapes.ts';

const FIXTURE = path.join(import.meta.dirname, '../fixtures/geometry.json');
const cases = (JSON.parse(readFileSync(FIXTURE, 'utf8')) as { cases: { case: string; component: Component }[] }).cases;

const ones: (c: Component) => PartState = (c) => ({ value: (i) => '1'.repeat(Math.max(1, c.ports[i]?.width ?? 1)), body: undefined });

interface Problem { case: string; what: string }

export function check(c: Component, st: PartState): { problems: string[]; ports: number } {
  const problems: string[] = [];
  const shapes: Shape[] = rendererFor(c).draw(c, st);
  const [bx, by, bw, bh] = c.bounds;
  const marks = shapes.filter((s) => s.k === 'port');
  // 1. port marks: one per engine port, exactly at its location
  for (const q of c.ports) {
    const m = marks.filter((s) => s.k === 'port' && s.i === q.i);
    if (m.length !== 1) { problems.push(`port ${q.i} (${q.name}): ${m.length} marks`); continue; }
    const s = m[0];
    if (s.k === 'port' && (s.x !== q.loc[0] || s.y !== q.loc[1])) problems.push(`port ${q.i} (${q.name}) drawn at ${s.x},${s.y}, engine ${q.loc}`);
  }
  if (marks.length !== c.ports.length) problems.push(`${marks.length} port marks for ${c.ports.length} ports`);
  // 2. inside the bounds (geometry, strokes not counted); text by its fitted width.  A stub may
  //    reach a port the engine puts outside its own bounds (Logisim's size-70 gate with 4 inputs).
  const eps = 0.51;
  const px = c.ports.map((q) => q.loc[0]), py = c.ports.map((q) => q.loc[1]);
  const reach = { x0: Math.min(bx, ...px), y0: Math.min(by, ...py), x1: Math.max(bx + bw, ...px), y1: Math.max(by + bh, ...py) };
  // A shape that grows (a tunnel's tag for a long name) may pass the bounds only on the sides no port is on.
  const onEdge = { x0: c.ports.some((q) => q.loc[0] <= bx), y0: c.ports.some((q) => q.loc[1] <= by),
    x1: c.ports.some((q) => q.loc[0] >= bx + bw), y1: c.ports.some((q) => q.loc[1] >= by + bh) };
  for (const s of shapes) {
    if (s.k === 'port') continue;
    const b = shapeBox(s);
    const lim = s.role === 'stub' ? reach : s.grows
      ? { x0: onEdge.x0 ? bx : -Infinity, y0: onEdge.y0 ? by : -Infinity, x1: onEdge.x1 ? bx + bw : Infinity, y1: onEdge.y1 ? by + bh : Infinity }
      : { x0: bx, y0: by, x1: bx + bw, y1: by + bh };
    if (b.x0 < lim.x0 - eps || b.y0 < lim.y0 - eps || b.x1 > lim.x1 + eps || b.y1 > lim.y1 + eps) {
      problems.push(`${s.k}${s.k === 'text' ? ` "${s.text}"` : ''} (${s.role}) at ${[b.x0, b.y0, b.x1, b.y1].map((v) => Math.round(v * 10) / 10)} outside ${c.bounds}`);
    }
  }
  // 3. every port touched by the body or a stub
  const solid = shapes.filter((s) => s.k !== 'port' && s.k !== 'text');
  for (const q of c.ports) {
    const touch = Math.min(...solid.map((s) => distanceTo(q.loc, s) - ('width' in s && s.width ? s.width / 2 : 0)));
    if (!(touch <= 0.26)) problems.push(`port ${q.i} (${q.name}) at ${q.loc} not touched (${touch.toFixed(2)} away)`);
  }
  return { problems, ports: c.ports.length };
}

test(`N-06: every kind × attributes (${cases.length} cases) draws its ports where the engine says, inside its bounds, attached`, () => {
  const problems: Problem[] = [];
  let ports = 0;
  const kinds = new Set<string>();
  for (const { case: name, component } of cases) {
    kinds.add(kindOf(component));
    for (const [label, st] of [['no values', NO_STATE], ['all ones', ones(component)]] as const) {
      const r = check(component, st);
      ports += r.ports;
      for (const what of r.problems) problems.push({ case: `${name} (${label})`, what });
    }
  }
  console.log(`geometry parity: ${cases.length} cases, ${kinds.size} kinds, ${ports} port checks (two states each), ${problems.length} problems`);
  assert.deepEqual(problems.slice(0, 30), [], `${problems.length} problems`);
  assert.ok(cases.length >= 800, `${cases.length} cases`);
});

test('the fixture covers every renderer of the registry and the default renderer\'s kinds', () => {
  const kinds = new Set(cases.map((c) => kindOf(c.component)));
  for (const r of REGISTRY) assert.ok(kinds.has(`${r.lib}/${r.name}`), `${r.lib}/${r.name} has no case`);
  for (const k of FALLBACK_KINDS) assert.ok(kinds.has(k), `${k} has no case`);
});

test('the check catches a port drawn off its place, a body out of its bounds, a port the body does not reach', () => {
  const c = cases.find((x) => x.case === 'Gates/AND Gate facing=east size=50 inputs=3')!.component;
  assert.deepEqual(check(c, NO_STATE).problems, []);
  const moved: Component = { ...c, ports: c.ports.map((q) => (q.i === 1 ? { ...q, loc: [q.loc[0], q.loc[1] + 10] } : q)) };
  // the renderer takes ports from the engine: a moved engine port moves the mark, so compare with the original
  const shapes = rendererFor(moved).draw(moved, NO_STATE);
  const mark = shapes.find((s) => s.k === 'port' && s.i === 1);
  assert.ok(mark && mark.k === 'port' && mark.y !== c.ports[1].loc[1]);
  const shrunk: Component = { ...c, bounds: [c.bounds[0] + 5, c.bounds[1], c.bounds[2] - 5, c.bounds[3]] };
  assert.ok(check(shrunk, NO_STATE).problems.some((p) => p.includes('outside')));
  const adder = cases.find((x) => x.case === 'Arithmetic/Adder width=8')!.component;
  const far: Component = { ...adder, ports: [...adder.ports, { i: 5, loc: [adder.loc[0] + 30, adder.loc[1] + 40], width: 1, dir: 'in', name: 'far' }] };
  assert.ok(check(far, NO_STATE).problems.some((p) => p.includes('port 5 (far)') && p.includes('not touched')));
});
