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

export interface Band {
  root: HTMLElement;
  show(text: string, kind?: 'warn' | 'error', title?: string): void;   // title: the whole story, on hover
  hide(): void;
  text(): string | null;
}

// cls: the band's class ('band', shared.css; another band styles its own).
export function band(cls = 'band'): Band {
  const root = h('div', { class: cls, 'data-band': '', role: 'status', hidden: true });
  return {
    root,
    show: (text, kind = 'warn', title = text) => {
      root.hidden = false;
      document.body.classList.add('band-shown');
      root.dataset.kind = kind;
      if (root.textContent !== text) root.textContent = text;
      if (root.title !== title) root.title = title;
    },
    hide: () => {
      root.hidden = true;
      root.textContent = '';
      root.title = '';
      if (!document.querySelector('[data-band]:not([hidden])')) document.body.classList.remove('band-shown');
    },
    text: () => (root.hidden ? null : root.textContent),
  };
}
