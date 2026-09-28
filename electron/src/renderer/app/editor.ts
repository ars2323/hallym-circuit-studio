/* The tools in hand on the Canvas (N-07 Poke; N-08 Edit, Wire, Text and
   placing a part -- docs/interaction-parity.md, D-145, D-146).  The Canvas
   (canvas.ts) gives the tool every pointer event but panning, in circuit
   units, and the keys while it has the focus; the tool sends the engine
   what the student did (intents: edit.*, sim.poke …) and says what to show
   over the circuit (an Overlay).  The engine is the authority: nothing
   here changes the circuit; the result comes back as model.changed,
   edit.selection and sim.values.

   The editing tools are in edit-tools.ts.  Poke (I-63 … I-76): a press
   and a release go to the part under the pointer, as Logisim's PokeTool
   gives them to the part's poker (a pin's bit, a clock, a button held
   down, a flip-flop, a register or a memory that then takes the keys, a
   Radix Probe); a wire shows its value in a box; a double click on a
   subcircuit's lens goes into that instance. */

import type { Component, EditResult, EditSelection, PokeResult, WindowMethod } from '../../main/protocol.ts';
import type { CanvasPointer, CanvasTool, CircuitCanvas } from '../canvas/canvas.ts';
import { KEYED, onMagnifier, pokeKey, pokePoint, pokeTarget, wireValueText } from '../canvas/poke.ts';
import { EditTool, editLabel, floatingOverlay, type Held, PlaceTool, TextTool, type ToolsHost, type Where, WireTool } from './edit-tools.ts';

export type { Held, Where } from './edit-tools.ts';
export type ToolName = 'Edit' | 'Poke' | 'Wire' | 'Text' | 'Place';

// The menu's editing commands (Edit › …, their keys: the window's, I-137..I-143).
export type MenuCommand = 'copy' | 'cut' | 'paste' | 'duplicate' | 'selectAll' | 'delete';

export interface EditorHost {
  board: CircuitCanvas;
  call<T = unknown>(method: WindowMethod, params: Record<string, unknown>): Promise<T>;
  where(): Where | null;
  ready(): boolean;
  enter(componentId: string): void;            // into a subcircuit instance (its values)
  failed(command: string, e: unknown): void;   // a call the engine refused: the status bar's line
  toolChanged?(tool: string): void;            // the tool in hand (the toolbar's name, or the part held)
  selectionChanged?(ids: string[]): void;      // the engine's selection in the circuit on show
  pinValue?(c: Component): void;               // a double click on an input pin (I-78)
}

export class Editor {
  tool: ToolName = 'Edit';
  private readonly host: EditorHost;
  readonly poke: PokeTool;
  readonly edit: EditTool;
  readonly wire: WireTool;
  readonly place: PlaceTool;
  readonly text: TextTool;
  private sel: EditSelection | null = null;
  private told = '';
  private readonly tools: ToolsHost;                     // the selection last told (hcs:selection), not told again

  constructor(host: EditorHost) {
    this.host = host;
    this.poke = new PokeTool(host);
    const tools: ToolsHost = {
      board: host.board,
      where: () => host.where(),
      ready: () => host.ready(),
      edit: async <T = EditResult>(method: WindowMethod, params: Record<string, unknown>, name: string): Promise<T | null> => {
        try {
          return await host.call<T>(method, params);
        } catch (e) {
          host.failed(name, e);
          return null;
        }
      },
      ask: async <T>(method: WindowMethod, params: Record<string, unknown>): Promise<T | null> => {
        try { return await host.call<T>(method, params); } catch { return null; }
      },
      selection: () => this.selection(),
      placed: () => this.setTool('Edit'),
      guess: (ids) => {
        host.board.setSelection(ids);
        this.tell(ids);
      },
      pinValue: (c) => host.pinValue?.(c),
    };
    this.tools = tools;
    this.edit = new EditTool(tools);
    this.wire = new WireTool(tools);
    this.place = new PlaceTool(tools);
    this.text = new TextTool(tools);
    host.board.tool = this.edit;
  }

  // The engine's selection, if it is in the circuit on show.
  // Parts pasted or duplicated and still floating in another circuit than the one about to be shown (or when going
  // inside an instance): the window drops them there first (edit.select, an edit: journaled, told), as Logisim's
  // Project.setCircuitState drops the selection before it changes circuit (D-146).
  floatingLeft(fileId: string, circuitId: string, path: string[]): EditSelection | null {
    const s = this.sel;
    return s && s.fileId === fileId && s.circuitId && s.floating.length > 0 && (s.circuitId !== circuitId || path.length > 0) ? s : null;
  }

