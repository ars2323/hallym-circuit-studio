# Hallym Circuit Studio 학생 안내

한림대학교 Micro-architecture 실습에서 쓰는 회로 편집기입니다. 수업에서 쓰던 **Logisim 2.7.1**을 바탕으로 만들었고, 같은 `.circ` 파일을 열고 저장하므로 한 파일을 두 프로그램에서 번갈아 써도 됩니다. 이름(메뉴·도구·부품)은 Logisim과 같은 영어이고, 설명 문장만 한국어입니다.

## 1. 설치

**Windows(권장):** `hallym-circuit-studio-<버전>-windows.zip`을 원하는 폴더에 풀고 `HallymCircuitStudio.exe`를 실행합니다. Java를 따로 설치할 필요가 없고 관리자 권한도 필요 없습니다. 설치형이 필요하면 `.msi`를 씁니다(시작 메뉴·바탕화면 바로가기).

**다른 운영체제:** Java 21 이상을 설치하고 `java -jar hallym-circuit-studio.jar`로 실행합니다.

처음 실행하면 창을 한 바퀴 돌며 어디에 무엇이 있는지 보여 주는 **Tutorial**이 뜹니다. Help › Tutorial에서 언제든 다시 볼 수 있고, Help › Getting Started에는 부품 놓기·선 잇기·값 바꾸기 카드가 있습니다.

## 2. 창

| 자리 | 하는 일 |
| --- | --- |
| 왼쪽 위 검색 칸 | 부품 이름(`mux 32`, `register`)이나 명령을 치면 아래 목록이 걸러집니다. Ctrl+K |
| 왼쪽 부품 목록 | 회로와 라이브러리. 부품을 고르고 캔버스를 누르면 놓입니다. **Hallym MIPS**는 새 파일에서도 늘 목록에 있고(처음엔 흐리게 "(아직 파일에 없음)"), Instruction Memory, Data Memory, Stack, Console이 들어 있습니다. 첫 부품을 놓는 순간 파일에 추가됩니다 |
| 도구 모음 | Edit(고르기·옮기기), Poke(값 바꾸기), Wire, Text, Pin, Tunnel, Probe, Signal Flow / Run, 1 Cycle, N Cycles, Reset, 클럭 속도, Load .s |
| 가운데 캔버스 | 회로. Ctrl+휠 확대·축소, Ctrl+0 전체 맞춤, Ctrl+1 100%, 스페이스+끌기 이동, 오른쪽 클릭으로 그 자리의 명령 |
| 오른쪽 Attributes | 고른 부품의 속성(Data Bits, Facing, Label…). 값을 두 번 누르면 바로 고칩니다. 부품을 놓으면 뜨는 작은 창(Quick Attributes)으로도 고칩니다 |
| 왼쪽 아래 Tunnels·Minimap | 터널 이름 목록(누르면 그 터널로), 회로 전체 축소판 |
| 아래 Messages | 동작하지 않는 연결만 알립니다. 줄을 누르면 그 자리로 갑니다 |
| 아래 Cycle View | 사이클마다 PC·명령어·고른 선의 값. 열을 누르면 그 사이클의 회로, Previous Cycle로 뒤로 |
| 아래 Console | Console 부품의 출력 |
| 상태 표시줄 | 메시지 수, Simulation On/Off, Cycle, PC, 배율, Wire Colors(선 색 뜻), Colors(값/신호 그룹), Labels, Bus Values |

파일은 **탭**으로 엽니다. 탭을 오른쪽 클릭하면 Detach Tab(제 창으로), View Side by Side(두 창을 나란히)가 있고, 다시 실행하면 열려 있던 탭이 돌아옵니다.

## 3. 회로 그리기

- **선 색은 값입니다.** 밝은 초록 1, 어두운 초록 0, 파랑 떠 있음(X, 값을 내는 곳이 없음), 빨강 오류(E, 서로 다른 값이 부딪힘), 주황 비트 폭 불일치, 검정 여러 비트(버스). 상태 표시줄 Wire Colors를 누르면 범례가 뜹니다.
- 버스는 굵게 보이고, Show Bus Widths를 켜면 비트 수(`/32`)가 붙습니다. 시뮬레이션 중에는 버스 옆에 지금 값(Bus Values: Hex/Dec/Signed)이 보입니다.
- **라벨 칩:** 터널·핀·부품 이름은 겹치지 않는 자리에 칩으로 보입니다. 같은 이름의 터널은 같은 색입니다(오른쪽 클릭 Tunnel Color로 바꿀 수 있고 파일에 저장됩니다).
- 선을 그을 때 포트에 닿으면 붙고, 부품을 옮기면 선이 따라옵니다. 지나가는 선 위에 뜻하지 않게 이어지려 하면 도구가 막고 알립니다.
- 부품을 오른쪽 클릭: Duplicate N…(여러 개 복제, R0 → R1, R2…), Align·Distribute(정렬·같은 간격), Influence(영향 경로), Signal Flow(값이 흐르는 애니메이션), Attach Probe.
- 선을 오른쪽 클릭: Net Information, Select Whole Net, Add to Cycle View, Signal Group(제어·데이터·주소 색), Find E/X Origin(파랑·빨강이 어디서 왔는지), Split Bits(스플리터 편집).
- 서브회로를 오른쪽 클릭: Edit Appearance, Auto Appearance(포트 이름이 들어가는 상자 모양), Port Order…(포트 순서), Mark as Register File.
- 캔버스 빈 자리를 오른쪽 클릭: Add Area Memo…(IF/ID/EX 같은 영역 상자와 메모).
- Edit › Undo History…에서 무엇을 되돌릴지 보고 고릅니다. 단축키는 Help › Keyboard Shortcuts(? 키)에서 보고 Customize…로 바꿉니다.

