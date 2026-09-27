/* src/main/engine.ts: the engine client -- against a process written by
   hand here (framing, ids, errors, notifications), and against the fake
   engine (tests/fake-engine/fake-engine.ts) as a real child process (start,
   calls, crash and restart, the restart limit, shutdown). */

import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { EventEmitter } from 'node:events';
import path from 'node:path';
import { PassThrough } from 'node:stream';
import { test } from 'node:test';

import { EngineClient, EngineError, EngineGone, MSG_CRASHED, MSG_NOT_STARTED, type EngineProcess } from '../../src/main/engine.ts';
import type { EngineStatus, NewResult, OpenResult, Snapshot } from '../../src/main/protocol.ts';

const FAKE = path.join(import.meta.dirname, '../fake-engine/fake-engine.ts');
const REPO = path.join(import.meta.dirname, '../../..');
const CLIENT = { client: 'test', version: '0' };

// ---- a process written by hand ------------------------------------------------------

class HandProcess extends EventEmitter {
  stdin = new PassThrough();
  stdout = new PassThrough();
  stderr = new PassThrough();
  pid = 4242;
  sent: { id: number; method: string; params: unknown }[] = [];
  queue: { id: number; method: string; params: unknown }[] = [];   // requests not yet taken by next()
  private rest = '';
  private readonly autoHello: boolean;
  constructor(autoHello = true) {
    super();
    this.autoHello = autoHello;
    this.stdin.on('data', (b: Buffer) => {
      const parts = (this.rest + b.toString('utf8')).split('\n');
      this.rest = parts.pop()!;
      for (const p of parts) {
        const m = JSON.parse(p);
        this.sent.push(m);
        if (m.method === 'engine.hello' && this.autoHello) this.reply(m.id, { engine: 'hand', version: '1', logisim: '2.7.1', java: '21' });
        else this.queue.push(m);
        this.emit('request');
      }
    });
  }
  reply(id: number, result: unknown): void { this.out(JSON.stringify({ jsonrpc: '2.0', id, result })); }
  out(text: string): void { this.stdout.write(`${text}\n`); }
  kill(): boolean { setImmediate(() => this.emit('exit', null, 'SIGKILL')); return true; }
  exit(code: number): void { this.emit('exit', code, null); }
}

const hand = (proc: HandProcess, opts: Partial<ConstructorParameters<typeof EngineClient>[0]> = {}) =>
  new EngineClient({ launch: () => proc as unknown as EngineProcess, client: CLIENT, restartDelayMs: 10, ...opts });

// The next request the engine got (hello aside, when it answers hello itself).
async function next(proc: HandProcess): Promise<{ id: number; method: string; params: unknown }> {
  while (proc.queue.length === 0) await new Promise((done) => proc.once('request', done));
  return proc.queue.shift()!;
}

test('hello first, with the client\'s name and version; ready after its answer', async () => {
  const proc = new HandProcess();
  const engine = hand(proc);
  const states: string[] = [];
  engine.on('status', (s) => states.push(s.state));
  const hello = await engine.start();
  assert.deepEqual(proc.sent[0], { jsonrpc: '2.0', id: 1, method: 'engine.hello', params: CLIENT });
  assert.equal(hello.engine, 'hand');
  assert.deepEqual(states, ['starting', 'ready']);
  assert.equal(engine.status().generation, 1);
});

test('ids go up by one; answers in any order settle the right calls', async () => {
  const proc = new HandProcess();
  const engine = hand(proc);
  await engine.start();
  const a = engine.call('model.circuit', { fileId: 'f1', circuitId: 'c1' });
  const ra = await next(proc);
  const b = engine.call('file.dirty', { fileId: 'f1' });
  const rb = await next(proc);
  assert.equal(rb.id, ra.id + 1);
  proc.reply(rb.id, { dirty: true });
  proc.reply(ra.id, { circuitId: 'c1' });
  assert.deepEqual(await b, { dirty: true });
  assert.deepEqual(await a, { circuitId: 'c1' });
});

