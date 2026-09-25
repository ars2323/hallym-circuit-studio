# 2026-09-26 원점 이동 한도(S-10 후속)

- 기준: fix/origin-cap `c982ea0`
- P-07 최종 검토의 확인 필요(18o 왼쪽 빈 띠) 반영. 장면 18은 배율을 25% → 400% → 100%로 원조 배율 조절(ZoomModel)로 바꾼 뒤 창 전체를 찍는다. 원조 배율 조절이 보던 가운데를 지키려고 두는 원점 이동은 이제 회로를 가운데 두는 만큼까지만이다. 100%의 demo 회로는 보이는 영역보다 넓으므로 원점 이동이 0이다.
- 비교(고치기 전): https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p07-signal-flow-6/18o-full-window.png
- 원조 2.7.1에는 원점 이동이 없다(늘 0). 이 장면의 원조 모습은 원점 0인 지금 그림과 같은 자리다.

### 18m-pc-25.png
- 25%: 회로 전체가 보이는 영역보다 작아 가운데 둔다(원점 이동 있음, 한도 안)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s10-origin-cap/18m-pc-25.png

### 18n-pc-400.png
- 400%: PC 둘레
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s10-origin-cap/18n-pc-400.png

### 18o-full-window.png
- 다시 100%: 캔버스 왼쪽에 빈 띠가 없다. 회로 왼쪽 끝(제어 핀)이 원조처럼 회로 좌표 그대로의 자리에 온다
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s10-origin-cap/18o-full-window.png
