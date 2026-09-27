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
  const dir = tree(['e/hcs-engine.jar']);
  try {
    const r = locateEngine({ env: { HCS_ENGINE_JAR: path.join(dir, 'e/hcs-engine.jar'), HCS_JAVA: '/opt/jdk/bin/java' }, runDir, resources: null, repoRoot: null });
    assert.ok(r.ok);
    assert.equal(r.engine.command, '/opt/jdk/bin/java');
    assert.deepEqual(r.engine.args.slice(-2), ['-jar', path.join(dir, 'e/hcs-engine.jar')]);
    const missing = locateEngine({ env: { HCS_ENGINE_JAR: path.join(dir, 'nope.jar') }, runDir, resources: null, repoRoot: null });
    assert.equal(missing.ok, false);
    if (!missing.ok) {
      assert.equal(missing.reason, '엔진 파일이 없습니다: nope.jar'); // the file tried, by its name
      assert.deepEqual(missing.looked, [path.join(dir, 'nope.jar')]);
    }
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test('next to the app: the packaged engine and runtime first, then the source tree\'s stage, then its build', () => {
  const resources = tree(['engine/hcs-engine.jar', 'engine/hcs-mips.jar', 'runtime/bin/java', 'runtime/bin/java.exe']);
  const repo = tree(['engine/build/libs/hcs-engine-sources.jar', 'engine/build/libs/hcs-engine.jar', 'lib-mips/build/libs/hcs-mips.jar']);
  const staged = tree(['engine/build/stage/hcs-engine.jar', 'engine/build/stage/hcs-mips.jar', 'engine/build/libs/hcs-engine.jar']);
  const other = tree(['engine/build/libs/an-engine-sources.jar', 'engine/build/libs/engine-0.1.jar']);
  try {
    const packaged = locateEngine({ env: {}, runDir, resources, repoRoot: repo, platform: 'linux' });
    assert.ok(packaged.ok);
    assert.equal(packaged.engine.command, path.join(resources, 'runtime/bin/java'));
    assert.deepEqual(packaged.engine.args.slice(-1), [path.join(resources, 'engine/hcs-engine.jar')]);
    // hcs-mips.jar beside it, named outright (not left to the engine's code source, D-142)
    assert.ok(packaged.engine.args.includes(`-Dhcs.bundledMips=${path.join(resources, 'engine/hcs-mips.jar')}`));
    const win = locateEngine({ env: {}, runDir, resources, repoRoot: repo, platform: 'win32' });
    assert.ok(win.ok && win.engine.command.endsWith('java.exe'));
    const stage = locateEngine({ env: {}, runDir, resources: null, repoRoot: staged });
    assert.ok(stage.ok);
    assert.deepEqual(stage.engine.args.slice(-1), [path.join(staged, 'engine/build/stage/hcs-engine.jar')]);
    assert.ok(stage.engine.args.includes(`-Dhcs.bundledMips=${path.join(staged, 'engine/build/stage/hcs-mips.jar')}`));
    const source = locateEngine({ env: {}, runDir, resources: null, repoRoot: repo });
    assert.ok(source.ok);
    assert.deepEqual(source.engine.args.slice(-1), [path.join(repo, 'engine/build/libs/hcs-engine.jar')]); // not -sources
    assert.ok(source.engine.args.includes(`-Dhcs.bundledMips=${path.join(repo, 'lib-mips/build/libs/hcs-mips.jar')}`));
    assert.equal(source.engine.command, 'java');
    const named = locateEngine({ env: {}, runDir, resources: null, repoRoot: other });
    assert.ok(named.ok && named.engine.args.at(-1) === path.join(other, 'engine/build/libs/engine-0.1.jar'));
    const none = locateEngine({ env: {}, runDir, resources: null, repoRoot: path.join(repo, 'nothing') });
    assert.equal(none.ok, false);
    if (!none.ok) assert.equal(none.reason, '엔진 파일이 없습니다: hcs-engine.jar');
  } finally {
    rmSync(staged, { recursive: true, force: true });
    rmSync(other, { recursive: true, force: true });
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

test('packaged: only the bundled runtime (not JAVA_HOME, not the PATH), with its AppCDS archive when there', () => {
  const withArchive = tree(['engine/hcs-engine.jar', 'runtime/bin/java', 'runtime/hcs-engine.jsa']);
  const without = tree(['engine/hcs-engine.jar', 'runtime/bin/java']);
  const noRuntime = tree(['engine/hcs-engine.jar']);
  const jdk = tree(['bin/java']);
  try {
    const a = locateEngine({ env: { JAVA_HOME: jdk }, runDir, resources: withArchive, repoRoot: null, platform: 'linux' });
    assert.ok(a.ok);
    assert.equal(a.engine.command, path.join(withArchive, 'runtime/bin/java'));
    assert.ok(a.engine.args.includes(`-XX:SharedArchiveFile=${path.join(withArchive, 'runtime/hcs-engine.jsa')}`));
    const b = locateEngine({ env: { JAVA_HOME: jdk }, runDir, resources: without, repoRoot: null, platform: 'linux' });
    assert.ok(b.ok && !b.engine.args.some((x) => x.startsWith('-XX:SharedArchiveFile')));
    const c = locateEngine({ env: { JAVA_HOME: jdk }, runDir, resources: noRuntime, repoRoot: null, platform: 'linux' });
    assert.equal(c.ok, false);
    if (!c.ok) {
      assert.equal(c.reason, `Java 런타임이 없습니다: ${path.join('runtime', 'bin', 'java')}`);
      assert.deepEqual(c.looked, [path.join(noRuntime, 'engine/hcs-engine.jar'), path.join(noRuntime, 'runtime/bin/java')]);
    }
    // HCS_JAVA still wins, packaged or not.
    const d = locateEngine({ env: { HCS_JAVA: '/opt/jdk/bin/java' }, runDir, resources: noRuntime, repoRoot: null, platform: 'linux' });
    assert.ok(d.ok && d.engine.command === '/opt/jdk/bin/java');
  } finally {
    for (const d of [withArchive, without, noRuntime, jdk]) rmSync(d, { recursive: true, force: true });
  }
});

test('in the source tree: JAVA_HOME, then the PATH; HCS_JAVA at a runtime of ours takes its archive', () => {
  const repo = tree(['engine/build/stage/hcs-engine.jar', 'engine/build/runtime/bin/java', 'engine/build/runtime/hcs-engine.jsa']);
  const jdk = tree(['bin/java', 'hcs-engine.jsa']);
  try {
    const home = locateEngine({ env: { JAVA_HOME: jdk }, runDir, resources: null, repoRoot: repo, platform: 'linux' });
    assert.ok(home.ok);
    assert.equal(home.engine.command, path.join(jdk, 'bin/java'));
    assert.ok(!home.engine.args.some((x) => x.startsWith('-XX:SharedArchiveFile')));  // only a runtime of ours is asked
    const bundled = path.join(repo, 'engine/build/runtime/bin/java');
    const ours = locateEngine({ env: { HCS_JAVA: bundled }, runDir, resources: null, repoRoot: repo, platform: 'linux' });
    assert.ok(ours.ok && ours.engine.args.includes(`-XX:SharedArchiveFile=${path.join(repo, 'engine/build/runtime/hcs-engine.jsa')}`));
    const plain = locateEngine({ env: { HCS_JAVA: 'java' }, runDir, resources: null, repoRoot: repo, platform: 'linux' });
    assert.ok(plain.ok && !plain.engine.args.some((x) => x.startsWith('-XX:SharedArchiveFile')));
    const onPath = locateEngine({ env: {}, runDir, resources: null, repoRoot: repo, platform: 'linux' });
    assert.ok(onPath.ok && onPath.engine.command === 'java');
  } finally {
    rmSync(repo, { recursive: true, force: true });
    rmSync(jdk, { recursive: true, force: true });
  }
});

test('the JVM\'s own warnings go to stderr, never to stdout (the JSON-RPC lines) or to a file', () => {
  const args = jvmArgs(runDir, '/x/engine.jar', null, '/r/hcs-engine.jsa');
  assert.deepEqual(args.filter((x) => x.startsWith('-Xlog')), ['-Xlog:disable', '-Xlog:all=warning:stderr']);
  assert.ok(args.indexOf('-XX:SharedArchiveFile=/r/hcs-engine.jsa') < args.indexOf('-jar'));
  assert.ok(!args.some((x) => /file=|LogFile|LogVMOutput|-Xloggc/.test(x)));
});
