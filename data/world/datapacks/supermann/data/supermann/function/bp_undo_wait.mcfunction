scoreboard players set #loaded sm_tunnel 0
function supermann:bp_undo_check with storage supermann:undo
execute in minecraft:overworld unless loaded 2000000 64 2000000 run scoreboard players set #loaded sm_tunnel 0
execute in minecraft:overworld unless loaded 2000100 64 2000100 run scoreboard players set #loaded sm_tunnel 0
scoreboard players add #tries sm_tunnel 1
execute if score #loaded sm_tunnel matches 1 run return run function supermann:bp_undo_go with storage supermann:undo
execute if score #tries sm_tunnel matches 30.. run return run function supermann:bp_undo_abort with storage supermann:undo
schedule function supermann:bp_undo_wait 20t
