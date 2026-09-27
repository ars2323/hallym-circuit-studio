/* The window.

     title bar   logo, name, file, the toolbar (its own row under the bar
                 when the bar cannot hold it); New, Open, About at the right
                 end; the system's caption buttons (titleBarOverlay)
     band        one line, only when there is something the student must
                 not miss (the engine stopped, or could not start)
     work        the first screen (start.ts), or once a file is open:
                   left    Components | Circuits   over   Tunnels | Minimap
                   center  file tabs, circuit tabs, Canvas
                           over Messages | Cycle View | Console
                   right   Attributes
                 A narrow window (layout.ts) moves Attributes into the left
                 panel's tabs.  Splitters between them; sizes for this run.
     status bar  facts only

   The engine (a Java process the main process runs, docs/engine-api.md)
   has the circuits; this page shows what it says.  Drawing the Canvas is
   item N-05: until then the Canvas says what the circuit holds.  Panels
   with nothing to show say, in one sentence, what fills them.

   Nothing is restored from an earlier run and nothing is written but the
   files the student saves (the lab-PC rule; src/main/main.ts). */

import type { CircuitRef, EngineStatus, LibraryGroup, NewResult, SimState, Snapshot } from '../../main/protocol.ts';
import { aboutDialog } from '../shared/about.ts';
import { ask } from '../shared/ask.ts';
import { band } from '../shared/band.ts';
import { code, codeText, h, icon } from '../shared/dom.ts';
import { noticeHost } from '../shared/notice.ts';
import { overlayColor } from '../shared/overlay.ts';
import { splitter } from '../shared/splitter.ts';
import { button, iconButton, titleBar } from '../shared/titlebar.ts';
import { headButton, panelHead, tabStrip, tabsHead } from '../shared/ui.ts';
import type { CallError, Opened } from './api.ts';
import { circuitFacts, count, engineFact, engineVersion } from './logic/facts.ts';
import { Files, type OpenFile } from './logic/files.ts';
import { arrange, nothingDragged, PAD, SPLITTER } from './logic/layout.ts';
import { startScreen } from './start.ts';

const api = window.app;
const APP_NAME = 'Hallym Circuit Studio';

// ---- state ------------------------------------------------------------------

let engine: EngineStatus = { state: 'starting', generation: 0, hello: null, error: null, detail: null };
const files = new Files();
const snapshots = new Map<string, Snapshot>();          // `${fileId} ${circuitId}`
const libraries = new Map<string, LibraryGroup[] | string>(); // by fileId; a string: why there is none
let decided = false;                                    // whether a file was named on the command line is known
let opening = false;                                    // a file on its way (the command line's)
let note: { cls: '' | 'err' | 'ok'; text: string } | null = null; // the last action's fact
let dragged = nothingDragged();
let bottomCollapsed = false;
let startSeen = false;
let untitled = 0;
const FREQUENCIES: [string, number][] = [['4.1 kHz', 4096], ['2.0 kHz', 2048], ['1.0 kHz', 1024], ['512 Hz', 512], ['256 Hz', 256],
  ['128 Hz', 128], ['64 Hz', 64], ['32 Hz', 32], ['16 Hz', 16], ['8 Hz', 8], ['4 Hz', 4], ['2 Hz', 2], ['1 Hz', 1], ['0.5 Hz', 0.5], ['0.25 Hz', 0.25]];

const key = (fileId: string, circuitId: string) => `${fileId} ${circuitId}`;
// A failed call's words (preload.cjs rejects with { name, message, code?, data? }).
const why = (e: unknown): string => (typeof e === 'object' && e !== null && 'message' in e ? String((e as CallError).message) : String(e));

// ---- the title bar ------------------------------------------------------------

