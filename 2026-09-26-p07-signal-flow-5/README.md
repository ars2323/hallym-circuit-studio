# 2026-09-26 Signal Flow(P-07) 재촬영 4

- 기준: feat/signal-flow `d768f4e`
- ui-reviewer 4차 위반 2건(18m에서 "Comparator (lt)" 칩이 잘림, 그 지시선이 "Comparator (gt)" 칩을 가로지름)을 반영했다.
  - 다시 그리는 상자를 그릴 때마다 지금 배율로 잡는다(끝점 칩은 화면 크기가 일정해 배율을 바꾸면 상자가 달라진다).
  - 지시선은 다른 끝점 칩 위에 그리지 않고, 놓을 때 지시선이 먼저 놓은 칩을 가로지르는 자리를 피한다.
  - 캔버스 원점 왼쪽·위(그려지지 않는 곳)와 선을 덮는 자리도 피한다. 가리지 않고는 놓을 곳이 없으면 라벨을 빼고 링만 둔다(D-063). 18m(25%)의 "Comparator (lt)"는 이 규칙으로 링만 있다.
- 원조 2.7.1에는 Signal Flow가 없어 -orig 비교 이미지가 없다.

### 18-pc-flow.gif
- 애니메이션 GIF(ImageIO로 직접 만듦, 프레임 80ms = 12.5fps, 무한 반복): demo PC 출력
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p07-signal-flow-5/18-pc-flow.gif

### 18a-pc-t0.png
- t=0
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p07-signal-flow-5/18a-pc-t0.png

### 18b-pc-front-1.png
- 앞단 1
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p07-signal-flow-5/18b-pc-front-1.png

### 18c-pc-front-2.png
- 앞단 2
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p07-signal-flow-5/18c-pc-front-2.png

### 18d-pc-front-3.png
- 앞단 3: pc 터널 점프(터널 색 점선 호)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p07-signal-flow-5/18d-pc-front-3.png

### 18e-pc-reached.png
- 모든 끝점에 닿은 직후: 링은 부품 몸체 밖에만 보여 PC 값·비교기 기호·Probe 값을 가리지 않는다(지난 위반 2~4). 띠는 선 양옆에만(선 색 그대로)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p07-signal-flow-5/18e-pc-reached.png

### 18f-pc-continuous.png
- 연속 흐름
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p07-signal-flow-5/18f-pc-continuous.png

### 18g-tunnel-jumps.png
- MemtoReg 핀에서: 같은 이름 터널(MUX 선택 입력)로 건너뛰는 점선 호. Data Memory 몸체 위는 잘라 낸다(지난 확인 필요 2)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p07-signal-flow-5/18g-tunnel-jumps.png

### 18h-subcircuit-boundary.png
- Instruction Memory Instr 출력: regfile·alu 경계와 "N places". 쓰지 않는 스플리터 팔은 작은 링(지난 확인 필요 4)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p07-signal-flow-5/18h-subcircuit-boundary.png

### 18i-active-memtoreg-0.png
- Active Path Only, MemtoReg=0: MUX에서 멈춘다
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p07-signal-flow-5/18i-active-memtoreg-0.png

### 18i-active-memtoreg-1.png
- Active Path Only, MemtoReg=1: WD까지. regfile 안에서 지난 부품이 없으면(레지스터에서 멈춤) "N places" 칩은 없고 경계만 빛난다(지난 확인 필요 3)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p07-signal-flow-5/18i-active-memtoreg-1.png

### 18j-backward-regfile-wd.png
- Backward, regfile WD: 링·호가 MemtoReg 터널 글자를 가리지 않는다(지난 위반 5)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p07-signal-flow-5/18j-backward-regfile-wd.png

### 18k-reduce-motion.png
- Reduce Motion: 화살표와 순서 번호(번호는 부품 모서리에서 대각선으로 떨어져 외곽선을 덮지 않는다). Instruction Memory (Addr) 라벨이 스플리터 팔 라벨과 겹치지 않는다(지난 위반 1)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p07-signal-flow-5/18k-reduce-motion.png

### 18m-pc-25.png
- 25%: 칩이 온전하고(상자가 배율에 맞춰 넓어짐) 다른 라벨 칩과 4px 이상 떨어진다. 놓을 곳이 없으면 칩 없이 링만(D-063)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p07-signal-flow-5/18m-pc-25.png

### 18n-pc-400.png
- 400%: 링이 굵은 선과 레일 밖으로 나와 보인다
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p07-signal-flow-5/18n-pc-400.png

### 18o-full-window.png
- 창 전체: 흐름은 선택을 바꾸지 않아 빠른 속성 창이 새로 뜨지 않고, Messages 탭은 가려지지 않는다(지난 확인 필요 7)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p07-signal-flow-5/18o-full-window.png

### 18l-contrast-dark-background.png
- 앱에는 다크 테마가 없다(D-063): 어두운 바탕 대비 확인 그림
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p07-signal-flow-5/18l-contrast-dark-background.png
