/* The Canvas's overlays, driven (N-15, D-151): what the student does and
   what the engine says, turned into the overlays of this folder.

   - Influence (v1 P-01, I-187): I shows what the chosen parts and wires
     drive, Shift+I what drives them; [ and ] show a step less or more;
     Esc clears it; an edit of the circuit or another circuit clears it.
     Right-click Influence ▸ also has Both, Path Between Selected (two
     parts) and Through Registers.
   - Signal Flow (v1 P-07, I-188): with Signal Flow on Click (the toolbar's
     Signal Flow, Ctrl+Shift+F; on at start) one click with the Edit tool
     -- let go within 4 px, no second press in 150 ms -- shows how the
     signal goes from there (Shift: backward, to there); an empty place, Esc,
     an edit, another circuit stop it.  Right-click Signal Flow ▸ has the
     settings (Flow Speed, Through Registers, Active Path Only, Reduce
     Motion, Smooth (60 fps)), for this run.
   - While the Cycle View is shown: the active path (Active Path in the Wire
     Colors panel, on at start) and the instruction's field colours.
   - Bus values (Hex, Dec, Signed, Off) and Colors: Values/Groups: the Wire
     Colors panel.
   - Signal Group ▸, Net Information…, Highlight Net (a wire's right
     click), Add/Edit/Fit/Delete Area Memo (an empty place's).

   Settings live for this run only (the lab-PC rule).  What is chosen comes
   from the Canvas's hcs:selection event (events.ts), the tool in use from
   hcs:tool or the toolbar.  Edits go to the engine as intents
   (edit.signalGroup, edit.areaMemo: one undo step each); the rest only
   reads. */

import type { ActivePathResult, AreaMemo, FieldPaths, FlowPath, InfluenceMode, InfluenceResult, ModelChanged, NetInfo, SignalGroup, SimValues, WindowMethod } from '../../../main/protocol.ts';
import { h } from '../../shared/dom.ts';
import type { CircuitCanvas } from '../canvas.ts';
import { onSelection, onTool, type SelectionDetail } from '../events.ts';
import type { Scene } from '../scene.ts';
import type { Box } from '../shapes.ts';
import { toCircuit } from '../view.ts';
import { BandOverlay } from './bands.ts';
import { BusValueOverlay } from './busvalues.ts';
import { memoDialog, netDialog } from './dialogs.ts';
import { FlowOverlay } from './flow.ts';
import { InfluenceOverlay } from './influence.ts';
import { BUS_MODE_NAMES, BUS_MODES, type BusMode, busText, FLOW_SPEED_NAMES, FLOW_SPEED_PX, type FlowSpeed, type FlowTarget, flowTarget, GROUP_COLORS, GROUP_NAMES, memoStart, widen } from './logic.ts';
import { closeMenus, type MenuEntry, menuOpen, SEPARATOR } from './menu.ts';
import { memoAt, MemoOverlay } from './memos.ts';
import { influenceStatus, typing } from './words.ts';

export interface OverlayHost {
  board: CircuitCanvas;
  call<T>(method: WindowMethod, params: Record<string, unknown>): Promise<T>;
  ready(): boolean;                              // the engine answers
  cycleViewShown(): boolean;                     // the Cycle View tab is on show
  note(cls: '' | 'err' | 'ok', text: string | null): void;   // the status bar's fact about the last action
  failed(name: string, e: unknown): void;        // a command failed: the window's words for it
  changed(): void;                               // the status bar again
  // the items a part's own right click menu starts with (N-11: a subcircuit instance's View, Edit Appearance,
  // Port Order…, Auto Appearance, Edit Original File); the overlays' items follow
  partMenu?(at: [number, number], part: string | null): MenuEntry[];
}

export interface FlowSettings { onClick: boolean; speed: FlowSpeed; throughRegisters: boolean; activePathOnly: boolean; reduceMotion: boolean; smooth: boolean }

