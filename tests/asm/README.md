# 어셈블리 시험 입력과 hcs-asm 출력(굳힘, D-141)

- **`*.s`**: 시험용 MIPS 프로그램(산술, lw/sw, 앞·뒤 분기, j/jal, 의사 명령어, .data 문자열, main이 처음이 아님, 자기 예외 처리기). 이 도구는 `.s`를 불러오지 않는다(D-141). 여기 `.s`는 굳힌 실행 이미지 `tests/hmx/asm/<이름>.hmx`의 원본(원본 대조 `source-sha256`)이고, 디스어셈블러 골든 `tests/disasm/asm-<이름>.txt`와 원본 spim 목록 `tests/spim-oracle/dump/asm-<이름>.txt`의 입력이다. 고치면 원본 대조가 "바뀜"이 되어 시험이 실패한다.
- **`*.json`**: 지금은 없어진 hcs-asm(SPIM 9.1.24 코어)의 JSON 출력이다(`text[{addr, word, line, source}]`, `data`, `labels`, `settings`). 명령줄 기본값(예외 처리기 없음, main이 `0x00400000`)으로 만들었고 `with-handler.json`만 `-exception`이다. vendor/spim이 있는 동안 원본 spim `-dump`와 QtSpim 창 글(`qtspim/`)에 대조해 같았다. 워드마다 원래 줄이 있어 `MipsTextTest`(포크 앱의 디스어셈블이 원래 줄과 같은지)가 읽는다. 2026-09-28 기준 main `dfd8fd2`에서 굳혔고 다시 만들지 않는다. 어셈블 오류 시험(`syntax-error`, `undefined-label`)과 `with-handler.flags`는 어셈블러와 함께 지웠다.
- **`qtspim/`**: Hallym MIPS에서 가져온 원본 QtSpim GUI 출력(README).
- 라이선스: `.json`은 SPIM이 낸 출력(`tests/spim-oracle/LICENSE`, NOTICE).
