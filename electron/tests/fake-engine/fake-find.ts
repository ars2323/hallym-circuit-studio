/* The fake engine's part of N-12 (docs/engine-api.md model.library,
   find.query, edit.tunnelColor, edit.splitterEdit, edit.splitterSplit;
   D-150).

     model.library   the real engine's library (tests/fixtures/library.json,
                     written by the engine: ./gradlew :engine:canvasFixtures):
                     for a file it has an answer for, that one, else a new
                     file's (the bundled Hallym MIPS not in the file yet --
                     until a part of it is placed); this file's circuits first
                     under this fake's ids
     find.query      the real engine's answer for the texts in
                     tests/fixtures/find.json (by the file's name; places by
                     circuit name and the part's name and place, here this
                     fake's ids); any other text: labels and tunnels of each
                     circuit by a plain part of the name, no instances
     edit.tunnelColor  every tunnel of that name in the circuit gets ext.color
     edit.splitterEdit, edit.splitterSplit
                     the ranges read simply ("7:4, 3:0", "4x8", a name after
                     a range), the splitter's fanout/incoming/bitN and
                     ext.arms; a new splitter is a plain record at `at`

   No imports from src/: it says the protocol on its own. */

import { readFileSync } from 'node:fs';
import path from 'node:path';

type Params = Record<string, unknown>;
export interface Comp { id: string; lib: string; name: string; loc: [number, number]; attrs: Record<string, string>; ext?: { color?: string; arms?: string[] }; ports?: { i: number; loc: [number, number] }[] }
export interface Circ { circuitId: string; name: string; comps: Comp[]; wires: { id: string; a: [number, number]; b: [number, number] }[] }
export interface FindFile { name: string; path: string | null; circuits: Circ[]; mipsIn?: boolean }

interface LibGroup { lib: string | null; display?: string; pending?: boolean; tools: { name: string; display: string; circuitId?: string }[] }
const LIBRARY = (() => {
  try { return JSON.parse(readFileSync(path.join(import.meta.dirname, '../fixtures/library.json'), 'utf8')) as Record<string, LibGroup[]>; } catch { return {} as Record<string, LibGroup[]>; }
})();
export const MIPS_LIB = 'kr.ac.hallym.hcs.mips.MipsLibrary';

export function library(f: FindFile): LibGroup[] {
  const base = (f.path && LIBRARY[path.basename(f.path)]) || LIBRARY.new || [];
  const own: LibGroup = { lib: null, display: f.name, tools: f.circuits.map((c) => ({ name: c.name, display: c.name, circuitId: c.circuitId })) };
  return [own, ...base.filter((g) => g.lib !== null).map((g) => (g.lib === MIPS_LIB && f.mipsIn ? { ...g, pending: undefined } : g))];
}

// ---- find.query -----------------------------------------------------------------------------

interface Place { name: string; loc: [number, number] }
interface FixturePlace { circuitId: string; root: string; path: Place[]; at: [number, number]; place: string; near: boolean; component: Place }
interface FixtureGroup { kind: string; text: string; path: string; places: FixturePlace[] }
const FIND = (() => {
  try { return JSON.parse(readFileSync(path.join(import.meta.dirname, '../fixtures/find.json'), 'utf8')) as Record<string, Record<string, { groups: FixtureGroup[]; more: boolean }>>; } catch { return {}; }
})();

const same = (a: [number, number], b: [number, number]) => a[0] === b[0] && a[1] === b[1];

export function findQuery(fileId: string, f: FindFile, text: string): unknown {
  const recorded = f.path ? FIND[path.basename(f.path)]?.[text] : undefined;
  if (recorded) {
    const byName = (n: string) => f.circuits.find((c) => c.name === n);
    const groups = recorded.groups.map((g) => ({
      ...g,
      places: g.places.map((p) => {
        const root = byName(p.root);
        const pathIds: string[] = [];
        let at = root;
        for (const x of p.path) {
          const k = at?.comps.find((y) => y.name === x.name && same(y.loc, x.loc));
          pathIds.push(k?.id ?? '');
          at = k ? byName(k.name) : undefined;
        }
        const c = byName(p.circuitId);
        return {
          circuitId: c?.circuitId ?? '', root: root?.circuitId ?? '', path: pathIds,
          componentId: c?.comps.find((k) => k.name === p.component.name && same(k.loc, p.component.loc))?.id ?? '',
          at: p.at, place: p.place, near: p.near,
        };
      }),
    }));
    return { fileId, text, groups, more: recorded.more };
  }
  const q = text.trim().toLowerCase();
  const groups: unknown[] = [];
  if (q) {
    for (const c of f.circuits) {
      const by = new Map<string, Comp[]>();
      for (const k of c.comps) {
        const label = k.attrs.label ?? '';
        if (label && label.toLowerCase().includes(q)) by.set(`${k.name}\u0000${label}`, [...(by.get(`${k.name}\u0000${label}`) ?? []), k]);
      }
      for (const [key, list] of by) {
        const [name, label] = key.split('\u0000');
        const kind = name === 'Tunnel' ? 'tunnel' : name === 'Pin' ? 'pin' : 'label';
        list.sort((a, b) => a.loc[1] - b.loc[1] || a.loc[0] - b.loc[0]);
        groups.push({
          kind, text: label, path: `${c.name} › ${label}`,
          places: list.map((k) => ({ circuitId: c.circuitId, root: c.circuitId, path: [], componentId: k.id, at: k.loc, place: `${c.name} › ${label}`, near: false })),
        });
      }
    }
  }
  return { fileId, text, groups, more: false };
}

