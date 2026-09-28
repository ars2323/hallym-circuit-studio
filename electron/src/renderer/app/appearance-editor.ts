/* The appearance editor (N-11, D-153; Project › Edit Circuit Appearance,
   docs/interaction-parity.md I-195..I-205): a subcircuit's own drawing --
   the shapes its instances show, its ports and its anchor -- edited with
   the original's tools.

     toolbar      Select, Text, Line, Curve, Polyline, Rectangle, Rounded
                  Rectangle, Oval, Polygon (the original's AppearanceToolbarModel),
                  then Revert To Default Appearance
     Select       a press on a chosen shape's handle drags the handle; on a
                  shape picks it (Shift: toggles) and drags what is chosen; on
                  nothing, a rectangle chooses (Shift: toggles); a drag starts
                  after 2 units; Ctrl snaps to the grid, Shift keeps one axis;
                  Delete / Backspace remove, Esc lets go (draw SelectTool)
     drawing      Rectangle, Rounded Rectangle, Oval (Shift square, Alt from
                  the middle), Line (Shift 45°), Curve (drag the ends, then
                  press and drag the control: Shift symmetric, Alt through the
                  point), Polyline and Polygon (a press a point, Shift 45°;
                  double click, Enter or back on the first point ends it; Esc
                  drops it), Text (a press opens a field; on a text, edits it;
                  Enter keeps, Esc drops).  Ctrl snaps to the grid.  After a
                  shape the tool goes back to Select with the shape chosen
                  (AppearanceCanvas.toolGestureComplete).
     keys         Ctrl+C, X, V, D, A; Delete; Ctrl+↑ ↓ (Raise, Lower), Ctrl+Shift+↑ ↓
                  (Raise To Top, Lower To Bottom); the right click menu has
                  them and Add / Remove Vertex (the original's popup)
     ports        a chosen port shows, bottom right, the circuit with its pin
                  marked (LayoutThumbnail)

   The page follows the pointer and draws what the tool would make; the
   engine makes it with the original's code (edit.appearance, one undo step
   each), and its answer (model.appearance) is what is drawn after.  Shapes
   are named by their number from the bottom; the chosen ones are the
   page's.  Nothing here changes the circuit. */

import type { AppearanceEdit, AppearanceEditShape, AppearanceHit, AppearanceMenu, EditResult, Snapshot, WindowMethod } from '../../main/protocol.ts';
import { closeMenus, type MenuEntry, SEPARATOR, showMenu } from '../canvas/overlays/menu.ts';
import { h, icon } from '../shared/dom.ts';
import {
  ALIGN_NAMES, ATTR_NAMES, closes, curveControl, DRAG_TOLERANCE, DRAW_DEFAULTS, type DrawTool, dragged, handleDelta, handleSize,
  lineEnd, type Mods, moveDelta, onCurve, type P, PAINT_NAMES, poly, type Press, pressCount, rectFromDrag, snap, toolAttributes, toolAttrs,
} from './logic/appearance.ts';

export interface AppearanceHost {
  call<T = unknown>(method: WindowMethod, params: Record<string, unknown>): Promise<T>;
  ready(): boolean;
  failed(name: string, e: unknown): void;
  note(text: string | null): void;                          // the status bar's fact about the last action
  attributes(content: HTMLElement | null): void;            // what the Attributes panel shows while the editor is up
  layout(fileId: string, circuitId: string): Promise<Snapshot | null>;   // the circuit, for the port thumbnail
}

const TOOL_ICONS: Record<DrawTool, string> = {
  Select: 'mouse-pointer-2', Text: 'type', Line: 'slash', Curve: 'spline', Polyline: 'waypoints', Rectangle: 'rectangle-horizontal',
  'Rounded Rectangle': 'square-round-corner', Oval: 'circle', Polygon: 'pentagon',
};
const TOOLS: DrawTool[] = ['Select', 'Text', 'Line', 'Curve', 'Polyline', 'Rectangle', 'Rounded Rectangle', 'Oval', 'Polygon'];

const MIN_ZOOM = 0.5;
const MAX_ZOOM = 8;          // the original's zoom list ends at 800 %
const SVG = 'http://www.w3.org/2000/svg';
const PORT_COLOR = '#2f66d0';      // the original's port marks (blue)
const ANCHOR_COLOR = '#008000';    // AppearanceAnchor.SYMBOL_COLOR

interface View { x: number; y: number; zoom: number }

type Gesture =
  | { kind: 'none' }
  | { kind: 'pending'; start: P; shift: boolean; end?: P }
  | { kind: 'move'; start: P; end: P; effective: boolean }
  | { kind: 'handle'; start: P; end: P; shape: number; at: P; effective: boolean; handles?: P[] }
  | { kind: 'rect'; start: P; end: P; toggle: boolean; effective: boolean; filled: number | null }
  | { kind: 'idle' }
  | { kind: 'box'; start: P; end: P }                     // Rectangle, Rounded Rectangle, Oval
  | { kind: 'line'; start: P; end: P }
  | { kind: 'curveEnds'; start: P; end: P; down: boolean }
  | { kind: 'curveControl'; e0: P; e1: P; control: P; down: boolean }
  | { kind: 'poly'; points: P[]; down: boolean };

const el = <K extends keyof SVGElementTagNameMap>(tag: K, attrs: Record<string, string | number> = {}): SVGElementTagNameMap[K] => {
  const e = document.createElementNS(SVG, tag);
  for (const [k, v] of Object.entries(attrs)) e.setAttribute(k, String(v));
  return e;
};

// The original's font words in CSS (Java's logical fonts).
export function cssFamily(family: string): string {
  if (/mono/i.test(family)) return 'D2Coding, monospace';
  if (/serif/i.test(family) && !/sans/i.test(family)) return 'serif';
  return 'Arial, "Liberation Sans", "DejaVu Sans", sans-serif';
}

export class AppearanceEditor {
  readonly root: HTMLElement;
  private readonly host: AppearanceHost;
  private readonly stage: HTMLElement;
  private readonly svg: SVGSVGElement;
  private readonly layer: SVGGElement;
  private readonly over: SVGGElement;
  private readonly gridRect: SVGRectElement;
  private readonly toolButtons = new Map<DrawTool, HTMLButtonElement>();
  private readonly revert: HTMLButtonElement;
  private readonly facts: HTMLElement;
  private readonly thumb: HTMLCanvasElement;
  private textField: HTMLInputElement | null = null;
  private model: AppearanceEdit | null = null;
  private fileId = '';
  private circuitId = '';
  private view: View = { x: 0, y: 0, zoom: 2 };
  private fitted = false;
  tool: DrawTool = 'Select';
  readonly values: Record<string, string> = { ...DRAW_DEFAULTS };
  selected: number[] = [];
  private vertex: { shape: number; at: P } | null = null;
  private gesture: Gesture = { kind: 'none' };
  private mods: Mods = {};
  private pan: { x: number; y: number; view: View } | null = null;

