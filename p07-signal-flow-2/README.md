# 2026-09-26 Signal Flow(P-07) 재촬영

- 기준: feat/signal-flow `a14fa45`
- ui-reviewer 1차 위반 5건(라벨·링·호가 글자를 가림)과 확인 필요(띠가 선 색을 바꿈, 18g에 점프 없음, 25%·400%·창 전체 없음)를 반영했다.

### 18-pc-flow.gif
- 애니메이션 GIF(ImageIO로 직접 만듦, 프레임 80ms = 12.5fps, 무한 반복): demo PC 출력
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p07-signal-flow-2/18-pc-flow.gif

### 18a-pc-t0.png
- t=0
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p07-signal-flow-2/18a-pc-t0.png

### 18b-pc-front-1.png
- 앞단 1
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p07-signal-flow-2/18b-pc-front-1.png

### 18c-pc-front-2.png
- 앞단 2
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p07-signal-flow-2/18c-pc-front-2.png

### 18d-pc-front-3.png
- 앞단 3: pc 터널 점프(터널 색 점선 호)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p07-signal-flow-2/18d-pc-front-3.png

### 18e-pc-reached.png
- 모든 끝점에 닿은 직후: 링은 부품 몸체 밖에만 보여 PC 값·비교기 기호·Probe 값을 가리지 않는다(지난 위반 2~4). 띠는 선 양옆에만(선 색 그대로)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p07-signal-flow-2/18e-pc-reached.png

### 18f-pc-continuous.png
- 연속 흐름
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p07-signal-flow-2/18f-pc-continuous.png

### 18g-tunnel-jumps.png
- MemtoReg 핀에서: 같은 이름 터널(MUX 선택 입력)로 건너뛰는 점선 호. Data Memory 몸체 위는 잘라 낸다(지난 확인 필요 2)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p07-signal-flow-2/18g-tunnel-jumps.png

### 18h-subcircuit-boundary.png
- Instruction Memory Instr 출력: regfile·alu 경계와 "N places". 쓰지 않는 스플리터 팔은 작은 링(지난 확인 필요 4)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p07-signal-flow-2/18h-subcircuit-boundary.png

### 18i-active-memtoreg-0.png
- Active Path Only, MemtoReg=0: MUX에서 멈춘다
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p07-signal-flow-2/18i-active-memtoreg-0.png

### 18i-active-memtoreg-1.png
- Active Path Only, MemtoReg=1: WD까지. regfile 안에서 지난 부품이 없으면(레지스터에서 멈춤) "N places" 칩은 없고 경계만 빛난다(지난 확인 필요 3)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p07-signal-flow-2/18i-active-memtoreg-1.png

### 18j-backward-regfile-wd.png
- Backward, regfile WD: 링·호가 MemtoReg 터널 글자를 가리지 않는다(지난 위반 5)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p07-signal-flow-2/18j-backward-regfile-wd.png

### 18k-reduce-motion.png
- Reduce Motion: 화살표와 순서 번호. Instruction Memory (Addr) 라벨이 스플리터 팔 라벨과 겹치지 않는다(지난 위반 1)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p07-signal-flow-2/18k-reduce-motion.png

### 18m-pc-25.png
- 25%
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p07-signal-flow-2/18m-pc-25.png

### 18n-pc-400.png
- 400%
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p07-signal-flow-2/18n-pc-400.png

### 18o-full-window.png
- 창 전체: 흐름은 선택을 바꾸지 않아 빠른 속성 창이 새로 뜨지 않고, Messages 탭은 가려지지 않는다(지난 확인 필요 7)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p07-signal-flow-2/18o-full-window.png

### 18l-contrast-dark-background.png
- 앱에는 다크 테마가 없다(D-063): 어두운 바탕 대비 확인 그림
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/p07-signal-flow-2/18l-contrast-dark-background.png
