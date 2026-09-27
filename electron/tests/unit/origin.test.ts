/* What is taken from Hallym MIPS (tools/import-hmips.ts, ORIGIN.md): every
   file is here and written down, every copy is byte for byte, nothing that
   comes from SPIM is here (D-133 point 5), and the screen never says 한림
   (D-130: "Hallym University" on screen). */

import assert from 'node:assert/strict';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import path from 'node:path';
import { test } from 'node:test';

import { check, LUCIDE_EXTRA, lucideIcon, NEVER, root, TAKEN } from '../../tools/import-hmips.ts';

const files = (dir: string): string[] => readdirSync(dir).flatMap((n) => {
  const p = path.join(dir, n);
  return statSync(p).isDirectory() ? files(p) : [p];
});
const ours = () => ['src', 'tests', 'tools'].flatMap((d) => files(path.join(root, d)))
  .filter((f) => /\.(ts|cjs|js|css|html)$/.test(f));

test('every file taken is here; every copy is byte for byte the tag\'s', () => {
  assert.deepEqual(check(), []);
});

test('ORIGIN.md names every file taken, where it came from, and every Lucide icon added', () => {
  const origin = readFileSync(path.join(root, 'ORIGIN.md'), 'utf8');
  for (const t of TAKEN) {
    if (t.to.includes('/icons/lucide/') && t.to.endsWith('.svg')) {
      assert.ok(origin.includes(path.basename(t.to, '.svg')), t.to);
      continue;
    }
    assert.ok(origin.includes(`\`${t.to}\``), `ORIGIN.md does not name ${t.to}`);
    assert.ok(origin.includes(`\`${t.from}\``), `ORIGIN.md does not name ${t.from}`);
  }
  for (const n of LUCIDE_EXTRA) assert.ok(origin.includes(`\`${n}\``), n);
  assert.match(origin, /SPIM 유래가 아님/);
});

test('nothing taken is SPIM\'s or leans on it', () => {
  for (const t of TAKEN) for (const n of NEVER) assert.ok(!t.from.includes(n), `${t.from} (${n})`);
});

// D-133 point 5, file by file: every text file taken from Hallym MIPS, read
// here one at a time -- no import of its src/core, src/sim or native/, and
// none of SPIM's names or strings (its op table and instruction classes, its
// addon, its core's folder, its name).  The fonts and icons are binary or
// drawings; the copies among them are checked byte for byte above.
const SPIM_IMPORT = /(?:from|import)\s*\(?\s*['"][^'"]*\/(?:core|sim|native)\//;
const SPIM_WORDS = /op-table|OP_TABLE|R3_TYPE_INST|I2_TYPE_INST|spim\.node|\bspim\b|SPIM|QtSpim|\bCPU\/|syscall|explain\.ts|decoder\.ts|instruction-text|asm-errors|mips-syntax/;
for (const t of TAKEN.filter((x) => /\.(ts|cjs|css|html|json|md|nsh)$/.test(x.to) || x.to === '.gitignore' || x.to === '.gitattributes')) {
  test(`taken, not from SPIM: ${t.to}`, () => {
    const lines = readFileSync(path.join(root, t.to), 'utf8').split('\n');
    const bad = lines.map((line, i) => [i + 1, line] as const)
      .filter(([, line]) => SPIM_IMPORT.test(line) || SPIM_WORDS.test(line))
      // (tools/mutants.ts names this app's own TextDecoder field; the list of what is never taken names what it never takes)
      .filter(([, line]) => !(t.to === 'tools/mutants.ts' && /this\.decoder\.decode/.test(line)));
    assert.deepEqual(bad.map(([i, line]) => `${i}: ${line.trim()}`), []);
  });
}

test('no source, test or tool here refers to SPIM\'s tables, its core or its addon', () => {
  const bad = /op-table|OP_TABLE|R3_TYPE_INST|spim\.node|vendor\/spim|\bCPU\/|from '[^']*\/(core|sim|native)\//;
  const hits = ours().filter((f) => !f.endsWith(path.join('tools', 'import-hmips.ts')) && !f.endsWith(path.join('unit', 'origin.test.ts')))
    .flatMap((f) => readFileSync(f, 'utf8').split('\n').map((line, i) => [f, i + 1, line] as const))
    .filter(([, , line]) => bad.test(line)).map(([f, i, line]) => `${path.relative(root, f)}:${i}: ${line.trim()}`);
  assert.deepEqual(hits, []);
});

test('the screen says Hallym University, never 한림', () => {
  const hits = files(path.join(root, 'src')).filter((f) => /\.(ts|cjs|css|html)$/.test(f))
    .filter((f) => readFileSync(f, 'utf8').includes('한림')).map((f) => path.relative(root, f));
  assert.deepEqual(hits, []);
});

test('an added Lucide icon is lucide-static\'s drawing without its license comment and class', () => {
  const upstream = '<!-- @license lucide-static v1.48.0 - ISC -->\n<svg\n  class="lucide lucide-x"\n  xmlns="http://www.w3.org/2000/svg"\n  width="24"\n>\n  <path d="M18 6 6 18" />\n</svg>\n';
  assert.equal(lucideIcon(upstream), '<svg\n  xmlns="http://www.w3.org/2000/svg"\n  width="24"\n>\n  <path d="M18 6 6 18" />\n</svg>\n');
  for (const n of LUCIDE_EXTRA) {
    const svg = readFileSync(path.join(root, 'src/renderer/assets/icons/lucide', `${n}.svg`), 'utf8');
    assert.ok(svg.startsWith('<svg\n') && !svg.includes('class=') && !svg.includes('@license'), n);
  }
});
