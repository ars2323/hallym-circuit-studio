# 2026-09-25 스크린샷 검토 3차 반영 + 2c 첫 보고(정적 진단, Messages 탭)

- 기준 main 커밋: `3190949`
- 관련 PR: #155(3차: 단수·복수, 남은 이름 영어화) #156(정적 진단 엔진) #157(Messages 탭) #158(MIPS 부품 진단)
- 이전 촬영: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/README.md
- "확인할 것"의 [3차-n]은 3차 검토 항목 번호, [2c]는 이번 단계 새 화면이다.

### 01-first-screen.png
- 앱 첫 화면
- 확인할 것: [3차-2] 부품 검색 칸 "Search components (e.g. mux 32, register)". [2c] 상태 표시줄 맨 앞 "No messages", 캔버스 아래 Messages 탭
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-messages/01-first-screen.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/01-first-screen.png

### 01b-tree-search-mux.png
- 부품 트리 검색 "mux"
- 확인할 것: 비교(변화 없음)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-messages/01b-tree-search-mux.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/01b-tree-search-mux.png

### 04a-menu-port.png
- 포트 우클릭
- 확인할 것: [3차-1] 요약 줄 "AND #1 · input in1 · 1 bit"(단수)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-messages/04a-menu-port.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/04a-menu-port.png

### 04b-menu-gate.png
- 게이트 우클릭
- 확인할 것: [3차-1] 폭 표시 단수·복수
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-messages/04b-menu-gate.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/04b-menu-gate.png

### 04c-menu-wire.png
- 선 우클릭
- 확인할 것: [3차-1] "1 bit"
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-messages/04c-menu-wire.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/04c-menu-wire.png

### 04d-menu-empty.png
- 빈 곳 우클릭
- 확인할 것: 비교
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-messages/04d-menu-empty.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/04d-menu-empty.png

### 04e-menu-subcircuit.png
- 서브회로 우클릭
- 확인할 것: 비교
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-messages/04e-menu-subcircuit.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/04e-menu-subcircuit.png

### 05a-quick-attrs-dock-open.png
- Quick Attributes + Attributes 패널
- 확인할 것: [2c] 아래 Messages 탭이 붙은 배치
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-messages/05a-quick-attrs-dock-open.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/05a-quick-attrs-dock-open.png

### 05b-quick-attrs-crop.png
- Quick Attributes 부분
- 확인할 것: [3차-2] 아래 안내 "F2: Label"
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-messages/05b-quick-attrs-crop.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/05b-quick-attrs-crop.png

### 05c-dock-collapsed.png
- Attributes 패널 접음
- 확인할 것: 비교
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-messages/05c-dock-collapsed.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/05c-dock-collapsed.png

### 06a-palette-mux32.png
- 부품·명령 찾기 "mux 32"
- 확인할 것: [3차-2] 아래 안내 "↑↓ Select · Enter Place/Run · Alt+Enter Favorite · Esc Close"
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-messages/06a-palette-mux32.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/06a-palette-mux32.png

### 06b-palette-reset.png
- 명령 찾기 "리셋"
- 확인할 것: [3차-2] 같은 영어 안내
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-messages/06b-palette-reset.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/06b-palette-reset.png

### 11a-load-summary.png
- .s 불러오기 요약
- 확인할 것: [3차-2] "27 words .text → …", "2 words .data → …", "Instructions used: …"
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-messages/11a-load-summary.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/11a-load-summary.png

### 11b-imem.png
- Instruction Memory
- 확인할 것: 비교
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-messages/11b-imem.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/11b-imem.png

### 11c-dmem.png
- Data Memory
- 확인할 것: 비교
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-messages/11c-dmem.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/11c-dmem.png

### 11d-stack-mid-recursion.png
- Stack, 45 사이클
- 확인할 것: 비교
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-messages/11d-stack-mid-recursion.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/11d-stack-mid-recursion.png

### 11e-console.png
- Console, 45 사이클
- 확인할 것: 비교
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-messages/11e-console.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/11e-console.png

### 11f-stack-end.png
- Stack, exit 시점
- 확인할 것: 비교
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-messages/11f-stack-end.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/11f-stack-end.png

### 11g-console-end.png
- Console, exit 시점
- 확인할 것: 비교
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-messages/11g-console-end.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/11g-console-end.png

### 11h-ref-mips-after-run.png
- 참조 CPU 전체, 실행 뒤
- 확인할 것: [2c] 참조 CPU도 Messages 0건("No messages")
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-messages/11h-ref-mips-after-run.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/11h-ref-mips-after-run.png

### 14a-messages.png
- 데모를 두 곳 망가뜨림(regfile 옆 터널 이름 RegWrit, PC의 clk 터널 삭제)
- 확인할 것: [2c] 편집을 멈추면 0.7초 뒤 Messages 2건, 상태 표시줄 "2 messages"(빨강), 해당 부품에 옅은 빨간 테두리
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-messages/14a-messages.png

### 14b-messages-clicked.png
- 첫 메시지를 누름
- 확인할 것: [2c] PC가 선택되고 굵은 빨간 테두리. 목록 줄 강조
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-messages/14b-messages-clicked.png

### 14c-messages-list.png
- Messages 목록 부분
- 확인할 것: [2c] 문장은 한국어, 이름은 영어·학생 이름(main › PC(Register), RegWrit, 비슷한 이름 RegWrite). 원인 한 곳, 고치는 법은 말하지 않음
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-messages/14c-messages-list.png

### 14d-messages-pc.png
- PC 부분 150%
- 확인할 것: [2c] 누른 진단의 강조
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-messages/14d-messages-pc.png
