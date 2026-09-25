# 2026-09-26 영향 경로(P-01) 재촬영 2

- 기준: feat/influence-paths `0caa707`
- ui-reviewer 확인 필요 1(서브회로 안 경계 핀이 흐리게 남아 경로가 끊겨 보임)을 고쳤다: 닿은 넷의 경계 핀도 선명하게 다시 그리고, 다시 그리는 순서를 원조 그리기 순서(회로의 부품 순서)에 맞췄다(겹친 부품이 원조처럼 겹친다).

### 17f-inside-alu.png
- alu 안: A·B 입력 핀과 Result·Zero 출력 핀이 선명하다
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p01-influence-3/17f-inside-alu.png

### 17a-forward-regfile.png
- 바깥 회로는 그대로(비교용)
- https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-p01-influence-3/17a-forward-regfile.png
