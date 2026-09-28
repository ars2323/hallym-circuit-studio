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
   sentence, what fills them.  Finding and placing (N-12, D-150): the
   Components list and its search (components.ts), the search palette
   (Ctrl+K, palette.ts), Find (Ctrl+F, find.ts), Tunnels (tunnels.ts), the
   Minimap (minimap.ts) and the Splitter editor (splitter-editor.ts); the
   events they meet the Canvas on are in tool-events.ts.

   Circuits, appearances, libraries and windows (N-11, D-153): the Circuits
   panel (circuits.ts) and what its commands do (circuit-control.ts, the
   dialogs in circuit-dialogs.ts), a circuit tab's Layout | Appearance switch
   and the appearance editor (appearance-editor.ts), the band over a
   subcircuit opened on its own (Go to Instance in main), file tabs of the
   same name told apart by their folder, a file tab's right click (Detach
   Tab, View Side by Side, Attach Tab, Close) and a tab dragged onto the
   Canvas (that file as a library, its main circuit placed) or out of the
   window (a window of its own: src/main/windows.ts).  A window of its own
   holds one file; Ctrl+W closes the focused window's file tab.

   Nothing is restored from an earlier run and nothing is written but the
   files the student saves (the lab-PC rule; src/main/main.ts) -- and, for
   a file saved at least once, its recovery file beside it until it is
   saved or closed: opening it again after the app died asks first
   (logic/recovery-ask.ts, N-19).  Every setting is for this run only
   (logic/run-settings.ts). */

import type { AppearanceEdit, CircuitRef, Component, ConsoleUpdate, DiagList, DiagMessage, EditSelection, EngineStatus, FileInfo, FindResult, InstancesInfo, LibrariesInfo, LibraryGroup, LibraryUpdated, MipsFacts, ModelChanged, NewResult, Point, PortImpact, RecordState, Recovered, RecoveryAsk, Reloaded, RunUntilDone, SimState, SimValues, Snapshot, Wire } from '../../main/protocol.ts';
import type { Handover } from '../../main/windows.ts';
import { type MenuEntry, SEPARATOR, showMenu } from '../canvas/overlays/menu.ts';
import { AppearanceEditor } from './appearance-editor.ts';
import { CircuitControl } from './circuit-control.ts';
import { circuitsPanel } from './circuits.ts';
import { distinguishers, libraryUpdatedText, pinAddText, pinPreviewText, portImpactText, frozenPinText, newStateNote, newStateQuestion, type SimNode, type SimPart, simTree, standaloneText } from './logic/circuits.ts';
import { CircuitCanvas } from '../canvas/canvas.ts';
import { emitTool } from '../canvas/events.ts';
import { legend } from '../canvas/legend.ts';
import { Overlays } from '../canvas/overlays/controller.ts';
import { Scene } from '../canvas/scene.ts';
import { toCircuit, type View, visible } from '../canvas/view.ts';
import { zoomControl } from '../canvas/zoom.ts';
import { aboutDialog } from '../shared/about.ts';
import { ask, choose } from '../shared/ask.ts';
import { band } from '../shared/band.ts';
import { code, codeText, h, icon } from '../shared/dom.ts';
import { noticeHost } from '../shared/notice.ts';
import { overlayColor } from '../shared/overlay.ts';
import { splitter } from '../shared/splitter.ts';
import { button, iconButton, titleBar } from '../shared/titlebar.ts';
import { headButton, panelHead, tabStrip, tabsHead } from '../shared/ui.ts';
import type { CallError, Opened } from './api.ts';
import { componentsPanel, type Pick, TOOL_MIME } from './components.ts';
import { consolePanel } from './console.ts';
import { CycleView, type PinSpot } from './cycleview.ts';
import { StatusKeeper } from './logic/engine-status.ts';
import { selectionFacts } from './logic/selection-facts.ts';
import { cycleFacts } from './logic/cycle.ts';
import { askCycles } from './cycles-dialog.ts';
import { Editor, type MenuCommand, type ToolName } from './editor.ts';
import { TOOLBAR_PARTS } from './logic/editing.ts';
import { findWindow } from './find.ts';
import { revealOfPlace } from './logic/find.ts';
import type { CommandId, SearchItem } from './logic/search.ts';
import { fromAttrs, initialSplit } from './logic/splitter.ts';
import { minimapPanel } from './minimap.ts';
import { palette } from './palette.ts';
import { splitterEditor } from './splitter-editor.ts';
import { type EditSplitter, emitPlaceTool, emitSelection, onEditSplitter, onPlaceTool, type PlaceTool, snap } from './tool-events.ts';
import { tunnelsPanel } from './tunnels.ts';
import { askPinValue, valueText } from './value-dialog.ts';
import { commandError, fileError } from './logic/errors.ts';
import { FREQUENCIES, going, resetTurnsOn, runLabel, simBand, simFacts } from './logic/sim.ts';
import { circuitFacts, count, counted, engineFact } from './logic/facts.ts';
import { Files, type OpenFile } from './logic/files.ts';
import { arrange, nothingDragged, PAD, SPLITTER } from './logic/layout.ts';
import { messageCount } from './logic/messages.ts';
import { messagesPanel } from './messages.ts';
import { programs as programController } from './program.ts';
import { emitReveal, onReveal, type Reveal } from './reveal.ts';
import { recoveredText } from './logic/recovered.ts';
import { answerRecovery, recoveredNote } from './logic/recovery-ask.ts';
import { settleUnsaved, type Leaving } from './logic/unsaved.ts';
import { RUN_DEFAULTS, RUN_ONLY } from './logic/run-settings.ts';
import { startScreen } from './start.ts';

const api = window.app;
const APP_NAME = 'Hallym Circuit Studio';

// ---- state ------------------------------------------------------------------

let engine: EngineStatus = { state: 'starting', generation: 0, hello: null, error: null, detail: null };
// the first status asked at the start must not undo a newer one pushed meanwhile (logic/engine-status.ts)
const statusKeeper = new StatusKeeper(engine);
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
// the last action's fact (N-11: with a button, e.g. Copy hcs-mips.jar Here)
let note: { cls: '' | 'err' | 'ok'; text: string; action?: { label: string; run: () => void } } | null = null;
// N-11: the circuit tabs that show their appearance (`${fileId} ${circuitId}`); files whose library came in new
// (" · Updated" until the tab is chosen); this window's role (the main one, or a file's own and what it started from)
const appearanceTabs = new Set<string>();
const updatedFiles = new Set<string>();
let role: { main: boolean; handover: Handover | null } = { main: true, handover: null };
const libInfo = new Map<string, LibrariesInfo>();     // model.libraries by fileId (the Open Files group)
const FILE_MIME = 'application/x-hcs-file';
let dragged = nothingDragged();
let bottomCollapsed = false;
let startSeen = false;
let untitled = 0;
let cycleFile: string | null = null;                     // the file the Cycle View shows
let lastCycles: number | null = null;                  // the N Cycles count given last (this run only)

const key = (fileId: string, circuitId: string) => `${fileId} ${circuitId}`;
const sceneKey = (fileId: string, circuitId: string, path: string[] = []) => (path.length ? `${key(fileId, circuitId)} ${path.join('/')}` : key(fileId, circuitId));

// ---- the Canvas ------------------------------------------------------------------------

const board = new CircuitCanvas({
  onView: (v) => zoomCtl.update(v.zoom),
  onEnter: (id) => enterInstance(id),
  onSelect: (ids) => selected(ids),
});
// What is selected on the Canvas, for the panels that follow it (tool-events.ts hcs:selection): the engine's
// selection (edit.selection, N-08) in the circuit on show.
let selection: { fileId: string; circuitId: string; ids: string[] } | null = null;
function selected(ids: string[]): void {
  const f = files.active();
  if (!f) return;
  const w = shown(f);
  selection = { fileId: f.fileId, circuitId: w.circuit, ids };
  emitSelection({ fileId: f.fileId, circuitId: w.circuit, path: w.path, ids });
  renderAttributes();
}
const zoomCtl = zoomControl({
  zoom: () => board.view.zoom, zoomTo: (z) => board.zoomTo(z), fit: () => board.fitView(), step: (d) => board.zoomStep(d),
});
// The Canvas's overlays (N-15, D-151, ../canvas/overlays/): influence, Signal Flow, the active path and the
// field colours while the Cycle View is shown, bus values, signal groups, area memos, a net's highlight.
const overlays = new Overlays({
  board,
  call: (method, params) => api.call(method, params),
  ready: () => engine.state === 'ready',
  cycleViewShown: () => bottomHead.selected() === 1 && !bottomCollapsed && files.active() !== null,
  note: (cls, text) => { note = text ? { cls, text } : null; renderStatus(); },
  failed: (name, e) => { note = { cls: 'err', text: commandError(name, e as CallError) }; renderStatus(); },
  changed: () => { renderStatus(); flowToggle.setAttribute('aria-pressed', String(overlays.settings.onClick)); },
  partMenu: (_at, part) => instanceMenu(part),
});
const wireLegend = legend({ busWidths: RUN_DEFAULTS.busWidths, onBusWidths: (on) => { board.busWidths = on; board.invalidate(); }, extra: overlays.legendRows() });
let boardKey = '';
// The tools in hand on the Canvas (editor.ts): Poke (N-07), and Edit (N-05's click selection until N-08's).
const editor = new Editor({
  board,
  call: (method, params) => api.call(method, params),
  where: () => {
    const f = files.active();
    return f && board.scene ? { fileId: f.fileId, circuitId: board.scene.circuitId } : null;
  },
  ready: () => engine.state === 'ready',
  enter: (id) => enterInstance(id),
  failed: (command, e) => {
    // an input pin inside an instance (I-64): the original's "Create a new circuit state?"
    if (command === 'Poke' && ((e as CallError).data as { reason?: string } | undefined)?.reason === 'frozenPin') { void newStateFor(); return; }
    note = { cls: 'err', text: commandError(command, e as CallError) };
    renderStatus();
  },
  // the tool in hand: the toolbar shows it, the others hear it (hcs:tool, canvas/events.ts)
  toolChanged: (t) => {
    showTool(t);
    emitTool(t);
    const f = files.active();
    if (f) renderCanvas(f);   // an empty circuit shows the Canvas while a part, a wire or a text is being put in
  },
  selectionChanged: (ids) => selected(ids),
  pinValue: (c) => void pinValue(c),
});

