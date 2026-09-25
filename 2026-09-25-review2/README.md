# 2026-09-25 스크린샷 검토 2차 반영 + 언어 방침

- 기준 main 커밋: `84298fc`
- 관련 PR: #148(A 언어 방침) #150(B Stack) #152(C Auto Appearance) #151(D 찾기 위치) #149(E 빠른 속성 창·팔 라벨) #153(A 보강: Swing 버튼 이름, 찾기 묶음 줄)
- 이전 촬영: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/README.md
- "확인할 것"의 [A]~[E]는 검토 요청의 항목이다(A 언어 방침, B 스택 표시, C Auto Appearance, D 찾기 결과 위치, E 빠른 속성 창·팔 라벨).
- [A] 원칙: 이름·명령은 영어, 설명 문장만 한국어(D-049, docs/GLOSSARY.md). 한국어로 남은 것은 부품 찾기 칸 안내, 단축키 표 설명, 요약·오류 문장, 마우스 오버 설명이다.

### 01-first-screen.png
- 앱 첫 화면
- 확인할 것: [A] 메뉴·도구 모음·탭·상태 표시줄·Attributes 패널·Quick Attributes가 영어인지. 부품 찾기 칸의 안내 문장만 한국어
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/01-first-screen.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/01-first-screen.png

### 01b-tree-search-mux.png
- 부품 트리 검색 "mux"
- 확인할 것: [A] 걸러진 부품 이름이 영어(Multiplexer, Demultiplexer)인지
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/01b-tree-search-mux.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/01b-tree-search-mux.png

### 02-demo-fit.png
- 데모 회로 Fit to Window
- 확인할 것: [C] regfile·alu 상자가 포트 이름·회로 이름이 든 넓은 상자인지. [A] 도구 모음·상태 표시줄 영어
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/02-demo-fit.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/02-demo-fit.png

### 02-demo-fit-orig.png
- 원조 2.7.1, 같은 회로 같은 배율
- 확인할 것: [C] 원조에서도 regfile·alu 상자가 같은 모양인지
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/02-demo-fit-orig.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/02-demo-fit-orig.png

### 03a-pc-adder-200.png
- PC·+4 부분 200%
- 확인할 것: [E] 비교용(변화 없음)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/03a-pc-adder-200.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/03a-pc-adder-200.png

### 03a-pc-adder-200-orig.png
- 원조 같은 영역
- 확인할 것: 비교
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/03a-pc-adder-200-orig.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/03a-pc-adder-200-orig.png

