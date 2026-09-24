# 2026-09-25 기본 시나리오 전체(검토 반영 뒤 기준 화면)

- 기준 main 커밋: `218b3d8`
- 관련 PR: #125 라벨 필터, #126 조작 도구 검색, #127 다시 실행, #128 저장 위치·터널 색, #129 용어집, #130 스플리터 팔 라벨, #131 빠른 속성 창·검색 이름
- 찍은 방법: `tools/screenshots/run.sh` (Xvfb 1920×1080, 배율 100%, Pretendard, 한국어). 전체 화면은 1600px로 줄였고 나머지는 원본 해상도 부분이다. `-orig`는 원조 Logisim 2.7.1 jar로 같은 회로·같은 배율·같은 회로 영역을 찍은 것이다.
- 아직 없는 것: 메모리 패널(Data·Stack 전체 내용, $sp 화살표)은 #98로 미구현이라 11번은 부품 화면으로 대신했다.

### 01-first-screen.png
- 앱 첫 화면(빈 캔버스, 도구 모음·탭·상태 표시줄)
- 확인할 것: 도구 모음 글자가 "편집·조작·다시 실행"(용어집)인지, 오른쪽 속성 패널과 상태 표시줄의 "라벨: 전부"
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/01-first-screen.png

### 02-ref-mips-100-orig.png
- 원조 2.7.1, 같은 회로 100%
- 확인할 것: 포크와의 차이는 UI 층뿐인지
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/02-ref-mips-100-orig.png

### 02-ref-mips-100.png
- 참조 MIPS 회로 100%(왼쪽 위)
- 확인할 것: 원조 비교 이미지와 같은 영역인지. 참조 회로는 부품이 넓게 흩어져 100%에서는 일부만 보인다
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/02-ref-mips-100.png

### 02b-ref-mips-25-orig.png
- 원조 2.7.1, 같은 회로 25%
- 확인할 것: 원조 라벨은 이 배율에서 읽히지 않는다(비교)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/02b-ref-mips-25-orig.png

### 02b-ref-mips-25.png
- 참조 회로 25%(포크 최소 배율)
- 확인할 것: 라벨 칩이 작은 배율에서도 읽히는 크기인지
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/02b-ref-mips-25.png

### 03a-register-sp-200-orig.png
- 원조 2.7.1, 같은 영역
- 확인할 것: 원조에서 라벨이 있던 자리와 비교
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/03a-register-sp-200-orig.png

### 03a-register-sp-200.png
- 레지스터 $29 부분 200%
- 확인할 것: 라벨 칩 "$29"가 부품·다른 칩과 겹치지 않는지, 원조 라벨 글자가 두 번 보이지 않는지(D-040)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/03a-register-sp-200.png

### 03b-adder-200-orig.png
- 원조 2.7.1, 같은 영역
- 확인할 것: 터널 글자가 포크에서도 그대로 보이는지
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/03b-adder-200-orig.png

### 03b-adder-200.png
- 가산기 부분 200%
- 확인할 것: 터널 자동 색(같은 이름 = 같은 색)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/03b-adder-200.png

### 03c-imem-tunnels-200-orig.png
- 원조 2.7.1, 같은 영역
- 확인할 것: 비교
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/03c-imem-tunnels-200-orig.png

### 03c-imem-tunnels-200.png
- 명령어 메모리와 터널 200%
- 확인할 것: 터널 색 칠이 글자를 가리지 않는지
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/03c-imem-tunnels-200.png

### 03d-subcircuit-200.png
- 서브회로 half_adder 200%(tests/circ/subcircuit.circ)
- 확인할 것: 상자 안 포트 이름(a, b, s…, c…)과 상자 아래 회로 이름 캡션. 참조 회로에는 서브회로가 없어 이 파일로 찍었다
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/03d-subcircuit-200.png

### 03e-splitter-bus-200-orig.png
- 원조 2.7.1, 같은 영역
- 확인할 것: 원조 스플리터의 작은 "0-5" 표시와 비교
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/03e-splitter-bus-200-orig.png

### 03e-splitter-bus-200.png
- 32비트 명령어 스플리터 200%
- 확인할 것: 팔 라벨 [31:26]…[5:0]이 팔 끝에 붙는지, 터널과 겹치는 정도
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/03e-splitter-bus-200.png

### 04a-menu-port.png
- 우클릭: 게이트의 빈 포트
- 확인할 것: "in1에 붙이기", "입력 in1 부정"이 있는지
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/04a-menu-port.png

### 04b-menu-gate.png
- 우클릭: 게이트 몸체
- 확인할 것: 입력 수·크기·방향·데이터 비트·게이트 종류 바꾸기
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/04b-menu-gate.png

### 04c-menu-wire.png
- 우클릭: 선
- 확인할 것: 넷 정보, 넷 전체 선택, 선을 터널로, 프로브 붙이기
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/04c-menu-wire.png

### 04d-menu-empty.png
- 우클릭: 빈 곳
- 확인할 것: 붙여넣기, 화면에 맞추기
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/04d-menu-empty.png

