/* The window.

     title bar   logo, name, file, the toolbar (its own row under the bar
                 when the bar cannot hold it); New, Open, About at the right
                 end; the system's caption buttons (titleBarOverlay)
     band        one line, only when there is something the student must
                 not miss (the engine stopped and what came back, or it
                 could not start)
     work        the first screen (start.ts), or once a file is open:
                   left    Components | Circuits   over   Tunnels | Minimap
                   center  file tabs, circuit tabs, Canvas
                           over Messages | Cycle View | Console
                   right   Attributes
                 A narrow window (layout.ts) moves Attributes into the left
                 panel's tabs.  Splitters between them; sizes for this run.
     status bar  facts only

   The engine (a Java process the main process runs, docs/engine-api.md)
   has the circuits; this page shows what it says.  The Canvas
   (../canvas/, N-05) draws the circuit from the engine's snapshot, its
   changes and its values.  Panels with nothing to show say, in one
   sentence, what fills them.

   Nothing is restored from an earlier run and nothing is written but the
   files the student saves (the lab-PC rule; src/main/main.ts). */

import type { CircuitRef, ConsoleUpdate, DiagList, DiagMessage, EngineStatus, LibraryGroup, MipsFacts, ModelChanged, NewResult, RecordState, Recovered, Reloaded, RunUntilDone, SimState, SimValues, Snapshot } from '../../main/protocol.ts';
import { CircuitCanvas } from '../canvas/canvas.ts';
import { legend } from '../canvas/legend.ts';
import { Scene } from '../canvas/scene.ts';
import type { View } from '../canvas/view.ts';
import { zoomControl } from '../canvas/zoom.ts';
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
import { consolePanel } from './console.ts';
import { CycleView, type PinSpot } from './cycleview.ts';
import { cycleFacts } from './logic/cycle.ts';
import { commandError, fileError } from './logic/errors.ts';
import { circuitFacts, count, counted, engineFact, engineVersion } from './logic/facts.ts';
import { Files, type OpenFile } from './logic/files.ts';
import { arrange, nothingDragged, PAD, SPLITTER } from './logic/layout.ts';
import { messageCount } from './logic/messages.ts';
import { messagesPanel } from './messages.ts';
import { programs as programController } from './program.ts';
import { emitReveal, onReveal, type Reveal } from './reveal.ts';
import { recoveredText } from './logic/recovered.ts';
import { startScreen } from './start.ts';

const api = window.app;
const APP_NAME = 'Hallym Circuit Studio';

// ---- state ------------------------------------------------------------------

let engine: EngineStatus = { state: 'starting', generation: 0, hello: null, error: null, detail: null };
const files = new Files();
// The circuits drawn: `${fileId} ${circuitId}`, and inside a subcircuit instance
// `${fileId} ${circuitId} ${instance ids}` (its values are that instance's).
const scenes = new Map<string, Scene>();
const views = new Map<string, View>();                 // each scene's zoom and pan, for this run
// Inside a subcircuit instance: per circuit tab, the instances gone into and their names.
const inside = new Map<string, { ids: string[]; names: string[]; circuits: string[] }>();
const watching = new Map<string, string>();            // fileId → the scene sim.watch was sent for
let pendingReveal: { k: string; r: Reveal } | null = null; // a message's place, marked once its scene is drawn
const libraries = new Map<string, LibraryGroup[] | string>(); // by fileId; a string: why there is none
const diags = new Map<string, DiagMessage[]>();         // Messages by fileId (diag.list, diag.changed; D-143)
let decided = false;                                    // whether a file was named on the command line is known
let opening = false;                                    // a file on its way (the command line's)
let note: { cls: '' | 'err' | 'ok'; text: string } | null = null; // the last action's fact
let dragged = nothingDragged();
let bottomCollapsed = false;
let startSeen = false;
let untitled = 0;
let cycleFile: string | null = null;                     // the file the Cycle View shows
// The clock's speed, ticks per second: v1's list (and the engine's, docs/engine-api.md sim.run).
const FREQUENCIES: [string, number][] = [['1 Hz', 1], ['4 Hz', 4], ['16 Hz', 16], ['64 Hz', 64], ['256 Hz', 256], ['1 kHz', 1024], ['4 kHz', 4096]];

