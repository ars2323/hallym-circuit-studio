/* The Memory panel's table (logic only; shared/memory.ts draws it the way
   Hallym MIPS's Data tab does).  The engine builds the rows from the
   circuit's Data Memory -- the data part from 0x10010000 with the image's
   labels, the stack part from the top down with $sp, runs of zero words as
   one row (D-140, docs/engine-api.md record.memory) -- and this file puts
   words to them: the four cells, their characters, the labels and
   pointers on the line, the section heads. */

import type { MemoryRow } from '../../../main/protocol.ts';
import type { MemoryLine, MemorySection } from '../../shared/memory.ts';
import { address, hex32 } from './values.ts';

export type MemoryView = MemorySection | MemoryLine;

const size = (bytes: number): string => (bytes < 1024 ? `${bytes} B` : `${(bytes / 1024).toLocaleString('en-US', { maximumFractionDigits: 1 })} KB`);
const offsetName = (n: number) => `+${n.toString(16).toUpperCase()}`;
const printable = (c: number) => (c >= 0x20 && c <= 0x7e ? String.fromCharCode(c) : '·');

// A word's four characters, lowest address first: the machine is little-endian (the image's endian line).
export function wordChars(word: string | null | undefined): string {
  if (!word || !/^[0-9a-f]{8}$/i.test(word)) return word === null || word === undefined ? '    ' : '····';
  const v = parseInt(word, 16) >>> 0;
  let s = '';
  for (let k = 0; k < 4; k += 1) s += printable((v >>> (8 * k)) & 0xff);
  return s;
}

export function memoryView(rows: MemoryRow[]): MemoryView[] {
  const parts = new Set(rows.map((r) => r.part));
  const out: MemoryView[] = [];
  for (const r of rows) {
    const from = address(r.addr) ?? 0;
    const end = (address(r.end) ?? from) + 1;
    if (r.kind === 'section') {
      const base = r.section === 'data' ? 'User data' : 'Stack';
      let facts = '';
      if (r.section === 'stack') {
        const d = r.depth ?? -1;
        facts = `${d >= 0 ? `$sp depth ${d.toLocaleString('en-US')} B` : '$sp outside the stack'} · peak ${(r.peak ?? 0).toLocaleString('en-US')} B`;
      }
      // A stack not used yet shows nothing below its top: its head names the top only.
      const empty = end - from <= 0;
      out.push({
        type: 'section', kind: r.section, title: parts.size > 1 ? `${base} · ${r.part}` : base,
        range: empty ? `– ${hex32(from - 1)}` : `${hex32(from)} – ${hex32(end - 1)}`, size: size(Math.max(0, end - from)), facts,
      });
      continue;
    }
    const pointed = Object.entries(r.pointers ?? {}).map(([name, a]) => ({ name, at: address(a) ?? -1 }));
    const run = r.kind === 'zeros';
    const where = (a: number) => (run ? hex32(a) : offsetName(a - from));
    const tags: MemoryLine['tags'] = [];
    for (const l of (r.labels ?? []).slice(0, 6)) {
      const a = address(l.addr) ?? from;
      tags.push({ kind: 'label', where: where(a), text: l.names.join(' ') });
    }
    for (const p of pointed) tags.push({ kind: 'pointer', where: where(p.at), text: `${p.name} → ${where(p.at)}` });
    if (run) {
      const words = r.count ?? Math.max(0, (end - from) / 4);
      out.push({
        type: 'zeros', kind: r.section, addr: from, end, cells: [], ascii: [], tags,
        zeroText: `~ ${hex32(end - 1)} · all 0 · ${words.toLocaleString('en-US')} words`,
      });
      continue;
    }
    const words = r.words ?? [null, null, null, null];
    const cells = words.map((w, i) => {
      const a = from + 4 * i;
      return {
        text: w ?? '', zero: w !== null && /^0{8}$/.test(w), none: w === null, addr: a,
        pointed: w !== null && pointed.some((p) => p.at >= a && p.at < a + 4),
      };
    });
    out.push({ type: 'words', kind: r.section, addr: from, end, cells, ascii: words.map(wordChars), tags, zeroText: '' });
  }
  return out;
}
