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

# 병렬 실행(run-parallel.sh)은 빌드를 한 번만 하고, 실행마다 작업 폴더·가상 화면 번호를 따로 쓴다
[ "${SHOTS_SKIP_BUILD:-0}" = 1 ] || ./gradlew -q :app:stage :lib-mips:jar
B=${SHOTS_WORK:-build/screenshots/work}
xvfb=(xvfb-run -a)
[ -n "${SHOTS_DISPLAY:-}" ] && xvfb=(xvfb-run -n "$SHOTS_DISPLAY")
rm -rf "$B"
mkdir -p "$B/classes" "$B/orig"
jar=app/build/stage/hallym-circuit-studio.jar
"$JAVAC" -encoding UTF-8 -nowarn -cp "$jar" -d "$B/classes" tools/screenshots/Shots.java

screen="-screen 0 1920x1080x24 -dpi 96"
opts=(-Duser.language=ko -Duser.country=KR -Dsun.java2d.uiScale=1 -Dawt.useSystemAAFontSettings=on)

# 장면 48(Y-04): 환경설정·최근 파일 없는 진짜 첫 실행은 별도 JVM으로(인자 없이 시작, 튜토리얼 첫 장)
if [ $# -eq 0 ] || printf '%s\n' "$@" | grep -qx '48'; then
    "$JAVAC" -encoding UTF-8 -nowarn -cp "$jar" -d "$B/classes" tools/screenshots/FirstRun.java
    rm -rf "$B/prefs-first" "$B/config-first"
    "${xvfb[@]}" -s "$screen" "$JAVA" "${opts[@]}" -Djava.util.prefs.userRoot="$B/prefs-first" \
        -Dhcs.configDir="$B/config-first" -cp "$B/classes:$jar" FirstRun "$out/48-first-run.png" | tee -a "$out/log-first.txt"
fi

# 포크: 저장소 루트에서 상대 경로로 연다
"${xvfb[@]}" -s "$screen" "$JAVA" "${opts[@]}" -Djava.util.prefs.userRoot="$B/prefs-fork" \
    -Dhcs.configDir="$B/config-fork" -cp "$B/classes:$jar" Shots fork "$out" "$@"

# 원조 2.7.1: 같은 회로, hcs-mips.jar를 회로 옆에 둔다(원조가 JAR 라이브러리를 찾는 방식)
orig=0
if [ $# -eq 0 ] || printf '%s\n' "$@" | grep -qxE '0[23]|16|21|23|37'; then orig=1; fi
[ -n "${SHOTS_ORIG:-}" ] && orig=$SHOTS_ORIG
if [ "$orig" = 1 ]; then
    cp tests/circ/demo-datapath.circ "$B/orig/"
    cp lib-mips/build/libs/hcs-mips.jar "$B/orig/"
    (cd "$B/orig" && "${xvfb[@]}" -s "$screen" "$JAVA" "${opts[@]}" -Djava.util.prefs.userRoot=prefs-orig \
        -cp "../classes:$root/vendor/logisim-2.7.1/logisim-generic-2.7.1.jar" Shots orig "$out")
fi
ls -la "$out"
