/* A question in the window's own dialog (derived from Hallym MIPS v2.3.0
   electron/src/renderer/app/panels/ask.ts), not the operating system's
   message box, which looks like another program.  Haram on the left, the
   question and the buttons on the right, the same place and size every
   time.  Its backdrop darkens everything behind it, and while it is up only
   its buttons can be reached (showModal: the rest of the page is inert).
   Esc is the cancel button -- the safe side; the other answer is only ever
   given by its button.  A click outside the dialog does nothing at all.

   A file's name is never part of the sentence (no particle after a name:
   "lab04.circ 은" reads wrong whatever the name); it stands on a line of
   its own, "File: lab04.circ".

   An error (`character: false`: the engine could not start, a file could
   not be opened) has no character, and while it is up none is on screen at
   all (body.error-dialog, shared.css): the university's characters never
   stand next to an error.  With no `cancel`, it has one button.

   The buttons are names, in English (Close, Cancel, Try Again: one name
   for one command everywhere); the title and the sentences are Korean. */

import { character, code, h, prose } from './dom.ts';

export interface Question {
  title: string;
  file?: string;      // the file the question is about
  body: string | Node;
  detail?: string;    // facts under the sentence (what was tried), in the mono font
  ok: string;
  cancel?: string | null;   // null: no cancel button (Esc still closes, as cancel)
  danger?: boolean;   // the ok button discards something
  character?: boolean;      // default true; false for errors
}

export function ask(q: Question): Promise<boolean> {
  return new Promise((answer) => {
    const ok = h('button', { class: `btn ${q.danger ? 'danger' : 'primary'}`, type: 'button' }, q.ok);
    const cancel = q.cancel === null ? null : h('button', { class: 'btn', type: 'button' }, q.cancel ?? 'Cancel');
    const withCharacter = q.character !== false;
    const dialog = h('dialog', { class: `modal ask${withCharacter ? '' : ' plain'}`, 'aria-label': q.title },
      h('div', { class: 'askbody' }, withCharacter ? character('haram', 96) : null,
        h('div', { class: 'asktext' }, h('h2', {}, q.title),
          q.file ? h('p', { class: 'askfile' }, 'File: ', code(q.file)) : null, h('p', {}, prose(q.body)),
          q.detail ? h('pre', { class: 'askdetail mono' }, q.detail) : null,
          h('div', { class: 'row end' }, cancel, ok))));
    let result = false;
    ok.addEventListener('click', () => { result = true; dialog.close(); });
    cancel?.addEventListener('click', () => dialog.close());
    dialog.addEventListener('close', () => {
      dialog.remove();
      document.body.classList.remove('dialog-open', 'error-dialog');
      answer(result);
    });
    document.body.append(dialog);
    document.body.classList.add('dialog-open');
    document.body.classList.toggle('error-dialog', !withCharacter);
    dialog.showModal();
    ok.focus();
  });
}
