/* The fake engine's attribute table, right-click menu facts and memories
   (N-10, D-157), from the real engine's answers in
   tests/fixtures/attributes.json (engine AttrFixtures, written by
   ./gradlew :engine:canvasFixtures):

     model.attributes  the part kind's rows as the real engine gives them
                       (names, editors, choices, Quick Attributes, the
                       original's number keys), with this fake's values;
                       the selection's common rows (the original's rule),
                       the circuit's, the tool's
     model.menu        demo-datapath's main circuit: the real engine's
                       facts for each part and wire (the fixture's ids),
                       with this fake's selection; elsewhere facts made
                       here from the parts' names and attributes
     a value the rows cannot take (not one of a list's choices, not a
     number where a number goes) is refused: -32602 badValue

   Plain Node, no imports from src/ (as the rest of the fake). */

import { readFileSync } from 'node:fs';
import path from 'node:path';

type Params = Record<string, unknown>;
export interface Comp { id: string; lib: string | null; name: string; loc: [number, number]; attrs: Record<string, string>; ext?: { color?: string } }
export interface Wire { id: string; a: [number, number]; b: [number, number] }
interface Row { attr: string; display: string; value: string | null; text: string; type: string; readOnly: boolean; mixed: boolean; options?: { value: string; display: string }[]; radix?: number; min?: number; max?: number }
interface Table { target: string; title: string; editable: boolean; rows: Row[]; quick?: { attrs: string[]; hints: unknown[]; rotate: boolean; label: boolean; count: number; autoAppearance?: string } }
interface Fixture { circuit: Table; kinds: Record<string, Table>; tools: Record<string, Table>; menus: Record<string, Record<string, Record<string, unknown>>> }

const FIXTURE: Fixture = JSON.parse(readFileSync(path.join(import.meta.dirname, '..', 'fixtures', 'attributes.json'), 'utf8')) as Fixture;

export class BadValue extends Error {
  readonly code = -32602;
  readonly data: Record<string, string>;
  constructor(attr: string, value: string) {
    super(`invalid value '${value}' for attribute '${attr}'`);
    this.data = { reason: 'badValue', attr, value };
  }
}

const kindKey = (k: Comp): string => `${k.lib === null || k.lib === 'circuit' ? '' : k.lib}/${k.name}`;
const kindOf = (k: Comp): Table | undefined => FIXTURE.kinds[kindKey(k)];
const shortTitle = (t: Table): string => t.title.replace(/^[A-Za-z]+: /, '');

// A row with a value: its text as the original table shows it (a list's display name, else the value).
function valued(r: Row, value: string | null, mixed = false): Row {
  const text = value === null ? '' : r.type === 'option' ? r.options?.find((o) => o.value === value)?.display ?? value : r.type === 'contents' ? r.text : value;
  return { ...r, value: r.type === 'contents' ? null : value, text, mixed };
}

// The part's value of an attribute: its own (a fixture part has them all), else the kind's default.
const valueOf = (k: Comp, r: Row): string | null => (r.attr in k.attrs ? k.attrs[r.attr] : r.value);

// model.attributes for the chosen parts (none: the circuit's rows).
export function selectionTable(chosen: (Comp | Wire)[], circuit: { circuitId: string; name: string }, editable: boolean): Table & { circuitId: string } {
  const parts = chosen.filter((x): x is Comp => !('a' in x));
  const comps = parts.length ? parts : [];
  if (!chosen.length || (!parts.length && !chosen.length)) return circuitTable(circuit, editable);
  if (!comps.length) {
    // wires only: the wire's rows, read only (the original's)
    return { circuitId: circuit.circuitId, target: 'selection', title: 'Selection: Wire', editable, rows: [] };
  }
  const first = kindOf(comps[0]);
  let rows: Row[] = (first?.rows ?? []).map((r) => valued(r, valueOf(comps[0], r)));
  for (const k of comps.slice(1)) {
    const kr = kindOf(k)?.rows ?? [];
    rows = rows.filter((r) => kr.some((x) => x.attr === r.attr)).map((r) => {
      const other = kr.find((x) => x.attr === r.attr)!;
      const v = valueOf(k, other);
      return r.value !== null && v !== r.value ? valued(r, null, true) : r;
    });
  }
  const kinds = new Set(comps.map(kindKey));
  const display = first ? shortTitle(first) : comps[0].name;
  const title = kinds.size > 1 ? `Selection: Various items × ${comps.length}` : comps.length === 1 ? `Selection: ${display}` : `Selection: ${display} × ${comps.length}`;
  const table: Table & { circuitId: string } = { circuitId: circuit.circuitId, target: 'selection', title, editable, rows };
  if (kinds.size === 1 && first?.quick) table.quick = { ...first.quick, count: comps.length };
  return table;
}

