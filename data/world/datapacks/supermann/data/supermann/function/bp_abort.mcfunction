function supermann:job_forceload_remove with storage supermann:job
execute in minecraft:overworld run forceload remove 2000000 2000000 2000100 2000100
tellraw @a[tag=sm_job] {"text":"[Supermann] Could not load the area, try again.","color":"red"}
tag @a[tag=sm_job] remove sm_job
scoreboard players set #busy sm_tunnel 0
