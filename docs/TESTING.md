# 테스트 안내

무엇을 어디서 시험하는지 적는다. 명령과 CI는 아래 "돌리기"에 있다. 새 기능은 모델 수준 단위 테스트를 두고, 창이 필요한 것은 화면(electron/)의 Playwright e2e(Xvfb)로 본다. v1 Swing 화면과 그 GUI 테스트·촬영 도구는 N-27(D-163)에서 지웠다(옛 코드는 태그 `swing-final`).

## 돌리기

| 명령 | 내용 |
| --- | --- |
| `./gradlew test` | 단위 테스트(lib-mips, app, engine). 창 없이 돈다 |
| `./gradlew :app:testConstantIdentityHash` | app 단위 테스트를 identity hash가 모두 같은 JVM(`-XX:hashCode=2`)에서 한 번 더(D-129). `@Tag("timing")`은 뺀다 |
| `bash tools/check-engine-unchanged.sh` | 엔진 패키지가 원본과 같은지(허용한 패치 1개 제외, 규칙 2.1) |
| `./gradlew :engine:test` | v2 엔진 서버 테스트(N-03). `-Phcs.constantHash=true`로 상수 identity hash JVM에서 한 번 더 |
| `cd electron && npm test`, `xvfb-run -a npx playwright test` | 화면 단위 테스트와 e2e(가짜 엔진; 진짜 엔진은 `HCS_ENGINE_JAR`·`HCS_ENGINE_CMD`, CI `runtime` 작업) |
| `node electron/tools/capture-screens.ts` | 화면 스크린샷(`electron/docs/screens/`) |

CI(`.github/workflows/ci.yml`)는 push·PR마다 Linux에서 vendor·assets 검사, 엔진 불변, 포크 표시, `build`(단위 테스트 포함), 돌연변이 테스트, 상수 identity hash 단위 테스트, 엔진 서버 테스트를 돌리고, 화면(electron) 단위·e2e, 번들 런타임과 진짜 엔진 e2e, Windows 설치 파일(setup exe) 설치·실행을 본다. 원조 2.7.1 + Java 8에서 트랙 A jar도 돌린다.

**SPIM 결과는 굳혀 둔 파일이다(D-141).** `vendor/spim-9.1.24`와 hcs-asm은 사용자 결정으로 지웠다. 지우기 전에 SPIM이 낸 결과를 파일로 굳혀 두었고, 테스트는 그 파일과 대조한다. 다시 만들지 않는다: 디스어셈블러 골든 `tests/disasm/`(README), 원본 spim의 실행·목록·메모리 배치 `tests/spim-oracle/`(README), Hallym MIPS 골든의 오라클 `tests/hmx/hallym-mips-v2.4.0/*.regs`, 시험용 실행 이미지 `tests/hmx/asm`·`mips`·`record`와 hcs-asm JSON `tests/asm/*.json`(`tests/asm/README.md`).

테스트는 개발자의 Logisim 환경설정을 건드리지 않는다: Gradle이 `java.util.prefs.userRoot`와 `hcs.configDir`를 `build/` 아래로 돌린다.

## 영역별

