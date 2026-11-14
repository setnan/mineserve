# Runs every 2 seconds. "replace" prevents double runs after /reload.
schedule function supermann:loop 2s replace
# Supermann worlds: the main world (with its nether/end) and every world with "super" in its name
# (the list in superworlds.mcfunction is written by ./mc.sh sync-aliases on every restart). Other Multiverse
# worlds are completely normal.
execute as @a at @s run function supermann:player_check
# Hide and seek on: players who log in meanwhile are also put on the team (applies to all worlds)
execute if score #hideandseek sm_tunnel matches 1 run team join hideandseek @a[team=!hideandseek]
# Player heads as icons on the locator bar (heads_style is written by heads.py; the tag skips it when it does not exist yet)
function #supermann:heads
