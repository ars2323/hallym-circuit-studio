/* src/renderer/app/logic/files.ts: the open files and their circuit tabs. */

import assert from 'node:assert/strict';
import { test } from 'node:test';

import { Files } from '../../src/renderer/app/logic/files.ts';

const circuits = [{ circuitId: 'c1', name: 'main' }, { circuitId: 'c2', name: 'regfile' }, { circuitId: 'c3', name: 'alu' }];
const file = (n: number, path: string | null = null) => ({ fileId: `f${n}`, name: `f${n}.circ`, path, circuits, main: 'c1' });

test('a file opens with its main circuit as its one tab, and is on show', () => {
  const files = new Files();
  const f = files.add(file(1, '/a/f1.circ'));
  assert.deepEqual([f.tabs, f.circuit, f.dirty], [['c1'], 'c1', false]);
  assert.equal(files.active()?.fileId, 'f1');
  assert.equal(files.byPath('/a/f1.circ')?.fileId, 'f1');
  assert.equal(files.byPath('/a/other.circ'), undefined);
  // A main that is not among the circuits: the first one.
  assert.equal(files.add({ ...file(2), main: 'nope' }).circuit, 'c1');
});

test('closing the file on show shows the one on its left, else the first; the last closed leaves none', () => {
  const files = new Files();
  for (const n of [1, 2, 3]) files.add(file(n));
  files.activate('f2');
  assert.equal(files.close('f2')?.fileId, 'f1');
  files.activate('f1');
  assert.equal(files.close('f1')?.fileId, 'f3');
  files.add(file(4));
  files.activate('f3');
  assert.equal(files.close('f4')?.fileId, 'f3'); // not on show: the one on show stays
  assert.equal(files.close('f3'), null);
  assert.equal(files.count(), 0);
});

test('circuit tabs: opened once, in order; closing the one on show shows its left neighbour; the last stays', () => {
  const files = new Files();
  files.add(file(1));
  files.openCircuit('f1', 'c3');
  files.openCircuit('f1', 'c2');
  files.openCircuit('f1', 'c3');
  const f = files.get('f1')!;
  assert.deepEqual([f.tabs, f.circuit], [['c1', 'c3', 'c2'], 'c3']);
  files.openCircuit('f1', 'nope');
  assert.equal(f.circuit, 'c3');
  files.closeCircuit('f1', 'c3');
  assert.deepEqual([f.tabs, f.circuit], [['c1', 'c2'], 'c1']);
  files.closeCircuit('f1', 'c1');
  files.closeCircuit('f1', 'c2');
  assert.deepEqual(f.tabs, ['c2']);
  assert.equal(files.circuitName(f, 'c2'), 'regfile');
});

test('saved: the new name and path, no unsaved changes; clear(): nothing left (a new engine)', () => {
  const files = new Files();
  files.add(file(1));
  files.setDirty('f1', true);
  assert.equal(files.get('f1')!.dirty, true);
  files.saved('f1', 'lab1.circ', '/w/lab1.circ');
  assert.deepEqual([files.get('f1')!.name, files.get('f1')!.path, files.get('f1')!.dirty], ['lab1.circ', '/w/lab1.circ', false]);
  files.setSim({ fileId: 'f1', running: true, ticking: true, cycle: 4, oscillating: false });
  assert.equal(files.get('f1')!.sim?.cycle, 4);
  files.clear();
  assert.deepEqual([files.count(), files.active()], [0, null]);
});
