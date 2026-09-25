# 2026-09-26 영향 경로(P-01) 재촬영

- 기준: feat/influence-paths `bce0e55`
- ui-reviewer 위반 2건 반영: 다시 그리는 부품은 원조 라벨 글자를 빼고 그림(라벨 칩이 대신), 터널 점선은 부품 몸체·라벨 칩 위에서 잘라 냄. 25%·400%·지운 뒤 장면을 더했다.

### 17a-forward-regfile.png
- regfile Forward 100%. 다시 그린 Zero LED의 원조 라벨 글자가 사라지고 칩 하나만(지난 위반 1)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p01-influence-2/17a-forward-regfile.png

### 17b-forward-one-step.png
- [ 로 한 단계
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p01-influence-2/17b-forward-one-step.png

### 17c-backward-dmem.png
- Data Memory Backward 80%. ALUOp 점선이 터널 몸체 밖에서 시작해 글자를 지나지 않음(지난 위반 2)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p01-influence-2/17c-backward-dmem.png

### 17d-through-registers-pc.png
- PC Forward + Through Registers. pc 점선이 가산기 몸체를 지나지 않음(지난 위반 2)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p01-influence-2/17d-through-registers-pc.png

### 17e-between-regfile-dmem.png
- Path Between Selected
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p01-influence-2/17e-between-regfile-dmem.png

### 17f-inside-alu.png
- alu 안. 점선이 터널 글자·다른 터널을 지나지 않음(지난 위반 2). 가운데 MUX 쪽 터널들은 회로 자체에서 겹쳐 놓여 있다
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p01-influence-2/17f-inside-alu.png

### 17g-forward-25.png
- regfile Forward 25%: 띠와 "alu: 6 places" 칩이 읽힌다(지난 확인 필요 2)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p01-influence-2/17g-forward-25.png

### 17h-forward-400.png
- regfile Forward 400%: RD1·RD2 → alu A·B(지난 확인 필요 2)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p01-influence-2/17h-forward-400.png

### 17i-cleared.png
- Clear Influence 뒤: 강조 전과 같은 모습(지난 확인 필요 4)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p01-influence-2/17i-cleared.png
