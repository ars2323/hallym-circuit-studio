# 엔진 API(v2, JSON-RPC over stdio)

Hallym Circuit Studio 2의 화면(Electron)과 Java 엔진(headless Logisim 2.7.1)이 주고받는 규약이다(D-132, D-133). 이 문서가 두 쪽의 계약이고, 바꾸면 두 쪽 테스트와 이 문서를 함께 고친다. 엔진 쪽 구현은 `engine/`(N-03, D-134)이다.

## 1. 원칙

- **엔진이 권위다.** 회로 모델의 진짜 상태는 엔진에 있다. 화면은 사본을 들고, 엔진이 보내는 변경분(`model.changed`)으로 맞춘다.
- **편집은 의도로 보낸다.** 화면은 "이 자리에 AND를 놓아라", "이 두 점을 선으로 이어라"만 보내고, 실제 변경은 엔진이 **Logisim의 편집 코드**(선 합치기·나누기, 연결점, 되돌리기 기록)로 한다. 그래서 결과 .circ가 원조와 같다(편집 동등성 N-09).
- **엔진을 고치지 않는다.** 엔진 패키지(`com.cburch.logisim.circuit/comp/data/instance/std/file`)는 그대로 쓰고, 서버 코드는 `kr.ac.hallym.hcs.engine.*`에 둔다.
- **디스크에 설정을 남기지 않는다.** 엔진은 메모리 전용 Preferences로 돌고 원조 Logisim의 디스크 설정을 읽지 않는다(실습실 규칙, N-19).

## 2. 전송

- 엔진은 Electron main 프로세스가 띄우는 자식 프로세스 하나다(앱당 하나, 여러 파일을 함께 연다). `java -Djava.awt.headless=true -jar hcs-engine.jar`(엔진도 시작할 때 headless로 맞춘다).
- 번들 MIPS 라이브러리 `hcs-mips.jar`는 시스템 속성 `-Dhcs.bundledMips=<경로>`, 없으면 `hcs-engine.jar` 옆, 그다음 옆의 `lib/`에서 찾는다. .circ의 `jar#…#kr.ac.hallym.hcs.mips.MipsLibrary`가 가리키는 jar가 없으면 이것으로 잇는다(v1과 같은 D-007).
- stdin·stdout: **JSON-RPC 2.0, 한 줄에 한 JSON 객체**(UTF-8, 줄 끝 `\n`). 요청은 `{"jsonrpc":"2.0","id":N,"method":"…","params":{…}}`, 응답은 `{"jsonrpc":"2.0","id":N,"result":…}` 또는 `"error":{"code","message","data"}`.
  - `id`는 숫자·글자 모두 된다(받은 그대로 돌려준다). `params`는 이름 있는 객체만 받는다(배열이면 -32602). 여러 요청을 배열로 묶은 요청(batch)은 받지 않는다(-32600).
  - 화면이 `id` 없이 보낸 것(알림)은 처리만 하고 응답하지 않는다.
- 엔진이 먼저 보내는 알림은 `id`가 없다(`model.changed`, `sim.values`, `sim.state`, `engine.log`).
- stdout에는 규약 줄만 나온다. Logisim 코드가 `System.out`에 쓰는 것도 stderr로 돌린다. stderr는 사람이 읽는 로그다(화면은 파일에 남기지 않는다).
- 요청은 순서대로 처리한다. 긴 일(N Cycles, Run Until)은 곧바로 응답하고 진행은 알림으로 보낸다. 편집의 `model.changed`는 그 편집의 응답 **뒤에**, 그 뒤의 `sim.values`보다 **앞에** 온다.
- 끝: `engine.shutdown`에 응답한 뒤, 또는 stdin이 닫히면 열린 파일을 닫고 종료 코드 0으로 끝난다(저장하지 않는다). 메모리 전용 환경설정을 켜지 못하면 코드 3으로 바로 끝난다.
- 잰 값(2026-09-27, Linux, JDK 21, `engine/build/engine-measure.txt`): 시작 → `engine.hello` 응답 약 80ms, ref-mips를 연 뒤 상주 메모리 약 115MB(`-XX:+UseSerialGC -XX:TieredStopAtLevel=1`이면 약 87MB). JVM 옵션은 N-04가 정한다.

