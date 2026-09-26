# 2026-09-26 최종 스크린샷 세트 3차(Q-03, v1.0.0)

- 기준: fix/release-polish 2a278cb(main 5caaa69 위); 바꾸지 않은 장면은 feat/windows-package `1c73cbe`(v1.0.0 릴리스 준비 브랜치, main과 내용 같음)
- 관련 이슈: Q-03 #236 (PR #281)
- 2차 세트 검토(위반 1: 12a 도움말이 regfile 아래 버스를 덮음) 반영: 도움말이 선도 피하도록 고치고(D-095) 장면 12를 다시 찍었다(촬영 크롭에 도움말 상자를 넣음). 나머지는 2차와 같다.
- 1차 세트(2026-09-26-release) 검토 반영: 장면 12(도움말이 값 칩을 덮음 → 칩을 피하는 자리, D-095), 03(굵은 버스 위 T자 연결점이 묻힘 → 점을 버스보다 넓게), 11·28(전체 촬영 때 hcs-asm이 없어 .s 불러오기가 실패 → 빌드 뒤 다시 촬영)을 다시 찍어 바꿨다. 나머지 이미지는 1차와 같다.
- 모든 장면을 한 번에 찍었다(`tools/screenshots/run.sh` 장면 없이). 장면 14는 아래 패널 탭을 고치고 따로 다시 찍어 바꿨다. `-orig`는 원조 2.7.1이 같은 장면을 찍은 것이다.
- 이미지 212장. 장면 설명은 `docs/SCREENSHOTS.md`.

## 장면 01: 앱 첫 화면(빈 캔버스, 도구 모음·탭·상태 표시줄 전체). 시작 안내가 뜨면 그것도

- [01-first-screen.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/01-first-screen.png)
- [01b-tree-search-mux.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/01b-tree-search-mux.png)
## 장면 02: 데모 회로 전체("화면 맞춤"), 같은 배율의 원조 2.7.1

- [02-demo-fit-orig.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/02-demo-fit-orig.png)
- [02-demo-fit.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/02-demo-fit.png)
## 장면 03: 데모 회로 200% 부분: 라벨 칩, 터널 색, 스플리터 팔 라벨, 서브회로 포트 이름·회로 이름 캡션, MIPS 부품(원조 비교)

- [03a-pc-adder-200-orig.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/03a-pc-adder-200-orig.png)
- [03a-pc-adder-200.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/03a-pc-adder-200.png)
- [03b-splitter-arms-200-orig.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/03b-splitter-arms-200-orig.png)
- [03b-splitter-arms-200.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/03b-splitter-arms-200.png)
- [03c-regfile-box-200-orig.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/03c-regfile-box-200-orig.png)
- [03c-regfile-box-200.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/03c-regfile-box-200.png)
- [03d-alu-box-200-orig.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/03d-alu-box-200-orig.png)
- [03d-alu-box-200.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/03d-alu-box-200.png)
- [03e-dmem-tunnels-200-orig.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/03e-dmem-tunnels-200-orig.png)
- [03e-dmem-tunnels-200.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/03e-dmem-tunnels-200.png)
## 장면 04: 우클릭 메뉴 4장: 포트, 게이트, 선, 빈 곳

- [04a-menu-port.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/04a-menu-port.png)
- [04b-menu-gate.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/04b-menu-gate.png)
- [04c-menu-wire.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/04c-menu-wire.png)
- [04d-menu-empty.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/04d-menu-empty.png)
- [04e-menu-subcircuit.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/04e-menu-subcircuit.png)
## 장면 05: 빠른 속성 창 + 오른쪽 속성 패널(펼침·접힘)

- [05a-quick-attrs-dock-open.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/05a-quick-attrs-dock-open.png)
- [05b-quick-attrs-crop.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/05b-quick-attrs-crop.png)
- [05c-dock-collapsed.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/05c-dock-collapsed.png)
## 장면 06: 검색 팔레트 "mux 32", 명령 검색 "리셋"