// Tools of the Canvas (N-05, N-08, N-15): shown, and off until there is a Canvas to use them on.
const TOOLS: [string, string, string][] = [
  ['Edit', 'mouse-pointer-2', 'Edit: 고르기·옮기기'], ['Poke', 'pointer', 'Poke: 값 바꾸기'], ['Wire', 'workflow', 'Wire'],
  ['Text', 'type', 'Text'], ['Pin', 'square-dot', 'Pin'], ['Tunnel', 'tag', 'Tunnel'], ['Probe', 'crosshair', 'Probe'],
  ['Signal Flow', 'activity', 'Signal Flow'],
];
const toolButtons = TOOLS.map(([name, ic, title], i) => h('button', {
  type: 'button', role: 'radio', title, 'aria-label': name, 'aria-checked': String(i === 0), class: i === 0 ? 'on' : undefined, disabled: true,
}, icon(ic), h('span', { class: 'label' }, name)));
const bSave = iconButton('Save (Ctrl+S)', 'save', () => void save(false));
const bUndo = iconButton('Undo (Ctrl+Z)', 'undo-2', () => void edit('edit.undo', 'Undo'));
const bRedo = iconButton('Redo (Ctrl+Y)', 'redo-2', () => void edit('edit.redo', 'Redo'));
const bRun = button('Run', 'play', 'F5', () => void run());
const bCycle = button('1 Cycle', 'step-forward', 'F10', () => void cycles(1));
const bCycles = button('N Cycles', 'fast-forward', '', () => {});
const bReset = button('Reset', 'rotate-ccw', '', () => void reset());
const frequency = h('select', { title: 'Clock speed', 'aria-label': 'Clock speed' },
  ...FREQUENCIES.map(([label, hz]) => h('option', { value: String(hz), selected: hz === 1 }, label)));
const bLoad = button('Load Program…', 'file-code', '', () => {});
const toolbar = h('span', { class: 'toolbar', role: 'toolbar', 'aria-label': 'Toolbar' },
  h('span', { class: 'tgroup' }, bSave, bUndo, bRedo),
  h('span', { class: 'tgroup' }, h('span', { class: 'seg tools-seg', role: 'radiogroup', 'aria-label': 'Tools' }, ...toolButtons)),
  h('span', { class: 'tgroup' }, bRun, bCycle, bCycles, bReset, h('span', { class: 'selectbox' }, icon('gauge'), frequency)),
  h('span', { class: 'tgroup' }, bLoad));
const bar = titleBar({
  appName: APP_NAME,
  toolbar,
  tools: [
    iconButton('New circuit (Ctrl+N)', 'file-plus', () => void newCircuit()),
    iconButton('Open file (Ctrl+O)', 'folder-open', () => void openFile()),
    iconButton('About', 'info', () => void about.open()),
  ],
});
const notices = band();
const status = h('footer', { class: 'status' });

// ---- the first screen -----------------------------------------------------------

const start = startScreen({
  course: () => void newCircuit(),       // the tracks are N-18's (D-135)
  newCircuit: () => void newCircuit(),
  openFile: () => void openFile(),
});
const stage = h('div', { class: 'stage-welcome' }, start.root);

// ---- the panels -----------------------------------------------------------------

// Left, upper: Components | Circuits (| Attributes, narrow)
const componentsBody = noticeHost('side');
const circuitsBody = noticeHost('side');
const attributesBody = noticeHost('side');
const upperBodies = [componentsBody.root, circuitsBody.root, attributesBody.root];
const upperHead = tabsHead(['Components', 'Circuits', 'Attributes'], (i) => showUpper(i));
const upperPanel = h('section', { class: 'panel upper', 'aria-label': 'Components' }, upperHead.root, ...upperBodies);
// Left, lower: Tunnels | Minimap
const tunnelsBody = noticeHost('side');
const minimapBody = noticeHost('side');
const lowerBodies = [tunnelsBody.root, minimapBody.root];
const lowerHead = tabsHead(['Tunnels', 'Minimap'], (i) => showBody(lowerBodies, i));
const lowerPanel = h('section', { class: 'panel lower', 'aria-label': 'Tunnels' }, lowerHead.root, ...lowerBodies);
// Right: Attributes
const rightHead = panelHead('Attributes');
const rightPanel = h('section', { class: 'panel right', 'aria-label': 'Attributes' }, rightHead.root);
// Center: the files, the circuits, the Canvas
const fileStrip = tabStrip({ label: 'Files', closable: true, onSelect: (id) => showFile(id), onClose: (id) => void closeFile(id) });
const circuitStrip = tabStrip({
  label: 'Circuit tabs', closable: true, canClose: (items) => items.length > 1,
  onSelect: (id) => { const f = files.active(); if (f) { files.openCircuit(f.fileId, id); render(); } },
  onClose: (id) => { const f = files.active(); if (f) { files.closeCircuit(f.fileId, id); render(); } },
});
const canvasBody = noticeHost('canvas');
const canvasPanel = h('section', { class: 'panel canvaspanel', 'aria-label': 'Canvas' },
  h('div', { class: 'phead filebar' }, fileStrip.root),
  h('div', { class: 'circuitbar' }, circuitStrip.root),
  canvasBody.root);
