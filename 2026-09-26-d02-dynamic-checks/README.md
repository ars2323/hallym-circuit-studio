# 2026-09-26 진동, MIPS 부품 값 검사(D-02·D-04·D-06)

- 기준: 스택 맨 위 feat/signal-groups `d6fecfe` (main `9049b79` 위, 각 PR 브랜치를 차례로 쌓은 빌드)
- 관련 이슈: D-02·D-04·D-06 #210 #212 #214 (PR #267)
- 확인할 것:
  - 메시지가 몸체 글자와 같은지
  - 정적 조합 루프 메시지가 진동 한 줄로 바뀌었는지(중복 없음)
  - Reset 단추 위치

### 32a-oscillation.png
- tests/circ/faults/dynamic-oscillation: 진동 메시지 한 줄, 고리(q, NAND #1) 강조, Reset Simulation 단추
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-d02-dynamic-checks/32a-oscillation.png

### 32b-oscillation-message.png
- Messages 줄과 Reset 단추 확대
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-d02-dynamic-checks/32b-oscillation-message.png

### 32c-mips-unaligned.png
- mips-unaligned: Data Memory 몸체의 빨간 글자와 같은 문구의 메시지
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-d02-dynamic-checks/32c-mips-unaligned.png

### 32d-mips-body.png
- Data Memory 몸체 확대
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-d02-dynamic-checks/32d-mips-body.png
