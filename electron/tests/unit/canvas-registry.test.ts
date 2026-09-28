/* The part renderer registry (registry.ts) and the tokens (tokens.ts,
   canvas.css): every course part has its own renderer, the rest are
   listed where docs/canvas-renderers.md says, the value colours are the
   same in the CSS the legend reads and in the Canvas, and the renderers'
   text helpers say what the original says. */

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { test } from 'node:test';

import type { Component } from '../../src/main/protocol.ts';
import { bin, dec, hex, NO_STATE, ranges } from '../../src/renderer/canvas/parts/common.ts';
import { HEX_SEGMENTS } from '../../src/renderer/canvas/parts/io.ts';
import { radixLines, staticLines } from '../../src/renderer/canvas/parts/mips.ts';
import { constantBits, probeText, splitterArms } from '../../src/renderer/canvas/parts/wiring.ts';
import { CIRCUIT, FALLBACK, FALLBACK_KINDS, kindOf, MIPS_LIB, REGISTRY, rendererFor } from '../../src/renderer/canvas/registry.ts';
import { KIND_TOKEN, LEGEND, THEME, TUNNEL_PALETTE, VALUE_VARS, valueKind } from '../../src/renderer/canvas/tokens.ts';

const repo = path.join(import.meta.dirname, '..', '..', '..');
const geometry = (JSON.parse(readFileSync(path.join(import.meta.dirname, '../fixtures/geometry.json'), 'utf8')) as { cases: { case: string; component: Component }[] }).cases;
const part = (name: string): Component => structuredClone(geometry.find((c) => c.case === name)!.component);

// The v2 brief's list (3-4): the course parts, each with its own renderer.
const COURSE: [string, string][] = [
  ...['AND Gate', 'OR Gate', 'NOT Gate', 'NAND Gate', 'NOR Gate', 'XOR Gate', 'XNOR Gate', 'Buffer', 'Controlled Buffer'].map((n) => ['Gates', n] as [string, string]),
  ...['Multiplexer', 'Demultiplexer', 'Decoder', 'Priority Encoder', 'BitSelector'].map((n) => ['Plexers', n] as [string, string]),
  ...['Splitter', 'Pin', 'Probe', 'Tunnel', 'Clock', 'Constant', 'Pull Resistor', 'Ground', 'Power'].map((n) => ['Wiring', n] as [string, string]),
  ...['Adder', 'Subtractor', 'Multiplier', 'Divider', 'Negator', 'Comparator', 'Shifter', 'BitAdder', 'BitFinder'].map((n) => ['Arithmetic', n] as [string, string]),
  ...['D Flip-Flop', 'T Flip-Flop', 'J-K Flip-Flop', 'S-R Flip-Flop', 'Register', 'Counter', 'Shift Register', 'Random', 'RAM', 'ROM'].map((n) => ['Memory', n] as [string, string]),
  ...['LED', 'Button', '7-Segment Display', 'Hex Digit Display'].map((n) => ['I/O', n] as [string, string]),
  ['Base', 'Text'],
  ...['Instruction Memory', 'Data Memory', 'Stack', 'Console', 'Radix Probe'].map((n) => [MIPS_LIB, n] as [string, string]),
  [CIRCUIT, '*'],
];

test(`every course part (${COURSE.length} kinds) has its own renderer, marked as a course part`, () => {
  for (const [lib, name] of COURSE) {
    const r = rendererFor({ lib, name, appearance: lib === CIRCUIT ? { default: true, anchor: [0, 0], facing: 'east', shapes: [], ports: [] } : undefined });
    assert.notEqual(r, FALLBACK, `${lib}/${name}`);
    assert.equal(r.course, true, `${lib}/${name}`);
  }
  assert.equal(new Set(REGISTRY.map((r) => `${r.lib}/${r.name}`)).size, REGISTRY.length, 'one row per kind');
});

