/* The window's own title bar (derived from Hallym MIPS v2.5.0
   electron/src/renderer/app/app.ts: its title bar, button(), iconButton()
   and fitTitlebar(), taken out into a component).

     logo · name · [course] · file · toolbar [»] · views · (drag) · tools · [the system's caption buttons]

   The whole bar moves the window (and double-click maximises); everything
   that is pressed opts out (shared.css).  The caption buttons are the
   system's (titleBarOverlay): the bar keeps clear of them.

   A bar too narrow gives way in steps, as far as it has to (D-158):
     1. what hides nothing (Hallym MIPS's steps): the key hints, the tool
        buttons' names (their icons and tooltips stay), the other buttons'
        icons (their names stay), tighter spacing;
     2. the » rule (overflow.ts, v1 X-02): whole commands, the least
        needed first and from the right, into the » menu at the toolbar's
        end -- the menu lists them with their keys, ticks and speeds, and
        runs them as their buttons do (the caller builds it: onMore);
        the file's name gets shorter first (down to FILE_LEAST columns,
        names.ts; KEEP_OVER_FILE), and the program's name goes (the logo stays) before the most used
        ones (KEEP_OVER_NAME);
     3. the file's name to its least.
   Every command is always on the bar or in its » menu, never cut off or
   hidden for good.  (Until D-158 the toolbar went to a row of its own
   under the bar at step 3: that took the Canvas's height -- v1 Y-01 wants
   the Canvas at least half of it -- and Hallym MIPS has no such row.)

   The toolbar's commands are its [data-unit] elements, each with its
   data-keep (higher stays longer); .tgroup and .seg are containers that
   disappear when all their units have gone to the menu.  `views` (the
   tight window's Canvas / Panels switch) and `tools` never move. */

import { h, hallym, icon } from './dom.ts';
import { shortName } from './names.ts';
import { overflowOrder } from './overflow.ts';

export function button(label: string, ic: string, key: string, onClick: () => void, cls = ''): HTMLButtonElement {
  const b = h('button', { class: `btn ${cls}`.trim(), type: 'button', title: key ? `${label} (${key})` : label }, icon(ic),
    h('span', { class: 'label' }, label), key ? h('kbd', {}, key) : null);
  b.addEventListener('click', onClick);
  return b;
}

export function iconButton(title: string, ic: string, onClick: () => void): HTMLButtonElement {
  const b = h('button', { class: 'iconbtn', type: 'button', title, 'aria-label': title }, icon(ic));
  b.addEventListener('click', onClick);
  return b;
}

export interface TitleBar {
  root: HTMLElement;        // header.titlebar
  more: HTMLButtonElement;  // the » button at the toolbar's end
  setFile(name: string | null, dirty: boolean): void;
  showToolbar(shown: boolean): void;
  overflowed(): HTMLElement[];   // the commands on the » menu now, in the toolbar's order
  fit(force?: boolean): void;    // again only when something it depends on changed, or forced (fonts, the caption buttons)
}

export interface TitleBarOptions {
  appName: string;
  toolbar: HTMLElement;     // .toolbar: its commands ([data-unit]), in groups
  views?: HTMLElement;      // after the toolbar, never moved to the menu
  course?: HTMLElement;     // after the name: the course on show (A-08), never moved or hidden by the fitting
  tools: HTMLElement[];     // the icon buttons at the right end
  onMore(units: HTMLElement[], anchor: DOMRect): void;   // the » button pressed
}

export const TOOLBAR_STEPS = ['nokeys', 'nolabels', 'noicons', 'tighter'] as const;
const FILE_MOST = 32;
const FILE_LEAST = 10;
// Commands kept this much (or more) stay on the bar longer than the program's name does; those kept KEEP_OVER_FILE
// or less would leave before the file's name gets shorter -- none: at a lab PC's 150 % the whole toolbar with a
// shorter name (its whole name in the tooltip) beats the whole name with commands on the » menu.
export const KEEP_OVER_NAME = 7;
export const KEEP_OVER_FILE = 0;

