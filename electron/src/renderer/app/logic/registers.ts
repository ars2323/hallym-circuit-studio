/* The Registers panel's rows (logic only; shared/registers.ts draws them,
   the way Hallym MIPS's register panel does).  The engine finds the
   student's registers by v1's rules -- a marked register file, else every
   Register, numbered by their labels; PC by Mark as PC, a label PC, or
   what drives the Instruction Memory's address (D-076, D-103, D-108) --
   and sends them in Hallym MIPS's groups.  Hex, Dec and Bin together, as
   in v1 and in Hallym MIPS; nothing here judges a value. */

import type { RegisterRow } from '../../../main/protocol.ts';
import { binGroups, dec, hex } from './values.ts';

export interface RegisterView {
  key: string;
  name: string;
  alias: string;            // the circuit's own name beside, quieter ('' none)
  group: string;
  hex: string;              // "0x0000002a", x and E digits for a partly defined value, '' not recorded
  dec: string;              // signed decimal, '' when not all bits are defined
  bin: string[];            // four bits to a group
  changed: boolean;
  zero: boolean;
  componentId: string | null;
  markable: boolean;
  markedPc: boolean;
}

export function registerViews(rows: RegisterRow[]): RegisterView[] {
  return rows.map((r) => ({
    key: r.key,
    name: r.name,
    alias: r.alias ?? '',
    group: r.group,
    hex: hex(r.value),
    dec: dec(r.value),
    bin: binGroups(r.value),
    // PC moves every cycle: like Hallym MIPS, it is never the "just changed" row.
    changed: r.changed && r.key !== 'PC',
    zero: r.value !== null && /^0+$/.test(r.value),
    componentId: r.componentId ?? null,
    markable: r.markable === true,
    markedPc: r.markedPc === true,
  }));
}

// The groups in order, each with its first and last register's name (Hallym MIPS's band: "Temporaries  $t0–$t9").
export function registerGroups(views: RegisterView[]): { title: string; span: string; keys: string[] }[] {
  const out: { title: string; span: string; keys: string[] }[] = [];
  for (const v of views) {
    const last = out[out.length - 1];
    if (last && last.title === v.group) last.keys.push(v.key);
    else out.push({ title: v.group, span: '', keys: [v.key] });
  }
  const name = new Map(views.map((v) => [v.key, v.name]));
  for (const g of out) {
    const a = name.get(g.keys[0]) ?? '';
    const b = name.get(g.keys[g.keys.length - 1]) ?? '';
    g.span = g.keys.length > 1 ? `${a}–${b}` : a;
  }
  return out;
}

// The set of rows (their keys) the panel is built for: a new set rebuilds the rows, the same set only updates cells.
export const shape = (views: RegisterView[]): string => views.map((v) => `${v.group}|${v.key}|${v.name}|${v.alias}|${v.markedPc}`).join('\n');
