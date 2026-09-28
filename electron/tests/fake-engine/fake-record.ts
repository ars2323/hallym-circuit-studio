/* The fake engine's record.* (docs/engine-api.md "record", N-14): enough of
   a recording for the window's tests and screenshots, with no Logisim in
   it.  A file with an Instruction Memory "runs" the recursive factorial
   (tests/mips/factorial.s, its words as the repository's disassembler
   golden tests/disasm/mips-factorial.txt lists them) on a tiny machine
   here, one instruction a cycle from 0x00400024: so PC, the instruction,
   the registers, the data and the stack move the way a single-cycle CPU's
   would.  Any other file records only the rows added to it, whose values
   are a fixed pattern of their name and the cycle.  Plain Node, no
   imports from src/ (the client is tested against something it did not
   write). */

type Params = Record<string, unknown>;

export interface RecordFile {
  fileId: string;
  cycle: number;            // the latest recorded cycle (the fake's sim.state cycle)
  view: number | null;      // a past cycle on show
  generation: number;
  cpu: boolean;
  rows: { id: string; name: string; width: number; bits: boolean; temp: boolean }[];
  pinnedCycle: number;
  markedPc: boolean;
  wide: boolean;            // FAKE_ENGINE_MODE wide-registers
  regfile: boolean;
  names: Map<string, string>;   // component loc "x,y" -> its label (for addRow at)
}

// factorial.s as assembled (tests/disasm/mips-factorial.txt): address, word, text.
const PROGRAM: [number, number, string][] = [
  [0x00400000, 0x8fa40000, 'lw $4, 0($29)'], [0x00400004, 0x27a50004, 'addiu $5, $29, 4'],
  [0x00400008, 0x24a60004, 'addiu $6, $5, 4'], [0x0040000c, 0x00041080, 'sll $2, $4, 2'],
  [0x00400010, 0x00c23021, 'addu $6, $6, $2'], [0x00400014, 0x0c100009, 'jal 0x00400024 [main]'],
  [0x00400018, 0x00000000, 'nop'], [0x0040001c, 0x3402000a, 'ori $2, $0, 10'], [0x00400020, 0x0000000c, 'syscall'],
  [0x00400024, 0x3c017fff, 'lui $1, 32767'], [0x00400028, 0x343deffc, 'ori $29, $1, -4100'],
  [0x0040002c, 0x34040006, 'ori $4, $0, 6'], [0x00400030, 0x0c100016, 'jal 0x00400058 [fact]'],
  [0x00400034, 0x00028021, 'addu $16, $0, $2'], [0x00400038, 0x3c041001, 'lui $4, 4097 [msg]'],
  [0x0040003c, 0x34020004, 'ori $2, $0, 4'], [0x00400040, 0x0000000c, 'syscall'], [0x00400044, 0x00102021, 'addu $4, $0, $16'],
  [0x00400048, 0x34020001, 'ori $2, $0, 1'], [0x0040004c, 0x0000000c, 'syscall'], [0x00400050, 0x3402000a, 'ori $2, $0, 10'],
  [0x00400054, 0x0000000c, 'syscall'], [0x00400058, 0x23bdfff8, 'addi $29, $29, -8'], [0x0040005c, 0xafbf0004, 'sw $31, 4($29)'],
  [0x00400060, 0xafa40000, 'sw $4, 0($29)'], [0x00400064, 0x14800004, 'bne $4, $0, 16 [recurse-0x00400064]'],
  [0x00400068, 0x34020001, 'ori $2, $0, 1'], [0x0040006c, 0x23bd0008, 'addi $29, $29, 8'], [0x00400070, 0x03e00008, 'jr $31'],
  [0x00400074, 0x2084ffff, 'addi $4, $4, -1'], [0x00400078, 0x0c100016, 'jal 0x00400058 [fact]'],
  [0x0040007c, 0x8fa40000, 'lw $4, 0($29)'], [0x00400080, 0x8fbf0004, 'lw $31, 4($29)'], [0x00400084, 0x23bd0008, 'addi $29, $29, 8'],
  [0x00400088, 0x70821002, 'mul $2, $4, $2'], [0x0040008c, 0x03e00008, 'jr $31'],
];
const WORDS = new Map(PROGRAM.map(([a, w, t]) => [a, { w, t }]));
const DATA: [number, number][] = [[0x10010000, 0x3d202136], [0x10010004, 0x00000020]]; // "6! = "
const LABELS: [number, string][] = [[0x10010000, 'msg']];
const ENTRY = 0x00400024;
const REG = ['$zero', '$at', '$v0', '$v1', '$a0', '$a1', '$a2', '$a3', '$t0', '$t1', '$t2', '$t3', '$t4', '$t5', '$t6', '$t7',
  '$s0', '$s1', '$s2', '$s3', '$s4', '$s5', '$s6', '$s7', '$t8', '$t9', '$k0', '$k1', '$gp', '$sp', '$fp', '$ra'];
