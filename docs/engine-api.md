# 엔진 API(v2, JSON-RPC over stdio)

Hallym Circuit Studio 2의 화면(Electron)과 Java 엔진(headless Logisim 2.7.1)이 주고받는 규약이다(D-132, D-133). 이 문서가 두 쪽의 계약이고, 바꾸면 두 쪽 테스트와 이 문서를 함께 고친다. 엔진 쪽 구현은 `engine/`(N-03, D-134)이다.

## 1. 원칙

- **엔진이 권위다.** 회로 모델의 진짜 상태는 엔진에 있다. 화면은 사본을 들고, 엔진이 보내는 변경분(`model.changed`)으로 맞춘다.
- **편집은 의도로 보낸다.** 화면은 "이 자리에 AND를 놓아라", "이 두 점을 선으로 이어라"만 보내고, 실제 변경은 엔진이 **Logisim의 편집 코드**(선 합치기·나누기, 연결점, 되돌리기 기록)로 한다. 그래서 결과 .circ가 원조와 같다(편집 동등성 N-09).
- **엔진을 고치지 않는다.** 엔진 패키지(`com.cburch.logisim.circuit/comp/data/instance/std/file`)는 그대로 쓰고, 서버 코드는 `kr.ac.hallym.hcs.engine.*`에 둔다.
- **디스크에 설정을 남기지 않는다.** 엔진은 메모리 전용 Preferences로 돌고 원조 Logisim의 디스크 설정을 읽지 않는다(실습실 규칙, N-19). 학생 파일 말고 쓰는 것은 한 번이라도 저장한 파일 옆의 복구 파일 하나뿐이다(7절 "복구 파일", D-152).

## 2. 전송

- 엔진은 Electron main 프로세스가 띄우는 자식 프로세스 하나다(앱당 하나, 여러 파일을 함께 연다). `java -Djava.awt.headless=true -jar hcs-engine.jar`(엔진도 시작할 때 headless로 맞춘다).
- 번들 MIPS 라이브러리 `hcs-mips.jar`는 시스템 속성 `-Dhcs.bundledMips=<경로>`, 없으면 `hcs-engine.jar` 옆, 그다음 옆의 `lib/`에서 찾는다. .circ의 `jar#…#kr.ac.hallym.hcs.mips.MipsLibrary`가 가리키는 jar가 없으면 이것으로 잇는다(v1과 같은 D-007).
- stdin·stdout: **JSON-RPC 2.0, 한 줄에 한 JSON 객체**(UTF-8, 줄 끝 `\n`). 요청은 `{"jsonrpc":"2.0","id":N,"method":"…","params":{…}}`, 응답은 `{"jsonrpc":"2.0","id":N,"result":…}` 또는 `"error":{"code","message","data"}`.
  - `id`는 숫자·글자 모두 된다(받은 그대로 돌려준다). `params`는 이름 있는 객체만 받는다(배열이면 -32602). 여러 요청을 배열로 묶은 요청(batch)은 받지 않는다(-32600).
  - 화면이 `id` 없이 보낸 것(알림)은 처리만 하고 응답하지 않는다.
- 엔진이 먼저 보내는 알림은 `id`가 없다(`model.changed`, `sim.values`, `sim.state`, `diag.changed`, `mips.facts`, `mips.reloaded`, `mips.console`, `engine.log`, N-11의 `file.changed`·`model.appearance`·`model.portImpact`·`file.libraryUpdated`).
- stdout에는 규약 줄만 나온다. Logisim 코드가 `System.out`에 쓰는 것도 stderr로 돌린다. stderr는 사람이 읽는 로그다(화면은 파일에 남기지 않는다).
- 요청은 순서대로 처리한다. 긴 일(N Cycles, Run Until)은 곧바로 응답하고 진행은 알림으로 보낸다. 편집의 `model.changed`는 그 편집의 응답 **뒤에**, 그 뒤의 `sim.values`보다 **앞에** 온다.
- 끝: `engine.shutdown`에 응답한 뒤, stdin이 닫히면, 또는 부모 프로세스가 끝나면(7절) 열린 파일을 닫고 종료 코드 0으로 끝난다(저장하지 않는다). 메모리 전용 환경설정을 켜지 못하면 코드 3으로 바로 끝난다.
- 잰 값(2026-09-27, Linux, JDK 21, `engine/build/engine-measure.txt`): 시작 → `engine.hello` 응답 약 80ms, ref-mips를 연 뒤 상주 메모리 약 115MB(`-XX:+UseSerialGC -XX:TieredStopAtLevel=1`이면 약 87MB). JVM 옵션과 번들 런타임은 7절(N-04, D-142).

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
- 번호는 엔진 전체에서 하나씩 늘고 다시 쓰지 않는다(파일이 달라도 겹치지 않는다). 모델에서 사라진 부품·선의 id는 잊는다: 되돌리기로 같은 부품이 돌아오면 **새 id**로 `added`에 온다. 엔진이 죽었다가 다시 시작하면 새 엔진은 `idFloor`보다 큰 번호부터 쓰고, 되살린 파일은 앞 엔진의 파일·회로 id를 다시 쓴다(부품·선 id는 새것, 7절).
- 라이브러리 이름 `lib`: Logisim 라이브러리 이름 그대로다. 기본 라이브러리는 `"Wiring"`, `"Gates"`, `"Plexers"`, `"Arithmetic"`, `"Memory"`, `"I/O"`, `"Base"`, JAR 라이브러리는 클래스 이름(`"kr.ac.hallym.hcs.mips.MipsLibrary"`, 보이는 이름은 `display`의 `"Hallym MIPS"`), .circ 라이브러리는 그 파일 이름(확장자 없이). 이 파일의 회로(서브회로)는 `null`.
- 좌표는 Logisim 논리 좌표(정수, 격자 10).

## 4. 값

한 넷의 값은 폭만큼의 글자, 높은 비트부터: `'0'`, `'1'`, `'x'`(떠 있음·정해지지 않음), `'E'`(오류). 예: 1비트 `"1"`, 4비트 `"01x1"`. 화면은 이 글자로 선 색(0·1·x·E·버스)과 값 칩을 정한다. 넷의 폭은 선 묶음이 정한 폭, 없으면 포트 폭 가운데 가장 큰 것, 그것도 없으면 1이다. 값이 폭보다 좁으면 모자라는 높은 비트는 `'x'`다.

## 5. 메서드(v0)

### engine

| 메서드 | params | result |
| --- | --- | --- |
| `engine.hello` | `{client, version, idFloor?, recoveryFiles?}` | `{engine:"hcs-engine", version, logisim:"2.7.1", java, api:"0"}` |
| `engine.shutdown` | `{}` | `{}` 뒤 종료 |

`engine.log = {level:"info"|"warn"|"error", message}`: 응답에 딸리지 않은 알림(예: N Cycles가 발진으로 멈춤, 보던 인스턴스가 사라짐).

- `idFloor`(0 이상의 정수): 다시 시작한 엔진에 main이 준다. 이 엔진이 새로 매기는 파일·회로·부품·선 번호는 모두 이 수보다 크다(7절). 없으면 1부터다.
- `recoveryFiles`(참·거짓, N-19): 참이면 엔진이 학생 파일 옆의 복구 파일을 맡는다(7절 "복구 파일"): 저장·닫기·`engine.shutdown`에 지우고, 화면이 사라져 끝날 때(stdin 닫힘·부모 끝남) 저장하지 않은 파일마다 쓴다. 앱(main)은 늘 참으로 준다. 없거나 거짓이면 명시한 요청(`file.recoverWrite`, `file.open`의 `recovery`) 말고는 복구 파일을 건드리지 않는다(테스트, 다른 클라이언트).

### file

| 메서드 | params | result |
| --- | --- | --- |
| `file.new` | `{restore?}` | `{fileId, name, circuits:[CircuitRef], main, libraries:[LibRef]}`(원조 File › New의 기본 틀) |
| `file.open` | `{path, readOnly?, restore?, recovery?}` | `{fileId, name, circuits:[CircuitRef], main, libraries:[LibRef], messages:[글], alreadyOpen?}` |
| `file.save` | `{fileId, path?}` | `{path, bytes, needsMipsJar}`(Logisim 저장 코드, 새 부품을 안 쓴 파일은 원조와 바이트 같음) |
| `file.close` | `{fileId, keepRecovery?}` | `{}` |
| `file.dirty` | `{fileId}` | `{dirty}` |
| `file.recoverWrite` | `{fileId}` | `{path, written, bytes?}`(복구 파일, N-19: 7절) |

`CircuitRef = {circuitId, name}`, `LibRef = {lib, display, kind:"builtin"|"jar"|"circ", path?}`(`path`는 .circ에 적힌 경로 글자)

