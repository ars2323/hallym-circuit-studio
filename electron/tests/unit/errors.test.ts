/* src/renderer/app/logic/errors.ts: the window's own words for an engine
   error -- a Korean sentence, the file's name alone, the engine's English
   only where it is the fact to act on. */

import assert from 'node:assert/strict';
import { test } from 'node:test';

import { commandError, fileError } from '../../src/renderer/app/logic/errors.ts';

// The real engine's errors (engine/ Files.java, docs/engine-api.md 2).
const fileErr = (reason: string, message: string, extra: Record<string, unknown> = {}) =>
  ({ name: 'EngineError', code: 2, message, data: { path: '/home/student/과제/lab3.circ', reason, ...extra } });

test('open: every reason in Korean, "File:" with the name only, never the engine\'s English or the whole path', () => {
  for (const [reason, message] of [['notFound', 'no such file: /home/student/과제/lab3.circ'], ['unreadable', 'cannot read: /home/student/과제/lab3.circ']]) {
    const d = fileError('open', fileErr(reason, message));
    assert.equal(d.title, '파일을 열지 못했습니다');
    assert.equal(d.file, 'lab3.circ');
    assert.match(d.body, /[가-힣]/);
    assert.equal(d.detail, undefined);
    for (const s of [d.title, d.body]) {
      assert.ok(!s.includes('/home/student') && !s.includes('lab3.circ') && !/cannot|no such/.test(s), `${reason}: ${s}`);
    }
  }
  assert.match(fileError('open', fileErr('notFound', '')).body, /Open으로 다시 골라 여세요/);
});

test('open: a loader failure keeps the loader\'s words as the detail; missing libraries are listed', () => {
  const load = fileError('open', fileErr('loadFailed', 'The file does not appear to be a Logisim project file'));
  assert.match(load.body, /Logisim이 이 파일을 회로로 읽지 못했습니다/);
  assert.equal(load.detail, 'The file does not appear to be a Logisim project file');
  const lib = fileError('open', fileErr('libraryMissing', 'missing library: adder.circ, hcs-mips.jar', { missing: ['adder.circ', 'hcs-mips.jar'] }));
  assert.match(lib.body, /라이브러리 파일/);
  assert.equal(lib.detail, 'adder.circ\nhcs-mips.jar');
});

test('save and new: their own titles; the name when the engine gives no path', () => {
  const save = fileError('save', fileErr('writeFailed', 'cannot write /x/lab3.circ'));
  assert.deepEqual([save.title, save.file], ['파일을 저장하지 못했습니다', 'lab3.circ']);
  assert.match(save.body, /Save As\(Ctrl\+Shift\+S\)/);
  const noPath = fileError('save', { name: 'EngineError', code: -32602, message: 'path is required' }, 'untitled.circ');
  assert.equal(noPath.file, 'untitled.circ');
  const made = fileError('new', { name: 'EngineError', code: 2, message: 'cannot create a new file: x', data: { path: null, reason: 'templateFailed' } });
  assert.deepEqual([made.title, made.file], ['새 회로를 만들지 못했습니다', undefined]);
  const gone = fileError('open', { name: 'EngineGone', message: '엔진이 멈췄습니다' });
  assert.match(gone.body, /엔진이 멈춰서/);
});

test('commandError: the command\'s name, then what it means in Korean -- never the engine\'s English', () => {
  const sim = (reason: string) => ({ name: 'EngineError', code: 4, message: 'the simulation stopped because the circuit oscillates', data: { reason } });
  assert.equal(commandError('1 Cycle', sim('oscillating')), '1 Cycle: 회로가 발진해서 시뮬레이션이 꺼져 있습니다. 회로를 고친 뒤 Reset을 누르세요');
  assert.equal(commandError('Run', sim('off')), 'Run: 시뮬레이션이 꺼져 있습니다. Reset을 누르세요');
  assert.match(commandError('Undo', { name: 'EngineError', code: 3, message: 'read-only file', data: { reason: 'readOnly' } }), /^Undo: 읽기 전용 파일입니다/);
  assert.equal(commandError('Reset', { name: 'EngineGone', message: 'x' }), 'Reset: 엔진이 멈춰서 하지 못했습니다');
  for (const s of [commandError('Run', sim('off')), commandError('Redo', { name: 'EngineError', code: -32603, message: 'NullPointerException' })]) {
    assert.ok(!/simulation|oscillat|Exception/.test(s), s);
  }
});
