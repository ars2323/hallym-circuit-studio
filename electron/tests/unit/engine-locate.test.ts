/* src/main/engine-locate.ts: which command starts the engine, and the
   lab-PC rule's JVM options. */

import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { test } from 'node:test';

import { jvmArgs, locateEngine } from '../../src/main/engine-locate.ts';

const runDir = path.join(tmpdir(), 'HallymCircuitStudio', 'run-1-2');

function tree(files: string[]): string {
  const dir = mkdtempSync(path.join(tmpdir(), 'hcs-locate-'));
  for (const f of files) {
    mkdirSync(path.dirname(path.join(dir, f)), { recursive: true });
    writeFileSync(path.join(dir, f), '');
  }
  return dir;
}

test('HCS_ENGINE_CMD: the whole command, as a JSON array', () => {
  const r = locateEngine({ env: { HCS_ENGINE_CMD: '["/usr/bin/node","/x/fake-engine.ts"]', HCS_ENGINE_JAR: '/ignored.jar' }, runDir, resources: null, repoRoot: null });
  assert.ok(r.ok);
  assert.deepEqual([r.engine.command, r.engine.args, r.engine.cwd], ['/usr/bin/node', ['/x/fake-engine.ts'], runDir]);
  const bad = locateEngine({ env: { HCS_ENGINE_CMD: '/usr/bin/node x' }, runDir, resources: null, repoRoot: null });
  assert.equal(bad.ok, false);
});

test('HCS_ENGINE_JAR: java -jar it; missing: not found, with where it looked', () => {
  const dir = tree(['e/engine.jar']);
  try {
    const r = locateEngine({ env: { HCS_ENGINE_JAR: path.join(dir, 'e/engine.jar'), HCS_JAVA: '/opt/jdk/bin/java' }, runDir, resources: null, repoRoot: null });
    assert.ok(r.ok);
    assert.equal(r.engine.command, '/opt/jdk/bin/java');
    assert.deepEqual(r.engine.args.slice(-2), ['-jar', path.join(dir, 'e/engine.jar')]);
    const missing = locateEngine({ env: { HCS_ENGINE_JAR: path.join(dir, 'nope.jar') }, runDir, resources: null, repoRoot: null });
    assert.equal(missing.ok, false);
    if (!missing.ok) {
      assert.match(missing.reason, /engine\.jar/);
      assert.deepEqual(missing.looked, [path.join(dir, 'nope.jar')]);
    }
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test('next to the app: the packaged engine and runtime first, then the source tree\'s build', () => {
  const resources = tree(['engine/engine.jar', 'runtime/bin/java', 'runtime/bin/java.exe']);
  const repo = tree(['engine/build/libs/engine-sources.jar', 'engine/build/libs/hcs-engine.jar']);
  try {
    const packaged = locateEngine({ env: {}, runDir, resources, repoRoot: repo, platform: 'linux' });
    assert.ok(packaged.ok);
    assert.equal(packaged.engine.command, path.join(resources, 'runtime/bin/java'));
    assert.deepEqual(packaged.engine.args.slice(-1), [path.join(resources, 'engine/engine.jar')]);
    const win = locateEngine({ env: {}, runDir, resources, repoRoot: repo, platform: 'win32' });
    assert.ok(win.ok && win.engine.command.endsWith('java.exe'));
    const source = locateEngine({ env: {}, runDir, resources: null, repoRoot: repo });
    assert.ok(source.ok);
    assert.deepEqual(source.engine.args.slice(-1), [path.join(repo, 'engine/build/libs/hcs-engine.jar')]); // not -sources
    assert.equal(source.engine.command, 'java');
    const none = locateEngine({ env: {}, runDir, resources: null, repoRoot: path.join(repo, 'nothing') });
    assert.equal(none.ok, false);
  } finally {
    rmSync(resources, { recursive: true, force: true });
    rmSync(repo, { recursive: true, force: true });
  }
});

test('the JVM: headless, UTF-8 on stdout, nothing outside this run\'s folder', () => {
  const args = jvmArgs(runDir, '/x/engine.jar');
  for (const a of ['-Djava.awt.headless=true', '-Dstdout.encoding=UTF-8', '-Dfile.encoding=UTF-8', '-XX:-UsePerfData']) assert.ok(args.includes(a), a);
  for (const a of args.filter((x) => x.startsWith('-XX:ErrorFile') || x.startsWith('-Djava.util.prefs') || x.startsWith('-Djava.io.tmpdir'))) {
    assert.ok(a.split('=')[1].startsWith(runDir), a);
  }
  assert.equal(args.filter((x) => x.startsWith('-XX:ErrorFile') || x.startsWith('-Djava.util.prefs') || x.startsWith('-Djava.io.tmpdir')).length, 3);
  assert.deepEqual(args.slice(-2), ['-jar', '/x/engine.jar']);
});
