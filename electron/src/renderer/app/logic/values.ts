/* A value as the engine sends it -- the protocol's letters, high bit first:
   '0' '1', 'x' (floating, not defined), 'E' (error) (docs/engine-api.md 4)
   -- shown in the bases the panels use (logic only).  A value is never
   judged here, only written out. */

export type Bits = string;

// Every bit is 0 or 1.
export const defined = (v: Bits | null | undefined): v is Bits => typeof v === 'string' && v.length > 0 && /^[01]+$/.test(v);

// The value as an unsigned number (up to 32 bits), or null.
export function unsigned(v: Bits | null | undefined): number | null {
  if (!defined(v) || v.length > 32) return null;
  return parseInt(v, 2) >>> 0;
}

// The value as a signed number of its width, or null.
export function signed(v: Bits | null | undefined): number | null {
  const u = unsigned(v);
  if (u === null || v === null || v === undefined) return null;
  const w = v.length;
  if (w === 32) return u | 0;
  return u >= 2 ** (w - 1) ? u - 2 ** w : u;
}

// "0x0000002a": one hex digit per four bits from the low end, as many as the
// width needs; a digit with an E bit is 'E', one with an x bit 'x' (the
// original's hex notation for a partly defined bus).
export function hex(v: Bits | null | undefined, prefix = true): string {
  if (v === null || v === undefined || v === '') return '';
  let out = '';
  for (let end = v.length; end > 0; end -= 4) {
    const nibble = v.slice(Math.max(0, end - 4), end);
    out = (nibble.includes('E') ? 'E' : nibble.includes('x') ? 'x' : parseInt(nibble, 2).toString(16)) + out;
  }
  return prefix ? `0x${out}` : out;
}

// Decimal, signed (the Registers panel's Dec, as Hallym MIPS's); '' when not defined.
export function dec(v: Bits | null | undefined): string {
  const n = signed(v);
  return n === null ? '' : String(n);
}

// Four bits to a group, high group first: "0000 0000 0010 1010".
export function binGroups(v: Bits | null | undefined): string[] {
  if (v === null || v === undefined || v === '') return [];
  const out: string[] = [];
  for (let end = v.length; end > 0; end -= 4) out.unshift(v.slice(Math.max(0, end - 4), end));
  return out;
}

// "0x0040002c" -> 0x0040002c; null for anything else.
export function address(text: string | null | undefined): number | null {
  if (!text || !/^0x[0-9a-f]{1,8}$/i.test(text)) return null;
  return parseInt(text.slice(2), 16) >>> 0;
}

// "0x10010000": eight digits, the Data tab's way.
export const hex32 = (n: number): string => `0x${(n >>> 0).toString(16).padStart(8, '0')}`;
