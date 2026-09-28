/* The search palette (Ctrl+K, or a letter typed on the Canvas; v1
   PaletteWindow, D-037, D-139, I-41, I-153, I-168..I-170; D-150): a box by
   the pointer -- parts, this file's circuits, the tunnels of the circuit on
   show and commands, in one ranked list (logic/search.ts).

     ↑ ↓         the chosen row (a new list: the first)
     Enter       place the part (or circuit) at the pointer, on the grid --
                 one, no tool held; go to the tunnel; run the command
     Alt+Enter   a favourite, or not any more (this run only)
     Esc         close; so does a press outside it or the focus leaving it
                 (I-168 정함), and only one is ever open
     double click  the same as Enter

   Nothing in it is remembered after the app closes (the lab-PC rule). */

import { code, h } from '../shared/dom.ts';
import { search, type SearchItem, type Sources, toggleFavorite, touch } from './logic/search.ts';

export interface PaletteHost {
  sources(): Sources | null;                    // null: no file open
  anchor(): { x: number; y: number };           // where to open it (window px)
  choose(item: SearchItem): void;
}

export interface Palette {
  root: HTMLElement;
  open(text?: string): void;
  close(): void;
  isOpen(): boolean;
  recent(): readonly string[];
  favorites(): readonly string[];
  remember(key: string): void;
}

export const HINT = '↑↓ Select · Enter Place/Run · Alt+Enter Favorite · Esc Close';
export const EMPTY_HINT = '부품 이름, 한글 별칭(앤드, 먹스), 명령(리셋, 맞춤)을 칩니다. 이름 뒤 숫자는 입력 수나 폭입니다: `and 3`, `mux 32`';

const KIND: Record<SearchItem['kind'], string> = { component: 'Part', subcircuit: 'Subcircuit', tunnel: 'Tunnel', command: 'Command' };

export function palette(host: PaletteHost): Palette {
  let recent: string[] = [];
  let favorites: string[] = [];
  let items: SearchItem[] = [];
  let chosen = 0;
  const input = h('input', { type: 'text', class: 'palinput', 'aria-label': 'Search', placeholder: 'Search parts, circuits, tunnels, commands', spellcheck: 'false', autocomplete: 'off' });
  const list = h('ul', { class: 'pallist', role: 'listbox', 'aria-label': 'Results' });
  const note = h('p', { class: 'palnote' });
  const root = h('div', { class: 'palette', role: 'dialog', 'aria-label': 'Search', hidden: true },
    input, list, note, h('div', { class: 'palhint' }, HINT));

  function render(): void {
    const src = host.sources();
    items = src ? search(input.value, { ...src, recent, favorites }) : [];
    chosen = Math.min(Math.max(0, chosen), Math.max(0, items.length - 1));
    list.replaceChildren(...items.map((it, i) => {
      const fav = favorites.includes(it.key);
      const li = h('li', { role: 'option', class: `palrow kind-${it.kind}${i === chosen ? ' on' : ''}`, 'aria-selected': String(i === chosen), 'data-key': it.key },
        h('span', { class: 'palkind' }, KIND[it.kind]),
        h('span', { class: 'palname' }, it.kind === 'component' || it.kind === 'command' ? it.name : code(it.name)),
        h('span', { class: 'palmore' }, it.attrText || (it.kind === 'tunnel' ? `${it.count} ${it.count === 1 ? 'tunnel' : 'tunnels'}` : it.kind === 'component' ? it.group ?? '' : it.kind === 'subcircuit' ? it.group ?? '' : '')),
        fav ? h('span', { class: 'palfav', title: 'Favorite', 'aria-label': 'Favorite' }, '★') : null);
      li.addEventListener('mousedown', (e) => e.preventDefault());    // keep the focus in the box
      li.addEventListener('click', () => { chosen = i; paint(); });
      li.addEventListener('dblclick', () => { chosen = i; run(); });
      return li;
    }));
    const typed = input.value.trim() !== '';
    note.hidden = typed && items.length > 0;
    if (!typed) note.replaceChildren(...hintText());
    else if (!items.length) note.replaceChildren('맞는 것이 없습니다: ', code(input.value.trim()));
  }

  function hintText(): (Node | string)[] {
    return EMPTY_HINT.split('`').map((t, i) => (i % 2 ? code(t) : t));
  }

  function paint(): void {
    [...list.children].forEach((li, i) => {
      li.classList.toggle('on', i === chosen);
      li.setAttribute('aria-selected', String(i === chosen));
    });
    (list.children[chosen] as HTMLElement | undefined)?.scrollIntoView({ block: 'nearest' });
  }

  function run(): void {
    const it = items[chosen];
    if (!it) return;
    close();
    if (it.kind === 'component' || it.kind === 'subcircuit') recent = touch(recent, it.key);
    host.choose(it);
  }

  function close(): void {
    if (root.hidden) return;
    root.hidden = true;
    input.value = '';
    items = [];
    list.replaceChildren();
  }

  input.addEventListener('input', () => { chosen = 0; render(); });
  input.addEventListener('keydown', (e) => {
    if (e.isComposing) return;
    if (e.key === 'ArrowDown') { e.preventDefault(); chosen = Math.min(items.length - 1, chosen + 1); paint(); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); chosen = Math.max(0, chosen - 1); paint(); }
    else if (e.key === 'Enter' && e.altKey) {
      e.preventDefault();
      const it = items[chosen];
      if (it) { favorites = toggleFavorite(favorites, it.key); render(); }
    } else if (e.key === 'Enter') { e.preventDefault(); run(); }
    else if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); close(); }
  });
  // A press outside it, or the focus going elsewhere, closes it (I-168 정함).
  window.addEventListener('pointerdown', (e) => { if (!root.hidden && !root.contains(e.target as Node)) close(); }, true);
  root.addEventListener('focusout', (e) => { if (!root.contains(e.relatedTarget as Node | null)) close(); });

  return {
    root,
    open: (text = '') => {
      if (!host.sources()) return;
      const a = host.anchor();
      root.hidden = false;
      input.value = text;
      chosen = 0;
      render();
      // By the pointer, kept inside the window.
      const w = root.offsetWidth || 440, hh = root.offsetHeight || 320;
      root.style.left = `${Math.max(8, Math.min(a.x, window.innerWidth - w - 8))}px`;
      root.style.top = `${Math.max(8, Math.min(a.y, window.innerHeight - hh - 8))}px`;
      input.focus();
      input.setSelectionRange(input.value.length, input.value.length);
    },
    close,
    isOpen: () => !root.hidden,
    recent: () => recent,
    favorites: () => favorites,
    remember: (key) => { recent = touch(recent, key); },
  };
}
