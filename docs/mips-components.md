# MIPS 부품 라이브러리 (트랙 A, `lib-mips`)

`hcs-mips.jar`는 원조 Logisim 2.7.1에서 Project › Load Library › JAR Library로 불러 쓰는 부품 라이브러리다. 라이브러리 이름은 "Hallym MIPS"다. 부품 이름, 포트 순서·좌표, 속성 이름은 .circ에 저장되므로 배포 뒤에는 바꾸지 않는다(D-013).

## Instruction Memory

| 포트 | 방향 | 폭 | 위치(부품 기준) |
| --- | --- | --- | --- |
| `Addr` | 입력 | 32 | 왼쪽 가운데 (−200, 0) |
| `Instr` | 출력 | 32 | 오른쪽 가운데 (0, 0) |

- 클럭 없는 읽기 전용. `Addr`가 영역 안이면 그 워드를, 밖이거나 정의되지 않았으면 아무것도 구동하지 않는다.
- 쓰지 않은 워드는 0이다(`sll $0, $0, 0` = `nop`).
- `Addr`의 하위 2비트는 쓰지 않는다. 0이 아니면 부품 안에 "Addr가 워드 정렬 안 됨"을 표시한다.

## Data Memory, Stack

| 포트 | 방향 | 폭 | 위치 |
| --- | --- | --- | --- |
| `Addr` | 입력 | 32 | 왼쪽 (−240, −40) |
| `WriteData` | 입력 | 32 | 왼쪽 (−240, 0) |
| `clk` | 입력 | 1 | 아래 (−200, 60) |
| `MemWrite` | 입력 | 1 | 아래 (−140, 60) |
| `MemRead` | 입력 | 1 | 아래 (−70, 60) |
| `ReadData` | 출력 | 32 | 오른쪽 가운데 (0, 0) |

- 읽기는 조합이다. `MemRead`가 1이고 `Addr`가 영역 안일 때만 `ReadData`를 구동한다. 그래서 Data Memory와 Stack의 `ReadData`를 한 선에 이을 수 있다.
- 쓰기는 `clk` 상승 에지에 `MemWrite`가 1이고 `Addr`가 영역 안일 때다. 에지 순간의 `Addr`·`WriteData`를 쓴다(single-cycle과 같은 타이밍).
- **떠 있는 제어 입력은 1로 치지 않는다.** 원조 RAM은 떠 있는 `sel`·`ld`·`str`을 1로 보지만, 이 부품은 쓰거나 읽지 않고 부품 안에 "MemWrite 떠 있음"처럼 표시한다.
- 정의되지 않은 `WriteData`(x)를 쓰면 그 칸은 정의되지 않은 값이 되고, 읽으면 x가 나온다(4단계 "X 기록 감지"의 기반).
- 워드 접근만 한다. 하위 2비트는 쓰지 않고, 읽거나 쓸 때 0이 아니면 표시한다.
- 실행 중 쓴 값은 .circ에 저장하지 않는다. 시뮬레이션을 리셋하면 `contents`로 돌아간다.

## 속성

| 이름(.circ) | 뜻 | Instruction Memory | Data Memory | Stack |
| --- | --- | --- | --- | --- |
| `base` | 영역 시작 주소 | `0x00400000` | `0x10010000` | `0x7ff00000` |
| `size` | 영역 크기(바이트) | `0x100000` | `0x100000` | `0x100000` |
| `contents` | 초기 내용(.s의 .text / .data) | 비어 있음 | 비어 있음 | 비어 있음 |
| `source` | 불러온 .s 경로 | `""` | `""` | `""` |
| `label` | 라벨 | `""` | `""` | `""` |

Stack의 영역 `0x7FF00000`~`0x7FFFFFFF`에는 SPIM의 `$sp` 초기값 `0x7FFFEFFC`가 들어 있다.

`contents`는 기본값(비어 있음)이 아니면 .circ에 이렇게 저장된다.

```xml
<comp lib="7" loc="(900,300)" name="Instruction Memory">
  <a name="contents">hcs-words 1
00400000 3c041001 34020004 0000000c
</a>
</comp>
```

첫 줄은 형식 이름, 그다음 줄마다 시작 주소(16진수 8자리)와 이어지는 워드(한 줄에 최대 8개)다.

## 원조 2.7.1에서 확인한 것 (`MemoryComponentsTest`)

테스트는 원조 API로 회로를 만들어 저장하고, 원조 jar를 `-tty table`로 돌려 명세에서 계산한 값과 비교한다.

- Instruction Memory가 PC를 따라 내용을 읽고, 쓰지 않은 워드는 0, 영역 밖은 구동하지 않는다.
- Data Memory와 Stack의 `ReadData`를 한 선에 이었을 때, 두 영역에 쓴 뒤 읽으면 주소가 속한 쪽만 값을 낸다. `MemRead`가 0인 동안은 선이 x다.
- `MemWrite`가 떠 있으면 쓰지 않는다. 떠 있는 `WriteData`를 쓴 칸은 다시 읽으면 x다.

## 알려진 원조 동작

원조 2.7.1 GUI는 파일을 연 직후 첫 전파를 하지 않아 모든 값이 x로 보일 수 있다. Simulate › Reset Simulation(Ctrl+R)이나 클럭 한 번으로 풀린다. 이 부품만의 문제가 아니다.
