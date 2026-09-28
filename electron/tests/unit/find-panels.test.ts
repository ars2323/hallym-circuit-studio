/* The models of Find, Tunnels and the Minimap (N-12, D-150): Find's rows
   from the engine's answer (tests/fixtures/find.json, the real engine's),
   the tunnels of a circuit by name with their colours and the next one to
   go to, the Minimap's fit. */

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { test } from 'node:test';

import type { Component, FindGroup, Snapshot } from '../../src/main/protocol.ts';
import { findRows, groupKey, KIND_NAMES, keepRow, placesCount, placeText, pressed, revealOfPlace } from '../../src/renderer/app/logic/find.ts';
import { AROUND, fitMap, fromMap, MARGIN, toMap, viewRect } from '../../src/renderer/app/logic/minimap.ts';
import { COLOR_NAMES, palette, TunnelCycle, tunnelEntries, tunnelTip } from '../../src/renderer/app/logic/tunnels.ts';
import { paletteIndex } from '../../src/renderer/canvas/labels.ts';
import { TUNNEL_PALETTE } from '../../src/renderer/canvas/tokens.ts';

type Recorded = Record<string, Record<string, { groups: (Omit<FindGroup, 'places'> & { places: { place: string; near: boolean; at: [number, number] }[] })[]; more: boolean }>>;
const FIND = JSON.parse(readFileSync(path.join(import.meta.dirname, '../fixtures/find.json'), 'utf8')) as Recorded;
const groupsOf = (file: string, q: string): FindGroup[] => FIND[file][q].groups.map((g) => ({
  ...g, places: g.places.map((p, i) => ({ circuitId: 'c1', root: 'c1', path: [], componentId: `k${i}`, at: p.at, place: p.place, near: p.near })),
}));

// ---- Find -----------------------------------------------------------------------------------

test('Find: a row a group, the places of an opened group under it, the same keys every time', () => {
  const g = groupsOf('demo-datapath.circ', 'clk');
  assert.deepEqual(g.map((x) => `${x.kind} ${x.text} · ${x.path} (${x.places.length})`), [
    'tunnel clk · main › clk (4)', 'pin clk · main › regfile #1 › clk (1)', 'tunnel clk · main › regfile #1 › clk (4)',
  ]);
  const closed = findRows(g, new Set());
  assert.equal(closed.length, 3);
  const open = findRows(g, new Set([groupKey(g[0])]));
  assert.equal(open.length, 3 + 4);
  assert.deepEqual(open.slice(0, 5).map((r) => r.child), [false, true, true, true, true]);
  assert.equal(new Set(open.map((r) => r.key)).size, open.length);
  assert.equal(open[1].place, g[0].places[0]);
});

test('Find: a group of several opens on a click, a place goes, a double click always goes', () => {
  const g = groupsOf('demo-datapath.circ', 'clk');
  const rows = findRows(g, new Set([groupKey(g[0])]));
  assert.equal(pressed(rows[0], 1), 'toggle');
  assert.equal(pressed(rows[1], 1), 'go');
  const single = rows.find((r) => !r.child && r.group.places.length === 1)!;
  assert.equal(pressed(single, 1), 'none', 'v1: one click on a group of one only chooses it');
  assert.equal(pressed(single, 2), 'go');
  assert.equal(pressed(rows[0], 2), 'go');
});

