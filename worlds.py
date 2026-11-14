#!/usr/bin/env python3
"""Register of all worlds on the server in an Obsidian note (MC_WORLDS_NOTE in .env, default notes/worlds.md).

sync() reads data/plugins/Multiverse-Core/worlds.yml and adds rows for worlds missing from the note
(existing rows are never touched, so you can write your own notes in them). Called by chatbro.py when the
server creates a new world, and by ./mc.sh worlds-sync. Run directly: python3 worlds.py

A new note gets an English header. An existing note keeps its language: if its table header is the
Norwegian one ("| Verden | Nøkkel |"), new rows use "ja"/"nei" in the Supermann column, otherwise "yes"/"no".
"""
import datetime as dt
import os
import re
import sys

MC = os.path.dirname(os.path.abspath(__file__))
WORLDS_YML = os.path.join(MC, "data", "plugins", "Multiverse-Core", "worlds.yml")
def env_setting(key, default):
    """A setting from the environment or .env (mc.sh settings live there), ~ expanded, relative to this folder."""
    v = os.environ.get(key)
    if v is None:
        try:
            for line in open(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".env")):
                if line.startswith(key + "="):
                    v = line.split("=", 1)[1].strip()
        except FileNotFoundError:
            pass
    return os.path.join(os.path.dirname(os.path.abspath(__file__)), os.path.expanduser(v or default))


NOTE = env_setting("MC_WORLDS_NOTE", "notes/worlds.md")   # set MC_WORLDS_NOTE in .env
HEADER = """---
tags: [minecraft]
---
# Minecraft worlds

All worlds on the server, in the order they were created. Rows are added automatically when a world is created
(the chat bridge sees it in the server log, otherwise `./mc.sh worlds-sync`). Nether and End belonging to a world
are indented below it. Feel free to write your own notes in the last column, they are never overwritten.

- **Supermann** = powers and gear from the datapack: the main world and every world with "super" in its name.
- **Switch world:** `/go <name>`, or `/<name>` after the next `./mc.sh restart`. New world: `/new <name>` (ops only).

| World | Key | Type | Created | Supermann | Notes |
|---|---|---|---|---|---|
"""
NORWEGIAN_TABLE = "| Verden | Nøkkel |"   # the original note is in Norwegian; keep its yes/no words


def read_worlds():
    """[(legacy_name, key, environment)] in the file's order."""
    out, key, env, name = [], None, None, None
    for line in open(WORLDS_YML, encoding="utf-8"):
        m = re.match(r"^(minecraft:[a-z0-9_.-]+):\s*$", line)
        if m:
            if key: out.append((name or key.split(":")[1], key, env or "normal"))
            key, env, name = m.group(1), None, None
            continue
        m = re.match(r"^\s+environment:\s*(\S+)", line)
        if m: env = m.group(1)
        m = re.match(r"^\s+legacy-world-name:\s*(\S+)", line)
        if m: name = m.group(1)
    if key: out.append((name or key.split(":")[1], key, env or "normal"))
    return out


def created(key):
    """Date the folder was created (birth time on APFS), otherwise today."""
    k = key.split(":")[1]
    d = os.path.join(MC, "data", "world") if k == "overworld" else os.path.join(MC, "data", "world", "dimensions", "minecraft", k)
    try:
        st = os.stat(d)
        return dt.date.fromtimestamp(getattr(st, "st_birthtime", st.st_mtime)).isoformat()
    except OSError:
        return dt.date.today().isoformat()


EXTRA_SUPER = os.path.join(MC, "data", "superworlds.txt")  # extra Supermann worlds without "super" in the name


def extra_super():
    try:
        return {l.split()[0].lower() for l in open(EXTRA_SUPER, encoding="utf-8") if l.strip() and not l.startswith("#")}
    except OSError:
        return set()


def is_super(name, key):
    return (key in ("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end")
            or "super" in name.lower() or name.lower() in extra_super())


def sync(verbose=False):
    text = open(NOTE, encoding="utf-8").read() if os.path.exists(NOTE) else HEADER
    have = set(re.findall(r"^\|[^|]*\|\s*`(minecraft:[a-z0-9_.-]+)`\s*\|", text, re.M))   # tolerates aligned columns
    yes, no = ("ja", "nei") if NORWEGIAN_TABLE in text else ("yes", "no")
    rows = []
    for name, key, env in read_worlds():
        if key in have: continue
        shown = {"minecraft:overworld": "Main world (world)", "minecraft:the_nether": "Main world nether", "minecraft:the_end": "Main world end"}.get(key, name)
        if env != "normal": shown = "↳ " + shown
        rows.append(f"| {shown} | `{key}` | {env} | {created(key)} | {yes if is_super(name, key) else no} |  |")
        if verbose: print(f"  + {shown} ({key})")
    if not rows and os.path.exists(NOTE):
        return 0
    # new rows at the bottom of the table (the table is the last block in the note)
    text = text.rstrip("\n") + "\n" + "\n".join(rows) + "\n"
    os.makedirs(os.path.dirname(NOTE), exist_ok=True)
    open(NOTE, "w", encoding="utf-8").write(text)
    return len(rows)


if __name__ == "__main__":
    n = sync(verbose=True)
    print(f"{n} new worlds added to {NOTE}" if n else "The note is up to date, no new worlds.")
