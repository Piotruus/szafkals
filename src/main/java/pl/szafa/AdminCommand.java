package pl.szafa;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pl.szafa.Cosmetic.Kind;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /szafaadmin give <gracz> <pet|kostium> <id> <dni|perm>
 * /szafaadmin take <gracz> <pet|kostium> <id>
 * /szafaadmin list <gracz>
 * /szafaadmin reload
 * Dziala tez z konsoli (np. nagrody ze skrzynek).
 */
public final class AdminCommand implements CommandExecutor, TabCompleter {

    private final SzafaPlugin plugin;

    public AdminCommand(SzafaPlugin plugin) {
        this.plugin = plugin;
    }

    private static Kind parseKind(String s) {
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "pet", "zwierzatko", "zwierzątko" -> Kind.PET;
            case "kostium", "costume" -> Kind.COSTUME;
            default -> null;
        };
    }

    private OfflinePlayer resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        return Bukkit.getOfflinePlayerIfCached(name);
    }

    private void usage(CommandSender s) {
        plugin.msg(s, "&e/szafaadmin give <gracz> <pet|kostium> <id> <dni|perm>");
        plugin.msg(s, "&e/szafaadmin take <gracz> <pet|kostium> <id>");
        plugin.msg(s, "&e/szafaadmin list <gracz>");
        plugin.msg(s, "&e/szafaadmin reload");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("szafa.admin")) {
            plugin.msg(sender, "&cBrak uprawnień.");
            return true;
        }
        if (args.length == 0) {
            usage(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadAll();
                plugin.msg(sender, "&aPrzeładowano konfigurację.");
            }
            case "list" -> {
                if (args.length < 2) {
                    usage(sender);
                    return true;
                }
                OfflinePlayer target = resolve(args[1]);
                if (target == null) {
                    plugin.msg(sender, "&cNie znaleziono gracza.");
                    return true;
                }
                PlayerData d = plugin.data().get(target.getUniqueId());
                plugin.msg(sender, "&fZwierzątka gracza &e" + args[1] + "&f:");
                printOwned(sender, d.pets);
                plugin.msg(sender, "&fKostiumy gracza &e" + args[1] + "&f:");
                printOwned(sender, d.costumes);
            }
            case "give" -> {
                if (args.length < 5) {
                    usage(sender);
                    return true;
                }
                give(sender, args);
            }
            case "take" -> {
                if (args.length < 4) {
                    usage(sender);
                    return true;
                }
                take(sender, args);
            }
            default -> usage(sender);
        }
        return true;
    }

    private void printOwned(CommandSender s, Map<String, Long> map) {
        if (map.isEmpty()) {
            plugin.msg(s, "&8- brak");
            return;
        }
        map.forEach((id, exp) -> plugin.msg(s, "&8- &f" + id + " &7do &f" + plugin.format(exp)));
    }

    private void give(CommandSender sender, String[] args) {
        OfflinePlayer target = resolve(args[1]);
        Kind kind = parseKind(args[2]);
        if (target == null) {
            plugin.msg(sender, "&cNie znaleziono gracza (musi być online lub już był na serwerze).");
            return;
        }
        if (kind == null) {
            plugin.msg(sender, "&cTyp: pet albo kostium.");
            return;
        }
        Cosmetic c = plugin.def(kind, args[3]);
        if (c == null) {
            plugin.msg(sender, "&cNie ma takiego id: &f" + args[3]);
            return;
        }
        Integer days;
        if (args[4].equalsIgnoreCase("perm")) {
            days = null;
        } else {
            try {
                days = Integer.parseInt(args[4]);
            } catch (NumberFormatException ex) {
                plugin.msg(sender, "&cPodaj liczbę dni albo 'perm'.");
                return;
            }
            if (days <= 0) {
                plugin.msg(sender, "&cLiczba dni musi być większa od 0.");
                return;
            }
        }
        long exp = plugin.give(target.getUniqueId(), c, days);
        plugin.msg(sender, "&aNadano &f" + c.name + " &agraczowi &f" + args[1]
                + " &7(do " + plugin.format(exp) + ")");
        Player online = target.getPlayer();
        if (online != null) {
            plugin.msg(online, "&aOtrzymałeś: &f" + c.name + " &7(do " + plugin.format(exp)
                    + "). Wpisz &f/szafa&7.");
        }
    }

    private void take(CommandSender sender, String[] args) {
        OfflinePlayer target = resolve(args[1]);
        Kind kind = parseKind(args[2]);
        if (target == null || kind == null) {
            plugin.msg(sender, "&cZły gracz lub typ (pet / kostium).");
            return;
        }
        Cosmetic c = plugin.def(kind, args[3]);
        if (c == null) {
            plugin.msg(sender, "&cNie ma takiego id: &f" + args[3]);
            return;
        }
        boolean had = plugin.take(target.getUniqueId(), c);
        plugin.msg(sender, had ? "&aZabrano &f" + c.name + "&a." : "&cGracz tego nie miał.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("szafa.admin")) {
            return out;
        }
        switch (args.length) {
            case 1 -> out.addAll(List.of("give", "take", "list", "reload"));
            case 2 -> {
                if (!args[0].equalsIgnoreCase("reload")) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        out.add(p.getName());
                    }
                }
            }
            case 3 -> {
                if (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("take")) {
                    out.addAll(List.of("pet", "kostium"));
                }
            }
            case 4 -> {
                Kind kind = parseKind(args[2]);
                if (kind != null) {
                    for (Cosmetic c : plugin.defs(kind)) {
                        out.add(c.id);
                    }
                }
            }
            case 5 -> {
                if (args[0].equalsIgnoreCase("give")) {
                    out.addAll(List.of("7", "14", "30", "perm"));
                }
            }
            default -> {
            }
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