// Set Pin Value (I-78): the engine reads the value (sim.pinValue); the dialog shows why it refused one.
async function pinValue(c: Component): Promise<void> {
  const f = files.active();
  const w = editorWhere();
  if (!f || !w || engine.state !== 'ready') return;
  const now = board.scene?.portValue(c.id, 0);
  await askPinValue(c, valueText(now, Number(c.attrs.width ?? '1') || 1), async (text) => {
    try {
      await api.call('sim.pinValue', { fileId: w.fileId, circuitId: w.circuitId, componentId: c.id, value: text });
      return null;
    } catch (e) {
      const err = e as CallError;
      if ((err.data as { reason?: string } | undefined)?.reason === 'badValue') return `그 값은 이 핀(${c.attrs.width ?? '1'} bits)에 넣을 수 없습니다.`;
      return commandError('Set Pin Value', err);
    }
  });
}
function editorWhere(): { fileId: string; circuitId: string } | null {
  const f = files.active();
  return f && board.scene ? { fileId: f.fileId, circuitId: board.scene.circuitId } : null;
}

// A part to place (hcs:place-tool, from the Components list, the palette, a drop): the placing tool takes it
// (the Canvas's gesture places it) or, with a point, it is placed there now; the Edit tool again after.
onPlaceTool((p: PlaceTool, e) => {
  const f = files.active();
  if (!f || p.fileId !== f.fileId || e.defaultPrevented) return;
  e.preventDefault();
  if (!p.at) { editor.hold({ lib: p.lib, name: p.name, ...(p.attrs ? { attrs: p.attrs } : {}) }); return; }
  void (async () => {
    if (engine.state !== 'ready') return;
    try {
      await api.call('edit.addComponent', { fileId: p.fileId, circuitId: p.circuitId, lib: p.lib, name: p.name, loc: p.at, ...(p.attrs && Object.keys(p.attrs).length ? { attrs: p.attrs } : {}) });
      note = null;
      editor.setTool('Edit');
    } catch (err) {
      note = { cls: 'err', text: commandError(p.name, err as CallError) };
    }
    renderStatus();
  })();
});

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
// Signal Flow is a switch (Signal Flow on Click, Ctrl+Shift+F; v1 I-188), not a tool: it stays with the tools.
const flowToggle = toolButtons[TOOLS.length - 1];
flowToggle.setAttribute('role', 'button');
flowToggle.removeAttribute('aria-checked');
flowToggle.classList.add('flowtoggle');
flowToggle.title = 'Signal Flow on Click (Ctrl+Shift+F)';
flowToggle.addEventListener('click', () => overlays.toggleOnClick());
// The tools in hand (editor.ts, edit-tools.ts): Edit, Poke, Wire, Text, and Pin, Tunnel, Probe (a Wiring part held,
// as the Components list holds one); Signal Flow is N-15's.
const WORKING_TOOLS = new Set<string>(['Edit', 'Poke', 'Wire', 'Text', 'Pin', 'Tunnel', 'Probe']);
toolButtons.forEach((b, i) => {
  const name = TOOLS[i][0];
  if (!WORKING_TOOLS.has(name)) return;
  const part = TOOLBAR_PARTS[name];
  b.addEventListener('click', () => (part ? editor.hold({ lib: part.lib, name: part.name }) : editor.setTool(name as ToolName)));
});
function showTool(t: string): void {
  const held = editor.tool === 'Place' ? editor.place.held : null;
  toolButtons.forEach((b, i) => {
    if (b === flowToggle) return;   // a switch, not a tool (N-15)
    const name = TOOLS[i][0];
    const part = TOOLBAR_PARTS[name];
    const on = part ? !!held && held.lib === part.lib && held.name === part.name : name === t;
    b.classList.toggle('on', on);
    b.setAttribute('aria-checked', String(on));
  });
}
const bSave = iconButton('Save (Ctrl+S)', 'save', () => void save(false));
const bUndo = iconButton('Undo (Ctrl+Z)', 'undo-2', () => void edit('edit.undo', 'Undo'));
const bRedo = iconButton('Redo (Ctrl+Y)', 'redo-2', () => void edit('edit.redo', 'Redo'));
const bRun = button('Run', 'play', 'F5', () => void run());
const bCycle = button('1 Cycle', 'step-forward', 'F10', () => void cycles(1));
const bCycles = button('N Cycles', 'fast-forward', '', () => void nCycles());
const bReset = button('Reset', 'rotate-ccw', '', () => void reset());
const frequency = h('select', { title: `Clock speed · ${RUN_ONLY}`, 'aria-label': 'Clock speed' },
  ...FREQUENCIES.map(([label, hz]) => h('option', { value: String(hz), selected: hz === RUN_DEFAULTS.hz }, label)));
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
// While the simulation is off (an oscillation, or Ctrl+E): values do not change (N-07, logic/sim.ts).
const simOffBand = band('simband');
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
const attributesBody = noticeHost('side attributes');
const upperBodies = [componentsBody.root, circuitsBody.root, attributesBody.root];
const upperHead = tabsHead(['Components', 'Circuits', 'Attributes'], (i) => showUpper(i));
const upperPanel = h('section', { class: 'panel upper', 'aria-label': 'Components' }, upperHead.root, ...upperBodies);
// Left, lower: Tunnels | Minimap
const tunnelsBody = noticeHost('side');
const minimapBody = noticeHost('side');
const lowerBodies = [tunnelsBody.root, minimapBody.root];
const lowerHead = tabsHead(['Tunnels', 'Minimap'], (i) => { showBody(lowerBodies, i); minimap.show(i === 1); });
const lowerPanel = h('section', { class: 'panel lower', 'aria-label': 'Tunnels' }, lowerHead.root, ...lowerBodies);
// Right: Attributes
const rightHead = panelHead('Attributes');
const rightPanel = h('section', { class: 'panel right', 'aria-label': 'Attributes' }, rightHead.root);
// Center: the files, the circuits, the Canvas
const fileStrip = tabStrip({
  label: 'Files', closable: true, onSelect: (id) => showFile(id), onClose: (id) => void closeFile(id),
  onMenu: (id, x, y) => fileTabMenu(id, x, y),
  // a tab dragged onto this window's Canvas: that file as a library (P-03); out of the window: its own window (I-180)
  drag: {
    mime: FILE_MIME, data: (id) => JSON.stringify({ fileId: id }),
    end: (id, e) => {
      const out = e.screenX < window.screenX || e.screenY < window.screenY || e.screenX > window.screenX + window.outerWidth || e.screenY > window.screenY + window.outerHeight;
      if (out && e.dataTransfer?.dropEffect === 'none' && role.main && files.count() > 1) void detachFile(id, 'window');
    },
  },
});
const circuitStrip = tabStrip({
  label: 'Circuit tabs', closable: true, canClose: (items) => items.length > 1,
  onSelect: (id) => { const f = files.active(); if (f) { files.openCircuit(f.fileId, id); render(); } },
  onClose: (id) => { const f = files.active(); if (f) { files.closeCircuit(f.fileId, id); render(); } },
});
const canvasBody = noticeHost('canvas');
// A circuit tab shows its layout or its appearance (the original's Edit Circuit Layout / Edit Circuit Appearance).
const modeLayout = h('button', { type: 'button', class: 'modebtn on', role: 'radio', 'aria-checked': 'true', title: 'Edit Circuit Layout' }, 'Layout');
const modeAppearance = h('button', { type: 'button', class: 'modebtn', role: 'radio', 'aria-checked': 'false', title: 'Edit Circuit Appearance (서브회로 인스턴스의 모양)' }, 'Appearance');
modeLayout.addEventListener('click', () => { const f = files.active(); if (f) showCircuit(f.fileId, f.circuit, false); });
modeAppearance.addEventListener('click', () => { const f = files.active(); if (f) showCircuit(f.fileId, f.circuit, true); });
const modeSwitch = h('span', { class: 'seg modeswitch', role: 'radiogroup', 'aria-label': 'Circuit view' }, modeLayout, modeAppearance);
// The band over a subcircuit opened on its own, or a pin's preview (v1 P-02 InstanceBanner).
const instanceBand = h('div', { class: 'instband', role: 'status', hidden: true });
const canvasPanel = h('section', { class: 'panel canvaspanel', 'aria-label': 'Canvas' },
  h('div', { class: 'phead filebar' }, fileStrip.root),
  h('div', { class: 'circuitbar' }, circuitStrip.root, h('span', { class: 'grow' }), modeSwitch),
  instanceBand,
  canvasBody.root);

// ---- finding and placing (N-12, D-150) --------------------------------------------------

