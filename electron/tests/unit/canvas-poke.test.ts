/* src/renderer/canvas/poke.ts (N-07): what the Poke tool decides on the
   screen -- the part under the pointer, a subcircuit's lens, the parts that
   take keys and which keys, the poked wire's box (Logisim's binary / signed
   decimal). */

import assert from 'node:assert/strict';
import { test } from 'node:test';

import type { Component } from '../../src/main/protocol.ts';
import { KEYED, magnifier, onMagnifier, pokeKey, pokePoint, pokeTarget, wireValueText } from '../../src/renderer/canvas/poke.ts';

const part = (id: string, name: string, bounds: [number, number, number, number], over: Partial<Component> = {}): Component => ({
  id, lib: 'Wiring', name, loc: [bounds[0], bounds[1]], bounds, facing: 'east', attrs: {}, ports: [], ...over,
});

test('pokeTarget: the topmost part whose box holds the point, edges included', () => {
  const a = part('k1', 'Pin', [80, 410, 20, 20]);
  const b = part('k2', 'Register', [90, 400, 30, 40]);
  assert.equal(pokeTarget([a, b], [95, 415])?.id, 'k2', 'drawn later, on top');
  assert.equal(pokeTarget([a, b], [82, 412])?.id, 'k1');
  assert.equal(pokeTarget([a, b], [100, 430])?.id, 'k2');
  assert.equal(pokeTarget([a], [100, 430])?.id, 'k1', 'the edge is inside');
  assert.equal(pokeTarget([a, b], [10, 10]), null);
});

test('onMagnifier: within r² ≤ 60 of the box\'s integer centre (SubcircuitPoker.isWithin), subcircuits only', () => {
  const sub = part('k3', 'alu', [100, 100, 41, 60], { lib: null, subcircuit: 'c2' });
  // centre: 100 + floor(41/2) = 120, 100 + 30 = 130
  assert.equal(magnifier(sub).cx, 120);
  assert.equal(magnifier(sub).cy, 130);
  assert.equal(onMagnifier(sub, [120, 130]), true);
  assert.equal(onMagnifier(sub, [127, 131]), true);     // 49 + 1 = 50
  assert.equal(onMagnifier(sub, [126, 136]), false);    // 36 + 36 = 72
  assert.equal(onMagnifier(part('k4', 'Pin', [100, 100, 41, 60]), [120, 130]), false, 'not a subcircuit');
  assert.equal(magnifier(sub).handle.length, 4);
});

test('pokeKey: named keys and single characters; nothing with a modifier or while composing', () => {
  const k = (key: string, over: Partial<{ ctrlKey: boolean; altKey: boolean; metaKey: boolean; isComposing: boolean }> = {}) =>
    pokeKey({ key, ctrlKey: false, altKey: false, metaKey: false, ...over });
  assert.equal(k('a'), 'a');
  assert.equal(k('F'), 'F');
  assert.equal(k(' '), ' ');
  assert.equal(k('Backspace'), 'Backspace');
  assert.equal(k('ArrowLeft'), 'ArrowLeft');
  assert.equal(k('Home'), 'Home');
  assert.equal(k('Shift'), null);
  assert.equal(k('F5'), null);
  assert.equal(k('a', { ctrlKey: true }), null);
  assert.equal(k('1', { altKey: true }), null);
  assert.equal(k('ㄱ', { isComposing: true }), null);
  assert.ok(KEYED.has('Register') && KEYED.has('RAM') && KEYED.has('Keyboard') && !KEYED.has('Pin'));
});

test('pokePoint: whole circuit units, as Logisim\'s mouse events', () => {
  assert.deepEqual(pokePoint([95.7, 619.2]), [95, 619]);
  assert.deepEqual(pokePoint([-0.5, 3]), [-1, 3]);
});

test('wireValueText: one bit as it is; more bits in groups of four / signed decimal (PokeTool.WireCaret)', () => {
  assert.equal(wireValueText('1', 1), '1');
  assert.equal(wireValueText('x', 1), 'x');
  assert.equal(wireValueText('E', 1), 'E');
  assert.equal(wireValueText('0110', 4), '0110 / 6');
  assert.equal(wireValueText('1110', 4), '1110 / -2');
  assert.equal(wireValueText('00000101', 8), '0000 0101 / 5');
  assert.equal(wireValueText('101', 3), '101 / -3');
  assert.equal(wireValueText('11111111111111111111111111111111', 32), '1111 1111 1111 1111 1111 1111 1111 1111 / -1');
  assert.equal(wireValueText('10000000000000000000000000000000', 32).endsWith('/ -2147483648'), true);
  assert.equal(wireValueText('01x1', 4), '01x1 / ???');
  assert.equal(wireValueText('0E00', 4), '0E00 / Error');
  assert.equal(wireValueText(undefined, 2), 'xx / ???', 'no value yet: floating');
  assert.equal(wireValueText('1', 0), '-');
});
