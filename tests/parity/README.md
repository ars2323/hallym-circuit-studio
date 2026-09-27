# 편집 동등성 골든(N-01)

Hallym Circuit Studio 2는 화면을 Electron으로 새로 만들고, 편집은 화면이 **의도**를 보내 Java 엔진이 Logisim의 편집 코드로 한다(D-132, D-133, `docs/engine-api.md`). Swing 화면을 지우기(N-27) 전에, 지금의 Swing 앱이 같은 편집에서 무엇을 저장하는지 여기에 고정해 둔다. N-09에서 v2 엔진이 같은 의도를 실행해 같은 .circ를 만들어야 한다(D-136).

## 파일

| 파일 | 뜻 |
| --- | --- |
| `<장면>.intents` | 의도. 한 줄에 JSON 객체 하나. `#`으로 시작하는 줄과 빈 줄은 설명이다. |
| `<장면>.circ` | 골든. Swing 앱이 그 의도를 실행하고 File › Save로 저장한 파일(원조 XmlWriter + v1의 hcs:ext). |
| `inputs/` | 장면이 여는·불러오는·가져오는 파일. `adders.circ`(main, half_adder, full_adder, parity3)는 `adders.intents`로 앱에서 만든 뒤 고정했고, `ext-sample.circ`는 장면 13의 골든을 복사해 고정했다. |

## 의도 형식

```
{"method":"edit.setToolAttr","lib":"Gates","name":"AND Gate","attr":"inputs","value":"3"}
{"method":"edit.addComponent","lib":"Gates","name":"AND Gate","loc":[260,100],"as":"and3"}
{"method":"edit.addWire","points":[[100,100],[150,100],[150,200]]}
{"method":"edit.move","ids":["and3","wire:120,100"],"dx":20,"dy":0}
```

의도 하나는 원조 되돌리기 기록의 한 단계다(원조가 합치는 경우는 아래 "알아 둘 동작"). 값을 바꿔 놓을 때는 Swing처럼 먼저 도구 속성을 바꾸고(`edit.setToolAttr`) 놓는다(`edit.addComponent`에는 `attrs`를 쓰지 않는다, D-136).

- `method`는 `docs/engine-api.md`의 JSON-RPC 메서드 이름이고, 나머지 키가 그 params다. `fileId`는 없고, `circuitId` 대신 회로 **이름**을 쓴다: `circuit`은 그 의도를 하는 회로(화면에 보이는 회로, 없으면 지금 회로), 회로 전체에 하는 의도(`setCircuitAttr`, `portOrder`, `autoAppearance`, `setMainCircuit`)는 대상 회로를 `target`으로 적는다.
- 좌표는 Logisim 논리 좌표(격자 10)다. 화면 픽셀이 아니다. 배율은 `view.zoom`(화면 상태, 엔진은 아무것도 하지 않는다)으로만 바꾼다.
- 부품은 이렇게 가리킨다(화면 픽셀로 가리키지 않는다).
  - 기호: 앞의 `edit.addComponent`가 `"as"`로 붙인 이름. 기호는 Logisim이 부품을 새 객체로 바꿔도 따라간다: 원조 `ReplacementMap`의 바꿔치기(옮기기·되돌리기)와, 한 변경에서 지워지고 놓인 같은 종류·같은 속성의 부품 한 쌍(정렬처럼 지우고 새로 놓는 편집). 짝이 둘 이상이면 따라가지 않으므로 그런 부품은 라벨로 가리킨다.
  - `label:글` — 지금 회로에서 라벨(`label` 속성)이 그 글인 부품 하나.
  - `at:x,y` 또는 `at:x,y/부품 이름` — 자리(`loc`)가 그 점인 부품 하나(붙여넣은 사본, 연 파일의 부품).
  - `wire:x,y` — 그 점을 지나는 선 하나, `wire:x0,y0,x1,y1` — 두 끝이 그 점인 선. 원조가 선을 합치고 나누므로 선에는 기호를 붙이지 않는다.
  - 하나가 아니면(없거나 여럿) 그 장면은 실패한다.
- 시작 상태: 첫 줄이 `file.open`이면 그 파일(`tests/parity` 기준 경로)을 File › Open으로 연다. 아니면 원조 기본 템플릿(File › New가 쓰는 `default.templ`)을 `<장면>.circ`로 한 번 저장해 둔 파일을 연다. 끝에 File › Save로 저장한 파일이 결과다.
- 작업 폴더에는 `<장면>.circ` 옆에 `inputs/`와 번들 `hcs-mips.jar`가 있다. 그래서 골든의 라이브러리 경로는 `file#inputs/adders.circ`, `jar#hcs-mips.jar#kr.ac.hallym.hcs.mips.MipsLibrary`(원조 2.7.1이 여는 모양, D-096)다.

