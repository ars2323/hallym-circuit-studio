/* src/renderer/app/logic/circuits.ts and appearance.ts (N-11, D-153): the
   words of circuits, appearances and libraries (Korean sentences, English
   names, no particle right after a name), the tabs of one name told apart,
   a port moved in its list; the appearance editor's gestures as the
   original's drawing tools compute them. */

import assert from 'node:assert/strict';
import { test } from 'node:test';

import {
  createRefusal, distinguishers, IMPACT_PLACES, impactPlaces, impactSentence, importedText, libraryUpdatedText, loadRefusal, moved,
  nameProblem, pinAddText, pinPreviewText, planLines, portImpactText, removeRefusal, saveCutLines, saveCutSentence, simTree, standaloneText, unloadRefusal,
} from '../../src/renderer/app/logic/circuits.ts';
import {
  closes, curveControl, dragged, handleDelta, handleSize, lineEnd, moveDelta, onCurve, poly, pressCount, rectFromDrag, snap, snap8, toolAttributes, toolAttrs,
} from '../../src/renderer/app/logic/appearance.ts';

// A Latin name, a closing quote or bracket right before a Korean particle reads wrong (D-135 14).
const PARTICLE_AFTER_NAME = /[A-Za-z0-9_)\]`'"][을를이가은는의에과와로도만]/;
const clean = (s: string) => assert.ok(!PARTICLE_AFTER_NAME.test(s), `a particle right after a name: ${s}`);

const circuits = [{ circuitId: 'c1', name: 'main' }, { circuitId: 'c2', name: 'alu' }];

test('circuit names: the original\'s checks (a name, not one there already; renaming may keep its own)', () => {
  assert.equal(nameProblem('  ', circuits), '회로 이름을 적으세요.');
  assert.equal(nameProblem('alu', circuits), '이 파일에 같은 이름의 회로가 이미 있습니다.');
  assert.equal(nameProblem(' alu ', circuits), '이 파일에 같은 이름의 회로가 이미 있습니다.');
  assert.equal(nameProblem('alu', circuits, 'c2'), null);
  assert.equal(nameProblem('alu32', circuits), null);
  assert.equal(createRefusal('nameMissing'), '회로 이름을 적으세요.');
  assert.match(createRefusal('nameTaken')!, /이미 있습니다/);
  assert.equal(createRefusal('other'), null);
});

test('refusals: Remove Circuit, Load Library, Unload Library -- the command\'s name, then a Korean sentence', () => {
  for (const s of [removeRefusal('lastCircuit', 'alu'), removeRefusal('inUse', 'alu')]) {
    assert.match(s!, /^Remove Circuit: /);
    assert.match(s!, /alu 회로를/);
    clean(s!);
  }
  assert.equal(removeRefusal('x', 'alu'), null);
  for (const r of ['self', 'circular', 'noLibraryClass', 'notFound', 'loadFailed', 'readOnly']) {
    assert.match(loadRefusal(r)!, /^Load Library: /);
    clean(loadRefusal(r)!);
  }
  assert.equal(loadRefusal(undefined), null);
  assert.match(unloadRefusal('inUse', 'datapath')!, /^Unload Library: datapath 회로가/);
  assert.match(unloadRefusal('toolbar')!, /도구 모음/);
  assert.equal(unloadRefusal('x'), null);
});

test('impact: how many instances and connections, the places eight at most and how many more', () => {
  const where = Array.from({ length: 11 }, (_, i) => `main › half #${i + 1}`);
  const s = impactSentence({ instances: 11, connections: 22, where });
  assert.match(s, /인스턴스 11개에서 연결 22곳이 끊어집니다/);
  clean(s);
  const places = impactPlaces({ instances: 11, connections: 22, where }).split('\n');
  assert.equal(places.length, IMPACT_PLACES + 1);
  assert.equal(places[0], 'main › half #1');
  assert.equal(places.at(-1), '… (+3)');
  assert.equal(impactPlaces({ instances: 1, connections: 1, where: ['main › alu #1'] }), 'main › alu #1');
});

