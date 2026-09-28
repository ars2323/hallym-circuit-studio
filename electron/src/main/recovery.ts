/* Crash recovery without disk (N-04, D-142, docs/engine-api.md 7).

   The engine (a Java process) has the circuits; if it dies, what the
   student did since the last save is gone with it -- unless the main
   process can do it again.  So the main process keeps, in memory only
   (the lab-PC rule: nothing on disk but the student's own files):

     Shadow    its own copy of what the window was shown: every component's
               and wire's library, name, place and attributes, from the
               engine's answers (model.circuit) and changes (model.changed).
     Journal   per open file, how it was opened (its path, or file.new)
               and every edit intent the window sent since it was opened
               or last saved, in the order the engine answered them.  An
               intent names parts by the engine's ids ("k17"), which a new
               engine does not know; so each id is written down as the part
               it named (its Ref: an AND Gate at (200,100) with these
               attributes; the wire from (a) to (b)), looked up in the
               shadow at the moment the engine answered -- the model just
               before that edit (the engine sends an edit's model.changed
               after its answer).

   When the engine dies and starts again (engine.ts: at most 3 times in
   60 s), the Supervisor
     1. holds the window's calls back (settled()),
     2. opens every file again -- its path, or a new file -- with the old
        engine's file and circuit ids (file.open/new `restore`; the new
        engine's own ids start above every id the window has seen:
        engine.hello `idFloor`), so the window's tabs, the circuit on show
        and its view stay as they were,
     3. replays every journal entry, all files together in their original
        order (the clipboard is shared), each Ref found again in the new
        model,
     4. tells the window what came back (the 'recovered' event: the dialog
        and the band).
   A file whose replay fails is closed and opened as last saved; if the
   engine dies again during a recovery, the next one opens the last saved
   versions only.  Either way the band lists the files whose unsaved edits
   could not be restored.  A file that cannot be opened again (gone from
   disk) is closed.  The simulation is not restored: it starts from Reset.

   Nothing here knows Electron: tests/unit/recovery.test.ts drives it with
   the fake engine. */

import { createHash } from 'node:crypto';
import { EventEmitter } from 'node:events';
import { readFileSync } from 'node:fs';

import { EngineError, EngineGone, type Answer, type EngineClient } from './engine.ts';
import type { CircuitRef, EngineStatus, LibraryGroup, Recovered, Snapshot } from './protocol.ts';

type Point = [number, number];

// A part named by what it is, not by an engine's id.
export type Ref =
  | { kind: 'component'; lib: string | null; name: string; loc: Point; attrs: Record<string, string> }
  | { kind: 'wire'; a: Point; b: Point };

// The parameters that carry part ids (docs/engine-api.md 5 edit): a list, or one.
// A new edit parameter that names parts goes here too.
export const ID_PARAMS: Readonly<Record<string, 'list' | 'one'>> = { ids: 'list', id: 'one', componentId: 'one', wire: 'one' };

// The methods that change a file's model: edit intents (docs/engine-api.md 5)
// and loading an executable image (mips.load, N-16: replayed from the same
// .hmx path; a load that named its memories by id -- target, picks -- is not
// found again in a new engine, and that file comes back as last saved).
export const journaled = (method: string): boolean => method.startsWith('edit.') || method === 'mips.load';

// The window's calls carry this tag (main.ts); the Supervisor's own, 'recovery'.
export const WINDOW = 'window';
const RECOVERY = 'recovery';

const isObject = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null && !Array.isArray(v);
const samePoint = (p: Point, q: Point) => p[0] === q[0] && p[1] === q[1];
const sameAttrs = (a: Record<string, string>, b: Record<string, string>) => {
  const ka = Object.keys(a);
  return ka.length === Object.keys(b).length && ka.every((k) => a[k] === b[k]);
};

export function sameRef(x: Ref, y: Ref): boolean {
  if (x.kind === 'wire' && y.kind === 'wire') return (samePoint(x.a, y.a) && samePoint(x.b, y.b)) || (samePoint(x.a, y.b) && samePoint(x.b, y.a));
  if (x.kind === 'component' && y.kind === 'component') {
    return x.lib === y.lib && x.name === y.name && samePoint(x.loc, y.loc) && sameAttrs(x.attrs, y.attrs);
  }
  return false;
}

