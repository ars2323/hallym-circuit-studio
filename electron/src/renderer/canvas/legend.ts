/* The value colours' legend (N-05; v1 E-03): what each wire colour means,
   drawn from the same CSS custom properties the Canvas reads its colours
   from (canvas.css --v-*, tokens.ts), so the two cannot disagree.  Opened
   from the status bar's "Wire Colors"; also where the bus widths are
   switched off and on (for this run only: the lab-PC rule). */

import { h } from '../shared/dom.ts';
import { LEGEND, VALUE_VARS } from './tokens.ts';

export interface LegendOptions {
  busWidths: boolean;
  onBusWidths(on: boolean): void;
}

export function legend(o: LegendOptions): { button: HTMLButtonElement; panel: HTMLElement } {
  const rows = LEGEND.map((r) => h('li', { 'data-token': r.token },
    h('span', { class: `swatch${r.token === 'vBus' ? ' bus' : ''}`, style: `background: var(${VALUE_VARS[r.token as keyof typeof VALUE_VARS]})` }),
    h('span', { class: 'mono name' }, r.name),
    h('span', { class: 'say' }, r.say)));
  const box = h('input', { type: 'checkbox', checked: o.busWidths }) as HTMLInputElement;
  box.addEventListener('change', () => o.onBusWidths(box.checked));
  // Names (the panel, the option, the button) in English title case as v1's (names.properties bar.legend,
  // legend.widths; GLOSSARY); the explanations are Korean.
  const panel = h('div', { class: 'legend-panel', role: 'dialog', 'aria-label': 'Wire Colors', hidden: true },
    h('h4', {}, 'Wire Colors'),
    h('ul', {}, ...rows),
    h('label', { class: 'legend-opt' }, box, 'Show Bus Widths'));
  const button = h('button', { type: 'button', class: 'legend-button', title: '선 색이 무엇을 뜻하는지 봅니다', 'aria-expanded': 'false' },
    h('span', { class: 'legend-dots', 'aria-hidden': 'true' },
      ...(['vOne', 'vZero', 'vFloat', 'vError'] as const).map((t) => h('span', { style: `background: var(${VALUE_VARS[t]})` }))),
    'Wire Colors') as HTMLButtonElement;
  const close = (e: Event) => {
    if (panel.hidden || panel.contains(e.target as Node) || button.contains(e.target as Node)) return;
    panel.hidden = true;
    button.setAttribute('aria-expanded', 'false');
  };
  button.addEventListener('click', () => {
    panel.hidden = !panel.hidden;
    button.setAttribute('aria-expanded', String(!panel.hidden));
    if (!panel.hidden) {
      const r = button.getBoundingClientRect();
      panel.style.left = `${Math.max(8, Math.min(window.innerWidth - 268, r.left))}px`;
      panel.style.bottom = `${window.innerHeight - r.top + 6}px`;
    }
  });
  document.addEventListener('pointerdown', close, true);
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape' && !panel.hidden) { panel.hidden = true; button.setAttribute('aria-expanded', 'false'); } });
  return { button, panel };
}
