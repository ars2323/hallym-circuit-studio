/* Set Pin Value (N-08, D-146; v1 Shortcuts.askValue, I-78): a double
   click on an input pin with the Edit tool asks what to put on it --
   0x1F, 0b1011, 31, -3 (two's complement); underscores and spaces are
   ignored.  The engine reads the text (sim.pinValue, v1's parseValue) and
   refuses a value too wide for the pin: the dialog stays open with the
   reason under the field.  Only the simulation's state changes (not the
   file).  No character here: the dialog can show an error. */

import type { Component } from '../../main/protocol.ts';
import { h } from '../shared/dom.ts';

// The value for the pin, sent by `submit`; it answers null when taken, or the reason it was refused.
export function askPinValue(c: Component, now: string | null, submit: (text: string) => Promise<string | null>): Promise<boolean> {
  return new Promise((answer) => {
    const width = Number(c.attrs.width ?? '1') || 1;
    const input = h('input', {
      type: 'text', class: 'field mono', 'aria-label': 'Value', value: now ?? '', autocomplete: 'off', spellcheck: 'false',
    }) as HTMLInputElement;
    const why = h('span', { class: 'hint err', role: 'alert' });
    const ok = h('button', { class: 'btn primary', type: 'button' }, 'Set');
    const cancel = h('button', { class: 'btn', type: 'button' }, 'Cancel');
    const name = c.attrs.label ? `Pin ${c.attrs.label}` : 'Pin';
    const dialog = h('dialog', { class: 'modal ask pinvalue', 'aria-label': 'Set Pin Value' },
      h('div', { class: 'asktext' }, h('h2', {}, 'Set Pin Value'),
        h('p', {}, `입력 핀에 넣을 값을 적으세요(${width} bit${width === 1 ? '' : 's'}). 예: 0x1F · 0b1011 · 31 · -3(2의 보수)`),
        h('label', { class: 'cyclesfield' }, h('span', { class: 'fieldname' }, name), input),
        why,
        h('div', { class: 'row end' }, cancel, ok)));
    let taken = false;
    let busy = false;
    const go = async () => {
      if (busy) return;
      busy = true;
      const err = await submit(input.value);
      busy = false;
      if (err === null) { taken = true; dialog.close(); return; }
      why.textContent = err;
      input.setAttribute('aria-invalid', 'true');
      input.focus();
      input.select();
    };
    ok.addEventListener('click', () => void go());
    cancel.addEventListener('click', () => dialog.close());
    input.addEventListener('keydown', (e) => { if (e.key === 'Enter' && !e.isComposing) { e.preventDefault(); void go(); } });
    input.addEventListener('input', () => { why.textContent = ''; input.removeAttribute('aria-invalid'); });
    dialog.addEventListener('close', () => {
      dialog.remove();
      document.body.classList.remove('dialog-open');
      answer(taken);
    });
    document.body.append(dialog);
    document.body.classList.add('dialog-open');
    dialog.showModal();
    input.focus();
    input.select();
  });
}

// The pin's value now as the dialog shows it first: hex for a bus, the bit for one bit ('' when not a number).
export function valueText(v: string | undefined, width: number): string {
  if (!v || v.length !== width || /[^01]/.test(v)) return '';
  return width === 1 ? v : `0x${BigInt(`0b${v}`).toString(16)}`;
}
