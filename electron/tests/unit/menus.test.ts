/* The right-click menus (N-10, D-157): the layout (v1 MenuLayout: summary,
   each provider's group, common, Delete last; S-25 singular and plural),
   the one registry, the Canvas's items for each kind of target from the
   engine's facts (v1 EditMenus, SplitterMenu, ProbeMenu and the original's
   component items), the dialogs' and the hex editor's pure parts. */

import assert from 'node:assert/strict';
import { test } from 'node:test';

import type { EditResult, MenuFacts, Point, WindowMethod } from '../../src/main/protocol.ts';
import type { MenuEntry } from '../../src/renderer/canvas/overlays/menu.ts';
import { tidy } from '../../src/renderer/canvas/overlays/menu.ts';
import { caretMove, digits, hexOf, PAGE, parseWords, wordsText } from '../../src/renderer/app/hex-editor.ts';
import { arrange, changeN, combineN, count, editLabelsN, gateShort, undefinedValue } from '../../src/renderer/app/logic/menu-layout.ts';
import { type Course, shows } from '../../src/renderer/app/logic/course.ts';
import { type CanvasActions, registerCanvasItems } from '../../src/renderer/app/menus/canvas-items.ts';
import { defaultSpacing, nextLabel } from '../../src/renderer/app/menus/dialogs.ts';
import { type CanvasTarget, menuFor, registered, registerMenu } from '../../src/renderer/app/menus/registry.ts';
import { toolOfKey } from '../../src/renderer/app/menus/side-menus.ts';

const labels = (es: MenuEntry[]): string[] => es.map((e) => (e.header ? `# ${e.label}` : e.label));

test('arrange (v1 MenuLayout): the summary in bold on top, each provider\'s own items a group, the common group, Delete last', () => {
  const out = arrange('AND #1 · 2 inputs · 1 bit', [
    [{ label: 'View x' }],
    [{ label: 'Duplicate', group: 'common' }, { label: 'Number of Inputs' }, { label: 'Delete', group: 'delete' }, { label: '-' }],
    [],
    [{ label: 'Influence' }],
  ]);
  assert.deepEqual(labels(out), ['# AND #1 · 2 inputs · 1 bit', 'View x', '-', 'Number of Inputs', '-', 'Influence', '-', 'Duplicate', '-', 'Delete']);
  assert.equal(out[0].header, true);
  assert.ok(out.every((e) => !('group' in e)));
  assert.deepEqual(labels(arrange(null, [[{ label: 'Paste' }]])), ['Paste']);
  assert.deepEqual(arrange(null, [[], []]), []);
});

test('tidy: no separator first, last, twice or under the header', () => {
  assert.deepEqual(labels(tidy([{ label: '-' }, { label: 'h', header: true }, { label: '-' }, { label: 'a' }, { label: '-' }, { label: '-' }, { label: 'b' }, { label: '-' }])),
    ['# h', 'a', '-', 'b']);
});

test('names: singular and plural (S-25), the gates\' short names, an E or x bit', () => {
  assert.equal(count(1, 'bit'), '1 bit');
  assert.equal(count(32, 'bit'), '32 bits');
  assert.equal(changeN(1), 'Change 1 Component');
  assert.equal(changeN(3), 'Change 3 Components');
  assert.equal(editLabelsN(2), 'Edit Labels of 2 Components…');
  assert.equal(combineN(2), 'Combine 2 Wires into One Bus (in the order chosen)');
  assert.equal(gateShort('XNOR Gate'), 'XNOR');
  assert.equal(undefinedValue('01x0'), true);
  assert.equal(undefinedValue('E'), true);
  assert.equal(undefinedValue('0101'), false);
  assert.equal(undefinedValue(undefined), false);
});

