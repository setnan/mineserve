# Local coordinates: ^side ^up ^forward. ^+ = left, ^- = right. ^0 in height = floor level (the block under the feet).
# Front wall at ^2, back wall at ^10, roof from ^1 to ^11 (overhang).

# Clear space
fill ^-5 ^1 ^1 ^5 ^10 ^11 minecraft:air

# Foundation, floor and path
fill ^-4 ^-2 ^2 ^4 ^-1 ^10 minecraft:cobblestone replace minecraft:air
fill ^-4 ^0 ^2 ^4 ^0 ^10 minecraft:cobblestone
fill ^-3 ^0 ^3 ^3 ^0 ^9 minecraft:spruce_planks
fill ^-1 ^0 ^9 ^1 ^0 ^9 minecraft:stone_bricks
fill ^-1 ^0 ^0 ^1 ^0 ^1 minecraft:dirt_path replace minecraft:grass_block

# Walls
fill ^-4 ^1 ^2 ^4 ^3 ^2 minecraft:oak_planks
fill ^-4 ^1 ^10 ^4 ^3 ^10 minecraft:oak_planks
fill ^4 ^1 ^2 ^4 ^3 ^10 minecraft:oak_planks
fill ^-4 ^1 ^2 ^-4 ^3 ^10 minecraft:oak_planks

# Top beams and corner posts in spruce
$fill ^-4 ^3 ^2 ^4 ^3 ^2 minecraft:spruce_log[axis=$(AX)]
$fill ^-4 ^3 ^10 ^4 ^3 ^10 minecraft:spruce_log[axis=$(AX)]
$fill ^4 ^3 ^2 ^4 ^3 ^10 minecraft:spruce_log[axis=$(AF)]
$fill ^-4 ^3 ^2 ^-4 ^3 ^10 minecraft:spruce_log[axis=$(AF)]
fill ^4 ^1 ^2 ^4 ^3 ^2 minecraft:spruce_log[axis=y]
fill ^-4 ^1 ^2 ^-4 ^3 ^2 minecraft:spruce_log[axis=y]
fill ^4 ^1 ^10 ^4 ^3 ^10 minecraft:spruce_log[axis=y]
fill ^-4 ^1 ^10 ^-4 ^3 ^10 minecraft:spruce_log[axis=y]

# Windows
fill ^2 ^2 ^2 ^3 ^2 ^2 minecraft:glass_pane
fill ^-3 ^2 ^2 ^-2 ^2 ^2 minecraft:glass_pane
fill ^4 ^2 ^6 ^4 ^2 ^7 minecraft:glass_pane
fill ^-4 ^2 ^6 ^-4 ^2 ^7 minecraft:glass_pane

# Door
$setblock ^ ^1 ^2 minecraft:oak_door[facing=$(F),half=lower]
$setblock ^ ^2 ^2 minecraft:oak_door[facing=$(F),half=upper]

# Roof: fill under the stairs, ceiling, stair blocks, gables, ridge
fill ^4 ^4 ^1 ^4 ^4 ^11 minecraft:dark_oak_planks
fill ^-4 ^4 ^1 ^-4 ^4 ^11 minecraft:dark_oak_planks
fill ^3 ^5 ^1 ^3 ^5 ^11 minecraft:dark_oak_planks
fill ^-3 ^5 ^1 ^-3 ^5 ^11 minecraft:dark_oak_planks
fill ^2 ^6 ^1 ^2 ^6 ^11 minecraft:dark_oak_planks
fill ^-2 ^6 ^1 ^-2 ^6 ^11 minecraft:dark_oak_planks
fill ^1 ^7 ^1 ^1 ^7 ^11 minecraft:dark_oak_planks
fill ^-1 ^7 ^1 ^-1 ^7 ^11 minecraft:dark_oak_planks
fill ^0 ^8 ^1 ^0 ^8 ^11 minecraft:dark_oak_planks
fill ^-4 ^4 ^2 ^4 ^4 ^10 minecraft:spruce_planks
$fill ^5 ^4 ^1 ^5 ^4 ^11 minecraft:dark_oak_stairs[facing=$(R)]
$fill ^-5 ^4 ^1 ^-5 ^4 ^11 minecraft:dark_oak_stairs[facing=$(L)]
$fill ^4 ^5 ^1 ^4 ^5 ^11 minecraft:dark_oak_stairs[facing=$(R)]
$fill ^-4 ^5 ^1 ^-4 ^5 ^11 minecraft:dark_oak_stairs[facing=$(L)]
$fill ^3 ^6 ^1 ^3 ^6 ^11 minecraft:dark_oak_stairs[facing=$(R)]
$fill ^-3 ^6 ^1 ^-3 ^6 ^11 minecraft:dark_oak_stairs[facing=$(L)]
$fill ^2 ^7 ^1 ^2 ^7 ^11 minecraft:dark_oak_stairs[facing=$(R)]
$fill ^-2 ^7 ^1 ^-2 ^7 ^11 minecraft:dark_oak_stairs[facing=$(L)]
$fill ^1 ^8 ^1 ^1 ^8 ^11 minecraft:dark_oak_stairs[facing=$(R)]
$fill ^-1 ^8 ^1 ^-1 ^8 ^11 minecraft:dark_oak_stairs[facing=$(L)]
fill ^0 ^9 ^1 ^0 ^9 ^11 minecraft:dark_oak_slab[type=bottom]
fill ^-3 ^5 ^2 ^3 ^5 ^2 minecraft:oak_planks
fill ^-2 ^6 ^2 ^2 ^6 ^2 minecraft:oak_planks
fill ^-1 ^7 ^2 ^1 ^7 ^2 minecraft:oak_planks
setblock ^ ^8 ^2 minecraft:oak_planks
fill ^-3 ^5 ^10 ^3 ^5 ^10 minecraft:oak_planks
fill ^-2 ^6 ^10 ^2 ^6 ^10 minecraft:oak_planks
fill ^-1 ^7 ^10 ^1 ^7 ^10 minecraft:oak_planks
setblock ^ ^8 ^10 minecraft:oak_planks

