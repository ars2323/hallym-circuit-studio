/* The notice band: one thin line over the work area that says a fact the
   student should not miss and covers nothing (Hallym MIPS's band over its
   Run side, "지금 보이는 것은 마지막으로 어셈블한 코드입니다", made a
   component).  Here: the engine stopped and was started again, the engine
   could not start; a program that could not be loaded again, with the one
   still on show (N-16).  Facts only.  It only ever says that something went
   wrong, so while one is up no character is on screen (body.band-shown,
   shared.css): the university's characters never stand next to an error.
   There can be more than one band: the class goes when the last one does. */

import { h } from './dom.ts';

// A band's one command (the engine's Try Again, the simulation's Turn On: v1 I-160, D-158).
export interface BandAction { label: string; run(): void }

export interface Band {
  root: HTMLElement;
  show(text: string, kind?: 'warn' | 'error', title?: string, action?: BandAction): void;   // title: the whole story, on hover
  hide(): void;
  text(): string | null;
}

// cls: the band's class ('band', shared.css; another band styles its own).
export function band(cls = 'band'): Band {
  const root = h('div', { class: cls, 'data-band': '', role: 'status', hidden: true });
  const words = h('span', { class: 'bandtext' });
  const button = h('button', { class: 'linkbtn bandaction', type: 'button', hidden: true });
  let run: (() => void) | null = null;
  button.addEventListener('click', () => run?.());
  root.append(words, button);
  return {
    root,
    show: (text, kind = 'warn', title = text, action) => {
      root.hidden = false;
      document.body.classList.add('band-shown');
      root.dataset.kind = kind;
      if (words.textContent !== text) words.textContent = text;
      if (root.title !== title) root.title = title;
      run = action?.run ?? null;
      button.hidden = !action;
      if (button.textContent !== (action?.label ?? '')) button.textContent = action?.label ?? '';
    },
    hide: () => {
      root.hidden = true;
      words.textContent = '';
      button.hidden = true;
      button.textContent = '';
      run = null;
      root.title = '';
      if (!document.querySelector('[data-band]:not([hidden])')) document.body.classList.remove('band-shown');
    },
    text: () => (root.hidden ? null : words.textContent),
  };
}
