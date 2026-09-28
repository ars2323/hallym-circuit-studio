/* The Instruction panel: one instruction taken apart, as Hallym MIPS's
   Inspector draws it (derived from Hallym MIPS v2.3.0
   electron/src/renderer/app/panels/inspector.ts): the word as thirty-two
   bits, MSB on the left, grouped into its fields; under it one line per
   field.  The same field names and the same colours as Hallym MIPS
   (panels.css .f-*), so the same instruction looks the same in both
   programs.

   Changed from the upstream file: the instruction, its fields and their
   values come from this program's engine -- the Java disassembler (D-127)
   and its field split (D-144), for the instruction the circuit's
   Instruction Memory gives in the cycle on show -- instead of the upstream
   decoder; so there is no sentence of what the instruction does and no
   destination sum (those are the upstream core's words).  It follows the
   Cycle View's cycle; nothing to pin.  The empty word is the caller's. */

import { code, h } from './dom.ts';

export interface InspectorField {
  name: string;
  hi: number;
  lo: number;
  bits: string;
  value: string;
  meaning: string;
}

export interface InspectorInstruction {
  text: string;           // "lw $9, 0($16)"
  format: string;         // R I J CP0 FR FI
  word: string;           // "0x8e090000"
  pc: string;             // "0x00400038"
  fields: InspectorField[];
}

export class Inspector {
  readonly root: HTMLElement;
  readonly body: HTMLElement;

  constructor() {
    this.body = h('div', { class: 'pbody ibody' });
    this.root = h('section', { class: 'insp', 'aria-label': 'Instruction' }, this.body);
  }

  show(ins: InspectorInstruction): void {
    const fields = ins.fields.map((f) => ({ ...f, width: f.hi - f.lo + 1 }));
    const cls = (name: string) => `f-${name}`;
    // The word as 32 cells, one per bit, each field a coloured group.
    const grid = h('div', { class: 'bitgrid' }, ...fields.map((f) =>
      h('div', { class: `fbox ${cls(f.name)}`, style: `grid-column: span ${f.width}`, 'data-field': f.name },
        h('div', { class: 'franges mono' }, h('span', {}, String(f.hi)), h('span', {}, f.hi !== f.lo ? String(f.lo) : '')),
        h('div', { class: 'fbits mono', style: `grid-template-columns: repeat(${f.width}, 1fr)` },
          ...[...f.bits].map((b) => h('span', { class: 'bit' }, b))),
        h('div', { class: 'fname' }, f.name),
        h('div', { class: 'fmean mono' }, f.meaning || f.value))));
    const table = h('table', { class: 'ftable' },
      h('tr', {}, ...['Field', 'Bits', 'Binary', 'Value', 'Meaning'].map((t) => h('th', {}, t))),
      ...fields.map((f) => h('tr', {},
        h('td', {}, h('span', { class: `sw ${cls(f.name)}` }), f.name),
        h('td', { class: 'mono', 'data-label': 'Bits' }, `${f.hi}–${f.lo}`), h('td', { class: 'mono', 'data-label': 'Binary' }, f.bits),
        h('td', { class: 'mono' }, f.value), h('td', { class: 'mono' }, f.meaning))));
    this.body.replaceChildren(
      h('div', { class: 'ihead' },
        code(ins.text, 'dis'), h('span', { class: `badge b-${ins.format}` }, ins.format),
        h('span', { class: 'grow' }),
        h('span', { class: 'where' }, code(ins.word), ' · ', code(ins.pc))),
      grid,
      table);
  }

  // Nothing to take apart: the caller's word (a notice) instead.
  empty(content: Node): void {
    this.body.replaceChildren(content);
  }
}
