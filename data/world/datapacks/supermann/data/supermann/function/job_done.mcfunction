$execute in $(dim) run forceload remove $(fx) $(fz) $(fx2) $(fz2)
$tellraw @a[tag=sm_job] [{"text":"[Supermann] ","color":"gold"},{"text":"Done: $(n) blocks.","color":"gray"}]
tag @a remove sm_job
scoreboard players set #busy sm_tunnel 0
