# 2026-09-26 레지스터·메모리 패널(C-05, C-06)

- 기준: feat/register-panel `a0595ee`
- Cycle View 탭 오른쪽 Registers | Memory 탭. 둘 다 보고 있는 사이클 값이다.
- 회로
  - 레지스터 파일 표시와 대응: 사람이 그린 demo-datapath의 regfile(레지스터 $1~$3)을 "Mark as Register File"로 표시하고 리셋 뒤 6사이클 돌렸다. demo의 프로그램은 레지스터 값이 모두 0이라 바뀌는 것은 PC뿐이다.
  - 스택: 스택은 jal·sw를 실행하는 CPU가 있어야 쌓인다. demo-datapath는 명령어를 실행하지 않는 데이터패스 조각이라, 참조 CPU ref-mips에 tests/mips/factorial.s를 불러 가장 깊을 때(fact(0)의 첫 bne)를 찍었다. ref-mips는 레지스터 파일을 표시하지 않아 모든 레지스터를 나열하는 모양도 함께 보인다.
- 원조 2.7.1에는 이 탭이 없어 -orig 비교가 없다. 캔버스 그림은 바뀌지 않았다.

### 27a-registers-full.png
- demo 창 전체(1600px로 줄인 그림): 아래 Cycle View 탭 오른쪽에 Registers 탭
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c05-registers-memory/27a-registers-full.png

### 27b-registers-panel.png
- Registers 탭 원래 크기: Special(PC), Return values($v0 R2, $v1 R3), Arguments … 역할별 묶음. 값은 16진·10진·2진 한 줄(2진은 4비트씩). 바뀐 PC 줄은 청록 글자와 옅은 청록 바탕. 대응할 부품이 없는 번호는 —
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c05-registers-memory/27b-registers-panel.png

### 27c-register-mapping.png
- regfile 오른쪽 클릭 "Register Mapping…" 창: 번호마다 레지스터 부품(라벨과 자리). 처음 값은 라벨 숫자로 짐작한 대응($at=$1, $v0=$2, $v1=$3)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c05-registers-memory/27c-register-mapping.png

### 27d-stack-deepest.png
- ref-mips factorial이 가장 깊을 때 Memory 탭: Stack(높은 주소가 위), $sp 화살표가 0x7fffefc4 칸, 저장된 $ra(0x00400058)와 $a0(6…1)가 번갈아. $sp 칸이 보이게 스크롤돼 머리("깊이 56바이트 · 최고 수위 56바이트")는 위로 올라가 있다
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c05-registers-memory/27d-stack-deepest.png

### 27e-registers-unmarked.png
- 같은 때 Registers 탭(레지스터 파일 표시 없음): 맨 위 "$sp 0x7fffefc4 · 스택 깊이 56바이트", 안내 문장, 모든 Register 나열(라벨 $1~$31은 번호 R1~R31)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c05-registers-memory/27e-registers-unmarked.png

### 27f-ref-mips-full.png
- ref-mips 창 전체(참고)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-c05-registers-memory/27f-ref-mips-full.png