// ---- edit.tunnelColor --------------------------------------------------------------------------

export function tunnelColor(c: Circ, p: Params): Comp[] {
  const t = c.comps.find((k) => k.id === p.id);
  const label = t?.attrs.label ?? '';
  if (!t || t.name !== 'Tunnel' || !label) throw Object.assign(new Error(`component ${String(p.id)} is not a tunnel with a label`), { code: -32602 });
  const color = typeof p.color === 'string' ? p.color.toUpperCase() : undefined;
  const changed: Comp[] = [];
  for (const k of c.comps) {
    if (k.name !== 'Tunnel' || (k.attrs.label ?? '') !== label) continue;
    if (color) k.ext = { ...k.ext, color };
    else if (k.ext) { const { color: _gone, ...rest } = k.ext; k.ext = Object.keys(rest).length ? rest : undefined; }
    changed.push(k);
  }
  return changed;
}

// ---- the Splitter editor -----------------------------------------------------------------------

// "7:4 hi, 3:0 lo" or "4x8": the arms (bits) and the names in them.
function readRanges(text: string, width: number): { arms: number[][]; names: string[] } {
  const rep = /^\s*(\d+)\s*[xX×]\s*(\d+)\s*$/.exec(text);
  if (rep) {
    const n = Number(rep[1]), each = Number(rep[2]);
    return { arms: Array.from({ length: n }, (_, i) => Array.from({ length: each }, (__, k) => i * each + k)), names: [] };
  }
  const arms: number[][] = [];
  const names: string[] = [];
  for (const part of text.split(',')) {
    const m = /^\s*(\d+)\s*(?::\s*(\d+))?\s*([A-Za-z_][\w.]*)?\s*$/.exec(part);
    if (!m) throw Object.assign(new Error(`cannot read the ranges '${text}'`), { code: -32602 });
    const a = Number(m[1]), b = m[2] === undefined ? a : Number(m[2]);
    const bits: number[] = [];
    for (let i = Math.min(a, b); i <= Math.max(a, b); i++) if (i < width) bits.push(i);
    arms.push(bits);
    names.push(m[3] ?? '');
  }
  return { arms, names };
}

function applyRanges(k: Comp, p: Params, width: number): void {
  const r = readRanges(String(p.ranges ?? ''), width);
  const order = r.arms.map((a, i) => ({ a, n: r.names[i] ?? '' })).sort((x, y) => (p.lsbTop ? Math.max(...x.a) - Math.max(...y.a) : Math.max(...y.a) - Math.max(...x.a)));
  const given = Array.isArray(p.names) ? p.names.map(String) : [];
  const names = order.map((o, i) => given[i] ?? o.n);
  const attrs: Record<string, string> = { ...k.attrs, fanout: String(order.length), incoming: String(width) };
  for (let b = 0; b < width; b++) {
    const i = order.findIndex((o) => o.a.includes(b));
    attrs[`bit${b}`] = i < 0 ? 'none' : String(i);
  }
  k.attrs = attrs;
  k.ext = names.some((n) => n) ? { ...k.ext, arms: names } : undefined;
}

export function splitterEdit(c: Circ, p: Params): Comp[] {
  const k = c.comps.find((x) => x.id === p.id);
  if (!k || k.name !== 'Splitter') throw Object.assign(new Error(`component ${String(p.id)} is not a splitter`), { code: -32602 });
  applyRanges(k, p, Number(k.attrs.incoming ?? 2));
  return [k];
}

export function splitterSplit(c: Circ, p: Params, width: number, id: string): Comp {
  const at = p.at as [number, number];
  const k: Comp = { id, lib: 'Wiring', name: 'Splitter', loc: [Math.round(at[0] / 10) * 10, Math.round(at[1] / 10) * 10], attrs: { facing: 'east' } };
  applyRanges(k, p, width);
  c.comps.push(k);
  return k;
}
