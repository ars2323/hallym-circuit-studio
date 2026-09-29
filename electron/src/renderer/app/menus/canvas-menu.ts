/* The Canvas's right-click menu, put on the Canvas (N-10, D-157): the right
   button (I-07, I-211), Ctrl+click on anything but an input pin or a
   button (the original's Ctrl Button1 → Menu Tool; the pin's is v1's poke,
   I-77), and from the keyboard the menu key or Shift+F10 at the selection
   (or the middle of the view).  The engine says what was clicked
   (model.menu); the registry (registry.ts, canvas-items.ts) builds the
   items; this file does what they ask (CanvasActions): intents, the
   dialogs (Label…, Edit Labels…, Replace Wire with Tunnels…, Duplicate N…),
   the hex editor, a RAM's or a ROM's image, Find E/X Origin …  Also the P
   key: a probe on the wire under the pointer (v1 ProbeMenu.installKey,
   I-186). */

import type { Component, EditResult, MenuFacts, Point, WindowMethod } from '../../../main/protocol.ts';
import type { CircuitCanvas } from '../../canvas/canvas.ts';
import type { Overlays } from '../../canvas/overlays/controller.ts';
import { menuOpen, showMenu } from '../../canvas/overlays/menu.ts';
import { toScreen } from '../../canvas/view.ts';
import { ask } from '../../shared/ask.ts';
import type { CallError, MemoryImageOptions } from '../api.ts';
import { hexEditor } from '../hex-editor.ts';
import type { Feature } from '../logic/course.ts';
import { ctrlPokes } from '../logic/editing.ts';
import type { MenuCommand } from '../editor.ts';
import { emitReveal } from '../reveal.ts';
import { emitEditSplitter } from '../tool-events.ts';
import { registerCanvasItems } from './canvas-items.ts';
import { askDuplicateN, askLabels, askText, defaultSpacing, nextLabel } from './dialogs.ts';
import { type CanvasTarget, menuFor } from './registry.ts';

export interface CanvasMenuDeps {
  board: CircuitCanvas;
  overlays: Overlays;
  call<T>(method: WindowMethod, params: Record<string, unknown>): Promise<T>;
  ready(): boolean;
  // The circuit on show: the file, the circuit drawn, the circuit the view starts from and the instances gone into.
  where(): { fileId: string; circuitId: string; root: string; path: string[] } | null;
  failed(name: string, e: unknown): void;
  note(cls: '' | 'err' | 'ok', text: string | null): void;
  menuCommand(cmd: MenuCommand): void;
  enter(id: string): void;
  showAttributes(): void;                         // the Attributes panel forward (its tab when narrow)
  reveal(id: string): void;                       // a part of the circuit on show, marked and in view
  tunnelColor(id: string, color: string | null): void;
  loadProgram(fileId: string, target: string, forSource?: string): void;
  registerMapping(): void;
  shows?(feature: Feature): boolean;              // what the course on show shows (A-08, logic/course.ts); all without
  memoryImage(fileId: string, o: MemoryImageOptions): Promise<unknown>;
  quietQuick(): void;                             // Quick Attributes stays away (a program's selection)
  // A menu shown for something else than the selection (D-158): Quick Attributes away until it closes.
  otherTarget?(): void;
  tool(): string;                                 // the tool in hand (the P key is not the Text tool's)
}

export interface CanvasMenu {
  open(at: Point, client: { x: number; y: number }, id?: string | null): Promise<void>;
  target(): CanvasTarget | null;                  // the last menu's target (tests)
  contents(id: string, editable: boolean): void;  // the hex editor of a RAM or a ROM of the circuit on show
}

