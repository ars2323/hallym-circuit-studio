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

/* Opening a file that has a recovery file beside it (N-19, D-152): the
   window asks first -- Recover (its content, as unsaved edits) or Discard
   (removed once the file is open) -- and answers with the id; Esc answers
   null (nothing opened, the recovery file left). */
export interface RecoveryAsk {
  ask: {
    id: string;             // for file:openRecovery
    name: string;           // the file's name (lab3.circ)
    recovery: string;       // the recovery file's name (lab3.circ.hcs-recover)
    modified: number;       // when it was written (ms since 1970)
  };
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
  appearance?: Appearance;  // a subcircuit instance: its circuit's appearance (N-05)
  ext?: ComponentExt;       // what the student set that the .circ keeps in hcs:ext (N-12)
}

/* The student's own settings on a part (hcs:ext, D-024; docs/engine-api.md,
   N-12): a tunnel's colour picked from the palette (#RRGGBB; none: the
   automatic colour from its name), a splitter's arm names (top arm first). */
export interface ComponentExt {
  color?: string;
  arms?: string[];
}

// A subcircuit's appearance (docs/engine-api.md): the original's <appear> SVG elements.
export interface AppearanceShape {
  tag: 'rect' | 'ellipse' | 'line' | 'polyline' | 'polygon' | 'path' | 'text' | string;
  attrs: Record<string, string>;
  text?: string;
}
export interface Appearance {
  default: boolean;
  anchor: Point;
  facing: 'east' | 'west' | 'north' | 'south';
  shapes: AppearanceShape[];
  ports: { at: Point; pin?: Point; input: boolean }[];
  label?: { text: string; facing: string; font: string };
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
  groups?: GroupRef[];      // signal groups (N-15): absent = none
  memos?: AreaMemo[];       // area memos (N-15): absent = none
}

/* What the student put on a circuit (hcs:ext, N-15, D-151; v1 E-04, E-08): a net's signal group -- chosen
   (assigned) or, for the outputs of a subcircuit named control, Control by default -- and the area memos
   (a box and a word; colour: the tunnel palette's index). */
export type SignalGroup = 'control' | 'data' | 'address';
export interface GroupRef { net: string; group: SignalGroup; assigned: boolean }
export interface AreaMemo { x: number; y: number; w: number; h: number; color: number; text: string }

export interface LibraryGroup {
  lib: string | null;       // null: this file's circuits (the first group)
  display?: string;
  pending?: boolean;        // the bundled Hallym MIPS, not in the file until its first part is placed
  tools: { name: string; display: string; circuitId?: string }[];
}

// ---- Find (docs/engine-api.md find.query, N-12, D-150) ----

export type FindKind = 'label' | 'pin' | 'tunnel' | 'subcircuit' | 'part';

/* One place a name was found: the circuit to show it in (root, then the
   subcircuit instances down to it), the part, and where it stands in words
   (the port it is next to, or its numbered name). */
export interface FindPlace {
  circuitId: string;
  root: string;
  path: string[];
  componentId: string;
  at: Point;
  place: string;            // "main › PC (D)", "main › Tunnel #3"
  near: boolean;            // place is the port it is attached to ("next to …")
}

// The same kind, name and path: one row, its places in order (top to bottom, left to right).
export interface FindGroup {
  kind: FindKind;
  text: string;
  path: string;             // "main › regfile #1 › RR1"
  places: FindPlace[];
}

export interface FindResult {
  fileId: string;
  text: string;
  groups: FindGroup[];
  more: boolean;            // more groups than the limit
}

export interface SimState {
  fileId: string;
  running: boolean;         // Logisim's Simulation Enabled (off: oscillation)
  ticking: boolean;         // the clock ticks (Run)
  cycle: number;
  oscillating: boolean;
  hz?: number;              // ticks per second (the student's, also while N Cycles ticks fast)
  cyclesLeft?: number;      // N Cycles going: the cycles still to come (0: none, N-07)
}

// sim.poke's answer: whether the part took the press, and whether it keeps a caret for keys (N-07).
export interface PokeResult { poked: boolean; caret?: boolean }

// ---- editing (docs/engine-api.md "edit", N-08, D-146) ----------------------------

// What an edit answers: whether the model or the undo log changed; outcome says more (edit.select {at}: "moving"
// or "rect", the drag's meaning); id: a part the edit put in (edit.addComponent, edit.text); circuitId: a new circuit.
export interface EditResult { changed: boolean; outcome?: string; id?: string; circuitId?: string }

/* edit.selection: what the engine's selection holds (the original Canvas's
   Selection) after an edit changed it: ids in the circuit, and the parts and
   wires floating (a paste or a duplicate not yet dropped into the circuit;
   not in the model, so given whole). */
export interface EditSelection {
  fileId: string;
  circuitId: string | null;
  ids: string[];
  floating: (Component | Wire)[];
}

// model.tool: the part a placing tool would put in at loc (id "ghost"), drawn under the pointer.
export interface ToolGhost { component: Component }