// Components: the library tree and its search; a part picked or dropped goes through hcs:place-tool.
const components = componentsPanel({
  host: componentsBody,
  onPick: (p) => pickTool(p, 'components'),
  onOpenCircuit: (id) => { const f = files.active(); if (f) { files.openCircuit(f.fileId, id); render(); } },
  // N-11 (P-03): another open file's circuit -- its file as a library first, then the part in hand
  onOpenFileCircuit: (fileId, circuit) => void (async () => {
    const lib = await circuitCtl.useOpenFile(fileId);
    const f = files.active();
    if (lib && f) pickTool({ lib, name: circuit }, 'components');
  })(),
  onLibraryMenu: (lib, display, x, y) => showMenu([{ label: `Unload Library (${display})`, disabled: !editableFile(), run: () => void circuitCtl.unload(lib) }], x, y),
  recent: () => pal.recent(),
  favorites: () => pal.favorites(),
});
// Circuits (N-11): the file's circuits and what is done to them (circuits.ts, circuit-control.ts).
const circuitCtl = new CircuitControl({
  api,
  ready: () => engine.state === 'ready',
  file: (id) => files.get(id),
  active: () => files.active(),
  note: (cls, text) => { note = text ? { cls, text } : null; renderStatus(); },
  show: (fileId, circuitId, appear) => showCircuit(fileId, circuitId, appear),
  opened: async (r) => { const o = await recoveryAnswered(r); openedOrError(o); return o; },
  librariesChanged: (fileId) => { libraries.delete(fileId); libInfo.delete(fileId); if (files.active()?.fileId === fileId) renderComponents(files.active()!); },
});
const circuitsList = circuitsPanel({
  host: circuitsBody,
  circuit: (cmd, id) => void circuitCtl.command(cmd, id),
  file: (cmd) => void circuitCtl.fileCommand(cmd),
  moveTo: (id, to) => void circuitCtl.moveTo(id, to),
  enter: (n) => { const f = files.active(); if (f) enterPath(f.fileId, n.ids, n.names, n.circuits); },
});
// The appearance editor (N-11): a circuit tab switched to Appearance shows it instead of the Canvas.
const appearance = new AppearanceEditor({
  call: (method, params) => api.call(method, params),
  ready: () => engine.state === 'ready',
  failed: (name, e) => { note = { cls: 'err', text: commandError(name, e as CallError) }; renderStatus(); },
  note: (text) => { note = text ? { cls: '', text } : null; renderStatus(); },
  attributes: (content) => { if (content) attributesBody.fill(content); else attributesBody.empty({ title: '고른 부품이 없습니다', body: codeText('Canvas에서 부품을 고르면 그 속성(`Data Bits`, `Facing`, `Label` …)이 여기에 나옵니다.') }); },
  layout: async (fileId, circuitId) => scenes.get(key(fileId, circuitId))?.snapshot() ?? await api.call<Snapshot>('model.circuit', { fileId, circuitId }).catch(() => null),
});
// Tunnels: by name; a name goes to its next tunnel, the chip sets Tunnel Color.
const tunnels = tunnelsPanel({
  host: tunnelsBody,
  go: (id) => revealPart(id),
  setColor: (id, color) => void setTunnelColor(id, color),
  editable: () => engine.state === 'ready',
});
// The Minimap: the whole circuit on show and the Canvas's view; press or drag to move the view.
const minimap = minimapPanel({ host: minimapBody, board });
// Find (Ctrl+F): over the Canvas's corner.
const finder = findWindow({
  query: async (text) => {
    const f = files.active();
    if (!f || engine.state !== 'ready') return null;
    try { return await api.call<FindResult>('find.query', { fileId: f.fileId, text }); } catch { return null; }
  },
  go: (p) => { const f = files.active(); if (f) emitReveal(revealOfPlace(f.fileId, p)); },
});
canvasPanel.append(finder.root);
// The search palette (Ctrl+K, a letter on the Canvas).
const pal = palette({
  sources: () => {
    const f = files.active();
    if (!f || engine.state !== 'ready') return null;
    const lib = libraries.get(f.fileId);
    const s = shownSnapshot(f);
    return {
      libraries: Array.isArray(lib) ? lib : null, fileName: f.name, current: shown(f).circuit,
      tunnels: tunnels.entries().map((e) => ({ name: e.name, count: e.ids.length })),
      commands: commandsNow(s),
    };
  },
  anchor: () => paletteAnchor(),
  choose: (it) => void chosen(it),
});
document.body.append(pal.root);

// The last place the pointer was over the Canvas (window px): the palette opens there and places there.
let pointerClient: { x: number; y: number } | null = null;
board.canvas.addEventListener('pointermove', (e) => { pointerClient = { x: e.clientX, y: e.clientY }; });
board.canvas.addEventListener('pointerleave', () => { pointerClient = null; });

// The Canvas's own box on screen (the empty circuit's word stands in for it).
function canvasRect(): DOMRect {
  return (board.root.isConnected ? board.canvas : canvasBody.root).getBoundingClientRect();
}
// A window point on the Canvas as a circuit point (zoom 1 from the corner while the circuit is empty).
function circuitPoint(x: number, y: number): Point {
  const r = canvasRect();
  const at: [number, number] = [x - r.left, y - r.top];
  return board.root.isConnected && board.scene ? toCircuit(board.view, at) : at;
}
// Where the palette puts a part: under the pointer, else the middle of what the Canvas shows (v1).
function pointerPoint(): Point {
  const r = canvasRect();
  if (pointerClient && pointerClient.x >= r.left && pointerClient.x <= r.right && pointerClient.y >= r.top && pointerClient.y <= r.bottom) {
    return circuitPoint(pointerClient.x, pointerClient.y);
  }
  if (board.root.isConnected && board.scene) {
    const v = visible(board.view, r.width, r.height);
    return [(v.x0 + v.x1) / 2, (v.y0 + v.y1) / 2];
  }
  return [r.width / 2, r.height / 2];
}
function paletteAnchor(): { x: number; y: number } {
  const r = canvasRect();
  if (pointerClient && pointerClient.x >= r.left && pointerClient.x <= r.right && pointerClient.y >= r.top && pointerClient.y <= r.bottom) {
    return { x: pointerClient.x + 12, y: pointerClient.y + 12 };
  }
  return { x: r.left + 80, y: r.top + 80 };
}

// A part to place: hcs:place-tool first (N-08's placement flow takes it); one with a point nobody took, placed here.
function placeTool(p: PlaceTool): void {
  if (emitPlaceTool(p) || !p.at) return;
  void placeNow(p);
}
async function placeNow(p: PlaceTool): Promise<void> {
  if (engine.state !== 'ready') return;
  try {
    await api.call('edit.addComponent', { fileId: p.fileId, circuitId: p.circuitId, lib: p.lib, name: p.name, loc: p.at, ...(p.attrs && Object.keys(p.attrs).length ? { attrs: p.attrs } : {}) });
    note = null;
  } catch (e) {
    note = { cls: 'err', text: commandError(p.name, e as CallError) };
  }
  renderStatus();
}
function pickTool(p: Pick, source: 'components' | 'drop', at?: Point): void {
  const f = files.active();
  if (!f) return;
  placeTool({ fileId: f.fileId, circuitId: shown(f).circuit, lib: p.lib, name: p.name, ...(p.attrs ? { attrs: p.attrs } : {}), ...(at ? { at } : {}), source });
}
// A part dragged from the Components list and dropped on the Canvas: one there (I-62).
canvasBody.root.addEventListener('dragover', (e) => {
  const types = e.dataTransfer?.types ?? [];
  if (!types.includes(TOOL_MIME) && !types.includes(FILE_MIME)) return;
  e.preventDefault();
  e.dataTransfer!.dropEffect = 'copy';
});
canvasBody.root.addEventListener('drop', (e) => {
  const tab = e.dataTransfer?.getData(FILE_MIME);
  if (tab) { e.preventDefault(); void dropFileTab((JSON.parse(tab) as { fileId: string }).fileId, snap(circuitPoint(e.clientX, e.clientY))); return; }
  const raw = e.dataTransfer?.getData(TOOL_MIME);
  if (!raw) return;
  e.preventDefault();
  pickTool(JSON.parse(raw) as Pick, 'drop', snap(circuitPoint(e.clientX, e.clientY)));
});

// A file tab dropped on the Canvas (v1 P-03, I-180): that file as a library of this one, its main circuit placed
// there.  The same file: nothing; a file never saved: the reason.
async function dropFileTab(fileId: string, at: Point): Promise<void> {
  const f = files.active();
  const other = files.get(fileId);
  if (!f || !other || fileId === f.fileId || engine.state !== 'ready' || appearanceShown(f)) return;
  const lib = await circuitCtl.useOpenFile(fileId);
  if (!lib) return;
  const main = other.circuits.find((c) => c.circuitId === other.main)?.name;
  if (main) placeTool({ fileId: f.fileId, circuitId: shown(f).circuit, lib, name: main, at, source: 'drop' });
}

// The commands the palette offers now.
function commandsNow(s: Snapshot | null): CommandId[] {
  const out: CommandId[] = ['reset', 'cycle', 'run', 'enable', 'load', 'find'];
  if (board.root.isConnected && board.scene) out.push('fit');
  if (selectedSplitter(s)) out.push('editSplitter');
  return out;
}
function selectedSplitter(s: Snapshot | null): string | null {
  const f = files.active();
  if (!f || !s || !selection || selection.fileId !== f.fileId || selection.circuitId !== s.circuitId || selection.ids.length !== 1) return null;
  const c = s.components.find((x) => x.id === selection!.ids[0]);
  return c && c.lib === 'Wiring' && c.name === 'Splitter' ? c.id : null;
}

// What the palette's Enter does.
async function chosen(it: SearchItem): Promise<void> {
  const f = files.active();
  if (!f) return;
  if (it.kind === 'component' || it.kind === 'subcircuit') {
    placeTool({ fileId: f.fileId, circuitId: shown(f).circuit, lib: it.lib ?? null, name: it.tool!, ...(Object.keys(it.attrs).length ? { attrs: it.attrs } : {}), at: snap(pointerPoint()), source: 'palette' });
  } else if (it.kind === 'tunnel') {
    const e = tunnels.entries().find((x) => x.name === it.name);
    if (e) revealPart(e.ids[0]);
  } else if (it.command) {
    runCommand(it.command);
  }
}
function runCommand(id: CommandId): void {
  const f = files.active();
  if (!f) return;
  switch (id) {
    case 'reset': void reset(); break;
    case 'cycle': void cycles(1); break;
    case 'run': void run(); break;
    case 'enable': void simCall('sim.enable', { on: !(f.sim?.running ?? true) }, 'Simulation Enabled'); break;
    case 'load': void programs.load(f.fileId); break;
    case 'fit': board.fitView(); break;
    case 'find': finder.open(); break;
    case 'editSplitter': {
      const id2 = selectedSplitter(shownSnapshot(f));
      if (id2) void editSplitter({ fileId: f.fileId, circuitId: shown(f).circuit, componentId: id2 });
      break;
    }
  }
}

