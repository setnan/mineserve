# Compass directions relative to the player, for block states (facing/axis) in house_build.
# F = forward, B = back, L = left, R = right, AX = cross axis, AF = forward axis.
execute if score #yaw sm_tunnel matches 0 run data merge storage supermann:job {F:"south",B:"north",L:"east",R:"west",AX:"x",AF:"z"}
execute if score #yaw sm_tunnel matches 90 run data merge storage supermann:job {F:"west",B:"east",L:"south",R:"north",AX:"z",AF:"x"}
execute if score #yaw sm_tunnel matches 180 run data merge storage supermann:job {F:"north",B:"south",L:"west",R:"east",AX:"x",AF:"z"}
execute if score #yaw sm_tunnel matches -90 run data merge storage supermann:job {F:"east",B:"west",L:"north",R:"south",AX:"z",AF:"x"}
