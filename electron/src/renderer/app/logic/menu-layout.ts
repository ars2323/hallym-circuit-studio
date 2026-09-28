/* How a right-click menu is put together (N-10, D-157; v1 MenuLayout,
   S-25): the summary line on top in bold, then what is particular to the
   thing clicked -- each provider's items a group of their own, separated --
   then what every part has (Cut, Copy, Duplicate, Duplicate N…, Rotate,
   Show in Attribute Panel), and Delete at the bottom.  Logic only: the
   registry (../menus/registry.ts) calls the providers and this arranges
   what they gave.  English words (D-135), singular and plural right. */

import type { MenuEntry } from '../../canvas/overlays/menu.ts';

// Where an item goes: with its provider's group (the default), the common group, or the bottom.
export type MenuGroup = 'specific' | 'common' | 'delete';
export interface MenuItem extends MenuEntry { group?: MenuGroup }

export const SEP: MenuEntry = { label: '-' };

// v1 MenuLayout.arrange: header; each provider's own items (a separator between groups); common; delete.
export function arrange(summary: string | null, parts: MenuItem[][]): MenuEntry[] {
  const specific: MenuEntry[][] = [];
  const common: MenuEntry[] = [];
  const del: MenuEntry[] = [];
  for (const part of parts) {
    const mine: MenuEntry[] = [];
    for (const it of part) {
      if (it.label === '-') continue;
      const { group, ...e } = it;
      if (group === 'common') common.push(e);
      else if (group === 'delete') del.push(e);
      else mine.push(e);
    }
    if (mine.length) specific.push(mine);
  }
  const out: MenuEntry[] = [];
  let items = 0;                                     // a separator goes between groups, never under the header
  const group = (g: MenuEntry[]) => { if (items) out.push(SEP); out.push(...g); items += g.length; };
  if (summary) out.push({ label: summary, header: true });
  for (const s of specific) group(s);
  if (common.length) group(common);
  if (del.length) group(del);
  return out;
}

// "1 bit", "32 bits"; "1 Component", "3 Components" (MessageFormat choice in v1's names).
export const count = (n: number, one: string, many = `${one}s`): string => `${n} ${n === 1 ? one : many}`;

// v1 names.properties: menu.bulk = Change {n} Components, menu.bulkLabels = Edit Labels of {n} Components…
export const changeN = (n: number): string => `Change ${count(n, 'Component')}`;
export const editLabelsN = (n: number): string => `Edit Labels of ${count(n, 'Component')}…`;
export const combineN = (n: number): string => `Combine ${count(n, 'Wire')} into One Bus (in the order chosen)`;

// The gate names without " Gate" (v1 menu.kind.item: "AND", "NOR" …).
export const gateShort = (name: string): string => name.replace(/ Gate$/, '');

// Data Bits ▸ (v1 EditMenus.WIDTHS) and Number of Inputs ▸ 2..8.
export const WIDTHS = [1, 2, 4, 8, 16, 32];
export const INPUT_COUNTS = [2, 3, 4, 5, 6, 7, 8];
export const FACINGS: [string, string][] = [['east', 'East'], ['west', 'West'], ['north', 'North'], ['south', 'South']];

// Attach Probe ▸ (v1 QuickProbe.RADICES and probe.radix.* names).
export const PROBE_RADICES: [string, string][] = [['16', 'Hexadecimal'], ['10signed', 'Signed Decimal'], ['10unsigned', 'Unsigned Decimal'], ['2', 'Binary']];

// Align ▸ and Distribute ▸ (v1 ArrangeActions: the engine's edit.align modes, edit.distribute axes).
export const ALIGNS: [string, string][] = [['left', 'Left'], ['centerX', 'Center'], ['right', 'Right'], ['top', 'Top'], ['centerY', 'Middle'], ['bottom', 'Bottom']];
export const DISTRIBUTES: [string, string][] = [['h', 'Horizontally'], ['v', 'Vertically']];

// A value (the protocol's bits, high first) has an E or x bit: Find E/X Origin has something to follow (v1 D-01).
export const undefinedValue = (v: string | undefined): boolean => !!v && /[xE]/.test(v);