test('a pin edit that broke instance connections; a subcircuit on its own; the pin previews', () => {
  const broken = portImpactText({ fileId: 'f1', circuitId: 'c2', name: 'half', broken: 2, kept: 0 });
  assert.equal(broken, 'half 회로의 핀을 바꿔 인스턴스 연결 2개가 끊겼습니다');
  const kept = portImpactText({ fileId: 'f1', circuitId: 'c2', name: 'half', broken: 2, kept: 1 });
  assert.match(kept, / · 1개는 선을 이어 되살렸습니다/);
  const band = standaloneText('regfile', 'main');
  assert.match(band, /^regfile 회로를 따로 열었습니다\. main 회로 안에서/);
  for (const s of [broken, kept, band]) clean(s);
  assert.equal(pinPreviewText(0, 0), null);
  assert.match(pinPreviewText(3, 2)!, /인스턴스 2개에서 연결 3개가 끊길 수 있습니다/);
  assert.equal(pinAddText('alu', 0, 0, true), null);
  assert.match(pinAddText('alu', 2, 3, true)!, /모양이 바뀌어 이어진 포트 3개가 움직일 수/);
  assert.match(pinAddText('alu', 2, 3, false)!, /사용자 모양이라 기존 포트는 그대로/);
  for (const s of [pinPreviewText(3, 2)!, pinAddText('alu', 2, 3, true)!, pinAddText('alu', 2, 3, false)!]) clean(s);
});

test('libraries and other files: a library saved elsewhere came in; what a save would break; an import\'s plan', () => {
  const u = libraryUpdatedText('lib.circ');
  assert.equal(u, '라이브러리 lib.circ 파일이 저장되어 새 버전을 다시 불러왔습니다 · 시뮬레이션은 Reset 상태입니다');
  clean(u);
  const cuts = [{ fileId: 'f2', file: 'cpu.circ', instances: ['fa0', 'fa1', 'fa2', 'fa3', 'fa4'], connections: 6 }, { fileId: 'f3', file: 'b.circ', instances: ['adder'], connections: 1 }];
  assert.equal(saveCutLines(cuts), 'cpu.circ: fa0, fa1, fa2, fa3 … — 6\nb.circ: adder — 1');
  assert.match(saveCutSentence(cuts), /인스턴스 연결 7곳이 끊깁니다/);
  const plan = planLines({ order: [{ name: 'inner', as: 'inner' }, { name: 'main', as: 'main-2' }], skipped: ['main › LED #1'] });
  assert.equal(plan, 'inner\nmain  →  main-2\n\nLeft out (1):\nmain › LED #1');
  assert.equal(importedText(2, 'src.circ'), '가져왔습니다 · 회로 2개 · src.circ');
});

test('same names: the shortest folder tail tells them apart (v1 V-05); unsaved and unique names get none', () => {
  const d = distinguishers([
    { id: 'a', name: 'lab.circ', path: '/home/s/hw1/lab.circ' },
    { id: 'b', name: 'lab.circ', path: '/home/s/hw2/lab.circ' },
    { id: 'c', name: 'cpu.circ', path: '/home/s/hw1/cpu.circ' },
    { id: 'd', name: 'lab.circ', path: null },
  ]);
  assert.deepEqual([...d.entries()], [['a', 'hw1'], ['b', 'hw2']]);
  // the same folder name: one level more
  const two = distinguishers([
    { id: 'a', name: 'x.circ', path: '/u/one/tests/x.circ' },
    { id: 'b', name: 'x.circ', path: '/u/two/tests/x.circ' },
  ]);
  assert.deepEqual([...two.entries()], [['a', 'one/tests'], ['b', 'two/tests']]);
  // Windows paths
  const win = distinguishers([
    { id: 'a', name: 'x.circ', path: 'C:\\Users\\s\\a\\x.circ' },
    { id: 'b', name: 'x.circ', path: 'C:\\Users\\s\\b\\x.circ' },
  ]);
  assert.deepEqual([...win.values()], ['a', 'b']);
  assert.equal(distinguishers([{ id: 'a', name: 'x.circ', path: '/a/x.circ' }]).size, 0);
});

test('a port moved in its side\'s list', () => {
  assert.deepEqual(moved(['a', 'b', 'c'], 0, 2), ['b', 'c', 'a']);
  assert.deepEqual(moved(['a', 'b', 'c'], 2, 0), ['c', 'a', 'b']);
  assert.deepEqual(moved(['a', 'b', 'c'], 1, 1), ['a', 'b', 'c']);
  assert.deepEqual(moved(['a', 'b', 'c'], 0, 3), ['a', 'b', 'c']);
});