test('framing: an answer in pieces, a character cut in two, two answers in one chunk, \\r\\n', async () => {
  const proc = new HandProcess();
  const engine = hand(proc);
  await engine.start();
  const a = engine.call('file.open', { path: 'x' });
  const b = engine.call('file.new');
  const [ra, rb] = [await next(proc), await next(proc)].sort((x, y) => x.id - y.id);
  const bytes = Buffer.from(`{"jsonrpc":"2.0","id":${ra.id},"result":{"name":"논리회로.circ"}}\r\n{"jsonrpc":"2.0","id":${rb.id},"result":{"fileId":"f2"}}\n`, 'utf8');
  for (let at = 0; at < bytes.length; at += 3) proc.stdout.write(bytes.subarray(at, at + 3));
  assert.deepEqual(await a, { name: '논리회로.circ' });
  assert.deepEqual(await b, { fileId: 'f2' });
});

test('errors: an error answer rejects with EngineError (method, code, data); stray lines are logged, not taken', async () => {
  const proc = new HandProcess();
  const engine = hand(proc);
  const logs: string[] = [];
  engine.on('log', (l) => logs.push(l));
  await engine.start();
  const call = engine.call('file.open', { path: 'missing.circ' });
  const r = await next(proc);
  proc.out('Picked up JAVA_TOOL_OPTIONS: -Dfile.encoding=UTF-8');
  proc.out('{"jsonrpc":"2.0","id":999,"result":{}}');
  proc.out('{"jsonrpc":"2.0","id":null,"error":{"code":-32700,"message":"parse error"}}');
  proc.out(JSON.stringify({ jsonrpc: '2.0', id: r.id, error: { code: 2, message: '파일을 읽지 못했습니다', data: { path: 'missing.circ', reason: 'ENOENT' } } }));
  await assert.rejects(call, (e: unknown) => {
    assert.ok(e instanceof EngineError);
    assert.equal(e.method, 'file.open');
    assert.equal(e.code, 2);
    assert.equal(e.message, '파일을 읽지 못했습니다');
    assert.deepEqual(e.data, { path: 'missing.circ', reason: 'ENOENT' });
    return true;
  });
  assert.equal(logs.length, 3, logs.join('\n'));
  assert.match(logs[0], /not JSON/);
  assert.match(logs[1], /answer to no call: id 999/);
  assert.match(logs[2], /error without id/);
});

test('notifications go out as events, in order, and settle no call', async () => {
  const proc = new HandProcess();
  const engine = hand(proc);
  const seen: [string, unknown][] = [];
  engine.on('notification', (m, p) => seen.push([m, p]));
  await engine.start();
  const call = engine.call('sim.cycles', { fileId: 'f1', n: 2 });
  const r = await next(proc);
  proc.out('{"jsonrpc":"2.0","method":"sim.state","params":{"fileId":"f1","cycle":1}}');
  proc.out('{"jsonrpc":"2.0","method":"sim.values","params":{"fileId":"f1","circuitId":"c1","nets":{"n1":"0x1E"}}}');
  proc.reply(r.id, {});
  assert.deepEqual(await call, {});
  assert.deepEqual(seen, [
    ['sim.state', { fileId: 'f1', cycle: 1 }],
    ['sim.values', { fileId: 'f1', circuitId: 'c1', nets: { n1: '0x1E' } }],
  ]);
});

test('stderr is kept in memory (the last lines), never as an answer', async () => {
  const proc = new HandProcess();
  const engine = hand(proc, { stderrLines: 2 });
  await engine.start();
  proc.stderr.write('one\ntwo\nthree\n');
  await new Promise((r) => setImmediate(r));
  assert.deepEqual(engine.log(), ['two', 'three']);
});

test('calls made while it starts wait for it', async () => {
  const proc = new HandProcess(false);
  const engine = hand(proc);
  const started = engine.start();
  const call = engine.call('file.new');
  const hello = await next(proc);
  assert.equal(hello.method, 'engine.hello');
  proc.reply(hello.id, { engine: 'hand', version: '1', logisim: '2.7.1', java: '21' });
  await started;
  const r = await next(proc);
  assert.equal(r.method, 'file.new');
  proc.reply(r.id, { fileId: 'f1' });
  assert.deepEqual(await call, { fileId: 'f1' });
});

