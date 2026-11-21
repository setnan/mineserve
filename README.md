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
- **Getting around**: `remember <name>`, `goto <name>`, `places`, `scout`, `wayback`. `merge` puts the
  helper inside you (you get its strength and can fly), `split` lets it out. `big`, `small`, `normal`.
- **The rest**: hide and seek, tag, treasure hunts, jokes, a nickname for you (`callme <name>`), opinions on your
  builds and a warning when something sneaks up on you. When you die, it keeps your things.

While you are logged out it works at home (set it with `/helper sethome`). It harvests, fishes, smelts (raw iron, gold and copper from your
barrel too) and looks after the animals, for as long as you are away (`settings.away-hours` in helpers.yml sets a limit). When the fields are done it goes exploring, further out each time (up to about 3000 blocks),
and saves the structures it finds as places. It also makes treasure maps for chests nobody has opened and digs
whatever ore you have least of (diamond, gold, iron, coal, lapis, redstone, in that order). `find redstone` or
`find birch` makes it look for one thing first, `find nothing` goes back to normal. `watch fish|farm|smelt|explore|guard|off`
gives it a different job. If it is in the middle of a building when you log out, it finishes that first, and a
building in progress survives a server restart.

From the terminal: `./mc.sh helper <words>` (yours), `./mc.sh helpers <words>` (all of them),
`./mc.sh helper status|pause|resume`.

## Haven

A dimension of floating islands over an azure sea, with waterfalls everywhere, no monsters, no hunger and no night:
the sun dips into a sunset and comes back up. Everything grows twice as fast there. Eleven biomes (dawn grove, silver birch, cloud jungle, oasis, glory peaks, golden savanna, golden
forest, golden plains, flower meadow, crystal valley, rainbow falls) with their own trees and colours. The biomes are
in the datapack `data/world/datapacks/haven`, generated from the vanilla files in the server jar by
`plugins-src/haven/gen.py`; run it again after a Minecraft update.

Every world has its own Haven, the way it has its own nether and end: `<world>_haven` (`world_haven`,
`hope_haven`, ...). The Creator plugin makes it the first time the world is loaded, also for worlds created later,
gives it a random seed of its own (kept in `plugins/Creator/haven-worlds.yml`) and puts it in the
Multiverse-Inventories group of its world, so you keep your inventory. The land comes from `HavenGenerator.kt`:
islands floating under the clouds over an azure sea (ground mostly at y 100 to 140, peaks to about 180 where it is
cold), cut apart by chasms, with hills, mesas and ledges, and small islets in the sky above them that each have a
waterfall. The earth is not the overworld's: grass and moss instead of dirt, cliffs overgrown with moss, pearl rock
in bands (calcite, diorite and a band of golden sandstone, prismarine or moss), an azure prismarine underside with
sea lanterns, sand and prismarine on the sea floor, and each biome its own top (bare rock over the tree line of the
glory peaks, amethyst in the crystal valley, sand on the golden plains, golden clay on the savanna, mud at the
oases, honeycomb under the golden forest). Gold is rare: a little ore and very rarely a raw gold block in the glory
peaks. The biomes, trees and ores come from the datapack; the ores also grow in the pearl rock
(`haven:ore_replaceables`). When a Haven is made, the log says how much of it is land and how high it lies. Time stands still and the plugin
swings the sun between noon and a sunset or sunrise glow; random tick speed 6 makes things grow twice as fast.
Haven worlds are not in Multiverse-Core's `worlds.yml`; the plugin loads them.

Every world family (the world, its nether, end and Haven) has one Multiverse-Inventories group and shares
everything inside it, never with another family. A world without a group gets its own, named after it, the first
time it loads; a nether or end made later joins its world's group. New worlds always get lower-case names
(`/new Skog`, `mv create Skog normal` and `./mc.sh new-world Skog` all make `skog`); existing worlds keep theirs.

