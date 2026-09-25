# 2026-09-26 Cycle View 탭 재촬영(C-02, C-03)

- 기준: feat/cycle-view `269f6a7`
- 앞 폴더(2026-09-26-c02-cycle-view) 검토 반영
  - 위반(체크리스트 10): 사람이 그린 demo-datapath로 다시 찍었다. 리셋 뒤 6사이클(프로그램 4워드, PC가 0x10이면 halt = 1). 줄은 clk·pc 터널, halt 핀, alu Result 선, regfile RD1 선을 오른쪽 클릭 "Add to Cycle View"와 같은 호출로 더했다.
  - 확인 필요 1(한 열 안의 순간이 다름): 열 c는 사이클 c다. 앞 절반 파형은 사이클을 여는 상승 에지 뒤(스텝 2c-1), 뒤 절반은 다음 상승 에지 앞(스텝 2c)이다. 머리 명령어와 버스 값도 스텝 2c다. 그래서 한 열이 한 사이클 전부를 가리키고 상승 에지는 열 경계에 온다(clk는 열마다 앞 절반 1, 뒤 절반 0). halt는 PC가 0x10인 열 4의 경계에서 1이 된다.
  - 확인 필요 2(마지막 열 파형이 절반에서 끊김): 위 정렬로 마지막 열도 두 절반이 모두 기록 안이다.
  - 확인 필요 3(캔버스가 25%라 지난 사이클 값이 안 보임): demo는 화면 맞춤이 77%다. PC 둘레를 200%로 확대한 두 장(25e 마지막 사이클, 25f 사이클 2)을 더했다.
  - 확인 필요 4(Cycles와 Add to Cycle View 두 이름): 탭 이름을 Cycle View로 바꿔 메뉴와 같게 했다. 새 이름들은 docs/GLOSSARY.md의 "Hallym Circuit Studio가 더한 이름" 표(Cycle View, Previous Cycle, Next Cycle, Latest Cycle, Add to Cycle View, Remove from Cycle View, Show Bits, Hide Bits)에 있다(이 PR 브랜치).
- 원조 2.7.1에는 이 탭이 없어 -orig 비교가 없다.

### 25a-cycles-full.png
- 창 전체(1600px로 줄인 그림): 마지막 사이클(6)을 보는 중, 캔버스 화면 맞춤(77%)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c02-cycle-view-2/25a-cycles-full.png

### 25b-cycles-table.png
- Cycle View 탭 원래 크기: 사이클 0~6, "Cycle 6 / 6"(Latest Cycle 꺼짐). 명령어는 .s가 없어 디스어셈블(프로그램 4워드 뒤는 0 = nop)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c02-cycle-view-2/25b-cycles-table.png

### 25e-canvas-latest-200.png
- 마지막 사이클(6)의 PC 둘레 200%: PC 0000 0018, Instruction Memory 00000018
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c02-cycle-view-2/25e-canvas-latest-200.png

### 25f-canvas-cycle2-200.png
- 열 2를 누른 뒤 같은 곳: PC 0000 0008, Instruction Memory 00000008: 00221824(회로도가 사이클 2의 값)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c02-cycle-view-2/25f-canvas-cycle2-200.png

### 25c-past-cycle-full.png
- 열 2를 누른 뒤 창 전체: 상태 표시줄 "Cycle 2 · PC 0x00000008", 조작 막대의 지난 사이클 알림
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c02-cycle-view-2/25c-past-cycle-full.png

### 25d-past-cycle-table.png
- 25c의 Cycle View 탭: 열 2 선택, "Cycle 2 / 6", 뒤 기록(3~6)은 그대로
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c02-cycle-view-2/25d-past-cycle-table.png
