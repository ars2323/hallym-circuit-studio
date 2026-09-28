/* The circuit Canvas (N-05, D-137): draws the scene (scene.ts) with Canvas
   2D -- a dot grid, the wires in their value colours (buses wider), jumps
   and connection dots, the parts from the registry (registry.ts), and the
   chips around them (labels.ts) -- and lets the student zoom (25–400 %,
   smoothly) and pan.  Hover, selection and keyboard focus have their own
   looks.  Editing is item N-08: here a click selects and a double click on
   a subcircuit asks the host to go inside.

   Drawing is done in one pass per animation frame, only when something
   changed; parts outside the view are skipped; each part's shapes are
   kept until its values change, and their paths as Path2D. */

import type { Component } from '../../main/protocol.ts';
import { h } from '../shared/dom.ts';
import type { CanvasPointer, CanvasTool, Overlay } from './input.ts';
import { magnifier, wireValueText } from './poke.ts';
import { type Chip, layoutChips, type Measure, portNames, sceneTunnelColors, tunnelRoom } from './labels.ts';
import { color, cssFont, fittedSize, FONTS, type Look, MIN_TEXT_PX, paintPort, paintText, portPx, strokeUnits, wirePx } from './paint.ts';
import type { PartState } from './parts/common.ts';
import { FALLBACK, rendererFor } from './registry.ts';
import type { Scene } from './scene.ts';
import { byArea, split, type Split } from './layers.ts';
import { type Box, boxesMeet, boxUnion, type Shape, shapeBox, type TextShape } from './shapes.ts';
import { shapesToSvg, svgDocument, svgElement, wireSvg } from './svg.ts';
import { type Theme, THEME, themeFrom, valueColor, valueKind } from './tokens.ts';
import { between, clampZoom, fit, percent, step, toCircuit, toScreen, type View, visible, wheelZoom, zoomAt } from './view.ts';
import { dotUnits, jumpUnits, type WireMarks, wireMarks } from './wires.ts';

/* An overlay on the circuit (N-15, D-151: overlays/ -- influence, Signal Flow, active path, field colours,
   bus values, signal groups, area memos, a net's highlight).  It draws at three points of a frame, in
   circuit units (the view's transform is set): under the wires (after the grid: bands around wires, memos),
   over the wires (before the parts: what must stay under the parts' bodies), and over the parts and chips
   (before the selection's and hover's outlines).  An overlay never changes the scene or the model. */
export interface OverlayDraw {
  ctx: CanvasRenderingContext2D;
  canvas: CircuitCanvas;
  scene: Scene;
  zoom: number;
  shown: Box;                  // the circuit area drawn (the view and a margin)
  look: Look;
  print: boolean;              // the picture only (no grid, no hover): overlays that are not the circuit's stay out
}
export interface CanvasOverlay {
  under?(d: OverlayDraw): void;
  overWires?(d: OverlayDraw): void;
  over?(d: OverlayDraw): void;
  svg?(canvas: CircuitCanvas): string[];      // in the picture export, behind everything (area memos)
  animating?(): boolean;                      // wants the next frame (Signal Flow)
  sceneChanged?(scene: Scene | null): void;   // another circuit (or none) is shown
}

export interface CanvasHost {
  onView?(view: View): void;                       // the zoom changed (the status bar's display)
  onEnter?(componentId: string): void;             // double click on a subcircuit instance (no tool in hand)
  onSelect?(ids: string[]): void;
}

export type { CanvasPointer, CanvasTool, Overlay } from './input.ts';

interface Cached { state: string; shapes: Shape[]; layers: Split }

export class CircuitCanvas {
  readonly root: HTMLDivElement;
  readonly canvas: HTMLCanvasElement;
  private readonly ctx: CanvasRenderingContext2D;
  private readonly tip: HTMLDivElement;
  private readonly crumbs: HTMLDivElement;
  scene: Scene | null = null;
  view: View = { x: 0, y: 0, zoom: 1 };
  theme: Theme = { ...THEME };
  busWidths = true;
  private width = 0;
  private height = 0;
  private dpr = 1;
  private marks: WireMarks | null = null;
  private chips: Chip[] = [];
  private tunnelColors = new Map<string, string>();
  private readonly growth = new Map<string, number>();   // tunnel id -> how far its tag may grow (labels.ts tunnelRoom)
  private laidOut = -1;                       // the model version the marks and chips are for
  private readonly cache = new WeakMap<Component, Cached>();
  private readonly paths = new WeakMap<Shape, Path2D>();
  private hovered: string | null = null;      // a component id
  private hoveredWire: string | null = null;
  private selected = new Set<string>();
  // "Show this place" (a message, D-143; a name found, N-12): the parts, wires and nets marked until the next selection
  private marked: { components: Set<string>; wires: Set<string>; nets: Set<string>; tone: 'error' | 'find' } | null = null;
  private dirty = true;
  private raf = 0;
  private anim: { from: View; to: View; start: number; ms: number; about?: [number, number] } | null = null;
  private grid: { key: string; canvas: OffscreenCanvas | HTMLCanvasElement } | null = null;
  private drag: { x: number; y: number; view: View } | null = null;
  private spaceDown = false;
  private fitted = false;
  // The tool in hand (app/editor.ts, N-07/N-08): every pointer event but panning, and the keys. None: N-05's own click and double click.
  tool: CanvasTool | null = null;
  private pressed = -1;                       // the pointer id of a button the tool got (down .. up)
  // What the tool shows over the circuit (input.ts).
  overlay: Overlay = {};
  lastFrameMs = 0;                           // how long the last frame took to draw (the measurement)
  private readonly overlays: CanvasOverlay[] = [];
  // what the last frame drew: the tests and tools wait on settled(), never on a guess of how long a frame takes
  private drawnAt: { scene: Scene; model: number; values: number; view: View; width: number; height: number } | null = null;
  private changed = true;                    // invalidated for something other than an overlay's animation, not drawn yet
  paused = false;                            // the measurement draws the other way meanwhile (tools/measure-canvas.ts)

  private readonly host: CanvasHost;

  constructor(host: CanvasHost = {}) {
    this.host = host;
    this.canvas = h('canvas', { class: 'circuit', tabindex: '0', 'aria-label': 'Circuit' }) as HTMLCanvasElement;
    this.ctx = this.canvas.getContext('2d', { alpha: false })!;
    this.tip = h('div', { class: 'canvas-tip', hidden: true }) as HTMLDivElement;
    this.crumbs = h('div', { class: 'canvas-crumbs', hidden: true }) as HTMLDivElement;
    this.root = h('div', { class: 'canvas-view' }, this.canvas, this.crumbs, this.tip) as HTMLDivElement;
    new ResizeObserver(() => this.resize()).observe(this.root);
    this.listen();
    (window as unknown as { __hcsCanvas?: CircuitCanvas }).__hcsCanvas = this;
  }

  // ---- what to draw ---------------------------------------------------------------------------

  setScene(scene: Scene | null, view?: View): void {
    const other = scene !== this.scene;
    this.scene = scene;
    this.laidOut = -1;
    this.hovered = null;
    this.hoveredWire = null;
    this.selected.clear();
    this.marked = null;
    this.tip.hidden = true;
    if (view) { this.view = view; this.fitted = true; } else this.fitted = false;
    this.readTheme();
    if (!view && this.width > 0) this.fitView(false);
    if (other) {
      for (const o of this.overlays) o.sceneChanged?.(scene);
      this.selectionChanged();
    }
    this.invalidate();
  }

  // ---- overlays (N-15) ---------------------------------------------------------------------------

  addOverlay(o: CanvasOverlay): void {
    this.overlays.push(o);
    this.invalidate();
  }

  // The parts selected now (the overlays' commands start from them).
  selectedIds(): string[] { return [...this.selected]; }

  // The host sends it on as hcs:selection (app.ts, tool-events.ts: one sender, with the instance path).
  private selectionChanged(): void {
    this.host.onSelect?.([...this.selected]);
  }

