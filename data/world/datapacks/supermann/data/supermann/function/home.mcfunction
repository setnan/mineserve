# /home: teleports you to your home (also in another world). Not set = nothing happens.
execute store result storage supermann:home tmp.id int 1 run data get entity @s UUID[0]
function supermann:home_go with storage supermann:home tmp
