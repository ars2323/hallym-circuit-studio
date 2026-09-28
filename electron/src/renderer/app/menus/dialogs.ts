/* The right-click menu's dialogs (N-10, D-157; v1 EditMenus.askLabel,
   LabelsDialog, the tunnel name of Replace Wire with Tunnels…,
   ArrangeActions.duplicateN): the window's own dialog -- the ask dialog's
   look without a character (a dialog that can say why a value is refused;
   the characters never stand next to an error), backdrop, Esc = Cancel,
   Enter = OK.  The title and the field names are English (names), the
   sentences Korean.  What the engine refuses comes back as a sentence
   under the fields and the dialog stays open. */

import { h } from '../../shared/dom.ts';

// The engine's answer to what was typed: null when it was taken, else why not (a sentence).
export type Submit<T> = (value: T) => Promise<string | null>;

interface FormField { name: string; el: HTMLElement; focus?: HTMLInputElement }

function form<T>(o: { title: string; sentence: string; fields: FormField[]; ok: string; read(): T | string; submit: Submit<T>; cls?: string; extra?: HTMLElement | null }): Promise<boolean> {
  return new Promise((answer) => {
    const why = h('span', { class: 'hint err', role: 'alert' });
    const ok = h('button', { class: 'btn primary', type: 'button' }, o.ok);
    const cancel = h('button', { class: 'btn', type: 'button' }, 'Cancel');
    const dialog = h('dialog', { class: `modal ask menudlg ${o.cls ?? ''}`.trim(), 'aria-label': o.title },
      h('div', { class: 'asktext' }, h('h2', {}, o.title),
        h('p', {}, o.sentence),
        h('div', { class: 'mfields' }, ...o.fields.map((f) => h('label', { class: 'mfield' }, h('span', { class: 'fieldname' }, f.name), f.el))),
        o.extra ?? null,
        why,
        h('div', { class: 'row end' }, cancel, ok)));
    let taken = false;
    let busy = false;
    const go = async () => {
      if (busy) return;
      const v = o.read();
      if (typeof v === 'string') { why.textContent = v; return; }
      busy = true;
      const err = await o.submit(v);
      busy = false;
      if (err === null) { taken = true; dialog.close(); return; }
      why.textContent = err;
    };
    ok.addEventListener('click', () => void go());
    cancel.addEventListener('click', () => dialog.close());
    dialog.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' && !e.isComposing && (e.target as HTMLElement).tagName !== 'BUTTON' && (e.target as HTMLElement).tagName !== 'SELECT') { e.preventDefault(); void go(); }
    });
    dialog.addEventListener('input', () => { why.textContent = ''; });
    dialog.addEventListener('close', () => {
      dialog.remove();
      document.body.classList.remove('dialog-open');
      answer(taken);
    });
    document.body.append(dialog);
    document.body.classList.add('dialog-open');
    dialog.showModal();
    const first = o.fields.find((f) => f.focus)?.focus;
    first?.focus();
    first?.select();
  });
}

const textField = (label: string, value: string, mono = false): HTMLInputElement =>
  h('input', { type: 'text', class: `field${mono ? ' mono' : ''}`, 'aria-label': label, value, autocomplete: 'off', spellcheck: 'false' }) as HTMLInputElement;

// One text (Label…: "Label:"; Replace Wire with Tunnels…: "Tunnel Name:").
export function askText(o: { title: string; sentence: string; field: string; value: string; ok?: string; required?: string }, submit: Submit<string>): Promise<boolean> {
  const input = textField(o.field, o.value);
  return form<string>({
    title: o.title, sentence: o.sentence, ok: o.ok ?? 'OK',
    fields: [{ name: `${o.field}:`, el: input, focus: input }],
    read: () => (o.required && !input.value.trim() ? o.required : input.value),
    submit,
  });
}

