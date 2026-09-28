/* A panel's word to the student when it has nothing else to show (derived
   from Hallym MIPS v2.5.0 electron/src/renderer/app/notice.ts): the words on
   the left -- a title, a sentence (what fills the panel), and whatever else
   the case needs (a button) -- and, where the panel is the screen's main
   place, a character on the right, the two together in the middle of the
   panel, no wider than a paragraph reads well.

   Most panels show the words alone: one character on screen is enough, and
   never one next to an error (the university's guideline, CLAUDE.md 8).
   A panel too short or too narrow for the character keeps the words alone
   (shared.css, the container query on .notice-host). */

import { character, h, prose } from './dom.ts';

export const NOTICE_CHARACTER = 120;

export interface Notice {
  title: string;
  body?: Node | string;
  more?: (Node | null)[];             // after the sentence: a button
  pose?: string;                      // the character's file (assets/hallym/character/), if any
}

export function notice(n: Notice): HTMLElement {
  return h('div', { class: 'notice' },
    h('div', { class: 'say' }, h('h3', {}, prose(n.title)), n.body ? h('p', {}, prose(n.body)) : null, ...(n.more ?? [])),
    n.pose ? character(n.pose, NOTICE_CHARACTER) : null);
}

// A panel body that shows a notice while it is empty: `host` goes where the
// panel's content goes; empty(n) shows the words, fill(...) the content.
export interface NoticeHost {
  root: HTMLElement;
  empty(n: Notice): void;
  fill(...content: Node[]): void;
  isEmpty(): boolean;
}

export function noticeHost(cls = ''): NoticeHost {
  const root = h('div', { class: `pbody ${cls}`.trim() });
  let shown = true;
  return {
    root,
    empty: (n) => {
      root.classList.add('notice-host');
      root.dataset.empty = 'true';
      root.replaceChildren(notice(n));
      shown = true;
    },
    fill: (...content) => {
      root.classList.remove('notice-host');
      delete root.dataset.empty;
      root.replaceChildren(...content);
      shown = false;
    },
    isEmpty: () => shown,
  };
}
