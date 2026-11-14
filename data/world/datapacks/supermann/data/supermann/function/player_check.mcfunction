# Runs as each player from loop: Supermann in Supermann worlds, otherwise the powers are removed once.
function #supermann:superworlds
execute if score #super sm_tunnel matches 1 run function supermann:player
execute if score #super sm_tunnel matches 0 if entity @s[tag=sm_superworld] run function supermann:leave
