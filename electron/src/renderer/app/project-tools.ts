/* The rest of v1's commands on screen (N-21, D-162; the words and small
   rules in logic/project-tools.ts):

     Undo History     Edit › Undo History…: a window over the Canvas's top
                      left that stays open while the student works (v1: a
                      window that is not modal, one per file).  Start of
                      History, the undo rows (oldest first), ▶ Now, the redo
                      rows; one click goes there -- one edit.history intent
                      (journaled, D-142).  It follows every change.
     Analyze Circuit  Project › Analyze Circuit (the original's Combinational
                      Analysis, read only): Inputs, Outputs, Table,
                      Expression, Minimized.
     Statistics       Project › Get Circuit Statistics: the original's table.
     Submission       File › Create Submission…: saved first, then v1's
                      checks and the files, Create → the save dialog → zip.
     Export Image     File › Export Image…: PNG (1–4 x), SVG, PDF of the
                      circuit on show (or the selection), with or without
                      the label chips and bus widths (v1's "overlays"); the
                      Canvas's own shapes (canvas.ts exportSvg, D-137).
     Print            File › Print… (Ctrl+P): the original's choices --
                      circuits, header, Rotate to Fit, Printer View -- then
                      the system's print dialog.

   Dialogs are the window's own (the ask dialog's look without a character:
   a check may say something is wrong).  Titles and field names English,
   sentences Korean. */

import type { Analysis, History, Snapshot, Statistics, SubmissionPlan } from '../../main/protocol.ts';
import { CircuitCanvas } from '../canvas/canvas.ts';
import { Scene } from '../canvas/scene.ts';
import { code, h, icon, prose } from '../shared/dom.ts';
import type { AppApi, CallError } from './api.ts';
import type { OpenFile } from './logic/files.ts';
import { commandError } from './logic/errors.ts';
import {
  analyzeProblemText, analyzeSourceText, ENTRY_TITLES, EXPORT_SENTENCE, PRINT_NONE, PRINT_SENTENCE, expressionParts, FORMATS, HISTORY_HINT, historyLines, type PictureFormat, pictureName,
  PRINT_HEADER, SCALES, statisticsLines, STATISTICS_HINT, subSnapshot, SUBMISSION_HINT, submissionChecks,
} from './logic/project-tools.ts';

export interface ProjectToolsHost {
  api: AppApi;
  ready(): boolean;
  active(): OpenFile | null;
  // the circuit on show: its scene (the values it has), and the ids selected in it
  shownScene(): Scene | null;
  selected(): string[];
  busWidths(): boolean;
  save(f: OpenFile): Promise<boolean>;
  note(cls: '' | 'err' | 'ok', text: string | null): void;
  circuitName(f: OpenFile, circuitId: string): string;
}

// ---- a dialog of the window's own ----------------------------------------------------------------

function dialog(o: { title: string; sentence?: string; body: Node[]; ok?: string; cancel?: string | null; cls?: string; onOpen?(d: HTMLDialogElement): void }): Promise<boolean> {
  return new Promise((answer) => {
    const ok = o.ok ? h('button', { class: 'btn primary', type: 'button' }, o.ok) : null;
    const cancel = o.cancel === null ? null : h('button', { class: 'btn', type: 'button' }, o.cancel ?? (o.ok ? 'Cancel' : 'Close'));
    const d = h('dialog', { class: `modal ask menudlg tooldlg ${o.cls ?? ''}`.trim(), 'aria-label': o.title },
      h('div', { class: 'asktext' }, h('h2', {}, o.title), o.sentence ? h('p', {}, prose(o.sentence)) : null, ...o.body,
        h('div', { class: 'row end' }, cancel, ok)));
    let taken = false;
    ok?.addEventListener('click', () => { taken = true; d.close(); });
    cancel?.addEventListener('click', () => d.close());
    d.addEventListener('close', () => { d.remove(); document.body.classList.remove('dialog-open'); answer(taken); });
    document.body.append(d);
    document.body.classList.add('dialog-open');
    d.showModal();
    (ok ?? cancel)?.focus();
    o.onOpen?.(d);
  });
}

const field = (name: string, el: HTMLElement): HTMLElement => h('label', { class: 'mfield' }, h('span', { class: 'fieldname' }, name), el);

// ---- Undo History --------------------------------------------------------------------------------

export interface HistoryWindow { root: HTMLElement; open(): void; close(): void; isOpen(): boolean; refresh(): void }

