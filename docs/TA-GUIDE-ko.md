# Hallym Circuit Studio 조교 안내

학생 안내(`GUIDE-ko.md`)에 더해, 실습 준비와 과제 관리에 필요한 것만 적습니다.

## 1. 배포

- **Windows zip**(`hallym-circuit-studio-<버전>-windows.zip`): JRE가 들어 있어 실습실 PC에 Java가 없어도 됩니다. 관리자 권한 없이 폴더에 풀어 실행합니다. 네트워크 드라이브보다 로컬 폴더가 빠릅니다.
- **MSI**: 사용자별 설치, 시작 메뉴·바탕화면 바로가기. `.circ` 파일 연결은 하지 않습니다(학생 PC의 원조 Logisim 연결을 건드리지 않기 위해).
- **트랙 A**(`hcs-mips-<버전>-windows.zip`): 원조 Logisim 2.7.1을 계속 쓰는 반을 위한 MIPS 부품 라이브러리(`hcs-mips.jar` + `hcs-asm.exe`). Project › Load Library › JAR Library로 불러옵니다. 자세한 것은 zip 안 `사용안내.md`(= `docs/track-a-guide.md`).
- 두 트랙의 `.circ`는 서로 열립니다. MIPS 부품을 쓴 파일은 원조에서 열 때 `hcs-mips.jar`가 .circ와 같은 폴더(또는 바로 위·아래)에 있어야 합니다.

## 2. 과제 템플릿 만들기

1. 새 파일에 부품·서브회로 틀을 그리고, 레지스터 파일 서브회로를 오른쪽 클릭 **Mark as Register File**로 표시해 두면 학생의 Cycle View › Registers에 바로 보입니다(파일에 저장됩니다).
2. IF/ID/EX 같은 영역은 **Add Area Memo…**로 상자와 메모를 둡니다. 신호 그룹 색(Signal Group)도 파일에 저장됩니다. 원조 2.7.1은 이 정보를 무시하고 회로만 엽니다.
3. 서브회로 포트 이름은 핀 라벨입니다. **Auto Appearance**로 이름이 들어가는 상자 모양을 만들고 **Port Order…**로 순서를 정합니다. 포트 자리가 바뀌면 인스턴스 연결이 끊어질 수 있다고 미리 알립니다.
4. 예제 .s는 `tests/mips/*.s`처럼 `.text`/`.data`로 두고, 과제 표준 설정(예외 처리기 없음, 지연 분기 끔, 의사 명령어 켬)을 학생에게 알립니다. 기계어는 QtSpim과 같습니다.

## 3. 제출물 다루기

- 학생은 **File › Create Submission…**으로 `.circ`+`.s`+라이브러리 zip을 냅니다. zip을 풀면 그대로 열립니다.
- 열어서 **Messages 0건**인지, Cycle View에서 몇 사이클 돌려 보는지가 첫 확인입니다. 도구는 결과의 정오를 판단하지 않으므로 값 비교는 조교가 합니다(Run Until… → 특정 PC나 사이클에서 레지스터·메모리 탭 확인).
- 여러 제출물은 탭으로 열고 **View Side by Side**로 나란히 봅니다. 다른 파일의 서브회로는 **Import Subcircuits…**로 가져와 비교할 수 있습니다.

## 4. 문제가 생겼을 때

| 증상 | 확인 |
| --- | --- |
| Load .s가 실패 | `hcs-asm.exe`가 `app/lib/`(패키지) 또는 `hcs-mips.jar` 옆(트랙 A)에 있는지. 어셈블 오류는 줄 번호와 함께 창에 나옵니다 |
| 원조 2.7.1에서 파일이 안 열림 | `hcs-mips.jar` 위치(위 1절). 파일에 MIPS 부품이 없으면 jar 없이도 열립니다 |
| 화면이 흐리거나 글자가 작음 | Windows 배율 100%·150%에서 확인했습니다. 그 밖의 배율은 `-Dsun.java2d.uiScale`로 조정할 수 있습니다 |
| 발진으로 Simulation Off | 회로를 고친 뒤 위 띠의 Turn On. 꺼진 동안의 1 Cycle은 무시되고 상태 표시줄에 알립니다 |
| 자동 저장 복구 | 시작할 때 남은 자동 저장을 보여 주고 Recover/Discard를 묻습니다. 복구한 창은 저장할 때 원래 파일을 미리 고릅니다 |

## 5. 개발자를 위한 자리

- 결정 기록 `docs/DECISIONS.md`, 진행 추적 `docs/PROGRESS.md`, 테스트 목록 `docs/TESTING.md`, 성능 `docs/PERFORMANCE.md`.
- 스크린샷은 `tools/screenshots/run.sh`로 찍고 `review-shots` 브랜치에 올립니다(`docs/SCREENSHOTS.md`).
- 고장 회로 모음 `tests/circ/faults/`는 진단 기대값 테스트입니다. 새 진단을 더하면 회로와 기대 메시지를 함께 더합니다.