- [06a-palette-mux32.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/06a-palette-mux32.png)
- [06b-palette-reset.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/06b-palette-reset.png)
## 장면 07: 도구 모음과 상태 표시줄 부분(아이콘 글자 포함)

- [07a-toolbar.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/07a-toolbar.png)
- [07b-status-bar.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/07b-status-bar.png)
- [07c-zoom-menu.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/07c-zoom-menu.png)
## 장면 08: 스플리터 편집기: R형 프리셋, 범위 "31:26, 25:21, 20:16, 15:0", 적용 뒤 캔버스의 팔 라벨

- [08a-splitter-before-200.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/08a-splitter-before-200.png)
- [08b-splitter-editor-ranges.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/08b-splitter-editor-ranges.png)
- [08c-splitter-editor-r-type.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/08c-splitter-editor-r-type.png)
- [08d-splitter-arm-labels-200.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/08d-splitter-arm-labels-200.png)
## 장면 09: Ctrl+F 찾기 결과, 터널 이름 목록

- [09a-find-pc.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/09a-find-pc.png)
- [09b-find-pc-expanded.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/09b-find-pc-expanded.png)
- [09c-tunnel-names.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/09c-tunnel-names.png)
- [09d-find-memtoreg-expanded.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/09d-find-memtoreg-expanded.png)
## 장면 10: 파일 탭 3개 이상

- [10-file-tabs-top.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/10-file-tabs-top.png)
- [10-file-tabs.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/10-file-tabs.png)
## 장면 11: .s 불러오기 요약, Instruction Memory·Data Memory·Stack·Console 부품(재귀 factorial 실행 중과 끝)

- [11a-load-summary.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/11a-load-summary.png)
- [11b-imem.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/11b-imem.png)
- [11c-dmem.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/11c-dmem.png)
- [11d-stack-mid-recursion.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/11d-stack-mid-recursion.png)
- [11e-console.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/11e-console.png)
- [11f-stack-end.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/11f-stack-end.png)
- [11g-console-end.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/11g-console-end.png)
- [11h-ref-mips-after-run.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/11h-ref-mips-after-run.png)
## 장면 12: 마우스 오버 정보(부품, 포트)

- [12a-hover-component.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/12a-hover-component.png)
- [12b-hover-port.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/12b-hover-port.png)
## 장면 13: ? 단축키 표

- [13-keys-table.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/13-keys-table.png)
## 장면 14: Messages 탭: 데모 회로를 두 곳 망가뜨린 뒤(터널 이름 틀림, PC 클럭 지움) 목록, 메시지를 눌러 강조한 캔버스, 표시가 배율 25·100·400%에서 보이는지(14f), 끝나면 되돌림. gateUndefined = error 회로의 빈 게이트 입력(14e)

- [14a-messages.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/14a-messages.png)
- [14b-messages-clicked.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/14b-messages-clicked.png)
- [14c-messages-list.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/14c-messages-list.png)
- [14d-messages-pc.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/14d-messages-pc.png)
- [14e-gate-undefined-error-crop.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/14e-gate-undefined-error-crop.png)
- [14e-gate-undefined-error.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/14e-gate-undefined-error.png)
- [14f-marks-100.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/14f-marks-100.png)
- [14f-marks-25.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/14f-marks-25.png)
- [14f-marks-400-pc.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/14f-marks-400-pc.png)
- [14f-marks-400-tunnel.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/14f-marks-400-tunnel.png)
- [14g-focused-fit-zoom-native.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/14g-focused-fit-zoom-native.png)
## 장면 15: 따라오는 배선: 데모의 PC를 끌어 옮기기 전후, rs 선의 가운데 세로 선분을 끌기 전후(끝나면 되돌림)

- [15a-move-before.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/15a-move-before.png)
- [15b-move-after.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/15b-move-after.png)
- [15c-segment-before.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/15c-segment-before.png)
- [15d-segment-after.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/15d-segment-after.png)
## 장면 16: 연결점과 점프(W-04): 이어지지 않은 교차(1140, 260)를 25·100·400%, 연결점이 있는 영역 25%, PC 출력 넷 강조. 원조 비교(-orig) 포함