test('the registry: providers in their order, one replaced by its id, a failing one leaves the others', () => {
  const off0 = registerMenu('circuitTab', { id: 't-c', order: 40, items: () => [{ label: 'C' }] });
  const off1 = registerMenu('circuitTab', { id: 't-b', order: 20, items: () => [{ label: 'B' }] });
  const off2 = registerMenu('circuitTab', { id: 't-a', order: 5, items: () => [{ label: 'A' }] });
  const off3 = registerMenu('circuitTab', { id: 't-b', order: 20, items: () => [{ label: 'B2' }] });
  const off4 = registerMenu('circuitTab', { id: 't-x', order: 30, items: () => { throw new Error('broken'); } });
  const t = { fileId: 'f1', circuitId: 'c1', name: 'main', main: true, editable: true };
  const err = console.error;
  console.error = () => {};
  try {
    const out = labels(menuFor('circuitTab', t)).filter((l) => ['A', 'B', 'B2', 'C'].includes(l) || l === '-');
    assert.deepEqual(out, ['A', '-', 'B2', '-', 'C']);
  } finally {
    console.error = err;
    off0(); off2(); off3(); off4(); off1();
  }
  assert.ok(!registered('circuitTab').includes('t-a'));
});

test('toolOfKey: the Components list\'s data-tool key', () => {
  assert.deepEqual(toolOfKey('/main'), { lib: null, name: 'main' });
  assert.deepEqual(toolOfKey('Gates/AND Gate'), { lib: 'Gates', name: 'AND Gate' });
  assert.deepEqual(toolOfKey('/a/b'), { lib: null, name: 'a/b' });
});

test('dialogs: the next label of a copy (v1 Arrange.nextLabel) and the default spacing (defaultSpacing)', () => {
  assert.equal(nextLabel('R0', 1), 'R1');
  assert.equal(nextLabel('R07', 1), 'R08');
  assert.equal(nextLabel('R9', 2), 'R11');
  assert.equal(nextLabel('sel', 2), 'sel2');
  assert.equal(nextLabel('', 1), '');
  assert.equal(defaultSpacing({ w: 30, h: 45 }, 'down'), 60);
  assert.equal(defaultSpacing({ w: 30, h: 45 }, 'right'), 40);
  assert.equal(defaultSpacing({ w: 0, h: 0 }, 'up'), 10);
});

test('the hex editor: digits, words from pasted text, words to text, the caret\'s moves', () => {
  assert.equal(digits(8), 2);
  assert.equal(digits(1), 1);
  assert.equal(digits(32), 8);
  assert.equal(hexOf(10, 8), '0a');
  assert.equal(hexOf(0xffffffff, 32), 'ffffffff');
  assert.deepEqual(parseWords('0a 0B\n0x1f, 7', 8), [10, 11, 31, 7]);
  assert.equal(parseWords('100', 8), null);          // too wide
  assert.equal(parseWords('zz', 8), null);
  assert.equal(parseWords('   ', 8), null);
  assert.equal(wordsText(Array.from({ length: 10 }, (_, i) => i), 8), '00 01 02 03 04 05 06 07\n08 09');
  assert.equal(caretMove('ArrowRight', false, 5, 16), 6);
  assert.equal(caretMove('ArrowLeft', false, 0, 16), 0);
  assert.equal(caretMove('ArrowDown', false, 3, 16), 11);
  assert.equal(caretMove('ArrowDown', false, 12, 16), 15);
  assert.equal(caretMove('Home', false, 13, 16), 8);
  assert.equal(caretMove('End', false, 9, 16), 15);
  assert.equal(caretMove('Home', true, 13, 16), 0);
  assert.equal(caretMove('End', true, 3, 16), 15);
  assert.equal(caretMove('PageDown', false, 3, 1024), 3 + PAGE);
  assert.equal(caretMove('x', false, 3, 16), null);
});

// ---- the Canvas's items, from facts ----------------------------------------------------------------

