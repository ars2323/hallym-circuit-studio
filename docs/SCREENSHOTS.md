# 스크린샷 검토 규칙

UI가 바뀌는 작업은 스크린샷을 저장소에 올리고 보고서와 PR 본문에 링크를 단다. 외부 검토자(원격 Claude)는 GitHub API 없이 `git clone`과 `raw.githubusercontent.com`만으로 확인한다.

## 저장 위치

- 이미지는 `main`이 아니라 orphan 브랜치 `review-shots`에 올린다(main 저장소 크기 보호).
  - 처음 한 번 `git checkout --orphan review-shots`로 만든다.
  - 이후에는 worktree로 관리한다: `git worktree add ../hcs-review-shots review-shots`.
- 구조:
  - `INDEX.md`: 최신 보고가 맨 위. 보고마다 날짜, 기준 main 커밋 해시, 관련 PR·이슈 번호, 폴더 링크.
  - `<YYYY-MM-DD>-<짧은-주제>/README.md`: 그 보고의 이미지 목록. 이미지마다 한 줄 설명과 "무엇을 확인할 것".
  - `<YYYY-MM-DD>-<짧은-주제>/NN-<이름>.png`: 번호 순서대로. 원조 2.7.1로 같은 장면을 찍은 것은 `NN-<이름>-orig.png`로 나란히 둔다.
- 한 번 올린 폴더는 고치지 않는다. 다시 찍으면 새 폴더를 만든다(전후 비교용).
- 링크는 전체 URL을 그대로 적는다: `https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/<폴더>/<파일>`

## 촬영 방법

사람이 누르지 않고 스크립트로 재현한다. `tools/screenshots/`(main 브랜치)의 시나리오 실행기가 같은 장면을 언제든 다시 찍는다.

```
tools/screenshots/run.sh <출력 폴더> [장면 번호 ...]
# 예: tools/screenshots/run.sh build/screenshots/out            # 전체
#     tools/screenshots/run.sh build/screenshots/out 05 06      # 빠른 속성 창과 검색만
```

- **환경:** 가상 화면 Xvfb 1920×1080, 96 dpi, 배율 100%(`sun.java2d.uiScale=1`), Pretendard(앱이 시작할 때 등록), 한국어(`user.language=ko`)로 찍는다. 앱 환경설정·Java 환경설정은 `build/screenshots/work/` 안의 빈 폴더를 쓴다. 개발자 PC 설정을 읽거나 바꾸지 않는다.
- **조작:** `Shots.java`가 앱을 같은 JVM에서 띄운다. 회로 모델에서 부품·선·포트의 화면 좌표를 계산해 Robot으로 누르고 키를 친다. 좌표를 손으로 적지 않는다.
- **찍는 범위:** 앱 창만 찍는다. 창 관리자가 없어 창이 화면 전체(1920×1080)를 덮으므로 바탕화면이나 다른 창이 나오지 않는다. 회로는 저장소 루트에서 상대 경로(`tests/mips/ref-mips.circ`)로 열어 창 제목·화면에 개인 경로가 나오지 않게 한다.
- **크기:** 이미지 하나는 1MB 이하다. 전체 화면은 1600px 폭으로 줄인다. 세부 확인용은 원본 해상도의 부분(crop)을 따로 찍는다. 글자가 읽히지 않는 이미지는 올리지 않는다.
- **원조 비교:** 비교가 필요한 장면(참조 회로 전체, 200% 부분 = 라벨 숨기기 D-040 검증)은 원조 2.7.1 jar로 같은 회로를 같은 비율·같은 회로 영역으로 찍어 `-orig`를 붙인다. 원조가 `hcs-mips.jar`를 찾도록 회로와 jar를 한 폴더에 복사해 연다.
- **데모 회로:** 화면 검토용 장면(02·03·05·12)은 `tests/circ/demo-datapath.circ`로 찍는다. 학생이 그린 것 같은 single-cycle MIPS 일부로, 선으로 잇고 터널은 제어선에만 쓴다. `tests/mips/ref-mips.circ`는 정확성 테스트용이고 .s 실행 장면(11)과 찾기(09)에만 쓴다.

## 기본 시나리오 세트

처음 촬영 때는 전부 찍는다. 이후에는 바뀐 부분과 영향받는 장면만 찍는다.

