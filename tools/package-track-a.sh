#!/usr/bin/env bash
# 트랙 A 배포물: hcs-mips.jar, hcs-asm(Linux), hcs-asm.exe, 사용 안내, 라이선스를 zip으로 묶는다.
#   tools/package-track-a.sh <버전> <입력 폴더> <출력 폴더>
# 입력 폴더는 CI 아티팩트(linux-build/, windows-build/)를 받은 곳이다.
set -euo pipefail
version="$1"
in="$2"
out="$3"
root="$(cd "$(dirname "$0")/.." && pwd)"

find_one() { find "$in" -type f -name "$1" | head -1; }
jar="$(find_one hcs-mips.jar)"
linux="$(find_one hcs-asm)"
exe="$(find_one hcs-asm.exe)"
for f in "$jar" "$linux" "$exe"; do
  [ -n "$f" ] || { echo "missing build output in $in" >&2; exit 1; }
done

mkdir -p "$out"
cp "$jar" "$out/hcs-mips.jar"
cp "$linux" "$out/hcs-asm"
chmod +x "$out/hcs-asm"
cp "$exe" "$out/hcs-asm.exe"
cp "$root/docs/track-a-guide.md" "$out/사용안내.md"
# Release 자산 이름은 ASCII로 둔다. GitHub가 한글 자산 이름을 default.md로 바꾼다. zip 안에는 사용안내.md로 둔다.
cp "$root/docs/track-a-guide.md" "$out/hcs-mips-guide-ko.md"

# 한 폴더에 풀면 바로 쓰는 zip. jar와 hcs-asm이 같은 폴더에 있어야 .s 불러오기가 된다.
for os in windows linux; do
  dir="$out/hcs-mips-$version-$os"
  mkdir -p "$dir/licenses"
  cp "$out/hcs-mips.jar" "$out/사용안내.md" "$dir/"
  if [ "$os" = windows ]; then cp "$out/hcs-asm.exe" "$dir/"; else cp "$out/hcs-asm" "$dir/"; fi
  cp "$root/LICENSE" "$dir/licenses/GPL-2.0-hcs-mips.txt"
  cp "$root/native/hcs-asm/LICENSE" "$dir/licenses/BSD-3-hcs-asm.txt"
  cp "$root/vendor/spim-9.1.24/README" "$dir/licenses/SPIM-README.txt"
  cp "$root/NOTICE" "$dir/licenses/NOTICE.txt"
  (cd "$out" && rm -f "hcs-mips-$version-$os.zip" && zip -qr "hcs-mips-$version-$os.zip" "hcs-mips-$version-$os")
  rm -rf "$dir"
done

cat > "$out/RELEASE_NOTES.md" <<NOTES
원조 Logisim 2.7.1에서 Project › Load Library › JAR Library로 불러 쓰는 MIPS 부품 라이브러리입니다.

- 부품: Instruction Memory, Data Memory, Stack, Console, Radix Probe
- 우클릭 "Load .s...": QtSpim(Hallym MIPS)과 같은 기계어
- \`hcs-mips-$version-windows.zip\`을 풀어 \`hcs-mips.jar\`와 \`hcs-asm.exe\`를 같은 폴더에 두고 씁니다. 자세한 내용은 zip 안의 \`사용안내.md\`. 같은 문서를 \`hcs-mips-guide-ko.md\`로도 따로 올렸습니다.

학생 배포 여부는 담당자가 정합니다(draft).
NOTES
ls -la "$out"
