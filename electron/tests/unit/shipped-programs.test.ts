/* No SPIM code goes out with the program (CLAUDE.md 2.6, D-141, NOTICE): every
   file the package carries beside the app (examples.ts exampleResources: Help ›
   Examples and the tutorials' examples) is searched for SPIM's start-up code --
   the nine words exceptions.s's __start assembles to, which Hallym MIPS puts at
   0x00400000 when it assembles with its default exception handler.  The
   tutorial's image is assembled without the handler (tests/tutorial/README.md),
   so it holds only its own program's words.

   Every run of hex digits in a file is read as a number, in order ("n*v" of a
   Logisim memory's contents as n copies of v), and the nine must not appear
   one after another.  A test file (Hallym MIPS v2.4.0's golden data.hmx) shows
   the search finds them where they are. */

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { test } from 'node:test';

import { exampleResources } from '../../src/main/examples.ts';

const repo = path.join(import.meta.dirname, '../../..');
const START_UP = [0x8fa40000, 0x27a50004, 0x24a60004, 0x00041080, 0x00c23021, 0x0c100009, 0x00000000, 0x3402000a, 0x0000000c];

function numbers(text: string): number[] {
  const out: number[] = [];
  for (const m of text.matchAll(/\b(?:(\d+)\*)?([0-9a-fA-F]+)\b/g)) {
    const v = Number.parseInt(m[2], 16) >>> 0;
    for (let i = 0; i < Number(m[1] ?? 1) && i < 64; i += 1) out.push(v);
  }
  return out;
}
const hasStartUp = (text: string): boolean => {
  const n = numbers(text);
  for (let i = 0; i + START_UP.length <= n.length; i += 1) if (START_UP.every((w, j) => n[i + j] === w)) return true;
  return false;
};

test('the search finds SPIM\'s start-up code where it is (an image assembled with the handler; a memory\'s contents)', () => {
  assert.equal(hasStartUp(readFileSync(path.join(repo, 'tests/hmx/hallym-mips-v2.4.0/data.hmx'), 'utf8')), true);
  assert.equal(hasStartUp('addr/data: 24 32\n8fa40000 27a50004 24a60004 41080 c23021 c100009 0 3402000a c\n'), true);
  assert.equal(hasStartUp('8fa40000 27a50004 24a60004 41080 c23021 c100009 2*0 3402000a c'), false, 'nine in a row, no more');
  assert.equal(hasStartUp(readFileSync(path.join(repo, 'tests/hmx/hallym-mips-v2.4.0/no-handler.hmx'), 'utf8')), false);
});

test('no file the package carries holds SPIM\'s start-up code (the examples, the tutorials\' examples and program)', () => {
  const shipped = exampleResources(repo);
  assert.ok(shipped.some((r) => r.to === 'tutorial/tutorial.hmx'), 'the tutorial\'s image is among them');
  for (const r of shipped) assert.equal(hasStartUp(readFileSync(r.from, 'utf8')), false, r.to);
});

test('the tutorial\'s image: Hallym MIPS\'s export without the exception handler -- entry = main at the first word', () => {
  const hmx = readFileSync(path.join(repo, 'tests/tutorial/tutorial.hmx'), 'utf8');
  assert.match(hmx, /^HALLYM-EXEC 1\n/);
  assert.match(hmx, /^produced-by +Hallym MIPS \d+\.\d+\.\d+$/m);
  assert.match(hmx, /^entry +0x00400000$/m);
  assert.match(hmx, /^symbol main +0x00400000$/m);
  assert.match(readFileSync(path.join(repo, 'tests/tutorial/tutorial.s'), 'utf8'), /^# assemble: no exception handler\n/);
});
