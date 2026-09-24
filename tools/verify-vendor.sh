#!/usr/bin/env bash
# vendor/ 원본이 처음 들여온 상태 그대로인지 확인한다 (CLAUDE.md 규칙 2.2).
# 체크섬에 없는 파일이 vendor/ 아래 생겨도 실패한다.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
sums="$root/docs/vendor-checksums.sha256"
cd "$root/vendor"

sha256sum --quiet -c "$sums"

expected="$(cut -c67- "$sums" | sort)"
actual="$(find . -type f | sed 's|^\./||' | sort)"
extra="$(comm -13 <(echo "$expected") <(echo "$actual"))"
if [ -n "$extra" ]; then
  echo "vendor/ 아래 원본에 없던 파일이 있다:" >&2
  echo "$extra" >&2
  exit 1
fi
echo "vendor OK ($(echo "$expected" | wc -l) files)"
