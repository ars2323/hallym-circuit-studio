#!/usr/bin/env python3
"""Regenerate the disassembler goldens in tests/disasm/ with hcs-asm -disasm (Z-04, D-127).

Every golden is SPIM's own instruction printer output (format_an_inst in the unmodified
vendor/spim-9.1.24/CPU core, called by hcs-asm -disasm). The Java disassembler
(kr.ac.hallym.hcs.mips.disasm) is written from the MIPS32 definition and compared with these
files by DisassemblerGoldenTest; it never reads SPIM's source.

Programs:
  keys.s     every opcode key of the MIPS32 encoding space (primary opcode, SPECIAL/SPECIAL2/
             SPECIAL3 funct, REGIMM rt, COP0/COP1 rs x funct, COP2/COP1X rs and funct), one
             .word each, so every instruction SPIM decodes and the unknown words appear.
  fields.s   every word SPIM decodes in keys.s again with representative field values
             (registers 0, 1, 31; immediates 0, 1, -1, 0x7fff, -0x8000; shamt 0, 1, 31;
             jump targets inside and outside the text segment; branch offsets forward,
             backward and zero).
  targets.s  (hand-written) branches and jumps to labels, so SPIM prints its [label] and
             [label-0x<pc>] brackets.
  quirks.s   instructions whose assembled listing differs from SPIM's listing of the same word
             placed with .word (see quirks() below), assembled from source.
  tests/asm/*.s and tests/mips/*.s with -exception (start code at 0x00400000, main after it), and
             SPIM's helloworld.s and Tests/tt.core.s (QtSpim's GUI listing is tests/asm/qtspim/).
             Programs that do not assemble are skipped: they cannot be loaded.

Usage: tools/gen-disasm-golden.sh   (HCS_ASM=<path> to use another hcs-asm)
"""
import os
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
OUT = os.path.join(ROOT, "tests", "disasm")
HCS_ASM = os.environ.get("HCS_ASM") or os.path.join(
    ROOT, "native", "hcs-asm", "build", "hcs-asm.exe" if os.name == "nt" else "hcs-asm")

UNKNOWN_PREFIX = "<unknown"  # how SPIM's printer shows a word it does not decode


def field(value, hi, lo):
    return (value & ((1 << (hi - lo + 1)) - 1)) << lo


def rtype(op, rs, rt, rd, sh, fn):
    return field(op, 31, 26) | field(rs, 25, 21) | field(rt, 20, 16) | field(rd, 15, 11) | field(sh, 10, 6) | field(fn, 5, 0)


def itype(op, rs, rt, imm):
    return field(op, 31, 26) | field(rs, 25, 21) | field(rt, 20, 16) | field(imm, 15, 0)


# ---- keys.s: the opcode keys of the MIPS32 encoding space ------------------------------------
# Each entry: (word, cls, key) where cls says which fields are free for fields.s.
# Free fields are filled with rs=9, rt=10, rd=11, shamt/fd=12 or imm=0x1234 so that a decoder that
# looks at the wrong bits shows it.

def keys():
    out = []
    for op in range(64):
        if op in (0, 28, 31):  # SPECIAL, SPECIAL2, SPECIAL3: funct
            for fn in range(64):
                out.append((rtype(op, 9, 10, 11, 12, fn), "r", (op, fn)))
        elif op == 1:  # REGIMM: rt
            for rt in range(32):
                out.append((itype(op, 9, rt, 0x1234), "regimm", (op, rt)))
        elif op in (16, 17):  # COP0, COP1: rs x funct
            for rs in range(32):
                for fn in range(64):
                    if op == 17 and rs == 8:  # BC1: tf (bit 16) and nd (bit 17) instead of funct
                        if fn < 4:
                            out.append((itype(op, rs, fn, 0x1234), "bc", (op, rs, fn)))
                        continue
                    out.append((rtype(op, rs, 10, 11, 12 if op == 17 else 0, fn), "cop", (op, rs, fn)))
        elif op in (18, 19):  # COP2, COP1X (MIPS32) / COP3: rs, and funct for COP1X
            for rs in range(32):
                for fn in (0, 1, 32, 63) if op == 19 else (0,):
                    out.append((rtype(op, rs, 10, 11, 12, fn), "cop", (op, rs, fn)))
            if op == 19:
                for fn in range(64):
                    out.append((rtype(op, 9, 10, 11, 12, fn), "cop", (op, 9, fn)))
        elif op in (2, 3):  # J, JAL
            out.append((field(op, 31, 26) | 0x0100009, "j", (op,)))
        else:
            out.append((itype(op, 9, 10, 0x1234), "i", (op,)))
    return out


