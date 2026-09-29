# Hallym Circuit Studio

한림대학교 Micro-architecture 실습도구. 수업에서 쓰는 **Logisim 2.7.1**의 시뮬레이션 엔진을 그대로 돌리고, 그 위에 새 화면을 얹었다. 학생이 single-cycle MIPS를 설계하다 막히는 곳을 도구가 짚어 준다.

- 기존 `.circ` 과제가 그대로 열리고 결과가 같다. 새 부품을 쓰지 않은 파일은 원조 2.7.1과 바이트까지 같게 저장된다.
- 동작하지 않는 회로(값이 정해지지 않음, 진동, 구조상 동작할 수 없음)만 알린다. 원인 한 곳을 학생이 붙인 이름과 위치로 말하고, 동작하는 회로의 정오는 판단하지 않는다.
- 사이클마다 명령어·신호·레지스터를 한 화면에서 보고 지난 사이클로 돌아가는 Cycle View.
- 32비트 주소를 그대로 쓰는 MIPS 부품(Instruction Memory, Data Memory(데이터+스택), Console)과 [Hallym MIPS Simulator](https://github.com/ars2323/hallym-mips-simulator)가 내보낸 실행 이미지(`.hmx`) 불러오기.

**Hallym Circuit Studio 2**(v2.0.0부터)는 화면을 Hallym MIPS와 같은 Electron으로 새로 만들고, 안에서는 Java 엔진이 Logisim 2.7.1을 돌린다. 편집도 엔진이 원조 Logisim의 편집 코드로 하므로 쓰는 법과 결과 파일이 원조와 같다. Swing판(v1.0.x)은 v1.0.3이 마지막이다.

기획과 결정 사항은 [PLAN.md](PLAN.md), 작업 규칙은 [CLAUDE.md](CLAUDE.md), 결정 기록은 [docs/DECISIONS.md](docs/DECISIONS.md), 진행 상황은 [docs/PROGRESS.md](docs/PROGRESS.md)에 있다.

## 화면

| | |
| --- | --- |
| ![시작 화면](electron/docs/screens/start.png) | ![회로 열기](electron/docs/screens/open-file.png) |
| 시작 화면: 튜토리얼 보기 / 바로 시작 | 파일 탭, 값 색으로 그린 회로, 버스 폭, 터널 색, Tunnels |
| ![Messages](electron/docs/screens/messages-list.png) | ![Cycle View와 Registers](electron/docs/screens/registers.png) |
| Messages: 동작하지 않는 연결을 종류별로, 학생이 붙인 이름으로 | Cycle View와 Registers(Hallym MIPS와 같은 패널) |
| ![Load Program](electron/docs/screens/load-summary.png) | ![Signal Flow](electron/docs/screens/signal-flow.png) |
| 실행 이미지(.hmx) 불러오기 요약 | Signal Flow: 한 번 눌러 신호가 가는 길 |

모든 화면은 `electron/docs/screens/`에 있고, 무엇을 볼지는 [그 폴더의 README](electron/docs/screens/README.md)에 있다.

## 다운로드

[Releases](https://github.com/ars2323/hallym-circuit-studio/releases)에서 받는다. v2.0.0 전까지 v2는 사전 릴리스(`v2.0.0-alpha.N`)로 올라온다.

- **Windows:** `HallymCircuitStudio-<버전>-win-x64-setup.exe` 하나다. 실행하면 진행 화면 뒤에 마침 화면이 나오고, **지금 실행하기**가 체크된 채로 마침을 누르면 바로 열린다. 관리자 권한도 Java도 필요 없다. 다음부터는 시작 메뉴의 **Hallym Circuit Studio**로 연다.
- **"Windows의 PC 보호" 창이 뜨면:** 설치 파일에 코드 서명이 없어서 Microsoft Defender SmartScreen이 막는 것이다. **추가 정보** → **실행** 순서로 누른다. 받은 파일이 릴리스의 파일과 같은지는 릴리스 노트의 SHA-256으로 확인한다(`certutil -hashfile <파일> SHA256`).
- 설치 파일이 쓰는 곳, 조용한 설치(`/S`), 제거, 예전 MSI 설치본 처리는 [docs/install-windows-ko.md](docs/install-windows-ko.md)에 있다.
- 앱은 설정을 기억하지 않는다. 껐다 켜면 모두 기본값이다(실습실 PC 규칙).

## 두 트랙

| | 트랙 A: MIPS 부품 라이브러리 | 트랙 B: Hallym Circuit Studio 앱 |
| --- | --- | --- |
| 무엇 | 원조 Logisim 2.7.1에서 불러 쓰는 JAR 라이브러리 `hcs-mips.jar` | 이 저장소의 앱(Electron 화면 + Java 엔진) |
| 받는 것 | `hcs-mips.jar` 또는 `hcs-mips-<버전>-windows.zip` | setup exe |
| 쓰는 법 | Project › Load Library › JAR Library로 `hcs-mips.jar`를 불러온다([docs/track-a-guide.md](docs/track-a-guide.md)) | 부품 목록의 **Hallym MIPS**에 늘 있다 |

두 트랙은 같은 부품 코드(`lib-mips`)를 쓴다. 한 `.circ`가 두 도구에서 모두 열린다(Hallym MIPS 부품을 쓴 파일을 원조에서 열 때는 `.circ` 옆에 `hcs-mips.jar`가 있어야 한다).

## 소스에서 빌드

필요한 것: JDK를 받아 오는 Gradle wrapper(toolchain 자동 다운로드, JDK 21), Node.js 22.18 이상.

```
./gradlew :engine:stage          # 엔진 jar(engine/build/stage/hcs-engine.jar)와 hcs-mips.jar
./gradlew test                   # Java 단위 테스트, 엔진 회귀, 편집 동등성
cd electron
npm ci
npm run electron                 # 앱 실행(소스 트리의 엔진을 씀. java는 JAVA_HOME 또는 PATH)
npm test                         # 화면 단위 테스트
xvfb-run -a npm run e2e          # Playwright e2e(Linux는 Xvfb)
```

Windows 설치 파일은 Windows에서 `./gradlew :engine:stage :engine:runtime` 뒤 `electron/`에서 `node tools/package.ts`로 만든다. 릴리스 순서는 [docs/release.md](docs/release.md), 테스트 전체는 [docs/TESTING.md](docs/TESTING.md)에 있다.

## 폴더

```text
electron/               화면: Electron main과 렌더러(TypeScript), 테스트, 도구, 설치 파일 설정, 스크린샷
  src/renderer/shared/  Hallym MIPS에서 가져온 공유 화면 부품(출처는 electron/ORIGIN.md)
engine/                 Java 엔진 서버: headless Logisim 2.7.1, stdio 위 JSON-RPC(docs/engine-api.md)
app/                    Logisim 2.7.1 포크 소스와 엔진이 쓰는 GUI 없는 코드
lib-mips/               트랙 A: MIPS 부품 JAR 라이브러리(원조 2.7.1용, 엔진도 씀)
vendor/logisim-2.7.1/   Logisim 2.7.1 원본 jar(수정 금지, tools/verify-vendor.sh로 확인)
assets/                 글꼴, 학교 식별요소 파생 파일
tests/                  엔진 회귀, 편집 동등성 골든, 실행 이미지(.hmx), 진단 테스트 입력, 굳혀 둔 SPIM 결과
docs/                   결정 기록, 진행 표, 엔진 규약, 조사 결과, 설계 메모
tools/                  저장소 검사(vendor, assets, 엔진 불변)와 트랙 A 포장
```

## 라이선스

Logisim 2.7.1을 따라 GNU GPL 버전 2 이상으로 배포한다([LICENSE](LICENSE)). 원저작자는 Carl Burch다. Hallym MIPS Simulator에서 가져온 화면 코드는 BSD 3-Clause이고, SPIM 코드는 들어 있지 않다(어셈블은 Hallym MIPS가 하고, SPIM이 낸 출력만 시험 자료로 남는다). 서드파티 라이선스와 학교 식별요소 사용 조건(Hallym University 소유, 상업적 사용 금지, 대학의 공식 제품이 아님)은 [NOTICE](NOTICE)에 있다.