  /* The Canvas shows what it has (for the tests and tools): the scene on the page at the Canvas's size,
     its first view chosen (a scene set while the Canvas was off the page -- another file's scene still
     loading -- is fitted only when its size is known, ResizeObserver), no frame to come, and the last
     frame drew this scene's model and values at this view and size (an overlay's own animation,
     N-15, draws on and does not count).  Until then a point computed from
     `view` may not be where the part will be drawn, and a screenshot may show the frame before. */
  settled(): boolean {
    const s = this.scene, d = this.drawnAt, v = this.view;
    if (!s || !d || !this.root.isConnected || this.width === 0 || !this.fitted || this.changed || this.anim || this.paused) return false;
    return d.scene === s && d.model === s.modelVersion && d.values === s.valueVersion && d.width === this.width && d.height === this.height
      && d.view.x === v.x && d.view.y === v.y && d.view.zoom === v.zoom;
  }

  // After the scene changed (model or values): draw again.
  // (content false: only an overlay's animation goes on -- settled() stays true)
  invalidate(content = true): void {
    if (content) this.changed = true;
    this.dirty = true;
    if (!this.raf) this.raf = requestAnimationFrame((t) => this.frame(t));
  }

  setCrumbs(names: string[], onPick: (index: number) => void): void {
    this.crumbs.hidden = names.length < 2;
    this.crumbs.replaceChildren(...names.flatMap((n, i) => {
      const last = i === names.length - 1;
      const el = last ? h('span', { class: 'mono here' }, n) : h('button', { type: 'button', class: 'mono' }, n);
      if (!last) el.addEventListener('click', () => onPick(i));
      return i ? [h('span', { class: 'sep', 'aria-hidden': 'true' }, '›'), el] : [el];
    }));
  }

  readTheme(): void {
    const css = getComputedStyle(document.documentElement);
    this.theme = themeFrom((v) => css.getPropertyValue(v));
  }

  // ---- zoom and pan -------------------------------------------------------------------------------

  setView(v: View, animate = false, about?: [number, number]): void {
    const to = { ...v, zoom: clampZoom(v.zoom) };
    if (animate) {
      this.anim = { from: { ...this.view }, to, start: performance.now(), ms: 140, about };
    } else {
      this.anim = null;
      this.view = to;
    }
    this.host.onView?.(to);
    this.invalidate();
  }

  zoomStep(dir: 1 | -1): void {
    const about: [number, number] = [this.width / 2, this.height / 2];
    const target = this.anim?.to ?? this.view;
    this.setView(zoomAt(target, step(target.zoom, dir), about), true, about);
  }

  zoomTo(zoom: number): void {
    const about: [number, number] = [this.width / 2, this.height / 2];
    this.setView(zoomAt(this.view, zoom, about), true, about);
  }

  // Fit the whole circuit (its parts, wires and chips), centred (v1 S-10).
  fitView(animate = true): void {
    if (!this.scene || this.width === 0) return;
    this.layout();
    let b = this.scene.extent();
    for (const c of this.chips) b = { x0: Math.min(b.x0, c.box.x0), y0: Math.min(b.y0, c.box.y0), x1: Math.max(b.x1, c.box.x1), y1: Math.max(b.y1, c.box.y1) };
    this.fitted = true;
    this.setView(fit(b, this.width, this.height), animate);
  }

  private resize(): void {
    const r = this.root.getBoundingClientRect();
    const dpr = window.devicePixelRatio || 1;
    if (r.width === this.width && r.height === this.height && dpr === this.dpr) return;
    this.width = r.width;
    this.height = r.height;
    this.dpr = dpr;
    this.canvas.width = Math.max(1, Math.round(r.width * dpr));
    this.canvas.height = Math.max(1, Math.round(r.height * dpr));
    this.canvas.style.width = `${r.width}px`;
    this.canvas.style.height = `${r.height}px`;
    this.grid = null;
    if (!this.fitted && this.scene && this.width > 0) this.fitView(false);
    this.invalidate();
  }

  // ---- the tool's side --------------------------------------------------------------------------------

  setOverlay(o: Overlay): void {
    this.overlay = o;
    this.invalidate();
  }

  // The selection to draw (the editor's, from the engine; N-08): component and wire ids.
  setSelection(ids: Iterable<string>): void {
    this.selected = new Set(ids);
    this.invalidate();
  }

  selection(): string[] { return [...this.selected]; }

  // A pointer event in circuit units, for the tool.
  pointerOf(e: MouseEvent, pressed = this.pressed >= 0): CanvasPointer {
    const s = this.point(e);
    return {
      at: toCircuit(this.view, s), screen: s, button: e.button, shift: e.shiftKey, ctrl: e.ctrlKey || e.metaKey, alt: e.altKey,
      detail: e.detail, pressed,
    };
  }

  // The pointer's circuit point now (the last move over the Canvas), or null.
  lastPointer: CanvasPointer | null = null;

  // ---- input --------------------------------------------------------------------------------------------

  private point(e: MouseEvent): [number, number] {
    const r = this.canvas.getBoundingClientRect();
    return [e.clientX - r.left, e.clientY - r.top];
  }

  private listen(): void {
    const c = this.canvas;
    c.addEventListener('wheel', (e) => {
      e.preventDefault();
      const unit = e.deltaMode === 1 ? 16 : e.deltaMode === 2 ? this.height : 1;
      if (e.ctrlKey || e.metaKey) {
        this.anim = null;
        this.setView(zoomAt(this.view, wheelZoom(this.view.zoom, e.deltaY * unit), this.point(e)));
      } else {
        const dx = (e.shiftKey && !e.deltaX ? e.deltaY : e.deltaX) * unit, dy = (e.shiftKey ? 0 : e.deltaY) * unit;
        this.setView({ ...this.view, x: this.view.x + dx / this.view.zoom, y: this.view.y + dy / this.view.zoom });
      }
    }, { passive: false });
    c.addEventListener('pointerdown', (e) => {
      c.focus({ preventScroll: true });
      if (e.button === 1 || (e.button === 0 && this.spaceDown)) {
        // the middle button pans (and never scrolls by itself, I-208); so does any button with Space held (I-125)
        e.preventDefault();
        this.drag = { x: e.clientX, y: e.clientY, view: { ...this.view } };
        c.setPointerCapture(e.pointerId);
        c.classList.add('panning');
        return;
      }
      if (this.tool) {
        if (e.button !== 0) return;           // the right button is the context menu's (N-10)
        if (this.marked) { this.marked = null; this.invalidate(); }   // a new press clears what a message marked
        this.pressed = e.pointerId;
        c.setPointerCapture(e.pointerId);
        this.hover(null);                     // a gesture going on shows no hover and no tooltip (Logisim's: none while dragging)
        this.tool.down?.(this.pointerOf(e, true));
        this.updateCursor();
        return;
      }
      if (e.button === 0) this.click(this.point(e), e.shiftKey || e.ctrlKey || e.metaKey);
    });
    c.addEventListener('pointermove', (e) => {
      if (this.drag) {
        const d = this.drag;
        this.setView({ ...d.view, x: d.view.x - (e.clientX - d.x) / d.view.zoom, y: d.view.y - (e.clientY - d.y) / d.view.zoom });
        return;
      }
      const p = this.pointerOf(e);
      this.lastPointer = p;
      if (this.pressed < 0) this.hover(this.point(e), e);
      this.tool?.move?.(p);
      this.updateCursor();
    });
    const end = (e: PointerEvent) => {
      if (c.hasPointerCapture(e.pointerId)) c.releasePointerCapture(e.pointerId);
      if (this.drag) {
        this.drag = null;
        c.classList.remove('panning');
        return;
      }
      if (this.pressed === e.pointerId) {
        this.pressed = -1;
        this.tool?.up?.(this.pointerOf(e, false));
        this.updateCursor();
      }
    };
    c.addEventListener('pointerup', end);
    c.addEventListener('pointercancel', end);
    // The pointer coming over the Canvas gives it the keys (Logisim's tools: requestFocusInWindow, I-02) -- not while
    // a field is being typed in (a browser would take them from it at the first pass of the mouse) or a dialog is open
    c.addEventListener('pointerenter', () => {
      const a = document.activeElement as HTMLElement | null;
      const typing = !!a && (a.tagName === 'INPUT' || a.tagName === 'TEXTAREA' || a.tagName === 'SELECT' || a.isContentEditable);
      if (!typing && !document.querySelector('dialog[open]')) c.focus({ preventScroll: true });
    });
    c.addEventListener('pointerleave', () => {
      if (this.drag || this.pressed >= 0) return;
      this.hover(null);
      this.lastPointer = null;
      this.tool?.leave?.();
    });
    c.addEventListener('dblclick', (e) => {
      if (this.tool) { this.tool.dbl?.(this.pointerOf(e, false)); return; }
      const id = this.partAt(toCircuit(this.view, this.point(e)));
      const p = id ? this.scene?.components.get(id) : undefined;
      if (p && (p.appearance || p.subcircuit !== undefined)) this.host.onEnter?.(p.id);
    });
    // The browser's own context menu never shows over the circuit (I-211; the circuit's menu is N-10's).
    c.addEventListener('contextmenu', (e) => e.preventDefault());
    c.addEventListener('keydown', (e) => {
      if (e.isComposing) return;              // Hangul being composed is not a key to act on (I-212)
      if (this.tool?.key?.(e)) { e.preventDefault(); return; }
      if (e.key === ' ' && !(this.tool?.wantsSpace?.() ?? false)) { this.spaceDown = true; c.classList.add('grab'); e.preventDefault(); }
      if (this.tool) return;
      const pan = 40 / this.view.zoom;
      const move: Record<string, [number, number]> = { ArrowLeft: [-pan, 0], ArrowRight: [pan, 0], ArrowUp: [0, -pan], ArrowDown: [0, pan] };
      if (move[e.key] && !e.ctrlKey && !e.metaKey) {
        e.preventDefault();
        this.setView({ ...this.view, x: this.view.x + move[e.key][0], y: this.view.y + move[e.key][1] });
      }
      if (e.key === 'Escape' && this.selected.size) { this.selected.clear(); this.selectionChanged(); this.invalidate(); }
    });
    c.addEventListener('keyup', (e) => {
      if (e.key === ' ') { this.spaceDown = false; c.classList.remove('grab'); }
      this.tool?.keyup?.(e);
    });
  }

