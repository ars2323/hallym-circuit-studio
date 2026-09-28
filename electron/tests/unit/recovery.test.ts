/* src/main/recovery.ts: the shadow of the model, the journal of edit
   intents, and the Supervisor that opens the files again and replays their
   edits after the engine died (N-04, D-142) -- against hand-made answers,
   and against the fake engine (tests/fake-engine) as a real child process
   killed as a crash would. */

import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { EventEmitter } from 'node:events';
import { copyFileSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { PassThrough } from 'node:stream';
import { test } from 'node:test';

import { EngineClient, EngineGone, type EngineProcess } from '../../src/main/engine.ts';
import type { EngineStatus, NewResult, OpenResult, Recovered, Snapshot } from '../../src/main/protocol.ts';
import { ID_PARAMS, Journal, journaled, refOf, sameRef, Shadow, Supervisor, WINDOW, type Ref } from '../../src/main/recovery.ts';

const FAKE = path.join(import.meta.dirname, '../fake-engine/fake-engine.ts');
const REPO = path.join(import.meta.dirname, '../../..');
const CLIENT = { client: 'test', version: '0' };

const and = (x: number, y: number, attrs: Record<string, string> = {}) => ({ id: '', lib: 'Gates', name: 'AND Gate', loc: [x, y], bounds: [0, 0, 0, 0], facing: 'east', attrs, ports: [] });
const snapshot = (circuitId: string, parts: { comps?: [string, ReturnType<typeof and>][]; wires?: [string, [number, number], [number, number]][] }): Snapshot => ({
  circuitId, name: 'main',
  components: (parts.comps ?? []).map(([id, c]) => ({ ...c, id })) as unknown as Snapshot["components"],
  wires: (parts.wires ?? []).map(([id, a, b]) => ({ id, a, b })),
  nets: [], junctions: [],
});

// ---- the shadow ---------------------------------------------------------------------------

test('refs: a component by library, name, place and attributes; a wire by its ends either way round', () => {
  const a = refOf(and(10, 20, { inputs: '3' }))!;
  assert.deepEqual(a, { kind: 'component', lib: 'Gates', name: 'AND Gate', loc: [10, 20], attrs: { inputs: '3' } });
  assert.ok(sameRef(a, refOf(and(10, 20, { inputs: '3' }))!));
  assert.ok(!sameRef(a, refOf(and(10, 20, { inputs: '2' }))!));
  assert.ok(!sameRef(a, refOf(and(10, 30, { inputs: '3' }))!));
  assert.ok(!sameRef(a, refOf({ ...and(10, 20, { inputs: '3' }), name: 'OR Gate' })!));
  const w = refOf({ id: 'w1', a: [0, 0], b: [10, 0] })!;
  assert.ok(sameRef(w, { kind: 'wire', a: [10, 0], b: [0, 0] }));
  assert.ok(!sameRef(w, { kind: 'wire', a: [0, 0], b: [20, 0] }));
  assert.ok(!sameRef(w, a));
  assert.equal(refOf('k1'), null);
});

test('the shadow: snapshots, model.changed (removed, added, changed in place), the circuits of open files', () => {
  const s = new Shadow();
  s.answer('file.open', { path: '/x.circ' }, { fileId: 'f1', circuits: [{ circuitId: 'c1', name: 'main' }, { circuitId: 'c2', name: 'alu' }] });
  assert.deepEqual(s.circuitIds('f1'), { main: 'c1', alu: 'c2' });
  assert.equal(s.loaded('f1', 'c1'), false);
  s.answer('model.circuit', { fileId: 'f1', circuitId: 'c1' }, snapshot('c1', { comps: [['k1', and(10, 10)], ['k2', and(50, 10)]], wires: [['w1', [0, 0], [10, 0]]] }));
  assert.equal(s.loaded('f1', 'c1'), true);
  assert.deepEqual(s.describe('f1', 'c1', 'k2'), refOf(and(50, 10)));
  s.notification('model.changed', { fileId: 'f1', circuitId: 'c1', removed: ['k2', 'w1'], added: [{ ...and(60, 10), id: 'k3' }, { ...and(10, 10, { inputs: '4' }), id: 'k1' }] });
  assert.equal(s.describe('f1', 'c1', 'k2'), null);
  assert.equal(s.describe('f1', 'c1', 'w1'), null);
  assert.deepEqual(s.describe('f1', 'c1', 'k1'), refOf(and(10, 10, { inputs: '4' })));
  assert.deepEqual(s.find('f1', 'c1', [refOf(and(60, 10))!]), ['k3']);
  assert.equal(s.find('f1', 'c1', [refOf(and(99, 99))!]), null);
  s.answer('model.library', { fileId: 'f1' }, [{ lib: null, tools: [{ name: 'alu2', display: 'alu2', circuitId: 'c9' }] }]);
  assert.equal(s.circuitNamed('f1', 'alu2'), 'c9');
  s.answer('file.close', { fileId: 'f1' }, {});
  assert.equal(s.describe('f1', 'c1', 'k1'), null);
});

test('the shadow: two identical parts in one place are two matches, one each', () => {
  const s = new Shadow();
  s.answer('model.circuit', { fileId: 'f1', circuitId: 'c1' }, snapshot('c1', { comps: [['k1', and(10, 10)], ['k2', and(10, 10)]] }));
  const r = refOf(and(10, 10))!;
  assert.deepEqual(s.find('f1', 'c1', [r, r])?.sort(), ['k1', 'k2']);
  assert.equal(s.find('f1', 'c1', [r, r, r]), null);
});

test('the id floor: the highest number of any id the window has seen, kept across clear()', () => {
  const s = new Shadow();
  assert.equal(s.idFloor(), 0);
  s.answer('file.new', {}, { fileId: 'f12', circuits: [{ circuitId: 'c30', name: 'main' }] });
  s.answer('model.circuit', { fileId: 'f12', circuitId: 'c30' }, snapshot('c30', { comps: [['k41', and(0, 0)]], wires: [['w77', [0, 0], [1, 0]]] }));
  assert.equal(s.idFloor(), 77);
  s.notification('model.changed', { fileId: 'f12', circuitId: 'c30', removed: [], added: [{ ...and(1, 1), id: 'k205' }] });
  assert.equal(s.idFloor(), 205);
  s.clear();
  assert.equal(s.idFloor(), 205);
  s.answer('sim.state', {}, { fileId: 'nothing-here', path: ['n900'] });   // net ids are not ours
  assert.equal(s.idFloor(), 205);
});

// ---- the journal --------------------------------------------------------------------------

test('the journal: edit intents with their parts written down as refs, in order over every file', () => {
  const s = new Shadow();
  const j = new Journal();
  s.answer('model.circuit', { fileId: 'f1', circuitId: 'c1' }, snapshot('c1', { comps: [['k1', and(10, 10)]], wires: [['w1', [0, 0], [10, 0]]] }));
  s.answer('file.new', {}, { fileId: 'f2', circuits: [{ circuitId: 'c2', name: 'main' }] });
  j.opened('f1', { kind: 'path', path: '/a.circ', readOnly: false }, { main: 'c1' }, 'sum');
  j.opened('f2', { kind: 'new' }, { main: 'c2' }, null);
  j.record('f1', 'edit.move', { fileId: 'f1', circuitId: 'c1', ids: ['k1', 'w1'], dx: 10, dy: 0 }, s);
  j.record('f2', 'edit.addComponent', { fileId: 'f2', circuitId: 'c2', lib: 'Gates', name: 'OR Gate', loc: [5, 5] }, s);
  j.record('f1', 'edit.undo', { fileId: 'f1' }, s);
  const [move, undo] = j.files.get('f1')!.entries;
  assert.deepEqual([move.seq, j.files.get('f2')!.entries[0].seq, undo.seq], [1, 2, 3]);
  assert.deepEqual(move.refs.ids, [refOf(and(10, 10)), { kind: 'wire', a: [0, 0], b: [10, 0] }]);
  assert.deepEqual(move.circuit, { id: 'c1', name: 'main' });
  assert.equal(undo.circuit, null);
  assert.equal(j.unsaved('f1'), 2);
  // Saving starts over; closing forgets.
  j.saved('f1', '/b.circ', { main: 'c1' }, 'sum2');
  assert.deepEqual([j.unsaved('f1'), j.files.get('f1')!.opened, j.files.get('f1')!.fingerprint], [0, { kind: 'path', path: '/b.circ', readOnly: false }, 'sum2']);
  j.closed('f2');
  assert.equal(j.files.has('f2'), false);
});

test('the journal: an id the window was never shown breaks the file\'s journal (its edits cannot be replayed)', () => {
  const s = new Shadow();
  const j = new Journal();
  j.opened('f1', { kind: 'new' }, { main: 'c1' }, null);
  j.record('f1', 'edit.delete', { fileId: 'f1', circuitId: 'c1', ids: ['k9'] }, s);
  assert.match(j.files.get('f1')!.broken ?? '', /k9 was never shown/);
  j.record('f1', 'edit.undo', { fileId: 'f1' }, s);
  assert.equal(j.unsaved('f1'), 0);
  j.opened('f2', { kind: 'new' }, { main: 'c2' }, null);
  j.record('f2', 'edit.delete', { fileId: 'f2', ids: ['k1'] }, s);   // ids without a circuit
  assert.match(j.files.get('f2')!.broken ?? '', /without a circuit/);
});

test('what is journaled: edit.*, mips.load (N-16) and the Cycle View\'s marks (undoable model edits); the id parameters', () => {
  assert.ok(journaled('edit.addComponent') && journaled('edit.undo') && journaled('mips.load'));
  for (const m of ['record.markPc', 'record.markRegisterFile', 'record.setRegisterMapping']) assert.ok(journaled(m), m);
  for (const m of ['sim.poke', 'sim.cycles', 'model.circuit', 'file.save', 'mips.facts', 'mips.reload', 'mips.console', 'mips.disasm',
    'record.state', 'record.pin', 'record.addRow', 'record.view', 'record.runUntil', 'record.registerMapping']) assert.ok(!journaled(m), m);
  assert.deepEqual(ID_PARAMS, { ids: 'list', id: 'one', componentId: 'one', wire: 'one' });
});

test('the journal: Mark as PC names its register as a part; the register file and its mapping by circuit and places', () => {
  const s = new Shadow();
  const j = new Journal();
  s.answer('file.open', {}, { fileId: 'f1', circuits: [{ circuitId: 'c1', name: 'main' }, { circuitId: 'c2', name: 'regfile' }] });
  s.answer('model.circuit', { fileId: 'f1', circuitId: 'c1' }, snapshot('c1', { comps: [['k7', and(300, 200, { label: 'PC' })]] }));
  j.opened('f1', { kind: 'path', path: '/a.circ', readOnly: false }, { main: 'c1', regfile: 'c2' }, 'sum');
  j.record('f1', 'record.markPc', { fileId: 'f1', circuitId: 'c1', componentId: 'k7', on: true }, s);
  j.record('f1', 'record.markRegisterFile', { fileId: 'f1', circuitId: 'c2', on: true }, s);
  j.record('f1', 'record.setRegisterMapping', { fileId: 'f1', circuitId: 'c2', map: { 1: [700, 120], 3: null } }, s);
  const [pc, rf, map] = j.files.get('f1')!.entries;
  assert.equal(j.files.get('f1')!.broken, null);
  assert.deepEqual(pc.refs.componentId, refOf(and(300, 200, { label: 'PC' })));
  assert.deepEqual([rf.circuit, rf.refs], [{ id: 'c2', name: 'regfile' }, {}]);
  assert.deepEqual([map.circuit, map.refs, map.params.map], [{ id: 'c2', name: 'regfile' }, {}, { 1: [700, 120], 3: null }]);
});

// ---- answers in the engine's order ---------------------------------------------------------

class HandProcess extends EventEmitter {
  stdin = new PassThrough();
  stdout = new PassThrough();
  stderr = new PassThrough();
  pid = 4242;
  requests: { id: number; method: string; params: Record<string, unknown> }[] = [];
  constructor() {
    super();
    let rest = '';
    this.stdin.on('data', (b: Buffer) => {
      const parts = (rest + b.toString('utf8')).split('\n');
      rest = parts.pop()!;
      for (const p of parts) {
        const m = JSON.parse(p);
        if (m.method === 'engine.hello') this.stdout.write(`${JSON.stringify({ jsonrpc: '2.0', id: m.id, result: { engine: 'hand', version: '1', logisim: '2.7.1', java: '21' } })}\n`);
        else { this.requests.push(m); this.emit('request'); }
      }
    });
  }
  kill(): boolean { setImmediate(() => this.emit('exit', null, 'SIGKILL')); return true; }
}

test('an edit\'s answer and its model.changed in one chunk: the journal sees the model before the edit', async () => {
  const proc = new HandProcess();
  const engine = new EngineClient({ launch: () => proc as unknown as EngineProcess, client: CLIENT });
  const sup = new Supervisor(engine, { fingerprint: () => 'x' });
  await engine.start();
  const next = async () => { while (proc.requests.length === 0) await new Promise((d) => proc.once('request', d)); return proc.requests.shift()!; };
  const answer = (id: number, result: unknown, ...notes: unknown[]) =>
    proc.stdout.write([{ jsonrpc: '2.0', id, result }, ...notes.map((params) => ({ jsonrpc: '2.0', method: 'model.changed', params }))].map((m) => `${JSON.stringify(m)}\n`).join(''));

  const opened = engine.call('file.new', {}, { tag: WINDOW });
  answer((await next()).id, { fileId: 'f1', circuits: [{ circuitId: 'c1', name: 'main' }], main: 'c1' });
  await opened;
  const snap = engine.call('model.circuit', { fileId: 'f1', circuitId: 'c1' }, { tag: WINDOW });
  answer((await next()).id, snapshot('c1', { comps: [['k1', and(10, 10)]] }));
  await snap;
  // setAttr: its answer and its model.changed (k1 now has 3 inputs) arrive together.
  const set = engine.call('edit.setAttr', { fileId: 'f1', circuitId: 'c1', ids: ['k1'], attr: 'inputs', value: '3' }, { tag: WINDOW });
  answer((await next()).id, { changed: true }, { fileId: 'f1', circuitId: 'c1', removed: [], added: [{ ...and(10, 10, { inputs: '3' }), id: 'k1' }] });
  await set;
  const move = engine.call('edit.move', { fileId: 'f1', circuitId: 'c1', ids: ['k1'], dx: 10, dy: 0 }, { tag: WINDOW });
  answer((await next()).id, { changed: true }, { fileId: 'f1', circuitId: 'c1', removed: ['k1'], added: [{ ...and(20, 10, { inputs: '3' }), id: 'k2' }] });
  await move;
  const [e1, e2] = sup.journal.files.get('f1')!.entries;
  assert.deepEqual((e1.refs.ids as Ref[])[0], refOf(and(10, 10)));                    // before setAttr
  assert.deepEqual((e2.refs.ids as Ref[])[0], refOf(and(10, 10, { inputs: '3' })));    // after setAttr, before move
  // A call of the main process's own (not the window's) is not journaled.
  const own = engine.call('edit.undo', { fileId: 'f1' });
  answer((await next()).id, { changed: true });
  await own;
  assert.equal(sup.journal.unsaved('f1'), 2);
  proc.stdin.end();
});

// ---- the supervisor, with the fake engine ------------------------------------------------

function fake(env: Record<string, string> = {}, opts: Partial<ConstructorParameters<typeof EngineClient>[0]> = {}): { engine: EngineClient; sup: Supervisor } {
  let sup: Supervisor | null = null;
  const engine = new EngineClient({
    client: CLIENT, restartDelayMs: 20, helloTimeoutMs: 10_000, helloParams: () => sup!.helloParams(), ...opts,
    launch: () => spawn(process.execPath, [FAKE], { stdio: 'pipe', env: { ...process.env, ...env } }),
  });
  sup = new Supervisor(engine);
  return { engine, sup };
}

const recovered = (sup: Supervisor) => new Promise<Recovered>((done) => sup.once('recovered', done));
const until = (engine: EngineClient, pred: (s: EngineStatus) => boolean) => new Promise<EngineStatus>((done) => {
  if (pred(engine.status())) { done(engine.status()); return; }
  const on = (s: EngineStatus) => { if (pred(s)) { engine.off('status', on); done(s); } };
  engine.on('status', on);
});
const win = <T>(engine: EngineClient, method: string, params: Record<string, unknown> = {}) => engine.call<T>(method, params, { tag: WINDOW });

function scratch(): { dir: string; datapath: string; gates: string } {
  const dir = mkdtempSync(path.join(tmpdir(), 'hcs-recovery-'));
  const datapath = path.join(dir, 'demo-datapath.circ');
  const gates = path.join(dir, 'gates.circ');
  copyFileSync(path.join(REPO, 'tests/circ/demo-datapath.circ'), datapath);
  copyFileSync(path.join(REPO, 'tests/circ/gates.circ'), gates);
  return { dir, datapath, gates };
}

test('recovery: files opened again under their ids, every edit replayed in the order the engine first answered, over both files', async () => {
  const { dir, datapath } = scratch();
  const { engine, sup } = fake();
  try {
    await engine.start();
    const a = await win<OpenResult>(engine, 'file.open', { path: datapath });
    const b = await win<NewResult>(engine, 'file.new');
    const main = a.circuits.find((c) => c.name === 'main')!.circuitId;
    const snap = await win<Snapshot>(engine, 'model.circuit', { fileId: a.fileId, circuitId: main });
    await win(engine, 'edit.move', { fileId: a.fileId, circuitId: main, ids: [snap.components[0].id], dx: 10, dy: 0 });
    await win(engine, 'edit.addComponent', { fileId: b.fileId, circuitId: b.main, lib: 'Gates', name: 'OR Gate', loc: [5, 5] });
    await win(engine, 'edit.undo', { fileId: a.fileId, circuitId: main });
    await win(engine, 'edit.redo', { fileId: a.fileId, circuitId: main });
    const before = await win<Snapshot>(engine, 'model.circuit', { fileId: a.fileId, circuitId: main });

    const replayed: string[] = [];
    engine.on('answer', (x) => { if (x.tag === 'recovery' && x.method.startsWith('edit.')) replayed.push(`${x.method} ${(x.params as { fileId: string }).fileId}`); });
    const states: string[] = [];
    sup.on('status', (s) => states.push(s.state));
    const done = recovered(sup);
    engine.kill();
    await until(engine, (s) => s.state === 'restarting');
    // A window call made now waits for the files to be back, then goes to the new engine.
    const waiting = sup.settled().then(() => win<{ dirty: boolean }>(engine, 'file.dirty', { fileId: a.fileId }));
    const r = await done;
    assert.equal((await waiting).dirty, true);
    assert.deepEqual(replayed, [`edit.move ${a.fileId}`, `edit.addComponent ${b.fileId}`, `edit.undo ${a.fileId}`, `edit.redo ${a.fileId}`]);
    assert.deepEqual(r.restored, [{ fileId: a.fileId, edits: 3, dirty: true }, { fileId: b.fileId, edits: 1, dirty: true }]);
    assert.deepEqual([r.lost, r.closed, r.attempt, r.generation], [[], [], 1, 2]);
    assert.equal(r.crash?.how, 'signal SIGKILL');
    // The window saw it restarting until the files were back, then ready.
    assert.equal(states.at(-1), 'ready');
    assert.ok(states.filter((s) => s === 'ready').length === 1 && states.includes('restarting'));
    // Same ids; the same model, part for part (the parts' own ids are new).
    const after = await win<Snapshot>(engine, 'model.circuit', { fileId: a.fileId, circuitId: main });
    const strip = (s: Snapshot) => s.components.map((c) => refOf(c)).concat(s.wires.map((w) => refOf(w)));
    assert.deepEqual(strip(after), strip(before));
    // New ids start above every id the window saw.
    const c = await win<NewResult>(engine, 'file.new');
    assert.ok(Number(c.fileId.slice(1)) > Number(b.fileId.slice(1)));
    assert.ok(Number(c.fileId.slice(1)) > 30);
  } finally {
    await engine.shutdown();
    rmSync(dir, { recursive: true, force: true });
  }
});

test('recovery: Mark as PC, Mark as Register File and Register Mapping are replayed; the PC register found again by what it is', async () => {
  const { dir, datapath } = scratch();
  // under another name: the fake reads the .circ itself (not the canvas fixture's fixed ids), so a new engine gives new ids
  const copy = path.join(dir, 'datapath-copy.circ');
  copyFileSync(datapath, copy);
  const { engine, sup } = fake();
  try {
    await engine.start();
    const a = await win<OpenResult>(engine, 'file.open', { path: copy });
    const main = a.circuits.find((c) => c.name === 'main')!.circuitId;
    const regfile = a.circuits.find((c) => c.name === 'regfile')!.circuitId;
    const snap = await win<Snapshot>(engine, 'model.circuit', { fileId: a.fileId, circuitId: main });
    const pc = snap.components.find((c) => c.name === 'Register' && c.attrs.label === 'PC')!;
    await win(engine, 'record.markPc', { fileId: a.fileId, circuitId: main, componentId: pc.id, on: true });
    await win(engine, 'record.markRegisterFile', { fileId: a.fileId, circuitId: regfile, on: true });
    await win(engine, 'record.setRegisterMapping', { fileId: a.fileId, circuitId: regfile, map: { 1: [700, 120] } });
    const replayed: [string, unknown][] = [];
    engine.on('answer', (x) => { if (x.tag === 'recovery' && x.method.startsWith('record.')) replayed.push([x.method, (x.params as { componentId?: string }).componentId]); });
    const done = recovered(sup);
    engine.kill();
    const r = await done;
    assert.deepEqual(r.restored, [{ fileId: a.fileId, edits: 3, dirty: true }]);
    assert.deepEqual(replayed.map(([m]) => m), ['record.markPc', 'record.markRegisterFile', 'record.setRegisterMapping']);
    const now = await win<Snapshot>(engine, 'model.circuit', { fileId: a.fileId, circuitId: main });
    const pcNow = now.components.find((c) => c.name === 'Register' && c.attrs.label === 'PC')!;
    assert.notEqual(pcNow.id, pc.id, 'the new engine\'s id');
    assert.equal(replayed[0][1], pcNow.id, 'Mark as PC sent with the id the part has now');
    const regs = await win<{ mode: string; rows: { key: string; markedPc?: boolean }[] }>(engine, 'record.registers', { fileId: a.fileId });
    assert.equal(regs.mode, 'file');
    assert.equal(regs.rows.find((x) => x.key === 'PC')?.markedPc, true);
  } finally {
    await engine.shutdown();
    rmSync(dir, { recursive: true, force: true });
  }
});

test('recovery: Tunnel Color and the Splitter editor (N-12) are journaled with their parts and replayed; the student\'s colours and names come back', async () => {
  const { dir, datapath } = scratch();
  const { engine, sup } = fake();
  try {
    await engine.start();
    const a = await win<OpenResult>(engine, 'file.open', { path: datapath });
    const main = a.circuits.find((c) => c.name === 'main')!.circuitId;
    const snap = await win<Snapshot>(engine, 'model.circuit', { fileId: a.fileId, circuitId: main });
    const clk = snap.components.find((c) => c.name === 'Tunnel' && c.attrs.label === 'clk')!;
    const sp = snap.components.find((c) => c.name === 'Splitter')!;
    const wire = snap.wires.find((w) => snap.nets.some((n) => n.width === 32 && n.wires.includes(w.id)) && w.a[1] === w.b[1])!;
    await win(engine, 'edit.tunnelColor', { fileId: a.fileId, circuitId: main, id: clk.id, color: '#e69f00' });
    await win(engine, 'edit.splitterEdit', { fileId: a.fileId, circuitId: main, id: sp.id, ranges: '31:26, 25:21, 20:16, 15:11, 10:6, 5:0', names: ['op', 'rs', 'rt', 'rd', 'sh', 'fn'] });
    await win(engine, 'edit.splitterSplit', { fileId: a.fileId, circuitId: main, wire: wire.id, at: [Math.min(wire.a[0], wire.b[0]) + 10, wire.a[1]], ranges: '5' });
    const ext = (s: Snapshot) => s.components.filter((c) => c.ext).map((c) => `${c.name}@${c.loc}:${JSON.stringify(c.ext)}`).sort();
    const before = await win<Snapshot>(engine, 'model.circuit', { fileId: a.fileId, circuitId: main });
    assert.ok(ext(before).some((e) => e.includes('"#E69F00"')));
    assert.ok(ext(before).some((e) => e.includes('"sh"')));
    const replayed: string[] = [];
    engine.on('answer', (x) => { if (x.tag === 'recovery' && x.method.startsWith('edit.')) replayed.push(x.method); });
    const done = recovered(sup);
    engine.kill();
    const r = await done;
    assert.deepEqual(replayed, ['edit.tunnelColor', 'edit.splitterEdit', 'edit.splitterSplit']);
    assert.deepEqual(r.restored, [{ fileId: a.fileId, edits: 3, dirty: true }]);
    const after = await win<Snapshot>(engine, 'model.circuit', { fileId: a.fileId, circuitId: main });
    assert.deepEqual(ext(after), ext(before));
    const strip = (s: Snapshot) => s.components.map((c) => refOf(c)).concat(s.wires.map((w) => refOf(w)));
    assert.deepEqual(strip(after), strip(before));
test('the journal: a signal group names its wire, an area memo the parts it goes around, as parts (N-15)', () => {
  const s = new Shadow();
  const j = new Journal();
  s.answer('file.open', {}, { fileId: 'f1', circuits: [{ circuitId: 'c1', name: 'main' }] });
  s.answer('model.circuit', { fileId: 'f1', circuitId: 'c1' }, snapshot('c1', { comps: [['k7', and(300, 200)]], wires: [['w3', [100, 100], [200, 100]]] }));
  j.opened('f1', { kind: 'path', path: '/a.circ', readOnly: false }, { main: 'c1' }, 'sum');
  j.record('f1', 'edit.signalGroup', { fileId: 'f1', circuitId: 'c1', wire: 'w3', group: 'control' }, s);
  j.record('f1', 'edit.areaMemo', { fileId: 'f1', circuitId: 'c1', at: [600, 600], ids: ['k7', 'w3'], text: 'IF', color: 2 }, s);
  j.record('f1', 'edit.areaMemo', { fileId: 'f1', circuitId: 'c1', at: [600, 600], delete: true }, s);
  const [g, add, del] = j.files.get('f1')!.entries;
  assert.equal(j.files.get('f1')!.broken, null);
  assert.ok(journaled('edit.signalGroup') && journaled('edit.areaMemo'));
  const wire = refOf({ id: 'w3', a: [100, 100], b: [200, 100] });
  assert.deepEqual(g.refs.wire, wire);
  assert.deepEqual(add.refs.ids, [refOf(and(300, 200)), wire]);
  assert.deepEqual(del.refs, {}, 'a memo is found by its place');
});

test('recovery: signal groups and area memos (N-15) are replayed with the wire and the parts found again; the memos and groups come back', async () => {
  const { dir, datapath } = scratch();
  const copy = path.join(dir, 'datapath-ext.circ');   // the fake reads it itself: a new engine gives new ids
  copyFileSync(datapath, copy);
  const { engine, sup } = fake();
  try {
    await engine.start();
    const a = await win<OpenResult>(engine, 'file.open', { path: copy });
    const main = a.circuits.find((c) => c.name === 'main')!.circuitId;
    const snap = await win<Snapshot>(engine, 'model.circuit', { fileId: a.fileId, circuitId: main });
    const w = snap.wires[0];
    const pc = snap.components.find((c) => c.name === 'Register' && c.attrs.label === 'PC')!;
    await win(engine, 'edit.signalGroup', { fileId: a.fileId, circuitId: main, wire: w.id, group: 'address' });
    await win(engine, 'edit.areaMemo', { fileId: a.fileId, circuitId: main, at: [900, 900], ids: [pc.id], text: 'IF', color: 2 });
    const replayed: [string, Record<string, unknown>][] = [];
    engine.on('answer', (x) => { if (x.tag === 'recovery' && x.method.startsWith('edit.')) replayed.push([x.method, x.params as Record<string, unknown>]); });
    const done = recovered(sup);
    engine.kill();
    const r = await done;
    assert.deepEqual(r.restored, [{ fileId: a.fileId, edits: 2, dirty: true }]);
    assert.deepEqual(replayed.map(([m]) => m), ['edit.signalGroup', 'edit.areaMemo']);
    const now = await win<Snapshot>(engine, 'model.circuit', { fileId: a.fileId, circuitId: main });
    const wNow = now.wires.find((x) => x.a[0] === w.a[0] && x.a[1] === w.a[1] && x.b[0] === w.b[0] && x.b[1] === w.b[1])!;
    const pcNow = now.components.find((c) => c.name === 'Register' && c.attrs.label === 'PC')!;
    assert.notEqual(wNow.id, w.id, 'the new engine\'s ids');
    assert.equal(replayed[0][1].wire, wNow.id);
    assert.deepEqual(replayed[1][1].ids, [pcNow.id]);
    assert.equal(now.memos?.[0].text, 'IF');
    assert.equal(now.groups?.[0].group, 'address');
  } finally {
    await engine.shutdown();
    rmSync(dir, { recursive: true, force: true });
  }
});

test('recovery: a file changed on disk since it was opened opens as it is now, its edits not replayed', async () => {
  const { dir, gates } = scratch();
  const { engine, sup } = fake();
  try {
    await engine.start();
    const a = await win<OpenResult>(engine, 'file.open', { path: gates });
    await win(engine, 'edit.addComponent', { fileId: a.fileId, circuitId: a.main, lib: 'Gates', name: 'OR Gate', loc: [5, 5] });
    writeFileSync(gates, `${'<?xml version="1.0"?>\n<project source="2.7.1" version="1.0"><main name="main"/><circuit name="main"></circuit></project>\n'}`);
    const done = recovered(sup);
    engine.kill();
    const r = await done;
    assert.deepEqual(r.lost, [{ fileId: a.fileId, reason: 'changedOnDisk', edits: 1 }]);
    assert.equal(sup.journal.unsaved(a.fileId), 0);
    assert.equal(sup.journal.files.get(a.fileId)!.fingerprint, sup.journal.files.get(a.fileId)!.fingerprint);
  } finally {
    await engine.shutdown();
    rmSync(dir, { recursive: true, force: true });
  }
});

test('recovery: an edit written down without its parts, and one that fails again: both files as last saved', async () => {
  const { dir, gates, datapath } = scratch();
  const { engine, sup } = fake({ FAKE_ENGINE_FAIL_AFTER_RESTART: 'edit.setAttr' });
  try {
    await engine.start();
    const a = await win<OpenResult>(engine, 'file.open', { path: gates });
    const b = await win<OpenResult>(engine, 'file.open', { path: datapath });
    // An id the shadow does not have (the window can have one from elsewhere, e.g. mips.facts):
    // the journal cannot say which part it was.
    const k = (await engine.call<Snapshot>('model.circuit', { fileId: a.fileId, circuitId: a.main })).components[0].id;
    sup.shadow.forget(a.fileId);
    await win(engine, 'edit.delete', { fileId: a.fileId, circuitId: a.main, ids: [k] });
    const added = await win<{ id: string }>(engine, 'edit.addComponent', { fileId: b.fileId, circuitId: b.main, lib: 'Gates', name: 'OR Gate', loc: [5, 5] });
    await new Promise((d) => setTimeout(d, 50));   // its model.changed
    await win(engine, 'edit.setAttr', { fileId: b.fileId, circuitId: b.main, ids: [added.id], attr: 'inputs', value: '3' });
    const done = recovered(sup);
    engine.kill();
    const r = await done;
    assert.deepEqual(r.lost, [{ fileId: a.fileId, reason: 'notRecorded', edits: 0 }, { fileId: b.fileId, reason: 'replayFailed', edits: 2 }]);
    assert.deepEqual(r.restored, []);
    // As last saved: no OR gate in b.
    const snap = await win<Snapshot>(engine, 'model.circuit', { fileId: b.fileId, circuitId: b.main });
    assert.ok(!snap.components.some((c) => c.name === 'OR Gate'));
    assert.equal((await win<{ dirty: boolean }>(engine, 'file.dirty', { fileId: b.fileId })).dirty, false);
  } finally {
    await engine.shutdown();
    rmSync(dir, { recursive: true, force: true });
  }
});

test('recovery: the engine dies again while replaying -- the next start opens the last saved versions only', async () => {
  const { dir, gates } = scratch();
  const { engine, sup } = fake({ FAKE_ENGINE_CRASH_AFTER_RESTART: 'edit.addComponent' });
  try {
    await engine.start();
    const a = await win<OpenResult>(engine, 'file.open', { path: gates });
    await win(engine, 'edit.addComponent', { fileId: a.fileId, circuitId: a.main, lib: 'Gates', name: 'OR Gate', loc: [5, 5] });
    const done = recovered(sup);
    engine.kill();
    const r = await done;
    assert.equal(r.attempt, 2);
    assert.equal(r.generation, 3);
    assert.deepEqual(r.lost, [{ fileId: a.fileId, reason: 'crashedAgain', edits: 1 }]);
    assert.equal(r.crash?.how, 'exit code 70');
    // A later crash replays again (the count starts over once a recovery finished).
    await win(engine, 'edit.delete', { fileId: a.fileId, circuitId: a.main, ids: [] });
    const again = recovered(sup);
    engine.kill();
    const r2 = await again;
    assert.equal(r2.attempt, 1);
    assert.deepEqual(r2.restored, [{ fileId: a.fileId, edits: 1, dirty: true }]);
  } finally {
    await engine.shutdown();
    rmSync(dir, { recursive: true, force: true });
  }
});

test('recovery: the restart limit (3 in 60 s) still ends it -- failed, the window\'s calls let go and refused', async () => {
  const { dir, gates } = scratch();
  const { engine, sup } = fake({ FAKE_ENGINE_CRASH_AFTER_RESTART: 'file.open' });
  let told = 0;
  sup.on('recovered', () => { told += 1; });
  try {
    await engine.start();
    await win<OpenResult>(engine, 'file.open', { path: gates });
    engine.kill();
    const s = await until(engine, (x) => x.state === 'failed');
    assert.match(s.detail ?? '', /60초 안에 4번/);
    await sup.settled();   // not held back for ever
    await assert.rejects(win(engine, 'file.new'), EngineGone);
    assert.equal(told, 0);
    assert.equal(sup.view(engine.status()).state, 'failed');
  } finally {
    await engine.shutdown();
    rmSync(dir, { recursive: true, force: true });
  }
});

test('recovery: Try Again after the limit opens the files as last saved (the replay may be what ended it)', async () => {
  const { dir, gates } = scratch();
  const { engine, sup } = fake({ FAKE_ENGINE_CRASH_AFTER_RESTART: 'edit.addComponent' }, { maxRestarts: 1 });
  try {
    await engine.start();
    const a = await win<OpenResult>(engine, 'file.open', { path: gates });
    await win(engine, 'edit.addComponent', { fileId: a.fileId, circuitId: a.main, lib: 'Gates', name: 'OR Gate', loc: [5, 5] });
    engine.kill();
    await until(engine, (s) => s.state === 'failed');   // the replay ended it again: over the limit of 1
    const done = recovered(sup);
    await engine.start();                                // Try Again
    const r = await done;
    assert.equal(r.attempt, 2);
    assert.deepEqual(r.lost, [{ fileId: a.fileId, reason: 'crashedAgain', edits: 1 }]);
    assert.equal(engine.status().state, 'ready');
  } finally {
    await engine.shutdown();
    rmSync(dir, { recursive: true, force: true });
  }
});

test('recovery: while it runs the window sees restarting and none of the replay\'s changes (quiet)', async () => {
  const { dir, gates } = scratch();
  const { engine, sup } = fake();
  try {
    await engine.start();
    const a = await win<OpenResult>(engine, 'file.open', { path: gates });
    await win(engine, 'edit.addComponent', { fileId: a.fileId, circuitId: a.main, lib: 'Gates', name: 'OR Gate', loc: [5, 5] });
    await new Promise((d) => setTimeout(d, 50));   // its own model.changed, before the crash
    const loud: string[] = [];
    engine.on('notification', (m) => { if (!sup.quiet() && m === 'model.changed') loud.push(m); });
    const seen: string[] = [];
    engine.on('status', (s) => seen.push(sup.view(s).state));
    const done = recovered(sup);
    engine.kill();
    await done;
    assert.deepEqual(loud, []);
    assert.ok(!seen.includes('ready') || seen.lastIndexOf('restarting') < seen.indexOf('ready') || seen.every((x) => x !== 'ready'));
    assert.equal(sup.view(engine.status()).state, 'ready');
    assert.equal(sup.quiet(), false);
  } finally {
    await engine.shutdown();
    rmSync(dir, { recursive: true, force: true });
  }
});

test('recovery with nothing open: the window is told all the same (no files)', async () => {
  const { engine, sup } = fake();
  try {
    await engine.start();
    const done = recovered(sup);
    engine.kill();
    const r = await done;
    assert.deepEqual([r.restored, r.lost, r.closed], [[], [], []]);
  } finally {
    await engine.shutdown();
  }
});
