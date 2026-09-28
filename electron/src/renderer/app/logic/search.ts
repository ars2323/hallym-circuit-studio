/* Finding a part, a circuit, a tunnel or a command by what the student
   types (v1 Palette, D-037, I-110, I-168..I-170; D-150): the search
   palette (Ctrl+K) and the Components list's search box use this one model.

   A name matches by the part's saved name, its shown name, or an alias
   (Korean names and short forms: 앤드, 먹스, reg, mux …), and scores
   100 for the same name, 80 for the start of one, 50 for a part of one,
   30 for its letters in order from its first (fuzzy, three letters or
   more: "rgw" finds RegWrite), else not at all.  Favourites +30, recent ones up to +20, this file's circuits +15,
   commands −5; ties by name.  A number after the name sets an attribute
   (tool-args.ts, v1 attributesFor): a gate's number of inputs, a
   splitter's incoming width, a constant's value after 0x, else the width;
   a number the original would not take leaves that part out.

   Recent and favourite parts are kept for this run only (the lab-PC rule:
   nothing persists between launches). */

import type { LibraryGroup } from '../../../main/protocol.ts';
import { TOOL_ARGS } from './tool-args.ts';

export type ItemKind = 'component' | 'subcircuit' | 'tunnel' | 'command';

export type CommandId = 'reset' | 'cycle' | 'run' | 'enable' | 'load' | 'fit' | 'find' | 'editSplitter';

export interface Command { id: CommandId; name: string; aliases: string[] }

// The commands (names as on the toolbar and in the original's menus; aliases a student may type).
export const COMMANDS: readonly Command[] = [
  { id: 'reset', name: 'Reset Simulation', aliases: ['reset', '리셋', '초기화'] },
  { id: 'cycle', name: '1 Cycle', aliases: ['cycle', 'tick', 'tick once', '클럭 한 번', '클럭', '사이클'] },
  { id: 'run', name: 'Ticks Enabled', aliases: ['run', 'stop', 'ticks', '실행', '정지'] },
  { id: 'enable', name: 'Simulation Enabled', aliases: ['simulation', '시뮬레이션', '시뮬레이션 켜기'] },
  { id: 'load', name: 'Load Program…', aliases: ['load', 'program', 'hmx', '.hmx', '프로그램', '불러오기'] },
  { id: 'fit', name: 'Fit to Window', aliases: ['fit', '화면 맞춤', '맞춤'] },
  { id: 'find', name: 'Find', aliases: ['find', '찾기'] },
  { id: 'editSplitter', name: 'Edit Splitter…', aliases: ['edit splitter', 'splitter', '스플리터 편집', '스플리터'] },
];

// A part's saved name → what else a student may call it (v1 Palette.ALIASES).
export const ALIASES: Readonly<Record<string, readonly string[]>> = {
  'AND Gate': ['and', '앤드', '논리곱', 'and게이트'],
  'OR Gate': ['or', '오어', '논리합', 'or게이트'],
  'NOT Gate': ['not', '낫', '인버터', 'inv', '부정'],
  'NAND Gate': ['nand', '낸드'],
  'NOR Gate': ['nor', '노어'],
  'XOR Gate': ['xor', '엑스오어', '배타적'],
  'XNOR Gate': ['xnor', '엑스노어'],
  Buffer: ['buf', '버퍼'],
  Multiplexer: ['mux', '먹스', '멀티플렉서', '선택기'],
  Demultiplexer: ['demux', '디먹스', '디멀티플렉서'],
  Decoder: ['dec', '디코더'],
  'Priority Encoder': ['enc', '인코더', '우선순위'],
  Register: ['reg', '레지스터'],
  Counter: ['ctr', 'cnt', '카운터'],
  RAM: ['ram', '램', '메모리'],
  ROM: ['rom', '롬'],
  'D Flip-Flop': ['dff', 'd플립플롭', '플립플롭'],
  Adder: ['add', 'adder', '가산기', '덧셈'],
  Subtractor: ['sub', '감산기', '뺄셈'],
  Multiplier: ['mul', '곱셈기', '곱셈'],
  Divider: ['div', '나눗셈기', '나눗셈'],
  Comparator: ['cmp', '비교기'],
  Shifter: ['shift', 'sll', '시프터'],
  Negator: ['neg', '부호반전'],
  Splitter: ['split', '스플리터', '분배기'],
  Tunnel: ['tunnel', '터널'],
  Pin: ['pin', '핀', '입력', '출력', 'in', 'out'],
  Probe: ['probe', '프로브'],
  Constant: ['const', '상수'],
  Clock: ['clk', 'clock', '클럭'],
  'Bit Extender': ['ext', 'sext', 'zext', '확장', '부호확장'],
  'Pull Resistor': ['pull', '풀'],
  'Instruction Memory': ['imem', '명령어메모리', 'instruction'],
  'Data Memory': ['dmem', '데이터메모리'],
  Console: ['console', '콘솔', 'syscall'],
  'Radix Probe': ['rprobe', '진법', '다중진법'],
};

export interface SearchItem {
  kind: ItemKind;
  key: string;                        // one per item: "component Gates/AND Gate", "tunnel pc", "command reset"
  name: string;                       // shown (English: the engine's names)
  group?: string;                     // where it is from: the library's shown name, the file's name
  lib?: string | null;                // component: model.library's lib; subcircuit: null
  tool?: string;                      // component, subcircuit: the tool's name
  circuitId?: string;                 // subcircuit
  pending?: boolean;                  // from a library not in the file yet (the bundled Hallym MIPS, V-01)
  attrs: Record<string, string>;      // from the number
  attrText: string;                   // the same in the attribute table's words: "Number Of Inputs 3"
  count?: number;                     // tunnel: how many in the circuit
  command?: CommandId;
  score: number;
}