// A part of the circuit on show, marked and brought into view (Tunnels, the palette's tunnels).
function revealPart(id: string): void {
  const f = files.active();
  if (!f) return;
  const w = shown(f);
  const c = shownSnapshot(f)?.components.find((x) => x.id === id);
  emitReveal({ fileId: f.fileId, messageId: null, circuitId: w.circuit, root: w.root, path: w.path, components: [id], wires: [], nets: [], at: c ? [c.loc[0], c.loc[1]] : null, cycle: null, tone: 'find' });
}

async function setTunnelColor(id: string, color: string | null): Promise<void> {
  const f = files.active();
  if (!f || engine.state !== 'ready') return;
  try {
    await api.call('edit.tunnelColor', { fileId: f.fileId, circuitId: shown(f).circuit, id, ...(color ? { color } : {}) });
    note = null;
  } catch (e) {
    note = { cls: 'err', text: commandError('Tunnel Color', e as CallError) };
  }
  renderStatus();
}

// The Splitter editor (N-12): for a splitter (Edit Splitter…) or a new one on a wire (Split Bits…, Take One Bit).
onEditSplitter((r) => void editSplitter(r));
async function editSplitter(r: EditSplitter): Promise<void> {
  const f = files.get(r.fileId);
  if (!f || engine.state !== 'ready') return;
  const scene = [...scenes.values()].find((x) => x.fileId === r.fileId && x.circuitId === r.circuitId);
  const snapshot = scene?.snapshot() ?? await api.call<Snapshot>('model.circuit', { fileId: r.fileId, circuitId: r.circuitId }).catch(() => null);
  if (!snapshot) return;
  try {
    if ('componentId' in r) {
      const c = snapshot.components.find((x) => x.id === r.componentId);
      if (!c || c.name !== 'Splitter') return;
      // each arm's wire: the widest other port on its net (a mismatch is a fact in the editor)
      const wired = c.ports.slice(1).map((q) => {
        const n = snapshot.nets.find((x) => x.ports.some(([id, i]) => id === c.id && i === q.i));
        const widths = (n?.ports ?? []).filter(([id]) => id !== c.id).map(([id, i]) => snapshot.components.find((x) => x.id === id)?.ports[i]?.width ?? 0);
        return widths.length ? Math.max(...widths) || null : null;
      });
      const a = await splitterEditor({ title: 'Edit Splitter', spec: fromAttrs(c.attrs, c.ext?.arms ?? []), wired });
      if (!a) return;
      const res = await api.call<{ changed: boolean; outcome?: string }>('edit.splitterEdit', { fileId: r.fileId, circuitId: r.circuitId, id: c.id, ranges: a.ranges, names: a.names, lsbTop: a.lsbTop });
      note = res.outcome === 'refused' ? { cls: 'err', text: 'Edit Splitter: 그렇게 두면 선이나 포트가 다른 연결에 닿아서 바꾸지 않았습니다' } : null;
    } else {
      if (r.bit !== undefined) {
        // Take One Bit [n]: no editor (the engine knows the wire's width and refuses a one-bit wire)
        await api.call('edit.splitterSplit', { fileId: r.fileId, circuitId: r.circuitId, wire: r.wire, at: r.at, ranges: String(r.bit) });
        note = null;
      } else {
        const width = snapshot.nets.find((x) => x.wires.includes(r.wire))?.width ?? 0;
        if (width <= 1) return;
        const a = await splitterEditor({ title: 'Split Bits', spec: initialSplit(width) });
        if (!a) return;
        const res = await api.call<{ changed: boolean; outcome?: string }>('edit.splitterSplit', { fileId: r.fileId, circuitId: r.circuitId, wire: r.wire, at: r.at, ranges: a.ranges, names: a.names, lsbTop: a.lsbTop });
        note = res.outcome === 'refused' ? { cls: 'err', text: 'Split Bits: 그렇게 두면 선이나 포트가 다른 연결에 닿아서 놓지 않았습니다' } : null;
      }
    }
  } catch (e) {
    note = { cls: 'err', text: commandError('componentId' in r ? 'Edit Splitter' : 'Split Bits', e as CallError) };
  }
  renderStatus();
}
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
  void overlays.refreshCycle(true);   // the active path and the field colours show with the Cycle View (N-15)
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
  // the Circuits tab: its Simulation Tree is asked for when it comes into view
  const f = files.active();
  if (i === 1 && f) renderCircuits(f);
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

document.body.append(h('div', { class: 'app' }, bar.root, bar.row, h('div', { class: 'bands' }, notices.root, simOffBand.root, programBand.root), work, status), zoomCtl.menu, wireLegend.panel);

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
  start.show(!stage.hidden); // the video plays on the first screen only (D-155)
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
  fitStatus();
}

// ---- rendering ----------------------------------------------------------------------

// While Run Until runs (the Cycle View's bar says Stop), the clock's own buttons wait.
function renderToolbarState(): void {
  const f = files.active();
  const until = cycleView.running(f?.fileId ?? null);
  const off = !f || engine.state !== 'ready';
  bRun.disabled = off || until;
  bCycle.disabled = off || until;
  bCycles.disabled = off || until;
}

function render(): void {
  reportDirty();
  const f = files.active();
  document.title = f ? `${f.name}${f.dirty ? ' •' : ''} — ${APP_NAME}` : APP_NAME;
  bar.setFile(f ? f.name : null, f?.dirty ?? false);
  bar.showToolbar(f !== null);
  const ready = engine.state === 'ready';
  for (const b of [bSave, bUndo, bRedo, bRun, bCycle, bCycles, bReset]) b.disabled = !f || !ready;
  renderToolbarState();
  frequency.disabled = !f || !ready;
  bLoad.disabled = !f || !ready;
  toolButtons.forEach((b, i) => { b.disabled = !f || !ready || !WORKING_TOOLS.has(TOOLS[i][0]); });
  flowToggle.disabled = !f || !ready;
  flowToggle.setAttribute('aria-pressed', String(overlays.settings.onClick));
  // Run is the clock (Ticks Enabled); while the clock ticks or N Cycles goes it is Stop (logic/sim.ts, D-145)
  const label = runLabel(f?.sim);
  // Both words take their room in either state (the one not shown is hidden): the toolbar is as wide running as
  // stopped, so the fitting rule (TOOLBAR_STEPS: key hints go first) gives both states the same key hints
  const word = (w: 'Run' | 'Stop') => h('span', w === label ? {} : { class: 'off', 'aria-hidden': 'true' }, w);
  bRun.replaceChildren(icon(label === 'Stop' ? 'square' : 'play'), h('span', { class: 'label swap' }, word('Run'), word('Stop')), h('kbd', {}, 'F5'));
  bRun.title = `${label} (F5)`;
  bRun.classList.toggle('primary', label === 'Stop');
  if (f) {
    // files of one name show the folder that tells them apart (v1 V-05); a library that came in new, " · Updated"
    const folders = distinguishers(files.list().map((x) => ({ id: x.fileId, name: x.name, path: x.path })));
    fileStrip.set(files.list().map((x) => {
      const bits = [folders.get(x.fileId) ? `— ${folders.get(x.fileId)}` : '', updatedFiles.has(x.fileId) ? '· Updated' : '', role.handover ? '· Window' : ''].filter(Boolean);
      return { id: x.fileId, label: x.name, title: x.path ?? 'Not saved yet', dirty: x.dirty, ...(bits.length ? { note: bits.join(' ') } : {}) };
    }), f.fileId);
    circuitStrip.set(f.tabs.map((c) => ({ id: c, label: files.circuitName(f, c), ...(appearanceTabs.has(key(f.fileId, c)) ? { note: '· Appearance' } : {}) })), f.circuit);
    const appear = appearanceShown(f);
    modeLayout.classList.toggle('on', !appear); modeLayout.setAttribute('aria-checked', String(!appear));
    modeAppearance.classList.toggle('on', appear); modeAppearance.setAttribute('aria-checked', String(appear));
    renderCircuits(f);
    renderComponents(f);
    renderCanvas(f);
  } else {
    fileStrip.set([], null);
    circuitStrip.set([], null);
    components.set(null);
    renderTunnels(null);
    finder.close();
    pal.close();
  }
  renderEmptyPanels();
  renderMessages();
  consoleView.show(f?.fileId ?? null);
  renderProgramBand();
  renderSimBand();
  renderStatus();
  layout();
}

