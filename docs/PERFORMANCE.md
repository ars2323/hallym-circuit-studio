# 성능 기준

측정은 CI(Linux, GitHub Actions ubuntu-latest)와 개발 PC에서 같은 테스트로 한다. 테스트가 수치를 로그에 남기고(`[perf]` 줄), 기준을 넘으면 실패한다. 중앙값으로 재고 처음 몇 번은 JIT 예열로 버린다.

## Signal Flow(P-07) — `FlowPerformanceTest`

| 항목 | 기준 | 개발 PC(2026-09-26) |
| --- | --- | --- |
| 경로 계산, ref-mips.circ(레지스터·핀·클럭 12곳에서 시작, Through Registers, 가장 느린 곳) | 50 ms 이하 | 5.7 ms(CI 11.5 ms) |
| 경로 계산, demo-datapath.circ(같은 방식, 9곳) | 50 ms 이하 | 1.2 ms(CI 2.5 ms) |
| 프레임 그리기, 연속 흐름, demo-datapath PC 출력(Through Registers, 1600×900 창) | 4 ms 이하 | 1.2 ms |
| 프레임 그리기, 연속 흐름, ref-mips(터널 점프 257개) | 4 ms 이하 | 0.5 ms |
| 프레임 그리기, 앞단이 퍼지는 중(처음 약 2초, 그때그때 그림) | 8 ms 이하 | demo 1.8 ms, ref-mips 0.6 ms |

- CI 기계는 개발 PC보다 약 2.5배 느리다(첫 CI에서 연속 흐름 프레임이 6.1 ms로 기준을 넘었다). 그래서 연속 흐름에서는 멈춘 부분을 그림 한 장으로 만들어 두고 프레임마다 그림 + 대시만 그린다.
- 띠와 대시는 가로·세로 직선이라 사각형으로 채운다. 라벨·칩 글자는 장치 화소 그림으로 한 번 만들어 두고, 호·링을 자르는 영역과 라벨 자리는 경로마다 한 번 계산한다.
- 매 프레임 경로 상자만 다시 그리고, 멈추면 Timer를 세운다(CPU 0에 가깝게). Reduce Motion은 Timer를 돌리지 않는다.

3단계(C)의 기록 엔진 기준(32레지스터 single-cycle MIPS 1000사이클 실행·기록·되감기)은 C 항목에서 이 문서에 더한다.
