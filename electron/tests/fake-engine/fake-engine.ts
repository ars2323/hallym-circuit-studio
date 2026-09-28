/* A stand-in for the Java engine (docs/engine-api.md), for the unit and e2e
   tests until the real one (engine/, N-03) is there -- and after, for the
   window's tests that should not wait for a JVM.  Plain Node, no imports
   from src/: it speaks the protocol on its own, so the client is tested
   against something it did not write.

     node tests/fake-engine/fake-engine.ts

   engine.hello, engine.shutdown, file.new, file.open (reads the .circ's
   circuits, components and wires with a few regular expressions -- enough
   for a snapshot, nothing like Logisim's loader), file.save (the bytes it
   opened, or an empty circuit), file.close, file.dirty, model.circuit,
   model.library (this file's circuits first, as the engine lists them),
   edit.addComponent/addWire/move/delete/setAttr/undo/redo (the parts as
   plain records: a move gives a part a new id, as Logisim's new objects
   do; undo brings the parts back under new ids; model.changed after the
   answer), sim.reset/cycles/run/enable/state (a cycle count and the
   clock, told back as sim.state), diag.list and
   diag.changed (D-143: the real engine's words for the circuits in
   tests/fixtures/messages.json -- written by tools/diag-fixture.ts --
   matched by file name; the list after cycles once the file has run the
   fixture's cycles, the first one again at Reset; every other file has no
   messages), trace.origin
   (nothing to follow), mips.* (N-16: tests/fake-engine/fake-mips.ts -- the
   real engine's answers for a few executable images, tests/fixtures/programs.json).  The same shapes as
   the real engine's (docs/engine-api.md, engine/ D-134): Logisim's project
   name (Untitled, a file's name without .circ), alreadyOpen, messages,
   needsMipsJar; and a restarted engine's engine.hello idFloor and
   file.new/open restore (docs/engine-api.md 7, D-142).  Anything else: -32601.

   FAKE_ENGINE_MODE (comma-separated) for the tests of the client:
     silent-hello   never answers engine.hello
     exit-at-start  writes a line to stderr and exits (code 3)
     noise          a line that is not JSON and an engine.log notification first
     split          writes every message in small pieces (a Hangul name cut inside a character)
     oscillate      sim.cycles turns the simulation off (oscillation), with an engine.log warning
     needs-mips     file.save says the saved .circ needs hcs-mips.jar beside it
   FAKE_ENGINE_CRASH_ON=<method>  exits (code 70) on that call, unanswered
   FAKE_ENGINE_OPEN_MESSAGE=<text> file.open reports it as a loader message
   A restarted engine (engine.hello with an idFloor), for the tests of recovery:
   FAKE_ENGINE_FAIL_AFTER_RESTART=<method>   answers that call with an error (-32603)
   FAKE_ENGINE_CRASH_AFTER_RESTART=<method>  exits (code 70) on that call

   A .circ with a fixture of the same name in tests/fixtures/circuits/
   (ref-mips, demo-datapath: written by the real engine,
   ./gradlew :engine:canvasFixtures) is answered from it instead: its
   circuits' snapshots for model.circuit, and on sim.watch, sim.cycles and
   sim.reset the values the real engine sent for that very view -- the
   circuit, or a subcircuit instance watched with a path (keyed
   "circuit/instance"), with bodies --
   so the Canvas's tests and screenshots draw real circuits, the same
   pixels every time. */

import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';

import * as mips from './fake-mips.ts';

type Params = Record<string, unknown>;
interface Comp { id: string; lib: string; name: string; loc: [number, number]; attrs: Record<string, string> }
interface Wire { id: string; a: [number, number]; b: [number, number] }
interface Circuit { circuitId: string; name: string; comps: Comp[]; wires: Wire[] }
interface Step { circuitId: string; comps: Comp[]; wires: Wire[] }   // a circuit's parts before an edit
interface File {
  fileId: string; name: string; path: string | null; bytes: Buffer | null; circuits: Circuit[]; main: string; libs: string[];
  cycle: number; ticking: boolean; hz: number; on: boolean; dirty: boolean; undo: Step[]; redo: Step[]; diag: Diag | null; ran: boolean;
  mips: mips.MipsState;     // mips.* (fake-mips.ts, N-16)
  // a canvas fixture (tests/fixtures/circuits): its circuits under this file's circuit ids, the watched circuit
  fixture?: Fixture; fixtureIds?: Map<string, string>; watched?: { circuitId: string; watchKey: string; root: string; path: string[] };
}