- `path`는 절대 경로로 보낸다(상대 경로는 엔진 프로세스의 작업 폴더 기준이다).
- `restore = {fileId, circuits?:{회로 이름: circuitId}}`: 다시 시작한 엔진이 파일을 되살릴 때만 쓴다(7절). 새 id 대신 앞 엔진의 파일 id를 쓰고, 이름이 같은 회로에 앞 엔진의 회로 id를 붙인다(이름이 없는 회로는 새 id). `fileId`가 `"f"`+숫자가 아니거나 지금 열린 파일이 쓰면 -32602, 모양이 틀린 회로 id는 붙이지 않는다.
- `file.open`: `messages`는 원조 로더가 대화상자로 보이던 경고(예: 알 수 없는 부품)다. 이미 열린 파일(같은 경로)을 다시 열면 그 `fileId`를 `alreadyOpen:true`와 함께 돌려준다. `readOnly`면 편집과 경로 없는 저장이 오류 3(`readOnly`)이다.
- `file.open`의 `recovery`(N-19, 7절 "복구 파일"): `"recover"`면 `path` 옆 `<이름>.circ.hcs-recover`의 내용을 `path` 자리에서 연다(원조 Loader의 바꿔 읽기, 명령줄 `-sub`와 같은 길): 파일 경로·이름·상대 경로 라이브러리는 `path`의 것이고 저장하지 않은 편집이다(`dirty`, 되돌리기를 모두 되돌려도, 저장할 때까지). 복구 파일이 없으면 오류 2(`notFound`, `data.path`는 복구 파일). `"discard"`면 `path`를 보통으로 연 뒤 복구 파일을 지운다(열지 못하면 남긴다). 그 밖의 값은 -32602. 이미 열린 파일이면 무시한다.
- `file.close`의 `keepRecovery`: 참이면 엔진이 복구 파일을 맡고 있어도(`recoveryFiles`) 지우지 않는다. main의 되살리기가 재생에 실패한 파일을 닫고 다시 열 때 쓴다(7절).
- `file.recoverWrite`: 둘 곳은 디스크 경로가 있고 읽기 전용으로 열지 않은 파일, 그 폴더에 쓸 수 있을 때뿐이다(쓸 수 없는 폴더는 조용히 건너뛰어 `written:false`). 저장하지 않은 편집이 있으면 연 파일 옆 `<이름>.circ.hcs-recover`에 쓰고 `{path, written:true, bytes}`, 없으면 있던 것을 지우고 `{path, written:false}`. 둘 곳이 없는 파일(한 번도 저장하지 않은 새 파일, 읽기 전용)은 아무것도 쓰지 않고 `{path:null, written:false}`. 쓰지 못하면 오류 2(`writeFailed`). 창은 부를 수 없다(main만).
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
  junctions: [[x,y]],           // 세 갈래 이상이 만나는 점(연결점)
  groups?: [{net, group:"control"|"data"|"address", assigned}],   // 신호 그룹(N-15), 없으면 빠진다
  memos?: [{x, y, w, h, color, text}]                               // 영역 메모(N-15), 없으면 빠진다
}
Component = {
  id, lib, name,                // 예: "Gates", "AND Gate"
  loc:[x,y], bounds:[x,y,w,h], facing:"east"|"west"|"north"|"south"|null,
  attrs: {<.circ 속성 이름>: <.circ에 저장되는 글자>},
  ports: [{i, loc:[x,y], width, dir:"in"|"out"|"inout", name?}],
  subcircuit?: circuitId,       // 서브회로 인스턴스면
  appearance?: Appearance,      // 서브회로 인스턴스면: 그 회로의 모양(N-05)
  ext?: {color?, arms?}         // 학생이 직접 정한 확장 정보(hcs:ext): 터널 색, 스플리터 팔 이름(N-12)
}
```

- `components`·`wires`는 위→아래, 왼쪽→오른쪽 순서다. `attrs`에는 .circ에 저장되는 속성을 기본값까지 모두 싣는다(.circ에는 기본값과 다른 것만 적힌다). `facing`은 `attrs.facing`과 같고, 방향 속성이 없는 부품은 `null`이다.
- `ports[].name`은 포크의 부품 등록표 이름(서브회로는 안쪽 핀의 라벨)이다. `dir`은 부품 쪽에서 본 방향이다(출력 핀은 넷을 읽으므로 `"in"`).
- 넷은 원조 연결 계산으로 묶은 선과 그 선의 끝·선 위에 닿은 포트, 선 없이 한 점에 닿은 포트들이다. 같은 이름의 터널은 한 넷이다(스플리터는 넷을 잇지 않는다). 모든 포트와 선은 정확히 한 넷에 든다.
- `junctions`: 선 끝 가운데 선·포트가 셋 이상 만나는 점(원조가 점을 그리는 조건).
- `groups`·`memos`(N-15, D-151): 학생이 회로에 둔 표시 정보(.circ 확장 정보 hcs:ext, 원조 회로 부분과 무관). `groups`는 그룹이 있는 넷(`net`은 이 스냅숏의 넷 번호)이고 `assigned`는 학생이 정한 것, 아니면 이름이 `control`인 서브회로 인스턴스의 출력이라 기본 Control인 것이다(v1 E-04). `memos`는 파일 차례의 영역 메모이고 `color`는 터널 색 팔레트 번호(0~11, v1 E-08)다. 없으면 두 칸 모두 빠진다(빈 목록과 같다).
- `appearance`(N-05, D-137): 서브회로 인스턴스를 원조와 같은 자리·크기로 그리는 도형. 기본 모양(원조가 핀으로 만드는 상자와 홈)도 사용자 모양도 같은 꼴이다.

  ```
  Appearance = {default, anchor:[x,y], facing, shapes:[{tag, attrs:{이름: 글자}, text?}],
                ports:[{at:[x,y], pin?:[x,y], input}], label?:{text, facing, font}}
  ```

  `shapes`는 원조 .circ의 `<appear>`와 같은 SVG 요소(`rect`, `ellipse`, `line`, `polyline`, `polygon`, `path`(`M`·`Q`), `text`)를 속성 글자 그대로 싣는다(원조 `toSvgElement`). 좌표는 모양 편집기의 좌표이고, 인스턴스(위치 `loc`, 방향 `f`)에서 점 `p`는 `loc + R(θ)(p − anchor)`, θ = `facing`(모양의 방향) − `f`(원조 `Direction.toRadians`, R은 y가 아래인 화면 좌표의 회전)다. `ports[].at`을 그렇게 옮기면 이 부품의 `ports[].loc`이 된다. `pin`은 서브회로 안 핀의 위치, `input`은 입력 핀인지, `label`은 회로 속성의 부품 안 글자(원조 Circuit Label: `clabel`, `clabelup`, `clabelfont`)다. 서브회로의 모양이 바뀌면(핀을 더함 등) 그 인스턴스들이 `model.changed`의 `added`로 다시 온다.
- `ext`(N-12, D-150): .circ 확장 정보(hcs:ext, D-024) 가운데 화면이 그리는 것, 학생이 직접 정한 것만. 터널은 `{color:"#RRGGBB"}`(Tunnel Color로 고른 팔레트 색, v1 `TunnelColorStore`; 고르지 않은 터널은 없고 화면이 이름으로 자동 색을 정한다), 스플리터는 `{arms:["op","rs",…]}`(Splitter 편집기의 팔 이름, 위 팔부터; 이름 항목의 팔 수가 부품의 팔 수와 같을 때만, 저장할 때의 v1 정리 규칙과 같다). 이 파일의 회로가 아니면(.circ 라이브러리) 없다. 그 정보가 바뀌면(색·이름을 바꾸거나 되돌림) 그 부품들이 같은 id로 `model.changed`의 `added`에 온다.
- `subcircuit`: 이 파일의 회로면 `lib`이 `null`이다. .circ 라이브러리의 회로 인스턴스는 `lib`이 그 라이브러리 이름이고 `subcircuit`이 그 회로를 가리킨다. 라이브러리 회로도 `model.circuit`·`sim.watch`로 볼 수 있지만 편집은 오류 3(`cannotModify`)이다.
- `model.library`: 첫 항목은 이 파일의 회로들(`lib:null`, 도구마다 `circuitId`), 그다음 파일의 라이브러리 순서다. 부품 도구(AddTool)만 싣는다(Poke·Edit·Wiring·Text·Menu 도구는 화면의 몫). 옛 파일을 위해서만 남긴 부품(Hallym MIPS의 `Stack`, D-140)은 새로 놓는 목록이라 싣지 않는다. 파일 안의 그 부품은 `model.circuit`에 전처럼 온다(`lib`은 MIPS 라이브러리). `pending:true`인 라이브러리(번들 Hallym MIPS)는 아직 파일에 들어가지 않았고, 그 부품을 처음 놓는 편집에서 파일에 들어간다(되돌리면 빠진다, V-01·D-096). 라이브러리 목록이 바뀐 것은 따로 알리지 않으므로, 그런 편집 뒤에는 화면이 `model.library`를 다시 묻는다.

### mips

| 메서드 | params | result |
| --- | --- | --- |
| `mips.facts` | `{fileId}` | `{fileId, facts:[{id, en, ko, components:[componentId], sources?:[글], pc?, entry?}], program: Program\|null}` |
| `mips.load` | `{fileId, path, target?:componentId, picks?:{text?, data?}}` | `LoadResult`(아래) |
| `mips.reload` | `{fileId}` | `{fileId, results:[{source, ok, changed?, problems?}]}` |
| `mips.disasm` | `{fileId, componentId, from?, count?}` | `{fileId, componentId, words, first, last, entry, symbols, lines:[{addr, word, text, labels?, entry?}]}` |
| `mips.console` | `{fileId}` | `{fileId, consoles:[{name, text, exited}]}` |

알림: `mips.facts`(result와 같은 객체, 바뀌었을 때), `mips.reloaded`(자동으로 다시 불러왔거나 불러오지 못함), `mips.console`(Console 출력, 바뀐 프레임마다).

- `mips.facts`: 파일의 MIPS **사실**(D-140, D-141, D-147). 진단(Messages)이 아니다: 회로는 동작하고, 도구가 바뀐 사실과 할 일만 상태 표시줄에 한 줄로 보인다(화면은 N-16/N-17). 문장은 영어(`en`)·한국어(`ko`) 두 벌이고 화면이 언어 설정에 맞춰 고른다. 파일을 열 때 묻고, 엔진은 바뀔 때마다(약 100ms마다 보고) 같은 객체를 알림 `mips.facts`로 보낸다.
  - `separateStack`: 파일에 따로 된 옛 Stack 부품이 있다. `ko` = "이 회로는 따로 된 Stack 부품을 씁니다. 새 Data Memory는 스택 영역을 함께 맡습니다.", `components` = 파일 안의 Stack 부품들(모든 회로). 옛 Stack은 전과 똑같이 동작하므로 고치라고 하지 않는다.
  - `assemblySource`(D-141): MIPS 메모리 부품의 `source` 속성이 .s(.asm, 대소문자 무관)를 가리킨다. .s 불러오기와 hcs-asm은 없어졌다. `ko` = "이 파일은 .s 파일을 가리킵니다. Hallym MIPS에서 Export executable image (.hmx) 단추로 내보낸 파일을 불러오세요.", `en` = "This file points to a .s file. Load the file exported with Export executable image (.hmx) in Hallym MIPS.", `components` = 그 부품들(모든 회로), `sources` = 부품마다 속성 글(`components`와 같은 순서, 예 `"prog/sum.s"`). 속성은 읽기만 하므로 파일은 전과 같이 열리고, 고치지 않으면 같은 바이트로 저장된다. 불러오기(N-16, 트랙 A `ProgramLoader`와 같은 길)가 .hmx를 넣으면 `source`가 그 .hmx 경로(.circ 기준 상대 경로)로 바뀌고 사실이 없어진다. 불러오기에 .s를 넘기면 이미지 없이 같은 문장 하나가 오류로 온다.
  - `pcEntry`(N-16, D-138): 사이클 0(`sim.state.cycle` = 0)에서 회로의 PC(상태 표시줄의 규칙 D-103: Mark as PC → 라벨이 PC인 부품 → Instruction Memory의 Addr)가 올린 실행 이미지의 entry와 다르다. `ko` = "PC 0x00400024 · 실행 이미지 진입점 0x0040002c", `en` = "PC 0x00400024 · executable image entry 0x0040002c"(`StartFacts.pcFact`), `components` = 그 Instruction Memory, `pc`·`entry`. 사이클이 0이 아니거나 PC가 정해지지 않았거나 entry를 모르면 없다. 도구는 PC에 아무것도 넣지 않는다(PC 시작은 학생 회로의 몫).
- `program`(N-16): 올린 프로그램. 메모리 부품(Instruction Memory 먼저, 그다음 Data Memory, 위→아래)의 `source`가 .hmx를 가리키거나 다시 불러오기 실패가 서 있을 때만 있고, 아니면 `null`.
  `Program = {name, source, entry, loadedAt, failure, memories:[{componentId, circuitId, kind:"text"|"data", source, words, entry?, text}]}`. `name`은 .hmx 파일 이름(`data.hmx`), `source`는 속성 글, `entry`는 부품 내용과 워드가 같은 이미지(이번 실행에서 넣은 것, 아니면 디스크의 .hmx)의 entry(모르면 `null`), `loadedAt`은 이번 실행에서 마지막으로 불러오거나 다시 불러온 시각(ms, 파일에 저장된 채로 열었으면 `null`). `memories[].text`는 부품을 한 줄로 말한 것: `27 words (0x00400000–0x00400068), entry 0x00400024`(비었으면 `no program`). 캔버스(N-05)의 Instruction Memory 몸체는 이 줄이 아니라 `sim.values.bodies`의 줄(트랙 A 몸체와 같은 영역·워드 수·주소의 워드)을 그린다: 200×80 몸체의 포트 이름 사이에 이 줄은 8px 아래로 줄여야만 들어간다(D-137 9-10).
  `failure = {at, file, source, reason:"open"|"changed"|"reset"|"manual", problems:[Problem], kept:{loadedAt}}`: 다시 불러오지 못해 **올라가 있던 프로그램과 시뮬레이션을 그대로 둔** 동안(화면의 띠). `kept.loadedAt`은 올라가 있는 것을 불러온 시각(`null`: .circ에 저장된 것). 같은 .hmx가 다시 읽히면(불러오기·다시 불러오기) 걷힌다.
- **`mips.load`**(N-16, D-147): 실행 이미지(.hmx) 하나를 트랙 A 메뉴와 같은 길(lib-mips `ProgramLoader` read·plan, 공용 `HmxParser`·`StartFacts`·`SourceCheck`)로 넣는다. `path`는 절대 경로(화면은 main 프로세스의 고르기 창에서만 얻는다: 페이지는 경로를 보내지 않는다). `target`은 사람이 고른 메모리 부품(우클릭): 그 종류의 구간을 모두 담아야 한다. **전부 아니면 전무**: 문제가 하나라도 있거나 고를 것이 남으면 아무것도 바꾸지 않는다. 넣으면 부품마다 `contents`와 `source`(.circ 폴더 기준 상대 경로, 저장한 적 없는 파일이면 절대 경로: 트랙 A와 같은 속성, 원조 2.7.1 + hcs-mips.jar에서 열고 저장해도 같은 바이트)를 원조 `SetAttributeAction` 한 번(되돌리기 한 단계, 이름 `Load Program`)으로 바꾸고, 시뮬레이션을 처음으로 돌린다(`sim.reset`과 같다, `sim.state`가 따른다). 레지스터에는 아무것도 넣지 않는다. 읽기 전용 파일은 오류 3 `readOnly`, 없는 `target`·다른 종류의 `picks`는 오류 1·-32602, lib-mips가 없으면 -32603.
  `LoadResult = {fileId, file, loaded, source?, loadedAt?, summary?, choose?, problems?}`:
  - `loaded:true` → `summary = {entry, entryLine, regs:[{name, value}], segments:[{kind, start, last, range, units, unit, bytes, words, text, componentId, circuitId, target}], emptied, stackBase:[{componentId, target, part, stack, sp}], instructions, source:{status:"same"|"changed"|"notFound"|"noHash", name, text:{en,ko}, warn}, facts:[{id:"noHandler"|"jrRa", text:{en,ko}}], producedBy, assembled, notes}`. `segments[].text` 예: `27 words (0x00400000–0x00400068)`, `28 bytes = 7 words (0x10010000–0x1001001b)`. `target`은 트랙 A 목록과 같은 이름(`main › Instruction Memory (00400000-004fffff)`). `emptied`는 `.data`가 없어 비운 Data Memory, `stackBase`는 `reg $sp`를 깊이 기준으로 기억한 부품(D-140: `target`은 트랙 A 목록 이름, `part`는 영역 없는 이름, `stack`은 그 스택 영역 `7ffc0000-7fffffff`), `notes`는 트랙 A 요약 창과 같은 줄들.
  - `choose = {kind:"text"|"data", segment, candidates:[{componentId, circuitId, name}]}`: 한 구간을 담는 부품이 여럿이다(위→아래 순). 화면이 묻고 `picks`에 고른 부품을 넣어 **같은 파일로** 다시 부른다(`text`를 고른 뒤 `data`를 또 물을 수 있다).
  - `problems = [{line, text:{en,ko}}]`: 읽지 못했거나(줄 번호 → 무엇이 틀렸나 → 무엇을 할까, `HmxParser`) 넣을 곳이 없다(`line` 0). .s(.asm)를 넘기면 `assemblySource`와 같은 문장 하나(D-141).
- **자동 다시 불러오기**(PLAN.md 6.8, v1 C-09의 .hmx판): 앱이 도는 동안만 엔진이 메모리 부품의 `source`가 가리키는 .hmx를 1초마다 본다(수정 시각·크기, 디스크에 아무것도 남기지 않는다). 파일을 연 뒤 처음 볼 때(`reason:"open"`, 열 때 부품이 가리키던 .hmx가 저장된 내용과 다르면. 연 뒤 편집으로 생긴 `source`는 그때부터 바뀜만 본다), 파일이 바뀌었을 때(`changed`), `sim.reset` 앞(`reset`, 감시가 아직 못 본 변경), `mips.reload`(`manual`)에 그 `source`를 가진 부품에 다시 넣는다(한 구간을 담는 부품이 여럿이면 그 `source`를 가진 부품). 내용이 같으면 아무것도 하지 않는다. 바뀌면 되돌리기 한 단계(`Reload data.hmx`)로 넣고 처음으로 돌린 뒤 `mips.reloaded {fileId, ok:true, reason, file, source, loadedAt, summary}`. **실패하면 올라가 있던 프로그램과 시뮬레이션을 그대로 두고** `mips.reloaded {fileId, ok:false, reason, file, source, problems, kept:{loadedAt}}`와 `program.failure`를 알린다(파일이 없어졌으면 "실행 이미지 파일이 그 자리에 없습니다. …"). 읽기 전용 파일과 .s 경로는 보지 않는다.
- **`mips.disasm`**: 메모리 부품(보통 Instruction Memory)의 워드를 lib-mips 디스어셈블러(D-127, SPIM 목록 글)로. 기호는 부품 내용과 워드가 같은 이미지의 것(`jal 0x00400024 [main]`, `bne $17, $0, -16 [next-0x00400048]`), 없으면 `symbols:false`. `from`(주소 글 `0x00400024`·`400024` 또는 수, 없으면 첫 워드)부터 `count`줄(기본 1024, 1~4096). `lines[].labels`는 그 주소의 기호(파일 순서), `entry:true`는 entry 줄.
- **`mips.console`·알림 `mips.console`**(v1 C-09): 보고 있는 회로 상태의 맨 위부터 모든 Console 부품의 출력 전체와 exit 여부(`ConsoleText`, 서브회로 안은 경로 이름). 요청은 `{name, text, exited}`, 알림은 콘솔마다 `text`(바꿈) 또는 `append`(앞에 보낸 것에 이어진 부분)와 `exited`이고 목록에 없는 Console은 사라진 것이다. 바뀐 프레임(16ms)에만 보낸다. `sim.reset`으로 비면 `text:""`가 온다(v1: Reset이 Console을 비운다). 입력은 없다: Console 부품은 출력 syscall(1, 4, 11)과 exit(10)만 처리한다(PLAN.md 6.9, D-147).
- 사실은 파일 내용에서 나오므로 편집(부품을 지우거나 놓음) 뒤에는 화면이 다시 묻는다(엔진도 바뀌면 알린다).
- **Memory 표(`record.memory`, 아래 record 절):** 엔진의 `MemoryTable`이 Hallym MIPS Data 탭 같은 한 표를 만든다. 줄은 `{kind:"section"|"words"|"zeros", section:"data"|"stack", part, addr, end}`(주소는 `"0x10010000"` 꼴 글자)에 `words`(칸 네 개 +0·+4·+8·+C, 구간 밖 `null`, 정해지지 않은 칸 `"xxxxxxxx"`), `zeros`의 `count`, `labels:[{addr, names}]`, `pointers:{"$sp": addr}`, 스택 `section`의 `base`·`depth`·`peak`가 붙는다. 데이터 구간은 `0x10010000`부터(그 아래에 값이 있으면 그 줄부터) 영역 끝까지, 스택 구간은 스택 영역 맨 위에서 아래로(높은 주소가 위) 지금 `$sp`·최고 수위·깊이 기준 가운데 가장 낮은 줄까지다. 0이 이어지는 줄들은 한 줄이고, 포인터가 가리키는 줄은 줄이지 않는다. 합친 Data Memory는 두 구간, 옛 구조(스택 영역 없는 Data Memory + Stack)는 부품마다 한 구간이다.

### edit(의도)

모두 `{fileId, circuitId, …}`를 받고 `{changed, outcome?, id?, circuitId?}`를 돌려준 뒤 `model.changed`를 보낸다. 의도 하나는 원조 `Project.doAction`이고 되돌리기 기록도 원조 그대로다(N-08, D-146): 대개 의도 하나가 한 단계지만, 붙여넣은(또는 복제한) 떠 있는 사본을 옮기거나 내려놓는 것은 붙여넣기 단계에 합쳐지고(원조 `shouldAppendTo`), 고른 것이 없을 때의 `edit.delete`도 빈 단계 하나를 남기며(`changed:true`), `edit.copy`도 기록에 한 단계로 든다(모델은 그대로). `changed`는 "모델이나 되돌리기 기록이 바뀌었다"이고, 모델이 바뀌었으면 `model.changed`가 온다. 바뀐 것이 없으면 `changed:false`.

**고른 것(편집 대상, N-08, D-146).** 엔진이 원조 창의 선택(`Canvas.getSelection()`, 떠 있는 사본까지)을 파일마다 들고 있다. `ids`를 받는 의도는 `ids`를 주면 원조 선택 도구처럼 떠 있는 것을 내려놓고(dropAll) 그것들을 고른 뒤 하고, 빼면 지금 고른 것에 한다. 다른 회로를 편집하면(`circuitId`가 지금 회로가 아니면) 원조 `Project.setCircuitState`처럼 떠 있는 것을 내려놓고 선택을 비운다.

| 메서드 | 더 받는 것 | 하는 일(Logisim 코드) |
| --- | --- | --- |
| `edit.addComponent` | `lib, name, loc, attrs?` | 부품 놓기(AddTool과 같은 동작). 떠 있는 것을 내려놓고, 놓은 부품을 고른다(원조: 놓은 뒤 Edit Tool, 그 부품 선택). result `id`: 놓은 부품 |
| `edit.addWire` | `points:[[x,y],…]`(2~3점, ㄱ자는 3점), `tool?:"wiring"\|"edit"` | 선 긋기(WiringTool과 같은 합치기·나누기·줄이기·포트 잇기). `wiring`(기본)은 Wiring 도구라 원조처럼 선택을 비우고, `edit`은 Edit 도구가 선 잇는 점에서 긋는 것이라 선택을 그대로 둔다 |
| `edit.select` | `at?:[x,y], toggle?` 또는 `ids?, rect?:[x0,y0,x1,y1], add?, toggle?, filter?:"components"\|"wires", all?` | 고르기. `at`: Edit 도구의 누름(원조 `SelectTool.mousePressed` 그대로), result `outcome`이 끌기의 뜻(`"moving"`·`"rect"`). `ids`(+`add` 더하기, +`toggle` Shift 뒤집기, `[]`는 비우기), `rect`(빈 곳에서 끈 사각형, `add`면 뒤집기), `filter`(Only Components / Only Wires), `all`(Ctrl+A). 아무것도 없으면 비우기. 모델은 바꾸지 않는다(`changed`는 떠 있는 것을 내려놓았을 때만) |
| `edit.move` | `ids?, dx, dy, connect?` | 고른 것을 옮기기와 따라오는 선(v1의 따라오는 선 규칙). 떠 있는 사본은 옮긴 자리에 내려앉는다(원조 translate). 옮긴 것은 고른 채로 남는다 |
| `edit.delete` | `ids?` | 지우기(떠 있는 사본은 그냥 사라진다). 고른 것이 없으면 빈 단계 |
| `edit.setAttr` | `ids?, attr, value` | 속성 바꾸기(선택 속성 표, 글자는 .circ에 저장되는 글자) |
| `edit.rotate` | `ids?, clockwise?`(기본 true) | v1 R·Shift+R: 방향이 있는 부품을 90도(되돌리기 한 단계 "Rotate") |
| `edit.keyConfig` | `key, alt?, chain?` + (`lib, name`: 놓기 도구에) | 숫자·Alt+숫자·Alt+방향 키(원조 KeyConfigurator: 게이트 입력 수, 비트 폭, Select Bits, 핀 라벨 자리…). `lib, name`이 없으면 고른 부품에(`SelectTool.processKeyEvent`, 한 단계), 있으면 그 놓기 도구에(`AddTool.processKeyEvent`, ToolAttributeAction). 도구에 방향 키(`ArrowUp` 등, Alt 없이)는 설정기가 받지 않으면 도구의 방향(`AddTool.setFacing`). `chain`: 앞 키의 설정기를 이어 쓴다(원조처럼 0.8초 안의 숫자를 여러 자리 수로, 시간은 화면이 잰다) |
| `edit.text` | `id?` 또는 `loc`, `text` | 글자 도구(`TextTool`): `id`면 그 부품의 글 칸(라벨, Label 글)을 원조 `TextEditable.getCommitAction`으로, 아니면 `loc`에 새 Label(글자 도구 속성으로, 빈 글이면 하지 않음). result `id`: 새 Label |
| `edit.undo`, `edit.redo` | `circuitId?`: 화면이 보던 회로(화면은 늘 보낸다) | Logisim 되돌리기 기록 그대로(다시 실행은 포크의 RedoStack). `circuitId`를 주면 먼저 그 회로를 편집하는 회로로 둔다(다른 회로의 선택은 원조처럼 그 회로에 내려놓고 비운다): 저널에 적혀 되살리기 재생이 저널에 없는 `sim.watch`·`record.view`와 상관없이 같다(D-146 12항). 없으면(의도 파일) 지금 회로 그대로 |
| `edit.setToolAttr` | `lib, name, attr, value` | 도구 속성 바꾸기(부품 목록에서 고른 도구의 속성 표, ToolAttributeAction). 도구에 남아 다음 놓기에 쓰이고 `<lib><tool>`에 저장된다. 이미 같은 값이면 `changed:false, outcome:"same"` |
| `edit.copy`, `edit.cut`, `edit.duplicate` | `ids?` | Edit 메뉴(LayoutEditHandler). 클립보드는 엔진 프로세스 안(열린 파일끼리 붙여넣기 됨, 시스템 클립보드 아님). 복제는 v1 SafeDuplicate: 사본이 옛 포트·선에 닿으면 나선으로 더 옮겨 내려놓는다(W-05) |
| `edit.paste` | — | Edit › Paste: 사본을 떠 있는 선택으로 둔다(다음 고르기·다른 회로·저장 때 내려놓는다) |
| `edit.duplicateN` | `ids?, count, direction:"right"\|"down"\|"left"\|"up", spacing?, number?` | N개 복제(v1 E-01) |
| `edit.align` | `ids, mode:"left"\|"centerX"\|"right"\|"top"\|"centerY"\|"bottom"` | 정렬(v1 E-01, 이어진 부품은 옮기지 않음) |
| `edit.distribute` | `ids, axis:"h"\|"v"` | 같은 간격(v1 E-01) |
| `edit.setCircuitAttr` | `attr, value` | 회로 속성(이름 `circuit`, 라벨 `clabel` 등). `circuitId`가 대상 회로 |
| `edit.createCircuit` | `name` | Project › Add Circuit(새 회로가 지금 회로가 된다). result `circuitId` |
| `edit.setMainCircuit` | — | `circuitId`를 주 회로로 |
| `edit.portOrder` | `order:{west\|east\|north\|south:[포트 이름\|번호]}, confirm?` | 서브회로 포트 순서로 모양 만들기(v1 P-04). `circuitId`가 대상 서브회로. **N-11에서 확정**(아래) |
| `edit.autoAppearance` | `confirm?` | Auto Appearance(v1 S-08). `circuitId`가 대상 서브회로. **N-11 확정** |
| `edit.importCircuits` | `path, circuits:[이름]` | 다른 .circ의 회로 가져오기(v1 P-05, 쓰는 회로 함께). **N-11 확정** |
| `edit.loadLibrary` | `kind:"builtin"\|"circ"\|"jar", name?, path?, className?` | Project › Load Library. **N-11 확정** |
| `edit.unloadLibrary` | `name` | Unload Library. **N-11 확정** |
| `edit.reloadLibrary` | `lib` | 저장된 라이브러리 새 버전 다시 읽기(되살리기 재생: 저널이 `file.libraryUpdated`를 이 의도로 적는다, 창은 부르지 못함). **N-11, D-153** |
| `edit.deleteCircuit` | — | Project › Remove Circuit(`circuitId`). **N-11** |
| `edit.moveCircuit` | `to` | 회로 차례(Move Circuit Up/Down, 부품 목록 끌기). **N-11** |
| `edit.appearance` | `op, …` | 모양 편집(Edit Circuit Appearance의 도구·Edit 메뉴, `circuitId`의 모양). **N-11** |
| `edit.tunnelColor` | `id, color?:"#rrggbb"` | 터널 색(v1 팔레트 12색, 없으면 Automatic). 그 회로의 같은 이름 터널 모두. hcs:ext(N-12, 아래) |
| `edit.signalGroup` | `wire, group?:"control"\|"data"\|"address"` | 신호 그룹(없으면 없음). hcs:ext(N-15, 아래) |
| `edit.areaMemo` | `at, ids?, text?, color?, bounds?:[x,y,w,h], delete?` | 영역 메모 더하기(고른 것을 감싼 상자에서 시작)·고치기·맞추기·지우기. hcs:ext(N-15, 아래) |
| `edit.splitterEdit` | `id, ranges, names?, lsbTop?` | Splitter 편집기 적용(원조 fanout·incoming·bitN + 팔 이름 hcs:ext, N-12, 아래) |
| `edit.splitterSplit` | `wire, at, ranges, names?, lsbTop?` | 여러 비트 선에 새 스플리터(Split Bits…, Take One Bit, N-12, 아래). result `id`: 새 스플리터 |
| `edit.labels` | `ids, labels:[글]` | Edit Labels of N Components…(v1 `LabelsDialog`): `ids`와 같은 차례의 라벨, 원조 `SetAttributeAction` 한 단계(N-10, 아래) |
| `edit.attach` | `id, port, what:"pin"\|"constant"\|"probe"\|"tunnel"` | Attach to <포트> ▸(v1 `CircuitEdits.attach`, 검사기 W-05) |
| `edit.swapGate` | `id, to` | Change Gate To ▸(v1 `CircuitEdits.swapGate`: AND·OR·NAND·NOR·XOR·XNOR, 입력 자리 그대로, 검사기) |
| `edit.deleteNet` | `wire` | Delete Net Wires(v1 W-04) |
| `edit.wireToTunnels` | `wire, label` | Replace Wire with Tunnels…(v1, 검사기) |
| `edit.probe` | `wire, at, radix?` | Attach Probe ▸ 진법, P 키(`radix` 없음: 1비트 2진, 여러 비트 16진; v1 `QuickProbe`, 검사기). result `id`: 새 프로브 |
| `edit.deleteProbes` | — | Delete All Probes (n)(v1 `ProbeMenu`: 프로브와 함께 놓인 막다른 짧은 선도) |
| `edit.combineBus` | `ids`(선, 고른 차례) | Combine N Wires into One Bus(v1 `SplitterMenu.combine`: 먼저 고른 선이 위 팔, 새 스플리터는 오른쪽 +60에 서쪽, 잇지 않음). result `id`: 새 스플리터 |
| `edit.originalItem` | `id, index` | 원조 부품 메뉴의 항목을 원조 코드로(Splitter의 Distribute Ascending·Descending = 원조 `SplitterDistributeItem`) |
| `edit.memContents` | `id, addr?, values?, clear?, file?` | ROM 내용(Contents 속성): 값 쓰기, 비우기, 이미지 읽기. 원조 `RomContentsListener`의 "Edit ROM Contents" 동작(붙은 곳은 한 단계로 합침) |

- `edit.addComponent`: `lib:null`(또는 빼면)이면 이 파일의 회로를 이름(`name`)으로 놓는다. `attrs`는 놓는 부품에만 쓴다(도구의 기본값은 바꾸지 않는다). `loc`은 그대로 쓴다(격자 맞추기는 화면 몫). 오류: 없는 도구 1, `circular`·`exclusive`·`negativeCoord` 3, 모르는 속성·틀린 값 -32602.
- `edit.addWire`: 2점은 가로·세로 곧은 선(3점이면 가운데 점이 그 선 위), ㄱ자는 `[시작, 꺾는 점, 끝]`이고 꺾는 점이 `[끝x, 시작y]`(가로 먼저) 또는 `[시작x, 끝y]`(세로 먼저)여야 한다. 원조처럼 한쪽 끝이 있는 선을 따라 되돌아 끌면 그 선을 줄이거나 지운다(`outcome:"shortened"|"removed"`; 따로 `edit.shortenWire`를 두지 않는다: 원조 WiringTool도 떼는 순간 같은 두 점으로 판단한다). 시작과 끝이 같으면 `changed:false, outcome:"empty"`.
- `edit.move`: `connect`(기본 true)면 원조 연결 유지 계산 뒤 v1 SafeMove(D-055)의 기준으로 남긴다. `outcome`: `"moved"`, `"movedWithoutWires"`(선을 잇지 못하고 옮김), `"refused"`(`changed:false`, 다른 넷이 바뀌므로 옮기지 않음), `"empty"`(고른 것이 없음). 원조처럼 선택 경계가 0 밑으로 가지 않게 자르고, 격자에 붙는 부품이 있으면 10에 맞춘다. 다른 출력과 한 점에 겹치면 오류 3 `exclusive`. 선 하나만 옮기면 v1 선분 끌기(양쪽 다리가 늘고 준다)다.
- `edit.setAttr`: 선은 건너뛴다. 모든 부품에 그 속성이 있어야 한다(없으면 -32602). 속성은 부품 객체 안에서 바뀌므로 같은 id가 `added`로 온다. 값은 원조 `Attribute.parse`로 읽고(원조 속성 표와 같은 규칙), 읽지 못하면 -32602에 `data:{reason:"badValue", attr, value}`(N-10: 화면이 한국어 문장을 짓는다; `setToolAttr`·`setCircuitAttr`·`labels`도 같다). `keepSelection:true`와 `ids`면 원조 `AttrTableComponentModel`처럼 그 부품들만 바꾸고 선택은 그대로다(우클릭 메뉴의 한 부품 항목, v1 `EditMenus`; 동작 이름 "Change Attribute").
- 편집하면 그 회로가 시뮬레이션의 지금 회로가 된다(Swing에서 보고 있는 회로를 편집하는 것과 같다). 다른 회로를 보고 있었다면 화면이 `sim.watch`를 다시 보낸다.

`edit.selection = {fileId, circuitId, ids, floating}`(N-08, D-146): 편집 의도 뒤, 선택이 앞에 알린 것과 다르면 보낸다(`edit.select` 뒤에는 늘 보낸다: 화면이 누른 즉시 그린 짐작을 바로잡는다). `circuitId`는 선택이 생긴 회로다(시뮬레이션이 다른 회로를 보고 있어도). `ids`는 회로에 있는 고른 부품·선(번호 차례), `floating`은 떠 있는 붙여넣기·복제 사본의 모습(Component·Wire JSON: 회로에 없어 `model.changed`에 오지 않는다; id는 엔진 id라 내려앉으면 같은 id로 `added`에 온다). `model.changed` 뒤에 온다(새로 놓인 부품의 id를 화면이 먼저 안다).

모델을 바꾸지 않는 물음(화면의 도구가 끄는 동안 그리는 것, 되살리기 저널에 적지 않는다):

| 메서드 | 받는 것 | 답 |
| --- | --- | --- |
| `model.tool` | `fileId, lib, name, loc?, attrs?` | `{component}`: 그 놓기 도구가 지금 속성(과 `attrs`)으로 `loc`에 놓을 부품의 모습(id `"ghost"`). 원조 AddTool이 끄는 동안 그리는 유령 |
| `model.movePreview` | `fileId, circuitId, dx, dy, connect?` | `{dx, dy, added:[[a,b]], removed:[id], unconnected:[[x,y]]}`: 고른 것을 그만큼 끌 때 원조 `MoveGesture`가 더할 선·뺄 선·잇지 못한 점(`SelectTool.handleMoveDrag`), `dx`·`dy`는 `edit.move`와 같이 자르고 맞춘 값 |
| `model.attributes` | `fileId, circuitId, ids?, circuit?` 또는 `fileId, lib, name` | 속성 표(N-10, 아래 `AttrTable`): 고른 것(`ids`, 없으면 엔진의 선택; 없으면 회로), `circuit:true`면 회로, `name`이면 든 도구 |
| `model.menu` | `fileId, circuitId, at, id?` | 우클릭 메뉴의 사실(N-10, 아래 `MenuFacts`). `id`는 화면이 누른 곳에서 찾은 부품·선(없으면 엔진이 찾는다) |
| `model.toolbar` | `fileId` | `[{name, lib, attrs?} \| {name, tool}]`(N-17, D-158, I-112): 파일의 `<toolbar>`(원조 Project › Options › Toolbar, 기본 틀: Poke·Edit·Text Tool, 입력 핀, 출력 핀, NOT·AND·OR Gate) 차례, 구분선은 뺌. 놓는 도구는 `lib`(파일의 회로면 null)·`name`과 라이브러리 도구와 다른 속성만 `attrs`(원조 문자열: `model.tool`·`edit.addComponent`의 `attrs`로 그대로), 기본 도구는 `tool`(`Poke Tool` …). 창의 Ctrl+2…9가 둘째…아홉째를 든다(Ctrl+1은 v1대로 100 %). 모델은 바꾸지 않는다 |
| `model.textAt` | `fileId, circuitId, loc` | 글자 도구가 `loc`을 누르면 원조 `TextTool.mousePressed`가 여는 칸: 고른 것 먼저, 그다음 회로 전체에서 그 점을 포함하고 칸을 주는 부품(라벨이 비었으면 몸체 어디든, 있으면 라벨 위) → `{id, text, box:[x,y,w,h]}`; 없으면 새 Label → `{id:null, text:"", box}`; 음수 자리면 `{id:null, none:true}` |

**속성 표와 우클릭 메뉴(N-10, D-157).**

```
AttrTable = {target:"selection"|"circuit"|"tool", circuitId?, lib?, name?, title, editable, rows:[AttrRow], quick?}
AttrRow = {attr, display, value, text, type, readOnly, mixed, options?:[{value, display}], radix?, min?, max?, styles?, families?}
QuickFacts = {attrs:[attr], hints:[{keys, attr, display}], rotate, label, count, autoAppearance?}
MenuFacts = {circuitId, editable, kind:"part"|"wire"|"empty"|"many", summary, id?, selection:{ids, ordered, parts, wires},
             part?, wire?:{width, net}, common?:{facing, width, label, labels}, probes?:[id], combine?:[width]}
