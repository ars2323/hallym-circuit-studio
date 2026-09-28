/* The open files and their circuit tabs (logic only; app.ts draws them).

   A file is what the engine opened (its fileId); each has the circuits the
   engine listed, the circuits opened as tabs (the main one first), the one
   on show, and whether it has unsaved changes.  One file is on show.
   Closing the file on show shows its neighbour (the one on its left, or
   the first).  When the engine dies and starts again, the main process opens
   every file again under the same ids (src/main/recovery.ts): a file keeps
   its tabs and the circuit on show, its simulation starts over (reopened());
   one that could not be opened again is closed. */

import type { CircuitRef, SimState } from '../../../main/protocol.ts';

export interface OpenFile {
  fileId: string;
  name: string;
  path: string | null;         // null: never saved
  circuits: CircuitRef[];
  main: string;                // circuitId
  tabs: string[];              // circuitIds opened as tabs, in order
  circuit: string;             // the one on show
  dirty: boolean;
  sim: SimState | null;
}

export class Files {
  private files: OpenFile[] = [];
  private shown: string | null = null;

  list(): readonly OpenFile[] { return this.files; }
  get(fileId: string): OpenFile | undefined { return this.files.find((f) => f.fileId === fileId); }
  active(): OpenFile | null { return this.shown === null ? null : this.get(this.shown) ?? null; }
  byPath(path: string): OpenFile | undefined { return this.files.find((f) => f.path !== null && f.path === path); }
  count(): number { return this.files.length; }

  add(f: { fileId: string; name: string; path: string | null; circuits: CircuitRef[]; main: string }): OpenFile {
    const main = f.circuits.some((c) => c.circuitId === f.main) ? f.main : f.circuits[0]?.circuitId ?? '';
    const file: OpenFile = { ...f, main, tabs: main ? [main] : [], circuit: main, dirty: false, sim: null };
    this.files.push(file);
    this.shown = file.fileId;
    return file;
  }

  activate(fileId: string): void { if (this.get(fileId)) this.shown = fileId; }

  // Returns the file now on show (null: none left).
  close(fileId: string): OpenFile | null {
    const i = this.files.findIndex((f) => f.fileId === fileId);
    if (i < 0) return this.active();
    this.files.splice(i, 1);
    if (this.shown === fileId) this.shown = this.files[Math.max(0, i - 1)]?.fileId ?? null;
    return this.active();
  }

  // Opens the circuit as a tab of its file (if it is not one yet) and shows it.
  openCircuit(fileId: string, circuitId: string): void {
    const f = this.get(fileId);
    if (!f || !f.circuits.some((c) => c.circuitId === circuitId)) return;
    if (!f.tabs.includes(circuitId)) f.tabs.push(circuitId);
    f.circuit = circuitId;
  }

  // A circuit tab closed; the last one stays.
  closeCircuit(fileId: string, circuitId: string): void {
    const f = this.get(fileId);
    if (!f || f.tabs.length <= 1) return;
    const i = f.tabs.indexOf(circuitId);
    if (i < 0) return;
    f.tabs.splice(i, 1);
    if (f.circuit === circuitId) f.circuit = f.tabs[Math.max(0, i - 1)];
  }

  /* file.changed (N-11): the circuits as they are now -- added, removed, renamed, moved -- and the main one.
     The tabs of circuits that are gone close (the one on show goes to its neighbour, or main); the others stay.
     Returns the circuit ids that went. */
  structure(fileId: string, circuits: CircuitRef[], main: string): string[] {
    const f = this.get(fileId);
    if (!f) return [];
    const now = new Set(circuits.map((c) => c.circuitId));
    const gone = f.circuits.map((c) => c.circuitId).filter((id) => !now.has(id));
    f.circuits = circuits;
    f.main = now.has(main) ? main : circuits[0]?.circuitId ?? '';
    const i = f.tabs.indexOf(f.circuit);
    f.tabs = f.tabs.filter((id) => now.has(id));
    if (!now.has(f.circuit)) f.circuit = f.tabs[Math.max(0, Math.min(i, f.tabs.length) - 1)] ?? f.main;
    if (f.circuit && !f.tabs.includes(f.circuit)) f.tabs.unshift(f.circuit);
    return gone;
  }

  circuitName(f: OpenFile, circuitId: string): string {
    return f.circuits.find((c) => c.circuitId === circuitId)?.name ?? circuitId;
  }

  saved(fileId: string, name: string, path: string): void {
    const f = this.get(fileId);
    if (!f) return;
    f.name = name;
    f.path = path;
    f.dirty = false;
  }

  setDirty(fileId: string, dirty: boolean): void { const f = this.get(fileId); if (f) f.dirty = dirty; }

  // Opened again by a new engine: the same tabs; unsaved or not as it says; the simulation from Reset.
  reopened(fileId: string, dirty: boolean): void {
    const f = this.get(fileId);
    if (!f) return;
    f.dirty = dirty;
    f.sim = null;
  }
  setSim(state: SimState): void { const f = this.get(state.fileId); if (f) f.sim = state; }

  clear(): void {
    this.files = [];
    this.shown = null;
  }
}
