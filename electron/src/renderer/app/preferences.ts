/* Preferences (the title bar's gear, File › Preferences…, Window ›
   Preferences…; D-158): Hallym MIPS's Settings dialog made this app's.
   Everything in it is for this run only -- lab PCs are shared, and every
   start is from the defaults (the lab-PC rule, N-19, D-152; RUN_ONLY at the
   top).  Two tabs:

     General    the settings the window has (logic/run-settings.ts): the
                clock's speed, Show Bus Widths, the wire colours (Values /
                Groups), Bus Values, Active Path, Signal Flow's choices, and
                the panels' sizes back to their defaults
     Keyboard   every key (v1's shortcut table, I-43) -- the fourteen that
                can change (logic/keys.ts, v1 E-09, I-183: Change… then the
                new key; Reset; Reset All) and those that cannot

   Names are English (Preferences, Clock Speed, Change…); what a setting
   does is a Korean sentence under it.  The same controls as elsewhere
   (the toolbar's speed, the Wire Colors panel) show the same values: the
   caller's getters are the one truth. */

import type { BusMode, FlowSpeed } from '../canvas/overlays/logic.ts';
import { BUS_MODE_NAMES, BUS_MODES, FLOW_SPEED_NAMES } from '../canvas/overlays/logic.ts';
import { codeText, h } from '../shared/dom.ts';
import { COMMANDS, fixedTable, isChanged, keyText, onKeysChanged, pressStroke, resetAll, resetKey, setKey } from './logic/keys.ts';
import { RUN_ONLY } from './logic/run-settings.ts';

export interface PrefsHost {
  frequencies: readonly (readonly [string, number])[];
  hz(): number;
  setHz(hz: number): void;
  showGrid(): boolean;
  setShowGrid(on: boolean): void;
  busWidths(): boolean;
  setBusWidths(on: boolean): void;
  groups(): boolean;
  setGroups(on: boolean): void;
  busMode(): BusMode;
  setBusMode(m: BusMode): void;
  activePath(): boolean;
  setActivePath(on: boolean): void;
  flowOnClick(): boolean;
  setFlowOnClick(on: boolean): void;
  flowSpeed(): FlowSpeed;
  setFlowSpeed(s: FlowSpeed): void;
  reduceMotion(): boolean;
  setReduceMotion(on: boolean): void;
  resetPanels(): void;
  about(): void;
}

export const PREFS_NOTE = `${RUN_ONLY}. 다음에 켜면 모두 기본값으로 돌아옵니다.`;

export interface Preferences {
  root: HTMLDialogElement;
  open(tab?: 'general' | 'keyboard'): void;
}

