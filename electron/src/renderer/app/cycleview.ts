/* The Cycle View tab under the Canvas (N-14; v1 C-02..C-07, D-074..D-078,
   D-098, D-113, D-114): the cycle table on the left, Registers | Memory |
   Instruction on the right (Hallym MIPS's Registers, Data and Inspector
   panels, shared/), and a bar with Previous / Next / Latest, the cycle on
   show and Run Until.  A narrow tab puts the table in a fourth tab
   (Cycles) instead of beside the others.

   The engine keeps the recording and answers what a cycle holds
   (docs/engine-api.md record.*); this file asks for what is on screen when
   the engine says something changed (record.state, once a frame at most),
   one request of a kind at a time.  A column clicked shows that cycle: the
   whole circuit takes its values (the engine swaps the state, v1 C-03).
   Nothing here judges a value (CLAUDE.md 2.6). */

import type {
  CycleRow, CycleTable, InstructionData, MemoryData, Point, RecordState, RegisterData, RegisterMapping, RunUntilDone, WindowMethod,
} from '../../main/protocol.ts';
import { ask } from '../shared/ask.ts';
import { code, codeText, h } from '../shared/dom.ts';
import { Inspector } from '../shared/inspector.ts';
import { MemoryView } from '../shared/memory.ts';
import { notice } from '../shared/notice.ts';
import { RegisterPanel } from '../shared/registers.ts';
import { headButton, tabsHead } from '../shared/ui.ts';
import type { CallError } from './api.ts';
import {
  bitOf, cellText, COLUMN_PX, columnsThatFit, NAME_LEAST, columnWindow, nameWidth, pinGone, sameAsBefore, UNTIL_KINDS, untilRequest, untilResult, wave,
  type UntilForm, type Window,
} from './logic/cycle.ts';
import { commandError } from './logic/errors.ts';
import { memoryView } from './logic/memory.ts';
import { registerGroups, registerViews, shape } from './logic/registers.ts';
import { popupMenu, type MenuItem } from './menu.ts';

export interface CycleViewHost {
  call<T>(method: WindowMethod, params: Record<string, unknown>): Promise<T>;
  fileId(): string | null;          // the file on show
  ready(): boolean;                 // the engine answers
  note(cls: '' | 'err' | 'ok', text: string): void;   // the status bar's last fact
  dirty(fileId: string, dirty: boolean): void;
  changed(): void;                  // the status bar and the toolbar again (the cycle, Run Until)
}

// A pinned row's place: the circuit it is in, the instances down from the recorded circuit, the point.
export interface PinSpot { circuitId: string; path: string[]; at: Point }

const SIDE = ['Cycles', 'Registers', 'Memory', 'Instruction'];
const nothingIn = (s: RecordState | undefined): boolean =>
  !s || ((s.empty || s.last === 0) && s.rows === 0 && s.pinned === 0 && !s.cpu);
const SIDE_LEAST = 320;            // Registers' Name, Hex and Dec, tight
const SIDE_MOST = 560;

export class CycleView {
  readonly root: HTMLElement;
  private readonly states = new Map<string, RecordState>();
  private readonly bar: HTMLElement;
  private readonly pos: HTMLElement;
  private readonly pastNote: HTMLElement;
  private readonly bPrev: HTMLButtonElement;
  private readonly bNext: HTMLButtonElement;
  private readonly bLatest: HTMLButtonElement;
  private readonly bUntil: HTMLButtonElement;
  private readonly main: HTMLElement;
  private readonly tableBox: HTMLElement;
  private readonly side: HTMLElement;
  private readonly sideHead: ReturnType<typeof tabsHead>;
  private readonly sideBodies: HTMLElement[];
  private readonly registers = new RegisterPanel();
  private readonly regsHint: HTMLElement;
  private readonly regsBox: HTMLElement;
  private readonly memory = new MemoryView();
  private readonly memBox: HTMLElement;
  private readonly inspector = new Inspector();
  private compact = false;
  private visible = false;
  private tab = 1;                  // the side tab: 1 Registers, 2 Memory, 3 Instruction (0 Cycles, compact)
  private window: Window | null = null;
  private table: CycleTable | null = null;
  private regs: RegisterData | null = null;
  private busy = false;
  private again = false;
  private asideButtons: HTMLElement[] = [];
  private regToggles: HTMLElement[] = [];
  private memToggles: HTMLElement[] = [];
  private readonly host: CycleViewHost;
  private readonly pinnedBy = new Map<string, string>();   // fileId -> the message whose rows are pinned (D-114)

