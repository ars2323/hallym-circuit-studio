# 테스트 안내

무엇을 어디서 시험하는지 적는다. 명령과 CI는 아래 "돌리기"에 있다. 새 기능은 모델 수준 단위 테스트를 두고, 창이 필요한 것은 Xvfb GUI 테스트로 본다(PLAN.md 11.16).

## 돌리기

| 명령 | 내용 |
| --- | --- |
| `./gradlew test` | 단위 테스트(lib-mips, app). 창 없이 돈다 |
| `xvfb-run -a ./gradlew :app:guiTest` | GUI 스모크 테스트(실제 창) |
| `bash tools/check-engine-unchanged.sh` | 엔진 패키지가 원본과 같은지(허용한 패치 1개 제외, 규칙 2.1) |
| `make -C native/hcs-asm test` | hcs-asm 어셈블 결과를 기대 JSON·QtSpim 오라클과 비교 |
| `bash tools/screenshots/run.sh <폴더> <장면…>` | 스크린샷(docs/SCREENSHOTS.md). 장면 번호는 칸으로 나눈다 |

CI(`.github/workflows/ci.yml`)는 push·PR마다 Linux에서 vendor·assets 검사, 엔진 불변, hcs-asm, `build`(단위 테스트 포함), GUI 테스트를 돌리고, Windows에서 hcs-asm을 빌드해 확인한다.

테스트는 개발자의 Logisim 환경설정을 건드리지 않는다: Gradle이 `java.util.prefs.userRoot`와 `hcs.configDir`를 `build/` 아래로 돌린다.

## 영역별

| 영역 | 테스트 |
| --- | --- |
| 엔진 회귀·저장 호환(규칙 2.1·2.3) | `ForkEngineRegressionTest`(tests/circ/*.expected를 원조 2.7.1 jar와 같은 결과로), `ForkSaveCompatTest`·`LocaleSaveCompatTest`(새 부품 없는 .circ는 원조 저장과 바이트 동일) |
| MIPS 부품(lib-mips) | `MemoryComponentsTest`, `StackRegionTest`, `ConsoleTest`, `RefMipsTest`(참조 CPU가 SPIM 결과와 같은지), `JarLibraryTest`(원조 2.7.1에서 불러오기) |
| 넷 모델·추적 | `NetlistTest`, `InfluenceTest`, `OriginTraceTest` |
| 정적 진단 | `StaticCheckTest`(정상 회로 0건, 종류마다 한 건), `MessagesPanelTest`, `DiagMarksTest` |
| 동적 진단 | `DynamicCheckTest`, `FaultCollectionTest`(tests/circ/faults 18개가 기대 메시지 한 건씩) |
| 편집기 UI | 패키지마다(`labels`, `wiring`, `props`, `find` …) 모델 테스트와 `*GuiTest` |

## 사이클 뷰(C-10)

추적표 C-10의 항목과 그것을 보는 테스트다.

| 항목 | 테스트 |
| --- | --- |
| 기록 재생 = 실제 재실행 | `RecordingTest.reconstructIsARealRerun`: 체크포인트에서 다시 만든 상태가 그 스텝의 실제 상태와 모든 넷에서 같다. `recordsEveryNetAndMatchesTheLiveValues`, `ReplayIsolationTest`(재실행이 지금 시뮬레이션·Console·메모리 등록에 새지 않는다) |
| 뒤로 가기 뒤 다시 진행 | `RecorderViewTest.viewingAPastCycleAndGoingOnFromThere`(지난 사이클에서 진행하면 그 뒤를 버리고 새로 적는다), `viewingRefMipsKeepsTheFuture`, `RecordingTest.truncateDropsTheFuture`·`pokedInputIsKeptForReplay`, `CycleViewGuiTest.cyclesTabRecordsAndGoesBack`, `viewingAPastCycleOfFactorialKeepsTheRecord` |
| Run Until 조건별 | `RunUntilTest`(PC 값, 다음 jr, exit, 줄 바뀜, 최대 사이클, E 발생, PC 글자 읽기), `CycleViewGuiTest.runUntilButtonRunsAndReports` |
| 레지스터 대응 추정 | `RegisterFileTest`(라벨 숫자·이름·위치로 추정, 수동 대응, 저장 후 다시 열기, 되돌리기, 표시 없을 때 경로별 나열), `CycleViewGuiTest.registerPanelWithAMarkedRegisterFile` |
| factorial.s로 $sp 이동·깊이·복귀 | `MachineStateTest.factorialStackDepthFollowsTheCalls`(가장 깊을 때 56바이트 = 7단 × 8, 모두 돌아오면 0, Stack 화살표·최고 수위), `CycleViewGuiTest.memoryPanelShowsTheStack`, `StackDemoTest` |
| 스크린샷 실행기는 halt에서 멈춤 | `Shots.runCycles`: 회로에 `halt` 출력 핀이 있으면 1이 되는 사이클에서 멈춘다(ref-mips는 Console Exit). exit 뒤에도 돌리면 CPU가 syscall 10 다음 코드를 실행해 Stack이 자라므로 장면 11·25~27이 쓴다. 테스트 쪽은 `RunUntil.halted`로 같은 곳에서 멈춘다(`MachineStateTest`, `DynamicCheckTest.normalCircuitsStayQuiet`) |
| 그 밖의 사이클 뷰 | `CycleModelTest`(PC·.s 원래 줄·디스어셈블), `MipsTextTest`, `ProgramReloadTest`, `FieldPathsTest`, `InstructionPanelTest`, `BusValuesTest`, `ActivePathOverlayTest`, `CycleViewGuiTest`(Console 탭·재로드·Instruction 탭·활성 경로·동적 메시지에서 사이클로) |
| 성능 | `RecordingPerformanceTest`(ref-mips 100k 스텝 캡처·재구성), `DynamicCheckTest.scanningAStepIsCheap`. 수치는 docs/PERFORMANCE.md |