// Messages (diag.*): the real engine's, for a few circuits (tests/fixtures/messages.json).
interface Place { name: string; loc: [number, number] }
interface FixtureMessage {
  code: string; kind: string; severity: string; text: { ko: string; en: string }; near?: string;
  location: { circuit: string; root: string; path: Place[]; components: Place[]; wires: { a: [number, number]; b: [number, number] }[]; at: [number, number] | null; cycle?: number };
}
interface Diag { static: FixtureMessage[]; afterCycles?: FixtureMessage[]; cycles?: number }
const DIAG: Record<string, Diag> = (() => {
  try { return JSON.parse(readFileSync(path.join(import.meta.dirname, '../fixtures/messages.json'), 'utf8')) as Record<string, Diag>; } catch { return {}; }
})();
interface Frame { nets: Record<string, string>; bodies: Record<string, unknown> }
interface Fixture { main: string; circuits: { circuitId: string; name: string; components: (Comp & { subcircuit?: string })[]; wires: Circuit['wires']; nets: unknown[]; junctions: unknown[] }[]; watch: Record<string, Frame[]> }

const FIXTURES = path.join(import.meta.dirname, '..', 'fixtures', 'circuits');

const modes = new Set((process.env.FAKE_ENGINE_MODE ?? '').split(',').filter(Boolean));
const crashOn = process.env.FAKE_ENGINE_CRASH_ON ?? '';
const failAfterRestart = process.env.FAKE_ENGINE_FAIL_AFTER_RESTART ?? '';
const crashAfterRestart = process.env.FAKE_ENGINE_CRASH_AFTER_RESTART ?? '';
let restarted = false;
const files = new Map<string, File>();
let nextFile = 1;
let nextCircuit = 1;
let nextComp = 1;
let nextWire = 1;

class Failure extends Error {
  code: number;
  data: unknown;
  constructor(code: number, message: string, data?: unknown) {
    super(message);
    this.code = code;
    this.data = data;
  }
}

function write(msg: unknown): void {
  const line = `${JSON.stringify(msg)}\n`;
  if (!modes.has('split')) { process.stdout.write(line); return; }
  const bytes = Buffer.from(line, 'utf8');
  for (let at = 0; at < bytes.length; at += 5) process.stdout.write(bytes.subarray(at, at + 5));
}
const notify = (method: string, params: unknown) => write({ jsonrpc: '2.0', method, params });

const point = (s: string): [number, number] => {
  const m = /\((-?\d+),\s*(-?\d+)\)/.exec(s);
  return m ? [Number(m[1]), Number(m[2])] : [0, 0];
};

// The .circ's circuits, as far as a snapshot needs them.
function readCirc(text: string): { circuits: Circuit[]; main: string; libs: string[] } {
  const libs = new Map<string, string>();
  for (const m of text.matchAll(/<lib desc="#([^"]+)" name="([^"]+)"/g)) libs.set(m[2], m[1]);
  const circuits: Circuit[] = [];
  for (const m of text.matchAll(/<circuit name="([^"]*)">([\s\S]*?)<\/circuit>/g)) {
    const c: Circuit = { circuitId: `c${nextCircuit++}`, name: m[1], comps: [], wires: [] };
    for (const w of m[2].matchAll(/<wire from="([^"]+)" to="([^"]+)"\s*\/>/g)) c.wires.push({ id: `w${nextWire++}`, a: point(w[1]), b: point(w[2]) });
    for (const k of m[2].matchAll(/<comp ([^>]*?)(\/>|>([\s\S]*?)<\/comp>)/g)) {
      const attr = (n: string) => new RegExp(`${n}="([^"]*)"`).exec(k[1])?.[1] ?? '';
      const attrs: Record<string, string> = {};
      for (const a of (k[3] ?? '').matchAll(/<a name="([^"]+)" val="([^"]*)"/g)) attrs[a[1]] = a[2];
      const lib = attr('lib');
      c.comps.push({ id: `k${nextComp++}`, lib: lib === '' ? 'circuit' : libs.get(lib) ?? lib, name: attr('name'), loc: point(attr('loc')), attrs });
    }
    circuits.push(c);
  }
  const main = /<main name="([^"]*)"/.exec(text)?.[1] ?? circuits[0]?.name ?? 'main';
  return { circuits, main, libs: [...libs.values()] };
}