// A component or wire of a snapshot or of model.changed's `added`, as a Ref.
export function refOf(part: unknown): Ref | null {
  if (!isObject(part)) return null;
  if (Array.isArray(part.a) && Array.isArray(part.b)) return { kind: 'wire', a: [...part.a] as Point, b: [...part.b] as Point };
  if (typeof part.name === 'string' && Array.isArray(part.loc)) {
    return { kind: 'component', lib: (part.lib as string | null) ?? null, name: part.name, loc: [...part.loc] as Point, attrs: { ...(part.attrs as Record<string, string> ?? {}) } };
  }
  return null;
}

// ---- the shadow ---------------------------------------------------------------------------

interface ShadowCircuit {
  name: string | null;
  loaded: boolean;              // a snapshot came: every part is known
  parts: Map<string, Ref>;
}

const ID = /^[fckw](\d{1,15})$/;

export class Shadow {
  private readonly files = new Map<string, Map<string, ShadowCircuit>>();
  private max = 0;

  // Every id number the window has seen stays below the next engine's (engine.hello idFloor).
  idFloor(): number { return this.max; }

  private circuit(fileId: string, circuitId: string): ShadowCircuit {
    let f = this.files.get(fileId);
    if (!f) { f = new Map(); this.files.set(fileId, f); }
    let c = f.get(circuitId);
    if (!c) { c = { name: null, loaded: false, parts: new Map() }; f.set(circuitId, c); }
    return c;
  }

  private scan(v: unknown, depth = 0): void {
    if (depth > 8) return;
    if (typeof v === 'string') {
      const m = ID.exec(v);
      if (m) this.max = Math.max(this.max, Number(m[1]));
    } else if (Array.isArray(v)) {
      for (const x of v) this.scan(x, depth + 1);
    } else if (isObject(v)) {
      for (const x of Object.values(v)) this.scan(x, depth + 1);
    }
  }

  private circuits(fileId: string, refs: CircuitRef[]): void {
    for (const r of refs) this.circuit(fileId, r.circuitId).name = r.name;
  }

  answer(method: string, params: unknown, result: unknown): void {
    this.scan(result);
    const p = isObject(params) ? params : {};
    const r = isObject(result) ? result : {};
    if ((method === 'file.new' || method === 'file.open') && typeof r.fileId === 'string' && Array.isArray(r.circuits)) {
      this.circuits(r.fileId, r.circuits as CircuitRef[]);
    } else if (method === 'model.circuit' && typeof p.fileId === 'string' && typeof p.circuitId === 'string') {
      const s = result as Snapshot;
      const c = this.circuit(p.fileId, p.circuitId);
      c.name = s.name ?? c.name;
      c.loaded = true;
      c.parts = new Map();
      for (const part of [...(s.components ?? []), ...(s.wires ?? [])]) {
        const ref = refOf(part);
        if (ref) c.parts.set(part.id, ref);
      }
    } else if (method === 'model.library' && typeof p.fileId === 'string' && Array.isArray(result)) {
      const own = (result as LibraryGroup[]).find((g) => g.lib === null);
      for (const t of own?.tools ?? []) if (t.circuitId) this.circuit(p.fileId, t.circuitId).name = t.name;
    } else if (method === 'file.close' && typeof p.fileId === 'string') {
      this.files.delete(p.fileId);
    }
  }

  notification(method: string, params: unknown): void {
    if (method !== 'model.changed' || !isObject(params)) return;
    this.scan(params.added);
    if (typeof params.fileId !== 'string' || typeof params.circuitId !== 'string') return;
    const c = this.circuit(params.fileId, params.circuitId);
    for (const id of (params.removed as string[] | undefined) ?? []) c.parts.delete(id);
    for (const part of (params.added as { id: string }[] | undefined) ?? []) {
      const ref = refOf(part);
      if (ref) c.parts.set(part.id, ref);
    }
  }

