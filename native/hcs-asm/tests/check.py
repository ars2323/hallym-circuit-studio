#!/usr/bin/env python3
"""hcs-asm tests.

1. Golden: tests/asm/<name>.s (+ optional <name>.flags) must produce exactly
   tests/asm/<name>.json. --update rewrites the golden files.
2. Oracle: for every input that assembles without errors, the unmodified spim
   command line (build/oracle/spim -dump) must give the same text and data words.
   The vendor SPIM test programs are checked against the oracle too.
"""
import json
import os
import re
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
TOOL_DIR = os.path.dirname(HERE)
ROOT = os.path.abspath(os.path.join(TOOL_DIR, "..", ".."))
HCS_ASM = os.path.join(TOOL_DIR, "build", "hcs-asm.exe" if os.name == "nt" else "hcs-asm")
ORACLE = os.path.join(TOOL_DIR, "build", "oracle", "spim")
CASES = os.path.join(ROOT, "tests", "asm")
SPIM_SRC = os.path.join(ROOT, "vendor", "spim-9.1.24")

# Vendor programs checked only against the oracle: (path, hcs-asm flags).
ORACLE_ONLY = [
    ("helloworld.s", []),
    ("Tests/tt.core.s", ["-exception"]),
    ("Tests/tt.le.s", ["-exception"]),
    ("Tests/tt.dir.s", ["-exception"]),
    ("Tests/tt.io.s", ["-exception"]),
    ("Tests/tt.bare.s", ["-nopseudo"]),
]

# QtSpim GUI 출력(Save Log File)과 비교: (프로그램, 골든 파일). tests/asm/qtspim/README.md
QTSPIM_GOLDEN = [
    ("helloworld.s", "helloworld.text.txt"),
    ("Tests/tt.core.s", "tt.core.text.txt"),
]

failures = []


def fail(name, message):
    failures.append(f"{name}: {message}")


def run_hcs_asm(path, flags):
    p = subprocess.run([HCS_ASM] + flags + [path], capture_output=True, text=True)
    return p.returncode, p.stdout, p.stderr


def oracle_flags(settings):
    """spim command line with the same settings. hcs-asm always uses QtSpim's defaults
    (extended machine, no delayed branches), so only these two can differ."""
    return ["-exception" if settings["exception_handler"] else "-noexception", "-asm",
            "-pseudo" if settings["accept_pseudo_insts"] else "-nopseudo"]


def run_oracle(path, settings):
    """Return ({addr: word} text, {addr: word} data) from spim -dump."""
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run([ORACLE] + oracle_flags(settings) + ["-dump", "-file", os.path.abspath(path)],
                       cwd=tmp, capture_output=True, text=True, timeout=60)
        text = {}
        with open(os.path.join(tmp, "text.asm"), errors="replace") as f:
            for line in f:
                m = re.match(r"\[0x([0-9a-f]{8})\]\s+0x([0-9a-f]{8})", line)
                if m:
                    text[int(m.group(1), 16)] = int(m.group(2), 16)
        data = {}
        with open(os.path.join(tmp, "data.asm"), errors="replace") as f:
            for line in f:
                m = re.match(r"\[0x([0-9a-f]{8})\]\.\.\.\[0x([0-9a-f]{8})\]\s+0x([0-9a-f]{8})", line)
                if m:
                    continue  # a run of equal words (zeros); absent words read as 0 below
                m = re.match(r"\[0x([0-9a-f]{8})\]\s+((?:0x[0-9a-f]{8}\s*)+)$", line.strip())
                if m:
                    base = int(m.group(1), 16)
                    for i, w in enumerate(m.group(2).split()):
                        data[base + 4 * i] = int(w, 16)
        return text, data


