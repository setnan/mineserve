# NB: integers without a decimal in positioned are centred automatically (+0.5 in x and z), only y is adjusted here.
$execute in $(dim) positioned $(x) $(y) $(z) positioned ~ ~-0.5 ~ rotated $(yaw) 0 run fill ^ ^ ^1 ^ ^ ^$(n) minecraft:glass
