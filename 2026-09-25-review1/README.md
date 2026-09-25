# 2026-09-25 스크린샷 검토 1차 반영

- 기준 main 커밋: `cf50246`
- 관련 PR: #136(1 화면 구성) #137(2 캔버스 겹침, #133·#134) #138(3 터널 색) #139(4 우클릭 메뉴) #140(6 #135 찾기 묶음) #141(5 데모 회로, 서브회로 포트 글자)
- 이전 촬영: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/README.md
- "확인할 것"의 [n]은 검토 요청의 항목 번호다(1 화면 구성, 2 캔버스 겹침, 3 터널 색, 4 우클릭 메뉴, 5 데모 회로, 6 #133·#134·#135).
- [3] 터널 색: 03a·03e·02에서 가까운 다른 이름 터널(예: MemWrite·MemRead·clk, pc·RegWrite)이 서로 다른 색이고 테두리가 원색인지 본다.
- 아직 없는 것: 메모리 패널(#98).

### 01-first-screen.png
- 앱 첫 화면
- 확인할 것: [1] 위쪽 줄이 메뉴·도구 모음(창 전체 폭)·파일 탭·회로 탭 넷인지, 원조 도구 모음·탐색 창 아이콘 줄·왼쪽 아래 배율 칸이 없는지, 상태 표시줄이 창 전체 폭이고 배율(100%) 하나인지
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/01-first-screen.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/01-first-screen.png

### 01b-tree-search-mux.png
- 부품 트리 위 검색창에 "mux"
- 확인할 것: [1] 트리 자리에 한국어 이름으로 걸러진 목록(멀티플렉서·디멀티플렉서)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/01b-tree-search-mux.png

### 02-demo-fit-orig.png
- 원조 2.7.1, 같은 회로 같은 배율
- 확인할 것: [5] 원조에서 열리고 같은 배선인지(원조 라벨 비교)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/02-demo-fit-orig.png

### 02-demo-fit.png
- 데모 회로(tests/circ/demo-datapath.circ) "화면 맞춤"
- 확인할 것: [5] 선으로 이은 PC·+4·명령어 메모리·R형 스플리터·레지스터 파일·ALU·데이터 메모리·MemtoReg MUX, 터널은 제어선·clk뿐. [1] 상태 표시줄 배율이 실제 배율(81%)과 같은지(02b 25%/100% 버그)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/02-demo-fit.png

### 03a-pc-adder-200-orig.png
- 원조 2.7.1 같은 영역
- 확인할 것: 비교
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/03a-pc-adder-200-orig.png

### 03a-pc-adder-200.png
- PC·+4 부분 200%
- 확인할 것: [2] 라벨 칩, 터널 색과 테두리, 버스 이름 pc[31:0]
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/03a-pc-adder-200.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/03a-register-sp-200.png

### 03b-splitter-arms-200-orig.png
- 원조 2.7.1 같은 영역
- 확인할 것: 원조의 "26-31" 표시(비교)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/03b-splitter-arms-200-orig.png

### 03b-splitter-arms-200.png
- R형 스플리터 200%
- 확인할 것: [2] 팔 라벨 "[31:26] op"… 한 벌만(원조 "26-31" 표시가 없어야 함)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/03b-splitter-arms-200.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/03e-splitter-bus-200.png

### 03c-regfile-box-200-orig.png
- 원조 2.7.1 같은 영역
- 확인할 것: 원조 기본 상자(포트 이름 없음) 비교
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/03c-regfile-box-200-orig.png

### 03c-regfile-box-200.png
- 레지스터 파일 서브회로 200%
- 확인할 것: [2] 상자 안 포트 이름 RR1·RR2·WR·WD·RD1·RD2가 읽히는지, 회로 이름 캡션 regfile
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/03c-regfile-box-200.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/03d-subcircuit-200.png

### 03d-alu-box-200-orig.png
- 원조 2.7.1 같은 영역
- 확인할 것: 비교
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/03d-alu-box-200-orig.png

### 03d-alu-box-200.png
- ALU 서브회로 200%
- 확인할 것: [2] A·B·ALUOp, Result·Zero 포트 이름과 캡션 alu
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/03d-alu-box-200.png

### 03e-dmem-tunnels-200-orig.png
- 원조 2.7.1 같은 영역
- 확인할 것: 비교
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/03e-dmem-tunnels-200-orig.png

### 03e-dmem-tunnels-200.png
- 데이터 메모리 200%
- 확인할 것: [2] 포트 이름(Addr·WriteData·ReadData·MemWrite·MemRead)이 테두리에서 떨어져 터널 글자와 안 겹치는지, 부품 제목이 "데이터 메모리"(#133)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/03e-dmem-tunnels-200.png

### 04a-menu-port.png
- 우클릭: 게이트의 빈 포트
- 확인할 것: [4] 맨 위 요약 "AND #1 · 입력 in1 · 1비트", 대상별 → 공통(복제·속성 패널에서 보기) → 삭제 맨 아래
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/04a-menu-port.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/04a-menu-port.png

### 04b-menu-gate.png
- 우클릭: 게이트 몸체
- 확인할 것: [4] 요약 "AND #1 · 입력 2개 · 1비트", 순서
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/04b-menu-gate.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/04b-menu-gate.png

### 04c-menu-wire.png
- 우클릭: 선
- 확인할 것: [4] 요약 "선 · 1비트", 삭제 맨 아래
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/04c-menu-wire.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/04c-menu-wire.png

### 04d-menu-empty.png
- 우클릭: 빈 곳
- 확인할 것: [4] 요약 "빈 곳 · main"
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/04d-menu-empty.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/04d-menu-empty.png

### 05a-quick-attrs-dock-open.png
- 데모 회로 PC 선택: 빠른 속성 창 + 오른쪽 속성 패널
- 확인할 것: [2] 빠른 속성 창이 PC 라벨 칩·부품을 덮지 않는 자리(왼쪽)로 옮겨졌는지
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/05a-quick-attrs-dock-open.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/05a-quick-attrs-dock-open.png

### 05b-quick-attrs-crop.png
- 빠른 속성 창 부분
- 확인할 것: [2] 칩 "PC"와 선택 테두리를 덮지 않는지
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/05b-quick-attrs-crop.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/05b-quick-attrs-crop.png

### 05c-dock-collapsed.png
- 속성 패널 접힘
- 확인할 것: [1] 상태 표시줄·도구 모음은 그대로 창 전체 폭
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/05c-dock-collapsed.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/05c-dock-collapsed.png

### 06a-palette-mux32.png
- 검색 "mux 32"
- 확인할 것: 한국어 이름
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/06a-palette-mux32.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/06a-palette-mux32.png

### 06b-palette-reset.png
- 명령 검색 "리셋"
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/06b-palette-reset.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/06b-palette-reset.png

### 07a-toolbar.png
- 도구 모음 부분
- 확인할 것: [1] 편집·조작·배선·텍스트(원조 도구 모음에서 옮김)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/07a-toolbar.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/07a-toolbar.png

### 07b-status-bar.png
- 상태 표시줄 부분
- 확인할 것: [1] 배율 단추 하나(옛 칸 없음)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/07b-status-bar.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/07b-status-bar.png

### 07c-zoom-menu.png
- 상태 표시줄 배율 단추를 누른 메뉴
- 확인할 것: [1] 단계·화면 맞춤·격자 보이기
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/07c-zoom-menu.png

### 08a-splitter-before-200.png
- 새 32비트 스플리터(팔 4개) 200%
- 확인할 것: [2] 팔 라벨 한 벌
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/08a-splitter-before-200.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/08a-splitter-before-200.png

### 08b-splitter-editor-ranges.png
- 스플리터 편집기: 범위 입력
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/08b-splitter-editor-ranges.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/08b-splitter-editor-ranges.png

### 08c-splitter-editor-r-type.png
- 스플리터 편집기: R형 프리셋
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/08c-splitter-editor-r-type.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/08c-splitter-editor-r-type.png

### 08d-splitter-arm-labels-200.png
- 적용 뒤 캔버스 200%
- 확인할 것: [2] "[31:26] op" 등만 보이고 원조 "26-31" 표시는 없는지
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/08d-splitter-arm-labels-200.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/08d-splitter-arm-labels-200.png

### 09a-find-pc.png
- Ctrl+F "PC"(ref-mips)
- 확인할 것: [6 #135] 같은 이름은 한 줄 "(6곳 · 누르면 펼침)"
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/09a-find-pc.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/09a-find-pc.png

### 09b-find-pc-expanded.png
- 첫 묶음을 누른 모습
- 확인할 것: [6 #135] 위치별 줄로 펼침
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/09b-find-pc-expanded.png

### 09c-tunnel-names.png
- 터널 이름 목록
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/09c-tunnel-names.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/09b-tunnel-names.png

### 10-file-tabs-top.png
- 위쪽 줄 부분(원본 해상도)
- 확인할 것: [1] 메뉴·도구 모음·파일 탭·회로 탭 네 줄
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/10-file-tabs-top.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/10-file-tabs-top.png

### 10-file-tabs.png
- 파일 탭 여러 개
- 확인할 것: [1] 탭 줄이 도구 모음 바로 아래
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/10-file-tabs.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/10-file-tabs.png

### 11a-load-summary.png
- .s 불러오기 요약(factorial.s)
- 확인할 것: [6 #133] "명령어 메모리", "데이터 메모리"
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/11a-load-summary.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/11a-load-summary.png

### 11b-imem.png
- 명령어 메모리 200%
- 확인할 것: [6 #133] 제목 "명령어 메모리", [2] 포트 이름 안쪽, 터널은 바깥(ref-mips 생성기 수정)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/11b-imem.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/11b-imem.png

### 11c-dmem.png
- 데이터 메모리 200%
- 확인할 것: [2]
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/11c-dmem.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/11c-dmem.png

### 11d-stack-mid-recursion.png
- 스택, 재귀 중 200%
- 확인할 것: [6 #134] 깊이가 SPIM 시작 $sp 기준(예전 4156 B → 56 B)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/11d-stack-mid-recursion.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/11d-stack-mid-recursion.png

### 11e-console.png
- 콘솔, 실행 중
- 확인할 것: [2] 출력 칸이 포트에서 떨어진 안쪽
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/11e-console.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/11e-console.png

### 11f-stack-end.png
- 스택, 끝난 뒤
- 확인할 것: [6 #134] 최대 56 B
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/11f-stack-end.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/11f-stack-end.png

### 11g-console-end.png
- 콘솔, 끝난 뒤
- 확인할 것: [2] "6! = 720"이 Syscall 터널에 가리지 않는지
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/11g-console-end.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/11g-console-end.png

### 11h-ref-mips-after-run.png
- 실행 뒤 전체
- 확인할 것: 상태 표시줄 사이클·프로그램
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/11h-ref-mips-after-run.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/11h-ref-mips-after-run.png

### 12a-hover-component.png
- 마우스 오버: regfile 서브회로(데모)
- 확인할 것: 경로·포트·넷
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/12a-hover-component.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/12a-hover-component.png

### 12b-hover-port.png
- 마우스 오버: regfile 포트
- 확인할 것: 포트 이름과 폭
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/12b-hover-port.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/12b-hover-port.png

### 13-keys-table.png
- ? 단축키 표
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/13-keys-table.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-baseline/13-keys-table.png
