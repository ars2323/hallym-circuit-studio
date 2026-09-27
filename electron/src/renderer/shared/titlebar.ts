/* The window's own title bar (derived from Hallym MIPS v2.3.0
   electron/src/renderer/app/app.ts: its title bar, button(), iconButton()
   and fitTitlebar(), taken out into a component).

     logo · name · file · toolbar · (drag) · tools · [the system's caption buttons]

   The whole bar moves the window (and double-click maximises); everything
   that is pressed opts out (shared.css).  The caption buttons are the
   system's (titleBarOverlay): the bar keeps clear of them.

   A bar too narrow gives way in steps, as far as it has to: the key hints,
   the tool buttons' names (their icons and tooltips stay), the other
   buttons' icons (their names stay), tighter spacing -- and then, a step
   Hallym MIPS does not need (its toolbar is five buttons, this one about
   twenty): the toolbar moves to a row of its own under the bar, where it
   takes the same steps again for that row's width.  Only then the file's
   name gets shorter (logic in names.ts), and last the program's name goes
   (the logo stays).  Nothing is ever cut off or hidden for good: every
   button stays, with its name in its tooltip. */

import { h, hallym, icon } from './dom.ts';
import { shortName } from './names.ts';

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
  row: HTMLElement;         // the toolbar's own row, under the bar (hidden while the bar holds it)
  setFile(name: string | null, dirty: boolean): void;
  showToolbar(shown: boolean): void;
  fit(): void;
}

export interface TitleBarOptions {
  appName: string;
  toolbar: HTMLElement;     // .toolbar: its buttons, in groups
  tools: HTMLElement[];     // the icon buttons at the right end
}

export const TOOLBAR_STEPS = ['nokeys', 'nolabels', 'noicons', 'tighter'] as const;
const FILE_MOST = 32;
const FILE_LEAST = 10;

export function titleBar(o: TitleBarOptions): TitleBar {
  const fileLabel = h('span', { class: 'file' });
  const tools = h('span', { class: 'tools' }, ...o.tools);
  const toolbarSlot = h('span', { class: 'toolbar-slot' }, o.toolbar);
  const root = h('header', { class: 'titlebar' },
    h('span', { class: 'brand' },
      h('img', { class: 'logo', src: hallym('logo/symbol-basic.svg'), alt: '' }),
      h('span', { class: 'appname' }, o.appName)),
    fileLabel, toolbarSlot, h('span', { class: 'drag' }), tools);
  const row = h('div', { class: 'toolrow', hidden: true });
  let file: { name: string; dirty: boolean } | null = null;
  let toolbarShown = true;

  const showFileName = (cols: number) => {
    fileLabel.title = file ? file.name : '';
    fileLabel.replaceChildren(file ? h('b', { class: 'mono' }, shortName(file.name, cols)) : '',
      file?.dirty ? h('span', { class: 'dirty', title: 'Unsaved changes' }, ' •') : '');
  };
  const steps = (level: number) => TOOLBAR_STEPS.forEach((s, k) => o.toolbar.classList.toggle(s, k < level));
  const end = () => root.getBoundingClientRect().right - parseFloat(getComputedStyle(root).paddingRight);
  const fitsBar = () => tools.getBoundingClientRect().right <= end() + 0.5;
  const fitsRow = () => o.toolbar.scrollWidth <= row.clientWidth - parseFloat(getComputedStyle(row).paddingLeft) - parseFloat(getComputedStyle(row).paddingRight) + 0.5;

  const fit = () => {
    root.classList.remove('noapp', 'tighter');
    showFileName(FILE_MOST);
    if (toolbarShown) {
      // 1. In the bar, one step at a time.
      if (o.toolbar.parentElement !== toolbarSlot) toolbarSlot.append(o.toolbar);
      row.hidden = true;
      root.classList.remove('toolbar-below');
      for (let level = 0; level <= TOOLBAR_STEPS.length; level += 1) {
        steps(level);
        root.classList.toggle('tighter', level === TOOLBAR_STEPS.length);
        if (fitsBar()) return;
      }
      // 2. In its own row, the steps again for the row's width.
      root.classList.remove('tighter');
      row.append(o.toolbar);
      row.hidden = false;
      root.classList.add('toolbar-below');
      for (let level = 0; level <= TOOLBAR_STEPS.length; level += 1) {
        steps(level);
        if (fitsRow()) break;
      }
    } else {
      row.hidden = true;
      root.classList.remove('toolbar-below');
    }
    if (fitsBar()) return;
    // 3. The longest file name that fits, then without the program's name.
    const longest = (): boolean => {
      let lo = FILE_LEAST;
      let hi = FILE_MOST;
      showFileName(lo);
      if (!fitsBar()) return false;
      while (lo < hi) {
        const mid = Math.ceil((lo + hi) / 2);
        showFileName(mid);
        if (fitsBar()) lo = mid; else hi = mid - 1;
      }
      showFileName(lo);
      return true;
    };
    if (longest()) return;
    root.classList.add('noapp');
    if (!longest()) showFileName(FILE_LEAST);
  };

  return {
    root, row,
    setFile: (name, dirty) => { file = name === null ? null : { name, dirty }; fit(); },
    showToolbar: (shown) => { toolbarShown = shown; toolbarSlot.hidden = !shown; o.toolbar.hidden = !shown; fit(); },
    fit,
  };
}
