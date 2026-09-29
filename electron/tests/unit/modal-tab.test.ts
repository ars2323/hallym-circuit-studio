/* src/renderer/shared/modal-tab.ts: Tab goes round inside a modal dialog
   at its two ends; in between Chromium's own order (D-164). */

import assert from 'node:assert/strict';
import { test } from 'node:test';

import { tabWithin } from '../../src/renderer/shared/modal-tab.ts';

test('one button (an error\'s Close): Tab and Shift+Tab stay on it', () => {
  assert.equal(tabWithin(1, 0, false), 0);
  assert.equal(tabWithin(1, 0, true), 0);
});

test('past the last control to the first, before the first to the last', () => {
  assert.equal(tabWithin(3, 2, false), 0);
  assert.equal(tabWithin(3, 0, true), 2);
});

test('in between: Chromium\'s own move', () => {
  assert.equal(tabWithin(3, 0, false), null);
  assert.equal(tabWithin(3, 1, false), null);
  assert.equal(tabWithin(3, 1, true), null);
  assert.equal(tabWithin(3, 2, true), null);
});

test('focus on none of them: the first (Shift+Tab: the last); no control at all: nowhere', () => {
  assert.equal(tabWithin(3, -1, false), 0);
  assert.equal(tabWithin(3, -1, true), 2);
  assert.equal(tabWithin(0, -1, false), -1);
  assert.equal(tabWithin(0, -1, true), -1);
});
