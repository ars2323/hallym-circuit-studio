# 엔진 API(v2, JSON-RPC over stdio)

Hallym Circuit Studio 2의 화면(Electron)과 Java 엔진(headless Logisim 2.7.1)이 주고받는 규약이다(D-132, D-133). 이 문서가 두 쪽의 계약이고, 바꾸면 두 쪽 테스트와 이 문서를 함께 고친다.

## 1. 원칙

- **엔진이 권위다.** 회로 모델의 진짜 상태는 엔진에 있다. 화면은 사본을 들고, 엔진이 보내는 변경분(`model.changed`)으로 맞춘다.
- **편집은 의도로 보낸다.** 화면은 "이 자리에 AND를 놓아라", "이 두 점을 선으로 이어라"만 보내고, 실제 변경은 엔진이 **Logisim의 편집 코드**(선 합치기·나누기, 연결점, 되돌리기 기록)로 한다. 그래서 결과 .circ가 원조와 같다(편집 동등성 N-09).
- **엔진을 고치지 않는다.** 엔진 패키지(`com.cburch.logisim.circuit/comp/data/instance/std/file`)는 그대로 쓰고, 서버 코드는 `kr.ac.hallym.hcs.engine.*`에 둔다.
- **디스크에 설정을 남기지 않는다.** 엔진은 메모리 전용 Preferences로 돌고 원조 Logisim의 디스크 설정을 읽지 않는다(실습실 규칙, N-19).

## 2. 전송

- 엔진은 Electron main 프로세스가 띄우는 자식 프로세스 하나다(앱당 하나, 여러 파일을 함께 연다). `java -Djava.awt.headless=true -jar engine.jar`.
- stdin·stdout: **JSON-RPC 2.0, 한 줄에 한 JSON 객체**(UTF-8, 줄 끝 `\n`). 요청은 `{"jsonrpc":"2.0","id":N,"method":"…","params":{…}}`, 응답은 `{"jsonrpc":"2.0","id":N,"result":…}` 또는 `"error":{"code","message","data"}`.
- 엔진이 먼저 보내는 알림은 `id`가 없다(`model.changed`, `sim.values`, `sim.state`, `engine.log`).
- stderr는 사람이 읽는 로그다(화면은 파일에 남기지 않는다).
- 요청은 순서대로 처리한다. 긴 일(N Cycles, Run Until)은 곧바로 응답하고 진행은 알림으로 보낸다.

### 오류 코드

| code | 뜻 |
| --- | --- |
| -32700, -32600, -32601, -32602 | JSON-RPC 표준(구문, 요청, 없는 메서드, 인자) |
| 1 | 없는 파일·회로·부품 id |
| 2 | 파일을 읽거나 쓰지 못함(`data.path`, `data.reason`) |
| 3 | 편집할 수 없음(예: 읽기 전용 예제, `data.reason`) |
| 4 | 시뮬레이션 상태 때문에 할 수 없음(꺼짐, 발진) |

## 3. 식별자

- `fileId`: `"f1"`, `"f2"` … 파일을 열 때 엔진이 준다.
- `circuitId`: `"c1"` … 파일 안의 회로. 이름이 바뀌어도 그대로다.
- 부품 `id`: `"k17"` … 엔진이 Logisim `Component` 객체마다 준다. Logisim은 옮기거나 속성이 바뀐 부품을 새 객체로 바꿀 수 있으므로, 그때는 `model.changed`에 옛 id 삭제와 새 id 추가가 함께 온다.
- 선 `id`: `"w5"` …(Logisim `Wire`). 넷 `id`: `"n3"` …(연결된 선·포트 묶음, 모델이 바뀔 때마다 다시 매긴다).
- 좌표는 Logisim 논리 좌표(정수, 격자 10).

## 4. 값

한 넷의 값은 폭만큼의 글자, 높은 비트부터: `'0'`, `'1'`, `'x'`(떠 있음·정해지지 않음), `'E'`(오류). 예: 1비트 `"1"`, 4비트 `"01x1"`. 화면은 이 글자로 선 색(0·1·x·E·버스)과 값 칩을 정한다.

## 5. 메서드(v0)

### engine

| 메서드 | params | result |
| --- | --- | --- |
| `engine.hello` | `{client, version}` | `{engine, version, logisim:"2.7.1", java}` |
| `engine.shutdown` | `{}` | `{}` 뒤 종료 |

