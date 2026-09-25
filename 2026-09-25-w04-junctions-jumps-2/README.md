# 2026-09-25 연결점과 점프(W-04) 재촬영

- 기준: feat/junctions-jumps `a9c2a6d`
- ui-reviewer 확인 필요(25%에서 Zero 반원이 옆 연결점에 닿아 이어진 것처럼 보임)를 고쳤다: 반원 반지름은 회로 8 이하(격자 반 칸 미만)이고 화면 3.5px보다 작아지는 배율(약 44% 아래)에서는 그리지 않는다. 연결점 지름은 회로 16 이하(반지름이 격자 한 칸 미만)라 25%에서 4px이다.

### 02-demo-fit-orig.png
- 원조 2.7.1 같은 배율
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-w04-junctions-jumps-2/02-demo-fit-orig.png

### 02-demo-fit.png
- 데모 화면 맞춤(77%): 연결점 7px 이상, Zero 교차는 반원
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-w04-junctions-jumps-2/02-demo-fit.png

### 16a-crossing-100-orig.png
- 원조 100%
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-w04-junctions-jumps-2/16a-crossing-100-orig.png

### 16a-crossing-100.png
- 100%: 반원 반지름 5(격자 반 칸 미만)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-w04-junctions-jumps-2/16a-crossing-100.png

### 16a-crossing-25-orig.png
- 원조 25%
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-w04-junctions-jumps-2/16a-crossing-25-orig.png

### 16a-crossing-25.png
- 교차(1140, 260) 25%: 반원은 3.5px보다 작아 그리지 않는다(원조처럼 평범한 십자). 연결점은 4px
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-w04-junctions-jumps-2/16a-crossing-25.png

### 16a-crossing-400-orig.png
- 원조 400%
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-w04-junctions-jumps-2/16a-crossing-400-orig.png

### 16a-crossing-400.png
- 400%
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-w04-junctions-jumps-2/16a-crossing-400.png

### 16b-junctions-25-orig.png
- 원조 25%
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-w04-junctions-jumps-2/16b-junctions-25-orig.png

### 16b-junctions-25.png
- 연결점 영역 25%: 점 4px(원조 2px), 옆 줄에 닿지 않는다
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-w04-junctions-jumps-2/16b-junctions-25.png

### 16d-crossing-25-x6-orig.png
- 원조 25% 같은 곳 6배
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-w04-junctions-jumps-2/16d-crossing-25-x6-orig.png

### 16d-crossing-25-x6.png
- 위 25% 교차부를 6배 확대(최근접 보간, 검토용). Result 연결점과 Zero 선 사이에 틈이 있다
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-25-w04-junctions-jumps-2/16d-crossing-25-x6.png