interface Sent { method: WindowMethod | string; params: Record<string, unknown> }
const sent: Sent[] = [];
const notes: (string | null)[] = [];
let overlayItems: MenuEntry[] = [];
let course: Course = 'architecture';   // the course on show (A-08): 컴퓨터구조 shows everything
const actions: CanvasActions = {
  edit: async (_t, method, params) => { sent.push({ method, params }); return { changed: true } as EditResult; },
  overlayItems: () => overlayItems,
  netValue: (w) => (w === 'wx' ? '0x01' : '0101'),
  netWires: (w) => [w, 'w9'],
  menuCommand: (cmd) => sent.push({ method: `menu:${cmd}`, params: {} }),
  fit: () => sent.push({ method: 'fit', params: {} }),
  enter: (id) => sent.push({ method: 'enter', params: { id } }),
  showAttributes: (_t, id) => sent.push({ method: 'showAttributes', params: { id } }),
  reveal: (id) => sent.push({ method: 'reveal', params: { id } }),
  editSplitter: (_t, o) => sent.push({ method: 'editSplitter', params: o as Record<string, unknown> }),
  tunnelColor: (_t, id, color) => sent.push({ method: 'tunnelColor', params: { id, color } }),
  addRow: (_t, o) => sent.push({ method: 'addRow', params: o as Record<string, unknown> }),
  markPc: (_t, id, on) => sent.push({ method: 'markPc', params: { id, on } }),
  markRegisterFile: (_t, circuitId, on) => sent.push({ method: 'markRegisterFile', params: { circuitId, on } }),
  registerMapping: () => sent.push({ method: 'registerMapping', params: {} }),
  shows: (f) => shows(course, f),
  loadProgram: (_t, id, forSource) => sent.push({ method: 'loadProgram', params: { id, forSource } }),
  reloadProgram: () => sent.push({ method: 'reloadProgram', params: {} }),
  findOrigin: (_t, w) => sent.push({ method: 'findOrigin', params: { w } }),
  label: (_t, ids, now) => sent.push({ method: 'label', params: { ids, now } }),
  labels: (_t, ids, ls) => sent.push({ method: 'labels', params: { ids, ls } }),
  tunnels: (_t, w) => sent.push({ method: 'tunnels', params: { w } }),
  duplicateN: (_t, ids) => sent.push({ method: 'duplicateN', params: { ids } }),
  contents: (_t, id, kind) => sent.push({ method: 'contents', params: { id, kind } }),
  clearContents: (_t, id, kind) => sent.push({ method: 'clearContents', params: { id, kind } }),
  image: (_t, id, kind, mode) => sent.push({ method: 'image', params: { id, kind, mode } }),
  note: (_c, text) => notes.push(text),
  portOf: () => [10, 20],
  partIds: () => new Set(['k1', 'k2', 'k3']),
  tunnelColorOf: (id) => (id === 'k1' ? '#e69f00' : null),
};
registerCanvasItems(actions);

const target = (facts: Partial<MenuFacts>, at: Point = [100, 100]): CanvasTarget => ({
  fileId: 'f1', circuitId: 'c1', root: 'c1', path: [], at,
  facts: { circuitId: 'c1', editable: true, kind: 'part', summary: 'S', selection: { ids: [], ordered: true, parts: 0, wires: 0 }, ...facts } as MenuFacts,
});
const find = (es: MenuEntry[], ...path: string[]): MenuEntry => {
  let list = es;
  let e: MenuEntry | undefined;
  for (const p of path) {
    e = list.find((x) => x.label === p);
    assert.ok(e, `${p} in ${labels(list).join(' | ')}`);
    list = e.items ?? [];
  }
  return e!;
};
const run = async (e: MenuEntry) => { e.run?.(); await new Promise((r) => setTimeout(r, 0)); };

