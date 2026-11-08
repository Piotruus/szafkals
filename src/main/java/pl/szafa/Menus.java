package pl.szafa;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.szafa.Cosmetic.Kind;

import java.util.ArrayList;
import java.util.List;

public final class Menus implements Listener {

    private static final int PET_SLOT = 20;
    private static final int INFO_SLOT = 22;
    private static final int COSTUME_SLOT = 24;
    private static final int BACK_SLOT = 49;

    // Y = zolty naroznik, O = pomaranczowy, L = jasnoszary, G = szary, . = tlo
    private static final String[] LAYOUT = {
            "YOLLGLLOY",
            "O.......O",
            "L.......L",
            "L.......L",
            "O.......O",
            "YOLLGLLOY"
    };

    private static final List<Integer> LIST_SLOTS = new ArrayList<>();

    static {
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                LIST_SLOTS.add(row * 9 + col);
            }
        }
    }

    /** Holder oznaczajacy nasze GUI. kind == null => glowne okno SZAFA. */
    private static final class Holder implements InventoryHolder {
        final Kind kind;
        Inventory inv;

        Holder(Kind kind) {
            this.kind = kind;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final SzafaPlugin plugin;

    public Menus(SzafaPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ otwieranie

    public void openMain(Player p) {
        Holder h = new Holder(null);
        Inventory inv = Bukkit.createInventory(h, 54, Text.c("&a&lSZAFA"));
        h.inv = inv;
        renderMain(p, inv);
        p.openInventory(inv);
    }

    public void openList(Player p, Kind kind) {
        Holder h = new Holder(kind);
        String title = kind == Kind.PET ? "&6&lLista Zwierzątek" : "&6&lLista Kostiumów";
        Inventory inv = Bukkit.createInventory(h, 54, Text.c(title));
        h.inv = inv;
        renderList(p, inv, kind);
        p.openInventory(inv);
    }

    // ------------------------------------------------------------------ rysowanie

    private void renderMain(Player p, Inventory inv) {
        fillFrame(inv);
        Cosmetic pet = plugin.activeOf(p, Kind.PET);
        Cosmetic costume = plugin.activeOf(p, Kind.COSTUME);

        inv.setItem(PET_SLOT, pet != null
                ? icon(p, pet, true)
                : placeholder("&cBrak aktywnego peta", "&7Kliknij, aby wybrać zwierzątko."));
        inv.setItem(COSTUME_SLOT, costume != null
                ? icon(p, costume, true)
                : placeholder("&cBrak aktywnego kostiumu", "&7Kliknij, aby wybrać kostium."));
        inv.setItem(INFO_SLOT, info());
    }

    private void renderList(Player p, Inventory inv, Kind kind) {
        fillFrame(inv);
        List<Cosmetic> defs = new ArrayList<>(plugin.defs(kind));
        for (int i = 0; i < defs.size() && i < LIST_SLOTS.size(); i++) {
            inv.setItem(LIST_SLOTS.get(i), icon(p, defs.get(i), false));
        }
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta m = back.getItemMeta();
        m.displayName(Text.c("&eWróć"));
        back.setItemMeta(m);
        inv.setItem(BACK_SLOT, back);
    }

    private void fillFrame(Inventory inv) {
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                inv.setItem(row * 9 + col, pane(LAYOUT[row].charAt(col)));
            }
        }
    }

    private ItemStack pane(char ch) {
        Material mat = switch (ch) {
            case 'Y' -> Material.YELLOW_STAINED_GLASS_PANE;
            case 'O' -> Material.ORANGE_STAINED_GLASS_PANE;
            case 'L' -> Material.LIGHT_GRAY_STAINED_GLASS_PANE;
            default -> Material.GRAY_STAINED_GLASS_PANE;
        };
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setHideTooltip(true);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack placeholder(String name, String lore) {
        ItemStack it = new ItemStack(Material.BARRIER);
        ItemMeta m = it.getItemMeta();
        m.displayName(Text.c(name));
        m.lore(List.of(Text.c(lore)));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack info() {
        ItemStack it = new ItemStack(Material.NETHER_STAR);
        ItemMeta m = it.getItemMeta();
        m.displayName(Text.c("&e&lSZAFA"));
        m.lore(List.of(
                Text.c("&7Po lewej: &fzwierzątko &7(leci za Tobą)"),
                Text.c("&7Po prawej: &fkostium &7(widzą go wszyscy)"),
                Component.empty(),
                Text.c("&eLPM &7- lista, &ePPM &7- zdejmij")));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack icon(Player p, Cosmetic c, boolean forMain) {
        ItemStack it = new ItemStack(c.kind == Kind.PET ? Material.PAPER : Material.LEATHER_CHESTPLATE);
        ItemMeta m = it.getItemMeta();
        if (c.itemModel != null) {
            m.setItemModel(c.itemModel);
        }
        m.displayName(Text.c(c.name));

        PlayerData d = plugin.data().get(p.getUniqueId());
        Long exp = SzafaPlugin.owned(d, c.kind).get(c.id);
        boolean active = c.id.equals(SzafaPlugin.activeId(d, c.kind));
        boolean valid = exp != null && exp > System.currentTimeMillis();

        List<Component> lore = new ArrayList<>();
        for (String line : c.description) {
            lore.add(Text.c(line));
        }
        lore.add(Component.empty());

        String noun = c.kind == Kind.PET ? "ZWIERZĄTKO" : "KOSTIUM";
        if (exp == null) {
            lore.add(Text.c("&c✘ &fNie posiadasz tego " + c.noun() + "."));
        } else if (!valid) {
            lore.add(Text.c("&c✘ &f" + noun + " WYGASŁO: &c" + plugin.format(exp)));
        } else if (exp == Long.MAX_VALUE) {
            lore.add(Text.c("&a✔ &f" + noun + " &aBEZ LIMITU CZASU"));
        } else {
            lore.add(Text.c("&c✘ &f" + noun + " WYGASA: &c" + plugin.format(exp)));
        }
        lore.add(Component.empty());

        if (active) {
            lore.add(Text.c("&a✔ &a&lAKTYWNE"));
        }
        if (forMain) {
            lore.add(Text.c("&e&lLPM &7- otwórz listę"));
            lore.add(Text.c("&e&lPPM &7- zdejmij"));
        } else if (valid) {
            if (c.kind == Kind.PET) {
                lore.add(Text.c(active
                        ? "&5◆ &e&lKLIKNIJ &fABY DEZAKTYWOWAĆ!"
                        : "&5◆ &e&lKLIKNIJ &fABY AKTYWOWAĆ!"));
            } else {
                lore.add(Text.c(active
                        ? "&5◆ &e&lKLIKNIJ PPM &fABY ZDJĄĆ!"
                        : "&5◆ &e&lKLIKNIJ PPM &fABY ZAŁOŻYĆ!"));
            }
        }
        lore.add(Component.empty());
        lore.add(Text.c(c.rarity));

        m.lore(lore);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (active) {
            m.setEnchantmentGlintOverride(true);
        }
        it.setItemMeta(m);
        return it;
    }

    // ------------------------------------------------------------------ klikniecia

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder h)) {
            return;
        }
        e.setCancelled(true);
        if (e.getClickedInventory() != top || !(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        if (h.kind == null) {
            clickMain(p, h, e.getSlot(), e.getClick());
        } else {
            clickList(p, h, e.getSlot(), e.getClick());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }

    private void clickMain(Player p, Holder h, int slot, ClickType click) {
        Kind kind;
        if (slot == PET_SLOT) {
            kind = Kind.PET;
        } else if (slot == COSTUME_SLOT) {
            kind = Kind.COSTUME;
        } else {
            return;
        }
        if (click.isRightClick()) {
            if (plugin.deactivate(p, kind)) {
                plugin.msg(p, kind == Kind.PET ? "Zdjęto zwierzątko." : "Zdjęto kostium.");
                click(p);
            }
            renderMain(p, h.inv);
        } else {
            click(p);
            openList(p, kind);
        }
    }

    private void clickList(Player p, Holder h, int slot, ClickType click) {
        if (slot == BACK_SLOT) {
            click(p);
            openMain(p);
            return;
        }
        int idx = LIST_SLOTS.indexOf(slot);
        List<Cosmetic> defs = new ArrayList<>(plugin.defs(h.kind));
        if (idx < 0 || idx >= defs.size()) {
            return;
        }
        Cosmetic c = defs.get(idx);
        PlayerData d = plugin.data().get(p.getUniqueId());
        Long exp = SzafaPlugin.owned(d, c.kind).get(c.id);
        if (exp == null) {
            plugin.msg(p, "&cNie posiadasz tego " + c.noun() + ".");
            return;
        }
        if (exp <= System.currentTimeMillis()) {
            plugin.msg(p, "&cTo " + c.noun() + " już wygasło.");
            return;
        }
        boolean active = c.id.equals(SzafaPlugin.activeId(d, c.kind));

        if (c.kind == Kind.COSTUME && !click.isRightClick()) {
            plugin.msg(p, "Kliknij &fPPM&7, aby założyć lub zdjąć kostium.");
            return;
        }
        if (active) {
            plugin.deactivate(p, c.kind);
            plugin.msg(p, "Zdjęto: &f" + c.name);
        } else {
            plugin.activate(p, c);
        }
        click(p);
        renderList(p, h.inv, h.kind);
    }

    private void click(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
    }
}