const EMPTY = `<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<project source="2.7.1" version="1.0">
  <main name="main"/>
  <circuit name="main">
    <a name="circuit" val="main"/>
  </circuit>
</project>
`;

function fileOf(p: Params): File {
  const f = files.get(String(p.fileId));
  if (!f) throw new Failure(1, `no such file id: ${String(p.fileId)}`, { kind: 'file', id: String(p.fileId) });
  return f;
}
function circuitOf(p: Params): Circuit {
  const c = fileOf(p).circuits.find((x) => x.circuitId === p.circuitId);
  if (!c) throw new Failure(1, `no such circuit id: ${String(p.circuitId)}`, { kind: 'circuit', id: String(p.circuitId) });
  return c;
}
// A fixture message with this fake engine's ids (found by circuit name, part name and place, wire ends).
function diagMessage(f: File, m: FixtureMessage, i: number): unknown {
  const byName = (n: string) => f.circuits.find((c) => c.name === n);
  const c = byName(m.location.circuit);
  const root = byName(m.location.root) ?? c;
  const same = (a: [number, number], b: [number, number]) => a[0] === b[0] && a[1] === b[1];
  const comp = (in_: Circuit | undefined, x: Place) => in_?.comps.find((k) => k.name === x.name && same(k.loc, x.loc))?.id ?? '';
  const pathIds: string[] = [];
  let above = root;
  for (const x of m.location.path) {
    const k = above?.comps.find((y) => y.name === x.name && same(y.loc, x.loc));
    pathIds.push(k?.id ?? '');
    above = k ? byName(k.name) : undefined;
  }
  return {
    id: `d${i + 1}`, code: m.code, kind: m.kind, severity: m.severity, text: m.text, ...(m.near ? { near: m.near } : {}),
    location: {
      circuitId: c?.circuitId ?? '', root: root?.circuitId ?? '', path: pathIds,
      components: m.location.components.map((x) => comp(c, x)),
      wires: m.location.wires.map((w) => c?.wires.find((y) => (same(y.a, w.a) && same(y.b, w.b)) || (same(y.a, w.b) && same(y.b, w.a)))?.id ?? ''),
      nets: [], at: m.location.at, ...(typeof m.location.cycle === 'number' ? { cycle: m.location.cycle } : {}),
    },
  };
}
const diagList = (f: File) => {
  const list = f.diag ? (f.ran && f.diag.afterCycles ? f.diag.afterCycles : f.diag.static) : [];
  return list.map((m, i) => diagMessage(f, m, i));
};
const diagChanged = (f: File) => setImmediate(() => notify('diag.changed', { fileId: f.fileId, messages: diagList(f) }));

const refs = (f: File) => f.circuits.map((c) => ({ circuitId: c.circuitId, name: c.name }));
const mainId = (f: File) => f.circuits.find((c) => c.name === f.main)?.circuitId ?? f.circuits[0]?.circuitId ?? '';
const simState = (f: File) => ({ fileId: f.fileId, running: f.on, ticking: f.ticking, cycle: f.cycle, oscillating: !f.on, hz: f.hz });
const libRefs = (f: File) => (f.libs.length ? f.libs : BUILTIN).map((lib) => ({ lib, display: lib === 'I/O' ? 'Input/Output' : lib, kind: 'builtin' }));
const BUILTIN = ['Wiring', 'Gates', 'Plexers', 'Arithmetic', 'Memory', 'I/O', 'Base'];

