# 2026-09-26 레지스터·메모리 패널 재촬영(C-05, C-06)

- 기준: feat/register-panel `79c1fba`
- 앞 폴더(2026-09-26-c05-registers-memory) 검토 반영
  - 위반(체크리스트 10, ref-mips): 스택 장면을 작은 회로 stack-demo(tests/circ/stack-demo.circ)로 다시 찍었다. 카운터 → ×4 → 0x7FFFEFF8에서 빼기 → 레지스터 $sp, 같은 주소의 Stack 칸에 count를 쓴다. 부품을 흐름대로 왼쪽에서 오른쪽에 두고 이름 붙인 터널로 이었다. 6사이클 뒤다. ref-mips는 더 쓰지 않는다.
  - 확인 필요 1(요약 줄이 한국어): "$sp 0x7fffefe4 · stack depth 24 B", "Stack · depth 24 B · peak 24 B"로 영어 이름·값 모양이 됐다. 안내 문장만 한국어다.
  - 확인 필요 2(상태 표시줄의 "사이클 43에서 멈췄습니다"): C-04(#261)의 Run Until 알림이다. 상태 표시줄 알림 자리는 원래 설명 문장(한국어)을 보이는 곳이고(#81), #261 검토에서 "사이클"로 바로잡아 통과했다. 이번 그림에는 없다.
  - 확인 필요 3(빈 영역)
    - 나열할 때 이름 칸을 가장 긴 이름에 맞췄다.
    - Register Mapping 창은 이름 칸을 글자 폭에 붙이고 선택 상자가 나머지를 채운다.
    - Memory 목록의 오른쪽 여백은 한 줄 값(주소, 16진, 10진)이 짧아서 생긴 것이다. 탭 폭은 사용자가 나누개로 바꿀 수 있다.
  - 확인 필요 4(Stack 머리가 스크롤로 안 보임): 깊이·최고 수위 요약을 목록 위에 고정했다(27d 맨 위).
  - 확인 필요 5(창 제목): 가상 화면에는 창 관리자가 없어 제목 표시줄이 그려지지 않는다. 제목은 영어 "Register Mapping"(names.properties regfile.mappingTitle)이다.
  - 확인 필요 6(표 왼쪽 잘린 열 조각): 마지막 사이클을 따라갈 때 표의 왼쪽 끝을 열 경계에 맞춘다(끝에 필요한 만큼 빈칸). 27a·27f에 조각이 없다.

### 27a-registers-full.png
- demo 창 전체: regfile을 표시하고 6사이클 뒤, 오른쪽 Registers 탭
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c05-registers-memory-2/27a-registers-full.png

### 27b-registers-panel.png
- Registers 탭 원래 크기(역할별 묶음, 16진·10진·2진 한 줄, 바뀐 PC 줄 청록)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c05-registers-memory-2/27b-registers-panel.png

### 27c-register-mapping.png
- regfile 오른쪽 클릭 "Register Mapping…" 창
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c05-registers-memory-2/27c-register-mapping.png

### 27d-stack.png
- stack-demo 6사이클 뒤 Memory 탭: 고정 요약 "Stack · depth 24 B · peak 24 B", 높은 주소가 위, $sp 화살표가 0x7fffefe4 칸
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c05-registers-memory-2/27d-stack.png

### 27e-registers-unmarked.png
- stack-demo의 Registers 탭(레지스터 파일 표시 없음): "$sp 0x7fffefe4 · stack depth 24 B", 안내 문장, 라벨 $sp → R29
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c05-registers-memory-2/27e-registers-unmarked.png

### 27f-stack-demo-full.png
- stack-demo 창 전체(화면 맞춤)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c05-registers-memory-2/27f-stack-demo-full.png
