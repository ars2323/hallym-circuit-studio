# 한국어 UI 용어집

앱(포크 `app/`), MIPS 부품 라이브러리(`lib-mips/`), 문서가 모두 이 표의 말을 쓴다. 기준은 셋이다.
- 학생이 수업·교재에서 듣는 말
- 영어 원어를 그대로 읽는 말(프로브, 터널, 스플리터)
- 직역한 말은 쓰지 않는다(예: Poke → "찌르기").

**표 규칙**
- "쓰지 않는 말" 칸의 낱말은 한국어 문구에 나오면 안 된다. `GlossaryTest`가 확인한다. 쉼표로 나누고, 빈 칸은 확인하지 않는다.
- 검사 범위: 원조 번역(`app/resources/logisim/ko/`), 앱 문구(`messages_ko.properties`), `lib-mips`의 한국어 문구
- 검색 별칭(`Palette.ALIASES`, 예: "분배기"로 찾으면 스플리터가 나옴)은 학생이 칠 수 있는 말이라 검사하지 않는다.
- 포트 이름(`Addr`, `WriteData`, `Count`, `Load` …)과 파일 형식(`.circ`, `.s`)은 번역하지 않는다.

## 도구

| 영어(원조) | 한국어 | 쓰지 않는 말 |
| --- | --- | --- |
| Poke Tool | 조작 도구 | 찌르기, 찌를, 포크 도구 |
| Edit Tool | 편집 도구 |  |
| Select Tool | 선택 도구 |  |
| Wiring Tool | 배선 도구 | 와이어링 |
| Text Tool | 텍스트 도구 |  |
| Menu Tool | 메뉴 도구 |  |
| Toolbar | 도구 모음 | 툴바 |
| Explorer pane | 탐색 창 |  |
| Attribute table | 속성 표(오른쪽 속성 패널) | 특성 |
| Quick properties | 빠른 속성 창 |  |
| Search palette (Ctrl+K) | 부품·명령 찾기 |  |

## 회로와 부품

| 영어(원조) | 한국어 | 쓰지 않는 말 |
| --- | --- | --- |
| Circuit | 회로 |  |
| Subcircuit | 서브회로 | 하위 회로, 부분 회로 |
| Component | 부품 | 컴포넌트, 구성 요소 |
| Attribute | 속성 |  |
| Wire | 선 | 와이어, 전선 |
| Bus | 버스 |  |
| Net | 넷 | 네트 |
| Port | 포트 |  |
| Label | 라벨 | 레이블, 이름표 |
| Appearance | 모양 |  |
| Library | 라이브러리 |  |
| Pin | 핀 |  |
| Probe | 프로브 | 탐침, 진법 Probe |
| Tunnel | 터널 |  |
| Splitter | 스플리터 | 분할기 |
| Splitter arm | 팔 |  |
| Constant | 상수 |  |
| Clock | 클럭 | 클록 |
| Pull Resistor / pull behavior | 풀 저항 / 풀 동작 |  |
| Three-state | 3상태 |  |
| Data Bits | 데이터 비트 |  |
| Facing | 방향 |  |
| Floating | 떠 있음 |  |
| Radix | 진법 |  |
| Register | 레지스터 |  |
| Multiplexer | 멀티플렉서 |  |
| Adder | 가산기 | 덧셈기 |
| Instruction Memory | 명령어 메모리 | Instruction Memory가, Instruction Memory를 |
| Data Memory | 데이터 메모리 | Data Memory가, Data Memory를 |
| Stack | 스택 |  |
| Console | 콘솔 |  |
| Radix Probe | 다중 진법 프로브 |  |

## 시뮬레이션과 편집

| 영어(원조) | 한국어 | 쓰지 않는 말 |
| --- | --- | --- |
| Simulation | 시뮬레이션 |  |
| Reset Simulation | 시뮬레이션 리셋 |  |
| Tick | 틱(클럭 반 주기) |  |
| Cycle | 사이클(틱 두 번) |  |
| Undo | 되돌리기 | 실행 취소 |
| Redo | 다시 실행 | 다시 하기, 재실행 |
| Zoom | 확대·축소 | 줌 |
| Duplicate | 복제 |  |
| Label chip | 라벨 칩 |  |
| Tunnel color | 터널 색 |  |
| Autosave | 자동 저장 |  |
