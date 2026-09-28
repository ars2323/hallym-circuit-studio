/* The question when a file has a recovery file beside it (N-19, D-152;
   src/main/recovery-files.ts): the app ended before those edits were saved.
   Facts only -- there are unsaved edits and when they were written -- and
   what the two answers do, the names of the buttons standing on their own
   (no particle after a name):

     Recover   the recovery file's content, opened in the file's place as
               unsaved edits (Ctrl+S writes the file; the recovery file then goes)
     Discard   the recovery file removed, the file opened as saved
     Esc       neither: nothing opened, the recovery file left (asked again
               the next time the file is opened)

   No character: it follows an abnormal end (D-135 14).  Logic only; app.ts
   shows it (shared/ask.ts choose()). */

import type { RecoveryAsk } from '../../../main/protocol.ts';
import type { Opened } from '../api.ts';

export const RECOVER = 'Recover';
export const DISCARD = 'Discard';

const two = (n: number) => String(n).padStart(2, '0');

// "2026-09-28 14:05", this PC's local time.
export function stamp(ms: number): string {
  const d = new Date(ms);
  return `${d.getFullYear()}-${two(d.getMonth() + 1)}-${two(d.getDate())} ${two(d.getHours())}:${two(d.getMinutes())}`;
}

export interface RecoveryQuestion {
  title: string;
  file: string;
  body: string;
  detail: string;
  ok: string;
  cancel: string;
  character: false;
}

export function recoveryQuestion(a: RecoveryAsk['ask']): RecoveryQuestion {
  return {
    title: '저장하지 않은 편집이 있습니다',
    file: a.name,
    body: `마지막으로 저장한 뒤의 편집이 복구 파일에 남아 있습니다(${stamp(a.modified)}). `
      + '불러오면 저장하지 않은 편집으로 열리고, 버리면 복구 파일을 지운 뒤 저장한 파일을 엽니다.',
    detail: `복구 파일: ${a.recovery}`,
    ok: RECOVER,
    cancel: DISCARD,
    character: false,
  };
}

// The status bar's fact after Recover.
export const recoveredNote = (name: string): string => `저장하지 않은 편집을 불러왔습니다 · ${name}`;

// An open's answer: a file, or first the question (then the chosen open; Esc: nothing).
export async function answerRecovery(
  r: Opened | RecoveryAsk | null,
  choose: (q: RecoveryQuestion) => Promise<'ok' | 'cancel' | null>,
  openRecovery: (id: string, choice: 'recover' | 'discard' | null) => Promise<Opened | null>,
): Promise<Opened | null> {
  if (!r || !('ask' in r)) return r;
  const c = await choose(recoveryQuestion(r.ask));
  return openRecovery(r.ask.id, c === 'ok' ? 'recover' : c === 'cancel' ? 'discard' : null);
}
