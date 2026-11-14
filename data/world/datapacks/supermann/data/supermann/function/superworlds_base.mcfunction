# Supermann worlds: the main world (overworld, nether, end). Extra worlds come from superworlds.mcfunction, which
# ./mc.sh sync-aliases writes (not in git). Both run via the tag #supermann:superworlds.
scoreboard players set #super sm_tunnel 0
execute if dimension minecraft:overworld run scoreboard players set #super sm_tunnel 1
execute if dimension minecraft:the_nether run scoreboard players set #super sm_tunnel 1
execute if dimension minecraft:the_end run scoreboard players set #super sm_tunnel 1
