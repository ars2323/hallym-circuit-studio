/* The fake engine's part of N-11 (docs/engine-api.md "회로·모양·라이브러리·다른
   파일"; D-153): plain records, nothing like Logisim's rules -- the real
   engine's are in engine/ (CircuitsTest, AppearanceEditTest, LibrariesTest)
   and tests/e2e/real-engine-circuits.e2e.ts.

     file.info, file.changed        the circuits (order, names), main, libraries
     edit.createCircuit, setMainCircuit, setCircuitAttr (circuit), deleteCircuit
                                    (lastCircuit, inUse), moveCircuit
     model.ports, edit.portOrder, edit.autoAppearance
                                    the pins by facing; an instance anywhere and
                                    a default appearance: needsConfirm first
     model.instances, model.pinImpact  main's direct instances
     model.appearance, edit.appearance, model.appearanceHit / Handles / Menu
                                    shapes as records (a default box, its ports,
                                    the anchor on top); hits by bounds.  A file
                                    with an appearance fixture (demo-datapath:
                                    tests/fixtures/appearance/, the real engine's
                                    answers, ./gradlew :engine:canvasFixtures)
                                    starts from the engine's shapes, ports and
                                    instance paths
     model.libraries, edit.loadLibrary, edit.unloadLibrary, file.peek,
     model.importPlan, edit.importCircuits, file.saveImpact, file.originOf,
     file.copyMipsJar
   Every change is one undo step of the file's state (the fake's edit.undo),
   with file.changed and model.appearance after the answer.

   No imports from src/. */

import { readFileSync } from 'node:fs';
import path from 'node:path';

type Params = Record<string, unknown>;
type P = [number, number];
export interface CComp { id: string; lib: string | null; name: string; loc: P; attrs: Record<string, string>; subcircuit?: string }
export interface CCircuit { circuitId: string; name: string; comps: CComp[]; wires: { id: string; a: P; b: P }[] }
export interface Shape { kind: string; attrs: Record<string, string>; bounds: [number, number, number, number]; points?: P[]; text?: string; at?: P; pin?: P; input?: boolean; name?: string; facing?: string; svg?: unknown }

// The real engine's answers for a file (tests/fixtures/appearance/<name>.json), by circuit name.
interface FixtureCircuit { appearance: { default: boolean; shapes: FixtureShape[] }; ports: unknown; instances: unknown }
interface FixtureShape { kind: string; svg?: unknown; attrs: Record<string, string>; bounds: [number, number, number, number]; points?: P[]; text?: string; at?: P; port?: { input: boolean; pin?: P; name?: string; at: P }; facing?: string }
const FIXTURES = path.join(import.meta.dirname, '..', 'fixtures', 'appearance');
function fixtureOf(f: CFile): Record<string, FixtureCircuit> | null {
  if (!f.path) return null;
  try { return (JSON.parse(readFileSync(path.join(FIXTURES, path.basename(f.path).replace(/\.circ$/i, '.json')), 'utf8')) as { circuits: Record<string, FixtureCircuit> }).circuits; } catch { return null; }
}
function fromFixture(x: FixtureShape): Shape {
  return {
    kind: x.kind, attrs: { ...x.attrs }, bounds: x.bounds, ...(x.points ? { points: x.points } : {}), ...(x.text !== undefined ? { text: x.text } : {}),
    ...(x.at ? { at: x.at } : x.port ? { at: x.port.at } : {}), ...(x.port ? { pin: x.port.pin, input: x.port.input, name: x.port.name } : {}),
    ...(x.facing ? { facing: x.facing } : {}), ...(x.svg ? { svg: x.svg } : {}),
  };
}
export interface Appear { default: boolean; shapes: Shape[] }
export interface CFile {
  fileId: string; name: string; path: string | null; circuits: CCircuit[]; main: string; libs: string[]; dirty: boolean;
  undo: unknown[]; redo: unknown[];
  appear?: Map<string, Appear>;          // by circuit name
  watchedAppear?: Set<string>;           // circuit ids model.appearance was asked for
}
export interface FileState { circuits: CCircuit[]; main: string; libs: string[]; appear: [string, Appear][] }

