#!/usr/bin/env bash
# GitHub CI(.github/workflows/ci.yml)와 같은 검사를 로컬에서 재현한다. 순서와 명령을 워크플로와 맞춘다.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

step() { printf '\n== %s\n' "$1"; }

step "vendor 원본 검증"
tools/verify-vendor.sh
tools/check-upstream-markers.sh

step "assets 원형 유지"
tools/verify-assets.sh

step "엔진 소스 원본 일치"
tools/check-engine-unchanged.sh

step "hcs-asm 빌드·어셈블 일치"
make -s -C native/hcs-asm -j"$(nproc)" test

step "디스어셈블러 골든 다시 만들어도 같음 (D-127)"
tools/gen-disasm-golden.sh
git diff --exit-code -- tests/disasm
test -z "$(git status --porcelain -- tests/disasm)"

step "Gradle 빌드·테스트 (hcs-asm을 쓰는 테스트 포함)"
./gradlew --no-daemon -q build

step "단위 테스트, identity hash가 모두 같은 JVM에서 (D-129)"
./gradlew --no-daemon -q :app:testConstantIdentityHash

printf '\nci-local: all checks passed (%s)\n' "$(git rev-parse --short HEAD)"
