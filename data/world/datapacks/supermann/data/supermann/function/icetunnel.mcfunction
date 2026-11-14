# /icetunnel N: like /tunnel, but the floor is packed_ice (slippery, fast to walk on). Everything else is the same: tunnel_step builds.
$scoreboard players set #n sm_tunnel $(n)
scoreboard players set #floor sm_tunnel 1
scoreboard players set #job sm_tunnel 1
function supermann:job_start
