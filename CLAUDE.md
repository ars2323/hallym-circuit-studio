# Hallym Circuit Studio — 작업 지침

Logisim 2.7.1을 포크한 한림대학교 Micro-architecture 실습도구다. 무엇을 왜 만드는지는 **`PLAN.md`가 기준**이다. 작업 전에 반드시 읽고, 이 파일과 충돌하면 PLAN.md의 제품 결정을 따른다.

## 0. 이번 범위

- 로드맵 **0~4단계**만 구현한다(PLAN.md 9장): 기반, MIPS 부품 라이브러리(트랙 A), 포크 + 편집기 기반(2a), 편집 핵심(2b), 정적 진단 + 배선(2c), 기록 엔진 + 사이클 뷰, 동적 진단, 편의 마무리(4b).
- **PLAN.md 11장 편집기 개선 전체**를 구현한다. 전부 UI 층이다. 엔진과 .circ 안의 툴바·마우스 매핑·라벨 글꼴은 바꾸지 않고, 사용자 설정은 앱 환경설정에, 사용자가 직접 지정한 정보(터널 색, 신호 그룹, 영역 메모, 스플리터 팔 이름)만 PLAN.md 7.0 네임스페이스로 저장한다(11.0).
- **Verilog(5~9단계)는 구현하지 않는다.** PLAN.md 7.0의 원칙만 지킨다. Verilog 파서, Yosys, ANTLR 의존성을 추가하지 않는다.

## 1. 일하는 방식: 사용자에게 일을 시키지 않는다

- 사용자는 결과만 받는다. 질문하거나 확인을 요청하지 말고, 가장 합리적인 쪽으로 결정하고 기록한다.
- 결정은 `docs/DECISIONS.md`에 한 항목씩 남긴다(날짜, 결정, 이유, 대안). PLAN.md의 미결정 사항을 풀면 PLAN.md도 함께 고친다.
- 멈추는 경우는 **되돌릴 수 없고 어느 쪽이든 그럴듯한 결정**뿐이다. 예: 저장소를 public으로 바꾸기, 학생에게 배포하기, 사용자가 둔 원본 파일 삭제. 이때는 준비 작업까지 하고 결정만 남긴다.
- 사람이 해야 하는 일은 모두 **GitHub 이슈 하나**(`needs-human` 라벨)에 체크리스트로 모은다. 예: sudo가 필요한 패키지 설치, 교수님 확인, 실습실 PC 실행 확인. 정확한 명령 한 줄을 적는다. 그동안 다른 일은 계속한다.
- 작업 세션이 끝날 때는 한국어로 짧게 보고한다. 끝난 것, 머지한 PR, 다음 할 일, `needs-human` 변경 사항을 적는다.

## 2. 절대 규칙

1. **Logisim 시뮬레이션 엔진을 수정하지 않는다.** 값 전파, 넷 계산, 기존 부품 동작을 바꾸지 않는다. 대상 패키지는 `com.cburch.logisim.circuit`, `.comp`, `.data`, `.instance`, `.std.*`와 `.file` 로딩 규칙이다. 새 기능은 리스너, 새 부품, 새 UI 패널로 얹는다. 피할 수 없으면 최소 패치 하나로 격리한다. 그리고 `docs/DECISIONS.md`에 이유를 적고 회귀 테스트로 동작 불변을 증명한다.
2. **`vendor/` 아래 원본은 수정하지 않는다.** Logisim 2.7.1 jar와 SPIM 9.1.24 소스가 여기에 속한다. 빌드 산출물은 밖에 둔다.
3. **새 부품을 안 쓴 .circ는 원조 2.7.1과 바이트 수준으로 호환되게 저장한다.** 추가 정보는 PLAN.md 7.0의 네임스페이스 방식으로만 넣는다.
4. **학교 로고와 캐릭터는 원형 그대로 쓴다.** 다시 그리기, 색 변경, 비율 변경, 요소 추가를 하지 않는다. 가이드라인 PDF와 .ai 원본은 git에 넣지 않는다(8절).
5. **SPIM 코드는 GPL 코드와 섞지 않는다.** SPIM은 별도 실행 파일(`hcs-asm`)로만 쓴다.
6. **도구는 부품 라이브러리와 편집 도구를 주고 "동작하지 않는 회로"만 알린다.** 동작하지만 잘못된 회로(값이 0/1로 흐르는데 결과만 틀림)는 판단하지도, 고치지도, 정답과 비교하지도 않는다. 학생 설계에 관여하지 않는다: 예를 들어 분기 목적지 계산은 학생 데이터패스의 몫이고, 도구는 QtSpim 기계어를 그대로 메모리에 올려 주소의 워드를 내보낼 뿐이다(PLAN.md 1장 설계 원칙, D-010, D-019).

