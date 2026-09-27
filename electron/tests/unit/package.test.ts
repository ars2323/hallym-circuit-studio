/* tools/stage-engine.ts: what the packaged app gets beside it (N-04) --
   the engine's two jars and the bundled runtime, staged where
   electron-builder takes them into resources/, or a clear error naming the
   Gradle task when one is missing.  electron-builder itself is not run. */

import assert from 'node:assert/strict';
import { existsSync, lstatSync, mkdirSync, mkdtempSync, readFileSync, readlinkSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { test } from 'node:test';

import { DEFAULT_STAGE_PATHS, extraResources, stageEngine } from '../../tools/stage-engine.ts';

function tree(files: Record<string, string>): string {
  const dir = mkdtempSync(path.join(tmpdir(), 'hcs-stage-'));
  for (const [f, text] of Object.entries(files)) {
    mkdirSync(path.dirname(path.join(dir, f)), { recursive: true });
    writeFileSync(path.join(dir, f), text);
  }
  return dir;
}

const ENGINE = { 'stage/hcs-engine.jar': 'engine', 'stage/hcs-mips.jar': 'mips' };
const RUNTIME = { 'runtime/bin/java': 'java', 'runtime/bin/java.exe': 'java.exe', 'runtime/hcs-engine.jsa': 'archive', 'runtime/lib/modules': 'modules', 'runtime/legal/java.base/LICENSE': 'GPLv2 + CE' };
const paths = (dir: string, platform: NodeJS.Platform = 'linux') =>
  ({ stage: path.join(dir, 'stage'), runtime: path.join(dir, 'runtime'), out: path.join(dir, 'out'), platform });

test('stages the two jars into engine/ and the whole runtime (its AppCDS archive too) into runtime/', () => {
  const dir = tree({ ...ENGINE, ...RUNTIME, 'out/engine/stale.jar': 'old', 'out/runtime/stale': 'old' });
  try {
    stageEngine(paths(dir));
    const out = (f: string) => readFileSync(path.join(dir, 'out', f), 'utf8');
    assert.deepEqual([out('engine/hcs-engine.jar'), out('engine/hcs-mips.jar')], ['engine', 'mips']);
    assert.deepEqual([out('runtime/bin/java'), out('runtime/hcs-engine.jsa'), out('runtime/lib/modules'), out('runtime/legal/java.base/LICENSE')],
      ['java', 'archive', 'modules', 'GPLv2 + CE']);
    // What an earlier staging left is gone.
    assert.equal(existsSync(path.join(dir, 'out/engine/stale.jar')), false);
    assert.equal(existsSync(path.join(dir, 'out/runtime/stale')), false);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test('the runtime\'s relative links stay relative (legal/<module>/LICENSE -> ../java.base/LICENSE), not pointing into the build tree', { skip: process.platform === 'win32' }, () => {
  const dir = tree({ ...ENGINE, ...RUNTIME });
  try {
    mkdirSync(path.join(dir, 'runtime/legal/java.desktop'), { recursive: true });
    symlinkSync('../java.base/LICENSE', path.join(dir, 'runtime/legal/java.desktop/LICENSE'));
    stageEngine(paths(dir));
    const link = path.join(dir, 'out/runtime/legal/java.desktop/LICENSE');
    assert.ok(lstatSync(link).isSymbolicLink());
    assert.equal(readlinkSync(link), '../java.base/LICENSE');
    assert.equal(readFileSync(link, 'utf8'), 'GPLv2 + CE');
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test('a missing jar or runtime: an error naming the file and the Gradle task, nothing staged', () => {
  const cases: [Record<string, string>, NodeJS.Platform, RegExp][] = [
    [{ 'stage/hcs-mips.jar': 'm', ...RUNTIME }, 'linux', /hcs-engine\.jar is missing: \.\/gradlew :engine:stage \(on this OS\) first/],
    [{ 'stage/hcs-engine.jar': 'e', ...RUNTIME }, 'linux', /hcs-mips\.jar is missing: \.\/gradlew :engine:stage/],
    [{ ...ENGINE }, 'linux', /bin[/\\]java is missing: \.\/gradlew :engine:runtime \(on this OS\) first/],
    [{ ...ENGINE, 'runtime/bin/java': 'linux only' }, 'win32', /bin[/\\]java\.exe is missing: \.\/gradlew :engine:runtime/],
  ];
  for (const [files, platform, message] of cases) {
    const dir = tree(files);
    try {
      assert.throws(() => stageEngine(paths(dir, platform)), message);
      assert.equal(existsSync(path.join(dir, 'out')), false);
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  }
});

test('electron-builder takes the staged engine/ and runtime/ into resources/ (where engine-locate.ts looks)', () => {
  assert.deepEqual(extraResources('/x/build/package'), [
    { from: path.join('/x/build/package', 'engine'), to: 'engine' },
    { from: path.join('/x/build/package', 'runtime'), to: 'runtime' },
  ]);
  // The defaults: the Gradle build's folders, staged under electron/build/package.
  const repo = path.join(import.meta.dirname, '../../..');
  assert.deepEqual(DEFAULT_STAGE_PATHS, {
    stage: path.join(repo, 'engine/build/stage'), runtime: path.join(repo, 'engine/build/runtime'), out: path.join(repo, 'electron/build/package'),
  });
  const pkg = readFileSync(path.join(repo, 'electron/tools/package.ts'), 'utf8');
  assert.match(pkg, /extraResources: extraResources\(DEFAULT_STAGE_PATHS\.out\)/);
  assert.match(pkg, /\n {2}stageEngine\(\);\n {2}await stageApp\(\);/);
});