## 의도와 Swing 경로

하네스는 `app/src-test/kr/ac/hallym/hcs/app/parity/`(`SwingReplayer`)다. 도구가 하는 일을 다시 짜지 않고, 실제 창(Frame)에서 사람이 하는 길을 그대로 탄다. 원조 도구는 캔버스에 **합성 마우스 사건**(논리 좌표 × 배율 + 원점 = 캔버스 점, 원조 `Canvas`가 배율로 나눠 도구에 넘긴다)을 보내고, 메뉴 항목은 그 항목이 부르는 코드를 부르며, 뜨는 대화상자는 `Dialogs`가 칸을 채우고 단추를 누른다.

| 의도 | 받는 것 | Swing에서 하는 일 |
| --- | --- | --- |
| `edit.addComponent` | `lib`(없으면 이 파일의 회로), `name`, `loc`, `as?` | 부품 목록에서 도구를 고르고(`proj.setTool`) AddTool에 누르기·놓기. 부품은 그 도구의 속성으로 놓인다. 놓은 뒤 원조처럼 Edit Tool로 바뀌고 놓은 부품이 골라진다. |
| `edit.setToolAttr` | `lib`, `name`, `attr`, `value` | 부품 목록에서 도구를 고르고 속성 표(`AttrTableToolModel` 행)에서 값 바꾸기(ToolAttributeAction, 되돌리기 한 단계). 값은 도구에 남아 다음 놓기에도 쓰이고 `<lib><tool>`에 저장된다. 이미 같은 값이면 아무것도 하지 않는다 |
| `edit.addWire` | `points`(곧은 선 2점, ㄱ자 3점: 가운데가 꺾이는 점) | Wiring Tool로 첫 점을 누르고 꺾이는 점을 지나 끝점에서 놓기(원조 합치기·나누기·줄이기·수리) |
| `edit.select` | `ids?`, `rect?`, `add?`, `filter?` | `ids`: 다른 것을 누를 때처럼 떠 있는 것을 내려놓고(dropAll) 하나씩 고른다(`add`면 Shift로 더 고르기, `[]`면 비우기). `rect`: Edit Tool로 빈 곳을 눌러 끌어 사각형 고르기. `filter`: 여러 개를 고른 우클릭 Only Components / Only Wires(`ArrangeActions.filter`) |
| `edit.move` | `ids?`, `dx`, `dy`, `connect?` | (고른 뒤) Edit Tool로 고른 것 안을 눌러 끌어 놓기. 원조 MoveGesture + v1 따라오는 선(SafeMove). 선 하나만 골라 수직으로 끌면 v1 선분 끌기(SegmentDrag). `connect:false`는 끄는 동안 Shift(원조 Keep Connected 반대) |
| `edit.delete` · `edit.copy` · `edit.cut` · `edit.duplicate` | `ids?` | (고른 뒤) Edit 메뉴(LayoutEditHandler). 복제는 v1 SafeDuplicate |
| `edit.paste` | — | Edit › Paste. 사본은 떠 있는 선택이고, 다른 것을 고르거나 저장할 때 내려놓는다 |
| `edit.setAttr` | `ids?`, `attr`, `value` | (고른 뒤) 속성 표(선택 속성 모델)의 그 행에 글 넣기. 여러 개면 함께 가진 속성만 |
| `edit.setCircuitAttr` | `target`, `attr`, `value` | 회로 속성 표(`AttrTableCircuitModel`): 이름 `circuit`, 라벨 `clabel` 등 |
| `edit.undo` · `edit.redo` | — | Edit › Undo(원조 `Project.undoAction`), Edit › Redo(v1 RedoStack) |
| `edit.createCircuit` | `name` | Project › Add Circuit…, 이름 창 |
| `edit.setMainCircuit` | `target` | Set As Main Circuit |
| `edit.duplicateN` | `ids?`, `count`, `direction`(`right`·`down`·`left`·`up`), `spacing?`, `number?` | 우클릭 Duplicate N… 창을 채우고 OK |
| `edit.align` · `edit.distribute` | `ids`, `mode`(`left`·`centerX`·`right`·`top`·`centerY`·`bottom`) · `axis`(`h`·`v`) | 여러 개를 고른 우클릭 Align ›·Distribute › |
| `edit.portOrder` | `target`, `order`(`{west:[포트 이름…], east:…}`), `confirm?` | 인스턴스 우클릭 Port Order…: 변마다 ▲로 순서를 맞추고 Apply, 끊어질 연결 확인 창에서 Apply(`confirm:false`면 Cancel) |
| `edit.autoAppearance` | `target`, `confirm?` | 인스턴스 우클릭 Auto Appearance(끊어질 연결 확인 창) |
| `edit.importCircuits` | `path`, `circuits` | File › Import Subcircuits…: 파일 선택 창, 가져올 회로 표시, 계획 창 Apply |
| `edit.loadLibrary` | `kind`(`builtin`·`circ`·`jar`), `name?`·`path?` | Project › Load Library › Built-in(목록)·Logisim(파일 선택)·JAR(파일 선택) |
| `edit.unloadLibrary` | `name` | 부품 목록의 라이브러리 우클릭 Unload Library |
| `edit.tunnelColor` | `id`, `color?`(팔레트 색 `#rrggbb`, 없으면 Auto) | 터널 우클릭 Tunnel Color › |
| `edit.signalGroup` | `wire`, `group?`(`control`·`data`·`address`, 없으면 None) | 선 우클릭 Signal Group › |
| `edit.areaMemo` | `at`, `ids?`, `text?`, `color?`, `bounds?`, `delete?` | 빈 곳 우클릭 Add Area Memo…(고른 것을 감싼 상자에서 시작, 창에서 글·색·자리·크기) 또는 메모 안 우클릭 Delete Area Memo |
| `edit.splitterEdit` | `id`, `ranges`, `names?`, `lsbTop?` | 스플리터 우클릭 Edit Splitter…: 비트 범위 글·팔 이름·LSB 위를 채우고 Apply |
| `edit.splitterSplit` | `wire`, `at`, `ranges`, `names?`, `lsbTop?` | 여러 비트 선 우클릭 Split Bits Here…(편집 창은 위와 같다) |
| `view.zoom` | `factor` | 화면 배율(모델은 그대로) |

