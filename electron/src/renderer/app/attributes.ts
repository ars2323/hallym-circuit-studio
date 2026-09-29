/* The Attributes panel (N-10, D-157): Logisim's attribute table in Hallym
   MIPS's Inspector form -- a head with the name and what the table is of
   (Selection, Circuit, Tool), the facts of the selection under it (N-08),
   then the table: Attribute | Value, a row per attribute the original
   table shows (the selection's -- only those every part has, empty where
   they differ --, the circuit's when nothing is selected, the tool's in
   hand), each with the editor its kind wants: a list (Data Bits, Facing,
   Yes/No and every original list), a field (Label; a number, hex where
   the original writes hex), a font, a colour, a ROM's contents (the hex
   editor).  At the foot: Quick Attributes (the bar by the selection, for
   this run).

   A value goes to the engine as the .circ's text (edit.setAttr on the
   selection, edit.setToolAttr on the tool, edit.setCircuitAttr on the
   circuit: one undo step each); the engine reads it with the original's
   parse and refuses what the original refuses -- the row turns red and a
   Korean sentence says why (no character: it is an error).  A list applies
   at once; a field on Enter or when it loses the focus, Esc puts it back. */

import type { AttrRow, AttrTable, WindowMethod } from '../../main/protocol.ts';
import { h } from '../shared/dom.ts';
import type { NoticeHost } from '../shared/notice.ts';
import type { CallError } from './api.ts';
import { type AttrRequest, badValueSentence, circuitNameProblem, colorField, colorValue, editorOf, fontParts, fontValue, heading, requestKey, withHeld } from './logic/attributes.ts';
import type { SelectionFacts } from './logic/selection-facts.ts';

export interface AttributesHost {
  call<T>(method: WindowMethod, params: Record<string, unknown>): Promise<T>;
  failed(name: string, e: unknown): void;               // any other refusal: the status bar's line
  facts(): SelectionFacts | null;                       // the selection's facts (N-08), above the table
  circuitNames(fileId: string, except: string): string[];   // the other circuits' names (a new name must differ)
  contents(componentId: string): void;                  // a ROM's Contents row: the hex editor
  toolChanged(): void;                                  // the tool's attributes changed: its ghost again
  heldChanged?(attr: string, value: string): void;      // a value of the part held changed (its own values, withHeld)
  quickToggled(on: boolean): void;                      // the foot's Quick Attributes
}

export const EMPTY_ATTRIBUTES = { title: '고른 부품이 없습니다', body: 'Canvas에서 부품을 고르면 그 속성(`Data Bits`, `Facing`, `Label` …)이 여기에 나옵니다.' };

export class AttributesPanel {
  private readonly body: NoticeHost;
  private readonly host: AttributesHost;
  private req: AttrRequest | null = null;
  private asked = 0;                                    // the last question's number (older answers are dropped)
  table: AttrTable | null = null;
  quickOn = true;                                       // Quick Attributes (this run only)
  private error: { attr: string; text: string; typed?: string } | null = null;
  private readonly listeners = new Set<(t: AttrTable | null) => void>();

  constructor(body: NoticeHost, host: AttributesHost) {
    this.body = body;
    this.host = host;
  }

  onTable(f: (t: AttrTable | null) => void): void { this.listeners.add(f); }

  // What to show now; null: no file (the empty state).  The same request asks again (after an edit).
  show(req: AttrRequest | null): void {
    if (requestKey(req) !== requestKey(this.req)) this.error = null;
    this.req = req;
    void this.refresh();
  }

  // force: draw even while a field is being typed in (after that field's own value was applied).
  async refresh(force = false): Promise<void> {
    const req = this.req;
    const n = ++this.asked;
    if (!req) { this.set(null, true); return; }
    let t: AttrTable | null = null;
    try {
      t = req.kind === 'tool'
        ? await this.host.call<AttrTable>('model.attributes', { fileId: req.fileId, lib: req.lib, name: req.name })
        : await this.host.call<AttrTable>('model.attributes', { fileId: req.fileId, circuitId: req.circuitId });
    } catch {
      t = null;                                         // the engine is not there (restarting): the facts only
    }
    if (n !== this.asked) return;
    if (t && req.kind === 'tool' && req.attrs) t = withHeld(t, req.attrs);
    this.set(t, force);
  }

  private pending = false;

  private set(t: AttrTable | null, force: boolean): void {
    this.table = t;
    // a field being typed in keeps its text: the new table is drawn when it loses the focus
    const a = document.activeElement as HTMLInputElement | null;
    if (!force && a && this.body.root.contains(a) && a.tagName === 'INPUT' && a.type === 'text') {
      if (!this.pending) {
        this.pending = true;
        a.addEventListener('blur', () => { this.pending = false; setTimeout(() => this.render()); }, { once: true });
      }
    } else {
      this.render();
    }
    for (const f of this.listeners) f(t);
  }

