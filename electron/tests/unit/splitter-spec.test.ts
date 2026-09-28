/* The Splitter editor's model (logic/splitter.ts; v1 SplitterSpec, B-14,
   I-189; D-150): the ranges read as the engine reads them (every case in
   tests/fixtures/splitter-ranges.json, written by the engine from v1's
   parser), the original attributes, the strip's split and join, the
   presets, the words for a mistake. */

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { test } from 'node:test';

import { armLabel, armRange, errorText, fromAttrs, initialSplit, msbOnTop, parse, PRESETS, ranges, toggle, toStandardAttrs, toText, unassigned, withNames } from '../../src/renderer/app/logic/splitter.ts';

interface Case {
  text: string; width: number; msbTop: boolean;
  ok?: { width: number; arms: { bits: number[]; name: string; range: string; label: string }[]; attrs: Record<string, string>; text: string; unassigned: number[] };
  error?: string;
}
const FIXTURE = JSON.parse(readFileSync(path.join(import.meta.dirname, '../fixtures/splitter-ranges.json'), 'utf8')) as { presets: Record<string, string>; cases: Case[] };

test('every case reads as the engine reads it (v1 SplitterSpec.parse): arms, attributes, the text again, bits of no arm', () => {
  assert.ok(FIXTURE.cases.length >= 30);
  for (const c of FIXTURE.cases) {
    const r = parse(c.text, c.width, c.msbTop);
    const what = JSON.stringify([c.text, c.width, c.msbTop]);
    if (c.error !== undefined) {
      assert.equal(r.ok, false, `${what} is an error in the engine: ${c.error}`);
      continue;
    }
    assert.equal(r.ok, true, `${what} reads in the engine`);
    if (!r.ok) continue;
    const want = c.ok!;
    assert.equal(r.spec.width, want.width, what);
    assert.deepEqual(r.spec.arms.map((a) => ({ bits: a.bits, name: a.name, range: armRange(a), label: armLabel(a) })), want.arms, what);
    assert.deepEqual(toStandardAttrs(r.spec), want.attrs, what);
    assert.deepEqual(Object.keys(toStandardAttrs(r.spec)), Object.keys(want.attrs), `${what}: fanout and incoming before the bits`);
    assert.equal(toText(r.spec), want.text, what);
    assert.deepEqual(unassigned(r.spec), want.unassigned, what);
  }
});

test('the presets are v1\'s, word for word', () => {
  assert.deepEqual(Object.fromEntries(PRESETS.map((p) => [p.id, p.text])), FIXTURE.presets);
  for (const p of PRESETS) assert.equal(parse(p.text, 32, true).ok, true, p.id);
});

test('ranges: highest first, runs as a:b, singles alone', () => {
  assert.equal(ranges([31, 30, 29]), '31:29');
  assert.equal(ranges([7, 3, 2, 1, 0]), '7,3:0');
  assert.equal(ranges([5]), '5');
  assert.equal(ranges([]), '');
});

test('the strip: a line inside an arm splits it there, a line between two arms joins them, a bit of no arm does nothing', () => {
  const r = parse('7:4 hi, 3:0 lo', 8, true);
  assert.ok(r.ok);
  if (!r.ok) return;
  const split = toggle(r.spec, 6);               // between bit 6 and bit 5
  assert.equal(toText(split), '7:6 hi, 5:4, 3:0 lo');
  const joined = toggle(split, 4);               // between bit 4 and bit 3: two arms
  assert.equal(toText(joined), '7:6 hi, 5:0');
  assert.equal(toggle(r.spec, 8), r.spec, 'past the top');
  assert.equal(toggle(r.spec, 0), r.spec, 'past the bottom');
  const gap = parse('7:6, 3:0', 8, true);
  assert.ok(gap.ok);
  if (gap.ok) assert.equal(toggle(gap.spec, 6), gap.spec, 'bit 5 goes to no arm');
});

test('a splitter as the engine sends it: its bits and arm names; the editor opens with its direction', () => {
  const s = fromAttrs({ fanout: '3', incoming: '8', bit0: '0', bit1: '1', bit2: '2', bit3: '2', bit4: '2', bit5: '2', bit6: '2', bit7: '2' }, ['b0', 'b1', 'rest']);
  assert.equal(toText(s), '0 b0, 1 b1, 7:2 rest');
  assert.equal(msbOnTop(s), false);
  const t = fromAttrs({ fanout: '2', incoming: '8', bit0: '1', bit1: '1', bit2: '1', bit3: '1', bit4: '0', bit5: '0', bit6: '0', bit7: 'none' });
  assert.equal(toText(t), '6:4, 3:0');
  assert.deepEqual(unassigned(t), [7]);
  assert.equal(msbOnTop(t), true);
  assert.equal(toText(withNames(t, ['hi'])), '6:4 hi, 3:0');
  // no bitN: the default spread (as the Canvas draws it)
  assert.equal(toText(fromAttrs({ fanout: '2', incoming: '4' })), '1:0, 3:2');
});

test('a new splitter on a wire starts as v1\'s: 32 bits the R-type fields, else two halves', () => {
  assert.equal(toText(initialSplit(32)), PRESETS[0].text);
  assert.equal(toText(initialSplit(8)), '7:4, 3:0');
  assert.equal(toText(initialSplit(5)), '4:2, 1:0');
  assert.equal(toText(initialSplit(2)), '1, 0');
});

test('the words for a mistake: Korean, the bits and the text in the mono font, no particle right after a value', () => {
  const words = [
    errorText({ kind: 'empty' }), errorText({ kind: 'repeat', text: '33x1' }), errorText({ kind: 'unreadable', text: '7-4' }),
    errorText({ kind: 'outside', bit: 9, width: 8 }), errorText({ kind: 'twice', bit: 4 }), errorText({ kind: 'noArms' }), errorText({ kind: 'width' }),
  ];
  for (const w of words) {
    assert.match(w, /[가-힣]/);
    assert.doesNotMatch(w, /하면 됩니다/);
    assert.doesNotMatch(w, /[A-Za-z0-9)\]`](은|는|이|가|을|를|의|에|로|으로|와|과)(\s|$)/, w);
  }
  assert.match(errorText({ kind: 'outside', bit: 9, width: 8 }), /0부터 7까지.*`9`$/);
  const e = parse('7-4', 8, true);
  assert.equal(e.ok, false);
  if (!e.ok) assert.deepEqual(e.error, { kind: 'unreadable', text: '7-4' });
});
