#!/bin/bash
# 스크린샷을 장면마다 새 JVM으로, 가상 화면 여러 개에서 나눠 동시에 찍는다(D-121). docs/SCREENSHOTS.md 참고.
# 쓰기: tools/screenshots/run-parallel.sh <출력 폴더> [일꾼 수, 기본 4] [장면 번호 ...(기본: 전체)]
#   일꾼 수 1이 순차 촬영이다. 장면마다 환경설정을 비운 새 JVM(run.sh 한 번)으로 찍으므로 앞 장면이 남긴 상태가 없고,
#   일꾼 수와 순서에 상관없이 같은 픽셀이 나온다(한 JVM에서 이어 찍으면 앞 장면에 따라 이미지가 달라졌다).
#   SHOTS_PAR_ORIG=1이면 원조 2.7.1 장면(-orig)도 찍는다(장면 02 뒤에 원조 JVM 하나로). 기본은 0: -orig는 기준 이미지를 쓴다.
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
cd "$root"
out=$(mkdir -p "${1:?출력 폴더}" && cd "$1" && pwd)
shift
n=${1:-4}
[ $# -gt 0 ] && shift
JAVA=${JAVA:-java}
JAVAC=${JAVAC:-javac}
export JAVA JAVAC
# 오래 걸리는 장면부터 나눠 준다(일꾼이 비슷한 때 끝나도록). screens/scenes.tsv의 장면이 모두 있어야 한다
all=(18 17 14 11 25 51 52 16 31 29 30 20 21 37 38 03 40 27 26 28 19 24 09 35 36 39 41 43 44 47 49 50 22 23 32 12
    15 08 06 05 04 07 10 13 33 34 42 45 46 01 02 48)
for s in $(awk -F'\t' '$2=="core"||$2=="feature"{print $1}' screens/scenes.tsv); do
    printf '%s\n' "${all[@]}" | grep -qx "$s" || { echo "run-parallel.sh: 장면 $s가 순서 목록에 없다" >&2; exit 2; }
done
scenes=()
if [ $# -gt 0 ]; then
    for s in "$@"; do printf '%s\n' "${all[@]}" | grep -qx "$s" || { echo "모르는 장면: $s" >&2; exit 2; }; done
    for s in "${all[@]}"; do printf '%s\n' "$@" | grep -qx "$s" && scenes+=("$s"); done
else
    scenes=("${all[@]}")
fi
orig=${SHOTS_PAR_ORIG:-0}
[ "${SHOTS_SKIP_BUILD:-0}" = 1 ] || ./gradlew -q :app:stage :lib-mips:jar
work="build/screenshots/work-par-$(basename "$out")"
rm -rf "$work"
mkdir -p "$work/classes" "$out/.parts"
jar=app/build/stage/hallym-circuit-studio.jar
"$JAVAC" -encoding UTF-8 -nowarn -cp "$jar" -d "$work/classes" tools/screenshots/Shots.java tools/screenshots/FirstRun.java
queue="$work/queue"
jobs=()
for s in "${scenes[@]}"; do
    if [ "$s" = 02 ] && [ "$orig" = 1 ]; then jobs+=("02+orig"); else jobs+=("$s"); fi
done
if [ "$orig" = 1 ] && ! printf '%s\n' "${scenes[@]}" | grep -qx 02; then jobs=("02+orig" "${jobs[@]}"); fi
printf '%s\n' "${jobs[@]}" > "$queue"
take() { flock "$queue.lock" sh -c 'l=$(head -n 1 "$1"); [ -n "$l" ] && sed -i 1d "$1"; echo "$l"' _ "$queue"; }
shoot() { # <일꾼> <장면> <원조 1/0>
    local i=$1 s=$2 o=$3 dir="$out/.parts/$2"
    rm -rf "$dir"
    SHOTS_SKIP_BUILD=1 SHOTS_CLASSES="$work/classes" SHOTS_WORK="$work/w$i" SHOTS_DISPLAY=$((100 + 10 * i)) SHOTS_ORIG=0 \
        tools/screenshots/run.sh "$dir" "$s" > "$dir.log" 2>&1 || return 1
    if [ "$o" = 1 ]; then # 원조는 포크 02가 정한 "화면 맞춤" 배율(fit-zoom.txt)로 찍는다
        mkdir -p "$out/.parts/orig"
        cp "$dir/fit-zoom.txt" "$out/.parts/orig/"
        SHOTS_SKIP_BUILD=1 SHOTS_CLASSES="$work/classes" SHOTS_WORK="$work/w$i" SHOTS_DISPLAY=$((100 + 10 * i)) SHOTS_ORIG=1 \
            SHOTS_FORK=0 tools/screenshots/run.sh "$out/.parts/orig" 02 > "$out/.parts/orig.log" 2>&1 || return 1
    fi
}
worker() {
    local i=$1 j
    while :; do
        j=$(take)
        [ -z "$j" ] && break
        if [ "$j" = "02+orig" ]; then shoot "$i" 02 1; else shoot "$i" "$j" 0; fi || echo "$j" >> "$out/failed.txt"
    done
}
rm -f "$out/failed.txt"
start=$(date +%s)
pids=()
for ((i = 0; i < n; i++)); do
    worker "$i" &
    pids+=($!)
done
for p in "${pids[@]}"; do wait "$p"; done
# 모으기: 로그는 장면 순서(Shots.run의 순서와 무관하게 번호순)로 이어 붙인다
for f in log-fork.txt log-first.txt log-orig.txt; do rm -f "$out/$f"; done
for d in $(ls -d "$out"/.parts/*/ | sort); do
    cp "$d"*.png "$out"/ 2>/dev/null || true
    for f in log-fork.txt log-first.txt log-orig.txt; do
        [ -f "$d$f" ] && cat "$d$f" >> "$out/$f"
    done
done
secs=$(( $(date +%s) - start ))
echo "per-scene JVMs: $n workers, ${#scenes[@]} scenes, orig $orig, $(ls "$out"/*.png | wc -l) images, $secs s" | tee "$out/timing.txt"
if [ -s "$out/failed.txt" ]; then
    echo "failed scenes: $(tr '\n' ' ' < "$out/failed.txt")" | tee -a "$out/timing.txt"
    exit 1
fi
