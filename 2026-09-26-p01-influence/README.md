# 2026-09-26 영향 경로(P-01)

- 기준: feat/influence-paths `14ea7f3`
- 관련 이슈: P-01 #193
- 원조에는 없는 기능이라 -orig 비교 대신 강조가 없는 모습은 앞 폴더(02, 03a~03e)를 본다.

### 17a-forward-regfile.png
- regfile을 고르고 Show Influence (Forward), 100%. 나머지는 흐리게, 닿은 선은 파랑 띠(값 색 그대로), 시작 부품은 남색 테두리, alu 안에 닿은 곳은 "alu: 6 places", Data Memory(상태 부품)에서 멈춤(호박색 점선 테두리)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p01-influence/17a-forward-regfile.png

### 17b-forward-one-step.png
- 같은 영향 경로를 [ 로 한 단계로 좁힘: regfile 출력에서 바로 닿는 alu 입력과 Data Memory WriteData까지
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p01-influence/17b-forward-one-step.png

### 17c-backward-dmem.png
- Data Memory에서 Show Influence (Backward), 80%. 호박색 띠, Instruction Memory에서 멈춤, regfile·alu 안 칩. 터널이 딱 둘인 넷(ALUOp)만 점선, 클럭 넷은 잇지 않음
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p01-influence/17c-backward-dmem.png

### 17d-through-registers-pc.png
- PC에서 Forward + Through Registers: 레지스터·메모리를 넘어 다음 사이클 경로까지
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p01-influence/17d-through-registers-pc.png

### 17e-between-regfile-dmem.png
- regfile과 Data Memory를 골라 Path Between Selected: 둘 사이 경로만
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p01-influence/17e-between-regfile-dmem.png

### 17f-inside-alu.png
- regfile에서 Forward를 켠 채 alu 안으로 들어가 본 모습: 안쪽에서 닿은 곳이 이어서 강조, 닿은 터널은 얇은 테두리, 터널이 둘인 넷만 점선
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p01-influence/17f-inside-alu.png