const GROUPS: [string, number[]][] = [['Constant', [0]], ['Return values', [2, 3]], ['Arguments', [4, 5, 6, 7]],
  ['Temporaries', [8, 9, 10, 11, 12, 13, 14, 15, 24, 25]], ['Saved', [16, 17, 18, 19, 20, 21, 22, 23]], ['Pointers', [28, 29, 30]],
  ['Return address', [31]], ['Reserved', [1, 26, 27]]];

interface Machine { pc: number; regs: number[]; mem: Map<number, number>; lowest: number; halted: boolean }

// The machine after `cycles` instructions (one a cycle; after exit it stays).
function machine(f: RecordFile, cycles: number): Machine {
  const m: Machine = { pc: ENTRY, regs: new Array(32).fill(0), mem: new Map(DATA), lowest: -1, halted: false };
  // FAKE_ENGINE_MODE wide-registers: the widest values the Registers panel shows ($s6 = -1, $s7 = -2147483648)
  if (f.wide) { m.regs[22] = 0xffffffff; m.regs[23] = 0x80000000; }
  for (let c = 0; c < cycles && !m.halted; c += 1) step(m);
  return m;
}

function step(m: Machine): void {
  const w = WORDS.get(m.pc)?.w ?? 0;
  const op = w >>> 26;
  const rs = (w >>> 21) & 31;
  const rt = (w >>> 16) & 31;
  const rd = (w >>> 11) & 31;
  const imm = (w << 16) >> 16;
  const uimm = w & 0xffff;
  const r = m.regs;
  let next = (m.pc + 4) >>> 0;
  const set = (n: number, v: number) => { if (n !== 0) r[n] = v >>> 0; };
  if (op === 0) {
    const fn = w & 63;
    if (fn === 0x21) set(rd, r[rs] + r[rt]);
    else if (fn === 0x08) next = r[rs];
    else if (fn === 0x0c && r[2] === 10) m.halted = true;
  } else if (op === 0x1c) set(rd, Math.imul(r[rs], r[rt]));
  else if (op === 0x0f) set(rt, uimm << 16);
  else if (op === 0x0d) set(rt, r[rs] | uimm);
  else if (op === 0x08 || op === 0x09) set(rt, r[rs] + imm);
  else if (op === 0x03) { set(31, m.pc + 4); next = ((m.pc + 4) & 0xf0000000) | ((w & 0x3ffffff) << 2); }
  else if (op === 0x05) { if (r[rs] !== r[rt]) next = (m.pc + (imm << 2)) >>> 0; }
  else if (op === 0x2b) { const a = (r[rs] + imm) >>> 0; m.mem.set(a, r[rt]); if (a >= 0x7ffc0000) m.lowest = m.lowest < 0 ? a : Math.min(m.lowest, a); }
  else if (op === 0x23) { const a = (r[rs] + imm) >>> 0; set(rt, m.mem.get(a) ?? 0); if (a >= 0x7ffc0000) m.lowest = m.lowest < 0 ? a : Math.min(m.lowest, a); }
  m.pc = next >>> 0;
}

const bits = (v: number, width = 32) => (v >>> 0).toString(2).padStart(width, '0').slice(-width);
const hex32 = (v: number) => `0x${(v >>> 0).toString(16).padStart(8, '0')}`;
const hex8 = (v: number) => (v >>> 0).toString(16).padStart(8, '0');
const hash = (s: string) => [...s].reduce((h, c) => (Math.imul(h, 31) + c.charCodeAt(0)) >>> 0, 7);

export function newRecordFile(fileId: string, cpu: boolean, names: Map<string, string>, wide = false): RecordFile {
  return { fileId, cycle: 0, view: null, generation: 1, cpu, rows: [], pinnedCycle: -1, markedPc: false, regfile: false, names, wide };
}

const shown = (f: RecordFile) => f.view ?? f.cycle;

export function recordState(f: RecordFile, until: { kind: string; value?: string; from: number } | null = null): Params {
  const c = shown(f);
  return {
    fileId: f.fileId, empty: false, first: 0, last: f.cycle, cycle: c, past: f.view !== null && f.view < f.cycle, generation: f.generation,
    pc: f.cpu ? hex32(machine(f, c).pc) : null, cpu: f.cpu, rows: f.rows.filter((r) => !r.temp).length, pinned: f.rows.filter((r) => r.temp).length,
    runUntil: until,
  };
}