export function preferences(host: PrefsHost): Preferences {
  const dialog = h('dialog', { class: 'modal prefs', 'aria-label': 'Preferences' });
  let tab: 'general' | 'keyboard' = 'general';
  let capturing: string | null = null;     // the command waiting for its new key
  let refused: { id: string; why: string } | null = null;

  const row = (name: string, control: Node, say: string) =>
    h('div', { class: 'prefrow' }, h('span', { class: 'prefname' }, name), h('span', { class: 'grow' }), control,
      h('small', { class: 'hint' }, codeText(say)));
  const check = (on: boolean, label: string, set: (v: boolean) => void) => {
    const box = h('input', { type: 'checkbox', checked: on, 'aria-label': label }) as HTMLInputElement;
    box.addEventListener('change', () => { set(box.checked); render(); });
    return box;
  };
  const seg = <T extends string>(label: string, values: readonly T[], names: Record<T, string>, now: T, set: (v: T) => void) =>
    h('span', { class: 'seg', role: 'radiogroup', 'aria-label': label }, ...values.map((v) => {
      const b = h('button', { type: 'button', role: 'radio', class: v === now ? 'on' : undefined, 'aria-checked': String(v === now) }, names[v]);
      b.addEventListener('click', () => { set(v); render(); });
      return b;
    }));

  const general = () => {
    const speed = h('select', { 'aria-label': 'Clock Speed' },
      ...host.frequencies.map(([label, hz]) => h('option', { value: String(hz), selected: hz === host.hz() }, label))) as HTMLSelectElement;
    speed.addEventListener('change', () => { host.setHz(Number(speed.value)); render(); });
    const bus = h('select', { 'aria-label': 'Bus Values' },
      ...BUS_MODES.map((m) => h('option', { value: m, selected: m === host.busMode() }, BUS_MODE_NAMES[m]))) as HTMLSelectElement;
    bus.addEventListener('change', () => { host.setBusMode(bus.value as BusMode); render(); });
    const panels = h('button', { class: 'btn small', type: 'button' }, 'Reset Panel Sizes');
    panels.addEventListener('click', () => host.resetPanels());
    return h('div', { class: 'preftab' },
      h('h4', {}, 'Simulation'),
      row('Clock Speed', speed, '클럭이 도는 빠르기입니다(Run, `F5`). 도구 모음의 속도 칸과 같은 값입니다.'),
      h('h4', {}, 'Canvas'),
      row('Show Grid', check(host.showGrid(), 'Show Grid', host.setShowGrid), 'Canvas 바탕에 점 격자를 보입니다. 상태 표시줄의 배율 메뉴에도 있습니다.'),
      row('Show Bus Widths', check(host.busWidths(), 'Show Bus Widths', host.setBusWidths), '버스(여러 비트 선) 옆에 비트 수를 보입니다.'),
      row('Colors', seg('Colors', ['values', 'groups'] as const, { values: 'Values', groups: 'Groups' }, host.groups() ? 'groups' : 'values', (v) => host.setGroups(v === 'groups')),
        '선 색은 늘 값을 뜻합니다. Groups 쪽은 신호 그룹이 있는 선에 그룹 색 테두리를 더합니다.'),
      row('Bus Values', bus, '시뮬레이션 중 버스마다 지금 값을 칩으로 보입니다.'),
      row('Active Path', check(host.activePath(), 'Active Path', host.setActivePath), 'Cycle View 탭이 보이는 동안 MUX 부품이 고른 입력을 진하게 보입니다.'),
      h('h4', {}, 'Signal Flow'),
      row('Signal Flow on Click', check(host.flowOnClick(), 'Signal Flow on Click', host.setFlowOnClick), 'Edit 도구로 부품이나 선을 누르면 신호가 가는 길을 흐름으로 보입니다.'),
      row('Flow Speed', seg('Flow Speed', ['slow', 'normal', 'fast'] as const, FLOW_SPEED_NAMES, host.flowSpeed(), host.setFlowSpeed), '흐름이 움직이는 빠르기입니다.'),
      row('Reduce Motion', check(host.reduceMotion(), 'Reduce Motion', host.setReduceMotion), '흐름을 움직이지 않고 화살표와 순서 번호로 보입니다.'),
      h('h4', {}, 'Window'),
      row('Panels', panels, '끌어서 바꾼 칸의 크기와 접은 칸을 처음대로 돌립니다.'));
  };

  const keyboard = () => {
    const rows = COMMANDS.map((c) => {
      const waiting = capturing === c.id;
      const change = h('button', { class: 'hbtn', type: 'button', 'aria-label': `Change ${c.name}` }, waiting ? 'Cancel' : 'Change…');
      change.addEventListener('click', () => { capturing = waiting ? null : c.id; refused = null; render(); });
      const reset = h('button', { class: 'hbtn', type: 'button', disabled: !isChanged(c.id), 'aria-label': `Reset ${c.name}` }, 'Reset');
      reset.addEventListener('click', () => { resetKey(c.id); refused = null; render(); });
      return h('tr', { class: `${waiting ? 'waiting' : ''}${isChanged(c.id) ? ' changed' : ''}`.trim(), 'data-command': c.id },
        h('td', { class: 'kname' }, c.name, h('small', { class: 'hint' }, codeText(c.say)),
          refused?.id === c.id ? h('small', { class: 'hint err' }, refused.why) : null),
        h('td', { class: 'kkey' }, waiting ? h('span', { class: 'kwait' }, '새 키를 누르세요 · Esc: Cancel') : h('kbd', {}, keyText(c.id))),
        h('td', { class: 'kbtns' }, change, reset));
    });
    const fixedRows = fixedTable().map((f) =>
      h('tr', { class: 'fixed' }, h('td', { class: 'kname' }, f.name), h('td', { class: 'kkey' }, h('kbd', {}, f.keys)), h('td', {})));
    const all = h('button', { class: 'btn small', type: 'button', disabled: !COMMANDS.some((c) => isChanged(c.id)) }, 'Reset All');
    all.addEventListener('click', () => { resetAll(); refused = null; render(); });
    return h('div', { class: 'preftab keys' },
      h('table', { class: 'keytable' }, h('tbody', {}, ...rows)),
      h('div', { class: 'row end' }, all),
      h('h4', {}, 'Keys That Do Not Change'),
      h('table', { class: 'keytable' }, h('tbody', {}, ...fixedRows)));
  };

  const render = () => {
    const tabs = h('div', { class: 'tabs', role: 'tablist' }, ...(['general', 'keyboard'] as const).map((t) => {
      const b = h('button', { class: `tab${t === tab ? ' on' : ''}`, type: 'button', role: 'tab', 'aria-selected': String(t === tab) }, t === 'general' ? 'General' : 'Keyboard');
      b.addEventListener('click', () => { tab = t; capturing = null; refused = null; render(); });
      return b;
    }));
    const close = h('button', { class: 'btn primary', type: 'button' }, 'Close');
    close.addEventListener('click', () => dialog.close());
    const aboutButton = h('button', { class: 'linkbtn', type: 'button' }, 'About · Licenses');
    aboutButton.addEventListener('click', () => { dialog.close(); host.about(); });
    dialog.replaceChildren(
      h('h2', {}, 'Preferences'),
      h('p', { class: 'prefnote' }, PREFS_NOTE),
      tabs,
      h('div', { class: 'prefbody' }, tab === 'general' ? general() : keyboard()),
      h('div', { class: 'row' }, aboutButton, h('span', { class: 'grow' }), close));
    if (capturing) document.body.dataset.keyCapture = capturing; else delete document.body.dataset.keyCapture;
  };

  // The new key: the next press while a command waits (modifiers alone are not a key; Esc cancels, the dialog stays).
  // On the document, first: wherever the focus is while the dialog is up.
  document.addEventListener('keydown', (e) => {
    if (!capturing || !dialog.open) return;
    e.preventDefault();
    e.stopPropagation();
    if (e.key === 'Escape' && !e.ctrlKey && !e.altKey && !e.shiftKey) { capturing = null; refused = null; render(); return; }
    const k = pressStroke(e);
    if (!k) return;
    const why = setKey(capturing, k);
    refused = why ? { id: capturing, why } : null;
    if (!why) capturing = null;
    render();
  }, true);
  dialog.addEventListener('close', () => { capturing = null; refused = null; delete document.body.dataset.keyCapture; });
  onKeysChanged(() => { if (dialog.open) render(); });

  return {
    root: dialog,
    open: (t = 'general') => { tab = t; capturing = null; refused = null; render(); dialog.showModal(); },
  };
}