- [16a-crossing-100-orig.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/16a-crossing-100-orig.png)
- [16a-crossing-100.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/16a-crossing-100.png)
- [16a-crossing-25-orig.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/16a-crossing-25-orig.png)
- [16a-crossing-25.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/16a-crossing-25.png)
- [16a-crossing-400-orig.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/16a-crossing-400-orig.png)
- [16a-crossing-400.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/16a-crossing-400.png)
- [16b-junctions-25-orig.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/16b-junctions-25-orig.png)
- [16b-junctions-25.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/16b-junctions-25.png)
- [16c-net-highlight.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/16c-net-highlight.png)
## 장면 17: 영향 경로(P-01): regfile에서 앞으로(alu 안 칩, Data Memory에서 멈춤), 한 단계로 좁힘, Data Memory에서 뒤로, PC에서 Through Registers, regfile–Data Memory 사이 경로, alu 안으로 들어가 본 모습

- [17a-forward-regfile.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/17a-forward-regfile.png)
- [17b-forward-one-step.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/17b-forward-one-step.png)
- [17c-backward-dmem.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/17c-backward-dmem.png)
- [17d-through-registers-pc.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/17d-through-registers-pc.png)
- [17e-between-regfile-dmem.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/17e-between-regfile-dmem.png)
- [17f-inside-alu.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/17f-inside-alu.png)
- [17g-forward-25.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/17g-forward-25.png)
- [17h-forward-400.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/17h-forward-400.png)
- [17i-cleared.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/17i-cleared.png)
## 장면 18: Signal Flow(P-07): demo PC 출력의 프레임 6장(t=0, 앞단 셋, 닿은 직후, 연속)과 GIF(ImageIO), 터널 점프, 서브회로 경계, Active Path Only(MemtoReg 0·1), Backward(regfile WD), Reduce Motion, 어두운 바탕 대비 그림

- [18-pc-flow.gif](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/18-pc-flow.gif)
- [18a-pc-t0.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/18a-pc-t0.png)
- [18b-pc-front-1.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/18b-pc-front-1.png)
- [18c-pc-front-2.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/18c-pc-front-2.png)
- [18d-pc-front-3.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/18d-pc-front-3.png)
- [18e-pc-reached.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/18e-pc-reached.png)
- [18f-pc-continuous.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/18f-pc-continuous.png)
- [18g-tunnel-jumps.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/18g-tunnel-jumps.png)
- [18h-subcircuit-boundary.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/18h-subcircuit-boundary.png)
- [18i-active-memtoreg-0.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/18i-active-memtoreg-0.png)
- [18i-active-memtoreg-1.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/18i-active-memtoreg-1.png)
- [18j-backward-regfile-wd.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/18j-backward-regfile-wd.png)
- [18k-reduce-motion.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/18k-reduce-motion.png)
- [18l-contrast-dark-background.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/18l-contrast-dark-background.png)
- [18m-pc-25.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/18m-pc-25.png)
- [18n-pc-400.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/18n-pc-400.png)
- [18o-full-window.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/18o-full-window.png)
## 장면 19: 서브회로 인스턴스 안내(P-02): 탐색기에서 따로 연 regfile의 띠, 이어진 핀 미리 보기, 핀 도구 미리 보기, 실행 중 인스턴스로 간 뒤, 이어진 핀을 지운 뒤 알림

- [19a-standalone-banner.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/19a-standalone-banner.png)
- [19b-pin-preview.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/19b-pin-preview.png)
- [19c-pin-tool-preview.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/19c-pin-tool-preview.png)
- [19d-running-instance.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/19d-running-instance.png)
- [19e-cut-connection-notice.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/19e-cut-connection-notice.png)
## 장면 20: 탭 간 라이브러리(P-03): 1bit_adder를 쓰는 ripple_carry, 새 파일에서 검색 "adder"의 Open Files 항목, 불러와 놓은 뒤, 출력 핀을 지우고 저장할 때 경고, 속만 고쳐 저장한 뒤 ripple_carry 탭의 Updated, 인스턴스 우클릭의 Edit Original File