  private updateCursor(): void {
    const want = this.drag || this.spaceDown ? '' : this.tool?.cursor?.() ?? '';
    if (this.canvas.style.cursor !== want) this.canvas.style.cursor = want;
  }

  // The keys the window routes here (app.ts): Ctrl+= / Ctrl++ / Ctrl+− / Ctrl+0.
  zoomKey(e: KeyboardEvent): boolean {
    if (!(e.ctrlKey || e.metaKey) || e.altKey) return false;
    if (e.key === '=' || e.key === '+') { this.zoomStep(1); return true; }
    if (e.key === '-' || e.key === '_') { this.zoomStep(-1); return true; }
    if (e.key === '0' || e.code === 'Numpad0') { this.fitView(); return true; }
    if (e.code === 'Digit1' || e.code === 'Numpad1') { this.zoomTo(1); return true; }   // 100 % about the middle (v1 D-028, I-123)
    return false;
  }

  // The view fitted to a box (the selection's bounds, v1 F, I-124): at most 200 %, as the whole circuit's fit.
  fitBox(b: Box): void {
    this.setView(fit(b, this.width, this.height), true);
  }

  partAt(p: [number, number]): string | null {
    if (!this.scene) return null;
    const pad = 2;
    let hit: string | null = null;
    for (const c of this.scene.components.values()) {
      const [x, y, w, hh] = c.bounds;
      if (p[0] >= x - pad && p[0] <= x + w + pad && p[1] >= y - pad && p[1] <= y + hh + pad) hit = c.id;
    }
    return hit;
  }

  wireAt(p: [number, number]): string | null {
    if (!this.scene) return null;
    const reach = 5 / this.view.zoom;
    for (const w of this.scene.wires.values()) {
      const x0 = Math.min(w.a[0], w.b[0]) - reach, x1 = Math.max(w.a[0], w.b[0]) + reach;
      const y0 = Math.min(w.a[1], w.b[1]) - reach, y1 = Math.max(w.a[1], w.b[1]) + reach;
      if (p[0] >= x0 && p[0] <= x1 && p[1] >= y0 && p[1] <= y1) return w.id;
    }
    return null;
  }

  private click(at: [number, number], add: boolean): void {
    this.marked = null;   // a new selection clears what a message marked
    const id = this.partAt(toCircuit(this.view, at));
    if (!add) this.selected.clear();
    if (id) { if (add && this.selected.has(id)) this.selected.delete(id); else this.selected.add(id); }
    this.selectionChanged();
    this.invalidate();
  }

  private hover(at: [number, number] | null, e?: MouseEvent): void {
    const p = at ? toCircuit(this.view, at) : null;
    const part = p ? this.partAt(p) : null;
    const wire = p && !part ? this.wireAt(p) : null;
    if (part !== this.hovered || wire !== this.hoveredWire) {
      this.hovered = part;
      this.hoveredWire = wire;
      this.invalidate();
    }
    const text = this.tipText();
    if (!text || !at || !e) { this.tip.hidden = true; return; }
    this.tip.textContent = text;
    this.tip.hidden = false;
    const x = Math.min(at[0] + 14, this.width - this.tip.offsetWidth - 4);
    const y = at[1] + 18 + this.tip.offsetHeight > this.height ? at[1] - this.tip.offsetHeight - 8 : at[1] + 18;
    this.tip.style.transform = `translate(${Math.max(4, x)}px, ${Math.max(4, y)}px)`;
  }

  // What the pointer is over, in a line (v1 B-07): a part's name and label, a wire's width and value.
  tipText(): string | null {
    const s = this.scene;
    if (!s) return null;
    if (this.hovered) {
      const c = s.components.get(this.hovered);
      if (!c) return null;
      const label = c.attrs.label ? ` "${c.attrs.label}"` : '';
      // a splitter's arm names (N-12): its chips show them only where they stand free
      const arms = c.name === 'Splitter' && c.ext?.arms?.some(Boolean) ? ` · ${c.ext.arms.map((n) => n || '–').join(' ')}` : '';
      return `${c.name}${label}${arms}`;
    }
    if (this.hoveredWire) {
      const n = s.wireNet(this.hoveredWire);
      if (!n) return null;
      const v = s.values.get(n.id);
      const bits = `${n.width} bit${n.width === 1 ? '' : 's'}`;
      if (!v) return bits;
      const kind = valueKind(v);
      const say = kind === 'float' ? 'x (floating)' : kind === 'error' ? 'E (error)' : n.width > 4 && /^[01]+$/.test(v) ? `0x${BigInt(`0b${v}`).toString(16)}` : v;
      return `${bits} · ${say}`;
    }
    return null;
  }

  // ---- drawing ------------------------------------------------------------------------------------------------

  private layout(): void {
    const s = this.scene;
    if (!s || this.laidOut === s.modelVersion) return;
    this.marks = wireMarks(s);
    this.tunnelColors = sceneTunnelColors(s);
    this.growth.clear();
    for (const c of s.components.values()) if (c.lib === 'Wiring' && c.name === 'Tunnel') this.growth.set(c.id, tunnelRoom(s, this.marks, c));
    this.laidOut = s.modelVersion;   // (before the chips: the extents below draw the parts with this layout)
    this.chips = layoutChips(s, this.marks, this.measure, (c) => this.extentOf(c));
  }

  readonly measure: Measure = (text, font, size, weight) => {
    this.ctx.font = `${weight} 100px ${FONTS[font]}`;
    return (this.ctx.measureText(text).width / 100) * size;
  };

  stateOf(c: Component): PartState {
    const s = this.scene!;
    return {
      value: (i) => s.portValue(c.id, i),
      body: s.bodies.get(c.id),
      tunnelColor: c.name === 'Tunnel' ? this.tunnelColors.get(c.attrs.label ?? '') : undefined,
      measure: this.measure,
      grow: this.growth.get(c.id),
    };
  }

  // Where a part is drawn: its bounds, and past them where a shape grows (a tunnel's long name).
  extentOf(c: Component): Box {
    const [x, y, w, h] = c.bounds;
    let b: Box = { x0: x, y0: y, x1: x + w, y1: y + h };
    for (const sh of this.shapesOf(c)) if (sh.grows) b = boxUnion(b, shapeBox(sh));
    return b;
  }