  selection(): EditSelection | null {
    const s = this.sel, w = this.host.where();
    return s && w && s.fileId === w.fileId && s.circuitId === w.circuitId ? s : null;
  }

  // The name the toolbar and hcs:tool give the tool in hand.
  toolName(): string {
    return this.tool === 'Place' ? this.place.held?.name ?? 'Edit' : this.tool;
  }

  setTool(t: ToolName): void {
    if (t === this.tool && t !== 'Place') return;
    if (this.tool === 'Poke') this.poke.stop();
    if (this.tool === 'Text') this.text.stop();
    this.edit.cancel();
    this.wire.cancel();
    this.tool = t;
    const b = this.host.board;
    b.tool = { Edit: this.edit, Poke: this.poke, Wire: this.wire, Text: this.text, Place: this.place }[t] as CanvasTool;
    if (t === 'Poke') b.setOverlay({ magnifiers: true });
    else if (t === 'Edit') this.edit.show();
    else b.setOverlay({});
    if (t === 'Place') void this.place.refresh();
    this.host.toolChanged?.(this.toolName());
  }

  // A part to place (the toolbar's Pin, Tunnel, Probe; the Components list, the palette: hcs:place-tool).
  hold(p: Held): void {
    this.place.hold(p);
    this.setTool('Place');
  }

  // edit.selection from the engine.
  onSelection(s: EditSelection): void {
    this.sel = s;
    const mine = this.selection();
    this.host.board.setSelection(mine?.ids ?? []);
    if (this.tool === 'Edit') this.edit.show();
    const w = this.host.where();
    if (w && s.fileId === w.fileId) this.tell(mine?.ids ?? []);
  }

  // hcs:selection for the circuit on show, when it is not what was told last (a guess the engine confirms is not told twice).
  private tell(ids: string[]): void {
    const w = this.host.where();
    const k = `${w?.fileId} ${w?.circuitId} ${[...ids].sort().join(',')}`;
    if (k === this.told) return;
    this.told = k;
    this.host.selectionChanged?.(ids);
  }

  // Another circuit or file is shown: what the tool held there goes.
  sceneChanged(): void {
    if (this.tool === 'Poke') this.poke.forget();
    this.edit.cancel();
    this.host.board.setSelection(this.selection()?.ids ?? []);
    this.tell(this.selection()?.ids ?? []);   // another circuit on show: its selection (none, mostly) is told
    if (this.tool === 'Edit') this.edit.show();
    if (this.tool === 'Place') void this.place.refresh();
  }

  // The menu's commands on the selection (LayoutEditHandler); Paste takes the Edit tool (Logisim's).
  menu(cmd: MenuCommand): void {
    const w = this.host.where();
    if (!w || !this.host.ready()) return;
    const base = { fileId: w.fileId, circuitId: w.circuitId };
    const call = (method: WindowMethod, name: string, extra: Record<string, unknown> = {}) =>
      void this.host.call(method, { ...base, ...extra }).catch((e) => this.host.failed(name, e));
    this.forgetWire();
    switch (cmd) {
      case 'copy': call('edit.copy', 'Copy'); break;
      case 'cut': call('edit.cut', 'Cut'); break;
      case 'paste': this.setTool('Edit'); call('edit.paste', 'Paste'); break;
      case 'duplicate': call('edit.duplicate', 'Duplicate'); break;
      case 'selectAll': this.setTool('Edit'); call('edit.select', 'Select All', { all: true }); break;
      case 'delete': call('edit.delete', 'Delete'); break;
    }
  }

  // Another edit came in between (Undo, a menu command): Backspace no longer takes the wire back.
  forgetWire(): void {
    this.edit.lastWire = false;
    this.wire.lastWire = false;
  }

  // A part's label in place (F2's field; Quick Attributes' Label, N-10).
  editLabel(id: string): void {
    const c = this.host.board.scene?.components.get(id);
    if (c) void editLabel(this.tools, c);
  }

  // The floating paste as drawn (tests).
  floating(): ReturnType<typeof floatingOverlay> {
    return floatingOverlay({ selection: () => this.selection() } as ToolsHost);
  }
}

