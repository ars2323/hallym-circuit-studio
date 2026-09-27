/* The engine's API as types (docs/engine-api.md, v0) and what the window
   may ask of it.  Types only: the window imports this file for its types
   and nothing of it ends up in its script. */

export type Point = [number, number];

export interface Hello {
  engine: string;
  version: string;
  logisim: string;
  java: string;
}

export interface CircuitRef {
  circuitId: string;
  name: string;
}

export interface LibRef {
  lib: string;
  kind: 'builtin' | 'jar' | 'circ';
  path?: string;
}

export interface NewResult {
  fileId: string;
  circuits: CircuitRef[];
  main: string;
}

export interface OpenResult extends NewResult {
  name: string;
  libraries: LibRef[];
}

export interface SaveResult {
  path: string;
  bytes: number;
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
  lib: string;
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
  lib: string;
  tools: { name: string; display: string }[];
}

export interface SimState {
  fileId: string;
  running: boolean;
  ticking: boolean;
  cycle: number;
  oscillating: boolean;
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
  'edit.undo', 'edit.redo',
  'sim.reset', 'sim.cycles', 'sim.run', 'sim.watch',
] as const;
export type WindowMethod = typeof WINDOW_METHODS[number];

// The engine as the window sees it (src/main/engine.ts EngineStatus).
export type EngineState = 'starting' | 'ready' | 'restarting' | 'failed' | 'stopped';

export interface EngineStatus {
  state: EngineState;
  generation: number;       // one more at every start that answered hello: files of an earlier one are gone
  hello: Hello | null;
  error: string | null;     // why it is not running (state failed), in the window's words
  detail: string | null;    // what was tried and what the engine said last (stderr), for the dialog
}