```

- `model.attributes`: 원조 속성 표가 보이는 것 그대로다. 고른 것은 원조 `AttrTableSelectionModel`·`SelectionAttributes`의 규칙(선이 아닌 것이 있으면 선은 빼고, 모두가 가진 속성만 첫 부품의 차례로, 값이 다르면 `value:null, mixed:true`), 고른 것이 없으면 회로 속성(`AttrTableCircuitModel`: `circuit`·`clabel`·`clabelup`·`clabelfont`), 도구는 `AttrTableToolModel`. `title`은 원조 글(`Selection: AND Gate × 2`, `Selection: Various items × 3`, `Circuit: main`, `Tool: Pin`). `value`는 .circ 글자(`toStandardString`), `text`는 원조 표의 글(`toDisplayString`). `type`은 편집기: `option`(원조 편집기가 목록 상자: 비트 폭, 방향, Yes/No, 부품이 정한 선택지. 목록의 항목이 값이 아니라 보기 객체인 속성은 원조 표처럼 차례가 값: Splitter `bitN`), `number`(`radix` 16이면 원조가 16진으로 쓰는 값, `min`·`max`는 원조 범위), `text`, `font`(`styles`·`families`), `color`, `contents`(ROM 내용: 표에 싣지 않고 16진 편집기가 `mem.read`로 읽는다). `editable`은 파일을 쓸 수 있고 이 파일의 회로일 때. `quick`은 고른 선 아닌 부품이 모두 한 종류일 때의 빠른 속성 창(v1 `QuickAttrs`: 부품 종류 등록표의 차례로 최대 5개, 원조 숨은 숫자 키를 키 설정기에 넣어 보고 알아낸 `hints`, R·F2, 기본 모양 서브회로의 `autoAppearance` 회로). 모델을 바꾸지 않는다.
- `model.menu`: v1 우클릭 메뉴가 기대던 사실만 v1 코드로 답한다(항목은 화면의 메뉴 등록표 `electron/src/renderer/app/menus/`가 정한다). `summary`는 v1 `MenuLayout.summary`(단수·복수, S-25: `AND #1 · 2 inputs · 1 bit`, `PC(Register) · 32 bits`, `Net pc · 32 bits`, `2 components`, `Empty spot · main`), `kind`는 v1 규칙(선 아닌 것 둘 이상을 골랐고 누른 곳이 그 안이거나 빈 곳이면 `many`). `selection.ids`는 고른 차례(v1 `SelectionOrder`: 편집 의도 하나가 한 입력, 한 번에 여럿이 들어오면 `ordered:false`). `part`는 포트(5px 안: `port = {i, name, dir, width, negate?, negated?}`), 게이트(`gate`, `swaps`), 원조 선택지(`options.size`·`options.pull`, 지금 것 `checked`), 핀(`pin`), 라벨 있는 터널(`tunnel.same`: 같은 이름, 위→아래), 서브회로(`subcircuit = {circuitId, name, library?, defaultAppearance, registerFile}`), Register·Counter의 `pcMarked`, 메모리(`memory: "ram"|"rom"|"program"`, 프로그램 메모리의 `source`), 원조 부품 메뉴의 항목(`original = [{i, text, enabled}]`: 창 없이 원조 코드로 할 수 있는 Splitter Distribute). 빈 곳이면 `probes`, 고른 것이 모두 선이고 둘 이상이면 `combine`(고른 차례의 폭). 모델을 바꾸지 않는다.
- 우클릭 메뉴의 편집 의도(위 표)는 모두 v1 항목의 동작을 `Project.doAction` 한 번에 넘긴다: 되돌리기 한 단계, 원조 부품·속성만 바뀐다. 새 부품·선을 자동으로 두는 것(`attach`·`swapGate`·`wireToTunnels`·`probe`·`combineBus`)은 v1 검사기(W-05)를 거치고, 막히면 `changed:false, outcome:"refused"`. `probe`는 자리가 없으면 `outcome:"noRoom"`, `combineBus`는 합이 32비트를 넘으면 `outcome:"tooWide"`, `originalItem`은 꺼진 항목이면 `outcome:"disabled"`, 같은 값이면 `outcome:"same"`. 되살리기 저널(7절)에 저절로 적히고 `ids`·`id`·`wire`는 부품으로 적는다(`labels`가 `ids`와 같은 차례의 목록인 까닭).