// Center, under the Canvas: Messages | Cycle View | Console
const messagesBody = noticeHost('bottom');
const cycleBody = noticeHost('bottom');
const consoleBody = noticeHost('bottom');
const bottomBodies = [messagesBody.root, cycleBody.root, consoleBody.root];
const bottomHead = tabsHead(['Messages', 'Cycle View', 'Console'], (i) => { showBody(bottomBodies, i); if (bottomCollapsed) toggleBottom(); });
const bCollapse = headButton('Collapse', 'Collapse the panel', () => toggleBottom());
bottomHead.aside.append(bCollapse);
const bottomPanel = h('section', { class: 'panel bottom', 'aria-label': 'Messages' }, bottomHead.root, ...bottomBodies);

function showBody(bodies: HTMLElement[], i: number): void {
  bodies.forEach((b, k) => { b.hidden = k !== i; });
}
// The upper left panel's tabs: only the bodies in that panel (Attributes has its own column when wide).
function showUpper(i: number): void {
  upperBodies.forEach((b, k) => { b.hidden = b.parentElement === upperPanel ? k !== i : false; });
}
showUpper(0);
showBody(lowerBodies, 0);
showBody(bottomBodies, 0);

// ---- the grid and its splitters ----------------------------------------------------

const shell = h('div', { class: 'shell' });
const leftCol = h('div', { class: 'leftcol' });
const center = h('div', { class: 'center' });
const rightCol = h('div', { class: 'rightcol' }, rightPanel);
const leftSplit = splitter({
  between: 'columns', label: 'Left panels',
  onDrag: ({ x }) => { dragged.left = x - shell.getBoundingClientRect().left - PAD - SPLITTER / 2; layout(); },
  onReset: () => { dragged.left = null; layout(); },
});
const rightSplit = splitter({
  between: 'columns', label: 'Attributes',
  onDrag: ({ x }) => { dragged.right = shell.getBoundingClientRect().right - PAD - x - SPLITTER / 2; layout(); },
  onReset: () => { dragged.right = null; layout(); },
});
const lowerGrip = splitter({
  between: 'rows', label: 'Tunnels',
  onDrag: ({ y }) => { dragged.lower = leftCol.getBoundingClientRect().bottom - y - SPLITTER / 2; layout(); },
  onReset: () => { dragged.lower = null; layout(); },
});
const bottomGrip = splitter({
  between: 'rows', label: 'Messages',
  onDrag: ({ y }) => { if (bottomCollapsed) toggleBottom(); dragged.bottom = center.getBoundingClientRect().bottom - y - SPLITTER / 2; layout(); },
  onReset: () => { dragged.bottom = null; layout(); },
});
rightSplit.classList.add('rightsplit');
leftCol.append(upperPanel, lowerGrip, lowerPanel);
center.append(canvasPanel, bottomGrip, bottomPanel);
shell.append(leftCol, leftSplit, center, rightSplit, rightCol);
const work = h('main', { class: 'work' }, stage, shell);

document.body.append(h('div', { class: 'app' }, bar.root, bar.row, notices.root, work, status));

