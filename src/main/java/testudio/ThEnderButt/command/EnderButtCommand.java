package testudio.ThEnderButt.command;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import testudio.ThEnderButt.Main;
import testudio.ThEnderButt.config.MessageService;
import testudio.ThEnderButt.config.PluginSettings;
import testudio.ThEnderButt.integration.AjParkourHook;
import testudio.ThEnderButt.integration.PlaceholderApiHook;
import testudio.ThEnderButt.item.EnderButtItemService;

public final class EnderButtCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private final MessageService messages;
    private final PlaceholderApiHook placeholderApi;
    private final AjParkourHook ajParkour;
    private final EnderButtItemService items;

    public EnderButtCommand(JavaPlugin plugin, PluginSettings settings, MessageService messages,
                            PlaceholderApiHook placeholderApi, AjParkourHook ajParkour,
                            EnderButtItemService items) {
        this.plugin = plugin;
        this.settings = settings;
        this.messages = messages;
        this.placeholderApi = placeholderApi;
        this.ajParkour = ajParkour;
        this.items = items;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (arguments.length == 0 || arguments[0].equalsIgnoreCase("help")) {
            messages.sendHelp(sender, placeholderApi);
            return true;
        }
        if (arguments[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("thenderbutt.reload")) {
                messages.send(sender, "no-permission", placeholderApi, Collections.<String, String>emptyMap());
                return true;
            }
            long start = System.currentTimeMillis();
            if (!((Main) plugin).reloadPlugin()) {
                messages.send(sender, "reload-failed", placeholderApi, Collections.<String, String>emptyMap());
                return true;
            }
            Map<String, String> replacements = new HashMap<String, String>();
            replacements.put("time", String.valueOf(System.currentTimeMillis() - start));
            messages.send(sender, "reload", placeholderApi, replacements);
            return true;
        }
        if (arguments[0].equalsIgnoreCase("give")) {
            giveItem(sender, arguments);
            return true;
        }
        messages.sendHelp(sender, placeholderApi);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] arguments) {
        if (arguments.length == 1) {
            return matching(arguments[0], "help", "reload", "give");
        }
        if (arguments.length == 2 && arguments[0].equalsIgnoreCase("give")
                && sender.hasPermission("thenderbutt.give")) {
            java.util.ArrayList<String> names = new java.util.ArrayList<String>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(java.util.Locale.ENGLISH)
                        .startsWith(arguments[1].toLowerCase(java.util.Locale.ENGLISH))) {
                    names.add(player.getName());
                }
            }
            return names;
        }
        return Collections.emptyList();
    }

    private void giveItem(CommandSender sender, String[] arguments) {
        if (!sender.hasPermission("thenderbutt.give")) {
            messages.send(sender, "no-permission", placeholderApi, Collections.<String, String>emptyMap());
            return;
        }
        if (arguments.length < 2) {
            messages.sendHelp(sender, placeholderApi);
            return;
        }
        Player target = Bukkit.getPlayerExact(arguments[1]);
        if (target == null) {
            Map<String, String> replacements = new HashMap<String, String>();
            replacements.put("player", arguments[1]);
            messages.send(sender, "player-not-found", placeholderApi, replacements);
            return;
        }
        if (!settings.isEnabledWorld(target.getWorld())) {
            items.removeItems(target);
            messages.send(target, "world-disabled", placeholderApi, Collections.<String, String>emptyMap());
            return;
        }
        if (ajParkour.isPlayerInParkour(target)) {
            items.removeItems(target);
            return;
        }
        if (!items.give(target)) {
            messages.send(target, "inventory-full", placeholderApi, Collections.<String, String>emptyMap());
            return;
        }
        Map<String, String> replacements = new HashMap<String, String>();
        replacements.put("player", target.getName());
        messages.send(sender, "enderbutt-given", placeholderApi, replacements);
        messages.send(target, "enderbutt-recieved", placeholderApi, Collections.<String, String>emptyMap());
    }

    private List<String> matching(String input, String... options) {
        java.util.ArrayList<String> matches = new java.util.ArrayList<String>();
        for (String option : options) {
            if (option.startsWith(input.toLowerCase(java.util.Locale.ENGLISH))) {
                matches.add(option);
            }
        }
        return matches;
    }
}