**RAM·ROM 내용(N-10, D-157, 원조 `MemMenu`).**

| 메서드 | params | result |
| --- | --- | --- |
| `mem.read` | `{fileId, circuitId, path?, componentId, from?, count?}` | `{kind:"ram"\|"rom", addrBits, dataBits, from, total, words:[부호 없는 수]}`(`count` 1~4096, 기본 256) |
| `mem.write` | `{fileId, circuitId, path?, componentId, addr, values}` | `{changed}`: RAM(시뮬레이션 상태) |
| `mem.clear` | `{fileId, circuitId, path?, componentId}` | `{changed}`: RAM Clear Contents |
| `mem.loadImage`, `mem.saveImage` | `{…, componentId, file}` | 원조 `HexFile.open`·`save`(창은 부르지 못한다: main의 고르기 창이 부른다) |

- RAM의 내용은 원조처럼 시뮬레이션 상태다(원조 HexFrame이 RAM 상태를 고치는 것과 같다): .circ에 남지 않고 되돌리기에 들지 않으며 되살리기 저널에 적지 않는다. `circuitId`·`path`는 보이는 상태(`sim.watch`와 같다). 내용 객체는 원조 상태 객체에서 읽기만 해서 얻는다. 아직 전파가 닿지 않은 RAM은 오류 4 `notReady`. 폭을 넘는 값은 -32602 `badValue`.
- ROM의 내용은 Contents 속성(.circ에 저장)이라 편집 의도 `edit.memContents`다(원조 메뉴처럼 원조 `RomContentsListener`를 달아 고칠 때마다 원조 "Edit ROM Contents" 동작이 되돌리기에 든다). `mem.read`·`mem.saveImage`는 둘 다 읽는다.

**편집 동등성(N-01, D-136, `tests/parity/`)과 N-09(D-146에서 풂).** 의도 파일은 `edit.move`·`edit.delete`·`edit.copy` 등에서 `ids`를 빼고 "앞 `edit.select`로 고른 것"을 대상으로 적는다. 엔진이 원조 선택을 들고 있으므로(위) 그대로 보내면 된다. 재생기(`EngineParityReplayTest`)가 채우는 것은 회로 이름 → `circuitId`(`circuit`은 그 의도의 회로, `target`은 회로 전체에 하는 의도의 대상), `fileId`, 기호·`label:`·`at:`·`wire:` → 엔진 id(`ids`·`id`·`wire`; 기호는 원조 `ReplacementMap`을 따라간다), 라이브러리의 보이는 이름(`Hallym MIPS`) → 엔진 이름이다. 상대 경로(`tests/parity` 기준, `file.open`·`edit.importCircuits`·`edit.loadLibrary`)는 재생기가 작업 폴더의 절대 경로로 바꾼다(`file.open`은 지금 재생기가 쓴다; 나머지 둘은 N-11 의도와 함께). 18장면 가운데 N-11 의도(Port Order, Auto Appearance, Import, 라이브러리 싣기)를 쓰는 08·09·11을 뺀 15장면을 엔진이 재생해 Swing 골든과 같은 .circ를 저장한다(D-006 정규화).

- 의도 파일은 `edit.addComponent`에 `attrs`를 쓰지 않는다. Swing에서 값을 바꿔 놓는 길은 부품 목록에서 도구를 고르고 속성 표에서 **도구 속성**을 바꾼 뒤(`edit.setToolAttr`, 원조 ToolAttributeAction: 되돌리기 한 단계, 도구에 남아 다음 놓기에도 쓰이고 `<lib><tool>`에 저장된다) 누르는 것이다. 놓는 부품에만 속성을 주는 한 번의 동작(위 `attrs`)은 Swing에 없고, 검색 창의 "and 3"(N-12)이 쓴다.

**터널 색과 Splitter 편집기(N-12, D-150, 확정).** 셋 다 v1의 동작 객체를 그대로 쓰고(`TunnelColorStore.action`, `SplitterEdits.change·create·withNames`, 검사기 `WireGuard`), `Project.doAction` 한 번 = 되돌리기 한 단계다. 원조 부품 속성은 스플리터의 fanout·incoming·bitN만 바뀌고, 학생이 정한 것은 hcs:ext에만 간다(D-024). 되돌리면 그 회로의 hcs:ext 항목이 **차례까지** 전과 같다(v1은 지우고 끝에 다시 넣어 차례가 바뀌었다: 편집하고 되돌린 파일도 저장 결과가 원래와 같게, `OpenSaveParityTest.extEditsUndoneSaveTheOriginal`).
- `edit.tunnelColor {id, color?}`: `id`는 라벨이 있는 터널(아니면 -32602). `color`는 v1 팔레트 12색 가운데 하나(`#e69f00` 꼴, 대소문자 무관; 다른 색은 -32602), 없으면 Automatic(항목을 지운다). 그 회로에서 그 이름을 가진 모든 터널의 색이다(`model.changed`에 그 터널들이 `ext.color`와 함께 온다, 저장은 `#E69F00` 대문자). 이미 그 색이면 `changed:false, outcome:"same"`.
- `edit.splitterEdit {id, ranges, names?, lsbTop?}`: 편집기의 Apply. `ranges`는 편집기 범위 글(v1 `SplitterSpec.parse`: `31:26, 25:21, 20:16, 15:0`, 범위 뒤 이름 `31:26 op`, `4x8`, `32x1`)이고 폭은 그 스플리터의 incoming이다. `lsbTop`(기본 false)은 "LSB on top"(위 팔이 낮은 비트). 팔 이름은 편집기 칸처럼 정한다: 처음 칸은 지금 이름, 범위 글을 다시 읽을 때 글에 이름이 없는 팔은 팔 수가 같으면 전의 칸 이름을 지키고, 끝으로 `names`가 앞 칸부터 채운다(편집 동등성 골든 14가 이 차례로 만들어졌다). 읽을 수 없는 글·폭 밖 비트·한 비트가 두 팔·`names`가 팔보다 많음은 -32602. 원조 속성도 이름도 그대로면 `changed:false, outcome:"same"`. 팔 자리가 바뀌어 새 팔 끝이 다른 연결에 닿으면 검사기가 막고 `changed:false, outcome:"refused"`(v1 "그렇게 두면 선이나 포트가 다른 연결에 닿아…"). 이름만 바뀌면 부품은 같은 id로 `ext.arms`만 바뀐다.
- `edit.splitterSplit {wire, at, ranges, names?, lsbTop?}`: 여러 비트 선(`wire`, 폭은 원조가 계산한 그 선의 폭) 위, `at`에서 가장 가까운 선 위 격자점에 동쪽을 보는 새 스플리터(v1 Split Bits…). 편집기는 32비트면 MIPS R 형식(`31:26 op, …, 5:0 funct`), 아니면 반씩으로 열린 것으로 보고 `ranges`·`names`·`lsbTop`을 위처럼 읽는다. 비트 하나 뽑기(Take One Bit [n])는 `ranges`가 그 비트 하나(`"5"`)다. 1비트 선·선이 아닌 id는 -32602. 검사기가 막으면 `outcome:"refused"`. result `id`는 새 스플리터.
- 되살리기 저널(7절): 모두 `edit.*`라 저절로 적히고, `id`·`wire`는 부품 자체(ref)로 바꿔 적는다.
- `ids`의 부품은 엔진 id다. 의도 파일은 id 대신 기호(앞 `edit.addComponent`의 `as`, result `id`로 바꾼다)·`label:`·`at:x,y`·`wire:x,y`로 적는다(형식은 `tests/parity/README.md`).
- `view.zoom`(`{factor}`)은 화면 배율이라 엔진 메서드가 아니다(의도 파일에만 있고 엔진은 아무것도 하지 않는다).

**신호 그룹·영역 메모(N-15, D-151).** 둘 다 v1 우클릭 항목의 동작(`SignalGroups.action`, `AreaMemos.action`)을 `Project.doAction`에 한 번 넘긴다: 되돌리기 한 단계이고 .circ 확장 정보(hcs:ext)만 바뀌며 원조 회로 부분은 바이트까지 그대로다. 편집 동등성 골든(tests/parity 13·16·18)의 Swing 경로와 같은 코드다.

- `edit.signalGroup {wire, group?}`: 선 `wire`의 넷에 그룹을 정한다(`group`이 없으면 None: 학생이 정한 그룹을 뗀다). 넷은 이름(터널·핀 라벨), 없으면 가장 작은 자리로 저장된다(v1). 이미 같으면 `changed:false, outcome:"same"`(Swing은 같은 그룹도 한 단계로 적는다: 차이는 되돌리기 기록뿐이고 저장 결과는 같다). 선이 아니면 -32602, 모르는 그룹 -32602.
- `edit.areaMemo {at, ids?, text?, color?, bounds?, delete?}`: `at`을 감싸는 메모(겹치면 가장 작은 것)가 있으면 그 메모를 **고친다**: 준 것만 바뀐다(`text` 앞뒤 공백 뺌, `color` 0~11, `bounds` [x,y,w,h] 폭·높이 20 이상), `bounds` 없이 `ids`를 주면 그 부품·선 둘레로 맞춘다(Fit Area Memo to Selection), 같으면 `outcome:"same"`. `delete:true`면 그 메모를 지운다(없으면 `outcome:"noMemo"`). 감싸는 메모가 없으면 **더한다**(Add Area Memo…): `ids`의 둘레(여백 20, 격자에 맞춤), 없으면 `at`에 200×120, 색은 주지 않으면 메모 수로 돌아가며. result `outcome`: `"added"`·`"edited"`·`"deleted"`. 되돌린 지우기는 메모를 목록 끝에 다시 붙인다(v1, 그리기 차례만 다르다).
- 두 의도 뒤에는 부품이 바뀌지 않아도 `model.changed`가 온다(`removed`·`added`는 비고 `groups`·`memos`가 새것). 되살리기 저널(7절)에 그대로 적힌다: `wire`·`ids`는 부품으로, `at`·`bounds`는 자리다.

**회로·모양·라이브러리·다른 파일(N-11, D-153, 확정).** 모두 원조 동작 객체(`LogisimFileActions`, 원조 모양 편집기의 `ModelAction`, v1 `AutoAppearance`·`CircuitImport`)를 `Project.doAction`에 한 번 넘긴다: 되돌리기 한 단계. 원조가 오류 창으로 거절하던 것은 오류 3(`data.reason`)이다. 경로(`path`)는 절대 경로이거나 이 파일(.circ)의 폴더 기준 상대 경로다(편집 동등성 의도 파일의 `inputs/adders.circ`·`hcs-mips.jar`가 그대로 간다). 화면은 경로를 보내지 않는다: main 프로세스가 고르기 창에서 얻어 보낸다.

- `edit.createCircuit {name}`(N-08): 원조 이름 검사(빈 이름 `nameMissing`, 있는 이름 `nameTaken`, -32602). result `circuitId`. `edit.setMainCircuit`(같으면 `outcome:"same"`), `edit.setCircuitAttr {attr:"circuit", value}`(이름 바꾸기, 인스턴스가 따라간다).
- `edit.deleteCircuit`: 원조 `doRemoveCircuit`. 마지막 회로 `lastCircuit`, 다른 회로가 쓰는 회로 `inUse`(오류 3). 보던 회로를 지우면 원조 Project가 주 회로로 돌아간다.
- `edit.moveCircuit {to}`: 옮긴 뒤 자리(0부터). 같은 자리 `outcome:"same"`, 밖이면 -32602.
- `edit.portOrder {order, confirm?}`·`edit.autoAppearance {confirm?}`: v1 `PortOrderDialog.apply`·`AutoAppearance.run`. `order`의 변은 `west east north south`, 목록 항목은 핀 이름(라벨; 같은 이름이 여럿이면 지금 차례로 하나씩) 또는 지금 차례의 번호이고, 준 변은 그 변의 포트를 모두 한 번씩 든다(아니면 -32602). 이 회로를 쓰는 인스턴스에서 이어진 포트가 움직이면 `confirm:false`일 때 바꾸지 않고 `{changed:false, outcome:"needsConfirm", impact:{instances, connections, where:[글]}}`(v1 확인 창, `where`는 `main › half #1`, 화면은 8곳까지 보인다). `confirm`은 기본 참(Swing Apply). 바꾸면 result에도 `impact`가 있을 수 있다. 핀이 없으면 `outcome:"noPorts"`.
- `edit.importCircuits {path, circuits}`: v1 Import Subcircuits…의 Apply(딸린 서브회로 먼저, 이미 있는 이름은 `이름-2`, 이 파일에 없는 라이브러리 부품은 빠짐). result `plan`(아래 `model.importPlan`과 같다). 같은 파일 `sameFile`(3), 읽지 못함 2, 없는 회로 -32602. v1은 원조 `new Loader`로 열어 그 파일의 Wiring 도구 일곱 개 설정(`<lib desc="#Wiring"><tool>`)이 모든 열린 파일의 도구에 남았다(골든 09). 엔진은 파일마다 제 도구라(D-149) 그 설정을 **이 파일의** Wiring 도구에만 옮긴다.
- `edit.loadLibrary {kind, name?, path?, className?}`: `builtin`은 이 파일에 없는 기본 라이브러리 `name`(없으면 -32602), `circ`은 원조 `loadLogisimLibrary`(자기 자신 `self`, 엔진이 연 그 파일이 이미 이 파일을 쓰면 `circular`, 없는 파일 2, 이미 있으면 `outcome:"already"`), `jar`는 manifest의 `Library-Class`(없으면 `className`, 그것도 없으면 -32602 `noLibraryClass`). result `lib`: 넣은 라이브러리 이름(부품 목록의 `lib`). 다시 넣은 기본 라이브러리는 원조처럼 목록 끝에 붙는다(골든 11).
- `edit.unloadLibrary {name}`(라이브러리 이름 또는 보이는 이름): 원조 `getUnloadLibraryMessage`로 거절하면 오류 3, `reason`은 `inUse`(`data.circuit`: 그 부품을 쓰는 첫 회로) 또는 `toolbar`(원조 도구 모음·마우스 설정이 그 도구를 씀). `message`는 원조 영어 글이다(화면은 까닭으로 문장을 짓는다).
- `edit.appearance {op, …}`: 원조 모양 편집기(Project › Edit Circuit Appearance). 엔진은 회로마다 화면에 붙지 않은 원조 `AppearanceView`(Canvas와 Edit 메뉴 처리기)를 두고, 원조 도구가 마우스를 뗄 때 넘기는 동작을 같은 `AppearanceCanvas.doAction`으로 보낸다: 포트·기준점은 늘 맨 위이고 인스턴스가 있는 회로는 원조 거래 안에서 바뀐다. 편집하면 그 회로가 지금 회로가 된다(Swing과 같다). 도형은 **아래부터의 번호**(0부터, `model.appearance`의 `i`)로 가리키고, 고른 것은 화면의 몫이다. result `selected`: 원조 편집기가 그 동작 뒤에 고른 도형 번호들.

  | op | 더 받는 것 | 원조 |
  | --- | --- | --- |
  | `add` | `shape:{kind, bounds?:[x,y,w,h], points?:[[x,y]…], at?, text?}, attrs?` | 그리기 도구(`rect roundrect oval`: bounds, `line`: 두 점, `polyline polygon`: 두 점 이상, `curve`: [끝, 끝, 조절점], `text`: at·text). 도구 속성(`attrs`, 원조 DrawingAttributeSet 이름: `stroke-width stroke fill paintType rx font align`)을 입힌다. 원조 PolyTool은 입히지 않아 다각선·다각형은 기본 속성이다(원조 그대로). 빈 새 글은 `outcome:"empty"`. result `index` |
  | `move` | `shapes, dx, dy` | 고르기 도구 끌기(ModelTranslateAction). 포트·기준점도 옮긴다 |
  | `handle` | `shape, at, dx, dy, shift?, ctrl?, alt?` | 손잡이 끌기(HandleGesture, ModelMoveHandleAction). 그 자리에 움직이는 손잡이가 없으면 -32602. result `handle?`: 그 뒤 고른 꼭짓점 |
  | `delete` `cut` `copy` `paste` `duplicate` | `shapes` | Edit 메뉴(원조 AppearanceEditHandler. Copy도 원조처럼 되돌리기 한 단계, 클립보드는 앱 하나에 하나). 지울 수 없는 포트·기준점은 남기고 고른 채 둔다. 빈 클립보드 `outcome:"emptyClipboard"` |
  | `raise` `lower` `raiseTop` `lowerBottom` | `shapes` | Edit › Raise …(포트 위로는 가지 않는다) |
  | `addVertex` `removeVertex` | `shape, at` | Edit › Add/Remove Vertex(그 자리의 꼭짓점, 없으면 `outcome:"noVertex"`) |
  | `setAttr` | `shapes, attr, value` | 속성 표(ModelChangeAttributeAction). 맞춤 `align`은 `left center right`, 기준점의 `facing`도. 같으면 `outcome:"same"` |
  | `text` | `shape, text` | 글자 도구로 고치기. 빈 글이면 지운다 |
  | `revert` | — | Project › Revert To Default Appearance. 원조 동작은 인스턴스가 있으면 거래 밖에서 포트를 바꿔 멈추므로(원조 결함) 쓰는 회로를 잠근 거래 안에서 부른다. 이미 기본이면 `outcome:"same"` |

  모양이 바뀌면 그 인스턴스들이 `model.changed`의 `added`로, 연 모양이 `model.appearance` 알림으로 온다.

