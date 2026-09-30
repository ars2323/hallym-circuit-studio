/* The window's menu (logic only; menubar.ts shows it from the title bar's
   Menu button, D-158).  The original's menu bar -- File, Edit, Project,
   Simulate, Window, Help (LogisimMenuBar) -- with v1's changes
   (docs/interaction-parity.md 14), as one button's menu: Hallym MIPS has
   no menu bar, and the title bar keeps its row.  Names in English as the
   original's (D-049); each item shows its key.

   Only what the window can do is here.  Project runs the Circuits panel's
   commands (N-11's circuit-control.ts) on the circuit on show; Analyze
   Circuit and Get Circuit Statistics (the engine's read-only wrap of the
   original's classes), Print, Export Image, Undo History and Create
   Submission come with N-21 (D-162; Logging is not moved: D-162 6), which adds its items to this
   table.  Edit › Tool picks a tool, the Menu Tool too (I-84: the
   original's Base library tool, no toolbar button).  An item is off while it cannot be done now (no
   file, the engine not up). */

import { keyText } from './keys.ts';

export interface MenuState {
  file: boolean;            // a file is open
  ready: boolean;           // the engine answers
  simOn: boolean;           // Simulation Enabled
  ticking: boolean;         // the clock runs (Ticks Enabled)
  hz: number;               // the clock's speed
  frequencies: readonly (readonly [string, number])[];   // the toolbar's speeds (logic/sim.ts)
  recent: { id: string; name: string }[];      // this run's files (I-130: this run only)
  examples: { id: string; name: string }[];    // Help › Examples (V-07)
  files: { id: string; name: string; active: boolean }[];   // Window's list of the open files
  project: { editable: boolean; index: number; count: number; main: boolean } | null;   // the circuit on show
  tool: string;             // the tool in hand (editor.ts ToolName)
}

// Edit › Tool: the original's Base tools the Canvas has (Select Tool is the Edit tool's, D-146).
export const MENU_TOOLS: readonly (readonly [string, string])[] = [['Edit', 'Edit Tool'], ['Poke', 'Poke Tool'], ['Wire', 'Wiring Tool'], ['Text', 'Text Tool'], ['Menu', 'Menu Tool']];

export interface MenuSpec {
  label: string;
  id?: string;              // what runs (menubar.ts); none: a submenu or a separator
  key?: string;
  disabled?: boolean;
  checked?: boolean;
  radio?: boolean;
  items?: MenuSpec[];
}
export const SEP: MenuSpec = { label: '-' };

