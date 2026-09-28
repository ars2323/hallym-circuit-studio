/* A small menu at the pointer (right-click on a Cycle View row or a
   register): a few commands, closed by a click anywhere, Esc, or a
   command.  Names in English (D-135). */

import { h } from '../shared/dom.ts';

export interface MenuItem { label: string; run: () => void; disabled?: boolean }

let open: HTMLElement | null = null;

export function closeMenu(): void {
  open?.remove();
  open = null;
}

export function popupMenu(items: MenuItem[], x: number, y: number): HTMLElement {
  closeMenu();
  const menu = h('div', { class: 'popmenu', role: 'menu' }, ...items.map((it) => {
    const b = h('button', { type: 'button', role: 'menuitem', disabled: it.disabled === true }, it.label);
    b.addEventListener('click', () => { closeMenu(); it.run(); });
    return b;
  }));
  document.body.append(menu);
  const r = menu.getBoundingClientRect();
  menu.style.left = `${Math.max(4, Math.min(x, window.innerWidth - r.width - 4))}px`;
  menu.style.top = `${Math.max(4, Math.min(y, window.innerHeight - r.height - 4))}px`;
  open = menu;
  (menu.querySelector('button:not([disabled])') as HTMLButtonElement | null)?.focus();
  return menu;
}

window.addEventListener('pointerdown', (e) => { if (open && !open.contains(e.target as Node)) closeMenu(); }, true);
window.addEventListener('keydown', (e) => { if (open && e.key === 'Escape') { e.stopPropagation(); closeMenu(); } }, true);
window.addEventListener('blur', () => closeMenu());
