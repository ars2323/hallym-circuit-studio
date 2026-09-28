/* The right-click menus of the Components list and the circuit tabs (N-10,
   D-157; the original's explorer menus, I-109): built from the same
   registry as the Canvas's (registry.ts, contexts 'components' and
   'circuitTab').  This file puts the menus on the panels (a right click,
   the menu key, Shift+F10) and registers the items: a circuit's are the
   Circuits panel's own list (N-11's circuits.ts circuitItems: Edit Circuit
   Layout, Edit Circuit Appearance, Rename…, Set As Main Circuit, Port
   Order…, Auto Appearance, Move Up/Down, Remove Circuit), a library's
   Unload Library (N-11's).  N-17 adds Analyze Circuit and Get Circuit
   Statistics with the Project menu.  A library's tool has no menu (the
   original's has none); Hallym MIPS not in the file yet has none (v1 V-01). */

import type { LibraryGroup } from '../../../main/protocol.ts';
import { type MenuEntry, showMenu } from '../../canvas/overlays/menu.ts';
import { type CircuitTabTarget, type ComponentsTarget, menuFor, registerMenu } from './registry.ts';

export interface SideMenuDeps {
  components: HTMLElement;                 // the Components list's body
  circuitTabs: HTMLElement;                // the circuit tabs' strip
  file(): { fileId: string; main: string; editable: boolean; libraries: LibraryGroup[] | null; circuitName(id: string): string } | null;
  circuitItems(circuitId: string): MenuEntry[];            // the Circuits panel's items for that circuit (N-11)
  libraryItems(lib: string, display: string): MenuEntry[]; // Unload Library (N-11)
}

// The data-tool key of the Components list ("lib/name", "" for this file's circuits: components.ts pickKey).
export function toolOfKey(key: string): { lib: string | null; name: string } {
  const i = key.indexOf('/');
  const lib = key.slice(0, i);
  return { lib: lib === '' ? null : lib, name: key.slice(i + 1) };
}

export function installSideMenus(d: SideMenuDeps): void {
  registerMenu('components', {
    id: 'circuit', order: 10,
    items: (t: ComponentsTarget) => (t.kind === 'circuit' && t.circuitId ? d.circuitItems(t.circuitId) : []),
  });
  registerMenu('components', {
    id: 'library', order: 20,
    items: (t: ComponentsTarget) => (t.kind === 'library' && t.lib ? d.libraryItems(t.lib, t.display) : []),
  });
  registerMenu('circuitTab', {
    id: 'circuit', order: 10,
    items: (t: CircuitTabTarget) => d.circuitItems(t.circuitId),
  });

  const componentsTarget = (el: HTMLElement): ComponentsTarget | null => {
    const f = d.file();
    if (!f) return null;
    const libs = f.libraries ?? [];
    const tool = el.closest('[data-tool]') as HTMLElement | null;
    if (tool?.dataset.tool) {
      const { lib, name } = toolOfKey(tool.dataset.tool);
      const g = libs.find((x) => x.lib === lib);
      if (!g || g.pending) return null;
      const t = g.tools.find((x) => x.name === name);
      if (lib === null) {
        const circuitId = t?.circuitId;
        if (!circuitId) return null;
        return { fileId: f.fileId, kind: 'circuit', lib: null, name, display: t?.display ?? name, circuitId, main: circuitId === f.main };
      }
      return { fileId: f.fileId, kind: 'tool', lib, name, display: t?.display ?? name };
    }
    const summary = el.closest('summary');
    const details = summary?.parentElement;
    if (!summary || !details) return null;
    const i = [...(details.parentElement?.children ?? [])].indexOf(details);
    const g = libs[i];
    if (!g || g.pending) return null;
    return { fileId: f.fileId, kind: 'library', lib: g.lib, name: g.lib ?? '', display: g.display ?? g.lib ?? '' };
  };
  const openComponents = (el: HTMLElement, x: number, y: number): boolean => {
    const t = componentsTarget(el);
    if (!t) return false;
    const entries = menuFor('components', t);
    if (!entries.length) return false;
    showMenu(entries, x, y);
    return true;
  };
  d.components.addEventListener('contextmenu', (e) => {
    const el = e.target as HTMLElement;
    if (el.closest('input')) return;                // the search field keeps its own menu
    e.preventDefault();
    openComponents(el, e.clientX, e.clientY);
  });
  d.components.addEventListener('keydown', (e) => {
    if (!(e.key === 'ContextMenu' || (e.shiftKey && e.key === 'F10'))) return;
    const el = document.activeElement as HTMLElement | null;
    if (!el || !d.components.contains(el) || el.closest('input')) return;
    const r = el.getBoundingClientRect();
    if (openComponents(el, r.left + 12, r.bottom)) e.preventDefault();
  });

  const tabTarget = (el: HTMLElement): CircuitTabTarget | null => {
    const f = d.file();
    const tab = el.closest('[data-id]') as HTMLElement | null;
    if (!f || !tab?.dataset.id) return null;
    const id = tab.dataset.id;
    return { fileId: f.fileId, circuitId: id, name: f.circuitName(id), main: id === f.main, editable: f.editable };
  };
  const openTab = (el: HTMLElement, x: number, y: number): boolean => {
    const t = tabTarget(el);
    if (!t) return false;
    const entries = menuFor('circuitTab', t);
    if (!entries.length) return false;
    showMenu(entries, x, y);
    return true;
  };
  d.circuitTabs.addEventListener('contextmenu', (e) => {
    e.preventDefault();
    openTab(e.target as HTMLElement, e.clientX, e.clientY);
  });
  d.circuitTabs.addEventListener('keydown', (e) => {
    if (!(e.key === 'ContextMenu' || (e.shiftKey && e.key === 'F10'))) return;
    const el = document.activeElement as HTMLElement | null;
    if (!el || !d.circuitTabs.contains(el)) return;
    const r = el.getBoundingClientRect();
    if (openTab(el, r.left + 12, r.bottom)) e.preventDefault();
  });
}
