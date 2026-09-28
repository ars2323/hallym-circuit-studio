/* The Console tab under the Canvas (v1 C-09, N-16, D-147): the program's
   whole output from every Console part of the circuit on show (the part's
   body shows its last lines only), a name line between Consoles when there
   are more than one, "-- exit --" after the program's exit.  The engine
   streams it (mips.console: the text once, then what was printed since, once
   a frame); Reset empties it, as in v1.

   Output only: the Console part handles print syscalls (1, 4, 11) and exit
   (10); it has no input syscall (PLAN.md 6.9, D-147), so there is no input
   line.  No character here: this panel is beside the circuit's errors. */

import type { ConsoleUpdate } from '../../main/protocol.ts';
import { h } from '../shared/dom.ts';
import type { NoticeHost } from '../shared/notice.ts';
import { applyConsole, consoleEmpty, consoleText, type ConsoleState } from './logic/console.ts';

export interface ConsolePanel {
  update(u: ConsoleUpdate): void;          // the engine's notification or its answer to mips.console
  show(fileId: string | null): void;       // the file on show (null: none)
  drop(fileId: string): void;
  text(fileId: string): string;
}

export function consolePanel(host: NoticeHost): ConsolePanel {
  const byFile = new Map<string, ConsoleState[]>();
  const pre = h('pre', { class: 'consoletext mono', 'aria-label': 'Console output' });
  const scroll = h('div', { class: 'consolescroll' }, pre);
  let shown: string | null = null;
  let shownText: string | null = null;

  const render = () => {
    const list = shown ? byFile.get(shown) ?? [] : [];
    if (consoleEmpty(list)) {
      shownText = null;
      host.empty({
        title: '아직 출력이 없습니다',
        body: list.length ? '프로그램이 Console 부품으로 출력하면(syscall 1, 4, 11) 여기에 모두 나옵니다.'
          : '회로의 Console 부품이 출력하면 여기에 나옵니다.',
      });
      return;
    }
    const text = consoleText(list);
    if (text === shownText && host.root.firstChild === scroll) return;
    // New output stays in view unless the student has scrolled up to read.
    const atEnd = scroll.scrollTop + scroll.clientHeight >= scroll.scrollHeight - 4;
    pre.textContent = text;
    shownText = text;
    if (host.root.firstChild !== scroll) host.fill(scroll);
    if (atEnd) scroll.scrollTop = scroll.scrollHeight;
  };

  return {
    update: (u) => {
      byFile.set(u.fileId, applyConsole(byFile.get(u.fileId) ?? [], u.consoles));
      if (u.fileId === shown) render();
    },
    show: (fileId) => { shown = fileId; shownText = null; render(); },
    drop: (fileId) => { byFile.delete(fileId); if (shown === fileId) { shown = null; render(); } },
    text: (fileId) => consoleText(byFile.get(fileId) ?? []),
  };
}
