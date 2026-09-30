/* The shell's logic (N-17, D-158): the » rule's order (shared/overflow.ts),
   the keys the student can change (app/logic/keys.ts), the menu
   (app/logic/menus.ts), the Changed chip (app/logic/facts.ts), the
   examples (src/main/examples.ts), the status bar's keep order. */

import assert from 'node:assert/strict';
import path from 'node:path';
import { test } from 'node:test';

import { EXAMPLE_COURSE, EXAMPLES, exampleResources, examplesDir, isCourse, isExample, TUTORIALS, tutorialDir } from '../../src/main/examples.ts';
import { changedChip } from '../../src/renderer/app/logic/facts.ts';
import {
  COMMANDS, FIXED, fixedTable, isChanged, keyText, onKeysChanged, pressStroke, refusal, resetAll, resetKey, route, sameStroke, setKey, strokes, strokeText,
} from '../../src/renderer/app/logic/keys.ts';
import { appMenu, menuIds, type MenuSpec, type MenuState } from '../../src/renderer/app/logic/menus.ts';
import { leftBar, overflowCount, overflowOrder } from '../../src/renderer/shared/overflow.ts';

test('the » rule: the least kept first, and among equals the rightmost; the menu in the bar\'s order; the fewest that fit', () => {
  const units = [{ keep: 6 }, { keep: 2 }, { keep: 9 }, { keep: 2 }, { keep: 3 }];
  assert.deepEqual(overflowOrder(units), [3, 1, 4, 0, 2]);
  assert.deepEqual(leftBar(units, 2), [1, 3]);
  assert.deepEqual(leftBar(units, 3), [1, 3, 4]);
  assert.deepEqual(leftBar(units, 0), []);
  assert.equal(overflowCount(5, (k) => k >= 2), 2);
  assert.equal(overflowCount(5, () => true), 0);
  assert.equal(overflowCount(5, () => false), 5);
});

const press = (code: string, key: string, mods: { ctrl?: boolean; shift?: boolean; alt?: boolean } = {}) =>
  ({ code, key, ctrlKey: !!mods.ctrl, shiftKey: !!mods.shift, altKey: !!mods.alt });

test('the keys: fourteen commands (v1 I-183) with their defaults, written as the student reads them; the fixed ones grouped', () => {
  assert.deepEqual(COMMANDS.map((c) => c.id), ['rotate', 'label', 'redo', 'zoomIn', 'zoomOut', 'zoomFit', 'zoom100', 'zoomSel', 'influence', 'influenceLess', 'influenceMore', 'flowToggle', 'find', 'palette']);
  assert.deepEqual(COMMANDS.map((c) => keyText(c.id)), ['R', 'F2', 'Ctrl+Y', 'Ctrl+=', 'Ctrl+-', 'Ctrl+0', 'Ctrl+1', 'F', 'I', '[', ']', 'Ctrl+Shift+F', 'Ctrl+F', 'Ctrl+K']);
  assert.equal(strokeText({ code: 'Numpad0', key: '0', ctrl: true }), 'Ctrl+Num 0');
  assert.equal(strokeText({ code: 'Slash', key: '/', shift: true }), '?');
  const table = fixedTable();
  assert.deepEqual(table.find((r) => r.name === 'Tool'), { name: 'Tool', keys: 'Ctrl+2 … Ctrl+9' });
  assert.deepEqual(table.find((r) => r.name === 'Move One Step'), { name: 'Move One Step', keys: '← · → · ↑ · ↓' });
  assert.deepEqual(table.find((r) => r.name === 'Close File'), { name: 'Close File', keys: 'Ctrl+W · Ctrl+Shift+W' });
  // Every fixed key is a key no command has by default (they never meet).
  for (const f of FIXED) for (const c of COMMANDS) assert.ok(!c.defaults.some((d) => sameStroke(d, f.stroke)), `${c.id} / ${f.name}`);
  // A modifier alone is not a key; a letter is its lower case; Cmd is Ctrl
  assert.equal(pressStroke(press('ShiftLeft', 'Shift', { shift: true })), null);
  assert.equal(pressStroke({ ...press('ControlLeft', 'Control'), ctrlKey: true }), null);
  assert.deepEqual(pressStroke(press('KeyG', 'G', { ctrl: true, shift: true })), { code: 'KeyG', key: 'g', ctrl: true, shift: true, alt: undefined });
  assert.deepEqual(pressStroke({ ...press('KeyG', 'g'), metaKey: true })?.ctrl, true);
});