### 오류 코드

| code | 뜻 |
| --- | --- |
| -32700, -32600, -32601, -32602 | JSON-RPC 표준(구문, 요청, 없는 메서드, 인자). -32603은 엔진 내부 오류 |
| 1 | 없는 파일·회로·부품 id(`data.kind`, `data.id`) |
| 2 | 파일을 읽거나 쓰지 못함(`data.path`, `data.reason`) |
| 3 | 편집할 수 없음(예: 읽기 전용 예제, `data.reason`) |
| 4 | 시뮬레이션 상태 때문에 할 수 없음(꺼짐, 발진, `data.reason`) |

`data.reason` 값:

| code | reason |
| --- | --- |
| 2 | `notFound`, `unreadable`, `libraryMissing`(`data.missing`: .circ에 적힌 경로들), `loadFailed`, `writeFailed`, `templateFailed` |
| 3 | `readOnly`, `cannotModify`(.circ 라이브러리의 회로), `circular`(회로를 자기 안에), `exclusive`(한 점에 출력 둘), `negativeCoord`, `readOnlyAttribute` |
| 4 | `off`, `oscillating`, `frozenPin`(서브회로 안의 입력 핀), `needsDialog` |

원조가 대화상자로 묻던 곳(없는 라이브러리 찾기, 서브회로 안 핀 상태 복제)은 창을 열지 않고 오류로 알린다. 화면이 물은 뒤 다시 보내는 방법은 필요해지는 N 항목에서 더한다.

## 3. 식별자

- `fileId`: `"f1"`, `"f2"` … 파일을 열 때 엔진이 준다.
- `circuitId`: `"c1"` … 파일 안의 회로. 이름이 바뀌어도 그대로다.
- 부품 `id`: `"k17"` … 엔진이 Logisim `Component` 객체마다 준다. Logisim은 옮기거나 속성이 바뀐 부품을 새 객체로 바꿀 수 있으므로, 그때는 `model.changed`에 옛 id 삭제와 새 id 추가가 함께 온다.
- 선 `id`: `"w5"` …(Logisim `Wire`). 넷 `id`: `"n3"` …(연결된 선·포트 묶음, 모델이 바뀔 때마다 다시 매긴다).
- 번호는 엔진 전체에서 하나씩 늘고 다시 쓰지 않는다(파일이 달라도 겹치지 않는다). 모델에서 사라진 부품·선의 id는 잊는다: 되돌리기로 같은 부품이 돌아오면 **새 id**로 `added`에 온다.
- 라이브러리 이름 `lib`: Logisim 라이브러리 이름 그대로다. 기본 라이브러리는 `"Wiring"`, `"Gates"`, `"Plexers"`, `"Arithmetic"`, `"Memory"`, `"I/O"`, `"Base"`, JAR 라이브러리는 클래스 이름(`"kr.ac.hallym.hcs.mips.MipsLibrary"`, 보이는 이름은 `display`의 `"Hallym MIPS"`), .circ 라이브러리는 그 파일 이름(확장자 없이). 이 파일의 회로(서브회로)는 `null`.
- 좌표는 Logisim 논리 좌표(정수, 격자 10).

## 4. 값

한 넷의 값은 폭만큼의 글자, 높은 비트부터: `'0'`, `'1'`, `'x'`(떠 있음·정해지지 않음), `'E'`(오류). 예: 1비트 `"1"`, 4비트 `"01x1"`. 화면은 이 글자로 선 색(0·1·x·E·버스)과 값 칩을 정한다. 넷의 폭은 선 묶음이 정한 폭, 없으면 포트 폭 가운데 가장 큰 것, 그것도 없으면 1이다. 값이 폭보다 좁으면 모자라는 높은 비트는 `'x'`다.

## 5. 메서드(v0)

### engine

| 메서드 | params | result |
| --- | --- | --- |
| `engine.hello` | `{client, version}` | `{engine:"hcs-engine", version, logisim:"2.7.1", java, api:"0"}` |
| `engine.shutdown` | `{}` | `{}` 뒤 종료 |

`engine.log = {level:"info"|"warn"|"error", message}`: 응답에 딸리지 않은 알림(예: N Cycles가 발진으로 멈춤, 보던 인스턴스가 사라짐).

