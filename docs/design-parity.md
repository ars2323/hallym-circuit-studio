# 디자인 정합(Z-12b): Hallym MIPS와 같은 재료

통일감은 같은 재료(값·글꼴·아이콘·치수)에서 나온다. 값은 Hallym MIPS **v2.3.0**의 `electron/src/renderer/app/app.css`(태그 v2.3.0, 저장소 ars2323/hallym-mips-simulator)에서 직접 가져온다. 줄 번호는 그 태그의 app.css 기준이다.

- 우리 값은 `app/src-hcs/kr/ac/hallym/hcs/app/theme/Tokens.java` 한 곳에 두고, FlatLaf 설정은 `theme/flatlaf.properties` 한 파일에 모은다.
- `DesignParityTest`가 아래 표의 값과 `Tokens` 값을 한 줄씩 비교한다. 판정이 "같음"인 줄은 값이 같아야 하고, 일부러 다르게 둔 줄은 판정 칸에 "다름:"과 이유를 적는다.
- 글꼴: Pretendard(UI)와 D2Coding(주소·기계어·레지스터 값, OFL, 원본 TTF 그대로 번들). 아이콘: Lucide(ISC) SVG를 같은 이름으로 번들해 FlatSVGIcon으로 그린다. Hallym MIPS가 쓰는 15개는 그 파일 그대로이고, 도구 모음처럼 Hallym MIPS에 없는 자리는 같은 Lucide 판(lucide-static 1.48.0)에서 가져온다.
- 캐릭터 크기는 app.css가 아니라 코드에서 정한다: 시작 카드 200px(`panels/welcome.ts` 44줄), 빈 상태 120px(`notice.ts` 17줄 `NOTICE_CHARACTER`), 묻는 대화상자 96px(`panels/ask.ts` 32줄), 튜토리얼 카드 76px(`tutorial.ts` 401줄). 이것도 표에 있다.

## 값 표

