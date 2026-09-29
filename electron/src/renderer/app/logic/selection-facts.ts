/* What the Attributes panel says about the selection until the attribute
   table comes (N-10; D-146): facts only, in English like the status bar --
   one part: its label and kind, where it is, its facing; several: how many
   parts and wires, and their names.  Parts pasted or duplicated and not yet
   placed (the engine's floating selection) count as parts too. */

import type { Component, Wire } from '../../../main/protocol.ts';

export interface SelectionFacts {
  title: string;
  lines: string[];
}

export const MAX_NAMES = 8;

const plural = (n: number, one: string) => `${n} ${one}${n === 1 ? '' : 's'}`;

// A part's name as the student reads it: its label and its kind ("PC · Register"), or the kind alone.
export const partName = (c: Component): string => (c.attrs.label ? `${c.attrs.label} · ${c.name}` : c.name);

const isWire = (x: Component | Wire): x is Wire => 'a' in x && 'b' in x && !('name' in x);

// null: nothing selected (the panel's empty state).
export function selectionFacts(chosen: (Component | Wire)[]): SelectionFacts | null {
  const parts = chosen.filter((x): x is Component => !isWire(x));
  const wires = chosen.length - parts.length;
  if (chosen.length === 0) return null;
  if (parts.length === 1 && wires === 0) {
    const c = parts[0];
    const lines = [`Location (${c.loc[0]}, ${c.loc[1]})`];
    if (c.facing) lines.push(`Facing ${c.facing}`);
    return { title: partName(c), lines };
  }
  const title = [parts.length ? plural(parts.length, 'component') : '', wires ? plural(wires, 'wire') : ''].filter(Boolean).join(', ');
  // in name order: the engine's selection is a set with no order of its own, and the same selection reads the same (D-158)
  const names = parts.map(partName).sort((a, b) => a.localeCompare(b, 'en'));
  const lines = names.slice(0, MAX_NAMES);
  if (names.length > MAX_NAMES) lines.push(`and ${names.length - MAX_NAMES} more`);
  return { title, lines };
}
