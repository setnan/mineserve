#!/usr/bin/env bash
# Helper script for day-to-day running of the Minecraft server.
# Run ./mc.sh without arguments for help.
set -euo pipefail
cd "$(dirname "$0")"

rcon() { docker compose exec mc rcon-cli -- "$@"; }   # "--" so negative coordinates (-7) are not taken as flags

# LuckPerms does not answer over RCON, so send via the console pipe and show what ends up in the log
lp() {
  local log=data/logs/latest.log n; n=$(wc -l < "$log")
  docker compose exec -T -u 1000 mc mc-send-to-console "lp $*"
  sleep 1.5; tail -n +$((n+1)) "$log" | grep '\[LP\]\|luckperms' || true
}
# Writes a /<worldname> alias in commands.yml for every Multiverse world (not nether/end).
# Paper reads it at startup, so this runs automatically on up/restart.
sync_aliases() {
  local f=data/commands.yml w gen
  # aliases.yml (in git) + data/aliases-local.yml (yours) + the generated blocks kept from the current file
  gen="$(sed -n '/# --- auto: build catalog/,/# --- end blueprint aliases ---/p' "$f" 2>/dev/null | sed '/# --- auto: world aliases/,$d' || true)"
  { cat aliases.yml
    [ ! -f data/aliases-local.yml ] || grep -v '^#' data/aliases-local.yml
    [ -z "$gen" ] || printf '%s\n' "$gen"
  } > "$f.new" && mv "$f.new" "$f"
  echo "  # --- auto: world aliases (written by ./mc.sh sync-aliases, do not edit below here) ---" >> "$f"
  # the world name (folder) is legacy-world-name, otherwise the key without "minecraft:"
  for w in $(awk '/^[^ #]/ { if (k != "" && !l) print k; k = $0; sub(/:$/, "", k); sub(/^minecraft:/, "", k); l = 0 }
                  /legacy-world-name:/ { print $2; l = 1 }
                  END { if (k != "" && !l) print k }' data/plugins/Multiverse-Core/worlds.yml 2>/dev/null); do
    case "$w" in *_nether|*_the_end) continue ;; esac
    grep -q "^  - mvtp $w\$" "$f" && continue   # already has an alias (e.g. /superworld)
    printf '  %s:\n  - mvtp %s\n' "$w" "$w" >> "$f"
  done
  sync_superworlds
}

# Which worlds are Supermann worlds (powers, gear): the main world (+ nether/end), all worlds with
# "super" in the name (like the Multiverse-Inventories group) and those listed in data/superworlds.txt
# (they must also be added to the inventory group: mvinv addworld <name> default).
# Written to the datapack, read by loop.
sync_superworlds() {
  local f=data/world/datapacks/supermann/data/supermann/function/superworlds.mcfunction k
  {
    echo "# Written by ./mc.sh sync-aliases (do not edit, not in git): your extra Supermann worlds."
    echo "# Runs after superworlds_base via the tag #supermann:superworlds."
    for k in $(grep -o '^minecraft:[^:]*' data/plugins/Multiverse-Core/worlds.yml 2>/dev/null | grep -i super) \
             $(grep -v '^#' data/superworlds.txt 2>/dev/null | awk 'NF { print "minecraft:" tolower($1) }'); do
      echo "execute if dimension $k run scoreboard players set #super sm_tunnel 1"
    done | sort -u
  } > "$f"
}
# Worlds where normal players have no cheats and ops can get them temporarily (world-gm, cheat). Set in .env.
RESTRICTED_WORLDS="$(grep '^MC_RESTRICTED_WORLDS=' .env 2>/dev/null | cut -d= -f2 | tr ',' ' ')"
CHEAT_GROUP="$(grep '^MC_CHEAT_GROUP=' .env 2>/dev/null | cut -d= -f2)"; CHEAT_GROUP="${CHEAT_GROUP:-cheat}"   # LuckPerms group
CHATBRO_LABEL="$(grep '^MC_CHATBRO_LABEL=' .env 2>/dev/null | cut -d= -f2)"; CHATBRO_LABEL="${CHATBRO_LABEL:-local.mc-chatbro}"
LAUNCHD="$(command -v launchctl || true)"
OWNER="$(grep '^OPS=' .env 2>/dev/null | cut -d= -f2 | cut -d, -f1)"   # default player for gm and chatbro test

