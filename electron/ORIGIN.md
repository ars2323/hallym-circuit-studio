# electron/ — Hallym MIPS에서 가져온 것

Hallym Circuit Studio 2의 화면(`electron/`)은 **Hallym MIPS Simulator**(`ars2323/hallym-mips-simulator`, 같은 저자, BSD 3-Clause)의 Electron판과 같은 스택·같은 재료로 만든다(D-132, D-133, D-135, D-155). 이 파일은 거기서 가져온 파일마다 어디서 왔는지와 무엇을 바꿨는지를 적는다.

- **출처:** https://github.com/ars2323/hallym-mips-simulator, 태그 **`v2.6.0`**(커밋 `d8f0c97`). 경로는 그 저장소의 뿌리에서부터다. 처음에는 `v2.3.0`(커밋 `749841e`, D-135)에서 가져왔고, 사용자 지시(2026-09-28)로 모두 `v2.5.0`(커밋 `16d0597`)에 맞췄다(D-155: 바뀐 공유 파일은 다시 가져오고, 일부러 다른 곳은 아래 "v2.5.0에서 가져오지 않은 것"에 적었다). 사용자 추가 지시(2026-09-29)로 `v2.6.0`에 맞췄다(A-07: 1단계 D-167, 2단계 D-169 — 아래 "v2.6.0에서 가져온 것"). **.hmx 명세와 골든만 `v2.4.0`에 고정한다**(`tests/hmx/hallym-mips-v2.4.0/`, D-138).
- **라이선스:** BSD 3-Clause. 원문은 `LICENSE.hallym-mips.txt`(그 저장소 `LICENSE`와 바이트까지 같음)이고, 앱의 About › Licenses와 저장소 `NOTICE`에 함께 실린다.
- **다시 가져오기:** `node tools/import-hmips.ts`(목록은 그 파일의 `TAKEN`). `copy`는 태그의 파일을 그대로 덮어쓰고, `derived`는 태그의 파일을 `build/hmips/<태그>/`에 내려놓고 우리가 가져온 뒤로 바뀌었는지(`tools/hmips-sums.json`의 SHA-256과 비교) 알려 준다. 바뀌었으면 손으로 합치고 `--record`로 기록한다. `--check`는 저장소 밖에 아무것도 없이 목록의 파일이 다 있는지, `copy` 파일이 바이트까지 같은지 본다(`tests/unit/origin.test.ts`가 매번 돌린다).

## SPIM에서 나온 것은 가져오지 않는다(D-133 5항, 규칙 2.5)

Hallym MIPS `electron/`에는 SPIM의 `CPU/op.h`에서 만든 `src/core/op-table.ts`(생성 파일)와, SPIM 코어를 같은 프로세스에 링크하는 `native/`(`binding.gyp`, `src/addon.cc`, `src/run-win.cpp`)가 있다. 이것들과, 그 표나 SPIM의 문자열에 기대는 파일 — `src/core/*`(디코더, 명령어 글자, 설명, 어셈블 오류 해석, 문법 강조, 레지스터·메모리 표), `src/sim/*`(시뮬레이터 프로세스), `logic/machine.ts`, 편집기, Text·Console 패널, MIPS 튜토리얼 — 은 **하나도 가져오지 않았다.** Registers·Data·Inspector 패널(N-14, D-144)은 화면 코드만 `derived`로 가져왔다: 그 파일들이 upstream core에서 가져오던 것(디코더, 명령어 설명, 수 형식, 메모리 줄 배치, 라벨 표, `logic/machine.ts`)은 모두 빼고, 이 앱의 엔진이 보내는 값(Java 디스어셈블러와 필드 나누기, MemoryTable)과 앱의 얇은 어댑터(`src/renderer/app/logic/registers.ts`, `memory.ts`)로 바꿨다. 아래 표의 파일은 모두 Hallym MIPS가 직접 쓴 화면 코드·디자인 값·도구·테스트 틀이거나 서드파티 글꼴·아이콘이다.

