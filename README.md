# Hallym Circuit Studio

한림대학교 Micro-architecture 실습도구. 수업에서 쓰는 Logisim 2.7.1을 포크해, 학생이 single-cycle MIPS를 설계하다 막히는 곳을 도구가 짚어 준다.

- 작동하지 않는 회로의 원인 한 곳을 학생이 붙인 이름으로 알려 주는 진단
- 명령어 단위로 묶인 사이클 뷰와 뒤로 가기
- 32비트 주소를 그대로 쓰는 MIPS 메모리 부품과 [Hallym MIPS Simulator](https://github.com/ars2323/hallym-mips-simulator)의 .s 불러오기
- Logisim 2.7.1과 같은 시뮬레이션 엔진. 기존 .circ 과제가 그대로 열리고 결과가 같다

기획과 결정 사항은 [PLAN.md](PLAN.md), 작업 규칙은 [CLAUDE.md](CLAUDE.md), 결정 기록은 [docs/DECISIONS.md](docs/DECISIONS.md)에 있다.

## 상태

개발 중이다. 이번 범위는 로드맵 0~4단계(PLAN.md 9장)다.

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
