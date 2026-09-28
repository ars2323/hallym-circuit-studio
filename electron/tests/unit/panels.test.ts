/* The Registers and Memory panels' models (src/renderer/app/logic/
   registers.ts, memory.ts): Hallym MIPS's groups and cells, and the Data
   tab's lines from the engine's rows (N-14, D-140, D-144). */

import assert from 'node:assert/strict';
import { test } from 'node:test';

import type { MemoryRow, RegisterRow } from '../../src/main/protocol.ts';
import { memoryView, wordChars } from '../../src/renderer/app/logic/memory.ts';
import { registerGroups, registerViews, shape } from '../../src/renderer/app/logic/registers.ts';

const b32 = (n: number) => (n >>> 0).toString(2).padStart(32, '0');
const reg = (o: Partial<RegisterRow>): RegisterRow => ({ key: '$t0', name: '$t0', number: 8, group: 'Temporaries', value: b32(0), changed: false, ...o });

test('registerViews: hex, signed decimal and binary together; zero and changed rows; the circuit\'s own name beside', () => {
  const [pc, sp, t0, x] = registerViews([
    reg({ key: 'PC', name: 'PC', number: -1, group: 'Special', value: b32(0x00400030), changed: true, alias: 'Register #1', markable: true, componentId: 'k1' }),
    reg({ key: '$sp', name: '$sp', number: 29, group: 'Pointers', value: b32(0x7fffeff4), alias: '$29' }),
    reg({ value: b32(-6) }),
    reg({ key: 'reg0', name: 'alu › Register #2', number: -1, group: 'Other registers', value: '0000x1', changed: false }),
  ]);
  assert.deepEqual([pc.hex, pc.dec, pc.changed, pc.alias, pc.markable, pc.componentId], ['0x00400030', '4194352', false, 'Register #1', true, 'k1'],
    'PC moves every cycle: never the changed row (Hallym MIPS)');
  assert.deepEqual(pc.bin, ['0000', '0000', '0100', '0000', '0000', '0000', '0011', '0000']);
  assert.equal(sp.alias, '$29');
  assert.equal(t0.dec, '-6');
  assert.equal(t0.zero, false);
  assert.equal(registerViews([reg({})])[0].zero, true);
  assert.deepEqual([x.hex, x.dec, x.zero], ['0x0x', '', false], 'a partly defined value: x digits, no decimal');
  assert.equal(registerViews([reg({ value: null })])[0].hex, '', 'not recorded');
});

test('registerGroups: bands in order with their first and last names (Hallym MIPS\'s)', () => {
  const views = registerViews([
    reg({ key: 'PC', name: 'PC', number: -1, group: 'Special' }),
    reg({ key: '$v0', name: '$v0', number: 2, group: 'Return values' }), reg({ key: '$v1', name: '$v1', number: 3, group: 'Return values' }),
    reg({ key: '$t0', name: '$t0', group: 'Temporaries' }), reg({ key: '$t9', name: '$t9', number: 25, group: 'Temporaries' }),
  ]);
  assert.deepEqual(registerGroups(views), [
    { title: 'Special', span: 'PC', keys: ['PC'] },
    { title: 'Return values', span: '$v0–$v1', keys: ['$v0', '$v1'] },
    { title: 'Temporaries', span: '$t0–$t9', keys: ['$t0', '$t9'] },
  ]);
  const again = registerViews([reg({ value: b32(5), changed: true })]);
  assert.equal(shape(again), shape(registerViews([reg({})])), 'new values keep the rows');
  assert.notEqual(shape(registerViews([reg({ alias: '$8' })])), shape(registerViews([reg({})])), 'a new name rebuilds them');
  assert.notEqual(shape(registerViews([reg({ markedPc: true })])), shape(registerViews([reg({})])), 'a PC mark rebuilds them (its tag)');
});