set_env() { # set_env KEY VALUE  -> updates .env
  local key="$1" val="$2"
  if grep -q "^${key}=" .env; then
    sed -i '' "s|^${key}=.*|${key}=${val}|" .env
  else
    echo "${key}=${val}" >> .env
  fi
  echo ".env: ${key}=${val}"
}

case "${1:-}" in
  up|start)     sync_aliases; docker compose up -d ;;
  down|stop)    docker compose down ;;
  restart)      sync_aliases; docker compose up -d --force-recreate mc
                # playit shares the network with mc and must be recreated with it
                if grep -q '^COMPOSE_PROFILES=.*playit' .env; then docker compose up -d --force-recreate --no-deps playit; fi ;;
  tunnel)       docker compose logs --tail=50 playit ;;
  update)       docker compose pull && docker compose up -d ;;
  logs)         docker compose logs -f --tail=200 mc ;;
  status)       docker compose ps; echo; rcon list ;;
  console)      docker compose exec -it mc rcon-cli ;;
  cmd)          shift; rcon "$@" ;;

  # --- players / permissions ---
  op)           rcon op "$2" ;;
  deop)         rcon deop "$2" ;;
  kick)         shift; rcon kick "$@" ;;
  ban)          shift; rcon ban "$@" ;;
  pardon)       rcon pardon "$2" ;;
  whitelist)    shift; rcon whitelist "$@" ;;      # add <name> | remove <name> | list | on | off
  players)      rcon list ;;

  # --- world / mechanics (live, no restart) ---
  gamerule)     shift; rcon gamerule "$@" ;;      # ./mc.sh gamerule keepInventory true  (no value = show)
  gamemode)     shift; rcon gamemode "$@" ;;      # ./mc.sh gamemode creative Player
  gm)           rcon gamemode "${2:?usage: ./mc.sh gm <mode> [player]}" "${3:-$OWNER}" ;;
  difficulty)   # per world (Multiverse), always the overworld, nether and end together:
                #   difficulty                 shows each world
                #   difficulty <level>         the world the owner (OWNER) is in now
                #   difficulty <level> <world|all>
                mvworlds() { for p in 1 2 3; do docker compose exec -T mc rcon-cli -- mv list --raw -p $p 2>/dev/null; done | sed 's/\x1b\[[0-9;]*m//g' | tr -d '\r' | sed -E -n 's/^(.*) - (NORMAL|NETHER|THE_END) *$/\1/p' | sort -u; }
                lvl=${2:-}; want=${3:-}
                if [ -z "$lvl" ]; then
                  for w in $(mvworlds); do printf '%-26s %s\n' "$w" "$(docker compose exec -T mc rcon-cli -- mv info "$w" 2>/dev/null | sed 's/\x1b\[[0-9;]*m//g' | sed -n 's/.*Difficulty: //p' | head -1)"; done
                else
                  if [ -z "$want" ]; then
                    dim=$(docker compose exec -T mc rcon-cli -- data get entity "$OWNER" Dimension 2>/dev/null | sed 's/\x1b\[[0-9;]*m//g' | sed -n 's/.*"minecraft:\([^"]*\)".*/\1/p')
                    if [ -z "$dim" ]; then        # logged out: the world the helper works in (its home)
                      uuid=$(python3 -c "import json,sys; print(next((e['uuid'] for e in json.load(open('data/usercache.json')) if e['name'].lower()==sys.argv[1].lower()),''))" "$OWNER" 2>/dev/null)
                      dim=$(awk -v u="$uuid:" '$0==u {f=1; next} /^[^ ]/ {f=0} f && /^  home: / {print $NF; exit}' data/plugins/Creator/helpers.yml)
                      [ -z "$dim" ] && { echo "$OWNER is not online and the helper has no home. Say which world: ./mc.sh difficulty $lvl <world|all>"; exit 1; }
                      echo "$OWNER is not online: using the world the helper works in"
                    fi
                    case "$dim" in overworld) dim=world ;; the_nether) dim=world_nether ;; the_end) dim=world_the_end ;; esac
                    want=$(printf '%s' "$dim" | sed -E 's/_(nether|the_end)$//')
                    echo "World: $dim, setting $want with its nether and end"
                  fi
                  want=$(printf '%s' "$want" | sed -E 's/_(nether|the_end)$//')
                  all=$(mvworlds)
                  if [ "$want" = all ]; then pick=$all; else pick=$(printf '%s\n' "$all" | grep -i -x -E "$want(_nether|_the_end)?"); fi
                  [ -z "$pick" ] && { echo "No world called $want. See ./mc.sh worlds"; exit 1; }
                  for w in $pick; do rcon mv modify "$w" set difficulty "$lvl" | sed 's/\x1b\[[0-9;]*m//g'; done
                fi ;;
  defaultgamemode) rcon defaultgamemode "$2" ;;
  say)          shift; rcon say "$*" ;;
  time)         rcon time set "${2:-day}" ;;
  weather)      rcon weather "${2:-clear}" ;;
  save)         rcon save-all ;;
  helper|h)     # ./mc.sh h <words>  =  ./mc.sh helper <words> (your own helper) | @<player> <words> | status | pause | resume
                shift
                case "${1:-status}" in
                  status|pause|resume|save) rcon helper "${1:-status}" ;;
                  @*) who="${1#@}"; shift; rcon helper tell "$who" "$@" ;;
                  *)  rcon helper tell "${OWNER:?no OPS in .env}" "$@" ;;
                esac ;;
  helpers|hs)   # ./mc.sh helpers <words>: the same order to every helper, e.g. helpers work at home | fish | find diamonds | explore
                shift
                [ $# -eq 0 ] && { echo "usage: ./mc.sh helpers <words>   (the same order to every helper)"; exit 1; }
                names=$(sed -n "s/^  name: ['\"]\{0,1\}\([^'\"]*\)['\"]\{0,1\} *$/\1/p" data/plugins/Creator/helpers.yml)
                [ -z "$names" ] && { echo "No helpers in data/plugins/Creator/helpers.yml"; exit 1; }
                printf '%s\n' "$names" | while IFS= read -r n; do
                  echo "== $n"
                  docker compose exec -T mc rcon-cli -- helper named "$n" "$@" </dev/null | sed 's/\x1b\[[0-9;]*m//g'
                done ;;
  pause)        # freezes the whole server (no CPU, nothing happens in the world) after saving everything. ./mc.sh resume to go on
                if ! rcon list | grep -q "There are 0 " && [ "${2:-}" != "--force" ]; then
                  echo "Players are online (they would be disconnected). ./mc.sh pause --force to pause anyway."; exit 1
                fi
                rcon helper save; rcon save-all flush; sleep 3
                docker compose pause mc && echo "Paused. ./mc.sh resume to continue." ;;
  resume)       docker compose unpause mc && echo "Running again." ;;

  # --- LuckPerms / restricted worlds (MC_RESTRICTED_WORLDS in .env) ---
  lp)           shift; lp "$@" ;;                  # ./mc.sh lp user Name permission info
  world-gm)     # grant/remove /gamemode in the restricted worlds:  ./mc.sh world-gm <name> [on|off]
                name="${2:?usage: ./mc.sh world-gm <name> [on|off]}"
                for w in $RESTRICTED_WORLDS; do
                  if [ "${3:-on}" = off ]; then lp user "$name" permission unset minecraft.command.gamemode "world=$w"
                  else lp user "$name" permission set minecraft.command.gamemode true "world=$w"; fi
                done ;;
  cheat)        # all commands in the restricted worlds temporarily (LuckPerms group MC_CHEAT_GROUP):  ./mc.sh cheat <name> [time, e.g. 10m]
                lp user "${2:?usage: ./mc.sh cheat <name> [time]}" parent addtemp "$CHEAT_GROUP" "${3:-10m}" ;;
  uncheat)      lp user "${2:?usage: ./mc.sh uncheat <name>}" parent removetemp "$CHEAT_GROUP" ;;

  # --- worlds (Multiverse) ---
  new-world)    # ./mc.sh new-world <name> [normal|nether|the_end] [--seed 123 --world-type flat ...]
                name="$(printf %s "${2:?usage: ./mc.sh new-world <name> [environment] [flags]}" | tr "[:upper:]" "[:lower:]")"; env="${3:-normal}"   # world names are lower case
                shift $(( $# < 3 ? $# : 3 )); rcon mv create "$name" "$env" "$@" ;;
  worlds)       rcon mv list ;;
  sync-aliases) sync_aliases; echo "Updated data/commands.yml (takes effect on ./mc.sh restart) and superworlds.mcfunction (/reload)." ;;

  # --- chat bridge: "ai …" in chat -> AI model (chatbro.py as a launchd agent on macOS, systemd user service on Linux; see service/) ---
  chatbro)      case "${2:-status}" in
                  status)  if [ -n "$LAUNCHD" ]; then launchctl print "gui/$(id -u)/$CHATBRO_LABEL" 2>/dev/null | grep -E "state|pid" | head -3; else systemctl --user --no-pager status mc-chatbro | head -3; fi; tail -n 5 chatbro.log 2>/dev/null ;;
                  log)     tail -f chatbro.log ;;
                  restart) if [ -n "$LAUNCHD" ]; then launchctl kickstart -k "gui/$(id -u)/$CHATBRO_LABEL"; else systemctl --user restart mc-chatbro; fi ;;
                  stop)    if [ -n "$LAUNCHD" ]; then launchctl bootout "gui/$(id -u)/$CHATBRO_LABEL"; else systemctl --user stop mc-chatbro; fi ;;
                  start)   if [ -n "$LAUNCHD" ]; then launchctl bootstrap "gui/$(id -u)" "$HOME/Library/LaunchAgents/$CHATBRO_LABEL.plist"; else systemctl --user enable --now mc-chatbro; fi ;;
                  test)    python3 chatbro.py --once "${3:-$OWNER}" "${4:?usage: ./mc.sh chatbro test <player> \"wish\"}" ;;
                  *)       echo "usage: ./mc.sh chatbro [status|log|restart|stop|start|test <player> \"wish\"]" ;;
                esac ;;

  # --- player heads on the locator bar ---
  heads|pack)   for f in *_pack.py; do python3 "$f"; done   # custom items (needs the Minecraft client, see heads.client_jar)
                python3 heads.py ;;                 # + player heads -> data/resourcepack/supermann-heads.zip, then ./mc.sh restart

  # --- GrabCraft blueprints (links in the note MC_BLUEPRINTS_NOTE) ---
  blueprints)   python3 blueprints.py ;;            # writes bp/*.mcfunction + aliases; /reload, new commands after restart

  # --- world register in a markdown note (MC_WORLDS_NOTE) ---
  worlds-sync)  python3 worlds.py ;;               # adds new worlds from worlds.yml to the note

  # --- backup ---
  backup)       docker compose exec backup backup now ;;
  backups)      ls -lh backups/ ;;

  # --- import your own world ---
  import-world)
    src="${2:?usage: ./mc.sh import-world <path to zip or world folder>}"
    name="$(basename "$src")"
    if [ -d "$src" ]; then
      rm -rf "worlds/$name"; cp -R "$src" "worlds/$name"
    else
      cp "$src" "worlds/$name"
    fi
    set_env WORLD "/worlds/$name"
    echo
    echo "World copied to worlds/$name."
    echo "It is used on the next start if data/\$LEVEL does not exist yet."
    echo "To replace the existing world:  ./mc.sh replace-world"
    ;;
  replace-world)
    level="$(grep '^LEVEL=' .env | cut -d= -f2)"; level="${level:-world}"
    echo "This deletes data/${level} (and world_nether/world_the_end for Paper) and imports WORLD from .env again."
    read -r -p "Continue? [y/N] " a; [ "$a" = "y" ] || exit 1
    docker compose stop mc
    rm -rf "data/${level}" "data/${level}_nether" "data/${level}_the_end"
    docker compose up -d mc
    ;;

  *)            # ./mc.sh <helper name> <words>: an order to the helper with that name (see data/plugins/Creator/helpers.yml)
                if [ -n "${1:-}" ] && [ $# -ge 2 ] && grep -qi "^  name: ['\"]\{0,1\}${1}['\"]\{0,1\}\s*$" data/plugins/Creator/helpers.yml 2>/dev/null; then
                  name="$1"; shift; rcon helper named "$name" "$@"; exit
                fi
                # anything else that is not "help": an order to your own helper (./mc.sh farm = ./mc.sh h farm)
                if [ -n "${1:-}" ] && [ "$1" != "help" ] && [ "$1" != "-h" ] && [ "$1" != "--help" ] && [ -n "$OWNER" ]; then
                  echo "(not an ./mc.sh command, so sent to your helper; ./mc.sh help lists the commands)"
                  rcon helper tell "$OWNER" "$@"; exit
                fi
    cat <<'HELP'
Usage: ./mc.sh <command>

Server:       up | down | restart | update | logs | status | console | cmd <mc-command> | tunnel
Players:      op <name> | deop <name> | kick <name> | ban <name> | pardon <name> | players
              whitelist add|remove|list|on|off [name]
Mechanics:    gamerule <rule> <value> | gamemode <mode> [player] | gm <mode> [player=first in OPS]
              difficulty [<level> [world|all]]   (no level: shows each world; no world: where OWNER is; nether and end follow)
              defaultgamemode <mode> | time [day|night] | weather [clear|rain] | say <text> | save
Perms:        world-gm <name> [on|off]   (/gamemode in MC_RESTRICTED_WORLDS)
              cheat <name> [time=10m] | uncheat <name>   (all commands there temporarily)
              lp <luckperms-command>
Backup:       backup | backups
Pack:         pack    (resource pack: custom items + player heads, then restart; also: heads)
Worlds:       worlds-sync   (new worlds into the note MC_WORLDS_NOTE)
Blueprints:   blueprints   (GrabCraft links in the note MC_BLUEPRINTS_NOTE -> /build_<code>)
Helpers:      <words> (anything that is not a command here goes to your own helper) | h <words> | helper <words> | helper @<player> <words> | <helper name> <words> | helper status|pause|resume
              helpers <words>   (the same order to every helper: work at home, fish, find diamonds, explore …)
              pause | resume   (saves and freezes the whole server, and goes on)
Chat bridge:  chatbro [status|log|restart|stop|start|test <player> "wish"]   ("ai …" in chat)
Worlds:       new-world <name> [normal|nether|the_end] [--seed N --world-type flat ...] | worlds
              sync-aliases   (writes /<worldname> for each world, also happens on restart)
World:        import-world <zip|folder> | replace-world

Permanent changes (version, seed, MODE, DIFFICULTY, OPS, WHITELIST, gamerules at startup):
edit .env / docker-compose.yml and run ./mc.sh restart
HELP
    ;;
esac
