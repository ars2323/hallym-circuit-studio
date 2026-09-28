/* The editing tools on the Canvas (N-08, D-146; docs/interaction-parity.md
   I-09..I-62, I-77..I-83, I-105): Edit (select, move, draw a wire from a
   wiring point), Wire, a part to place (from the toolbar, the Components
   list or the palette: hcs:place-tool) and Text.  Each gesture ends in one
   intent to the engine, which runs Logisim's own tool code on the model
   (SelectTool, MoveGesture + v1 SafeMove, WiringTool, AddTool, TextTool):
   the result comes back as model.changed and edit.selection.  While the
   gesture goes on, the tool shows what Logisim shows (the green circle,
   the wire, the rectangle, the parts dragged with the wires the engine
   says would follow, the part about to be placed): gestures.ts. */

import type { Component, EditResult, EditSelection, MovePreview, Point, ToolGhost, Wire, WindowMethod } from '../../main/protocol.ts';
import type { CanvasPointer, CanvasTool, CircuitCanvas, Overlay } from '../canvas/canvas.ts';
import { boundsOf, dragOffset, isClick, logical, type Parts, rectOf, snapPoint, snapsToGrid, WireDrag, wiringPoint } from '../canvas/gestures.ts';
import { pokePoint, pokeTarget } from '../canvas/poke.ts';
import { toScreen } from '../canvas/view.ts';
import { h } from '../shared/dom.ts';
import { chained, ctrlPokes, editKey, isInputPin, labelFieldWidth, labelled, placeKey } from './logic/editing.ts';

export interface Where { fileId: string; circuitId: string }

// A part to place: a library's tool (lib null: a circuit of this file) and the values for this part only.
export interface Held { lib: string | null; name: string; attrs?: Record<string, string> }

export interface ToolsHost {
  board: CircuitCanvas;
  where(): Where | null;
  ready(): boolean;
  // An intent; null when the engine refused it (the status bar says why).
  edit<T = EditResult>(method: WindowMethod, params: Record<string, unknown>, name: string): Promise<T | null>;
  // A question that changes nothing (model.*); null when it failed.
  ask<T>(method: WindowMethod, params: Record<string, unknown>): Promise<T | null>;
  selection(): EditSelection | null;       // the engine's selection in the circuit on show
  placed(): void;                          // a part was placed: the Edit tool again (Logisim's After Adding: Edit Tool)
  guess(ids: string[]): void;              // the selection a press will make, shown at once (the engine's edit.selection follows)
  pinValue(c: Component): void;            // a double click on an input pin (I-78)
}

// The parts and wires of the circuit on show, as the gestures read them (arrays: a gesture reads them more than once).
function partsOf(board: CircuitCanvas): Parts {
  const s = board.scene;
  return { components: s ? [...s.components.values()] : [], wires: s ? [...s.wires.values()] : [] };
}

const selectedIds = (host: ToolsHost): Set<string> => new Set(host.selection()?.ids ?? []);
const hasSelection = (host: ToolsHost): boolean => {
  const s = host.selection();
  return !!s && (s.ids.length > 0 || s.floating.length > 0);
};
const isWire = (x: Component | Wire): x is Wire => 'a' in x && 'b' in x;

// The floating parts (a paste, a duplicate) shown over the circuit while nothing is dragged.
export function floatingOverlay(host: ToolsHost): Overlay['ghost'] {
  const f = host.selection()?.floating ?? [];
  if (!f.length) return null;
  return { parts: f.filter((x) => !isWire(x)) as Component[], wires: f.filter(isWire), dx: 0, dy: 0, look: 'float' };
}

/* ---------------------------------------------------------------- the Edit tool */

type Press =
  | { kind: 'wire'; drag: WireDrag; at: Point; still: boolean }
  | { kind: 'select'; at: Point; shift: boolean; answer: Promise<EditResult | null>; mode: 'moving' | 'rect' | null; guess: 'moving' | 'rect'; dx: number; dy: number; connect: boolean }
  | { kind: 'poke'; id: string; where: Where };

export class EditTool implements CanvasTool {
  private readonly host: ToolsHost;
  private press: Press | null = null;
  private previewAsked = '';
  lastWire = false;                        // the last intent was a wire this tool drew (Backspace takes it back, I-31)
  private lastKeyAt = -Infinity;           // when a digit went to the configurator (digits in a row make one number)
  private hoverDot: Point | null = null;