### 05a-quick-attrs-dock-open.png
- 레지스터 선택: 빠른 속성 창 + 오른쪽 속성 패널(펼침)
- 확인할 것: 빠른 속성 창이 부품 위에 뜨는지(우클릭 메뉴 뒤에도)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/05a-quick-attrs-dock-open.png

### 05b-quick-attrs-crop.png
- 빠른 속성 창 부분
- 확인할 것: 속성 단추와 숨은 단축키 줄(Alt+0–9: 데이터 비트, F2: 라벨)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/05b-quick-attrs-crop.png

### 05c-dock-collapsed.png
- 속성 패널 접힘
- 확인할 것: 오른쪽에 좁은 띠만 남는지
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/05c-dock-collapsed.png

### 06a-palette-mux32.png
- 검색 "mux 32"
- 확인할 것: 한국어 이름("멀티플렉서 데이터 비트 32")으로 보이는지
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/06a-palette-mux32.png

### 06b-palette-reset.png
- 명령 검색 "리셋"
- 확인할 것: "시뮬레이션 리셋 명령"
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/06b-palette-reset.png

### 07a-toolbar.png
- 도구 모음 부분
- 확인할 것: 아이콘과 글자가 읽히는지, 되돌리기 옆 "다시 실행"
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/07a-toolbar.png

### 07b-status-bar.png
- 상태 표시줄 부분
- 확인할 것: 시뮬레이션 상태, 사이클, 배율, 선 색, 라벨 밀도
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/07b-status-bar.png

### 08a-splitter-before-200.png
- 새 파일에 놓은 32비트 스플리터(팔 4개) 200%
- 확인할 것: 편집 전 팔 라벨
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/08a-splitter-before-200.png

### 08b-splitter-editor-ranges.png
- 스플리터 편집기: 범위 "31:26, 25:21, 20:16, 15:0"
- 확인할 것: 비트 그림과 팔 목록이 범위대로인지
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/08b-splitter-editor-ranges.png

### 08c-splitter-editor-r-type.png
- 스플리터 편집기: MIPS R형 프리셋
- 확인할 것: op rs rt rd shamt funct 여섯 팔과 이름
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/08c-splitter-editor-r-type.png

### 08d-splitter-arm-labels-200.png
- 적용 뒤 캔버스 200%
- 확인할 것: 팔 끝의 "[31:26] op" … "[5:0] funct"
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/08d-splitter-arm-labels-200.png

### 09a-find-pc.png
- Ctrl+F "PC"
- 확인할 것: 종류·경로 표시. 같은 이름 터널이 여러 줄로 똑같이 보이는 점(개선 거리)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/09a-find-pc.png

### 09b-tunnel-names.png
- 터널 이름 목록
- 확인할 것: 이름과 개수
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/09b-tunnel-names.png

### 10-file-tabs-top.png
- 탭 줄 부분(원본 해상도)
- 확인할 것: 탭 이름이 읽히는지
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/10-file-tabs-top.png

### 10-file-tabs.png
- 파일 탭 5개(ref-mips, subcircuit, register, values, gates)
- 확인할 것: 탭 줄
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/10-file-tabs.png

### 11a-load-summary.png
- .s 불러오기 요약(tests/mips/factorial.s)
- 확인할 것: .text·.data 워드 수와 한국어 부품 이름
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/11a-load-summary.png

### 11b-imem.png
- 명령어 메모리(45사이클 실행 뒤) 200%
- 확인할 것: 27 워드, 지금 주소와 워드
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/11b-imem.png

### 11c-dmem.png
- 데이터 메모리 200%
- 확인할 것: 영역 표시
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/11c-dmem.png

### 11d-stack-mid-recursion.png
- 스택, 재귀 실행 중(45사이클) 200%
- 확인할 것: 깊이·최대 표시. 깊이에 SPIM 초기 $sp 위의 4KB가 포함되어 보이는 점(확인 거리)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/11d-stack-mid-recursion.png

### 11e-console.png
- 콘솔, 실행 중
- 확인할 것: 아직 출력 없음
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/11e-console.png

### 11f-stack-end.png
- 스택, 끝난 뒤
- 확인할 것: 최대 깊이
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/11f-stack-end.png

### 11g-console-end.png
- 콘솔, 끝난 뒤
- 확인할 것: "5! = 720"과 "-- 종료 --"(Syscall 터널이 글자 일부를 가림)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/11g-console-end.png

### 11h-ref-mips-after-run.png
- 실행 뒤 전체 화면
- 확인할 것: 상태 표시줄의 사이클 445, 프로그램 factorial.s
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/11h-ref-mips-after-run.png

### 12a-hover-component.png
- 마우스 오버: AND 게이트
- 확인할 것: 경로 main › AND #1, 입력 수·폭, 넷 이름
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/12a-hover-component.png

### 12b-hover-port.png
- 마우스 오버: 포트
- 확인할 것: 포트 이름과 폭
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/12b-hover-port.png

### 13-keys-table.png
- ? 단축키 표
- 확인할 것: 다시 실행(Ctrl+Y / Ctrl+Shift+Z), F2 줄
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/13-keys-table.png