  constructor(host: CycleViewHost) {
    this.host = host;
    this.bPrev = headButton('Previous Cycle', '앞 사이클을 봅니다', () => void this.step(-1));
    this.bNext = headButton('Next Cycle', '다음 사이클을 봅니다. 마지막 사이클이면 한 사이클 실행합니다', () => void this.step(1));
    this.bLatest = headButton('Latest Cycle', '마지막 사이클로 돌아옵니다', () => void this.view(null));
    this.bUntil = headButton('Run Until…', '조건을 만날 때까지 한 사이클씩 실행합니다', () => void this.runUntil());
    this.pos = h('span', { class: 'cpos mono' });
    this.pastNote = h('span', { class: 'cpast', hidden: true }, codeText('지난 사이클을 보고 있습니다. 여기서 진행하거나 입력·회로를 바꾸면 그 뒤 사이클 기록은 지워집니다.'));
    this.bar = h('div', { class: 'cbar' }, this.bPrev, this.bNext, this.bLatest, this.pos, this.bUntil, this.pastNote);
    this.tableBox = h('div', { class: 'ctablebox' });
    this.tableBox.addEventListener('click', (e) => this.clicked(e));
    this.tableBox.addEventListener('contextmenu', (e) => this.rowMenu(e));
    this.regsHint = h('div', { class: 'chint', hidden: true });
    this.regsBox = h('div', { class: 'cside-body' }, this.regsHint, this.registers.root);
    this.memBox = h('div', { class: 'cside-body' }, this.memory.root);
    this.sideBodies = [h('div', { class: 'cside-body cside-cycles' }), this.regsBox, this.memBox, h('div', { class: 'cside-body' }, this.inspector.root)];
    this.sideHead = tabsHead(SIDE, (i) => this.selectSide(i));
    this.side = h('section', { class: 'cside', 'aria-label': 'Registers' }, this.sideHead.root, ...this.sideBodies);
    this.main = h('div', { class: 'cmain' }, this.tableBox, this.side);
    this.root = h('div', { class: 'cycleview' }, this.bar, this.main);
    this.registers.onToggles = (b) => { if (this.tab === 1) this.setAside(b); this.regToggles = b; };
    this.memory.onToggles = (b) => { if (this.tab === 2) this.setAside(b); this.memToggles = b; };
    this.registers.onRowMenu = (key, x, y) => this.registerMenu(key, x, y);
    this.sideHead.select(1);
    this.sideHead.show(0, false);
    this.selectSide(1);
    new ResizeObserver(() => this.layout()).observe(this.root);
  }

  // ---- what the app tells --------------------------------------------------------

  state(fileId: string): RecordState | undefined {
    return this.states.get(fileId);
  }

  // Nothing to show yet (the tab keeps its word, "아직 사이클이 없습니다"): no cycle after the first, no row, no CPU to read.
  nothingYet(fileId: string | null): boolean {
    return nothingIn(fileId ? this.states.get(fileId) : undefined);
  }

  running(fileId: string | null): boolean {
    return fileId !== null && (this.states.get(fileId)?.runUntil ?? null) !== null;
  }

  onState(s: RecordState): void {
    const before = this.states.get(s.fileId);
    this.states.set(s.fileId, s);
    if (s.fileId === this.host.fileId()) {
      this.renderBar();
      this.refresh();
    }
    if (!before || before.cycle !== s.cycle || before.past !== s.past || before.pc !== s.pc || (before.runUntil === null) !== (s.runUntil === null)
        || nothingIn(before) !== nothingIn(s)) {
      this.host.changed();
    }
  }

  onRunUntil(d: RunUntilDone): void {
    const name = (id: string) => this.table?.rows.find((r) => r.id === id)?.name;
    this.host.note(d.result === 'met' ? 'ok' : '', untilResult(d, name));
    this.host.changed();
  }