# ---- fields.s: representative field values for each word SPIM decodes --------------------------
IMMS = [0, 1, -1, 0x7fff, -0x8000]
REG3 = [(0, 0, 0), (1, 1, 1), (31, 31, 31), (1, 31, 0), (31, 0, 1), (4, 5, 6)]
SHAMTS = [0, 1, 31, 7]


def variants(word, cls):
    op = (word >> 26) & 63
    out = []
    if cls == "r":
        fn = word & 63
        for (rs, rt, rd), sh in zip(REG3, SHAMTS + [16, 5]):
            out.append(rtype(op, rs, rt, rd, sh, fn))
        out.append(rtype(op, 0, 0, 0, 0, fn))  # all fields zero (for sll: nop)
    elif cls == "regimm":
        rt = (word >> 16) & 31
        for rs in (0, 1, 31):
            for imm in IMMS:
                out.append(itype(op, rs, rt, imm))
    elif cls == "i":
        for rs, rt in ((0, 0), (1, 31), (31, 1)):
            for imm in IMMS:
                out.append(itype(op, rs, rt, imm))
        out.append(itype(op, 1, 1, 0x1234))
        out.append(itype(op, 31, 31, 0xfff0))
    elif cls == "j":
        # 0x00400000 (text start), 0x00400024, 0x00000000, 0x0ffffffc, 0x00100000 (below text),
        # 0x01000000 and 0x08000000 (above text)
        for target in (0x100000, 0x100009, 0, 0x3ffffff, 0x40000, 0x400000, 0x2000000):
            out.append(field(op, 31, 26) | target)
    elif cls == "bc":
        tf_nd = (word >> 16) & 3
        for cc in (0, 1, 7):
            for imm in IMMS:
                out.append(itype(op, 8, (cc << 2) | tf_nd, imm))
    elif cls == "cop":
        rs = (word >> 21) & 31
        fn = word & 63
        for (rt, rd, sh) in REG3:
            out.append(rtype(op, rs, rt, rd, sh, fn))
        out.append(rtype(op, rs, 8, 4, 20, fn))  # FP compare with condition code 5 in fd
    return out


# ---- quirks.s: instructions whose assembled listing differs from SPIM's listing of the same word -----
# SPIM's own decoder (used for .word) shows these words differently from the listing of the assembled
# instruction: FP compares and conditional moves put fields elsewhere, movt/bc1fl/bc1tl/bc2t... lose
# their tf/nd bit, trunc.w.s shows as suxc1. The disassembler follows the assembled listing (what
# QtSpim shows for a program), so these are assembled from source here with representative fields.
CONDS = ["f", "un", "eq", "ueq", "olt", "ult", "ole", "ule", "sf", "ngle", "seq", "ngl", "lt", "nge", "le", "ngt"]


def quirks():
    out = ["main:"]
    for cond in CONDS:
        for fmt in ("s", "d"):
            for cc, fs, ft in ((None, 0, 2), (1, 30, 4), (7, 2, 31), (0, 4, 6)):
                out.append("c.%s.%s %s$f%d, $f%d" % (cond, fmt, "" if cc is None else "%d, " % cc, fs, ft))
    for name in ("movf", "movt"):
        for fmt in ("s", "d"):
            for fd, fs, cc in ((0, 2, 0), (4, 2, 3), (30, 31, 7)):
                out.append("%s.%s $f%d, $f%d, %d" % (name, fmt, fd, fs, cc))
    for name in ("movz", "movn"):
        for fmt in ("s", "d"):
            for fd, fs, rt in ((4, 2, "$t1"), (0, 0, "$0"), (30, 31, "$31")):
                out.append("%s.%s $f%d, $f%d, %s" % (name, fmt, fd, fs, rt))
    for fd, fs in ((0, 2), (31, 1), (4, 30)):
        out.append("trunc.w.s $f%d, $f%d" % (fd, fs))
    for z in ("1", "2"):
        for name in ("f", "t", "fl", "tl"):
            for cc, target in ((None, "main"), (3, "fwd"), (7, "main")):
                out.append("bc%s%s %s%s" % (z, name, "" if cc is None else "%d, " % cc, target))
    for name in ("movf", "movt"):
        for rd, rs, cc in ((3, 2, 1), (31, 31, 7), (0, 0, 0), (8, 9, 4)):
            out.append("%s $%d, $%d, %d" % (name, rd, rs, cc))
    out.append("fwd:")
    out.append("jr $31")
    return out


