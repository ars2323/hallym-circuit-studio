# 2026-09-25 칩과 선: 속성 패널 접기 뒤 초점

- 기준: fix/chips-wires `f9011a0`
- ui-reviewer 확인 필요(05c에서 부품 검색 칸에 초점 테두리)를 고친 뒤 다시 찍었다. 속성 패널을 접거나 펴면 캔버스 쪽을 떼었다 붙이면서 Swing이 초점을 검색 칸으로 넘겼다. 이제 캔버스로 되돌린다(빠른 속성 창 단축키 Alt+0-9·F2가 캔버스에 닿는다).

### 05a-quick-attrs-dock-open.png
- PC를 누름, 속성 패널 열림
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-chips-wires-3/05a-quick-attrs-dock-open.png

### 05c-dock-collapsed.png
- 속성 패널 접음. 검색 칸에 초점 테두리가 없다
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-chips-wires-3/05c-dock-collapsed.png
