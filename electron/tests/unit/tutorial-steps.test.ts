/* The two courses' steps (src/renderer/app/tutorial/, N-18, D-161; v2 brief
   6-1..6-3): sixteen and fourteen, in the brief's order and kinds; a
   practice step waits for the student (done and skip), says in its last
   sentence what happens once it is done, and has a result beat exactly
   where the brief has one; the words keep the screen's rules (D-135 14:
   no particle right after a name, no "하면 됩니다", no 한림); keys named as
   the engine of the tutorial names them. */

import assert from 'node:assert/strict';
import { test } from 'node:test';

import { keyName, type Step } from '../../src/renderer/shared/tutorial.ts';
import { LOGIC_STEPS } from '../../src/renderer/app/tutorial/logic-steps.ts';
import { MIPS_STEPS } from '../../src/renderer/app/tutorial/mips-steps.ts';
import type { CourseTutorial } from '../../src/renderer/app/tutorial/host.ts';

type S = Step<CourseTutorial>;
// A stand-in for the tutorial: the words never read the window.
const t = {} as CourseTutorial;

const PARTICLE_AFTER_NAME = /[A-Za-z0-9_)\]`'"][을를이가은는의에과와로도만]/;
const words = (s: S): string[] => [s.title(t), s.body(t), ...(s.result ? [s.result.title(t), s.result.body(t)] : [])];
const lastSentence = (text: string): string => text.trim().split(/(?<=[.!?])\s+/).at(-1) ?? '';

const LOGIC_KINDS = 'E P P P P P P P P E P P P P P X';   // L1..L16 (6-2)
const MIPS_KINDS = 'E E E P P P E P P E E P P X';          // C1..C14 (6-3)
const kinds = (steps: S[]) => steps.map((s) => ({ explain: 'E', practice: 'P', end: 'X' })[s.kind]).join(' ');
// The practice steps with a result beat (the brief's "결과: …"); the others go straight on.
const LOGIC_RESULTS = ['L4', 'L5', 'L6', 'L7', 'L9', 'L12', 'L13', 'L14', 'L15'];
const MIPS_RESULTS = ['C5', 'C6', 'C8', 'C9', 'C12', 'C13'];

for (const [name, steps, prefix, kindsWanted, results] of [
  ['논리설계 및 실험', LOGIC_STEPS, 'L', LOGIC_KINDS, LOGIC_RESULTS],
  ['컴퓨터구조', MIPS_STEPS, 'C', MIPS_KINDS, MIPS_RESULTS],
] as const) {
  test(`${name}: the brief's steps, in order and of its kinds`, () => {
    assert.deepEqual(steps.map((s) => s.id), steps.map((_, i) => `${prefix}${i + 1}`));
    assert.equal(kinds(steps as S[]), kindsWanted);
    assert.deepEqual(steps.filter((s) => s.result).map((s) => s.id), [...results]);
    assert.equal(steps.at(-1)!.targets(t).length, 0, 'the end card stands in the middle');
  });

  test(`${name}: a practice step waits (done, skip) and says in its last sentence what comes once it is done`, () => {
    for (const s of steps as S[]) {
      if (s.kind === 'practice') {
        assert.ok(s.done && s.skip, `${s.id}: done and skip`);
        const last = lastSentence(s.body(t));
        if (s.result) assert.match(last, /짚어 드립니다\.$/, `${s.id}: ${last}`);
        else assert.match(last, /넘어갑니다\.$/, `${s.id}: ${last}`);
        assert.ok(s.result === undefined || (typeof s.result.targets === 'function' && s.result.title(t) && s.result.body(t)), s.id);
      } else {
        assert.equal(s.done, undefined, `${s.id}: an explanation is not waited for`);
        assert.equal(s.result, undefined, s.id);
      }
    }
  });

  test(`${name}: the words keep the screen's rules`, () => {
    for (const s of steps as S[]) {
      for (const w of words(s)) {
        assert.ok(!PARTICLE_AFTER_NAME.test(w), `${s.id}: a particle right after a name: ${w.match(PARTICLE_AFTER_NAME)?.[0]} in ${w}`);
        assert.doesNotMatch(w, /하면 됩니다|한림/, s.id);
        assert.equal((w.match(/`/g) ?? []).length % 2, 0, `${s.id}: code in pairs of backticks`);
        // an address is code (D2Coding tells 0 from O, and no x turned into ×)
        assert.doesNotMatch(w.replace(/`[^`]*`/g, ''), /0x[0-9A-Fa-f]/, `${s.id}: an address outside backticks`);
      }
      assert.ok(s.title(t).length <= 24, `${s.id}: a short title`);
    }
  });

  test(`${name}: the keys a step lets through are named as keyName names them`, () => {
    for (const s of steps as S[]) for (const k of s.keys ?? []) assert.match(k, /^(F\d+|Delete|Ctrl\+.)$/, `${s.id}: ${k}`);
  });
}

test('keyName: F keys, Delete, Ctrl with a letter (upper case), the zoom keys; plain letters are no name', () => {
  const k = (o: Partial<KeyboardEvent>) => keyName({ key: '', ctrlKey: false, metaKey: false, ...o } as KeyboardEvent);
  assert.equal(k({ key: 'F10' }), 'F10');
  assert.equal(k({ key: 'F5' }), 'F5');
  assert.equal(k({ key: 'Delete' }), 'Delete');
  assert.equal(k({ key: 'k', ctrlKey: true }), 'Ctrl+K');
  assert.equal(k({ key: 'K', metaKey: true }), 'Ctrl+K');
  assert.equal(k({ key: '=', ctrlKey: true }), 'Ctrl+=');
  assert.equal(k({ key: '+', ctrlKey: true }), 'Ctrl+=');
  assert.equal(k({ key: '_', ctrlKey: true }), 'Ctrl+-');
  assert.equal(k({ key: 'a' }), null);
  assert.equal(k({ key: 'Enter' }), null);
});