  // The panel's content now: the head, the facts, the table, the foot.
  render(): void {
    const t = this.table;
    const facts = this.host.facts();
    if (!t && !facts) {
      this.body.empty(EMPTY_ATTRIBUTES);
      return;
    }
    const head = t ? heading(t) : { name: facts!.title, badge: 'Selection' };
    const name = t?.target === 'selection' && facts ? facts.title : head.name;
    const box = h('div', { class: 'attrs' },
      h('div', { class: 'ahead' }, h('span', { class: 'aname', title: t?.title ?? name }, name), head.badge ? h('span', { class: 'badge' }, head.badge) : null),
      facts && t?.target !== 'tool' && t?.target !== 'circuit' && facts.lines.length
        ? h('ul', { class: 'afacts' }, ...facts.lines.map((l) => h('li', {}, l))) : null,
      t ? this.tableOf(t) : null,
      this.error ? h('p', { class: 'aerr', role: 'alert' }, this.error.text) : null,
      t && !t.editable && t.target !== 'tool' ? h('p', { class: 'ahint' }, '이 회로는 바꿀 수 없습니다(읽기 전용 파일이거나 불러온 라이브러리의 회로).') : null,
      this.foot());
    this.body.fill(box);
  }

  private tableOf(t: AttrTable): HTMLElement {
    if (!t.rows.length) return h('p', { class: 'ahint' }, '이 도구에는 바꿀 속성이 없습니다.');
    return h('table', { class: 'atable' },
      h('thead', {}, h('tr', {}, h('th', {}, 'Attribute'), h('th', {}, 'Value'))),
      h('tbody', {}, ...t.rows.map((r) => {
        const bad = this.error?.attr === r.attr;
        return h('tr', { class: bad ? 'bad' : undefined, 'data-attr': r.attr },
          h('th', { scope: 'row', title: r.display }, r.display),
          h('td', {}, this.editor(t, r)));
      })));
  }

  private foot(): HTMLElement {
    const box = h('input', { type: 'checkbox', checked: this.quickOn }) as HTMLInputElement;
    box.addEventListener('change', () => { this.quickOn = box.checked; this.host.quickToggled(box.checked); });
    return h('label', { class: 'afoot', title: '부품을 고르면 그 옆에 자주 바꾸는 속성을 띄웁니다. 이번 실행에만 기억합니다.' }, box, 'Quick Attributes');
  }

  // The value cell.
  private editor(t: AttrTable, r: AttrRow): HTMLElement {
    const kind = editorOf(r, t.editable);
    const label = r.display;
    switch (kind) {
      case 'text':
        return h('span', { class: `aval${monoValue(r.attr, r.text) ? ' mono' : ''}`, title: r.text }, r.text);
      case 'contents': {
        const b = h('button', { type: 'button', class: 'alink', 'aria-label': label }, r.text || '(click to edit)');
        const ids = t.target === 'selection' ? this.selectionIds() : [];
        b.disabled = ids.length !== 1;
        b.addEventListener('click', () => { if (ids.length === 1) this.host.contents(ids[0]); });
        return b;
      }
      case 'select': {
        // numbers (Data Bits 1…32, Number Of Inputs) in D2Coding like every value; words (East, Yes) in Pretendard (D-158)
        const numbers = (r.options ?? []).length > 0 && (r.options ?? []).every((o) => numericValue(o.display));
        const s = h('select', { 'aria-label': label, class: numbers ? 'mono' : undefined }) as HTMLSelectElement;
        if (r.mixed || r.value === null) s.append(h('option', { value: '', selected: true, disabled: true }, ''));
        for (const o of r.options ?? []) s.append(h('option', { value: o.value, selected: o.value === r.value }, o.display));
        s.addEventListener('change', () => void this.apply(t, r, s.value));
        return s;
      }
      case 'font': {
        const f = fontParts(r.value);
        const fam = h('select', { 'aria-label': `${label} family` }, ...[...new Set([...(r.families ?? []), f.family])].map((x) => h('option', { value: x, selected: x === f.family }, x))) as HTMLSelectElement;
        const sty = h('select', { 'aria-label': `${label} style` }, ...(r.styles ?? []).map((x) => h('option', { value: x.value, selected: x.value === f.style }, x.display))) as HTMLSelectElement;
        const size = h('input', { type: 'text', inputmode: 'numeric', class: 'mono size', 'aria-label': `${label} size`, value: String(f.size) }) as HTMLInputElement;
        const go = () => {
          const n = Number(size.value);
          if (!Number.isInteger(n) || n < 1 || n > 200) { this.fail(r, `${label} 속성의 크기는 1–200 사이의 수입니다.`); return; }
          const v = fontValue({ family: fam.value, style: sty.value, size: n });
          if (v !== r.value) void this.apply(t, r, v);
        };
        fam.addEventListener('change', go);
        sty.addEventListener('change', go);
        this.fieldKeys(size, r.value === null ? '' : String(f.size), go);
        return h('span', { class: 'afont' }, fam, sty, size);
      }
      case 'color': {
        const c = h('input', { type: 'color', 'aria-label': label, value: colorField(r.value) }) as HTMLInputElement;
        c.addEventListener('change', () => void this.apply(t, r, colorValue(c.value, r.value)));
        return h('span', { class: 'acolor' }, c, h('span', { class: 'mono' }, r.text));
      }
      default: {
        const input = h('input', {
          type: 'text', class: kind === 'number' || NAME_ATTRS.has(r.attr) ? 'mono' : undefined, 'aria-label': label, value: r.value === null ? '' : r.text,
          spellcheck: 'false', autocomplete: 'off', placeholder: r.mixed ? '(various)' : undefined,
          title: kind === 'number' && r.radix === 16 ? '16진수(0x1F) 또는 10진수(31)' : undefined,
        }) as HTMLInputElement;
        const before = input.value;
        this.fieldKeys(input, before, (how) => {
          if (input.value === before) { if (this.error?.attr === r.attr) { this.error = null; this.render(); } return; }
          // leaving a refused value as it was: the value stays as it is (the original table reverts too)
          if (how === 'blur' && this.error?.attr === r.attr && input.value === this.error.typed) { this.error = null; setTimeout(() => this.render()); return; }
          void this.apply(t, r, input.value);
        });
        if (this.error?.attr === r.attr) {
          input.setAttribute('aria-invalid', 'true');
          if (this.error.typed !== undefined) input.value = this.error.typed;   // what was typed stays, to be fixed
        }
        return input;
      }
    }
  }