def check_against_oracle(name, path, result):
    text, data = run_oracle(path, result["settings"])
    ours_text = {int(w["addr"], 16): int(w["word"], 16) for w in result["text"]}
    if ours_text != text:
        missing = sorted(set(text) - set(ours_text))[:3]
        extra = sorted(set(ours_text) - set(text))[:3]
        diff = [a for a in sorted(set(text) & set(ours_text)) if text[a] != ours_text[a]][:3]
        fail(name, f"text differs from spim: missing {list(map(hex, missing))}, "
                   f"extra {list(map(hex, extra))}, words {[(hex(a), hex(ours_text[a]), hex(text[a])) for a in diff]}")
    ours_data = {int(d["addr"], 16): int(d["word"], 16) for d in result["data"]}
    for addr, word in ours_data.items():
        if data.get(addr, 0) != word:
            fail(name, f"data word at {addr:#010x}: hcs-asm {word:#010x}, spim {data.get(addr, 0):#010x}")
            break
    user_data = {a: w for a, w in data.items() if 0x10000000 <= a < 0x10040000 and w != 0}
    for addr in sorted(set(user_data) - set(ours_data))[:1]:
        fail(name, f"spim has data word {user_data[addr]:#010x} at {addr:#010x} that hcs-asm does not report")


def main():
    update = "--update" in sys.argv
    with_oracle = "--no-oracle" not in sys.argv  # Windows CI: 원본 spim 오라클은 Linux에서만 돈다
    cases = sorted(f[:-2] for f in os.listdir(CASES) if f.endswith(".s"))
    for name in cases:
        path = os.path.join(CASES, name + ".s")
        flags_path = os.path.join(CASES, name + ".flags")
        flags = open(flags_path).read().split() if os.path.exists(flags_path) else []
        code, out, err = run_hcs_asm(path, flags)
        if code not in (0, 1):
            fail(name, f"exit {code}: {err.strip()}")
            continue
        result = json.loads(out)
        if (code == 0) != (not result["errors"]):
            fail(name, f"exit {code} does not match errors {result['errors']}")
        golden = os.path.join(CASES, name + ".json")
        if update:
            with open(golden, "w") as f:
                f.write(out)
        elif not os.path.exists(golden):
            fail(name, "no golden .json (run with --update)")
        elif open(golden).read() != out:
            fail(name, "output differs from golden .json")
        if not result["errors"] and with_oracle:
            check_against_oracle(name, path, result)

    for rel, flags in (ORACLE_ONLY if with_oracle else []):
        path = os.path.join(SPIM_SRC, rel)
        code, out, err = run_hcs_asm(path, flags)
        if code not in (0, 1):
            fail(rel, f"exit {code}: {err.strip()}")
            continue
        check_against_oracle(rel, path, json.loads(out))

    for rel, golden in QTSPIM_GOLDEN:
        path = os.path.join(SPIM_SRC, rel)
        code, out, err = run_hcs_asm(path, ["-exception"])
        if code not in (0, 1):
            fail(rel, f"exit {code}: {err.strip()}")
            continue
        ours = {int(w["addr"], 16): int(w["word"], 16) for w in json.loads(out)["text"]}
        theirs = {}
        with open(os.path.join(CASES, "qtspim", golden)) as f:
            for line in f:
                if line.startswith("Kernel Text Segment"):
                    break  # hcs-asm은 커널 세그먼트를 내보내지 않는다
                m = re.match(r"\[([0-9a-f]{8})\] ([0-9a-f]{8}) ", line)
                if m:
                    theirs[int(m.group(1), 16)] = int(m.group(2), 16)
        if not theirs or ours != theirs:
            diff = [a for a in sorted(set(ours) | set(theirs)) if ours.get(a) != theirs.get(a)][:3]
            fail(rel, f"differs from QtSpim at {[hex(a) for a in diff]} ({len(theirs)} QtSpim words)")

    total = len(cases) + (len(ORACLE_ONLY) if with_oracle else 0) + len(QTSPIM_GOLDEN)
    if failures:
        print("\n".join(failures))
        print(f"hcs-asm tests: {len(failures)} failure(s) in {total} programs")
        sys.exit(1)
    oracle = f"{total - len(QTSPIM_GOLDEN)} checked against spim" if with_oracle else "spim oracle skipped"
    print(f"hcs-asm tests OK ({len(cases)} golden, {oracle}, {len(QTSPIM_GOLDEN)} against QtSpim GUI output)")


if __name__ == "__main__":
    main()