export class Overlays {
  readonly memos = new MemoOverlay();
  readonly bands = new BandOverlay();
  readonly bus = new BusValueOverlay();
  readonly influence = new InfluenceOverlay();
  readonly flow = new FlowOverlay();
  readonly settings: FlowSettings;
  activePathOn = true;
  influenceThrough = false;
  private selection: SelectionDetail = { fileId: '', circuitId: '', ids: [] };
  private tool = '';
  private inf: { fileId: string; circuitId: string; from: string[]; mode: InfluenceMode; depth: number } | null = null;
  private flowFrom: { fileId: string; circuitId: string; target: NonNullable<FlowTarget>; backward: boolean } | null = null;
  private highlight: { fileId: string; circuitId: string; wires: Set<string> } | null = null;
  private asking = { active: false, again: false };
  private fieldsCycle = '';
  private fieldsUnsupported = false;
  private readonly host: OverlayHost;
  private readonly listeners = new Set<() => void>();

  constructor(host: OverlayHost) {
    this.host = host;
    const reduce = typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
    this.settings = { onClick: true, speed: 'normal', throughRegisters: false, activePathOnly: false, reduceMotion: reduce, smooth: false };
    this.flow.reduceMotion = reduce;
    const b = host.board;
    for (const o of [this.memos, this.bands, this.bus, this.influence, this.flow]) b.addOverlay(o);
    this.flow.attach(b);
    this.influence.faded = () => this.flow.running;
    this.influence.moreChips = (d) => this.bus.boxes(d);
    this.influence.drawChips = (d, nets) => this.bus.draw(d, (c) => nets.has(c.net));
    this.flow.moreChips = (d) => this.bus.boxes(d);
    this.memos.moreChips = (d) => (d ? this.bus.boxes(d) : []);   // the picture export has no bus chips
    // another circuit on show: the influence and the highlight are the old one's
    b.addOverlay({ sceneChanged: () => { this.clearInfluence(false); this.clearHighlight(); this.bands.activePath = []; this.bands.fields = []; this.fieldsCycle = ''; this.emit(); } });
    onSelection((d) => { this.selection = d; });
    onTool((t) => { this.tool = t; });
    this.listenClicks();
    window.addEventListener('keydown', (e) => { if (this.onKey(e)) { e.preventDefault(); e.stopPropagation(); } });
    (window as unknown as { __hcsOverlays?: Overlays }).__hcsOverlays = this;   // the tests read what is shown
  }

  // Something the status bar or the panels show changed.
  onChange(f: () => void): void { this.listeners.add(f); }

  // What the overlays show over the circuit on show, as boxes (circuit units): the flow's arcs and labels, the
  // influence's chips and dotted lines, the bus values' chips.  Quick Attributes is placed off them (D-158).
  obstacles(): Box[] {
    const s = this.scene;
    if (!s) return [];
    return [...this.flow.obstacles(s.fileId, s.circuitId), ...this.influence.obstacles(), ...this.bus.boxes({ canvas: this.host.board, scene: s })];
  }
  private emit(): void { for (const f of this.listeners) f(); this.host.changed(); }

  private get scene(): Scene | null { return this.host.board.scene; }

  // The parts and wires chosen on the Canvas now (the circuit on show).
  selected(): string[] {
    const s = this.scene, d = this.selection;
    return s && d.fileId === s.fileId && d.circuitId === s.circuitId ? d.ids.filter((id) => s.components.has(id) || s.wires.has(id)) : [];
  }

  // The tool in use: the hcs:tool event's, else the toolbar's checked tool.
  currentTool(): string {
    if (this.tool) return this.tool;
    return document.querySelector('.tools-seg [role=radio][aria-checked="true"]')?.getAttribute('aria-label') ?? 'Edit';
  }

  // ---- influence (P-01) --------------------------------------------------------------------------