- [20a-ripple-uses-adder.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/20a-ripple-uses-adder.png)
- [20b-open-files-search.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/20b-open-files-search.png)
- [20c-loaded-and-placed.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/20c-loaded-and-placed.png)
- [20d-port-change-warning.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/20d-port-change-warning.png)
- [20e-updated-badge.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/20e-updated-badge.png)
- [20f-edit-original-menu.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/20f-edit-original-menu.png)
## 장면 21: 원조 부품의 포트 이름(S-06, S-07): PC 레지스터와 PC+4 가산기를 100·200·400%로(원조 비교 -orig 포함), 100%에서 가산기에 마우스를 올린 모습

- [21a-pc-100-orig.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/21a-pc-100-orig.png)
- [21a-pc-100.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/21a-pc-100.png)
- [21a-pc-200-orig.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/21a-pc-200-orig.png)
- [21a-pc-200.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/21a-pc-200.png)
- [21a-pc-400-orig.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/21a-pc-400-orig.png)
- [21a-pc-400.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/21a-pc-400.png)
- [21b-adder-100-orig.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/21b-adder-100-orig.png)
- [21b-adder-100.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/21b-adder-100.png)
- [21b-adder-200-orig.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/21b-adder-200-orig.png)
- [21b-adder-200.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/21b-adder-200.png)
- [21b-adder-400-orig.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/21b-adder-400-orig.png)
- [21b-adder-400.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/21b-adder-400.png)
- [21c-adder-hover-100.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/21c-adder-hover-100.png)
- [21d-pc-hover-25.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/21d-pc-hover-25.png)
## 장면 22: 기본 모양 서브회로(S-08): 고르면 빠른 속성 창의 Auto Appearance 단추, 마우스 오버의 포트 이름 목록, 단추를 누른 뒤 모양

- [22a-quick-bar-auto-appearance.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/22a-quick-bar-auto-appearance.png)
- [22b-hover-port-list.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/22b-hover-port-list.png)
- [22c-after-auto-appearance.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/22c-after-auto-appearance.png)
## 장면 23: 제어 핀과 같은 이름의 터널(S-12): demo 왼쪽 제어 핀 다섯 개를 200%로(원조 비교 -orig 포함)

- [23a-control-pins-100-orig.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/23a-control-pins-100-orig.png)
- [23a-control-pins-100.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/23a-control-pins-100.png)
- [23a-control-pins-200-orig.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/23a-control-pins-200-orig.png)
- [23a-control-pins-200.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/23a-control-pins-200.png)
- [23a-control-pins-400-orig.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/23a-control-pins-400-orig.png)
- [23a-control-pins-400.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/23a-control-pins-400.png)
## 장면 24: 왼쪽 칸 아래 탭(S-11): 창 전체(Tunnels), 왼쪽 칸 Tunnels, 150%에서 Minimap(보이는 영역 네모)

- [24a-full-window-tunnels.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/24a-full-window-tunnels.png)
- [24b-left-panel-tunnels.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/24b-left-panel-tunnels.png)
- [24c-left-panel-minimap.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/24c-left-panel-minimap.png)
## 장면 25: Cycle View 탭(C-02, C-03): demo-datapath 6사이클, 신호 줄 다섯(clk, pc, halt, alu Result 선, regfile RD1 선), 사이클 2 보기, PC 둘레 확대(마지막·사이클 2)

- [25a-cycles-full.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/25a-cycles-full.png)
- [25b-cycles-table.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/25b-cycles-table.png)
- [25c-past-cycle-full.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/25c-past-cycle-full.png)
- [25d-past-cycle-table.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/25d-past-cycle-table.png)
- [25e-canvas-latest-200.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/25e-canvas-latest-200.png)
- [25f-canvas-cycle2-200.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/25f-canvas-cycle2-200.png)
## 장면 26: Run Until(C-04): demo-datapath 리셋 뒤 Run Until… 창(PC Is 0x10), 멈춘 뒤 표와 상태 표시줄 알림