| 영역 | 테스트 |
| --- | --- |
| 엔진 회귀·저장 호환(규칙 2.1·2.3) | `ForkEngineRegressionTest`(포크 클래스를 원조 시작점으로 돌린 시험 jar `fork-tty.jar`의 `-tty table`이 tests/circ/*.expected와 같다), `ForkSaveCompatTest`·`LocaleSaveCompatTest`(새 부품 없는 .circ는 원조 저장과 바이트 동일), 엔진 `OpenSaveParityTest`·`EngineParityReplayTest` |
| MIPS 부품(lib-mips) | `MemoryComponentsTest`, `StackRegionTest`, `ConsoleTest`, `RefMipsTest`(참조 CPU가 굳혀 둔 SPIM 결과 `tests/spim-oracle/run`과 같은지), `JarLibraryTest`(원조 2.7.1에서 불러오기) |
| 불러오기(.hmx만, D-126·D-141) | `ProgramLoadIntegrationTest`, `ProgramLoaderTest`, `LoadSummaryTest`, `HmxConsistencyTest`(굳힌 이미지 = 디스어셈블러 골든의 SPIM 목록), `HallymMipsGoldenTest`, `MergedLoadTest`, `AssemblySourceTest`·`AssemblySourceFileTest`(옛 .circ의 .s 경로: 사실과 할 일, 같은 바이트로 저장, .hmx로 바뀜), 엔진 `AssemblySourceEngineTest`(`mips.facts`의 `assemblySource`) |
| 디스어셈블러(공용, D-127) | `DisassemblerGoldenTest`(tests/disasm 골든을 SPIM 목록과 한 줄씩, QtSpim 창 글 4717줄), `DisassemblerTest`, `SpimDumpListingTest`(원본 spim -dump 목록, `tests/spim-oracle/dump`) |
| 넷 모델·추적 | `NetlistTest`, `InfluenceTest`, `OriginTraceTest` |
| 정적 진단 | `StaticCheckTest`(정상 회로 0건, 종류마다 한 건), 엔진 `DiagTest` |
| 동적 진단 | `DynamicCheckTest`, `FaultCollectionTest`(tests/circ/faults 18개가 기대 메시지 한 건씩) |
| 편집기 | 패키지마다(`wiring`, `props`, `find` …) 모델 테스트, 엔진의 의도 테스트(`EditTest`, `EditParityTest` …), 화면 e2e |

## 사이클 뷰(C-10)

추적표 C-10의 항목과 그것을 보는 테스트다.

| 항목 | 테스트 |
| --- | --- |
| 기록 재생 = 실제 재실행 | `RecordingTest.reconstructIsARealRerun`: 체크포인트에서 다시 만든 상태가 그 스텝의 실제 상태와 모든 넷에서 같다. `recordsEveryNetAndMatchesTheLiveValues`, `ReplayIsolationTest`(재실행이 지금 시뮬레이션·Console·메모리 등록에 새지 않는다) |
| 뒤로 가기 뒤 다시 진행 | `RecorderViewTest.viewingAPastCycleAndGoingOnFromThere`(지난 사이클에서 진행하면 그 뒤를 버리고 새로 적는다), `viewingRefMipsKeepsTheFuture`, `RecordingTest.truncateDropsTheFuture`·`pokedInputIsKeptForReplay`, `viewingAPastCycleOfFactorialKeepsTheRecord` |
| Run Until 조건별 | 엔진 `RecordTest.runUntil*`(PC 값, 부하에서도 같은 사이클, 줄 바뀜), `RunUntilTest`(PC 글자 읽기) |
| 레지스터 대응 추정 | `RegisterFileTest`(라벨 숫자·이름·위치로 추정, 수동 대응, 저장 후 다시 열기, 되돌리기, 표시 없을 때 경로별 나열) |
| factorial.s로 $sp 이동·깊이·복귀 | `MachineStateTest.factorialStackDepthFollowsTheCalls`(가장 깊을 때 56바이트 = 7단 × 8, 모두 돌아오면 0, Stack 화살표·최고 수위), `StackDemoTest` |
| halt에서 멈춤 | `RunUntil.halted`: 회로에 `halt` 출력 핀이 있으면 1이 되는 사이클에서 멈춘다(ref-mips는 Console Exit, `MachineStateTest`, `DynamicCheckTest.normalCircuitsStayQuiet`) |
| 그 밖의 사이클 뷰 | `CycleModelTest`(PC·디스어셈블·실행 이미지의 라벨, 옛 .s 경로는 읽지 않음), `MipsTextTest`, `FieldPathsTest`, 엔진 `RecordTest`와 화면 e2e(Cycle View·Registers·Memory·Instruction, D-144) |
| 성능 | `RecordingPerformanceTest`(ref-mips 100k 스텝 캡처·재구성), `DynamicCheckTest.scanningAStepIsCheap`. 수치는 docs/PERFORMANCE.md |