  describe(fileId: string, circuitId: string, id: string): Ref | null {
    const ref = this.files.get(fileId)?.get(circuitId)?.parts.get(id);
    return ref ? structuredClone(ref) : null;
  }

  // The ids of parts matching refs, one part each (two identical parts in
  // one place are two matches); null if one is not there.
  find(fileId: string, circuitId: string, refs: Ref[]): string[] | null {
    const parts = [...(this.files.get(fileId)?.get(circuitId)?.parts ?? [])];
    const taken = new Set<string>();
    const out: string[] = [];
    for (const ref of refs) {
      const hit = parts.find(([id, r]) => !taken.has(id) && sameRef(r, ref));
      if (!hit) return null;
      taken.add(hit[0]);
      out.push(hit[0]);
    }
    return out;
  }

  loaded(fileId: string, circuitId: string): boolean { return this.files.get(fileId)?.get(circuitId)?.loaded ?? false; }
  hasCircuit(fileId: string, circuitId: string): boolean { return this.files.get(fileId)?.has(circuitId) ?? false; }
  circuitName(fileId: string, circuitId: string): string | null { return this.files.get(fileId)?.get(circuitId)?.name ?? null; }
  circuitNamed(fileId: string, name: string): string | null {
    for (const [id, c] of this.files.get(fileId) ?? []) if (c.name === name) return id;
    return null;
  }
  // {name: circuitId} of a file (for file.open/new `restore`).
  circuitIds(fileId: string): Record<string, string> {
    const out: Record<string, string> = {};
    for (const [id, c] of this.files.get(fileId) ?? []) if (c.name !== null && !(c.name in out)) out[c.name] = id;
    return out;
  }
  forget(fileId: string): void { this.files.delete(fileId); }
  clear(): void { this.files.clear(); }
}

// ---- the journal --------------------------------------------------------------------------

export type Opened = { kind: 'path'; path: string; readOnly: boolean } | { kind: 'new' };

export interface Entry {
  seq: number;                              // the order the engine answered, over every file
  method: string;
  params: Record<string, unknown>;          // as the window sent them
  circuit: { id: string; name: string | null } | null;
  refs: Record<string, Ref | Ref[]>;        // the ID_PARAMS in params, as parts
}

export interface FileJournal {
  fileId: string;
  opened: Opened;
  fingerprint: string | null;               // the file's bytes when opened or saved (sha-256)
  circuits: Record<string, string>;         // {name: circuitId} when opened or saved
  entries: Entry[];
  broken: string | null;                    // an intent that could not be written down: no replay
}

export class Journal {
  readonly files = new Map<string, FileJournal>();   // in the order they were opened
  private seq = 0;

  opened(fileId: string, opened: Opened, circuits: Record<string, string>, fingerprint: string | null): void {
    this.files.set(fileId, { fileId, opened, fingerprint, circuits, entries: [], broken: null });
  }

  // Saved: the file on disk is the model now.  Its edits start over.
  saved(fileId: string, path: string, circuits: Record<string, string>, fingerprint: string | null): void {
    const f = this.files.get(fileId);
    if (!f) return;
    const readOnly = false; // saving to a path makes it writable (docs/engine-api.md file.save)
    Object.assign(f, { opened: { kind: 'path', path, readOnly }, fingerprint, circuits, entries: [], broken: null });
  }

  closed(fileId: string): void { this.files.delete(fileId); }

  // An edit the engine answered (the shadow is the model just before it).
  record(fileId: string, method: string, params: unknown, shadow: Shadow): void {
    const f = this.files.get(fileId);
    if (!f || f.broken) return;
    const p = structuredClone(isObject(params) ? params : {});
    const circuitId = typeof p.circuitId === 'string' ? p.circuitId : null;
    const refs: Record<string, Ref | Ref[]> = {};
    for (const [key, shape] of Object.entries(ID_PARAMS)) {
      if (!(key in p)) continue;
      const ids = shape === 'list' ? p[key] : [p[key]];
      if (!Array.isArray(ids) || !ids.every((x) => typeof x === 'string') || circuitId === null) {
        f.broken = `${method}: ${key} without a circuit`;
        return;
      }
      const found = (ids as string[]).map((id) => shadow.describe(fileId, circuitId, id));
      const missing = (ids as string[]).find((_, i) => found[i] === null);
      if (missing !== undefined) {
        f.broken = `${method}: ${missing} was never shown`;
        return;
      }
      refs[key] = shape === 'list' ? (found as Ref[]) : found[0]!;
    }
    this.seq += 1;
    f.entries.push({ seq: this.seq, method, params: p, circuit: circuitId === null ? null : { id: circuitId, name: shadow.circuitName(fileId, circuitId) }, refs });
  }

