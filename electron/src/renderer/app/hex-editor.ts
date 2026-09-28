/* Edit Contents… of a RAM or a ROM (N-10, D-157; the original's HexFrame,
   I-102, guide/mem/hex.html): the memory's words in rows of eight, the
   address on the left, a page of 256 words at a time.

   Keys as the original's hex editor: the arrows move the caret (Shift
   widens a range), Home/End go to the row's ends (Ctrl: the memory's),
   PgUp/PgDn a page; hex digits type the caret word's new value (Enter,
   Tab or moving applies it, Backspace takes a digit back, Esc drops it);
   Ctrl+C copies the range as hex, Ctrl+V writes hex words from the caret.

   A RAM's contents are the simulation's (as the original: not in the file,
   no undo; mem.write); a ROM's are its Contents attribute (the file keeps
   them; each change is the original "Edit ROM Contents", one undo step:
   edit.memContents).  The engine reads a value too wide for the data bits
   as refused (badValue): the word stays and the reason shows.  The page is
   read again twice a second while the dialog is open (a running circuit
   writes its RAM). */

import type { MemWords, WindowMethod } from '../../main/protocol.ts';
import { h } from '../shared/dom.ts';
import type { CallError } from './api.ts';

export const COLS = 8;
export const PAGE = 256;

export interface HexTarget {
  fileId: string;
  circuitId: string;          // the circuit the view starts from (mem.*), the memory's own (edit.memContents)
  root: string;
  path: string[];
  componentId: string;
  kind: 'ram' | 'rom';
  name: string;               // "RAM", "ROM · program"
  editable: boolean;          // a ROM in a file that cannot be changed: read only
}

export interface HexHost {
  call<T>(method: WindowMethod, params: Record<string, unknown>): Promise<T>;
}

// Hex digits for a data width, an address width.
export const digits = (bits: number): number => Math.max(1, Math.ceil(bits / 4));
export const hexOf = (v: number, bits: number): string => (v >>> 0).toString(16).padStart(digits(bits), '0');

// Hex words in a text (Ctrl+V): separated by anything but hex digits; an optional 0x each.
export function parseWords(text: string, bits: number): number[] | null {
  const max = bits >= 32 ? 0xffffffff : 2 ** bits - 1;
  const toks = text.split(/[^0-9a-fA-Fx]+/).filter(Boolean).map((t) => t.replace(/^0x/i, ''));
  if (!toks.length) return null;
  const out: number[] = [];
  for (const t of toks) {
    if (!/^[0-9a-f]+$/i.test(t)) return null;
    const v = parseInt(t, 16);
    if (!Number.isFinite(v) || v > max) return null;
    out.push(v);
  }
  return out;
}

// The words of a range as text (Ctrl+C): eight to a line.
export function wordsText(words: number[], bits: number): string {
  const lines: string[] = [];
  for (let i = 0; i < words.length; i += COLS) lines.push(words.slice(i, i + COLS).map((w) => hexOf(w, bits)).join(' '));
  return lines.join('\n');
}

// Where the caret goes for a key (null: not a moving key).  total: the memory's words.
export function caretMove(key: string, ctrl: boolean, at: number, total: number): number | null {
  const clamp = (n: number) => Math.max(0, Math.min(total - 1, n));
  switch (key) {
    case 'ArrowLeft': return clamp(at - 1);
    case 'ArrowRight': return clamp(at + 1);
    case 'ArrowUp': return clamp(at - COLS);
    case 'ArrowDown': return clamp(at + COLS);
    case 'PageUp': return clamp(at - PAGE);
    case 'PageDown': return clamp(at + PAGE);
    case 'Home': return ctrl ? 0 : at - (at % COLS);
    case 'End': return ctrl ? total - 1 : clamp(at - (at % COLS) + COLS - 1);
    default: return null;
  }
}

