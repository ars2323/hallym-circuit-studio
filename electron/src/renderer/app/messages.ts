/* The Messages panel (under the Canvas, D-143): what the engine says
   cannot work in the open file -- found from the wiring, or while the
   simulation ran (then with its cycle) -- grouped by kind with a count per
   kind, in the engine's words (logic/messages.ts).

   Choosing a message marks it and sends "show this place" (reveal.ts): the
   Canvas goes there, the Cycle View to its cycle.  The list keeps its place
   and the chosen message when the engine sends a new one (v1 D-124), and
   the keyboard focus stays where it was (the Messages tab never takes the
   focus away, Y-10).  While the circuit oscillates, Reset Simulation.

   Nothing is said with a character: a message is about a circuit that
   cannot work (CLAUDE.md 8). */

import type { DiagMessage } from '../../main/protocol.ts';
import { code, h, prose } from '../shared/dom.ts';
import type { NoticeHost } from '../shared/notice.ts';
import { groupMessages, keepChosen, messageCount, oscillating, placeLine, revealOf, sentence, type Reveal } from './logic/messages.ts';

export interface MessagesState {
  fileId: string;
  list: DiagMessage[] | null;          // null: not here yet
  circuitName(circuitId: string): string;
}

export interface MessagesPanel {
  set(state: MessagesState | null): void;
  chosen(): string | null;
}

// The empty panel says what fills it (v2 4: no empty panel without a word).
export const EMPTY = '동작할 수 없는 연결(떠 있는 입력, 짝 없는 터널, 폭이 다른 선 …)이 있으면 여기에 나옵니다. 시뮬레이션 중에 생긴 E·X 값과 발진은 그 사이클과 함께 나옵니다.';

// Hexadecimal words (addresses, ranges) in the mono font: 0 and O apart (dom.ts).
const HEX = /(0x[0-9a-fA-F]+|\b[0-9a-f]{8}(?:-[0-9a-f]{8})?\b)/;

function messageText(text: string): DocumentFragment {
  const f = document.createDocumentFragment();
  text.split(HEX).forEach((part, i) => {
    if (part === '') return;
    f.append(i % 2 === 1 ? code(part, 'name') : prose(part));
  });
  return f;
}

export function messagesPanel(o: {
  host: NoticeHost;
  tab: HTMLElement;                   // the Messages tab: its count badge
  onReveal(r: Reveal): void;
  onReset(): void;                    // Reset Simulation (an oscillation)
}): MessagesPanel {
  let state: MessagesState | null = null;
  let chosen: string | null = null;
  let shownFile: string | null = null;
  const tabName = o.tab.textContent ?? 'Messages';

  const badge = (n: number) => {
    if (n > 0) o.tab.replaceChildren(tabName, h('span', { class: 'tabcount', 'aria-label': messageCount(n) }, n.toLocaleString('en-US')));
    else o.tab.replaceChildren(tabName);
  };

  function choose(m: DiagMessage): void {
    if (!state) return;
    chosen = m.id;
    for (const b of o.host.root.querySelectorAll<HTMLElement>('.msg')) {
      const on = b.dataset.id === chosen;
      b.classList.toggle('on', on);
      b.setAttribute('aria-current', String(on));
    }
    o.onReveal(revealOf(state.fileId, m));
  }

  function render(): void {
    const list = state?.list ?? null;
    badge(list?.length ?? 0);
    if (!state || list === null) { o.host.fill(); return; }
    if (list.length === 0) {
      o.host.empty({
        title: '메시지가 없습니다',
        body: EMPTY,
      });
      return;
    }
    const st = state;
    // Keep the place: the scroll position, and the focus if a message had it.
    const scroll = o.host.root.querySelector<HTMLElement>('.msgs')?.scrollTop ?? 0;
    const focused = (document.activeElement as HTMLElement | null)?.closest?.('.msg') as HTMLElement | null;
    const focusId = focused && o.host.root.contains(focused) ? focused.dataset.id ?? null : null;
    const groups = groupMessages(list).map((g) => h('section', { class: 'msggroup', 'data-code': g.code, 'aria-label': g.name },
      h('div', { class: 'msghead' }, h('span', { class: 'gname' }, g.name), h('span', { class: 'count', title: messageCount(g.messages.length) }, String(g.messages.length))),
      h('ul', { class: 'msglist' }, ...g.messages.map((m) => {
        const on = m.id === chosen;
        const b = h('button', { type: 'button', class: `msg${on ? ' on' : ''}`, 'data-id': m.id, 'data-kind': m.kind, 'aria-current': String(on) },
          h('span', { class: 'dot', 'aria-hidden': 'true' }),
          h('span', { class: 'say' }, messageText(sentence(m))),
          h('span', { class: 'where' }, placeLine(m, st.circuitName)));
        b.addEventListener('click', () => choose(m));
        return h('li', {}, b);
      }))));
    const actions = oscillating(list)
      ? h('div', { class: 'msgactions' }, (() => {
        const r = h('button', { type: 'button', class: 'hbtn', title: 'Reset the simulation and turn it on again' }, 'Reset Simulation');
        r.addEventListener('click', () => o.onReset());
        return r;
      })())
      : null;
    const box = h('div', { class: 'msgs', role: 'region', 'aria-label': messageCount(list.length) }, ...groups, actions);
    o.host.fill(box);
    box.scrollTop = scroll;
    if (focusId) box.querySelector<HTMLElement>(`.msg[data-id="${CSS.escape(focusId)}"]`)?.focus({ preventScroll: true });
  }

  return {
    set: (next) => {
      if (next?.fileId !== shownFile) { chosen = null; shownFile = next?.fileId ?? null; }
      state = next;
      chosen = keepChosen(chosen, next?.list ?? []);
      render();
    },
    chosen: () => chosen,
  };
}