const key = (fileId: string, circuitId: string) => `${fileId} ${circuitId}`;
const sceneKey = (fileId: string, circuitId: string, path: string[] = []) => (path.length ? `${key(fileId, circuitId)} ${path.join('/')}` : key(fileId, circuitId));

// ---- the Canvas ------------------------------------------------------------------------

const board = new CircuitCanvas({
  onView: (v) => zoomCtl.update(v.zoom),
  onEnter: (id) => enterInstance(id),
});
const zoomCtl = zoomControl({
  zoom: () => board.view.zoom, zoomTo: (z) => board.zoomTo(z), fit: () => board.fitView(), step: (d) => board.zoomStep(d),
});
const wireLegend = legend({ busWidths: true, onBusWidths: (on) => { board.busWidths = on; board.invalidate(); } });
let boardKey = '';

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
// A new speed while the clock runs applies at once (as v1's menu did).
frequency.addEventListener('change', () => { if (files.active()?.sim?.ticking) void simCall('sim.run', { on: true, hz: Number(frequency.value) }, 'Run'); });
// Load Program… (N-16): an executable image (.hmx) into the circuit's memories (program.ts).
const bLoad = button('Load Program…', 'file-code', '', () => { const f = files.active(); if (f) void programs.load(f.fileId); });
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
// While a reload of the program has failed: what is on show and since when (N-16, program.ts).
const programBand = band('progband');
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
// The Console tab (N-16, console.ts): every Console part's output, streamed by the engine.
const consoleView = consolePanel(consoleBody);
const bottomHead = tabsHead(['Messages', 'Cycle View', 'Console'], (i) => { showBody(bottomBodies, i); if (bottomCollapsed) toggleBottom(); cycleShown(); });
// The Cycle View (N-14): the table, Registers | Memory | Instruction, Run Until (cycleview.ts).
const cycleView = new CycleView({
  call: (method, params) => api.call(method, params),
  fileId: () => files.active()?.fileId ?? null,
  ready: () => engine.state === 'ready',
  note: (cls, text) => { note = text ? { cls, text } : null; renderStatus(); },
  dirty: (fileId, dirty) => { files.setDirty(fileId, dirty); render(); },
  changed: () => { renderStatus(); renderToolbarState(); renderCycleBody(); },
});
// The Cycle View tab: its word until there is something to show (no cycle after the first, no row, no
// Instruction Memory), then the view itself.
function renderCycleBody(): void {
  const active = files.active()?.fileId ?? null;
  if (active !== cycleFile) { cycleFile = active; cycleView.fileChanged(); }
  if (!files.active() || cycleView.nothingYet(active)) {
    if (!cycleBody.isEmpty() || cycleBody.root.dataset.empty !== 'true') {
      cycleBody.empty({ title: '아직 사이클이 없습니다', body: '1 Cycle이나 Run으로 클럭을 진행하면 사이클마다 값이 여기에 쌓입니다.' });
    }
  } else if (cycleBody.root.firstChild !== cycleView.root) {
    cycleBody.fill(cycleView.root);
  }
  cycleShown();
}
// Whether the Cycle View is on screen (its tab chosen, the panel open, a file open): it asks the engine only then.
function cycleShown(): void {
  cycleView.setVisible(bottomHead.selected() === 1 && !bottomCollapsed && files.active() !== null && cycleBody.root.firstChild === cycleView.root);
}
const bCollapse = headButton('Collapse', 'Collapse the panel', () => toggleBottom());
bottomHead.aside.append(bCollapse);
const bottomPanel = h('section', { class: 'panel bottom', 'aria-label': 'Messages' }, bottomHead.root, ...bottomBodies);
// Messages (messages.ts): choosing one sends "show this place" (reveal.ts) for the Canvas and the Cycle View.
const messages = messagesPanel({
  host: messagesBody, tab: bottomHead.tabs[0],
  onReveal: (r) => emitReveal(r),
  onReset: () => void resetSimulation(),
});

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