## 비교 기준

골든과 저장 결과는 D-006 기준으로 비교한다: 줄 앞 공백·빈 줄과 한 `<circuit>` 안의 `<wire>`·`<comp>` 순서만 정규화하고(원조가 HashSet 순서로 쓴다), 나머지(요소·속성·값·lib 번호·도구 속성·hcs:ext)는 바이트 그대로 같아야 한다. 다시 쓰기 모드는 정규화한 내용이 같으면 파일을 건드리지 않는다(부품 순서만 다른 저장으로 골든이 흔들리지 않게).

## 확인과 다시 만들기

```
# GUI 검사(CI의 guiTest에 들어 있다): 장면마다 Swing 앱으로 실행해 골든과 비교
xvfb-run -a ./gradlew :app:guiTest --tests kr.ac.hallym.hcs.app.parity.EditParityGuiTest
# 골든 다시 쓰기(일부만: -Dparity.only=02-wires,03-move-following)
xvfb-run -a ./gradlew :app:guiTest --tests kr.ac.hallym.hcs.app.parity.EditParityGuiTest -Dparity.update=true
# 화면 없는 검사(:app:test): 의도 읽기, 장면마다 골든, 원조 2.7.1로 열기, 이 표, engine-api.md의 메서드
./gradlew :app:test --tests kr.ac.hallym.hcs.app.parity.ParityGoldensTest
```

실행한 .circ와 기록(의도마다 새로 생긴 되돌리기 단계, 놓은 부품의 포트 자리, 답한 대화상자, 캔버스 알림)은 `app/build/parity/<장면>.circ`, `.trace`에 남는다. 장면을 쓸 때 포트 좌표는 이 기록에서 고른다.

## 장면

"의도 수"는 `edit.*` 줄 수다(`file.open`, `view.zoom` 제외). "원조 2.7.1"은 골든을 원조 jar로 `-tty stats` 해 본 결과다(MIPS 부품을 쓰는 골든은 원조가 찾는 자리인 .circ 옆에 hcs-mips.jar를 둔다). 종료 코드 0이고 표준 오류에 오류가 없으면 "열림", 부품 수는 마지막 TOTAL 줄의 첫 수다. `ParityGoldensTest`가 이 표를 다시 계산해 맞춰 본다.