## 3. 현재 폴더와 목표 구조

사용자가 둔 시작 상태:

```
logisim-generic-2.7.1.jar
spim-9.1.24/                 # SPIM 9.1.24 원본 (CPU/, spim/, QtSpim/ ...)
resources/font/Pretendard-1.3.9.zip
resources/hallym/logo/logo.zip
resources/hallym/character/character.zip
PLAN.md, CLAUDE.md
```

목표 구조(0단계 첫 PR에서 정리):

```
PLAN.md, CLAUDE.md, README.md, LICENSE (GPL), NOTICE (서드파티 라이선스 목록)
docs/DECISIONS.md, docs/…                  # 조사 결과, 설계 메모
vendor/logisim-2.7.1/logisim-generic-2.7.1.jar   # 원본, 수정 금지
vendor/spim-9.1.24/                         # 원본, 수정 금지
native/hcs-asm/                             # SPIM CPU/를 링크하는 명령줄 어셈블러 (C++)
lib-mips/                                   # 트랙 A: 원조 2.7.1용 JAR 라이브러리
app/                                        # 트랙 B: 2.7.1 포크 (0~2단계에서 생성)
assets/fonts/, assets/hallym/…              # git에 넣는 파생 리소스
tests/circ/, tests/asm/                     # 회귀·어셈블·진단 테스트 입력
resources/                                  # 사용자 원본 보관. .gitignore 대상
ref/                                        # 참고용 외부 클론. .gitignore 대상
```

## 4. Git·GitHub 관리

git과 gh는 설치·로그인돼 있다. 저장소 관리는 전부 직접 한다.

- **저장소:** `ars2323/hallym-circuit-studio`, public(사용자 확정, D-017). 설명은 "Hallym Circuit Studio — 한림대학교 Micro-architecture 실습도구 (Logisim 2.7.1 fork)". 가이드라인 PDF·`.ai`·`resources/`·비밀 값이 기록에 들어가지 않게 한다.
- **마일스톤:** 단계마다 하나씩 만든다. `0 기반`, `1 MIPS 부품 라이브러리`, `2a 포크 + 편집기 기반`, `2b 편집 핵심`, `2c 정적 진단 + 배선`, `3 기록 엔진 + 사이클 뷰`, `4 동적 진단`, `4b 편의 마무리`. 편집기 개선 항목의 배치는 PLAN.md 11.15를 따른다.
- **이슈:** PLAN.md의 항목을 작업 단위 이슈로 쪼개 해당 마일스톤에 단다. 미결정 사항은 `question` 라벨 이슈로 만들고, 조사로 풀리면 결론을 달고 닫는다.
- **브랜치와 PR:** `main`에 직접 push하지 않는다. `feat/…`, `fix/…`, `docs/…`, `chore/…` 브랜치를 만들고, 작업 단위마다 PR을 연다. PR 본문은 한국어로 쓰고 `Closes #N`을 단다.
- **머지 절차:**
  1. CI가 통과하는지 확인한다.
  2. `gh pr merge` 전에 compat-reviewer 서브에이전트(`.claude/agents/compat-reviewer.md`)를 돌린다. 이 에이전트는 diff만 보고 2절 절대 규칙 위반과 테스트 없는 기능 변경을 보고한다.
  3. 위반이 있으면 고친 뒤 다시 돌린다. 위반 0건이 될 때까지 반복한다. "확인 필요" 항목은 PR 본문에 판단 근거를 적는다.
  4. `gh pr merge --squash --delete-branch`로 직접 머지한다.
- **커밋:** 영어 명령형 한 줄 제목(`Add Data Memory component`)에 필요하면 본문을 단다. 작은 단위로 자주 커밋한다.
- **CI (GitHub Actions):** Linux에서 빌드, 단위 테스트, 엔진 회귀, 어셈블 일치를 매 push·PR마다 돌린다. Windows 러너 작업(`hcs-asm.exe`, jpackage zip/MSI)은 1단계 배포 전에 추가한다.
- **릴리스:** 단계 산출물은 태그(`v0.1.0` = 1단계 라이브러리)와 GitHub Release로 만든다. 첨부물은 `hcs-mips.jar`, `hcs-asm`(Linux), `hcs-asm.exe`, 사용 안내다. 학생 배포는 사용자 결정이므로 Release는 draft로 둔다.
- **.gitignore:** `resources/`, `ref/`, `build/`, `.gradle/`, `native/**/build/`, IDE 파일.