  constructor(host: ToolsHost) {
    this.host = host;
  }

  private get board(): CircuitCanvas { return this.host.board; }

  // What to show when nothing is pressed: the floating paste, the green circle.
  show(extra: Overlay = {}): void {
    this.board.setOverlay({ ghost: floatingOverlay(this.host), dot: this.hoverDot, ...extra });
  }

  down(e: CanvasPointer): void {
    const w = this.host.where();
    if (!w || !this.host.ready() || !this.board.scene) return;
    const p = logical(e.at);
    this.lastWire = false;
    if (e.ctrl) {
      // Ctrl+click on an input pin or a button pokes it (v1, I-77); elsewhere Ctrl+click is the menu's (N-10)
      const c = pokeTarget(this.board.scene.components.values(), e.at);
      if (c && ctrlPokes(c)) {
        this.press = { kind: 'poke', id: c.id, where: w };
        void this.host.edit('sim.poke', { fileId: w.fileId, circuitId: w.circuitId, componentId: c.id, at: pokePoint(e.at), action: 'press' }, 'Poke');
      }
      return;
    }
    const wp = wiringPoint(partsOf(this.board), selectedIds(this.host), p, e.alt);
    this.hoverDot = null;
    if (wp) {
      // a wiring point: a wire from here (EditTool → WiringTool), or a click that selects (I-15)
      this.press = { kind: 'wire', drag: new WireDrag(partsOf(this.board), wp), at: p, still: true };
      this.show({ ghost: floatingOverlay(this.host) });
      return;
    }
    // SelectTool.mousePressed in the engine: its answer says whether the drag moves or draws a rectangle.
    // Until it comes, what the screen sees under the pointer (the selection or a part: moving) stands in,
    // and the selection it will make is shown at once (the engine's edit.selection always follows)
    const under = this.board.partAt(e.at) ?? this.board.wireAt(e.at);
    const guess = under !== null ? 'moving' : 'rect';
    const now = selectedIds(this.host);
    if (under === null) { if (!e.shift) this.host.guess([]); }
    else if (e.shift) this.host.guess(now.has(under) ? [...now].filter((id) => id !== under) : [...now, under]);
    else if (!now.has(under)) this.host.guess([under]);
    const answer = this.host.edit<EditResult>('edit.select', { fileId: w.fileId, circuitId: w.circuitId, at: p, toggle: e.shift }, 'Edit');
    const pr: Press = { kind: 'select', at: p, shift: e.shift, answer, mode: null, guess, dx: 0, dy: 0, connect: !e.shift };
    this.press = pr;
    void answer.then((r) => { if (this.press === pr) { pr.mode = r?.outcome === 'moving' ? 'moving' : 'rect'; } });
  }

  move(e: CanvasPointer): void {
    const p = logical(e.at);
    const pr = this.press;
    if (!pr) {
      // the green circle where a press would start a wire (I-44)
      const dot = this.board.scene ? wiringPoint(partsOf(this.board), selectedIds(this.host), p, e.alt) : null;
      if ((dot?.[0] !== this.hoverDot?.[0]) || (dot?.[1] !== this.hoverDot?.[1])) { this.hoverDot = dot; this.show(); }
      return;
    }
    if (pr.kind === 'wire') {
      if (pr.still && !isClick(pr.at, p)) pr.still = false;
      if (pr.drag.move(p)) this.showWire(pr.drag);
      return;
    }
    if (pr.kind !== 'select') return;
    const mode = pr.mode ?? pr.guess;
    if (mode === 'rect') {
      this.show({ rubber: rectOf(pr.at, p) });
      return;
    }
    this.dragTo(pr, p, !e.shift);
  }

  // The selection dragged by (dx, dy): its picture there, and the wires the engine would add (MoveGesture).
  private dragTo(pr: Extract<Press, { kind: 'select' }>, p: Point, connect: boolean): void {
    const s = this.board.scene;
    const sel = this.host.selection();
    if (!s || !sel) return;
    const comps = sel.ids.map((id) => s.components.get(id)).filter((c): c is Component => !!c);
    const wires = sel.ids.map((id) => s.wires.get(id)).filter((c): c is Wire => !!c);
    const floatParts = sel.floating.filter((x) => !isWire(x)) as Component[];
    const floatWires = sel.floating.filter(isWire);
    const all = [...comps, ...floatParts];
    const box = boundsOf(all, [...wires, ...floatWires]);
    const snaps = all.some(snapsToGrid) || all.length === 0;
    const [dx, dy] = dragOffset(box, snaps, p[0] - pr.at[0], p[1] - pr.at[1]);
    pr.dx = dx; pr.dy = dy; pr.connect = connect;
    const hidden = new Set<string>(sel.ids);
    this.board.setOverlay({ ghost: { parts: all, wires: [...wires, ...floatWires], dx, dy, look: 'move' }, hidden: wires.length ? hidden : undefined });
    if ((dx || dy) && connect && comps.length + wires.length > 0) void this.preview(pr, dx, dy);
  }

