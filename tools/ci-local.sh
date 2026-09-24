#!/usr/bin/env bash
# CI(Linux)와 같은 검사를 로컬에서 돌린다. .github/workflows/ci.yml도 이 스크립트를 호출한다.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

step() { printf '\n== %s\n' "$1"; }

step "vendor 원본 검증"
tools/verify-vendor.sh

step "엔진 소스 원본 일치"
tools/check-engine-unchanged.sh

step "Gradle 빌드·테스트"
./gradlew --no-daemon -q build

step "hcs-asm 빌드·어셈블 일치"
make -s -C native/hcs-asm -j"$(nproc)" test

printf '\nci-local: all checks passed (%s)\n' "$(git rev-parse --short HEAD)"
