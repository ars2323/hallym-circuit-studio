# 2026-09-26 Signal Flow(P-07)

- 기준: feat/signal-flow `7e48c2e`
- 관련 이슈: P-07 #243
- demo-datapath(사람이 그린 회로), 100%(18a~18f, GIF)와 75%(18g~18k). 흐름은 선 색을 바꾸지 않는 덧그림이다.

### 18-pc-flow.gif
- 애니메이션 GIF(ImageIO로 직접 만듦, 12fps): demo-datapath에서 PC 출력을 누른 흐름. 앞단이 PC(D)·Instruction Memory(Addr)·비교기로 퍼지고, 모두 닿은 뒤 연속 흐름
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p07-signal-flow/18-pc-flow.gif

### 18a-pc-t0.png
- t=0: 누른 직후(아직 아무것도 켜지지 않음)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p07-signal-flow/18a-pc-t0.png

### 18b-pc-front-1.png
- 앞단 진행 1: PC 출력에서 Instruction Memory 쪽과 가산기 쪽으로
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p07-signal-flow/18b-pc-front-1.png

### 18c-pc-front-2.png
- 앞단 진행 2
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p07-signal-flow/18c-pc-front-2.png

### 18d-pc-front-3.png
- 앞단 진행 3: pc 터널에서 비교기 pc 터널로 점프(점선 호, 터널 색)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p07-signal-flow/18d-pc-front-3.png

### 18e-pc-reached.png
- 모든 끝점에 닿은 직후: 끝점마다 링과 라벨(PC (D), Instruction Memory (Addr), Comparator (gt)/(lt)), 멀리 놓인 라벨은 지시선
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p07-signal-flow/18e-pc-reached.png

### 18f-pc-continuous.png
- 연속 흐름: 대시가 신호 방향으로 계속 흐른다(선 값 색은 대시 사이로 보인다)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p07-signal-flow/18f-pc-continuous.png

### 18g-tunnel-jumps.png
- alu Result 출력에서: 같은 이름 터널로 건너뛰는 점선 호(부품 몸체·칩 위는 잘라 냄), 75%
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p07-signal-flow/18g-tunnel-jumps.png

### 18h-subcircuit-boundary.png
- Instruction Memory Instr 출력에서: 스플리터 → regfile·alu 경계(테두리가 빛남)와 "regfile: 7 places", "alu: 7 places". 쓰지 않는 스플리터 팔은 링만
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p07-signal-flow/18h-subcircuit-boundary.png

### 18i-active-memtoreg-0.png
- Active Path Only, MemtoReg=0: Data Memory ReadData는 MUX에서 멈춘다(선택되지 않은 가지)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p07-signal-flow/18i-active-memtoreg-0.png

### 18i-active-memtoreg-1.png
- Active Path Only, MemtoReg=1: ReadData가 MUX를 지나 regfile WD로 간다
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p07-signal-flow/18i-active-memtoreg-1.png

### 18j-backward-regfile-wd.png
- Show Signal Flow (Backward), regfile WD: 흐름은 출처(Instruction Memory Instr 등) → WD 방향
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p07-signal-flow/18j-backward-regfile-wd.png

### 18k-reduce-motion.png
- Reduce Motion: 움직임 없이 방향 화살표와 부품 순서 번호
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p07-signal-flow/18k-reduce-motion.png

### 18l-contrast-dark-background.png
- 앱에는 다크 테마가 없다(D-063). 어두운 바탕에 흐름(TEAL 띠, 흰 대시, 링, 라벨)을 그려 대비를 확인한 그림
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p07-signal-flow/18l-contrast-dark-background.png
