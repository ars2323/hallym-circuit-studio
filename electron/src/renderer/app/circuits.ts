/* The Circuits panel (left, upper; N-11, D-153): the file's circuits in
   their order, the main one marked with a house, and what the original's
   Project menu and explorer do to a circuit (I-107..I-109, I-113, I-147;
   v1 P-04, S-08, P-05).

     click            its tab (the circuit on show)
     double click     the same (the original's explorer: open the layout)
     right click      Edit Circuit Layout, Edit Circuit Appearance, Rename…,
                      Set As Main Circuit, Port Order…, Auto Appearance,
                      Move Up, Move Down, Remove Circuit
     Delete, Backspace  Remove Circuit (the original: Backspace; Delete too,
                      I-108 정함)
     drag onto another  moves it there (the original's explorer drag, I-107)
     the head row     Add Circuit…, Import…, Libraries ▾ (Load Library ›
                      Built-in…, Logisim…, JAR…; Unload Libraries…)
     Simulation Tree  below: main and its instances (I-118, v1 View
                      Simulation Tree); a click goes into that instance

   It only says what was asked; the engine does it (edit.*) and the file's
   new structure comes back as file.changed. */

import type { CircuitRef } from '../../main/protocol.ts';
import type { SimNode } from './logic/circuits.ts';
import { type MenuEntry, SEPARATOR, showMenu } from '../canvas/overlays/menu.ts';
import { h, icon } from '../shared/dom.ts';
import type { NoticeHost } from '../shared/notice.ts';

export type CircuitCommand = 'open' | 'layout' | 'appearance' | 'rename' | 'main' | 'portOrder' | 'autoAppearance' | 'up' | 'down' | 'remove';
export type FileCommand = 'add' | 'import' | 'loadBuiltin' | 'loadCirc' | 'loadJar' | 'unload';

export interface CircuitsState {
  fileId: string;
  circuits: CircuitRef[];
  main: string;
  shown: string;                // the circuit on show
  appearance: boolean;          // its appearance (not its layout) is on show
  editable: boolean;            // not a read-only file
  tree?: SimNode[];             // the Simulation Tree (main and its instances), when known
  inside?: string[];            // the instance path on show from main (null: main's own tab is not on show)
}

export interface CircuitsPanel {
  set(state: CircuitsState | null): void;
  root: HTMLElement;
}

export const CIRCUIT_MIME = 'application/x-hcs-circuit';