  constructor(host: AppearanceHost) {
    this.host = host;
    const bar = h('div', { class: 'apptools', role: 'toolbar', 'aria-label': 'Appearance tools' });
    for (const t of TOOLS) {
      const b = h('button', { type: 'button', class: 'apptool', role: 'radio', 'aria-checked': String(t === 'Select'), title: t, 'aria-label': t }, icon(TOOL_ICONS[t]));
      b.addEventListener('click', () => this.setTool(t));
      this.toolButtons.set(t, b);
      bar.append(b);
    }
    this.revert = h('button', { type: 'button', class: 'btn small', title: '원조 기본 모양(핀으로 만드는 상자)으로 되돌립니다' }, 'Revert to Default');
    this.revert.addEventListener('click', () => void this.op('revert', {}, 'Revert To Default Appearance'));
    this.facts = h('span', { class: 'appfacts' });
    bar.append(h('span', { class: 'grow' }), this.facts, this.revert);
    this.svg = el('svg', { class: 'appsvg', tabindex: '0', 'aria-label': 'Appearance' });
    const defs = el('defs');
    const pattern = el('pattern', { id: 'appgrid', width: 10, height: 10, patternUnits: 'userSpaceOnUse' });
    pattern.append(el('circle', { cx: 0, cy: 0, r: 0.7, fill: '#c2cad4' }));
    defs.append(pattern);
    this.gridRect = el('rect', { x: -5000, y: -5000, width: 10000, height: 10000, fill: 'url(#appgrid)' });
    this.layer = el('g', { class: 'appshapes' });
    this.over = el('g', { class: 'appover' });
    this.svg.append(defs, this.gridRect, this.layer, this.over);
    this.thumb = h('canvas', { class: 'appthumb', width: '220', height: '160', hidden: true, 'aria-label': 'Circuit layout (the chosen port\'s pin)' });
    this.stage = h('div', { class: 'appstage' }, this.svg as unknown as HTMLElement, this.thumb);
    this.root = h('div', { class: 'appeditor' }, bar, this.stage);
    this.listen();
    new ResizeObserver(() => this.render()).observe(this.stage);
  }

  get shownFor(): { fileId: string; circuitId: string } | null {
    return this.model ? { fileId: this.fileId, circuitId: this.circuitId } : null;
  }

  // ---- showing a circuit's appearance ---------------------------------------------------------

  async open(fileId: string, circuitId: string): Promise<void> {
    if (this.fileId !== fileId || this.circuitId !== circuitId) {
      this.cancelGesture();
      this.selected = [];
      this.vertex = null;
      this.fitted = false;
      this.model = null;
    }
    this.fileId = fileId;
    this.circuitId = circuitId;
    try {
      const a = await this.host.call<AppearanceEdit>('model.appearance', { fileId, circuitId });
      if (this.fileId === fileId && this.circuitId === circuitId) this.show(a);
    } catch (e) {
      this.host.failed('Edit Appearance', e);
    }
  }

  close(): void {
    this.cancelGesture();
    this.commitText();
    this.model = null;
    this.thumb.hidden = true;
    this.host.attributes(null);
  }

  // model.appearance (the answer or the notification): drawn as it is.
  show(a: AppearanceEdit): void {
    if (a.fileId !== this.fileId || a.circuitId !== this.circuitId) return;
    this.model = a;
    this.selected = this.selected.filter((i) => i < a.shapes.length);
    if (!this.fitted && this.stage.clientWidth > 0) { this.fit(); this.fitted = true; }
    this.render();
    this.showAttributes();
    void this.showThumb();
  }

  private shape(i: number): AppearanceEditShape | undefined { return this.model?.shapes[i]; }

  // ---- the view --------------------------------------------------------------------------

