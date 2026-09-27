/* A few DOM helpers (derived from Hallym MIPS v2.3.0
   electron/src/renderer/app/dom.ts).  The rule they keep: anything that may
   be a hexadecimal literal or a name with the digit 0 is set in the mono
   font (Pretendard draws 0x1 as 0×1), so text with code in it goes through
   code() or codeText().

   The university's characters and marks are this repository's originals
   (assets/hallym/, the same files Hallym MIPS uses, byte for byte), shown
   as they are: scaled by size only, never under 76 px (the guideline's
   20 mm). */

type Child = Node | string | null | undefined | false;

export function h<K extends keyof HTMLElementTagNameMap>(tag: K, attrs: Record<string, string | boolean | undefined> = {},
                                                         ...children: Child[]): HTMLElementTagNameMap[K] {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (v === undefined || v === false) continue;
    if (k === 'class') el.className = String(v);
    else el.setAttribute(k, v === true ? '' : v);
  }
  for (const c of children) if (c !== null && c !== undefined && c !== false) el.append(c);
  return el;
}

// One piece of code: a circuit's name, a label, a value.
export const code = (text: string, cls = ''): HTMLSpanElement => h('span', { class: `mono ${cls}`.trim() }, text);

// `code` parts of a sentence (between backquotes).
export function codeParts(text: string): { text: string; code: boolean }[] {
  return text.split('`').map((t, i) => ({ text: t, code: i % 2 === 1 })).filter((p) => p.text !== '');
}

// Prose with `code` parts.  A word joiner holds a parenthesis to the word it
// belongs to -- 값(`0x…`)을 is one word, and Chromium would break before the
// "(" or after the ")" even with keep-all.
export function codeText(text: string): DocumentFragment {
  const f = document.createDocumentFragment();
  for (const p of codeParts(text)) f.append(p.code ? code(p.text) : document.createTextNode(p.text.replace(/([가-힣])\(/g, '$1⁠(').replace(/\)([가-힣])/g, ')⁠$1')));
  return f;
}

// The page's own files (fonts, icons), next to it.
export const asset = (p: string): string => `../assets/${p}`;
// The university's marks and characters: where tools/build-ui.ts says they
// are (the repository's assets/hallym/ in the source tree).
declare const __HALLYM__: string;
export const hallym = (p: string): string => `${__HALLYM__}/${p}`;
export const icon = (name: string): HTMLImageElement => h('img', { class: 'icon', src: asset(`icons/lucide/${name}.svg`), alt: '' });
// Hallym characters: the original PNGs, scaled by CSS only, never under 76 px.
export const character = (name: string, height: number): HTMLImageElement =>
  h('img', { class: 'char', src: hallym(`character/${name}.png`), alt: '', style: `height:${Math.max(76, height)}px` });