test('a key given: refused if fixed or another command\'s (with the reason, the name before a colon), taking the place of every default, for this run only', () => {
  resetAll();
  let told = 0;
  const off = onKeysChanged(() => { told += 1; });
  assert.equal(refusal('find', { code: 'KeyS', key: 's', ctrl: true }), 'Ctrl+S: 바꿀 수 없는 키입니다(Save)');
  assert.equal(refusal('find', { code: 'KeyK', key: 'k', ctrl: true }), 'Ctrl+K: 이미 쓰는 키입니다(Search)');
  assert.equal(refusal('find', { code: 'KeyI', key: 'i', shift: true }), 'Shift+I: 이미 쓰는 키입니다(Show Influence)');   // the influence's other way
  assert.equal(refusal('rotate', { code: 'KeyT', key: 't', shift: true }), 'Shift+T: 이 명령은 Shift를 더해 반대로 하므로 Shift 없는 키를 고르세요');
  for (const why of [refusal('find', { code: 'KeyS', key: 's', ctrl: true })!]) assert.doesNotMatch(why, /[A-Za-z)][은는이가을를]/);
  assert.equal(setKey('redo', { code: 'KeyG', key: 'g', ctrl: true }), null);
  assert.deepEqual(strokes('redo'), [{ code: 'KeyG', key: 'g', ctrl: true }]);   // Ctrl+Shift+Z goes too
  assert.equal(keyText('redo'), 'Ctrl+G');
  assert.equal(isChanged('redo'), true);
  assert.equal(told, 1);
  // the default again is no change
  assert.equal(setKey('redo', { code: 'KeyY', key: 'y', ctrl: true }), null);
  assert.equal(isChanged('redo'), true);   // redo has two defaults: one of them alone is a change
  resetKey('redo');
  assert.equal(isChanged('redo'), false);
  assert.equal(setKey('find', { code: 'KeyF', key: 'f', ctrl: true }), null);
  assert.equal(isChanged('find'), false);   // its only default: nothing changed
  resetAll();
  off();
});

test('a press routed: the new key becomes the command\'s own default (Shift keeps the other way); its old keys do nothing; everything else passes', () => {
  resetAll();
  const p = (code: string, key: string, mods: { ctrl?: boolean; shift?: boolean } = {}) => pressStroke(press(code, key, mods))!;
  assert.deepEqual(route(p('KeyF', 'f', { ctrl: true })), { kind: 'pass' });
  setKey('find', { code: 'KeyG', key: 'g', ctrl: true });
  setKey('rotate', { code: 'KeyT', key: 't' });
  assert.deepEqual(route(p('KeyG', 'g', { ctrl: true })), { kind: 'remap', id: 'find', to: { code: 'KeyF', key: 'f', ctrl: true } });
  assert.deepEqual(route(p('KeyF', 'f', { ctrl: true })), { kind: 'block', id: 'find' });
  assert.deepEqual(route(p('KeyT', 't')), { kind: 'remap', id: 'rotate', to: { code: 'KeyR', key: 'r' } });
  assert.deepEqual(route(p('KeyT', 'T', { shift: true })), { kind: 'remap', id: 'rotate', to: { code: 'KeyR', key: 'r', shift: true } });
  assert.deepEqual(route(p('KeyR', 'r')), { kind: 'block', id: 'rotate' });
  assert.deepEqual(route(p('KeyR', 'R', { shift: true })), { kind: 'block', id: 'rotate' });
  assert.deepEqual(route(p('KeyF', 'F', { ctrl: true, shift: true })), { kind: 'pass' });   // Signal Flow on Click: its own
  assert.deepEqual(route(p('KeyK', 'k', { ctrl: true })), { kind: 'pass' });
  resetAll();
  assert.deepEqual(route(p('KeyG', 'g', { ctrl: true })), { kind: 'pass' });
});

