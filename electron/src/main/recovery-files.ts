/* Recovery files beside the student's files (N-19, D-152, docs/engine-api.md
   7 "복구 파일").

   If the whole app dies -- the lab PC is switched off, the program is
   killed -- the edits since the last save would be gone: the journal of
   recovery.ts lives in this process's memory only.  So for a file the
   student has saved at least once (it has a place: a path), the engine
   keeps `<name>.circ.hcs-recover` beside it -- the .circ a save would write
   now, written by the engine's own save code into that folder and nowhere
   else (the lab-PC rule: nothing app-global on disk).  A new file never
   saved has none: nothing is written anywhere.

     RecoveryWriter   asks the engine to write it (file.recoverWrite) after
                      the window's edits: 10 s after the last one, 60 s after
                      the first one not yet written at the latest, or after
                      30 of them.  Saving or closing the file drops what is
                      waiting (the engine removes the file then).
     recoveryBeside   when a file is opened: is there a recovery file not
                      older than the file?  Then the window asks (Recover /
                      Discard) before it is opened (main.ts openPath).

   The engine does the rest (engine.hello recoveryFiles): it removes the
   file on a save, a close and a normal quit (engine.shutdown), and when
   this process is gone (its stdin closes, its parent ends) it writes the
   recovery file of every file with unsaved edits before it ends -- so a
   killed app loses nothing the engine had.  The writes here are for the
   case where both end at once (the power goes).

   Nothing here knows Electron: tests/unit/recovery-files.test.ts drives it
   with the fake engine. */

import { EventEmitter } from 'node:events';
import { statSync } from 'node:fs';

import { EngineError, EngineGone, type Answer, type EngineClient } from './engine.ts';
import { journaled, recoveryPathOf, WINDOW } from './recovery.ts';

export { RECOVERY_SUFFIX, recoveryPathOf } from './recovery.ts';

// The tag of the writer's calls (not the window's: the journal does not record them).
export const WRITER = 'recovery-file';

// A recovery file to ask about: beside `circ`, and not older than it (an
// older one was left before the file was saved again, by another program).
export function recoveryBeside(circ: string, stat: typeof statSync = statSync): { path: string; modified: number } | null {
  const rec = recoveryPathOf(circ);
  try {
    const r = stat(rec);
    const c = stat(circ);
    if (!r.isFile()) return null;
    return r.mtimeMs >= c.mtimeMs ? { path: rec, modified: r.mtimeMs } : null;
  } catch {
    return null;
  }
}

export interface WriterOptions {
  idleMs?: number;          // after the last edit: default 10 s
  maxWaitMs?: number;       // after the first edit not yet written: default 60 s
  maxEdits?: number;        // edits not yet written: default 30
  // Whether a file has a place for one (opened from disk or saved; not a new file): the others are not asked about.
  hasPlace?: (fileId: string) => boolean;
  // Resolves when no engine-crash recovery is replaying (recovery.ts settled()): writes wait for it.
  settled?: () => Promise<void>;
}

interface Waiting {
  edits: number;
  first: number;
  timer: NodeJS.Timeout | null;
}

export interface WriteResult { path: string | null; written: boolean; bytes?: number }

interface Events {
  written: [fileId: string, result: WriteResult];
  failed: [fileId: string, message: string];
}

export class RecoveryWriter extends EventEmitter<Events> {
  private readonly engine: EngineClient;
  private readonly idleMs: number;
  private readonly maxWaitMs: number;
  private readonly maxEdits: number;
  private readonly hasPlace: (fileId: string) => boolean;
  private readonly settled: () => Promise<void>;
  private readonly waiting = new Map<string, Waiting>();
  private readonly writing = new Set<Promise<void>>();

  constructor(engine: EngineClient, options: WriterOptions = {}) {
    super();
    this.engine = engine;
    this.idleMs = options.idleMs ?? 10_000;
    this.maxWaitMs = options.maxWaitMs ?? 60_000;
    this.maxEdits = options.maxEdits ?? 30;
    this.hasPlace = options.hasPlace ?? (() => true);
    this.settled = options.settled ?? (() => Promise.resolve());
    engine.on('answer', (a) => this.answered(a));
  }

  // The files with edits not yet written (tests).
  pending(): string[] { return [...this.waiting.keys()]; }

  // Resolves when every write asked for so far has been answered (tests).
  async idle(): Promise<void> { while (this.writing.size) await Promise.all([...this.writing]); }

  private answered(a: Answer): void {
    const p = (typeof a.params === 'object' && a.params !== null ? a.params : {}) as Record<string, unknown>;
    if (typeof p.fileId !== 'string') return;
    if (a.tag === WINDOW && journaled(a.method)) this.edited(p.fileId);
    else if (a.method === 'file.save' || a.method === 'file.close') this.forget(p.fileId);
  }

  private edited(fileId: string): void {
    if (!this.hasPlace(fileId)) return;
    const now = Date.now();
    let w = this.waiting.get(fileId);
    if (!w) {
      w = { edits: 0, first: now, timer: null };
      this.waiting.set(fileId, w);
    }
    w.edits += 1;
    if (w.timer) clearTimeout(w.timer);
    const due = w.edits >= this.maxEdits ? 0 : Math.max(0, Math.min(this.idleMs, w.first + this.maxWaitMs - now));
    w.timer = setTimeout(() => this.write(fileId), due);
  }

  private forget(fileId: string): void {
    const w = this.waiting.get(fileId);
    if (w?.timer) clearTimeout(w.timer);
    this.waiting.delete(fileId);
  }

  // Writes now whatever is waiting (tests; and the timers).
  write(fileId: string): Promise<void> {
    this.forget(fileId);
    const job = this.writeNow(fileId).finally(() => this.writing.delete(job));
    this.writing.add(job);
    return job;
  }

  private async writeNow(fileId: string): Promise<void> {
    await this.settled();
    try {
      const r = await this.engine.call<WriteResult>('file.recoverWrite', { fileId }, { tag: WRITER });
      this.emit('written', fileId, r);
    } catch (e) {
      // Closed meanwhile (1), or the engine ended (the journal's recovery takes over; its last write is on disk).
      if (e instanceof EngineGone || (e instanceof EngineError && e.code === 1)) return;
      this.emit('failed', fileId, (e as Error).message);
    }
  }

  dispose(): void {
    for (const w of this.waiting.values()) if (w.timer) clearTimeout(w.timer);
    this.waiting.clear();
  }
}
