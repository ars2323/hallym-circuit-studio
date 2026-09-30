/* Load Program… and what follows it (N-16, D-147; docs/engine-api.md "mips").

     Load Program…   the main process's dialog (executable images only), then
                     the engine loads the file the way track A's menu does:
                     all or nothing.  Several memories hold a segment: the
                     window asks which (a dialog with the candidates) and
                     loads again.  A problem: the error dialog, its lines
                     "N번째 줄: what is wrong. what to do" -- nothing changed.
                     Loaded: the summary (entry, each segment's words and
                     bytes and where they went, the start values the file
                     gives, the source check, the facts).
     status bar      Program x.hmx; PC ≠ entry at cycle 0 (a fact, not a
                     message); an old separate Stack; a memory still pointing
                     to a .s, with Load .hmx… (D-141).
     band            while a reload has failed: what is on show and since
                     when, and why the file was not loaded again (Hallym MIPS's
                     error-kept band: a band over the work, not an overlay).

   The engine watches the .hmx while the app runs and loads it again when it
   changes, at Reset and when the file opens; this page only shows what the
   engine says (mips.facts, mips.reloaded).  The dialogs are the window's own
   (the shared ask dialog's look); an error has no character. */

import type { LoadChoice, LoadResult, MipsFacts, ProgramInfo, Reloaded } from '../../main/protocol.ts';
import { character, code, codeText, h, prose } from '../shared/dom.ts';
import type { CallError, LoadProgramOptions } from './api.ts';
import { commandError } from './logic/errors.ts';
import { chooseSentence, keptBand, problemLines, programFacts, summaryFacts, summaryRows } from './logic/program.ts';

export interface ProgramDeps {
  loadProgram(fileId: string, options: LoadProgramOptions): Promise<LoadResult | null>;
  call<T>(method: 'mips.facts', params: Record<string, unknown>): Promise<T>;
  note(cls: '' | 'err' | 'ok', text: string | null): void;   // the status bar's last fact
  changed(fileId: string): void;                             // facts or band changed
}

export interface Programs {
  load(fileId: string, options?: LoadProgramOptions): Promise<void>;
  refresh(fileId: string): Promise<void>;
  facts(m: MipsFacts): void;
  reloaded(r: Reloaded): void;
  drop(fileId: string): void;
  statusNodes(fileId: string): Node[];
  band(fileId: string): { text: string; title: string } | null;
  info(fileId: string): ProgramInfo | null;   // the program on the memories now (the tutorial reads it, N-18)
}

// ---- the dialogs (the shared ask dialog's shape: character left, backdrop, Esc = cancel) ----

interface ModalButton<T> { label: string; value: T; primary?: boolean }

function modal<T>(o: { title: string; file?: string; content: Node[]; buttons: ModalButton<T>[]; cancel: T; character: boolean; cls: string }): Promise<T> {
  return new Promise((answer) => {
    let result = o.cancel;
    const buttons = o.buttons.map((b) => {
      const el = h('button', { class: `btn${b.primary ? ' primary' : ''}`, type: 'button' }, b.label);
      el.addEventListener('click', () => { result = b.value; dialog.close(); });
      return el;
    });
    const dialog = h('dialog', { class: `modal ask ${o.cls}${o.character ? '' : ' plain'}`, 'aria-label': o.title },
      h('div', { class: 'askbody' }, o.character ? character('haram', 96) : null,
        h('div', { class: 'asktext' }, h('h2', {}, o.title),
          o.file ? h('p', { class: 'askfile' }, 'File: ', code(o.file)) : null,
          ...o.content,
          h('div', { class: 'row end' }, ...buttons))));
    dialog.addEventListener('close', () => {
      dialog.remove();
      document.body.classList.remove('dialog-open', 'error-dialog');
      answer(result);
    });
    document.body.append(dialog);
    document.body.classList.add('dialog-open');
    document.body.classList.toggle('error-dialog', !o.character);
    dialog.showModal();
    (buttons.find((_, i) => o.buttons[i].primary) ?? buttons[0])?.focus();
  });
}

// The summary after a load: the rows, then the facts (sentences).
function summaryDialog(r: LoadResult): Promise<boolean> {
  const s = r.summary!;
  const rows = summaryRows(s).map((row) => h('tr', { class: row.warn ? 'warn' : undefined },
    h('th', { scope: 'row' }, row.name),
    h('td', {}, row.name === 'Source' ? prose(row.value) : code(row.value), row.note ? h('div', { class: 'note' }, codeText(row.note)) : null)));
  const facts = summaryFacts(s);
  return modal({
    title: '실행 이미지를 불러왔습니다', file: r.file, cls: 'loadsummary', character: true,
    content: [
      h('p', {}, '메모리 부품에 넣고 시뮬레이션을 처음 상태로 돌렸습니다.'),
      h('table', { class: 'summary' }, h('tbody', {}, ...rows)),
      ...facts.map((f) => h('p', { class: 'fact' }, prose(f))),
    ],
    buttons: [{ label: 'OK', value: true, primary: true }], cancel: true,
  });
}

