# /tunnel N: build a tunnel or bridge N blocks straight ahead in the compass direction you face.
# Called from the alias in data/commands.yml: function supermann:tunnel {n:N}
# Floor: the block in front of the one you stand on + the one to its right, in stone_bricks. Three blocks of air above.
# Every 10 blocks: a lantern on the right side (tunnel_light.mcfunction). The building itself happens in tunnel_step.
$scoreboard players set #n sm_tunnel $(n)
# Floor type: 0 = stone_bricks (/tunnel), 1 = packed_ice (/icetunnel)
scoreboard players set #floor sm_tunnel 0
scoreboard players set #job sm_tunnel 1
function supermann:job_start
