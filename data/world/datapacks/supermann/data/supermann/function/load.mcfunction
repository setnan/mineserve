# Runs at startup and on /reload. Starts the loop that hands out powers and gear.
tellraw @a [{"text":"[Supermann] ","color":"gold"},{"text":"datapack loaded","color":"gray"}]
scoreboard objectives add sm_rockets dummy
scoreboard objectives add sm_arrows dummy
scoreboard objectives add sm_tunnel dummy
scoreboard players set #ten sm_tunnel 10
scoreboard players set #busy sm_tunnel 0
# The hide-and-seek flag is kept in the scoreboard across restarts; no reset here.
# Migration from the old Norwegian names (flag #gjemsel, team gjemsel): carry the flag over once.
execute if score #gjemsel sm_tunnel matches 1 run scoreboard players set #hideandseek sm_tunnel 1
scoreboard players reset #gjemsel sm_tunnel
team remove gjemsel
execute if score #hideandseek sm_tunnel matches 1 run function supermann:hideandseek_team
# Long tunnels run many commands in one chain (about 20 per block); the default limit is 65536.
# NB: gamerule names are snake_case in 26.x; this one used to be maxCommandChainLength.
gamerule max_command_sequence_length 500000
# The undo copy (clone) and filling a whole build in one command: the default limit is 32768 blocks.
gamerule max_block_modifications 1000000
function supermann:loop
# Locator bar on, unless hide and seek is active
execute unless score #hideandseek sm_tunnel matches 1 run gamerule locator_bar true
