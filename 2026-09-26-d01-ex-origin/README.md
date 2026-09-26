# 2026-09-26 E·X 출처 추적, X 쓰기 감지, 메시지에서 사이클로(D-01·D-03·D-05)

- 기준: 스택 맨 위 feat/signal-groups `d6fecfe` (main `9049b79` 위, 각 PR 브랜치를 차례로 쌓은 빌드)
- 관련 이슈: D-01·D-03·D-05 #209 #211 #213 (PR #266)
- 촬영: 장면 31만 단독으로 찍었다(장면 28이 앞서 sum.s를 불러오면 처음 몇 사이클에 레지스터 1~3 쓰기가 없어 메시지가 나지 않는다 — 회로 문제가 아니라 장면 차례)
- 확인할 것:
  - 메시지가 한 줄이고 사이클·원인·경로가 있는지
  - 누르면 사이클 뷰와 원인 선택이 함께 되는지
  - 메뉴 이름 영어, 문장 한국어

### 31a-dynamic-message.png
- RegWrite 입력 핀을 3상태로 두고 몇 사이클 돌린 뒤 Messages의 동적 메시지(사이클 번호 + 원인: 입력 핀 main › RegWrite)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-d01-ex-origin/31a-dynamic-message.png

### 31b-message-row.png
- 메시지 줄 확대
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-d01-ex-origin/31b-message-row.png

### 31c-message-clicked.png
- 메시지를 누르면 사이클 뷰가 그 사이클로 가고 원인 핀을 고른다(속성 창에 Pin)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-d01-ex-origin/31c-message-clicked.png

### 31d-menu-find-origin.png
- 선 우클릭 메뉴의 Find E/X Origin
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-d01-ex-origin/31d-menu-find-origin.png

### 31e-origin-notice.png
- 상태 표시줄의 출처 한 줄
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-d01-ex-origin/31e-origin-notice.png