### file

| 메서드 | params | result |
| --- | --- | --- |
| `file.new` | `{}` | `{fileId, name, circuits:[CircuitRef], main, libraries:[LibRef]}`(원조 File › New의 기본 틀) |
| `file.open` | `{path, readOnly?}` | `{fileId, name, circuits:[CircuitRef], main, libraries:[LibRef], messages:[글], alreadyOpen?}` |
| `file.save` | `{fileId, path?}` | `{path, bytes, needsMipsJar}`(Logisim 저장 코드, 새 부품을 안 쓴 파일은 원조와 바이트 같음) |
| `file.close` | `{fileId}` | `{}` |
| `file.dirty` | `{fileId}` | `{dirty}` |

`CircuitRef = {circuitId, name}`, `LibRef = {lib, display, kind:"builtin"|"jar"|"circ", path?}`(`path`는 .circ에 적힌 경로 글자)

- `path`는 절대 경로로 보낸다(상대 경로는 엔진 프로세스의 작업 폴더 기준이다).
- `file.open`: `messages`는 원조 로더가 대화상자로 보이던 경고(예: 알 수 없는 부품)다. 이미 열린 파일(같은 경로)을 다시 열면 그 `fileId`를 `alreadyOpen:true`와 함께 돌려준다. `readOnly`면 편집과 경로 없는 저장이 오류 3(`readOnly`)이다.
- `file.save`: `path`가 없으면 연 파일(또는 마지막으로 저장한 경로)에 쓴다. 한 번도 저장하지 않은 새 파일은 `path`가 있어야 한다(-32602). 다른 경로에 저장하면 그 경로가 이 파일의 경로가 되고(Save As) 읽기 전용이 풀린다. 포크의 .circ 확장 정보(D-024)도 원조 방식으로 저장한 뒤 붙인다. `needsMipsJar`: MIPS 부품을 쓰는데 저장한 .circ 옆에 `hcs-mips.jar`가 없다(원조 2.7.1이 열려면 필요, 화면이 알린다).

### model

| 메서드 | params | result |
| --- | --- | --- |
| `model.circuit` | `{fileId, circuitId}` | `Snapshot` |
| `model.library` | `{fileId}` | `[{lib, display, pending?, tools:[{name, display, circuitId?}]}]`(부품 목록 트리) |

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

- `components`·`wires`는 위→아래, 왼쪽→오른쪽 순서다. `attrs`에는 .circ에 저장되는 속성을 기본값까지 모두 싣는다(.circ에는 기본값과 다른 것만 적힌다). `facing`은 `attrs.facing`과 같고, 방향 속성이 없는 부품은 `null`이다.
- `ports[].name`은 포크의 부품 등록표 이름(서브회로는 안쪽 핀의 라벨)이다. `dir`은 부품 쪽에서 본 방향이다(출력 핀은 넷을 읽으므로 `"in"`).
- 넷은 원조 연결 계산으로 묶은 선과 그 선의 끝·선 위에 닿은 포트, 선 없이 한 점에 닿은 포트들이다. 같은 이름의 터널은 한 넷이다(스플리터는 넷을 잇지 않는다). 모든 포트와 선은 정확히 한 넷에 든다.
- `junctions`: 선 끝 가운데 선·포트가 셋 이상 만나는 점(원조가 점을 그리는 조건).
- `subcircuit`: 이 파일의 회로면 `lib`이 `null`이다. .circ 라이브러리의 회로 인스턴스는 `lib`이 그 라이브러리 이름이고 `subcircuit`이 그 회로를 가리킨다. 라이브러리 회로도 `model.circuit`·`sim.watch`로 볼 수 있지만 편집은 오류 3(`cannotModify`)이다.
- `model.library`: 첫 항목은 이 파일의 회로들(`lib:null`, 도구마다 `circuitId`), 그다음 파일의 라이브러리 순서다. 부품 도구(AddTool)만 싣는다(Poke·Edit·Wiring·Text·Menu 도구는 화면의 몫). `pending:true`인 라이브러리(번들 Hallym MIPS)는 아직 파일에 들어가지 않았고, 그 부품을 처음 놓는 편집에서 파일에 들어간다(되돌리면 빠진다, V-01·D-096).