물음(모델을 바꾸지 않는다):

| 메서드 | params | result |
| --- | --- | --- |
| `model.ports` | `{fileId, circuitId}` | `{circuitId, name, default, sides:{west,east,north,south:[{name, width, input}]}, instances}`: Port Order 창(v1 `AutoAppearance.sides`: 핀이 보는 방향의 반대 변, 지금 모양의 차례) |
| `model.instances` | `{fileId, circuitId}` | `{circuitId, main, mainName, paths:[{ids, names, circuits, text}], instances, connected, default}`: 주 회로에서 이 회로까지의 인스턴스 경로(v1 `InstancePaths`, 위치 차례, 64개까지; `text` = `main › cpu › alu`), 쓰는 인스턴스 수, 이어진 포트 수(인스턴스 안내 띠, 핀 도구 미리 보기) |
| `model.pinImpact` | `{fileId, circuitId, ids}` | `{connections, instances}`: 이 핀들을 지우거나 옮기면 끊길 수 있는 연결(v1 `InstanceBanner.previewText`) |
| `model.appearance` | `{fileId, circuitId}` | 아래 `AppearanceEdit`. 부른 뒤로 그 회로의 모양이 바뀌면 같은 모양의 알림 `model.appearance`가 온다(파일을 닫을 때까지) |
| `model.appearanceHit` | `{fileId, circuitId, at?, selected?, zoom?, rect?:[x0,y0,x1,y1]}` | `{handle?:{shape, at}, clicked?, top, topFilled, removable?, insertable?, inRect?}`: 원조 고르기 도구가 누른 자리에서 묻는 것(고른 도형의 움직이는 손잡이(배율에 따른 크기), 채움 없이·채운 것으로 본 맨 위 도형, 지울·더할 꼭짓점, 사각형 안 도형) |
| `model.appearanceHandles` | `{fileId, circuitId, shape, at, dx, dy, shift?, ctrl?, alt?}` | `{handles:[[x,y]]}`: 손잡이를 끄는 동안의 모습(원조 `getHandles(gesture)`) |
| `model.appearanceMenu` | `{fileId, circuitId, shapes, vertexShape?, vertexAt?}` | `{cut, copy, paste, delete, duplicate, raise, lower, raiseTop, lowerBottom, addVertex, removeVertex}`: 원조 Edit 메뉴의 켜짐(`computeEnabled`) |
| `model.libraries` | `{fileId}` | `{builtins:[{name, display}], loaded:[{name, display, usedIn}], openFiles:[{fileId, name, state, lib?, circuits, main}], mips}`: Load/Unload Library 창과 탭 간 라이브러리. `state`: `ok`, `loaded`(이미 라이브러리), `unsaved`, `self`, `circular`. `name`: 그 파일의 이름(자기 창에 있는 파일도 이름으로 보인다) |
| `model.importPlan` | `{fileId, path, circuits}` | `{order:[{name, as}], skipped:[글]}`: 가져오기 계획(v1 계획 창) |
| `file.info` | `{fileId}` | `{fileId, name, circuits, main, libraries, dirty, saved, readOnly}`: 열린 파일 하나의 지금 모습(`file.changed`와 같은 꼴과 저장한 적 있는지·읽기 전용인지). 창을 나눠 다른 창이 그 파일을 받을 때 쓴다(N-11) |
| `file.peek` | `{fileId, path}` | `{name, main, circuits:[{name, uses:[이름]}]}`: 가져오기 창의 목록 |
| `file.saveImpact` | `{fileId}` | `{cuts:[{fileId, file, instances:[이름], connections}]}`: 지금 내용으로 저장하면 이 파일을 라이브러리로 쓰는 다른 열린 파일에서 끊길 연결(v1 `LibrarySync.impact`, D-065. 원조 기본 모양은 핀을 지우면 남은 포트가 당겨져 옆 선에 조용히 붙는다) |
| `file.originOf` | `{fileId, circuitId}` | `{path, circuit}`: 라이브러리 회로의 파일(v1 Edit Original File). 이 파일의 회로면 `path:null` |
| `file.copyMipsJar` | `{fileId}` | `{name}`: 번들 `hcs-mips.jar`를 저장한 .circ 옆에 복사한다(v1 [Copy hcs-mips.jar Here], D-096. 누르기 전에는 복사하지 않는다). 저장한 적 없으면 -32602 |

```
AppearanceEdit = {fileId, circuitId, name, default, editable,
  shapes:[{i, kind, svg:{tag, attrs, text?}, attrs:{이름: 글자}, handles:[[x,y]], moves:[bool], bounds:[x,y,w,h],
           removable, points?, closed?, text?, at?, port?:{input, pin:[x,y], name, width, at}, facing?}]}
```

`kind`: `rect roundrect oval line polyline polygon curve text port anchor`. `svg`는 원조 `<appear>`에 적히는 요소 그대로(인스턴스의 `appearance.shapes`와 같은 꼴), `attrs`는 원조 속성 표의 값(맞춤은 `left center right`), `handles`·`moves`는 원조 손잡이와 움직일 수 있는지.

알림:

- `file.changed = {fileId, name, circuits:[CircuitRef], main, libraries:[LibRef], dirty}`: 편집·되돌리기 뒤 회로 목록(차례·이름), 주 회로, 라이브러리가 바뀌었을 때만.
- `model.portImpact = {fileId, circuitId, name, broken, kept}`: 주 회로가 아닌 회로를 보며 한 편집이 그 인스턴스의 이어진 포트를 끊었다(v1 P-02, D-064: 핀을 지움, 포트가 떨어지거나 옆 선에 붙음). 옛 자리에 선 끝이 남고 새 자리가 비었으면 v1처럼 선을 이어 되살린다(`kept`, 부모 회로마다 따로 되돌리는 한 단계 "Keep Instance Connections", 검사기 WireGuard). 되돌리기·다시 실행에는 하지 않는다(v1과 같다).
- `file.libraryUpdated = {fileId, library, lib}`: 이 파일이 라이브러리로 쓰는 `library`(파일 이름, 이 파일에서의 라이브러리 이름 `lib`)가 다른 탭에서 저장되어, 원조 `Loader.reload`로 새 버전을 받고 그 인스턴스를 새 부품으로 바꿨다(원조 `LoadedLibrary`가 창의 프로젝트에 하는 것과 같은 CircuitMutation, 되돌리기 기록 밖). 파일마다 그 파일의 시뮬레이터를 세운 채 한다(`SimSession.quiet`). 시뮬레이션은 처음으로 돌아간다(v1 D-065). 앞에 그 파일의 `model.changed`가 온다. main의 되살리기 저널은 이 알림을 그 파일의 `edit.reloadLibrary {fileId, lib}`로 적는다(D-153).
- `edit.reloadLibrary {lib}`(되살리기 재생용, 창은 부르지 못함): 이 파일의 Logisim 라이브러리 `lib`을 디스크에서 다시 읽고, 그 라이브러리를 쓰는 열린 파일 모두의 옛 버전 부품을 바꾼다(`file.save`의 저장 반영과 같은 일). 없는 라이브러리는 오류 1. 이 파일이 바뀌었으면 `changed`, 아니면 `outcome:"same"`.

`model.changed = {fileId, circuitId, removed:[id], added:[Component|Wire], nets, junctions, groups, memos, dirty}`

- 화면은 `removed`를 먼저 지우고 `added`를 id로 넣거나 바꾼다(upsert). 제자리에서 바뀐 부품(같은 id)은 `added`에만 온다.
- `nets`·`junctions`는 그 회로의 **전체** 목록이다(넷 번호를 다시 매겼다). 한 편집이 다른 회로도 바꾸면(예: 서브회로 핀을 바꿔 인스턴스 포트가 바뀜) 회로마다 하나씩 온다. `dirty`는 파일의 저장 필요 여부다.
- `groups`·`memos`(N-15)도 그 회로의 **전체** 목록이다(빈 목록이면 없음). 부품·선이 그대로이고 그룹·메모만 바뀐 편집(과 그 되돌리기)도 이 알림으로 온다.

### sim

| 메서드 | params | result |
| --- | --- | --- |
| `sim.reset` | `{fileId}` | `{}`(원조 Reset Simulation, 사이클 수 0. N Cycles 도중이면 처리 중인 틱이 끝난 뒤 재설정하고 그때 `sim.state`) |
| `sim.poke` | `{fileId, circuitId, componentId, at?:[x,y], action?}` | `{poked, caret}`(Poke 도구와 같은 동작: 핀 값 바꾸기 등) |
| `sim.pokeKey` | `{fileId, key}` | `{poked}`(Poke로 누른 부품의 캐럿에 키 하나, N-07) |
| `sim.pokeStop` | `{fileId}` | `{}`(Poke 캐럿을 닫는다: 원조 PokeTool.removeCaret, N-07) |
| `sim.pinValue` | `{fileId, circuitId, componentId, value}` | `{}`(입력 핀에 값 넣기: v1 Set Pin Value, N-08) |
| `sim.cycles` | `{fileId, n}` | `{}` 곧바로. 틱은 엔진이 따라오는 만큼만 요청한다(D-123). 끝나면 `sim.state` |
| `sim.tick` | `{fileId}` | `{}` 곧바로(원조 Simulate › Tick Once: 틱 한 번 = 반 사이클, N-07) |
| `sim.step` | `{fileId}` | `{}`(원조 Simulate › Step Simulation: 꺼져 있을 때만 전파 한 단계, N-07) |
| `sim.run` | `{fileId, on, hz?}` | `{}` |
| `sim.enable` | `{fileId, on}` | `{}`(원조 Simulation Enabled: 발진으로 꺼진 뒤 다시 켠다) |
| `sim.watch` | `{fileId, circuitId, path?:[componentId]}` | `{}`: 이 회로(서브회로 안이면 인스턴스 경로)의 넷 값을 보낸다 |
| `sim.state` | `{fileId}` | 아래 `sim.state`와 같은 객체(요청으로도 물을 수 있다) |

- `sim.poke`: `action`은 `"click"`(기본, 누르고 뗌), `"press"`, `"release"`(버튼처럼 누르는 동안만 켜지는 부품은 화면이 누를 때와 뗄 때 따로 보낸다). `at`은 회로 좌표(여러 비트 핀에서 어느 비트인지), 없으면 부품 가운데. 여러 비트 입력 핀은 원조 `PinPoker.getBit`가 `at`으로 비트를 고른다(오른쪽 아래가 비트 0, 한 줄 8비트, 칸은 가로 10·세로 20): 화면은 누른 점을 정수로 내려 보낸다. 누를 것이 없는 부품·선은 `poked:false`. `caret`: 누른 뒤 그 부품에 원조 캐럿이 남았다(키를 줄 수 있다: `sim.pokeKey`). 보고 있지 않은 회로의 부품을 누르면 그 회로가 시뮬레이션의 지금 회로가 된다. 서브회로를 보며 그 안의 입력 핀을 누르면 오류 4 `frozenPin`(원조는 상태를 복제할지 묻는다).
- `sim.pinValue`(N-08, D-146): 입력 핀 더블클릭의 값 넣기(v1 `Shortcuts.askValue`·`parseValue`·`setPinValue`). `value`는 `0x1F`, `0b1011`, `31`, `-3`(2의 보수)이고 `_`·빈칸은 무시한다. 핀 폭을 넘거나 읽을 수 없으면 -32602 `{reason:"badValue"}`, 출력 핀·핀이 아닌 것은 -32602, 서브회로 인스턴스 안을 보며 그 안의 입력 핀이면 오류 4 `frozenPin`. 시뮬레이션 상태만 바뀐다(파일은 그대로, 되살리기 저널에도 적지 않는다).
- `sim.pokeKey`(N-07, D-145): `key`는 화면 `KeyboardEvent.key`의 글자 하나 또는 `Backspace`·`Enter`·`Tab`·`Delete`·`Escape`·`ArrowLeft`·`ArrowRight`·`ArrowUp`·`ArrowDown`·`Home`·`End`(그 밖은 -32602). 원조 PokeTool처럼 캐럿에 keyPressed, 글자가 있으면 keyTyped, keyReleased를 준다: Register·Counter는 16진 글자를 오른쪽에서 밀어 넣고(`RegisterPoker`), RAM·ROM은 값·주소(`MemPoker`), Shift Register(`ShiftRegisterPoker`), Keyboard(`Keyboard.Poker`)가 받는다. 캐럿이 없으면 `poked:false`. 누르던 부품이 모델에서 사라지면 캐럿도 닫힌다.
- `sim.cycles`: `n` ≥ 1, 한 사이클 = 원조 틱 2번. 도는 클럭(Ticks Enabled)은 먼저 끈다: 요청한 수만큼만 돈다(N-07, D-145). 처리 중인 틱이 8개를 넘지 않게 요청하고 틱 완료로 센다(틱이 빠지지 않는다). 도는 동안 원조 틱 스레드가 틱마다 학생의 틱 주파수만큼(1 Hz면 최대 100ms) 자지 않게 원조 틱 주파수를 1024 Hz(한 주기 1ms)로 두고 끝나면 되돌린다(`sim.state.hz`는 늘 학생의 값). ref-mips 1000 사이클 약 1.4초(v1 25초, D-145). 돌고 있으면 `n`을 더한다. 시뮬레이션이 꺼져 있으면 오류 4(`off`·`oscillating`), 도중에 꺼지면 `engine.log`와 `sim.state`로 알리고 멈춘다. 끝나면 그 순간의 값(`sim.values`)을 먼저, `sim.state`를 뒤에 보낸다. `sim.tick`은 같은 실행기로 틱 한 번이다.
- `sim.step`: 시뮬레이션이 켜져 있으면 오류 4 `running`(원조 메뉴 항목이 꺼져 있는 것과 같다). 원조가 그리던 전파 지점(파란 원)은 보내지 않는다(docs/interaction-parity.md I-150).
- `sim.run`: `on`이면 원조 틱(Ticks Enabled)을 켜고, `hz`는 원조 틱 주파수(초당 틱, Swing 속도 메뉴와 같은 값: 1·4·16·64·256·1024·4096)다. 켤 때 시뮬레이션이 꺼져 있으면 오류 4. 돌고 있는 N Cycles(`sim.cycles`·`sim.tick`)는 켜든 끄든 멈춘다: 남은 틱을 더 요청하지 않고 처리 중인 틱(8개 이하)만 끝내며, 사이클 가운데면 한 틱을 더해 사이클을 채운다(화면의 Stop, D-145). N Cycles 없이 도는 클럭을 끄는 것(`on:false`)은 원조 Simulate › Ticks Enabled 끄기 그대로라 사이클을 채우지 않는다: 반 사이클(클럭 1)에서 멈출 수 있고, 그때 `cycle`은 다 끝난 사이클 수이며 오름 끝에 움직이는 부품(카운터·레지스터)은 다음 사이클의 끝을 이미 받았다(D-145 2항, D-146).
- `sim.watch`: `circuitId`는 시작 회로, `path`는 거기서 내려가는 서브회로 인스턴스 id들이다. 보는 회로는 시뮬레이션의 지금 상태가 된다(Swing에서 그 회로·인스턴스를 여는 것과 같다). 파일마다 하나만 본다(다시 보내면 바꾼다). 처음에는 모든 넷을 한 번 보낸다. 없는 경로는 오류 1. 모델과 편집 선택은 바꾸지 않는다: 떠 있는 붙여넣기·복제 사본은 그대로 남아 다음 편집 의도가 그것이 생긴 회로에 내려놓는다. 화면은 떠 있는 사본이 있는 채 다른 회로·인스턴스를 보이기 전에 `edit.select {circuitId: 사본이 생긴 회로}`(비우기)를 보내 거기 내려놓는다(편집 의도: `model.changed`와 되살리기 저널이 따른다, D-146).

