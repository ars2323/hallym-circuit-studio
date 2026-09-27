/* "Show this place" (D-143): one typed event on the window, sent when the
   student chooses a message (messages.ts) -- later also an E/X origin.  The
   panels that show places listen: the Canvas (N-05, N-15) goes to the
   circuit and marks the parts, wires and nets; the Cycle View (N-14) goes
   to the cycle.  A plain CustomEvent, so a panel that is not there yet
   costs nothing and the tests can listen from the page. */

import type { Reveal } from './logic/messages.ts';

export type { Reveal } from './logic/messages.ts';
export const REVEAL_EVENT = 'hcs:reveal';

export function emitReveal(r: Reveal): void {
  window.dispatchEvent(new CustomEvent<Reveal>(REVEAL_EVENT, { detail: r }));
}

// Listens; the result stops listening.
export function onReveal(listener: (r: Reveal) => void): () => void {
  const f = (e: Event) => listener((e as CustomEvent<Reveal>).detail);
  window.addEventListener(REVEAL_EVENT, f);
  return () => window.removeEventListener(REVEAL_EVENT, f);
}
