#!/usr/bin/env python3
"""
Mod-compatibility guard for CoralIntel's mixins. Run by CI (and by you: python3 scripts/check_mixins.py).

Why: only ONE mod can @Redirect / @Overwrite / @ModifyConstant a given spot in Minecraft. If two mods
try, the loser silently stops working (this is how another mod's key hook once killed CoralIntel's
keybinds and GUIs). @Inject / @ModifyArg / @ModifyVariable stack fine, so those are allowed.

Rules for everything under src/main/java/coralintel/mixin:
  1. No @Redirect, @Overwrite or @ModifyConstant.
  2. Every injector / accessor method is named "coralintel$...", so it can never share a name with
     another mod's method once Mixin merges it into the game class.
  3. Keep logic out of mixins: put it in a normal class and call it (see util/TabListFormatter).
"""
import pathlib
import re
import sys

MIXIN_DIR = pathlib.Path(__file__).resolve().parent.parent / "src/main/java/coralintel/mixin"
BANNED = ("Redirect", "Overwrite", "ModifyConstant")
NEEDS_PREFIX = ("Inject", "ModifyArg", "ModifyArgs", "ModifyVariable", "Accessor", "Invoker")
PREFIX = "coralintel$"


def strip_comments(text):
    text = re.sub(r"/\*.*?\*/", lambda m: "\n" * m.group(0).count("\n"), text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


def after_annotation(text, start):
    """Index just past the annotation that starts at `start` (handles nested parentheses)."""
    i = start
    while i < len(text) and (text[i].isalnum() or text[i] in "@_."):
        i += 1
    if i < len(text) and text[i] == "(":
        depth = 0
        while i < len(text):
            if text[i] == "(":
                depth += 1
            elif text[i] == ")":
                depth -= 1
                if depth == 0:
                    return i + 1
            i += 1
    return i


def method_name_after(text, pos):
    m = re.compile(r"(?:@\w+(?:\([^)]*\))?\s*)*[\w<>\[\],?.$ \t\n]*?([\w$]+)\s*\(").search(text, pos)
    return m.group(1) if m else None


problems = []
for path in sorted(MIXIN_DIR.glob("*.java")):
    raw = path.read_text(encoding="utf-8")
    text = strip_comments(raw)
    line_of = lambda idx: text.count("\n", 0, idx) + 1

    for m in re.finditer(r"@(" + "|".join(BANNED) + r")\b", text):
        problems.append(f"{path.name}:{line_of(m.start())}: @{m.group(1)} is not allowed (another mod can't share that spot)")

    for m in re.finditer(r"@(" + "|".join(NEEDS_PREFIX) + r")\b", text):
        end = after_annotation(text, m.start())
        name = method_name_after(text, end)
        if name is None or not name.startswith(PREFIX):
            problems.append(f"{path.name}:{line_of(m.start())}: handler for @{m.group(1)} must be named '{PREFIX}...' (found '{name}')")

if problems:
    print("CoralIntel mixin compatibility check FAILED:\n")
    print("\n".join("  - " + p for p in problems))
    sys.exit(1)

print("CoralIntel mixin compatibility check passed.")
