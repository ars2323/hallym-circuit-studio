# 2026-09-26 Run Until 재촬영(C-04)

- 기준: feat/run-until `ff111fd`
- 앞 폴더(2026-09-26-c04-run-until) 검토 반영
  - 확인 필요 1(한국어 문장의 "Cycle"): 알림 문장을 "사이클 4에서 멈췄습니다: PC 0x00000010"으로 바꿨다. 상태 표시줄의 이름 "Cycle 4"는 영어 그대로다.
  - 확인 필요 2(대화 상자 경계와 제목): 대화 상자 범위만 잘랐다. 촬영 환경(가상 화면)에는 창 관리자가 없어 제목 표시줄이 그려지지 않는다. 제목은 영어 이름 "Run Until"이다(names.properties runUntil.title).
  - 확인 필요 3(이동 뒤 창 전체): 26d를 더했다. 캔버스 PC가 0000 0010, halt 1, 상태 표시줄 "Cycle 4 · PC 0x00000010"과 알림, 빠른 속성 창은 없다.
- demo-datapath(사람이 그린 회로)를 리셋하고 줄 셋(pc, clk, halt)을 더한 뒤 Run Until…에서 "PC Is" 0x10.

### 26a-run-until-dialog.png
- Run Until 창(대화 상자 범위만)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c04-run-until-2/26a-run-until-dialog.png

### 26b-run-until-stopped.png
- 멈춘 뒤 Cycle View 탭: 사이클 0~4, "Cycle 4 / 4"
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c04-run-until-2/26b-run-until-stopped.png

### 26c-status-notice.png
- 상태 표시줄: "사이클 4에서 멈췄습니다: PC 0x00000010"
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c04-run-until-2/26c-status-notice.png

### 26d-full-window.png
- 멈춘 뒤 창 전체(1600px로 줄인 그림)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c04-run-until-2/26d-full-window.png