  async showInfluence(mode: InfluenceMode, from = this.selected(), depth = -1): Promise<void> {
    const s = this.scene;
    if (!s || !from.length) return;
    const ask = { fileId: s.fileId, circuitId: s.circuitId, from: [...from], mode, depth };
    try {
      const r = await this.host.call<InfluenceResult>('trace.influence', { ...ask, throughRegisters: this.influenceThrough, depth });
      if (this.scene !== s) return;
      this.inf = { ...ask, depth: r.depth };
      this.influence.result = r;
      this.host.note('', null);
    } catch (e) {
      this.host.failed('Influence', e);
    }
    this.host.board.invalidate();
    this.emit();
  }

  get influenceShown(): InfluenceResult | null { return this.influence.result; }

  async widenInfluence(delta: 1 | -1): Promise<void> {
    const r = this.influence.result, i = this.inf;
    if (!r || !i || i.mode === 'between') return;
    await this.showInfluence(i.mode, i.from, widen(r.depth, r.maxDepth, delta));
  }

  clearInfluence(draw = true): void {
    if (!this.influence.result && !this.inf) return;
    this.influence.result = null;
    this.inf = null;
    if (draw) { this.host.board.invalidate(); this.emit(); }
  }

  setInfluenceThrough(on: boolean): void {
    this.influenceThrough = on;
    const i = this.inf;
    if (i) void this.showInfluence(i.mode, i.from, -1);
  }

  // ---- Signal Flow (P-07) --------------------------------------------------------------------------

  async startFlow(target: NonNullable<FlowTarget>, backward: boolean): Promise<void> {
    const s = this.scene;
    if (!s) return;
    const set = this.settings;
    const params: Record<string, unknown> = { fileId: s.fileId, circuitId: s.circuitId, backward, throughRegisters: set.throughRegisters, activePathOnly: set.activePathOnly };
    if ('wire' in target) { params.wire = target.wire; params.at = target.at; } else { params.componentId = target.componentId; if (target.port >= 0) params.port = target.port; }
    try {
      const p = await this.host.call<FlowPath>('flow.path', params);
      if (this.scene !== s) return;
      this.flowFrom = { fileId: s.fileId, circuitId: s.circuitId, target, backward };
      this.flow.fileId = s.fileId;
      this.flow.speedPx = FLOW_SPEED_PX[set.speed];
      this.flow.start(p);
    } catch (e) {
      this.host.failed('Signal Flow', e);
    }
    this.emit();
  }

  stopFlow(): void {
    if (!this.flow.running) return;
    this.flow.stop();
    this.flowFrom = null;
    this.emit();
  }

  toggleOnClick(): void {
    this.settings.onClick = !this.settings.onClick;
    if (!this.settings.onClick) this.stopFlow();
    this.host.note('', `Signal Flow on Click: ${this.settings.onClick ? 'On' : 'Off'}`);
    this.emit();
  }

  setFlow<K extends keyof FlowSettings>(k: K, v: FlowSettings[K]): void {
    this.settings[k] = v;
    this.flow.speedPx = FLOW_SPEED_PX[this.settings.speed];
    this.flow.reduceMotion = this.settings.reduceMotion;
    this.flow.smooth = this.settings.smooth;
    if (k === 'onClick' && !v) this.stopFlow();
    // the way itself changes: find it again from the same place
    const f = this.flowFrom;
    if (f && (k === 'throughRegisters' || k === 'activePathOnly')) void this.startFlow(f.target, f.backward);
    this.host.board.invalidate();
    this.emit();
  }

  // One click on the Canvas (the Edit tool, let go where pressed, no second press within 150 ms).
  async clickAt(at: [number, number], backward: boolean): Promise<void> {
    const s = this.scene, b = this.host.board;
    if (!s) return;
    const t = flowTarget(s, at, (p) => b.partAt(p), (p) => b.wireAt(p));
    if (!t) { this.stopFlow(); return; }
    if (this.settings.onClick && this.currentTool() === 'Edit') await this.startFlow(t, backward);
  }