document.body.append(h('div', { class: 'app' }, bar.root, bar.row, h('div', { class: 'bands' }, notices.root, programBand.root), work, status), zoomCtl.menu, wireLegend.panel);

function toggleBottom(): void {
  bottomCollapsed = !bottomCollapsed;
  bottomPanel.classList.toggle('collapsed', bottomCollapsed);
  bCollapse.textContent = bottomCollapsed ? 'Expand' : 'Collapse';
  bCollapse.title = bottomCollapsed ? 'Expand the panel' : 'Collapse the panel';
  cycleShown();
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

// While Run Until runs (the Cycle View's bar says Stop), the clock's own buttons wait.
function renderToolbarState(): void {
  const f = files.active();
  const until = cycleView.running(f?.fileId ?? null);
  const off = !f || engine.state !== 'ready';
  bRun.disabled = off || until;
  bCycle.disabled = off || until;
}

function render(): void {
  const f = files.active();
  document.title = f ? `${f.name}${f.dirty ? ' •' : ''} — ${APP_NAME}` : APP_NAME;
  bar.setFile(f ? f.name : null, f?.dirty ?? false);
  bar.showToolbar(f !== null);
  const ready = engine.state === 'ready';
  for (const b of [bSave, bUndo, bRedo, bRun, bCycle, bReset]) b.disabled = !f || !ready;
  renderToolbarState();
  frequency.disabled = !f || !ready;
  bCycles.disabled = true;   // N-07: the count to go
  bLoad.disabled = !f || !ready;
  const ticking = f?.sim?.ticking ?? false;
  bRun.replaceChildren(icon(ticking ? 'square' : 'play'), h('span', { class: 'label' }, ticking ? 'Stop' : 'Run'), h('kbd', {}, 'F5'));
  bRun.title = ticking ? 'Stop (F5)' : 'Run (F5)';
  bRun.classList.toggle('primary', ticking);
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
  renderMessages();
  consoleView.show(f?.fileId ?? null);
  renderProgramBand();
  renderStatus();
  layout();
}

// The band over the work while the active file's program could not be loaded again (N-16).
function renderProgramBand(): void {
  const f = files.active();
  const b = f ? programs.band(f.fileId) : null;
  if (b) programBand.show(b.text, 'warn', b.title);
  else if (programBand.text() !== null) programBand.hide();
}

function renderMessages(): void {
  const f = files.active();
  const list = f ? diags.get(f.fileId) ?? null : null;
  messages.set(f ? { fileId: f.fileId, list, circuitName: (id) => files.circuitName(f, id) } : null);
  // No character on screen while the circuit on show has messages: they say it cannot work (app.css, D-143).
  document.body.classList.toggle('messages-shown', (list?.length ?? 0) > 0);
}

async function loadDiags(fileId: string): Promise<void> {
  try {
    const r = await api.call<DiagList>('diag.list', { fileId });
    if (files.get(fileId)) diags.set(fileId, r.messages);
  } catch {
    // An older engine without diag.* (before N-13): no list, the panel stays as it is.
    return;
  }
  if (files.active()?.fileId === fileId) { renderMessages(); renderStatus(); }
}

// The status bar's Messages count opens the Messages tab (v1).
function showMessages(): void {
  bottomHead.select(0);
  showBody(bottomBodies, 0);
  if (bottomCollapsed) toggleBottom();
}

// A message with a cycle (v1 D-05, V-03): the Cycle View comes forward at that cycle, with the
// message's cause and the place its E/X appeared as the table's top rows (record.pin).
function pinMessage(r: Reveal): void {
  if (r.cycle === null || r.messageId === null || !files.get(r.fileId)) return;
  const m = diags.get(r.fileId)?.find((x) => x.id === r.messageId);
  const spots: PinSpot[] = [];
  if (r.at) spots.push({ circuitId: r.circuitId, path: r.path, at: r.at });
  if (m?.appeared) spots.push({ circuitId: m.appeared.circuitId, path: [...m.appeared.path], at: m.appeared.at });
  if (spots.length === 0) return;
  bottomHead.select(1);
  showBody(bottomBodies, 1);
  if (bottomCollapsed) toggleBottom();
  cycleShown();
  void cycleView.pinMessage(r.fileId, r.messageId, r.cycle, spots);
}

function renderCircuits(f: OpenFile): void {
  circuitsBody.fill(h('ul', { class: 'list' }, ...f.circuits.map((c: CircuitRef) => {
    // The main circuit (the one Logisim simulates first): a mark, not a second "main".
    const b = h('button', { type: 'button', title: c.circuitId === f.main ? `${c.name} (main circuit)` : c.name }, h('span', { class: 'mono' }, c.name),
      c.circuitId === f.main ? h('span', { class: 'mainmark', role: 'img', 'aria-label': 'Main circuit', title: 'Main circuit' }, icon('house')) : null);
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
    componentsBody.empty({ title: '부품 목록을 받지 못했습니다', body: '엔진이 이 파일의 부품 목록을 보내지 않았습니다. 파일을 닫았다가 다시 열어 보세요.' });
  } else {
    // The first group is this file's circuits (lib null); the bundled Hallym MIPS is listed before it is in the file (pending).
    componentsBody.fill(...lib.map((g, i) => h('details', { class: 'libgroup', open: i < 2 },
      h('summary', {}, g.lib === null ? f.name : g.display ?? g.lib, g.pending ? h('span', { class: 'dim', title: 'Placed first, it is added to the file' }, 'not in the file yet') : null,
        h('span', { class: 'count' }, count(g.tools.length))),
      h('ul', { class: 'list' }, ...g.tools.map((t) => h('li', {}, h('span', { class: 'item', title: t.name }, t.circuitId ? code(t.display) : t.display)))))));
  }
}

async function loadLibrary(fileId: string): Promise<void> {
  try {
    libraries.set(fileId, await api.call<LibraryGroup[]>('model.library', { fileId }));
  } catch (e) {
    libraries.set(fileId, (e as CallError).message ?? 'failed');
  }
  if (files.active()?.fileId === fileId) renderComponents(files.active()!);
}

let wanted = '';
// The circuit (or the subcircuit instance gone into) the file's tab shows.
function shown(f: OpenFile): { k: string; root: string; path: string[]; circuit: string; names: string[] } {
  const inst = inside.get(key(f.fileId, f.circuit));
  const path = inst?.ids ?? [];
  return { k: sceneKey(f.fileId, f.circuit, path), root: f.circuit, path, circuit: inst?.circuits.at(-1) ?? f.circuit, names: inst?.names ?? [] };
}

function shownSnapshot(f: OpenFile): Snapshot | null {
  return scenes.get(shown(f).k)?.snapshot() ?? null;
}

function renderCanvas(f: OpenFile): void {
  const w = shown(f);
  const scene = scenes.get(w.k);
  canvasBody.root.dataset.circuit = files.circuitName(f, f.circuit);
  if (!scene) {
    canvasBody.fill();
    renderTunnels(null);
    if (wanted !== w.k) { wanted = w.k; void loadScene(f.fileId, w.circuit, w.k); }
    return;
  }
  const s = scene.snapshot();
  const facts = circuitFacts(s);
  if (facts.components === 0 && facts.wires === 0 && !w.path.length) {
    canvasBody.empty({ title: '빈 회로입니다', body: '부품과 선을 놓으면 여기 Canvas에 그려집니다.', pose: 'haram-hari-guide' });
  } else {
    if (canvasBody.isEmpty() || board.root.parentElement !== canvasBody.root) canvasBody.fill(board.root);
    if (board.scene !== scene) {
      if (board.scene && boardKey) views.set(boardKey, { ...board.view });
      boardKey = w.k;
      board.setScene(scene, views.get(w.k));
    }
    board.setCrumbs(w.path.length ? [files.circuitName(f, f.circuit), ...w.names] : [], (i) => leaveInstance(f, i));
    if (pendingReveal?.k === w.k) { board.reveal(pendingReveal.r); pendingReveal = null; }
  }
  void watch(f.fileId, w.root, w.path, w.k);
  renderTunnels(s);
}

async function loadScene(fileId: string, circuitId: string, k: string): Promise<void> {
  try {
    scenes.set(k, new Scene(fileId, await api.call<Snapshot>('model.circuit', { fileId, circuitId })));
  } catch (e) {
    note = { cls: 'err', text: commandError('Canvas', e as CallError) };
  }
  if (wanted === k) wanted = '';
  const f = files.active();
  if (f && shown(f).k === k) { renderCanvas(f); renderStatus(); }
}

// The values of what the Canvas shows (docs/engine-api.md sim.watch): one circuit a file.
async function watch(fileId: string, circuitId: string, path: string[], k: string): Promise<void> {
  if (watching.get(fileId) === k || engine.state !== 'ready') return;
  watching.set(fileId, k);
  try {
    await api.call('sim.watch', path.length ? { fileId, circuitId, path } : { fileId, circuitId });
  } catch (e) {
    watching.delete(fileId);
    note = { cls: 'err', text: commandError('Canvas', e as CallError) };
    renderStatus();
  }
}

// Double click on a subcircuit instance: its circuit, with that instance's values (Logisim's way in).
function enterInstance(id: string): void {
  const f = files.active();
  const scene = board.scene;
  const c = scene?.components.get(id);
  if (!f || !c || c.subcircuit === undefined) return;
  const base = key(f.fileId, f.circuit);
  const now = inside.get(base) ?? { ids: [], names: [], circuits: [] };
  const name = c.attrs.label || files.circuitName(f, c.subcircuit) || c.name;
  inside.set(base, { ids: [...now.ids, id], names: [...now.names, name], circuits: [...now.circuits, c.subcircuit] });
  render();
}

// A crumb: back out to that level (0: the tab's own circuit).
function leaveInstance(f: OpenFile, level: number): void {
  const base = key(f.fileId, f.circuit);
  const now = inside.get(base);
  if (!now) return;
  if (level === 0) inside.delete(base);
  else inside.set(base, { ids: now.ids.slice(0, level), names: now.names.slice(0, level), circuits: now.circuits.slice(0, level) });
  render();
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
  renderCycleBody();
}

function renderStatus(): void {
  const parts: Node[] = [];
  const span = (cls: string, ...c: (Node | string)[]) => h('span', { class: cls }, ...c);
  const ef = engineFact(engine);
  if (ef) parts.push(span(ef.cls, ef.text));
  const f = files.active();
  // Facts are names, in English (Ready, 35 components, Cycle 2, Running); a sentence to the student is Korean.
  if (opening) parts.push(span('', 'Opening file'));
  if (!f && engine.state === 'ready' && !opening) parts.push(span('', 'Ready'));
  if (f) {
    const s = shownSnapshot(f);
    parts.push(span('', code([files.circuitName(f, f.circuit), ...shown(f).names].join(' › ')),
      s ? ` · ${counted(s.components.length, 'component')} · ${counted(s.wires.length, 'wire')}` : ''));
    const list = diags.get(f.fileId);
    if (list) {
      const b = h('button', { type: 'button', class: `msgcount${list.length ? ' err' : ''}`, title: 'Messages' }, messageCount(list.length));
      b.addEventListener('click', () => showMessages());
      parts.push(b);
    }
    // The cycle on show and PC (N-14): the recording's, "Cycle 5 / 12" on a past cycle; the clock's count before it is known.
    const rec = cycleView.state(f.fileId) ?? null;
    const facts = cycleFacts(rec, f.sim ? f.sim.cycle : null);
    if (facts.cycle) parts.push(span(facts.past ? 'warn' : '', facts.cycle));
    if (facts.pc) parts.push(span('', code(facts.pc)));
    if (rec?.runUntil) parts.push(span('run', 'Running (Run Until)'));
    if (f.sim) {
      const speed = FREQUENCIES.find(([, hz]) => hz === f.sim?.hz)?.[0];
      if (f.sim.ticking) parts.push(span('run', speed ? `Running (${speed})` : 'Running'));
      if (!f.sim.running) parts.push(span('err', f.sim.oscillating ? '발진으로 시뮬레이션이 꺼졌습니다' : '시뮬레이션이 꺼져 있습니다'));
    }
    // The program: its name, PC ≠ entry at cycle 0, an old Stack, a .s path (facts, not messages; N-16).
    parts.push(...programs.statusNodes(f.fileId));
  }
  if (note) parts.push(span(note.cls, note.text));
  parts.push(span('grow'));
  if (f && board.scene && board.root.isConnected) parts.push(wireLegend.button, zoomCtl.button);
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
  void loadDiags(f.fileId);
  void programs.refresh(f.fileId);
  void loadConsole(f.fileId);
}

async function newCircuit(): Promise<void> {
  if (!(await engineReady())) return;
  try {
    const r = await api.call<NewResult>('file.new');
    untitled += 1;
    added({ fileId: r.fileId, name: untitled === 1 ? 'untitled.circ' : `untitled-${untitled}.circ`, path: null, circuits: r.circuits, main: r.main });
  } catch (e) {
    fileErrorDialog('new', e);
  }
}

// A file that could not be opened, saved or made: the window's own words (logic/errors.ts), no character.
function fileErrorDialog(action: 'open' | 'save' | 'new', e: unknown, name?: string): void {
  const d = fileError(action, e as CallError, name);
  void ask({ title: d.title, file: d.file, body: d.body, detail: d.detail, ok: 'Close', cancel: null, character: false });
}

function openedOrError(r: Opened | null, e?: unknown): void {
  if (e) {
    fileErrorDialog('open', e);
    return;
  }
  if (!r) return;
  if (r.already && files.get(r.fileId)) { showFile(r.fileId); return; }
  added({ fileId: r.fileId, name: r.name, path: r.path, circuits: r.circuits, main: r.main });
  // What the original loader would have shown in its dialogs (e.g. a component it does not know).
  if (r.messages?.length) {
    note = { cls: 'err', text: `불러오며 알린 것 ${r.messages.length}개 — ${r.messages[0]}` };
    renderStatus();
  }
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
    note = { cls: 'ok', text: `저장했습니다 · ${r.name}${r.needsMipsJar ? ' · 원조 Logisim 2.7.1에서 열려면 옆에 hcs-mips.jar가 있어야 합니다' : ''}` };
  } catch (e) {
    note = null;
    fileErrorDialog('save', e, f.name);
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
      body: '닫으면 저장하지 않은 내용은 사라집니다. 남기려면 Cancel을 누르고 저장하세요(Ctrl+S).', ok: 'Discard', cancel: 'Cancel', danger: true,
    });
    if (!go) return;
  }
  if (engine.state === 'ready') await api.call('file.close', { fileId }).catch(() => {});
  files.close(fileId);
  for (const m of [scenes, views, inside]) for (const k of [...m.keys()]) if (k.startsWith(`${fileId} `)) m.delete(k);
  watching.delete(fileId);
  if (board.scene?.fileId === fileId) { board.setScene(null); boardKey = ''; }
  cycleView.forget(fileId);
  libraries.delete(fileId);
  diags.delete(fileId);
  programs.drop(fileId);
  consoleView.drop(fileId);
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
    note = { cls: 'err', text: commandError(name, e as CallError) };
  }
  renderStatus();
}

