# 2026-09-26 핀과 같은 이름의 터널(S-12) 재촬영 2

- 기준: fix/pin-tunnel-label `7097f40`
- ui-reviewer 2차 위반 1건(400% 두 장이 같은 장면이 아님)을 반영했다. 찍는 영역을 가운데에 두고 찍어 포크와 원조가 같은 크기·자리다.
- 확인 필요(포트 네모 밖에서 칩 테두리가 핀 오른쪽 테두리 바깥 줄을 덮음)도 고쳤다. 터널 색 칩은 포트가 있는 변을 따라 폭 3의 띠를 칠하지 않는다(D-070, LabelsTest.tunnelColorLeavesThePortClear).
- demo 왼쪽 제어 핀 다섯 개는 포트에 같은 이름의 터널이 붙어 있다. 포크는 핀 라벨 칩 없이 터널 이름으로 한 번만 보인다. 원조는 핀 라벨 글자와 터널 글자가 둘 다 보인다.

### 23a-control-pins-100.png / -orig
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s12-pin-tunnel-label-3/23a-control-pins-100.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s12-pin-tunnel-label-3/23a-control-pins-100-orig.png

### 23a-control-pins-200.png / -orig
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s12-pin-tunnel-label-3/23a-control-pins-200.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s12-pin-tunnel-label-3/23a-control-pins-200-orig.png

### 23a-control-pins-400.png / -orig
- RegWrite와 MemtoReg 위쪽(포트 점과 핀 테두리 전체가 보인다)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s12-pin-tunnel-label-3/23a-control-pins-400.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s12-pin-tunnel-label-3/23a-control-pins-400-orig.png
