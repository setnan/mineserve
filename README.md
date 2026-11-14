# mineserve

Our family Minecraft server. Paper in Docker, our own plugin (**Creator**), a datapack (**supermann**)
and a small Python bridge that lets you type `ai build a lighthouse here` in the chat.

It runs on [itzg/minecraft-server](https://docker-minecraft-server.readthedocs.io/), which takes care of the
server jar, EULA, `server.properties`, ops, whitelist and world import through environment variables.

It was made for one server (Paper 26.3 on a Mac), so expect to change paths and names if you use it.

## Creator plugin

Source in `plugins-src/creator`, Java, compiled against the Paper API the server downloads.

| What | Commands |
|---|---|
| Crafting stations anywhere (permission `creator.craft`) | `/craft` (`/c`), `/bigbench` (`/bb`), `/tool_anvil`, `/tool_smithing`, `/tool_stonecutter`, `/tool_grindstone`, `/tool_loom`, `/tool_cartography`, `/tool_enderchest`, `/tool_trash` |
| Powers you switch on and off. Sneak to pause one | `/lumberpower`, `/minerpower`, `/harvestpower`, `/magnetpower`, `/flypower`, `/lightpower`, each with `…off`. `/super` / `/superoff`: no damage or hunger. `/powers` lists them |
| Big Bench: a 5x5 bench for super tools described in JSON | `/bb`, `/givesuper`, `/superreload`, `/superlist`, `/superapprove`, `/superdelete` |
| Metal detector: points to the nearest block, structure or biome | `/detector <thing>` (`/d`), `/detectoroff`, `/givedetector` |
| Magic wand, 32 spells (floor, wall, sow, blink, shield, duplicate, mob spells like tame, ride, zombify, big/small …) | `/wand <spell>` (`/wa`), `/wand undo`, `/clones <count> <seconds>`, `/givewand` |
| A helper for every player, see below | `/helper …`, or its name in chat |
| Skins from your own `skins/` folder, for you and your helper | `/skin <name>\|reset\|list`, `/helper skin <name>`, `/sendpack` (op) |
| Storage barrels: your own storage (same in every barrel) and a shared one | `/storagebarrel`, `/dump`, `/dumpmine`, `/pc`, `/cc`, `/fetch` |
| Golden chicken: feed a chicken a golden apple | `/goldenchicken`, `/givegoldenegg`, `/givegoldenfood` |
| Glowing coal (burns like 64 coal) and x-ray goggles | `/giveglowingcoal`, `/givexray` |
| Teleport requests, gravestones, water elevators, one-player sleep | `/tpa`, `/tpaccept`, `/tpdeny`, `/pos`, `/grave`, `/elevator`, `/elevatordown` |

`give…` commands are for ops. The rest can be crafted or found in survival. In the worlds listed in
`MC_RESTRICTED_WORLDS`, `/super` needs the `creator.restricted.bypass` permission.

### Helpers

Every player gets a helper that only they can see. It follows you, fights for you, never dies and can wear any
skin. Talk to it by name in the chat (`Bob come`, `Bob how do I make a piston?`), start a line with a full stop
(`.come`), or use `/helper <order>`. Common words are understood directly; anything else goes to the AI bridge.

With you:

- **Fighting**: `guard`, `attack` (what you look at), `passive`, `style sword|bow|shield`. It never hurts players,
  and it leaves piglins alone unless they go for you. `help <player>` sends it to look after someone else.
- **Work**: mines the vein or tree you look at, farms, fishes, places torches, builds bridges.
  `craft <item> [n]`, `smelt`, `repair`, `tidy`, `trade <item>`, `collect <place>`.
- **Building**: describe what you want and it draws it, gathers the materials and builds it block by block.
  It builds on top of a foundation at your feet. `undo build` takes its last building down again.
- **Carrying**: a bag (sneak + right-click), picks up drops, `fetch <item>`. `unload` takes the bag, and whatever
  is in the chest next to it, home to the chests there, or to your storage barrel if you have one.
- **Getting around**: `remember <name>`, `goto <name>`, `places`, `scout`, `wayback`, `ride`. `merge` puts the
  helper inside you (you get its strength and can fly), `split` lets it out. `big`, `small`, `normal`.
- **The rest**: hide and seek, tag, treasure hunts, jokes, a nickname for you (`callme <name>`), opinions on your
  builds and a warning when something sneaks up on you. When you die, it keeps your things.

While you are logged out it works at home (set it with `/helper sethome`). It harvests, fishes, smelts and looks
after the animals. When the fields are done it goes exploring, further out each time (up to about 3000 blocks),
and saves the structures it finds as places. It also makes treasure maps for chests nobody has opened and digs
whatever ore you have least of (diamond, gold, iron, coal, lapis, redstone, in that order). `find redstone` or
`find birch` makes it look for one thing first, `find nothing` goes back to normal. `watch fish|farm|smelt|explore|guard|off`
gives it a different job. If it is in the middle of a building when you log out, it finishes that first, and a
building in progress survives a server restart.

From the terminal: `./mc.sh helper <words>` (yours), `./mc.sh helpers <words>` (all of them),
`./mc.sh helper status|pause|resume`.

## supermann datapack

In `data/world/datapacks/supermann`: `/tunnel`, `/icetunnel`, `/glassbridge`, `/staircase`, `/staircase_up`,
`/house`, `/hideandseek`, `/save home` + `/home`, a GrabCraft blueprint builder with undo (`/build`,
`/buildundo`) and player heads on the locator bar. The command names are aliases in `aliases.yml`, along with
some WorldEdit shortcuts (`/copyarea`, `/paste`, `/undo` …).

## AI chat bridge

`chatbro.py` is optional. It reads the server log and answers chat lines that start with `ai`:

- `ai <wish>` builds something in front of you. The model only writes a plan; chatbro checks the commands against
  a deny-list before running them over RCON, and `ai undo` puts the area back.
- `ai make supertool <wish>` designs a new Big Bench tool (stats, abilities from a fixed list, a 16x16 drawing).
  `ai change <id> <wish>` and `ai redraw <id>` edit it. Tools made by other players wait for `/superapprove`.
- Helper questions the plugin does not understand itself end up here, and the model answers as the helper. It
  remembers the last few things said and a few facts per player (`data/helper-memory.json`).
- `ai skin <name>`, `ai helper skin <name>`, `ai skins`.
- `ai help`, `ai stop`, `ai reset`. `/ai <wish>` does the same without others seeing it.
- Only the owner can use it at first. `/master <name>` (or `/master @a`) gives others access, `/masteroff` takes it back.

The name and the trigger word are set in `.chatbro.env` (`CHATBRO_NAME`, `CHATBRO_KEYWORDS`).

## Resource pack

The `*_pack.py` scripts draw the custom items into `resourcepack-extra/`, some by recolouring vanilla textures
from your local Minecraft client. `heads.py` adds the player heads and builds
`data/resourcepack/supermann-heads.zip`, which the `mc-pack` nginx container serves. The pack itself is not in git.

## Files

| File / folder | What |
|---|---|
| `.env.example` → `.env` | Server config: version, memory, world, rules, ops, whitelist, RCON, tunnel |
| `.chatbro.env.example` → `.chatbro.env` | Which AI model the chat bridge uses |
| `docker-compose.yml` | `mc` (server), `pack` (resource pack), `backup`, and an optional `playit` tunnel. Gamerules are in `RCON_CMDS_STARTUP` |
| `mc.sh` | Day-to-day commands. `./mc.sh` lists them |
| `skins.py` | Imports skins from `skins/` (resource pack for helpers, mineskin.org signing for players) |
| `chatbro.py`, `service/` | The chat bridge, and service files to run it in the background (launchd / systemd) |
| `plugins-src/creator/` | The Creator plugin. `build.sh` compiles it in a JDK container |
| `plugins-src/creator/supers/` | The five built-in super tools |
| `data/world/datapacks/supermann/` | The datapack |
| `aliases.yml` | Command aliases. `./mc.sh sync-aliases` (runs on every start) turns it, `data/aliases-local.yml`, the `build_…` catalog and one `/<world>` alias per Multiverse world into `data/commands.yml` |
| `findbuild.py`, `blueprints.py` | GrabCraft catalog search and blueprint import |
| `worlds.py` | Keeps a list of worlds in a markdown note (`MC_WORLDS_NOTE` in `.env`) |

Not in git: `.env`, `.chatbro.env`, the rest of `data/` (worlds, player data, plugin data), `backups/`, `worlds/`,
logs and anything generated. `.gitignore` is a whitelist, so a new file stays out until you add it there.

## Getting started

You need [Docker](https://docs.docker.com/get-docker/), Python 3 (no extra packages) and a Minecraft Java account.
Everything targets Minecraft 26.3.

**1. The server**

```bash
git clone https://github.com/setnan/mineserve.git ~/minecraft && cd ~/minecraft
cp .env.example .env        # fill in OPS, WHITELIST and RCON_PASSWORD (openssl rand -hex 16)
./mc.sh up                  # the first start downloads Paper and the plugins; ./mc.sh logs until "Done"
```

Connect to `localhost:25565`, or the machine's address on your network. The datapack works now.

**2. The Creator plugin**

```bash
plugins-src/creator/build.sh   # needs the Paper API that step 1 downloaded
./mc.sh restart
```

While the server is running, `build.sh` puts the new jar in `data/plugins/update/`, and Paper swaps it in on the
next start. Ops have everything. Other players get the crafting stations with
`./mc.sh lp user <name> permission set creator.craft true`.

**3. The resource pack** (optional, the items work without it)

Start Minecraft 26.3 once on the same computer, since the scripts read vanilla textures from the client (or set
`MC_CLIENT_JAR`). Then:

```bash
./mc.sh pack                # writes data/resourcepack/supermann-heads.zip and RESOURCE_PACK_SHA1 in .env
```

Set `RESOURCE_PACK=http://<your-computer>.local:8080/supermann-heads.zip` in `.env` and run `./mc.sh restart`.
Use the `.local` name (`scutil --get LocalHostName` on macOS) rather than an IP address: when the IP changes,
the pack stops loading without any error.

**4. The chat bridge** (optional)

Pick a model in `.chatbro.env` (`CHATBRO_PROVIDER`, plus `CHATBRO_API_KEY` and `CHATBRO_MODEL` for API services):

| Provider | Cost | What you need |
|---|---|---|
| `codex` | included in a ChatGPT plan | `npm install -g @openai/codex`, then `codex login` |
| `openai`, `anthropic`, `gemini`, `mistral`, `groq`, `deepseek` | pay per use (Gemini and Groq have free tiers) | an API key and a model name |
| `openrouter` | pay per use, lots of models | a key from openrouter.ai, model as `vendor/model` |
| `glm` | free (`glm-4.7-flash`) | an API key from z.ai |
| `ollama`, `lmstudio` | free, runs locally | the app and a model (small models get coordinates wrong) |
| `openai-compatible` | depends | also set `CHATBRO_BASE_URL` |
| `claude` | uses your Claude plan | Claude Code installed and logged in |

All except `claude` work the same way: the model writes a JSON plan and chatbro runs it, so building, undo and
super tools work with any of them. The key stays in `.chatbro.env` and only goes to the provider you picked.

```bash
cp .chatbro.env.example .chatbro.env && chmod 600 .chatbro.env
./mc.sh chatbro test <your-name> "build a small tower"   # a dry run from the terminal
python3 chatbro.py                                       # Ctrl-C stops it
```

To run it in the background, use `service/mc-chatbro.plist.example` (macOS) or `service/mc-chatbro.service`
(Linux); each file says how to install it. Then `./mc.sh chatbro start|stop|restart|status|log`. The owner is the
first name in `OPS`.

**5. A shortcut** (optional)

I start everything with `mineserve`. Put this in `~/.zshrc` or `~/.bashrc`. Without arguments it starts Docker
Desktop if needed, starts the server and follows the log; with arguments it passes them to `mc.sh`:

```bash
mineserve() {
  local dir="$HOME/minecraft"                       # where you cloned the repo
  if [ "$(uname)" = Darwin ] && ! docker info >/dev/null 2>&1; then
    open -a Docker; until docker info >/dev/null 2>&1; do sleep 2; done
  fi
  if [ $# -eq 0 ]; then "$dir/mc.sh" up && "$dir/mc.sh" logs; else "$dir/mc.sh" "$@"; fi
}
```

**6. Skins** (optional)

Put 64x64 skin PNGs in `skins/` (not in git). The file name is the name you use: `skins/pirate.png` →
`/skin pirate` or `/helper skin pirate`. Download skins yourself and respect the site's terms; nothing here
fetches them for you.

- Slim "Alex" arms are detected. If a slim skin gets thick arms, rename it `<name>.slim.png`.
- Helper skins go into the resource pack about 20 seconds after you save the file, and the pack is sent to
  everyone online. No account needed.
- Your own skin has to be signed by Mojang to show up, so the bridge uploads it to
  [mineskin.org](https://mineskin.org) (unlisted) once. Put a free key from account.mineskin.org/keys in
  `.chatbro.env` as `MINESKIN_API_KEY`.
- From the terminal: `python3 skins.py list | sync | helper <name> | sign <name>`.

**7. Extras**

- GrabCraft builds as commands: `python3 findbuild.py catalog`, `./mc.sh restart`, then type `/build_` in game.
- Friends outside your network: a free [playit.gg](https://playit.gg) tunnel (`PLAYIT_SECRET_KEY` and
  `COMPOSE_PROFILES=playit` in `.env`).
- Your own aliases go in `data/aliases-local.yml`, extra worlds with powers in `data/superworlds.txt`. The rest
  of the settings for `mc.sh` and the scripts are at the end of `.env.example`.

## World settings

Under **World** and **Rules** in `.env`: `SEED`, `LEVEL_TYPE`, `MODE`, `DIFFICULTY`, `HARDCORE`, `PVP` … Seed and
level type only matter when the world is created. For the rest, change it and `./mc.sh restart`.

Difficulty per world (Multiverse): `./mc.sh difficulty` shows them, `./mc.sh difficulty hard` sets the world you
are in (nether and end follow), `./mc.sh difficulty hard <world|all>` picks one.

Gamerules are in `docker-compose.yml` under `RCON_CMDS_STARTUP` and run on every start. To change one without a
restart: `./mc.sh gamerule keep_inventory false`. Minecraft 26.x uses snake_case names, 1.21.10 and older
camelCase. List: https://minecraft.wiki/w/Game_rule

## Importing a world

```bash
./mc.sh import-world ~/Downloads/myworld.zip     # or a folder with level.dat
./mc.sh up
```

The world is copied to `worlds/` and `WORLD=/worlds/myworld.zip` is set in `.env`. It is only imported if
`data/world` does not exist yet, and the repo has the datapack inside `data/world/datapacks/`, so move that
aside first and put it back after the first start:

```bash
mv data/world/datapacks /tmp/supermann-datapacks && rmdir data/world
./mc.sh up                                   # imports the world; wait for "Done"
cp -r /tmp/supermann-datapacks data/world/datapacks && ./mc.sh cmd reload
```

`./mc.sh replace-world` swaps the world on a running setup (save `data/world/datapacks` the same way).
Singleplayer worlds on a Mac are in `~/Library/Application Support/minecraft/saves/<name>`.

## Permissions

- Only players in `OPS` can cheat. `OP_PERMISSION_LEVEL` sets how much (2 = normal commands, 4 = everything).
- `./mc.sh op <name>` / `./mc.sh deop <name>` work live. With `EXISTING_OPS_FILE=SYNCHRONIZE`, `OPS` in `.env`
  wins after a restart, so change it there too.
- Whitelist: `ENABLE_WHITELIST=true` and `WHITELIST=name1,name2`, or `./mc.sh whitelist add <name>`.
- Anything finer goes through LuckPerms (`./mc.sh lp …`).

## Running it somewhere else

Any Linux machine with Docker will do:

```bash
rsync -av --exclude data --exclude backups ./ user@server:~/minecraft/
ssh user@server 'cd ~/minecraft && ./mc.sh up'
```

Open TCP 25565 (and 8080 for the resource pack). 2 vCPU and 4 GB RAM is plenty for about five players. The chat
bridge service and the `launchctl` parts of `mc.sh` are macOS-only.

## Backups

`./mc.sh backup` puts a snapshot in `backups/` (run it from cron or launchd), `./mc.sh backups` lists them.
To restore, stop the server, unpack a `backups/world-*.tgz` into `data/` and start it again.
