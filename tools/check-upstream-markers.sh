#!/usr/bin/env bash
# 포크(app/)의 원본 트리가 Logisim 2.7.1 jar에서 어떻게 달라졌는지 기계로 확인한다(D-130, CLAUDE.md 9절).
# - app/src: jar의 src/와 파일 목록이 같아야 한다(새 코드는 app/src-hcs에). 다른 .java에는 "// HCS:" 주석이 있어야 한다.
# - app/resources, app/doc: jar와 다른 파일(새 파일 포함)은 docs/upstream-resources.txt에 D-번호와 함께 있어야 한다.
# 엔진 패키지는 더 엄격한 tools/check-engine-unchanged.sh가 따로 본다.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
jar="$root/vendor/logisim-2.7.1/logisim-generic-2.7.1.jar"
allow="$root/docs/upstream-resources.txt"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
unzip -q "$jar" 'src/*' 'resources/*' 'doc/*' -d "$tmp"
fail=0
changed=0

list() { (cd "$1" && find . -type f | sed 's|^\./||' | sort); }

# app/src
while IFS= read -r f; do echo "원본 트리에 새 파일(새 코드는 app/src-hcs에): app/src/$f" >&2; fail=1; done \
  < <(comm -13 <(list "$tmp/src") <(list "$root/app/src"))
while IFS= read -r f; do echo "원본 파일이 빠짐: app/src/$f" >&2; fail=1; done \
  < <(comm -23 <(list "$tmp/src") <(list "$root/app/src"))
while IFS= read -r f; do
  if ! cmp -s "$tmp/src/$f" "$root/app/src/$f"; then
    changed=$((changed + 1))
    case "$f" in
      *.java) grep -q '// HCS:' "$root/app/src/$f" || { echo "HCS 주석 없이 바뀐 원본: app/src/$f" >&2; fail=1; } ;;
      *) echo "원본과 다른 파일(.java 아님): app/src/$f" >&2; fail=1 ;;
    esac
  fi
done < <(comm -12 <(list "$tmp/src") <(list "$root/app/src"))

# app/resources, app/doc
allowed="$(grep -v '^\s*#' "$allow" | awk 'NF {print $1}')"
is_allowed() {
  local p="$1" a
  while IFS= read -r a; do
    [ -z "$a" ] && continue
    if [ "$p" = "$a" ] || { [ "${a%/}" != "$a" ] && [ "${p#"$a"}" != "$p" ]; }; then return 0; fi
  done <<< "$allowed"
  return 1
}
for d in resources doc; do
  while IFS= read -r f; do
    p="app/$d/$f"
    if [ ! -f "$tmp/$d/$f" ] || ! cmp -s "$tmp/$d/$f" "$root/$p"; then
      changed=$((changed + 1))
      is_allowed "$p" || { echo "원본과 다른 리소스(docs/upstream-resources.txt에 없음): $p" >&2; fail=1; }
    fi
  done < <(list "$root/app/$d")
  while IFS= read -r f; do echo "원본 리소스가 빠짐: app/$d/$f" >&2; fail=1; done \
    < <(comm -23 <(list "$tmp/$d") <(list "$root/app/$d"))
done

# 허용 목록의 D-번호는 DECISIONS에 있어야 한다
while read -r path ref _; do
  grep -q "^## $ref " "$root/docs/DECISIONS.md" || { echo "upstream-resources.txt의 $path: DECISIONS에 $ref 없음" >&2; fail=1; }
done < <(grep -v '^\s*#' "$allow" | awk 'NF')

[ "$fail" -eq 0 ] || exit 1
echo "upstream markers OK ($changed files differ from the 2.7.1 jar, all marked or listed)"
