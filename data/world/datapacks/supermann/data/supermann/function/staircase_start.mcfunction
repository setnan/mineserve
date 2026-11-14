execute unless score #n sm_tunnel matches 1.. run return run tellraw @s [{"text":"[Supermann] ","color":"gold"},{"text":"Give a number above 0, e.g. /staircase 30","color":"red"}]
execute if score #n sm_tunnel matches 401.. run scoreboard players set #n sm_tunnel 400
scoreboard players set #i sm_tunnel 0
scoreboard players set #stop sm_tunnel 0
function supermann:dir
execute if score #job sm_tunnel matches 3 run function supermann:staircase_run with storage supermann:job
execute if score #job sm_tunnel matches 4 run function supermann:staircase_up_run with storage supermann:job
execute if score #stop sm_tunnel matches 0 run tellraw @s [{"text":"[Supermann] ","color":"gold"},{"text":"Ladder done: ","color":"gray"},{"score":{"name":"#i","objective":"sm_tunnel"},"color":"gray"},{"text":" blocks.","color":"gray"}]
execute if score #stop sm_tunnel matches 1 run tellraw @s [{"text":"[Supermann] ","color":"gold"},{"text":"Ladder stopped by bedrock after ","color":"gray"},{"score":{"name":"#i","objective":"sm_tunnel"},"color":"gray"},{"text":" blocks.","color":"gray"}]