  private listenClicks(): void {
    const c = this.host.board.canvas;
    let press: { x: number; y: number } | null = null;
    let timer = 0;
    c.addEventListener('pointerdown', (e) => {
      if (e.button !== 0 || e.ctrlKey || e.metaKey || e.altKey || c.classList.contains('grab')) { press = null; return; }
      if (timer) { clearTimeout(timer); timer = 0; press = null; return; }   // a second press: a double click
      press = { x: e.clientX, y: e.clientY };
    });
    c.addEventListener('pointerup', (e) => {
      const p = press;
      press = null;
      if (!p || e.button !== 0 || Math.hypot(e.clientX - p.x, e.clientY - p.y) > 4) return;
      const r = c.getBoundingClientRect();
      const at = toCircuit(this.host.board.view, [e.clientX - r.left, e.clientY - r.top]);
      const shift = e.shiftKey;
      timer = window.setTimeout(() => { timer = 0; void this.clickAt(at, shift); }, 150);
    });
  }

  // ---- while the Cycle View is shown: the active path and the field colours ---------------------

  // The values or the cycle changed (or the Cycle View came into view): ask again, one question at a time.
  async refreshCycle(cycleChanged = false): Promise<void> {
    const s = this.scene;
    if (!s || !this.host.ready() || !this.host.cycleViewShown()) {
      if (this.bands.activePath.length || this.bands.fields.length) { this.bands.activePath = []; this.bands.fields = []; this.fieldsCycle = ''; this.host.board.invalidate(); }
      return;
    }
    if (this.asking.active) { this.asking.again = true; return; }
    this.asking.active = true;
    try {
      if (this.activePathOn) {
        const r = await this.host.call<ActivePathResult>('flow.activePath', { fileId: s.fileId, circuitId: s.circuitId });
        if (this.scene === s) this.bands.activePath = r.muxes.flatMap((m) => m.segments);
      } else this.bands.activePath = [];
      const key = `${s.fileId} ${s.circuitId} ${s.modelVersion}`;
      if (!this.fieldsUnsupported && (cycleChanged || this.fieldsCycle !== key)) {
        try {
          const f = await this.host.call<FieldPaths>('record.fieldPaths', { fileId: s.fileId, circuitId: s.circuitId });
          if (this.scene === s) {
            this.bands.fields = Object.entries(f.fields).map(([name, wires]) => ({ name, wires }));
            this.fieldsCycle = key;
          }
        } catch (e) {
          // an engine without the Cycle View's record (before N-14): no field colours
          if ((e as { code?: number }).code === -32601 || /not allowed|no method/i.test(String((e as Error).message))) this.fieldsUnsupported = true;
          this.bands.fields = [];
        }
      }
    } catch {
      this.bands.activePath = [];
    } finally {
      this.asking.active = false;
    }
    this.host.board.invalidate();
    if (this.asking.again) { this.asking.again = false; void this.refreshCycle(); }
  }

  setActivePath(on: boolean): void {
    this.activePathOn = on;
    if (!on) this.bands.activePath = [];
    void this.refreshCycle();
    this.host.board.invalidate();
    this.emit();
  }

  // ---- the Wire Colors panel's choices -----------------------------------------------------------

  setGroupsShown(on: boolean): void {
    this.bands.showGroups = on;
    this.bus.groups = on;
    this.host.board.invalidate();
    this.emit();
  }

  setBusMode(m: BusMode): void {
    this.bus.mode = m;
    this.host.board.invalidate();
    this.emit();
  }

  // ---- signal groups, memos, a net ----------------------------------------------------------------

  async setGroup(wire: string, group: SignalGroup | null): Promise<void> {
    const s = this.scene;
    if (!s) return;
    try {
      await this.host.call('edit.signalGroup', { fileId: s.fileId, circuitId: s.circuitId, wire, ...(group ? { group } : {}) });
    } catch (e) {
      this.host.failed('Signal Group', e);
    }
  }

  groupOf(wire: string): { group: SignalGroup; assigned: boolean } | null {
    const s = this.scene, n = s?.wireNet(wire);
    return n ? s!.groups.get(n.id) ?? null : null;
  }