async function simCall(method: 'sim.run' | 'sim.cycles' | 'sim.reset' | 'sim.enable', params: Record<string, unknown>, name: string): Promise<void> {
  const f = files.active();
  if (!f || engine.state !== 'ready') return;
  try {
    await api.call(method, { fileId: f.fileId, ...params });
    note = null;
  } catch (e) {
    note = { cls: 'err', text: commandError(name, e as CallError) };
  }
  renderStatus();
}
const run = () => simCall('sim.run', { on: !(files.active()?.sim?.ticking ?? false), hz: Number(frequency.value) }, 'Run');
const cycles = (n: number) => simCall('sim.cycles', { n }, n === 1 ? '1 Cycle' : 'N Cycles');
const reset = () => simCall('sim.reset', {}, 'Reset');
// Messages' Reset Simulation (an oscillation turned the simulation off): Reset, then on again.
async function resetSimulation(): Promise<void> {
  await simCall('sim.reset', {}, 'Reset');
  await simCall('sim.enable', { on: true }, 'Reset');
}

// ---- the program (N-16) ------------------------------------------------------------------

const programs = programController({
  loadProgram: (fileId, options) => api.loadProgram(fileId, options),
  call: (method, params) => api.call(method, params),
  note: (cls, text) => { note = text ? { cls, text } : null; renderStatus(); },
  changed: (fileId) => { if (files.active()?.fileId === fileId) { renderProgramBand(); renderStatus(); } },
});