const state = (o: Partial<MenuState> = {}): MenuState => ({
  file: true, ready: true, simOn: true, ticking: false, hz: 1, frequencies: [['1 Hz', 1], ['64 Hz', 64]], recent: [], examples: [], files: [],
  project: { editable: true, index: 0, count: 2, main: true }, tool: 'Edit', ...o,
});
const find = (specs: MenuSpec[], ...labels: string[]): MenuSpec => {
  let at: MenuSpec | undefined;
  let list = specs;
  for (const l of labels) { at = list.find((s) => s.label === l); assert.ok(at, labels.join(' › ')); list = at.items ?? []; }
  return at!;
};

test('the menu: File, Edit, Project, Simulate, Window, Help (the original\'s); keys as the keys are now; off while it cannot be done', () => {
  resetAll();
  const m = appMenu(state({ recent: [{ id: 'f1', name: 'lab3.circ' }], examples: EXAMPLES.map((n) => ({ id: n, name: n })), files: [{ id: 'a', name: 'x.circ', active: true }] }));
  assert.deepEqual(m.map((s) => s.label), ['File', 'Edit', 'Project', 'Simulate', 'Window', 'Help']);
  // Project: the circuit on show (the first of two, the main one); Analyze Circuit and Statistics are N-21's
  assert.equal(find(m, 'Project', 'Move Circuit Up').disabled, true);
  assert.equal(find(m, 'Project', 'Move Circuit Down').disabled, undefined);
  assert.equal(find(m, 'Project', 'Set As Main Circuit').disabled, true);
  assert.equal(find(m, 'Project', 'Remove Circuit').disabled, undefined);
  assert.equal(find(m, 'Project', 'Load Library', 'JAR Library…').id, 'project.loadJar');
  assert.equal(find(appMenu(state({ project: { editable: true, index: 1, count: 2, main: false } })), 'Project', 'Set As Main Circuit').disabled, undefined);
  assert.equal(find(appMenu(state({ project: { editable: false, index: 1, count: 2, main: false } })), 'Project', 'Add Circuit…').disabled, true);
  assert.equal(find(appMenu(state({ project: { editable: true, index: 0, count: 1, main: true } })), 'Project', 'Remove Circuit').disabled, true);
  // Edit › Tool: the tool in hand ticked, the Menu Tool among them (I-84)
  assert.deepEqual(find(m, 'Edit', 'Tool').items?.map((i) => i.label), ['Edit Tool', 'Poke Tool', 'Wiring Tool', 'Text Tool', 'Menu Tool']);
  assert.equal(find(m, 'Edit', 'Tool', 'Edit Tool').checked, true);
  assert.equal(find(appMenu(state({ tool: 'Menu' })), 'Edit', 'Tool', 'Menu Tool').checked, true);
  assert.equal(find(m, 'Edit', 'Tool', 'Menu Tool').id, 'edit.tool:Menu');
  assert.equal(find(m, 'File', 'Save As…').key, 'Ctrl+Shift+S');
  assert.equal(find(m, 'File', 'Close').key, 'Ctrl+W');
  assert.equal(find(m, 'Edit', 'Redo').key, 'Ctrl+Y');
  assert.equal(find(m, 'Simulate', 'Reset Simulation').key, 'Ctrl+R');
  assert.equal(find(m, 'Simulate', 'Step Simulation').disabled, true);   // only while the simulation is off
  assert.equal(find(m, 'Simulate', 'Simulation Enabled').checked, true);
  assert.equal(find(m, 'Simulate', 'Tick Frequency', '1 Hz').checked, true);
  assert.equal(find(m, 'File', 'Open Recent', 'lab3.circ').id, 'file.recent:f1');
  assert.equal(find(m, 'Help', 'Examples', 'demo-datapath.circ').id, 'help.example:demo-datapath.circ');
  assert.equal(find(m, 'Window', 'x.circ').checked, true);
  setKey('redo', { code: 'KeyG', key: 'g', ctrl: true });
  assert.equal(find(appMenu(state()), 'Edit', 'Redo').key, 'Ctrl+G');
  resetAll();
  // No file: what needs one is off; nothing opened this run: no recent files
  const none = appMenu(state({ file: false }));
  for (const [menu, item] of [['File', 'Save'], ['Edit', 'Undo'], ['Simulate', 'Reset Simulation'], ['File', 'Close'], ['Project', 'Add Circuit…'], ['Project', 'Edit Circuit Layout'], ['Edit', 'Tool']]) assert.equal(find(none, menu, item).disabled, true, item);
  assert.equal(find(none, 'File', 'Open Recent').disabled, true);
  assert.equal(find(none, 'File', 'New').disabled, undefined);
  // The engine down: New and Open off too
  assert.equal(find(appMenu(state({ ready: false })), 'File', 'New').disabled, true);
  // Every item runs something (menubar.ts's ids are the app's: tests/e2e/shell.e2e.ts runs them)
  const ids = menuIds(m);
  assert.ok(ids.includes('file.saveAs') && ids.includes('window.minimize') && ids.includes('help.keys') && ids.includes('help.about'));
  assert.equal(new Set(ids).size, ids.length);
});

