# Hallym MIPS 부품 라이브러리 사용 안내

지금 쓰는 **Logisim 2.7.1**에 MIPS 메모리 부품과 Console을 더하는 라이브러리입니다. Logisim을 새로 설치할 필요가 없습니다.

## 1. 준비

1. `hcs-mips-<버전>-windows.zip`을 원하는 폴더에 풉니다.
2. 풀린 폴더의 **`hcs-mips.jar`와 `hcs-asm.exe`를 같은 폴더에 둡니다.** Hallym MIPS가 내보낸 실행 이미지(`.hmx`)는 `hcs-mips.jar` 파일 하나로 불러옵니다. `hcs-asm.exe`는 전환 기간에 .s 파일을 바로 불러올 때만 씁니다(.s 파일을 기계어로 바꾸는 프로그램). 둘이 떨어져 있으면 .s 불러오기만 안 됩니다.
3. 회로 파일(.circ)도 **`hcs-mips.jar`와 같은 폴더**에 두는 것을 권합니다. Logisim은 jar가 .circ와 같은 폴더(또는 바로 아래·위 폴더)에 있을 때만 상대 경로로 기억합니다. 다른 곳에 두면 다른 PC에서 파일을 열 때 jar를 다시 찾으라는 창이 뜹니다.

## 2. 라이브러리 불러오기

Logisim 2.7.1에서 **Project › Load Library › JAR Library…**를 누르고 `hcs-mips.jar`를 고릅니다. 왼쪽 부품 목록에 **Hallym MIPS**가 생깁니다. 한 번 불러오면 .circ에 저장되어 다음부터는 자동으로 불러옵니다.

Hallym Circuit Studio(포크)에서는 이 단계가 없습니다. Hallym MIPS가 새 파일에서도 늘 목록에 있고, 첫 부품을 놓으면 `jar#hcs-mips.jar#…`(같은 폴더의 jar)로 파일에 기록됩니다. 그 파일을 원조 2.7.1에서 열려면 `hcs-mips.jar`를 .circ 옆에 두면 됩니다.

## 3. 부품

| 부품 | 입력 | 출력 | 하는 일 |
| --- | --- | --- | --- |
| Instruction Memory | `Addr`(32) | `Instr`(32) | `0x00400000`부터 프로그램(.text). 읽기만 합니다 |
| Data Memory | `Addr`, `WriteData`(32), `MemWrite`, `MemRead`, clk | `ReadData`(32) | `0x10010000`부터 위로(.data). 읽기는 바로, 쓰기는 clk 상승 에지 |
| Stack | Data Memory와 같음 | 같음 | `0x7FFFFFFC`부터 아래로 |
| Console | `Syscall`, `V0`(32), `A0`(32), clk | `Exit` | syscall 1(정수), 4(문자열), 11(문자), 10(끝) |
| Radix Probe | 값 하나 | 없음 | 16·10·2진수를 함께 보여 줌. Poke Tool로 누르면 주 진법이 바뀜 |

- 주소는 **32비트 바이트 주소 그대로** 연결합니다. 스플리터로 자를 필요가 없습니다. 워드 단위로만 읽고 씁니다.
- Data Memory와 Stack의 `ReadData`는 **한 선에 이어도 됩니다.** 주소가 속한 쪽만 값을 냅니다.
- 부품 안에 빨간 글자가 보이면 회로가 동작할 수 없는 상태라는 뜻입니다. 예: "MemWrite 떠 있음", "Addr가 워드 정렬 안 됨", "…는 어느 메모리 영역에도 없음", "Stack 사용량이 한계(1MB)를 넘었습니다". 도구는 **동작하지 않는 경우만** 알립니다. 결과가 맞는지는 판단하지 않습니다.
- 명령줄로 돌릴 때(`-tty`)는 Console의 `Exit`를 `halt`라는 출력 핀에 이으면 프로그램 끝에서 멈춥니다.

## 4. 프로그램 불러오기

1. Instruction Memory나 Data Memory를 **오른쪽 클릭 › "Load Program..."**
2. Hallym MIPS에서 내보낸 **실행 이미지(`.hmx`)** 파일을 고릅니다. 전환 기간에는 .s 파일도 고를 수 있습니다(요약에 ".s 임시 지원"이 보입니다).
3. .text는 파일에 적힌 주소 그대로 Instruction Memory에, .data는 그 주소를 담는 Data Memory에 들어갑니다. 같은 부품이 여럿이면 어느 쪽인지 묻습니다.
4. 파일에 오류가 있거나 담을 부품이 없으면 이유를 줄 번호와 함께 보여 주고 아무것도 바꾸지 않습니다.
5. 넣은 영역과 양(예: `14 words (0x00400000–0x00400034), entry 0x00400024`)과 **이미지의 명령어 목록**이 뜹니다. 데이터패스가 무엇을 지원해야 하는지 볼 수 있습니다. 원본 .s 파일이 내보낸 뒤 바뀌었으면 노란 줄로 알립니다. 이때는 Hallym MIPS에서 다시 내보냅니다.
6. 한 번 불러오면 메뉴에 "Reload"가 생깁니다. 불러온 내용은 .circ에 저장되어 파일이 없어도 동작합니다. Edit › Undo로 되돌릴 수 있습니다.

기계어와 주소는 **Hallym MIPS Simulator와 비트 단위로 같습니다.** Hallym MIPS(2.2.0부터)는 늘 기본 예외 처리기를 불러옵니다. 시작 코드 9워드가 `0x00400000`~`0x00400020`에 있고 `main`은 `0x00400024`입니다. 이 도구도 시작 코드까지 파일 주소 그대로 올리므로 Hallym MIPS 설정을 바꿀 필요가 없습니다.

PC 시작 주소를 entry 값(`0x00400024`, `main`)으로 맞추는 것과 `$sp` 초기값은 회로와 프로그램에서 직접 맞춥니다. PC가 `0x00400000`에서 시작하면 시작 코드부터 실행합니다.

## 5. 알아 둘 것

- Logisim 2.7.1은 파일을 연 직후 값이 모두 x로 보일 수 있습니다. **Simulate › Reset Simulation(Ctrl+R)**을 한 번 누르면 풀립니다.
- 실행 중 메모리에 쓴 값과 Console 출력은 저장되지 않습니다. 리셋하면 불러온 초기 내용으로 돌아갑니다.
- 이 라이브러리를 쓴 .circ는 `hcs-mips.jar`가 없는 Logisim에서는 열리지 않습니다.

## 라이선스

`hcs-mips.jar`는 GNU GPL 버전 2 이상, `hcs-asm`은 BSD 3-Clause(SPIM 9.1.24 코어 포함)입니다. `licenses/` 폴더를 보세요.