  // model.movePreview: only the last drag point's is shown (a request still out for an older one is dropped).
  private async preview(pr: Extract<Press, { kind: 'select' }>, dx: number, dy: number): Promise<void> {
    const w = this.host.where();
    if (!w) return;
    const k = `${dx},${dy}`;
    this.previewAsked = k;
    const r = await this.host.ask<MovePreview>('model.movePreview', { fileId: w.fileId, circuitId: w.circuitId, dx, dy, connect: true });
    if (!r || this.press !== pr || this.previewAsked !== k || !pr.connect) return;
    this.board.setOverlay({ ...this.board.overlay, moveWires: { added: r.added, unconnected: r.unconnected } });
  }

  private showWire(d: WireDrag): void {
    const sh = d.shortened();
    this.board.setOverlay({
      ghost: floatingOverlay(this.host),
      wire: sh ? (sh.rest ? [sh.rest[0], sh.rest[1]] : null) : d.points(),
      hidden: sh ? new Set([sh.wire.id]) : undefined,
    });
  }

  up(e: CanvasPointer): void {
    const pr = this.press;
    this.press = null;
    const w = this.host.where();
    if (!pr || !w) { this.show(); return; }
    const p = logical(e.at);
    if (pr.kind === 'poke') {
      void this.host.edit('sim.poke', { fileId: pr.where.fileId, circuitId: pr.where.circuitId, componentId: pr.id, at: pokePoint(e.at), action: 'release' }, 'Poke');
      return;
    }
    if (pr.kind === 'wire') {
      this.show();
      if (pr.still || !pr.drag.dragged) {
        // a click on a wiring point selects what is there (EditTool.isClick → SelectTool)
        void this.host.edit('edit.select', { fileId: w.fileId, circuitId: w.circuitId, at: p, toggle: e.shift }, 'Edit');
        return;
      }
      pr.drag.move(p);
      void this.host.edit('edit.addWire', { fileId: w.fileId, circuitId: w.circuitId, points: pr.drag.points(), tool: 'edit' }, 'Wire')
        .then((r) => { this.lastWire = !!r?.changed; });
      return;
    }
    // the rest waits for the press's answer (the engine takes the intents in order anyway)
    void pr.answer.then((r) => {
      this.show();
      if (!r) return;
      if (r.outcome === 'rect') {
        const b = rectOf(pr.at, p);
        if (b.x0 === b.x1 && b.y0 === b.y1) return;     // a click on nothing: the press already dropped the selection
        void this.host.edit('edit.select', { fileId: w.fileId, circuitId: w.circuitId, rect: [b.x0, b.y0, b.x1, b.y1], add: pr.shift }, 'Edit');
        return;
      }
      if (r.outcome !== 'moving') return;
      if (pr.mode === null) { pr.mode = 'moving'; this.dragTo(pr, p, !e.shift); }
      if (pr.dx || pr.dy) void this.host.edit('edit.move', { fileId: w.fileId, circuitId: w.circuitId, dx: pr.dx, dy: pr.dy, connect: !e.shift }, 'Move');
    });
  }

  dbl(e: CanvasPointer): void {
    const s = this.board.scene;
    if (!s || e.ctrl) return;
    const c = pokeTarget(s.components.values(), e.at);
    if (!c) return;
    if (isInputPin(c)) { this.host.pinValue(c); return; }       // I-78
    if (labelled(c)) void editLabel(this.host, c);                // I-42, I-105 (not into a subcircuit: I-114)
  }