| 값 | Hallym MIPS v2.3.0 | app.css 줄 | 우리 Tokens | 판정 |
| --- | --- | --- | --- | --- |
| `--navy` | `#00205b` | 20 | `NAVY` | 같음 |
| `--blue` | `#0055a5` | 20 | `BLUE` | 같음 |
| `--teal` | `#00a9a5` | 20 | `TEAL` | 같음 |
| `--gray` | `#bcbec0` | 20 | `GRAY` | 같음 |
| `--white` | `#ffffff` | 21 | `WHITE` | 같음 |
| `--window` | `#f5f7fa` | 21 | `WINDOW` | 같음 |
| `--border` | `#e1e5ea` | 21 | `BORDER` | 같음 |
| `--hover` | `#f3f6f9` | 21 | `HOVER` | 같음 |
| `--text` | `#1f2933` | 22 | `TEXT` | 같음 |
| `--text-2` | `#5a6472` | 22 | `TEXT_2` | 같음 |
| `--muted` | `#65707e` | 22 | `TEXT_MUTED` | 같음 |
| `--dim` | `#a9b1bb` | 22 | `DIM` | 같음 |
| `--blue-tint` | `#e8f0f9` | 23 | `BLUE_TINT` | 같음 |
| `--blue-tint2` | `#d3e2f3` | 23 | `BLUE_TINT_2` | 같음 |
| `--teal-tint` | `#e6f6f5` | 23 | `TEAL_TINT` | 같음 |
| `--teal-text` | `#00736f` | 23 | `TEAL_TEXT` | 같음 |
| `--amber-tint` | `#fdf3e1` | 24 | `AMBER_TINT` | 같음 |
| `--amber-text` | `#8a5a00` | 24 | `AMBER_TEXT` | 같음 |
| `--purple` | `#6b4c9a` | 24 | `PURPLE` | 같음 |
| `--cp0-tint` | `#eef0f2` | 24 | `CP0_TINT` | 같음 |
| `--cp0-text` | `#4a5560` | 24 | `CP0_TEXT` | 같음 |
| `--error` | `#c0392b` | 25 | `ERROR` | 같음 |
| `--error-tint` | `#fbeae8` | 25 | `ERROR_TINT` | 같음 |
| `--error-text` | `#8e2a1f` | 25 | `ERROR_TEXT` | 같음 |
| `--changed` | `#fff1b8` | 26 | `CHANGED` | 같음 |
| `--changed-bar` | `#e0a100` | 26 | `CHANGED_BAR` | 같음 |
| `--changed-text` | `#6b4a00` | 26 | `CHANGED_TEXT` | 같음 |
| `--ui` | `Pretendard` | 27 | `UI_FONT` | 같음 |
| `--code` | `D2Coding` | 27 | `CODE_FONT` | 같음 |
| `--fs` | `13px` | 28 | `FONT_UI` | 같음 |
| `--row` | `22px` | 28 | `ROW` | 같음 |
| `--rrow` | `21px` | 28 | `REG_ROW` | 같음 |
| `--titlebar` | `40px` | 29 | `TITLEBAR` | 같음 |
| `--head` | `34px` | 29 | `HEAD` | 같음 |
| `상태 표시줄 높이` | `24px` | 45 | `STATUS_BAR` | 같음 |
| `상태 표시줄 글자` | `12px` | 112 | `FONT_SMALL` | 같음 |
| `.btn 높이` | `28px` | 72 | `BUTTON_HEIGHT` | 같음 |
| `.btn 모서리` | `6px` | 72 | `RADIUS` | 같음 |
| `.btn 아이콘` | `16px` | 74 | `BUTTON_ICON` | 같음 |
| `.btn.small 높이` | `26px` | 81 | `BUTTON_SMALL_HEIGHT` | 같음 |
| `.iconbtn 크기` | `30px` | 82 | `ICON_BUTTON` | 같음 |
| `.iconbtn 아이콘` | `18px` | 84 | `ICON_BUTTON_ICON` | 같음 |
| `.seg 모서리` | `8px` | 85 | `RADIUS_SEG` | 같음 |
| `.panel 모서리` | `8px` | 124 | `RADIUS_PANEL` | 같음 |
| `.phead 왼쪽 여백` | `12px` | 125 | `HEAD_PAD_LEFT` | 같음 |
| `.phead 오른쪽 여백` | `8px` | 125 | `HEAD_PAD_RIGHT` | 같음 |
| `.ptab 가로 여백` | `10px` | 128 | `TAB_PAD` | 같음 |
| `.ptab 밑줄` | `2px` | 129 | `TAB_UNDERLINE` | 같음(CSS의 border-bottom 2px(.ptab)) |
| `.hbtn 높이` | `24px` | 136 | `HEAD_BUTTON` | 같음 |
| `.notice 간격` | `24px` | 480 | `NOTICE_GAP` | 같음 |
| `.notice 최대 폭` | `640px` | 480 | `NOTICE_MAX_WIDTH` | 같음 |
| `.notice 제목 글자` | `16px` | 216 | `NOTICE_TITLE` | 같음 |
| `.notice 캐릭터 숨김 높이` | `156px` | 486 | `NOTICE_MIN_HEIGHT` | 같음 |
| `.notice 캐릭터 숨김 폭` | `380px` | 486 | `NOTICE_MIN_WIDTH` | 같음 |
| `.wcard 폭` | `780px` | 493 | `START_CARD_WIDTH` | 같음 |
| `.wcard 모서리` | `14px` | 493 | `START_CARD_RADIUS` | 같음 |
| `.wcard 세로 여백` | `36px` | 494 | `START_CARD_PAD_V` | 같음 |
| `.wcard 가로 여백` | `40px` | 494 | `START_CARD_PAD_H` | 같음 |
| `.wcard 캐릭터 칸` | `200px` | 493 | `START_CHARACTER` | 같음(welcome.ts의 character('hello', 200)과 같다) |
| `.wcard 칸 사이` | `40px` | 493 | `START_CARD_GAP` | 같음 |
| `.wcard h1 글자` | `24px` | 495 | `START_TITLE` | 같음 |
| `.wcard 설명 글자` | `14px` | 496 | `START_LEAD` | 같음 |
| `.action 폭` | `220px` | 498 | `ACTION_WIDTH` | 같음 |
| `.action 높이` | `84px` | 498 | `ACTION_HEIGHT` | 같음 |
| `.action 모서리` | `10px` | 498 | `ACTION_RADIUS` | 같음 |
| `.action 아이콘` | `20px` | 500 | `ACTION_ICON` | 같음 |
| `.actions 간격` | `12px` | 497 | `ACTION_GAP` | 같음 |
| `.modal 폭` | `560px` | 508 | `DIALOG_WIDTH` | 같음 |
| `.modal 모서리` | `12px` | 508 | `DIALOG_RADIUS` | 같음 |
| `.modal.ask 폭` | `480px` | 510 | `ASK_WIDTH` | 같음 |
| `.askbody 간격` | `18px` | 511 | `ASK_GAP` | 같음 |
| `.asktext h2 글자` | `17px` | 513 | `ASK_TITLE` | 같음 |
| `.modal h2 글자` | `18px` | 519 | `DIALOG_TITLE` | 같음 |
| `.modal 뒤 덮개 불투명도(%)` | `35%` | 509 | `BACKDROP_ALPHA` | 같음 |
| `튜토리얼 덮개 불투명도(%)` | `26%` | 553 | `TUTORIAL_DIM_ALPHA` | 같음 |
| `.tut-ring 선 굵기` | `2px` | 555 | `RING_WIDTH` | 같음 |
| `.tut-ring 모서리` | `6px` | 555 | `RADIUS` | 같음 |
| `.tut-card 폭` | `310px` | 557 | `TUTORIAL_CARD_WIDTH` | 같음 |
| `.tut-card 모서리` | `12px` | 558 | `TUTORIAL_CARD_RADIUS` | 같음 |
| `.tut-card.kind-end 폭` | `420px` | 570 | `TUTORIAL_END_WIDTH` | 같음 |
| `.tut-say h3 글자` | `15px` | 567 | `FONT_TITLE` | 같음 |
| `.tut-buttons 간격` | `6px` | 569 | `TUTORIAL_BUTTON_GAP` | 같음 |
| `opcode·fmt 바탕` | `#dfe5ef` | 419 | `FIELD_OPCODE_BG` | 같음 |
| `opcode·fmt 글자` | `#00205b` | 419 | `FIELD_OPCODE_FG` | 같음 |
| `rs 바탕` | `#e8f0f9` | 420 | `FIELD_RS_BG` | 같음 |
| `rs 글자` | `#0055a5` | 420 | `FIELD_RS_FG` | 같음 |
| `rt·ft 바탕` | `#e6f6f5` | 421 | `FIELD_RT_BG` | 같음 |
| `rt·ft 글자` | `#00736f` | 421 | `FIELD_RT_FG` | 같음 |
| `rd·fs 바탕` | `#fdf3e1` | 422 | `FIELD_RD_BG` | 같음 |
| `rd·fs 글자` | `#8a5a00` | 422 | `FIELD_RD_FG` | 같음 |
| `shamt·fd 바탕` | `#efe9f6` | 423 | `FIELD_SHAMT_BG` | 같음 |
| `shamt·fd 글자` | `#6b4c9a` | 423 | `FIELD_SHAMT_FG` | 같음 |
| `funct 바탕` | `#eef0f2` | 424 | `FIELD_FUNCT_BG` | 같음 |
| `funct 글자` | `#4a5560` | 424 | `FIELD_FUNCT_FG` | 같음 |
| `immediate·target·offset 바탕` | `#e3eef0` | 425 | `FIELD_IMM_BG` | 같음 |
| `immediate·target·offset 글자` | `#1d5c63` | 425 | `FIELD_IMM_FG` | 같음 |
| `시작 카드 캐릭터` | `200px` | welcome.ts 44 | `START_CHARACTER` | 같음 |
| `빈 상태 캐릭터` | `120px` | notice.ts 17 | `NOTICE_CHARACTER` | 같음 |
| `묻는 대화상자 캐릭터` | `96px` | ask.ts 32 | `ASK_CHARACTER` | 같음 |
| `튜토리얼 카드 캐릭터` | `76px` | tutorial.ts 401 | `TUTORIAL_CHARACTER` | 같음 |

