# 2026-09-26 원조 부품의 포트 이름(S-06, S-07) 재촬영

- 기준: fix/port-name-overlay `0e3fb34`
- ui-reviewer 1차 위반 2건(포크 캔버스의 값 글자가 원조보다 넓어 Register 값이 테두리에 붙고 상수 바탕이 가산기 테두리를 덮음)을 반영했다. 원인은 포크 캔버스가 FlatLaf 글꼴(Pretendard)을 물려받은 것이다. 캔버스 글꼴을 원조(Metal)와 같은 Dialog 12로 두었다(D-066).
- 각 장면은 같은 회로 좌표를 같은 배율로 포크와 원조 2.7.1(-orig)에서 찍었다. 이미지는 배율 그대로의 화면 픽셀이다(100%는 작다).
- S-07: 원조도 400%에서 "en0"가 값과 겹친다(21a-pc-400-orig). 원조 그림이다.
- S-06: 포크는 100%에서 원조 부품의 포트 이름을 숨기고(마우스를 올리면 보임, 21c), 200% 이상에서 부품 바깥 포트 옆에 그린다. 값과 다른 글자는 원조 자리 그대로다.

### 21a-pc-100-orig.png
- PC 레지스터 100% — 원조 2.7.1
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s06-port-names-2/21a-pc-100-orig.png

### 21a-pc-100.png
- PC 레지스터 100% — 포크
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s06-port-names-2/21a-pc-100.png

### 21a-pc-200-orig.png
- PC 레지스터 200% — 원조 2.7.1
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s06-port-names-2/21a-pc-200-orig.png

### 21a-pc-200.png
- PC 레지스터 200% — 포크
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s06-port-names-2/21a-pc-200.png

### 21a-pc-400-orig.png
- PC 레지스터 400% — 원조 2.7.1
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s06-port-names-2/21a-pc-400-orig.png

### 21a-pc-400.png
- PC 레지스터 400% — 포크
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s06-port-names-2/21a-pc-400.png

### 21b-adder-100-orig.png
- PC+4 가산기 100% — 원조 2.7.1
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s06-port-names-2/21b-adder-100-orig.png

### 21b-adder-100.png
- PC+4 가산기 100% — 포크
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s06-port-names-2/21b-adder-100.png

### 21b-adder-200-orig.png
- PC+4 가산기 200% — 원조 2.7.1
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s06-port-names-2/21b-adder-200-orig.png

### 21b-adder-200.png
- PC+4 가산기 200% — 포크
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s06-port-names-2/21b-adder-200.png

### 21b-adder-400-orig.png
- PC+4 가산기 400% — 원조 2.7.1
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s06-port-names-2/21b-adder-400-orig.png

### 21b-adder-400.png
- PC+4 가산기 400% — 포크
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s06-port-names-2/21b-adder-400.png

### 21c-adder-hover-100.png
- 100%, 가산기 왼쪽 위에 마우스를 올린 직후(마우스 오버 정보 창이 뜨기 전): "c in"·"c out"이 부품 바깥에
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s06-port-names-2/21c-adder-hover-100.png