  // The part's shapes for its state now (kept while the state is the same).
  shapesOf(c: Component): Shape[] {
    const st = this.stateOf(c);
    const key = c.ports.map((q) => st.value(q.i) ?? '').join('|') + (st.body ? JSON.stringify(st.body) : '') + (st.tunnelColor ?? '') + (st.grow ?? '');
    const hit = this.cache.get(c);
    if (hit && hit.state === key) return hit.shapes;
    let shapes: Shape[];
    try {
      shapes = rendererFor(c).draw(c, st);
    } catch (e) {
      // a part the renderer could not draw (an attribute it did not expect): the default look
      console.warn(`[canvas] ${c.lib}/${c.name}: ${String(e)}`);
      shapes = FALLBACK.draw(c, st);
    }
    this.cache.set(c, { state: key, shapes, layers: split(shapes) });
    return shapes;
  }

  // The part's shapes in the two painting passes (layers.ts), kept with its shapes.
  layersOf(c: Component): Split {
    this.shapesOf(c);
    return this.cache.get(c)!.layers;
  }

  private frame(now: number): void {
    this.raf = 0;
    if (this.paused) return;
    if (this.anim) {
      const a = this.anim;
      const t = Math.min(1, (now - a.start) / a.ms);
      this.view = a.about ? zoomAt(a.from, a.from.zoom * Math.pow(a.to.zoom / a.from.zoom, 1 - Math.pow(1 - t, 3)), a.about) : between(a.from, a.to, t);
      if (t >= 1) { this.view = a.to; this.anim = null; }
      this.dirty = true;
    }
    if (!this.dirty) return;
    this.dirty = false;
    this.changed = false;
    const t0 = performance.now();
    this.draw();
    this.lastFrameMs = performance.now() - t0;
    const s = this.scene;
    this.drawnAt = s ? { scene: s, model: s.modelVersion, values: s.valueVersion, view: { ...this.view }, width: this.width, height: this.height } : null;
    if (this.anim) this.invalidate();
    else if (this.overlays.some((o) => o.animating?.())) this.invalidate(false);
  }

  // print: the picture only -- no grid, no hover, no selection, no port names (what exportSvg writes).
  draw(print = false): void {
    const ctx = this.ctx, v = this.view, dpr = this.dpr;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.fillStyle = this.theme.paper;
    ctx.fillRect(0, 0, this.width, this.height);
    if (!print) this.drawGrid();
    const s = this.scene;
    if (!s) return;
    this.layout();
    const z = v.zoom;
    ctx.setTransform(dpr * z, 0, 0, dpr * z, -v.x * z * dpr, -v.y * z * dpr);
    ctx.lineJoin = 'round';
    const view = visible(v, this.width, this.height);
    const margin = 40;
    const shown: Box = { x0: view.x0 - margin, y0: view.y0 - margin, x1: view.x1 + margin, y1: view.y1 + margin };
    const look: Look = { theme: this.theme, zoom: z, minText: MIN_TEXT_PX };
    const od: OverlayDraw = { ctx, canvas: this, scene: s, zoom: z, shown, look, print };
    for (const o of this.overlays) o.under?.(od);

    // selection behind (a tint), hover and selection outlines after the parts
    for (const id of print ? [] : this.selected) {
      const c = s.components.get(id);
      if (c) this.outline(c, this.theme.selectTint, null, 0);
      const w = c ? undefined : s.wires.get(id);
      if (w) this.wireHalo([w], this.theme.selectTint, 8);
    }
    if (this.marked && !print) this.drawMarked(true);
    this.drawWires(shown);
    for (const o of this.overlays) o.overWires?.(od);
    // parts: every body fill first, then outlines, marks and text (layers.ts: no part hidden by another's fill)
    const partsShown: Component[] = [];
    for (const c of s.components.values()) {
      const [x, y, w, hh] = c.bounds;
      if (x > shown.x1 || y > shown.y1 || x + w < shown.x0 || y + hh < shown.y0) continue;
      partsShown.push(c);
    }
    const ordered = byArea(partsShown);
    for (const c of ordered) this.paintShapes(c, this.layersOf(c).base, look);
    for (const c of ordered) this.paintShapes(c, this.layersOf(c).top, look);
    this.drawDots(shown);
    this.drawChips(shown, look);
    // port names: the hovered part's, or every part's from 200 % (v1 S-06)
    const taken = this.chips.filter((ch) => boxesMeet(ch.box, shown)).map((ch) => ch.box);
    const named = print ? [] : z >= 2 ? partsShown : this.hovered ? partsShown.filter((c) => c.id === this.hovered) : [];
    for (const c of named) {
      const written = new Set(this.shapesOf(c).flatMap((sh) => (sh.k === 'text' ? [sh.text] : [])));
      const [bx, by, bw, bh] = c.bounds;
      const near = this.marks!.segments.filter((g) => Math.max(g.a[0], g.b[0]) >= bx - 60 && Math.min(g.a[0], g.b[0]) <= bx + bw + 60
        && Math.max(g.a[1], g.b[1]) >= by - 60 && Math.min(g.a[1], g.b[1]) <= by + bh + 60);
      for (const n of portNames(c, z, this.measure, taken, written, near)) {
        paintText(ctx, { k: 'text', x: n.x, y: n.y, text: n.text, font: 'ui', size: n.size, weight: 500, anchor: n.anchor, baseline: n.baseline, fill: 'tealText' }, { ...look, minText: 0 });
      }
    }
    if (print) return;
    for (const o of this.overlays) o.over?.(od);
    for (const id of this.selected) {
      const c = s.components.get(id);
      if (c) this.outline(c, null, this.theme.select, 2);
      const w = c ? undefined : s.wires.get(id);
      if (w) this.wireEnds(w);
    }
    if (this.hovered && !this.selected.has(this.hovered)) {
      const c = s.components.get(this.hovered);
      if (c) this.outline(c, null, this.theme.hover, 1.5);
    }
    if (this.hoveredWire) this.drawNetHalo(this.hoveredWire);
    if (this.marked) this.drawMarked(false);
    this.drawOverlay(partsShown);
  }

  // ---- what the tool shows (input.ts Overlay) ------------------------------------------------------------

  // A band along wires (a selected wire's tint), px wide on screen.
  private wireHalo(wires: { a: [number, number]; b: [number, number] }[], stroke: string, px: number): void {
    const ctx = this.ctx, z = this.view.zoom;
    ctx.beginPath();
    for (const w of wires) { ctx.moveTo(w.a[0], w.a[1]); ctx.lineTo(w.b[0], w.b[1]); }
    ctx.strokeStyle = stroke;
    ctx.lineWidth = px / z;
    ctx.lineCap = 'round';
    ctx.stroke();
  }

  // A selected wire's ends: small squares, as Logisim marks a selected wire's handles.
  private wireEnds(w: { a: [number, number]; b: [number, number] }): void {
    const ctx = this.ctx, z = this.view.zoom, r = 3 / z;
    ctx.fillStyle = this.theme.select;
    for (const p of [w.a, w.b]) ctx.fillRect(p[0] - r, p[1] - r, 2 * r, 2 * r);
  }

