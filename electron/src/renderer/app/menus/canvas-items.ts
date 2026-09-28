/* The Canvas's right-click items (N-10, D-157): every original 2.7.1 item
   and every v1 item, from the engine's facts (model.menu) and the
   overlays' items (N-15), put in the registry (registry.ts) as providers
   in v1's order (v1 ContextMenus.PROVIDERS: the component's own items,
   EditMenus, SplitterMenu, ProbeMenu, InfluenceMenu, FlowMenu, MemoMenu).

   - a part: its own items (View <circuit>, RAM·ROM Edit Contents… …,
     Splitter Distribute, Load Program…), Attach to <port> ▸ and Negate
     Input on a port, the pin's, the gate's, the tunnel's and the
     subcircuit's items, Mark as PC; common Cut, Copy, Duplicate,
     Duplicate N…, Rotate, Show in Attribute Panel; Delete last.
   - a wire: Net Information…, Select Whole Net, Add to Cycle View, Signal
     Group ▸, Find E/X Origin, Highlight Net, Delete Net Wires, Replace
     Wire with Tunnels…; Split Bits… and Take One Bit ▸; Attach Probe ▸.
   - several parts: Change N Components ▸, Edit Labels of N Components…,
     Duplicate N…, Align ▸, Distribute ▸, Select Only …; Cut Selection,
     Copy Selection; Delete Selection.
   - an empty spot: Paste, Fit to Window, Select All Probes, Delete All
     Probes, the area memos.
   - wires chosen one by one: Combine N Wires into One Bus.

   The engine does every change as one undo step (edit.*); the items that
   only look (Select Whole Net, Go to Next Tunnel) select through edit.select. */

import type { AttrOption, EditResult, MenuFacts, MenuPart, Point, WindowMethod } from '../../../main/protocol.ts';
import type { MenuEntry } from '../../canvas/overlays/menu.ts';
import { ALIGNS, changeN, combineN, count, DISTRIBUTES, editLabelsN, FACINGS, gateShort, INPUT_COUNTS, type MenuItem, PROBE_RADICES, undefinedValue, WIDTHS } from '../logic/menu-layout.ts';
import { palette } from '../logic/tunnels.ts';
import { type CanvasTarget, registerMenu } from './registry.ts';

export interface CanvasActions {
  // An intent (fileId and circuitId added); null when the engine refused it (the status bar says why).
  edit(t: CanvasTarget, method: WindowMethod, params: Record<string, unknown>, name: string): Promise<EditResult | null>;
  overlayItems(at: Point): MenuEntry[];            // the overlays' items (N-15) for this point, by id
  netValue(wire: string): string | undefined;      // the value on the wire's net now (Find E/X Origin)
  netWires(wire: string): string[];                // the wires of the wire's net (Select Whole Net)
  menuCommand(cmd: 'cut' | 'copy' | 'paste' | 'duplicate' | 'delete'): void;   // Edit › … on the selection
  fit(): void;                                     // Fit to Window
  enter(id: string): void;                         // View <circuit>
  showAttributes(t: CanvasTarget, id: string): void;   // Show in Attribute Panel
  reveal(id: string): void;                        // bring a part into view, marked
  editSplitter(t: CanvasTarget, o: { componentId: string } | { wire: string; at: Point; bit?: number }): void;
  tunnelColor(t: CanvasTarget, id: string, color: string | null): void;
  addRow(t: CanvasTarget, o: { wire?: string; at?: Point }): void;       // Add to Cycle View
  markPc(t: CanvasTarget, id: string, on: boolean): void;
  markRegisterFile(t: CanvasTarget, circuitId: string, on: boolean): void;
  registerMapping(): void;
  loadProgram(t: CanvasTarget, id: string, forSource?: string): void;
  reloadProgram(t: CanvasTarget): void;
  findOrigin(t: CanvasTarget, wire: string): void;
  label(t: CanvasTarget, ids: string[], now: string): void;            // Label… (one part)
  labels(t: CanvasTarget, ids: string[], labels: string[]): void;      // Edit Labels of N Components…
  tunnels(t: CanvasTarget, wire: string): void;                        // Replace Wire with Tunnels…
  duplicateN(t: CanvasTarget, ids: string[]): void;                    // Duplicate N…
  contents(t: CanvasTarget, id: string, kind: 'ram' | 'rom'): void;    // Edit Contents…
  clearContents(t: CanvasTarget, id: string, kind: 'ram' | 'rom'): void;
  image(t: CanvasTarget, id: string, kind: 'ram' | 'rom', mode: 'load' | 'save'): void;
  note(cls: '' | 'err' | 'ok', text: string | null): void;
  portOf(t: CanvasTarget, id: string): Point | undefined;   // a part's first port (a pin's, a tunnel's)
  partIds(t: CanvasTarget): Set<string>;                    // the parts (not wires) of the circuit on show
  tunnelColorOf(id: string): string | null;                 // a tunnel's chosen colour (hcs:ext), null: Automatic
}

