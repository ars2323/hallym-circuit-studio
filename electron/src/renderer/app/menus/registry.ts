/* The menu registry (N-10, D-157): every right-click menu of the window is
   built here from one table of providers -- the Canvas (a part, a port, a
   wire, an empty spot, several parts), the Components list's entries and
   the circuit tabs -- the neighbour of the part-kind registries
   (canvas/registry.ts, the engine's Kinds) where a Verilog mapping will
   hang later.  A provider gives the items for what was clicked; the
   registry arranges them the v1 way (logic/menu-layout.ts: the summary
   line, each provider's items as a group, the common group, Delete last).

   Other items register their own providers here instead of opening menus
   of their own: N-11's circuit and library entries (Edit Appearance, Auto
   Appearance, Port Order…, Remove Circuit, Load Library ▸ …), N-17's
   Project menu items when they come.  `order` places a provider's group:
   the original component's own items first (10), then v1's (20…), the
   overlays' (50…). */

import type { MenuFacts, Point } from '../../../main/protocol.ts';
import type { MenuEntry } from '../../canvas/overlays/menu.ts';
import { arrange, type MenuItem } from '../logic/menu-layout.ts';

export type { MenuItem } from '../logic/menu-layout.ts';

// What was right-clicked on the Canvas: the engine's facts (model.menu) and where.
export interface CanvasTarget {
  fileId: string;
  circuitId: string;          // the circuit on show (inside an instance: that subcircuit)
  root: string;               // the circuit the view starts from
  path: string[];             // the instances gone into (sim.watch's path)
  at: Point;                  // the circuit point clicked
  facts: MenuFacts;
}

// An entry of the Components list: this file's circuit, a library (its group), or a library's tool.
export interface ComponentsTarget {
  fileId: string;
  kind: 'circuit' | 'library' | 'tool';
  lib: string | null;         // null: this file's circuits
  name: string;               // the circuit's, the library's or the tool's name
  display: string;
  circuitId?: string;         // a circuit of this file
  main?: boolean;             // it is the main circuit
  pending?: boolean;          // Hallym MIPS not in the file yet (no menu, v1 V-01)
}

// A circuit tab over the Canvas.
export interface CircuitTabTarget {
  fileId: string;
  circuitId: string;
  name: string;
  main: boolean;
  editable: boolean;
}

export interface Targets {
  canvas: CanvasTarget;
  components: ComponentsTarget;
  circuitTab: CircuitTabTarget;
}
export type MenuContext = keyof Targets;

export interface MenuProvider<C extends MenuContext> {
  id: string;                 // who gives these items (tests, a second registration replaces the first)
  order: number;              // where its group goes (lower first)
  items(target: Targets[C]): MenuItem[];
}

const providers: { [C in MenuContext]: MenuProvider<C>[] } = { canvas: [], components: [], circuitTab: [] };

// Adds (or replaces, by id) a provider; the result takes it away again.
export function registerMenu<C extends MenuContext>(context: C, provider: MenuProvider<C>): () => void {
  const list = providers[context] as MenuProvider<C>[];
  const i = list.findIndex((p) => p.id === provider.id);
  if (i >= 0) list.splice(i, 1);
  list.push(provider);
  list.sort((a, b) => a.order - b.order);
  return () => {
    const k = list.indexOf(provider);
    if (k >= 0) list.splice(k, 1);
  };
}

export function registered(context: MenuContext): string[] {
  return providers[context].map((p) => p.id);
}

// The menu for a target: each provider's items (a provider that fails gives none: one broken provider does not
// take the menu away), arranged with the summary line on top.
export function menuFor<C extends MenuContext>(context: C, target: Targets[C], summary: string | null = null): MenuEntry[] {
  const parts: MenuItem[][] = [];
  for (const p of providers[context] as MenuProvider<C>[]) {
    try {
      parts.push(p.items(target));
    } catch (e) {
      console.error(`menu provider ${p.id}`, e);
    }
  }
  return arrange(summary, parts);
}