  // The editing tools' pictures (N-08): what is dragged or placed, the rectangle, the wire being drawn.
  private drawGestures(): void {
    const o = this.overlay, ctx = this.ctx, z = this.view.zoom, t = this.theme;
    const look: Look = { theme: t, zoom: z, minText: MIN_TEXT_PX };
    if (o.ghost) {
      const g = o.ghost;
      ctx.save();
      ctx.translate(g.dx, g.dy);
      ctx.globalAlpha = g.look === 'place' ? 0.5 : g.look === 'move' ? 0.55 : 0.75;
      this.wireHalo(g.wires, t.ink2, 3);
      for (const c of g.parts) {
        let shapes: Shape[];
        try { shapes = rendererFor(c).draw(c, this.stateOf(c)); } catch { shapes = FALLBACK.draw(c, this.stateOf(c)); }
        this.paintShapes(c, shapes, look);
      }
      ctx.globalAlpha = 1;
      // a paste or a duplicate not yet dropped into the circuit stays selected; what is dragged, grey (Logisim)
      if (g.look !== 'place') for (const c of g.parts) this.outline(c, null, g.look === 'float' ? t.select : t.ink2, 1.5);
      ctx.restore();
    }
    if (o.moveWires) {
      // MoveGesture's wires while dragging (grey, thick) and the points it could not connect (red), I-24
      this.wireHalo(o.moveWires.added.map(([a, b]) => ({ a, b })), 'rgba(90,100,114,0.7)', 3);
      ctx.fillStyle = t.error;
      for (const p of o.moveWires.unconnected) { ctx.beginPath(); ctx.arc(p[0], p[1], 3 / z + 1, 0, Math.PI * 2); ctx.fill(); }
    }
    if (o.wire && o.wire.length >= 2) {
      // the wire being drawn: black, 3 px (WiringTool.draw)
      ctx.beginPath();
      o.wire.forEach(([x, y], i) => (i ? ctx.lineTo(x, y) : ctx.moveTo(x, y)));
      ctx.strokeStyle = t.ink;
      ctx.lineWidth = 3 / z;
      ctx.lineCap = 'square';
      ctx.stroke();
    }
    if (o.rubber) {
      const b = o.rubber;
      ctx.fillStyle = 'rgba(0,85,165,0.10)';
      ctx.fillRect(b.x0, b.y0, b.x1 - b.x0, b.y1 - b.y0);
      ctx.strokeStyle = t.select;
      ctx.lineWidth = 1 / z;
      ctx.strokeRect(b.x0, b.y0, b.x1 - b.x0, b.y1 - b.y0);
    }
    if (o.dot) {
      // a wiring point under the pointer: the green circle, radius 5 (EditTool.draw, I-44)
      ctx.beginPath();
      ctx.arc(o.dot[0], o.dot[1], 5, 0, Math.PI * 2);
      ctx.strokeStyle = t.vOne;
      ctx.lineWidth = 2 / z;
      ctx.stroke();
    }
    if (o.cursorDot) {
      // the Wiring tool's small grey dot at the snapped pointer (WiringTool.draw, I-45)
      ctx.beginPath();
      ctx.arc(o.cursorDot[0], o.cursorDot[1], 2.5, 0, Math.PI * 2);
      ctx.fillStyle = t.ink2;
      ctx.fill();
    }
  }

  private drawOverlay(partsShown: Component[]): void {
    this.drawGestures();
    const o = this.overlay, ctx = this.ctx, z = this.view.zoom, s = this.scene!;
    // The Poke tool's lens on every subcircuit (SubcircuitPoker.paint): a double click in it goes inside.
    if (o.magnifiers) {
      for (const c of partsShown) {
        if (c.subcircuit === undefined && !c.appearance) continue;
        const m = magnifier(c);
        ctx.beginPath();
        ctx.arc(m.cx, m.cy, m.r, 0, Math.PI * 2);
        ctx.fillStyle = 'rgba(0,85,165,0.16)';
        ctx.fill();
        ctx.strokeStyle = this.theme.navy;
        ctx.lineWidth = 1.2 / z + 0.2;
        ctx.stroke();
        ctx.beginPath();
        m.handle.forEach(([x, y], i) => (i ? ctx.lineTo(x, y) : ctx.moveTo(x, y)));
        ctx.closePath();
        ctx.fillStyle = this.theme.navy;
        ctx.fill();
      }
    }
    // A poked part that takes keys (a register, a memory): the red box of Logisim's caret.
    if (o.caret) {
      const c = s.components.get(o.caret);
      if (c) {
        const [x, y, w, hh] = c.bounds;
        ctx.strokeStyle = this.theme.error;
        ctx.lineWidth = 2 / z;
        ctx.strokeRect(x - 2 / z, y - 2 / z, w + 4 / z, hh + 4 / z);
      }
    }
    // A poked wire: its net stands out and a box shows the value (PokeTool.WireCaret).
    if (o.valueBox) {
      const b = o.valueBox;
      if (b.net) {
        const n = s.net(b.net);
        if (n) {
          ctx.beginPath();
          for (const id of n.wires) { const w = s.wires.get(id); if (w) { ctx.moveTo(w.a[0], w.a[1]); ctx.lineTo(w.b[0], w.b[1]); } }
          ctx.strokeStyle = 'rgba(245,190,40,0.55)';
          ctx.lineWidth = (wirePx(z, n.width) + 6) / z;
          ctx.lineCap = 'round';
          ctx.stroke();
        }
      }
      const dpr = this.dpr;
      ctx.save();
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      const [sx, sy] = toScreen(this.view, b.at);
      // the value now, as the net's value changes (Logisim reads it at every paint)
      const net = b.net ? s.net(b.net) : undefined;
      const text = net ? wireValueText(s.values.get(net.id), net.width) : b.text;
      ctx.font = `600 12px ${FONTS.code}`;
      const tw = ctx.measureText(text).width;
      const bx = Math.min(sx + 4, this.width - tw - 14), by = Math.min(sy + 4, this.height - 24);
      ctx.beginPath();
      ctx.roundRect(bx, by, tw + 10, 20, 4);
      ctx.fillStyle = '#fff4c2';
      ctx.fill();
      ctx.strokeStyle = this.theme.ink;
      ctx.lineWidth = 1;
      ctx.stroke();
      ctx.beginPath();
      ctx.arc(sx, sy, 2.5, 0, Math.PI * 2);
      ctx.fillStyle = this.theme.ink;
      ctx.fill();
      ctx.fillStyle = this.theme.ink;
      ctx.textBaseline = 'middle';
      ctx.textAlign = 'left';
      ctx.fillText(text, bx + 5, by + 10.5);
      ctx.restore();
    }
  }

  private outline(c: Component, fill: string | null, stroke: string | null, px: number): void {
    const ctx = this.ctx, z = this.view.zoom;
    const pad = 3 + 2 / z;
    const [x, y, w, hh] = c.bounds;
    ctx.beginPath();
    ctx.roundRect(x - pad, y - pad, w + 2 * pad, hh + 2 * pad, 4 + 2 / z);
    if (fill) { ctx.fillStyle = fill; ctx.fill(); }
    if (stroke) { ctx.strokeStyle = stroke; ctx.lineWidth = px / z; ctx.stroke(); }
  }

  private drawGrid(): void {
    const v = this.view, z = v.zoom;
    const minor = z >= 0.6 ? 10 : 50;           // a dot every grid step when there is room (≥ 6 px)
    const period = 50 * (minor === 50 ? 2 : 1);
    const key = `${z}|${this.width}|${this.height}|${this.dpr}|${minor}`;
    if (!this.grid || this.grid.key !== key) {
      const cell = period * z;
      const w = Math.ceil((this.width + cell) * this.dpr), hh = Math.ceil((this.height + cell) * this.dpr);
      const g = typeof OffscreenCanvas !== 'undefined' ? new OffscreenCanvas(w, hh) : Object.assign(document.createElement('canvas'), { width: w, height: hh });
      const gc = g.getContext('2d') as CanvasRenderingContext2D;
      const step = minor * z * this.dpr;
      const r = Math.max(1, Math.round(this.dpr * (z >= 1.5 ? 1.5 : 1)));
      const per = Math.round(period / minor);
      for (const major of [false, true]) {
        gc.fillStyle = major ? this.theme.gridMajor : this.theme.grid;
        gc.beginPath();
        let i = 0;
        for (let x = 0; x <= w; x += step, i++) {
          let j = 0;
          for (let y = 0; y <= hh; y += step, j++) {
            const isMajor = i % per === 0 && j % per === 0;
            if (isMajor !== major) continue;
            gc.rect(Math.round(x) - (r >> 1), Math.round(y) - (r >> 1), r, r);
          }
        }
        gc.fill();
      }
      this.grid = { key, canvas: g };
    }
    const cell = period * z;
    const ox = -(((v.x % period) + period) % period) * z;
    const oy = -(((v.y % period) + period) % period) * z;
    this.ctx.drawImage(this.grid.canvas as CanvasImageSource, 0, 0, this.grid.canvas.width, this.grid.canvas.height, ox, oy, this.width + cell, this.height + cell);
  }

