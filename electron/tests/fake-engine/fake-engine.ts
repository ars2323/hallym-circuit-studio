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
   clock, told back as sim.state; more than 200 cycles go on over time
   with cyclesLeft, and Run or Stop ends them, D-145), sim.tick/step,
   sim.poke/pokeKey/pokeStop (N-07: an input pin flips the bit under the
   pointer, a clock flips, a button is 1 while pressed, a register takes
   hex digits -- on a canvas fixture's nets), editing with a selection
   (N-08: edit.select at/rect/ids/all/filter, the selection moved, deleted,
   copied, cut, pasted and duplicated -- a paste floats until the next
   selection -- edit.rotate, edit.keyConfig, edit.setToolAttr, edit.text,
   model.tool, model.movePreview, model.textAt, sim.pinValue; edit.selection
   after the answer: the part boxes' hits, nothing like Logisim's rules --
   the real engine's are in tests/e2e/real-engine-edit.e2e.ts), diag.list and
   diag.changed (D-143: the real engine's words for the circuits in
   tests/fixtures/messages.json -- written by tools/diag-fixture.ts --
   matched by file name; the list after cycles once the file has run the
   fixture's cycles, the first one again at Reset; every other file has no
   messages), trace.origin
   (nothing to follow), mips.* (N-16: tests/fake-engine/fake-mips.ts -- the
   real engine's answers for a few executable images, tests/fixtures/programs.json),
   model.library, find.query, edit.tunnelColor/splitterEdit/splitterSplit (N-12:
   tests/fake-engine/fake-find.ts -- the real engine's library and Find answers,
   tests/fixtures/library.json, find.json).  The same shapes as
   the real engine's (docs/engine-api.md, engine/ D-134): Logisim's project
   name (Untitled, a file's name without .circ), alreadyOpen, messages,
   needsMipsJar; and a restarted engine's engine.hello idFloor and
   file.new/open restore (docs/engine-api.md 7, D-142).  Recovery files
   (N-19, D-152): file.recoverWrite writes the fake's model as a small .circ
   beside the file (what readCirc reads back), file.open recovery
   recover|discard, and when engine.hello asked (recoveryFiles) the
   file is removed on save, close and engine.shutdown and written for every
   file with unsaved edits when stdin ends (the main process is gone).  record.* (N-14):
   tests/fake-engine/fake-record.ts -- a file with an Instruction Memory
   runs the recursive factorial on a tiny machine there, one instruction a
   cycle.  The overlays (N-15, D-151): tests/fake-engine/fake-flow.ts --
   trace.influence, trace.net, flow.path and flow.activePath answer from the
   real engine's answers for demo-datapath (tests/fixtures/flow/, written by
   the engine's canvasFixtures task), found by the same fixture ids;
   edit.signalGroup and edit.areaMemo keep the groups and memos per circuit
   (one undo step each, model.changed with groups and memos).  Circuits,
   appearances, libraries, other files (N-11): tests/fake-engine/fake-circuits.ts
   -- file.info, file.changed, edit.createCircuit … edit.appearance,
   model.appearance*, Load/Unload Library, Import Subcircuits (one undo step
   of the file's state each).  The
   attribute table, the right-click menu's facts and its intents, RAM and
   ROM contents (N-10, D-157): tests/fake-engine/fake-attrs.ts -- the real
   engine's rows and menu facts (tests/fixtures/attributes.json), this
   fake's values; a value the rows cannot take is refused (badValue).
   Anything else: -32601.

   FAKE_ENGINE_MODE (comma-separated) for the tests of the client:
     silent-hello   never answers engine.hello
     exit-at-start  writes a line to stderr and exits (code 3)
     noise          a line that is not JSON and an engine.log notification first
     split          writes every message in small pieces (a Hangul name cut inside a character)
     oscillate      sim.cycles turns the simulation off (oscillation), with an engine.log warning
     needs-mips     file.save says the saved .circ needs hcs-mips.jar beside it
     slow-until     record.runUntil takes 1.5 s (record.stop ends it sooner)
     wide-registers the factorial machine starts with $s6 = -1 and $s7 = -2147483648 (the widest decimals)
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

import { existsSync, readFileSync, renameSync, rmSync, writeFileSync } from 'node:fs';
import path from 'node:path';

import * as find from './fake-find.ts';
import * as mips from './fake-mips.ts';
import * as rec from './fake-record.ts';
import * as flow from './fake-flow.ts';
import * as circuitsFake from './fake-circuits.ts';
import * as attrs from './fake-attrs.ts';

type Params = Record<string, unknown>;
interface Comp { id: string; lib: string; name: string; loc: [number, number]; attrs: Record<string, string>; ext?: { color?: string; arms?: string[] } }
interface Wire { id: string; a: [number, number]; b: [number, number] }
interface Circuit { circuitId: string; name: string; comps: Comp[]; wires: Wire[] }
interface Step { circuitId: string; comps: Comp[]; wires: Wire[]; ext?: flow.Ext; file?: circuitsFake.FileState }   // a circuit's parts before an edit (ext: a group or memo edit; file: the file's circuits, N-11)
interface File {
  fileId: string; name: string; path: string | null; bytes: Buffer | null; circuits: Circuit[]; main: string; libs: string[];
  cycle: number; ticking: boolean; hz: number; on: boolean; dirty: boolean; undo: Step[]; redo: Step[]; diag: Diag | null; ran: boolean;
  mips: mips.MipsState;     // mips.* (fake-mips.ts, N-16)
  mipsIn?: boolean;         // a Hallym MIPS part was placed: the library is in the file (V-01)
  // N-07: half a cycle ticked (sim.tick), the net values last sent, the poked part that takes keys,
  // a long N Cycles still going (its cycles left and timer)
  half?: boolean; values: Map<string, string>; caret?: { id: string; net: string | null; width: number } | null;
  going?: { left: number; timer: ReturnType<typeof setInterval> } | null;
  offByHand?: boolean;      // Simulation Enabled turned off (sim.enable), not an oscillation
  // a canvas fixture (tests/fixtures/circuits): its circuits under this file's circuit ids, the watched circuit
  fixture?: Fixture; fixtureIds?: Map<string, string>; watched?: { circuitId: string; watchKey: string; root: string; path: string[] };
  recovered?: boolean;      // opened from its recovery file: unsaved until saved (N-19)
  rec: rec.RecordFile;
  // the overlays (fake-flow.ts, N-15): the real engine's answers for a fixture circuit; groups and memos per circuit
  flow?: flow.FlowFixture; ext: Map<string, flow.Ext>;
  // N-08: the selection (the circuit it is in, its parts and wires, a paste or duplicate not yet dropped) and the last one told
  sel?: { circuitId: string; ids: string[]; floating: (Comp | Wire)[] }; selSent?: string;
  selUnordered?: boolean;   // N-10: the selection came in at once (a rectangle, all): its order is not known
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
let recoveryFiles = false;    // engine.hello recoveryFiles: the app keeps recovery files beside its files (N-19)
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
  // a JAR library by its class, as the real engine names it (the bundled Hallym MIPS: kr.ac.hallym.hcs.mips.MipsLibrary)
  for (const m of text.matchAll(/<lib desc="jar#[^"]*#([^"#]+)" name="([^"]+)"/g)) libs.set(m[2], m[1]);
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

// ---- recovery files (N-19, D-152) ----------------------------------------------------

const RECOVERY = '.hcs-recover';
// Values as readCirc read them (it does not unescape: what was escaped in the file stays so).
const esc = (s: string) => s;

// The fake's model as a .circ its own readCirc reads back (the real engine writes what a save would).
function writeCirc(f: File): string {
  const libs = [...new Set(f.circuits.flatMap((c) => c.comps.map((k) => k.lib)).filter((l) => l !== 'circuit'))];
  const out = ['<?xml version="1.0" encoding="UTF-8" standalone="no"?>', '<project source="2.7.1" version="1.0">'];
  libs.forEach((l, i) => out.push(`  <lib desc="#${esc(l)}" name="${i}"/>`));
  out.push(`  <main name="${esc(f.main)}"/>`);
  for (const c of f.circuits) {
    out.push(`  <circuit name="${esc(c.name)}">`);
    for (const w of c.wires) out.push(`    <wire from="(${w.a[0]},${w.a[1]})" to="(${w.b[0]},${w.b[1]})"/>`);
    for (const k of c.comps) {
      const lib = k.lib === 'circuit' ? '' : ` lib="${libs.indexOf(k.lib)}"`;
      const attrs = Object.entries(k.attrs).map(([n, v]) => `      <a name="${esc(n)}" val="${esc(v)}"/>`);
      out.push(`    <comp${lib} loc="(${k.loc[0]},${k.loc[1]})" name="${esc(k.name)}">`, ...attrs, '    </comp>');
    }
    out.push('  </circuit>');
  }
  out.push('</project>', '');
  return out.join('\n');
}
const recoveryOf = (file: string) => `${file}${RECOVERY}`;
function removeRecovery(file: string | null): void {
  if (!file) return;
  for (const p of [recoveryOf(file), `${recoveryOf(file)}.tmp`]) rmSync(p, { force: true });
}
function writeRecovery(f: File): { path: string; bytes: number } {
  const target = recoveryOf(f.path!);
  const text = writeCirc(f);
  writeFileSync(`${target}.tmp`, text);
  renameSync(`${target}.tmp`, target);
  return { path: target, bytes: Buffer.byteLength(text) };
}

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
// A file's register places now (fake-record.ts Places).
function places(f: File): rec.Places {
  const main = f.circuits.find((c) => c.name === f.main) ?? f.circuits[0];
  const pc = main?.comps.find((k) => k.name === 'Register' && (k.attrs.label ?? '').toLowerCase() === 'pc');
  return { main: main?.circuitId ?? '', pc: pc?.id ?? null, regfile: f.circuits.find((c) => c.name === 'regfile')?.circuitId ?? null };
}

// Run Until in progress, by file.
const untils = new Map<string, { until: { kind: string; value?: string; from: number }; stop: () => void }>();
// record.state after anything that changes the recording (the engine sends it once a frame at most).
// Reset (sim.reset, a program loaded): the recording starts over from cycle 0 and its pinned rows go.
function restartRecording(f: File): void {
  f.rec.cycle = 0; f.rec.view = null; f.rec.generation += 1; f.rec.pinnedCycle = -1;
  f.rec.rows = f.rec.rows.filter((r) => !r.temp);
  setImmediate(() => recordChanged(f));
}
function recordChanged(f: File): void {
  if (files.has(f.fileId)) notify('record.state', rec.recordState(f.rec, untils.get(f.fileId)?.until ?? null));
}
const simState = (f: File) => ({ fileId: f.fileId, running: f.on, ticking: f.ticking, cycle: f.cycle, oscillating: !f.on && !f.offByHand, hz: f.hz, cyclesLeft: f.going?.left ?? 0 });
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

type ToolbarItem = { name: string; tool: string } | { name: string; lib: string | null; attrs?: Record<string, string> };
const DEFAULT_TOOLBAR: ToolbarItem[] = [
  { name: 'Poke Tool', tool: 'Poke Tool' }, { name: 'Edit Tool', tool: 'Edit Tool' }, { name: 'Text Tool', tool: 'Text Tool' },
  { name: 'Pin', lib: 'Wiring', attrs: { tristate: 'false' } }, { name: 'Pin', lib: 'Wiring', attrs: { facing: 'west', output: 'true', labelloc: 'east' } },
  { name: 'NOT Gate', lib: 'Gates' }, { name: 'AND Gate', lib: 'Gates' }, { name: 'OR Gate', lib: 'Gates' },
];
function toolbarOf(f: { bytes: Buffer | null }): ToolbarItem[] {
  const text = f.bytes?.toString('utf8') ?? '';
  const bar = /<toolbar>([\s\S]*?)<\/toolbar>/.exec(text);
  if (!bar) return DEFAULT_TOOLBAR;
  const libs = new Map([...text.matchAll(/<lib desc="#?([^"]*)" name="([^"]*)"/g)].map((m) => [m[2], m[1].startsWith('jar#') ? 'Hallym MIPS' : m[1]]));
  const out: ToolbarItem[] = [];
  for (const m of bar[1].matchAll(/<tool(?: lib="([^"]*)")? name="([^"]*)"\s*(\/>|>([\s\S]*?)<\/tool>)/g)) {
    const lib = m[1] === undefined ? null : libs.get(m[1]) ?? null;
    if (lib === 'Base') { out.push({ name: m[2], tool: m[2] }); continue; }
    const attrs = Object.fromEntries([...(m[4] ?? '').matchAll(/<a name="([^"]*)" val="([^"]*)"\/>/g)].map((a) => [a[1], a[2]]));
    out.push({ name: m[2], lib, ...(Object.keys(attrs).length ? { attrs } : {}) });
  }
  return out;
}

const methods: Record<string, (p: Params) => unknown> = {
  'engine.hello': (p) => {
    if (typeof p.recoveryFiles === 'boolean') recoveryFiles = p.recoveryFiles;
    return { engine: 'fake-engine', version: '0', logisim: '2.7.1', java: 'none (fake engine, Node)', api: '0' };
  },
  'engine.shutdown': () => {
    if (recoveryFiles) for (const f of files.values()) removeRecovery(f.path);
    setImmediate(() => process.exit(0));
    return {};
  },
  'file.new': (p) => {
    const c: Circuit = { circuitId: `c${nextCircuit++}`, name: 'main', comps: [], wires: [] };
    const fileIdFor0 = fileIdFor(p);
    const f: File = { fileId: fileIdFor0, name: 'Untitled', path: null, bytes: null, circuits: [c], main: 'main', libs: [], cycle: 0, ticking: false, hz: 1, on: true, dirty: false, undo: [], redo: [], diag: null, ran: false, mips: mips.newState(), values: new Map(), rec: rec.newRecordFile(fileIdFor0, false, new Map()), ext: new Map() };
    adopt(f, p);
    files.set(f.fileId, f);
    setImmediate(() => recordChanged(f));
    return { fileId: f.fileId, name: f.name, circuits: refs(f), main: mainId(f), libraries: libRefs(f) };
  },
  'file.open': (p) => {
    const file = String(p.path ?? '');
    const open = [...files.values()].find((x) => x.path === file);
    if (open) return { fileId: open.fileId, name: open.name, circuits: refs(open), main: mainId(open), libraries: libRefs(open), messages: [], alreadyOpen: true };
    const recovery = p.recovery;
    if (recovery !== undefined && recovery !== 'recover' && recovery !== 'discard') throw new Failure(-32602, 'param \'recovery\' must be "recover" or "discard"');
    let bytes: Buffer;
    try { bytes = readFileSync(file); } catch (e) {
      // The real engine's words and reasons (engine/ Files.open, docs/engine-api.md 2): English, for developers.
      if ((e as NodeJS.ErrnoException).code === 'ENOENT') throw new Failure(2, `no such file: ${file}`, { path: file, reason: 'notFound' });
      throw new Failure(2, `cannot read: ${file}`, { path: file, reason: 'unreadable' });
    }
    if (recovery === 'recover') {
      // the recovery file's content in the file's place (the real engine: the original loader's substitution)
      try { bytes = readFileSync(recoveryOf(file)); } catch {
        throw new Failure(2, `no recovery file: ${recoveryOf(file)}`, { path: recoveryOf(file), reason: 'notFound' });
      }
    }
    const text = bytes.toString('utf8');
    if (!text.includes('<project')) throw new Failure(2, `The file does not appear to be a Logisim project file: ${file}`, { path: file, reason: 'loadFailed' });
    const fileId = fileIdFor(p);
    const r = readCirc(text);
    const f: File = { fileId, name: stem(path.basename(file)), path: file, bytes, circuits: r.circuits, main: r.main, libs: r.libs, cycle: 0, ticking: false, hz: 1, on: true, dirty: false, undo: [], redo: [], diag: DIAG[path.basename(file)] ?? null, ran: false, mips: mips.newState(), values: new Map(), rec: rec.newRecordFile(fileId, false, new Map()), ext: new Map() };
    const fx = path.join(FIXTURES, `${stem(path.basename(file))}.json`);
    if (recovery !== 'recover' && existsSync(fx)) {
      f.fixture = JSON.parse(readFileSync(fx, 'utf8')) as Fixture;
      // the fixture's parts, copied: this fake's edits change them (and model.circuit shows the edits)
      f.circuits = f.fixture.circuits.map((c) => ({ circuitId: c.circuitId, name: c.name, comps: structuredClone(c.components), wires: structuredClone(c.wires) }));
      f.main = f.fixture.circuits.find((c) => c.circuitId === f.fixture!.main)?.name ?? f.main;
    }
    // the Cycle View's recording (fake-record.ts): the labels of the main circuit's parts, an Instruction Memory anywhere
    const mainCircuit = f.circuits.find((c) => c.name === f.main) ?? f.circuits[0];
    const names = new Map((mainCircuit?.comps ?? []).filter((k) => k.attrs.label).map((k) => [`${k.loc[0]},${k.loc[1]}`, k.attrs.label]));
    const cpu = f.circuits.some((c) => c.comps.some((k) => k.name === 'Instruction Memory'));
    f.rec = rec.newRecordFile(fileId, cpu, names, modes.has('wide-registers'));
    adopt(f, p);
    if (f.fixture) f.fixtureIds = new Map(f.circuits.map((c, i) => [c.circuitId, f.fixture!.circuits[i].circuitId]));
    f.flow = flow.load(path.basename(file));
    if (recovery === 'recover') { f.dirty = true; f.recovered = true; }
    files.set(f.fileId, f);
    if (recovery === 'discard') removeRecovery(file);   // after it opened
    setImmediate(() => recordChanged(f));
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
    if (recoveryFiles) { removeRecovery(f.path); removeRecovery(target); }
    f.path = target;
    f.name = stem(path.basename(target));
    f.dirty = false;
    f.recovered = false;
    circuitsFake.saved(circuitsCtx, f as unknown as circuitsFake.CFile);
    return { path: target, bytes: bytes.length, needsMipsJar: modes.has('needs-mips') };
  },
  'file.close': (p) => {
    const f = fileOf(p);
    stopRun(f);
    if (recoveryFiles && p.keepRecovery !== true) removeRecovery(f.path);
    mips.close(f);
    files.delete(String(p.fileId));
    return {};
  },
  'file.recoverWrite': (p) => {
    const f = fileOf(p);
    if (!f.path) return { path: null, written: false };
    if (!f.dirty) { removeRecovery(f.path); return { path: recoveryOf(f.path), written: false }; }
    return { ...writeRecovery(f), written: true };
  },
  'file.dirty': (p) => ({ dirty: fileOf(p).dirty }),
  'model.circuit': (p) => {
    const f = fileOf(p);
    if (f.fixture) {
      const snap = f.fixture.circuits.find((x) => x.circuitId === f.fixtureIds!.get(String(p.circuitId)));
      const c = f.circuits.find((x) => x.circuitId === p.circuitId);
      if (!snap || !c) throw new Failure(1, `no such circuit id: ${String(p.circuitId)}`, { kind: 'circuit', id: String(p.circuitId) });
      // the engine's parts as recorded, the fake's own edits on top (the nets only while there are none);
      // the fixture's circuit ids as this file's (a recovered file keeps the old engine's)
      const edited = f.undo.some((s) => !s.ext) || f.redo.some((s) => !s.ext);   // a group or memo keeps the nets
      const comps = c.comps.map((k) => {
        const x = k as Comp & { ports?: unknown; subcircuit?: string };
        if (x.ports === undefined) return compJson(k);
        return x.subcircuit ? { ...x, subcircuit: currentId(f, x.subcircuit) } : x;
      });
      return { circuitId: c.circuitId, name: c.name, components: comps, wires: c.wires, nets: edited ? netsOf(f, c) : snap.nets, junctions: edited ? [] : snap.junctions, ...extJson(f, c.circuitId, true) };
    }
    const c = circuitOf(p);
    return { circuitId: c.circuitId, name: c.name, components: c.comps.map(compJson), wires: c.wires, nets: [], junctions: [], ...extJson(f, c.circuitId, true) };
  },
  'model.library': (p) => {
    const f = fileOf(p);
    const real = find.library(f);   // the real engine's (tests/fixtures/library.json, N-12)
    if (real.length > 1) return real;
    return [
      { lib: null, display: f.name, tools: f.circuits.map((c) => ({ name: c.name, display: c.name, circuitId: c.circuitId })) },
      ...LIBRARY.map((g) => ({ lib: g.lib, display: g.lib === 'I/O' ? 'Input/Output' : g.lib, tools: g.tools.map((name) => ({ name, display: name })) })),
    ];
  },
  // The toolbar (N-17, D-158: Ctrl+2…9): the .circ's <toolbar> as the real engine lists it (a library by its name, a
  // base tool by `tool`; the real engine gives only the attributes unlike the library's, this fake the file's own),
  // the default template's for a new file.
  'model.toolbar': (p) => toolbarOf(fileOf(p)),
  'find.query': (p) => { const f = fileOf(p); return find.findQuery(f.fileId, f, String(p.text ?? '')); },
  'edit.tunnelColor': (p) => {
    const c = circuitOf(p);
    return edit(p, c, () => ({ removed: [], added: failing(() => find.tunnelColor(c, p)) }));
  },
  'edit.splitterEdit': (p) => {
    const c = circuitOf(p);
    return edit(p, c, () => ({ removed: [], added: failing(() => find.splitterEdit(c, p)) }));
  },
  'edit.splitterSplit': (p) => {
    const f = fileOf(p);
    const c = circuitOf(p);
    if (!c.wires.some((w) => w.id === p.wire)) throw new Failure(1, `no such component id: ${String(p.wire)}`, { kind: 'component', id: String(p.wire) });
    // the wire's width: the engine's net (a canvas fixture's circuit), else 8
    const fx = f.fixture?.circuits.find((x) => x.circuitId === f.fixtureIds?.get(c.circuitId));
    const net = (fx?.nets as { width: number; wires: string[] }[] | undefined)?.find((n) => n.wires.includes(String(p.wire)));
    const width = net?.width ?? 8;
    if (width <= 1) throw new Failure(-32602, 'Split Bits needs a multi-bit wire');
    const id = `k${nextComp++}`;
    return edit(p, c, () => ({ removed: [], added: [failing(() => find.splitterSplit(c, p, width, id))] }), { id });
  },
  'edit.addComponent': (p) => {
    const c = circuitOf(p);
    const loc = p.loc as [number, number];
    if (!Array.isArray(loc) || typeof p.name !== 'string') throw new Failure(-32602, 'loc and name are required');
    const f = fileOf(p);
    dropFloating(f, c);
    const lib = (p.lib as string | null | undefined) ?? 'circuit';
    const labelled: Record<string, string> = p.name !== 'Text' && p.name !== 'Splitter' ? { label: '' } : {};   // the parts with a label attribute (most)
    const k: Comp = { id: `k${nextComp++}`, lib, name: p.name, loc: [loc[0], loc[1]], attrs: { ...labelled, ...(toolAttrs.get(`${lib}/${p.name}`) ?? {}), ...(p.attrs as Record<string, string> ?? {}) } };
    if (k.lib === find.MIPS_LIB) f.mipsIn = true;   // the bundled library goes into the file (V-01)
    const r = edit(p, c, () => { c.comps.push(k); return { removed: [], added: [k] }; }, { id: k.id });
    select(f, c, [k.id]);
    return r;
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
    if (p.tool !== 'edit') select(fileOf(p), c, []);      // taking the Wiring tool drops the selection
    return edit(p, c, () => { c.wires.push(...added); return { removed: [], added }; });
  },
  'edit.move': (p) => {
    const c = circuitOf(p);
    const f = fileOf(p);
    const ids = targets(f, c, p);
    const dx = Number(p.dx);
    const dy = Number(p.dy);
    const floating = f.sel?.circuitId === c.circuitId ? f.sel.floating : [];
    if (floating.length) {
      // a paste moved: dropped where it is moved to (one step with the paste in Logisim)
      const moved = floating.map((x) => ('a' in x ? { ...x, a: [x.a[0] + dx, x.a[1] + dy] as [number, number], b: [x.b[0] + dx, x.b[1] + dy] as [number, number] } : { ...x, loc: [x.loc[0] + dx, x.loc[1] + dy] as [number, number] }));
      f.sel!.floating = [];
      const r = edit(p, c, () => {
        for (const x of moved) if ('a' in x) c.wires.push(x); else c.comps.push(x);
        return { removed: [], added: moved };
      }, { outcome: 'moved' });
      select(f, c, moved.map((x) => x.id));
      return r;
    }
    if (!ids.size) return { changed: false, outcome: 'empty' };
    const newIds: string[] = [];
    const r = edit(p, c, () => {
      const added: (Comp | Wire)[] = [];
      c.comps = c.comps.map((k) => {
        if (!ids.has(k.id)) return k;
        const moved = { ...k, id: `k${nextComp++}`, loc: [k.loc[0] + dx, k.loc[1] + dy] as [number, number] };
        added.push(moved);
        newIds.push(moved.id);
        return moved;
      });
      c.wires = c.wires.map((w) => {
        if (!ids.has(w.id)) return w;
        const moved = { id: `w${nextWire++}`, a: [w.a[0] + dx, w.a[1] + dy] as [number, number], b: [w.b[0] + dx, w.b[1] + dy] as [number, number] };
        added.push(moved);
        newIds.push(moved.id);
        return moved;
      });
      return { removed: [...ids], added };
    }, { outcome: 'moved' });
    select(f, c, newIds);
    return r;
  },
  'edit.delete': (p) => {
    const c = circuitOf(p);
    const f = fileOf(p);
    const ids = targets(f, c, p);
    if (f.sel?.circuitId === c.circuitId && f.sel.floating.length) f.sel.floating = [];   // a paste not dropped just goes
    select(f, c, []);
    if (!ids.size) { f.undo.push(copyParts(c)); f.redo = []; f.dirty = true; return { changed: true, outcome: 'empty' }; }   // Logisim's empty Delete is an undo step too
    circuitsFake.pinsRemoved(circuitsCtx, f as unknown as circuitsFake.CFile, c as unknown as circuitsFake.CCircuit, c.comps.filter((k) => ids.has(k.id)) as unknown as circuitsFake.CComp[]);
    return edit(p, c, () => {
      c.comps = c.comps.filter((k) => !ids.has(k.id));
      c.wires = c.wires.filter((w) => !ids.has(w.id));
      return { removed: [...ids], added: [] };
    });
  },
  'edit.setAttr': (p) => {
    const c = circuitOf(p);
    const ids = p.keepSelection === true && Array.isArray(p.ids) ? partsOf(c, p.ids) : targets(fileOf(p), c, p);
    for (const k of c.comps.filter((x) => ids.has(x.id))) attrCheck(k, String(p.attr), String(p.value));
    return edit(p, c, () => {
      const added = c.comps.filter((k) => ids.has(k.id));
      for (const k of added) k.attrs = { ...k.attrs, [String(p.attr)]: String(p.value) };
      return { removed: [], added };
    });
  },
  'edit.undo': (p) => undoRedo(fileOf(p), 'undo'),
  'edit.redo': (p) => undoRedo(fileOf(p), 'redo'),
  // ---- N-08: the selection's edits
  // (the engine always tells the selection after edit.select: the window's guess is set right)
  'edit.select': (p) => { const r = selectIntent(p); const f = fileOf(p); if (f.sel) { f.selSent = undefined; publishSel(f); } return r; },
  'edit.copy': (p) => {
    const c = circuitOf(p);
    const ids = targets(fileOf(p), c, p);
    clipboard = { comps: structuredClone(c.comps.filter((k) => ids.has(k.id))), wires: structuredClone(c.wires.filter((w) => ids.has(w.id))) };
    return { changed: true };
  },
  'edit.cut': (p) => {
    methods['edit.copy'](p);
    return methods['edit.delete']({ ...p, ids: undefined });
  },
  'edit.paste': (p) => {
    const c = circuitOf(p);
    const f = fileOf(p);
    if (!clipboard || (!clipboard.comps.length && !clipboard.wires.length)) return { changed: false, outcome: 'empty' };
    dropFloating(f, c);
    f.sel = { circuitId: c.circuitId, ids: [], floating: copies(clipboard.comps, clipboard.wires, 10) };
    publishSel(f);
    return { changed: true };
  },
  'edit.duplicate': (p) => {
    const c = circuitOf(p);
    const f = fileOf(p);
    const ids = targets(f, c, p);
    if (!ids.size) return { changed: false, outcome: 'empty' };
    dropFloating(f, c);
    f.sel = { circuitId: c.circuitId, ids: [], floating: copies(c.comps.filter((k) => ids.has(k.id)), c.wires.filter((w) => ids.has(w.id)), 10) };
    publishSel(f);
    return { changed: true };
  },
  'edit.rotate': (p) => {
    const c = circuitOf(p);
    const ids = targets(fileOf(p), c, p);
    const turn = p.clockwise === false ? { east: 'north', north: 'west', west: 'south', south: 'east' } : { east: 'south', south: 'west', west: 'north', north: 'east' };
    const faced = c.comps.filter((k) => ids.has(k.id));
    if (!faced.length) return { changed: false };
    return edit(p, c, () => {
      for (const k of faced) k.attrs = { ...k.attrs, facing: turn[(k.attrs.facing ?? 'east') as keyof typeof turn] };
      return { removed: [], added: faced };
    });
  },
  'edit.keyConfig': (p) => {
    const key = String(p.key ?? '');
    const digit = /^[0-9]$/.test(key) ? key : null;
    if (typeof p.name === 'string') {
      const k = `${(p.lib as string | null | undefined) ?? 'circuit'}/${p.name}`;
      const a = { ...(toolAttrs.get(k) ?? {}) };
      if (digit !== null) a[p.alt ? 'width' : 'inputs'] = digit;
      else if (key.startsWith('Arrow') && !p.alt) a.facing = { ArrowUp: 'north', ArrowDown: 'south', ArrowLeft: 'west', ArrowRight: 'east' }[key] ?? 'east';
      toolAttrs.set(k, a);
      return { changed: true };
    }
    const c = circuitOf(p);
    const ids = targets(fileOf(p), c, p);
    const parts = c.comps.filter((k) => ids.has(k.id) && digit !== null);
    if (!parts.length) return { changed: false };
    return edit(p, c, () => {
      for (const k of parts) k.attrs = { ...k.attrs, [p.alt ? 'width' : 'inputs']: digit! };
      return { removed: [], added: parts };
    });
  },
  'edit.setToolAttr': (p) => {
    const k = `${(p.lib as string | null | undefined) ?? 'circuit'}/${String(p.name)}`;
    const a = { ...(toolAttrs.get(k) ?? {}) };
    if (a[String(p.attr)] === String(p.value)) return { changed: false, outcome: 'same' };
    a[String(p.attr)] = String(p.value);
    toolAttrs.set(k, a);
    return { changed: true };
  },
  'edit.text': (p) => {
    const c = circuitOf(p);
    const text = String(p.text ?? '');
    if (typeof p.id === 'string') {
      const k = c.comps.find((x) => x.id === p.id);
      if (!k) throw new Failure(1, `no such component id: ${p.id}`, { kind: 'component', id: p.id });
      return edit(p, c, () => { k.attrs = { ...k.attrs, [k.name === 'Text' ? 'text' : 'label']: text }; return { removed: [], added: [k] }; });
    }
    if (!text) return { changed: false, outcome: 'empty' };
    const loc = p.loc as [number, number];
    return methods['edit.addComponent']({ ...p, lib: 'Base', name: 'Text', loc, attrs: { text } });
  },
  'model.tool': (p) => {
    const lib = (p.lib as string | null | undefined) ?? null;
    if (typeof p.name !== 'string' || (lib !== null && !LIBRARY.some((g) => g.lib === lib && g.tools.includes(String(p.name))))) throw new Failure(1, `no tool ${String(lib)}/${String(p.name)}`, { kind: 'tool', id: String(p.name) });
    const loc = (Array.isArray(p.loc) ? p.loc : [0, 0]) as [number, number];
    return { component: { ...compJson({ id: 'ghost', lib: lib ?? 'circuit', name: p.name, loc, attrs: { ...(toolAttrs.get(`${lib ?? 'circuit'}/${p.name}`) ?? {}), ...(p.attrs as Record<string, string> ?? {}) } }) } };
  },
  'model.movePreview': (p) => { circuitOf(p); return { dx: Number(p.dx), dy: Number(p.dy), added: [], removed: [], unconnected: [] }; },
  'model.textAt': (p) => {
    const c = circuitOf(p);
    const at = p.loc as [number, number];
    for (const id of hitsAt(c, at)) {
      const k = c.comps.find((x) => x.id === id);
      if (k && ('label' in k.attrs || k.name === 'Text')) return { id: k.id, text: k.name === 'Text' ? k.attrs.text ?? '' : k.attrs.label ?? '', box: boxOf(k) };
    }
    if (at[0] < 0 || at[1] < 0) return { id: null, none: true };
    return { id: null, text: '', box: [at[0], at[1] - 8, 40, 16] };
  },
  'sim.pinValue': (p) => {
    const f = fileOf(p);
    const c = circuitOf(p);
    const k = c.comps.find((x) => x.id === p.componentId);
    if (!k || k.name !== 'Pin' || k.attrs.output === 'true') throw new Failure(-32602, 'component is not an input pin');
    const width = Number(k.attrs.width ?? '1');
    const v = parseValue(String(p.value ?? ''), width);
    if (v === null) throw new Failure(-32602, `cannot read '${String(p.value)}' as a ${width}-bit value`, { reason: 'badValue' });
    const net = netAt(f, c.circuitId, k.id, 0);
    if (net) setNet(f, net.id, v.toString(2).padStart(width, '0'));
    return {};
  },
  'sim.reset': (p) => {
    const f = fileOf(p);
    stopRun(f);
    f.cycle = 0; f.ticking = false; f.on = true; f.offByHand = false; f.half = false;
    sendFrames(f, 0, 0);
    setImmediate(() => notify('sim.state', simState(f)));
    mips.reset(f, notify);
    if (f.ran) { f.ran = false; if (f.diag?.afterCycles) diagChanged(f); }
    restartRecording(f);
    return {};
  },
  'sim.cycles': (p) => {
    const f = fileOf(p);
    if (!f.on) throw new Failure(4, 'the simulation stopped because the circuit oscillates', { reason: 'oscillating' });
    const n = Number(p.n ?? 1);
    if (!(n >= 1)) throw new Failure(-32602, 'n must be positive');
    f.ticking = false;             // N Cycles stops the clock first (D-145)
    if (n > LONG_CYCLES || f.going) {
      // a long run goes on over time (so Stop can end it): RUN_STEP cycles every 20 ms, cyclesLeft told
      if (f.going) f.going.left += n;
      else {
        f.cycle = f.rec.view ?? f.cycle;   // from a past cycle on show, the run goes on from there
        f.rec.view = null;
        f.going = { left: n, timer: setInterval(() => {
          const g = f.going;
          if (!g) return;
          const k = Math.min(RUN_STEP, g.left);
          const from = f.cycle;
          f.cycle += k;
          f.rec.cycle = f.cycle;
          recordChanged(f);
          g.left -= k;
          sendFrames(f, from + 1, f.cycle);
          if (g.left === 0) stopRun(f);
          notify('sim.state', simState(f));
        }, 20) };
      }
      setImmediate(() => notify('sim.state', simState(f)));
      return {};
    }
    const from = f.cycle;
    // From a past cycle on show the later ones are dropped and the run goes on from there (as the engine's recording).
    f.rec.cycle = (f.rec.view ?? f.rec.cycle) + n;
    f.rec.view = null;
    f.cycle = f.rec.cycle;
    setImmediate(() => recordChanged(f));
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
    if (p.on && !f.on) throw new Failure(4, 'the simulation is off', { reason: f.offByHand ? 'off' : 'oscillating' });
    stopRun(f);                    // Run and Stop end N Cycles (D-145)
    f.ticking = Boolean(p.on);
    if (typeof p.hz === 'number') f.hz = p.hz;
    setImmediate(() => notify('sim.state', simState(f)));
    return {};
  },
  'sim.enable': (p) => { const f = fileOf(p); f.on = Boolean(p.on); f.offByHand = !f.on; if (!f.on) stopRun(f); setImmediate(() => notify('sim.state', simState(f))); return {}; },
  // N-07: Tick Once (half a cycle), Step Simulation (only while off), Poke and its keys.
  'sim.tick': (p) => {
    const f = fileOf(p);
    if (!f.on) throw new Failure(4, 'the simulation is off', { reason: 'off' });
    stopRun(f);
    f.half = !f.half;
    if (!f.half) { f.cycle += 1; f.rec.cycle = f.cycle; f.rec.view = null; recordChanged(f); sendFrames(f, f.cycle, f.cycle); }
    setImmediate(() => notify('sim.state', simState(f)));
    return {};
  },
  'sim.step': (p) => {
    const f = fileOf(p);
    if (f.on) throw new Failure(4, 'Step Simulation works while the simulation is off', { reason: 'running' });
    return {};
  },
  'sim.poke': (p) => poke(fileOf(p), circuitOf(p), p),
  'sim.pokeKey': (p) => {
    const f = fileOf(p);
    const key = String(p.key ?? '');
    if (!key || ([...key].length !== 1 && !/^(Backspace|Enter|Tab|Delete|Escape|Arrow(Left|Right|Up|Down)|Home|End)$/.test(key))) {
      throw new Failure(-32602, 'key must be one character or a named key');
    }
    if (!f.caret) return { poked: false };
    const d = Number.parseInt(key, 16);
    if ([...key].length === 1 && !Number.isNaN(d) && f.caret.net) {
      // a register or counter: the hex digit shifts in from the right (RegisterPoker.keyTyped)
      const now = BigInt(`0b${(f.values.get(f.caret.net) ?? '0'.repeat(f.caret.width)).replace(/[^01]/g, '0')}`);
      const mask = (1n << BigInt(f.caret.width)) - 1n;
      setNet(f, f.caret.net, ((now * 16n + BigInt(d)) & mask).toString(2).padStart(f.caret.width, '0'));
    }
    return { poked: true };
  },
  'sim.pokeStop': (p) => { fileOf(p).caret = null; return {}; },
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
    // a reload the fake's watch makes starts the simulation over, the recording with it (as the engine's sim.reset)
    const r = mips.load(f, p, (m, x) => {
      notify(m, x);
      if (m === 'mips.reloaded' && (x as { ok?: boolean }).ok) restartRecording(f);
    });
    if ((r as { loaded?: boolean }).loaded) {
      setImmediate(() => notify('sim.state', simState(f)));
      restartRecording(f); // the load starts the simulation over (sim.reset), the recording with it
    }
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
  // ---- record.* (fake-record.ts)
  'record.state': (p) => rec.recordState(fileOf(p).rec, untils.get(String(p.fileId))?.until ?? null),
  'record.table': (p) => rec.recordTable(fileOf(p).rec, p),
  'record.addRow': (p) => { circuitOf(p); const f = fileOf(p); const r = rec.addRow(f.rec, p); setImmediate(() => recordChanged(f)); return r; },
  'record.removeRow': (p) => {
    const f = fileOf(p);
    const before = f.rec.rows.length;
    f.rec.rows = f.rec.rows.filter((r) => r.id !== p.id);
    setImmediate(() => recordChanged(f));
    return { removed: f.rec.rows.length < before };
  },
  'record.rowBits': (p) => { const r = fileOf(p).rec.rows.find((x) => x.id === p.id); if (r) r.bits = Boolean(p.bits); return { changed: r !== undefined }; },
  'record.pin': (p) => {
    const f = fileOf(p);
    f.rec.rows = f.rec.rows.filter((r) => !r.temp);
    const ids: string[] = [];
    for (const [i, spot] of (Array.isArray(p.rows) ? p.rows : []).entries()) {
      const at = (spot as { at?: [number, number] }).at ?? [0, 0];
      const id = `p${i + 1}`;
      f.rec.rows.push({ id, name: f.rec.names.get(`${at[0]},${at[1]}`) ?? `(${at[0]},${at[1]})`, width: 1, bits: false, temp: true });
      ids.push(id);
    }
    f.rec.pinnedCycle = typeof p.cycle === 'number' ? p.cycle : -1;
    if (f.rec.pinnedCycle >= 0) f.rec.view = Math.min(f.rec.pinnedCycle, f.rec.cycle);
    setImmediate(() => recordChanged(f));
    return { ids, ...(f.rec.pinnedCycle >= 0 ? { view: { cycle: f.rec.view, past: (f.rec.view ?? 0) < f.rec.cycle } } : {}) };
  },
  'record.unpin': (p) => { const f = fileOf(p); f.rec.rows = f.rec.rows.filter((r) => !r.temp); f.rec.pinnedCycle = -1; setImmediate(() => recordChanged(f)); return {}; },
  'record.view': (p) => {
    const f = fileOf(p);
    if (untils.has(f.fileId)) throw new Failure(4, 'the clock is running (N Cycles or Run Until)', { reason: 'busy' });
    const c = p.latest === true ? f.rec.cycle : Math.max(0, Math.min(f.rec.cycle, Number(p.cycle ?? 0)));
    f.rec.view = c < f.rec.cycle ? c : null;
    f.cycle = c;
    setImmediate(() => { notify('sim.state', simState(f)); recordChanged(f); });
    return { cycle: c, past: f.rec.view !== null };
  },
  'record.values': (p) => { circuitOf(p); return { fileId: p.fileId, circuitId: p.circuitId, cycle: p.cycle, nets: {} }; },
  'record.runUntil': (p) => {
    const f = fileOf(p);
    if (untils.has(f.fileId)) throw new Failure(4, 'Run Until is already running', { reason: 'busy' });
    const kind = String(p.kind);
    if (kind === 'pc' && !/^(0x)?[0-9a-f]{1,8}$/i.test(String(p.value ?? ''))) throw new Failure(-32602, `cannot read the PC value: ${String(p.value)}`, { reason: 'badPc' });
    const r = rec.runUntil(f.rec, p);
    const until = { kind, ...(typeof p.value === 'string' ? { value: p.value } : {}), from: r.from };
    const finish = (result: string, cycle: number) => {
      untils.delete(f.fileId);
      f.rec.cycle = cycle; f.rec.view = null; f.cycle = cycle;
      notify('record.runUntil', { fileId: f.fileId, result, cycle, from: r.from, kind, ...(until.value ? { value: until.value } : {}) });
      notify('sim.state', simState(f));
      recordChanged(f);
    };
    const timer = setTimeout(() => finish(r.result, r.cycle), modes.has('slow-until') ? 1500 : 30);
    untils.set(f.fileId, { until, stop: () => { clearTimeout(timer); finish('stopped', r.from + 1); } });
    setImmediate(() => recordChanged(f));
    return {};
  },
  'record.stop': (p) => { const u = untils.get(String(fileOf(p).fileId)); if (u) u.stop(); return { stopped: u !== undefined }; },
  'record.registers': (p) => { const f = fileOf(p); return rec.registers(f.rec, typeof p.cycle === 'number' ? p.cycle : undefined, places(f)); },
  'record.memory': (p) => rec.memory(fileOf(p).rec),
  'record.instruction': (p) => rec.instruction(fileOf(p).rec, typeof p.cycle === 'number' ? p.cycle : undefined),
  'record.fieldPaths': (p) => {
    circuitOf(p);
    const f = fileOf(p);
    const cycle = f.rec.view ?? f.cycle;
    const r = flow.fieldPaths(f.flow, fixtureOf(f, p), f.fileId, String(p.circuitId), cycle, (x) => currentId(f, x));
    // the fields of the instruction the fake's recording has in that cycle (fake-record.ts), as the engine gives only those
    const ins = rec.instruction(f.rec, cycle) as { format?: string; fields?: { name: string }[] };
    if (ins.fields && r.fields) {
      const names = new Set(ins.fields.map((x) => x.name));
      r.fields = Object.fromEntries(Object.entries(r.fields as Record<string, string[]>).filter(([n]) => names.has(n)));
      r.format = ins.format;
    }
    return r;
  },
  // ---- the Canvas's overlays (fake-flow.ts, N-15)
  'trace.influence': (p) => { const f = fileOf(p); circuitOf(p); return flowCall(() => flow.influence(f.flow, fixtureOf(f, p), p)); },
  'flow.path': (p) => {
    const f = fileOf(p);
    const c = circuitOf(p);
    const id = typeof p.wire === 'string' ? p.wire : String(p.componentId ?? '');
    if (!c.comps.some((k) => k.id === id) && !c.wires.some((w) => w.id === id)) throw new Failure(1, `no such component id: ${id}`, { kind: 'component', id });
    return flow.flowPath(f.flow, fixtureOf(f, p), c.circuitId, p, (x) => currentId(f, x));
  },
  'flow.activePath': (p) => { const f = fileOf(p); const c = circuitOf(p); return flow.activePath(f.flow, fixtureOf(f, p), c.circuitId, f.rec.view ?? f.cycle, (x) => currentId(f, x)); },
  'trace.net': (p) => { const f = fileOf(p); circuitOf(p); return flowCall(() => flow.net(f.flow, fixtureNets(f, p), p)); },
  'edit.signalGroup': (p) => {
    const f = fileOf(p);
    const c = circuitOf(p);
    const wire = String(p.wire ?? '');
    if (!c.wires.some((w) => w.id === wire)) throw new Failure(1, `no such component id: ${wire}`, { kind: 'component', id: wire });
    const net = fixtureNets(f, p).find((n) => n.wires.includes(wire))?.id ?? `n-${wire}`;
    return extEdit(f, c, (ext) => (flowCall(() => flow.setGroup(ext, net, typeof p.group === 'string' ? p.group : undefined)) ? 'set' : null));
  },
  'edit.areaMemo': (p) => {
    const f = fileOf(p);
    const c = circuitOf(p);
    const around = (ids: string[]) => {
      let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity;
      for (const id of ids) {
        const k = c.comps.find((x) => x.id === id) as (Comp & { bounds?: number[] }) | undefined;
        const w = c.wires.find((x) => x.id === id);
        if (!k && !w) throw new Failure(1, `no such component id: ${id}`, { kind: 'component', id });
        const b = k ? (k.bounds ?? compJson(k).bounds) : [Math.min(w!.a[0], w!.b[0]), Math.min(w!.a[1], w!.b[1]) - 2, Math.abs(w!.a[0] - w!.b[0]), Math.abs(w!.a[1] - w!.b[1]) + 4];
        x0 = Math.min(x0, b[0]); y0 = Math.min(y0, b[1]); x1 = Math.max(x1, b[0] + b[2]); y1 = Math.max(y1, b[1] + b[3]);
      }
      if (!(x1 > x0 && y1 > y0)) return null;
      const x = Math.floor((x0 - 20) / 10) * 10, y = Math.floor((y0 - 20) / 10) * 10;
      return { x, y, w: Math.ceil((x1 + 20) / 10) * 10 - x, h: Math.ceil((y1 + 20) / 10) * 10 - y };
    };
    return extEdit(f, c, (ext) => { const r = flowCall(() => flow.areaMemo(ext, p, around)); return r === 'same' || r === 'noMemo' ? null : r; }, true);
  },
  // ---- the circuit's and the arranging intents the N-10 menus send (the engine's rules: the engine's tests) ----
  'edit.setMainCircuit': (p) => {
    const f = fileOf(p);
    const c = circuitOf(p);
    if (f.main === c.circuitId) return { changed: false, outcome: 'same' };
    f.main = c.circuitId;
    f.dirty = true;
    return { changed: true };
  },
  'edit.setCircuitAttr': (p) => {
    const f = fileOf(p);
    const c = circuitOf(p);
    if (p.attr === 'circuit') c.name = String(p.value);
    f.dirty = true;
    return { changed: true };
  },
  'edit.duplicateN': (p) => {
    const c = circuitOf(p);
    const ids = targets(fileOf(p), c, p);
    const n = Number(p.count);
    if (!Number.isInteger(n) || n < 1 || n > 64) throw new Failure(-32602, 'count must be 1..64');
    const made = Array.from({ length: n }, (_, i) => copies(c.comps.filter((k) => ids.has(k.id)), [], 10 * (i + 1)) as Comp[]).flat();
    return edit(p, c, () => { c.comps.push(...made); return { removed: [], added: made }; });
  },
  'edit.align': (p) => { circuitOf(p); return { changed: false, outcome: 'nothing' }; },
  'edit.distribute': (p) => { circuitOf(p); return { changed: false, outcome: 'nothing' }; },
  // ---- N-10: the attribute table, the right-click menu's facts and intents, memories (fake-attrs.ts) ----
  'model.attributes': (p) => {
    const f = fileOf(p);
    if (typeof p.name === 'string') {
      const lib = (p.lib as string | null | undefined) ?? null;
      // a part held with values of its own (N-17, D-158 18): those over the tool's, the tool untouched
      const held = p.attrs && typeof p.attrs === 'object' ? p.attrs as Record<string, string> : {};
      return failing(() => attrs.toolTable(lib, p.name as string, { ...toolAttrs.get(`${lib ?? 'circuit'}/${p.name}`) ?? {}, ...held }));
    }
    const c = circuitOf(p);
    if (p.circuit === true) return attrs.circuitTable(c, true);
    const ids: string[] = Array.isArray(p.ids) ? [...partsOf(c, p.ids)] : f.sel?.circuitId === c.circuitId ? f.sel.ids : [];
    const chosen: (Comp | Wire)[] = ids.map((id) => c.comps.find((k) => k.id === id) ?? c.wires.find((w) => w.id === id)).filter((x): x is Comp | Wire => !!x);
    if (!Array.isArray(p.ids) && f.sel?.circuitId === c.circuitId) chosen.push(...f.sel.floating);
    return attrs.selectionTable(chosen as attrs.Comp[], c, true);
  },
  'model.menu': (p) => {
    const f = fileOf(p);
    const c = circuitOf(p);
    const at = p.at as [number, number];
    if (!Array.isArray(at)) throw new Failure(-32602, 'at is required');
    let id = typeof p.id === 'string' ? p.id : null;
    if (id !== null) partsOf(c, [id]);
    else { const hits = hitsAt(c, at); id = hits.find((x) => x.startsWith('k')) ?? hits[0] ?? null; }
    const fx = f.fixture?.circuits.find((x) => x.circuitId === f.fixtureIds?.get(c.circuitId));
    const width = (w: string) => ((fx?.nets as { width: number; wires: string[] }[] | undefined)?.find((n) => n.wires.includes(w))?.width ?? 1);
    return attrs.menuFacts({
      file: f.name, fixtureCircuit: f.fixtureIds?.get(c.circuitId) ?? null, circuit: c, comps: c.comps as attrs.Comp[], wires: c.wires,
      selected: f.sel?.circuitId === c.circuitId ? f.sel.ids : [], ordered: !f.selUnordered, editable: true, netWidth: width,
    }, at, id);
  },
  'edit.labels': (p) => {
    const c = circuitOf(p);
    const list = Array.isArray(p.ids) ? p.ids as string[] : [];
    const texts = Array.isArray(p.labels) ? p.labels as string[] : [];
    if (texts.length !== list.length) throw new Failure(-32602, 'labels must have one text for each of ids');
    const ids = partsOf(c, list);
    const labels = Object.fromEntries(list.map((id, i) => [id, String(texts[i])]));
    const parts = c.comps.filter((k) => ids.has(k.id) && (k.attrs.label ?? '') !== labels[k.id].trim());
    if (!parts.length) return { changed: false, outcome: 'same' };
    return edit(p, c, () => {
      for (const k of parts) k.attrs = { ...k.attrs, label: labels[k.id].trim() };
      return { removed: [], added: parts };
    });
  },
  'edit.attach': (p) => {
    const c = circuitOf(p);
    const k = one(c, p.id);
    const what = String(p.what ?? '');
    const names: Record<string, string> = { pin: 'Pin', constant: 'Constant', probe: 'Probe', tunnel: 'Tunnel' };
    if (!names[what]) throw new Failure(-32602, 'what must be pin, constant, probe or tunnel');
    const port = ((k as Comp & { ports?: { loc: [number, number] }[] }).ports ?? [])[Number(p.port)]?.loc ?? k.loc;
    const add: Comp = { id: `k${nextComp++}`, lib: 'Wiring', name: names[what], loc: [port[0], port[1]], attrs: what === 'constant' ? { value: '0x0' } : { label: '' } };
    return edit(p, c, () => { c.comps.push(add); return { removed: [], added: [add] }; });
  },
  'edit.swapGate': (p) => {
    const c = circuitOf(p);
    const k = one(c, p.id);
    const to = String(p.to ?? '');
    if (!/^(AND|OR|NAND|NOR|XOR|XNOR) Gate$/.test(k.name) || !/^(AND|OR|NAND|NOR|XOR|XNOR) Gate$/.test(to)) throw new Failure(-32602, 'not a gate');
    if (k.name === to) return { changed: false, outcome: 'same' };
    return edit(p, c, () => { k.name = to; return { removed: [], added: [k] }; });
  },
  'edit.deleteNet': (p) => {
    const f = fileOf(p);
    const c = circuitOf(p);
    const w = String(p.wire ?? '');
    partsOf(c, [w]);
    const net = fixtureNets(f, p).find((n) => n.wires.includes(w));
    const gone = new Set(net ? net.wires : [w]);
    return edit(p, c, () => { c.wires = c.wires.filter((x) => !gone.has(x.id)); return { removed: [...gone], added: [] }; });
  },
  'edit.wireToTunnels': (p) => {
    const c = circuitOf(p);
    const w = c.wires.find((x) => x.id === p.wire);
    if (!w) throw new Failure(1, `no such component id: ${String(p.wire)}`, { kind: 'component', id: String(p.wire) });
    const label = String(p.label ?? '').trim();
    if (!label) throw new Failure(-32602, 'a tunnel needs a name');
    const t1: Comp = { id: `k${nextComp++}`, lib: 'Wiring', name: 'Tunnel', loc: w.a, attrs: { label } };
    const t2: Comp = { id: `k${nextComp++}`, lib: 'Wiring', name: 'Tunnel', loc: w.b, attrs: { label } };
    return edit(p, c, () => { c.wires = c.wires.filter((x) => x !== w); c.comps.push(t1, t2); return { removed: [w.id], added: [t1, t2] }; });
  },
  'edit.probe': (p) => {
    const c = circuitOf(p);
    const w = c.wires.find((x) => x.id === p.wire);
    if (!w) throw new Failure(1, `no such component id: ${String(p.wire)}`, { kind: 'component', id: String(p.wire) });
    const at = p.at as [number, number];
    const k: Comp = { id: `k${nextComp++}`, lib: 'Wiring', name: 'Probe', loc: [Math.round(at[0] / 10) * 10, Math.round(at[1] / 10) * 10 - 20], attrs: { radix: String(p.radix ?? '16'), label: '' } };
    return edit(p, c, () => { c.comps.push(k); return { removed: [], added: [k] }; }, { id: k.id });
  },
  'edit.deleteProbes': (p) => {
    const c = circuitOf(p);
    const probes = c.comps.filter((k) => k.name === 'Probe' || k.name === 'Radix Probe');
    if (!probes.length) return { changed: false, outcome: 'empty' };
    return edit(p, c, () => { c.comps = c.comps.filter((k) => !probes.includes(k)); return { removed: probes.map((k) => k.id), added: [] }; });
  },
  'edit.combineBus': (p) => {
    const c = circuitOf(p);
    const ids = partsOf(c, p.ids);
    const ws = c.wires.filter((w) => ids.has(w.id));
    if (ws.length < 2) throw new Failure(-32602, 'pick two or more wires');
    const x = Math.max(...ws.flatMap((w) => [w.a[0], w.b[0]])) + 60;
    const y = Math.round(ws.reduce((s2, w) => s2 + w.a[1], 0) / ws.length / 10) * 10;
    const k: Comp = { id: `k${nextComp++}`, lib: 'Wiring', name: 'Splitter', loc: [Math.round(x / 10) * 10, y], attrs: { facing: 'west', fanout: String(ws.length), incoming: String(ws.length) } };
    return edit(p, c, () => { c.comps.push(k); return { removed: [], added: [k] }; }, { id: k.id });
  },
  'edit.originalItem': (p) => {
    const c = circuitOf(p);
    const k = one(c, p.id);
    if (k.name !== 'Splitter') throw new Failure(-32602, 'no menu items the engine can do');
    return edit(p, c, () => { k.attrs = { ...k.attrs, appear: Number(p.index) === 0 ? 'left' : 'right' }; return { removed: [], added: [k] }; });
  },
  'edit.memContents': (p) => {
    const f = fileOf(p);
    const c = circuitOf(p);
    const k = one(c, p.id);
    if (k.name !== 'ROM') throw new Failure(-32602, 'not a ROM');
    let changed: boolean;
    if (p.clear === true) changed = attrs.clear(f.fileId, k as attrs.Comp);
    else if (typeof p.file === 'string') {
      const vals = readFileSync(p.file, 'utf8').split('\n').slice(1).join(' ').split(/\s+/).filter(Boolean).map((t) => parseInt(t, 16));
      changed = failing(() => attrs.write(f.fileId, k as attrs.Comp, 0, vals));
    } else changed = failing(() => attrs.write(f.fileId, k as attrs.Comp, Number(p.addr), (p.values as number[]).map(Number)));
    if (!changed) return { changed: false, outcome: 'same' };
    return edit(p, c, () => ({ removed: [], added: [k] }));
  },
  'mem.read': (p) => { const f = fileOf(p); return failing(() => attrs.read(f.fileId, memoryOf(f, p), p)); },
  'mem.write': (p) => { const f = fileOf(p); const k = memoryOf(f, p); if (k.name !== 'RAM') throw new Failure(-32602, 'not a RAM'); return { changed: failing(() => attrs.write(f.fileId, k as attrs.Comp, Number(p.addr), (p.values as number[]).map(Number))) }; },
  'mem.clear': (p) => { const f = fileOf(p); const k = memoryOf(f, p); if (k.name !== 'RAM') throw new Failure(-32602, 'not a RAM'); return { changed: attrs.clear(f.fileId, k as attrs.Comp) }; },
  'mem.loadImage': (p) => {
    const f = fileOf(p);
    const k = memoryOf(f, p);
    const vals = readFileSync(String(p.file), 'utf8').split('\n').slice(1).join(' ').split(/\s+/).filter(Boolean).map((t) => parseInt(t, 16));
    return { changed: failing(() => attrs.write(f.fileId, k as attrs.Comp, 0, vals)) };
  },
  'mem.saveImage': (p) => {
    const f = fileOf(p);
    const k = memoryOf(f, p);
    const m = attrs.words(f.fileId, k as attrs.Comp);
    writeFileSync(String(p.file), `v2.0 raw\n${m.map((v) => v.toString(16)).join(' ')}\n`);
    return {};
  },
  // The marks are undoable model edits in the engine (hcs:ext): the file is unsaved after them.
  'record.markPc': (p) => {
    const c = circuitOf(p);
    const f = fileOf(p);
    if (!c.comps.some((k) => k.id === p.componentId)) throw new Failure(1, `no such component id: ${String(p.componentId)}`, { kind: 'component', id: String(p.componentId) });
    f.rec.markedPc = p.on !== false;
    f.dirty = true;
    setImmediate(() => recordChanged(f));
    return { changed: true, dirty: true };
  },
  'record.markRegisterFile': (p) => {
    circuitOf(p);
    const f = fileOf(p);
    f.rec.regfile = p.on !== false;
    f.dirty = true;
    setImmediate(() => recordChanged(f));
    return { changed: true, dirty: true };
  },
  'record.registerMapping': (p) => {
    const f = fileOf(p);
    if (!f.rec.regfile) throw new Failure(1, 'no such registerFile: (none marked)', { kind: 'registerFile', id: '(none marked)' });
    const registers = Array.from({ length: 32 }, (_, n) => ({ id: `kr${n}`, name: `$${n}`, loc: [700, 100 + 20 * n] }));
    const map = Object.fromEntries(registers.map((r, n) => [String(n), r.loc]));
    return { circuitId: places(f).regfile ?? 'c-regfile', name: 'regfile', registers, map, guess: map };
  },
  'record.setRegisterMapping': (p) => {
    const f = fileOf(p);
    if (!f.rec.regfile || p.circuitId !== places(f).regfile) throw new Failure(-32602, 'circuitId is not the marked register file');
    const map = p.map as Record<string, unknown>;
    for (const v of Object.values(map ?? {})) {
      if (v !== null && !(Array.isArray(v) && v.length === 2)) throw new Failure(-32602, 'map values are [x, y] or null');
    }
    f.dirty = true;
    return { changed: true, dirty: true };
  },
};

// ---- N-10's helpers -------------------------------------------------------------------------

// One part by id (-32602/1 as the engine).
function one(c: Circuit, id: unknown): Comp {
  const k = c.comps.find((x) => x.id === id);
  if (!k) throw new Failure(1, `no such component id: ${String(id)}`, { kind: 'component', id: String(id) });
  return k;
}
// A value the part's rows cannot take: the engine's badValue (fake-attrs.ts check).
function attrCheck(k: Comp, attr: string, value: string): void {
  try { attrs.check(k as attrs.Comp, attr, value); } catch (e) {
    const x = e as { code?: number; message: string; data?: unknown };
    throw new Failure(x.code ?? -32602, x.message, x.data);
  }
}
// The RAM or ROM of mem.*: a part of any of the file's circuits (a subcircuit's own, seen through a path).
function memoryOf(f: File, p: Params): Comp {
  for (const c of f.circuits) {
    const k = c.comps.find((x) => x.id === p.componentId);
    if (k) {
      if (k.name !== 'RAM' && k.name !== 'ROM') throw new Failure(-32602, `component ${k.id} is not a RAM or a ROM`);
      return k;
    }
  }
  throw new Failure(1, `no such component: ${String(p.componentId)}`, { kind: 'component', id: String(p.componentId) });
}

// ---- ids and edits -----------------------------------------------------------------

// A restarted engine's ids start above the window's (engine.hello idFloor).
// N-11 (fake-circuits.ts): what its methods need of this fake.
const circuitsCtx: circuitsFake.Ctx = {
  files: files as unknown as Map<string, circuitsFake.CFile>,
  fileOf: (p) => fileOf(p) as unknown as circuitsFake.CFile,
  circuitOf: (p) => circuitOf(p) as unknown as circuitsFake.CCircuit,
  notify,
  fail: (code, message, data) => { throw new Failure(code, message, data); },
  nextCircuit: () => `c${nextCircuit++}`,
  nextComp: () => `k${nextComp++}`,
  readCirc: (text) => readCirc(text) as unknown as { circuits: circuitsFake.CCircuit[]; main: string; libs: string[] },
  libRefs: (f) => libRefs(f as unknown as File),
  builtins: BUILTIN,
};
Object.assign(methods, circuitsFake.methods(circuitsCtx));

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
  id: k.id, lib: k.lib === 'circuit' ? null : k.lib, name: k.name, loc: k.loc, bounds: [k.loc[0] - 30, k.loc[1] - 15, 30, 30],
  facing: (k.attrs.facing as 'east' | undefined) ?? 'east', attrs: k.attrs, ports: [], ...(k.ext ? { ext: k.ext } : {}),
  ...(k.lib === 'circuit' ? { subcircuit: subcircuitOf(k) } : {}),
});
// A circuit instance's circuit: the one of that name in the file that has the part (N-11).
function subcircuitOf(k: Comp): string | undefined {
  for (const f of files.values()) if (f.circuits.some((c) => c.comps.includes(k))) return f.circuits.find((c) => c.name === k.name)?.circuitId;
  return undefined;
}
// A canvas fixture's part is the engine's whole JSON already (bounds, ports): as it is.
const partJson = (x: Comp | Wire) => ('a' in x ? x : (x as Comp & { ports?: unknown }).ports !== undefined ? x : compJson(x));

// fake-find.ts throws plain errors with a code: the protocol's error.
function failing<T>(f: () => T): T {
  try { return f(); } catch (e) {
    const code = (e as { code?: number }).code;
    throw code ? new Failure(code, (e as Error).message, (e as { data?: unknown }).data) : e;
  }
}

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
  const params = { fileId: f.fileId, circuitId: c.circuitId, removed, added: added.map(partJson), nets: netsOf(f, c), junctions: [], dirty: true };
  setImmediate(() => notify('model.changed', params));
  return { changed: true, ...result };
}

function undoRedo(f: File, which: 'undo' | 'redo'): unknown {
  const from = which === 'undo' ? f.undo : f.redo;
  const to = which === 'undo' ? f.redo : f.undo;
  const step = from.pop();
  if (!step) return { changed: false };
  if (step.file) {
    // a circuit, appearance or library edit (N-11): the file's state back
    to.push({ circuitId: '', comps: [], wires: [], file: circuitsFake.snapshot(f as unknown as circuitsFake.CFile) });
    circuitsFake.restore(f as unknown as circuitsFake.CFile, step.file);
    f.dirty = true;
    circuitsFake.told(circuitsCtx, f as unknown as circuitsFake.CFile);
    for (const c of f.circuits) setImmediate(() => notify('model.changed', { fileId: f.fileId, circuitId: c.circuitId, removed: [], added: [...c.comps.map(partJson), ...c.wires], nets: netsOf(f, c), junctions: [], dirty: true }));
    return { changed: true };
  }
  const c = f.circuits.find((x) => x.circuitId === step.circuitId)!;
  if (step.ext) {
    // a group or memo edit: its hcs:ext back, the parts (and their ids) as they are
    to.push({ circuitId: c.circuitId, comps: [], wires: [], ext: structuredClone(extOf(f, c.circuitId)) });
    f.ext.set(c.circuitId, step.ext);
    f.dirty = true;
    extChanged(f, c);
    return { changed: true };
  }
  to.push(copyParts(c));
  const removed = [...c.comps.map((k) => k.id), ...c.wires.map((w) => w.id)];
  // What comes back comes back under new ids (as the real engine's).
  c.comps = step.comps.map((k) => ({ ...k, id: `k${nextComp++}` }));
  c.wires = step.wires.map((w) => ({ ...w, id: `w${nextWire++}` }));
  f.dirty = true;
  const params = { fileId: f.fileId, circuitId: c.circuitId, removed, added: [...c.comps.map(partJson), ...c.wires], nets: netsOf(f, c), junctions: [], dirty: true };
  setImmediate(() => notify('model.changed', params));
  if (f.sel?.circuitId === c.circuitId) select(f, c, []);   // the parts came back under new ids
  return { changed: true };
}

/* After an edit: a canvas fixture's nets (the engine's) with what is still there -- the wires and the
   ports of the parts left -- so a wire keeps its width (the Splitter editor asks it); none elsewhere. */
function netsOf(f: File, c: Circuit): unknown[] {
  const fx = f.fixture?.circuits.find((x) => x.circuitId === f.fixtureIds?.get(c.circuitId));
  if (!fx) return [];
  const wires = new Set(c.wires.map((w) => w.id));
  const comps = new Set(c.comps.map((k) => k.id));
  return (fx.nets as { id: string; width: number; wires: string[]; ports: [string, number][] }[])
    .map((n) => ({ ...n, wires: n.wires.filter((w) => wires.has(w)), ports: n.ports.filter(([k]) => comps.has(k)) }))
    .filter((n) => n.wires.length || n.ports.length);
}

// ---- the overlays' helpers (N-15) ---------------------------------------------------------

// fake-flow.ts throws plain errors with a code: the protocol's error.
function flowCall<T>(run: () => T): T {
  try { return run(); } catch (e) {
    if (e instanceof Failure) throw e;
    const x = e as { code?: number; message?: string; data?: unknown };
    throw new Failure(x.code ?? -32603, x.message ?? String(e), x.data);
  }
}
// The circuit's id in the overlay fixture (its canvas fixture's), or its own.
const fixtureOf = (f: File, p: Params): string => f.fixtureIds?.get(String(p.circuitId)) ?? String(p.circuitId);
// The circuit's nets as the fixture has them (none for a circuit without one).
function fixtureNets(f: File, p: Params): { id: string; wires: string[] }[] {
  const snap = f.fixture?.circuits.find((x) => x.circuitId === fixtureOf(f, p));
  return (snap?.nets ?? []) as { id: string; wires: string[] }[];
}
const extOf = (f: File, circuitId: string): flow.Ext => {
  let e = f.ext.get(circuitId);
  if (!e) { e = flow.emptyExt(); f.ext.set(circuitId, e); }
  return e;
};
// A snapshot has groups and memos when there are some; a change always has both (the circuit's whole lists).
function extJson(f: File, circuitId: string, onlySome: boolean): Record<string, unknown> {
  const e = f.ext.get(circuitId) ?? flow.emptyExt();
  if (!onlySome) return { groups: e.groups, memos: e.memos };
  return { ...(e.groups.length ? { groups: e.groups } : {}), ...(e.memos.length ? { memos: e.memos } : {}) };
}
// A group or memo edit: one undo step of the circuit's hcs:ext, model.changed with the nets it has.
function extEdit(f: File, c: Circuit, change: (ext: flow.Ext) => string | null, outcome = false): unknown {
  const ext = extOf(f, c.circuitId);
  const before = structuredClone(ext);
  const what = change(ext);
  if (what === null) return { changed: false, outcome: 'same' };
  f.undo.push({ circuitId: c.circuitId, comps: [], wires: [], ext: before });
  f.redo = [];
  f.dirty = true;
  extChanged(f, c);
  return { changed: true, ...(outcome ? { outcome: what } : {}) };
}
function extChanged(f: File, c: Circuit): void {
  const edited = f.undo.some((s) => !s.ext) || f.redo.some((s) => !s.ext);
  const snap = f.fixture?.circuits.find((x) => x.circuitId === f.fixtureIds?.get(c.circuitId));
  const params = { fileId: f.fileId, circuitId: c.circuitId, removed: [], added: [], nets: !snap ? [] : edited ? netsOf(f, c) : snap.nets, junctions: edited || !snap ? [] : snap.junctions, ...extJson(f, c.circuitId, false), dirty: true };
  setImmediate(() => notify('model.changed', params));
}

// ---- N-08: the selection ---------------------------------------------------------------------

// edit.select: a press, all, a rectangle, a filter, ids (+add, +toggle), or nothing.
function selectIntent(p: Params): unknown {
    const c = circuitOf(p);
    const f = fileOf(p);
    const now = f.sel?.circuitId === c.circuitId ? f.sel.ids : [];
    if (Array.isArray(p.at)) {
      // SelectTool.mousePressed: into the selection (Shift: out of it), onto a part, or onto nothing (a rectangle)
      const at = p.at as [number, number];
      const hits = hitsAt(c, at);
      const inSel = hits.filter((id) => now.includes(id));
      if (inSel.length && !p.toggle) return { changed: false, outcome: 'moving' };
      let ids = p.toggle ? now.filter((id) => !inSel.includes(id)) : now;
      if (hits.length) {
        if (!p.toggle && !inSel.length) { dropFloating(f, c); ids = []; }
        select(f, c, [...ids, ...hits.filter((id) => !inSel.includes(id) && !ids.includes(id))]);
        return { changed: false, outcome: 'moving' };
      }
      if (!p.toggle) { dropFloating(f, c); select(f, c, []); } else select(f, c, ids);
      return { changed: false, outcome: 'rect' };
    }
    if (p.all) { f.selUnordered = true; select(f, c, [...c.comps.map((k) => k.id), ...c.wires.map((w) => w.id)]); return { changed: false }; }
    if (Array.isArray(p.rect)) {
      const [x0, y0, x1, y1] = (p.rect as number[]).map(Number);
      const inside = [...c.comps.filter((k) => { const [x, y, w, h] = boxOf(k); return x >= Math.min(x0, x1) && y >= Math.min(y0, y1) && x + w <= Math.max(x0, x1) && y + h <= Math.max(y0, y1); }).map((k) => k.id),
        ...c.wires.filter((w) => Math.min(w.a[0], w.b[0]) >= Math.min(x0, x1) && Math.max(w.a[0], w.b[0]) <= Math.max(x0, x1) && Math.min(w.a[1], w.b[1]) >= Math.min(y0, y1) && Math.max(w.a[1], w.b[1]) <= Math.max(y0, y1)).map((w) => w.id)];
      if (inside.length > 1) f.selUnordered = true;
      if (!p.add) { dropFloating(f, c); select(f, c, inside); } else select(f, c, [...now.filter((id) => !inside.includes(id)), ...inside.filter((id) => !now.includes(id))]);
      return { changed: false };
    }
    if (typeof p.filter === 'string') {
      if (p.filter !== 'components' && p.filter !== 'wires') throw new Failure(-32602, 'filter must be components or wires');
      select(f, c, now.filter((id) => id.startsWith(p.filter === 'wires' ? 'w' : 'k')));
      return { changed: false };
    }
    const ids = Array.isArray(p.ids) ? [...partsOf(c, p.ids)] : [];
    if (!p.add && !p.toggle) f.selUnordered = ids.length > 1;
    else if (ids.length > 1) f.selUnordered = true;
    if (p.toggle) select(f, c, [...now.filter((id) => !ids.includes(id)), ...ids.filter((id) => !now.includes(id))]);
    else if (p.add) select(f, c, [...now, ...ids.filter((id) => !now.includes(id))]);
    else { dropFloating(f, c); select(f, c, ids); }
    return { changed: false };
  }



// The tools' attributes (Logisim's, shared by every file of the process) and the clipboard.
const toolAttrs = new Map<string, Record<string, string>>();
let clipboard: { comps: Comp[]; wires: Wire[] } | null = null;

// A part's box: the fixture's (the engine's), else the fake's own (compJson).
const boxOf = (k: Comp): [number, number, number, number] => (k as Comp & { bounds?: [number, number, number, number] }).bounds ?? compJson(k).bounds as [number, number, number, number];

// The parts and wires at a point (a part's box, a wire within 2 units).
function hitsAt(c: Circuit, at: [number, number]): string[] {
  const out: string[] = [];
  for (const k of c.comps) { const [x, y, w, h] = boxOf(k); if (at[0] >= x && at[0] <= x + w && at[1] >= y && at[1] <= y + h) out.push(k.id); }
  for (const w of c.wires) {
    const x0 = Math.min(w.a[0], w.b[0]) - 2, x1 = Math.max(w.a[0], w.b[0]) + 2, y0 = Math.min(w.a[1], w.b[1]) - 2, y1 = Math.max(w.a[1], w.b[1]) + 2;
    if (at[0] >= x0 && at[0] <= x1 && at[1] >= y0 && at[1] <= y1) out.push(w.id);
  }
  return out;
}

function select(f: File, c: Circuit, ids: string[]): void {
  const floating = f.sel?.circuitId === c.circuitId ? f.sel.floating : [];
  f.sel = { circuitId: c.circuitId, ids: [...new Set(ids)], floating };
  publishSel(f);
}

// The parts an edit is for: the ids given (selected first, as the engine does), else the selection.
function targets(f: File, c: Circuit, p: Params): Set<string> {
  if (Array.isArray(p.ids)) {
    const ids = partsOf(c, p.ids);
    dropFloating(f, c);
    select(f, c, [...ids]);
    return ids;
  }
  return new Set(f.sel?.circuitId === c.circuitId ? f.sel.ids.filter((id) => c.comps.some((k) => k.id === id) || c.wires.some((w) => w.id === id)) : []);
}

// A paste or duplicate not yet dropped goes into the circuit (Logisim's dropAll), selected no more.
function dropFloating(f: File, c: Circuit): void {
  const fl = f.sel?.circuitId === c.circuitId ? f.sel.floating : [];
  if (!fl.length) return;
  f.sel!.floating = [];
  f.sel!.ids = [];
  edit({ fileId: f.fileId }, c, () => {
    for (const x of fl) if ('a' in x) c.wires.push(x); else c.comps.push(x);
    return { removed: [], added: fl };
  });
  publishSel(f);
}

function copies(comps: Comp[], wires: Wire[], d: number): (Comp | Wire)[] {
  return [
    ...comps.map((k) => {
      const b = (k as Comp & { bounds?: [number, number, number, number] }).bounds;
      return { ...structuredClone(k), id: `k${nextComp++}`, loc: [k.loc[0] + d, k.loc[1] + d] as [number, number], ...(b ? { bounds: [b[0] + d, b[1] + d, b[2], b[3]] } : {}) };
    }),
    ...wires.map((w) => ({ id: `w${nextWire++}`, a: [w.a[0] + d, w.a[1] + d] as [number, number], b: [w.b[0] + d, w.b[1] + d] as [number, number] })),
  ];
}

// edit.selection after the answer, when it changed (the engine's rule).
function publishSel(f: File): void {
  const s = f.sel;
  if (!s) return;
  const params = { fileId: f.fileId, circuitId: s.circuitId, ids: [...s.ids].sort(), floating: s.floating.map(partJson) };
  const text = JSON.stringify(params);
  if (text === f.selSent) return;
  f.selSent = text;
  setImmediate(() => setImmediate(() => notify('edit.selection', params)));
}

// v1's parseValue: 0x…, 0b…, decimal, -n (two's complement); _ and spaces ignored; null when it does not fit.
function parseValue(text: string, width: number): bigint | null {
  const s = text.replace(/[_\s]/g, '').toLowerCase();
  let v: bigint;
  try {
    if (/^0x[0-9a-f]+$/.test(s)) v = BigInt(s);
    else if (/^0b[01]+$/.test(s)) v = BigInt(s);
    else if (/^-?[0-9]+$/.test(s)) v = BigInt(s);
    else return null;
  } catch { return null; }
  const size = 1n << BigInt(width);
  if (v < 0n) { if (-v > size / 2n) return null; v += size; }
  return v < size ? v : null;
}

// ---- N-07: long N Cycles, Poke ----------------------------------------------------------

const LONG_CYCLES = 200;   // more than this goes on over time (a Stop can end it)
const RUN_STEP = 25;       // cycles every 20 ms

function stopRun(f: File): void {
  if (!f.going) return;
  clearInterval(f.going.timer);
  f.going = null;
}

// A net's value set by a poke: sent as the engine would (the watched circuit's changed nets).
function setNet(f: File, net: string, value: string): void {
  f.values.set(net, value);
  const w = f.watched;
  if (!w) return;
  const params: Record<string, unknown> = { fileId: f.fileId, circuitId: w.circuitId, nets: { [net]: value } };
  if (w.path.length) { params.root = w.root; params.path = w.path; }
  setImmediate(() => notify('sim.values', params));
}

// The fixture's net at a component's port (a canvas fixture's circuit, under this file's circuit id).
function netAt(f: File, circuitId: string, componentId: string, port: number): { id: string; width: number } | null {
  const fx = f.fixture?.circuits.find((c) => c.circuitId === (f.fixtureIds?.get(circuitId) ?? circuitId));
  for (const n of (fx?.nets ?? []) as { id: string; width: number; ports: [string, number][] }[]) {
    if (n.ports.some(([c, i]) => c === componentId && i === port)) return { id: n.id, width: n.width };
  }
  return null;
}

/* sim.poke as Logisim's pokers do it, for the parts the window's tests poke: an input pin flips the bit
   under the pointer (PinPoker.getBit: bit 0 at the right, eight a row, rows of 20 from the bottom), a
   clock flips, a button is 1 while pressed, a register or counter keeps a caret for its hex digits. */
function poke(f: File, c: Circuit, p: Params): unknown {
  const k = c.comps.find((x) => x.id === p.componentId) as (Comp & { bounds?: [number, number, number, number] }) | undefined;
  if (!k) {
    if (c.wires.some((w) => w.id === p.componentId)) return { poked: false, caret: false };
    throw new Failure(1, `no such component id: ${String(p.componentId)}`, { kind: 'component', id: String(p.componentId) });
  }
  const action = String(p.action ?? 'click');
  if (!['click', 'press', 'release'].includes(action)) throw new Failure(-32602, 'action must be click, press or release');
  const at = Array.isArray(p.at) ? (p.at as [number, number]) : null;
  const net = netAt(f, c.circuitId, k.id, 0);
  const now = (width: number) => (net ? f.values.get(net.id) : undefined) ?? '0'.repeat(width);
  const flip = (v: string, bit: number) => { const i = v.length - 1 - bit; return v.slice(0, i) + (v[i] === '1' ? '0' : '1') + v.slice(i + 1); };
  if (k.name === 'Pin' && k.attrs.output !== 'true') {
    // inside an instance its value is the parent's (the engine's frozenPin, I-64)
    if (f.watched?.path.length && f.watched.circuitId === c.circuitId) throw new Failure(4, 'the pin is tied to the supercircuit state', { reason: 'frozenPin' });
    const width = Number(k.attrs.width ?? '1');
    let bit = 0;
    if (width > 1 && at && k.bounds) {
      const i = Math.trunc((k.bounds[0] + k.bounds[2] - at[0]) / 10), j = Math.trunc((k.bounds[1] + k.bounds[3] - at[1]) / 20);
      bit = 8 * j + i;
    }
    if (action !== 'press' && net && bit >= 0 && bit < width) setNet(f, net.id, flip(now(width), bit));
    f.caret = { id: k.id, net: null, width };
    return { poked: true, caret: true };
  }
  if (k.name === 'Clock') {
    if (action !== 'press' && net) setNet(f, net.id, flip(now(1), 0));
    return { poked: true, caret: true };
  }
  if (k.name === 'Button') {
    if (net) setNet(f, net.id, action === 'release' ? '0' : '1');
    if (action === 'click' && net) setNet(f, net.id, '0');
    return { poked: true, caret: action === 'press' };
  }
  if (k.name === 'Register' || k.name === 'Counter') {
    f.caret = { id: k.id, net: net?.id ?? null, width: Number(k.attrs.width ?? '8') };
    return { poked: true, caret: true };
  }
  return { poked: false, caret: false };
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
  for (const [k, v] of Object.entries(nets)) f.values.set(k, v);
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
// The main process is gone: the recovery file of every file with unsaved edits first (N-19; the real engine: Files.closeAll).
process.stdin.on('end', () => setImmediate(() => {
  if (recoveryFiles) for (const f of files.values()) if (f.path && f.dirty) { try { writeRecovery(f); } catch { /* the last one stays */ } }
  process.exit(0);
}));