export interface Ctx {
  files: Map<string, CFile>;
  fileOf(p: Params): CFile;
  circuitOf(p: Params): CCircuit;
  notify(method: string, params: unknown): void;
  fail(code: number, message: string, data?: unknown): never;
  nextCircuit(): string;
  nextComp(): string;
  readCirc(text: string): { circuits: CCircuit[]; main: string; libs: string[] };
  libRefs(f: CFile): unknown;
  builtins: string[];
}

const BUILTIN_DISPLAY = (lib: string) => (lib === 'I/O' ? 'Input/Output' : lib);

export function snapshot(f: CFile): FileState {
  return { circuits: structuredClone(f.circuits), main: f.main, libs: [...f.libs], appear: structuredClone([...(f.appear ?? new Map()).entries()]) };
}
export function restore(f: CFile, s: FileState): void {
  f.circuits = s.circuits;
  f.main = s.main;
  f.libs = s.libs;
  f.appear = new Map(s.appear);
}

const mainOf = (f: CFile) => f.circuits.find((c) => c.name === f.main) ?? f.circuits[0];

export function fileInfo(ctx: Ctx, f: CFile): Record<string, unknown> {
  return {
    fileId: f.fileId, name: f.name, circuits: f.circuits.map((c) => ({ circuitId: c.circuitId, name: c.name })),
    main: mainOf(f)?.circuitId ?? '', libraries: ctx.libRefs(f), dirty: f.dirty,
  };
}

// After a change: file.changed, and the appearances asked for.
export function told(ctx: Ctx, f: CFile): void {
  setImmediate(() => {
    ctx.notify('file.changed', fileInfo(ctx, f));
    for (const id of f.watchedAppear ?? []) {
      const c = f.circuits.find((x) => x.circuitId === id);
      if (c) ctx.notify('model.appearance', appearanceJson(f, c));
    }
  });
}

function step(ctx: Ctx, f: CFile, change: () => void, result: Record<string, unknown> = {}): Record<string, unknown> {
  const before = snapshot(f);
  change();
  f.undo.push({ circuitId: '', comps: [], wires: [], file: before });
  f.redo = [];
  f.dirty = true;
  told(ctx, f);
  return { changed: true, ...result };
}

// ---- appearances ----

function pins(c: CCircuit): CComp[] { return c.comps.filter((k) => k.name === 'Pin'); }
function sideOf(k: CComp): string {
  const facing = k.attrs.facing ?? 'east';
  return ({ east: 'west', west: 'east', north: 'south', south: 'north' } as Record<string, string>)[facing] ?? 'west';
}
const isOutput = (k: CComp) => k.attrs.output === 'true' || k.attrs.type === 'output';

function defaultAppear(c: CCircuit): Appear {
  const ps = pins(c);
  const west = ps.filter((k) => sideOf(k) === 'west');
  const east = ps.filter((k) => sideOf(k) === 'east');
  const rows = Math.max(1, west.length, east.length);
  const h = rows * 10 + 10;
  const shapes: Shape[] = [{ kind: 'rect', attrs: { 'stroke-width': '2', stroke: '#000000', fill: '#ffffff', paintType: 'stroke' }, bounds: [50, 50, 30, h] }];
  west.forEach((k, i) => shapes.push({ kind: 'port', attrs: {}, bounds: [46, 56 + i * 10, 8, 8], at: [50, 60 + i * 10], pin: k.loc, input: !isOutput(k), name: k.attrs.label ?? '' }));
  east.forEach((k, i) => shapes.push({ kind: 'port', attrs: {}, bounds: [75, 55 + i * 10, 10, 10], at: [80, 60 + i * 10], pin: k.loc, input: !isOutput(k), name: k.attrs.label ?? '' }));
  shapes.push({ kind: 'anchor', attrs: { facing: 'east' }, bounds: [77, 57, 6, 6], at: [80, 60], facing: 'east' });
  return { default: true, shapes };
}

function appearOf(f: CFile, c: CCircuit): Appear {
  f.appear ??= new Map();
  let a = f.appear.get(c.name);
  const fx = fixtureOf(f)?.[c.name];
  if (!a && fx) { a = { default: fx.appearance.default, shapes: fx.appearance.shapes.map(fromFixture) }; f.appear.set(c.name, a); }
  if (!a || (a.default && !fx)) { a = defaultAppear(c); f.appear.set(c.name, a); }
  return a;
}

