# 2026-09-26 버스 값 칩과 활성 경로(C-08)

- 기준: feat/bus-values `b82c4de` (main `a41fa1f` 위)
- 관련 이슈: C-08 #206
- 회로: 사람이 그린 모양의 demo-datapath(생성기 DemoDatapath). 두 사이클 뒤(cycle 2, `and $v1, $at, $v0`).
- **버스 값 칩:** 폭 2 이상 버스의 가장 긴 선 옆에 지금 값(라벨 칩 규칙: 겹치면 비켜 두고 지시선). 이름 있는 버스는 이름 칩에 값을 붙인다(`pc[31:0] = 0x00000008`). 진법은 상태 표시줄 `Bus Values: Hex` 단추로 Hex → Dec → Signed → Off. 칩 자리는 가장 넓은 값으로 잡아 값이 바뀌어도 움직이지 않는다.
- **활성 경로:** MemtoReg MUX가 고른 입력(0: ALU Result) 넷 둘레에 진한 남색 띠. 사이클 뷰 도구 모음의 Active Path(기본 켬). 선 자체는 비워 값 색을 가리지 않는다.
- **함께 고친 것:** 사이클 표 빈 줄 안내 밑 흰 바탕(C-05 검토, 30a·29a 아래 가운데), 필드 색 띠가 부품 테두리를 칠하지 않음(C-07 검토, 29e). 29a·29c·29e는 C-07 장면을 다시 찍은 것이고, 필드 색만 보이게 이 장면에서는 버스 값을 끈다.

### 30a-bus-values-full.png
- 창 전체: 캔버스 버스 값 칩, 활성 경로, 상태 표시줄 Bus Values: Hex, 사이클 뷰 Active Path, 표 빈 줄 안내.
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c08-bus-values/30a-bus-values-full.png
### 30b-bus-values-canvas.png
- Hex. `0x01`·`0x02`·`0x03`은 5비트 팔(rs·rt·rd), `0x00221824`는 명령어 버스.
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c08-bus-values/30b-bus-values-canvas.png
### 30c-bus-values-signed.png
- Signed(10진 부호): `pc[31:0] = 8`, `12` 등. 칩은 지금 글자 폭으로 그린다.
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c08-bus-values/30c-bus-values-signed.png
### 30d-off.png
- Bus Values: Off, Active Path 끔: 이름 칩만 남는다.
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c08-bus-values/30d-off.png
### 29a-instruction-fields-full.png
- 사이클 표 빈 줄 안내 밑 흰 바탕(열 경계선이 글자 위를 지나지 않음).
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c08-bus-values/29a-instruction-fields-full.png
### 29c-field-colors-canvas.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c08-bus-values/29c-field-colors-canvas.png
### 29e-field-colors-400.png
- 띠가 regfile 테두리 앞에서 멈춘다(C-07 검토 확인 필요 1).
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c08-bus-values/29e-field-colors-400.png
