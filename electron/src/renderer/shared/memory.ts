/* The Memory panel: memory as a table, the way Hallym MIPS's Data tab shows
   it (derived from Hallym MIPS v2.3.0
   electron/src/renderer/app/panels/data.ts):

     Address | +0 | +4 | +8 | +C | ASCII

   with the labels drawn as a thin row above the line they belong to (a
   narrow panel has no room for another column):

   - One section per part of the memory -- user data, the stack -- each
     under a header row with its range; a section folds when its head is
     clicked.  The stack is drawn apart from the data (its own colour).
   - Up to four words on a 16-byte line, a run of zero words as ONE row
     that says how long it is.
   - Every address is written the same way: 0x10010000.
   - ASCII is grouped by word, four characters under each +0..+C;
     pointing at a word lights its four characters, and the other way
     round.
   - Labels on the line (by offset), and $sp / $fp / $gp when they point
     into it (their word is tinted too).
   - A narrow tab gives up margins, then a pixel of font, then the ASCII
     column (columns.ts); "+ ASCII" in the head brings it back.  The four
     words stay.

   Changed from the upstream file: the lines come made (the engine lays out
   the circuit's Data Memory: D-140, docs/engine-api.md record.memory; the
   app writes their words, app/logic/memory.ts), so this file draws them
   and does not lay them out; the stack's head says its depth and peak; no
   kernel section and no base switch (words in hexadecimal). */

import { code, h, monoCh } from './dom.ts';
import { fit, needed, styles, type Column } from './columns.ts';
import { columnButton } from './ui.ts';

export interface MemorySection {
  type: 'section';
  kind: 'data' | 'stack';
  title: string;
  range: string;
  size: string;
  facts: string;
}

export interface MemoryLine {
  type: 'words' | 'zeros';
  kind: 'data' | 'stack';
  addr: number;
  end: number;
  cells: { text: string; zero: boolean; none: boolean; pointed: boolean; addr: number }[];
  ascii: string[];
  tags: { kind: 'label' | 'pointer'; where: string; text: string }[];
  zeroText: string;
}

const hex32 = (n: number): string => `0x${(n >>> 0).toString(16).padStart(8, '0')}`;
const ASCII: Column = { key: 'ascii', ch: 16, px: 4 };
// Padding (left and right together) and the gap between columns; the smallest
// style is tighter still.
const NORMAL = { pad: 27, gap: 10 };
const TIGHT = { pad: 16, gap: 6 };
const SMALLEST = { pad: 10, gap: 4 };
const dataStyles = (fontPx: number) => styles(NORMAL, TIGHT, fontPx).map((s) => (s.name === 'small' ? { ...s, ...SMALLEST } : s));
const COLUMNS: Column[] = [{ key: 'daddr', ch: 10 }, ...[0, 1, 2, 3].map((i) => ({ key: `w${i}`, ch: 8 })), ASCII];

export class MemoryView {
  readonly root: HTMLElement;
  private folded = new Set<string>();
  private last: (MemorySection | MemoryLine)[] | null = null;
  private forceAscii = false;
  onToggles: (buttons: HTMLElement[]) => void = () => {};

  constructor() {
    this.root = h('div', { class: 'pbody data' });
    new ResizeObserver(() => this.fit()).observe(this.root);
    // A word and its four characters light up together.
    this.root.addEventListener('pointerover', (e) => this.hover(e, true));
    this.root.addEventListener('pointerout', (e) => this.hover(e, false));
  }

