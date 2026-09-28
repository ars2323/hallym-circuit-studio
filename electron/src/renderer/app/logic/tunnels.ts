/* The Tunnels panel's model (v1 TunnelList, S-11, V-08, I-175; D-150): the
   tunnels of the circuit on show by name, how many of each, their colour
   (the one the student picked with Tunnel Color, else the automatic one
   from the name: the Canvas's own rule, canvas/labels.ts), a name with one
   tunnel only marked (a fact on hover, not a message: it may be on its way
   to a pair), and "go to the next one" on every press.

   Names are grouped by their exact text, as Logisim joins tunnels, the
   right-click menu and Find do (I-175 정함; v1's list alone grouped them
   case-insensitively).  Sorted case-insensitively, then by the exact text. */

import type { Component, Snapshot } from '../../../main/protocol.ts';
import { tunnelColors } from '../../canvas/labels.ts';
import { TUNNEL_PALETTE } from '../../canvas/tokens.ts';

export interface TunnelEntry {
  name: string;
  ids: string[];               // its tunnels, top to bottom, left to right
  color: string;               // as drawn (#rrggbb)
  chosen: boolean;             // the student picked it (saved in the .circ)
  lone: boolean;               // the only tunnel of that name in the circuit
}

const isTunnel = (c: Component) => c.lib === 'Wiring' && c.name === 'Tunnel';
const label = (c: Component) => c.attrs.label ?? '';

export function tunnelEntries(s: Snapshot): TunnelEntry[] {
  const tunnels = s.components.filter((c) => isTunnel(c) && label(c) !== '');
  const colors = tunnelColors(tunnels.map((c) => ({ name: label(c), at: c.loc, color: c.ext?.color })));
  const by = new Map<string, Component[]>();
  for (const c of tunnels) by.set(label(c), [...(by.get(label(c)) ?? []), c]);
  const names = [...by.keys()].sort((a, b) => {
    const x = a.toLowerCase(), y = b.toLowerCase();
    return x < y ? -1 : x > y ? 1 : a < b ? -1 : a > b ? 1 : 0;
  });
  return names.map((name) => {
    const list = by.get(name)!.sort((a, b) => a.loc[1] - b.loc[1] || a.loc[0] - b.loc[0] || (a.id < b.id ? -1 : 1));
    return {
      name, ids: list.map((c) => c.id), color: colors.get(name) ?? TUNNEL_PALETTE[0],
      chosen: list.some((c) => !!c.ext?.color), lone: list.length === 1,
    };
  });
}

/* Pressing a name goes to its next tunnel: the same name again, the next
   one (after the last, the first); another name, its first (v1 goTo). */
export class TunnelCycle {
  private name: string | null = null;
  private at = 0;
  next(e: TunnelEntry): string | null {
    if (!e.ids.length) return null;
    this.at = e.name === this.name ? (this.at + 1) % e.ids.length : 0;
    this.name = e.name;
    return e.ids[this.at];
  }
  reset(): void { this.name = null; this.at = 0; }
}

// The palette's colours by name, in its order (v1 tunnel.color.N; Title Case names).
export const COLOR_NAMES: readonly string[] = [
  'Orange', 'Sky Blue', 'Bluish Green', 'Blue', 'Vermillion', 'Pink',
  'Indigo', 'Green', 'Olive', 'Wine', 'Teal', 'Purple',
];

export const palette = (): { color: string; name: string }[] => TUNNEL_PALETTE.map((color, i) => ({ color, name: COLOR_NAMES[i] }));

// "RegWrite: 3 tunnels"; one: "RegWrite: 같은 이름의 터널이 하나뿐입니다." (a fact on hover, V-08).
export function tunnelTip(e: TunnelEntry): string {
  return e.lone ? `${e.name}: 같은 이름의 터널이 하나뿐입니다.` : `${e.name}: ${e.ids.length} tunnels`;
}
