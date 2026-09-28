/* The overlays' two small windows (N-15, D-151), in the window's own dialog
   (shared.css .modal, as ask.ts: a dark backdrop, only its controls
   reachable, Esc cancels):

   - Area Memo (v1 E-08 MemoMenu): the word, the colour (the tunnel
     palette, v1's names), the box's X, Y, Width and Height; OK or Cancel.
   - Net Information (v1 B-09 NetInfoDialog): the net's width, the ports
     that drive it, read it, and the rest on it (tunnels, splitters), in
     the common names (`alu #1 (Result)`); Close.

   Names and facts are English; the one sentence (what a memo is) Korean. */

import type { AreaMemo, NetInfo } from '../../../main/protocol.ts';
import { code, h } from '../../shared/dom.ts';
import { TUNNEL_PALETTE } from '../tokens.ts';

export const COLOR_NAMES = ['Orange', 'Sky blue', 'Bluish green', 'Blue', 'Vermillion', 'Pink', 'Indigo', 'Green', 'Olive', 'Wine', 'Teal', 'Purple'];
export const MEMO_HINT = '상자는 부품 뒤에 그려지고 회로 파일에 함께 저장됩니다. 원조 Logisim 2.7.1 프로그램은 이 상자를 건너뜁니다.';

function modal(label: string, ...body: (Node | null)[]): HTMLDialogElement {
  const d = h('dialog', { class: 'modal ovdialog', 'aria-label': label }, ...body);
  d.addEventListener('close', () => { d.remove(); document.body.classList.remove('dialog-open'); });
  document.body.append(d);
  document.body.classList.add('dialog-open');
  d.showModal();
  return d;
}

/* The memo's word, colour and box; null: cancelled.  `start` is what the box is now (or would be). */
export function memoDialog(start: AreaMemo, adding: boolean): Promise<AreaMemo | null> {
  return new Promise((answer) => {
    const text = h('input', { type: 'text', value: start.text, 'aria-label': 'Text', maxlength: '60' }) as HTMLInputElement;
    const color = h('select', { 'aria-label': 'Color' }, ...COLOR_NAMES.map((n, i) => h('option', { value: String(i), selected: i === start.color }, n))) as HTMLSelectElement;
    const swatch = h('span', { class: 'swatch', style: `background:${TUNNEL_PALETTE[start.color] ?? TUNNEL_PALETTE[0]}` });
    color.addEventListener('change', () => { swatch.style.background = TUNNEL_PALETTE[Number(color.value)]; });
    const num = (label: string, v: number, min: number) => h('input', { type: 'number', value: String(v), step: '10', min: String(min), max: '100000', 'aria-label': label, class: 'mono' }) as HTMLInputElement;
    const x = num('X', start.x, -100000), y = num('Y', start.y, -100000), w = num('Width', start.w, 20), hh = num('Height', start.h, 20);
    const ok = h('button', { type: 'button', class: 'btn primary' }, 'OK');
    const cancel = h('button', { type: 'button', class: 'btn' }, 'Cancel');
    const row = (label: string, ...c: Node[]) => h('label', { class: 'ovfield' }, h('span', {}, label), ...c);
    const d = modal('Area Memo',
      h('h2', {}, adding ? 'Add Area Memo' : 'Edit Area Memo'),
      h('div', { class: 'ovgrid' }, row('Text', text), row('Color', h('span', { class: 'ovcolor' }, swatch, color)),
        row('X', x), row('Y', y), row('Width', w), row('Height', hh)),
      h('p', { class: 'hint' }, MEMO_HINT),
      h('div', { class: 'row end' }, cancel, ok));
    let result: AreaMemo | null = null;
    const int = (i: HTMLInputElement, min: number) => Math.max(min, Math.round(Number(i.value) || 0));
    ok.addEventListener('click', () => {
      result = { x: int(x, -100000), y: int(y, -100000), w: int(w, 20), h: int(hh, 20), color: Number(color.value), text: text.value.trim() };
      d.close();
    });
    cancel.addEventListener('click', () => d.close());
    d.addEventListener('keydown', (e) => { if (e.key === 'Enter' && (e.target as HTMLElement).tagName === 'INPUT') { e.preventDefault(); ok.click(); } });
    d.addEventListener('close', () => answer(result));
    text.focus();
    text.select();
  });
}

const count = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`;

/* v1's words: "Width: 32 bits", "Driven by (1)", "Read by (2)", "Also on this net (3)". */
export function netInfoLines(n: NetInfo): { title: string; items: string[] }[] {
  return [
    { title: `Driven by (${n.drivers.length})`, items: n.drivers.map((p) => p.text) },
    { title: `Read by (${n.readers.length})`, items: n.readers.map((p) => p.text) },
    { title: `Also on this net (${n.others.length})`, items: n.others.map((p) => p.text) },
  ];
}

export function netDialog(n: NetInfo): Promise<void> {
  return new Promise((done) => {
    const close = h('button', { type: 'button', class: 'btn primary' }, 'Close');
    const d = modal('Net Information',
      h('h2', {}, 'Net Information', n.name ? h('span', { class: 'ovname' }, ' · ', code(n.name)) : null),
      h('p', { class: 'ovwidth' }, `Width: ${count(n.width, 'bit', 'bits')}`),
      ...netInfoLines(n).map((s) => h('section', { class: 'ovnet' }, h('h3', {}, s.title),
        s.items.length ? h('ul', {}, ...s.items.map((t) => h('li', {}, code(t)))) : h('p', { class: 'dim' }, '—'))),
      h('div', { class: 'row end' }, close));
    close.addEventListener('click', () => d.close());
    d.addEventListener('close', () => done());
    close.focus();
  });
}
