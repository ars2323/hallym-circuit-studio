/* Quick Attributes (N-10, D-157; v1 QuickBar, I-103, I-104, S-04): a small
   bar by the selection with the part kind's most-used attributes (at most
   five, in the kind registry's order: a gate's inputs, bits, size, facing,
   label; a pin's label, bits, output, three-state, facing; a register's
   bits, trigger, label …) as "<name> <value>" buttons, All Attributes (the
   Attributes panel), Auto Appearance for a subcircuit with its default
   appearance (N-11's intent), and a line of the original's hidden keys
   ("0–9: Number of Inputs", "Alt+0–9: …", R: Rotate, F2: Label).

   A list's button opens its choices (one undo step for every part chosen);
   a single part's Label edits it in place (F2's field); another text
   attribute opens a field under the bar.  The buttons never take the focus:
   the keys stay with the Canvas (the digits go to the original's key
   configurator).

   Shown while the Edit tool is in hand, the parts chosen (wires left out)
   are all of one kind in a circuit that can be changed, and Quick
   Attributes is on (the Attributes panel's foot, this run).  Hidden while
   the left button is down on the Canvas and back when it is let go; after a
   drag that moved something, an arrow key, a message chosen or a place
   found (hcs:reveal) it stays away until the next press on the Canvas --
   a selection the program made is not the student's to change right there
   (v1 "quiet", UI-CHECKLIST 6).  Placed where it covers nothing (logic/
   attributes.ts placement), off-screen targets hide it; it follows zoom and
   scroll. */

import type { AttrTable, Component, Wire } from '../../main/protocol.ts';
import type { CircuitCanvas } from '../canvas/canvas.ts';
import { showMenu } from '../canvas/overlays/menu.ts';
import { toScreen } from '../canvas/view.ts';
import { h } from '../shared/dom.ts';
import { hintLine, placement, type QuickButton, quickButtons, type Rect } from './logic/attributes.ts';

export interface QuickHost {
  board: CircuitCanvas;
  table(): AttrTable | null;                 // the Attributes panel's table (the selection's)
  selected(): string[];                      // the parts and wires chosen in the circuit on show
  toolIsEdit(): boolean;
  on(): boolean;                             // Quick Attributes (the panel's foot)
  apply(attr: string, value: string): Promise<boolean>;   // on every part chosen (the table's own way)
  editLabel(id: string): void;               // F2's field
  showAll(): void;                           // All Attributes: the Attributes panel
  autoAppearance?: (circuitId: string) => void;          // N-11 (Auto Appearance), when it is there
}

export class QuickBar {
  readonly root: HTMLElement;
  private readonly host: QuickHost;
  private pressed = false;
  private quiet = false;
  private field: HTMLInputElement | null = null;
  private target: Rect | null = null;

  constructor(host: QuickHost) {
    this.host = host;
    this.root = h('div', { class: 'quickbar', role: 'toolbar', 'aria-label': 'Quick Attributes', hidden: true });
    host.board.root.append(this.root);
    const c = host.board.canvas;
    let at: { x: number; y: number; onPart: boolean } | null = null;
    c.addEventListener('pointerdown', (e) => {
      this.quiet = false;                     // the student's own press: shown again after it
      if (e.button !== 0) return;             // the right button's menu: no release comes to the Canvas
      const p = host.board.pointerOf(e).at;
      at = { x: e.clientX, y: e.clientY, onPart: host.board.partAt(p) !== null || host.board.wireAt(p) !== null };
      this.pressed = true;
      this.hide();
    });
    const up = (e: PointerEvent) => {
      if (!this.pressed) return;
      this.pressed = false;
      // a drag that moved the parts: away until the next press (v1 S-04: the bar covered the chips it had moved)
      if (at && at.onPart && Math.hypot(e.clientX - at.x, e.clientY - at.y) > 3) this.quiet = true;
      at = null;
      setTimeout(() => this.update());
    };
    window.addEventListener('pointerup', up, true);
    window.addEventListener('pointercancel', up, true);
    c.addEventListener('keydown', (e) => {
      if (/^Arrow/.test(e.key) && !e.ctrlKey && !e.metaKey && this.host.selected().length) { this.quiet = true; this.hide(); }
    });
    // a message chosen, a place found: the program's selection (v1 markQuiet)
    window.addEventListener('hcs:reveal', () => { this.quiet = true; this.hide(); });
  }

