#!/bin/bash
# 스크린샷 전체 세트를 가상 화면 여러 개에서 나눠 동시에 찍는다(촬영 시간 줄이기). docs/SCREENSHOTS.md 참고.
# 쓰기: tools/screenshots/run-parallel.sh <출력 폴더> [묶음 수, 기본 4]
# 장면 순서(Shots.run의 순서)를 이어진 묶음으로 나누고, 31은 따로(혼자 찍어야 한다), 원조 2.7.1 장면은 첫 묶음에서 한 번.
# 결과는 순차 촬영(run.sh)과 같아야 한다: tools/screenshots/diff.py로 확인한다.
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
cd "$root"
out=$(mkdir -p "${1:?출력 폴더}" && cd "$1" && pwd)
n=${2:-4}
# Shots.run의 장면 순서(31은 뺀다)
order=(01 02 03 05 12 04 06 07 08 09 13 11 25 26 27 37 28 29 30 32 33 34 35 36 38 39 40 41 42 43 44 47 49 50 51 45 46 10 15 16 17 18 19 22 23 24 20 21 14 48)
./gradlew -q :app:stage :lib-mips:jar
start=$(date +%s)
pids=()
per=$(( (${#order[@]} + n - 1) / n ))
for ((i = 0; i < n; i++)); do
    part=("${order[@]:$((i * per)):$per}")
    [ ${#part[@]} -eq 0 ] && continue
    o=1; [ $i -gt 0 ] && o=0
    SHOTS_SKIP_BUILD=1 SHOTS_WORK="build/screenshots/work-p$i" SHOTS_DISPLAY=$((90 + i)) SHOTS_ORIG=$o \
        tools/screenshots/run.sh "$out/part$i" "${part[@]}" > "$out/run-part$i.log" 2>&1 &
    pids+=($!)
done
SHOTS_SKIP_BUILD=1 SHOTS_WORK="build/screenshots/work-p31" SHOTS_DISPLAY=$((90 + n)) SHOTS_ORIG=0 \
    tools/screenshots/run.sh "$out/part31" 31 > "$out/run-part31.log" 2>&1 &
pids+=($!)
fail=0
for p in "${pids[@]}"; do wait "$p" || fail=1; done
for d in "$out"/part*; do
    cp "$d"/*.png "$out"/ 2>/dev/null || true
    [ -f "$d/log-fork.txt" ] && cat "$d/log-fork.txt" >> "$out/log-fork.txt"
    [ -f "$d/log-first.txt" ] && cat "$d/log-first.txt" >> "$out/log-first.txt"
    [ -f "$d/log-orig.txt" ] && cat "$d/log-orig.txt" >> "$out/log-orig.txt"
done
echo "parallel: $n parts + scene 31, $(ls "$out"/*.png | wc -l) images, $(( $(date +%s) - start )) s" | tee "$out/timing.txt"
exit $fail
