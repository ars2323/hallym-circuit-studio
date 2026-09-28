/* The small typed events the panels and the Canvas meet on (N-12 with N-08
   and N-10, D-150), like "show this place" (reveal.ts, D-143): plain
   CustomEvents on the window, so a side that is not there yet costs nothing
   and the tests can send and hear them from the page.

     hcs:place-tool    a part to place, from the Components list, its search
                       or the search palette.  Without `at`: pick the tool
                       up (the Canvas's placement gesture places it, N-08).
                       With `at` (a drop on the Canvas, the palette's Enter
                       at the pointer): place one there now.  The event can
                       be cancelled: whoever places it (N-08's placement
                       flow) calls preventDefault(); if nobody did,
                       `placeNow` below places the one with `at` itself,
                       through the engine (edit.addComponent).
     hcs:selection     what is selected on the Canvas changed (N-08's
                       selection model; until then the Canvas's click).
     hcs:edit-splitter open the Splitter editor (N-12) for a splitter, or
                       for a new splitter on a multi-bit wire (Split Bits…,
                       Take One Bit): what the right-click menus (N-10) send. */

import type { Point } from '../../main/protocol.ts';

export const PLACE_TOOL = 'hcs:place-tool';
export const SELECTION = 'hcs:selection';
export const EDIT_SPLITTER = 'hcs:edit-splitter';

export interface PlaceTool {
  fileId: string;
  circuitId: string;                  // the circuit on show (where it goes)
  lib: string | null;                 // model.library's lib; null: a circuit of this file
  name: string;                       // the tool's name, or the circuit's
  attrs?: Record<string, string>;     // from the number after a name (logic/search.ts)
  at?: Point;                         // place one here now (logical point, on the grid)
  source: 'components' | 'palette' | 'drop';
}

// Sends it; true when a listener took it (preventDefault: it places the part).
export function emitPlaceTool(p: PlaceTool): boolean {
  return !window.dispatchEvent(new CustomEvent<PlaceTool>(PLACE_TOOL, { detail: p, cancelable: true }));
}

export function onPlaceTool(listener: (p: PlaceTool, e: CustomEvent<PlaceTool>) => void): () => void {
  const f = (e: Event) => listener((e as CustomEvent<PlaceTool>).detail, e as CustomEvent<PlaceTool>);
  window.addEventListener(PLACE_TOOL, f);
  return () => window.removeEventListener(PLACE_TOOL, f);
}

export interface Selection {
  fileId: string;
  circuitId: string;
  path: string[];                     // the subcircuit instances the Canvas is inside
  ids: string[];                      // the selected parts and wires
}

export function emitSelection(s: Selection): void {
  window.dispatchEvent(new CustomEvent<Selection>(SELECTION, { detail: s }));
}

export function onSelection(listener: (s: Selection) => void): () => void {
  const f = (e: Event) => listener((e as CustomEvent<Selection>).detail);
  window.addEventListener(SELECTION, f);
  return () => window.removeEventListener(SELECTION, f);
}

export type EditSplitter =
  | { fileId: string; circuitId: string; componentId: string }                   // Edit Splitter…
  | { fileId: string; circuitId: string; wire: string; at: Point; bit?: number }; // Split Bits… (bit: Take One Bit [bit])

export function emitEditSplitter(r: EditSplitter): void {
  window.dispatchEvent(new CustomEvent<EditSplitter>(EDIT_SPLITTER, { detail: r }));
}

export function onEditSplitter(listener: (r: EditSplitter) => void): () => void {
  const f = (e: Event) => listener((e as CustomEvent<EditSplitter>).detail);
  window.addEventListener(EDIT_SPLITTER, f);
  return () => window.removeEventListener(EDIT_SPLITTER, f);
}

// The grid point nearest a logical point (Logisim's grid of 10, v1 I-01).
export const snap = (p: Point): Point => [Math.round(p[0] / 10) * 10, Math.round(p[1] / 10) * 10];