  forget(fileId: string | null): void {
    if (fileId === null) { this.states.clear(); this.pinnedBy.clear(); } else { this.states.delete(fileId); this.pinnedBy.delete(fileId); }
    this.table = null;
    this.regs = null;
    this.window = null;
  }

  // The file on show changed.  Its state is asked for once if none came yet (a notification comes only on a change).
  fileChanged(): void {
    this.table = null;
    this.regs = null;
    this.window = null;
    this.renderBar();
    this.refresh();
    const fileId = this.host.fileId();
    if (fileId && !this.states.has(fileId) && this.host.ready()) {
      void this.host.call<RecordState>('record.state', { fileId }).then((s) => { if (!this.states.has(s.fileId)) this.onState(s); }, () => {});
    }
  }

  setVisible(on: boolean): void {
    this.visible = on;
    if (on) {
      this.layout();
      this.refresh();
    }
  }

  // ---- layout --------------------------------------------------------------------------

  private layout(): void {
    const w = this.root.clientWidth;
    if (!w) return;
    // The side takes 40 % of the width within limits; if the table would not keep three cycles beside it,
    // the table becomes a tab of its own, the first one (D-113).
    const side = Math.round(Math.max(SIDE_LEAST, Math.min(SIDE_MOST, w * 0.4)));
    const compact = w - side < NAME_LEAST + 3 * COLUMN_PX + 2;
    if (compact !== this.compact) {
      this.compact = compact;
      this.root.classList.toggle('compact', compact);
      this.sideHead.show(0, compact);
      if (compact) {
        this.sideBodies[0].append(this.tableBox);
        this.sideHead.select(0);
        this.selectSide(0);
      } else {
        this.main.prepend(this.tableBox);
        if (this.tab === 0) { this.sideHead.select(1); this.selectSide(1); }
      }
      this.showSide();
    }
    if (!compact) this.main.style.setProperty('--side-w', `${side}px`);
    if (this.visible) this.refresh();
  }

  private selectSide(i: number): void {
    this.tab = i;
    this.showSide();
    this.setAside(i === 1 ? this.regToggles : i === 2 ? this.memToggles : []);
    this.side.setAttribute('aria-label', SIDE[i]);
    this.refresh();
  }

  private showSide(): void {
    this.sideBodies.forEach((b, k) => { b.hidden = k !== this.tab; });
  }

  private setAside(buttons: HTMLElement[]): void {
    this.asideButtons = buttons;
    this.sideHead.aside.replaceChildren(...buttons, ...this.extraAside());
  }

  // Register File / Mapping controls in the head while Registers is on show.
  private extraAside(): HTMLElement[] {
    if (this.tab !== 1 || !this.regs) return [];
    if (this.regs.mode === 'file') {
      // Unmark Register File: the rows' right-click menu (the head keeps one button)
      return [headButton('Register Mapping…', '레지스터 번호마다 회로의 Register 부품을 고릅니다', () => void this.mapping())];
    }
    if ((this.regs.candidates ?? []).length > 0) {
      return [headButton('Mark as Register File…', '레지스터 파일로 쓰는 서브회로를 고릅니다', () => void this.chooseRegisterFile())];
    }
    return [];
  }

  // ---- asking the engine ---------------------------------------------------------------

  // Asks for what is on screen; one round at a time, again once more if something changed meanwhile.
  refresh(): void {
    if (!this.visible || !this.host.ready() || this.host.fileId() === null) return;
    if (this.busy) { this.again = true; return; }
    this.busy = true;
    this.again = false;
    void this.fetch().finally(() => {
      this.busy = false;
      if (this.again) requestAnimationFrame(() => this.refresh());
    });
  }

  private async fetch(): Promise<void> {
    const fileId = this.host.fileId();
    if (!fileId) return;
    const s = this.states.get(fileId);
    const jobs: Promise<void>[] = [];
    const tableShown = !this.compact || this.tab === 0;
    if (tableShown) jobs.push(this.fetchTable(fileId, s));
    if (this.tab === 1) jobs.push(this.fetchRegisters(fileId));
    if (this.tab === 2) jobs.push(this.fetchMemory(fileId));
    if (this.tab === 3) jobs.push(this.fetchInstruction(fileId));
    await Promise.all(jobs);
  }

