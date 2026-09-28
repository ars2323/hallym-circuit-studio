/* The search model of the palette (Ctrl+K) and the Components search
   (logic/search.ts; v1 Palette, D-037, I-110, I-170; D-150): names,
   aliases, the number after a name, the ranking, recent and favourites. */

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { test } from 'node:test';

import type { LibraryGroup } from '../../src/main/protocol.ts';
import { ALIASES, argAttrs, attrText, COMMANDS, fuzzy, match, search, SEARCH_HINTS, searchHint, split, toggleFavorite, touch } from '../../src/renderer/app/logic/search.ts';
import { TOOL_ARGS } from '../../src/renderer/app/logic/tool-args.ts';

// The real engine's library (tests/fixtures/library.json, written by the engine).
const LIB = JSON.parse(readFileSync(path.join(import.meta.dirname, '../fixtures/library.json'), 'utf8')) as Record<string, LibraryGroup[]>;
const libraries = (key: string): LibraryGroup[] => structuredClone(LIB[key]).map((g, i) => (i === 0 ? { ...g, tools: g.tools.map((t, k) => ({ ...t, circuitId: `c${k + 1}` })) } : g));
const src = (over: Record<string, unknown> = {}) => ({ libraries: libraries('demo-datapath.circ'), fileName: 'demo-datapath.circ', current: 'c1', ...over });
const names = (q: string, over: Record<string, unknown> = {}) => search(q, src(over)).map((i) => `${i.kind}:${i.name}`);

test('split: the number after a name, decimal or 0x', () => {
  assert.deepEqual(split('and 3'), ['and', '3']);
  assert.deepEqual(split('  mux   32 '), ['mux', '32']);
  assert.deepEqual(split('const 0x1F'), ['const', '0x1F']);
  assert.deepEqual(split('and'), ['and', null]);
  assert.deepEqual(split('3'), ['3', null]);         // a number alone is the name
  assert.deepEqual(split('reg x'), ['reg x', null]);
});

test('match: same 100, start 80, part 50, letters in order from the first 30 (three letters or more)', () => {
  assert.equal(match('and', ['AND Gate', 'and']), 100);
  assert.equal(match('reg', ['Register']), 80);
  assert.equal(match('gate', ['AND Gate']), 50);
  assert.equal(match('rgw', ['RegWrite']), 30);
  assert.equal(match('rg', ['RegWrite']), 0, 'two letters: no fuzzy');
  assert.equal(match('and', ['Simulation Enabled']), 0, 'fuzzy starts at the first letter');
  assert.equal(match('xyz', ['Adder']), 0);
  assert.equal(fuzzy('abc', 'aXbXc'), true);
  assert.equal(fuzzy('acb', 'aXbXc'), false);
});

test('argAttrs: a gate\'s inputs, a splitter\'s incoming width, a constant\'s 0x value, else the width; a number out of range leaves the part out', () => {
  assert.deepEqual(argAttrs('Gates', 'AND Gate', '3'), { inputs: '3' });
  assert.equal(argAttrs('Gates', 'AND Gate', '1'), null, 'a gate has 2 to 32 inputs');
  assert.equal(argAttrs('Gates', 'AND Gate', '33'), null);
  assert.deepEqual(argAttrs('Gates', 'NOT Gate', '8'), { width: '8' });
  assert.deepEqual(argAttrs('Wiring', 'Splitter', '32'), { incoming: '32' });
  assert.deepEqual(argAttrs('Wiring', 'Constant', '0x1f'), { value: '0x1f' });
  assert.deepEqual(argAttrs('Wiring', 'Constant', '8'), { width: '8' });
  assert.deepEqual(argAttrs('Plexers', 'Multiplexer', '0x20'), { width: '32' }, '0x is a number where no value takes it');
  assert.deepEqual(argAttrs('Memory', 'RAM', '16'), { dataWidth: '16' });
  assert.equal(argAttrs('Memory', 'Register', '0'), null);
  assert.deepEqual(argAttrs('Wiring', 'Probe', '8'), {}, 'a probe takes no number: listed as it is');
  assert.deepEqual(argAttrs('Gates', 'AND Gate', null), {});
  assert.deepEqual(argAttrs('kr.ac.hallym.hcs.mips.MipsLibrary', 'Radix Probe', '32'), { width: '32' });
  assert.equal(attrText('Gates', 'AND Gate', { inputs: '3' }), 'Number Of Inputs 3');
  assert.equal(attrText('Wiring', 'Constant', { value: '0x1f' }), 'Value 0x1f');
  assert.equal(attrText('Memory', 'RAM', { dataWidth: '16' }), 'Data Bit Width 16');
});