  private wireColor(netId: string | null): string {
    const s = this.scene!;
    if (!netId) return this.theme.vNone;
    const n = s.net(netId);
    if (n && s.widthMismatch(n)) return this.theme.vWidth;
    const v = s.values.get(netId);
    return valueKind(v) === 'none' ? this.theme.vNone : valueColor(this.theme, v);
  }

  private drawWires(shown: Box): void {
    const m = this.marks!, ctx = this.ctx, z = this.view.zoom;
    const groups = new Map<string, { color: string; width: number; segs: typeof m.segments }>();
    const colorOf = new Map<string, string>();
    const hidden = this.overlay.hidden;
    for (const seg of m.segments) {
      if (hidden?.has(seg.id)) continue;         // a wire being shortened: only what is left of it shows (I-50)
      const x0 = Math.min(seg.a[0], seg.b[0]), x1 = Math.max(seg.a[0], seg.b[0]), y0 = Math.min(seg.a[1], seg.b[1]), y1 = Math.max(seg.a[1], seg.b[1]);
      if (x0 > shown.x1 || x1 < shown.x0 || y0 > shown.y1 || y1 < shown.y0) continue;
      let col = seg.net ? colorOf.get(seg.net) : undefined;
      if (!col) { col = this.wireColor(seg.net); if (seg.net) colorOf.set(seg.net, col); }
      const width = wirePx(z, seg.bits) / z;
      const k = `${col}|${width}`;
      const g = groups.get(k);
      if (g) g.segs.push(seg); else groups.set(k, { color: col, width, segs: [seg] });
    }
    ctx.lineCap = 'round';
    for (const g of groups.values()) {
      ctx.beginPath();
      for (const seg of g.segs) { ctx.moveTo(seg.a[0], seg.a[1]); ctx.lineTo(seg.b[0], seg.b[1]); }
      ctx.strokeStyle = g.color;
      ctx.lineWidth = g.width;
      ctx.stroke();
    }
    // jumps: the horizontal wire hops over the vertical one (v1 D-061)
    const r = jumpUnits(z);
    if (r === null) return;
    const byId = new Map(m.segments.map((x) => [x.id, x]));
    for (const c of m.crossings) {
      const [x, y] = c.at;
      if (x < shown.x0 || x > shown.x1 || y < shown.y0 || y > shown.y1) continue;
      const over = byId.get(c.over)!, under = byId.get(c.under)!;
      const wo = wirePx(z, over.bits) / z, wu = wirePx(z, under.bits) / z;
      ctx.lineCap = 'butt';
      ctx.beginPath(); ctx.moveTo(x - r, y); ctx.lineTo(x + r, y);
      ctx.strokeStyle = this.theme.paper; ctx.lineWidth = wo + 2 / z; ctx.stroke();
      ctx.beginPath(); ctx.moveTo(x, y - r - 1); ctx.lineTo(x, y + r + 1);
      ctx.strokeStyle = this.wireColor(under.net); ctx.lineWidth = wu; ctx.stroke();
      ctx.beginPath(); ctx.arc(x, y, r, Math.PI, 0);
      ctx.strokeStyle = this.wireColor(over.net); ctx.lineWidth = wo; ctx.stroke();
      ctx.lineCap = 'round';
    }
  }

  private drawDots(shown: Box): void {
    const m = this.marks!, ctx = this.ctx, z = this.view.zoom;
    const r = dotUnits(z) / 2;
    const netAt = new Map<string, string | null>();
    for (const seg of m.segments) { netAt.set(`${seg.a}`, seg.net); netAt.set(`${seg.b}`, seg.net); }
    for (const d of m.dots) {
      if (d[0] < shown.x0 || d[0] > shown.x1 || d[1] < shown.y0 || d[1] > shown.y1) continue;
      ctx.beginPath();
      ctx.arc(d[0], d[1], r, 0, Math.PI * 2);
      ctx.fillStyle = this.wireColor(netAt.get(`${d}`) ?? null);
      ctx.fill();
    }
  }

  private paintShapes(c: Component, shapes: Shape[], look: Look): void {
    const ctx = this.ctx, s = this.scene!;
    for (const sh of shapes) {
      if (sh.k === 'port') {
        const open = sh.i >= 0 && s.isOpen(c.id, sh.i);
        if (open || look.zoom >= 0.5) paintPort(ctx, sh.x, sh.y, sh.value, look, open);
        continue;
      }
      if (sh.k === 'text') { paintText(ctx, sh, look); continue; }
      let p = this.paths.get(sh);
      if (!p) { p = toPath2D(sh); this.paths.set(sh, p); }
      const fill = color(look.theme, sh.fill), stroke = color(look.theme, sh.stroke);
      if (fill) { ctx.fillStyle = fill; ctx.fill(p); }
      if (stroke) {
        ctx.strokeStyle = stroke;
        ctx.lineWidth = strokeUnits(sh, look.zoom);
        ctx.lineCap = sh.cap ?? 'round';
        if (sh.dash) ctx.setLineDash(sh.dash);
        ctx.stroke(p);
        if (sh.dash) ctx.setLineDash([]);
      }
    }
  }

  private drawChips(shown: Box, look: Look, owners?: ReadonlySet<string>): void {
    const ctx = this.ctx, s = this.scene!, z = this.view.zoom;
    const wiresUnder: Box[] = [];
    for (const ch of this.chips) {
      if (!boxesMeet(ch.box, shown) || (owners && !owners.has(ch.owner))) continue;
      if (ch.size * z < MIN_TEXT_PX) continue;    // unreadable: not drawn at all (not a band of colour either)
      const b = ch.box;
      let text = ch.text;
      let border: string = this.theme.chipStroke;
      let ink: string = this.theme.ink;
      if (ch.kind === 'value') {
        const c = s.components.get(ch.owner);
        const r = c ? rendererFor(c).valueChip?.(c, this.stateOf(c)) : null;
        if (r) { text = r.text; border = valueKind(r.value) === 'none' ? this.theme.chipStroke : valueKind(r.value) === 'bus' ? this.theme.vBus : valueColor(this.theme, r.value); }
      }
      if (ch.kind === 'width') {
        if (!this.busWidths) continue;
        const [x0, y0, x1, y1] = ch.slash!;
        ctx.beginPath(); ctx.moveTo(x0, y0); ctx.lineTo(x1, y1);
        ctx.strokeStyle = this.wireColor(ch.owner); ctx.lineWidth = 1.4 / z + 0.6; ctx.stroke();
        paintText(ctx, { k: 'text', x: (b.x0 + b.x1) / 2, y: (b.y0 + b.y1) / 2 + 0.3, text, font: 'code', size: ch.size, weight: 700, anchor: 'middle', baseline: 'middle', fill: 'ink2' }, look);
        continue;
      }
      if (ch.kind === 'arm') {
        ctx.fillStyle = 'rgba(255,255,255,0.88)';
        ctx.fillRect(b.x0 - 0.5, b.y0, b.x1 - b.x0 + 1, b.y1 - b.y0);
        paintText(ctx, { k: 'text', x: b.x0 + 1, y: (b.y0 + b.y1) / 2 + 0.3, text, font: 'ui', size: ch.size, weight: 700, anchor: 'start', baseline: 'middle', fill: 'tealText' }, look);
        continue;
      }
      ctx.beginPath();
      ctx.roundRect(b.x0, b.y0, b.x1 - b.x0, b.y1 - b.y0, 3.5);
      ctx.fillStyle = this.theme.chip; ctx.fill();
      ctx.strokeStyle = border; ctx.lineWidth = (ch.kind === 'value' ? 1.3 : 1) / z; ctx.stroke();
      if (ch.kind === 'label') ink = this.theme.navy;
      ctx.fillStyle = ink;
      ctx.font = cssFont({ k: 'text', x: 0, y: 0, text, font: ch.font, size: ch.size, weight: ch.weight }, ch.size);
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(text, (b.x0 + b.x1) / 2, (b.y0 + b.y1) / 2 + 0.4);
      wiresUnder.push(b);
    }
    // A chip that could not stand free never hides a wire: the wires under it go on top again (v1 S-01).
    if (!wiresUnder.length) return;
    for (const seg of this.marks!.segments) {
      const sb = { x0: Math.min(seg.a[0], seg.b[0]) - 1, y0: Math.min(seg.a[1], seg.b[1]) - 1, x1: Math.max(seg.a[0], seg.b[0]) + 1, y1: Math.max(seg.a[1], seg.b[1]) + 1 };
      if (!wiresUnder.some((b) => boxesMeet(b, sb))) continue;
      ctx.beginPath(); ctx.moveTo(seg.a[0], seg.a[1]); ctx.lineTo(seg.b[0], seg.b[1]);
      ctx.strokeStyle = this.wireColor(seg.net); ctx.lineWidth = wirePx(z, seg.bits) / z; ctx.lineCap = 'round'; ctx.stroke();
    }
  }