- [26a-run-until-dialog.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/26a-run-until-dialog.png)
- [26b-run-until-stopped.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/26b-run-until-stopped.png)
- [26c-status-notice.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/26c-status-notice.png)
- [26d-full-window.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/26d-full-window.png)
## 장면 27: 레지스터·메모리 패널(C-05, C-06): demo regfile 표시 뒤 Registers 탭·Register Mapping 창, stack-demo 6사이클 뒤 Memory 탭(Stack)·Registers 탭(표시 없음)

- [27a-registers-full.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/27a-registers-full.png)
- [27b-registers-panel.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/27b-registers-panel.png)
- [27c-register-mapping.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/27c-register-mapping.png)
- [27d-stack.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/27d-stack.png)
- [27e-registers-unmarked.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/27e-registers-unmarked.png)
- [27f-stack-demo-full.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/27f-stack-demo-full.png)
## 장면 28: Console 탭·.s 자동 재로드(C-09): console-demo exit까지 뒤 Console 탭, demo-datapath에 불러온 .s를 고친 뒤 상태 표시줄 알림

- [28a-console-full.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/28a-console-full.png)
- [28b-console-tab.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/28b-console-tab.png)
- [28c-reload-notice.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/28c-reload-notice.png)
## 장면 29: 명령어 필드 색(C-07): demo-datapath에서 R 형식 명령어 사이클의 Instruction 탭과 캔버스 필드 색 띠(rs → RR1, rt → RR2, rd → WR)

- [29a-instruction-fields-full.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/29a-instruction-fields-full.png)
- [29b-instruction-tab.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/29b-instruction-tab.png)
- [29c-field-colors-canvas.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/29c-field-colors-canvas.png)
- [29d-field-colors-25.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/29d-field-colors-25.png)
- [29e-field-colors-400.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/29e-field-colors-400.png)
- [29f-no-field-colors.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/29f-no-field-colors.png)
## 장면 30: 버스 값 칩과 활성 경로(C-08): demo-datapath 두 사이클 뒤 버스 값 칩(Hex, Signed), MemtoReg MUX가 고른 입력 진한 띠, 끈 모습

- [30a-bus-values-full.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/30a-bus-values-full.png)
- [30b-bus-values-canvas.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/30b-bus-values-canvas.png)
- [30c-bus-values-signed.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/30c-bus-values-signed.png)
- [30d-off.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/30d-off.png)
- [30e-bus-values-25.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/30e-bus-values-25.png)
- [30f-bus-values-400.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/30f-bus-values-400.png)
## 장면 31: 동적 진단(D-01·D-03·D-05): demo-datapath의 RegWrite 핀을 3상태로 두고 돌린 뒤 Messages 한 줄, 누른 뒤 사이클 뷰와 원인 선택, 선 우클릭 Find E/X Origin과 알림

- [31a-dynamic-message.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/31a-dynamic-message.png)
- [31b-message-row.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/31b-message-row.png)
- [31c-message-clicked.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/31c-message-clicked.png)
- [31d-menu-find-origin.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/31d-menu-find-origin.png)
- [31e-origin-notice.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/31e-origin-notice.png)
## 장면 32: 진동과 MIPS 부품 값(D-02·D-04): 고장 회로 모음의 NAND 되먹임 진동 메시지와 Reset 단추, 정렬 안 된 주소를 읽는 Data Memory 메시지와 몸체 빨간 글자

- [32a-oscillation.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/32a-oscillation.png)
- [32b-oscillation-message.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/32b-oscillation-message.png)
- [32c-mips-unaligned.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/32c-mips-unaligned.png)
- [32d-mips-body.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/32d-mips-body.png)
## 장면 33: About 창(E-11): 엠블럼·이름·버전·설명·캐릭터, License·Notices 탭

- [33a-about-license.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/33a-about-license.png)
- [33b-about-notices.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/33b-about-notices.png)
## 장면 34: Undo History(E-05)와 단축키(E-09): 기록 창(되돌릴 것·Now·다시 실행할 것), ? 표, Customize…로 연 설정 창

