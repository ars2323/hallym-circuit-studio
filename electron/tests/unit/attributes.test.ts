/* The Attributes panel's and Quick Attributes' logic (N-10, D-157;
   logic/attributes.ts): which table, the head, the editors, the words
   when a value is refused, the font and colour editors, Quick Attributes'
   buttons, hint line and place (v1 QuickBar.placement, S-04). */

import assert from 'node:assert/strict';
import { test } from 'node:test';

import type { AttrRow, AttrTable } from '../../src/main/protocol.ts';
import { badValueSentence, circuitNameProblem, colorField, colorValue, editorOf, fontParts, fontValue, heading, hintLine, placement, QUICK_MAX, quickButtons, type Rect, requestFor, requestKey, withHeld } from '../../src/renderer/app/logic/attributes.ts';

const row = (attr: string, over: Partial<AttrRow> = {}): AttrRow =>
  ({ attr, display: attr, value: '1', text: '1', type: 'text', readOnly: false, mixed: false, ...over });

test('requestFor: a part held and the Text tool ask for the tool\'s table; the other tools for the selection\'s', () => {
  assert.deepEqual(requestFor('f1', 'c1', 'Place', { lib: 'Gates', name: 'AND Gate' }), { kind: 'tool', fileId: 'f1', lib: 'Gates', name: 'AND Gate' });
  assert.deepEqual(requestFor('f1', 'c1', 'Text', null), { kind: 'tool', fileId: 'f1', lib: 'Base', name: 'Text Tool' });
  for (const t of ['Edit', 'Poke', 'Wire']) assert.deepEqual(requestFor('f1', 'c1', t, null), { kind: 'selection', fileId: 'f1', circuitId: 'c1' });
  assert.deepEqual(requestFor('f1', 'c1', 'Place', null), { kind: 'selection', fileId: 'f1', circuitId: 'c1' });
  assert.equal(requestKey(null), '');
  assert.notEqual(requestKey(requestFor('f1', 'c1', 'Edit', null)), requestKey(requestFor('f1', 'c2', 'Edit', null)));
  assert.notEqual(requestKey(requestFor('f1', 'c1', 'Text', null)), requestKey(requestFor('f1', 'c1', 'Edit', null)));
});

test('heading: the original title as a name and a badge', () => {
  const t = (title: string): AttrTable => ({ target: 'selection', title, editable: true, rows: [] });
  assert.deepEqual(heading(t('Selection: AND Gate × 2')), { name: 'AND Gate × 2', badge: 'Selection' });
  assert.deepEqual(heading(t('Circuit: main')), { name: 'main', badge: 'Circuit' });
  assert.deepEqual(heading(t('Tool: Pin')), { name: 'Pin', badge: 'Tool' });
  assert.deepEqual(heading(t('something else')), { name: 'something else', badge: '' });
});

test('editorOf: the editor for each kind; read-only rows and files that cannot change show text; contents always opens the hex editor', () => {
  assert.equal(editorOf(row('width', { type: 'option' }), true), 'select');
  assert.equal(editorOf(row('value', { type: 'number' }), true), 'number');
  assert.equal(editorOf(row('labelfont', { type: 'font' }), true), 'font');
  assert.equal(editorOf(row('color', { type: 'color' }), true), 'color');
  assert.equal(editorOf(row('label'), true), 'field');
  assert.equal(editorOf(row('contents', { type: 'contents' }), true), 'contents');
  assert.equal(editorOf(row('contents', { type: 'contents' }), false), 'contents');
  assert.equal(editorOf(row('width', { type: 'option', readOnly: true }), true), 'text');
  assert.equal(editorOf(row('label'), false), 'text');
});

