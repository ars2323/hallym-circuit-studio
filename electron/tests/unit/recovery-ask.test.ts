/* src/renderer/app/logic/recovery-ask.ts: the question when a file has a
   recovery file beside it (N-19, D-152) -- facts only, the time it was
   written, the two answers; Esc opens nothing -- and the window's rules
   for words (no particle right after a name, no "하면 됩니다", no 한림). */

import assert from 'node:assert/strict';
import { test } from 'node:test';

import type { RecoveryAsk } from '../../src/main/protocol.ts';
import type { Opened } from '../../src/renderer/app/api.ts';
import { answerRecovery, DISCARD, RECOVER, recoveredNote, recoveryQuestion, stamp } from '../../src/renderer/app/logic/recovery-ask.ts';

const at = new Date(2026, 8, 28, 14, 5, 31).getTime();   // local time
const ask: RecoveryAsk = { ask: { id: 'r1', name: 'lab3.circ', recovery: 'lab3.circ.hcs-recover', modified: at } };
const opened: Opened = { fileId: 'f1', name: 'lab3.circ', path: '/w/lab3.circ', circuits: [], main: 'c1', libraries: [], already: false };

test('the time, as this PC keeps it: year-month-day hour:minute', () => {
  assert.equal(stamp(at), '2026-09-28 14:05');
  assert.equal(stamp(new Date(2027, 0, 2, 3, 4).getTime()), '2027-01-02 03:04');
});

test('the question: unsaved edits and when, the file on its own line, the recovery file in the facts, Recover or Discard, no character', () => {
  const q = recoveryQuestion(ask.ask);
  assert.equal(q.title, '저장하지 않은 편집이 있습니다');
  assert.equal(q.file, 'lab3.circ');
  assert.match(q.body, /마지막으로 저장한 뒤의 편집이 복구 파일에 남아 있습니다\(2026-09-28 14:05\)\./);
  assert.match(q.body, /불러오면 저장하지 않은 편집으로 열리고, 버리면 복구 파일을 지운 뒤 저장한 파일을 엽니다\./);
  assert.deepEqual(q.names, [['Recovery file', 'lab3.circ.hcs-recover']]);
  assert.deepEqual([q.ok, q.cancel, q.character], [RECOVER, DISCARD, false]);
  assert.deepEqual([RECOVER, DISCARD], ['Recover', 'Discard']);
  assert.equal(recoveredNote('lab3.circ'), '저장하지 않은 편집을 불러왔습니다 · lab3.circ');
});

test('the words keep the window\'s rules', () => {
  const q = recoveryQuestion(ask.ask);
  for (const text of [q.title, q.body, recoveredNote('lab3.circ')]) {
    assert.doesNotMatch(text, /\.circ[은는이가을를의에로와과도]/);
    assert.doesNotMatch(text, /\.hcs-recover[은는이가을를의에로와과도]/);
    assert.doesNotMatch(text, /(Recover|Discard)[은는이가을를의에와과도](?![가-힣])/);
    assert.doesNotMatch(text, /하면 됩니다|한림/);
  }
});

test('answering: a file opens as it was; a question is asked, and Recover, Discard or Esc is what is opened', async () => {
  const calls: string[] = [];
  const open = async (id: string, c: 'recover' | 'discard' | null) => { calls.push(`${id} ${c}`); return c === null ? null : { ...opened, recovered: c === 'recover' }; };
  const never = async () => { throw new Error('not asked'); };
  assert.equal(await answerRecovery(opened, never, open), opened);
  assert.equal(await answerRecovery(null, never, open), null);
  const asked: string[] = [];
  const choose = (a: 'ok' | 'cancel' | null) => async (q: { title: string }) => { asked.push(q.title); return a; };
  assert.equal((await answerRecovery(ask, choose('ok'), open))?.recovered, true);
  assert.equal((await answerRecovery(ask, choose('cancel'), open))?.recovered, false);
  assert.equal(await answerRecovery(ask, choose(null), open), null);
  assert.deepEqual(calls, ['r1 recover', 'r1 discard', 'r1 null']);
  assert.equal(asked.length, 3);
});