export function circuitTable(circuit: { circuitId: string; name: string }, editable: boolean): Table & { circuitId: string } {
  return {
    circuitId: circuit.circuitId, target: 'circuit', title: `Circuit: ${circuit.name}`, editable,
    rows: FIXTURE.circuit.rows.map((r) => (r.attr === 'circuit' ? valued(r, circuit.name) : r)),
  };
}

// model.attributes for a tool: the kind's rows with the tool's own values over its defaults.
export function toolTable(lib: string | null, name: string, own: Record<string, string>): Table & { lib: string | null; name: string } {
  const key = `${lib ?? ''}/${name}`;
  const kind = FIXTURE.tools[key] ?? FIXTURE.kinds[key];
  if (!kind) throw Object.assign(new Error(`no tool ${key}`), { code: 1 });
  return {
    target: 'tool', lib, name, title: `Tool: ${shortTitle(kind)}`, editable: true,
    rows: kind.rows.map((r) => valued(r, r.attr in own ? own[r.attr] : r.value)),
  };
}

// The original's parse, as far as the rows say: a list takes one of its choices, a number a number.
export function check(k: Comp, attr: string, value: string): void {
  const r = kindOf(k)?.rows.find((x) => x.attr === attr);
  if (!r) return;
  if (r.readOnly) throw Object.assign(new Error(`attribute '${attr}' is read-only`), { code: 3 });
  if (r.type === 'option' && r.options && !r.options.some((o) => o.value === value)) throw new BadValue(attr, value);
  if (r.type === 'number') {
    const n = r.radix === 16 ? (/^0x[0-9a-f]+$/i.test(value) || /^-?\d+$/.test(value) ? Number(value) : NaN) : /^-?\d+$/.test(value) ? Number(value) : NaN;
    if (!Number.isFinite(n) || (r.min !== undefined && n < r.min) || (r.max !== undefined && n > r.max)) throw new BadValue(attr, value);
  }
  if (r.type === 'font' && !/^.+\s+(plain|bold|italic|bolditalic)\s+\d+$/i.test(value)) throw new BadValue(attr, value);
}

// ---- model.menu ---------------------------------------------------------------------------------

const GATES = ['AND Gate', 'OR Gate', 'NAND Gate', 'NOR Gate', 'XOR Gate', 'XNOR Gate'];
const bits = (n: number): string => `${n} bit${n === 1 ? '' : 's'}`;

export interface MenuWorld {
  file: string;                       // the file's name without .circ (demo-datapath: the fixture's facts)
  fixtureCircuit: string | null;      // the circuit's id in the canvas fixture
  circuit: { circuitId: string; name: string };
  comps: Comp[];
  wires: Wire[];
  selected: string[];                 // the selection in this circuit, in the order chosen
  ordered: boolean;
  editable: boolean;
  netWidth(wire: string): number;
}

export function menuFacts(w: MenuWorld, at: [number, number], id: string | null): Record<string, unknown> {
  const parts = w.selected.filter((x) => w.comps.some((k) => k.id === x));
  const inSel = id === null || w.selected.includes(id);
  const hit = id ? (w.comps.find((k) => k.id === id) ?? w.wires.find((x) => x.id === id) ?? null) : null;
  const kind = parts.length >= 2 && inSel ? 'many' : !hit ? 'empty' : 'a' in hit ? 'wire' : 'part';
  const selection = { ids: [...w.selected], ordered: w.ordered, parts: parts.length, wires: w.selected.length - parts.length };
  const recorded = w.file === 'demo-datapath' && w.fixtureCircuit && id ? FIXTURE.menus['demo-datapath']?.[`${w.fixtureCircuit}|${id}`] : undefined;
  const out: Record<string, unknown> = { circuitId: w.circuit.circuitId, editable: w.editable, kind, selection };
  if (hit) out.id = hit.id;
  if (kind === 'many') {
    out.summary = `${parts.length} component${parts.length === 1 ? '' : 's'}`;
    const comps = parts.map((x) => w.comps.find((k) => k.id === x)!);
    const all = (a: string) => comps.every((k) => a in k.attrs || kindOf(k)?.rows.some((r) => r.attr === a));
    out.common = { facing: all('facing'), width: all('width'), label: all('label'), labels: comps.map((k) => k.attrs.label ?? '') };
  } else if (kind === 'empty') {
    out.summary = `Empty spot · ${w.circuit.name}`;
  } else if (recorded) {
    out.summary = recorded.summary;
    if (recorded.part) out.part = recorded.part;
    if (recorded.wire) out.wire = recorded.wire;
  } else if (kind === 'wire') {
    const width = w.netWidth(hit!.id);
    out.summary = width > 0 ? `Wire · ${bits(width)}` : 'Wire';
    out.wire = { width, net: '' };
  } else {
    out.part = partFacts(w, hit as Comp, at);
    const k = hit as Comp;
    out.summary = [k.attrs.label ? `${k.attrs.label}(${k.name})` : k.name, k.attrs.width ? bits(Number(k.attrs.width)) : ''].filter(Boolean).join(' · ');
  }
  if (!hit) out.probes = w.comps.filter((k) => k.name === 'Probe' || k.name === 'Radix Probe').map((k) => k.id);
  const wiresOnly = w.selected.length >= 2 && parts.length === 0;
  if (wiresOnly && inSel) out.combine = w.selected.map((x) => w.netWidth(x));
  return out;
}

