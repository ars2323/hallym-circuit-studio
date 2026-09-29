/* The screenshots' fixed conditions (tests/e2e/screen-conditions.ts, D-167):
   the time they fix and how the window writes it, the fake engine's clock
   held there, and the launch environment. */

import assert from 'node:assert/strict';
import { test } from 'node:test';

import { FIXED_MS, FIXED_TIME, screenOptions, SCREEN_TZ, START_AT } from '../e2e/screen-conditions.ts';
import { fakeClock } from '../fake-engine/fake-mips.ts';

test('the fixed time is 10:00:00 in Seoul, and the video\'s second is 3.0', () => {
  assert.equal(FIXED_TIME, '2026-09-28T10:00:00+09:00');
  assert.equal(new Date(FIXED_MS).toISOString(), '2026-09-28T01:00:00.000Z');
  assert.equal(SCREEN_TZ, 'Asia/Seoul');
  const seoul = new Date(FIXED_MS).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false, timeZone: SCREEN_TZ });
  assert.equal(seoul, '10:00:00');
  assert.equal(START_AT, 3.0);
});

test('the launch gets the zone and the fake engine\'s clock; a scene\'s own env comes over them', () => {
  assert.deepEqual(screenOptions().env, { TZ: 'Asia/Seoul', FAKE_ENGINE_NOW: String(FIXED_MS) });
  const o = screenOptions({ env: { FAKE_ENGINE_MODE: 'wide-registers', TZ: 'UTC' }, switches: ['--x'] });
  assert.deepEqual(o.env, { TZ: 'UTC', FAKE_ENGINE_NOW: String(FIXED_MS), FAKE_ENGINE_MODE: 'wide-registers' });
  assert.deepEqual(o.switches, ['--x']);
});

test('the fake engine\'s clock: held, it stands still; not held, a second per reading from 13:47:44', () => {
  assert.equal(fakeClock(FIXED_MS), FIXED_MS);
  assert.equal(fakeClock(FIXED_MS), FIXED_MS);
  const a = fakeClock(null), b = fakeClock(null);
  assert.equal(b - a, 1000);
  assert.ok(a >= Date.parse('2026-09-28T13:47:44+09:00'));
  assert.notEqual(fakeClock(Number.NaN), Number.NaN, 'a value that is not a time is not held');
});
