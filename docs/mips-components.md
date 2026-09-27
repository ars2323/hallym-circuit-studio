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

## Data Memory(데이터+스택)

실제 MIPS처럼 데이터 메모리는 하나다(사용자 결정, D-140). 학생의 단일 사이클 데이터패스에서 `lw`/`sw`와 `$sp` 접근이 같은 Data Memory 한 개로 간다. 새로 놓는 Data Memory는 QtSpim(SPIM 9.1.24)의 데이터 세그먼트와 스택 세그먼트를 함께 맡는다. 두 영역은 한 메모리다(같은 희소 저장).

| 포트 | 방향 | 폭 | 위치 |
| --- | --- | --- | --- |
| `Addr` | 입력 | 32 | 왼쪽 (−240, −40) |
| `WriteData` | 입력 | 32 | 왼쪽 (−240, 0) |
| `clk` | 입력 | 1 | 아래 (−200, 60) |
| `MemWrite` | 입력 | 1 | 아래 (−140, 60) |
| `MemRead` | 입력 | 1 | 아래 (−70, 60) |
| `ReadData` | 출력 | 32 | 오른쪽 가운데 (0, 0) |

포트는 합치기 전과 같다(PLAN.md 6.2 이름, 좌표).

### 영역: SPIM 9.1.24 소스에서

| 값 | 소스(vendor/spim-9.1.24/CPU) | 뜻 |
| --- | --- | --- |
| `DATA_BOT` = `0x10000000` | `mem.h:67` `#define DATA_BOT ((mem_addr)0x10000000)` | 데이터 세그먼트 시작 |
| `DATA_LIMIT` = 1MB | `spim.h:125` `#define DATA_LIMIT (K * K)`(`spim.h:90` `#define K 1024`), `mem.cpp:187-193`(`expand_data`가 한계를 넘으면 오류) | 데이터 세그먼트 최대 크기 → 데이터 영역 `0x10000000`~`0x100FFFFF` |
| `DATA_SIZE` = 256KB | `spim.h:119` | 처음 잡는 크기(자라서 한계까지) |
| `$gp` = `0x10008000` | `data.cpp:78-86` `data_begins_at_point`: `gp_midpoint = addr + 32 * K; R[REG_GP] = gp_midpoint;`, 부르는 곳 `spim-utils.cpp:91` `data_begins_at_point(DATA_BOT)` | `DATA_BOT + 32K` |
| 사용자 `.data` = `0x10010000` | `data.cpp:85` `next_data_pc = addr + 64 * K;` | `DATA_BOT + 64K`부터 위로 |
| `STACK_TOP` = `0x80000000` | `mem.h:83-85` `/* Exclusive, but include 4K at top of stack. */ #define STACK_TOP ((mem_addr)0x80000000)` | 스택 세그먼트 끝(제외) |
| `STACK_LIMIT` = 256KB | `spim.h:151` `#define STACK_LIMIT (256 * K)`, `mem.cpp:213-220`(`expand_stack`이 한계를 넘으면 오류), `mem.cpp:150` `stack_bot = STACK_TOP - stack_size` | 스택 세그먼트 최대 크기 → 스택 영역 `0x7FFC0000`~`0x7FFFFFFF`, 아래로 자람 |
| `STACK_SIZE` = 64KB | `spim.h:145` | 처음 잡는 크기(자라서 한계까지) |
| 초기 `$sp` = `0x7FFFEFFC` | `spim-utils.cpp:147` `R[REG_SP] = STACK_TOP - BYTES_PER_WORD - 4096;` | SPIM이 기계를 초기화할 때 |
| 실행 때 `$sp` | `spim-utils.cpp:237-270` `initialize_run_stack`: `STACK_TOP - 1`에서 환경 문자열·인자를 쌓고 정렬 | Hallym MIPS `.hmx`의 `reg $sp`(예: `0x7fffffe4`)는 이 값이다 |

사용자 지시의 값과 소스가 모두 같다(다른 값 없음). `MergedDataMemoryTest.newPartRegionsAreSpimDataAndStackSegments`가 소스 파일에서 이 `#define`과 식을 읽어 부품 값과 대조한다. `.bss`와 힙(`sbrk`)은 과제에서 쓰지 않으므로 따로 두지 않는다(데이터 영역 1MB 안이다).

### 동작

