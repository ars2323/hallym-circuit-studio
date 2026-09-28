/* src/renderer/canvas/gestures.ts (N-08): Logisim 2.7.1's gesture rules as
   the screen shows them -- the grid (Canvas.snapXToGrid), a pointer's
   logical point, Wire.contains, the Edit tool's wiring point (EditTool
   updateLocation + isWiringPoint, Alt turning it round), a click (within 2
   units), the Wire tool's L and its bend (WiringTool.computeMove),
   shortening (willShorten + getShortenResult), a drag's offset
   (SelectTool.computeDxDy) and the boxes. */

import assert from 'node:assert/strict';
import { test } from 'node:test';

import type { Component, Point, Wire } from '../../src/main/protocol.ts';
import { boundsOf, dragOffset, endsAt, isClick, logical, rectOf, snapCoord, snapPoint, snapsToGrid, WireDrag, wireContains, wireEndsAt, wireLength, wiresEndingAt, wiringPoint } from '../../src/renderer/canvas/gestures.ts';

const wire = (id: string, a: Point, b: Point): Wire => ({ id, a, b });
const part = (id: string, loc: Point, ports: Point[], over: Partial<Component> = {}): Component => ({
  id, lib: 'Gates', name: 'AND Gate', loc, bounds: [loc[0] - 50, loc[1] - 25, 50, 50], facing: 'east', attrs: {},
  ports: ports.map((p, i) => ({ i, loc: p, width: 1, dir: i === ports.length - 1 ? 'out' : 'in' })), ...over,
});

test('snapCoord: the nearest multiple of 10, a half away from zero (Canvas.snapXToGrid)', () => {
  assert.deepEqual([4, 5, 14, 15, 0, -4, -5, -6, -15].map(snapCoord), [0, 10, 10, 20, 0, -0, -10, -10, -20]);
  assert.deepEqual(snapPoint([104, 96]), [100, 100]);
  assert.deepEqual(logical([10.4, 10.6]), [10, 11]);
});

test('wireContains: on the wire, 2 units either side, the ends included; endsAt and length', () => {
  const h = wire('w1', [100, 100], [200, 100]);
  const v = wire('w2', [50, 200], [50, 100]);
  assert.equal(wireContains(h, [150, 102]), true);
  assert.equal(wireContains(h, [150, 103]), false);
  assert.equal(wireContains(h, [100, 100]), true);
  assert.equal(wireContains(h, [99, 100]), false);
  assert.equal(wireContains(v, [48, 150]), true, 'the ends in either order');
  assert.equal(wireEndsAt(v, [50, 100]), true);
  assert.equal(wireLength(h), 100);
});

test('endsAt and wiresEndingAt: ports and wire ends at a point (Circuit.getComponents)', () => {
  const parts = { components: [part('k1', [300, 100], [[250, 90], [250, 110], [300, 100]])], wires: [wire('w1', [250, 90], [200, 90]), wire('w2', [200, 90], [200, 150])] };
  assert.equal(endsAt(parts, [250, 90]), 2);
  assert.equal(endsAt(parts, [200, 120]), 0, 'the middle of a wire is not an end');
  assert.deepEqual(wiresEndingAt(parts, [200, 90]).map((w) => w.id), ['w1', 'w2']);
});

test('wiringPoint: the nearest grid point within 6 units, on a port or a wire; Alt turns it round; a selected wire\'s middle selects', () => {
  const parts = { components: [part('k1', [300, 100], [[250, 90], [300, 100]])], wires: [wire('w1', [100, 200], [200, 200])] };
  const none = new Set<string>();
  assert.deepEqual(wiringPoint(parts, none, [252, 92], false), [250, 90], 'a port');
  assert.deepEqual(wiringPoint(parts, none, [150, 203], false), [150, 200], 'the middle of a wire');
  assert.deepEqual(wiringPoint(parts, none, [100, 200], false), [100, 200], 'a wire\'s end');
  assert.equal(wiringPoint(parts, none, [245, 85], false), null, 'the port (250, 90) is the nearest grid point, but dx² + dy² = 50: too far');
  assert.equal(wiringPoint(parts, none, [400, 400], false), null, 'nothing there');
  assert.equal(wiringPoint(parts, none, [150, 200], true), null, 'Alt on a wire selects');
  assert.deepEqual(wiringPoint(parts, none, [405, 405], true), [410, 410], 'Alt elsewhere draws, however far from the grid');
  assert.equal(wiringPoint(parts, new Set(['w1']), [150, 200], false), null, 'the middle of a selected wire: a drag moves it');
  assert.deepEqual(wiringPoint(parts, new Set(['w1']), [200, 200], false), [200, 200], 'its end still draws');
});