  key(e: KeyboardEvent): boolean {
    const w = this.host.where();
    if (!w || !this.host.ready()) return false;
    const pr = this.press;
    if (pr?.kind === 'select' && e.key === 'Shift') {
      // Shift while dragging turns Keep Connections round for this drag (I-25)
      if ((pr.mode ?? pr.guess) === 'moving' && this.board.lastPointer) this.dragTo(pr, logical(this.board.lastPointer.at), false);
      return true;
    }
    const sel = this.host.selection();
    const s = this.board.scene;
    const one = sel && sel.ids.length === 1 && sel.floating.length === 0 ? s?.components.get(sel.ids[0]) : undefined;
    const k = editKey(e, hasSelection(this.host), !!one && labelled(one));
    if (!k) return false;
    const base = { fileId: w.fileId, circuitId: w.circuitId };
    switch (k.kind) {
      case 'delete': void this.host.edit('edit.delete', base, 'Delete'); break;
      case 'undoWire': if (!this.lastWire) return false; void this.host.edit('edit.undo', base, 'Undo'); break;
      case 'nudge': void this.host.edit('edit.move', { ...base, dx: k.dx, dy: k.dy, connect: true }, 'Move'); break;
      case 'rotate': void this.host.edit('edit.rotate', { ...base, clockwise: k.clockwise }, 'Rotate'); break;
      case 'keyConfig': {
        const now = performance.now();
        const chain = chained(this.lastKeyAt, now);
        this.lastKeyAt = now;
        void this.host.edit('edit.keyConfig', { ...base, key: k.key, alt: k.alt, chain }, 'Edit');
        break;
      }
      case 'label': if (one) void editLabel(this.host, one); break;
      case 'duplicate': void this.host.edit('edit.duplicate', base, 'Duplicate'); break;
      case 'fit': {
        const s2 = this.board.scene;
        const sel2 = this.host.selection();
        if (!s2 || !sel2) return false;
        const comps = sel2.ids.map((id) => s2.components.get(id)).filter((c): c is Component => !!c);
        const ws = sel2.ids.map((id) => s2.wires.get(id)).filter((c): c is Wire => !!c);
        const box = boundsOf([...comps, ...sel2.floating.filter((x) => !isWire(x)) as Component[]], [...ws, ...sel2.floating.filter(isWire)]);
        if (!box) return false;
        this.board.fitBox(box);
        return true;
      }
    }
    this.lastWire = false;
    return true;
  }

  keyup(e: KeyboardEvent): void {
    const pr = this.press;
    if (pr?.kind === 'select' && e.key === 'Shift' && (pr.mode ?? pr.guess) === 'moving' && this.board.lastPointer) {
      this.dragTo(pr, logical(this.board.lastPointer.at), true);
    }
  }

  leave(): void {
    if (this.press) return;
    this.hoverDot = null;
    this.show();
  }

  cursor(): string {
    const pr = this.press;
    if (pr?.kind === 'select' && (pr.mode ?? pr.guess) === 'moving' && (pr.dx || pr.dy)) return 'move';
    if (pr?.kind === 'select' && (pr.mode ?? pr.guess) === 'rect') return 'crosshair';
    return '';
  }

  cancel(): void {
    this.press = null;
    this.hoverDot = null;
  }
}

/* ---------------------------------------------------------------- the Wire tool */

export class WireTool implements CanvasTool {
  private readonly host: ToolsHost;
  private drag: WireDrag | null = null;
  lastWire = false;

  constructor(host: ToolsHost) {
    this.host = host;
  }

  private get board(): CircuitCanvas { return this.host.board; }

  down(e: CanvasPointer): void {
    if (!this.host.where() || !this.host.ready() || !this.board.scene) return;
    this.lastWire = false;
    this.drag = new WireDrag(partsOf(this.board), logical(e.at));
    this.board.setOverlay({ wire: null });
  }

  move(e: CanvasPointer): void {
    const p = logical(e.at);
    const d = this.drag;
    if (!d) { this.board.setOverlay({ cursorDot: snapPoint(p) }); return; }   // I-45
    if (!d.move(p)) return;
    const sh = d.shortened();
    this.board.setOverlay({ wire: sh ? (sh.rest ? [sh.rest[0], sh.rest[1]] : null) : d.points(), hidden: sh ? new Set([sh.wire.id]) : undefined });
  }

  up(e: CanvasPointer): void {
    const d = this.drag;
    this.drag = null;
    const w = this.host.where();
    this.board.setOverlay({ cursorDot: snapPoint(logical(e.at)) });
    if (!d || !w) return;
    d.move(logical(e.at));
    if (!d.dragged) return;                  // pressed and let go without moving: no wire (I-46)
    void this.host.edit('edit.addWire', { fileId: w.fileId, circuitId: w.circuitId, points: d.points(), tool: 'wiring' }, 'Wire')
      .then((r) => { this.lastWire = !!r?.changed; });
  }

