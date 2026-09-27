/* The notice band: one thin line over the work area that says a fact the
   student should not miss and covers nothing (Hallym MIPS's band over its
   Run side, "지금 보이는 것은 마지막으로 어셈블한 코드입니다", made a
   component).  Here: the engine stopped and was started again, the engine
   could not start.  Facts only; no character. */

import { h } from './dom.ts';

export interface Band {
  root: HTMLElement;
  show(text: string, kind?: 'warn' | 'error'): void;
  hide(): void;
  text(): string | null;
}

export function band(): Band {
  const root = h('div', { class: 'band', role: 'status', hidden: true });
  return {
    root,
    show: (text, kind = 'warn') => {
      root.hidden = false;
      root.dataset.kind = kind;
      if (root.textContent !== text) { root.textContent = text; root.title = text; }
    },
    hide: () => { root.hidden = true; root.textContent = ''; },
    text: () => (root.hidden ? null : root.textContent),
  };
}
