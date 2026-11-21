package dev.etnan.creator;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.packs.ResourcePack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.profile.PlayerTextures;

/**
 * Skins imported by skins.py (the skins/ folder on the host, through the AI bridge). Each one is described in
 * plugins/Creator/skins/<name>.json: "model" (slim|classic), "pack": true when the PNG is in the resource pack
 * (for helpers, via the profile skin patch), and "value"/"signature" when mineskin.org has signed it (for players).
 *
 * /skin <name> | reset | list    your own skin (remembered, put back on when you join)
 * /sendpack                      (op/console) sends the current resource pack to everyone online, so a new
 *                                skin or item look shows up without a restart
 */
public final class Skins implements Listener {
    private final JavaPlugin plugin;
    private final File dir, stateFile;
    private final YamlConfiguration state;
    private String hash;
    private long stamp = -1;

    Skins(JavaPlugin plugin) {
        this.plugin = plugin;
        dir = new File(plugin.getDataFolder(), "skins");
        stateFile = new File(plugin.getDataFolder(), "skins-state.yml");
        state = YamlConfiguration.loadConfiguration(stateFile);
    }

    static String slug(String s) {
        s = s.toLowerCase(Locale.ROOT).replace("æ", "ae").replace("ø", "o").replace("å", "a").replaceAll("\\.png$", "");
        s = s.replaceAll("[^a-z0-9_-]", "_").replaceAll("_+", "_").replaceAll("^[_-]+|[_-]+$", "");
        return s.length() > 40 ? s.substring(0, 40) : s;
    }

