/* The keys the student can change (logic only; v1 E-09, D-083,
   docs/interaction-parity.md I-183): fourteen commands, each with its
   default keys, the table the Keyboard tab of Preferences shows (with the
   keys that cannot change), and what a key press becomes once a command
   has another key (keymap.ts does it in the window).

   For this run only, as every setting (the lab-PC rule, N-19, D-152):
   the changed keys live in this module's memory and the next start has
   the defaults again.  One key given to a command takes the place of all
   of its defaults (v1: Ctrl+Shift+Z and the number pad's keys go too).
   A key already fixed (the original menus' keys, the arrows, Delete, …)
   or another command's is refused, with the reason; modifier keys alone
   are not a key; Esc with no modifier cancels.

   A stroke is the physical key (KeyboardEvent.code: a Korean layout gives
   the same) with its modifiers; `key` is what the command's own handler
   reads (e.key), for the press made in its place. */

export interface Stroke {
  code: string;             // KeyboardEvent.code: KeyR, F2, BracketLeft, Digit0, Numpad0 …
  key: string;              // KeyboardEvent.key for that code without Shift: r, F2, [, 0 …
  ctrl?: boolean;
  shift?: boolean;
  alt?: boolean;
}

export interface Command {
  id: string;
  name: string;             // English (a command's name, as the menus say it)
  say: string;              // Korean: what it does
  defaults: Stroke[];
  shiftReverses?: boolean;  // Shift with its key does the same the other way (rotate, influence)
}

const s = (code: string, key: string, mods: Omit<Stroke, 'code' | 'key'> = {}): Stroke => ({ code, key, ...mods });
const ctrl = (code: string, key: string, more: Omit<Stroke, 'code' | 'key' | 'ctrl'> = {}): Stroke => s(code, key, { ctrl: true, ...more });

export const COMMANDS: readonly Command[] = [
  { id: 'rotate', name: 'Rotate', say: '고른 부품을 시계 방향으로 돌립니다(Shift: 반대로).', defaults: [s('KeyR', 'r')], shiftReverses: true },
  { id: 'label', name: 'Edit Label', say: '고른 부품의 라벨을 그 자리에서 고칩니다.', defaults: [s('F2', 'F2')] },
  { id: 'redo', name: 'Redo', say: '되돌린 것을 다시 합니다.', defaults: [ctrl('KeyY', 'y'), ctrl('KeyZ', 'z', { shift: true })] },
  { id: 'zoomIn', name: 'Zoom In', say: 'Canvas 배율을 한 단계 키웁니다.', defaults: [ctrl('Equal', '='), ctrl('NumpadAdd', '+')] },
  { id: 'zoomOut', name: 'Zoom Out', say: 'Canvas 배율을 한 단계 줄입니다.', defaults: [ctrl('Minus', '-'), ctrl('NumpadSubtract', '-')] },
  { id: 'zoomFit', name: 'Fit to Window', say: '회로 전체가 Canvas 안에 들어오게 맞춥니다.', defaults: [ctrl('Digit0', '0'), ctrl('Numpad0', '0')] },
  { id: 'zoom100', name: 'Zoom 100%', say: 'Canvas 배율을 100%로 합니다.', defaults: [ctrl('Digit1', '1'), ctrl('Numpad1', '1')] },
  { id: 'zoomSel', name: 'Fit to Selection', say: '고른 것이 Canvas 안에 들어오게 맞춥니다.', defaults: [s('KeyF', 'f')] },
  { id: 'influence', name: 'Show Influence', say: '고른 부품이 영향을 주는 곳을 보입니다(Shift: 영향을 받는 곳).', defaults: [s('KeyI', 'i')], shiftReverses: true },
  { id: 'influenceLess', name: 'Influence: One Step Less', say: '영향 경로를 한 단계 좁힙니다.', defaults: [s('BracketLeft', '[')] },
  { id: 'influenceMore', name: 'Influence: One Step More', say: '영향 경로를 한 단계 넓힙니다.', defaults: [s('BracketRight', ']')] },
  { id: 'flowToggle', name: 'Signal Flow on Click', say: 'Edit 도구로 누른 곳의 신호 흐름 보이기를 켜고 끕니다.', defaults: [ctrl('KeyF', 'f', { shift: true })] },
  { id: 'find', name: 'Find', say: '라벨·터널·회로 이름을 찾습니다.', defaults: [ctrl('KeyF', 'f')] },
  { id: 'palette', name: 'Search', say: '부품·명령·터널을 찾는 검색 창을 엽니다.', defaults: [ctrl('KeyK', 'k')] },
];