function handlesOf(s: Shape): P[] {
  if (s.points) return s.points;
  if (s.kind === 'port' || s.kind === 'anchor' || s.kind === 'text') return [s.at ?? [s.bounds[0], s.bounds[1]]];
  const [x, y, w, h] = s.bounds;
  return [[x, y], [x + w, y], [x + w, y + h], [x, y + h]];
}

function svgOf(s: Shape): unknown {
  if (s.svg) return s.svg;   // the engine's own, until the shape changes here
  const a = s.attrs;
  const stroke = { stroke: a.stroke ?? '#000000', 'stroke-width': a['stroke-width'] ?? '1', fill: a.paintType === 'fill' || a.paintType === 'both' ? a.fill ?? '#ffffff' : 'none' };
  const [x, y, w, h] = s.bounds;
  switch (s.kind) {
    case 'rect': case 'roundrect': return { tag: 'rect', attrs: { x: String(x), y: String(y), width: String(w), height: String(h), ...(s.kind === 'roundrect' ? { rx: a.rx ?? '10' } : {}), ...stroke } };
    case 'oval': return { tag: 'ellipse', attrs: { cx: String(x + w / 2), cy: String(y + h / 2), rx: String(w / 2), ry: String(h / 2), ...stroke } };
    case 'line': case 'polyline': case 'polygon': return { tag: s.kind === 'polygon' ? 'polygon' : 'polyline', attrs: { points: (s.points ?? []).map((p) => p.join(',')).join(' '), ...stroke } };
    case 'curve': { const [e0, e1, c] = s.points ?? []; return { tag: 'path', attrs: { d: `M${e0[0]},${e0[1]} Q${c[0]},${c[1]} ${e1[0]},${e1[1]}`, ...stroke } }; }
    case 'text': return { tag: 'text', attrs: { x: String(s.at?.[0] ?? x), y: String(s.at?.[1] ?? y), 'font-family': 'SansSerif', 'font-size': '12', 'text-anchor': a.align === 'left' ? 'start' : a.align === 'right' ? 'end' : 'middle', fill: a.fill ?? '#000000' }, text: s.text ?? '' };
    default: return undefined;
  }
}

export function appearanceJson(f: CFile, c: CCircuit): Record<string, unknown> {
  const a = appearOf(f, c);
  return {
    fileId: f.fileId, circuitId: c.circuitId, name: c.name, default: a.default, editable: true,
    shapes: a.shapes.map((s, i) => ({
      i, kind: s.kind, ...(svgOf(s) ? { svg: svgOf(s) } : {}), attrs: s.attrs, handles: handlesOf(s), moves: handlesOf(s).map(() => s.kind !== 'text'),
      bounds: s.bounds, removable: s.kind !== 'port' && s.kind !== 'anchor',
      ...(s.points ? { points: s.points, closed: s.kind === 'polygon' } : {}), ...(s.text !== undefined ? { text: s.text } : {}), ...(s.at ? { at: s.at } : {}),
      ...(s.kind === 'port' ? { port: { input: s.input ?? true, pin: s.pin, name: s.name ?? '', width: 1, at: s.at } } : {}),
      ...(s.kind === 'anchor' ? { facing: s.facing ?? 'east' } : {}),
    })),
  };
}

function boundsOf(pts: P[]): [number, number, number, number] {
  const xs = pts.map((p) => p[0]), ys = pts.map((p) => p[1]);
  return [Math.min(...xs), Math.min(...ys), Math.max(...xs) - Math.min(...xs), Math.max(...ys) - Math.min(...ys)];
}

// Keeps the ports and the anchor on top (the original's AppearanceCanvas.getMaxIndex).
function firstElement(a: Appear): number {
  const i = a.shapes.findIndex((s) => s.kind === 'port' || s.kind === 'anchor');
  return i < 0 ? a.shapes.length : i;
}

function translate(s: Shape, dx: number, dy: number): void {
  delete s.svg;
  s.bounds = [s.bounds[0] + dx, s.bounds[1] + dy, s.bounds[2], s.bounds[3]];
  if (s.points) s.points = s.points.map((p) => [p[0] + dx, p[1] + dy]);
  if (s.at) s.at = [s.at[0] + dx, s.at[1] + dy];
}

const clipboard: Shape[] = [];

