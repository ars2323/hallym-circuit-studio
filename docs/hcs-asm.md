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
  -asm / -bare            확장 기계(기본) / bare 기계(-nopseudo 포함)
  -pseudo / -nopseudo     의사 명령어 허용(기본: 허용)
  -branch pc4             분기 오프셋 = (목적지 − (PC+4)) / 4 (기본, 교재와 같음)
  -branch pc              분기 오프셋 = (목적지 − PC) / 4 (QtSpim·Hallym MIPS 기본값과 같은 비트)
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
  "settings": {"bare_machine": false, "accept_pseudo_insts": true, "exception_handler": false, "branch_offset": "pc+4"},
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
| `settings` | 어셈블에 쓴 설정. 같은 소스라도 `branch_offset`에 따라 분기 워드가 달라진다 |
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

## 분기 오프셋 (PLAN.md 10장 미결정 1번, D-010)

SPIM은 `delayed_branches` 설정에 따라 분기 명령의 16비트 오프셋을 다르게 인코딩한다. 어셈블 단계에서 이 설정이 바꾸는 것은 이것 하나뿐이다(`CPU/sym-tbl.cpp:264`).

| | 오프셋 인코딩 | 실행 | `jal`이 저장하는 `$ra` |
| --- | --- | --- | --- |
| SPIM, 지연 분기 끔(QtSpim·Hallym MIPS 기본) | (목적지 − PC) / 4 | 지연 슬롯 없음 | PC+4 |
| SPIM, 지연 분기 켬 | (목적지 − (PC+4)) / 4 | 지연 슬롯 실행 | PC+8 |
| 교재 single-cycle 데이터패스 | (목적지 − (PC+4)) / 4 | 지연 슬롯 없음 | PC+4 |

예(`tests/asm/branches.s`): `0x00400010`의 `bne $t0, $t1, loop`(목적지 `0x0040000c`)는 지연 분기 끔에서 `0x1509ffff`(오프셋 −1), 교재 정의로는 `0x1509fffe`(오프셋 −2)다.

교재 데이터패스는 인코딩은 "지연 분기 켬"과, 실행은 "지연 분기 끔"과 같다. 그래서:

- **과제 표준 설정(Hallym MIPS):** 예외 처리기 불러오기 끔, 지연 분기 끔, 의사 명령어 켬, bare 끔. 실행 의미가 학생 회로(지연 슬롯 없음, `$ra` = PC+4)와 같고 `main`이 `0x00400000`에 온다.
- **hcs-asm 기본값:** 같은 설정으로 어셈블하되 분기 오프셋만 교재 정의(`-branch pc4`)로 인코딩한다. 교재대로 만든 데이터패스가 그대로 실행할 수 있는 기계어다.
- **결과:** 분기 명령(`beq`, `bne`와 `blt` 같은 의사 분기가 펼쳐진 `bne`/`beq`)의 오프셋 필드만 Hallym MIPS 화면보다 1 작고, 나머지 워드는 비트 단위로 같다. Hallym MIPS와 같은 비트가 필요하면 `-branch pc`를 쓴다.

## 테스트

`native/hcs-asm/tests/check.py`가 두 가지를 확인한다.

1. **골든:** `tests/asm/<이름>.s`(+ `<이름>.flags`)의 출력이 `tests/asm/<이름>.json`과 글자 단위로 같다. 산술, lw/sw, 앞·뒤 분기, j/jal/jr, 의사 명령어, `.data` 문자열·지시어, 문법 오류, 미정의 라벨, `main` 위치 경고, 예외 처리기, `-branch pc`를 다룬다. 출력을 바꿨으면 `python3 native/hcs-asm/tests/check.py --update`로 다시 만들고 diff를 검토한다.
2. **오라클:** 오류 없는 모든 입력과 SPIM 원본 테스트 프로그램(`helloworld.s`, `Tests/tt.core.s` 등 8개)을 수정하지 않은 원본 `spim` 명령줄(`build/oracle/spim -dump`, 배포하지 않음)로도 어셈블해 텍스트·데이터 워드가 모두 같은지 본다. `-branch pc4`는 `spim -delayed_branches`와 비교한다.
