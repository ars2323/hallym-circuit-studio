/* The Poke tool's rules on the screen (N-07, D-145; logic only).  The
   engine pokes with Logisim's own pokers (sim.poke: Pin, Clock, Button,
   flip-flops, Register, RAM, Radix Probe …, docs/engine-api.md); here is
   what the screen decides before and after:

   - which part is under the pointer (Logisim's PokeTool asks every part
     containing the point; the topmost wins here, as it is drawn),
   - a subcircuit's magnifier in the middle of its box (SubcircuitPoker:
     within r² ≤ 60 of the centre, a double click goes into that instance),
   - which parts take keys once poked (RegisterPoker, MemPoker,
     ShiftRegisterPoker, Keyboard.Poker: the hex digits, Space, Tab, Enter,
     Backspace, the arrows) -- the others' carets ignore keys,
   - a wire poked: Logisim's yellow value box, "first radix / second
     radix" with the default preferences (binary in groups of four, then
     signed decimal for more than one bit; PokeTool.WireCaret). */

import type { Component } from '../../main/protocol.ts';

// Parts whose poke caret takes keys (their names as the engine sends them).
export const KEYED = new Set(['Register', 'Counter', 'RAM', 'ROM', 'Shift Register', 'Keyboard']);

// The keys a keyed caret gets (KeyboardEvent.key; the engine's sim.pokeKey takes the same names).
const NAMED_KEYS = new Set(['Backspace', 'Enter', 'Tab', 'Delete', 'Escape', 'ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Home', 'End']);
export function pokeKey(e: { key: string; ctrlKey: boolean; altKey: boolean; metaKey: boolean; isComposing?: boolean }): string | null {
  if (e.ctrlKey || e.altKey || e.metaKey || e.isComposing) return null;   // Logisim's caret ignores keys with modifiers
  if (NAMED_KEYS.has(e.key)) return e.key;
  return [...e.key].length === 1 ? e.key : null;
}

// Logisim's integer centre of a part's box.
const centre = (c: Component): [number, number] => [c.bounds[0] + Math.floor(c.bounds[2] / 2), c.bounds[1] + Math.floor(c.bounds[3] / 2)];

// A subcircuit instance's magnifier: the pointer within it (SubcircuitPoker.isWithin).
export function onMagnifier(c: Component, p: [number, number]): boolean {
  if (c.subcircuit === undefined && !c.appearance) return false;
  const [cx, cy] = centre(c);
  const dx = Math.round(p[0]) - cx, dy = Math.round(p[1]) - cy;
  return dx * dx + dy * dy <= 60;
}

// Where to draw the magnifier: its lens (centre, radius 5) and handle (SubcircuitPoker.paint).
export function magnifier(c: Component): { cx: number; cy: number; r: number; handle: [number, number][] } {
  const [cx, cy] = centre(c);
  const tx = cx + 3, ty = cy + 3;
  return { cx, cy, r: 5, handle: [[tx - 1, ty + 1], [cx + 8, cy + 10], [cx + 10, cy + 8], [tx + 1, ty - 1]] };
}

// The part to poke at p: the last (topmost) part whose box holds the point; subcircuits are not
// poked (their magnifier is a double click, handled by the screen).
export function pokeTarget(parts: Iterable<Component>, p: [number, number]): Component | null {
  let hit: Component | null = null;
  for (const c of parts) {
    const [x, y, w, h] = c.bounds;
    if (p[0] >= x && p[0] <= x + w && p[1] >= y && p[1] <= y + h) hit = c;
  }
  return hit;
}

// The circuit point sent with a poke: whole units (Logisim's mouse events are ints).
export const pokePoint = (p: [number, number]): [number, number] => [Math.floor(p[0]), Math.floor(p[1])];

/* A wire's value in Logisim's poked-wire box: binary with a space every four bits (Value.toDisplayString),
   and for more than one bit " / " and the signed decimal (Value.toDecimalString(true): "Error" with an
   E bit, "???" with an x bit).  `value` is the engine's text, high bit first ('0' '1' 'x' 'E'). */
export function wireValueText(value: string | undefined, width: number): string {
  if (width <= 0) return '-';
  const v = value && value.length === width ? value : 'x'.repeat(width);
  if (width === 1) return v;
  let bin = '';
  for (let i = width - 1, k = 0; i >= 0; i--, k++) {
    bin += v[k];
    if (i % 4 === 0 && i !== 0) bin += ' ';
  }
  let dec: string;
  if (v.includes('E')) dec = 'Error';
  else if (/[^01]/.test(v)) dec = '???';
  else {
    const u = BigInt(`0b${v}`);
    const s = v[0] === '1' ? u - (1n << BigInt(width)) : u;
    dec = s.toString();
  }
  return `${bin} / ${dec}`;
}
