# Reads the player's direction and position into the scoreboard and storage supermann:job.
# Yaw snaps to the nearest compass direction: 0 = south (+z), 90 = west (-x), 180 = north (-z), -90 = east (+x).
execute store result score #rot sm_tunnel run data get entity @s Rotation[0]
execute if score #rot sm_tunnel matches -45..44 run scoreboard players set #yaw sm_tunnel 0
execute if score #rot sm_tunnel matches 45..134 run scoreboard players set #yaw sm_tunnel 90
execute if score #rot sm_tunnel matches 135..180 run scoreboard players set #yaw sm_tunnel 180
execute if score #rot sm_tunnel matches -180..-136 run scoreboard players set #yaw sm_tunnel 180
execute if score #rot sm_tunnel matches -135..-46 run scoreboard players set #yaw sm_tunnel -90
execute store result storage supermann:job yaw int 1 run scoreboard players get #yaw sm_tunnel
# Block position of the feet
execute store result storage supermann:job x int 1 run data get entity @s Pos[0]
execute store result storage supermann:job y int 1 run data get entity @s Pos[1]
execute store result storage supermann:job z int 1 run data get entity @s Pos[2]
execute store result score #x sm_tunnel run data get entity @s Pos[0]
execute store result score #z sm_tunnel run data get entity @s Pos[2]