function appearanceOp(ctx: Ctx, f: CFile, c: CCircuit, p: Params): Record<string, unknown> {
  const op = String(p.op);
  const cur = appearOf(f, c);
  const shapesParam = (Array.isArray(p.shapes) ? p.shapes : []) as number[];
  const n = cur.shapes.length;
  for (const i of shapesParam) if (i < 0 || i >= n) ctx.fail(1, `no such shape: ${i}`, { kind: 'shape', id: String(i) });
  const edit = (change: (a: Appear) => number[] | void, extra: Record<string, unknown> = {}) => {
    let sel: number[] = shapesParam;
    const r = step(ctx, f, () => {
      const a = appearOf(f, c);
      a.default = false;
      const out = change(a);
      if (out) sel = out;
    }, extra);
    return { ...r, selected: sel };
  };
  switch (op) {
    case 'add': {
      const sh = p.shape as { kind: string; bounds?: [number, number, number, number]; points?: P[]; at?: P; text?: string };
      if (!sh || typeof sh.kind !== 'string') ctx.fail(-32602, 'param \'shape\' must be an object');
      if (sh.kind === 'text' && !sh.text) return { changed: false, outcome: 'empty' };
      const attrs = { ...(p.attrs as Record<string, string> ?? {}) };
      const s: Shape = sh.bounds ? { kind: sh.kind, attrs, bounds: sh.bounds }
        : sh.points ? { kind: sh.kind, attrs, bounds: boundsOf(sh.points), points: sh.points }
          : { kind: 'text', attrs, bounds: [sh.at![0] - 10, sh.at![1] - 12, 20, 14], at: sh.at, text: sh.text };
      let index = 0;
      const r = edit((a) => { index = firstElement(a); a.shapes.splice(index, 0, s); return [index]; });
      return { ...r, index };
    }
    case 'move': return edit((a) => { for (const i of shapesParam) translate(a.shapes[i], Number(p.dx), Number(p.dy)); });
    case 'handle': return edit((a) => {
      const s = a.shapes[Number(p.shape)];
      const at = p.at as P;
      delete s.svg;
      if (s.points) s.points = s.points.map((q) => (q[0] === at[0] && q[1] === at[1] ? [q[0] + Number(p.dx), q[1] + Number(p.dy)] : q));
      else { const [x, y, w, h] = s.bounds; s.bounds = [x, y, Math.max(1, w + Number(p.dx)), Math.max(1, h + Number(p.dy))]; }
      return [Number(p.shape)];
    });
    case 'delete': case 'cut': {
      const gone = shapesParam.filter((i) => cur.shapes[i].kind !== 'port' && cur.shapes[i].kind !== 'anchor');
      if (!gone.length) return { changed: false, outcome: 'nothing', selected: shapesParam };
      if (op === 'cut') { clipboard.length = 0; clipboard.push(...gone.map((i) => structuredClone(cur.shapes[i]))); }
      return edit((a) => { a.shapes = a.shapes.filter((_, i) => !gone.includes(i)); return []; });
    }
    case 'copy': clipboard.length = 0; clipboard.push(...shapesParam.map((i) => structuredClone(cur.shapes[i])).filter((s) => s.kind !== 'port' && s.kind !== 'anchor')); return { changed: true, selected: shapesParam };
    case 'paste': case 'duplicate': {
      const src = op === 'paste' ? clipboard : shapesParam.map((i) => cur.shapes[i]).filter((s) => s.kind !== 'port' && s.kind !== 'anchor');
      if (!src.length) return { changed: false, outcome: op === 'paste' ? 'emptyClipboard' : 'nothing', selected: shapesParam };
      return edit((a) => {
        const at = firstElement(a);
        const copies = src.map((s) => { const x = structuredClone(s); translate(x, 10, 10); return x; });
        a.shapes.splice(at, 0, ...copies);
        return copies.map((_, k) => at + k);
      });
    }
    case 'raise': case 'lower': case 'raiseTop': case 'lowerBottom': return edit((a) => {
      const max = firstElement(a) - 1;
      const moving = shapesParam.filter((i) => i <= max).sort((x, y) => x - y);
      const rest = a.shapes.slice(0, max + 1).filter((_, i) => !moving.includes(i));
      const top = op === 'raiseTop' || op === 'raise';
      const ordered = top ? [...rest, ...moving.map((i) => a.shapes[i])] : [...moving.map((i) => a.shapes[i]), ...rest];
      a.shapes = [...ordered, ...a.shapes.slice(max + 1)];
      return moving.map((_, k) => (top ? rest.length + k : k));
    });
    case 'addVertex': case 'removeVertex': return edit((a) => {
      const s = a.shapes[Number(p.shape)];
      if (!s.points) return;
      const at = p.at as P;
      if (op === 'addVertex') s.points = [...s.points.slice(0, 1), at, ...s.points.slice(1)];
      else s.points = s.points.filter((q) => q[0] !== at[0] || q[1] !== at[1]);
      return [Number(p.shape)];
    });
    case 'setAttr': return edit((a) => { for (const i of shapesParam) { delete a.shapes[i].svg; a.shapes[i].attrs = { ...a.shapes[i].attrs, [String(p.attr)]: String(p.value) }; } });
    case 'text': return edit((a) => {
      const i = Number(p.shape);
      if (String(p.text) === '') { a.shapes.splice(i, 1); return []; }
      a.shapes[i].text = String(p.text);
      return [i];
    });
    case 'revert': {
      if (cur.default) return { changed: false, outcome: 'same' };
      return { ...step(ctx, f, () => { f.appear!.set(c.name, defaultAppear(c)); }), selected: [] };
    }
    default: return ctx.fail(-32602, `op ${op}`);
  }
}

