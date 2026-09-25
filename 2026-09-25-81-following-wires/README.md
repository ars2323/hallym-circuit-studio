# 2026-09-25 #81 따라오는 배선·선분 평행 이동

- 기준: 브랜치 feat/following-wires `be049bc` (PR 머지 전 촬영)
- 관련 PR: #81 작업 PR

### 15a-move-before.png
- 데모의 PC, 옮기기 전(150%)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-81-following-wires/15a-move-before.png

### 15b-move-after.png
- PC를 오른쪽 아래(20, 40)로 끈 뒤
- 확인할 것: D·Q·clk 선이 따라와 연결이 유지되는지(Messages 표시 없음). 새 선이 교차점에 끝점을 두거나 남의 포트 위를 지나지 않는지
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-81-following-wires/15b-move-after.png

### 15c-segment-before.png
- regfile RR1로 가는 rs 선, 가운데 세로 선분을 끌기 전
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-81-following-wires/15c-segment-before.png

### 15d-segment-after.png
- 가운데 세로 선분을 왼쪽으로 30 끈 뒤
- 확인할 것: 위아래 가로 다리가 늘어 따라오고, rt·rd 가로선과는 끝점 없이 교차만 하는지(연결 안 됨)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-81-following-wires/15d-segment-after.png
