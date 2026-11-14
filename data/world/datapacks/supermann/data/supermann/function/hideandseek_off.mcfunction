# /hideandseekoff: back to normal
scoreboard players set #hideandseek sm_tunnel 0
team remove hideandseek
gamerule locator_bar true
tellraw @a [{"text":"[Supermann] ","color":"gold"},{"text":"Hide and seek off: name tags and the locator bar are visible again.","color":"gray"}]
