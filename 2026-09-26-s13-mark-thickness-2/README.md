# 2026-09-26 진단 표시 굵기(S-13) 재촬영

- 기준: fix/mark-thickness `6bdb689`
- ui-reviewer 1차 위반 1건(14b에서 3px·1px로 잼)은 촬영 방식 때문이었다. 14b는 창 전체(1920×1080)를 1600px 폭으로 줄인 그림이라 4px이 약 3.3px, 2px이 약 1.7px로 줄어든다. 굵기는 원래 크기 자르기에서 잰다.
  - 새 14g: 화면 맞춤 배율(약 77%)에서 누른 PC 테두리를 원래 크기로 잘랐다. 가운데 줄에서 꽉 찬 빨간 픽셀은 좌우 모두 4px이다.
  - DiagMarksTest가 25·50·76.5·100·150·400%와 반 픽셀 이동에서 2·4px(선 3·6px)과 자리를 잰다.
- 1차 README가 14f-marks-400-pc를 "2px"라고 적은 것은 틀렸다. 14f의 PC는 메시지를 눌러 고른 항목이라 4px이고, 이름이 틀린 터널(RegWrit)은 누르지 않은 항목이라 2px이다.
- 원조 2.7.1에는 진단 표시가 없어 -orig 비교가 없다. 이 장면에는 선 덧칠이 없는 진단(연결 안 된 클럭, 짝 없는 터널)만 있다. 선 굵기는 테스트로 잰다.

### 14g-focused-fit-zoom-native.png
- 화면 맞춤 배율, 원래 크기: 누른 PC 테두리 4px
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s13-mark-thickness-2/14g-focused-fit-zoom-native.png

### 14f-marks-400-pc.png
- 400%: 누른 PC 테두리 4px과 오른쪽 위 점
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s13-mark-thickness-2/14f-marks-400-pc.png

### 14f-marks-400-tunnel.png
- 400%: 누르지 않은 터널 테두리 2px
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s13-mark-thickness-2/14f-marks-400-tunnel.png

### 14f-marks-100.png, 14f-marks-25.png
- 100%·25%: 같은 굵기(4px·2px)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s13-mark-thickness-2/14f-marks-100.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s13-mark-thickness-2/14f-marks-25.png

### 14b-messages-clicked.png
- 창 전체(1600px로 줄인 그림): 메시지를 눌러 PC를 고른 모습. 굵기 측정용이 아니다
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s13-mark-thickness-2/14b-messages-clicked.png