test('badValueSentence: Korean, the attribute named by its English name with a noun after it, what to do', () => {
  assert.equal(badValueSentence(row('inputs', { display: 'Number Of Inputs', type: 'number', min: 2, max: 32 }), '99'),
    '이 값은 Number Of Inputs 속성에 넣을 수 없습니다. 넣을 수 있는 수: 2–32.');
  assert.equal(badValueSentence(row('value', { display: 'Value', type: 'number', radix: 16 }), 'zz'),
    '이 값은 Value 속성에 넣을 수 없습니다. 16진수(0x1F), 10진수(31)로 적습니다.');
  assert.equal(badValueSentence(row('x', { display: 'X', type: 'number' }), ''), '빈 값은 X 속성에 넣을 수 없습니다. 숫자로 적습니다.');
  assert.equal(badValueSentence(row('label', { display: 'Label' }), 'a'), '이 값은 Label 속성에 넣을 수 없습니다.');
  assert.equal(badValueSentence(undefined, 'a'), '이 값은 이 속성에 넣을 수 없습니다.');
  assert.match(badValueSentence(row('c', { display: 'Color', type: 'color' }), 'red'), /#rrggbb/);
  assert.match(badValueSentence(row('f', { display: 'Font', type: 'font' }), 'x'), /글꼴/);
});

test('circuitNameProblem: an empty name and another circuit\'s name are refused (as Add Circuit refuses them)', () => {
  assert.equal(circuitNameProblem('alu2', ['main', 'alu']), null);
  assert.equal(circuitNameProblem('  ', ['main']), '회로 이름이 비어 있습니다. 이름을 적으세요.');
  assert.equal(circuitNameProblem(' alu ', ['main', 'alu']), '같은 이름의 회로가 이미 있습니다. 다른 이름을 적으세요.');
});

test('fonts: the .circ text in parts and back; colours: the field\'s #rrggbb and the alpha kept', () => {
  assert.deepEqual(fontParts('SansSerif plain 12'), { family: 'SansSerif', style: 'plain', size: 12 });
  assert.deepEqual(fontParts('Monospaced bolditalic 16'), { family: 'Monospaced', style: 'bolditalic', size: 16 });
  assert.deepEqual(fontParts('Serif  BOLD 9'), { family: 'Serif', style: 'bold', size: 9 });
  assert.deepEqual(fontParts(null), { family: 'SansSerif', style: 'plain', size: 12 });
  assert.equal(fontValue({ family: 'Serif', style: 'italic', size: 14.4 }), 'Serif italic 14');
  assert.equal(colorField('#FF8000'), '#ff8000');
  assert.equal(colorField('#11223344'), '#112233');
  assert.equal(colorField(null), '#000000');
  assert.equal(colorValue('#aabbcc', '#11223344'), '#aabbcc44');
  assert.equal(colorValue('#aabbcc', '#112233'), '#aabbcc');
});

const table = (quick: AttrTable['quick'], rows: AttrRow[], editable = true): AttrTable => ({ target: 'selection', title: 'Selection: X', editable, rows, quick });

test('quickButtons: the kind\'s quick attributes the table has and can change, at most five, lists with their choices', () => {
  const rows = [
    row('inputs', { display: 'Number Of Inputs', type: 'option', value: '2', text: '2', options: [{ value: '2', display: '2' }, { value: '3', display: '3' }] }),
    row('width', { display: 'Data Bits', type: 'option', value: '8', text: '8', options: [{ value: '8', display: '8' }] }),
    row('size', { display: 'Gate Size', readOnly: true }),
    row('label', { display: 'Label', value: '', text: '' }),
  ];
  const q = { attrs: ['inputs', 'width', 'size', 'missing', 'label'], hints: [], rotate: true, label: true, count: 1 };
  const b = quickButtons(table(q, rows));
  assert.deepEqual(b.map((x) => x.attr), ['inputs', 'width', 'label']);
  assert.deepEqual(b[0].options?.map((o) => o.checked), [true, false]);
  assert.equal(b[2].text, '(none)');
  assert.equal(b[2].label, true);
  assert.equal(quickButtons(table({ ...q, count: 2 }, rows))[2].label, false);   // several parts: a field under the bar
  assert.deepEqual(quickButtons(table(q, rows, false)), []);                      // a circuit that cannot change
  assert.deepEqual(quickButtons(table(undefined, rows)), []);
  const many = Array.from({ length: 8 }, (_, i) => row(`a${i}`));
  assert.equal(quickButtons(table({ ...q, attrs: many.map((r) => r.attr) }, many)).length, QUICK_MAX);
});

test('hintLine: the original\'s number keys, then R and F2 when they apply (v1 QuickBar.hintText)', () => {
  assert.equal(hintLine({ attrs: [], hints: [{ keys: '0–9', attr: 'inputs', display: 'Number Of Inputs' }, { keys: 'Alt+0–9', attr: 'width', display: 'Data Bits' }], rotate: true, label: true, count: 1 }),
    '0–9: Number Of Inputs  ·  Alt+0–9: Data Bits  ·  R: Rotate  ·  F2: Label');
  assert.equal(hintLine({ attrs: [], hints: [], rotate: false, label: false, count: 1 }), '');
});

test('placement (v1 QuickBar.placement): above left first; then the order above-right, below, right, left; further away; the least covered', () => {
  const target: Rect = { x: 300, y: 300, w: 60, h: 40 };
  const bar = { w: 200, h: 30 };
  const view: Rect = { x: 0, y: 0, w: 1000, h: 800 };
  assert.deepEqual(placement(target, bar, [], [], view), { x: 300, y: 264, w: 200, h: 30 });
  // a part above: right-aligned above is covered too (same row); below-left is free
  const above: Rect = { x: 250, y: 250, w: 400, h: 40 };
  assert.deepEqual(placement(target, bar, [above], [], view), { x: 300, y: 346, w: 200, h: 30 });
  // at the top of the view: pushed back into it would cover the target, so below
  assert.deepEqual(placement({ x: 300, y: 10, w: 60, h: 40 }, bar, [], [], view), { x: 300, y: 56, w: 200, h: 30 });
  // everything near covered by parts: the one covering least; a wire (soft) counts a thousand times less than a part
  const wall: Rect = { x: 0, y: 0, w: 1000, h: 800 };
  const r = placement(target, bar, [], [wall], view);
  assert.deepEqual(r, { x: 300, y: 264, w: 200, h: 30 });
  const hard: Rect = { x: 280, y: 200, w: 300, h: 100 };
  const soft: Rect = { x: 280, y: 340, w: 300, h: 40 };
  // above covered by a part, below by a wire: the right is free, before anything covered
  assert.deepEqual(placement(target, bar, [hard], [soft], view), { x: 366, y: 300, w: 200, h: 30 });
  // nothing free: covering a part (hard) weighs a thousand times a wire (soft) -- a little of a part loses to much wire
  const speck: Rect = { x: 300, y: 264, w: 10, h: 10 };
  const wireAround: Rect[] = [{ x: 0, y: 0, w: 1000, h: 250 }, { x: 0, y: 0, w: 1000, h: 250 }, { x: 0, y: 300, w: 1000, h: 500 }];
  assert.deepEqual(placement(target, bar, [speck], wireAround, view), { x: 300, y: 346, w: 200, h: 30 });
  assert.deepEqual(placement(target, bar, [], [speck, ...wireAround], view), { x: 300, y: 264, w: 200, h: 30 });
  // no free place near: further away, the first free one (below, 20 px further than the wire)
  const r2 = placement(target, bar, [hard], [soft, { x: 360, y: 0, w: 640, h: 800 }, { x: 0, y: 0, w: 300, h: 800 }], view);
  assert.ok(r2.y > 340 && !(r2.y + r2.h > 250 && r2.y < 300), JSON.stringify(r2));
});

test('withHeld: a part held with values of its own shows them over the tool\'s; requestFor carries them, requestKey tells them apart', () => {
  const row = (attr: string, value: string, options?: { value: string; display: string }[]): AttrRow =>
    ({ attr, display: attr, value, text: options?.find((o) => o.value === value)?.display ?? value, type: options ? 'option' : 'string', readOnly: false, mixed: false, ...(options ? { options } : {}) } as AttrRow);
  const t = { target: 'tool', title: 'Tool: AND Gate', lib: 'Gates', name: 'AND Gate', editable: true,
    rows: [row('inputs', '5', [{ value: '3', display: '3' }, { value: '5', display: '5' }]), row('facing', 'east', [{ value: 'east', display: 'East' }, { value: 'west', display: 'West' }])] } as unknown as AttrTable;
  const h = withHeld(t, { inputs: '3', facing: 'west' });
  assert.deepEqual(h.rows.map((r) => [r.value, r.text]), [['3', '3'], ['west', 'West']]);
  assert.equal(t.rows[0].value, '5');   // the tool's own table untouched
  const req = requestFor('f', 'c', 'Place', { lib: 'Gates', name: 'AND Gate', attrs: { inputs: '3' } });
  assert.deepEqual(req, { kind: 'tool', fileId: 'f', lib: 'Gates', name: 'AND Gate', attrs: { inputs: '3' } });
  assert.notEqual(requestKey(req), requestKey(requestFor('f', 'c', 'Place', { lib: 'Gates', name: 'AND Gate' })));
});