export function titleBar(o: TitleBarOptions): TitleBar {
  const fileLabel = h('span', { class: 'file' });
  const tools = h('span', { class: 'tools' }, ...o.tools);
  const more = h('button', { class: 'btn more', type: 'button', title: 'More commands', 'aria-label': 'More commands', 'aria-haspopup': 'menu', hidden: true }, '»');
  o.toolbar.append(more);
  const toolbarSlot = h('span', { class: 'toolbar-slot' }, o.toolbar);
  const root = h('header', { class: 'titlebar' },
    h('span', { class: 'brand' },
      h('img', { class: 'logo', src: hallym('logo/symbol-basic.svg'), alt: '' }),
      h('span', { class: 'appname' }, o.appName)),
    o.course ?? null, fileLabel, toolbarSlot, o.views ?? null, h('span', { class: 'drag' }), tools);
  let file: { name: string; dirty: boolean } | null = null;
  let toolbarShown = true;
  const units = () => [...o.toolbar.querySelectorAll<HTMLElement>('[data-unit]')];
  more.addEventListener('click', () => o.onMore(overflowed(), more.getBoundingClientRect()));

  const showFileName = (cols: number) => {
    fileLabel.title = file ? file.name : '';
    fileLabel.replaceChildren(file ? h('b', { class: 'mono' }, shortName(file.name, cols)) : '',
      file?.dirty ? h('span', { class: 'dirty', title: 'Unsaved changes' }, ' •') : '');
  };
  const steps = (level: number) => {
    TOOLBAR_STEPS.forEach((s, k) => o.toolbar.classList.toggle(s, k < level));
    root.classList.toggle('tighter', level === TOOLBAR_STEPS.length);
  };
  const end = () => root.getBoundingClientRect().right - parseFloat(getComputedStyle(root).paddingRight);
  const fits = () => tools.getBoundingClientRect().right <= end() + 0.5;
  // A group (or the tools' segment) with none of its commands on the bar goes too.
  const syncGroups = () => {
    for (const g of o.toolbar.querySelectorAll<HTMLElement>('.tgroup, .seg')) {
      const inside = [...g.querySelectorAll<HTMLElement>('[data-unit]')];
      if (inside.length) g.toggleAttribute('data-over', inside.every((u) => u.hasAttribute('data-over')));
    }
  };
  const overflowed = () => units().filter((u) => u.hasAttribute('data-over'));
  // The longest file name that fits, between FILE_LEAST and FILE_MOST columns; false: not even the least.
  const longest = (): boolean => {
    let lo = FILE_LEAST;
    let hi = FILE_MOST;
    showFileName(lo);
    if (!fits()) return false;
    while (lo < hi) {
      const mid = Math.ceil((lo + hi) / 2);
      showFileName(mid);
      if (fits()) lo = mid; else hi = mid - 1;
    }
    showFileName(lo);
    return true;
  };

  // What the fitting depends on: fitting again for nothing reads the layout dozens of times at a narrow width.
  let fitted = '';
  const fit = (force = false) => {
    const key = [window.innerWidth, file?.name ?? '', file?.dirty ?? false, toolbarShown, o.views?.hidden ?? true, o.toolbar.dataset.fit ?? '', o.course?.textContent ?? '', o.course?.hidden ?? true].join('|');
    if (!force && key === fitted) return;
    fitted = key;
    root.classList.remove('noapp');
    showFileName(FILE_MOST);
    for (const u of units()) u.removeAttribute('data-over');
    syncGroups();
    more.hidden = true;
    steps(0);
    if (toolbarShown) {
      // 1. The steps that hide nothing.
      for (let level = 0; level <= TOOLBAR_STEPS.length; level += 1) {
        steps(level);
        if (fits()) return;
      }
      // 2. Whole commands into the » menu, one at a time, the least needed first; the file's whole name is kept while
      //    only those go (keep <= KEEP_OVER_FILE), then it may get shorter, and the program's name goes before the
      //    commands the student uses most (Run, 1 Cycle, Reset, Load Program: keep >= KEEP_OVER_NAME) do.
      const all = units();
      const order = overflowOrder(all.map((u) => ({ keep: Number(u.dataset.keep ?? 0) })));
      let shorter = false;
      const fitsNow = () => (shorter ? longest() : fits());
      for (const i of order) {
        const keep = Number(all[i].dataset.keep ?? 0);
        if (keep > KEEP_OVER_FILE && !shorter) {
          shorter = true;
          if (longest()) return;
        }
        if (keep >= KEEP_OVER_NAME && !root.classList.contains('noapp')) {
          root.classList.add('noapp');
          if (longest()) return;
        }
        more.hidden = false;
        all[i].setAttribute('data-over', '');
        syncGroups();
        if (fitsNow()) return;
      }
    } else if (longest()) {
      return;
    }
    // 4. The program's name (the logo stays), then the file's name at its least.
    root.classList.add('noapp');
    if (!longest()) showFileName(FILE_LEAST);
  };

  return {
    root, more,
    setFile: (name, dirty) => { file = name === null ? null : { name, dirty }; fit(); },
    showToolbar: (shown) => { toolbarShown = shown; toolbarSlot.hidden = !shown; o.toolbar.hidden = !shown; fit(); },
    overflowed,
    fit,
  };
}
