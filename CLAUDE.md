# Hallym Circuit Studio — 작업 지침

한림대학교 Micro-architecture 실습도구다. Logisim 2.7.1 엔진을 그대로 돌리고, 화면은 Electron으로 새로 만든다(Hallym Circuit Studio 2, D-132). 무엇을 왜 만드는지는 **`PLAN.md`가 기준**이다. 작업 전에 반드시 읽고, 이 파일과 충돌하면 PLAN.md의 제품 결정을 따른다.

## 0. 이번 범위

- **v2 로드맵 N-00~N-28**(PLAN.md 9.2, `docs/PROGRESS.md`의 N 표)을 모두 끝내고 v2.0.0을 공개하는 것이 이번 범위다. 사용자 추가 지시는 같은 표의 A 항목으로 올린다.
- 화면은 Hallym MIPS Simulator(Electron판, 참고 태그 v2.5.0)와 같은 재료·같은 흐름으로 만든다. 쓰는 법은 원조 Logisim과 거의 같고 기능은 v1 전부다(`docs/v1-feature-parity.md`).
- **Swing 화면은 은퇴했다.** v1.0.3이 마지막 Swing 릴리스이고 태그 `swing-final`로 남는다. Swing·FlatLaf 코드는 N-27에서 지웠다(D-163). 되살리지 않는다.
- **Verilog(PLAN.md 7장, 로드맵 5~9단계)는 구현하지 않는다.** PLAN.md 7.0의 원칙만 지킨다. Verilog 파서, Yosys, ANTLR 의존성을 추가하지 않는다.

## 1. 일하는 방식: 사용자에게 일을 시키지 않는다

