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

# D-141: vendor/spim과 hcs-asm은 지웠다. SPIM 결과는 tests/disasm·tests/spim-oracle 등에 굳혀 두었다
step "Gradle 빌드·테스트 (굳혀 둔 SPIM 결과와 대조)"
./gradlew --no-daemon -q build

step "돌연변이 테스트: 로더·디스어셈블러 (Z-24, D-138)"
./gradlew --no-daemon -q :lib-mips:pitest

step "단위 테스트, identity hash가 모두 같은 JVM에서 (D-129)"
./gradlew --no-daemon -q :app:testConstantIdentityHash

step "엔진 서버 테스트 (N-03, 상수 identity hash로 한 번 더)"
./gradlew --no-daemon -q :engine:test
./gradlew --no-daemon -q :engine:test -Phcs.constantHash=true

printf '\nci-local: all checks passed (%s)\n' "$(git rev-parse --short HEAD)"
