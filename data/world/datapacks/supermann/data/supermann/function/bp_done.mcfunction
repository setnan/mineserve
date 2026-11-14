$execute in $(dim) run forceload remove $(fx) $(fz) $(fx2) $(fz2)
execute in minecraft:overworld run forceload remove 2000000 2000000 2000100 2000100
$tellraw @a[tag=sm_job] [{"text":"[Supermann] Done: $(title). ","color":"gold"},{"text":"Undo with /build_undo.","color":"gray"}]
execute unless data storage supermann:undo {ok:1b} run tellraw @a[tag=sm_job] {"text":"[Supermann] Could not take an undo copy of this build.","color":"red"}
tag @a[tag=sm_job] remove sm_job
scoreboard players set #busy sm_tunnel 0
