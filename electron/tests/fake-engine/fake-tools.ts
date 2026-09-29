/* The fake engine's part of N-21 (docs/engine-api.md model.history,
   edit.history, model.analyze, model.statistics, file.submission; D-162).

     model.history     this fake's undo steps (oldest first) and redo steps
                       (next first), named by the edit that made them (Add
                       AND Gate, Add Wire …: the original's names for those
                       edits); Start of History, Now
     edit.history      undo or redo that many times (the fake's own undo)
     model.analyze,    the real engine's answers for the circuits of the e2e
     model.statistics  samples (tests/fixtures/project-tools.json, written by
                       the engine's ProjectToolsFixtureTest), by the file's
                       name and the circuit's; any other circuit: no pins
                       (noInputs) and its parts counted by name
     file.submission   the checks and the files: the .circ, and the files
                       beside it the fake's own libraries name; with a path,
                       a zip of them

   No imports from src/: it says the protocol on its own. */

import { existsSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { deflateRawSync } from 'node:zlib';

type Params = Record<string, unknown>;
export interface ToolsStep { name?: string }
export interface ToolsComp { lib: string; name: string }
export interface ToolsCircuit { circuitId: string; name: string; comps: ToolsComp[] }
export interface ToolsFile { fileId: string; name: string; path: string | null; dirty: boolean; circuits: ToolsCircuit[]; undo: ToolsStep[]; redo: ToolsStep[] }

export interface ToolsCtx {
  fileOf(p: Params): ToolsFile;
  circuitOf(p: Params): ToolsCircuit;
  undoRedo(f: ToolsFile, which: 'undo' | 'redo'): { changed: boolean };
  fail(code: number, message: string, data?: unknown): never;
}

const FIXTURE = (() => {
  try { return JSON.parse(readFileSync(path.join(import.meta.dirname, '../fixtures/project-tools.json'), 'utf8')) as Record<string, Record<string, { analyze: unknown; statistics: unknown }>>; } catch { return {}; }
})();

// The original's action names for this fake's edits (tools.properties, gui.properties).
export function actionName(method: string, p: Params): string {
  switch (method) {
    case 'edit.addComponent': return `Add ${String(p.name ?? 'Component')}`;
    case 'edit.addWire': return 'Add Wire';
    case 'edit.move': return 'Move Selection';
    case 'edit.delete': return 'Delete Selection';
    case 'edit.setAttr': return `Change ${String(p.attr ?? 'Attribute')}`;
    case 'edit.paste': return 'Paste';
    case 'edit.duplicate': return 'Duplicate';
    case 'edit.rotate': return 'Change Facing';
    default: return 'Edit';
  }
}

// A zip of stored files (enough for the tests to read back its names and bytes).
function zip(entries: [string, Buffer][]): Buffer {
  const crcTable = Array.from({ length: 256 }, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
  const crc = (b: Buffer) => { let c = 0xffffffff; for (const x of b) c = crcTable[(c ^ x) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
  const locals: Buffer[] = [], centrals: Buffer[] = [];
  let offset = 0;
  for (const [name, data] of entries) {
    const n = Buffer.from(name, 'utf8');
    const body = deflateRawSync(data);
    const head = Buffer.alloc(30);
    head.writeUInt32LE(0x04034b50, 0); head.writeUInt16LE(20, 4); head.writeUInt16LE(0x0800, 6); head.writeUInt16LE(8, 8);
    head.writeUInt32LE(crc(data), 14); head.writeUInt32LE(body.length, 18); head.writeUInt32LE(data.length, 22); head.writeUInt16LE(n.length, 26);
    const cen = Buffer.alloc(46);
    cen.writeUInt32LE(0x02014b50, 0); cen.writeUInt16LE(20, 4); cen.writeUInt16LE(20, 6); cen.writeUInt16LE(0x0800, 8); cen.writeUInt16LE(8, 10);
    cen.writeUInt32LE(crc(data), 16); cen.writeUInt32LE(body.length, 20); cen.writeUInt32LE(data.length, 24); cen.writeUInt16LE(n.length, 28); cen.writeUInt32LE(offset, 42);
    locals.push(head, n, body);
    centrals.push(cen, n);
    offset += head.length + n.length + body.length;
  }
  const dir = Buffer.concat(centrals);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0); end.writeUInt16LE(entries.length, 8); end.writeUInt16LE(entries.length, 10); end.writeUInt32LE(dir.length, 12); end.writeUInt32LE(offset, 16);
  return Buffer.concat([...locals, dir, end]);
}

export function methods(ctx: ToolsCtx): Record<string, (p: Params) => unknown> {
  return {
    'model.history': (p) => {
      const f = ctx.fileOf(p);
      const n = f.undo.length;
      return {
        fileId: f.fileId,
        rows: [
          { kind: 'start', moves: -n },
          ...f.undo.map((s, i) => ({ kind: 'undo', name: s.name ?? 'Edit', moves: -(n - 1 - i) })),
          { kind: 'now', moves: 0 },
          ...[...f.redo].reverse().map((s, i) => ({ kind: 'redo', name: s.name ?? 'Edit', moves: i + 1 })),
        ],
      };
    },
    'edit.history': (p) => {
      const f = ctx.fileOf(p);
      const moves = Number(p.moves);
      if (!Number.isInteger(moves) || Math.abs(moves) > 10000) ctx.fail(-32602, 'moves must be an integer between -10000 and 10000');
      let changed = false;
      for (let i = 0; i < Math.abs(moves); i++) {
        if (!ctx.undoRedo(f, moves < 0 ? 'undo' : 'redo').changed) break;
        changed = true;
      }
      return changed ? { changed } : { changed, outcome: 'nothing' };
    },
    'model.analyze': (p) => {
      const f = ctx.fileOf(p);
      const c = ctx.circuitOf(p);
      const known = f.path ? FIXTURE[path.basename(f.path)]?.[c.name] : undefined;
      if (known) return known.analyze;
      return { circuit: c.name, inputs: [], outputs: [], maxInputs: 12, maxOutputs: 12, problem: 'noInputs', source: null };
    },
    'model.statistics': (p) => {
      const f = ctx.fileOf(p);
      const c = ctx.circuitOf(p);
      const known = f.path ? FIXTURE[path.basename(f.path)]?.[c.name] : undefined;
      if (known) return known.statistics;
      const counts = new Map<string, { component: string; library: string; simple: number; unique: number; recursive: number }>();
      for (const k of c.comps) {
        const r = counts.get(k.name) ?? { component: k.name, library: k.lib, simple: 0, unique: 0, recursive: 0 };
        r.simple++; r.unique++; r.recursive++;
        counts.set(k.name, r);
      }
      const total = c.comps.length;
      return { circuit: c.name, rows: [...counts.values()], without: { simple: total, unique: total, recursive: total }, with: { simple: total, unique: total, recursive: total } };
    },
    'file.submission': (p) => {
      const f = ctx.fileOf(p);
      const files = f.path ? [path.basename(f.path)] : [];
      const probes = f.circuits.reduce((n, c) => n + c.comps.filter((k) => k.name === 'Probe' || k.name === 'Radix Probe').length, 0);
      const out: Record<string, unknown> = {
        saved: f.path !== null, dirty: f.dirty, messages: 0, probes, bundledJar: false, missing: [], files,
        suggested: `${f.path ? path.basename(f.path).replace(/\.circ$/i, '') : f.name}-submission.zip`,
      };
      if (typeof p.path === 'string') {
        if (!f.path) ctx.fail(-32602, 'the file was never saved');
        let target = p.path;
        if (!target.toLowerCase().endsWith('.zip')) target += '.zip';
        const entries: [string, Buffer][] = files.map((n) => [n, existsSync(f.path!) && statSync(f.path!).isFile() ? readFileSync(f.path!) : Buffer.alloc(0)]);
        const bytes = zip(entries);
        writeFileSync(target, bytes);
        out.written = { path: target, name: path.basename(target), bytes: bytes.length, count: entries.length };
      }
      return out;
    },
  };
}
