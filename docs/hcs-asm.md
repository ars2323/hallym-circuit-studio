# hcs-asm 명령줄 어셈블러

`native/hcs-asm/`은 수정하지 않은 SPIM 9.1.24 코어(`vendor/spim-9.1.24/CPU/`)를 링크해 `.s`를 어셈블하고 결과를 JSON 한 덩어리로 낸다. MIPS 부품의 ".s 불러오기"(#15)가 이 도구를 별도 프로세스로 실행한다. SPIM(BSD)과 GPL 코드를 섞지 않기 위해 이 디렉터리는 BSD 3-Clause다(D-009).

## 빌드와 테스트

```sh
make -C native/hcs-asm          # build/hcs-asm, build/exceptions.s
make -C native/hcs-asm test     # 골든 비교 + 원본 spim 오라클 비교
```

g++, make, bison, flex가 필요하다. `parser.y`와 `scanner.l`은 `build/` 안에서 생성하고 `vendor/`에는 아무것도 쓰지 않는다.

## 사용법

```text
hcs-asm [options] <file.s>
  -pseudo / -nopseudo     의사 명령어 허용(기본: 허용)
  -exception / -noexception   예외 처리기를 먼저 올림 / 올리지 않음(기본)
  -exception_file <f>     예외 처리기 파일(기본: 실행 파일 옆 exceptions.s)
  -version
```

종료 코드: 0 성공, 1 어셈블 오류(JSON은 그대로 나옴), 2 사용법·파일 오류(stderr).

## 출력

```json
{
  "tool": "hcs-asm 0.1.0",
  "spim": "Version 9.1.24 of August 1, 2023 (final)",
  "settings": {"bare_machine": false, "accept_pseudo_insts": true, "exception_handler": false, "delayed_branches": false},
  "entry": "0x00400000",
  "text": [
    {"addr": "0x00400000", "word": "0x3c041001", "line": 7, "source": "la   $a0, msg        # load address"}
  ],
  "data": [
    {"addr": "0x10010000", "word": "0x000a6968"}
  ],
  "labels": {"main": "0x00400000", "msg": "0x10010000"},
  "errors": [{"line": 4, "message": "syntax error", "context": "addi  $t0, $t0,"}],
  "warnings": [{"line": null, "message": "main is at 0x00400004, not at the start of .text 0x00400000"}]
}
```

| 필드 | 내용 |
| --- | --- |
| `settings` | 어셈블에 쓴 설정. 확장 기계와 지연 분기 끔은 늘 QtSpim 기본값 그대로다 |
| `entry` | `main` 라벨 주소. 없으면 `null` |
| `text` | 사용자 텍스트 세그먼트의 모든 워드. 주소·값은 `0x` 16진수 문자열(8자리) |
| `text[].line`, `source` | 원래 소스 줄 번호와 원문(주석 포함). 의사 명령어가 여러 워드로 펼쳐지면 모든 워드가 같은 줄을 가진다 |
| `text[].handler` | `-exception`일 때 예외 처리기의 시작 코드(`__start`) 워드. `line`·`source`가 없다 |
| `data` | `.data`의 첫 주소(`0x10010000`)부터 마지막으로 쓴 워드까지 빠짐없이(0 포함). `$gp` 영역(`0x10000000`~)은 0이 아닌 워드만 |
| `labels` | 입력 파일이 정의한 모든 라벨(로컬 포함). 이름 순 |
| `errors` | 어셈블 오류. 파서는 첫 문법 오류에서 멈춘다. `line`은 SPIM이 보고한 번호다. 줄 끝에서 난 오류는 SPIM이 다음 줄 번호를 보고하므로 `context`(파서가 읽던 원문 줄)를 함께 준다 |
| `warnings` | 커널 세그먼트(`.ktext`)가 있음, `main`이 없음, `main`이 `.text` 처음이 아님 |

커널 세그먼트(`.ktext`, `.kdata`)는 내보내지 않는다. 바이트 순서는 SPIM과 같은 리틀 엔디언이다(`"hi\n"` → `0x000a6968`).

## 시작 코드 없이 올릴 때 SPIM이 두는 것

실제로 확인한 결과(`tests/asm/with-handler.s`):

- **예외 처리기 없음(기본):** 사용자 `.text`가 `0x00400000`부터 그대로 놓인다. 시작 코드도 커널 세그먼트도 없다. `main`이 첫 줄이면 `entry`는 `0x00400000`이다.
- **예외 처리기 있음(`-exception`, QtSpim·Hallym MIPS 기본값):** `exceptions.s`의 `__start` 시작 코드 9워드가 `0x00400000`~`0x00400020`에 먼저 놓이고 `main`은 `0x00400024`다.

## 기계어는 QtSpim 그대로 (D-010, 사용자 확정)

hcs-asm은 QtSpim·Hallym MIPS 기본 설정(확장 기계, 지연 분기 끔, 의사 명령어 켬)으로 SPIM 코어가 만든 기계어를 **그대로** 낸다. 인코딩을 고르거나 바꾸는 옵션은 없다. 분기 목적지를 어떻게 계산할지는 학생 데이터패스의 몫이고, 도구는 주소에 해당하는 워드를 내보낼 뿐이다.

참고 사실(0단계 조사): 지연 분기를 끈 SPIM은 분기 오프셋을 `(목적지 − PC) / 4`로 인코딩한다. 교재는 `(목적지 − (PC+4)) / 4`로 설명한다. 어셈블 단계에서 `delayed_branches` 설정이 바꾸는 것은 이 오프셋 하나다(`CPU/sym-tbl.cpp:264`). 예: `tests/asm/branches.s`의 `0x0040000c: bne $t0, $t1, loop`(목적지 `0x00400008`)는 `0x1509ffff`다.

## 테스트

`native/hcs-asm/tests/check.py`가 두 가지를 확인한다.

1. **골든:** `tests/asm/<이름>.s`(+ `<이름>.flags`)의 출력이 `tests/asm/<이름>.json`과 글자 단위로 같다. 산술, lw/sw, 앞·뒤 분기, j/jal/jr, 의사 명령어, `.data` 문자열·지시어, 문법 오류, 미정의 라벨, `main` 위치 경고, 예외 처리기를 다룬다. 출력을 바꿨으면 `python3 native/hcs-asm/tests/check.py --update`로 다시 만들고 diff를 검토한다.
2. **오라클:** 오류 없는 모든 입력과 SPIM 원본 테스트 프로그램(`helloworld.s`, `Tests/tt.core.s` 등 6개)을 수정하지 않은 원본 `spim` 명령줄(`build/oracle/spim -dump`, 배포하지 않음)로도 어셈블해 텍스트·데이터 워드가 모두 같은지 본다.
3. **QtSpim GUI 출력:** Hallym MIPS 저장소에 있는 원본 QtSpim GUI의 Save Log File 골든(`tests/asm/qtspim/`, `helloworld.s`와 `tt.core.s`의 사용자 텍스트 약 4700워드)을 `hcs-asm -exception` 출력과 주소·워드 단위로 비교한다. 분기를 포함해 모든 명령이 같다.
