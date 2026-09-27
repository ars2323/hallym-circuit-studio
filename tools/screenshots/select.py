#!/usr/bin/env python3
"""이번 라운드에 찍을 장면을 고른다(D-121, screens/scenes.tsv).

쓰기: tools/screenshots/select.py [기준 커밋, 기본: 마지막 v 태그]  [--all] [--filter 폴더] [--orig]
  core 장면은 늘 고른다. feature 장면은 기준 커밋 이후 바뀐 파일이 그 장면의 paths로 시작할 때만 고른다.
  global 줄의 paths가 바뀌었으면 모든 장면을 고른다.
  --filter 폴더: 찍은 폴더에서 core로만 고른 장면의 core 아닌 이미지를 지운다(검토 대상은 core 이미지뿐).
  --orig: 원조 2.7.1 장면(-orig)을 다시 찍어야 하는지(orig 줄의 paths가 바뀌었는지) 1/0으로 찍고 끝낸다.
출력: 첫 줄에 장면 번호(공백으로), 이어서 장면마다 고른 이유.
"""
import os
import re
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))


def rows():
    out = []
    with open(os.path.join(ROOT, "screens", "scenes.tsv"), encoding="utf-8") as f:
        for line in f:
            if not line.strip() or line.startswith("#"):
                continue
            cols = line.rstrip("\n").split("\t")
            cols += [""] * (4 - len(cols))
            scene, kind, images, paths = cols[:4]
            out.append((scene, kind, [i for i in images.split(",") if i and i != "-"],
                        [p for p in paths.split(",") if p]))
    return out


def git(*args):
    return subprocess.run(["git"] + list(args), cwd=ROOT, capture_output=True, text=True, check=True).stdout


def changed(base, head="HEAD"):
    return git("diff", "--name-only", base + "..." + head).split()


SHOTS = "tools/screenshots/Shots.java"
ORIG_SCENES = {"02", "03", "16", "21", "23", "37"}  # 원조 2.7.1로도 찍는 장면(-orig)
BUNDLES = ("app/src-hcs/kr/ac/hallym/hcs/app/messages.properties",
           "app/src-hcs/kr/ac/hallym/hcs/app/messages_ko.properties",
           "app/src-hcs/kr/ac/hallym/hcs/app/names.properties")
SIGNATURE = re.compile(r"^    (?!return|if|for|while|switch|catch|else|new)[\w<>\[\], .]+ \w+\(.*\)\s*(throws [\w., ]+)?\{\s*$")