function partFacts(w: MenuWorld, k: Comp, _at: [number, number]): Record<string, unknown> {
  const rows = kindOf(k)?.rows ?? [];
  const has = (a: string) => a in k.attrs || rows.some((r) => r.attr === a);
  const opt = (a: string) => rows.find((r) => r.attr === a && r.type === 'option')?.options?.map((o) => ({ ...o, checked: (k.attrs[a] ?? rows.find((r) => r.attr === a)?.value) === o.value }));
  const p: Record<string, unknown> = {
    name: k.name, display: k.name, ...(k.attrs.label ? { label: k.attrs.label } : {}),
    labelAttr: has('label'), facing: has('facing'), width: has('width'), inputs: has('inputs'),
    gate: GATES.includes(k.name) || k.name === 'NOT Gate' || k.name === 'Buffer',
    options: { ...(opt('size') ? { size: opt('size') } : {}), ...(opt('pull') ? { pull: opt('pull') } : {}) },
  };
  if (GATES.includes(k.name)) p.swaps = GATES.filter((g) => g !== k.name);
  if (k.name === 'Pin') p.pin = { output: k.attrs.output === 'true', tristate: k.attrs.tristate === 'true' };
  if (k.name === 'Tunnel' && k.attrs.label) {
    const same = w.comps.filter((x) => x.name === 'Tunnel' && x.attrs.label === k.attrs.label).sort((a, b) => a.loc[1] - b.loc[1] || a.loc[0] - b.loc[0]);
    p.tunnel = { same: same.map((x) => x.id), index: same.indexOf(k) };
  }
  if (k.name === 'Register' || k.name === 'Counter') p.pcMarked = false;
  if (k.name === 'RAM') p.memory = 'ram';
  if (k.name === 'ROM') p.memory = 'rom';
  if (k.name === 'Instruction Memory' || k.name === 'Data Memory') { p.memory = 'program'; if (k.attrs.source) p.source = k.attrs.source; }
  if (k.name === 'Splitter') p.original = [{ i: 0, text: 'Distribute Ascending', enabled: true }, { i: 1, text: 'Distribute Descending', enabled: true }];
  return p;
}

// ---- RAM and ROM contents (mem.*, edit.memContents) ------------------------------------------------

const memories = new Map<string, number[]>();   // by fileId and part: the words (sparse enough for the tests)

export function words(fileId: string, k: Comp): number[] {
  const key = `${fileId} ${k.id}`;
  let m = memories.get(key);
  if (!m) {
    const n = 2 ** Number(k.attrs.addrWidth ?? 8);
    m = new Array<number>(Math.min(n, 1 << 16)).fill(0);
    if (k.name === 'ROM' && k.attrs.contents) {
      const [, ...lines] = k.attrs.contents.split('\n');
      const vals = lines.join(' ').split(/\s+/).filter(Boolean).map((t) => parseInt(t, 16));
      vals.forEach((v, i) => { if (i < m!.length) m![i] = v; });
    }
    memories.set(key, m);
  }
  return m;
}

export function read(fileId: string, k: Comp, p: Params): Record<string, unknown> {
  const m = words(fileId, k);
  const from = Number(p.from ?? 0);
  const count = Number(p.count ?? 256);
  if (!Number.isInteger(count) || count < 1 || count > 4096) throw Object.assign(new Error('count must be 1..4096'), { code: -32602 });
  return { kind: k.name === 'ROM' ? 'rom' : 'ram', addrBits: Number(k.attrs.addrWidth ?? 8), dataBits: Number(k.attrs.dataWidth ?? 8), from, total: m.length, words: m.slice(from, from + count) };
}

export function write(fileId: string, k: Comp, addr: number, values: number[]): boolean {
  const m = words(fileId, k);
  const max = 2 ** Number(k.attrs.dataWidth ?? 8) - 1;
  if (values.some((v) => v < 0 || v > max)) throw new BadValue('contents', values.join(' '));
  let changed = false;
  values.forEach((v, i) => { if (m[addr + i] !== v) { m[addr + i] = v; changed = true; } });
  return changed;
}

export function clear(fileId: string, k: Comp): boolean {
  const m = words(fileId, k);
  const changed = m.some((v) => v !== 0);
  m.fill(0);
  return changed;
}