// A row's value in a cycle: a fixed pattern of its name (a CPU file's rows follow the machine where they can).
function rowValue(f: RecordFile, row: RecordFile['rows'][number], step: number): string {
  const c = Math.floor(step / 2);
  if (f.cpu && /^pc$/i.test(row.name)) return bits(machine(f, c).pc, row.width);
  if (row.width === 1) return (hash(row.name) + step) % 3 === 0 ? '1' : '0';
  return bits(hash(row.name) + c * ((hash(row.name) % 5) + 1), row.width);
}

export function recordTable(f: RecordFile, p: Params): Params {
  const last = f.cycle;
  const to = typeof p.to === 'number' ? Math.min(last, Math.max(0, p.to)) : last;
  const from = typeof p.from === 'number' ? Math.max(0, Math.min(to, p.from)) : Math.max(0, to - 59);
  const columns = [];
  for (let c = from; c <= to; c += 1) {
    const m = machine(f, c);
    const ins = WORDS.get(m.pc);
    columns.push(f.cpu ? { cycle: c, pc: hex32(m.pc), word: ins ? hex32(ins.w) : null, text: ins?.t ?? '' } : { cycle: c, pc: null, word: null, text: '' });
  }
  const rows = [...f.rows].sort((a, b) => Number(b.temp) - Number(a.temp)).map((r) => {
    const values: string[] = [];
    const halves: string[] = [];
    for (let c = from; c <= to; c += 1) {
      values.push(rowValue(f, r, 2 * c));
      halves.push(rowValue(f, r, Math.max(0, 2 * c - 1)));
    }
    return { ...r, values, ...(r.width === 1 ? { halves } : {}) };
  });
  return { fileId: f.fileId, empty: false, first: 0, last, cycle: shown(f), from, to, cpu: f.cpu, pinnedCycle: f.pinnedCycle, columns, rows };
}

let nextRow = 1;
export function addRow(f: RecordFile, p: Params): Params {
  const at = Array.isArray(p.at) ? `${p.at[0]},${p.at[1]}` : '';
  const name = f.names.get(at) ?? (typeof p.netId === 'string' ? p.netId : at || 'row');
  const width = /pc|instr|alu|data|addr|result|rd|imm/i.test(name) ? 32 : 1;
  const had = f.rows.find((r) => !r.temp && r.name === name);
  if (had) return { id: had.id, added: false };
  const row = { id: `r${nextRow++}`, name, width, bits: false, temp: false };
  f.rows.push(row);
  return { id: row.id, added: true, name, width };
}

// Where a file's registers are, as ids of its model now (the fake's ids change with edits): its main
// circuit, the Register labelled PC there, the circuit named regfile.
export interface Places { main: string; pc: string | null; regfile: string | null }

export function registers(f: RecordFile, cycle: number | undefined, at: Places): Params {
  const c = cycle ?? shown(f);
  if (!f.cpu) return { fileId: f.fileId, cycle: c, mode: 'none', rows: [], candidates: [] };
  const now = machine(f, c);
  const before = c > 0 ? machine(f, c - 1) : now;
  const rows: Params[] = [{
    key: 'PC', name: 'PC', number: -1, group: 'Special', value: bits(now.pc), changed: c > 0 && now.pc !== before.pc,
    alias: 'Register #1', componentId: at.pc ?? 'kpc', markable: true, ...(f.markedPc ? { markedPc: true } : {}),
  }];
  for (const [title, numbers] of GROUPS) {
    for (const n of numbers) {
      if (n === 0 && !f.regfile) continue; // like ref-mips: no $0 register part
      rows.push({ key: REG[n], name: REG[n], number: n, group: title, value: bits(now.regs[n]), changed: c > 0 && now.regs[n] !== before.regs[n],
        alias: `$${n}`, componentId: `kr${n}` });
    }
  }
  return {
    fileId: f.fileId, cycle: c, circuitId: at.main, mode: f.regfile ? 'file' : 'all', unmapped: false, rows,
    ...(f.regfile ? { registerFile: { circuitId: at.regfile ?? 'c-regfile', name: 'regfile' } }
      : { candidates: at.regfile ? [{ circuitId: at.regfile, name: 'regfile', registers: 31 }] : [] }),
  };
}

