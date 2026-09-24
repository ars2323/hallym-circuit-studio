#!/usr/bin/env bash
# assets/의 파일이 가져온 원본과 바이트 단위로 같은지 확인한다 (CLAUDE.md 규칙 2.4: 로고·캐릭터 원형 유지).
# 목록에 없는 파일이 생기거나 가이드라인 PDF·.ai 원본이 들어와도 실패한다.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root/assets"

sha256sum --quiet -c MANIFEST.sha256

expected="$(cut -c67- MANIFEST.sha256 | sort)"
actual="$(find . -type f ! -name MANIFEST.sha256 ! -name README.md | sed 's|^\./||' | sort)"
extra="$(comm -13 <(echo "$expected") <(echo "$actual"))"
if [ -n "$extra" ]; then
  echo "assets/에 MANIFEST.sha256에 없는 파일이 있다(tools/import-assets.py로 가져온다):" >&2
  echo "$extra" >&2
  exit 1
fi

forbidden="$(cd "$root" && git ls-files | grep -iE '\.(ai|eps|psd)$|메뉴얼|매뉴얼|manual.*\.pdf$|guideline.*\.pdf$' | grep -v '^vendor/' || true)"
if [ -n "$forbidden" ]; then
  echo "git에 넣지 않는 원본·가이드라인 파일이 추적되고 있다:" >&2
  echo "$forbidden" >&2
  exit 1
fi
echo "assets OK ($(echo "$expected" | wc -l) files)"