- 사용자는 결과만 받는다. 질문하거나 확인을 요청하지 말고, 가장 합리적인 쪽으로 결정하고 기록한다.
- 결정은 `docs/DECISIONS.md`에 한 항목씩 남긴다(날짜, 결정, 이유, 대안, 테스트). 번호는 커밋 직전에 main의 다음 빈 번호로 잡고, 다른 PR과 겹치면 rebase 때 고친다. PLAN.md의 미결정 사항을 풀면 PLAN.md도 함께 고친다.
- 끝날 때까지 멈추지 않는다. CI·빌드·검토를 기다리는 동안 다른 항목을 한다. 컨텍스트가 길어지면 `docs/PROGRESS.md`와 메모리에 진행 상태를 남기고 이어 간다.
- 멈추는 경우는 **되돌릴 수 없고 어느 쪽이든 그럴듯한 결정**뿐이다. 예: 학생에게 배포하기, 사용자가 둔 원본 파일 삭제. 이때는 준비 작업까지 하고 결정만 남긴다. 공개 저장소와 공개 릴리스는 이미 승인됐다.
- 사람이 해야 하는 일은 모두 **GitHub 이슈 하나**(#54, `needs-human` 라벨)에 체크리스트로 모은다. 예: 실습실 PC 설치 확인, 교수님 확인, 학생 배포 안내. 정확한 명령 한 줄을 적는다. 그동안 다른 일은 계속한다.
- 컨텍스트가 끊기면 `docs/PROGRESS.md`, PLAN.md, 이 파일, `docs/DECISIONS.md`만 읽고 이어 간다.

## 2. 절대 규칙

1. **Logisim 시뮬레이션 엔진을 수정하지 않는다.** 값 전파, 넷 계산, 기존 부품 동작을 바꾸지 않는다. 대상 패키지는 `app/src`의 `com.cburch.logisim.circuit`, `.comp`, `.data`, `.instance`, `.std.*`와 `.file` 로딩 규칙이다. 새 기능은 리스너, 새 부품, 엔진 서비스(`kr.ac.hallym.hcs.engine.*`)로 얹는다. 피할 수 없으면 최소 패치 하나로 격리한다. 그리고 `docs/DECISIONS.md`에 이유를 적고 회귀 테스트로 동작 불변을 증명한다. 반사(reflection)는 읽기에만 쓴다. `tools/check-engine-unchanged.sh`와 `tools/check-upstream-markers.sh`가 CI에서 지킨다.
2. **엔진이 권위다. 편집은 의도로 보내고 Logisim 코드가 한다.** 회로 모델의 진짜 상태는 Java 엔진에 있다. 화면은 사본을 들고 엔진이 보내는 변경분으로 맞춘다. 화면은 몸짓을 의도(`edit.*`)로 바꿔 보낼 뿐이고, 실제 변경(선 합치기·나누기, 연결점, 되돌리기 기록)은 엔진이 원조 Logisim의 편집·도구 코드로 한다(D-134, D-146). 그래서 결과 .circ가 원조와 같다. 화면이 모델을 스스로 고치거나 .circ를 짓지 않는다. 새 편집 의도는 복구 저널에 넣고 시험한다(D-142).
3. **`vendor/` 아래 원본은 수정하지 않는다.** 지금은 Logisim 2.7.1 jar(`vendor/logisim-2.7.1/`)뿐이다. `vendor/spim-9.1.24`는 사용자 결정으로 지웠다(D-141, 이 폴더에 한한 예외). 빌드 산출물은 밖에 둔다. `tools/verify-vendor.sh`가 확인한다.
4. **새 부품을 안 쓴 .circ는 원조 2.7.1과 바이트 수준으로 호환되게 저장한다.** 추가 정보는 PLAN.md 7.0의 네임스페이스 방식(`<hcs:ext>`)으로만 넣는다. 사용자가 직접 지정한 정보(터널 색, 신호 그룹, 영역 메모, 스플리터 팔 이름)만 여기에 담는다. 열고 저장만 한 파일은 바뀌지 않는다(D-149).
5. **학교 로고와 캐릭터는 원형 그대로 쓴다.** 다시 그리기, 색 변경, 비율 변경, 요소 추가를 하지 않는다. 가이드라인 PDF와 .ai 원본은 git에 넣지 않는다(15절).
6. **SPIM 코드를 GPL 코드와 섞지 않는다.** 이 저장소에는 SPIM 코드가 없다(D-141). 어셈블은 Hallym MIPS가 하고, 이 도구는 그 실행 이미지(.hmx)를 읽을 뿐이다. Hallym MIPS에서 가져올 때 SPIM에서 나온 파일(`src/core/op-table.ts`, `native/`, `src/core/*`, `src/sim/*`와 거기에 기대는 파일)은 가져오지 않는다(D-133 5항, `electron/tests/unit/origin.test.ts`). SPIM이 낸 출력은 시험 자료로만 둔다(`tests/spim-oracle/LICENSE`).
7. **도구는 부품 라이브러리와 편집 도구를 주고 "동작하지 않는 회로"만 알린다.** 동작하지만 잘못된 회로(값이 0/1로 흐르는데 결과만 틀림)는 판단하지도, 고치지도, 정답과 비교하지도 않는다. 학생 설계에 관여하지 않는다: 분기 목적지 계산과 PC·`$sp` 초기화는 학생 회로의 몫이고, 도구는 실행 이미지의 기계어를 그대로 메모리에 올려 주소의 워드를 내보낼 뿐이다. 도구는 학생 레지스터에 쓰지 않는다(PLAN.md 1장, D-010, D-019, D-118).

## 3. 구조: 두 프로세스

```
electron/          # 화면. Electron main + 렌더러(TypeScript, UI 프레임워크 없음)
  src/main/        #   창, 엔진 자식 프로세스, JSON-RPC 클라이언트, 복구 파일, 실행 폴더
  src/renderer/    #   app/(패널·대화상자), canvas/(그리기, 부품 렌더러 등록표), shared/(Hallym MIPS 공유)
  tests/           #   unit(node --test), e2e(Playwright), fake-engine, fixtures
  tools/           #   build-ui, capture-screens, mutants, package, import-hmips, perf …
  docs/screens/    #   고정 이름 스크린샷과 README
engine/            # Java 엔진 서버(hcs-engine.jar): headless Logisim 2.7.1 + kr.ac.hallym.hcs.engine.*
app/               # Logisim 2.7.1 포크 소스(원조 그대로 + // HCS: 줄)와 GUI 없는 kr.ac.hallym.hcs.app.*(엔진이 씀)
lib-mips/          # 트랙 A: 원조 2.7.1용 MIPS 부품 JAR(hcs-mips.jar, --release 8). 엔진도 같은 jar를 번들
vendor/logisim-2.7.1/   # 원본 jar, 수정 금지
assets/            # 로고·캐릭터·글꼴 파생 파일
tests/             # circ(엔진 회귀), parity(편집 동등성 골든), hmx, mips, spim-oracle, disasm …
docs/              # DECISIONS, PROGRESS, engine-api(규약), 조사·설계 메모
tools/             # 저장소 검사(vendor, assets, 엔진 불변, 원조 표시), 트랙 A 포장
resources/, ref/   # 사용자 원본과 참고 클론. .gitignore 대상
```

- **Electron main이 엔진을 띄운다.** 엔진은 앱당 하나이고 여러 파일(탭)을 함께 연다. stdio 위 JSON-RPC 2.0, 한 줄에 한 객체다. 메서드·식별자·값 글자·알림의 계약은 `docs/engine-api.md`다(D-133).
- **렌더러는 엔진과 직접 말하지 않는다.** contextIsolation, 렌더러 nodeIntegration 없음, preload로만 API를 연다. 창이 부를 수 있는 메서드는 정해 둔 목록뿐이고 경로를 넘기지 못한다(D-135).
- **엔진은 headless다**(`java.awt.headless=true`). 설치본은 jlink로 줄인 JRE 21을 번들한다(D-142). 엔진이 죽으면 알리고 다시 띄우며, 열린 파일은 저널로 되살린다(D-142).
- **가짜 엔진**(`electron/tests/fake-engine/`)은 규약만 아는 Node 대역이다. 화면 e2e와 스크린샷이 쓴다. 데이터가 중요한 곳의 답은 진짜 엔진이 만든 fixture다.
- **그리기:** Canvas 2D, 부품 모양은 벡터 정의의 렌더러 등록표 하나(`electron/src/renderer/canvas/registry.ts`, D-137). 크기와 포트 위치는 엔진 값 그대로다. 그림 내보내기도 같은 정의를 쓴다.

## 4. Hallym MIPS 공유 규칙

- Hallym MIPS Simulator(`ars2323/hallym-mips-simulator`, 같은 저자, BSD-3-Clause)의 `electron/`에서 화면 코드·디자인 값·글꼴·아이콘·도구·테스트 틀을 가져와 쓴다. 기준 태그는 **v2.5.0**이다(D-155). 다른 것을 쓰려면 이유를 DECISIONS에 적는다.
- **공유 화면 부품은 한 폴더에 모은다:** `electron/src/renderer/shared/`(dom, ui, ask, welcome, backdrop, notice, about, titlebar, splitter, registers, memory, inspector …).
- **가져온 파일마다 출처를 적는다:** `electron/ORIGIN.md`에 여기 경로, Hallym MIPS 경로, 방식(`copy` = 바이트 그대로, `derived` = 고쳐 씀), 바꾼 곳. 라이선스 원문은 `electron/LICENSE.hallym-mips.txt`, 고지는 NOTICE와 About › Licenses다.
- **다시 가져오기:** `cd electron && node tools/import-hmips.ts [--tag <태그>]`. 대상 목록은 그 파일의 `TAKEN`이다. `copy`는 덮어쓰고, `derived`는 바뀐 것만 알려 준다. 손으로 합친 뒤 `--record`로 `tools/hmips-sums.json`을 갱신한다. `--check`는 CI와 `origin.test.ts`가 매번 돌린다.
- **.hmx 명세와 골든만 v2.4.0에 고정한다**(`docs/hmx.md`, `tests/hmx/hallym-mips-v2.4.0/`, D-138). 명세 원본은 Hallym MIPS `docs/hmx-format.md`다. 전문을 복사하지 않는다.

## 5. Git·GitHub 관리

git과 gh는 설치·로그인돼 있다. 저장소 관리는 전부 직접 한다.

- **저장소:** `ars2323/hallym-circuit-studio`, public(D-017). 가이드라인 PDF·`.ai`·`resources/`·비밀 값이 기록에 들어가지 않게 한다.
- **마일스톤과 이슈:** v2 항목은 마일스톤 `v2.0.0`에 항목마다 이슈 하나다. `docs/PROGRESS.md`의 N·A 표(ID | 이슈 | 상태 | PR | 비고)를 항목이 끝날 때마다 갱신한다. 미결정 사항은 `question` 라벨 이슈로 만들고, 풀리면 결론을 달고 닫는다.
- **브랜치와 PR:** `main`에 직접 push하지 않는다. `feat/…`, `fix/…`, `docs/…`, `chore/…` 브랜치를 만들고 작업 단위마다 PR을 연다. PR 본문은 한국어로 쓰고 `Closes #N`을 단다.
- **머지 절차:**
  1. CI의 모든 작업이 초록인지 확인한다. 건너뛴 검사는 통과가 아니다(이유와 이슈 번호를 남긴다).
  2. 모든 PR은 머지 전에 compat-reviewer(`.claude/agents/compat-reviewer.md`)를 돌린다. diff만 보고 2절 절대 규칙 위반과 테스트 없는 기능 변경을 보고한다.
  3. **화면이 바뀌는 PR**(화면이 목적인 PR, 그리고 목적과 상관없이 `electron/docs/screens/`의 이미지를 바꾸는 모든 PR)은 ui-reviewer(`.claude/agents/ui-reviewer.md`, `docs/UI-CHECKLIST.md`)도 돌린다. 바뀐 스크린샷만 한 명이 본다(v2 지시 1절: 세 범위 검토·전체 재촬영은 하지 않는다).
  4. 위반이 있으면 고친 뒤 다시 돌린다. 위반 0건(ui-reviewer는 "막음" 0건)이어야 머지한다. "확인 필요"는 PR 본문에 판단 근거를 적는다.
  5. `gh pr merge --squash --delete-branch`로 머지한다.
- **커밋:** 영어 명령형 한 줄 제목(`Add Data Memory component`)에 필요하면 본문을 단다. 작은 단위로 자주 커밋한다.
- **CI(`.github/workflows/ci.yml`):** Linux 빌드·단위 테스트·엔진 회귀·편집 동등성·저장소 검사(`linux`), 트랙 A jar를 Java 8에서(`track-a-java8`), 화면 단위·e2e(`electron`), 번들 JRE와 진짜 엔진 e2e(`runtime`, Linux·Windows), Windows 설치 파일과 설치본 e2e(`setup-exe`, `setup-e2e`, `setup-upgrade`), 릴리스 파일 규칙(`release`)을 매 push·PR마다 돌린다.
- **.gitignore:** `resources/`, `ref/`, `build/`, `.gradle/`, IDE 파일, 에이전트 worktree.
- **줄 끝:** 시험이 바이트로 비교하는 골든과 명세(`tests/hmx/**`, `tests/parity/**`, `tests/circ/**`, `tests/mips/*.circ`, `tests/jarlib/**`, 시험이 읽는 docs)는 `.gitattributes`에서 `-text`다. 새 바이트 비교 파일을 더하면 여기에도 더한다(D-154).

## 6. 확인 규칙

- **자동 테스트가 주된 안전망이다.** 모든 동작에 테스트를 둔다. 테스트 없는 기능 PR은 머지하지 않는다.
  - Java: 엔진 API 단위 테스트, 엔진 회귀(`tests/circ`를 표준 2.7.1 jar `-tty`와 대조), 바이트 호환(`OpenSaveParityTest`: 새 엔진 경로는 `screenOpens`에 더한다, D-149), 로더·디스어셈블러·진단·기록 테스트, 새 핵심 패키지는 PIT.
  - 동등성: 편집 동등성(N-01 골든과 바이트 일치, D-136·D-159), 기하 동등성(엔진 포트 위치 = 렌더러 포트, D-137), 조작 동등성 표(`docs/interaction-parity.md`)의 줄마다 e2e.
  - TypeScript: `node --test` 단위 테스트, 새 로직은 돌연변이(`npm run test:mutants`).
  - e2e(Playwright, 가짜 엔진): 주요 흐름을 FHD 100·125·150 %에서 본다. 주요 흐름마다 진짜 엔진 e2e(`HCS_E2E_REAL_ENGINE=1`)를 하나 이상 둔다.
  - `docs/v1-feature-parity.md`와 `docs/interaction-parity.md`의 e2e 칸을 채운다.
- **정상 회로 Messages 0건**은 매 PR 필수다.
- **가볍게 확인한다.** 세 범위 검토, 전체 재촬영 반복, 절차 증명은 하지 않는다. 한 항목에서 확인 시간이 구현 시간보다 길어지면 멈추고 원인을 적은 뒤 줄여서 계속한다.
- **기대 결과는 고정값이다.** "N번 중 M번" 같은 임계값 테스트를 쓰지 않는다. 흔들리면 원인을 없앤다.

## 7. 스크린샷 규약

- 스크린샷은 `electron/docs/screens/`에 **고정 이름**으로 둔다. 하위 폴더는 없다. 매 라운드 같은 이름으로 덮어쓴다.
- **도구로만 찍는다:** `cd electron && xvfb-run -a -s '-screen 0 2400x1400x24' npm run screens`(`tools/capture-screens.ts`, 가짜 엔진, 같은 코드면 같은 픽셀). 손으로 찍지 않는다.
- 기본은 1920×1080 실습실 PC에서 최대화한 창(CSS 1920×1032, 100 %)이다. 커서·툴팁·hover·초점이 없고 개인 경로가 보이지 않는다. 한 장 1.5MB 이하다.
- `electron/docs/screens/README.md`에 파일별 표(파일, 볼 것, 찍는 법), 찍는 조건을 둔다. 새 장면을 더하면 표에 줄을 더한다. 내용이 바뀐 이미지만 커밋한다.
- **보고와 PR의 링크는 커밋 SHA로 고정한 raw 주소만 쓴다:** `https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/<커밋 SHA>/electron/docs/screens/<이름>.png`. 브랜치 이름으로 걸지 않고, push한 뒤에 보고한다.
- v1의 orphan 브랜치 `review-shots`와 `docs/SCREENSHOTS.md`는 v1.0.x 기록으로만 남는다. 새 스크린샷을 거기 올리지 않는다.

## 8. 보고 형식

- **작업 세션 끝:** 한국어로 짧게. 끝난 것, 머지한 PR, 다음 할 일, `needs-human` 변경. 화면이 바뀌었으면 끝에 "스크린샷" 절(7절 링크).
- **중간 보고:** alpha를 올릴 때마다 짧게 한다. 릴리스 URL, 된 것, 스크린샷 5~10장(한 줄에 링크 하나와 볼 것 한 줄).
- **최종 보고(v2.0.0 뒤 한 번):**
  - 첫 줄 `커밋: <main SHA>`, 다음 줄 릴리스 URL
  - N 항목별 한 줄(PR, 결정 번호)
  - 판단이 필요했던 것과 근거, 고치지 않기로 한 것과 이유
  - 검증은 표와 수치로: 테스트 수, 편집 동등성 일치 수/전체, 기하 검사 수, 튜토리얼 단계×배율, 성능 측정값, 설치·제거, 해시, v1 기능 대조표 완료 수/전체
  - 걸린 시간, OPEN-ISSUES 닫은 수·남은 수
  - 스크린샷 5~10장(Hallym MIPS와 나란히 비교한 그림 포함: 시작 화면, 대화상자, 빈 상태, 튜토리얼 카드, 패널 머리와 탭, 상태 표시줄)
  - needs-human 변경

## 9. 미해결 목록(OPEN-ISSUES)

- 릴리스를 막지 않아 넘긴 것과 결정은 났지만 뒤에서 할 일은 `docs/OPEN-ISSUES.md`에 모은다.
- ui-reviewer의 **넘김** 등급(3px 이하 간격, 정렬 같은 모양 다듬기)은 고치지 않고 여기에 적는다. **막음**은 그 PR에서 고친다(`docs/UI-CHECKLIST.md` 판정 등급).
- 고치면 줄을 지우지 않고 상태를 "고침(PR 번호)"으로 바꾼다. 닫을 때는 PR 번호나 근거를 적는다.
- v1 화면에서 나온 항목은 "v2에서 다시 만듦"으로 두고 N-25에서 v2 기준으로 다시 본다. v2.0.0 전에 남은 수 0이 목표다.

## 10. 화면 문구 규칙 (N-20, D-135 14항)

- **사물 이름은 영어, 학생에게 하는 문장은 한국어다.** 이름: 패널·탭·열 머리·버튼·부품·포트·속성·메뉴, 상태 표시줄의 사실(`Ready`, `Cycle 2`, `Running (64 Hz)`, `3 messages`). 문장: 빈 상태, 대화상자 본문, 띠, 상태 표시줄의 알림 문장. 이름은 원조 2.7.1 용어 그대로이고 용어는 `docs/GLOSSARY.md`를 따른다.
- 시작 카드의 선택지는 Hallym MIPS처럼 한국어로 둔다. 대화상자 제목은 문장이면 한국어, 이름이면 영어다. 대화상자 버튼은 이름이라 영어이고, 같은 명령은 어디서나 같은 이름이다(Close, Cancel, Try Again, Discard).
- 화면에 한국어 "한림"을 쓰지 않는다(Hallym University).
- "~하면 됩니다"류를 쓰지 않는다.
- **이름 바로 뒤에 조사를 붙이지 않는다.** 이름은 쌍점 뒤나 따로 된 줄에 둔다(`File: lab3.circ`).
- 한국어가 들어가는 모든 곳에 `word-break: keep-all`. 문장 속 영어 이름은 줄바꿈 없는 칸(`codeText()`)으로 감싼다.
- 로더·파일 오류는 무엇이 잘못 → 무엇을 할지를 말한다. 줄 번호가 있으면 줄 번호 → 무엇이 잘못 → 무엇을 할지다. 엔진의 개발자 문구를 그대로 옮기지 않는다.
- 회로 진단은 원인 한 곳, 학생이 붙인 이름, 사실과 위치까지만 말한다(PLAN.md 4.4). 가까운 이름은 확신할 때만 짚는다.
- 주소·기계어·값은 D2Coding으로 써서 0과 O를 가른다.
- 문구는 한국어·영어 리소스로 관리한다. 문구 검사(grep·조사 규칙)는 CI에 둔다.
- **캐릭터는 오류 옆에 두지 않는다.** 오류 대화상자나 띠가 떠 있는 동안 화면의 캐릭터를 모두 숨긴다. 진단 메시지에도 쓰지 않는다.

## 11. 실습실 PC 규칙 (N-19, D-152)

- **껐다 켜면 모두 기본값이다. 앱은 아무것도 기억하지 않는다.** 창 크기·배치·분할선·배율·최근 파일·단축키·튜토리얼 진행·설정을 디스크에 남기지 않는다. 설정 창에는 "이번 실행에만 적용됩니다"를 적는다.
- 켤 때마다 작업 영역에 맞춰 최대화한다.
- Chromium의 사용자 데이터는 실행마다 임시 실행 폴더(`<temp>/HallymCircuitStudio/run-<pid>-<시각>`)에 두고 끝나면 지운다(D-135 11항, D-148 6항).
- **엔진도 디스크 설정을 모른다.** 메모리 전용 환경설정이고, 원조 Logisim의 디스크 설정을 읽지도 쓰지도 않는다(D-134 10항).
- **비정상 종료 복구 파일은 학생 파일 옆에만 둔다.** 디스크에서 연 파일에만, 그 폴더에 쓸 수 있을 때만 둔다. 그 파일을 열 때만 묻는다. 저장·닫기·정상 종료에 지운다.
- 자동 검사: 모두 바꾸고 다시 켜서 기본값인지 본다. Windows에서 실행 전후 레지스트리·`%APPDATA%`·`%LOCALAPPDATA%`(설치 폴더 제외)·`%TEMP%` 변화 0(`setup-e2e`, D-148 11·12항).

## 12. 튜토리얼 원칙과 기준 화면 크기

- **기준 화면:** 실습실 PC는 최소 FHD다. 반드시 통과할 크기는 1920×1080의 배율 100 %·125 %·150 %다. 1366×768 계열은 참고용이다. 스크린샷 기본은 CSS 1920×1032(작업 표시줄 48px를 뺀 최대화 창)다.
- **튜토리얼**은 Hallym MIPS 튜토리얼 엔진을 따른다(N-18, 트랙과 단계는 PLAN.md 12장).
  - 단계는 배열 하나로 둔다. 단계마다 제목과 본문(하나의 글), 밝힐 패널, 박스 대상, 종류(설명·실습), 실습이면 기다릴 것, 결과 단계 여부를 선언한다.
  - 카드에는 "3 / 16", 그만두기, 제목, 본문, 작은 캐릭터(카드 먼 쪽 끝), [이전]·[다음]이 있다.
  - 설명 단계는 [다음]으로 넘어간다. 실습 단계에는 [다음]이 없고, 본문 마지막 문장이 무엇을 하면 넘어가는지 말한다. [건너뛰기]는 몇 초 뒤 나타나 동작을 대신한다.
  - 결과가 눈에 보이는 실습은 같은 단계에서 카드만 바꿔 결과를 짚고 [다음]을 기다린다.
  - 두 겹 강조: 대상 패널 전체를 밝게, 대상에 박스, 밖은 어둡게. 대상이 아닌 곳은 눌리지 않고, 요구하지 않은 키는 먹지 않는다.
  - 카드는 박스를 절대 덮지 않는다. 대상을 실제로 보이게 맞추고(탭, 스크롤, 배율, 접힌 칸), 한 일을 로그로 남긴다.
  - 예제는 트랙마다 한 파일이다. 읽기 전용으로 열고 끝나면 내린다(디스크에서 불변). 학생 파일의 저장 안 된 변경은 먼저 묻고, 끝나면 원래대로 돌려놓는다.
  - 진행을 저장하지 않는다. 켤 때마다 교과목 선택부터다. 키는 → ← Esc(묻고 그만둠)이고, 시뮬레이션 중 Esc는 정지다.
  - 판정은 엔진 이벤트(모델·시뮬레이션·선택)로 한다.
  - e2e는 두 트랙의 모든 단계에서 다섯 가지를 본다: 대상 패널이 통째로 밝음, 밖이 정확히 어두움, 박스가 대상에 맞음, 밝아도 대상 아닌 곳은 안 눌림, 카드가 박스를 덮지 않음.

## 13. 배포 규칙

순서와 명령은 `docs/release.md`를 따른다.

- **Windows 배포물은 setup exe 하나다:** `HallymCircuitStudio-<버전>-win-x64-setup.exe`(electron-builder NSIS, 사용자별 안내형 설치, 엔진과 번들 JRE 포함, D-148·D-155). 앱 zip과 MSI는 올리지 않는다. 함께 올리는 것은 트랙 A 파일(`hcs-mips.jar`, 트랙 A zip)과 이름에 `guide`가 든 안내 PDF뿐이다. 규칙은 `electron/tools/release-assets.ts` 하나이고 CI가 올리기 전·뒤·게시 때 본다.
- 코드 서명이 없다. 릴리스 노트와 안내서에 SmartScreen 안내(추가 정보 → 실행)를 넣는다(`docs/install-windows-ko.md`).
- **사전 릴리스:** `v2.0.0-alpha.N`은 GitHub 사전 릴리스다(Latest 아님). v2.0.0은 모든 항목이 끝난 뒤 공개 릴리스(Latest)로 올린다.
- **옛 릴리스는 지우지 않는다.** v1.0.x 릴리스와 자산은 게시한 그대로 둔다.
- **태그는 버전을 올린 커밋에 단다.** 그 커밋의 검사에서 문제가 나왔을 때만, 버전을 바꾸지 않고 그 문제만 고친 초록 후손 커밋에 달 수 있다. 이때 모든 검사를 태그 커밋에서 다시 돌리고, 보고에 두 커밋(버전을 올린 커밋, 태그 커밋)을 적는다(D-154).
- **크기와 해시는 공개된 자산의 것만 의미가 있다.** NSIS가 빌드 시각을 넣어 설치 파일 바이트가 빌드마다 다르다. PR·main 실행에서 만든 파일의 크기·SHA-256을 릴리스 값으로 적지 않는다. 노트와 보고의 값은 릴리스에 붙은(공개 주소에서 받은) 파일의 것이다(D-154).
- **배포 후 검증:** 공개 주소에서 setup exe를 받아 해시 대조 → 깨끗한 Windows 러너에 조용히 설치 → 시작 화면 → 두 트랙 튜토리얼 [건너뛰기]로 끝까지 → 예제 .hmx 불러오기 → factorial 실행과 Console 확인 → ref-mips 열기와 N Cycles → 종료 뒤 레지스트리·AppData 변화 0 → 제거.
- 바이트 비교 골든의 `.gitattributes -text`(5절)가 Windows CI에서도 통과해야 태그를 단다.

## 14. 빌드 환경

- **Java:** Gradle(wrapper)과 toolchain 자동 다운로드를 쓴다. JDK를 sudo로 설치하지 않는다. 명령줄에서 `java`를 직접 부를 때는 toolchain이 받은 JDK 21을 `JAVA_HOME`으로 먼저 export한다.
  - `lib-mips`: `--release 8`. 학생 PC의 원조 2.7.1이 어떤 JRE에서 돌지 모르므로 보수적으로 잡는다. 외부 의존성 없는 단일 jar다. 두 트랙 공용 소스는 `lib-mips/src/shared/java`(D-125).
  - `app`, `engine`: JDK 21. `./gradlew :engine:stage :engine:test`로 엔진 jar(`engine/build/stage/`)와 테스트, `:engine:runtime`으로 jlink JRE를 만든다.
- **Node:** 22.18 이상. `cd electron && npm ci`, `npm run typecheck`, `npm test`, `xvfb-run -a npm run e2e`, `npm run electron`(소스 트리의 엔진으로 실행).
- **원조 2.7.1 jar 실행:** `java -jar vendor/logisim-2.7.1/logisim-generic-2.7.1.jar file.circ -tty table`로 GUI 없이 회로를 돌린다. 엔진 회귀 테스트의 기준 엔진이다.
- 개발과 테스트는 Linux에서 한다. Windows 설치 파일은 CI의 Windows 러너에서 만든다.

## 15. 리소스 처리

원본 zip은 `resources/`에 그대로 두고(gitignore) 파생 파일만 `assets/`에 커밋한다. `tools/import-assets.py`가 만들고 `tools/verify-assets.sh`가 `assets/MANIFEST.sha256`과 대조한다.

- **로고(`assets/hallym/logo/`):** Hallym MIPS `assets/ci/`의 원본을 쓴다. 매뉴얼 페이지 JPG는 설명 글자가 섞여 앱에 쓰지 않는다. 앱 아이콘과 로고 배치는 Hallym MIPS의 선례를 따른다.
- **캐릭터(`assets/hallym/character/`, 하람&하리):** 영문 슬러그 이름(`haram-hari-greeting.png` 등)이다.
  - 쓰는 곳은 시작 화면, 튜토리얼 카드, 정보 창, 빈 Canvas 안내, 질문·요약 대화상자 정도로 아낀다.
  - 오류 진단 메시지와 오류 대화상자·띠에는 쓰지 않는다(10절).
  - 가이드라인을 지킨다: 요소 추가, 선·비율·색 변경 금지, 복잡하거나 비슷한 색 배경 금지(영상 위에 직접 두지 않는다), 최소 여백 확보.
  - 가이드라인 PDF는 외부 노출을 금하는 문서이므로 git에 넣지 않는다.
- **시작 화면 영상:** Hallym MIPS v2.5.0의 `start.webm`·`start.jpg`를 바이트 그대로 쓴다. 출처와 처리는 NOTICE와 `electron/hallym-assets.md`에 적는다(D-155).
- **글꼴:** 화면은 Hallym MIPS에서 가져온 Pretendard 부분집합과 D2Coding(woff2, OFL)을 쓴다. `assets/fonts/pretendard/`의 OTF는 설치 파일 띠 그림(`electron/tools/installer-art.py`)이 쓴다.
- NOTICE에 서드파티 라이선스를 적는다. 학교 식별요소는 "Hallym University 소유, 상업적 사용 금지, 대학의 공식 제품이 아님"으로 적는다.

## 16. 코드 원칙 (Verilog 대비 포함)

- 새 Java 코드는 `kr.ac.hallym.hcs.*`에 둔다. 엔진 서비스는 `kr.ac.hallym.hcs.engine.*`, 엔진이 쓰는 GUI 없는 v1 코드는 `app/`의 `kr.ac.hallym.hcs.app.*`, 두 트랙 공용은 `kr.ac.hallym.hcs.mips.*`다. 포크 원본 패키지(`com.cburch.*`)의 변경은 최소로 하고, 바꾼 곳에는 `// HCS:` 주석을 단다.
- 진단, 기록 엔진, 경로 계산은 화면이 아니라 회로 모델을 입력으로 받는다. 엔진 API로 부르고 GUI 없이 테스트한다.
- 부품·서브회로·터널·포트 이름을 다루는 공용 식별자 유틸리티를 하나 둔다. 경로 표기는 `datapath › PC`이다.
- 부품 종류별 처리는 등록표 하나에 모은다: 엔진 쪽 등록표, 화면 쪽 부품 렌더러 등록표(향후 Verilog 매핑표가 붙을 자리).
- 사용자에게 보이는 문구는 한국어·영어 리소스로 관리한다. 진단 문구는 PLAN.md 4.4의 원칙을 지킨다.
- 테스트 없는 기능 PR은 머지하지 않는다.
