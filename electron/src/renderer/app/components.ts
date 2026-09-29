/* The Components panel (left, upper; v1 explorer and its search box, I-53,
   I-61, I-62, I-106, I-110, I-113, V-01; D-150): the engine's library
   (model.library) as a tree -- this file's circuits first, under the file's
   name, then each library by its shown name; the bundled Hallym MIPS is
   there before the file has it ("not in the file yet": the first part
   placed puts it in, V-01) -- and a search box over it.

     click a part            pick it up (hcs:place-tool without a point:
                             the Canvas's placement gesture places it, N-08)
     drag it to the Canvas   one there, where it is dropped (I-62 정함)
     double click a circuit  open it (its tab)
     type in the box         the list becomes the matches (logic/search.ts:
                             the palette's ranking, no commands, the circuit
                             on show included); ↓ to the list, Enter picks
                             the chosen (or first) one, Esc clears the box;
                             a number after the name is picked with the part
                             ("and 3": an AND gate of three inputs)

   What is picked stays marked until another is.

   N-11 (v1 P-03 탭 간 라이브러리, I-109): the other open files' circuits
   under "Open Files · name" -- picking one loads that file as a library
   first (Project › Load Library › Logisim Library, one undo step); a file
   never saved, or one that already uses this file, shows why not -- and a
   library's right click offers Unload Library. */

import type { LibraryGroup } from '../../main/protocol.ts';
import { code, h } from '../shared/dom.ts';
import type { NoticeHost } from '../shared/notice.ts';
import { count } from './logic/facts.ts';
import { search, searchHint, SEARCH_HINTS, type SearchItem } from './logic/search.ts';

export const TOOL_MIME = 'application/x-hcs-tool';

export interface Pick {
  lib: string | null;
  name: string;
  attrs?: Record<string, string>;
}

export interface ComponentsState {
  fileId: string;
  fileName: string;
  circuit: string;                        // the circuit on show (circuitId)
  libraries: LibraryGroup[] | string | undefined;   // a string: why there is none; undefined: on its way
  openFiles?: OpenFileGroup[];           // the other open files (N-11)
}

// Another open file's circuits (model.libraries openFiles, the tab's name).
export interface OpenFileGroup { fileId: string; name: string; state: 'ok' | 'loaded' | 'unsaved' | 'self' | 'circular'; circuits: string[] }

export interface ComponentsPanel {
  set(state: ComponentsState | null): void;
  picked(): string | null;
  focusSearch(): void;
}

const pickKey = (lib: string | null, name: string) => `${lib ?? ''}/${name}`;

