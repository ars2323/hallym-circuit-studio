# Hallym Circuit Studio 1.0.0

한림대학교 Micro-architecture 실습도구의 첫 공개 릴리스입니다. 수업에서 쓰는 Logisim 2.7.1을 포크해, 학생이 single-cycle MIPS를 설계하다 막히는 곳을 도구가 짚어 줍니다.

## 설치

- **Windows:** `hallym-circuit-studio-1.0.0-windows.zip`을 풀고 `HallymCircuitStudio.exe`를 실행합니다(Java·관리자 권한 불필요). 설치형은 `hallym-circuit-studio-1.0.0-windows.msi`.
- **원조 Logisim 2.7.1을 계속 쓰는 경우(트랙 A):** `hcs-mips-1.0.0-windows.zip`의 `hcs-mips.jar`를 Project › Load Library › JAR Library로 불러옵니다. `hcs-asm.exe`를 같은 폴더에 둡니다.
- **그 밖:** Java 21 이상에서 `java -jar hallym-circuit-studio.jar`.
- 안내: 학생용 `GUIDE-ko.md`, 조교용 `TA-GUIDE-ko.md`.

## 기능 요약

- **MIPS 부품(Hallym MIPS):** Instruction Memory, Data Memory, Stack, Console, Radix Probe. 32비트 주소 그대로, `.s`를 `Load .s`로 올리며 기계어는 QtSpim(Hallym MIPS Simulator)과 같다. .s를 고쳐 저장하면 자동 재로드.
- **진단(Messages):** 동작하지 않는 연결만 알린다 — 정적(연결 안 된 입력, 짝 없는 터널, 폭 불일치, 서브회로 포트, 클럭, 메모리 영역 겹침)과 동적(값이 정해지지 않은 쓰기, E 발생과 출처, 발진, 정렬·영역·스택 문제)을 사이클과 함께. 원인 한 곳, 학생이 붙인 이름, 사실과 위치까지만.
- **사이클 뷰:** 사이클 표, 뒤로 가기, Run Until, 레지스터·메모리·명령어 필드 탭, 활성 경로와 버스 값 표시, Console 탭.
- **편집기:** 파일 탭(분리·나란히 보기·복원), 확대·축소, 라벨 칩과 터널 색, 굵은 버스·비트 수·선 색 범례, 신호 그룹 색, 따라오는 배선과 뜻하지 않은 연결 막기, 영향 경로와 Signal Flow, Duplicate N·Align·Distribute, Auto Appearance와 Port Order, 서브회로 가져오기, 영역 메모, 미니맵, 검색과 명령 팔레트, Undo History, 단축키 설정, 자동 저장과 복구, 제출 zip, PNG·SVG·PDF 내보내기, 첫 실행 튜토리얼, About.

## 원조 Logisim 2.7.1과의 차이

- 시뮬레이션 엔진과 `.circ` 형식은 그대로다. 새 부품을 쓰지 않은 파일은 원조와 바이트 수준으로 같게 저장된다. 이 도구가 더하는 정보(터널 색, 신호 그룹, 영역 메모, 스플리터 팔 이름, 레지스터 파일 표시)는 `<hcs:ext>` 한 곳에 들어가고 원조는 건너뛴다.
- 이름은 원조 영어 그대로이고 설명 문장만 한국어다. Help › Tutorial은 원조 JavaHelp 튜토리얼 대신 창 튜토리얼이다(원조 튜토리얼은 User's Guide 안에 있다).
- Ctrl+0·Ctrl+1은 확대(전체 맞춤·100%)이고 도구 선택은 Ctrl+2~9다.
- 시뮬레이션이 꺼진 동안의 Tick Once·1 Cycle은 무시하고 알린다(원조는 전파 스레드가 공회전한다).

## 알려진 한계

- 도구는 동작하지 않는 회로만 알린다. 값이 흐르는데 결과가 틀린 회로는 판단하지 않는다.
- 원조 Register·플립플롭은 X를 담지 않으므로 "값이 정해지지 않은 쓰기"는 클럭 에지에서 알린다.
- 25% 배율에서는 라벨 칩이 회로에 비해 커서 제 선에서 떨어져 보일 수 있다(지시선 문턱 안).
- 나란히 보기는 창을 반씩 나눌 뿐 배율·스크롤은 각 창의 것이다. 반 폭 창에서는 도구 모음 오른쪽이 넘칠 수 있다.
- Windows 100%·150% 배율에서 확인했다. macOS·Linux는 Java 21 jar로만 제공한다.
- Verilog 내보내기는 이 릴리스 범위 밖이다.