`sim.values = {fileId, circuitId, root?, path?, nets:{netId: value}, bodies?:{componentId: Body}}` — 보고 있는 회로의 바뀐 넷만, 화면 프레임(약 16ms)마다 묶어서. `circuitId`는 값이 속한 회로(경로의 끝), `root`·`path`는 `path`로 볼 때만 온다. 모델이 바뀌면(넷 번호가 새로 매겨지면) 다음 묶음에 모든 넷을 다시 보낸다.

- `bodies`(N-05, D-137): 부품 몸체에 보이는 상태 가운데 넷 값에 없는 것. 보는 회로의 부품만, 앞에 보낸 것과 달라졌을 때만 온다(보기를 바꾸거나 모델이 바뀌면 처음부터 다시). 레지스터·카운터·핀·LED처럼 값이 넷에 그대로 있는 부품은 싣지 않는다(화면이 넷 값으로 그린다). 엔진은 회로 상태에 이미 있는 부품 데이터만 읽고 만들지 않는다: 시뮬레이션이 그 부품을 아직 돌리지 않았으면(예: 꺼진 채 놓은 RAM) 그 몸체는 오지 않는다.

  | 부품 | Body |
  | --- | --- |
  | RAM, ROM | `{columns, rows:[{addr, words:[글자]}], current?}`: 원조가 몸체에 그리는 4줄 표(원조 MemState의 스크롤 자리부터), 16진수 글자, `current`는 지금 주소 |
  | Shift Register | `{stages:[글자]}`: 단마다의 값(16진수, 0번이 최근) |
  | Instruction Memory, Data Memory, Stack(Hallym MIPS) | `{lines:[글자], status?}`: lib-mips 몸체 줄(영역, words, 지금 주소의 워드, 합친 Data Memory의 두 영역과 쓰임 `data N words, stack peak N B`, D-140)과 빨간 상태 글 |
  | Console | `{lines:[글자], exited, status?, error?}`: 출력의 마지막 줄들(원조 몸체와 같은 접기), `-- exit --` 또는 문제 |
  | Radix Probe | `{lines:[글자 셋], primary}`: 주 진법이 첫 줄인 16·10·2진수, 조작 도구로 바꾼 주 진법 포함 |

`sim.state = {fileId, running, ticking, cycle, oscillating, hz, cyclesLeft}` — 무엇이든 바뀌면 보내고(사이클 수만 바뀐 것은 프레임마다 많아야 한 번), N Cycles가 끝날 때와 Reset 뒤에도 보낸다. `running`은 원조 Simulation Enabled, `ticking`은 틱 켜짐, `cycle`은 Reset 뒤 끝난 틱 수의 절반, `hz`는 학생이 고른 틱 주파수, `cyclesLeft`는 N Cycles(`sim.cycles`·`sim.tick`)에 남은 사이클(처리 중인 틱 포함, 반 사이클은 올림; 없으면 0, N-07)이다. `record.view`로 지난 사이클을 보이면 `cycle`은 그 사이클이 되고, 거기서 진행하면 거기서 이어 센다(기록과 같다). N Cycles와 Run Until이 도는 동안 엔진은 원조 틱 스레드가 틱마다 쉬지 않게 틱 주파수를 잠시 1024로 두지만(둘이 겹쳐도 되게 쥔 수를 센다) `hz`는 학생이 고른 값을 알리고, 끝나면 되돌린다(D-144, D-145). Run Until이 도는 동안 `sim.run {on:true}`와 `sim.cycles`·`sim.tick`은 오류 4 `busy`다.

### record(N-14, D-144)

사이클 기록(v1 C-01~C-07·V-03·V-08의 GUI 없는 코드: `Recorder`·`Recording`·`CycleModel`·`RegisterFile`·`PcMark`·`StatusModel`·`RunUntil`, 엔진 `MemoryTable`)을 연다. 기록은 파일을 열 때 시작하고, 원조 시뮬레이터가 돈 틱마다 한 스텝(한 사이클 = 두 스텝)을 적는다(버려진 틱은 돌지 않은 틱이라 적을 것이 없다, D-123). 모든 넷의 바뀐 값만 적고 64스텝마다 회로 상태 전체를 복제해 두므로, 지난 사이클의 넷 값은 다시 돌리지 않고 읽고(`record.values`, 표), 회로 상태(메모리·레지스터 부품 안·서브회로 안)는 가장 가까운 복제에서 원조 엔진으로 다시 만든다(`record.view`). Reset(`sim.reset`)과 회로 편집 뒤에는 새로 적는다(Reset은 스텝 0부터, 편집은 지금 스텝부터). `file.open`·`file.new`는 기록이 스텝 0(연 회로의 첫 전파)을 적은 뒤 답하고, Reset 바로 뒤의 `record.runUntil`·`record.view`는 재설정이 기록된 뒤 시작한다(첫 전파와 먼저 온 틱이 원조 시뮬레이터의 한 번에 겹치면 틱 뒤 상태가 스텝 0으로 적히므로). 보관 상한 100,000스텝. 값은 4절의 글자이고 보이기만 한다(판단하지 않는다, 학생 레지스터에 쓰지 않는다).

| 메서드 | params | result |
| --- | --- | --- |
| `record.state` | `{fileId}` | 아래 `record.state` 알림과 같은 객체 |
| `record.table` | `{fileId, from?, to?}` | 사이클 표(열 `from`~`to`, 없으면 마지막 60열, 한 번에 400열까지) |
| `record.addRow` | `{fileId, circuitId, path?:[componentId], wireId? \| netId? \| at?:[x,y]}` | `{id, added, name?, width?}`: 그 넷을 줄로(Add to Cycle View). 같은 넷이면 `added:false`와 그 줄 |
| `record.removeRow` | `{fileId, id}` | `{removed}` |
| `record.rowBits` | `{fileId, id, bits}` | `{changed}`: 버스 줄을 비트로 펼쳐 보이기(v1 Show Bits, 화면 표시만) |
| `record.pin` | `{fileId, cycle?, rows:[{circuitId, path?, at}]}` | `{ids, view?}`: 메시지의 원인·E/X가 생긴 자리를 표 맨 위 임시 줄로(있던 임시 줄은 대체, V-03). `cycle`이면 그 사이클을 본다. 넷을 찾지 못한 자리(기록 밖의 인스턴스 경로 포함)는 빠진다(v1처럼, 오류 없음) |
| `record.unpin` | `{fileId}` | `{}`: 임시 줄을 걷는다 |
| `record.view` | `{fileId, cycle? \| latest?:true}` | `{cycle, past}`: 그 사이클의 회로 상태를 보인다(C-03) |
| `record.values` | `{fileId, cycle, circuitId, path?}` | `{fileId, circuitId, cycle, nets:{netId: value}}`: 기록에서 읽은 그 사이클의 넷 값(다시 돌리지 않음) |
| `record.runUntil` | `{fileId, kind, value?, maxCycles?}` | `{}` 곧바로. 끝나면 `record.runUntil` 알림 |
| `record.stop` | `{fileId}` | `{stopped}`: Run Until을 멈춘다 |
| `record.registers` | `{fileId, cycle?}` | 레지스터(보는 사이클, 또는 `cycle`) |
| `record.memory` | `{fileId}` | Memory 표(보는 사이클의 회로 상태) |
| `record.instruction` | `{fileId, cycle?}` | 그 사이클의 명령어와 필드 |
| `record.fieldPaths` | `{fileId, circuitId, cycle?}` | `{fileId, circuitId, cycle, format?, fields:{name: [wireId]}}`: 그 사이클 명령어의 형식에 있는 필드마다, 이 회로에서 그 이름을 붙인 스플리터 팔의 선들(v1 C-07 FieldPaths; 캔버스의 필드 색 띠는 N-15) |
| `record.markPc` | `{fileId, circuitId, componentId, on?}` | `{changed, dirty}`: Mark as PC(V-08, D-103). 레지스터·카운터만(아니면 -32602) |
| `record.markRegisterFile` | `{fileId, circuitId, on?}` | `{changed, dirty}`: Mark as Register File(D-076, 파일에 하나) |
| `record.registerMapping` | `{fileId}` | `{circuitId, name, registers:[{id, name, loc}], map:{"0".."31": [x,y]\|null}, guess:{…}}`: 대응은 레지스터 파일 회로 안 Register의 자리(v1 `regmap`이 적는 그대로) |
| `record.setRegisterMapping` | `{fileId, circuitId, map:{"n": [x,y]\|null}}` | `{changed, dirty}`: `circuitId`는 표시한 레지스터 파일(아니면 -32602), 자리에 Register가 없으면 오류 1. 빠진 번호는 지금 대응 그대로. 짐작과 다른 것만 `regmap`으로 저장 |

`record.state = {fileId, empty, first, last, cycle, past, generation, pc, cpu, rows, pinned, runUntil}` — 알림. 엔진이 화면 프레임(16ms)마다 보고 바뀌었을 때만 보낸다(파일을 연 뒤 첫 프레임에도). `first`·`last`는 기록에 남은 첫·마지막 사이클, `cycle`은 보고 있는 사이클(지난 사이클을 보면 `past:true`), `generation`은 새로 적기 시작할 때마다 하나씩 는다(Reset, 다시 열기). `pc`는 상태 표시줄의 PC(D-103: Mark as PC → 라벨이 PC인 부품(터널보다 다른 부품 먼저) → Instruction Memory의 Addr, 첫 포트 값 `"0x00400024"`, 정해지지 않았으면 `null`), `cpu`는 Instruction Memory가 있어 표에 PC·명령어 줄이 있는가, `rows`·`pinned`는 줄 수, `runUntil`은 도는 동안 `{kind, value?, from}`.

- **사이클과 스텝.** 열 c = 사이클 c = c번째 상승 에지 뒤 다음 상승 에지 앞(스텝 2c, 상태 표시줄 "Cycle c"). 1비트 줄의 파형은 앞 절반(스텝 2c−1, `halves`)과 뒤 절반(스텝 2c, `values`)이다(D-074).
- **`record.table`** = `{fileId, empty, first, last, cycle, from, to, cpu, pinnedCycle, columns, rows}`. `columns:[{cycle, pc, word, text}]`: Instruction Memory(최상위 먼저, 그다음 서브회로 안에서 처음 만나는 것)의 Addr·Instr 값(`"0x…"`, 정해지지 않았으면 `null`)과 lib-mips 디스어셈블러(D-127)의 글(`"jal 0x00400058 [fact]"`: 이름표는 그 부품의 `source` 속성이 가리키는 .hmx의 기호, 읽기만 한다). `rows:[{id, name, width, bits, temp, values, halves?}]`: 임시 줄(`temp:true`) 먼저, 그다음 더한 순서. 줄은 파일에 저장하지 않는다(v1과 같다). 기록에 없는 값은 `null`.
- **줄의 넷.** `circuitId`는 그 선이 있는 회로, `path`는 기록하는 최상위 회로(= `sim.watch`로 본 회로)에서 내려가는 인스턴스 id들이다(없으면 최상위). `wireId`·`netId`(모델 알림의 넷 id)·`at`(포트나 선 끝 자리) 가운데 하나. 이름은 그 넷의 이름(터널·핀 라벨), 없으면 그 넷을 내는 포트(`PC (Q)`), 서브회로 안이면 경로(`regfile › RD1`)다.
- **임시 줄의 수명(D-114).** 기록이 바뀌면(Reset, 새로 적기, 임시 줄의 사이클이 기록 밖) 엔진이 걷는다. 창은 사이클이 있는 메시지를 누르면 `location`(원인)과 `appeared`(E·X가 처음 보인 자리)로 `record.pin {cycle}`을 부르고 Cycle View 탭을 앞으로 가져오며(v1 D-05·V-03), 그 메시지가 `diag.changed` 목록에서 사라지면 `record.unpin`을 부른다.
- **`record.view`.** 지난 사이클이면 체크포인트에서 다시 만든 상태를 프로젝트에 바꿔 끼우고 클럭(Run)을 멈춘다. 서브회로 안을 보고 있으면 같은 인스턴스 안으로. `sim.values`가 그 사이클의 값을 다시 보낸다(모든 넷). `latest:true`는 마지막 스텝(떼어 둔 지금 상태). 지난 사이클에서 틱하거나(1 Cycle·N Cycles·Run Until) 입력·회로를 바꾸면 그 뒤 기록을 버리고 거기서 이어 적는다. N Cycles나 Run Until이 도는 동안은 오류 4 `busy`, 기록이 없으면 오류 4 `empty`.
- **`record.runUntil`**(C-04, D-075): `kind`는 `pc`(`value`: `0x00400034`·`400034` 16진, 또는 .hmx 기호 이름), `instruction`(`value`: 디스어셈블러의 명령어 이름, 대소문자 무관), `row`(`value`: 줄 id, 그 줄 값이 바뀜), `errorOrX`(넷 하나라도 E가 되거나 정해져 있던 넷에 X가 생김), `halt`(라벨이 halt인 출력 핀이 1, 또는 Console의 Exit가 1, 서브회로 안도). `maxCycles` 기본 10,000. 보고 있는 사이클에서 시작하고 조건은 다음 사이클부터 본다(이미 조건인 곳에서 누르면 한 번은 나아간다). 한 사이클(틱 두 번)씩 요청하고 기록이 그 사이클을 적은 뒤 조건을 보고 다음 사이클을 요청한다(쌓이는 틱 ≤ 2, D-123). Run(틱 켜짐)은 끈다. 도는 동안 원조 틱 스레드의 주파수는 1024 Hz로 두어(틱 뒤 잠드는 시간 없이 다음 사이클로) `sim.state`의 `hz`는 학생이 고른 값을 말하고, 그동안 `sim.run {hz}`로 바꾼 속도는 끝난 뒤 적용하며, `sim.run {on:true}`는 오류 4 `busy`다. 끝나면(만남·한계·멈춤·꺼짐·파일 닫기) 학생의 속도로 돌아온다. 오류: 읽을 수 없는 값 -32602(`data.reason` `badPc`·`badInstruction`·`noRow`), 시뮬레이션 꺼짐 4 `off`·`oscillating`, 이미 돌거나 N Cycles 중 4 `busy`, 기록 없음 4 `empty`.
  `record.runUntil`(알림) = `{fileId, result:"met"|"limit"|"stopped"|"off", cycle, from, kind, value?}`: 조건을 만남, 최대 사이클 수에 닿음, `record.stop`·Reset·회로 편집, 도중에 시뮬레이션이 꺼짐.
- **`record.registers`** = `{fileId, cycle, circuitId, mode:"file"|"all"|"none", registerFile?, unmapped?, candidates?, rows}`. 규칙은 v1 그대로다(D-076, D-103, D-108): 레지스터 파일을 표시했으면(`mode:"file"`, `registerFile:{circuitId, name}`) $0~$31(대응: 라벨 숫자 → 라벨 이름 → 위치, 고친 것은 `regmap`), 아니면(`all`) 모든 Register 부품(인스턴스 경로별, 라벨이 `$5`·`R5`·`t0` 같으면 번호). PC(Mark as PC → 라벨 PC → Instruction Memory Addr을 내는 레지스터·카운터, 조합 부품 한 단계까지)는 `key:"PC"`. `rows:[{key, name, number, group, value, changed, alias?, componentId?, markable?, markedPc?}]`: 번호가 있는 줄은 Hallym MIPS 레지스터 창의 묶음과 순서(`Special`(PC), `Constant`, `Return values`, `Arguments`, `Temporaries`, `Saved`, `Pointers`($gp $sp $fp), `Return address`, `Reserved`($at $k0 $k1)), 이름은 `$t0` 꼴이고 회로 안의 이름(`$8`)은 `alias`. 번호가 없거나 겹친 레지스터는 `Other registers`. `changed`는 앞 사이클과 다름. `markable`: 최상위의 Register·Counter(Mark as PC를 걸 수 있음). `unmapped`: 표시가 없고 $n 이름도 없음(화면이 "레지스터 파일을 표시하지 않아 모두 나열" 안내). `candidates`: 표시가 없을 때 고를 수 있는 서브회로(최상위 아래에서 쓰이고 Register가 든 것). `circuitId`: 줄의 `componentId`가 든 최상위 회로(`record.markPc`에 쓴다).
- **`record.memory`** = `{fileId, cycle, parts, rows}`: 보고 있는 사이클의 회로 상태에서 모든 Data Memory(옛 Stack 포함)를 위 mips 절의 Memory 표로. 라벨은 .hmx 기호, 포인터는 레지스터 $sp·$fp·$gp(정해진 값만). 시뮬레이터가 쓰는 도중에 읽혔으면 다시 읽는다.
- **`record.instruction`** = `{fileId, cycle, pc, word, text, mnemonic, format, fields}` 또는 `{…, none:"empty"|"noCpu"|"undefined"}`. `format`: `R`·`I`·`J`·`CP0`·`FR`·`FI`. `fields:[{name, hi, lo, bits, value, meaning}]`는 높은 비트부터 32비트를 빈틈없이 덮고, 이름은 Hallym MIPS Inspector와 같다(`opcode rs rt rd shamt funct immediate target`, `fmt ft fs fd cc nd tf`, `CO code sel`): 화면이 이 이름으로 필드 색을 고르므로 두 프로그램이 같은 명령에 같은 색을 보인다. `value`는 10진(immediate는 부호 있게), `meaning`은 사실만(레지스터 이름, 16진, 분기 목적지 = 분기 주소 + imm×4(D-010·D-127)와 기호, 점프 주소, opcode·funct는 명령어 이름).
- **`record.fieldPaths`**: 팔 이름은 v1 규칙(대소문자 무시, `op`·`opcode`, `imm`·`immediate`·`imm16`·`offset`, `addr`·`target`·`address`)으로 읽고, 필드 이름은 Hallym MIPS 이름(`opcode rs rt rd shamt funct immediate target`)으로 준다. 팔에서 첫 부품 입력까지만 따라간다(레지스터 파일·ALU 너머는 필드가 아니라 레지스터 값, D-078). 명령어가 정해지지 않았으면 모든 필드.
- **표시(hcs:ext, v1 그대로).** Mark as PC는 회로마다 `<hcs:pc at="(x,y)"/>`, 레지스터 파일은 `<hcs:regfile/>`, 대응은 `<hcs:regmap r5="x,y" …/>`(짐작과 다른 번호만, 없음은 `-`). 원조 2.7.1은 이 확장 블록을 건너뛴다. 각각 되돌리기 한 단계(`edit.undo`·`edit.redo`)이고, 읽기 전용 파일은 오류 3 `readOnly`.