test('a gate (v1 EditMenus.gate): Number of Inputs ▸, Size ▸ (the original\'s choices), Facing ▸, Data Bits ▸, Change Gate To ▸, Label…; a port: Attach to ▸, Negate Input; common; Delete', async () => {
  const t = target({
    summary: 'AND #1 · input in0 · 1 bit', id: 'k1',
    part: {
      name: 'AND Gate', display: 'AND Gate', labelAttr: true, facing: true, width: true, inputs: true, gate: true,
      port: { i: 1, name: 'in0', dir: 'in', width: 1, negate: 'negate0', negated: false },
      swaps: ['OR Gate', 'NAND Gate'], options: { size: [{ value: '30', display: 'Narrow' }, { value: '50', display: 'Medium', checked: true }] },
    },
  });
  overlayItems = [{ id: 'influence', label: 'Influence', items: [] }, { id: 'flow', label: 'Signal Flow', items: [] }];
  const m = menuFor('canvas', t, t.facts.summary);
  assert.deepEqual(labels(m), ['# AND #1 · input in0 · 1 bit',
    'Attach to in0', 'Negate Input in0', 'Number of Inputs', 'Size', 'Facing', 'Data Bits', 'Change Gate To', 'Label…', '-',
    'Influence', '-', 'Signal Flow', '-',
    'Cut', 'Copy', 'Duplicate', 'Duplicate N…', 'Rotate', 'Show in Attribute Panel', '-', 'Delete']);
  assert.deepEqual(labels(find(m, 'Attach to in0').items!), ['Pin', 'Constant', 'Probe', 'Tunnel']);
  assert.equal(find(m, 'Duplicate').keys, 'Ctrl+D');
  assert.equal(find(m, 'Delete').keys, 'Del');
  sent.length = 0;
  await run(find(m, 'Size', 'Narrow'));
  await run(find(m, 'Negate Input in0'));
  await run(find(m, 'Change Gate To', 'NAND'));
  await run(find(m, 'Attach to in0', 'Tunnel'));
  await run(find(m, 'Number of Inputs', '4'));
  await run(find(m, 'Duplicate'));
  await run(find(m, 'Delete'));
  assert.deepEqual(sent, [
    { method: 'edit.setAttr', params: { ids: ['k1'], attr: 'size', value: '30', keepSelection: true } },
    { method: 'edit.setAttr', params: { ids: ['k1'], attr: 'negate0', value: 'true', keepSelection: true } },
    { method: 'edit.swapGate', params: { id: 'k1', to: 'NAND Gate' } },
    { method: 'edit.attach', params: { id: 'k1', port: 1, what: 'tunnel' } },
    { method: 'edit.setAttr', params: { ids: ['k1'], attr: 'inputs', value: '4', keepSelection: true } },
    { method: 'edit.duplicate', params: { ids: ['k1'] } },
    { method: 'edit.delete', params: { ids: ['k1'] } },
  ]);
  assert.equal(find(m, 'Size', 'Medium').checked, true);
  // an output port: no Constant
  const out = menuFor('canvas', target({ id: 'k1', part: { ...t.facts.part!, port: { i: 0, name: 'out', dir: 'out', width: 1 } } }));
  assert.deepEqual(labels(find(out, 'Attach to out').items!), ['Pin', 'Probe', 'Tunnel']);
  // a file that cannot change: the edits are off, what only looks stays
  const ro = menuFor('canvas', { ...t, facts: { ...t.facts, editable: false } });
  assert.equal(find(ro, 'Delete').disabled, true);
  assert.equal(find(ro, 'Change Gate To').disabled, true);
  assert.notEqual(find(ro, 'Copy').disabled, true);
  assert.notEqual(find(ro, 'Show in Attribute Panel').disabled, true);
});