export function circuitsPanel(o: {
  host: NoticeHost;
  circuit(cmd: CircuitCommand, circuitId: string): void;
  file(cmd: FileCommand): void;
  moveTo(circuitId: string, to: number): void;
  enter(node: SimNode): void;
}): CircuitsPanel {
  let state: CircuitsState | null = null;
  const add = h('button', { type: 'button', class: 'hbtn', title: 'Add Circuit… (Project › Add Circuit)' }, icon('plus'), 'Add Circuit');
  const imp = h('button', { type: 'button', class: 'hbtn', title: 'Import Subcircuits… (다른 .circ 파일의 회로를 이 파일에 복사합니다)' }, icon('import'), 'Import');
  const libs = h('button', { type: 'button', class: 'hbtn', title: 'Load Library, Unload Libraries', 'aria-haspopup': 'menu' }, icon('library'), 'Libraries');
  add.addEventListener('click', () => o.file('add'));
  imp.addEventListener('click', () => o.file('import'));
  libs.addEventListener('click', () => {
    const r = libs.getBoundingClientRect();
    showMenu([
      { label: 'Load Library', items: [
        { label: 'Built-in Library…', run: () => o.file('loadBuiltin') },
        { label: 'Logisim Library…', run: () => o.file('loadCirc') },
        { label: 'JAR Library…', run: () => o.file('loadJar') },
      ] },
      { label: 'Unload Libraries…', run: () => o.file('unload') },
    ], r.left, r.bottom + 2);
  });
  const bar = h('div', { class: 'circbar' }, add, imp, libs);
  const list = h('ul', { class: 'list circlist', 'aria-label': 'Circuits' });
  const tree = h('ul', { class: 'simtree', 'aria-label': 'Simulation Tree' });   // the .list look, not a .list (the circuit list is that)
  const treeHead = h('h3', { class: 'simhead' }, 'Simulation Tree');
  const root = h('div', { class: 'circpanel' }, bar, list, treeHead, tree);

  function menuFor(c: CircuitRef, x: number, y: number): void {
    const st = state!;
    const i = st.circuits.findIndex((k) => k.circuitId === c.circuitId);
    const ed = st.editable;
    const entries: MenuEntry[] = [
      { label: 'Edit Circuit Layout', run: () => o.circuit('layout', c.circuitId) },
      { label: 'Edit Circuit Appearance', run: () => o.circuit('appearance', c.circuitId) },
      SEPARATOR,
      { label: 'Rename…', disabled: !ed, run: () => o.circuit('rename', c.circuitId) },
      { label: 'Set As Main Circuit', disabled: !ed || c.circuitId === st.main, run: () => o.circuit('main', c.circuitId) },
      SEPARATOR,
      { label: 'Port Order…', disabled: !ed, run: () => o.circuit('portOrder', c.circuitId) },
      { label: 'Auto Appearance', disabled: !ed, run: () => o.circuit('autoAppearance', c.circuitId) },
      SEPARATOR,
      { label: 'Move Circuit Up', disabled: !ed || i <= 0, run: () => o.circuit('up', c.circuitId) },
      { label: 'Move Circuit Down', disabled: !ed || i >= st.circuits.length - 1, run: () => o.circuit('down', c.circuitId) },
      { label: 'Remove Circuit', disabled: !ed || st.circuits.length <= 1, run: () => o.circuit('remove', c.circuitId) },
    ];
    showMenu(entries, x, y);
  }

  function render(): void {
    const st = state;
    if (!st) { o.host.fill(); return; }
    if (o.host.root.firstChild !== root) o.host.fill(root);
    for (const b of [add, imp, libs]) b.disabled = !st.editable;
    list.replaceChildren(...st.circuits.map((c, i) => {
      const isMain = c.circuitId === st.main;
      const b = h('button', { type: 'button', 'aria-current': c.circuitId === st.shown ? 'true' : undefined, title: `${c.name}${isMain ? ' (main circuit)' : ''} — right click: Rename, Port Order, Appearance…`, draggable: String(st.editable), 'data-circuit': c.circuitId },
        h('span', { class: 'mono' }, c.name),
        isMain ? h('span', { class: 'mainmark', role: 'img', 'aria-label': 'Main circuit', title: 'Main circuit' }, icon('house')) : null,
        c.circuitId === st.shown && st.appearance ? h('span', { class: 'tag' }, 'Appearance') : null);
      b.addEventListener('click', () => o.circuit('open', c.circuitId));
      b.addEventListener('dblclick', () => o.circuit('layout', c.circuitId));
      b.addEventListener('contextmenu', (e) => { e.preventDefault(); menuFor(c, e.clientX, e.clientY); });
      b.addEventListener('keydown', (e) => {
        if ((e.key === 'Delete' || e.key === 'Backspace') && st.editable) { e.preventDefault(); o.circuit('remove', c.circuitId); }
        else if (e.key === 'ArrowDown') { e.preventDefault(); (list.querySelectorAll('button')[i + 1] as HTMLButtonElement | undefined)?.focus(); }
        else if (e.key === 'ArrowUp') { e.preventDefault(); (list.querySelectorAll('button')[i - 1] as HTMLButtonElement | undefined)?.focus(); }
        else if (e.key === 'ContextMenu' || (e.shiftKey && e.key === 'F10')) { e.preventDefault(); const r = b.getBoundingClientRect(); menuFor(c, r.left + 12, r.bottom); }
      });
      // a circuit dragged onto another goes to its place (the original explorer's move)
      b.addEventListener('dragstart', (e) => {
        e.dataTransfer?.setData(CIRCUIT_MIME, JSON.stringify({ fileId: st.fileId, circuitId: c.circuitId, name: c.name }));
        if (e.dataTransfer) e.dataTransfer.effectAllowed = 'copyMove';
      });
      b.addEventListener('dragover', (e) => { if (e.dataTransfer?.types.includes(CIRCUIT_MIME)) { e.preventDefault(); b.classList.add('dropat'); } });
      b.addEventListener('dragleave', () => b.classList.remove('dropat'));
      b.addEventListener('drop', (e) => {
        b.classList.remove('dropat');
        const raw = e.dataTransfer?.getData(CIRCUIT_MIME);
        if (!raw) return;
        e.preventDefault();
        const from = JSON.parse(raw) as { fileId: string; circuitId: string };
        if (from.fileId === st.fileId && from.circuitId !== c.circuitId) o.moveTo(from.circuitId, i);
      });
      return h('li', { class: c.circuitId === st.shown ? 'on' : undefined }, b);
    }));
    const nodes = st.tree ?? [];
    treeHead.hidden = nodes.length === 0;
    const here = st.inside ? st.inside.join('/') : null;
    tree.replaceChildren(...nodes.map((n) => {
      const on = here !== null && n.ids.join('/') === here;
      const b = h('button', { type: 'button', 'aria-current': on ? 'true' : undefined, style: `padding-left:${10 + n.depth * 14}px`,
        title: n.depth ? `${[st.circuits.find((c) => c.circuitId === st.main)?.name ?? 'main', ...n.names].join(' › ')}: 그 인스턴스 안의 값을 봅니다` : `${n.text}: main 회로` },
      h('span', { class: 'mono' }, n.text));
      b.addEventListener('click', () => o.enter(n));
      return h('li', { class: on ? 'on' : undefined }, b);
    }));
  }

  return { root, set: (next) => { state = next; render(); } };
}
