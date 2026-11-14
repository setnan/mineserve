# Always exactly 64 arrows, same logic as rockets.mcfunction
execute store result score @s sm_arrows run clear @s minecraft:arrow 0
execute unless score @s sm_arrows matches 64 run clear @s minecraft:arrow
execute unless score @s sm_arrows matches 64 run give @s minecraft:arrow 64
