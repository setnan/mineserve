function supermann:job_forceload_remove with storage supermann:job
tellraw @a[tag=sm_job] [{"text":"[Supermann] ","color":"gold"},{"text":"Could not load the area within 30 seconds. Try a shorter stretch.","color":"red"}]
tag @a remove sm_job
scoreboard players set #busy sm_tunnel 0