// Edit Labels of N Components… (v1 LabelsDialog): a field per part, one undo step for all.
export function askLabels(rows: { id: string; name: string; label: string }[], submit: Submit<Record<string, string>>): Promise<boolean> {
  const inputs = rows.map((r) => textField(r.name, r.label));
  return form<Record<string, string>>({
    title: 'Labels', sentence: '부품마다 라벨을 적으세요. OK를 누르면 한 번에 바뀌고, Undo 한 번으로 모두 되돌립니다.', ok: 'OK', cls: 'labels',
    fields: rows.map((r, i) => ({ name: r.name, el: inputs[i], focus: i === 0 ? inputs[i] : undefined })),
    read: () => Object.fromEntries(rows.map((r, i) => [r.id, inputs[i].value])),
    submit,
  });
}

// Duplicate N… (v1 ArrangeActions.duplicateN, E-01): count 1–64 (3), direction (Down), spacing 10–2000
// (the group's size + 10 in that direction), and whether the labels' numbers go on (R0 → R1, R2 …).
export interface DuplicateN { count: number; direction: 'right' | 'down' | 'left' | 'up'; spacing: number; number: boolean }
export const DIRECTIONS: [DuplicateN['direction'], string][] = [['right', 'Right'], ['down', 'Down'], ['left', 'Left'], ['up', 'Up']];

export function askDuplicateN(o: { spacing(direction: DuplicateN['direction']): number; firstLabel: string | null; example: (label: string) => string }, submit: Submit<DuplicateN>): Promise<boolean> {
  const count = textField('Count', '3', true);
  count.inputMode = 'numeric';
  const dir = h('select', { 'aria-label': 'Direction' }, ...DIRECTIONS.map(([v, n]) => h('option', { value: v, selected: v === 'down' }, n))) as HTMLSelectElement;
  const spacing = textField('Spacing (px)', String(o.spacing('down')), true);
  spacing.inputMode = 'numeric';
  dir.addEventListener('change', () => { spacing.value = String(o.spacing(dir.value as DuplicateN['direction'])); });
  const number = h('input', { type: 'checkbox', checked: o.firstLabel !== null, disabled: o.firstLabel === null }) as HTMLInputElement;
  const extra = h('div', { class: 'mextra' },
    h('label', { class: 'mcheck' }, number, 'Number the labels'),
    o.firstLabel !== null ? h('p', { class: 'hint' }, o.example(o.firstLabel)) : null);
  return form<DuplicateN>({
    title: 'Duplicate N', sentence: '고른 부품을 한 방향으로 여러 벌 복제합니다. 사본이 다른 연결에 닿으면 복제하지 않습니다.', ok: 'OK',
    fields: [{ name: 'Count', el: count, focus: count }, { name: 'Direction', el: dir }, { name: 'Spacing (px)', el: spacing }],
    extra,
    read: () => {
      const n = Number(count.value);
      if (!Number.isInteger(n) || n < 1 || n > 64) return '복제할 수는 1–64 사이의 정수입니다.';
      const s = Number(spacing.value);
      if (!Number.isInteger(s) || s < 10 || s > 2000) return '간격은 10–2000 사이의 정수(px)입니다.';
      return { count: n, direction: dir.value as DuplicateN['direction'], spacing: Math.round(s / 10) * 10, number: number.checked };
    },
    submit,
  });
}

// v1 Arrange.nextLabel: the k-th copy's label (a number at the end goes on, keeping its digits: R07 → R08;
// none: the number after the name).
export function nextLabel(label: string, k: number): string {
  if (!label) return label;
  const m = /^(.*?)(\d+)$/.exec(label);
  if (!m) return `${label}${k}`;
  const n = String(Number(m[2]) + k).padStart(m[2].length, '0');
  return m[1] + n;
}

// v1 Arrange.defaultSpacing: the group's size in that direction + 10, up to the next 10.
export function defaultSpacing(box: { w: number; h: number }, direction: DuplicateN['direction']): number {
  const size = direction === 'left' || direction === 'right' ? box.w : box.h;
  return Math.ceil((size + 10) / 10) * 10;
}
