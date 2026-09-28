/* The Splitter editor (v1 SplitterEditor, D-032, B-14, I-189; D-150): the
   window's own modal dialog, opened for a splitter (Edit Splitter…) or for a
   new splitter on a multi-bit wire (Split Bits…).

     Ranges     "31:26, 25:21, 20:16, 15:0", "31:26 op, …", "4x8", read as
                typed (logic/splitter.ts, the engine's reading)
     Preset     32 bits only: MIPS R-, I-, J-type, four bytes, halves, sign
     Top arm    MSB on top / LSB on top
     the strip  a cell a bit (the highest on the left), each coloured by its
                arm and numbered too; a click on the line between two cells
                splits an arm there or joins the two arms
     the arms   "Arm 0 [31:26]", its name (saved by this tool only, hcs:ext),
                its width
     under them what is left over (bits of no arm) and arms whose wire is
                another width -- facts, the Apply is still there

   Enter is Apply; Esc and Cancel change nothing.  Apply gives the ranges,
   the names and the direction; the engine applies them in one undo step. */

import { code, codeText, h } from '../shared/dom.ts';
import { type Arm, armOfBit, armRange, errorText, msbOnTop, parse, PRESETS, type Spec, toggle, toText, unassigned, withNames } from './logic/splitter.ts';

export interface SplitterAnswer { ranges: string; names: string[]; lsbTop: boolean; spec: Spec }

export interface SplitterQuestion {
  title: string;                // Edit Splitter, Split Bits
  spec: Spec;                   // what it is now (or starts as)
  wired?: (number | null)[];    // each arm's wire width now (Edit Splitter), null: nothing wired
}

// Arm colours (v1 ARM_COLORS: pale, told apart by the arm's number too).
export const ARM_COLORS = ['#dce9f7', '#fbe3c8', '#d9f0e3', '#f3d9e8', '#e6e0f5', '#f7f0c6', '#d5eef0', '#eadfd3'];
export const CELL = 16;

