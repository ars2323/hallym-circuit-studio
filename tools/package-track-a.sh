#!/usr/bin/env bash
# 트랙 A 배포물: hcs-mips.jar, 사용 안내, 라이선스를 zip으로 묶는다(hcs-asm은 D-141에서 없어졌다).
#   tools/package-track-a.sh <버전> <입력 폴더> <출력 폴더>
# 입력 폴더는 CI 아티팩트(linux-build/)를 받은 곳이다.
set -euo pipefail
version="$1"
in="$2"
out="$3"
root="$(cd "$(dirname "$0")/.." && pwd)"

find_one() { find "$in" -type f -name "$1" | head -1; }
jar="$(find_one hcs-mips.jar)"
[ -n "$jar" ] || { echo "missing build output in $in" >&2; exit 1; }

mkdir -p "$out"
cp "$jar" "$out/hcs-mips.jar"
cp "$root/docs/track-a-guide.md" "$out/사용안내.md"
# Release 자산 이름은 ASCII로 둔다. GitHub가 한글 자산 이름을 default.md로 바꾼다. zip 안에는 사용안내.md로 둔다.
cp "$root/docs/track-a-guide.md" "$out/hcs-mips-guide-ko.md"

# 한 폴더에 풀면 바로 쓰는 zip. 운영체제마다 같은 jar이고 따로 둘 실행 파일이 없다.
for os in windows linux; do
  dir="$out/hcs-mips-$version-$os"
  mkdir -p "$dir/licenses"
  cp "$out/hcs-mips.jar" "$out/사용안내.md" "$dir/"
  cp "$root/LICENSE" "$dir/licenses/GPL-2.0-hcs-mips.txt"
  cp "$root/NOTICE" "$dir/licenses/NOTICE.txt"
  (cd "$out" && rm -f "hcs-mips-$version-$os.zip" && zip -qr "hcs-mips-$version-$os.zip" "hcs-mips-$version-$os")
  rm -rf "$dir"
done

cat > "$out/RELEASE_NOTES.md" <<NOTES
원조 Logisim 2.7.1에서 Project › Load Library › JAR Library로 불러 쓰는 MIPS 부품 라이브러리입니다.

- 부품: Instruction Memory, Data Memory(데이터+스택), Console, Radix Probe(옛 회로용 Stack도 들어 있음)
- 우클릭 "Load Program...": Hallym MIPS가 내보낸 실행 이미지(.hmx)를 주소 그대로 올립니다. Hallym MIPS에서 Ctrl+S로 어셈블한 뒤 제목 줄 오른쪽의 Export executable image (.hmx) 단추로 내보냅니다. .s는 받지 않습니다(D-141).
- \`hcs-mips-$version-windows.zip\`을 풀어 \`hcs-mips.jar\`를 씁니다. 자세한 내용은 zip 안의 \`사용안내.md\`. 같은 문서를 \`hcs-mips-guide-ko.md\`로도 따로 올렸습니다.

학생 배포 여부는 담당자가 정합니다(draft).
NOTES
ls -la "$out"