// ---- the appearance editor's gestures ----

test('the grid: AppearanceCanvas.snapX, halves away from zero', () => {
  assert.deepEqual([snap(4), snap(5), snap(14), snap(15), snap(-4), snap(-5), snap(-15)], [0, 10, 10, 20, -0, -10, -20]);
});

test('a rectangle dragged (RectangularTool.computeBounds): either way, Shift a square, Alt from the middle, Ctrl the grid', () => {
  assert.deepEqual(rectFromDrag([10, 10], [40, 30], {}), [10, 10, 30, 20]);
  assert.deepEqual(rectFromDrag([40, 30], [10, 10], {}), [10, 10, 30, 20]);
  assert.deepEqual(rectFromDrag([10, 10], [40, 30], { shift: true }), [10, 10, 20, 20]);
  assert.deepEqual(rectFromDrag([10, 10], [0, -20], { shift: true }), [0, 0, 10, 10]);
  assert.deepEqual(rectFromDrag([50, 50], [60, 70], { alt: true }), [40, 30, 20, 40]);
  assert.deepEqual(rectFromDrag([50, 50], [60, 70], { alt: true, shift: true }), [40, 40, 20, 20]);
  assert.deepEqual(rectFromDrag([12, 13], [37, 44], { ctrl: true }), [10, 10, 30, 30]);
  assert.equal(rectFromDrag([10, 10], [10, 10], {}), null);
  assert.equal(rectFromDrag([10, 10], [40, 10], {}), null, 'no height: no shape');
});

test('45° (LineUtil.snapTo8Cardinals) and a line\'s end: Shift first, then Ctrl', () => {
  assert.deepEqual(snap8([0, 0], [100, 10]), [100, 0]);
  assert.deepEqual(snap8([0, 0], [10, 100]), [0, 100]);
  assert.deepEqual(snap8([0, 0], [-100, 10]), [-100, 0]);
  assert.deepEqual(snap8([0, 0], [50, 40]), [45, 45]);
  assert.deepEqual(snap8([0, 0], [-50, 40]), [-45, 45]);
  assert.deepEqual(snap8([0, 0], [-50, -40]), [-45, -45]);
  assert.deepEqual(snap8([0, 0], [50, -40]), [45, -45]);
  assert.deepEqual(snap8([0, 0], [0, 30]), [0, 30]);
  assert.deepEqual(lineEnd([0, 0], [53, 41], {}), [53, 41]);
  assert.deepEqual(lineEnd([0, 0], [53, 41], { shift: true }), [47, 47]);
  assert.deepEqual(lineEnd([0, 0], [53, 41], { shift: true, ctrl: true }), [50, 50]);
});

test('moving what is chosen (SelectTool): a drag after 2 units; Ctrl snaps the top-left handle; Shift one axis', () => {
  assert.equal(dragged(1, 1), false);
  assert.equal(dragged(2, 1), true);
  assert.deepEqual(moveDelta(13, 4, [[21, 32], [60, 70]], {}), [13, 4]);
  assert.deepEqual(moveDelta(13, 4, [[21, 32], [60, 70]], { ctrl: true }), [9, 8]);
  assert.deepEqual(moveDelta(13, 4, [], { shift: true }), [13, 0]);
  assert.deepEqual(moveDelta(3, 14, [], { shift: true }), [0, 14]);
  assert.deepEqual(handleDelta([21, 32], 13, 4, {}), [13, 4]);
  assert.deepEqual(handleDelta([21, 32], 13, 4, { ctrl: true }), [9, 8]);
  assert.deepEqual([handleSize(1), handleSize(4), handleSize(0.25)], [8, 4, 16]);
});

test('polygons (PolyTool): back on the first point ends one of three points or more; the same point twice is one', () => {
  assert.equal(closes([[0, 0], [10, 0]]), false);
  assert.equal(closes([[0, 0], [10, 0], [10, 10], [1, 1]]), true);
  assert.equal(closes([[0, 0], [10, 0], [10, 10], [2, 1]]), false);
  assert.deepEqual(poly([[0, 0], [0, 0], [10, 0], [10, 0], [10, 10]]), [[0, 0], [10, 0], [10, 10]]);
});