  unsaved(fileId: string): number { return this.files.get(fileId)?.entries.length ?? 0; }
}

// The file's bytes, to see whether it changed on disk since (null: not there).
export function fingerprintOf(path: string): string | null {
  try {
    return createHash('sha256').update(readFileSync(path)).digest('hex');
  } catch {
    return null;
  }
}

// ---- the supervisor -----------------------------------------------------------------------

// A replay step that could not be done (a part not found, a circuit gone).
class ReplayError extends Error {}

interface Events {
  status: [EngineStatus];      // the engine's status as the window should see it (view())
  recovered: [Recovered];
}

export interface SupervisorOptions {
  fingerprint?: (path: string) => string | null;
}

export class Supervisor extends EventEmitter<Events> {
  readonly shadow = new Shadow();
  readonly journal = new Journal();
  private readonly engine: EngineClient;
  private readonly fingerprint: (path: string) => string | null;
  private gate: { promise: Promise<void>; open: () => void } | null = null;
  private recovering = false;
  private run = 0;               // the recovery under way (a newer one supersedes it)
  private crashes = 0;           // engine ends since the last finished recovery
  private handled = 1;           // the last generation recovered (the first needs none)
  private last: EngineStatus['state'] = 'stopped';

  constructor(engine: EngineClient, options: SupervisorOptions = {}) {
    super();
    this.engine = engine;
    this.fingerprint = options.fingerprint ?? fingerprintOf;
    engine.on('answer', (a) => this.answered(a));
    engine.on('notification', (method, params) => this.shadow.notification(method, params));
    engine.on('status', (s) => this.status(s));
  }

  // Resolves when no recovery is pending: the window's calls wait for it.
  settled(): Promise<void> { return this.gate?.promise ?? Promise.resolve(); }

  // Recovering: the window's notifications of the engine are held back (the
  // replay's model.changed are of ids it never had).
  quiet(): boolean { return this.recovering; }

  // The engine's status as the window sees it: restarting until recovered.
  view(s: EngineStatus): EngineStatus {
    return this.recovering && s.state === 'ready' ? { ...s, state: 'restarting' } : s;
  }

  // For engine.hello (engine.ts helloParams): ids from here on are above every id seen.
  helloParams(): Record<string, unknown> {
    const floor = this.shadow.idFloor();
    return floor > 0 ? { idFloor: floor } : {};
  }

  private answered(a: Answer): void {
    const p = isObject(a.params) ? a.params : {};
    const r = isObject(a.result) ? a.result : {};
    if (a.tag === WINDOW) {
      if (journaled(a.method) && typeof p.fileId === 'string') {
        this.journal.record(p.fileId, a.method, p, this.shadow);
      } else if (a.method === 'file.new' && typeof r.fileId === 'string') {
        this.journal.opened(r.fileId, { kind: 'new' }, circuitIds(r.circuits), null);
      } else if (a.method === 'file.open' && typeof r.fileId === 'string' && r.alreadyOpen !== true && typeof p.path === 'string') {
        this.journal.opened(r.fileId, { kind: 'path', path: p.path, readOnly: p.readOnly === true }, circuitIds(r.circuits), this.fingerprint(p.path));
      }
    }
    // Saved or closed by anyone (the main process saves for the window).
    if (a.method === 'file.save' && typeof p.fileId === 'string' && a.tag !== RECOVERY) {
      const target = typeof r.path === 'string' ? r.path : String(p.path ?? '');
      // The circuits' names now (the shadow's), and any it does not know from when the file was opened.
      const now = this.shadow.circuitIds(p.fileId);
      const known = new Set(Object.values(now));
      for (const [name, id] of Object.entries(this.journal.files.get(p.fileId)?.circuits ?? {})) if (!known.has(id) && !(name in now)) now[name] = id;
      this.journal.saved(p.fileId, target, now, this.fingerprint(target));
    } else if (a.method === 'file.close' && typeof p.fileId === 'string' && a.tag !== RECOVERY) {
      this.journal.closed(p.fileId);
    }
    this.shadow.answer(a.method, a.params, a.result);
  }

