/* The Registers panel (derived from Hallym MIPS v2.5.0
   electron/src/renderer/app/panels/registers.ts).  One DOM row per
   register, made once for a set of registers; an update touches only the
   cells whose text changed and the rows whose highlight changed.

   What to look at first, as in Hallym MIPS:
     - the register that just changed: a yellow row with a bar, flashed
       once when it changes, and its own "Changed" tag wherever the
       panel's width has the room (columns.ts badgeStyle).  After a step
       the list scrolls to it, unless the student is scrolling it
       (dom.ts userScrolls);
     - its value in hexadecimal (the strongest column); decimal quieter,
       binary quietest;
     - groups as bands, each with the registers it holds;
     - zero registers dimmed.

   Hex, Dec and Bin together (v1 C-05 and Hallym MIPS): at a narrow width
   the panel gives up its margins and a pixel of font before a column,
   then Dec, and Bin last (columns.ts).  A column given up comes back from
   the head ("+ Bin").

   Changed from the upstream file: the rows come from the circuit (the
   engine finds the student's registers: a marked register file, or every
   Register by its label; PC in Special), so the set of rows can change and
   is rebuilt then; a register's own name in the circuit stands quieter
   beside the shown one ("$sp  $29"); a row can be right-clicked (Mark as
   PC); no CP0 fold (a circuit has no coprocessor registers); the values
   and the groups are given, not read from a simulator. */

import { badgeStyle, fit, needed, styles, type Column, type Fit } from './columns.ts';
import { code, h, monoCh, userScrolls } from './dom.ts';
import { columnButton } from './ui.ts';

// One register as the panel shows it (the app computes the texts).
export interface RegisterLine {
  key: string;
  name: string;
  alias: string;        // '' none
  group: string;
  hex: string;
  dec: string;
  bin: string[];        // four bits to a group
  changed: boolean;
  zero: boolean;
  markedPc?: boolean;
}

interface Row { el: HTMLElement; hex: HTMLElement; dec: HTMLElement; bin: HTMLElement; last: string; flags: string }

const TAG: Column = { key: 'tag', px: 56 }; // the badge: "Changed" at 10.5 px, 6 px either side
const DROPS = [['dec'], ['bin']];
const NAMES: Record<string, string> = { dec: 'Dec', bin: 'Bin' };
// Padding and border (left and right together) and the gap between columns.
const NORMAL = { pad: 22, gap: 10 };
const TIGHT = { pad: 14, gap: 6 };

export class RegisterPanel {
  readonly root: HTMLElement;
  private readonly list: HTMLElement;
  private readonly rhead: HTMLElement;
  private readonly rows = new Map<string, Row>();
  private order: string[] = [];
  private shape = '';
  private readonly forced = new Set<string>();
  private readonly scrolledByStudent: () => boolean;
  private columnsNow: Column[] = [{ key: 'rn', ch: 7 }, { key: 'hex', ch: 10.5 }, { key: 'dec', ch: 10.5 }, { key: 'bin', ch: 28.5 }];
  columns: Fit | null = null;
  // The head's controls for this panel ("+ Bin"): the host puts them in its head.
  onToggles: (buttons: HTMLElement[]) => void = () => {};
  // A row right-clicked: its key and where.
  onRowMenu: (key: string, x: number, y: number) => void = () => {};

  constructor() {
    this.rhead = h('div', { class: 'rhead' }, h('span', { class: 'rn' }, 'Name'), h('span', { class: 'hex strong' }, 'Hex'),
      h('span', { class: 'dec right' }, 'Dec'), h('span', { class: 'bin' }, 'Bin'));
    this.list = h('div', { class: 'pbody regs-list' }, this.rhead);
    this.root = h('section', { class: 'regs', 'aria-label': 'Registers' }, this.list);
    this.scrolledByStudent = userScrolls(this.list);
    this.list.addEventListener('contextmenu', (e) => {
      const row = (e.target as HTMLElement).closest('.rrow') as HTMLElement | null;
      if (!row?.dataset.reg) return;
      e.preventDefault();
      this.onRowMenu(row.dataset.reg, e.clientX, e.clientY);
    });
    new ResizeObserver(() => this.fit()).observe(this.list);
  }

