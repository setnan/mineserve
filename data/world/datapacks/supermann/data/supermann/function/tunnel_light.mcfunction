# Light every 10 blocks: a hanging lantern in the roof on the right side (^-1 ^4).
# The block above the lantern must be solid, otherwise it falls and leaves a hole: make it glass.
execute unless block ^-1 ^5 ^ #supermann:solid run setblock ^-1 ^5 ^ minecraft:glass
setblock ^-1 ^4 ^ minecraft:lantern[hanging=true]