test('the table covers the parts the engine lists, and the names it uses', () => {
  const tools = new Set(libraries('new').filter((g) => g.lib !== null).flatMap((g) => g.tools.map((t) => `${g.lib}/${t.name}`)));
  for (const k of Object.keys(TOOL_ARGS)) assert.ok(tools.has(k), `${k} is in model.library`);
  // every alias names a part the engine has (or had in v1: Stack is kept for old files only, D-140)
  const known = new Set([...tools].map((k) => k.split('/').slice(1).join('/')));
  for (const n of Object.keys(ALIASES)) assert.ok(known.has(n), `alias for ${n}`);
});

test('search: exact names first, aliases in Korean and short forms, the number sets the attribute', () => {
  assert.deepEqual(names('and 3').slice(0, 2), ['component:AND Gate', 'component:NAND Gate']);
  assert.equal(search('and 3', src())[0].attrText, 'Number Of Inputs 3');
  assert.equal(names('먹스')[0], 'component:Multiplexer');
  assert.equal(names('레지스터')[0], 'component:Register');
  assert.equal(names('reg 32')[0], 'component:Register');
  assert.ok(!names('and 1').includes('component:AND Gate'), 'one input: not an AND gate');
  assert.equal(names('imem')[0], 'component:Instruction Memory');
  assert.deepEqual(search('dmem', src())[0].group, 'Hallym MIPS');
  assert.deepEqual(names(''), []);
  assert.deepEqual(names('zzzz'), []);
});

test('search: this file\'s circuits (+15), the circuit on show only in the Components search, tunnels, commands (−5, only those that can run)', () => {
  assert.equal(names('regfile')[0], 'subcircuit:regfile');
  assert.equal(search('regfile', src())[0].score, 115);
  assert.ok(!names('main').includes('subcircuit:main'), 'the circuit on show cannot go into itself');
  assert.ok(names('main', { includeCurrent: true }).includes('subcircuit:main'));
  assert.deepEqual(names('regw', { tunnels: [{ name: 'RegWrite', count: 3 }] }), ['tunnel:RegWrite']);
  assert.equal(search('regw', src({ tunnels: [{ name: 'RegWrite', count: 3 }] }))[0].count, 3);
  assert.deepEqual(names('reset'), [], 'no commands unless offered');
  const all = COMMANDS.map((c) => c.id);
  assert.equal(names('reset', { commands: all })[0], 'command:Reset Simulation');
  assert.equal(search('reset', src({ commands: all }))[0].score, 95);
  assert.equal(names('리셋', { commands: all })[0], 'command:Reset Simulation');
  assert.equal(names('find', { commands: all })[0], 'command:Find');
  assert.equal(names('run', { commands: all })[0], 'command:Ticks Enabled');
  assert.ok(!names('splitter', { commands: ['reset'] }).includes('command:Edit Splitter…'));
  assert.ok(names('splitter', { commands: ['editSplitter'] }).includes('command:Edit Splitter…'));
});

test('recent and favourites move a part up; the recent list keeps eight', () => {
  const plain = search('gate', src()).map((i) => i.name);
  const fav = search('gate', src({ favorites: ['component Gates/XOR Gate'] }));
  assert.equal(fav[0].name, 'XOR Gate');
  assert.equal(fav[0].score, 50 + 30);
  const rec = search('gate', src({ recent: ['component Gates/OR Gate', 'component Gates/NOR Gate'] }));
  assert.equal(rec[0].name, 'OR Gate');
  assert.equal(rec[0].score, 50 + 20);
  assert.equal(rec[1].name, 'NOR Gate');
  assert.equal(rec[1].score, 50 + 18);
  assert.notDeepEqual(plain.slice(0, 2), ['OR Gate', 'NOR Gate']);
  let r: string[] = [];
  for (let i = 0; i < 10; i++) r = touch(r, `k${i}`);
  assert.equal(r.length, 8);
  assert.equal(r[0], 'k9');
  assert.deepEqual(touch(['a', 'b', 'c'], 'b'), ['b', 'a', 'c']);
  assert.deepEqual(toggleFavorite(['a'], 'b'), ['a', 'b']);
  assert.deepEqual(toggleFavorite(['a', 'b'], 'a'), ['b']);
});

test('ties go by name, then key: the same list every time', () => {
  const a = search('or', src()).map((i) => i.key);
  const b = search('or', src()).map((i) => i.key);
  assert.deepEqual(a, b);
  for (let i = 1; i < a.length; i++) {
    const x = search('or', src())[i - 1], y = search('or', src())[i];
    assert.ok(x.score > y.score || (x.score === y.score && x.name.localeCompare(y.name, 'en') <= 0));
  }
});

test('the search box\'s example text: the longest that fits whole, down to "Search"', () => {
  const w = (t: string) => t.length * 7;
  assert.equal(searchHint(1000, w), SEARCH_HINTS[0]);
  assert.equal(searchHint(w(SEARCH_HINTS[0]) - 1, w), 'Search (and 3, mux 32)');
  assert.equal(searchHint(w('Search (and 3, mux 32)') - 1, w), 'Search');
  assert.equal(searchHint(10, w), 'Search');
});