// The band while the active file's simulation is off (N-07).
function renderSimBand(): void {
  const text = simBand(files.active()?.sim);
  if (text) simOffBand.show(text, 'error');
  else if (simOffBand.text() !== null) simOffBand.hide();
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

// The Circuits panel (circuits.ts): the main circuit marked with a house (not a second "main", D-135).
function renderCircuits(f: OpenFile): void {
  const inst = inside.get(key(f.fileId, f.main));
  circuitsList.set({
    fileId: f.fileId, circuits: f.circuits, main: f.main, shown: f.circuit, appearance: appearanceShown(f), editable: editableFile(),
    tree: simTreeOf(f), inside: f.circuit === f.main && !appearanceShown(f) ? inst?.ids ?? [] : undefined,
  });
}

// The Simulation Tree (I-118): each circuit's subcircuit instances (model.circuit), asked once per change of the file.
const simParts = new Map<string, SimPart[]>();   // key(fileId, circuitId)
const simAsking = new Set<string>();
function simTreeOf(f: OpenFile): SimNode[] | undefined {
  if (engine.state !== 'ready' || circuitsBody.root.hidden) return undefined;   // asked only while the Circuits tab is up
  const missing = new Set<string>();
  const tree = simTree(f.main, (id) => files.circuitName(f, id), (id) => {
    const got = simParts.get(key(f.fileId, id));
    if (!got) missing.add(id);
    return got;
  });
  for (const id of missing) void askSimParts(f.fileId, id);
  return missing.size ? undefined : tree;
}
async function askSimParts(fileId: string, circuitId: string): Promise<void> {
  const k = key(fileId, circuitId);
  if (simAsking.has(k)) return;
  simAsking.add(k);
  try {
    const s = await api.call<Snapshot>('model.circuit', { fileId, circuitId });
    simParts.set(k, s.components.filter((c) => c.subcircuit !== undefined && c.subcircuit !== null)
      .map((c) => ({ id: c.id, label: (c.attrs.label as string | undefined) ?? '', loc: c.loc, subcircuit: c.subcircuit! })));
  } catch {
    simParts.set(k, []);   // a circuit of a library: nothing inside to show
  } finally {
    simAsking.delete(k);
  }
  const f = files.active();
  if (f?.fileId === fileId) renderCircuits(f);
}
// An input pin poked inside an instance (I-64; Pin.PinPoker: "The pin is tied to the supercircuit state. Create a new
// circuit state?"): the answer opens that circuit on its own tab -- its own state, apart from the one in main (the
// band says so) -- where the pin can be set.  The original pokes at once; here the student presses it again there.
let askingNewState = false;   // the press and the release are both refused: one question
async function newStateFor(): Promise<void> {
  const f = files.active();
  if (!f || askingNewState) return;
  const w = shown(f);
  const sub = files.circuitName(f, w.circuit);
  if (!w.path.length) { note = { cls: 'err', text: frozenPinText(sub) }; renderStatus(); return; }
  askingNewState = true;
  const go = await ask(newStateQuestion(sub)).finally(() => { askingNewState = false; });
  if (!go || files.active() !== f) return;
  files.openCircuit(f.fileId, w.circuit);
  note = { cls: '', text: newStateNote(sub) };
  render();
}

// Into an instance from main (the Simulation Tree, Go to Instance): main's tab, down the path.
function enterPath(fileId: string, ids: string[], names: string[], circuits: string[]): void {
  const f = files.get(fileId);
  if (!f) return;
  files.activate(fileId);
  files.openCircuit(fileId, f.main);
  appearanceTabs.delete(key(fileId, f.main));
  if (ids.length) inside.set(key(fileId, f.main), { ids: [...ids], names: [...names], circuits: [...circuits] });
  else inside.delete(key(fileId, f.main));
  render();
}

// The file on show can be edited (not read-only; the engine says so on its edits too).
function editableFile(): boolean {
  return files.active() !== null && engine.state === 'ready';
}

// ---- a circuit tab's layout or appearance (N-11) ----

function appearanceShown(f: OpenFile): boolean {
  return appearanceTabs.has(key(f.fileId, f.circuit)) && !inside.get(key(f.fileId, f.circuit));
}

// A circuit the engine just made is not in the file's list until its file.changed: shown then.
let pendingShow: { fileId: string; circuitId: string; appear: boolean } | null = null;
function showCircuit(fileId: string, circuitId: string, appear: boolean): void {
  const f = files.get(fileId);
  if (!f) return;
  if (!f.circuits.some((c) => c.circuitId === circuitId)) { pendingShow = { fileId, circuitId, appear }; return; }
  pendingShow = null;
  files.activate(fileId);
  files.openCircuit(fileId, circuitId);
  const k = key(fileId, circuitId);
  if (appear) { appearanceTabs.add(k); inside.delete(k); } else appearanceTabs.delete(k);
  render();
}

function renderComponents(f: OpenFile): void {
  const lib = libraries.get(f.fileId);
  // The first group is this file's circuits (lib null); the bundled Hallym MIPS is listed before it is in the file (pending).
  const info = libInfo.get(f.fileId);
  const openFiles = info?.openFiles.map((o) => ({ fileId: o.fileId, name: files.get(o.fileId)?.name ?? o.name, state: o.state, circuits: o.circuits })) ?? [];
  components.set({ fileId: f.fileId, fileName: f.name, circuit: shown(f).circuit, libraries: lib, openFiles });
  if (lib === undefined) void loadLibrary(f.fileId);
  // the other open files may be in windows of their own (N-11): asked whatever this window holds
  if (!info && engine.state === 'ready') void loadLibInfo(f.fileId);
}
// Back to this window: files opened or closed in another window since are asked again (the Open Files group).
window.addEventListener('focus', () => {
  if (!libInfo.size) return;
  libInfo.clear();
  const f = files.active();
  if (f) renderComponents(f);
});

// model.libraries (N-11): the other open files for the Components list's Open Files group.
async function loadLibInfo(fileId: string): Promise<void> {
  try {
    libInfo.set(fileId, await api.call<LibrariesInfo>('model.libraries', { fileId }));
  } catch {
    return;   // an engine before N-11: no Open Files group
  }
  if (files.active()?.fileId === fileId) renderComponents(files.active()!);
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
  canvasBody.root.dataset.circuit = files.circuitName(f, f.circuit);
  // the circuit tab's appearance (N-11): the appearance editor instead of the Canvas
  if (appearanceShown(f)) {
    if (canvasBody.root.firstChild !== appearance.root) canvasBody.fill(appearance.root);
    const now = appearance.shownFor;
    if (!now || now.fileId !== f.fileId || now.circuitId !== f.circuit) void appearance.open(f.fileId, f.circuit);
    canvasBody.root.dataset.view = 'appearance';
    renderInstanceBand(f);
    renderTunnels(null);
    minimap.set(null);
    return;
  }
  if (appearance.shownFor) appearance.close();
  canvasBody.root.dataset.view = 'layout';
  renderInstanceBand(f);
  const scene = scenes.get(w.k);
  if (!scene) {
    canvasBody.fill();
    renderTunnels(null, true);
    minimap.set(null);
    if (wanted !== w.k) { wanted = w.k; void loadScene(f.fileId, w.circuit, w.k); }
    return;
  }
  const s = scene.snapshot();
  const facts = circuitFacts(s);
  // An empty circuit says what fills it; with a tool that puts something in (a part, a wire, a text), the Canvas
  // itself (N-08): its origin at the top-left, where Logisim's is
  // (and while that Canvas is up: the first part placed takes the Edit tool before its model.changed comes)
  const drawing = editor.tool === 'Place' || editor.tool === 'Wire' || editor.tool === 'Text'
    || (board.scene === scene && board.root.parentElement === canvasBody.root && !canvasBody.isEmpty());
  if (facts.components === 0 && facts.wires === 0 && !w.path.length && !drawing) {
    canvasBody.empty({ title: '빈 회로입니다', body: '부품과 선을 놓으면 여기 Canvas에 그려집니다. 부품은 왼쪽 Components 목록에서 끌어 오거나 Ctrl+K 검색 창에서 찾아 놓습니다.', pose: 'haram-hari-guide' });
    minimap.set(null);
  } else {
    if (canvasBody.isEmpty() || board.root.parentElement !== canvasBody.root) canvasBody.fill(board.root);
    if (board.scene !== scene) {
      if (board.scene && boardKey) views.set(boardKey, { ...board.view });
      boardKey = w.k;
      board.setScene(scene, views.get(w.k) ?? (facts.components === 0 && facts.wires === 0 ? { x: 0, y: 0, zoom: 1 } : undefined));
      editor.sceneChanged();
    }
    board.setCrumbs(w.path.length ? [files.circuitName(f, f.circuit), ...w.names] : [], (i) => leaveInstance(f, i));
    if (pendingReveal?.k === w.k) { board.reveal(pendingReveal.r); pendingReveal = null; }
    minimap.set(scene);
  }
  void watch(f.fileId, w.root, w.path, w.k);
  renderTunnels(s, false, w.k, f.fileId);
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
    const left = editor.floatingLeft(fileId, circuitId, path);
    if (left) await api.call('edit.select', { fileId, circuitId: left.circuitId });   // dropped where it was pasted
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

// Tunnels (tunnels.ts): the circuit on show (loading: its snapshot is on its way).
function renderTunnels(s: Snapshot | null, loading = false, k = '', fileId = ''): void {
  tunnels.set(s ? { fileId, key: k, snapshot: s } : null, loading);
}

function renderEmptyPanels(): void {
  renderAttributes();
  if (!files.active()) minimap.set(null);
  renderCycleBody();
}

// The Attributes panel: until the attribute table (N-10), the facts of what is selected on the Canvas -- the
// parts and wires in the circuit on show, and those pasted and not yet placed (D-146); nothing selected: its empty state.
function renderAttributes(): void {
  const f = files.active();
  const s = board.scene;
  const chosen: (Component | Wire)[] = [];
  if (f && s && selection && selection.fileId === f.fileId && selection.circuitId === s.circuitId) {
    for (const id of selection.ids) {
      const x = s.components.get(id) ?? s.wires.get(id);
      if (x) chosen.push(x);
    }
  }
  if (f && s) chosen.push(...(editor.selection()?.floating ?? []));
  const facts = selectionFacts(chosen);
  if (!facts) {
    attributesBody.empty({ title: '고른 부품이 없습니다', body: codeText('Canvas에서 부품을 고르면 그 속성(`Data Bits`, `Facing`, `Label` …)이 여기에 나옵니다.') });
    return;
  }
  attributesBody.fill(h('div', { class: 'sel-facts' },
    h('h3', {}, facts.title),
    facts.lines.length ? h('ul', {}, ...facts.lines.map((l) => h('li', {}, l))) : null));
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
    // Simulation On/Off, then the cycle on show and PC (N-14: the recording's, "Cycle 5 / 12" on a past
    // cycle; the clock's count before it is known), then the clock -- 1 Hz, Running (64 Hz), N Cycles · n left (N-07)
    const sf = simFacts(f.sim, { cycle: false });
    if (sf[0]) parts.push(dropFirst(span(`sim ${sf[0].cls}`.trim(), sf[0].text), sf[0].cls === '' ? 3 : 0));
    const rec = cycleView.state(f.fileId) ?? null;
    const facts = cycleFacts(rec, f.sim ? f.sim.cycle : null);
    if (facts.cycle) parts.push(span(`sim ${facts.past ? 'warn' : ''}`.trim(), facts.cycle));
    if (facts.pc) parts.push(span('', code(facts.pc)));
    if (rec?.runUntil) parts.push(span('run', 'Running (Run Until)'));
    for (const x of sf.slice(1)) parts.push(dropFirst(span(`sim ${x.cls}`.trim(), x.text), x.cls === '' ? 1 : 0));
    // The program: its name, PC ≠ entry at cycle 0, an old Stack, a .s path (facts, not messages; N-16).
    parts.push(...programs.statusNodes(f.fileId));
    parts.push(...overlays.statusNodes());
  }
  if (note) {
    parts.push(span(note.cls, note.text));
    if (note.action) {
      const a = note.action;
      const b = h('button', { type: 'button', class: 'linkbtn notebtn' }, a.label);
      b.addEventListener('click', () => a.run());
      parts.push(b);
    }
  }
  parts.push(span('grow'));
  if (f && board.scene && board.root.isConnected) parts.push(wireLegend.button, zoomCtl.button);
  // The engine's and Java's versions are About's only, not the student's status bar (D-154).
  status.replaceChildren(...parts);
  fitStatus();
}

/* A status bar too narrow for its facts (half a screen) leaves out the ones that say least, in this order
   (N-07): the clock's speed while it does not run (the toolbar shows it), then Simulation On (Off always
   stays).  The engine's versions are not here at all (About only, D-154).  0: never left out. */
function dropFirst(el: HTMLElement, order: number): HTMLElement {
  if (order > 0) el.dataset.drop = String(order);
  return el;
}
function fitStatus(): void {
  const drop = [...status.querySelectorAll<HTMLElement>('[data-drop]')].sort((a, b) => Number(a.dataset.drop) - Number(b.dataset.drop));
  for (const el of drop) el.hidden = false;
  for (const el of drop) {
    if (status.scrollWidth <= status.clientWidth + 1) break;
    el.hidden = true;
  }
}

// ---- files ------------------------------------------------------------------------------

function showFile(fileId: string): void {
  files.activate(fileId);
  updatedFiles.delete(fileId);   // " · Updated" goes once the tab is chosen (v1 D-065)
  note = null;
  render();
  finder.refresh();
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
  void loadSimState(f.fileId);
}

// The simulation's state from the start (Simulation On, Cycle 0, the clock's speed): then sim.state notifications.
async function loadSimState(fileId: string): Promise<void> {
  try {
    const st = await api.call<SimState>('sim.state', { fileId });
    if (!files.get(fileId) || files.get(fileId)!.sim) return;   // a notification came first: it is newer
    files.setSim(st);
    if (files.active()?.fileId === fileId) render();
  } catch {
    // the notifications will tell
  }
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
  // Opened from its recovery file (N-19): unsaved edits.
  if (r.recovered) {
    files.setDirty(r.fileId, true);
    note = { cls: '', text: recoveredNote(r.name) };
    render();
  }
  // What the original loader would have shown in its dialogs (e.g. a component it does not know).
  if (r.messages?.length) {
    note = { cls: 'err', text: `불러오며 알린 것 ${r.messages.length}개 — ${r.messages[0]}` };
    renderStatus();
  }
}

// A file with a recovery file beside it: the question first (logic/recovery-ask.ts, N-19); Esc opens nothing.
const recoveryAnswered = (r: Opened | RecoveryAsk | null) => answerRecovery(r, (q) => choose(q), (id, c) => api.openRecovery(id, c));

async function openFile(): Promise<void> {
  if (!(await engineReady())) return;
  try {
    openedOrError(await recoveryAnswered(await api.openFile()));
  } catch (e) {
    openedOrError(null, e);
  }
}

// .circ files dragged from the desktop onto the window (v1 DropOpen, I-181): each opened, the recovery question first.
// Only the main window opens files; a drop anywhere else never replaces the page.
function droppedFiles(e: DragEvent): boolean { return (e.dataTransfer?.types ?? []).includes('Files'); }
window.addEventListener('dragover', (e) => {
  if (!droppedFiles(e)) return;
  e.preventDefault();
  e.dataTransfer!.dropEffect = role.main ? 'copy' : 'none';
});
window.addEventListener('drop', (e) => {
  if (!droppedFiles(e)) return;
  e.preventDefault();
  const list = e.dataTransfer?.files;
  if (!role.main || !list || !list.length) return;
  void (async () => {
    if (!(await engineReady())) return;
    try {
      for (const r of await api.openDropped([...list])) openedOrError(await recoveryAnswered(r));   // an array: a FileList does not cross the bridge
    } catch (err) {
      openedOrError(null, err);
    }
  })();
});

async function save(saveAs: boolean): Promise<void> {
  const f = files.active();
  if (!f || engine.state !== 'ready') return;
  // another open file uses this one as a library: the connections this save would break, first (v1 P-03, D-065)
  if (!saveAs && !(await circuitCtl.saveCuts(f))) return;
  await saveOf(f, saveAs);
}

// Saves a file (the save dialog for one never saved); whether it was saved.
async function saveOf(f: OpenFile, saveAs: boolean): Promise<boolean> {
  let ok = false;
  try {
    const r = await api.saveFile(f.fileId, { name: f.name, saveAs });
    if (r) {
      files.saved(f.fileId, r.name, r.path);
      note = { cls: 'ok', text: `저장했습니다 · ${r.name}${r.needsMipsJar ? ' · 원조 Logisim 2.7.1에서 열려면 옆에 hcs-mips.jar가 있어야 합니다' : ''}` };
      // the bundled hcs-mips.jar beside the file, on the student's word (v1 V-01 [Copy hcs-mips.jar Here], D-096)
      if (r.needsMipsJar) note.action = { label: 'Copy hcs-mips.jar Here', run: () => void circuitCtl.copyMipsJar(f.fileId) };
      ok = true;
    }
  } catch (e) {
    note = null;
    fileErrorDialog('save', e, f.name);
  }
  render();
  return ok;
}

// Before unsaved changes would be lost (N-19, logic/unsaved.ts): Save / Discard / Cancel for each file that has
// them; whether to go on.  With no engine to save through, there is nothing to keep: go on.
async function unsavedSettled(list: readonly OpenFile[], leaving: Leaving): Promise<boolean> {
  if (engine.state !== 'ready') return true;
  return settleUnsaved(list, leaving, {
    dirty: async (f) => (await api.call<{ dirty: boolean }>('file.dirty', { fileId: f.fileId }).catch(() => ({ dirty: f.dirty }))).dirty,
    show: (f) => { files.activate(f.fileId); render(); },
    choose: (q) => choose(q),
    save: (f) => saveOf(f, false),
  });
}

// Leaving the app (the close button, Alt+F4, Ctrl+Q, the PC shutting down): each unsaved file asked about, then
// the window closes (main.ts app:leave).
let leaving = false;
async function leave(): Promise<void> {
  if (leaving || document.querySelector('dialog[open]')) return;
  leaving = true;
  try {
    if (await unsavedSettled([...files.list()], 'quit')) await api.leave();
  } finally {
    leaving = false;
  }
}
api.onLeave(() => void leave());
let reportedDirty: boolean | null = null;
function reportDirty(): void {
  const d = files.list().some((f) => f.dirty);
  if (d !== reportedDirty) { reportedDirty = d; void api.reportDirty(d); }
}

// Closes a file's tab after the save question; whether it closed.  A window of its own goes with its file (N-11).
async function closeFile(fileId: string): Promise<boolean> {
  const f = files.get(fileId);
  if (!f) return false;
  if (!(await unsavedSettled([f], 'close'))) return false;
  if (engine.state === 'ready') await api.call('file.close', { fileId }).catch(() => {});
  dropFile(fileId);
  if (role.handover && files.count() === 0) void api.windowClosed();
  return true;
}

// A file's tab leaves this window (closed, or into a window of its own): everything the window kept for it.
function dropFile(fileId: string): void {
  files.close(fileId);
  for (const m of [scenes, views, inside]) for (const k of [...m.keys()]) if (k.startsWith(`${fileId} `)) m.delete(k);
  for (const k of [...appearanceTabs]) if (k.startsWith(`${fileId} `)) appearanceTabs.delete(k);
  if (appearance.shownFor?.fileId === fileId) appearance.close();
  updatedFiles.delete(fileId);
  libInfo.clear();
  instCache.clear();
  watching.delete(fileId);
  if (board.scene?.fileId === fileId) { board.setScene(null); boardKey = ''; }
  cycleView.forget(fileId);
  libraries.delete(fileId);
  diags.delete(fileId);
  programs.drop(fileId);
  consoleView.drop(fileId);
  overlays.fileClosed(fileId);
  note = null;
  if (files.count() === 0) start.go('first');
  render();
}

// ---- subcircuits: the band, their instances, their right click (N-11; v1 P-02) -------------------

// model.instances by `${fileId} ${circuitId}` (the instance paths from main; the ports' use); cleared on every change.
const instCache = new Map<string, InstancesInfo | null>();
let bandShown = '';

async function instancesOf(fileId: string, circuitId: string): Promise<InstancesInfo | null> {
  const k = key(fileId, circuitId);
  if (instCache.has(k)) return instCache.get(k) ?? null;
  instCache.set(k, null);
  try {
    const r = await api.call<InstancesInfo>('model.instances', { fileId, circuitId });
    instCache.set(k, r);
    return r;
  } catch {
    return null;   // an engine before N-11
  }
}

/* The band over the Canvas (v1 InstanceBanner): a subcircuit opened on its own tab while main runs it as an
   instance -- its values are not main's -- with "Go to Instance in main"; or, with a pin chosen in it (or the Pin
   tool in hand), what its instances would lose. */
function renderInstanceBand(f: OpenFile): void {
  const w = shown(f);
  const show = (text: string | null, go: InstancesInfo | null = null) => {
    const k = `${text}|${go?.paths.length ?? 0}`;
    if (k === bandShown) return;
    bandShown = k;
    instanceBand.hidden = text === null;
    if (text === null) { instanceBand.replaceChildren(); return; }
    const parts: Node[] = [h('span', { class: 'bandtext' }, codeText(text))];
    if (go && go.paths.length) {
      const b = h('button', { type: 'button', class: 'btn small' }, `Go to Instance in ${go.mainName ?? 'main'}`);
      b.addEventListener('click', (e) => {
        if (go.paths.length === 1) { goToInstance(f.fileId, go, 0); return; }
        const r = (e.currentTarget as HTMLElement).getBoundingClientRect();
        showMenu(go.paths.map((p, i) => ({ label: p.text, run: () => goToInstance(f.fileId, go, i) })), r.left, r.bottom + 2);
      });
      parts.push(b);
    }
    instanceBand.replaceChildren(...parts);
    layout();
  };
  if (w.path.length || f.circuit === f.main || engine.state !== 'ready' || appearanceShown(f)) { show(null); return; }
  const sub = files.circuitName(f, f.circuit);
  const info = instCache.get(key(f.fileId, f.circuit));
  if (info === undefined) { void instancesOf(f.fileId, f.circuit).then(() => { if (files.active() === f) renderInstanceBand(f); }); show(null); return; }
  if (!info || info.instances === 0) { show(null); return; }
  // a pin chosen, or the Pin tool in hand: what the instances would lose (a preview; facts only)
  const held = editor.tool === 'Place' ? editor.place.held : null;
  if (held && held.lib === 'Wiring' && held.name === 'Pin' && !appearanceShown(f)) {
    show(pinAddText(sub, info.instances, info.connected, info.default));
    return;
  }
  const pins = selection && selection.fileId === f.fileId && selection.circuitId === f.circuit
    ? selection.ids.filter((id) => scenes.get(w.k)?.components.get(id)?.name === 'Pin') : [];
  if (pins.length && !appearanceShown(f)) {
    void api.call<{ connections: number; instances: number }>('model.pinImpact', { fileId: f.fileId, circuitId: f.circuit, ids: pins })
      .then((r) => { if (files.active() === f) show(pinPreviewText(r.connections, r.instances)); }).catch(() => {});
    return;
  }
  show(info.paths.length ? standaloneText(sub, info.mainName ?? 'main') : null, info);
}

// Go to Instance in main: main's tab, down the instance path (the values are that instance's).
function goToInstance(fileId: string, info: InstancesInfo, i: number): void {
  const p = info.paths[i];
  if (!p || !info.main) return;
  files.activate(fileId);
  files.openCircuit(fileId, info.main);
  appearanceTabs.delete(key(fileId, info.main));
  inside.set(key(fileId, info.main), { ids: [...p.ids], names: [...p.names], circuits: [...(p.circuits ?? [])] });
  render();
}

// A subcircuit instance's right click (v1 I-95): View, then this file's circuit's appearance and ports, or the
// library circuit's file.
function instanceMenu(part: string | null): MenuEntry[] {
  const f = files.active();
  const c = part ? board.scene?.components.get(part) : undefined;
  if (!f || !c || c.subcircuit === undefined) return [];
  const sub = c.subcircuit;
  const name = files.circuitName(f, sub) === sub ? c.name : files.circuitName(f, sub);
  const out: MenuEntry[] = [{ label: `View ${name}`, run: () => enterInstance(c.id) }];
  if (c.lib === null) {
    out.push({ label: `Edit Appearance of ${name}`, run: () => showCircuit(f.fileId, sub, true) });
    out.push({ label: 'Auto Appearance', disabled: !editableFile(), run: () => void circuitCtl.command('autoAppearance', sub) });
    out.push({ label: 'Port Order…', disabled: !editableFile(), run: () => void circuitCtl.command('portOrder', sub) });
  } else {
    out.push({ label: `Edit Original File (${c.lib}.circ)`, run: () => void circuitCtl.editOriginal(f.fileId, sub) });
  }
  return out;
}

// ---- file tabs: their menu, a window of their own (N-11; v1 P-06, I-179, I-180) -------------------

function handoverOf(f: OpenFile): Handover {
  return { fileId: f.fileId, name: f.name, path: f.path, tabs: [...f.tabs], circuit: f.circuit };
}

function fileTabMenu(fileId: string, x: number, y: number): void {
  const f = files.get(fileId);
  if (!f) return;
  const entries: MenuEntry[] = role.handover
    ? [{ label: 'Attach Tab', run: () => void attachFile(fileId) }]
    : [
      { label: 'Detach Tab', disabled: files.count() < 2, title: '이 파일을 제 창으로 떼어 냅니다', run: () => void detachFile(fileId, 'window') },
      { label: 'View Side by Side', disabled: files.count() < 2, title: '이 파일을 제 창으로 떼어 화면 오른쪽 반에 두고 이 창을 왼쪽 반에 둡니다', run: () => void detachFile(fileId, 'side') },
    ];
  entries.push(SEPARATOR, { label: 'Close', run: () => void closeFile(fileId) });
  showMenu(entries, x, y);
}

// Detach Tab / View Side by Side: the file goes to a window of its own (the engine keeps it open).
async function detachFile(fileId: string, how: 'window' | 'side'): Promise<void> {
  const f = files.get(fileId);
  if (!f || !role.main || files.count() < 2) return;
  try {
    if (await api.detach(fileId, handoverOf(f), how)) dropFile(fileId);
  } catch (e) {
    note = { cls: 'err', text: commandError('Detach Tab', e as CallError) };
    renderStatus();
  }
}

// Attach Tab (a window of its own): the file goes back to the main window, this one closes.
async function attachFile(fileId: string): Promise<void> {
  const f = files.get(fileId);
  if (!f) return;
  try { await api.attach(handoverOf(f)); } catch (e) {
    note = { cls: 'err', text: commandError('Attach Tab', e as CallError) };
    renderStatus();
  }
}

// A file handed to this window (a window of its own starting, or Attach Tab back to the main one): its tabs as they were.
async function adopt(h0: Handover): Promise<void> {
  if (files.get(h0.fileId)) { showFile(h0.fileId); return; }
  let info: FileInfo;
  try { info = await api.call<FileInfo>('file.info', { fileId: h0.fileId }); } catch { return; }
  added({ fileId: h0.fileId, name: h0.name, path: h0.path, circuits: info.circuits, main: info.main });
  files.setDirty(h0.fileId, info.dirty);
  for (const t of h0.tabs) files.openCircuit(h0.fileId, t);
  files.openCircuit(h0.fileId, h0.circuit);
  render();
}
api.onAdopt((h0) => void adopt(h0));
// A window of its own asked to close (its close button): its file first, with the save question.
api.onCloseRequest(() => void (async () => {
  const f = files.list()[0];
  if (!f) { await api.windowClosed(); return; }
  if (!(await closeFile(f.fileId))) api.closeCancelled();
})());

// ---- editing and the simulation (as far as the engine's v0 goes) ---------------------------

async function edit(method: 'edit.undo' | 'edit.redo', name: string): Promise<void> {
  const f = files.active();
  if (!f || engine.state !== 'ready') return;
  editor.forgetWire();
  try {
    await api.call(method, { fileId: f.fileId, circuitId: f.circuit });
    note = null;
  } catch (e) {
    note = { cls: 'err', text: commandError(name, e as CallError) };
  }
  renderStatus();
}

async function simCall(method: 'sim.run' | 'sim.cycles' | 'sim.reset' | 'sim.enable' | 'sim.tick' | 'sim.step', params: Record<string, unknown>, name: string): Promise<void> {
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
// Run starts the clock; while the clock ticks or N Cycles goes, it stops them (logic/sim.ts, D-145).
const run = () => simCall('sim.run', { on: !going(files.active()?.sim), hz: Number(frequency.value) }, going(files.active()?.sim) ? 'Stop' : 'Run');
const cycles = (n: number) => simCall('sim.cycles', { n }, n === 1 ? '1 Cycle' : 'N Cycles');
// Reset; after an oscillation it also turns the simulation on again (as Messages' Reset Simulation).
async function reset(): Promise<void> {
  const on = resetTurnsOn(files.active()?.sim);
  await simCall('sim.reset', {}, 'Reset');
  if (on) await simCall('sim.enable', { on: true }, 'Reset');
}
// N Cycles: the count from its dialog (v1: 10 at first, 1 to 100000), then the engine paces the ticks (D-123).
async function nCycles(): Promise<void> {
  if (!files.active() || engine.state !== 'ready') return;
  const n = await askCycles(lastCycles);
  if (n === null) return;
  lastCycles = n;
  await cycles(n);
}
// Simulate › Simulation Enabled (Ctrl+E), Tick Once (Ctrl+T), Step Simulation (Ctrl+I, only while off).
const toggleSimulation = () => simCall('sim.enable', { on: !(files.active()?.sim?.running ?? true) }, 'Simulation Enabled');
const tickOnce = () => simCall('sim.tick', {}, 'Tick Once');
const stepSimulation = () => simCall('sim.step', {}, 'Step Simulation');
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
  statusKeeper.pushed(s);
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
  instCache.clear();
  libInfo.clear();
  if (appearance.shownFor) appearance.close();   // asked again (its shapes are the new engine's)
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
  for (const f of files.list()) { consoleView.drop(f.fileId); void programs.refresh(f.fileId); void loadConsole(f.fileId); void loadSimState(f.fileId); }
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
    overlays.cycleChanged(st.fileId);
  } else if (method === 'edit.selection') {
    editor.onSelection(p as unknown as EditSelection);
    renderAttributes();   // pasted parts (floating) change it without changing the ids
  } else if (method === 'model.changed') {
    const c = p as unknown as ModelChanged;
    // The engine is the authority: its change goes into every scene of that circuit (and instances of it).
    for (const sc of scenes.values()) if (sc.fileId === c.fileId && sc.circuitId === c.circuitId) sc.applyChange(c);
    libraries.delete(c.fileId); // the first part of a pending library puts it in the file
    for (const k of [...instCache.keys()]) if (k.startsWith(`${c.fileId} `)) instCache.delete(k);   // instances, pins (N-11)
    simParts.delete(key(c.fileId, c.circuitId));   // the Simulation Tree asks that circuit again
    bandShown = '';
    watching.delete(c.fileId);  // an edit makes its circuit the simulation's own: watch the shown one again
    if (typeof c.dirty === 'boolean') files.setDirty(c.fileId, c.dirty);
    overlays.modelChanged(c);
    board.invalidate();
    render();
    finder.refresh();   // its index is the model now (I-171 정함)
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
      if (board.scene === sc) { board.invalidate(); overlays.values(v); }
    }
  } else if (method === 'record.state') {
    cycleView.onState(p as unknown as RecordState);
    overlays.cycleChanged(String(p.fileId));
  } else if (method === 'record.runUntil') {
    cycleView.onRunUntil(p as unknown as RunUntilDone);
  } else if (method === 'file.changed') {
    // N-11: the file's circuits (added, removed, renamed, moved), its main circuit, its libraries
    const c = p as unknown as FileInfo;
    if (!files.get(c.fileId)) return;
    for (const gone of files.structure(c.fileId, c.circuits, c.main)) {
      for (const m of [scenes, views, inside]) for (const k of [...m.keys()]) if (k === key(c.fileId, gone) || k.startsWith(`${key(c.fileId, gone)} `)) m.delete(k);
      appearanceTabs.delete(key(c.fileId, gone));
      if (appearance.shownFor?.circuitId === gone) appearance.close();
    }
    // a renamed circuit: the scenes keep their parts, their name is the snapshot's (asked again)
    for (const sc of [...scenes.entries()]) if (sc[1].fileId === c.fileId && sc[1].snapshot().name !== files.circuitName(files.get(c.fileId)!, sc[1].circuitId)) scenes.delete(sc[0]);
    if (typeof c.dirty === 'boolean') files.setDirty(c.fileId, c.dirty);
    libraries.delete(c.fileId);
    libInfo.clear();
    for (const k of [...instCache.keys()]) if (k.startsWith(`${c.fileId} `)) instCache.delete(k);
    bandShown = '';
    const ps = pendingShow;
    if (ps && ps.fileId === c.fileId && c.circuits.some((x) => x.circuitId === ps.circuitId)) showCircuit(ps.fileId, ps.circuitId, ps.appear);
    render();
  } else if (method === 'model.appearance') {
    appearance.show(p as unknown as AppearanceEdit);
  } else if (method === 'model.portImpact') {
    const pi = p as unknown as PortImpact;
    if (files.active()?.fileId === pi.fileId) { note = { cls: pi.kept < pi.broken ? 'err' : '', text: portImpactText(pi) }; renderStatus(); }
  } else if (method === 'file.libraryUpdated') {
    const u = p as unknown as LibraryUpdated;
    if (!files.get(u.fileId)) return;
    libraries.delete(u.fileId);
    if (files.active()?.fileId === u.fileId) { note = { cls: '', text: libraryUpdatedText(u.library) }; renderStatus(); }
    else updatedFiles.add(u.fileId);
    render();
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
      h('p', { class: 'hint' }, 'Hallym University 논리설계와 컴퓨터구조 실습을 위한 회로 편집·시뮬레이션 도구입니다.'),
      // The university's marks, characters and the first screen's video (D-155: NOTICE, as Hallym MIPS NOTICE 8).
      h('p', { class: 'hint' }, '로고, 캐릭터(하람, 하리), 시작 화면의 영상은 Hallym University 소유이며 상업적 사용을 금지합니다. 로고와 캐릭터는 원형 그대로 씁니다. 시작 화면의 영상은 홍보 영상의 첫 장면 2.5초를 소리 없이 느리게 자른 것이고, 흐리게 하고 남색 층을 덮어 보입니다. 영상 출처: Hallym University 공식 YouTube 채널 @HALLYMNEWS (NOTICE). 이 프로그램은 Hallym University 공식 제품이 아닙니다.'),
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
  // the appearance editor's keys while it is on show (N-11): its Edit menu, Delete, Esc, its zoom; Undo/Redo stay the file's
  const af = files.active();
  if (af && appearanceShown(af) && !typing(e.target) && !(mod && /^[zy]$/i.test(e.key)) && appearance.key(e)) { e.preventDefault(); return; }
  // Ctrl+W (and v1's Ctrl+Shift+W): the focused window's file tab (I-156 정함; the last one: the first screen)
  if (mod && !e.altKey && (e.code === 'KeyW' || e.key.toLowerCase() === 'w')) { e.preventDefault(); if (af) void closeFile(af.fileId); return; }
  // Ctrl+← / Ctrl+→: Go Out To State / Go In To State (I-151): out one instance, into the first one
  if (mod && !e.shiftKey && !e.altKey && (e.key === 'ArrowLeft' || e.key === 'ArrowRight') && af && !typing(e.target) && !appearanceShown(af)) {
    e.preventDefault();
    const w = shown(af);
    if (e.key === 'ArrowLeft') { if (w.path.length) leaveInstance(af, w.path.length - 1); }
    else {
      const first = [...(board.scene?.components.values() ?? [])].filter((c) => c.subcircuit !== undefined)
        .sort((a, b) => a.loc[1] - b.loc[1] || a.loc[0] - b.loc[0])[0];
      if (first) enterInstance(first.id);
    }
    return;
  }
  if (e.key === 'F5') { e.preventDefault(); void run(); return; }
  if (e.key === 'F10') { e.preventDefault(); void cycles(1); return; }
  if (!mod) return;
  if (board.scene && board.root.isConnected && board.zoomKey(e)) { e.preventDefault(); return; }
  const k = e.key.toLowerCase();
  // Ctrl+K the search palette, Ctrl+F Find (D-139, I-168, I-171; e.code too: a Korean keyboard layout)
  if ((k === 'k' || e.code === 'KeyK') && !e.shiftKey && !e.altKey) { e.preventDefault(); if (files.active()) pal.open(''); return; }
  if ((k === 'f' || e.code === 'KeyF') && !e.shiftKey && !e.altKey) { e.preventDefault(); if (files.active()) finder.open(); return; }
  // Simulate's keys (docs/interaction-parity.md I-148..I-152; the physical key: a Korean layout gives the same code)
  if (!e.shiftKey && !e.altKey && files.active()) {
    const code = e.code;
    if (code === 'KeyE') { e.preventDefault(); void toggleSimulation(); return; }
    if (code === 'KeyR') { e.preventDefault(); void reset(); return; }
    if (code === 'KeyT') { e.preventDefault(); void tickOnce(); return; }
    if (code === 'KeyI') { e.preventDefault(); void stepSimulation(); return; }
  }
  if (e.code === 'KeyR') { e.preventDefault(); return; }   // never the page's reload (I-206)
  // Edit's keys (I-137..I-143): on the Canvas's selection; a field being typed in keeps its own
  const menuKeys: Record<string, MenuCommand> = { KeyC: 'copy', KeyX: 'cut', KeyV: 'paste', KeyD: 'duplicate', KeyA: 'selectAll' };
  if (!e.shiftKey && !e.altKey && menuKeys[e.code] && files.active() && !typing(e.target)) {
    e.preventDefault();
    editor.menu(menuKeys[e.code]);
    return;
  }
  if (k === 'n') { e.preventDefault(); if (role.main) void newCircuit(); }
  else if (k === 'o') { e.preventDefault(); if (role.main) void openFile(); }
  else if (k === 'q') { e.preventDefault(); if (role.main) void leave(); else void api.leave(); }
  else if (k === 's') { e.preventDefault(); void save(e.shiftKey); }
  else if (k === 'z' && !e.shiftKey) { e.preventDefault(); void edit('edit.undo', 'Undo'); }
  else if (k === 'y' || (k === 'z' && e.shiftKey)) { e.preventDefault(); void edit('edit.redo', 'Redo'); }
}, true);

