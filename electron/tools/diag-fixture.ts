/* Writes tests/fixtures/messages.json: what the real engine (engine/, N-13
   D-143) says in Messages for a few broken circuits, so that the fake engine
   (tests/fake-engine/fake-engine.ts) answers diag.list and diag.changed with
   the real engine's words -- for the e2e tests and the screenshots, which
   run without Java.

     ./gradlew :engine:stage             (engine/build/stage/hcs-engine.jar)
     HCS_JAVA=<a Java 21>/bin/java node tools/diag-fixture.ts

   The engine's ids (k17, w5, c3) mean nothing to the fake engine, which
   numbers the parts of a file as it reads them; so every part is written as
   its name and place ({name, loc}), every wire as its ends, every circuit
   as its name, and the fake engine finds its own ids from those.  Nets are
   left out (the fake engine has none).  engine/ DiagFixtureTest checks on
   every CI run that the real engine still says exactly these words. */

import { spawn } from 'node:child_process';
import { writeFileSync } from 'node:fs';
import path from 'node:path';
import { createInterface } from 'node:readline';

const root = path.join(import.meta.dirname, '..');
const repo = path.join(root, '..');
const jar = process.env.HCS_ENGINE_JAR ?? path.join(repo, 'engine/build/stage/hcs-engine.jar');
const java = process.env.HCS_JAVA ?? 'java';

// The circuits, and how many cycles to run before the second list (0: none).
export const FIXTURES: { file: string; cycles: number }[] = [
  { file: 'electron/tests/fixtures/broken-datapath.circ', cycles: 2 },
  { file: 'tests/circ/faults/dynamic-x-write-data.circ', cycles: 4 },
  { file: 'tests/circ/faults/dynamic-oscillation.circ', cycles: 3 },
];

type Json = Record<string, unknown>;
const engine = spawn(java, ['-Djava.awt.headless=true', '-jar', jar], { stdio: ['pipe', 'pipe', 'inherit'] });
const pending = new Map<number, (r: Json) => void>();
const notes: Json[] = [];
let next = 1;
createInterface({ input: engine.stdout }).on('line', (line) => {
  const m = JSON.parse(line) as Json;
  if (typeof m.id === 'number' && pending.has(m.id)) {
    pending.get(m.id)!(m);
    pending.delete(m.id);
  } else if (m.method) notes.push(m);
});

function call<T = Json>(method: string, params: Json = {}): Promise<T> {
  const id = next++;
  return new Promise((done, fail) => {
    pending.set(id, (m) => (m.error ? fail(new Error(`${method}: ${JSON.stringify(m.error)}`)) : done(m.result as T)));
    engine.stdin.write(`${JSON.stringify({ jsonrpc: '2.0', id, method, params })}\n`);
  });
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

interface Snap { components: { id: string; name: string; loc: [number, number] }[]; wires: { id: string; a: [number, number]; b: [number, number] }[] }

async function portable(fileId: string, names: Map<string, string>, m: Json): Promise<Json> {
  const loc = m.location as Json;
  const snaps = new Map<string, Snap>();
  const snap = async (c: string) => {
    if (!snaps.has(c)) snaps.set(c, await call<Snap>('model.circuit', { fileId, circuitId: c }));
    return snaps.get(c)!;
  };
  const inCircuit = await snap(String(loc.circuitId));
  const comp = (s: Snap, id: string) => { const c = s.components.find((x) => x.id === id)!; return { name: c.name, loc: c.loc }; };
  // The instances of path: each in the circuit above it, from root down.
  const path: Json[] = [];
  let above = await snap(String(loc.root));
  for (const id of loc.path as string[]) {
    const c = above.components.find((x) => x.id === id) as unknown as Json & { subcircuit: string };
    path.push({ name: c.name, loc: c.loc });
    above = await snap(c.subcircuit);
  }
  const out: Json = {
    code: m.code, kind: m.kind, severity: m.severity, text: m.text,
    location: {
      circuit: names.get(String(loc.circuitId)), root: names.get(String(loc.root)), path,
      components: (loc.components as string[]).map((id) => comp(inCircuit, id)),
      wires: (loc.wires as string[]).map((id) => { const w = inCircuit.wires.find((x) => x.id === id)!; return { a: w.a, b: w.b }; }),
      at: loc.at, ...(typeof loc.cycle === 'number' ? { cycle: loc.cycle } : {}),
    },
  };
  if (m.near) out.near = m.near;
  return out;
}

const result: Record<string, Json> = {};
try {
  await call('engine.hello', { client: 'diag-fixture', version: '0' });
  for (const fx of FIXTURES) {
    const opened = await call<{ fileId: string; circuits: { circuitId: string; name: string }[] }>('file.open', { path: path.join(repo, fx.file) });
    const names = new Map(opened.circuits.map((c) => [c.circuitId, c.name]));
    const first = (await call<{ messages: Json[] }>('diag.list', { fileId: opened.fileId })).messages;
    const entry: Json = { from: fx.file, static: await Promise.all(first.map((m) => portable(opened.fileId, names, m))) };
    if (fx.cycles > 0) {
      await call('sim.cycles', { fileId: opened.fileId, n: fx.cycles });
      for (let i = 0; i < 200; i++) {
        const st = await call<{ cycle: number; running: boolean }>('sim.state', { fileId: opened.fileId });
        if (st.cycle >= fx.cycles || !st.running) break;
        await sleep(20);
      }
      await sleep(300); // the last step's dynamic check (the simulator's thread)
      const after = (await call<{ messages: Json[] }>('diag.list', { fileId: opened.fileId })).messages;
      entry.cycles = fx.cycles;
      entry.afterCycles = await Promise.all(after.map((m) => portable(opened.fileId, names, m)));
    }
    result[path.basename(fx.file)] = entry;
    await call('file.close', { fileId: opened.fileId });
  }
} finally {
  engine.stdin.end();
}
const out = path.join(root, 'tests/fixtures/messages.json');
writeFileSync(out, `${JSON.stringify(result, null, 2)}\n`);
console.log(`wrote ${path.relative(root, out)}: ${Object.keys(result).join(', ')}`);