### file

| 메서드 | params | result |
| --- | --- | --- |
| `file.new` | `{}` | `{fileId, circuits:[CircuitRef], main}` |
| `file.open` | `{path, readOnly?}` | `{fileId, name, circuits:[CircuitRef], main, libraries:[LibRef]}` |
| `file.save` | `{fileId, path?}` | `{path, bytes}`(Logisim 저장 코드, 새 부품을 안 쓴 파일은 원조와 바이트 같음) |
| `file.close` | `{fileId}` | `{}` |
| `file.dirty` | `{fileId}` | `{dirty}` |

`CircuitRef = {circuitId, name}`, `LibRef = {lib, kind:"builtin"|"jar"|"circ", path?}`

### model

| 메서드 | params | result |
| --- | --- | --- |
| `model.circuit` | `{fileId, circuitId}` | `Snapshot` |
| `model.library` | `{fileId}` | `[{lib, tools:[{name, display}]}]`(부품 목록 트리) |

```
Snapshot = {
  circuitId, name,
  components: [Component], wires: [{id, a:[x,y], b:[x,y]}],
  nets: [{id, width, wires:[wireId], ports:[[componentId, portIndex]]}],
  junctions: [[x,y]]            // 세 갈래 이상이 만나는 점(연결점)
}
Component = {
  id, lib, name,                // 예: "Gates", "AND Gate"
  loc:[x,y], bounds:[x,y,w,h], facing:"east"|"west"|"north"|"south"|null,
  attrs: {<.circ 속성 이름>: <.circ에 저장되는 글자>},
  ports: [{i, loc:[x,y], width, dir:"in"|"out"|"inout", name?}],
  subcircuit?: circuitId        // 서브회로 인스턴스면
}
```

### edit(의도)

모두 `{fileId, circuitId, …}`를 받고 `{changed:true}`를 돌려준 뒤 `model.changed`를 보낸다. 하나의 의도가 되돌리기 한 단계다.

| 메서드 | 더 받는 것 | 하는 일(Logisim 코드) |
| --- | --- | --- |
| `edit.addComponent` | `lib, name, loc, attrs?` | 부품 놓기(AddTool과 같은 동작) |
| `edit.addWire` | `points:[[x,y],…]`(2~3점, ㄱ자는 3점) | 선 긋기(WiringTool과 같은 합치기·나누기) |
| `edit.move` | `ids, dx, dy` | 옮기기와 따라오는 선(v1의 따라오는 선 규칙) |
| `edit.delete` | `ids` | 지우기 |
| `edit.setAttr` | `ids, attr, value` | 속성 바꾸기(글자는 .circ에 저장되는 글자) |
| `edit.undo`, `edit.redo` | — | Logisim 되돌리기 기록 그대로 |

`model.changed = {fileId, circuitId, removed:[id], added:[Component|Wire], nets?, junctions?, dirty}`

### sim

| 메서드 | params | result |
| --- | --- | --- |
| `sim.reset` | `{fileId}` | `{}` |
| `sim.poke` | `{fileId, circuitId, componentId}` | `{}`(Poke 도구와 같은 동작: 핀 값 바꾸기 등) |
| `sim.cycles` | `{fileId, n}` | `{}` 곧바로. 틱은 엔진이 따라오는 만큼만 요청한다(D-123). 끝나면 `sim.state` |
| `sim.run` | `{fileId, on, hz?}` | `{}` |
| `sim.watch` | `{fileId, circuitId, path?:[componentId]}` | `{}`: 이 회로(서브회로 안이면 인스턴스 경로)의 넷 값을 보낸다 |

`sim.values = {fileId, circuitId, nets:{netId: value}}` — 보고 있는 회로의 바뀐 넷만, 화면 프레임(약 16ms)마다 묶어서.
`sim.state = {fileId, running, ticking, cycle, oscillating}`

## 6. 확장

진단(Messages), E/X 출처, 영향 경로, Signal Flow, 기록(사이클 표, Registers·Memory·Instruction), MIPS(.hmx·.s 불러오기, 디스어셈블, Console)는 각 N 항목에서 이 문서에 절을 더하며 늘린다. 메서드 이름은 `diag.*`, `trace.*`, `record.*`, `mips.*`로 묶는다.
