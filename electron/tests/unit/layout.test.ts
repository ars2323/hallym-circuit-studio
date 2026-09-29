/* src/renderer/app/logic/layout.ts: where the panels go (D-135, D-158: v1 X-03 and Y-01). */

import assert from 'node:assert/strict';
import { test } from 'node:test';

import { arrange, BOTTOM_FOLD, CANVAS_LEAST, canvasLeast, HEAD, LOWER_FOLD, NARROW_PX, noFolds, nothingDragged, PAD, SPLITTER, TIGHT_PX } from '../../src/renderer/app/logic/layout.ts';

const canvas = (width: number, a: ReturnType<typeof arrange>) => width - 2 * PAD - a.left - a.right - SPLITTER * (a.narrow ? 1 : 2);
// The Canvas panel's height: the work area's inner height less the bottom panel and its splitter.
const canvasHeight = (height: number, a: ReturnType<typeof arrange>) => height - 2 * PAD - SPLITTER - a.bottom;

test('the lab PCs (1920x1080 at 100, 125, 150 %): three columns, the Canvas the widest', () => {
  for (const [w, h] of [[1920, 968], [1536, 760], [1280, 616]]) {
    const a = arrange(w, h, nothingDragged(), noFolds());
    assert.equal(a.narrow, false, `${w}`);
    assert.equal(a.tight, false, `${w}`);
    assert.ok(a.left >= 220 && a.left <= 300, `${w}: left ${a.left}`);
    assert.ok(a.right >= 240 && a.right <= 320, `${w}: right ${a.right}`);
    assert.ok(canvas(w, a) > a.left + a.right, `${w}: canvas ${canvas(w, a)}`);
    assert.ok(a.bottom >= BOTTOM_FOLD && a.bottom < h / 2, `${w}: bottom ${a.bottom}`);
    assert.equal(a.bottomFolded, false);
    assert.equal(a.lowerFolded, false);
  }
});

test('narrow (half a screen): no right column, Attributes in the left panel\'s tabs', () => {
  const a = arrange(NARROW_PX - 1, 900, nothingDragged(), noFolds());
  assert.equal(a.narrow, true);
  assert.equal(a.tight, false);
  assert.equal(a.right, 0);
  assert.equal(arrange(960, 900, nothingDragged(), noFolds()).left, 288); // room for three tabs
  assert.equal(arrange(820, 900, nothingDragged(), noFolds()).left, 280);
  assert.equal(arrange(NARROW_PX, 900, nothingDragged(), noFolds()).narrow, false);
});

test('tight (683 px, half a 1366 screen): one side at a time, the whole width each', () => {
  const a = arrange(683, 700, nothingDragged(), noFolds());
  assert.equal(a.tight, true);
  assert.equal(a.narrow, true);
  assert.equal(a.right, 0);
  assert.equal(a.left, 683 - 2 * PAD);
  assert.equal(arrange(TIGHT_PX - 1, 700, nothingDragged(), noFolds()).tight, true);
  assert.equal(arrange(TIGHT_PX, 700, nothingDragged(), noFolds()).tight, false);
});

test('the Canvas keeps half the width (v1 X-03): the right column gives way first, then the left', () => {
  assert.equal(canvasLeast(1920), 960);
  assert.equal(canvasLeast(600), CANVAS_LEAST);
  for (const w of [TIGHT_PX, 900, 1099, 1100, 1280, 1536, 1920]) {
    const a = arrange(w, 800, nothingDragged(), noFolds());
    assert.ok(canvas(w, a) >= canvasLeast(w) - 1, `${w}: canvas ${canvas(w, a)} < ${canvasLeast(w)}`);
  }
  const d = { ...nothingDragged(), left: 400, right: 350, bottom: 300 };
  const a = arrange(1920, 968, d, noFolds());
  assert.deepEqual([a.left, a.right, a.bottom], [400, 350, 300]);
  const greedy = arrange(1280, 616, { ...nothingDragged(), left: 900, right: 900 }, noFolds());
  assert.ok(canvas(1280, greedy) >= canvasLeast(1280) - 1, `canvas ${canvas(1280, greedy)}`);
  assert.equal(greedy.right, 200);   // the right column gave way first
  const tiny = arrange(1280, 616, { ...nothingDragged(), left: 10, bottom: 5 }, noFolds());
  assert.equal(tiny.left, 180);
  assert.equal(tiny.bottom, BOTTOM_FOLD);
});

test('the Canvas keeps half the height (v1 Y-01): the bottom panel cut down, folded when too little is left; the student\'s height back when there is room', () => {
  // dragged tall: cut down to leave the Canvas half
  const tall = arrange(1920, 968, { ...nothingDragged(), bottom: 800 }, noFolds());
  assert.ok(canvasHeight(968, tall) >= (968 - 2 * PAD - SPLITTER) / 2, `canvas ${canvasHeight(968, tall)}`);
  // a low window (v1's 910x505 less the bars): folded, the window's doing
  const low = arrange(910, 240, nothingDragged(), noFolds());
  assert.equal(low.bottomFolded, true);
  assert.equal(low.bottomAuto, true);
  assert.equal(low.bottom, HEAD + 2);
  // a tab pressed opens it (until the size changes: app.ts forgets bottomOpened then)
  const opened = arrange(910, 240, nothingDragged(), { ...noFolds(), bottomOpened: true });
  assert.equal(opened.bottomFolded, false);
  assert.ok(opened.bottom >= BOTTOM_FOLD);
  // the student's Collapse: folded, not the window's doing, whatever the room
  const collapsed = arrange(1920, 968, nothingDragged(), { ...noFolds(), collapsed: true });
  assert.deepEqual([collapsed.bottomFolded, collapsed.bottomAuto, collapsed.bottom], [true, false, HEAD + 2]);
  // a larger window: the student's height again
  assert.equal(arrange(1920, 968, { ...nothingDragged(), bottom: 300 }, noFolds()).bottom, 300);
});

test('the left column\'s lower panel folds the same way under the upper one\'s half', () => {
  const a = arrange(1920, 968, nothingDragged(), noFolds());
  assert.ok(a.lower >= LOWER_FOLD && a.lower <= (968 - 2 * PAD) / 2, `lower ${a.lower}`);
  const low = arrange(1280, 200, nothingDragged(), noFolds());
  assert.equal(low.lowerFolded, true);
  assert.equal(low.lower, HEAD + 2);
  assert.equal(arrange(1280, 200, nothingDragged(), { ...noFolds(), lowerOpened: true }).lowerFolded, false);
  const tall = arrange(1920, 968, { ...nothingDragged(), lower: 900 }, noFolds());
  assert.ok(968 - 2 * PAD - SPLITTER - tall.lower >= (968 - 2 * PAD - SPLITTER) / 2 - 1, `lower ${tall.lower}`);
});
