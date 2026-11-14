#!/usr/bin/env python3
"""Chat bridge: lets players ask an AI model for things right in the Minecraft chat.

Follows the server log (docker logs -f mc). When an approved player writes a chat line that starts
with the keyword (CHATBRO_KEYWORDS, default "ai"), the request goes to the configured model: Codex (plans the commands, we filter and run
them) or Claude Code without a window (claude -p) in the server folder, allowed to run ./mc.sh cmd (RCON)
and read files. The answer is sent back to the player with tellraw.

Run in the background with service/ (launchd or systemd), or just python3 chatbro.py. Log: chatbro.log in the same folder.
"/ai …" works as a command too (alias in aliases.yml); then only the player sees it.
Special requests: "ai help", "ai stop" (cancel the job), "ai reset" (new conversation),
"ai undo" (put back what was built last), "ai make supertool <wish>", "ai change <id> <wish>",
"ai redraw <id>".
"""
import datetime as dt
import json
import os
import re
import shutil
import signal
import subprocess
import sys
import threading
import time
import uuid

import findbuild                               # /build_<code>: GrabCraft catalog builds without an AI model

MC_DIR = os.path.dirname(os.path.abspath(__file__))
LOG_FILE = os.path.join(MC_DIR, "chatbro.log")
SESSION_FILE = os.path.join(MC_DIR, ".chatbro-session")
GUESTS_FILE = os.path.join(MC_DIR, ".chatbro-access.json")   # names (lower case) or "*" for everyone


def migrate(old, new):
    """State files used to have Norwegian names; move them once."""
    old, new = os.path.join(MC_DIR, old), os.path.join(MC_DIR, new)
    if os.path.exists(old) and not os.path.exists(new):
        os.rename(old, new)


migrate(".chatbro-tilgang.json", ".chatbro-access.json")
migrate(".codex-angre.json", ".codex-undo.json")


def load_guests():
    try:
        return set(json.load(open(GUESTS_FILE)))
    except (OSError, ValueError):
        return set()


guests = load_guests()


def save_guests():
    json.dump(sorted(guests), open(GUESTS_FILE, "w"))


def may_talk(player):
    return player in ALLOWED or "*" in guests or player.lower() in guests


def load_env(path):
    """Reads KEY=VALUE from a simple env file (comments and blank lines are skipped)."""
    env = {}
    try:
        with open(path) as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    k, v = line.split("=", 1)
                    env[k.strip()] = v.strip().strip('"').strip("'")
    except FileNotFoundError:
        pass
    return env


# Model provider, set in .chatbro.env (chmod 600; a separate file so keys never reach the mc container):
#   CHATBRO_PROVIDER  one of PROVIDERS below, "codex" (Codex CLI with a ChatGPT plan) or "claude" (Claude Code CLI)
#   CHATBRO_API_KEY   the key for API providers (not needed for codex, claude, ollama, lmstudio)
#   CHATBRO_MODEL     the model name at that provider (defaults below where there is a sensible one)
#   CHATBRO_BASE_URL  any other OpenAI-compatible endpoint (use CHATBRO_PROVIDER=openai-compatible)
# Every provider except "claude" plans the commands as JSON and chatbro filters and runs them (same as codex),
# so building, undo and super tools work with all of them.
CONF = {**load_env(os.path.join(MC_DIR, ".chatbro.env")), **{k: v for k, v in os.environ.items() if k.startswith("CHATBRO_")}}
# The server owner: CHATBRO_MASTER, otherwise the first name in OPS in .env. Only the owner can grant and revoke
# access with /master and /masteroff; everyone else needs access first.
MASTER = CONF.get("CHATBRO_MASTER") or (load_env(os.path.join(MC_DIR, ".env")).get("OPS", "").split(",")[0].strip())
ALLOWED = {MASTER} if MASTER else set()        # who may talk to the bot without being granted access
# What the bot is called in the game, and the word(s) that start a request in the chat ("ai build a bridge").
NAME = CONF.get("CHATBRO_NAME") or "AI"
KEYWORDS = [k.strip().lower() for k in (CONF.get("CHATBRO_KEYWORDS") or "ai").split(",") if k.strip()]
KW = KEYWORDS[0]                                # the one shown in help texts
# provider: (kind, base URL, default model). kind "openai" = OpenAI-compatible /chat/completions.
PROVIDERS = {
    "openai":            ("openai", "https://api.openai.com/v1", ""),
    "openrouter":        ("openai", "https://openrouter.ai/api/v1", ""),
    "gemini":            ("openai", "https://generativelanguage.googleapis.com/v1beta/openai", ""),
    "mistral":           ("openai", "https://api.mistral.ai/v1", ""),
    "groq":              ("openai", "https://api.groq.com/openai/v1", ""),
    "deepseek":          ("openai", "https://api.deepseek.com/v1", "deepseek-chat"),
    "glm":               ("openai", "https://api.z.ai/api/paas/v4", "glm-4.7-flash"),
    "ollama":            ("openai", "http://localhost:11434/v1", "qwen3:14b"),
    "lmstudio":          ("openai", "http://localhost:1234/v1", ""),
    "openai-compatible": ("openai", "", ""),
    "anthropic":         ("anthropic", "https://api.anthropic.com/v1", "claude-sonnet-5-5"),
}
API_KEY = CONF.get("CHATBRO_API_KEY") or CONF.get("ZAI_API_KEY", "")
PROVIDER = (CONF.get("CHATBRO_PROVIDER") or ("glm" if API_KEY else "codex")).lower()
KIND, _base, _model = PROVIDERS.get(PROVIDER, ("cli", "", ""))
MODEL = CONF.get("CHATBRO_MODEL") or _model or ("opus" if PROVIDER == "claude" else "")
BASE_URL = (CONF.get("CHATBRO_BASE_URL") or _base).rstrip("/")
LOCAL = PROVIDER in ("ollama", "lmstudio") or BASE_URL.startswith(("http://localhost", "http://127.0.0.1"))
JSON_MODE = PROVIDER != "claude"               # plan as JSON, chatbro runs the commands (all but Claude Code)


def provider_problem():
    """Why the configured provider cannot work, or "" if it looks fine."""
    if PROVIDER not in PROVIDERS and PROVIDER not in ("codex", "claude"):
        return f"unknown CHATBRO_PROVIDER {PROVIDER!r}, use one of: codex, claude, {', '.join(PROVIDERS)}"
    if KIND in ("openai", "anthropic"):
        if not BASE_URL:
            return "CHATBRO_BASE_URL is missing"
        if not MODEL:
            return f"CHATBRO_MODEL is missing (the model name at {PROVIDER})"
        if not API_KEY and not LOCAL:
            return "CHATBRO_API_KEY is missing"
    return ""


def claude_env():
    """Environment for claude -p (Claude Code with its own login)."""
    return dict(os.environ)

TIMEOUT = 900                                 # seconds per request
RADIUS = 48                                   # blocks around the anchor that stay force-loaded during the job
PREFIX = f"[{NAME}] "

CHAT_RE = re.compile(r"INFO\]: <(\w+)> (.*?)\s*$")
CMD_RE = re.compile(r"INFO\]: (\w+) issued server command: /(?:" + "|".join(map(re.escape, KEYWORDS)) + r")\b[\s,:!]*(.*?)\s*$", re.IGNORECASE)
# A new world was created (e.g. /new): if "super" is in its name it gets the Supermann powers -> update the list + /reload
WORLD_RE = re.compile(r"Selecting spawn point for level 'minecraft:([a-z0-9_.-]+)'")
SUPERWORLDS = os.path.join(MC_DIR, "data", "world", "datapacks", "supermann", "data", "supermann", "function", "superworlds.mcfunction")
JOIN_RE = re.compile(r"INFO\]: (\w+) joined the game")
MASTER_RE = re.compile(r"INFO\]: (\w+) issued server command: /(master|masteroff)(?:\s+(\S+))?\s*$")  # bot access
CREATOR_RE = re.compile(r"INFO\]: (\w+) issued server command: /(creatorpower|creatorpoweroff)\s*$")  # /craft on/off
BUILD_RE = re.compile(r"INFO\]: (\w+) issued server command: /(build_\w+)\s*$")   # catalog codes from findbuild.py
KEYWORD_RE = re.compile(r"^\s*(?:" + "|".join(map(re.escape, KEYWORDS)) + r")\b[\s,:!]*(.*)$", re.IGNORECASE)

SYSTEM_PROMPT = """You are "{NAME}" on a family Minecraft server (Paper 26.3 in Docker, the folder you are in). A player has written a wish in the chat, and you carry it out in the game through the server console. Answers and messages are in English.

TOOLS
- `./mc.sh cmd <command>` sends one command over RCON and returns the reply. The console has no position: use `execute as <player> at @s run …` for anything that should happen where the player is. Several commands = several calls.
- Position and direction: `./mc.sh cmd data get entity <player> Pos` / `Rotation` / `Dimension`. Yaw: 0 = south (+z), 90 = west (-x), ±180 = north (-z), -90 = east (+x). Local ^ coordinates are ^left ^up ^forward, so ^-1 = one to the right. Integers in `positioned` are centred (+0.5 in x/z).
- `./mc.sh lp <args>` for LuckPerms (it does not answer over RCON; mc.sh handles that).
- Ready-made build functions in the supermann datapack, run with `execute as <player> at @s run function …`: supermann:tunnel {n:N}, supermann:glassbridge {n:N}, supermann:staircase {n:N}, supermann:staircase_up {n:N}, supermann:house (furnished cabin), supermann:hideandseek / supermann:hideandseek_off. The source is in data/world/datapacks/supermann/data/supermann/function/ and shows how such builds are made.
- setblock/fill only work in loaded chunks (about 160 blocks from the player). Use `forceload add` for big jobs and `forceload remove` afterwards.

RULES
- ANCHOR: Everything is built from the block the player stood on when the command was sent (given in the message), in the direction the player was looking. Use `execute in <world> positioned <x> <y> <z> rotated <yaw> 0 run …` with those numbers, not the player's current position (they may have moved). Don't survey the surroundings on a large scale; check at most a few blocks around the anchor when needed.
- FRONT: If the build has a front (door, entrance, facade), it should stand in front of the player with the front facing the player, so the player looks straight at it from the anchor block (as supermann:house does). Things without a front (bridge, road, tunnel, fence) run from the anchor forward in the viewing direction.
- Do what the player asks, accurately and with care for looks. Feel free to check the result with `execute if block`.
- Never: change files, /reload, stop, restart, op, deop, whitelist, ban, kick, gamemode for others, permissions through lp, or builds and changes beyond what the wish is about.
- If the wish is only a question, answer without doing anything.
- PROGRESS: The player sees a boss bar at the top of the screen. Make a short plan first and update the bar as you pass milestones: `./mc.sh cmd bossbar set supermann:chatbot value <0-100>` (percent done). Don't send status messages in the chat beyond that, only the final answer.
- Your final answer is sent to the player in the chat automatically. Write one or two short sentences in English, without markdown, code blocks or bullet lists."""