  private drawNetHalo(wireId: string): void {
    const s = this.scene!, ctx = this.ctx, z = this.view.zoom;
    const n = s.wireNet(wireId);
    if (!n) return;
    ctx.beginPath();
    for (const id of n.wires) {
      const w = s.wires.get(id);
      if (w) { ctx.moveTo(w.a[0], w.a[1]); ctx.lineTo(w.b[0], w.b[1]); }
    }
    ctx.strokeStyle = 'rgba(0,169,165,0.28)';
    ctx.lineWidth = (wirePx(z, n.width) + 6) / z;
    ctx.lineCap = 'round';
    ctx.stroke();
  }

  // ---- what the overlays draw again (the influence's parts over its dimming) --------------------------

  // Stroke these wires as the Canvas does (their value colour and width).
  paintWireIds(ids: Iterable<string>): void {
    const s = this.scene, m = this.marks;
    if (!s || !m) return;
    const want = new Set(ids);
    const ctx = this.ctx, z = this.view.zoom;
    ctx.lineCap = 'round';
    for (const seg of m.segments) {
      if (!want.has(seg.id)) continue;
      ctx.beginPath(); ctx.moveTo(seg.a[0], seg.a[1]); ctx.lineTo(seg.b[0], seg.b[1]);
      ctx.strokeStyle = this.wireColor(seg.net); ctx.lineWidth = wirePx(z, seg.bits) / z; ctx.stroke();
    }
  }

  // Paint these parts again (both passes, larger first) and their chips.
  paintPartIds(ids: Iterable<string>, look: Look, shown: Box): void {
    const s = this.scene;
    if (!s) return;
    const want = new Set(ids);
    const parts = [...s.components.values()].filter((c) => want.has(c.id));
    const ordered = byArea(parts);
    for (const c of ordered) this.paintShapes(c, this.layersOf(c).base, look);
    for (const c of ordered) this.paintShapes(c, this.layersOf(c).top, look);
    this.drawChips(shown, look, want);
  }

  // Where the chips stand (the overlays' own chips keep off them).
  chipBoxes(): Box[] {
    this.layout();
    return this.chips.map((c) => c.box);
  }

  wireSegments(): WireMarks['segments'] {
    this.layout();
    return this.marks?.segments ?? [];
  }

  get context(): CanvasRenderingContext2D { return this.ctx; }