test('a pin, a tunnel, a register, a subcircuit (v1 EditMenus): their own items', async () => {
  overlayItems = [];
  const pin = menuFor('canvas', target({ id: 'k2', part: { name: 'Pin', display: 'Pin', label: 'A', labelAttr: true, facing: true, width: true, inputs: false, gate: false, pin: { output: false, tristate: false }, options: { pull: [{ value: 'none', display: 'Unchanged', checked: true }] } } }), 'S');
  assert.deepEqual(labels(pin).slice(1, 7), ['Make Output Pin', 'Data Bits', 'Allow Three-State', 'Add to Cycle View', 'Pull', 'Label…']);
  assert.deepEqual(labels(find(pin, 'Data Bits').items!), ['1 bit', '2 bits', '4 bits', '8 bits', '16 bits', '32 bits']);
  sent.length = 0;
  await run(find(pin, 'Make Output Pin'));
  await run(find(pin, 'Add to Cycle View'));
  await run(find(pin, 'Label…'));
  assert.deepEqual(sent, [
    { method: 'edit.setAttr', params: { ids: ['k2'], attr: 'output', value: 'true', keepSelection: true } },
    { method: 'addRow', params: { at: [10, 20] } },
    { method: 'label', params: { ids: ['k2'], now: 'A' } },
  ]);
  const tun = menuFor('canvas', target({ id: 'k1', part: { name: 'Tunnel', display: 'Tunnel', label: 'clk', labelAttr: true, facing: true, width: true, inputs: false, gate: false, tunnel: { same: ['k7', 'k1', 'k9'], index: 1 }, options: {} } }), 'S');
  assert.deepEqual(labels(tun).slice(1, 5), ['Go to Next "clk" Tunnel', 'Select All "clk" Tunnels (3)', 'Tunnel Color', 'Add to Cycle View']);
  const colors = find(tun, 'Tunnel Color').items!;
  assert.equal(colors[0].label, 'Automatic (from the name, not saved)');
  assert.equal(colors.filter((c) => c.swatch).length, 12);
  assert.equal(colors.find((c) => c.checked)?.swatch?.toLowerCase(), '#e69f00');
  sent.length = 0;
  await run(find(tun, 'Go to Next "clk" Tunnel'));
  await run(find(tun, 'Tunnel Color', 'Automatic (from the name, not saved)'));
  assert.deepEqual(sent, [{ method: 'edit.select', params: { ids: ['k9'] } }, { method: 'reveal', params: { id: 'k9' } }, { method: 'tunnelColor', params: { id: 'k1', color: null } }]);
  const reg = menuFor('canvas', target({ id: 'k3', part: { name: 'Register', display: 'Register', labelAttr: true, facing: false, width: true, inputs: false, gate: false, pcMarked: true, options: {} } }), 'S');
  assert.ok(labels(reg).includes('Unmark as PC'));
  assert.ok(!labels(reg).includes('Rotate'));                   // no Facing: nothing to rotate
  const sub = menuFor('canvas', target({ id: 'k4', part: { name: 'alu', display: 'alu', labelAttr: true, facing: true, width: false, inputs: false, gate: false, subcircuit: { circuitId: 'c2', name: 'alu', defaultAppearance: false, registerFile: true }, options: {} } }), 'S');
  assert.deepEqual(labels(sub).slice(1, 4), ['View alu', '-', 'Unmark Register File']);
  assert.ok(labels(sub).includes('Register Mapping…'));
  const lib = menuFor('canvas', target({ id: 'k4', part: { name: 'x', display: 'x', labelAttr: false, facing: true, width: false, inputs: false, gate: false, subcircuit: { circuitId: 'c9', name: 'x', library: 'lib.circ', defaultAppearance: true, registerFile: false }, options: {} } }), 'S');
  assert.ok(!labels(lib).includes('Mark as Register File'));    // another file's circuit (N-11's Edit Original File)
});

test('the original\'s component items: RAM and ROM (MemMenu), a Splitter\'s Distribute, Hallym MIPS memories (Load Program…)', async () => {
  const part = (over: Record<string, unknown>) => ({ name: 'RAM', display: 'RAM', labelAttr: true, facing: false, width: false, inputs: false, gate: false, options: {}, ...over });
  const ram = menuFor('canvas', target({ id: 'k5', part: part({ memory: 'ram' }) as MenuFacts['part'] }), 'S');
  assert.deepEqual(labels(ram).slice(1, 5), ['Edit Contents…', 'Clear Contents', 'Load Image…', 'Save Image…']);
  sent.length = 0;
  await run(find(ram, 'Edit Contents…'));
  await run(find(ram, 'Load Image…'));
  assert.deepEqual(sent, [{ method: 'contents', params: { id: 'k5', kind: 'ram' } }, { method: 'image', params: { id: 'k5', kind: 'ram', mode: 'load' } }]);
  const rom = menuFor('canvas', { ...target({ id: 'k6', part: part({ name: 'ROM', memory: 'rom' }) as MenuFacts['part'] }), facts: { ...target({}).facts, editable: false, id: 'k6', part: part({ name: 'ROM', memory: 'rom' }) as MenuFacts['part'] } }, 'S');
  assert.equal(find(rom, 'Clear Contents').disabled, true);    // a ROM's contents are the file's
  assert.notEqual(find(rom, 'Save Image…').disabled, true);
  const sp = menuFor('canvas', target({ id: 'k7', part: part({ name: 'Splitter', original: [{ i: 0, text: 'Distribute Ascending', enabled: false }, { i: 1, text: 'Distribute Descending', enabled: true }] }) as MenuFacts['part'] }), 'S');
  assert.equal(find(sp, 'Distribute Ascending').disabled, true);
  assert.ok(labels(sp).includes('Edit Splitter…'));
  sent.length = 0;
  await run(find(sp, 'Distribute Descending'));
  await run(find(sp, 'Edit Splitter…'));
  assert.deepEqual(sent, [{ method: 'edit.originalItem', params: { id: 'k7', index: 1 } }, { method: 'editSplitter', params: { componentId: 'k7' } }]);
  const im = menuFor('canvas', target({ id: 'k8', part: part({ name: 'Instruction Memory', memory: 'program', source: 'prog/sum.hmx' }) as MenuFacts['part'] }), 'S');
  assert.deepEqual(labels(im).slice(1, 3), ['Load Program…', 'Reload sum.hmx']);
  const old = menuFor('canvas', target({ id: 'k8', part: part({ name: 'Instruction Memory', memory: 'program', source: 'prog\\sum.s' }) as MenuFacts['part'] }), 'S');
  assert.deepEqual(labels(old).slice(1, 3), ['Load Program…', 'Load .hmx for sum.s…']);
  sent.length = 0;
  await run(find(old, 'Load .hmx for sum.s…'));
  assert.deepEqual(sent, [{ method: 'loadProgram', params: { id: 'k8', forSource: 'prog\\sum.s' } }]);
});

