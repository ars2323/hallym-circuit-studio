/* The Messages panel's model (logic only; the panel is ../messages.ts):
   what the engine says cannot work (docs/engine-api.md diag.*, D-143),
   grouped by kind in the engine's order, counted, and turned into the
   place to show when one is chosen.

   Facts are names, in English (a group's name, "3 messages", "Cycle 2");
   the messages themselves are the engine's Korean sentences, the student's
   names in them as they are (CLAUDE.md 2.6: fact and place only). */

import type { DiagCode, DiagMessage, Point } from '../../../main/protocol.ts';

// The kinds in the engine's order (v1's Diagnostic.Kind): the wiring first,
// then what the simulation found.  A name each, English (a fact).
export const GROUPS: readonly { code: DiagCode; name: string }[] = [
  { code: 'CLOCK_UNCONNECTED', name: 'Clock not connected' },
  { code: 'SHORT', name: 'Two outputs on one wire' },
  { code: 'WIDTH_MISMATCH', name: 'Bit width mismatch' },
  { code: 'INPUT_UNCONNECTED', name: 'Input not connected' },
  { code: 'INPUT_UNDRIVEN', name: 'Nothing drives the wire' },
  { code: 'TUNNEL_UNPAIRED', name: 'Tunnel without a pair' },
  { code: 'SUBCIRCUIT_PORT_UNCONNECTED', name: 'Subcircuit port not connected' },
  { code: 'COMBINATIONAL_LOOP', name: 'Combinational loop' },
  { code: 'MEMORY_OVERLAP', name: 'Memory regions overlap' },
  { code: 'E_APPEARED', name: 'E value' },
  { code: 'X_WRITE_DATA', name: 'Undefined value written' },
  { code: 'X_WRITE_CONTROL', name: 'Undefined write input' },
  { code: 'OSCILLATION', name: 'Oscillation' },
  { code: 'MIPS_STATUS', name: 'Hallym MIPS part' },
];

export interface Group {
  code: DiagCode | 'OTHER';
  name: string;
  messages: DiagMessage[];
}

// The messages by kind: groups in GROUPS order, each message in the
// engine's order within its group; a kind this window does not know yet
// goes last under "Other" (a newer engine), never dropped.
export function groupMessages(list: readonly DiagMessage[]): Group[] {
  const known = new Map<string, Group>();
  for (const g of GROUPS) known.set(g.code, { code: g.code, name: g.name, messages: [] });
  const other: Group = { code: 'OTHER', name: 'Other', messages: [] };
  for (const m of list) (known.get(m.code) ?? other).messages.push(m);
  return [...known.values(), other].filter((g) => g.messages.length > 0);
}

// "No messages", "1 message", "12 messages": the status bar and the tab.
export function messageCount(n: number): string {
  if (n === 0) return 'No messages';
  return `${n.toLocaleString('en-US')} message${n === 1 ? '' : 's'}`;
}

// The sentence to show: the engine's Korean (the window's sentences are Korean, D-135).
export const sentence = (m: DiagMessage): string => m.text.ko;

// A fact under the sentence: where (the circuit's name) and, for what the
// simulation found, the cycle -- "main · Cycle 2".
export function placeLine(m: DiagMessage, circuitName: (circuitId: string) => string): string {
  const parts = [circuitName(m.location.circuitId)];
  if (m.kind === 'dynamic' && typeof m.location.cycle === 'number') parts.push(`Cycle ${m.location.cycle.toLocaleString('en-US')}`);
  return parts.join(' · ');
}

/* "Show this place": the event the panel sends when a message is chosen
   (../reveal.ts).  The Canvas goes to the circuit (through the instances of
   path from root, for a message the simulation found), marks the parts,
   wires and nets and brings `at` into view; the Cycle View goes to `cycle`.
   The same shape serves anything else that points at a place (E/X origin). */
export interface Reveal {
  fileId: string;
  messageId: string | null;
  circuitId: string;
  root: string;
  path: string[];
  components: string[];
  wires: string[];
  nets: string[];
  at: Point | null;
  cycle: number | null;
  // How the Canvas marks it: a message's error colour (the default), or the selection's
  // blue for a part found by name (Find, Tunnels, the search palette; N-12, D-150).
  tone?: 'error' | 'find';
}

export function revealOf(fileId: string, m: DiagMessage): Reveal {
  const l = m.location;
  return {
    fileId, messageId: m.id, circuitId: l.circuitId, root: l.root, path: [...l.path],
    components: [...l.components], wires: [...l.wires], nets: [...l.nets],
    at: l.at ? [l.at[0], l.at[1]] : null,
    cycle: m.kind === 'dynamic' && typeof l.cycle === 'number' ? l.cycle : null,
  };
}

// The message chosen before a new list came: the same id if it is still
// there (the engine keeps an id while its cause stays), else none.
export function keepChosen(chosen: string | null, list: readonly DiagMessage[]): string | null {
  return chosen !== null && list.some((m) => m.id === chosen) ? chosen : null;
}

// While the circuit oscillates the panel offers Reset Simulation (v1 I-166).
export const oscillating = (list: readonly DiagMessage[]): boolean => list.some((m) => m.code === 'OSCILLATION');