  // Builds the rows for a new set of registers (their names and groups).
  private build(lines: RegisterLine[], groups: { title: string; span: string; keys: string[] }[]): void {
    this.rows.clear();
    this.order = lines.map((l) => l.key);
    const byKey = new Map(lines.map((l) => [l.key, l]));
    const out: HTMLElement[] = [this.rhead];
    for (const g of groups) {
      out.push(h('div', { class: 'rgroup' }, h('span', { class: 'gname' }, g.title), code(g.span, 'gspan')));
      for (const key of g.keys) {
        const l = byKey.get(key)!;
        const hex = code('', 'hex');
        const dec = code('', 'dec');
        const bin = code('', 'bin');
        const el = h('div', { class: 'rrow', 'data-reg': key, title: l.alias ? `${l.name} (${l.alias})` : l.name },
          h('span', { class: 'rn mono' }, l.name, l.alias ? h('span', { class: 'alias' }, l.alias) : null,
            l.markedPc ? h('span', { class: 'pcmark', title: 'Marked as PC' }, 'PC') : null),
          hex, dec, bin, h('span', { class: 'tag' }, 'Changed'));
        out.push(el);
        this.rows.set(key, { el, hex, dec, bin, last: '', flags: '' });
      }
    }
    this.list.replaceChildren(...out);
    // The name column: as wide as the longest name with its alias, within 7..20 characters.
    const longest = lines.reduce((m, l) => Math.max(m, l.name.length + (l.alias ? l.alias.length + 1 : 0)), 5);
    this.columnsNow = [{ key: 'rn', ch: Math.max(7, Math.min(20, longest + 1)) }, ...this.columnsNow.slice(1)];
    this.fit();
  }

  // Columns and style for the width the panel has now.
  fit(): void {
    const width = this.list.clientWidth;
    if (!width) return;
    const fontPx = parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--fs')) || 13;
    const ch = monoCh(fontPx);
    const all = styles(NORMAL, TIGHT, fontPx);
    const f = fit(width, this.columnsNow, DROPS, this.forced, ch, all);
    // The "Changed" tag wherever it fits beside the columns the width keeps.
    const withTag = badgeStyle(width, this.columnsNow, f, TAG, ch, all);
    const tag = withTag !== null;
    const cols = this.columnsNow.filter((c) => !f.hidden.has(c.key));
    const template = cols.map((c) => `${c.ch}ch`).join(' ') + (tag ? ` ${TAG.px}px` : '') + ' minmax(0, 1fr)';
    this.root.style.setProperty('--rcols', template);
    this.root.dataset.style = (withTag ?? f.style).name;
    for (const key of ['dec', 'bin']) this.root.classList.toggle(`hide-${key}`, f.hidden.has(key));
    this.root.classList.toggle('hide-tag', !tag);
    this.root.classList.toggle('overflow', f.overflow);
    this.columns = f;
    // The columns the width takes away, to turn back on.
    const auto = fit(width, this.columnsNow, DROPS, new Set(), ch, all).hidden;
    this.onToggles([...auto].map((key) => {
      const on = this.forced.has(key);
      return columnButton(NAMES[key], on, () => {
        if (on) this.forced.delete(key); else this.forced.add(key);
        this.fit();
      });
    }));
  }

  // The width the panel wants: all of Hex, Dec and Bin with tight margins.
  leastWidth(fontPx: number): number {
    const [, tight] = styles(NORMAL, TIGHT, fontPx);
    return Math.ceil(needed(this.columnsNow, tight, monoCh(fontPx)) + 14);
  }

  update(lines: RegisterLine[], groups: { title: string; span: string; keys: string[] }[], shape: string): void {
    if (shape !== this.shape) {
      this.shape = shape;
      this.build(lines, groups);
    }
    for (const l of lines) {
      const row = this.rows.get(l.key);
      if (!row) continue;
      const text = `${l.hex}|${l.dec}`;
      const moved = text !== row.last;
      if (moved) {
        row.hex.textContent = l.hex;
        row.dec.textContent = l.dec;
        // Four bits to a group, the groups a few pixels apart (not a space).
        row.bin.replaceChildren(...l.bin.map((n) => h('span', {}, n)));
        row.last = text;
      }
      const flags = `${l.changed ? 'c' : ''}${l.zero ? 'z' : ''}`;
      if (flags !== row.flags) {
        row.el.classList.toggle('chg', l.changed);
        row.el.classList.toggle('zero', l.zero && !l.changed);
        row.flags = flags;
      }
      if (l.changed && moved) { // flash again, even if it was changed at the last step too
        row.el.classList.remove('flash');
        void row.el.offsetWidth;
        row.el.classList.add('flash');
      }
    }
    const first = this.order.find((k) => lines.find((l) => l.key === k)?.changed && k !== 'PC');
    if (first && !this.scrolledByStudent()) this.reveal(this.rows.get(first)!.el);
  }

  // Scrolls as little as possible to have `row` in view, below the sticky
  // column head, with a row to spare on either side.
  private reveal(row: HTMLElement): void {
    if (!row.offsetParent) return;
    const list = this.list;
    const margin = row.offsetHeight;
    const top = row.offsetTop - this.rhead.offsetHeight - margin;
    const bottom = row.offsetTop + row.offsetHeight + margin;
    if (top < list.scrollTop) list.scrollTop = Math.max(0, top);
    else if (bottom > list.scrollTop + list.clientHeight) list.scrollTop = bottom - list.clientHeight;
  }
}