### diag·trace(Messages와 E/X 출처, N-13, D-143)

| 메서드 | params | result |
| --- | --- | --- |
| `diag.list` | `{fileId}` | `{fileId, messages:[Message]}` |
| `trace.origin` | `{fileId, circuitId, path?:[componentId], netId}` | `{found, text?, origin?, chain:[{circuitId, path, netId}]}` |

알림 `diag.changed = {fileId, messages:[Message]}` — 목록 전체.

```
Message = {
  id,                        // "d7": 같은 원인이 목록에 머무는 동안 그대로(문구·사이클이 바뀌어도)
  code,                      // 아래 표
  kind: "static"|"dynamic",  // 연결만 보고 찾음 | 시뮬레이션 중에 찾음
  severity: "error",         // 모든 메시지는 "동작할 수 없는 회로"다(CLAUDE.md 2.6). 다른 값은 아직 없다
  text: {ko, en},            // 한 문장(원인 한 곳, 학생이 붙인 이름, 사실과 위치까지만)
  near?,                     // TUNNEL_UNPAIRED: 가까운 이름 하나("혹시 RegWrite?"), 확신할 때만
  location: {
    circuitId,               // 원인이 있는 회로
    root, path:[componentId],// 맨 위 회로와 거기서 circuitId 인스턴스까지의 서브회로 부품들(static은 root = circuitId, path [])
    components:[id], wires:[id], nets:[netId],   // 보일 부품·선·넷(원인 부품이 맨 앞)
    at:[x,y]|null,           // 갈 곳(원인 포트나 부품 자리)
    cycle?                   // dynamic: 사이클 뷰의 열(스텝 2c-1, 2c가 열 c)
  },
  appeared?: {circuitId, root, path, at, netId?}  // dynamic: E·X가 처음 보인(쓰려던) 자리. 원인 자리와 다를 수 있다
}
```

| code | kind | 뜻(v1 `Diagnostic.Kind`와 같다) |
| --- | --- | --- |
| `CLOCK_UNCONNECTED` | static | 클럭 입력이 비어 값이 바뀌지 않는 부품(Register, 플립플롭, 쓰기가 있는 Data Memory, syscall이 있는 Console 등) |
| `SHORT` | static | 떠 있을 수 없는 출력 둘이 한 선을 구동 |
| `WIDTH_MISMATCH` | static | 한 선의 비트 폭 불일치(원조 계산) |
| `INPUT_UNCONNECTED` | static | 비면 동작할 수 없는 입력(게이트의 빈 입력은 프로젝트 옵션 gateUndefined = error일 때만) |
| `INPUT_UNDRIVEN` | static | 입력이 이어진 선에 값을 내는 것이 없음 |
| `TUNNEL_UNPAIRED` | static | 값을 받는 터널의 이름이 회로에 하나뿐(`near`) |
| `SUBCIRCUIT_PORT_UNCONNECTED` | static | 서브회로 인스턴스의 입력 포트가 비었음 |
| `COMBINATIONAL_LOOP` | static | 조합 루프(발진 중이면 `OSCILLATION` 한 줄로 바뀐다) |
| `MEMORY_OVERLAP` | static | Data Memory·Stack 영역 겹침 |
| `E_APPEARED` | dynamic | E가 새로 생김, 원인 한 곳(E·X 출처) |
| `X_WRITE_DATA` | dynamic | 클럭 에지에 쓸 값·주소가 정해지지 않아 쓰지 못함 |
| `X_WRITE_CONTROL` | dynamic | 클럭 에지에 쓰기 허용 입력(en, MemWrite)이 정해지지 않음 |
| `OSCILLATION` | dynamic | 원조가 발진으로 전파를 그만둠(`sim.state.oscillating`). 고리의 부품·선·넷 |
| `MIPS_STATUS` | dynamic | Hallym MIPS 부품의 값 문제(영역 밖 주소, 워드 정렬, 스택 한계, syscall). 부품 몸체의 사실 |

- **어느 회로가 동작할 수 없는지만 말한다.** 정상 회로(tests/circ의 엔진 회귀 회로·데모, 참조 CPU)는 열 때도, 사이클을 돌린 뒤에도 0건이다(`DiagTest`). 원인이 같으면 한 줄이다: 정적 진단과 같은 부품·선을 말하는 동적 진단은 빼고, 발진 중이면 같은 고리의 조합 루프 대신 발진 한 줄이다(v1 `DiagnosticSet`).
- **순서:** 정적 진단(파일의 회로 순서, 그 안에서 표의 순서), 발진, 동적 진단(찾은 차례).
- **문구:** 영어·한국어 두 벌을 함께 보내고 화면이 고른다. 이름(부품·포트·터널·회로)은 어느 PC에서나 영어이고 학생이 붙인 이름 그대로다(엔진의 원조 언어는 영어로 고정). 한국어 문장은 이름 바로 뒤에 조사를 붙이지 않는다(v2 지시 7절: "main › PC (Register) 부품은 …", "RegWirte 터널과 …"). 고장 회로 모음의 두 벌 문구가 `tests/circ/faults/messages.v2.{ko,en}.expected`에 있다.
- **가까운 이름(`near`, D-143):** 짝 없는 터널과 가까운 다른 터널 이름이 **하나뿐**이고 그 이름의 터널에 같은 비트 폭이 있을 때만 짚는다. 가깝다 = 대소문자만 다름, 또는 두 이름 모두 4글자 이상이고 숫자만 다른 것이 아니며(`ALUOp0`·`ALUOp1`) 편집 거리(넣기·빼기·바꾸기·붙은 두 글자 맞바꾸기, 대소문자 무시)가 짧은 쪽 7글자까지 1, 8글자부터 2 이하. 3글자 이하(`rs`·`rt`)는 대소문자만 다를 때만. 후보가 둘 이상이거나 폭이 다르면 짚지 않는다.
- **언제 바뀌나:** 편집·되돌리기 뒤 120ms 동안 다른 편집이 없으면 정적 검사를 다시 돈다(ref-mips 약 70ms, 엔진 스레드). 동적 진단은 기록 엔진이 스텝을 적을 때마다 새 스텝만 본다(원조 시뮬레이터 스레드). `sim.reset`은 기록을 스텝 0부터 다시 적으므로 동적 진단이 걷힌다. 발진은 원조가 전파를 그만둘 때 생기고 Reset으로 걷힌다.
- **`diag.changed`:** 화면이 마지막으로 받은 목록(앞의 `diag.list` 결과나 `diag.changed`)과 다를 때만, 화면 프레임(16ms)에 묶어 보낸다. 파일을 연 직후 화면이 아는 목록은 빈 목록이다: 첫 검사에서 메시지가 나오면 곧 `diag.changed`가 온다(화면은 열자마자 `diag.list`를 물어도 된다). 넷 번호는 모델이 바뀔 때마다 다시 매기므로(`model.changed`와 같다) 편집 뒤 `location.nets`만 바뀐 목록도 온다. 그때도 `id`와 문구는 그대로다.
- **파일을 열 때:** 기록 엔진이 붙은 뒤 첫 상태가 스텝 0으로 적힐 때까지 `file.open`·`file.new` 안에서 잠깐(많아야 2초) 기다린다. 그래야 곧바로 이어지는 `sim.cycles`에서도 사이클 번호가 v1과 같다.
- **`trace.origin`:** 보이는 상태(맨 위 `circuitId`, 거기서 `path`로 내려간 인스턴스; 사이클 뷰가 지난 사이클을 보이면 그 상태)에서 넷 `netId`의 E·X가 처음 생긴 곳을 입력 쪽으로 거슬러 찾는다(v1 Find E/X Origin, D-01: MUX는 고른 입력만, 서브회로 경계와 스플리터 비트를 건넌다, 메모리는 주소·읽기 입력을 따라간다). `found:false`면 `text`가 따라갈 것이 없다는 문장이다. `origin`은 위 `location`과 같은 모양에 `cause`(`COMPONENT`·`UNDRIVEN`·`CONFLICT`·`ALL_OFF`·`INPUT_PIN`·`STORED`·`LOOP`), `value`(`"E"`·`"x"`), `text`(원인 문장 두 벌)가 붙는다. `chain`은 시작 넷부터 원인까지 지난 넷들(강조용)이다. 없는 넷·인스턴스는 오류 1.
- **화면의 "이곳 보이기"(reveal):** 화면은 메시지를 누르면 `location`을 그대로 담은 사건(`electron/src/renderer/app/reveal.ts`, `hcs:reveal`: `{fileId, messageId, circuitId, root, path, components, wires, nets, at, cycle}`)을 보낸다. Canvas(N-05)가 그 회로·인스턴스로 가서 부품·선·넷을 표시하고, Cycle View(N-14)가 `cycle`로 간다.

### find(Find와 검색 창, N-12, D-150)

| 메서드 | params | result |
| --- | --- | --- |
| `find.query` | `{fileId, text, limit?}` | `{fileId, text, groups:[FindGroup], more}` |

```
FindGroup = {kind: "label"|"pin"|"tunnel"|"subcircuit"|"part", text, path, places:[FindPlace]}
FindPlace = {circuitId, root, path:[componentId], componentId, at:[x,y], place, near}
```