  private async fetchTable(fileId: string, s: RecordState | undefined): Promise<void> {
    try {
      const names = this.table?.rows.map((r) => r.name) ?? [];
      const nameCol = nameWidth(['Instruction', ...names]);
      const count = columnsThatFit(this.tableBox.clientWidth || 600, nameCol);
      let params: Record<string, unknown> = { fileId };
      if (s && !s.empty) {
        this.window = columnWindow(s.first, s.last, s.cycle, count, this.window);
        params = { fileId, from: this.window.from, to: this.window.to };
      }
      const t = await this.host.call<CycleTable>('record.table', params);
      if (this.host.fileId() !== fileId) return;
      this.table = t;
      this.renderTable(t, nameCol);
    } catch (e) {
      this.tableBox.replaceChildren(notice({ title: '사이클 표를 받지 못했습니다', body: commandError('Cycle View', e as CallError) }));
    }
  }

  private async fetchRegisters(fileId: string): Promise<void> {
    try {
      const r = await this.host.call<RegisterData>('record.registers', { fileId });
      if (this.host.fileId() !== fileId) return;
      this.regs = r;
      this.renderRegisters(r);
    } catch { /* the next state asks again */ }
  }

  private async fetchMemory(fileId: string): Promise<void> {
    try {
      const m = await this.host.call<MemoryData>('record.memory', { fileId });
      if (this.host.fileId() !== fileId) return;
      if (m.parts === 0) {
        this.memory.root.replaceChildren(h('div', { class: 'notice-host' }, notice({
          title: '이 회로에는 Data Memory 부품이 없습니다', body: 'Hallym MIPS 부품 목록의 Data Memory 부품을 놓으면 데이터와 스택이 여기에 한 표로 나옵니다.',
        })));
        return;
      }
      this.memory.show(memoryView(m.rows));
    } catch { /* the next state asks again */ }
  }

  private async fetchInstruction(fileId: string): Promise<void> {
    try {
      const d = await this.host.call<InstructionData>('record.instruction', { fileId });
      if (this.host.fileId() !== fileId) return;
      if (d.none || !d.fields || !d.text || !d.word) {
        const words = d.none === 'noCpu'
          ? { title: '이 회로에는 Instruction Memory 부품이 없습니다', body: 'Instruction Memory 부품이 내는 명령어를 32비트 필드로 나누어 여기에 보입니다.' }
          : { title: '이 사이클에는 명령어가 없습니다', body: 'Instruction Memory 부품의 출력이 정해지지 않았습니다. 실행 이미지를 불러오고 한 사이클 진행하면 나옵니다.' };
        this.inspector.empty(h('div', { class: 'notice-host' }, notice(words)));
        return;
      }
      this.inspector.show({ text: d.text, format: d.format ?? '', word: d.word, pc: d.pc ?? '', fields: d.fields });
    } catch { /* the next state asks again */ }
  }

  // ---- drawing ---------------------------------------------------------------------

  private renderBar(): void {
    const s = this.host.fileId() ? this.states.get(this.host.fileId()!) : undefined;
    const empty = !s || s.empty;
    this.pos.textContent = empty ? 'Cycle 0' : s.past ? `Cycle ${s.cycle} / ${s.last}` : `Cycle ${s.cycle}`;
    this.pastNote.hidden = !s?.past;
    const running = s?.runUntil != null;
    this.bPrev.disabled = empty || running || s.cycle <= s.first;
    this.bNext.disabled = !this.host.ready() || running;
    this.bLatest.disabled = empty || running || !s.past;
    this.bUntil.textContent = running ? 'Stop' : 'Run Until…';
    this.bUntil.title = running ? 'Run Until 실행을 멈춥니다' : '조건을 만날 때까지 한 사이클씩 실행합니다. 지난 사이클을 보고 있으면 거기서 진행하고 뒤 사이클 기록은 지워집니다.';
    this.bUntil.classList.toggle('on', running);
    this.bUntil.disabled = !this.host.ready() || this.host.fileId() === null;
  }

