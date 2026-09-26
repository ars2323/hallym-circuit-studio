# 2026-09-26 버스 값 칩과 활성 경로 재촬영 2(C-08)

- 기준: feat/bus-values `2fa5187` (main `a41fa1f` 위)
- 관련 이슈: C-08 #206 (PR #265)
- 앞 폴더(2026-09-26-c08-bus-values-2) 검토(위반 1, 확인 필요 1) 반영
  - **위반 1(rt 칩 지시선이 rs 선을 가로지름):** 배치가 이제 지시선을 따진다. 버스 칩의 지시선(그 버스 위 가장 가까운 점 → 칩)이 다른 선·부품을 가로지르는 자리는 쓰지 않는다. rs·rt·rd가 촘촘히 나란한 스플리터–regfile 사이에는 rt 칩을 둘 그런 자리가 없어, **rt 값 칩은 빠진다**(다른 선 위에 얹거나 이웃 버스를 가리키게 두지 않는다). rt 값은 마우스를 올리면 보인다. rs `0x01`, rd `0x03`은 제 선 옆에 지시선 없이 있다(30b, 30f).
  - **확인 필요 1(원조 비교):** 앞과 같이 같은 앱에서 끈 장면(30d)으로 대신한다. 이 PR의 원조 GUI 변경은 CanvasPainter 덧그림 호출 한 줄과 상태 표시줄 단추 한 줄이라, 30c와 30d의 차이가 칩·지시선·띠 자리에만 있으면 그림 변화 전부를 보인 것이다.

### 30a-bus-values-full.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c08-bus-values-3/30a-bus-values-full.png
### 30b-bus-values-canvas.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c08-bus-values-3/30b-bus-values-canvas.png
### 30c-bus-values-signed.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c08-bus-values-3/30c-bus-values-signed.png
### 30d-off.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c08-bus-values-3/30d-off.png
### 30e-bus-values-25.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c08-bus-values-3/30e-bus-values-25.png
### 30f-bus-values-400.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c08-bus-values-3/30f-bus-values-400.png
### 29a-instruction-fields-full.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c08-bus-values-3/29a-instruction-fields-full.png
