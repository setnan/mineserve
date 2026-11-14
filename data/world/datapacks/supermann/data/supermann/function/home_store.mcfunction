# Macro: puts the tmp entry under the player's id (the id can be negative, hence the quotes)
$data modify storage supermann:home h."$(id)" set from storage supermann:home tmp
$tellraw @s [{"text":"Home saved: ","color":"gold"},{"text":"$(dim)","color":"yellow"}]