  // A field: Enter or leaving applies; Esc puts the value back.  The Canvas's and the window's keys wait.
  private fieldKeys(input: HTMLInputElement, before: string, commit: (how: 'enter' | 'blur') => void): void {
    let done = false;
    input.addEventListener('keydown', (e) => {
      e.stopPropagation();
      if (e.isComposing) return;
      if (e.key === 'Enter') { e.preventDefault(); done = true; commit('enter'); }
      if (e.key === 'Escape') { e.preventDefault(); done = true; input.value = before; this.error = null; input.blur(); this.render(); }
    });
    input.addEventListener('focus', () => { done = false; });
    input.addEventListener('blur', () => { if (!done) commit('blur'); done = false; });
  }

  private selectionIds(): string[] {
    const req = this.req;
    return req?.kind === 'selection' ? this.selected() : [];
  }

  selected: () => string[] = () => [];

  private fail(r: AttrRow, text: string, typed?: string): void {
    this.error = { attr: r.attr, text, typed };
    this.render();
    (this.body.root.querySelector(`tr[data-attr="${CSS.escape(r.attr)}"] input, tr[data-attr="${CSS.escape(r.attr)}"] select`) as HTMLElement | null)?.focus();
  }

  // One value to the engine: the selection's, the circuit's or the tool's.
  async apply(t: AttrTable, r: AttrRow, value: string): Promise<boolean> {
    const req = this.req;
    if (!req) return false;
    if (t.target === 'circuit' && r.attr === 'circuit') {
      const why = circuitNameProblem(value, this.host.circuitNames(req.fileId, t.circuitId ?? ''));
      if (why) { this.fail(r, why, value); return false; }
    }
    // a value the part held carries of its own (the palette's "and 3", Ctrl+2..9's toolbar tool): that part's value
    // only -- the library's tool is not changed and the file stays as it was (D-158 18 ⑦); the ghost and the table
    // follow.  A row it does not carry is the tool's, as ever (edit.setToolAttr).
    if (t.target === 'tool' && req.kind === 'tool' && req.attrs?.[r.attr] !== undefined && this.host.heldChanged) {
      this.error = null;
      this.host.heldChanged(r.attr, value);
      return true;
    }
    const method: WindowMethod = t.target === 'tool' ? 'edit.setToolAttr' : t.target === 'circuit' ? 'edit.setCircuitAttr' : 'edit.setAttr';
    const params: Record<string, unknown> = t.target === 'tool'
      ? { fileId: req.fileId, lib: t.lib ?? null, name: t.name, attr: r.attr, value }
      : { fileId: req.fileId, circuitId: t.circuitId, attr: r.attr, value };
    try {
      await this.host.call(method, params);
      this.error = null;
      if (t.target === 'tool') this.host.toolChanged();
      await this.refresh(true);
      return true;
    } catch (e) {
      const err = e as CallError;
      if ((err.data as { reason?: string } | undefined)?.reason === 'badValue') {
        this.fail(r, badValueSentence(r, value), value);
      } else {
        this.host.failed(r.display, e);
        await this.refresh(true);
      }
      return false;
    }
  }
}

// A value that is a number (32, -3, 0x1F, 0/1): set in D2Coding like the Canvas's values and the tables' (0 and O
// apart; D-158, the coordinator's note on #449 and the UI review).  Words (East, Rising Edge) stay in the sentences' font.
export const numericValue = (text: string): boolean => /^[-+]?(0x[0-9a-f]+|\d+)$/i.test(text.trim());
// A name the student gives (a label, a circuit's name, a shared label): in D2Coding like names everywhere else in the
// window (the Tunnels panel, the lists, the dialogs' names).
export const NAME_ATTRS: ReadonlySet<string> = new Set(['label', 'circuit', 'clabel']);
export const monoValue = (attr: string, text: string): boolean => NAME_ATTRS.has(attr) || numericValue(text);
