effect give @s minecraft:speed infinite 2 true
effect give @s minecraft:strength infinite 4 true
effect give @s minecraft:resistance infinite 4 true
effect give @s minecraft:regeneration infinite 4 true
effect give @s minecraft:fire_resistance infinite 0 true
effect give @s minecraft:night_vision infinite 0 true
effect give @s minecraft:water_breathing infinite 0 true
effect give @s minecraft:haste infinite 4 true
# Jump boost can be turned off per player: /tag Name add sm_nojump
execute if entity @s[tag=!sm_nojump] run effect give @s minecraft:jump_boost infinite 4 true
