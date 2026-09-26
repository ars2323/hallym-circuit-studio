# 2026-09-26 명령어 필드 색 재촬영(C-07)

- 기준: feat/field-colors `7692013` (main `7bd1e99` 위)
- 관련 이슈: C-07 #205 (PR #264)
- 앞 폴더(2026-09-26-c07-field-colors) 검토(위반 0, 확인 필요 3) 반영
  1. 배율(체크리스트 4): 25%(29d)와 400%(29e) 장면을 더했다. 400%에서 띠가 선보다 가늘어 거의 안 보이던 것을 고쳤다: 띠 폭은 화면 7px, 확대하면 회로 좌표 7(버스 선 3보다 넓게). 띠 끝은 잘라 포트 글자(RR1·RR2·WR)에 닿지 않고, 선이 만나는 꺾임에만 둥근 마디를 둔다.
  2. 비교(체크리스트 5): 필드 색은 원조에 없는 덧그림이라 -orig 대신, 같은 장면에서 Registers 탭을 골라 띠가 없는 모습(29f)을 29c와 나란히 둔다. 띠 밖은 같다.
  3. 사이클 표 빈 줄 안내 위의 열 경계선(C-05 검토에서 이어짐)은 다음 C-08 PR에서 고친다(안내 밑에 바탕).
- 회로: 사람이 그린 모양의 demo-datapath(생성기 DemoDatapath), 스플리터 팔 이름 op·rs·rt·rd·shamt·funct. 두 사이클 뒤 `and $v1, $at, $v0`(R 형식).

### 29a-instruction-fields-full.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c07-field-colors-2/29a-instruction-fields-full.png
### 29b-instruction-tab.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c07-field-colors-2/29b-instruction-tab.png
### 29c-field-colors-canvas.png
- 100% 근처(화면 맞춤). rs → RR1 파랑, rt → RR2 초록, rd → WR 분홍.
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c07-field-colors-2/29c-field-colors-canvas.png
### 29d-field-colors-25.png
- 25%: 스플리터에서 regfile까지 세 띠가 나란히 보인다.
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c07-field-colors-2/29d-field-colors-25.png
### 29e-field-colors-400.png
- 400%: 띠가 선 둘레에 보이고, 포트 글자 앞에서 끝난다.
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c07-field-colors-2/29e-field-colors-400.png
### 29f-no-field-colors.png
- 29c와 같은 장면에서 Registers 탭: 띠 없음(비교).
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c07-field-colors-2/29f-no-field-colors.png
