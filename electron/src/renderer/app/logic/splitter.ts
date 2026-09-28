/* The Splitter editor's model (v1 SplitterSpec and SplitterEditor.toggle,
   D-032, B-14, I-189; D-150): the combined end's width and the arms, top
   arm first, each with its bits (highest first) and a name.  What it writes
   is always the original's own attributes (fanout, incoming, bitN); the
   names go to hcs:ext (the engine does both: edit.splitterEdit).

   The ranges text reads as v1's did, so what the student sees here is what
   the engine will do: "31:26, 25:21, 20:16, 15:0" (a name may follow a
   range: "31:26 op"), "4x8" (four arms of 8 bits), "32x1".  The engine's
   own parser is the authority (the same inputs: tests/fixtures/
   splitter-ranges.json, made by the engine, checks this one). */

export interface Arm { bits: number[]; name: string }   // bits highest first
export interface Spec { width: number; arms: Arm[] }

export type ParseError =
  | { kind: 'empty' }
  | { kind: 'repeat'; text: string }         // "33x1": more than 32 bits, or a zero
  | { kind: 'unreadable'; text: string }     // a part that is not "7", "7:4" or "7:4 name"
  | { kind: 'outside'; bit: number; width: number }
  | { kind: 'twice'; bit: number }
  | { kind: 'noArms' }
  | { kind: 'width' };                       // the combined end would be wider than 32 bits

export type Parsed = { ok: true; spec: Spec } | { ok: false; error: ParseError };

function arm(bits: number[], name = ''): Arm {
  return { bits: [...new Set(bits)].sort((a, b) => b - a), name: name.trim() };
}
const msb = (a: Arm) => (a.bits.length ? a.bits[0] : -1);

const REPEAT = /^\s*(\d+)\s*[xX×]\s*(\d+)\s*$/;
const PART = /^\s*(\d+)\s*(?::\s*(\d+))?\s*([A-Za-z_][A-Za-z0-9_.]*)?\s*$/;

// Java's String.split(","): trailing empty pieces are dropped.
function javaSplit(t: string): string[] {
  const parts = t.split(',');
  while (parts.length > 1 && parts[parts.length - 1] === '') parts.pop();
  return parts;
}

export function parse(text: string, width: number, msbOnTop: boolean): Parsed {
  const t = (text ?? '').trim();
  if (!t) return { ok: false, error: { kind: 'empty' } };
  let arms: Arm[] = [];
  const rep = REPEAT.exec(t);
  if (rep) {
    const n = Number(rep[1]), each = Number(rep[2]);
    if (n <= 0 || each <= 0 || n * each > 32) return { ok: false, error: { kind: 'repeat', text: t } };
    for (let i = 0; i < n; i++) arms.push(arm(Array.from({ length: each }, (_, k) => i * each + k)));
    if (width <= 0) width = n * each;
  } else {
    for (const part of javaSplit(t)) {
      const m = PART.exec(part);
      if (!m) return { ok: false, error: { kind: 'unreadable', text: part.trim() } };
      const a = Number(m[1]), b = m[2] === undefined ? a : Number(m[2]);
      const bits: number[] = [];
      for (let i = Math.min(a, b); i <= Math.max(a, b); i++) bits.push(i);
      arms.push(arm(bits, m[3] ?? ''));
    }
    if (width <= 0) width = Math.max(0, ...arms.map(msb)) + 1;
  }
  const spec = ordered({ width, arms }, msbOnTop);
  const err = problem(spec);
  return err ? { ok: false, error: err } : { ok: true, spec };
}

// The arms in the chosen order: the top arm the highest bits (MSB on top) or the lowest.
export function ordered(s: Spec, msbOnTop: boolean): Spec {
  return { width: s.width, arms: [...s.arms].sort((p, q) => (msbOnTop ? msb(q) - msb(p) : msb(p) - msb(q))) };
}

function problem(s: Spec): ParseError | null {
  const used = new Array<boolean>(Math.max(s.width, 0)).fill(false);
  for (const a of s.arms) {
    for (const b of a.bits) {
      if (b < 0 || b >= s.width) return { kind: 'outside', bit: b, width: s.width };
      if (used[b]) return { kind: 'twice', bit: b };
      used[b] = true;
    }
  }
  if (!s.arms.length) return { kind: 'noArms' };
  if (s.width < 1 || s.width > 32) return { kind: 'width' };
  return null;
}

// Bits (highest first) as ranges: 31,30,29 → 31:29; 7,3,2,1,0 → 7,3:0.
export function ranges(bits: number[]): string {
  const out: string[] = [];
  for (let i = 0; i < bits.length; i++) {
    const start = bits[i];
    let end = start;
    while (i + 1 < bits.length && bits[i + 1] === end - 1) end = bits[++i];
    out.push(start === end ? `${start}` : `${start}:${end}`);
  }
  return out.join(',');
}

export const armRange = (a: Arm): string => `[${ranges(a.bits)}]`;
// The chip on the Canvas: "[31:26] op".
export const armLabel = (a: Arm): string => (a.name ? `${armRange(a)} ${a.name}` : armRange(a));

// The text for the ranges box again: "31:26 op, 25:21 rs".
export function toText(s: Spec): string {
  return s.arms.map((a) => (a.name ? `${ranges(a.bits)} ${a.name}` : ranges(a.bits))).join(', ');
}

// Bits that go to no arm (highest first).
export function unassigned(s: Spec): number[] {
  const of = armOfBit(s);
  const out: number[] = [];
  for (let b = s.width - 1; b >= 0; b--) if (of[b] < 0) out.push(b);
  return out;
}

