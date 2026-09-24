# QtSpim 텍스트 세그먼트 골든

Hallym MIPS Simulator(`ars2323/hallym-mips-simulator`, 커밋 `8ebd3c9`)의 `tests/golden/text-load.txt`, `text-ttcore.txt`를 이름만 바꿔 그대로 가져왔다. 원본 QtSpim 9.1.24 GUI의 File › Save Log File 출력이다(예외 처리기 켬, 지연 분기 끔, 의사 명령어 켬).

| 파일 | 프로그램 |
| --- | --- |
| `helloworld.text.txt` | `vendor/spim-9.1.24/helloworld.s` |
| `tt.core.text.txt` | `vendor/spim-9.1.24/Tests/tt.core.s` (4758 명령) |

`native/hcs-asm/tests/check.py`가 `hcs-asm -exception`의 사용자 텍스트 세그먼트를 이 파일의 주소·워드와 한 줄씩 비교한다. 분기 명령을 포함해 모든 명령이 비트 단위로 같아야 한다(D-010).
