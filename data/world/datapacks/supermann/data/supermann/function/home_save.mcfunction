# Saves where you stand (position, direction and world) as your home. One home per player,
# the key is the first int of the UUID. Stored in storage supermann:home and survives restarts.
execute store result storage supermann:home tmp.id int 1 run data get entity @s UUID[0]
data modify storage supermann:home tmp.x set from entity @s Pos[0]
data modify storage supermann:home tmp.y set from entity @s Pos[1]
data modify storage supermann:home tmp.z set from entity @s Pos[2]
data modify storage supermann:home tmp.yaw set from entity @s Rotation[0]
data modify storage supermann:home tmp.pitch set from entity @s Rotation[1]
data modify storage supermann:home tmp.dim set from entity @s Dimension
function supermann:home_store with storage supermann:home tmp
