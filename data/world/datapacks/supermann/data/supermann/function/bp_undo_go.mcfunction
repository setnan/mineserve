$execute store success score #ok sm_tunnel run clone from minecraft:overworld 2000000 $(y1) 2000000 $(sx2) $(y2) $(sz2) to $(dim) $(x1) $(y1) $(z1)
$execute in $(dim) run forceload remove $(x1) $(z1) $(x2) $(z2)
execute in minecraft:overworld run forceload remove 2000000 2000000 2000100 2000100
execute if score #ok sm_tunnel matches 1 run data modify storage supermann:undo ok set value 0b
$execute if score #ok sm_tunnel matches 1 run tellraw @a[tag=sm_job] {"text":"[Supermann] Undone: $(title) is removed and the old blocks are back.","color":"gold"}
execute unless score #ok sm_tunnel matches 1 run tellraw @a[tag=sm_job] {"text":"[Supermann] Undo failed (clone). Try again.","color":"red"}
tag @a[tag=sm_job] remove sm_job
scoreboard players set #busy sm_tunnel 0
