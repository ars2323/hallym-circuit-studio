/* The first screen's card (derived from Hallym MIPS v2.5.0
   electron/src/renderer/app/panels/welcome.ts): the greeting character and
   two ways in, then two more for whichever was chosen.  No recent files:
   nothing of a session is kept (lab PCs are shared).

   Every step has the same shape: the card has a fixed width, each choice a
   fixed size with its line break written in, and the "← 처음으로" row is
   there in every step (hidden in the first), so going from one step to
   another moves nothing but the words.  What the steps say and do is the
   caller's (src/renderer/app/start.ts).  Behind the card, the same for
   every step: the university's video (backdrop.ts), which a step never
   restarts; the caller says when the first screen is on show (show()). */

import { backdrop } from './backdrop.ts';
import { character, h, icon } from './dom.ts';

export interface Choice {
  label: string;
  lines: [string, string];      // the two lines under the label, broken where they are written
  icon: string;
  main?: boolean;               // the one to take first: tinted
  go?: string;                  // another step of the card
  onClick?: () => void;         // or something to do
}

export interface WelcomeSpec {
  title: string;
  lead: [string, string];
  pose: string;                 // the character (assets/hallym/character/)
  steps: Record<string, Choice[]>;
  first: string;
}

export interface Welcome {
  root: HTMLElement;
  go(step: string): void;
  step(): string;
  show(on: boolean): void;      // the first screen on show or not: the video plays only while it is
  enable(on: boolean): void;    // the choices that do something can be pressed (off: the engine is down, D-158)
}

function action(c: Choice, onClick: () => void, enabled: boolean): HTMLElement {
  const b = h('button', { class: `action${c.main ? ' main' : ''}`, type: 'button', disabled: !enabled && !c.go }, icon(c.icon),
    h('span', {}, h('b', {}, c.label), h('span', { class: 'sub' }, c.lines[0], h('br'), c.lines[1])));
  b.addEventListener('click', onClick);
  return b;
}

export function welcome(spec: WelcomeSpec): Welcome {
  const actions = h('div', { class: 'actions' });
  const back = h('button', { class: 'linkbtn back', type: 'button' }, '← 처음으로');
  let current = spec.first;
  let enabled = true;
  const go = (step: string) => {
    current = step;
    actions.replaceChildren(...spec.steps[step].map((c) => action(c, () => (c.go ? go(c.go) : c.onClick?.()), enabled)));
    back.style.visibility = step === spec.first ? 'hidden' : 'visible';
    actions.dataset.step = step;
    if (step !== spec.first) (actions.firstElementChild as HTMLElement).focus();
  };
  back.addEventListener('click', () => go(spec.first));
  go(spec.first);
  const start = backdrop();
  const card = h('div', { class: 'wcard' },
    character(spec.pose, 200),
    h('div', { class: 'wbody' }, h('h1', {}, spec.title),
      h('p', { class: 'lead' }, spec.lead[0], h('br'), spec.lead[1]),
      actions, back));
  const root = h('div', { class: 'welcome' }, start.root, card);
  const enable = (on: boolean) => { if (on !== enabled) { enabled = on; go(current); } };
  return { root, go, step: () => current, show: start.show, enable };
}