test('논리설계 및 실험 (A-08, logic/course.ts): no Load Program…, Mark as PC, Mark as Register File or Register Mapping; the rest as in 컴퓨터구조', () => {
  const part = (over: Record<string, unknown>) => ({ name: 'RAM', display: 'RAM', labelAttr: true, facing: false, width: false, inputs: false, gate: false, options: {}, ...over });
  const menus = () => ({
    im: labels(menuFor('canvas', target({ id: 'k8', part: part({ name: 'Instruction Memory', memory: 'program', source: 'prog/sum.hmx' }) as MenuFacts['part'] }), 'S')),
    reg: labels(menuFor('canvas', target({ id: 'k3', part: { name: 'Register', display: 'Register', labelAttr: true, facing: false, width: true, inputs: false, gate: false, pcMarked: false, options: {} } }), 'S')),
    sub: labels(menuFor('canvas', target({ id: 'k4', part: { name: 'alu', display: 'alu', labelAttr: true, facing: true, width: false, inputs: false, gate: false, subcircuit: { circuitId: 'c2', name: 'alu', defaultAppearance: false, registerFile: true }, options: {} } }), 'S')),
    ram: labels(menuFor('canvas', target({ id: 'k5', part: part({ memory: 'ram' }) as MenuFacts['part'] }), 'S')),
  });
  const arch = menus();
  course = 'logic';
  try {
    const logic = menus();
    for (const gone of ['Load Program…', 'Reload sum.hmx']) { assert.ok(arch.im.includes(gone)); assert.ok(!logic.im.includes(gone), gone); }
    assert.ok(arch.reg.includes('Mark as PC') && !logic.reg.includes('Mark as PC'));
    for (const gone of ['Unmark Register File', 'Register Mapping…']) { assert.ok(arch.sub.includes(gone)); assert.ok(!logic.sub.includes(gone), gone); }
    // what is not the course's stays
    assert.deepEqual(logic.ram, arch.ram);
    const rest = (ls: string[]) => ls.filter((l) => l !== 'Mark as PC' && l !== '-');
    assert.deepEqual(rest(logic.reg), rest(arch.reg));
    assert.ok(logic.sub.includes('View alu'));
  } finally {
    course = 'architecture';
  }
});

