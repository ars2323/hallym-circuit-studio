#!/usr/bin/env python3
"""PreToolUse hook for compat-reviewer: allow only read-only git inspection in Bash.

Allowed: git diff | log | show | merge-base | rev-parse, optionally piped into
grep | head | tail | wc | sort | uniq | cut. Everything else is blocked (exit 2).
"""
import json
import shlex
import sys

GIT_SUBCOMMANDS = {"diff", "log", "show", "merge-base", "rev-parse"}
FILTERS = {"grep", "head", "tail", "wc", "sort", "uniq", "cut"}
# Options that write files or run external programs.
BANNED_OPTIONS = ("--output", "--ext-diff", "--exec", "--upload-pack")


def block(reason):
    print(f"compat-reviewer는 읽기 전용이다. 차단: {reason}", file=sys.stderr)
    sys.exit(2)


def main():
    data = json.load(sys.stdin)
    if data.get("tool_name") != "Bash":
        return
    command = data.get("tool_input", {}).get("command", "")
    if "`" in command or "$(" in command or "\n" in command:
        block("명령 치환·여러 줄 명령")

    lexer = shlex.shlex(command, posix=True, punctuation_chars=True)
    lexer.whitespace_split = True
    try:
        tokens = list(lexer)
    except ValueError as e:
        block(f"구문 해석 실패 ({e})")

    segments, current = [], []
    for tok in tokens:
        if tok == "|":
            segments.append(current)
            current = []
        elif tok and set(tok) <= set(";&|<>()"):
            block(f"허용하지 않는 연산자 '{tok}'")
        else:
            current.append(tok)
    segments.append(current)
    if any(not seg for seg in segments):
        block("빈 파이프 구간")

    first = segments[0]
    if first[0] != "git" or len(first) < 2:
        block("git diff/log/show/merge-base/rev-parse만 쓸 수 있다")
    if first[1] not in GIT_SUBCOMMANDS:
        block(f"git {first[1]} (전역 옵션·다른 하위 명령 금지)")
    for tok in first[2:]:
        if tok.startswith(BANNED_OPTIONS):
            block(f"옵션 {tok}")

    for seg in segments[1:]:
        if seg[0] not in FILTERS:
            block(f"파이프 대상 {seg[0]} (grep/head/tail/wc/sort/uniq/cut만)")
        if seg[0] == "sort" and any(t.startswith(("-o", "--output")) for t in seg[1:]):
            block("sort -o")


if __name__ == "__main__":
    main()
