/* src/main/run-folder.ts: this run's folder, the folders of earlier runs,
   and the .circ named on the command line. */

import assert from 'node:assert/strict';
import { existsSync, mkdirSync, mkdtempSync, readdirSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { test } from 'node:test';

import { circArgument, removeAfterExitScript, removeEarlierRuns, runDirName, runsDirFor } from '../../src/main/run-folder.ts';

test('the run folders: in the temp folder, or HCS_USER_DATA; one per run, named by pid and time', () => {
  assert.equal(runsDirFor({}), path.join(tmpdir(), 'HallymCircuitStudio'));
  assert.equal(runsDirFor({ HCS_USER_DATA: '/x/runs' }), '/x/runs');
  assert.equal(runDirName(123, 456), 'run-123-456');
});

test('earlier runs: removed once their process is gone; a running one and this one stay; other folders stay', () => {
  const dir = mkdtempSync(path.join(tmpdir(), 'hcs-runs-'));
  try {
    for (const n of ['run-10-1', 'run-11-2', 'run-12-3', 'notes', 'run-x-1']) mkdirSync(path.join(dir, n));
    const removed = removeEarlierRuns(dir, 12, (pid) => pid === 11);
    assert.deepEqual(removed, ['run-10-1']);
    assert.deepEqual(readdirSync(dir).sort(), ['notes', 'run-11-2', 'run-12-3', 'run-x-1']);
    assert.deepEqual(removeEarlierRuns(path.join(dir, 'missing'), 1), []);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test('the remover script: waits for the process to be gone, then removes the folder', async () => {
  const dir = mkdtempSync(path.join(tmpdir(), 'hcs-remove-'));
  mkdirSync(path.join(dir, 'Cache'));
  // A pid that is not running: it removes at once.
  const { spawnSync } = await import('node:child_process');
  const r = spawnSync(process.execPath, ['-e', removeAfterExitScript(2 ** 22 + 7, dir)], { encoding: 'utf8' });
  assert.equal(r.status, 0, r.stderr);
  for (let i = 0; i < 50 && existsSync(dir); i += 1) await new Promise((d) => setTimeout(d, 20));
  assert.equal(existsSync(dir), false);
});

test('circArgument: the first .circ that is not a switch, resolved against the start folder', () => {
  assert.equal(circArgument(['electron', 'src/main/main.ts'], '/w'), null);
  assert.equal(circArgument(['electron', 'src/main/main.ts', 'lab1.circ'], '/w'), path.resolve('/w', 'lab1.circ'));
  assert.equal(circArgument(['HallymCircuitStudio.exe', '--force-device-scale-factor=1.5', 'C:\\과제\\Lab 2.CIRC'], '/w'),
    path.resolve('/w', 'C:\\과제\\Lab 2.CIRC'));
  assert.equal(circArgument(['x', '--flag=a.circ', 'b.txt'], '/w'), null);
  assert.equal(circArgument(['x.circ'], '/w'), null); // the executable itself is never it
});
