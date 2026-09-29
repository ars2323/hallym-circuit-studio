/* The rest of v1's commands (N-21, D-162): Undo History's rows, Analyze
   Circuit's and Create Submission's words, Get Circuit Statistics' totals,
   Export Image's name and selection, and the main process's picture work
   (src/main/pictures.ts: fonts embedded, the PNG's scale, the PDF's page,
   Print's header and pages). */

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { test } from 'node:test';

import type { Analysis, Snapshot, SubmissionPlan } from '../../src/main/protocol.ts';
import { embedFonts, FACES, fontFaceCss, pdfHtml, pngScale, PNG_MAX_PIXELS, PNG_MAX_SIDE, printHeader, printHtml, svgSize, usedFaces } from '../../src/main/pictures.ts';
import { WINDOW_METHODS } from '../../src/main/protocol.ts';
import { circuitItems } from '../../src/renderer/app/circuits.ts';
import {
  analyzeProblemText, analyzeSourceText, EXPORT_SENTENCE, expressionParts, HISTORY_HINT, PRINT_NONE, PRINT_SENTENCE, STATISTICS_HINT, SUBMISSION_HINT, historyLines, pictureName, PRINT_HEADER, statisticsLines, subSnapshot, submissionChecks,
} from '../../src/renderer/app/logic/project-tools.ts';
import { COMMANDS, search } from '../../src/renderer/app/logic/search.ts';
import { appMenu, type MenuSpec } from '../../src/renderer/app/logic/menus.ts';

