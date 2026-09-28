/* The window's first engine status (D-146, logic/engine-status.ts): a push
   that comes while the question is on its way wins over the older answer. */

import assert from 'node:assert/strict';
import { test } from 'node:test';

import type { EngineStatus } from '../../src/main/protocol.ts';
import { StatusKeeper } from '../../src/renderer/app/logic/engine-status.ts';

const st = (state: EngineStatus['state']): EngineStatus => ({ state, generation: 0, hello: null, error: null, detail: null });

test('the answer is taken when nothing was pushed meanwhile', async () => {
  const k = new StatusKeeper(st('starting'));
  assert.equal((await k.ask(async () => st('ready'))).state, 'ready');
  assert.equal(k.status.state, 'ready');
});

test('"ready" pushed while the question was on its way is not undone by the older "starting" answer', async () => {
  const k = new StatusKeeper(st('starting'));
  let answer!: (s: EngineStatus) => void;
  const asked = k.ask(() => new Promise<EngineStatus>((done) => { answer = done; }));
  k.pushed(st('ready'));
  answer(st('starting'));
  assert.equal((await asked).state, 'ready');
  assert.equal(k.status.state, 'ready');
});

test('a push after the answer is the newest', async () => {
  const k = new StatusKeeper(st('starting'));
  await k.ask(async () => st('starting'));
  k.pushed(st('ready'));
  assert.equal(k.status.state, 'ready');
});
