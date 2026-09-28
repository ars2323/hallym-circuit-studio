# v1 기능 대조표(v2.0.0 기준, N-21)

v1(Swing, `swing-final`)의 기능을 v2(Electron 화면 + Java 엔진)로 모두 옮긴다(D-132). 줄마다 v2에서 맡는 N 항목, 구현 PR, e2e를 잇는다. **모든 줄에 PR과 테스트가 있어야 v2.0.0을 태그한다.** 빼는 기능은 이유를 DECISIONS에 적고 여기 비고에 번호를 단다(기본은 전부 옮김).

- 원천: docs/PROGRESS.md의 S·W·P·C·D·E·V·X·Y 항목과, 그 이전 단계(PLAN.md 11장)의 기본 편집기 기능(B-, 이 표에서 붙인 번호).
- "과정"은 v1의 검토·릴리스 절차라 옮길 기능이 없는 줄이다.
- S-·W- 줄 가운데 그림 규칙(칩이 선을 가리지 않음, 포트 이름 과밀 없음 등)은 v2 캔버스(N-05)의 규칙으로 옮기고 e2e로 지킨다.
- 기능을 어떤 키·마우스로 쓰는지(원조 2.7.1 도구 동작 포함)는 `docs/interaction-parity.md`(N-08, I- 줄)가 줄마다 적는다.

## 기본 편집기 기능(PLAN 11장)

| 번호 | 기능 | v2 항목 | v2 PR | e2e | 비고 |
| --- | --- | --- | --- | --- | --- |
| B-01 | 여러 파일 탭, 탭 간 라이브러리 반영(PLAN 11.1) | N-11 |  |  |  |
| B-02 | 확대·축소(Ctrl+스크롤, Ctrl+±), 화면 맞춤(Ctrl+0), 배율 표시(11.2) | N-05 | #425 | canvas.e2e "zoom and pan" (Ctrl+휠 포인터 기준, Ctrl±, Ctrl+0, 25–400 %, 상태 표시줄 배율) |  |
| B-03 | 우클릭 메뉴(부품·선·빈 곳·서브회로, 요약 줄) | N-10 |  |  |  |
| B-04 | Quick Attributes(빠른 속성 창)과 속성 표 | N-10 |  |  |  |
| B-05 | 검색 팔레트(Ctrl+K), 부품 목록 검색 | N-12 | #433 | find.e2e "the search palette: …", "a letter typed on the Canvas…", "Components search: …"; unit search.test; real-engine.e2e "…finding and placing (N-12)" | D-150. 명령·부품·이 파일 회로·보는 회로의 터널, 뒤 숫자(`tool-args.ts`는 엔진 `ToolArgsTest`가 v1 규칙으로 확인), 글자 순서 일치(3글자부터). 즐겨찾기·최근은 이번 실행에만(실습실 규칙) |
| B-06 | 도구 모음·상태 표시줄·도구 조작(Edit·Poke·Wire·Text…) | N-17·N-08 |  |  |  |
| B-07 | 라벨 칩, 터널 색 칩, 포트 이름, 마우스 오버 정보 | N-05 | #425 | canvas.e2e "hover, selection…", "the drawing rules"; unit canvas-labels |  |
| B-08 | 터널 이동(짝 터널로), 찾기(Ctrl+F) | N-12 | #433 | find.e2e "Find (Ctrl+F): …", "Tunnels: … each press the next tunnel…"; 엔진 `FindTest` | D-150. 엔진 `find.query`(v1 NameIndex + 핀·부품 이름). 터널 우클릭 "Go to Next" 메뉴 항목은 N-10이 Tunnels 칸과 같은 `revealPart`를 쓴다 |
| B-09 | 넷 강조(Highlight Net), 넷 정보 | N-15 |  |  |  |
| B-10 | 정적 진단과 Messages 탭(정상 회로 0건) | N-13 |  | messages.e2e.ts "a broken circuit…", "nothing to say…"; 엔진 `DiagTest.normalCircuitsHaveNoMessagesBeforeAndAfterCycles` | D-143. 정상 회로 0건은 열 때와 6사이클 뒤 모두 |
| B-11 | 따라오는 선(SafeMove), 선 한 토막 끌기 | N-08 |  |  |  |
| B-12 | 서브회로 인스턴스 안내와 포트 변경 영향 알림 | N-11 |  |  |  |
| B-13 | 자동 저장·복구 파일 | N-19 |  |  |  |
| B-14 | Splitter 편집기(범위 입력, R/I/J 프리셋, 팔 라벨) | N-12 | #433 | find.e2e "the Splitter editor: …", "Split Bits …"; unit splitter-spec.test(엔진 해석과 같음); 엔진 `ExtEditTest.theSplitterEditorsIntentsGiveTheGoldenOfScene14` | D-150. `edit.splitterEdit`·`edit.splitterSplit`(v1 SplitterEdits·WireGuard), 여는 곳은 검색 창의 Edit Splitter…와 `hcs:edit-splitter` 사건(우클릭 메뉴는 N-10) |
| B-15 | MIPS 부품: Instruction·Data Memory, Stack, Console, Radix Probe | N-05·N-16 | #425 | canvas.e2e "values after ticks"(Instruction Memory 몸체 줄); unit canvas-registry "the MIPS bodies" |  |
| B-16 | 실행 이미지(.hmx) 불러오기와 요약(.s 불러오기는 D-141에서 없앰: 옛 .s 경로는 사실 `assemblySource`) | N-16 | #428 | `program.e2e.ts` "Load Program…: executable images only…", "a file that cannot be loaded…", "several Instruction Memories…", "PC ≠ entry at cycle 0…", "a memory that points to a .s…" · `real-engine.e2e.ts` "Load Program puts data.hmx into ref-mips…" · 엔진 `ProgramsTest` | D-147. 트랙 A와 같은 `ProgramLoader` 길(`mips.load`), 불러오면 처음으로. 부품 우클릭 메뉴(I-98)는 N-10이 같은 `api.loadProgram`을 부른다 |
| B-17 | hcs-mips.jar 복사 알림, 새 파일에서 Hallym MIPS 부품 바로 사용 | N-21 |  |  |  |
| B-18 | Mark as PC, Register Mapping | N-14 | #423 | `cycle.e2e.ts` "Mark as PC from the Registers panel; the register file and its mapping" · 엔진 `RecordTest.markAsPcRegisterFileAndMappingAreSavedAsV1Did` | 캔버스 우클릭 메뉴(N-10)도 같은 `record.markPc` |
| B-19 | Create Submission, 그림 내보내기(SVG·PDF·고해상도 PNG) | N-21 |  |  |  |
| B-20 | About, 예제 메뉴(Help › Examples) | N-20·N-17 |  |  |  |