**SPIM 유래가 아님을 확인한 근거:** 표의 코드 파일은 `src/core/`, `src/sim/`, `native/`의 어떤 파일도 import하지 않는다(upstream의 `dom.ts`가 `src/core/explain.ts`에서 가져오던 `codeParts`는 두 줄짜리 백틱 나누기라 여기서 직접 썼다). 명령어 표·레지스터 이름·SPIM 메시지 같은 SPIM의 표나 문자열이 들어 있지 않다. **이것은 사람의 확인에 기대지 않는다: `tests/unit/origin.test.ts`가 아래 표의 글 파일(코드·CSS·HTML·설정·문서·스크립트, 44개)을 하나씩 따로 읽어 파일마다 테스트 하나로 확인한다**("taken, not from SPIM: <파일>"). 파일마다 보는 것: ① Hallym MIPS의 `src/core/`, `src/sim/`, `native/`를 import하지 않는다(`from`·`import(…)`의 경로), ② SPIM의 이름과 문자열이 없다 — 명령어 표(`op-table`, `OP_TABLE`, `R3_TYPE_INST` 같은 명령어 분류), 애드온(`spim.node`), 코어 폴더(`CPU/`), `syscall`, SPIM·QtSpim이라는 이름, SPIM에 기대는 upstream 모듈 이름(`decoder.ts`, `explain.ts`, `instruction-text`, `asm-errors`, `mips-syntax`). 나머지 19개(글꼴·아이콘 그림·라이선스 글·시작 화면 영상과 정지 그림)는 `copy`라 태그의 파일과 바이트까지 같은지 본다. 이 파일별 자동 검사가 D-133 5항의 근거다. 같은 테스트가 가져온 파일의 upstream 경로가 가져오지 않는 목록(`NEVER`)에 걸리지 않는지, 그리고 `electron/`의 소스·테스트·도구 전체에 SPIM 표·코어·애드온 참조가 없는지도 본다.

## 가져온 파일

`copy` = 태그의 파일과 바이트까지 같다. `derived` = 그 파일을 바탕으로 이 앱에 맞게 고쳤다.