  async memo(at: [number, number], m: { around?: string[]; delete?: boolean; fit?: boolean } = {}): Promise<void> {
    const s = this.scene;
    if (!s) return;
    const here = memoAt(s.memos, at);
    const base = { fileId: s.fileId, circuitId: s.circuitId, at: [Math.round(at[0]), Math.round(at[1])] };
    try {
      if (m.delete && here) { await this.host.call('edit.areaMemo', { ...base, delete: true }); return; }
      if (m.fit && here) { await this.host.call('edit.areaMemo', { ...base, ids: m.around ?? [] }); return; }
      const start: AreaMemo = here ?? { ...memoStart(s, at, m.around ?? []), color: s.memos.length % 12, text: '' };
      const got = await memoDialog(start, !here);
      if (!got) return;
      await this.host.call('edit.areaMemo', { ...base, ...(here ? {} : { ids: m.around ?? [] }), text: got.text, color: got.color, bounds: [got.x, got.y, got.w, got.h] });
    } catch (e) {
      this.host.failed('Area Memo', e);
    }
  }

  async netInfo(wire: string): Promise<void> {
    const s = this.scene;
    if (!s) return;
    try {
      await netDialog(await this.host.call<NetInfo>('trace.net', { fileId: s.fileId, circuitId: s.circuitId, wire }));
    } catch (e) {
      this.host.failed('Net Information', e);
    }
  }

  // Add to Cycle View (the Cycle View's row, N-14): the wire's net.
  async addRow(wire: string): Promise<void> {
    const s = this.scene;
    if (!s) return;
    try {
      await this.host.call('record.addRow', { fileId: s.fileId, circuitId: s.circuitId, wireId: wire });
    } catch (e) {
      this.host.failed('Add to Cycle View', e);
    }
  }

  toggleHighlight(wire: string): void {
    const s = this.scene;
    const n = s?.wireNet(wire);
    if (!s || !n) return;
    if (this.highlight && this.highlight.wires.has(wire)) { this.clearHighlight(); return; }
    this.highlight = { fileId: s.fileId, circuitId: s.circuitId, wires: new Set(n.wires) };
    this.bands.highlight = this.highlight.wires;
    this.host.board.invalidate();
  }

  highlighted(wire: string): boolean { return this.highlight?.wires.has(wire) ?? false; }

  private clearHighlight(): void {
    if (!this.highlight) return;
    this.highlight = null;
    this.bands.highlight = null;
    this.host.board.invalidate();
  }

  // ---- what the engine says ---------------------------------------------------------------------

  // An edit (or its undo): the influence and the flow are the old circuit's (v1: an edit clears them);
  // a highlighted net whose wires changed goes; the cycle overlays ask again.
  modelChanged(c: ModelChanged): void {
    const shape = c.added.length > 0 || c.removed.length > 0;
    if (shape && this.inf && this.inf.fileId === c.fileId && this.inf.circuitId === c.circuitId) this.clearInfluence();
    if (shape && this.flowFrom && this.flowFrom.fileId === c.fileId && this.flowFrom.circuitId === c.circuitId) this.stopFlow();
    const hl = this.highlight;
    if (hl && hl.fileId === c.fileId && hl.circuitId === c.circuitId) {
      const s = this.scene;
      const first = [...hl.wires][0];
      const n = s?.wireNet(first);
      if (!n || n.wires.length !== hl.wires.size || n.wires.some((w) => !hl.wires.has(w))) this.clearHighlight();
    }
    if (this.scene?.fileId === c.fileId) void this.refreshCycle();
  }

  values(v: SimValues): void {
    const s = this.scene;
    if (s && s.fileId === v.fileId && this.host.cycleViewShown() && this.activePathOn) void this.refreshCycle();
  }

  cycleChanged(fileId: string): void {
    if (this.scene?.fileId === fileId) void this.refreshCycle(true);
  }