test('no answer to hello in time: failed, with the reason; calls reject at once', async () => {
  const proc = new HandProcess(false);
  const engine = hand(proc, { helloTimeoutMs: 100 });
  await assert.rejects(engine.start(), EngineGone);
  const s = engine.status();
  assert.equal(s.state, 'failed');
  assert.equal(s.error, MSG_NOT_STARTED);
  assert.match(s.detail ?? '', /engine.hello/);
  await assert.rejects(engine.call('file.new'), EngineGone);
});

test('a launch that throws (no engine found): failed, the reason in the detail', async () => {
  const engine = new EngineClient({ launch: () => { throw new Error('엔진 파일(engine.jar)을 찾지 못했습니다\n  /x/engine.jar'); }, client: CLIENT });
  await assert.rejects(engine.start(), /엔진을 시작하지 못했습니다/);
  assert.equal(engine.status().state, 'failed');
  assert.match(engine.status().detail ?? '', /engine\.jar/);
});

// ---- the fake engine, a real child process ------------------------------------------------

function fake(env: Record<string, string> = {}, opts: Partial<ConstructorParameters<typeof EngineClient>[0]> = {}): EngineClient {
  return new EngineClient({
    client: CLIENT, restartDelayMs: 20, helloTimeoutMs: 10_000, ...opts,
    launch: () => spawn(process.execPath, [FAKE], { stdio: 'pipe', env: { ...process.env, ...env } }),
  });
}

const until = (engine: EngineClient, pred: (s: EngineStatus) => boolean) => new Promise<EngineStatus>((done) => {
  if (pred(engine.status())) { done(engine.status()); return; }
  const on = (s: EngineStatus) => { if (pred(s)) { engine.off('status', on); done(s); } };
  engine.on('status', on);
});

test('the fake engine: hello, file.new, model.circuit, file.open of a real .circ, a missing file', async () => {
  const engine = fake();
  try {
    const hello = await engine.start();
    assert.equal(hello.logisim, '2.7.1');
    const n = await engine.call<NewResult>('file.new');
    assert.deepEqual(n.circuits.map((c) => c.name), ['main']);
    const empty = await engine.call<Snapshot>('model.circuit', { fileId: n.fileId, circuitId: n.main });
    assert.deepEqual([empty.components.length, empty.wires.length], [0, 0]);
    const o = await engine.call<OpenResult>('file.open', { path: path.join(REPO, 'tests/circ/demo-datapath.circ') });
    assert.equal(o.name, 'demo-datapath'); // Logisim's project name; the window shows the file's own name
    assert.deepEqual(o.circuits.map((c) => c.name), ['main', 'regfile', 'alu']);
    const s = await engine.call<Snapshot>('model.circuit', { fileId: o.fileId, circuitId: o.main });
    assert.ok(s.components.some((c) => c.name === 'Tunnel' && c.attrs.label === 'RegWrite'));
    await assert.rejects(engine.call('file.open', { path: path.join(REPO, 'no-such.circ') }), (e: unknown) => e instanceof EngineError && e.code === 2);
    await assert.rejects(engine.call('no.such.method'), (e: unknown) => e instanceof EngineError && e.code === -32601);
  } finally {
    await engine.shutdown();
  }
});

test('the fake engine speaking in 5-byte pieces with noise first: every answer and notification whole', async () => {
  const engine = fake({ FAKE_ENGINE_MODE: 'noise,split' });
  const notes: [string, unknown][] = [];
  const logs: string[] = [];
  engine.on('notification', (m, p) => notes.push([m, p]));
  engine.on('log', (l) => logs.push(l));
  try {
    await engine.start();
    const n = await engine.call<NewResult>('file.new');
    await engine.call('sim.cycles', { fileId: n.fileId, n: 3 });
    await new Promise((r) => setTimeout(r, 100));
    assert.deepEqual(notes[0], ['engine.log', { level: 'info', message: '가짜 엔진이 시작했습니다' }]);
    assert.equal((notes.at(-1)![1] as { cycle: number }).cycle, 3);
    assert.ok(logs.some((l) => l.includes('Picked up JAVA_TOOL_OPTIONS')));
  } finally {
    await engine.shutdown();
  }
});