const LIBRARY = [
  { lib: 'Wiring', tools: ['Splitter', 'Pin', 'Probe', 'Tunnel', 'Pull Resistor', 'Clock', 'Constant'] },
  { lib: 'Gates', tools: ['NOT Gate', 'Buffer', 'AND Gate', 'OR Gate', 'NAND Gate', 'NOR Gate', 'XOR Gate', 'XNOR Gate'] },
  { lib: 'Plexers', tools: ['Multiplexer', 'Demultiplexer', 'Decoder', 'Priority Encoder', 'Bit Selector'] },
  { lib: 'Arithmetic', tools: ['Adder', 'Subtractor', 'Multiplier', 'Divider', 'Negator', 'Comparator', 'Shifter'] },
  { lib: 'Memory', tools: ['D Flip-Flop', 'Register', 'Counter', 'RAM', 'ROM'] },
  { lib: 'I/O', tools: ['Button', 'LED', 'Hex Digit Display'] },
];
const stem = (name: string) => name.replace(/\.circ$/i, '');

const methods: Record<string, (p: Params) => unknown> = {
  'engine.hello': () => ({ engine: 'fake-engine', version: '0', logisim: '2.7.1', java: 'none (fake engine, Node)', api: '0' }),
  'engine.shutdown': () => { setImmediate(() => process.exit(0)); return {}; },
  'file.new': (p) => {
    const c: Circuit = { circuitId: `c${nextCircuit++}`, name: 'main', comps: [], wires: [] };
    const f: File = { fileId: fileIdFor(p), name: 'Untitled', path: null, bytes: null, circuits: [c], main: 'main', libs: [], cycle: 0, ticking: false, hz: 1, on: true, dirty: false, undo: [], redo: [], diag: null, ran: false, mips: mips.newState() };
    adopt(f, p);
    files.set(f.fileId, f);
    return { fileId: f.fileId, name: f.name, circuits: refs(f), main: mainId(f), libraries: libRefs(f) };
  },
  'file.open': (p) => {
    const file = String(p.path ?? '');
    const open = [...files.values()].find((x) => x.path === file);
    if (open) return { fileId: open.fileId, name: open.name, circuits: refs(open), main: mainId(open), libraries: libRefs(open), messages: [], alreadyOpen: true };
    let bytes: Buffer;
    try { bytes = readFileSync(file); } catch (e) {
      // The real engine's words and reasons (engine/ Files.open, docs/engine-api.md 2): English, for developers.
      if ((e as NodeJS.ErrnoException).code === 'ENOENT') throw new Failure(2, `no such file: ${file}`, { path: file, reason: 'notFound' });
      throw new Failure(2, `cannot read: ${file}`, { path: file, reason: 'unreadable' });
    }
    const text = bytes.toString('utf8');
    if (!text.includes('<project')) throw new Failure(2, `The file does not appear to be a Logisim project file: ${file}`, { path: file, reason: 'loadFailed' });
    const fileId = fileIdFor(p);
    const r = readCirc(text);
    const f: File = { fileId, name: stem(path.basename(file)), path: file, bytes, circuits: r.circuits, main: r.main, libs: r.libs, cycle: 0, ticking: false, hz: 1, on: true, dirty: false, undo: [], redo: [], diag: DIAG[path.basename(file)] ?? null, ran: false, mips: mips.newState() };
    const fx = path.join(FIXTURES, `${stem(path.basename(file))}.json`);
    if (existsSync(fx)) {
      f.fixture = JSON.parse(readFileSync(fx, 'utf8')) as Fixture;
      // the fixture's parts, copied: this fake's edits change them (and model.circuit shows the edits)
      f.circuits = f.fixture.circuits.map((c) => ({ circuitId: c.circuitId, name: c.name, comps: structuredClone(c.components), wires: structuredClone(c.wires) }));
      f.main = f.fixture.circuits.find((c) => c.circuitId === f.fixture!.main)?.name ?? f.main;
    }
    adopt(f, p);
    if (f.fixture) f.fixtureIds = new Map(f.circuits.map((c, i) => [c.circuitId, f.fixture!.circuits[i].circuitId]));
    files.set(f.fileId, f);
    const messages = process.env.FAKE_ENGINE_OPEN_MESSAGE ? [process.env.FAKE_ENGINE_OPEN_MESSAGE] : [];
    return { fileId: f.fileId, name: f.name, circuits: refs(f), main: mainId(f), libraries: libRefs(f), messages };
  },
  'file.save': (p) => {
    const f = fileOf(p);
    const target = typeof p.path === 'string' ? p.path : f.path;
    if (!target) throw new Failure(-32602, 'path is required for a file that was never saved');
    const bytes = f.bytes ?? Buffer.from(EMPTY, 'utf8');
    try { writeFileSync(target, bytes); } catch (e) {
      throw new Failure(2, `cannot write ${target}`, { path: target, reason: 'writeFailed' });
    }
    f.path = target;
    f.name = stem(path.basename(target));
    f.dirty = false;
    return { path: target, bytes: bytes.length, needsMipsJar: modes.has('needs-mips') };
  },
  'file.close': (p) => { mips.close(fileOf(p)); files.delete(String(p.fileId)); return {}; },
  'file.dirty': (p) => ({ dirty: fileOf(p).dirty }),
  'model.circuit': (p) => {
    const f = fileOf(p);
    if (f.fixture) {
      const snap = f.fixture.circuits.find((x) => x.circuitId === f.fixtureIds!.get(String(p.circuitId)));
      const c = f.circuits.find((x) => x.circuitId === p.circuitId);
      if (!snap || !c) throw new Failure(1, `no such circuit id: ${String(p.circuitId)}`, { kind: 'circuit', id: String(p.circuitId) });
      // the engine's parts as recorded, the fake's own edits on top (the nets only while there are none);
      // the fixture's circuit ids as this file's (a recovered file keeps the old engine's)
      const edited = f.undo.length > 0 || f.redo.length > 0;
      const comps = c.comps.map((k) => {
        const x = k as Comp & { ports?: unknown; subcircuit?: string };
        if (x.ports === undefined) return compJson(k);
        return x.subcircuit ? { ...x, subcircuit: currentId(f, x.subcircuit) } : x;
      });
      return { circuitId: c.circuitId, name: c.name, components: comps, wires: c.wires, nets: edited ? [] : snap.nets, junctions: edited ? [] : snap.junctions };
    }
    const c = circuitOf(p);
    return { circuitId: c.circuitId, name: c.name, components: c.comps.map(compJson), wires: c.wires, nets: [], junctions: [] };
  },
  'model.library': (p) => {
    const f = fileOf(p);
    return [
      { lib: null, display: f.name, tools: f.circuits.map((c) => ({ name: c.name, display: c.name, circuitId: c.circuitId })) },
      ...LIBRARY.map((g) => ({ lib: g.lib, display: g.lib === 'I/O' ? 'Input/Output' : g.lib, tools: g.tools.map((name) => ({ name, display: name })) })),
    ];
  },
  'edit.addComponent': (p) => {
    const c = circuitOf(p);
    const loc = p.loc as [number, number];
    if (!Array.isArray(loc) || typeof p.name !== 'string') throw new Failure(-32602, 'loc and name are required');
    const k: Comp = { id: `k${nextComp++}`, lib: (p.lib as string | null | undefined) ?? 'circuit', name: p.name, loc: [loc[0], loc[1]], attrs: { ...(p.attrs as Record<string, string> ?? {}) } };
    return edit(p, c, () => { c.comps.push(k); return { removed: [], added: [k] }; }, { id: k.id });
  },
  'edit.addWire': (p) => {
    const c = circuitOf(p);
    const pts = p.points as [number, number][];
    const added: Wire[] = [];
    for (let i = 1; i < pts.length; i += 1) {
      if (pts[i][0] === pts[i - 1][0] && pts[i][1] === pts[i - 1][1]) continue;
      added.push({ id: `w${nextWire++}`, a: [...pts[i - 1]] as [number, number], b: [...pts[i]] as [number, number] });
    }
    if (added.length === 0) return { changed: false, outcome: 'empty' };
    return edit(p, c, () => { c.wires.push(...added); return { removed: [], added }; });
  },
  'edit.move': (p) => {
    const c = circuitOf(p);
    const ids = partsOf(c, p.ids);
    const dx = Number(p.dx);
    const dy = Number(p.dy);
    return edit(p, c, () => {
      const added: (Comp | Wire)[] = [];
      c.comps = c.comps.map((k) => {
        if (!ids.has(k.id)) return k;
        const moved = { ...k, id: `k${nextComp++}`, loc: [k.loc[0] + dx, k.loc[1] + dy] as [number, number] };
        added.push(moved);
        return moved;
      });
      c.wires = c.wires.map((w) => {
        if (!ids.has(w.id)) return w;
        const moved = { id: `w${nextWire++}`, a: [w.a[0] + dx, w.a[1] + dy] as [number, number], b: [w.b[0] + dx, w.b[1] + dy] as [number, number] };
        added.push(moved);
        return moved;
      });
      return { removed: [...ids], added };
    }, { outcome: 'moved' });
  },
  'edit.delete': (p) => {
    const c = circuitOf(p);
    const ids = partsOf(c, p.ids);
    return edit(p, c, () => {
      c.comps = c.comps.filter((k) => !ids.has(k.id));
      c.wires = c.wires.filter((w) => !ids.has(w.id));
      return { removed: [...ids], added: [] };
    });
  },
  'edit.setAttr': (p) => {
    const c = circuitOf(p);
    const ids = partsOf(c, p.ids);
    return edit(p, c, () => {
      const added = c.comps.filter((k) => ids.has(k.id));
      for (const k of added) k.attrs = { ...k.attrs, [String(p.attr)]: String(p.value) };
      return { removed: [], added };
    });
  },
  'edit.undo': (p) => undoRedo(fileOf(p), 'undo'),
  'edit.redo': (p) => undoRedo(fileOf(p), 'redo'),
  'sim.reset': (p) => {
    const f = fileOf(p);
    f.cycle = 0; f.ticking = false; f.on = true;
    sendFrames(f, 0, 0);
    setImmediate(() => notify('sim.state', simState(f)));
    mips.reset(f, notify);
    if (f.ran) { f.ran = false; if (f.diag?.afterCycles) diagChanged(f); }
    return {};
  },
  'sim.cycles': (p) => {
    const f = fileOf(p);
    if (!f.on) throw new Failure(4, 'the simulation stopped because the circuit oscillates', { reason: 'oscillating' });
    const from = f.cycle;
    f.cycle += Number(p.n ?? 1);
    if (!f.ran && f.diag?.afterCycles && f.cycle >= (f.diag.cycles ?? 1)) {
      f.ran = true;
      if (f.diag.afterCycles.some((m) => m.code === 'OSCILLATION')) f.on = false; // the real engine turns the simulation off
      diagChanged(f);
    }
    sendFrames(f, from + 1, f.cycle);
    if (modes.has('oscillate')) {
      f.on = false;
      setImmediate(() => notify('engine.log', { level: 'warn', message: 'cycles stopped: the simulation is off (oscillation)' }));
    }
    setImmediate(() => notify('sim.state', simState(f)));
    mips.cycles(f, notify);
    return {};
  },
  'sim.run': (p) => {
    const f = fileOf(p);
    f.ticking = Boolean(p.on);
    if (typeof p.hz === 'number') f.hz = p.hz;
    setImmediate(() => notify('sim.state', simState(f)));
    return {};
  },
  'sim.enable': (p) => { const f = fileOf(p); f.on = Boolean(p.on); setImmediate(() => notify('sim.state', simState(f))); return {}; },
  'sim.state': (p) => simState(fileOf(p)),
  'sim.watch': (p) => {
    const f = fileOf(p);
    if (!f.fixture) { circuitOf(p); return {}; }
    // the circuit at the end of the instance path; its values are the ones the real engine sent for
    // that very view (the fixture's watch data is keyed "root" or "root/instance…": an instance's
    // values are its own, not those of the circuit opened on its own)
    const root = f.fixtureIds!.get(String(p.circuitId)) ?? '';
    let circuitId = root;
    const pathIds = Array.isArray(p.path) ? p.path.map(String) : [];
    for (const id of pathIds) {
      const inst = f.fixture.circuits.find((c) => c.circuitId === circuitId)?.components.find((k) => k.id === id);
      if (!inst?.subcircuit) throw new Failure(1, `no such instance path: ${pathIds.join('/')}`, { kind: 'instance path', id: pathIds.join('/') });
      circuitId = inst.subcircuit;
    }
    f.watched = { circuitId: currentId(f, circuitId), watchKey: [root, ...pathIds].join('/'), root: String(p.circuitId), path: pathIds };
    sendFrames(f, 0, f.cycle);   // every net as it is now: the frames up to this cycle
    return {};
  },
  'diag.list': (p) => { const f = fileOf(p); return { fileId: f.fileId, messages: diagList(f) }; },
  // ---- mips.* (fake-mips.ts, N-16)
  'mips.load': (p) => {
    const f = fileOf(p);
    const r = mips.load(f, p, notify);
    if ((r as { loaded?: boolean }).loaded) setImmediate(() => notify('sim.state', simState(f)));
    return r;
  },
  'mips.facts': (p) => mips.facts(fileOf(p)),
  'mips.console': (p) => mips.consoleOf(fileOf(p)),
  'mips.disasm': (p) => mips.disasm(fileOf(p), p),
  'mips.reload': (p) => ({ fileId: fileOf(p).fileId, results: [] }),
  'trace.origin': (p) => {
    circuitOf(p);
    return { found: false, text: { ko: '이 선의 값은 정해져 있어 따라갈 E·X 값이 없습니다.', en: "This wire's value is defined, so there is no E/X to trace." }, chain: [] };
  },
};