// The Console's output so far (then the engine streams it: mips.console).
async function loadConsole(fileId: string): Promise<void> {
  try {
    const r = await api.call<ConsoleUpdate>('mips.console', { fileId });
    if (files.get(fileId)) consoleView.update({ ...r, fileId });
  } catch {
    // an engine before N-16: no Console tab content
  }
}

// ---- the engine ----------------------------------------------------------------------

let failureOpen = false;
async function engineFailed(): Promise<void> {
  notices.show('엔진을 시작하지 못했습니다 · 회로를 만들거나 열 수 없습니다', 'error');
  if (failureOpen) return;
  failureOpen = true;
  const retry = await ask({
    title: '엔진을 시작하지 못했습니다',
    body: '회로를 열고 돌리는 엔진(Java)이 시작되지 않았습니다. 엔진 없이는 회로를 만들거나 열 수 없습니다. 아래에 적힌 파일이 있는지 확인한 뒤 Try Again을 누르세요.',
    detail: engine.detail ?? undefined, ok: 'Try Again', cancel: 'Close', character: false,
  });
  failureOpen = false;
  if (retry) onEngine(await api.retryEngine());
}

function onEngine(s: EngineStatus): void {
  const before = engine;
  engine = s;
  // Restarting lasts until the files are back (the main process says 'ready' after onRecovered).
  if (s.state === 'restarting') notices.show('엔진이 멈춰서 다시 시작하는 중입니다', 'warn');
  if (s.state === 'ready' && before.state !== 'ready' && notices.text()?.startsWith('엔진을 시작하지 못했습니다')) notices.hide();

  render();
  if (s.state === 'failed') void engineFailed();
}