export function historyWindow(host: ProjectToolsHost): HistoryWindow {
  const list = h('ul', { class: 'histlist', role: 'listbox', 'aria-label': 'Undo History', tabindex: '0' });
  const close = h('button', { type: 'button', class: 'iconbtn histclose', title: 'Close (Esc)', 'aria-label': 'Close' }, icon('x'));
  const root = h('section', { class: 'histwin', role: 'dialog', 'aria-label': 'Undo History', hidden: true },
    h('div', { class: 'phead' }, h('span', { class: 'ptitle' }, 'Undo History'), h('span', { class: 'pgrow' }), close),
    list, h('div', { class: 'histfoot' }, prose(HISTORY_HINT)));
  let asked = 0;
  let busy = false;

  async function refresh(): Promise<void> {
    if (root.hidden) return;
    const f = host.active();
    const n = ++asked;
    let hist: History | null = null;
    if (f && host.ready()) hist = await host.api.call<History>('model.history', { fileId: f.fileId }).catch(() => null);
    if (n !== asked) return;
    const lines = historyLines(hist);
    list.replaceChildren(...lines.map((l) => {
      const li = h('li', {
        role: 'option', class: `histrow ${l.kind}`, 'aria-selected': String(l.kind === 'now'), title: l.title,
        'data-moves': String(l.moves),
      }, l.kind === 'now' ? h('span', { class: 'histnow', 'aria-hidden': 'true' }, '▶') : null, l.text);
      li.addEventListener('mousedown', (e) => e.preventDefault());
      li.addEventListener('click', () => void go(l.moves));
      return li;
    }));
    if (!f) list.replaceChildren(h('li', { class: 'histempty' }, '파일을 열면 되돌릴 수 있는 동작이 여기에 나옵니다.'));
    list.querySelector('.histrow.now')?.scrollIntoView({ block: 'nearest' });
  }

  async function go(moves: number): Promise<void> {
    const f = host.active();
    if (!f || moves === 0 || busy || !host.ready()) return;
    busy = true;
    try {
      await host.api.call('edit.history', { fileId: f.fileId, circuitId: f.circuit, moves });
      host.note('', null);
    } catch (e) {
      host.note('err', commandError('Undo History', e as CallError));
    } finally {
      busy = false;
    }
    void refresh();
  }

  close.addEventListener('click', () => { root.hidden = true; });
  root.addEventListener('keydown', (e) => { if (e.key === 'Escape') { e.preventDefault(); root.hidden = true; } });
  return {
    root,
    open: () => { root.hidden = false; void refresh(); list.focus(); },
    close: () => { root.hidden = true; },
    isOpen: () => !root.hidden,
    refresh: () => void refresh(),
  };
}

// ---- Get Circuit Statistics ---------------------------------------------------------------------------

export function showStatistics(s: Statistics): Promise<boolean> {
  const head = h('tr', {}, ...['Component', 'Library', 'Simple', 'Unique', 'Recursive'].map((t, i) => h('th', { class: i > 1 ? 'num' : '' }, t)));
  const row = (l: ReturnType<typeof statisticsLines>[number]) => h('tr', { class: l.total ? 'total' : '' },
    h('td', {}, l.component), h('td', {}, l.library), h('td', { class: 'num' }, String(l.simple)), h('td', { class: 'num' }, String(l.unique)), h('td', { class: 'num' }, String(l.recursive)));
  const lines = statisticsLines(s);
  // the two TOTAL rows stay in view at the table's foot (the rows scroll above them)
  return dialog({
    title: `${s.circuit} Statistics`, cls: 'statsdlg',
    body: [h('div', { class: 'tooltable' }, h('table', { class: 'stats' }, h('thead', {}, head),
      h('tbody', {}, ...lines.filter((l) => !l.total).map(row)), h('tfoot', {}, ...lines.filter((l) => l.total).map(row)))),
    h('p', { class: 'hint' }, prose(STATISTICS_HINT))],
    cancel: 'Close',
  });
}

// ---- Analyze Circuit -------------------------------------------------------------------------------

function expression(expr: string | null): HTMLElement {
  if (!expr) return h('span', { class: 'hint' }, '(없음)');
  return h('span', { class: 'expr mono' }, ...expressionParts(expr).map((p) => (p.not ? h('span', { class: 'not' }, p.text) : p.text)));
}