  private renderTable(t: CycleTable, nameCol: number): void {
    const cols = t.columns;
    const cursor = t.cycle ?? -1;
    const colgroup = h('colgroup', {}, h('col', { style: `width:${nameCol}px` }), ...cols.map(() => h('col', { style: `width:${COLUMN_PX}px` })));
    const headCell = (c: number, ...content: (Node | string)[]) =>
      h('td', { class: `ccell${c === cursor ? ' on' : ''}`, 'data-cycle': String(c) }, ...content);
    const head = h('thead', {},
      h('tr', { class: 'hcycle' }, h('th', { class: 'nm' }, 'Cycle'), ...cols.map((c) =>
        h('th', { class: `ccell${c.cycle === cursor ? ' on' : ''}`, 'data-cycle': String(c.cycle), title: `Cycle ${c.cycle}` }, String(c.cycle)))));
    if (t.cpu) {
      head.append(
        h('tr', { class: 'hpc' }, h('th', { class: 'nm' }, 'PC'), ...cols.map((c) => headCell(c.cycle, c.pc ? code(c.pc) : ''))),
        h('tr', { class: 'hins' }, h('th', { class: 'nm' }, 'Instruction'), ...cols.map((c) =>
          headCell(c.cycle, c.text ? code(c.text) : ''))));
      for (const [i, td] of [...head.querySelectorAll('tr.hins td')].entries()) (td as HTMLElement).title = cols[i].text;
    }
    const body = h('tbody', {});
    for (const r of t.rows) this.renderRow(body, r, cols.map((c) => c.cycle), cursor, t.pinnedCycle ?? -1);
    // Fixed columns: a long instruction is cut (its whole text in the title), a column never widens.
    const table = h('table', { class: 'ctable', style: `width:${nameCol + cols.length * COLUMN_PX}px` }, colgroup, head, body);
    const out: Node[] = [table];
    if (t.rows.length === 0) {
      out.push(h('p', { class: 'cempty' }, codeText('선을 오른쪽 클릭하고 `Add to Cycle View` 메뉴를 고르면 신호가 줄로 더해집니다.')));
    }
    const scroll = this.tableBox.scrollTop;
    this.tableBox.replaceChildren(...out);
    this.tableBox.scrollTop = scroll;
  }

  private renderRow(body: HTMLElement, r: CycleRow, cycles: number[], cursor: number, pinnedCycle: number): void {
    const values = r.values ?? [];
    const halves = r.halves ?? [];
    const cls = (i: number) => `ccell${cycles[i] === cursor ? ' on' : ''}${r.temp && cycles[i] === pinnedCycle ? ' pin' : ''}`;
    const name = h('th', { class: 'nm', title: r.temp ? `${r.name} — 누른 메시지의 원인 신호와 E·X 값이 생긴 자리입니다. 저장되지 않습니다.` : r.name },
      h('span', { class: 'rname mono' }, r.name), r.width > 1 ? h('span', { class: 'rw' }, `[${r.width - 1}:0]`) : null,
      r.temp ? h('button', { class: 'unpin', type: 'button', title: '임시 줄을 지웁니다', 'aria-label': `Remove ${r.name}`, 'data-remove': r.id }, '×') : null);
    const tr = h('tr', { class: `crow${r.temp ? ' temp' : ''}`, 'data-row': r.id }, name);
    if (r.width === 1) {
      cycles.forEach((c, i) => {
        const w = wave(halves, values, i);
        const level = (v: string) => (v === '1' ? 'hi' : v === '0' ? 'lo' : v === 'E' ? 'err' : v === '' ? 'none' : 'x');
        tr.append(h('td', { class: `${cls(i)} wavecell`, 'data-cycle': String(c), title: `Cycle ${c}: ${w.a} → ${w.b}` },
          h('span', { class: `w ${level(w.a)}${w.edgeIn ? ' edge' : ''}` }), h('span', { class: `w ${level(w.b)}${w.edgeMid ? ' edge' : ''}` })));
      });
    } else {
      cycles.forEach((c, i) => {
        tr.append(h('td', { class: `${cls(i)}${sameAsBefore(values, i) ? ' same' : ''}`, 'data-cycle': String(c) }, code(cellText(values[i], r.width))));
      });
    }
    body.append(tr);
    if (r.bits && r.width > 1) {
      for (let b = r.width - 1; b >= 0; b -= 1) {
        const sub = h('tr', { class: 'crow bit' }, h('th', { class: 'nm' }, code(`${r.name}[${b}]`, 'rname')));
        const bitValues = values.map((v) => bitOf(v, b));
        cycles.forEach((c, i) => sub.append(h('td', { class: `${cls(i)}${sameAsBefore(bitValues, i) ? ' same' : ''}`, 'data-cycle': String(c) }, code(bitValues[i] ?? ''))));
        body.append(sub);
      }
    }
  }