test('isClick: released within 2 units (dx² + dy² ≤ 4)', () => {
  assert.equal(isClick([10, 10], [12, 10]), true);
  assert.equal(isClick([10, 10], [11, 11]), true);
  assert.equal(isClick([10, 10], [12, 11]), false);
});

test('WireDrag: the first move decides the L\'s bend, a move back in line decides again; straight wires have two points', () => {
  const empty = { components: [], wires: [] };
  const h = new WireDrag(empty, [100, 100]);
  assert.equal(h.move([100, 100]), false, 'no move');
  h.move([140, 100]);
  h.move([160, 180]);
  assert.deepEqual(h.points(), [[100, 100], [160, 100], [160, 180]], 'horizontal first');
  assert.equal(h.dragged, true);
  const v = new WireDrag(empty, [100, 100]);
  v.move([100, 130]);
  v.move([160, 180]);
  assert.deepEqual(v.points(), [[100, 100], [100, 180], [160, 180]], 'vertical first');
  // back on the start's column: the direction turns to vertical, and back on its row too: to horizontal
  v.move([100, 150]);
  assert.equal(v.direction, 'v');
  v.move([130, 100]);
  assert.equal(v.direction, 'h');
  v.move([100, 100]);
  assert.equal(v.direction, 0, 'back at the start: undecided');
  const s = new WireDrag(empty, [104, 96]);
  assert.deepEqual(s.start, [100, 100], 'the start on the grid');
  s.move([200, 104]);
  assert.deepEqual(s.points(), [[100, 100], [200, 100]]);
});

test('WireDrag.shortened: from a wire\'s end back along it, what is left; to the other end, nothing; off it, none', () => {
  const w = wire('w1', [100, 100], [300, 100]);
  const parts = { components: [], wires: [w] };
  const back = new WireDrag(parts, [300, 100]);
  back.move([200, 100]);
  assert.deepEqual(back.shortened(), { wire: w, rest: [[200, 100], [100, 100]] });
  back.move([100, 100]);
  assert.deepEqual(back.shortened(), { wire: w, rest: null }, 'all of it: the wire goes');
  const out = new WireDrag(parts, [300, 100]);
  out.move([400, 100]);
  assert.equal(out.shortened(), null, 'outwards: a new wire');
  const into = new WireDrag(parts, [200, 50]);
  into.move([200, 100]);
  assert.equal(into.shortened(), null, 'onto the middle of a wire from elsewhere');
  // ending on a wire's end, having started inside that wire: shortened from that end (the second search)
  const fromInside = new WireDrag(parts, [200, 100]);
  fromInside.move([100, 100]);
  assert.deepEqual(fromInside.shortened(), { wire: w, rest: [[200, 100], [300, 100]] });
});

test('dragOffset: not past the page\'s top or left, on the grid when a part keeps to it (SelectTool.computeDxDy)', () => {
  const box = { x0: 30, y0: 40, x1: 90, y1: 90 };
  assert.deepEqual(dragOffset(box, true, 14, 26), [10, 30]);
  assert.deepEqual(dragOffset(box, true, -100, -100), [-30, -40], 'clamped at the origin');
  assert.deepEqual(dragOffset(box, false, 14, 26), [14, 26], 'a Label only: not on the grid');
  assert.deepEqual(dragOffset(null, true, 7, 7), [10, 10]);
});

test('boundsOf, rectOf, snapsToGrid', () => {
  const k = part('k1', [300, 100], []);
  assert.deepEqual(boundsOf([k], [wire('w', [100, 20], [100, 400])]), { x0: 100, y0: 20, x1: 300, y1: 400 });
  assert.equal(boundsOf([], []), null);
  assert.deepEqual(rectOf([50, 60], [10, 20]), { x0: 10, y0: 20, x1: 50, y1: 60 });
  assert.equal(snapsToGrid(k), true);
  assert.equal(snapsToGrid({ ...k, lib: 'Base', name: 'Text' }), false);
});
