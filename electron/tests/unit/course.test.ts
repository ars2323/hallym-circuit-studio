/* The two courses (A-08, D-168: src/renderer/app/logic/course.ts): the one
   table of what each shows, the parts each lists (the real engine's
   library, tests/fixtures/library.json), the course a file chooses, the
   strip and Help › Examples. */

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { test } from 'node:test';

import type { LibraryGroup } from '../../src/main/protocol.ts';
import { EXAMPLE_COURSE, EXAMPLES } from '../../src/main/examples.ts';
import {
  COURSE_NAMES, COURSE_TABLE, COURSES, examplesFor, inferredCourse, MIPS_LIB, MIPS_NOTICE, MIPS_ONLY, mipsNoticeShown, partShown, shows, usesMipsOnly, visibleLibraries,
} from '../../src/renderer/app/logic/course.ts';

const library = JSON.parse(readFileSync(path.join(import.meta.dirname, '../fixtures/library.json'), 'utf8')) as { new: LibraryGroup[] };

test('the table: 컴퓨터구조 shows everything, 논리설계 및 실험 none of the MIPS features', () => {
  assert.deepEqual(COURSES, ['logic', 'architecture']);
  assert.deepEqual(COURSE_NAMES, { logic: '논리설계 및 실험', architecture: '컴퓨터구조' });
  const features = Object.keys(COURSE_TABLE) as (keyof typeof COURSE_TABLE)[];
  assert.deepEqual(features.sort(), ['cycleSide', 'fieldColors', 'loadProgram', 'markPc', 'markRegisterFile', 'programNotices', 'registerMapping', 'statusPc', 'statusProgram']);
  for (const f of features) {
    assert.equal(shows('architecture', f), true, f);
    assert.equal(shows('logic', f), false, f);
  }
});

test('the parts: 논리설계 lists Radix Probe of Hallym MIPS and every other library whole; 컴퓨터구조 the engine\'s list as it is', () => {
  const libs = library.new;
  const mips = libs.find((g) => g.lib === MIPS_LIB)!;
  // the real engine's list (D-140: Stack is not listed for placing)
  assert.deepEqual(mips.tools.map((t) => t.name), ['Instruction Memory', 'Data Memory', 'Console', 'Radix Probe']);
  assert.equal(visibleLibraries(libs, 'architecture'), libs);
  const logic = visibleLibraries(libs, 'logic');
  assert.deepEqual(logic.find((g) => g.lib === MIPS_LIB)!.tools.map((t) => t.name), ['Radix Probe']);
  assert.deepEqual(logic.map((g) => g.lib), libs.map((g) => g.lib));
  for (const g of logic) if (g.lib !== MIPS_LIB) assert.deepEqual(g, libs.find((x) => x.lib === g.lib));
  // the engine's answer is not changed
  assert.equal(mips.tools.length, 4);
  // exactly the MIPS-only parts, of the MIPS library only
  assert.deepEqual([...MIPS_ONLY], ['Instruction Memory', 'Data Memory', 'Console', 'Stack']);
  for (const name of MIPS_ONLY) assert.equal(partShown('logic', MIPS_LIB, name), false, name);
  assert.equal(partShown('logic', MIPS_LIB, 'Radix Probe'), true);
  assert.equal(partShown('logic', 'Memory', 'RAM'), true);
  assert.equal(partShown('logic', 'mylib', 'Console'), true);   // a .circ library's circuit named Console is the student's
  // a library of MIPS-only parts only: gone in 논리설계
  assert.deepEqual(visibleLibraries([{ lib: MIPS_LIB, display: 'Hallym MIPS', tools: [{ name: 'Console', display: 'Console' }] }], 'logic'), []);
});

test('the course a file chooses before one was: 컴퓨터구조 only for a MIPS-only part (Radix Probe alone is 논리설계)', () => {
  assert.equal(usesMipsOnly([{ lib: 'Gates', name: 'AND Gate' }, { lib: MIPS_LIB, name: 'Radix Probe' }]), false);
  for (const name of MIPS_ONLY) assert.equal(usesMipsOnly([{ lib: 'Gates', name: 'AND Gate' }, { lib: MIPS_LIB, name }]), true, name);
  assert.equal(usesMipsOnly([{ lib: null, name: 'Console' }]), false);   // a subcircuit of that name
  assert.equal(usesMipsOnly([]), false);
  assert.equal(inferredCourse(true), 'architecture');
  assert.equal(inferredCourse(false), 'logic');
});

test('the strip: in 논리설계 for a MIPS-only part, the fact then the action (the user\'s words, D-135 point 14: the particle goes with 부품)', () => {
  assert.equal(mipsNoticeShown('logic', true), true);
  assert.equal(mipsNoticeShown('logic', false), false);
  assert.equal(mipsNoticeShown('architecture', true), false);
  assert.equal(MIPS_NOTICE.text, '이 파일은 컴퓨터구조 부품(Hallym MIPS)을 씁니다');
  assert.equal(MIPS_NOTICE.action, '컴퓨터구조로 바꾸기');
});

test('Help › Examples by course: the logic circuits, the MIPS ones', () => {
  const list = EXAMPLES.map((n) => ({ id: n, name: n, course: EXAMPLE_COURSE[n] }));
  assert.deepEqual(examplesFor(list, 'logic').map((x) => x.id), ['adder-1bit.circ', 'ripple-carry-4bit.circ', 'counter-4bit.circ']);
  assert.deepEqual(examplesFor(list, 'architecture').map((x) => x.id), ['demo-datapath.circ', 'console-demo.circ', 'stack-demo.circ']);
});
