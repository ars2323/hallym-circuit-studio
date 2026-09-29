/* The window's menu on screen (logic/menus.ts has what is in it): the
   title bar's Menu button opens File › Edit › Simulate › Window › Help ›
   under it, with the keys; the toolbar's » menu (the commands the bar had
   no room for) is the same kind of menu.  The Canvas's own menu component
   (../canvas/overlays/menu.ts: submenus, arrow keys, Esc) draws both. */

import { showMenu, type MenuEntry } from '../canvas/overlays/menu.ts';
import type { MenuSpec } from './logic/menus.ts';

export function entries(specs: MenuSpec[], run: (id: string) => void): MenuEntry[] {
  return specs.map((s): MenuEntry => {
    if (s.label === '-') return { label: '-' };
    const e: MenuEntry = { label: s.label };
    if (s.key) e.keys = s.key;
    if (s.disabled) e.disabled = true;
    if (s.checked !== undefined) e.checked = s.checked;
    if (s.radio) e.radio = true;
    if (s.items) e.items = entries(s.items, run);
    else if (s.id) { const id = s.id; e.run = () => run(id); }
    return e;
  });
}

// A menu opened from a button in the title bar: under it, its right edge at the button's when it would not fit.
export function menuUnder(anchor: DOMRect, list: MenuEntry[]): HTMLElement {
  const m = showMenu(list, anchor.left, anchor.bottom + 4);
  m.classList.add('barmenu');
  const r = m.getBoundingClientRect();
  if (r.right > window.innerWidth - 4) m.style.left = `${Math.max(4, anchor.right - r.width)}px`;
  return m;
}
