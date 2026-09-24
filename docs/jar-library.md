# JAR 라이브러리 방식 확인 (0단계, #4)

트랙 A의 MIPS 부품은 원조 Logisim 2.7.1의 JAR 라이브러리로 배포한다. 이 문서는 그 방식이 실제로 되는지, `.circ`에 무엇이 저장되는지, 포크가 같은 파일을 어떻게 열지를 정리한다. 모든 항목은 `lib-mips/src/test/.../JarLibraryTest.java`가 매 CI에서 확인한다.

## 만드는 법

| 항목 | 내용 |
| --- | --- |
| 라이브러리 클래스 | `com.cburch.logisim.tools.Library` 하위 클래스. 인자 없는 public 생성자가 있어야 한다(`Class.newInstance()`로 만든다) |
| 부품 | `InstanceFactory` 하위 클래스를 `new AddTool(factory)`로 감싸 `getTools()`에서 돌려준다 |
| 저장 이름 | 라이브러리는 `Library.getName()` = 클래스 전체 이름, 부품은 `InstanceFactory` 생성자에 준 이름. 둘 다 `.circ`에 저장되므로 한 번 배포하면 바꾸지 않는다 |
| manifest | `Library-Class: kr.ac.hallym.hcs.mips.MipsLibrary`. GUI의 Project › Load Library › JAR Library에서만 쓴다. 없으면 클래스 이름을 묻는다 |
| 바이트코드 | Java 8(클래스 버전 52). `--release 8`로 컴파일한다 |
| 클래스 로더 | 2.7.1은 `URLClassLoader`가 아니라 자체 `ZipClassLoader`로 jar를 읽는다. jar 안에 다른 jar를 넣어도 읽지 못하므로 의존성은 풀어 넣어야 한다(외부 의존성 없는 단일 jar) |

## 원조 2.7.1에서 확인한 것

- 최소 라이브러리(`hcs-smoke.jar`, 32비트 입력에 1을 더하는 `Incrementer` 부품)를 쓴 회로를 원조 jar의 `-tty table`로 돌리면 `0x29 + 1 = 0x2A`가 나온다. 헤드리스로 돌아가므로 CI에서 GUI 없이 확인할 수 있다.
- JDK 8, 11, 17, 21 모두에서 원조 2.7.1이 이 jar를 불러 계산하고 저장한다.
- 부품 없는 `MipsLibrary`(`hcs-mips.jar`)도 불러온다.

## `.circ`에 저장되는 것

```xml
<lib desc="jar#hcs-mips.jar#kr.ac.hallym.hcs.mips.MipsLibrary" name="1"/>
...
<comp lib="1" loc="(200,100)" name="Instruction Memory"/>
```

`desc`는 `jar#<jar 경로>#<라이브러리 클래스>`다. 경로는 `LibraryManager.toRelative()`가 정한다.

| jar 위치(.circ 기준) | 저장되는 경로 |
| --- | --- |
| 같은 폴더 | `hcs-mips.jar` |
| 한 단계 아래 폴더 | `lib/hcs-mips.jar` |
| 한 단계 위 폴더 | `../hcs-mips.jar` |
| 그 밖 | 절대 경로(예: `C:\Users\…\Downloads\hcs-mips.jar`) |

그 밖의 위치에 두면 다른 PC에서 열 때 경로가 맞지 않는다. **학생에게는 jar를 .circ와 같은 폴더에 두라고 안내한다.** 과제 템플릿도 jar와 함께 배포한다.

## jar를 찾지 못할 때

- 원조 2.7.1은 `Loader.getFileFor()`에서 "라이브러리를 찾을 수 없다"는 메시지를 띄우고 파일 선택 창으로 jar를 고르게 한다. 고른 뒤 저장하면 새 경로가 저장된다.
- 헤드리스(`-tty`)에서는 이 대화상자 때문에 `HeadlessException`으로 끝난다.
- 명령줄 `-sub <원래 파일> <대체 파일>`은 원래 파일을 먼저 찾은 **뒤**에 적용되므로, 없는 jar는 `-sub`로도 구할 수 없다. 엔진 회귀 테스트에서는 jar를 .circ 옆에 복사해 둔다.

## 포크에서 같은 파일 열기 (D-007)

한 .circ가 원조 2.7.1과 포크 양쪽에서 열려야 한다.

- 저장된 경로의 jar를 읽을 수 있으면 원조와 똑같이 그 jar를 쓴다.
- 읽을 수 없고 클래스가 `kr.ac.hallym.hcs.mips.MipsLibrary`이면, 대화상자 대신 포크에 번들된 `hcs-mips.jar`로 연결한다. 저장할 때는 파일에 적혀 있던 설명자 문자열을 그대로 쓴다. 원조 2.7.1에서 다시 열 때 경로가 바뀌지 않게 하기 위해서다.
- 이것은 `.file`의 로딩 규칙을 건드리는 일이라 규칙 2.1의 "최소 패치 하나"로 `Loader.getFileFor()` 한 곳에 격리한다. 구현, `docs/engine-patches.txt` 등록, 회귀 테스트는 #22에서 한다.

## `.circ` 바이트 호환의 기준 (D-006)

원조 2.7.1도 같은 회로를 늘 같은 바이트로 저장하지 않는다.

- **JRE마다 공백이 다르다.** JDK 8은 머리 문구를 들여쓰지 않고, JDK 9 이후는 들여쓰고 빈 줄을 넣는다.
- **부품 순서가 해시 순서다.** `Circuit.comps`가 `HashSet<Component>`이고 부품은 `hashCode()`를 재정의하지 않는다. 순서가 객체 identity hash로 정해져 세션마다 달라질 수 있다. 선(`Wire`)은 좌표 해시라 같은 JDK에서는 일정하지만 JDK 버전마다 다르다.

그래서 규칙 2.3의 "바이트 수준 호환"은 **줄 앞 공백·빈 줄과 회로 안의 `wire`·`comp` 순서만 정규화한 뒤 바이트 단위로 같음**으로 검사한다. 요소·속성 이름, 속성 순서, 값, 라이브러리 번호, `options`·`mappings`·`toolbar`는 그대로 같아야 한다. 정규화는 테스트의 `CircNormalizer`가 한다.

`tests/jarlib/*.circ`는 원조 2.7.1을 JDK 8에서 돌려 저장한 실제 결과다.