To get there, build a frame of shroomlight shaped like a nether portal and pour a bucket of water into it. Stand in
the azure portal for a second. One block in Haven is 16 blocks back home, and you return to the world you came
from. Operators have `/haven` (list), `/haven portal <world> <x> <z>`, `/haven close <n>` and
`/haven go [player]`.

What is there (the Kotlin classes `Haven*.kt` in the Creator plugin):

| | |
|---|---|
| Ores | diamond ore is common and drops diamond shards (9 = a diamond); havenite debris (azure chiseled copper) deep in the islands drops havenite scrap (4 scrap + 4 gold = havenite ingot); amethyst gives light crystals; pearlescent froglight in the peaks is nacre |
| Havenite armor | ingot + diamond piece on the smithing table. Helmet: long breath. Chestplate: elytra wings. Leggings: step up, faster, dolphin swim. Boots: no fall damage. Full set: you jump about three blocks high, and sneak + jump to soar, once per landing |
| Light crystals | on the smithing table onto armor (light around you), sword (more against undead), shield (flash), bow (arrows leave light), fishing rod, horse armor, elytra |
| Pearls | from clams (sea pickles) and fishing; very rarely a time pearl: right click, everything within 16 blocks stands still for 10 s (only monsters outside Haven, never players) |
| Golden barley | wheat in Haven, twice the harvest; the seeds keep that at home. Barley fields on the golden plains |
| Nacre | placed at home: no monsters spawn within 16 blocks |
| Sea fruit | small, round, shining turquoise fruits on a blue coral fan, here and there on the sea floor; break the fan for one to three (older land had them as warped wart blocks: they are changed when the land is loaded). Eat one and you are as small as a player can be (1/16) for a minute. Plant it in any world: sneak and right click the bottom of a sea or lake (a blue coral fan shows the sprout), and after 8 to 14 minutes there is a new fruit |
| Glow fruit | small, round, light green fruits in the caves (a head block with the skin from `plugins-src/haven/fruit.py`), on the floor or on a little azalea bush, often with a glow berry vine over them, most of them in the lush caves. Older land has them as verdant froglights, which give fruit too. Eat one and you grow as big as there is room for, up to 16 times your size, for a minute: you keep growing when you come out in the open, step, jump and reach like a giant, walk as many times faster as you are bigger (and fly up to ten times faster), dig and hit as many times harder, and take a giant's bite with every block you break (a ball of blocks up to eight across; chests are left alone), and rock around your head does not hurt. Plant it in any world: sneak and right click moss or earth out of the daylight (a fern shows the sprout). `/haven fruit` gives four of each |
| Star dust and starstone | glowstone in the rock and the cave walls of Haven gives star dust; five star dust and four light crystals (checkered in the crafting table) make two starstones |
| Teleportals | a frame of starstone shaped like a nether portal (inside at least 2 x 3, so ten starstones), lit with a lava bucket. Every world (overworld, Nether, End, a Haven) has room for two, and you walk from one straight to the other however far apart they are; never to another world. One alone is lit but asleep (grey), the pair is bright green. Breaking a frame block closes it: build and light it again. `/haven kit` gives starstone for two, lava and a flying carpet |
| Flying carpet | the sheep of Haven give magic wool; five magic wool and a light crystal (`www` over `wcw`) make a flying carpet. It is purple with an ornate weave, a gold border and gold tassels. Right click to sit on it, in any world: forward flies where you look (sprint is faster), jump rises, it hovers when you let go, sneak or right click steps off and you float down. You keep the carpet in your inventory |
| Golden Ram's Horn | one of Haven's rarest treasures (1 in 40 temple chests, 1 in 25 of the best wishes at the well). Blown in the overworld: the Lion comes out of a burst of gold and fights for you (pounces, heavy golden blows, bosses take it but are not felled at once) until every enemy within 40 blocks is gone, then bows its head and goes (at most 5 minutes; the horn can call it every 5 minutes). Blown in the nether or the end: the ground shakes in three waves and every enemy within 20 blocks is flung back several blocks (once a minute). Nothing in Haven. `/the_lion horn [player]` gives one |
| Music | C418's creative music in the open golden lands (golden plains and savanna, glory peaks, crystal valley, oasis), the cherry grove music in dawn grove and rainbow falls, the meadow music in flower meadows and silver birch, flower forest music in the golden forest, jungle music in the cloud jungle; 3 to 8 minutes between pieces. Motifs on note block sounds (`HavenMusic.kt`): arriving through a portal, a wish at the well, coming near a temple (once in ten minutes), the Lion appearing and the Lion arriving where it wanted to take you |
| Angel temples | rare (at least 700 blocks apart): a domed temple open up into its dome, with a chandelier, two rows of pillars, water running along the walls from two falls at the back, and the treasure chest on an altar with a light crystal behind it. Two angels keep the open portal and one the altar; they walk in and out. `/haven temple` builds one in front of you |
| In the sky | now and then an angel lives on a wool cloud or on one of the bigger islets, and about a third of them keep a small treasure chest (the log says where: "a treasure in the sky at x y z") |
| Caves | inside the bigger islands: winding tunnels, halls and now and then a great hall, open to the cliffs and here and there to the sky, and of three kinds. Lush caves: moss, glow berry vines and spore blossoms from the roof, azaleas and dripleaves. Crystal caves: amethyst and calcite walls lit by pearlescent froglights, amethyst clusters, calcite icicles with a glowing tip. Water caves: prismarine, pools with light in them, lantern icicles and now and then a waterfall from the roof. Land from before this keeps its old cave shapes, but gets the vines, crystals and fruit the first time it is loaded again. A few hold a treasure chest on a glowing sea lantern (the log says where: "a treasure in a cave at x y z"). Now and then a builder lives in one and hews cave houses into the rock: a white front with a prismarine frame, an azure vault, a porch with lamps and flowers, and a furnished room behind it |
| Sea builders | builders in bright azure or green suits who live on the sea floor and shine in the water (an aqua outline). They build houses under water that are dry inside: a glass dome with prismarine ribs, or a long panorama house of glass in a prismarine frame with a glass roof, both lit by sea lanterns and furnished. They lay the whole shell first and then take the water out block by block from the top; a door keeps the sea out, so you walk in and breathe. Otherwise they are builders like the others: they come back to an unfinished house and finish it. `/haven seabuilder` sets one where you stand |
| Bridges | the builders' favourite (half of what they build): from the edge of one island to another 10 to 64 blocks away, also somewhat higher or lower. An arch with an azure line down the deck and an azure underside, ribs that deepen towards the cliffs, a low parapet with lamp posts, a gate at each end, five wide from 16 blocks and with a domed canopy in the middle from 30 |
| Creatures | Haven is the home of the allays (in new land, most in the flower meadows and dawn groves, and a few more drift in around players now and then); golden chickens; winged horses (tame with 6–12 golden apples, jump to fly); the wand in Haven: no hostile, no fire, nothing at all on the Lion, and no disarm, ride or fetch on angels and builders (big, small and lift are fine); angels (they turn to you and greet you when you come near and stroll about when nobody is close; men and women in white and gold armor with gold crowns or circlets, blond most often but also brown, black or red hair, white gold-edged wings; drawn by `plugins-src/haven/angels.py` into the pack, then `python3 heads.py`; trade only for diamonds, cannot be hurt, warn once and strike on the second blow within 30 s, never against full havenite); builders (small flying craftsmen in a white work uniform with azure or green lines, gold buttons, a leather pouch on the left hip, a gold hammer on the back slung at 45 degrees, no cap, brown, blond or grey hair; drawn by `plugins-src/haven/builders.py` who put up Santorini-style buildings near players, "poff" when hit; a builder always comes back to its site and carries on until the building is finished, puts back what was knocked out, and another builder takes over if the first is lost: unfinished buildings are kept in `plugins/Creator/haven-jobs.yml`); sky birds around players (golden shoals, manta birds, rare sky whales, dolphin swallows when you fly); the Lion (one per Haven, now and then near a player; cannot be tamed or hurt; cares for players and has a bond with helpers; on the wolf's body (broad, deep upper body) at 3.6 times normal size, about three blocks tall, with its own tawny gold coat, a majestic golden mane, golden brows and deep dark blue eyes (wolf variant `haven:lion`, coat drawn by `plugins-src/haven/lion.py`, `render3d.py` shows it in 3D); it glints faintly with gold and carries a soft light; its size follows the room it has (full size in the open, down to about a block where it is tight, so it comes through anywhere), and it leaps up to 64 blocks far and 48 up, rising fast and gliding down slowly and gracefully: stand on a block and right click to ride, or stroke it (sneak + right click) and it sits down so you can climb on from the ground; your helper comes along: riding heals you, mends your armor and what you hold and gives experience slowly; it comes to show you something: it stays near you for a minute or so, then leads the way there, waiting when you fall behind (or ride it, and it carries you, leaping onto high islands); once you have seen the place it is gone when you look away, never before; `/the_lion [player]` brings it) |
| Magic | levitation columns (a golden chicken in one anchors it), time rifts to fixed safe spots, the wishing well (gift for a blessing, once a game day; fall in for a cloud ride), gold water (armor to gold, deadly), havenite springs (every armor piece you wear to havenite), a rainbow that points to the nearest spring, speed and jump in the spray at the rainbow falls, angel temples with treasure |

Places and claimed land are kept in `plugins/Creator/haven-places.yml` and `haven-land.yml`. New land is decorated
the first time a chunk loads, so the area that existed before an update does not get it.

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
| `plugins-src/creator/` | The Creator plugin, Java and Kotlin. `build.sh` builds it with Gradle in a JDK container |
| `plugins-src/haven/` | `gen.py` writes the Haven datapack |
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

## Worlds without cheats

The worlds in `MC_PURE_WORLDS` (`.env`, default `globus`) are plain Minecraft with no cheats, whatever happens. The
Creator plugin makes the family the first time it starts with the name (`globus`, `globus_nether`, `globus_the_end`,
random seeds, no Haven) and gives it a Multiverse-Inventories group of its own, so nothing is carried in or out. The
code is `Pure.kt`.

- `/globus` takes you there from any world, back to the spot where you left it (the spawn the first time). `/go <world>`
  and the `/<world>` aliases take you out again.
- Nothing of the Creator plugin happens there: all its event listeners are deaf in those worlds, its loops leave out
  the players there, nobody has a power or a helper, and nothing from the plugin or the datapacks can be crafted. The
  startup gamerules (keep inventory and so on) only apply to the main world, so globus has Minecraft's own.
- No commands for anybody, operators too, except chat (`/msg`, `/tell`, `/me`, `/list`, `/help`), the world commands
  and `/peaceful` and `/hard`.
- Everybody is in survival, cannot fly and has a player's own size, speed and reach. It is checked four times a
  second, so a game mode set from the console does not last.
- The console and RCON (`./mc.sh cmd`, the chat bridge) do not run a command that names the world or a player who is
  in it, except to look (`data get`), talk, moderate (`kick`, `ban`, `op`, `whitelist`) and manage it with `mv`,
  `mvinv` and `lp`. The chat bridge answers "I can't help you in globus".
- `/peaceful` and `/hard` switch the difficulty of the whole family, for whoever is there. It starts peaceful.

Not covered: a console command with a selector (`@a`, `@e`) is not looked into, and whoever runs the machine can
always take the world out of `MC_PURE_WORLDS`.

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

## Feedback

This is open source under the [MIT license](LICENSE), so use whatever you like. It is a hobby project for my
family, but if you try it, find a bug or have an idea for something better, open an issue or a pull request.
I would like to hear about it.
