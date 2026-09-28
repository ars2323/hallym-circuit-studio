/* The tools in hand on the Canvas (N-07 Poke; N-08 Edit, Wire, Text and
   placing a part -- docs/interaction-parity.md).  The Canvas (canvas.ts)
   gives the tool every pointer event but panning, in circuit units, and
   the keys while it has the focus; the tool sends the engine what the
   student did (intents: edit.*, sim.poke …) and says what to show over the
   circuit (an Overlay).  The engine is the authority: nothing here changes
   the circuit; the result comes back as model.changed and sim.values.

   Poke (I-63 … I-76): a press and a release go to the part under the
   pointer, as Logisim's PokeTool gives them to the part's poker (a pin's
   bit, a clock, a button held down, a flip-flop, a register or a memory
   that then takes the keys, a Radix Probe); a wire shows its value in a
   box; a double click on a subcircuit's lens goes into that instance. */

import type { PokeResult, WindowMethod } from '../../main/protocol.ts';
import type { CanvasPointer, CanvasTool, CircuitCanvas } from '../canvas/canvas.ts';
import { KEYED, onMagnifier, pokeKey, pokePoint, pokeTarget, wireValueText } from '../canvas/poke.ts';

export type ToolName = 'Edit' | 'Poke';

// Where the Canvas is: the file, the circuit drawn (inside an instance: the instance's circuit).
export interface Where { fileId: string; circuitId: string }

export interface EditorHost {
  board: CircuitCanvas;
  call<T = unknown>(method: WindowMethod, params: Record<string, unknown>): Promise<T>;
  where(): Where | null;
  ready(): boolean;
  enter(componentId: string): void;            // into a subcircuit instance (its values)
  failed(command: string, e: unknown): void;   // a call the engine refused: the status bar's line
  toolChanged?(tool: ToolName): void;
}

export class Editor {
  tool: ToolName = 'Edit';
  private readonly host: EditorHost;
  private readonly poke: PokeTool;

  constructor(host: EditorHost) {
    this.host = host;
    this.poke = new PokeTool(host);
  }

  setTool(t: ToolName): void {
    if (t === this.tool) return;
    if (this.tool === 'Poke') this.poke.stop();
    this.tool = t;
    // Edit (until N-08's own): the Canvas's click selection and double click into a subcircuit (N-05)
    this.host.board.tool = t === 'Poke' ? this.poke : null;
    this.host.board.setOverlay(t === 'Poke' ? { magnifiers: true } : {});
    this.host.toolChanged?.(t);
  }

  // Another circuit or file is shown: what the tool held there goes.
  sceneChanged(): void {
    if (this.tool === 'Poke') this.poke.forget();
  }
}

/* The Poke tool. */
export class PokeTool implements CanvasTool {
  private readonly host: EditorHost;
  private pressedOn: { id: string; where: Where } | null = null;
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
      this.pressedOn = { id: c.id, where: w };
      this.show({});
      void this.send(w, c.id, e.at, 'press').then((r) => {
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
    void this.send(p.where, p.id, e.at, 'release').then((r) => {
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