export function splitterEditor(q: SplitterQuestion): Promise<SplitterAnswer | null> {
  return new Promise((answer) => {
    const width = q.spec.width;
    let spec: Spec | null = q.spec;
    let names = q.spec.arms.map((a) => a.name);
    const ranges = h('input', { type: 'text', class: 'sranges mono', 'aria-label': 'Ranges', spellcheck: 'false', autocomplete: 'off' });
    const preset = h('select', { class: 'spreset', 'aria-label': 'Preset', disabled: width !== 32, title: width !== 32 ? '프리셋은 32비트 스플리터에만 있습니다.' : undefined },
      h('option', { value: '' }, '(choose)'), ...PRESETS.map((p) => h('option', { value: p.id }, p.name)));
    const msb = h('input', { type: 'radio', name: 'topArm', value: 'msb', checked: msbOnTop(q.spec) });
    const lsb = h('input', { type: 'radio', name: 'topArm', value: 'lsb', checked: !msbOnTop(q.spec) });
    const strip = h('div', { class: 'sstrip', role: 'group', 'aria-label': 'Bits', title: '두 칸 사이 선을 누르면 팔을 나누거나 합칩니다.' });
    const arms = h('div', { class: 'sarms' });
    const problems = h('div', { class: 'sproblems', role: 'status' });
    const apply = h('button', { class: 'btn primary', type: 'button' }, 'Apply');
    const cancel = h('button', { class: 'btn', type: 'button' }, 'Cancel');
    const dialog = h('dialog', { class: 'modal splitter-editor', 'aria-label': q.title },
      h('h2', {}, q.title, h('span', { class: 'swidth' }, ` · ${width} ${width === 1 ? 'bit' : 'bits'}`)),
      h('div', { class: 'sform' },
        h('label', { class: 'slabel' }, 'Ranges'), ranges,
        h('label', { class: 'slabel' }, 'Preset'), preset,
        h('span', { class: 'slabel' }, 'Top arm'),
        h('span', { class: 'sdir' }, h('label', {}, msb, 'MSB on top'), h('label', {}, lsb, 'LSB on top'))),
      strip, arms, problems,
      h('div', { class: 'row end' }, cancel, apply));

    const top = () => msb.checked;

    // What is typed, read again (a new text: the names typed so far stay where the arms are the same many).
    function reread(): void {
      const r = parse(ranges.value, width, top());
      if (!r.ok) { spec = null; show(); return; }
      const keep = r.spec.arms.length === names.length;
      names = r.spec.arms.map((a, i) => a.name || (keep ? names[i] ?? '' : ''));
      spec = withNames(r.spec, names);
      show();
    }

    function setSpec(s: Spec): void {
      spec = s;
      names = s.arms.map((a) => a.name);
      ranges.value = toText(s);
      show();
    }

    function show(): void {
      drawStrip();
      arms.replaceChildren(...(spec?.arms ?? []).map((a: Arm, i) => {
        const input = h('input', { type: 'text', class: 'sname', value: names[i] ?? '', 'aria-label': `Arm ${i} name`, title: 'Arm Name (saved only by Hallym Circuit Studio)', spellcheck: 'false' });
        input.addEventListener('input', () => { names[i] = input.value; spec = spec && withNames(spec, names); });
        return h('div', { class: 'sarm' },
          h('span', { class: 'sarmtag', style: `--c:${ARM_COLORS[i % ARM_COLORS.length]}` }, `Arm ${i}`, ' ', code(armRange(a))),
          input,
          h('span', { class: 'sbits' }, `${a.bits.length} ${a.bits.length === 1 ? 'bit' : 'bits'}`));
      }));
      const lines: string[] = [];
      let kind = 'warn';          // facts about the arms; a text that cannot be read: an error
      if (!spec) {
        const r = parse(ranges.value, width, top());
        if (!r.ok) { lines.push(errorText(r.error)); kind = 'err'; }
      } else {
        const left = unassigned(spec);
        if (left.length) lines.push(`어느 팔에도 가지 않는 비트가 있습니다: \`${left.length === 1 ? left[0] : rangesOf(left)}\``);
        spec.arms.forEach((a, i) => {
          const w = q.wired?.[i];
          if (w && w !== a.bits.length) lines.push(`이어진 선과 폭이 다릅니다: Arm ${i} \`${armRange(a)}\` ${a.bits.length} ${a.bits.length === 1 ? 'bit' : 'bits'} · 선 ${w} ${w === 1 ? 'bit' : 'bits'}`);
        });
      }
      problems.replaceChildren(...lines.map((l) => h('p', { class: kind }, codeText(l))));
      apply.disabled = spec === null;
    }

    function rangesOf(bits: number[]): string {
      const out: string[] = [];
      for (let i = 0; i < bits.length; i++) {
        const s = bits[i];
        let e = s;
        while (i + 1 < bits.length && bits[i + 1] === e - 1) e = bits[++i];
        out.push(s === e ? `${s}` : `${s}:${e}`);
      }
      return out.join(',');
    }

    function drawStrip(): void {
      const of = spec ? armOfBit(spec) : new Array<number>(width).fill(-1);
      strip.style.setProperty('--cells', String(width));
      strip.replaceChildren(...Array.from({ length: width }, (_, i) => {
        const bit = width - 1 - i;
        const a = of[bit];
        const edge = i > 0 && of[bit] !== of[bit + 1];
        return h('span', { class: `scell${edge ? ' edge' : ''}${a < 0 ? ' none' : ''}`, style: a >= 0 ? `--c:${ARM_COLORS[a % ARM_COLORS.length]}` : undefined, 'data-bit': String(bit) },
          h('span', { class: 'sbit' }, String(bit)), h('span', { class: 'sarmno' }, a < 0 ? '–' : String(a)));
      }));
    }

    strip.addEventListener('click', (e) => {
      if (!spec) return;
      const r = strip.getBoundingClientRect();
      const x = e.clientX - r.left - 1;   // the strip's border
      const boundary = Math.round(x / CELL);
      if (boundary > 0 && boundary < width && Math.abs(x - boundary * CELL) <= 4) {
        const next = toggle(spec, width - boundary);
        if (next !== spec) setSpec(withNames({ width: next.width, arms: next.arms }, next.arms.map((a) => a.name)));
      }
    });
    ranges.addEventListener('input', () => { preset.value = ''; reread(); });
    for (const r of [msb, lsb]) r.addEventListener('change', () => reread());
    preset.addEventListener('change', () => {
      const p = PRESETS.find((x) => x.id === preset.value);
      if (!p) return;
      const r = parse(p.text, width, top());
      if (r.ok) setSpec(r.spec);
    });

    let result: SplitterAnswer | null = null;
    apply.addEventListener('click', () => {
      if (!spec) return;
      // the ranges without the names (a name may hold any letter; the names go on their own)
      result = { ranges: toText(withNames(spec, [])), names: spec.arms.map((_, i) => (names[i] ?? '').trim()), lsbTop: !top(), spec };
      dialog.close();
    });
    cancel.addEventListener('click', () => dialog.close());
    dialog.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' && !e.isComposing && (e.target as HTMLElement).tagName !== 'BUTTON' && (e.target as HTMLElement).tagName !== 'SELECT') {
        e.preventDefault();
        apply.click();
      }
    });
    dialog.addEventListener('close', () => {
      dialog.remove();
      document.body.classList.remove('dialog-open');
      answer(result);
    });
    document.body.append(dialog);
    document.body.classList.add('dialog-open');
    ranges.value = toText(q.spec);
    show();
    dialog.showModal();
    ranges.focus();
    ranges.select();
  });
}