  // Margins, font and the ASCII column for the width the tab has now.
  fit(): void {
    const width = this.root.clientWidth;
    if (!width) return;
    const fontPx = (parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--fs')) || 13) - 0.5;
    const ch = monoCh(fontPx);
    const all = dataStyles(fontPx);
    const f = fit(width, COLUMNS, [['ascii']], new Set(this.forceAscii ? ['ascii'] : []), ch, all);
    this.root.dataset.style = f.style.name;
    this.root.classList.toggle('hide-ascii', f.hidden.has('ascii'));
    const auto = fit(width, COLUMNS, [['ascii']], new Set(), ch, all).hidden.has('ascii');
    this.onToggles(auto ? [columnButton('ASCII', this.forceAscii, () => { this.forceAscii = !this.forceAscii; this.fit(); })] : []);
  }

  // The width the tab needs to show the four words and the ASCII column, tight.
  leastWidth(fontPx: number): number {
    const tight = dataStyles(fontPx - 0.5)[1];
    return Math.ceil(needed(COLUMNS, tight, monoCh(fontPx - 0.5)) + 14);
  }

  show(lines: (MemorySection | MemoryLine)[]): void {
    this.last = lines;
    const table = h('div', { class: 'dtable' },
      h('div', { class: 'dhead' }, h('span', {}, 'Address'),
        ...['+0', '+4', '+8', '+C'].map((t) => h('span', { class: 'dval' }, t)),
        h('span', { class: 'ascii' }, 'ASCII')));
    let section: MemorySection | null = null;
    let sectionKey = '';
    for (const l of lines) {
      if (l.type === 'section') {
        section = l;
        sectionKey = `${l.kind} ${l.title}`;
        table.append(this.head(l, sectionKey));
        continue;
      }
      if (section && this.folded.has(sectionKey)) continue;
      table.append(...this.line(l));
    }
    const scroll = this.root.scrollTop;
    this.root.replaceChildren(table);
    this.root.scrollTop = scroll;
  }

  private head(s: MemorySection, key: string): HTMLElement {
    const folded = this.folded.has(key);
    const toggle = h('button', { class: 'dfold', type: 'button', 'aria-expanded': String(!folded) }, folded ? '▸' : '▾');
    const header = h('div', { class: `dsec dsec-${s.kind}` }, toggle,
      h('b', {}, s.title), ' ', code(s.range, 'range'),
      h('span', { class: 'dsize' }, s.size),
      s.facts ? h('span', { class: 'dfacts mono' }, s.facts) : null,
      folded ? h('span', { class: 'dhint' }, '눌러서 펼치기') : null);
    header.addEventListener('click', () => {
      if (this.folded.has(key)) this.folded.delete(key); else this.folded.add(key);
      if (this.last) this.show(this.last);
    });
    return header;
  }

  private line(l: MemoryLine): HTMLElement[] {
    const out: HTMLElement[] = [];
    if (l.tags.length) {
      out.push(h('div', { class: `dtags dsec-${l.kind}` }, ...l.tags.map((t) => (t.kind === 'label'
        ? h('span', { class: 'dlabel' }, code(t.where, 'off'), ' ', code(t.text))
        : h('span', { class: 'dptr' }, code(t.text))))));
    }
    if (l.type === 'zeros') {
      out.push(h('div', { class: `drow dzero dsec-${l.kind}` }, code(hex32(l.addr), 'daddr'),
        h('span', { class: 'dzerotext' }, code(l.zeroText))));
      return out;
    }
    const cells = l.cells.map((c, slot) => h('span', {
      class: `dval${c.none ? ' none' : ''}${c.zero ? ' zero' : ''}${c.pointed ? ' pointed' : ''}`,
      'data-slot': String(slot), title: c.none ? undefined : hex32(c.addr),
    }, c.none ? '' : code(c.text)));
    const chars = l.ascii.map((a, slot) => h('span', { class: 'dch', 'data-slot': String(slot) }, a));
    out.push(h('div', { class: `drow dsec-${l.kind}` }, code(hex32(l.addr), 'daddr'), ...cells,
      h('span', { class: 'dascii mono ascii' }, ...chars)));
    return out;
  }

  private hover(e: Event, on: boolean): void {
    const cell = (e.target as HTMLElement).closest('[data-slot]') as HTMLElement | null;
    const row = cell?.closest('.drow');
    if (!cell || !row) return;
    for (const el of row.querySelectorAll(`[data-slot="${cell.dataset.slot}"]`)) el.classList.toggle('lit', on);
  }
}
