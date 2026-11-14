package dev.etnan.creator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/** How a helper talks and behaves on its own: its personality and voice, jokes, the comments it makes about what
 *  you do, its small habits when it has nothing to do, teasing other helpers, and what it says about a new look. */
final class Persona {
    final Helper core;

    Persona(Helper core) { this.core = core; }

    void comments(Player p, Helper.H h) {
        boolean n = Helper.night(p.getWorld());
        if (n && !h.night) core.remark(p, h, "night", 300, "It is getting dark.", "Night is coming. I will keep watch.", "*yawn* The sun is going down.");
        h.night = n;
        if (!h.merged && !p.isFlying() && !p.isGliding() && p.getFallDistance() > 10)
            core.remark(p, h, "fall", 15, "Whoa, careful!", "Aaah, that is a long way down!", "Hold on!");
        if (core.tick % 20 != 0 || p.isDead()) return;
        if (h.merged && core.tiny(p.getUniqueId())) return;                    // nothing notices us anyway
        LivingEntity worst = null;                                        // danger close by: one sentence in chat
        int count = 0;
        double best = Double.MAX_VALUE;
        for (Entity e : p.getNearbyEntities(10, 5, 10))
            if (core.foe(e) && e instanceof LivingEntity le) {
                count++;
                double d = e.getLocation().distanceSquared(p.getLocation());
                if (d < best) { best = d; worst = le; }
            }
        if (worst != null)
            core.remark(p, h, "danger", 20, count >= 3 ? "Watch out, " + count + " monsters around us!"
                    : "Watch out, " + worst.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ') + " " + Helper.side(p, worst) + "!");
        Block feet = p.getLocation().getBlock();
        for (int x = -2; x <= 2; x++) for (int y = -2; y <= 0; y++) for (int z = -2; z <= 2; z++)
            if (feet.getRelative(x, y, z).getType() == Material.LAVA) { core.remark(p, h, "lava", 45, "Watch out, lava!"); return; }
    }

    /** Called while it has nobody to fight and no job. True while a habit keeps it from walking back to the owner. */
    boolean habits(Player p, Helper.H h) {
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
            if (!still || h.calm < 20 || now < h.nextIdle || p.isSleeping() || h.dance > 0 || core.flag(p.getUniqueId(), "quiet", false)) return false;
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
            for (Entity e : w.getNearbyEntities(8, 3, 8)) if (e instanceof Animals && core.ownerOf(e) == null) { animal = e; break; }
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
        if (goal != null && goal.getWorld() == w.getWorld() && Helper.dist2(w, goal) > 2.5 * 2.5 && h.idleStep < 16) {    // on its way
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
                Helper.crack(b);
                b.setType(Material.AIR);
                h.m.swingMainHand();
                h.idle = "give";
                h.idleStep = 0;
            }
            case "give" -> {
                if (h.gift != null) Helper.give(p, h.gift);
                h.gift = null;
                h.m.swingMainHand();
                core.remark(p, h, "flower", 0, "I picked this for you.", "Look what I found! It is for you.", "Here, " + core.nick(p) + ". It made me think of you.");
                h.idle = null;
            }
            case "pet" -> {
                if (goal != null) {
                    h.m.swingMainHand();
                    p.spawnParticle(Particle.HEART, goal.clone().add(0, h.idleEnt.getHeight() + 0.3, 0), 3, 0.2, 0.2, 0.2);
                    String kind = h.idleEnt.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
                    core.remark(p, h, "pet", 0, "Good " + kind + ".", "Who is a good " + kind + "? You are.", "I like this " + kind + ".");
                }
                h.idle = null;
            }
            case "campfire" -> {
                h.look = h.idleLoc;
                if (h.idleStep < 100) { h.idleStep = 100; core.remark(p, h, "fire", 0, "Nice and warm.", "I could stand here all night.", "Fires are the best."); }
                if (++h.idleStep > 116) h.idle = null;                    // eight seconds by the fire
                return true;
            }
            case "sunset" -> {
                h.look = w.getLocation().add(-40, 8, 0);                  // the sun sets in the west
                if (h.idleStep < 100) { h.idleStep = 100; core.remark(p, h, "sunset", 0, "Look at that sunset.", "I love this time of day.", "Pretty, isn't it?"); }
                if (++h.idleStep > 116) h.idle = null;
                return true;
            }
            default -> {                                                  // puddle
                if (h.idleStep < 100) { h.idleStep = 100; core.remark(p, h, "puddle", 0, "Splash!", "I love the rain!", "Puddles!"); }
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
        List<UUID> ids = new ArrayList<>(core.helpers.keySet());
        for (int i = 0; i < ids.size(); i++) for (int j = i + 1; j < ids.size(); j++) {
            Helper.H a = core.helpers.get(ids.get(i)), b = core.helpers.get(ids.get(j));
            Player pa = Bukkit.getPlayer(ids.get(i)), pb = Bukkit.getPlayer(ids.get(j));
            if (pa == null || pb == null || a.merged || b.merged || a.job != Helper.Job.NONE || b.job != Helper.Job.NONE || now < a.banter || now < b.banter) continue;
            if (a.w.getTarget() != null || b.w.getTarget() != null || Helper.dist2(a.w, b.w.getLocation()) > 6 * 6) continue;
            if (ThreadLocalRandom.current().nextBoolean()) { Helper.H t = a; a = b; b = t; Player tp = pa; pa = pb; pb = tp; }
            a.banter = b.banter = now + 90_000 + ThreadLocalRandom.current().nextInt(90_000);
            String[] line = Helper.BANTER[ThreadLocalRandom.current().nextInt(Helper.BANTER.length)];
            String na = core.name(pa.getUniqueId()), nb = core.name(pb.getUniqueId());
            for (int k = 0; k < 2; k++) {
                String text = "<" + (k == 0 ? na : nb) + "> " + line[k].replace("{a}", na).replace("{b}", nb).replace("{oa}", pa.getName()).replace("{ob}", pb.getName());
                for (Player to : List.of(pa, pb)) if (!core.flag(to.getUniqueId(), "quiet", false)) to.sendMessage(Component.text(text));
            }
            a.dance = Math.max(a.dance, 65);                              // a wave
            b.dance = Math.max(b.dance, 65);
        }
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
            if (e instanceof LivingEntity && core.ownerOf(e) == null) mobs.merge(e instanceof Player pl ? "player " + pl.getName() : e.getType().name().toLowerCase(Locale.ROOT), 1, Integer::sum);
        StringBuilder sb = new StringBuilder("world " + w.getName() + ", biome " + c.getBiome().getKey().getKey() + ", height y " + l.getBlockY()
                + (w.getEnvironment() == World.Environment.NORMAL ? ", " + (w.isDayTime() ? "day" : "night") + (w.hasStorm() ? ", raining" : "") : "")
                + ", the player has " + (int) Math.ceil(p.getHealth() / 2) + " of 10 hearts and holds " + p.getInventory().getItemInMainHand().getType().name().toLowerCase(Locale.ROOT)
                + ", you call the player " + (core.conf.contains(core.path(p.getUniqueId(), "nick")) ? core.nick(p) : p.getName() + " (no nickname yet)") + ". Blocks within 10 blocks:");
        blocks.entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue()).limit(14).forEach(en -> sb.append(' ').append(en.getValue()).append(' ').append(en.getKey()).append(','));
        sb.append(" creatures nearby:");
        if (mobs.isEmpty()) sb.append(" none");
        mobs.entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue()).limit(6).forEach(en -> sb.append(' ').append(en.getValue()).append(' ').append(en.getKey()).append(','));
        return sb.toString().replace('|', ' ');
    }

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
        List<String> now = core.conf.getStringList(core.path(id, "personality"));
        if (t.isEmpty()) return (now.isEmpty() ? "I am my usual self." : "I am " + String.join(" and ", now) + ".") + " You can pick one or two: " + String.join(", ", all) + ". personality reset makes me my usual self again.";
        if (t.matches("reset|default|off|none|normal|usual|standard|av|nullstill|vanlig")) {
            core.conf.set(core.path(id, "personality"), null);
            core.save();
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
        core.conf.set(core.path(id, "personality"), pick);
        core.save();
        return "From now on I am " + String.join(" and ", pick) + ".";
    }

