/* src/main/recovery-files.ts and the recovery files' part of recovery.ts
   (N-19, D-152): which recovery file is asked about, when the engine is
   asked to write one (after the window's edits: idle, at most so long,
   after so many; never for a new file; not while a crash recovery replays;
   dropped on save and close), and a file opened from its recovery file as
   the journal's base (opened from it again after an engine crash, the
   journal starting over at each write; the Supervisor's own close keeps
   it) -- against the fake engine (tests/fake-engine) as a child process. */

import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { copyFileSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, utimesSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { test } from 'node:test';

import { EngineClient } from '../../src/main/engine.ts';
import type { NewResult, OpenResult, Recovered, Snapshot } from '../../src/main/protocol.ts';
import { recoveryBeside, recoveryPathOf, RecoveryWriter, WRITER } from '../../src/main/recovery-files.ts';
import { baseOf, refOf, Supervisor, WINDOW } from '../../src/main/recovery.ts';

const FAKE = path.join(import.meta.dirname, '../fake-engine/fake-engine.ts');
const REPO = path.join(import.meta.dirname, '../../..');
const sleep = (ms: number) => new Promise((done) => setTimeout(done, ms));
// Waits until pred() holds (a loaded machine runs timers late), then for the writes under way.
async function eventually(w: RecoveryWriter, pred: () => boolean, ms = 10_000): Promise<void> {
  const t0 = Date.now();
  while (!pred() && Date.now() - t0 < ms) await sleep(20);
  await w.idle();
}

function scratch(): { dir: string; gates: string } {
  const dir = mkdtempSync(path.join(tmpdir(), 'hcs-recovery-files-'));
  const gates = path.join(dir, 'gates.circ');
  copyFileSync(path.join(REPO, 'tests/circ/gates.circ'), gates);
  return { dir, gates };
}

function fake(writer: Partial<ConstructorParameters<typeof RecoveryWriter>[1]> = {}): { engine: EngineClient; sup: Supervisor; w: RecoveryWriter } {
  let sup: Supervisor | null = null;
  const engine = new EngineClient({
    client: { client: 'test', version: '0' }, restartDelayMs: 20, helloTimeoutMs: 10_000,
    helloParams: () => ({ recoveryFiles: true, ...sup!.helloParams() }),
    launch: () => spawn(process.execPath, [FAKE], { stdio: 'pipe' }),
  });
  sup = new Supervisor(engine);
  const s = sup;
  const w = new RecoveryWriter(engine, { hasPlace: (id) => s.journal.files.get(id)?.opened.kind === 'path', settled: () => s.settled(), ...writer });
  return { engine, sup, w };
}
const win = <T>(engine: EngineClient, method: string, params: Record<string, unknown> = {}) => engine.call<T>(method, params, { tag: WINDOW });
const not = (engine: EngineClient, fileId: string, circuitId: string, x: number) =>
  win<{ id: string }>(engine, 'edit.addComponent', { fileId, circuitId, lib: 'Gates', name: 'NOT Gate', loc: [x, 500] });
const recovered = (sup: Supervisor) => new Promise<Recovered>((done) => sup.once('recovered', done));

// ---- which recovery file is asked about ------------------------------------------------------

test('recoveryBeside: a recovery file not older than the file is asked about; an older one, none, or a folder is not', () => {
  const { dir, gates } = scratch();
  try {
    assert.equal(recoveryPathOf(gates), `${gates}.hcs-recover`);
    assert.equal(recoveryBeside(gates), null);
    writeFileSync(recoveryPathOf(gates), '<project/>');
    const t = Date.now() / 1000;
    utimesSync(gates, t - 60, t - 60);
    utimesSync(recoveryPathOf(gates), t, t);
    const asked = recoveryBeside(gates)!;
    assert.equal(asked.path, recoveryPathOf(gates));
    assert.ok(Math.abs(asked.modified - t * 1000) < 1000, `when it was written: ${asked.modified}`);
    utimesSync(recoveryPathOf(gates), t - 60, t - 60);
    assert.equal(recoveryBeside(gates)?.path, recoveryPathOf(gates), 'the same time: asked (it may have been copied with the file)');
    utimesSync(recoveryPathOf(gates), t - 120, t - 120);
    assert.equal(recoveryBeside(gates), null, 'older: the file was saved again after it (by another program)');
    rmSync(recoveryPathOf(gates));
    mkdirSync(recoveryPathOf(gates));
    assert.equal(recoveryBeside(gates), null);
    assert.equal(recoveryBeside(path.join(dir, 'gone.circ')), null);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

// ---- when it is written ---------------------------------------------------------------------------

test('the writer: after the window\'s edits settle, the engine writes it beside the file -- once, what the model is', async () => {
  const { dir, gates } = scratch();
  const { engine, w } = fake({ idleMs: 150 });
  const written: string[] = [];
  w.on('written', (id, r) => written.push(`${id} ${r.written} ${r.path}`));
  try {
    await engine.start();
    const a = await win<OpenResult>(engine, 'file.open', { path: gates });
    await not(engine, a.fileId, a.main, 100);
    await not(engine, a.fileId, a.main, 200);
    assert.deepEqual(w.pending(), [a.fileId]);
    assert.equal(existsSync(recoveryPathOf(gates)), false, 'not while edits come');
    await eventually(w, () => written.length > 0);
    await sleep(200);   // and no second write
    assert.deepEqual(written, [`${a.fileId} true ${recoveryPathOf(gates)}`]);
    const text = readFileSync(recoveryPathOf(gates), 'utf8');
    const nots = (t: string) => (t.match(/<comp [^>]*name="NOT Gate"/g) ?? []).length;
    assert.equal(nots(text), nots(readFileSync(gates, 'utf8')) + 2, 'the two edits');
    assert.deepEqual(w.pending(), []);
    // The main process's own calls (a recovery's replay) are not the window's edits.
    await engine.call('edit.addComponent', { fileId: a.fileId, circuitId: a.main, lib: 'Gates', name: 'NOT Gate', loc: [300, 500] }, { tag: 'recovery' });
    assert.deepEqual(w.pending(), []);
  } finally {
    w.dispose();
    await engine.shutdown();
    rmSync(dir, { recursive: true, force: true });
  }
});

test('the writer: a new file never saved gets none, and nothing is written anywhere; saved once, it does', async () => {
  const { dir } = scratch();
  const { engine, w } = fake({ idleMs: 50 });
  try {
    await engine.start();
    const b = await win<NewResult>(engine, 'file.new');
    await not(engine, b.fileId, b.main, 100);
    assert.deepEqual(w.pending(), [], 'a new file has no place for one');
    const mine = path.join(dir, 'mine.circ');
    await win(engine, 'file.save', { fileId: b.fileId, path: mine });
    await not(engine, b.fileId, b.main, 200);
    assert.deepEqual(w.pending(), [b.fileId]);
    await eventually(w, () => existsSync(recoveryPathOf(mine)));
    assert.ok(existsSync(recoveryPathOf(mine)));
  } finally {
    w.dispose();
    await engine.shutdown();
    rmSync(dir, { recursive: true, force: true });
  }
});

test('the writer: after so many edits at once, and at most so long after the first while edits keep coming', async () => {
  const { dir, gates } = scratch();
  const { engine, w } = fake({ idleMs: 60_000, maxEdits: 3 });
  try {
    await engine.start();
    const a = await win<OpenResult>(engine, 'file.open', { path: gates });
    await not(engine, a.fileId, a.main, 100);
    await not(engine, a.fileId, a.main, 200);
    await sleep(50);
    assert.equal(existsSync(recoveryPathOf(gates)), false);
    await not(engine, a.fileId, a.main, 300);
    await eventually(w, () => existsSync(recoveryPathOf(gates)), 2_000);
    assert.ok(existsSync(recoveryPathOf(gates)), 'the third edit: written at once (the idle wait is a minute)');
  } finally {
    w.dispose();
    await engine.shutdown();
  }
  const second = fake({ idleMs: 1_000, maxWaitMs: 400 });
  try {
    await second.engine.start();
    rmSync(recoveryPathOf(gates), { force: true });
    const at: number[] = [];
    second.w.on('written', () => at.push(Date.now()));
    const a = await win<OpenResult>(second.engine, 'file.open', { path: gates });
    let last = 0;
    for (let i = 0; i < 12; i += 1) {   // an edit every 100 ms for 1.2 s: never 1 s idle
      await not(second.engine, a.fileId, a.main, 400 + 10 * i);
      last = Date.now();
      await sleep(100);
    }
    await eventually(second.w, () => at.length > 0, 3_000);
    assert.ok(at.length > 0 && at[0] < last, `written while the edits kept coming (400 ms after the first): ${at[0] - last} ms before the last`);
  } finally {
    second.w.dispose();
    await second.engine.shutdown();
    rmSync(dir, { recursive: true, force: true });
  }
});

test('the writer: saving or closing the file drops what was waiting (the engine removes the file then)', async () => {
  const { dir, gates } = scratch();
  const { engine, w } = fake({ idleMs: 150 });
  try {
    await engine.start();
    const a = await win<OpenResult>(engine, 'file.open', { path: gates });
    await not(engine, a.fileId, a.main, 100);
    await w.write(a.fileId);
    assert.ok(existsSync(recoveryPathOf(gates)));
    await not(engine, a.fileId, a.main, 200);
    await win(engine, 'file.save', { fileId: a.fileId });
    assert.deepEqual(w.pending(), []);
    assert.equal(existsSync(recoveryPathOf(gates)), false, 'saved: removed');
    await not(engine, a.fileId, a.main, 300);
    await win(engine, 'file.close', { fileId: a.fileId });
    assert.deepEqual(w.pending(), []);
    await sleep(300);
    assert.equal(existsSync(recoveryPathOf(gates)), false);
  } finally {
    w.dispose();
    await engine.shutdown();
    rmSync(dir, { recursive: true, force: true });
  }
});

test('the writer waits while a crash recovery replays; a quit removes the recovery files', async () => {
  const { dir, gates } = scratch();
  const { engine, sup, w } = fake({ idleMs: 10 });
  try {
    await engine.start();
    const a = await win<OpenResult>(engine, 'file.open', { path: gates });
    await not(engine, a.fileId, a.main, 100);
    const seen: string[] = [];
    engine.on('answer', (x) => { if (x.tag === WRITER || x.tag === 'recovery') seen.push(`${x.tag} ${x.method}`); });
    const done = recovered(sup);
    engine.kill();
    await done;
    await eventually(w, () => existsSync(recoveryPathOf(gates)));
    // The write asked for as the engine died (10 ms: before or after its end was seen) went out once the files were back.
    assert.ok(seen.indexOf(`${WRITER} file.recoverWrite`) > seen.lastIndexOf('recovery edit.addComponent'), seen.join('\n'));
    assert.ok(existsSync(recoveryPathOf(gates)));
  } finally {
    w.dispose();
    await engine.shutdown();
  }
  assert.equal(existsSync(recoveryPathOf(gates)), false, 'engine.shutdown (a quit) removes it');
  rmSync(dir, { recursive: true, force: true });
});

// ---- a file opened from its recovery file ---------------------------------------------------------

test('a file opened from its recovery file: the journal\'s base is that file; each write starts the journal over; after an engine crash it is opened from it again', async () => {
  const { dir, gates } = scratch();
  // The app's first run: an edit and its recovery file, then gone (an engine the app did not ask keeps it).
  const first = new EngineClient({ client: { client: 'test', version: '0' }, launch: () => spawn(process.execPath, [FAKE], { stdio: 'pipe' }) });
  let before: unknown;
  try {
    await first.start();
    const a = await first.call<OpenResult>('file.open', { path: gates });
    await first.call('edit.addComponent', { fileId: a.fileId, circuitId: a.main, lib: 'Gates', name: 'NOT Gate', loc: [100, 500] });
    await first.call('file.recoverWrite', { fileId: a.fileId });
  } finally {
    await first.shutdown();
  }
  const rec = recoveryPathOf(gates);
  assert.ok(existsSync(rec));
  const { engine, sup, w } = fake({ idleMs: 60_000 });
  try {
    await engine.start();
    const r = await win<OpenResult>(engine, 'file.open', { path: gates, recovery: 'recover' });
    const f = sup.journal.files.get(r.fileId)!;
    assert.deepEqual(f.opened, { kind: 'path', path: gates, readOnly: false, recover: true });
    assert.equal(baseOf(f.opened as Extract<typeof f.opened, { kind: 'path' }>), rec);
    assert.equal(f.fingerprint, sup.journal.files.get(r.fileId)!.fingerprint);
    assert.equal((await win<{ dirty: boolean }>(engine, 'file.dirty', { fileId: r.fileId })).dirty, true);
    await not(engine, r.fileId, r.main, 200);
    assert.equal(sup.journal.unsaved(r.fileId), 1);
    const fp0 = f.fingerprint;
    await w.write(r.fileId);
    assert.equal(sup.journal.unsaved(r.fileId), 0, 'the recovery file now has that edit: the journal starts over from it');
    assert.notEqual(sup.journal.files.get(r.fileId)!.fingerprint, fp0);
    await not(engine, r.fileId, r.main, 300);
    before = (await win<Snapshot>(engine, 'model.circuit', { fileId: r.fileId, circuitId: r.main })).components.map(refOf);

    const opens: unknown[] = [];
    engine.on('answer', (x) => { if (x.method === 'file.open' || x.method === 'file.close') opens.push(x.params); });
    const done = recovered(sup);
    engine.kill();
    const back = await done;
    assert.deepEqual(back.restored, [{ fileId: r.fileId, edits: 1, dirty: true }]);
    assert.deepEqual((opens[0] as { recovery?: string }).recovery, 'recover', 'opened from the recovery file again');
    const after = (await win<Snapshot>(engine, 'model.circuit', { fileId: r.fileId, circuitId: r.main })).components.map(refOf);
    assert.deepEqual(after, before);
    // Saved: the journal's base is the student's file again.
    await win(engine, 'file.save', { fileId: r.fileId });
    assert.deepEqual(sup.journal.files.get(r.fileId)!.opened, { kind: 'path', path: gates, readOnly: false });
  } finally {
    w.dispose();
    await engine.shutdown();
    rmSync(dir, { recursive: true, force: true });
  }
});

test('a file opened from its recovery file whose recovery file changed meanwhile: opened as saved, said so; the Supervisor\'s own close keeps the file', async () => {
  const { dir, gates } = scratch();
  writeFileSync(recoveryPathOf(gates), readFileSync(gates, 'utf8'));
  const { engine, sup, w } = fake({ idleMs: 60_000 });
  try {
    await engine.start();
    const r = await win<OpenResult>(engine, 'file.open', { path: gates, recovery: 'recover' });
    await not(engine, r.fileId, r.main, 200);
    writeFileSync(recoveryPathOf(gates), readFileSync(gates, 'utf8').replace('</project>', '<!-- x --></project>'));
    const opens: Record<string, unknown>[] = [];
    engine.on('answer', (x) => { if (x.method === 'file.open') opens.push(x.params as Record<string, unknown>); });
    const done = recovered(sup);
    engine.kill();
    const back = await done;
    assert.deepEqual(back.lost, [{ fileId: r.fileId, reason: 'changedOnDisk', edits: 1 }]);
    assert.equal(opens[0].recovery, undefined, 'opened as saved');
    assert.deepEqual(sup.journal.files.get(r.fileId)!.opened, { kind: 'path', path: gates, readOnly: false, recover: false });
    assert.ok(existsSync(recoveryPathOf(gates)), 'the recovery file is left: the student may open it again');
  } finally {
    w.dispose();
    await engine.shutdown();
    rmSync(dir, { recursive: true, force: true });
  }
});

test('a replay that fails closes the file with keepRecovery: its recovery file stays beside it', async () => {
  const { dir, gates } = scratch();
  let sup: Supervisor | null = null;
  const engine = new EngineClient({
    client: { client: 'test', version: '0' }, restartDelayMs: 20, helloTimeoutMs: 10_000,
    helloParams: () => ({ recoveryFiles: true, ...sup!.helloParams() }),
    launch: () => spawn(process.execPath, [FAKE], { stdio: 'pipe', env: { ...process.env, FAKE_ENGINE_FAIL_AFTER_RESTART: 'edit.addComponent' } }),
  });
  sup = new Supervisor(engine);
  const w = new RecoveryWriter(engine, { idleMs: 60_000 });
  try {
    await engine.start();
    const a = await win<OpenResult>(engine, 'file.open', { path: gates });
    await not(engine, a.fileId, a.main, 100);
    await w.write(a.fileId);
    const closes: unknown[] = [];
    engine.on('answer', (x) => { if (x.method === 'file.close') closes.push(x.params); });
    const done = recovered(sup);
    engine.kill();
    const back = await done;
    assert.deepEqual(back.lost, [{ fileId: a.fileId, reason: 'replayFailed', edits: 1 }]);
    assert.deepEqual(closes, [{ fileId: a.fileId, keepRecovery: true }]);
    assert.ok(existsSync(recoveryPathOf(gates)));
  } finally {
    w.dispose();
    await engine.shutdown();
    rmSync(dir, { recursive: true, force: true });
  }
});