## 5. 빌드 환경

- **Java:** Gradle(wrapper)과 toolchain 자동 다운로드를 쓴다. JDK를 sudo로 설치하지 않는다.
  - `lib-mips`: `--release 8`. 학생 PC의 원조 2.7.1이 어떤 JRE에서 돌지 모르므로 보수적으로 잡는다. 외부 의존성 없는 단일 jar로 만든다(JSON 파서 등은 직접 짜거나 shade한다).
  - `app`: 2.7.1 소스를 최신 JDK(21)로 빌드되게 정리한다. FlatLaf는 의존성으로 넣는다.
- **C++:** `hcs-asm`은 g++, make, bison, flex로 빌드한다. 없으면 `sudo -n` 가능 여부를 확인한다. 안 되면 사용자 공간 대안(예: 소스 빌드, 정적 바이너리)을 시도한다. 그래도 안 되면 `needs-human`에 설치 명령을 적고 Java 쪽을 먼저 진행한다.
- **2.7.1 jar 실행:** `java -jar vendor/logisim-2.7.1/logisim-generic-2.7.1.jar file.circ -tty table`로 GUI 없이 회로를 돌릴 수 있다. 엔진 회귀 테스트는 이것을 기준 엔진으로 쓴다. `-load` 옵션으로 RAM 이미지도 올릴 수 있다. 정확한 옵션은 2.7.1 문서 "Command-line verification"을 확인한다.

## 6. 0단계 할 일 (첫 세션)

순서대로 진행하고, 결과는 `docs/`와 `docs/DECISIONS.md`에 남긴다.

1. **저장소 초기화.** 3절 구조로 파일을 옮긴다(원본은 옮기기만 하고 내용은 수정하지 않는다). `.gitignore`, README, LICENSE, NOTICE를 추가하고, GitHub 저장소·라벨·마일스톤·이슈를 만든다.
2. **2.7.1 소스 확보.** `logisim-generic-2.7.1.jar` 안에 `.java` 소스가 들어 있는지 확인한다(`unzip -l … | grep '\.java$'`). 없으면 SourceForge `circuit` 프로젝트의 2.7.1 소스를 받는다. 포크는 `app/`에 두고, 첫 커밋은 원본 소스 그대로 올린다. 이후 변경을 diff로 추적하기 위해서다.
3. **JAR 라이브러리 방식 확인.** 2.7.1의 `Library` 하위 클래스, manifest `Library-Class`, `InstanceFactory`로 최소 부품 하나를 만든다. 원조 jar에서 Project › Load Library › JAR Library로 불러 동작을 확인한다. 이어서 그 부품을 쓴 .circ의 `<lib desc="jar#…">` 경로 저장 방식을 기록한다. 포크에서 같은 파일을 열 때 번들된 라이브러리로 자동 연결하는 방법도 정한다. 한 .circ가 두 도구에서 모두 열려야 한다.
4. **Hallym MIPS 참고 클론.** `gh repo clone ars2323/hallym-mips-simulator ref/hallym-mips-simulator`로 받는다(사용자 본인의 저장소). 다음을 확인하고 재사용한다.
   - `docs/design/tokens.md`: 디자인 토큰. Swing 이식 대상.
   - `assets/ci/`: 학교가 배포한 로고 원본과 UI 규정. 로고는 여기 것을 우선 쓴다.
   - CI 설정: Windows에서 SPIM을 MSVC와 winflexbison으로 빌드하는 방법.
   - `docs/ARCHITECTURE.md`, 디코더 오라클 테스트: 어셈블 일치 테스트의 참고.
   - `CPU/`가 `vendor/spim-9.1.24/CPU/`와 같은지 diff로 확인한다. 달라진 점이 있으면 기록한다.
   - QtSpim 기본 설정: 예외 처리기 불러오기, 지연 분기, bare machine, 의사 명령어. `QtSpim/spim_settings.h`, `spimview.cpp`에서 기본값을 찾는다. Hallym MIPS가 이를 바꿨는지도 본다.
