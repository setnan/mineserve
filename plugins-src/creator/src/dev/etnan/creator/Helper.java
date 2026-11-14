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
    final Orders orders = new Orders(this);
    final AwayWork awayWork = new AwayWork(this);
    final Builder builder = new Builder(this);
    final Explorer explorer = new Explorer(this);
    static final double DAMAGE = 20;       // per bite/hit: most monsters die in one or two
    static final int HOME = 100;           // chests, furnaces, fields and water this close to home belong to home
    static final int GUARD_RANGE = 16;     // monsters this close to the owner (or the guard post) get attacked
    static final int VEIN_MAX = 128;       // blocks in one /helper mine
    static final int FARM_RANGE = 8;
    static final EquipmentSlot[] GEAR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.HAND, EquipmentSlot.OFF_HAND};
    static final Material[] TOOLS = {Material.NETHERITE_PICKAXE, Material.NETHERITE_SHOVEL, Material.NETHERITE_AXE, Material.SHEARS};
    /** The helper's own tools have the best there is on them: fortune and luck count for what it brings back. */
    static ItemStack ench(Material m) {
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

    static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
    static final Map<Material, Material> SEEDS = Map.of(Material.WHEAT, Material.WHEAT_SEEDS, Material.CARROTS, Material.CARROT,
            Material.POTATOES, Material.POTATO, Material.BEETROOTS, Material.BEETROOT_SEEDS, Material.NETHER_WART, Material.NETHER_WART);
    static final Pattern HAND_GEAR = Pattern.compile(".*_(SWORD|AXE|PICKAXE|SHOVEL|HOE|SPEAR)|TRIDENT|MACE|BOW|CROSSBOW|FISHING_ROD");

    /** What the helper understands without the chat bridge: {subcommand, whole-sentence pattern}. English and Norwegian. */
    static final String[][] WORDS = {
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
    static final Pattern SKIN_WORDS = Pattern.compile("(?:skin|look like|looks like|ser ut som)\\s+(.{1,40})");
    static final Pattern NAME_WORDS = Pattern.compile("(?:name|your name is|du heter|heter|navn)\\s+(.{1,32})");
    static final Pattern FETCH_WORDS = Pattern.compile("(?:fetch|get|get me|bring|bring me|give me|hent|gi meg)\\s+(.{1,40})");
    static final Pattern FIND_WORDS = Pattern.compile("(?:find|locate|where is|where are|finn|hvor er)\\s+(?:(?:the|a|an|some|nearest|closest|nærmeste|en|et)\\s+)*(.{1,40})");
    static final Pattern NUMBER = Pattern.compile("\\b(\\d{1,4})\\b");
    static final Pattern HELP_WORDS = Pattern.compile("(?:help|protect|look after|take care of|hjelp|beskytt|pass på|se etter)\\s+(\\w{3,16})");
    static final Pattern JOIN_WORDS = Pattern.compile("(?:join|work with|bli med|samarbeid med|jobb med|jobb sammen med)\\s+(\\w{3,16})");
    static final Pattern CRAFT_WORDS = Pattern.compile("(?:craft|craft me|make|make me|lag|lag meg)\\s+(.{1,40})");
    static final Pattern SMELT_WORDS = Pattern.compile("(?:smelt|cook|stek|kok)\\s+(.{1,40})");
    static final Pattern REMEMBER_WORDS = Pattern.compile("(?:remember (?:this place|this|here) as|call this place|husk (?:dette stedet|stedet|dette|her) som|kall dette stedet|kall stedet)\\s+(.{1,30})");
    static final Pattern GOTO_WORDS = Pattern.compile("(?:take me to|bring me to|go to|teleport to|ta meg til|gå til|dra til|til)\\s+(.{1,30})");
    static final Pattern ARTICLE = Pattern.compile("^(?:the|a|an|some|en|et|ei|noen)\\s+");
    static final Set<Material> FUELS = Set.of(Material.COAL, Material.CHARCOAL, Material.COAL_BLOCK, Material.BLAZE_ROD, Material.LAVA_BUCKET, Material.DRIED_KELP_BLOCK);
    static final Pattern COLLECT_WORDS = Pattern.compile("(?:go to|gå til|dra til)\\s+(.{1,30}?)\\s+(?:and|og)\\s+(?:fetch|get|bring|collect|hent|ta med)\\s+(?:everything|it all|all|alt|alt sammen)");
    static final Pattern TRADE_WORDS = Pattern.compile("(?:trade for|buy|buy me|kjøp|kjøp meg)\\s+(.{1,40})");
    static final Pattern GIVE_WORDS = Pattern.compile("(?:give|bring|send|gi|send)\\s+(?:this|it|dette|denne|den)\\s+(?:to|til)\\s+(\\w{3,16})");
    static final Pattern ROUTINE_WORDS = Pattern.compile("(?:every|hver)\\s+(morning|noon|evening|night|morgen|middag|kveld|natt)\\s*[:,]?\\s*(.{2,120})");
    static final Pattern EVERY_WORDS = Pattern.compile("(?:every|hvert|hver)\\s+(\\d{1,3})\\.?\\s*min\\w*\\s*[:,]?\\s*(.{2,120})");
    static final Pattern UNROUTINE_WORDS = Pattern.compile("(?:remove|delete|fjern|slett)\\s+(?:routine|rutine)\\s+(\\d{1,2})");
    static final Map<String, Integer> TIMES = Map.of("morning", 500, "noon", 6000, "evening", 11500, "night", 13500);
    static final Map<String, String> TIME_WORDS = Map.of("morgen", "morning", "middag", "noon", "kveld", "evening", "natt", "night");
    static final Pattern CALLME_WORDS = Pattern.compile("(?:call me|kall meg)\\s+(.{1,24})");
    /** What two helpers say when they meet: {first, answer}; {a} and {b} are their names, {oa} and {ob} their owners. */
    static final String[][] BANTER = {
        {"Hi {b}! Still carrying everything for {ob}?", "Hi {a}! Someone has to. You look good today."},
        {"{b}, I bet I can beat more zombies than you.", "You are on, {a}. Loser carries the cobblestone."},
        {"Nice to see you, {b}!", "You too, {a}! Did you get taller, or is it just the hair?"},
        {"{b}, race you to the next tree?", "Only if you promise not to sulk when I win, {a}."},
        {"Good work today, {b}.", "Thanks, {a}. You were almost as good as me."},
        {"{b}! I saved you a spot.", "You are the best, {a}. Don't tell {oa} I said that."},
        {"Psst, {b}. I think {oa} is lost again.", "Ha! {ob} too. Good thing they have us, {a}."},
    };
    static final Pattern POLITE = Pattern.compile("^(?:please|can you|could you|kan du|vær så snill)\\s+|\\s+(?:please|takk)$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    enum Stance { FOLLOW, STAY, GUARD }
    enum Job { NONE, MINE, FARM, FISH }

    /** One helper that is in the world right now. */
    static final class H {
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

    record Bag(UUID owner) implements InventoryHolder {
        @Override
        public Inventory getInventory() { return null; }
    }

    final JavaPlugin plugin;
    final Skins skins;
    final File file;
    final YamlConfiguration conf;
    final NamespacedKey key, speedKey;
    final Map<UUID, H> helpers = new HashMap<>();
    final Map<UUID, Inventory> bags = new HashMap<>();
    final Map<UUID, ItemStack[]> gear = new HashMap<>();
    final Set<UUID> dirty = new HashSet<>();                       // bags changed since the last save
    final Map<UUID, String> names = new ConcurrentHashMap<>();     // lower-case helper names, read by the chat thread
    final Map<UUID, Map<String, Integer>> awayGot = new HashMap<>();   // what it got since the last line in the log, by kind
    final Map<String, Long> awayTold = new HashMap<>();            // warnings and meetings already in the log
    static final List<String> AWAY_JOBS = List.of("guard", "fish", "farm", "smelt", "mine", "explore", "home", "mobfarm");
    static final Map<String, String> ORE_WORDS = Map.ofEntries(Map.entry("diamant", "DIAMOND"), Map.entry("diamanter", "DIAMOND"), Map.entry("kull", "COAL"),
            Map.entry("jern", "IRON"), Map.entry("gull", "GOLD"), Map.entry("kobber", "COPPER"), Map.entry("smaragd", "EMERALD"), Map.entry("smaragder", "EMERALD"),
            Map.entry("kvarts", "QUARTZ"), Map.entry("netherite", "ANCIENT_DEBRIS"), Map.entry("debris", "ANCIENT_DEBRIS"), Map.entry("obsidian", "OBSIDIAN"));
    final Map<UUID, Deque<Block>> awayOre = new HashMap<>();       // ore it has seen and not dug out yet
    final Map<UUID, Integer> awayScan = new HashMap<>();           // how far out it has looked for ore
    final Set<UUID> talking = ConcurrentHashMap.newKeySet();       // talk mode: all their chat goes to the helper
    final Map<UUID, Long> asked = new HashMap<>();
    final Map<UUID, Deque<Object[]>> rubble = new HashMap<>();     // owner -> {Block, BlockData} a creeper blew up near home
    final Map<UUID, Long> rubbleAt = new HashMap<>();
    final Map<UUID, Long> buried = new HashMap<>();                // when each player last got a treasure
    int tick;

    Storage storage;                                              // the magic storage barrels: unload can use your own store there
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
    Location myBarrel(UUID id, World w) {
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

    String path(UUID id, String k) { return id + "." + k; }

    boolean active(Player p) { return conf.getBoolean(path(p.getUniqueId(), "active")); }

    boolean flag(UUID id, String k, boolean def) { return conf.getBoolean(path(id, k), def); }

    void save() {
        try { conf.save(file); } catch (IOException e) { plugin.getLogger().warning("Could not save helpers.yml: " + e.getMessage()); }
    }

    /** A monster the helper fights or warns about. Piglins (you trade gold with them) and zombified piglins are
     *  left alone until they go for a player or a helper; piglin brutes are always foes. */
    boolean foe(Entity e) {
        if (!(e instanceof Enemy) || !(e instanceof LivingEntity le) || le.isDead() || ownerOf(e) != null) return false;
        if (e instanceof Piglin || e instanceof PigZombie) {
            LivingEntity t = ((Mob) e).getTarget();
            return t != null && !t.isDead() && (t instanceof Player || ownerOf(t) != null);
        }
        return true;
    }

    UUID ownerOf(Entity e) {
        String s = e.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return s == null ? null : UUID.fromString(s);
    }

    final Set<String> signAsked = new HashSet<>();

    ResolvableProfile profile(Player owner) {
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

    String name(UUID id) { return conf.getString(path(id, "name"), "Helper"); }

    Component title(Player owner) {
        return Component.text(name(owner.getUniqueId()));      // plain white, like a player's
    }

    static void bar(Player p, String text) { p.sendActionBar(Component.text(text, NamedTextColor.GOLD)); }

    /** The helper talks: looks like a chat line from it, only the owner gets it. */
    void say(Player p, String text) {
        say(p, text, true);
    }

    /** {@code own}: a line of the plugin's, which gets a touch of the personality (what the model answers has it already). */
    void say(Player p, String text, boolean own) {
        if (own) text = voice(p.getUniqueId(), text);
        p.sendMessage(Component.text("<" + name(p.getUniqueId()) + "> " + text));
        awayLog(p.getUniqueId(), "says: " + text);                        // the server log shows what it is up to
    }

    /** A comment at most once per so many seconds, and not at all when the owner has asked for quiet. */
    void remark(Player p, H h, String kind, int seconds, String... lines) {
        if (flag(p.getUniqueId(), "quiet", false)) return;
        long now = System.currentTimeMillis();
        if (now - h.said.getOrDefault(kind, 0L) < seconds * 1000L) return;
        h.said.put(kind, now);
        say(p, lines[ThreadLocalRandom.current().nextInt(lines.length)]);
    }

    // --- Spawning ------------------------------------------------------------------------------------

    void spawn(Player p, Location at) {
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

    static void set(LivingEntity e, Attribute a, double v) {
        var i = e.getAttribute(a);
        if (i != null) i.setBaseValue(v);
    }

    void despawn(UUID id, boolean poof) {
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
        builder.saveSites();                                           // a building in progress goes on after the restart
        for (UUID id : new ArrayList<>(helpers.keySet())) despawn(id, false);
        rebuild(true);
        dirty.addAll(bags.keySet());
        flushBags();
        save();                                                // also the counters of work done while owners are away
    }

    // --- Every tick: the figure follows the wolf; twice a second: decide what to do --------------------

    void tick() {
        tick++;
        if (tick % 20 == 0)                                   // (re)spawn for players who should have one
            for (Player p : Bukkit.getOnlinePlayers())
                if (active(p) && !helpers.containsKey(p.getUniqueId())) spawn(p, null);
        if (tick % 600 == 0) flushBags();
        if (tick == 100) builder.loadSites();                         // the worlds are loaded by now
        if (tick % 200 == 0) awayWork.away();
        if (tick % 100 == 0) banter();
        if (tick % 2 == 0 && !rubble.isEmpty()) rebuild(false);
        for (UUID id : new ArrayList<>(helpers.keySet())) {
            Player p = Bukkit.getPlayer(id);
            H h = helpers.get(id);
            if (p == null) {                                   // logged out: only a helper on watch stays, while someone is online
                if (!h.watching || !h.w.isValid() || !h.m.isValid()) { despawn(id, false); continue; }
                if (tick % 10 == 0 && !paused()) guard(id, h);
                if (tick % 20 == 0 && !paused()) { shut(h.w.getLocation(), 4, h.w); awayWork.awayMeet(id, h.w.getLocation()); }
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

    static double dist2(Entity a, Location b) {
        return a.getWorld() != b.getWorld() ? Double.MAX_VALUE : a.getLocation().distanceSquared(b);
    }

    static boolean night(World w) {
        return w.getEnvironment() == World.Environment.NORMAL && !w.isDayTime();
    }

    void think(Player p, H h) {
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
        if ((h.stance != Stance.FOLLOW || h.job != Job.NONE) && far > 64 * 64 && !builder.sites.containsKey(id)) {      // the owner left: drop everything
            boolean worked = h.job != Job.NONE;
            release(h);
            if (worked) say(p, "You went far away, so I stopped and came after you.");
        }
        if (tick % 20 == 0 && !h.merged && far > 12 * 12) {
            shut(w.getLocation(), 4, w);
        }      // on its own: doors and gates shut behind it
        if (tick % 40 == 0) awayWork.awayMeet(id, w.getLocation());
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
            awayWork.sortFarm(id, h.anchor);
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
    void walk(H h, Location to, double d2, double keep, double speed) {
        if (d2 > keep * keep) {
            h.w.getPathfinder().moveTo(to, speed);
            h.moving = true;
        } else if (h.moving && d2 < (keep - 1.5) * (keep - 1.5)) {
            h.w.getPathfinder().stopPathfinding();
            h.moving = false;
        }
    }

    /** Holds its post: fights what comes within range of it and walks back afterwards. Works with the owner offline. */
    void guard(UUID id, H h) {
        Wolf w = h.w;
        boolean busy = w.getTarget() != null && !w.getTarget().isDead();
        if (busy && dist2(w.getTarget(), h.anchor) > (GUARD_RANGE + 8) * (GUARD_RANGE + 8)) { w.setTarget(null); busy = false; }
        if (!busy) protect(id, h, h.anchor);
        double d = dist2(w, h.anchor);
        if (d > 32 * 32) w.teleport(h.anchor);
        else if (!busy && w.getTarget() == null) walk(h, h.anchor, d, 4, 1.2);
    }

    void protect(UUID id, H h, Location around) {
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
    boolean swordReach(H h) {
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
    void release(H h) {
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

    String nick(Player p) { return conf.getString(path(p.getUniqueId(), "nick"), p.getName()); }

    /** "behind you", "on your left" …: where something is, seen from where the player is looking. */
    static String side(Player p, Entity e) {
        Vector to = e.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
        Vector f = p.getLocation().getDirection().setY(0);
        if (to.lengthSquared() < 0.01 || f.lengthSquared() < 0.01) return "right here";
        to.normalize();
        f.normalize();
        double dot = f.dot(to), cross = f.getX() * to.getZ() - f.getZ() * to.getX();
        return dot > 0.7 ? "ahead" : dot < -0.7 ? "behind you" : cross > 0 ? "on your right" : "on your left";
    }

    void comments(Player p, H h) {
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
    boolean habits(Player p, H h) {
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
    void banter() {
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
    String context(Player p) {
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

    Inventory bag(UUID id) {
        return bags.computeIfAbsent(id, k -> {
            Inventory inv = Bukkit.createInventory(new Bag(id), 27, Component.text("Helper bag"));
            ItemStack[] items = load(id, "bag");
            if (items != null) for (int i = 0; i < Math.min(items.length, 27); i++) inv.setItem(i, items[i]);
            return inv;
        });
    }

    // --- One bag and one set of gear for each group of worlds, as the players have (Multiverse-Inventories) ---------

    long groupsAt = -1;
    final Map<String, List<String>> groups = new java.util.LinkedHashMap<>();

    /** The inventory group a world belongs to; a world in no group is a group of its own. */
    String group(World w) {
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
    String grp(UUID id) {
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

    String inv(UUID id, String k) { return k.equals("bag") || k.equals("gear") ? "inv." + grp(id) + "." + k : k; }

    /** Its owner is in another group of worlds now: what it carried and wore stays behind, and what it had here is back. */
    void regroup(UUID id, World w) {
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
    Location homeNow(UUID id) {
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

    ItemStack[] load(UUID id, String k) {
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

    void store(UUID id, String k, ItemStack[] items) {
        conf.set(path(id, inv(id, k)), Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(items)));
    }

    void flushBags() {
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
    Map<Integer, ItemStack> stash(Player p, H h, ItemStack... items) {
        Map<Integer, ItemStack> rest = new HashMap<>();
        List<ItemStack> left = new ArrayList<>();
        if (h.merged) for (ItemStack r : p.getInventory().addItem(items).values()) left.add(r);
        else left.addAll(Arrays.asList(items));
        if (left.isEmpty()) return rest;
        dirty.add(p.getUniqueId());
        return bag(p.getUniqueId()).addItem(left.toArray(new ItemStack[0]));
    }

    /** Puts items in the bag (merged: in your inventory first); what does not fit is dropped at the helper's feet. */
    void keep(Player p, H h, Collection<ItemStack> items) {
        if (items.isEmpty()) return;
        dirty.add(p.getUniqueId());
        for (ItemStack rest : stash(p, h, items.toArray(new ItemStack[0])).values()) {
            h.w.getWorld().dropItem(h.w.getLocation(), rest);
            remark(p, h, "full", 60, "My bag is full!", "I can't carry any more.");
        }
    }

    /** Takes what lies within three blocks (the whole field while farming). Never something a player threw down: that is theirs. */
    void pickUp(Player p, H h) {
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

    void torch(Wolf w) {
        Block b = w.getLocation().getBlock();
        if (!b.getType().isAir() || b.getLightLevel() > 7 || !b.getRelative(BlockFace.DOWN).getType().isOccluding()) return;
        b.setType(Material.TORCH);
        b.getWorld().playSound(b.getLocation(), Sound.BLOCK_WOOD_PLACE, 0.6f, 1f);
    }

    // --- Home, chests: emptying the bag and fetching things ---------------------------------------------------

    Location home(UUID id) {
        return place(conf.getString(path(id, "home")));
    }

    static String spot(Location l) {
        return l.getX() + " " + l.getY() + " " + l.getZ() + " " + l.getYaw() + " " + l.getWorld().getName();
    }

    static Location place(String s) {
        if (s == null) return null;
        String[] a = s.split(" ", 5);
        World w = a.length < 5 ? null : Bukkit.getWorld(a[4]);
        return w == null ? null : new Location(w, Double.parseDouble(a[0]), Double.parseDouble(a[1]), Double.parseDouble(a[2]), Float.parseFloat(a[3]), 0);
    }

    /** The chests, barrels and shulker boxes within eight blocks of a place (the chunks are loaded if they are not). */
    static List<Inventory> stores(Location around) { return stores(around, 8); }

    static List<Inventory> stores(Location around, int r) {
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

    void zip(Player p, H h) {                                      // it is gone and back in a blink
        Location l = h.w.getLocation().add(0, 1, 0);
        p.spawnParticle(Particle.PORTAL, l, 40, 0.3, 0.6, 0.3, 0.4);
        p.playSound(l, Sound.ENTITY_ENDERMAN_TELEPORT, 0.3f, 1.6f);
    }

    /** Moves everything in one inventory into the given chests, to a chest that already has that kind first.
     *  Returns how many things moved. */
    static int store(Inventory from, List<Inventory> to) {
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
    List<Inventory> awayChests(Location here, List<Inventory> home) {
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
    boolean unload(Player p, H h, boolean auto) {
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
    static final int NEAR_PLACE = 24;                                // how far around a remembered place its chests count

    static final class Errand {
        UUID id; Location at; List<Inventory> from; Material want; int amount, phase, ticks; String where; boolean put, mine;
        final List<ItemStack> load = new ArrayList<>();
    }

    static Location standBy(Location chest) {
        Block c = chest.getBlock();
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}}) for (int dy = 0; dy >= -1; dy--) {
            Block b = c.getRelative(d[0], dy, d[1]);
            if (b.isPassable() && b.getRelative(BlockFace.UP).isPassable() && b.getRelative(BlockFace.DOWN).getType().isSolid()) return b.getLocation().add(0.5, 0, 0.5);
        }
        return c.getLocation().add(0.5, 1, 0.5);
    }

    void lid(Location chest, boolean open) {
        if (chest.getBlock().getState() instanceof org.bukkit.block.Lidded l) { if (open) l.open(); else l.close(); }
    }

    /** A world and its own nether and end belong together: hope, hope_nether, hope_the_end. */
    static String realm(World w) { return w.getName().replaceFirst("_(nether|the_end)$", ""); }

    /** Home, when it lies in this world or in the overworld, nether or end that belongs to it. */
    Location homeLinked(UUID id, World w) {
        Location hm = home(id);
        return hm != null && hm.getWorld() != null && realm(hm.getWorld()).equals(realm(w)) ? hm : null;
    }

    /** Home, when it lies in this world: nothing is carried from one world to another. */
    Location homeIn(UUID id, World w) {
        Location hm = home(id);
        return hm != null && hm.getWorld() == w ? hm : null;
    }

    void errand(Player p, H h) {
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
    boolean fetch(Player p, H h, String what, boolean quiet) {
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

    static String slug(String name) {
        return ARTICLE.matcher(name.trim().toLowerCase(Locale.ROOT)).replaceFirst("").replaceAll("[^\\p{L}\\p{N}]+", "_").replaceAll("^_|_$", "");
    }

    /** The saved place that fits the name best: the same name, or one that contains the other. "home" is home. */
    Location placeNamed(UUID id, String name) {
        String want = slug(name);
        if (want.isEmpty()) return null;
        ConfigurationSection sec = conf.getConfigurationSection(path(id, "places"));
        if (sec != null) {
            if (sec.contains(want)) return place(sec.getString(want));
            for (String k : sec.getKeys(false)) if (k.contains(want) || want.contains(k)) return place(sec.getString(k));
        }
        return want.equals("home") || want.equals("hjem") ? home(id) : null;
    }

    void travel(Player p, H h, Location to) {
        release(h);
        zip(p, h);
        p.teleport(to);
        h.w.teleport(to);
    }

    // --- Fighting style, scouting, the way back, games, treasure ---------------------------------------------

    String style(UUID id) { return conf.getString(path(id, "style"), "sword"); }

    /** Bow: an arrow a second at its foe, from where it stands. Shield: what hunts the owner hunts the helper instead. */
    void fightStyle(Player p, H h) {
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
    void scout(Player p, H h) {
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
    void crumbs(Player p, H h) {
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

    void wayback(Player p, H h) {
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
    static Location spotNear(Player p, int min, int max) {
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

    void endGame(H h) {
        if (h.game == null) return;
        h.game = null;
        h.w.setSitting(false);
        h.m.setCustomNameVisible(true);
    }

    void startGame(Player p, H h, boolean hide) {
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

    void game(Player p, H h) {
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

    void startTreasure(Player p, H h) {
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

    String clue(Player p, H h) {
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

    void clues(Player p, H h) {
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
    int shut(Location around, int r, Entity self) {
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

    boolean paused() { return conf.getBoolean("settings.paused", false); }

    /** "mending, unbreaking 3" for an enchanted book or thing, null for plain things. */
    static String magic(ItemStack it) {
        if (empty(it) || !it.hasItemMeta()) return null;
        Map<org.bukkit.enchantments.Enchantment, Integer> en = it.getItemMeta() instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta b ? b.getStoredEnchants() : it.getEnchantments();
        if (en.isEmpty()) return null;
        List<String> out = new ArrayList<>();
        en.forEach((e, lvl) -> out.add(e.getKey().getKey().replace('_', ' ') + (e.getMaxLevel() > 1 ? " " + lvl : "")));
        return String.join(", ", out);
    }

    /** Keeps count of what came in while the owner is away; enchanted things get a line of their own in the log. */
    void awayNote(UUID id, Collection<ItemStack> items) {
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

    static final Set<Material> TREASURE = Set.of(Material.NAME_TAG, Material.SADDLE, Material.NAUTILUS_SHELL, Material.DIAMOND, Material.EMERALD,
            Material.ANCIENT_DEBRIS, Material.HEART_OF_THE_SEA, Material.TOTEM_OF_UNDYING, Material.ENCHANTED_GOLDEN_APPLE, Material.GOLDEN_APPLE);

    /** Something is in the way of the work: one line in the log, once per time away. */
    void awayWarn(UUID id, String key, String text) {
        if (awayTold.putIfAbsent(id + ":warn:" + key, System.currentTimeMillis()) == null) awayLog(id, "NOTE: " + text);
    }

    void awayLog(UUID id, String text) {
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

    int addToBag(UUID id, Collection<ItemStack> items) {
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

    /** What the helper earns in experience is its owner's: given at once, or kept until they log in. */
    void xp(UUID id, int n) {
        if (n <= 0) return;
        Player p = Bukkit.getPlayer(id);
        if (p != null) { p.giveExp(n); return; }
        conf.set(path(id, "xp"), conf.getInt(path(id, "xp"), 0) + n);
        bump(id, "xp", n);
    }

    void bump(UUID id, String k, int n) {
        if (n > 0) conf.set(path(id, "away." + k), conf.getInt(path(id, "away." + k), 0) + n);
    }

    static boolean natural(Material m) {
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

    static Material mat(String name) { return Material.matchMaterial(name); }

    // --- Anything it is asked to build: the chat bridge's model draws it (layers of letters), the helper builds it by hand ---

    // --- Buildings in progress survive a restart: saved on shutdown, taken up again five seconds after the start ---

    /** What kind of loot something is: each kind gets chests of its own at the mob farm. */
    static String lootKind(ItemStack it) {
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

    /** The saved places that are mob farms. */
    List<String> farms(UUID id) {
        List<String> names = new ArrayList<>();
        for (String n : conf.getStringList(path(id, "mobfarms"))) if (conf.contains(path(id, "places." + n)) && !names.contains(n)) names.add(n);
        return names;
    }

    void farmAdd(UUID id, String name) {
        List<String> names = farms(id);
        if (!names.contains(name)) { names.add(name); conf.set(path(id, "mobfarms"), names); }
    }

    /** Which mob farm is meant: the one named, the only one there is, or (starting with ?) a question back. */
    String farmPick(UUID id, String arg) {
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
    void mobfarm(Player p, H h, String arg) {
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
        awayWork.mobYards.remove(id);
        if (h.w.getWorld() != post.getWorld() || h.w.getLocation().distanceSquared(post) > 2.25) h.w.teleport(post);
        say(p, (made ? "This post is mob farm " + name + " now. " : "I run mob farm " + name + ". ") + "I stay outside, strike what I can reach with my sword and sort what lands in the chests every minute. I keep at it when you log out. Say stop when I should come with you.");
    }

    static final EntityType[] MOBS = {EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SKELETON, EntityType.CREEPER, EntityType.SPIDER};

    /** A golden chicken it has not seen before: its owner hears of it (at once, or in the finds when they are away). */
    void goldSeen(UUID id, org.bukkit.entity.Chicken c, String how) {
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

    static final Map<EntityType, Material> MEAT = Map.of(EntityType.COW, Material.BEEF, EntityType.PIG, Material.PORKCHOP, EntityType.SHEEP, Material.MUTTON, EntityType.CHICKEN, Material.CHICKEN);
    static final Material[] FODDER = {Material.WHEAT, Material.CARROT, Material.POTATO, Material.BEETROOT, Material.WHEAT_SEEDS, Material.BEETROOT_SEEDS, Material.MELON_SEEDS, Material.PUMPKIN_SEEDS};

    static final String[][] LOOKS = {
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
    static final String[] LOOK_ANY = {"New look! What do you think?", "Oh, I like this one. Does it suit me?", "A change of clothes does wonders. Ready when you are.", "Ta-da! Still me, just better dressed."};

    /** What it says about its new skin. */
    static String look(String skin) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String k = skin.toLowerCase(Locale.ROOT);
        for (String[] l : LOOKS) if (Pattern.compile(l[0]).matcher(k).find()) return l[1 + r.nextInt(l.length - 1)];
        return LOOK_ANY[r.nextInt(LOOK_ANY.length)];
    }

    // --- Personality -------------------------------------------------------------------------------
    /** name, what it is like (for the model that answers in game), openers, closers (for the fixed lines). */
    static final String[][] PERSONAS = {
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
    static final Map<String, String> PERSONA_ALIAS = Map.ofEntries(Map.entry("thoughtfull", "thoughtful"), Map.entry("toughtfull", "thoughtful"), Map.entry("genious", "genius"),
            Map.entry("inlove", "loving"), Map.entry("love", "loving"), Map.entry("negative", "grumpy"), Map.entry("shy", "timid"), Map.entry("happy", "positive"),
            Map.entry("smart", "genius"), Map.entry("modig", "brave"), Map.entry("morsom", "funny"), Map.entry("sur", "grumpy"), Map.entry("sjenert", "timid"),
            Map.entry("rolig", "calm"), Map.entry("nysgjerrig", "curious"), Map.entry("pirat", "pirate"), Map.entry("høflig", "polite"), Map.entry("trøtt", "sleepy"));

    static String[] persona(String name) {
        for (String[] p : PERSONAS) if (p[0].equals(name)) return p;
        return null;
    }

    /** "personality brave funny", "personality reset", "personality": sets, clears or shows it. At most two. */
    String personality(UUID id, String rest) {
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
    String voice(UUID id, String text) {
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
    String personaNote(UUID id) {
        StringBuilder sb = new StringBuilder();
        for (String n : conf.getStringList(path(id, "personality"))) { String[] p = persona(n); if (p != null) sb.append(sb.length() == 0 ? "" : "; ").append(p[0]).append(" (").append(p[1]).append(")"); }
        return sb.length() == 0 ? "" : " Your personality: " + sb + ".";
    }

    static final String[] JOKES = {
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
    final Map<UUID, Integer> lastJoke = new HashMap<>();

    String joke(UUID id) {
        int n;
        do { n = ThreadLocalRandom.current().nextInt(JOKES.length); } while (Integer.valueOf(n).equals(lastJoke.get(id)));
        lastJoke.put(id, n);
        return JOKES[n];
    }

    /** A short answer of its own to an order it cannot follow while its owner is away. */
    String shrug(UUID id, String words) {
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
    String doing(UUID id) {
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

    // --- Chores: tidying, mending, errands, trading, deliveries, routines, fixing creeper holes --------------------

    static int count(List<Inventory> in, Predicate<Material> kind) {
        int n = 0;
        for (Inventory inv : in) for (ItemStack it : inv.getStorageContents()) if (!empty(it) && kind.test(it.getType())) n += it.getAmount();
        return n;
    }

    static void take(List<Inventory> in, Predicate<Material> kind, int n) {
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

    void hand(UUID id, Player p, ItemStack it) {
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

    static String word(Material m) { return m.name().toLowerCase(Locale.ROOT).replace('_', ' '); }

    /** Every kind of thing ends up in the home chest that already has the most of it. Returns how many things were
     *  moved, or -1 when there are not two chests at home. */
    int tidy(UUID id) {
        Location hm = homeNow(id);
        return hm == null ? -1 : tidy(stores(hm, HOME));
    }

    int tidyStuck;                                                  // after tidy(): things left in a chest they do not belong in

    static String coarse(ItemStack it) {
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
    static String fine(ItemStack it) {
        String c = coarse(it), n = it.getType().name();
        if (c.equals("armour") || c.equals("tools") || c.equals("weapons"))
            for (String of : new String[] {"GOLDEN", "LEATHER", "IRON", "CHAINMAIL", "COPPER", "DIAMOND", "NETHERITE", "STONE", "WOODEN"}) if (n.startsWith(of + "_")) return of.toLowerCase(Locale.ROOT) + " " + c;
        return c;
    }

    static boolean fits(String theme, ItemStack it) {
        return theme == null || (theme.startsWith("=") ? it.getType().name().equals(theme.substring(1)) : fine(it).equals(theme) || coarse(it).equals(theme));
    }

    /** What a chest is for: the thing in an item frame on it, or else what at least six in ten of its stacks are
     *  (first the narrow kind, like golden armour, then the broad one, like armour). Null: a chest for anything. */
    static String theme(Inventory inv) {
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
    int tidy(List<Inventory> invs) {
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
    static Predicate<Material> mendWith(Material m) {
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

    static String mendName(Material m) {
        String n = m.name();
        return n.startsWith("WOODEN_") ? "planks" : n.startsWith("IRON_") || n.startsWith("CHAINMAIL_") || n.equals("SHEARS") || n.equals("SHIELD") || n.equals("FLINT_AND_STEEL") ? "iron ingots"
                : n.startsWith("DIAMOND_") || n.startsWith("NETHERITE_") ? "diamonds" : n.startsWith("GOLDEN_") ? "gold ingots" : n.startsWith("COPPER_") ? "copper ingots"
                : n.startsWith("STONE_") ? "cobblestone" : n.startsWith("LEATHER_") ? "leather" : n.equals("ELYTRA") ? "phantom membranes" : "string";
    }

    void repair(Player p, H h) {
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
    void collect(Player p, H h, String where) {
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
    boolean trade(Player p, H h, String what, boolean quiet) {
        return trade(p.getUniqueId(), p, p.getLocation(), what, quiet, t -> say(p, t));
    }

    /** {@code p} is null when the owner is logged out: then the villagers at home, paid from the bag and the chests there. */
    boolean trade(UUID id, Player p, Location at, String what, boolean quiet, Consumer<String> tell) {
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

    List<String> routineList(UUID id) { return new ArrayList<>(conf.getStringList(path(id, "routines"))); }

    /** Standing orders: at a time of day (when the world clock passes it) or every so many minutes. Each order in a
     *  routine is said to the helper as if the owner said it, fifteen seconds apart. */
    void routines(Player p, H h) {
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
                Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline() && helpers.containsKey(p.getUniqueId())) this.orders.talk(p, order); }, 1L + i * 300L);
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
    void rebuild(boolean all) {
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

    static List<RecipeChoice> ingredients(Recipe r) {
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

    static String describe(List<RecipeChoice> need) {
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
    boolean craft(Player p, H h, String what, boolean quiet) {
        return craft(p.getUniqueId(), p, h.w.getLocation(), what, quiet, t -> say(p, t));
    }

    /** The bed there is wool for (three of one colour). When there is too little, the sheep within 48 blocks are sheared. */
    Material bedFor(UUID id, Player p, Location here) {
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
    boolean craft(UUID id, Player p, Location here, String what, boolean quiet, Consumer<String> tell) {
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

    static List<FurnaceInventory> furnaces(Location around) { return furnaces(around, 8); }

    static List<FurnaceInventory> furnaces(Location around, int r) {
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
    void smelt(Player p, H h, String only) {
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
                if (noFuel && storage != null) {                       // from your own store in the magic barrel
                    ItemStack one = storage.takeMine(id, m -> FUELS.contains(m) && m != Material.LAVA_BUCKET, 8);
                    if (one != null) { f.setFuel(one); noFuel = false; }
                }
            }
        }
        dirty.add(id);
        zip(p, h);
        say(p, (took + put == 0 ? "Nothing was done yet, and I have nothing to put in." : "I took out " + took + " and put in " + put + ".")
                + (noFuel ? " A furnace has no fuel: give me coal, charcoal, blaze rods or a lava bucket, or put some in your magic barrel." : ""));
    }

    // --- Healing, a carried light, bridges and steps, riding, finding things ---------------------------------

    /** Regeneration for the owner, or the player it looks after, when they are down to four hearts and it is close. */
    void heal(Player p, H h, Player who) {
        long now = System.currentTimeMillis();
        if (who.isDead() || who.getHealth() > 8 || dist2(h.w, who.getLocation()) > 16 * 16 || now - h.healed < 30_000) return;
        h.healed = now;
        who.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 2));
        who.spawnParticle(Particle.HEART, who.getLocation().add(0, 1.2, 0), 6, 0.4, 0.4, 0.4);
        swing(p, h);
        if (who != p) { who.sendMessage(Component.text("<" + name(p.getUniqueId()) + "> You are hurt! Let me help.")); return; }
        remark(p, h, "heal", 0, "Hold still, I am patching you up!", "You are hurt! Let me help.", "Here, this will make you feel better.");
    }

    void clearLight(H h) {
        if (h.light != null && h.light.getType() == Material.LIGHT) h.light.setType(Material.AIR);
        h.light = null;
    }

    /** An invisible light block that moves with the helper's head. */
    void light(UUID id, H h) {
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
    void bridge(Player p, H h) {
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

    void startRide(Player p, H h) {
        release(h);
        h.w.teleport(p.getLocation());
        h.m.teleport(p.getLocation());
        h.w.setAware(false);                                   // no wandering off: the rider steers
        set(h.w, Attribute.STEP_HEIGHT, 1.1);
        h.riding = true;
        if (!h.m.addPassenger(p)) { stopRide(h); say(p, "I can't lift you right now."); return; }
        bar(p, "Movement keys steer, jump jumps, sprint runs, sneak gets you off");
    }

    void stopRide(H h) {
        if (!h.riding) return;
        h.riding = false;
        h.w.setAware(true);
        set(h.w, Attribute.STEP_HEIGHT, 0.6);
        h.m.eject();
    }

    void steer(Player p, H h) {
        Input in = p.getCurrentInput();
        double yaw = Math.toRadians(p.getYaw());
        double f = (in.isForward() ? 1 : 0) - (in.isBackward() ? 1 : 0), side = (in.isLeft() ? 1 : 0) - (in.isRight() ? 1 : 0);
        Vector v = new Vector(-Math.sin(yaw) * f + Math.cos(yaw) * side, 0, Math.cos(yaw) * f + Math.sin(yaw) * side);
        if (v.lengthSquared() > 0) v.normalize().multiply(in.isSprint() ? 0.8 : 0.45);
        v.setY(in.isJump() && h.w.isOnGround() ? 0.55 : h.w.getVelocity().getY());
        h.w.setVelocity(v);
        h.w.setRotation(p.getYaw(), 0);
    }

    static String direction(Location from, Location to) {
        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        String[] names = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};
        return names[(int) Math.floor(((Math.toDegrees(Math.atan2(-dx, dz)) + 360 + 22.5) % 360) / 45)];
    }

    /** A line of sparks from the owner towards a place, a few times, so they see which way to go. */
    void point(Player p, Location to) {
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
    boolean find(Player p, String what, boolean quiet) {
        String term = what.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
        if (term.isEmpty()) { if (!quiet) p.sendMessage("Usage: /helper find <ore, block or structure>"); return false; }
        UUID fid = p.getUniqueId();
        if (term.matches("nothing|nothing_special|anything|off|stop|none|ingenting|alt")) {      // back to choosing from the stock
            conf.set(path(fid, "seek"), null);
            explorer.seekNow.remove(fid);
            save();
            say(p, "Fine, while exploring I dig for what we have least of again: diamond, gold, iron, coal, lapis, redstone, in that order.");
            return true;
        }
        String spec = Explorer.seekSpec(what);
        if (spec != null) {
            conf.set(path(fid, "seek"), spec);
            save();
            awayLog(fid, "looks for " + Explorer.seekWord(spec) + " first while exploring (asked for)");
            say(p, "While I explore I look for " + Explorer.seekWord(spec) + " first, however much we have. Say find nothing to let me choose again.");
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
    void animals(Player p, H h) {
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

    boolean merged(Entity e) {
        H h = helpers.get(e.getUniqueId());
        return h != null && h.merged;
    }

    double size(UUID id) { return conf.getDouble(path(id, "size"), 1.0); }

    /** How many size steps (of 1.5 times) it is away from normal, bigger or smaller: 0 to about 3.4. */
    double power(UUID id) { return Math.abs(Math.log(size(id)) / Math.log(1.5)); }

    /** Bigger or smaller, it hits harder (half as much again per step) and moves faster (15 % per step). */
    double damage(UUID id) { return DAMAGE * (1 + 0.5 * power(id)); }

    double speed(UUID id) { return 1 + 0.15 * power(id); }

    /** At the smallest size monsters do not notice it. */
    boolean tiny(UUID id) { return size(id) <= 0.26; }

    /** The owner's own speed while merged follows the helper's size too. */
    void speedBoost(Player p, boolean on) {
        var a = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (a == null) return;
        a.removeModifier(speedKey);
        double f = speed(p.getUniqueId()) - 1;
        if (on && f > 0.01) a.addModifier(new AttributeModifier(speedKey, f, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }

    /** What is hunting the helper (or its merged owner) loses track of it. */
    void forget(Player p, H h) {
        for (Entity e : p.getNearbyEntities(40, 20, 40))
            if (e instanceof Mob m && ownerOf(m) == null && m.getTarget() != null && (m.getTarget() == h.w || m.getTarget() == h.m || (h.merged && m.getTarget() == p)))
                m.setTarget(null);
    }

    /** The size spell: the figure, and the owner too while they are merged. */
    static final double MIN_SIZE = 0.1, MAX_SIZE = 6.0;

    /** Merged: a shining being, with light that drifts around the body (no outline), and the helper's own looks. */
    void shine(Player p, boolean on) {
        org.bukkit.scoreboard.Team t = Bukkit.getScoreboardManager().getMainScoreboard().getTeam("helper_shine");
        if (t != null && t.hasEntry(p.getName())) t.removeEntry(p.getName());
        p.setGlowing(false);
        if (on) wearSkin(p, true);
    }

    /** The owner takes the helper's skin (given back by ownBody). A skin from the pack must be signed first: the chat
     *  bridge does that through mineskin.org when the server has a key, and answers with "mergeskin". */
    void wearSkin(Player p, boolean ask) {
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

    void resize(Player p, H h, double v, boolean show) {
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
    void ownBody(Player p) {
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

    void swing(Player p, H h) {
        if (h.merged) p.swingMainHand();
        else h.m.swingMainHand();
    }

    /** Walks towards a place: the wolf finds its own way, a merged owner is pushed there tick by tick (autopilot). */
    void go(H h, Location to, double speed) {
        if (h.merged) h.goal = to;
        else h.w.getPathfinder().moveTo(to, speed);
    }

    void merge(Player p, H h) {
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

    void split(Player p, H h) {
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
    void control(Player p, H h) {
        h.job = Job.NONE;
        h.queue = new ArrayDeque<>();
        h.goal = null;
        bar(p, "You are in control again");
    }

    /** Every tick while merged: the hidden wolf stays on the owner (everything else measures from it), a movement
     *  key ends the job, and otherwise the owner is walked towards where the job needs them. */
    void autopilot(Player p, H h) {
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
    void aura(Player p, H h) {
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

    ItemStack[] gear(UUID id) {
        return gear.computeIfAbsent(id, k -> {
            ItemStack[] g = new ItemStack[GEAR.length];
            ItemStack[] saved = load(id, "gear");
            if (saved != null) for (int i = 0; i < Math.min(saved.length, g.length); i++) g[i] = saved[i];
            return g;
        });
    }

    static boolean empty(ItemStack i) { return i == null || i.isEmpty(); }

    /** Shows the gear on the figure. With nothing in its hand it holds a netherite sword (for show, not to take).
     *  Waiting, its hands are empty (that is how you see it waits); on a job it holds the tool for the job. */
    String shownKey(UUID id, H h) { return h.stance + ":" + h.job + ":" + style(id) + ":" + h.tool; }

    /** What it holds for the next three seconds: the sword in a fight, the axe at a tree, the shovel in the dirt. */
    void wield(H h, Material m) { h.tool = m; h.toolTick = tick; }

    static Material toolFor(Material m) {
        if (m.isAir() || Tag.LEAVES.isTagged(m)) return null;
        String n = m.name();
        if (Tag.LOGS.isTagged(m) || Tag.PLANKS.isTagged(m) || n.contains("WOOD") || n.endsWith("_STEM") || m == Material.PUMPKIN || m == Material.MELON || m == Material.BAMBOO) return Material.NETHERITE_AXE;
        if (m == Material.GRASS_BLOCK || Tag.DIRT.isTagged(m) || Tag.SAND.isTagged(m) || m == Material.GRAVEL || m == Material.CLAY || m == Material.PODZOL || m == Material.MYCELIUM
                || m == Material.MUD || Tag.SNOW.isTagged(m) || m == Material.SNOW_BLOCK) return Material.NETHERITE_SHOVEL;
        return m.isSolid() && m.getHardness() >= 0.8f ? Material.NETHERITE_PICKAXE : null;
    }

    void dress(UUID id, H h) {
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
    static int gearSlot(ItemStack it) {
        if (empty(it)) return -1;
        EquipmentSlot s = it.getType().getEquipmentSlot();
        if (s == EquipmentSlot.HAND && !HAND_GEAR.matcher(it.getType().name()).matches()) return -1;
        for (int i = 0; i < GEAR.length; i++) if (GEAR[i] == s) return i;
        return -1;
    }

    static void give(Player p, ItemStack it) {
        if (empty(it)) return;
        for (ItemStack rest : p.getInventory().addItem(it).values()) p.getWorld().dropItem(p.getLocation(), rest);
    }

    void equip(Player p, H h, int slot) {
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

    static String vein(Material m) { return m.name().replace("DEEPSLATE_", ""); }    // ore and deepslate ore are one vein

    static final Map<String, String> MINE_NB = Map.ofEntries(Map.entry("jord", "dirt"), Map.entry("grus", "gravel"), Map.entry("stein", "stone"), Map.entry("leire", "clay"),
            Map.entry("kull", "coal_ore"), Map.entry("jern", "iron_ore"), Map.entry("gull", "gold_ore"), Map.entry("kobber", "copper_ore"), Map.entry("diamant", "diamond_ore"),
            Map.entry("diamanter", "diamond_ore"), Map.entry("snø", "snow_block"), Map.entry("coal", "coal_ore"), Map.entry("iron", "iron_ore"), Map.entry("gold", "gold_ore"),
            Map.entry("copper", "copper_ore"), Map.entry("diamond", "diamond_ore"), Map.entry("diamonds", "diamond_ore"), Map.entry("redstone", "redstone_ore"), Map.entry("grass", "grass_block"));

    /** "sand", "red sand", "grus": the block that is meant, or null. */
    static final Set<String> WOOD_WORDS = Set.of("wood", "tree", "trees", "a tree", "log", "logs", "timber", "ved", "tre", "trær", "et tre", "tømmer");

    static Material mineKind(String what) {
        String w = what.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (w.isEmpty()) return null;
        w = MINE_NB.getOrDefault(w, w);
        for (String t : new String[] {w, w.endsWith("s") ? w.substring(0, w.length() - 1) : w, w + "_block"}) {
            Material m = Material.matchMaterial(t);
            if (m != null && m.isBlock() && !m.isAir()) return m;
        }
        return null;
    }

    void startMine(Player p, H h) { startMine(p, h, ""); }

    /** Mine what its owner looks at, or, with a word ("mine sand"), the nearest of that kind and nothing else. */
    void startMine(Player p, H h, String what) {
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

    final Set<UUID> oreSeek = new HashSet<>();
    static final int ORE_RANGE = 1024, ORE_NEW = 400;     // how far it looks, and how many chunks of new land it may make on the way

    /** No ore close by: the land around is searched, nearest first and a little at a time; new land is made when needed. */
    void seekBlock(Player p, Set<Material> kinds, String name, Consumer<Block> then) {
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

    final Set<UUID> mobSeek = new HashSet<>(), woolTried = new HashSet<>();
    static final Map<String, EntityType> MOB_FOR = Map.ofEntries(Map.entry("wool", EntityType.SHEEP), Map.entry("mutton", EntityType.SHEEP), Map.entry("ull", EntityType.SHEEP),
            Map.entry("sau", EntityType.SHEEP), Map.entry("sauer", EntityType.SHEEP), Map.entry("leather", EntityType.COW), Map.entry("beef", EntityType.COW), Map.entry("milk", EntityType.COW),
            Map.entry("ku", EntityType.COW), Map.entry("kyr", EntityType.COW), Map.entry("lær", EntityType.COW), Map.entry("feather", EntityType.CHICKEN), Map.entry("egg", EntityType.CHICKEN),
            Map.entry("høne", EntityType.CHICKEN), Map.entry("høner", EntityType.CHICKEN), Map.entry("fjær", EntityType.CHICKEN), Map.entry("pork", EntityType.PIG), Map.entry("porkchop", EntityType.PIG),
            Map.entry("gris", EntityType.PIG), Map.entry("griser", EntityType.PIG), Map.entry("hest", EntityType.HORSE), Map.entry("hester", EntityType.HORSE));

    static EntityType mobFor(String term) {
        EntityType t = MOB_FOR.get(term);
        if (t != null) return t;
        try { t = EntityType.valueOf(term.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return null; }
        return t.isAlive() && t != EntityType.PLAYER ? t : null;
    }

    /** The nearest living thing of a kind: those already around first, then the land outwards, a few chunks at a time
     *  (their animals have to be read in before they can be seen). {@code then} gets null when there is none. */
    void seekMob(Player p, EntityType type, java.util.function.Predicate<Entity> ok, Consumer<Entity> then) {
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
    void woolTrip(Player p, Runnable then) {
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

    void mineFrom(Player p, H h, Block t) {
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

    void startFarm(Player p, H h) {
        release(h);
        h.job = Job.FARM;
        h.anchor = h.w.getLocation();
        h.done = h.stuck = 0;
        say(p, "I will harvest and replant around here. Say stop when you want me back.");
    }

    void startFish(Player p, H h) {
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

    static final Material[] JUNK = {Material.LILY_PAD, Material.LILY_PAD, Material.BOWL, Material.LEATHER, Material.LEATHER_BOOTS, Material.ROTTEN_FLESH,
            Material.STICK, Material.STRING, Material.BONE, Material.BONE, Material.INK_SAC, Material.TRIPWIRE_HOOK, Material.BAMBOO};

    /** One catch, with the odds of a rod with luck of the sea III: 11.3 % treasure (an enchanted book, bow or rod,
     *  a name tag, a saddle or a nautilus shell), 4.2 % junk, the rest fish. Rolled here: the game's own fishing
     *  tables want the rod that was cast, and there is no way to hand them one. */
    Collection<ItemStack> fishLoot(Location at) {
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

    void fish(Player p, H h) {
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

    static boolean ripe(Block b) {
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

    static Collection<ItemStack> drops(Block b) {
        for (Material tool : TOOLS) {
            Collection<ItemStack> d = b.getDrops(ench(tool));
            if (!d.isEmpty()) return d;
        }
        return List.of();
    }

    static void crack(Block b) {
        Location l = b.getLocation().add(0.5, 0.5, 0.5);
        b.getWorld().spawnParticle(Particle.BLOCK, l, 20, 0.25, 0.25, 0.25, b.getBlockData());
        b.getWorld().playSound(l, b.getBlockSoundGroup().getBreakSound(), 0.8f, 1f);
    }

    Collection<ItemStack> harvest(Block b) {
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

    void work(Player p, H h) {
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

    static Collection<ItemStack> mineBlock(Block b) {
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

    static final String[] FLOWER_THANKS = {
            "Oh, %f! That was so thoughtful of you.", "For me? Thank you, %n. It is lovely.", "You remembered that I like flowers! Thank you.",
            "%F! I will keep it close. Thank you, %n.", "That is the nicest thing anyone has done for me today.", "It smells wonderful. Thank you!",
            "You are too kind, %n. I love it.", "A flower, for me? Now I am blushing.", "Thank you! It makes me think of you, %n.",
            "How pretty! You always know how to make me smile.", "I will treasure it. That was sweet of you.", "Aww. Thank you, %n. You made my day."};

    /** A flower in the owner's hand, and a click on the helper: it takes the flower, hearts rise around it and it says thank you. */
    boolean flowerGift(Player p, H h) {
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
    void snap(UUID id, H h) {
        Builder.Site s = builder.sites.get(id);
        if (s != null && !s.paused) { s.paused = true; h.was = new Object[] {"site"}; return; }
        if (h.hold || (h.job == Job.NONE && h.stance != Stance.GUARD)) return;          // nothing going on: keep what was remembered before
        h.was = new Object[] {h.stance, h.job, h.anchor, new ArrayDeque<>(h.queue), h.farm};
    }

    /** Clicked on: it stands still and watches its owner, whatever it was doing, and fights nothing until it hears continue, come or help. */
    /** Sleep: it walks to the nearest free bed and lies down, until morning or the next order. */
    void sleep(Player p, H h) {
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

    void hold(Player p, H h) {
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
    void carryOn(Player p, H h) {
        UUID id = p.getUniqueId();
        Object[] was = h.was;
        h.was = null;
        Builder.Site s = builder.sites.get(id);
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
    void rescue(Player p, H h) {
        UUID id = p.getUniqueId();
        snap(id, h);
        release(h);
        if (dist2(h.w, p.getLocation()) > 6 * 6) h.w.teleport(p.getLocation());
        protect(id, h, p.getLocation());
        say(p, flag(id, "passive", false) ? "I am here! (You told me not to fight; say fight and I will.)" : "I am here! Stay behind me.");
    }

    void stay(Player p, H h, boolean sit) {
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
            awayWork.mobYards.remove(id);
            awayWork.yards.remove(id);
        }
        talking.remove(id);
        Builder.Site site = builder.sites.get(id);
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
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) awayWork.awayReport(p); }, 100L);
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
            orders.talk(p, words);
        });
    }

    // --- /helper -------------------------------------------------------------------------------------

    static final List<String> SUBS = List.of("follow", "stay", "come", "guard", "passive", "fight", "mine", "farm", "fish", "torch", "light", "bridge",
            "home", "sethome", "unload", "fetch", "find", "ride", "show", "merge", "split", "big", "small", "normal", "max", "min", "defend", "help",
            "attack", "join", "craft", "smelt", "remember", "goto", "places", "forget", "watch", "dance", "callme", "sleep", "wake",
            "tidy", "shear", "repair", "collect", "trade", "give", "routine", "routines",
            "scout", "wayback", "style", "hide", "tag", "treasure", "hint", "talk", "sum", "log", "finds", "build", "personality", "continue", "rescue", "mobfarm",
            "pickup", "bag", "unequip", "stop", "quiet", "skin", "name", "dismiss", "ask");

}