test('wordChars: a word\'s bytes, lowest address first; unprintable as a dot', () => {
  assert.equal(wordChars('3d202136'), '6! =', 'factorial\'s "6! = " (little-endian)');
  assert.equal(wordChars('00000020'), ' ···');
  assert.equal(wordChars('xxxxxxxx'), '····');
  assert.equal(wordChars(null), '    ');
});

test('memoryView: section heads, lines with their labels and $sp, a zero run as one line', () => {
  const rows: MemoryRow[] = [
    { kind: 'section', section: 'data', part: 'Data Memory', addr: '0x10010000', end: '0x100fffff' },
    { kind: 'words', section: 'data', part: 'Data Memory', addr: '0x10010000', end: '0x1001000f', words: ['3d202136', '00000020', '00000000', null],
      labels: [{ addr: '0x10010000', names: ['msg'] }, { addr: '0x10010008', names: ['nums', 'first'] }] },
    { kind: 'zeros', section: 'data', part: 'Data Memory', addr: '0x10010010', end: '0x100fffff', count: 65532, labels: [{ addr: '0x10010020', names: ['buf'] }] },
    { kind: 'section', section: 'stack', part: 'Data Memory', addr: '0x7fffefe0', end: '0x7fffffff', base: '0x7fffeffc', depth: 24, peak: 56 },
    { kind: 'words', section: 'stack', part: 'Data Memory', addr: '0x7fffefe0', end: '0x7fffefef', words: ['00000006', '0040007c', '00000000', '00000000'],
      pointers: { $sp: '0x7fffefe4' } },
  ];
  const v = memoryView(rows);
  assert.equal(v.length, 5);
  const [data, line, zeros, stack, sline] = v;
  assert.deepEqual(data.type === 'section' && [data.title, data.range, data.size, data.facts], ['User data', '0x10010000 – 0x100fffff', '960 KB', '']);
  assert.ok(line.type === 'words');
  assert.deepEqual(line.cells.map((c) => [c.text, c.zero, c.none]), [['3d202136', false, false], ['00000020', false, false], ['00000000', true, false], ['', false, true]]);
  assert.deepEqual(line.ascii, ['6! =', ' ···', '····', '    ']);
  assert.deepEqual(line.tags, [{ kind: 'label', where: '+0', text: 'msg' }, { kind: 'label', where: '+8', text: 'nums first' }]);
  assert.ok(zeros.type === 'zeros');
  assert.equal(zeros.zeroText, '~ 0x100fffff · all 0 · 65,532 words');
  assert.deepEqual(zeros.tags, [{ kind: 'label', where: '0x10010020', text: 'buf' }], 'a zero run names addresses in full');
  assert.deepEqual(stack.type === 'section' && [stack.title, stack.facts], ['Stack', '$sp depth 24 B · peak 56 B']);
  assert.ok(sline.type === 'words');
  assert.deepEqual(sline.cells.map((c) => c.pointed), [false, true, false, false], '$sp points into the second word');
  assert.deepEqual(sline.tags, [{ kind: 'pointer', where: '+4', text: '$sp → +4' }]);
});

test('memoryView: the old structure (a Data Memory and a Stack) names each part', () => {
  const v = memoryView([
    { kind: 'section', section: 'data', part: 'Data Memory', addr: '0x10010000', end: '0x1001ffff' },
    { kind: 'section', section: 'stack', part: 'Stack', addr: '0x7ffff000', end: '0x7fffffff', base: '0x7fffeffc', depth: -1, peak: 0 },
  ]);
  assert.deepEqual(v.map((x) => x.type === 'section' && x.title), ['User data · Data Memory', 'Stack · Stack']);
  assert.equal(v[1].type === 'section' && v[1].facts, '$sp outside the stack · peak 0 B');
  // A stack not used yet (nothing below the top): its head names the top only.
  const unused = memoryView([{ kind: 'section', section: 'stack', part: 'Data Memory', addr: '0x80000000', end: '0x7fffffff', base: '0x80000000', depth: -1, peak: 0 }]);
  assert.deepEqual(unused[0].type === 'section' && [unused[0].range, unused[0].size], ['– 0x7fffffff', '0 B']);
});
