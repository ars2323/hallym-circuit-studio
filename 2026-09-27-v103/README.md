# 2026-09-27 v1.0.3 패치 스크린샷(Y-01~Y-05 전후, 기준 창 크기 6가지, 첫 실행 튜토리얼, 최종 세트, windows-smoke)

- 기준: main `c628668`(v1.0.3)
- 관련 이슈: #319~#324, #333, #336, #338, #339 (마일스톤 v1.0.3)
- "전"은 v1.0.2 세트(https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v102/README.md)의 같은 장면이고, "후"는 각 PR 검토 때 찍은 것이다. Y-03(임시 줄 수명)은 그림이 바뀌지 않아 GUI 테스트가 근거다. 아래 최종 세트는 세 범위 검토를 마친 세트(84c98bc 기준, 앱 코드는 v1.0.3과 같고 그 뒤 바뀐 것은 N Cycles 틱 조절과 Messages 초점뿐)다. 검토에서 앞 장면(19의 핀 지우기·되돌리기)이 남긴 상태가 드러난 14·24와, 초점을 고친 14·31은 v1.0.3 main에서 장면마다 새 JVM으로 다시 찍어 바꿨다(D-121, D-124). 바꾼 세 장면은 사용자 지시에 따라 추가 검토 없이 올린다.

## Y-01 세로 공간 배분(캔버스 높이 ≥ 창 내부의 절반) (#319 (PR #325))

전(v1.0.2):
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v102/windows-smoke/screenshot-150.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v102/50-panels-960.png

후:
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/y01-51-laptop-1920x1040.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/y01-51-laptop-1280x800.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/y01-51-laptop-1093x582.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/y01-51-laptop-1024x728.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/y01-51-laptop-910x505.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/y01-51-laptop-683x512.png

## Y-02 사이클 표 폭(3열, Registers 칸 줄이기·숨기기·접기) — 6사이클 기록 뒤 (#320 (PR #326))

전(v1.0.2):
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v102/50-panels-960.png

후:
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/y03-51-laptop-1920x1040.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/y03-51-laptop-1280x800.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/y03-51-laptop-1093x582.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/y03-51-laptop-1024x728.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/y03-51-laptop-910x505.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/y03-51-laptop-683x512.png

## Y-04 진짜 첫 실행(환경설정 없음, 튜토리얼 첫 장) (#322 (PR #328))

전(v1.0.2):
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v102/48-first-run.png

후:
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/y04b-48-first-run.png

## Y-05 빈 Attributes 칸(Circuit: main)과 자동 아이콘만 모드의 » 메뉴 (#323 (PR #329))

전(v1.0.2):
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v102/01-first-screen.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v102/49-toolbar-960.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v102/49-toolbar-640-menu.png

후:
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/y05-01-first-screen.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/y05-49-toolbar-960.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/y05-49-toolbar-960-menu.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/y05-49-toolbar-640-menu.png

## 최종 세트(모든 장면, v1.0.3; 51-laptop-* 6장이 기준 창 크기의 전체 창, 48-first-run이 첫 실행 튜토리얼)

- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/01-first-screen.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/01b-tree-search-mux.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/02-demo-fit-orig.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/02-demo-fit.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/03a-pc-adder-200-orig.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/03a-pc-adder-200.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/03b-splitter-arms-200-orig.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/03b-splitter-arms-200.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/03c-regfile-box-200-orig.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/03c-regfile-box-200.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/03d-alu-box-200-orig.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/03d-alu-box-200.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/03e-dmem-tunnels-200-orig.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/03e-dmem-tunnels-200.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/04a-menu-port.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/04b-menu-gate.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/04c-menu-wire.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/04d-menu-empty.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/04e-menu-subcircuit.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/05a-quick-attrs-dock-open.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/05b-quick-attrs-crop.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/05c-dock-collapsed.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/06a-palette-mux32.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/06b-palette-reset.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/07a-toolbar.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/07b-status-bar.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/07c-zoom-menu.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/08a-splitter-before-200.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/08b-splitter-editor-ranges.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/08c-splitter-editor-r-type.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/08d-splitter-arm-labels-200.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/09a-find-pc.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/09b-find-pc-expanded.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/09c-tunnel-names.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/09d-find-memtoreg-expanded.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/10-file-tabs-top.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/10-file-tabs.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/11a-load-summary.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/11b-imem.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/11c-dmem.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/11d-stack-mid-recursion.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/11e-console.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/11f-stack-end.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/11g-console-end.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/11h-ref-mips-after-run.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/12a-hover-component.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/12b-hover-port.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/13-keys-table.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/14a-messages.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/14b-messages-clicked.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/14c-messages-list.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/14d-messages-pc.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/14e-gate-undefined-error-crop.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/14e-gate-undefined-error.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/14f-marks-100.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/14f-marks-25.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/14f-marks-400-pc.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/14f-marks-400-tunnel.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/14g-focused-fit-zoom-native.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/15a-move-before.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/15b-move-after.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/15c-segment-before.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/15d-segment-after.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/16a-crossing-100-orig.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/16a-crossing-100.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/16a-crossing-25-orig.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/16a-crossing-25.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/16a-crossing-400-orig.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/16a-crossing-400.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/16b-junctions-25-orig.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/16b-junctions-25.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/16c-net-highlight.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/17a-forward-regfile.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/17b-forward-one-step.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/17c-backward-dmem.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/17d-through-registers-pc.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/17e-between-regfile-dmem.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/17f-inside-alu.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/17g-forward-25.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/17h-forward-400.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/17i-cleared.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/18a-pc-t0.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/18b-pc-front-1.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/18c-pc-front-2.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/18d-pc-front-3.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/18e-pc-reached.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/18f-pc-continuous.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/18g-tunnel-jumps.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/18h-subcircuit-boundary.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/18i-active-memtoreg-0.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/18i-active-memtoreg-1.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/18j-backward-regfile-wd.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/18k-reduce-motion.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/18l-contrast-dark-background.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/18m-pc-25.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/18n-pc-400.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/18o-full-window.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/19a-standalone-banner.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/19b-pin-preview.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/19c-pin-tool-preview.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/19d-running-instance.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/19e-cut-connection-notice.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/20a-ripple-uses-adder.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/20b-open-files-search.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/20c-loaded-and-placed.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/20d-port-change-warning.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/20e-updated-badge.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/20f-edit-original-menu.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/21a-pc-100-orig.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/21a-pc-100.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/21a-pc-200-orig.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/21a-pc-200.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/21a-pc-400-orig.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/21a-pc-400.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/21b-adder-100-orig.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/21b-adder-100.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/21b-adder-200-orig.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/21b-adder-200.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/21b-adder-400-orig.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/21b-adder-400.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/21c-adder-hover-100.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/21d-pc-hover-25.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/22a-quick-bar-auto-appearance.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/22b-hover-port-list.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/22c-after-auto-appearance.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/23a-control-pins-100-orig.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/23a-control-pins-100.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/23a-control-pins-200-orig.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/23a-control-pins-200.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/23a-control-pins-400-orig.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/23a-control-pins-400.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/24a-full-window-tunnels.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/24b-left-panel-tunnels.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/24c-left-panel-minimap.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/25a-cycles-full.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/25b-cycles-table.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/25c-past-cycle-full.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/25d-past-cycle-table.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/25e-canvas-latest-200.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/25f-canvas-cycle2-200.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/26a-run-until-dialog.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/26b-run-until-stopped.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/26c-status-notice.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/26d-full-window.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/27a-registers-full.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/27b-registers-panel.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/27d-stack.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/27e-registers-unmarked.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/27f-stack-demo-full.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/28a-console-full.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/28b-console-tab.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/28c-reload-notice.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/29a-instruction-fields-full.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/29b-instruction-tab.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/29c-field-colors-canvas.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/29d-field-colors-25.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/29e-field-colors-400.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/29f-no-field-colors.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/30a-bus-values-full.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/30b-bus-values-canvas.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/30c-bus-values-signed.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/30d-off.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/30e-bus-values-25.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/30f-bus-values-400.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/31a-dynamic-message.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/31b-message-row.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/31c-message-clicked.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/31d-menu-find-origin.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/31e-origin-notice.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/31f-message-rows-pinned.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/32a-oscillation.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/32b-oscillation-message.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/32c-mips-unaligned.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/32d-mips-body.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/33a-about-license.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/33b-about-notices.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/34a-undo-history.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/34b-shortcut-table.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/34c-shortcut-settings.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/35a-menu-duplicate-n.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/35b-duplicate-n-dialog.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/35c-copies-and-loose-gates.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/35d-menu-arrange.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/35e-aligned-left.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/36a-submission-checks.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/36b-export-image.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/36c-export-png-2x.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/37a-bus-widths-full-orig.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/37a-bus-widths-full.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/37b-bus-widths-150-orig.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/37b-bus-widths-150.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/37c-wire-legend.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/38a-signal-groups-full.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/38b-signal-groups-150.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/38c-signal-group-menu.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/38d-signal-groups-25.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/38e-signal-groups-400.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/39a-area-memos-full.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/39b-area-memo-150.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/39c-menu-area-memo.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/39d-area-memo-dialog.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/40a-tour-welcome.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/40b-tour-toolbar.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/40c-tour-messages.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/41a-tab-menu.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/41b-detached-window.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/41c-side-by-side.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/42a-port-order-dialog.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/42b-regfile-reordered.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/43a-import-choose.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/43b-import-plan.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/44a-new-file-tree-pending.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/44b-new-file-search.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/44c-tree-after-first-part.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/44d-first-part-placed.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/44e-save-jar-notice.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/45a-same-name-tabs.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/45b-window-menu.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/46a-empty-canvas-hint.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/46b-help-examples-menu.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/46c-example-readonly-notice.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/47a-refmips-status-pc.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/47b-tunnels-lone.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/47c-register-menu-mark-pc.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/48-first-run.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/49-toolbar-640-menu.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/49-toolbar-640.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/49-toolbar-960-menu.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/49-toolbar-960.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/50-panels-1280.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/50-panels-960.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/51-laptop-1024x728.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/51-laptop-1093x582.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/51-laptop-1280x800.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/51-laptop-1920x1040.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/51-laptop-683x512.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/51-laptop-910x505.png

## Windows 첫 실행·튜토리얼 화면(CI windows-smoke 아티팩트, v1.0.3 main)

- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/windows-smoke/screenshot-100.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/windows-smoke/screenshot-150.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/windows-smoke/screenshot-1920-100.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/windows-smoke/screenshot-1920-125.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/windows-smoke/screenshot-tutorial-100.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/windows-smoke/screenshot-tutorial-150.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/windows-smoke/screenshot-tutorial-1920-125.png
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-27-v103/windows-smoke/tty.txt
