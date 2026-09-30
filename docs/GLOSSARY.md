# UI glossary: English names and Korean sentences

**Policy (confirmed by the user, PLAN.md chapter 3 "UI language", D-049): names and commands are English; only explanatory sentences are Korean.**

Students see English names in class, in the textbook and in original Logisim 2.7.1. If the tool translated names, the same thing would have two names. So words that point at something on the screen (names) stay English, and only sentences that tell the student something (explanations) are Korean.

## 1. Which side is English and which is Korean

| English (names and commands) | Korean (explanatory sentences) |
| --- | --- |
| Menus and menu items, toolbar buttons, tabs, status bar text | Diagnostic messages (the sentences in the 2c "Messages" tab) |
| Part names, library names, library categories (Wiring, Plexers …) | Tooltips and other hover descriptions |
| Attribute names and values (Data Bits, Facing, East …) | Guidance and error sentences in dialogs |
| Right-click menus and their top summary line (`Adder · 32 bits`) | The "Description" column of the shortcut table |
| Part body titles and state text (Instruction Memory, Data Memory, Stack, Console, `-- exit --`) | First-run guidance, empty-screen guidance |
| Search results, shortcut names, dialog titles | Sentences in the load summary |

On the border:
- A port description has the form "port name: description". The port name is English and the description is Korean (`Clock: 트리거가 오면 상태가 바뀝니다`).
- The label in front of an input field (`Tunnel Name:`, `Label:`) is a name. A one-sentence prompt (`클럭 사이클 수:`) is an explanation.
- The table header of `-tty` output (`TOTAL` and so on) is a name. Grading scripts read the original output (D-026).
- Search aliases (`Palette.ALIASES`, for example "먹스" ("mux") and "리셋" ("reset")) are words a student may type, so Korean is accepted too. The result shown is the English name.

## 2. English names

Words the original 2.7.1 uses are used as they are. Names of new features follow the same style (noun phrases, title case).

### Names from the original 2.7.1 (examples)

| English name | Translations not used in sentences (문장 안에서 쓰지 않는 번역) |
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
| Project › Add Circuit, Load Library, Unload Libraries, Move Circuit Up, Move Circuit Down, Set As Main Circuit, Remove Circuit, Edit Circuit Layout, Edit Circuit Appearance, Revert To Default Appearance | |

### Names Hallym Circuit Studio added

