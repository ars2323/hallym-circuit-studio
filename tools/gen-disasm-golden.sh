#!/usr/bin/env bash
# 디스어셈블러 골든(tests/disasm/)을 hcs-asm -disasm(SPIM 자신의 명령어 출력)으로 다시 만든다(Z-04, D-127).
# 먼저 make -C native/hcs-asm 로 hcs-asm을 빌드한다. CI는 이것을 돌린 뒤 git diff로 골든이 SPIM과 어긋나지 않았는지 본다.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
exec python3 "$root/tools/disasm-golden.py" "$@"
