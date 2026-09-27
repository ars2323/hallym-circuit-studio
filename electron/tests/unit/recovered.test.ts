/* src/renderer/app/logic/recovered.ts: what the dialog and the band say
   after the engine died and started again -- facts only, names after a
   colon (no particle after a name), the simulation's Reset said. */

import assert from 'node:assert/strict';
import { test } from 'node:test';

import type { Recovered } from '../../src/main/protocol.ts';
import { CLOSED_REASON, LOST_REASON, RESET, recoveredText } from '../../src/renderer/app/logic/recovered.ts';

const NAMES: Record<string, string> = { f1: 'lab3.circ', f2: 'untitled.circ', f3: 'gone.circ' };
const name = (id: string) => NAMES[id];
const base: Recovered = { generation: 2, attempt: 1, crash: { how: 'signal SIGKILL', log: ['[hcs-engine] info: ready'] }, restored: [], lost: [], closed: [] };

test('every file back with its edits: how many, how many edits, the simulation from Reset', () => {
  const t = recoveredText({ ...base, restored: [{ fileId: 'f1', edits: 4, dirty: true }, { fileId: 'f2', edits: 0, dirty: false }] }, name);
  assert.equal(t.title, '엔진이 멈췄다가 다시 시작했습니다');
  assert.equal(t.body, '회로를 돌리는 엔진(Java)이 끝나서 다시 시작했습니다. 열려 있던 파일 2개를 다시 열고 저장하지 않은 편집 4개를 다시 적용했습니다. 시뮬레이션은 Reset 상태로 돌아갔습니다.');
  assert.equal(t.detail, '다시 엶: lab3.circ · 편집 4개 다시 적용\n다시 엶: untitled.circ\n엔진: signal SIGKILL\n[hcs-engine] info: ready');
  assert.equal(t.band, `엔진이 멈춰서 다시 시작했습니다 · 파일 2개를 되살렸습니다 · ${RESET}`);
});

test('no unsaved edits anywhere: the files opened again', () => {
  const t = recoveredText({ ...base, restored: [{ fileId: 'f1', edits: 0, dirty: false }] }, name);
  assert.match(t.body, /열려 있던 파일 1개를 다시 열었습니다\./);
});

test('edits that could not come back: the band lists the files, as last saved; the dialog says why', () => {
  const t = recoveredText({ ...base, attempt: 2, restored: [{ fileId: 'f2', edits: 1, dirty: true }], lost: [{ fileId: 'f1', reason: 'crashedAgain', edits: 7 }] }, name);
  assert.equal(t.band, `저장하지 않은 편집을 되살리지 못했습니다: lab3.circ · 마지막으로 저장한 상태로 열었습니다 · ${RESET}`);
  assert.match(t.body, /파일 1개는 저장하지 않은 편집을 되살리지 못해 마지막으로 저장한 상태로 열었습니다\./);
  assert.match(t.detail, /^다시 엶: untitled\.circ · 편집 1개 다시 적용\n저장한 상태로 엶: lab3\.circ · 되살리는 중에 엔진이 다시 멈춤 · 편집 7개\n/);
});

test('a file that could not be opened again: closed, listed', () => {
  const t = recoveredText({ ...base, restored: [{ fileId: 'f1', edits: 0, dirty: false }], closed: [{ fileId: 'f3', reason: 'missing' }] }, name);
  assert.equal(t.band, `엔진이 멈춰서 다시 시작했습니다 · 파일 1개를 되살렸습니다 · 다시 열지 못해 닫았습니다: gone.circ · ${RESET}`);
  assert.match(t.body, /파일 1개는 다시 열지 못해 닫았습니다\./);
  assert.match(t.detail, /닫음: gone\.circ · 그 자리에 파일이 없음/);
});

test('nothing was open: one sentence, no Reset (there is no simulation)', () => {
  const t = recoveredText({ ...base, crash: null }, name);
  assert.equal(t.body, '회로를 돌리는 엔진(Java)이 끝나서 다시 시작했습니다. 열려 있던 파일은 없었습니다.');
  assert.equal(t.band, '엔진이 멈춰서 다시 시작했습니다');
  assert.equal(t.detail, '');
});

test('the words keep the window\'s rules: no particle right after a name, no "하면 됩니다", no 한림', () => {
  const all: Recovered = {
    ...base,
    restored: [{ fileId: 'f2', edits: 2, dirty: true }],
    lost: (Object.keys(LOST_REASON) as Recovered['lost'][number]['reason'][]).map((reason) => ({ fileId: 'f1', reason, edits: 1 })),
    closed: (Object.keys(CLOSED_REASON) as Recovered['closed'][number]['reason'][]).map((reason) => ({ fileId: 'f3', reason })),
  };
  const t = recoveredText(all, name);
  for (const text of [t.title, t.body, t.detail, t.band]) {
    assert.doesNotMatch(text, /\.circ[은는이가을를의에로와과도]/);
    assert.doesNotMatch(text, /(Reset|Java|Close)[은는이가을를의에와과도](?![가-힣])/);
    assert.doesNotMatch(text, /하면 됩니다|한림/);
  }
});
