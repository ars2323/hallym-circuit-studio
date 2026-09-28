/* src/main/windows.ts, src/main/circuit-files.ts (N-11, D-153): which
   window a file is in and where the engine's notifications go, what a
   window of its own starts from (once), the halves of View Side by Side
   and the place of a tab dragged out; the folder a library dialog opens in.
   And the file's circuits as file.changed tells them (logic/files.ts), the
   recovery shadow's names after a rename or a removal (recovery.ts). */

import assert from 'node:assert/strict';
import { test } from 'node:test';

import type { BrowserWindow } from 'electron';

import { dialogFolder } from '../../src/main/circuit-files.ts';
import { Shadow } from '../../src/main/recovery.ts';
import { FileWindows, halves, offset } from '../../src/main/windows.ts';
import { Files } from '../../src/renderer/app/logic/files.ts';

const win = (id: number): BrowserWindow => ({ webContents: { id }, isDestroyed: () => false } as unknown as BrowserWindow);

test('windows: a file\'s own window gets its notifications; the others and fileless ones go to the main window', () => {
  const main = win(1), own = win(2);
  const w = new FileWindows(() => main);
  assert.equal(w.route({ fileId: 'f1' }), main);
  w.adopt('f2', own, { fileId: 'f2', name: 'b.circ', path: '/b.circ', tabs: ['c1'], circuit: 'c1' });
  assert.equal(w.route({ fileId: 'f2' }), own);
  assert.equal(w.route({ fileId: 'f1' }), main);
  assert.equal(w.route({ level: 'info', message: 'x' }), main);
  assert.equal(w.route(null), main);
  assert.deepEqual(w.all(), [main, own]);
  assert.deepEqual(w.detached().map((d) => d.fileId), ['f2']);
  assert.equal(w.fileOf(own), 'f2');
  assert.equal(w.isMain(main), true);
  assert.equal(w.isMain(own), false);
  // what it starts from: once
  assert.equal(w.handoverFor(2)?.name, 'b.circ');
  assert.equal(w.handoverFor(2), null);
  // Attach Tab: the file is the main window's again
  assert.equal(w.release('f2'), own);
  assert.equal(w.route({ fileId: 'f2' }), main);
  assert.deepEqual(w.detached(), []);
});

test('a destroyed window of its own: its file goes to the main window', () => {
  const main = win(1);
  const dead = { webContents: { id: 3 }, isDestroyed: () => true } as unknown as BrowserWindow;
  const w = new FileWindows(() => main);
  w.adopt('f3', dead, { fileId: 'f3', name: 'c.circ', path: null, tabs: [], circuit: '' });
  assert.equal(w.ownerOf('f3'), main);
  assert.deepEqual(w.all(), [main]);
});

test('View Side by Side: the work area\'s halves; a tab dragged out: +60, +60, inside the area, not smaller than usable', () => {
  assert.deepEqual(halves({ x: 0, y: 0, width: 1921, height: 1032 }), { left: { x: 0, y: 0, width: 960, height: 1032 }, right: { x: 960, y: 0, width: 961, height: 1032 } });
  const area = { x: 0, y: 0, width: 1920, height: 1032 };
  const o = offset({ x: 0, y: 0, width: 1920, height: 1032 }, area);
  assert.deepEqual([o.x, o.y], [60, 60]);
  assert.ok(o.x + o.width <= area.width && o.y + o.height <= area.height);
  assert.ok(o.width >= 760 && o.height >= 480);
  const edge = offset({ x: 1500, y: 800, width: 800, height: 600 }, area);
  assert.ok(edge.x + edge.width <= 1920 && edge.y + edge.height <= 1032);
});

test('a library dialog opens in the file\'s folder (none for a file never saved)', () => {
  assert.equal(dialogFolder('/home/s/hw1/lab.circ'), '/home/s/hw1');
  assert.equal(dialogFolder(null), undefined);
});

test('file.changed: circuits added, renamed, moved and removed; a removed circuit\'s tab closes, the one on show goes left', () => {
  const files = new Files();
  const c = [{ circuitId: 'c1', name: 'main' }, { circuitId: 'c2', name: 'alu' }, { circuitId: 'c3', name: 'reg' }];
  files.add({ fileId: 'f1', name: 'a.circ', path: null, circuits: c, main: 'c1' });
  files.openCircuit('f1', 'c2');
  files.openCircuit('f1', 'c3');
  const f = files.get('f1')!;
  assert.deepEqual([f.tabs, f.circuit], [['c1', 'c2', 'c3'], 'c3']);
  // renamed, moved, a new one
  assert.deepEqual(files.structure('f1', [{ circuitId: 'c3', name: 'regs' }, { circuitId: 'c1', name: 'main' }, { circuitId: 'c2', name: 'alu' }, { circuitId: 'c4', name: 'new' }], 'c1'), []);
  assert.equal(files.circuitName(f, 'c3'), 'regs');
  assert.deepEqual(f.tabs, ['c1', 'c2', 'c3']);
  // removed: the one on show
  assert.deepEqual(files.structure('f1', [{ circuitId: 'c1', name: 'main' }, { circuitId: 'c2', name: 'alu' }, { circuitId: 'c4', name: 'new' }], 'c2'), ['c3']);
  assert.deepEqual([f.tabs, f.circuit, f.main], [['c1', 'c2'], 'c2', 'c2']);
  // every tab gone but main
  assert.deepEqual(files.structure('f1', [{ circuitId: 'c4', name: 'new' }], 'c4'), ['c1', 'c2']);
  assert.deepEqual([f.tabs, f.circuit, f.main], [['c4'], 'c4', 'c4']);
  assert.deepEqual(files.structure('f9', [], ''), []);
});

test('the recovery shadow follows file.changed: a renamed circuit\'s edits are written with its new name, a removed one is forgotten', () => {
  const s = new Shadow();
  s.answer('file.new', {}, { fileId: 'f1', circuits: [{ circuitId: 'c1', name: 'main' }] });
  s.notification('file.changed', { fileId: 'f1', circuits: [{ circuitId: 'c1', name: 'main' }, { circuitId: 'c7', name: 'alu' }], main: 'c1' });
  assert.equal(s.circuitName('f1', 'c7'), 'alu');
  assert.equal(s.idFloor(), 7);
  s.notification('file.changed', { fileId: 'f1', circuits: [{ circuitId: 'c7', name: 'alu32' }], main: 'c7' });
  assert.equal(s.circuitName('f1', 'c7'), 'alu32');
  assert.equal(s.hasCircuit('f1', 'c1'), false);
  assert.deepEqual(s.circuitIds('f1'), { alu32: 'c7' });
  s.answer('file.info', { fileId: 'f1' }, { fileId: 'f1', circuits: [{ circuitId: 'c8', name: 'x' }] });
  assert.equal(s.circuitName('f1', 'c8'), 'x');
});