| English name | Translations not used in sentences (문장 안에서 쓰지 않는 번역) |
| --- | --- |
| Instruction Memory | 명령어 메모리 |
| Data Memory | 데이터 메모리 |
| Stack, Console | |
| Radix Probe | 다중 진법 프로브 |
| Load Program..., Load .hmx for …, Reload | 프로그램 불러오기, .s 프로그램 불러오기 |
| Executable image (*.hmx) | 목적 파일, 오브젝트 파일 |
| 1 Cycle, N Cycles, Reset, Run | |
| Quick Attributes, All Attributes | 빠른 속성 |
| Find, Tunnels | |
| Show in Attribute Panel | 속성 패널에서 보기 |
| Fit to Window, Show Grid | 화면 맞춤 |
| Labels: All, Labels: Pins, Tunnels, Subcircuits, Labels: Under Pointer | |
| Edit Splitter…, Split Bits…, Take One Bit, Arm | |
| Attach Probe, Attach to Pin, Net Information…, Select Whole Net | |
| Replace Wire with Tunnels…, Tunnel Color | |
| Auto Appearance | |
| Minimap | 미니맵 |
| Cycle View, Previous Cycle, Next Cycle, Latest Cycle, Add to Cycle View, Remove from Cycle View, Show Bits, Hide Bits | 사이클 뷰 |
| Run Until…, Stop, Condition, Value, Max Cycles, PC Is, Next Instruction Is, Row Changes, E or X Appears, Halt or Exit | 여기까지 실행 |
| Registers, Memory, Instruction, Cycles, Signed Decimal, Mark as PC, Unmark as PC, Mark as Register File, Unmark Register File, Register Mapping… | 레지스터 파일로 표시 |
| Signal Flow, Signal Flow on Click, Show Signal Flow, Show Signal Flow (Backward), Stop Signal Flow, Flow Speed, Slow, Normal, Fast, Active Path Only, Reduce Motion, Smooth (60 fps) | |
| Influence, Show Influence (Forward), Show Influence (Backward), Show Influence (Both), Path Between Selected, Through Registers, Clear Influence | |
| Active Path, Bus Values, Hex, Dec, Signed, Off, Colors: Values, Groups, Wire Colors | |
| Signal Group, Control, Data, Address, None, Highlight Net, Clear Net Highlight | |
| Add Area Memo…, Edit Area Memo…, Fit Area Memo to Selection, Delete Area Memo, Text, Color | |
| Duplicate, Undo, Redo | |
| Recover, Discard | |
| Undo History…, Start of History, Now, Export Image…, Format, Scale, Range, Chips, Entire Circuit, Selection Only, Label Chips and Bus Widths, Print…, Header, Rotate To Fit, Printer View, Create Submission…, Create…, Files, Not Included, Analyze Circuit, Combinational Analysis, Inputs, Outputs, Table, Expression, Minimized, Sum of Products, Product of Sums, Get Circuit Statistics, Statistics, Component, Library, Simple, Unique, Recursive | |
| Getting Started, Shortcuts | |
| Menu, Preferences, General, Keyboard, Change…, Reset All, Reset Panel Sizes, About · Licenses, Keys That Do Not Change | 설정 창, 단축키 설정 |
| More commands, More facts, Canvas, Panels, Changed, Read-only, Turn On, Try Again | 방금 바뀜 |
| Open Recent, Close, Save As…, Exit, Minimize, Maximize, Examples, Keyboard Shortcuts, Ticks Enabled, Tick Frequency, Simulation Enabled, Reset Simulation, Step Simulation, Tick Once | 최근 파일 |

The words in the "Translations not used in sentences" column must not appear in Korean explanatory sentences. The cell is split at commas, and empty cells are not checked. `UiLanguageTest` checks the original Korean bundle, the app's `messages_ko.properties` and the Korean sentences of lib-mips `Text.of`. It finds these tables by the Korean words "쓰지 않는 번역" in the header, so the header keeps them.

## 3. Rules for Korean sentences

- When a sentence names something, it uses the English name as it is: "Instruction Memory를 오른쪽 클릭하고 "Load Program..."을 고릅니다" ("Right-click Instruction Memory and choose "Load Program...""), "Poke Tool(손 모양)을 고르고" ("choose the Poke Tool (the hand)").
- In sentences, the `.hmx` that Hallym MIPS exports is called "실행 이미지" ("executable image"). It is not called an object file or `.o` (D-126).
- No particle goes right after a file name, a register, a key or a placeholder (`{0}`). Put a Korean noun between them ("`$sp` 레지스터" ("the $sp register"), "`entry` 키가" ("the entry key")) or write it like "File: lab04.s" (D-126, the executable-image error wording).
- Menu paths are joined with `›`: `Simulate › Reset Simulation`.
- A particle follows the final sound of the English name (Console이, Stack을). When unsure, write both, as in "은(는)".
- Common nouns such as circuit, wire, part, port, bus, bit, label, tunnel and subcircuit are written in Korean. Only a specific button, menu or part kind on the screen gets its English name.
- Diagnostic sentences follow PLAN.md 4.4: one cause, the names the student gave, facts and places only.
- Port names (`Addr`, `WriteData`, `Count`, `Load` …) and file formats (`.circ`, `.s`) are not translated.
- English counts distinguish singular and plural: `1 bit`, `32 bits`, `1 word`, `27 words` (MessageFormat choice, lib-mips `Text.count`).

## 4. Wording resources