## v1 항목(PROGRESS)

| 번호 | 기능 | v2 항목 | v2 PR | e2e | 비고 |
| --- | --- | --- | --- | --- | --- |
| S-01 | 칩이 선을 가림 | N-05 | #425 | canvas.e2e "the drawing rules"(칩이 선·칩 위에 없음); 못 피한 칩 아래 선은 칩 위에 다시 그림(canvas.ts drawChips) |  |
| S-02 | 팔 라벨과 선 겹침 | N-05 | #425 | unit canvas-labels "splitter arms" |  |
| S-03 | 따라온 선의 군더더기 | N-08 |  |  |  |
| S-04 | 끌기 직후 빠른 속성 창이 칩을 가림 | N-10 |  |  |  |
| S-05 | 출력 핀 라벨 칩이 선 위에 겹침 | N-05 | #425 | unit canvas-labels "a label chip … moves off a wire"; canvas.e2e "the drawing rules" |  |
| S-06 | 포트 이름 덧그림 과밀 | N-05 | #425 | unit canvas-labels "port names"(바깥, 선 비킴, 칩과 안 겹침, 몸체 글자 중복 없음) |  |
| S-07 | Register 값 표시 겹침 | N-05 | #425 | unit canvas-labels "a 32-bit register's value is a chip" |  |
| S-08 | 기본 모양 서브회로의 포트 이름 | N-05 | #425 | unit canvas-labels "port names"(기본 모양만) |  |
| S-09 | 찾기 결과의 내부 포트 이름 | N-12 | #433 | 엔진 `FindTest.placesNameTheAttachedPortReadablyNeverAnInternalName`; unit find-panels.test "Find: places are named by the port…" | D-150. v1 `NameIndex.place` 그대로 |
| S-10 | 화면 맞춤 여백 | N-05 | #425 | canvas.e2e "zoom and pan"(맞춤 가운데·여백); unit canvas-scene "zoom and pan" |  |
| S-11 | 왼쪽 패널 빈 공간 | N-17 |  |  |  |
| S-12 | 제어 핀 라벨 중복 | N-05 | #425 | unit canvas-labels "a pin whose port has a tunnel of its own name" |  |
| S-13 | 400% 굵기 | N-05 | #425 | unit canvas-draw "widths on screen"(25·100·400 %) |  |
| S-20 | 회귀 확인: 원조 도구 모음·탐색기 아이콘 줄 숨김, 위쪽 네 줄 | N-17 |  |  |  |
| S-21 | 회귀 확인: 배율 표시 하나와 실제 배율 동기화 | N-05 | #425 | canvas.e2e "zoom and pan"(상태 표시줄 배율 하나) |  |
| S-22 | 회귀 확인: 스플리터 원조 "0-7" 표시와 팔 라벨 이중 표시 없음 | N-12 | #433 | unit canvas-labels "an arm the student named… (S-22: no original "0-7" beside it)" | D-150. 스플리터 몸체는 비트 번호를 쓰지 않고 팔 칩만 |
| S-23 | 회귀 확인: MIPS 부품 포트 이름 안쪽 14px, 콘솔 출력 영역 | N-05 | #425 | unit canvas-registry "the MIPS bodies"(포트 이름 안쪽 14); canvas-geometry |  |
| S-24 | 회귀 확인: 터널 색 12색, 가까운 다른 이름은 다른 색 | N-05 | #425 | unit canvas-labels "tunnel colours"(v1 해시·12색·가까운 이름) |  |
| S-25 | 회귀 확인: 우클릭 메뉴 순서와 요약 줄 단수·복수 | N-10 |  |  |  |
| S-26 | 회귀 확인: UI 언어(D-049) | N-20 |  |  |  |
| S-27 | 회귀 확인: Stack은 used N B (peak)만 | N-14 | #423 | `cycle.e2e.ts` "Registers, Memory and Instruction follow the cycle" (스택 머리 `peak`) · `panels.test.ts` | Memory 패널 스택 머리. 부품 몸체 글은 N-05 |
| S-28 | 회귀 확인: 찾기 결과 묶음과 위치 표시 | N-12 | #433 | find.e2e "Find (Ctrl+F): groups with their places…"; 엔진 `FindTest.sameNamesInOnePlaceAreOneGroupWithTheirPlacesInOrder`, `theOrderIsTheSameInEveryOpening` | D-150. 같은 자리의 부품 차례를 정해 v1의 HashSet 차례 흔들림을 없앰 |
| S-29 | 회귀 확인: 메시지 클릭 뒤 속성 패널·빠른 속성 창·캔버스 표시 | N-13 |  | messages.e2e.ts "choosing a message…" | D-143. 메시지를 누르면 `hcs:reveal`(회로·인스턴스·부품·선·넷·자리·사이클)과 회로 탭. 캔버스 표시·고르기는 N-05/N-10이 이 사건을 받아 한다(후속) |
| S-30 | 회귀 확인: gateUndefined=error일 때만 빈 게이트 입력 알림 | N-13 |  | 엔진 `DiagTest.emptyGateInputsOnlyWhenTheProjectSaysError` | D-143. v1 StaticCheck 그대로 |
| W-01 | 결정적 길 찾기 | N-08 |  |  |  |
| W-02 | 따라온 선 정리 단계 | N-08 |  |  |  |
| W-03 | 묶음 재배선 | N-08 |  |  |  |
| W-04 | 연결점과 넷(#82) | N-05 | #425 | unit canvas-scene "wires: the engine's dots and every T, jumps"; unit canvas-draw |  |
| W-05 | 새 선 A.4 검사기 통일 | N-08 |  |  |  |
| P-01 | 영향 경로(#83) | N-15 |  |  |  |
| P-07 | Signal Flow 애니메이션(추가 지시, P-01 다음) | N-15 |  |  |  |
| P-02 | 서브회로 인스턴스 안내(#84) | N-11 |  |  |  |
| P-03 | 탭 간 라이브러리(#85) | N-11 |  |  |  |
| P-04 | 서브회로 포트 순서 끌어 바꾸기 | N-11 |  |  |  |
| P-05 | 다른 .circ에서 서브회로 가져오기 | N-11 |  |  |  |
| P-06 | 나란히 보기·창 분리·탭 복원 | N-11 |  |  |  |
| C-01 | 기록 엔진 | N-14 | #423 | `cycle.e2e.ts` "the table follows the clock" · 엔진 `RecordTest.theRecordingKeepsEveryCycleUnderLoad` | v1 `Recorder`·`Recording` 그대로(D-144) |
| C-02 | 사이클 표 | N-14 | #423 | `cycle.e2e.ts` "the table follows the clock", "rows: a signal added from the engine…" | 줄 더하기는 캔버스 우클릭(N-05/N-10)이 `record.addRow`로 |
| C-03 | 열 클릭과 뒤로 가기 | N-14 | #423 | `cycle.e2e.ts` "the table follows the clock; a column shows a past cycle; Latest comes back" · 엔진 `RecordTest.aPastCycleIsShownAndTheRunGoesOnFromThere` |  |
| C-04 | Run Until | N-14 | #423 | `cycle.e2e.ts` "Run Until: the condition in the window's dialog…" · 엔진 `RecordTest.runUntilStopsAtTheCycleOfItsCondition`·`runUntilStopsAtTheSameCycleUnderLoad` |  |
| C-05 | 레지스터 패널 | N-14 | #423 | `cycle.e2e.ts` "Registers, Memory and Instruction follow the cycle on show" · `real-engine.e2e.ts` (data.regs) · 엔진 `RecordTest.theRegistersAtExitAreTheOracles` | Hallym MIPS Registers 패널 |
| C-06 | 메모리 패널(#98) | N-14 | #423 | `cycle.e2e.ts` "Registers, Memory and Instruction…" · `real-engine.e2e.ts` · 엔진 `RecordTest.theMemoryTableShowsTheLoadedDataAndTheStack` | Hallym MIPS Data 탭 한 표(D-140) |
| C-07 | 명령어 필드 색 | N-14 | #423 | `cycle.e2e.ts` "Registers, Memory and Instruction…" (Instruction 필드) · 엔진 `RecordTest.theInstructionIsSplitIntoHallymMipsFields`·`fieldPathsFollowTheNamedSplitterArms` | 캔버스 선의 필드 색 띠는 N-15(`record.fieldPaths` 데이터) |
| C-08 | 버스 값 칩과 활성 경로 | N-15 |  |  |  |
| C-09 | Console 탭과 자동 재로드(.hmx. .s 자동 재로드는 D-141에서 없앰) | N-16 | #428 | `program.e2e.ts` "the Console tab…", "a reload that fails keeps the program on show…" · 엔진 `ProgramsTest.aFailedLoadOrReloadKeepsTheLoadedProgramAndTheSimulation`, `anExportedAgainImageLoadsWhenTheFileOpensAndAtReset`, `theConsoleStreamsTheProgramOutputAndResetClearsIt` | D-147. 감시는 앱이 도는 동안만, 실패하면 올라가 있던 것 그대로(띠). Console은 출력만(부품에 입력 syscall 없음) |
| C-10 | 사이클 뷰 테스트 | N-14 | #423 | `cycle.e2e.ts` 7개 · `messages.e2e.ts` · `real-engine.e2e.ts` · 엔진 `RecordTest` 20개·`RunUntilTicksTest` 4개·`OpenSaveParityTest`(Cycle View 요청) |  |
| D-01 | E·X 출처 추적 | N-13 |  | 엔진 `DiagTest.traceOriginFollowsAnXBackToTheInputPin` | D-143. `trace.origin` API. 선 우클릭 "Find E/X Origin" 메뉴는 캔버스 메뉴(N-10)가 이 API를 부른다(후속). E 발생 메시지(`E_APPEARED`)는 원인 한 곳을 이미 담는다 |
| D-02 | 진동 | N-13 |  | messages.e2e.ts "an oscillation…"; 엔진 `DiagTest.oscillationReplacesTheStaticLoopWithItsLoop` | D-143. 고리의 부품·선·넷, Messages의 Reset Simulation(Reset 뒤 다시 켬) |
| D-03 | X 기록 감지 | N-13 |  | messages.e2e.ts "the clock runs…"; 엔진 `DiagTest.dynamicMessagesComeWithTheirCycleAndGoAtReset` | D-143 |
| D-04 | MIPS 부품 값 의존 검사(#41) | N-13 |  | 엔진 `DiagTest.everyFaultCircuitGivesOneMessageInBothLanguages`(mips-* 5개) | D-143. lib-mips 몸체 글자를 v2 문구 두 벌로(`DiagTextTest`) |
| D-05 | 메시지 클릭과 사이클 뷰 | N-13·N-14 | #423 | messages.e2e.ts "the clock runs…"(reveal의 cycle, Cycle View 탭과 그 사이클) | D-143 사건에 사이클을 싣고, 창이 그 사건으로 Cycle View를 앞으로 가져와 `record.pin {cycle}`로 그 사이클을 본다(D-144 결정 9) |
| D-06 | 동적 고장 회로 모음 | N-13 |  | 엔진 `DiagTest.everyFaultCircuitGivesOneMessageInBothLanguages` | D-143. v1 모음 19개 + 가까운 이름 3개(`static-tunnel-near-*`), v2 문구 골든 `messages.v2.*.expected` |
| E-01 | N개 복제 | N-21 |  |  |  |
| E-02 | 정렬·같은 간격, 선택 필터 | N-21 |  |  |  |
| E-03 | 버스 폭 표시와 선 색 범례 | N-05 | #425 | canvas.e2e "demo-datapath is drawn"(범례 = 화면 선 색, 버스 폭 끄기); unit canvas-scene·canvas-labels "bus widths" |  |
| E-04 | 신호 그룹 색 | N-15 |  |  |  |
| E-05 | Undo History | N-21 |  |  |  |
| E-06 | Create Submission | N-21 |  |  |  |
| E-07 | Export Image | N-21 |  |  |  |
| E-08 | 미니맵과 영역 메모 | N-12·N-15 | #433 | find.e2e "the Minimap: …"(미니맵) | D-150. 미니맵은 N-12, 영역 메모는 N-15에 남음 |
| E-09 | 단축키 설정 창 | N-21 |  |  |  |
| E-10 | 첫 실행 튜토리얼 | N-18 |  |  |  |
| E-11 | About 창 | N-20 |  |  |  |
| E-12 | 앱 아이콘과 창 제목 | N-17 |  |  |  |
| V-01 | 새 파일에서도 Hallym MIPS가 보이고 바로 쓰임 | N-12 | #433 | find.e2e "Components: … Hallym MIPS before the file has it…", "a part dragged … puts the library in the file"; real-engine.e2e "…finding and placing (N-12)" | D-150. 엔진 `model.library`의 `pending`(D-096) |
| V-02 | 진단 문구 정확성(E 원인 종류, 내부 포트 이름 숨김) | N-13 |  | 엔진 `DiagTest.everyFaultCircuitGivesOneMessageInBothLanguages`, `DiagTextTest` | D-143. 내부 포트 이름 없음, "충돌"은 충돌에만, 한국어 틀에서 이름 뒤 조사 없음 |
| V-03 | 메시지를 누르면 원인이 사이클 표에 | N-14 | #423 | messages.e2e.ts "the clock runs…"(임시 줄 `MemWrite`, 그 사이클 테두리, Reset이면 걷힘) · 엔진 `RecordTest.resetStartsTheRecordingAgainAndTakesThePinnedRowsAway` (`record.pin`) · `cycle.test.ts` `pinGone` | 원인(`location`)과 E·X가 보인 자리(`appeared`), 메시지가 사라지면 `record.unpin`(D-114) |
| V-04 | 활성 경로는 가지만 칠함 | N-15 |  |  |  |
| V-05 | 같은 이름 파일 탭 구분 | N-11 |  |  |  |
| V-06 | Signal Flow 터널 호가 부품·라벨을 피함 | N-15 |  |  |  |
| V-07 | 빈 캔버스 안내와 예제 메뉴 | N-17 |  |  |  |
| V-08 | 상태 표시줄 PC·Mark as PC, Tunnels 외톨이 표시 | N-14·N-12 | #423, #433 | `cycle.e2e.ts` "the table follows the clock…"(상태 표시줄 PC), "Mark as PC…" · 엔진 `RecordTest.theHeadOfTheTableAndTheStatusBarReadTheDatapath`; find.e2e "Tunnels: … a lone one in amber…"(외톨이 표시) | D-144(상태 표시줄 PC·Mark as PC, N-14), D-150(Tunnels 외톨이 표시, N-12) |
| V-09 | 스크린샷 실행기 위생과 데모 값 | 과정 |  |  | v1 검토·릴리스 과정이라 옮길 기능이 아님 |
| V-10 | v1.0.1 공개 릴리스 | 과정 |  |  | v1 검토·릴리스 과정이라 옮길 기능이 아님 |
| X-01 | 첫 실행 창 크기, 포크 전용 창 설정 | N-19·N-17 |  |  |  |
| X-02 | 도구 모음 넘침(Icons Only 자동 → » 메뉴) | N-17 |  |  |  |
| X-03 | 좁은 창의 패널 비율(캔버스 최소 폭) | N-17 |  |  |  |
| X-04 | Registers PC 줄 이름·안내 조건·칩과 강조 선 간격 | N-14 | #423 | `cycle.e2e.ts` "Mark as PC…", "a narrow window…"(안내) · 엔진 `RecordTest.theRegisterRowsAgreeWithV1MachineState` | 칩과 강조 선 간격은 N-05/N-15 |
| X-05 | windows-smoke 보강·Xvfb 첫 실행 장면·v1.0.2 릴리스 | 과정 |  |  | v1 검토·릴리스 과정이라 옮길 기능이 아님 |
| Y-01 | 세로 공간 배분(캔버스 높이 ≥ 50%, 아래 칸·왼쪽 칸 줄이기·접기, 배지) | N-17 |  |  |  |
| Y-02 | 사이클 표 폭(≥ 3열, Registers 칸 좁히기·접기, 이름 열 상한) | N-14 | #423 | `cycle.e2e.ts` "a circuit with no clock run yet…; a narrow window puts the table in a Cycles tab" (1920·1280·960 폭) · `cycle.test.ts` "widths" |  |
| Y-03 | 고정 줄 수명 | N-14 | #423 | 엔진 `RecordTest.resetStartsTheRecordingAgainAndTakesThePinnedRowsAway` | 메시지가 사라지면 N-13이 `record.unpin` |
| Y-04 | 진짜 첫 실행(장면 48, windows-smoke 튜토리얼) | N-18 |  |  |  |
| Y-05 | 작은 것(빈 Attributes 칸, 아이콘만 모드 글자 단추) | N-10·N-17 |  |  |  |
| Y-06 | 확인과 릴리스(review-shots v103, v1.0.3, needs-human) | 과정 |  |  | v1 검토·릴리스 과정이라 옮길 기능이 아님 |
| Y-07 | MSI 배포 중단(zip만), setup exe 결정(v1.1.0, #334) | N-23 |  |  |  |
| Y-08 | 틱 누락 확인(N Cycles 고침, Run Until·기록·연속 실행은 해당 없음) | N-07 |  |  |  |
| Y-10 | 메시지를 누르면 초점이 검색 칸으로 옮겨 가던 것 | N-13 |  | messages.e2e.ts "choosing a message…"(초점이 메시지에 남음, Tab·Enter) | D-143 |
| Y-09 | 촬영·검토 절차(장면마다 새 JVM, 병렬, 같은 코드면 같은 픽셀, 바뀐 것만 검토, core·feature 세트) | 과정 |  |  | v1 검토·릴리스 과정이라 옮길 기능이 아님 |
