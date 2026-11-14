# NB: integers without a decimal in positioned are centred automatically (+0.5 in x and z), only y is adjusted here.
# Downwards: the first shaft block is right under the feet
$execute positioned $(x) $(y) $(z) positioned ~ ~-0.5 ~ rotated $(yaw) 0 run function supermann:staircase_step
