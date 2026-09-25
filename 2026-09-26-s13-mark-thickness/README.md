# 2026-09-26 진단 표시 굵기(S-13)

- 기준: fix/mark-thickness `382908a`
- 진단 표시(부품 테두리·점·선 덧칠)를 화면 픽셀에 맞춰 그린다. 누르지 않은 항목 테두리 2px·선 3px, 누른 항목 테두리 4px·선 6px이 25·100·400%에서 꽉 찬 픽셀로 보인다(DiagMarksTest가 픽셀 수를 잰다). 원조 2.7.1에는 진단 표시가 없어 -orig 비교가 없다.

### 14f-marks-400-pc.png
- 400%: PC 레지스터 둘레 2px 테두리와 오른쪽 위 점(흐림 없음)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s13-mark-thickness/14f-marks-400-pc.png

### 14f-marks-400-tunnel.png
- 400%: 이름이 틀린 터널 둘레
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s13-mark-thickness/14f-marks-400-tunnel.png

### 14f-marks-100.png, 14f-marks-25.png
- 100%·25%: 같은 굵기
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s13-mark-thickness/14f-marks-100.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s13-mark-thickness/14f-marks-25.png

### 14b-messages-clicked.png
- 메시지를 눌러 강조(누른 항목 4px, 선 6px)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s13-mark-thickness/14b-messages-clicked.png