test('Find: places are named by the port they are next to (S-09, S-28), never by an internal name', () => {
  for (const [file, queries] of Object.entries(FIND)) {
    if (file === 'source') continue;
    for (const r of Object.values(queries)) {
      for (const g of r.groups) {
        for (const p of g.places) {
          assert.doesNotMatch(p.place, /\b(Split|Reg|Mux|Add) #\d/, p.place);
          assert.doesNotMatch(p.place, /\.(in\d|out|combined)\b/, p.place);
        }
      }
    }
  }
  const near = groupsOf('demo-datapath.circ', 'clk')[0].places.find((p) => p.near)!;
  assert.match(placeText(near), /^next to main › /);
  assert.equal(placeText({ ...near, near: false }), near.place);
  assert.equal(placesCount(1), '1 place');
  assert.equal(placesCount(4), '4 places');
  assert.deepEqual(Object.values(KIND_NAMES), ['Label', 'Pin', 'Tunnel', 'Subcircuit', 'Part']);
});

test('Find: going there is "show this place" in the selection\'s look, into its instance', () => {
  const p = { circuitId: 'c2', root: 'c1', path: ['k7'], componentId: 'k9', at: [100, 300] as [number, number], place: 'main › regfile #1 › RR1', near: false };
  assert.deepEqual(revealOfPlace('f1', p), {
    fileId: 'f1', messageId: null, circuitId: 'c2', root: 'c1', path: ['k7'], components: ['k9'], wires: [], nets: [], at: [100, 300], cycle: null, tone: 'find',
  });
});

test('Find: the chosen row stays when the list comes again, else the first', () => {
  const g = groupsOf('demo-datapath.circ', 'clk');
  const rows = findRows(g, new Set());
  assert.equal(keepRow(rows, rows[2].key), 2);
  assert.equal(keepRow(rows, 'gone'), 0);
  assert.equal(keepRow(rows, null), 0);
  assert.equal(keepRow([], null), -1);
});

// ---- Tunnels --------------------------------------------------------------------------------

const tunnel = (id: string, label: string, x: number, y: number, color?: string): Component => ({
  id, lib: 'Wiring', name: 'Tunnel', loc: [x, y], bounds: [x - 20, y - 10, 20, 20], facing: 'west', attrs: { label }, ports: [{ i: 0, loc: [x, y], width: 1, dir: 'inout' }],
  ...(color ? { ext: { color } } : {}),
});
const snap = (components: Component[]): Snapshot => ({ circuitId: 'c1', name: 'main', components, wires: [], nets: [], junctions: [] });

test('Tunnels: by exact name (case-insensitive order), top to bottom, a lone one marked; no name, no row', () => {
  const s = snap([
    tunnel('k1', 'pc', 100, 200), tunnel('k2', 'PC', 300, 100), tunnel('k3', 'pc', 50, 100), tunnel('k4', 'ALUOp', 0, 0),
    tunnel('k5', '', 0, 50), { ...tunnel('k6', 'x', 0, 0), name: 'Pin' },
  ]);
  const e = tunnelEntries(s);
  assert.deepEqual(e.map((x) => [x.name, x.ids, x.lone]), [['ALUOp', ['k4'], true], ['PC', ['k2'], true], ['pc', ['k3', 'k1'], false]]);
  assert.equal(tunnelTip(e[0]), 'ALUOp: 같은 이름의 터널이 하나뿐입니다.');
  assert.equal(tunnelTip(e[2]), 'pc: 2 tunnels');
});

test('Tunnels: the colour the student picked wins; the rest are the Canvas\'s automatic colours', () => {
  const s = snap([tunnel('k1', 'a', 0, 0), tunnel('k2', 'a', 900, 900, '#E69F00'), tunnel('k3', 'b', 10, 0)]);
  const e = tunnelEntries(s);
  assert.equal(e[0].color, '#e69f00');
  assert.equal(e[0].chosen, true);
  assert.equal(e[1].chosen, false);
  assert.equal(e[1].color, TUNNEL_PALETTE[paletteIndex('b') === 0 ? 1 : paletteIndex('b')], 'b: its own colour unless a near name took it');
  assert.equal(palette().length, 12);
  assert.deepEqual(palette().map((p) => p.color), [...TUNNEL_PALETTE]);
  assert.equal(COLOR_NAMES.length, 12);
});

test('Tunnels: each press the next tunnel of the name, then the first again; another name starts at its first', () => {
  const e = tunnelEntries(snap([tunnel('k1', 'a', 0, 0), tunnel('k2', 'a', 0, 10), tunnel('k3', 'a', 0, 20), tunnel('k4', 'b', 0, 0)]));
  const c = new TunnelCycle();
  assert.deepEqual([c.next(e[0]), c.next(e[0]), c.next(e[0]), c.next(e[0])], ['k1', 'k2', 'k3', 'k1']);
  assert.equal(c.next(e[1]), 'k4');
  assert.equal(c.next(e[0]), 'k1');
  c.reset();
  assert.equal(c.next(e[0]), 'k1');
});

// ---- the Minimap ----------------------------------------------------------------------------

test('Minimap: the circuit and 20 units around it fit the panel with 8 px to spare, centred; the way back is exact', () => {
  const f = fitMap({ x0: 100, y0: 50, x1: 500, y1: 250 }, 300, 200);
  const [x0, y0] = toMap(f, [100 - AROUND, 50 - AROUND]);
  const [x1, y1] = toMap(f, [500 + AROUND, 250 + AROUND]);
  assert.ok(Math.abs(x0 - MARGIN) < 1e-6 && Math.abs(x1 - (300 - MARGIN)) < 1e-6, 'fills the width');
  assert.ok(Math.abs((y0 + y1) / 2 - 100) < 1e-6, 'centred up and down');
  const p = fromMap(f, toMap(f, [321, 123]));
  assert.ok(Math.abs(p[0] - 321) < 1e-9 && Math.abs(p[1] - 123) < 1e-9);
  const empty = fitMap({ x0: Infinity, y0: Infinity, x1: -Infinity, y1: -Infinity }, 300, 200);
  assert.ok(Number.isFinite(empty.scale) && empty.scale > 0);
  const r = viewRect(f, { x0: 100, y0: 50, x1: 300, y1: 150 });
  assert.ok(Math.abs(r.w - 200 * f.scale) < 1e-9 && Math.abs(r.h - 100 * f.scale) < 1e-9);
});
