# Count rockets (clear with count 0 removes nothing, it only counts). Anything but exactly 64: swap for a full stack.
execute store result score @s sm_rockets run clear @s minecraft:firework_rocket 0
execute unless score @s sm_rockets matches 64 run clear @s minecraft:firework_rocket
execute unless score @s sm_rockets matches 64 run give @s minecraft:firework_rocket[fireworks={flight_duration:3}] 64
