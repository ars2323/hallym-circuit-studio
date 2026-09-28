/* The Tunnels panel (left, lower; v1 TunnelList, S-11, V-08, I-175, B-08;
   D-150): the tunnels of the circuit on show by name (logic/tunnels.ts) --
   a colour chip, the name, how many; a name with one tunnel only has its
   count in amber and says so on hover.

     the name       the next tunnel of that name, marked and brought into
                    view (each press the next one, then the first again)
     the colour chip Tunnel Color: Automatic (from the name, not saved) or
                    one of the twelve (every tunnel of that name; the
                    engine keeps it in the .circ, edit.tunnelColor, one
                    undo step) */

import type { Snapshot } from '../../main/protocol.ts';
import { code, h } from '../shared/dom.ts';
import type { NoticeHost } from '../shared/notice.ts';
import { count } from './logic/facts.ts';
import { palette, TunnelCycle, type TunnelEntry, tunnelEntries, tunnelTip } from './logic/tunnels.ts';

export interface TunnelsPanel {
  set(s: { fileId: string; key: string; snapshot: Snapshot } | null, loading?: boolean): void;
  entries(): TunnelEntry[];
}

export const NO_TUNNELS = { title: '터널이 없습니다', body: '이 회로에 Tunnel을 놓으면 이름별로 여기에 모입니다.' };

export function tunnelsPanel(o: {
  host: NoticeHost;
  go(tunnelId: string): void;
  setColor(tunnelId: string, color: string | null): void;
  editable(): boolean;
}): TunnelsPanel {
  let entries: TunnelEntry[] = [];
  let shownKey = '';
  const cycle = new TunnelCycle();
  const menu = h('div', { class: 'colormenu', role: 'menu', 'aria-label': 'Tunnel Color', hidden: true });
  document.body.append(menu);
  let menuFor: TunnelEntry | null = null;

  function openMenu(e: TunnelEntry, at: HTMLElement): void {
    menuFor = e;
    const item = (label: string, color: string | null) => {
      const on = color === null ? !e.chosen : e.chosen && e.color.toLowerCase() === color;
      const b = h('button', { type: 'button', role: 'menuitemradio', 'aria-checked': String(on), class: `coloritem${on ? ' on' : ''}`, title: label },
        color ? h('span', { class: 'swatch', style: `--c:${color}` }) : h('span', { class: 'swatch auto', style: `--c:${e.chosen ? '#ffffff' : e.color}` }),
        h('span', {}, label));
      b.addEventListener('click', () => { closeMenu(); if (!on) o.setColor(e.ids[0], color); });
      return b;
    };
    menu.replaceChildren(
      h('div', { class: 'colorhead' }, 'Tunnel Color · ', code(e.name)),
      item('Automatic (from the name, not saved)', null),
      h('div', { class: 'colorgrid' }, ...palette().map((p) => item(p.name, p.color))));
    menu.hidden = false;
    // beside the row, over the Canvas: the list stays in view (the panel is short, low in the window)
    const r = (at.closest('li') ?? at).getBoundingClientRect();
    const w = menu.offsetWidth, hh = menu.offsetHeight;
    menu.style.left = `${Math.max(8, Math.min(r.right + 6, window.innerWidth - w - 8))}px`;
    menu.style.top = `${Math.max(8, Math.min(r.top - 6, window.innerHeight - hh - 8))}px`;
    menu.querySelector<HTMLElement>('.coloritem.on, .coloritem')?.focus();
  }
  function closeMenu(): void { menu.hidden = true; menuFor = null; }
  window.addEventListener('pointerdown', (e) => { if (!menu.hidden && !menu.contains(e.target as Node) && !(e.target as HTMLElement).closest?.('.tswatch')) closeMenu(); }, true);
  menu.addEventListener('keydown', (e) => {
    const items = [...menu.querySelectorAll<HTMLElement>('.coloritem')];
    const i = items.indexOf(document.activeElement as HTMLElement);
    if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); closeMenu(); }
    else if (e.key === 'ArrowDown' || e.key === 'ArrowRight') { e.preventDefault(); items[(i + 1) % items.length]?.focus(); }
    else if (e.key === 'ArrowUp' || e.key === 'ArrowLeft') { e.preventDefault(); items[(i - 1 + items.length) % items.length]?.focus(); }
  });

  function render(): void {
    if (!entries.length) { o.host.empty(NO_TUNNELS); return; }
    o.host.fill(h('ul', { class: 'list tunnels' }, ...entries.map((e) => {
      const chip = h('button', {
        type: 'button', class: `tswatch${e.chosen ? ' chosen' : ''}`, style: `--c:${e.color}`, 'aria-label': `Tunnel Color: ${e.name}`,
        title: e.chosen ? 'Tunnel Color — 고른 색입니다(파일에 저장됩니다)' : 'Tunnel Color — 이름으로 정한 자동 색입니다', disabled: !o.editable(),
      });
      chip.addEventListener('click', (ev) => { ev.stopPropagation(); if (menuFor === e && !menu.hidden) closeMenu(); else openMenu(e, chip); });
      const name = h('button', { type: 'button', class: 'tname', title: tunnelTip(e) }, code(e.name),
        h('span', { class: `count${e.lone ? ' lone' : ''}` }, e.lone ? '(1)' : count(e.ids.length)));
      name.addEventListener('click', () => { const id = cycle.next(e); if (id) o.go(id); });
      return h('li', { 'data-name': e.name }, chip, name);
    })));
  }

  return {
    set: (s, loading = false) => {
      if (!s) { entries = []; shownKey = ''; cycle.reset(); closeMenu(); if (loading) o.host.fill(); else render(); return; }
      if (s.key !== shownKey) { cycle.reset(); closeMenu(); shownKey = s.key; }
      entries = tunnelEntries(s.snapshot);
      if (menuFor && !entries.some((e) => e.name === menuFor!.name)) closeMenu();
      render();
    },
    entries: () => entries,
  };
}
