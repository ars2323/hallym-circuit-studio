# 2026-09-26 읽는 포트 이름(S-09)

- 기준: fix/readable-port-names `7a59b81`
- 찾기 결과의 위치 줄이 내부 이름("Split #10.combined") 대신 읽는 이름을 쓴다. 원조 2.7.1에는 찾기 창이 없어 -orig 비교가 없다.

### 09a-find-pc.png
- Ctrl+F "PC": 이름 묶음
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/s09-readable-port-names/09a-find-pc.png

### 09b-find-pc-expanded.png
- pc 터널 묶음을 펼침: "next to main › Splitter #10 (combined end)", "XOR Gate #1 (output)", "Instruction Memory #1 (Addr)", "Adder #1 (a)"
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/s09-readable-port-names/09b-find-pc-expanded.png

### 09c-tunnel-names.png
- 터널 이름 목록(바뀌지 않음)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/s09-readable-port-names/09c-tunnel-names.png
