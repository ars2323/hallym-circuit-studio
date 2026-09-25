# 2026-09-26 Cycles 탭: 사이클 표와 지난 사이클 보기(C-02, C-03)

- 기준: feat/cycle-view `accb9f3`
- ref-mips.circ에 tests/mips/factorial.s를 우클릭 ".s 불러오기"로 불러와 리셋 뒤 30사이클 돌렸다. 터널 다섯(clk, regWrite, aluResult, writeData, rsVal)을 오른쪽 클릭 "Add to Cycle View"로 줄에 더했다.
- 표: 열 하나가 한 사이클(사이클이 끝난 뒤 값, 상태 표시줄의 Cycle 번호와 같다). 머리는 사이클 번호, PC, 명령어(학생이 쓴 .s 원래 줄, 라벨 포함). 1비트는 반 사이클 단위 파형, 버스는 16진 값이고 앞 열과 같은 값은 옅게 보인다. 선택한 열은 옅은 파랑, 마지막 사이클 오른쪽 끝은 청록 선이다.
- 원조 2.7.1에는 이 탭이 없어 -orig 비교가 없다. 회로도 그림은 바뀌지 않았다(25c는 사이클 12의 값으로 그려진 회로도).

### 25a-cycles-full.png
- 창 전체(1600px로 줄인 그림): 마지막 사이클(30)을 보는 중. 캔버스는 화면 맞춤(25%)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c02-cycle-view/25a-cycles-full.png

### 25b-cycles-table.png
- Cycles 탭 원래 크기: 사이클 21~30, 조작 막대 "Cycle 30 / 30"(Latest Cycle 꺼짐)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c02-cycle-view/25b-cycles-table.png

### 25c-past-cycle-full.png
- 열 12를 누른 뒤 창 전체: 회로도가 사이클 12의 값, 상태 표시줄 "Cycle 12", 조작 막대의 지난 사이클 알림
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c02-cycle-view/25c-past-cycle-full.png

### 25d-past-cycle-table.png
- 25c의 Cycles 탭: 열 12가 가운데, "Cycle 12 / 30", 뒤 기록(13~30)은 그대로
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c02-cycle-view/25d-past-cycle-table.png