/* The Poke tool. */
export class PokeTool implements CanvasTool {
  private readonly host: EditorHost;
  private pressedOn: { id: string; where: Where; pressed: Promise<PokeResult | null> } | null = null;
  caretOn: { id: string; where: Where } | null = null;   // a poked part that takes keys

  constructor(host: EditorHost) {
    this.host = host;
  }

  private get board(): CircuitCanvas { return this.host.board; }

  private show(o: { caret?: string | null; valueBox?: { at: [number, number]; text: string; net: string | null } | null }): void {
    this.board.setOverlay({ magnifiers: true, caret: this.caretOn?.id ?? null, valueBox: null, ...o });
  }

  down(e: CanvasPointer): void {
    const w = this.host.where();
    const s = this.board.scene;
    if (!w || !s || !this.host.ready()) return;
    const c = pokeTarget(s.components.values(), e.at);
    if (c && (c.subcircuit !== undefined || c.appearance)) {
      // a subcircuit: only its lens does something (a double click, dbl below)
      this.drop(w);
      this.show({});
      return;
    }
    if (c) {
      // The engine moves its caret to the new part.  A part that takes keys has them at once: the keys
      // typed next go after this press (the engine answers in order), before its answer is back.
      this.caretOn = KEYED.has(c.name) ? { id: c.id, where: w } : null;
      this.show({});
      const pressed = this.send(w, c.id, e.at, 'press');
      this.pressedOn = { id: c.id, where: w, pressed };
      void pressed.then((r) => {
        if (!r?.poked && this.caretOn?.id === c.id) { this.caretOn = null; this.show({}); }
      });
      return;
    }
    // a wire: its value in the yellow box, its net shown (I-74); nothing: the caret goes
    this.drop(w);
    const wire = this.board.wireAt(e.at);
    if (wire) {
      const n = s.wireNet(wire);
      this.show({ valueBox: { at: pokePoint(e.at), text: wireValueText(n ? s.values.get(n.id) : undefined, n?.width ?? 1), net: n?.id ?? null } });
    } else {
      this.show({});
    }
  }

  up(e: CanvasPointer): void {
    const p = this.pressedOn;
    this.pressedOn = null;
    if (!p) return;
    // after the press's answer: a press the engine refused (an input pin inside an instance, I-64) has no release,
    // so its refusal is told once
    void p.pressed.then((pr) => (pr ? this.send(p.where, p.id, e.at, 'release') : null)).then((r) => {
      if (r && !r.caret && this.caretOn?.id === p.id) { this.caretOn = null; this.show({}); }
    });
  }

  dbl(e: CanvasPointer): void {
    const s = this.board.scene;
    if (!s) return;
    const c = pokeTarget(s.components.values(), e.at);
    if (c && onMagnifier(c, e.at)) this.host.enter(c.id);
  }

  key(e: KeyboardEvent): boolean {
    if (!this.caretOn) return false;
    const k = pokeKey(e);
    if (k === null) return false;
    const w = this.caretOn.where;
    void this.host.call('sim.pokeKey', { fileId: w.fileId, key: k }).catch((err) => this.host.failed('Poke', err));
    return true;
  }

  // A poked memory's Space is the next address (I-70): no Space-drag panning while it has the keys.
  wantsSpace(): boolean { return this.caretOn !== null; }

  cursor(): string { return 'pointer'; }

  // The tool is put down: Logisim's PokeTool drops its caret (removeCaret).
  stop(): void {
    const w = this.caretOn?.where ?? this.host.where();
    if (w) this.drop(w);
    this.pressedOn = null;
  }

  forget(): void {
    this.caretOn = null;
    this.pressedOn = null;
    this.show({});
  }

  private drop(w: Where): void {
    if (!this.caretOn) return;
    this.caretOn = null;
    if (this.host.ready()) void this.host.call('sim.pokeStop', { fileId: w.fileId }).catch(() => {});
  }

  private async send(w: Where, componentId: string, at: [number, number], action: 'press' | 'release'): Promise<PokeResult | null> {
    try {
      return await this.host.call<PokeResult>('sim.poke', { fileId: w.fileId, circuitId: w.circuitId, componentId, at: pokePoint(at), action });
    } catch (err) {
      this.host.failed('Poke', err);
      return null;
    }
  }
}