- [34a-undo-history.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/34a-undo-history.png)
- [34b-shortcut-table.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/34b-shortcut-table.png)
- [34c-shortcut-settings.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/34c-shortcut-settings.png)
## 장면 35: 배치 편집(E-01·E-02): 우클릭 Duplicate N…, 창, R0 → R1~R3, 여러 개 우클릭(Align·Distribute), Align › Left 결과

- [35a-menu-duplicate-n.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/35a-menu-duplicate-n.png)
- [35b-duplicate-n-dialog.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/35b-duplicate-n-dialog.png)
- [35c-copies-and-loose-gates.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/35c-copies-and-loose-gates.png)
- [35d-menu-arrange.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/35d-menu-arrange.png)
- [35e-aligned-left.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/35e-aligned-left.png)
## 장면 36: 제출 파일(E-06)과 그림 내보내기(E-07): 점검 창(저장·Messages·Probe·원조에서 열림)과 묶을 파일, Export Image 창, 2배 PNG 결과

- [36a-submission-checks.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/36a-submission-checks.png)
- [36b-export-image.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/36b-export-image.png)
- [36c-export-png-2x.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/36c-export-png-2x.png)
## 장면 37: 버스 폭과 선 색 범례(E-03): 굵은 버스와 비트 수 표시(전체, 150%), Wire Colors 범례

- [37a-bus-widths-full-orig.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/37a-bus-widths-full-orig.png)
- [37a-bus-widths-full.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/37a-bus-widths-full.png)
- [37b-bus-widths-150-orig.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/37b-bus-widths-150-orig.png)
- [37b-bus-widths-150.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/37b-bus-widths-150.png)
- [37c-wire-legend.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/37c-wire-legend.png)
## 장면 38: 신호 그룹 색(E-04): RegWrite=Control, ALU 결과=Data, PC→명령어 메모리=Address로 정한 뒤 Colors: Groups(전체, 150%), 선 우클릭 Signal Group 하위 메뉴

- [38a-signal-groups-full.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/38a-signal-groups-full.png)
- [38b-signal-groups-150.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/38b-signal-groups-150.png)
- [38c-signal-group-menu.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/38c-signal-group-menu.png)
- [38d-signal-groups-25.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/38d-signal-groups-25.png)
- [38e-signal-groups-400.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/38e-signal-groups-400.png)
## 장면 39: 영역 메모(E-08): IF·EX 영역 상자(전체, 150%), 메모 안 우클릭 메뉴, Add Area Memo… 창

- [39a-area-memos-full.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/39a-area-memos-full.png)
- [39b-area-memo-150.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/39b-area-memo-150.png)
- [39c-menu-area-memo.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/39c-menu-area-memo.png)
- [39d-area-memo-dialog.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/39d-area-memo-dialog.png)
## 장면 40: 첫 실행 튜토리얼(E-10): 첫 장(캐릭터), 도구 모음 단계, Messages 탭 단계

- [40a-tour-welcome.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/40a-tour-welcome.png)
- [40b-tour-toolbar.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/40b-tour-toolbar.png)
- [40c-tour-messages.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/40c-tour-messages.png)
## 장면 41: 창 분리·나란히 보기(P-06): 탭 우클릭 메뉴, 분리한 창(· Window 배지, console-demo), 나란히 보기(왼쪽·오른쪽 반)

- [41a-tab-menu.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/41a-tab-menu.png)
- [41b-detached-window.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/41b-detached-window.png)
- [41c-side-by-side.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/41c-side-by-side.png)
## 장면 42: 포트 순서(P-04): 서브회로 우클릭 Port Order… 창(변마다 목록), Cin을 맨 위로 옮긴 뒤의 regfile 모양

- [42a-port-order-dialog.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/42a-port-order-dialog.png)
- [42b-regfile-reordered.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/42b-regfile-reordered.png)
## 장면 43: 서브회로 가져오기(P-05): 회로 고르기 창, 계획 창(딸린 회로·새 이름)

- [43a-import-choose.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/43a-import-choose.png)
- [43b-import-plan.png](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-release/43b-import-plan.png)