// A Latin name, a closing quote or bracket right before a Korean particle reads wrong (D-135 14).
const PARTICLE_AFTER_NAME = /[A-Za-z0-9_)\]`'"][을를이가은는의에과와로도만]/;
const clean = (s: string) => assert.ok(!PARTICLE_AFTER_NAME.test(s), `a particle right after a name: ${s}`);

test('Undo History: Start of History, the undo rows oldest first, Now, the redo rows next first (v1 UndoHistory)', () => {
  const lines = historyLines({
    fileId: 'f1', rows: [
      { kind: 'start', moves: -2 }, { kind: 'undo', name: 'Add AND Gate', moves: -1 }, { kind: 'undo', name: 'Add Wire', moves: 0 },
      { kind: 'now', moves: 0 }, { kind: 'redo', name: 'Move Selection', moves: 1 },
    ],
  });
  assert.deepEqual(lines.map((l) => l.text), ['Start of History', 'Add AND Gate', 'Add Wire', 'Now', 'Move Selection']);
  assert.deepEqual(lines.map((l) => l.moves), [-2, -1, 0, 0, 1]);
  assert.match(lines[0].title, /모두 되돌립니다/);
  assert.match(lines[2].title, /마지막 동작/, 'the last undo row is where the file is');
  assert.match(lines[4].title, /다시 실행/);
  assert.match(historyLines({ fileId: 'f1', rows: [{ kind: 'start', moves: 0 }, { kind: 'now', moves: 0 }] })[0].title, /없습니다/);
  assert.deepEqual(historyLines(null), []);
  for (const l of lines) clean(l.title);
});

const analysis = (over: Partial<Analysis>): Analysis => ({
  circuit: 'main', inputs: ['a', 'b'], outputs: ['y'], maxInputs: 12, maxOutputs: 12, source: 'expression', ...over,
});

test('Analyze Circuit: the original\'s stops as facts and what to do, the simulated table said, no particle after a name', () => {
  assert.equal(analyzeProblemText(analysis({})), null);
  const texts = [
    analyzeProblemText(analysis({ problem: 'multibitInput', pin: 'd', source: null })),
    analyzeProblemText(analysis({ problem: 'multibitOutput', pin: 'q', source: null })),
    analyzeProblemText(analysis({ problem: 'tooManyInputs', inputs: Array.from({ length: 13 }, (_, i) => `i${i}`), source: null })),
    analyzeProblemText(analysis({ problem: 'tooManyOutputs', outputs: Array.from({ length: 14 }, (_, i) => `o${i}`), source: null })),
    analyzeProblemText(analysis({ problem: 'noInputs', inputs: [], source: null })),
    analyzeProblemText(analysis({ problem: 'noOutputs', outputs: [], source: null })),
    analyzeSourceText(analysis({ source: 'table' })),
    analyzeSourceText(analysis({ source: 'table', expressionReason: 'cannotHandle', expressionPart: 'Tunnel' })),
  ];
  assert.ok(texts.every((t) => typeof t === 'string' && t.length > 10), JSON.stringify(texts));
  assert.match(texts[0]!, /핀 이름: d/);
  assert.match(texts[2]!, /13개.*12개/);
  assert.match(texts[3]!, /14개.*12개/);
  assert.equal(analyzeSourceText(analysis({})), null);
  assert.match(analyzeSourceText(analysis({ source: 'table', expressionReason: 'cannotHandle', expressionPart: 'Tunnel' }))!, /^식 계산이 다루지 않는 부품이 있어\(부품 이름: Tunnel\) 식을/);
  assert.match(analyzeSourceText(analysis({ source: 'table', expressionReason: 'circular' }))!, /고리/);
  assert.match(analyzeSourceText(analysis({ source: 'table', expressionReason: 'conflict' }))!, /출력이 둘/);
  assert.doesNotMatch(texts[0]!, /Splitter|두면/, 'facts only: which pin, and what the analysis takes');
  for (const t of texts) clean(t!);
});

test('Analyze Circuit: the original\'s expression text with ~ as an overline', () => {
  assert.deepEqual(expressionParts('~a b + a ~b'), [
    { text: 'a', not: true }, { text: ' b + a ', not: false }, { text: 'b', not: true },
  ]);
  assert.deepEqual(expressionParts('(a + b) (~a + ~b)').filter((p) => p.not).map((p) => p.text), ['a', 'b']);
  assert.deepEqual(expressionParts('~sel_1 x2'), [{ text: 'sel_1', not: true }, { text: ' x2', not: false }]);
  assert.deepEqual(expressionParts('0'), [{ text: '0', not: false }]);
});

test('Get Circuit Statistics: the rows, then the original\'s two totals', () => {
  const lines = statisticsLines({
    circuit: 'main', rows: [{ component: 'AND Gate', library: 'Gates', simple: 1, unique: 2, recursive: 3 }],
    without: { simple: 1, unique: 2, recursive: 3 }, with: { simple: 3, unique: 4, recursive: 5 },
  });
  assert.deepEqual(lines.map((l) => l.component), ['AND Gate', "TOTAL (without project's subcircuits)", 'TOTAL (with subcircuits)']);
  assert.deepEqual(lines.map((l) => !!l.total), [false, true, true]);
  assert.equal(lines[2].recursive, 5);
});

const plan = (over: Partial<SubmissionPlan>): SubmissionPlan => ({
  saved: true, dirty: false, messages: 0, probes: 0, bundledJar: false, missing: [], files: ['lab.circ'], suggested: 'lab-submission.zip', ...over,
});

test('Create Submission: v1\'s four checks, each a fact (they never stop the student)', () => {
  assert.deepEqual(submissionChecks(plan({})).map((c) => c.ok), [true, true, true, true]);
  const bad = submissionChecks(plan({ dirty: true, messages: 3, probes: 2, missing: ['../x.hmx'] }));
  assert.deepEqual(bad.map((c) => c.ok), [false, false, false, false]);
  assert.match(bad[0].text, /마지막으로 저장한 파일/);
  assert.match(bad[1].text, /Messages 3건/);
  assert.match(bad[2].text, /Probe 부품 2개/);
  assert.match(bad[3].text, /폴더 밖/);
  assert.match(submissionChecks(plan({ saved: false }))[0].text, /저장한 적이 없습니다/);
  for (const c of [...bad, ...submissionChecks(plan({}))]) clean(c.text);
});

test('the dialogs\' sentences: Korean, no particle right after a name, no 하면 됩니다', () => {
  for (const t of [EXPORT_SENTENCE, PRINT_SENTENCE, PRINT_NONE, STATISTICS_HINT, SUBMISSION_HINT, HISTORY_HINT]) {
    clean(t);
    assert.doesNotMatch(t, /하면 됩니다|한림/);
  }
});

test('Export Image: v1\'s name <file>-<circuit>.<ext>, letters a file name cannot have replaced', () => {
  assert.equal(pictureName('lab04.circ', 'main', 'png'), 'lab04-main.png');
  assert.equal(pictureName('Lab.CIRC', 'alu', 'svg'), 'Lab-alu.svg');
  assert.equal(pictureName('untitled', 'a/b:c', 'pdf'), 'untitled-a_b_c.pdf');
  assert.equal(pictureName('', '', 'png'), 'circuit-circuit.png');
});

test('Export Image, Selection Only: the chosen parts and wires, nets cut down to them with their ids, the dots on kept wires', () => {
  const s: Snapshot = {
    circuitId: 'c1', name: 'main',
    components: [
      { id: 'k1', lib: 'Gates', name: 'AND Gate', loc: [100, 100], bounds: [50, 75, 50, 50], facing: 'east', attrs: {}, ports: [] },
      { id: 'k2', lib: 'Gates', name: 'OR Gate', loc: [300, 100], bounds: [250, 75, 50, 50], facing: 'east', attrs: {}, ports: [] },
    ],
    wires: [{ id: 'w1', a: [100, 100], b: [200, 100] }, { id: 'w2', a: [200, 100], b: [250, 100] }, { id: 'w3', a: [200, 100], b: [200, 200] }],
    nets: [{ id: 'n1', width: 1, wires: ['w1', 'w2', 'w3'], ports: [['k1', 0], ['k2', 1]] }, { id: 'n2', width: 1, wires: [], ports: [['k2', 0]] }],
    junctions: [[200, 100]],
  };
  const sub = subSnapshot(s, ['k1', 'w1']);
  assert.deepEqual(sub.components.map((c) => c.id), ['k1']);
  assert.deepEqual(sub.wires.map((w) => w.id), ['w1']);
  assert.deepEqual(sub.nets, [{ id: 'n1', width: 1, wires: ['w1'], ports: [['k1', 0]] }], 'the net keeps its id (its value), n2 has nothing left');
  assert.deepEqual(sub.junctions, [[200, 100]]);
  assert.deepEqual(subSnapshot(s, ['k2']).junctions, [], 'no kept wire ends there');
  assert.equal(s.nets[0].wires.length, 3, 'the snapshot on show is not changed');
});

test('the commands are in the palette (names as in the menus) and the circuit menu has the original\'s two items', () => {
  const all = COMMANDS.map((c) => c.id);
  const first = (q: string) => search(q, { libraries: [], fileName: 'x.circ', current: 'c1', commands: all })[0]?.name;
  assert.equal(first('undo history'), 'Undo History…');
  assert.equal(first('export'), 'Export Image…');
  assert.equal(first('print'), 'Print…');
  assert.equal(first('제출'), 'Create Submission…');
  assert.equal(first('진리표'), 'Analyze Circuit');
  assert.equal(first('통계'), 'Get Circuit Statistics');
  const items = circuitItems({ circuits: [{ circuitId: 'c1', name: 'main' }], main: 'c1', editable: false }, 'c1', () => {});
  const labels = items.map((i) => i.label);
  assert.deepEqual(labels.slice(0, 4), ['Edit Circuit Layout', 'Edit Circuit Appearance', 'Analyze Circuit', 'Get Circuit Statistics']);
  const ran: string[] = [];
  circuitItems({ circuits: [{ circuitId: 'c1', name: 'main' }], main: 'c1', editable: false }, 'c1', (cmd) => ran.push(cmd))
    .filter((i) => i.label === 'Analyze Circuit' || i.label === 'Get Circuit Statistics').forEach((i) => i.run!());
  assert.deepEqual(ran, ['analyze', 'statistics'], 'read only: on even when the file cannot be edited');
  for (const m of ['model.history', 'edit.history', 'model.analyze', 'model.statistics']) assert.ok((WINDOW_METHODS as readonly string[]).includes(m), m);
  assert.ok(!(WINDOW_METHODS as readonly string[]).includes('file.submission'), 'it takes a path: the main process calls it');
});

// ---- the main process (src/main/pictures.ts) ----

const SVG = '<svg xmlns="http://www.w3.org/2000/svg" width="200" height="100" viewBox="0 0 200 100">\n<rect x="0" y="0" width="200" height="100" fill="#ffffff"/>\n'
  + '<text x="1" y="2" font-family="Pretendard, sans-serif" font-size="10" fill="#000">a</text>\n'
  + '<text x="1" y="2" font-family="Pretendard, sans-serif" font-size="10" font-weight="700" fill="#000">b</text>\n</svg>';

test('pictures: only the faces a picture uses are embedded, as data in the SVG (the same look anywhere)', () => {
  assert.deepEqual(usedFaces(SVG).map((f) => f.file), ['Pretendard-Regular.subset.woff2', 'Pretendard-Bold.subset.woff2']);
  const withCode = SVG.replace('</svg>', '<text font-family="D2Coding, monospace" font-size="9">0x1</text></svg>');
  assert.ok(usedFaces(withCode).some((f) => f.family === 'D2Coding'));
  assert.deepEqual(usedFaces('<svg width="1" height="1"></svg>'), [], 'no text, no fonts');
  const css = fontFaceCss([{ family: 'Pretendard', weight: 700, data: Buffer.from('xyz') }]);
  assert.equal(css, "@font-face{font-family:Pretendard;font-weight:700;src:url(data:font/woff2;base64,eHl6) format('woff2')}");
  const out = embedFonts(SVG, css);
  assert.ok(out.startsWith('<svg xmlns="http://www.w3.org/2000/svg" width="200" height="100" viewBox="0 0 200 100">\n<defs><style>@font-face'));
  assert.ok(out.endsWith(SVG.slice(SVG.indexOf('\n'))));
  assert.equal(embedFonts(SVG, ''), SVG);
  assert.throws(() => embedFonts('<html></html>', css));
  // every face the Canvas uses is a file the app has
  for (const f of FACES) assert.ok(readFileSync(path.join(import.meta.dirname, '../../src/renderer/assets/fonts', f.file)).length > 1000, f.file);
});

test('pictures: the size, the PNG\'s scale (1-4 x, smaller when it would not fit a canvas), the PDF page the picture\'s size', () => {
  assert.deepEqual(svgSize(SVG), { width: 200, height: 100 });
  assert.throws(() => svgSize('<svg></svg>'));
  assert.equal(pngScale(200, 100, 2), 2);
  assert.equal(pngScale(200, 100, 9), 4);
  assert.equal(pngScale(200, 100, 0), 1);
  const s = pngScale(8000, 6000, 4);
  assert.ok(s < 4 && 8000 * s * 6000 * s <= PNG_MAX_PIXELS, String(s));
  const t = pngScale(20000, 100, 1);
  assert.ok(20000 * t <= PNG_MAX_SIDE, String(t));
  const html = pdfHtml(SVG);
  assert.match(html, /@page\{size:200px 100px;margin:0\}/);
  assert.ok(html.includes(SVG));
});

test('Print: the original\'s header (%n %p %P %%), a page per circuit, a wide picture turned with Rotate To Fit', () => {
  assert.equal(printHeader(PRINT_HEADER, 'alu', 2, 3), 'alu (2 of 3)');
  assert.equal(printHeader('%n %% %x %P', 'main', 1, 1), 'main % %x 1');
  const tall = SVG.replace('width="200" height="100"', 'width="100" height="200"');
  const html = printHtml([{ svg: SVG, name: 'main' }, { svg: tall, name: 'alu<b>' }], { header: PRINT_HEADER, rotate: true }, '@font-face{}');
  assert.equal(html.match(/<section class="page">/g)?.length, 2);
  assert.ok(html.includes('main (1 of 2)'));
  assert.ok(html.includes('alu&lt;b&gt; (2 of 2)'), 'a name is text, not markup');
  assert.equal(html.match(/class="pic turn"/g)?.length, 1, 'only the wide one turns');
  assert.equal(printHtml([{ svg: SVG, name: 'm' }], { header: PRINT_HEADER, rotate: false }, '').match(/pic turn/g), null);
  assert.equal(printHtml([{ svg: SVG, name: 'm' }], { header: '  ', rotate: true }, '').includes('class="head"'), false, 'no header');
});

test('the title bar\'s menu has the original\'s places for them: File (Create Submission…, Export Image…, Print… Ctrl+P), Edit › Undo History…, Project › Analyze Circuit, Get Circuit Statistics (read only)', () => {
  const st = { file: true, ready: true, simOn: true, ticking: false, hz: 1, frequencies: [], recent: [], examples: [], files: [], tool: 'Edit', project: { editable: false, index: 0, count: 1, main: true } };
  const m = appMenu(st as unknown as Parameters<typeof appMenu>[0]);
  const items = (menu: string): MenuSpec[] => m.find((x) => x.label === menu)!.items!;
  const labels = (menu: string) => items(menu).map((i) => i.label);
  const file = labels('File');
  assert.ok(file.indexOf('Create Submission…') === file.indexOf('Save As…') + 1, file.join(' | '));
  assert.ok(file.indexOf('Export Image…') < file.indexOf('Print…') && file.indexOf('Print…') < file.indexOf('Preferences…'));
  assert.equal(items('File').find((i) => i.label === 'Print…')!.key, 'Ctrl+P');
  assert.equal(labels('Edit')[labels('Edit').indexOf('Redo') + 1], 'Undo History…');
  const project = items('Project');
  for (const [label, id] of [['Analyze Circuit', 'project.analyze'], ['Get Circuit Statistics', 'project.statistics']]) {
    const it = project.find((i) => i.label === label)!;
    assert.equal(it.id, id);
    assert.equal(it.disabled, undefined, `${label}: on for a file that cannot be edited (read only)`);
  }
  const none = appMenu({ ...st, file: false } as unknown as Parameters<typeof appMenu>[0]);
  for (const [menu, label] of [['File', 'Export Image…'], ['Edit', 'Undo History…'], ['Project', 'Analyze Circuit']]) {
    assert.equal(none.find((x) => x.label === menu)!.items!.find((i) => i.label === label)!.disabled, true, label);
  }
});
