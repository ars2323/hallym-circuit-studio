/* About (derived from Hallym MIPS v2.3.0 electron/src/renderer/app/panels/
   about.ts): the version, what it is built on, and every notice that goes
   with the program -- read from the same files the package carries
   (src/main/paths.ts LICENSES), so the two cannot drift apart.  What the
   About tab says is the caller's (src/renderer/app/app.ts). */

import { h } from './dom.ts';

export interface AboutSource {
  lines(): Promise<Node[]>;             // the About tab
  licenses(): Promise<string[]>;        // the titles, in order (the last: Electron's)
  license(index: number): Promise<string>;
  openCredits(): Promise<void>;
  creditsNote: (Node | string)[];       // under the list: where Chromium's notices are
}

export function aboutDialog(source: AboutSource): { root: HTMLDialogElement; open(): Promise<void> } {
  const dialog = h('dialog', { class: 'modal about', 'aria-label': 'About' });

  const open = async () => {
    const [lines, titles] = await Promise.all([source.lines(), source.licenses()]);
    const body = h('div', { class: 'tabbody' });
    const tabs = h('div', { class: 'tabs', role: 'tablist' });
    const pick = (i: number) => {
      [...tabs.children].forEach((t, k) => { t.classList.toggle('on', k === i); t.setAttribute('aria-selected', String(k === i)); });
      if (i === 0) {
        body.replaceChildren(...lines);
      } else {
        const list = h('div', { class: 'licenses' });
        titles.forEach((title, k) => {
          const pre = h('pre', { class: 'mono' });
          const d = h('details', {}, h('summary', {}, title), pre);
          d.addEventListener('toggle', async () => {
            if (d.open && pre.textContent === '') pre.textContent = await source.license(k);
          });
          list.append(d);
        });
        const credits = h('button', { class: 'btn small', type: 'button' }, 'Open the Chromium · Node.js notices (LICENSES.chromium.html)');
        credits.addEventListener('click', () => void source.openCredits());
        list.append(h('p', { class: 'hint' }, ...source.creditsNote), credits);
        body.replaceChildren(list);
      }
    };
    ['About', 'Licenses'].forEach((t, i) => {
      const b = h('button', { class: 'tab', type: 'button', role: 'tab' }, t);
      b.addEventListener('click', () => pick(i));
      tabs.append(b);
    });
    const close = h('button', { class: 'btn primary', type: 'button' }, 'Close');
    close.addEventListener('click', () => dialog.close());
    dialog.replaceChildren(h('h2', {}, 'About'), tabs, body, h('div', { class: 'row end' }, close));
    pick(0);
    dialog.showModal();
  };
  return { root: dialog, open };
}