export function appMenu(st: MenuState): MenuSpec[] {
  const noFile = !st.file || !st.ready;
  const item = (label: string, id: string, key?: string, disabled = false): MenuSpec => ({ label, id, ...(key ? { key } : {}), ...(disabled ? { disabled } : {}) });
  const file: MenuSpec[] = [
    item('New', 'file.new', 'Ctrl+N', !st.ready),
    item('Open…', 'file.open', 'Ctrl+O', !st.ready),
    { label: 'Open Recent', disabled: st.recent.length === 0 || !st.ready, items: st.recent.map((r) => item(r.name, `file.recent:${r.id}`)) },
    item('Close', 'file.close', 'Ctrl+W', !st.file),
    SEP,
    item('Save', 'file.save', 'Ctrl+S', noFile),
    item('Save As…', 'file.saveAs', 'Ctrl+Shift+S', noFile),
    // the original's File menu (Export Image…, Print…) and v1's Create Submission… (N-21, D-162)
    item('Create Submission…', 'file.submission', undefined, noFile),
    SEP,
    item('Export Image…', 'file.export', undefined, noFile),
    item('Print…', 'file.print', 'Ctrl+P', noFile),
    SEP,
    item('Preferences…', 'file.preferences'),
    SEP,
    item('Exit', 'file.exit', 'Ctrl+Q'),
  ];
  const edit: MenuSpec[] = [
    item('Undo', 'edit.undo', 'Ctrl+Z', noFile),
    item('Redo', 'edit.redo', keyText('redo'), noFile),
    item('Undo History…', 'edit.history', undefined, noFile),
    SEP,
    item('Cut', 'edit.cut', 'Ctrl+X', noFile),
    item('Copy', 'edit.copy', 'Ctrl+C', noFile),
    item('Paste', 'edit.paste', 'Ctrl+V', noFile),
    item('Delete', 'edit.delete', 'Delete', noFile),
    item('Duplicate', 'edit.duplicate', 'Ctrl+D', noFile),
    item('Select All', 'edit.selectAll', 'Ctrl+A', noFile),
    SEP,
    item('Find…', 'edit.find', keyText('find'), noFile),
    item('Search…', 'edit.palette', keyText('palette'), noFile),
    SEP,
    { label: 'Tool', disabled: noFile, items: MENU_TOOLS.map(([t, label]) => ({ ...item(label, `edit.tool:${t}`), checked: st.file && st.tool === t, radio: true })) },
  ];
  const pr = st.project;
  const locked = noFile || !pr || !pr.editable;
  const project: MenuSpec[] = [
    item('Add Circuit…', 'project.add', undefined, locked),
    item('Import Subcircuits…', 'project.import', undefined, locked),
    { label: 'Load Library', disabled: locked, items: [
      item('Built-in Library…', 'project.loadBuiltin'), item('Logisim Library…', 'project.loadCirc'), item('JAR Library…', 'project.loadJar')] },
    item('Unload Libraries…', 'project.unload', undefined, locked),
    SEP,
    item('Move Circuit Up', 'project.up', undefined, locked || !pr || pr.index <= 0),
    item('Move Circuit Down', 'project.down', undefined, locked || !pr || pr.index >= pr.count - 1),
    item('Set As Main Circuit', 'project.main', undefined, locked || !pr || pr.main),
    item('Remove Circuit', 'project.remove', undefined, locked || !pr || pr.count <= 1),
    SEP,
    item('Edit Circuit Layout', 'project.layout', undefined, noFile || !pr),
    item('Edit Circuit Appearance', 'project.appearance', undefined, noFile || !pr),
    // read only: on for a file that cannot be edited too (N-21)
    item('Analyze Circuit', 'project.analyze', undefined, noFile || !pr),
    item('Get Circuit Statistics', 'project.statistics', undefined, noFile || !pr),
  ];
  const simulate: MenuSpec[] = [
    { ...item('Simulation Enabled', 'sim.enabled', 'Ctrl+E', noFile), checked: st.file && st.simOn },
    item('Reset Simulation', 'sim.reset', 'Ctrl+R', noFile),
    item('Step Simulation', 'sim.step', 'Ctrl+I', noFile || st.simOn),
    SEP,
    item('Tick Once', 'sim.tick', 'Ctrl+T', noFile || !st.simOn),
    { ...item('Ticks Enabled', 'sim.ticks', 'F5', noFile || !st.simOn), checked: st.file && st.ticking },
    { label: 'Tick Frequency', disabled: noFile, items: st.frequencies.map(([label, hz]) => ({ ...item(label, `sim.hz:${hz}`), checked: hz === st.hz, radio: true })) },
    SEP,
    item('1 Cycle', 'sim.cycle', 'F10', noFile),
    item('N Cycles…', 'sim.cycles', undefined, noFile),
  ];
  const windowMenu: MenuSpec[] = [
    item('Minimize', 'window.minimize', 'Ctrl+M'),
    item('Maximize', 'window.maximize'),
    SEP,
    item('Preferences…', 'window.preferences'),
    ...(st.files.length ? [SEP, ...st.files.map((f) => ({ ...item(f.name, `window.file:${f.id}`), checked: f.active, radio: true }))] : []),
  ];
  const help: MenuSpec[] = [
    { label: 'Examples', disabled: st.examples.length === 0 || !st.ready, items: st.examples.map((x) => item(x.name, `help.example:${x.id}`)) },
    // the courses' tutorials (N-18): the first screen's two courses, by their names there
    { label: 'Tutorial', disabled: !st.ready, items: [item('논리설계 및 실험', 'help.tutorial:logic'), item('컴퓨터구조', 'help.tutorial:architecture')] },
    item('Keyboard Shortcuts', 'help.keys', '?'),
    SEP,
    item('About…', 'help.about'),
  ];
  return [
    { label: 'File', items: file },
    { label: 'Edit', items: edit },
    { label: 'Project', items: project },
    { label: 'Simulate', items: simulate },
    { label: 'Window', items: windowMenu },
    { label: 'Help', items: help },
  ];
}

// Every item with an id, depth first (the tests: each id has something to run).
export function menuIds(specs: MenuSpec[]): string[] {
  return specs.flatMap((s) => [...(s.id ? [s.id] : []), ...menuIds(s.items ?? [])]);
}
