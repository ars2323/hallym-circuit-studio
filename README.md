# Hallym Circuit Studio

한림대학교 Micro-architecture 실습도구. 수업에서 쓰는 Logisim 2.7.1을 포크해, 학생이 single-cycle MIPS를 설계하다 막히는 곳을 도구가 짚어 준다.

- 작동하지 않는 회로의 원인 한 곳을 학생이 붙인 이름으로 알려 주는 진단
- 명령어 단위로 묶인 사이클 뷰와 뒤로 가기
- 32비트 주소를 그대로 쓰는 MIPS 메모리 부품과 [Hallym MIPS Simulator](https://github.com/ars2323/hallym-mips-simulator)의 .s 불러오기
- Logisim 2.7.1과 같은 시뮬레이션 엔진. 기존 .circ 과제가 그대로 열리고 결과가 같다

기획과 결정 사항은 [PLAN.md](PLAN.md), 작업 규칙은 [CLAUDE.md](CLAUDE.md), 결정 기록은 [docs/DECISIONS.md](docs/DECISIONS.md)에 있다.

## 다운로드와 빠른 시작

1. [Releases](https://github.com/ars2323/hallym-circuit-studio/releases)에서 `hallym-circuit-studio-<버전>-windows.zip`을 받아 풀고 `HallymCircuitStudio.exe`를 실행한다(Java·관리자 권한 불필요). 설치형은 `.msi`.
2. 왼쪽 목록 **Hallym MIPS**의 Instruction Memory를 놓고 **Load .s**로 어셈블리 파일을 올린다. **1 Cycle**·**Run**으로 돌리고 **Cycle View**에서 사이클마다 값을 본다.
3. 아래 **Messages**는 동작하지 않는 연결만 알린다. 줄을 누르면 그 자리로 간다.
4. 원조 Logisim 2.7.1을 계속 쓰려면 `hcs-mips-<버전>-windows.zip`의 `hcs-mips.jar`를 Project › Load Library › JAR Library로 불러온다.

학생 안내 [docs/GUIDE-ko.md](docs/GUIDE-ko.md), 조교 안내 [docs/TA-GUIDE-ko.md](docs/TA-GUIDE-ko.md), 릴리스 노트 [docs/RELEASE-NOTES.md](docs/RELEASE-NOTES.md).

## 스크린샷

| | |
| --- | --- |
| ![동적 진단](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-d01-ex-origin-2/31a-dynamic-message.png) | ![사이클 뷰](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-d01-ex-origin-2/31c-message-clicked.png) |
| 값이 정해지지 않은 입력이 만든 E를 사이클·원인과 함께 알린다 | 메시지를 누르면 그 사이클의 회로와 원인 부품 |
| ![신호 그룹](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-e04-signal-groups-2/38a-signal-groups-full.png) | ![영역 메모](https://raw.githubusercontent.com/ars2323/hallym-circuit-studio/review-shots/2026-09-26-e08-area-memo/39a-area-memos-full.png) |
| 굵은 버스, 신호 그룹 색, 라벨 칩 | 영역 메모와 튜토리얼 등 편집기 개선 |

## 상태

로드맵 0~4단계(PLAN.md 9장)와 편집기 개선(11장)을 마쳤다. v1.0.0부터 공개 릴리스이고, v1.0.1은 첫 검토에서 나온 문제(새 파일의 Hallym MIPS, 진단 문구, 사이클 표 임시 줄, 활성 경로, 탭 이름, Signal Flow 호, 빈 캔버스 안내, PC 판별)를, v1.0.2는 첫 실행 창 크기와 좁은 창의 도구 모음·칸 비율을 고친 패치다.

| 단계 | 내용 |
| --- | --- |
| 0 | 기반: 2.7.1 소스, JAR 라이브러리 방식 확인, `hcs-asm` 어셈블러 |
| 1 | MIPS 부품 라이브러리 (트랙 A, 원조 2.7.1에서 불러 쓰는 JAR) |
| 2 | 포크 + 정적 진단 (트랙 B) |
| 3 | 기록 엔진 + 사이클 뷰 |
| 4 | 동적 진단 |

## 폴더

```text
vendor/logisim-2.7.1/   Logisim 2.7.1 원본 jar (수정 금지)
vendor/spim-9.1.24/     SPIM 9.1.24 원본 소스 (수정 금지)
native/hcs-asm/         SPIM 코어를 링크한 명령줄 어셈블러 (C++, BSD 코드와 분리)
lib-mips/               트랙 A: 원조 2.7.1용 MIPS 부품 JAR 라이브러리
app/                    트랙 B: Logisim 2.7.1 포크
assets/                 글꼴, 학교 식별요소 파생 파일
tests/circ/, tests/asm/ 엔진 회귀, 어셈블, 진단 테스트 입력
docs/                   결정 기록, 조사 결과, 설계 메모
```

`vendor/` 원본이 바뀌지 않았는지는 `tools/verify-vendor.sh`로 확인한다.

## 라이선스

Logisim 2.7.1을 따라 GNU GPL 버전 2 이상으로 배포한다([LICENSE](LICENSE)). SPIM(BSD)은 별도 실행 파일 `hcs-asm`으로만 쓰며 GPL 코드와 섞지 않는다. 서드파티 라이선스와 학교 식별요소 사용 조건은 [NOTICE](NOTICE)에 있다.