export interface Sources {
  libraries: LibraryGroup[] | null;   // model.library (this file's circuits first)
  fileName?: string;                  // the first group's name
  current?: string;                   // the circuit on show (its circuitId)
  includeCurrent?: boolean;           // list the circuit on show among the circuits (Components' search)
  tunnels?: { name: string; count: number }[];
  commands?: readonly CommandId[];    // the commands that can run now
  recent?: readonly string[];         // item keys, most recent first
  favorites?: readonly string[];      // item keys
}

// "and 3" → ["and", "3"]; "0x1f" stays a number too.
export function split(query: string): [string, string | null] {
  const q = query.trim().replace(/\s+/g, ' ');
  const sp = q.lastIndexOf(' ');
  if (sp > 0) {
    const last = q.slice(sp + 1);
    if (/^(0x[0-9a-fA-F]+|\d+)$/.test(last)) return [q.slice(0, sp), last];
  }
  return [q, null];
}

const squeeze = (s: string) => s.toLowerCase().replace(/\s+/g, '');

// Its letters in order: "rgw" in "regwrite".
export function fuzzy(q: string, name: string): boolean {
  let i = 0;
  for (const ch of name) if (i < q.length && ch === q[i]) i++;
  return i === q.length;
}

// The best score of the query among a thing's names (squeezed: no spaces, lower case).
export function match(q: string, names: readonly string[]): number {
  let best = 0;
  for (const n of names) {
    const a = squeeze(n);
    if (a === q) best = Math.max(best, 100);
    else if (a.startsWith(q)) best = Math.max(best, 80);
    else if (a.includes(q)) best = Math.max(best, 50);
    else if (q.length >= 3 && a[0] === q[0] && fuzzy(q, a)) best = Math.max(best, 30);
  }
  return best;
}

/* What the number sets on this tool: {} when it takes none (or no number was
   typed), null when the original would not take that number (the part is not
   listed then). */
export function argAttrs(lib: string | null | undefined, tool: string, arg: string | null): Record<string, string> | null {
  if (arg === null) return {};
  const a = TOOL_ARGS[`${lib ?? ''}/${tool}`];
  if (!a) return {};
  const hex = /^0x/i.test(arg);
  if (hex && a.hex) return { [a.hex]: arg };
  const n = hex ? Number.parseInt(arg.slice(2), 16) : Number(arg);
  if (!Number.isSafeInteger(n) || n < a.min || n > a.max) return null;
  return { [a.attr]: String(n) };
}

export function attrText(lib: string | null | undefined, tool: string, attrs: Record<string, string>): string {
  const a = TOOL_ARGS[`${lib ?? ''}/${tool}`];
  const label = (k: string) => (a && k === a.attr ? a.label : a && k === a.hex ? 'Value' : k);
  return Object.entries(attrs).map(([k, v]) => `${label(k)} ${v}`).join(', ');
}

function boost(key: string, s: Sources): number {
  let b = 0;
  if (s.favorites?.includes(key)) b += 30;
  const i = s.recent?.indexOf(key) ?? -1;
  if (i >= 0) b += Math.max(5, 20 - 2 * i);
  return b;
}

export function search(query: string, s: Sources): SearchItem[] {
  const [name, arg] = split(query);
  const q = squeeze(name);
  const out: SearchItem[] = [];
  if (!q) return out;
  for (const g of s.libraries ?? []) {
    if (g.lib === null) {
      // this file's circuits: placed as subcircuits
      for (const t of g.tools) {
        if (t.circuitId === s.current && !s.includeCurrent) continue;
        const sc = match(q, [t.name]);
        if (!sc) continue;
        const key = `subcircuit ${t.name}`;
        out.push({ kind: 'subcircuit', key, name: t.name, group: s.fileName, lib: null, tool: t.name, circuitId: t.circuitId, attrs: {}, attrText: '', score: sc + 15 + boost(key, s) });
      }
      continue;
    }
    for (const t of g.tools) {
      const sc = match(q, [t.name, t.display, ...(ALIASES[t.name] ?? [])]);
      if (!sc) continue;
      const attrs = argAttrs(g.lib, t.name, arg);
      if (attrs === null) continue;      // the number does not fit this part
      const key = `component ${g.lib}/${t.name}`;
      out.push({
        kind: 'component', key, name: t.display, group: g.display ?? g.lib, lib: g.lib, tool: t.name, pending: g.pending || undefined,
        attrs, attrText: attrText(g.lib, t.name, attrs), score: sc + boost(key, s),
      });
    }
  }
  for (const t of s.tunnels ?? []) {
    const sc = match(q, [t.name]);
    if (sc) out.push({ kind: 'tunnel', key: `tunnel ${t.name}`, name: t.name, count: t.count, attrs: {}, attrText: '', score: sc });
  }
  for (const c of COMMANDS) {
    if (!s.commands?.includes(c.id)) continue;
    const sc = match(q, [c.name, ...c.aliases]);
    if (sc) out.push({ kind: 'command', key: `command ${c.id}`, name: c.name, command: c.id, attrs: {}, attrText: '', score: sc - 5 });
  }
  return out.sort((a, b) => b.score - a.score || a.name.localeCompare(b.name, 'en') || a.key.localeCompare(b.key, 'en'));
}

// The recent list: this one first, at most 8.
export function touch(recent: readonly string[], key: string): string[] {
  return [key, ...recent.filter((k) => k !== key)].slice(0, 8);
}

// Favourites: in, or out again.
export function toggleFavorite(favorites: readonly string[], key: string): string[] {
  return favorites.includes(key) ? favorites.filter((k) => k !== key) : [...favorites, key];
}
