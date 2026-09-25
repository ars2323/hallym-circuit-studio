# 2026-09-26 Run Until(C-04)

- 기준: feat/run-until `db340ac`
- 사람이 그린 demo-datapath를 리셋하고 줄 셋(pc, clk, halt)을 더한 뒤 Cycle View 탭의 Run Until…을 눌렀다. 조건 "PC Is", 값 0x10. PC는 사이클마다 4씩 오르므로 사이클 4에서 멈춘다.
- 원조 2.7.1에는 이 기능이 없어 -orig 비교가 없다. 캔버스 그림은 바뀌지 않았다.

### 26a-run-until-dialog.png
- Run Until 창: 안내 문장(한국어), Condition(PC Is, Next Instruction Is, Row Changes, E or X Appears, Halt or Exit), Value와 도움말, Max Cycles(기본 10,000)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c04-run-until/26a-run-until-dialog.png

### 26b-run-until-stopped.png
- 멈춘 뒤 Cycle View: 사이클 0~4, "Cycle 4 / 4", 단추가 다시 Run Until…. halt가 사이클 4에서 1
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c04-run-until/26b-run-until-stopped.png

### 26c-status-notice.png
- 상태 표시줄 한 줄 알림: "Cycle 4에서 멈췄습니다: PC 0x00000010"
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c04-run-until/26c-status-notice.png