// ---- ids and edits -----------------------------------------------------------------

// A restarted engine's ids start above the window's (engine.hello idFloor).
function floor(n: number): void {
  nextFile = Math.max(nextFile, n + 1);
  nextCircuit = Math.max(nextCircuit, n + 1);
  nextComp = Math.max(nextComp, n + 1);
  nextWire = Math.max(nextWire, n + 1);
}

// file.new/open `restore`: the old engine's file id, and its circuit ids by name.
function fileIdFor(p: Params): string {
  const r = p.restore as { fileId?: unknown } | undefined;
  if (r === undefined) return `f${nextFile++}`;
  if (typeof r !== 'object' || r === null || typeof r.fileId !== 'string' || !/^f\d+$/.test(r.fileId)) throw new Failure(-32602, 'restore.fileId must be "f" and a number');
  if (files.has(r.fileId)) throw new Failure(-32602, `restore.fileId is in use: ${r.fileId}`);
  nextFile = Math.max(nextFile, Number(r.fileId.slice(1)) + 1);   // as the real engine: never given again
  return r.fileId;
}
function adopt(f: File, p: Params): void {
  const names = (p.restore as { circuits?: Record<string, string> } | undefined)?.circuits ?? {};
  for (const c of f.circuits) if (typeof names[c.name] === 'string' && /^c\d+$/.test(names[c.name])) c.circuitId = names[c.name];
}