// ---- the methods ----

export function methods(ctx: Ctx): Record<string, (p: Params) => unknown> {
  const circuitNamed = (f: CFile, name: string) => f.circuits.find((c) => c.name === name);
  const usersOf = (f: CFile, c: CCircuit) => f.circuits.flatMap((k) => k.comps.filter((x) => (x.lib === 'circuit' && x.name === c.name) || x.subcircuit === c.circuitId).map((x) => ({ parent: k, comp: x })));
  return {
    'file.info': (p) => ({ ...fileInfo(ctx, ctx.fileOf(p)), saved: ctx.fileOf(p).path !== null, readOnly: false }),
    'edit.createCircuit': (p) => {
      const f = ctx.fileOf(p);
      const name = String(p.name ?? '').trim();
      if (!name) ctx.fail(-32602, 'a circuit needs a name', { reason: 'nameMissing' });
      if (circuitNamed(f, name)) ctx.fail(-32602, `there is a circuit named ${name}`, { reason: 'nameTaken' });
      const c: CCircuit = { circuitId: ctx.nextCircuit(), name, comps: [], wires: [] };
      return step(ctx, f, () => f.circuits.push(c), { circuitId: c.circuitId });
    },
    'edit.setMainCircuit': (p) => {
      const f = ctx.fileOf(p);
      const c = ctx.circuitOf(p);
      if (f.main === c.name) return { changed: false, outcome: 'same' };
      return step(ctx, f, () => { f.main = c.name; });
    },
    'edit.setCircuitAttr': (p) => {
      const f = ctx.fileOf(p);
      const c = ctx.circuitOf(p);
      if (p.attr !== 'circuit') return step(ctx, f, () => {});
      const name = String(p.value);
      const old = c.name;
      return step(ctx, f, () => {
        for (const u of usersOf(f, c)) u.comp.name = name;
        if (f.main === old) f.main = name;
        const a = f.appear?.get(old);
        if (a) { f.appear!.delete(old); f.appear!.set(name, a); }
        c.name = name;
      });
    },
    'edit.deleteCircuit': (p) => {
      const f = ctx.fileOf(p);
      const c = ctx.circuitOf(p);
      if (f.circuits.length <= 1) ctx.fail(3, 'a file keeps at least one circuit', { reason: 'lastCircuit' });
      if (usersOf(f, c).length) ctx.fail(3, `another circuit uses ${c.name}`, { reason: 'inUse' });
      return step(ctx, f, () => {
        f.circuits = f.circuits.filter((x) => x !== c);
        if (f.main === c.name) f.main = f.circuits[0].name;
      });
    },
    'edit.moveCircuit': (p) => {
      const f = ctx.fileOf(p);
      const c = ctx.circuitOf(p);
      const to = Number(p.to);
      if (!(to >= 0 && to < f.circuits.length)) ctx.fail(-32602, 'to is out of range');
      if (f.circuits.indexOf(c) === to) return { changed: false, outcome: 'same' };
      return step(ctx, f, () => { f.circuits = f.circuits.filter((x) => x !== c); f.circuits.splice(to, 0, c); });
    },
    'model.ports': (p) => {
      const f = ctx.fileOf(p);
      const c = ctx.circuitOf(p);
      const fx = fixtureOf(f)?.[c.name];
      if (fx) return { ...(fx.ports as object), circuitId: c.circuitId, default: appearOf(f, c).default };
      const sides: Record<string, unknown[]> = { west: [], east: [], north: [], south: [] };
      for (const k of pins(c).sort((a, b) => a.loc[1] - b.loc[1] || a.loc[0] - b.loc[0])) sides[sideOf(k)].push({ name: k.attrs.label ?? '', width: Number(k.attrs.width ?? 1), input: !isOutput(k) });
      return { circuitId: c.circuitId, name: c.name, default: appearOf(f, c).default, sides, instances: usersOf(f, c).length };
    },
    'edit.portOrder': (p) => {
      const f = ctx.fileOf(p);
      const c = ctx.circuitOf(p);
      if (!pins(c).length) return { changed: false, outcome: 'noPorts' };
      const users = usersOf(f, c);
      if (users.length && appearOf(f, c).default && p.confirm === false) {
        return { changed: false, outcome: 'needsConfirm', impact: { instances: users.length, connections: users.length * 2, where: users.map((u, i) => `${u.parent.name} › ${c.name} #${i + 1}`) } };
      }
      return step(ctx, f, () => { appearOf(f, c).default = false; });
    },
    'edit.autoAppearance': (p) => {
      const f = ctx.fileOf(p);
      const c = ctx.circuitOf(p);
      if (!pins(c).length) return { changed: false, outcome: 'noPorts' };
      const users = usersOf(f, c);
      if (users.length && appearOf(f, c).default && p.confirm === false) {
        return { changed: false, outcome: 'needsConfirm', impact: { instances: users.length, connections: users.length, where: users.map((u, i) => `${u.parent.name} › ${c.name} #${i + 1}`) } };
      }
      return step(ctx, f, () => { appearOf(f, c).default = false; });
    },
    'model.instances': (p) => {
      const f = ctx.fileOf(p);
      const c = ctx.circuitOf(p);
      const fx = fixtureOf(f)?.[c.name];
      if (fx) return { ...(fx.instances as object), circuitId: c.circuitId, default: appearOf(f, c).default };
      const main = mainOf(f);
      const users = usersOf(f, c);
      const direct = main && main !== c ? main.comps.filter((x) => x.lib === 'circuit' && x.name === c.name) : [];
      return {
        circuitId: c.circuitId, main: main?.circuitId ?? null, mainName: main?.name ?? null,
        paths: direct.map((x, i) => ({ ids: [x.id], names: [x.attrs.label || `${c.name} #${i + 1}`], circuits: [c.circuitId], text: `${main!.name} › ${x.attrs.label || c.name}` })),
        instances: users.length, connected: users.length * 2, default: appearOf(f, c).default,
      };
    },
    'model.pinImpact': (p) => {
      const f = ctx.fileOf(p);
      const c = ctx.circuitOf(p);
      const ids = (p.ids as string[]) ?? [];
      const users = usersOf(f, c).length;
      return { connections: users ? ids.length * users : 0, instances: users };
    },
    'model.appearance': (p) => {
      const f = ctx.fileOf(p);
      const c = ctx.circuitOf(p);
      (f.watchedAppear ??= new Set()).add(c.circuitId);
      return appearanceJson(f, c);
    },
    'edit.appearance': (p) => appearanceOp(ctx, ctx.fileOf(p), ctx.circuitOf(p), p),
    'model.appearanceHit': (p) => {
      const f = ctx.fileOf(p);
      const a = appearOf(f, ctx.circuitOf(p));
      const out: Record<string, unknown> = { top: null, topFilled: null };
      const at = p.at as P | undefined;
      if (at) {
        for (const i of (p.selected as number[] | undefined) ?? []) {
          const s = a.shapes[i];
          const h = s && handlesOf(s).find((q) => Math.abs(q[0] - at[0]) <= 4 && Math.abs(q[1] - at[1]) <= 4);
          if (h && s.kind !== 'text') { out.handle = { shape: i, at: h }; break; }
        }
        const inside = (s: Shape) => at[0] >= s.bounds[0] - 2 && at[0] <= s.bounds[0] + s.bounds[2] + 2 && at[1] >= s.bounds[1] - 2 && at[1] <= s.bounds[1] + s.bounds[3] + 2;
        for (let i = a.shapes.length - 1; i >= 0; i--) if (inside(a.shapes[i])) { out.top = i; out.topFilled = i; break; }
      }
      const r = p.rect as number[] | undefined;
      if (r) {
        const [x0, x1] = [Math.min(r[0], r[2]), Math.max(r[0], r[2])], [y0, y1] = [Math.min(r[1], r[3]), Math.max(r[1], r[3])];
        out.inRect = a.shapes.map((s, i) => (s.bounds[0] >= x0 && s.bounds[1] >= y0 && s.bounds[0] + s.bounds[2] <= x1 && s.bounds[1] + s.bounds[3] <= y1 ? i : -1)).filter((i) => i >= 0);
      }
      return out;
    },
    'model.appearanceHandles': (p) => {
      const f = ctx.fileOf(p);
      const s = appearOf(f, ctx.circuitOf(p)).shapes[Number(p.shape)];
      const at = p.at as P;
      return { handles: handlesOf(s).map((q) => (q[0] === at[0] && q[1] === at[1] ? [q[0] + Number(p.dx), q[1] + Number(p.dy)] : q)) };
    },
    'model.appearanceMenu': (p) => {
      const f = ctx.fileOf(p);
      const a = appearOf(f, ctx.circuitOf(p));
      const sel = ((p.shapes as number[] | undefined) ?? []).map((i) => a.shapes[i]).filter(Boolean);
      const removable = sel.some((s) => s.kind !== 'port' && s.kind !== 'anchor');
      return {
        cut: removable, copy: sel.length > 0, paste: clipboard.length > 0, delete: removable, duplicate: sel.length > 0,
        raise: removable, lower: removable, raiseTop: removable, lowerBottom: removable, addVertex: p.vertexAt !== undefined, removeVertex: p.vertexAt !== undefined,
      };
    },
    'model.libraries': (p) => {
      const f = ctx.fileOf(p);
      const have = f.libs.length ? f.libs : ctx.builtins;
      const used = (lib: string) => f.circuits.find((c) => c.comps.some((k) => k.lib === lib))?.name ?? null;
      return {
        fileId: f.fileId,
        builtins: ctx.builtins.filter((b) => !have.includes(b)).map((b) => ({ name: b, display: BUILTIN_DISPLAY(b) })),
        loaded: have.map((b) => ({ name: b, display: BUILTIN_DISPLAY(b), usedIn: used(b) })),
        openFiles: [...ctx.files.values()].filter((o) => o !== f).map((o) => ({
          fileId: o.fileId, name: o.path ? path.basename(o.path) : `${o.name}.circ`, state: o.path === null ? 'unsaved' : have.includes(path.basename(o.path, '.circ')) ? 'loaded' : 'ok',
          ...(o.path && have.includes(path.basename(o.path, '.circ')) ? { lib: path.basename(o.path, '.circ') } : {}),
          circuits: o.circuits.map((c) => c.name), main: mainOf(o)?.name ?? null,
        })),
        mips: false,
      };
    },
    'edit.loadLibrary': (p) => {
      const f = ctx.fileOf(p);
      const have = f.libs.length ? f.libs : [...ctx.builtins];
      const name = p.kind === 'builtin' ? String(p.name) : path.basename(String(p.path), p.kind === 'jar' ? '.jar' : '.circ');
      if (p.kind === 'builtin' && !ctx.builtins.includes(name)) ctx.fail(-32602, `no built-in library ${name}`);
      if (have.includes(name)) return { changed: false, outcome: 'already' };
      return step(ctx, f, () => { f.libs = [...have, name]; }, { lib: name });
    },
    'edit.unloadLibrary': (p) => {
      const f = ctx.fileOf(p);
      const have = f.libs.length ? f.libs : [...ctx.builtins];
      const name = String(p.name);
      if (!have.includes(name)) ctx.fail(1, `no library ${name}`, { kind: 'library', id: name });
      const user = f.circuits.find((c) => c.comps.some((k) => k.lib === name));
      if (user) ctx.fail(3, `Circuit '${user.name}' uses components from this library.`, { reason: 'inUse', circuit: user.name });
      return step(ctx, f, () => { f.libs = have.filter((l) => l !== name); });
    },
    'file.peek': (p) => {
      const r = ctx.readCirc(readFileSync(String(p.path), 'utf8'));
      return { name: path.basename(String(p.path)), main: r.main, circuits: r.circuits.map((c) => ({ name: c.name, uses: [...new Set(c.comps.filter((k) => k.lib === 'circuit').map((k) => k.name))] })) };
    },
    'model.importPlan': (p) => importPlan(ctx, ctx.fileOf(p), String(p.path), p.circuits as string[]),
    'edit.importCircuits': (p) => {
      const f = ctx.fileOf(p);
      const plan = importPlan(ctx, f, String(p.path), p.circuits as string[]);
      const src = ctx.readCirc(readFileSync(String(p.path), 'utf8'));
      return step(ctx, f, () => {
        for (const o of plan.order) {
          const c = src.circuits.find((x) => x.name === o.name)!;
          f.circuits.push({ ...c, circuitId: ctx.nextCircuit(), name: o.as, comps: c.comps.map((k) => ({ ...k, id: ctx.nextComp() })) });
        }
      }, { plan });
    },
    // other open files that use this one as a library (by its name) and have its parts: 2 connections a part
    'file.saveImpact': (p) => {
      const f = ctx.fileOf(p);
      const lib = f.path ? path.basename(f.path, '.circ') : null;
      const cuts = [];
      for (const o of lib ? ctx.files.values() : []) {
        if (o === f || !o.libs.includes(lib!)) continue;
        const parts = o.circuits.flatMap((c) => c.comps.filter((k) => k.lib === lib).map((k) => k.attrs.label || k.name));
        if (parts.length) cuts.push({ fileId: o.fileId, file: o.path ? path.basename(o.path) : o.name, instances: parts, connections: parts.length * 2 });
      }
      return { cuts };
    },
    'file.originOf': (p) => ({ path: null, circuit: ctx.circuitOf(p).name }),
    'file.copyMipsJar': (p) => {
      const f = ctx.fileOf(p);
      if (!f.path) ctx.fail(-32602, 'the file was never saved');
      return { name: 'hcs-mips.jar' };
    },
  };
}

