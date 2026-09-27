/* src/renderer/app/logic/layout.ts: where the panels go. */

import assert from 'node:assert/strict';
import { test } from 'node:test';

import { arrange, CANVAS_LEAST, NARROW_PX, nothingDragged, PAD, SPLITTER } from '../../src/renderer/app/logic/layout.ts';

const canvas = (width: number, a: ReturnType<typeof arrange>) => width - 2 * PAD - a.left - a.right - SPLITTER * (a.narrow ? 1 : 2);

test('the lab PCs (1920x1080 at 100, 125, 150 %): three columns, the Canvas the widest', () => {
  for (const [w, h] of [[1920, 968], [1536, 760], [1280, 616]]) {
    const a = arrange(w, h, nothingDragged(), false);
    assert.equal(a.narrow, false, `${w}`);
    assert.ok(a.left >= 220 && a.left <= 300, `${w}: left ${a.left}`);
    assert.ok(a.right >= 240 && a.right <= 320, `${w}: right ${a.right}`);
    assert.ok(canvas(w, a) > a.left + a.right, `${w}: canvas ${canvas(w, a)}`);
    assert.ok(a.bottom >= 94 && a.bottom < h / 2, `${w}: bottom ${a.bottom}`);
  }
});

test('narrow (half a screen): no right column, Attributes in the left panel\'s tabs', () => {
  const a = arrange(NARROW_PX - 1, 900, nothingDragged(), false);
  assert.equal(a.narrow, true);
  assert.equal(a.right, 0);
  assert.equal(arrange(960, 900, nothingDragged(), false).left, 288); // room for three tabs
  assert.equal(arrange(800, 900, nothingDragged(), false).left, 280);
  assert.equal(arrange(NARROW_PX, 900, nothingDragged(), false).narrow, false);
});

test('dragged sizes are kept, within limits, and never take the Canvas\'s least', () => {
  const d = { ...nothingDragged(), left: 400, right: 350, bottom: 300 };
  const a = arrange(1920, 968, d, false);
  assert.deepEqual([a.left, a.right, a.bottom], [400, 350, 300]);
  const greedy = arrange(1280, 616, { ...nothingDragged(), left: 900, right: 900 }, false);
  assert.ok(canvas(1280, greedy) >= CANVAS_LEAST, `canvas ${canvas(1280, greedy)}`);
  const tiny = arrange(1280, 616, { ...nothingDragged(), left: 10, bottom: 5 }, false);
  assert.equal(tiny.left, 180);
  assert.equal(tiny.bottom, 94);
});

test('the bottom panel collapsed: its head only', () => {
  assert.equal(arrange(1920, 968, nothingDragged(), true).bottom, 36);
});