  fit(): void {
    const m = this.model;
    const w = this.stage.clientWidth || 600, hh = this.stage.clientHeight || 400;
    if (!m || m.shapes.length === 0) { this.view = { x: -w / 4, y: -hh / 4, zoom: 2 }; return; }
    let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity;
    for (const s of m.shapes) {
      const [x, y, bw, bh] = s.bounds;
      x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x + bw); y1 = Math.max(y1, y + bh);
    }
    const margin = 70;   // AppearanceCanvas.BOUNDS_BUFFER
    const zoom = Math.max(MIN_ZOOM, Math.min(4, w / (x1 - x0 + 2 * margin), hh / (y1 - y0 + 2 * margin)));
    this.view = { zoom, x: (x0 + x1) / 2 - w / 2 / zoom, y: (y0 + y1) / 2 - hh / 2 / zoom };
    this.render();
  }

  zoomBy(factor: number, at?: [number, number]): void {
    const w = this.stage.clientWidth, hh = this.stage.clientHeight;
    const p = at ?? [w / 2, hh / 2];
    const z = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, this.view.zoom * factor));
    const cx = this.view.x + p[0] / this.view.zoom, cy = this.view.y + p[1] / this.view.zoom;
    this.view = { zoom: z, x: cx - p[0] / z, y: cy - p[1] / z };
    this.render();
  }

  get zoom(): number { return this.view.zoom; }

  private toCircuit(e: { clientX: number; clientY: number }): P {
    const r = this.svg.getBoundingClientRect();
    return [this.view.x + (e.clientX - r.left) / this.view.zoom, this.view.y + (e.clientY - r.top) / this.view.zoom];
  }

  private at(e: { clientX: number; clientY: number }): P {
    const [x, y] = this.toCircuit(e);
    return [Math.round(x), Math.round(y)];
  }

  // ---- tools ------------------------------------------------------------------------------

  setTool(t: DrawTool): void {
    if (this.tool === t) return;
    this.commitText();
    this.cancelGesture();
    this.tool = t;
    // choosing a tool lets go of what was chosen (the original's SelectTool.toolDeselected / toolSelected)
    this.selected = [];
    this.vertex = null;
    for (const [n, b] of this.toolButtons) { b.classList.toggle('on', n === t); b.setAttribute('aria-checked', String(n === t)); }
    this.svg.classList.toggle('drawing', t !== 'Select');
    this.render();
    this.showAttributes();
  }

  private cancelGesture(): void {
    this.gesture = { kind: 'none' };
    this.over.replaceChildren();
    this.render();
  }

  // ---- drawing -----------------------------------------------------------------------------

  render(): void {
    const w = this.stage.clientWidth, hh = this.stage.clientHeight;
    if (!w || !hh) return;
    const v = this.view;
    this.svg.setAttribute('viewBox', `${v.x} ${v.y} ${w / v.zoom} ${hh / v.zoom}`);
    this.gridRect.style.display = v.zoom * 10 >= 5 ? '' : 'none';
    const m = this.model;
    this.layer.replaceChildren();
    if (!m) return;
    const moving = this.gesture.kind === 'move' && this.gesture.effective ? moveDelta(this.gesture.end[0] - this.gesture.start[0], this.gesture.end[1] - this.gesture.start[1], this.chosenHandles(), this.mods) : null;
    for (const s of m.shapes) {
      const g = this.drawShape(s);
      if (!g) continue;
      if (moving && this.selected.includes(s.i)) g.setAttribute('transform', `translate(${moving[0]} ${moving[1]})`);
      if (this.gesture.kind === 'handle' && this.gesture.effective && this.gesture.shape === s.i) g.setAttribute('opacity', '0.35');
      this.layer.append(g);
    }
    this.renderOver(moving);
    this.renderFacts();
  }

  private drawShape(s: AppearanceEditShape): SVGGElement | null {
    const g = el('g', { 'data-shape': s.i, class: `appshape kind-${s.kind}` });
    if (s.kind === 'port' && s.port) {
      const [x, y] = s.port.at;
      if (s.port.input) g.append(el('rect', { x: x - 4, y: y - 4, width: 8, height: 8, fill: 'none', stroke: PORT_COLOR, 'stroke-width': 1 }));
      else g.append(el('circle', { cx: x, cy: y, r: 5, fill: 'none', stroke: PORT_COLOR, 'stroke-width': 1 }));
      g.append(el('circle', { cx: x, cy: y, r: 2, fill: PORT_COLOR }));
      return g;
    }
    if (s.kind === 'anchor' && s.at) {
      const [x, y] = s.at;
      const d: Record<string, P> = { east: [1, 0], west: [-1, 0], north: [0, -1], south: [0, 1] };
      const [dx, dy] = d[s.facing ?? 'east'] ?? [1, 0];
      g.append(el('circle', { cx: x, cy: y, r: 3, fill: 'none', stroke: ANCHOR_COLOR, 'stroke-width': 1 }));
      g.append(el('line', { x1: x + 3 * dx, y1: y + 3 * dy, x2: x + 11 * dx, y2: y + 11 * dy, stroke: ANCHOR_COLOR, 'stroke-width': 1 }));
      return g;
    }
    if (!s.svg) return null;
    const e = document.createElementNS(SVG, s.svg.tag) as SVGElement;
    for (const [k, v] of Object.entries(s.svg.attrs)) {
      if (k === 'font-family') e.setAttribute(k, cssFamily(v));
      else e.setAttribute(k, v);
    }
    if (s.svg.tag === 'text') {
      e.textContent = s.svg.text ?? '';
      e.setAttribute('dominant-baseline', 'alphabetic');
      if (this.textField && this.editingText === s.i) e.setAttribute('visibility', 'hidden');
    }
    if (!e.hasAttribute('fill') && s.svg.tag !== 'text') e.setAttribute('fill', 'none');
    if (e.hasAttribute('stroke') && !e.hasAttribute('stroke-width')) e.setAttribute('stroke-width', '1');
    g.append(e);
    return g;
  }

  private editingText: number | null = null;

  private chosenHandles(): P[] {
    return this.selected.flatMap((i) => this.shape(i)?.handles ?? []);
  }

  // Handles of the chosen shapes, the gesture's outline, the rectangle.
  private renderOver(moving: [number, number] | null): void {
    this.over.replaceChildren();
    const m = this.model;
    if (!m) return;
    const size = handleSize(this.view.zoom);
    const hs = size / 2;
    const line = 1 / this.view.zoom;
    const gs = this.gesture;
    for (const i of this.selected) {
      const s = this.shape(i);
      if (!s) continue;
      let handles = s.handles;
      if (gs.kind === 'handle' && gs.effective && gs.shape === i && gs.handles) handles = gs.handles;
      const [ox, oy] = moving ?? [0, 0];
      if (gs.kind === 'handle' && gs.effective && gs.shape === i && gs.handles && handles.length > 1) {
        const pts = gs.handles.map((p) => p.join(',')).join(' ');
        this.over.append(el(s.kind === 'oval' || s.kind === 'rect' || s.kind === 'roundrect' || s.closed ? 'polygon' : 'polyline',
          { points: pts, fill: 'none', stroke: '#5a6472', 'stroke-width': line, 'stroke-dasharray': `${3 * line} ${2 * line}` }));
      }
      handles.forEach((p, k) => {
        const on = this.vertex && this.vertex.shape === i && this.vertex.at[0] === p[0] && this.vertex.at[1] === p[1];
        this.over.append(el('rect', {
          x: p[0] + ox - hs, y: p[1] + oy - hs, width: size, height: size, class: 'apphandle',
          fill: on ? '#0055a5' : '#ffffff', stroke: s.moves[k] === false ? '#a9b1bb' : '#0055a5', 'stroke-width': line,
        }));
      });
    }
    // an insertable vertex (Add Vertex): a hollow mark where it would go
    if (this.vertex && !this.chosenHandles().some((p) => p[0] === this.vertex!.at[0] && p[1] === this.vertex!.at[1])) {
      this.over.append(el('circle', { cx: this.vertex.at[0], cy: this.vertex.at[1], r: hs, fill: 'none', stroke: '#0055a5', 'stroke-width': line }));
    }
    const ghost = (e: SVGElement) => { e.setAttribute('fill', 'none'); e.setAttribute('stroke', '#808080'); e.setAttribute('stroke-width', String(Math.max(line, Number(this.values['stroke-width'] ?? 1)))); this.over.append(e); };
    switch (gs.kind) {
      case 'rect': if (gs.effective) {
        const x = Math.min(gs.start[0], gs.end[0]), y = Math.min(gs.start[1], gs.end[1]);
        this.over.append(el('rect', { x, y, width: Math.abs(gs.end[0] - gs.start[0]), height: Math.abs(gs.end[1] - gs.start[1]), fill: 'rgba(0,0,0,0.125)', stroke: '#5a6472', 'stroke-width': line }));
      } break;
      case 'box': {
        const b = rectFromDrag(gs.start, gs.end, this.mods);
        if (b) ghost(this.tool === 'Oval' ? el('ellipse', { cx: b[0] + b[2] / 2, cy: b[1] + b[3] / 2, rx: b[2] / 2, ry: b[3] / 2 })
          : el('rect', { x: b[0], y: b[1], width: b[2], height: b[3], ...(this.tool === 'Rounded Rectangle' ? { rx: Number(this.values.rx ?? 10) } : {}) }));
      } break;
      case 'line': ghost(el('line', { x1: gs.start[0], y1: gs.start[1], x2: gs.end[0], y2: gs.end[1] })); break;
      case 'curveEnds': ghost(el('line', { x1: gs.start[0], y1: gs.start[1], x2: gs.end[0], y2: gs.end[1] })); break;
      case 'curveControl': {
        const pts: string[] = [];
        for (let t = 0; t <= 1.0001; t += 0.05) pts.push(onCurve(gs.e0, gs.control, gs.e1, t).join(','));
        ghost(el('polyline', { points: pts.join(' ') }));
      } break;
      case 'poly': ghost(el(this.tool === 'Polygon' ? 'polygon' : 'polyline', { points: gs.points.map((p) => p.join(',')).join(' ') })); break;
      default: break;
    }
  }

  private renderFacts(): void {
    const m = this.model;
    if (!m) { this.facts.textContent = ''; return; }
    const n = m.shapes.filter((s) => s.kind !== 'port' && s.kind !== 'anchor').length;
    const ports = m.shapes.filter((s) => s.kind === 'port').length;
    this.facts.textContent = `${m.default ? 'Default appearance' : 'Custom appearance'} · ${n} shape${n === 1 ? '' : 's'} · ${ports} port${ports === 1 ? '' : 's'} · ${Math.round(this.view.zoom * 100)}%`;
    this.revert.disabled = m.default || !m.editable;
    for (const b of this.toolButtons.values()) b.disabled = !m.editable;
  }

  // ---- the port thumbnail (I-204: the chosen ports' pins in the circuit) -----------------------

  private async showThumb(): Promise<void> {
    const m = this.model;
    const ports = this.selected.map((i) => this.shape(i)).filter((s): s is AppearanceEditShape => s?.kind === 'port');
    const allPorts = m?.shapes.filter((s) => s.kind === 'port').length ?? 0;
    // the original shows it when some port is chosen and not every one (LayoutPopupManager.shouldShowPopup)
    if (!m || ports.length === 0 || ports.length === allPorts) { this.thumb.hidden = true; return; }
    const snap = await this.host.layout(this.fileId, this.circuitId);
    if (!snap || this.model !== m) { this.thumb.hidden = true; return; }
    const pins = new Set(ports.map((p) => p.port?.pin?.join(',')).filter(Boolean));
    drawThumb(this.thumb, snap, pins as Set<string>);
    this.thumb.hidden = false;
  }

  // ---- attributes (the Attributes panel while the editor is up) -------------------------------

  private showAttributes(): void {
    const m = this.model;
    if (!m) { this.host.attributes(null); return; }
    const chosen = this.selected.map((i) => this.shape(i)).filter((s): s is AppearanceEditShape => s !== undefined);
    if (this.tool === 'Select' && chosen.length > 0) {
      // the attributes all chosen shapes have (the original's SelectionAttributes)
      const names = chosen.map((s) => Object.keys(s.attrs));
      const common = names[0].filter((n) => names.every((l) => l.includes(n)));
      const title = chosen.length === 1 ? kindName(chosen[0].kind) : `${chosen.length} shapes`;
      const values: Record<string, string> = {};
      for (const n of common) values[n] = chosen.every((s) => s.attrs[n] === chosen[0].attrs[n]) ? chosen[0].attrs[n] : '';
      this.host.attributes(attrTable(title, common, values, m.editable, (name, value) => void this.op('setAttr', { shapes: [...this.selected], attr: name, value }, ATTR_NAMES[name] ?? name)));
      return;
    }
    if (this.tool !== 'Select') {
      const names = toolAttributes(this.tool, this.values.paintType);
      this.host.attributes(attrTable(`${this.tool} Tool`, names, this.values, true, (name, value) => { this.values[name] = value; this.showAttributes(); this.render(); }));
      return;
    }
    this.host.attributes(h('div', { class: 'attrs' }, h('p', { class: 'hint' }, `${m.name} 회로의 모양입니다. 도형을 고르면 그 속성이 여기에 나옵니다.`)));
  }

  // ---- the engine -------------------------------------------------------------------------

  private async op(op: string, params: Record<string, unknown>, name: string): Promise<EditResult & { selected?: number[]; index?: number; handle?: P } | null> {
    if (!this.model || !this.host.ready()) return null;
    try {
      const r = await this.host.call<EditResult & { selected?: number[]; index?: number; handle?: P }>('edit.appearance', { fileId: this.fileId, circuitId: this.circuitId, op, ...params });
      if (r.selected) this.selected = r.selected;
      this.vertex = r.handle && this.selected.length === 1 ? { shape: this.selected[0], at: r.handle } : null;
      this.host.note(null);
      // the answer's model.appearance notification comes after it; draw the choice meanwhile
      this.render();
      this.showAttributes();
      return r;
    } catch (e) {
      this.host.failed(name, e);
      return null;
    }
  }

  private async hit(at: P, rect?: [number, number, number, number]): Promise<AppearanceHit | null> {
    try {
      return await this.host.call<AppearanceHit>('model.appearanceHit', { fileId: this.fileId, circuitId: this.circuitId, at, selected: this.selected, zoom: this.view.zoom, ...(rect ? { rect } : {}) });
    } catch { return null; }
  }

  // The Edit menu (keys, the right click): the original's AppearanceEditHandler.
  async command(cmd: 'cut' | 'copy' | 'paste' | 'delete' | 'duplicate' | 'selectAll' | 'raise' | 'lower' | 'raiseTop' | 'lowerBottom' | 'addVertex' | 'removeVertex'): Promise<void> {
    const m = this.model;
    if (!m) return;
    const names: Record<string, string> = {
      cut: 'Cut', copy: 'Copy', paste: 'Paste', delete: 'Delete', duplicate: 'Duplicate', raise: 'Raise Selection', lower: 'Lower Selection',
      raiseTop: 'Raise to Top', lowerBottom: 'Lower to Bottom', addVertex: 'Add Vertex', removeVertex: 'Remove Vertex',
    };
    if (cmd === 'selectAll') { this.setTool('Select'); this.selected = m.shapes.map((s) => s.i); this.render(); this.showAttributes(); void this.showThumb(); return; }
    if (cmd === 'addVertex' || cmd === 'removeVertex') {
      if (!this.vertex) return;
      await this.op(cmd, { shape: this.vertex.shape, at: this.vertex.at }, names[cmd]);
      return;
    }
    if (cmd !== 'paste' && this.selected.length === 0) return;
    await this.op(cmd, cmd === 'paste' ? {} : { shapes: [...this.selected] }, names[cmd]);
  }

  private async menuAt(x: number, y: number): Promise<void> {
    let on: AppearanceMenu | null = null;
    try {
      on = await this.host.call<AppearanceMenu>('model.appearanceMenu', {
        fileId: this.fileId, circuitId: this.circuitId, shapes: this.selected, ...(this.vertex ? { vertexShape: this.vertex.shape, vertexAt: this.vertex.at } : {}),
      });
    } catch { return; }
    const item = (label: string, key: keyof AppearanceMenu, cmd: Parameters<AppearanceEditor['command']>[0]): MenuEntry => ({ label, disabled: !on![key], run: () => void this.command(cmd) });
    const entries: MenuEntry[] = [
      item('Cut', 'cut', 'cut'), item('Copy', 'copy', 'copy'), item('Paste', 'paste', 'paste'), item('Delete', 'delete', 'delete'), item('Duplicate', 'duplicate', 'duplicate'),
      SEPARATOR,
      item('Raise Selection', 'raise', 'raise'), item('Lower Selection', 'lower', 'lower'), item('Raise to Top', 'raiseTop', 'raiseTop'), item('Lower to Bottom', 'lowerBottom', 'lowerBottom'),
      SEPARATOR,
      item('Add Vertex', 'addVertex', 'addVertex'), item('Remove Vertex', 'removeVertex', 'removeVertex'),
    ];
    showMenu(entries, x, y);
  }

  // ---- pointer and keys ------------------------------------------------------------------------

  private presses: Press | null = null;

  private listen(): void {
    const svg = this.svg;
    svg.addEventListener('contextmenu', (e) => { e.preventDefault(); void this.menuAt(e.clientX, e.clientY); });
    svg.addEventListener('wheel', (e) => {
      e.preventDefault();
      const r = svg.getBoundingClientRect();
      if (e.ctrlKey || e.metaKey) { this.zoomBy(Math.exp(-e.deltaY * 0.0015), [e.clientX - r.left, e.clientY - r.top]); return; }
      const dx = e.shiftKey ? e.deltaY : e.deltaX, dy = e.shiftKey ? 0 : e.deltaY;
      this.view = { ...this.view, x: this.view.x + dx / this.view.zoom, y: this.view.y + dy / this.view.zoom };
      this.render();
    }, { passive: false });
    svg.addEventListener('pointerdown', (e) => {
      svg.focus();
      if (e.button === 1) { e.preventDefault(); this.pan = { x: e.clientX, y: e.clientY, view: { ...this.view } }; svg.setPointerCapture(e.pointerId); return; }
      if (e.button !== 0 || !this.model?.editable && this.tool !== 'Select') return;
      svg.setPointerCapture(e.pointerId);
      this.mods = { shift: e.shiftKey, ctrl: e.ctrlKey || e.metaKey, alt: e.altKey };
      this.presses = pressCount(this.presses, e.timeStamp, e.clientX, e.clientY);
      void this.press(this.at(e), Math.max(this.presses.n, e.detail));
    });
    svg.addEventListener('pointermove', (e) => {
      if (this.pan) {
        this.view = { ...this.pan.view, x: this.pan.view.x - (e.clientX - this.pan.x) / this.view.zoom, y: this.pan.view.y - (e.clientY - this.pan.y) / this.view.zoom };
        this.render();
        return;
      }
      this.mods = { shift: e.shiftKey, ctrl: e.ctrlKey || e.metaKey, alt: e.altKey };
      this.drag(this.at(e), e.buttons !== 0);
    });
    svg.addEventListener('pointerup', (e) => {
      if (this.pan) { this.pan = null; return; }
      if (e.button !== 0) return;
      this.mods = { shift: e.shiftKey, ctrl: e.ctrlKey || e.metaKey, alt: e.altKey };
      void this.release(this.at(e));
    });
    svg.addEventListener('dblclick', (e) => { if (this.gesture.kind === 'poly') { e.preventDefault(); void this.finishPoly(); } });
  }

  // A key the editor takes (true: it did). Called by the window's key handler while the editor is up.
  key(e: KeyboardEvent): boolean {
    if (this.textField && document.activeElement === this.textField) return false;
    const mod = e.ctrlKey || e.metaKey;
    const g = this.gesture;
    if (e.key === 'Escape') {
      if (g.kind === 'poly' || g.kind === 'curveEnds' || g.kind === 'curveControl' || g.kind === 'box' || g.kind === 'line') { this.cancelGesture(); this.setTool('Select'); return true; }
      if (this.selected.length) { this.selected = []; this.vertex = null; this.render(); this.showAttributes(); void this.showThumb(); return true; }
      return false;
    }
    if (e.key === 'Enter' && g.kind === 'poly') { void this.finishPoly(); return true; }
    if ((e.key === 'Delete' || e.key === 'Backspace') && !mod && this.tool === 'Select') { void this.command('delete'); return true; }
    if (mod && (e.key === 'ArrowUp' || e.key === 'ArrowDown')) {
      void this.command(e.key === 'ArrowUp' ? (e.shiftKey ? 'raiseTop' : 'raise') : (e.shiftKey ? 'lowerBottom' : 'lower'));
      return true;
    }
    if (mod && !e.shiftKey && !e.altKey) {
      const map: Record<string, Parameters<AppearanceEditor['command']>[0]> = { KeyC: 'copy', KeyX: 'cut', KeyV: 'paste', KeyD: 'duplicate', KeyA: 'selectAll' };
      const cmd = map[e.code];
      if (cmd) { void this.command(cmd); return true; }
      if (e.key === '0') { this.fit(); return true; }
      if (e.key === '1') { this.zoomBy(1 / this.view.zoom); return true; }
      if (e.key === '=' || e.key === '+') { this.zoomBy(1.25); return true; }
      if (e.key === '-') { this.zoomBy(0.8); return true; }
    }
    if (e.key === 'Shift' || e.key === 'Control' || e.key === 'Alt') {
      this.mods = { shift: e.shiftKey, ctrl: e.ctrlKey || e.metaKey, alt: e.altKey };
      this.render();
    }
    return false;
  }

  private async press(at: P, clicks: number): Promise<void> {
    closeMenus();
    const m = this.model;
    if (!m) return;
    if (this.tool !== 'Text') this.commitText();
    const c: P = this.mods.ctrl ? [snap(at[0]), snap(at[1])] : at;
    switch (this.tool) {
      case 'Select': {
        this.gesture = { kind: 'pending', start: at, shift: !!this.mods.shift };
        const hit = await this.hit(at);
        const g = this.gesture;
        if (!hit || g.kind !== 'pending') return;
        const end = g.end ?? at;
        this.vertex = null;
        if (hit.handle && m.editable) { this.gesture = { kind: 'handle', start: at, end, shape: hit.handle.shape, at: hit.handle.at, effective: false }; break; }
        let clicked = hit.clicked ?? hit.top;
        const shift = !!this.mods.shift;
        if (clicked !== null && clicked !== undefined) {
          if (shift && this.selected.includes(clicked)) { this.selected = this.selected.filter((i) => i !== clicked); this.gesture = { kind: 'idle' }; }
          else {
            if (!shift && !this.selected.includes(clicked)) this.selected = [];
            if (!this.selected.includes(clicked)) this.selected = [...this.selected, clicked].sort((a, b) => a - b);
            this.gesture = { kind: 'move', start: at, end, effective: false };
          }
        } else {
          clicked = hit.topFilled;
          if (clicked !== null && this.selected.includes(clicked)) {
            if (shift) { this.selected = this.selected.filter((i) => i !== clicked); this.gesture = { kind: 'idle' }; }
            else this.gesture = { kind: 'move', start: at, end, effective: false };
          } else {
            if (!shift) this.selected = [];
            this.gesture = { kind: 'rect', start: at, end, toggle: shift, effective: false, filled: hit.topFilled };
          }
        }
        this.lastHit = hit;
        this.render();
        this.showAttributes();
        void this.showThumb();
        if (this.pendingRelease) { const r = this.pendingRelease; this.pendingRelease = null; void this.release(r); }
        break;
      }
      case 'Rectangle': case 'Rounded Rectangle': case 'Oval':
        this.gesture = { kind: 'box', start: at, end: at };
        break;
      case 'Line':
        this.gesture = { kind: 'line', start: c, end: c };
        break;
      case 'Curve': {
        const g = this.gesture;
        if (g.kind === 'curveEnds' && !g.down && (g.start[0] !== g.end[0] || g.start[1] !== g.end[1])) {
          this.gesture = { kind: 'curveControl', e0: g.start, e1: g.end, control: curveControl(g.start, g.end, at, this.mods), down: true };
        } else {
          this.gesture = { kind: 'curveEnds', start: c, end: c, down: true };
        }
        break;
      }
      case 'Polyline': case 'Polygon': {
        const g = this.gesture;
        if (g.kind === 'poly' && clicks > 1) { void this.finishPoly(); return; }
        if (g.kind === 'poly') this.gesture = { kind: 'poly', points: [...g.points, c], down: true };
        else this.gesture = { kind: 'poly', points: [c, c], down: true };
        break;
      }
      case 'Text':
        this.openText(at);
        return;
    }
    this.render();
  }

  private lastHit: AppearanceHit | null = null;
  private pendingRelease: P | null = null;

  private drag(at: P, down: boolean): void {
    const g = this.gesture;
    switch (g.kind) {
      case 'pending': g.end = at; return;
      case 'move': case 'handle': case 'rect':
        if (!down) return;
        g.end = at;
        if (!g.effective && dragged(at[0] - g.start[0], at[1] - g.start[1])) g.effective = true;
        if (g.kind === 'handle' && g.effective) void this.previewHandle(g);
        break;
      case 'box': if (down) g.end = at; break;
      case 'line': if (down) g.end = lineEnd(g.start, at, this.mods); break;
      case 'curveEnds': if (g.down) g.end = lineEnd(g.start, at, this.mods); break;
      case 'curveControl': if (g.down) g.control = curveControl(g.e0, g.e1, at, this.mods); break;
      case 'poly': {
        if (!down) return;   // PolyTool: mouseDragged only (no mouseMoved) -- the last corner follows a drag, not a hover
        const pts = [...g.points];
        const prev = pts.length > 1 ? pts[pts.length - 2] : pts[0];
        pts[pts.length - 1] = lineEnd(prev, at, this.mods);
        this.gesture = { ...g, points: pts };
        break;
      }
      default: return;
    }
    this.render();
  }

  private previewAsked = false;
  private async previewHandle(g: Extract<Gesture, { kind: 'handle' }>): Promise<void> {
    if (this.previewAsked) return;
    this.previewAsked = true;
    try {
      const [dx, dy] = handleDelta(g.at, g.end[0] - g.start[0], g.end[1] - g.start[1], this.mods);
      const r = await this.host.call<{ handles: P[] }>('model.appearanceHandles', { fileId: this.fileId, circuitId: this.circuitId, shape: g.shape, at: g.at, dx, dy, shift: !!this.mods.shift, ctrl: !!this.mods.ctrl, alt: !!this.mods.alt });
      if (this.gesture === g) { g.handles = r.handles; this.render(); }
    } catch { /* the drop still does it */ } finally { this.previewAsked = false; }
  }

  private async release(at: P): Promise<void> {
    const g = this.gesture;
    if (g.kind === 'pending') { this.pendingRelease = at; return; }
    switch (g.kind) {
      case 'move': {
        this.gesture = { kind: 'none' };
        if (!g.effective) { this.chooseVertex(at); break; }
        const [dx, dy] = moveDelta(g.end[0] - g.start[0], g.end[1] - g.start[1], this.chosenHandles(), this.mods);
        if (dx !== 0 || dy !== 0) await this.op('move', { shapes: [...this.selected], dx, dy }, 'Move');
        break;
      }
      case 'handle': {
        this.gesture = { kind: 'none' };
        if (!g.effective) { this.chooseVertex(at); break; }
        const [dx, dy] = handleDelta(g.at, g.end[0] - g.start[0], g.end[1] - g.start[1], this.mods);
        await this.op('handle', { shape: g.shape, at: g.at, dx, dy, shift: !!this.mods.shift, ctrl: !!this.mods.ctrl, alt: !!this.mods.alt }, 'Move Handle');
        break;
      }
      case 'rect': {
        this.gesture = { kind: 'none' };
        if (g.effective) {
          const r = await this.hit(g.start, [g.start[0], g.start[1], g.end[0], g.end[1]]);
          const inRect = r?.inRect ?? [];
          if (g.toggle) this.selected = [...this.selected.filter((i) => !inRect.includes(i)), ...inRect.filter((i) => !this.selected.includes(i))].sort((a, b) => a - b);
          else this.selected = [...new Set([...this.selected, ...inRect])].sort((a, b) => a - b);
        } else if (g.filled !== null) {
          if (g.toggle) this.selected = this.selected.includes(g.filled) ? this.selected.filter((i) => i !== g.filled) : [...this.selected, g.filled].sort((a, b) => a - b);
          else this.selected = [g.filled];
        }
        this.chooseVertex(at);
        break;
      }
      case 'box': {
        this.gesture = { kind: 'none' };
        const b = rectFromDrag(g.start, g.end, this.mods);
        if (b) await this.add({ kind: this.tool === 'Oval' ? 'oval' : this.tool === 'Rounded Rectangle' ? 'roundrect' : 'rect', bounds: b });
        this.setTool('Select');
        await this.selectAdded();
        break;
      }
      case 'line': {
        this.gesture = { kind: 'none' };
        if (g.start[0] !== g.end[0] || g.start[1] !== g.end[1]) await this.add({ kind: 'line', points: [g.start, g.end] });
        this.setTool('Select');
        await this.selectAdded();
        break;
      }
      case 'curveEnds': g.down = false; if (g.start[0] === g.end[0] && g.start[1] === g.end[1]) this.gesture = { kind: 'none' }; break;
      case 'curveControl': {
        this.gesture = { kind: 'none' };
        await this.add({ kind: 'curve', points: [g.e0, g.e1, g.control] });
        this.setTool('Select');
        await this.selectAdded();
        break;
      }
      case 'poly': {
        g.down = false;
        if (closes(g.points)) { g.points = g.points.slice(0, -1); await this.finishPoly(); return; }
        break;
      }
      default: this.gesture = { kind: 'none' };
    }
    this.render();
    this.showAttributes();
    void this.showThumb();
  }

  // A press let go where it was (SelectTool.mouseReleased): the vertex under it, for Add / Remove Vertex.
  private chooseVertex(at: P): void {
    const h0 = this.lastHit;
    this.vertex = null;
    if (!h0 || h0.top === null) return;
    const p = h0.removable ?? h0.insertable;
    if (p && this.selected.includes(h0.top)) this.vertex = { shape: h0.top, at: p };
    void at;
  }

  private added: number | null = null;
  private async add(shape: Record<string, unknown>): Promise<void> {
    const kind = String(shape.kind);
    const tool = this.tool;
    const r = await this.op('add', { shape, attrs: kind === 'polyline' || kind === 'polygon' ? {} : toolAttrs(tool, this.values) }, `${tool} Tool`);
    this.added = r?.index ?? null;
  }
  private async selectAdded(): Promise<void> {
    if (this.added !== null) { this.selected = [this.added]; this.added = null; this.render(); this.showAttributes(); }
  }

  private async finishPoly(): Promise<void> {
    const g = this.gesture;
    if (g.kind !== 'poly') return;
    this.gesture = { kind: 'none' };
    const pts = poly(g.points);
    const tool = this.tool;
    if (pts.length >= 2) await this.add({ kind: tool === 'Polygon' ? 'polygon' : 'polyline', points: pts });
    this.setTool('Select');
    await this.selectAdded();
    this.render();
  }

  // ---- text ---------------------------------------------------------------------------------

  private openText(at: P): void {
    this.commitText();
    const m = this.model;
    if (!m) return;
    // on a text shape: edit it (TextTool: the topmost text that contains the point, filled)
    const onText = [...m.shapes].reverse().find((s) => s.kind === 'text' && inBounds(s.bounds, at));
    const field = h('input', { type: 'text', class: 'apptext mono', 'aria-label': 'Text', spellcheck: 'false', autocomplete: 'off' });
    field.value = onText?.text ?? '';
    const where = onText?.at ?? at;
    const r = this.svg.getBoundingClientRect(), sr = this.stage.getBoundingClientRect();
    const size = Number(/\s(\d+)$/.exec(onText?.attrs.font ?? this.values.font)?.[1] ?? 12) * this.view.zoom;
    field.style.left = `${r.left - sr.left + (where[0] - this.view.x) * this.view.zoom - 4}px`;
    field.style.top = `${r.top - sr.top + (where[1] - this.view.y) * this.view.zoom - size}px`;
    field.style.fontSize = `${Math.max(11, size)}px`;
    this.stage.append(field);
    this.textField = field;
    this.editingText = onText ? onText.i : null;
    const origin = { at: where, shape: onText?.i ?? null };
    field.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' && !e.isComposing) { e.preventDefault(); void this.commitText(); }
      else if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); this.dropText(); }
    });
    (field as HTMLInputElement & { origin?: typeof origin }).origin = origin;
    this.render();
    field.focus();
    // the press's own mousedown focuses the drawing after this (its default action): the field takes it back
    setTimeout(() => { if (this.textField === field) field.focus(); }, 0);
  }

  private dropText(): void {
    this.textField?.remove();
    this.textField = null;
    this.editingText = null;
    this.render();
  }

  private async commitText(): Promise<void> {
    const f = this.textField as (HTMLInputElement & { origin?: { at: P; shape: number | null } }) | null;
    if (!f) return;
    const o = f.origin!;
    const text = f.value;
    this.dropText();
    if (o.shape === null) {
      if (text !== '') {
        const r = await this.op('add', { shape: { kind: 'text', at: o.at, text }, attrs: toolAttrs('Text', this.values) }, 'Text Tool');
        if (r?.index !== undefined) this.selected = [];
      }
    } else if (this.shape(o.shape)?.text !== text) {
      await this.op('text', { shape: o.shape, text }, 'Text Tool');
      this.selected = [];
    }
    this.render();
  }
}

