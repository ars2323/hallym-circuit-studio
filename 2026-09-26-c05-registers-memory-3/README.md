# 2026-09-26 레지스터·메모리 패널 재촬영 2(C-05, C-06)

- 기준: feat/register-panel `a673078`
- 앞 폴더(2026-09-26-c05-registers-memory-2) 검토(위반 0, 확인 필요 6) 반영
  1. 안내 문장이 칸 끝에서 잘림: 목록 위에 따로 두고 칸 폭에 맞춰 줄바꿈한다(27e, "…Mark as Register File을 고릅니다."까지).
  2. stack-demo 터널 겹침: Shifter의 상수 2와 Subtractor의 상수 0x7fffeff8을 터널 대신 짧은 선으로 곧장 이었다(27f). $sp 레지스터 양옆 터널은 포트에 붙인 것이다.
  3. 표 왼쪽 머리 칸과 열 "2" 사이 약 10px: 옆 탭 배치로 보이는 폭이 바뀐 뒤 스크롤이 열 경계에서 어긋났다. 폭이 바뀔 때마다 다시 맞추고, GUI 테스트가 보이는 영역 왼쪽 x가 열 폭의 배수인지 확인한다(27a·27f, 틈 없음).
  4. depth/used 용어: 패널 요약을 부품 몸통과 같은 말로 바꿨다: "Stack · depth 24 B · used 24 B (peak)". depth는 $sp 기준 지금 깊이, used (peak)는 Stack이 실제로 읽거나 쓴 가장 낮은 곳까지다(D-050).
  5. ref-mips 탭 이름: 스크린샷 실행기가 모든 장면 앞에 참조 회로를 열어 두는 탭이다. 이 폴더의 그림은 demo-datapath와 stack-demo만 보인다.
  6. Register Mapping 창 제목: 가상 화면에는 창 관리자가 없어 제목 표시줄이 그려지지 않는다. 제목은 names.properties의 regfile.mappingTitle = Register Mapping이다.

### 27a-registers-full.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c05-registers-memory-3/27a-registers-full.png
### 27b-registers-panel.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c05-registers-memory-3/27b-registers-panel.png
### 27c-register-mapping.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c05-registers-memory-3/27c-register-mapping.png
### 27d-stack.png
- "Stack · depth 24 B · used 24 B (peak)", $sp 화살표
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c05-registers-memory-3/27d-stack.png
### 27e-registers-unmarked.png
- 줄바꿈한 안내 문장, $sp R29
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c05-registers-memory-3/27e-registers-unmarked.png
### 27f-stack-demo-full.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c05-registers-memory-3/27f-stack-demo-full.png
