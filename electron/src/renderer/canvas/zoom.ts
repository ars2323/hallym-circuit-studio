/* The zoom in the status bar (N-05; v1 S-21): one display, always the
   Canvas's zoom; pressed, a menu of the steps and Fit (Ctrl+0). */

import { h } from '../shared/dom.ts';
import { percent, STEPS } from './view.ts';

export interface ZoomTarget {
  zoom(): number;
  zoomTo(z: number): void;
  fit(): void;
  step(dir: 1 | -1): void;
}

export function zoomControl(t: ZoomTarget): { button: HTMLButtonElement; menu: HTMLElement; update(zoom?: number): void } {
  const button = h('button', { type: 'button', class: 'zoom-button', title: 'Zoom (Ctrl+wheel, Ctrl+ +/−, Ctrl+0 fits)', 'aria-haspopup': 'menu', 'aria-expanded': 'false' }) as HTMLButtonElement;
  const menu = h('div', { class: 'zoom-menu', role: 'menu', hidden: true });
  const item = (label: string, key: string, run: () => void, on = false) => {
    const b = h('button', { type: 'button', role: 'menuitem', class: on ? 'on' : undefined }, h('span', {}, label), key ? h('kbd', {}, key) : null);
    b.addEventListener('click', () => { hide(); run(); });
    return b;
  };
  const hide = () => { menu.hidden = true; button.setAttribute('aria-expanded', 'false'); };
  const show = () => {
    const z = t.zoom();
    menu.replaceChildren(
      item('Fit to window', 'Ctrl+0', () => t.fit()),
      item('Zoom in', 'Ctrl++', () => t.step(1)),
      item('Zoom out', 'Ctrl+−', () => t.step(-1)),
      h('hr'),
      ...[...STEPS].reverse().map((s) => item(percent(s), '', () => t.zoomTo(s), Math.abs(s - z) < 0.005)));
    menu.hidden = false;
    button.setAttribute('aria-expanded', 'true');
    const r = button.getBoundingClientRect();
    menu.style.left = `${Math.max(8, Math.min(window.innerWidth - 200, r.left))}px`;
    menu.style.bottom = `${window.innerHeight - r.top + 6}px`;
  };
  button.addEventListener('click', () => (menu.hidden ? show() : hide()));
  document.addEventListener('pointerdown', (e) => { if (!menu.hidden && !menu.contains(e.target as Node) && !button.contains(e.target as Node)) hide(); }, true);
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape' && !menu.hidden) hide(); });
  const update = (zoom = t.zoom()) => { button.textContent = percent(zoom); };
  update();
  return { button, menu, update };
}
