# 2026-09-26 명령어 필드 색과 Instruction 탭(C-07)

- 기준: feat/field-colors `c2e11bf` (main `7bd1e99` 위)
- 관련 이슈: C-07 #205
- 회로: 사람이 그린 모양의 demo-datapath(생성기 DemoDatapath). 명령어 워드를 스플리터로 나누고 팔 이름을 op, rs, rt, rd, shamt, funct로 붙였다(스플리터 편집기, D-041). 두 사이클 뒤 세 번째 명령어 `and $v1, $at, $v0`(R 형식)를 본다.
- 필드 색(Tokens.FIELD, 색약 친화): op 주황 #D55E00, rs 파랑 #0072B2, rt 초록 #009E73, rd 분홍 #CC79A7, shamt 황토 #B8860B, funct 하늘 #3A9AD9, imm 갈색 #8C6D31, addr 보라 #6A3D9A.
- 캔버스 띠는 Instruction 탭이 보이는 동안만 그린다. 팔의 선(터널 포함, 첫 부품 입력까지)만 칠하고 레지스터 파일을 지나 번지지 않는다. op·shamt·funct 팔은 이 회로에서 짧은 선만 있다.
- 사이클 표 빈 줄 안내 위를 열 경계선이 지나는 것(C-05 검토 확인 필요 1)은 다음 C-08 PR에서 고친다.

### 29a-instruction-fields-full.png
- 창 전체. 오른쪽 Instruction 탭, 캔버스의 rs → RR1(파랑), rt → RR2(초록), rd → WR(분홍) 띠.
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c07-field-colors/29a-instruction-fields-full.png
### 29b-instruction-tab.png
- 디스어셈블과 형식(R-type), 워드와 PC, 필드마다 비트 범위·비트·이름·값(rs·rt·rd는 레지스터 이름도).
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c07-field-colors/29b-instruction-tab.png
### 29c-field-colors-canvas.png
- 캔버스만.
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c07-field-colors/29c-field-colors-canvas.png
