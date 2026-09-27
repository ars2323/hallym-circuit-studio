# Hallym MIPS v2.4.0 실행 이미지 골든

Hallym MIPS 명세(`docs/hmx-format.md`)의 "Test files" 일곱 쌍(`.s`, `.hmx`)을 그대로 받아 두었다. 내용은 고치지 않는다(D-138).

- **출처:** `ars2323/hallym-mips-simulator`, 태그 `v2.4.0`(태그 객체 `3cbc26254a259ce6287c82e45e052ca19c912cc2`, 커밋 `b3117c701da6a17c22ab6af778ce97e69745ede8`), 경로 `electron/tests/hmx/<이름>.s`, `<이름>.hmx`.
- **주소:** `https://raw.githubusercontent.com/ars2323/hallym-mips-simulator/v2.4.0/electron/tests/hmx/<이름>.<s|hmx>`
- **받은 날:** 2026-09-28.
- **라이선스:** BSD 3-Clause, 같은 폴더의 `LICENSE`(Hallym MIPS v2.4.0의 `LICENSE` 그대로). 시험 자료이고 배포하는 프로그램에는 들어가지 않는다(NOTICE).

| 이름 | 명세가 말하는 것 |
| --- | --- |
| branches | `beq`, `j`, `jal`, `jr`: 반복문과 호출 |
| data | `la`·`lw`로 읽는 `.data`(명세의 예) |
| main-later | 함수 뒤의 `main`: entry가 시작 코드 다음 첫 워드가 아님 |
| pseudo | 의사 명령어가 펼쳐진 워드 |
| no-data | `.data` 구간 없음 |
| space-gap | `.space 4096`과 끝의 `.space 64`: `zero` 줄, 마지막 `.space`까지의 데이터 |
| no-handler | 예외 처리기 없이 어셈블: 시작 코드 없음, 프로그램 자신의 `__start`가 entry |

## 오라클 파일(`<이름>.regs`)

각 `.s`를 끝까지 돌린 레지스터 32개, `hi`, `lo`, Console 글이다. `vendor/spim-9.1.24`를 빌드한 원본 SPIM으로 만들었다. 이 SPIM의 `CPU/` 28개 파일은 Hallym MIPS v2.4.0의 `CPU/`와 바이트까지 같다(git blob 해시 비교, v2.4.0에는 출처 메모 `ORIGIN.md`만 더 있다). 여섯 쌍은 `spim -exception`, no-handler는 `spim -noexception`으로, 빈 환경에서 `load`·`run`·`print_all_regs hex` 명령으로 돌렸다(프로그램 인자 없음). 그래서 실행 스택에서 오는 값(`$sp` 0x7ffffff0, 시작 코드가 쓰는 `$a0`~`$a2`)은 파일 이름을 인자로 넘기는 Hallym MIPS(`reg $sp` 0x7fffffe4)와 다르다. 프로그램 자신이 쓰는 레지스터는 같다.

SPIM이 있는 동안(커밋 `5bde1ea`~`dfd8fd2`)은 `HallymMipsGoldenTest`가 매번 다시 만들어 같은지 봤다. `vendor/spim-9.1.24`는 D-141에서 지웠으므로 지금은 이 파일들이 오라클이고 다시 만들지 않는다(각 파일 머리 주석의 마지막 줄). 라이선스는 `tests/spim-oracle/LICENSE`(SPIM이 낸 출력).

## 시험(`lib-mips` `HallymMipsGoldenTest`)

1. 파서가 일곱 파일을 모두 읽고 명세 표의 설명과 맞는다. 원본 대조는 모두 "같음"이다.
2. 불러오기가 Instruction Memory·Data Memory에 넣은 것을 부품 출력으로 되읽으면 워드마다·바이트마다 이미지와 같고, 이미지 끝 뒤는 0이다.
3. 참조 CPU(`RefMips`, PC 시작 = entry, no-handler는 0x00400000으로 만든 같은 CPU)가 exit까지 돌린 레지스터와 Console 글이 오라클과 같다. 비교하는 레지스터는 프로그램 자신의 워드가 쓰는 것이다. 참조 CPU에 없는 명령(pseudo의 `div`·`mfhi`·`break`, space-gap의 `sb`)이 쓰는 레지스터는 비교하지 않고 빌드 로그에 "NOT compared"로 남긴다(pseudo의 `$s0`). 건너뛴 검사는 통과가 아니다.
