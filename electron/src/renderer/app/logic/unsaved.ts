/* The question before unsaved changes would be lost (N-19, D-152): quitting
   the app (the window's close button, Alt+F4, Ctrl+Q, the PC shutting down)
   or closing a file's tab asks once for each file with unsaved changes, in
   the shared ask dialog (logic only; app.ts asks):

     Save      the normal save (a file never saved: the Save As dialog);
               a cancelled or failed save stops the quit or the close
     Discard   the changes go (and, quitting, the recovery file with them)
     Cancel    (and Esc) nothing closes

   A Korean sentence, English buttons; plain (no character): it is a
   question, not an error, but the file's changes are at stake. */

export const SAVE = 'Save';
export const DISCARD = 'Discard';
export const CANCEL = 'Cancel';

export type Leaving = 'quit' | 'close';

export interface UnsavedQuestion {
  title: string;
  file: string;
  body: string;
  ok: string;
  extra: string;
  cancel: string;
  character: false;
}

export function unsavedQuestion(name: string, leaving: Leaving): UnsavedQuestion {
  return {
    title: '저장하지 않은 변경이 있습니다',
    file: name,
    body: leaving === 'quit'
      ? '저장하지 않고 끝내면 이 파일의 변경은 사라집니다.'
      : '저장하지 않고 닫으면 이 파일의 변경은 사라집니다.',
    ok: SAVE,
    extra: DISCARD,
    cancel: CANCEL,
    character: false,
  };
}

export interface UnsavedFile { fileId: string; name: string }

/* Asks for each file with unsaved changes, in order; true when every one was
   saved or discarded (go on), false when one was cancelled or its save did
   not happen (stop). */
export async function settleUnsaved<F extends UnsavedFile>(
  list: readonly F[],
  leaving: Leaving,
  o: {
    dirty: (f: F) => Promise<boolean>;
    show: (f: F) => void;
    choose: (q: UnsavedQuestion) => Promise<'ok' | 'extra' | 'cancel' | null>;
    save: (f: F) => Promise<boolean>;
  },
): Promise<boolean> {
  for (const f of list) {
    if (!(await o.dirty(f))) continue;
    o.show(f);
    const c = await o.choose(unsavedQuestion(f.name, leaving));
    if (c === 'extra') continue;
    if (c !== 'ok' || !(await o.save(f))) return false;
  }
  return true;
}