### edit(의도)

모두 `{fileId, circuitId, …}`를 받고 `{changed, outcome?, id?}`를 돌려준 뒤 `model.changed`를 보낸다. 하나의 의도가 되돌리기 한 단계다. 바뀐 것이 없으면 `changed:false`이고 `model.changed`를 보내지 않는다.

| 메서드 | 더 받는 것 | 하는 일(Logisim 코드) |
| --- | --- | --- |
| `edit.addComponent` | `lib, name, loc, attrs?` | 부품 놓기(AddTool과 같은 동작). result `id`: 놓은 부품 |
| `edit.addWire` | `points:[[x,y],…]`(2~3점, ㄱ자는 3점) | 선 긋기(WiringTool과 같은 합치기·나누기·줄이기·포트 잇기) |
| `edit.move` | `ids, dx, dy, connect?` | 옮기기와 따라오는 선(v1의 따라오는 선 규칙) |
| `edit.delete` | `ids` | 지우기 |
| `edit.setAttr` | `ids, attr, value` | 속성 바꾸기(글자는 .circ에 저장되는 글자) |
| `edit.undo`, `edit.redo` | —(`circuitId`는 없어도 된다) | Logisim 되돌리기 기록 그대로(다시 실행은 포크의 RedoStack) |

- `edit.addComponent`: `lib:null`(또는 빼면)이면 이 파일의 회로를 이름(`name`)으로 놓는다. `attrs`는 놓는 부품에만 쓴다(도구의 기본값은 바꾸지 않는다). `loc`은 그대로 쓴다(격자 맞추기는 화면 몫). 오류: 없는 도구 1, `circular`·`exclusive`·`negativeCoord` 3, 모르는 속성·틀린 값 -32602.
- `edit.addWire`: 2점은 가로·세로 곧은 선(3점이면 가운데 점이 그 선 위), ㄱ자는 `[시작, 꺾는 점, 끝]`이고 꺾는 점이 `[끝x, 시작y]`(가로 먼저) 또는 `[시작x, 끝y]`(세로 먼저)여야 한다. 원조처럼 한쪽 끝이 있는 선을 따라 되돌아 끌면 그 선을 줄이거나 지운다(`outcome:"shortened"|"removed"`). 시작과 끝이 같으면 `changed:false, outcome:"empty"`.
- `edit.move`: `connect`(기본 true)면 원조 연결 유지 계산 뒤 v1 SafeMove(D-055)의 기준으로 남긴다. `outcome`: `"moved"`, `"movedWithoutWires"`(선을 잇지 못하고 옮김), `"refused"`(`changed:false`, 다른 넷이 바뀌므로 옮기지 않음). 다른 출력과 한 점에 겹치면 오류 3 `exclusive`.
- `edit.setAttr`: 선은 건너뛴다. 모든 부품에 그 속성이 있어야 한다(없으면 -32602). 속성은 부품 객체 안에서 바뀌므로 같은 id가 `added`로 온다.
- 편집하면 그 회로가 시뮬레이션의 지금 회로가 된다(Swing에서 보고 있는 회로를 편집하는 것과 같다). 다른 회로를 보고 있었다면 화면이 `sim.watch`를 다시 보낸다.

`model.changed = {fileId, circuitId, removed:[id], added:[Component|Wire], nets, junctions, dirty}`

- 화면은 `removed`를 먼저 지우고 `added`를 id로 넣거나 바꾼다(upsert). 제자리에서 바뀐 부품(같은 id)은 `added`에만 온다.
- `nets`·`junctions`는 그 회로의 **전체** 목록이다(넷 번호를 다시 매겼다). 한 편집이 다른 회로도 바꾸면(예: 서브회로 핀을 바꿔 인스턴스 포트가 바뀜) 회로마다 하나씩 온다. `dirty`는 파일의 저장 필요 여부다.

### sim

