# 엔진 회귀 테스트

포크가 시뮬레이션 결과를 바꾸지 않았는지 확인한다(PLAN.md 3장 "엔진", 8.3). 같은 회로를 표준 Logisim 2.7.1 jar와 포크에서 `-tty table`로 돌려 출력이 같아야 한다.

## 구성

| 위치 | 내용 |
| --- | --- |
| `tests/circ/<이름>.circ` | 회로. 원조 2.7.1 API로 만들고 원조의 저장 코드로 저장한 파일 |
| `tests/circ/<이름>.expected` | 표준 2.7.1 jar(JDK 8)의 `-tty table` 출력. 첫 줄은 `exit=<종료 코드>` |
| `tests/circ/<이름>.args` | 추가 명령줄 인자(예: `-load memory.ram`) |
| `tests/regress/` | Gradle 하위 프로젝트 `:regress`. 회로 생성기(`Circuits`), 실행기(`Engine`, `Regress`), 테스트 |

```sh
./gradlew :regress:test                              # 표준 jar 결과 = .expected
./gradlew :regress:run --args="generate"             # 회로를 다시 만든다
./gradlew :regress:run --args="update"               # .expected를 다시 쓴다(표준 jar 기준)
./gradlew :regress:run --args="check path/to.jar"    # 다른 jar(포크)를 .expected와 비교
```

## 회로

`-tty table`은 입력 핀을 0으로 둔 채 클럭만 돌리고, 출력 핀 값이 바뀔 때마다 한 줄을 찍으며, `halt` 출력 핀이 1이 되면 멈춘다. 그래서 회로는 Counter나 Clock으로 스스로 입력을 만든다.

| 회로 | 확인하는 것 |
| --- | --- |
| `gates` | 3비트 카운터로 AND/OR/NAND/NOR/XNOR, 3입력 XOR, NOT, MUX 진리표 8행 |
| `register` | 레지스터 + 가산기(PC처럼 3씩 증가), 부호 없는 비교기, 클럭 상승 에지 |
| `memory` | ROM 읽기, 분리 버스 RAM 쓰기와 다시 읽기, `-load` 초기값. 5비트 카운터로 두 바퀴 |
| `values` | 합선(E), 떠 있는 출력(x), `gateUndefined=ignore`인 게이트의 떠 있는 입력, 일부만 구동된 버스(`xx10`), 곧은 선 연결 |
| `subcircuit` | 반가산기 서브회로 두 개로 만든 전가산기 |

생성기는 포트 좌표를 규칙으로 계산하지 않고 부품 객체에서 받는다. 연결은 대부분 포트 위에 같은 라벨의 터널을 놓아 만든다. 선 모양 때문에 생기는 우연한 합선·단선(PLAN.md 부록 A)을 피하기 위해서다.

관찰한 원조 동작: `-load`는 첫 전파 뒤에 이미지를 올리고 첫 행을 다시 전파하지 않고 찍는다. 그래서 `memory`의 첫 행 RAM 값은 `0`이다. 포크도 같아야 한다.

## 한계와 다음 단계

- 이 회로들은 엔진의 기본 동작만 덮는다. 실제 과제 .circ 세트(선이 많은 회로, 학생 제출물 형태)는 사용자에게 받는다(#54). 받으면 `tests/circ/assignments/`에 두고 같은 방식으로 기대값을 만든다.
- 포크 jar와의 비교는 포크가 빌드되면(#18) `check`로 붙인다(#19).
- 커밋된 회로가 생성기 결과와 같은지는 D-006 정규화 뒤 비교한다. 부품 순서가 identity hash 순서라 같은 JVM 안에서도 호출 이력에 따라 달라지기 때문이다.
