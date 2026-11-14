function supermann:staircase_block
# The top: water or lava right above the last block becomes glass
execute if score #n sm_tunnel matches 0 if block ^ ^1 ^ #supermann:liquid run setblock ^ ^1 ^ minecraft:glass
execute if score #n sm_tunnel matches 1.. positioned ~ ~1 ~ run function supermann:staircase_up_step
