# 2026-09-26 명령어 필드 색 재촬영 2(C-07)

- 기준: feat/field-colors `5be1d2f` (main `7bd1e99` 위)
- 관련 이슈: C-07 #205 (PR #264)
- 앞 폴더(2026-09-26-c07-field-colors-2) 검토(위반 0, 확인 필요 3) 반영
  1. 띠가 선 위에 겹쳐 선 색이 바뀜: 띠를 **선 둘레(후광)** 로 바꿨다. 50% 이상에서는 선 자체(폭 6)를 비워 두어 선의 값 색(0·1·E·X)이 그대로 보인다(29c·29e의 선은 29f와 같은 검정). 50% 미만(29d)에서는 선이 너무 가늘어 띠를 선 위에 반투명으로 칠한다.
  2. 25%에서 세 띠가 겹쳐 섞임: 띠 폭을 격자 간격(회로 좌표 10)으로 고정해 나란한 선의 띠끼리 겹치지 않는다. 29d에서 파랑·초록·분홍이 나란히 붙어 보인다(겹쳐 섞이지 않는다). 25%는 개별 글자도 읽기 어려운 배율이라 색 구분까지만 목표로 한다.
  3. 사이클 표 빈 줄 안내 위 열 경계선: 고침은 C-08 PR(#206, 브랜치 feat/bus-values, 안내 밑에 흰 바탕)에 이미 들어 있다. PR #264 본문과 PROGRESS C-08 행에도 적었다.
- 29f는 원조 2.7.1이 아니라 같은 앱에서 띠만 끈 장면이다. 이 PR의 원조 GUI 변경은 CanvasPainter의 덧그림 호출 한 줄뿐이라, 29c와 29f의 차이가 띠 영역 안에만 있다는 것이 이 PR의 그림 변화 전부다.

### 29a-instruction-fields-full.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c07-field-colors-3/29a-instruction-fields-full.png
### 29b-instruction-tab.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c07-field-colors-3/29b-instruction-tab.png
### 29c-field-colors-canvas.png
- 화면 맞춤 배율: 선은 검정 그대로, 둘레에 rs 파랑·rt 초록·rd 분홍.
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c07-field-colors-3/29c-field-colors-canvas.png
### 29d-field-colors-25.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c07-field-colors-3/29d-field-colors-25.png
### 29e-field-colors-400.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c07-field-colors-3/29e-field-colors-400.png
### 29f-no-field-colors.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c07-field-colors-3/29f-no-field-colors.png
