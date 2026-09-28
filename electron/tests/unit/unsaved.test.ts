/* src/renderer/app/logic/unsaved.ts: before unsaved changes would be lost
   (quitting, closing a tab; N-19, D-152) -- the question's words and the
   order of the answers: Save goes through the save (a cancelled save stops),
   Discard goes on, Cancel and Esc stop; files without changes are not asked
   about. */

import assert from 'node:assert/strict';
import { test } from 'node:test';

import { CANCEL, DISCARD, SAVE, settleUnsaved, unsavedQuestion } from '../../src/renderer/app/logic/unsaved.ts';

test('the question: a sentence for quitting and one for closing, the file on its own line, Save / Discard / Cancel, plain', () => {
  const q = unsavedQuestion('lab3.circ', 'quit');
  assert.equal(q.title, '저장하지 않은 변경이 있습니다');
  assert.equal(q.file, 'lab3.circ');
  assert.equal(q.body, '저장하지 않고 끝내면 이 파일의 변경은 사라집니다.');
  assert.equal(unsavedQuestion('lab3.circ', 'close').body, '저장하지 않고 닫으면 이 파일의 변경은 사라집니다.');
  assert.deepEqual([q.ok, q.extra, q.cancel, q.character], [SAVE, DISCARD, CANCEL, false]);
  assert.deepEqual([SAVE, DISCARD, CANCEL], ['Save', 'Discard', 'Cancel']);
  for (const t of [q.title, q.body, unsavedQuestion('x', 'close').body]) {
    assert.doesNotMatch(t, /\.circ[은는이가을를의에로와과도]|(Save|Discard|Cancel)[은는이가을를의에와과도](?![가-힣])|하면 됩니다|한림/);
  }
});

type F = { fileId: string; name: string; dirty: boolean };
const files: F[] = [{ fileId: 'f1', name: 'a.circ', dirty: true }, { fileId: 'f2', name: 'b.circ', dirty: false }, { fileId: 'f3', name: 'c.circ', dirty: true }];

async function run(answers: ('ok' | 'extra' | 'cancel' | null)[], saved = true): Promise<{ go: boolean; asked: string[]; saves: string[]; shown: string[] }> {
  const asked: string[] = [];
  const saves: string[] = [];
  const shown: string[] = [];
  const go = await settleUnsaved(files, 'quit', {
    dirty: async (f) => f.dirty,
    show: (f) => { shown.push(f.fileId); },
    choose: async (q) => { asked.push(q.file); return answers.shift() ?? null; },
    save: async (f) => { saves.push(f.fileId); return saved; },
  });
  return { go, asked, saves, shown };
}

test('each file with unsaved changes, in order, on show while asked; Save saves, Discard goes on', async () => {
  assert.deepEqual(await run(['ok', 'extra']), { go: true, asked: ['a.circ', 'c.circ'], saves: ['f1'], shown: ['f1', 'f3'] });
  assert.deepEqual(await run(['extra', 'extra']), { go: true, asked: ['a.circ', 'c.circ'], saves: [], shown: ['f1', 'f3'] });
});

test('Cancel, Esc or a save that did not happen (its dialog cancelled, it failed) stops there', async () => {
  assert.deepEqual(await run(['cancel']), { go: false, asked: ['a.circ'], saves: [], shown: ['f1'] });
  assert.deepEqual(await run([null]), { go: false, asked: ['a.circ'], saves: [], shown: ['f1'] });
  assert.deepEqual(await run(['extra', 'ok'], false), { go: false, asked: ['a.circ', 'c.circ'], saves: ['f3'], shown: ['f1', 'f3'] });
});

test('nothing unsaved: nothing asked, go on', async () => {
  const go = await settleUnsaved([files[1]], 'close', { dirty: async () => false, show: () => assert.fail(), choose: async () => assert.fail(), save: async () => assert.fail() });
  assert.equal(go, true);
});
