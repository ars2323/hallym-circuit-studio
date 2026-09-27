#!/usr/bin/env python3
"""두 스크린샷 폴더를 픽셀 단위로 비교한다(바뀐 것만 다시 검토하기, docs/SCREENSHOTS.md).

쓰기: tools/screenshots/diff.py <기준 폴더> <새 폴더> [--list 바뀐목록.txt]
출력: 바뀐/새/없어진 이미지와 "바뀐 수/전체". 바뀐 이미지는 다른 픽셀 수와 다른 영역(상자)을 적는다.
종료 코드: 모두 같으면 0, 다르면 1.
"""
import os
import sys

import numpy as np
from PIL import Image


def load(p):
    return np.asarray(Image.open(p).convert("RGB"), dtype=np.int16)


def main(argv):
    if len(argv) < 3:
        print(__doc__)
        return 2
    a, b = argv[1], argv[2]
    listfile = argv[argv.index("--list") + 1] if "--list" in argv else None
    na = {f for f in os.listdir(a) if f.endswith(".png")}
    nb = {f for f in os.listdir(b) if f.endswith(".png")}
    changed, added, removed = [], sorted(nb - na), sorted(na - nb)
    for f in sorted(na & nb):
        x, y = load(os.path.join(a, f)), load(os.path.join(b, f))
        if x.shape != y.shape:
            changed.append((f, "size %s -> %s" % (x.shape[1::-1], y.shape[1::-1])))
            continue
        d = np.any(x != y, axis=2)
        n = int(d.sum())
        if n:
            ys, xs = np.nonzero(d)
            changed.append((f, "%d px in x%d-%d y%d-%d" % (n, xs.min(), xs.max(), ys.min(), ys.max())))
    for f, why in changed:
        print("CHANGED", f, why)
    for f in added:
        print("ADDED", f)
    for f in removed:
        print("REMOVED", f)
    total = len(nb)
    print("summary: %d changed, %d added, %d removed / %d images" % (len(changed), len(added), len(removed), total))
    if listfile:
        with open(listfile, "w", encoding="utf-8") as out:
            for f, _ in changed:
                out.write(f + "\n")
            for f in added:
                out.write(f + "\n")
    return 0 if not (changed or added or removed) else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
