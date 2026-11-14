# Copies the box to the undo store before building (one clone; max_block_modifications is raised in load).
$execute store success storage supermann:undo ok byte 1 run clone from $(dim) $(x1) $(y1) $(z1) $(x2) $(y2) $(z2) to minecraft:overworld 2000000 $(y1) 2000000