const compJson = (k: Comp) => ({
  id: k.id, lib: k.lib, name: k.name, loc: k.loc, bounds: [k.loc[0] - 30, k.loc[1] - 15, 30, 30],
  facing: (k.attrs.facing as 'east' | undefined) ?? 'east', attrs: k.attrs, ports: [],
});
const partJson = (x: Comp | Wire) => ('a' in x ? x : compJson(x));

function partsOf(c: Circuit, ids: unknown): Set<string> {
  if (!Array.isArray(ids)) throw new Failure(-32602, 'ids must be an array');
  for (const id of ids) {
    if (!c.comps.some((k) => k.id === id) && !c.wires.some((w) => w.id === id)) throw new Failure(1, `no such component id: ${String(id)}`, { kind: 'component', id: String(id) });
  }
  return new Set(ids as string[]);
}

const copyParts = (c: Circuit): Step => ({ circuitId: c.circuitId, comps: structuredClone(c.comps), wires: structuredClone(c.wires) });

// One edit: its undo step, the change, model.changed after the answer.
function edit(p: Params, c: Circuit, change: () => { removed: string[]; added: (Comp | Wire)[] }, result: Record<string, unknown> = {}): unknown {
  const f = fileOf(p);
  const before = copyParts(c);
  const { removed, added } = change();
  f.undo.push(before);
  f.redo = [];
  f.dirty = true;
  const params = { fileId: f.fileId, circuitId: c.circuitId, removed, added: added.map(partJson), nets: [], junctions: [], dirty: true };
  setImmediate(() => notify('model.changed', params));
  return { changed: true, ...result };
}

