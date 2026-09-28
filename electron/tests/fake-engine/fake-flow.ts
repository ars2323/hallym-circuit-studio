/* The fake engine's overlay data (docs/engine-api.md "flow·trace", N-15,
   D-151): for a circuit with a fixture in tests/fixtures/flow/ (demo-
   datapath: written by the real engine, ./gradlew :engine:canvasFixtures,
   with the ids of tests/fixtures/circuits/) the real engine's answers to
   trace.influence, flow.path, flow.activePath (by cycle), trace.net and
   record.fieldPaths (by cycle); for anything else an empty answer of the
   same shape.  Signal groups and area memos (edit.signalGroup,
   edit.areaMemo) are kept here as plain lists, as the engine's hcs:ext
   would hold them.  Plain Node, no imports from src/. */

import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';

type Params = Record<string, unknown>;
type J = Record<string, unknown>;

export interface FlowFixture {
  influence: Record<string, J>;
  flow: Record<string, J>;
  net: Record<string, J>;
  activePath: J[];
  fieldPaths: J[];
}

const DIR = path.join(import.meta.dirname, '..', 'fixtures', 'flow');

export function load(fileName: string): FlowFixture | undefined {
  const f = path.join(DIR, fileName.replace(/\.circ$/i, '.json'));
  return existsSync(f) ? (JSON.parse(readFileSync(f, 'utf8')) as FlowFixture) : undefined;
}

// The fixture's circuit ids in an answer, as this file's (a recovered file keeps the old engine's).
function circuits(v: unknown, current: (fixtureId: string) => string): unknown {
  if (Array.isArray(v)) return v.map((x) => circuits(x, current));
  if (v && typeof v === 'object') {
    const o: J = {};
    for (const [k, x] of Object.entries(v as J)) o[k] = k === 'circuitId' && typeof x === 'string' ? current(x) : circuits(x, current);
    return o;
  }
  return v;
}

export function influence(fx: FlowFixture | undefined, fixtureCircuit: string, p: Params): J {
  const from = (Array.isArray(p.from) ? p.from.map(String) : []);
  if (!from.length) throw Object.assign(new Error("'from' must name at least one component or wire"), { code: -32602 });
  const mode = String(p.mode ?? '');
  if (!['forward', 'backward', 'both', 'between'].includes(mode)) throw Object.assign(new Error('mode must be forward, backward, both or between'), { code: -32602 });
  const through = p.throughRegisters === true;
  const depth = typeof p.depth === 'number' ? p.depth : -1;
  const key = (d: number) => `${fixtureCircuit}|${[...from].sort().join(',')}|${mode}|${through}|${d}`;
  const hit = fx?.influence[key(depth)] ?? (depth >= 0 ? fx?.influence[key(-1)] : undefined);
  if (hit) return hit;
  return { mode, depth: -1, maxDepth: 0, throughRegisters: through, forward: { wires: [], parts: [] }, backward: { wires: [], parts: [] }, stops: [], origin: from, inside: [], tunnels: [], links: [] };
}

export function flowPath(fx: FlowFixture | undefined, fixtureCircuit: string, currentCircuit: string, p: Params, current: (id: string) => string): J {
  const id = typeof p.wire === 'string' ? p.wire : String(p.componentId ?? '');
  const port = typeof p.port === 'number' ? p.port : -1;
  const back = p.backward === true, through = p.throughRegisters === true, active = p.activePathOnly === true;
  const key = (pt: number, t: boolean, a: boolean) => `${fixtureCircuit}|${id}|${pt}|${back}|${t}|${a}`;
  const hit = fx?.flow[key(port, through, active)] ?? fx?.flow[key(port, through, false)] ?? fx?.flow[key(port, false, false)] ?? fx?.flow[key(-1, false, false)];
  const out = hit ? (circuits(hit, current) as J) : { circuitId: currentCircuit, backward: back, total: 0, segments: [], jumps: [], passes: [], endpoints: [], loops: [], undetermined: [] };
  // a wire's click point is the one asked for (the fixture pressed each wire's middle)
  if (typeof p.wire === 'string' && Array.isArray(p.at)) out.click = p.at;
  return out;
}

