package testudio.ThEnderButt.config;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import testudio.ThEnderButt.integration.PlaceholderApiHook;
import testudio.ThEnderButt.util.ConsoleMessages;

public final class MessageService {
    private final JavaPlugin plugin;
    private YamlConfiguration messages;

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(file);
        } catch (IOException exception) {
            ConsoleMessages.error(plugin, "ERROR Unable to read messages.yml.");
            return false;
        } catch (InvalidConfigurationException exception) {
            ConsoleMessages.error(plugin, "ERROR messages.yml contains invalid YAML.");
            return false;
        }
        messages = loaded;
        return true;
    }

    public void send(CommandSender sender, String key, PlaceholderApiHook placeholderApi,
                     Map<String, String> replacements) {
        String message = messages.getString(key, "");
        if (message == null || message.isEmpty()) {
            return;
        }
        String prefix = messages.getString("prefix", "");
        sendRaw(sender, (prefix == null ? "" : prefix) + message, placeholderApi, replacements);
    }

    public void sendRaw(CommandSender sender, String text, PlaceholderApiHook placeholderApi,
                        Map<String, String> replacements) {
        String rendered = text;
        if (sender instanceof Player) {
            rendered = placeholderApi.apply((Player) sender, rendered);
        }
        if (replacements != null) {
            for (Map.Entry<String, String> replacement : replacements.entrySet()) {
                rendered = rendered.replace("%" + replacement.getKey() + "%",
                        replacement.getValue() == null ? "" : replacement.getValue());
            }
        }
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', rendered));
    }

    public void sendHelp(CommandSender sender, PlaceholderApiHook placeholderApi) {
        List<String> help = messages.getStringList("help");
        String prefix = messages.getString("prefix", "");
        if (prefix == null) {
            prefix = "";
        }
        if (help == null) {
            help = Collections.emptyList();
        }
        for (String line : help) {
            sendRaw(sender, prefix + line, placeholderApi, Collections.<String, String>emptyMap());
        }
    }
}
