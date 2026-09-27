# hcs-asm (없어짐)

`hcs-asm`은 수정하지 않은 SPIM 9.1.24 코어(`vendor/spim-9.1.24/CPU/`)를 링크한 명령줄 어셈블러였다(`native/hcs-asm/`, BSD 3-Clause, D-009). v1의 ".s 불러오기"(#15)와 전환 기간의 `.s` 경로(D-126)가 별도 프로세스로 돌렸고, 디스어셈블러 골든(`-disasm`, D-127)과 원본 spim 오라클 비교에도 썼다.

**2026-09-28, 사용자 결정으로 지웠다(D-141).** Hallym MIPS 2.4.0이 실행 이미지 내보내기(Export executable image (.hmx))를 배포해, 이 도구는 `.s`를 받지 않고 `.hmx`만 불러온다. `native/hcs-asm/`, `vendor/spim-9.1.24/`, CI의 SPIM 빌드와 hcs-asm 릴리스 자산이 함께 없어졌다.

- **어셈블은 Hallym MIPS에서 한다.** Ctrl+S로 어셈블한 뒤 제목 줄 오른쪽 아이콘 묶음의 Export executable image (.hmx) 단추로 내보내고, 이 도구의 Load Program...으로 불러온다(docs/hmx.md).
- **SPIM이 낸 결과는 지우기 전에 굳혀 두었다.** 디스어셈블러 골든 `tests/disasm/`, 시험용 실행 이미지 `tests/hmx/asm`·`mips`·`record`, hcs-asm JSON `tests/asm/*.json`, 원본 spim 실행·목록·메모리 배치 `tests/spim-oracle/`, Hallym MIPS 골든의 오라클 `tests/hmx/hallym-mips-v2.4.0/*.regs`. 만든 방법과 커밋은 각 폴더의 README와 파일 머리 주석에 있다. 다시 만들지 않는다.
- **옛 소스는 git 기록에 있다.** 마지막으로 들어 있던 main 커밋은 `dfd8fd2`다(`git show dfd8fd2:native/hcs-asm/src/hcs-asm.cpp`). 명령줄 옵션(`-exception`, `-disasm` 등)과 JSON 출력 형식도 그 커밋의 이 문서에 있다.