### 03b-splitter-arms-200.png
- 스플리터 팔 라벨 200%
- 확인할 것: [E] 팔 라벨 글자가 더 크고(8.5px 굵게) 진한지(#004D4A, 흰 바탕 대비 7:1 이상)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/03b-splitter-arms-200.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/03b-splitter-arms-200.png

### 03b-splitter-arms-200-orig.png
- 원조 같은 영역
- 확인할 것: 비교(원조 "0-5" 표시)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/03b-splitter-arms-200-orig.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/03b-splitter-arms-200-orig.png

### 03c-regfile-box-200.png
- regfile 상자 200%
- 확인할 것: [C] Auto Appearance: 포트 이름이 상자 안에 겹치지 않고, 회로 이름이 위에 있는지. 포트 순서 RR1·RR2·WR·WD·RegWrite·clk
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/03c-regfile-box-200.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/03c-regfile-box-200.png

### 03c-regfile-box-200-orig.png
- 원조 2.7.1 같은 영역
- 확인할 것: [C] 원조에서도 같은 모양·같은 포트 자리인지(파일에 든 표준 사용자 모양)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/03c-regfile-box-200-orig.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/03c-regfile-box-200-orig.png

### 03d-alu-box-200.png
- alu 상자 200%
- 확인할 것: [C] A·B·ALUOp / Result·Zero, ALUOp 터널이 상자 밖에 있는지
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/03d-alu-box-200.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/03d-alu-box-200.png

### 03d-alu-box-200-orig.png
- 원조 2.7.1 같은 영역
- 확인할 것: [C] 같은 모양인지
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/03d-alu-box-200-orig.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/03d-alu-box-200-orig.png

### 03e-dmem-tunnels-200.png
- Data Memory·제어 터널 200%
- 확인할 것: [A] 부품 몸체 제목 "Data Memory"가 영어인지(#133 되돌림)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/03e-dmem-tunnels-200.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/03e-dmem-tunnels-200.png

### 03e-dmem-tunnels-200-orig.png
- 원조 같은 영역
- 확인할 것: 비교
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/03e-dmem-tunnels-200-orig.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/03e-dmem-tunnels-200-orig.png

### 04a-menu-port.png
- 포트 우클릭
- 확인할 것: [A] 요약 줄과 항목이 영어인지(Attach to Pin, Negate Input …)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/04a-menu-port.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/04a-menu-port.png

### 04b-menu-gate.png
- 게이트 우클릭
- 확인할 것: [A] 영어 항목, "Show in Attribute Panel"
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/04b-menu-gate.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/04b-menu-gate.png

### 04c-menu-wire.png
- 선 우클릭
- 확인할 것: [A] 영어 항목(Attach Probe, Split Bits…)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/04c-menu-wire.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/04c-menu-wire.png

### 04d-menu-empty.png
- 빈 곳 우클릭
- 확인할 것: [A] "Fit to Window"
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/04d-menu-empty.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/04d-menu-empty.png

### 04e-menu-subcircuit.png
- 서브회로 우클릭(새 장면)
- 확인할 것: [C] "Auto Appearance" 항목이 Edit Appearance 아래에 있는지
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/04e-menu-subcircuit.png

### 05a-quick-attrs-dock-open.png
- Quick Attributes + 오른쪽 Attributes 패널
- 확인할 것: [A] 속성 이름·값이 영어(Data Bits, Rising Edge …)인지
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/05a-quick-attrs-dock-open.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/05a-quick-attrs-dock-open.png

### 05b-quick-attrs-crop.png
- Quick Attributes 부분
- 확인할 것: [E] 창이 선(가산기 출력선 등)을 가리지 않고 PC 아래에 놓였는지
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/05b-quick-attrs-crop.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/05b-quick-attrs-crop.png

### 05c-dock-collapsed.png
- Attributes 패널 접음
- 확인할 것: [A] 영어
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/05c-dock-collapsed.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/05c-dock-collapsed.png

### 06a-palette-mux32.png
- 부품·명령 찾기 "mux 32"
- 확인할 것: [A] 결과가 영어 이름(Multiplexer · Data Bits 32)인지. 아래 안내 줄만 한국어
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/06a-palette-mux32.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/06a-palette-mux32.png

### 06b-palette-reset.png
- 명령 찾기 "리셋"
- 확인할 것: [A] 한국어 별칭으로 찾아도 결과는 영어 명령 이름(Reset Simulation)인지
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/06b-palette-reset.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/06b-palette-reset.png

### 07a-toolbar.png
- 도구 모음
- 확인할 것: [A] Edit·Poke·Wire·Text·Pin·Tunnel·Probe·Run·1 Cycle·N Cycles·Reset·Load .s
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/07a-toolbar.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/07a-toolbar.png

### 07b-status-bar.png
- 상태 표시줄
- 확인할 것: [A] Simulation On·Cycle 0·Wire Colors·Labels: All
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/07b-status-bar.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/07b-status-bar.png

### 07c-zoom-menu.png
- 배율 메뉴
- 확인할 것: [A] Fit to Window (Ctrl+0), Show Grid
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/07c-zoom-menu.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/07c-zoom-menu.png

### 08a-splitter-before-200.png
- 스플리터(편집 전) 200%
- 확인할 것: 비교
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/08a-splitter-before-200.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/08a-splitter-before-200.png

### 08b-splitter-editor-ranges.png
- 스플리터 편집기: 범위 입력
- 확인할 것: [A] 편집기 이름(Ranges, Preset, Arm, Apply)은 영어, 비트 칸 안내 문장만 한국어
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/08b-splitter-editor-ranges.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/08b-splitter-editor-ranges.png

### 08c-splitter-editor-r-type.png
- 스플리터 편집기: MIPS R-type 프리셋
- 확인할 것: [A] 프리셋 이름 영어
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/08c-splitter-editor-r-type.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/08c-splitter-editor-r-type.png

### 08d-splitter-arm-labels-200.png
- 적용 뒤 팔 라벨 200%
- 확인할 것: [E] 팔 라벨이 또렷한지
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/08d-splitter-arm-labels-200.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/08d-splitter-arm-labels-200.png

### 09a-find-pc.png
- Ctrl+F "PC"
- 확인할 것: [A] 결과 줄이 이름만(영어)인지, 묶음은 "+"와 "(6 places)"
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/09a-find-pc.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/09a-find-pc.png

### 09b-find-pc-expanded.png
- 같은 이름 터널 묶음을 펼침
- 확인할 것: [D] 위치 줄이 좌표 대신 "next to main › IMem #1.Addr"처럼 붙은 포트·부품·경로인지. 누르면 그곳으로 가 선택한다
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/09b-find-pc-expanded.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/09b-find-pc-expanded.png

### 09c-tunnel-names.png
- Tunnels 탭
- 확인할 것: [A] 영어 탭 이름
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/09c-tunnel-names.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/09c-tunnel-names.png

### 10-file-tabs.png
- 파일 탭 3개
- 확인할 것: 비교
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/10-file-tabs.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/10-file-tabs.png

### 10-file-tabs-top.png
- 파일 탭 부분
- 확인할 것: 비교
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/10-file-tabs-top.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/10-file-tabs-top.png

### 11a-load-summary.png
- .s 불러오기 요약
- 확인할 것: [A] 요약 문장은 한국어, 부품 이름(Instruction Memory, Data Memory)은 영어. 버튼 OK
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/11a-load-summary.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/11a-load-summary.png

### 11b-imem.png
- Instruction Memory 부품
- 확인할 것: [A] 몸체 제목 영어
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/11b-imem.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/11b-imem.png

### 11c-dmem.png
- Data Memory 부품
- 확인할 것: [A] 몸체 제목 영어
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/11c-dmem.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/11c-dmem.png

### 11d-stack-mid-recursion.png
- Stack, 45 사이클
- 확인할 것: [B] "used N B (peak)" 한 줄만(현재 깊이 없음)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/11d-stack-mid-recursion.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/11d-stack-mid-recursion.png

### 11e-console.png
- Console, 45 사이클
- 확인할 것: [A] 몸체 제목 "Console"
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/11e-console.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/11e-console.png

### 11f-stack-end.png
- Stack, exit 시점(halt에서 멈춤, 87 사이클)
- 확인할 것: [B] "used 56 B (peak)". 1차의 480 B는 실행기가 exit 뒤에도 클럭을 돌려 CPU가 main 뒤 fact를 $a0 = 720으로 다시 돌린 탓이었다(D-050)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/11f-stack-end.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/11f-stack-end.png

### 11g-console-end.png
- Console, exit 시점
- 확인할 것: [A] "-- exit --" 영어
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/11g-console-end.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/11g-console-end.png

### 11h-ref-mips-after-run.png
- 참조 CPU 전체, 실행 뒤
- 확인할 것: 비교
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/11h-ref-mips-after-run.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/11h-ref-mips-after-run.png

### 12a-hover-component.png
- 마우스 오버: 서브회로
- 확인할 것: [C] 새 모양의 regfile. [A] 경로 이름
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/12a-hover-component.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/12a-hover-component.png

### 12b-hover-port.png
- 마우스 오버: 포트
- 확인할 것: [A] 포트 이름·폭
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/12b-hover-port.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/12b-hover-port.png

### 13-keys-table.png
- ? 단축키 표
- 확인할 것: [A] 키 이름은 영어, 설명 칸만 한국어, 설명 속 이름도 영어(Edit Tool, Poke Tool, Toolbar). 버튼 "OK"(1차는 "확인")
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review2/13-keys-table.png
- 이전: https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-review1/13-keys-table.png