  /* The picture of the circuit as drawn now, as SVG (picture export, N-21, writes this to a file):
     the same wires, jumps, dots, parts' shapes and chips as the Canvas at 100 %, without the grid,
     hover or selection.  `box` defaults to everything drawn with a 20-unit margin. */
  exportSvg(box?: Box): string {
    const s = this.scene;
    if (!s) return '';
    this.layout();
    const look = { theme: this.theme, zoom: 1, minText: MIN_TEXT_PX, fitted: (t: TextShape) => fittedSize(this.ctx, t) };
    const m = this.marks!;
    const out: string[] = [];
    for (const o of this.overlays) out.push(...(o.svg?.(this) ?? []));
    for (const seg of m.segments) out.push(wireSvg(seg.a, seg.b, undefined, seg.bits, look).replace(/stroke="[^"]*"/, `stroke="${this.wireColor(seg.net)}"`));
    const r = jumpUnits(1);
    const byId = new Map(m.segments.map((x) => [x.id, x]));
    for (const c of r === null ? [] : m.crossings) {
      const [x, y] = c.at;
      const j = r!;
      const over = byId.get(c.over)!, under = byId.get(c.under)!;
      out.push(`<line x1="${x - j}" y1="${y}" x2="${x + j}" y2="${y}" stroke="${this.theme.paper}" stroke-width="${wirePx(1, over.bits) + 2}"/>`);
      out.push(`<line x1="${x}" y1="${y - j - 1}" x2="${x}" y2="${y + j + 1}" stroke="${this.wireColor(under.net)}" stroke-width="${wirePx(1, under.bits)}"/>`);
      out.push(`<path d="M${x - j} ${y} A${j} ${j} 0 0 1 ${x + j} ${y}" fill="none" stroke="${this.wireColor(over.net)}" stroke-width="${wirePx(1, over.bits)}"/>`);
    }
    const netAt = new Map<string, string | null>();
    for (const seg of m.segments) { netAt.set(`${seg.a}`, seg.net); netAt.set(`${seg.b}`, seg.net); }
    for (const d of m.dots) out.push(`<circle cx="${d[0]}" cy="${d[1]}" r="${dotUnits(1) / 2}" fill="${this.wireColor(netAt.get(`${d}`) ?? null)}"/>`);
    // the same order as the screen (layers.ts): every body fill, then the rest
    const ordered = byArea([...s.components.values()]);
    out.push('<g class="fills">', ...ordered.map((c) => shapesToSvg(this.layersOf(c).base, look)), '</g>');
    for (const c of ordered) out.push(`<g data-part="${c.id}">`, shapesToSvg(this.layersOf(c).top, look), '</g>');
    for (const ch of this.chips) {
      if ((ch.kind === 'width' && !this.busWidths) || ch.size < MIN_TEXT_PX) continue;   // as on screen at 100 %
      out.push(...this.chipSvg(ch));
    }
    const e = s.extent();
    let b = box ?? { x0: e.x0 - 20, y0: e.y0 - 20, x1: e.x1 + 20, y1: e.y1 + 20 };
    if (!box) for (const ch of this.chips) b = { x0: Math.min(b.x0, ch.box.x0 - 4), y0: Math.min(b.y0, ch.box.y0 - 4), x1: Math.max(b.x1, ch.box.x1 + 4), y1: Math.max(b.y1, ch.box.y1 + 4) };
    return svgDocument(out, b, look);
  }

  private chipSvg(ch: Chip): string[] {
    const b = ch.box, s = this.scene!;
    const esc = (t: string) => t.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    const font = (size: number, weight: number, fam: 'ui' | 'code') => `font-family="${FONTS[fam]}" font-size="${size}" font-weight="${weight}"`;
    if (ch.kind === 'width') {
      const [x0, y0, x1, y1] = ch.slash!;
      return [`<line x1="${x0}" y1="${y0}" x2="${x1}" y2="${y1}" stroke="${this.wireColor(ch.owner)}" stroke-width="2"/>`,
        `<text x="${(b.x0 + b.x1) / 2}" y="${(b.y0 + b.y1) / 2 + 0.3}" ${font(ch.size, 700, 'code')} text-anchor="middle" dominant-baseline="central" fill="${this.theme.ink2}">${esc(ch.text)}</text>`];
    }
    if (ch.kind === 'arm') {
      return [`<rect x="${b.x0 - 0.5}" y="${b.y0}" width="${b.x1 - b.x0 + 1}" height="${b.y1 - b.y0}" fill="rgba(255,255,255,0.88)"/>`,
        `<text x="${b.x0 + 1}" y="${(b.y0 + b.y1) / 2 + 0.3}" ${font(ch.size, 700, 'ui')} dominant-baseline="central" fill="${this.theme.tealText}">${esc(ch.text)}</text>`];
    }
    let text = ch.text, border: string = this.theme.chipStroke;
    if (ch.kind === 'value') {
      const c = s.components.get(ch.owner);
      const r = c ? rendererFor(c).valueChip?.(c, this.stateOf(c)) : null;
      if (r) { text = r.text; border = valueKind(r.value) === 'none' ? this.theme.chipStroke : valueKind(r.value) === 'bus' ? this.theme.vBus : valueColor(this.theme, r.value); }
    }
    return [`<rect x="${b.x0}" y="${b.y0}" width="${b.x1 - b.x0}" height="${b.y1 - b.y0}" rx="3.5" fill="${this.theme.chip}" stroke="${border}" stroke-width="${ch.kind === 'value' ? 1.3 : 1}"/>`,
      `<text x="${(b.x0 + b.x1) / 2}" y="${(b.y0 + b.y1) / 2 + 0.4}" ${font(ch.size, ch.weight, ch.font)} text-anchor="middle" dominant-baseline="central" fill="${ch.kind === 'label' ? this.theme.navy : this.theme.ink}">${esc(text)}</text>`];
  }

  // A part's drawing as live SVG elements (the same shapes; tools/measure-canvas.ts drew the circuit this way).
  partElements(c: Component, doc: Document): SVGElement[] {
    const look = { theme: this.theme, zoom: this.view.zoom };
    return this.shapesOf(c).map((sh) => svgElement(doc, sh, look)).filter((e): e is SVGElement => e !== null);
  }

  wireColorOf(netId: string | null): string { return this.wireColor(netId); }

  /* "Show this place" (reveal.ts, D-143): mark the parts, wires and nets (a net: its wires and the
     ports on it) in the error colour -- or, for a part found by its name (tone 'find': Find, Tunnels,
     the search palette, N-12), in the selection's blue -- and bring them into view: centred at this
     zoom when they fit, else fitted.  With nothing to mark, centre on `at`.  The next selection
     clears the marks. */
  reveal(r: { components: string[]; wires: string[]; nets: string[]; at: [number, number] | null; tone?: 'error' | 'find' }): void {
    const s = this.scene;
    if (!s) return;
    this.marked = { components: new Set(r.components), wires: new Set(r.wires), nets: new Set(r.nets), tone: r.tone ?? 'error' };
    let b: Box | null = null;
    const add = (x0: number, y0: number, x1: number, y1: number) => {
      b = b ? { x0: Math.min(b.x0, x0), y0: Math.min(b.y0, y0), x1: Math.max(b.x1, x1), y1: Math.max(b.y1, y1) } : { x0, y0, x1, y1 };
    };
    for (const id of r.components) { const c = s.components.get(id); if (c) add(c.bounds[0], c.bounds[1], c.bounds[0] + c.bounds[2], c.bounds[1] + c.bounds[3]); }
    const wires = new Set(r.wires);
    for (const id of r.nets) for (const w of s.net(id)?.wires ?? []) wires.add(w);
    for (const id of wires) { const w = s.wires.get(id); if (w) add(Math.min(w.a[0], w.b[0]), Math.min(w.a[1], w.b[1]), Math.max(w.a[0], w.b[0]), Math.max(w.a[1], w.b[1])); }
    for (const id of r.nets) for (const [cid, i] of s.net(id)?.ports ?? []) { const q = s.components.get(cid)?.ports[i]; if (q) add(q.loc[0], q.loc[1], q.loc[0], q.loc[1]); }
    if (!b && r.at) add(r.at[0], r.at[1], r.at[0], r.at[1]);
    if (b && this.width > 0) {
      const box: Box = b;
      const pad = 40 / this.view.zoom;
      const fits = (box.x1 - box.x0) + 2 * pad <= this.width / this.view.zoom && (box.y1 - box.y0) + 2 * pad <= this.height / this.view.zoom;
      const cx = (box.x0 + box.x1) / 2, cy = (box.y0 + box.y1) / 2;
      this.setView(fits ? { zoom: this.view.zoom, x: cx - this.width / 2 / this.view.zoom, y: cy - this.height / 2 / this.view.zoom } : fit(box, this.width, this.height), true);
    }
    this.invalidate();
  }

  // What a message marked: a tint behind (before the wires), outlines and halos over them (after the parts).
  private drawMarked(behind: boolean): void {
    const m = this.marked!, s = this.scene!, ctx = this.ctx, z = this.view.zoom;
    const tint = m.tone === 'find' ? this.theme.selectTint : this.theme.errorTint;
    const line = m.tone === 'find' ? this.theme.select : this.theme.error;
    // A marked part: tinted and outlined INSIDE its own bounds, so the mark never reaches a part next to it
    // (a tunnel on its port; UI review of #425): the red line lies over the part's own outline.
    for (const id of m.components) {
      const c = s.components.get(id);
      if (!c) continue;
      const [x, y, w, hh] = c.bounds, i = 1 / z;
      ctx.beginPath();
      ctx.rect(x + i, y + i, Math.max(0, w - 2 * i), Math.max(0, hh - 2 * i));
      if (behind) { ctx.fillStyle = tint; ctx.fill(); } else { ctx.strokeStyle = line; ctx.lineWidth = 2 / z; ctx.stroke(); }
    }
    if (behind) return;
    const wires = new Set(m.wires);
    for (const id of m.nets) for (const w of s.net(id)?.wires ?? []) wires.add(w);
    ctx.beginPath();
    for (const id of wires) { const w = s.wires.get(id); if (w) { ctx.moveTo(w.a[0], w.a[1]); ctx.lineTo(w.b[0], w.b[1]); } }
    ctx.strokeStyle = m.tone === 'find' ? 'rgba(0,85,165,0.3)' : 'rgba(192,57,43,0.35)';
    ctx.lineWidth = (wirePx(z, 2) + 7) / z;
    ctx.lineCap = 'round';
    ctx.stroke();
    for (const id of m.nets) {
      for (const [cid, i] of s.net(id)?.ports ?? []) {
        const q = s.components.get(cid)?.ports[i];
        if (!q) continue;
        ctx.beginPath();
        ctx.arc(q.loc[0], q.loc[1], (portPx(z) * 1.25 + 2) / z, 0, Math.PI * 2);   // just round the port's own mark
        ctx.strokeStyle = line;
        ctx.lineWidth = 2 / z;
        ctx.stroke();
      }
    }
  }

  // What is marked now (tests).
  markedIds(): { components: string[]; wires: string[]; nets: string[]; tone: 'error' | 'find' } | null {
    return this.marked ? { components: [...this.marked.components], wires: [...this.marked.wires], nets: [...this.marked.nets], tone: this.marked.tone } : null;
  }

  // The Canvas's size on screen, CSS px (the Minimap draws the view's rectangle, N-12).
  size(): { width: number; height: number } { return { width: this.width, height: this.height }; }

  // Bring the circuit point to the middle of the Canvas, at this zoom (the Minimap, v1 Minimap.centerAt).
  centerOn(p: [number, number]): void {
    if (this.width === 0) return;
    this.setView({ zoom: this.view.zoom, x: p[0] - this.width / 2 / this.view.zoom, y: p[1] - this.height / 2 / this.view.zoom });
  }

  percent(): string { return percent(this.view.zoom); }
  portRadius(): number { return portPx(this.view.zoom); }
}

export function toPath2D(s: Shape): Path2D {
  const p = new Path2D();
  if (s.k === 'rect') { if (s.r) p.roundRect(s.x, s.y, s.w, s.h, s.r); else p.rect(s.x, s.y, s.w, s.h); return p; }
  if (s.k === 'ellipse') { p.ellipse(s.cx, s.cy, s.rx, s.ry, 0, 0, Math.PI * 2); return p; }
  if (s.k !== 'path') return p;
  for (const g of s.d) {
    switch (g[0]) {
      case 'M': p.moveTo(g[1], g[2]); break;
      case 'L': p.lineTo(g[1], g[2]); break;
      case 'Q': p.quadraticCurveTo(g[1], g[2], g[3], g[4]); break;
      case 'C': p.bezierCurveTo(g[1], g[2], g[3], g[4], g[5], g[6]); break;
      case 'A': p.arc(g[1], g[2], g[3], g[4], g[5], g[6]); break;
      case 'Z': p.closePath(); break;
    }
  }
  return p;
}
