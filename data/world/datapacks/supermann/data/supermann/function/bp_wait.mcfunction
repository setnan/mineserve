# Waits until the area around the blueprint and the undo store are loaded (forceload takes a few ticks), then builds.
# NB: schedule runs in the overworld without an executor, so everything after this is pinned to a dimension via $(dim).
scoreboard players set #loaded sm_tunnel 0
function supermann:job_check with storage supermann:job
execute in minecraft:overworld unless loaded 2000000 64 2000000 run scoreboard players set #loaded sm_tunnel 0
execute in minecraft:overworld unless loaded 2000100 64 2000100 run scoreboard players set #loaded sm_tunnel 0
scoreboard players add #tries sm_tunnel 1
execute if score #loaded sm_tunnel matches 1 run return run function supermann:bp_go
execute if score #tries sm_tunnel matches 30.. run return run function supermann:bp_abort
schedule function supermann:bp_wait 20t