| 여기(`electron/`) | Hallym MIPS v2.5.0 | 방식 | 바꾼 곳 |
| --- | --- | --- | --- |
| `src/renderer/shared/dom.ts` | `electron/src/renderer/app/dom.ts` | derived | `codeParts`를 직접 씀(`src/core/explain.ts`를 가져오지 않음). `hallym()`·`character()`가 이 저장소의 원본 `assets/hallym/`을 가리킴(빌드 때 `__HALLYM__`). `withHex()`는 뺌. 표용 `monoCh()`, `userScrolls()`는 Registers·Data 패널과 함께 가져옴(N-14) |
| `src/renderer/shared/ui.ts` | `electron/src/renderer/app/ui.ts` | derived | `panelHead`, `tabsHead`, `headButton` 그대로. `tabsHead`에 탭 숨기기(`show`)와 `selected()`를 더함(좁은 창에서 패널이 다른 패널의 탭으로 들어감). `fitMeta()`는 뺌. `columnButton()`은 Registers·Data 패널과 함께 가져옴(N-14). 여러 파일·회로 탭용 `tabStrip()`을 새로 더함 |
| `src/renderer/shared/notice.ts` | `electron/src/renderer/app/notice.ts` | derived | 캐릭터를 선택으로(대부분의 빈 패널은 글만). 빈 패널 몸체 `noticeHost()`를 더함 |
| `src/renderer/shared/ask.ts` | `electron/src/renderer/app/panels/ask.ts` | derived | `character: false`(오류에는 캐릭터를 두지 않음), 버튼 하나(`cancel: null`), 사실을 적는 `detail` 칸, Esc가 어느 답도 아닌 `choose()`(복구 파일의 Recover / Discard)와 세 번째 답 `extra`(Save / Discard / Cancel, N-19), `File:` 줄과 같은 모양의 줄 `names`(Recovery file: …)를 더함 |
| `src/renderer/shared/welcome.ts` | `electron/src/renderer/app/panels/welcome.ts` | derived | 같은 카드·같은 크기, 카드 뒤 영상 하나(`backdrop.ts`, 단계가 바뀌어도 이어 돎, 2.5.0). 문구와 선택지는 부르는 쪽(`src/renderer/app/start.ts`)이 주고, 단계가 셋까지 이어질 수 있어(교과목 → 튜토리얼 보기·바로 시작 → 새 회로·파일 열기, A-08) upstream의 "← 처음으로" 줄이 한 단계 뒤로 가는 "← 이전"이고, 선택지는 할 일과 다음 단계를 함께 가질 수 있음(D-168). 시작 화면이 보이는지는 부르는 쪽이 `show()`로 알림(upstream은 `welcome()`이 `{root, show}`를 돌려줌: 여기는 `go`·`step`도 함께) |
| `src/renderer/shared/backdrop.ts` | `electron/src/renderer/app/panels/backdrop.ts` | derived | import 경로만(D-155). 정지 그림 먼저, 영상은 재생되면 겹쳐 나타남, 소리 없음, 줄인 움직임에서는 정지 그림만(영상을 불러오지 않음), 시작 화면이 아니면 영상을 내려놓음, 재생할 수 없으면 남색 |
| `src/renderer/assets/hallym/start/start.webm` | `electron/src/renderer/assets/hallym/start/start.webm` | copy | — (학교 홍보 영상의 첫 공중 촬영 장면, 느리게, 소리 없음: NOTICE, `hallym-assets.md`) |
| `src/renderer/assets/hallym/start/start.jpg` | `electron/src/renderer/assets/hallym/start/start.jpg` | copy | — (그 첫 프레임) |
| `tools/start-video.ts` | `electron/tools/start-video.ts` | copy | — (원본 영상에서 둘을 만드는 도구, ffmpeg) |
| `src/renderer/shared/about.ts` | `electron/src/renderer/app/panels/about.ts` | derived | About / Licenses 탭 그대로. About 탭의 줄은 부르는 쪽이 줌(Logisim 2.7.1 by Carl Burch, 엔진, 학교 식별요소) |
| `src/renderer/shared/overlay.ts` | `electron/src/renderer/app/logic/overlay.ts` | copy | — |
| `src/renderer/shared/names.ts` | `electron/src/renderer/app/logic/names.ts` | copy | — |
| `src/renderer/shared/titlebar.ts` | `electron/src/renderer/app/app.ts` | derived | 제목 줄, `button()`, `iconButton()`, `fitTitlebar()`를 부품으로 떼어 냄. 이름 뒤에 교과목 칩 자리(`course`, 줄이기에서 빠지지 않음, A-08·D-168). 줄이는 단계에 "도구 모음을 제목 줄 아래 제 줄로"를 더함(도구 모음이 버튼 20개 남짓) |
| `src/renderer/shared/splitter.ts` | `electron/src/renderer/app/app.ts` | derived | 편집기·Run 사이 분할선과 Console·Assemble 위 손잡이를 한 부품으로(두 방향). 접기 버튼은 뺌 |
| `src/renderer/shared/shared.css` | `electron/src/renderer/app/app.css` | derived | 토큰, 글꼴, 제목 줄, 버튼, 패널 머리, 분할선, 빈 상태, 시작 화면(2.6.0의 유리 카드 `rgba(255,255,255,.82)` + `backdrop-filter: blur(18px) saturate(1.2)`, `--text-2-glass`, 창 전체 아래의 영상 `.wback`(`position: fixed`, `blur(8px) saturate(1.25) sepia(.1) hue-rotate(-6deg)`, `scale(1.06)`, 위아래가 조금 짙은 남색 층), 시작 화면의 어두운 유리 제목 줄·상태 표시줄 `body.first-screen` — 그대로, D-169), 대화상자, About, 상태 표시줄, 띠. SPIM 패널(편집기, Assemble, Text, Data, Registers, Inspector, Console)과 튜토리얼 규칙은 뺌. 도구 모음 묶음·제 줄·선택 상자 규칙을 더함 |
| `src/renderer/app/index.html` | `electron/src/renderer/app/index.html` | derived | 같은 CSP(2.5.0의 `media-src 'self'` 포함). 제목과 스타일 시트만 다름 |
| `src/renderer/shared/columns.ts` | `electron/src/renderer/app/logic/columns.ts` | copy | — (폭에 맞춰 여백 → 글꼴 1px → 열 순서로 줄이는 규칙, N-14) |
| `src/renderer/shared/registers.ts` | `electron/src/renderer/app/panels/registers.ts` | derived | 행을 한 번 만들고 바뀐 칸만 고침, Hex·Dec·Bin과 폭 맞춤, 방금 바뀐 줄의 노란 띠·깜빡임·Changed 표, 묶음 띠 그대로. 행은 회로에서 옴(엔진이 찾은 학생의 레지스터: 표시한 레지스터 파일, 아니면 라벨로 번호를 붙인 모든 Register, PC는 Special): 행 묶음이 바뀌면 다시 만듦. 회로 안의 이름을 곁에 흐리게(`$sp  $29`), 오른쪽 클릭(Mark as PC). CP0 접기 없음. `logic/machine.ts`(upstream core에 기댐)는 가져오지 않고 칸 글자는 `src/renderer/app/logic/registers.ts`가 만듦 |
| `src/renderer/shared/memory.ts` | `electron/src/renderer/app/panels/data.ts` | derived | Data 탭을 Memory 패널로: Address · +0~+C · ASCII, 접히는 구간 머리, 0 구간 한 줄, 줄 위 얇은 줄의 라벨·포인터, 칸과 글자 함께 밝히기, 폭 맞춤(`+ ASCII`) 그대로. 줄은 엔진의 MemoryTable(D-140)이 배치해 보내고 글자는 `src/renderer/app/logic/memory.ts`가 씀(upstream `memory-rows`·`memory-text`·`symbols` 대신). 스택 머리에 깊이·최고 수위. 커널 구간·진법 바꾸기 없음. **0 구간 줄의 사실은 일부러 영어**(`~ 0x100fffff · all 0 · 245,756 words`): upstream `data.ts` 137행은 `모두 0`이지만 이 앱은 표 안의 사실(이름·수)을 영어로 쓴다(D-135 14항, D-144) |
| `src/renderer/shared/inspector.ts` | `electron/src/renderer/app/panels/inspector.ts` | derived | Inspector를 Instruction 패널로: 32비트 격자를 색 필드로 묶음, 필드 표 그대로. 필드·이름·값은 이 앱의 엔진(Java 디스어셈블러와 필드 나누기, D-127·D-144)이 줌(upstream 디코더 대신). 명령 설명·주석·목적지 계산(upstream core의 글)과 고정(pin)은 없음. Cycle View의 사이클을 따라감 |
| `src/renderer/shared/panels.css` | `electron/src/renderer/app/app.css` | derived | Registers·Data·Inspector 패널 규칙, 필드 색(`.f-*`)과 형식 표(`.b-R` 등), 바뀜 색 토큰. 같은 명령이면 두 프로그램에서 같은 색. Inspector의 설명·주석·목적지, CP0 접기, Data의 진법 규칙은 뺌. `columnButton`의 `.colbtn` 규칙을 더함 |
| `src/main/main.ts` | `electron/src/main/main.ts` | derived | 창(시스템 제목 줄 없음, `titleBarOverlay`, 매번 최대화), 실행마다 임시 폴더의 프로필과 끝난 뒤 지우기, 창 버튼 자리 색, About. 시뮬레이터 부분은 엔진 클라이언트(`src/main/engine.ts`, 새로 씀)와 충돌 복구(`src/main/recovery.ts`, 새로 씀, N-04)로 바뀜 |
| `src/main/paths.ts` | `electron/src/main/paths.ts` | derived | 이 앱의 고지 목록, 엔진 자리 |
| `src/main/preload.cjs` | `electron/src/main/preload.cjs` | derived | 같은 방식(좁은 `window.app`, 결과 풀기). 이 앱의 호출과 엔진 오류 코드, 엔진을 되살린 알림(N-04) |
| `src/renderer/assets/fonts/Pretendard-Regular.subset.woff2` | `electron/src/renderer/assets/fonts/Pretendard-Regular.subset.woff2` | copy | — |
| `src/renderer/assets/fonts/Pretendard-Medium.subset.woff2` | `electron/src/renderer/assets/fonts/Pretendard-Medium.subset.woff2` | copy | — |
| `src/renderer/assets/fonts/Pretendard-SemiBold.subset.woff2` | `electron/src/renderer/assets/fonts/Pretendard-SemiBold.subset.woff2` | copy | — |
| `src/renderer/assets/fonts/Pretendard-Bold.subset.woff2` | `electron/src/renderer/assets/fonts/Pretendard-Bold.subset.woff2` | copy | — |
| `src/renderer/assets/fonts/OFL-Pretendard.txt` | `electron/src/renderer/assets/fonts/OFL-Pretendard.txt` | copy | — |
| `src/renderer/assets/fonts/D2Coding.woff2` | `electron/src/renderer/assets/fonts/D2Coding.woff2` | copy | — |
| `src/renderer/assets/fonts/OFL-D2Coding.txt` | `electron/src/renderer/assets/fonts/OFL-D2Coding.txt` | copy | — |
| `src/renderer/assets/icons/lucide/LICENSE.txt` | `electron/src/renderer/assets/icons/lucide/LICENSE.txt` | copy | — |
| `src/renderer/assets/icons/lucide/{circle-question-mark, file-plus, folder-open, save, play, square, step-forward, rotate-ccw, settings}.svg` | `electron/src/renderer/assets/icons/lucide/` 같은 이름 | copy | — (`settings`: D-158 Preferences, Hallym MIPS의 Settings와 같은 톱니) |
| `LICENSE.hallym-mips.txt` | `LICENSE` | copy | — |
| `hallym-assets.md` | `electron/src/renderer/assets/hallym/README.md` | derived | 이 저장소 `assets/hallym/`의 파일 이름으로 다시 씀(About › Licenses에 나옴) |
| `tsconfig.json` | `electron/tsconfig.json` | derived | 같은 컴파일러 설정. 이 트리의 폴더 |
| `playwright.config.ts` | `electron/playwright.config.ts` | derived | 같은 설정. 주석이 엔진을 말함 |
| `.gitignore` | `electron/.gitignore` | derived | `native/` 줄을 뺌 |
| `.gitattributes` | `electron/.gitattributes` | copy | — |
| `tools/build-ui.ts` | `electron/tools/build-ui.ts` | derived | 시뮬레이터 worker 없음. `__HALLYM__` 정의 |
| `tools/electron.ts` | `electron/tools/electron.ts` | copy | — |
| `tools/licenses.ts` | `electron/tools/licenses.ts` | derived | 애드온(node-addon-api) 없음. 묶은 패키지가 없으면 그렇게 적음 |
| `tools/e2e-widths.ts` | `electron/tools/e2e-widths.ts` | derived | 이 앱의 창 크기(실습실 1920×1080의 100·125·150 %, 화면 절반) |
| `tools/capture-screens.ts` | `electron/tools/capture-screens.ts` | derived | PNG 메타데이터 빼기, 크기 한도, 마우스·포커스 치우기, `shot()` 그대로. 장면은 이 앱의 것 |
| `tools/mutants.ts` | `electron/tools/mutants.ts` | derived | 임시 폴더에 복사해 돌연변이마다 테스트하는 틀 그대로. 돌연변이 목록은 이 앱의 것, 네이티브 빌드 없음 |
| `tools/package.ts` | `electron/tools/package.ts` | derived | 앱과 엔진(jar 둘, 번들 JRE: N-04)을 모아 electron-builder를 부름. 설치 파일의 옵션은 `tools/package-config.ts`(2.5.0과 같은 안내형 NSIS: `oneClick: false`, 사용자별, 폴더 선택 없음, 권한 상승 없음, 한국어, 시작 메뉴 바로 가기만, 마침 화면의 지금 실행하기, 왼쪽 띠 그림; 블록맵·elevate.exe 없음; N-23, D-148, D-155). `--version`·`--out`·`--win` |
| `packaging/installer.nsh` | `electron/packaging/installer.nsh` | derived | 설치 폴더 이름을 이 프로그램 이름으로, 이 사용자만(묻는 화면 없음), 진행 화면 머리 문구, 마침 화면("설치가 완료되었습니다", "Hallym Circuit Studio 설치를 마쳤습니다…", 지금 실행하기), 진행 막대를 앱 파랑 #0055A5로(`PBM_SETBARCOLOR`), 제거 화면(진행, "제거가 끝났습니다"), 업데이터 사본 지우기(여기까지 upstream 2.5.0, 함수 이름 `Hcs…`). v1.0.x MSI를 UpgradeCode로 찾아 조용히 지우기, `%TEMP%`의 제거 프로그램 사본 지우기, 설치 파일의 나머지 한국어 문구(이름 뒤 조사 없음)는 이 앱의 것(N-23, D-148, D-155) |
| `tools/installer-art.py` | `electron/tools/installer-art.py` | derived | 왼쪽 띠 그림(164×314): 남색, 흰 판 위의 학교 심벌(원형 그대로), 흰 Pretendard 이름. 이 저장소의 심벌·글꼴 파일(`assets/hallym/logo/symbol-basic.svg`, `assets/fonts/pretendard/Pretendard-Bold.otf`), 이름 "Hallym Circuit Studio"는 폭에 맞춰 두 줄. 만든 `packaging/installerSidebar.bmp`·`uninstallerSidebar.bmp`는 이 앱의 것(가져오지 않음) |
| `tools/windows/check-installer-ui.ps1` | `electron/tools/windows/check-installer-ui.ps1` | derived | 이 프로그램의 이름·폴더·실행 파일. 진행 화면 머리 문구, 마침·제거 화면의 문장, 띠의 흰 판(심벌)도 화면에서 확인 |
| `tools/windows/screen-1920.ps1` | `electron/tools/windows/screen-1920.ps1` | copy | — (Windows CI 러너 화면을 1920×1080으로: 설치본 시작 시간을 거기서 잰다) |
| `tests/e2e/harness.ts` | `electron/tests/e2e/harness.ts` | derived | 매번 새 실행 폴더·새 HOME, 크기와 배율 스위치, 파일 대화상자를 테스트에서 답함. 가짜 엔진 |
| `tests/e2e/backdrop-measure.ts` | `electron/tests/e2e/backdrop-measure.ts` | copy | — (시작 화면 배경을 화면에서 재기: 남색 쪽 tint, 흐림) |
| `tests/e2e/backdrop.e2e.ts` | `electron/tests/e2e/start.e2e.ts` | derived | 영상 시험(2.5.0)을 이 앱의 단계·파일 탭으로. 화면 측정을 실습실 PC의 세 배율(1920×1080 100·125·150 %)에서, 처리의 CSS도 읽음. 튜토리얼 부분은 뺌(튜토리얼은 N-18) |
| `tests/unit/start-clip.test.ts` | `electron/tests/renderer/start-clip.test.ts` | copy | — (영상 파일: VP9 한 트랙, 소리 없음, 크기; `start-video.ts`의 인자) |
| `tests/unit/overlay.test.ts` | `electron/tests/renderer/overlay.test.ts` | derived | 같은 테스트. import 경로 |
| `tests/unit/names.test.ts` | `electron/tests/renderer/names.test.ts` | derived | 같은 테스트. import 경로 |

