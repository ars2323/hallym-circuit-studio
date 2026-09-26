# 2026-09-26 Console 탭과 .s 자동 재로드 재촬영(C-09)

- 기준: feat/console-reload `541dab9` (main `608bb7a` 위)
- 관련 이슈: C-09 #207 (PR #263)
- 앞 폴더(2026-09-26-c09-console-reload) 검토(위반 0, 확인 필요 1) 반영: Adder·Comparator·MUX 입력에서 이웃 포트의 터널끼리 겹치던 것을, 상수를 터널 대신 짧은 선으로 곧장 이어 없앴다(stack-demo와 같은 방식). 남은 터널은 포트마다 하나다.
- 회로: `tests/circ/console-demo.circ`. 사람이 그린 모양의 작은 회로이고, 생성기 `ConsoleDemo`가 만든다(demo-datapath, stack-demo와 같은 방식). 사이클마다 'A'+count를 print_char로 찍고, count 6에서 exit한다. 28a는 exit 뒤에도 사이클을 몇 번 더 진행한 상태다(Console은 exit 뒤 출력하지 않는다).

### 28a-console-full.png
- 창 전체. 아래 Console 탭에 "ABCDEF"와 "-- exit --".
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c09-console-reload-2/28a-console-full.png
### 28b-console-tab.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c09-console-reload-2/28b-console-tab.png
### 28c-reload-notice.png
- demo-datapath에 불러온 .s(임시 복사본 reload-demo.s)를 고쳐 저장한 뒤의 상태 표시줄 한 줄 알림. 모달 창 없음(GUI 테스트 consoleTabAndReloadWatcher가 알림과 창 없음을 본다).
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c09-console-reload-2/28c-reload-notice.png
