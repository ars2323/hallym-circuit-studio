# Hallym MIPS Simulator 참고 조사 (0단계, #5)

`gh repo clone ars2323/hallym-mips-simulator ref/hallym-mips-simulator`(커밋 `8ebd3c9`, 1.2.4 직후)를 읽고 이 프로젝트가 재사용할 것을 정리했다. `ref/`는 git에 넣지 않는다. 아래 경로는 따로 적지 않으면 `ref/hallym-mips-simulator/` 기준이고, `vendor/`는 이 저장소의 `vendor/spim-9.1.24/`다.

## 결론

| 주제 | 결론 | 반영 |
| --- | --- | --- |
| SPIM 코어 | `ref/CPU/`와 `vendor/spim-9.1.24/CPU/`는 `diff -rq` 결과 완전히 같다 | `hcs-asm`은 vendor `CPU/`를 링크한다. 같은 설정이면 Hallym MIPS와 기계어가 같다 |
| 어셈블 기본값 | Hallym MIPS = 원본 QtSpim: bare 끔, 의사 명령어 켬, 지연 분기·지연 로드 끔, mapped I/O 끔, **예외 처리기(내장 `CPU/exceptions.s`) 켬** | `hcs-asm`은 과제 표준 설정(예외 처리기 끔)을 기본값으로 하고 모든 설정을 JSON `settings`에 남긴다. 예외 처리기를 켜면 학생 코드가 `0x00400024`부터라는 점은 #42에서 다룬다 |
| 디자인 토큰 | `docs/design/tokens.md`보다 코드 `QtSpim/edu/theme/tokens.h`가 최신이다. 라이트 전용 | Swing 이식(#21)은 `tokens.h` 값을 쓴다. 다크 모드는 새로 설계하고 대비 근거를 남긴다 |
| 변경된 레지스터 값 | "굵은 청록색"이 아니라 굵기 없이 글자 `#00736F` + 배경 `#E6F6F5`(1.2.0부터). 고정폭 열에 굵기를 쓰면 정렬이 깨진다 | 레지스터 패널(#32)·다중 진법 프로브(#14)도 같은 규칙. PLAN.md 5.1 문구를 고친다 |
| 로고 원본 | `assets/ci/marks/*.svg`가 마크별로 잘라 치수선을 지운 결과다. 자르기 스크립트가 없어 다시 만들 수 없다 | 로고는 이 SVG를 그대로 가져와 쓴다(#7). PNG는 SVG에서 렌더해 커밋한다 |
| 로고 배치 선례 | 앱 아이콘: 흰 둥근 타일(라운드 18%, 1px `#E1E5EA`)에 심볼 기본형 76%. 스플래시: 시그니처 국영문 좌우조합 260px. About: 엠블럼 A 112px + 로고타입 국영문 220px | 앱 아이콘과 About에서 같은 선례를 따른다 |
| 캐릭터 | Hallym MIPS에는 캐릭터 사용 선례가 없다 | 이 프로젝트가 처음 쓴다. CLAUDE.md 8절 사용처만 쓴다 |
| 학교 UI 규정 | ref에는 규정 PDF가 없고, 웹페이지 원문을 `assets/ci/manual/README.md`에 옮겨 두었다 | 같은 방식으로 출처와 확인 날짜를 남긴다 |
| Windows 빌드 | `ilammy/msvc-dev-cmd@v1`(x64, toolset 14.29) + `choco install winflexbison3`. CPU/는 MSVC에서 `-Zc:strictStrings-`, `_CRT_SECURE_NO_WARNINGS`, `/utf-8`이 필요하다 | `hcs-asm.exe` CI(#17) |
| 어셈블 일치 테스트 | 오라클 테스트는 정답을 코어에서 얻는다. 프런트엔드 스텁, 로컬 라벨 확보 로더, `snapshot()` 추출 방식을 거의 그대로 쓸 수 있다 | `hcs-asm`(#6) |

---


---

## 1. 디자인 토큰 (`docs/design/tokens.md`, 실제 값은 `QtSpim/edu/theme/tokens.h`)

### 1.0 구조와 우선순위

| 절 | 내용 | 줄 |
|---|---|---|
| 머리말 | 학교 UI 규정 원문 인용(비상업, 변형 금지), 시그니처 배치 규칙 | tokens.md:3-13 |
| §1.1 | CI 4색(Pantone·CMYK·근거 파일·채택 HEX) | :15-27 |
| §1.2 | 파생 색(틴트, AA용 진한 글자색, 중립) | :29-55 |
| §1.3 | 색 배치 규칙(어디에 어떤 토큰) | :57-72 |
| §1.4 | WCAG 대비 표 | :74-105 |
| §2 | 글꼴(Pretendard / D2Coding), 타이포 스케일 | :107-128 |
| §3 | 간격·크기(4px 단위) | :130-142 |
| §4 | 타입 배지 R/I/J/FR/FI/CP0 | :144-157 |
| §5 | 그 밖의 요소(띠, 문법 강조, 스플래시, About, 앱 아이콘, 튜토리얼) | :159-178 |
| §6 | 구현: `tokens.h` + `light.qss`(`@name@` 치환) | :180-182 |
| §7 | 결정 기록(PC 행, 변경값, 글꼴, 1366×768) | :184-189 |

**다크 모드는 없다.** 라이트 전용이며, Windows 제목 표시줄도 다크 시스템 테마에서 라이트로 강제한다(`QtSpim/edu/theme/edu_theme.cpp:118-141`, DWM 속성 19/20=FALSE, 35/36/34=캡션 흰색·글자 navy·테두리 border). `docs/FUTURE.md:9`에 "어두운 테마"가 아이디어로만 있다. **Swing에서 다크를 넣으려면 새로 설계해야 한다**(아래 1.7에 제안만 적음).

**주의: 문서와 코드가 어긋난 곳.** 코드가 최신이다. 이식할 때는 `tokens.h` 값을 쓴다.
- `color.text.2`: tokens.md:45는 #5B6B7B, 코드 `tokens.h:36`은 **#5A6472** (ARCHITECTURE §12 78번, 1.0.1에서 AA 맞추려 바꿈. 대비 표 tokens.md:90은 이미 #5A6472).
- `color.text.muted`: tokens.md:43·168은 #8A94A0, 코드 `tokens.h:37`은 **#65707E** (같은 78번).
- 변경된 레지스터 값: tokens.md:65·187, PLAN.md:259는 "teal.text SemiBold, 배경 없음". 1.2.0부터 코드는 **굵기 없이 글자색(설정값, 기본 #00736F)과 teal.tint #E6F6F5 셀 배경**이다(`QtSpim/edu/edu_register_model.cpp:155-169`, ARCHITECTURE.md:819 96번: "D2Coding에는 볼드가 없어 Windows가 합성하면 폭이 넓어져 열 정렬이 깨진다"). → **"굵은 청록색"은 지금 기준으로 틀린 설명이다.** Swing에서도 고정폭 열에는 굵기를 쓰지 말 것.
- 앱 아이콘: PLAN.md:294, ARCHITECTURE:789 66번은 "16~32 심볼, 48 이상 엠블럼"이지만 1.0.1부터 **흰 타일 + 심볼 기본형, 모든 크기 같음**(ARCHITECTURE:799 76번, `tools/make-theme-icons.py:39-76`, tokens.md:176). `edu_theme.cpp:148-150`의 주석은 옛 설명 그대로다.
- 행 높이: PLAN.md:267은 20px. 코드는 `kRowHeight = 16`을 최소값으로 두고 `max(글리프 높이, 16)`(`edu_register_view.cpp:29-46`). 실측은 20px(ARCHITECTURE:833).
- 스플래시: tokens.md:174는 480×300·1.5초·진행 바. 코드는 **480×360**, 튜토리얼/바로 시작 두 버튼이 있고 선택을 기다린다(`edu_splash.cpp:28-31`, 진행 바 없음, 문구 "처음이라면 튜토리얼을 보고 시작하세요").

### 1.1 CI 색 (tokens.md:19-25, tokens.h:25-28)

| 토큰 | 코드 상수 | HEX | 근거 | 쓰임 |
|---|---|---|---|---|
| `color.navy` | `kNavy` | **#00205B** | PANTONE 281 C, `A2/a-2-1.ai` 22행 CMYK 100/80/0/40 | 제목·그룹 헤더·아이콘 기본·틴트 위 글자 |
| `color.blue` | `kBlue` | **#0055A5** | PANTONE 2945, `A1/a-1-1.ai` 22행 CMYK 100/60/0/0 | 활성 탭, 주요 버튼, PC 막대, 라벨, 지시어 |
| `color.teal` | `kTeal` | **#00A9A5** | PANTONE 326, `A1/a-1-1.ai` 21행 CMYK 80/0/40/0 | **CI 표시에만. 글자로 금지(대비 2.91), 청록 위 흰 글자 금지** |
| `color.gray` | `kGray` | **#BCBEC0** | Cool Gray 4 | 비활성, pseudo 묶음 막대 |

### 1.2 중립·파생 색 (라이트 전용) — Swing 이식용 전체 표 (tokens.h:30-57 + light.qss 리터럴)

| 토큰(qss 이름) | 코드 상수 | HEX | 쓰임 |
|---|---|---|---|
| white | `kWhite` | #FFFFFF | 패널·표·메뉴바·툴바·상태바 배경 |
| window | `kWindow` | #F5F7FA | 창 배경, 에디터 현재 줄, pseudo 묶음 행, 대화상자 배경 |
| border | `kBorder` | #E1E5EA | 1px 경계선 전부 |
| hover | `kHover` | #F3F6F9 | 표 행 hover, 비선택 탭 hover |
| text | `kText` | #1F2933 | 본문 |
| text-2 | `kText2` | #5A6472 | 보조 글자(버전, 비활성 탭, 소스 열) |
| text-muted | `kTextMuted` | #65707E | 줄 번호, 주석 |
| text-log | `kTextLog` | #2B3440 | 메시지 로그 본문 |
| scroll | `kScroll` | #C9D0D8 | 스크롤 핸들 |
| scroll-hover | `kScrollHover` | #AEB7C2 | 스크롤 핸들 hover |
| blue-tint | `kBlueTint` | #E8F0F9 | PC 행 배경, 배지 R, 메뉴바·툴버튼 hover |
| blue-tint2 | `kBlueTint2` | #D3E2F3 | 선택 행 배경(글자 navy), pressed |
| teal-tint | `kTealTint` | #E6F6F5 | 배지 I·FI, $sp/$fp/$gp 마커, **변경된 레지스터 값 셀 배경** |
| teal-text | `kTealText` | #00736F | 변경된 값 글자, 마커 글자, 레지스터 문법 강조, 성공 상태 |
| amber-tint | `kAmberTint` | #FDF3E1 | 배지 J, "Source changed" 띠, 모드 배지 |
| amber-text | `kAmberText` | #8A5A00 | 위의 글자, 문자열 문법 강조 |
| (없음) | `kPurpleText` | #6B4C9A | 숫자 문법 강조 |
| (없음) | `kCp0Tint` / `kCp0Text` | #EEF0F2 / #4A5560 | 배지 CP0 |
| error | `kError` | #C0392B | 에러 글자·마커 |
| error-tint | `kErrorTint` | #FBEAE8 | 에러 배지·에러 줄 배경 |
| error-text | `kErrorText` | #8E2A1F | error-tint 위 글자 |
| (없음) | `kWarning` | #B7791F | 경고 아이콘만(대비 3.6) |
| (qss 리터럴) | — | #EAEEF3 | 비선택 탭 배경 (`light.qss:58`) |
| (qss 리터럴) | — | #F1DDB4 | Source changed 띠 아래 선 (`light.qss:139`) |
| (qss 리터럴) | — | #F8E8C8 | Source changed 띠 hover (`light.qss:141`) |
| (문서만) | — | #7A4E0C / #FCF3E3 | warning.text / warning.tint (tokens.md:49-50. 코드는 amber 쌍을 대신 씀 `light.qss:132`) |

### 1.3 색 배치 (tokens.md:59-72, light.qss)

| 어디 | 값 |
|---|---|
| 선택 행 | 배경 blue-tint2 #D3E2F3 + 글자 navy. **진파랑 채움 + 흰 글자는 쓰지 않는다** |
| PC 행 | 배경 blue-tint #E8F0F9 + 왼쪽 3px blue 막대(`kPcBarWidth=3`). PC이면서 선택 = 막대 + blue-tint2 |
| 활성 탭 | 흰 배경, 글자 blue, 위 2px blue 선. 세로 탭은 왼쪽 2px (`light.qss:58-70`). 탭 글자 굵기는 선택 여부와 무관하게 600 고정 |
| 주요 버튼 | blue 배경 + 흰 글자, 라운드 6px, 높이 28px, hover/pressed navy, disabled gray 배경 (`light.qss:33-37`) |
| 일반 버튼 | 흰 배경, navy 600 글자, 1px border, 라운드 4, 패딩 5×14, default 버튼은 blue 채움 (`light.qss:103-108`) |
| 제목(창·그룹·도크) | navy 600 |
| 아이콘 | 기본 navy / hover(active) blue / disabled gray |
| 표 헤더 | 흰 배경, navy 600, 열 구분선 없음, 아래 1px border, 패딩 3×6 (`light.qss:81-82`) |
| 툴팁 | navy 배경 + 흰 글자 12px, 패딩 5×8 (`light.qss:143`) |
| 메뉴 | 흰 배경, 1px border, 라운드 6, 항목 선택 blue-tint2 + navy (`light.qss:11-22`) |
| 스크롤바 | 폭 12px, 핸들 라운드 4, 여백 2, 화살표 없음 (`light.qss:87-94`) |
| 입력칸 | 1px border, 라운드 4, focus 시 테두리 blue (`light.qss:97-102`) |
| 도크(카드) | 흰 배경, 1px border, 라운드 6, 도크 사이 8px(창 배경이 보임) (`light.qss:8,39-44`) |

### 1.4 타입 배지 (tokens.md:146-157, tokens.h:105-114)

라운드 4px, 글자 11px 600, 최소 폭 24px, 높이 16px.

| 배지 | 배경 | 글자 |
|---|---|---|
| R | #E8F0F9 | #0055A5 |
| I | #E6F6F5 | #00736F |
| J | #FDF3E1 | #8A5A00 |
| FR | #E8F0F9 | #00205B |
| FI | #E6F6F5 | #00205B |
| CP0 | #EEF0F2 | #4A5560 |

### 1.5 글꼴 (tokens.md:107-128, tokens.h:61-76)

| 역할 | 글꼴 | 크기 | 폴백 |
|---|---|---|---|
| UI | Pretendard Regular/Medium/SemiBold/Bold (OTF, OFL, `QtSpim/edu/theme/fonts/`, 4종 6.3MB) | 13px (`kUiPixelSize`) | Win: Malgun Gothic → Segoe UI / Linux: Noto Sans CJK KR |
| 코드(레지스터·Text·Data 표, 에디터, 로그, 콘솔) | D2Coding Regular/Bold (TTF, OFL, 2종 8.5MB, 한글 = 라틴 2칸) | 10pt ≈ 13.3px (`kCodePointSize`) | Consolas → monospace |

| 스케일 | px | 쓰임 |
|---|---|---|
| xs `kBadgePixelSize` | 11 | 배지 |
| s `kFontSmall` | 12 | 상태바, 툴팁, 스플래시 연구실 줄 |
| m `kUiPixelSize` | 13 | UI 기본, 메뉴, 도크 제목(600), 코드 표 |
| l `kTitlePixelSize` | 15 | 대화상자 제목, About 이름 |
| — `kCardBodySize` / `kCardTitleSize` | 15 / 16 | 튜토리얼 카드 본문 / 제목 |
| xl `kDisplayPixelSize` | 20 | About 제품명(DemiBold) |
| — `kSplashTitleSize` | 22 | 스플래시 제품명 Bold |
| 한글 굵기 `kKoreanWeight` | 600 | 한글 안내문 |

주의(tokens.md:116): fontconfig가 D2Coding을 dual-spacing으로 분류해 Linux에서 "고정폭 아님"으로 판정된다. Swing에서도 `Font.canDisplay`/폭 검사로 D2Coding을 거르지 말 것. **D2Coding은 Bold를 쓰지 않는다**(합성 볼드 폭 문제, ARCHITECTURE:819).

### 1.6 간격·크기 (tokens.md:130-142, tokens.h:80-101)

| 토큰 | 값 |
|---|---|
| space.1/2/3/4/6 | 4 / 8 / 12 / 16 / 24 px |
| 코드 표 행 높이 | 최소 16px(`kRowHeight`), 실제는 max(글리프, 16) ≈ 20px |
| 도크 제목 | 32px(13px 600 + 위아래 8px), 제목–헤더 간격 4px |
| 툴바 | 아이콘 20px(`kToolIconSize`), 버튼 패딩 3px, 간격 4px, 좌우 패딩 8px |
| 표 셀 패딩 | 좌우 6px |
| 라운드 | 도크·탭·메뉴 6px, 배지·버튼·입력칸 4px, 스플래시·튜토리얼 카드 8px |
| 경계선 | 1px |
| 레지스터 열 폭 | 380px(`kRegisterColumnWidth`) |
| 패널 최소 | 200 × 104px, 탭 글자 여백 40, 닫기 버튼 18 |
| PC 막대 / pseudo 묶음 막대 | 3px blue / 2px gray |
| 들여쓰기(레지스터 트리) | 12px (`edu_register_view.cpp:76`) |

### 1.7 레지스터 창 스타일

- **역할별 묶음** 8그룹, 순서 고정(`QtSpim/edu/core/edu_registers.cpp:145-184`):
  Special(PC, HI, LO) → Return values($v0-$v1) → Arguments($a0-$a3) → Temporaries($t0-$t9 = R8-15, R24-25) → Saved($s0-$s7) → Pointers($gp, $sp, $fp, $ra) → Reserved($zero, $at, $k0, $k1) → CP0(Status, Cause, EPC, BadVAddr). 그룹마다 툴팁 설명문이 있다.
- 열: 이름($t0) / 번호(R8) / 선택 진법 값 / 부호 있는 10진(오른쪽 정렬) (`edu_register_model.h:33`, `.cpp:135-152`). 이름·번호 열은 가로 스크롤 때 고정(`EduFrozenColumns`).
- 그룹 행: 글자 navy (`edu_register_model.cpp:124-126`), 처음엔 모두 펼침(`edu_register_view.cpp:122`).
- **변경된 값**: 값 열 글자색 = 설정 `RegWin/ChangedRegColor`(기본 teal-text #00736F, `QtSpim/state.cpp:78-79`), 값·10진 열 배경 = teal-tint #E6F6F5, 굵기 없음(`edu_register_model.cpp:155-169`). "변경" 기준은 실행 명령 시작 시점의 스냅샷.
- 툴팁: `$t0 (R8, "t0" in the simulator's own listings)` + hex = signed = unsigned.

**Swing 다크 모드 제안(ref에 근거 없음, 새 설계 필요):** CI 원색은 유지하되 텍스트 쪽은 AA를 다시 계산해야 한다. 특히 teal-text #00736F는 어두운 배경에서 대비가 모자라므로 별도 값을 정하고 tokens 표에 대비 근거를 남길 것.

---

## 2. `assets/ci/`

### 2.1 폴더별 파일 (모두 git에 들어 있음: `git ls-files assets` = 118개 = 전체)

| 폴더 | 파일 | 형식·해상도 | 설명 |
|---|---|---|---|
| `A1/` | `a-1-1.ai`, `a-1-2.ai` + `A-1-1.jpg`, `A-1-2.jpg` | .ai = EPS(DSC 3.0) / JPG 624×842 | 심볼마크(기본형·활용형) 원본 AI + 매뉴얼 페이지 미리보기 |
| `A2/` | `a-2-1.ai`, `a-2-2.ai` + JPG 2 | 동일 | 로고타입 |
| `A3/` | `a-3-1.ai` + JPG 1 | 동일 | 엠블럼 A/B/C |
| `A4/` | `a-4-1..5.ai` + `A-4-1..5.jpg` | 동일 | 시그니처(좌우·상하·세로 조합). `A-4-1.jpg`가 배치·여백 규정(A = 심볼 폭) |
| `converted/` | `a-*-*.pdf/.png/.svg` 10세트(30개) | PDF 1.7 1쪽 / PNG **1300×1754** RGB / SVG | .ai 페이지 전체 변환 |
| `marks/` | 26개 마크 × (svg + png) = 52개 | PNG RGB, 마크별 크기(아래) | 마크별로 잘라 치수 가이드를 지운 것 |
| `manual/` | `README.md` + JPG 15 | symbol-mark 750×320, symbol-logo1-3·emblem1-3 460×300, symbol-signature1-8 720×300 | 학교 웹페이지 이미지 사본 |

`marks/` PNG 해상도:

| 파일 | 크기 | 파일 | 크기 |
|---|---|---|---|
| emblem-a-blue | 577×577 | signature-h-cn | 931×255 |
| emblem-a-navy | 576×576 | signature-h-en | 846×301 |
| emblem-b | 545×601 | signature-h-ko-en | 938×307 |
| emblem-c | 998×583 | signature-h-ko | 931×251 |
| logotype-cn | 1063×222 | signature-va-{cn,en,ko-en,ko} | 560×359 / 599×445 / 602×426 / 593×366 |
| logotype-en-inline | 1353×107 | signature-vb-{cn,en,ko-en,ko} | 758×366 / 758×439 / 758×418 / 758×362 |
| logotype-en-stacked | 1238×416 | signature-vertical-a / -b | 372×986 / 370×1468 |
| logotype-ko-en | 1180×349 | symbol-applied | 1609×1443 |
| logotype-ko-vertical | 192×969 | symbol-basic | 1895×915 |
| logotype-ko | 1163×236 | | |

PNG는 모두 8-bit RGB(알파 없음, 흰 배경)다. 투명 배경이 필요하면 **SVG를 쓴다**. 앱 리소스용 투명 PNG는 `QtSpim/edu/theme/brand/`에 따로 있다(아래 2.3).

### 2.2 converted/·marks/ 만든 방법

README나 스크립트는 없다. 유일한 근거는 커밋 메시지 `git log -- assets/ci`:

```
0d7eb5c 2026-09-22 [H0] assets: Hallym CI artwork A1-A4, manual copies, marks converted to SVG/PNG
Original zips stay in ~/workspace/hallym-ci.  .ai -> PDF (Ghostscript,
-dEPSCrop) -> SVG/PNG (pdftocairo); assets/ci/marks/ holds each mark cropped
by its coloured pixels with the manual's dimension guides removed.
```

PLAN.md:275도 같은 내용이다("Ghostscript + pdftocairo", marks = 심볼 기본형·활용형, 로고타입 6종, 엠블럼 A 2종·B·C, 시그니처 좌우 4·상하 A/B 8·세로 2). 자르기 스크립트는 커밋되지 않았다. **다시 만들 수 없으니 marks/의 SVG를 그대로 가져다 쓰는 것이 맞다.**

### 2.3 앱 아이콘·로고를 어디서 어떻게 쓰나

- 앱 리소스: `QtSpim/edu/theme/brand/`. SVG 4개는 `assets/ci/marks/`의 같은 이름 파일과 **바이트가 같다**(`cmp` 확인: emblem-a-navy, logotype-ko-en, signature-h-ko-en, symbol-basic).
- PNG는 `tools/make-theme-icons.py`가 cairosvg로 렌더한다(`:36-37` BRAND 표, `:225-231`). 크기는 `emblem-a-navy` 112(+@2x 224), `logotype-ko-en` 220×65(+@2x), `signature-h-ko-en` 260×85·320×104(+@2x), `symbol-basic` 64×31(+@2x). 모두 RGBA.
- 앱 아이콘 `app-{16,24,32,48,64,256}.png`, `HallymMIPS.ico`, `HallymMIPS.icns`도 같은 스크립트가 만든다(`:217-224`). 구성(`:45-76`): **흰 둥근 사각 타일(라운드 = 폭의 18%, 1px #E1E5EA 테두리)에 symbol-basic을 타일 폭의 76%로 가운데 배치**, 8배로 그린 뒤 LANCZOS로 줄인다. .ico는 크기마다 다른 이미지를 담으려고 직접 쓴다(`:92-111`).
- 쓰는 곳:
  - 창 아이콘: `edu_theme.cpp:151-156` (`app-*.png` 6개를 QIcon에 넣음), Windows 실행 파일 `QtSpim/QtSpim.pro:172` `RC_FILE = edu/theme/brand/HallymMIPS.rc`(ICON "HallymMIPS.ico"), mac `QtSpim.pro:376,393`, Linux 런처 `tools/install-linux-launcher.sh:40`(app-256.png).
  - 스플래시: `edu_splash.cpp:176-185`. 480×360 흰 카드(라운드 8, 1px border). **시그니처 국영문 좌우조합을 폭 260px로 위에서 44px 지점 가운데**에 놓는다. 그 아래 24px(space4+space2) → 제품명 22px Bold navy → 버전 13px text-2 → 12px 간격 → 320px 구분선(border) → 12px 간격 → "AIAC Lab · Hallym University" 12px text-2. 바닥에서 92px 위에 질문 문구와 버튼 두 개.
  - About: `edu_about.cpp:84-88`. 엠블럼 A(navy) 112px + 로고타입 국영문 220px, 제품명 20px DemiBold navy, "Version x · based on QtSpim 9.1.24" text-2, License 탭에 "The university symbol, logotype, emblem and signature are the property of Hallym University and are used under its UI regulations for non-commercial university purposes."(`:69-71`).
  - `brandPixmap(name, width, dpr)`(`edu_theme.cpp:112-116`)는 미리 렌더한 폭만 받는다. dpr > 1이면 @2x를 쓴다.

### 2.4 `manual/`에 학교 UI 규정 PDF가 있나

**없다.** `assets/ci/manual/`에는 `README.md`와 웹페이지 이미지 JPG 15개뿐이고 모두 git에 들어 있다. 규정은 PDF가 아니라 웹페이지 원문을 `manual/README.md:24-30`에 텍스트로 옮겨 두었다(출처 https://www.hallym.ac.kr/hallym/965/subview.do, 966, 2026-09-22 확인). 저장소 안의 PDF는 SPIM 문서(`Documentation/**.pdf`)와 `assets/ci/converted/*.pdf`(.ai 변환본)뿐이다. `.gitignore`에는 CI 자산 관련 규칙이 없다.

---

## 3. `.github/workflows/ci.yml`

트리거: main push, 태그 `stage-*`·`v*`, PR, 수동 실행(`:15-20`).

### 3.1 Windows 잡 (`:114-257`, `runs-on: windows-2022`, `needs: linux`)

| 순서 | 단계 | 정확한 내용 | 줄 |
|---|---|---|---|
| 1 | checkout | `actions/checkout@v4` | :119 |
| 2 | Qt | `jurplel/install-qt-action@v4`, `version: '5.15.2'`, `arch: 'win64_msvc2019_64'`, `cache: true` | :121-126 |
| 3 | MSVC 환경 | `ilammy/msvc-dev-cmd@v1`, `arch: x64`, `toolset: '14.29'` (VS2019 v142) | :128-132 |
| 4 | winflexbison | `choco install winflexbison3 --no-progress -y` (`win_bison`, `win_flex` 명령이 생긴다) | :134-135 |
| 5 | 버전 출력 (cmd) | `qmake -v` / `win_bison --version` / `win_flex --version` / `cl 2>&1 \| findstr /C:"Version"` | :137-143 |
| 6 | 빌드 (cmd) | `mkdir build && cd build` → `qmake "CONFIG+=release" "CONFIG-=debug_and_release" ..\QtSpim\QtSpim.pro` → `nmake` (각각 `if errorlevel 1 exit /b 1`) | :145-153 |
| 7 | 단위 테스트 (cmd, env `QT_LOGGING_TO_CONSOLE=1`) | `tests\tests.pro`를 같은 방식으로 qmake/nmake → `nmake check > %RUNNER_TEMP%\unit-tests.log 2>&1`, 로그에 `Totals:`가 없으면 실패 | :162-176 |
| 8 이후 | 패키징 | guide-pdf 아티팩트 받기 → `tools/package-windows.ps1` zip → 압축 풀고 10초 실행 확인 → `tools/package-msi.ps1`(WiX) → 이전 릴리스 MSI 받아 `tools/check-msi.ps1` 설치·업그레이드 검사 → 업로드 | :178-257 |

bison/flex 호출은 워크플로가 아니라 qmake 파일이 정한다(`QtSpim/QtSpim.pro:190-198`, `win32-msvc` 블록 `:281-300`; 테스트용은 `tests/spim_core.pri:25-50`).

```
QMAKE_YACC       = bison          # win32-msvc: win_bison
QMAKE_YACCFLAGS  = --defines=parser_yacc.h --output=parser_yacc.cpp
QMAKE_YACCFLAGS_MANGLE = -p yy
QMAKE_LEX        = flex           # win32-msvc: win_flex
QMAKE_LEXFLAGS_MANGLE = -Pyy
QMAKE_LEXFLAGS   = -I -8 --outfile=lex.scanner.c
win32-msvc: DEFINES += _CRT_SECURE_NO_WARNINGS; /utf-8;
            QMAKE_CXXFLAGS_RELEASE -= -Zc:strictStrings; QMAKE_CXXFLAGS += -Zc:strictStrings-
linux-g++:  -Wno-write-strings; QMAKE_MOVE = touch (yacc.prf가 parser_yacc.h를 자기 자신에게 mv하는 문제)
```

**`-Zc:strictStrings-`가 핵심이다.** CPU/는 문자열 리터럴을 `char*`로 넘기므로(`error("...")`, `int_reg_names[]`) MSVC 릴리스 기본값(strictStrings)으로는 컴파일되지 않는다. 원래 SPIM Makefile의 플래그는 `vendor/spim/Makefile:82-86`: `flex -I -8 -o lex.yy.cpp`, `bison -d --defines=parser_yacc.h --output=parser_yacc.cpp -p yy`.

### 3.2 Linux 잡 (`:23-112`, `ubuntu-22.04`, 이름 "Qt 5.15.3, gcc 11")

1. `actions/checkout@v4`, `fetch-depth: 0`(regress.sh가 `vanilla-9.1.24` 태그를 씀) (:27-29)
2. `sudo apt-get install -y --no-install-recommends qtbase5-dev qt5-qmake qttools5-dev-tools libqt5help5 bison flex fonts-nanum` (:31-36)
3. 릴리스 빌드: `mkdir -p build-release && cd build-release && qmake ../QtSpim/QtSpim.pro && make -j"$(nproc)" 2>&1 | tee build.log && test -x HallymMIPS` (:38-43)
4. 개발 빌드: 같은 방식 + `CONFIG+=edu_devtools`, 디렉터리 `build` (:45-50)
5. **우리 코드 경고 0**: build.log에서 `warning:`을 뽑고 `(^|/)CPU/|parser_yacc|scanner_lex|lex\.scanner|moc_|qrc_|ui_`를 빼서 남으면 실패 (:52-60)
6. 빌드 후 `git status --porcelain`이 비어 있어야 함 (:62-65)
7. 단위 테스트: `build-tests`에서 `qmake ../tests/tests.pro && make && make check` (:67-72)
8. `tools/regress.sh`, `tools/check-menu-load.sh --compare-vanilla`, `tools/check-editor.sh`, `tools/check-path-warning.sh`, `tools/capture-panels.sh`, 가이드 PDF(pandoc) 만들고 아티팩트 업로드 (:74-112)

---

## 4. ARCHITECTURE.md와 디코더 오라클 테스트

### 4.1 무엇과 무엇을 비교하나 (`docs/ARCHITECTURE.md:857-943`, `tests/edu_oracle/tst_decoder_oracle.cpp`)

"정답을 테스트에 적지 않고 코어에서 얻는다"(`tst_decoder_oracle.cpp:1-15`). 비교 대상은 우리 디코더 `QtSpim/edu/core/edu_decoder.cpp`(코어를 링크하지 않음)와 **실제 SPIM 코어**(이 테스트 바이너리만 코어를 링크)다.

1. `opTable()` (`:287-362`): `CPU/op.h`를 `#define OP(...)`로 표로 읽어(`:52-58`) 인코딩 있는 명령 200개마다 코어 `instruction`을 만들고 `inst_encode()`로 워드를 얻는다. 피연산자 30조합, 5,971워드. 이름, 슬롯(rs/rt/rd/shamt/imm/target/cc), 필드 재조립이 원래 워드와 같은지, 필드가 32비트를 빈틈없이 덮는지 확인한다.
2. `programs()` (`:366-458`): `exceptions.s`, `helloworld.s`, `Tests/tt.{core,le,dir,io,bare,alu.bare,fpu.bare}.s`를 코어로 어셈블(`initialize_world` + `read_assembly_file`)한 뒤, 텍스트 세그먼트(`TEXT_BOT..text_top`, `K_TEXT_BOT..k_text_top`)의 모든 명령(8,964개)을 `read_mem_inst(pc)`로 읽어 같은 검사를 하고, 분기·점프 목적지를 `EXPR(inst)->symbol->addr`(심볼 테이블 라벨 주소)와 대조한다(1,484개). 파일마다 bare/delayed/handler 플래그가 다르다(`:372-380`, spim/Makefile 테스트 타깃과 같음).
3. `unknownEncodings()`: 안 쓰는 opcode와 Release 2 명령 91개는 모른다고 답해야 한다.
4. `coreDecodeAgreement()`: 코어 `inst_decode()`와의 차이 10개를 고정한다(`:41-44`). 새 차이가 생기면 실패. (코어 디코더 버그, ARCHITECTURE:911-929)

### 4.2 돌리는 방법

```
mkdir -p build-tests && cd build-tests
qmake ../tests/tests.pro && make -j$(nproc) && make check      # tests/tests.pro:3-6
```
`tests/tests.pro`는 subdirs(`edu_core edu_oracle edu_loader`)이고, `edu_oracle.pro`는 `include(../spim_core.pri)`로 CPU/를 붙인다. `EDU_SOURCE_ROOT`를 정의해 저장소 루트의 `.s` 파일을 찾는다(`edu_oracle.pro:24`).

### 4.3 어셈블 일치 테스트에 그대로 쓸 수 있는 것

| 자산 | 위치 | 쓰임 |
|---|---|---|
| **코어 빌드 조각** | `tests/spim_core.pri` (50줄) | CPU/ 소스 9개 + parser.y/scanner.l 생성 + 플랫폼 플래그. "CPU/는 고치지 않고 파서·스캐너는 빌드 디렉터리에 생성"(`:1-6`). hcs-asm의 CMake/Makefile로 옮기면 된다 |
| **프런트엔드 스텁** | `tests/edu_oracle/core_frontend_stubs.{h,cpp}` | CPU/가 요구하는 전역(`bare_machine`… `message_out/console_out/console_in`)과 함수(`error`, `run_error`, `fatal_error`, `write_output`, `read_input`, `console_input_available`, `get_console_char`, `put_console_char`)를 정의한다. 에러는 모으고 출력은 버리며, `eduOutputCapture`로 `print_symbols()` 출력을 잡는다. Qt 의존은 `QString`뿐이라 std::string으로 바꾸기 쉽다. **hcs-asm의 main 옆에 거의 그대로 둘 수 있다** |
| **로컬 라벨까지 얻는 로더** | `QtSpim/edu/edu_loader.cpp:24-51` | `read_assembly_file()`을 줄마다 그대로 따라 하되, `flush_local_labels()` 바로 전에 `print_symbols()`를 캡처한다. 이렇게 하지 않으면 **로드가 끝난 뒤 로컬 라벨(`msg:`, `loop:`)이 해시 테이블에서 빠져 JSON `labels`를 만들 수 없다**(ARCHITECTURE:289-296). `tests/edu_loader/tst_loader.cpp`가 원본 read_assembly_file과 상태가 같은지 검증한다 |
| **print_symbols 파서** | `QtSpim/edu/core/edu_symbols.{h,cpp}` | `"g\tmain at 0x00400024\n"` / `"\tmsg at 0x10010000\n"` 형식 파싱 |
| **상태 스냅샷** | `tst_loader.cpp:75-111` `snapshot()` | text = 각 주소의 `format_an_inst` 줄, data = `DATA_BOT..data_top`, `K_DATA_BOT..k_data_top`에서 0이 아닌 워드의 (주소, 값), 세그먼트 top, 에러 목록, print_symbols. **hcs-asm JSON의 text/data를 만드는 방법과 같다** |
| **소스 라벨 추출기** | `tst_loader.cpp:117-160` `labelsInSource()` | 소스에서 `이름:`을 정규식으로 뽑는다(주석·문자열 처리). labels 완전성 검사에 재사용 |
| **테스트 입력 .s** | `Tests/*.s`, `helloworld.s`, `tests/samples/*.s`, `samples/tutorial.s` | 아래 7절 |
| **골든(원본 GUI 출력)** | `tests/golden/text-load.txt`, `text-ttcore.txt`, `data-load.txt` 등 | 원본 QtSpim의 "Save Log File" 출력. 형식은 `[00400024] 34020004  ori $2, $0, 4 ; 40: li $v0, 4 ...`. 주소·워드 쌍을 뽑아 hcs-asm 출력과 비교할 수 있다(단 `env -i` 고정 환경이라 data 세그먼트의 스택 부분은 다를 수 있음, ARCHITECTURE:327-353) |

hcs-asm이 알아야 할 코어 사실(ARCHITECTURE 근거):
- `SOURCE(inst)`는 `"줄번호: 원문"`이거나 NULL이다. pseudo 확장의 **첫 명령에만** 붙는다(§3.4, `CPU/inst.cpp:174`, `CPU/scanner.l:686-694,722`). JSON `line`/`source`는 이것을 쪼개 쓰고, 뒤따르는 확장 명령은 직전 줄을 이어받게 해야 한다.
- 예외 처리기를 불러오면 사용자 텍스트는 `0x00400000`부터 `exceptions.s`의 `__start` 스텁 9개(183~192행)로 시작하고, 학생 코드는 `0x00400024`부터다(`tests/golden/text-load.txt:2-11`). 커널 텍스트 `0x80000180~`도 채워진다. **SOURCE 줄 번호가 exceptions.s 것인지 학생 파일 것인지 구분해야 한다**(학생 파일을 읽기 전 `text_top`을 기록해 두면 된다).
- 분기 오프셋은 `delayed_branches`에 따라 **같은 소스라도 워드가 달라진다**(기본 꺼짐: (라벨−PC)/4, 켜짐: −1 더) — `CPU/sym-tbl.cpp:258-266`, ARCHITECTURE:876-891. `settings`에 반드시 기록할 것.
- 에러 형식: `spim: (parser) <msg> on line <N> of file <path>` + 탭 두 줄(원문, 캐럿) (ARCHITECTURE:357-378, `CPU/parser.y:2941-2950`). 파서는 **첫 syntax error에서 멈춘다**(read_assembly_file 루프, `tst_loader.cpp:162-173`). 파서 에러는 `clear_labels()`를 부른다.
- 코어 헤더에는 include guard가 없다. 각 헤더를 한 번만 include할 것(ARCHITECTURE:316-325).
- `find_symbol_address`/`lookup_label`은 없는 이름을 테이블에 만들어 넣는다. 쓰지 말고 `label_is_defined`를 쓸 것(ARCHITECTURE:273-283).

---

## 5. `ref/CPU/` vs `vendor/spim-9.1.24/CPU/`

`diff -rq vendor/spim-9.1.24/CPU ref/hallym-mips-simulator/CPU` → **출력 없음, 종료 코드 0. 완전히 같다.** (`spim/`, `Tests/`, `helloworld.s`, `QtSpim/exception.qrc`도 vendor와 같다.) 따라서 hcs-asm이 vendor CPU/를 링크하면 Hallym MIPS와 같은 기계어가 나온다(설정만 같으면).

---

## 6. 기본 설정

### 6.1 CPU/가 요구하는 전역 (선언 `vendor/CPU/spim.h:225-236`)

`bare_machine`, `accept_pseudo_insts`, `delayed_branches`, `delayed_loads`, `quiet`, `exception_file_name`(char*), `force_break`, `parser_error_occurred`, `spim_return_value`, `message_out`/`console_out`/`console_in`(port), `mapped_io`, 그리고 `initial_{text,data,stack,k_text,k_data}_size/limit`.
**정의는 CPU/ 밖(프런트엔드)에 있다.** 예외: `force_break = false`는 `CPU/run.cpp:65`, `initial_*`는 `CPU/spim-utils.cpp:61-`, `text_modified`/`data_modified`는 `CPU/mem.cpp:53,56`.

| 전역 | 터미널 spim 정의·초기값 (`vendor/spim/spim.cpp`) | QtSpim 정의 (`vendor/QtSpim/spim_support.cpp:43-51`) | 오라클 스텁 (`ref/tests/edu_oracle/core_frontend_stubs.cpp:30-41`) |
|---|---|---|---|
| `bare_machine` | :106, main에서 `false` (:141) | 초기화 없음(0) → readSettings | 0, 테스트가 설정 |
| `delayed_branches` | :107, `false` | 〃 | 〃 |
| `delayed_loads` | :108, `false` | 〃 | 〃 |
| `accept_pseudo_insts` | :109, `true` (:144) | 〃 | 〃 |
| `quiet` | :110, `false` | 〃 | 〃 |
| `exception_file_name` | :112, `DEFAULT_EXCEPTION_HANDLER` (Makefile:101 = `$(PREFIX)/share/spim/exceptions.s`, 환경변수 `SPIM_EXCEPTION_HANDLER`로 바꿈 :155-156) | `= 0` (:48-49, QtSpim은 이 전역을 쓰지 않고 `initialize_world(파일명)`에 직접 넘김) | `= 0` |
| `mapped_io` | :114, `false` (:151) | 초기화 없음 → readSettings | 0 |
| (static) `load_exception_handler` | :121, `true` | — (QtSpim은 `st_loadExceptionHandler`) | — |

터미널 spim 옵션(`spim.cpp:164-205`): `-asm/-a`(bare·지연 끔), `-bare/-b`(bare + delayed_branches + delayed_loads + quiet 켬), `-delayed_branches/-db`, `-delayed_loads/-dl`, `-exception/-e`, `-noexception/-ne`, `-exception_file/-ef <f>`, `-mapped_io/-mio`, `-nomapped_io/-nmio`, `-pseudo/-p`, `-nopseudo/-np`, `-quiet/-q`, `-noquiet/-nq`, `-trap/-notrap/-trap_file`(옛 이름). **hcs-asm CLI 옵션 이름을 이것에 맞추면 좋다.**

`initialize_world(files, print_message)`(`CPU/spim-utils.cpp:79-`)는 예외 처리기를 읽는 동안만 `bare_machine=false`, `accept_pseudo_insts=true`로 바꿨다가 되돌린다(:94-102). 파일 이름은 `;`로 여러 개를 줄 수 있다.

### 6.2 QtSpim 설정 키와 기본값 (`QSettings` 그룹 `Spim`)

원본: `vendor/QtSpim/state.cpp:120-137`(읽기), `:188-200`(쓰기). 조직/앱 `"LarusStone"/"QtSpim"`(`spimview.cpp:46`). `spim_settings.h`에는 기본값이 없다(글꼴·색 선택 대화상자 슬롯뿐, `:45-97`).

| 기능 | 설정 키 | 변수 | 원본 기본값 | Hallym MIPS |
|---|---|---|---|---|
| 경고 끄기 | `Spim/Quiet` | `quiet` | false | 같음 (`ref/QtSpim/state.cpp:137`) |
| Bare machine | `Spim/BareMachine` | `bare_machine` | 0 (false) | **읽지 않고 항상 false** (`state.cpp:139`, `menu.cpp:455-470`에서 체크박스와 "Bare Machine" 버튼 숨김, `menu.cpp:586`). 쓰기에서도 뺐다(:192 부근에 없음) |
| 의사 명령어 허용 | `Spim/AcceptPseudoInsts` | `accept_pseudo_insts` | 1 (true) | 같음 (:140) |
| 지연 분기 | `Spim/DelayedBranches` | `delayed_branches` | 0 (false) | 같음 (:141) |
| 지연 로드 | `Spim/DelayedLoads` | `delayed_loads` | 0 (false) | 같음 (:142) |
| 메모리 매핑 I/O | `Spim/MappedIO` | `mapped_io` | 0 (false) | 같음 (:143) |
| 예외 처리기 불러오기 | `Spim/LoadExceptionHandler` | `st_loadExceptionHandler` | true | 같음 (:146) |
| 예외 처리기 파일 | `Spim/ExceptionHandlerFileName` | `st_exceptionHandlerFileName` | `stdExceptionHandler` = `"<<SPIM Exception Handler>>"` (`spimview.cpp:52`) = 내장 리소스 `:exceptions.s` → `../CPU/exceptions.s` (`exception.qrc`) | 표시 문자열만 `"<<Built-in Exception Handler>>"`(`ref/QtSpim/spimview.cpp:64`). 내용은 같은 CPU/exceptions.s |
| 시작 주소 | `Spim/StartingAddress` | `st_startAddress` | `starting_address()` | 같음 |
| 실행 인자 | 읽기 `CommandLineArguments` / 쓰기 `CommandLine` (원본 버그, ARCHITECTURE:556-557) | `st_commandLine` | "" | 같음 |
| 변경 레지스터 색 | `RegWin/ChangedRegColor` | | "red" | **teal-text #00736F** (`ref/state.cpp:78-79`) |
| 레지스터·텍스트 글꼴/색 | `RegWin|TextWin/Font, FontColor, BackgroundColor` | | Courier 10 / black / white | D2Coding 10pt / #1F2933 / #FFFFFF |
| 진법·표시 토글 | `RegisterDisplayBase` 등 | | 16, 전부 true | 저장하지 않고 매번 16·true로 시작(`state.cpp:48-70`, "AA") |

Hallym MIPS는 조직/앱을 `"HallymMIPS"/"HallymMIPS"`로 바꿨다(`ref/QtSpim/edu/edu_version.h:38-39`, `spimview.cpp:53`, `main.cpp:66-67`). 화면 배치·최근 파일은 저장하지 않는다(`state.cpp:48-69` `eduForgetScreenSettings`).

예외 처리기 적용 경로(원본 `vendor/QtSpim/spimview.cpp:194-226`): 켜져 있고 기본값이면 리소스를 `QTemporaryFile`에 풀어 `initialize_world(tmp, false)`, 사용자 파일이면 `initialize_world(file, true)`, 꺼져 있으면 `initialize_world(NULL, true)`. ref는 같은 구조(`ref/QtSpim/spimview.cpp:215-250`)에 라벨 초기화만 더했다.

**결론: Hallym MIPS의 어셈블 관련 기본값 = 원본 QtSpim 기본값**(bare 끔, pseudo 켬, 지연 분기·로드 끔, mapped I/O 끔, 예외 처리기 내장본 켬). 다른 점은 bare machine을 켤 방법이 없다는 것뿐이다. hcs-asm 기본값도 이것으로 하면 두 제품의 기계어가 같다.

---

## 7. ref의 어셈블 예제 .s

| 파일 | 줄 | 내용 / 오라클·로더 테스트 플래그 (bare, delayed, handler) |
|---|---|---|
| `helloworld.s` | — | vendor와 같음. (F,F,T) |
| `samples/tutorial.s` | 69 | 튜토리얼 예제(한글 주석, `sum_loop` 라벨) |
| `Tests/tt.core.s` | 4864 | (F,F,T) |
| `Tests/tt.le.s` / `tt.be.s` | 546 / 556 | (F,F,T) |
| `Tests/tt.dir.s` | 167 | 지시어. (F,F,T) |
| `Tests/tt.io.s` | 209 | mapped I/O. (F,F,T) |
| `Tests/tt.bare.s` | 519 | (F,**T**,F) |
| `Tests/tt.alu.bare.s` | 1852 | (T,T,F), 기본 모드로도 돌림(에러 11개: 즉시값 범위 등) |
| `Tests/tt.fpu.bare.s` | 2239 | (T,T,F) |
| `Tests/read.s`, `time.s`, `timer.s` | 50 / 57 / 50 | 로더 테스트만 (F,F,T) |
| `tests/samples/data-stack.s` | 31 | 로컬·전역 .data 라벨, 워드 |
| `tests/samples/bare-branch.s` | 13 | 진짜 지연 분기 (T,T,F) |
| `tests/samples/editor-errors.s` | 14 | 어셈블 에러 두 개(멈추지 않는 종류) |
| `tests/samples/syntax-error-midfile.s` | 14 | 중간 syntax error에서 멈춤 |

`Tests/`는 vendor의 `Tests/`와 같다(`tt.in`은 입력 파일). 플래그 근거: `tst_decoder_oracle.cpp:372-380`, `tst_loader.cpp` programs_data.

---

## 8. PLAN.md / CLAUDE.md의 로고·캐릭터 선례

- 규칙(CLAUDE.md:29, PLAN.md:250, tokens.md:11): 심볼마크·로고타입·엠블럼·시그니처는 **축소와 여백만**. 단색화·회전·비율 변경·색 변경·요소 분리 금지. 심볼+로고타입 조합은 A4 시그니처의 배치·여백(A = 심볼 폭, `assets/ci/A4/A-4-1.jpg`)을 따른다. 색·글꼴·간격 리터럴은 토큰 파일 밖에 쓰지 않는다.
- 앱 아이콘은 **심볼 기본형만**(PLAN.md:250). 16·32px에서 엠블럼·시그니처가 뭉개진다(`docs/design/mockups/icon-sizes.png`). 최종은 흰 타일 + 심볼 76%(2.3절).
- 스플래시: 시그니처 국영문 좌우조합 260px, 위 44px, 가운데 정렬(2.3절). About: 엠블럼 A(navy) 112px + 로고타입 국영문 220px(PLAN.md:270, 2.3절).
- 크기 선례 요약: 엠블럼 112px, 로고타입 220px, 시그니처 260/320px, 심볼 64px(렌더된 폭, `make-theme-icons.py:36-37`).
- 라이선스 고지: About License 탭에 대학 자산 문구(`edu_about.cpp:69-71`). 규정 원문 인용에 "UI는 홍보용으로 제작되어 상업용 목적으로 사용할 수 없다", 문의처 커뮤니케이션팀(tokens.md:3-9).
- 이름 규칙(CLAUDE.md:28): 표시명 "Hallym MIPS Simulator", 실행 파일·설정 폴더 "HallymMIPS", 한글명은 안내문에만. 원본 이름은 License 탭에만.
- **캐릭터(마스코트) 사용 선례는 없다.** PLAN·CLAUDE·tokens·ARCHITECTURE를 "캐릭터/마스코트/character/mascot"로 찾아도 결과가 없고, assets/ci에도 캐릭터 파일이 없다.

---

## hcs-circuit-studio가 재사용할 것

1. **CPU/는 vendor 것을 그대로 링크한다.** ref와 바이트가 같으므로 결과도 Hallym MIPS와 같다.
2. **빌드 조각**: `tests/spim_core.pri`의 소스 목록과 bison/flex 플래그(`-p yy`, `--defines=parser_yacc.h --output=parser_yacc.cpp`, flex `-I -8 -Pyy`), MSVC의 `-Zc:strictStrings-`·`_CRT_SECURE_NO_WARNINGS`·`/utf-8`, gcc의 `-Wno-write-strings`. 생성물은 빌드 디렉터리에만 둔다.
3. **Windows CI 단계**: `jurplel/install-qt-action@v4`(Qt가 필요할 때만), `ilammy/msvc-dev-cmd@v1`(x64, toolset 14.29), `choco install winflexbison3 --no-progress -y` → `win_bison`/`win_flex`. Linux는 `apt-get install bison flex`. "우리 코드 경고 0"(CPU/·생성물 제외) 필터와 "빌드 후 git status 깨끗" 검사.
4. **프런트엔드 스텁**: `tests/edu_oracle/core_frontend_stubs.cpp` 구조(전역 정의 + error 모으기 + write_output 캡처)를 hcs-asm main에 옮긴다. QString은 std::string으로 바꾼다.
5. **로컬 라벨 확보**: `QtSpim/edu/edu_loader.cpp`의 `eduReadAssemblyFile()` 방식(flush_local_labels 전에 print_symbols 캡처)과 `edu_symbols` 파서. 이것 없이는 JSON `labels`에 로컬 라벨이 빠진다.
6. **text/data 추출**: `tst_loader.cpp` `snapshot()`처럼 `read_mem_inst`로 주소를 훑어 `ENCODING(inst)`와 `SOURCE(inst)`("N: 원문")를 얻고, data는 `read_mem_word`로 얻는다. 학생 파일을 읽기 전 `text_top`/`data_top`을 기록해 두면 exceptions.s 몫과 구분된다.
7. **settings 기본값**: bare=false, pseudo=true, delayed_branches=false, delayed_loads=false, mapped_io=false, 예외 처리기 = `CPU/exceptions.s` 켬, quiet=false. 이름은 spim CLI 옵션(`-bare`, `-db`, `-noexception`, `-ef`, `-nopseudo`, `-mio`)에 맞추고, JSON `settings`에 모두 기록한다. delayed_branches는 분기 워드를 바꾼다.
8. **일치 테스트**: 오라클 테스트의 입력 목록과 플래그 표(7절)를 그대로 쓴다. 기댓값은 코어가 직접 내고(같은 프로세스에서 `format_an_inst`, 또는 vendor `spim -dump`/`-file`), 추가로 `tests/golden/text-*.txt`의 주소·워드와 비교한다. 에러 케이스는 `tests/samples/{editor-errors,syntax-error-midfile}.s`.
9. **토큰(Swing)**: 1.1~1.6의 표를 `tokens.h` 값으로 옮긴다(text-2 #5A6472, text-muted #65707E). 문서의 옛 값은 쓰지 않는다. 라이트 전용이므로 다크는 새로 설계하고 대비 근거를 남긴다. 고정폭 표·D2Coding에는 굵기를 쓰지 않는다(변경값 = #00736F 글자 + #E6F6F5 배경). 레지스터 8그룹 순서와 설명문도 재사용한다.
10. **글꼴**: Pretendard 4종 + D2Coding 2종(OFL 파일 포함) `QtSpim/edu/theme/fonts/`. Swing에서는 `Font.createFont`로 등록하고 고정폭 검사로 거르지 않는다.
11. **CI 자산**: `assets/ci/marks/*.svg`를 그대로 쓴다(자르기 스크립트가 없어 다시 만들 수 없다). 필요한 PNG는 `tools/make-theme-icons.py`처럼 SVG에서 렌더해 커밋한다(런타임 SVG 렌더러 없이). 앱 아이콘 규칙은 흰 타일(라운드 18%, 1px #E1E5EA) + symbol-basic 76%, 16~256px. .ico는 직접 쓴다.
12. **규정 PDF는 ref에 없다.** 규정 근거는 `assets/ci/manual/README.md`(웹페이지 원문 사본)와 이미지 JPG다. hcs에서도 원문 인용과 출처 URL·확인 날짜를 같은 방식으로 남긴다.