- **두 영역:** 데이터 [`base`, `base`+`size`)는 위로, 스택 [`stacktop`+4−`stacksize`, `stacktop`+4)는 아래로 자란다. 새 부품은 데이터 `0x10000000`~`0x100FFFFF`, 스택 `0x7FFC0000`~`0x7FFFFFFF`이다.
- **두 영역 밖 주소는 출력하지 않는다(floating).** 쓰기도 하지 않는다. 읽기는 조합이다. `MemRead`가 1이고 `Addr`가 두 영역 중 하나 안일 때만 `ReadData`를 구동한다.
- 쓰기는 `clk` 상승 에지에 `MemWrite`가 1이고 `Addr`가 영역 안일 때다. 에지 순간의 `Addr`·`WriteData`를 쓴다(single-cycle과 같은 타이밍).
- **떠 있는 제어 입력은 1로 치지 않는다.** 원조 RAM은 떠 있는 `sel`·`ld`·`str`을 1로 보지만, 이 부품은 쓰거나 읽지 않고 부품 안에 "MemWrite 떠 있음"처럼 표시한다.
- 정의되지 않은 `WriteData`(x)를 쓰면 그 칸은 정의되지 않은 값이 되고, 읽으면 x가 나온다(4단계 "X 기록 감지"의 기반).
- **워드 접근만** 한다. 하위 2비트는 쓰지 않고, 읽거나 쓸 때 0이 아니면 표시한다. **희소 저장:** 쓴 4KB 페이지만 메모리를 차지한다.
- 실행 중 쓴 값은 .circ에 저장하지 않는다. 시뮬레이션을 리셋하면 `contents`로 돌아간다. 스택 영역의 내용은 실행 이미지에 없으므로 0에서 시작한다.
- **동작하지 않는 경우만 부품 안에 빨갛게 알린다(사실만):**

  | 경우 | 문구 |
  | --- | --- |
  | 떠 있는 `MemWrite`·`MemRead` | "MemWrite 떠 있음" |
  | 정렬 안 된 주소로 읽기·쓰기 | "Addr가 워드 정렬 안 됨" |
  | 스택 영역 한계 바로 아래(한계 폭 안, 새 부품은 `0x7FF80000`~`0x7FFBFFFF`) | "Stack 사용량이 한계(256KB)를 넘었습니다" |
  | 어느 메모리 영역에도 없는 주소(두 영역 사이 포함) | "10100000는 어느 메모리 영역에도 없음" |
  | 두 부품의 영역이 겹침(예: 새 Data Memory 옆의 옛 Stack) | "메모리 영역이 7ffc0000에서 겹침" |

  한 부품 안의 두 영역은 한 메모리라, 학생이 겹치게 두어도 문제로 보지 않는다. 같은 판정을 부품 상태(`State.problem`)에 두어 포크의 정적·동적 진단(#28)이 읽는다.
- **몸체:** 두 영역의 범위(`data  10000000-100fffff`, `stack 7ffc0000-7fffffff`), 지금 주소의 워드, 쓰임 줄 `data 3 words, stack peak 56 B`. 데이터 워드 수는 초기 내용(.data, 0 구간 포함)이나 쓰기로 값을 가진 데이터 영역의 워드 수, 스택 최대 깊이는 깊이 기준에서 클럭 에지에 접근한 가장 낮은 스택 주소까지다(최고 수위, 팝해도 줄지 않는다, D-050). 줄 자리는 고정이고, 넷째 줄 자리(왼쪽 `WriteData`·오른쪽 `ReadData` 이름 높이)는 비운다. 부품은 `$sp`를 모르므로 지금 깊이는 보이지 않는다.
- **스택 깊이 기준:** 실행 이미지에 `reg $sp`가 있으면 그 값, 없으면 SPIM의 `0x7FFFEFFC`다(#134). 접근이 모두 그 아래이고 스택 영역이 그 위까지 있을 때 거기서 재고, 아니면 스택 영역 맨 위에서 잰다. 기준은 `contents`에 **주소만 있는 줄**로 저장한다(아래 "스택 깊이 기준").

### 속성 설계: 새로 놓는 값과 저장 기준값

원조 2.7.1은 부품 속성 중 **저장 기준값**(`ComponentFactory.getDefaultAttributeValue`)과 다른 것만 .circ에 적고(`XmlWriter.addAttributeSetContent`), 적히지 않은 속성은 그 값으로 읽는다(`XmlReader.initAttributeSet`). 새로 놓는 부품의 값은 `createAttributeSet()`이다. Data Memory는 둘을 나눴다.

| 속성(.circ) | 새로 놓는 값 | 저장 기준값(= v1 Data Memory) |
| --- | --- | --- |
| `base` | `0x10000000` | `0x10010000` |
| `size` | `0x100000` | `0x100000` |
| `stacktop` | `0x7ffffffc` | `0x7ffffffc` |
| `stacksize` | `0x40000` | `0` (스택 영역 없음) |

- **옛 파일:** v1 lib-mips로 만든 Data Memory는 속성이 적히지 않았거나(`<comp … name="Data Memory"/>`) `base`·`size`·`contents`만 적혀 있다. 새 jar는 적히지 않은 속성을 저장 기준값으로 읽으므로 `stacksize`가 0, 곧 v1과 같은 데이터 영역 하나다. 옛 Stack과 겹치지 않고 전과 똑같이 동작한다(`LegacyStackFileTest`가 D-140 직전의 `ref-mips.circ`를 그대로 둔 `tests/mips/ref-mips-v1-stack.circ`로 factorial·sum을 돌리고 다시 저장한다).
- **새 부품:** `base`와 `stacksize`가 기준값과 달라 .circ에 적힌다. 원조 2.7.1 + 이 jar로 열고 저장해도 같은 두 영역이고 바이트가 같다(`MergedDataMemoryTest`).

  ```xml
  <comp lib="7" loc="(600,300)" name="Data Memory">
    <a name="base" val="0x10000000"/>
    <a name="stacksize" val="0x40000"/>
  </comp>
  ```

- **도구 기본값:** 원조 `AddTool`은 부품의 저장 기준값을 도구 기본값으로 써서, 도구 속성을 한 번 만들면(놓기, 도구 고르기) 새로 놓는 값을 `<lib>` 아래 `<tool name="Data Memory">`로 적는다. 그래서 Data Memory 도구는 `PlacementTool`(도구 기본값 = 새로 놓는 값, 복사본도 같음)이다. 학생이 도구 속성을 바꾸지 않으면 아무것도 적지 않는다. 도구 모음에 둔 Data Memory도 `<tool lib="7" name="Data Memory"/>`로만 적힌다.
- 원조의 Multiplexer(`enable`)·Splitter(`appear`)가 파일 판에 따라 기본값을 달리하는 것과 같은 방법이다. 판 대신 "속성이 적혔는가"로 가른다.
- 새 jar로 만든 파일을 **옛 lib-mips(v1.0.3 이하)** 로 열면 `stacksize`를 모르는 속성으로 건너뛰어 데이터 영역만 있는 부품이 된다(앞으로의 호환은 약속하지 않는다, 새 jar를 쓴다).

### Memory 패널(v2, N-14)

엔진의 `MemoryTable`이 Hallym MIPS Data 탭처럼 한 표를 만든다: 데이터 구간(`0x10010000`부터, 실행 이미지의 라벨), 스택 구간(스택 영역 맨 위에서 아래로, `$sp`가 가리키는 줄에 표시), 줄마다 16바이트(+0 +4 +8 +C), 0이 이어지는 줄들은 한 줄. 합친 부품은 두 구간을, 옛 구조는 두 부품이 한 구간씩 낸다. 규약은 docs/engine-api.md "mips".

## Stack (옛 회로용)

D-140 전의 따로 된 스택 부품이다. 옛 .circ가 전과 똑같이 열리고 동작하도록 lib-mips와 엔진에 남긴다. 저장 이름·속성(`top`, `size`, `contents`, `source`, `label`)·기본값(`top` `0x7FFFFFFC`, `size` 1MB → `0x7FF00000`~`0x7FFFFFFF`)·동작을 바꾸지 않았다.

- **새로 놓는 목록:** v2(엔진 `model.library`)는 이 부품을 보이지 않는다(Kinds 등록표 `offeredForNewPlacement`). 원조 2.7.1(트랙 A)은 JAR 라이브러리를 `LoadedLibrary`로 감싸고 파일의 부품을 도구 목록(`Library.getTool` → `getTools()`)에서만 찾으므로 목록에서 빼면 Stack이 든 옛 파일을 열 수 없다. 그래서 트랙 A 목록에는 v1과 같은 자리(Data Memory 다음)에 **"Stack (old circuits)"** 로 남는다(자리를 옮기면 옛 파일을 다시 저장할 때 `<lib>` 아래 `<tool>` 순서가 바뀐다)(몸체 제목은 그대로 "Stack", 도움말 한 줄).
- 옛 Stack과 스택 영역이 없는 옛 Data Memory의 `ReadData`는 한 선에 이을 수 있고, 주소가 속한 쪽만 값을 낸다(`MemoryComponentsTest`). 새 Data Memory 옆에 옛 Stack을 두면 스택 영역이 겹쳐 "메모리 영역이 7ffc0000에서 겹침"을 알린다.
- 파일에 옛 Stack이 있으면 v2 상태 표시줄에 사실 한 줄을 둔다(진단이 아님, 엔진 `mips.facts`): "이 회로는 따로 된 Stack 부품을 씁니다. 새 Data Memory는 스택 영역을 함께 맡습니다."
- 몸체의 `used N B (peak)`와 깊이 기준(`reg $sp` 또는 `0x7FFFEFFC`)은 전과 같다.

## Console

| 포트 | 방향 | 폭 | 위치 |
| --- | --- | --- | --- |
| `Syscall` | 입력 | 1 | 왼쪽 (−260, −40) |
| `V0` | 입력 | 32 | 왼쪽 (−260, 0) |
| `A0` | 입력 | 32 | 왼쪽 (−260, 40) |
| `clk` | 입력 | 1 | 아래 (−220, 60) |
| `Exit` | 출력 | 1 | 오른쪽 가운데 (0, 0) |

`clk` 상승 에지에 `Syscall`이 1이면 `V0`의 번호대로 처리한다(PLAN.md 6.9).

| `V0` | 동작 |
| --- | --- |
| 1 | print_int: `A0`를 부호 있는 10진수로 |
| 4 | print_string: `A0` 주소부터 0 바이트까지. 같은 회로의 Data Memory(옛 Stack 포함)에서 바이트 단위로 읽는다(리틀 엔디언). 바이트는 UTF-8로 읽어 .s의 한글 문자열도 보인다 |
| 11 | print_char: `A0`의 하위 8비트 |
| 10 | exit: `Exit`를 1로 하고 시뮬레이션 클럭 틱을 멈춘다. 그 뒤 syscall은 처리하지 않는다 |

- 그 밖의 번호, 정의되지 않은 `V0`·`A0`, 메모리에 없는 문자열 주소는 처리하지 않고 부품 안에 빨갛게 표시한다. `Syscall`이 떠 있으면 "Syscall 떠 있음"을 표시한다.
- 부품 안에 최근 출력 6줄(한 줄 34자, 넘치면 접음)이 보인다. 원조 2.7.1에서도 출력을 볼 수 있다.
- `Exit`는 PLAN.md 6.9에 없던 출력이다(D-014). 원조 2.7.1 `-tty` 모드는 `halt` 출력 핀으로만 멈추므로, `Exit`를 `halt` 핀에 이으면 명령줄에서도 프로그램 끝에서 멈춘다.
- 실행 중 출력은 .circ에 저장하지 않는다. 리셋하면 지워진다.

## Radix Probe (다중 진법 프로브)

입력 하나(폭 1~32, 왼쪽 가운데)의 값을 세 줄로 보인다. 맨 위 굵은 줄이 주 진법이고, 나머지는 16진수·10진수·2진수 순서다(PLAN.md 5.1).

```text
0x0000002a
42
0000 0000 0000 0000 0000 0000 0010 1010
```

- 주 진법은 `radix` 속성(`hex`, `dec`, `bin`)으로 정한다. 시뮬레이션 중 Poke Tool로 클릭하면 16 → 10 → 2진수로 돌아간다. 클릭으로 바꾼 것은 저장하지 않는다.
- 10진수는 `signed` 속성(기본: 부호 있음)을 따른다. 1비트는 부호로 보지 않는다.
- 정의되지 않은 비트는 `x`, 오류 비트는 `E`로 자리마다 보인다(16진수는 그 비트를 포함한 자리).
- 부품 폭은 2진수 줄이 들어가게 입력 폭에 따라 정해진다(32비트: 260).

## 프로그램 불러오기 (Load Program)

Instruction Memory나 Data Memory를 우클릭하면 "Load Program..."이 있다(PLAN.md 6.3, D-126). 한 번 불러온 뒤에는 "Reload 파일 이름"도 보인다.

1. Hallym MIPS가 내보낸 실행 이미지(`.hmx`, docs/hmx.md)를 고른다. 파일 고르기 창의 거르개는 "Executable image (*.hmx)"이고, 전환 기간에는 `.s`도 고를 수 있다(기본 거르개가 둘 다 보인다).
2. `.hmx`는 lib-mips 안의 공용 파서(D-125)가 읽는다. hcs-asm이 필요 없다. `.s`는 전환용 클래스 `AssemblyTransition` 하나가 `hcs-asm -exception`(Hallym MIPS 기본 배치)으로 어셈블해 같은 이미지 모델로 바꾼다(#373에서 지운다). `hcs-asm`은 시스템 속성 `hcs.asm`, 환경 변수 `HCS_ASM`, `hcs-mips.jar`와 같은 폴더 순서로 찾는다.
3. 파일에 오류가 있으면 줄 번호와 문장(앞 12개)을 보이고 아무것도 바꾸지 않는다.
4. `.text`는 파일 주소 그대로(시작 코드 포함) 그 구간을 담는 Instruction Memory의 `contents`에, `.data`는 그 구간을 데이터 영역에 담는 Data Memory(옛 Stack 제외)에 넣는다. 우클릭한 부품이 그 종류면 그 부품이 구간을 모두 담아야 한다. 아니면 담는 부품이 하나면 그것, 둘 이상이면 목록에서 고르게 한다. 담는 부품이 없으면 구간과 범위를 말하고 아무것도 넣지 않는다. `.data`가 없는 프로그램은 그 Data Memory를 비운다.
5. `reg $sp`가 있으면 그 값 바로 아래 워드를 스택 영역에 품은 부품(Data Memory, 옛 파일은 Stack)의 `contents`에 깊이 기준으로 기억한다(아래 "스택 깊이 기준"). Data Memory는 .data와 깊이 기준이 한 `contents`에 들어간다. 스택 내용은 파일에 없으므로 넣지 않는다(0이다).
6. `source` 속성에 고른 파일 경로를 저장한다. .circ와 같은 폴더나 그 아래면 상대 경로(`prog.hmx`, `asm/prog.s`), 아니면 절대 경로다. 옛 파일의 `.s` 경로도 그대로 다시 읽는다.
7. 바꾼 속성은 Edit › Undo 한 번으로 되돌린다.
8. 요약 창: 어디서 온 이미지인가(`.s`면 ".s 임시 지원"), 넣은 영역과 양(`14 words (0x00400000–0x00400034), entry 0x00400024`, `12 bytes = 3 words (0x10010000–0x1001000b)`), `$sp` 깊이 기준, 이미지의 명령어 목록(PLAN.md 6.6, 시작 코드 포함), 원본 `.s` 대조 결과(바뀌었으면 노란 줄).

### 스택 깊이 기준

Data Memory 몸체의 `stack peak N B`(옛 Stack은 `used N B (peak)`)는 시작 `$sp`에서 잰다. 실행 이미지에 `reg $sp`가 있으면 그 값, 없으면 SPIM의 `0x7FFFEFFC`다(#134). 새 속성 이름을 더하지 않으려고 이 값은 부품의 `contents`에 **주소만 있는 줄**로 저장한다. 워드가 없는 줄이라 메모리 내용은 그대로이고, 옛 lib-mips도 이 줄을 오류 없이 건너뛴다. Data Memory에서는 .data 워드 줄들과 함께 적힌다.

```text
hcs-words 1
7ffff000
```

명령어 이름은 `Disassembler`가 MIPS32 명세의 opcode·funct 표로 직접 정한다. SPIM의 표는 옮기지 않았다(규칙 2.5). 정수 명령어와 syscall, 코프로세서 이동, TLB·eret을 알고, 부동소수점은 모른다(`?`). 테스트는 원본 spim의 디스어셈블(`tt.core.s` 등 2000워드 이상)과 이름을 대조한다.

원조 2.7.1 GUI(Xvfb)에서 우클릭 → 파일 선택 → 요약 창까지 확인했다. 다만 우클릭 메뉴는 파일의 마우스 매핑(Project › Options › Mouse, 기본: 오른쪽 버튼 = Menu Tool)에 따른다.

## 속성

| 이름(.circ) | 뜻 | Instruction Memory | Data Memory(새로 놓는 값 / 저장 기준값) | Stack(옛 회로용) |
| --- | --- | --- | --- | --- |
| `base` | 시작 주소(높은 쪽으로 자람) | `0x00400000` | `0x10000000` / `0x10010000` | 없음 |
| `size` | 한계(바이트) | `0x100000` | `0x100000` / 같음 | `0x100000` |
| `stacktop` | 스택 영역 맨 위 워드(낮은 쪽으로 자람) | 없음 | `0x7ffffffc` / 같음 | 없음 |
| `stacksize` | 스택 영역 한계(바이트), 0이면 스택 영역 없음 | 없음 | `0x40000` / `0` | 없음 |
| `top` | 맨 위 워드 주소(낮은 쪽으로 자람) | 없음 | 없음 | `0x7ffffffc` |
| `contents` | 초기 내용(실행 이미지의 .text / .data, 스택 깊이 기준) | 비어 있음 | 비어 있음 | 비어 있음 |
| `source` | 불러온 프로그램(.hmx, 전환 기간 .s) 경로. 속성 창 이름은 `Program` | `""` | `""` | `""` |
| `label` | 라벨 | `""` | `""` | `""` |

영역은 데이터가 [`base`, `base`+`size`), Data Memory의 스택이 [`stacktop`+4−`stacksize`, `stacktop`+4)(`stacksize`가 0이 아닐 때), 옛 Stack이 [`top`+4−`size`, `top`+4)다. 저장 기준값과 새로 놓는 값이 다른 까닭은 위 "속성 설계"에 있다. 옛 Stack 영역 `0x7FF00000`~`0x7FFFFFFF`에도 SPIM의 `$sp` 초기값 `0x7FFFEFFC`가 들어 있다.

`contents`는 기본값(비어 있음)이 아니면 .circ에 이렇게 저장된다.

```xml
<comp lib="7" loc="(900,300)" name="Instruction Memory">
  <a name="contents">hcs-words 1
00400000 3c041001 34020004 0000000c
</a>
</comp>
```

첫 줄은 형식 이름, 그다음 줄마다 시작 주소(16진수 8자리)와 이어지는 워드(한 줄에 최대 8개)다.

## 원조 2.7.1에서 확인한 것 (`MemoryComponentsTest`, `MergedDataMemoryTest`, `LegacyStackFileTest`, `ConsoleTest`)

테스트는 원조 API로 회로를 만들어 저장하고, 원조 jar를 `-tty table`로 돌려 명세에서 계산한 값과 비교한다.

- Instruction Memory가 PC를 따라 내용을 읽고, 쓰지 않은 워드는 0, 영역 밖은 구동하지 않는다.
- 새 Data Memory 하나가 네 경계 워드(`0x10000000`, `0x100FFFFC`, `0x7FFC0000`, `0x7FFFFFFC`)에 쓰고 되읽으며, 바로 바깥(`0x0FFFFFFC`, `0x10100000`, `0x7FFBFFFC`, `0x80000000`)에는 쓰지도 내지도 않는다(x).
- 옛 구조: 스택 영역이 없는 Data Memory와 옛 Stack의 `ReadData`를 한 선에 이었을 때, 두 영역에 쓴 뒤 읽으면 주소가 속한 쪽만 값을 낸다. `MemRead`가 0인 동안은 선이 x다.
- 새 Data Memory를 놓고 저장하면 `base`·`stacksize`만 적히고(`<lib>` 아래 `<tool>` 없음), 다시 열고 저장해도 바이트가 같다. D-140 직전의 참조 회로(`tests/mips/ref-mips-v1-stack.circ`)는 새 jar로 열어도 영역이 같고, factorial·sum이 전과 같이 돌며(스택 깊이 56 B는 옛 Stack이 잰다), 다시 저장하면 D-006 기준(부품 순서만 정규화)으로 같다.
- `MemWrite`가 떠 있으면 쓰지 않는다. 떠 있는 `WriteData`를 쓴 칸은 다시 읽으면 x다.
- Console이 ROM으로 만든 syscall 순서(print_string, print_int −42, print_char, Syscall 0, 지원하지 않는 5번, 한글 print_string, exit, exit 뒤 print_int)를 처리해 `Hello\n-42A한글`을 내고, exit 뒤에는 처리하지 않는다. 출력 글자는 원조 엔진을 테스트 JVM 안에서 돌려(`InProcessSim`) 부품 상태로 확인한다.
- 원조 jar `-tty`에서 `Exit`를 `halt`에 이으면 exit를 처리한 사이클에서 멈춘다.

## 알려진 원조 동작

원조 2.7.1 GUI는 파일을 연 직후 첫 전파를 하지 않아 모든 값이 x로 보일 수 있다. Simulate › Reset Simulation(Ctrl+R)이나 클럭 한 번으로 풀린다. 이 부품만의 문제가 아니다.