// The keys that do not change (the menus' keys, Simulate's, the tools', the arrows …), and what each does: the
// Keyboard tab lists them too.  A new key for a command may not be one of them.
export const FIXED: readonly { stroke: Stroke; name: string }[] = [
  [ctrl('KeyN', 'n'), 'New'], [ctrl('KeyO', 'o'), 'Open…'], [ctrl('KeyS', 's'), 'Save'], [ctrl('KeyS', 's', { shift: true }), 'Save As…'],
  [ctrl('KeyW', 'w'), 'Close File'], [ctrl('KeyW', 'w', { shift: true }), 'Close File'], [ctrl('KeyQ', 'q'), 'Exit'],
  [ctrl('KeyZ', 'z'), 'Undo'], [ctrl('KeyX', 'x'), 'Cut'], [ctrl('KeyC', 'c'), 'Copy'], [ctrl('KeyV', 'v'), 'Paste'],
  [ctrl('KeyD', 'd'), 'Duplicate'], [ctrl('KeyA', 'a'), 'Select All'], [ctrl('KeyP', 'p'), 'Print…'],
  [ctrl('KeyE', 'e'), 'Simulation Enabled'], [ctrl('KeyR', 'r'), 'Reset Simulation'], [ctrl('KeyI', 'i'), 'Step Simulation'],
  [ctrl('KeyT', 't'), 'Tick Once'], [ctrl('KeyM', 'm'), 'Minimize'], [s('F5', 'F5'), 'Run'], [s('F10', 'F10'), '1 Cycle'],
  ...[2, 3, 4, 5, 6, 7, 8, 9].map((d): [Stroke, string] => [ctrl(`Digit${d}`, String(d)), 'Tool']),
  [ctrl('ArrowUp', 'ArrowUp'), 'Raise Selection'], [ctrl('ArrowDown', 'ArrowDown'), 'Lower Selection'],
  [ctrl('ArrowUp', 'ArrowUp', { shift: true }), 'Raise To Top'], [ctrl('ArrowDown', 'ArrowDown', { shift: true }), 'Lower To Bottom'],
  [ctrl('ArrowLeft', 'ArrowLeft'), 'Go Out To State'], [ctrl('ArrowRight', 'ArrowRight'), 'Go In To State'],
  ...['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].map((c): [Stroke, string] => [s(c, c), 'Move One Step']),
  [s('Delete', 'Delete'), 'Delete'], [s('Backspace', 'Backspace'), 'Delete'], [s('Insert', 'Insert'), 'Duplicate'],
  [s('Escape', 'Escape'), 'Cancel'], [s('Enter', 'Enter'), 'Enter'], [s('Tab', 'Tab'), 'Tab'], [s('Space', ' '), 'Pan (Space+Drag)'],
  [s('KeyP', 'p'), 'Probe'], [s('Slash', '/', { shift: true }), 'Keyboard Shortcuts'],
].map(([stroke, name]) => ({ stroke: stroke as Stroke, name: name as string }));

// The fixed keys as the Keyboard tab lists them: one line a command, its keys together (Ctrl+2 … Ctrl+9, ← ↑ → ↓).
export function fixedTable(): { name: string; keys: string }[] {
  const out: { name: string; keys: string[] }[] = [];
  for (const f of FIXED) {
    const row = out.find((r) => r.name === f.name);
    if (row) row.keys.push(strokeText(f.stroke)); else out.push({ name: f.name, keys: [strokeText(f.stroke)] });
  }
  return out.map((r) => ({ name: r.name, keys: r.name === 'Tool' ? `${r.keys[0]} … ${r.keys[r.keys.length - 1]}` : r.keys.join(' · ') }));
}

const CODE_NAMES: Record<string, string> = {
  Equal: '=', Minus: '-', NumpadAdd: 'Num +', NumpadSubtract: 'Num -', BracketLeft: '[', BracketRight: ']', Slash: '/',
  Backquote: '`', Backslash: '\\', Semicolon: ';', Quote: "'", Comma: ',', Period: '.', Space: 'Space', Escape: 'Esc',
  ArrowUp: '↑', ArrowDown: '↓', ArrowLeft: '←', ArrowRight: '→',
};

// "Ctrl+Shift+F", "F2", "[", "Ctrl+Num 0".
export function strokeText(k: Stroke): string {
  let base = CODE_NAMES[k.code];
  if (!base) {
    const m = /^(?:Key|Digit)(.)$/.exec(k.code) ?? /^Numpad(\d)$/.exec(k.code);
    base = m ? (k.code.startsWith('Numpad') ? `Num ${m[1]}` : m[1]) : k.code;
  }
  if (k.code === 'Slash' && k.shift) return '?';
  return [k.ctrl ? 'Ctrl' : '', k.alt ? 'Alt' : '', k.shift ? 'Shift' : '', base].filter(Boolean).join('+');
}

export const sameStroke = (a: Stroke, b: Stroke): boolean =>
  a.code === b.code && !!a.ctrl === !!b.ctrl && !!a.shift === !!b.shift && !!a.alt === !!b.alt;

// A key press as a stroke (null: a modifier alone).
export interface Press { code: string; key: string; ctrlKey: boolean; metaKey?: boolean; shiftKey: boolean; altKey: boolean }
export function pressStroke(e: Press): Stroke | null {
  if (/^(Control|Shift|Alt|Meta|OS)(Left|Right)?$/.test(e.code) || ['Control', 'Shift', 'Alt', 'Meta'].includes(e.key)) return null;
  const key = e.key.length === 1 ? e.key.toLowerCase() : e.key;
  return { code: e.code, key, ctrl: e.ctrlKey || e.metaKey === true || undefined, shift: e.shiftKey || undefined, alt: e.altKey || undefined };
}

// ---- this run's keys ------------------------------------------------------------------------

const changed = new Map<string, Stroke>();   // command id -> the key given to it (this run only)
const listeners = new Set<() => void>();

export const command = (id: string): Command | undefined => COMMANDS.find((c) => c.id === id);
// A command's keys now: the one given to it, else its defaults.
export function strokes(id: string): Stroke[] {
  const k = changed.get(id);
  return k ? [k] : [...(command(id)?.defaults ?? [])];
}
// The key shown for a command (menus, tooltips, the table).
export const keyText = (id: string): string => { const k = strokes(id)[0]; return k ? strokeText(k) : ''; };
export const isChanged = (id: string): boolean => changed.has(id);
export function onKeysChanged(f: () => void): () => void { listeners.add(f); return () => listeners.delete(f); }
const tell = () => { for (const f of listeners) f(); };

// Whether `k` can be given to `id`: null, or the reason in Korean (a name then a colon: no particle after it).
export function refusal(id: string, k: Stroke): string | null {
  const fixed = FIXED.find((f) => sameStroke(f.stroke, k));
  if (fixed) return `${strokeText(k)}: 바꿀 수 없는 키입니다(${fixed.name})`;
  for (const c of COMMANDS) {
    if (c.id === id) continue;
    if (strokes(c.id).some((x) => sameStroke(x, k) || (c.shiftReverses && !x.shift && sameStroke({ ...x, shift: true }, k)))) {
      return `${strokeText(k)}: 이미 쓰는 키입니다(${c.name})`;
    }
  }
  const own = command(id);
  if (own?.shiftReverses && k.shift) return `${strokeText(k)}: 이 명령은 Shift를 더해 반대로 하므로 Shift 없는 키를 고르세요`;
  return null;
}

export function setKey(id: string, k: Stroke): string | null {
  const why = refusal(id, k);
  if (why) return why;
  const c = command(id);
  if (!c) return 'unknown command';
  if (c.defaults.length === 1 && sameStroke(c.defaults[0], k)) changed.delete(id); else changed.set(id, k);
  tell();
  return null;
}
export function resetKey(id: string): void { if (changed.delete(id)) tell(); }
export function resetAll(): void { if (changed.size) { changed.clear(); tell(); } }

// ---- a press, routed -----------------------------------------------------------------------------

/* What a key press becomes (keymap.ts):
     pass     nothing changed about it: it goes on as it is
     block    a default key of a command that has another key now: nothing
     remap    the key of a command given another key: the command's own
              first default is pressed in its place (with Shift for the
              other way), so every handler keeps reading its own keys */
export type Route = { kind: 'pass' } | { kind: 'block'; id: string } | { kind: 'remap'; id: string; to: Stroke };

export function route(p: Stroke): Route {
  if (changed.size === 0) return { kind: 'pass' };
  for (const [id, k] of changed) {
    const c = command(id)!;
    const reversed = c.shiftReverses && !k.shift && sameStroke({ ...k, shift: true }, p);
    if (sameStroke(k, p) || reversed) {
      const d = c.defaults[0];
      return { kind: 'remap', id, to: reversed ? { ...d, shift: true } : d };
    }
  }
  for (const id of changed.keys()) {
    const c = command(id)!;
    if (c.defaults.some((d) => sameStroke(d, p) || (c.shiftReverses && !d.shift && sameStroke({ ...d, shift: true }, p)))) return { kind: 'block', id };
  }
  return { kind: 'pass' };
}
