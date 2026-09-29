/* A menu at the pointer with submenus (N-15, D-151; N-10, D-157): the
   Canvas's right-click menu (built from one registry, app/menus/), the
   Components list's and the circuit tabs' menus, v1's context menus.
   Closed by a click elsewhere, Esc, or a command.  Arrow keys move (→ opens
   a submenu, ← closes it), Home/End go to the first and last item, Enter
   and Space run; a letter goes to the next item starting with it.  Names in
   English (D-135); a menu is names.

   An entry may be a header (the bold summary line at the top, v1
   MenuLayout: not an item), carry a key hint on its right (Ctrl+D, R, F2 …)
   and a colour swatch (Tunnel Color ▸).  `id` names an entry for whoever
   arranges entries from several places (the overlays' items, N-15). */

import { h } from '../../shared/dom.ts';

export interface MenuEntry {
  label: string;
  id?: string;                // what the entry is, for arranging (the overlays' items)
  run?: () => void;
  checked?: boolean;          // a check item (on or off)
  radio?: boolean;            // one of a group (a dot, not a tick)
  disabled?: boolean;
  items?: MenuEntry[];        // a submenu
  title?: string;             // what it does, a sentence (tooltip)
  keys?: string;              // the key that does the same ("Ctrl+D"), shown on the right
  header?: boolean;           // the summary line at the top: bold, not an item
  swatch?: string;            // a colour shown before the label (#rrggbb)
}
export const SEPARATOR: MenuEntry = { label: '-' };

let open: HTMLElement[] = [];

export function closeMenus(): void {
  for (const m of open) m.remove();
  open = [];
}

export function menuOpen(): boolean { return open.length > 0; }

// The submenus from `depth` on closed; the item that opened the last one left no longer marked open.
function closeFrom(depth: number): void {
  while (open.length > depth) open.pop()!.remove();
  open[depth - 1]?.querySelectorAll('[aria-expanded="true"]').forEach((x) => x.setAttribute('aria-expanded', 'false'));
}

// The entries shown: no separator at the start or the end, none twice in a row, none right after the header.
export function tidy(entries: MenuEntry[]): MenuEntry[] {
  const out: MenuEntry[] = [];
  for (const e of entries) {
    const sep = e === SEPARATOR || e.label === '-';
    if (sep && (out.length === 0 || out[out.length - 1].label === '-' || out[out.length - 1].header)) continue;
    out.push(e);
  }
  while (out.length && out[out.length - 1].label === '-') out.pop();
  return out;
}

// A long list of short choices (Data Bits' 32, Take One Bit's [31]…[0]): in columns, not down the whole screen.
export const inColumns = (entries: MenuEntry[]): boolean =>
  entries.length > 12 && entries.every((e) => !e.items && !e.header && e.label !== '-' && e.label.length <= 5);

