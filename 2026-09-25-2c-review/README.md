# 2026-09-25 2c 검토 반영(gateUndefined, 항상 보이는 표시, 빠른 속성 창)

- 기준 main 커밋: `0797924`
- 관련 PR: #160
- 이전 촬영: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-messages/README.md
- "확인할 것"의 [n]은 2c 검토 항목 번호다(1 게이트 빈 입력, 2 캔버스 표시, 3 빠른 속성 창).

### 14a-messages.png
- 데모를 두 곳 망가뜨림(regfile 옆 터널 이름 RegWrit, PC의 clk 터널 삭제), 아무것도 누르지 않은 상태
- 확인할 것: [2] PC와 RegWrit 터널에 누르지 않아도 빨간 테두리와 오른쪽 위 작은 빨간 점
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-review/14a-messages.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-messages/14a-messages.png

### 14b-messages-clicked.png
- 첫 메시지를 누름
- 확인할 것: [2] PC 테두리가 더 굵음. [3] 빠른 속성 창이 뜨지 않음(오른쪽 Attributes 패널만 선택을 보임)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-review/14b-messages-clicked.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-messages/14b-messages-clicked.png

### 14c-messages-list.png
- Messages 목록
- 확인할 것: 비교(문구 변화 없음)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-review/14c-messages-list.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-messages/14c-messages-list.png

### 14d-messages-pc.png
- PC 부분 150%, 누른 상태
- 확인할 것: [2] 굵은 테두리와 점
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-review/14d-messages-pc.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-messages/14d-messages-pc.png

### 14f-marks-25.png
- PC와 RegWrit 부분 25%
- 확인할 것: [2] 가장 작은 배율에서도 테두리와 점이 보이는지
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-review/14f-marks-25.png

### 14f-marks-100.png
- 같은 영역 100%
- 확인할 것: [2] 누른 PC(굵게)와 누르지 않은 RegWrit(보통)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-review/14f-marks-100.png

### 14f-marks-400-pc.png
- PC 400%
- 확인할 것: [2] 테두리·점 굵기가 화면 px 기준이라 커지지 않음
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-review/14f-marks-400-pc.png

### 14f-marks-400-tunnel.png
- RegWrit 터널 400%
- 확인할 것: [2] 누르지 않은 터널의 표시
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-review/14f-marks-400-tunnel.png

### 14e-gate-undefined-error.png
- gateUndefined = error 회로: 5입력 AND에 a·b만 연결
- 확인할 것: [1] Messages 한 줄 "main › AND #1의 입력 in2, in3, in4이(가) 연결되지 않았습니다.", 출력 y가 E(원조 동작). 상태 표시줄 "1 message"
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-review/14e-gate-undefined-error.png

### 14e-gate-undefined-error-crop.png
- 같은 회로 150% 부분
- 확인할 것: [1] 게이트에 빨간 테두리와 점, 출력선이 E(빨강)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-2c-review/14e-gate-undefined-error-crop.png
