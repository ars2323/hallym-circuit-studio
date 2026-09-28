# 캔버스 부품 렌더러(N-05, D-137)

v2 화면(electron/)은 부품을 **부품 렌더러 등록표 하나**(`electron/src/renderer/canvas/registry.ts`)로 그린다. 표의 한 줄은 부품 종류(엔진이 보내는 라이브러리 이름 `lib`과 부품 이름 `name`) 하나이고, 엔진이 준 속성·경계·포트로 벡터 정의(윤곽·곡선·호·포트 표시·글자, `shapes.ts`)를 만든다. 화면은 이 정의를 Canvas 2D로 그리고(`paint.ts`), 그림 내보내기(SVG·PDF·PNG, N-21)는 같은 정의를 SVG로 쓴다(`svg.ts`). 크기·포트 위치는 엔진 값 그대로이고, 기하 동등성 검사(N-06, `electron/tests/unit/canvas-geometry.test.ts`)가 부품 종류 × 대표 속성 조합마다 포트 표시가 엔진 포트 위치에 있고 몸체가 엔진 경계 안에 있으며 모든 포트에 몸체나 선이 닿는지 본다. 향후 Verilog 매핑표(PLAN.md 7.0)는 이 표의 줄에 붙는다.

## 수업 부품(전용 렌더러)

| 라이브러리 | 부품(엔진 이름) | 그림 | 주변 |
| --- | --- | --- | --- |
| Gates | AND Gate, OR Gate, NAND Gate, NOR Gate, XOR Gate, XNOR Gate | 원조 모양 게이트(크기 30·50·70, 부정 입력 방울, XOR 두 번째 곡선, 입력이 많으면 긴 뒷선·날개), 포트마다 입력선 | 라벨 칩 |
| Gates | NOT Gate, Buffer, Controlled Buffer | 삼각형(+방울), 제어 포트에서 삼각형 변까지 선 | 라벨 칩 |
| Plexers | Multiplexer, Demultiplexer, Decoder, Priority Encoder, BitSelector | 사다리꼴(데이터 쪽이 넓음), 선택·Enable 포트에서 빗변까지 선, 선택 값이 정해지면 지나가는 길을 안에 점선으로 | 포트 이름(마우스 오버·200%) |
| Wiring | Splitter | 선처럼 그린 척추와 팔(값 색, 버스 굵기) | 팔마다 비트 범위 `[31:26]`(척추 반대쪽이 비면 거기, 아니면 팔 끝 위) |
| Wiring | Pin | 입력은 네모, 출력은 둥근 모양. 1비트는 값 색 점과 숫자, 여러 비트는 원조처럼 8비트씩 줄 | 라벨 칩(`labelloc` 쪽), 같은 이름 터널이 붙으면 칩 없음(S-12) |
| Wiring | Probe | 둥근 칸 안에 `radix` 진법의 값 | 라벨 칩 |
| Wiring | Tunnel | 원조 태그 모양을 터널 색(v1 12색, 가까운 다른 이름은 다른 색)으로, 이름은 안에 한 크기(labelfont의 7/8). 엔진의 어림보다 긴 이름은 태그가 점 반대쪽으로 늘어남(다른 부품·선까지, D-137) | — |
| Wiring | Clock, Constant, Pull Resistor, Ground, Power | 네모와 파형, 값 글자(1비트는 값 색), 지그재그와 당기는 값, 접지 막대, 전원 삼각형 | Clock 라벨 칩 |
| Arithmetic | Adder, Subtractor, Multiplier, Divider, Negator, Comparator, Shifter, BitAdder, BitFinder | 원조 40×40 상자와 연산 기호(+, −, ×, ÷, −x, `>` `=` `<`, `<<` `>>` `>>>` `rotl` `rotr`, `#1`, find) | 포트 이름(마우스 오버·200%) |
| Memory | D Flip-Flop, T Flip-Flop, J-K Flip-Flop, S-R Flip-Flop | 상자, 클럭 삼각형(하강 에지·낮은 레벨은 동그라미), 기호 글자(D, Q, Q̄ …), 가운데에 상태 | 라벨 칩, 포트 이름 |
| Memory | Register, Counter, Random | 좁은 상자, 클럭 삼각형, D·Q 글자. 8비트까지는 값을 안에 | 9비트 이상은 **값 칩**(옆, 선을 피해서, v1 S-07), 라벨 칩 |
| Memory | Shift Register | 상자, 클럭 삼각형, 병렬이면 단마다의 값(엔진 bodies) | 라벨 칩 |
| Memory | RAM, ROM | 상자, 제목(`RAM 256 × 8`), A·D 글자, 원조 4줄 표(엔진 bodies, 지금 주소는 남색 칸) | 포트 이름 |
| I/O | Button, LED, 7-Segment Display, Hex Digit Display | 눌린 모양, 색 속성 그대로의 LED(하이라이트), 둥근 막대 7세그먼트(색·꺼짐 색·바탕 속성) | 라벨 칩 |
| Base | Text | 원조 글꼴 속성의 크기·굵기·정렬(SansSerif → Pretendard, Monospaced → D2Coding), 경계 안에 맞춤 | — |
| Hallym MIPS | Instruction Memory, Data Memory, Stack(옛 회로용, D-140), Console | 제목, 엔진이 보낸 몸체 줄(영역, words, 지금 주소의 워드, 합친 Data Memory의 두 영역과 `data N words, stack peak N B`), 빨간 상태 글, 포트 이름은 안쪽 14(S-23), Console 출력 칸 | 라벨 칩 |
| Hallym MIPS | Radix Probe | 주 진법(굵게)과 나머지 두 진법 | 라벨 칩 |
| (회로) | 서브회로 인스턴스 | 엔진이 보낸 회로 모양(원조 기본 상자와 홈, 또는 학생이 그린 모양)을 원조처럼 닻·방향으로 놓음. 90° 돌리면 글자도 돈다 | 기본 모양만 포트 이름(마우스 오버·200%, S-08), 라벨 칩 |

모든 부품: 포트 표시(이어진 포트는 값 색 점, 아무것도 닿지 않은 포트는 빈 고리), 선택·마우스 오버 모양, 마우스 오버 정보 한 줄. 칠하는 차례는 모든 부품의 몸체 채움 먼저, 그다음 윤곽·글자(`layers.ts`, 큰 부품부터): 겹친 부품도 가려지지 않는다. 화면에서 8px보다 작은 글자는 그리지 않는다.

## 원조 모양을 흉내 낸 기본 렌더러로 그리는 부품

수업 목록 밖의 Logisim 2.7.1 부품이다. 경계 크기의 상자, 그 안에 부품 이름, 포트 표시로 그린다(`parts/fallback.ts`). 다른 JAR 라이브러리의 부품도 이 렌더러로 그린다. 아래 목록은 `registry.ts`의 `FALLBACK_KINDS`와 같아야 한다(테스트가 비교한다).

<!-- fallback-kinds: 아래 줄들을 테스트가 읽는다 -->
- `Wiring/Transistor`
- `Wiring/Transmission Gate`
- `I/O/Joystick`
- `I/O/Keyboard`
- `I/O/DotMatrix`
- `I/O/TTY`
<!-- /fallback-kinds -->

수업 목록 밖이지만 전용 렌더러가 있는 부품: `Gates/Odd Parity`, `Gates/Even Parity`(`2k+1`·`2k` 상자), `Gates/Controlled Inverter`, `Wiring/Bit Extender`(`8→16`과 확장 방식).