// model.movePreview: the wires the move would add and take away, the points it could not connect, the offset it would use.
export interface MovePreview { dx: number; dy: number; added: [Point, Point][]; removed: string[]; unconnected: Point[] }

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

// The values of the watched circuit's nets that changed, a frame at a time (docs/engine-api.md sim.values).
export interface SimValues {
  fileId: string;
  circuitId: string;
  root?: string;
  path?: string[];
  nets: Record<string, string>;
  bodies?: Record<string, Record<string, unknown>>;   // what a part's body shows that no net carries (N-05)
}

// An edit's result (docs/engine-api.md model.changed).
export interface ModelChanged {
  fileId: string;
  circuitId: string;
  removed: string[];
  added: (Component | Wire)[];
  nets: Net[];
  junctions: Point[];
  groups?: GroupRef[];      // the circuit's whole lists (N-15; the real engine always sends them)
  memos?: AreaMemo[];
  dirty: boolean;
}

// ---- record.* (docs/engine-api.md "record", N-14, D-144) ----------------------
// A value is the protocol's letters, high bit first ('0' '1' 'x' 'E'); null: not recorded.

export type RunUntilKind = 'pc' | 'instruction' | 'row' | 'errorOrX' | 'halt';

export interface RecordState {
  fileId: string;
  empty: boolean;           // nothing recorded yet
  first: number;            // the oldest cycle kept
  last: number;             // the latest cycle
  cycle: number;            // the cycle on show (last unless a past one is)
  past: boolean;            // a past cycle is on show (the circuit shows its values)
  generation: number;       // one more at every new recording (Reset, reopening)
  pc: string | null;        // "0x00400024": the status bar's PC in the cycle on show
  cpu: boolean;             // the circuit has an Instruction Memory (the table's PC and Instruction rows)
  rows: number;             // rows the student added
  pinned: number;           // temporary rows (a message's cause)
  runUntil: { kind: RunUntilKind; value?: string; from: number } | null;
}

export interface RunUntilDone {
  fileId: string;
  result: 'met' | 'limit' | 'stopped' | 'off';
  cycle: number;
  from: number;
  kind: RunUntilKind;
  value?: string;
}

export interface CycleColumn {
  cycle: number;
  pc: string | null;        // "0x00400024"
  word: string | null;      // "0x8e090000"
  text: string;             // the Java disassembler's text ("" when PC or the word is not defined)
}

export interface CycleRow {
  id: string;
  name: string;             // "ALUResult", "regfile › RD1"
  width: number;
  bits: boolean;            // shown bit by bit
  temp: boolean;            // pinned from a message (not saved)
  values?: (string | null)[];   // one per column: the value at the cycle's end
  halves?: (string | null)[];   // 1-bit rows: the first half (after the rising edge)
}

export interface CycleTable {
  fileId: string;
  empty: boolean;
  first?: number;
  last?: number;
  cycle?: number;
  from?: number;
  to?: number;
  cpu?: boolean;            // an Instruction Memory gives PC and the instruction
  pinnedCycle?: number;     // the pinned rows' cycle, -1 if none
  columns: CycleColumn[];
  rows: CycleRow[];
}

export interface RegisterRow {
  key: string;              // "PC", "$t0", "reg3"
  name: string;             // "$t0"; a register with no number: its label or path
  number: number;           // 0..31, or -1
  group: string;            // Hallym MIPS's groups (Special, Constant, Return values, ...), then Other registers
  value: string | null;
  changed: boolean;         // not the same as in the cycle before
  alias?: string;           // the circuit's own name when it is not the shown one ("$29", "Register #1")
  componentId?: string;
  markable?: boolean;       // a top-level Register or Counter: Mark as PC
  markedPc?: boolean;
}

export interface RegisterData {
  fileId: string;
  cycle?: number;
  circuitId?: string;       // the recorded top circuit (the rows' componentId are in it: Mark as PC)
  mode: 'file' | 'all' | 'none';
  registerFile?: { circuitId: string; name: string };
  unmapped?: boolean;       // no register has a $n or Rn name (no register file marked)
  candidates?: { circuitId: string; name: string; registers: number }[];
  rows: RegisterRow[];
}

export interface MemoryRow {
  kind: 'section' | 'words' | 'zeros';
  section: 'data' | 'stack';
  part: string;
  addr: string;             // "0x10010000"
  end: string;              // the last byte's address
  words?: (string | null)[];    // four cells (+0 +4 +8 +C): "0000002a", "xxxxxxxx", null outside
  count?: number;           // zeros: how many words
  labels?: { addr: string; names: string[] }[];
  pointers?: Record<string, string>;
  base?: string;            // stack section: the depth's base
  depth?: number;           // stack section: base − $sp in bytes, -1 unknown
  peak?: number;            // stack section: the deepest access in bytes
}

export interface MemoryData {
  fileId: string;
  cycle?: number;
  parts: number;
  rows: MemoryRow[];
}