export function installCanvasMenu(d: CanvasMenuDeps): CanvasMenu {
  const b = d.board;
  let last: CanvasTarget | null = null;
  let asking = 0;

  const edit = async (t: CanvasTarget, method: WindowMethod, params: Record<string, unknown>, name: string): Promise<EditResult | null> => {
    try {
      const r = await d.call<EditResult>(method, { fileId: t.fileId, circuitId: t.circuitId, ...params });
      d.note('', null);
      return r;
    } catch (e) {
      d.failed(name, e);
      return null;
    }
  };
  const scene = () => b.scene;
  const component = (id: string): Component | undefined => scene()?.components.get(id);

  registerCanvasItems({
    edit,
    overlayItems: (at) => d.overlays.menu(at),
    netValue: (w) => { const s = scene(); const n = s?.wireNet(w); return n ? s!.values.get(n.id) : undefined; },
    netWires: (w) => scene()?.wireNet(w)?.wires ?? [w],
    menuCommand: (cmd) => d.menuCommand(cmd),
    fit: () => b.fitView(),
    enter: (id) => d.enter(id),
    showAttributes: (t, id) => {
      void edit(t, 'edit.select', { ids: [id] }, 'Edit').then(() => { d.quietQuick(); d.showAttributes(); });
    },
    reveal: (id) => { d.quietQuick(); d.reveal(id); },
    editSplitter: (t, o) => emitEditSplitter({ fileId: t.fileId, circuitId: t.circuitId, ...o }),
    tunnelColor: (_t, id, color) => d.tunnelColor(id, color),
    addRow: (t, o) => {
      if (!o.at && !o.wire) return;
      void d.call('record.addRow', { fileId: t.fileId, circuitId: t.root, ...(t.path.length ? { path: t.path } : {}), ...(o.wire ? { wireId: o.wire } : { at: o.at }) })
        .catch((e) => d.failed('Add to Cycle View', e));
    },
    markPc: (t, id, on) => {
      void d.call('record.markPc', { fileId: t.fileId, circuitId: t.circuitId, componentId: id, on }).catch((e) => d.failed('Mark as PC', e));
    },
    markRegisterFile: (t, circuitId, on) => {
      void d.call('record.markRegisterFile', { fileId: t.fileId, circuitId, on }).catch((e) => d.failed('Register File', e));
    },
    registerMapping: () => d.registerMapping(),
    shows: (feature) => d.shows?.(feature) ?? true,
    loadProgram: (t, id, forSource) => d.loadProgram(t.fileId, id, forSource),
    reloadProgram: (t) => { void d.call('mips.reload', { fileId: t.fileId }).catch((e) => d.failed('Reload', e)); },
    findOrigin: (t, w) => void findOrigin(t, w),
    label: (t, ids, now) => {
      void askText({ title: 'Label', sentence: '부품의 라벨을 적으세요. 비우면 라벨을 지웁니다.', field: 'Label', value: now }, async (text) => {
        const r = await submit(t, 'edit.setAttr', { ids, attr: 'label', value: text.trim(), keepSelection: true });
        return r;
      });
    },
    labels: (t, ids, labels) => {
      const rows = ids.map((id, i) => {
        const c = component(id);
        const name = c ? (c.name + (c.attrs.label ? ` · ${c.attrs.label}` : '')) : id;
        return { id, name, label: labels[i] ?? c?.attrs.label ?? '' };
      });
      void askLabels(rows, (v) => submit(t, 'edit.labels', { ids: Object.keys(v), labels: Object.values(v) }));
    },
    tunnels: (t, w) => {
      void askText({ title: 'Replace Wire with Tunnels', sentence: '선을 지우고 양 끝에 같은 이름의 Tunnel을 둡니다. 넷은 그대로입니다.', field: 'Tunnel Name', value: '', required: '터널 이름을 적으세요.' },
        async (text) => {
          const why = await submit(t, 'edit.wireToTunnels', { wire: w, label: text.trim() });
          return why;
        });
    },
    duplicateN: (t, ids) => {
      const parts = ids.map(component).filter((c): c is Component => !!c);
      let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity;
      for (const c of parts) {
        x0 = Math.min(x0, c.bounds[0]); y0 = Math.min(y0, c.bounds[1]);
        x1 = Math.max(x1, c.bounds[0] + c.bounds[2]); y1 = Math.max(y1, c.bounds[1] + c.bounds[3]);
      }
      const box = parts.length ? { w: x1 - x0, h: y1 - y0 } : { w: 0, h: 0 };
      const first = parts.map((c) => c.attrs.label ?? '').find((l) => l !== '') ?? null;
      void askDuplicateN({
        spacing: (dir) => defaultSpacing(box, dir), firstLabel: first,
        example: (l) => `라벨: ${l} → ${nextLabel(l, 1)}, ${nextLabel(l, 2)}, …`,
      }, (v) => submit(t, 'edit.duplicateN', { ids, count: v.count, direction: v.direction, spacing: v.spacing, number: v.number }));
    },
    contents: (t, id, kind) => {
      const c = component(id);
      void hexEditor({
        fileId: t.fileId, circuitId: t.circuitId, root: t.root, path: t.path, componentId: id, kind,
        name: `${kind === 'ram' ? 'RAM' : 'ROM'}${c?.attrs.label ? ` · ${c.attrs.label}` : ''}`, editable: t.facts.editable,
      }, { call: d.call });
    },
    clearContents: (t, id, kind) => void (async () => {
      const ok = await ask({
        title: 'Clear Contents', ok: 'Clear', cancel: 'Cancel', danger: kind === 'ram',
        body: kind === 'ram' ? '메모리를 모두 0으로 지웁니다. RAM의 값은 시뮬레이션 상태라 Undo로 되돌릴 수 없습니다.' : 'ROM의 내용을 모두 0으로 지웁니다. Undo로 되돌릴 수 있습니다.',
      });
      if (!ok) return;
      if (kind === 'ram') {
        await d.call('mem.clear', { fileId: t.fileId, circuitId: t.root, path: t.path, componentId: id }).catch((e) => d.failed('Clear Contents', e));
      } else {
        await edit(t, 'edit.memContents', { id, clear: true }, 'Clear Contents');
      }
    })(),
    image: (t, id, kind, mode) => {
      void d.memoryImage(t.fileId, { circuitId: t.circuitId, root: t.root, path: t.path, componentId: id, kind, mode })
        .then((r) => { if (r !== null) d.note('ok', mode === 'load' ? '이미지를 불러왔습니다.' : '이미지를 저장했습니다.'); })
        .catch((e) => d.failed(mode === 'load' ? 'Load Image' : 'Save Image', e));
    },
    note: (cls, text) => d.note(cls, text),
    portOf: (_t, id) => component(id)?.ports[0]?.loc,
    partIds: () => new Set(scene()?.components.keys() ?? []),
    tunnelColorOf: (id) => component(id)?.ext?.color ?? null,
  });

  // A dialog's value to the engine: null when taken, else why not (the dialog keeps it open).
  async function submit(t: CanvasTarget, method: WindowMethod, params: Record<string, unknown>): Promise<string | null> {
    try {
      const r = await d.call<EditResult>(method, { fileId: t.fileId, circuitId: t.circuitId, ...params });
      if (r.outcome === 'refused') return '그렇게 두면 선이나 포트가 다른 연결에 닿아, 바꾸지 않았습니다.';
      d.note('', null);
      return null;
    } catch (e) {
      const err = e as CallError;
      if ((err.data as { reason?: string } | undefined)?.reason === 'badValue') return '이 값은 이 속성에 넣을 수 없습니다.';
      if (err.code === -32602) return '엔진이 이 값을 받지 않았습니다. 값을 고쳐 다시 해 보세요.';
      d.failed(method, e);
      return null;
    }
  }

  // Find E/X Origin (v1 D-01): the engine goes back from the net to where the E or X began; the place is shown.
  async function findOrigin(t: CanvasTarget, w: string): Promise<void> {
    const n = scene()?.wireNet(w);
    if (!n) return;
    try {
      const r = await d.call<{ found: boolean; text?: { ko: string; en: string }; origin?: { circuitId: string; root: string; path: string[]; components: string[]; wires: string[]; nets: string[]; at: Point | null; text?: { ko: string } } }>(
        'trace.origin', { fileId: t.fileId, circuitId: t.root, ...(t.path.length ? { path: t.path } : {}), netId: n.id });
      if (!r.found || !r.origin) { d.note('', r.text?.ko ?? 'E·X가 처음 생긴 곳을 찾지 못했습니다.'); return; }
      const o = r.origin;
      d.quietQuick();
      emitReveal({ fileId: t.fileId, messageId: null, circuitId: o.circuitId, root: o.root, path: [...o.path], components: [...o.components], wires: [...o.wires], nets: [...o.nets], at: o.at, cycle: null });
      d.note('', o.text?.ko ?? null);
    } catch (e) {
      d.failed('Find E/X Origin', e);
    }
  }

  async function open(at: Point, client: { x: number; y: number }, id: string | null = null): Promise<void> {
    const w = d.where();
    if (!w || !d.ready() || !b.scene) return;
    const n = ++asking;
    let facts: MenuFacts;
    try {
      facts = await d.call<MenuFacts>('model.menu', { fileId: w.fileId, circuitId: w.circuitId, at: [Math.round(at[0]), Math.round(at[1])], ...(id ? { id } : {}) });
    } catch (e) {
      d.failed('Menu', e);
      return;
    }
    if (n !== asking) return;
    const t: CanvasTarget = { fileId: w.fileId, circuitId: w.circuitId, root: w.root, path: w.path, at, facts };
    last = t;
    const entries = menuFor('canvas', t, facts.summary);
    if (!entries.length) return;
    showMenu(entries, client.x, client.y);
    // the original keeps the selection whatever is right-clicked (MenuTool.mousePressed); the bar by the selection
    // is not the menu's target then
    if (!id || !b.selection().includes(id)) d.otherTarget?.();
  }

  const c = b.canvas;
  const hit = (at: Point): string | null => b.partAt(at) ?? b.wireAt(at);
  c.addEventListener('contextmenu', (e) => {
    e.preventDefault();
    const at = b.pointerOf(e).at;
    void open(at, { x: e.clientX, y: e.clientY }, hit(at));
  });
  // Ctrl+click: the original's Menu Tool (Ctrl Button1), but on an input pin or a button v1's poke (I-07, I-77)
  c.addEventListener('pointerdown', (e) => {
    if (e.button !== 0 || !(e.ctrlKey || e.metaKey) || !b.scene) return;
    const at = b.pointerOf(e).at;
    const id = b.partAt(at);
    const part = id ? b.scene.components.get(id) : undefined;
    if (part && ctrlPokes(part)) return;
    void open(at, { x: e.clientX, y: e.clientY }, hit(at));
  });
  // The menu key, Shift+F10: at the selection's middle (or the view's).
  c.addEventListener('keydown', (e) => {
    if (menuOpen() || !(e.key === 'ContextMenu' || (e.shiftKey && e.key === 'F10'))) return;
    e.preventDefault();
    const s = b.scene;
    if (!s) return;
    const ids = b.selection();
    const first = ids.map((i) => s.components.get(i)).find((x): x is Component => !!x);
    const size = b.size();
    const at: Point = first ? [first.bounds[0] + first.bounds[2] / 2, first.bounds[1] + first.bounds[3] / 2]
      : [b.view.x + size.width / 2 / b.view.zoom, b.view.y + size.height / 2 / b.view.zoom];
    const r = c.getBoundingClientRect();
    const [sx, sy] = toScreen(b.view, at);
    void open(at, { x: r.left + sx, y: r.top + sy }, first?.id ?? null);
  });
  // P: a probe on the wire under the pointer (1 bit: binary, more: hex), not with the Text tool (v1 I-186)
  c.addEventListener('keydown', (e) => {
    if (e.defaultPrevented || e.isComposing || e.code !== 'KeyP' || e.ctrlKey || e.metaKey || e.altKey || e.shiftKey) return;
    const p = b.lastPointer;
    const w = d.where();
    if (!p || !w || !d.ready() || !b.scene || d.tool() === 'Text') return;
    const wire = b.partAt(p.at) ? null : b.wireAt(p.at);
    if (!wire) return;                          // off a wire: the letter opens the search palette (I-41)
    e.preventDefault();
    const at: Point = [Math.round(p.at[0]), Math.round(p.at[1])];
    void d.call<EditResult>('edit.probe', { fileId: w.fileId, circuitId: w.circuitId, wire, at })
      .then((r) => {
        if (r.outcome === 'noRoom') d.note('err', '이 선 옆에 빈 자리가 없습니다. 가까운 부품을 조금 옮긴 뒤 다시 해 보세요.');
        else if (r.outcome === 'refused') d.note('err', '그렇게 두면 선이나 포트가 다른 연결에 닿아, 바꾸지 않았습니다.');
        else d.note('', null);
      })
      .catch((err) => d.failed('Attach Probe', err));
  });

  return {
    open,
    target: () => last,
    contents: (id, editable) => {
      const w = d.where();
      const c = component(id);
      if (!w || !c || (c.name !== 'RAM' && c.name !== 'ROM')) return;
      const kind = c.name === 'RAM' ? 'ram' : 'rom';
      void hexEditor({ ...w, componentId: id, kind, name: `${c.name}${c.attrs.label ? ` · ${c.attrs.label}` : ''}`, editable }, { call: d.call });
    },
  };
}
