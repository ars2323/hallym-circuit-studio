# 2026-09-26 버스 값 칩과 활성 경로 재촬영(C-08)

- 기준: feat/bus-values `82ccb7e` (main `a41fa1f` 위)
- 관련 이슈: C-08 #206 (PR #265)
- 앞 폴더(2026-09-26-c08-bus-values) 검토(위반 0, 확인 필요 6) 반영
  1. 5비트 칩이 엉뚱한 선을 가리킴 / 2. 명령어 칩 지시선이 팔 라벨에 닿음: 버스 칩의 지시선을 칩이 놓이려던 자리 가운데가 아니라 **그 버스 위의 가장 가까운 점**에서 긋는다. 칩이 버스에 붙어 있으면 긋지 않는다(30b의 `0x02`는 rt 선에서, 30f 400%도 같다).
  3. 활성 경로 띠가 Zero 칩 지시선을 덮음: 값 칩이 더해지면 배치가 바뀌어 Zero 칩이 제자리(지시선 없음)에 놓인 것이다. 30b에서 Zero 칩은 Zero 출력 옆 제자리이고 지시선이 없다.
  4. Result 칩이 띠 가장자리를 끊음: 칩은 덧그림 위에 그린다(칩 글자를 가리지 않게). 검은 선은 끊기지 않는다(칩 밑 선은 다시 그린다).
  5. 배율: 25%(30e)와 400%(30f)를 더했다.
  6. 원조 비교는 C-07과 같이 같은 앱에서 끈 장면(30d)으로 대신한다(이 PR의 원조 GUI 변경은 덧그림 호출 한 줄과 상태 표시줄 단추 한 줄). 29a 상태 표시줄 `Bus Values` 글자가 실제 상태와 어긋나던 것을 고쳤다: 진법을 코드나 다른 창에서 바꿔도 모든 창의 단추 글자가 따라간다(29a는 이 장면에서 끈 상태라 `Bus Values: Off`).

### 30a-bus-values-full.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c08-bus-values-2/30a-bus-values-full.png
### 30b-bus-values-canvas.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c08-bus-values-2/30b-bus-values-canvas.png
### 30c-bus-values-signed.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c08-bus-values-2/30c-bus-values-signed.png
### 30d-off.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c08-bus-values-2/30d-off.png
### 30e-bus-values-25.png
- 25%: 칩 글자는 최소 크기로 유지된다(라벨 칩 규칙).
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c08-bus-values-2/30e-bus-values-25.png
### 30f-bus-values-400.png
- 400%: `0x02`의 지시선이 rt 선에서 시작한다.
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c08-bus-values-2/30f-bus-values-400.png
### 29a-instruction-fields-full.png
- 상태 표시줄 `Bus Values: Off`(이 장면에서 끔)와 캔버스가 맞는다. 사이클 표 빈 줄 안내 밑 흰 바탕.
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c08-bus-values-2/29a-instruction-fields-full.png
### 29e-field-colors-400.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c08-bus-values-2/29e-field-colors-400.png