| Kind | App (`app/src-hcs/kr/ac/hallym/hcs/app/`) | Original (`app/resources/logisim/`) | lib-mips |
| --- | --- | --- | --- |
| Names (English, fixed) | One `names.properties`. No per-language files | `en/*.properties` | `Text.name("…")` |
| Explanations (Korean and English) | `messages.properties`, `messages_ko.properties` | `ko/*.properties` holds only explanation keys. Other keys fall back to `en` | `Text.of("영어", "한국어")` |

What `UiLanguageTest` checks:
- The name resources (`names.properties`, the arguments of `Text.name`, and lib-mips part, attribute and option values read under the Korean locale) contain no Hangul.
- The original `ko/*.properties` holds only explanation keys (told apart by the key's ending and the text's shape; the rules are in the test).
- No key is both a name and an explanation.
- No Korean sentence uses one of the "Translations not used in sentences" above.

## 5. English terms for English documents

The repository's documents are in English (D-174). These are the English terms for concepts that had only Korean names. Every document and translation uses them. The screen keeps its Korean, so when a document quotes the screen it quotes the Korean and adds the meaning, as in `"설치가 완료되었습니다" ("Installation complete")`.

| Korean | English in documents | Note |
| --- | --- | --- |
| 논리설계 및 실험 | the logic design course | The course name on the screen (the course chip, the start card). First mention in a document: `논리설계 및 실험 (the logic design course)`, then English only |
| 컴퓨터구조 | the computer architecture course | Same rule: `컴퓨터구조 (the computer architecture course)` on first mention |
| 교과목 | course | |
| 교과목 칩 | the course chip | The chip in the title bar that shows and switches the course (D-168) |
| 시작 카드 | the start card | The card on the start screen: course → [튜토리얼 보기] ("View the tutorial") / [바로 시작] ("Start now") → [새 회로] ("New circuit") / [파일 열기] ("Open a file") |
| 실습실 PC 규칙 | the lab PC rule | Nothing is remembered between launches; the app writes nothing outside the student's files (D-118, D-152) |
| 트랙 A | track A | `hcs-mips.jar`, the MIPS part library used in original Logisim 2.7.1 |
| 트랙 B | track B | Hallym Circuit Studio itself |
| 동작하지 않는 회로 | a circuit that cannot work | What the tool reports: floating inputs, E and X values, oscillation, width conflicts … |
| 동작하지만 틀린 회로 | a working but wrong circuit | Values flow as 0 and 1 but the result is wrong; the tool does not judge it (CLAUDE.md section 2) |
| 진단 | diagnostics | Shown in the Messages tab |
| E, X | E (error) and X (unknown) values | |
| 원인 한 곳 | one cause | The diagnostic wording rule (PLAN.md 4.4) |
| 사이클 0 | cycle 0 | The first recorded step after opening or Reset |
| 실행 이미지 | executable image | A `.hmx` file exported by Hallym MIPS |
| 원조 Logisim 2.7.1 | original Logisim 2.7.1 | |
| 엔진 | the engine | The headless Java process that runs Logisim (D-134) |
| 화면 | the screen, the window | The Electron side |
| 복구 파일 | recovery file | Next to the student's saved file (D-152) |
| 알림 띠 | notice band | A thin strip at the top of the work area |
| 값 칩 | value chip | |
| 덧그림 | overlay | Signal Flow, influence, active path … drawn over the Canvas |
| 튜토리얼 | tutorial | |
| 연습 단계 | practice step | A tutorial step that advances when the student does the task |
| 사전 릴리스 | pre-release | |
| 배포 없음 | no release | A round that changed only documents or tests |
| 격리한 검사 | quarantined checks | D-166 |
| 지휘 세션 | the coordinator session | The session that assigns D numbers and edits PROGRESS (D-166) |
| 위반 / 확인 필요 | violation / to check | compat-reviewer verdicts |
| 막음 / 넘김 | blocking / passed on | ui-reviewer verdicts |
| 하람, 하리 | Haram and Hari | The university characters |
| 김학현 | Hakhyeon Kim | |
| AIAC Lab | AIAC Lab | |
| 한림대학교 | Hallym University | Screen text also uses "Hallym University", not "한림" |