function build(entries: MenuEntry[], depth: number): HTMLElement {
  const menu = h('div', { class: `ovmenu${inColumns(entries) ? ' cols' : ''}`, role: 'menu' });
  for (const e of tidy(entries)) {
    if (e === SEPARATOR || e.label === '-') { menu.append(h('hr')); continue; }
    if (e.header) { menu.append(h('div', { class: 'mhead', role: 'presentation' }, e.label)); continue; }
    const role = e.checked !== undefined ? (e.radio ? 'menuitemradio' : 'menuitemcheckbox') : 'menuitem';
    const mark = e.swatch
      ? h('span', { class: 'mark swatchmark', 'aria-hidden': 'true' }, h('span', { class: 'swatch', style: `background:${e.swatch}` }), e.checked ? h('span', { class: 'on' }, '●') : null)
      : h('span', { class: 'mark', 'aria-hidden': 'true' }, e.checked ? (e.radio ? '●' : '✓') : '');
    const b = h('button', {
      type: 'button', role, disabled: e.disabled === true, title: e.title,
      'aria-checked': e.checked !== undefined ? String(e.checked) : undefined,
      'aria-haspopup': e.items ? 'menu' : undefined,
      'aria-keyshortcuts': e.keys,
      'data-id': e.id,
    }, mark, h('span', { class: 'label' }, e.label),
    e.items ? h('span', { class: 'sub', 'aria-hidden': 'true' }, '›') : h('span', { class: 'keys', 'aria-hidden': 'true' }, e.keys ?? ''));
    if (e.items) {
      const openSub = () => {
        closeFrom(depth + 1);
        const sub = build(e.items!, depth + 1);
        b.setAttribute('aria-expanded', 'true');   // the row stays lit while its submenu is open (UI review of menu.png)
        document.body.append(sub);
        const r = b.getBoundingClientRect();
        place(sub, r.right - 2, r.top - 5, r.left);
        open.push(sub);
        return sub;
      };
      b.addEventListener('pointerenter', () => { if (open[depth + 1]?.dataset.for !== e.label) { const s = openSub(); s.dataset.for = e.label; } });
      b.addEventListener('click', () => { const s = openSub(); s.dataset.for = e.label; focusFirst(s); });
      b.addEventListener('keydown', (k) => { if (k.key === 'ArrowRight') { k.preventDefault(); const s = openSub(); s.dataset.for = e.label; focusFirst(s); } });
    } else {
      b.addEventListener('pointerenter', () => closeFrom(depth + 1));
      b.addEventListener('click', () => { closeMenus(); e.run?.(); });
    }
    menu.append(b);
  }
  menu.addEventListener('keydown', (k) => {
    const items = [...menu.querySelectorAll<HTMLButtonElement>(':scope > button:not([disabled])')];
    const i = items.indexOf(document.activeElement as HTMLButtonElement);
    const cols = menu.classList.contains('cols');
    const row = cols ? 4 : 1;                 // in columns: ↑↓ a row, ←→ an item (← at a row's start closes a submenu)
    if (k.key === 'ArrowDown') { k.preventDefault(); items[cols ? Math.min(items.length - 1, i + row) : (i + 1) % items.length]?.focus(); }
    else if (k.key === 'ArrowUp') { k.preventDefault(); items[cols ? Math.max(0, i - row) : (i - 1 + items.length) % items.length]?.focus(); }
    else if (cols && k.key === 'ArrowRight') { k.preventDefault(); items[Math.min(items.length - 1, i + 1)]?.focus(); }
    else if (cols && k.key === 'ArrowLeft' && (i % 4 !== 0 || depth === 0)) { k.preventDefault(); items[Math.max(0, i - 1)]?.focus(); }
    else if (k.key === 'Home') { k.preventDefault(); items[0]?.focus(); }
    else if (k.key === 'End') { k.preventDefault(); items[items.length - 1]?.focus(); }
    else if (k.key === 'ArrowLeft' && depth > 0) {
      k.preventDefault();
      const parent = open[depth - 1];
      closeFrom(depth);
      (parent.querySelector('button[aria-haspopup]') as HTMLButtonElement | null)?.focus();
    } else if (k.key.length === 1 && /\S/.test(k.key) && !k.ctrlKey && !k.altKey && !k.metaKey) {
      // a letter: the next item whose name starts with it
      const want = k.key.toLowerCase();
      const next = [...items.slice(i + 1), ...items.slice(0, i + 1)].find((x) => (x.querySelector('.label')?.textContent ?? '').toLowerCase().startsWith(want));
      if (next) { k.preventDefault(); next.focus(); }
    }
  });
  return menu;
}

function place(menu: HTMLElement, x: number, y: number, flipX?: number): void {
  const r = menu.getBoundingClientRect();
  const left = x + r.width > window.innerWidth - 4 && flipX !== undefined ? flipX - r.width + 2 : Math.min(x, window.innerWidth - r.width - 4);
  menu.style.left = `${Math.max(4, left)}px`;
  menu.style.top = `${Math.max(4, Math.min(y, window.innerHeight - r.height - 4))}px`;
}

const focusFirst = (m: HTMLElement) => (m.querySelector('button:not([disabled])') as HTMLButtonElement | null)?.focus();

export function showMenu(entries: MenuEntry[], x: number, y: number): HTMLElement {
  listen();
  closeMenus();
  const menu = build(entries, 0);
  document.body.append(menu);
  place(menu, x, y);
  open.push(menu);
  focusFirst(menu);
  return menu;
}

// A click elsewhere, Esc or leaving the window closes it (listening from the first menu on).
let listening = false;
function listen(): void {
  if (listening) return;
  listening = true;
  window.addEventListener('pointerdown', (e) => { if (open.length && !open.some((m) => m.contains(e.target as Node))) closeMenus(); }, true);
  window.addEventListener('keydown', (e) => { if (open.length && e.key === 'Escape') { e.stopPropagation(); e.preventDefault(); closeMenus(); } }, true);
  window.addEventListener('blur', () => closeMenus());
}