function undoRedo(f: File, which: 'undo' | 'redo'): unknown {
  const from = which === 'undo' ? f.undo : f.redo;
  const to = which === 'undo' ? f.redo : f.undo;
  const step = from.pop();
  if (!step) return { changed: false };
  const c = f.circuits.find((x) => x.circuitId === step.circuitId)!;
  to.push(copyParts(c));
  const removed = [...c.comps.map((k) => k.id), ...c.wires.map((w) => w.id)];
  // What comes back comes back under new ids (as the real engine's).
  c.comps = step.comps.map((k) => ({ ...k, id: `k${nextComp++}` }));
  c.wires = step.wires.map((w) => ({ ...w, id: `w${nextWire++}` }));
  f.dirty = true;
  const params = { fileId: f.fileId, circuitId: c.circuitId, removed, added: [...c.comps.map(compJson), ...c.wires], nets: [], junctions: [], dirty: true };
  setImmediate(() => notify('model.changed', params));
  return { changed: true };
}

// A fixture circuit's id as this file's.
function currentId(f: File, fixtureId: string): string {
  for (const [cur, fx] of f.fixtureIds ?? []) if (fx === fixtureId) return cur;
  return fixtureId;
}

// The fixture's values for frames from … to (merged): frame 0 is every net, then one frame a cycle.
function sendFrames(f: File, from: number, to: number): void {
  const w = f.watched;
  if (!f.fixture || !w) return;
  const frames = f.fixture.watch[w.watchKey] ?? [];   // a view the fixture did not record: no values
  const nets: Record<string, string> = {};
  const bodies: Record<string, unknown> = {};
  for (let k = from; k <= to; k++) {
    // past the recorded cycles: the last one again (the circuit keeps its state)
    const fr = frames[Math.min(k, frames.length - 1)];
    if (!fr || (k >= frames.length && k > from)) continue;
    Object.assign(nets, fr.nets);
    Object.assign(bodies, fr.bodies);
  }
  if (!Object.keys(nets).length && !Object.keys(bodies).length) return;
  const params: Record<string, unknown> = { fileId: f.fileId, circuitId: w.circuitId, nets, bodies };
  if (w.path.length) { params.root = w.root; params.path = w.path; }
  setImmediate(() => notify('sim.values', params));
}