def hunks(base, head, path):
    """바뀐 줄 번호(새 파일 기준)와 +/- 줄의 글."""
    lines, texts = [], []
    for line in git("diff", "-U0", base + "..." + head, "--", path).split("\n"):
        m = re.match(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@", line)
        if m:
            start, count = int(m.group(1)), int(m.group(2) or "1")
            lines.extend(range(max(start, 1), start + max(count, 1)))
        elif line[:1] in "+-" and not line.startswith(("+++", "---")):
            texts.append(line[1:])
    return lines, texts


def javadoc_scene(src, sig):
    """메서드 머리 줄 위 javadoc의 첫 "NN:"(장면 번호). 없으면 None(도우미 → 모든 장면)."""
    j, doc = sig - 1, []
    while j >= 0 and src[j].strip().startswith(("*", "/**", "@")):
        doc.insert(0, src[j])
        j -= 1
    m = re.search(r"(?:/\*\*|\*)\s*(\d{1,2})[a-z]?:", " ".join(doc))
    return m.group(1).zfill(2) if m else None


def shots_scene(src, ln):
    """Shots.java의 한 줄이 속한 장면: run() 안이면 위쪽 가장 가까운 want(scenes, "NN"), 장면 메서드면 그 javadoc 번호."""
    i = ln - 1
    if i < len(src) and src[i].strip().startswith(("*", "/**")):
        while i < len(src) and not SIGNATURE.match(src[i]):
            i += 1
        return javadoc_scene(src, i) if i < len(src) else None
    while i >= 0:
        m = re.search(r'want\(scenes, "(\d\d)"\)', src[i])
        if m:
            return m.group(1)
        if SIGNATURE.match(src[i]):
            return javadoc_scene(src, i)
        i -= 1
    return None


def key_files(keys):
    """문구 키를 쓰는 소스 파일. 못 찾은 키가 있으면 None(동적으로 만든 키 → 모든 장면)."""
    out = set()
    for k in keys:
        found = subprocess.run(["git", "grep", "-l", "-F", '"' + k + '"', "--", "app/src-hcs", "app/src", "lib-mips/src"],
                               cwd=ROOT, capture_output=True, text=True).stdout.split()
        if not found:
            return None
        out.update(found)
    return out


def refine(base, head, files):
    """Shots.java와 문구 파일은 통째로 모든 장면이 아니라, 바뀐 곳이 닿는 장면만 고르게 좁힌다.
    (남은 파일 목록, 더 고를 장면{장면: 이유}, 모든 장면을 찍을 이유 또는 None)"""
    rest, extra, glob = [], {}, None
    for f in files:
        if f == SHOTS:
            src = git("show", head + ":" + f).split("\n")
            lines, _ = hunks(base, head, f)
            for ln in lines:
                sc = shots_scene(src, ln)
                if sc is None:
                    glob = glob or "%s:%d (도우미)" % (f, ln)
                else:
                    extra.setdefault(sc, "%s:%d" % (f, ln))
        elif f in BUNDLES:
            _, texts = hunks(base, head, f)
            keys = {t.split("=", 1)[0].strip() for t in texts if "=" in t and not t.lstrip().startswith("#")}
            users = key_files(keys)
            if users is None:
                glob = glob or "%s (쓰는 곳을 못 찾은 키)" % f
            else:
                rest.extend(sorted(users))
        else:
            rest.append(f)
    return rest, extra, glob


def last_tag():
    return subprocess.run(["git", "describe", "--tags", "--abbrev=0", "--match", "v*"], cwd=ROOT,
                          capture_output=True, text=True, check=True).stdout.strip()


def select(table, files, extra=None, forced=None):
    """(고른 장면, 이유, core로만 고른 장면) — core 장면이라도 paths가 바뀌면 이미지 전체를 찍고 검토한다.
    extra는 refine()이 Shots.java에서 찾은 장면, forced는 모든 장면을 찍을 이유."""
    extra = extra or {}
    glob = next((p for s, k, _, p in table if s == "global"), [])
    hit_global = [f for f in files if any(f.startswith(p) for p in glob)]
    if forced:
        hit_global.insert(0, forced)
    picked, why, core_only = [], [], set()
    for scene, kind, images, paths in table:
        if kind not in ("core", "feature"):
            continue
        hits = [f for f in files if any(f.startswith(p) for p in paths)]
        if scene in extra:
            hits.insert(0, extra[scene])
        if hit_global:
            picked.append(scene)
            why.append("%s: global (%s)" % (scene, hit_global[0]))
        elif hits:
            picked.append(scene)
            why.append("%s: %s%s" % (scene, hits[0], " (core, 전체)" if kind == "core" else ""))
        elif kind == "core":
            picked.append(scene)
            core_only.add(scene)
            why.append("%s: core" % scene)
    return picked, why, core_only


def filter_dir(table, core_only, folder):
    keep = {i for s, k, images, _ in table if s in core_only for i in images}
    removed = 0
    for name in sorted(os.listdir(folder)):
        if not name.endswith(".png"):
            continue
        stem = name[:-4]
        if stem[:2] in core_only and stem not in keep:
            os.remove(os.path.join(folder, name))
            removed += 1
    return removed


def main(argv):
    args = []
    folder = None
    it = iter(argv[1:])
    for a in it:
        if a == "--filter":
            folder = next(it)
        elif not a.startswith("--"):
            args.append(a)
    table = rows()
    order = [s for s, k, _, _ in table if k in ("core", "feature")]
    if "--all" in argv:
        print(" ".join(order))
        print("all: --all")
        return 0
    base = args[0] if args else last_tag()
    files = changed(base)
    if "--orig" in argv:
        paths = next((p for s, k, _, p in table if s == "orig"), [])
        _, extra, forced = refine(base, "HEAD", files)
        shots = bool(forced and forced.startswith(SHOTS)) or any(sc in ORIG_SCENES for sc in extra)
        print(1 if shots or any(f.startswith(p) for f in files for p in paths) else 0)
        return 0
    rest, extra, forced = refine(base, "HEAD", files)
    picked, why, core_only = select(table, rest, extra, forced)
    if folder:
        print("removed %d non-core images of core-only scenes" % filter_dir(table, core_only, folder))
        return 0
    print(" ".join(picked))
    print("base %s, %d changed files, %d of %d scenes" % (base, len(files), len(picked), len(order)))
    for w in why:
        print(w)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
