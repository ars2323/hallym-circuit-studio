/* The tutorial's facts (src/renderer/app/tutorial/facts.ts, N-18, D-161) on
   the real engine's snapshots at the tutorial's steps (tests/fixtures/
   tutorial/, written by engine TutorialExamplesTest doing what [건너뛰기]
   does): each practice step's fact is false before the student's action
   and true after it -- and false again when the action is only half done. */

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { test } from 'node:test';

import type { DiagMessage, Snapshot } from '../../src/main/protocol.ts';
import {
  andGate, andLabelled, andWired, boxOf, clockWired, halfAdderPlaced, labelled, longest, memoBox, messagesOf, netOf, onNet, othersAt, parts, tunnel, union, wireBox, wiresAt,
} from '../../src/renderer/app/tutorial/facts.ts';
import actions from '../../src/renderer/app/tutorial/actions.json' with { type: 'json' };
import { MIPS_LIB, partShown, usesMipsOnly } from '../../src/renderer/app/logic/course.ts';

type Point = { snapshot: Snapshot; messages: DiagMessage[] };
const load = (name: string): Record<string, Point> => JSON.parse(readFileSync(path.join(import.meta.dirname, '../fixtures/tutorial', name), 'utf8'));
const logic = load('logic.json');
const mips = load('mips.json');

// A copy of a snapshot with one port taken off its net (the student's wire not quite there).
function without(s: Snapshot, componentId: string, port: number): Snapshot {
  const c = structuredClone(s);
  for (const n of c.nets) n.ports = n.ports.filter(([id, i]) => !(id === componentId && i === port));
  return c;
}

test('L3·L4: an AND gate placed, then A, B at two of its inputs and its output at Y', () => {
  assert.equal(andGate(logic.initial.snapshot), undefined);
  assert.equal(andWired(logic.initial.snapshot), false);
  assert.ok(andGate(logic.andPlaced.snapshot));
  assert.equal(andWired(logic.andPlaced.snapshot), false, 'placed, not wired');
  assert.equal(andWired(logic.andWired.snapshot), true);
  const s = logic.andWired.snapshot;
  const g = andGate(s)!;
  assert.deepEqual(g.loc, actions.logic.and.loc);
  // any one of the three wires missing: not done
  assert.equal(andWired(without(s, g.id, 0)), false, 'Y not at the output');
  assert.equal(andWired(without(s, labelled(s, 'Pin', 'A')!.id, 0)), false, 'A not at an input');
  assert.equal(andWired(without(s, labelled(s, 'Pin', 'B')!.id, 0)), false, 'B not at an input');
  assert.deepEqual(othersAt(s, labelled(s, 'Pin', 'Y')!.id, 0).map(([c, i]) => `${c.name}#${i}`), ['AND Gate#0']);
});

test('L6: the label', () => {
  assert.equal(andLabelled(logic.andWired.snapshot, actions.logic.label), false);
  assert.equal(andLabelled(logic.labelled.snapshot, actions.logic.label), true);
  assert.equal(andLabelled(logic.labelled.snapshot, 'g2'), false);
});

test('L8: the half_adder with both inputs on the waiting wires', () => {
  assert.equal(halfAdderPlaced(logic.labelled.snapshot), false);
  const s = logic.halfAdder.snapshot;
  assert.equal(halfAdderPlaced(s), true);
  const k = parts(s, 'half_adder')[0];
  assert.deepEqual(k.loc, actions.logic.halfAdder.loc);
  const inputs = k.ports.filter((p) => p.dir !== 'out');
  assert.equal(halfAdderPlaced(without(s, k.id, inputs[0].i)), false, 'one input off its wire');
});

test('L11·L12: the clock message, then the Clock at the count register and no message', () => {
  assert.deepEqual(logic.initial.messages.map((m) => m.code), ['CLOCK_UNCONNECTED']);
  assert.equal(messagesOf(logic.initial.messages, 'CLOCK_UNCONNECTED').length, 1);
  assert.equal(clockWired(logic.halfAdder.snapshot), false);
  assert.equal(clockWired(logic.clockWired.snapshot), true);
  assert.deepEqual(logic.clockWired.messages, []);
  const r = labelled(logic.clockWired.snapshot, 'Register', 'count')!;
  assert.equal(clockWired(without(logic.clockWired.snapshot, r.id, 2)), false);
});

test('L13: the count register\'s output wires, the longest to click', () => {
  const s = logic.initial.snapshot;
  const r = labelled(s, 'Register', 'count')!;
  const wires = wiresAt(s, r.id, 0);
  assert.ok(wires.length >= 3, 'Q runs to the adder, the probes and the splitter');
  const w = longest(s, wires)!;
  assert.ok(wires.includes(w));
  const b = wireBox(s, w)!;
  assert.ok(b[2] - b[0] >= 12 && b[3] - b[1] >= 12);
  assert.equal(wireBox(s, 'nope'), null);
  assert.equal(netOf(s, r.id, 0)?.width, 4);
});