  // A right-click menu open for something else than the selection (a wire, an empty spot, another part: the
  // original keeps the selection, MenuTool.mousePressed): the bar stays away while it is open (D-158).
  private blocked = false;
  block(on: boolean): void {
    this.blocked = on;
    if (on) this.hide(); else this.update();
  }

  // The program chose these (Find E/X Origin, a message): the bar stays away until the next press.
  hush(): void {
    this.quiet = true;
    this.hide();
  }

  shown(): boolean { return !this.root.hidden; }

  private hide(): void {
    this.root.hidden = true;
    this.field?.remove();
    this.field = null;
  }

  // The parts chosen now (wires left out), in the circuit on show.
  private parts(): Component[] {
    const s = this.host.board.scene;
    if (!s) return [];
    return this.host.selected().map((id) => s.components.get(id)).filter((c): c is Component => !!c);
  }

  // Again: the buttons for the table now, and where they go.
  update(): void {
    const t = this.host.table();
    const parts = this.parts();
    if (this.pressed || this.quiet || this.blocked || !this.host.on() || !this.host.toolIsEdit() || !t || t.target !== 'selection' || !t.quick || !t.editable
      || !parts.length || parts.length !== t.quick.count) {
      this.hide();
      return;
    }
    const buttons = quickButtons(t);
    const row = h('div', { class: 'qrow' }, ...buttons.map((b) => this.button(b, parts)));
    const all = h('button', { type: 'button', class: 'qbtn qlink', tabindex: '-1', title: 'Attributes 패널에서 모든 속성을 봅니다' }, 'All Attributes');
    noFocus(all);
    all.addEventListener('click', () => this.host.showAll());
    row.append(all);
    const auto = t.quick.autoAppearance;
    if (auto && this.host.autoAppearance) {
      const b = h('button', { type: 'button', class: 'qbtn qlink', tabindex: '-1', title: '포트 이름이 보이는 상자를 만듭니다' }, 'Auto Appearance');
      noFocus(b);
      b.addEventListener('click', () => this.host.autoAppearance?.(auto));
      row.append(b);
    }
    const hint = hintLine(t.quick);
    this.root.replaceChildren(row, hint ? h('div', { class: 'qhint' }, hint) : '');
    this.root.hidden = false;
    this.place();
  }

  private button(b: QuickButton, parts: Component[]): HTMLElement {
    const el = h('button', { type: 'button', class: 'qbtn', tabindex: '-1', title: `Change ${b.name}`, 'aria-label': `${b.name} ${b.text}`, 'data-attr': b.attr },
      h('span', { class: 'qname' }, b.name), ' ', h('span', { class: 'qval' }, b.text));
    noFocus(el);
    el.addEventListener('click', () => {
      if (b.options) {
        const r = el.getBoundingClientRect();
        showMenu(b.options.map((o) => ({ label: o.display, radio: true, checked: o.checked === true, run: () => void this.host.apply(b.attr, o.value) })), r.left, r.bottom + 2);
        this.host.board.canvas.focus({ preventScroll: true });   // the keys stay with the Canvas while the list is open
      } else if (b.label && parts.length === 1) {
        this.host.editLabel(parts[0].id);
      } else {
        this.openField(b, el);
      }
    });
    return el;
  }