const ASSEMBLY = /\.(s|asm)$/i;
const fileName = (p: string): string => p.split(/[\\/]/).filter(Boolean).pop() ?? p;

// An option's submenu (Size ▸, Pull ▸: the original list box's choices, the one in use checked).
function optionItems(opts: AttrOption[], set: (value: string) => void): MenuEntry[] {
  return opts.map((o) => ({ label: o.display, radio: true, checked: o.checked === true, run: () => set(o.value) }));
}

export function registerCanvasItems(a: CanvasActions): void {
  const set = (t: CanvasTarget, ids: string[], attr: string, value: string, keep = true) =>
    void a.edit(t, 'edit.setAttr', { ids, attr, value, ...(keep ? { keepSelection: true } : {}) }, 'Change Attribute');
  const facingMenu = (t: CanvasTarget, ids: string[], keep: boolean): MenuEntry => ({
    label: 'Facing', items: FACINGS.map(([v, name]) => ({ label: name, run: () => set(t, ids, 'facing', v, keep) })),
  });
  const widthMenu = (t: CanvasTarget, ids: string[], keep: boolean): MenuEntry => ({
    label: 'Data Bits', items: WIDTHS.map((w) => ({ label: count(w, 'bit'), run: () => set(t, ids, 'width', String(w), keep) })),
  });

  // ---- 10: the component's own items (the original's MenuExtender, lib-mips LoadProgramMenu) ----------------
  registerMenu('canvas', {
    id: 'original', order: 10,
    items: (t) => {
      const p = t.facts.part;
      const id = t.facts.id;
      if (t.facts.kind !== 'part' || !p || !id) return [];
      const out: MenuItem[] = [];
      if (p.subcircuit) out.push({ label: `View ${p.subcircuit.name}`, run: () => a.enter(id) });
      if (p.memory === 'ram' || p.memory === 'rom') {
        const kind = p.memory;
        out.push({ label: 'Edit Contents…', run: () => a.contents(t, id, kind) });
        out.push({ label: 'Clear Contents', disabled: kind === 'rom' && !t.facts.editable, run: () => a.clearContents(t, id, kind) });
        out.push({ label: 'Load Image…', disabled: kind === 'rom' && !t.facts.editable, run: () => a.image(t, id, kind, 'load') });
        out.push({ label: 'Save Image…', run: () => a.image(t, id, kind, 'save') });
      }
      if (p.memory === 'program') {
        out.push({ label: 'Load Program…', disabled: !t.facts.editable, run: () => a.loadProgram(t, id) });
        const src = p.source;
        if (src && ASSEMBLY.test(src)) out.push({ label: `Load .hmx for ${fileName(src)}…`, disabled: !t.facts.editable, run: () => a.loadProgram(t, id, src) });
        else if (src) out.push({ label: `Reload ${fileName(src)}`, disabled: !t.facts.editable, run: () => a.reloadProgram(t) });
      }
      for (const o of p.original ?? []) {
        out.push({ label: o.text, disabled: !o.enabled || !t.facts.editable, run: () => void a.edit(t, 'edit.originalItem', { id, index: o.i }, o.text) });
      }
      return out;
    },
  });

  // ---- 20: v1 EditMenus (a port, a pin, a gate, a tunnel, a subcircuit, PC; several parts; a wire; an empty spot) ----
  registerMenu('canvas', {
    id: 'edit', order: 20,
    items: (t) => {
      const f = t.facts;
      if (f.kind === 'many') return many(t, f);
      if (f.kind === 'empty') return empty(t, f);
      if (f.kind === 'wire') return wire(t, f);
      return part(t, f);
    },
  });

  function part(t: CanvasTarget, f: MenuFacts): MenuItem[] {
    const p = f.part!;
    const id = f.id!;
    const one = [id];
    const ed = f.editable;
    const out: MenuItem[] = [];
    if (p.port) {
      const q = p.port;
      const kinds: [string, string][] = [['pin', 'Pin'], ['constant', 'Constant'], ['probe', 'Probe'], ['tunnel', 'Tunnel']];
      out.push({
        label: `Attach to ${q.name}`, disabled: !ed,
        items: kinds.filter(([k]) => k !== 'constant' || q.dir !== 'out').map(([k, name]) => ({
          label: name, run: () => void a.edit(t, 'edit.attach', { id, port: q.i, what: k }, `Attach to ${q.name}`).then((r) => refused(r)),
        })),
      });
      if (q.negate) {
        out.push({ label: q.negated ? `Stop Negating Input ${q.name}` : `Negate Input ${q.name}`, disabled: !ed, run: () => set(t, one, q.negate!, String(!q.negated)) });
      }
    }
    // common (v1: Duplicate, Duplicate N…, Show in Attribute Panel; the brief's Cut, Copy, Rotate)
    out.push({ group: 'common', label: 'Cut', keys: 'Ctrl+X', disabled: !ed, run: () => void a.edit(t, 'edit.cut', { ids: one }, 'Cut') });
    out.push({ group: 'common', label: 'Copy', keys: 'Ctrl+C', run: () => void a.edit(t, 'edit.copy', { ids: one }, 'Copy') });
    out.push({ group: 'common', label: 'Duplicate', keys: 'Ctrl+D', disabled: !ed, run: () => void a.edit(t, 'edit.duplicate', { ids: one }, 'Duplicate') });
    out.push({ group: 'common', label: 'Duplicate N…', disabled: !ed, run: () => a.duplicateN(t, one) });
    if (p.facing) out.push({ group: 'common', label: 'Rotate', keys: 'R', disabled: !ed, run: () => void a.edit(t, 'edit.rotate', { ids: one, clockwise: true }, 'Rotate') });
    out.push({ group: 'common', label: 'Show in Attribute Panel', run: () => a.showAttributes(t, id) });
    out.push({ group: 'delete', label: 'Delete', keys: 'Del', disabled: !ed, run: () => void a.edit(t, 'edit.delete', { ids: one }, 'Delete') });
    if (p.name === 'Pin' && p.pin) out.push(...pin(t, p, id));
    else if (p.gate) out.push(...gate(t, p, id));
    else if (p.name === 'Tunnel' && p.tunnel) out.push(...tunnel(t, p, id));
    else if (p.subcircuit) out.push(...subcircuit(t, p));
    if (p.pcMarked !== undefined) {
      out.push({ label: p.pcMarked ? 'Unmark as PC' : 'Mark as PC', disabled: !ed, run: () => a.markPc(t, id, !p.pcMarked) });
    }
    return out;

    function pin(t2: CanvasTarget, q: MenuPart, pid: string): MenuItem[] {
      const r: MenuItem[] = [];
      r.push({ label: q.pin!.output ? 'Make Input Pin' : 'Make Output Pin', disabled: !ed, run: () => set(t2, [pid], 'output', String(!q.pin!.output)) });
      r.push({ ...widthMenu(t2, [pid], true), disabled: !ed });
      r.push({ label: q.pin!.tristate ? 'No Three-State' : 'Allow Three-State', disabled: !ed, run: () => set(t2, [pid], 'tristate', String(!q.pin!.tristate)) });
      r.push({ label: 'Add to Cycle View', run: () => a.addRow(t2, { at: a.portOf(t2, pid) }) });
      if (q.options.pull?.length) r.push({ label: 'Pull', disabled: !ed, items: optionItems(q.options.pull, (v) => set(t2, [pid], 'pull', v)) });
      r.push({ label: 'Label…', keys: 'F2', disabled: !ed, run: () => a.label(t2, [pid], q.label ?? '') });
      return r;
    }

    function gate(t2: CanvasTarget, q: MenuPart, gid: string): MenuItem[] {
      const r: MenuItem[] = [];
      if (q.inputs) r.push({ label: 'Number of Inputs', disabled: !ed, items: INPUT_COUNTS.map((n) => ({ label: String(n), run: () => set(t2, [gid], 'inputs', String(n)) })) });
      if (q.options.size?.length) r.push({ label: 'Size', disabled: !ed, items: optionItems(q.options.size, (v) => set(t2, [gid], 'size', v)) });
      r.push({ ...facingMenu(t2, [gid], true), disabled: !ed });
      r.push({ ...widthMenu(t2, [gid], true), disabled: !ed });
      if (q.swaps?.length) {
        r.push({
          label: 'Change Gate To', disabled: !ed,
          items: q.swaps.map((g) => ({ label: gateShort(g), run: () => void a.edit(t2, 'edit.swapGate', { id: gid, to: g }, 'Change Gate To').then((x) => refused(x)) })),
        });
      }
      r.push({ label: 'Label…', keys: 'F2', disabled: !ed, run: () => a.label(t2, [gid], q.label ?? '') });
      return r;
    }

    function tunnel(t2: CanvasTarget, q: MenuPart, tid: string): MenuItem[] {
      const r: MenuItem[] = [];
      const same = q.tunnel!.same;
      const name = q.label ?? '';
      if (same.length > 1) {
        r.push({
          label: `Go to Next "${name}" Tunnel`,
          run: () => {
            const next = same[(Math.max(0, same.indexOf(tid)) + 1) % same.length];
            void a.edit(t2, 'edit.select', { ids: [next] }, 'Edit');
            a.reveal(next);
          },
        });
      }
      r.push({ label: `Select All "${name}" Tunnels (${same.length})`, run: () => void a.edit(t2, 'edit.select', { ids: same }, 'Edit') });
      const now = a.tunnelColorOf(tid);
      r.push({
        label: 'Tunnel Color', disabled: !ed,
        items: [
          { label: 'Automatic (from the name, not saved)', radio: true, checked: !now, run: () => a.tunnelColor(t2, tid, null) },
          { label: '-' },
          ...palette().map((c) => ({ label: c.name, swatch: c.color, radio: true, checked: !!now && now.toLowerCase() === c.color.toLowerCase(), run: () => a.tunnelColor(t2, tid, c.color) })),
        ],
      });
      r.push({ label: 'Add to Cycle View', run: () => a.addRow(t2, { at: a.portOf(t2, tid) }) });
      return r;
    }

    function subcircuit(t2: CanvasTarget, q: MenuPart): MenuItem[] {
      const s = q.subcircuit!;
      if (s.library !== undefined) return [];              // another file's circuit: N-11's Edit Original File
      const r: MenuItem[] = [];
      r.push({ label: s.registerFile ? 'Unmark Register File' : 'Mark as Register File', disabled: !ed, run: () => a.markRegisterFile(t2, s.circuitId, !s.registerFile) });
      if (s.registerFile) r.push({ label: 'Register Mapping…', run: () => a.registerMapping() });
      return r;
    }
  }

  function many(t: CanvasTarget, f: MenuFacts): MenuItem[] {
    const ed = f.editable;
    const n = f.selection.parts;
    const all = a.partIds(t);
    const parts = f.selection.ids.filter((id) => all.has(id));
    const out: MenuItem[] = [];
    // the original's MenuSelection: Delete Selection (bottom), Cut Selection, Copy Selection (common)
    out.push({ group: 'common', label: 'Cut Selection', keys: 'Ctrl+X', disabled: !ed, run: () => a.menuCommand('cut') });
    out.push({ group: 'common', label: 'Copy Selection', keys: 'Ctrl+C', run: () => a.menuCommand('copy') });
    out.push({ group: 'delete', label: 'Delete Selection', keys: 'Del', disabled: !ed, run: () => a.menuCommand('delete') });
    const c = f.common;
    const change: MenuEntry[] = [];
    if (c?.facing) change.push(facingMenu(t, parts, false));
    if (c?.width) change.push(widthMenu(t, parts, false));
    if (change.length) out.push({ label: changeN(n), disabled: !ed, items: change });
    if (c?.label) out.push({ label: editLabelsN(n), disabled: !ed, run: () => a.labels(t, parts, c.labels) });
    out.push({ label: 'Duplicate N…', disabled: !ed, run: () => a.duplicateN(t, parts) });
    out.push({
      label: 'Align', disabled: !ed,
      items: ALIGNS.map(([mode, name]) => ({ label: name, run: () => void a.edit(t, 'edit.align', { ids: parts, mode }, 'Align').then((r) => arranged(r)) })),
    });
    if (n >= 3) {
      out.push({
        label: 'Distribute', disabled: !ed,
        items: DISTRIBUTES.map(([axis, name]) => ({ label: name, run: () => void a.edit(t, 'edit.distribute', { ids: parts, axis }, 'Distribute').then((r) => arranged(r)) })),
      });
    }
    if (f.selection.wires > 0) {
      out.push({ label: 'Select Only Components', run: () => void a.edit(t, 'edit.select', { filter: 'components' }, 'Edit') });
      out.push({ label: 'Select Only Wires', run: () => void a.edit(t, 'edit.select', { filter: 'wires' }, 'Edit') });
    }
    return out;
  }

  function wire(t: CanvasTarget, f: MenuFacts): MenuItem[] {
    const w = f.id!;
    const ed = f.editable;
    const ov = byId(a.overlayItems(t.at));
    const out: MenuItem[] = [];
    if (ov.netInfo) out.push(ov.netInfo);
    out.push({ label: 'Select Whole Net', run: () => void a.edit(t, 'edit.select', { ids: a.netWires(w) }, 'Edit') });
    if (ov.addRow) out.push(ov.addRow);
    if (ov.signalGroup) out.push({ ...ov.signalGroup, disabled: !ed });
    out.push({ label: 'Find E/X Origin', disabled: !undefinedValue(a.netValue(w)), run: () => a.findOrigin(t, w) });
    if (ov.highlight) out.push(ov.highlight);
    out.push({ label: 'Delete Net Wires', disabled: !ed, run: () => void a.edit(t, 'edit.deleteNet', { wire: w }, 'Delete Net Wires') });
    out.push({ label: 'Replace Wire with Tunnels…', disabled: !ed, run: () => a.tunnels(t, w) });
    out.push({ group: 'delete', label: 'Delete', keys: 'Del', disabled: !ed, run: () => void a.edit(t, 'edit.delete', { ids: [w] }, 'Delete') });
    return out;
  }

  function empty(t: CanvasTarget, f: MenuFacts): MenuItem[] {
    return [
      { label: 'Paste', keys: 'Ctrl+V', disabled: !f.editable, run: () => a.menuCommand('paste') },
      { label: 'Fit to Window', keys: 'Ctrl+0', run: () => a.fit() },
    ];
  }

  // ---- 30: v1 SplitterMenu (Edit Splitter…, Split Bits…, Take One Bit ▸, Combine N Wires into One Bus) -------
  registerMenu('canvas', {
    id: 'splitter', order: 30,
    items: (t) => {
      const f = t.facts;
      const ed = f.editable;
      const out: MenuItem[] = [];
      if (f.kind === 'part' && f.part?.name === 'Splitter' && f.id) {
        const id = f.id;
        out.push({ label: 'Edit Splitter…', disabled: !ed, run: () => a.editSplitter(t, { componentId: id }) });
      }
      const inSel = !f.id || f.selection.ids.includes(f.id);
      if (f.combine && f.combine.length >= 2 && inSel) {
        const known = f.combine.every((w) => w > 0);
        out.push({
          label: combineN(f.combine.length), disabled: !ed || !f.selection.ordered || !known,
          title: !f.selection.ordered ? '여러 선을 하나의 버스로 합치려면 Shift+클릭으로 선을 하나씩 고르세요(고른 순서가 비트 순서)'
            : !known ? '고른 선 중 폭을 아직 모르는 것이 있습니다. 먼저 연결하세요' : undefined,
          run: () => void a.edit(t, 'edit.combineBus', { ids: f.selection.ids }, 'Combine Wires').then((r) => {
            if (r?.outcome === 'tooWide') a.note('err', `고른 선의 폭을 모두 더하면 ${f.combine!.reduce((x, y) => x + y, 0)}비트입니다. Splitter는 32비트까지입니다.`);
            else refused(r);
          }),
        });
      } else if (f.kind === 'wire' && f.wire && f.wire.width > 1 && f.id) {
        const w = f.id;
        const width = f.wire.width;
        out.push({ label: 'Split Bits…', disabled: !ed, run: () => a.editSplitter(t, { wire: w, at: t.at }) });
        out.push({
          label: 'Take One Bit', disabled: !ed,
          items: Array.from({ length: width }, (_, i) => width - 1 - i).map((b) => ({ label: `[${b}]`, run: () => a.editSplitter(t, { wire: w, at: t.at, bit: b }) })),
        });
      }
      return out;
    },
  });

  // ---- 40: v1 ProbeMenu (Attach Probe ▸ on a wire, Select/Delete All Probes on an empty spot) -----------------
  registerMenu('canvas', {
    id: 'probe', order: 40,
    items: (t) => {
      const f = t.facts;
      const ed = f.editable;
      if (f.kind === 'wire' && f.id && f.selection.ids.length <= 1) {
        const w = f.id;
        return [{
          label: 'Attach Probe', keys: 'P', disabled: !ed,
          items: PROBE_RADICES.map(([radix, name]) => ({ label: name, run: () => void a.edit(t, 'edit.probe', { wire: w, at: t.at, radix }, 'Attach Probe').then((r) => probed(r)) })),
        }];
      }
      if (f.kind === 'empty' || (f.kind === 'many' && !f.id)) {
        const probes = f.probes ?? [];
        if (!probes.length) return [];
        return [
          { label: `Select All Probes (${probes.length})`, run: () => void a.edit(t, 'edit.select', { ids: probes }, 'Edit') },
          { label: `Delete All Probes (${probes.length})`, disabled: !ed, run: () => void a.edit(t, 'edit.deleteProbes', {}, 'Delete All Probes') },
        ];
      }
      return [];
    },
  });

  // ---- 50, 60, 70: the overlays' Influence ▸, Signal Flow ▸, area memos (N-15, v1 InfluenceMenu, FlowMenu, MemoMenu) ----
  for (const [id, order] of [['influence', 50], ['flow', 60], ['memo', 70]] as [string, number][]) {
    registerMenu('canvas', { id, order, items: (t) => a.overlayItems(t.at).filter((e) => e.id === id) });
  }

  function refused(r: EditResult | null): void {
    if (r?.outcome === 'refused') a.note('err', '그렇게 두면 선이나 포트가 다른 연결에 닿아, 바꾸지 않았습니다.');
  }
  function probed(r: EditResult | null): void {
    if (r?.outcome === 'noRoom') a.note('err', '이 선 옆에 빈 자리가 없습니다. 가까운 부품을 조금 옮긴 뒤 다시 해 보세요.');
    else refused(r);
  }
  function arranged(r: EditResult | null): void {
    if (r?.outcome === 'connected') a.note('err', '선이 이어진 부품은 옮기지 않습니다(배선하기 전에 정렬합니다).');
    else if (r?.outcome === 'nothing') a.note('', '이미 그 자리에 있습니다.');
    else refused(r);
  }
}

const byId = (xs: MenuEntry[]): Record<string, MenuEntry> => Object.fromEntries(xs.filter((x) => x.id).map((x) => [x.id!, x]));
