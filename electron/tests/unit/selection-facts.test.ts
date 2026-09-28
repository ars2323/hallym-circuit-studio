/* The Attributes panel's facts about the selection (D-146, logic/selection-facts.ts). */

import assert from 'node:assert/strict';
import { test } from 'node:test';

import type { Component, Wire } from '../../src/main/protocol.ts';
import { MAX_NAMES, selectionFacts } from '../../src/renderer/app/logic/selection-facts.ts';

const part = (id: string, name: string, label = '', loc: [number, number] = [0, 0], facing: Component['facing'] = 'east'): Component =>
  ({ id, lib: 'Memory', name, loc, bounds: [0, 0, 10, 10], facing, attrs: label ? { label } : {}, ports: [] });
const wire = (id: string): Wire => ({ id, a: [0, 0], b: [10, 0] } as Wire);

test('nothing selected: no facts (the empty state)', () => {
  assert.equal(selectionFacts([]), null);
});

test('one part: its label and kind, its place and facing', () => {
  assert.deepEqual(selectionFacts([part('c1', 'Register', 'PC', [300, 200])]),
    { title: 'PC · Register', lines: ['Location (300, 200)', 'Facing east'] });
  assert.deepEqual(selectionFacts([part('c2', 'Adder', '', [200, 120], null)]),
    { title: 'Adder', lines: ['Location (200, 120)'] });
});

test('several: how many parts and wires, their names', () => {
  assert.deepEqual(selectionFacts([part('c1', 'Register', 'PC'), part('c2', 'Adder')]),
    { title: '2 components', lines: ['PC · Register', 'Adder'] });
  assert.deepEqual(selectionFacts([part('c1', 'Adder'), wire('w1'), wire('w2')]),
    { title: '1 component, 2 wires', lines: ['Adder'] });
  assert.deepEqual(selectionFacts([wire('w1')]), { title: '1 wire', lines: [] });
});

test('many parts: the first names and how many more', () => {
  const many = Array.from({ length: MAX_NAMES + 3 }, (_, i) => part(`c${i}`, 'AND Gate'));
  const f = selectionFacts(many)!;
  assert.equal(f.title, `${MAX_NAMES + 3} components`);
  assert.equal(f.lines.length, MAX_NAMES + 1);
  assert.equal(f.lines.at(-1), 'and 3 more');
});
