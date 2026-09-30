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
    print(f"compat-reviewer is read-only. Blocked: {reason}", file=sys.stderr)
    sys.exit(2)


def main():
    data = json.load(sys.stdin)
    if data.get("tool_name") != "Bash":
        return
    command = data.get("tool_input", {}).get("command", "")
    if "`" in command or "$(" in command or "\n" in command:
        block("command substitution or a multi-line command")

    lexer = shlex.shlex(command, posix=True, punctuation_chars=True)
    lexer.whitespace_split = True
    try:
        tokens = list(lexer)
    except ValueError as e:
        block(f"failed to parse the syntax ({e})")

    segments, current = [], []
    for tok in tokens:
        if tok == "|":
            segments.append(current)
            current = []
        elif tok and set(tok) <= set(";&|<>()"):
            block(f"operator '{tok}' is not allowed")
        else:
            current.append(tok)
    segments.append(current)
    if any(not seg for seg in segments):
        block("empty pipe segment")

    first = segments[0]
    if first[0] != "git" or len(first) < 2:
        block("only git diff/log/show/merge-base/rev-parse can be used")
    if first[1] not in GIT_SUBCOMMANDS:
        block(f"git {first[1]} (global options and other subcommands are forbidden)")
    for tok in first[2:]:
        if tok.startswith(BANNED_OPTIONS):
            block(f"option {tok}")

    for seg in segments[1:]:
        if seg[0] not in FILTERS:
            block(f"pipe target {seg[0]} (only grep/head/tail/wc/sort/uniq/cut)")
        if seg[0] == "sort" and any(t.startswith(("-o", "--output")) for t in seg[1:]):
            block("sort -o")


if __name__ == "__main__":
    main()