test('a curve\'s control (CurveTool): as pointed, Ctrl on the grid, Shift on the ends\' bisector, Alt through the point', () => {
  assert.deepEqual(curveControl([0, 0], [100, 0], [37, 44], {}), [37, 44]);
  assert.deepEqual(curveControl([0, 0], [100, 0], [37, 44], { ctrl: true }), [40, 40]);
  assert.deepEqual(curveControl([0, 0], [100, 0], [37, 44], { shift: true }), [50, 44]);
  const c = curveControl([0, 0], [100, 0], [50, 40], { alt: true });
  assert.deepEqual(c, [50, 80]);
  assert.deepEqual(onCurve([0, 0], c, [100, 0], 0.5), [50, 40]);
});

test('the drawing tools\' attributes (DrawAttr lists, the fill list by paint type) and what an add sends', () => {
  assert.deepEqual(toolAttributes('Text', 'stroke'), ['font', 'align', 'fill']);
  assert.deepEqual(toolAttributes('Line', 'both'), ['stroke-width', 'stroke']);
  assert.deepEqual(toolAttributes('Rectangle', 'stroke'), ['paintType', 'stroke-width', 'stroke']);
  assert.deepEqual(toolAttributes('Oval', 'fill'), ['paintType', 'fill']);
  assert.deepEqual(toolAttributes('Rounded Rectangle', 'both'), ['paintType', 'stroke-width', 'stroke', 'fill', 'rx']);
  assert.deepEqual(toolAttributes('Select', 'both'), []);
  const v = { font: 'SansSerif plain 12', align: 'center', paintType: 'fill', 'stroke-width': '1', stroke: '#000000', fill: '#ffffff', rx: '10' };
  assert.deepEqual(toolAttrs('Rectangle', v), { paintType: 'fill', fill: '#ffffff' });
  assert.deepEqual(toolAttrs('Text', v), { font: 'SansSerif plain 12', align: 'center', fill: '#ffffff' });
});

test('presses in a row (PolyTool\'s click count): within 500 ms and 4 px counts on, else one again', () => {
  const a = pressCount(null, 1000, 100, 100);
  assert.equal(a.n, 1);
  const b = pressCount(a, 1300, 103, 96);
  assert.equal(b.n, 2);
  assert.equal(pressCount(b, 1700, 103, 96).n, 3);
  assert.equal(pressCount(b, 1801, 103, 96).n, 1);   // too late
  assert.equal(pressCount(b, 1400, 108, 96).n, 1);   // too far
  assert.equal(pressCount(b, 1400, 103, 91).n, 1);
});

test('the Simulation Tree: main, then each instance under its parent -- its label, else circuit(x,y) -- by name, then place; no recursion', () => {
  const names: Record<string, string> = { c1: 'main', c2: 'datapath', c3: 'alu' };
  const parts: Record<string, { id: string; label: string; loc: [number, number]; subcircuit: string }[]> = {
    c1: [{ id: 'k9', label: '', loc: [300, 200], subcircuit: 'c2' }, { id: 'k2', label: '', loc: [100, 200], subcircuit: 'c2' }, { id: 'k5', label: 'ALU0', loc: [0, 0], subcircuit: 'c3' }],
    c2: [{ id: 'k7', label: '', loc: [50, 60], subcircuit: 'c3' }, { id: 'k8', label: '', loc: [10, 10], subcircuit: 'c2' }],   // itself: skipped
    c3: [],
  };
  const t = simTree('c1', (id) => names[id], (id) => parts[id]);
  assert.deepEqual(t.map((n) => '  '.repeat(n.depth) + n.text), [
    'main', '  ALU0', '  datapath(100,200)', '    alu(50,60)', '  datapath(300,200)', '    alu(50,60)',
  ]);
  const deep = t[3];
  assert.deepEqual([deep.ids, deep.names, deep.circuits, deep.circuit], [['k2', 'k7'], ['datapath', 'alu'], ['c2', 'c3'], 'c3']);
  assert.deepEqual([t[1].names], [['ALU0']]);
  // a circuit not asked yet: no children under it
  assert.deepEqual(simTree('c1', (id) => names[id], () => undefined).map((n) => n.text), ['main']);
});
