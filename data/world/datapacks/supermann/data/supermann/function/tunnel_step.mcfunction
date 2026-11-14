# One step of the tunnel. Runs at floor level (same height as the block the player stands on),
# with the rotation locked to a compass direction, so ^ ^ ^1 is straight ahead and ^-1 is to the right.
scoreboard players add #i sm_tunnel 1
scoreboard players remove #n sm_tunnel 1
# Floor: this block and the one to the right. #floor is chosen in tunnel/icetunnel (0 = stone_bricks, 1 = packed_ice)
execute unless score #floor sm_tunnel matches 1 run setblock ^ ^ ^ minecraft:stone_bricks
execute unless score #floor sm_tunnel matches 1 run setblock ^-1 ^ ^ minecraft:stone_bricks
execute if score #floor sm_tunnel matches 1 run setblock ^ ^ ^ minecraft:packed_ice
execute if score #floor sm_tunnel matches 1 run setblock ^-1 ^ ^ minecraft:packed_ice
# Three blocks of air above the floor
fill ^ ^1 ^ ^-1 ^3 ^ minecraft:air
# Shell: left wall (^1), right wall (^-2) and roof (^4). Everything that is not solid (#supermann:solid: rock, dirt,
# ore, wood …) becomes glass: water, lava, air, gravel, sand, plants, kelp, coral, waterlogged blocks.
execute unless block ^1 ^1 ^ #supermann:solid run setblock ^1 ^1 ^ minecraft:glass
execute unless block ^1 ^2 ^ #supermann:solid run setblock ^1 ^2 ^ minecraft:glass
execute unless block ^1 ^3 ^ #supermann:solid run setblock ^1 ^3 ^ minecraft:glass
execute unless block ^-2 ^1 ^ #supermann:solid run setblock ^-2 ^1 ^ minecraft:glass
execute unless block ^-2 ^2 ^ #supermann:solid run setblock ^-2 ^2 ^ minecraft:glass
execute unless block ^-2 ^3 ^ #supermann:solid run setblock ^-2 ^3 ^ minecraft:glass
execute unless block ^ ^4 ^ #supermann:solid run setblock ^ ^4 ^ minecraft:glass
execute unless block ^-1 ^4 ^ #supermann:solid run setblock ^-1 ^4 ^ minecraft:glass
# The end: same rule for the wall in front of the last block
execute if score #n sm_tunnel matches 0 run function supermann:tunnel_end
# Every 10 blocks: light
scoreboard players operation #m sm_tunnel = #i sm_tunnel
scoreboard players operation #m sm_tunnel %= #ten sm_tunnel
execute if score #m sm_tunnel matches 0 run function supermann:tunnel_light
# Next step, one block ahead
execute if score #n sm_tunnel matches 1.. positioned ^ ^ ^1 run function supermann:tunnel_step