- v1 Ctrl+F의 색인(`NameIndex`, D-036)을 그대로 쓴다: 라벨·터널 이름·서브회로 이름을 주 회로에서 서브회로 부품을 따라 내려가며(깊이 32) 모으고, 주 회로에서 닿지 않는 회로도 넣는다. 대소문자 없는 부분 일치이고 이름이 정확히 같은 것이 먼저다. v2는 라벨이 있는 핀을 `pin`으로 가르고, 부품을 원조 부품 이름으로도 찾는다(`part`: `Register`, `Instruction Memory`; 터널과 서브회로 부품은 이름으로 이미 찾으므로 빼고, 같은 회로 경로의 같은 종류가 한 묶음).
- 묶음: 종류·글(`text`)·경로(`path`, 예 `main › regfile #1 › RR1`, 부품은 회로 경로)가 같은 것이 한 줄이다(v1 #135). 묶음 차례는 v1의 차례(주 회로의 부품 위→아래·왼쪽→오른쪽, 인스턴스 안은 그 인스턴스 자리에서)이고, 한 점에 선 부품(핀과 그 포트의 터널)은 종류 차례(label, pin, tunnel, subcircuit, part)·부품 이름·글로 정한다(v1은 원조 회로의 HashSet 차례라 실행마다 달랐다). 묶음 안 자리는 위→아래, 왼쪽→오른쪽이다.
- 자리(`FindPlace`): 보일 회로(`root`에서 `path`의 서브회로 부품들을 따라 내려간 `circuitId`), 부품(`componentId`, `at`은 그 위치), 자리 글 `place`는 v1 위치 줄(S-09): 포트 하나짜리 부품은 선으로 닿는 가장 뜻있는 포트(`main › PC (D)`, `near:true`: 화면이 "next to …"를 붙인다), 없으면 번호 이름(`main › Tunnel #3`), 부품(`part`)은 라벨이나 번호 이름. 내부 포트 이름(`.in1`, `Split #10`)은 쓰지 않는다.
- `limit`(기본 100, 1 이상) 묶음까지 돌려주고 넘으면 `more:true`. 빈 글은 묶음 없음.
- 색인은 부를 때마다 지금 모델로 새로 만든다(편집 뒤 곧바로 맞다, I-171 정함). 모델을 읽기만 한다(`OpenSaveParityTest`의 화면이 여는 동안 하는 일에 든다). 화면은 편집(`model.changed`) 뒤 열린 찾기 창의 글로 다시 묻는다.
- 화면이 자리로 가는 것은 "이곳 보이기"(`hcs:reveal`, 5절 끝)에 `tone:"find"`를 붙인 것이다: 그 인스턴스 안으로 가서 부품을 선택의 파란 모양으로 보인다.

### flow·trace(캔버스 덧그림, N-15, D-151)

| 메서드 | params | result |
| --- | --- | --- |
| `trace.influence` | `{fileId, circuitId, from:[id], mode:"forward"\|"backward"\|"both"\|"between", throughRegisters?, depth?}` | `Influence` |
| `trace.net` | `{fileId, circuitId, wire? \| netId?}` | `{netId, width, name, drivers, readers, others}`: 포트마다 `{componentId, port, text}` |
| `flow.path` | `{fileId, circuitId, componentId, port?} \| {fileId, circuitId, wire, at?}` + `{backward?, throughRegisters?, activePathOnly?}` | `FlowPath` |
| `flow.activePath` | `{fileId, circuitId}` | `{circuitId, watched, muxes:[{componentId, input, segments:[[[x,y],[x,y]]]}]}` |

모두 **읽기만** 한다: 회로 모델도 시뮬레이션 상태도 바꾸지 않는다(`model.changed` 없음, 파일이 더러워지지 않음; `OpenSaveParityTest`가 이 요청을 거친 뒤에도 저장 결과가 원래와 같은지 본다). 계산은 v1의 GUI 없는 코드 그대로다: 공용 연결 엔진(`Netlist`, `Trace`)과 `Influence`(P-01), `SignalFlowPath`·`ActivePath`(P-07), `ActiveBranches`(C-08·V-04, v1 `ActivePathOverlay`에서 떼어 낸 계산). 회로는 화면이 보이는 회로이고(서브회로 인스턴스 안을 보면 그 서브회로), 경로는 그 회로를 맨 위로 삼는다.

```
Influence = {mode, depth, maxDepth, throughRegisters,
  forward:{wires:[id], parts:[id]}, backward:{wires:[id], parts:[id]},
  stops:[id], origin:[id], inside:[{componentId, name, places}], tunnels:[id], links:[[[x,y],[x,y]]]}
FlowPath = {circuitId, backward, total, click?:[x,y],
  segments:[{path, circuitId, from, to, start, length, width, cycle}],
  jumps:[{path, circuitId, from, to, start}],
  passes:[{path, circuitId, componentId, name, time, boundary, cycle}],
  endpoints:[{path, circuitId, componentId, port, at, time, kind:"output"|"state"|"unconnected"|"source", label}],
  loops:[id], undetermined:[id]}
```

- **`trace.influence`**(v1 P-01, D-062): `from`(부품·선)에서 앞(`forward`: 그것이 구동하는 곳), 뒤(`backward`: 그것을 구동하는 곳), 양쪽(`both`), 또는 두 부품 사이(`between`: 첫째에서 앞으로 ∩ 둘째에서 뒤로, `from`이 부품 둘이어야 한다). 상태 부품(레지스터·메모리)에서 멈추고 `throughRegisters`면 넘는다. `depth`는 건넌 부품 수의 한계(없거나 -1이면 끝까지, 0은 -32602, `maxDepth` 이상은 끝까지로 돌려준다). `maxDepth`는 끝까지 갔을 때의 가장 깊은 곳이다(화면의 [ ]가 쓴다). `stops`는 멈춘 상태 부품, `origin`은 시작, `inside`는 안쪽에 닿은 서브회로 인스턴스와 그 안에서 닿은 곳 수(`name`은 서브회로 이름, "alu: 3 places"), `tunnels`는 닿은 넷의 터널(자리 순), `links`는 터널이 딱 둘인 넷의 두 터널(클럭 넷 빼고, v1). 선·부품 목록은 v1 `Influence.View`의 차례다.
- **`trace.net`**(v1 B-09 Net Information…): 선 `wire`의 넷 또는 `netId`의 폭, 이름(터널 라벨 → 구동하는 핀 → 다른 핀, 없으면 `""`), 값을 내는 포트·읽는 포트·그 밖(터널·스플리터 등). `text`는 v1 넷 정보와 같은 공용 식별자 글(`alu #1 (Result)`, 포트 하나짜리는 부품 이름)이고, 목록은 포트 자리(y, x)·부품 이름·포트 번호 순이다(넷의 포트 차례는 해시 차례를 따를 수 있어 자리로 정렬한다).
- **`flow.path`**(v1 P-07, V-06, D-063): 부품 `componentId`(`port`를 주면 그 포트만, 없으면 앞으로는 모든 출력·뒤로는 모든 입력) 또는 선 `wire`(앞으로는 그 넷의 드라이버에서, `at`은 누른 점으로 `click`에 돌아온다)에서 신호가 가는 길. 시각·길이는 회로 단위의 거리다: 선을 따라 간 길이에 부품 30, 터널 점프 30, 서브회로 경계 20, 스플리터 10을 더한다(소수 한 자리). `backward`면 출처에서 누른 곳으로 흐르게 시각을 뒤집는다. `path`는 맨 위 회로에서 그 걸음의 회로까지 거친 서브회로 인스턴스 id들(맨 위는 `[]`), `cycle`은 상태 부품을 넘은 수(`throughRegisters`), `boundary`는 서브회로 경계를 지난 것이다. 끝점 `label`은 학생 라벨 먼저(`PC (D)`, `Splitter [31:26]`, 서브회로 안이면 `regfile › WD`). `activePathOnly`면 MUX·Demux·Decoder·Priority Encoder에서 선택이 정해진 가지만 건너고 정해지지 않은 부품은 `undetermined`에 든다. 값은 화면이 `sim.watch`로 보고 있는 회로 상태에서만 읽는다(그 회로를 보고 있지 않으면 값을 모르는 것으로: 모든 가지와 `undetermined`). 서브회로 안의 값은 원조가 이미 붙인 하위 상태만 따라가고 상태를 새로 만들지 않는다. 같은 누름은 늘 같은 글자다.
- **`flow.activePath`**(v1 C-08, V-04, D-079, D-099): 화면이 보고 있는 회로 상태(`sim.watch`, 지난 사이클을 보이면 `record.view`가 바꿔 끼운 그 사이클의 상태)에서 MUX마다 선택 값이 정해졌으면 고른 데이터 입력(`input`)까지 오는 가지의 선분들. 그 넷을 내는 포트에서 그 입력까지의 가장 짧은 선 경로만이고(`Netlist.branch`), 내는 포트를 모르면 넷 전체다. 그 회로를 보고 있지 않으면 `watched:false`와 빈 목록이다.
- 필드 색(C-07)의 선은 `record.fieldPaths`(위 record 절)가 준다.
- **화면:** 캔버스 덧그림(`electron/src/renderer/canvas/overlays/`)이 이 답을 그린다. 선택은 캔버스의 `hcs:selection` 사건(`canvas/events.ts`), 도구는 `hcs:tool` 사건 또는 도구 모음에서 읽는다.

## 6. 확장

다른 경로 계산이 생기면 이 문서에 절을 더하며 늘린다. 메서드 이름은 `trace.*`·`flow.*`로 묶는다(`mips.*`와 기록 `record.*`는 5절, `diag.*`·`trace.origin`과 `flow.*`·`trace.influence`·`trace.net`은 5절 끝에 있다).

## 7. 수명: 시작, 끝, 다시 시작, 되살리기(N-04, D-142)

엔진은 Electron main 프로세스(`electron/src/main/engine.ts`, `engine-locate.ts`, `recovery.ts`)가 띄우고 지킨다. 실습실 규칙대로 학생이 저장한 파일 말고는 디스크에 아무것도 남기지 않는다.

### 시작

- 명령: `<java> [-Dhcs.bundledMips=<hcs-mips.jar>] [-XX:SharedArchiveFile=<런타임>/hcs-engine.jsa] -Xlog:disable -Xlog:all=warning:stderr -Djava.awt.headless=true -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -XX:-UsePerfData -XX:ErrorFile=<실행 폴더>/hs_err_pid%p.log -Djava.util.prefs.userRoot=<실행 폴더>/java-prefs -Djava.io.tmpdir=<실행 폴더>/tmp -jar hcs-engine.jar`. 작업 폴더도 이번 실행의 폴더(`<temp>/HallymCircuitStudio/run-<pid>-<시각>`, 끝난 뒤 지운다)다.
  - `-Xlog:…`: JVM 자신의 경고(통합 로그, 기본은 stdout)를 stderr로 보낸다. stdout에는 규약 줄만 나온다.
  - `-Dhcs.bundledMips`: `hcs-engine.jar` 옆의 `hcs-mips.jar`를 이름으로 준다(엔진이 제 코드 위치로 찾지 않는다. AppCDS와 함께일 때 코드 위치가 비는 경우를 피한다).
  - GC와 JIT는 JVM 기본값(G1, C1+C2)이다. `-XX:TieredStopAtLevel=1`은 상주 메모리를 줄이지만 긴 시뮬레이션(N Cycles)이 느려서 쓰지 않는다.
- java는 이 순서로 찾는다. `HCS_JAVA`(늘 먼저) → 설치본이면 **번들 런타임** `resources/runtime/bin/java(.exe)`만(PC에 깔린 다른 Java를 쓰지 않는다. 없으면 "Java 런타임이 없습니다: runtime/bin/java.exe"로 시작 실패) → 소스 트리면 `JAVA_HOME`, 그다음 PATH의 `java`. 우리 런타임(`bin/`의 부모에 `hcs-engine.jsa`가 있음)이면 그 AppCDS 아카이브를 준다.
- 번들 런타임: `./gradlew :engine:runtime` → `engine/build/runtime/`(이 OS용), `:engine:runtimeZip` → `engine/build/distributions/hcs-runtime-<os>-x64.zip`. jdeps가 찾은 모듈(`java.base, java.desktop, java.prefs, java.sql`)에 `jdk.charsets`(한국어 Windows의 시스템 문자 집합)·`jdk.unsupported`(Gson)를 더해 jlink `--strip-debug --no-header-files --no-man-pages --generate-cds-archive`로 만들고, 엔진을 한 번 돌려(새 파일·편집·저장·demo-datapath와 ref-mips 열기·사이클) AppCDS 아카이브 `hcs-engine.jsa`를 만든 뒤 hello와 MIPS 라이브러리를 쓰는 파일 열기로 확인한다. 설치본은 `resources/engine/`(두 jar)과 `resources/runtime/`에 둔다(`electron/tools/package.ts`).
- 준비: `engine.hello`에 답하면 준비됨이다(30초 안, 아니면 시작 실패 대화상자).

### 끝

- 앱을 끝낼 때: `engine.shutdown` → 엔진이 답하고 열린 파일을 닫은 뒤 코드 0으로 끝난다. 3초 안에 끝나지 않으면 main이 강제로 끝낸다.
- 화면이 사라져 끝날 때(stdin 닫힘·부모 끝남·stdout 닫힘)는 시한이 있다: 정리가 `-Dhcs.exitDeadlineMs`(기본 10000) 안에 끝나지 않으면(엔진 스레드가 바쁘거나 멈춤) 프로세스를 멈춘다(`Runtime.halt`). 그 안의 복구 파일 쓰기는 `-Dhcs.recoveryWriteMs`(기본 5000)까지. `-Dhcs.testHooks=true`면 시험용 `test.block {ms}`(엔진 스레드를 붙잡음)가 생긴다(배포본은 켜지 않는다).
- main 프로세스가 죽으면(작업 관리자, 충돌): 엔진의 stdin이 닫히고 엔진이 스스로 끝난다. Windows에서 main은 엔진을 `detached`로 띄운다: Node가 자식을 넣는 "부모와 함께 죽는" 작업 개체 밖이라, 엔진이 끝나기 전에 복구 파일을 쓸 수 있다(N-19). stdin이 닫히지 않아도 엔진은 시작할 때의 부모 프로세스가 끝나는 것을 지켜보다 끝난다(`Main.watchParent`, Windows CI에서 stdin만으로는 끝나지 않은 것을 보고 더했다. `-Dhcs.watchParent=false`로 끈다). 떠도는 java 프로세스가 남지 않는다.
- 엔진의 stderr(로그)는 main의 메모리에 마지막 40줄만 둔다. 파일로 쓰지 않는다. JVM 충돌 보고서(`hs_err`)는 실행 폴더에 떨어지고 실행 폴더와 함께 지워진다.

### 스스로 끝났을 때(충돌): 다시 시작

- 진행 중이던 호출은 모두 `EngineGone`("엔진이 멈췄습니다")으로 끝난다. 300 ms 뒤 다시 띄운다.
- **한도: 60초 안에 3번까지 다시 띄운다.** 60초 안에 네 번째로 끝나면 멈춘 채 둔다(`failed`: "엔진을 시작하지 못했습니다" 대화상자, [Try Again]은 처음부터 다시 띄우고 파일을 되살리되, 되살리는 중에 끝난 것으로 보아 저장한 상태로 연다).
- 다시 띄운 엔진의 `engine.hello`에는 `idFloor` = 화면이 본 id(파일·회로·부품·선) 번호 가운데 가장 큰 것을 준다. 창이 옛 부품 id를 들고 있다가 보내도 새 엔진의 다른 부품을 가리키지 않고 오류 1이 된다.
- 엔진이 hello에 답한 뒤에도 파일을 되살리는 동안 창에는 `restarting`으로 보이고, 창의 호출은 되살리기가 끝날 때까지 기다린다. 그동안 엔진의 알림은 창에 보내지 않는다(창이 모르는 부품의 변경분이다).

### 되살리기(디스크 없이)

main은 열린 파일마다 메모리에 **저널**을 든다(`recovery.ts`).
- 연 방법: 경로(+ 읽기 전용 여부, 그때 파일 내용의 SHA-256, 회로 이름→id) 또는 `file.new`. 저장하면 저장한 경로·내용·회로로 바뀌고 의도 목록을 비운다. 닫으면 지운다.
- 의도: 창이 보낸 `edit.*`와 `mips.load`(실행 이미지 불러오기, N-16) 가운데 엔진이 **답한** 것(오류 응답은 적지 않는다)을, 엔진이 답한 순서의 전역 번호와 함께 적는다. `sim.*`(시뮬레이션)은 파일을 바꾸지 않으므로 적지 않는다. 모델을 바꾸는 새 메서드가 `edit.` 밖에 생기면 `recovery.ts`의 `journaled`에 더한다. `edit.` 밖에서 적는 것은 `mips.load`와 `record.markPc`·`record.markRegisterFile`·`record.setRegisterMapping`(되돌리기 한 단계인 파일 표시, `MODEL_EDITS`, D-144)이다. 대응은 자리(`[x,y]`)로 적으므로 id를 바꿀 것이 없고, Mark as PC의 `componentId`는 위 규칙대로 부품으로 적는다.
- 부품 id: 새 엔진은 옛 id를 모르므로 의도의 id 매개변수(`ids`, `id`, `componentId`, `wire`; 새로 생기면 `ID_PARAMS`에 더한다)는 적을 때 **부품 자체**로 바꿔 둔다. 부품은 라이브러리·이름·위치·속성 전부, 선은 두 끝이다. main은 창에 간 `model.circuit` 응답과 `model.changed` 알림으로 모델의 사본(그림자)을 들고 있고, 의도는 그 응답을 읽는 순간(편집의 `model.changed`는 응답 뒤에 온다) 곧 편집 바로 앞의 모델로 적는다. 사본에 없는 id를 쓴 의도가 있으면 그 파일은 재생할 수 없는 것으로 표시한다(`notRecorded`).

엔진이 다시 시작하면:
1. 창의 호출을 붙잡는다.
2. 파일을 연 순서대로 다시 연다: `file.open {path, readOnly?, restore:{fileId, circuits}}` 또는 `file.new {restore}`. 파일·회로 id가 앞과 같으므로 창의 탭, 보던 회로, 보기(배율·위치)는 그대로다. 부품·선 id는 새것이라 창은 모델을 다시 묻는다.
3. 모든 파일의 의도를 **처음 답한 순서대로** 한데 섞어 재생한다(클립보드처럼 파일 사이에 걸친 것도 같은 순서). 부품은 새 모델에서 같은 부품을 찾아 id로 바꾼다(똑같은 부품이 한자리에 둘이면 하나씩 짝짓는다).
4. 창에 `engine:recovered`로 알린다: `restored`(다시 열고 의도를 모두 재생), `lost`(마지막으로 저장한 상태로 엶, 까닭), `closed`(다시 열지 못해 닫음), `crash`(어떻게 끝났는지, 마지막 로그), `attempt`. 창은 대화상자(사실만)와 띠를 보인다.

재생하지 못하면:
- 의도가 오류로 답하거나 부품을 찾지 못함 → 그 파일을 닫고 마지막으로 저장한 상태(새 파일이면 빈 새 파일)로 다시 연다(`replayFailed`).
- 되살리는 중에 엔진이 또 끝남 → 다음 시작은 재생 없이 저장한 상태로만 연다(`crashedAgain`). 되살리기를 끝내면 이 셈은 다시 0이다.
- 연 뒤 디스크의 파일이 바뀌었음(SHA-256이 다름) → 재생하지 않고 지금 디스크의 파일로 연다(`changedOnDisk`).
- 파일이 그 자리에 없거나 열리지 않음 → 탭을 닫는다(`closed`).
- **시뮬레이션 상태는 되살리지 않는다.** 새 엔진은 Reset 상태에서 시작하고, 대화상자와 띠가 그렇게 말한다.
- 저널은 앱 프로세스가 살아 있는 동안만 있다. 앱 자체가 죽었을 때는 아래 복구 파일이 맡는다(N-19).
- 복구 파일의 내용으로 연 파일(아래)은 그 복구 파일이 저널의 기준이다: 되살릴 때 복구 파일에서 다시 열고(`recovery:"recover"`), main이 그 복구 파일을 다시 쓸 때마다 저널을 비운다(그 뒤의 편집만 재생). 그사이 복구 파일이 바뀌었으면 저장한 파일로 연다(`changedOnDisk`). main이 되살리는 중에 닫는 파일은 `keepRecovery`로 닫아 복구 파일을 남긴다.

### 복구 파일(N-19, D-152)

앱 전체(Electron main)가 죽으면(작업 관리자, 전원) 메모리의 저널도 함께 사라진다. 그래서 학생이 **한 번이라도 저장한 파일**(디스크에서 열었거나 저장해서 경로가 있는 파일)에는 그 옆에 복구 파일 `<이름>.circ.hcs-recover`를 둔다. 실습실 규칙대로 앱 전역 자리(설정 폴더, 임시 폴더)에는 아무것도 쓰지 않는다. 한 번도 저장하지 않은 새 파일은 복구하지 않는다(어디에도 쓰지 않는다).

- **내용:** 그 순간 저장하면 쓰일 .circ 그대로다(원조 writer `LogisimFile.write`와 확장 정보. 새 부품을 안 쓴 파일은 원조와 바이트 호환, D-006). 상대 경로 라이브러리는 같은 폴더라 그대로 풀린다. 쓰기는 같은 폴더의 `<이름>.circ.hcs-recover.tmp`에 쓴 뒤 옮긴다(반쯤 쓴 복구 파일이 남지 않게).
- **쓰는 때:** main(`recovery-files.ts`)이 창의 편집(저널에 드는 의도) 뒤에 `file.recoverWrite`를 부른다: 마지막 편집 뒤 10초, 아직 쓰지 않은 첫 편집 뒤 60초가 넘지 않게, 또는 편집 30개마다. 엔진이 되살리는 중에는 기다린다. 그리고 엔진은 화면이 사라져 끝날 때(stdin 닫힘·부모 끝남) 저장하지 않은 파일마다 한 번 더 쓴다(`recoveryFiles`): main을 죽여도 엔진이 가진 마지막 편집까지 남는다. 앞의 쓰기는 둘이 함께 끝날 때(전원)를 위한 것이다.
- **지우는 때:** 저장(앞 경로와 새 경로 옆 모두), 닫기(학생이 저장하지 않고 닫기를 골랐다), 앱 정상 종료(`engine.shutdown`), 편집을 모두 되돌려 저장한 파일과 같아졌을 때(`file.recoverWrite`가 지움), Discard.
- **여는 때(main `openPath`):** 파일 옆에 복구 파일이 있고 그 파일보다 오래되지 않았으면(같은 시각 포함) 열기 전에 창이 묻는다(`RecoveryAsk`, 대화상자 "저장하지 않은 편집이 있습니다", 쓴 시각, 캐릭터 없음). Recover → `file.open {recovery:"recover"}`(저장하지 않은 편집, Ctrl+S가 학생 파일에 쓰고 복구 파일을 지운다), Discard → `file.open {recovery:"discard"}`, Esc → 열지 않고 복구 파일을 둔다. 파일보다 오래된 복구 파일은 묻지 않는다(그 뒤에 다른 프로그램이 파일을 저장했다): 이 파일을 편집하면 새 것으로 바뀌고, 저장하거나 닫으면 지워진다.
- 창은 경로를 넘기지 않는다: main이 물은 것의 id(`file:openRecovery`)로만 답한다.
