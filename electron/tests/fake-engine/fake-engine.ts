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
   model.library, edit.undo/redo ({changed:false}), sim.reset/cycles/run
   (a cycle count, told back as sim.state).  Anything else: -32601.

   FAKE_ENGINE_MODE (comma-separated) for the tests of the client:
     silent-hello   never answers engine.hello
     exit-at-start  writes a line to stderr and exits (code 3)
     noise          a line that is not JSON and an engine.log notification first
     split          writes every message in small pieces (a Hangul name cut inside a character)
   FAKE_ENGINE_CRASH_ON=<method>  exits (code 70) on that call, unanswered */

import { readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';

type Params = Record<string, unknown>;
interface Comp { id: string; lib: string; name: string; loc: [number, number]; attrs: Record<string, string> }
interface Circuit { circuitId: string; name: string; comps: Comp[]; wires: { id: string; a: [number, number]; b: [number, number] }[] }
interface File { fileId: string; name: string; path: string | null; bytes: Buffer | null; circuits: Circuit[]; main: string; libs: string[]; cycle: number; running: boolean }

const modes = new Set((process.env.FAKE_ENGINE_MODE ?? '').split(',').filter(Boolean));
const crashOn = process.env.FAKE_ENGINE_CRASH_ON ?? '';
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
  if (!f) throw new Failure(1, `no file ${String(p.fileId)}`);
  return f;
}
function circuitOf(p: Params): Circuit {
  const c = fileOf(p).circuits.find((x) => x.circuitId === p.circuitId);
  if (!c) throw new Failure(1, `no circuit ${String(p.circuitId)}`);
  return c;
}
const refs = (f: File) => f.circuits.map((c) => ({ circuitId: c.circuitId, name: c.name }));
const mainId = (f: File) => f.circuits.find((c) => c.name === f.main)?.circuitId ?? f.circuits[0]?.circuitId ?? '';
const simState = (f: File) => ({ fileId: f.fileId, running: f.running, ticking: f.running, cycle: f.cycle, oscillating: false });

const LIBRARY = [
  { lib: 'Wiring', tools: ['Splitter', 'Pin', 'Probe', 'Tunnel', 'Pull Resistor', 'Clock', 'Constant'] },
  { lib: 'Gates', tools: ['NOT Gate', 'Buffer', 'AND Gate', 'OR Gate', 'NAND Gate', 'NOR Gate', 'XOR Gate', 'XNOR Gate'] },
  { lib: 'Plexers', tools: ['Multiplexer', 'Demultiplexer', 'Decoder', 'Priority Encoder', 'Bit Selector'] },
  { lib: 'Arithmetic', tools: ['Adder', 'Subtractor', 'Multiplier', 'Divider', 'Negator', 'Comparator', 'Shifter'] },
  { lib: 'Memory', tools: ['D Flip-Flop', 'Register', 'Counter', 'RAM', 'ROM'] },
  { lib: 'Input/Output', tools: ['Button', 'LED', 'Hex Digit Display'] },
];

const methods: Record<string, (p: Params) => unknown> = {
  'engine.hello': () => ({ engine: 'fake-engine', version: '0', logisim: '2.7.1', java: 'none (fake engine, Node)' }),
  'engine.shutdown': () => { setImmediate(() => process.exit(0)); return {}; },
  'file.new': () => {
    const c: Circuit = { circuitId: `c${nextCircuit++}`, name: 'main', comps: [], wires: [] };
    const f: File = { fileId: `f${nextFile++}`, name: 'untitled.circ', path: null, bytes: null, circuits: [c], main: 'main', libs: [], cycle: 0, running: false };
    files.set(f.fileId, f);
    return { fileId: f.fileId, circuits: refs(f), main: mainId(f) };
  },
  'file.open': (p) => {
    const file = String(p.path ?? '');
    let bytes: Buffer;
    try { bytes = readFileSync(file); } catch (e) {
      throw new Failure(2, `${path.basename(file)}을(를) 읽지 못했습니다`, { path: file, reason: (e as NodeJS.ErrnoException).code ?? String(e) });
    }
    const text = bytes.toString('utf8');
    if (!text.includes('<project')) throw new Failure(2, `${path.basename(file)}은(는) Logisim 회로 파일이 아닙니다`, { path: file, reason: 'no <project>' });
    const r = readCirc(text);
    const f: File = { fileId: `f${nextFile++}`, name: path.basename(file), path: file, bytes, circuits: r.circuits, main: r.main, libs: r.libs, cycle: 0, running: false };
    files.set(f.fileId, f);
    return { fileId: f.fileId, name: f.name, circuits: refs(f), main: mainId(f), libraries: f.libs.map((lib) => ({ lib, kind: 'builtin' })) };
  },
  'file.save': (p) => {
    const f = fileOf(p);
    const target = typeof p.path === 'string' ? p.path : f.path;
    if (!target) throw new Failure(2, 'no path to save to', { reason: 'no path' });
    const bytes = f.bytes ?? Buffer.from(EMPTY, 'utf8');
    try { writeFileSync(target, bytes); } catch (e) {
      throw new Failure(2, `${path.basename(target)}에 저장하지 못했습니다`, { path: target, reason: (e as NodeJS.ErrnoException).code ?? String(e) });
    }
    f.path = target;
    f.name = path.basename(target);
    return { path: target, bytes: bytes.length };
  },
  'file.close': (p) => { fileOf(p); files.delete(String(p.fileId)); return {}; },
  'file.dirty': (p) => { fileOf(p); return { dirty: false }; },
  'model.circuit': (p) => {
    const c = circuitOf(p);
    return {
      circuitId: c.circuitId, name: c.name,
      components: c.comps.map((k) => ({
        id: k.id, lib: k.lib, name: k.name, loc: k.loc, bounds: [k.loc[0] - 30, k.loc[1] - 15, 30, 30],
        facing: (k.attrs.facing as 'east' | undefined) ?? 'east', attrs: k.attrs, ports: [],
      })),
      wires: c.wires, nets: [], junctions: [],
    };
  },
  'model.library': (p) => { fileOf(p); return LIBRARY.map((g) => ({ lib: g.lib, tools: g.tools.map((name) => ({ name, display: name })) })); },
  'edit.undo': (p) => { circuitOf(p); return { changed: false }; },
  'edit.redo': (p) => { circuitOf(p); return { changed: false }; },
  'sim.reset': (p) => { const f = fileOf(p); f.cycle = 0; f.running = false; setImmediate(() => notify('sim.state', simState(f))); return {}; },
  'sim.cycles': (p) => { const f = fileOf(p); f.cycle += Number(p.n ?? 1); setImmediate(() => notify('sim.state', simState(f))); return {}; },
  'sim.run': (p) => { const f = fileOf(p); f.running = Boolean(p.on); setImmediate(() => notify('sim.state', simState(f))); return {}; },
  'sim.watch': (p) => { circuitOf(p); return {}; },
};

function handle(line: string): void {
  let msg: { jsonrpc?: string; id?: number; method?: string; params?: Params };
  try { msg = JSON.parse(line); } catch {
    write({ jsonrpc: '2.0', id: null, error: { code: -32700, message: 'parse error' } });
    return;
  }
  const { id, method } = msg;
  if (method === crashOn) process.exit(70);
  if (method === 'engine.hello' && modes.has('silent-hello')) return;
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
  notify('engine.log', { level: 'info', message: '가짜 엔진이 시작했습니다' });
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