## v2.6.0에서 가져온 것(A-07 2단계, D-169)

1단계(D-167)가 .hmx 참조·필드 색·스크린샷 시계를 맞춘 뒤, 2단계는 창 껍데기의 디자인을 v2.6.0에서 다시 가져왔다(upstream `app.css`·`app.ts`·`panels/welcome.ts`·`logic/overlay.ts`·`api.ts`·`main.ts`·`tests/e2e/backdrop-measure.ts`의 2.5.0 → 2.6.0 변경).

- **시작 화면:** 영상이 창 전체 아래에 깔리고(`.wback` `position: fixed`), 제목 줄과 상태 표시줄은 그 위의 어두운 유리(`rgba(0,20,56,.3)` + `blur(16px)`, 흰 글자·흰 아이콘, 나눔선 없음), 가운데 카드는 반투명 유리(흰색 .82, `blur(18px) saturate(1.2)`, 가벼운 그림자, 캐릭터 168 px, 부제 `--text-2-glass`). `shared/shared.css`, `shared/welcome.ts`.
- **Windows 캡션 단추:** 시작 화면에서는 단추 뒤 조각이 투명(`#00000000`)이고 기호가 흰색이라 유리 위에 바로 앉는다(흰 조각 없음). 그 밖에서는 전처럼 흰색, 또는 대화상자·튜토리얼이 덮으면 그 아래 흰색의 색이고 기호는 남색. `shared/overlay.ts`(copy: `captionPatch`, `FIRST_SCREEN_PATCH`), `app/captions.ts`, `src/main/main.ts`의 `win:overlay`(조각과 기호 둘 다).
- **제목 줄 아이콘:** Hallym MIPS의 다섯(Tutorial, New, Open, Export, Settings)과 같은 자리·같은 모양: `Tutorial`(`circle-question-mark`), `New circuit`(`file-plus`), `Open file`(`folder-open`), `Export Image…`(`image-down`, Hallym MIPS의 Export executable image 자리: 그림 내보내기는 N-21의 것이라 그것이 들어올 때까지·회로가 없을 때는 보이지 않음 — Hallym MIPS가 파일이 없을 때 Export를 숨기는 것과 같다), `Preferences`(`settings`). 그 앞에 이 앱의 `Menu`(원조 메뉴 막대). About은 Hallym MIPS처럼 Preferences 안(About · Licenses)과 Help › About…에 있다.
- **재기:** `tests/e2e/backdrop-measure.ts`(copy)가 영상의 배율을 계산된 transform에서 읽는다(scale 1.06).
- 가져오지 않은 것: v2.6.0 `tests/e2e/backdrop.e2e.ts`의 유리 카드 대비 측정(`docs/PORTING.md` 29의 디자인 후보 비교)은 이 앱의 `backdrop.e2e.ts`가 같은 규칙(모든 프레임에서 부제 4.5:1 이상, 카드 밝기)을 자체 방법으로 본다.