def log(msg):
    line = f"{dt.datetime.now():%Y-%m-%d %H:%M:%S} {msg}"
    print(line, flush=True)
    with open(LOG_FILE, "a") as f:
        f.write(line + "\n")


def rcon(cmd):
    try:
        r = subprocess.run(["./mc.sh", "cmd", cmd], cwd=MC_DIR, capture_output=True, text=True, timeout=30)
        return (r.stdout + r.stderr).strip()
    except Exception as e:  # noqa: BLE001
        return f"rcon error: {e}"


pending = {}                                  # answers waiting for the player to log in again
BAR = "supermann:chatbot"                     # boss bar at the top of the screen, used as a progress bar
TRANSCRIPTS = os.path.expanduser("~/.claude/projects/" + MC_DIR.replace("/", "-") + "/")


def bar_start(player):
    rcon(f'bossbar add {BAR} {{"text":"{NAME} is working…"}}')
    for c in (f'name {{"text":"{NAME}: starting…"}}', "color yellow", "max 100", "value 0", f"players {player}", "visible true"):
        rcon(f"bossbar set {BAR} {c}")


def bar_end(ok):
    rcon(f'bossbar set {BAR} value 100')
    rcon(f'bossbar set {BAR} color {"green" if ok else "red"}')
    rcon(f'bossbar set {BAR} name {{"text":"{NAME}: {"done" if ok else "stopped"}"}}')
    time.sleep(6)
    rcon(f"bossbar remove {BAR}")


def tool_calls(sid, start_lines):
    """How many tool calls the model has made in this job (counts lines in the conversation transcript)."""
    try:
        with open(os.path.join(TRANSCRIPTS, sid + ".jsonl")) as f:
            lines = f.read().splitlines()
        return sum('"type":"tool_use"' in l for l in lines[start_lines:])
    except OSError:
        return 0


def bar_ticker(stop, t0, start_lines):
    while not stop.wait(10):
        sid, _ = session_id()
        n = tool_calls(sid, start_lines)
        m, sec = divmod(int(time.time() - t0), 60)
        extra = "" if JSON_MODE else f" · {n} commands"
        rcon(f'bossbar set {BAR} name {{"text":"{NAME} is working… {m}:{sec:02d}{extra}"}}')


def tell(player, text, color="gold"):
    text = re.sub(r"\s+", " ", text).strip()
    if len(text) > 600:
        text = text[:597] + "…"
    out = rcon("tellraw " + player + " " + json.dumps({"text": PREFIX + text, "color": color}, ensure_ascii=False))
    if "No player was found" in out and color != "gray":
        pending.setdefault(player, []).append(text)
        log(f"{player} is not online, the answer is waiting")


def flush_pending(player):
    msgs = pending.pop(player, [])
    if msgs:
        time.sleep(3)                         # let the player get fully in first
        for t in msgs:
            tell(player, "While you were away: " + t)


def session_id(new=False):
    if not new and os.path.exists(SESSION_FILE):
        with open(SESSION_FILE) as f:
            sid = f.read().strip()
        if sid:
            return sid, True
    sid = str(uuid.uuid4())
    with open(SESSION_FILE, "w") as f:
        f.write(sid)
    return sid, False