  // A text attribute of several parts (or not the label): a field under the bar (v1 InlineEditor.start).
  private openField(b: QuickButton, under: HTMLElement): void {
    this.field?.remove();
    const r = under.getBoundingClientRect();
    const box = this.host.board.root.getBoundingClientRect();
    const input = h('input', { type: 'text', class: 'inline-field', 'aria-label': b.name, spellcheck: 'false', autocomplete: 'off',
      value: b.text === '(none)' ? '' : b.text, title: `${b.name}: Enter 적용, Esc 취소` }) as HTMLInputElement;
    input.style.left = `${Math.round(r.left - box.left)}px`;
    input.style.top = `${Math.round(r.bottom - box.top + 2)}px`;
    input.style.width = `${Math.max(120, Math.round(r.width))}px`;
    this.host.board.root.append(input);
    this.field = input;
    const close = () => { input.remove(); if (this.field === input) this.field = null; this.host.board.canvas.focus({ preventScroll: true }); };
    let done = false;
    const commit = async () => {
      if (done) return;
      done = true;
      const ok = await this.host.apply(b.attr, input.value);
      if (!ok) {
        done = false;
        input.setAttribute('aria-invalid', 'true');
        input.title = `"${input.value}" 값을 이 속성에 넣을 수 없습니다.`;
        input.focus();
        return;
      }
      close();
    };
    input.addEventListener('keydown', (e) => {
      e.stopPropagation();
      if (e.isComposing) return;
      if (e.key === 'Enter') { e.preventDefault(); void commit(); }
      if (e.key === 'Escape') { e.preventDefault(); done = true; close(); }
    });
    input.addEventListener('blur', () => { if (!done) void commit(); });
    input.focus();
    input.select();
  }

  // Where it goes (v1 QuickBar.place): by the chosen parts' box, covering no part or chip, few wires.
  place(): void {
    if (this.root.hidden) return;
    const b = this.host.board;
    const s = b.scene;
    const parts = this.parts();
    if (!s || !parts.length) { this.hide(); return; }
    const v = b.view;
    const toRect = (x0: number, y0: number, x1: number, y1: number): Rect => {
      const [sx, sy] = toScreen(v, [x0, y0]);
      return { x: sx, y: sy, w: (x1 - x0) * v.zoom, h: (y1 - y0) * v.zoom };
    };
    let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity;
    for (const c of parts) {
      x0 = Math.min(x0, c.bounds[0]); y0 = Math.min(y0, c.bounds[1]);
      x1 = Math.max(x1, c.bounds[0] + c.bounds[2]); y1 = Math.max(y1, c.bounds[1] + c.bounds[3]);
    }
    const target = toRect(x0, y0, x1, y1);
    const size = b.size();
    const view: Rect = { x: 0, y: 0, w: size.width, h: size.height };
    if (target.x > view.w || target.y > view.h || target.x + target.w < 0 || target.y + target.h < 0) { this.root.hidden = true; return; }
    const self = { x: target.x - 6, y: target.y - 6, w: target.w + 12, h: target.h + 12 };
    const chosen = new Set(parts.map((c) => c.id));
    const hard: Rect[] = [];
    for (const c of s.components.values()) if (!chosen.has(c.id)) hard.push(toRect(c.bounds[0], c.bounds[1], c.bounds[0] + c.bounds[2], c.bounds[1] + c.bounds[3]));
    for (const box of b.chipBoxes()) hard.push(toRect(box.x0, box.y0, box.x1, box.y1));
    const soft: Rect[] = [...s.wires.values()].map((w: Wire) => {
      const r = toRect(Math.min(w.a[0], w.b[0]), Math.min(w.a[1], w.b[1]), Math.max(w.a[0], w.b[0]), Math.max(w.a[1], w.b[1]));
      return { x: r.x - 4, y: r.y - 4, w: r.w + 8, h: r.h + 8 };     // a bus is 4 px, a dot bigger
    });
    const bar = { w: this.root.offsetWidth, h: this.root.offsetHeight };
    const at = placement(self, bar, hard, soft, view);
    this.target = at;
    this.root.style.left = `${Math.round(at.x)}px`;
    this.root.style.top = `${Math.round(at.y)}px`;
  }

  // Where it stands now (tests): screen px in the Canvas.
  box(): Rect | null { return this.root.hidden ? null : this.target; }
}

// A button that does not take the keys from the Canvas.
function noFocus(b: HTMLElement): void {
  b.addEventListener('pointerdown', (e) => e.preventDefault());
}
