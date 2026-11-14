# /build_undo: puts back what stood there before the last build (blueprint or /house). Last build only, once.
execute unless data storage supermann:undo {ok:1b} run return run tellraw @s {"text":"[Supermann] Nothing to undo.","color":"red"}
execute if score #busy sm_tunnel matches 1 run return run tellraw @s {"text":"[Supermann] A build job is running, wait until it is done.","color":"red"}
scoreboard players set #busy sm_tunnel 1
tag @s add sm_job
function supermann:bp_undo_load with storage supermann:undo
execute in minecraft:overworld run forceload add 2000000 2000000 2000100 2000100
scoreboard players set #tries sm_tunnel 0
function supermann:bp_undo_wait
