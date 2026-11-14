# /save [home]: without an argument all players gather at you, "/save home" saves your home.
# Called from commands.yml with {a:"$1"} ($1 is optional and becomes an empty string without an argument).
$data modify storage supermann:home tmp.a set value "$(a)"
execute if data storage supermann:home tmp{a:"home"} run function supermann:home_save
execute unless data storage supermann:home tmp{a:"home"} run tp @a @s
execute unless data storage supermann:home tmp{a:"home"} run tellraw @a {"text":"All players are gathered.","color":"gold"}