export function showAnalysis(a: Analysis): Promise<boolean> {
  const tabs: [string, () => HTMLElement][] = [
    ['Inputs', () => h('ol', { class: 'varlist' }, ...a.inputs.map((n) => h('li', {}, code(n))))],
    ['Outputs', () => h('ol', { class: 'varlist' }, ...a.outputs.map((n) => h('li', {}, code(n))))],
  ];
  const problem = analyzeProblemText(a);
  if (!problem && a.table && a.expressions) {
    const table = a.table;
    const truth = (): HTMLElement => {
      const head = h('tr', {}, ...a.inputs.map((n) => h('th', {}, n)), ...a.outputs.map((n, i) => h('th', { class: i === 0 ? 'out first' : 'out' }, n)));
      const body = table.rows.map((r) => h('tr', {},
        ...[...r[0]].map((b) => h('td', {}, b)),
        ...r.slice(1).map((v, i) => h('td', { class: `${i === 0 ? 'out first' : 'out'}${ENTRY_TITLES[v] ? ' odd' : ''}`, title: ENTRY_TITLES[v] ?? '' }, v))));
      return h('div', { class: 'tooltable' }, h('table', { class: 'truth mono' }, h('thead', {}, head), h('tbody', {}, ...body)));
    };
    tabs.push(['Table', truth]);
    tabs.push(['Expression', () => h('dl', { class: 'exprs' }, ...a.expressions!.flatMap((x) => [h('dt', {}, code(x.output)), h('dd', {}, expression(x.expression))]))]);
    tabs.push(['Minimized', () => h('dl', { class: 'exprs' }, ...a.expressions!.flatMap((x) => [h('dt', {}, code(x.output)),
      h('dd', {}, h('span', { class: 'fmt' }, 'Sum of Products'), expression(x.sop)), h('dd', {}, h('span', { class: 'fmt' }, 'Product of Sums'), expression(x.pos))]))]);
  }
  const strip = h('div', { class: 'tooltabs', role: 'tablist' });
  const pane = h('div', { class: 'toolpane', role: 'tabpanel' });
  const buttons = tabs.map(([name, make], i) => {
    const b = h('button', { type: 'button', role: 'tab', class: 'tooltab', 'aria-selected': 'false' }, name);
    b.addEventListener('click', () => pick(i));
    void make;
    return b;
  });
  strip.append(...buttons);
  function pick(i: number): void {
    buttons.forEach((b, k) => b.setAttribute('aria-selected', String(k === i)));
    pane.replaceChildren(tabs[i][1]());
  }
  // the original's first tab: Inputs when there are none, Outputs when there are none, else the expression (or the table)
  const first = problem === null ? tabs.findIndex(([n]) => n === (a.source === 'expression' ? 'Expression' : 'Table'))
    : a.problem === 'noOutputs' || a.problem === 'tooManyOutputs' || a.problem === 'multibitOutput' ? 1 : 0;
  pick(Math.max(0, first));
  const notes = [problem, analyzeSourceText(a)].filter((t): t is string => !!t).map((t) => h('p', { class: 'toolnote' }, prose(t)));
  return dialog({ title: `Combinational Analysis: ${a.circuit}`, cls: 'analysisdlg', body: [...notes, strip, pane], cancel: 'Close' });
}

// ---- Create Submission --------------------------------------------------------------------------------

export function askSubmission(p: SubmissionPlan): Promise<boolean> {
  const checks = submissionChecks(p).map((c) => h('li', { class: c.ok ? 'ok' : 'warn' }, h('span', { class: 'mark', 'aria-hidden': 'true' }, c.ok ? '✓' : '!'), h('span', { class: 'checktext' }, prose(c.text))));
  const files = h('ul', { class: 'subfiles' }, ...p.files.map((f) => h('li', {}, code(f))));
  const missing = p.missing.length ? h('ul', { class: 'subfiles missing' }, ...p.missing.map((f) => h('li', {}, code(f)))) : null;
  return dialog({
    title: 'Create Submission', cls: 'subdlg',
    body: [h('ul', { class: 'checks' }, ...checks), h('h3', {}, `Files (${p.files.length})`), files,
      ...(missing ? [h('h3', {}, `Not Included (${p.missing.length})`), missing] : []), h('p', { class: 'hint' }, prose(SUBMISSION_HINT))],
    ok: 'Create…', cancel: 'Cancel',
  });
}

// ---- Export Image and Print --------------------------------------------------------------------------

export interface ExportChoice { format: PictureFormat; scale: number; selection: boolean; chips: boolean }

