# UI 용어집: 영어 이름과 한국어 문장

**방침(사용자 확정, PLAN.md 3장 "UI 언어", D-049): 이름·명령은 영어, 설명 문장만 한국어.**

학생은 수업·교재·원조 Logisim 2.7.1에서 영어 이름을 본다. 도구가 이름을 번역하면 같은 것이 두 이름을 갖게 된다. 그래서 화면에서 무엇을 가리키는 말(이름)은 영어로 두고, 무엇을 알려 주는 문장(설명)만 한국어로 쓴다.

## 1. 어느 쪽이 영어이고 어느 쪽이 한국어인가

| 영어(이름·명령) | 한국어(설명 문장) |
| --- | --- |
| 메뉴와 메뉴 항목, 도구 모음 버튼, 탭, 상태 표시줄 글자 | 진단 메시지(2c "Messages" 탭의 문장) |
| 부품 이름, 라이브러리 이름, 라이브러리 분류(Wiring, Plexers …) | 도구 설명, 마우스 오버 설명 문장 |
| 속성 이름과 값(Data Bits, Facing, East …) | 대화 상자의 안내 문장, 오류 문장 |
| 우클릭 메뉴와 맨 위 요약 줄(`Adder · 32 bits`) | 단축키 표의 "설명" 칸 |
| 부품 몸체 제목과 상태 글자(Instruction Memory, Data Memory, Stack, Console, `-- exit --`) | 첫 실행 안내, 빈 화면 안내 |
| 검색 결과, 단축키 이름, 대화 상자 제목 | .s 불러오기 요약의 문장 |

경계에 있는 것:
- 포트 설명은 "포트 이름: 설명" 모양이다. 포트 이름은 영어, 설명은 한국어다(`Clock: 트리거가 오면 상태가 바뀝니다`).
- 입력 칸 앞 글자(`Tunnel Name:`, `Label:`)는 이름이다. 한 문장 안내(`클럭 사이클 수:`)는 설명이다.
- `-tty` 출력의 표 머리(`TOTAL` 등)는 이름이다. 채점 스크립트가 원조 출력을 읽는다(D-026).
- 검색 별칭(`Palette.ALIASES`, 예: "먹스", "리셋")은 학생이 칠 수 있는 말이라 한국어도 받는다. 화면에 보이는 결과는 영어 이름이다.

## 2. 영어 이름

원조 2.7.1이 쓰는 말은 그대로 쓴다. 새 기능 이름도 같은 문체(명사구, 제목식 대문자)로 짓는다.

### 원조 2.7.1의 이름(예)

| 영어 이름 | 문장 안에서 쓰지 않는 번역 |
| --- | --- |
| Poke Tool | 조작 도구, 찌르기 |
| Edit Tool | 편집 도구 |
| Select Tool | 선택 도구 |
| Wiring Tool | 배선 도구 |
| Text Tool | 텍스트 도구 |
| Menu Tool | 메뉴 도구 |
| Toolbar | 도구 모음, 툴바 |
| Attribute table, Attributes 패널 | 속성 표, 속성 패널 |
| Explorer pane | 탐색 창 |
| Wiring, Gates, Plexers, Arithmetic, Memory, Input/Output, Base | |
| Splitter, Pin, Probe, Tunnel, Pull Resistor, Clock, Constant | |
| Multiplexer, Demultiplexer, Decoder, Adder, Register, RAM, ROM | |
| Data Bits, Facing, Label, Label Font | |
| Simulate › Reset Simulation, Tick Once, Step Simulation, Simulation Enabled | |
| Project › Edit Circuit Appearance, Revert To Default Appearance | |

### Hallym Circuit Studio가 더한 이름

| 영어 이름 | 문장 안에서 쓰지 않는 번역 |
| --- | --- |
| Instruction Memory | 명령어 메모리 |
| Data Memory | 데이터 메모리 |
| Stack, Console | |
| Radix Probe | 다중 진법 프로브 |
| Load .s, Reload | .s 프로그램 불러오기 |
| 1 Cycle, N Cycles, Reset, Run | |
| Quick Attributes, All Attributes | 빠른 속성 |
| Find, Tunnels | |
| Show in Attribute Panel | |
| Fit to Window, Show Grid | 화면 맞춤 |
| Labels: All, Labels: Pins, Tunnels, Subcircuits, Labels: Under Pointer | |
| Edit Splitter…, Split Bits…, Take One Bit, Arm | |
| Attach Probe, Attach to Pin, Net Information…, Select Whole Net | |
| Replace Wire with Tunnels…, Tunnel Color | |
| Auto Appearance | |
| Duplicate, Undo, Redo | |
| Getting Started, Shortcuts | |

"문장 안에서 쓰지 않는 번역" 칸의 말은 한국어 설명 문장에 나오면 안 된다. 쉼표로 나누고, 빈 칸은 확인하지 않는다. `UiLanguageTest`가 원조 한국어 번들, 앱 `messages_ko.properties`, lib-mips `Text.of`의 한국어 문장을 확인한다.

## 3. 한국어 문장 규칙

- 문장 안에서 이름을 말할 때는 영어 이름 그대로 쓴다: "Instruction Memory를 오른쪽 클릭하고 "Load .s..."를 고릅니다", "Poke Tool(손 모양)을 고르고".
- 메뉴 경로는 `›`로 잇는다: `Simulate › Reset Simulation`.
- 조사는 영어 이름의 끝소리에 맞춘다(Console이, Stack을). 헷갈리면 "은(는)"처럼 둘 다 적는다.
- 회로, 선, 부품, 포트, 버스, 비트, 라벨, 터널, 서브회로 같은 일반 명사는 한국어로 쓴다. 화면의 특정 버튼·메뉴·부품 종류를 가리킬 때만 영어 이름이다.
- 진단 문장은 PLAN.md 4.4를 따른다: 원인 한 곳, 학생이 붙인 이름, 사실과 위치까지만.
- 포트 이름(`Addr`, `WriteData`, `Count`, `Load` …)과 파일 형식(`.circ`, `.s`)은 번역하지 않는다.

## 4. 문구 리소스

| 종류 | 앱(`app/src-hcs/kr/ac/hallym/hcs/app/`) | 원조(`app/resources/logisim/`) | lib-mips |
| --- | --- | --- | --- |
| 이름(영어 고정) | `names.properties` 하나. 언어별 파일 없음 | `en/*.properties` | `Text.name("…")` |
| 설명(한국어·영어) | `messages.properties`, `messages_ko.properties` | `ko/*.properties`에는 설명 키만 있다. 나머지 키는 `en`으로 넘어간다 | `Text.of("영어", "한국어")` |

`UiLanguageTest`가 확인하는 것:
- 이름 리소스(`names.properties`, `Text.name`의 인자, lib-mips 부품·속성·선택지를 한국어 설정에서 읽은 값)에 한글이 없다.
- 원조 `ko/*.properties`에는 설명 문장 키만 있다(키 끝말과 문장 모양으로 가른다. 규칙은 테스트 안에 있다).
- 한 키가 이름과 설명 양쪽에 있지 않다.
- 한국어 문장에 위 "쓰지 않는 번역"이 없다.
