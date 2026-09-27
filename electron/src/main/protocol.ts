/* The engine's API as types (docs/engine-api.md, v0) and what the window
   may ask of it.  Types only: the window imports this file for its types
   and nothing of it ends up in its script. */

export type Point = [number, number];

export interface Hello {
  engine: string;
  version: string;
  logisim: string;
  java: string;
  api?: string;
}

export interface CircuitRef {
  circuitId: string;
  name: string;
}

export interface LibRef {
  lib: string;
  display?: string;
  kind: 'builtin' | 'jar' | 'circ';
  path?: string;
}

export interface NewResult {
  fileId: string;
  name?: string;            // Logisim's project name ("Untitled"; a file's name without .circ)
  circuits: CircuitRef[];
  main: string;
  libraries?: LibRef[];
}

export interface OpenResult extends NewResult {
  name: string;
  libraries: LibRef[];
  messages?: string[];      // what the original loader showed in its dialogs
  alreadyOpen?: boolean;    // the same path was open: its fileId
}

export interface SaveResult {
  path: string;
  bytes: number;
  needsMipsJar?: boolean;   // uses MIPS parts, and no hcs-mips.jar next to the saved .circ
}

export interface Port {
  i: number;
  loc: Point;
  width: number;
  dir: 'in' | 'out' | 'inout';
  name?: string;
}

export interface Component {
  id: string;
  lib: string | null;       // null: a circuit of this file
  name: string;
  loc: Point;
  bounds: [number, number, number, number];
  facing: 'east' | 'west' | 'north' | 'south' | null;
  attrs: Record<string, string>;
  ports: Port[];
  subcircuit?: string;
}

export interface Wire {
  id: string;
  a: Point;
  b: Point;
}

export interface Net {
  id: string;
  width: number;
  wires: string[];
  ports: [string, number][];
}

export interface Snapshot {
  circuitId: string;
  name: string;
  components: Component[];
  wires: Wire[];
  nets: Net[];
  junctions: Point[];
}

export interface LibraryGroup {
  lib: string | null;       // null: this file's circuits (the first group)
  display?: string;
  pending?: boolean;        // the bundled Hallym MIPS, not in the file until its first part is placed
  tools: { name: string; display: string; circuitId?: string }[];
}

export interface SimState {
  fileId: string;
  running: boolean;         // Logisim's Simulation Enabled (off: oscillation)
  ticking: boolean;         // the clock ticks (Run)
  cycle: number;
  oscillating: boolean;
  hz?: number;              // ticks per second
}

// ---- Messages and E/X origin (docs/engine-api.md "diag·trace", D-143) ----

/* Where a message points: the circuit the cause is in; for a message found
   while the simulation ran, the top circuit (root) and the subcircuit
   instances down to it (path, component ids); the parts, wires and nets to
   show; the point to go to; the Cycle View column (dynamic only). */
export interface DiagLocation {
  circuitId: string;
  root: string;
  path: string[];
  components: string[];
  wires: string[];
  nets: string[];
  at: Point | null;
  cycle?: number;
}

export type DiagCode =
  | 'CLOCK_UNCONNECTED' | 'SHORT' | 'WIDTH_MISMATCH' | 'INPUT_UNCONNECTED' | 'INPUT_UNDRIVEN' | 'TUNNEL_UNPAIRED'
  | 'SUBCIRCUIT_PORT_UNCONNECTED' | 'COMBINATIONAL_LOOP' | 'MEMORY_OVERLAP'
  | 'E_APPEARED' | 'X_WRITE_DATA' | 'X_WRITE_CONTROL' | 'OSCILLATION' | 'MIPS_STATUS';

export interface DiagMessage {
  id: string;                 // stable while the same cause stays in the list
  code: DiagCode;
  kind: 'static' | 'dynamic'; // found from the wiring, or while the simulation ran
  severity: 'error';          // every message is a circuit that cannot work (CLAUDE.md 2.6)
  text: { ko: string; en: string };
  near?: string;              // TUNNEL_UNPAIRED: the one near name ("혹시 RegWrite?")
  location: DiagLocation;
  appeared?: { circuitId: string; root: string; path: string[]; at: Point; netId?: string };
}

export interface DiagList {
  fileId: string;
  messages: DiagMessage[];
}

export interface TraceOrigin {
  found: boolean;
  text?: { ko: string; en: string };          // found: false -- why there is nothing to follow
  origin?: DiagLocation & { cause: string; value: 'E' | 'x'; text: { ko: string; en: string } };
  chain: { circuitId: string; path: string[]; netId: string }[];
}

// Error codes (docs/engine-api.md 2).
export const ERR_NOT_FOUND = 1;
export const ERR_FILE = 2;
export const ERR_READ_ONLY = 3;
export const ERR_SIM = 4;
export const ERR_NO_METHOD = -32601;

/* What the window may call through window.app.call().  Opening and saving
   are not here: the paths they take come from the main process (its file
   dialogs, the command line), never from the page. */
export const WINDOW_METHODS = [
  'file.new', 'file.close', 'file.dirty',
  'model.circuit', 'model.library',
  'edit.addComponent', 'edit.addWire', 'edit.move', 'edit.delete', 'edit.setAttr', 'edit.undo', 'edit.redo',
  'sim.reset', 'sim.poke', 'sim.cycles', 'sim.run', 'sim.enable', 'sim.watch', 'sim.state',
  'diag.list', 'trace.origin',
] as const;
export type WindowMethod = typeof WINDOW_METHODS[number];

/* What came back after the engine died and started again (src/main/recovery.ts,
   the 'engine:recovered' event, D-142).  Files keep their fileId and circuit
   ids; their parts have new ids (the window asks for the model again). */
export interface Recovered {
  generation: number;
  attempt: number;          // 1: unsaved edits replayed; 2: it ended again while recovering, last saved versions opened
  crash: { how: string; log: string[] } | null;   // how the engine ended, its last log lines
  restored: { fileId: string; edits: number; dirty: boolean }[];   // opened again, every unsaved edit replayed
  lost: { fileId: string; reason: 'replayFailed' | 'crashedAgain' | 'changedOnDisk' | 'notRecorded'; edits: number }[];  // opened as last saved
  closed: { fileId: string; reason: 'missing' | 'openFailed' }[];  // could not be opened again: its tab closes
}

// The engine as the window sees it (src/main/engine.ts EngineStatus).
export type EngineState = 'starting' | 'ready' | 'restarting' | 'failed' | 'stopped';

export interface EngineStatus {
  state: EngineState;
  generation: number;       // one more at every start that answered hello: files of an earlier one are gone
  hello: Hello | null;
  error: string | null;     // why it is not running (state failed), in the window's words
  detail: string | null;    // what was tried and what the engine said last (stderr), for the dialog
}
