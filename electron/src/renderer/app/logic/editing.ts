/* The editing tools' key and part rules (N-08, D-146; logic only, tested
   in tests/unit/editing.test.ts).  docs/interaction-parity.md gives each
   row; the engine does the edit (edit.*), this says which intent a key is.

   - Edit tool with a selection (I-28..I-32, I-38..I-40, I-42): Delete and
     Backspace delete; the arrows move one grid step (v1's nudge, D-035);
     R turns clockwise and Shift+R back (v1); a digit or Alt+digit and
     Alt+arrow go to the parts' key configurators (Logisim's
     KeyConfigurator: inputs, bits, label side); F2 edits the label of a
     single part that has one; Insert duplicates.  Without a selection only
     Backspace does something: it takes back the wire just drawn (I-31).
   - A tool placing a part (I-58, I-59): an arrow turns it, a digit or
     Alt+digit or Alt+arrow goes to its key configurator.
   Keys are read by KeyboardEvent.code (the physical key): a Korean layout
   or the IME gives the same code (I-39, I-210, I-212). */

import type { Component } from '../../../main/protocol.ts';

export interface KeyLike { key: string; code: string; ctrlKey: boolean; altKey: boolean; shiftKey: boolean; metaKey: boolean; isComposing?: boolean }

export type EditKey =
  | { kind: 'delete' }
  | { kind: 'undoWire' }
  | { kind: 'nudge'; dx: number; dy: number }
  | { kind: 'rotate'; clockwise: boolean }
  | { kind: 'keyConfig'; key: string; alt: boolean }
  | { kind: 'label' }
  | { kind: 'duplicate' }
  | { kind: 'fit' };

// Logisim's NumericConfigurator reads digits typed within this many ms as one number (MAX_TIME_KEY_LASTS).
export const CHAIN_MS = 800;

// The digit a key is (the row or the number pad), or null.
export function digitOf(code: string): string | null {
  const m = /^(?:Digit|Numpad)([0-9])$/.exec(code);
  return m ? m[1] : null;
}

const ARROWS: Record<string, [number, number]> = { ArrowLeft: [-10, 0], ArrowRight: [10, 0], ArrowUp: [0, -10], ArrowDown: [0, 10] };

// The configurator's key (KeyConfigurator): a digit, with or without Alt; an arrow with Alt (a pin's label side).
function configKey(e: KeyLike): { key: string; alt: boolean } | null {
  if (e.ctrlKey || e.metaKey) return null;
  const d = digitOf(e.code);
  if (d !== null && !e.shiftKey) return { key: d, alt: e.altKey };
  if (e.altKey && !e.shiftKey && e.key in ARROWS) return { key: e.key, alt: true };
  return null;
}

/* The Edit tool's key.  `selected`: something is selected (in the circuit
   or floating); `labelled`: exactly one part is selected and it has a label. */
export function editKey(e: KeyLike, selected: boolean, labelled: boolean): EditKey | null {
  if (e.isComposing) return null;
  if (e.ctrlKey || e.metaKey) return null;          // the menu's keys (Ctrl+C …) are the window's
  if (e.key === 'Backspace' || e.key === 'Delete') {
    if (selected) return { kind: 'delete' };
    return e.key === 'Backspace' && !e.altKey && !e.shiftKey ? { kind: 'undoWire' } : null;
  }
  if (!selected) return null;
  const c = configKey(e);
  if (c) return { kind: 'keyConfig', ...c };
  if (!e.altKey && !e.shiftKey && e.key in ARROWS) {
    const [dx, dy] = ARROWS[e.key];
    return { kind: 'nudge', dx, dy };
  }
  if (e.code === 'KeyR' && !e.altKey) return { kind: 'rotate', clockwise: !e.shiftKey };
  if (e.key === 'F2' && !e.altKey && !e.shiftKey) return labelled ? { kind: 'label' } : null;
  if (e.key === 'Insert' && !e.altKey && !e.shiftKey) return { kind: 'duplicate' };
  if (e.code === 'KeyF' && !e.altKey && !e.shiftKey) return { kind: 'fit' };   // v1: the view fitted to the selection (I-124)
  return null;
}

// A placing tool's key: the configurator's, or an arrow (its facing; AddTool.keyPressed).
export function placeKey(e: KeyLike): { key: string; alt: boolean } | null {
  if (e.isComposing) return null;
  const c = configKey(e);
  if (c) return c;
  if (!e.ctrlKey && !e.metaKey && !e.altKey && !e.shiftKey && e.key in ARROWS) return { key: e.key, alt: false };
  return null;
}

// Whether digits typed now go on from the one before (the configurator keeps its number).
export const chained = (last: number, now: number): boolean => now - last < CHAIN_MS;

// ---- the parts ----

export const isInputPin = (c: Component): boolean => c.lib === 'Wiring' && c.name === 'Pin' && c.attrs.output !== 'true';
export const isButton = (c: Component): boolean => c.lib === 'Input/Output' && c.name === 'Button';

// Ctrl+click pokes an input pin or a button with the Edit tool (v1 Shortcuts.mouse, I-77).
export const ctrlPokes = (c: Component): boolean => isInputPin(c) || isButton(c);

/* A part whose label is edited in place (v1 InlineEditor, I-42, I-105):
   one with a label attribute; a Label (Base › Text) is the Text tool's.
   A double click on an input pin asks for its value instead (I-78). */
export const labelled = (c: Component): boolean => 'label' in c.attrs && !(c.lib === 'Base' && c.name === 'Text');

// The field's width on screen for a part's label (v1: max(96, the part's width × zoom + 16)).
export const labelFieldWidth = (c: Component, zoom: number): number => Math.max(96, c.bounds[2] * zoom + 16);

// The tool a toolbar button holds (the Wiring parts the toolbar offers).
export const TOOLBAR_PARTS: Record<string, { lib: string; name: string }> = {
  Pin: { lib: 'Wiring', name: 'Pin' },
  Tunnel: { lib: 'Wiring', name: 'Tunnel' },
  Probe: { lib: 'Wiring', name: 'Probe' },
};