// Each bit's arm, or -1.
export function armOfBit(s: Spec): number[] {
  const of = new Array<number>(Math.max(0, s.width)).fill(-1);
  s.arms.forEach((a, i) => { for (const b of a.bits) if (b >= 0 && b < s.width) of[b] = i; });
  return of;
}

// The original's attributes, in the order that keeps them (fanout and incoming reset the bitN).
export function toStandardAttrs(s: Spec): Record<string, string> {
  const m: Record<string, string> = { fanout: String(s.arms.length), incoming: String(s.width) };
  const of = armOfBit(s);
  for (let b = 0; b < s.width; b++) m[`bit${b}`] = of[b] < 0 ? 'none' : String(of[b]);
  return m;
}

// A splitter as the engine sends it (its attributes, ext.arms) as a spec.
export function fromAttrs(attrs: Record<string, string>, names: readonly string[] = []): Spec {
  const fanout = Number(attrs.fanout ?? 2), width = Number(attrs.incoming ?? 2);
  const bits: number[][] = Array.from({ length: fanout }, () => []);
  for (let b = 0; b < width; b++) {
    const v = attrs[`bit${b}`];
    const a = v === undefined ? Math.min(fanout - 1, Math.floor((b * fanout) / width)) : v === 'none' ? -1 : Number(v);
    if (a >= 0 && a < fanout) bits[a].push(b);
  }
  return { width, arms: bits.map((b, i) => arm(b, names[i] ?? '')) };
}

export function withNames(s: Spec, names: readonly string[]): Spec {
  return { width: s.width, arms: s.arms.map((a, i) => arm(a.bits, names[i] ?? '')) };
}

// Whether the top arm holds the high bits (so the editor opens with the direction the splitter has).
export function msbOnTop(s: Spec): boolean {
  for (let i = 0; i + 1 < s.arms.length; i++) if (msb(s.arms[i]) !== msb(s.arms[i + 1])) return msb(s.arms[i]) > msb(s.arms[i + 1]);
  return true;
}

/* The line between two cells of the bit strip: `bitLeft` and the bit right of
   it (one lower).  In one arm: split it there; in two arms: join them.  With
   a bit of no arm on either side nothing happens (the ranges box does that). */
export function toggle(s: Spec, bitLeft: number): Spec {
  const bitRight = bitLeft - 1;
  if (bitRight < 0 || bitLeft >= s.width) return s;
  const of = armOfBit(s);
  const a = of[bitLeft], b = of[bitRight];
  if (a < 0 || b < 0) return s;
  const arms: Arm[] = [];
  s.arms.forEach((x, i) => {
    if (a === b && i === a) {
      arms.push(arm(x.bits.filter((bit) => bit >= bitLeft), x.name));
      arms.push(arm(x.bits.filter((bit) => bit < bitLeft)));
    } else if (a !== b && i === a) {
      arms.push(arm([...x.bits, ...s.arms[b].bits], x.name));
    } else if (!(a !== b && i === b)) {
      arms.push(x);
    }
  });
  return { width: s.width, arms };
}

// The presets (32 bits only), as v1 wrote them: MSB on top.
export const PRESETS: readonly { id: string; name: string; text: string }[] = [
  { id: 'MIPS_R', name: 'MIPS R-type: op rs rt rd shamt funct', text: '31:26 op, 25:21 rs, 20:16 rt, 15:11 rd, 10:6 shamt, 5:0 funct' },
  { id: 'MIPS_I', name: 'MIPS I-type: op rs rt imm', text: '31:26 op, 25:21 rs, 20:16 rt, 15:0 imm' },
  { id: 'MIPS_J', name: 'MIPS J-type: op addr', text: '31:26 op, 25:0 addr' },
  { id: 'BYTES', name: 'Four bytes', text: '31:24 b3, 23:16 b2, 15:8 b1, 7:0 b0' },
  { id: 'HALVES', name: 'Upper and lower 16 bits', text: '31:16 hi, 15:0 lo' },
  { id: 'SIGN', name: 'Sign bit and the rest', text: '31 sign, 30:0 rest' },
];

// What a new splitter on a wire starts as (v1 createNew): 32 bits the R-type fields, else two halves.
export function initialSplit(width: number): Spec {
  const text = width === 32 ? PRESETS[0].text : `${width - 1}:${Math.floor(width / 2)}, ${Math.floor(width / 2) - 1}:0`;
  const r = parse(text, width, true);
  return r.ok ? r.spec : { width, arms: [arm(Array.from({ length: width }, (_, i) => i))] };
}

/* Korean words for a parse error (a sentence to the student; the bit
   numbers and the text in `backquotes`: the mono font, dom.ts codeText). */
export function errorText(e: ParseError): string {
  switch (e.kind) {
    case 'empty': return '범위를 칩니다. 예: `31:26, 25:21, 20:16, 15:0` 또는 `4x8`';
    case 'repeat': return `반복 표기를 쓸 수 없습니다: \`${e.text}\`. 팔 수와 팔의 폭은 1 이상이고 모두 합해 32비트까지입니다.`;
    case 'unreadable': return `읽을 수 없는 범위입니다: \`${e.text}\`. 예: \`31:26\`, \`7\`, \`31:26 op\`, \`4x8\``;
    case 'outside': return `묶인 쪽이 ${e.width}비트라 비트 번호는 0부터 ${e.width - 1}까지입니다: \`${e.bit}\``;
    case 'twice': return `한 비트를 두 팔에 둘 수 없습니다: \`${e.bit}\``;
    case 'noArms': return '팔이 하나도 없습니다.';
    case 'width': return '묶인 쪽 폭은 1비트에서 32비트까지입니다.';
  }
}