    private JsonObject info(String slug) {
        File f = new File(dir, slug + ".json");
        if (slug.isEmpty() || !f.isFile()) return null;
        try (FileReader r = new FileReader(f)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    List<String> names() {
        List<String> out = new ArrayList<>();
        String[] files = dir.list();
        if (files != null) for (String f : files) if (f.endsWith(".json")) out.add(f.substring(0, f.length() - 5));
        out.sort(null);
        return out;
    }

    /** Profile for a helper/mannequin that uses the PNG from the resource pack, or null if that skin is not in the pack. */
    ResolvableProfile packProfile(String slug) {
        JsonObject j = info(slug);
        if (j == null || !j.has("pack") || !j.get("pack").getAsBoolean()) return null;
        boolean slim = j.has("model") && j.get("model").getAsString().equals("slim");
        return ResolvableProfile.resolvableProfile()
                .skinPatch(b -> b.body(Key.key("supermann", "entity/skins/" + slug))
                        .model(slim ? PlayerTextures.SkinModel.SLIM : PlayerTextures.SkinModel.CLASSIC))
                .build();
    }

    /** The Mojang-signed texture of a skin (value, signature), when it has been signed. */
    String[] signed(String slug) {
        JsonObject j = info(slug);
        return j == null || !j.has("value") || !j.has("signature") ? null : new String[] {j.get("value").getAsString(), j.get("signature").getAsString()};
    }

    // --- Player skins ----------------------------------------------------------------------------------

    private boolean apply(Player p, String slug) {
        JsonObject j = info(slug);
        if (j == null || !j.has("value") || !j.has("signature")) return false;
        PlayerProfile prof = p.getPlayerProfile();
        String id = p.getUniqueId().toString();
        if (!state.contains(id + ".original"))
            for (ProfileProperty pp : prof.getProperties())
                if (pp.getName().equals("textures")) {
                    state.set(id + ".original.value", pp.getValue());
                    state.set(id + ".original.signature", pp.getSignature());
                }
        prof.setProperty(new ProfileProperty("textures", j.get("value").getAsString(), j.get("signature").getAsString()));
        p.setPlayerProfile(prof);
        state.set(id + ".skin", slug);
        save();
        return true;
    }

    private void reset(Player p) {
        String id = p.getUniqueId().toString();
        String v = state.getString(id + ".original.value"), sig = state.getString(id + ".original.signature");
        state.set(id + ".skin", null);
        save();
        if (v == null) return;
        PlayerProfile prof = p.getPlayerProfile();
        prof.setProperty(new ProfileProperty("textures", v, sig));
        p.setPlayerProfile(prof);
    }

    private void save() {
        try { state.save(stateFile); } catch (IOException e) { plugin.getLogger().warning("Could not save skins-state.yml: " + e.getMessage()); }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String id = p.getUniqueId().toString();
        // Mojang hands out the real skin on every login: remember it fresh, then put the chosen one back on.
        for (ProfileProperty pp : p.getPlayerProfile().getProperties())
            if (pp.getName().equals("textures")) {
                state.set(id + ".original.value", pp.getValue());
                state.set(id + ".original.signature", pp.getSignature());
            }
        String chosen = state.getString(id + ".skin");
        if (chosen != null) Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) apply(p, chosen); }, 20L);
        // The pack may have changed since the server started (new skins): then the hash in server.properties is old.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            ResourcePack sp = Bukkit.getServerResourcePack();
            String now = packHash();
            if (p.isOnline() && sp != null && now != null && !now.equalsIgnoreCase(sp.getHash())) sendPack(List.of(p));
        }, 60L);
    }

    // --- Resource pack ---------------------------------------------------------------------------------

    /** The pack file the server's URL points at, in the folder the mc-pack web server serves (data/resourcepack). */
    private File packFile() {
        ResourcePack sp = Bukkit.getServerResourcePack();
        if (sp == null || sp.getUrl() == null) return null;
        String name = sp.getUrl().replaceAll(".*/", "");
        File f = new File(new File(Bukkit.getWorldContainer(), "resourcepack"), name);
        return f.isFile() ? f : null;
    }

    private String packHash() {
        File f = packFile();
        if (f == null) return null;
        if (f.lastModified() == stamp && hash != null) return hash;
        try (InputStream in = Files.newInputStream(f.toPath())) {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[65536];
            for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
            hash = HexFormat.of().formatHex(md.digest());
            stamp = f.lastModified();
            return hash;
        } catch (Exception e) {
            return null;
        }
    }

    int sendPack(Collection<? extends Player> to) {
        ResourcePack sp = Bukkit.getServerResourcePack();
        String h = packHash();
        if (sp == null || h == null) return 0;
        ResourcePackRequest req = ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(sp.getId(), URI.create(sp.getUrl()), h))
                .replace(true).required(sp.isRequired()).build();
        for (Player p : to) p.sendResourcePacks(req);
        return to.size();
    }

    // --- Commands --------------------------------------------------------------------------------------

    void register(Commands r) {
        r.register("skin", "Your skin: /skin <name> | reset | list (skins are imported with \"ai skin <name>\")", new BasicCommand() {
            @Override
            public void execute(CommandSourceStack src, String[] args) {
                if (!(src.getExecutor() instanceof Player p)) { src.getSender().sendMessage("Run it as a player."); return; }
                String a = args.length == 0 ? "list" : slug(String.join("_", args));
                if (a.equals("list")) {
                    p.sendMessage(Component.text("Skins: " + String.join(", ", names()) + ". New ones: save a PNG in skins/ on the server and write \"ai skin <name>\".", NamedTextColor.GOLD));
                } else if (a.equals("reset")) {
                    reset(p);
                    p.sendMessage(Component.text("Your normal skin is back.", NamedTextColor.GOLD));
                } else if (apply(p, a)) {
                    p.sendMessage(Component.text("You now have the skin " + a + ". /skin reset gives you your own back.", NamedTextColor.GOLD));
                } else {
                    p.sendMessage(Component.text("The skin " + a + " is not ready for players yet: write \"ai skin " + a + "\" to import it.", NamedTextColor.RED));
                }
            }

            @Override
            public Collection<String> suggest(CommandSourceStack src, String[] args) {
                List<String> s = new ArrayList<>(names());
                s.add("reset");
                String part = args.length == 0 ? "" : args[args.length - 1].toLowerCase(java.util.Locale.ROOT);   // "boy" also finds blueboy
                s.removeIf(n -> !n.contains(part));
                return s;
            }
        });
        r.register("sendpack", "Sends the current resource pack to everyone online (op)", (src, args) -> {
            if (!src.getSender().hasPermission("creator.sendpack")) { src.getSender().sendMessage("Only ops."); return; }
            src.getSender().sendMessage("Sent the resource pack to " + sendPack(Pure.players()) + " players.");
        });
    }
}
