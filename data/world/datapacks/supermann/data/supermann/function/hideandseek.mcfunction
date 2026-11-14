# /hideandseek: hide where the players are, without anyone having to sneak.
# 1) Name tags above heads show through walls: everyone goes on a team with nametagVisibility never.
# 2) The locator bar (coloured dots at the top of the screen) shows the direction to everyone: gamerule off.
function supermann:hideandseek_team
gamerule locator_bar false
# The flag makes players who log in later join the team too (see loop.mcfunction)
scoreboard players set #hideandseek sm_tunnel 1
tellraw @a [{"text":"[Supermann] ","color":"gold"},{"text":"Hide and seek on: name tags and the locator bar are hidden. /hideandseekoff turns them back on.","color":"gray"}]