const inBounds = (b: [number, number, number, number], p: P) => p[0] >= b[0] - 2 && p[0] <= b[0] + b[2] + 2 && p[1] >= b[1] - 2 && p[1] <= b[1] + b[3] + 2;

export function kindName(kind: string): string {
  const n: Record<string, string> = {
    rect: 'Rectangle', roundrect: 'Rounded Rectangle', oval: 'Oval', line: 'Line', polyline: 'Polyline', polygon: 'Polygon',
    curve: 'Curve', text: 'Text', port: 'Port', anchor: 'Anchor',
  };
  return n[kind] ?? 'Shape';
}

// The attribute table: the original's attributes of a shape or a tool (names English, values as the original shows).
function attrTable(title: string, names: string[], values: Record<string, string>, editable: boolean, set: (name: string, value: string) => void): HTMLElement {
  const rows = names.map((n) => {
    const v = values[n] ?? '';
    let input: HTMLElement;
    if (n === 'align' || n === 'paintType' || n === 'facing') {
      const options = n === 'align' ? ALIGN_NAMES : n === 'paintType' ? PAINT_NAMES : { east: 'East', west: 'West', north: 'North', south: 'South' };
      const s = h('select', { 'aria-label': ATTR_NAMES[n] ?? n, disabled: !editable }, ...(v === '' ? [h('option', { value: '', selected: true }, '—')] : []),
        ...Object.entries(options).map(([k, label]) => h('option', { value: k, selected: k === v }, label))) as HTMLSelectElement;
      s.addEventListener('change', () => { if (s.value) set(n, s.value); });
      input = s;
    } else if (n === 'stroke' || n === 'fill') {
      const c = h('input', { type: 'color', value: /^#[0-9a-f]{6}$/i.test(v) ? v : '#000000', 'aria-label': ATTR_NAMES[n] ?? n, disabled: !editable }) as HTMLInputElement;
      c.addEventListener('change', () => set(n, c.value));
      input = c;
    } else if (n === 'stroke-width' || n === 'rx') {
      const x = h('input', { type: 'number', min: '1', max: n === 'rx' ? '1000' : '8', value: v, class: 'mono', 'aria-label': ATTR_NAMES[n] ?? n, disabled: !editable }) as HTMLInputElement;
      x.addEventListener('change', () => { if (x.value) set(n, x.value); });
      input = x;
    } else if (n === 'font') {
      const [family, style, size] = parseFont(v);
      const fam = h('select', { 'aria-label': 'Font family', disabled: !editable }, ...['SansSerif', 'Serif', 'Monospaced', 'Dialog', 'DialogInput'].map((f) => h('option', { value: f, selected: f === family }, f))) as HTMLSelectElement;
      const sty = h('select', { 'aria-label': 'Font style', disabled: !editable }, ...['plain', 'bold', 'italic', 'bolditalic'].map((s) => h('option', { value: s, selected: s === style }, s))) as HTMLSelectElement;
      const sz = h('input', { type: 'number', min: '1', max: '200', value: String(size), class: 'mono', 'aria-label': 'Font size', disabled: !editable }) as HTMLInputElement;
      const send = () => set(n, `${fam.value} ${sty.value} ${sz.value || size}`);
      fam.addEventListener('change', send); sty.addEventListener('change', send); sz.addEventListener('change', send);
      input = h('span', { class: 'fontfields' }, fam, sty, sz);
    } else {
      input = h('span', { class: 'mono' }, v);
    }
    return h('tr', {}, h('th', {}, ATTR_NAMES[n] ?? n), h('td', {}, input));
  });
  return h('div', { class: 'attrs' }, h('p', { class: 'attrtitle' }, title), names.length ? h('table', { class: 'attrtable' }, h('tbody', {}, ...rows)) : h('p', { class: 'hint' }, '이 도구에는 속성이 없습니다.'));
}

export function parseFont(s: string): [string, string, number] {
  const m = /^(.*?)\s+(plain|bold|italic|bolditalic)\s+(\d+)$/.exec((s || '').trim());
  return m ? [m[1], m[2], Number(m[3])] : ['SansSerif', 'plain', 12];
}

// The circuit's layout, small, the chosen ports' pins marked (LayoutThumbnail).
export function drawThumb(canvas: HTMLCanvasElement, s: Snapshot, pins: Set<string>): void {
  const ctx = canvas.getContext('2d');
  if (!ctx) return;
  const W = canvas.width, H = canvas.height;
  ctx.clearRect(0, 0, W, H);
  ctx.fillStyle = '#ffffff';
  ctx.fillRect(0, 0, W, H);
  let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity;
  for (const c of s.components) { const [x, y, w, h0] = c.bounds; x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x + w); y1 = Math.max(y1, y + h0); }
  for (const w of s.wires) for (const p of [w.a, w.b]) { x0 = Math.min(x0, p[0]); y0 = Math.min(y0, p[1]); x1 = Math.max(x1, p[0]); y1 = Math.max(y1, p[1]); }
  if (!Number.isFinite(x0)) return;
  const k = Math.min((W - 16) / Math.max(1, x1 - x0), (H - 16) / Math.max(1, y1 - y0), 1);
  const tx = (x: number) => 8 + (x - x0) * k, ty = (y: number) => 8 + (y - y0) * k;
  ctx.strokeStyle = '#1f3b63';
  ctx.lineWidth = 1;
  for (const w of s.wires) { ctx.beginPath(); ctx.moveTo(tx(w.a[0]), ty(w.a[1])); ctx.lineTo(tx(w.b[0]), ty(w.b[1])); ctx.stroke(); }
  for (const c of s.components) {
    const [x, y, w, h0] = c.bounds;
    const on = c.name === 'Pin' && pins.has(`${c.loc[0]},${c.loc[1]}`);
    ctx.fillStyle = on ? '#0055a5' : '#f4f6f9';
    ctx.strokeStyle = on ? '#0055a5' : '#2b3743';
    ctx.fillRect(tx(x), ty(y), Math.max(2, w * k), Math.max(2, h0 * k));
    ctx.strokeRect(tx(x), ty(y), Math.max(2, w * k), Math.max(2, h0 * k));
  }
}

export { DRAG_TOLERANCE };
