# 2026-09-26 서브회로 인스턴스 안내(P-02)

- 기준: feat/instance-banner `8c83925`
- 관련 이슈: P-02 #194

### 19a-standalone-banner.png
- 탐색기에서 regfile을 따로 열었을 때: 캔버스 위 띠(설명 한국어, "Go to Instance in main" 영어). 값은 main과 따로라 x로 보인다
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p02-instance-banner/19a-standalone-banner.png

### 19b-pin-preview.png
- 이어진 입력 핀 RR1을 고르면 같은 띠에 "이 핀을 지우거나 옮기면 인스턴스 1개에서 연결 1개가 끊길 수 있습니다"
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p02-instance-banner/19b-pin-preview.png

### 19c-pin-tool-preview.png
- 핀 도구를 들면 핀을 더할 때 모양이 바뀔 인스턴스 수와 이어진 포트 수
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p02-instance-banner/19c-pin-tool-preview.png

### 19d-running-instance.png
- Go to Instance in main 뒤: main 안의 실행 중 인스턴스(경로 main › regfile, 값이 보인다), 띠 없음
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p02-instance-banner/19d-running-instance.png

### 19e-cut-connection-notice.png
- 이어진 핀 RR1을 지운 뒤 상태 표시줄 알림 "regfile의 핀을 바꿔 인스턴스 연결 1개가 끊겼습니다."(되돌려 둠). 데모 regfile은 사용자 모양이라 핀을 더해도 포트가 밀리지 않는다
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p02-instance-banner/19e-cut-connection-notice.png