test('the places the steps point at: the memos, the parts\' boxes', () => {
  const s = logic.initial.snapshot;
  assert.deepEqual(memoBox(s, 'AND Gate'), [200, 90, 380, 230]);
  assert.equal(memoBox(s, 'nothing'), null);
  const hb = memoBox(s, 'half_adder')!;
  const [x, y] = actions.logic.halfAdder.loc;
  assert.ok(hb[0] < x && x < hb[2] && hb[1] < y && y < hb[3], 'the half_adder\'s place is inside its memo');
  const pins = boxOf([labelled(s, 'Pin', 'A'), labelled(s, 'Pin', 'Y')], 10)!;
  assert.ok(pins[0] < 100 && pins[2] > 460);
  assert.equal(boxOf([], 10), null);
  assert.deepEqual(union([0, 0, 10, 10], [5, -5, 20, 5]), [0, -5, 20, 10]);
  assert.deepEqual(union(null, [1, 2, 3, 4]), [1, 2, 3, 4]);
});

test('C4·C5: the misspelt tunnel and its one near name, then no message', () => {
  const first = mips.initial.messages;
  assert.deepEqual(first.map((m) => m.code), ['TUNNEL_UNPAIRED']);
  assert.equal(first[0].near, actions.mips.fixed);
  assert.ok(tunnel(mips.initial.snapshot, actions.mips.typo));
  assert.equal(tunnel(mips.fixed.snapshot, actions.mips.typo), undefined);
  assert.ok(tunnel(mips.fixed.snapshot, actions.mips.fixed));
  assert.deepEqual(mips.fixed.messages, []);
});

test('C2: the datapath\'s parts the tour points at are all there', () => {
  const s = mips.initial.snapshot;
  for (const name of ['Instruction Memory', 'Data Memory', 'Console', 'control', 'regfile', 'alu']) assert.equal(parts(s, name).length, 1, name);
  for (const label of ['PC reg', 'started']) assert.ok(labelled(s, 'Register', label), label);
  assert.ok(parts(s, 'Constant').some((c) => /^0x0*400000$/i.test(c.attrs.value ?? '')), 'the entry constant');
});

test('L13·C12: a Signal Flow from this net: one of its wires or a part on it', () => {
  const s = logic.initial.snapshot;
  const r = labelled(s, 'Register', 'count')!;
  const n = netOf(s, r.id, 0);
  assert.equal(onNet(n, n!.wires[0]), true);
  assert.equal(onNet(n, r.id), true, 'the register itself');
  assert.equal(onNet(n, labelled(s, 'Pin', 'A')!.id), false);
  assert.equal(onNet(n, null), false);
  assert.equal(onNet(undefined, r.id), false);
});

test('논리설계 및 실험 (A-08): its track needs no part the course hides -- the example\'s parts and the one it places are listed there, Radix Probe the only Hallym MIPS part', () => {
  const held = actions.logic.and;
  assert.equal(partShown('logic', held.lib, held.name), true);
  for (const [name, point] of Object.entries(logic)) {
    if (name === 'about') continue;
    for (const c of point.snapshot.components) assert.ok(partShown('logic', c.lib ?? null, c.name), `${c.lib} ${c.name} (${name})`);
  }
  const fromMips = logic.initial.snapshot.components.filter((c) => c.lib === MIPS_LIB).map((c) => c.name);
  assert.deepEqual([...new Set(fromMips)], ['Radix Probe']);
  assert.equal(usesMipsOnly(logic.initial.snapshot.components), false);
  assert.equal(usesMipsOnly(mips.initial.snapshot.components), true, 'the 컴퓨터구조 example does use them');
});

test("L4: a wire at the gate's output is not at an input, Y at an input is not at the output", () => {
  const s = logic.andWired.snapshot;
  const g = andGate(s)!;
  const renumber = (pin: string, from: number, to: number) => {
    const c = structuredClone(s);
    const p = labelled(c, 'Pin', pin)!;
    for (const n of c.nets) if (n.ports.some(([id, i]) => id === p.id && i === 0)) n.ports = n.ports.map(([id, i]) => (id === g.id && i === from ? [id, to] : [id, i]));
    return c;
  };
  const aPort = othersAt(s, labelled(s, 'Pin', 'A')!.id, 0)[0][1];
  assert.equal(andWired(renumber('A', aPort, 0)), false, 'A at the output');
  assert.equal(andWired(renumber('Y', 0, 3)), false, 'Y at an input');
});