    /** A fixed line said in its own voice: now and then a few words of its personality before or after. */
    String voice(UUID id, String text) {
        List<String> now = core.conf.getStringList(core.path(id, "personality"));
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
        for (String n : core.conf.getStringList(core.path(id, "personality"))) { String[] p = persona(n); if (p != null) sb.append(sb.length() == 0 ? "" : "; ").append(p[0]).append(" (").append(p[1]).append(")"); }
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
        String t = Helper.POLITE.matcher(words.replaceAll("[.!?\\s]+$", "").trim()).replaceAll("").trim().toLowerCase(Locale.ROOT);
        String first = t.split(" ")[0], doing = doing(id);
        boolean known = Helper.SUBS.contains(first);
        for (String[] w : Helper.WORDS) if (t.matches(w[1])) known = true;
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
        String job = core.conf.getString(core.path(id, "away.job"), "");
        if (!core.conf.getBoolean(core.path(id, "active")) || !core.flag(id, "watch", false)) return "I have no job while you are away.";
        return "I carry on " + switch (job) {
            case "" -> "guarding home";
            case "home" -> "working at home";
            case "place" -> "working at " + core.conf.getString(core.path(id, "away.place"), "the place");
            case "fish" -> "fishing at home";
            case "farm" -> "farming at home";
            case "smelt" -> "smelting at home";
            case "mine" -> "digging for " + core.conf.getString(core.path(id, "away.target"), "ore").toLowerCase(Locale.ROOT).replace('_', ' ');
            default -> "exploring";
        } + ".";
    }
}
