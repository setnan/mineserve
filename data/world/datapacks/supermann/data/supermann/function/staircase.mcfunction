# /staircase N: 1×1 shaft N blocks straight down from under your feet, with a ladder on the wall in front of you
# and a lantern in the wall behind you every 10 blocks. Stops at bedrock. Water and lava around become glass.
$scoreboard players set #n sm_tunnel $(n)
scoreboard players set #job sm_tunnel 3
function supermann:staircase_start