export function askExport(o: { selection: boolean }): Promise<ExportChoice | null> {
  const format = h('select', { 'aria-label': 'Format' }, ...FORMATS.map(([v, n]) => h('option', { value: v }, n))) as HTMLSelectElement;
  const scale = h('select', { 'aria-label': 'Scale' }, ...SCALES.map((s) => h('option', { value: String(s), selected: s === 2 }, `${s}×`))) as HTMLSelectElement;
  const all = h('input', { type: 'radio', name: 'scope', value: 'all', checked: true }) as HTMLInputElement;
  const sel = h('input', { type: 'radio', name: 'scope', value: 'selection', disabled: !o.selection }) as HTMLInputElement;
  const chips = h('input', { type: 'checkbox', checked: true, 'aria-label': 'Label Chips and Bus Widths' }) as HTMLInputElement;
  format.addEventListener('change', () => { scale.disabled = format.value !== 'png'; });
  let choice: ExportChoice | null = null;
  return dialog({
    title: 'Export Image', cls: 'exportdlg',
    sentence: EXPORT_SENTENCE,
    body: [h('div', { class: 'mfields' },
      field('Format', format), field('Scale', scale),
      field('Range', h('span', { class: 'mradios' }, h('label', {}, all, 'Entire Circuit'), h('label', {}, sel, 'Selection Only'))),
      field('Chips', h('label', { class: 'mcheck' }, chips, 'Label Chips and Bus Widths')))],
    ok: 'Export…',
    onOpen: () => format.focus(),
  }).then((ok) => {
    if (ok) choice = { format: format.value as PictureFormat, scale: Number(scale.value), selection: sel.checked, chips: chips.checked };
    return choice;
  });
}

export interface PrintChoice { circuits: string[]; header: string; rotate: boolean; printer: boolean }

export function askPrint(o: { circuits: { circuitId: string; name: string }[]; shown: string }): Promise<PrintChoice | null> {
  const boxes = o.circuits.map((c) => ({ c, box: h('input', { type: 'checkbox', checked: c.circuitId === o.shown }) as HTMLInputElement }));
  const header = h('input', { type: 'text', class: 'field mono', value: PRINT_HEADER, 'aria-label': 'Header', spellcheck: 'false' }) as HTMLInputElement;
  const rotate = h('input', { type: 'checkbox', checked: true }) as HTMLInputElement;
  const printer = h('input', { type: 'checkbox', checked: true }) as HTMLInputElement;
  const why = h('span', { class: 'hint err', role: 'alert' });
  let result: PrintChoice | null = null;
  return dialog({
    title: 'Print', cls: 'printdlg',
    sentence: PRINT_SENTENCE,
    body: [h('div', { class: 'printcircs' }, ...boxes.map(({ c, box }) => h('label', { class: 'mcheck' }, box, code(c.name)))),
      h('div', { class: 'mfields' }, field('Header', header)),
      h('div', { class: 'mextra' }, h('label', { class: 'mcheck' }, rotate, 'Rotate To Fit'), h('label', { class: 'mcheck' }, printer, 'Printer View')),
      why],
    ok: 'Print…',
    onOpen: (d) => {
      // at least one circuit: the dialog stays until one is chosen
      d.querySelector<HTMLButtonElement>('.btn.primary')!.addEventListener('click', (e) => {
        if (!boxes.some((b) => b.box.checked)) { e.stopImmediatePropagation(); why.textContent = PRINT_NONE; }
      }, { capture: true });
    },
  }).then((ok) => {
    if (ok) result = { circuits: boxes.filter((b) => b.box.checked).map((b) => b.c.circuitId), header: header.value, rotate: rotate.checked, printer: printer.checked };
    return result;
  });
}

/* A canvas no one sees, drawing one scene for a picture: the scene on show (its values, or the chosen
   parts of it) or another circuit's snapshot (no values).  Printer View: no values (the original's
   print view draws the circuit, not its state), every wire in ink. */
export function pictureSvg(o: { snapshot: Snapshot; fileId: string; values?: Scene | null; printer?: boolean; chips: boolean; busWidths: boolean }): string {
  const w = window as unknown as { __hcsCanvas?: unknown };
  const keep = w.__hcsCanvas;
  const c = new CircuitCanvas();
  w.__hcsCanvas = keep;       // the Canvas on screen stays the tests' and tools' one
  const scene = new Scene(o.fileId, o.snapshot);
  if (o.values && !o.printer) {
    for (const [k, v] of o.values.values) scene.values.set(k, v);
    for (const [k, v] of o.values.bodies) scene.bodies.set(k, v);
  }
  c.busWidths = o.busWidths;
  c.setScene(scene, { x: 0, y: 0, zoom: 1 });
  if (o.printer) c.theme = { ...c.theme, vNone: c.theme.ink };
  return c.exportSvg(undefined, { chips: o.chips });
}

