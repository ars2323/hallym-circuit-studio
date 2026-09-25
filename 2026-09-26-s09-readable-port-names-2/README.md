# 2026-09-26 읽는 포트 이름(S-09) 재촬영

- 기준: fix/readable-port-names `2358d5b`
- ui-reviewer 1차 위반 1건(자동 생성 회로 ref-mips로 찍음, 체크리스트 10)을 반영해 사람이 그린 demo-datapath로 다시 찍었다.
- 찾기 결과의 위치 줄이 내부 이름("Split #10.combined", "Mux #1.sel") 대신 읽는 이름을 쓴다. 원조 2.7.1에는 찾기 창이 없어 -orig 비교가 없다.
- demo에는 스플리터 끝에 붙은 이름이 없다. 스플리터 예("Splitter #1 (combined end)")는 NameIndexTest가 확인한다.

### 09a-find-pc.png
- Ctrl+F "PC": 이름 묶음
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s09-readable-port-names-2/09a-find-pc.png

### 09b-find-pc-expanded.png
- pc 터널 묶음을 펼침: "next to main › Adder #1 (a)", "Comparator #1 (a)"
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s09-readable-port-names-2/09b-find-pc-expanded.png

### 09c-tunnel-names.png
- 터널 이름 목록(바뀌지 않음)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s09-readable-port-names-2/09c-tunnel-names.png

### 09d-find-memtoreg-expanded.png
- "MemtoReg" 묶음을 펼침: "next to main › Multiplexer #1 (select)"(예전 "Mux #1.sel"), 한 포트짜리 핀은 "MemtoReg"
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-s09-readable-port-names-2/09d-find-memtoreg-expanded.png