function toggleBottom(): void {
  bottomCollapsed = !bottomCollapsed;
  bottomPanel.classList.toggle('collapsed', bottomCollapsed);
  bCollapse.textContent = bottomCollapsed ? 'Expand' : 'Collapse';
  bCollapse.title = bottomCollapsed ? 'Expand the panel' : 'Collapse the panel';
  layout();
}

// ---- layout --------------------------------------------------------------------------

function layout(): void {
  const open = files.count() > 0;
  stage.hidden = !decided || open || opening;
  shell.hidden = !open;
  if (!stage.hidden && !startSeen) { startSeen = true; document.documentElement.dataset.startSeen = 'true'; }
  if (open) {
    const a = arrange(work.clientWidth, work.clientHeight, dragged, bottomCollapsed);
    shell.classList.toggle('narrow', a.narrow);
    shell.style.setProperty('--left-w', `${a.left}px`);
    shell.style.setProperty('--right-w', `${a.right}px`);
    shell.style.setProperty('--bottom-h', `${a.bottom}px`);
    shell.style.setProperty('--lower-h', `${a.lower}px`);
    // Attributes: its own column, or a tab of the left panel.
    upperHead.show(2, a.narrow);
    if (a.narrow && attributesBody.root.parentElement !== upperPanel) {
      upperPanel.append(attributesBody.root);
      showUpper(upperHead.selected());
    } else if (!a.narrow && attributesBody.root.parentElement !== rightPanel) {
      rightPanel.append(attributesBody.root);
      showUpper(upperHead.selected());
    }
  }
  bar.fit();
}

// ---- rendering ----------------------------------------------------------------------

function render(): void {
  const f = files.active();
  document.title = f ? `${f.name}${f.dirty ? ' •' : ''} — ${APP_NAME}` : APP_NAME;
  bar.setFile(f ? f.name : null, f?.dirty ?? false);
  bar.showToolbar(f !== null);
  const ready = engine.state === 'ready';
  for (const b of [bSave, bUndo, bRedo, bRun, bCycle, bReset]) b.disabled = !f || !ready;
  frequency.disabled = !f || !ready;
  bCycles.disabled = true;   // N-07: the count to go
  bLoad.disabled = true;     // N-16: .hmx and .s
  const running = f?.sim?.running ?? false;
  bRun.replaceChildren(icon(running ? 'square' : 'play'), h('span', { class: 'label' }, running ? 'Stop' : 'Run'), h('kbd', {}, 'F5'));
  bRun.title = running ? 'Stop (F5)' : 'Run (F5)';
  bRun.classList.toggle('primary', running);
  if (f) {
    fileStrip.set(files.list().map((x) => ({ id: x.fileId, label: x.name, title: x.path ?? x.name, dirty: x.dirty })), f.fileId);
    circuitStrip.set(f.tabs.map((c) => ({ id: c, label: files.circuitName(f, c) })), f.circuit);
    renderCircuits(f);
    renderComponents(f);
    renderCanvas(f);
  } else {
    fileStrip.set([], null);
    circuitStrip.set([], null);
  }
  renderEmptyPanels();
  renderStatus();
  layout();
}

function renderCircuits(f: OpenFile): void {
  circuitsBody.fill(h('ul', { class: 'list' }, ...f.circuits.map((c: CircuitRef) => {
    const b = h('button', { type: 'button', title: c.name }, h('span', { class: 'mono' }, c.name),
      c.circuitId === f.main ? h('span', { class: 'tag' }, 'main') : null);
    b.addEventListener('click', () => { files.openCircuit(f.fileId, c.circuitId); render(); });
    return h('li', { class: c.circuitId === f.circuit ? 'on' : undefined }, b);
  })));
}

function renderComponents(f: OpenFile): void {
  const lib = libraries.get(f.fileId);
  if (lib === undefined) {
    componentsBody.fill();
    void loadLibrary(f.fileId);
  } else if (typeof lib === 'string') {
    componentsBody.empty({ title: '부품 목록을 받지 못했습니다', body: lib });
  } else {
    componentsBody.fill(...lib.map((g, i) => h('details', { class: 'libgroup', open: i < 2 },
      h('summary', {}, g.lib, h('span', { class: 'count' }, count(g.tools.length))),
      h('ul', { class: 'list' }, ...g.tools.map((t) => h('li', {}, h('span', { class: 'item', title: t.name }, t.display)))))));
  }
}