  private openGate(): void {
    if (this.gate) return;
    let open!: () => void;
    const promise = new Promise<void>((done) => { open = done; });
    this.gate = { promise, open };
  }

  private releaseGate(): void {
    const g = this.gate;
    this.gate = null;
    g?.open();
  }

  private status(s: EngineStatus): void {
    const prev = this.last;
    this.last = s.state;
    if (s.state === 'restarting' && prev !== 'restarting') this.crashes += 1;
    if (s.state === 'restarting' || (s.state === 'starting' && s.generation > 0)) this.openGate();
    if (s.state === 'failed' || s.state === 'stopped') {
      this.run += 1;              // a recovery under way is over
      this.recovering = false;
      this.releaseGate();
    }
    if (s.state === 'ready' && s.generation > this.handled) {
      this.handled = s.generation;
      void this.recover(s.generation);
    }
    this.emit('status', this.view(s));
  }

  private async recover(generation: number): Promise<void> {
    const run = ++this.run;
    this.recovering = true;
    this.openGate();
    const replay = this.crashes <= 1;
    try {
      const report = await this.reopen(replay, generation);
      if (run !== this.run) return;
      this.crashes = 0;
      this.recovering = false;
      this.emit('recovered', report);
      this.releaseGate();
      this.emit('status', this.view(this.engine.status()));
    } catch (e) {
      // The engine ended again (EngineGone): the next start recovers,
      // opening the last saved versions only.  Anything else is a bug here:
      // the window is told every file lost its unsaved edits (it reloads
      // what the engine has) rather than being held back for ever.
      if (run !== this.run || e instanceof EngineGone) return;
      this.recovering = false;
      const crash = this.engine.crash();
      this.emit('recovered', {
        generation, attempt: replay ? 1 : 2, crash: crash ? { how: crash.how, log: [...crash.log, String(e)] } : null, restored: [], closed: [],
        lost: [...this.journal.files.values()].map((f) => ({ fileId: f.fileId, reason: 'replayFailed' as const, edits: f.entries.length })),
      });
      for (const f of this.journal.files.values()) { f.entries = []; f.broken = null; }
      this.releaseGate();
      this.emit('status', this.view(this.engine.status()));
    }
  }

  private call<T>(method: string, params: Record<string, unknown>): Promise<T> {
    return this.engine.call<T>(method, params, { tag: RECOVERY });
  }

  // Opens one file again with its old ids: 'ok', or why it could not be.
  private async openAgain(f: FileJournal): Promise<'ok' | 'missing' | 'openFailed'> {
    const restore = { fileId: f.fileId, circuits: f.circuits };
    try {
      if (f.opened.kind === 'new') await this.call('file.new', { restore });
      else {
        if (this.fingerprint(f.opened.path) === null) return 'missing';
        await this.call('file.open', { path: f.opened.path, ...(f.opened.readOnly ? { readOnly: true } : {}), restore });
      }
      return 'ok';
    } catch (e) {
      if (e instanceof EngineError) return 'openFailed';
      throw e;
    }
  }