| 번호 | 장면 |
| --- | --- |
| 01 | 앱 첫 화면(빈 캔버스, 도구 모음·탭·상태 표시줄 전체). 시작 안내가 뜨면 그것도 |
| 02 | 데모 회로 전체("화면 맞춤"), 같은 배율의 원조 2.7.1 |
| 03 | 데모 회로 200% 부분: 라벨 칩, 터널 색, 스플리터 팔 라벨, 서브회로 포트 이름·회로 이름 캡션, MIPS 부품(원조 비교) |
| 04 | 우클릭 메뉴 4장: 포트, 게이트, 선, 빈 곳 |
| 05 | 빠른 속성 창 + 오른쪽 속성 패널(펼침·접힘) |
| 06 | 검색 팔레트 "mux 32", 명령 검색 "리셋" |
| 07 | 도구 모음과 상태 표시줄 부분(아이콘 글자 포함) |
| 08 | 스플리터 편집기: R형 프리셋, 범위 "31:26, 25:21, 20:16, 15:0", 적용 뒤 캔버스의 팔 라벨 |
| 09 | Ctrl+F 찾기 결과, 터널 이름 목록 |
| 10 | 파일 탭 3개 이상 |
| 11 | .s 불러오기 요약, Instruction Memory·Data Memory·Stack·Console 부품(재귀 factorial 실행 중과 끝) |
| 12 | 마우스 오버 정보(부품, 포트) |
| 13 | ? 단축키 표 |
| 15 | 따라오는 배선: 데모의 PC를 끌어 옮기기 전후, rs 선의 가운데 세로 선분을 끌기 전후(끝나면 되돌림) |
| 16 | 연결점과 점프(W-04): 이어지지 않은 교차(1140, 260)를 25·100·400%, 연결점이 있는 영역 25%, PC 출력 넷 강조. 원조 비교(-orig) 포함 |
| 17 | 영향 경로(P-01): regfile에서 앞으로(alu 안 칩, Data Memory에서 멈춤), 한 단계로 좁힘, Data Memory에서 뒤로, PC에서 Through Registers, regfile–Data Memory 사이 경로, alu 안으로 들어가 본 모습 |
| 18 | Signal Flow(P-07): demo PC 출력의 프레임 6장(t=0, 앞단 셋, 닿은 직후, 연속)과 GIF(ImageIO), 터널 점프, 서브회로 경계, Active Path Only(MemtoReg 0·1), Backward(regfile WD), Reduce Motion, 어두운 바탕 대비 그림 |
| 19 | 서브회로 인스턴스 안내(P-02): 탐색기에서 따로 연 regfile의 띠, 이어진 핀 미리 보기, 핀 도구 미리 보기, 실행 중 인스턴스로 간 뒤, 이어진 핀을 지운 뒤 알림 |
| 22 | 기본 모양 서브회로(S-08): 고르면 빠른 속성 창의 Auto Appearance 단추, 마우스 오버의 포트 이름 목록, 단추를 누른 뒤 모양 |
| 20 | 탭 간 라이브러리(P-03): 1bit_adder를 쓰는 ripple_carry, 새 파일에서 검색 "adder"의 Open Files 항목, 불러와 놓은 뒤, 출력 핀을 지우고 저장할 때 경고, 속만 고쳐 저장한 뒤 ripple_carry 탭의 Updated, 인스턴스 우클릭의 Edit Original File |
| 21 | 원조 부품의 포트 이름(S-06, S-07): PC 레지스터와 PC+4 가산기를 100·200·400%로(원조 비교 -orig 포함), 100%에서 가산기에 마우스를 올린 모습 |
| 23 | 제어 핀과 같은 이름의 터널(S-12): demo 왼쪽 제어 핀 다섯 개를 200%로(원조 비교 -orig 포함) |
| 24 | 왼쪽 칸 아래 탭(S-11): 창 전체(Tunnels), 왼쪽 칸 Tunnels, 150%에서 Minimap(보이는 영역 네모) |
| 25 | Cycle View 탭(C-02, C-03): demo-datapath 6사이클, 신호 줄 다섯(clk, pc, halt, alu Result 선, regfile RD1 선), 사이클 2 보기, PC 둘레 확대(마지막·사이클 2) |
| 26 | Run Until(C-04): demo-datapath 리셋 뒤 Run Until… 창(PC Is 0x10), 멈춘 뒤 표와 상태 표시줄 알림 |
| 27 | 레지스터·메모리 패널(C-05, C-06): demo regfile 표시 뒤 Registers 탭·Register Mapping 창, stack-demo 6사이클 뒤 Memory 탭(Stack)·Registers 탭(표시 없음) |
| 28 | Console 탭·.s 자동 재로드(C-09): console-demo exit까지 뒤 Console 탭, demo-datapath에 불러온 .s를 고친 뒤 상태 표시줄 알림 |
| 29 | 명령어 필드 색(C-07): demo-datapath에서 R 형식 명령어 사이클의 Instruction 탭과 캔버스 필드 색 띠(rs → RR1, rt → RR2, rd → WR) |
| 30 | 버스 값 칩과 활성 경로(C-08): demo-datapath 두 사이클 뒤 버스 값 칩(Hex, Signed), MemtoReg MUX가 고른 입력 진한 띠, 끈 모습 |
| 31 | 동적 진단(D-01·D-03·D-05): demo-datapath의 RegWrite 핀을 3상태로 두고 돌린 뒤 Messages 한 줄, 누른 뒤 사이클 뷰와 원인 선택, 선 우클릭 Find E/X Origin과 알림 |
| 32 | 진동과 MIPS 부품 값(D-02·D-04): 고장 회로 모음의 NAND 되먹임 진동 메시지와 Reset 단추, 정렬 안 된 주소를 읽는 Data Memory 메시지와 몸체 빨간 글자 |
| 33 | About 창(E-11): 엠블럼·이름·버전·설명·캐릭터, License·Notices 탭 |
| 34 | Undo History(E-05)와 단축키(E-09): 기록 창(되돌릴 것·Now·다시 실행할 것), ? 표, Customize…로 연 설정 창 |
| 35 | 배치 편집(E-01·E-02): 우클릭 Duplicate N…, 창, R0 → R1~R3, 여러 개 우클릭(Align·Distribute), Align › Left 결과 |
| 36 | 제출 파일(E-06)과 그림 내보내기(E-07): 점검 창(저장·Messages·Probe·원조에서 열림)과 묶을 파일, Export Image 창, 2배 PNG 결과 |
| 37 | 버스 폭과 선 색 범례(E-03): 굵은 버스와 비트 수 표시(전체, 150%), Wire Colors 범례 |
| 38 | 신호 그룹 색(E-04): RegWrite=Control, ALU 결과=Data, PC→명령어 메모리=Address로 정한 뒤 Colors: Groups(전체, 150%), 선 우클릭 Signal Group 하위 메뉴 |
| 39 | 영역 메모(E-08): IF·EX 영역 상자(전체, 150%), 메모 안 우클릭 메뉴, Add Area Memo… 창 |
| 40 | 첫 실행 튜토리얼(E-10): 첫 장(캐릭터), 도구 모음 단계, Messages 탭 단계 |
| 41 | 창 분리·나란히 보기(P-06): 탭 우클릭 메뉴, 분리한 창(· Window 배지, console-demo), 나란히 보기(왼쪽·오른쪽 반) |
| 42 | 포트 순서(P-04): 서브회로 우클릭 Port Order… 창(변마다 목록), Cin을 맨 위로 옮긴 뒤의 regfile 모양 |
| 43 | 서브회로 가져오기(P-05): 회로 고르기 창, 계획 창(딸린 회로·새 이름) |
| 14 | Messages 탭: 데모 회로를 두 곳 망가뜨린 뒤(터널 이름 틀림, PC 클럭 지움) 목록, 메시지를 눌러 강조한 캔버스, 표시가 배율 25·100·400%에서 보이는지(14f), 끝나면 되돌림. gateUndefined = error 회로의 빈 게이트 입력(14e) |

## 올리기와 보고

1. `tools/screenshots/run.sh build/screenshots/<폴더 이름>`으로 찍고, 이미지를 눈으로 본다. 그 전에 `make -s -C native/hcs-asm`으로 `hcs-asm`을 빌드해 둔다(.s 장면 11·28이 쓴다). 글자가 안 읽히거나 장면이 잘못 잡힌 것은 올리지 않는다.
2. `review-shots` worktree에 `<YYYY-MM-DD>-<주제>/`를 새로 만들어 이미지를 복사하고, README.md(이미지별 설명과 확인할 것, raw URL)를 쓴다. INDEX.md 맨 위에 한 항목을 더하고 커밋·push한다.
3. 세션 보고서 끝의 "스크린샷" 절에 INDEX.md 링크, 이번 폴더 링크, 이미지별 한 줄 설명을 적는다.
4. UI가 바뀐 PR 본문에도 해당 이미지 raw URL을 넣는다.