| 메서드 | params | result |
| --- | --- | --- |
| `sim.reset` | `{fileId}` | `{}`(원조 Reset Simulation, 사이클 수 0. N Cycles 도중이면 처리 중인 틱이 끝난 뒤 재설정하고 그때 `sim.state`) |
| `sim.poke` | `{fileId, circuitId, componentId, at?:[x,y], action?}` | `{poked}`(Poke 도구와 같은 동작: 핀 값 바꾸기 등) |
| `sim.cycles` | `{fileId, n}` | `{}` 곧바로. 틱은 엔진이 따라오는 만큼만 요청한다(D-123). 끝나면 `sim.state` |
| `sim.run` | `{fileId, on, hz?}` | `{}` |
| `sim.enable` | `{fileId, on}` | `{}`(원조 Simulation Enabled: 발진으로 꺼진 뒤 다시 켠다) |
| `sim.watch` | `{fileId, circuitId, path?:[componentId]}` | `{}`: 이 회로(서브회로 안이면 인스턴스 경로)의 넷 값을 보낸다 |
| `sim.state` | `{fileId}` | 아래 `sim.state`와 같은 객체(요청으로도 물을 수 있다) |

- `sim.poke`: `action`은 `"click"`(기본, 누르고 뗌), `"press"`, `"release"`(버튼처럼 누르는 동안만 켜지는 부품은 화면이 누를 때와 뗄 때 따로 보낸다). `at`은 회로 좌표(여러 비트 핀에서 어느 비트인지), 없으면 부품 가운데. 누를 것이 없는 부품·선은 `poked:false`. 보고 있지 않은 회로의 부품을 누르면 그 회로가 시뮬레이션의 지금 회로가 된다. 서브회로를 보며 그 안의 입력 핀을 누르면 오류 4 `frozenPin`(원조는 상태를 복제할지 묻는다).
- `sim.cycles`: `n` ≥ 1, 한 사이클 = 원조 틱 2번. 처리 중인 틱이 8개를 넘지 않게 요청하고 틱 완료로 센다(틱이 빠지지 않는다). 돌고 있으면 `n`을 더한다. 시뮬레이션이 꺼져 있으면 오류 4(`off`·`oscillating`), 도중에 꺼지면 `engine.log`와 `sim.state`로 알리고 멈춘다. 끝나면 그 순간의 값(`sim.values`)을 먼저, `sim.state`를 뒤에 보낸다.
- `sim.run`: `on`이면 원조 틱(Ticks Enabled)을 켜고, `hz`는 원조 틱 주파수(초당 틱, Swing 속도 메뉴와 같은 값: 1·4·16·64·256·1024·4096)다. 켤 때 시뮬레이션이 꺼져 있으면 오류 4.
- `sim.watch`: `circuitId`는 시작 회로, `path`는 거기서 내려가는 서브회로 인스턴스 id들이다. 보는 회로는 시뮬레이션의 지금 상태가 된다(Swing에서 그 회로·인스턴스를 여는 것과 같다). 파일마다 하나만 본다(다시 보내면 바꾼다). 처음에는 모든 넷을 한 번 보낸다. 없는 경로는 오류 1.

`sim.values = {fileId, circuitId, root?, path?, nets:{netId: value}}` — 보고 있는 회로의 바뀐 넷만, 화면 프레임(약 16ms)마다 묶어서. `circuitId`는 값이 속한 회로(경로의 끝), `root`·`path`는 `path`로 볼 때만 온다. 모델이 바뀌면(넷 번호가 새로 매겨지면) 다음 묶음에 모든 넷을 다시 보낸다.

`sim.state = {fileId, running, ticking, cycle, oscillating, hz}` — 무엇이든 바뀌면 보내고(사이클 수만 바뀐 것은 프레임마다 많아야 한 번), N Cycles가 끝날 때와 Reset 뒤에도 보낸다. `running`은 원조 Simulation Enabled, `ticking`은 틱 켜짐, `cycle`은 Reset 뒤 끝난 틱 수의 절반, `hz`는 틱 주파수다.

## 6. 확장

진단(Messages), E/X 출처, 영향 경로, Signal Flow, 기록(사이클 표, Registers·Memory·Instruction), MIPS(.hmx·.s 불러오기, 디스어셈블, Console)는 각 N 항목에서 이 문서에 절을 더하며 늘린다. 메서드 이름은 `diag.*`, `trace.*`, `record.*`, `mips.*`로 묶는다.
