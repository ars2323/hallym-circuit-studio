/* src/renderer/app/logic/cycle.ts and values.ts: which cycles the table
   shows, what a cell says, the status bar's cycle facts, Run Until's form
   and words (N-14, D-144). */

import assert from 'node:assert/strict';
import { test } from 'node:test';

import type { RecordState, RunUntilDone } from '../../src/main/protocol.ts';
import {
  bitOf, cellText, columnsThatFit, columnWindow, COLUMN_PX, cycleFacts, nameWidth, pinGone, sameAsBefore, untilRequest, untilResult, wave,
} from '../../src/renderer/app/logic/cycle.ts';
import { address, binGroups, dec, defined, hex, hex32, signed, unsigned } from '../../src/renderer/app/logic/values.ts';

test('values: hex keeps the width and writes x and E digits; decimal is signed; binary in fours', () => {
  assert.equal(hex('00000000000000000000000000101010'), '0x0000002a');
  assert.equal(hex('0101'), '0x5');
  assert.equal(hex('00000101'), '0x05');
  assert.equal(hex('1xxxx0000'), '0x1x0', 'a nibble with an x bit is x');
  assert.equal(hex('E0001111'), '0xEf');
  assert.equal(hex(null), '');
  assert.equal(hex('101', false), '5');
  assert.equal(dec('11111111111111111111111111111111'), '-1');
  assert.equal(dec('0111'), '7');
  assert.equal(dec('1000'), '-8', 'the width is the sign');
  assert.equal(dec('01x1'), '');
  assert.deepEqual(binGroups('000100101010'), ['0001', '0010', '1010']);
  assert.deepEqual(binGroups('10101'), ['1', '0101']);
  assert.equal(unsigned('1'.repeat(32)), 0xffffffff);
  assert.equal(unsigned('1'.repeat(33)), null);
  assert.equal(signed('10000000000000000000000000000000'), -2147483648);
  assert.equal(defined('0101'), true);
  assert.equal(defined('01x1'), false);
  assert.equal(defined(''), false);
  assert.equal(address('0x0040002c'), 0x0040002c);
  assert.equal(address('40002c'), null);
  assert.equal(hex32(0x10010000), '0x10010000');
  assert.equal(hex32(-1), '0xffffffff');
});

test('columnWindow: follows the latest cycle, keeps a shown window, centres a cycle outside it', () => {
  assert.deepEqual(columnWindow(0, 30, 30, 5, null), { from: 26, to: 30 }, 'the latest: the last five');
  assert.deepEqual(columnWindow(0, 3, 3, 5, null), { from: 0, to: 3 }, 'fewer cycles than room');
  assert.deepEqual(columnWindow(0, 30, 27, 5, { from: 26, to: 30 }), { from: 26, to: 30 }, 'still in the window: it stays');
  assert.deepEqual(columnWindow(0, 30, 10, 5, { from: 26, to: 30 }), { from: 8, to: 12 }, 'outside: centred');
  assert.deepEqual(columnWindow(0, 30, 1, 5, null), { from: 0, to: 4 }, 'near the start: from the first');
  assert.deepEqual(columnWindow(100, 130, 100, 5, null), { from: 100, to: 104 }, 'never before the first kept');
  assert.deepEqual(columnWindow(0, 30, 29, 5, { from: 20, to: 24 }), { from: 26, to: 30 }, 'never past the last');
  assert.deepEqual(columnWindow(0, -1, 0, 5, null), { from: 0, to: -1 }, 'nothing recorded');
});

test('widths: the name column fits the longest name within limits; columns that fit', () => {
  assert.equal(nameWidth(['PC']), 96);
  assert.equal(nameWidth(['regfile › RD1']), 13 * 7 + 30);
  assert.equal(nameWidth(['x'.repeat(80)]), 200);
  assert.equal(columnsThatFit(96 + 2 + 3 * COLUMN_PX, 96), 3);
  assert.equal(columnsThatFit(50, 96), 1, 'at least one');
});

test('cells: a bus in hex, a bit as it is, a repeated value marked, a bit of a bus', () => {
  assert.equal(cellText('00000101', 8), '0x05');
  assert.equal(cellText('1', 1), '1');
  assert.equal(cellText(null, 8), '');
  assert.equal(sameAsBefore(['01', '01', '10'], 1), true);
  assert.equal(sameAsBefore(['01', '01', '10'], 2), false);
  assert.equal(sameAsBefore([null, null], 1), false, 'nothing recorded is not a repeat');
  assert.equal(bitOf('0110', 0), '0');
  assert.equal(bitOf('0110', 2), '1');
  assert.equal(bitOf('0110', 4), null);
});

test('wave: two halves a cycle, an edge where the level changes', () => {
  const halves = ['0', '1', '1'];
  const values = ['0', '0', '1'];
  assert.deepEqual(wave(halves, values, 0), { a: '0', b: '0', edgeIn: false, edgeMid: false });
  assert.deepEqual(wave(halves, values, 1), { a: '1', b: '0', edgeIn: true, edgeMid: true }, 'rises after the edge, falls before the next');
  assert.deepEqual(wave(halves, values, 2), { a: '1', b: '1', edgeIn: true, edgeMid: false });
});

