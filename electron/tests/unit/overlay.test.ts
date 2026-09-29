/* src/renderer/shared/overlay.ts: the caption buttons' colour under
   the tutorial's dim and a dialog's backdrop. */

import assert from 'node:assert/strict';
import { test } from 'node:test';

import { captionPatch, composite, FIRST_SCREEN_PATCH, hex, NAVY, overlayColor, WHITE, WHITE_PATCH } from '../../src/renderer/shared/overlay.ts';

test('white under nothing is white; under navy at 100% is navy', () => {
  assert.equal(hex(composite(WHITE, [])), '#ffffff');
  assert.equal(hex(composite(WHITE, [{ color: NAVY, alpha: 1 }])), '#00205b');
});

test('the tutorial\'s dim, a dialog\'s backdrop, and both: each darker than the last', () => {
  const none = overlayColor(false, false);
  const tutorial = overlayColor(true, false);
  const dialog = overlayColor(false, true);
  const both = overlayColor(true, true);
  assert.equal(none, '#ffffff');
  assert.equal(tutorial, '#bdc5d4'); // 255 * .74 + navy * .26
  const lum = (c: string) => parseInt(c.slice(1, 3), 16) + parseInt(c.slice(3, 5), 16) + parseInt(c.slice(5, 7), 16);
  assert.ok(lum(tutorial) < lum(none) && lum(dialog) < lum(tutorial) && lum(both) < lum(dialog), [none, tutorial, dialog, both].join(' '));
});

test('the patch and its symbols (Hallym MIPS v2.6.0, D-169): see-through with white symbols on the first screen, whatever covers it; else white or white under the layers, navy symbols', () => {
  assert.deepEqual(FIRST_SCREEN_PATCH, { color: '#00000000', symbolColor: '#ffffff' });
  assert.deepEqual(WHITE_PATCH, { color: '#ffffff', symbolColor: '#00205b' });
  for (const t of [false, true]) for (const d of [false, true]) {
    assert.deepEqual(captionPatch(true, t, d), FIRST_SCREEN_PATCH);
    assert.deepEqual(captionPatch(false, t, d), { color: overlayColor(t, d), symbolColor: '#00205b' });
  }
});
