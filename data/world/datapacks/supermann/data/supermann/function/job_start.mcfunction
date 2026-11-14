# Shared start for builds that reach far (tunnel, glass bridge). The area must be loaded for
# setblock/fill to work, so we force-load the strip first, wait until it is loaded (job_wait),
# build (job_run) and release it again (job_done). #job: 1 = tunnel, 2 = glass bridge.
execute if score #busy sm_tunnel matches 1 run return run tellraw @s [{"text":"[Supermann] ","color":"gold"},{"text":"Wait, the previous build is not done yet.","color":"red"}]
execute unless score #n sm_tunnel matches 1.. run return run tellraw @s [{"text":"[Supermann] ","color":"gold"},{"text":"Give a number above 0, e.g. /tunnel 50","color":"red"}]
execute if score #n sm_tunnel matches 2001.. run scoreboard players set #n sm_tunnel 2000
scoreboard players set #busy sm_tunnel 1
execute store result storage supermann:job n int 1 run scoreboard players get #n sm_tunnel
function supermann:dir
# Store the dimension: schedule (job_wait) runs in the overworld, so everything after must name the world
data modify storage supermann:job dim set from entity @s Dimension
# End point
scoreboard players operation #x2 sm_tunnel = #x sm_tunnel
scoreboard players operation #z2 sm_tunnel = #z sm_tunnel
execute if score #yaw sm_tunnel matches 0 run scoreboard players operation #z2 sm_tunnel += #n sm_tunnel
execute if score #yaw sm_tunnel matches 180 run scoreboard players operation #z2 sm_tunnel -= #n sm_tunnel
execute if score #yaw sm_tunnel matches 90 run scoreboard players operation #x2 sm_tunnel -= #n sm_tunnel
execute if score #yaw sm_tunnel matches -90 run scoreboard players operation #x2 sm_tunnel += #n sm_tunnel
execute store result storage supermann:job x2 int 1 run scoreboard players get #x2 sm_tunnel
execute store result storage supermann:job z2 int 1 run scoreboard players get #z2 sm_tunnel
# Forceload area: from min to max with a 3 block margin
scoreboard players operation #fx sm_tunnel = #x sm_tunnel
scoreboard players operation #fx sm_tunnel < #x2 sm_tunnel
scoreboard players remove #fx sm_tunnel 3
scoreboard players operation #fx2 sm_tunnel = #x sm_tunnel
scoreboard players operation #fx2 sm_tunnel > #x2 sm_tunnel
scoreboard players add #fx2 sm_tunnel 3
scoreboard players operation #fz sm_tunnel = #z sm_tunnel
scoreboard players operation #fz sm_tunnel < #z2 sm_tunnel
scoreboard players remove #fz sm_tunnel 3
scoreboard players operation #fz2 sm_tunnel = #z sm_tunnel
scoreboard players operation #fz2 sm_tunnel > #z2 sm_tunnel
scoreboard players add #fz2 sm_tunnel 3
execute store result storage supermann:job fx int 1 run scoreboard players get #fx sm_tunnel
execute store result storage supermann:job fz int 1 run scoreboard players get #fz sm_tunnel
execute store result storage supermann:job fx2 int 1 run scoreboard players get #fx2 sm_tunnel
execute store result storage supermann:job fz2 int 1 run scoreboard players get #fz2 sm_tunnel
function supermann:job_forceload with storage supermann:job
tag @s add sm_job
scoreboard players set #tries sm_tunnel 0
function supermann:job_wait
