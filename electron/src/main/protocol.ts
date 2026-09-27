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

/* What the window may call through window.app.call().  Opening, saving and
   loading a program (mips.load) are not here: the paths they take come from
   the main process (its file dialogs, the command line), never from the page. */
export const WINDOW_METHODS = [
  'file.new', 'file.close', 'file.dirty',
  'model.circuit', 'model.library',
  'edit.addComponent', 'edit.addWire', 'edit.move', 'edit.delete', 'edit.setAttr', 'edit.undo', 'edit.redo',
  'sim.reset', 'sim.poke', 'sim.cycles', 'sim.run', 'sim.enable', 'sim.watch', 'sim.state',
  'diag.list', 'trace.origin',
  'mips.facts', 'mips.reload', 'mips.disasm', 'mips.console',
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

// ---- mips.* (docs/engine-api.md "mips", N-16, D-147) ----------------------------
// Loading an executable image (.hmx, Hallym MIPS's export) into the circuit's
// memories, the facts about it, its disassembly and the Console's output.

export interface Bilingual { en: string; ko: string }

// A fact for the status bar (not a Messages diagnostic): separateStack, assemblySource, pcEntry.
export interface MipsFact {
  id: string;
  en: string;
  ko: string;
  components: string[];
  sources?: string[];       // assemblySource: each component's source attribute
  pc?: string;              // pcEntry: the circuit's PC at cycle 0
  entry?: string;           // pcEntry: the image's entry
}

// Why an image was not loaded: its line (0: no one line) -> what is wrong -> what to do.
export interface LoadProblem { line: number; text: Bilingual }

export interface ProgramMemory {
  componentId: string;
  circuitId: string;
  kind: 'text' | 'data';
  source: string | null;
  words: number;
  entry?: string;
  text: string;             // the body line: "27 words (0x00400000–0x00400068), entry 0x00400024"
}

// A reload that failed: the program and the simulation on show are the last loaded ones.
export interface ProgramFailure {
  at: number;               // ms
  file: string;             // the .hmx's name
  source: string;           // the attribute's text
  reason: 'open' | 'changed' | 'reset' | 'manual';
  problems: LoadProblem[];
  kept: { loadedAt: number | null };   // null: the program the .circ was saved with
}

export interface ProgramInfo {
  name: string;             // "data.hmx"
  source: string | null;    // the attribute's text ("prog/data.hmx")
  entry: string | null;
  loadedAt?: number | null; // this run's last good load (ms)
  failure: ProgramFailure | null;
  memories: ProgramMemory[];
}

export interface MipsFacts {
  fileId: string;
  facts: MipsFact[];
  program: ProgramInfo | null;
}

export interface LoadSegment {
  kind: 'text' | 'data';
  start: string;
  last: string;
  range: string;            // "0x00400000–0x00400068"
  units: number;
  unit: 'word' | 'byte';
  bytes: number;
  words: number;
  text: string;             // "28 bytes = 7 words (0x10010000–0x1001001b)"
  componentId: string;
  circuitId: string;
  target: string;           // "main › Data Memory (10000000-100fffff)"
}

export interface LoadSummary {
  entry: string | null;
  entryLine: string;        // "entry 0x00400024 (main)"
  regs: { name: string; value: string }[];
  segments: LoadSegment[];
  emptied?: { componentId: string; circuitId: string; target: string } | null;
  stackBase: { componentId: string; target: string; sp: string | null }[];
  instructions: string[];
  source?: { status: 'same' | 'changed' | 'notFound' | 'noHash'; name?: string | null; text: Bilingual; warn: boolean };
  facts: { id: 'noHandler' | 'jrRa' | string; text: Bilingual }[];
  producedBy?: string | null;
  assembled?: string | null;
  notes: string[];
}

export interface LoadChoice {
  kind: 'text' | 'data';
  segment: string;          // ".text 0x00400000–0x00400068"
  candidates: { componentId: string; circuitId: string; name: string }[];
}

export interface LoadResult {
  fileId: string;
  file: string;             // the .hmx's name
  loaded: boolean;
  source?: string;
  loadedAt?: number;
  summary?: LoadSummary;
  choose?: LoadChoice;      // several memories hold a segment: pick one and load again
  problems?: LoadProblem[];
}

export interface Reloaded {
  fileId: string;
  ok: boolean;
  reason: 'open' | 'changed' | 'reset' | 'manual';
  file: string;
  source: string;
  loadedAt?: number;
  summary?: LoadSummary;
  problems?: LoadProblem[];
  kept?: { loadedAt: number | null };
}

// The Console tab: every Console's output; `text` replaces, `append` adds.
export interface ConsoleEntry { name: string; text?: string; append?: string; exited: boolean }
export interface ConsoleUpdate { fileId: string; consoles: ConsoleEntry[] }

export interface DisasmLine { addr: string; word: string; text: string; labels?: string[]; entry?: boolean }
export interface Disasm {
  fileId: string;
  componentId: string;
  words: number;
  first: string | null;
  last: string | null;
  entry: string | null;
  symbols: boolean;
  lines: DisasmLine[];
}