## 우리에게만 있는 값(Hallym MIPS에 짝이 없음)

| Tokens | 값 | 이유 |
| --- | --- | --- |
| `SCROLL`, `SCROLL_HOVER` | `#c9d0d8`, `#aeb7c2` | Hallym MIPS는 Chromium 기본 스크롤바를 쓴다. Swing은 직접 그려야 해서 Qt판(tokens.h) 값을 쓴다 |
| `TAB_INACTIVE` | `#eaeef3` | 파일 탭(여러 파일)은 Hallym MIPS에 없다 |
| `WARNING` | `#b7791f` | 회로 캔버스의 경고 표시(Hallym MIPS에는 캔버스가 없다) |
| `SPACE_1`~`SPACE_6` | 4·8·12·16·24 | 간격 단계. Hallym MIPS도 같은 값들을 CSS에 바로 쓴다 |
| `RADIUS_SMALL` | 4 | 배지·입력칸. Hallym MIPS의 `.status .changed`·`.fbits .bit` 3~4px과 같은 자리 |
| `SCROLLBAR` | 12 | 위 스크롤바와 같은 이유 |
| `TOOL_ICON` | 20 | 캔버스 부품 도구 아이콘(원조 Logisim 도구). Lucide 아이콘이 아니다 |
| `FONT_BADGE`, `FONT_DISPLAY` | 11, 20 | 캔버스 칩 글자, 큰 숫자 |