function handle(line: string): void {
  let msg: { jsonrpc?: string; id?: number; method?: string; params?: Params };
  try { msg = JSON.parse(line); } catch {
    write({ jsonrpc: '2.0', id: null, error: { code: -32700, message: 'parse error' } });
    return;
  }
  const { id, method } = msg;
  if (method === crashOn) process.exit(70);
  if (method === 'engine.hello' && modes.has('silent-hello')) return;
  if (method === 'engine.hello' && typeof msg.params?.idFloor === 'number' && msg.params.idFloor > 0) {
    restarted = true;
    floor(msg.params.idFloor);
  }
  if (restarted && method === crashAfterRestart) process.exit(70);
  if (restarted && method === failAfterRestart) {
    write({ jsonrpc: '2.0', id, error: { code: -32603, message: `failing ${method} after a restart, as asked` } });
    return;
  }
  const f = method ? methods[method] : undefined;
  if (!f) { write({ jsonrpc: '2.0', id, error: { code: -32601, message: `no method ${String(method)}` } }); return; }
  try {
    write({ jsonrpc: '2.0', id, result: f(msg.params ?? {}) });
  } catch (e) {
    const err = e instanceof Failure ? e : new Failure(-32603, String(e));
    write({ jsonrpc: '2.0', id, error: { code: err.code, message: err.message, ...(err.data === undefined ? {} : { data: err.data }) } });
  }
}

if (modes.has('exit-at-start')) {
  process.stderr.write('fake engine: exiting at start, as asked\n');
  process.exit(3);
}
if (modes.has('noise')) {
  process.stdout.write('Picked up JAVA_TOOL_OPTIONS: something a JVM prints\n');
  notify('engine.log', { level: 'info', message: 'fake engine started' });
}
process.stderr.write('fake engine ready\n');
let rest = '';
process.stdin.setEncoding('utf8');
process.stdin.on('data', (chunk: string) => {
  const parts = (rest + chunk).split('\n');
  rest = parts.pop() ?? '';
  for (const p of parts) if (p.trim()) handle(p.trim());
});
process.stdin.on('end', () => setImmediate(() => process.exit(0)));
