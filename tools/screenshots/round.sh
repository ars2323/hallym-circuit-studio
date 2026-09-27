#!/bin/bash
# 한 라운드의 스크린샷(D-121, screens/README.md): 장면 고르기 → 병렬 촬영 → core만 남기기 → 직전 통과 세트와 픽셀 비교.
# 쓰기: tools/screenshots/round.sh <출력 폴더> <직전 통과 세트 폴더> [기준 커밋, 기본: 마지막 v 태그]
# 결과:
#   <출력>/select.txt       고른 장면과 이유
#   <출력>/diff.txt         바뀐/새 이미지와 "바뀐 수/전체"(찍지 않은 이미지는 직전 판정을 이어 씀)
#   <출력>/review-list.txt  ui-reviewer에게 보낼 이미지(바뀐 것과 새 것)
#   <출력>/timing.txt       촬영 시간
#   <출력>-set/             직전 통과 세트 위에 이번 이미지를 덮은 새 세트. 검토에서 막는 위반이 0건이면 다음 라운드의 기준이다
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
cd "$root"
out=${1:?출력 폴더}
prev=$(cd "${2:?직전 통과 세트 폴더}" && pwd)
base=${3:-$(git describe --tags --abbrev=0 --match 'v*')}
sel=$(python3 tools/screenshots/select.py "$base")
read -r -a scenes <<< "$(head -1 <<< "$sel")"
orig=$(python3 tools/screenshots/select.py "$base" --orig)
start=$(date +%s)
SHOTS_PAR_ORIG=$orig tools/screenshots/run-parallel.sh "$out" 4 "${scenes[@]}"
out=$(cd "$out" && pwd)
printf '%s\n' "$sel" > "$out/select.txt"
python3 tools/screenshots/select.py "$base" --filter "$out" | tee -a "$out/select.txt"
echo "round: ${#scenes[@]} scenes, orig $orig, $(ls "$out"/*.png | wc -l) images kept, $(( $(date +%s) - start )) s" | tee -a "$out/timing.txt"
python3 tools/screenshots/diff.py "$prev" "$out" --partial --list "$out/review-list.txt" | tee "$out/diff.txt" || true
rm -rf "$out-set"
cp -r "$prev" "$out-set"
cp "$out"/*.png "$out-set"/
echo "review: $(wc -l < "$out/review-list.txt") images → ui-reviewer; new set: $out-set"