5. **`hcs-asm` 명령줄 어셈블러.** `native/hcs-asm/`에서 `vendor/spim-9.1.24/CPU/`를 수정 없이 링크하고, `parser.y`와 `scanner.l`은 빌드 디렉터리에서 생성한다.
   - 입력은 .s 파일 경로와 설정 플래그다. 과제 표준 설정을 기본값으로 한다: 예외 처리기 없음, 지연 분기 끔, 의사 명령어 켬. 기계어는 QtSpim 그대로이고 도구는 인코딩에 관여하지 않는다(`docs/DECISIONS.md` D-010).
   - 출력은 JSON 한 덩어리다: `text[{addr, word, line, source}]`, `data[{addr, word}]`, `labels{name: addr}`, `errors[{line, message}]`, `settings{…}`. 어셈블 오류가 있으면 비정상 종료 코드를 낸다.
   - 사용자 `.text`는 `0x00400000`부터(main부터), `.data`는 `0x10010000`부터다. 커널 세그먼트는 출력하지 않는다. 시작 코드 없이 올렸을 때 SPIM이 어떤 주소에 무엇을 두는지 실제로 확인한다.
   - 테스트: `tests/asm/`에 예제 .s(산술, lw/sw, beq/bne 앞·뒤 분기, j/jal, la/li 등 의사 명령어, .data 문자열)를 두고 기대 JSON과 비교한다.
6. **분기 인코딩 확인(PLAN.md 10장 미결정 1번).** 지연 분기 켬/끔 각각에서 `beq`의 오프셋 필드 값을 교재 정의(목적지 = PC+4 + offset×4)와 비교한다. 결과와 과제 표준 설정 결론을 DECISIONS에 적고, PLAN.md 6.7과 10장을 갱신한다.
7. **리소스 정리.** 8절대로 한다.
8. **엔진 회귀 테스트 골격.** `tests/circ/`에 작은 회로 몇 개(조합, 레지스터+클럭, RAM)를 만든다. 표준 2.7.1 jar의 `-tty` 결과를 기대값으로 저장하는 스크립트를 두고 CI에 연결한다. 실제 과제 .circ 세트는 사용자에게 받을 항목으로 `needs-human`에 적는다.

## 7. 단계별 요점

- **1단계 (트랙 A, `lib-mips`):** PLAN.md 6.2·6.9의 포트 이름과 동작을 그대로 구현한다.
  - Instruction Memory, Data Memory, Stack, Console, 다중 진법 Probe를 만든다.
  - 32비트 byte 주소, 희소 저장, 영역 밖이면 출력 안 함(floating), 떠 있는 제어 입력은 쓰기 안 함, 워드 접근만 지원한다.
  - 부품 우클릭 메뉴에 ".s 불러오기"를 둔다. `hcs-asm`을 jar와 같은 폴더에서 찾는다.
  - 참조 single-cycle MIPS 회로(`tests/mips/ref-mips.circ`, 우리 테스트용)를 직접 만든다. 분기 가산기는 PC 기준이다(QtSpim 기계어 그대로, D-010). 교재 기본 명령어와 syscall 디코드를 넣고, 예제 .s의 레지스터·메모리·Console 결과가 SPIM 실행 결과와 같은지 테스트한다.
- **2a단계 (트랙 B, `app`):**
  - 원본 소스 커밋 위에서 최신 JDK 빌드를 정리하고, 엔진 회귀 테스트가 표준 jar와 같은 결과를 내는지 먼저 확인한다.
  - 그다음 FlatLaf, 디자인 토큰, Pretendard를 적용하고 `lib-mips` 부품을 번들한다.
  - 창 구조와 탭(PLAN.md 11.1, 탭 간 라이브러리 제외), 확대·축소(11.2), 자동 저장(11.13)을 만든다. 이후 UI는 모두 이 틀 위에 얹는다.
- **2b단계:** 우클릭 메뉴, 속성 편집, 빠른 Probe, 검색과 명령, 툴바·상태 표시줄·조작, 라벨·터널·마우스 오버 표시, 터널 이동·Ctrl+F(PLAN.md 11.15). 연결 탐색 엔진(넷 모델)을 여기서 만든다. 영향 경로·E·X 추적·넷 정보가 이것 하나를 쓴다.
- **2c단계:** 정적 진단(PLAN.md 4.2)과 Messages 탭을 만든다. 진단은 "정상 회로에서 메시지 0건"을 필수 테스트로 둔다. 따라오는 배선, 영향 경로, 서브회로 인스턴스 안내·포트 변경 영향, 탭 간 라이브러리와 저장 반영도 이 단계다.
- **편집기 공통:** 모든 편집 기능 PR은 "새 부품을 안 쓴 .circ를 저장하면 원조 2.7.1 저장 결과와 바이트 동일"(D-006 기준) 회귀 테스트를 통과해야 머지한다. UI 기능마다 모델 수준 단위 테스트를 두고, 가능하면 GUI 스모크 테스트(Xvfb)를 둔다(PLAN.md 11.16).
- **3단계:** 기록 엔진(변화분 기록), 사이클 표, 레지스터 패널(16·10·2진수 동시 표시), 뒤로 가기, 여기까지 실행, Console 탭, 자동 재로드를 만든다(PLAN.md 5장). 버스 값 표시와 활성 경로(11.12)도 여기다.
- **4단계:** E·X 출처 추적, 진동 경로, X 기록 감지를 만들고 사이클 뷰와 연동한다(PLAN.md 4.3).
- **4b단계:** PLAN.md 11장의 나머지 편의 기능(11.15 표).

