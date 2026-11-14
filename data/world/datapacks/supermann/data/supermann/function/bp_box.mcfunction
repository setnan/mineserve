# Computes the box around the build: absolute corners for undo (supermann:undo) and forceload (supermann:job). Generated.
$scoreboard players set #L sm_tunnel $(L)
$scoreboard players set #H sm_tunnel $(H)
$scoreboard players set #F1 sm_tunnel $(F1)
$scoreboard players set #F2 sm_tunnel $(F2)
execute store result score #y1 sm_tunnel run data get storage supermann:job y
scoreboard players remove #y1 sm_tunnel 1
scoreboard players operation #y2 sm_tunnel = #y1 sm_tunnel
scoreboard players operation #y2 sm_tunnel += #H sm_tunnel
execute if score #yaw sm_tunnel matches 180 run scoreboard players operation #x1 sm_tunnel = #x sm_tunnel
execute if score #yaw sm_tunnel matches 180 run scoreboard players operation #x1 sm_tunnel -= #L sm_tunnel
execute if score #yaw sm_tunnel matches 180 run scoreboard players operation #x2 sm_tunnel = #x sm_tunnel
execute if score #yaw sm_tunnel matches 180 run scoreboard players operation #x2 sm_tunnel += #L sm_tunnel
execute if score #yaw sm_tunnel matches 180 run scoreboard players operation #z1 sm_tunnel = #z sm_tunnel
execute if score #yaw sm_tunnel matches 180 run scoreboard players operation #z1 sm_tunnel -= #F2 sm_tunnel
execute if score #yaw sm_tunnel matches 180 run scoreboard players operation #z2 sm_tunnel = #z sm_tunnel
execute if score #yaw sm_tunnel matches 180 run scoreboard players operation #z2 sm_tunnel -= #F1 sm_tunnel
execute if score #yaw sm_tunnel matches 0 run scoreboard players operation #x1 sm_tunnel = #x sm_tunnel
execute if score #yaw sm_tunnel matches 0 run scoreboard players operation #x1 sm_tunnel -= #L sm_tunnel
execute if score #yaw sm_tunnel matches 0 run scoreboard players operation #x2 sm_tunnel = #x sm_tunnel
execute if score #yaw sm_tunnel matches 0 run scoreboard players operation #x2 sm_tunnel += #L sm_tunnel
execute if score #yaw sm_tunnel matches 0 run scoreboard players operation #z1 sm_tunnel = #z sm_tunnel
execute if score #yaw sm_tunnel matches 0 run scoreboard players operation #z1 sm_tunnel += #F1 sm_tunnel
execute if score #yaw sm_tunnel matches 0 run scoreboard players operation #z2 sm_tunnel = #z sm_tunnel
execute if score #yaw sm_tunnel matches 0 run scoreboard players operation #z2 sm_tunnel += #F2 sm_tunnel
execute if score #yaw sm_tunnel matches 90 run scoreboard players operation #x1 sm_tunnel = #x sm_tunnel
execute if score #yaw sm_tunnel matches 90 run scoreboard players operation #x1 sm_tunnel -= #F2 sm_tunnel
execute if score #yaw sm_tunnel matches 90 run scoreboard players operation #x2 sm_tunnel = #x sm_tunnel
execute if score #yaw sm_tunnel matches 90 run scoreboard players operation #x2 sm_tunnel -= #F1 sm_tunnel
execute if score #yaw sm_tunnel matches 90 run scoreboard players operation #z1 sm_tunnel = #z sm_tunnel
execute if score #yaw sm_tunnel matches 90 run scoreboard players operation #z1 sm_tunnel -= #L sm_tunnel
execute if score #yaw sm_tunnel matches 90 run scoreboard players operation #z2 sm_tunnel = #z sm_tunnel
execute if score #yaw sm_tunnel matches 90 run scoreboard players operation #z2 sm_tunnel += #L sm_tunnel
execute if score #yaw sm_tunnel matches -90 run scoreboard players operation #x1 sm_tunnel = #x sm_tunnel
execute if score #yaw sm_tunnel matches -90 run scoreboard players operation #x1 sm_tunnel += #F1 sm_tunnel
execute if score #yaw sm_tunnel matches -90 run scoreboard players operation #x2 sm_tunnel = #x sm_tunnel
execute if score #yaw sm_tunnel matches -90 run scoreboard players operation #x2 sm_tunnel += #F2 sm_tunnel
execute if score #yaw sm_tunnel matches -90 run scoreboard players operation #z1 sm_tunnel = #z sm_tunnel
execute if score #yaw sm_tunnel matches -90 run scoreboard players operation #z1 sm_tunnel -= #L sm_tunnel
execute if score #yaw sm_tunnel matches -90 run scoreboard players operation #z2 sm_tunnel = #z sm_tunnel
execute if score #yaw sm_tunnel matches -90 run scoreboard players operation #z2 sm_tunnel += #L sm_tunnel
execute store result storage supermann:undo x1 int 1 run scoreboard players get #x1 sm_tunnel
execute store result storage supermann:undo y1 int 1 run scoreboard players get #y1 sm_tunnel
execute store result storage supermann:undo z1 int 1 run scoreboard players get #z1 sm_tunnel
execute store result storage supermann:undo x2 int 1 run scoreboard players get #x2 sm_tunnel
execute store result storage supermann:undo y2 int 1 run scoreboard players get #y2 sm_tunnel
execute store result storage supermann:undo z2 int 1 run scoreboard players get #z2 sm_tunnel
scoreboard players operation #sx2 sm_tunnel = #x2 sm_tunnel
scoreboard players operation #sx2 sm_tunnel -= #x1 sm_tunnel
scoreboard players add #sx2 sm_tunnel 2000000
scoreboard players operation #sz2 sm_tunnel = #z2 sm_tunnel
scoreboard players operation #sz2 sm_tunnel -= #z1 sm_tunnel
scoreboard players add #sz2 sm_tunnel 2000000
execute store result storage supermann:undo sx2 int 1 run scoreboard players get #sx2 sm_tunnel
execute store result storage supermann:undo sz2 int 1 run scoreboard players get #sz2 sm_tunnel
# Forceload area for the job: the box + 2, and the opposite corner for job_check
scoreboard players operation #fx sm_tunnel = #x1 sm_tunnel
scoreboard players remove #fx sm_tunnel 2
scoreboard players operation #fz sm_tunnel = #z1 sm_tunnel
scoreboard players remove #fz sm_tunnel 2
scoreboard players operation #fx2 sm_tunnel = #x2 sm_tunnel
scoreboard players add #fx2 sm_tunnel 2
scoreboard players operation #fz2 sm_tunnel = #z2 sm_tunnel
scoreboard players add #fz2 sm_tunnel 2
execute store result storage supermann:job fx int 1 run scoreboard players get #fx sm_tunnel
execute store result storage supermann:job fz int 1 run scoreboard players get #fz sm_tunnel
execute store result storage supermann:job fx2 int 1 run scoreboard players get #fx2 sm_tunnel
execute store result storage supermann:job fz2 int 1 run scoreboard players get #fz2 sm_tunnel
execute store result storage supermann:job x2 int 1 run scoreboard players get #fx2 sm_tunnel
execute store result storage supermann:job z2 int 1 run scoreboard players get #fz2 sm_tunnel