async function loadLibrary(fileId: string): Promise<void> {
  try {
    libraries.set(fileId, await api.call<LibraryGroup[]>('model.library', { fileId }));
  } catch (e) {
    libraries.set(fileId, why(e));
  }
  if (files.active()?.fileId === fileId) renderComponents(files.active()!);
}

let wanted = '';
function renderCanvas(f: OpenFile): void {
  const k = key(f.fileId, f.circuit);
  const s = snapshots.get(k);
  canvasBody.root.dataset.circuit = files.circuitName(f, f.circuit);
  if (!s) {
    canvasBody.fill();
    renderTunnels(null);
    if (wanted !== k) { wanted = k; void loadSnapshot(f.fileId, f.circuit); }
    return;
  }
  const facts = circuitFacts(s);
  if (facts.components === 0 && facts.wires === 0) {
    canvasBody.empty({ title: '빈 회로입니다', body: '부품과 선을 놓으면 여기 Canvas에 그려집니다.', pose: 'haram-hari-guide' });
  } else {
    canvasBody.empty({
      title: `이 회로에는 부품 ${count(facts.components)}개와 선 ${count(facts.wires)}개가 있습니다`,
      body: '회로 그림은 이 Canvas에 그려집니다.', pose: 'haram-hari-guide',
    });
  }
  renderTunnels(s);
}

async function loadSnapshot(fileId: string, circuitId: string): Promise<void> {
  const k = key(fileId, circuitId);
  try {
    snapshots.set(k, await api.call<Snapshot>('model.circuit', { fileId, circuitId }));
  } catch (e) {
    note = { cls: 'err', text: `model.circuit — ${why(e)}` };
  }
  if (wanted === k) wanted = '';
  const f = files.active();
  if (f && key(f.fileId, f.circuit) === k) { renderCanvas(f); renderStatus(); }
}

function renderTunnels(s: Snapshot | null): void {
  const tunnels = s ? circuitFacts(s).tunnels : [];
  if (!s) { tunnelsBody.fill(); return; }
  if (tunnels.length === 0) {
    tunnelsBody.empty({ title: '터널이 없습니다', body: '이 회로에 Tunnel을 놓으면 이름별로 여기에 모입니다.' });
    return;
  }
  tunnelsBody.fill(h('ul', { class: 'list' }, ...tunnels.map((t) =>
    h('li', {}, h('span', { class: 'item', title: t.label || '(no label)' }, t.label ? code(t.label) : h('span', { class: 'dim' }, '(no label)'),
      h('span', { class: 'count' }, count(t.count)))))));
}

function renderEmptyPanels(): void {
  attributesBody.empty({ title: '고른 부품이 없습니다', body: codeText('Canvas에서 부품을 고르면 그 속성(`Data Bits`, `Facing`, `Label` …)이 여기에 나옵니다.') });
  minimapBody.empty({ title: '회로 전체가 작게 나옵니다', body: 'Canvas에 그린 회로의 전체 모습과 지금 보는 곳이 여기에 나옵니다.' });
  messagesBody.empty({ title: '메시지가 없습니다', body: '동작할 수 없는 연결(떠 있는 입력, 짝 없는 터널, 폭이 다른 선 …)이 있으면 여기에 나옵니다.' });
  cycleBody.empty({ title: '아직 사이클이 없습니다', body: '1 Cycle이나 Run으로 클럭을 진행하면 사이클마다 값이 여기에 쌓입니다.' });
  consoleBody.empty({ title: '아직 출력이 없습니다', body: '회로의 Console 부품이 출력하면 여기에 나옵니다.' });
}