// "Show this place" (a message chosen): the file and the circuit tab here, the
// Canvas (N-05) marks the parts, the instance path; for a message with a cycle
// the Cycle View comes forward at that cycle with its place pinned (N-14).
onReveal((r) => { void revealPlace(r); pinMessage(r); });

// The place of a message (reveal.ts): its circuit -- inside the subcircuit instances when a message the
// simulation found is in one -- then the Canvas marks the parts, wires and nets once that scene is drawn.
async function revealPlace(r: Reveal): Promise<void> {
  const f = files.get(r.fileId);
  if (!f) return;
  files.activate(r.fileId);
  const tab = r.path.length ? r.root : r.circuitId;
  files.openCircuit(r.fileId, tab);
  const base = key(r.fileId, tab);
  if (r.path.length) {
    // the instances' names and circuits, from the snapshots down the path
    const names: string[] = [], circuits: string[] = [];
    let at = r.root;
    for (const id of r.path) {
      const snap = scenes.get(key(r.fileId, at))?.snapshot()
        ?? await api.call<Snapshot>('model.circuit', { fileId: r.fileId, circuitId: at }).catch(() => null);
      const c = snap?.components.find((x) => x.id === id);
      at = c?.subcircuit ?? r.circuitId;
      circuits.push(at);
      names.push(c?.attrs.label || files.circuitName(f, at) || c?.name || id);
    }
    circuits[circuits.length - 1] = r.circuitId;
    inside.set(base, { ids: [...r.path], names, circuits });
  } else {
    inside.delete(base);
  }
  pendingReveal = { k: sceneKey(r.fileId, tab, r.path), r };
  render();
}