  fileClosed(fileId: string): void {
    if (this.inf?.fileId === fileId) this.clearInfluence();
    if (this.flowFrom?.fileId === fileId) this.stopFlow();
    if (this.highlight?.fileId === fileId) this.clearHighlight();
  }

  // ---- keys (I-187, I-188, I-19) --------------------------------------------------------------------

  onKey(e: KeyboardEvent): boolean {
    if (document.querySelector('dialog[open]') || menuOpen() || typing(e.target)) return false;
    if (document.querySelector('.legend-panel:not([hidden]), .zoom-menu:not([hidden])')) return false;
    const b = this.host.board;
    if (!b.scene || !b.root.isConnected) return false;
    const mod = e.ctrlKey || e.metaKey;
    if (mod && e.shiftKey && !e.altKey && e.key.toLowerCase() === 'f') { this.toggleOnClick(); return true; }
    if (mod || e.altKey) return false;
    const onCanvas = document.activeElement === b.canvas || document.activeElement === document.body || document.activeElement === null;
    if (e.key === 'Escape') {
      // one at a time: a running flow first, then the influence (a menu or a panel closed itself before)
      if (this.flow.running) { this.stopFlow(); return true; }
      if (this.influence.result) { this.clearInfluence(); return true; }
      return false;
    }
    if (!onCanvas) return false;
    if ((e.key === 'i' || e.key === 'I') && this.selected().length && this.currentTool() === 'Edit') {
      void this.showInfluence(e.shiftKey ? 'backward' : 'forward');
      return true;
    }
    if ((e.key === '[' || e.key === ']') && this.influence.result && this.inf?.mode !== 'between') {
      void this.widenInfluence(e.key === '[' ? -1 : 1);
      return true;
    }
    return false;
  }

  // ---- the right-click items: the Canvas's menu (N-10, app/menus/canvas-items.ts) places them by id ------

