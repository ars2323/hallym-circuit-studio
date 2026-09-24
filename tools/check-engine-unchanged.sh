#!/usr/bin/env bash
# app/의 Logisim 엔진 소스가 원본 2.7.1 jar와 같은지 확인한다 (CLAUDE.md 규칙 2.1, 2.3).
# 원본과 다른 파일은 docs/engine-patches.txt에 D-번호와 함께 올라 있어야 하고,
# 파일 안에 "// HCS:" 주석이 있어야 한다.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
jar="$root/vendor/logisim-2.7.1/logisim-generic-2.7.1.jar"
allow="$root/docs/engine-patches.txt"

protected=(
  com/cburch/logisim/circuit
  com/cburch/logisim/comp
  com/cburch/logisim/data
  com/cburch/logisim/instance
  com/cburch/logisim/std
  com/cburch/logisim/file
  com/cburch/logisim/Main.java
)

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
unzip -q "$jar" 'src/*' -d "$tmp"

allowed="$(grep -v '^\s*#' "$allow" | awk 'NF {print $1}' || true)"
fail=0
checked=0
for p in "${protected[@]}"; do
  upstream="$tmp/src/$p"
  fork="$root/app/src/$p"
  while IFS= read -r rel; do
    checked=$((checked + 1))
    path="app/src/$rel"
    if [ ! -f "$tmp/src/$rel" ] || [ ! -f "$root/$path" ] || ! cmp -s "$tmp/src/$rel" "$root/$path"; then
      if grep -qxF "$path" <<<"$allowed"; then
        if [ -f "$root/$path" ] && ! grep -q '// HCS:' "$root/$path"; then
          echo "HCS 주석 없음: $path" >&2; fail=1
        fi
      else
        echo "원본과 다름 (engine-patches.txt에 없음): $path" >&2; fail=1
      fi
    fi
  done < <( { [ -e "$upstream" ] && (cd "$tmp/src" && find "$p" -type f -name '*.java'); \
              [ -e "$fork" ] && (cd "$root/app/src" && find "$p" -type f -name '*.java'); } | sort -u )
done

# 허용 목록의 각 줄은 DECISIONS의 D-번호를 가리켜야 한다.
while read -r path ref _; do
  [ -z "$path" ] && continue
  if ! grep -q "^## $ref " "$root/docs/DECISIONS.md"; then
    echo "engine-patches.txt의 $path: DECISIONS에 $ref 항목이 없음" >&2; fail=1
  fi
done < <(grep -v '^\s*#' "$allow" | awk 'NF')

[ "$fail" -eq 0 ] || exit 1
echo "engine OK ($checked files, $(grep -cv '^\s*#\|^\s*$' "$allow" || true) allowed patches)"
