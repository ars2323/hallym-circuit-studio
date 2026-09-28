/* The two heads every panel is made from (derived from Hallym MIPS v2.3.0
   electron/src/renderer/app/ui.ts): one height, one font, one set of
   margins, one place for controls on the right.

     panelHead   a panel's name: Attributes, Minimap
     tabsHead    names that compete for one place: Components / Circuits,
                 Messages / Cycle View / Console

   Everything on the right (counts, switches, buttons) goes into `aside`,
   separated from each other, so that two of them never read as one.

   A tab can be hidden and shown again (a narrow window moves a panel into
   another panel's tabs: app/logic/layout.ts); the selected one then stays
   selected, or the first shown one is.

     tabStrip    tabs that come and go (the open files, the circuits
                 opened in one): the same look as tabsHead's, each with its
                 name in the mono font, a dot while it has unsaved changes,
                 and a close button if it can be closed.  (New here: Hallym
                 MIPS has one file at a time.) */

import { h, icon } from './dom.ts';

export interface Head {
  root: HTMLElement;
  aside: HTMLElement;   // the right-hand slot
  setMeta(text: string | Node): void;  // a quiet count or note, left of the controls
}

function head(cls: string, left: Node): Head {
  const meta = h('span', { class: 'pmeta', hidden: true }); // no note: nothing, not even its divider
  const aside = h('span', { class: 'paside' });
  const root = h('div', { class: `phead ${cls}` }, left, h('span', { class: 'pgrow' }), meta, aside);
  return {
    root, aside,
    setMeta: (text) => { meta.replaceChildren(text); meta.hidden = text === ''; },
  };
}

export function panelHead(title: string): Head {
  return head('', h('span', { class: 'ptitle' }, title));
}

export interface TabsHead extends Head {
  tabs: HTMLButtonElement[];
  selected(): number;
  select(index: number): void;
  show(index: number, shown: boolean): void;
}

export function tabsHead(titles: string[], onSelect: (index: number) => void): TabsHead {
  let current = 0;
  const tabs = titles.map((t, i) => {
    const b = h('button', { class: 'ptab', type: 'button', role: 'tab' }, t);
    b.addEventListener('click', () => { select(i); onSelect(i); });
    return b;
  });
  const select = (i: number) => {
    current = i;
    tabs.forEach((t, k) => {
      t.classList.toggle('on', k === i);
      t.setAttribute('aria-selected', String(k === i));
    });
  };
  const show = (i: number, shown: boolean) => {
    tabs[i].hidden = !shown;
    if (!shown && current === i) {
      const first = tabs.findIndex((t) => !t.hidden);
      if (first >= 0) { select(first); onSelect(first); }
    }
  };
  const hd = head('with-tabs', h('span', { class: 'ptabs', role: 'tablist' }, ...tabs));
  select(0);
  return { ...hd, tabs, selected: () => current, select, show };
}

// A column the width took away, to turn back on ("+ Bin"), or turned on ("Bin").
// (Taken with the Registers and Data panels, N-14.)
export function columnButton(name: string, on: boolean, onClick: () => void): HTMLButtonElement {
  const b = headButton(on ? name : `+ ${name}`, on ? '폭에 맞춰 다시 숨깁니다' : '좁아서 숨긴 열입니다. 누르면 보입니다', onClick);
  b.classList.add('colbtn');
  b.classList.toggle('on', on);
  b.setAttribute('aria-pressed', String(on));
  return b;
}

// A small button for a head's right-hand slot.
export function headButton(label: string, title: string, onClick: () => void): HTMLButtonElement {
  const b = h('button', { class: 'hbtn', type: 'button', title }, label);
  b.addEventListener('click', onClick);
  return b;
}

export interface StripItem {
  id: string;
  label: string;
  title?: string;       // the tooltip: the whole name, a path
  dirty?: boolean;
}

export interface TabStrip {
  root: HTMLElement;
  set(items: StripItem[], active: string | null): void;
}

export function tabStrip(o: { label: string; closable: boolean; onSelect(id: string): void; onClose?(id: string): void;
                           canClose?(items: StripItem[]): boolean }): TabStrip {
  const root = h('div', { class: `ptabs strip${o.closable ? ' closable' : ''}`, role: 'tablist', 'aria-label': o.label });
  return {
    root,
    set: (items, active) => {
      const closable = o.closable && (o.canClose?.(items) ?? true);
      root.replaceChildren(...items.map((it) => {
        const on = it.id === active;
        const tab = h('button', { class: `ptab${on ? ' on' : ''}`, type: 'button', role: 'tab', 'aria-selected': String(on), title: it.title ?? it.label, 'data-id': it.id },
          h('span', { class: 'mono' }, it.label), it.dirty ? h('span', { class: 'dirty', title: 'Unsaved changes' }, '•') : null);
        tab.addEventListener('click', () => o.onSelect(it.id));
        if (!closable) return tab;
        const close = h('button', { class: 'tabclose', type: 'button', title: 'Close', 'aria-label': `Close ${it.label}` }, icon('x'));
        close.addEventListener('click', (e) => { e.stopPropagation(); o.onClose?.(it.id); });
        return h('span', { class: `stripitem${on ? ' on' : ''}` }, tab, close);
      }));
    },
  };
}
