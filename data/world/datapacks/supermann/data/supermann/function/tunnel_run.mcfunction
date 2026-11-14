# NB: integers without a decimal in positioned are centred automatically (+0.5 in x and z), only y is adjusted here.
# Stands in the middle of the floor block (the one the player stood on), facing the compass direction, and starts one ahead.
$execute in $(dim) positioned $(x) $(y) $(z) positioned ~ ~-0.5 ~ rotated $(yaw) 0 positioned ^ ^ ^1 run function supermann:tunnel_step
