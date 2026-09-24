#!/bin/bash
# 스크린샷 시나리오를 가상 화면(1920×1080)에서 다시 찍는다. docs/SCREENSHOTS.md 참고.
# 쓰기: tools/screenshots/run.sh <출력 폴더> [장면 번호 ...]   (예: run.sh build/screenshots/out 01 05)
# 출력: <폴더>/NN-이름.png(포크), NN-이름-orig.png(원조 2.7.1), log-fork.txt, log-orig.txt
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
cd "$root"
out=$(mkdir -p "${1:?출력 폴더}" && cd "$1" && pwd)
shift
JAVA=${JAVA:-java}
JAVAC=${JAVAC:-javac}

./gradlew -q :app:stage :lib-mips:jar
B=build/screenshots/work
rm -rf "$B"
mkdir -p "$B/classes" "$B/orig"
jar=app/build/stage/hallym-circuit-studio.jar
"$JAVAC" -encoding UTF-8 -nowarn -cp "$jar" -d "$B/classes" tools/screenshots/Shots.java

screen="-screen 0 1920x1080x24 -dpi 96"
opts=(-Duser.language=ko -Duser.country=KR -Dsun.java2d.uiScale=1 -Dawt.useSystemAAFontSettings=on)

# 포크: 저장소 루트에서 상대 경로로 연다
xvfb-run -a -s "$screen" "$JAVA" "${opts[@]}" -Djava.util.prefs.userRoot="$B/prefs-fork" \
    -Dhcs.configDir="$B/config-fork" -cp "$B/classes:$jar" Shots fork "$out" "$@"

# 원조 2.7.1: 같은 회로, hcs-mips.jar를 회로 옆에 둔다(원조가 JAR 라이브러리를 찾는 방식)
if [ $# -eq 0 ] || printf '%s\n' "$@" | grep -qx '0[23]'; then
    cp tests/mips/ref-mips.circ "$B/orig/"
    cp lib-mips/build/libs/hcs-mips.jar "$B/orig/"
    (cd "$B/orig" && xvfb-run -a -s "$screen" "$JAVA" "${opts[@]}" -Djava.util.prefs.userRoot=prefs-orig \
        -cp "../classes:$root/vendor/logisim-2.7.1/logisim-generic-2.7.1.jar" Shots orig "$out")
fi
ls -la "$out"