## v2.5.0에서 가져오지 않은 것(일부러 다른 곳, D-155)

`node tools/import-hmips.ts --tag v2.5.0`이 "MERGE"로 알린 `derived` 13개 중 위 표에 반영한 것 말고, upstream이 v2.3.0 뒤에 바꿨지만 이 앱에 들이지 않은 것:

- **실행 이미지 내보내기(.hmx Export, 2.4.0):** `app.ts`(→ `shared/titlebar.ts`·`shared/splitter.ts`의 upstream)·`main.ts`·`preload.cjs`의 Export 버튼·`file:exportImage`·`exportImage`. SPIM 코어로 어셈블해 이미지를 만드는 기능이라(`src/core/hmx.ts`, `src/sim/image.ts`) 가져올 수 없고(D-133 5항), 이 앱은 .hmx를 읽기만 한다(D-138, D-147).
- **제목 줄 가장 좁은 단계의 20 px 아이콘 단추**(`app.css` `.titlebar.tighter .iconbtn`, 2.4.0): upstream의 다섯째 아이콘(Export) 때문이다. 이 앱의 제목 줄 단계(D-135 3항)는 그대로 둔다.
- **`tools/capture-screens.ts`의 JPEG 사진·너비별 시작 화면 장면(`start-<너비>.jpg`)과 `tools/mutants.ts`의 .hmx 돌연변이:** 스크린샷은 이 앱의 고정 이름(`start.png` 등, PNG)을 지키고, 영상은 upstream처럼 3.0초에 멈춰 찍는다(`videoAt`). 돌연변이는 시작 화면과 설치 파일 것만 이 앱에 맞게 옮겼다.
- **`tools/package.ts`의 아이콘 경로·이름:** 이 앱의 것(`assets/hallym/logo/app.ico`).
- **upstream `start.e2e.ts`의 튜토리얼 부분:** 이 앱의 튜토리얼은 N-18이다.
- **`installerSidebar.bmp`·`uninstallerSidebar.bmp`:** 이름이 "Hallym MIPS"라 가져오지 않고 `tools/installer-art.py`로 이 앱의 것을 만들었다.