export interface InstructionField {
  name: string;             // Hallym MIPS's: opcode rs rt rd shamt funct immediate target fmt ft fs fd cc nd tf CO code sel
  hi: number;
  lo: number;
  bits: string;
  value: string;
  meaning: string;
}

export interface InstructionData {
  fileId: string;
  cycle?: number;
  none?: 'empty' | 'noCpu' | 'undefined';
  pc?: string | null;
  word?: string;
  text?: string;
  mnemonic?: string | null;
  format?: string;          // R I J CP0 FR FI
  fields?: InstructionField[];
}

export interface RegisterMapping {
  circuitId: string;
  name: string;
  registers: { id: string; name: string; loc: Point }[];
  map: Record<string, Point | null>;      // "0".."31" -> the Register's place (v1's regmap speaks of places)
  guess: Record<string, Point | null>;
}

// ---- the Canvas's overlays (docs/engine-api.md "flow·trace", N-15, D-151) ----

// trace.influence: what the start drives (forward) and what drives it (backward), in the shown circuit.
export type InfluenceMode = 'forward' | 'backward' | 'both' | 'between';
export interface InfluenceResult {
  mode: InfluenceMode;
  depth: number;            // the steps shown; -1: all
  maxDepth: number;
  throughRegisters: boolean;
  forward: { wires: string[]; parts: string[] };
  backward: { wires: string[]; parts: string[] };
  stops: string[];          // state parts where it stopped
  origin: string[];         // where it started
  inside: { componentId: string; name: string; places: number }[];  // subcircuit instances reached inside
  tunnels: string[];        // the tunnels of the nets reached
  links: Point[][];         // the two tunnels of a net that has exactly two
}

// flow.path: the Signal Flow's shape (v1 SignalFlowPath). Times and lengths are circuit units.
export interface FlowWhere { path: string[]; circuitId: string }
export interface FlowSegment extends FlowWhere { from: Point; to: Point; start: number; length: number; width: number; cycle: number }
export interface FlowJump extends FlowWhere { from: Point; to: Point; start: number }
export interface FlowPass extends FlowWhere { componentId: string; name: string; time: number; boundary: boolean; cycle: number }
export interface FlowEndpoint extends FlowWhere {
  componentId: string; port: number; at: Point; time: number;
  kind: 'output' | 'state' | 'unconnected' | 'source'; label: string;
}
export interface FlowPath {
  circuitId: string;
  backward: boolean;
  total: number;
  click?: Point;
  segments: FlowSegment[];
  jumps: FlowJump[];
  passes: FlowPass[];
  endpoints: FlowEndpoint[];
  loops: string[];
  undetermined: string[];
}

// flow.activePath: each multiplexer's selected input and the wire pieces from its driver to that input.
export interface ActivePathResult {
  circuitId: string;
  watched: boolean;
  muxes: { componentId: string; input: number; segments: [Point, Point][] }[];
}

// trace.net: a net's width, name and ports (v1 Net Information).
export interface NetPort { componentId: string; port: number; text: string }
export interface NetInfo { netId: string; width: number; name: string; drivers: NetPort[]; readers: NetPort[]; others: NetPort[] }

// record.fieldPaths (N-14, D-144): the wires of the splitter arms named after the instruction's fields.
export interface FieldPaths { fileId: string; circuitId: string; cycle: number; format?: string; fields: Record<string, string[]> }

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
  'edit.tunnelColor', 'edit.splitterEdit', 'edit.splitterSplit', 'find.query',
  'edit.select', 'edit.copy', 'edit.cut', 'edit.paste', 'edit.duplicate', 'edit.rotate', 'edit.keyConfig', 'edit.setToolAttr', 'edit.text',
  'edit.duplicateN', 'edit.align', 'edit.distribute', 'edit.setCircuitAttr', 'edit.createCircuit', 'edit.setMainCircuit',
  'model.tool', 'model.movePreview', 'model.textAt',
  'sim.reset', 'sim.poke', 'sim.pinValue', 'sim.pokeKey', 'sim.pokeStop', 'sim.cycles', 'sim.tick', 'sim.step', 'sim.run', 'sim.enable', 'sim.watch', 'sim.state',
  'diag.list', 'trace.origin',
  'mips.facts', 'mips.reload', 'mips.disasm', 'mips.console',
  'record.state', 'record.table', 'record.addRow', 'record.removeRow', 'record.rowBits', 'record.pin', 'record.unpin',
  'record.view', 'record.values', 'record.runUntil', 'record.stop', 'record.registers', 'record.memory',
  'record.instruction', 'record.fieldPaths', 'record.markPc', 'record.markRegisterFile', 'record.registerMapping', 'record.setRegisterMapping',
  'trace.influence', 'trace.net', 'flow.path', 'flow.activePath', 'edit.signalGroup', 'edit.areaMemo',
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
  stackBase: { componentId: string; target: string; part: string; stack: string; sp: string | null }[];   // part: "main › Data Memory", stack: its stack region
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
