# 굳혀 둔 원본 SPIM 결과(D-141)

`vendor/spim-9.1.24`와 hcs-asm을 지우기(사용자 결정, D-141) 전에, SPIM을 직접 돌려 비교하던 시험의 기대값을 이 폴더에 파일로 굳혀 두었다. 만든 때: 2026-09-28, 기준 main 커밋 `dfd8fd2`(D-140 머지 직후). SPIM은 `vendor/spim-9.1.24`(SVN r764, 수정 없음)를 `make -C native/hcs-asm oracle`로 빌드한 원본 명령줄 `spim`이다. 고치지 않고 다시 만들지 않는다. 라이선스: 같은 폴더의 `LICENSE`(SPIM이 낸 출력, BSD 3-Clause).

| 파일 | 무엇 | 만든 방법 | 쓰는 시험 |
| --- | --- | --- | --- |
| `run/<이름>.regs` | `tests/mips/<이름>.s` 다섯 개를 돌린 끝의 레지스터 32개, `hi`, `lo`, `.data` 워드(`mem <주소> <워드>`), Console 글 | 빈 환경에서 `spim -exception`, 명령 `load "<이름>.s"`, `run 0x00400024`(main부터), `print_all_regs hex`, 이미지 `tests/hmx/mips/<이름>.hmx`의 `.data` 워드마다 `print <주소>`. `$a1`·`$a2`·`$gp`는 SPIM의 실행 스택 값이라 프로그램이 쓸 때만 비교한다(빈 환경이 아니면 `$a2`만 달랐다) | `RefMipsTest.programsMatchSpim`, `LegacyStackFileTest` |
| `dump/<이름>.txt` | `tests/asm/*.s`, SPIM의 `Tests/tt.core.s`·`Tests/tt.alu.bare.s`의 텍스트 세그먼트 목록(주소, 워드, `;` 원래 줄 주석 앞까지의 글) | `spim -noexception -dump -file <프로그램>`이 쓴 `text.asm`에서 `[0x…]\t0x…  글` 줄만. 어셈블 오류로 텍스트가 없던 `syntax-error.s`는 파일이 없고, `undefined-label.s`(어셈블 오류 시험, 입력은 지웠다)는 SPIM이 올린 세 줄이 남아 있다 | `SpimDumpListingTest`(5825줄, 부동소수점 명령 포함) |
| `memory-layout.txt` | 합친 Data Memory가 따르는 SPIM 메모리 배치 값(`DATA_BOT`, `DATA_LIMIT`, `STACK_TOP`, `STACK_LIMIT`, 초기 `$sp`, `$gp`, 사용자 `.data` 시작)과 소스 자리(파일:줄) | `MergedDataMemoryTest`가 소스의 `#define`과 식을 읽던 값(D-140) | `MergedDataMemoryTest.newPartRegionsAreSpimDataAndStackSegments` |

같은 때 굳힌 다른 SPIM 결과: 디스어셈블러 골든 `tests/disasm/`(README), Hallym MIPS 골든의 오라클 `tests/hmx/hallym-mips-v2.4.0/*.regs`, 시험용 실행 이미지 `tests/hmx/asm`·`mips`·`record`와 hcs-asm JSON `tests/asm/*.json`(`tests/asm/README.md`).