// The engine died and started again, and the main process opened every file
// again with its unsaved edits (src/main/recovery.ts, D-142).  The files keep
// their ids, tabs and the circuit on show; their parts have new ids, so the
// window asks for everything again.  The simulation starts from Reset.
function onRecovered(r: Recovered): void {
  const text = recoveredText(r, (fileId) => files.get(fileId)?.name ?? fileId);
  // every part has a new id: the Canvas's scenes and the instance paths go, and come again from the engine;
  // the files' zoom and pan stay (their file and circuit ids are kept)
  scenes.clear();
  inside.clear();
  watching.clear();
  pendingReveal = null;
  board.setScene(null);
  boardKey = '';
  libraries.clear();
  diags.clear();
  cycleView.forget(null);   // the recordings were the old engine's (the engine sends record.state again)
  wanted = '';
  for (const f of r.closed) { files.close(f.fileId); programs.drop(f.fileId); consoleView.drop(f.fileId); }
  for (const f of r.restored) files.reopened(f.fileId, f.dirty);
  for (const f of r.lost) files.reopened(f.fileId, false);
  if (files.count() === 0) start.go('first');
  note = null;
  notices.show(text.band, 'warn');
  render();
  for (const f of files.list()) void loadDiags(f.fileId);   // their messages name parts by the new ids
  // The program's facts and the Console: the new engine's (N-16; the simulation starts from Reset)
  for (const f of files.list()) { consoleView.drop(f.fileId); void programs.refresh(f.fileId); void loadConsole(f.fileId); }
  void ask({ title: text.title, body: text.body, detail: text.detail || undefined, ok: 'Close', cancel: null, character: false });
}

