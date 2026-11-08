package pl.szafa;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import pl.szafa.Cosmetic.Kind;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SzafaPlugin extends JavaPlugin {

    private final Map<String, Cosmetic> pets = new LinkedHashMap<>();
    private final Map<String, Cosmetic> costumes = new LinkedHashMap<>();

    private DataStore data;
    private PetManager petManager;
    private CostumeManager costumeManager;
    private Menus menus;
    private DateTimeFormatter dateFormat;
    private boolean showCostumeToSelf;
    private float petScale = 0.5f;
    private float petYawOffset = 0f;
    private boolean petNametag = true;
    private int maintenanceTick;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadDefinitions();

        data = new DataStore(this);
        petManager = new PetManager(this);
        costumeManager = new CostumeManager(this);
        menus = new Menus(this);

        Bukkit.getPluginManager().registerEvents(menus, this);
        Bukkit.getPluginManager().registerEvents(new Listeners(this), this);

        SzafaCommand szafa = new SzafaCommand(this);
        getCommand("szafa").setExecutor(szafa);
        AdminCommand admin = new AdminCommand(this);
        getCommand("szafaadmin").setExecutor(admin);
        getCommand("szafaadmin").setTabCompleter(admin);

        Bukkit.getScheduler().runTaskTimer(this, petManager::tick, 20L, 2L);
        Bukkit.getScheduler().runTaskTimer(this, this::maintenance, 40L, 20L);
    }

    @Override
    public void onDisable() {
        if (petManager != null) {
            petManager.removeAll();
        }
        if (data != null) {
            data.flushNow();
        }
    }

    // ------------------------------------------------------------------ config

    public void loadDefinitions() {
        reloadConfig();
        FileConfiguration cfg = getConfig();

        ZoneId zone;
        try {
            zone = ZoneId.of(cfg.getString("timezone", "Europe/Warsaw"));
        } catch (Exception ex) {
            zone = ZoneId.systemDefault();
        }
        dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(zone);
        showCostumeToSelf = cfg.getBoolean("show-costume-to-self", false);
        petScale = (float) cfg.getDouble("pet-scale", 0.5);
        petYawOffset = (float) cfg.getDouble("pet-yaw-offset", 0.0);
        petNametag = cfg.getBoolean("pet-nametag", true);

        pets.clear();
        costumes.clear();
        loadSection(cfg.getConfigurationSection("pets"), Kind.PET, pets);
        loadSection(cfg.getConfigurationSection("costumes"), Kind.COSTUME, costumes);
        if (costumeManager != null) {
            costumeManager.clearCache();
        }
    }

    private void loadSection(ConfigurationSection root, Kind kind, Map<String, Cosmetic> out) {
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s != null) {
                out.put(id, Cosmetic.load(kind, id, s, getLogger()));
            }
        }
    }

    public void reloadAll() {
        loadDefinitions();
        petManager.removeAll();
        for (Player p : Bukkit.getOnlinePlayers()) {
            costumeManager.refresh(p);
        }
    }

    // ------------------------------------------------------------------ accessors

    public DataStore data() {
        return data;
    }

    public PetManager petManager() {
        return petManager;
    }

    public CostumeManager costumeManager() {
        return costumeManager;
    }

    public Menus menus() {
        return menus;
    }

    public boolean showCostumeToSelf() {
        return showCostumeToSelf;
    }

    public float petScale() {
        return petScale;
    }

    public float petYawOffset() {
        return petYawOffset;
    }

    public boolean petNametag() {
        return petNametag;
    }

    public Collection<Cosmetic> defs(Kind kind) {
        return (kind == Kind.PET ? pets : costumes).values();
    }

    public Cosmetic def(Kind kind, String id) {
        if (id == null) {
            return null;
        }
        return (kind == Kind.PET ? pets : costumes).get(id);
    }

    public String format(long millis) {
        if (millis == Long.MAX_VALUE) {
            return "BEZ LIMITU";
        }
        return dateFormat.format(Instant.ofEpochMilli(millis));
    }

    public static Map<String, Long> owned(PlayerData d, Kind kind) {
        return kind == Kind.PET ? d.pets : d.costumes;
    }

    public static String activeId(PlayerData d, Kind kind) {
        return kind == Kind.PET ? d.activePet : d.activeCostume;
    }

    public void msg(CommandSender to, String text) {
        to.sendMessage(Text.c(Text.PREFIX + text));
    }

    // ------------------------------------------------------------------ logika

    /** Aktywne i niewygasniete zwierzatko / kostium gracza (albo null). */
    public Cosmetic activeOf(Player p, Kind kind) {
        PlayerData d = data.get(p.getUniqueId());
        String id = activeId(d, kind);
        if (id == null) {
            return null;
        }
        Long exp = owned(d, kind).get(id);
        if (exp == null || exp <= System.currentTimeMillis()) {
            return null;
        }
        return def(kind, id);
    }

    public List<Cosmetic> activeList(Player p) {
        List<Cosmetic> out = new ArrayList<>(2);
        Cosmetic pet = activeOf(p, Kind.PET);
        Cosmetic costume = activeOf(p, Kind.COSTUME);
        if (pet != null) {
            out.add(pet);
        }
        if (costume != null) {
            out.add(costume);
        }
        return out;
    }

    public List<Bonuses> bonusesOf(Player p) {
        List<Bonuses> out = new ArrayList<>(2);
        for (Cosmetic c : activeList(p)) {
            out.add(c.bonuses);
        }
        return out;
    }

    public void activate(Player p, Cosmetic c) {
        PlayerData d = data.get(p.getUniqueId());
        Cosmetic old = activeOf(p, c.kind);
        if (old != null) {
            clearEffects(p, old.bonuses);
        }
        if (c.kind == Kind.PET) {
            d.activePet = c.id;
            petManager.remove(p);
        } else {
            d.activeCostume = c.id;
        }
        data.markDirty();
        applyEffects(p);
        if (c.kind == Kind.COSTUME) {
            costumeManager.refresh(p);
        }
        msg(p, "Aktywowano: &f" + c.name);
    }

    public boolean deactivate(Player p, Kind kind) {
        PlayerData d = data.get(p.getUniqueId());
        String id = activeId(d, kind);
        if (id == null) {
            return false;
        }
        Cosmetic cur = def(kind, id);
        if (kind == Kind.PET) {
            d.activePet = null;
            petManager.remove(p);
        } else {
            d.activeCostume = null;
        }
        if (cur != null) {
            clearEffects(p, cur.bonuses);
        }
        if (kind == Kind.COSTUME) {
            costumeManager.restore(p);
        }
        data.markDirty();
        return true;
    }

    /** Nadaje rzecz na X dni (days == null = bez limitu). Dodaje czas do istniejacego. */
    public long give(java.util.UUID uuid, Cosmetic c, Integer days) {
        PlayerData d = data.get(uuid);
        Map<String, Long> map = owned(d, c.kind);
        long now = System.currentTimeMillis();
        Long cur = map.get(c.id);
        long result;
        if (days == null || (cur != null && cur == Long.MAX_VALUE)) {
            result = Long.MAX_VALUE;
        } else {
            long base = (cur != null && cur > now) ? cur : now;
            result = base + days * 86_400_000L;
        }
        map.put(c.id, result);
        data.markDirty();
        return result;
    }

    public boolean take(java.util.UUID uuid, Cosmetic c) {
        PlayerData d = data.get(uuid);
        boolean had = owned(d, c.kind).remove(c.id) != null;
        Player online = Bukkit.getPlayer(uuid);
        if (online != null && c.id.equals(activeId(d, c.kind))) {
            deactivate(online, c.kind);
        } else if (c.id.equals(activeId(d, c.kind))) {
            if (c.kind == Kind.PET) {
                d.activePet = null;
            } else {
                d.activeCostume = null;
            }
        }
        data.markDirty();
        return had;
    }

    // ------------------------------------------------------------------ efekty i wygasanie

    public void applyEffects(Player p) {
        for (Cosmetic c : activeList(p)) {
            for (Bonuses.EffectSpec s : c.bonuses.effects) {
                p.addPotionEffect(new PotionEffect(s.type(), 100, s.amplifier(), true, false, true));
            }
        }
    }

    public void clearEffects(Player p, Bonuses b) {
        for (Bonuses.EffectSpec s : b.effects) {
            PotionEffect cur = p.getPotionEffect(s.type());
            if (cur != null && cur.getAmplifier() == s.amplifier() && cur.getDuration() <= 120) {
                p.removePotionEffect(s.type());
            }
        }
    }

    private void maintenance() {
        maintenanceTick++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            checkExpiry(p);
            if (maintenanceTick % 2 == 0) {
                applyEffects(p);
            }
            if (maintenanceTick % 3 == 0) {
                costumeManager.refresh(p);
            }
        }
        if (maintenanceTick % 10 == 0) {
            data.flushAsyncIfDirty();
        }
    }

    private void checkExpiry(Player p) {
        PlayerData d = data.get(p.getUniqueId());
        long now = System.currentTimeMillis();
        for (Kind kind : Kind.values()) {
            String id = activeId(d, kind);
            if (id == null) {
                continue;
            }
            Long exp = owned(d, kind).get(id);
            if (exp == null || exp <= now) {
                Cosmetic c = def(kind, id);
                deactivate(p, kind);
                msg(p, "&cTwoje " + (kind == Kind.PET ? "zwierzątko" : "przebranie")
                        + (c != null ? " &f" + c.name : "") + " &cwygasło.");
            }
        }
    }
}