  private renderRegisters(r: RegisterData): void {
    const views = registerViews(r.rows);
    if (r.mode === 'none' || views.length === 0) {
      this.regsHint.hidden = false;
      this.regsHint.replaceChildren(codeText('이 회로에는 `Register` 부품이 없습니다. 레지스터를 놓으면 사이클마다 값이 여기에 16·10·2진수로 나옵니다.'));
    } else if (r.unmapped) {
      this.regsHint.hidden = false;
      this.regsHint.replaceChildren(codeText('레지스터 파일을 표시하지 않아 모든 레지스터를 나열합니다. 레지스터 파일 서브회로는 머리의 `Mark as Register File…` 단추로 고릅니다.'));
    } else {
      this.regsHint.hidden = true;
    }
    this.registers.update(views, registerGroups(views), shape(views));
    this.setAside(this.tab === 1 ? this.regToggles : this.asideButtons);
  }

  // ---- commands ------------------------------------------------------------------------

  private async call<T>(method: WindowMethod, params: Record<string, unknown>, what: string): Promise<T | null> {
    try {
      return await this.host.call<T>(method, params);
    } catch (e) {
      this.host.note('err', commandError(what, e as CallError));
      return null;
    }
  }

  // A message with a cycle was chosen (v1 D-05, V-03): its cause and the place the E/X appeared become
  // the table's top rows, and the circuit shows that cycle (record.pin views it).
  async pinMessage(fileId: string, messageId: string, cycle: number, spots: PinSpot[]): Promise<void> {
    const r = await this.call<{ ids: string[] }>('record.pin', { fileId, cycle, rows: spots }, 'Cycle View');
    if (!r) return;
    this.pinnedBy.set(fileId, messageId);
    if (fileId === this.host.fileId()) this.refresh();
  }

  // The Messages list changed: when the message whose rows are pinned went, the rows go too (D-114).
  messagesChanged(fileId: string, ids: readonly string[]): void {
    if (!pinGone(this.pinnedBy.get(fileId), ids)) return;
    this.pinnedBy.delete(fileId);
    void this.call('record.unpin', { fileId }, 'Cycle View').then(() => { if (fileId === this.host.fileId()) this.refresh(); });
  }

  // Shows a cycle (null: the latest).
  async view(cycle: number | null): Promise<void> {
    const fileId = this.host.fileId();
    if (!fileId) return;
    await this.call('record.view', cycle === null ? { fileId, latest: true } : { fileId, cycle }, 'Cycle View');
  }

  // Previous / Next: at the latest cycle, Next runs one.
  private async step(d: number): Promise<void> {
    const fileId = this.host.fileId();
    const s = fileId ? this.states.get(fileId) : undefined;
    if (!fileId) return;
    if (d > 0 && (!s || s.empty || s.cycle >= s.last)) {
      await this.call('sim.cycles', { fileId, n: 1 }, '1 Cycle');
      return;
    }
    if (!s || s.empty) return;
    await this.view(d > 0 && s.cycle + 1 >= s.last ? null : Math.max(s.first, s.cycle + d));
  }

  private clicked(e: MouseEvent): void {
    const t = e.target as HTMLElement;
    const remove = t.closest('[data-remove]') as HTMLElement | null;
    const fileId = this.host.fileId();
    if (remove && fileId) {
      void this.call('record.removeRow', { fileId, id: remove.dataset.remove }, 'Cycle View').then(() => this.refresh());
      return;
    }
    const cell = t.closest('[data-cycle]') as HTMLElement | null;
    if (!cell) return;
    const c = Number(cell.dataset.cycle);
    const s = fileId ? this.states.get(fileId) : undefined;
    void this.view(s && c >= s.last ? null : c);
  }

