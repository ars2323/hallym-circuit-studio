/* The dialogs of circuits, appearances and libraries (N-11, D-153): the
   window's own dialog (the ask dialog's look, shared/ask.ts), no character
   -- they are questions and lists, and some are about connections that
   break.  The backdrop darkens the page; a click outside does nothing; Esc
   is Cancel.  Titles are the commands' names (English), sentences Korean,
   buttons names (D-135 14).

     askName          Add Circuit… and Rename…: the name, the original's
                      checks under the field (Enter: OK)
     portOrder        Port Order…: a column a side, ports dragged or moved
                      with ▲ ▼ (v1 PortOrderDialog, P-04)
     confirmImpact    Apply / Cancel before connections break (v1: up to
                      eight places, Cancel first)
     chooseCircuits   Import Subcircuits…: the file's circuits to take (the
                      ones they use come along) -- then the plan
     chooseLibraries  Load Library › Built-in Library… and Unload Libraries…
     confirmSaveCuts  Save Anyway / Cancel before a save breaks other files */

import type { Impact, ImportPeek, ImportPlan, PortsInfo, SaveCut, Side } from '../../main/protocol.ts';
import { code, h, icon, prose } from '../shared/dom.ts';
import { impactPlaces, impactSentence, moved, planLines, saveCutLines, saveCutSentence } from './logic/circuits.ts';

interface Button { label: string; primary?: boolean; danger?: boolean; value: unknown }

// A modal of the window's own look; resolves with the pressed button's value (Esc: `cancel`).
function modal<T>(o: { title: string; label?: string; cls?: string; content: (HTMLElement | null)[]; buttons: Button[]; cancel: T; focus?: 'first' | 'last' | HTMLElement; onKey?: (e: KeyboardEvent, done: (v: T) => void) => void }): Promise<T> {
  return new Promise((answer) => {
    let result: T = o.cancel;
    const buttons = o.buttons.map((b) => {
      const el = h('button', { class: `btn${b.primary ? ' primary' : ''}${b.danger ? ' danger' : ''}`, type: 'button' }, b.label);
      el.addEventListener('click', () => { result = b.value as T; dialog.close(); });
      return el;
    });
    const dialog = h('dialog', { class: `modal ask plain ${o.cls ?? ''}`.trim(), 'aria-label': o.label ?? o.title },
      h('div', { class: 'askbody' }, h('div', { class: 'asktext' }, h('h2', {}, o.title), ...o.content, h('div', { class: 'row end' }, ...buttons))));
    const done = (v: T) => { result = v; dialog.close(); };
    dialog.addEventListener('keydown', (e) => o.onKey?.(e, done));
    dialog.addEventListener('close', () => {
      dialog.remove();
      document.body.classList.remove('dialog-open');
      answer(result);
    });
    document.body.append(dialog);
    document.body.classList.add('dialog-open');
    dialog.showModal();
    const f = o.focus === 'last' ? buttons[buttons.length - 1] : o.focus === 'first' || o.focus === undefined ? buttons[0] : o.focus;
    f?.focus();
  });
}

// ---- a circuit's name ----