function renderStatus(): void {
  const parts: Node[] = [];
  const span = (cls: string, ...c: (Node | string)[]) => h('span', { class: cls }, ...c);
  const ef = engineFact(engine);
  if (ef) parts.push(span(ef.cls, ef.text));
  const f = files.active();
  if (opening) parts.push(span('', '파일을 여는 중'));
  if (!f && engine.state === 'ready' && !opening) parts.push(span('', '준비'));
  if (f) {
    const s = snapshots.get(key(f.fileId, f.circuit));
    parts.push(span('', code(files.circuitName(f, f.circuit)),
      s ? ` · 부품 ${count(s.components.length)}개 · 선 ${count(s.wires.length)}개` : ''));
    if (f.sim) {
      parts.push(span('', `Cycle ${count(f.sim.cycle)}`));
      if (f.sim.running) parts.push(span('run', '실행 중'));
      if (f.sim.oscillating) parts.push(span('err', '발진'));
    }
  }
  if (note) parts.push(span(note.cls, note.text));
  parts.push(span('grow'));
  const v = engineVersion(engine);
  if (v) parts.push(span('engine', v));
  status.replaceChildren(...parts);
}

// ---- files ------------------------------------------------------------------------------

function showFile(fileId: string): void {
  files.activate(fileId);
  note = null;
  render();
}

async function engineReady(): Promise<boolean> {
  if (engine.state === 'ready') return true;
  if (engine.state === 'starting' || engine.state === 'restarting') {
    // Waits for the start: the calls wait in the main process too.
    return new Promise((done) => {
      const t = setInterval(() => { if (engine.state !== 'starting' && engine.state !== 'restarting') { clearInterval(t); done(engine.state === 'ready'); } }, 50);
    });
  }
  await engineFailed();
  return false;
}

function added(f: { fileId: string; name: string; path: string | null; circuits: CircuitRef[]; main: string }): void {
  files.add(f);
  notices.hide();
  note = null;
  render();
}

async function newCircuit(): Promise<void> {
  if (!(await engineReady())) return;
  try {
    const r = await api.call<NewResult>('file.new');
    untitled += 1;
    added({ fileId: r.fileId, name: untitled === 1 ? 'untitled.circ' : `untitled-${untitled}.circ`, path: null, circuits: r.circuits, main: r.main });
  } catch (e) {
    note = { cls: 'err', text: `새 회로를 만들지 못했습니다 — ${why(e)}` };
    renderStatus();
  }
}

function openedOrError(r: Opened | null, e?: unknown): void {
  if (e) {
    const err = e as CallError;
    const data = err.data as { path?: string } | undefined;
    void ask({
      title: '파일을 열지 못했습니다', file: data?.path ? data.path.split(/[\\/]/).pop() : undefined,
      body: err.message, ok: '닫기', cancel: null, character: false,
    });
    return;
  }
  if (!r) return;
  if (r.already) { showFile(r.fileId); return; }
  added({ fileId: r.fileId, name: r.name, path: r.path, circuits: r.circuits, main: r.main });
}

async function openFile(): Promise<void> {
  if (!(await engineReady())) return;
  try {
    openedOrError(await api.openFile());
  } catch (e) {
    openedOrError(null, e);
  }
}

async function save(saveAs: boolean): Promise<void> {
  const f = files.active();
  if (!f || engine.state !== 'ready') return;
  try {
    const r = await api.saveFile(f.fileId, { name: f.name, saveAs });
    if (!r) return;
    files.saved(f.fileId, r.name, r.path);
    note = { cls: 'ok', text: `저장했습니다 · ${r.name}` };
  } catch (e) {
    note = { cls: 'err', text: `저장하지 못했습니다 — ${why(e)}` };
  }
  render();
}

