/* Find (Ctrl+F; v1 FindDialog, D-036, #135, S-09, S-28, I-171, I-172;
   D-150): a window over the Canvas's top right corner that stays open
   while the student works (not modal).  It asks the engine (find.query) for
   labels, tunnels, pins, subcircuits and parts by name in every circuit of
   the file -- down every subcircuit instance -- and lists them grouped
   (logic/find.ts).

     typing        searches again (the engine's index is always the model
                   now: an edit is found at once, I-171 정함)
     ↑ ↓           the chosen row (from the box too: I-172 정함)
     Enter         go to the chosen row (else the first)
     a group row   opens or closes its places; a place row goes there;
                   double click on any row goes
     Esc, ×        close (I-171 정함)

   Going there is "show this place" (reveal.ts): its circuit, into the
   instance it is in, the part marked in the selection's blue and brought
   into view. */

import type { FindPlace, FindResult } from '../../main/protocol.ts';
import { code, h, icon } from '../shared/dom.ts';
import { findRows, type FindRow, groupKey, KIND_NAMES, keepRow, placesCount, placeText, pressed } from './logic/find.ts';

export interface FindHost {
  query(text: string): Promise<FindResult | null>;   // null: no file open, or the engine could not answer
  go(p: FindPlace): void;
}

export interface FindWindow {
  root: HTMLElement;
  open(): void;
  close(): void;
  isOpen(): boolean;
  refresh(): void;          // the model or the file changed: the same text again
}

export const FIND_EMPTY = '이름을 치면 모든 회로에서 라벨, 터널, 핀, 서브회로, 부품을 찾습니다. 서브회로 안에 있는 것은 그 인스턴스 안으로 가서 보입니다.';

export function findWindow(host: FindHost): FindWindow {
  let result: FindResult | null = null;
  let rows: FindRow[] = [];
  let chosen = -1;
  let chosenKey: string | null = null;
  const opened = new Set<string>();
  let asked = 0;
  let timer = 0;
  const input = h('input', { type: 'search', class: 'findinput', 'aria-label': 'Find', placeholder: 'Name (RegWrite, PC, Register …)', spellcheck: 'false', autocomplete: 'off' });
  const list = h('ul', { class: 'findlist', role: 'listbox', 'aria-label': 'Found' });
  const foot = h('div', { class: 'findfoot' });
  const close = h('button', { type: 'button', class: 'iconbtn findclose', title: 'Close (Esc)', 'aria-label': 'Close' }, icon('x'));
  const root = h('section', { class: 'findwin', role: 'dialog', 'aria-label': 'Find', hidden: true },
    h('div', { class: 'phead' }, h('span', { class: 'ptitle' }, 'Find'), h('span', { class: 'pgrow' }), close),
    h('div', { class: 'findbox' }, input), list, foot);

  function render(): void {
    rows = findRows(result?.groups ?? [], opened);
    chosen = keepRow(rows, chosenKey);
    chosenKey = chosen >= 0 ? rows[chosen].key : null;
    const typed = input.value.trim() !== '';
    list.replaceChildren(...rows.map((r, i) => {
      const g = r.group;
      const many = g.places.length > 1;
      const li = h('li', {
        role: 'option', class: `findrow${r.child ? ' child' : ''}${i === chosen ? ' on' : ''}`, 'aria-selected': String(i === chosen),
        'data-key': r.key, title: r.child ? placeText(r.place) : many ? '누르면 자리를 펼치거나 접습니다. 두 번 누르면 첫 자리로 갑니다.' : placeText(r.place),
      }, ...(r.child
        ? [h('span', { class: 'fplace' }, placeText(r.place))]
        : [
          h('span', { class: 'ftoggle', 'aria-hidden': 'true' }, many ? (opened.has(groupKey(g)) ? '−' : '+') : ''),
          h('span', { class: 'fname' }, code(g.text)),
          h('span', { class: 'fkind' }, KIND_NAMES[g.kind]),
          h('span', { class: 'fpath' }, g.path),
          many ? h('span', { class: 'fcount' }, placesCount(g.places.length)) : null,
        ]));
      li.addEventListener('mousedown', (e) => e.preventDefault());
      li.addEventListener('click', (e) => {
        chosen = i; chosenKey = r.key;
        const what = pressed(r, e.detail);
        if (what === 'toggle') { toggle(r); return; }
        paint();
        if (what === 'go') host.go(r.place);
      });
      return li;
    }));
    if (!typed) foot.textContent = FIND_EMPTY;
    else if (!result) foot.textContent = '';
    else if (!result.groups.length) foot.replaceChildren('찾는 이름과 맞는 것이 없습니다: ', code(input.value.trim()));
    else foot.textContent = `${result.groups.length.toLocaleString('en-US')} ${result.groups.length === 1 ? 'result' : 'results'}${result.more ? ' · 더 있습니다. 이름을 더 치면 좁혀집니다.' : ''}`;
  }

  function toggle(r: FindRow): void {
    const k = groupKey(r.group);
    if (!opened.delete(k)) opened.add(k);
    render();
  }

  function paint(): void {
    [...list.children].forEach((li, i) => {
      li.classList.toggle('on', i === chosen);
      li.setAttribute('aria-selected', String(i === chosen));
    });
    (list.children[chosen] as HTMLElement | undefined)?.scrollIntoView({ block: 'nearest' });
  }

  async function ask(): Promise<void> {
    const text = input.value;
    const n = ++asked;
    if (!text.trim()) { result = null; render(); return; }
    const r = await host.query(text);
    if (n !== asked) return;       // a newer question is on its way
    result = r;
    render();
  }

  input.addEventListener('input', () => {
    opened.clear();
    chosenKey = null;
    clearTimeout(timer);
    timer = window.setTimeout(() => void ask(), 90);
  });
  input.addEventListener('keydown', (e) => {
    if (e.isComposing) return;
    if (e.key === 'ArrowDown') { e.preventDefault(); if (rows.length) { chosen = Math.min(rows.length - 1, chosen + 1); chosenKey = rows[chosen].key; paint(); } }
    else if (e.key === 'ArrowUp') { e.preventDefault(); if (rows.length) { chosen = Math.max(0, chosen - 1); chosenKey = rows[chosen].key; paint(); } }
    else if (e.key === 'Enter') {
      e.preventDefault();
      clearTimeout(timer);
      // the answer to what is typed now first, then its chosen (or first) row
      void (async () => {
        if (!result || result.text !== input.value) await ask();
        const r = rows[Math.max(0, chosen)];
        if (r) host.go(r.place);
      })();
    }
  });
  root.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); hide(); }
  });
  close.addEventListener('click', () => hide());

  function hide(): void {
    root.hidden = true;
  }

  return {
    root,
    open: () => {
      root.hidden = false;
      input.focus();
      input.select();
      render();
    },
    close: hide,
    isOpen: () => !root.hidden,
    refresh: () => { if (!root.hidden && input.value.trim()) void ask(); else render(); },
  };
}