export function askName(o: { title: string; sentence: string; value: string; ok: string; check: (name: string) => string | null }): Promise<string | null> {
  return new Promise((answer) => {
    const input = h('input', { type: 'text', class: 'field mono', 'aria-label': 'Name', value: o.value, autocomplete: 'off', spellcheck: 'false' }) as HTMLInputElement;
    const why = h('span', { class: 'hint err', role: 'alert' });
    const ok = h('button', { class: 'btn primary', type: 'button' }, o.ok);
    const cancel = h('button', { class: 'btn', type: 'button' }, 'Cancel');
    const dialog = h('dialog', { class: 'modal ask plain namedialog', 'aria-label': o.title },
      h('div', { class: 'askbody' }, h('div', { class: 'asktext' }, h('h2', {}, o.title), h('p', {}, o.sentence),
        h('label', { class: 'cyclesfield' }, h('span', { class: 'fieldname' }, 'Name'), input), why,
        h('div', { class: 'row end' }, cancel, ok))));
    let result: string | null = null;
    const submit = () => {
      const problem = o.check(input.value);
      if (problem) { why.textContent = problem; input.setAttribute('aria-invalid', 'true'); input.focus(); input.select(); return; }
      result = input.value.trim();
      dialog.close();
    };
    ok.addEventListener('click', submit);
    cancel.addEventListener('click', () => dialog.close());
    input.addEventListener('keydown', (e) => { if (e.key === 'Enter' && !e.isComposing) { e.preventDefault(); submit(); } });
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

// ---- Port Order… ----

export const SIDE_NAMES: Record<Side, string> = { west: 'West', east: 'East', north: 'North', south: 'South' };

export function portOrder(ports: PortsInfo): Promise<Partial<Record<Side, number[]>> | null> {
  const sides = (Object.keys(SIDE_NAMES) as Side[]).filter((s) => ports.sides[s].length > 0);
  // each side's list: the numbers of the ports in the order now (the engine takes numbers or names)
  const order = new Map<Side, number[]>(sides.map((s) => [s, ports.sides[s].map((_, i) => i)]));
  const cols = h('div', { class: 'portcols' });
  const draw = () => {
    cols.replaceChildren(...sides.map((side) => {
      const list = h('ol', { class: 'portlist', 'aria-label': `${SIDE_NAMES[side]} ports` });
      const items = order.get(side)!;
      items.forEach((idx, k) => {
        const p = ports.sides[side][idx];
        const up = h('button', { type: 'button', class: 'iconbtn small', title: 'Up', 'aria-label': `Move ${p.name || '(no name)'} up`, disabled: k === 0 }, icon('arrow-up'));
        const down = h('button', { type: 'button', class: 'iconbtn small', title: 'Down', 'aria-label': `Move ${p.name || '(no name)'} down`, disabled: k === items.length - 1 }, icon('arrow-down'));
        up.addEventListener('click', () => { order.set(side, moved(items, k, k - 1)); draw(); focusRow(side, k - 1, 'up'); });
        down.addEventListener('click', () => { order.set(side, moved(items, k, k + 1)); draw(); focusRow(side, k + 1, 'down'); });
        const li = h('li', { class: 'portrow', draggable: 'true', 'data-side': side, 'data-k': String(k) },
          h('span', { class: 'grip', 'aria-hidden': 'true' }, '⋮⋮'), p.name ? code(p.name) : h('span', { class: 'dim' }, '(no name)'),
          h('span', { class: 'pmeta' }, `${p.input ? 'in' : 'out'} · ${p.width}`), h('span', { class: 'grow' }), up, down);
        li.addEventListener('dragstart', (e) => { e.dataTransfer?.setData('text/x-hcs-port', `${side}:${k}`); li.classList.add('dragging'); });
        li.addEventListener('dragend', () => li.classList.remove('dragging'));
        li.addEventListener('dragover', (e) => { if (e.dataTransfer?.types.includes('text/x-hcs-port')) { e.preventDefault(); li.classList.add('dropat'); } });
        li.addEventListener('dragleave', () => li.classList.remove('dropat'));
        li.addEventListener('drop', (e) => {
          e.preventDefault();
          const [fs, fk] = (e.dataTransfer?.getData('text/x-hcs-port') ?? '').split(':');
          if (fs !== side) return;   // a port stays on its side (its pin's facing decides the side)
          order.set(side, moved(items, Number(fk), k));
          draw();
        });
        list.append(li);
      });
      return h('section', { class: 'portcol' }, h('h3', {}, SIDE_NAMES[side]), list);
    }));
  };
  const focusRow = (side: Side, k: number, which: 'up' | 'down') => {
    const row = cols.querySelector(`li[data-side="${side}"][data-k="${k}"]`);
    const b = row?.querySelectorAll('button')[which === 'up' ? 0 : 1] as HTMLButtonElement | undefined;
    (b && !b.disabled ? b : (row?.querySelectorAll('button')[which === 'up' ? 1 : 0] as HTMLButtonElement | undefined))?.focus();
  };
  draw();
  if (sides.length === 0) {
    return modal({ title: `Port Order — ${ports.name}`, content: [h('p', {}, '이 회로에는 핀이 없어 순서를 바꿀 포트가 없습니다.')], buttons: [{ label: 'Close', primary: true, value: null }], cancel: null });
  }
  return modal<Partial<Record<Side, number[]>> | null>({
    title: 'Port Order', cls: 'portorder', label: `Port Order — ${ports.name}`,
    content: [h('p', {}, prose(`\`${ports.name}\` 회로의 포트 차례를 쪽마다(West, East …) 바꿉니다. 포트를 끌거나 위·아래 단추로 옮기고 Apply하면 그 차례로 Auto Appearance 모양을 만듭니다.`)), cols],
    buttons: [{ label: 'Cancel', value: null }, { label: 'Apply', primary: true, value: 'apply' }],
    cancel: null, focus: 'last',
  }).then((v) => (v === null ? null : Object.fromEntries([...order.entries()])));
}

// ---- before connections break ----

export function confirmImpact(title: string, impact: Impact): Promise<boolean> {
  return modal<boolean>({
    title, cls: 'impact',
    content: [h('p', {}, impactSentence(impact)), h('pre', { class: 'askdetail mono' }, impactPlaces(impact))],
    buttons: [{ label: 'Cancel', value: false }, { label: 'Apply', primary: true, danger: true, value: true }],
    cancel: false, focus: 'first',   // Cancel first (v1: the default button was Cancel)
  });
}

export function confirmSaveCuts(file: string, cuts: readonly SaveCut[]): Promise<boolean> {
  return modal<boolean>({
    title: '저장하면 다른 파일의 연결이 끊깁니다', cls: 'impact',
    content: [h('p', { class: 'askfile' }, 'File: ', code(file)), h('p', {}, saveCutSentence(cuts)), h('pre', { class: 'askdetail mono' }, saveCutLines(cuts))],
    buttons: [{ label: 'Cancel', value: false }, { label: 'Save Anyway', primary: true, danger: true, value: true }],
    cancel: false, focus: 'first',
  });
}

// ---- Import Subcircuits… ----

export function chooseCircuits(peek: ImportPeek): Promise<string[] | null> {
  const boxes = peek.circuits.map((c) => {
    const box = h('input', { type: 'checkbox', checked: true, 'aria-label': c.name }) as HTMLInputElement;
    return { name: c.name, box, row: h('label', { class: 'checkrow' }, box, code(c.name), c.uses.length ? h('span', { class: 'dim' }, `uses ${c.uses.join(', ')}`) : null) };
  });
  return modal<string[] | null>({
    title: 'Import Subcircuits', cls: 'importdialog',
    content: [h('p', { class: 'askfile' }, 'File: ', code(peek.name)), h('p', {}, '가져올 회로를 고르세요. 그 안에서 쓰는 서브회로는 함께 들어옵니다.'),
      h('div', { class: 'checklist' }, ...boxes.map((b) => b.row))],
    buttons: [{ label: 'Cancel', value: null }, { label: 'Next', primary: true, value: 'next' }],
    cancel: null, focus: 'last',
  }).then((v) => (v === null ? null : boxes.filter((b) => b.box.checked).map((b) => b.name)));
}

export function confirmPlan(file: string, plan: ImportPlan): Promise<boolean> {
  return modal<boolean>({
    title: 'Import Subcircuits', cls: 'importdialog',
    content: [h('p', { class: 'askfile' }, 'File: ', code(file)),
      h('p', {}, `회로 ${plan.order.length}개를 이 파일에 복사합니다(쓰이는 것 먼저). 이미 있는 이름에는 번호를 붙입니다.`),
      h('pre', { class: 'askdetail mono' }, planLines(plan))],
    buttons: [{ label: 'Cancel', value: false }, { label: 'Import', primary: true, value: true }],
    cancel: false, focus: 'last',
  });
}

// ---- Load / Unload Library ----

export function chooseLibraries(o: { title: string; sentence: string; items: { name: string; display: string; note?: string | null; disabled?: boolean }[]; ok: string }): Promise<string[] | null> {
  const boxes = o.items.map((it) => {
    const box = h('input', { type: 'checkbox', 'aria-label': it.display, disabled: it.disabled === true }) as HTMLInputElement;
    return { it, box, row: h('label', { class: `checkrow${it.disabled ? ' off' : ''}` }, box, h('span', {}, it.display), it.note ? h('span', { class: 'dim' }, it.note) : null) };
  });
  if (!boxes.length) {
    return modal<string[] | null>({ title: o.title, content: [h('p', {}, o.sentence)], buttons: [{ label: 'Close', primary: true, value: null }], cancel: null });
  }
  return modal<string[] | null>({
    title: o.title, cls: 'libdialog',
    content: [h('p', {}, o.sentence), h('div', { class: 'checklist' }, ...boxes.map((b) => b.row))],
    buttons: [{ label: 'Cancel', value: null }, { label: o.ok, primary: true, value: 'ok' }],
    cancel: null, focus: 'last',
  }).then((v) => (v === null ? null : boxes.filter((b) => b.box.checked).map((b) => b.it.name)));
}