async function closeFile(fileId: string): Promise<void> {
  const f = files.get(fileId);
  if (!f) return;
  let dirty = f.dirty;
  if (engine.state === 'ready') dirty = (await api.call<{ dirty: boolean }>('file.dirty', { fileId }).catch(() => ({ dirty: f.dirty }))).dirty;
  if (dirty) {
    const go = await ask({
      title: '저장하지 않은 변경이 있습니다', file: f.name,
      body: '닫으면 저장하지 않은 내용은 사라집니다.', ok: '버리고 닫기', cancel: '돌아가기', danger: true,
    });
    if (!go) return;
  }
  if (engine.state === 'ready') await api.call('file.close', { fileId }).catch(() => {});
  files.close(fileId);
  for (const k of [...snapshots.keys()]) if (k.startsWith(`${fileId} `)) snapshots.delete(k);
  libraries.delete(fileId);
  note = null;
  if (files.count() === 0) start.go('first');
  render();
}

// ---- editing and the simulation (as far as the engine's v0 goes) ---------------------------

async function edit(method: 'edit.undo' | 'edit.redo', name: string): Promise<void> {
  const f = files.active();
  if (!f || engine.state !== 'ready') return;
  try {
    await api.call(method, { fileId: f.fileId, circuitId: f.circuit });
    note = null;
  } catch (e) {
    note = { cls: 'err', text: `${name} — ${why(e)}` };
  }
  renderStatus();
}

async function simCall(method: 'sim.run' | 'sim.cycles' | 'sim.reset', params: Record<string, unknown>, name: string): Promise<void> {
  const f = files.active();
  if (!f || engine.state !== 'ready') return;
  try {
    await api.call(method, { fileId: f.fileId, ...params });
    note = null;
  } catch (e) {
    note = { cls: 'err', text: `${name} — ${why(e)}` };
  }
  renderStatus();
}
const run = () => simCall('sim.run', { on: !(files.active()?.sim?.running ?? false), hz: Number(frequency.value) }, 'Run');
const cycles = (n: number) => simCall('sim.cycles', { n }, n === 1 ? '1 Cycle' : 'N Cycles');
const reset = () => simCall('sim.reset', {}, 'Reset');

// ---- the engine ----------------------------------------------------------------------

let failureOpen = false;
async function engineFailed(): Promise<void> {
  notices.show('엔진을 시작하지 못했습니다 · 회로를 만들거나 열 수 없습니다', 'error');
  if (failureOpen) return;
  failureOpen = true;
  const retry = await ask({
    title: '엔진을 시작하지 못했습니다',
    body: '회로를 열고 돌리는 엔진(Java)이 시작되지 않았습니다. 엔진 없이는 회로를 만들거나 열 수 없습니다.',
    detail: engine.detail ?? undefined, ok: '다시 시도', cancel: '닫기', character: false,
  });
  failureOpen = false;
  if (retry) onEngine(await api.retryEngine());
}

function onEngine(s: EngineStatus): void {
  const before = engine;
  engine = s;
  if (s.state === 'restarting') notices.show('엔진이 멈춰서 다시 시작하는 중입니다', 'warn');
  if (s.state === 'ready' && before.generation > 0 && s.generation !== before.generation) {
    // A new engine: the files were the old one's.
    const had = files.count();
    files.clear();
    snapshots.clear();
    libraries.clear();
    start.go('first');
    notices.show(had ? `엔진이 멈춰서 다시 시작했습니다 · 열려 있던 파일 ${had}개를 닫았습니다` : '엔진이 멈춰서 다시 시작했습니다', 'warn');
  } else if (s.state === 'ready' && before.state !== 'ready' && notices.text()?.startsWith('엔진을 시작하지 못했습니다')) {
    notices.hide();
  }
  render();
  if (s.state === 'failed') void engineFailed();
}

api.onEngineStatus(onEngine);
api.onNotify((method, params) => {
  const p = params as Record<string, unknown>;
  if (method === 'sim.state') {
    files.setSim(p as unknown as SimState);
    render();
  } else if (method === 'model.changed') {
    const fileId = String(p.fileId);
    snapshots.delete(key(fileId, String(p.circuitId)));
    if (typeof p.dirty === 'boolean') files.setDirty(fileId, p.dirty);
    render();
  }
});

// ---- About -------------------------------------------------------------------------------