export function memory(f: RecordFile): Params {
  if (!f.cpu) return { fileId: f.fileId, parts: 0, rows: [] };
  const m = machine(f, shown(f));
  const sp = m.regs[29];
  const rows: Params[] = [{ kind: 'section', section: 'data', part: 'Data Memory', addr: '0x10010000', end: '0x100fffff' }];
  const label = (from: number) => LABELS.filter(([a]) => a >= from && a < from + 16).map(([a, n]) => ({ addr: hex32(a), names: [n] }));
  rows.push({ kind: 'words', section: 'data', part: 'Data Memory', addr: '0x10010000', end: '0x1001000f',
    words: [0, 4, 8, 12].map((o) => hex8(m.mem.get(0x10010000 + o) ?? 0)), labels: label(0x10010000) });
  rows.push({ kind: 'zeros', section: 'data', part: 'Data Memory', addr: '0x10010010', end: '0x100fffff', count: (0x10100000 - 0x10010010) / 4 });
  const base = 0x7fffeffc;
  const inStack = sp >= 0x7ffc0000 && sp <= 0x7fffffff;
  const bottom = Math.min(inStack ? sp : base, m.lowest >= 0 ? m.lowest : base) & ~15;
  rows.push({ kind: 'section', section: 'stack', part: 'Data Memory', addr: hex32(bottom), end: '0x7fffffff', base: hex32(base),
    depth: inStack ? base - sp : -1, peak: m.lowest >= 0 ? base - m.lowest : 0 });
  rows.push({ kind: 'zeros', section: 'stack', part: 'Data Memory', addr: '0x7ffff000', end: '0x7fffffff', count: 1024 });
  for (let line = 0x7fffeff0; line >= bottom; line -= 16) {
    const ptr = inStack && sp >= line && sp < line + 16 ? { pointers: { $sp: hex32(sp) } } : {};
    rows.push({ kind: 'words', section: 'stack', part: 'Data Memory', addr: hex32(line), end: hex32(line + 15),
      words: [0, 4, 8, 12].map((o) => hex8(m.mem.get(line + o) ?? 0)), ...ptr });
  }
  return { fileId: f.fileId, cycle: shown(f), parts: 1, rows };
}

export function instruction(f: RecordFile, cycle?: number): Params {
  const c = cycle ?? shown(f);
  if (!f.cpu) return { fileId: f.fileId, cycle: c, none: 'noCpu' };
  const m = machine(f, c);
  const ins = WORDS.get(m.pc);
  if (!ins) return { fileId: f.fileId, cycle: c, pc: hex32(m.pc), none: 'undefined' };
  const w = ins.w >>> 0;
  const op = w >>> 26;
  const format = op === 0 || op === 0x1c ? 'R' : op === 2 || op === 3 ? 'J' : 'I';
  const field = (name: string, hi: number, lo: number, meaning: (v: number) => string) => {
    const v = (w >>> lo) & (2 ** (hi - lo + 1) - 1);
    const value = name === 'immediate' ? String((v << 16) >> 16) : String(v);
    return { name, hi, lo, bits: v.toString(2).padStart(hi - lo + 1, '0'), value, meaning: meaning(v) };
  };
  const name = ins.t.split(' ')[0];
  const reg = (v: number) => REG[v];
  const fields = [field('opcode', 31, 26, () => (format === 'R' ? (op === 0 ? 'R-type' : 'SPECIAL2') : name))];
  if (format === 'R') {
    fields.push(field('rs', 25, 21, reg), field('rt', 20, 16, reg), field('rd', 15, 11, reg), field('shamt', 10, 6, String), field('funct', 5, 0, () => name));
  } else if (format === 'J') {
    fields.push(field('target', 25, 0, (v) => hex32(((m.pc + 4) & 0xf0000000) | (v << 2))));
  } else {
    fields.push(field('rs', 25, 21, reg), field('rt', 20, 16, reg), field('immediate', 15, 0, (v) => `0x${v.toString(16).padStart(4, '0')}`));
  }
  return { fileId: f.fileId, cycle: c, pc: hex32(m.pc), word: hex32(w), text: ins.t, mnemonic: name, format, fields };
}

// Run Until: the first cycle after the one on show where the condition holds, within maxCycles.
export function runUntil(f: RecordFile, p: Params): { result: string; cycle: number; from: number } {
  const from = shown(f);
  const max = typeof p.maxCycles === 'number' && p.maxCycles > 0 ? p.maxCycles : 10_000;
  const kind = String(p.kind);
  const value = typeof p.value === 'string' ? p.value : '';
  const m = machine(f, from);
  for (let c = from + 1; c <= from + max; c += 1) {
    const wasHalted = m.halted;
    if (!m.halted) step(m);
    const text = WORDS.get(m.pc)?.t ?? '';
    const met = kind === 'pc' ? f.cpu && m.pc === parseInt(value.replace(/^0x/i, ''), 16)
      : kind === 'instruction' ? f.cpu && text.split(' ')[0] === value
      : kind === 'halt' ? f.cpu && m.halted && !wasHalted
      : kind === 'row' ? true
      : false;
    if (met) return { result: 'met', cycle: c, from };
  }
  return { result: 'limit', cycle: from + max, from };
}