test('a wire (v1 EditMenus.wire, SplitterMenu, ProbeMenu): its items in v1\'s order with the overlays\' in their places', async () => {
  overlayItems = [
    { id: 'netInfo', label: 'Net Information…' }, { id: 'addRow', label: 'Add to Cycle View' },
    { id: 'highlight', label: 'Highlight Net' }, { id: 'signalGroup', label: 'Signal Group', items: [] },
    { id: 'influence', label: 'Influence', items: [] }, { id: 'flow', label: 'Signal Flow', items: [] },
  ];
  const t = target({ kind: 'wire', id: 'wx', summary: 'Net pc · 32 bits', wire: { width: 32, net: 'pc' } }, [150, 100]);
  const m = menuFor('canvas', t, t.facts.summary);
  assert.deepEqual(labels(m), ['# Net pc · 32 bits',
    'Net Information…', 'Select Whole Net', 'Add to Cycle View', 'Signal Group', 'Find E/X Origin', 'Highlight Net', 'Delete Net Wires', 'Replace Wire with Tunnels…', '-',
    'Split Bits…', 'Take One Bit', '-', 'Attach Probe', '-', 'Influence', '-', 'Signal Flow', '-', 'Delete']);
  assert.equal(find(m, 'Take One Bit').items!.length, 32);
  assert.equal(find(m, 'Take One Bit').items![0].label, '[31]');
  assert.deepEqual(labels(find(m, 'Attach Probe').items!), ['Hexadecimal', 'Signed Decimal', 'Unsigned Decimal', 'Binary']);
  assert.equal(find(m, 'Find E/X Origin').disabled, false);   // the fake net value has an x
  sent.length = 0;
  await run(find(m, 'Select Whole Net'));
  await run(find(m, 'Take One Bit', '[5]'));
  await run(find(m, 'Attach Probe', 'Binary'));
  await run(find(m, 'Delete Net Wires'));
  assert.deepEqual(sent, [
    { method: 'edit.select', params: { ids: ['wx', 'w9'] } },
    { method: 'editSplitter', params: { wire: 'wx', at: [150, 100], bit: 5 } },
    { method: 'edit.probe', params: { wire: 'wx', at: [150, 100], radix: '2' } },
    { method: 'edit.deleteNet', params: { wire: 'wx' } },
  ]);
  // a one-bit wire: no Split Bits; a defined value: no E/X origin to find; several chosen: no Attach Probe
  const one = menuFor('canvas', target({ kind: 'wire', id: 'w1', wire: { width: 1, net: '' }, selection: { ids: ['w1', 'w2'], ordered: true, parts: 0, wires: 2 }, combine: [1, 1] }));
  assert.ok(!labels(one).includes('Split Bits…'));
  assert.equal(find(one, 'Find E/X Origin').disabled, true);
  assert.ok(!labels(one).includes('Attach Probe'));
  assert.ok(labels(one).includes('Combine 2 Wires into One Bus (in the order chosen)'));
  sent.length = 0;
  await run(find(one, 'Combine 2 Wires into One Bus (in the order chosen)'));
  assert.deepEqual(sent, [{ method: 'edit.combineBus', params: { ids: ['w1', 'w2'] } }]);
  // chosen with a rectangle: the order is not known -- off, with the reason
  const rect = menuFor('canvas', target({ kind: 'wire', id: 'w1', wire: { width: 1, net: '' }, selection: { ids: ['w1', 'w2'], ordered: false, parts: 0, wires: 2 }, combine: [1, 1] }));
  const c = find(rect, 'Combine 2 Wires into One Bus (in the order chosen)');
  assert.equal(c.disabled, true);
  assert.match(c.title ?? '', /Shift\+클릭/);
});