  key(e: KeyboardEvent): boolean {
    const w = this.host.where();
    if (!w || e.key !== 'Backspace' || e.ctrlKey || e.altKey || e.metaKey || !this.lastWire) return false;
    // WiringTool.keyPressed: the wire just drawn, if it is still the last edit (I-31)
    this.lastWire = false;
    void this.host.edit('edit.undo', { fileId: w.fileId, circuitId: w.circuitId }, 'Undo');
    return true;
  }

  leave(): void { if (!this.drag) this.board.setOverlay({}); }
  cursor(): string { return 'crosshair'; }
  cancel(): void { this.drag = null; }
}

/* ---------------------------------------------------------------- placing a part */

export class PlaceTool implements CanvasTool {
  private readonly host: ToolsHost;
  held: Held | null = null;
  private ghost: Component | null = null;         // the part at (0, 0), from the engine (model.tool)
  private at: Point | null = null;
  private asked = 0;
  private lastKeyAt = -Infinity;

  constructor(host: ToolsHost) {
    this.host = host;
  }

  private get board(): CircuitCanvas { return this.host.board; }

  hold(p: Held): void {
    this.held = p;
    this.ghost = null;
    void this.refresh();
  }

  // The part as the tool would place it now (its attributes, facing): drawn under the pointer (I-55).
  async refresh(): Promise<void> {
    const w = this.host.where();
    const held = this.held;
    if (!w || !held) return;
    const n = ++this.asked;
    const r = await this.host.ask<ToolGhost>('model.tool', { fileId: w.fileId, lib: held.lib, name: held.name, loc: [0, 0], ...(held.attrs ? { attrs: held.attrs } : {}) });
    if (n !== this.asked || this.held !== held) return;
    this.ghost = r?.component ?? null;
    this.draw();
  }

  private draw(): void {
    this.board.setOverlay(this.ghost && this.at ? { ghost: { parts: [this.ghost], wires: [], dx: this.at[0], dy: this.at[1], look: 'place' } } : {});
  }

  move(e: CanvasPointer): void {
    this.at = snapPoint(logical(e.at));
    this.draw();
  }

  down(e: CanvasPointer): void { this.move(e); }

  up(e: CanvasPointer): void {
    const w = this.host.where();
    const held = this.held;
    if (!w || !held || !this.host.ready()) return;
    const loc = snapPoint(logical(e.at));
    void this.host.edit('edit.addComponent', { fileId: w.fileId, circuitId: w.circuitId, lib: held.lib, name: held.name, loc, ...(held.attrs ? { attrs: held.attrs } : {}) }, held.name)
      .then((r) => { if (r?.changed) this.host.placed(); });
  }

  key(e: KeyboardEvent): boolean {
    const w = this.host.where();
    const held = this.held;
    if (!w || !held) return false;
    const k = placeKey(e);
    if (!k) return false;
    const now = performance.now();
    const chain = chained(this.lastKeyAt, now);
    this.lastKeyAt = now;
    // AddTool.keyPressed: the tool's configurator, or its facing (a tool attribute, one undo step)
    void this.host.edit('edit.keyConfig', { fileId: w.fileId, circuitId: w.circuitId, lib: held.lib, name: held.name, key: k.key, alt: k.alt, chain }, held.name)
      .then(() => this.refresh());
    return true;
  }

  leave(): void { this.at = null; this.draw(); }   // the ghost goes when the pointer leaves the Canvas
  cursor(): string { return 'copy'; }
}

/* ---------------------------------------------------------------- the Text tool */

export class TextTool implements CanvasTool {
  private readonly host: ToolsHost;
  field: InlineField | null = null;

  constructor(host: ToolsHost) {
    this.host = host;
  }

