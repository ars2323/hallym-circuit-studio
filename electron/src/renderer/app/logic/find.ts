/* The Find window's model (v1 FindDialog, D-036, #135, S-09, S-28, I-171,
   I-172; D-150): what the engine found (find.query) as rows -- one row a
   group (the same kind, name and path; "(7 places)"), and under an opened
   group a row per place, named by the port it is next to ("next to
   main › PC (D)") or its numbered name, never by coordinates or an
   internal port name.  Going to a place is "show this place" (reveal.ts)
   with the selection's look. */

import type { FindGroup, FindKind, FindPlace } from '../../../main/protocol.ts';
import type { Reveal } from './messages.ts';

export interface FindRow {
  key: string;                 // unique in the list
  group: FindGroup;
  place: FindPlace;            // the group's first place, or this row's
  child: boolean;              // a place under an opened group
}

export const groupKey = (g: FindGroup): string => `${g.kind}\u0000${g.text}\u0000${g.path}`;

// The rows: a group each, and under an opened one a row per place.
export function findRows(groups: readonly FindGroup[], opened: ReadonlySet<string>): FindRow[] {
  const out: FindRow[] = [];
  for (const g of groups) {
    const k = groupKey(g);
    out.push({ key: k, group: g, place: g.places[0], child: false });
    if (g.places.length > 1 && opened.has(k)) g.places.forEach((p, i) => out.push({ key: `${k}\u0000${i}`, group: g, place: p, child: true }));
  }
  return out;
}

// Kinds by name (English, Title Case: GLOSSARY).
export const KIND_NAMES: Readonly<Record<FindKind, string>> = {
  label: 'Label', pin: 'Pin', tunnel: 'Tunnel', subcircuit: 'Subcircuit', part: 'Part',
};

export const placesCount = (n: number): string => `${n.toLocaleString('en-US')} ${n === 1 ? 'place' : 'places'}`;

// A place's words: the port it is next to, or its numbered name.
export const placeText = (p: FindPlace): string => (p.near ? `next to ${p.place}` : p.place);

// Pressing a row: a group of several opens or closes; a place (or a group of one: double click, Enter) goes there.
export function pressed(row: FindRow, clicks: number): 'toggle' | 'go' | 'none' {
  if (clicks >= 2) return 'go';
  if (row.child) return 'go';
  return row.group.places.length > 1 ? 'toggle' : 'none';
}

// "Show this place" for a found place: into its instance, the part in the selection's look.
export function revealOfPlace(fileId: string, p: FindPlace): Reveal {
  return {
    fileId, messageId: null, circuitId: p.circuitId, root: p.root, path: [...p.path],
    components: [p.componentId], wires: [], nets: [], at: [p.at[0], p.at[1]], cycle: null, tone: 'find',
  };
}

// The row that stays chosen when the list comes again: the same key if it is still there, else the first.
export function keepRow(rows: readonly FindRow[], key: string | null): number {
  if (!rows.length) return -1;
  const i = key === null ? -1 : rows.findIndex((r) => r.key === key);
  return i >= 0 ? i : 0;
}