// ---- the commands ------------------------------------------------------------------------------------

export class ProjectTools {
  readonly history: HistoryWindow;
  private readonly host: ProjectToolsHost;

  constructor(host: ProjectToolsHost) {
    this.host = host;
    this.history = historyWindow(host);
  }

  private fail(name: string, e: unknown): void { this.host.note('err', commandError(name, e as CallError)); }

  async analyze(circuitId: string): Promise<void> {
    const f = this.host.active();
    if (!f || !this.host.ready()) return;
    try {
      await showAnalysis(await this.host.api.call<Analysis>('model.analyze', { fileId: f.fileId, circuitId }));
    } catch (e) { this.fail('Analyze Circuit', e); }
  }

  async statistics(circuitId: string): Promise<void> {
    const f = this.host.active();
    if (!f || !this.host.ready()) return;
    try {
      await showStatistics(await this.host.api.call<Statistics>('model.statistics', { fileId: f.fileId, circuitId }));
    } catch (e) { this.fail('Get Circuit Statistics', e); }
  }

  // Saved first (v1: the file on disk is what goes in; a new file asks where), then the checks, then the zip.
  async submission(): Promise<void> {
    const f = this.host.active();
    if (!f || !this.host.ready()) return;
    try {
      const dirty = (await this.host.api.call<{ dirty: boolean }>('file.dirty', { fileId: f.fileId })).dirty;
      if ((dirty || f.path === null) && !(await this.host.save(f))) return;
      const plan = await this.host.api.submissionPlan(f.fileId);
      if (!(await askSubmission(plan))) return;
      const r = await this.host.api.submissionWrite(f.fileId);
      if (r?.written) this.host.note('ok', `제출 파일을 만들었습니다 · ${r.written.name} · 파일 ${r.written.count}개`);
    } catch (e) { this.fail('Create Submission', e); }
  }

  async exportImage(): Promise<void> {
    const f = this.host.active();
    const scene = this.host.shownScene();
    if (!f || !scene || !this.host.ready()) { this.host.note('err', 'Export Image: Canvas 화면에 내보낼 회로가 없습니다.'); return; }
    const ids = this.host.selected();
    const c = await askExport({ selection: ids.length > 0 });
    if (!c) return;
    const snap = c.selection ? subSnapshot(scene.snapshot(), ids) : scene.snapshot();
    if (snap.components.length === 0 && snap.wires.length === 0) { this.host.note('err', 'Export Image: 내보낼 부품이나 선이 없습니다.'); return; }
    const svg = pictureSvg({ snapshot: snap, fileId: f.fileId, values: scene, chips: c.chips, busWidths: this.host.busWidths() });
    try {
      const r = await this.host.api.exportPicture(f.fileId, { format: c.format, svg, name: pictureName(f.name, scene.name, c.format), scale: c.scale });
      if (r) this.host.note('ok', `내보냈습니다 · ${r.name}${c.format === 'png' && r.scale !== c.scale ? ` · 너무 커서 배율을 ${r.scale}×로 줄였습니다` : ''}`);
    } catch (e) { this.fail('Export Image', e); }
  }

  async print(): Promise<void> {
    const f = this.host.active();
    if (!f || !this.host.ready()) return;
    const c = await askPrint({ circuits: f.circuits, shown: f.circuit });
    if (!c) return;
    try {
      const shown = this.host.shownScene();
      const pages: { svg: string; name: string }[] = [];
      for (const id of c.circuits) {
        const snap = shown && shown.circuitId === id ? shown.snapshot() : await this.host.api.call<Snapshot>('model.circuit', { fileId: f.fileId, circuitId: id });
        if (snap.components.length === 0 && snap.wires.length === 0) continue;
        pages.push({ svg: pictureSvg({ snapshot: snap, fileId: f.fileId, values: shown && shown.circuitId === id ? shown : null, printer: c.printer, chips: true, busWidths: this.host.busWidths() }), name: this.host.circuitName(f, id) });
      }
      if (pages.length === 0) { this.host.note('err', 'Print: 고른 회로가 모두 비어 있어 인쇄하지 않았습니다.'); return; }
      const r = await this.host.api.printPictures(pages, { header: c.header, rotate: c.rotate });
      if (r.printed) this.host.note('ok', `인쇄로 보냈습니다 · ${r.pages}쪽`);
    } catch (e) { this.fail('Print', e); }
  }
}
