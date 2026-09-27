# QtSpim 텍스트 세그먼트 골든

Hallym MIPS Simulator(`ars2323/hallym-mips-simulator`, 커밋 `8ebd3c9`)의 `tests/golden/text-load.txt`, `text-ttcore.txt`를 이름만 바꿔 그대로 가져왔다. 원본 QtSpim 9.1.24 GUI의 File › Save Log File 출력이다(예외 처리기 켬, 지연 분기 끔, 의사 명령어 켬).

| 파일 | 프로그램 |
| --- | --- |
| `helloworld.text.txt` | SPIM 9.1.24의 `helloworld.s` |
| `tt.core.text.txt` | SPIM 9.1.24의 `Tests/tt.core.s` (4758 명령) |

`DisassemblerGoldenTest.goldenTextIsWhatQtSpimShows`가 디스어셈블러 골든(`tests/disasm/spim-helloworld.txt`, `spim-tt.core.txt`)을 이 파일과 한 줄씩 대조한다. vendor/spim과 hcs-asm이 있던 동안에는 `native/hcs-asm/tests/check.py`가 `hcs-asm -exception`의 기계어도 이 파일과 비교했다(분기 포함 비트 단위로 같았다, D-010). 둘은 D-141에서 지웠다.