## Hallym MIPS가 아닌 곳에서 온 것

- **Lucide 아이콘(더한 것, 32개):** `src/renderer/assets/icons/lucide/`의 `undo-2`, `redo-2`, `mouse-pointer-2`, `pointer`, `workflow`, `type`, `square-dot`, `tag`, `crosshair`, `activity`, `fast-forward`, `gauge`, `file-code`, `info`, `x`, `cpu`, `circuit-board`, `house`, 그리고 N-11(모양 편집 도구, Circuits 패널)의 `slash`, `spline`, `waypoints`, `rectangle-horizontal`, `square-round-corner`, `circle`, `pentagon`, `plus`, `import`, `library`, `arrow-up`, `arrow-down`, N-17(D-158) 제목 줄의 `menu`, A-07 2단계(D-169) 제목 줄 Export Image의 `image-down`. npm 패키지 `lucide-static` **1.48.0**의 `icons/`에서 가져와 Hallym MIPS의 아이콘과 같은 모양으로 만들었다(첫 줄 라이선스 주석과 `class` 속성만 뺌, 그림은 그대로: `tools/import-hmips.ts --lucide`). 라이선스는 같은 `LICENSE.txt`(ISC, Feather 유래는 MIT).
- **학교 식별요소:** 로고와 캐릭터는 복사하지 않는다. 화면은 이 저장소의 원본 `assets/hallym/`(Hallym MIPS가 쓰는 것과 바이트까지 같은 파일, `assets/MANIFEST.sha256`)을 그대로 불러 크기만 줄인다. 시작 화면 영상과 정지 그림(`src/renderer/assets/hallym/start/`)은 이 저장소에 원본이 없어 Hallym MIPS v2.5.0의 파일을 바이트 그대로 가져왔다(`copy`, D-155; 고지는 NOTICE와 `hallym-assets.md`).
- **나머지(`src/main/engine.ts`, `rpc.ts`, `engine-locate.ts`, `run-folder.ts`, `protocol.ts`, `src/renderer/app/*`, `src/renderer/shared/band.ts`, `tests/fake-engine/`, 설치 파일의 옵션과 검사 `tools/package-config.ts`·`tools/release-assets.ts`·`tools/windows/state.ts`·`tools/windows/check-install.ps1`, 나머지 테스트):** 이 앱을 위해 새로 썼다.