def snapshot(player):
    """Where the player stood and looked when the command came in. Used as the anchor for everything built."""
    def get(path):
        out = rcon(f"data get entity {player} {path}")
        return out.split("entity data:", 1)[1].strip() if "entity data:" in out else None
    pos, rot, dim = get("Pos"), get("Rotation"), get("Dimension")
    if not pos:
        return f"{player} is not online, so I don't know where they are.", None
    nums = [float(v) for v in re.findall(r"-?\d+(?:\.\d+)?", pos)]
    x, y, z = (int(v // 1) for v in nums[:3])
    yaw = float(re.findall(r"-?\d+(?:\.\d+)?", rot or "0")[0])
    yaw = ((yaw + 180) % 360) - 180
    facing = ("south (+z)" if -45 <= yaw < 45 else "west (-x)" if 45 <= yaw < 135
              else "east (+x)" if -135 <= yaw < -45 else "north (-z)")
    dim = (dim or "minecraft:overworld").strip(chr(34))
    return (f"When the command was sent, {player} stood in block x={x} y={y} z={z} (the block under their feet: y={y - 1}), "
            f"looking {facing} (yaw {yaw:.0f}), in world {dim}. "
            f"Build with exactly this block as the starting point. The area x={x - RADIUS}..{x + RADIUS}, z={z - RADIUS}..{z + RADIUS} "
            f"in {dim} is force-loaded while you work, so it is safe to build there even if the player logs out."), (dim, x, z)


def forceload(anchor, on):
    if not anchor:
        return
    dim, x, z = anchor
    verb = "add" if on else "remove"
    rcon(f"execute in {dim} run forceload {verb} {x - RADIUS} {z - RADIUS} {x + RADIUS} {z + RADIUS}")


# --- Codex (ChatGPT Plus): plans the commands, chatbro runs them after filtering --------------------
CODEX = shutil.which("codex") or "/opt/homebrew/bin/codex"
CODEX_FUNC = os.path.join(MC_DIR, "data", "world", "datapacks", "supermann", "data", "supermann", "function", "codex")
MAX_COMMANDS = 5000
history = []                                   # recent wishes and answers, so "make it bigger" makes sense


def _post_json(url, headers, body):
    import urllib.request, urllib.error
    req = urllib.request.Request(url, data=json.dumps(body).encode(), headers={"Content-Type": "application/json", **headers})
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT) as r:
            return json.load(r)
    except urllib.error.HTTPError as e:
        raise RuntimeError(f"HTTP {e.code}: {e.read().decode(errors='replace')[:300]}") from None


def _api_call(prompt, schema, name):
    """One request to an API provider; returns the model's text (should be JSON)."""
    system = ("Answer with one JSON object only, no markdown, matching this JSON schema:\n" + json.dumps(schema))
    if KIND == "anthropic":
        r = _post_json(BASE_URL + "/messages", {"x-api-key": API_KEY, "anthropic-version": "2023-06-01"},
                       {"model": MODEL, "max_tokens": 32000, "system": system,
                        "messages": [{"role": "user", "content": prompt}]})
        return "".join(b.get("text", "") for b in r.get("content", []))
    headers = {"Authorization": f"Bearer {API_KEY or 'local'}"}
    body = {"model": MODEL, "messages": [{"role": "system", "content": system}, {"role": "user", "content": prompt}]}
    try:                                       # strict structured output where the provider has it …
        r = _post_json(BASE_URL + "/chat/completions", headers,
                       {**body, "response_format": {"type": "json_schema", "json_schema": {"name": name, "schema": schema}}})
    except RuntimeError as e:                  # … otherwise plain JSON mode, then no format at all
        if not str(e).startswith("HTTP 4"):
            raise
        try:
            r = _post_json(BASE_URL + "/chat/completions", headers, {**body, "response_format": {"type": "json_object"}})
        except RuntimeError as e2:
            if not str(e2).startswith("HTTP 4"):
                raise
            r = _post_json(BASE_URL + "/chat/completions", headers, body)
    return r["choices"][0]["message"]["content"] or ""


def _parse_json(text):
    text = text.strip()
    if text.startswith("```"):
        text = re.sub(r"^```(?:json)?\s*|\s*```$", "", text)
    try:
        return json.loads(text)
    except ValueError:
        m = re.search(r"\{.*\}", text, re.S)  # models that add a sentence around the JSON
        return json.loads(m.group(0)) if m else None


def ask_json(prompt, schema, name, effort="medium", solo=True):
    """Asks the configured model for a JSON object following schema. Returns (dict, "") or (None, reason for the
    player). Codex runs as a CLI in a read-only sandbox; API providers are plain HTTPS calls. "ai stop" cancels."""
    problem = provider_problem()
    if problem:
        log(f"chatbro is not set up: {problem}")
        return None, "The AI model is not set up on the server yet (see chatbro.log)."
    if solo:
        current["cancelled"] = False
    if PROVIDER == "codex":
        tag = name if solo else f"{name}-{uuid.uuid4().hex[:8]}"          # several helpers think at once: each its own files
        schema_file = os.path.join(MC_DIR, "data", f"codex-{tag}-schema.json")
        out_file = os.path.join(MC_DIR, f".codex-{tag}.json")
        json.dump(schema, open(schema_file, "w"))
        if os.path.exists(out_file):
            os.remove(out_file)
        args = [CODEX, "exec", "--sandbox", "read-only", "--skip-git-repo-check", "--ephemeral", "-C", MC_DIR,
                "--output-schema", schema_file, "-o", out_file, "-c", f"model_reasoning_effort={effort}"]
        if MODEL:
            args += ["-m", MODEL]
        proc = subprocess.Popen(args + [prompt], cwd=MC_DIR, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                stdin=subprocess.DEVNULL, text=True, start_new_session=True)
        if solo:
            current["proc"] = proc
        try:
            out, err = proc.communicate(timeout=TIMEOUT)
        except subprocess.TimeoutExpired:
            os.killpg(proc.pid, signal.SIGKILL)
            proc.communicate()
            return None, "That took too long, I gave up. Try a smaller wish."
        finally:
            if solo:
                current["proc"] = None
        if solo and current["cancelled"]:
            return None, "Cancelled."
        try:
            return json.load(open(out_file)), ""
        except (OSError, ValueError):
            log(f"codex failed (code {proc.returncode}): {(err or out).strip()[-300:]}")
            return None, "Something went wrong on my side, see chatbro.log on the server."
        finally:
            if not solo:
                for f in (schema_file, out_file):
                    try:
                        os.remove(f)
                    except OSError:
                        pass
    result = {}
    def call():
        try:
            result["text"] = _api_call(prompt, schema, name)
        except Exception as e:  # noqa: BLE001
            result["error"] = str(e)
    t = threading.Thread(target=call, daemon=True)
    t.start()
    t0 = time.time()
    while t.is_alive():
        t.join(0.5)
        if current["cancelled"]:
            return None, "Cancelled."
        if time.time() - t0 > TIMEOUT:
            return None, "That took too long, I gave up. Try a smaller wish."
    if "error" in result:
        log(f"{PROVIDER} ({MODEL}) failed: {result['error']}")
        return None, "Something went wrong on my side, see chatbro.log on the server."
    d = _parse_json(result.get("text", ""))
    if not isinstance(d, dict):
        log(f"{PROVIDER} ({MODEL}) did not answer with JSON: {result.get('text', '')[:300]}")
        return None, "The model did not answer in the right format, try again."
    return d, ""

CODEX_PROMPT = """You are "{NAME}", the building helper on a family Minecraft server (Paper 26.3, Java commands). A player has written a wish in the chat. You cannot run anything yourself: answer with JSON where "commands" are the Minecraft commands to run (without /, in order) and "answer" is one or two short sentences in English to the player, without markdown.

RULES
- ANCHOR: Build from the block the player stood on (given below), in the direction the player was looking. Use absolute coordinates, or `execute in <world> positioned <x> <y> <z> rotated <yaw> 0 run …` with local ^ coordinates (^left ^up ^forward, ^-1 = one to the right). Yaw 0 = south (+z), 90 = west (-x), ±180 = north (-z), -90 = east (+x).
- FLOORS AND WALLS: The ground is y-1, and the floor goes there. Walls, doors and furniture start at y. NEVER use `fill … hollow` or `outline` for walls: that also makes a bottom and a top of the wall material, and then a layer of wall covers the whole floor. Make walls with one solid `fill` of the whole box, then fill the inside with air (from y, one block in from each wall). Or use four narrow `fill`s, one per wall. Lay the floor afterwards if anything may have covered it. Once the walls are made, the inside at y must be air (or carpet and furniture you placed on purpose).
- Use `fill` for surfaces and walls instead of many `setblock`s. At most 32768 blocks per fill. Clear air where the build goes if the terrain is in the way.
- FRONT: Builds with a front stand in front of the player with the front facing the player. Things without a front (bridge, road, tunnel) run from the anchor forward.
- Do what the player asks, accurately and with care for looks (varied materials, roofs, windows, light).
- Ready-made functions that can be used with `execute as <player> at @s run function …`: supermann:tunnel {n:N}, supermann:glassbridge {n:N}, supermann:staircase {n:N}, supermann:staircase_up {n:N}, supermann:house. The source is in data/world/datapacks/supermann/data/supermann/function/ if you want to see how builds are made; don't read other files.
- Never: stop, op, deop, whitelist, ban, kick, reload, lp, gamemode for others, kill without type=, or changes beyond the wish. If the wish is only a question, give an empty commands list and answer in answer.
- COMMAND BLOCKS ARE OFF on the server, and you cannot create datapack files. Don't build automation that needs command blocks, command-based redstone clocks or /trigger logic. If the player wants something that runs by itself (sorting, storage, farms with logic), build it with plain redstone, hoppers and observers that work in survival, or say in answer that the server owner has to add it as a datapack.
- FOLLOW-UP: The commands from the previous job are in data/world/datapacks/supermann/data/supermann/function/codex/job.mcfunction. Read it when the player wants to fix, change or extend what you made last.
- UNDO: If the player asks to undo, remove or reset what you built last, set undo to true and commands to []. Then the area is put back the way it was before the previous job. Otherwise undo is false.
"""

DENY_RE = re.compile(r"(^|\brun\s+)(stop|op|deop|whitelist|ban|ban-ip|pardon|pardon-ip|kick|reload|restart|save-off|lp|luckperms|perm|transfer|debug|jfr|perf)\b", re.I)


def allowed_cmd(c):
    if DENY_RE.search(c):
        return False
    if re.search(r"(^|\brun\s+)kill\b", c) and "type=" not in c:
        return False
    return True


def run_codex(player, request):
    """The model (Codex or an API) writes the command list as JSON; we filter and run it."""
    info, anchor = snapshot(player)
    if not anchor:
        return info
    schema = {"type": "object", "additionalProperties": False, "required": ["commands", "answer", "undo"],
              "properties": {"commands": {"type": "array", "items": {"type": "string"}}, "answer": {"type": "string"},
                             "undo": {"type": "boolean"}}}
    hist = "".join(f"\nEarlier: \"{q}\" -> {a}" for q, a in history[-3:])
    prompt = f"{CODEX_PROMPT.replace('{NAME}', NAME)}\nThe player {player} wrote: \"{request}\"\n{info}{hist}"
    forceload(anchor, True)
    current["anchor"] = anchor
    try:
        plan, problem = ask_json(prompt, schema, "answer")
        if plan is None:
            return problem
        if plan.get("undo"):
            return undo_last()
        cmds = [c.strip().lstrip("/") for c in plan.get("commands", []) if c.strip()][:MAX_COMMANDS]
        ok = [c for c in cmds if allowed_cmd(c)]
        refused = len(cmds) - len(ok)
        if refused:
            log(f"refused {refused} commands: {[c for c in cmds if not allowed_cmd(c)][:5]}")
        backup(anchor[0], ok)
        failed = run_commands(player, ok)
        answer = plan.get("answer", "").strip() or "Done."
        if failed:
            answer += f" ({failed} of {len(ok)} commands failed.)"
        history.append((request, answer))
        log(f"{PROVIDER}: {len(ok)} commands, {failed} failed, {refused} refused")
        return answer
    finally:
        forceload(anchor, False)
        current["anchor"] = None


# --- Undo for Codex jobs: copy of the area (from the coordinates in fill/setblock) before the job runs ---
UNDO_X, UNDO_Z = 2100000, 2100000              # storage in the overworld (blueprint undo uses 2000000)

# --- Super tools: Codex describes a new tool as data, the Creator plugin builds it ------------------
SUPERS_DIR = os.path.join(MC_DIR, "data", "plugins", "Creator", "supers")
TRIGGERS = ["mine", "hit", "use", "hold", "impact"]
ABILITIES = ["area", "tree", "vein", "smelt", "harvest", "till", "damage", "sweep", "effect", "self_effect", "burn", "freeze",
             "lightning", "explosion", "push", "pull", "blink", "jump", "projectile", "heal", "freeze_water", "particles", "sound"]
TOOLS = ["sword", "axe", "pickaxe", "shovel", "hoe"]
MATERIALS = ["wood", "stone", "copper", "iron", "gold", "diamond", "netherite"]
SUPER_NEW_RE = re.compile(r"^(?:make|create|new)\s+(?:me\s+)?(?:a\s+|an\s+)?(?:new\s+)?super\s?tool\b[\s,:!-]*(.*)$", re.I | re.S)
SUPER_EDIT_RE = re.compile(r"^(?:change|edit|improve|update|fix)\s+(?:the\s+)?(?:super\s?tool\s+)?(\w+)\b[\s,:!-]*(.*)$", re.I | re.S)
SUPER_DRAW_RE = re.compile(r"^redraw\s+(?:the\s+)?(\w+)\b[\s,:!-]*(.*)$", re.I | re.S)

SUPER_PROMPT = """You are making a SUPER TOOL for a family Minecraft server (Paper 26.3) from a wish in the chat. You cannot run anything and must not read files (except the examples in data/plugins/Creator/supers/). Answer only with JSON following the schema. The tool is crafted in the Big Bench (a 5x5 crafting grid) and is built from fixed building blocks, not code.

FIELDS
- id: lower case a-z, 0-9 and _, 2–32 characters, based on the name. name: short English name, capitalised. color: "#RRGGBB" (the name colour in chat).
- tool: sword | axe | pickaxe | shovel | hoe. Decides what it mines fast (pickaxe = stone and ore, shovel = dirt and sand, axe = wood) and the basic shape.
- materials: list of wood, stone, copper, iron, gold, diamond, netherite. If the tool is a bigger or longer version of a normal tool (long sword, big pickaxe, wide shovel …), it should exist in every material: then there is one version per material, with the same damage and durability as the normal tool of that material, twice as durable, and it looks like the normal tool only bigger (leave frames in texture as an empty list). If it is a magic or special tool (frost axe, lightning sword …), use an empty list: then it becomes one netherite tool that never breaks, with your own drawing.
- size: 1.0–2.0, how big it looks in the hand (1.0 normal, 1.8 very big).
- description: 1–4 short English lines explaining what it does (shown on the tool).
- recipe: exactly 5 strings of 5 characters (space = empty slot), shaped like the tool. ingredients: each character → a Minecraft material with its English name in capitals (DIAMOND, BLAZE_ROD, GOLD_BLOCK, ENDER_PEARL, NETHERITE_INGOT, PACKED_ICE …). With materials you can also use "@tool" (the normal tool of the same kind and material) and "@material" (planks, cobblestone, copper, iron or gold ingot, diamond, netherite ingot).
- PRICE: A long or big tool costs TWO normal tools of the same material and nothing more (long diamond sword = two diamond swords: a recipe with two "@tool"). If it also has an extra ability, you may add a few fitting ingredients for it. Magic tools without materials cost according to how strong they are: weak things 5–10 common materials, strong things diamonds, netherite or rare things, preferably with a normal diamond tool of the same kind (its enchantments carry over).
- stats: damage 1–20 and speed 0.5–4 hits per second apply to tools without materials (normal netherite: sword 8 and 1.6, axe 10 and 1.0, pickaxe 6 and 1.2, shovel 6.5 and 1.0, hoe 1 and 4). With materials, bonus_damage (-5–10) and bonus_speed (-2–2) are added to the normal tool of each material (set damage and speed to 0). reach 0–4 extra blocks for hitting, block_reach 0–3 extra blocks for mining and building.
- abilities: 0–6 building blocks. Each has trigger, type, strength, seconds, name (empty string if unused) and cooldown (seconds between uses, 0 = none).
- texture: a 16x16 pixel image of the tool (see DRAWING).
- answer: one or two short English sentences to the player about what you made, without markdown.

TRIGGERS: mine (when a block is mined), hit (when you hit a creature; target = what you hit), use (right click; target = the creature you aim at within 48 blocks, otherwise the point you aim at), hold (twice a second while the tool is in your hand), impact (where a projectile from the projectile ability lands).

TYPES (what strength means):
- area (mine): mines an area, strength 1 = 3x3, 2 = 5x5, 3 = 7x7. tree (mine): the whole tree, and plants a new one. vein (mine): the whole ore vein. smelt (mine): what you mine comes out smelted (iron → iron ingot).
- harvest (use): harvests ripe crops in radius strength (1–4) and replants. till (use): tills soil in radius strength (1–3).
- damage (hit/impact): extra damage strength (0–20). sweep (hit/use): hurts monsters in radius strength (1–5) around the target.
- effect (hit/impact/use): potion effect on the target, name = effect (slowness, poison, wither, glowing, levitation, weakness, blindness, darkness, nausea …), strength = level 1–5, seconds 1–60. Without a target: monsters within 4 blocks.
- self_effect (hold/use/hit/mine): effect on yourself, name = effect (speed, haste, night_vision, jump_boost, resistance, regeneration, fire_resistance, water_breathing, slow_falling, strength, invisibility …), strength 1–5, seconds (with hold it lasts as long as you hold the tool).
- burn (hit/impact): sets the target on fire for seconds. freeze (hit/impact/use): freezes and slows the target for seconds. lightning (hit/impact/use): lightning strikes the target, strength = damage (6 if 1), no fire.
- explosion (hit/impact/use): a blast that hurts and throws creatures, strength 1–4 = size, never breaks blocks.
- push (hit/use): throws the target away, strength 0.5–4 (without a target: all monsters within 5 blocks). pull (use: monsters and items towards you; hold: items on the ground towards you), strength = radius 2–16.
- blink (use): teleports you to where you look, strength = max distance 4–48. jump (use): launches you upwards, strength 0.5–3, no fall damage afterwards.
- projectile (use): shoots name = snowball | arrow | spectral_arrow | wind_charge | small_fireball | egg, strength = speed 0.5–4. Combine with impact abilities (lightning, explosion, freeze …) for what happens where it lands.
- heal (hold/use/hit): gives strength hearts (0.5–10); with hold at most every 5 seconds.
- freeze_water (hold): water under you turns to ice in radius strength (1–4), so you can walk on water.
- particles (all): name = a particle without extra data (flame, soul_fire_flame, snowflake, heart, end_rod, enchant, portal, cloud, crit, witch, electric_spark, cherry_leaves, totem_of_undying, sonic_boom …), strength = count 1–100. sound (all): name = sound id (entity.lightning_bolt.thunder, block.amethyst_block.chime, entity.blaze.shoot …), strength = pitch 0.5–2.
Use cooldown on strong use abilities (blink 3, lightning 2, explosion 3, jump 2), otherwise they can be spammed. Don't make anything that makes the tool unreasonably strong for a family-friendly survival game.

DRAWING (texture)
- If the tool has materials and should look like the normal tool, give an empty palette and an empty frames list.
- palette: 2–24 colours, each with one character (not a dot) and "#RRGGBB". frames: 1–8 images (more = animation, speed = ticks per image 1–20), each exactly 16 strings of 16 characters, where "." is transparent.
- Draw the tool diagonally the way Minecraft does: the handle at the bottom left (around x 1–6, y 10–15) and the blade or head at the top right. Row 0 is the top. Use a dark outline colour around the shape, 2–3 shades per material (light side top left), and fill at least 40 pixels. Make it recognisable and pretty in Minecraft style. Animation suits glow, flames or sparks: the same shape in every frame, only a few pixels change.
"""


def super_schema():
    num, s = {"type": "number"}, {"type": "string"}
    def obj(props):
        return {"type": "object", "additionalProperties": False, "required": list(props), "properties": props}
    return obj({
        "id": s, "name": s, "color": s, "tool": {"type": "string", "enum": TOOLS},
        "materials": {"type": "array", "items": {"type": "string", "enum": MATERIALS}},
        "size": num, "description": {"type": "array", "items": s}, "recipe": {"type": "array", "items": s},
        "ingredients": {"type": "array", "items": obj({"char": s, "material": s})},
        "stats": obj({"damage": num, "speed": num, "bonus_damage": num, "bonus_speed": num, "reach": num, "block_reach": num}),
        "abilities": {"type": "array", "items": obj({"trigger": {"type": "string", "enum": TRIGGERS}, "type": {"type": "string", "enum": ABILITIES},
                                                     "strength": num, "seconds": num, "name": s, "cooldown": num})},
        "texture": obj({"palette": {"type": "array", "items": obj({"char": s, "color": s})},
                        "frames": {"type": "array", "items": {"type": "array", "items": s}}, "speed": {"type": "integer"}}),
        "answer": s,
    })


def super_match(request):
    """(id or None, wish, redraw_only) if the message is about super tools."""
    r = request.strip()
    m = SUPER_NEW_RE.match(r)
    if m:
        return None, m.group(1).strip() or r, False
    for rx, draw in ((SUPER_DRAW_RE, True), (SUPER_EDIT_RE, False)):
        m = rx.match(r)
        if m:
            sid = slug(m.group(1))
            if os.path.exists(os.path.join(SUPERS_DIR, sid + ".json")):
                return sid, m.group(2).strip(), draw
    return None


def slug(name):
    t = name.lower().replace("æ", "ae").replace("ø", "o").replace("å", "a")   # Scandinavian letters in player names
    return re.sub(r"[^a-z0-9_]+", "_", t).strip("_")[:32]


def ops():
    try:
        return {o["name"] for o in json.load(open(os.path.join(MC_DIR, "data", "ops.json")))}
    except (OSError, ValueError, KeyError):
        return {MASTER}


def run_super(player, sid, wish, draw_only):
    """The model describes the tool (JSON); we check the drawing, write supers/<id>.json, build the pack and reload."""
    import super_pack
    old = None
    if sid:
        old = json.load(open(os.path.join(SUPERS_DIR, sid + ".json")))
    if old and draw_only:
        task = f"Redraw the texture of this tool, prettier and clearer{': ' + wish if wish else ''}. Keep everything else the same (same id).\n{json.dumps(old, ensure_ascii=False)}"
    elif old:
        task = f"Change this tool the way the player wishes: \"{wish}\". Keep id \"{sid}\" and whatever should not change.\n{json.dumps(old, ensure_ascii=False)}"
    else:
        os.makedirs(SUPERS_DIR, exist_ok=True)
        taken = sorted(f[:-5] for f in os.listdir(SUPERS_DIR) if f.endswith(".json"))
        task = f"The player {player} wishes for: \"{wish}\". These ids are taken: {', '.join(taken)}."
    if PROVIDER != "codex":                    # API models cannot read the example files, so show one
        try:
            task += "\nA valid example tool:\n" + open(os.path.join(SUPERS_DIR, "longsword.json")).read()
        except OSError:
            pass
    d, problem = ask_json(SUPER_PROMPT + "\n" + task, super_schema(), "super")
    if d is None:
        return problem
    answer = (d.pop("answer", "") or "").strip()
    new_id = sid or slug(d.get("id") or d.get("name") or "tool") or "tool"
    if not sid:
        base, n = new_id, 2
        while os.path.exists(os.path.join(SUPERS_DIR, new_id + ".json")):
            new_id, n = f"{base}_{n}", n + 1
    d["id"] = new_id
    note = ""
    plain = bool(d.get("materials")) and not (d.get("texture") or {}).get("frames")
    if plain:                                            # long/big normal tool: normal look, only bigger
        d["texture"] = None
    else:
        try:
            super_pack.check_texture(d.get("texture"))
        except ValueError as e:
            log(f"super tool {new_id}: the drawing was rejected ({e}), recolouring instead")
            d["texture"] = old.get("texture") if old and not draw_only else None
            note = " The drawing wasn't good enough, so it got a colour instead (write \"" + KW + " redraw " + new_id + "\" to try again)."
    is_op = player in ops()
    d["approved"] = bool(old.get("approved")) if old and not is_op else is_op
    d["made_by"] = old.get("made_by", player) if old else player
    path = os.path.join(SUPERS_DIR, new_id + ".json")
    backup = open(path).read() if os.path.exists(path) else None
    json.dump(d, open(path, "w"), ensure_ascii=False, indent=1)
    res = rcon("superreload")
    if f"{new_id}.json" in res:                         # the plugin rejected the file
        if backup is None:
            os.remove(path)
        else:
            open(path, "w").write(backup)
        rcon("superreload")
        log(f"super tool {new_id} rejected by the plugin: {res[-300:]}")
        return "The tool wasn't valid (see the server log). Try describing it a little differently."
    for script in ("super_pack.py", "heads.py"):
        r = subprocess.run([sys.executable, script], cwd=MC_DIR, capture_output=True, text=True, timeout=120)
        if r.returncode:
            log(f"{script} failed: {(r.stderr or r.stdout)[-300:]}")
    rcon("superreload")
    log(f"super tool {new_id} {'changed' if old else 'made'} by {player} (approved: {d['approved']}): {json.dumps(d.get('abilities'), ensure_ascii=False)[:300]}")
    if not d["approved"]:
        tell(MASTER, f"{player} made the super tool \"{d.get('name')}\". See /superlist, approve with /superapprove {new_id} or remove with /superdelete {new_id}.")
        return (answer or f"{d.get('name')} is drawn.") + note + f" It is waiting for {MASTER} to approve it, then it can be made in the Big Bench (/bb)."
    return (answer or f"{d.get('name')} is ready.") + note + " It is in the Big Bench (/bb)."


UNDO_FILE = os.path.join(MC_DIR, ".codex-undo.json")
COORD_RE = re.compile(r"\b(?:fill|setblock|clone)\s+((?:-?\d+\s+){2,5}-?\d+)")


def job_box(cmds):
    xs, ys, zs = [], [], []
    for c in cmds:
        for m in COORD_RE.finditer(c):
            n = [int(v) for v in m.group(1).split()]
            for i in range(0, len(n) - len(n) % 3, 3):
                xs.append(n[i]); ys.append(n[i + 1]); zs.append(n[i + 2])
    if not xs:
        return None
    return min(xs) - 1, max(min(ys) - 1, -64), min(zs) - 1, max(xs) + 1, min(max(ys) + 1, 319), max(zs) + 1


def chunks(dim, x1, z1, x2, z2, on):
    rcon(f"execute in {dim} run forceload {'add' if on else 'remove'} {x1} {z1} {x2} {z2}")


def clone_box(src_dim, box, dst_dim, dx, dz):
    """Clones the box layer by layer (clone handles at most 32768 blocks) to (dx, y, dz) in dst_dim."""
    x1, y1, z1, x2, y2, z2 = box
    per = max(1, 32768 // ((x2 - x1 + 1) * (z2 - z1 + 1)))
    for y in range(y1, y2 + 1, per):
        ye = min(y + per - 1, y2)
        rcon(f"clone from {src_dim} {x1} {y} {z1} {x2} {ye} {z2} to {dst_dim} {dx} {y} {dz}")


def backup(dim, cmds):
    if not cmds:                               # no changes: keep the copy from the previous job
        return
    box = job_box(cmds)
    if not box or (box[3] - box[0] + 1) * (box[5] - box[2] + 1) > 32768:
        log("no undo copy (found no coordinates, or the area is too big)")
        if os.path.exists(UNDO_FILE):
            os.remove(UNDO_FILE)
        return
    w, d = box[3] - box[0], box[5] - box[2]
    chunks(dim, box[0], box[2], box[3], box[5], True)
    chunks("minecraft:overworld", UNDO_X, UNDO_Z, UNDO_X + w, UNDO_Z + d, True)
    time.sleep(2)
    clone_box(dim, box, "minecraft:overworld", UNDO_X, UNDO_Z)
    chunks("minecraft:overworld", UNDO_X, UNDO_Z, UNDO_X + w, UNDO_Z + d, False)
    chunks(dim, box[0], box[2], box[3], box[5], False)
    json.dump({"dim": dim, "box": box}, open(UNDO_FILE, "w"))


def undo_last():
    try:
        u = json.load(open(UNDO_FILE))
    except (OSError, ValueError):
        return "I have nothing to undo (no copy from the previous job)."
    dim, box = u["dim"], u["box"]
    w, d = box[3] - box[0], box[5] - box[2]
    chunks(dim, box[0], box[2], box[3], box[5], True)
    chunks("minecraft:overworld", UNDO_X, UNDO_Z, UNDO_X + w, UNDO_Z + d, True)
    time.sleep(2)
    clone_box("minecraft:overworld", (UNDO_X, box[1], UNDO_Z, UNDO_X + w, box[4], UNDO_Z + d), dim, box[0], box[2])
    chunks("minecraft:overworld", UNDO_X, UNDO_Z, UNDO_X + w, UNDO_Z + d, False)
    chunks(dim, box[0], box[2], box[3], box[5], False)
    os.remove(UNDO_FILE)
    history.append(("undo", "Undid the previous job."))
    return "Undone: the area is back the way it was before the previous job."


def run_commands(player, cmds):
    """Runs the commands as one datapack function (fast). If it doesn't load (a line is invalid), runs them one by one."""
    if not cmds:
        return 0
    os.makedirs(CODEX_FUNC, exist_ok=True)
    open(os.path.join(CODEX_FUNC, "job.mcfunction"), "w").write("\n".join(cmds) + "\n")
    rcon("bossbar set " + BAR + ' name {"text":"' + NAME + ' is building…"}')
    server_log = os.path.join(MC_DIR, "data", "logs", "latest.log")
    start = os.path.getsize(server_log) if os.path.exists(server_log) else 0
    rcon("reload")
    time.sleep(1)
    with open(server_log, errors="replace") as f:      # an invalid line -> "Failed to load function …" in the log
        f.seek(start)
        broken = "Failed to load function supermann:codex/job" in f.read()
    if not broken:
        rcon(f"execute as {player} at @s run function supermann:codex/job")
        return 0
    log("the function did not load, running the commands one by one")
    failed = 0
    for i, c in enumerate(cmds):
        if current["cancelled"]:
            break
        r = rcon(c)
        if re.search(r"Unknown|Incorrect|Invalid|Expected|No player|Could not|not loaded|too many", r, re.I):
            failed += 1
        if i % 20 == 0:
            rcon(f"bossbar set {BAR} value {int(100 * i / len(cmds))}")
    return failed


def run_claude(player, request):
    if JSON_MODE:
        return run_codex(player, request)
    info, anchor = snapshot(player)
    prompt = f"The player {player} wrote in the chat: \"{request}\"\n\n{info}"
    sid, resume = session_id()
    base = ["claude", "-p", prompt, "--model", MODEL,
            "--append-system-prompt", SYSTEM_PROMPT.replace("{NAME}", NAME),
            "--disallowedTools",
            "Bash(./mc.sh cmd stop:*)", "Bash(./mc.sh cmd op:*)", "Bash(./mc.sh cmd deop:*)",
            "Bash(./mc.sh cmd whitelist:*)", "Bash(./mc.sh cmd ban:*)", "Bash(./mc.sh cmd kick:*)",
            "Bash(./mc.sh cmd reload:*)", "Bash(./mc.sh lp user * parent:*)", "Bash(./mc.sh lp user * permission set:*)",
            "Read(./.env)", "Read(./.chatbro.env)", "Read(./docker-compose.yml)",   # secrets
            "--allowedTools",
            "Bash(./mc.sh cmd:*)", "Bash(./mc.sh lp:*)", "Read", "Grep", "Glob"]
    forceload(anchor, True)
    current["anchor"] = anchor
    try:
        for attempt in range(2):
            args = base + (["--resume", sid] if resume else ["--session-id", sid])
            current["cancelled"] = False
            proc = subprocess.Popen(args, cwd=MC_DIR, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                    text=True, start_new_session=True, env=claude_env())
            current["proc"] = proc
            try:
                out, err = proc.communicate(timeout=TIMEOUT)
            except subprocess.TimeoutExpired:
                os.killpg(proc.pid, signal.SIGKILL)
                proc.communicate()
                return "That took too long, I gave up. Try a smaller wish."
            finally:
                current["proc"] = None
            if current["cancelled"]:
                return "Cancelled. Whatever was already built is still there."
            if proc.returncode == 0 and out.strip():
                return out.strip()
            log(f"claude failed (code {proc.returncode}, resume={resume}): {(err or out).strip()[:300]}")
            if not resume:
                break
            sid, resume = session_id(new=True)   # old conversation gone, start a new one
        return "Something went wrong on my side, see chatbro.log on the server."
    finally:
        forceload(anchor, False)
        current["anchor"] = None


busy = threading.Lock()
current = {"proc": None, "cancelled": False, "anchor": None}   # the running job, for "ai stop"


def cancel():
    proc = current["proc"]
    if proc is None:
        return False
    current["cancelled"] = True
    try:
        os.killpg(proc.pid, signal.SIGTERM)       # the whole process group, also commands the model has running
    except ProcessLookupError:
        pass
    return True



# --- The helper (Creator plugin, /helper) -------------------------------------------------------------
# "ai helper <anything>", or the helper's own name in chat, goes to the plugin first: it knows the common words
# (come, stay, guard, mine …) itself. What it does not understand it writes to the server log as HELPER_ASK, and
# helper_ask() below lets the model answer or pick a command. Open to every player.
HELPER_RE = re.compile(r"^(?:my\s+|min\s+)?(?:helper|hjelper(?:en)?)\b[\s,:]*(.*)$", re.I)
HELPER_SKIN_RE = re.compile(r"^(?:skin|look(?:s)? like|ser ut som|utseende)\s+(?:as\s+|som\s+|til\s+|to\s+)?@?(.{1,40})$", re.I)
HELPER_ASK_RE = re.compile(r"INFO\]: \[Creator\] HELPER_ASK (\w+)\|([^|]*)\|([^|]*)\|(.*?)\s*$")
HELPER_ACTIONS = ["none", "follow", "stay", "come", "guard", "passive", "fight", "mine", "farm", "fish", "torch on", "torch off",
                  "light on", "light off", "bridge on", "bridge off", "show on", "show off", "pickup on", "pickup off",
                  "bag", "unequip", "unload", "home", "sethome", "ride", "merge", "split", "big", "small", "normal", "stop", "quiet on", "quiet off",
                  "attack", "smelt", "dance", "places", "watch on", "watch off", "watch guard", "watch fish", "watch farm", "watch smelt",
                  "fetch", "find", "help", "join", "craft", "remember", "goto", "callme", "tidy", "repair", "collect", "trade", "give",
                  "routine", "routines", "routine clear", "routine remove", "scout", "wayback", "style sword", "style bow",
                  "style shield", "hide", "tag", "treasure", "hint", "skin", "name", "dismiss"]
HELPER_SCHEMA = {"type": "object", "additionalProperties": False, "required": ["action", "value", "answer", "remember"],
                 "properties": {"action": {"type": "string", "enum": HELPER_ACTIONS},
                                "value": {"type": "string"}, "answer": {"type": "string"}, "remember": {"type": "string"}}}
helper_busy = threading.BoundedSemaphore(4)       # so many helpers may think at the same time
helper_gate = threading.BoundedSemaphore(8)
HELPER_MEMORY = os.path.join(MC_DIR, "data", "helper-memory.json")     # per player: what it knows and the last things said


def helper_memory(player=None, update=None):
    """The helper's memory of each player: {"facts": [...], "talk": [[said, answered], ...]}. Kept small."""
    try:
        with open(HELPER_MEMORY) as f:
            mem = json.load(f)
    except (OSError, ValueError):
        mem = {}
    me = mem.setdefault(player, {"facts": [], "talk": []}) if player else None
    if update:
        update(me)
        me["facts"], me["talk"] = me["facts"][-12:], me["talk"][-6:]
        with open(HELPER_MEMORY, "w") as f:
            json.dump(mem, f, ensure_ascii=False, indent=1)
    return me


def helper_cmd(player, arg):
    rcon(f"execute as {player} at @s run helper {arg}".rstrip())


def helper_request(player, text):
    """ "ai helper <text>": a skin from skins/ is put in the resource pack first; everything else is handed to the
    plugin as /helper ask <text>. Returns a message for the player, or ""."""
    text = re.sub(r"\s+", " ", text).strip(" .!?")
    m = HELPER_SKIN_RE.match(text)
    if m:
        import skins
        value = m.group(1).strip()
        sl, path = skins.find(value)
        if sl:                                 # a PNG from skins/: into the resource pack, then send it out
            tell(player, f"Putting the skin {sl} on your helper…", "gray")
            if skins.for_helper(sl, path):
                rcon("sendpack")
            value = sl
        elif not re.fullmatch(r"[A-Za-z0-9_]{3,16}", value):
            return f"No skin called {value!r}. Skins: {', '.join(skins.names()) or '(none yet, save PNG files in skins/)'}. A Minecraft player name works too."
        helper_cmd(player, f"skin {value}")
    else:
        helper_cmd(player, f"ask {text}" if text else "")
    log(f"<{player}> helper: {text or 'summon'}")
    return ""


helper_waiting = set()


def helper_turn(player, say):
    """One helper thinks at a time. A player's own second wish is turned away; another player's wish waits in line
    (up to four minutes) and is told so."""
    if player in helper_waiting:
        say("Wait, I am still thinking about the last thing.")
        return False
    helper_waiting.add(player)
    if helper_busy.acquire(blocking=False):
        return True
    say("One moment: the other helpers are all thinking right now. You are next in line.")
    if helper_busy.acquire(timeout=240):
        return True
    helper_waiting.discard(player)
    say("That took too long. Ask me again in a little while.")
    return False


def helper_ask(player, name, around, text):
    """The plugin did not understand what the player said to their helper: the model answers as the helper and may
    pick one /helper command. The answer is shown as a chat line from the helper, only to that player."""
    def say(t):
        t = re.sub(r"\s+", " ", t).strip()[:240]
        if t:
            helper_cmd(player, f"say {t}")
    if not helper_turn(player, say):
        return
    try:
        if provider_problem() or not helper_gate.acquire(timeout=120):      # no model, or it is busy building
            say("I can't think right now, ask me again in a little while.")
            return
        try:
            log(f"<{player}> helper ask: {text}")
            mem = helper_memory(player)
            known = "\n".join(f"- {x}" for x in mem["facts"]) or "(nothing yet)"
            before = "\n".join(f"{player}: {q}\n{name}: {a}" for q, a in mem["talk"]) or "(this is the start)"
            d, problem = ask_json(f"""You are "{name}", the personal helper of the Minecraft player {player} on a family server (kids play here):
a loyal, cheerful companion that follows them, fights monsters, never dies and carries things in a bag.
What you remember about {player}:
{known}
What you can see right now: {around}
If a "Your personality:" is given in what you can see, that is who you are: let every answer sound clearly like
that character (choice of words, mood, little habits), still kind, correct and suitable for children.
The last things you two said:
{before}
Now the player says to you: \"{text}\"

answer = what you say back: one or two short sentences, in the language the player wrote in, plain text, friendly and
in character. If it is a question (about Minecraft or anything else), answer it correctly and concretely.
Asked what you think of their house or build, give your own honest, kind opinion based on the blocks you can see
(name a couple of them, and maybe one idea to make it even better). Asked for a joke, a riddle or a fun fact, tell a
good one that suits children, and not one you already told above; if you asked a riddle, judge their answer next time.
Use the name you call the player now and then.
action = one thing to do if the player asked for it, otherwise none: follow, stay (wait here), come (teleport to the
player), guard (guard this spot), passive / fight (stop / start fighting), mine (mine the block the player looks at
and its whole vein or tree), farm (harvest and replant crops around you), fish (fish in the nearest water), torch on/off (place torches where it is
dark), pickup on/off (pick up items), bag (open your bag for the player), unequip (give your armour and weapon
back), stop (stop the job), quiet on/off (stop / start commenting), light on/off (carry a light), bridge on/off
(put blocks under the player's feet over gaps, and steps when they look up), show on/off (let other players see
you), unload (empty your bag into the chests next to you, or at home), sethome (this place is home), home (take the
player home), ride (carry the player on your shoulders), merge (go into the player: they become unhurtable and as strong as
you, and do your jobs on autopilot), split (step out of the player again), big / small / normal (grow, shrink or go back to
normal size; bigger or smaller both make you stronger and faster, and at the smallest size monsters do not notice
you; merged, the player changes too), help (look after another player until called back; value = that player's
Minecraft name), join (work together with another player's helper; value = that player's name), attack (go for the
mob the player is looking at), craft (make an item from what is in the bag and chests; value = English item id and
optionally an amount, like "torch 16"), smelt (load the furnaces from your bag and bring back what is done),
remember (remember this place; value = its name), goto (take the player to a remembered place; value = its name),
places (list the remembered places), tidy (sort the chests at home), repair (mend what the player holds and
wears), collect (go to a remembered place and bring back what is in the chests there; value = the place), trade (buy
from a villager nearby; value = English item id and optionally how many times, like "bread 3"), give (carry what the
player holds to another player; value = that player's name), routine (a standing order; value = when + orders, where
when is morning, noon, evening, night or "every <minutes>" and the orders are action words from this list joined by
" and ", like "morning farm and smelt" or "every 30 unload"), routines (list them), routine remove (value = its
number), routine clear (remove all routines), scout (run 100 blocks the way the player looks and report), wayback (show the
way back out of a cave), style sword / style bow / style shield (fight up close, shoot arrows, or draw monsters onto
yourself), hide (play hide and seek: you hide), tag (play tag), treasure (bury a treasure and give clues), hint
(repeat the treasure clue), callme (what you call the player; value = the nickname they ask for, or, once
you have talked a few times and still have no nickname for them, a friendly one you came up with yourself), watch on/off (stay home while the player is logged out), watch guard / watch fish / watch farm / watch smelt (what to do at home while the player is logged out), dance, fetch (bring items from your bag or the chests; value = the
Minecraft item id in English and optionally an amount, like "iron_ingot 20"), find (point to the nearest ore, block
or structure; value = its English Minecraft id, like "diamond", "village" or "stronghold"), skin (value = a
Minecraft player name to look like), name (value = your new name), dismiss (go away until called).
fetch can also bring things from a place the helper remembers: value = "bone 64 from mobfarm".
NEVER say you will do, fetch, build or go somewhere unless you pick the action that really does it: your words do nothing
by themselves. If no action fits, say plainly that you cannot do that and what the player can say instead.
value = "" unless the action is fetch, find, help, join, give, craft, trade, collect, routine, routine remove,
remember, goto, callme, skin or name.
remember = one short fact worth keeping about the player from what they just said (their real name, what they like,
what they are building, a promise you made), or "" when there is nothing new. Never store secrets.
You cannot craft, place whole buildings or hand out items you do not have: say so kindly if asked. Do not run or read anything, just answer.""",
                                 HELPER_SCHEMA, "helper", effort="low", solo=False)
        finally:
            helper_gate.release()
        if d is None:
            say(problem)
            return
        action, value = d.get("action") or "none", re.sub(r"[^\w ' -]", "", d.get("value") or "", flags=re.U).strip()[:32]
        if action in ("skin", "name", "fetch", "find", "help", "join", "craft", "remember", "goto", "callme", "collect", "trade",
                      "give", "routine", "routine remove"):
            if value and (action not in ("skin", "help", "join", "give") or re.fullmatch(r"[A-Za-z0-9_]{3,16}", value)):
                helper_cmd(player, f"{action} {value}")
        elif action in HELPER_ACTIONS and action != "none":
            helper_cmd(player, action)
        answer, fact = re.sub(r"\s+", " ", d.get("answer") or "").strip()[:240], re.sub(r"\s+", " ", d.get("remember") or "").strip()[:160]
        say(answer)

        def note(me):
            me["talk"].append([text[:200], answer])
            if fact and fact not in me["facts"]:
                me["facts"].append(fact)
        helper_memory(player, note)
    except Exception as e:  # noqa: BLE001
        log(f"helper ask failed: {e}")
        say("Something went wrong in my head. Try again?")
    finally:
        helper_waiting.discard(player)
        helper_busy.release()



# --- A helper asked to build something it has no plan for: the model draws it, the helper builds it by hand ----------
HELPER_BUILD_RE = re.compile(r"INFO\]: \[Creator\] HELPER_BUILD (\w+)\|([^|]*)\|(.*?)\s*$")
BLUEPRINTS = os.path.join(MC_DIR, "data", "plugins", "Creator", "blueprints")
BLUEPRINT_SCHEMA = {"type": "object", "additionalProperties": False, "required": ["name", "legend", "layers"], "properties": {
    "name": {"type": "string"},
    "legend": {"type": "array", "items": {"type": "object", "additionalProperties": False, "required": ["key", "block"],
                                          "properties": {"key": {"type": "string"}, "block": {"type": "string"}}}},
    "layers": {"type": "array", "items": {"type": "object", "additionalProperties": False, "required": ["y", "rows"],
                                          "properties": {"y": {"type": "integer"}, "rows": {"type": "array", "items": {"type": "string"}}}}}}}
BLUEPRINT_PROMPT = """You are the architect for a helper character on a family Minecraft server (Java 26.3). A player asked the helper to build
something. You draw it as a block plan; the helper then builds it by hand, block by block, from materials it gathers itself.
Do not run anything and do not read files. Answer only with the JSON object.

The wish: {wish}

Format:
- "name": two or three plain English words for what it is, lower case (for example "stone cottage").
- "legend": one entry per letter you use: "key" is ONE character, "block" is a Minecraft block id without "minecraft:", with block
  states in square brackets where they matter, for example "oak_stairs[facing=north,half=bottom]", "oak_log[axis=y]",
  "ladder[facing=south]", "wall_torch[facing=east]", "oak_trapdoor[facing=north,half=top,open=false]". The character "." is always air.
- "layers": horizontal slices. "y" is the height: y = 0 is the level where the player's feet are (the first layer above the ground),
  y = -1 is the ground block itself (put the floor there), lower numbers are underground (a basement), higher numbers go up.
  Give every layer you use, each with the same number of rows and every row the same length. Rows run from north (first row) to
  south (last row); the characters in a row run from west (first) to east (last).
- The FRONT of the building, with the main door, is the SOUTH side (the last row). It is turned to face the player afterwards.

Rules:
- At most 21 columns, 21 rows, y from -6 to 20, and about 1500 blocks that are not air. Smaller is better: it is built by hand.
- Make it a real, complete, good-looking thing: closed walls, a floor at y = -1, a roof, a doorway people can walk through (a door
  is the same door block at y = 0 and y = 1; facing = the direction you look when you walk IN), windows, light inside, stairs or a
  ladder between floors, and furniture that fits (bed, chest, crafting_table, furnace). The inside must be air where people walk.
- A bed is one cell "red_bed" (the helper adds the second half next to it in a free cell).
- Stair "facing" is the direction you look when you walk UP the stair. A roof of stairs: the west slope uses facing=east, the east
  slope facing=west, the north slope facing=south, the south slope facing=north.
- The helper makes these itself: any wood block (all wood in one building becomes the one kind of wood that grows nearby),
  cobblestone, stone, stone_bricks, smooth_stone and their stairs, slabs and walls, glass, glass_pane, sandstone, torch, wall_torch,
  ladder, chest, barrel, crafting_table, furnace, campfire, fences, fence gates, trapdoors, doors, beds, dirt. Prefer these.
  Other blocks are only set when the player has them in a chest, so use them sparingly and only when the wish names them.
- Never use water, lava, fire, tnt or redstone machines. For something "watertight" just make the walls and floor solid."""


def helper_build(player, name, wish):
    def say(t):
        t = re.sub(r"\s+", " ", t).strip()[:240]
        if t:
            helper_cmd(player, f"say {t}")
    if not helper_turn(player, say):
        return
    try:
        if provider_problem() or not helper_gate.acquire(timeout=120):
            say("I can't draw right now, ask me again in a little while.")
            return
        try:
            log(f"<{player}> helper build: {wish}")
            d, why = ask_json(BLUEPRINT_PROMPT.replace("{wish}", wish[:300]), BLUEPRINT_SCHEMA, "blueprint", effort="medium", solo=False)
        finally:
            helper_gate.release()
        if d is None:
            say(why or "I could not draw it. Ask me again?")
            return
        os.makedirs(BLUEPRINTS, exist_ok=True)
        with open(os.path.join(BLUEPRINTS, f"{player}.json"), "w") as f:
            json.dump(d, f)
        log(f"blueprint for {player}: {d.get('name')} ({len(d.get('layers') or [])} layers)")
        rcon(f"helper tell {player} buildplan")              # works when the player has logged out too
    except Exception as e:  # noqa: BLE001
        log(f"helper build failed: {e}")
        say("Something went wrong with my drawing. Try again?")
    finally:
        helper_waiting.discard(player)
        helper_busy.release()


# --- Merged with a helper: the player wears the helper's skin, which must be signed by mineskin.org first -----------
HELPER_SIGN_RE = re.compile(r"INFO\]: \[Creator\] HELPER_SIGN (\w+)\|([a-z0-9_]+)\s*$")
sign_told = set()


def helper_sign(player, sl):
    import skins
    try:
        key = skins._key()
        if not key:
            if player not in sign_told:
                sign_told.add(player)
                helper_cmd(player, "say To look like me while we are one, the server needs a MineSkin key (MINESKIN_API_KEY in .chatbro.env). Until then you keep your own looks.")
            return
        found, path = skins.find(sl)
        if not found:
            return
        for attempt in range(3):                                # MineSkin now and then fails for no reason of ours
            try:
                skins.sign(found, path, key)
                break
            except Exception as e:  # noqa: BLE001
                log(f"helper sign, try {attempt + 1}: {e}")
                if attempt == 2:
                    helper_cmd(player, "say I could not get my looks ready for you this time. Split and merge again in a minute.")
                    return
                time.sleep(8)
        log(f"<{player}> helper skin signed for merge: {found}")
        rcon(f"helper tell {player} mergeskin")
    except Exception as e:  # noqa: BLE001
        log(f"helper sign failed: {e}")


# --- Your own skin from skins/ (skins.py): "ai skin <name>", "ai skin reset", "ai skins" ------------------
SKIN_RE = re.compile(r"^(?:skins?|change skin|bytt skin)\b[\s,:]*(?:(?:on |på )?(?:me|meg)\s+|to\s+|til\s+)?(.*)$", re.I)


def skin_request(player, arg):
    import skins
    arg = arg.strip(" .!?")
    if arg.lower() in ("", "list", "liste", "names", "navn"):
        return f"Skins: {', '.join(skins.names()) or '(none yet)'}. New ones: save a 64x64 PNG in skins/ on the server. \"{KW} skin <name>\" for you, \"{KW} helper skin <name>\" for your helper."
    if arg.lower() in ("reset", "normal", "vanlig", "original", "my own", "min egen"):
        rcon(f"execute as {player} run skin reset")
        return ""
    sl, path = skins.find(arg)
    if not sl:
        return f"No skin called {arg!r}. Skins: {', '.join(skins.names()) or '(none yet)'}."
    tell(player, f"Signing the skin {sl} with mineskin.org, a moment…", "gray")
    skins.sign(sl, path, CONF.get("MINESKIN_API_KEY", ""))
    rcon(f"execute as {player} run skin {sl}")
    log(f"<{player}> skin: {sl}")
    return ""


def handle(player, request):
    if not request:
        tell(player, f"Write what you want, e.g. \"{KW} build a bridge 20 blocks ahead\".")
        return
    low = request.lower().strip(" .!")
    if low == "help":
        tell(player, f"Ready-made builds: type /build_ and scroll with the arrow keys (e.g. /build_ship_…, /build_castle_…), /buildundo undoes. Anything else: write \"{KW}\" and your wish; \"{KW} undo\" removes what I built last. \"{KW} stop\" cancels the running job, \"{KW} reset\" starts a new conversation. \"{KW} make supertool <wish>\" makes a new tool for the Big Bench (/bb), \"{KW} change <id> <wish>\" changes it, \"{KW} redraw <id>\" draws it again. \"{KW} helper …\" or just your helper's name talks to your helper (come, stay, guard, mine, farm, fish, unload, fetch <item>, find <thing>, home, ride, bridge, light, merge, split, or a question). \"{KW} skin <name>\" changes your own skin (PNG files in skins/), \"{KW} skins\" lists them. Progress is shown at the top of the screen.")
        return
    if low in ("stop", "cancel"):
        tell(player, "Cancelling the job…" if cancel() else "No job is running.", "gray")
        return
    if low in ("reset", "new conversation"):
        session_id(new=True)
        tell(player, "New conversation started.")
        return
    sm = SKIN_RE.match(request.strip())
    if sm:
        if not busy.acquire(blocking=False):
            tell(player, "I'm still working on the previous wish, wait a moment.", "gray")
            return
        try:
            answer = skin_request(player, sm.group(1))
            if answer:
                tell(player, answer)
        except Exception as e:  # noqa: BLE001
            log(f"skin failed: {e}")
            tell(player, f"The skin did not work: {str(e)[:150]}")
        finally:
            busy.release()
        return
    hm = HELPER_RE.match(request.strip())
    if hm:
        try:
            answer = helper_request(player, hm.group(1))
            if answer:
                tell(player, answer)
        except Exception as e:  # noqa: BLE001
            log(f"helper failed: {e}")
            tell(player, f"Something went wrong with the helper: {str(e)[:150]}")
        return
    if not busy.acquire(blocking=False):
        tell(player, "I'm still working on the previous wish, wait a moment.", "gray")
        return
    if JSON_MODE and low in ("undo", "undo last", "remove last"):
        try:
            answer = undo_last()
            log(f"<{player}> undo: {answer}")
            tell(player, answer)
        finally:
            busy.release()
        return
    sup = super_match(request)
    if sup:
        try:
            if not JSON_MODE:
                tell(player, "Super tools need a JSON-capable provider (anything but claude), see .chatbro.env.")
                return
            tell(player, "I'm drawing and making the super tool, it takes a couple of minutes…", "gray")
            log(f"<{player}> super tool: {request}")
            t0 = time.time()
            answer = run_super(player, *sup)
            log(f"answer ({time.time() - t0:.0f}s): {answer[:300]}")
            tell(player, answer)
        except Exception as e:  # noqa: BLE001
            log(f"super tool failed: {e}")
            tell(player, "Something went wrong with the super tool, see chatbro.log.")
        finally:
            busy.release()
        return
    if provider_problem():
        busy.release()
        log(f"chatbro is not set up: {provider_problem()}")
        tell(player, "Right now I can only build from the catalog: type /build_ and scroll with the arrow keys.")
        return
    try:
        tell(player, "I heard you, working…", "gray")
        log(f"<{player}> {request}")
        t0 = time.time()
        sid, _ = session_id()
        try:
            with open(os.path.join(TRANSCRIPTS, sid + ".jsonl")) as f:
                start_lines = sum(1 for _ in f)
        except OSError:
            start_lines = 0
        bar_start(player)
        stop = threading.Event()
        threading.Thread(target=bar_ticker, args=(stop, t0, start_lines), daemon=True).start()
        try:
            answer = run_claude(player, request)
        finally:
            stop.set()
        ok = not answer.startswith(("That took too long", "Something went wrong", "Cancelled"))
        log(f"answer ({time.time() - t0:.0f}s): {answer[:300]}")
        tell(player, answer)
        bar_end(ok)
    finally:
        busy.release()


def new_world(name):
    """Runs when the server creates a world: registers it in the Obsidian note "Minecraft-verdener" (worlds.py),
    and "super" worlds get the powers right away (sync-aliases + reload)."""
    time.sleep(5)                                       # let Multiverse finish writing worlds.yml
    try:
        import worlds
        n = worlds.sync()
        if n:
            log(f"new world {name}: added to the world register note")
    except Exception as e:  # noqa: BLE001
        log(f"could not register world {name} in the note: {e}")
    if "super" not in name.lower():
        return
    try:
        if f"minecraft:{name} " in open(SUPERWORLDS).read():
            return
    except OSError:
        pass
    log(f"new super world {name}: updating superworlds.mcfunction and running /reload")
    subprocess.run(["./mc.sh", "sync-aliases"], cwd=MC_DIR, capture_output=True, timeout=60)
    rcon("reload")
    rcon('tellraw @a {"text":"[Supermann] ' + name + ' is a Supermann world: powers and gear are on.","color":"gold"}')


def build(player, code):
    log(f"<{player}> /{code}")
    try:
        err = findbuild.build_code(player, code)
    except Exception as e:   # noqa: BLE001
        err = f"The build failed: {e}"
    if err:
        log(err)
        tell(player, err)


def master(issuer, cmd, arg):
    """/master <name> or /master @a grants bot access, /masteroff [name] takes it away (everyone except MASTER)."""
    if issuer != MASTER:
        tell(issuer, "Only " + MASTER + " can grant access to " + NAME + ".")
        return
    if cmd == "master" and not arg:
        who = "everyone" if "*" in guests else (", ".join(sorted(guests)) or "only you")
        tell(MASTER, f"{NAME} access: {who}. Use /master <name> or /master @a, and /masteroff to take it back.")
        return
    if cmd == "master":
        if arg == "@a":
            guests.add("*")
            rcon("tellraw @a " + json.dumps({"text": PREFIX + f"Everyone can use {NAME} now: write {KW} and a wish in the chat.", "color": "gold"}, ensure_ascii=False))
        else:
            guests.add(arg.lower())
            tell(arg, f"You can use {NAME} now: write {KW} and a wish in the chat, for example \"{KW} build a bridge here\".")
            tell(MASTER, f"{arg} can use {NAME} now.")
    elif arg and arg != "@a":
        guests.discard(arg.lower())
        tell(MASTER, f"{arg} can no longer use {NAME}." + (" (Access for everyone is still on, use /masteroff.)" if "*" in guests else ""))
    else:
        guests.clear()
        rcon("tellraw @a " + json.dumps({"text": PREFIX + NAME + " is now only for " + MASTER + ".", "color": "gray"}, ensure_ascii=False))
    save_guests()
    log(f"<{issuer}> /{cmd} {arg or ''} -> access: {sorted(guests)}")


def creator_power(player, on):
    """/creatorpower gives the player /craft (crafting table anywhere, plugin in plugins-src/creator), /creatorpoweroff takes it away."""
    r = subprocess.run(["./mc.sh", "lp", "user", player, "permission", "set" if on else "unset", "creator.craft"]
                       + (["true"] if on else []), cwd=MC_DIR, capture_output=True, text=True, timeout=60)
    log(f"<{player}> creator power {'on' if on else 'off'}: {(r.stdout + r.stderr).strip()[-120:]}")
    if on:
        tell(player, "Creator power on! Type /c or /craft for a crafting table anywhere. /creatorpoweroff turns it off.")
        time.sleep(1)
        rcon(f"craft {player}")
    else:
        tell(player, "Creator power off. Now you need a crafting table again.")


def skin_watcher():
    """Every 20 s: new or changed PNG files in skins/ go into the resource pack and out to everyone online, so
    /helper skin <name> works in game shortly after the file is saved (no chat command needed)."""
    import skins
    while True:
        if busy.acquire(blocking=False):           # not while a job (or a super tool pack build) is running
            try:
                changed = skins.sync_all()
                if changed:
                    rcon("sendpack")
                    log(f"skins imported: {', '.join(changed)}")
            except Exception as e:  # noqa: BLE001
                log(f"skin import failed: {e}")
            finally:
                busy.release()
        time.sleep(20)


def follow_log():
    while True:
        try:
            p = subprocess.Popen(["docker", "logs", "-f", "--tail", "0", "mc"],
                                 stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, errors="replace")
            log("following the server log")
            for line in p.stdout:
                w = WORLD_RE.search(line)
                if w:
                    threading.Thread(target=new_world, args=(w.group(1),), daemon=True).start()
                    continue
                j = JOIN_RE.search(line)
                if j and current["proc"] is None and not busy.locked():   # the boss bar can linger after a restart
                    rcon(f"bossbar remove {BAR}")
                if j and j.group(1) in pending:
                    threading.Thread(target=flush_pending, args=(j.group(1),), daemon=True).start()
                    continue
                hs = HELPER_SIGN_RE.search(line)
                if hs:
                    threading.Thread(target=helper_sign, args=hs.groups(), daemon=True).start()
                    continue
                hb = HELPER_BUILD_RE.search(line)            # a helper was asked to build something it must draw first
                if hb:
                    threading.Thread(target=helper_build, args=hb.groups(), daemon=True).start()
                    continue
                ha = HELPER_ASK_RE.search(line)              # a helper was asked something the plugin does not know
                if ha:
                    threading.Thread(target=helper_ask, args=ha.groups(), daemon=True).start()
                    continue
                mm = MASTER_RE.search(line)
                if mm:
                    threading.Thread(target=master, args=mm.groups(), daemon=True).start()
                    continue
                s = CREATOR_RE.search(line)
                if s:
                    threading.Thread(target=creator_power, args=(s.group(1), s.group(2) == "creatorpower"), daemon=True).start()
                    continue
                b = BUILD_RE.search(line)                    # /build_<type>_<name>: GrabCraft build, no model
                if b and findbuild.in_catalog(b.group(2)):
                    threading.Thread(target=build, args=(b.group(1), b.group(2)), daemon=True).start()
                    continue
                m = CMD_RE.search(line)                      # /ai … (only the player sees it)
                if m:
                    player, request = m.group(1), m.group(2)
                else:                                        # ai … in normal chat
                    m = CHAT_RE.search(line)
                    if not m:
                        continue
                    k = KEYWORD_RE.match(m.group(2))
                    if not k:
                        continue
                    player, request = m.group(1), k.group(1)
                if not may_talk(player) and not HELPER_RE.match(request.strip()):   # everyone may talk to their own helper
                    log(f"ignored <{player}> {request}")
                    continue
                threading.Thread(target=handle, args=(player, request.strip()), daemon=True).start()
            p.wait()
            log(f"docker logs ended (code {p.returncode}), retrying in 10 s")
        except Exception as e:  # noqa: BLE001
            log(f"error: {e}")
        time.sleep(10)


def on_terminate(signum, frame):
    """Clean up when launchd stops us (restart/stop): kill the job, release forceload, remove the boss bar."""
    if cancel():
        log("shutting down: cancelled the running job")
    forceload(current.get("anchor"), False)
    rcon(f"bossbar remove {BAR}")
    sys.exit(0)


if __name__ == "__main__":
    signal.signal(signal.SIGTERM, on_terminate)
    if len(sys.argv) >= 4 and sys.argv[1] == "--once":   # test: chatbro.py --once Player "wish"
        handle(sys.argv[2], sys.argv[3])
        sys.exit(0)
    threading.Thread(target=skin_watcher, daemon=True).start()
    log(f"chatbro starting ({PROVIDER}, model {MODEL or 'default'}{', NOT SET UP: ' + provider_problem() if provider_problem() else ''}, allowed: {', '.join(sorted(ALLOWED | guests))})")
    follow_log()
