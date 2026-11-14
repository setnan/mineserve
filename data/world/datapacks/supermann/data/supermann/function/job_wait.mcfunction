# Waits until both ends of the strip are loaded, checks every second, gives up after 30 tries.
scoreboard players set #loaded sm_tunnel 0
function supermann:job_check with storage supermann:job
scoreboard players add #tries sm_tunnel 1
execute if score #loaded sm_tunnel matches 1 run return run function supermann:job_run
execute if score #tries sm_tunnel matches 30.. run return run function supermann:job_abort
schedule function supermann:job_wait 20t
