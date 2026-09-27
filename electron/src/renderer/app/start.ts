/* The first screen (the card: src/renderer/shared/welcome.ts): the greeting
   and two ways in; each leads to a second step on the same card.

     튜토리얼 보기  → 논리설계 및 실험 · 컴퓨터구조
     바로 시작      → 새 회로 · 파일 열기

   The two courses' tutorials are item N-18; until then each starts a new
   circuit, as 새 회로 does (D-135) -- nothing on screen says otherwise. */

import { welcome, type Welcome } from '../shared/welcome.ts';

export interface StartEvents {
  course(which: 'logic' | 'architecture'): void;
  newCircuit(): void;
  openFile(): void;
}

export const START_TITLE = '안녕하세요!';
export const START_LEAD: [string, string] = ['논리 회로와 MIPS 프로세서를 그리고,', '클럭을 한 번씩 뛰며 동작을 보는 곳입니다.'];

export function startScreen(events: StartEvents): Welcome {
  return welcome({
    title: START_TITLE,
    lead: START_LEAD,
    pose: 'haram-hari-greeting',
    first: 'first',
    steps: {
      first: [
        { label: '튜토리얼 보기', lines: ['예제를 열어', '한 단계씩 따라가 봅니다'], icon: 'circle-question-mark', main: true, go: 'tutorial' },
        { label: '바로 시작', lines: ['새 회로를 그리거나', '가진 파일을 엽니다'], icon: 'play', go: 'start' },
      ],
      tutorial: [
        { label: '논리설계 및 실험', lines: ['게이트와 선, 서브회로,', '클럭과 레지스터'], icon: 'circuit-board', main: true, onClick: () => events.course('logic') },
        { label: '컴퓨터구조', lines: ['MIPS 부품, 프로그램 불러오기,', '사이클 보기'], icon: 'cpu', onClick: () => events.course('architecture') },
      ],
      start: [
        { label: '새 회로', lines: ['빈 회로에서', '시작합니다'], icon: 'file-plus', main: true, onClick: () => events.newCircuit() },
        { label: '파일 열기', lines: ['가진 .circ 파일을', '엽니다 (Ctrl+O)'], icon: 'folder-open', onClick: () => events.openFile() },
      ],
    },
  });
}