## 4. MIPS 프로그램 올리기와 돌리기

1. Instruction Memory를 놓고(왼쪽 목록 Hallym MIPS, 또는 Ctrl+K에서 `instruction memory`) 오른쪽 클릭 **Load .s…** 또는 도구 모음 **Load .s**로 어셈블리 파일을 고릅니다. 기계어는 QtSpim(Hallym MIPS Simulator)과 같고, `.text`는 `0x00400000`, `.data`는 `0x10010000`부터입니다. `.data`가 있으면 Data Memory에도 함께 올라갑니다.
2. .s 파일을 고쳐 저장하면 자동으로 다시 불러오고 시뮬레이션을 리셋합니다(상태 표시줄에 알림).
3. **1 Cycle**은 한 사이클(클럭 두 틱), **N Cycles**는 정한 수만큼, **Run**은 계속 돌립니다. **Reset**은 처음으로.
4. **Cycle View**에서 사이클 표를 보고, 열을 누르면 그 사이클의 회로가 보입니다. Run Until…로 조건(PC 값, 사이클 수, halt)까지 돌립니다. Registers·Memory·Instruction 탭에 레지스터 파일(오른쪽 클릭 Mark as Register File로 지정)과 메모리, 지금 명령어의 필드가 보입니다.
5. Console 부품은 `syscall`(print_int 1, print_string 4, read 5/8, exit 10)을 흉내 냅니다. 아래 Console 탭에 출력이 모입니다.

## 5. 회로가 안 돌 때

Messages 탭은 **동작할 수 없는 연결**만 알립니다. 결과가 맞는지는 판단하지 않습니다(그건 여러분 몫입니다).

- 연결되지 않은 입력, 짝이 없는 터널, 폭이 다른 선, 서브회로의 안 이어진 포트, 클럭이 안 이어진 레지스터, 겹치는 메모리 영역 — 그리기 중에 바로.
- 값이 정해지지 않은 채 쓰기가 일어난 레지스터(예: RegWrite가 떠 있음), E(충돌)가 생긴 곳, 발진(값이 멈추지 않음), 정렬 안 된 주소·영역 밖 주소·스택 한계 — 돌릴 때 그 **사이클**과 함께.
- 메시지를 누르면 그 자리로 가고, 동적 메시지는 그 사이클로 갑니다. 선을 오른쪽 클릭 **Find E/X Origin**으로 파랑·빨강이 처음 생긴 곳을 찾습니다.
- 파일을 연 직후 값이 파랑(x)이면 Simulate › Reset Simulation(Ctrl+R)을 누릅니다. 발진으로 시뮬레이션이 꺼지면(위 띠) 회로를 고친 뒤 Turn On을 누릅니다.

## 6. 저장과 제출

- Ctrl+S로 저장합니다. 원조 Logisim 2.7.1에서도 열립니다. MIPS 부품을 쓴 파일은 .circ 옆에 `hcs-mips.jar`가 있어야 원조에서 열립니다. 저장할 때 jar가 없으면 상태 표시줄에 한 번 알리고 **Copy hcs-mips.jar Here** 단추로 복사할 수 있습니다(누르기 전에는 복사하지 않습니다). 몇 분마다 자동 저장되고, 프로그램이 갑자기 끝났으면 다음 실행 때 복구를 제안합니다.
- **File › Create Submission…**은 .circ와 불러온 .s, 라이브러리를 zip 하나로 묶습니다. 묶기 전에 저장했는지, Messages가 0건인지, Probe가 남았는지, 원조 2.7.1에서 열리는지 알려 줍니다(막지는 않습니다).
- **File › Export Image…**는 회로를 PNG(1~4배)·SVG·PDF로 내보냅니다. 보고서용입니다.
- 다른 과제 파일의 서브회로가 필요하면 **File › Import Subcircuits…**로 복사해 옵니다(딸린 서브회로 포함).
