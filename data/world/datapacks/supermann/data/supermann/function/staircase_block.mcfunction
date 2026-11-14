# One shaft block. Runs in the middle of the block, facing the compass direction: ^ ^ ^1 is the wall in front, ^ ^ ^-1 the wall behind.
# Bedrock stops everything
execute if block ^ ^ ^ minecraft:bedrock run scoreboard players set #stop sm_tunnel 1
execute if block ^ ^ ^ minecraft:bedrock run scoreboard players set #n sm_tunnel 0
execute if score #stop sm_tunnel matches 1 run return 0
scoreboard players add #i sm_tunnel 1
scoreboard players remove #n sm_tunnel 1
# Water and lava on the sides become glass
execute if block ^1 ^ ^ #supermann:liquid run setblock ^1 ^ ^ minecraft:glass
execute if block ^-1 ^ ^ #supermann:liquid run setblock ^-1 ^ ^ minecraft:glass
execute if block ^ ^ ^1 #supermann:liquid run setblock ^ ^ ^1 minecraft:glass
execute if block ^ ^ ^-1 #supermann:liquid run setblock ^ ^ ^-1 minecraft:glass
# The wall the ladder hangs on must be solid; if it is air (cave, open air) it becomes glass
execute if block ^ ^ ^1 #minecraft:air run setblock ^ ^ ^1 minecraft:glass
# The ladder, attached to the wall in front
execute if score #yaw sm_tunnel matches 0 run setblock ^ ^ ^ minecraft:ladder[facing=north]
execute if score #yaw sm_tunnel matches 90 run setblock ^ ^ ^ minecraft:ladder[facing=east]
execute if score #yaw sm_tunnel matches 180 run setblock ^ ^ ^ minecraft:ladder[facing=south]
execute if score #yaw sm_tunnel matches -90 run setblock ^ ^ ^ minecraft:ladder[facing=west]
# Every 10 blocks: a lantern in the wall behind (needs something to stand on)
scoreboard players operation #m sm_tunnel = #i sm_tunnel
scoreboard players operation #m sm_tunnel %= #ten sm_tunnel
execute if score #m sm_tunnel matches 0 if block ^ ^-1 ^-1 #minecraft:air run setblock ^ ^-1 ^-1 minecraft:glass
execute if score #m sm_tunnel matches 0 run setblock ^ ^ ^-1 minecraft:lantern
