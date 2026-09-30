# 튜토리얼 예제 (N-18, D-161)

두 교과목 튜토리얼이 여는 예제다. 앱은 이 파일들을 실행 폴더에 복사해 열므로 여기 파일은 바뀌지 않는다. 설치 파일은 `resources/tutorial/`에 싣는다(`electron/src/main/examples.ts`).

| 파일 | 무엇 | 만든 방법 |
| --- | --- | --- |
| `tutorial-logic.circ` | 논리설계 및 실험 트랙의 회로. Hallym MIPS 부품은 Radix Probe뿐 | `app/src-test/.../demo/TutorialLogic.java`(`:engine:canvasFixtures`가 다시 만들고 CI가 차이를 본다) |
| `tutorial-mips.circ` | 컴퓨터구조 트랙의 작은 단일 사이클 데이터패스. PC 시작 = entry 상수 `0x00400000`, 터널 오타 `RegWirte` 하나 | `app/src-test/.../demo/TutorialMips.java` |
| `tutorial.s` | 컴퓨터구조 트랙의 프로그램 원본. Hallym MIPS v2.4.0 골든 `data.s`와 같은 프로그램에 머리 주석(`# assemble: no exception handler`)을 붙였다 | 손으로 씀 |
| `tutorial.hmx` | 그 실행 이미지. **SPIM 시작 코드가 없다**: 프로그램 자신의 18워드뿐이고 entry = `main` = `0x00400000` | Hallym MIPS 2.6.0이 만들었다(아래) |

## tutorial.hmx를 만든 방법

이 도구는 기계어를 만들지 않는다(D-010, D-141). 이미지는 Hallym MIPS 자신의 코드가 만든다.

1. `git clone --depth 1 --branch v2.6.0 https://github.com/ars2323/hallym-mips-simulator`
2. `cd electron && npm ci --ignore-scripts && npm run build`(SPIM 코어 addon, g++·bison·flex)
3. Hallym MIPS의 `tests/sim/hmx.test.ts`가 골든을 만드는 것과 같은 호출로 내보냈다: `readImage(call, source, {fileName, run: {argv: ['program.s'], env: []}, handler: null})`와 `formatHmx({...image, source, sourceSha256, producedBy: 'Hallym MIPS 2.6.0', assembled})`. `handler: null`은 원본 첫 줄 `# assemble: no exception handler`가 뜻하는 것(Hallym MIPS 설정 › 고급 › Exception handler: None)이다. 쓴 스크립트는 그 두 함수를 부르는 몇 줄뿐이다.
4. 같은 빌드와 스크립트로 Hallym MIPS의 골든 `no-handler.hmx`를 다시 만들어 `assembled`·`produced-by` 말고 같은지 먼저 확인했다. Hallym MIPS의 `tests/sim/hmx.test.ts`도 그 빌드에서 모두 통과했다(19).

파일은 Hallym MIPS가 쓴 그대로다(손으로 고친 줄 없음).

## 확인하는 시험

- `app` `TutorialMipsTest.theProgramIsHallymMipsExportWithNoStartUpCode`: 머리(Hallym MIPS 2.6.0, 원본 sha256, entry = main = `0x00400000`)와, 워드·데이터가 Hallym MIPS v2.4.0 골든 `data.hmx`(같은 프로그램을 처리기와 함께 어셈블한 것)의 시작 코드 9워드 뒤와 같은지.
- `electron` `shipped-programs.test.ts`: 설치 파일이 싣는 모든 예제 파일에 SPIM 시작 코드 9워드가 없는지.
- 엔진 `TutorialExamplesTest`, 진짜 엔진 `real-engine-tutorial.e2e.ts`: 불러오기와 끝까지 실행(`sum = 14`, exit).