  private async reopen(replay: boolean, generation: number): Promise<Recovered> {
    const crash = this.engine.crash();
    const report: Recovered = { generation, attempt: replay ? 1 : 2, crash: crash ? { how: crash.how, log: crash.log } : null, restored: [], lost: [], closed: [] };
    this.shadow.clear();
    const files = [...this.journal.files.values()];
    const open: FileJournal[] = [];
    const lost = new Map<string, 'replayFailed' | 'crashedAgain' | 'changedOnDisk' | 'notRecorded'>();
    for (const f of files) {
      const how = await this.openAgain(f);
      if (how !== 'ok') {
        report.closed.push({ fileId: f.fileId, reason: how });
        this.journal.closed(f.fileId);
        continue;
      }
      open.push(f);
      if (f.entries.length === 0 && !f.broken) continue;
      if (!replay) lost.set(f.fileId, 'crashedAgain');
      else if (f.broken) lost.set(f.fileId, 'notRecorded');
      else if (f.opened.kind === 'path' && this.fingerprint(f.opened.path) !== f.fingerprint) lost.set(f.fileId, 'changedOnDisk');
    }

    // Every file's edits together, in the order the engine first answered them.
    const queue = open.filter((f) => !lost.has(f.fileId)).flatMap((f) => f.entries.map((e) => ({ f, e }))).sort((x, y) => x.e.seq - y.e.seq);
    for (const { f, e } of queue) {
      if (lost.has(f.fileId)) continue;
      try {
        await this.replayOne(f.fileId, e);
      } catch (err) {
        if (!(err instanceof EngineError || err instanceof ReplayError)) throw err;
        lost.set(f.fileId, 'replayFailed');
        // Its last saved version, from scratch.
        await this.call('file.close', { fileId: f.fileId }).catch((x) => { if (x instanceof EngineGone) throw x; });
        const how = await this.openAgain(f);
        if (how !== 'ok') {
          lost.delete(f.fileId);
          report.closed.push({ fileId: f.fileId, reason: how });
          this.journal.closed(f.fileId);
        }
      }
    }

    for (const f of open) {
      if (!this.journal.files.has(f.fileId)) continue;
      const why = lost.get(f.fileId);
      const edits = f.entries.length;
      if (why) {
        report.lost.push({ fileId: f.fileId, reason: why, edits });
        // What the engine has now is the file as last saved (or new).
        f.entries = [];
        f.broken = null;
        if (f.opened.kind === 'path') f.fingerprint = this.fingerprint(f.opened.path);
      } else {
        const dirty = edits > 0 ? (await this.call<{ dirty: boolean }>('file.dirty', { fileId: f.fileId })).dirty : false;
        report.restored.push({ fileId: f.fileId, edits, dirty });
      }
    }
    return report;
  }

  private async circuitFor(fileId: string, c: { id: string; name: string | null }): Promise<string> {
    if (this.shadow.hasCircuit(fileId, c.id)) return c.id;
    if (c.name !== null) {
      let id = this.shadow.circuitNamed(fileId, c.name);
      if (!id) {
        await this.call('model.library', { fileId });
        id = this.shadow.circuitNamed(fileId, c.name);
      }
      if (id) return id;
    }
    throw new ReplayError(`no circuit ${c.name ?? c.id}`);
  }

  private async replayOne(fileId: string, e: Entry): Promise<void> {
    const params: Record<string, unknown> = structuredClone(e.params);
    params.fileId = fileId;
    if (e.circuit) params.circuitId = await this.circuitFor(fileId, e.circuit);
    const keys = Object.keys(e.refs);
    if (keys.length > 0) {
      const circuitId = params.circuitId as string;
      // The model as it is now: the last edit's model.changed is read
      // before this answer (the engine answers in order).
      if (this.shadow.loaded(fileId, circuitId)) await this.call('file.dirty', { fileId });
      else await this.call('model.circuit', { fileId, circuitId });
      for (const key of keys) {
        const ref = e.refs[key];
        const ids = this.shadow.find(fileId, circuitId, Array.isArray(ref) ? ref : [ref]);
        if (!ids) throw new ReplayError(`${e.method}: a part of ${key} is not there`);
        params[key] = Array.isArray(ref) ? ids : ids[0];
      }
    }
    await this.call(e.method, params);
  }
}

function circuitIds(circuits: unknown): Record<string, string> {
  const out: Record<string, string> = {};
  if (!Array.isArray(circuits)) return out;
  for (const c of circuits as CircuitRef[]) if (!(c.name in out)) out[c.name] = c.circuitId;
  return out;
}
