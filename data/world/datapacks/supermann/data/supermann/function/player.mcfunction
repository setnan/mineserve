# Everything Supermann does for one player. Called from player_check.mcfunction, only in Supermann worlds.
tag @s add sm_superworld
# Gear once per player (the tag sm_kit is stored on the player)
execute if entity @s[tag=!sm_kit] run function supermann:kit
# Weapons, separate tag so players who got the kit before the weapons existed also get them
execute if entity @s[tag=!sm_weapons] run function supermann:weapons
# Powers for everyone who has not opted out with the tag sm_nopowers. Also catches respawn after death.
execute if entity @s[tag=!sm_nopowers] run function supermann:powers
# Always exactly 64 rockets, same on/off tag as the powers
execute if entity @s[tag=!sm_nopowers] run function supermann:rockets
# Always exactly 64 arrows
execute if entity @s[tag=!sm_nopowers] run function supermann:arrows
