/* What the Canvas hands the tools (N-07, N-08) and what they hand back to
   draw.  The Canvas (canvas.ts) keeps zooming and panning to itself (the
   wheel, the middle button, Space; D-137) and gives every other pointer
   event and key to the tool in hand (the editor, app/editor.ts), in circuit
   units.  A tool never draws: it says what to show over the circuit (an
   Overlay) and the Canvas draws it with the circuit's own look. */

import type { Component, Point, Wire } from '../../main/protocol.ts';
import type { Box } from './shapes.ts';

export interface CanvasPointer {
  at: [number, number];          // circuit units, not snapped
  screen: [number, number];      // CSS px inside the Canvas
  button: number;                // 0 left, 2 right (the middle one pans, the Canvas keeps it)
  shift: boolean;
  ctrl: boolean;                 // Ctrl, or Cmd on macOS
  alt: boolean;
  detail: number;                // the click count (the system's double-click time, I-213)
  pressed: boolean;              // a button is down (in move)
}

export interface CanvasTool {
  down?(e: CanvasPointer): void;
  move?(e: CanvasPointer): void;
  up?(e: CanvasPointer): void;
  dbl?(e: CanvasPointer): void;
  // A key on the focused Canvas; true: the tool used it (the Canvas does nothing more with it).
  key?(e: KeyboardEvent): boolean;
  keyup?(e: KeyboardEvent): void;
  leave?(): void;
  // The tool wants Space as a key (a poked RAM's next address): no Space-drag panning meanwhile.
  wantsSpace?(): boolean;
  cursor?(): string;
}

// What a tool shows over the circuit.  Every field is optional; the Canvas draws what is there.
export interface Overlay {
  // parts drawn translated by (dx, dy): the part a placing tool holds, a floating paste, what is dragged
  ghost?: { parts: Component[]; wires: Wire[]; dx: number; dy: number; look: 'place' | 'move' | 'float' } | null;
  rubber?: Box | null;                         // the rectangle being dragged to select
  wire?: Point[] | null;                       // the wire being drawn (2 or 3 points)
  hidden?: ReadonlySet<string>;                // wires not drawn meanwhile (the wire being shortened)
  dot?: Point | null;                          // a wiring point under the pointer (the green circle, I-44)
  cursorDot?: Point | null;                    // the Wiring tool's grey dot at the snapped pointer (I-45)
  moveWires?: { added: [Point, Point][]; unconnected: Point[]; computing?: boolean } | null;   // I-24
  caret?: string | null;                       // a poked part that takes keys: the red box (I-68, I-70)
  valueBox?: { at: Point; text: string; net: string | null } | null;   // a poked wire (I-74)
  magnifiers?: boolean;                        // the Poke tool: the lens on every subcircuit (I-75)
}