// A file saved: the open files that use it as a library take the new version (file.libraryUpdated).
export function saved(ctx: Ctx, f: CFile): void {
  if (!f.path) return;
  const lib = path.basename(f.path, '.circ');
  for (const o of ctx.files.values()) {
    if (o !== f && o.libs.includes(lib)) setImmediate(() => ctx.notify('file.libraryUpdated', { fileId: o.fileId, library: lib }));
  }
}

// Pins deleted from a circuit other circuits use: their instances lose a connection each (model.portImpact).
export function pinsRemoved(ctx: Ctx, f: CFile, c: CCircuit, removed: readonly CComp[]): void {
  const n = removed.filter((k) => k.name === 'Pin').length;
  if (!n) return;
  const users = f.circuits.flatMap((k) => k.comps.filter((x) => (x.lib === 'circuit' && x.name === c.name) || x.subcircuit === c.circuitId));
  if (!users.length) return;
  setImmediate(() => ctx.notify('model.portImpact', { fileId: f.fileId, circuitId: c.circuitId, name: c.name, broken: n * users.length, kept: 0 }));
}

function importPlan(ctx: Ctx, f: CFile, file: string, names: string[]): { order: { name: string; as: string }[]; skipped: string[] } {
  const src = ctx.readCirc(readFileSync(file, 'utf8'));
  const order: string[] = [];
  const visit = (n: string) => {
    if (order.includes(n)) return;
    const c = src.circuits.find((x) => x.name === n);
    if (!c) ctx.fail(-32602, `that file has no circuit ${n}`);
    for (const k of c!.comps) if (k.lib === 'circuit') visit(k.name);
    order.push(n);
  };
  for (const n of names) visit(n);
  const used = new Set(f.circuits.map((c) => c.name));
  return {
    order: order.map((n) => {
      let as = n, k = 2;
      while (used.has(as)) as = `${n}-${k++}`;
      used.add(as);
      return { name: n, as };
    }),
    skipped: [],
  };
}