# Fireplace in the back wall with a chimney
fill ^-1 ^1 ^10 ^1 ^3 ^10 minecraft:bricks
fill ^-1 ^1 ^11 ^1 ^3 ^11 minecraft:bricks
fill ^0 ^4 ^11 ^0 ^10 ^11 minecraft:bricks
setblock ^ ^2 ^10 minecraft:air
$setblock ^ ^1 ^10 minecraft:campfire[lit=true,facing=$(F)]

# Furniture: chest and bookshelf on the left, kitchen on the right, flowers by the door
$setblock ^3 ^1 ^3 minecraft:chest[facing=$(R)]
fill ^3 ^1 ^4 ^3 ^2 ^5 minecraft:bookshelf
setblock ^-3 ^1 ^3 minecraft:barrel[facing=up]
setblock ^-3 ^1 ^4 minecraft:crafting_table
$setblock ^-3 ^1 ^5 minecraft:furnace[facing=$(L)]
setblock ^2 ^1 ^3 minecraft:potted_oxeye_daisy
setblock ^-2 ^1 ^3 minecraft:potted_cornflower

# Dining table with four chairs
fill ^0 ^1 ^5 ^0 ^1 ^6 minecraft:oak_fence
fill ^0 ^2 ^5 ^0 ^2 ^6 minecraft:white_carpet
$fill ^1 ^1 ^5 ^1 ^1 ^6 minecraft:oak_stairs[facing=$(L)]
$fill ^-1 ^1 ^5 ^-1 ^1 ^6 minecraft:oak_stairs[facing=$(R)]

# Rug and two armchairs in front of the fireplace
fill ^-1 ^1 ^7 ^1 ^1 ^7 minecraft:red_carpet
setblock ^ ^1 ^8 minecraft:red_carpet
$setblock ^1 ^1 ^8 minecraft:oak_stairs[facing=$(B)]
$setblock ^-1 ^1 ^8 minecraft:oak_stairs[facing=$(B)]

# Two beds at the back along the walls
$setblock ^3 ^1 ^8 minecraft:red_bed[facing=$(F),part=foot]
$setblock ^3 ^1 ^9 minecraft:red_bed[facing=$(F),part=head]
$setblock ^-3 ^1 ^8 minecraft:blue_bed[facing=$(F),part=foot]
$setblock ^-3 ^1 ^9 minecraft:blue_bed[facing=$(F),part=head]

# Light: lanterns in the ceiling and lamp posts at the entrance
setblock ^2 ^3 ^5 minecraft:lantern[hanging=true]
setblock ^-2 ^3 ^5 minecraft:lantern[hanging=true]
setblock ^2 ^3 ^8 minecraft:lantern[hanging=true]
setblock ^-2 ^3 ^8 minecraft:lantern[hanging=true]
setblock ^2 ^1 ^1 minecraft:oak_fence
setblock ^2 ^2 ^1 minecraft:lantern
setblock ^-2 ^1 ^1 minecraft:oak_fence
setblock ^-2 ^2 ^1 minecraft:lantern