export function hexEditor(t: HexTarget, host: HexHost): Promise<void> {
  return new Promise((done) => {
    let info: MemWords | null = null;
    let page = 0;
    let caret = 0;
    let anchor = 0;                          // the range's other end (Shift)
    let typed = '';                          // hex digits typed for the caret's word, not yet applied
    const grid = h('div', { class: 'hexgrid', tabindex: '0', role: 'grid', 'aria-label': `${t.name} contents` });
    const where = h('span', { class: 'hexwhere mono' });
    const why = h('span', { class: 'hint err', role: 'alert' });
    const go = h('input', { type: 'text', class: 'field mono', 'aria-label': 'Address', placeholder: 'Address', spellcheck: 'false', autocomplete: 'off' }) as HTMLInputElement;
    const prev = h('button', { type: 'button', class: 'btn', title: 'PgUp' }, 'Previous');
    const next = h('button', { type: 'button', class: 'btn', title: 'PgDn' }, 'Next');
    const close = h('button', { type: 'button', class: 'btn primary' }, 'Close');
    const dialog = h('dialog', { class: 'modal ask hexedit', 'aria-label': 'Edit Contents' },
      h('div', { class: 'asktext' },
        h('h2', {}, 'Edit Contents'),
        h('p', { class: 'hexname' }, t.name, ' ', where),
        h('p', { class: 'hint' }, t.kind === 'ram'
          ? 'RAM의 값은 시뮬레이션 상태입니다. 파일에 저장되지 않고 Undo로 되돌리지 않습니다.'
          : t.editable ? 'ROM의 값은 파일에 저장됩니다. 바꿀 때마다 Undo로 되돌릴 수 있습니다.' : '이 ROM은 바꿀 수 없습니다(읽기 전용 파일이거나 불러온 라이브러리의 회로).'),
        h('div', { class: 'hexnav' }, prev, next, go),
        grid,
        why,
        h('div', { class: 'row end' }, close)));
    const editable = t.kind === 'ram' || t.editable;
    const base = { fileId: t.fileId, circuitId: t.root, path: t.path, componentId: t.componentId };

    const read = async (): Promise<void> => {
      try {
        const r = await host.call<MemWords>('mem.read', { ...base, from: page * PAGE, count: PAGE });
        info = r;
        draw();
      } catch (e) {
        why.textContent = (e as CallError).code === 4 ? '시뮬레이션이 아직 이 RAM에 닿지 않았습니다. Reset 뒤에 다시 여세요.' : '내용을 읽지 못했습니다.';
      }
    };

    const draw = (): void => {
      if (!info) return;
      const bits = info.dataBits;
      const lo = Math.min(caret, anchor), hi = Math.max(caret, anchor);
      const rows: HTMLElement[] = [h('div', { class: 'hexrow hexhead', role: 'row' }, h('span', { class: 'hexaddr' }, ''),
        ...Array.from({ length: COLS }, (_, i) => h('span', { class: 'hexcell', role: 'columnheader' }, `+${i}`)))];
      for (let r = 0; r * COLS < info.words.length; r++) {
        const addr = info.from + r * COLS;
        const cells = info.words.slice(r * COLS, r * COLS + COLS).map((w, i) => {
          const a = addr + i;
          const txt = a === caret && typed ? typed.padStart(digits(bits), ' ') : hexOf(w, bits);
          return h('span', {
            class: `hexcell mono${a === caret ? ' caret' : ''}${a >= lo && a <= hi && lo !== hi ? ' range' : ''}${a === caret && typed ? ' typing' : ''}`,
            role: 'gridcell', 'data-addr': String(a),
          }, txt);
        });
        rows.push(h('div', { class: 'hexrow', role: 'row' }, h('span', { class: 'hexaddr mono' }, hexOf(addr, info.addrBits)), ...cells));
      }
      grid.replaceChildren(...rows);
      where.textContent = `${info.addrBits}-bit address · ${info.dataBits}-bit data · ${hexOf(caret, info.addrBits)}`;
      prev.disabled = page === 0;
      next.disabled = (page + 1) * PAGE >= info.total;
    };

    const write = async (addr: number, values: number[]): Promise<boolean> => {
      if (!editable) return false;
      try {
        if (t.kind === 'ram') await host.call('mem.write', { ...base, addr, values });
        else await host.call('edit.memContents', { fileId: t.fileId, circuitId: t.circuitId, id: t.componentId, addr, values });
        why.textContent = '';
        return true;
      } catch (e) {
        const err = e as CallError;
        why.textContent = (err.data as { reason?: string } | undefined)?.reason === 'badValue'
          ? `그 값은 ${info?.dataBits ?? ''}비트에 들어가지 않습니다.` : '값을 쓰지 못했습니다.';
        return false;
      }
    };

    const apply = async (): Promise<void> => {
      if (!typed || !info) return;
      const v = parseInt(typed, 16);
      typed = '';
      await write(caret, [v]);
      await read();
    };

    const moveTo = async (a: number, extend: boolean): Promise<void> => {
      await apply();
      caret = a;
      if (!extend) anchor = a;
      const p = Math.floor(a / PAGE);
      if (p !== page) { page = p; await read(); } else draw();
      grid.querySelector('.caret')?.scrollIntoView({ block: 'nearest' });
    };

    grid.addEventListener('keydown', (e) => {
      e.stopPropagation();
      if (!info || e.isComposing) return;
      const m = caretMove(e.key, e.ctrlKey || e.metaKey, caret, info.total);
      if (m !== null) { e.preventDefault(); void moveTo(m, e.shiftKey); return; }
      if (e.key === 'Tab' || e.key === 'Enter') { e.preventDefault(); void moveTo(Math.min(info.total - 1, caret + 1), false); return; }
      if (e.key === 'Escape' && typed) { e.preventDefault(); typed = ''; draw(); return; }
      if (e.key === 'Backspace' && typed) { e.preventDefault(); typed = typed.slice(0, -1); draw(); return; }
      if ((e.ctrlKey || e.metaKey) && (e.key === 'c' || e.code === 'KeyC')) {
        e.preventDefault();
        const lo = Math.min(caret, anchor), hi = Math.max(caret, anchor);
        void host.call<MemWords>('mem.read', { ...base, from: lo, count: Math.min(4096, hi - lo + 1) })
          .then((r) => navigator.clipboard?.writeText(wordsText(r.words, r.dataBits))).catch(() => {});
        return;
      }
      if ((e.ctrlKey || e.metaKey) && (e.key === 'v' || e.code === 'KeyV')) {
        e.preventDefault();
        if (!editable) return;
        void navigator.clipboard?.readText().then(async (text) => {
          const words = parseWords(text, info!.dataBits);
          if (!words) { why.textContent = '붙여 넣을 16진수 값을 읽지 못했습니다.'; return; }
          const n = Math.min(words.length, info!.total - caret);
          await write(caret, words.slice(0, n));
          await read();
        }).catch(() => {});
        return;
      }
      if (/^[0-9a-fA-F]$/.test(e.key) && !e.ctrlKey && !e.metaKey && !e.altKey && editable) {
        e.preventDefault();
        const next2 = (typed + e.key.toLowerCase()).slice(-digits(info.dataBits));
        typed = next2;
        draw();
      }
    });
    grid.addEventListener('click', (e) => {
      const cell = (e.target as HTMLElement).closest('.hexcell[data-addr]') as HTMLElement | null;
      if (cell) void moveTo(Number(cell.dataset.addr), e.shiftKey);
      grid.focus();
    });
    prev.addEventListener('click', () => void moveTo(Math.max(0, caret - PAGE), false));
    next.addEventListener('click', () => void moveTo(Math.min((info?.total ?? 1) - 1, caret + PAGE), false));
    go.addEventListener('keydown', (e) => {
      e.stopPropagation();
      if (e.key !== 'Enter' || e.isComposing || !info) return;
      e.preventDefault();
      const v = /^(0x)?[0-9a-f]+$/i.test(go.value.trim()) ? parseInt(go.value.trim().replace(/^0x/i, ''), 16) : NaN;
      if (!Number.isFinite(v) || v >= info.total) { why.textContent = `주소는 0x0–0x${(info.total - 1).toString(16)} 사이의 16진수입니다.`; return; }
      void moveTo(v, false).then(() => grid.focus());
    });
    const timer = setInterval(() => { if (!typed) void read(); }, 500);
    close.addEventListener('click', () => dialog.close());
    dialog.addEventListener('close', () => {
      clearInterval(timer);
      void apply();
      dialog.remove();
      document.body.classList.remove('dialog-open');
      done();
    });
    document.body.append(dialog);
    document.body.classList.add('dialog-open');
    dialog.showModal();
    grid.focus();
    void read();
  });
}
