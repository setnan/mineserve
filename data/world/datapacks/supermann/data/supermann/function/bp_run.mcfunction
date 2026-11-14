# Stands in the middle of the block the player stood on, facing the compass direction, and runs the blueprint with the direction keys.
$execute in $(dim) positioned $(x) $(y) $(z) positioned ~ ~-0.5 ~ rotated $(yaw) 0 run function supermann:bp/$(bp) with storage supermann:job