test('the Changed chip: the registers the cycle changed, PC never one, three names at most then how many more', () => {
  const row = (key: string, changed: boolean) => ({ key, name: key, number: 0, group: 'g', value: '0', changed });
  assert.equal(changedChip([row('PC', true), row('$t0', false)]), null);
  assert.deepEqual(changedChip([row('PC', true), row('$t0', true)]), { shown: ['$t0'], more: 0 });
  assert.deepEqual(changedChip(['$t0', '$t1', '$t2', '$t3', '$t4'].map((k) => row(k, true))), { shown: ['$t0', '$t1', '$t2'], more: 2 });
});

test('Help › Examples: the six circuits by name only (three a course, A-08), in the package\'s resources or the repository\'s tests/circ', () => {
  assert.deepEqual([...EXAMPLES], ['adder-1bit.circ', 'ripple-carry-4bit.circ', 'counter-4bit.circ', 'demo-datapath.circ', 'console-demo.circ', 'stack-demo.circ']);
  assert.deepEqual(EXAMPLES.map((n) => EXAMPLE_COURSE[n]), ['logic', 'logic', 'logic', 'architecture', 'architecture', 'architecture']);
  assert.equal(isExample('demo-datapath.circ'), true);
  for (const bad of ['../demo-datapath.circ', '/etc/passwd', 'gates.circ', '']) assert.equal(isExample(bad), false, bad);
  assert.equal(examplesDir('/r', '/repo'), path.join('/r', 'examples'));
  assert.equal(examplesDir(null, '/repo'), path.join('/repo', 'tests/circ'));
  assert.equal(examplesDir(null, null), null);
  assert.deepEqual(exampleResources('/repo').map((r) => r.to), [...EXAMPLES.map((n) => `examples/${n}`),
    // and the tutorials' examples (N-18): each course's, the architecture's program beside it
    'tutorial/tutorial-logic.circ', 'tutorial/tutorial-mips.circ', 'tutorial/tutorial.hmx', 'tutorial/tutorial.s']);
  assert.deepEqual(TUTORIALS, { logic: ['tutorial-logic.circ'], architecture: ['tutorial-mips.circ', 'tutorial.hmx', 'tutorial.s'] });
  assert.equal(isCourse('logic'), true);
  assert.equal(isCourse('architecture'), true);
  for (const bad of ['../logic', 'mips', '', null]) assert.equal(isCourse(bad), false, String(bad));
  assert.equal(tutorialDir('/r', '/repo'), path.join('/r', 'tutorial'));
  assert.equal(tutorialDir(null, '/repo'), path.join('/repo', 'tests/tutorial'));
  assert.equal(tutorialDir(null, null), null);
});
