/* The splitter between two panels (derived from Hallym MIPS v2.5.0
   electron/src/renderer/app/app.ts: its splitter between the Editor and the
   Run side, and its grips over the Console and the Assemble panel): drag to
   share the room, double-click for the default share.  One component for
   both directions:

     'columns'  between two columns: col-resize, a vertical grip
     'rows'     between two rows: row-resize, a horizontal grip

   The owner decides what a position means -- onDrag gets where the
   splitter's middle would be, in the page (where it was grabbed does not
   make it jump) -- and keeps the size, for this run only: nothing of it is
   kept for the next start (the lab-PC rule). */

import { h } from './dom.ts';

export interface SplitterOptions {
  between: 'columns' | 'rows';
  label: string;                                   // what it resizes, for the tooltip and screen readers
  onDrag(at: { x: number; y: number }): void;
  onReset(): void;
}

export function splitter(o: SplitterOptions): HTMLElement {
  const columns = o.between === 'columns';
  const el = h('div', {
    class: columns ? 'splitter' : 'vgrip', role: 'separator',
    'aria-orientation': columns ? 'vertical' : 'horizontal', 'aria-label': o.label,
    title: '끌어서 크기 조절 · 두 번 눌러 되돌리기',
  }, h('span', { class: 'grip' }));
  el.addEventListener('pointerdown', (e) => {
    if (e.button !== 0) return;
    e.preventDefault();
    el.setPointerCapture(e.pointerId);
    el.classList.add('dragging');
    const r = el.getBoundingClientRect();
    const grab = { x: e.clientX - (r.left + r.width / 2), y: e.clientY - (r.top + r.height / 2) };
    const move = (m: PointerEvent) => o.onDrag({ x: m.clientX - grab.x, y: m.clientY - grab.y });
    const up = () => {
      el.classList.remove('dragging');
      el.removeEventListener('pointermove', move);
      el.removeEventListener('pointerup', up);
      el.removeEventListener('pointercancel', up);
    };
    el.addEventListener('pointermove', move);
    el.addEventListener('pointerup', up);
    el.addEventListener('pointercancel', up);
  });
  el.addEventListener('dblclick', () => o.onReset());
  return el;
}

// A size dragged to: within [least, most], rounded to a pixel.
export const clampSize = (want: number, least: number, most: number): number =>
  Math.round(Math.max(least, Math.min(Math.max(least, most), want)));