test('a crash: the call in flight rejects, the engine starts again (a new generation), and answers', async () => {
  const engine = fake({ FAKE_ENGINE_CRASH_ON: 'file.dirty' });
  const states: string[] = [];
  engine.on('status', (s) => states.push(s.state));
  try {
    await engine.start();
    const first = engine.pid;
    const n = await engine.call<NewResult>('file.new');
    await assert.rejects(engine.call('file.dirty', { fileId: n.fileId }), (e: unknown) => e instanceof EngineGone && e.message === MSG_CRASHED);
    const s = await until(engine, (x) => x.state === 'ready' && x.generation === 2);
    assert.equal(s.generation, 2);
    assert.deepEqual(states, ['starting', 'ready', 'restarting', 'restarting', 'ready']);
    assert.notEqual(engine.pid, first);
    // The new engine has none of the old one's files.
    await assert.rejects(engine.call('model.circuit', { fileId: n.fileId, circuitId: n.main }), (e: unknown) => e instanceof EngineError && e.code === 1);
    assert.ok((await engine.call<NewResult>('file.new')).fileId);
  } finally {
    await engine.shutdown();
  }
});

test('a call made right after a crash waits for the new engine', async () => {
  const engine = fake();
  try {
    await engine.start();
    engine.kill();
    await until(engine, (s) => s.state === 'restarting');
    const n = await engine.call<NewResult>('file.new');
    assert.ok(n.fileId);
    assert.equal(engine.status().generation, 2);
  } finally {
    await engine.shutdown();
  }
});

test('crashing again and again: after maxRestarts in the window, it stays failed', async () => {
  const engine = fake({ FAKE_ENGINE_CRASH_ON: 'file.new' }, { maxRestarts: 2, restartWindowMs: 60_000 });
  try {
    await engine.start();
    for (let i = 0; i < 3; i += 1) {
      await assert.rejects(engine.call('file.new'), EngineGone);
      await until(engine, (s) => s.state === 'ready' || s.state === 'failed');
    }
    const s = engine.status();
    assert.equal(s.state, 'failed');
    assert.equal(s.error, MSG_CRASHED);
    assert.match(s.detail ?? '', /60초 안에 3번/);
    await assert.rejects(engine.call('file.new'), EngineGone);
    // start() tries again from scratch.
    await engine.start();
    assert.equal(engine.status().state, 'ready');
  } finally {
    await engine.shutdown();
  }
});

test('an engine that exits at start: failed, its stderr in the detail', async () => {
  const engine = fake({ FAKE_ENGINE_MODE: 'exit-at-start' });
  await assert.rejects(engine.start(), EngineGone);
  const s = engine.status();
  assert.equal(s.state, 'failed');
  assert.match(s.detail ?? '', /exit code 3/);
  assert.match(s.detail ?? '', /exiting at start/);
});

test('a command that does not exist: failed, not thrown out of the process', async () => {
  const engine = new EngineClient({ client: CLIENT, launch: () => spawn(path.join(REPO, 'no-such-java'), ['-jar', 'x.jar'], { stdio: 'pipe' }) });
  await assert.rejects(engine.start(), EngineGone);
  assert.equal(engine.status().state, 'failed');
  assert.match(engine.status().detail ?? '', /ENOENT/);
});

test('shutdown: engine.shutdown, the process ends, stopped; a crash after it is not restarted', async () => {
  const engine = fake();
  await engine.start();
  const pid = engine.pid!;
  await engine.shutdown();
  assert.equal(engine.status().state, 'stopped');
  assert.throws(() => process.kill(pid, 0));
  await assert.rejects(engine.call('file.new'), EngineGone);
});

test('shutdown of an engine that does not end: killed after the timeout', async () => {
  const proc = new HandProcess();
  proc.kill = () => { setImmediate(() => proc.emit('exit', null, 'SIGKILL')); return true; };
  const engine = hand(proc, { shutdownTimeoutMs: 100 });
  await engine.start();
  const t0 = Date.now();
  await engine.shutdown();
  assert.ok(Date.now() - t0 >= 90);
  assert.equal(engine.status().state, 'stopped');
  assert.ok(proc.sent.some((m) => m.method === 'engine.shutdown'));
});
