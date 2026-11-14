scoreboard players set #i sm_tunnel 0
execute if score #job sm_tunnel matches 1 run function supermann:tunnel_run with storage supermann:job
execute if score #job sm_tunnel matches 2 run function supermann:glassbridge_run with storage supermann:job
function supermann:job_done with storage supermann:job