  menu(at: [number, number]): MenuEntry[] {
    const s = this.scene, b = this.host.board;
    if (!s) return [];
    const part = b.partAt(at);
    const wire = part ? null : b.wireAt(at);
    const out: MenuEntry[] = [...(this.host.partMenu?.(at, part) ?? [])];
    if (out.length) out.push(SEPARATOR);
    if (wire) {
      const g = this.groupOf(wire);
      out.push({ id: 'netInfo', label: 'Net Information…', run: () => void this.netInfo(wire) });
      out.push({ id: 'addRow', label: 'Add to Cycle View', run: () => void this.addRow(wire) });
      out.push({ id: 'highlight', label: this.highlighted(wire) ? 'Clear Net Highlight' : 'Highlight Net', run: () => this.toggleHighlight(wire) });
      out.push({ id: 'signalGroup', label: 'Signal Group', items: [
        ...(['control', 'data', 'address'] as SignalGroup[]).map((k) => ({ label: GROUP_NAMES[k], checked: g?.group === k, radio: true, run: () => void this.setGroup(wire, k) })),
        { label: 'None', checked: !g || !g.assigned, radio: true, run: () => void this.setGroup(wire, null) },
      ] });
    }
    const target = part ?? wire;
    const sel = this.selected();
    const starts = target && sel.includes(target) && sel.length > 1 ? sel : target ? [target] : [];
    const partsChosen = sel.filter((id) => s.components.has(id));
    const inf: MenuEntry[] = [];
    if (starts.length) {
      inf.push({ label: 'Show Influence (Forward)', keys: 'I', run: () => void this.showInfluence('forward', starts) });
      inf.push({ label: 'Show Influence (Backward)', keys: 'Shift+I', run: () => void this.showInfluence('backward', starts) });
      inf.push({ label: 'Show Influence (Both)', run: () => void this.showInfluence('both', starts) });
    }
    if (partsChosen.length === 2) inf.push({ label: 'Path Between Selected', run: () => void this.showInfluence('between', partsChosen) });
    inf.push({ label: 'Through Registers', checked: this.influenceThrough, run: () => this.setInfluenceThrough(!this.influenceThrough) });
    if (this.influence.result) inf.push({ label: 'Clear Influence', keys: 'Esc', run: () => this.clearInfluence() });
    if (inf.length > 1 || this.influence.result) out.push({ id: 'influence', label: 'Influence', items: inf });
    const f = this.settings;
    const flow: MenuEntry[] = [];
    const t = flowTarget(s, at, (p) => b.partAt(p), (p) => b.wireAt(p));
    if (t) {
      flow.push({ label: 'Show Signal Flow', run: () => void this.startFlow(t, false) });
      flow.push({ label: 'Show Signal Flow (Backward)', run: () => void this.startFlow(t, true) });
    }
    if (this.flow.running) flow.push({ label: 'Stop Signal Flow', run: () => this.stopFlow() });
    if (flow.length) flow.push(SEPARATOR);
    flow.push({ label: 'Signal Flow on Click', keys: 'Ctrl+Shift+F', checked: f.onClick, run: () => this.toggleOnClick(), title: 'Edit Tool로 부품이나 선을 누르면 신호가 가는 길을 흐름으로 보입니다(Shift+누름: 거꾸로)' });
    flow.push({ label: 'Flow Speed', items: (['slow', 'normal', 'fast'] as FlowSpeed[]).map((k) => ({ label: FLOW_SPEED_NAMES[k], checked: f.speed === k, radio: true, run: () => this.setFlow('speed', k) })) });
    flow.push({ label: 'Through Registers', checked: f.throughRegisters, run: () => this.setFlow('throughRegisters', !f.throughRegisters), title: '레지스터와 메모리를 넘어 다음 사이클까지 이어서 보입니다(점선 흐름)' });
    flow.push({ label: 'Active Path Only', checked: f.activePathOnly, run: () => this.setFlow('activePathOnly', !f.activePathOnly), title: '지금 값으로 MUX·Demux·Decoder가 고른 가지만 따라갑니다' });
    flow.push({ label: 'Reduce Motion', checked: f.reduceMotion, run: () => this.setFlow('reduceMotion', !f.reduceMotion), title: '움직이지 않고 방향 화살표와 순서 번호로 보입니다' });
    flow.push({ label: 'Smooth (60 fps)', checked: f.smooth, run: () => this.setFlow('smooth', !f.smooth), title: '초당 30장 대신 60장으로 그립니다' });
    out.push({ id: 'flow', label: 'Signal Flow', items: flow });
    if (!part && !wire) {
      const here = memoAt(s.memos, at);
      if (!here) out.push({ id: 'memo', label: 'Add Area Memo…', run: () => void this.memo(at, { around: partsChosen.length ? sel : [] }) });
      else {
        out.push({ id: 'memo', label: 'Edit Area Memo…', run: () => void this.memo(at) });
        if (sel.length) out.push({ id: 'memo', label: 'Fit Area Memo to Selection', run: () => void this.memo(at, { fit: true, around: sel }) });
        out.push({ id: 'memo', label: 'Delete Area Memo', run: () => void this.memo(at, { delete: true }) });
      }
    }
    return out;
  }

  // ---- the status bar and the Wire Colors panel ---------------------------------------------------

  statusNodes(): Node[] {
    const out: Node[] = [];
    const r = this.influence.result;
    if (r && this.scene) {
      out.push(h('span', { class: 'ovstatus' }, influenceStatus(r)), h('span', { class: 'ovhint' }, r.mode === 'between' ? 'Esc 키로 지웁니다' : '[ ] 키로 좁히거나 넓히고 Esc 키로 지웁니다'));
    }
    if (this.flow.running) out.push(h('span', { class: 'ovstatus' }, `Signal Flow: ${this.flow.path?.backward ? 'Backward' : 'Forward'}`), h('span', { class: 'ovhint' }, 'Esc 키로 멈춥니다'));
    return out;
  }

