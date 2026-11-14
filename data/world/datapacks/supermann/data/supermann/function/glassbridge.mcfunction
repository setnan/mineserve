# /glassbridge N: N glass blocks at floor level straight ahead, one wide. Replaces whatever is there.
$scoreboard players set #n sm_tunnel $(n)
scoreboard players set #job sm_tunnel 2
function supermann:job_start