// A letter typed on the Canvas that nothing else used opens the palette with it (I-41, D-037): with
// nothing selected, no modifier, not while a Korean syllable is being composed.  Looked at after every
// listener had its turn (a key N-08 or N-15 uses -- R, P on a wire -- is theirs: they preventDefault()).
window.addEventListener('keydown', (e) => {
  if (e.target !== board.canvas || e.ctrlKey || e.metaKey || e.altKey || e.isComposing || e.key.length !== 1 || !/\S/.test(e.key)) return;
  if (selection?.ids.length && selection.fileId === files.active()?.fileId) return;
  const key = e.key;
  setTimeout(() => { if (!e.defaultPrevented && !pal.isOpen()) pal.open(key); });
});

// A text field, a list box or anything typed into has the keys (not the Canvas's edit).
function typing(target: EventTarget | null): boolean {
  const el = target as HTMLElement | null;
  return !!el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.tagName === 'SELECT' || el.isContentEditable);
}

// ---- start ----------------------------------------------------------------------------------

async function begin(): Promise<void> {
  // a window of its own (N-11): its file, as the tab stood; no first screen, no New or Open
  role = await api.windowRole().catch(() => ({ main: true, handover: null }));
  if (role.handover) document.body.classList.add('ownwindow');
  new ResizeObserver(() => layout()).observe(document.body);
  (navigator as unknown as { windowControlsOverlay?: EventTarget }).windowControlsOverlay
    ?.addEventListener('geometrychange', () => layout());
  void document.fonts.ready.then(() => layout());
  engine = await statusKeeper.ask(() => api.engineStatus());
  if (role.handover) {
    decided = true;
    opening = true;
    render();
    await engineReady();
    await adopt(role.handover);
    opening = false;
    render();
    return;
  }
  // A file named on the command line opens instead of the first screen.
  const startup = await api.startupFile();
  opening = startup !== null;
  decided = true;
  render();
  if (engine.state === 'failed') void engineFailed();
  if (startup) {
    let r: Opened | null = null;
    let err: unknown;
    try { r = await recoveryAnswered(await api.openStartupFile()); } catch (e) { err = e; }
    opening = false;
    openedOrError(r, err);
    render();
  }
}
void begin();