## 8. 리소스 처리

원본 zip은 `resources/`에 그대로 두고(gitignore) 파생 파일만 `assets/`에 커밋한다.

- **파일 이름 복원:** 로고·캐릭터 zip 안의 한글 파일명이 `#Uc751#Uc6a9…`처럼 인코딩돼 있다. 풀 때 `#Uxxxx`를 해당 유니코드 문자로 바꾼다. 캐릭터 zip 안에 zip이 한 번 더 있다.
- **글꼴:** `Pretendard-1.3.9.zip`의 `public/static/`에서 Regular, Medium, SemiBold, Bold OTF와 `LICENSE.txt`만 `assets/fonts/pretendard/`에 넣는다. 가변 폰트와 웹 폰트는 넣지 않는다. 앱 시작 시 `Font.createFont`로 등록한다.
- **로고(`logo.zip`):** A1 심벌마크(기본형·활용형), A2 로고타입, A3 엠블럼, A4 시그니처가 있다. 각각 .ai(Illustrator 8 EPS, PostScript 기반)와 매뉴얼 페이지 JPG다.
  - 먼저 Hallym MIPS `assets/ci/`의 원본을 쓴다.
  - 부족하면 Ghostscript(`gs -dEPSCrop -sDEVICE=pngalpha -r600`)로 렌더링한다. 치수선·설명 글자 없이 마크만 잘라내고, 결과를 눈으로 확인한다.
  - JPG는 설명 글자가 섞인 매뉴얼 페이지이므로 앱에 쓰지 않는다.
  - 앱 아이콘과 로고 배치는 Hallym MIPS의 선례를 따른다.
- **캐릭터(`character.zip`, 하람&하리):** 기본형 PNG 3종(조합, 하람, 하리)과 응용동작 PNG 20종이 있다. 응용동작은 인사, 최고, OK, 안내, GO, 소통, 셀카, 식사, 축하, 공지, 금지, 교육, 궁금해, 사랑해, 감사, 감동, 팻말, 명절, 입학(졸업), 운동이다.
  - 영문 슬러그 이름(`haram-hari-greeting.png` 등)으로 `assets/hallym/character/`에 넣는다.
  - 쓰는 곳은 첫 실행 튜토리얼, 정보 창, 빈 화면 안내, 프로그램 정상 종료(`exit`) 안내 정도로 아낀다.
  - 오류 진단 메시지에는 쓰지 않는다.
  - 가이드라인을 지킨다: 요소 추가, 선·비율·색 변경 금지, 복잡하거나 비슷한 색 배경 금지, 최소 여백 확보.
  - 가이드라인 PDF는 외부 노출을 금하는 문서이므로 git에 넣지 않는다.
- NOTICE에 Pretendard(OFL), SPIM(BSD), Logisim(GPL), FlatLaf(Apache-2.0)를 적는다. 학교 식별요소는 "한림대학교 소유, 상업적 사용 금지"로 적는다.

## 9. 코드 원칙 (Verilog 대비 포함)

- 새 코드 패키지는 `kr.ac.hallym.hcs.*`로 한다. 포크 원본 패키지(`com.cburch.*`)의 변경은 최소로 하고, 바꾼 곳에는 `// HCS:` 주석을 단다.
- 진단, 기록 엔진, 사이클 뷰는 GUI가 아니라 회로 모델을 입력으로 받는다. GUI 없이 테스트할 수 있어야 한다.
- 부품·서브회로·터널·포트 이름을 다루는 공용 식별자 유틸리티를 하나 둔다. 경로 표기는 `datapath › PC`이다.
- 부품 종류별 처리는 등록표 하나에 모은다(향후 Verilog 매핑표가 붙을 자리).
- 사용자에게 보이는 문구는 한국어·영어 리소스 번들로 관리한다. 진단 문구는 PLAN.md 4.4의 원칙(원인 한 곳, 학생이 붙인 이름, 사실과 위치까지만)을 지킨다.
- 테스트 없는 기능 PR은 머지하지 않는다.