api.onEngineStatus(onEngine);
api.onEngineRecovered(onRecovered);
api.onNotify((method, params) => {
  const p = params as Record<string, unknown>;
  if (method === 'sim.state') {
    const st = p as unknown as SimState;
    files.setSim(st);
    if (st.fileId === files.active()?.fileId && FREQUENCIES.some(([, hz]) => hz === st.hz)) frequency.value = String(st.hz);
    render();
  } else if (method === 'model.changed') {
    const c = p as unknown as ModelChanged;
    // The engine is the authority: its change goes into every scene of that circuit (and instances of it).
    for (const sc of scenes.values()) if (sc.fileId === c.fileId && sc.circuitId === c.circuitId) sc.applyChange(c);
    libraries.delete(c.fileId); // the first part of a pending library puts it in the file
    watching.delete(c.fileId);  // an edit makes its circuit the simulation's own: watch the shown one again
    if (typeof c.dirty === 'boolean') files.setDirty(c.fileId, c.dirty);
    board.invalidate();
    render();
  } else if (method === 'mips.facts') {
    if (files.get(String(p.fileId))) programs.facts(p as unknown as MipsFacts);
  } else if (method === 'mips.reloaded') {
    if (files.get(String(p.fileId))) { programs.reloaded(p as unknown as Reloaded); renderStatus(); }
  } else if (method === 'mips.console') {
    if (files.get(String(p.fileId))) consoleView.update(p as unknown as ConsoleUpdate);
  } else if (method === 'diag.changed') {
    const fileId = String(p.fileId);
    if (!files.get(fileId)) return;
    diags.set(fileId, (p as unknown as DiagList).messages);
    cycleView.messagesChanged(fileId, (p as unknown as DiagList).messages.map((m) => m.id));
    if (files.active()?.fileId === fileId) { renderMessages(); renderStatus(); }
  } else if (method === 'sim.values') {
    const v = p as unknown as SimValues;
    const sc = scenes.get(v.path?.length ? sceneKey(v.fileId, v.root ?? v.circuitId, v.path) : sceneKey(v.fileId, v.circuitId));
    if (sc && sc.circuitId === v.circuitId) {
      sc.applyValues(v);
      if (board.scene === sc) board.invalidate();
    }
  } else if (method === 'record.state') {
    cycleView.onState(p as unknown as RecordState);
  } else if (method === 'record.runUntil') {
    cycleView.onRunUntil(p as unknown as RunUntilDone);
  } else if (method === 'engine.log') {
    // The engine's log is English, for developers: the window says what it means
    // from sim.state (an oscillation) and from the answers; the log goes to the console only.
    console.info(`[engine] ${String(p.level)}: ${String(p.message)}`);
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
  if (board.scene && board.root.isConnected && board.zoomKey(e)) { e.preventDefault(); return; }
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