export function componentsPanel(o: {
  host: NoticeHost;
  onPick(p: Pick): void;
  onOpenCircuit(circuitId: string): void;
  onOpenFileCircuit?(fileId: string, circuit: string): void;          // N-11: load that file as a library, then pick it
  onLibraryMenu?(lib: string, display: string, x: number, y: number): void;   // N-11: Unload Library
  recent(): readonly string[];
  favorites(): readonly string[];
}): ComponentsPanel {
  let state: ComponentsState | null = null;
  let held: string | null = null;
  let shownFile: string | null = null;
  const input = h('input', { type: 'search', class: 'compsearch-input', placeholder: SEARCH_HINTS[0], 'aria-label': 'Search parts', spellcheck: 'false', autocomplete: 'off' });
  const tree = h('div', { class: 'comptree' });
  const results = h('ul', { class: 'list compresults', role: 'listbox', 'aria-label': 'Matching parts', hidden: true });
  const empty = h('p', { class: 'compnone', hidden: true });
  const root = h('div', { class: 'comps' }, h('div', { class: 'compsearch' }, input), tree, results, empty);
  // the example text as long as the box has room for, whole
  const ruler = document.createElement('canvas').getContext('2d');
  new ResizeObserver(() => {
    const cs = getComputedStyle(input);
    const room = input.clientWidth - parseFloat(cs.paddingLeft) - parseFloat(cs.paddingRight) - 2;
    if (!ruler || room <= 0) return;
    ruler.font = `${cs.fontStyle} ${cs.fontWeight} ${cs.fontSize} ${cs.fontFamily}`;
    input.placeholder = searchHint(room, (t) => ruler.measureText(t).width);
  }).observe(input);
  let items: SearchItem[] = [];

  function pick(p: Pick): void {
    held = pickKey(p.lib, p.name);
    mark();
    o.onPick(p);
  }

  function mark(): void {
    for (const b of root.querySelectorAll<HTMLElement>('[data-tool]')) b.closest('li')?.classList.toggle('on', b.dataset.tool === held);
  }

  function toolButton(lib: string | null, name: string, label: Node | string, title: string, circuitId?: string, attrs?: Record<string, string>): HTMLButtonElement {
    const b = h('button', { type: 'button', class: 'tool', title, draggable: 'true', 'data-tool': pickKey(lib, name) }, label);
    b.addEventListener('click', () => {
      if (circuitId !== undefined && circuitId === state?.circuit) return; // the circuit on show: nothing to place (I-61)
      pick(attrs && Object.keys(attrs).length ? { lib, name, attrs } : { lib, name });
    });
    if (circuitId !== undefined) b.addEventListener('dblclick', () => o.onOpenCircuit(circuitId));
    b.addEventListener('dragstart', (e) => {
      if (circuitId !== undefined && circuitId === state?.circuit) { e.preventDefault(); return; }
      e.dataTransfer?.setData(TOOL_MIME, JSON.stringify({ lib, name, ...(attrs && Object.keys(attrs).length ? { attrs } : {}) } satisfies Pick));
      if (e.dataTransfer) e.dataTransfer.effectAllowed = 'copy';
    });
    return b;
  }

  // The list as last built: the same list again is not built again -- a part being dragged from it would be taken
  // out of the page and the drag would end with nothing dropped (and a group opened or closed would snap back).
  let built = '';
  function renderTree(libs: LibraryGroup[]): void {
    const st = state!;
    const key = JSON.stringify([libs, st.fileName, st.circuit, st.openFiles ?? []]);
    if (key === built && tree.childElementCount > 0) { mark(); return; }
    built = key;
    tree.replaceChildren(...libs.map((g, i) => {
      const summary = h('summary', {}, g.lib === null ? st.fileName : g.display ?? g.lib,
        g.pending ? h('span', { class: 'dim', title: '처음 놓으면 이 파일에 들어갑니다' }, 'not in the file yet') : null,
        h('span', { class: 'count' }, count(g.tools.length)));
      // a library's right click: Unload Library (not the file's own circuits, not the pending Hallym MIPS, V-01)
      const lib = g.lib;
      if (lib !== null && !g.pending && o.onLibraryMenu) summary.addEventListener('contextmenu', (e) => { e.preventDefault(); o.onLibraryMenu!(lib, g.display ?? lib, e.clientX, e.clientY); });
      return h('details', { class: `libgroup${g.pending ? ' pending' : ''}`, open: i < 2 || g.pending }, summary,
        h('ul', { class: 'list' }, ...g.tools.map((t) => h('li', {},
          toolButton(g.lib, t.name, t.circuitId ? code(t.display) : t.display, t.circuitId ? `${t.name} (double click: open)` : t.name, t.circuitId)))));
    }), ...openFileGroups(st.openFiles ?? []));
    mark();
  }

  // The other open files' circuits (P-03): picking one loads its file as a library first.
  function openFileGroups(groups: OpenFileGroup[]): HTMLElement[] {
    return groups.filter((g) => g.state !== 'loaded' && g.state !== 'self').map((g) => {
      const why = g.state === 'unsaved' ? '라이브러리로 쓰려면 그 파일을 먼저 저장하세요' : g.state === 'circular' ? '그 파일이 이미 이 파일을 쓰고 있어 넣을 수 없습니다(순환 참조)' : null;
      return h('details', { class: `libgroup openfile${why ? ' off' : ''}`, open: false },
        h('summary', { title: why ?? `${g.name}: 고르면 이 파일에 라이브러리로 넣습니다` }, 'Open Files · ', code(g.name), h('span', { class: 'count' }, count(g.circuits.length))),
        h('ul', { class: 'list' }, ...g.circuits.map((c) => {
          const b = h('button', { type: 'button', class: 'tool', title: why ?? `${c} (${g.name})`, disabled: why !== null, 'data-openfile': `${g.fileId}/${c}` }, code(c));
          b.addEventListener('click', () => o.onOpenFileCircuit?.(g.fileId, c));
          return h('li', {}, b);
        })));
    });
  }

  function renderResults(): void {
    const st = state!;
    const libs = Array.isArray(st.libraries) ? st.libraries : [];
    items = search(input.value, { libraries: libs, fileName: st.fileName, current: st.circuit, includeCurrent: true, recent: o.recent(), favorites: o.favorites() });
    const typed = input.value.trim() !== '';
    tree.hidden = typed;
    results.hidden = !typed || items.length === 0;
    empty.hidden = !typed || items.length > 0;
    if (typed && !items.length) empty.replaceChildren('맞는 부품이 없습니다: ', code(input.value.trim()));
    results.replaceChildren(...items.map((it) => {
      const b = toolButton(it.lib ?? null, it.tool!, h('span', { class: 'rname' }, it.kind === 'subcircuit' ? code(it.name) : it.name),
        it.kind === 'subcircuit' ? `${it.name} (${it.group ?? 'this file'})` : `${it.name} (${it.group ?? ''})`, it.circuitId, it.attrs);
      b.setAttribute('role', 'option');
      b.append(h('span', { class: 'rgroup' }, it.attrText || (it.kind === 'subcircuit' ? 'Subcircuit' : it.group ?? '')));
      return h('li', {}, b);
    }));
    mark();
  }

  function chooseFirst(): void {
    const focused = document.activeElement as HTMLElement | null;
    const b = (focused && results.contains(focused) ? focused : results.querySelector('button')) as HTMLButtonElement | null;
    b?.click();
  }

  input.addEventListener('input', () => renderResults());
  input.addEventListener('keydown', (e) => {
    if (e.key === 'ArrowDown') { e.preventDefault(); results.querySelector<HTMLElement>('button')?.focus(); }
    else if (e.key === 'Enter') { e.preventDefault(); chooseFirst(); }
    else if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); input.value = ''; renderResults(); }
  });
  results.addEventListener('keydown', (e) => {
    const buttons = [...results.querySelectorAll<HTMLButtonElement>('button')];
    const i = buttons.indexOf(document.activeElement as HTMLButtonElement);
    if (e.key === 'ArrowDown') { e.preventDefault(); buttons[Math.min(buttons.length - 1, i + 1)]?.focus(); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); if (i <= 0) input.focus(); else buttons[i - 1].focus(); }
    else if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); input.value = ''; renderResults(); input.focus(); }
  });

  function render(): void {
    const st = state;
    if (!st || st.libraries === undefined) { o.host.fill(); return; }
    if (typeof st.libraries === 'string') {
      o.host.empty({ title: '부품 목록을 받지 못했습니다', body: '엔진이 이 파일의 부품 목록을 보내지 않았습니다. 파일을 닫았다가 다시 열어 보세요.' });
      return;
    }
    if (o.host.root.firstChild !== root) o.host.fill(root);
    renderTree(st.libraries);
    renderResults();
  }

  return {
    set: (next) => {
      if (next?.fileId !== shownFile) { held = null; shownFile = next?.fileId ?? null; input.value = ''; }
      state = next;
      render();
    },
    picked: () => held,
    focusSearch: () => input.focus(),
  };
}
