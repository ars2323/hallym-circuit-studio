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
# 첫 실행(장면 48)·포크·원조는 가상 화면 번호를 따로 쓴다: 앞 서버가 막 끝난 번호를 바로 다시 잡으면 Xvfb가 못 뜰 때가 있다
xvfb=(xvfb-run -a); xfirst=(xvfb-run -a); xorig=(xvfb-run -a)
if [ -n "${SHOTS_DISPLAY:-}" ]; then
    xvfb=(xvfb-run -a -n "$SHOTS_DISPLAY"); xfirst=(xvfb-run -a -n $((SHOTS_DISPLAY + 1))); xorig=(xvfb-run -a -n $((SHOTS_DISPLAY + 2)))
fi
rm -rf "$B"
mkdir -p "$B/classes" "$B/orig"
jar=app/build/stage/hallym-circuit-studio.jar
# SHOTS_CLASSES: 미리 컴파일한 Shots·FirstRun(run-parallel.sh가 한 번 컴파일해 장면마다 이 스크립트를 부른다)
C=${SHOTS_CLASSES:-$B/classes}
[ -n "${SHOTS_CLASSES:-}" ] || "$JAVAC" -encoding UTF-8 -nowarn -cp "$jar" -d "$C" tools/screenshots/Shots.java
C=$(cd "$C" && pwd)

screen="-screen 0 1920x1080x24 -dpi 96"
opts=(-Duser.language=ko -Duser.country=KR -Dsun.java2d.uiScale=1 -Dawt.useSystemAAFontSettings=on)
# 같은 장면이 매번 같은 픽셀이 되게: 원조 엔진은 부품을 HashSet에 두고 그 순서대로 그린다(겹친 끝점의 색, 목록 순서).
# 객체 해시를 JVM마다 같은 값으로 고정해 순서를 넣은 순서로 만든다(촬영 JVM에만 쓰고 앱에는 쓰지 않는다, D-121)
opts+=(-XX:+UnlockExperimentalVMOptions -XX:hashCode=2)

# 장면 48(Y-04): 환경설정·최근 파일 없는 진짜 첫 실행은 별도 JVM으로(인자 없이 시작, 튜토리얼 첫 장)
if [ $# -eq 0 ] || printf '%s\n' "$@" | grep -qx '48'; then
    [ -n "${SHOTS_CLASSES:-}" ] || "$JAVAC" -encoding UTF-8 -nowarn -cp "$jar" -d "$C" tools/screenshots/FirstRun.java
    rm -rf "$B/prefs-first" "$B/config-first"
    "${xfirst[@]}" -s "$screen" "$JAVA" "${opts[@]}" -Djava.util.prefs.userRoot="$B/prefs-first" \
        -Dhcs.configDir="$B/config-first" -cp "$C:$jar" FirstRun "$out/48-first-run.png" | tee -a "$out/log-first.txt"
fi

# 포크: 저장소 루트에서 상대 경로로 연다. 48만 찍을 때는 건너뛴다(48은 위 첫 실행 JVM이 찍는다). SHOTS_FORK=0이면 원조만
only48=0
[ $# -eq 1 ] && [ "$1" = 48 ] && only48=1
if [ "${SHOTS_FORK:-1}" = 1 ] && [ "$only48" = 0 ]; then
    "${xvfb[@]}" -s "$screen" "$JAVA" "${opts[@]}" -Djava.util.prefs.userRoot="$B/prefs-fork" \
        -Dhcs.configDir="$B/config-fork" -cp "$C:$jar" Shots fork "$out" "$@"
fi

# 원조 2.7.1: 같은 회로, hcs-mips.jar를 회로 옆에 둔다(원조가 JAR 라이브러리를 찾는 방식)
orig=0
if [ $# -eq 0 ] || printf '%s\n' "$@" | grep -qxE '0[23]|16|21|23|37'; then orig=1; fi
[ -n "${SHOTS_ORIG:-}" ] && orig=$SHOTS_ORIG
if [ "$orig" = 1 ]; then
    cp tests/circ/demo-datapath.circ "$B/orig/"
    cp lib-mips/build/libs/hcs-mips.jar "$B/orig/"
    (cd "$B/orig" && "${xorig[@]}" -s "$screen" "$JAVA" "${opts[@]}" -Djava.util.prefs.userRoot=prefs-orig \
        -cp "$C:$root/vendor/logisim-2.7.1/logisim-generic-2.7.1.jar" Shots orig "$out")
fi
ls -la "$out"