const about = aboutDialog({
  lines: async () => {
    const info = await api.about();
    return [
      h('p', {}, h('b', {}, APP_NAME), ' ', code(info.version)),
      h('p', {}, 'Based on Logisim 2.7.1 by Carl Burch (GNU GPL, version 2 or later)'),
      h('p', {}, 'Screen parts from Hallym MIPS Simulator (BSD 3-Clause)'),
      h('p', { class: 'hint' }, 'Hallym University의 논리설계와 컴퓨터구조 실습을 위한 회로 편집·시뮬레이션 도구입니다.'),
      h('p', { class: 'hint' }, 'Hallym University의 로고와 캐릭터(하람, 하리)는 Hallym University의 소유이며 상업적 사용을 금지합니다. 이 프로그램은 Hallym University의 공식 제품이 아닙니다.'),
      h('p', { class: 'hint' }, 'Engine ', info.engine ? code(`${info.engine.engine} ${info.engine.version}`) : '—',
        info.engine ? h('span', {}, ' · Logisim ', code(info.engine.logisim), ' · Java ', code(info.engine.java)) : null),
      h('p', { class: 'hint' }, 'Electron ', code(info.electron), ' · Chromium ', code(info.chrome), ' · Node.js ', code(info.node)),
    ];
  },
  licenses: async () => [...(await api.about()).licenses, 'Electron — MIT License'],
  license: (i) => api.license(i),
  openCredits: () => api.openCredits(),
  creditsNote: ['Chromium · Node.js 고지(그 안의 라이브러리 포함)는 설치 폴더에 있습니다: ', code('LICENSES.chromium.html')],
});
document.body.append(about.root);

// ---- the caption buttons' patch ------------------------------------------------------------
// The system draws the minimise / maximise / close buttons on a patch the
// page cannot paint (titleBarOverlay).  While a dialog's backdrop covers the
// page, the patch takes the colour white has under it (overlay.ts); white again after.
let overlayNow = '#ffffff';
function updateOverlay(): void {
  const c = overlayColor(false, document.querySelector('dialog[open]') !== null);
  if (c === overlayNow) return;
  overlayNow = c;
  void api.setOverlay(c === '#ffffff' ? null : c);
}
new MutationObserver(updateOverlay).observe(document.body, { subtree: true, childList: true, attributes: true, attributeFilter: ['class', 'open'] });

// ---- keys ---------------------------------------------------------------------------------

window.addEventListener('keydown', (e) => {
  if (document.querySelector('dialog[open]')) return; // the dialog has the keys (Esc closes it)
  const mod = e.ctrlKey || e.metaKey;
  if (e.key === 'F5') { e.preventDefault(); void run(); return; }
  if (e.key === 'F10') { e.preventDefault(); void cycles(1); return; }
  if (!mod) return;
  const k = e.key.toLowerCase();
  if (k === 'n') { e.preventDefault(); void newCircuit(); }
  else if (k === 'o') { e.preventDefault(); void openFile(); }
  else if (k === 's') { e.preventDefault(); void save(e.shiftKey); }
  else if (k === 'z' && !e.shiftKey) { e.preventDefault(); void edit('edit.undo', 'Undo'); }
  else if (k === 'y' || (k === 'z' && e.shiftKey)) { e.preventDefault(); void edit('edit.redo', 'Redo'); }
}, true);

// ---- start ----------------------------------------------------------------------------------

async function begin(): Promise<void> {
  new ResizeObserver(() => layout()).observe(document.body);
  (navigator as unknown as { windowControlsOverlay?: EventTarget }).windowControlsOverlay
    ?.addEventListener('geometrychange', () => layout());
  void document.fonts.ready.then(() => layout());
  engine = await api.engineStatus();
  // A file named on the command line opens instead of the first screen.
  const startup = await api.startupFile();
  opening = startup !== null;
  decided = true;
  render();
  if (engine.state === 'failed') void engineFailed();
  if (startup) {
    let r: Opened | null = null;
    let err: unknown;
    try { r = await api.openStartupFile(); } catch (e) { err = e; }
    opening = false;
    openedOrError(r, err);
    render();
  }
}
void begin();