  // The rows the Wire Colors panel shows under the value colours (the choices of this folder).
  legendRows(): HTMLElement {
    const box = h('div', { class: 'ovlegend' });
    const render = () => {
      const values = h('button', { type: 'button', 'aria-pressed': String(!this.bands.showGroups) }, 'Values');
      const groups = h('button', { type: 'button', 'aria-pressed': String(this.bands.showGroups) }, 'Groups');
      values.addEventListener('click', () => this.setGroupsShown(false));
      groups.addEventListener('click', () => this.setGroupsShown(true));
      const bus = h('select', { 'aria-label': 'Bus Values' }, ...BUS_MODES.map((m) => h('option', { value: m, selected: m === this.bus.mode }, BUS_MODE_NAMES[m]))) as HTMLSelectElement;
      bus.addEventListener('change', () => this.setBusMode(bus.value as BusMode));
      const active = h('input', { type: 'checkbox', checked: this.activePathOn }) as HTMLInputElement;
      active.addEventListener('change', () => this.setActivePath(active.checked));
      box.replaceChildren(...([
        h('div', { class: 'ovrow', title: '선 색은 늘 값을 뜻합니다. Groups: 그룹이 있는 선 옆에 그룹 색의 얇은 테두리를 더합니다. 이름이 control인 서브회로의 출력 선은 따로 정하지 않아도 Control 그룹입니다.' },
          h('span', { class: 'ovlabel' }, 'Colors'), h('span', { class: 'ovseg', role: 'group', 'aria-label': 'Colors' }, values, groups)),
        this.bands.showGroups ? h('ul', { class: 'ovgroups' }, ...(Object.keys(GROUP_COLORS) as SignalGroup[]).map((k) =>
          h('li', {}, h('span', { class: 'swatch', style: `background:${GROUP_COLORS[k]}` }), GROUP_NAMES[k]))) : null,
        h('label', { class: 'ovrow', title: '시뮬레이션 중 버스마다 지금 값을 칩으로 보입니다. 파일은 바뀌지 않습니다.' }, h('span', { class: 'ovlabel' }, 'Bus Values'), bus),
        h('label', { class: 'ovrow legend-opt', title: 'Cycle View가 보이는 동안 고른 사이클에서 MUX가 실제로 고른 입력을 진하게 보입니다.' }, active, 'Active Path'),
      ] as (HTMLElement | null)[]).filter((x): x is HTMLElement => x !== null));
    };
    render();
    this.onChange(render);
    return box;
  }

  closeMenus(): void { closeMenus(); }

  // What the overlays show now, as plain data (the end-to-end tests read it).
  shown(): {
    flow: { running: boolean; backward: boolean; ends: string[]; lit: number; labels: string[]; onClick: boolean; t: number; total: number; jumps: number; drawn: FlowOverlay['drawn'] };
    influence: InfluenceResult | null; activePath: number; fields: string[]; groups: boolean; highlight: number;
    bus: { net: string; text: string; box: { x0: number; y0: number; x1: number; y1: number } }[]; memos: AreaMemo[];
  } {
    const s = this.scene, st = this.flow.state();
    const bus = s ? this.bus.chips({ canvas: this.host.board, scene: s }).flatMap((c) => {
      const v = busText(s.values.get(c.net), this.bus.mode);
      return v === null ? [] : [{ net: c.net, text: c.prefix + v, box: c.box }];
    }) : [];
    return {
      flow: { running: this.flow.running, backward: this.flow.path?.backward ?? false, ends: this.flow.path?.endpoints.map((e) => `${e.kind} ${e.label}`) ?? [], lit: st?.lit ?? 0, labels: st?.labels ?? [], onClick: this.settings.onClick,
        t: st?.t ?? 0, total: st?.total ?? 0, jumps: st?.jumps ?? 0, drawn: st?.drawn ?? { arcs: [], labels: [] } },
      influence: this.influence.result, activePath: this.bands.activePath.length, fields: this.bands.fields.filter((f) => f.wires.length).map((f) => f.name),
      groups: this.bands.showGroups, highlight: this.bands.highlight?.size ?? 0, bus, memos: s?.memos ?? [],
    };
  }
}
