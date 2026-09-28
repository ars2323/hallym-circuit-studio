/* src/renderer/app/logic/editing.ts (N-08): which intent a key is for the
   Edit tool and for a tool placing a part (docs/interaction-parity.md
   I-28..I-32, I-38..I-40, I-42, I-58, I-59), by the physical key; digits
   in a row within 0.8 s; the parts a double click or Ctrl+click does
   something to. */

import assert from 'node:assert/strict';
import { test } from 'node:test';

import type { Component } from '../../src/main/protocol.ts';
import { chained, CHAIN_MS, ctrlPokes, digitOf, editKey, isButton, isInputPin, type KeyLike, labelFieldWidth, labelled, placeKey, TOOLBAR_PARTS } from '../../src/renderer/app/logic/editing.ts';

const k = (key: string, code: string, mods: Partial<KeyLike> = {}): KeyLike => ({ key, code, ctrlKey: false, altKey: false, shiftKey: false, metaKey: false, ...mods });
const part = (lib: string, name: string, attrs: Record<string, string> = {}): Component => ({
  id: 'k1', lib, name, loc: [0, 0], bounds: [0, 0, 30, 40], facing: 'east', attrs, ports: [],
});

test('digitOf: the row and the number pad', () => {
  assert.equal(digitOf('Digit3'), '3');
  assert.equal(digitOf('Numpad7'), '7');
  assert.equal(digitOf('KeyA'), null);
  assert.equal(digitOf('NumpadAdd'), null);
});

test('editKey with a selection: Delete, Backspace, the arrows, R, Shift+R, digits, Alt, F2, Insert', () => {
  assert.deepEqual(editKey(k('Delete', 'Delete'), true, false), { kind: 'delete' });
  assert.deepEqual(editKey(k('Backspace', 'Backspace'), true, false), { kind: 'delete' });
  assert.deepEqual(editKey(k('ArrowLeft', 'ArrowLeft'), true, false), { kind: 'nudge', dx: -10, dy: 0 });
  assert.deepEqual(editKey(k('ArrowDown', 'ArrowDown'), true, false), { kind: 'nudge', dx: 0, dy: 10 });
  assert.deepEqual(editKey(k('r', 'KeyR'), true, false), { kind: 'rotate', clockwise: true });
  assert.deepEqual(editKey(k('R', 'KeyR', { shiftKey: true }), true, false), { kind: 'rotate', clockwise: false });
  assert.deepEqual(editKey(k('ㄱ', 'KeyR'), true, false), { kind: 'rotate', clockwise: true }, 'a Korean layout: the same key');
  assert.deepEqual(editKey(k('3', 'Digit3'), true, false), { kind: 'keyConfig', key: '3', alt: false });
  assert.deepEqual(editKey(k('8', 'Digit8', { altKey: true }), true, false), { kind: 'keyConfig', key: '8', alt: true });
  assert.deepEqual(editKey(k('ArrowUp', 'ArrowUp', { altKey: true }), true, false), { kind: 'keyConfig', key: 'ArrowUp', alt: true });
  assert.deepEqual(editKey(k('F2', 'F2'), true, true), { kind: 'label' });
  assert.equal(editKey(k('F2', 'F2'), true, false), null, 'no single labelled part');
  assert.deepEqual(editKey(k('Insert', 'Insert'), true, false), { kind: 'duplicate' });
  assert.deepEqual(editKey(k('f', 'KeyF'), true, false), { kind: 'fit' });
  assert.equal(editKey(k('f', 'KeyF'), false, false), null, 'F with nothing selected: the palette\'s letter (I-41)');
  assert.equal(editKey(k('Escape', 'Escape'), true, false), null, 'Esc leaves the selection (I-19)');
  assert.equal(editKey(k('c', 'KeyC', { ctrlKey: true }), true, false), null, 'the menu\'s keys are the window\'s');
  assert.equal(editKey(k('ArrowLeft', 'ArrowLeft', { shiftKey: true }), true, false), null);
  assert.equal(editKey(k('#', 'Digit3', { shiftKey: true }), true, false), null);
  assert.equal(editKey(k('3', 'Digit3', { isComposing: true }), true, false), null, 'Hangul being composed (I-212)');
});

test('editKey without a selection: only Backspace, to take back the wire just drawn (I-31); letters go to the palette', () => {
  assert.deepEqual(editKey(k('Backspace', 'Backspace'), false, false), { kind: 'undoWire' });
  assert.equal(editKey(k('Delete', 'Delete'), false, false), null);
  assert.equal(editKey(k('ArrowLeft', 'ArrowLeft'), false, false), null);
  assert.equal(editKey(k('r', 'KeyR'), false, false), null);
  assert.equal(editKey(k('3', 'Digit3'), false, false), null);
});

test('placeKey: an arrow turns the tool, a digit or Alt+digit or Alt+arrow goes to its configurator', () => {
  assert.deepEqual(placeKey(k('ArrowUp', 'ArrowUp')), { key: 'ArrowUp', alt: false });
  assert.deepEqual(placeKey(k('4', 'Digit4')), { key: '4', alt: false });
  assert.deepEqual(placeKey(k('4', 'Numpad4', { altKey: true })), { key: '4', alt: true });
  assert.deepEqual(placeKey(k('ArrowLeft', 'ArrowLeft', { altKey: true })), { key: 'ArrowLeft', alt: true });
  assert.equal(placeKey(k('ArrowUp', 'ArrowUp', { ctrlKey: true })), null);
  assert.equal(placeKey(k('ArrowUp', 'ArrowUp', { shiftKey: true })), null);
  assert.equal(placeKey(k('a', 'KeyA')), null);
});

test('chained: digits within 0.8 s make one number', () => {
  assert.equal(CHAIN_MS, 800);
  assert.equal(chained(1000, 1799), true);
  assert.equal(chained(1000, 1800), false);
  assert.equal(chained(-Infinity, 0), false);
});

test('the parts: input pins and buttons (Ctrl+click, a double click\'s value), the labelled ones (F2, a double click)', () => {
  const inPin = part('Wiring', 'Pin', { output: 'false', label: '' });
  const outPin = part('Wiring', 'Pin', { output: 'true', label: '' });
  const button = part('Input/Output', 'Button', { label: '' });
  assert.equal(isInputPin(inPin), true);
  assert.equal(isInputPin(outPin), false);
  assert.equal(isButton(button), true);
  assert.equal(ctrlPokes(inPin), true);
  assert.equal(ctrlPokes(button), true);
  assert.equal(ctrlPokes(outPin), false);
  assert.equal(ctrlPokes(part('Gates', 'AND Gate', { label: '' })), false);
  assert.equal(labelled(part('Gates', 'AND Gate', { label: '' })), true);
  assert.equal(labelled(part('Base', 'Text', { text: 'x', label: '' })), false, 'a Label is the Text tool\'s');
  assert.equal(labelled(part('Wiring', 'Splitter', { fanout: '2' })), false);
  assert.equal(labelFieldWidth(part('Gates', 'AND Gate'), 1), 96);
  assert.equal(labelFieldWidth(part('Gates', 'AND Gate'), 4), 136);
  assert.deepEqual(TOOLBAR_PARTS.Pin, { lib: 'Wiring', name: 'Pin' });
});
