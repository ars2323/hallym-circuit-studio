#!/usr/bin/env python3
"""사용자 원본(resources/)과 Hallym MIPS(ref/)에서 assets/로 파생 파일을 가져온다 (CLAUDE.md 8절).

이미지를 열거나 다시 인코딩하지 않는다. zip에서 꺼내 이름만 바꾸거나 파일을 그대로 복사한다.
끝나면 assets/MANIFEST.sha256을 다시 쓴다. CI는 tools/verify-assets.sh로 이 목록과 대조한다.

    python3 tools/import-assets.py
"""
import hashlib
import io
import os
import re
import shutil
import zipfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
RES = os.path.join(ROOT, "resources")
REF_BRAND = os.path.join(ROOT, "ref", "hallym-mips-simulator", "QtSpim", "edu", "theme", "brand")
ASSETS = os.path.join(ROOT, "assets")

PRETENDARD = ["Regular", "Medium", "SemiBold", "Bold"]

CHARACTER_BASIC = {
    "캐릭터 기본형(조합).png.png": "haram-hari.png",
    "캐릭터-기본형(하람).png.png": "haram.png",
    "캐릭터 기본형(하리).png.png": "hari.png",
}

CHARACTER_ACTIONS = {
    "인사": "greeting", "최고": "best", "OK": "ok", "안내": "guide", "go": "go",
    "소통": "talk", "셀카": "selfie", "식사(먹방)": "meal", "축하": "congrats", "공지": "notice",
    "금지": "no", "교육": "education", "궁금해": "curious", "사랑해": "love", "감사": "thanks",
    "감동": "moved", "팻말": "sign", "명절": "holiday", "입학(졸업)": "graduation", "운동": "exercise",
}

# Hallym MIPS가 assets/ci/marks/의 SVG와 그로부터 렌더한 PNG를 둔 곳(D-008). 이름만 바꾼다.
LOGO_FILES = {
    "symbol-basic.svg": "symbol-basic.svg",
    "symbol-basic-64.png": "symbol-basic-64.png",
    "symbol-basic-64@2x.png": "symbol-basic-64@2x.png",
    "emblem-a-navy.svg": "emblem-a-navy.svg",
    "emblem-a-navy-112.png": "emblem-a-navy-112.png",
    "emblem-a-navy-112@2x.png": "emblem-a-navy-112@2x.png",
    "logotype-ko-en.svg": "logotype-ko-en.svg",
    "logotype-ko-en-220.png": "logotype-ko-en-220.png",
    "logotype-ko-en-220@2x.png": "logotype-ko-en-220@2x.png",
    "signature-h-ko-en.svg": "signature-h-ko-en.svg",
    "signature-h-ko-en-260.png": "signature-h-ko-en-260.png",
    "signature-h-ko-en-260@2x.png": "signature-h-ko-en-260@2x.png",
    "signature-h-ko-en-320.png": "signature-h-ko-en-320.png",
    "signature-h-ko-en-320@2x.png": "signature-h-ko-en-320@2x.png",
    "app-16.png": "app-16.png",
    "app-24.png": "app-24.png",
    "app-32.png": "app-32.png",
    "app-48.png": "app-48.png",
    "app-64.png": "app-64.png",
    "app-256.png": "app-256.png",
    "HallymMIPS.ico": "app.ico",
}


def restore_name(name):
    """zip 안 한글 파일명이 #Uc751#Uc6a9처럼 인코딩돼 있으면 되살린다."""
    return re.sub(r"#U([0-9a-fA-F]{4})", lambda m: chr(int(m.group(1), 16)), name)


def entries(zf):
    """{복원한 이름: ZipInfo}. 경로의 마지막 부분만 쓴다."""
    return {restore_name(i.filename).rsplit("/", 1)[-1]: i for i in zf.infolist() if not i.is_dir()}


def write(rel, data):
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(data)


def import_fonts():
    with zipfile.ZipFile(os.path.join(RES, "font", "Pretendard-1.3.9.zip")) as zf:
        for w in PRETENDARD:
            write(f"fonts/pretendard/Pretendard-{w}.otf", zf.read(f"public/static/Pretendard-{w}.otf"))
        write("fonts/pretendard/LICENSE.txt", zf.read("LICENSE.txt"))


def import_characters():
    with zipfile.ZipFile(os.path.join(RES, "hallym", "character", "character.zip")) as outer:
        inner = {restore_name(i.filename).rsplit("/", 1)[-1]: i for i in outer.infolist()}
        with zipfile.ZipFile(io.BytesIO(outer.read(inner["캐릭터 기본형(PNG).zip"]))) as zf:
            found = entries(zf)
            for src, dst in CHARACTER_BASIC.items():
                write(f"hallym/character/{dst}", zf.read(found[src]))
        with zipfile.ZipFile(io.BytesIO(outer.read(inner["응용동작(PNG).zip"]))) as zf:
            found = entries(zf)
            for ko, slug in CHARACTER_ACTIONS.items():
                write(f"hallym/character/haram-hari-{slug}.png", zf.read(found[f"응용동작_{ko}.png"]))
        # 가이드라인 PDF는 외부 노출 금지 문서라 가져오지 않는다.


def import_logos():
    for src, dst in LOGO_FILES.items():
        with open(os.path.join(REF_BRAND, src), "rb") as f:
            write(f"hallym/logo/{dst}", f.read())


def write_manifest():
    lines = []
    for dirpath, _, files in os.walk(ASSETS):
        for name in files:
            path = os.path.join(dirpath, name)
            rel = os.path.relpath(path, ASSETS)
            if rel in ("MANIFEST.sha256", "README.md"):
                continue
            with open(path, "rb") as f:
                lines.append(f"{hashlib.sha256(f.read()).hexdigest()}  {rel}")
    with open(os.path.join(ASSETS, "MANIFEST.sha256"), "w") as f:
        f.write("\n".join(sorted(lines, key=lambda l: l[66:])) + "\n")
    return len(lines)


def main():
    for sub in ("fonts", "hallym"):
        shutil.rmtree(os.path.join(ASSETS, sub), ignore_errors=True)
    import_fonts()
    import_characters()
    import_logos()
    print(f"assets: {write_manifest()} files")


if __name__ == "__main__":
    main()