export function activePath(fx: FlowFixture | undefined, fixtureCircuit: string, currentCircuit: string, cycle: number, current: (id: string) => string): J {
  const list = fx?.activePath ?? [];
  const hit = list[Math.min(cycle, list.length - 1)];
  if (!hit || hit.circuitId !== fixtureCircuit) return { circuitId: currentCircuit, muxes: [], watched: true };
  return circuits(hit, current) as J;
}

export function fieldPaths(fx: FlowFixture | undefined, fixtureCircuit: string, fileId: string, currentCircuit: string, cycle: number, current: (id: string) => string): J {
  const list = fx?.fieldPaths ?? [];
  const hit = list[Math.min(cycle, list.length - 1)];
  if (!hit || hit.circuitId !== fixtureCircuit) return { fileId, circuitId: currentCircuit, cycle, fields: {} };
  return { fileId, ...(circuits(hit, current) as J) };
}

export function net(fx: FlowFixture | undefined, nets: { id: string; wires: string[] }[], p: Params): J {
  let id = typeof p.netId === 'string' ? p.netId : '';
  if (typeof p.wire === 'string') id = nets.find((n) => n.wires.includes(String(p.wire)))?.id ?? '';
  const hit = fx?.net[id];
  if (!hit) throw Object.assign(new Error(`no such net: ${id || String(p.wire)}`), { code: 1, data: { kind: 'net', id } });
  return hit;
}

// ---- signal groups and area memos (hcs:ext) ------------------------------------------------------

export interface Ext { groups: { net: string; group: string; assigned: boolean }[]; memos: { x: number; y: number; w: number; h: number; color: number; text: string }[] }

export const emptyExt = (): Ext => ({ groups: [], memos: [] });

export function setGroup(ext: Ext, netId: string, group: string | undefined): boolean {
  if (group !== undefined && !['control', 'data', 'address'].includes(group)) throw Object.assign(new Error('group must be control, data or address'), { code: -32602 });
  const now = ext.groups.find((g) => g.net === netId);
  if ((now?.group ?? undefined) === group) return false;
  ext.groups = ext.groups.filter((g) => g.net !== netId);
  if (group) ext.groups.push({ net: netId, group, assigned: true });
  return true;
}

// The memo that holds the point, the smallest when they overlap (the engine's AreaMemos.at).
const memoAt = (ext: Ext, x: number, y: number) => ext.memos.filter((m) => x >= m.x && x <= m.x + m.w && y >= m.y && y <= m.y + m.h)
  .sort((a, b) => a.w * a.h - b.w * b.h)[0];

export function areaMemo(ext: Ext, p: Params, around: (ids: string[]) => { x: number; y: number; w: number; h: number } | null): 'added' | 'edited' | 'deleted' | 'same' | 'noMemo' {
  const at = p.at as [number, number];
  if (!Array.isArray(at)) throw Object.assign(new Error("missing point param 'at'"), { code: -32602 });
  const color = p.color;
  if (color !== undefined && (typeof color !== 'number' || color < 0 || color > 11)) throw Object.assign(new Error('color must be 0..11'), { code: -32602 });
  const b = p.bounds as number[] | undefined;
  if (b !== undefined && (!Array.isArray(b) || b.length !== 4 || b[2] < 20 || b[3] < 20)) throw Object.assign(new Error('bounds must be [x, y, w, h] with w and h from 20'), { code: -32602 });
  const text = typeof p.text === 'string' ? p.text.trim() : undefined;
  const ids = Array.isArray(p.ids) ? p.ids.map(String) : [];
  const here = memoAt(ext, at[0], at[1]);
  if (p.delete === true) {
    if (!here) return 'noMemo';
    ext.memos = ext.memos.filter((m) => m !== here);
    return 'deleted';
  }
  const box = b ? { x: b[0], y: b[1], w: b[2], h: b[3] } : ids.length ? around(ids) : null;
  if (here) {
    const after = { ...here, ...(box ?? {}), ...(color !== undefined ? { color } : {}), ...(text !== undefined ? { text } : {}) };
    if (JSON.stringify(after) === JSON.stringify(here)) return 'same';
    ext.memos = ext.memos.map((m) => (m === here ? after : m));
    return 'edited';
  }
  const start = box ?? { x: Math.floor((at[0] - 100) / 10) * 10, y: Math.floor((at[1] - 60) / 10) * 10, w: 200, h: 120 };
  ext.memos = [...ext.memos, { ...start, color: typeof color === 'number' ? color : ext.memos.length % 12, text: text ?? '' }];
  return 'added';
}
