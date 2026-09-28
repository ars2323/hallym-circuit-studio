/* The Console tab's text (v1 C-09, N-16): every Console part's whole
   output -- the part's body shows its last lines only.  The engine sends
   each Console's text once (`text`) and then what it printed since
   (`append`); Reset sends an empty text (v1: Reset clears the Console).
   Logic only. */

import type { ConsoleEntry } from '../../../main/protocol.ts';

export interface ConsoleState {
  name: string;
  text: string;
  exited: boolean;
}

// The engine's update over what was shown: a Console missing from the update is gone.
export function applyConsole(prev: ConsoleState[], update: ConsoleEntry[]): ConsoleState[] {
  const before = new Map(prev.map((c) => [c.name, c]));
  return update.map((u) => ({
    name: u.name,
    text: u.text !== undefined ? u.text : (before.get(u.name)?.text ?? '') + (u.append ?? ''),
    exited: u.exited,
  }));
}

// v1's Console tab: a name line between Consoles when there are more than
// one, and "-- exit --" on its own line after a program's exit.
export function consoleText(list: ConsoleState[]): string {
  let out = '';
  for (const c of list) {
    if (list.length > 1) {
      if (out.length > 0 && !out.endsWith('\n')) out += '\n';   // the name line on a line of its own
      out += `── ${c.name} ──\n`;
    }
    out += c.text;
    if (c.exited) {
      if (out.length > 0 && !out.endsWith('\n')) out += '\n';
      out += '-- exit --\n';
    }
  }
  return out;
}

// Nothing printed yet (and no exit): the tab says what fills it.
export const consoleEmpty = (list: ConsoleState[]): boolean => list.every((c) => c.text === '' && !c.exited);
