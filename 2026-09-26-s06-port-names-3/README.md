# 2026-09-26 원조 부품의 포트 이름(S-06, S-07) 재촬영 2

- 기준: fix/port-name-overlay `2a58f44`
- ui-reviewer 2차(위반 0건)의 확인 필요 2건을 반영했다.
  - 옆 변 포트 이름은 부품 가운데에서 먼 쪽에 둔다: 레지스터 "en"(아래쪽 절반)은 포트 아래라 위의 D 입력선과 떨어진다(21a-pc-200, 400).
  - 낮은 배율에서 마우스를 올려도 화면 10px 이상으로 그리고, 그만큼 넉넉히 다시 그린다(21d-pc-hover-25).
- 각 장면은 같은 회로 좌표를 같은 배율로 포크와 원조 2.7.1(-orig)에서 찍었다. 캔버스 글꼴은 원조와 같은 Dialog 12다(D-066).
- S-07: 원조도 400%에서 "en0"가 값과 겹친다(21a-pc-400-orig). 원조 그림이다.
- S-06: 포크는 100%에서 원조 부품의 포트 이름을 숨기고(마우스를 올리면 보임, 21c), 200% 이상에서 부품 바깥 포트 옆에 그린다. 값과 다른 글자는 원조 자리 그대로다.

### 21a-pc-100-orig.png
- PC 레지스터 100% — 원조 2.7.1
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s06-port-names-3/21a-pc-100-orig.png

### 21a-pc-100.png
- PC 레지스터 100% — 포크
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s06-port-names-3/21a-pc-100.png

### 21a-pc-200-orig.png
- PC 레지스터 200% — 원조 2.7.1
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s06-port-names-3/21a-pc-200-orig.png

### 21a-pc-200.png
- PC 레지스터 200% — 포크
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s06-port-names-3/21a-pc-200.png

### 21a-pc-400-orig.png
- PC 레지스터 400% — 원조 2.7.1
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s06-port-names-3/21a-pc-400-orig.png

### 21a-pc-400.png
- PC 레지스터 400% — 포크
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s06-port-names-3/21a-pc-400.png

### 21b-adder-100-orig.png
- PC+4 가산기 100% — 원조 2.7.1
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s06-port-names-3/21b-adder-100-orig.png

### 21b-adder-100.png
- PC+4 가산기 100% — 포크
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s06-port-names-3/21b-adder-100.png

### 21b-adder-200-orig.png
- PC+4 가산기 200% — 원조 2.7.1
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s06-port-names-3/21b-adder-200-orig.png

### 21b-adder-200.png
- PC+4 가산기 200% — 포크
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s06-port-names-3/21b-adder-200.png

### 21b-adder-400-orig.png
- PC+4 가산기 400% — 원조 2.7.1
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s06-port-names-3/21b-adder-400-orig.png

### 21b-adder-400.png
- PC+4 가산기 400% — 포크
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s06-port-names-3/21b-adder-400.png

### 21c-adder-hover-100.png
- 100%, 가산기 왼쪽 위에 마우스를 올린 직후(마우스 오버 정보 창이 뜨기 전): "c in"·"c out"이 부품 바깥에
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s06-port-names-3/21c-adder-hover-100.png

### 21d-pc-hover-25.png
- 25%, PC 레지스터에 마우스를 올린 직후(도움말 창이 뜨기 전): "en"과 "0"이 부품 바깥에 화면 10px로
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s06-port-names-3/21d-pc-hover-25.png

