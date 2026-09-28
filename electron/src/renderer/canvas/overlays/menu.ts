/* A menu at the pointer with submenus (N-15, D-151): the Canvas's right-click
   items for the overlays (Influence ▸, Signal Flow ▸, Signal Group ▸, Net
   Information…, Highlight Net, the area memos), as v1's context menus had
   them.  Closed by a click elsewhere, Esc, or a command.  Arrow keys move
   (→ opens a submenu, ← closes it), Enter and Space run.  Names in English
   (D-135); a menu is names.

   The full right-click menu of the Canvas (Delete, Duplicate, Attach …) is
   N-10's; it takes these items as its own (overlayMenu in controller.ts
   builds them for a place on the Canvas). */

import { h } from '../../shared/dom.ts';

export interface MenuEntry {
  label: string;
  run?: () => void;
  checked?: boolean;          // a check item (on or off)
  radio?: boolean;            // one of a group (a dot, not a tick)
  disabled?: boolean;
  items?: MenuEntry[];        // a submenu
  title?: string;             // what it does, a sentence (tooltip)
}
export const SEPARATOR: MenuEntry = { label: '-' };

let open: HTMLElement[] = [];

export function closeMenus(): void {
  for (const m of open) m.remove();
  open = [];
}

export function menuOpen(): boolean { return open.length > 0; }

function build(entries: MenuEntry[], depth: number): HTMLElement {
  const menu = h('div', { class: 'ovmenu', role: 'menu' });
  for (const e of entries) {
    if (e === SEPARATOR || e.label === '-') { menu.append(h('hr')); continue; }
    const role = e.checked !== undefined ? (e.radio ? 'menuitemradio' : 'menuitemcheckbox') : 'menuitem';
    const b = h('button', {
      type: 'button', role, disabled: e.disabled === true, title: e.title,
      'aria-checked': e.checked !== undefined ? String(e.checked) : undefined,
      'aria-haspopup': e.items ? 'menu' : undefined,
    }, h('span', { class: 'mark', 'aria-hidden': 'true' }, e.checked ? (e.radio ? '●' : '✓') : ''), h('span', { class: 'label' }, e.label),
      e.items ? h('span', { class: 'sub', 'aria-hidden': 'true' }, '›') : null);
    if (e.items) {
      const openSub = () => {
        while (open.length > depth + 1) open.pop()!.remove();
        const sub = build(e.items!, depth + 1);
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
      b.addEventListener('pointerenter', () => { while (open.length > depth + 1) open.pop()!.remove(); });
      b.addEventListener('click', () => { closeMenus(); e.run?.(); });
    }
    menu.append(b);
  }
  menu.addEventListener('keydown', (k) => {
    const items = [...menu.querySelectorAll<HTMLButtonElement>(':scope > button:not([disabled])')];
    const i = items.indexOf(document.activeElement as HTMLButtonElement);
    if (k.key === 'ArrowDown') { k.preventDefault(); items[(i + 1) % items.length]?.focus(); }
    else if (k.key === 'ArrowUp') { k.preventDefault(); items[(i - 1 + items.length) % items.length]?.focus(); }
    else if (k.key === 'ArrowLeft' && depth > 0) {
      k.preventDefault();
      const parent = open[depth - 1];
      while (open.length > depth) open.pop()!.remove();
      (parent.querySelector('button[aria-haspopup]') as HTMLButtonElement | null)?.focus();
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