| 장면 | 의도 수 | 다루는 것 | 원조 2.7.1 |
| --- | --- | --- | --- |
| `01-place-parts` | 44 | 방향·속성으로 놓기: 게이트 입력 수·크기·부정 입력, 핀 폭·방향·출력, 상수, MUX 선택 비트, 레지스터, 터널, 스플리터, 클럭, 가산기, 프로브. 도구 속성이 남아 다음 놓기에 쓰이고 저장됨 | 열림 · 부품 17 |
| `02-wires` | 25 | 끝 합치기, T자 나누기, 교차는 잇지 않음, 겹침 합치기, 안쪽 선, ㄱ자(가로·세로 먼저), 줄이기, 끝에서 끝까지 끌어 지우기, 포트 위를 지나는 선(포트에서 나뉨), 포트에서 시작하는 선, OR 수리·AND 안 함 | 열림 · 부품 4 |
| `03-move-following` | 30 | 따라오는 선(세로·가로·대각선), 출력 핀 옮기기, Shift로 선 없이 옮기기, 두 부품 함께, 선 없는 부품, 다른 부품 포트를 지나지 않는 다리(부록 A.4) | 열림 · 부품 8 |
| `04-segment-delete` | 36 | 선분 끌기(가운데·다리), 지우기(부품·선·섞어서·사각형), 고르기 거르기(Only Wires), Shift로 더 고르기, 빈 선택 지우기 | 열림 · 부품 4 |
| `05-copy-paste-duplicate` | 25 | 복사·붙여넣기(떠 있는 사본 끌기·내려놓기), 잘라내기, 복제(SafeDuplicate), Duplicate N(라벨 번호, 방향·간격) | 열림 · 부품 17 |
| `06-align-distribute` | 31 | 위·왼쪽·오른쪽·가운데 맞춤, 가로·세로 같은 간격, 검사기(W-05)가 막는 정렬, 이어진 부품은 옮기지 않음 | 열림 · 부품 11 |
| `07-attributes` | 31 | 입력 수·방향·크기·라벨·데이터 폭, 여러 핀 한 번에, 종류가 다른 두 부품의 공통 속성, 선이 붙은 부품, MUX 선택 비트·자리, 상수 값, 회로 라벨·이름 | 열림 · 부품 8 |
| `08-subcircuit` | 43 | Add Circuit, 서브회로 만들기, 인스턴스 놓기·잇기, Port Order(끊어질 연결 확인), Auto Appearance, 아래를 향한 인스턴스, 회로 이름 바꾸기, Set As Main Circuit | 열림 · 부품 12 |
| `09-import-subcircuits` | 9 | Import Subcircuits(의존 회로 함께, 같은 이름에 번호), 가져온 회로 놓기 | 열림 · 부품 20 |
| `10-undo-redo` | 36 | 되돌리기·다시 실행 사슬, 새로 고치면 다시 실행 비움, 지우기·붙여넣기 되돌리기, 도구 속성 되돌리기, 도구 속성과 놓기의 두 단계 | 열림 · 부품 6 |
| `11-load-libraries` | 11 | Unload Library·Load Built-in(목록 끝으로), Logisim 라이브러리(.circ), JAR 라이브러리(hcs-mips.jar), 그 부품 놓기 | 열림 · 부품 5 |
| `12-mips-bundled` | 16 | 번들 Hallym MIPS에서 놓기(라이브러리 자동 추가), 되돌리기로 빠짐·다시 실행으로 들어감, MIPS 부품 속성, 선·옮기기 | 열림 · 부품 6 |
| `13-ext-colors-groups-memos` | 36 | 터널 색(팔레트·Auto), 신호 그룹(셋·None), 영역 메모(감싸기·자리 지정·한글과 특수 문자), 메모 지우기, 지운 터널·선의 항목이 저장 때 빠짐 | 열림 · 부품 4 |
| `14-splitter-editor` | 12 | Split Bits Here(팔 이름), Edit Splitter(MIPS R 형식 글, I 형식과 이름, LSB 위) | 열림 · 부품 4 |
| `15-zoom` | 13 | 200%·50%·150%에서 놓기·긋기·옮기기·사각형 고르기·선분 끌기 | 열림 · 부품 3 |
| `16-datapath-mix` | 36 | PC+4 인출 데이터패스: 레지스터·가산기·상수·클럭·Instruction Memory·터널·Split Bits Here·따라오는 선·되돌리기·터널 색·영역 메모·Duplicate N | 열림 · 부품 8 |
| `17-open-existing` | 9 | 서브회로가 있는 파일 열어 고치기: 선 가운데에 프로브 놓기, 옮기기, 지우기, 라벨·회로 이름 바꾸기, 인스턴스 놓기 | 열림 · 부품 20 |
| `18-open-ext` | 7 | hcs:ext가 있는 파일 열어 고치기: 터널 지우기(색 항목 빠짐), 메모 지우기·더하기, 색·그룹 바꾸기, 두 넷 잇기 | 열림 · 부품 2 |