test('several parts (v1 EditMenus.multi, ArrangeActions) and an empty spot', async () => {
  overlayItems = [{ id: 'memo', label: 'Add Area Memo…' }, { id: 'flow', label: 'Signal Flow', items: [] }];
  const many = target({ kind: 'many', summary: '3 components', selection: { ids: ['k1', 'w5', 'k2', 'k3'], ordered: true, parts: 3, wires: 1 }, common: { facing: true, width: true, label: true, labels: ['a', 'b', 'c'] } });
  const m = menuFor('canvas', many, many.facts.summary);
  assert.deepEqual(labels(m), ['# 3 components',
    'Change 3 Components', 'Edit Labels of 3 Components…', 'Duplicate N…', 'Align', 'Distribute', 'Select Only Components', 'Select Only Wires', '-',
    'Signal Flow', '-', 'Add Area Memo…', '-', 'Cut Selection', 'Copy Selection', '-', 'Delete Selection']);
  assert.deepEqual(labels(find(m, 'Change 3 Components').items!), ['Facing', 'Data Bits']);
  assert.deepEqual(labels(find(m, 'Align').items!), ['Left', 'Center', 'Right', 'Top', 'Middle', 'Bottom']);
  sent.length = 0;
  await run(find(m, 'Change 3 Components', 'Data Bits', '8 bits'));
  await run(find(m, 'Align', 'Middle'));
  await run(find(m, 'Distribute', 'Vertically'));
  await run(find(m, 'Edit Labels of 3 Components…'));
  await run(find(m, 'Delete Selection'));
  assert.deepEqual(sent, [
    { method: 'edit.setAttr', params: { ids: ['k1', 'k2', 'k3'], attr: 'width', value: '8' } },
    { method: 'edit.align', params: { ids: ['k1', 'k2', 'k3'], mode: 'centerY' } },
    { method: 'edit.distribute', params: { ids: ['k1', 'k2', 'k3'], axis: 'v' } },
    { method: 'labels', params: { ids: ['k1', 'k2', 'k3'], ls: ['a', 'b', 'c'] } },
    { method: 'menu:delete', params: {} },
  ]);
  const two = menuFor('canvas', target({ kind: 'many', selection: { ids: ['k1', 'k2'], ordered: true, parts: 2, wires: 0 }, common: { facing: false, width: false, label: false, labels: [] } }));
  assert.ok(!labels(two).includes('Distribute'));
  assert.ok(!labels(two).some((l) => l.startsWith('Change ')));
  const empty = menuFor('canvas', target({ kind: 'empty', summary: 'Empty spot · main', probes: ['k5', 'k6'] }), 'Empty spot · main');
  assert.deepEqual(labels(empty), ['# Empty spot · main', 'Paste', 'Fit to Window', '-', 'Select All Probes (2)', 'Delete All Probes (2)', '-', 'Signal Flow', '-', 'Add Area Memo…']);
  sent.length = 0;
  await run(find(empty, 'Paste'));
  await run(find(empty, 'Delete All Probes (2)'));
  assert.deepEqual(sent, [{ method: 'menu:paste', params: {} }, { method: 'edit.deleteProbes', params: {} }]);
});

test('what the engine answers comes to the status bar in Korean: refused (W-05), no room for a probe, arranging', async () => {
  let answer: EditResult = { changed: false, outcome: 'noRoom' };
  registerCanvasItems({ ...actions, edit: async () => answer });
  try {
    notes.length = 0;
    const w = menuFor('canvas', target({ kind: 'wire', id: 'w1', wire: { width: 8, net: '' } }, [1, 1]));
    await run(find(w, 'Attach Probe', 'Hexadecimal'));
    answer = { changed: false, outcome: 'refused' };
    const g = menuFor('canvas', target({ id: 'k1', part: { name: 'AND Gate', display: 'AND Gate', labelAttr: true, facing: true, width: true, inputs: true, gate: true, swaps: ['OR Gate'], options: {} } }));
    await run(find(g, 'Change Gate To', 'OR'));
    const many = menuFor('canvas', target({ kind: 'many', selection: { ids: ['k1', 'k2'], ordered: true, parts: 2, wires: 0 }, common: { facing: false, width: false, label: false, labels: [] } }));
    answer = { changed: false, outcome: 'connected' };
    await run(find(many, 'Align', 'Left'));
    answer = { changed: false, outcome: 'nothing' };
    await run(find(many, 'Align', 'Left'));
    answer = { changed: false, outcome: 'tooWide' };
    const comb = menuFor('canvas', target({ kind: 'wire', id: 'w1', wire: { width: 32, net: '' }, selection: { ids: ['w1', 'w2'], ordered: true, parts: 0, wires: 2 }, combine: [32, 8] }));
    await run(find(comb, 'Combine 2 Wires into One Bus (in the order chosen)'));
    assert.deepEqual(notes, [
      '이 선 옆에 빈 자리가 없습니다. 가까운 부품을 조금 옮긴 뒤 다시 해 보세요.',
      '그렇게 두면 선이나 포트가 다른 연결에 닿아, 바꾸지 않았습니다.',
      '선이 이어진 부품은 옮기지 않습니다(배선하기 전에 정렬합니다).',
      '이미 그 자리에 있습니다.',
      '고른 선의 폭을 모두 더하면 40비트입니다. Splitter는 32비트까지입니다.',
    ]);
  } finally {
    registerCanvasItems(actions);
  }
});
