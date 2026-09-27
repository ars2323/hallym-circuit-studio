# 디스어셈블러 골든(D-127, 굳힘 D-141)

`*.txt`는 SPIM 9.1.24 자신의 명령어 출력 함수(`format_an_inst`)가 낸 목록이다. 지금은 없어진 `hcs-asm -disasm`(`vendor/spim-9.1.24/CPU`를 수정 없이 링크한 BSD 도구)이 각 파일 첫 줄의 명령으로 만들었다. 줄 모양: `label <주소> <이름>`, 그리고 텍스트 워드마다 `<주소> <워드> <글>`. `DisassemblerGoldenTest`가 Java 디스어셈블러(`kr.ac.hallym.hcs.mips.disasm`)를 한 줄씩 대조하고(규칙은 D-127), `HmxConsistencyTest`가 `asm-*`·`mips-*`·`record-*` 골든의 주소·워드·라벨을 굳힌 실행 이미지 `tests/hmx/<폴더>/<이름>.hmx`와 대조한다.

- **굳힘.** vendor/spim과 hcs-asm은 사용자 결정으로 지웠다(D-141). 그 전에는 CI가 골든을 매번 다시 만들어 저장소와 같은지 봤고(`tools/gen-disasm-golden.sh`, glibc·Windows qsort 순서 8가지 포함), 마지막으로 다시 만든 것은 2026-09-28 기준 main `dfd8fd2`다. `record-busy-loop.txt`, `record-overwrite-print.txt`는 그때 더했다(나머지는 한 글자도 바뀌지 않았다). 이제 다시 만들지 않고 고치지 않는다.
- **입력.** `keys.s`, `fields.s`, `quirks.s`는 생성기가 쓴 입력(워드·명령), `targets.s`는 손으로 쓴 입력이다. 프로그램 골든의 입력은 `tests/asm`, `tests/mips`, `tests/record`의 `.s`와 SPIM의 `helloworld.s`·`Tests/tt.core.s`(SPIM 소스와 함께 지웠다)다.
- **라이선스.** SPIM이 낸 출력이다: `tests/spim-oracle/LICENSE`(BSD 3-Clause), NOTICE.