def write_words(path, title, words):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("# %s\n# Generated by tools/gen-disasm-golden.sh (D-127). Do not edit.\n" % title)
        f.write("\t.text\n")
        for i in range(0, len(words), 8):
            f.write("\t.word " + ", ".join("0x%08x" % w for w in words[i:i + 8]) + "\n")


def disasm(rel, flags):
    """hcs-asm -disasm on ROOT/rel. Returns (exit code, golden text)."""
    p = subprocess.run([HCS_ASM] + flags + ["-disasm", rel], cwd=ROOT, capture_output=True)
    head = "# hcs-asm %s -disasm %s\n" % (" ".join(flags), rel.replace(os.sep, "/"))
    return p.returncode, head + p.stdout.decode("utf-8"), p.stderr.decode("utf-8", "replace")


def write_golden(name, rel, flags, allow_errors=False):
    code, text, err = disasm(rel, flags)
    if code != 0 and not (allow_errors and code == 1):
        return False, err
    with open(os.path.join(OUT, name), "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    return True, text


def main():
    if not os.access(HCS_ASM, os.X_OK):
        sys.exit("hcs-asm not built: make -C native/hcs-asm (%s)" % HCS_ASM)
    os.makedirs(OUT, exist_ok=True)
    written = []

    ks = keys()
    write_words(os.path.join(OUT, "keys.s"), "Every opcode key of the MIPS32 encoding space, one word each.",
                [w for w, _, _ in ks])
    ok, text = write_golden("keys.txt", "tests/disasm/keys.s", ["-noexception"])
    if not ok:
        sys.exit("keys.s: " + text)
    written.append("keys.txt")
    lines = [l for l in text.splitlines() if l and not l.startswith("#") and not l.startswith("label ")]
    assert len(lines) == len(ks), (len(lines), len(ks))
    fields = []
    for (w, cls, _), l in zip(ks, lines):
        assert int(l.split(" ")[1], 16) == w
        if not l.split(" ", 2)[2].startswith(UNKNOWN_PREFIX):
            fields.extend(variants(w, cls))
    write_words(os.path.join(OUT, "fields.s"),
                "Representative field values for every word SPIM decodes in keys.s.", fields)
    ok, text = write_golden("fields.txt", "tests/disasm/fields.s", ["-noexception"])
    if not ok:
        sys.exit("fields.s: " + text)
    written.append("fields.txt")

    with open(os.path.join(OUT, "quirks.s"), "w", encoding="utf-8", newline="\n") as f:
        f.write("# Instructions SPIM lists differently when assembled than as a .word (D-127).\n"
                "# Generated by tools/gen-disasm-golden.sh. Do not edit.\n\t.text\n")
        for l in quirks():
            f.write(l + "\n" if l.endswith(":") else "\t" + l + "\n")
    for name, rel, flags in (("targets.txt", "tests/disasm/targets.s", ["-exception"]),
                             ("quirks.txt", "tests/disasm/quirks.s", ["-noexception"])):
        ok, text = write_golden(name, rel, flags)
        if not ok:
            sys.exit(rel + ": " + text)
        written.append(name)

    skipped = []
    programs = []
    for d in ("asm", "mips"):
        for name in sorted(os.listdir(os.path.join(ROOT, "tests", d))):
            if name.endswith(".s"):
                programs.append(("%s-%s.txt" % (d, name[:-2]), "tests/%s/%s" % (d, name), ["-exception"]))
    # SPIM's own test programs, also in tests/asm/qtspim/ as QtSpim's GUI listing. tt.core.s has two
    # jumps whose target differs in the high 4 bits; SPIM reports them and loads the program anyway.
    for rel in ("helloworld.s", "Tests/tt.core.s"):
        programs.append(("spim-%s.txt" % os.path.basename(rel)[:-2], "vendor/spim-9.1.24/" + rel, ["-exception"]))
    for golden, rel, flags in programs:
        ok, _ = write_golden(golden, rel, flags, allow_errors=rel.startswith("vendor/"))
        if ok:
            written.append(golden)
        else:
            skipped.append(rel)
            if os.path.exists(os.path.join(OUT, golden)):
                os.remove(os.path.join(OUT, golden))

    words = 0
    for g in written:
        with open(os.path.join(OUT, g), encoding="utf-8") as f:
            words += sum(1 for l in f if l[:1] not in ("#", "l", "\n"))
    print("disasm goldens: %d files, %d words; skipped (assembly errors): %s"
          % (len(written), words, ", ".join(skipped) or "none"))


if __name__ == "__main__":
    main()