  private rowMenu(e: MouseEvent): void {
    const tr = (e.target as HTMLElement).closest('tr[data-row]') as HTMLElement | null;
    const fileId = this.host.fileId();
    const row = this.table?.rows.find((r) => r.id === tr?.dataset.row);
    if (!tr || !row || !fileId) return;
    e.preventDefault();
    const items = [];
    if (row.width > 1) {
      items.push({ label: row.bits ? 'Hide Bits' : 'Show Bits', run: () => void this.call('record.rowBits', { fileId, id: row.id, bits: !row.bits }, 'Cycle View').then(() => this.refresh()) });
    }
    items.push({ label: 'Remove from Cycle View', run: () => void this.call('record.removeRow', { fileId, id: row.id }, 'Cycle View').then(() => this.refresh()) });
    popupMenu(items, e.clientX, e.clientY);
  }

  // A register row's menu: Mark as PC (a top-level Register or Counter), and the register file's commands.
  private registerMenu(key: string, x: number, y: number): void {
    const fileId = this.host.fileId();
    const regs = this.regs;
    const row = regs?.rows.find((r) => r.key === key);
    if (!fileId || !regs || !row) return;
    const items: MenuItem[] = [];
    if (row.markable && row.componentId && regs.circuitId) {
      const on = !row.markedPc;
      const circuitId = regs.circuitId;
      items.push({
        label: on ? 'Mark as PC' : 'Unmark as PC',
        run: () => void this.call<{ changed: boolean; dirty: boolean }>('record.markPc', { fileId, circuitId, componentId: row.componentId, on }, 'Mark as PC')
          .then((r) => { if (r) this.host.dirty(fileId, r.dirty); this.refresh(); }),
      });
    }
    if (regs.mode === 'file' && regs.registerFile) {
      const rf = regs.registerFile.circuitId;
      items.push({ label: 'Register Mapping…', run: () => void this.mapping() });
      items.push({ label: 'Unmark Register File', run: () => void this.markRegisterFile(rf, false) });
    } else if ((regs.candidates ?? []).length > 0) {
      items.push({ label: 'Mark as Register File…', run: () => void this.chooseRegisterFile() });
    }
    if (items.length) popupMenu(items, x, y);
  }

  private async markRegisterFile(circuitId: string, on: boolean): Promise<void> {
    const fileId = this.host.fileId();
    if (!fileId || !circuitId) return;
    const r = await this.call<{ changed: boolean; dirty: boolean }>('record.markRegisterFile', { fileId, circuitId, on }, 'Register File');
    if (r) this.host.dirty(fileId, r.dirty);
    this.refresh();
  }

  private async chooseRegisterFile(): Promise<void> {
    const cands = this.regs?.candidates ?? [];
    if (!cands.length) return;
    const select = h('select', { class: 'cselect', 'aria-label': 'Register file' },
      ...cands.map((c) => h('option', { value: c.circuitId }, `${c.name} (${c.registers} register${c.registers === 1 ? '' : 's'})`)));
    const body = h('div', { class: 'cform' },
      h('p', {}, codeText('레지스터 파일로 쓰는 서브회로를 고릅니다. 그 안의 `Register` 부품이 라벨 숫자, 라벨 이름, 위치 순으로 `$0`~`$31` 번호에 대응됩니다. 표시는 파일에 함께 저장됩니다.')),
      h('label', { class: 'cfield' }, h('span', {}, 'Subcircuit'), select));
    if (await ask({ title: '레지스터 파일 고르기', body, ok: 'Mark as Register File', cancel: 'Cancel' })) {
      await this.markRegisterFile(select.value, true);
    }
  }

