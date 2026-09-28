/* The N Cycles dialog (N-07, D-145): how many clock cycles to go -- v1's
   question (a number, 10 at first, 1 to 100000), in the window's own
   dialog (the ask dialog's look without a character -- it can say why a
   count is refused, and the characters are never next to an error; the
   backdrop, a click outside does nothing, Esc is Cancel).  Enter runs; a count out of range
   keeps the dialog open with the reason under the field.  The title is the
   command's name (English), the sentence Korean (D-135). */

import { h } from '../shared/dom.ts';
import { CYCLES_DEFAULT, CYCLES_MAX, CYCLES_MIN, parseCycles } from './logic/sim.ts';

// The count to run, or null (Cancel, Esc).  `last`: the count given the time before (this run only).
export function askCycles(last: number | null = null): Promise<number | null> {
  return new Promise((answer) => {
    const input = h('input', {
      type: 'text', inputmode: 'numeric', class: 'field mono', 'aria-label': 'Cycles', value: String(last ?? CYCLES_DEFAULT),
      autocomplete: 'off', spellcheck: 'false',
    });
    const why = h('span', { class: 'hint err', role: 'alert' });
    const ok = h('button', { class: 'btn primary', type: 'button' }, 'Run');
    const cancel = h('button', { class: 'btn', type: 'button' }, 'Cancel');
    const dialog = h('dialog', { class: 'modal ask cycles', 'aria-label': 'N Cycles' },
      h('div', { class: 'askbody' },
        h('div', { class: 'asktext' }, h('h2', {}, 'N Cycles'),
          h('p', {}, `진행할 클럭 사이클 수를 적으세요(${CYCLES_MIN}–${CYCLES_MAX}). 한 사이클은 클럭 틱 두 번입니다.`),
          h('label', { class: 'cyclesfield' }, h('span', { class: 'fieldname' }, 'Cycles'), input),
          why,
          h('div', { class: 'row end' }, cancel, ok))));
    let result: number | null = null;
    const submit = () => {
      const r = parseCycles(input.value);
      if ('error' in r) {
        why.textContent = r.error;
        input.setAttribute('aria-invalid', 'true');
        input.focus();
        input.select();
        return;
      }
      result = r.n;
      dialog.close();
    };
    ok.addEventListener('click', submit);
    cancel.addEventListener('click', () => dialog.close());
    input.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' && !e.isComposing) { e.preventDefault(); submit(); }
    });
    input.addEventListener('input', () => { why.textContent = ''; input.removeAttribute('aria-invalid'); });
    dialog.addEventListener('close', () => {
      dialog.remove();
      document.body.classList.remove('dialog-open');
      answer(result);
    });
    document.body.append(dialog);
    document.body.classList.add('dialog-open');
    dialog.showModal();
    input.focus();
    input.select();
  });
}
