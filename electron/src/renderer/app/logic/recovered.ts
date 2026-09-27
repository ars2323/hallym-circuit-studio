/* What the window says after the engine died and started again (N-04,
   D-142; src/main/recovery.ts): a dialog in the Hallym MIPS ask style --
   what happened, facts only -- and a band that stays while the student
   works on.  Logic only; app.ts shows them.

   A file's name never takes a particle: names stand after a colon, in a
   list.  The simulation is not restored (a new engine starts from Reset),
   and both say so. */

import type { Recovered } from '../../../main/protocol.ts';

export interface RecoveredText {
  title: string;
  body: string;       // sentences, Korean
  detail: string;     // the facts, a line each (the dialog's mono box)
  band: string;       // one line
}

// Why a file's unsaved edits did not come back.
export const LOST_REASON: Record<Recovered['lost'][number]['reason'], string> = {
  replayFailed: '편집을 다시 적용하지 못함',
  crashedAgain: '되살리는 중에 엔진이 다시 멈춤',
  changedOnDisk: '그사이 디스크의 파일이 바뀜',
  notRecorded: '기록하지 못한 편집이 있음',
};

// Why a file could not be opened again.
export const CLOSED_REASON: Record<Recovered['closed'][number]['reason'], string> = {
  missing: '그 자리에 파일이 없음',
  openFailed: '파일을 열지 못함',
};

export const RESET = '시뮬레이션은 Reset 상태입니다';

export function recoveredText(r: Recovered, name: (fileId: string) => string): RecoveredText {
  const restored = r.restored.length;
  const edits = r.restored.reduce((n, f) => n + f.edits, 0);
  const lost = r.lost.length;
  const closed = r.closed.length;
  const any = restored + lost + closed > 0;

  const body: string[] = ['회로를 돌리는 엔진(Java)이 끝나서 다시 시작했습니다.'];
  if (!any) body.push('열려 있던 파일은 없었습니다.');
  if (restored > 0) {
    body.push(edits > 0
      ? `열려 있던 파일 ${restored}개를 다시 열고 저장하지 않은 편집 ${edits}개를 다시 적용했습니다.`
      : `열려 있던 파일 ${restored}개를 다시 열었습니다.`);
  }
  if (lost > 0) body.push(`파일 ${lost}개는 저장하지 않은 편집을 되살리지 못해 마지막으로 저장한 상태로 열었습니다.`);
  if (closed > 0) body.push(`파일 ${closed}개는 다시 열지 못해 닫았습니다.`);
  if (any) body.push('시뮬레이션은 Reset 상태로 돌아갔습니다.');

  const detail: string[] = [];
  for (const f of r.restored) detail.push(`다시 엶: ${name(f.fileId)}${f.edits > 0 ? ` · 편집 ${f.edits}개 다시 적용` : ''}`);
  for (const f of r.lost) detail.push(`저장한 상태로 엶: ${name(f.fileId)} · ${LOST_REASON[f.reason]}${f.edits > 0 ? ` · 편집 ${f.edits}개` : ''}`);
  for (const f of r.closed) detail.push(`닫음: ${name(f.fileId)} · ${CLOSED_REASON[f.reason]}`);
  if (r.crash) detail.push(`엔진: ${r.crash.how}`, ...r.crash.log);

  const names = (xs: { fileId: string }[]) => xs.map((f) => name(f.fileId)).join(', ');
  const band: string[] = [];
  if (lost > 0) band.push(`저장하지 않은 편집을 되살리지 못했습니다: ${names(r.lost)}`, '마지막으로 저장한 상태로 열었습니다');
  else band.push('엔진이 멈춰서 다시 시작했습니다');
  if (lost === 0 && restored > 0) band.push(`파일 ${restored}개를 되살렸습니다`);
  if (closed > 0) band.push(`다시 열지 못해 닫았습니다: ${names(r.closed)}`);
  if (any) band.push(RESET);

  return { title: '엔진이 멈췄다가 다시 시작했습니다', body: body.join(' '), detail: detail.join('\n'), band: band.join(' · ') };
}
