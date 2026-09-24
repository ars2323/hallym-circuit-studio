# 참조 single-cycle MIPS (테스트용)

`ref-mips.circ`는 MIPS 부품과 hcs-asm이 SPIM 실행과 같은 결과를 내는지 확인하는 **우리 테스트용** 회로다. 학생 과제의 정답이 아니다. 원조 2.7.1에서 열려면 `hcs-mips.jar`를 이 파일과 같은 폴더에 둔다.

- 생성: `lib-mips/src/test/.../RefMips.java`가 원조 API로 만든다. 부품 사이 연결은 모두 라벨 터널이다. 다시 쓰기: `./gradlew :lib-mips:test -Phcs.update=true`.
- 기계어는 QtSpim 그대로라(D-010) 분기 가산기는 PC 기준(PC + imm×4)이다.
- PC 레지스터는 `PC XOR 0x00400000`을 담아 리셋 때 PC가 `0x00400000`이다.
- 명령어: add addu sub subu and or xor nor slt sltu sll srl sra sllv srlv srav jr syscall mul, addi addiu slti sltiu andi ori xori lui lw sw beq bne bgez bltz, j jal.

| 프로그램 | 확인하는 것 |
| --- | --- |
| `sum.s` | 반복문, print_string·print_int·print_char |
| `factorial.s` | 재귀, `jal`/`jr`, 스택 push·pop, `mul` |
| `memory.s` | `.data` 배열 `lw`·`sw`, 한글 문자열 |
| `branches.s` | beq/bne 앞·뒤, blt/bge/bgt/ble, b(bgez), bltz/bgez |
| `alu.s` | 산술·논리·시프트·즉값 명령 |

`RefMipsTest`가 각 프로그램을 회로(원조 엔진)와 원본 spim(`-noexception`, `run 0x00400000`)으로 돌려 Console 출력, 레지스터, `.data` 워드를 비교한다. spim이 실행 전에 채우는 `$a1`·`$a2`·`$gp`는 프로그램이 쓸 때만 비교한다.