// Why nothing was loaded: the lines (line -> what -> what to do), no character.
function problemDialog(r: LoadResult): Promise<boolean> {
  return modal({
    title: '실행 이미지를 불러오지 못했습니다', file: r.file, cls: 'loaderror', character: false,
    content: [
      h('p', {}, '아무것도 바꾸지 않았습니다.'),
      h('ul', { class: 'problems' }, ...problemLines(r.problems ?? []).map((l) => h('li', {}, prose(l)))),
    ],
    buttons: [{ label: 'Close', value: false, primary: true }], cancel: false,
  });
}

// Several memories hold a segment: which one.  null: cancelled.
function chooseDialog(file: string, c: LoadChoice): Promise<string | null> {
  let picked = c.candidates[0]?.componentId ?? '';
  const options = c.candidates.map((x, i) => {
    const input = h('input', { type: 'radio', name: 'memory', value: x.componentId, checked: i === 0 });
    input.addEventListener('change', () => { if (input.checked) picked = x.componentId; });
    return h('label', { class: 'choice' }, input, code(x.name));
  });
  return modal<string | null>({
    title: '어느 메모리에 넣을까요?', file, cls: 'loadchoose', character: true,
    content: [h('p', {}, prose(chooseSentence(c.kind, c.segment))), h('div', { class: 'choices', role: 'radiogroup' }, ...options)],
    buttons: [{ label: 'Cancel', value: null }, { label: 'Load', value: '', primary: true }], cancel: null,
  }).then((v) => (v === null ? null : picked));
}

export function programs(d: ProgramDeps): Programs {
  const facts = new Map<string, MipsFacts>();
  let loading = false;

  const load = async (fileId: string, options: LoadProgramOptions = {}): Promise<void> => {
    if (loading) return;
    loading = true;
    try {
      let o: LoadProgramOptions = { ...options };
      for (let round = 0; round < 4; round++) {
        const r = await d.loadProgram(fileId, o);
        if (!r) return;                                   // the dialog was cancelled
        if (r.choose) {
          const id = await chooseDialog(r.file, r.choose);
          if (id === null) { d.note('', null); return; }
          o = { ...o, again: true, picks: { ...(o.picks ?? {}), [r.choose.kind]: id } };
          continue;
        }
        if (!r.loaded) {
          d.note('err', `불러오지 못했습니다 · ${r.file}`);
          await problemDialog(r);
          return;
        }
        d.note('ok', `불러왔습니다 · ${r.file}`);
        await refresh(fileId);
        await summaryDialog(r);
        return;
      }
    } catch (e) {
      d.note('err', commandError('Load Program', e as CallError));
    } finally {
      loading = false;
    }
  };

  const refresh = async (fileId: string): Promise<void> => {
    try {
      const m = await d.call<MipsFacts>('mips.facts', { fileId });
      facts.set(fileId, { ...m, fileId });
      d.changed(fileId);
    } catch {
      // an engine before N-16: no facts
    }
  };

  return {
    load,
    refresh,
    facts: (m) => { facts.set(m.fileId, m); d.changed(m.fileId); },
    reloaded: (r) => {
      if (r.ok) d.note('ok', `다시 불러왔습니다 · ${r.file}`);
    },
    drop: (fileId) => { facts.delete(fileId); },
    statusNodes: (fileId) => programFacts(facts.get(fileId)).map((f) => {
      const span = h('span', { class: `progfact ${f.cls}`.trim(), title: f.title },
        ...(f.program !== undefined ? ['Program ', code(f.program)] : [prose(f.text)]));
      if (f.loadHmx === undefined) return span;
      const b = h('button', { type: 'button', class: 'factbtn', title: 'Hallym MIPS에서 내보낸 .hmx 파일을 고릅니다' }, 'Load .hmx…');
      b.addEventListener('click', () => void load(fileId, { forSource: f.loadHmx }));
      return h('span', { class: 'progfact-wrap' }, span, b);
    }),
    band: (fileId) => keptBand(facts.get(fileId)?.program),
    info: (fileId) => facts.get(fileId)?.program ?? null,
  };
}
