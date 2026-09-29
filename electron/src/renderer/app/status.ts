/* The status bar (Hallym MIPS's: facts only, D-135 14, D-158).  From the
   left: the engine's state or Ready, the circuit on show and its counts,
   the Messages count, Simulation On/Off, the cycle, PC, the clock, the
   program, the overlays' facts, the last action's word; on the right: the
   registers the last cycle changed (the "Changed" chip, Hallym MIPS's
   방금 바뀜), the wire colours' mode, Wire Colors and the zoom.

   A bar too narrow for all of it keeps the rule the toolbar keeps (D-158,
   ../shared/overflow.ts): whole facts, the least needed first and from
   the right, go into a » list at the bar's right end, in the bar's order;
   nothing is ever left out without it (v1's known limit Z-20: a status
   bar at 683 px lost its facts).  The facts moved are the same elements
   (a button there still works), so what the bar says is all in it. */

import { h } from '../shared/dom.ts';
import { leftBar, overflowCount } from '../shared/overflow.ts';

export interface Fact {
  node: HTMLElement;
  keep: number;             // higher: stays on the bar longer
  right?: boolean;          // at the right end (after the gap)
}

// How long each kind of fact stays (the most needed last): errors and the engine, then the simulation's facts.
export const KEEP = {
  error: 9, engine: 9, messages: 8, simOff: 8, running: 7, cycle: 7, pc: 6, circuit: 5, overlay: 5, zoom: 4, program: 4,
  note: 4, changed: 3, legend: 3, colors: 2, simOn: 1, hint: 1, speed: 0,
} as const;

export interface StatusBar {
  root: HTMLElement;
  set(facts: Fact[]): void;
  fit(): void;
  moved(): HTMLElement[];   // the facts in the » list now
}

export function statusBar(): StatusBar {
  const root = h('footer', { class: 'status' });
  const list = h('div', { class: 'statusmore', role: 'dialog', 'aria-label': 'More facts', hidden: true });
  const more = h('button', { type: 'button', class: 'moreb', title: 'More facts', 'aria-haspopup': 'dialog', 'aria-expanded': 'false', hidden: true });
  let facts: Fact[] = [];
  let open = false;

  const place = () => {
    if (!open || more.hidden) { list.hidden = true; more.setAttribute('aria-expanded', 'false'); return; }
    list.hidden = false;
    more.setAttribute('aria-expanded', 'true');
    const r = more.getBoundingClientRect();
    const w = list.getBoundingClientRect().width;
    list.style.left = `${Math.max(8, Math.min(window.innerWidth - w - 8, r.right - w))}px`;
    list.style.bottom = `${window.innerHeight - r.top + 6}px`;
  };
  more.addEventListener('click', () => { open = !open; place(); });
  document.addEventListener('pointerdown', (e) => {
    if (open && !list.contains(e.target as Node) && !more.contains(e.target as Node)) { open = false; place(); }
  }, true);
  document.addEventListener('keydown', (e) => { if (open && e.key === 'Escape') { open = false; place(); } });

  const lay = (away: Set<number>) => {
    const leftSide = facts.filter((f, i) => !f.right && !away.has(i)).map((f) => f.node);
    const rightSide = facts.filter((f, i) => f.right && !away.has(i)).map((f) => f.node);
    list.replaceChildren(...facts.filter((_, i) => away.has(i)).map((f) => h('div', { class: 'statusrow' }, f.node)));
    more.hidden = away.size === 0;
    more.textContent = `» ${away.size}`;
    root.replaceChildren(...leftSide, h('span', { class: 'grow' }), ...rightSide, more, list);
  };
  const fits = () => root.scrollWidth <= root.clientWidth + 1;
  const fit = () => {
    const n = overflowCount(facts.length, (k) => { lay(new Set(leftBar(facts, k))); return fits(); });
    lay(new Set(leftBar(facts, n)));
    if (n === 0) open = false;
    place();
  };
  return {
    root,
    set: (f) => { facts = f; fit(); },
    fit,
    moved: () => [...list.querySelectorAll<HTMLElement>('.statusrow > *')],
  };
}