## 알아 둘 동작(N-09 엔진이 맞춰야 할 것)

골든을 만들며 확인한 원조·v1 동작이다. 엔진이 원조 코드를 그대로 쓰면 저절로 맞지만, 하나라도 다시 짜면 여기서 어긋난다.

- **Wiring 도구 7개는 한 프로세스에서 공유된다.** 원조 `Wiring.ADD_TOOLS`가 static이라 Splitter·Pin·Probe·Tunnel·Pull Resistor·Clock·Constant 도구 속성은 같은 JVM에 열린 모든 파일이 함께 쓰고, 파일을 열 때 그 파일의 `<lib><tool>` 값이 덮어쓴다. 하네스는 장면마다 이 도구들을 처음 값으로 되돌린 뒤 파일을 연다(새로 띄운 앱과 같은 상태). v2 엔진은 한 프로세스가 여러 파일을 열므로 같은 현상이 생긴다(N-03·N-09에서 정할 것).
- **도구 속성은 남고 저장된다.** Swing에서 값을 바꿔 놓으려면 도구 속성을 바꾼다(`edit.setToolAttr`). 그 값은 도구에 남아 다음 놓기에도 쓰이고 `<lib><tool>`에 저장되며, 되돌리기 기록에 한 단계로 든다(01, 08, 10). 엔진의 `edit.addComponent`의 `attrs`(놓는 부품에만, D-134)는 Swing에 한 번의 동작으로 없어서 의도 파일에 쓰지 않는다. v2 부품 목록이 도구 속성을 원조대로 하려면(N-08 I-54) 엔진에 `edit.setToolAttr`가 있어야 이 골든과 같아진다.
- **되돌리기 단계:** 붙여넣고 끌고 내려놓은 것은 한 단계(원조 `shouldAppendTo`), 빈 선택에서 Edit › Delete도 한 단계(아무것도 지우지 않음), Duplicate N·정렬·Port Order·Auto Appearance·Import·확장 정보 바꾸기는 각각 한 단계.
- **놓기 거절:** 라벨까지 포함한 경계가 음수 좌표로 가면 원조가 놓지 않는다("Component cannot have negative coordinates.").
- **선:** 포트 위를 지나는 선은 포트 자리에서 나뉘어 이어진다. 끝에서 다른 끝까지 선을 따라 끌면 그 선이 지워진다(원조 줄이기). 몸통 안에서 끝난 선의 수리는 부품이 정한다(OR·NOR·XOR·XNOR는 포트까지로 줄이고 AND는 하지 않는다).
- **hcs:ext 항목의 속성 순서:** 새로 만든 항목은 코드 순서(`net`, `group`)로, 열었다 저장한 항목은 알파벳 순서(`group`, `net`)로 쓴다(13과 18). 이 차이도 골든에 들어 있다.
- **라이브러리 번호:** 뺐다 다시 넣은 기본 라이브러리는 목록 끝에 붙어 `lib` 번호가 바뀐다(11).
- **글꼴과 그림에 달린 모양:** 원조 터널의 경계는 **그려질 때** 라벨 글꼴 크기로 정해진다(`Tunnel.paintGhost`가 경계를 고친다). 그래서 터널 경계를 쓰는 편집(터널을 감싸는 메모, 사각형 고르기의 포함 판정, 터널 정렬, 붙여넣기·복제 자리)은 기계의 글꼴과 그 터널이 한 번이라도 그려졌는지에 따라 달라진다. 처음 CI에서 장면 13의 메모(터널 둘을 감쌈)가 로컬과 달라 알게 됐고, 장면은 그런 편집을 쓰지 않게 고쳤다(메모는 선을 감싼다). 그리지 않는 v2 엔진에서는 터널 경계가 늘 처음 어림값이므로 N-09에서 같은 편집을 더하면 어긋난다. v1 Auto Appearance·Port Order의 상자 폭도 글꼴 너비(회로 이름, 포트 이름)로 정해지고 10 단위로 올린다(08은 로컬과 CI가 같았다).