test('the default renderer\'s kinds: the list in docs/canvas-renderers.md, and anything unknown', () => {
  const doc = readFileSync(path.join(repo, 'docs/canvas-renderers.md'), 'utf8');
  const block = /<!-- fallback-kinds[^>]*-->([\s\S]*?)<!-- \/fallback-kinds -->/.exec(doc)![1];
  const listed = [...block.matchAll(/`([^`]+)`/g)].map((m) => m[1]);
  assert.deepEqual(listed, FALLBACK_KINDS);
  for (const k of FALLBACK_KINDS) {
    const [lib, ...rest] = k.split('/');
    const name = lib === 'I' ? rest.slice(1).join('/') : rest.join('/');
    assert.equal(rendererFor({ lib: lib === 'I' ? 'I/O' : lib, name }), FALLBACK, k);
  }
  assert.equal(rendererFor({ lib: 'com.example.MyLibrary', name: 'Widget' }), FALLBACK);
  assert.equal(kindOf({ lib: null, name: 'regfile', subcircuit: 'c2' }), 'circuit/*');
  assert.equal(kindOf({ lib: 'adder_lib', name: 'adder', subcircuit: 'c9' }), 'circuit/*', 'a .circ library\'s circuit is a subcircuit too');
});

test('no renderer fails on a part it did not expect (no ports, odd attributes): the Canvas never breaks', () => {
  for (const r of REGISTRY) {
    const odd: Component = { id: 'k1', lib: r.lib, name: r.name, loc: [100, 100], bounds: [80, 80, 40, 40], facing: null, attrs: { size: 'x', width: '', select: '0' }, ports: [] };
    assert.doesNotThrow(() => r.draw(odd, NO_STATE), `${r.lib}/${r.name}`);
  }
});

test('the value colours: one set, in canvas.css (the legend) and tokens.ts (the Canvas), matching Hallym MIPS\'s tokens', () => {
  const css = readFileSync(path.join(import.meta.dirname, '../../src/renderer/canvas/canvas.css'), 'utf8');
  for (const [token, name] of Object.entries(VALUE_VARS)) {
    const m = new RegExp(`${name}:\\s*(#[0-9a-f]{6})`, 'i').exec(css);
    assert.ok(m, `${name} in canvas.css`);
    assert.equal(m[1].toLowerCase(), THEME[token as keyof typeof THEME], name);
  }
  const shared = readFileSync(path.join(import.meta.dirname, '../../src/renderer/shared/shared.css'), 'utf8');
  for (const [token, name] of [['navy', '--navy'], ['blue', '--blue'], ['teal', '--teal'], ['ink', '--text'], ['ink2', '--text-2'], ['muted', '--muted'], ['border', '--border'], ['error', '--error'], ['blueTint2', '--blue-tint2']] as const) {
    const m = new RegExp(`${name}:\\s*(#[0-9a-f]{6})`, 'i').exec(shared);
    assert.equal(m?.[1].toLowerCase(), THEME[token], `${token} = shared.css ${name}`);
  }
  assert.equal(THEME.vError, THEME.error, 'an error is the Hallym MIPS error colour');
  // the legend: every kind a wire can show, each once
  assert.deepEqual(LEGEND.map((l) => l.token), ['vOne', 'vZero', 'vFloat', 'vError', 'vBus', 'vWidth']);
  for (const k of ['one', 'zero', 'float', 'error', 'bus'] as const) assert.ok(LEGEND.some((l) => l.token === KIND_TOKEN[k]), k);
  assert.equal(new Set(Object.values(VALUE_VARS).map((n) => n)).size, Object.keys(VALUE_VARS).length);
  assert.equal(TUNNEL_PALETTE.every((c) => /^#[0-9a-f]{6}$/.test(c)), true);
});

test('value kinds (docs/engine-api.md 4): 1, 0, x, E, a bus', () => {
  assert.equal(valueKind('1'), 'one');
  assert.equal(valueKind('0'), 'zero');
  assert.equal(valueKind('x'), 'float');
  assert.equal(valueKind('E'), 'error');
  assert.equal(valueKind('0x01'), 'bus', 'a bus with a floating bit keeps the bus colour (Logisim)');
  assert.equal(valueKind('xxxx'), 'float');
  assert.equal(valueKind('01E0'), 'error');
  assert.equal(valueKind(undefined), 'none');
});

test('the renderers\' words: hexadecimal, decimal, binary, bit ranges, constants, probes, the Radix Probe, the MIPS bodies', () => {
  assert.equal(hex('00000000010000000000000000100100', 32), '00400024');
  assert.equal(hex('01x1', 4), 'x');
  assert.equal(hex('1E01', 4), 'E');
  assert.equal(hex('101', 3), '5');
  assert.equal(dec('11111111', true), '-1');
  assert.equal(dec('11111111', false), '255');
  assert.equal(dec('1x', false), 'x');
  assert.equal(bin('10101010', 8), '1010 1010');
  assert.equal(ranges([0, 1, 2, 3, 7]), '[7,3:0]');
  assert.equal(ranges([5]), '[5]');
  assert.equal(constantBits(part('Wiring/Constant facing=east width=32 value=0x400000')), '00000000010000000000000000000000');
  assert.equal(probeText('11110000', '16'), 'f0');
  assert.equal(probeText('11110000', '10signed'), '-16');
  assert.equal(probeText('11110000', '8'), '360');
  assert.deepEqual(radixLines('00101010', 8, 1, true), ['42', '0x2a', '0010 1010']);
  assert.deepEqual(splitterArms(part('Wiring/Splitter facing=east fanout=4 incoming=8 appear=left')), [[0, 1], [2, 3], [4, 5], [6, 7]]);
  assert.deepEqual(staticLines(part(`${MIPS_LIB}/Data Memory`)), ['data  10000000-100fffff', 'stack 7ffc0000-7fffffff']);
  assert.deepEqual(staticLines(part(`${MIPS_LIB}/Data Memory base=0x10010000 stacksize=0x0`)), ['10010000-1010ffff']);
  assert.deepEqual(staticLines(part(`${MIPS_LIB}/Stack`)), ['7ff00000-7fffffff'], 'the old Stack\'s own attributes (top 0x7ffffffc, 1 MB)');
  assert.deepEqual(staticLines(part(`${MIPS_LIB}/Instruction Memory`)), ['00400000-004fffff']);
  assert.equal(HEX_SEGMENTS[8], 'abcdefg');
  assert.equal(HEX_SEGMENTS[1], 'bc');
});

test('the MIPS bodies say what the engine sends (sim.values bodies), and the attributes\' ranges before it has', () => {
  const dm = part(`${MIPS_LIB}/Data Memory`);
  const r = rendererFor(dm);
  const lines = ['data  10000000-100fffff', 'stack 7ffc0000-7fffffff', '10010000: 00000005', '', 'data 3 words, stack peak 56 B'];
  const shapes = r.draw(dm, { value: () => undefined, body: { lines, status: 'Addr not word-aligned' } });
  const texts = shapes.filter((s) => s.k === 'text').map((s) => (s.k === 'text' ? s.text : ''));
  for (const l of lines.filter(Boolean)) assert.ok(texts.includes(l), l);
  assert.ok(texts.includes('Addr not word-aligned'));
  for (const n of ['Addr', 'WriteData', 'MemWrite', 'MemRead', 'ReadData']) assert.ok(texts.includes(n), n);
  const console = part(`${MIPS_LIB}/Console`);
  const out = rendererFor(console).draw(console, { value: () => undefined, body: { lines: ['720', 'done'], exited: true, status: '-- exit --' } });
  assert.ok(out.some((s) => s.k === 'text' && s.text === '720'));
  assert.ok(out.some((s) => s.k === 'text' && s.text === '-- exit --' && s.fill === 'muted'));
});
