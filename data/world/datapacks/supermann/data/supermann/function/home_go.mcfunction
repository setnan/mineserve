# Macro: only if there is a saved entry for the player
$execute if data storage supermann:home h."$(id)" run function supermann:home_tp with storage supermann:home h."$(id)"