  private async mapping(): Promise<void> {
    const fileId = this.host.fileId();
    if (!fileId) return;
    const m = await this.call<RegisterMapping>('record.registerMapping', { fileId }, 'Register Mapping');
    if (!m) return;
    const names = ['$zero', '$at', '$v0', '$v1', '$a0', '$a1', '$a2', '$a3', '$t0', '$t1', '$t2', '$t3', '$t4', '$t5', '$t6', '$t7',
      '$s0', '$s1', '$s2', '$s3', '$s4', '$s5', '$s6', '$s7', '$t8', '$t9', '$k0', '$k1', '$gp', '$sp', '$fp', '$ra'];
    const place = (p: Point | null | undefined) => (p ? `${p[0]},${p[1]}` : '');
    const selects = names.map((n, i) => h('select', { class: 'cselect mono', 'aria-label': n },
      h('option', { value: '' }, '(none)'),
      ...m.registers.map((r) => h('option', { value: place(r.loc), selected: place(m.map[String(i)]) === place(r.loc) }, r.name))));
    const body = h('div', { class: 'cform' },
      h('p', {}, codeText('번호마다 회로의 `Register` 부품을 고릅니다. 처음 값은 라벨 숫자, 라벨 이름, 위치 순으로 짐작한 것입니다. 짐작과 다른 것만 파일에 저장됩니다.')),
      h('div', { class: 'cmap' }, ...names.map((n, i) => h('label', { class: 'cfield' }, h('span', { class: 'mono' }, `$${i} ${n}`), selects[i]))));
    if (!(await ask({ title: `Register Mapping · ${m.name}`, body, ok: 'Apply', cancel: 'Cancel' }))) return;
    const map: Record<string, Point | null> = {};
    selects.forEach((s, i) => { map[String(i)] = s.value === '' ? null : s.value.split(',').map(Number) as Point; });
    const r = await this.call<{ changed: boolean; dirty: boolean }>('record.setRegisterMapping', { fileId, circuitId: m.circuitId, map }, 'Register Mapping');
    if (r) this.host.dirty(fileId, r.dirty);
    this.refresh();
  }

  // Run Until…: the condition in the window's own dialog; Stop while it runs.
  async runUntil(): Promise<void> {
    const fileId = this.host.fileId();
    if (!fileId) return;
    if (this.running(fileId)) {
      await this.call('record.stop', { fileId }, 'Run Until');
      return;
    }
    const rows = this.table?.rows ?? [];
    const kind = h('select', { class: 'cselect', 'aria-label': 'Condition' }, ...UNTIL_KINDS.map((k) => h('option', { value: k.kind }, k.label)));
    const value = h('input', { class: 'cinput mono', type: 'text', 'aria-label': 'Value', spellcheck: 'false', autocomplete: 'off' });
    const row = h('select', { class: 'cselect', 'aria-label': 'Row' }, ...rows.map((r) => h('option', { value: r.id }, r.name)));
    const max = h('input', { class: 'cinput mono', type: 'text', 'aria-label': 'Max Cycles', value: '10000', inputmode: 'numeric' });
    const help = h('p', { class: 'chelp' });
    const why = h('p', { class: 'cwhy', role: 'status' });
    const valueField = h('label', { class: 'cfield' }, h('span', {}, 'Value'), value);
    const rowField = h('label', { class: 'cfield' }, h('span', {}, 'Row'), row);
    const body = h('div', { class: 'cform' },
      h('label', { class: 'cfield' }, h('span', {}, 'Condition'), kind), valueField, rowField,
      h('label', { class: 'cfield' }, h('span', {}, 'Max Cycles'), max), help, why);
    const form = (): UntilForm => ({ kind: kind.value as UntilForm['kind'], value: value.value, row: row.value, max: max.value });
    let ok: HTMLButtonElement | null = null;
    const update = () => {
      const k = UNTIL_KINDS.find((x) => x.kind === kind.value)!;
      valueField.hidden = k.kind !== 'pc' && k.kind !== 'instruction';
      rowField.hidden = k.kind !== 'row';
      help.replaceChildren(codeText(k.help));
      const r = untilRequest(form());
      why.replaceChildren(r.ok ? '' : codeText(r.why));
      if (ok) ok.disabled = !r.ok;
    };
    for (const el of [kind, value, row, max]) el.addEventListener('input', update);
    for (const el of [value, max]) el.addEventListener('keydown', (e) => { if (e.key === 'Enter' && ok && !ok.disabled) ok.click(); });
    const answer = ask({ title: 'Run Until', body, ok: 'Run', cancel: 'Cancel' });
    ok = body.closest('dialog')?.querySelector('.btn.primary') ?? null;
    update();
    value.focus();
    if (!(await answer)) return;
    const r = untilRequest(form());
    if (!r.ok) return;
    this.host.note('', '');
    await this.call('record.runUntil', { fileId, ...r.params }, 'Run Until');
  }
}