  down(e: CanvasPointer): void {
    const w = this.host.where();
    if (!w || !this.host.ready()) return;
    // a field open elsewhere ends first (TextTool.mousePressed: stopEditing)
    this.field?.commit();
    const p = logical(e.at);
    void (async () => {
      const t = await this.host.ask<{ id: string | null; text?: string; box?: [number, number, number, number]; none?: boolean }>('model.textAt', { fileId: w.fileId, circuitId: w.circuitId, loc: p });
      if (!t || t.none) return;
      const box = t.box ?? [p[0], p[1] - 8, 40, 16];
      this.field = new InlineField(this.host.board, { at: [box[0], box[1]], width: Math.max(80, box[2] * this.host.board.view.zoom + 24), text: t.text ?? '', label: 'Text' }, async (text) => {
        this.field = null;
        if (t.id !== null) {
          if (text === (t.text ?? '')) return true;
          return (await this.host.edit('edit.text', { fileId: w.fileId, circuitId: w.circuitId, id: t.id, text }, 'Text')) !== null;
        }
        if (!text) return true;                 // an empty new Label is not added (TextTool)
        return (await this.host.edit('edit.text', { fileId: w.fileId, circuitId: w.circuitId, loc: p, text }, 'Text')) !== null;
      }, () => { this.field = null; });
    })();
  }

  cursor(): string { return 'text'; }
  stop(): void { this.field?.commit(); }
}

/* ---------------------------------------------------------------- a text field over the Canvas */

/* A field over the circuit (the Text tool's caret, the label edited in
   place): Enter applies, Esc closes without applying, leaving it (a click
   outside, Tab) applies (v1 InlineEditor; TextTool's caret).  `apply`
   answers false to keep it open (a value the engine refused: red, the
   reason in its tooltip). */
export class InlineField {
  readonly input: HTMLInputElement;
  private done = false;
  private readonly apply: (text: string) => Promise<boolean>;
  private readonly closed: () => void;

  constructor(board: CircuitCanvas, o: { at: Point; width: number; text: string; label: string; centre?: boolean }, apply: (text: string) => Promise<boolean>, closed: () => void) {
    this.apply = apply;
    this.closed = closed;
    const [sx, sy] = toScreen(board.view, o.at);
    this.input = h('input', { type: 'text', class: 'inline-field', 'aria-label': o.label, spellcheck: 'false', autocomplete: 'off', value: o.text }) as HTMLInputElement;
    this.input.style.width = `${Math.round(o.width)}px`;
    this.input.style.left = `${Math.round(o.centre ? sx - o.width / 2 : sx)}px`;
    this.input.style.top = `${Math.round(o.centre ? sy - 12 : sy)}px`;
    board.root.append(this.input);
    this.input.addEventListener('keydown', (e) => {
      e.stopPropagation();                     // the Canvas's and the window's keys wait
      if (e.isComposing) return;
      if (e.key === 'Enter') { e.preventDefault(); void this.commit(); }
      if (e.key === 'Escape') { e.preventDefault(); this.cancel(); }
    });
    this.input.addEventListener('input', () => { this.input.removeAttribute('aria-invalid'); this.input.title = ''; });
    this.input.addEventListener('blur', () => { if (!this.done) void this.commit(); });
    this.input.focus();
    this.input.select();
  }

  async commit(): Promise<void> {
    if (this.done) return;
    this.done = true;
    const ok = await this.apply(this.input.value);
    if (!ok) {
      this.done = false;
      this.input.setAttribute('aria-invalid', 'true');
      this.input.title = `"${this.input.value}" is not a valid value for this property.`;
      this.input.focus();
      return;
    }
    this.close();
  }

  cancel(): void {
    this.done = true;
    this.close();
  }

  private close(): void {
    this.input.remove();
    this.closed();
  }
}

// F2 or a double click: the part's label in a field at its middle (v1 InlineEditor.editLabel, I-42, I-105).
let labelField: InlineField | null = null;
export async function editLabel(host: ToolsHost, c: Component): Promise<void> {
  const w = host.where();
  if (!w) return;
  labelField?.cancel();                         // one at a time: the one before closes without applying
  const board = host.board;
  const mid: Point = [c.bounds[0] + c.bounds[2] / 2, c.bounds[1] + c.bounds[3] / 2];
  const before = c.attrs.label ?? '';
  labelField = new InlineField(board, { at: mid, width: labelFieldWidth(c, board.view.zoom), text: before, label: 'Label', centre: true }, async (text) => {
    if (text === before) return true;          // the same value: no edit
    return (await host.edit('edit.setAttr', { fileId: w.fileId, circuitId: w.circuitId, ids: [c.id], attr: 'label', value: text }, 'Label')) !== null;
  }, () => { labelField = null; board.canvas.focus({ preventScroll: true }); });
}
