# 2026-09-26 핀과 같은 이름의 터널(S-12) 재촬영

- 기준: fix/pin-tunnel-label `b6a5e18`
- ui-reviewer 1차 위반 1건(터널 색 칩이 핀과 맞닿은 포트 점과 핀 오른쪽 테두리를 덮음)을 반영했다. 터널 색 칩은 터널 끝(포트) 둘레 반 폭 4의 네모를 칠하지 않는다(D-070, LabelsTest.tunnelColorLeavesThePortClear).
- 확인 필요 반영: 100·200·400% 모두 찍었다(원조 비교 포함). 색 칩과 원래 터널 윤곽이 함께 보이는 것은 터널 색 칩의 설계다. 원조 터널 그림 위에 반투명 색과 원색 테두리를 얹는다(#128).
- demo 왼쪽 제어 핀 다섯 개는 포트에 같은 이름의 터널이 붙어 있다. 포크는 핀 라벨 칩 없이 터널 이름으로 한 번만 보인다. 원조는 핀 라벨 글자와 터널 글자가 둘 다 보인다.

### 23a-control-pins-100.png / -orig
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s12-pin-tunnel-label-2/23a-control-pins-100.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s12-pin-tunnel-label-2/23a-control-pins-100-orig.png

### 23a-control-pins-200.png / -orig
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s12-pin-tunnel-label-2/23a-control-pins-200.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s12-pin-tunnel-label-2/23a-control-pins-200-orig.png

### 23a-control-pins-400.png / -orig
- RegWrite, MemtoReg 두 핀(포트 점과 핀 테두리가 보인다)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s12-pin-tunnel-label-2/23a-control-pins-400.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s12-pin-tunnel-label-2/23a-control-pins-400-orig.png
