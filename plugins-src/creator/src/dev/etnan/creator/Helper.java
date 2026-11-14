package dev.etnan.creator;

import com.destroystokyo.paper.entity.ai.VanillaGoal;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.entity.TeleportFlag;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.Furnace;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Tameable;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.PigZombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTables;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.StructureSearchResult;
import org.bukkit.util.Vector;

/**
 * A personal helper: a figure in the player model with any player's skin that follows its owner, fights for them
 * (very strong), never dies, lies down when the owner sleeps, and stays or follows when you right-click it. While it
 * waits it puts its weapon away and keeps looking at you. Every player can have one. Only the owner sees it (and its particles and sounds); above it is just its name, like
 * the name tag of a player.
 *
 * It also carries things (a bag of 27 slots: sneak + right-click), picks up what lies around it, wears the armour and
 * weapon you right-click it with, guards a spot, mines the vein or tree you point at, harvests and replants a field,
 * fishes, and puts torches where it is dark. When you die it catches your things, so you wake up with everything. It heals you when you are badly hurt, shears and feeds animals while
 * farming, and empties its bag into chests by itself (next to it, or else at home). Say its name in chat and it comes to you; its name plus a few words is an order
 * ("Bob wait here") or a question that the chat bridge answers ("Bob how do I make a piston?"). Only you see that chat.
 *
 * It has a bit of a personality. With nothing to do it picks you a flower, pets an animal, warms itself at a campfire,
 * watches the sunset or jumps in the rain. It cheers when you find diamonds, gasps when you fall, admires a big build,
 * and warns you in chat ("Watch out, …") when monsters or lava are close. Helpers that meet are friends and tease each
 * other a little. Asked what it thinks of your house it answers from what actually stands around you.
 *
 * When a creeper blows a hole within 32 blocks of home, it puts the blocks back.
 *
 * Under the hood an invisible tamed wolf does the walking and fighting; a Mannequin with the skin is moved onto the
 * wolf every tick. The wolf's own follow-the-owner goal is removed, this class steers it instead. Both are temporary
 * entities: the helper leaves when the owner logs out and comes back when they join. It works in every world,
 * also the ones in MC_RESTRICTED_WORLDS. Settings, bag and gear per player are kept in plugins/Creator/helpers.yml.
 *
 * /helper                       summon (or show help when it is already here)
 * /helper follow|stay|come      follow you, wait here, teleport to you
 * /helper guard                 stay around this spot and fight what comes near
 * /helper passive|fight         stop / start fighting
 * /helper mine                  mine the block you look at and the rest of its vein or tree
 * /helper farm                  harvest and replant ripe crops around where it stands, until you say stop
 * /helper fish                  fish in the nearest water until you say stop or its bag is full
 * /helper torch                 torches where it is dark, on/off
 * /helper light                 carries a light with it, on/off
 * /helper bridge                puts blocks under your feet as you walk out over a gap, and steps when you look up, on/off
 * /helper sethome | home        remember this place as home | take you both home
 * /helper unload                take its bag, and what is in the chests next to it, home and put it in the chests
 *                               there (it empties its bag at home by itself when the bag is full)
 * /helper fetch <item> [n]      bring you items from its bag, the chests next to it or the chests at home
 * /helper find <thing>          point to the nearest ore/block (20 blocks) or structure (village, stronghold …)
 * /helper ride                  carry you on its shoulders: steer with the movement keys, sneak to get off
 * /helper show                  let the other players see it too, on/off
 * /helper big | small | normal  the wand's size spells without a wand: 1.5 times bigger or smaller (¼ to 3 times).
 *                               Every step away from normal, either way, makes it stronger and faster (you too while
 *                               merged). At the smallest size monsters do not notice it, or you while merged
 * /helper attack                send it at the mob you are looking at (up to 48 blocks away)
 * /helper join <player>         work together with that player's helper: the same vein, field, water or guard post
 * /helper craft <item> [n]      make it from what is in its bag, the chests here or at home, and your own inventory
 * /helper smelt [item]          load the furnaces here or at home from its bag (ores and raw food, or the item you
 *                               name; fuel: coal, charcoal, blaze rods, lava) and bring back what is done
 * /helper remember <name>       remember this place; /helper goto <name> takes you both there, /helper places
 *                               lists them, /helper forget <name> forgets one
 * /helper watch                 on/off: when you log out it stays and guards home as long as someone is online
 * /helper tidy                  gather like things in the same chest at home (it also does this by itself now and then
 *                               when it is near home; it never puts up signs)
 * /helper repair                mend what you hold and wear, for one ingot, diamond, plank … each from its bag or yours
 * /helper collect <place>       go to a place it remembers and bring back what is in the chests there
 * /helper trade <item> [n]      buy that from a villager within 16 blocks, paying from its bag and your inventory
 * /helper give <player>         carry what you hold in your hand to another player
 * /helper routine <when> <orders>   a standing order: when = morning, noon, evening, night or "every <minutes>";
 *                               orders = what you would say to it, several with "and". /helper routines lists them,
 *                               /helper routine remove <number> removes one, /helper routine clear removes all
 * /helper scout                 run 100 blocks the way you look and tell you what is there
 * /helper wayback               show the way back out along the path you walked in (sparks, for a minute and a half)
 * /helper style <sword|bow|shield>   how it fights: up close, with arrows from where it stands, or drawing the
 *                               monsters that are after you onto itself
 * /helper hide | tag            games: hide and seek (it hides within 30 blocks and says warmer/colder), tag
 * /helper treasure | hint       it buries a small treasure 18 to 40 blocks away and gives you clues; hint repeats the clue
 * /helper callme <name>         what it calls you (it may also come up with a nickname itself in conversation)
 * /helper dance                 a little dance (it waves and dances when you say hi)
 * /helper help <player>         look after another player (follow, fight for and heal them) until you call it back.
 *                               A helper never hurts players
 * /helper merge | split         merge: the helper goes into you. Nothing hurts you, you hit as hard as it does, monsters
 *                               next to you are struck down, and its jobs (mine, farm, fish …) are done by you on
 *                               autopilot until you press a movement key or swing your arm. You get its size, and you
 *                               glow and light up the dark, and you can fly (double-tap jump, like in creative;
 *                               sprint forward in the air to glide fast where you look, like with an elytra).
 *                               split: it steps out again
 * /helper pickup                picking up items, on/off
 * /helper bag                   open its bag (also: sneak + right-click it)
 * /helper unequip               get back the gear you gave it (give: right-click it with armour or a weapon)
 * /helper stop                  stop the job it is doing
 * /helper watch [on|off|guard|fish|farm|smelt|mine <ore>|explore|home]   (home = work at home: all the chores)
 * /helper watch […]   what it does while you are logged out: guard home,
 *                               fish, harvest or smelt at home, dig for one kind of ore within 128 blocks of home, or
 *                               explore the land to 1000 blocks out and remember the buildings it finds. When bag and
 *                               chests are full it makes a new chest from planks or logs and sets it down at home (also on an empty server and after a restart, for up to settings.away-hours
 *                               in helpers.yml, default 2). It tells you how it went when you are back.
 * On its own (you more than 12 blocks away, or logged out) it shuts the wooden doors and fence gates around it, so the
 * animals stay in. Its own tools have fortune III, and its rod luck of the sea III, so it brings back more.
 * Console/RCON: helper pause | resume | save | status | tell <player> <words>
 * /helper personality [name] [name] | reset   how it talks: brave, funny, grumpy, pirate, robot … (25 to pick from, at most two)
 * right-click it               it stands still and watches you, whatever it was doing, and fights nothing; a click more (or continue) and it carries on
 * /helper mobfarm [name] [here]  it runs a mob farm from a post outside it (where you stand the first time): strikes what it can reach, sorts the chests, keeps on when you log out
 * /helper continue              back to what it did before a click, come, stay, stop or help interrupted it
 * /helper rescue                (say "help") it drops everything, comes at once and fights what is around you
 * /helper sum                   everything it has done since it began its job while you were away (also ./mc.sh sum)
 * /helper talk [on|off]         talk mode (or just "h" in chat, on and off): everything you write in chat goes to your helper until you say "talk off"
 *                               (or log out). Without it, start a chat line with the helper's name or with a full stop
 * /helper quiet                 comments on what happens, on/off
 * /helper skin <name>           a skin from skins/ (imported with "ai helper skin <name>") or a player's skin
 * /helper name <name>           name shown above it, and the word it listens for in chat
 * /helper dismiss               send it away (until /helper again)
 * /helper ask <anything>        the same as saying its name and <anything> in chat
 * /helper say <text>            used by the chat bridge: the helper says this to you
 */
final class Helper implements Listener {
    private static final double DAMAGE = 20;       // per bite/hit: most monsters die in one or two
    private static final int HOME = 100;           // chests, furnaces, fields and water this close to home belong to home
    private static final int GUARD_RANGE = 16;     // monsters this close to the owner (or the guard post) get attacked
    private static final int VEIN_MAX = 128;       // blocks in one /helper mine
    private static final int FARM_RANGE = 8;
    private static final EquipmentSlot[] GEAR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.HAND, EquipmentSlot.OFF_HAND};
    private static final Material[] TOOLS = {Material.NETHERITE_PICKAXE, Material.NETHERITE_SHOVEL, Material.NETHERITE_AXE, Material.SHEARS};
    /** The helper's own tools have the best there is on them: fortune and luck count for what it brings back. */
    private static ItemStack ench(Material m) {
        ItemStack it = new ItemStack(m);
        String n = m.name();
        if (n.endsWith("_PICKAXE") || n.endsWith("_AXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE")) {
            it.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.FORTUNE, 3);
            it.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.EFFICIENCY, 5);
        } else if (m == Material.FISHING_ROD) {
            it.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.LUCK_OF_THE_SEA, 3);
            it.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.MENDING, 1);
            it.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.UNBREAKING, 3);
            it.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.LURE, 3);
        } else if (n.endsWith("_SWORD")) {
            it.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.SHARPNESS, 5);
            it.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.LOOTING, 3);
        } else return it;
        it.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.UNBREAKING, 3);
        it.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.MENDING, 1);
        return it;
    }

    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
    private static final Map<Material, Material> SEEDS = Map.of(Material.WHEAT, Material.WHEAT_SEEDS, Material.CARROTS, Material.CARROT,
            Material.POTATOES, Material.POTATO, Material.BEETROOTS, Material.BEETROOT_SEEDS, Material.NETHER_WART, Material.NETHER_WART);
    private static final Pattern HAND_GEAR = Pattern.compile(".*_(SWORD|AXE|PICKAXE|SHOVEL|HOE|SPEAR)|TRIDENT|MACE|BOW|CROSSBOW|FISHING_ROD");

    /** What the helper understands without the chat bridge: {subcommand, whole-sentence pattern}. English and Norwegian. */
    private static final String[][] WORDS = {
        {"continue", "continue|go on|carry on|keep going|back to work|fortsett|fortsett med det|jobb videre"},
        {"rescue", "help|help me|hjelp|hjelp meg|save me|redd meg"},
        {"come", "come|come here|come to me|here|kom|kom hit|kom her|hit"},
        {"follow", "follow|follow me|with me|come with me|følg|følg meg|bli med"},
        {"stay", "stay|stay here|wait|wait here|sit|vent|vent her|bli|bli her|sitt"},
        {"guard", "guard|guard here|guard this|stand guard|vakt|pass på|pass på her|stå vakt"},
        {"passive", "passive|peaceful|don'?t fight|do not fight|stop fighting|ikke slåss|fredelig"},
        {"fight", "fight|protect me|slåss|beskytt meg"},
        {"defend", "defend|defend me|defend us|forsvar|forsvar meg|forsvar oss"},
        {"defend off", "defend off|stop defending|ikke forsvar|slutt å forsvare"},
        {"attack", "attack|attack that|attack it|attack him|kill|kill that|kill it|get it|get him|angrip|angrip den|angrip det|ta den|drep|drep den"},
        {"smelt", "smelt|smelt everything|cook|cook the food|smelt alt|stek|stek maten|kok"},
        {"dance", "dance|danse|dans"},
        {"sleep", "sleep|go to bed|go to sleep|bedtime|good night|sov|legg deg|gå og legg deg|god natt|natta"},
        {"wake", "wake up|wake|get up|våkn opp|våkne|stå opp"},
        {"finds", "finds|special|anything special|did you find anything(?: special)?|what did you find|found anything(?: special)?|funn|noe spesielt|fant du noe(?: spesielt)?|hva fant du|har du funnet noe(?: spesielt)?"},
        {"log", "log|logg|helper log|report|rapport|details|detaljer"},
        {"sum", "sum|sum up|summary|what have you done|oppsummer|oppsummering|summer|hva har du gjort"},
        {"watch fish", "fish (?:while|when) i(?: am|'?m) (?:away|gone|logged out)|fisk (?:mens|når) jeg er (?:borte|logget ut)"},
        {"watch farm", "(?:farm|harvest) (?:while|when) i(?: am|'?m) (?:away|gone|logged out)|høst (?:mens|når) jeg er (?:borte|logget ut)"},
        {"watch smelt", "smelt (?:while|when) i(?: am|'?m) (?:away|gone|logged out)|smelt (?:mens|når) jeg er (?:borte|logget ut)"},
        {"watch home", "work at home|(?:work|work at home|do the chores) (?:while|when) i(?: am|'?m) (?:away|gone|logged out)|jobb hjemme|(?:jobb|jobb hjemme|arbeid hjemme) (?:mens|når) jeg er (?:borte|logget ut)"},
        {"watch explore", "explore (?:while|when) i(?: am|'?m) (?:away|gone|logged out)|utforsk (?:mens|når) jeg er (?:borte|logget ut)"},
        {"watch guard", "guard (?:while|when) i(?: am|'?m) (?:away|gone|logged out)|(?:vokt|pass på) (?:mens|når) jeg er (?:borte|logget ut)"},
        {"shear", "shear|shear sheep|shear the sheep|get wool|fetch wool|find wool|bring wool|bring me wool|hent ull|klipp sau|klipp sauer|klipp sauene"},
        {"tidy", "tidy|tidy up|sort|sort the chests|rydd|rydd opp|rydd kistene|sorter|sorter kistene"},
        {"repair", "repair|fix|fix my things|fix my gear|repair my gear|reparer|fiks|fiks tingene mine|reparer utstyret|reparer utstyret mitt"},
        {"routines", "routines|show routines|what are your routines|rutiner|vis rutiner|hvilke rutiner har du"},
        {"routine clear", "clear routines|reset routines|forget all routines|nullstill|nullstill rutiner|slett alle rutiner|fjern alle rutiner"},
        {"places", "places|where can we go|what places do you know|steder|hvilke steder husker du|hvor kan vi dra"},
        {"watch on", "watch|keep watch|guard home|watch the house|guard when i am gone|hold vakt|hold vakt hjemme|pass på huset|vokt hjemme|pass på hjemme"},
        {"watch off", "stop watching|no watch|don'?t keep watch|ikke hold vakt|slutt å holde vakt"},
        {"mine", "mine|mine this|mine that|dig|dig this|chop|chop this|hugg|hugg denne|hugg dette|grav|grav her|grav denne"},
        {"farm", "farm|farm here|harvest|høst|høst her"},
        {"fish", "fish|go fish|go fishing|start fishing|fisk|fiske|fisk litt|dra og fisk"},
        {"torch", "torch|torches|fakler|fakkel"},
        {"light", "light|light up|lantern|carry a light|lys|lys opp|lykt"},
        {"bridge", "bridge|build a bridge|stairs|build stairs|bro|bygg bro|trapp|bygg trapp"},
        {"home", "home|take me home|let'?s go home|hjem|ta meg hjem|vi drar hjem"},
        {"sethome", "set home|sethome|this is home|home is here|sett hjem|her er hjemme|dette er hjemme|her bor vi"},
        {"unload", "unload|empty|empty your bag|empty the bag|tøm|tøm sekken|tøm sekken din|tøm deg"},
        {"ride", "ride|carry me|let me ride|ri|bær meg"},
        {"big", "big|bigger|grow|get bigger|get big|stor|større|bli stor|bli større|voks"},
        {"small", "small|smaller|shrink|get smaller|get small|liten|mindre|bli liten|bli mindre|krymp"},
        {"max", "max|maximum|max size|huge|giant|biggest|maks|størst|kjempestor|kjempe"},
        {"min", "min|minimum|min size|tiny|smallest|minst|bitteliten|ørliten"},
        {"normal", "normal|normal size|be normal|vanlig|vanlig størrelse|normal størrelse|bli normal|bli vanlig"},
        {"merge", "merge|merge with me|fuse|become me|smelt sammen|smelt sammen med meg|slå oss sammen|bli meg"},
        {"split", "split|split up|unmerge|separate|come out|del oss|del deg|skill oss|kom ut"},
        {"show on", "show|show yourself|vis deg"},
        {"show off", "hide yourself from the others|don'?t show yourself|skjul deg|skjul deg for de andre"},
        {"hide", "hide|hide and seek|play hide and seek|let'?s play hide and seek|gjemsel|gjem deg|vi leker gjemsel|lek gjemsel"},
        {"tag", "tag|play tag|let'?s play tag|sisten|vi leker sisten|lek sisten"},
        {"treasure", "treasure|treasure hunt|hide a treasure|skatt|skattejakt|gjem en skatt"},
        {"hint", "hint|clue|give me a hint|give me a clue|ledetråd|gi meg et hint|gi meg en ledetråd"},
        {"scout", "scout|scout ahead|look ahead|speid|speid foran|se hva som er foran"},
        {"wayback", "wayback|way back|way out|show the way out|show the way back|veien ut|veien tilbake|vis veien ut|vis veien tilbake"},
        {"style bow", "bow|use your bow|shoot|shoot them|bue|bruk buen|skyt|skyt dem"},
        {"style shield", "shield|use your shield|tank|skjold|bruk skjoldet|stå foran meg"},
        {"style sword", "sword|use your sword|sverd|bruk sverdet"},
        {"bag", "bag|chest|inventory|open|open bag|open the bag|sekk|kiste|åpne|åpne sekken"},
        {"pickup", "pickup|pick up|plukk|plukk opp"},
        {"unequip", "unequip|undress|take off|kle av|kle av deg"},
        {"stop", "stop|cancel|stopp|avbryt"},
        {"quiet", "quiet|be quiet|shut up|hysj|stille|vær stille"},
        {"talk", "h"},
        {"talk on", "talk|talk on|talk mode|let'?s talk|snakk|snakk på|snakkemodus|la oss snakke|vi snakker"},
        {"talk off", "talk off|stop talk mode|normal chat|chat|snakk av|vanlig chat|ferdig snakket"},
        {"dismiss", "dismiss|go home|leave|bye|gå hjem|forsvinn|ha det"},
        {"hello", "hi|hello|hey|hei|hallo|summon|appear"},
    };
    private static final Pattern SKIN_WORDS = Pattern.compile("(?:skin|look like|looks like|ser ut som)\\s+(.{1,40})");
    private static final Pattern NAME_WORDS = Pattern.compile("(?:name|your name is|du heter|heter|navn)\\s+(.{1,32})");
    private static final Pattern FETCH_WORDS = Pattern.compile("(?:fetch|get|get me|bring|bring me|give me|hent|gi meg)\\s+(.{1,40})");
    private static final Pattern FIND_WORDS = Pattern.compile("(?:find|locate|where is|where are|finn|hvor er)\\s+(?:(?:the|a|an|some|nearest|closest|nærmeste|en|et)\\s+)*(.{1,40})");
    private static final Pattern NUMBER = Pattern.compile("\\b(\\d{1,4})\\b");
    private static final Pattern HELP_WORDS = Pattern.compile("(?:help|protect|look after|take care of|hjelp|beskytt|pass på|se etter)\\s+(\\w{3,16})");
    private static final Pattern JOIN_WORDS = Pattern.compile("(?:join|work with|bli med|samarbeid med|jobb med|jobb sammen med)\\s+(\\w{3,16})");
    private static final Pattern CRAFT_WORDS = Pattern.compile("(?:craft|craft me|make|make me|lag|lag meg)\\s+(.{1,40})");
    private static final Pattern SMELT_WORDS = Pattern.compile("(?:smelt|cook|stek|kok)\\s+(.{1,40})");
    private static final Pattern REMEMBER_WORDS = Pattern.compile("(?:remember (?:this place|this|here) as|call this place|husk (?:dette stedet|stedet|dette|her) som|kall dette stedet|kall stedet)\\s+(.{1,30})");
    private static final Pattern GOTO_WORDS = Pattern.compile("(?:take me to|bring me to|go to|teleport to|ta meg til|gå til|dra til|til)\\s+(.{1,30})");
    private static final Pattern ARTICLE = Pattern.compile("^(?:the|a|an|some|en|et|ei|noen)\\s+");
    private static final Set<Material> FUELS = Set.of(Material.COAL, Material.CHARCOAL, Material.COAL_BLOCK, Material.BLAZE_ROD, Material.LAVA_BUCKET, Material.DRIED_KELP_BLOCK);
    private static final Pattern COLLECT_WORDS = Pattern.compile("(?:go to|gå til|dra til)\\s+(.{1,30}?)\\s+(?:and|og)\\s+(?:fetch|get|bring|collect|hent|ta med)\\s+(?:everything|it all|all|alt|alt sammen)");
    private static final Pattern TRADE_WORDS = Pattern.compile("(?:trade for|buy|buy me|kjøp|kjøp meg)\\s+(.{1,40})");
    private static final Pattern GIVE_WORDS = Pattern.compile("(?:give|bring|send|gi|send)\\s+(?:this|it|dette|denne|den)\\s+(?:to|til)\\s+(\\w{3,16})");
    private static final Pattern ROUTINE_WORDS = Pattern.compile("(?:every|hver)\\s+(morning|noon|evening|night|morgen|middag|kveld|natt)\\s*[:,]?\\s*(.{2,120})");
    private static final Pattern EVERY_WORDS = Pattern.compile("(?:every|hvert|hver)\\s+(\\d{1,3})\\.?\\s*min\\w*\\s*[:,]?\\s*(.{2,120})");
    private static final Pattern UNROUTINE_WORDS = Pattern.compile("(?:remove|delete|fjern|slett)\\s+(?:routine|rutine)\\s+(\\d{1,2})");
    private static final Map<String, Integer> TIMES = Map.of("morning", 500, "noon", 6000, "evening", 11500, "night", 13500);
    private static final Map<String, String> TIME_WORDS = Map.of("morgen", "morning", "middag", "noon", "kveld", "evening", "natt", "night");
    private static final Pattern CALLME_WORDS = Pattern.compile("(?:call me|kall meg)\\s+(.{1,24})");
    /** What two helpers say when they meet: {first, answer}; {a} and {b} are their names, {oa} and {ob} their owners. */
    private static final String[][] BANTER = {
        {"Hi {b}! Still carrying everything for {ob}?", "Hi {a}! Someone has to. You look good today."},
        {"{b}, I bet I can beat more zombies than you.", "You are on, {a}. Loser carries the cobblestone."},
        {"Nice to see you, {b}!", "You too, {a}! Did you get taller, or is it just the hair?"},
        {"{b}, race you to the next tree?", "Only if you promise not to sulk when I win, {a}."},
        {"Good work today, {b}.", "Thanks, {a}. You were almost as good as me."},
        {"{b}! I saved you a spot.", "You are the best, {a}. Don't tell {oa} I said that."},
        {"Psst, {b}. I think {oa} is lost again.", "Ha! {ob} too. Good thing they have us, {a}."},
    };
    private static final Pattern POLITE = Pattern.compile("^(?:please|can you|could you|kan du|vær så snill)\\s+|\\s+(?:please|takk)$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private enum Stance { FOLLOW, STAY, GUARD }
    private enum Job { NONE, MINE, FARM, FISH }

    /** One helper that is in the world right now. */
    private static final class H {
        Location bedGo; int bedTries; boolean sleep, sleptNight;   // on its way to a bed; lying in it
        Errand errand;                                         // on its way to a chest and back
        boolean defend;                                        // merged: it strikes the monsters that come near (asked for with defend)
        double fit = -1;                                       // the size it is shown in (smaller than its real size under a low roof)
        Material tool; int toolTick;                           // what it holds just now (sword, axe, shovel), and since when
        Wolf w;
        Mannequin m;
        Stance stance = Stance.FOLLOW;
        Job job = Job.NONE;
        Location anchor;                                   // the guard post, the middle of the field, or the fishing water
        Deque<Block> queue = new ArrayDeque<>();           // blocks left to mine or harvest (shared when helpers work together)
        int done, stuck, cast;                             // cast: half-seconds until the fish bites, 0 = not cast yet
        boolean riding, merged, gliding, hold;             // hold: clicked on, stands still and watches its owner, fights nothing
        String farm;                                       // the mob farm it is running: it keeps its post there, strikes what it can reach and sorts the chests
        long farmSort;
        Location holdAt;                                   // where it was clicked on: it does not let itself be pushed or carried off from there
        LivingEntity sword;                                // a monster it cannot walk up to: it stays outside and reaches it with the sword
        int boxed;                                         // seconds it has had no way to its owner
        final Map<UUID, Long> shun = new HashMap<>();      // monsters it can neither walk to nor reach, left alone for a while
        Object[] was;                                      // what it did before it was interrupted: stance, job, anchor, queue (for "continue")
        UUID ordered;                                      // the mob it was sent at with /helper attack
        boolean watching, saved;                           // watching: the owner is offline and it guards home
        Chunk ticket;                                      // kept loaded while it watches
        Location ownerAt, idleLoc, look;                   // habits: where the owner stood, where it is going, what it looks at
        Entity idleEnt;
        String idle;                                       // the habit it is busy with: flower, give, pet, campfire, sunset, puddle
        ItemStack gift;
        int calm, idleStep, placed;                        // calm: half-seconds the owner has stood still
        long nextIdle, placedSince, banter;
        long lastTime = -1;                                // routines: the world time at the last look
        final Map<String, Long> ran = new HashMap<>();     // routines "every N minutes": when each last ran
        LivingEntity foe;                                  // bow style: what it is shooting at
        final List<Location> crumbs = new ArrayList<>();   // where the owner has walked
        int trail = -1, trailTop, trailEnd;                // the way back: exit crumb, newest crumb, tick it stops showing
        String game;                                       // hide, flee or chase
        long gameAt;
        double gameDist;
        int gameN;
        Location treasure;
        int clue;
        int dance;                                         // ticks left of waving (above 50) and dancing
        UUID owner, ward;                                  // ward: another player it is looking after right now
        Location goal, last;                               // merged: where the owner is being walked to, and where they were
        int still;
        Block light;                                       // where its carried light is right now
        long healed, unloadFailed;
        String shown = "";                                 // the stance and job its hands were last dressed for
        boolean moving, night;
        final Map<String, Long> said = new HashMap<>();    // when it last said each kind of comment
    }

    private record Bag(UUID owner) implements InventoryHolder {
        @Override
        public Inventory getInventory() { return null; }
    }

    private final JavaPlugin plugin;
    private final Skins skins;
    private final File file;
    private final YamlConfiguration conf;
    private final NamespacedKey key, speedKey;
    private final Map<UUID, H> helpers = new HashMap<>();
    private final Map<UUID, Inventory> bags = new HashMap<>();
    private final Map<UUID, ItemStack[]> gear = new HashMap<>();
    private final Set<UUID> dirty = new HashSet<>();                       // bags changed since the last save
    private final Map<UUID, String> names = new ConcurrentHashMap<>();     // lower-case helper names, read by the chat thread
    private final Map<UUID, List<long[]>> awayHeld = new HashMap<>();      // home chunks kept loaded for work while the owner is away
    private final Map<UUID, World> awayWorld = new HashMap<>();
    private final Map<UUID, Long> awayTime = new HashMap<>();              // routines while away: the world time at the last look
    private final Map<String, Long> awayRan = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> awayGot = new HashMap<>();   // what it got since the last line in the log, by kind
    private final Map<UUID, Map<String, Integer>> awaySeen = new HashMap<>();  // the counters at the last ten-minute line
    private final Map<String, Long> awayTold = new HashMap<>();            // warnings and meetings already in the log
    private final Map<UUID, Integer> awayMinute = new HashMap<>();         // what it got since the last line in the log
    private static final List<String> AWAY_JOBS = List.of("guard", "fish", "farm", "smelt", "mine", "explore", "home", "mobfarm");
    private static final Map<String, String> ORE_WORDS = Map.ofEntries(Map.entry("diamant", "DIAMOND"), Map.entry("diamanter", "DIAMOND"), Map.entry("kull", "COAL"),
            Map.entry("jern", "IRON"), Map.entry("gull", "GOLD"), Map.entry("kobber", "COPPER"), Map.entry("smaragd", "EMERALD"), Map.entry("smaragder", "EMERALD"),
            Map.entry("kvarts", "QUARTZ"), Map.entry("netherite", "ANCIENT_DEBRIS"), Map.entry("debris", "ANCIENT_DEBRIS"), Map.entry("obsidian", "OBSIDIAN"));
    private final Map<UUID, Deque<Block>> awayOre = new HashMap<>();       // ore it has seen and not dug out yet
    private final Map<UUID, Integer> awayIdle = new HashMap<>();         // ten-second rounds in a row with nothing to do in the field
    private final Map<UUID, Integer> awayScan = new HashMap<>();           // how far out it has looked for ore
    private final Set<UUID> talking = ConcurrentHashMap.newKeySet();       // talk mode: all their chat goes to the helper
    private final Map<UUID, Long> asked = new HashMap<>();
    private final Map<UUID, Deque<Object[]>> rubble = new HashMap<>();     // owner -> {Block, BlockData} a creeper blew up near home
    private final Map<UUID, Long> rubbleAt = new HashMap<>();
    private final Map<UUID, Long> buried = new HashMap<>();                // when each player last got a treasure
    private int tick;

    private Storage storage;                                              // the magic storage barrels: unload can use your own store there
    void useStorage(Storage s) { storage = s; }

    /** You placed or opened a magic barrel: that is your barrel now, and your helper remembers where it stands. */
    void barrelUsed(Player p, Block b) {
        UUID id = p.getUniqueId();
        String at = b.getWorld().getName() + " " + b.getX() + " " + b.getY() + " " + b.getZ();
        if (at.equals(conf.getString(path(id, "barrel")))) return;
        conf.set(path(id, "barrel"), at);
        save();
        H h = helpers.get(id);
        if (h != null) say(p, "I will remember your magic barrel here (" + b.getX() + " " + b.getY() + " " + b.getZ() + "), and put everything I gather in your own store in it.");
    }

    /** Your own magic barrel, when it stands in this world (or its nether or end) and is still there. Gone: forgotten. */
    private Location myBarrel(UUID id, World w) {
        String s = conf.getString(path(id, "barrel"));
        if (s == null || storage == null || w == null) return null;
        String[] a = s.split(" ");
        World bw = Bukkit.getWorld(a[0]);
        if (bw == null || !realm(bw).equals(realm(w))) return null;
        Block b = bw.getBlockAt(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]));
        if (!storage.isBarrel(b)) { conf.set(path(id, "barrel"), null); save(); return null; }
        return b.getLocation();
    }

    Helper(JavaPlugin plugin, Skins skins) {
        this.plugin = plugin;
        this.skins = skins;
        file = new File(plugin.getDataFolder(), "helpers.yml");
        conf = YamlConfiguration.loadConfiguration(file);
        key = new NamespacedKey(plugin, "helper_owner");
        speedKey = new NamespacedKey(plugin, "helper_speed");
        for (String k : conf.getKeys(false)) {
            try { names.put(UUID.fromString(k), conf.getString(k + ".name", "Helper").toLowerCase(Locale.ROOT)); } catch (IllegalArgumentException ignored) { }
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 1L);
    }

    private String path(UUID id, String k) { return id + "." + k; }

    private boolean active(Player p) { return conf.getBoolean(path(p.getUniqueId(), "active")); }

    private boolean flag(UUID id, String k, boolean def) { return conf.getBoolean(path(id, k), def); }

    private void save() {
        try { conf.save(file); } catch (IOException e) { plugin.getLogger().warning("Could not save helpers.yml: " + e.getMessage()); }
    }

    /** A monster the helper fights or warns about. Piglins (you trade gold with them) and zombified piglins are
     *  left alone until they go for a player or a helper; piglin brutes are always foes. */
    private boolean foe(Entity e) {
        if (!(e instanceof Enemy) || !(e instanceof LivingEntity le) || le.isDead() || ownerOf(e) != null) return false;
        if (e instanceof Piglin || e instanceof PigZombie) {
            LivingEntity t = ((Mob) e).getTarget();
            return t != null && !t.isDead() && (t instanceof Player || ownerOf(t) != null);
        }
        return true;
    }

    private UUID ownerOf(Entity e) {
        String s = e.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return s == null ? null : UUID.fromString(s);
    }

    private final Set<String> signAsked = new HashSet<>();

    private ResolvableProfile profile(Player owner) {
        String skin = conf.getString(path(owner.getUniqueId(), "skin"));
        if (skin == null) return ResolvableProfile.resolvableProfile(owner.getPlayerProfile());
        if (skin.startsWith("pack:")) {                        // a PNG from skins/ in the resource pack
            ResolvableProfile rp = skins.packProfile(skin.substring(5));
            String[] sg = rp == null ? null : skins.signed(skin.substring(5));
            if (sg != null) return ResolvableProfile.resolvableProfile().addProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", sg[0], sg[1])).build();   // seen by everyone, with or without the resource pack
            if (rp != null && signAsked.add(skin)) plugin.getLogger().info("HELPER_SIGN " + owner.getName() + "|" + skin.substring(5));
            return rp != null ? rp : ResolvableProfile.resolvableProfile(owner.getPlayerProfile());
        }
        return ResolvableProfile.resolvableProfile().name(skin).build();   // a Minecraft player's skin
    }

    private String name(UUID id) { return conf.getString(path(id, "name"), "Helper"); }

    private Component title(Player owner) {
        return Component.text(name(owner.getUniqueId()));      // plain white, like a player's
    }

    private static void bar(Player p, String text) { p.sendActionBar(Component.text(text, NamedTextColor.GOLD)); }

    /** The helper talks: looks like a chat line from it, only the owner gets it. */
    private void say(Player p, String text) {
        say(p, text, true);
    }

    /** {@code own}: a line of the plugin's, which gets a touch of the personality (what the model answers has it already). */
    private void say(Player p, String text, boolean own) {
        if (own) text = voice(p.getUniqueId(), text);
        p.sendMessage(Component.text("<" + name(p.getUniqueId()) + "> " + text));
        awayLog(p.getUniqueId(), "says: " + text);                        // the server log shows what it is up to
    }

    /** A comment at most once per so many seconds, and not at all when the owner has asked for quiet. */
    private void remark(Player p, H h, String kind, int seconds, String... lines) {
        if (flag(p.getUniqueId(), "quiet", false)) return;
        long now = System.currentTimeMillis();
        if (now - h.said.getOrDefault(kind, 0L) < seconds * 1000L) return;
        h.said.put(kind, now);
        say(p, lines[ThreadLocalRandom.current().nextInt(lines.length)]);
    }

    // --- Spawning ------------------------------------------------------------------------------------

    private void spawn(Player p, Location at) {
        despawn(p.getUniqueId(), false);
        String owner = p.getUniqueId().toString();
        if (at == null) at = p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(-1.5));
        H h = new H();
        h.owner = p.getUniqueId();
        h.w = p.getWorld().spawn(at, Wolf.class, b -> {
            b.setTamed(true);
            b.setOwner(p);
            b.setAdult();
            b.setPersistent(false);
            b.setRemoveWhenFarAway(false);
            b.setSilent(true);
            b.setInvisible(true);
            b.setVisibleByDefault(false);                      // only the owner gets it, see showEntity below
            b.setCollidable(false);
            set(b, Attribute.MAX_HEALTH, 200);
            b.setHealth(200);
            set(b, Attribute.ATTACK_DAMAGE, DAMAGE);
            set(b, Attribute.MOVEMENT_SPEED, 0.38 * speed(p.getUniqueId()));
            set(b, Attribute.FOLLOW_RANGE, 48);
            b.getPersistentDataContainer().set(key, PersistentDataType.STRING, owner);
        });
        Bukkit.getMobGoals().removeGoal((org.bukkit.entity.Creature) h.w, VanillaGoal.WATER_AVOIDING_RANDOM_STROLL);   // a calm temper: no wandering about,
        Bukkit.getMobGoals().removeGoal((org.bukkit.entity.Creature) h.w, VanillaGoal.RANDOM_STROLL);                  // no begging, no fleeing,
        Bukkit.getMobGoals().removeGoal((org.bukkit.entity.Creature) h.w, VanillaGoal.PANIC);                          // no looking all over the place
        Bukkit.getMobGoals().removeGoal((Tameable) h.w, VanillaGoal.TAMABLE_ANIMAL_PANIC);
        Bukkit.getMobGoals().removeGoal(h.w, VanillaGoal.WOLF_BEG);
        Bukkit.getMobGoals().removeGoal(h.w, VanillaGoal.RANDOM_LOOK_AROUND);
        Bukkit.getMobGoals().removeGoal(h.w, VanillaGoal.LEAP_AT);                   // no jumping in through gaps after a monster
        Bukkit.getMobGoals().removeGoal((Tameable) h.w, VanillaGoal.FOLLOW_OWNER);   // think() decides where it walks
        h.m = p.getWorld().spawn(at, Mannequin.class, b -> {
            b.setProfile(profile(p));
            b.customName(title(p));
            b.setCustomNameVisible(true);
            b.setDescription(null);                            // no second line under the name
            b.setVisibleByDefault(flag(p.getUniqueId(), "show", false));
            b.setGravity(false);
            b.setPersistent(false);
            b.setSilent(true);
            b.setCollidable(false);
            b.setInvulnerable(true);
            set(b, Attribute.SCALE, size(p.getUniqueId()));
            b.getPersistentDataContainer().set(key, PersistentDataType.STRING, owner);
        });
        h.night = night(p.getWorld());
        helpers.put(p.getUniqueId(), h);
        names.put(p.getUniqueId(), name(p.getUniqueId()).toLowerCase(Locale.ROOT));
        dress(p.getUniqueId(), h);
        p.showEntity(plugin, h.w);
        p.showEntity(plugin, h.m);
        p.spawnParticle(Particle.CLOUD, at.clone().add(0, 1, 0), 15, 0.3, 0.6, 0.3, 0.02);
    }

    private static void set(LivingEntity e, Attribute a, double v) {
        var i = e.getAttribute(a);
        if (i != null) i.setBaseValue(v);
    }

    private void despawn(UUID id, boolean poof) {
        H h = helpers.get(id);
        if (h == null) return;
        release(h);                                            // also lets go of a player it was looking after
        helpers.remove(id);
        Player p = Bukkit.getPlayer(id);
        if (poof && p != null && h.m.isValid() && h.m.getWorld() == p.getWorld()) {
            Location l = h.m.getLocation().add(0, 1, 0);
            p.spawnParticle(Particle.POOF, l, 30, 0.35, 0.7, 0.35, 0.03);
            p.playSound(l, Sound.BLOCK_FIRE_EXTINGUISH, 0.35f, 1.8f);
        }
        if (h.merged && p != null) { h.gliding = false; p.setGliding(false); ownBody(p); }
        if (h.ticket != null) h.ticket.removePluginChunkTicket(plugin);
        clearLight(h);
        h.m.eject();
        h.w.remove();
        h.m.remove();
    }

    void shutdown() {
        saveSites();                                           // a building in progress goes on after the restart
        for (UUID id : new ArrayList<>(helpers.keySet())) despawn(id, false);
        rebuild(true);
        dirty.addAll(bags.keySet());
        flushBags();
        save();                                                // also the counters of work done while owners are away
    }

    // --- Every tick: the figure follows the wolf; twice a second: decide what to do --------------------

    private void tick() {
        tick++;
        if (tick % 20 == 0)                                   // (re)spawn for players who should have one
            for (Player p : Bukkit.getOnlinePlayers())
                if (active(p) && !helpers.containsKey(p.getUniqueId())) spawn(p, null);
        if (tick % 600 == 0) flushBags();
        if (tick == 100) loadSites();                         // the worlds are loaded by now
        if (tick % 200 == 0) away();
        if (tick % 100 == 0) banter();
        if (tick % 2 == 0 && !rubble.isEmpty()) rebuild(false);
        for (UUID id : new ArrayList<>(helpers.keySet())) {
            Player p = Bukkit.getPlayer(id);
            H h = helpers.get(id);
            if (p == null) {                                   // logged out: only a helper on watch stays, while someone is online
                if (!h.watching || !h.w.isValid() || !h.m.isValid()) { despawn(id, false); continue; }
                if (tick % 10 == 0 && !paused()) guard(id, h);
                if (tick % 20 == 0 && !paused()) { shut(h.w.getLocation(), 4, h.w); awayMeet(id, h.w.getLocation()); }
                Location l = h.w.getLocation();
                l.setPitch(0);
                if (h.m.getWorld() != l.getWorld() || h.m.getLocation().distanceSquared(l) > 0.0001) h.m.teleport(l);
                continue;
            }
            if (!h.w.isValid() || !h.m.isValid()) { spawn(p, null); continue; }
            if (h.merged) autopilot(p, h);
            if (h.riding) {
                if (h.m.getPassengers().contains(p)) steer(p, h);
                else stopRide(h);
            }
            if (tick % 10 == 0) think(p, h);
            if (tick % 5 == 0) light(id, h);
            if (tick % 2 == 0 && flag(id, "bridge", false)) bridge(p, h);
            Location l = h.w.getLocation();
            l.setPitch(0);
            Location look = h.sleep ? null : h.stance == Stance.STAY || h.dance > 50 ? p.getEyeLocation() : h.job == Job.FISH && h.cast > 0 ? h.anchor : h.look;
            if (look != null && look.getWorld() == l.getWorld()) {        // waiting: it watches you; fishing: the water
                Vector d = look.toVector().subtract(l.toVector().setY(l.getY() + 1.6));
                if (d.lengthSquared() > 0.01 && d.lengthSquared() < 32 * 32) l.setDirection(d);
            }
            if (h.dance > 0) {                                 // hi: a wave, then a spin with a bob
                h.dance--;
                if (h.dance > 50) { if (h.dance % 5 == 0) h.m.swingMainHand(); }
                else {
                    l.setYaw(l.getYaw() + h.dance * 36f);
                    if (h.dance % 8 == 0) { h.m.swingMainHand(); p.spawnParticle(Particle.NOTE, l.clone().add(0, 2.2, 0), 1, 0.3, 0.1, 0.3, 1); }
                }
            }
            Location at = h.m.getLocation();
            if (h.m.getWorld() != l.getWorld() || at.distanceSquared(l) > 0.0001 || Math.abs(at.getYaw() - l.getYaw()) > 0.5
                    || Math.abs(at.getPitch() - l.getPitch()) > 0.5) h.m.teleport(l, TeleportFlag.EntityState.RETAIN_PASSENGERS);
            Pose pose = (p.isSleeping() || h.sleep) && Mannequin.validPoses().contains(Pose.SLEEPING) ? Pose.SLEEPING
                    : h.dance > 0 && h.dance <= 50 && (h.dance / 5) % 2 == 0 ? Pose.SNEAKING : Pose.STANDING;
            if (h.m.getPose() != pose) h.m.setPose(pose, true);
            if (h.tool != null && tick - h.toolTick > 60) h.tool = null;
            if (tick % 10 == 0 && h.w.getTarget() != null && !h.w.getTarget().isDead()) wield(h, Material.NETHERITE_SWORD);
            if (!h.shown.equals(shownKey(id, h))) dress(id, h);
        }
    }

    private static double dist2(Entity a, Location b) {
        return a.getWorld() != b.getWorld() ? Double.MAX_VALUE : a.getLocation().distanceSquared(b);
    }

    private static boolean night(World w) {
        return w.getEnvironment() == World.Environment.NORMAL && !w.isDayTime();
    }

    private void think(Player p, H h) {
        UUID id = p.getUniqueId();
        Wolf w = h.w;
        if (h.bedGo != null && !h.merged) {                               // on its way to bed
            if (h.bedGo.getWorld() != w.getWorld()) h.bedGo = null;
            else if (w.getLocation().distanceSquared(h.bedGo) > 2.5 * 2.5 && ++h.bedTries < 40) { w.getPathfinder().moveTo(h.bedGo, 1.0); return; }
            else {
                h.hold = true;
                h.stance = Stance.STAY;
                w.setTarget(null);
                w.getPathfinder().stopPathfinding();
                w.setAware(false);
                h.holdAt = h.bedGo;
                h.bedGo = null;
                h.sleep = true;
                h.sleptNight = night(w.getWorld());
                w.teleport(h.holdAt);
            }
        }
        if (h.sleep && !h.merged) {                                       // up with the sun
            if (h.sleptNight && !night(w.getWorld())) { release(h); say(p, "Good morning, " + nick(p) + "! I slept well."); return; }
            if (night(w.getWorld())) h.sleptNight = true;
        }
        if (h.errand != null && !h.merged && !h.hold) { errand(p, h); return; }
        if (h.hold && !h.merged) {                                        // stands still and looks at its owner, monsters or not
            w.setTarget(null);
            Location l = h.holdAt != null && h.holdAt.getWorld() == w.getWorld() ? h.holdAt.clone() : w.getLocation();
            if (!h.sleep && w.getWorld() == p.getWorld()) l.setDirection(p.getEyeLocation().toVector().subtract(l.toVector().add(new Vector(0, 1.5, 0))));
            l.setPitch(0);
            if (w.getLocation().distanceSquared(l) > 0.04) { w.teleport(l); w.setVelocity(new Vector(0, 0, 0)); }      // pushed, or carried by water: back to its spot
            else w.setRotation(l.getYaw(), 0);
            return;
        }
        double far = dist2(w, p.getLocation());
        if ((h.stance != Stance.FOLLOW || h.job != Job.NONE) && far > 64 * 64 && !sites.containsKey(id)) {      // the owner left: drop everything
            boolean worked = h.job != Job.NONE;
            release(h);
            if (worked) say(p, "You went far away, so I stopped and came after you.");
        }
        if (tick % 20 == 0 && !h.merged && far > 12 * 12) {
            shut(w.getLocation(), 4, w);
        }      // on its own: doors and gates shut behind it
        if (tick % 40 == 0) awayMeet(id, w.getLocation());
        if (tick % 100 == 0 && GoldenChicken.instance != null)
            for (Entity e : w.getNearbyEntities(12, 6, 12)) if (e instanceof org.bukkit.entity.Chicken c && GoldenChicken.instance.isGolden(c)) goldSeen(id, c, null);
        Player ward = h.ward == null ? null : Bukkit.getPlayer(h.ward);
        if (h.ward != null && ward == null) {                             // the one it looked after has left
            release(h);
            w.teleport(p.getLocation());
            say(p, "They left, so I am back with you.");
        }
        comments(p, h);
        heal(p, h, p);
        if (ward != null) heal(p, h, ward);
        var scale = h.m.getAttribute(Attribute.SCALE);                    // the wand's big/small cast on it counts too
        if (scale != null && Math.abs(scale.getBaseValue() - (h.fit > 0 ? h.fit : size(id))) > 0.01) resize(p, h, scale.getBaseValue(), false);
        if (scale != null && !h.merged) {                                  // under a roof it makes itself small enough to stand upright (a head in the ceiling turns it black)
            double want = size(id);
            int free = 0, need = (int) Math.ceil(1.85 * want) + 1;
            Block feet = h.w.getLocation().getBlock();
            while (free < need && !feet.getRelative(0, free, 0).getType().isOccluding()) free++;
            double fit = free >= need ? want : Math.min(want, Math.max(0.3, (free - 0.15) / 1.8));
            if (Math.abs(fit - (h.fit > 0 ? h.fit : want)) > 0.01) set(h.m, Attribute.SCALE, fit);
            h.fit = fit;
        }
        Inventory full = bags.get(id);                                     // a full bag: empty it into chests, here or at home
        long now = System.currentTimeMillis();
        if (full != null && full.firstEmpty() == -1 && now - h.unloadFailed > 30_000 && !unload(p, h, true)) h.unloadFailed = now;
        crumbs(p, h);
        if (h.treasure != null) clues(p, h);
        if (h.game != null) { game(p, h); return; }
        routines(p, h);
        Location hm = tick % 6000 == 0 && h.job == Job.NONE ? home(id) : null;      // every five minutes, near home: tidy the chests
        if (hm != null && dist2(w, hm) < 24 * 24) {
            int n = tidy(id);
            if (n > 0) remark(p, h, "tidy", 0, "I tidied the chests at home: " + n + (n == 1 ? " thing" : " things") + " moved to where the rest of its kind is.");
        }
        if (h.stance == Stance.STAY) return;
        if (!h.merged) fightStyle(p, h);
        if (flag(id, "pickup", true)) pickUp(p, h);
        if (h.riding) return;
        if (flag(id, "torch", false) && tick % 20 == 0) torch(w);
        if (h.merged) {                                                   // the owner is the body now
            h.goal = null;
            aura(p, h);
            if (h.job != Job.NONE) work(p, h);
            return;
        }
        if (h.job != Job.NONE) { work(p, h); return; }
        if (h.farm != null && System.currentTimeMillis() - h.farmSort > 60_000 && h.anchor != null) {
            h.farmSort = System.currentTimeMillis();
            sortFarm(id, h.anchor);
        }
        if (swordReach(h)) { h.idle = null; h.look = null; return; }
        boolean busy = w.getTarget() != null && !w.getTarget().isDead();
        if (h.stance == Stance.GUARD) { guard(id, h); return; }
        Player lead = ward != null ? ward : p;                            // who it walks with and fights for
        double d = dist2(w, lead.getLocation());
        if (d > (busy ? 64 * 64 : 24 * 24)) { w.setTarget(null); w.teleport(lead.getLocation()); return; }
        if (lead.isSleeping()) return;
        if (!busy && d > 4 * 4 && tick % 20 == 0) {                       // shut in somewhere with no way to its owner: after four seconds it is fetched
            com.destroystokyo.paper.entity.Pathfinder.PathResult way = w.getPathfinder().findPath(lead.getLocation());
            if (way != null && way.canReachFinalPoint()) h.boxed = 0;
            else if (++h.boxed >= 4) { h.boxed = 0; w.teleport(lead.getLocation()); return; }
        }
        if (!busy) protect(id, h, lead.getLocation());
        if (w.getTarget() != null) { h.idle = null; h.look = null; return; }
        if (ward == null && habits(p, h)) return;
        walk(h, lead.getLocation(), d, 6, d > 14 * 14 ? 1.4 : 1.0);       // calm: it lets its owner get six blocks ahead, and walks, not runs, after them
    }

    /** Walks towards a place when it is further away than {@code keep} blocks, and stops again when it is close. */
    private void walk(H h, Location to, double d2, double keep, double speed) {
        if (d2 > keep * keep) {
            h.w.getPathfinder().moveTo(to, speed);
            h.moving = true;
        } else if (h.moving && d2 < (keep - 1.5) * (keep - 1.5)) {
            h.w.getPathfinder().stopPathfinding();
            h.moving = false;
        }
    }

    /** Holds its post: fights what comes within range of it and walks back afterwards. Works with the owner offline. */
    private void guard(UUID id, H h) {
        Wolf w = h.w;
        boolean busy = w.getTarget() != null && !w.getTarget().isDead();
        if (busy && dist2(w.getTarget(), h.anchor) > (GUARD_RANGE + 8) * (GUARD_RANGE + 8)) { w.setTarget(null); busy = false; }
        if (!busy) protect(id, h, h.anchor);
        double d = dist2(w, h.anchor);
        if (d > 32 * 32) w.teleport(h.anchor);
        else if (!busy && w.getTarget() == null) walk(h, h.anchor, d, 4, 1.2);
    }

    private void protect(UUID id, H h, Location around) {
        if (flag(id, "passive", false)) return;
        LivingEntity best = null;
        double bd = GUARD_RANGE * GUARD_RANGE;
        for (Entity e : around.getWorld().getNearbyEntities(around, GUARD_RANGE, 8, GUARD_RANGE))
            if (foe(e) && e instanceof LivingEntity le && h.shun.getOrDefault(e.getUniqueId(), 0L) < System.currentTimeMillis()) {
                double d = e.getLocation().distanceSquared(around);
                if (d < bd) { bd = d; best = le; }
            }
        if (best == null) return;
        if (style(id).equals("bow")) h.foe = best;                       // it shoots from where it stands, see shoot()
        else h.w.setTarget(best);
    }

    /** A monster it has no way to walk up to (behind the wall of a mob farm, across a gap): it does not try to get
     *  in. It goes as near as the ground lets it and strikes with the sword from there, when the monster is within
     *  4.5 blocks and in sight (or within 3, through a slit). What it cannot reach either it leaves alone for 15 s. */
    private boolean swordReach(H h) {
        Wolf w = h.w;
        if (h.merged) return false;
        LivingEntity t = h.sword != null && !h.sword.isDead() && h.sword.isValid() && h.sword.getWorld() == w.getWorld() ? h.sword : null;
        h.sword = t;
        if (t == null && h.farm != null) {                                  // at its post by the mob farm: the nearest monster within reach
            Location from = w.getLocation().add(0, 1.2, 0);
            double best = 4.5 * 4.5;
            for (Entity e : w.getNearbyEntities(5, 5, 5)) if (foe(e) && e instanceof LivingEntity le) {
                double d2 = from.distanceSquared(le.getLocation().add(0, le.getHeight() / 2, 0));
                if (d2 < best && (d2 <= 9 || w.hasLineOfSight(le) || h.m.hasLineOfSight(le))) { best = d2; t = le; }
            }
            h.sword = t;
            if (t != null) w.setTarget(null);
        }
        if (t == null) {
            LivingEntity v = w.getTarget();
            if (v == null || v.isDead() || v instanceof Player) return false;
            com.destroystokyo.paper.entity.Pathfinder.PathResult pr = w.getPathfinder().findPath(v);
            if (pr != null && pr.canReachFinalPoint()) return false;        // it can walk up to it: it fights as before
            w.setTarget(null);
            h.sword = t = v;
        }
        Location eye = w.getLocation().add(0, 1.2, 0), body = t.getLocation().add(0, t.getHeight() / 2, 0);
        double d = eye.distance(body);
        if (d > GUARD_RANGE + 8) { h.sword = null; return false; }
        if (d <= 4.5 && (d <= 3 || w.hasLineOfSight(t) || h.m.hasLineOfSight(t))) {
            w.getPathfinder().stopPathfinding();
            Location l = w.getLocation();
            l.setDirection(body.toVector().subtract(eye.toVector()));
            w.setRotation(l.getYaw(), 0);
            if (h.farm != null) t.addScoreboardTag("helper_farm");          // counted, not a line each in the log
            t.damage(8.0, w);
            wield(h, Material.NETHERITE_SWORD);
            try { h.m.swingMainHand(); } catch (RuntimeException ignored) { }
            w.getWorld().playSound(w.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 1f);
            return true;
        }
        com.destroystokyo.paper.entity.Pathfinder.PathResult pr = w.getPathfinder().findPath(t.getLocation());
        Location end = pr == null ? null : pr.getFinalPoint();
        if (h.farm == null && end != null && end.distance(t.getLocation()) <= 4.0 && end.distanceSquared(w.getLocation()) > 1) { w.getPathfinder().moveTo(end, 1.2); return true; }
        h.shun.put(t.getUniqueId(), System.currentTimeMillis() + 15_000);
        h.sword = null;
        return false;
    }

    /** Back to plain following: no job, not sitting, no guard post. */
    private void release(H h) {
        if (h.errand != null) {                                           // called off on the way: what it carries goes into its bag
            for (ItemStack it : h.errand.load) for (ItemStack r : bag(h.errand.id).addItem(it).values()) h.w.getWorld().dropItemNaturally(h.w.getLocation(), r);
            dirty.add(h.errand.id);
            h.errand = null;
        }
        stopRide(h);
        endGame(h);
        h.foe = null;
        if (h.ward != null) {
            Player t = Bukkit.getPlayer(h.ward);
            if (t != null && !h.m.isVisibleByDefault()) t.hideEntity(plugin, h.m);
            h.ward = null;
        }
        h.job = Job.NONE;
        h.queue = new ArrayDeque<>();
        h.ordered = null;
        h.idle = null;
        h.look = null;
        h.stance = Stance.FOLLOW;
        if (h.hold) h.w.setAware(true);
        h.hold = false;
        h.defend = false;
        h.sleep = false;
        h.bedGo = null;
        h.farm = null;
        h.holdAt = null;
        h.sword = null;
        h.w.setSitting(false);
    }

    private String nick(Player p) { return conf.getString(path(p.getUniqueId(), "nick"), p.getName()); }

    /** "behind you", "on your left" …: where something is, seen from where the player is looking. */
    private static String side(Player p, Entity e) {
        Vector to = e.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
        Vector f = p.getLocation().getDirection().setY(0);
        if (to.lengthSquared() < 0.01 || f.lengthSquared() < 0.01) return "right here";
        to.normalize();
        f.normalize();
        double dot = f.dot(to), cross = f.getX() * to.getZ() - f.getZ() * to.getX();
        return dot > 0.7 ? "ahead" : dot < -0.7 ? "behind you" : cross > 0 ? "on your right" : "on your left";
    }

    private void comments(Player p, H h) {
        boolean n = night(p.getWorld());
        if (n && !h.night) remark(p, h, "night", 300, "It is getting dark.", "Night is coming. I will keep watch.", "*yawn* The sun is going down.");
        h.night = n;
        if (!h.merged && !p.isFlying() && !p.isGliding() && p.getFallDistance() > 10)
            remark(p, h, "fall", 15, "Whoa, careful!", "Aaah, that is a long way down!", "Hold on!");
        if (tick % 20 != 0 || p.isDead()) return;
        if (h.merged && tiny(p.getUniqueId())) return;                    // nothing notices us anyway
        LivingEntity worst = null;                                        // danger close by: one sentence in chat
        int count = 0;
        double best = Double.MAX_VALUE;
        for (Entity e : p.getNearbyEntities(10, 5, 10))
            if (foe(e) && e instanceof LivingEntity le) {
                count++;
                double d = e.getLocation().distanceSquared(p.getLocation());
                if (d < best) { best = d; worst = le; }
            }
        if (worst != null)
            remark(p, h, "danger", 20, count >= 3 ? "Watch out, " + count + " monsters around us!"
                    : "Watch out, " + worst.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ') + " " + side(p, worst) + "!");
        Block feet = p.getLocation().getBlock();
        for (int x = -2; x <= 2; x++) for (int y = -2; y <= 0; y++) for (int z = -2; z <= 2; z++)
            if (feet.getRelative(x, y, z).getType() == Material.LAVA) { remark(p, h, "lava", 45, "Watch out, lava!"); return; }
    }

    // --- Habits: what it does when there is nothing to do ---------------------------------------------------

    /** Called while it has nobody to fight and no job. True while a habit keeps it from walking back to the owner. */
    private boolean habits(Player p, H h) {
        Wolf w = h.w;
        if (h.foe != null) { h.idle = null; return false; }               // busy shooting
        Location at = p.getLocation();
        boolean still = h.ownerAt != null && h.ownerAt.getWorld() == at.getWorld() && h.ownerAt.distanceSquared(at) < 2.25;
        h.look = null;
        if (still) h.calm++;
        else {                                                            // the owner is on the move: only a picked flower is still delivered
            h.ownerAt = at;
            h.calm = 0;
            if (h.idle != null && !h.idle.equals("give")) h.idle = null;
        }
        long now = System.currentTimeMillis();
        if (h.idle == null) {
            if (!still || h.calm < 20 || now < h.nextIdle || p.isSleeping() || h.dance > 0 || flag(p.getUniqueId(), "quiet", false)) return false;
            h.nextIdle = now + 40_000 + ThreadLocalRandom.current().nextInt(50_000);
            h.idleStep = 0;
            List<String> can = new ArrayList<>();
            Block c = w.getLocation().getBlock(), flower = null, fire = null;
            for (int x = -6; x <= 6; x++) for (int y = -2; y <= 2; y++) for (int z = -6; z <= 6; z++) {
                Block b = c.getRelative(x, y, z);
                if (flower == null && Tag.SMALL_FLOWERS.isTagged(b.getType())) flower = b;
                if (fire == null && (b.getType() == Material.CAMPFIRE || b.getType() == Material.SOUL_CAMPFIRE)) fire = b;
            }
            Entity animal = null;
            for (Entity e : w.getNearbyEntities(8, 3, 8)) if (e instanceof Animals && ownerOf(e) == null) { animal = e; break; }
            World world = w.getWorld();
            boolean sky = world.getEnvironment() == World.Environment.NORMAL && c.getLightFromSky() > 12;
            if (flower != null) can.add("flower");
            if (fire != null) can.add("campfire");
            if (animal != null) can.add("pet");
            if (sky && !world.hasStorm() && world.getTime() > 11800 && world.getTime() < 12800) can.add("sunset");
            if ((sky && world.hasStorm()) || c.getType() == Material.WATER) can.add("puddle");
            can.add("stroll");                                            // a few slow steps, when there is nothing else
            h.idle = can.get(ThreadLocalRandom.current().nextInt(can.size()));
            h.idleLoc = h.idle.equals("flower") ? flower.getLocation().add(0.5, 0, 0.5) : h.idle.equals("campfire") ? fire.getLocation().add(0.5, 0, 0.5)
                    : h.idle.equals("stroll") ? at.clone().add(ThreadLocalRandom.current().nextInt(-4, 5), 0, ThreadLocalRandom.current().nextInt(-4, 5)) : null;
            h.idleEnt = animal;
        }
        Location goal = h.idle.equals("give") ? at : h.idle.equals("pet") ? (h.idleEnt.isValid() ? h.idleEnt.getLocation() : null) : h.idleLoc;
        if (goal != null && goal.getWorld() == w.getWorld() && dist2(w, goal) > 2.5 * 2.5 && h.idleStep < 16) {    // on its way
            w.getPathfinder().moveTo(goal, h.idle.equals("stroll") ? 0.6 : 0.85);
            h.idleStep++;
            return true;
        }
        switch (h.idle) {
            case "stroll" -> { w.getPathfinder().stopPathfinding(); h.idle = null; }
            case "flower" -> {
                Block b = h.idleLoc.getBlock();
                if (!Tag.SMALL_FLOWERS.isTagged(b.getType())) { h.idle = null; return false; }
                h.gift = new ItemStack(b.getType());
                crack(b);
                b.setType(Material.AIR);
                h.m.swingMainHand();
                h.idle = "give";
                h.idleStep = 0;
            }
            case "give" -> {
                if (h.gift != null) give(p, h.gift);
                h.gift = null;
                h.m.swingMainHand();
                remark(p, h, "flower", 0, "I picked this for you.", "Look what I found! It is for you.", "Here, " + nick(p) + ". It made me think of you.");
                h.idle = null;
            }
            case "pet" -> {
                if (goal != null) {
                    h.m.swingMainHand();
                    p.spawnParticle(Particle.HEART, goal.clone().add(0, h.idleEnt.getHeight() + 0.3, 0), 3, 0.2, 0.2, 0.2);
                    String kind = h.idleEnt.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
                    remark(p, h, "pet", 0, "Good " + kind + ".", "Who is a good " + kind + "? You are.", "I like this " + kind + ".");
                }
                h.idle = null;
            }
            case "campfire" -> {
                h.look = h.idleLoc;
                if (h.idleStep < 100) { h.idleStep = 100; remark(p, h, "fire", 0, "Nice and warm.", "I could stand here all night.", "Fires are the best."); }
                if (++h.idleStep > 116) h.idle = null;                    // eight seconds by the fire
                return true;
            }
            case "sunset" -> {
                h.look = w.getLocation().add(-40, 8, 0);                  // the sun sets in the west
                if (h.idleStep < 100) { h.idleStep = 100; remark(p, h, "sunset", 0, "Look at that sunset.", "I love this time of day.", "Pretty, isn't it?"); }
                if (++h.idleStep > 116) h.idle = null;
                return true;
            }
            default -> {                                                  // puddle
                if (h.idleStep < 100) { h.idleStep = 100; remark(p, h, "puddle", 0, "Splash!", "I love the rain!", "Puddles!"); }
                if (w.isOnGround() || w.isInWater()) w.setVelocity(new Vector(0, 0.42, 0));
                if (++h.idleStep > 108) h.idle = null;
                return true;
            }
        }
        return h.idle != null;
    }

    /** Helpers that stand next to each other with nothing to do say hello and tease each other a little. Both owners
     *  see both lines. */
    private void banter() {
        long now = System.currentTimeMillis();
        List<UUID> ids = new ArrayList<>(helpers.keySet());
        for (int i = 0; i < ids.size(); i++) for (int j = i + 1; j < ids.size(); j++) {
            H a = helpers.get(ids.get(i)), b = helpers.get(ids.get(j));
            Player pa = Bukkit.getPlayer(ids.get(i)), pb = Bukkit.getPlayer(ids.get(j));
            if (pa == null || pb == null || a.merged || b.merged || a.job != Job.NONE || b.job != Job.NONE || now < a.banter || now < b.banter) continue;
            if (a.w.getTarget() != null || b.w.getTarget() != null || dist2(a.w, b.w.getLocation()) > 6 * 6) continue;
            if (ThreadLocalRandom.current().nextBoolean()) { H t = a; a = b; b = t; Player tp = pa; pa = pb; pb = tp; }
            a.banter = b.banter = now + 90_000 + ThreadLocalRandom.current().nextInt(90_000);
            String[] line = BANTER[ThreadLocalRandom.current().nextInt(BANTER.length)];
            String na = name(pa.getUniqueId()), nb = name(pb.getUniqueId());
            for (int k = 0; k < 2; k++) {
                String text = "<" + (k == 0 ? na : nb) + "> " + line[k].replace("{a}", na).replace("{b}", nb).replace("{oa}", pa.getName()).replace("{ob}", pb.getName());
                for (Player to : List.of(pa, pb)) if (!flag(to.getUniqueId(), "quiet", false)) to.sendMessage(Component.text(text));
            }
            a.dance = Math.max(a.dance, 65);                              // a wave
            b.dance = Math.max(b.dance, 65);
        }
    }

    /** It is happy for you: treasure found, and a big build growing. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOwnerBreak(BlockBreakEvent e) {
        H h = helpers.get(e.getPlayer().getUniqueId());
        String t = e.getBlock().getType().name();
        if (h == null || !(t.endsWith("DIAMOND_ORE") || t.endsWith("EMERALD_ORE") || t.equals("ANCIENT_DEBRIS"))) return;
        String what = t.equals("ANCIENT_DEBRIS") ? "Ancient debris" : t.endsWith("DIAMOND_ORE") ? "Diamonds" : "Emeralds";
        remark(e.getPlayer(), h, "treasure", 20, what + "! We are rich!", what + "!! You found them!", "Yes! " + what + "!");
        if (!h.merged && h.job == Job.NONE) h.dance = 50;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOwnerPlace(BlockPlaceEvent e) {
        H h = helpers.get(e.getPlayer().getUniqueId());
        if (h == null) return;
        long now = System.currentTimeMillis();
        if (now - h.placedSince > 300_000) { h.placedSince = now; h.placed = 0; }
        if (++h.placed != 120) return;                                    // 120 blocks within five minutes
        remark(e.getPlayer(), h, "build", 600, "Wow, look at what you are building!", "This is turning into something great, " + nick(e.getPlayer()) + ".", "I love it. Keep going!");
        if (!h.merged && h.job == Job.NONE) h.dance = Math.max(h.dance, 70);     // a round of applause
    }

    /** What the helper can see, for the chat bridge: so it can have an opinion on the house and knows where it is. */
    private String context(Player p) {
        Location l = p.getLocation();
        World w = l.getWorld();
        Map<String, Integer> blocks = new HashMap<>(), mobs = new HashMap<>();
        Block c = l.getBlock();
        for (int x = -10; x <= 10; x++) for (int y = -4; y <= 8; y++) for (int z = -10; z <= 10; z++) {
            Material m = c.getRelative(x, y, z).getType();
            if (!m.isAir()) blocks.merge(m.name().toLowerCase(Locale.ROOT), 1, Integer::sum);
        }
        for (Entity e : p.getNearbyEntities(16, 8, 16))
            if (e instanceof LivingEntity && ownerOf(e) == null) mobs.merge(e instanceof Player pl ? "player " + pl.getName() : e.getType().name().toLowerCase(Locale.ROOT), 1, Integer::sum);
        StringBuilder sb = new StringBuilder("world " + w.getName() + ", biome " + c.getBiome().getKey().getKey() + ", height y " + l.getBlockY()
                + (w.getEnvironment() == World.Environment.NORMAL ? ", " + (w.isDayTime() ? "day" : "night") + (w.hasStorm() ? ", raining" : "") : "")
                + ", the player has " + (int) Math.ceil(p.getHealth() / 2) + " of 10 hearts and holds " + p.getInventory().getItemInMainHand().getType().name().toLowerCase(Locale.ROOT)
                + ", you call the player " + (conf.contains(path(p.getUniqueId(), "nick")) ? nick(p) : p.getName() + " (no nickname yet)") + ". Blocks within 10 blocks:");
        blocks.entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue()).limit(14).forEach(en -> sb.append(' ').append(en.getValue()).append(' ').append(en.getKey()).append(','));
        sb.append(" creatures nearby:");
        if (mobs.isEmpty()) sb.append(" none");
        mobs.entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue()).limit(6).forEach(en -> sb.append(' ').append(en.getValue()).append(' ').append(en.getKey()).append(','));
        return sb.toString().replace('|', ' ');
    }

    // --- The bag, picking things up, torches -------------------------------------------------------------

    private Inventory bag(UUID id) {
        return bags.computeIfAbsent(id, k -> {
            Inventory inv = Bukkit.createInventory(new Bag(id), 27, Component.text("Helper bag"));
            ItemStack[] items = load(id, "bag");
            if (items != null) for (int i = 0; i < Math.min(items.length, 27); i++) inv.setItem(i, items[i]);
            return inv;
        });
    }

    // --- One bag and one set of gear for each group of worlds, as the players have (Multiverse-Inventories) ---------

    private long groupsAt = -1;
    private final Map<String, List<String>> groups = new java.util.LinkedHashMap<>();

    /** The inventory group a world belongs to; a world in no group is a group of its own. */
    private String group(World w) {
        File f = new File(plugin.getDataFolder().getParentFile(), "Multiverse-Inventories/groups.yml");
        if (f.lastModified() != groupsAt) {
            groupsAt = f.lastModified();
            groups.clear();
            ConfigurationSection sec = YamlConfiguration.loadConfiguration(f).getConfigurationSection("groups");
            if (sec != null) for (String g : sec.getKeys(false)) groups.put(g, sec.getStringList(g + ".worlds"));
        }
        String name = w.getName();
        for (Map.Entry<String, List<String>> e : groups.entrySet()) for (String m : e.getValue()) {
            try {
                if (m.startsWith("r=") ? name.matches(m.substring(2)) : m.equalsIgnoreCase(name)) return e.getKey().replace('.', '_');
            } catch (RuntimeException ignored) { }
        }
        return name.replace('.', '_');
    }

    /** The group whose bag and gear the helper has now. What it had before there were groups stays where its home is. */
    private String grp(UUID id) {
        String g = conf.getString(path(id, "group"));
        if (g != null) return g;
        Location hm = home(id);
        Player p = Bukkit.getPlayer(id);
        g = hm != null && hm.getWorld() != null ? group(hm.getWorld()) : p != null ? group(p.getWorld()) : "default";
        for (String k : new String[] {"bag", "gear"}) {
            String old = conf.getString(path(id, k));
            if (old != null) { conf.set(path(id, "inv." + g + "." + k), old); conf.set(path(id, k), null); }
        }
        conf.set(path(id, "group"), g);
        save();
        return g;
    }

    private String inv(UUID id, String k) { return k.equals("bag") || k.equals("gear") ? "inv." + grp(id) + "." + k : k; }

    /** Its owner is in another group of worlds now: what it carried and wore stays behind, and what it had here is back. */
    private void regroup(UUID id, World w) {
        String to = group(w);
        if (to.equals(grp(id))) return;
        Inventory old = bags.remove(id);
        if (old != null) {
            for (org.bukkit.entity.HumanEntity v : new ArrayList<>(old.getViewers())) v.closeInventory();
            store(id, "bag", old.getContents());
        }
        dirty.remove(id);
        gear.remove(id);
        conf.set(path(id, "group"), to);
        save();
        H h = helpers.get(id);
        if (h != null) dress(id, h);
    }

    /** Home, when it lies in the group of worlds the helper is in now. */
    private Location homeNow(UUID id) {
        Location hm = home(id);
        return hm != null && hm.getWorld() != null && group(hm.getWorld()).equals(grp(id)) ? hm : null;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldGroup(org.bukkit.event.player.PlayerChangedWorldEvent e) { regroup(e.getPlayer().getUniqueId(), e.getPlayer().getWorld()); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoinGroup(PlayerJoinEvent e) { regroup(e.getPlayer().getUniqueId(), e.getPlayer().getWorld()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuitGroup(PlayerQuitEvent e) {                             // away it works at home, with what it has there
        Location hm = home(e.getPlayer().getUniqueId());
        if (hm != null && hm.getWorld() != null) regroup(e.getPlayer().getUniqueId(), hm.getWorld());
    }

    private ItemStack[] load(UUID id, String k) {
        k = inv(id, k);
        String s = conf.getString(path(id, k));
        if (s == null || s.isEmpty()) return null;
        try {
            return ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(s));
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Could not read the helper " + k + " of " + id + ": " + e.getMessage());
            return null;
        }
    }

    private void store(UUID id, String k, ItemStack[] items) {
        conf.set(path(id, inv(id, k)), Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(items)));
    }

    private void flushBags() {
        if (dirty.isEmpty()) return;
        for (UUID id : dirty) if (bags.containsKey(id)) store(id, "bag", bags.get(id).getContents());
        dirty.clear();
        save();
    }

    @EventHandler
    public void onBagClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof Bag b)) return;
        dirty.add(b.owner());
        flushBags();
    }

    /** Merged, what it gets goes in your own inventory first and in its bag only when yours is full. Returns what did not fit. */
    private Map<Integer, ItemStack> stash(Player p, H h, ItemStack... items) {
        Map<Integer, ItemStack> rest = new HashMap<>();
        List<ItemStack> left = new ArrayList<>();
        if (h.merged) for (ItemStack r : p.getInventory().addItem(items).values()) left.add(r);
        else left.addAll(Arrays.asList(items));
        if (left.isEmpty()) return rest;
        dirty.add(p.getUniqueId());
        return bag(p.getUniqueId()).addItem(left.toArray(new ItemStack[0]));
    }

    /** Puts items in the bag (merged: in your inventory first); what does not fit is dropped at the helper's feet. */
    private void keep(Player p, H h, Collection<ItemStack> items) {
        if (items.isEmpty()) return;
        dirty.add(p.getUniqueId());
        for (ItemStack rest : stash(p, h, items.toArray(new ItemStack[0])).values()) {
            h.w.getWorld().dropItem(h.w.getLocation(), rest);
            remark(p, h, "full", 60, "My bag is full!", "I can't carry any more.");
        }
    }

    /** Takes what lies within three blocks (the whole field while farming). Never something a player threw down: that is theirs. */
    private void pickUp(Player p, H h) {
        Inventory inv = bag(p.getUniqueId());
        boolean took = false;
        int reach = h.job == Job.FARM ? FARM_RANGE + 1 : 3;
        for (Entity e : h.w.getNearbyEntities(reach, 3, reach)) {
            if (!(e instanceof Item it) || !it.isValid() || it.getPickupDelay() > 0 || it.getThrower() != null) continue;
            ItemStack stack = it.getItemStack();
            int before = stack.getAmount();
            Map<Integer, ItemStack> rest = stash(p, h, stack.clone());
            if (rest.isEmpty()) it.remove();
            else {
                ItemStack r = rest.values().iterator().next();
                if (r.getAmount() == before) { remark(p, h, "full", 60, "My bag is full!", "I can't carry any more."); continue; }
                it.setItemStack(r);
            }
            took = true;
        }
        if (took) {
            dirty.add(p.getUniqueId());
            p.playSound(h.w.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.25f, 1.4f);
        }
    }

    private void torch(Wolf w) {
        Block b = w.getLocation().getBlock();
        if (!b.getType().isAir() || b.getLightLevel() > 7 || !b.getRelative(BlockFace.DOWN).getType().isOccluding()) return;
        b.setType(Material.TORCH);
        b.getWorld().playSound(b.getLocation(), Sound.BLOCK_WOOD_PLACE, 0.6f, 1f);
    }

    // --- Home, chests: emptying the bag and fetching things ---------------------------------------------------

    private Location home(UUID id) {
        return place(conf.getString(path(id, "home")));
    }

    private static String spot(Location l) {
        return l.getX() + " " + l.getY() + " " + l.getZ() + " " + l.getYaw() + " " + l.getWorld().getName();
    }

    private static Location place(String s) {
        if (s == null) return null;
        String[] a = s.split(" ", 5);
        World w = a.length < 5 ? null : Bukkit.getWorld(a[4]);
        return w == null ? null : new Location(w, Double.parseDouble(a[0]), Double.parseDouble(a[1]), Double.parseDouble(a[2]), Float.parseFloat(a[3]), 0);
    }

    /** The chests, barrels and shulker boxes within eight blocks of a place (the chunks are loaded if they are not). */
    private static List<Inventory> stores(Location around) { return stores(around, 8); }

    private static List<Inventory> stores(Location around, int r) {
        List<Inventory> out = new ArrayList<>();
        if (around == null) return out;
        Set<Object> seen = new HashSet<>();
        World w = around.getWorld();
        int bx = around.getBlockX(), bz = around.getBlockZ();
        for (int cx = (bx - r) >> 4; cx <= (bx + r) >> 4; cx++) for (int cz = (bz - r) >> 4; cz <= (bz + r) >> 4; cz++)
            for (BlockState st : w.getChunkAt(cx, cz).getTileEntities(b -> b.getLocation().distanceSquared(around) <= (r + 0.5) * (r + 0.5), false))
                if (st instanceof Container c && (st instanceof Chest || st instanceof Barrel || st instanceof ShulkerBox)) {
                    Inventory inv = c.getInventory();
                    Location l = inv.getLocation();                        // both halves of a double chest give the same one
                    if (seen.add(l == null ? inv : l.toVector())) out.add(inv);
                }
        return out;
    }

    private void zip(Player p, H h) {                                      // it is gone and back in a blink
        Location l = h.w.getLocation().add(0, 1, 0);
        p.spawnParticle(Particle.PORTAL, l, 40, 0.3, 0.6, 0.3, 0.4);
        p.playSound(l, Sound.ENTITY_ENDERMAN_TELEPORT, 0.3f, 1.6f);
    }

    /** Moves everything in one inventory into the given chests, to a chest that already has that kind first.
     *  Returns how many things moved. */
    private static int store(Inventory from, List<Inventory> to) {
        int moved = 0;
        for (int i = 0; i < from.getSize(); i++) {
            ItemStack it = from.getItem(i);
            if (empty(it)) continue;
            int before = it.getAmount();
            for (int pass = 0; pass < 2 && it.getAmount() > 0; pass++)
                for (Inventory inv : to) {
                    if (inv == from) continue;
                    if (pass == 0 && !inv.contains(it.getType())) continue;
                    Map<Integer, ItemStack> rest = inv.addItem(it.clone());
                    it.setAmount(rest.isEmpty() ? 0 : rest.values().iterator().next().getAmount());
                    if (it.getAmount() == 0) break;
                }
            moved += before - it.getAmount();
            from.setItem(i, it.getAmount() > 0 ? it : null);
        }
        return moved;
    }

    /** The chests next to the helper that do not already belong to home. */
    private List<Inventory> awayChests(Location here, List<Inventory> home) {
        Set<Object> at = new HashSet<>();
        for (Inventory inv : home) at.add(inv.getLocation() == null ? inv : inv.getLocation().toVector());
        List<Inventory> out = new ArrayList<>();
        for (Inventory inv : stores(here))
            if (!at.contains(inv.getLocation() == null ? inv : inv.getLocation().toVector()) && !inv.isEmpty()
                    && !(storage != null && inv.getHolder(false) instanceof Barrel && storage.barrelNear(inv.getLocation(), 1))) out.add(inv);   // not the magic barrel itself
        return out;
    }

    /** Empties the bag into the chests at home. When asked to, it also empties the chests next to it and carries
     *  that home too. Things go to a chest that already has that kind first. Quiet (no complaints) when the helper
     *  does it by itself, and then it leaves other chests alone. */
    private boolean unload(Player p, H h, boolean auto) {
        UUID id = p.getUniqueId();
        Location hm = homeLinked(id, h.w.getWorld());
        Location mb = myBarrel(id, h.w.getWorld());
        if (mb != null) {                                                 // your own magic barrel: all of it in your own store (Mine), never the chests
            List<Inventory> here = auto ? List.of() : awayChests(h.w.getLocation(), List.of());
            if (!auto && !h.merged && mb.getWorld() == h.w.getWorld() && (!bag(id).isEmpty() || !here.isEmpty())) {      // asked to: it walks there with it all
                if (h.hold || h.errand != null) release(h);
                Errand e = new Errand();
                e.id = id; e.at = mb; e.from = List.of(); e.put = true; e.mine = true; e.where = "your magic barrel";
                for (Inventory inv : here) for (int i = 0; i < inv.getSize(); i++) if (!empty(inv.getItem(i))) { e.load.add(inv.getItem(i).clone()); inv.setItem(i, null); }
                if (!here.isEmpty()) swing(p, h);
                h.errand = e;
                h.w.setSitting(false);
                say(p, "I will take my bag" + (here.isEmpty() ? "" : " and the chests here") + " to your magic barrel at " + mb.getBlockX() + " " + mb.getBlockY() + " " + mb.getBlockZ() + ".");
                return true;
            }
            int moved = storage.putMine(id, bag(id));
            for (Inventory inv : here) moved += storage.putMine(id, inv);
            if (moved == 0) {
                if (!auto) say(p, "My bag is empty already, and there is no chest here with anything in it.");
                return false;
            }
            dirty.add(id);
            zip(p, h);
            say(p, "I put " + moved + (moved == 1 ? " thing" : " things") + " from my bag" + (here.isEmpty() ? "" : " and the chests here") + " in your own store in the magic barrel (the Mine tab).");
            return true;
        }
        List<Inventory> to = stores(hm, HOME);
        if (to.isEmpty()) {
            if (!auto) say(p, home(id) == null ? "I don't know where home is. Stand at home and say sethome."
                    : hm == null ? "Home is in another world. I only carry things home from its own nether and end."
                    : "There is no chest within 100 blocks of home.");
            return false;
        }
        Inventory bag = bag(id);
        List<Inventory> here = auto ? List.of() : awayChests(h.w.getLocation(), to);
        if (bag.isEmpty() && here.isEmpty()) {
            if (!auto) say(p, "My bag is empty already, and there is no chest here with anything in it.");
            return false;
        }
        String extra = here.isEmpty() ? "" : " and what was in the " + (here.size() == 1 ? "chest" : here.size() + " chests") + " here";
        boolean far = hm.getWorld() != h.w.getWorld();                    // home is in the overworld of this nether or end: it zips there and back
        if (!auto && !h.merged && !far && to.get(0).getLocation() != null) {      // asked to: it empties the chests here and walks home with it all
            if (h.hold || h.errand != null) release(h);
            Errand e = new Errand();
            e.id = id; e.at = to.get(0).getLocation().getBlock().getLocation(); e.from = to; e.put = true; e.where = "the chests at home";
            for (Inventory inv : here) {
                for (int i = 0; i < inv.getSize(); i++) if (!empty(inv.getItem(i))) { e.load.add(inv.getItem(i).clone()); inv.setItem(i, null); }
            }
            if (!here.isEmpty()) swing(p, h);
            h.errand = e;
            h.w.setSitting(false);
            say(p, "I will take my bag" + extra + " home and put it in the chests there.");
            return true;
        }
        int moved = store(bag, to);
        for (Inventory inv : here) moved += store(inv, to);
        if (moved == 0) {
            if (!auto) say(p, "The chests at home are full.");
            return false;
        }
        dirty.add(id);
        zip(p, h);
        say(p, "I put " + moved + (moved == 1 ? " thing" : " things") + " from my bag" + extra + " in the chests at home.");
        return true;
    }

    /** An errand done the way a player would: walk to the chest (a teleport when it is far or there is no way), open
     *  it, take or put, and for a fetch walk back to the owner and hand it over. */
    private static final int NEAR_PLACE = 24;                                // how far around a remembered place its chests count

    private static final class Errand {
        UUID id; Location at; List<Inventory> from; Material want; int amount, phase, ticks; String where; boolean put, mine;
        final List<ItemStack> load = new ArrayList<>();
    }

    private static Location standBy(Location chest) {
        Block c = chest.getBlock();
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}}) for (int dy = 0; dy >= -1; dy--) {
            Block b = c.getRelative(d[0], dy, d[1]);
            if (b.isPassable() && b.getRelative(BlockFace.UP).isPassable() && b.getRelative(BlockFace.DOWN).getType().isSolid()) return b.getLocation().add(0.5, 0, 0.5);
        }
        return c.getLocation().add(0.5, 1, 0.5);
    }

    private void lid(Location chest, boolean open) {
        if (chest.getBlock().getState() instanceof org.bukkit.block.Lidded l) { if (open) l.open(); else l.close(); }
    }

    /** A world and its own nether and end belong together: hope, hope_nether, hope_the_end. */
    private static String realm(World w) { return w.getName().replaceFirst("_(nether|the_end)$", ""); }

    /** Home, when it lies in this world or in the overworld, nether or end that belongs to it. */
    private Location homeLinked(UUID id, World w) {
        Location hm = home(id);
        return hm != null && hm.getWorld() != null && realm(hm.getWorld()).equals(realm(w)) ? hm : null;
    }

    /** Home, when it lies in this world: nothing is carried from one world to another. */
    private Location homeIn(UUID id, World w) {
        Location hm = home(id);
        return hm != null && hm.getWorld() == w ? hm : null;
    }

    private void errand(Player p, H h) {
        Errand e = h.errand;
        Wolf w = h.w;
        if (p.getWorld() != e.at.getWorld()) {                            // its owner went to another world: what it carries goes back where it came from
            if (e.mine && storage != null) storage.putMine(e.id, e.load);
            else for (ItemStack it : e.load) { Map<Integer, ItemStack> rest = Map.of(0, it); for (Inventory inv : e.from) { if (rest.isEmpty()) break; rest = inv.addItem(rest.values().iterator().next()); }
                for (ItemStack r : rest.values()) e.at.getWorld().dropItemNaturally(e.at.clone().add(0.5, 1, 0.5), r); }
            e.load.clear();
            h.errand = null;
            return;
        }
        e.ticks++;
        if (e.phase == 0 || e.phase == 2) {                               // on the way: to the chest, or back to its owner
            Location goal = e.phase == 0 ? standBy(e.at) : p.getLocation();
            double d = dist2(w, goal);
            if (d <= (e.phase == 0 ? 2.2 * 2.2 : 3 * 3)) { w.getPathfinder().stopPathfinding(); e.phase++; e.ticks = 0; return; }
            if (d > 40 * 40 || e.ticks > 24) { w.teleport(goal); e.ticks = 0; }                    // far, or no way there in twelve seconds
            else w.getPathfinder().moveTo(goal, 1.25);
            return;
        }
        if (e.phase == 1) {                                               // at the chest
            h.look = e.at.clone().add(0.5, 0.5, 0.5);
            if (e.ticks == 1) { lid(e.at, true); swing(p, h); return; }
            if (e.ticks < 3) return;
            lid(e.at, false);
            h.look = null;
            if (e.put && e.mine && storage != null) {                     // your own magic barrel
                int moved = storage.putMine(e.id, bag(e.id)) + storage.putMine(e.id, e.load);
                e.load.clear();
                dirty.add(e.id);
                h.errand = null;
                say(p, moved == 0 ? "There was nothing to put away." : "I put " + moved + (moved == 1 ? " thing" : " things") + " in your own store in the magic barrel (the Mine tab), at " + e.at.getBlockX() + " " + e.at.getBlockY() + " " + e.at.getBlockZ() + ".");
                return;
            }
            if (e.put) {
                int moved = store(bag(e.id), e.from);
                for (ItemStack it : e.load) {                             // what it carried from the chests out there
                    int before = it.getAmount();
                    Map<Integer, ItemStack> rest = Map.of(0, it);
                    for (int pass = 0; pass < 2 && !rest.isEmpty(); pass++) for (Inventory inv : e.from) {
                        if (rest.isEmpty()) break;
                        if (pass == 0 && !inv.contains(it.getType())) continue;
                        rest = inv.addItem(rest.values().iterator().next());
                    }
                    if (!rest.isEmpty()) rest = bag(e.id).addItem(rest.values().iterator().next());     // home is full: it keeps it in the bag
                    for (ItemStack r : rest.values()) e.at.getWorld().dropItemNaturally(standBy(e.at), r);
                    moved += before - rest.values().stream().mapToInt(ItemStack::getAmount).sum();
                }
                e.load.clear();
                dirty.add(e.id);
                h.errand = null;
                say(p, moved == 0 ? "The chests there are full." : "I put " + moved + (moved == 1 ? " thing" : " things") + " in " + e.where + ", at " + e.at.getBlockX() + " " + e.at.getBlockY() + " " + e.at.getBlockZ() + ".");
                return;
            }
            int got = 0;
            for (Inventory inv : e.from) for (int i = 0; i < inv.getSize() && got < e.amount; i++) {
                ItemStack it = inv.getItem(i);
                if (empty(it) || it.getType() != e.want) continue;
                int take = Math.min(e.amount - got, it.getAmount());
                ItemStack out = it.clone();
                out.setAmount(take);
                e.load.add(out);
                it.setAmount(it.getAmount() - take);
                inv.setItem(i, it.getAmount() > 0 ? it : null);
                got += take;
            }
            if (got == 0) { h.errand = null; say(p, "Someone was there before me: the " + word(e.want) + " is gone from " + e.where + "."); return; }
            wield(h, e.want);
            e.phase = 2;
            e.ticks = 0;
            return;
        }
        int got = 0;                                                      // back: here you go
        for (ItemStack it : e.load) { got += it.getAmount(); give(p, it); }
        e.load.clear();
        h.errand = null;
        swing(p, h);
        zip(p, h);
        say(p, "Here you go: " + got + " " + word(e.want) + ", from " + e.where + " (" + e.at.getBlockX() + " " + e.at.getBlockY() + " " + e.at.getBlockZ() + ").");
    }

    /** "20 iron", "iron_ingot 20", "diamonds", "bones from mobfarm": brings it from the bag, the chests here, the
     *  chests at home or the chests at a place it remembers. It walks there and back with it. */
    private boolean fetch(Player p, H h, String what, boolean quiet) {
        UUID id = p.getUniqueId();
        String placeName = null;
        Location placeAt = null;
        Matcher fm = Pattern.compile("(.*?)\\s+(?:from|fra)\\s+(?:the\\s+|my\\s+)?(.+?)(?:\\s+(?:and|og)\\s+.*)?", Pattern.CASE_INSENSITIVE).matcher(what.trim());
        if (fm.matches()) {
            placeName = fm.group(2).trim();
            placeAt = placeNamed(id, placeName);
            if (placeAt == null) { if (!quiet) say(p, "I don't know a place called " + placeName + ". Stand there and say remember " + placeName + "."); return false; }
            if (placeAt.getWorld() != p.getWorld()) { if (!quiet) say(p, placeName + " is in another world (" + placeAt.getWorld().getName() + "). I carry nothing from one world to another."); return false; }
            what = fm.group(1);
        }
        int amount = 64;
        Matcher n = NUMBER.matcher(what);
        if (n.find()) { amount = Math.max(1, Integer.parseInt(n.group(1))); what = what.substring(0, n.start()) + what.substring(n.end()); }
        String term = what.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
        if (term.isEmpty()) { if (!quiet) p.sendMessage("Usage: /helper fetch <item> [amount]"); return false; }
        List<Inventory> from = new ArrayList<>();
        Map<String, Location> spots = new java.util.LinkedHashMap<>();    // the places it remembers, nearest first
        ConfigurationSection sec = conf.getConfigurationSection(path(id, "places"));
        if (sec != null && placeAt == null) {
            List<String> keys = new ArrayList<>(sec.getKeys(false));
            keys.removeIf(k -> place(sec.getString(k)) == null || place(sec.getString(k)).getWorld() != p.getWorld());      // only the places in this world
            keys.sort(Comparator.comparingDouble(k -> dist2(h.w, place(sec.getString(k)))));
            for (String k : keys) spots.put(k, place(sec.getString(k)));
        }
        if (placeAt != null) from.addAll(stores(placeAt, NEAR_PLACE));
        else {                                                            // its bag, the chests here, at home, and at every place it remembers
            from.add(bag(id)); from.addAll(stores(h.w.getLocation())); from.addAll(stores(homeIn(id, p.getWorld()), HOME));
            for (Location l : spots.values()) for (Inventory inv : stores(l, NEAR_PLACE)) if (!from.contains(inv)) from.add(inv);
        }
        Material want = null;
        search:
        for (String t : term.endsWith("S") ? new String[] {term, term.substring(0, term.length() - 1)} : new String[] {term}) {
            for (Inventory inv : from) for (ItemStack it : inv.getContents()) {
                if (empty(it)) continue;
                String name = it.getType().name();
                if (name.equals(t)) { want = it.getType(); break search; }
                if (name.contains(t) && (want == null || name.length() < want.name().length())) want = it.getType();
            }
            if (want != null) break;
        }
        if (want == null) {
            if (!quiet) say(p, "I can't find any " + term.toLowerCase(Locale.ROOT).replace('_', ' ') + (placeAt != null ? " in the chests at " + placeName + "." : " in my bag, the chests here, the chests at home or at the places I remember in this world."));
            return false;
        }
        Material kind = want;
        if (!h.merged) {
            if (placeAt == null && bag(id).contains(kind)) from = new ArrayList<>(List.of(bag(id)));        // it carries it already: here you go
            else for (Inventory inv : from) {
                if (!inv.contains(kind) || inv.getLocation() == null || inv == bag(id)) continue;
                if (h.hold) release(h);
                Errand e = new Errand();
                e.id = id; e.at = inv.getLocation().getBlock().getLocation(); e.from = new ArrayList<>(from); e.from.remove(bag(id)); e.want = kind; e.amount = amount;
                Location hm = home(id);
                String near = null;
                for (Map.Entry<String, Location> sp : spots.entrySet()) if (near == null && sp.getValue().getWorld() == e.at.getWorld() && sp.getValue().distanceSquared(e.at) <= (NEAR_PLACE + 1) * (NEAR_PLACE + 1)) near = sp.getKey();
                e.where = placeAt != null ? "the chests at " + placeName : dist2(h.w, e.at) <= 12 * 12 ? "the chest here" : near != null ? "the chests at " + near.replace('_', ' ') : dist2(h.w, e.at) <= 12 * 12 ? "the chest here" : hm != null && hm.getWorld() == e.at.getWorld() && hm.distanceSquared(e.at) <= HOME * HOME ? "the chests at home" : "a chest";
                if (h.errand != null) release(h);
                h.errand = e;
                h.w.setSitting(false);
                say(p, "I will get the " + word(kind) + " from " + e.where + ". Back in a moment.");
                return true;
            }
        }
        int got = 0;
        for (Inventory inv : from)
            for (int i = 0; i < inv.getSize() && got < amount; i++) {
                ItemStack it = inv.getItem(i);
                if (empty(it) || it.getType() != want) continue;
                int take = Math.min(amount - got, it.getAmount());
                ItemStack out = it.clone();
                out.setAmount(take);
                give(p, out);
                it.setAmount(it.getAmount() - take);
                inv.setItem(i, it.getAmount() > 0 ? it : null);
                got += take;
            }
        dirty.add(id);
        zip(p, h);
        say(p, "Here you go: " + got + " " + want.name().toLowerCase(Locale.ROOT).replace('_', ' ') + ".");
        return true;
    }

    // --- Places it remembers ---------------------------------------------------------------------------------

    private static String slug(String name) {
        return ARTICLE.matcher(name.trim().toLowerCase(Locale.ROOT)).replaceFirst("").replaceAll("[^\\p{L}\\p{N}]+", "_").replaceAll("^_|_$", "");
    }

    /** The saved place that fits the name best: the same name, or one that contains the other. "home" is home. */
    private Location placeNamed(UUID id, String name) {
        String want = slug(name);
        if (want.isEmpty()) return null;
        ConfigurationSection sec = conf.getConfigurationSection(path(id, "places"));
        if (sec != null) {
            if (sec.contains(want)) return place(sec.getString(want));
            for (String k : sec.getKeys(false)) if (k.contains(want) || want.contains(k)) return place(sec.getString(k));
        }
        return want.equals("home") || want.equals("hjem") ? home(id) : null;
    }

    private void travel(Player p, H h, Location to) {
        release(h);
        zip(p, h);
        p.teleport(to);
        h.w.teleport(to);
    }

    // --- Fighting style, scouting, the way back, games, treasure ---------------------------------------------

    private String style(UUID id) { return conf.getString(path(id, "style"), "sword"); }

    /** Bow: an arrow a second at its foe, from where it stands. Shield: what hunts the owner hunts the helper instead. */
    private void fightStyle(Player p, H h) {
        String style = style(p.getUniqueId());
        if (style.equals("shield") && tick % 20 == 0) {
            for (Entity e : p.getNearbyEntities(12, 6, 12))
                if (e instanceof Mob m && ownerOf(m) == null && m.getTarget() == p) m.setTarget(h.w);
            return;
        }
        if (!style.equals("bow")) { h.foe = null; return; }
        LivingEntity foe = h.foe;
        if (foe == null) return;
        if (foe.isDead() || !foe.isValid() || dist2(h.w, foe.getLocation()) > 28 * 28) { h.foe = null; h.look = null; return; }
        h.look = foe.getEyeLocation();
        if (tick % 20 != 0 || !h.m.hasLineOfSight(foe)) return;
        Location from = h.m.getEyeLocation();
        Vector v = foe.getEyeLocation().subtract(0, 0.3, 0).toVector().subtract(from.toVector());
        double far = v.length();
        v.normalize().multiply(2.4);
        v.setY(v.getY() + far * 0.012);                                   // a little up for the drop
        String owner = p.getUniqueId().toString();
        h.m.launchProjectile(Arrow.class, v, a -> {
            a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            a.setPersistent(false);
            a.getPersistentDataContainer().set(key, PersistentDataType.STRING, owner);   // onBite: its hit counts as the helper's
        });
        h.m.swingMainHand();
        from.getWorld().playSound(from, Sound.ENTITY_ARROW_SHOOT, 0.7f, 1.1f);
    }

    /** A hundred blocks the way the owner looks, in steps of 16: biomes, water, lava, structures and monsters. Only
     *  land that already exists is looked at. */
    private void scout(Player p, H h) {
        Location l = p.getLocation();
        Vector d = l.getDirection().setY(0);
        if (d.lengthSquared() < 0.01) d = new Vector(0, 0, 1);
        d.normalize();
        World w = l.getWorld();
        Registry<Structure> reg = RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE);
        List<String> seen = new ArrayList<>();
        Set<String> things = new java.util.LinkedHashSet<>();
        String biome = null;
        int monsters = 0;
        boolean water = false, lava = false;
        for (int dist = 16; dist <= 112; dist += 16) {
            int x = l.getBlockX() + (int) Math.round(d.getX() * dist), z = l.getBlockZ() + (int) Math.round(d.getZ() * dist);
            if (!w.isChunkGenerated(x >> 4, z >> 4)) { seen.add("land nobody has been to from " + dist + " blocks on"); break; }
            String b = w.getBlockAt(x, l.getBlockY(), z).getBiome().getKey().getKey().replace('_', ' ');
            if (!b.equals(biome)) { seen.add(b + (biome == null ? "" : " from " + dist + " blocks")); biome = b; }
            Material top = w.getHighestBlockAt(x, z).getType();
            if (top == Material.WATER && !water) { water = true; things.add("water at " + dist + " blocks"); }
            if (top == Material.LAVA && !lava) { lava = true; things.add("lava at " + dist + " blocks"); }
            for (GeneratedStructure gs : w.getStructures(x >> 4, z >> 4)) {
                NamespacedKey k = reg.getKey(gs.getStructure());
                if (k != null) things.add("a " + k.getKey().replace('_', ' '));
            }
            for (Entity e : w.getChunkAt(x >> 4, z >> 4).getEntities()) if (e instanceof Enemy) monsters++;
        }
        zip(p, h);
        say(p, "I ran " + direction(l, l.clone().add(d.clone().multiply(100))) + " and back. " + String.join(", then ", seen) + "."
                + (things.isEmpty() ? "" : " I saw " + String.join(", ", things) + ".") + (monsters > 0 ? " And " + monsters + (monsters == 1 ? " monster." : " monsters.") : " No monsters in sight."));
    }

    /** Remembers where the owner walks (a crumb every four blocks), and shows the way back while that is asked for. */
    private void crumbs(Player p, H h) {
        Location at = p.getLocation();
        if (h.trail < 0) {
            Location last = h.crumbs.isEmpty() ? null : h.crumbs.get(h.crumbs.size() - 1);
            if (last != null && (last.getWorld() != at.getWorld() || last.distanceSquared(at) > 60 * 60)) { h.crumbs.clear(); last = null; }   // teleported
            if (last == null || last.distanceSquared(at) > 16) h.crumbs.add(at);
            if (h.crumbs.size() > 800) h.crumbs.remove(0);
            return;
        }
        if (tick > h.trailEnd || h.trailTop > h.crumbs.size() || h.crumbs.get(h.trail).getWorld() != at.getWorld()) { h.trail = -1; return; }
        int near = h.trail;
        double best = Double.MAX_VALUE;
        for (int i = h.trail; i < h.trailTop; i++) {
            double d = h.crumbs.get(i).distanceSquared(at);
            if (d < best) { best = d; near = i; }
        }
        if (near <= h.trail + 1 && best < 36) { h.trail = -1; h.crumbs.clear(); say(p, "Here we are, out again."); return; }
        Location from = at.clone().add(0, 0.6, 0);
        for (int i = near - 1; i >= Math.max(h.trail, near - 8); i--) {
            Location to = h.crumbs.get(i).clone().add(0, 0.6, 0);
            Vector d = to.toVector().subtract(from.toVector());
            double len = d.length();
            if (len > 0.1) {
                d.normalize();
                for (double t = 0.5; t <= len; t += 0.6) p.spawnParticle(Particle.END_ROD, from.clone().add(d.clone().multiply(t)), 1, 0, 0, 0, 0);
            }
            from = to;
        }
    }

    private void wayback(Player p, H h) {
        if (h.crumbs.size() < 3) { say(p, "I have not followed you far enough to know a way back."); return; }
        if (p.getLocation().getBlock().getLightFromSky() >= 14) { say(p, "We are outside already."); return; }
        int exit = 0;
        for (int i = h.crumbs.size() - 1; i >= 0; i--) if (h.crumbs.get(i).getBlock().getLightFromSky() >= 14) { exit = i; break; }
        h.trail = exit;
        h.trailTop = h.crumbs.size();
        h.trailEnd = tick + 20 * 90;
        say(p, "Follow the sparks, I remember the way we came.");
    }

    /** A place to stand between {@code min} and {@code max} blocks from the owner, roughly at their height. */
    private static Location spotNear(Player p, int min, int max) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location l = p.getLocation();
        for (int tries = 0; tries < 30; tries++) {
            double a = r.nextDouble(Math.PI * 2), d = min + r.nextDouble(max - min);
            int x = l.getBlockX() + (int) (Math.cos(a) * d), z = l.getBlockZ() + (int) (Math.sin(a) * d);
            for (int y = l.getBlockY() + 8; y >= l.getBlockY() - 8; y--) {
                Block b = l.getWorld().getBlockAt(x, y, z);
                if (b.getType().isOccluding() && b.getRelative(BlockFace.UP).isPassable() && !b.getRelative(BlockFace.UP).isLiquid() && b.getRelative(0, 2, 0).isPassable())
                    return b.getLocation().add(0.5, 1, 0.5);
            }
        }
        return null;
    }

    private void endGame(H h) {
        if (h.game == null) return;
        h.game = null;
        h.w.setSitting(false);
        h.m.setCustomNameVisible(true);
    }

    private void startGame(Player p, H h, boolean hide) {
        release(h);
        if (hide) {
            Location spot = spotNear(p, 10, 30);
            if (spot == null) { say(p, "I can't find a place to hide here."); return; }
            zip(p, h);
            h.w.teleport(spot);
            h.w.setSitting(true);
            h.m.setCustomNameVisible(false);                       // the name would give it away through the walls
            h.gameDist = spot.distance(p.getLocation());
            say(p, "Ready or not, I am hidden! Come and find me.");
        } else say(p, "Catch me if you can!");
        h.game = hide ? "hide" : "flee";
        h.gameAt = System.currentTimeMillis();
        h.gameN = 0;
    }

    private void game(Player p, H h) {
        Wolf w = h.w;
        long now = System.currentTimeMillis();
        double d = w.getWorld() != p.getWorld() ? 999 : w.getLocation().distance(p.getLocation());
        if (d > 60) { endGame(h); w.teleport(p.getLocation()); say(p, "You went too far away, so the game is over."); return; }
        switch (h.game) {
            case "hide" -> {
                if (d < 3 && p.hasLineOfSight(h.m)) {
                    long secs = (now - h.gameAt) / 1000;
                    endGame(h);
                    h.dance = 50;
                    say(p, "You found me! That took you " + secs + " seconds.");
                    return;
                }
                if (++h.gameN % 10 != 0) return;                          // a hint every five seconds
                say(p, d < 6 ? "Hot! Very hot!" : d < h.gameDist - 2 ? "Warmer." : d > h.gameDist + 2 ? "Colder." : "About the same.");
                h.gameDist = d;
            }
            case "flee" -> {
                if (d < 2.2 && now > h.gameAt) {
                    h.game = "chase";
                    h.gameAt = now + 3000;
                    w.getPathfinder().stopPathfinding();
                    say(p, "You got me! Now I am it. Run!");
                    return;
                }
                Vector away = w.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                if (away.lengthSquared() < 0.01) away = new Vector(1, 0, 0);
                away.normalize().rotateAroundY(ThreadLocalRandom.current().nextDouble(-0.6, 0.6));
                w.getPathfinder().moveTo(w.getLocation().add(away.multiply(8)), 1.35);
            }
            default -> {                                                  // chase
                if (now < h.gameAt) return;                               // a head start for the owner
                if (d < 1.8) {
                    h.game = "flee";
                    h.gameAt = now + 2000;
                    say(p, "Got you! You are it.");
                    return;
                }
                w.getPathfinder().moveTo(p.getLocation(), 1.25);
            }
        }
    }

    private void startTreasure(Player p, H h) {
        long now = System.currentTimeMillis();
        if (h.treasure != null) { say(p, "You have not found the last one yet! " + clue(p, h)); return; }
        if (now - buried.getOrDefault(p.getUniqueId(), 0L) < 20 * 60_000) { say(p, "I need to find new treasure first. Ask me again in a while."); return; }
        Location spot = spotNear(p, 18, 40);
        if (spot == null) { say(p, "I can't find a good place to bury anything here."); return; }
        Block b = spot.getBlock().getRelative(0, -2, 0);                  // one block under the ground you stand on
        if (b.getState() instanceof Container || b.getType().getHardness() < 0) { say(p, "I can't find a good place to bury anything here."); return; }
        b.setType(Material.CHEST);
        if (!(b.getState() instanceof Chest chest)) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Inventory inv = chest.getBlockInventory();
        inv.addItem(new ItemStack(Material.EMERALD, 2 + r.nextInt(4)), new ItemStack(Material.BREAD, 4 + r.nextInt(5)), new ItemStack(Material.TORCH, 16));
        inv.addItem(new ItemStack(switch (r.nextInt(6)) { case 0 -> Material.GOLDEN_APPLE; case 1 -> Material.NAME_TAG; case 2 -> Material.COOKIE; case 3 -> Material.IRON_INGOT; case 4 -> Material.EXPERIENCE_BOTTLE; default -> Material.GOLD_INGOT; }, 1 + r.nextInt(4)));
        if (r.nextInt(10) == 0) inv.addItem(new ItemStack(Material.DIAMOND));
        buried.put(p.getUniqueId(), now);
        h.treasure = b.getLocation().add(0.5, 0, 0.5);
        h.clue = 1;
        zip(p, h);
        say(p, "I buried a treasure for you! " + clue(p, h));
    }

    private String clue(Player p, H h) {
        Location l = p.getLocation(), t = h.treasure;
        if (h.clue >= 3) return "Clue 3: it is right under your feet. Dig!";
        if (h.clue == 2) {
            Block top = t.getBlock().getRelative(BlockFace.UP);
            boolean tree = false, water = false;
            for (int x = -4; x <= 4; x++) for (int y = 0; y <= 4; y++) for (int z = -4; z <= 4; z++) {
                Material m = top.getRelative(x, y, z).getType();
                tree |= Tag.LOGS.isTagged(m);
                water |= m == Material.WATER;
            }
            return "Clue 2: you are close. It lies under " + word(top.getType()) + (tree ? ", by a tree" : "") + (water ? ", near water" : "") + ".";
        }
        String way = switch (direction(l, t)) {
            case "east", "north-east" -> "towards where the sun rises";
            case "west", "south-west" -> "towards where the sun sets";
            case "north", "north-west" -> "with the rising sun on your right hand";
            default -> "with the rising sun on your left hand";
        };
        return "Clue 1: walk " + way + ", about " + (int) (Math.round(l.distance(t) / 5) * 5) + " steps.";
    }

    private void clues(Player p, H h) {
        Location t = h.treasure;
        if (t.getWorld() != p.getWorld()) return;
        if (t.getBlock().getType() != Material.CHEST || (t.getBlock().getState() instanceof Chest c && c.getBlockInventory().isEmpty())) {
            h.treasure = null;
            if (!h.merged && h.game == null && h.job == Job.NONE) h.dance = 50;
            say(p, "You found the treasure! Well done, " + nick(p) + ".");
            return;
        }
        double d = t.distance(p.getLocation());
        int now = d < 3 ? 3 : d < 10 ? 2 : 1;
        if (now > h.clue) { h.clue = now; say(p, clue(p, h)); }
        if (h.clue == 3) p.spawnParticle(Particle.HAPPY_VILLAGER, t.clone().add(0, 1.2, 0), 4, 0.3, 0.2, 0.3);
    }

    /** What a helper (or its merged owner, or its arrow) kills goes in the server log. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onKill(org.bukkit.event.entity.EntityDeathEvent e) {
        if (e.getEntity() instanceof Player || !(e.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent hit)) return;
        Entity by = hit.getDamager();
        UUID id = by instanceof Player ? null : ownerOf(by);
        if (by instanceof Player pl) { H h = helpers.get(pl.getUniqueId()); if (h != null && h.merged) id = pl.getUniqueId(); }
        if (id == null) return;
        if (e.getEntity().getScoreboardTags().contains("helper_farm")) {                     // the mob farm: counted, not a line each in the log
            xp(id, Math.max(5, e.getDroppedExp()));
            e.setDroppedExp(0);
            bump(id, "mobs", 1);
            return;
        }
        if (!(by instanceof Player)) { xp(id, e.getDroppedExp()); e.setDroppedExp(0); }      // merged: the owner picks the orbs up themselves
        Location l = e.getEntity().getLocation();
        List<String> drops = new ArrayList<>();
        for (ItemStack it : e.getDrops()) if (!empty(it)) drops.add(it.getAmount() + " " + word(it.getType()));
        awayLog(id, "killed a " + e.getEntityType().getKey().getKey().replace('_', ' ') + " at " + l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ()
                + (drops.isEmpty() ? "" : " (it dropped " + String.join(", ", drops) + ")"));
    }

    /** Closes the open wooden doors and fence gates within {@code r} blocks that no player stands by, so the animals
     *  stay in. {@code self}: the helper, which does not shut a door it is standing in. Returns how many. */
    private int shut(Location around, int r, Entity self) {
        World w = around.getWorld();
        int n = 0, ry = Math.min(r, 4);
        for (int x = -r; x <= r; x++) for (int y = -ry; y <= ry; y++) for (int z = -r; z <= r; z++) {
            Block b = w.getBlockAt(around.getBlockX() + x, around.getBlockY() + y, around.getBlockZ() + z);
            Material t = b.getType();
            boolean door = Tag.DOORS.isTagged(t) && t != Material.IRON_DOOR;
            if (!door && !Tag.FENCE_GATES.isTagged(t)) continue;
            if (!(b.getBlockData() instanceof org.bukkit.block.data.Openable o) || !o.isOpen()) continue;
            Location at = b.getLocation().add(0.5, 0.5, 0.5);
            if (self != null && self.getLocation().distanceSquared(at) < 2.6) continue;
            if (!at.getNearbyPlayers(5).isEmpty()) continue;
            o.setOpen(false);
            b.setBlockData(o);
            if (door && o instanceof org.bukkit.block.data.Bisected half) {
                Block other = b.getRelative(half.getHalf() == org.bukkit.block.data.Bisected.Half.BOTTOM ? BlockFace.UP : BlockFace.DOWN);
                if (other.getType() == t && other.getBlockData() instanceof org.bukkit.block.data.Openable oo && oo.isOpen()) { oo.setOpen(false); other.setBlockData(oo); n--; }
            }
            w.playSound(at, door ? Sound.BLOCK_WOODEN_DOOR_CLOSE : Sound.BLOCK_FENCE_GATE_CLOSE, 0.7f, 1f);
            n++;
        }
        return Math.max(n, 0);
    }

    // --- Work while the owner is logged out ----------------------------------------------------------------------
    // /helper watch fish|farm|smelt: the job is done at home also when the server is empty and after a restart, without
    // the figure having to be there (the figure stays and guards when the owner logged out with it). Everything that
    // counts is in helpers.yml (<uuid>.away.*) and in the chests, so nothing is lost when the server stops.

    private boolean paused() { return conf.getBoolean("settings.paused", false); }

    /** "mending, unbreaking 3" for an enchanted book or thing, null for plain things. */
    private static String magic(ItemStack it) {
        if (empty(it) || !it.hasItemMeta()) return null;
        Map<org.bukkit.enchantments.Enchantment, Integer> en = it.getItemMeta() instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta b ? b.getStoredEnchants() : it.getEnchantments();
        if (en.isEmpty()) return null;
        List<String> out = new ArrayList<>();
        en.forEach((e, lvl) -> out.add(e.getKey().getKey().replace('_', ' ') + (e.getMaxLevel() > 1 ? " " + lvl : "")));
        return String.join(", ", out);
    }

    /** Keeps count of what came in while the owner is away; enchanted things get a line of their own in the log. */
    private void awayNote(UUID id, Collection<ItemStack> items) {
        for (ItemStack it : items) {
            if (empty(it)) continue;
            awayGot.computeIfAbsent(id, k -> new java.util.TreeMap<>()).merge(word(it.getType()), it.getAmount(), Integer::sum);
            String magic = magic(it);
            if (magic == null) {
                if (TREASURE.contains(it.getType())) awayLog(id, "FOUND " + it.getAmount() + " " + word(it.getType()).toUpperCase(Locale.ROOT));
                continue;
            }
            awayLog(id, "FOUND " + word(it.getType()).toUpperCase(Locale.ROOT) + ": " + magic);
            List<String> finds = conf.getStringList(path(id, "away.finds"));
            if (finds.size() < 30) { finds.add(word(it.getType()) + " (" + magic + ")"); conf.set(path(id, "away.finds"), finds); }
        }
    }

    private static final Set<Material> TREASURE = Set.of(Material.NAME_TAG, Material.SADDLE, Material.NAUTILUS_SHELL, Material.DIAMOND, Material.EMERALD,
            Material.ANCIENT_DEBRIS, Material.HEART_OF_THE_SEA, Material.TOTEM_OF_UNDYING, Material.ENCHANTED_GOLDEN_APPLE, Material.GOLDEN_APPLE);

    /** Something is in the way of the work: one line in the log, once per time away. */
    private void awayWarn(UUID id, String key, String text) {
        if (awayTold.putIfAbsent(id + ":warn:" + key, System.currentTimeMillis()) == null) awayLog(id, "NOTE: " + text);
    }

    /** Other players near the helper (or near home, when it works there without its figure): in the log at once,
     *  and then not again for ten minutes. */
    private void awayMeet(UUID id, Location around) {
        long now = System.currentTimeMillis();
        for (Player other : around.getNearbyPlayers(16)) {
            if (other.getUniqueId().equals(id) || other.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            Long last = awayTold.get(id + ":met:" + other.getUniqueId());
            if (last != null && now - last < 600_000) continue;
            awayTold.put(id + ":met:" + other.getUniqueId(), now);
            Location l = other.getLocation();
            awayLog(id, "met " + other.getName() + " at " + l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ());
        }
    }

    private void awayLog(UUID id, String text) {
        String who = Bukkit.getOfflinePlayer(id).getName();
        String shown = text.startsWith("FOUND ") ? "found " + text.substring(6).toLowerCase(Locale.ROOT) : text;
        String line = name(id) + (shown.matches("(?:starts|stopped|slaughtered|killed|met|says|found|took|caught|paused|made|closed|sees) .*|paused") ? " " : ": ") + shown;   // just its name; whose it is says the status
        NamedTextColor c = text.startsWith("FOUND") && text.contains(":") ? NamedTextColor.LIGHT_PURPLE     // enchanted things
                : text.startsWith("FOUND") || text.startsWith("found a ") || text.startsWith("the house") ? NamedTextColor.GOLD            // treasure, buildings
                : text.startsWith("killed ") ? NamedTextColor.RED
                : text.startsWith("NOTE:") || text.startsWith("stopped") ? NamedTextColor.YELLOW
                : text.startsWith("met ") ? NamedTextColor.AQUA
                : text.startsWith("last 10 min") || text.startsWith("starts to") ? NamedTextColor.GREEN : null;
        if (c == null) plugin.getLogger().info(line);
        else Bukkit.getConsoleSender().sendMessage(Component.text("[Creator] " + line, c));               // colours in the console
    }

    private void awayLet(UUID id) {
        List<long[]> held = awayHeld.remove(id);
        World w = awayWorld.remove(id);
        if (held != null && w != null) for (long[] c : held) w.removePluginChunkTicket((int) c[0], (int) c[1], plugin);
    }

    private int addToBag(UUID id, Collection<ItemStack> items) {
        Inventory bag = bag(id);
        int n = 0;
        for (ItemStack it : items) {
            if (empty(it)) continue;
            Map<Integer, ItemStack> rest = bag.addItem(it.clone());
            n += it.getAmount() - (rest.isEmpty() ? 0 : rest.values().iterator().next().getAmount());
        }
        if (n > 0) dirty.add(id);
        return n;
    }

    /** Every ten seconds, for everyone who is logged out with a job for their helper. */
    private void away() {
        if (paused()) { for (UUID id : new ArrayList<>(awayHeld.keySet())) { awayLog(id, "paused"); awayLet(id); } return; }
        long max = (long) (conf.getDouble("settings.away-hours", 2) * 3_600_000L);
        boolean changed = false;
        for (String k : conf.getKeys(false)) {
            UUID id;
            try { id = UUID.fromString(k); } catch (IllegalArgumentException e) { continue; }
            String job = conf.getString(path(id, "away.job"), "");
            if (job.isEmpty() && !conf.getBoolean(path(id, "away.rest"), false) && conf.getBoolean(path(id, "active")) && home(id) != null) {
                job = "home";                                              // nothing to do: the chores at home
                conf.set(path(id, "away.job"), job);
                conf.set(path(id, "watch"), true);
                changed = true;
            }
            if (conf.getBoolean(path(id, "away.full"), false) && !conf.contains(path(id, "away.why")) && home(id) != null && myBarrel(id, home(id).getWorld()) != null) {
                conf.set(path(id, "away.full"), null);                     // stopped because the chests were full, but there is a magic barrel now
                changed = true;
            }
            long ms = conf.getLong(path(id, "away.ms"), 0);
            if (Bukkit.getPlayer(id) == null && conf.getBoolean(path(id, "active")) && conf.contains(path(id, "routines"))) {
                Location rh = home(id);
                if (rh != null) awayRoutines(id, rh);
            }
            Location hm0 = job.isEmpty() || Bukkit.getPlayer(id) != null || !flag(id, "watch", false) || !conf.getBoolean(path(id, "active"))
                    || ms >= max || conf.getBoolean(path(id, "away.full"), false) ? null : home(id);
            Location hm = hm0 != null && job.equals("place") ? placeNamed(id, conf.getString(path(id, "away.place"), "")) : hm0;   // that job is done at the saved place, not at home
            if (hm != null && sites.containsKey(id)) {                     // still building: that is finished first, then the job
                awayWarn(id, "building", "finishes the " + sites.get(id).name + " at " + sites.get(id).where + " first, then starts the job");
                continue;
            }
            if (hm0 != null && hm == null) awayWarn(id, "nofarm", "no place called " + conf.getString(path(id, "away.place"), "?") + " yet (remember <name> <x> <y> <z> [world])");
            if (hm == null) {
                if (awayHeld.containsKey(id)) {
                    String did = awayDid(id);
                    awayLog(id, "stopped (" + (Bukkit.getPlayer(id) != null ? "the owner is back" : ms >= max ? "the time is up" : conf.getBoolean(path(id, "away.full"), false) ? conf.getString(path(id, "away.why"), "bag and chests are full, no wood for a new chest") : "called off")
                            + ") after " + ms / 60_000 + " min" + (did.isEmpty() ? ", nothing done" : ": " + did));
                }
                awayLet(id);
                continue;
            }
            if (!awayHeld.containsKey(id)) {                              // keep home loaded (crops, furnaces)
                Map<String, Integer> was0 = new HashMap<>();
                for (String c0 : List.of("fish", "crops", "sown", "meal", "wool", "fed", "meat", "smelted", "xp", "mined", "explored", "maps", "chicks", "goldchicks", "mobs")) was0.put(c0, conf.getInt(path(id, "away." + c0), 0));
                awaySeen.put(id, was0);
                awayTold.keySet().removeIf(k0 -> k0.startsWith(id + ":warn:"));
                awayLog(id, "starts to " + (job.equals("place") ? "work at " + conf.getString(path(id, "away.place"), "the place") + " (" : (job.equals("home") ? "work" : job) + " at home (") + hm.getWorld().getName() + " " + hm.getBlockX() + " " + hm.getBlockY() + " " + hm.getBlockZ() + "), " + ms / 60_000 + " min worked so far");
                List<long[]> held = new ArrayList<>();
                for (int cx = (hm.getBlockX() - HOME) >> 4; cx <= (hm.getBlockX() + HOME) >> 4; cx++) for (int cz = (hm.getBlockZ() - HOME) >> 4; cz <= (hm.getBlockZ() + HOME) >> 4; cz++) {
                    long[] c = {cx, cz};
                    hm.getWorld().addPluginChunkTicket((int) c[0], (int) c[1], plugin);
                    held.add(c);
                }
                awayHeld.put(id, held);
                awayWorld.put(id, hm.getWorld());
            }
            Block c = hm.getBlock();
            int got = 0;
            switch (job) {
                case "fish" -> got = awayFish(id, hm);
                case "farm" -> got = awayFarm(id, hm);
                case "home" -> {                                          // a bit of everything, and the gates stay shut
                    int field = awayFarm(id, hm);
                    got = awayFish(id, hm) + field;
                    int idle = field > 0 ? 0 : awayIdle.merge(id, 1, Integer::sum);
                    if (field > 0) awayIdle.put(id, 0);
                    if (idle >= 3) bump(id, "explored", awayExplore(id, hm, true));   // the field is done: out exploring
                    if (ms % 60_000 == 0) {
                        got += awayAnimals(id, hm);
                        int out = awaySmelt(id, hm);
                        bump(id, "smelted", out);
                        got += out;
                    }
                    shutYard(yard(id, hm));
                }
                case "place" -> got = awayPlace(id, hm);
                case "smelt" -> bump(id, "smelted", got = awaySmelt(id, hm));
                case "mine" -> bump(id, "mined", got = awayMine(id, hm));
                case "explore" -> bump(id, "explored", got = awayExplore(id, hm, false));
                default -> { }
            }
            Inventory bag = bag(id);
            int free = 0;
            for (ItemStack it : bag.getStorageContents()) if (empty(it)) free++;
            if (free < 6 && !job.equals("smelt") && myBarrel(id, hm.getWorld()) != null) {      // nearly full, and you have a magic barrel: all of it in your own store
                int moved = storage.putMine(id, bag);
                dirty.add(id);
                if (moved > 0) awayLog(id, "put " + moved + " things in your own store in the magic barrel");
            } else if (free < 6 && !job.equals("smelt")) {                // nearly full: into the chests at home
                int moved = 0;
                List<Inventory> to = stores(hm, HOME);
                for (int i = 0; i < bag.getSize(); i++) {
                    ItemStack it = bag.getItem(i);
                    if (empty(it)) continue;
                    int before = it.getAmount();
                    for (int pass = 0; pass < 2 && it.getAmount() > 0; pass++)
                        for (Inventory inv : to) {
                            if (pass == 0 && !inv.contains(it.getType())) continue;
                            Map<Integer, ItemStack> rest = inv.addItem(it.clone());
                            it.setAmount(rest.isEmpty() ? 0 : rest.values().iterator().next().getAmount());
                            if (it.getAmount() == 0) break;
                        }
                    moved += before - it.getAmount();
                    bag.setItem(i, it.getAmount() > 0 ? it : null);
                }
                dirty.add(id);
                if (moved == 0 && free == 0 && !moreRoom(id, hm)) conf.set(path(id, "away.full"), true);       // bag and chests full: it stops
            }
            awayMeet(id, hm);
            if ((ms + 10_000) % 600_000 == 0) {                           // a line every ten minutes: what was done since the last one
                Map<String, Integer> was = awaySeen.computeIfAbsent(id, k2 -> new HashMap<>());
                List<String> did = new ArrayList<>();
                for (String[] c2 : new String[][] {{"fish", "fish"}, {"crops", "crops"}, {"sown", "sown"}, {"meal", "bone meal used"}, {"wool", "sheep sheared"}, {"fed", "animals fed"}, {"chicks", "chicks hatched"}, {"mobs", "monsters killed"}, {"goldchicks", "golden chicks"},
                        {"meat", "slaughtered"}, {"smelted", "smelted"}, {"xp", "experience for you"}, {"mined", "ore blocks"}, {"explored", "new chunks"}, {"maps", "treasure maps"}}) {
                    int now2 = conf.getInt(path(id, "away." + c2[0]), 0), d = now2 - was.getOrDefault(c2[0], 0);
                    was.put(c2[0], now2);
                    if (d > 0) did.add(c2[1] + " " + d);
                }
                awayLog(id, "last 10 min (" + (job.equals("home") ? "work" : job) + "): " + (did.isEmpty() ? "nothing done" : String.join(", ", did)) + ". " + (ms + 10_000) / 60_000 + " min in all");
            }
            if (ms % 300_000 == 0 && ms > 0) tidy(id);
            conf.set(path(id, "away.ms"), ms + 10_000);
            changed = true;
        }
        if (changed && tick % 1200 == 0) { flushBags(); save(); }         // once a minute; shutdown() and pause save too
    }

    /** Done things out of the furnaces at home into the chests there; ores and raw food from the bag and the chests
     *  into empty furnaces, and fuel the same way. Returns how many things came out. */
    private int awaySmelt(UUID id, Location hm) {
        List<Inventory> from = new ArrayList<>(List.of(bag(id)));
        List<Inventory> chests = stores(hm, HOME);
        from.addAll(chests);
        int took = 0;
        for (FurnaceInventory f : furnaces(hm, HOME)) {
            ItemStack done = f.getResult();
            if (!empty(done)) {
                int before = done.getAmount(), left = before;
                for (Inventory inv : chests) {
                    ItemStack rest = done.clone();
                    rest.setAmount(left);
                    Map<Integer, ItemStack> over = inv.addItem(rest);
                    left = over.isEmpty() ? 0 : over.values().iterator().next().getAmount();
                    if (left == 0) break;
                }
                done.setAmount(left);
                f.setResult(left > 0 ? done : null);
                took += before - left;
            }
            if (empty(f.getSmelting()))
                load:
                for (Inventory inv : from) for (int i = 0; i < inv.getSize(); i++) {
                    ItemStack it = inv.getItem(i);
                    if (empty(it) || !f.canSmelt(it)) continue;
                    String n = it.getType().name();
                    if (!(n.startsWith("RAW_") || n.endsWith("_ORE") || n.equals("ANCIENT_DEBRIS") || it.getType().isEdible())) continue;
                    f.setSmelting(it.clone());
                    inv.setItem(i, null);
                    break load;
                }
            if (!empty(f.getSmelting()) && empty(f.getFuel()))
                fuel:
                for (Inventory inv : from) for (int i = 0; i < inv.getSize(); i++) {
                    ItemStack it = inv.getItem(i);
                    if (empty(it) || !FUELS.contains(it.getType())) continue;
                    ItemStack one = it.clone();
                    if (it.getType() != Material.LAVA_BUCKET) { one.setAmount(Math.min(it.getAmount(), 8)); it.setAmount(it.getAmount() - one.getAmount()); inv.setItem(i, it.getAmount() > 0 ? it : null); }
                    else inv.setItem(i, null);
                    f.setFuel(one);
                    break fuel;
                }
            if (!empty(f.getSmelting()) && empty(f.getFuel())) awayWarn(id, "fuel", "a furnace at home has no fuel (coal, charcoal, blaze rods, a lava bucket or dried kelp blocks in a chest)");
        }
        dirty.add(id);
        xp(id, (int) Math.round(took * 0.35));                            // about what a furnace gives per thing
        return took;
    }

    /** What there is to tend within 100 blocks of home: looked up once every five minutes (a look through every
     *  block there is too much to do every ten seconds). */
    private static final class Yard {
        final List<Block> soil = new ArrayList<>(), doors = new ArrayList<>();
        Block water;
        long at;
    }
    private final Map<UUID, Yard> yards = new HashMap<>();

    private Yard yard(UUID id, Location hm) {
        Yard y = yards.get(id);
        long now = System.currentTimeMillis();
        if (y != null && now - y.at < 300_000) return y;
        y = new Yard();
        y.at = now;
        World w = hm.getWorld();
        int hx = hm.getBlockX(), hy = hm.getBlockY(), hz = hm.getBlockZ();
        int lo = Math.max(w.getMinHeight(), hy - 16), hi = Math.min(w.getMaxHeight() - 2, hy + 16);
        double best = Double.MAX_VALUE;
        for (int cx = (hx - HOME) >> 4; cx <= (hx + HOME) >> 4; cx++) for (int cz = (hz - HOME) >> 4; cz <= (hz + HOME) >> 4; cz++) {
            if (!w.isChunkGenerated(cx, cz)) continue;
            org.bukkit.ChunkSnapshot snap = w.getChunkAt(cx, cz).getChunkSnapshot();
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                int bx = cx * 16 + x, bz = cz * 16 + z;
                if (Math.abs(bx - hx) > HOME || Math.abs(bz - hz) > HOME) continue;
                for (int yy = lo; yy <= hi; yy++) {
                    Material t = snap.getBlockType(x, yy, z);
                    if (t.isAir() || t == Material.STONE || t == Material.DIRT || t == Material.GRASS_BLOCK || t == Material.DEEPSLATE) continue;
                    if (t == Material.FARMLAND || t == Material.SOUL_SAND && snap.getBlockType(x, yy + 1, z) == Material.NETHER_WART) y.soil.add(w.getBlockAt(bx, yy, bz));
                    else if (Tag.FENCE_GATES.isTagged(t) || Tag.DOORS.isTagged(t) && t != Material.IRON_DOOR) y.doors.add(w.getBlockAt(bx, yy, bz));
                    else if (t == Material.WATER && snap.getBlockType(x, yy + 1, z).isAir()) {
                        double d = (bx - hx) * (double) (bx - hx) + (bz - hz) * (double) (bz - hz);
                        if (d < best) { best = d; y.water = w.getBlockAt(bx, yy, bz); }
                    }
                }
            }
        }
        yards.put(id, y);
        return y;
    }

    /** Shuts the open doors and gates of the yard that no player stands by. */
    private int shutYard(Yard y) {
        int n = 0;
        for (Block b : y.doors) {
            if (!(b.getBlockData() instanceof org.bukkit.block.data.Openable o) || !o.isOpen()) continue;
            Location at = b.getLocation().add(0.5, 0.5, 0.5);
            if (!at.getNearbyPlayers(5).isEmpty()) continue;
            o.setOpen(false);
            b.setBlockData(o);
            boolean door = Tag.DOORS.isTagged(b.getType());
            b.getWorld().playSound(at, door ? Sound.BLOCK_WOODEN_DOOR_CLOSE : Sound.BLOCK_FENCE_GATE_CLOSE, 0.7f, 1f);
            if (!door || !(o instanceof org.bukkit.block.data.Bisected half) || half.getHalf() == org.bukkit.block.data.Bisected.Half.BOTTOM) n++;
        }
        return n;
    }

    /** What the helper earns in experience is its owner's: given at once, or kept until they log in. */
    private void xp(UUID id, int n) {
        if (n <= 0) return;
        Player p = Bukkit.getPlayer(id);
        if (p != null) { p.giveExp(n); return; }
        conf.set(path(id, "xp"), conf.getInt(path(id, "xp"), 0) + n);
        bump(id, "xp", n);
    }

    private void bump(UUID id, String k, int n) {
        if (n > 0) conf.set(path(id, "away." + k), conf.getInt(path(id, "away." + k), 0) + n);
    }

    private int awayFish(UUID id, Location hm) {
        Block b = yard(id, hm).water;
        if (b == null || b.getType() != Material.WATER) { awayWarn(id, "water", "no open water within 100 blocks of home, so no fishing"); return 0; }
        Collection<ItemStack> loot = fishLoot(b.getLocation());
        awayNote(id, loot);
        int n = addToBag(id, loot);
        bump(id, "fish", n);
        xp(id, 1 + ThreadLocalRandom.current().nextInt(6));
        return n;
    }

    /** One bone meal from the bag or the chests; when there is none, a bone is ground into three. */
    private boolean boneMeal(UUID id, List<Inventory> from) {
        if (count(from, m -> m == Material.BONE_MEAL) < 1) {
            if (count(from, m -> m == Material.BONE) < 1) return false;
            take(from, m -> m == Material.BONE, 1);
            hand(id, null, new ItemStack(Material.BONE_MEAL, 3));
        }
        take(from, m -> m == Material.BONE_MEAL, 1);
        dirty.add(id);
        return true;
    }

    /** The fields within 100 blocks of home: ripe plants are harvested and set again, empty farmland is sown with what
     *  there is in the bag and the chests, and what is still growing gets bone meal (made from bones when needed). */
    private int awayFarm(UUID id, Location hm) {
        List<Inventory> from = new ArrayList<>(List.of(bag(id)));
        from.addAll(stores(hm, HOME));
        int n = 0, sown = 0, meal = 0;
        for (Block soil : yard(id, hm).soil) {
            if (n >= 24 && sown >= 16 && meal >= 8) break;
            Block b = soil.getRelative(BlockFace.UP);
            Material t = b.getType();
            if (SEEDS.containsKey(t) && b.getBlockData() instanceof Ageable a) {
                if (a.getAge() >= a.getMaximumAge()) {
                    if (n >= 24) continue;
                    Collection<ItemStack> crop = harvest(b);
                    awayNote(id, crop);
                    addToBag(id, crop);
                    n++;
                } else if (meal < 8 && t != Material.NETHER_WART) {
                    if (!boneMeal(id, from)) { awayWarn(id, "meal", "out of bone meal and bones, the fields grow at their own speed now"); meal = 8; continue; }
                    b.applyBoneMeal(BlockFace.UP);
                    meal++;
                }
            } else if (soil.getType() == Material.FARMLAND && sown < 16 && t.isAir()) {
                for (Map.Entry<Material, Material> e : SEEDS.entrySet()) {
                    Material seed = e.getValue();
                    if (e.getKey() == Material.NETHER_WART || count(from, m -> m == seed) < 1) continue;
                    take(from, m -> m == seed, 1);
                    b.setType(e.getKey());
                    dirty.add(id);
                    sown++;
                    break;
                }
                if (b.getType().isAir()) awayWarn(id, "seed", "there is empty farmland and nothing to sow in the bag or the chests");
            }
        }
        bump(id, "crops", n);
        bump(id, "sown", sown);
        bump(id, "meal", meal);
        return n + sown;
    }

    // --- Building ----------------------------------------------------------------------------------
    private static final Pattern BUILD_WORDS = Pattern.compile("(?:build|bygg)(?: \\S+){0,6}? (house|cottage|hut|cabin|hus|hytte)\\b(.*)");
    private static final Pattern XYZ = Pattern.compile("(-?\\d+)[ ,]+(-?\\d+)[ ,]+(-?\\d+)(?:\\s+(\\w+))?");
    private static final String[] FACES = {"south", "west", "north", "east"};

    private static boolean natural(Material m) {
        String n = m.name();                                   // (the dirt tag of this version has no grass block in it)
        if (m == Material.GRASS_BLOCK || m == Material.DIRT || m == Material.COARSE_DIRT || m == Material.ROOTED_DIRT || m == Material.PODZOL || m == Material.MYCELIUM
                || m == Material.MUD || m == Material.MOSS_BLOCK || m == Material.SHORT_GRASS || m == Material.TALL_GRASS || m == Material.FERN || m == Material.LARGE_FERN
                || m == Material.DEAD_BUSH || m == Material.VINE || m == Material.SNOW_BLOCK || m == Material.TUFF || m == Material.CALCITE || m == Material.TERRACOTTA
                || n.endsWith("_ORE") || n.endsWith("_TERRACOTTA") && !n.contains("GLAZED")) return true;
        return m.isAir() || Tag.LOGS.isTagged(m) || Tag.LEAVES.isTagged(m) || Tag.DIRT.isTagged(m) || Tag.BASE_STONE_OVERWORLD.isTagged(m) || Tag.SAND.isTagged(m)
                || Tag.FLOWERS.isTagged(m) || Tag.SAPLINGS.isTagged(m) || Tag.REPLACEABLE.isTagged(m) || Tag.SNOW.isTagged(m) || m == Material.GRAVEL || m == Material.CLAY
                || m == Material.SANDSTONE || m == Material.RED_MUSHROOM || m == Material.BROWN_MUSHROOM || m == Material.SUGAR_CANE || m == Material.MOSS_CARPET
                || m == Material.PUMPKIN || m == Material.MELON || m == Material.BAMBOO || m == Material.SWEET_BERRY_BUSH || m == Material.ICE || m == Material.CACTUS;
    }

    private static final Pattern DOWN_WORDS = Pattern.compile("(?:undo|remove|take down|tear down|pull down|demolish|angre|fjern|riv|ta ned)(?: the| my| your| last| det| siste| mitt| ditt| this| that)* ?(?:build|building|house|bygg|bygget|bygningen|huset|hus)\\b(.*)");
    private static final String[] WOODS = {"OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "DARK_OAK", "MANGROVE", "CHERRY", "PALE_OAK"};

    /** A building job: a house going up (clear the plot, gather, place) or something coming down. One thing every 0.4 s. */
    private final class Site {
        UUID id; World w; int x, y, z, fx, fz; String where, wood; boolean down;
        final Deque<Block> clear = new ArrayDeque<>(), trees = new ArrayDeque<>(), stone = new ArrayDeque<>(), sand = new ArrayDeque<>();
        final List<Object[]> place = new ArrayList<>();
        int at, wait, far, phase, needLogs, needCobble, needSand, sheep, broke, put;
        boolean door, paused; String bed;
        final List<long[]> held = new ArrayList<>();
        final List<String> placed = new ArrayList<>();
        final Set<String> lacked = new java.util.TreeSet<>();
        final Set<String> had = new HashSet<>();                           // set in an earlier round of the same drawing
        List<Inventory> from; long fromAt;
        Stance stance; Location anchor;
        org.bukkit.scheduler.BukkitTask task;
        int out = 5; String name = "little house"; boolean plan;           // how far in front of the middle it stands; what is built; from a drawing
        final Set<Material> keep = new HashSet<>();                       // what a drawing needs: kept when the plot is cleared
        final Inventory pile = Bukkit.createInventory(null, 54);          // the materials for this job, kept apart from the bag (which may be full)
    }
    private final Map<UUID, Site> sites = new HashMap<>();
    private final Map<UUID, Object[]> razing = new HashMap<>();              // asked to take something down: the blocks, and when

    private static Material mat(String name) { return Material.matchMaterial(name); }

    private static boolean builtBlock(Material m) {
        return (!natural(m) || Tag.LOGS.isTagged(m)) && !m.name().endsWith("_ORE") && m != Material.BEDROCK;
    }

    private void note(Site s, String text) {
        Player p = Bukkit.getPlayer(s.id);
        if (p != null) say(p, text); else awayLog(s.id, text);
    }

    private List<Inventory> stock(Site s) {
        long now = System.currentTimeMillis();
        if (s.from == null || now - s.fromAt > 10_000) {
            s.from = new ArrayList<>(List.of(s.pile, bag(s.id)));
            s.from.addAll(stores(new Location(s.w, s.x, s.y, s.z), 12));
            s.fromAt = now;
        }
        return s.from;
    }

    private void stash(Site s, ItemStack it) {
        if (empty(it)) return;
        Map<Integer, ItemStack> rest = s.pile.addItem(it.clone());
        if (!rest.isEmpty()) { rest = bag(s.id).addItem(rest.values().iterator().next()); dirty.add(s.id); }
        for (ItemStack r : rest.values()) s.w.dropItemNaturally(new Location(s.w, s.x + 0.5 + s.fx * s.out, s.y, s.z + 0.5 + s.fz * s.out), r);
    }

    private boolean ensure(Site s, Material m, int n, int depth) {
        if (m == null) return false;
        while (count(stock(s), k -> k == m) < n) if (depth > 6 || !craft(s, m, depth + 1)) return false;
        return true;
    }

    /** How a thing is made: {what goes in (thing, how many, ...), how many come out, the wait}. Null: it cannot be made. */
    private Object[] recipe(Site s, Material m) {
        Material log = mat(s.wood + "_LOG"), planks = mat(s.wood + "_PLANKS");
        String n = m.name();
        Object[] in;
        int out = 1, wait = 0, smelt = s.plan ? 2 : 25;
        if (m == planks) { in = new Object[] {log, 1}; out = 4; }
        else if (m == mat(s.wood + "_STAIRS")) { in = new Object[] {planks, 6}; out = 4; }
        else if (m == mat(s.wood + "_SLAB")) { in = new Object[] {planks, 3}; out = 6; }
        else if (m == mat(s.wood + "_DOOR")) { in = new Object[] {planks, 6}; out = 3; }
        else if (m == mat(s.wood + "_FENCE")) { in = new Object[] {planks, 4, Material.STICK, 2}; out = 3; }
        else if (m == mat(s.wood + "_FENCE_GATE")) in = new Object[] {Material.STICK, 4, planks, 2};
        else if (m == mat(s.wood + "_TRAPDOOR")) { in = new Object[] {planks, 6}; out = 2; }
        else if (m == mat(s.wood + "_PRESSURE_PLATE")) in = new Object[] {planks, 2};
        else if (m == mat(s.wood + "_BUTTON")) in = new Object[] {planks, 1};
        else if (m == Material.CHEST) in = new Object[] {planks, 8};
        else if (m == Material.BARREL) in = new Object[] {planks, 7};
        else if (m == Material.CRAFTING_TABLE) in = new Object[] {planks, 4};
        else if (m == Material.LADDER) { in = new Object[] {Material.STICK, 7}; out = 3; }
        else if (m == Material.FURNACE) in = new Object[] {Material.COBBLESTONE, 8};
        else if (m == Material.STICK) { in = new Object[] {planks, 2}; out = 4; }
        else if (m == Material.CHARCOAL) { in = new Object[] {log, 1}; wait = smelt; }
        else if (m == Material.CAMPFIRE) in = new Object[] {Material.STICK, 3, Material.CHARCOAL, 1, log, 3};
        else if (m == Material.TORCH || m == Material.WALL_TORCH) { in = m == Material.WALL_TORCH ? new Object[] {Material.TORCH, 1} : new Object[] {Material.STICK, 1, Material.CHARCOAL, 1}; out = m == Material.WALL_TORCH ? 1 : 4; }
        else if (m == Material.IRON_NUGGET) { in = new Object[] {Material.IRON_INGOT, 1}; out = 9; }
        else if (m == Material.LANTERN) in = new Object[] {Material.IRON_NUGGET, 8, Material.TORCH, 1};
        else if (m == Material.GLASS) { in = new Object[] {Material.SAND, 1}; wait = smelt; }
        else if (m == Material.GLASS_PANE) { in = new Object[] {Material.GLASS, 6}; out = 16; }
        else if (m == Material.STONE) { in = new Object[] {Material.COBBLESTONE, 1}; wait = smelt; }
        else if (m == Material.SMOOTH_STONE) { in = new Object[] {Material.STONE, 1}; wait = smelt; }
        else if (m == Material.STONE_BRICKS) { in = new Object[] {Material.STONE, 4}; out = 4; }
        else if (m == Material.SANDSTONE) in = new Object[] {Material.SAND, 4};
        else if (n.endsWith("_STAIRS") || n.endsWith("_SLAB") || n.endsWith("_WALL")) {                // of stone: from the block of the same name
            String stem = n.substring(0, n.lastIndexOf('_'));
            Material from = mat(stem) != null && mat(stem).isBlock() ? mat(stem) : mat(stem + "S");
            if (from == null || !from.isBlock() || from == m || Tag.PLANKS.isTagged(from)) return null;
            in = new Object[] {from, n.endsWith("_SLAB") ? 3 : 6};
            out = n.endsWith("_STAIRS") ? 4 : 6;
        }
        else return null;
        return new Object[] {in, out, wait};
    }

    /** Makes one batch of something from what is in the bag and the chests by the plot, the way a crafting table or a furnace would. */
    private boolean craft(Site s, Material m, int depth) {
        Object[] r = recipe(s, m);
        if (r == null) return false;
        Object[] in = (Object[]) r[0];
        for (int i = 0; i < in.length; i += 2) if (!ensure(s, (Material) in[i], (Integer) in[i + 1], depth)) return false;
        for (int i = 0; i < in.length; i += 2) { Material k = (Material) in[i]; take(stock(s), q -> q == k, (Integer) in[i + 1]); }
        stash(s, new ItemStack(m, (Integer) r[1]));
        s.wait += (Integer) r[2];                                          // a furnace takes its time
        return true;
    }

    // --- Anything it is asked to build: the chat bridge's model draws it (layers of letters), the helper builds it by hand ---

    private final Map<UUID, Object[]> designs = new HashMap<>();             // waiting for a drawing: world, x, y, z, facing, when

    private static final Pattern PLAIN_HOUSE = Pattern.compile("(?:build|bygg|a|an|the|me|us|et|en|ei|small|little|liten|lite|house|cottage|hut|cabin|hus|hytte|here|her|for|yourself|myself|deg|selv|meg|oss|please|-?\\d+|[a-z_]*world[a-z_]*|[\\s,.!])*");
    private static final Pattern ANY_BUILD = Pattern.compile("(?:build|bygg|construct|make|lag)\\s+(?:me |us |meg |oss )?(.{3,300})");

    /** "build a stone tower with a spiral stair here": asks for a drawing. False: not a wish for a building. */
    private boolean designOrder(UUID id, Player p, String t, Consumer<String> tell) {
        Matcher m = ANY_BUILD.matcher(t);
        if (!m.matches() || m.group(1).matches("(?:a |an |the |en |ei |et )?(?:bridge|stairs|bro|trapp|super ?tool.*)")) return false;
        String thing = ARTICLE.matcher(m.group(1).trim().replaceAll("\\s+(?:here|for me|please|her|til meg|takk)$", "")).replaceFirst("").trim();
        String key = thing.toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
        Material one = key.isEmpty() ? null : Material.matchMaterial(key);
        if (key.equals("BED") || key.equals("SENG") || one != null && one.isItem() && !Bukkit.getRecipesFor(new ItemStack(one)).isEmpty()) {      // one thing, not a building: it is crafted and handed over
            craft(id, p, p != null ? p.getLocation() : null, thing, false, tell);
            return true;
        }
        if (sites.containsKey(id)) { tell.accept("I am already at work on a building. Say stop building first."); return true; }
        String wish = m.group(1), who = Bukkit.getOfflinePlayer(id).getName();
        Matcher c = XYZ.matcher(wish);
        if (c.find()) {                                                   // "... 100 64 100 [world]": there, the front to the south
            World w = c.group(4) != null && Bukkit.getWorld(c.group(4)) != null ? Bukkit.getWorld(c.group(4)) : p != null ? p.getWorld() : home(id) != null ? home(id).getWorld() : Bukkit.getWorlds().get(0);
            designs.put(id, new Object[] {w, Integer.parseInt(c.group(1)), Integer.parseInt(c.group(2)), Integer.parseInt(c.group(3)), "north", System.currentTimeMillis()});
            wish = (wish.substring(0, c.start()) + wish.substring(c.end())).replaceAll("\\b(?:at|ved|på)\\s*$", "").trim();
        } else if (p != null) {
            Location l = p.getLocation();
            designs.put(id, new Object[] {l.getWorld(), l.getBlockX(), l.getBlockY(), l.getBlockZ(), p.getFacing().name().toLowerCase(Locale.ROOT), System.currentTimeMillis()});
        } else { tell.accept("Where? Say build <what> <x> <y> <z> [world] (y is the level you walk on)."); return true; }
        if (who == null || wish.length() < 3) { designs.remove(id); return false; }
        plugin.getLogger().info("HELPER_BUILD " + who + "|" + name(id).replace('|', ' ') + "|" + wish.replace('|', ' ').replaceAll("\\s+", " "));
        tell.accept("Let me draw it first. Give me a minute or two, then I start" + (c.reset(m.group(1)).find() ? " there." : " in front of where you stand now."));
        return true;
    }

    /** The drawing has come (plugins/Creator/blueprints/<player>.json): build it. */
    private void buildPlan(UUID id, Consumer<String> tell) {
        Object[] d = designs.remove(id);
        if (d == null || System.currentTimeMillis() - (Long) d[5] > 20 * 60_000L) { tell.accept("I have no drawing waiting. Tell me what to build."); return; }
        File f = new File(new File(plugin.getDataFolder(), "blueprints"), Bukkit.getOfflinePlayer(id).getName() + ".json");
        conf.set(path(id, "plan"), ((World) d[0]).getName() + " " + d[1] + " " + d[2] + " " + d[3] + " " + d[4]);      // for "continue building"
        save();
        try { java.nio.file.Files.copy(f.toPath(), new File(f.getParentFile(), f.getName().replace(".json", ".built.json")).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); } catch (IOException ignored) { }   // a later drawing does not take this one away
        try (java.io.Reader r = java.nio.file.Files.newBufferedReader(f.toPath())) {
            tell.accept(blueprint(id, com.google.gson.JsonParser.parseReader(r).getAsJsonObject(), (World) d[0], (Integer) d[1], (Integer) d[2], (Integer) d[3], (String) d[4]));
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("Blueprint " + f + ": " + e);
            tell.accept("I could not read my own drawing. Ask me again?");
        }
    }

    private final Set<UUID> buildingOn = new HashSet<>();
    private static final Pattern BUILD_ON = Pattern.compile("(?:(?:continue|keep|go on|carry on|resume|finish)(?: on| with)?(?: the)? build(?:ing)?|keep building|build on|finish (?:the |my )?(?:house|building)|bygg videre|fortsett (?:å bygge|byggingen|bygget)|bygg ferdig)(?: .*)?");

    /** "continue building": the last drawing again on the same spot; what already stands is kept and the rest is set. */
    private void buildOn(UUID id, String at, Consumer<String> tell) {
        String[] a = (at.isBlank() ? conf.getString(path(id, "plan"), "") : at).trim().split("\\s+");
        if (a.length == 4 && conf.getString(path(id, "built.world")) != null) a = new String[] {conf.getString(path(id, "built.world")), a[0], a[1], a[2], a[3]};
        World w = a.length == 5 ? Bukkit.getWorld(a[0]) : null;
        File f = new File(new File(plugin.getDataFolder(), "blueprints"), Bukkit.getOfflinePlayer(id).getName() + ".built.json");
        if (!f.isFile()) f = new File(f.getParentFile(), Bukkit.getOfflinePlayer(id).getName() + ".json");      // a build from before the copies were kept
        if (w == null || !f.isFile() || !Arrays.asList(FACES).contains(a[4])) { tell.accept("I have no half-built drawing to go on with. Tell me what to build."); return; }
        buildingOn.add(id);
        try (java.io.Reader r = java.nio.file.Files.newBufferedReader(f.toPath())) {
            tell.accept(blueprint(id, com.google.gson.JsonParser.parseReader(r).getAsJsonObject(), w, Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]), a[4]));
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("Blueprint " + f + ": " + e);
            tell.accept("I could not read my own drawing. Ask me again?");
        } finally { buildingOn.remove(id); }
    }

    private static final Set<String> NO_BUILD = Set.of("WATER", "LAVA", "FIRE", "SOUL_FIRE", "TNT", "BEDROCK", "BARRIER", "SPAWNER", "TRIAL_SPAWNER", "JIGSAW", "LIGHT", "END_PORTAL_FRAME");

    private static String turn(String props, int turns) {                    // the drawing has its front to the south
        if (props.isEmpty()) return props;
        StringBuilder out = new StringBuilder("[");
        for (String kv : props.substring(1, props.length() - 1).split(",")) {
            String[] a = kv.trim().split("=");
            if (a.length != 2 || !a[0].matches("[a-z_]+") || !a[1].matches("[a-z0-9_]+")) continue;
            int i = Arrays.asList(FACES).indexOf(a[1]);
            if (a[0].equals("facing") && i >= 0) a[1] = FACES[(i + turns) % 4];
            else if (a[0].equals("axis") && turns % 2 == 1) a[1] = a[1].equals("x") ? "z" : a[1].equals("z") ? "x" : a[1];
            else if (Arrays.asList(FACES).contains(a[0])) continue;         // fence and pane arms: worked out when it stands
            if (out.length() > 1) out.append(',');
            out.append(a[0]).append('=').append(a[1]);
        }
        return out.length() == 1 ? "" : out.append(']').toString();
    }

    /** The thing that is set for a word of a plan: LOG, PLANKS, WOOD:FENCE (of the wood of this job), or a block's own name. */
    /** The thing in the hand for a block: a torch for a wall torch, a sign for a wall sign. */
    private static Material asItem(Material m) {
        if (m == null || m.isItem()) return m;
        try { Material i = m.createBlockData().getPlacementMaterial(); return i == null || i.isAir() ? m : i; } catch (RuntimeException e) { return m; }
    }

    private Material planItem(Site s, String base) {
        return switch (base) {
            case "LOG" -> mat(s.wood + "_LOG");
            case "PLANKS" -> mat(s.wood + "_PLANKS");
            case "STAIRS" -> mat(s.wood + "_STAIRS");
            case "SLAB" -> mat(s.wood + "_SLAB");
            case "DOOR" -> mat(s.wood + "_DOOR");
            case "BED" -> null;
            default -> base.startsWith("WOOD:") ? mat(s.wood + "_" + base.substring(5)) : mat(base);
        };
    }

    /** How much wood (0), stone (1) and sand (2) it takes to make n of this from nothing. False: it cannot be made. */
    private boolean raw(Site s, Material m, double n, double[] acc, int depth) {
        if (m == null || depth > 8) return false;
        if (m == mat(s.wood + "_LOG")) { acc[0] += n; return true; }
        if (m == Material.COBBLESTONE) { acc[1] += n; return true; }
        if (m == Material.SAND) { acc[2] += n; return true; }
        Object[] r = recipe(s, m);
        if (r == null) return false;
        Object[] in = (Object[]) r[0];
        double[] sub = new double[3];
        for (int i = 0; i < in.length; i += 2) if (!raw(s, (Material) in[i], n / (Integer) r[1] * (Integer) in[i + 1], sub, depth + 1)) return false;
        for (int i = 0; i < 3; i++) acc[i] += sub[i];
        return true;
    }

    private String blueprint(UUID id, com.google.gson.JsonObject j, World w, int px, int py, int pz, String facing) {
        if (sites.containsKey(id)) return "I am already at work on a building. Say stop building first.";
        String title = j.has("name") ? j.get("name").getAsString().replaceAll("[^\\p{L}\\p{N} '-]", "").trim() : "";
        if (title.isEmpty()) title = "building";
        if (title.length() > 40) title = title.substring(0, 40);
        Map<Character, String> legend = new HashMap<>();
        for (com.google.gson.JsonElement e : j.getAsJsonArray("legend")) {
            String k = e.getAsJsonObject().get("key").getAsString(), b = e.getAsJsonObject().get("block").getAsString().trim().toLowerCase(Locale.ROOT).replace("minecraft:", "");
            if (k.length() == 1 && !b.isEmpty()) legend.put(k.charAt(0), b);
        }
        Map<Integer, List<String>> layers = new java.util.TreeMap<>();
        int cols = 0, rows = 0;
        for (com.google.gson.JsonElement e : j.getAsJsonArray("layers")) {
            List<String> rs = new ArrayList<>();
            for (com.google.gson.JsonElement r : e.getAsJsonObject().getAsJsonArray("rows")) { rs.add(r.getAsString()); cols = Math.max(cols, r.getAsString().length()); }
            rows = Math.max(rows, rs.size());
            layers.put(e.getAsJsonObject().get("y").getAsInt(), rs);
        }
        if (layers.isEmpty() || cols == 0) return "My drawing came out empty. Ask me again, maybe with a few more words about what you want.";
        int ymin = ((java.util.TreeMap<Integer, List<String>>) layers).firstKey(), ymax = ((java.util.TreeMap<Integer, List<String>>) layers).lastKey();
        if (cols > 25 || rows > 25 || ymin < -10 || ymax > 30) return "What I drew is too big for me to build by hand (" + cols + " x " + rows + ", from " + ymin + " to " + ymax + "). Ask for something smaller: at most 25 x 25 and 30 high.";
        if (py + ymin < w.getMinHeight() + 6 || py + ymax > w.getMaxHeight() - 4) return "That is too high or too low to build.";
        int cu = cols / 2, cv = rows / 2, fi = Math.max(0, Arrays.asList(FACES).indexOf(facing)), turns = (fi + 2) % 4, gap = rows - 1 - cv + 3;
        int x = px + (facing.equals("east") ? gap : facing.equals("west") ? -gap : 0), y = py, z = pz + (facing.equals("south") ? gap : facing.equals("north") ? -gap : 0);
        int[][] rot = {{1, 0, 0, 1}, {0, -1, 1, 0}, {-1, 0, 0, -1}, {0, 1, -1, 0}};
        int[] r = rot[turns];
        Site s = new Site();
        s.id = id; s.w = w; s.x = x; s.y = y; s.z = z; s.fx = r[1]; s.fz = r[3]; s.out = gap + 1; s.name = title.toLowerCase(Locale.ROOT); s.plan = true; s.wood = "OAK";
        s.where = w.getName() + " " + x + " " + y + " " + z;
        // the drawing, cell by cell
        Map<String, String> plan = new java.util.LinkedHashMap<>();          // "u y v" -> what
        Set<String> unknown = new java.util.TreeSet<>(), beds = new HashSet<>();
        for (Map.Entry<Integer, List<String>> ly : layers.entrySet()) for (int row = 0; row < ly.getValue().size(); row++) {
            String line = ly.getValue().get(row);
            for (int col = 0; col < line.length(); col++) {
                String spec = legend.get(line.charAt(col));
                if (spec == null || spec.equals("air") || spec.equals("cave_air")) continue;
                String base = spec.contains("[") ? spec.substring(0, spec.indexOf('[')) : spec, props = spec.contains("[") && spec.endsWith("]") ? spec.substring(spec.indexOf('[')) : "";
                Material m = mat(base);
                String up = m == null ? "" : m.name();
                if (m == null || !m.isBlock() || m.isAir() || NO_BUILD.contains(up) || up.contains("COMMAND") || up.contains("STRUCTURE") || up.contains("PORTAL") || up.contains("INFESTED")) { unknown.add(base.replace('_', ' ')); continue; }
                String key = (col - cu) + " " + ly.getKey() + " " + (row - cv), word = base;
                props = turn(props, turns);
                if (up.endsWith("_BED")) { beds.add(key); continue; }
                String bare = up.startsWith("STRIPPED_") ? up.substring(9) : up;
                for (String wd : new String[] {"DARK_OAK", "PALE_OAK", "OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "MANGROVE", "CHERRY", "BAMBOO", "CRIMSON", "WARPED"}) if (bare.startsWith(wd + "_")) {
                    String part = bare.substring(wd.length() + 1);
                    if (part.equals("STEM") || part.equals("HYPHAE") || part.equals("BLOCK") || part.equals("WOOD")) part = "LOG";
                    if (part.equals("LEAVES") || part.equals("SAPLING") || part.equals("PROPAGULE") || part.equals("ROOTS") || part.equals("MOSAIC")) break;
                    if (part.equals("DOOR")) {
                        boolean upper = plan.getOrDefault((col - cu) + " " + (ly.getKey() - 1) + " " + (row - cv), "").startsWith("DOOR[half=lower");
                        String face = "north";
                        Matcher fm = Pattern.compile("facing=([a-z]+)").matcher(spec);
                        if (fm.find() && Arrays.asList(FACES).contains(fm.group(1))) face = fm.group(1);
                        if (upper) { String low = plan.get((col - cu) + " " + (ly.getKey() - 1) + " " + (row - cv)); props = low.substring(low.indexOf('[')).replace("half=lower", "half=upper"); }
                        else props = "[half=lower,facing=" + FACES[(Arrays.asList(FACES).indexOf(face) + turns) % 4] + "]";
                    }
                    word = Set.of("LOG", "PLANKS", "STAIRS", "SLAB", "DOOR").contains(part) ? part : "WOOD:" + part;
                    break;
                }
                plan.put(key, word + props);
            }
        }
        for (String head : new ArrayList<>(beds)) {                         // a bed is two blocks: the other half next to it
            if (!beds.contains(head)) continue;
            String[] a = head.split(" ");
            int u = Integer.parseInt(a[0]), hy = Integer.parseInt(a[1]), v = Integer.parseInt(a[2]);
            int[][] side = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};               // south, west, north, east
            int pick = -1;
            for (int k = 0; k < 4 && pick < 0; k++) if (beds.contains((u + side[k][0]) + " " + hy + " " + (v + side[k][1]))) pick = k;
            for (int k = 0; k < 4 && pick < 0; k++) if (!plan.containsKey((u + side[k][0]) + " " + hy + " " + (v + side[k][1])) && Math.abs(u + side[k][0]) <= cu && Math.abs(v + side[k][1]) <= cv) pick = k;
            beds.remove(head);
            if (pick < 0) continue;
            String foot = (u + side[pick][0]) + " " + hy + " " + (v + side[pick][1]), face = FACES[((pick + 2) % 4 + turns) % 4];   // it faces from the foot to the head
            beds.remove(foot);
            plan.put(head, "BED[part=head,facing=" + face + "]");
            plan.put(foot, "BED[part=foot,facing=" + face + "]");
        }
        if (plan.isEmpty()) return "Nothing in my drawing is something I can build" + (unknown.isEmpty() ? "." : " (" + String.join(", ", unknown) + ").") + " Ask me again?";
        if (plan.size() > 4000) return "What I drew takes " + plan.size() + " blocks. That is too much to build by hand; ask for something smaller.";
        // the plot: everything from the top down, and the hole for what goes under the ground
        if (buildingOn.contains(id) && w.getName().equals(conf.getString(path(id, "built.world")))) { s.had.addAll(conf.getStringList(path(id, "built.blocks"))); s.placed.addAll(s.had); }
        for (int h = ymax + 1; h >= ymin; h--) for (int u = -cu - 1; u <= cols - cu; u++) for (int v = -cv - 1; v <= rows - cv; v++) {
            boolean rim = u < -cu || u > cols - 1 - cu || v < -cv || v > rows - 1 - cv;
            if (h < 0 && rim) continue;
            Block b = w.getBlockAt(x + r[0] * u + r[1] * v, y + h, z + r[2] * u + r[3] * v);
            if (s.had.contains(b.getX() + " " + b.getY() + " " + b.getZ())) continue;      // what it set itself last time stays
            if (buildingOn.contains(id) && !natural(b.getType())) { s.had.add(b.getX() + " " + b.getY() + " " + b.getZ()); continue; }   // what its owner has set in the meantime stays too, and is not hers to take down
            if (!natural(b.getType()) && h <= 0) { s.had.add(b.getX() + " " + b.getY() + " " + b.getZ()); continue; }      // a foundation at your feet or lower: it stays, she builds on it
            if (!natural(b.getType())) return "I cannot build there: there is " + word(b.getType()) + " at " + b.getX() + " " + b.getY() + " " + b.getZ() + ", above where you stand. I build on untouched ground or on a foundation at your feet or lower. Pick another spot.";
            if (!b.getType().isAir()) s.clear.add(b);
        }
        List<String> keys = new ArrayList<>(plan.keySet());
        keys.sort(Comparator.<String>comparingInt(k -> Integer.parseInt(k.split(" ")[1])).thenComparingInt(k -> plan.get(k).contains("part=foot") || plan.get(k).contains("half=upper") ? 1 : 0));
        for (String k : keys) {
            String[] a = k.split(" ");
            int u = Integer.parseInt(a[0]), h = Integer.parseInt(a[1]), v = Integer.parseInt(a[2]);
            s.place.add(new Object[] {w.getBlockAt(x + r[0] * u + r[1] * v, y + h, z + r[2] * u + r[3] * v), plan.get(k)});
        }
        // what there is around: trees and sand within 40 blocks, the chests at home
        Map<String, List<Block>> trees = new HashMap<>();
        List<Block> sands = new ArrayList<>();
        survey(w, x, y, z, trees, sands);
        if (farSand.containsKey(id)) { Set<Block> known = new HashSet<>(sands); for (Block b : farSand.get(id)) if (known.add(b)) sands.add(b); }
        if (farWood.containsKey(id)) for (Map.Entry<String, List<Block>> e : farWood.get(id).entrySet()) {
            List<Block> logs = trees.computeIfAbsent(e.getKey(), k -> new ArrayList<>());
            Set<Block> known = new HashSet<>(logs);
            for (Block b : e.getValue()) if (known.add(b)) logs.add(b);
        }
        Location hm = homeIn(id, w);
        List<Inventory> homeChests = hm == null ? new ArrayList<>() : stores(hm, HOME);
        List<Inventory> all = new ArrayList<>(stock(s));
        all.addAll(homeChests);
        String best = "OAK";
        int bestLogs = -1;
        for (String wd : WOODS) {
            Material lg = mat(wd + "_LOG"), pl = mat(wd + "_PLANKS");
            if (lg == null || pl == null || mat(wd + "_STAIRS") == null) continue;
            int have = count(all, k -> k == lg) + count(all, k -> k == pl) / 4 + trees.getOrDefault(wd, List.of()).size();
            if (have > bestLogs) { bestLogs = have; best = wd; }
        }
        s.wood = best;
        Material lg = mat(best + "_LOG"), pl = mat(best + "_PLANKS");
        Map<Material, Integer> want = new java.util.LinkedHashMap<>();
        for (String what : plan.values()) {
            String base = what.contains("[") ? what.substring(0, what.indexOf('[')) : what;
            if (what.contains("half=upper") || what.contains("part=foot")) continue;
            Material m = base.equals("BED") ? pl : asItem(planItem(s, base));
            if (m == null) { unknown.add(base.toLowerCase(Locale.ROOT).replace("wood:", "").replace('_', ' ')); continue; }
            want.merge(m, base.equals("BED") ? 3 : 1, Integer::sum);
        }
        double[] acc = new double[3];
        Set<String> cannot = new java.util.TreeSet<>();
        int before0 = count(stock(s), k -> k == lg) + count(stock(s), k -> k == pl) / 4, before1 = count(stock(s), k -> k == Material.COBBLESTONE),
                before2 = count(stock(s), k -> k == Material.GLASS) + count(stock(s), k -> k == Material.SAND);
        for (Map.Entry<Material, Integer> e : want.entrySet()) {
            Material k = e.getKey();
            s.keep.add(k);
            int lack = e.getValue() - count(stock(s), q -> q == k), there = Math.min(lack, count(homeChests, q -> q == k));
            if (there > 0) { take(homeChests, q -> q == k, there); stash(s, new ItemStack(k, there)); lack -= there; }
            if (lack > 0 && !raw(s, k, lack, acc, 0)) cannot.add(lack + " " + word(k));
        }
        Object[][] bring = {{lg, (int) Math.ceil(acc[0]) + 2}, {Material.COBBLESTONE, (int) Math.ceil(acc[1])}, {Material.SAND, (int) Math.ceil(acc[2])}};
        for (Object[] b : bring) {
            Material k = (Material) b[0];
            int there = Math.min((Integer) b[1], count(homeChests, q -> q == k));
            if (there > 0) { take(homeChests, q -> q == k, there); stash(s, new ItemStack(k, there)); }
        }
        for (Inventory inv : homeChests) for (ItemStack it : inv.getContents()) if (!empty(it) && Tag.WOOL.isTagged(it.getType()) && it.getAmount() >= 3 && plan.containsValue("BED[part=head,facing=south]") | plan.values().stream().anyMatch(q -> q.startsWith("BED")) && count(stock(s), q -> Tag.WOOL.isTagged(q)) < 3) {
            it.setAmount(it.getAmount() - 3);
            stash(s, new ItemStack(it.getType(), 3));
        }
        if (plan.values().stream().noneMatch(q -> q.startsWith("BED"))) s.sheep = 99;
        int logs = acc[0] > 0 ? (int) Math.ceil(acc[0]) + 2 : 0, cobble = (int) Math.ceil(acc[1]), sand = (int) Math.ceil(acc[2]);
        s.needLogs = before0 + logs;
        int woodHere = count(stock(s), k -> k == lg) + count(stock(s), k -> k == pl) / 4 + trees.getOrDefault(best, List.of()).size();
        int sandHere = count(stock(s), k -> k == Material.GLASS) + count(stock(s), k -> k == Material.SAND) + sands.size(), sandAll = before2 + sand;
        if ((woodHere < s.needLogs || sandHere < sandAll) && !farWood.containsKey(id) && !seeking.contains(id) && w.getEnvironment() == World.Environment.NORMAL) {
            int need = s.needLogs;
            boolean on = buildingOn.contains(id), lackWood = woodHere < need, lackSand = sandHere < sandAll;
            endSite(s);                                                    // what was taken out of the chests goes back into the bag
            seekFar(id, w, x, y, z, lackWood ? need : 0, lackSand ? sandAll - sandHere + sands.size() : 0, (fw, fs) -> {
                farWood.put(id, fw);
                farSand.put(id, fs);
                if (on) buildingOn.add(id);
                try { return blueprint(id, j, w, px, py, pz, facing); } finally { farWood.remove(id); farSand.remove(id); if (on) buildingOn.remove(id); }
            });
            return "I have drawn a " + title.toLowerCase(Locale.ROOT) + ". I find too little " + (lackWood ? lackSand ? "wood and sand" : "wood" : "sand") + " around here, so I go looking for it first, however far that is, and then I build.";
        }
        s.needCobble = before1 + cobble;
        s.needSand = before2 + sand;
        s.trees.addAll(trees.getOrDefault(best, List.of()));
        Location centre = new Location(w, x, y, z);
        sands.sort(Comparator.comparingDouble(b -> b.getLocation().distanceSquared(centre)));
        s.sand.addAll(sands.subList(0, Math.min(sands.size(), sand + 4)));
        holdFar(s);
        for (int dy = 4 - ymin; dy <= 40 - ymin && s.stone.size() < cobble + 12 && y - dy > w.getMinHeight() + 6; dy++) for (int u = -3; u <= 3; u++) for (int v = -3; v <= 3; v++) {   // a quarry right under it
            Block b = w.getBlockAt(x + u, y - dy, z + v);
            if (b.getType() == Material.STONE || b.getType() == Material.COBBLESTONE) s.stone.add(b);
        }
        startSite(s);
        awayLog(id, "starts to build a " + s.name + " at " + s.where + " (" + s.clear.size() + " blocks to clear, " + s.place.size() + " to set, wood: " + best.toLowerCase(Locale.ROOT) + ")");
        int mins = Math.max(3, (s.clear.size() + logs + cobble + sand + s.place.size()) * 8 / 20 / 60 + 2);
        String short1 = logs > bestLogs ? " I find only about " + Math.max(0, bestLogs) + " of the " + logs + " logs it takes, so some wood may be missing." : "";
        String miss = cannot.isEmpty() ? "" : " I have no way to make " + String.join(", ", cannot) + ": put it in a chest at home or by the plot and I use it, or I leave it out.";
        String odd = unknown.isEmpty() ? "" : " I left out what I cannot set: " + String.join(", ", unknown) + ".";
        return "I have drawn a " + s.name + ": " + cols + " x " + rows + " and " + (ymax - ymin + 1) + " high, " + s.place.size() + " blocks, at " + x + " " + y + " " + z + ". I clear the plot, fetch what I need and build it by hand, so give me about "
                + mins + " minutes." + short1 + miss + odd + " Say stop building if you change your mind.";
    }

    /** Fences, panes, bars and walls reach out to what stands next to them (they are set without the game's own tidying). */
    private static void connect(Block b) {
        BlockData d = b.getBlockData();
        BlockFace[] sides = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
        if (d instanceof org.bukkit.block.data.type.Wall wall) {
            for (BlockFace f : sides) { Material n = b.getRelative(f).getType(); wall.setHeight(f, n.isOccluding() || Tag.WALLS.isTagged(n) ? org.bukkit.block.data.type.Wall.Height.LOW : org.bukkit.block.data.type.Wall.Height.NONE); }
            b.setBlockData(wall, false);
        } else if (d instanceof org.bukkit.block.data.MultipleFacing mf && (Tag.FENCES.isTagged(b.getType()) || b.getType().name().endsWith("_PANE") || b.getType() == Material.IRON_BARS)) {
            for (BlockFace f : sides) if (mf.getAllowedFaces().contains(f)) {
                Material n = b.getRelative(f).getType();
                mf.setFace(f, n.isOccluding() || n == b.getType() || Tag.FENCES.isTagged(b.getType()) && (Tag.FENCES.isTagged(n) || Tag.FENCE_GATES.isTagged(n)) || n.name().endsWith("_PANE") && b.getType().name().endsWith("_PANE") || n.name().endsWith("GLASS") && b.getType().name().endsWith("_PANE"));
            }
            b.setBlockData(mf, false);
        }
    }

    /** "build a house here", "build a house 100 64 100 [world]", "stop building", "undo build" (its own last house),
     *  "tear down the house here / 100 64 100" (anything built, asked by an op or from the console, after a yes). */
    private boolean buildOrder(UUID id, Player p, String t, Consumer<String> tell) {
        if (t.matches("(?:stop|cancel|avbryt|stopp)(?: the)? ?(?:build|building|bygg|bygging|byggingen|bygginga|riving|rivingen)")) {
            Site s = sites.get(id);
            if (s == null) { tell.accept("I am not building anything."); return true; }
            endSite(s);
            tell.accept(s.down ? "I stopped. What is left stands as it is." : "I stopped. What I have built stands as it is; say undo build and I take it down again.");
            return true;
        }
        if (razing.containsKey(id) && t.matches("(?:(?:tear|take|pull) (?:it )?down |riv |ta ned )?(?:yes|ja)(?: (?:tear|take|pull) it down| riv)?")) {
            Object[] r = razing.remove(id);
            if (System.currentTimeMillis() - (Long) r[1] > 60_000) { tell.accept("That was more than a minute ago. Ask me again."); return true; }
            @SuppressWarnings("unchecked") List<Block> blocks = (List<Block>) r[0];
            tell.accept(raze(id, blocks, false));
            return true;
        }
        Matcher d = DOWN_WORDS.matcher(t);
        if (d.matches()) {
            Matcher c = XYZ.matcher(d.group(1));
            boolean at = c.find(), here = d.group(1).matches(".*\\b(here|her|this|that|den|det der)\\b.*");
            if (!at && !here) { tell.accept(unbuild(id)); return true; }
            if (p != null && !p.isOp()) { tell.accept("Only an op can ask me to take down something I did not build. My own last house I take down when you say undo build."); return true; }
            Block start;
            if (at) {
                World w = c.group(4) != null && Bukkit.getWorld(c.group(4)) != null ? Bukkit.getWorld(c.group(4)) : p != null ? p.getWorld() : home(id) != null ? home(id).getWorld() : Bukkit.getWorlds().get(0);
                start = w.getBlockAt(Integer.parseInt(c.group(1)), Integer.parseInt(c.group(2)), Integer.parseInt(c.group(3)));
            } else if (p != null) {
                start = p.getTargetBlockExact(10);
                if (start == null) start = p.getLocation().getBlock().getRelative(BlockFace.DOWN);
            } else { tell.accept("Where? Say tear down the house <x> <y> <z> [world]."); return true; }
            if (!builtBlock(start.getType())) {
                Block found = null;
                for (int dx = -2; dx <= 2 && found == null; dx++) for (int dy = -2; dy <= 2 && found == null; dy++) for (int dz = -2; dz <= 2 && found == null; dz++)
                    if (builtBlock(start.getRelative(dx, dy, dz).getType())) found = start.getRelative(dx, dy, dz);
                if (found == null) { tell.accept("I see nothing built at " + start.getX() + " " + start.getY() + " " + start.getZ() + "."); return true; }
                start = found;
            }
            List<Block> all = new ArrayList<>();
            Set<Block> seen = new HashSet<>(List.of(start));
            Deque<Block> open = new ArrayDeque<>(List.of(start));
            int[] lo = {start.getX(), start.getY(), start.getZ()}, hi = lo.clone();
            while (!open.isEmpty() && all.size() <= 4000) {
                Block b = open.poll();
                all.add(b);
                lo[0] = Math.min(lo[0], b.getX()); lo[1] = Math.min(lo[1], b.getY()); lo[2] = Math.min(lo[2], b.getZ());
                hi[0] = Math.max(hi[0], b.getX()); hi[1] = Math.max(hi[1], b.getY()); hi[2] = Math.max(hi[2], b.getZ());
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                    Block nb = b.getRelative(dx, dy, dz);
                    if (builtBlock(nb.getType()) && seen.add(nb)) open.add(nb);
                }
            }
            if (all.size() > 4000) { tell.accept("That is more than 4000 blocks hanging together. Too big for me to be sure what you mean."); return true; }
            razing.put(id, new Object[] {all, System.currentTimeMillis()});
            tell.accept("That is " + all.size() + " blocks, from " + lo[0] + " " + lo[1] + " " + lo[2] + " to " + hi[0] + " " + hi[1] + " " + hi[2] + ". I take it apart block by block and keep the materials. Say tear down yes within a minute.");
            return true;
        }
        if (t.equals("mergeskin")) {                                       // the skin is signed now: the figure shows it to all, and so does a merged owner
            Player on = Bukkit.getPlayer(id);
            H hh = helpers.get(id);
            if (on != null && hh != null && hh.m != null) hh.m.setProfile(profile(on));
            if (on != null && hh != null && hh.merged) wearSkin(on, false);
            return true;
        }
        if (t.equals("buildplan")) { Player on = Bukkit.getPlayer(id); buildPlan(id, on != null ? said -> say(on, said) : tell); return true; }
        if (t.startsWith("buildon") || BUILD_ON.matcher(t).matches()) { Player on = Bukkit.getPlayer(id); buildOn(id, t.startsWith("buildon") ? t.substring(7) : "", on != null ? said -> say(on, said) : tell); return true; }
        if (!PLAIN_HOUSE.matcher(t).matches() && designOrder(id, p, t, tell)) return true;      // more than "a little house": it is drawn first
        Matcher m = BUILD_WORDS.matcher(t);
        if (!m.matches()) return false;
        Matcher c = XYZ.matcher(m.group(2));
        World w;
        int x, y, z, turns = 0;
        if (c.find()) {
            w = c.group(4) != null && Bukkit.getWorld(c.group(4)) != null ? Bukkit.getWorld(c.group(4)) : p != null ? p.getWorld() : home(id) != null ? home(id).getWorld() : Bukkit.getWorlds().get(0);
            x = Integer.parseInt(c.group(1)); y = Integer.parseInt(c.group(2)); z = Integer.parseInt(c.group(3));
        } else if (p != null) {                                           // six blocks in front of the player, the door towards them
            String f = p.getFacing().name().toLowerCase(Locale.ROOT);
            int fi = Math.max(0, Arrays.asList(FACES).indexOf(f));
            turns = (fi + 2) % 4;
            Location l = p.getLocation();
            w = l.getWorld();
            x = l.getBlockX() + (f.equals("east") ? 6 : f.equals("west") ? -6 : 0); y = l.getBlockY(); z = l.getBlockZ() + (f.equals("south") ? 6 : f.equals("north") ? -6 : 0);
        } else { tell.accept("Where? Say build a house <x> <y> <z> [world] (y is the floor you walk on)."); return true; }
        tell.accept(house(id, w, x, y, z, turns, null));
        return true;
    }

    /** The trees (by kind of wood) and the open sand within 40 blocks of a plot. */
    private void survey(World w, int x, int y, int z, Map<String, List<Block>> trees, List<Block> sands) {
        int lo = Math.max(w.getMinHeight() + 1, y - 12), hi = Math.min(w.getMaxHeight() - 2, y + 30);
        for (int cx = (x - 40) >> 4; cx <= (x + 40) >> 4; cx++) for (int cz = (z - 40) >> 4; cz <= (z + 40) >> 4; cz++) {
            if (!w.isChunkGenerated(cx, cz)) continue;
            org.bukkit.ChunkSnapshot snap = w.getChunkAt(cx, cz).getChunkSnapshot();
            for (int bx = 0; bx < 16; bx++) for (int bz = 0; bz < 16; bz++) for (int yy = lo; yy <= hi; yy++) {
                Material t = snap.getBlockType(bx, yy, bz);
                if (t == Material.SAND) { if (snap.getBlockType(bx, yy + 1, bz).isAir() && sands.size() < 400) sands.add(w.getBlockAt(cx * 16 + bx, yy, cz * 16 + bz)); continue; }
                if (!Tag.LOGS.isTagged(t) || !t.name().endsWith("_LOG") || t.name().startsWith("STRIPPED") || !Tag.DIRT.isTagged(snap.getBlockType(bx, yy - 1, bz))) continue;
                Block base = w.getBlockAt(cx * 16 + bx, yy, cz * 16 + bz), top = base;
                while (top.getRelative(BlockFace.UP).getType() == t && top.getY() < hi + 30) top = top.getRelative(BlockFace.UP);
                boolean tree = false;
                for (int dx = -1; dx <= 1 && !tree; dx++) for (int dy = -1; dy <= 1 && !tree; dy++) for (int dz = -1; dz <= 1 && !tree; dz++)
                    if (top.getRelative(dx, dy, dz).getBlockData() instanceof org.bukkit.block.data.type.Leaves lv && !lv.isPersistent()) tree = true;
                if (!tree) continue;                                       // a log somebody set down, not a tree
                List<Block> logs = trees.computeIfAbsent(t.name().replace("_LOG", ""), k -> new ArrayList<>());
                Set<Block> seen = new HashSet<>(List.of(base));
                Deque<Block> open = new ArrayDeque<>(List.of(base));
                int n = 0;
                while (!open.isEmpty() && n++ < 120) {
                    Block b = open.poll();
                    if (!logs.contains(b)) logs.add(b);
                    for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                        Block nb = b.getRelative(dx, dy, dz);
                        if (nb.getType() == t && seen.add(nb)) open.add(nb);
                    }
                }
            }
        }
    }

    private final Set<UUID> seeking = new HashSet<>();
    private static final String[] TREE_BIOMES = {"forest", "birch_forest", "dark_forest", "flower_forest", "old_growth_birch_forest", "taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga",
            "snowy_taiga", "jungle", "sparse_jungle", "savanna", "cherry_grove", "windswept_forest", "grove", "swamp", "mangrove_swamp", "pale_garden", "wooded_badlands"};

    // --- Buildings in progress survive a restart: saved on shutdown, taken up again five seconds after the start ---

    private static String xyz(Block b) { return b.getX() + " " + b.getY() + " " + b.getZ(); }

    private static List<String> xyzs(Collection<Block> bs) { List<String> l = new ArrayList<>(); for (Block b : bs) l.add(xyz(b)); return l; }

    private static void blocks(World w, List<String> from, Collection<Block> to) {
        for (String k : from) {
            String[] a = k.split(" ");
            try { to.add(w.getBlockAt(Integer.parseInt(a[0]), Integer.parseInt(a[1]), Integer.parseInt(a[2]))); } catch (RuntimeException ignored) { }
        }
    }

    private void saveSites() {
        for (Site s : new ArrayList<>(sites.values())) {
            if (s.task != null) s.task.cancel();
            String k = path(s.id, "site");
            conf.set(k, null);
            conf.set(k + ".world", s.w.getName());
            conf.set(k + ".at", List.of(s.x, s.y, s.z, s.fx, s.fz, s.out, s.phase, s.at, s.needLogs, s.needCobble, s.needSand, s.sheep, s.broke, s.put));
            conf.set(k + ".where", s.where);
            conf.set(k + ".wood", s.wood);
            conf.set(k + ".name", s.name);
            conf.set(k + ".bed", s.bed);
            conf.set(k + ".flags", (s.down ? "d" : "") + (s.door ? "o" : "") + (s.plan ? "p" : ""));
            conf.set(k + ".clear", xyzs(s.clear));
            conf.set(k + ".trees", xyzs(s.trees));
            conf.set(k + ".stone", xyzs(s.stone));
            conf.set(k + ".sand", xyzs(s.sand));
            List<String> place = new ArrayList<>();
            for (Object[] o : s.place) place.add(xyz((Block) o[0]) + "|" + o[1]);
            conf.set(k + ".place", place);
            conf.set(k + ".placed", s.placed);
            conf.set(k + ".lacked", new ArrayList<>(s.lacked));
            conf.set(k + ".had", new ArrayList<>(s.had));
            List<String> keep = new ArrayList<>();
            for (Material m : s.keep) keep.add(m.name());
            conf.set(k + ".keep", keep);
            conf.set(k + ".pile", Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(s.pile.getContents())));
            awayLog(s.id, "saves the " + s.name + " at " + s.where + " for after the restart");
        }
        sites.clear();
    }

    private void loadSites() {
        for (String key : conf.getKeys(false)) {
            UUID id;
            try { id = UUID.fromString(key); } catch (IllegalArgumentException e) { continue; }
            String k = path(id, "site");
            if (!conf.contains(k)) continue;
            World w = Bukkit.getWorld(conf.getString(k + ".world", ""));
            try {
                if (w == null || sites.containsKey(id)) continue;
                List<Integer> n = conf.getIntegerList(k + ".at");
                Site s = new Site();
                s.id = id; s.w = w;
                s.x = n.get(0); s.y = n.get(1); s.z = n.get(2); s.fx = n.get(3); s.fz = n.get(4); s.out = n.get(5); s.phase = n.get(6); s.at = n.get(7);
                s.needLogs = n.get(8); s.needCobble = n.get(9); s.needSand = n.get(10); s.sheep = n.get(11); s.broke = n.get(12); s.put = n.get(13);
                s.where = conf.getString(k + ".where");
                s.wood = conf.getString(k + ".wood", "OAK");
                s.name = conf.getString(k + ".name", "little house");
                s.bed = conf.getString(k + ".bed");
                String f = conf.getString(k + ".flags", "");
                s.down = f.contains("d"); s.door = f.contains("o"); s.plan = f.contains("p");
                blocks(w, conf.getStringList(k + ".clear"), s.clear);
                blocks(w, conf.getStringList(k + ".trees"), s.trees);
                blocks(w, conf.getStringList(k + ".stone"), s.stone);
                blocks(w, conf.getStringList(k + ".sand"), s.sand);
                for (String pl : conf.getStringList(k + ".place")) {
                    int bar = pl.indexOf('|');
                    List<Block> one = new ArrayList<>();
                    blocks(w, List.of(pl.substring(0, bar)), one);
                    if (!one.isEmpty()) s.place.add(new Object[] {one.get(0), pl.substring(bar + 1)});
                }
                s.placed.addAll(conf.getStringList(k + ".placed"));
                s.lacked.addAll(conf.getStringList(k + ".lacked"));
                s.had.addAll(conf.getStringList(k + ".had"));
                for (String m : conf.getStringList(k + ".keep")) { Material mm = mat(m); if (mm != null) s.keep.add(mm); }
                String pile = conf.getString(k + ".pile");
                if (pile != null) s.pile.setContents(ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(pile)));
                startSite(s);
                holdFar(s);
                awayLog(id, "goes on with the " + s.name + " at " + s.where + " after the restart");
                Player p = Bukkit.getPlayer(id);
                if (p != null) say(p, "I go on with the " + s.name + " at " + s.where + " where I stopped.");
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Could not take up the building of " + id + " again: " + e);
            }
            conf.set(k, null);
        }
        save();
    }

    /** The forest further away stays loaded while she fells there. */
    private void holdFar(Site s) {
        Set<Long> faraway = new HashSet<>();
        List<Block> out = new ArrayList<>(s.trees);
        out.addAll(s.sand);
        for (Block b : out) if ((Math.abs(b.getX() - s.x) > 40 || Math.abs(b.getZ() - s.z) > 40) && faraway.add(((long) (b.getX() >> 4) << 32) ^ ((b.getZ() >> 4) & 0xffffffffL))) {
            s.w.addPluginChunkTicket(b.getX() >> 4, b.getZ() >> 4, plugin);
            s.held.add(new long[] {b.getX() >> 4, b.getZ() >> 4});
        }
    }

    private final Map<UUID, Map<String, List<Block>>> farWood = new HashMap<>();

    private static int most(Map<String, List<Block>> trees) {
        int n = 0;
        for (Map.Entry<String, List<Block>> e : trees.entrySet()) if (mat(e.getKey() + "_STAIRS") != null) n = Math.max(n, e.getValue().size());
        return n;
    }

    private final Map<UUID, List<Block>> farSand = new HashMap<>();
    private static final String[] SAND_BIOMES = {"desert", "beach"};

    /** Too little wood or sand by the plot: first the land already seen within 120 blocks, then the nearest forest (and
     *  the nearest desert or beach) however far that is, a little at a time so the server keeps its pace. When it is
     *  found the building is ordered again with the trees and the sand. */
    private void seekFar(UUID id, World w, int x, int y, int z, int needLogs, int needSand, java.util.function.BiFunction<Map<String, List<Block>>, List<Block>, String> again) {
        seeking.add(id);
        Map<String, List<Block>> found = new HashMap<>();
        List<Block> sand = new ArrayList<>();
        List<int[]> spots = new ArrayList<>();
        for (int dx = -80; dx <= 80; dx += 80) for (int dz = -80; dz <= 80; dz += 80) if (dx != 0 || dz != 0) spots.add(new int[] {x + dx, z + dz});
        int[] k = {0, 0, 0, 0};                                            // spot, stage, target x, target z
        int[] wood = {0, x, z, 0}, snd = {0, x, z, 0};                    // tries, last x, last z, found there (1)
        Deque<int[]> gen = new ArrayDeque<>();
        org.bukkit.scheduler.BukkitTask[] task = new org.bukkit.scheduler.BukkitTask[1];
        Runnable done = () -> {
            task[0].cancel();
            seeking.remove(id);
            Player p = Bukkit.getPlayer(id);
            if (p != null && wood[0] > 0 && most(found) >= needLogs) say(p, "I found a forest " + (int) Math.hypot(wood[1] - x, wood[2] - z) + " blocks away, at x " + wood[1] + ", z " + wood[2] + ".");
            if (p != null && snd[0] > 0 && sand.size() >= needSand) say(p, "I found sand " + (int) Math.hypot(snd[1] - x, snd[2] - z) + " blocks away, at x " + snd[1] + ", z " + snd[2] + ".");
            String res = again.apply(found, sand);
            if (p != null) say(p, res); else awayLog(id, res);
        };
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                boolean lackWood = most(found) < needLogs, lackSand = sand.size() < needSand;
                if (k[1] == 0) {
                    if (k[0] < spots.size() && (lackWood || lackSand)) {
                        int[] sp = spots.get(k[0]++);
                        if (w.isChunkGenerated(sp[0] >> 4, sp[1] >> 4)) survey(w, sp[0], w.getHighestBlockYAt(sp[0], sp[1], org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES), sp[1], found, sand);
                        return;
                    }
                    k[1] = 1;
                }
                if (k[1] == 1) {                                           // where is the nearest forest, or the nearest sand?
                    int[] t = lackWood && wood[0] < 8 ? wood : lackSand && snd[0] < 4 ? snd : null;
                    if (t == null) { done.run(); return; }
                    List<org.bukkit.block.Biome> biomes = new ArrayList<>();
                    Registry<org.bukkit.block.Biome> reg = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
                    for (String b : t == wood ? TREE_BIOMES : SAND_BIOMES) { org.bukkit.block.Biome bm = reg.get(NamespacedKey.minecraft(b)); if (bm != null) biomes.add(bm); }
                    Location from = new Location(w, t[0] == 0 ? x : t[1] + (t[1] - x >= 0 ? 96 : -96), y, t[0] == 0 ? z : t[2] + (t[2] - z >= 0 ? 96 : -96));
                    t[0]++;
                    org.bukkit.util.BiomeSearchResult r = w.locateNearestBiome(from, 12800, 64, 64, biomes.toArray(new org.bukkit.block.Biome[0]));
                    if (r == null) { t[0] = 99; return; }                  // none in this world: on to the other thing, or home
                    k[2] = t[1] = r.getLocation().getBlockX(); k[3] = t[2] = r.getLocation().getBlockZ();
                    for (int cx = (k[2] - 40) >> 4; cx <= (k[2] + 40) >> 4; cx++) for (int cz = (k[3] - 40) >> 4; cz <= (k[3] + 40) >> 4; cz++) gen.add(new int[] {cx, cz});
                    k[1] = 2;
                    return;
                }
                for (int i = 0; i < 2 && !gen.isEmpty(); i++) { int[] c = gen.poll(); w.getChunkAt(c[0], c[1]); }      // the land there is made, two chunks at a time
                if (!gen.isEmpty()) return;
                survey(w, k[2], w.getHighestBlockYAt(k[2], k[3], org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES), k[3], found, sand);
                k[1] = 1;
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Search for wood and sand stopped: " + e);
                task[0].cancel();
                seeking.remove(id);
            }
        }, 4L, 4L);
    }

    /** A little cottage, 7 x 7 with a gabled roof, built by hand: the plot is cleared, the wood is felled from the trees
     *  around (or taken from the bag and the chests at home and by the plot), stone is dug under the plot, sand is dug
     *  and smelted to glass, and every part is crafted and set one block at a time. Only on untouched ground. */
    private String house(UUID id, World w, int x, int y, int z, int turns, Map<String, List<Block>> far) {
        if (sites.containsKey(id)) return "I am already at work on a building. Say stop building first.";
        if (seeking.contains(id)) return "I am out looking for wood for the house. I will tell you when I have found it.";
        if (y < w.getMinHeight() + 24 || y > w.getMaxHeight() - 12) return "That is too high or too low to build.";
        String front = FACES[turns], right = FACES[(turns + 3) % 4], back = FACES[(turns + 2) % 4], left = FACES[(turns + 1) % 4];
        Map<String, String> plan = new java.util.LinkedHashMap<>();        // "u h v" -> what goes there
        for (int u = -3; u <= 3; u++) for (int v = -3; v <= 3; v++) {
            boolean edge = Math.abs(u) == 3 || Math.abs(v) == 3, corner = Math.abs(u) == 3 && Math.abs(v) == 3;
            plan.put(u + " -1 " + v, edge ? "cobblestone" : "PLANKS");
            if (edge) for (int h = 0; h <= 2; h++) plan.put(u + " " + h + " " + v, corner ? "LOG" : "PLANKS");
            if (Math.abs(u) == 3) plan.put(u + " 3 " + v, "PLANKS");
        }
        for (int h = 3; h <= 6; h++) for (int u = -(6 - h); u <= 6 - h; u++) { plan.put(u + " " + h + " 3", "PLANKS"); plan.put(u + " " + h + " -3", "PLANKS"); }
        for (String win : new String[] {"-3 1 0", "3 1 0", "0 1 -3", "-2 1 3", "2 1 3", "0 4 3", "0 4 -3"}) plan.put(win, "glass");
        plan.put("0 0 3", "DOOR[half=lower,facing=" + back + "]");
        plan.put("0 1 3", "DOOR[half=upper,facing=" + back + "]");
        for (int k = 0; k <= 3; k++) for (int v = -4; v <= 4; v++) {
            plan.put(-(4 - k) + " " + (3 + k) + " " + v, "STAIRS[facing=" + right + "]");
            plan.put((4 - k) + " " + (3 + k) + " " + v, "STAIRS[facing=" + left + "]");
        }
        for (int v = -4; v <= 4; v++) plan.put("0 7 " + v, "SLAB");
        plan.put("2 0 -2", "furnace[facing=" + front + "]");
        for (int h = 1; h <= 6; h++) plan.put("2 " + h + " -2", "cobblestone");
        plan.put("2 7 -2", "campfire[lit=true]");
        plan.put("-2 0 -2", "BED[part=head,facing=" + back + "]");
        plan.put("-2 0 -1", "BED[part=foot,facing=" + back + "]");
        plan.put("-2 0 1", "chest[facing=" + right + "]");
        plan.put("2 0 0", "crafting_table");
        plan.put("2 1 0", "lantern");
        int[][] rot = {{1, 0, 0, 1}, {0, -1, 1, 0}, {-1, 0, 0, -1}, {0, 1, -1, 0}};      // dx = a*u + b*v, dz = c*u + d*v
        int[] r = rot[turns];
        Site s = new Site();
        s.id = id; s.w = w; s.x = x; s.y = y; s.z = z; s.fx = r[1]; s.fz = r[3];
        s.where = w.getName() + " " + x + " " + y + " " + z;
        for (int h = 8; h >= -1; h--) for (int u = -4; u <= 4; u++) for (int v = -4; v <= 4; v++) {
            if (h == -1 && (Math.abs(u) > 3 || Math.abs(v) > 3)) continue;
            Block b = w.getBlockAt(x + r[0] * u + r[1] * v, y + h, z + r[2] * u + r[3] * v);
            if (!natural(b.getType()) && h <= 0) { s.had.add(b.getX() + " " + b.getY() + " " + b.getZ()); continue; }      // a foundation at your feet or lower: it stays, she builds on it
            if (!natural(b.getType())) return "I cannot build there: there is " + word(b.getType()) + " at " + b.getX() + " " + b.getY() + " " + b.getZ() + ", above where you stand. I build on untouched ground or on a foundation at your feet or lower. Pick another spot.";
            if (!b.getType().isAir()) s.clear.add(b);
        }
        int legs = 0;
        List<Object[]> under = new ArrayList<>();
        for (int u = -3; u <= 3; u++) for (int v = -3; v <= 3; v++) if (Math.abs(u) == 3 || Math.abs(v) == 3) for (int h = -2; h >= -5; h--) {   // legs down to the ground
            Block b = w.getBlockAt(x + r[0] * u + r[1] * v, y + h, z + r[2] * u + r[3] * v);
            if (b.getType().isOccluding() || !natural(b.getType())) break;
            under.add(new Object[] {b, "cobblestone"});
            legs++;
        }
        s.place.addAll(under);
        List<String> keys = new ArrayList<>(plan.keySet());
        keys.sort(java.util.Comparator.comparingInt(k -> Integer.parseInt(k.split(" ")[1])));
        int nPlanks = 0, nLogs = 0, nStairs = 0, nSlab = 0, nCobble = legs, nGlass = 0;
        for (String k : keys) {
            String[] a = k.split(" ");
            int u = Integer.parseInt(a[0]), h = Integer.parseInt(a[1]), v = Integer.parseInt(a[2]);
            String what = plan.get(k);
            s.place.add(new Object[] {w.getBlockAt(x + r[0] * u + r[1] * v, y + h, z + r[2] * u + r[3] * v), what});
            if (what.equals("PLANKS")) nPlanks++; else if (what.equals("LOG")) nLogs++; else if (what.startsWith("STAIRS")) nStairs++;
            else if (what.equals("SLAB")) nSlab++; else if (what.equals("cobblestone")) nCobble++; else if (what.equals("glass")) nGlass++;
        }
        int planks = nPlanks + (nStairs + 3) / 4 * 6 + (nSlab + 5) / 6 * 3 + 6 + 8 + 4 + 3 + 2;      // door, chest, table, bed, sticks
        int logsAll = nLogs + 3 + 2 + (planks + 3) / 4;                                              // campfire, charcoal
        // what there is: the bag, the chests by the plot and at home, and the trees and the sand within 40 blocks
        Map<String, List<Block>> trees = new HashMap<>();
        List<Block> sands = new ArrayList<>();
        survey(w, x, y, z, trees, sands);
        if (farSand.containsKey(id)) { Set<Block> known = new HashSet<>(sands); for (Block b : farSand.get(id)) if (known.add(b)) sands.add(b); }
        if (far != null) for (Map.Entry<String, List<Block>> e : far.entrySet()) {       // the trees found further away, after the near ones
            List<Block> logs = trees.computeIfAbsent(e.getKey(), k -> new ArrayList<>());
            Set<Block> known = new HashSet<>(logs);
            for (Block b : e.getValue()) if (known.add(b)) logs.add(b);
        }
        Location hm = homeIn(id, w);
        List<Inventory> homeChests = hm == null ? new ArrayList<>() : stores(hm, HOME);
        List<Inventory> all = new ArrayList<>(stock(s));
        all.addAll(homeChests);
        String best = null;
        int bestLogs = -1;
        for (String wd : WOODS) {
            Material lg = mat(wd + "_LOG"), pl = mat(wd + "_PLANKS");
            if (lg == null || pl == null || mat(wd + "_STAIRS") == null) continue;
            int have = count(all, k -> k == lg) + count(all, k -> k == pl) / 4 + trees.getOrDefault(wd, List.of()).size();
            if (have > bestLogs) { bestLogs = have; best = wd; }
        }
        int sandHave = sands.size() + count(all, k -> k == Material.GLASS) + count(all, k -> k == Material.SAND);
        boolean lackWood = best == null || bestLogs < logsAll, lackSand = sandHave < nGlass;
        if ((lackWood || lackSand) && far == null && w.getEnvironment() == World.Environment.NORMAL) {
            seekFar(id, w, x, y, z, lackWood ? logsAll : 0, lackSand ? nGlass - sandHave + sands.size() : 0, (fw, fs) -> {
                farSand.put(id, fs);
                try { return house(id, w, x, y, z, turns, fw); } finally { farSand.remove(id); }
            });
            return "A house takes about " + logsAll + " logs of one kind of wood and sand for " + nGlass + " windows, and I find too little " + (lackWood ? lackSand ? "wood and sand" : "wood" : "sand")
                    + " around here. I go looking for it, however far that is, and come back to build.";
        }
        if (best == null || bestLogs < logsAll) return "A house takes about " + logsAll + " logs of one kind of wood, and I find only " + Math.max(0, bestLogs) + (best == null ? "" : " of " + best.toLowerCase(Locale.ROOT).replace('_', ' '))
                + (far == null ? " (trees within 40 blocks, my bag and the chests at home)" : ", even in the nearest forest I could find") + ". Pick a spot nearer a forest, or put logs in a chest at home.";
        s.wood = best;
        Material lg = mat(best + "_LOG"), pl = mat(best + "_PLANKS");
        // from the chests at home into the bag: what the house needs
        Object[][] bring = {{lg, logsAll}, {pl, planks}, {Material.COBBLESTONE, nCobble + 8}, {Material.GLASS, nGlass}, {Material.SAND, nGlass}, {Material.LANTERN, 1}, {Material.IRON_INGOT, 1},
                {Material.COAL, 2}, {Material.CHARCOAL, 2}};
        for (Object[] b : bring) {
            Material k = (Material) b[0];
            int want = (Integer) b[1] - count(stock(s), q -> q == k), there = Math.min(want, count(homeChests, q -> q == k));
            if (there > 0) { take(homeChests, q -> q == k, there); stash(s, new ItemStack(k, there)); }
        }
        for (Inventory inv : homeChests) for (ItemStack it : inv.getContents()) if (!empty(it) && Tag.WOOL.isTagged(it.getType()) && it.getAmount() >= 3 && count(stock(s), q -> Tag.WOOL.isTagged(q)) < 3) {
            it.setAmount(it.getAmount() - 3);
            stash(s, new ItemStack(it.getType(), 3));
        }
        s.needLogs = logsAll;
        s.needCobble = nCobble + 8;
        s.needSand = nGlass;
        s.trees.addAll(trees.getOrDefault(best, List.of()));
        Location centre = new Location(w, x, y, z);
        sands.sort(java.util.Comparator.comparingDouble(b -> b.getLocation().distanceSquared(centre)));
        s.sand.addAll(sands.subList(0, Math.min(sands.size(), nGlass + 4)));
        holdFar(s);
        for (int dy = 4; dy <= 22 && s.stone.size() < nCobble + 12; dy++) for (int u = -2; u <= 2; u++) for (int v = -2; v <= 2; v++) {      // a little quarry right under the house
            Block b = w.getBlockAt(x + u, y - dy, z + v);
            if (b.getType() == Material.STONE || b.getType() == Material.COBBLESTONE) s.stone.add(b);
        }
        startSite(s);
        awayLog(id, "starts to build a house at " + s.where + " (" + s.clear.size() + " blocks to clear, " + s.place.size() + " to set, wood: " + best.toLowerCase(Locale.ROOT) + ")");
        int mins = Math.max(3, (s.clear.size() + logsAll + nCobble + s.place.size()) * 8 / 20 / 60 + 2);
        return "I will build a little house of " + best.toLowerCase(Locale.ROOT).replace('_', ' ') + " at " + x + " " + y + " " + z + ", the door to the " + front + ". I clear the plot, fetch what I need and build it by hand, so give me about "
                + mins + " minutes. Say stop building if you change your mind.";
    }

    private void startSite(Site s) {
        for (int cx = (s.x - 44) >> 4; cx <= (s.x + 44) >> 4; cx++) for (int cz = (s.z - 44) >> 4; cz <= (s.z + 44) >> 4; cz++) { s.w.addPluginChunkTicket(cx, cz, plugin); s.held.add(new long[] {cx, cz}); }
        H h = helpers.get(s.id);
        if (h != null) { s.stance = h.stance; s.anchor = h.anchor; }
        sites.put(s.id, s);
        s.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> { try { step(s); } catch (RuntimeException e) { plugin.getLogger().warning("Building stopped: " + e); endSite(s); } }, 8L, 8L);
    }

    private void endSite(Site s) {
        if (s.task != null) s.task.cancel();
        if (sites.get(s.id) == s) sites.remove(s.id);
        for (long[] c : s.held) s.w.removePluginChunkTicket((int) c[0], (int) c[1], plugin);
        H h = helpers.get(s.id);
        if (h != null && s.stance != null) { h.stance = s.stance; h.anchor = s.anchor; }
        if (h != null && h.watching && Bukkit.getPlayer(s.id) == null) {   // built while you were away: back to its post at home, where the away job takes over
            Location post = home(s.id) != null ? home(s.id) : h.w.getLocation();
            h.stance = Stance.GUARD;
            h.anchor = post;
            if (h.w.getWorld() != post.getWorld() || h.w.getLocation().distanceSquared(post) > 32 * 32) h.w.teleport(post);
        }
        for (ItemStack it : s.pile.getContents()) {                         // what is left over: into the bag, the chests at home, or on the ground by the plot
            if (empty(it)) continue;
            Map<Integer, ItemStack> rest = group(s.w).equals(grp(s.id)) ? bag(s.id).addItem(it) : new HashMap<>(Map.of(0, it));      // never into the bag of another world
            dirty.add(s.id);
            Location hm = homeIn(s.id, s.w);
            if (!rest.isEmpty() && hm != null) for (Inventory inv : stores(hm, HOME)) { if (rest.isEmpty()) break; rest = inv.addItem(rest.values().iterator().next()); }
            for (ItemStack r : rest.values()) s.w.dropItemNaturally(new Location(s.w, s.x + 0.5 + s.fx * s.out, s.y, s.z + 0.5 + s.fz * s.out), r);
        }
        s.pile.clear();
        if (!s.down && !s.placed.isEmpty()) { conf.set(path(s.id, "built.world"), s.w.getName()); conf.set(path(s.id, "built.blocks"), s.placed); save(); }
        flushBags();
    }

    /** The figure goes to where the work is. False while it is still on its way (it is fetched after five seconds). */
    private boolean reach(Site s, Block b) {
        H h = helpers.get(s.id);
        if (h == null || h.merged || h.w == null || h.w.isDead()) return true;
        Location l = b.getLocation().add(0.5, 0, 0.5);
        h.stance = Stance.GUARD;
        h.anchor = l;
        Location at = h.w.getLocation();
        boolean near = at.getWorld() == s.w && Math.abs(at.getX() - l.getX()) <= 9 && Math.abs(at.getZ() - l.getZ()) <= 9;
        if (!near) {
            if (++s.far > 12) { h.w.teleport(new Location(s.w, s.x + 0.5 + s.fx * s.out, s.y, s.z + 0.5 + s.fz * s.out)); s.far = 0; if (Math.abs(b.getX() - s.x) > 12 || Math.abs(b.getZ() - s.z) > 12) h.w.teleport(l.clone().add(1, 1, 0)); }
            return false;
        }
        s.far = 0;
        Material held = toolFor(b.getType());
        if (held != null) wield(h, held);
        try { h.m.swingMainHand(); } catch (RuntimeException ignored) { }
        return true;
    }

    private void take1(Site s, Block b, boolean keepAll) {
        if (b.getState() instanceof org.bukkit.inventory.InventoryHolder holder) {
            for (ItemStack it : holder.getInventory().getContents()) if (!empty(it)) stash(s, it);
            holder.getInventory().clear();
        }
        Material m = b.getType();
        for (ItemStack it : drops(b)) {
            Material k = it.getType();
            if (keepAll || s.keep.contains(k) || Tag.LOGS.isTagged(k) || Tag.PLANKS.isTagged(k) || k == Material.COBBLESTONE || k == Material.SAND || Tag.WOOL.isTagged(k)) stash(s, it);
            else b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), it);
        }
        b.getWorld().playSound(b.getLocation(), m.createBlockData().getSoundGroup().getBreakSound(), 0.8f, 1f);
        b.setType(Material.AIR, s.down);
        s.broke++;
        s.from = null;
    }

    /** One thing every 0.4 seconds: a block broken, a block set, or a wait for the furnace. */
    private void step(Site s) {
        if (paused() || s.paused) return;
        if (s.wait > 0) { s.wait--; return; }
        UUID id = s.id;
        if (s.down) {
            Block b = s.clear.peek();
            while (b != null && b.getType().isAir()) { s.clear.poll(); b = s.clear.peek(); }
            if (b == null) {
                endSite(s);
                conf.set(path(id, "built"), null);
                save();
                note(s, "It is down: " + s.broke + " blocks taken apart at " + s.where + ". The materials are in my bag and the chests at home; what did not fit lies on the ground there.");
                return;
            }
            if (!reach(s, b)) return;
            s.clear.poll();
            take1(s, b, true);
            return;
        }
        if (s.phase == 0) {                                                // the plot
            Block b = s.clear.peek();
            while (b != null && b.getType().isAir()) { s.clear.poll(); b = s.clear.peek(); }
            if (b == null) { s.phase = 1; note(s, "The plot is clear. Now I fetch what I need: wood, stone and sand."); return; }
            if (!reach(s, b)) return;
            s.clear.poll();
            take1(s, b, false);
            return;
        }
        Material lg = mat(s.wood + "_LOG"), pl = mat(s.wood + "_PLANKS");
        if (s.phase == 1) {
            List<Inventory> from = stock(s);
            int logs = count(from, k -> k == lg) + count(from, k -> k == pl) / 4;
            Block b = null;
            if (logs < s.needLogs) { while ((b = s.trees.peek()) != null && b.getType() != lg) s.trees.poll(); if (b != null) s.trees.poll(); }
            if (b == null && count(from, k -> k == Material.COBBLESTONE) < s.needCobble) { while ((b = s.stone.peek()) != null && b.getType() != Material.STONE && b.getType() != Material.COBBLESTONE) s.stone.poll(); if (b != null) s.stone.poll(); }
            if (b == null && count(from, k -> k == Material.GLASS) + count(from, k -> k == Material.SAND) < s.needSand) { while ((b = s.sand.peek()) != null && b.getType() != Material.SAND) s.sand.poll(); if (b != null) s.sand.poll(); }
            if (b != null) {
                if (!reach(s, b)) { if (lg == b.getType()) ((ArrayDeque<Block>) s.trees).addFirst(b); else if (b.getType() == Material.SAND) ((ArrayDeque<Block>) s.sand).addFirst(b); else ((ArrayDeque<Block>) s.stone).addFirst(b); return; }
                take1(s, b, false);
                return;
            }
            if (count(from, k -> Tag.WOOL.isTagged(k)) < 3 && s.sheep < 4) {                  // wool for the bed
                s.sheep++;
                for (Entity e : s.w.getNearbyEntities(new Location(s.w, s.x, s.y, s.z), 40, 16, 40)) if (e instanceof Sheep sh && sh.isAdult() && !sh.isSheared()) {
                    sh.setSheared(true);
                    org.bukkit.DyeColor col = sh.getColor();
                    Material wool = mat((col == null ? "WHITE" : col.name()) + "_WOOL");
                    stash(s, new ItemStack(wool == null ? Material.WHITE_WOOL : wool, 1 + ThreadLocalRandom.current().nextInt(3)));
                    s.w.playSound(sh.getLocation(), Sound.ENTITY_SHEEP_SHEAR, 1f, 1f);
                    s.from = null;
                    return;
                }
            }
            s.phase = 2;
            note(s, "I have what I could find. Now I build.");
            return;
        }
        if (s.at >= s.place.size()) {
            if (s.plan) for (Object[] pb : s.place) connect((Block) pb[0]);
            endSite(s);
            String lack = s.lacked.isEmpty() ? "" : " I had to leave out: " + String.join(", ", s.lacked) + ".";
            awayLog(id, "the " + s.name + " at " + s.where + " is finished (" + s.put + " blocks set, " + s.broke + " broken)" + lack);
            Player owner = Bukkit.getPlayer(id);
            if (owner != null) say(owner, "Your " + s.name + " at " + s.x + " " + s.y + " " + s.z + " is ready! I built it by hand." + lack + " Say undo build and I take it down again.");
            else {
                List<String> finds = conf.getStringList(path(id, "away.finds"));
                if (finds.size() < 30) { finds.add("a " + s.name + " I built for you at " + s.x + " " + s.y + " " + s.z); conf.set(path(id, "away.finds"), finds); }
            }
            return;
        }
        Block b = (Block) s.place.get(s.at)[0];
        String what = (String) s.place.get(s.at)[1], base = what.contains("[") ? what.substring(0, what.indexOf('[')) : what, props = what.contains("[") ? what.substring(what.indexOf('[')) : "";
        if (!b.getType().isAir() && s.had.contains(b.getX() + " " + b.getY() + " " + b.getZ())) { s.at++; return; }      // it stands already
        if (!reach(s, b)) return;
        s.at++;
        Material item;
        boolean free = false;
        switch (base) {
            case "LOG" -> item = lg;
            case "PLANKS" -> item = pl;
            case "stone", "stone_bricks", "cobblestone" -> { item = mat(base); if (s.plan && item != null && !ensure(s, item, 1, 0) && item != Material.COBBLESTONE && ensure(s, Material.COBBLESTONE, 1, 0)) item = Material.COBBLESTONE; }
            case "STAIRS" -> item = mat(s.wood + "_STAIRS");
            case "SLAB" -> item = mat(s.wood + "_SLAB");
            case "DOOR" -> { item = mat(s.wood + "_DOOR"); if (props.contains("upper")) { if (!s.door) return; free = true; } }
            case "BED" -> {
                if (props.contains("foot")) { if (s.bed == null) return; item = mat(s.bed + "_BED"); free = true; }
                else {
                    item = null;
                    List<Inventory> from = stock(s);
                    for (org.bukkit.DyeColor c : org.bukkit.DyeColor.values()) {
                        Material wool = mat(c.name() + "_WOOL");
                        if (wool == null || count(from, k -> k == wool) < 3 || !ensure(s, pl, 3, 0)) continue;
                        take(stock(s), k -> k == wool, 3);
                        take(stock(s), k -> k == pl, 3);
                        s.bed = c.name();
                        item = mat(s.bed + "_BED");
                        free = true;
                        break;
                    }
                    if (item == null) { s.lacked.add("the bed (no three pieces of wool of one colour)"); return; }
                }
            }
            default -> item = base.startsWith("WOOD:") ? mat(s.wood + "_" + base.substring(5)) : mat(base);
        }
        if (item == null) { if (s.plan) s.lacked.add(base.toLowerCase(Locale.ROOT).replace("wood:", "").replace('_', ' ')); return; }
        Material block = item;                                             // a wall torch is set, a torch is what it takes
        item = asItem(item);
        if (!free && !ensure(s, item, 1, 0)) {
            block = item;
            if (item == Material.COBBLESTONE && ensure(s, pl, 1, 0)) { item = pl; props = ""; }                    // no stone: wood will do
            else if (item == Material.LANTERN && ensure(s, Material.TORCH, 1, 0)) { item = Material.TORCH; props = ""; }
            else { s.lacked.add(item == Material.GLASS ? "the window glass (no sand to be found)" : word(item)); return; }
        }
        if (!free) { Material k = item; take(stock(s), q -> q == k, 1); dirty.add(id); }
        if (base.equals("DOOR")) s.door = true;
        BlockData data;
        if (block != item && (!block.isBlock() || !props.isEmpty() && item != asItem(block))) block = item;      // swapped for something else on the way
        if (!block.isBlock()) { s.lacked.add(word(block)); return; }
        try { data = Bukkit.createBlockData(block.getKey() + props); } catch (IllegalArgumentException e) { data = block.createBlockData(); }
        b.setBlockData(data, false);
        b.getWorld().playSound(b.getLocation(), data.getSoundGroup().getPlaceSound(), 0.8f, 1f);
        s.placed.add(b.getX() + " " + b.getY() + " " + b.getZ());
        s.put++;
        s.from = null;
    }

    /** Takes its own last house down again, block by block; the materials go into the bag. */
    private String unbuild(UUID id) {
        Site running = sites.get(id);
        if (running != null) { if (running.down) return "I am taking it down already."; endSite(running); }
        List<String> was = conf.getStringList(path(id, "built.blocks"));
        World w = Bukkit.getWorld(conf.getString(path(id, "built.world"), ""));
        if (was.isEmpty() || w == null) return "I have no house of mine to take down.";
        List<Block> blocks = new ArrayList<>();
        for (String k : was) {
            String[] a = k.split(" ");
            try { blocks.add(w.getBlockAt(Integer.parseInt(a[0]), Integer.parseInt(a[1]), Integer.parseInt(a[2]))); } catch (RuntimeException ignored) { }
        }
        return raze(id, blocks, true);
    }

    private String raze(UUID id, List<Block> blocks, boolean own) {
        if (sites.containsKey(id)) return "I am already at work on a building. Say stop building first.";
        if (blocks.isEmpty()) return "There is nothing to take down.";
        Site s = new Site();
        s.id = id; s.down = true; s.w = blocks.get(0).getWorld();
        blocks.sort(java.util.Comparator.comparingInt((Block b) -> -b.getY()));
        s.x = blocks.get(blocks.size() - 1).getX(); s.y = blocks.get(blocks.size() - 1).getY() + 1; s.z = blocks.get(blocks.size() - 1).getZ();
        s.where = s.w.getName() + " " + s.x + " " + s.y + " " + s.z;
        s.clear.addAll(blocks);
        startSite(s);
        awayLog(id, "starts to take down " + blocks.size() + " blocks at " + s.where);
        return "I take it down block by block, from the top: " + blocks.size() + " blocks, about " + Math.max(1, blocks.size() * 8 / 20 / 60 + 1) + " minutes. The materials go into my bag. Say stop building to stop me.";
    }

    /** Work at a saved place: it looks at what is there. Farmland within 32 blocks: the fields are tended as at home.
     *  Else a spawner or dark, roofed floors: the mob farm is run. Else open water within 32 blocks: it fishes. */
    private int awayPlace(UUID id, Location at) {
        Yard y = yard(id, at);
        String name = conf.getString(path(id, "away.place"), "the place"), see = null;
        boolean fields = false;
        for (Block b : y.soil) if (Math.abs(b.getX() - at.getBlockX()) <= 32 && Math.abs(b.getZ() - at.getBlockZ()) <= 32) { fields = true; break; }
        int got = 0;
        if (farms(id).contains(slug(name))) { see = "my mob farm"; got = Math.max(0, awayMobs(id, at, true)); }
        else if (fields) { see = "fields"; got = awayFarm(id, at); }
        else if ((got = awayMobs(id, at, false)) >= 0) see = "a mob farm";
        else {
            got = 0;
            if (y.water != null && Math.abs(y.water.getX() - at.getBlockX()) <= 32 && Math.abs(y.water.getZ() - at.getBlockZ()) <= 32) { see = "water to fish in"; got = awayFish(id, at); }
        }
        if (awayTold.putIfAbsent(id + ":warn:see", System.currentTimeMillis()) == null)
            awayLog(id, see == null ? "NOTE: I see nothing to do at " + name + ": no fields, no mob farm and no water" : "at " + name + " I see " + see + ", so that is my job here");
        return got;
    }

    /** What kind of loot something is: each kind gets chests of its own at the mob farm. */
    private static String lootKind(ItemStack it) {
        Material m = it.getType();
        if (m.getMaxDurability() > 0 || it.getType() == Material.ENCHANTED_BOOK) return "gear";
        return switch (m) {
            case BONE, BONE_MEAL, ARROW, TIPPED_ARROW, SPECTRAL_ARROW -> "skeleton";
            case ROTTEN_FLESH, IRON_INGOT, IRON_NUGGET, CARROT, POTATO, BAKED_POTATO, POISONOUS_POTATO, COPPER_INGOT -> "zombie";
            case GUNPOWDER -> "creeper";
            case STRING, SPIDER_EYE -> "spider";
            case GLASS_BOTTLE, GLOWSTONE_DUST, REDSTONE, SUGAR, STICK -> "witch";
            case ENDER_PEARL, SLIME_BALL, BLAZE_ROD, MAGMA_CREAM, GOLD_NUGGET, GOLD_INGOT, PHANTOM_MEMBRANE -> "rare";
            default -> m.name().startsWith("MUSIC_DISC") ? "creeper" : "other";
        };
    }

    /** True when a hopper empties into this chest: it is where the farm delivers, not a place to keep things. */
    private static boolean fed(Block chest) {
        for (BlockFace f : new BlockFace[] {BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block n = chest.getRelative(f);
            if (n.getBlockData() instanceof org.bukkit.block.data.type.Hopper hp && n.getRelative(hp.getFacing()).equals(chest)) return true;
        }
        return false;
    }

    /** Once a minute at the mob farm: what the hoppers have delivered (and the loot in the bag) goes into the other
     *  chests within 16 blocks, each kind of loot in chests of its own; when there is no room, a new chest is made
     *  (eight planks or two logs) and set down next to them. Returns how many things were moved. */
    private int sortFarm(UUID id, Location at) {
        List<Inventory> inbox = new ArrayList<>(), keep = new ArrayList<>();
        for (Inventory inv : stores(at, 16)) {
            boolean in = false;
            if (inv.getHolder() instanceof org.bukkit.block.DoubleChest dc) {
                if (dc.getLeftSide() instanceof org.bukkit.block.Chest a && fed(a.getBlock())) in = true;
                if (dc.getRightSide() instanceof org.bukkit.block.Chest b && fed(b.getBlock())) in = true;
            } else if (inv.getLocation() != null) in = fed(inv.getLocation().getBlock());
            (in ? inbox : keep).add(inv);
        }
        List<Inventory> sources = new ArrayList<>(inbox);
        Inventory bag = bag(id);
        sources.add(bag);
        int moved = 0;
        for (Inventory src : sources) for (int i = 0; i < src.getSize(); i++) {
            ItemStack it = src.getItem(i);
            if (empty(it)) continue;
            String kind = lootKind(it);
            if (src == bag && kind.equals("other")) continue;                // its own things stay in the bag
            int before = it.getAmount();
            ItemStack rest = it.clone();
            for (int pass = 0; pass < 3 && rest != null; pass++) {
                if (pass == 2) { Inventory fresh = farmChest(id, at, keep, inbox); if (fresh == null) break; keep.add(fresh); }
                for (Inventory to : keep) {
                    boolean same = false, bare = true;
                    for (ItemStack c : to.getContents()) if (!empty(c)) { bare = false; if (lootKind(c).equals(kind)) { same = true; break; } }
                    if (pass == 0 ? !same : !bare) continue;
                    Map<Integer, ItemStack> left = to.addItem(rest);
                    rest = left.isEmpty() ? null : left.values().iterator().next();
                    if (rest == null) break;
                }
            }
            int gone = before - (rest == null ? 0 : rest.getAmount());
            if (gone > 0) { src.setItem(i, rest); moved += gone; if (src == bag) dirty.add(id); }
            if (rest != null) { awayWarn(id, "farmfull", "the chests at the mob farm are full and I have no wood for a new one (eight planks or two logs in a chest there)"); bump(id, "sorted", moved); return moved; }
        }
        bump(id, "sorted", moved);
        return moved;
    }

    /** A new chest by the storage chests of the mob farm. Null when there is no wood or no free spot. */
    private Inventory farmChest(UUID id, Location at, List<Inventory> keep, List<Inventory> inbox) {
        List<Inventory> from = new ArrayList<>(List.of(bag(id)));
        from.addAll(keep);
        from.addAll(inbox);
        boolean planks = count(from, Tag.PLANKS::isTagged) >= 8;
        if (!planks && count(from, Tag.LOGS::isTagged) < 2) return null;
        Block spot = null;
        List<Inventory> by = new ArrayList<>(keep);
        by.addAll(inbox);
        find:
        for (Inventory inv : by) {
            if (inv.getLocation() == null) continue;
            for (int up = 0; up <= 1; up++) for (BlockFace f : SIDES) {
                Block b = inv.getLocation().getBlock().getRelative(f).getRelative(0, up, 0);
                if (b.getType().isAir() && b.getRelative(BlockFace.UP).getType().isAir() && (b.getRelative(BlockFace.DOWN).getType().isSolid() || up == 1 && b.getRelative(BlockFace.DOWN).getType() == Material.CHEST)
                        && !fed(b) && b.getLocation().getNearbyPlayers(1.5).isEmpty()) { spot = b; break find; }
            }
        }
        if (spot == null) return null;
        if (planks) take(from, Tag.PLANKS::isTagged, 8); else take(from, Tag.LOGS::isTagged, 2);
        dirty.add(id);
        spot.setType(Material.CHEST);
        spot.getWorld().playSound(spot.getLocation(), Sound.BLOCK_WOOD_PLACE, 0.8f, 1f);
        awayLog(id, "made a new chest at the mob farm at " + spot.getX() + " " + spot.getY() + " " + spot.getZ());
        List<String> finds = conf.getStringList(path(id, "away.finds"));
        if (finds.size() < 30) { finds.add("a new chest I made at the mob farm at " + spot.getX() + " " + spot.getY() + " " + spot.getZ()); conf.set(path(id, "away.finds"), finds); }
        return spot.getState() instanceof org.bukkit.block.Chest c ? c.getInventory() : null;
    }

    /** The saved places that are mob farms. */
    private List<String> farms(UUID id) {
        List<String> names = new ArrayList<>();
        for (String n : conf.getStringList(path(id, "mobfarms"))) if (conf.contains(path(id, "places." + n)) && !names.contains(n)) names.add(n);
        return names;
    }

    private void farmAdd(UUID id, String name) {
        List<String> names = farms(id);
        if (!names.contains(name)) { names.add(name); conf.set(path(id, "mobfarms"), names); }
    }

    /** Which mob farm is meant: the one named, the only one there is, or (starting with ?) a question back. */
    private String farmPick(UUID id, String arg) {
        String name = slug(arg);
        if (!name.isEmpty()) return name;
        if (conf.contains(path(id, "places.mobfarm"))) farmAdd(id, "mobfarm");
        List<String> names = farms(id);
        if (names.isEmpty()) return "?I have no mob farm yet. Stand at it in the game, where I should strike from, and say mobfarm.";
        if (names.size() == 1) return names.get(0);
        return "?Which one? I know " + String.join(", ", names) + ". Say mobfarm and the name.";
    }

    /** "mobfarm", "mobfarm <name>", "mobfarm <name> here": it runs a mob farm from a post outside it. With no name it
     *  is the only mob farm there is; with none yet (or a new name, or "here") the post is where its owner stands. */
    private void mobfarm(Player p, H h, String arg) {
        UUID id = p.getUniqueId();
        String a = arg.toLowerCase(Locale.ROOT).trim();
        boolean here = a.matches("(?:.* )?(?:here|her)");
        if (here) a = a.replaceAll("(?:^| )(?:here|her)$", "").trim();
        String name = slug(a);
        List<String> names = farms(id);
        if (conf.contains(path(id, "places.mobfarm")) && !names.contains("mobfarm")) names.add("mobfarm");
        if (name.isEmpty()) {
            if (names.size() > 1 && !here) { say(p, "Which one? I know " + String.join(", ", names) + ". Say mobfarm and the name."); return; }
            name = names.size() == 1 && !here ? names.get(0) : names.isEmpty() ? "mobfarm" : "mobfarm" + (names.size() + 1);
        }
        boolean made = here || !conf.contains(path(id, "places." + name));
        if (made) conf.set(path(id, "places." + name), spot(p.getLocation()));
        farmAdd(id, name);
        save();
        Location post = place(conf.getString(path(id, "places." + name)));
        if (post == null) { say(p, "I cannot find " + name + " any more."); return; }
        release(h);
        h.farm = name;
        h.farmSort = System.currentTimeMillis() - 50_000;
        h.stance = Stance.GUARD;
        h.anchor = post;
        mobYards.remove(id);
        if (h.w.getWorld() != post.getWorld() || h.w.getLocation().distanceSquared(post) > 2.25) h.w.teleport(post);
        say(p, (made ? "This post is mob farm " + name + " now. " : "I run mob farm " + name + ". ") + "I stay outside, strike what I can reach with my sword and sort what lands in the chests every minute. I keep at it when you log out. Say stop when I should come with you.");
    }

    private static final class MobYard { final List<Block> floors = new ArrayList<>(); EntityType kind; long at; int rounds; }
    private final Map<UUID, MobYard> mobYards = new HashMap<>();
    private static final EntityType[] MOBS = {EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SKELETON, EntityType.CREEPER, EntityType.SPIDER};

    /** The mob farm at the saved place "mobfarm". No monsters come while nobody is logged in, so the helper calls
     *  them itself: three every ten seconds on the dark, roofed floors within 24 blocks (from the level of the place
     *  and 48 blocks up), the kind of a spawner when there is one within 24 blocks. The farm does the rest; what gets
     *  within 8 blocks of the place is killed, the drops go in the bag and the experience to the owner. */
    private int awayMobs(UUID id, Location at, boolean sure) {
        World w = at.getWorld();
        MobYard y = mobYards.get(id);
        long now = System.currentTimeMillis();
        if (y == null || now - y.at > 300_000) {
            y = new MobYard();
            y.at = now;
            int hx = at.getBlockX(), hy = at.getBlockY(), hz = at.getBlockZ();
            int lo = Math.max(w.getMinHeight() + 1, hy - 1), hi = Math.min(w.getMaxHeight() - 3, hy + 48);
            for (int cx = (hx - 24) >> 4; cx <= (hx + 24) >> 4; cx++) for (int cz = (hz - 24) >> 4; cz <= (hz + 24) >> 4; cz++) {
                if (!w.isChunkGenerated(cx, cz)) continue;
                org.bukkit.Chunk ch = w.getChunkAt(cx, cz);
                for (org.bukkit.block.BlockState st : ch.getTileEntities()) if (st instanceof org.bukkit.block.CreatureSpawner sp && sp.getSpawnedType() != null
                        && Math.abs(st.getX() - hx) <= 24 && Math.abs(st.getZ() - hz) <= 24) y.kind = sp.getSpawnedType();
                org.bukkit.ChunkSnapshot snap = ch.getChunkSnapshot();
                for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                    int bx = cx * 16 + x, bz = cz * 16 + z;
                    if (Math.abs(bx - hx) > 24 || Math.abs(bz - hz) > 24 || Math.abs(bx - hx) <= 8 && Math.abs(bz - hz) <= 8) continue;
                    for (int yy = lo; yy <= hi; yy++)
                        if (snap.getBlockType(x, yy, z).isAir() && snap.getBlockType(x, yy + 1, z).isAir() && snap.getBlockType(x, yy - 1, z).isOccluding()
                                && snap.getBlockEmittedLight(x, yy, z) == 0 && snap.getBlockSkyLight(x, yy, z) == 0) y.floors.add(w.getBlockAt(bx, yy, bz));
                }
            }
            mobYards.put(id, y);
        }
        if (!sure && y.kind == null && y.floors.size() < 64) return -1;              // no spawner and no dark floors to speak of: not a mob farm
        int got = 0, alive = 0, killed = 0;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<org.bukkit.entity.Monster> prey = new ArrayList<>();
        for (Entity e : w.getNearbyEntities(at, 32, 56, 32)) {
            if (e instanceof Item item && item.getTicksLived() > 20 && Math.abs(e.getLocation().getX() - at.getX()) <= 8 && Math.abs(e.getLocation().getZ() - at.getZ()) <= 8 && Math.abs(e.getLocation().getY() - at.getY()) <= 6) {
                List<ItemStack> one = List.of(item.getItemStack());
                if (addToBag(id, one) == item.getItemStack().getAmount()) { awayNote(id, one); item.remove(); got++; }
                continue;
            }
            if (!(e instanceof org.bukkit.entity.Monster m) || m.customName() != null) continue;
            Location l = m.getLocation();
            if (Math.abs(l.getX() - at.getX()) <= 8 && Math.abs(l.getZ() - at.getZ()) <= 8 && Math.abs(l.getY() - at.getY()) <= 6) {
                if (prey.size() < 12) prey.add(m);
            } else if (m.getScoreboardTags().contains("helper_farm") && m.getTicksLived() > 6000) m.remove();   // never got to the killing room
            else alive++;
        }
        if (!prey.isEmpty()) {                                               // the sword: a blow every 0.6 s for the next nine seconds, until they are dead
            int[] blows = {0};
            Bukkit.getScheduler().runTaskTimer(plugin, task -> {
                prey.removeIf(m -> m.isDead() || !m.isValid());
                if (blows[0]++ >= 15 || prey.isEmpty() || Bukkit.getPlayer(id) != null || paused()) { task.cancel(); return; }
                org.bukkit.entity.Monster m = prey.get(0);
                m.addScoreboardTag("helper_farm");
                H hh = helpers.get(id);
                if (hh != null && !hh.merged && hh.w != null && hh.w.isValid() && hh.w.getWorld() == m.getWorld()) {
                    m.damage(10.0, hh.w);                                 // onKill counts it and gives the experience
                } else {
                    m.damage(10.0);
                    if (m.isDead()) { xp(id, 5); bump(id, "mobs", 1); }
                }
                m.getWorld().playSound(m.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 1f);
            }, 4L, 12L);
        }
        if (y.rounds++ % 6 == 0) got += sortFarm(id, at);
        if (y.floors.isEmpty()) awayWarn(id, "nofloor", "no dark, roofed floors within 24 blocks of the mob farm (from its level and up), so I cannot call any monsters there");
        else if (alive < 40) for (int i = 0; i < 3; i++) {
            Block b = y.floors.get(r.nextInt(y.floors.size()));
            if (!b.getType().isAir() || !b.getRelative(BlockFace.DOWN).getType().isOccluding()) continue;
            Entity mob = w.spawnEntity(b.getLocation().add(0.5, 0, 0.5), y.kind != null ? y.kind : MOBS[r.nextInt(MOBS.length)]);
            mob.addScoreboardTag("helper_farm");
        }
        return got + prey.size();
    }

    /** A golden chicken it has not seen before: its owner hears of it (at once, or in the finds when they are away). */
    private void goldSeen(UUID id, org.bukkit.entity.Chicken c, String how) {
        List<String> seen = conf.getStringList(path(id, "golden"));
        String key = c.getUniqueId().toString();
        if (seen.contains(key)) return;
        seen.add(key);
        while (seen.size() > 200) seen.remove(0);
        conf.set(path(id, "golden"), seen);
        Location l = c.getLocation();
        String where = l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
        Player p = Bukkit.getPlayer(id);
        if (p != null) { say(p, "Look, a golden chicken at " + where + "! I will take good care of it. It lays golden eggs."); awayLog(id, "FOUND a golden chicken at " + l.getWorld().getName() + " " + where); return; }
        awayLog(id, "FOUND a golden " + (how == null ? "chicken" : "chick (" + how + " from a golden egg)") + " at " + l.getWorld().getName() + " " + where);
        List<String> finds = conf.getStringList(path(id, "away.finds"));
        if (finds.size() < 30) { finds.add(how == null ? "a golden chicken at " + where : "a golden chick " + how + " at " + where); conf.set(path(id, "away.finds"), finds); }
    }

    private static final Map<EntityType, Material> MEAT = Map.of(EntityType.COW, Material.BEEF, EntityType.PIG, Material.PORKCHOP, EntityType.SHEEP, Material.MUTTON, EntityType.CHICKEN, Material.CHICKEN);
    private static final Material[] FODDER = {Material.WHEAT, Material.CARROT, Material.POTATO, Material.BEETROOT, Material.WHEAT_SEEDS, Material.BEETROOT_SEEDS, Material.MELON_SEEDS, Material.PUMPKIN_SEEDS};

    /** Once a minute for the cows, pigs, sheep and chickens within 100 blocks of home: sheep are shorn, a herd under
     *  twelve gets fed two and two (so there are young), a herd over twelve grown ones loses up to two (meat, leather
     *  and feathers into the bag; never the young, the named or the leashed), and what lies on the ground is picked up. */
    private int awayAnimals(UUID id, Location hm) {
        List<Inventory> from = new ArrayList<>(List.of(bag(id)));
        from.addAll(stores(hm, HOME));
        Map<EntityType, List<Animals>> herds = new HashMap<>();
        int got = 0;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        GoldenChicken gc = GoldenChicken.instance;
        Collection<Entity> near = hm.getWorld().getNearbyEntities(hm, HOME, 16, HOME);
        List<org.bukkit.entity.Chicken> gold = new ArrayList<>();
        int hens = 0;
        for (Entity e : near) if (e instanceof org.bukkit.entity.Chicken c) {
            hens++;
            if (gc != null && gc.isGolden(c)) { gold.add(c); goldSeen(id, c, null); }
        }
        for (Entity e : near) {
            if (e instanceof Item egg && egg.getTicksLived() > 100 && hens < 300 && (egg.getItemStack().getType() == Material.EGG
                    || egg.getItemStack().getType() == Material.BLUE_EGG || egg.getItemStack().getType() == Material.BROWN_EGG)) {
                boolean golden = gc != null && gc.isEgg(egg.getItemStack());
                if (!golden || gold.size() < 12) {                           // cracked where it lies: one in eight hatches, now and then four
                    int chicks = 0;
                    for (int i = 0; i < egg.getItemStack().getAmount(); i++) if (r.nextInt(8) == 0) chicks += r.nextInt(32) == 0 ? 4 : 1;
                    Location at = egg.getLocation();
                    for (int i = 0; i < chicks; i++) {
                        org.bukkit.entity.Chicken c = at.getWorld().spawn(at, org.bukkit.entity.Chicken.class);
                        c.setBaby();
                        if (golden) { gc.makeGolden(c); gold.add(c); goldSeen(id, c, "hatched"); }
                    }
                    at.getWorld().playSound(at, Sound.ENTITY_EGG_THROW, 0.6f, 1f);
                    bump(id, "eggs", egg.getItemStack().getAmount());
                    bump(id, golden ? "goldchicks" : "chicks", chicks);
                    hens += chicks;
                    got++;
                    egg.remove();
                    continue;
                }
            }
            if (e instanceof Item item && item.getTicksLived() > 100) {      // what the animals dropped (golden eggs too, once there are twelve golden chickens)
                List<ItemStack> one = List.of(item.getItemStack());
                if (addToBag(id, one) == item.getItemStack().getAmount()) { awayNote(id, one); item.remove(); got++; }
                continue;
            }
            if (e instanceof Animals a && MEAT.containsKey(e.getType()) && a.customName() == null && !a.isLeashed()) herds.computeIfAbsent(e.getType(), k -> new ArrayList<>()).add(a);
        }
        if (gold.size() < 30) {                                              // golden chickens are never slaughtered, and two golden parents give a golden chick
            List<org.bukkit.entity.Chicken> ready = new ArrayList<>();
            for (org.bukkit.entity.Chicken c : gold) if (c.isAdult() && c.canBreed() && !c.isLoveMode() && ready.size() < 2) ready.add(c);
            if (ready.size() == 2) {
                Material food = null;
                for (Material f : FODDER) if (ready.get(0).isBreedItem(f) && count(from, m -> m == f) >= 2) { food = f; break; }
                if (food == null) awayWarn(id, "goldfood", "no seeds for the golden chickens (wheat, beetroot, melon or pumpkin seeds in a chest and I will breed them)");
                else {
                    Material fd = food;
                    take(from, m -> m == fd, 2);
                    dirty.add(id);
                    for (org.bukkit.entity.Chicken c : ready) c.setLoveModeTicks(600);
                    bump(id, "fed", 2);
                    got += 2;
                }
            }
        }
        for (Map.Entry<EntityType, List<Animals>> herd : herds.entrySet()) {
            List<Animals> grown = new ArrayList<>();
            for (Animals a : herd.getValue()) if (a.isAdult()) grown.add(a);
            for (Animals a : grown) if (a instanceof Sheep sh && !sh.isSheared()) {
                sh.setSheared(true);
                org.bukkit.DyeColor col = sh.getColor();
                Material wool = Material.matchMaterial((col == null ? "WHITE" : col.name()) + "_WOOL");
                List<ItemStack> w = List.of(new ItemStack(wool == null ? Material.WHITE_WOOL : wool, 1 + r.nextInt(3)));
                awayNote(id, w);
                addToBag(id, w);
                bump(id, "wool", 1);
                got++;
            }
            if (grown.size() > 12) {
                for (int i = 0; i < Math.min(2, grown.size() - 12); i++) {
                    Animals a = grown.get(grown.size() - 1 - i);
                    List<ItemStack> meat = new ArrayList<>(List.of(new ItemStack(MEAT.get(herd.getKey()), 2 + r.nextInt(3))));
                    if (a instanceof Cow) meat.add(new ItemStack(Material.LEATHER, 1 + r.nextInt(2)));
                    if (herd.getKey() == EntityType.CHICKEN) meat.add(new ItemStack(Material.FEATHER, 1 + r.nextInt(2)));
                    awayNote(id, meat);
                    addToBag(id, meat);
                    a.remove();
                    xp(id, 1 + r.nextInt(3));
                    bump(id, "meat", 1);
                    got++;
                }
                int gone = Math.min(2, grown.size() - 12);
                awayLog(id, "slaughtered " + gone + " " + herd.getKey().getKey().getKey() + ", " + (grown.size() - gone) + " grown left");
            } else if (herd.getValue().size() < 12) {
                List<Animals> ready = new ArrayList<>();
                for (Animals a : grown) if (a.canBreed() && !a.isLoveMode() && ready.size() < 2) ready.add(a);
                if (ready.size() < 2) continue;
                Material food = null;
                for (Material f : FODDER) if (ready.get(0).isBreedItem(f) && count(from, m -> m == f) >= 2) { food = f; break; }
                if (food == null) continue;
                Material fd = food;
                take(from, m -> m == fd, 2);
                dirty.add(id);
                for (Animals a : ready) a.setLoveModeTicks(600);
                xp(id, 1 + r.nextInt(7));
                bump(id, "fed", 2);
                got += 2;
            }
        }
        return got;
    }

    /** The n-th chunk of a square spiral around 0,0. */
    private static int[] spiral(int n) {
        int x = 0, z = 0, dx = 0, dz = -1;
        for (int i = 0; i < n; i++) {
            if (x == z || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) { int t = dx; dx = -dz; dz = t; }
            x += dx;
            z += dz;
        }
        return new int[] {x, z};
    }

    private static boolean oreFor(Material m, String target) {
        String n = m.name();
        return n.contains(target) && (n.endsWith("_ORE") || m == Material.ANCIENT_DEBRIS || m == Material.OBSIDIAN);
    }

    /** "diamonds", "kull", "iron ore" -> DIAMOND, COAL, IRON; null when it is not something it digs for. */
    private static String oreTarget(String words) {
        String w = ARTICLE.matcher(words.trim().toLowerCase(Locale.ROOT)).replaceFirst("").replaceAll("\\s+(?:ore|malm)$", "").trim();
        String t = ORE_WORDS.get(w);
        if (t == null) t = w.toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
        if (t.endsWith("S") && t.length() > 4) { String one = t.substring(0, t.length() - 1); for (Material m : Material.values()) if (oreFor(m, one)) return one; }
        for (Material m : Material.values()) if (oreFor(m, t)) return t;
        return null;
    }

    /** Digs out one kind of ore within 128 blocks of home (all heights), where the land already exists: looks
     *  through up to four chunks every ten seconds, and takes three blocks at a time, with fortune III. */
    private int awayMine(UUID id, Location hm) {
        String target = conf.getString(path(id, "away.target"), "");
        if (target.isEmpty()) return 0;
        Deque<Block> q = awayOre.computeIfAbsent(id, k -> new ArrayDeque<>());
        World w = hm.getWorld();
        for (int tries = 0; q.isEmpty() && tries < 4; tries++) {
            int i = awayScan.merge(id, 1, Integer::sum) - 1;
            int[] c = spiral(i);
            if (Math.max(Math.abs(c[0]), Math.abs(c[1])) > 8) {
                awayScan.remove(id);
                conf.set(path(id, "away.why"), "there is no more " + target.toLowerCase(Locale.ROOT).replace('_', ' ') + " within 128 blocks of home");
                conf.set(path(id, "away.full"), true);
                return 0;
            }
            int cx = (hm.getBlockX() >> 4) + c[0], cz = (hm.getBlockZ() >> 4) + c[1];
            if (!w.isChunkGenerated(cx, cz)) continue;
            org.bukkit.ChunkSnapshot snap = w.getChunkAt(cx, cz).getChunkSnapshot();
            for (int y = w.getMinHeight(); y < w.getMaxHeight(); y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++)
                if (oreFor(snap.getBlockType(x, y, z), target)) q.add(w.getBlockAt(cx * 16 + x, y, cz * 16 + z));
        }
        int n = 0;
        while (n < 3 && !q.isEmpty()) {
            Block b = q.poll();
            if (!oreFor(b.getType(), target)) continue;
            Collection<ItemStack> d = drops(b);
            awayNote(id, d);
            addToBag(id, d);
            String ore = b.getType().name();
            xp(id, ore.contains("DIAMOND") || ore.contains("EMERALD") ? 3 + ThreadLocalRandom.current().nextInt(5) : ore.contains("LAPIS") || ore.contains("QUARTZ") || ore.contains("REDSTONE") ? 2 + ThreadLocalRandom.current().nextInt(4)
                    : ore.contains("COAL") ? ThreadLocalRandom.current().nextInt(3) : 0);
            b.setType(Material.AIR);
            n++;
        }
        return n;
    }

    /** Goes further and further out from home (to 3000 blocks), one new piece of land every ten seconds, also where
     *  nobody has been. Buildings it finds are remembered as places (goto <name>) and told about afterwards. On the
     *  way it digs out what it is looking for (see SEEK), and for every treasure chest nobody has opened it makes
     *  a treasure map with a red cross and puts it in a chest at home. From the home chores (side) it never stops
     *  the work when there is no more land to see. */
    private static final int EXPLORE_STEPS = 94;                           // two chunks a step: about 3000 blocks out

    private int awayExplore(UUID id, Location hm, boolean side) {
        int i = conf.getInt(path(id, "away.scan"), 0);
        int[] c = spiral(i);
        if (Math.max(Math.abs(c[0]), Math.abs(c[1])) > EXPLORE_STEPS) {
            if (side) return 0;
            conf.set(path(id, "away.why"), "I have seen everything within 3000 blocks of home");
            conf.set(path(id, "away.full"), true);
            return 0;
        }
        conf.set(path(id, "away.scan"), i + 1);
        World w = hm.getWorld();
        int cx = (hm.getBlockX() >> 4) + c[0] * 2, cz = (hm.getBlockZ() >> 4) + c[1] * 2;
        w.getChunkAtAsync(cx, cz, true).thenAccept(ch -> {
            passOres(id, ch);
            treasures(id, hm, ch);
            Registry<Structure> reg = RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE);
            for (GeneratedStructure gs : w.getStructures(cx, cz)) {
                NamespacedKey k = reg.getKey(gs.getStructure());
                if (k == null) continue;
                Vector mid = gs.getBoundingBox().getCenter();
                int x = mid.getBlockX(), z = mid.getBlockZ();
                Location at = new Location(w, x + 0.5, w.getEnvironment() == World.Environment.NORMAL ? w.getHighestBlockYAt(x, z) + 1 : mid.getBlockY(), z + 0.5);
                ConfigurationSection sec = conf.getConfigurationSection(path(id, "places"));
                boolean known = false;
                if (sec != null) for (String name : sec.getKeys(false)) {
                    Location l = place(sec.getString(name));
                    if (name.startsWith(k.getKey()) && l != null && l.getWorld() == w && l.distanceSquared(at) < 200 * 200) { known = true; break; }
                }
                if (known) continue;
                String name = k.getKey();
                for (int n = 2; sec != null && sec.contains(name) && n < 50; n++) name = k.getKey() + "_" + n;
                conf.set(path(id, "places." + name), spot(at));
                String told = k.getKey().replace('_', ' ') + " at " + x + " " + z + " (goto " + name.replace('_', ' ') + ")";
                awayLog(id, "found a " + told);
                List<String> finds = conf.getStringList(path(id, "away.finds"));
                if (finds.size() < 30) { finds.add(told); conf.set(path(id, "away.finds"), finds); }
            }
        });
        return 1;
    }

    /** What it digs for while exploring, in this order: the first kind you have less than the limit of (in your own
     *  store, its bag and the chests at home). "find <ore or wood>" puts one kind first, however much you have. */
    private static final String[][] SEEK = {   // name, what it yields, limit
            {"diamond", "DIAMOND", "640"}, {"gold", "RAW_GOLD GOLD_INGOT", "640"}, {"iron", "RAW_IRON IRON_INGOT", "640"},
            {"coal", "COAL", "640"}, {"lapis", "LAPIS_LAZULI", "640"}, {"redstone", "REDSTONE", "640"}};
    private final Map<UUID, Object[]> seekNow = new HashMap<>();             // the kind chosen from the stock, and when

    /** "redstone", "gold ore", "oak", "birch logs", "wood" -> ORE:REDSTONE, ORE:GOLD, LOG:OAK, LOG:BIRCH, LOG:; null if it is neither. */
    private static String seekSpec(String words) {
        String w = ARTICLE.matcher(words.trim().toLowerCase(Locale.ROOT)).replaceFirst("").trim();
        if (w.matches("wood|woods|log|logs|tree|trees|tre|ved|stokker")) return "LOG:";
        String t = w.replaceAll("\\s+(?:logs?|wood|trees?|tre|stokker)$", "").toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
        if (mat(t + "_LOG") != null) return "LOG:" + t;
        if (mat(t + "_STEM") != null && !t.contains("MUSHROOM")) return "LOG:" + t;
        String ore = oreTarget(w);
        return ore == null ? null : "ORE:" + ore;
    }

    private static boolean seekBlock(Material m, String spec) {
        if (spec.startsWith("ORE:")) {
            String t = spec.substring(4);
            return oreFor(m, t) && (!t.equals("IRON") || m.name().endsWith("IRON_ORE")) && (!t.equals("GOLD") || m.name().endsWith("GOLD_ORE"));
        }
        String t = spec.substring(4), n = m.name();
        boolean log = Tag.LOGS.isTagged(m) && !n.startsWith("STRIPPED_") && (n.endsWith("_LOG") || n.endsWith("_STEM"));
        return log && (t.isEmpty() || n.equals(t + "_LOG") || n.equals(t + "_STEM"));
    }

    private static String seekWord(String spec) {
        String t = spec.substring(4).toLowerCase(Locale.ROOT).replace('_', ' ');
        return spec.startsWith("LOG:") ? (t.isEmpty() ? "wood" : t + " wood") : t;
    }

    /** What it digs for now: your order, else the first kind below its limit, else nothing. Looked at once a minute. */
    private String seeking(UUID id) {
        String asked = conf.getString(path(id, "seek"));
        if (asked != null) return asked;
        Object[] was = seekNow.get(id);
        if (was != null && System.currentTimeMillis() - (long) was[1] < 60_000) return (String) was[0];
        List<Inventory> in = new ArrayList<>(List.of(bag(id)));
        Location hm = home(id);
        if (hm != null && hm.getWorld() != null) in.addAll(stores(hm, HOME));
        String pick = null;
        for (String[] k : SEEK) {
            Set<Material> yields = new HashSet<>();
            for (String y : k[1].split(" ")) yields.add(Material.valueOf(y));
            long have = count(in, yields::contains) + (storage == null ? 0 : storage.countMine(id, yields));
            if (have < conf.getLong("settings.seek-limit." + k[0], Long.parseLong(k[2]))) { pick = "ORE:" + k[0].toUpperCase(Locale.ROOT); break; }
        }
        String before = was == null ? null : (String) was[0];
        if (!java.util.Objects.equals(before, pick)) awayLog(id, pick == null ? "has plenty of every ore now, so it digs for none while exploring"
                : "looks for " + seekWord(pick) + " while exploring" + (before == null ? "" : " (enough " + seekWord(before) + " now)"));
        seekNow.put(id, new Object[] {pick, System.currentTimeMillis()});
        return pick;
    }

    /** A log that belongs to a tree: leaves that grew there (not placed) within a few blocks above or beside it.
     *  So the walls of a log cabin are left alone. */
    private static boolean wildLog(org.bukkit.ChunkSnapshot snap, int x, int y, int z, int top) {
        for (int dy = 0; dy <= 8 && y + dy < top; dy++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            int ax = x + dx, az = z + dz;
            if (ax < 0 || ax > 15 || az < 0 || az > 15) continue;
            if (snap.getBlockData(ax, y + dy, az) instanceof org.bukkit.block.data.type.Leaves l && !l.isPersistent()) return true;
        }
        return false;
    }

    /** What it is looking for in a piece of land it passes: up to 64 blocks of it.
     *  Ore turns to stone (no holes in the land), a felled log to air. */
    private void passOres(UUID id, org.bukkit.Chunk ch) {
        String spec = seeking(id);
        if (spec == null) return;
        World w = ch.getWorld();
        org.bukkit.ChunkSnapshot snap = ch.getChunkSnapshot();
        int n = 0, top = w.getMaxHeight();
        Map<Material, Integer> got = new java.util.EnumMap<>(Material.class);
        outer:
        for (int y = w.getMinHeight(); y < top; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            Material m = snap.getBlockType(x, y, z);
            if (!seekBlock(m, spec)) continue;
            boolean log = spec.startsWith("LOG:");
            if (log && !wildLog(snap, x, y, z, top)) continue;
            Block b = ch.getBlock(x, y, z);
            for (ItemStack d : drops(b)) if (!empty(d)) got.merge(d.getType(), d.getAmount(), Integer::sum);
            String k = m.name();
            if (!log) xp(id, k.contains("DIAMOND") || k.contains("EMERALD") ? 3 + ThreadLocalRandom.current().nextInt(5) : k.contains("LAPIS") || k.contains("REDSTONE") ? 2 + ThreadLocalRandom.current().nextInt(4) : k.contains("COAL") ? ThreadLocalRandom.current().nextInt(3) : 0);
            b.setType(log ? Material.AIR : k.startsWith("DEEPSLATE") ? Material.DEEPSLATE : k.startsWith("NETHER") ? Material.NETHERRACK : Material.STONE);
            if (++n >= 64) break outer;
        }
        if (n == 0) return;
        bump(id, "mined", n);
        List<ItemStack> stacks = new ArrayList<>();                         // one line in the log per kind (per stack), not per block
        got.forEach((m, a) -> { for (int left = a; left > 0; left -= m.getMaxStackSize()) stacks.add(new ItemStack(m, Math.min(left, m.getMaxStackSize()))); });
        awayNote(id, stacks);
        addToBag(id, stacks);
    }

    /** Chests nobody has opened yet (buried treasure, shipwrecks, temples, dungeons …, not villages): a treasure map. */
    private void treasures(UUID id, Location hm, org.bukkit.Chunk ch) {
        World w = ch.getWorld();
        for (BlockState st : ch.getTileEntities(b -> b.getType() == Material.CHEST || b.getType() == Material.BARREL, false)) {
            if (!(st instanceof org.bukkit.loot.Lootable lt) || !lt.hasLootTable() || lt.getLootTable() == null) continue;
            String table = lt.getLootTable().getKey().getKey();                         // chests/buried_treasure …
            if (table.contains("village")) continue;
            int x = st.getX(), y = st.getY(), z = st.getZ();
            ConfigurationSection sec = conf.getConfigurationSection(path(id, "places"));
            boolean known = false;
            if (sec != null) for (String name : sec.getKeys(false)) {
                Location l = place(sec.getString(name));
                if (name.startsWith("treasure") && l != null && l.getWorld() == w && l.distanceSquared(new Location(w, x, y, z)) < 48 * 48) { known = true; break; }
            }
            if (known) continue;
            String what = table.replaceFirst("^chests/", "").replace('/', ' ').replace('_', ' ');
            String name = "treasure";
            for (int n = 2; sec != null && sec.contains(name) && n < 200; n++) name = "treasure_" + n;
            conf.set(path(id, "places." + name), spot(new Location(w, x + 0.5, y + 1, z + 0.5)));
            ItemStack map = treasureMap(w, x, y, z, what);
            boolean inBarrel = myBarrel(id, hm.getWorld()) != null;
            if (inBarrel) storage.putMine(id, List.of(map));
            else {
                List<Inventory> to = stores(hm, HOME);
                Map<Integer, ItemStack> rest = Map.of(0, map);
                for (Inventory inv : to) { if (rest.isEmpty()) break; rest = inv.addItem(map.clone()); }
                if (!rest.isEmpty()) bag(id).addItem(map);
            }
            dirty.add(id);
            bump(id, "maps", 1);
            String told = "a treasure (" + what + ") at " + x + " " + y + " " + z + ": a map is in " + (inBarrel ? "your magic barrel" : "the chests at home") + " (goto " + name.replace('_', ' ') + ")";
            awayLog(id, "found " + told);
            List<String> finds = conf.getStringList(path(id, "away.finds"));
            if (finds.size() < 30) { finds.add(told); conf.set(path(id, "away.finds"), finds); }
        }
    }

    private static ItemStack treasureMap(World w, int x, int y, int z, String what) {
        org.bukkit.map.MapView v = Bukkit.createMap(w);
        v.setCenterX(x);
        v.setCenterZ(z);
        v.setScale(org.bukkit.map.MapView.Scale.NORMAL);
        v.setTrackingPosition(true);
        v.setUnlimitedTracking(true);
        ItemStack it = new ItemStack(Material.FILLED_MAP);
        if (it.getItemMeta() instanceof org.bukkit.inventory.meta.MapMeta mm) {
            mm.setMapView(v);
            mm.customName(Component.text("Treasure map: " + what, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            mm.lore(List.of(Component.text(x + " " + y + " " + z + " in " + w.getName(), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                    Component.text("Dig where the red cross is.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
            it.setItemMeta(mm);
        }
        it.setData(io.papermc.paper.datacomponent.DataComponentTypes.MAP_DECORATIONS, io.papermc.paper.datacomponent.item.MapDecorations.mapDecorations(
                Map.of("treasure", io.papermc.paper.datacomponent.item.MapDecorations.decorationEntry(org.bukkit.map.MapCursor.Type.RED_X, x, z, 180f))));
        return it;
    }

    /** Bag and chests full: a new chest from eight planks (or two logs) out of the chests, set down next to one of
     *  the chests at home. False when there is no wood or no free spot. */
    private boolean moreRoom(UUID id, Location hm) {
        List<Inventory> from = new ArrayList<>(List.of(bag(id)));
        List<Inventory> chests = stores(hm, HOME);
        from.addAll(chests);
        boolean planks = count(from, Tag.PLANKS::isTagged) >= 8;
        if (!planks && count(from, Tag.LOGS::isTagged) < 2) return false;
        Block spot = null;
        find:
        for (Inventory inv : chests) {
            Location l = inv.getLocation();
            if (l == null) continue;
            for (BlockFace f : SIDES) {
                Block b = l.getBlock().getRelative(f);
                if (b.getType().isAir() && b.getRelative(BlockFace.UP).getType().isAir() && b.getRelative(BlockFace.DOWN).getType().isSolid()) { spot = b; break find; }
            }
        }
        if (spot == null) for (int r = 1; r <= 4 && spot == null; r++) for (int x = -r; x <= r && spot == null; x++) for (int z = -r; z <= r && spot == null; z++) {
            Block b = hm.getBlock().getRelative(x, 0, z);
            if (b.getType().isAir() && b.getRelative(BlockFace.UP).getType().isAir() && b.getRelative(BlockFace.DOWN).getType().isSolid() && b.getLocation().getNearbyPlayers(1.5).isEmpty()) spot = b;
        }
        if (spot == null) return false;
        if (planks) take(from, Tag.PLANKS::isTagged, 8); else take(from, Tag.LOGS::isTagged, 2);
        dirty.add(id);
        spot.setType(Material.CHEST);
        spot.getWorld().playSound(spot.getLocation(), Sound.BLOCK_WOOD_PLACE, 0.8f, 1f);
        awayLog(id, "everything was full, so it made a new chest at " + spot.getX() + " " + spot.getY() + " " + spot.getZ());
        List<String> finds = conf.getStringList(path(id, "away.finds"));
        if (finds.size() < 30) { finds.add("a new chest I made at " + spot.getX() + " " + spot.getY() + " " + spot.getZ()); conf.set(path(id, "away.finds"), finds); }
        return true;
    }

    private String awayDid(UUID id) {
        int fish = conf.getInt(path(id, "away.fish"), 0), crops = conf.getInt(path(id, "away.crops"), 0), smelted = conf.getInt(path(id, "away.smelted"), 0);
        List<String> did = new ArrayList<>();
        if (fish > 0) did.add("caught " + fish + (fish == 1 ? " thing" : " things") + " fishing");
        if (crops > 0) did.add("harvested " + crops + (crops == 1 ? " plant" : " plants"));
        if (smelted > 0) did.add("took " + smelted + (smelted == 1 ? " thing" : " things") + " out of the furnaces");
        int sown = conf.getInt(path(id, "away.sown"), 0), meal = conf.getInt(path(id, "away.meal"), 0), fed = conf.getInt(path(id, "away.fed"), 0),
                meat = conf.getInt(path(id, "away.meat"), 0), wool = conf.getInt(path(id, "away.wool"), 0);
        if (sown > 0) did.add("sowed " + sown + (sown == 1 ? " plant" : " plants"));
        if (meal > 0) did.add("used " + meal + " bone meal");
        if (fed > 0) did.add("fed " + fed + " animals");
        if (wool > 0) did.add("sheared " + wool + (wool == 1 ? " sheep" : " sheep"));
        if (meat > 0) did.add("slaughtered " + meat + (meat == 1 ? " animal" : " animals"));
        int mined = conf.getInt(path(id, "away.mined"), 0), explored = conf.getInt(path(id, "away.explored"), 0);
        if (mined > 0) did.add("dug out " + mined + " blocks of " + conf.getString(path(id, "away.target"), "ore").toLowerCase(Locale.ROOT).replace('_', ' '));
        int eggs = conf.getInt(path(id, "away.eggs"), 0), chicks = conf.getInt(path(id, "away.chicks"), 0), goldchicks = conf.getInt(path(id, "away.goldchicks"), 0);
        if (eggs > 0) did.add("cracked " + eggs + (eggs == 1 ? " egg" : " eggs") + " (" + chicks + (chicks == 1 ? " chick" : " chicks") + ")");
        if (goldchicks > 0) did.add("hatched " + goldchicks + " golden " + (goldchicks == 1 ? "chick" : "chicks"));
        int sorted = conf.getInt(path(id, "away.sorted"), 0);
        if (sorted > 0) did.add("sorted " + sorted + " things into the chests at the mob farm");
        int mobs = conf.getInt(path(id, "away.mobs"), 0);
        if (mobs > 0) did.add("killed " + mobs + (mobs == 1 ? " monster" : " monsters") + " at the mob farm");
        int earned = conf.getInt(path(id, "away.xp"), 0);
        if (earned > 0) did.add("earned you " + earned + " experience points");
        if (explored > 0) did.add("explored " + explored * 32 * 32 / 1000 + " thousand square blocks of land");
        return String.join(", ", did);
    }

    /** "sum": everything done since the job began (the counters start again each time the owner logs out). */
    private String sum(UUID id) {
        String did = awayDid(id), job = conf.getString(path(id, "away.job"), "");
        long min = conf.getLong(path(id, "away.ms"), 0) / 60_000;
        List<String> finds = conf.getStringList(path(id, "away.finds"));
        if (did.isEmpty() && min == 0) {
            String last = conf.getString(path(id, "away.last"));
            return last == null ? "I have not done any work on my own yet." : "Last time you were away: " + last;
        }
        boolean full = conf.getBoolean(path(id, "away.full"), false);
        return "Since I began (" + min + " min of work" + (job.isEmpty() ? "" : ", job: " + (job.equals("home") ? "work at home" : job)) + "): " + (did.isEmpty() ? "nothing yet" : did) + "."
                + (finds.isEmpty() ? "" : " Special finds: " + String.join("; ", finds) + ".")
                + (!full ? "" : " I have stopped: " + conf.getString(path(id, "away.why"), "bag and chests are full") + ".");
    }

    /** "Did you find anything special?": the enchanted things, treasure, buildings and new chests of this time away,
     *  or of the last one when the owner is back. */
    private String finds(UUID id) {
        List<String> now = conf.getStringList(path(id, "away.finds")), last = conf.getStringList(path(id, "away.lastfinds"));
        if (!now.isEmpty()) return "Yes! So far: " + String.join("; ", now) + ".";
        if (conf.getLong(path(id, "away.ms"), 0) > 0) return "Nothing special yet this time. I will tell you when I do.";
        return last.isEmpty() ? "No, nothing special last time." : "Yes! Last time you were away: " + String.join("; ", last) + ".";
    }

    private static final String[][] LOOKS = {
        {"ninja", "Silent as a shadow. You will not hear me coming.", "Black belt on. Nobody sneaks up on you now."},
        {"knight|paladin|templar|angelman", "Armour on, sword ready. I have your back.", "A knight at your service!"},
        {"princess|elsa|anna|peach|angel_woman|fairy", "Oh, I love this dress! Shall we?", "Do I get a crown with this?"},
        {"king", "A crown! Does that make you my royal adviser?"},
        {"wizard|mage", "Abracadabra! Now, where did I put my wand?"},
        {"pirate", "Arr! Where is the treasure, captain?"},
        {"viking|dwarf", "By my beard, let us go raiding!"},
        {"superman|batman|batgirl|robin|spiderman|ironman|captainamerica|thor|wonderwoman|aquaman|greenlantern|antman|optimus|tron|cyborg|android", "Suit on. Who needs saving today?", "With great power comes great farming."},
        {"astronaut", "Ground control, I am ready for lift-off."},
        {"farmer|cowboy|cowgirl", "Boots on. The fields will not tend themselves!"},
        {"miner|explorer", "Pickaxe sharpened. Let us find some diamonds."},
        {"santa|elf", "Ho ho ho! Have you been good this year?"},
        {"snowman", "Brr. Do not take me to the desert, please."},
        {"skeleton", "Do not worry, it is still me under the bones."},
        {"mario|luigi", "Wahoo! Let's-a go!"},
        {"cat|kitten|catgirl", "Meow. I mean: ready when you are."},
        {"dog|puppy|wolf|fox", "Woof! Throw me a bone and I will make bone meal."},
        {"panda|bear|tiger|bunny|rabbit|frog|penguin|dragon|trex|dino|unicorn", "Rawr! Too cute, or just cute enough?"},
        {"boy|girl|man|woman|steve|alex", "Fresh clothes, same me. How do I look?"},
    };
    private static final String[] LOOK_ANY = {"New look! What do you think?", "Oh, I like this one. Does it suit me?", "A change of clothes does wonders. Ready when you are.", "Ta-da! Still me, just better dressed."};

    /** What it says about its new skin. */
    private static String look(String skin) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String k = skin.toLowerCase(Locale.ROOT);
        for (String[] l : LOOKS) if (Pattern.compile(l[0]).matcher(k).find()) return l[1 + r.nextInt(l.length - 1)];
        return LOOK_ANY[r.nextInt(LOOK_ANY.length)];
    }

    // --- Personality -------------------------------------------------------------------------------
    /** name, what it is like (for the model that answers in game), openers, closers (for the fixed lines). */
    private static final String[][] PERSONAS = {
        {"brave", "bold and fearless, wants to go first into every danger", "Fear not!|No danger is too big!", "I will go first.|Nothing scares me."},
        {"tough", "tough and short-spoken, never complains", "Hmph.|Easy.", "No sweat.|I have had worse."},
        {"thoughtful", "considerate, remembers what the player likes and asks how they are", "I thought of you.|Just so you know:", "I hope that helps.|Take care of yourself."},
        {"pensive", "brooding and philosophical, wonders about the world", "Hmm, I wonder…|I have been thinking.", "Makes you think, does it not?|What does it all mean, I wonder."},
        {"outgoing", "talkative and sociable, loves company and greets everyone", "Hey hey!|Oh, hello friend!", "We should invite everyone!|I love having company."},
        {"funny", "a joker, puns and silliness in almost every answer", "Knock knock!|Ha!", "No yolk.|I crack myself up."},
        {"genius", "a brilliant know-it-all who explains how things work and gives tips", "Elementary.|By my calculations:", "Science!|As I predicted."},
        {"timid", "shy and careful, afraid of creepers, but does the job anyway", "Um…|Oh! You startled me.", "…if that is all right.|I hope no creepers heard that."},
        {"clingy", "misses the player when they are away and wants to stay close", "There you are!|I missed you!", "Do not go far, please.|Stay a little longer?"},
        {"loving", "warm and devoted, the player's biggest fan", "Dearest friend!|Oh, it is you!", "You are the best.|I am your biggest fan."},
        {"positive", "sees the bright side of everything", "Great news!|What a lovely day!", "It will all turn out fine!|I love this."},
        {"grumpy", "grumbles and sighs about everything, but is never mean to anyone", "Ugh.|Sigh.", "Not that anyone asked me.|My feet hurt."},
        {"curious", "asks and wonders, wants to explore everything", "Ooh!|I was just wondering:", "What is over that hill, I wonder?|How does that work, do you think?"},
        {"proud", "brags a little about what it has done", "Ahem.|Look at this:", "Not bad, if I say so myself.|I am rather good at this."},
        {"dramatic", "everything is either wonderful or a catastrophe", "Oh my stars!|You will NOT believe this.", "It is the greatest day of my life!|Oh, the drama of it all!"},
        {"calm", "relaxed, nothing stresses it", "All is well.|No hurry.", "Easy does it.|Breathe in, breathe out."},
        {"sleepy", "yawns and would rather nap, but works anyway", "Yaaawn.|Mmh, what?", "Time for a nap soon?|Zzz… I am awake, I am awake."},
        {"polite", "a courteous butler", "With pleasure.|If I may:", "At your service.|As you wish."},
        {"sarcastic", "dry humour, kindly meant", "Oh, wonderful.|Well, well.", "What a surprise.|My favourite thing. Truly."},
        {"playful", "childlike, turns everything into a game", "Wheee!|Tag, you are it!", "Can we play after?|Last one home is a rotten egg!"},
        {"wise", "old and wise, speaks in little proverbs", "As the old ones say:|Listen, young one.", "Patience grows the tallest tree.|A full chest begins with one block."},
        {"pirate", "talks like a pirate and calls the player captain", "Arr!|Ahoy, captain!", "Yo ho ho!|Or I will walk the plank!"},
        {"royal", "talks like a queen", "Hear ye!|We are pleased to say:", "So decrees the crown.|One is most pleased."},
        {"robot", "precise and literal like a robot", "BEEP.|PROCESSING… DONE.", "TASK LOGGED.|END OF MESSAGE."},
        {"poet", "answers in rhyme", "A verse for you:|Roses are red:", "…and that is my rhyme, right on time.|…so says your poet, and now you know it."}
    };
    private static final Map<String, String> PERSONA_ALIAS = Map.ofEntries(Map.entry("thoughtfull", "thoughtful"), Map.entry("toughtfull", "thoughtful"), Map.entry("genious", "genius"),
            Map.entry("inlove", "loving"), Map.entry("love", "loving"), Map.entry("negative", "grumpy"), Map.entry("shy", "timid"), Map.entry("happy", "positive"),
            Map.entry("smart", "genius"), Map.entry("modig", "brave"), Map.entry("morsom", "funny"), Map.entry("sur", "grumpy"), Map.entry("sjenert", "timid"),
            Map.entry("rolig", "calm"), Map.entry("nysgjerrig", "curious"), Map.entry("pirat", "pirate"), Map.entry("høflig", "polite"), Map.entry("trøtt", "sleepy"));

    private static String[] persona(String name) {
        for (String[] p : PERSONAS) if (p[0].equals(name)) return p;
        return null;
    }

    /** "personality brave funny", "personality reset", "personality": sets, clears or shows it. At most two. */
    private String personality(UUID id, String rest) {
        String t = rest.toLowerCase(Locale.ROOT).replace("in love", "inlove").trim();
        List<String> all = new ArrayList<>();
        for (String[] p : PERSONAS) all.add(p[0]);
        List<String> now = conf.getStringList(path(id, "personality"));
        if (t.isEmpty()) return (now.isEmpty() ? "I am my usual self." : "I am " + String.join(" and ", now) + ".") + " You can pick one or two: " + String.join(", ", all) + ". personality reset makes me my usual self again.";
        if (t.matches("reset|default|off|none|normal|usual|standard|av|nullstill|vanlig")) {
            conf.set(path(id, "personality"), null);
            save();
            return "I am my usual self again.";
        }
        List<String> pick = new ArrayList<>();
        for (String w : t.split("[\\s,+&]+|\\band\\b|\\bog\\b")) {
            if (w.isEmpty()) continue;
            String k = PERSONA_ALIAS.getOrDefault(w, w);
            if (persona(k) == null) return "I do not know how to be " + w + ". I can be: " + String.join(", ", all) + ".";
            if (!pick.contains(k) && pick.size() < 2) pick.add(k);
        }
        if (pick.isEmpty()) return "personality <name> [name], or personality reset.";
        conf.set(path(id, "personality"), pick);
        save();
        return "From now on I am " + String.join(" and ", pick) + ".";
    }

    /** A fixed line said in its own voice: now and then a few words of its personality before or after. */
    private String voice(UUID id, String text) {
        List<String> now = conf.getStringList(path(id, "personality"));
        if (now.isEmpty() || text.indexOf('\n') >= 0) return text;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String[] p = persona(now.get(r.nextInt(now.size())));
        if (p == null) return text;
        int roll = r.nextInt(10);
        String[] open = p[2].split("\\|"), close = p[3].split("\\|");
        if (roll < 5) return open[r.nextInt(open.length)] + " " + text;
        if (roll < 8 && text.length() <= 140) return text + " " + close[r.nextInt(close.length)];
        return text;
    }

    /** For the model: who it is, as part of what it "sees". */
    private String personaNote(UUID id) {
        StringBuilder sb = new StringBuilder();
        for (String n : conf.getStringList(path(id, "personality"))) { String[] p = persona(n); if (p != null) sb.append(sb.length() == 0 ? "" : "; ").append(p[0]).append(" (").append(p[1]).append(")"); }
        return sb.length() == 0 ? "" : " Your personality: " + sb + ".";
    }

    private static final String[] JOKES = {
        "Why did the creeper cross the road? To get to the other sssssside.",
        "What is a skeleton's favourite instrument? The trom-bone.",
        "Why are there no cars in Minecraft? Because the streets are blocked.",
        "What did the chicken say to the sheep? Nothing. Chickens cannot talk. But I can!",
        "Why did the enderman go to school? To get a little more end-ucation.",
        "What does a zombie eat for breakfast? Rotten flakes.",
        "Why was the pig a bad friend? He kept hogging the carrots.",
        "How does Steve keep fit? He runs around the block.",
        "What do you call a cow that plays the guitar? A moo-sician.",
        "Why do miners make good friends? They always dig you.",
        "What is a ghast's favourite country? The Nether-lands.",
        "Why did the skeleton stay home from the party? He had no body to go with.",
        "What did the fish say when it hit the wall? Dam. Wait, I caught that one myself.",
        "Why is the villager so rich? He never spends an emerald, he just says hrmm.",
        "What kind of music do I like? Bed-rock.",
        "Why did the sheep say sorry? It felt baaad.",
        "What does a creeper say on its birthday? Do not blow out the candles, I will do it.",
        "Why do I never get lost? Because you gave me a home.",
    };
    private final Map<UUID, Integer> lastJoke = new HashMap<>();

    private String joke(UUID id) {
        int n;
        do { n = ThreadLocalRandom.current().nextInt(JOKES.length); } while (Integer.valueOf(n).equals(lastJoke.get(id)));
        lastJoke.put(id, n);
        return JOKES[n];
    }

    /** A short answer of its own to an order it cannot follow while its owner is away. */
    private String shrug(UUID id, String words) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String t = POLITE.matcher(words.replaceAll("[.!?\\s]+$", "").trim()).replaceAll("").trim().toLowerCase(Locale.ROOT);
        String first = t.split(" ")[0], doing = doing(id);
        boolean known = SUBS.contains(first);
        for (String[] w : WORDS) if (t.matches(w[1])) known = true;
        String[] say;
        if (t.matches(".*\\b(funny|sing|song|syng|sang|story|historie|riddle|gåte)\\b.*"))
            say = new String[] {"I save those for when you are here. Come and play!", "Not over the wire. Log in and I will. But I can tell a joke!", "That is better face to face. See you in the game?"};
        else if (t.matches(".*\\b(hi|hello|hey|hei|hallo|how are you|hvordan går det|good morning|god morgen|good night|god natt|thanks|thank you|takk)\\b.*"))
            say = new String[] {"Hi! All is well here.", "Hello! Nice to hear from you.", "Hei! I am doing fine."};
        else if (t.matches(".*\\b(love|miss|savner|elsker|good girl|good job|well done|bra jobba|flink)\\b.*"))
            say = new String[] {"Aw, thank you!", "That made my day.", "Thanks! I do my best."};
        else if (known)
            say = new String[] {"I need you here for that one.", "That one only works when you are in the world.", "Come and join me, and I will."};
        else
            say = new String[] {"I don't know this one.", "Hmm, that is new to me.", "Sorry, I did not get \"" + (words.length() > 30 ? words.substring(0, 30) + "…" : words) + "\".", "No idea what that means, sorry."};
        return say[r.nextInt(say.length)] + " " + doing + (!known && r.nextInt(3) == 0 ? " (Say help to see what I can do.)" : "");
    }

    /** What it is doing while its owner is away, for answers to orders that change nothing. */
    private String doing(UUID id) {
        String job = conf.getString(path(id, "away.job"), "");
        if (!conf.getBoolean(path(id, "active")) || !flag(id, "watch", false)) return "I have no job while you are away.";
        return "I carry on " + switch (job) {
            case "" -> "guarding home";
            case "home" -> "working at home";
            case "place" -> "working at " + conf.getString(path(id, "away.place"), "the place");
            case "fish" -> "fishing at home";
            case "farm" -> "farming at home";
            case "smelt" -> "smelting at home";
            case "mine" -> "digging for " + conf.getString(path(id, "away.target"), "ore").toLowerCase(Locale.ROOT).replace('_', ' ');
            default -> "exploring";
        } + ".";
    }

    private void awayReport(Player p) {
        UUID id = p.getUniqueId();
        if (conf.getBoolean(path(id, "away.auto"))) {                      // it ran a mob farm on its own: the job from before counts again
            conf.set(path(id, "away.job"), conf.getString(path(id, "away.prevjob")));
            conf.set(path(id, "away.place"), conf.getString(path(id, "away.prevplace")));
            if (conf.getBoolean(path(id, "away.autowatch"))) conf.set(path(id, "watch"), null);
            for (String k : List.of("auto", "autowatch", "prevjob", "prevplace")) conf.set(path(id, "away." + k), null);
        }
        awayLet(id);
        String did = awayDid(id);
        int saved = conf.getInt(path(id, "xp"), 0);
        if (saved > 0) { p.giveExp(saved); conf.set(path(id, "xp"), null); save(); p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f); }
        long min = conf.getLong(path(id, "away.ms"), 0) / 60_000;
        boolean full = conf.getBoolean(path(id, "away.full"), false);
        if (!did.isEmpty()) conf.set(path(id, "away.lastfinds"), conf.getStringList(path(id, "away.finds")).isEmpty() ? null : conf.getStringList(path(id, "away.finds")));
        if (!did.isEmpty()) conf.set(path(id, "away.last"), did + " (" + min + " min of work)" + (conf.getStringList(path(id, "away.finds")).isEmpty() ? "" : ". Special finds: " + String.join("; ", conf.getStringList(path(id, "away.finds")))) + ".");
        String why = conf.getString(path(id, "away.why"));
        List<String> finds = conf.getStringList(path(id, "away.finds"));
        for (String k : List.of("ms", "fish", "crops", "sown", "meal", "fed", "meat", "wool", "smelted", "mined", "explored", "xp", "eggs", "chicks", "goldchicks", "mobs", "sorted", "full", "why", "finds")) conf.set(path(id, "away." + k), null);
        if (did.isEmpty() && !full) return;
        save();
        String stopped = !full ? "" : why != null ? " I stopped because " + why + "." : " My bag and the chests are full and I had no wood for a new chest, so I had to stop.";
        conf.set(path(id, "away.report"), (did.isEmpty() ? "" : "While you were away I " + did + " (" + min + " minutes of work). It is in the chests at home and in my bag."
                + (finds.isEmpty() ? "" : " The best of it: " + String.join("; ", finds) + ".")) + stopped);
        save();
        say(p, "Welcome back, " + nick(p) + "!" + (did.isEmpty() ? "" : " " + min + " min of work" + (saved > 0 ? ", " + saved + " experience for you" : "") + (finds.isEmpty() ? "" : ", " + finds.size() + (finds.size() == 1 ? " special find" : " special finds")) + ".")
                + (full ? " I had to stop early." : "") + " Say log for the details.");
    }

    /** The orders that only change what the helper is like or remembers: these work with the owner away, straight
     *  into helpers.yml. {@code raw} keeps the capitals (for names). False when it is not one of them. */
    private boolean settings(UUID id, String raw, String t, Consumer<String> tell) {
        String[] args = raw.split("\\s+");
        for (String[] w : WORDS) if (t.matches(w[1])) { args = w[0].split(" "); break; }
        Matcher nm = Pattern.compile("(?i)(?:your name is|you are called|call yourself|du heter|heter|kall deg)\\s+(.{2,32})").matcher(raw);
        if (nm.matches()) args = ("name " + nm.group(1)).split("\\s+");
        String sub = args[0].toLowerCase(Locale.ROOT);
        String rest = args.length < 2 ? "" : String.join(" ", Arrays.copyOfRange(args, 1, args.length)).replace('|', ' ').trim();
        String a1 = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
        H h = helpers.get(id);                                            // the figure on watch, if there is one
        switch (sub) {
            case "name" -> {
                if (rest.length() < 2) { tell.accept("A name needs at least two letters: name <name>."); return true; }
                String name = rest.length() > 32 ? rest.substring(0, 32) : rest;
                conf.set(path(id, "name"), name);
                names.put(id, name.toLowerCase(Locale.ROOT));
                if (h != null) h.m.customName(Component.text(name));
                tell.accept("I am now called " + name + ".");
            }
            case "personality", "personlighet" -> tell.accept(personality(id, rest));
            case "callme" -> {
                if (rest.isEmpty()) { tell.accept("callme <name>"); return true; }
                conf.set(path(id, "nick"), rest.length() > 24 ? rest.substring(0, 24) : rest);
                tell.accept("I will call you " + rest + ".");
            }
            case "skin" -> {
                if (rest.isEmpty()) { tell.accept("skin <name>. Skins: " + String.join(", ", skins.names())); return true; }
                String want = rest.replace(' ', '_'), sl = Skins.slug(want);
                if (skins.packProfile(sl) != null) conf.set(path(id, "skin"), "pack:" + sl);
                else if (want.matches("[A-Za-z0-9_]{3,16}")) conf.set(path(id, "skin"), want);
                else { tell.accept("No skin called " + want + ". Skins: " + String.join(", ", skins.names())); return true; }
                if (h != null) {
                    ResolvableProfile rp = skins.packProfile(sl);
                    h.m.setProfile(rp != null ? rp : ResolvableProfile.resolvableProfile().name(want).build());
                }
                tell.accept(look(want) + " (I now look like " + want + ".)");
            }
            case "quiet", "torch", "light", "bridge", "pickup", "show" -> {
                boolean def = sub.equals("pickup");
                boolean v = a1.equals("on") || !a1.equals("off") && !flag(id, sub, def);
                conf.set(path(id, sub), v);
                if (sub.equals("show") && h != null) h.m.setVisibleByDefault(v);
                tell.accept(sub + " is now " + (v ? "on" : "off") + ".");
            }
            case "passive", "fight" -> {
                conf.set(path(id, "passive"), sub.equals("passive"));
                if (h != null && sub.equals("passive")) h.w.setTarget(null);
                tell.accept(sub.equals("passive") ? "I will not fight." : "I fight again.");
            }
            case "style" -> {
                if (!List.of("sword", "bow", "shield").contains(a1)) { tell.accept("style sword | bow | shield"); return true; }
                conf.set(path(id, "style"), a1);
                tell.accept("I fight with the " + a1 + " now.");
            }
            case "big", "small", "normal" -> {
                double v = sub.equals("normal") ? 1.0 : Math.max(MIN_SIZE, Math.min(MAX_SIZE, size(id) * (sub.equals("big") ? 1.5 : 1 / 1.5)));
                if (Math.abs(v - 1.0) < 0.1) v = 1.0;
                conf.set(path(id, "size"), v);
                if (h != null) { set(h.m, Attribute.SCALE, v); set(h.w, Attribute.MOVEMENT_SPEED, 0.38 * speed(id)); }
                tell.accept("My size is now " + Math.round(v * 100) + " % of normal.");
            }
            case "sethome", "remember" -> {
                boolean home = sub.equals("sethome");
                int at = home ? 1 : 2;                                    // sethome x y z [world] | remember <name> x y z [world]
                if (args.length < at + 3 || !args[at].matches("-?\\d+") || !args[at + 1].matches("-?\\d+") || !args[at + 2].matches("-?\\d+")) {
                    tell.accept(home ? "While you are not in the world I need the spot: sethome <x> <y> <z> [world]." : "While you are not in the world I need the spot: remember <name> <x> <y> <z> [world].");
                    return true;
                }
                Location old = home(id);
                World w = args.length > at + 3 ? Bukkit.getWorld(args[at + 3]) : old != null ? old.getWorld() : Bukkit.getWorlds().get(0);
                if (w == null) { tell.accept("There is no world called " + args[at + 3] + "."); return true; }
                Location l = new Location(w, Integer.parseInt(args[at]) + 0.5, Integer.parseInt(args[at + 1]), Integer.parseInt(args[at + 2]) + 0.5);
                String k = home ? "home" : "places." + slug(args[1]);
                conf.set(path(id, k), spot(l));
                if (home) { awayLet(id); awayOre.remove(id); awayScan.remove(id); yards.remove(id); }
                tell.accept((home ? "Home" : "The place " + slug(args[1]).replace('_', ' ')) + " is now " + w.getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ() + ".");
            }
            case "places" -> {
                ConfigurationSection sec = conf.getConfigurationSection(path(id, "places"));
                List<String> known = new ArrayList<>();
                Location hm = home(id);
                if (hm != null) known.add("home (" + hm.getWorld().getName() + " " + hm.getBlockX() + " " + hm.getBlockY() + " " + hm.getBlockZ() + ")");
                if (sec != null) for (String k : sec.getKeys(false)) {
                    Location l = place(sec.getString(k));
                    known.add(k.replace('_', ' ') + (l == null ? "" : " (" + l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ() + ")"));
                }
                tell.accept(known.isEmpty() ? "I don't remember any places yet." : "I remember: " + String.join(", ", known) + ".");
            }
            case "forget" -> {
                String k = slug(rest);
                boolean had = !k.isEmpty() && conf.contains(path(id, "places." + k));
                conf.set(path(id, "places." + k), null);
                tell.accept(had ? "Forgotten: " + rest + "." : "I don't know a place called " + rest + ".");
            }
            case "sum" -> { tell.accept(sum(id)); return true; }
            case "finds" -> { tell.accept(finds(id)); return true; }
            case "bag" -> {
                Map<String, Integer> in = new java.util.TreeMap<>();
                for (ItemStack it : bag(id).getContents()) if (!empty(it)) in.merge(word(it.getType()), it.getAmount(), Integer::sum);
                List<String> out = new ArrayList<>();
                in.forEach((k, v) -> out.add(v + " " + k));
                tell.accept(out.isEmpty() ? "My bag is empty." : "In my bag: " + String.join(", ", out) + ".");
            }
            case "routines" -> {
                List<String> rs = routineList(id);
                StringBuilder sb = new StringBuilder(rs.isEmpty() ? "I have no routines." : "My routines:");
                for (int i = 0; i < rs.size(); i++) sb.append(" ").append(i + 1).append(") ").append(rs.get(i).replace("|", ": ")).append(i + 1 < rs.size() ? ";" : ".");
                tell.accept(sb.toString());
            }
            case "routine" -> {
                List<String> rs = routineList(id);
                if (a1.equals("clear") || a1.equals("reset")) { conf.set(path(id, "routines"), null); tell.accept("All " + rs.size() + " routines are gone."); }
                else if (a1.equals("remove") || a1.equals("delete")) {
                    int n = args.length < 3 || !args[2].matches("\\d{1,2}") ? 0 : Integer.parseInt(args[2]);
                    if (n < 1 || n > rs.size()) { tell.accept("Which one? I have " + rs.size() + "."); return true; }
                    String gone = rs.remove(n - 1);
                    conf.set(path(id, "routines"), rs);
                    tell.accept("Removed: " + gone.replace("|", ": ") + ".");
                } else {
                    boolean every = a1.equals("every") && args.length > 3 && args[2].matches("\\d{1,3}") && Integer.parseInt(args[2]) > 0;
                    if (!every && !TIMES.containsKey(a1) || args.length < (every ? 4 : 3)) { tell.accept("routine <morning|noon|evening|night|every <minutes>> <orders> | routine remove <number> | routine clear"); return true; }
                    if (rs.size() >= 10) { tell.accept("Ten routines is all I can keep in my head. Remove one first."); return true; }
                    String orders = String.join(" ", Arrays.copyOfRange(args, every ? 3 : 2, args.length)).replace('|', ' ').trim();
                    rs.add((every ? "every " + args[2] : a1) + "|" + orders);
                    conf.set(path(id, "routines"), rs);
                    tell.accept("Okay: " + (every ? "every " + args[2] + " minutes" : "every " + a1) + " I will: " + orders + ".");
                }
            }
            case "dismiss" -> { conf.set(path(id, "active"), false); despawn(id, false); awayLet(id); tell.accept("I went home. Nothing happens until I am called again (summon)."); }
            case "summon" -> { conf.set(path(id, "active"), true); tell.accept("I am back on duty" + (flag(id, "watch", false) ? "." : ", but with no job for when you are away (say fish, farm, smelt, mine <ore>, explore or guard).")); }
            case "watch" -> {
                if (a1.equals("off")) return offline(id, "stop", tell);
                if (AWAY_JOBS.contains(a1)) return offline(id, a1.equals("mobfarm") ? "mobfarm" : a1.equals("farm") && args.length > 2 ? "jobplace " + String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : a1.equals("mine") ? "mine " + String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : a1, tell);
                conf.set(path(id, "watch"), true);
                tell.accept("I stay at home while you are away" + (conf.getString(path(id, "away.job"), "").isEmpty() ? " and guard." : " and " + conf.getString(path(id, "away.job")) + "."));
            }
            default -> { return false; }
        }
        save();
        return true;
    }

    /** An order for a helper whose owner is logged out. Everything happens at home. False when it is not something
     *  it can do without the owner in the world. */
    private boolean offline(UUID id, String words, Consumer<String> tell) {
        String t = POLITE.matcher(words.replaceAll("[.!?\\s]+$", "").trim()).replaceAll("").trim().toLowerCase(Locale.ROOT);
        if (buildOrder(id, null, t, tell)) return true;
        if (t.matches(".*\\b(joke|jokes|vits|vitser|spøk)\\b.*")) { tell.accept(joke(id)); return true; }
        if (settings(id, words.trim(), t, tell)) return true;
        Location hm = home(id);
        Matcher m = Pattern.compile("(?:trade for|trade|buy me|buy|kjøp meg|kjøp|handle|bytt til deg)\\s+(.{1,40})").matcher(t);
        if (m.matches()) {
            if (hm == null) { tell.accept("I have no home yet (stand at home in the game and say sethome, or give me sethome <x> <y> <z>)."); return true; }
            if (!trade(id, null, hm, m.group(1), false, tell)) { }
            return true;
        }
        if (t.matches("trade|trades|handel|handle|kjøp|what can you buy|hva kan du kjøpe")) {
            if (hm == null) { tell.accept("I have no home yet (stand at home in the game and say sethome, or give me sethome <x> <y> <z>)."); return true; }
            List<String> offers = new ArrayList<>();
            for (Entity e : hm.getWorld().getNearbyEntities(hm, HOME, 8, HOME))
                if (e instanceof AbstractVillager v) for (MerchantRecipe r : v.getRecipes()) {
                    if (r.getUses() >= r.getMaxUses() || offers.size() >= 40) continue;
                    List<String> cost = new ArrayList<>();
                    for (ItemStack c : r.getIngredients()) if (!empty(c)) cost.add(c.getAmount() + " " + word(c.getType()));
                    offers.add(r.getResult().getAmount() + " " + word(r.getResult().getType()) + (magic(r.getResult()) == null ? "" : " (" + magic(r.getResult()) + ")") + " for " + String.join(" + ", cost));
                }
            tell.accept(offers.isEmpty() ? "There is no villager with anything to sell within 100 blocks of home." : "The villagers at home sell: " + String.join("; ", offers) + ". Say trade and the item, like trade bread 3.");
            return true;
        }
        if (t.matches("explore|go exploring|utforsk|dra på oppdagelsesferd|oppdag")) {
            conf.set(path(id, "watch"), true);
            conf.set(path(id, "away.job"), "explore");
            for (String k : List.of("full", "why", "ms")) conf.set(path(id, "away." + k), null);
            save();
            tell.accept("I will explore the land around home while you are away, and remember the buildings I find." + (hm == null ? " But I have no home yet (stand at home in the game and say sethome, or give me sethome <x> <y> <z>)." : ""));
            return true;
        }
        m = Pattern.compile("(?:mine|dig for|dig|find|look for|finn|let etter|grav etter|grav|hugg)\\s+(.{1,30})").matcher(t);
        if (m.matches()) {
            String target = oreTarget(m.group(1));
            if (target == null) { tell.accept("I can dig for ores while you are away: diamond, coal, iron, gold, copper, redstone, lapis, emerald, quartz, ancient debris, obsidian. Not " + m.group(1) + "."); return true; }
            conf.set(path(id, "watch"), true);
            conf.set(path(id, "away.job"), "mine");
            conf.set(path(id, "away.target"), target);
            for (String k : List.of("full", "why", "ms")) conf.set(path(id, "away." + k), null);
            awayOre.remove(id);
            awayScan.remove(id);
            save();
            tell.accept("I will dig for " + target.toLowerCase(Locale.ROOT).replace('_', ' ') + " within 128 blocks of home while you are away." + (hm == null ? " But I have no home yet (stand at home in the game and say sethome, or give me sethome <x> <y> <z>)." : ""));
            return true;
        }
        m = Pattern.compile("(?:craft|make|lag|lage)\\s+(.{1,40})").matcher(t);
        if (m.matches()) { craft(id, null, hm, m.group(1), false, tell); return true; }
        m = Pattern.compile("(?:collect|collect from|hent alt fra|hent fra)\\s+(.{1,30})").matcher(t);
        Matcher m2 = COLLECT_WORDS.matcher(t);
        if (m.matches() || m2.matches()) {
            String where = m.matches() ? m.group(1) : m2.group(1);
            Location l = placeNamed(id, where);
            if (l == null) { tell.accept("I don't know a place called " + where + "."); return true; }
            int moved = 0;
            for (Inventory inv : stores(l))
                for (int j = 0; j < inv.getSize(); j++) {
                    ItemStack it = inv.getItem(j);
                    if (empty(it)) continue;
                    int before = it.getAmount();
                    Map<Integer, ItemStack> rest;
                    if (hm != null && myBarrel(id, hm.getWorld()) != null) { storage.putMine(id, List.of(it.clone())); rest = Map.of(); }
                    else {
                        rest = bag(id).addItem(it.clone());
                        for (Inventory to : stores(hm, HOME)) { if (rest.isEmpty()) break; rest = to.addItem(rest.values().iterator().next()); }
                    }
                    int left = rest.isEmpty() ? 0 : rest.values().iterator().next().getAmount();
                    moved += before - left;
                    it.setAmount(left);
                    inv.setItem(j, left > 0 ? it : null);
                }
            dirty.add(id);
            tell.accept(moved == 0 ? "There was nothing to bring from " + where + " (or everything at home is full)." : "I brought " + moved + (moved == 1 ? " thing" : " things") + " from " + where + " home.");
            return true;
        }
        if (t.matches("(?:tidy|tidy up|sort|sort the chests|rydd|rydd opp|rydd kistene|sorter|sorter kistene)")) {
            int n = tidy(id);
            tell.accept(n < 0 ? (hm == null ? "I have no home yet (stand at home in the game and say sethome, or give me sethome <x> <y> <z>)." : "I need at least two chests within 100 blocks of home.")
                    : n == 0 ? "The chests at home are tidy already." : "I tidied the chests at home: " + n + (n == 1 ? " thing" : " things") + " moved to where the rest of its kind is.");
            return true;
        }
        if (t.matches("(?:unload|empty your bag|empty the bag|tøm|tøm sekken)") && hm != null && myBarrel(id, hm.getWorld()) != null) {
            int moved = storage.putMine(id, bag(id));
            dirty.add(id);
            tell.accept("I put " + moved + (moved == 1 ? " thing" : " things") + " from my bag in your own store in the magic barrel.");
            return true;
        }
        if (t.matches("(?:unload|empty your bag|empty the bag|tøm|tøm sekken)")) {
            Inventory bag = bag(id);
            int moved = 0;
            for (int i = 0; i < bag.getSize(); i++) {
                ItemStack it = bag.getItem(i);
                if (empty(it)) continue;
                int before = it.getAmount();
                for (Inventory inv : stores(hm, HOME)) {
                    Map<Integer, ItemStack> rest = inv.addItem(it.clone());
                    it.setAmount(rest.isEmpty() ? 0 : rest.values().iterator().next().getAmount());
                    if (it.getAmount() == 0) break;
                }
                moved += before - it.getAmount();
                bag.setItem(i, it.getAmount() > 0 ? it : null);
            }
            dirty.add(id);
            tell.accept("I put " + moved + (moved == 1 ? " thing" : " things") + " from my bag in the chests at home.");
            return true;
        }
        if (t.matches("(?:smelt now|smelt once|smelt|smelt nå)") && hm != null && conf.getString(path(id, "away.job"), "").equals("smelt")) {
            tell.accept("I took " + awaySmelt(id, hm) + " things out of the furnaces at home and filled them again.");
            return true;
        }
        Matcher pj = Pattern.compile("jobplace (.+)").matcher(t);
        String spot = pj.matches() ? pj.group(1) : null;
        Matcher mfo = Pattern.compile("(?:run |drive |driv |kjør |work (?:at |on )?|jobb (?:på |i |ved )?)?(?:the )?mob ?farm(?:en)?\\b\\s*(.*)").matcher(t);
        if (spot == null && mfo.matches()) {
            String pick = farmPick(id, mfo.group(1));
            if (pick.startsWith("?")) { tell.accept(pick.substring(1)); return true; }
            spot = pick;
            if (conf.contains(path(id, "places." + spot))) farmAdd(id, spot);
        }
        if (spot == null) {
            Matcher fm = Pattern.compile("(?:farm|work|harvest|jobb|høst|arbeid)(?: at| on| in| på| i| ved)?(?: the)? (.{2,30})").matcher(t);
            if (fm.matches() && !slug(fm.group(1)).matches("home|hjemme|hjem|at_home|around_the_house") && conf.contains(path(id, "places." + slug(fm.group(1))))) spot = slug(fm.group(1));
        }
        if (spot != null) {
            conf.set(path(id, "watch"), true);
            conf.set(path(id, "away.job"), "place");
            conf.set(path(id, "away.place"), spot);
            conf.set(path(id, "away.full"), null);
            conf.set(path(id, "away.ms"), null);
            save();
            yards.remove(id);
            mobYards.remove(id);
            tell.accept("I will work at " + spot + " while you are away. " + "I look at what is there: fields I harvest and sow again, a mob farm I run (call the monsters, kill what comes to me, keep the drops and the experience for you), and where there is only water I fish."
                    + (placeNamed(id, spot) != null ? "" : " But I know no place called " + spot + " yet: stand there and say remember " + spot + ", or give me remember " + spot + " <x> <y> <z> [world].")
                    + (conf.getBoolean(path(id, "active")) ? "" : " But I am dismissed, so nothing happens until I am called."));
            return true;
        }
        String job = t.matches("(?:work|work at home|work around the house|do the chores|chores|jobb|jobb hjemme|arbeid hjemme|hjemmearbeid|gjør husarbeid)") ? "home" : t.matches(".*\\b(fish|fisk)\\w*.*") ? "fish" : t.matches(".*\\b(farm|harvest|høst)\\w*.*") ? "farm" : t.matches(".*\\bsmelt\\w*.*") ? "smelt"
                : t.matches(".*\\b(guard|vokt|pass på)\\w*.*") ? "guard" : t.matches(".*\\b(stop|stopp|off|av|nothing|ingenting)\\b.*") ? "off" : null;
        if (job == null) return false;
        conf.set(path(id, "watch"), job.equals("off") ? null : true);
        conf.set(path(id, "away.job"), job.equals("off") || job.equals("guard") ? null : job);
        conf.set(path(id, "away.rest"), job.equals("off") || job.equals("guard") ? true : null);          // told to do nothing: no chores by itself
        conf.set(path(id, "away.full"), null);
        conf.set(path(id, "away.ms"), null);                              // a new order: the clock starts again
        save();
        tell.accept((job.equals("off") ? "I do nothing while you are away." : job.equals("mobfarm") ? "I will work at the mob farm while you are away: call the monsters, kill what comes to me, and keep the drops and the experience for you."
                        + (conf.contains(path(id, "places.mobfarm")) ? "" : " But I do not know where it is yet: stand in the killing room and say remember mobfarm, or give me remember mobfarm <x> <y> <z> [world].")
                : "I will " + (job.equals("home") ? "work" : job) + " at home while you are away.")
                + (hm == null ? " But I have no home yet (stand at home in the game and say sethome, or give me sethome <x> <y> <z>)." : "")
                + (conf.getBoolean(path(id, "active")) ? "" : " But I am dismissed, so nothing happens until I am called."));
        return true;
    }

    /** The owner's routines also run while they are away, for the orders offline() knows. */
    private void awayRoutines(UUID id, Location hm) {
        List<String> rs = conf.getStringList(path(id, "routines"));
        if (rs.isEmpty()) return;
        long t = hm.getWorld().getTime(), now = System.currentTimeMillis();
        Long lastSeen = awayTime.put(id, t);
        long last = lastSeen == null ? -1 : lastSeen;
        for (String r : rs) {
            String[] a = r.split("\\|", 2);
            if (a.length < 2) continue;
            boolean due;
            Integer at = TIMES.get(a[0]);
            if (at != null) due = last >= 0 && last != t && (last < t ? last < at && at <= t : at > last || at <= t);
            else {
                long every = Long.parseLong(a[0].replaceAll("\\D", "")) * 60_000L;
                Long ranAt = awayRan.putIfAbsent(id + r, now);
                due = ranAt != null && now - ranAt >= every;
                if (due) awayRan.put(id + r, now);
            }
            if (!due) continue;
            for (String order : a[1].split("\\s*(?:,|;|\\band\\b|\\bog\\b|\\bthen\\b|\\bså\\b)\\s*")) {
                if (order.isBlank()) continue;
                String o = order.trim();
                if (!offline(id, o, said -> awayLog(id, "routine \"" + o + "\": " + said))) awayLog(id, "routine \"" + o + "\": not something I can do while you are away");
            }
        }
    }

    /** From the server console or RCON: helper pause | resume | save | status | tell <player> <words>. */
    private void console(org.bukkit.command.CommandSender to, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "pause", "resume" -> {
                conf.set("settings.paused", sub.equals("pause") ? true : null);
                dirty.addAll(bags.keySet());
                flushBags();
                save();
                to.sendMessage(sub.equals("pause") ? "Helpers are paused: no work or guarding while their owners are away. Everything is saved. (helper resume)" : "Helpers work again.");
            }
            case "save" -> {
                dirty.addAll(bags.keySet());
                flushBags();
                save();
                to.sendMessage("Helper bags and progress are saved.");
            }
            case "status" -> {
                StringBuilder sb = new StringBuilder(paused() ? "Helpers are PAUSED." : "Helpers are running.");
                sb.append(" Work away from the owner stops after ").append(conf.getDouble("settings.away-hours", 2)).append(" hours.");
                for (String k : conf.getKeys(false)) {
                    UUID id;
                    try { id = UUID.fromString(k); } catch (IllegalArgumentException e) { continue; }
                    if (!conf.getBoolean(path(id, "active"))) continue;
                    String who = Bukkit.getOfflinePlayer(id).getName();
                    boolean on = Bukkit.getPlayer(id) != null;
                    String job = conf.getString(path(id, "away.job"), "guard");
                    String did = awayDid(id);
                    sb.append("\n").append(who == null ? k : who).append(" (").append(name(id)).append("): ").append(on ? "online" : "offline")
                            .append(", when away: ").append(flag(id, "watch", false) ? job : "nothing").append(home(id) == null ? ", NO HOME SET" : "");
                    if (!on && flag(id, "watch", false)) sb.append(", worked ").append(conf.getLong(path(id, "away.ms"), 0) / 60_000).append(" min")
                            .append(did.isEmpty() ? "" : ", " + did).append(conf.getBoolean(path(id, "away.full"), false) ? ", STOPPED: chests full" : "");
                }
                to.sendMessage(sb.toString());
            }
            case "named" -> {                                             // helper named <helper name> <words>
                if (args.length < 3) { to.sendMessage("Usage: helper named <helper name> <words>"); return; }
                List<String> owners = new ArrayList<>();
                for (Map.Entry<UUID, String> n : names.entrySet())
                    if (n.getValue().equalsIgnoreCase(args[1])) { String who = Bukkit.getOfflinePlayer(n.getKey()).getName(); if (who != null) owners.add(who); }
                if (owners.size() != 1) { to.sendMessage(owners.isEmpty() ? "No helper is called " + args[1] + "." : "Several helpers are called " + args[1] + " (" + String.join(", ", owners) + "): use helper tell <player> <words>."); return; }
                String[] as = args.clone();
                as[0] = "tell";
                as[1] = owners.get(0);
                console(to, as);
            }
            case "tell" -> {
                if (args.length < 3) { to.sendMessage("Usage: helper tell <player> <words>"); return; }
                String words = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                Player pl = Bukkit.getPlayerExact(args[1]);
                if (pl != null) {
                    pl.sendMessage(Component.text("[console] " + name(pl.getUniqueId()) + ", " + words, NamedTextColor.GRAY));
                    talk(pl, words);
                    to.sendMessage("Told " + pl.getName() + "'s helper: " + words + " (the answer shows in the game)");
                    return;
                }
                org.bukkit.OfflinePlayer off = Bukkit.getOfflinePlayerIfCached(args[1]);
                if (off == null || !conf.contains(off.getUniqueId().toString())) { to.sendMessage(args[1] + " has no helper (check the spelling)."); return; }
                UUID id = off.getUniqueId();
                String who = off.getName();
                if (!offline(id, words, said -> { said = voice(id, said); to.sendMessage(who + "'s helper: " + said); awayLog(id, "(from the console: " + words + ") " + said); }))
                    if (!words.trim().toLowerCase(Locale.ROOT).matches("help|hjelp|\\?|what can you do|hva kan du")) to.sendMessage(who + "'s helper: " + voice(id, shrug(id, words)));
                    else to.sendMessage(who + "'s helper: " + doing(id) + " What works while you are away:\n"
                            + "  jobs while away: mobfarm [name], farm <place> (at a saved place: fields, mob farm or fishing, whatever is there), work (a bit of everything at home), fish, farm, smelt, mine <ore>, explore, guard, stop\n"
                            + "  at once: joke, build house <x> <y> <z> [world], undo build; at home: sum, finds, tidy, unload, trade, trade <item> [times], craft <item> [n], collect <place>, bag\n"
                            + "  settings: personality <name> [name] | reset, name <name>, skin <name>, callme <name>, big, small, normal, style sword|bow|shield, passive, fight, quiet, torch, light, bridge, pickup, show [on|off]\n"
                            + "  places: sethome <x> <y> <z> [world], remember <name> <x> <y> <z> [world], places, forget <name>\n"
                            + "  routines: routine <morning|noon|evening|night|every N> <orders>, routines, routine remove <n>, routine clear\n"
                            + "  dismiss, summon. See also: helper status");
                flushBags();
            }
            default -> to.sendMessage("helper pause | resume | save | status | tell <player> <words>");
        }
    }

    // --- Chores: tidying, mending, errands, trading, deliveries, routines, fixing creeper holes --------------------

    private static int count(List<Inventory> in, Predicate<Material> kind) {
        int n = 0;
        for (Inventory inv : in) for (ItemStack it : inv.getStorageContents()) if (!empty(it) && kind.test(it.getType())) n += it.getAmount();
        return n;
    }

    private static void take(List<Inventory> in, Predicate<Material> kind, int n) {
        for (Inventory inv : in) {
            ItemStack[] c = inv.getStorageContents();
            for (int i = 0; i < c.length && n > 0; i++) {
                if (empty(c[i]) || !kind.test(c[i].getType())) continue;
                int t = Math.min(n, c[i].getAmount());
                c[i].setAmount(c[i].getAmount() - t);
                inv.setItem(i, c[i].getAmount() > 0 ? c[i] : null);
                n -= t;
            }
        }
    }

    private void hand(UUID id, Player p, ItemStack it) {
        if (p != null) { give(p, it); return; }
        Map<Integer, ItemStack> rest = bag(id).addItem(it);
        dirty.add(id);
        for (Inventory inv : stores(homeNow(id), HOME)) {
            if (rest.isEmpty()) return;
            rest = inv.addItem(rest.values().iterator().next());
        }
        Location hm = homeNow(id);
        if (!rest.isEmpty() && hm != null) hm.getWorld().dropItem(hm, rest.values().iterator().next());
    }

    private static String word(Material m) { return m.name().toLowerCase(Locale.ROOT).replace('_', ' '); }

    /** Every kind of thing ends up in the home chest that already has the most of it. Returns how many things were
     *  moved, or -1 when there are not two chests at home. */
    private int tidy(UUID id) {
        Location hm = homeNow(id);
        return hm == null ? -1 : tidy(stores(hm, HOME));
    }

    private int tidyStuck;                                                  // after tidy(): things left in a chest they do not belong in

    private static String coarse(ItemStack it) {
        Material m = it.getType();
        String n = m.name();
        if (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS") || m == Material.ELYTRA) return "armour";
        if (n.endsWith("_SWORD") || m == Material.BOW || m == Material.CROSSBOW || m == Material.TRIDENT || m == Material.SHIELD || n.equals("MACE") || n.endsWith("ARROW")) return "weapons";
        if (m.getMaxDurability() > 0) return "tools";
        if (m == Material.ENCHANTED_BOOK || m == Material.BOOK || m == Material.WRITABLE_BOOK) return "books";
        if (m.isEdible()) return "food";
        if (n.contains("INGOT") || n.contains("NUGGET") || n.startsWith("RAW_") || n.endsWith("_ORE") || m == Material.DIAMOND || m == Material.EMERALD || m == Material.COAL || m == Material.CHARCOAL
                || m == Material.REDSTONE || m == Material.LAPIS_LAZULI || m == Material.QUARTZ || m == Material.AMETHYST_SHARD || m == Material.NETHERITE_SCRAP) return "ore and metal";
        if (!lootKind(it).equals("other")) return "mob loot";
        if (Tag.LOGS.isTagged(m) || Tag.PLANKS.isTagged(m) || Tag.WOODEN_SLABS.isTagged(m) || Tag.WOODEN_STAIRS.isTagged(m) || Tag.WOODEN_FENCES.isTagged(m) || Tag.WOODEN_DOORS.isTagged(m)) return "wood";
        if (Tag.FLOWERS.isTagged(m) || Tag.SAPLINGS.isTagged(m) || Tag.LEAVES.isTagged(m) || n.endsWith("_SEEDS") || m == Material.WHEAT || m == Material.SUGAR_CANE || m == Material.BAMBOO || m == Material.CACTUS) return "plants";
        if (m.isBlock()) return "blocks";
        return "other";
    }

    /** The narrow kind of a thing: "golden armour", "iron tools" …; for what is not gear, the same as the broad kind. */
    private static String fine(ItemStack it) {
        String c = coarse(it), n = it.getType().name();
        if (c.equals("armour") || c.equals("tools") || c.equals("weapons"))
            for (String of : new String[] {"GOLDEN", "LEATHER", "IRON", "CHAINMAIL", "COPPER", "DIAMOND", "NETHERITE", "STONE", "WOODEN"}) if (n.startsWith(of + "_")) return of.toLowerCase(Locale.ROOT) + " " + c;
        return c;
    }

    private static boolean fits(String theme, ItemStack it) {
        return theme == null || (theme.startsWith("=") ? it.getType().name().equals(theme.substring(1)) : fine(it).equals(theme) || coarse(it).equals(theme));
    }

    /** What a chest is for: the thing in an item frame on it, or else what at least six in ten of its stacks are
     *  (first the narrow kind, like golden armour, then the broad one, like armour). Null: a chest for anything. */
    private static String theme(Inventory inv) {
        Location l = inv.getLocation();
        if (l != null) for (Entity e : l.getWorld().getNearbyEntities(l.clone().add(0.5, 0.5, 0.5), 1.6, 1.2, 1.6))
            if (e instanceof org.bukkit.entity.ItemFrame fr && !empty(fr.getItem())) { String f = fine(fr.getItem()); return f.equals(coarse(fr.getItem())) ? "=" + fr.getItem().getType().name() : f; }
        Map<String, Integer> narrow = new HashMap<>(), broad = new HashMap<>();
        int stacks = 0;
        for (ItemStack it : inv.getContents()) if (!empty(it)) { stacks++; narrow.merge(fine(it), 1, Integer::sum); broad.merge(coarse(it), 1, Integer::sum); }
        if (stacks < 5) return null;
        for (Map<String, Integer> kinds : List.of(narrow, broad))
            for (Map.Entry<String, Integer> k : kinds.entrySet()) if (!k.getKey().equals("other") && k.getValue() * 10 >= stacks * 6) return k.getKey();
        return null;
    }

    /** Tidies a set of chests. Every kind of thing ends up in the chest that already has the most of it, and what does
     *  not belong in a chest (leather boots among the golden armour) moves to a chest for its kind, or else to a chest
     *  for anything. Returns how many things were moved, or -1 when there are not two chests. */
    private int tidy(List<Inventory> invs) {
        tidyStuck = 0;
        if (invs.size() < 2) return -1;
        int size = invs.size();
        String[] theme = new String[size];
        for (int i = 0; i < size; i++) theme[i] = theme(invs.get(i));
        Map<Material, int[]> have = new HashMap<>();
        for (int i = 0; i < size; i++)
            for (ItemStack it : invs.get(i).getContents()) if (!empty(it) && fits(theme[i], it)) have.computeIfAbsent(it.getType(), k -> new int[size])[i] += it.getAmount();
        int moved = 0;
        for (int i = 0; i < size; i++) {
            Inventory inv = invs.get(i);
            for (int j = 0; j < inv.getSize(); j++) {
                ItemStack it = inv.getItem(j);
                if (empty(it)) continue;
                int[] n = have.get(it.getType());
                if (n == null) continue;
                int best = 0;
                for (int k = 1; k < n.length; k++) if (n[k] > n[best]) best = k;
                if (best == i || n[best] == n[i] || n[best] == 0) continue;
                Map<Integer, ItemStack> rest = invs.get(best).addItem(it.clone());
                int left = rest.isEmpty() ? 0 : rest.values().iterator().next().getAmount();
                moved += it.getAmount() - left;
                it.setAmount(left);
                inv.setItem(j, left > 0 ? it : null);
            }
        }
        for (int i = 0; i < size; i++) {                                    // what still sits where it does not belong
            if (theme[i] == null) continue;
            Inventory inv = invs.get(i);
            for (int j = 0; j < inv.getSize(); j++) {
                ItemStack it = inv.getItem(j);
                if (empty(it) || fits(theme[i], it)) continue;
                String f = fine(it);
                List<int[]> to = new ArrayList<>();                          // {chest, how well it suits}
                for (int k = 0; k < size; k++) {
                    if (k == i || !fits(theme[k], it)) continue;
                    int score = 1;
                    if (theme[k] != null) score = theme[k].equals(f) || theme[k].startsWith("=") ? 4 : 3;
                    else for (ItemStack c : invs.get(k).getContents()) if (!empty(c) && fine(c).equals(f)) { score = 2; break; }
                    to.add(new int[] {k, score});
                }
                to.sort((a, b) -> b[1] - a[1]);
                ItemStack rest = it.clone();
                for (int[] k : to) {
                    Map<Integer, ItemStack> left = invs.get(k[0]).addItem(rest);
                    if (left.isEmpty()) { rest = null; break; }
                    rest = left.values().iterator().next();
                }
                int gone = it.getAmount() - (rest == null ? 0 : rest.getAmount());
                moved += gone;
                inv.setItem(j, rest);
                if (rest != null) tidyStuck++;
            }
        }
        return moved;
    }

    /** What it takes to mend a thing: one of this. Null = nothing needed. */
    private static Predicate<Material> mendWith(Material m) {
        String n = m.name();
        Material need = n.startsWith("IRON_") || n.startsWith("CHAINMAIL_") || n.equals("SHEARS") || n.equals("SHIELD") || n.equals("FLINT_AND_STEEL") ? Material.IRON_INGOT
                : n.startsWith("DIAMOND_") || n.startsWith("NETHERITE_") ? Material.DIAMOND
                : n.startsWith("GOLDEN_") ? Material.GOLD_INGOT
                : n.startsWith("COPPER_") ? Material.COPPER_INGOT
                : n.startsWith("STONE_") ? Material.COBBLESTONE
                : n.startsWith("LEATHER_") ? Material.LEATHER
                : n.equals("BOW") || n.equals("CROSSBOW") || n.equals("FISHING_ROD") ? Material.STRING
                : n.equals("ELYTRA") ? Material.PHANTOM_MEMBRANE : null;
        if (n.startsWith("WOODEN_")) return Tag.PLANKS::isTagged;
        return need == null ? null : x -> x == need;
    }

    private static String mendName(Material m) {
        String n = m.name();
        return n.startsWith("WOODEN_") ? "planks" : n.startsWith("IRON_") || n.startsWith("CHAINMAIL_") || n.equals("SHEARS") || n.equals("SHIELD") || n.equals("FLINT_AND_STEEL") ? "iron ingots"
                : n.startsWith("DIAMOND_") || n.startsWith("NETHERITE_") ? "diamonds" : n.startsWith("GOLDEN_") ? "gold ingots" : n.startsWith("COPPER_") ? "copper ingots"
                : n.startsWith("STONE_") ? "cobblestone" : n.startsWith("LEATHER_") ? "leather" : n.equals("ELYTRA") ? "phantom membranes" : "string";
    }

    private void repair(Player p, H h) {
        List<Inventory> pay = List.of(bag(p.getUniqueId()), p.getInventory());
        var inv = p.getInventory();
        ItemStack[] worn = inv.getArmorContents();
        List<ItemStack> all = new ArrayList<>(Arrays.asList(worn));
        ItemStack main = inv.getItemInMainHand(), off = inv.getItemInOffHand();
        all.add(main);
        all.add(off);
        int fixed = 0;
        Set<String> missing = new java.util.LinkedHashSet<>();
        for (ItemStack it : all) {
            if (empty(it) || !(it.getItemMeta() instanceof Damageable d) || !d.hasDamage()) continue;
            Predicate<Material> need = mendWith(it.getType());
            if (need != null) {
                if (count(pay, need) < 1) { missing.add(mendName(it.getType())); continue; }
                take(pay, need, 1);
            }
            d.setDamage(0);
            it.setItemMeta(d);
            fixed++;
        }
        inv.setArmorContents(worn);
        inv.setItemInMainHand(main);
        inv.setItemInOffHand(off);
        if (fixed > 0) {
            dirty.add(p.getUniqueId());
            swing(p, h);
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.3f);
        }
        say(p, (fixed == 0 && missing.isEmpty() ? "Nothing you hold or wear needs mending." : fixed == 0 ? "I could not mend anything." : "I mended " + fixed + (fixed == 1 ? " thing." : " things."))
                + (missing.isEmpty() ? "" : " For the rest I need " + String.join(" and ", missing) + "."));
    }

    /** An errand: everything in the chests at a remembered place comes back in the bag (as much as fits). */
    private void collect(Player p, H h, String where) {
        Location l = placeNamed(p.getUniqueId(), where);
        if (l == null) { say(p, "I don't know a place called " + where + "."); return; }
        List<Inventory> from = stores(l);
        if (from.isEmpty()) { say(p, "There are no chests within eight blocks of " + where + "."); return; }
        Inventory bag = bag(p.getUniqueId());
        int moved = 0;
        boolean more = false;
        for (Inventory inv : from)
            for (int j = 0; j < inv.getSize(); j++) {
                ItemStack it = inv.getItem(j);
                if (empty(it)) continue;
                Map<Integer, ItemStack> rest = bag.addItem(it.clone());
                int left = rest.isEmpty() ? 0 : rest.values().iterator().next().getAmount();
                moved += it.getAmount() - left;
                more |= left > 0;
                it.setAmount(left);
                inv.setItem(j, left > 0 ? it : null);
            }
        dirty.add(p.getUniqueId());
        zip(p, h);
        say(p, moved == 0 ? (more ? "My bag is full, I could not bring anything." : "The chests at " + where + " are empty.")
                : "I went to " + where + " and brought back " + moved + (moved == 1 ? " thing." : " things.") + (more ? " There is more, but my bag is full." : ""));
    }

    /** Buys from a villager within 16 blocks, as many times as asked and affordable. False when nobody sells it. */
    private boolean trade(Player p, H h, String what, boolean quiet) {
        return trade(p.getUniqueId(), p, p.getLocation(), what, quiet, t -> say(p, t));
    }

    /** {@code p} is null when the owner is logged out: then the villagers at home, paid from the bag and the chests there. */
    private boolean trade(UUID id, Player p, Location at, String what, boolean quiet, Consumer<String> tell) {
        int times = 1;
        Matcher num = NUMBER.matcher(what);
        if (num.find()) { times = Math.max(1, Math.min(64, Integer.parseInt(num.group(1)))); what = what.substring(0, num.start()) + what.substring(num.end()); }
        String term = slug(what).toUpperCase(Locale.ROOT);
        if (term.isEmpty()) { if (!quiet) tell.accept("Trade for what? Say trade and an item, like trade bread 3."); return false; }
        if (term.endsWith("S") && term.length() > 3) term = term.substring(0, term.length() - 1);
        List<Inventory> pay = new ArrayList<>(List.of(bag(id)));
        if (p != null) pay.add(p.getInventory()); else pay.addAll(stores(homeNow(id), HOME));
        String price = null;
        int reach = p != null ? 16 : HOME;
        for (Entity e : at.getWorld().getNearbyEntities(at, reach, 8, reach)) {
            if (!(e instanceof AbstractVillager v)) continue;
            for (MerchantRecipe r : v.getRecipes()) {
                ItemStack out = r.getResult();
                String spell = magic(out);                                   // "trade mending" finds the book with mending
                if (!out.getType().name().contains(term) && (spell == null || !spell.toUpperCase(Locale.ROOT).replace(' ', '_').contains(term))) continue;
                List<ItemStack> cost = new ArrayList<>(r.getIngredients());
                cost.removeIf(Helper::empty);
                List<String> parts = new ArrayList<>();
                int can = Math.min(times, r.getMaxUses() - r.getUses());
                for (ItemStack c : cost) {
                    parts.add(c.getAmount() + " " + word(c.getType()));
                    can = Math.min(can, count(pay, x -> x == c.getType()) / c.getAmount());
                }
                if (price == null) price = String.join(" and ", parts);
                if (can <= 0) continue;
                for (ItemStack c : cost) take(pay, x -> x == c.getType(), c.getAmount() * can);
                for (int i = 0; i < can; i++) hand(id, p, out.clone());
                r.setUses(r.getUses() + can);
                xp(id, can * (3 + ThreadLocalRandom.current().nextInt(4)));
                dirty.add(id);
                if (p != null) { H h = helpers.get(id); if (h != null) swing(p, h); }
                e.getWorld().playSound(e.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.8f, 1f);
                tell.accept("Deal! " + out.getAmount() * can + " " + word(out.getType()) + " for " + String.join(" and ", parts) + (can > 1 ? ", " + can + " times." : "."));
                return true;
            }
        }
        if (price == null) { if (!quiet) tell.accept("No villager close by sells " + term.toLowerCase(Locale.ROOT).replace('_', ' ') + "."); return false; }
        tell.accept("I can't pay for that, or the villager is sold out. It costs " + price + ".");
        return true;
    }

    private List<String> routineList(UUID id) { return new ArrayList<>(conf.getStringList(path(id, "routines"))); }

    /** Standing orders: at a time of day (when the world clock passes it) or every so many minutes. Each order in a
     *  routine is said to the helper as if the owner said it, fifteen seconds apart. */
    private void routines(Player p, H h) {
        List<String> rs = conf.getStringList(path(p.getUniqueId(), "routines"));
        long t = p.getWorld().getTime(), last = h.lastTime, now = System.currentTimeMillis();
        h.lastTime = t;
        for (String r : rs) {
            String[] a = r.split("\\|", 2);
            if (a.length < 2) continue;
            boolean due;
            Integer at = TIMES.get(a[0]);
            if (at != null) due = last >= 0 && last != t && (last < t ? last < at && at <= t : at > last || at <= t);
            else {
                long every = Long.parseLong(a[0].replaceAll("\\D", "")) * 60_000L;
                Long ranAt = h.ran.putIfAbsent(r, now);
                due = ranAt != null && now - ranAt >= every;
                if (due) h.ran.put(r, now);
            }
            if (!due) continue;
            String[] orders = a[1].split("\\s*(?:,|;|\\band\\b|\\bog\\b|\\bthen\\b|\\bså\\b)\\s*");
            for (int i = 0; i < orders.length; i++) {
                String order = orders[i].trim();
                if (order.isEmpty()) continue;
                Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline() && helpers.containsKey(p.getUniqueId())) talk(p, order); }, 1L + i * 300L);
            }
        }
    }

    /** A creeper within 32 blocks of someone's home: nothing drops, and the helper puts the blocks back. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBoom(EntityExplodeEvent e) {
        if (!(e.getEntity() instanceof Creeper) || e.blockList().isEmpty()) return;
        Location at = e.getLocation();
        List<UUID> owners = new ArrayList<>();
        for (String k : conf.getKeys(false)) try { UUID u = UUID.fromString(k); if (conf.getBoolean(path(u, "active"))) owners.add(u); } catch (IllegalArgumentException ignored) { }
        for (UUID id : owners) {
            Location hm = home(id);
            if (hm == null || hm.getWorld() != at.getWorld() || hm.distanceSquared(at) > 32 * 32) continue;
            List<Block> blocks = new ArrayList<>(e.blockList());
            blocks.sort(Comparator.comparingInt(Block::getY));
            Deque<Object[]> q = rubble.computeIfAbsent(id, k -> new ArrayDeque<>());
            for (Block b : blocks) q.add(new Object[] {b, b.getBlockData()});
            e.setYield(0);
            rubbleAt.put(id, System.currentTimeMillis() + 4000);
            Player p = Bukkit.getPlayer(id);
            if (p != null) say(p, "A creeper blew a hole near home! I will put it back.");
            else awayLog(id, "a creeper blew up " + blocks.size() + " blocks near home, putting them back");
            return;
        }
    }

    /** One block back every other tick, bottom first (everything at once when the server stops). */
    private void rebuild(boolean all) {
        long now = System.currentTimeMillis();
        for (UUID id : new ArrayList<>(rubble.keySet())) {
            Deque<Object[]> q = rubble.get(id);
            if (!all && now < rubbleAt.getOrDefault(id, 0L)) continue;
            do {
                Object[] r = q.poll();
                if (r == null) break;
                Block b = (Block) r[0];
                if (!(b.isEmpty() || b.isLiquid() || b.isReplaceable())) continue;
                b.setBlockData((BlockData) r[1], false);
                if (all) continue;
                b.getWorld().playSound(b.getLocation(), b.getBlockSoundGroup().getPlaceSound(), 0.5f, 1f);
                H h = helpers.get(id);
                if (h != null && !h.merged) h.m.swingMainHand();
                break;
            } while (true);
            if (q.isEmpty()) {
                rubble.remove(id);
                Player p = Bukkit.getPlayer(id);
                if (p != null && !all) say(p, "There, the hole is fixed.");
            }
        }
    }

    // --- Crafting and smelting ---------------------------------------------------------------------------------

    private static List<RecipeChoice> ingredients(Recipe r) {
        List<RecipeChoice> need = new ArrayList<>();
        if (r instanceof ShapelessRecipe sl) need.addAll(sl.getChoiceList());
        else if (r instanceof ShapedRecipe sh)
            for (String row : sh.getShape()) for (char c : row.toCharArray()) {
                RecipeChoice ch = sh.getChoiceMap().get(c);
                if (ch != null) need.add(ch);
            }
        need.removeIf(java.util.Objects::isNull);
        return need;
    }

    private static String describe(List<RecipeChoice> need) {
        Map<String, Integer> n = new java.util.LinkedHashMap<>();
        for (RecipeChoice c : need) {
            String name = c instanceof RecipeChoice.MaterialChoice mc && !mc.getChoices().isEmpty() ? mc.getChoices().get(0).name()
                    : c instanceof RecipeChoice.ExactChoice ec && !ec.getChoices().isEmpty() ? ec.getChoices().get(0).getType().name() : "something";
            n.merge(name.toLowerCase(Locale.ROOT).replace('_', ' '), 1, Integer::sum);
        }
        List<String> parts = new ArrayList<>();
        n.forEach((k, v) -> parts.add(v + " " + k));
        return String.join(", ", parts);
    }

    /** "torch 16", "iron pickaxe": made from the bag, the chests here and at home and the owner's own inventory,
     *  one crafting step (it does not make the planks for the sticks). False when the word is not an item. */
    private boolean craft(Player p, H h, String what, boolean quiet) {
        return craft(p.getUniqueId(), p, h.w.getLocation(), what, quiet, t -> say(p, t));
    }

    /** The bed there is wool for (three of one colour). When there is too little, the sheep within 48 blocks are sheared. */
    private Material bedFor(UUID id, Player p, Location here) {
        List<Inventory> from = new ArrayList<>(List.of(bag(id)));
        if (p != null) from.add(p.getInventory());
        if (here != null) from.addAll(stores(here));
        from.addAll(stores(homeNow(id), HOME));
        Location c = here != null ? here : p != null ? p.getLocation() : homeNow(id);
        List<Sheep> flock = new ArrayList<>();
        if (c != null) for (Entity e : c.getWorld().getNearbyEntities(c, 48, 24, 48)) if (e instanceof Sheep sh && sh.isAdult() && !sh.isSheared()) flock.add(sh);
        for (int round = 0; ; round++) {
            for (Material w : Material.values()) {
                if (!Tag.WOOL.isTagged(w) || count(from, k -> k == w) < 3) continue;
                Material bed = mat(w.name().replace("_WOOL", "_BED"));
                if (bed != null) return bed;
            }
            if (round >= flock.size()) return null;
            Sheep sh = flock.get(round);
            sh.setSheared(true);
            org.bukkit.DyeColor col = sh.getColor();
            Material wool = mat((col == null ? "WHITE" : col.name()) + "_WOOL");
            bag(id).addItem(new ItemStack(wool == null ? Material.WHITE_WOOL : wool, 1 + ThreadLocalRandom.current().nextInt(3)));
            dirty.add(id);
            sh.getWorld().playSound(sh.getLocation(), Sound.ENTITY_SHEEP_SHEAR, 1f, 1f);
        }
    }

    /** {@code p} is null when the owner is logged out: then from the bag and the chests at home, and into them. */
    private boolean craft(UUID id, Player p, Location here, String what, boolean quiet, Consumer<String> tell) {
        int amount = 1;
        Matcher num = NUMBER.matcher(what);
        if (num.find()) { amount = Math.max(1, Math.min(2304, Integer.parseInt(num.group(1)))); what = what.substring(0, num.start()) + what.substring(num.end()); }
        String term = ARTICLE.matcher(what.trim().toLowerCase(Locale.ROOT)).replaceFirst("").trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
        if (term.equals("BED") || term.equals("SENG")) {                   // any colour: the wool there is, or what the sheep close by give
            Material bed = bedFor(id, p, here);
            if (bed == null && p != null && woolTried.add(id)) {           // no wool, no sheep close by: she goes and finds some
                woolTrip(p, () -> { try { craft(id, p, here, "bed", quiet, tell); } finally { woolTried.remove(id); } });
                return true;
            }
            if (bed == null) { if (!quiet) tell.accept("A bed takes three wool of one colour and three planks, and I could not get three wool of one colour."); return false; }
            term = bed.name();
        }
        Material m = term.isEmpty() ? null : Material.matchMaterial(term);
        if (m == null && term.endsWith("S")) m = Material.matchMaterial(term.substring(0, term.length() - 1));
        if (m == null && term.endsWith("ES")) m = Material.matchMaterial(term.substring(0, term.length() - 2));
        if (m == null || !m.isItem()) { if (!quiet) tell.accept("I don't know what " + what.trim() + " is."); return false; }
        String word = m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        List<Inventory> from = new ArrayList<>(List.of(bag(id)));
        if (p != null) from.add(p.getInventory());
        if (here != null) from.addAll(stores(here));
        from.addAll(stores(homeNow(id), HOME));
        String needs = null;
        for (Recipe r : Bukkit.getRecipesFor(new ItemStack(m))) {
            List<RecipeChoice> need = ingredients(r);
            if (need.isEmpty()) continue;
            if (needs == null) needs = describe(need);
            int per = r.getResult().getAmount(), times = (amount + per - 1) / per, made = 0;
            ItemStack[][] have = new ItemStack[from.size()][];
            int[][] left = new int[from.size()][];
            for (int i = 0; i < from.size(); i++) {
                have[i] = from.get(i).getStorageContents();
                left[i] = new int[have[i].length];
                for (int j = 0; j < have[i].length; j++) left[i][j] = empty(have[i][j]) ? 0 : have[i][j].getAmount();
            }
            rounds:
            for (; made < times; made++) {
                int[][] trial = new int[left.length][];
                for (int i = 0; i < left.length; i++) trial[i] = left[i].clone();
                for (RecipeChoice c : need) {
                    boolean got = false;
                    find:
                    for (int i = 0; i < have.length; i++) for (int j = 0; j < have[i].length; j++)
                        if (trial[i][j] > 0 && c.test(have[i][j])) { trial[i][j]--; got = true; break find; }
                    if (!got) break rounds;
                }
                left = trial;
            }
            if (made == 0) continue;
            for (int i = 0; i < have.length; i++) for (int j = 0; j < have[i].length; j++) {
                if (empty(have[i][j]) || left[i][j] == have[i][j].getAmount()) continue;
                ItemStack it = have[i][j];
                it.setAmount(left[i][j]);
                from.get(i).setItem(j, left[i][j] > 0 ? it : null);
            }
            ItemStack out = r.getResult();
            int total = made * per;
            for (int n = total; n > 0; n -= out.getMaxStackSize()) { ItemStack g = out.clone(); g.setAmount(Math.min(n, out.getMaxStackSize())); hand(id, p, g); }
            dirty.add(id);
            if (p != null) { H h = helpers.get(id); if (h != null) swing(p, h); p.playSound(p.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 0.6f, 1.2f); }
            tell.accept("I made " + total + " " + word + "." + (made < times ? " That is all I had enough for." : ""));
            return true;
        }
        tell.accept(needs == null ? "I don't know how to make " + word + "." : "I don't have what it takes to make " + word + ". One needs " + needs + ".");
        return true;
    }

    private static List<FurnaceInventory> furnaces(Location around) { return furnaces(around, 8); }

    private static List<FurnaceInventory> furnaces(Location around, int r) {
        List<FurnaceInventory> out = new ArrayList<>();
        if (around == null) return out;
        World w = around.getWorld();
        int bx = around.getBlockX(), bz = around.getBlockZ();
        for (int cx = (bx - r) >> 4; cx <= (bx + r) >> 4; cx++) for (int cz = (bz - r) >> 4; cz <= (bz + r) >> 4; cz++)
            for (BlockState st : w.getChunkAt(cx, cz).getTileEntities(b -> b.getLocation().distanceSquared(around) <= (r + 0.5) * (r + 0.5), false))
                if (st instanceof Furnace f) out.add(f.getInventory());
        return out;
    }

    /** Takes what is done out of the furnaces (here, or else at home), and loads the empty ones from the bag: ores
     *  and raw food, or only the item named. Fuel from the bag too, but only real fuel, never planks or tools. */
    private void smelt(Player p, H h, String only) {
        UUID id = p.getUniqueId();
        List<FurnaceInventory> fs = furnaces(h.w.getLocation());
        if (fs.isEmpty()) fs = furnaces(home(id), HOME);
        if (fs.isEmpty()) { say(p, "There is no furnace here or at home."); return; }
        String term = slug(only).toUpperCase(Locale.ROOT);
        Inventory bag = bag(id);
        int took = 0, put = 0;
        boolean noFuel = false;
        for (FurnaceInventory f : fs) {
            ItemStack done = f.getResult();
            if (!empty(done)) {
                int before = done.getAmount();
                Map<Integer, ItemStack> rest = bag.addItem(done.clone());
                int left = rest.isEmpty() ? 0 : rest.values().iterator().next().getAmount();
                done.setAmount(left);
                f.setResult(left > 0 ? done : null);
                took += before - left;
            }
            if (empty(f.getSmelting()))
                for (int i = 0; i < bag.getSize(); i++) {
                    ItemStack it = bag.getItem(i);
                    if (empty(it) || !f.canSmelt(it)) continue;
                    String n = it.getType().name();
                    if (term.isEmpty() ? !(n.startsWith("RAW_") || n.endsWith("_ORE") || n.equals("ANCIENT_DEBRIS") || it.getType().isEdible()) : !n.contains(term)) continue;
                    f.setSmelting(it.clone());
                    bag.setItem(i, null);
                    put += it.getAmount();
                    break;
                }
            if (!empty(f.getSmelting()) && empty(f.getFuel())) {
                noFuel = true;
                for (int i = 0; i < bag.getSize(); i++) {
                    ItemStack it = bag.getItem(i);
                    if (empty(it) || !FUELS.contains(it.getType())) continue;
                    f.setFuel(it.clone());
                    bag.setItem(i, null);
                    noFuel = false;
                    break;
                }
            }
        }
        dirty.add(id);
        zip(p, h);
        say(p, (took + put == 0 ? "Nothing was done yet, and I have nothing to put in." : "I took out " + took + " and put in " + put + ".")
                + (noFuel ? " A furnace has no fuel: give me coal, charcoal, blaze rods or a lava bucket." : ""));
    }

    // --- Healing, a carried light, bridges and steps, riding, finding things ---------------------------------

    /** Regeneration for the owner, or the player it looks after, when they are down to four hearts and it is close. */
    private void heal(Player p, H h, Player who) {
        long now = System.currentTimeMillis();
        if (who.isDead() || who.getHealth() > 8 || dist2(h.w, who.getLocation()) > 16 * 16 || now - h.healed < 30_000) return;
        h.healed = now;
        who.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 2));
        who.spawnParticle(Particle.HEART, who.getLocation().add(0, 1.2, 0), 6, 0.4, 0.4, 0.4);
        swing(p, h);
        if (who != p) { who.sendMessage(Component.text("<" + name(p.getUniqueId()) + "> You are hurt! Let me help.")); return; }
        remark(p, h, "heal", 0, "Hold still, I am patching you up!", "You are hurt! Let me help.", "Here, this will make you feel better.");
    }

    private void clearLight(H h) {
        if (h.light != null && h.light.getType() == Material.LIGHT) h.light.setType(Material.AIR);
        h.light = null;
    }

    /** An invisible light block that moves with the helper's head. */
    private void light(UUID id, H h) {
        Block want = null;
        if (flag(id, "light", false) || h.merged) {                      // merged, the owner shines
            Block feet = h.w.getLocation().getBlock(), head = feet.getRelative(BlockFace.UP);
            want = head.isEmpty() || head.equals(h.light) ? head : feet.isEmpty() || feet.equals(h.light) ? feet : null;
        }
        if (want == null ? h.light == null : want.equals(h.light)) return;
        clearLight(h);
        if (want != null) { want.setType(Material.LIGHT); h.light = want; }
    }

    /** A block from the bag (cobblestone when it has none) where the owner is about to step: under the next step
     *  when walking straight ahead, as a step up when looking up. Not when looking down, so you can still climb down. */
    private void bridge(Player p, H h) {
        if (p.isFlying() || p.isGliding() || p.isInsideVehicle() || p.isSwimming() || p.isDead() || dist2(h.w, p.getLocation()) > 24 * 24) return;
        Location l = p.getLocation();
        if (l.getPitch() > 40 || !l.clone().subtract(0, 0.1, 0).getBlock().getType().isSolid()) return;    // only with ground under you
        Vector d = l.getDirection().setY(0);
        if (d.lengthSquared() < 0.01) return;
        Block feet = l.getBlock(), ahead = l.clone().add(d.normalize()).getBlock();
        if (ahead.equals(feet)) return;
        Block spot = l.getPitch() < -30 ? ahead : ahead.getRelative(BlockFace.DOWN);
        if (!(spot.isEmpty() || spot.isLiquid())) return;
        if (l.getPitch() < -30 && !(spot.getRelative(BlockFace.UP).isPassable() && spot.getRelative(0, 2, 0).isPassable())) return;
        Material m = Material.COBBLESTONE;
        Inventory bag = bag(p.getUniqueId());
        for (int i = 0; i < bag.getSize(); i++) {
            ItemStack it = bag.getItem(i);
            if (empty(it) || !it.getType().isBlock() || !it.getType().isOccluding() || it.getType().hasGravity()) continue;
            m = it.getType();
            it.setAmount(it.getAmount() - 1);
            bag.setItem(i, it.getAmount() > 0 ? it : null);
            dirty.add(p.getUniqueId());
            break;
        }
        spot.setType(m);
        spot.getWorld().playSound(spot.getLocation(), spot.getBlockSoundGroup().getPlaceSound(), 0.7f, 1f);
        swing(p, h);
    }

    private void startRide(Player p, H h) {
        release(h);
        h.w.teleport(p.getLocation());
        h.m.teleport(p.getLocation());
        h.w.setAware(false);                                   // no wandering off: the rider steers
        set(h.w, Attribute.STEP_HEIGHT, 1.1);
        h.riding = true;
        if (!h.m.addPassenger(p)) { stopRide(h); say(p, "I can't lift you right now."); return; }
        bar(p, "Movement keys steer, jump jumps, sprint runs, sneak gets you off");
    }

    private void stopRide(H h) {
        if (!h.riding) return;
        h.riding = false;
        h.w.setAware(true);
        set(h.w, Attribute.STEP_HEIGHT, 0.6);
        h.m.eject();
    }

    private void steer(Player p, H h) {
        Input in = p.getCurrentInput();
        double yaw = Math.toRadians(p.getYaw());
        double f = (in.isForward() ? 1 : 0) - (in.isBackward() ? 1 : 0), side = (in.isLeft() ? 1 : 0) - (in.isRight() ? 1 : 0);
        Vector v = new Vector(-Math.sin(yaw) * f + Math.cos(yaw) * side, 0, Math.cos(yaw) * f + Math.sin(yaw) * side);
        if (v.lengthSquared() > 0) v.normalize().multiply(in.isSprint() ? 0.8 : 0.45);
        v.setY(in.isJump() && h.w.isOnGround() ? 0.55 : h.w.getVelocity().getY());
        h.w.setVelocity(v);
        h.w.setRotation(p.getYaw(), 0);
    }

    private static String direction(Location from, Location to) {
        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        String[] names = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};
        return names[(int) Math.floor(((Math.toDegrees(Math.atan2(-dx, dz)) + 360 + 22.5) % 360) / 45)];
    }

    /** A line of sparks from the owner towards a place, a few times, so they see which way to go. */
    private void point(Player p, Location to) {
        for (int k = 0; k < 6; k++)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline() || p.getWorld() != to.getWorld()) return;
                Location from = p.getEyeLocation().subtract(0, 0.4, 0);
                Vector d = to.toVector().subtract(from.toVector());
                double len = Math.min(d.length(), 14);
                if (len < 0.5) return;
                d.normalize();
                for (double t = 1; t <= len; t += 0.5) p.spawnParticle(Particle.END_ROD, from.clone().add(d.clone().multiply(t)), 1, 0, 0, 0, 0);
            }, k * 15L);
    }

    /** The nearest structure with that word in its name (village, stronghold, mansion …), or else the nearest block
     *  of that kind within 20 blocks (ores first). False when the word means nothing to it. */
    private boolean find(Player p, String what, boolean quiet) {
        String term = what.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
        if (term.isEmpty()) { if (!quiet) p.sendMessage("Usage: /helper find <ore, block or structure>"); return false; }
        UUID fid = p.getUniqueId();
        if (term.matches("nothing|nothing_special|anything|off|stop|none|ingenting|alt")) {      // back to choosing from the stock
            conf.set(path(fid, "seek"), null);
            seekNow.remove(fid);
            save();
            say(p, "Fine, while exploring I dig for what we have least of again: diamond, gold, iron, coal, lapis, redstone, in that order.");
            return true;
        }
        String spec = seekSpec(what);
        if (spec != null) {
            conf.set(path(fid, "seek"), spec);
            save();
            awayLog(fid, "looks for " + seekWord(spec) + " first while exploring (asked for)");
            say(p, "While I explore I look for " + seekWord(spec) + " first, however much we have. Say find nothing to let me choose again.");
        }
        if (term.endsWith("s") && term.length() > 3) term = term.substring(0, term.length() - 1);
        if (term.equals("temple")) term = "pyramid";
        Location here = p.getLocation();
        String word = term.replace('_', ' ');
        Registry<Structure> reg = RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE);
        Location best = null;
        boolean known = false;
        int tried = 0;
        for (Structure st : reg) {
            NamespacedKey k = reg.getKey(st);
            if (k == null || !k.getKey().contains(term) || tried++ >= 6) continue;
            known = true;
            StructureSearchResult r = p.getWorld().locateNearestStructure(here, st, 64, false);
            if (r == null) continue;
            Location l = r.getLocation();
            l.setY(here.getY());
            if (best == null || l.distanceSquared(here) < best.distanceSquared(here)) best = l;
        }
        if (known) {
            if (best == null) { say(p, "There is no " + word + " anywhere near here in this world."); return true; }
            say(p, "The nearest " + word + " is " + (int) best.distance(here) + " blocks to the " + direction(here, best)
                    + ", at x " + best.getBlockX() + ", z " + best.getBlockZ() + ".");
            point(p, best);
            return true;
        }
        EntityType mob = mobFor(term);
        if (mob != null) {
            String mobName = mob.name().toLowerCase(Locale.ROOT).replace('_', ' ');
            seekMob(p, mob, e -> true, e -> {
                if (e == null) { say(p, "I find no " + mobName + " within " + ORE_RANGE + " blocks."); return; }
                Location l = e.getLocation(), now = p.getLocation();
                say(p, "The nearest " + mobName + " is " + (int) l.distance(now) + " blocks to the " + direction(now, l) + ", at " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ() + ". Follow the sparks!");
                point(p, l);
            });
            return true;
        }
        String up = term.toUpperCase(Locale.ROOT);
        Set<Material> kinds = new HashSet<>();
        for (Material m : Material.values()) if (!m.isLegacy() && m.isBlock() && !m.isAir() && m.name().contains(up)) kinds.add(m);
        if (kinds.stream().anyMatch(m -> m.name().endsWith("_ORE"))) kinds.removeIf(m -> !m.name().endsWith("_ORE"));
        if (kinds.isEmpty()) { if (!quiet) say(p, "I don't know what " + word + " is."); return false; }
        Block c = here.getBlock(), hit = null;
        int bd = Integer.MAX_VALUE, lo = p.getWorld().getMinHeight(), hi = p.getWorld().getMaxHeight();
        for (int x = -20; x <= 20; x++) for (int z = -20; z <= 20; z++) for (int y = Math.max(lo, c.getY() - 20); y <= Math.min(hi - 1, c.getY() + 20); y++) {
            int d = x * x + z * z + (y - c.getY()) * (y - c.getY());
            if (d >= bd) continue;
            Block b = c.getWorld().getBlockAt(c.getX() + x, y, c.getZ() + z);
            if (kinds.contains(b.getType())) { bd = d; hit = b; }
        }
        if (hit == null) { seekBlock(p, kinds, word, b -> point(p, b.getLocation().add(0.5, 0.5, 0.5))); return true; }
        Location l = hit.getLocation().add(0.5, 0.5, 0.5);
        int dy = hit.getY() - c.getY();
        say(p, "There is " + hit.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ') + " " + (int) Math.sqrt(bd) + " blocks away, to the "
                + direction(here, l) + (dy == 0 ? "" : dy > 0 ? ", " + dy + " up" : ", " + -dy + " down") + ". Follow the sparks!");
        point(p, l);
        return true;
    }

    /** While farming: one sheep sheared, one cow milked (with a bucket from the bag) or one animal fed (with food
     *  from the bag) every other second. No feeding when there are already twelve of that animal around. */
    private void animals(Player p, H h) {
        Inventory bag = bag(p.getUniqueId());
        Collection<Entity> around = h.anchor.getWorld().getNearbyEntities(h.anchor, FARM_RANGE, 4, FARM_RANGE);
        for (Entity e : around) {
            if (!(e instanceof Animals a) || !a.isAdult() || ownerOf(e) != null) continue;
            if (a instanceof Sheep sh && sh.readyToBeSheared()) { sh.shear(); swing(p, h); return; }
            if (a instanceof Cow && bag.contains(Material.BUCKET) && System.currentTimeMillis() - h.said.getOrDefault("milk", 0L) > 60_000) {
                h.said.put("milk", System.currentTimeMillis());
                bag.removeItem(new ItemStack(Material.BUCKET));
                keep(p, h, List.of(new ItemStack(Material.MILK_BUCKET)));
                e.getWorld().playSound(e.getLocation(), Sound.ENTITY_COW_MILK, 0.8f, 1f);
                swing(p, h);
                return;
            }
            if (!a.canBreed() || a.isLoveMode() || around.stream().filter(o -> o.getType() == a.getType()).count() >= 12) continue;
            for (int i = 0; i < bag.getSize(); i++) {
                ItemStack it = bag.getItem(i);
                if (empty(it) || !a.isBreedItem(it)) continue;
                it.setAmount(it.getAmount() - 1);
                bag.setItem(i, it.getAmount() > 0 ? it : null);
                dirty.add(p.getUniqueId());
                a.setLoveModeTicks(600);
                a.setBreedCause(p.getUniqueId());
                e.getWorld().spawnParticle(Particle.HEART, e.getLocation().add(0, e.getHeight(), 0), 5, 0.3, 0.3, 0.3);
                swing(p, h);
                return;
            }
        }
    }

    // --- Merged: the helper is inside its owner -------------------------------------------------------------

    private boolean merged(Entity e) {
        H h = helpers.get(e.getUniqueId());
        return h != null && h.merged;
    }

    private double size(UUID id) { return conf.getDouble(path(id, "size"), 1.0); }

    /** How many size steps (of 1.5 times) it is away from normal, bigger or smaller: 0 to about 3.4. */
    private double power(UUID id) { return Math.abs(Math.log(size(id)) / Math.log(1.5)); }

    /** Bigger or smaller, it hits harder (half as much again per step) and moves faster (15 % per step). */
    private double damage(UUID id) { return DAMAGE * (1 + 0.5 * power(id)); }

    private double speed(UUID id) { return 1 + 0.15 * power(id); }

    /** At the smallest size monsters do not notice it. */
    private boolean tiny(UUID id) { return size(id) <= 0.26; }

    /** The owner's own speed while merged follows the helper's size too. */
    private void speedBoost(Player p, boolean on) {
        var a = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (a == null) return;
        a.removeModifier(speedKey);
        double f = speed(p.getUniqueId()) - 1;
        if (on && f > 0.01) a.addModifier(new AttributeModifier(speedKey, f, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }

    /** What is hunting the helper (or its merged owner) loses track of it. */
    private void forget(Player p, H h) {
        for (Entity e : p.getNearbyEntities(40, 20, 40))
            if (e instanceof Mob m && ownerOf(m) == null && m.getTarget() != null && (m.getTarget() == h.w || m.getTarget() == h.m || (h.merged && m.getTarget() == p)))
                m.setTarget(null);
    }

    /** The size spell: the figure, and the owner too while they are merged. */
    private static final double MIN_SIZE = 0.1, MAX_SIZE = 6.0;

    /** Merged: a shining being, with light that drifts around the body (no outline), and the helper's own looks. */
    private void shine(Player p, boolean on) {
        org.bukkit.scoreboard.Team t = Bukkit.getScoreboardManager().getMainScoreboard().getTeam("helper_shine");
        if (t != null && t.hasEntry(p.getName())) t.removeEntry(p.getName());
        p.setGlowing(false);
        if (on) wearSkin(p, true);
    }

    /** The owner takes the helper's skin (given back by ownBody). A skin from the pack must be signed first: the chat
     *  bridge does that through mineskin.org when the server has a key, and answers with "mergeskin". */
    private void wearSkin(Player p, boolean ask) {
        UUID id = p.getUniqueId();
        String skin = conf.getString(path(id, "skin"));
        if (skin == null || !skin.startsWith("pack:")) return;
        String[] tex = skins.signed(skin.substring(5));
        if (tex == null) { if (ask) plugin.getLogger().info("HELPER_SIGN " + p.getName() + "|" + skin.substring(5)); return; }
        com.destroystokyo.paper.profile.PlayerProfile prof = p.getPlayerProfile();
        if (!conf.contains(path(id, "ownskin.value")))
            for (com.destroystokyo.paper.profile.ProfileProperty pp : prof.getProperties()) if (pp.getName().equals("textures")) {
                conf.set(path(id, "ownskin.value"), pp.getValue());
                conf.set(path(id, "ownskin.signature"), pp.getSignature());
                save();
            }
        prof.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", tex[0], tex[1]));
        p.setPlayerProfile(prof);
    }

    private void resize(Player p, H h, double v, boolean show) {
        UUID id = p.getUniqueId();
        v = Math.max(MIN_SIZE, Math.min(MAX_SIZE, v));
        if (Math.abs(v - 1.0) < 0.1) v = 1.0;
        conf.set(path(id, "size"), v);
        save();
        h.fit = v;
        set(h.m, Attribute.SCALE, v);
        set(h.w, Attribute.MOVEMENT_SPEED, 0.38 * speed(id));
        if (h.merged) { set(p, Attribute.SCALE, v); speedBoost(p, true); }
        if (tiny(id)) forget(p, h);
        if (!show) return;
        Entity who = h.merged ? p : h.m;
        p.spawnParticle(Particle.WITCH, who.getLocation().add(0, who.getHeight() / 2, 0), 30, 0.4, 0.6, 0.4, 0);
        bar(p, String.format(Locale.ROOT, "Size: %.2f× · hits for %.0f · speed +%.0f %%%s", v, damage(id), (speed(id) - 1) * 100,
                tiny(id) ? " · monsters don't notice " + (h.merged ? "you" : "it") : ""));
    }

    /** The owner gets their own size back and stops glowing (after a split, a logout or a crash while merged). */
    private void ownBody(Player p) {
        String k = path(p.getUniqueId(), "ownsize");
        if (!conf.contains(k)) return;
        set(p, Attribute.SCALE, conf.getDouble(k, 1.0));
        speedBoost(p, false);
        shine(p, false);
        String ov = conf.getString(path(p.getUniqueId(), "ownskin.value")), os = conf.getString(path(p.getUniqueId(), "ownskin.signature"));
        conf.set(path(p.getUniqueId(), "ownskin"), null);
        if (ov != null && p.isOnline()) {
            com.destroystokyo.paper.profile.PlayerProfile prof = p.getPlayerProfile();
            prof.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", ov, os));
            p.setPlayerProfile(prof);
        }
        p.setFlySpeed(0.1f);
        boolean own = conf.getBoolean(path(p.getUniqueId(), "ownflight"));     // had flight already (fly power)
        conf.set(path(p.getUniqueId(), "ownflight"), null);
        if (!own && p.getGameMode() != org.bukkit.GameMode.CREATIVE && p.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
            if (p.isFlying()) p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 300, 0));   // a soft landing
            p.setFlying(false);
            p.setAllowFlight(false);
        }
        conf.set(k, null);
        save();
    }

    private void swing(Player p, H h) {
        if (h.merged) p.swingMainHand();
        else h.m.swingMainHand();
    }

    /** Walks towards a place: the wolf finds its own way, a merged owner is pushed there tick by tick (autopilot). */
    private void go(H h, Location to, double speed) {
        if (h.merged) h.goal = to;
        else h.w.getPathfinder().moveTo(to, speed);
    }

    private void merge(Player p, H h) {
        if (h.merged) { say(p, "We already are one."); return; }
        release(h);
        h.merged = true;
        h.goal = null;
        h.w.setAware(false);
        h.w.setTarget(null);
        h.m.setVisibleByDefault(false);
        p.hideEntity(plugin, h.w);
        p.hideEntity(plugin, h.m);
        var own = p.getAttribute(Attribute.SCALE);
        conf.set(path(p.getUniqueId(), "ownsize"), own == null ? 1.0 : own.getBaseValue());
        conf.set(path(p.getUniqueId(), "ownflight"), p.getAllowFlight());
        save();
        set(p, Attribute.SCALE, size(p.getUniqueId()));                // the owner takes the helper's size and speed, and shines
        speedBoost(p, true);
        if (tiny(p.getUniqueId())) forget(p, h);
        shine(p, true);
        Location l = p.getLocation().add(0, 1, 0);
        p.spawnParticle(Particle.TOTEM_OF_UNDYING, l, 60, 0.4, 0.8, 0.4, 0.3);
        p.playSound(l, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.4f);
        say(p, "We are one now. Nothing can hurt you, and you hit as hard as I do. You can fly: double-tap jump. Tell me what to do and your body does it; move or swing and you are in control again. Say split to let me out.");
    }

    private void split(Player p, H h) {
        if (!h.merged) { say(p, "We are not merged."); return; }
        release(h);
        h.merged = false;
        h.goal = null;
        if (h.gliding) { h.gliding = false; p.setGliding(false); }
        ownBody(p);
        h.w.setAware(true);
        h.w.teleport(p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(-1.5)));
        h.m.setVisibleByDefault(flag(p.getUniqueId(), "show", false));
        p.showEntity(plugin, h.w);
        p.showEntity(plugin, h.m);
        Location l = h.w.getLocation().add(0, 1, 0);
        p.spawnParticle(Particle.CLOUD, l, 25, 0.3, 0.6, 0.3, 0.03);
        p.playSound(l, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.4f);
        say(p, "I am out again.");
    }

    /** The owner takes over from the autopilot. */
    private void control(Player p, H h) {
        h.job = Job.NONE;
        h.queue = new ArrayDeque<>();
        h.goal = null;
        bar(p, "You are in control again");
    }

    /** Every tick while merged: the hidden wolf stays on the owner (everything else measures from it), a movement
     *  key ends the job, and otherwise the owner is walked towards where the job needs them. */
    private void autopilot(Player p, H h) {
        Location at = p.getLocation();
        if (h.w.getWorld() != at.getWorld() || h.w.getLocation().distanceSquared(at) > 0.01) h.w.teleport(at);
        if (!p.getAllowFlight()) p.setAllowFlight(true);                   // merged you fly (the fly power turns it off on world changes)
        float fly = (float) Math.min(0.3, 0.1 * speed(p.getUniqueId()));
        if (Math.abs(p.getFlySpeed() - fly) > 0.001) p.setFlySpeed(fly);
        Input keys = p.getCurrentInput();                                  // sprint forward in the air: glide where you look
        if (!h.gliding && p.isFlying() && keys.isSprint() && keys.isForward()) {
            h.gliding = true;
            p.setFlying(false);
        }
        if (h.gliding) {
            if (!keys.isSprint() || p.isOnGround() || p.isInWater()) {
                h.gliding = false;
                p.setGliding(false);
                if (!p.isOnGround()) p.setFlying(true);                    // back to hovering
            } else {
                if (!p.isGliding()) p.setGliding(true);
                p.setVelocity(at.getDirection().multiply(1.1 * speed(p.getUniqueId())));
            }
        }
        if (h.job == Job.NONE) return;
        Input in = p.getCurrentInput();
        if (in.isForward() || in.isBackward() || in.isLeft() || in.isRight()) { control(p, h); return; }
        if (h.goal == null || h.goal.getWorld() != at.getWorld()) return;
        Vector d = h.goal.toVector().subtract(at.toVector()).setY(0);
        if (d.lengthSquared() < 1) return;
        boolean moved = h.last == null || h.last.getWorld() != at.getWorld() || h.last.distanceSquared(at) > 0.0004;
        h.still = moved ? 0 : h.still + 1;
        h.last = at;
        d.normalize().multiply(0.24);
        d.setY(h.still > 3 && p.isOnGround() ? 0.42 : p.getVelocity().getY());     // something in the way: jump
        p.setVelocity(d);
    }

    /** Merged, monsters within four blocks are struck twice a second (the helper fighting through its owner). */
    private void aura(Player p, H h) {
        if (!h.defend || flag(p.getUniqueId(), "passive", false) || p.isDead()) return;     // merged, the owner fights for themself unless they say defend
        for (Entity e : p.getNearbyEntities(4, 3, 4))
            if (foe(e) && e instanceof LivingEntity le) {
                le.damage(damage(p.getUniqueId()), p);
                p.swingMainHand();
                return;
            }
    }

    /** Without an elytra the server would end the glide at once. */
    @EventHandler
    public void onGlide(EntityToggleGlideEvent e) {
        H h = helpers.get(e.getEntity().getUniqueId());
        if (h != null && h.merged && h.gliding && !e.isGliding()) e.setCancelled(true);
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent e) {
        H h = helpers.get(e.getPlayer().getUniqueId());
        if (h != null && h.merged && h.job != Job.NONE) control(e.getPlayer(), h);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && merged(p) && e.getFoodLevel() < p.getFoodLevel()) e.setCancelled(true);
    }

    // --- Gear: what it wears and holds --------------------------------------------------------------------

    private ItemStack[] gear(UUID id) {
        return gear.computeIfAbsent(id, k -> {
            ItemStack[] g = new ItemStack[GEAR.length];
            ItemStack[] saved = load(id, "gear");
            if (saved != null) for (int i = 0; i < Math.min(saved.length, g.length); i++) g[i] = saved[i];
            return g;
        });
    }

    private static boolean empty(ItemStack i) { return i == null || i.isEmpty(); }

    /** Shows the gear on the figure. With nothing in its hand it holds a netherite sword (for show, not to take).
     *  Waiting, its hands are empty (that is how you see it waits); on a job it holds the tool for the job. */
    private String shownKey(UUID id, H h) { return h.stance + ":" + h.job + ":" + style(id) + ":" + h.tool; }

    /** What it holds for the next three seconds: the sword in a fight, the axe at a tree, the shovel in the dirt. */
    private void wield(H h, Material m) { h.tool = m; h.toolTick = tick; }

    private static Material toolFor(Material m) {
        if (m.isAir() || Tag.LEAVES.isTagged(m)) return null;
        String n = m.name();
        if (Tag.LOGS.isTagged(m) || Tag.PLANKS.isTagged(m) || n.contains("WOOD") || n.endsWith("_STEM") || m == Material.PUMPKIN || m == Material.MELON || m == Material.BAMBOO) return Material.NETHERITE_AXE;
        if (m == Material.GRASS_BLOCK || Tag.DIRT.isTagged(m) || Tag.SAND.isTagged(m) || m == Material.GRAVEL || m == Material.CLAY || m == Material.PODZOL || m == Material.MYCELIUM
                || m == Material.MUD || Tag.SNOW.isTagged(m) || m == Material.SNOW_BLOCK) return Material.NETHERITE_SHOVEL;
        return m.isSolid() && m.getHardness() >= 0.8f ? Material.NETHERITE_PICKAXE : null;
    }

    private void dress(UUID id, H h) {
        ItemStack[] g = gear(id);
        String style = style(id);
        h.shown = shownKey(id, h);
        Material tool = h.tool != null ? h.tool : switch (h.job) {
            case MINE -> Material.NETHERITE_PICKAXE;
            case FARM -> Material.NETHERITE_HOE;
            case FISH -> Material.FISHING_ROD;
            case NONE -> null;
        };
        for (int i = 0; i < GEAR.length; i++) {
            boolean hand = GEAR[i] == EquipmentSlot.HAND, hands = hand || GEAR[i] == EquipmentSlot.OFF_HAND;
            ItemStack it = hands && h.stance == Stance.STAY ? null
                    : hand && tool == Material.NETHERITE_SWORD ? (style.equals("bow") ? new ItemStack(Material.BOW) : !empty(g[i]) ? g[i] : ench(tool))   // a fight: its own weapon
                    : hand ? (tool == null ? null : tool.getMaxDurability() > 0 ? ench(tool) : new ItemStack(tool))                       // the tool for what it does, or else empty hands
                    : hands ? (!empty(g[i]) && g[i].getType() != Material.SHIELD ? g[i] : null)   // no shield
                    : !empty(g[i]) ? g[i] : null;
            h.m.getEquipment().setItem(GEAR[i], it);
        }
    }

    /** Which slot the helper would put this in, or -1 when it is not something to wear or wield. */
    private static int gearSlot(ItemStack it) {
        if (empty(it)) return -1;
        EquipmentSlot s = it.getType().getEquipmentSlot();
        if (s == EquipmentSlot.HAND && !HAND_GEAR.matcher(it.getType().name()).matches()) return -1;
        for (int i = 0; i < GEAR.length; i++) if (GEAR[i] == s) return i;
        return -1;
    }

    private static void give(Player p, ItemStack it) {
        if (empty(it)) return;
        for (ItemStack rest : p.getInventory().addItem(it).values()) p.getWorld().dropItem(p.getLocation(), rest);
    }

    private void equip(Player p, H h, int slot) {
        ItemStack held = p.getInventory().getItemInMainHand();
        ItemStack one = held.clone();
        one.setAmount(1);
        held.setAmount(held.getAmount() - 1);
        p.getInventory().setItemInMainHand(held);
        ItemStack[] g = gear(p.getUniqueId());
        give(p, g[slot]);
        g[slot] = one;
        store(p.getUniqueId(), "gear", g);
        save();
        dress(p.getUniqueId(), h);
        p.playSound(h.w.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.8f, 1f);
        bar(p, "Your helper puts it on. /helper unequip gives it back");
    }

    // --- Jobs: mining a vein or tree, harvesting a field ----------------------------------------------------

    private static String vein(Material m) { return m.name().replace("DEEPSLATE_", ""); }    // ore and deepslate ore are one vein

    private static final Map<String, String> MINE_NB = Map.ofEntries(Map.entry("jord", "dirt"), Map.entry("grus", "gravel"), Map.entry("stein", "stone"), Map.entry("leire", "clay"),
            Map.entry("kull", "coal_ore"), Map.entry("jern", "iron_ore"), Map.entry("gull", "gold_ore"), Map.entry("kobber", "copper_ore"), Map.entry("diamant", "diamond_ore"),
            Map.entry("diamanter", "diamond_ore"), Map.entry("snø", "snow_block"), Map.entry("coal", "coal_ore"), Map.entry("iron", "iron_ore"), Map.entry("gold", "gold_ore"),
            Map.entry("copper", "copper_ore"), Map.entry("diamond", "diamond_ore"), Map.entry("diamonds", "diamond_ore"), Map.entry("redstone", "redstone_ore"), Map.entry("grass", "grass_block"));

    /** "sand", "red sand", "grus": the block that is meant, or null. */
    private static final Set<String> WOOD_WORDS = Set.of("wood", "tree", "trees", "a tree", "log", "logs", "timber", "ved", "tre", "trær", "et tre", "tømmer");

    private static Material mineKind(String what) {
        String w = what.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (w.isEmpty()) return null;
        w = MINE_NB.getOrDefault(w, w);
        for (String t : new String[] {w, w.endsWith("s") ? w.substring(0, w.length() - 1) : w, w + "_block"}) {
            Material m = Material.matchMaterial(t);
            if (m != null && m.isBlock() && !m.isAir()) return m;
        }
        return null;
    }

    private void startMine(Player p, H h) { startMine(p, h, ""); }

    /** Mine what its owner looks at, or, with a word ("mine sand"), the nearest of that kind and nothing else. */
    private void startMine(Player p, H h, String what) {
        Block t = p.getTargetBlockExact(6);
        boolean wood = WOOD_WORDS.contains(what.trim().toLowerCase(Locale.ROOT));
        Material want = wood ? null : mineKind(what);
        if (wood && (t == null || !Tag.LOGS.isTagged(t.getType()))) {       // any tree: the nearest log within 24 blocks
            t = null;
            double bd = Double.MAX_VALUE;
            Block c = p.getLocation().getBlock();
            for (int x = -24; x <= 24; x++) for (int y = -8; y <= 16; y++) for (int z = -24; z <= 24; z++) {
                Block b = c.getRelative(x, y, z);
                double d = x * x + y * y * 4 + z * z;
                if (d < bd && Tag.LOGS.isTagged(b.getType())) { bd = d; t = b; }
            }
            if (t == null) { say(p, "I see no trees within 24 blocks."); return; }
        }
        if (!what.isBlank() && !wood && want == null) { say(p, "I do not know what " + what.trim() + " is. Look at a block and say mine, or say mine sand, mine gravel, mine clay…"); return; }
        if (want != null && (t == null || !vein(t.getType()).equals(vein(want)))) {       // not looking at it: the nearest one
            t = null;
            double bd = Double.MAX_VALUE;
            Block c = p.getLocation().getBlock();
            for (int x = -16; x <= 16; x++) for (int y = -8; y <= 8; y++) for (int z = -16; z <= 16; z++) {
                Block b = c.getRelative(x, y, z);
                double d = x * x + y * y * 4 + z * z;
                if (d < bd && vein(b.getType()).equals(vein(want))) { bd = d; t = b; }
            }
            if (t == null) {                                               // none close by: as far as it takes
                Set<Material> same = new HashSet<>();
                for (Material k : Material.values()) if (!k.isLegacy() && k.isBlock() && vein(k).equals(vein(want))) same.add(k);
                seekBlock(p, same, word(want), b -> { if (helpers.get(p.getUniqueId()) == h) mineFrom(p, h, b); });
                return;
            }
        }
        mineFrom(p, h, t);
    }

    private final Set<UUID> oreSeek = new HashSet<>();
    private static final int ORE_RANGE = 1024, ORE_NEW = 400;     // how far it looks, and how many chunks of new land it may make on the way

    /** No ore close by: the land around is searched, nearest first and a little at a time; new land is made when needed. */
    private void seekBlock(Player p, Set<Material> kinds, String name, Consumer<Block> then) {
        UUID id = p.getUniqueId();
        if (!oreSeek.add(id)) { say(p, "I am still looking."); return; }
        say(p, "I see no " + name + " close by. I look further away. One moment.");
        int[] made = {0};
        World w = p.getWorld();
        Location from = p.getLocation();
        int px = from.getBlockX(), pz = from.getBlockZ();
        List<int[]> chunks = new ArrayList<>();
        for (int cx = (px - ORE_RANGE) >> 4; cx <= (px + ORE_RANGE) >> 4; cx++) for (int cz = (pz - ORE_RANGE) >> 4; cz <= (pz + ORE_RANGE) >> 4; cz++) chunks.add(new int[] {cx, cz});
        chunks.sort(Comparator.comparingDouble(c -> Math.hypot(c[0] * 16 + 8 - px, c[1] * 16 + 8 - pz)));
        int[] at = {0};
        Block[] best = {null};
        double[] bd = {Double.MAX_VALUE};
        org.bukkit.scheduler.BukkitTask[] task = new org.bukkit.scheduler.BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                for (int n = 0; n < 4 && at[0] < chunks.size(); n++) {
                    int[] c = chunks.get(at[0]++);
                    double near = Math.hypot(c[0] * 16 + 8 - px, c[1] * 16 + 8 - pz) - 12;
                    if (near > ORE_RANGE || near * near > bd[0]) { at[0] = chunks.size(); break; }      // nothing nearer can come now
                    if (!w.isChunkGenerated(c[0], c[1])) { if (made[0]++ >= ORE_NEW) continue; n = 4; }       // new land: one chunk a tick
                    org.bukkit.ChunkSnapshot snap = w.getChunkAt(c[0], c[1]).getChunkSnapshot();
                    for (int bx = 0; bx < 16; bx++) for (int bz = 0; bz < 16; bz++) {
                        int top = snap.getHighestBlockYAt(bx, bz);
                        for (int y = w.getMinHeight(); y <= top; y++) {
                            if (!kinds.contains(snap.getBlockType(bx, y, bz))) continue;
                            double dx = c[0] * 16 + bx - px, dy = y - from.getY(), dz = c[1] * 16 + bz - pz, d = dx * dx + dy * dy + dz * dz;
                            if (d < bd[0] && dx * dx + dz * dz <= ORE_RANGE * ORE_RANGE) { bd[0] = d; best[0] = w.getBlockAt(c[0] * 16 + bx, y, c[1] * 16 + bz); }
                        }
                    }
                }
                if (at[0] < chunks.size()) return;
                task[0].cancel();
                oreSeek.remove(id);
                if (!p.isOnline()) return;
                if (best[0] == null) { say(p, "I find no " + name + " within " + ORE_RANGE + " blocks. It may not exist in this world."); return; }
                say(p, "The nearest " + name + " is " + (int) Math.sqrt(bd[0]) + " blocks away, at " + best[0].getX() + " " + best[0].getY() + " " + best[0].getZ() + ".");
                then.accept(best[0]);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Ore search stopped: " + e);
                task[0].cancel();
                oreSeek.remove(id);
            }
        }, 1L, 1L);
    }

    private final Set<UUID> mobSeek = new HashSet<>(), woolTried = new HashSet<>();
    private static final Map<String, EntityType> MOB_FOR = Map.ofEntries(Map.entry("wool", EntityType.SHEEP), Map.entry("mutton", EntityType.SHEEP), Map.entry("ull", EntityType.SHEEP),
            Map.entry("sau", EntityType.SHEEP), Map.entry("sauer", EntityType.SHEEP), Map.entry("leather", EntityType.COW), Map.entry("beef", EntityType.COW), Map.entry("milk", EntityType.COW),
            Map.entry("ku", EntityType.COW), Map.entry("kyr", EntityType.COW), Map.entry("lær", EntityType.COW), Map.entry("feather", EntityType.CHICKEN), Map.entry("egg", EntityType.CHICKEN),
            Map.entry("høne", EntityType.CHICKEN), Map.entry("høner", EntityType.CHICKEN), Map.entry("fjær", EntityType.CHICKEN), Map.entry("pork", EntityType.PIG), Map.entry("porkchop", EntityType.PIG),
            Map.entry("gris", EntityType.PIG), Map.entry("griser", EntityType.PIG), Map.entry("hest", EntityType.HORSE), Map.entry("hester", EntityType.HORSE));

    private static EntityType mobFor(String term) {
        EntityType t = MOB_FOR.get(term);
        if (t != null) return t;
        try { t = EntityType.valueOf(term.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return null; }
        return t.isAlive() && t != EntityType.PLAYER ? t : null;
    }

    /** The nearest living thing of a kind: those already around first, then the land outwards, a few chunks at a time
     *  (their animals have to be read in before they can be seen). {@code then} gets null when there is none. */
    private void seekMob(Player p, EntityType type, java.util.function.Predicate<Entity> ok, Consumer<Entity> then) {
        UUID id = p.getUniqueId();
        World w = p.getWorld();
        Location from = p.getLocation();
        Entity near = null;
        for (Entity e : w.getEntities()) if (e.getType() == type && e.isValid() && ok.test(e) && (near == null || e.getLocation().distanceSquared(from) < near.getLocation().distanceSquared(from))) near = e;
        if (near != null) { then.accept(near); return; }
        if (!mobSeek.add(id)) { say(p, "I am still looking."); return; }
        String name = type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        say(p, "I see no " + name + " around here. I look further away. This can take a few minutes.");
        int px = from.getBlockX(), pz = from.getBlockZ();
        List<int[]> chunks = new ArrayList<>();
        for (int cx = (px - ORE_RANGE) >> 4; cx <= (px + ORE_RANGE) >> 4; cx++) for (int cz = (pz - ORE_RANGE) >> 4; cz <= (pz + ORE_RANGE) >> 4; cz++) if (!w.isChunkLoaded(cx, cz)) chunks.add(new int[] {cx, cz});
        chunks.sort(Comparator.comparingDouble(c -> Math.hypot(c[0] * 16 + 8 - px, c[1] * 16 + 8 - pz)));
        int[] at = {0, 0, 0};                                              // next chunk, ticks waited, new land made
        List<int[]> batch = new ArrayList<>();
        org.bukkit.scheduler.BukkitTask[] task = new org.bukkit.scheduler.BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                if (batch.isEmpty()) {
                    while (batch.size() < 9 && at[0] < chunks.size()) {
                        int[] c = chunks.get(at[0]++);
                        if (!w.isChunkGenerated(c[0], c[1]) && at[2]++ >= ORE_NEW) continue;
                        w.addPluginChunkTicket(c[0], c[1], plugin);
                        batch.add(c);
                    }
                    at[1] = 0;
                    if (batch.isEmpty()) { task[0].cancel(); mobSeek.remove(id); if (p.isOnline()) then.accept(null); }
                    return;
                }
                boolean ready = true;
                for (int[] c : batch) if (!w.isChunkLoaded(c[0], c[1]) || !w.getChunkAt(c[0], c[1]).isEntitiesLoaded()) ready = false;
                if (!ready && at[1]++ < 30) return;
                Entity hit = null;
                for (int[] c : batch) for (Entity e : w.getChunkAt(c[0], c[1]).getEntities())
                    if (e.getType() == type && e.isValid() && ok.test(e) && (hit == null || e.getLocation().distanceSquared(from) < hit.getLocation().distanceSquared(from))) hit = e;
                for (int[] c : batch) {
                    if (hit != null && hit.getLocation().getBlockX() >> 4 == c[0] && hit.getLocation().getBlockZ() >> 4 == c[1]) {       // it stays loaded for a minute, for what comes next
                        Bukkit.getScheduler().runTaskLater(plugin, () -> w.removePluginChunkTicket(c[0], c[1], plugin), 1200L);
                    } else w.removePluginChunkTicket(c[0], c[1], plugin);
                }
                batch.clear();
                if (hit == null) return;
                task[0].cancel();
                mobSeek.remove(id);
                if (p.isOnline()) then.accept(hit);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Search for " + name + " stopped: " + e);
                for (int[] c : batch) w.removePluginChunkTicket(c[0], c[1], plugin);
                task[0].cancel();
                mobSeek.remove(id);
            }
        }, 2L, 2L);
    }

    /** No wool: she finds the nearest sheep, goes there, shears the flock and comes back. */
    private void woolTrip(Player p, Runnable then) {
        UUID id = p.getUniqueId();
        seekMob(p, EntityType.SHEEP, e -> e instanceof Sheep sh && sh.isAdult() && !sh.isSheared(), e -> {
            if (e == null) { say(p, "I found no sheep with wool on within " + ORE_RANGE + " blocks."); then.run(); return; }
            H h = helpers.get(id);
            Location at = e.getLocation();
            boolean goes = h != null && !h.merged && h.w != null && !h.w.isDead() && at.distanceSquared(p.getLocation()) > 24 * 24;
            if (goes) h.w.teleport(at);
            int n = 0;
            for (Entity o : at.getWorld().getNearbyEntities(at, 24, 12, 24)) if (o instanceof Sheep sh && sh.isAdult() && !sh.isSheared() && n < 8) {
                sh.setSheared(true);
                org.bukkit.DyeColor col = sh.getColor();
                Material wool = mat((col == null ? "WHITE" : col.name()) + "_WOOL");
                bag(id).addItem(new ItemStack(wool == null ? Material.WHITE_WOOL : wool, 1 + ThreadLocalRandom.current().nextInt(3)));
                sh.getWorld().playSound(sh.getLocation(), Sound.ENTITY_SHEEP_SHEAR, 1f, 1f);
                n++;
            }
            dirty.add(id);
            say(p, "I found sheep " + (int) at.distance(p.getLocation()) + " blocks away, at " + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ() + ", and sheared " + n + ". The wool is in my bag.");
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                H back = helpers.get(id);
                if (goes && p.isOnline() && back != null && back.w != null && !back.w.isDead()) back.w.teleport(p.getLocation());
                then.run();
            }, goes ? 60L : 1L);
        });
    }

    private void mineFrom(Player p, H h, Block t) {
        if (t == null || t.getType().isAir()) { p.sendMessage("Look at the block your helper should mine (within 6 blocks), or name it: mine sand."); return; }
        if (t.getType().getHardness() < 0 || t.getState() instanceof Container) { say(p, "I can't mine that."); return; }
        String kind = vein(t.getType());
        release(h);
        Set<Block> seen = new HashSet<>(List.of(t));
        Deque<Block> open = new ArrayDeque<>(List.of(t));
        while (!open.isEmpty() && h.queue.size() < VEIN_MAX) {             // the connected blocks of the same kind, nearest first
            Block b = open.poll();
            h.queue.add(b);
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
                Block n = b.getRelative(x, y, z);
                if (seen.add(n) && vein(n.getType()).equals(kind) && n.getLocation().distanceSquared(t.getLocation()) < 24 * 24) open.add(n);
            }
        }
        if (!Tag.LOGS.isTagged(t.getType())) {                              // tidy work: layer by layer from the top, row by row, back and forth
            List<Block> rows = new ArrayList<>(h.queue);
            rows.sort(Comparator.<Block>comparingInt(b -> -b.getY()).thenComparingInt(Block::getX).thenComparingInt(b -> (b.getX() & 1) == 0 ? b.getZ() : -b.getZ()));
            h.queue = new ArrayDeque<>(rows);
        }
        h.job = Job.MINE;
        h.done = h.stuck = 0;
        say(p, "On it: " + h.queue.size() + " " + (h.queue.size() == 1 ? "block" : "blocks") + " of " + word(t.getType()) + ", and nothing else.");
    }

    private void startFarm(Player p, H h) {
        release(h);
        h.job = Job.FARM;
        h.anchor = h.w.getLocation();
        h.done = h.stuck = 0;
        say(p, "I will harvest and replant around here. Say stop when you want me back.");
    }

    private void startFish(Player p, H h) {
        Block c = h.w.getLocation().getBlock(), water = null;
        double best = Double.MAX_VALUE;
        for (int x = -8; x <= 8; x++) for (int y = -4; y <= 1; y++) for (int z = -8; z <= 8; z++) {
            Block b = c.getRelative(x, y, z);
            double d = x * x + y * y + z * z;
            if (d < best && b.getType() == Material.WATER && b.getRelative(BlockFace.UP).getType().isAir()) { best = d; water = b; }
        }
        if (water == null) { say(p, "I need water close by to fish."); return; }
        release(h);
        h.job = Job.FISH;
        h.anchor = water.getLocation().add(0.5, 0.9, 0.5);
        h.done = h.stuck = h.cast = 0;
        say(p, "Gone fishing! Say stop when you want me back.");
    }

    private static final Material[] JUNK = {Material.LILY_PAD, Material.LILY_PAD, Material.BOWL, Material.LEATHER, Material.LEATHER_BOOTS, Material.ROTTEN_FLESH,
            Material.STICK, Material.STRING, Material.BONE, Material.BONE, Material.INK_SAC, Material.TRIPWIRE_HOOK, Material.BAMBOO};

    /** One catch, with the odds of a rod with luck of the sea III: 11.3 % treasure (an enchanted book, bow or rod,
     *  a name tag, a saddle or a nautilus shell), 4.2 % junk, the rest fish. Rolled here: the game's own fishing
     *  tables want the rod that was cast, and there is no way to hand them one. */
    private Collection<ItemStack> fishLoot(Location at) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int roll = r.nextInt(1000);
        if (roll < 113) {
            java.util.Random rnd = new java.util.Random();
            try {
                switch (r.nextInt(6)) {
                    case 0: return List.of(new ItemStack(Material.BOOK).enchantWithLevels(30, true, rnd));
                    case 1: return List.of(new ItemStack(Material.BOW).enchantWithLevels(22 + r.nextInt(9), true, rnd));
                    case 2: return List.of(new ItemStack(Material.FISHING_ROD).enchantWithLevels(22 + r.nextInt(9), true, rnd));
                    case 3: return List.of(new ItemStack(Material.NAME_TAG));
                    case 4: return List.of(new ItemStack(Material.SADDLE));
                    default: return List.of(new ItemStack(Material.NAUTILUS_SHELL));
                }
            } catch (RuntimeException ex) {
                return List.of(new ItemStack(Material.NAME_TAG));
            }
        }
        if (roll < 155) return List.of(new ItemStack(JUNK[r.nextInt(JUNK.length)]));
        int f = r.nextInt(100);
        return List.of(new ItemStack(f < 60 ? Material.COD : f < 85 ? Material.SALMON : f < 98 ? Material.PUFFERFISH : Material.TROPICAL_FISH));
    }

    private void fish(Player p, H h) {
        Wolf w = h.w;
        if (h.anchor.getBlock().getType() != Material.WATER) { release(h); say(p, "The water is gone, so I stopped fishing."); return; }
        double dx = h.anchor.getX() - w.getX(), dz = h.anchor.getZ() - w.getZ();
        if (dx * dx + dz * dz > 3.5 * 3.5 && h.stuck < 8) {              // walk to the shore; after four seconds it casts from where it is
            go(h, h.anchor, 1.2);
            h.stuck++;
            h.cast = 0;
            return;
        }
        if (h.cast == 0) {
            w.getPathfinder().stopPathfinding();
            h.cast = 10 + ThreadLocalRandom.current().nextInt(21);        // a bite after 5 to 15 seconds
            swing(p, h);
            p.playSound(w.getLocation(), Sound.ENTITY_FISHING_BOBBER_THROW, 0.5f, 1f);
            return;
        }
        if (tick % 20 == 0) p.spawnParticle(Particle.FISHING, h.anchor, 3, 0.15, 0, 0.15, 0.01);
        if (--h.cast > 0) return;
        h.stuck = 0;
        swing(p, h);
        p.spawnParticle(Particle.SPLASH, h.anchor, 15, 0.2, 0.05, 0.2, 0.05);
        p.playSound(h.anchor, Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.4f, 1f);
        Collection<ItemStack> loot = fishLoot(h.anchor);
        for (ItemStack i : loot) {
            bar(p, name(p.getUniqueId()) + " caught " + i.getType().name().toLowerCase(Locale.ROOT).replace('_', ' '));
            String magic = magic(i);
            if (magic != null) { say(p, "Look what I caught: " + word(i.getType()) + " with " + magic + "!"); awayLog(p.getUniqueId(), "FOUND " + word(i.getType()).toUpperCase(Locale.ROOT) + ": " + magic); }
        }
        keep(p, h, loot);
        xp(p.getUniqueId(), 1 + ThreadLocalRandom.current().nextInt(6));
        h.done++;
        if (bag(p.getUniqueId()).firstEmpty() == -1 && !unload(p, h, true)) {
            int n = h.done;
            release(h);
            say(p, "My bag is full, so I stopped fishing. I caught " + n + (n == 1 ? " thing." : " things."));
        }
    }

    private static boolean ripe(Block b) {
        Material t = b.getType();
        if (SEEDS.containsKey(t)) return b.getBlockData() instanceof Ageable a && a.getAge() == a.getMaximumAge();
        if (t == Material.MELON || t == Material.PUMPKIN) {              // only the ones growing from a stem, not decoration
            for (BlockFace f : SIDES) {
                Material n = b.getRelative(f).getType();
                if (n == Material.ATTACHED_MELON_STEM || n == Material.ATTACHED_PUMPKIN_STEM) return true;
            }
            return false;
        }
        return t == Material.SUGAR_CANE && b.getRelative(BlockFace.DOWN).getType() == Material.SUGAR_CANE
                && b.getRelative(0, -2, 0).getType() != Material.SUGAR_CANE;   // the second block: the bottom one stays
    }

    private static Collection<ItemStack> drops(Block b) {
        for (Material tool : TOOLS) {
            Collection<ItemStack> d = b.getDrops(ench(tool));
            if (!d.isEmpty()) return d;
        }
        return List.of();
    }

    private static void crack(Block b) {
        Location l = b.getLocation().add(0.5, 0.5, 0.5);
        b.getWorld().spawnParticle(Particle.BLOCK, l, 20, 0.25, 0.25, 0.25, b.getBlockData());
        b.getWorld().playSound(l, b.getBlockSoundGroup().getBreakSound(), 0.8f, 1f);
    }

    private Collection<ItemStack> harvest(Block b) {
        Material t = b.getType();
        if (b.getBlockData() instanceof Ageable a && SEEDS.containsKey(t)) {
            List<ItemStack> d = new ArrayList<>(b.getDrops(ench(Material.NETHERITE_HOE)));     // fortune III
            for (ItemStack i : d) if (i.getType() == SEEDS.get(t)) { i.setAmount(i.getAmount() - 1); break; }   // one goes back in the ground
            d.removeIf(Helper::empty);
            crack(b);
            a.setAge(0);
            b.setBlockData(a);
            return d;
        }
        if (t == Material.SUGAR_CANE) {
            List<Block> canes = new ArrayList<>();
            for (Block c = b; c.getType() == Material.SUGAR_CANE; c = c.getRelative(BlockFace.UP)) canes.add(c);
            crack(b);
            for (int i = canes.size() - 1; i >= 0; i--) canes.get(i).setType(Material.AIR);
            return List.of(new ItemStack(Material.SUGAR_CANE, canes.size()));
        }
        Collection<ItemStack> d = drops(b);
        crack(b);
        b.setType(Material.AIR);
        return d;
    }

    private void work(Player p, H h) {
        Wolf w = h.w;
        if (h.job == Job.FISH) { fish(p, h); return; }
        if (h.job == Job.FARM && h.queue.isEmpty() && tick % 40 == 0) {
            List<Block> found = new ArrayList<>();
            Block c = h.anchor.getBlock();
            for (int x = -FARM_RANGE; x <= FARM_RANGE; x++) for (int y = -2; y <= 3; y++) for (int z = -FARM_RANGE; z <= FARM_RANGE; z++) {
                Block b = c.getRelative(x, y, z);
                if (ripe(b)) found.add(b);
            }
            Location at = w.getLocation();
            found.sort(Comparator.comparingDouble(b -> b.getLocation().distanceSquared(at)));
            h.queue.addAll(found);
            animals(p, h);
        }
        for (int n = 0; n < 2; n++) {                                     // up to four blocks a second
            Block b = h.queue.peek();
            while (b != null && (h.job == Job.FARM ? !ripe(b) : b.getType().isAir())) { h.queue.poll(); b = h.queue.peek(); }
            if (b == null) break;
            Location l = b.getLocation().add(0.5, 0, 0.5);
            double dx = l.getX() - w.getX(), dz = l.getZ() - w.getZ();
            boolean reach = dx * dx + dz * dz <= 3.5 * 3.5 && Math.abs(l.getY() - w.getY()) <= 12;
            if (!reach && h.stuck < 8) {                                  // walk there; after four seconds it reaches anyway
                go(h, l, 1.3);
                h.stuck++;
                return;
            }
            boolean gone = !reach && h.job == Job.MINE && !h.merged && (dx * dx + dz * dz > 12 * 12 || Math.abs(l.getY() - w.getY()) > 12);   // far away or deep down: she goes there
            h.queue.poll();
            h.stuck = 0;
            if (h.job == Job.MINE) { Material held = toolFor(b.getType()); if (held != null) wield(h, held); }
            swing(p, h);
            keep(p, h, h.job == Job.FARM ? harvest(b) : mineBlock(b));
            h.done++;
            if (gone) { w.teleport(l); h.anchor = l; }                    // into the hole she just made, and on from there
        }
        if (!h.queue.isEmpty()) return;
        if (h.job == Job.MINE) {
            int n = h.done;
            release(h);
            boolean away = !h.merged && (w.getWorld() != p.getWorld() || w.getLocation().distanceSquared(p.getLocation()) > 20 * 20);
            if (away) w.teleport(p.getLocation());                        // back from the mine
            say(p, "Done! " + n + (n == 1 ? " block" : " blocks") + "." + (away ? " I am back." : ""));
        } else if (!h.merged) walk(h, h.anchor, dist2(w, h.anchor), 5, 1.0);
    }

    private static Collection<ItemStack> mineBlock(Block b) {
        Collection<ItemStack> d = drops(b);
        crack(b);
        b.setType(Material.AIR);
        return d;
    }

    // --- Never dies, hits hard -----------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHurt(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player pl) {                       // merged with their helper: as unbreakable as it is
            if (merged(pl) && e.getCause() != EntityDamageEvent.DamageCause.VOID && e.getCause() != EntityDamageEvent.DamageCause.KILL) {
                e.setCancelled(true);
                pl.setFireTicks(0);
            }
            return;
        }
        UUID owner = ownerOf(e.getEntity());
        if (owner == null) return;
        e.setCancelled(true);
        e.getEntity().setFireTicks(0);
        if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
            Player p = Bukkit.getPlayer(owner);
            if (p != null && e.getEntity() instanceof Wolf w) w.teleport(p.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBite(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player pl) {                      // merged: the owner hits like the helper (never players)
            if (!merged(pl) || e.getEntity() instanceof Player || ownerOf(e.getEntity()) != null) return;
            e.setDamage(Math.max(e.getDamage(), damage(pl.getUniqueId())));
            Location l = e.getEntity().getLocation().add(0, e.getEntity().getHeight() * 0.6, 0);
            l.getWorld().spawnParticle(Particle.SWEEP_ATTACK, l, 1);
            return;
        }
        UUID owner = ownerOf(e.getDamager());
        if (owner == null) return;
        if (e.getEntity() instanceof Player) { e.setCancelled(true); return; }   // never hurts players
        e.setDamage(damage(owner));
        H h = helpers.get(owner);
        if (h != null) { wield(h, Material.NETHERITE_SWORD); h.m.swingMainHand(); }
        Location l = e.getEntity().getLocation().add(0, e.getEntity().getHeight() * 0.6, 0);   // everyone sees the blow, not the helper
        l.getWorld().spawnParticle(Particle.SWEEP_ATTACK, l, 1);
        l.getWorld().playSound(l, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 1f);
    }

    /** The wolf must not go after players or other helpers (wolves normally attack whoever hits their owner), and
     *  after nothing at all when its owner has made it passive. */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent e) {
        if (e.getTarget() == null) return;
        UUID hunted = e.getTarget() instanceof Player pl ? (merged(pl) ? pl.getUniqueId() : null) : ownerOf(e.getTarget());
        if (hunted != null && tiny(hunted) && ownerOf(e.getEntity()) == null) { e.setCancelled(true); return; }   // too small to notice
        UUID owner = ownerOf(e.getEntity());
        if (owner == null) return;
        H h = helpers.get(owner);
        boolean sent = h != null && e.getTarget().getUniqueId().equals(h.ordered);          // /helper attack beats passive
        if (e.getTarget() instanceof Player || ownerOf(e.getTarget()) != null || ((flag(owner, "passive", false) || style(owner).equals("bow")) && !sent)) e.setCancelled(true);
        if (helpers.get(owner) != null && helpers.get(owner).hold) e.setCancelled(true);          // clicked on: it fights nothing
    }

    /** Arrows and other projectiles fly through helpers: to everyone but the owner there is nothing there. */
    @EventHandler(ignoreCancelled = true)
    public void onProjectile(ProjectileHitEvent e) {
        if (e.getHitEntity() != null && ownerOf(e.getHitEntity()) != null) e.setCancelled(true);
    }

    // --- Right-click: stay / follow, give gear, open the bag ------------------------------------------------

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(PlayerInteractEntityEvent e) {
        UUID owner = ownerOf(e.getRightClicked());
        if (owner == null || e.isCancelled()) return;          // cancelled = the Magic Wand already cast on it
        e.setCancelled(true);                                  // no feeding, leashing or vanilla sit toggle
        Player p = e.getPlayer();
        if (e.getHand() != EquipmentSlot.HAND || !p.getUniqueId().equals(owner)) return;
        H h = helpers.get(owner);
        if (h == null) return;
        if (p.isSneaking()) { p.openInventory(bag(owner)); return; }
        int slot = gearSlot(p.getInventory().getItemInMainHand());
        if (slot >= 0) { equip(p, h, slot); return; }
        if (flowerGift(p, h)) return;
        if (h.hold) carryOn(p, h); else hold(p, h);                    // a click: stand still and watch me; a click more: carry on
    }

    private static final String[] FLOWER_THANKS = {
            "Oh, %f! That was so thoughtful of you.", "For me? Thank you, %n. It is lovely.", "You remembered that I like flowers! Thank you.",
            "%F! I will keep it close. Thank you, %n.", "That is the nicest thing anyone has done for me today.", "It smells wonderful. Thank you!",
            "You are too kind, %n. I love it.", "A flower, for me? Now I am blushing.", "Thank you! It makes me think of you, %n.",
            "How pretty! You always know how to make me smile.", "I will treasure it. That was sweet of you.", "Aww. Thank you, %n. You made my day."};

    /** A flower in the owner's hand, and a click on the helper: it takes the flower, hearts rise around it and it says thank you. */
    private boolean flowerGift(Player p, H h) {
        ItemStack it = p.getInventory().getItemInMainHand();
        if (empty(it)) return false;
        Material m = it.getType();
        if (!(Tag.FLOWERS.isTagged(m) || Tag.SMALL_FLOWERS.isTagged(m) || m == Material.TORCHFLOWER || m == Material.PITCHER_PLANT || m == Material.SPORE_BLOSSOM || m == Material.PINK_PETALS)
                || Tag.LEAVES.isTagged(m)) return false;
        UUID id = p.getUniqueId();
        it.setAmount(it.getAmount() - 1);
        bag(id).addItem(new ItemStack(m));                                 // it keeps it (if the bag has room)
        dirty.add(id);
        wield(h, m);                                                       // and holds it for a moment
        double k = h.fit > 0 ? h.fit : size(id);
        Location l = h.m.getLocation();
        l.getWorld().spawnParticle(Particle.HEART, l.clone().add(0, 1.2 * k, 0), 9, 0.45 * k, 0.5 * k, 0.45 * k, 0);
        l.getWorld().playSound(l, Sound.ENTITY_ALLAY_AMBIENT_WITH_ITEM, 0.8f, 1.3f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (h.m.isValid()) h.m.getWorld().spawnParticle(Particle.HEART, h.m.getLocation().add(0, 1.9 * k, 0), 4, 0.3 * k, 0.2 * k, 0.3 * k, 0); }, 14L);
        String flower = word(m), line = FLOWER_THANKS[ThreadLocalRandom.current().nextInt(FLOWER_THANKS.length)];
        String an = "aeiou".indexOf(flower.charAt(0)) >= 0 ? "an " : "a ";
        say(p, line.replace("%F", an.substring(0, 1).toUpperCase(Locale.ROOT) + an.substring(1) + flower).replace("%f", an + flower).replace("%n", nick(p)));
        return true;
    }

    /** Remembers what it is doing (a job, a guard post, a building) so that "continue" can take it up again. */
    private void snap(UUID id, H h) {
        Site s = sites.get(id);
        if (s != null && !s.paused) { s.paused = true; h.was = new Object[] {"site"}; return; }
        if (h.hold || (h.job == Job.NONE && h.stance != Stance.GUARD)) return;          // nothing going on: keep what was remembered before
        h.was = new Object[] {h.stance, h.job, h.anchor, new ArrayDeque<>(h.queue), h.farm};
    }

    /** Clicked on: it stands still and watches its owner, whatever it was doing, and fights nothing until it hears continue, come or help. */
    /** Sleep: it walks to the nearest free bed and lies down, until morning or the next order. */
    private void sleep(Player p, H h) {
        Location o = h.w.getLocation();
        Block best = null;
        double bd = Double.MAX_VALUE;
        org.bukkit.block.data.type.Bed bestBed = null;
        for (int dx = -16; dx <= 16; dx++) for (int dy = -5; dy <= 5; dy++) for (int dz = -16; dz <= 16; dz++) {
            Block b = o.getBlock().getRelative(dx, dy, dz);
            if (!Tag.BEDS.isTagged(b.getType()) || !(b.getBlockData() instanceof org.bukkit.block.data.type.Bed bed)) continue;
            if (bed.getPart() != org.bukkit.block.data.type.Bed.Part.HEAD || bed.isOccupied()) continue;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bd) { bd = d; best = b; bestBed = bed; }
        }
        if (best == null) { say(p, "I see no free bed within 16 blocks. Put one down and I will sleep in it."); return; }
        snap(p.getUniqueId(), h);
        release(h);
        Vector along = bestBed.getFacing().getDirection();                 // from the foot of the bed to the pillow
        Location l = best.getLocation().add(0.5, 0.5625, 0.5).subtract(along.clone().multiply(1.3));   // a lying figure has its feet where it "stands"
        l.setDirection(along);
        l.setYaw(90 - l.getYaw());                                         // ... and its head a quarter turn from where it faces
        l.setPitch(0);
        h.bedGo = l;
        h.bedTries = 0;
        say(p, night(o.getWorld()) ? "Good night, " + nick(p) + ". Wake me if you need me." : "A nap? Gladly. Wake me if you need me.");
    }

    private void hold(Player p, H h) {
        snap(p.getUniqueId(), h);
        release(h);
        h.hold = true;
        h.stance = Stance.STAY;
        h.w.setTarget(null);
        h.w.getPathfinder().stopPathfinding();
        h.w.setVelocity(new Vector(0, 0, 0));
        h.w.setAware(false);                                               // no will of its own until it hears continue, come or help
        h.holdAt = h.w.getLocation();
        bar(p, "Your helper stands still and watches you (say continue, come or help)");
    }

    /** "continue": back to what it did before it was interrupted. */
    @SuppressWarnings("unchecked")
    private void carryOn(Player p, H h) {
        UUID id = p.getUniqueId();
        Object[] was = h.was;
        h.was = null;
        Site s = sites.get(id);
        release(h);
        if (s != null && s.paused) { s.paused = false; say(p, s.down ? "Back to taking it down." : "Back to the building."); return; }
        if (was == null || was[0] instanceof String) { bar(p, "Your helper follows you"); return; }
        h.stance = (Stance) was[0];
        h.job = (Job) was[1];
        h.anchor = (Location) was[2];
        h.queue = (Deque<Block>) was[3];
        h.farm = (String) was[4];
        if (h.farm != null) { say(p, "Back to the mob farm."); return; }
        say(p, h.job == Job.FISH ? "Back to the fishing." : h.job == Job.FARM ? "Back to the fields." : h.job == Job.MINE ? "Back to the digging." : "Back to my post.");
    }

    /** "help": it drops everything, comes at once and fights what is around its owner. "continue" afterwards takes the job up again. */
    private void rescue(Player p, H h) {
        UUID id = p.getUniqueId();
        snap(id, h);
        release(h);
        if (dist2(h.w, p.getLocation()) > 6 * 6) h.w.teleport(p.getLocation());
        protect(id, h, p.getLocation());
        say(p, flag(id, "passive", false) ? "I am here! (You told me not to fight; say fight and I will.)" : "I am here! Stay behind me.");
    }

    private void stay(Player p, H h, boolean sit) {
        snap(p.getUniqueId(), h);
        release(h);
        if (sit) {
            h.stance = Stance.STAY;
            h.w.setSitting(true);
            h.w.setTarget(null);
        }
        bar(p, sit ? "Your helper waits here" : "Your helper follows you");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        H hq = helpers.get(id);
        if (hq != null && hq.farm != null) {
            conf.set(path(id, "away.prevjob"), conf.getString(path(id, "away.job")));
            conf.set(path(id, "away.prevplace"), conf.getString(path(id, "away.place")));
            conf.set(path(id, "away.auto"), true);
            if (!flag(id, "watch", false)) { conf.set(path(id, "away.autowatch"), true); conf.set(path(id, "watch"), true); }
            conf.set(path(id, "away.job"), "place");
            conf.set(path(id, "away.place"), hq.farm);
            conf.set(path(id, "away.full"), null);
            mobYards.remove(id);
            yards.remove(id);
        }
        talking.remove(id);
        Site site = sites.get(id);
        if (site != null) {                                    // a building in progress: it finishes it on its own
            site.paused = false;
            awayLog(id, "keeps building the " + site.name + " at " + site.where + " while you are away");
        }
        H h = helpers.get(id);
        if (h == null || !flag(id, "watch", false)) { despawn(id, true); return; }
        for (String k : List.of("ms", "fish", "crops", "sown", "meal", "fed", "meat", "wool", "smelted", "mined", "explored", "xp", "eggs", "chicks", "goldchicks", "mobs", "sorted", "full", "why", "finds")) conf.set(path(id, "away." + k), null);    // a new time away
        if (h.merged) split(p, h);                             // on watch: it stays behind and guards home
        release(h);
        Location post = home(id) != null ? home(id) : h.w.getLocation();
        h.stance = Stance.GUARD;
        h.anchor = post;
        h.watching = true;
        awayLog(id, "stays on watch at " + post.getWorld().getName() + " " + post.getBlockX() + " " + post.getBlockY() + " " + post.getBlockZ());
        h.ticket = post.getChunk();
        h.ticket.addPluginChunkTicket(plugin);
        h.w.teleport(post);
        flushBags();
    }

    /** With a helper out, death costs nothing: it catches the things (and the levels), so there is no gravestone. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onOwnerDeath(PlayerDeathEvent e) {
        H h = helpers.get(e.getPlayer().getUniqueId());
        if (h == null || e.getKeepInventory()) return;
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setKeepLevel(true);
        e.setDroppedExp(0);
        h.saved = true;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            H h = helpers.get(p.getUniqueId());
            if (h == null || !h.saved || !p.isOnline()) return;
            h.saved = false;
            release(h);
            h.w.teleport(p.getLocation());
            say(p, "I caught your things when you fell. You still have everything.");
        }, 10L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        conf.set(path(p.getUniqueId(), "ownskin"), null);      // a login brings their own skin back by itself
        ownBody(p);                                            // the server stopped while they were merged
        if (flag(p.getUniqueId(), "talk", false)) {            // talk mode was on when they left: it still is
            talking.add(p.getUniqueId());
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline() && talking.contains(p.getUniqueId()))
                bar(p, "Talk mode is on: your chat goes to " + name(p.getUniqueId()) + " (say h to chat with the others)"); }, 60L);
        }
        if (active(p)) Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline() && active(p)) spawn(p, null); }, 40L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) awayReport(p); }, 100L);
    }

    // --- Talking to it: its name in chat ------------------------------------------------------------------

    /** "Bob", "Bob come", "Bob, how do I make a piston?": chat that starts with your helper's name goes to the helper
     *  and nobody else sees it. So does chat that starts with a full stop (".come"), and in talk mode all of it. Only for players who have (had) a helper. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        String name = names.get(p.getUniqueId());
        if (name == null || name.length() < 2) return;
        String msg = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        String rest;
        if (msg.equalsIgnoreCase("h")) rest = msg;                                     // "h": talk mode on/off
        else if (msg.length() > 1 && msg.charAt(0) == '.' && msg.charAt(1) != '.') rest = msg.substring(1);      // ".come"
        else if (msg.toLowerCase(Locale.ROOT).startsWith(name)) {
            rest = msg.substring(name.length());
            if (!rest.isEmpty() && Character.isLetterOrDigit(rest.charAt(0))) {        // "Bobby", not "Bob"
                if (!talking.contains(p.getUniqueId())) return;
                rest = msg;
            }
        } else if (talking.contains(p.getUniqueId())) rest = msg;                      // talk mode
        else {                                                                         // an order word in the open chat: it did not hear it
            String t = msg.toLowerCase(Locale.ROOT).replaceAll("[.!?\\s]+$", "");
            boolean order = t.matches("(?:skin|mine|dig|grav|build|bygg) .+");
            for (String[] w : WORDS) if (!order && t.matches(w[1])) order = true;
            String first = name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
            if (order) Bukkit.getScheduler().runTask(plugin, () -> { if (p.isOnline()) p.sendMessage(Component.text(first + " did not hear that (it went to everyone). Say \"h\" once and all you write goes to "
                    + first + ", or write ." + t + " or " + first + " " + t + ".", NamedTextColor.GRAY)); });
            return;
        }
        e.setCancelled(true);
        String words = rest.replaceFirst("^[\\s,:;.!?]+", "").trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            p.sendMessage(Component.text("<" + p.getName() + "> " + msg, NamedTextColor.GRAY));
            talk(p, words);
        });
    }

    /** Free text for the helper: the words it knows become a command, the rest goes to the chat bridge (which follows
     *  the server log, asks its model and answers with /helper say or another /helper command). */
    private void talk(Player p, String text) {
        String clean = POLITE.matcher(text.replaceAll("[.!?\\s]+$", "").trim()).replaceAll("").trim();
        String t = clean.toLowerCase(Locale.ROOT);
        if (t.isEmpty()) { command(p, new String[] {"come"}); return; }
        if (buildOrder(p.getUniqueId(), p, t, said -> say(p, said))) return;
        Matcher gf = Pattern.compile("(?:get|fetch|bring|grab|hent|ta med)(?: me| meg| us| oss)?(?: (?:some|the|noen|litt|all the|all))? (.+? (?:from|fra) .+)").matcher(t);
        if (gf.matches()) { command(p, ("fetch " + gf.group(1)).split(" ")); return; }
        Matcher mk = Pattern.compile("(?:mine|dig|dig up|grav|grav opp|hent)(?: (?:only|just|bare|kun|some|litt|the|all|all the|alt|all))* ([a-zæøå_ ]+?)(?:,? (?:not|and not|but not|ikke|og ikke|men ikke|here|her|please) .*)?").matcher(t);
        if (mk.matches() && mineKind(mk.group(1)) != null) { command(p, ("mine " + mk.group(1).trim()).split(" ")); return; }
        Matcher mfw = Pattern.compile("(?:run |drive |driv |kjør |work (?:at |on )?|jobb (?:på |i |ved )?)?(?:the )?mob ?farm(?:en)?\\b\\s*(.*)").matcher(t);
        if (mfw.matches()) { command(p, mfw.group(1).isEmpty() ? new String[] {"mobfarm"} : ("mobfarm " + mfw.group(1)).split(" ")); return; }
        Matcher pm = Pattern.compile("(?:personality|personlighet)\\b\\s*(.*)|(reset) (?:your )?personality|(nullstill) personlighet(?:en)?").matcher(t);
        if (pm.matches()) { say(p, personality(p.getUniqueId(), pm.group(1) != null ? pm.group(1) : "reset")); return; }
        Matcher fp = Pattern.compile("(?:farm|work|harvest|jobb|høst|arbeid)(?: at| on| in| på| i| ved)?(?: the)? (.{2,30})").matcher(t);
        if (fp.matches() && !slug(fp.group(1)).matches("home|hjemme|hjem|at_home|around_the_house") && conf.contains(path(p.getUniqueId(), "places." + slug(fp.group(1))))) {
            command(p, new String[] {"watch", "farm", slug(fp.group(1))});
            return;
        }
        for (String[] w : WORDS)
            if (t.matches(w[1])) { command(p, w[0].split(" ")); return; }
        H here = helpers.get(p.getUniqueId());
        Matcher m = FETCH_WORDS.matcher(t);
        if (m.matches() && here != null && fetch(p, here, m.group(1), true)) return;
        m = FIND_WORDS.matcher(t);
        if (m.matches() && find(p, m.group(1), true)) return;
        m = COLLECT_WORDS.matcher(t);
        if (m.matches() && placeNamed(p.getUniqueId(), m.group(1)) != null) { command(p, ("collect " + m.group(1)).split(" ")); return; }
        m = ROUTINE_WORDS.matcher(t);
        if (m.matches()) { command(p, ("routine " + TIME_WORDS.getOrDefault(m.group(1), m.group(1)) + " " + m.group(2)).split(" ")); return; }
        m = EVERY_WORDS.matcher(t);
        if (m.matches()) { command(p, ("routine every " + m.group(1) + " " + m.group(2)).split(" ")); return; }
        m = UNROUTINE_WORDS.matcher(t);
        if (m.matches()) { command(p, new String[] {"routine", "remove", m.group(1)}); return; }
        m = GIVE_WORDS.matcher(t);
        if (m.matches() && Bukkit.getPlayer(m.group(1)) != null) { command(p, new String[] {"give", m.group(1)}); return; }
        m = TRADE_WORDS.matcher(t);
        if (m.matches() && here != null && trade(p, here, m.group(1), true)) return;
        m = CALLME_WORDS.matcher(t);
        if (m.matches()) { command(p, ("callme " + clean.substring(clean.length() - m.group(1).length())).split(" ")); return; }
        m = JOIN_WORDS.matcher(t);
        if (m.matches() && Bukkit.getPlayer(m.group(1)) != null) { command(p, new String[] {"join", m.group(1)}); return; }
        m = REMEMBER_WORDS.matcher(t);
        if (m.matches()) { command(p, ("remember " + m.group(1)).split(" ")); return; }
        m = GOTO_WORDS.matcher(t);
        if (m.matches() && placeNamed(p.getUniqueId(), m.group(1)) != null) { command(p, ("goto " + m.group(1)).split(" ")); return; }
        m = SMELT_WORDS.matcher(t);
        if (m.matches() && here != null && Material.matchMaterial(slug(m.group(1)).toUpperCase(Locale.ROOT)) != null) { command(p, ("smelt " + m.group(1)).split(" ")); return; }
        m = CRAFT_WORDS.matcher(t);
        if (m.matches() && here != null && craft(p, here, m.group(1), true)) return;
        m = HELP_WORDS.matcher(t);
        if (m.matches() && Bukkit.getPlayer(m.group(1)) != null) { command(p, new String[] {"help", m.group(1)}); return; }
        m = SKIN_WORDS.matcher(t);
        if (m.matches()) { command(p, ("skin " + m.group(1)).split(" ")); return; }
        m = NAME_WORDS.matcher(t);
        if (m.matches()) { command(p, ("name " + clean.substring(clean.length() - m.group(1).length())).split(" ")); return; }
        long now = System.currentTimeMillis();
        if (now - asked.getOrDefault(p.getUniqueId(), 0L) < 3000) { bar(p, "One thing at a time…"); return; }
        asked.put(p.getUniqueId(), now);
        if (!helpers.containsKey(p.getUniqueId())) command(p, new String[] {"summon"});
        String name = name(p.getUniqueId());
        plugin.getLogger().info("HELPER_ASK " + p.getName() + "|" + name.replace('|', ' ') + "|" + (context(p) + personaNote(p.getUniqueId())).replace('|', ' ') + "|" + text.replaceAll("\\s+", " "));
        bar(p, name + " is thinking…");
    }

    // --- /helper -------------------------------------------------------------------------------------

    private static final List<String> SUBS = List.of("follow", "stay", "come", "guard", "passive", "fight", "mine", "farm", "fish", "torch", "light", "bridge",
            "home", "sethome", "unload", "fetch", "find", "ride", "show", "merge", "split", "big", "small", "normal", "max", "min", "defend", "help",
            "attack", "join", "craft", "smelt", "remember", "goto", "places", "forget", "watch", "dance", "callme", "sleep", "wake",
            "tidy", "shear", "repair", "collect", "trade", "give", "routine", "routines",
            "scout", "wayback", "style", "hide", "tag", "treasure", "hint", "talk", "sum", "log", "finds", "build", "personality", "continue", "rescue", "mobfarm",
            "pickup", "bag", "unequip", "stop", "quiet", "skin", "name", "dismiss", "ask");
    private static final String USAGE = "/helper follow | stay | come | guard | passive | fight | mine | farm | fish | torch | light | bridge | home | sethome | unload | fetch <item> [n] | find <thing> | ride | show | merge | split | big | small | normal | help <player> | attack | join <player> | craft <item> [n] | smelt [item] | remember <name> | goto <name> | places | watch | dance | pickup | bag | unequip | stop"
            + " | quiet | skin <name> | name <name> | dismiss. Right-click it: stay/follow, or give it the armour or weapon in your hand."
            + " Sneak + right-click: its bag. Say its name in chat and it comes; its name and a question, and it answers.";

    void register(Commands r) {
        r.register("helper", "Your helper: /helper [follow|stay|come|guard|mine|farm|torch|bag|skin <name>|name <name>|dismiss|…]", new BasicCommand() {
            @Override
            public void execute(CommandSourceStack src, String[] args) {
                if (!(src.getExecutor() instanceof Player p)) { console(src.getSender(), args); return; }
                command(p, args);
            }

            @Override
            public Collection<String> suggest(CommandSourceStack src, String[] args) {
                if (args.length <= 1) {
                    String a = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
                    return SUBS.stream().filter(s -> s.startsWith(a)).toList();
                }
                if (args.length == 2 && (args[0].equalsIgnoreCase("goto") || args[0].equalsIgnoreCase("forget")) && src.getExecutor() instanceof Player pl) {
                    ConfigurationSection sec = conf.getConfigurationSection(path(pl.getUniqueId(), "places"));
                    return sec == null ? List.of() : new ArrayList<>(sec.getKeys(false));
                }
                if (args.length == 2 && args[0].equalsIgnoreCase("watch")) return List.of("on", "off", "guard", "fish", "farm", "smelt", "mine", "explore", "home", "mobfarm");
                if (args.length == 2 && args[0].equalsIgnoreCase("style")) return List.of("sword", "bow", "shield");
                if (args.length == 2 && args[0].equalsIgnoreCase("routine")) return List.of("morning", "noon", "evening", "night", "every", "remove", "clear");
                if (args.length == 2 && (args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("join") || args[0].equalsIgnoreCase("give")))
                    return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> !n.equals(src.getSender().getName())).toList();
                if (args.length == 2 && args[0].equalsIgnoreCase("skin")) {
                    List<String> s = new ArrayList<>(skins.names());
                    Bukkit.getOnlinePlayers().forEach(pl -> s.add(pl.getName()));
                    String part = args[1].toLowerCase(Locale.ROOT);          // "boy" also finds blueboy and coolboy
                    s.removeIf(n -> !n.toLowerCase(Locale.ROOT).contains(part));
                    return s;
                }
                return List.of();
            }
        });
    }

    /** Turns a yes/no setting around (or sets it with on/off) and returns the new value. */
    private boolean toggle(UUID id, String k, boolean def, String[] args) {
        boolean v = args.length > 1 ? args[1].equalsIgnoreCase("on") : !flag(id, k, def);
        conf.set(path(id, k), v);
        save();
        return v;
    }

    private void command(Player p, String[] args) {
        UUID id = p.getUniqueId();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        String rest = args.length < 2 ? "" : String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        H h = helpers.get(id);
        if (h == null && List.of("sleep", "wake", "follow", "stay", "guard", "mine", "farm", "fish", "bag", "stop", "home", "unload", "fetch", "ride", "merge", "split", "big", "small", "normal", "max", "min", "defend", "help", "attack", "join", "craft", "smelt", "goto", "dance", "repair", "collect", "trade", "give", "scout", "wayback", "hide", "tag", "treasure", "hint").contains(sub)) {
            p.sendMessage("You have no helper here: /helper");
            return;
        }
        if (h != null && h.merged && List.of("stay", "guard", "ride", "come", "show", "help", "join", "dance", "hide", "tag").contains(sub)) {
            say(p, "We are one right now. Say split first.");
            return;
        }
        switch (sub) {
            case "help" -> {
                Player t = args.length < 2 ? null : Bukkit.getPlayer(args[1]);
                if (t == null) { say(p, args.length < 2 ? "Who should I help? Say help and a player's name." : "I can't find " + args[1] + "."); return; }
                if (t == p) { stay(p, h, false); return; }
                release(h);
                h.ward = t.getUniqueId();
                t.showEntity(plugin, h.m);
                h.w.teleport(t.getLocation());
                say(p, "I will look after " + t.getName() + " until you call me back.");
                t.sendMessage(Component.text("<" + name(id) + "> " + p.getName() + " sent me to look after you."));
            }
            case "big" -> resize(p, h, size(id) * 1.5, true);
            case "small" -> resize(p, h, size(id) / 1.5, true);
            case "normal" -> resize(p, h, 1.0, true);
            case "defend" -> {
                boolean on = !(args.length > 1 && args[1].equalsIgnoreCase("off"));
                if (h.merged) { h.defend = on; say(p, on ? "I strike what comes near us. Say defend off and the fighting is yours again." : "The fighting is yours again."); }
                else { conf.set(path(id, "passive"), !on); save(); say(p, on ? "I keep the monsters off you." : "I leave the monsters alone."); }
            }
            case "max" -> resize(p, h, MAX_SIZE, true);
            case "min" -> resize(p, h, MIN_SIZE, true);
            case "merge" -> merge(p, h);
            case "split" -> split(p, h);
            case "", "summon" -> {
                if (h != null && (sub.isEmpty() || h.merged)) { p.sendMessage(Component.text(USAGE, NamedTextColor.GOLD)); return; }
                conf.set(path(id, "active"), true);
                save();
                spawn(p, null);
                p.sendMessage(Component.text("Your helper is here! Right-click it to make it wait or follow. /helper for more.", NamedTextColor.GOLD));
            }
            case "follow" -> stay(p, h, false);
            case "stay" -> stay(p, h, true);
            case "mobfarm" -> mobfarm(p, h, rest);
            case "continue" -> carryOn(p, h);
            case "rescue" -> rescue(p, h);
            case "stop" -> {
                boolean worked = h.job != Job.NONE;
                stay(p, h, false);
                if (worked) say(p, "Okay, I stopped.");
            }
            case "come" -> {
                if (h == null) { command(p, new String[] {"summon"}); return; }
                h.w.teleport(p.getLocation());
                stay(p, h, false);
            }
            case "guard" -> {
                release(h);
                h.stance = Stance.GUARD;
                h.anchor = h.w.getLocation();
                bar(p, "Your helper guards this spot");
            }
            case "passive", "fight" -> {
                conf.set(path(id, "passive"), sub.equals("passive"));
                save();
                if (h != null && sub.equals("passive")) h.w.setTarget(null);
                bar(p, sub.equals("passive") ? "Your helper will not fight" : "Your helper fights for you");
            }
            case "mine" -> startMine(p, h, rest);
            case "farm" -> startFarm(p, h);
            case "fish" -> startFish(p, h);
            case "light" -> bar(p, toggle(id, "light", false, args) ? "Your helper carries a light" : "Your helper put the light out");
            case "bridge" -> bar(p, toggle(id, "bridge", false, args) ? "Your helper builds under your feet (look up for steps)" : "No more bridge building");
            case "show" -> {
                boolean v = toggle(id, "show", false, args);
                if (h != null) h.m.setVisibleByDefault(v);
                bar(p, v ? "Everyone can see your helper" : "Only you can see your helper");
            }
            case "sethome" -> {
                Location l = p.getLocation();
                conf.set(path(id, "home"), spot(l));
                save();
                yards.remove(id);
                say(p, "Got it, this is home. I bring my things to the chests here.");
            }
            case "home" -> {
                Location l = home(id);
                if (l == null) { say(p, "I don't know where home is. Stand there and say sethome."); return; }
                travel(p, h, l);
                say(p, "Home again!");
            }
            case "unload" -> unload(p, h, false);
            case "fetch" -> fetch(p, h, rest, false);
            case "find" -> find(p, rest, false);
            case "ride" -> startRide(p, h);
            case "torch" -> bar(p, toggle(id, "torch", false, args) ? "Your helper puts torches where it is dark" : "No more torches");
            case "pickup" -> bar(p, toggle(id, "pickup", true, args) ? "Your helper picks things up" : "Your helper leaves things on the ground");
            case "talk" -> {
                boolean on = args.length > 1 ? args[1].equalsIgnoreCase("on") : !talking.contains(id);
                if (on) talking.add(id); else talking.remove(id);
                conf.set(path(id, "talk"), on ? true : null);                 // kept over logouts and restarts
                save();
                p.sendMessage(Component.text(on ? "Talk mode: everything you write now goes to " + name(id) + " and nobody else sees it. Say \"h\" or \"talk off\" to chat with the others again."
                        : "Talk mode is off: your chat goes to everyone again. A line that starts with a full stop still goes to " + name(id) + ".", NamedTextColor.GRAY));
            }
            case "quiet" -> bar(p, toggle(id, "quiet", false, args) ? "Your helper keeps quiet" : "Your helper talks again");
            case "bag", "chest" -> p.openInventory(bag(id));
            case "unequip" -> {
                ItemStack[] g = gear(id);
                boolean any = false;
                for (int i = 0; i < g.length; i++) { any |= !empty(g[i]); give(p, g[i]); g[i] = null; }
                store(id, "gear", g);
                save();
                if (h != null) dress(id, h);
                bar(p, any ? "You got its gear back" : "It has none of your gear");
            }
            case "hello" -> {
                if (h == null) command(p, new String[] {"summon"});
                h = helpers.get(id);
                if (h != null && !h.merged) h.dance = 80;              // a wave, then a little dance
                say(p, "Hi " + nick(p) + "!");
            }
            case "dance" -> h.dance = 50;
            case "sleep" -> sleep(p, h);
            case "wake" -> { boolean was = h.sleep || h.bedGo != null; stay(p, h, false); if (was) say(p, "I am up! What do we do?"); }
            case "scout" -> scout(p, h);
            case "wayback" -> wayback(p, h);
            case "hide" -> startGame(p, h, true);
            case "tag" -> startGame(p, h, false);
            case "treasure" -> startTreasure(p, h);
            case "hint" -> say(p, h.treasure == null ? "There is no treasure hidden right now. Say treasure and I bury one." : clue(p, h));
            case "style" -> {
                String st = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
                if (!List.of("sword", "bow", "shield").contains(st)) { p.sendMessage("Usage: /helper style <sword|bow|shield>"); return; }
                conf.set(path(id, "style"), st);
                save();
                if (h != null) { h.foe = null; h.w.setTarget(null); }
                bar(p, st.equals("bow") ? "Your helper shoots from where it stands" : st.equals("shield") ? "Your helper draws the monsters onto itself" : "Your helper fights up close");
            }
            case "build" -> { if (!buildOrder(id, p, String.join(" ", args).toLowerCase(Locale.ROOT), said -> say(p, said))) say(p, "I can build a little house: build house (here, in front of you), build house <x> <y> <z>, and undo build."); }
            case "sum" -> say(p, sum(id));
            case "log" -> say(p, conf.getString(path(id, "away.report"), "").isEmpty() ? "Nothing to tell from the last time you were away." : conf.getString(path(id, "away.report")));
            case "finds" -> say(p, finds(id));
            case "shear" -> woolTrip(p, () -> { });
            case "tidy" -> {
                List<Inventory> here = stores(p.getLocation(), 16);               // the chests where its owner stands, or else those at home
                Location hm = home(id);
                boolean atHome = hm != null && hm.getWorld() == p.getWorld() && hm.distanceSquared(p.getLocation()) <= HOME * HOME;
                boolean near = here.size() >= 2 && !atHome;
                int n = near ? tidy(here) : tidy(id);
                say(p, n < 0 ? (here.size() == 1 ? "There is only one chest here, and I" : "I see no chests here, and I") + " need at least two chests within 100 blocks of home to tidy there (say sethome)."
                        : n == 0 ? "The chests " + (near ? "here" : "at home") + " are tidy already."
                        : "Done: " + n + (n == 1 ? " thing" : " things") + " in the chests " + (near ? "here" : "at home") + " moved to where the rest of its kind is."
                        + (tidyStuck > 0 && n >= 0 ? " " + tidyStuck + (tidyStuck == 1 ? " thing sits" : " things sit") + " in a chest for something else and I found no better chest: an empty chest there would help." : ""));
            }
            case "repair" -> repair(p, h);
            case "collect" -> collect(p, h, rest);
            case "trade" -> trade(p, h, rest, false);
            case "give" -> {
                Player t = args.length < 2 ? null : Bukkit.getPlayer(args[1]);
                ItemStack it = p.getInventory().getItemInMainHand();
                if (t == null || t == p) { say(p, args.length < 2 ? "Who should get it? Say give and a player's name." : "I can't find " + args[1] + "."); return; }
                if (empty(it)) { say(p, "Hold what I should bring to " + t.getName() + " in your hand."); return; }
                String what = it.getAmount() + " " + word(it.getType());
                p.getInventory().setItemInMainHand(null);
                give(t, it);
                zip(p, h);
                t.playSound(t.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1f);
                say(p, "I brought " + what + " to " + t.getName() + ".");
                t.sendMessage(Component.text("<" + name(id) + "> " + p.getName() + " sent you " + what + "."));
            }
            case "routines" -> {
                List<String> rs = routineList(id);
                if (rs.isEmpty()) { say(p, "I have no routines. Say for example: every morning farm and smelt."); return; }
                StringBuilder sb = new StringBuilder("My routines:");
                for (int i = 0; i < rs.size(); i++) sb.append(" ").append(i + 1).append(") ").append(rs.get(i).replace("|", ": ")).append(i + 1 < rs.size() ? ";" : ".");
                say(p, sb + " Remove one with: remove routine <number>. Remove all with: clear routines.");
            }
            case "routine" -> {
                List<String> rs = routineList(id);
                String a1 = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
                if (a1.equals("clear") || a1.equals("reset")) {
                    conf.set(path(id, "routines"), null);
                    save();
                    if (h != null) h.ran.clear();
                    say(p, rs.isEmpty() ? "I had no routines." : "All " + rs.size() + " routines are gone. A fresh start!");
                    return;
                }
                if (a1.equals("remove") || a1.equals("delete")) {
                    int n = args.length < 3 || !args[2].matches("\\d{1,2}") ? 0 : Integer.parseInt(args[2]);
                    if (n < 1 || n > rs.size()) { say(p, rs.isEmpty() ? "I have no routines." : "Which one? I have " + rs.size() + ". Say routines to see them."); return; }
                    String gone = rs.remove(n - 1);
                    conf.set(path(id, "routines"), rs);
                    save();
                    say(p, "Removed: " + gone.replace("|", ": ") + ".");
                    return;
                }
                boolean every = a1.equals("every") && args.length > 3 && args[2].matches("\\d{1,3}") && Integer.parseInt(args[2]) > 0;
                if (!every && !TIMES.containsKey(a1) || args.length < (every ? 4 : 3)) {
                    p.sendMessage("Usage: /helper routine <morning|noon|evening|night|every <minutes>> <orders>, /helper routine remove <number>, /helper routine clear");
                    return;
                }
                if (rs.size() >= 10) { say(p, "Ten routines is all I can keep in my head. Remove one first."); return; }
                String orders = String.join(" ", Arrays.copyOfRange(args, every ? 3 : 2, args.length)).replace('|', ' ').trim();
                rs.add((every ? "every " + args[2] : a1) + "|" + orders);
                conf.set(path(id, "routines"), rs);
                save();
                say(p, "Okay: " + (every ? "every " + args[2] + " minutes" : "every " + a1) + " I will: " + orders + ". (Where I am standing then.)");
            }
            case "callme" -> {
                String nick = rest.replace('|', ' ').trim();
                if (nick.isEmpty()) { p.sendMessage("Usage: /helper callme <name>"); return; }
                if (nick.length() > 24) nick = nick.substring(0, 24);
                conf.set(path(id, "nick"), nick);
                save();
                say(p, "Okay, " + nick + " it is!");
            }
            case "attack" -> {
                Location eye = p.getEyeLocation();
                RayTraceResult hit = p.getWorld().rayTraceEntities(eye, eye.getDirection(), 48, 0.4,
                        e -> e instanceof LivingEntity && !(e instanceof Player) && !(e instanceof Mannequin) && ownerOf(e) == null);
                if (hit == null || !(hit.getHitEntity() instanceof LivingEntity le)) { say(p, "Look at what I should attack."); return; }
                String what = le.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
                if (h.merged) { le.damage(damage(id), p); p.swingMainHand(); return; }
                release(h);
                h.ordered = le.getUniqueId();
                if (dist2(h.w, le.getLocation()) > 12 * 12) h.w.teleport(le.getLocation());
                h.w.setTarget(le);
                bar(p, "Your helper goes for the " + what);
            }
            case "join" -> {
                Player t = args.length < 2 ? null : Bukkit.getPlayer(args[1]);
                H o = t == null || t == p ? null : helpers.get(t.getUniqueId());
                if (o == null || o.merged || (o.job == Job.NONE && o.stance != Stance.GUARD)) {
                    say(p, t == null ? "Whose helper should I join? Say join and a player's name." : t.getName() + "'s helper is not doing a job I can join.");
                    return;
                }
                release(h);
                h.anchor = o.anchor;
                h.done = h.stuck = h.cast = 0;
                if (o.job == Job.NONE) h.stance = Stance.GUARD;
                else { h.job = o.job; h.queue = o.queue; }             // one shared list: they split the work
                h.w.teleport(o.w.getLocation());
                say(p, "I am working with " + t.getName() + "'s helper now. Say stop to get me back.");
                t.sendMessage(Component.text("<" + name(t.getUniqueId()) + "> " + p.getName() + "'s helper " + name(id) + " is helping me."));
            }
            case "craft" -> craft(p, h, rest, false);
            case "smelt" -> smelt(p, h, rest);
            case "watch" -> {
                String a1 = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
                if (!AWAY_JOBS.contains(a1)) {
                    boolean on = toggle(id, "watch", false, args);
                    String job = conf.getString(path(id, "away.job"), "");
                    bar(p, !on ? "Your helper leaves with you when you log out" : job.isEmpty() ? "Your helper guards home while you are logged out" : "Your helper will " + job + " at home while you are logged out");
                    return;
                }
                String at = a1.equals("mobfarm") ? "mobfarm" : a1.equals("farm") && args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : null;
                if (at != null) {                                         // work at a saved place
                    if (placeNamed(id, at) == null) { say(p, "I know no place called " + at + ". Stand there and say remember " + at + " first."); return; }
                    conf.set(path(id, "watch"), true);
                    conf.set(path(id, "away.job"), "place");
                    conf.set(path(id, "away.place"), at);
                    save();
                    yards.remove(id);
                    mobYards.remove(id);
                    say(p, "When you log out I will work at " + at + ". " + "I look at what is there: fields I harvest and sow again, a mob farm I run (call the monsters, kill what comes to me, keep the drops and the experience for you), and where there is only water I fish.");
                    return;
                }
                if (a1.equals("mine")) {
                    String target = args.length < 3 ? null : oreTarget(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
                    if (target == null) { say(p, "Dig for what? Say for example: watch mine diamond. I know diamond, coal, iron, gold, copper, redstone, lapis, emerald, quartz, ancient debris and obsidian."); return; }
                    conf.set(path(id, "away.target"), target);
                    awayOre.remove(id);
                    awayScan.remove(id);
                }
                conf.set(path(id, "watch"), true);
                conf.set(path(id, "away.job"), a1.equals("guard") ? null : a1);
                conf.set(path(id, "away.rest"), a1.equals("guard") ? true : null);
                save();
                String hours = String.valueOf(conf.getDouble("settings.away-hours", 2)).replaceAll("\\.0$", "");
                say(p, home(id) == null ? "I need a home for that first. Stand at home and say sethome."
                        : a1.equals("guard") ? "When you log out I stay and guard home."
                        : "When you log out I will " + (a1.equals("fish") ? "fish in the water at home" : a1.equals("farm") ? "harvest and replant the fields at home"
                                : a1.equals("mine") ? "dig for " + conf.getString(path(id, "away.target"), "ore").toLowerCase(Locale.ROOT).replace('_', ' ') + " within 128 blocks of home"
                                : a1.equals("mobfarm") ? "work at the mob farm" + (conf.contains(path(id, "places.mobfarm")) ? "" : " (stand in its killing room and say remember mobfarm first)") + ": call the monsters, kill what comes to me and keep the drops and the experience for you"
                                : a1.equals("home") ? "work at home: fish, tend the fields, feed and shear the animals, slaughter when there are more than twelve grown ones of a kind, keep the furnaces going and the doors and gates shut"
                                : a1.equals("explore") ? "explore the land around home and remember the buildings I find" : "keep the furnaces at home going, with ore and fuel from the chests")
                                + ", for up to " + hours + " hours, also when nobody else is here. It goes in the chests at home, and I tell you how it went when you are back.");
            }
            case "pause", "resume" -> {
                if (!p.isOp()) { say(p, "Only an operator can do that."); return; }
                console(p, new String[] {sub});
            }
            case "remember" -> {
                String k = slug(rest);
                if (k.isEmpty()) { p.sendMessage("Usage: /helper remember <name>"); return; }
                conf.set(path(id, "places." + k), spot(p.getLocation()));
                save();
                say(p, "I will remember this place as " + k.replace('_', ' ') + ".");
            }
            case "forget" -> {
                String k = slug(rest);
                boolean had = !k.isEmpty() && conf.contains(path(id, "places." + k));
                conf.set(path(id, "places." + k), null);
                save();
                say(p, had ? "Forgotten." : "I don't know a place called " + rest + ".");
            }
            case "places" -> {
                ConfigurationSection sec = conf.getConfigurationSection(path(id, "places"));
                List<String> known = sec == null ? new ArrayList<>() : new ArrayList<>(sec.getKeys(false));
                if (home(id) != null) known.add(0, "home");
                say(p, known.isEmpty() ? "I don't remember any places yet. Say: remember this place as the mine." : "I remember: " + String.join(", ", known).replace('_', ' ') + ".");
            }
            case "goto" -> {
                Location l = placeNamed(id, rest);
                if (l == null) { say(p, "I don't know a place called " + rest + "."); return; }
                travel(p, h, l);
                say(p, "Here we are!");
            }
            case "skin" -> {
                if (args.length < 2) { p.sendMessage("Usage: /helper skin <skin from skins/ or a Minecraft player name>"); return; }
                String want = String.join("_", Arrays.copyOfRange(args, 1, args.length));
                String s = Skins.slug(want);
                if (skins.packProfile(s) != null) conf.set(path(id, "skin"), "pack:" + s);
                else if (args.length == 2 && args[1].matches("[A-Za-z0-9_]{3,16}")) conf.set(path(id, "skin"), args[1]);
                else {
                    List<String> have = skins.names();
                    p.sendMessage("No skin called " + want + ". " + (have.isEmpty() ? "No skins yet" : "Skins: " + String.join(", ", have))
                            + ". New PNG files in skins/ on the server are ready about 20 s after you save them. A Minecraft player name (3-16 letters) works too.");
                    return;
                }
                save();
                if (h != null) h.m.setProfile(profile(p));
                if (skins.packProfile(s) != null && skins.signed(s) == null) plugin.getLogger().info("HELPER_SIGN " + p.getName() + "|" + s);   // ready for the next merge
                if (h != null && h.merged) wearSkin(p, false);
                say(p, look(want));
            }
            case "name" -> {
                String name = rest.replace('|', ' ').trim();
                if (name.length() < 2) { p.sendMessage("Usage: /helper name <name> (at least two letters)"); return; }
                if (name.length() > 32) name = name.substring(0, 32);
                conf.set(path(id, "name"), name);
                save();
                names.put(id, name.toLowerCase(Locale.ROOT));
                if (h != null) h.m.customName(title(p));
                p.sendMessage(Component.text("Your helper is now called " + name + ". Say \"" + name + "\" in chat and it comes.", NamedTextColor.GOLD));
            }
            case "dismiss", "off" -> {
                conf.set(path(id, "active"), false);
                save();
                despawn(id, true);
                p.sendMessage(Component.text("Your helper went home. /helper brings it back.", NamedTextColor.GOLD));
            }
            case "say" -> { if (!rest.isEmpty()) say(p, rest, false); }
            case "personality" -> say(p, personality(id, rest));
            case "buildplan" -> buildPlan(id, said -> say(p, said));
            case "ask" -> talk(p, rest);
            default -> talk(p, String.join(" ", args));
        }
    }
}