const state = (o: Partial<RecordState>): RecordState => ({
  fileId: 'f1', empty: false, first: 0, last: 12, cycle: 12, past: false, generation: 1, pc: '0x00400030', cpu: true, rows: 0, pinned: 0, runUntil: null, ...o,
});

test('cycleFacts: Cycle N and PC, "Cycle 5 / 12" on a past cycle; the clock\'s count before anything is recorded', () => {
  assert.deepEqual(cycleFacts(state({}), 12), { cycle: 'Cycle 12', pc: 'PC 0x00400030', past: false });
  assert.deepEqual(cycleFacts(state({ cycle: 5, past: true }), 5), { cycle: 'Cycle 5 / 12', pc: 'PC 0x00400030', past: true });
  assert.deepEqual(cycleFacts(state({ pc: null, cycle: 1234, last: 1234 }), 0), { cycle: 'Cycle 1,234', pc: null, past: false });
  assert.deepEqual(cycleFacts(null, 3), { cycle: 'Cycle 3', pc: null, past: false });
  assert.deepEqual(cycleFacts(state({ empty: true }), null), { cycle: null, pc: null, past: false });
});

test('untilRequest: what to send, or why not yet', () => {
  const f = (o: Partial<Parameters<typeof untilRequest>[0]>) => untilRequest({ kind: 'pc', value: '', row: '', max: '10000', ...o });
  assert.deepEqual(f({ value: '0x00400038' }), { ok: true, params: { kind: 'pc', value: '0x00400038', maxCycles: 10000 } });
  assert.deepEqual(f({ value: 'fact' }), { ok: true, params: { kind: 'pc', value: 'fact', maxCycles: 10000 } }, 'a label: the engine reads it');
  assert.equal(f({ value: '' }).ok, false);
  assert.equal(f({ value: '0x12 34' }).ok, false);
  assert.deepEqual(f({ kind: 'instruction', value: ' BEQ ' }), { ok: true, params: { kind: 'instruction', value: 'beq', maxCycles: 10000 } });
  assert.equal(f({ kind: 'instruction', value: '1beq' }).ok, false);
  assert.deepEqual(f({ kind: 'row', row: 'r3' }), { ok: true, params: { kind: 'row', value: 'r3', maxCycles: 10000 } });
  const noRow = f({ kind: 'row', row: '' });
  assert.equal(noRow.ok, false);
  assert.match(noRow.ok ? '' : noRow.why, /Add to Cycle View/);
  assert.deepEqual(f({ kind: 'halt', max: '' }), { ok: true, params: { kind: 'halt', maxCycles: 10000 } }, 'the default limit');
  assert.deepEqual(f({ kind: 'errorOrX', max: '50' }), { ok: true, params: { kind: 'errorOrX', maxCycles: 50 } });
  for (const max of ['0', '-1', '1.5', 'many', '2000000']) assert.equal(f({ kind: 'halt', max }).ok, false, max);
});

test('untilResult: v1\'s words, the row by its name', () => {
  const d = (o: Partial<RunUntilDone>): RunUntilDone => ({ fileId: 'f1', result: 'met', cycle: 12, from: 3, kind: 'pc', value: '0x00400038', ...o });
  const names = (id: string) => (id === 'r1' ? 'ALUResult' : undefined);
  assert.equal(untilResult(d({}), names), '사이클 12에서 멈췄습니다: PC 0x00400038');
  assert.equal(untilResult(d({ kind: 'instruction', value: 'beq' }), names), '사이클 12에서 멈췄습니다: 다음 명령어 beq');
  assert.equal(untilResult(d({ kind: 'row', value: 'r1' }), names), '사이클 12에서 멈췄습니다: ALUResult 값이 바뀜');
  assert.equal(untilResult(d({ kind: 'errorOrX', value: undefined }), names), '사이클 12에서 멈췄습니다: E 또는 X 값이 생김');
  assert.equal(untilResult(d({ kind: 'halt', value: undefined }), names), '사이클 12에서 멈췄습니다: halt 또는 exit');
  assert.equal(untilResult(d({ result: 'limit', cycle: 10003, from: 3 }), names), '10,000사이클을 돌았지만 조건을 만나지 않았습니다.');
  assert.equal(untilResult(d({ result: 'stopped' }), names), '사이클 12에서 멈췄습니다.');
  assert.equal(untilResult(d({ result: 'off' }), names), '시뮬레이션이 꺼져서 사이클 12에서 멈췄습니다.');
});

test('pinGone: the pinned rows go when their message leaves the list (D-114), not before', () => {
  assert.equal(pinGone(undefined, []), false);
  assert.equal(pinGone('m3', ['m1', 'm3']), false);
  assert.equal(pinGone('m3', ['m1', 'm2']), true);
  assert.equal(pinGone('m3', []), true);
});
