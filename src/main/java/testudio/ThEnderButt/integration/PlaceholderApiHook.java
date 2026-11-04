package testudio.ThEnderButt.integration;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import testudio.ThEnderButt.config.PluginSettings;
import testudio.ThEnderButt.util.ConsoleMessages;

public final class PlaceholderApiHook {
    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private Method setPlaceholders;
    private boolean warned;

    public PlaceholderApiHook(JavaPlugin plugin, PluginSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    public boolean initialize() {
        setPlaceholders = null;
        if (!settings.isPlaceholderApiEnabled() || !Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return true;
        }
        Plugin placeholderPlugin = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        if (placeholderPlugin == null) {
            return true;
        }
        try {
            Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI", false,
                    placeholderPlugin.getClass().getClassLoader());
            setPlaceholders = api.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
            warned = false;
            return true;
        } catch (ClassNotFoundException exception) {
            ConsoleMessages.warning(plugin, "WARNING PlaceholderAPI API class was not found.");
        } catch (NoSuchMethodException exception) {
            ConsoleMessages.warning(plugin, "WARNING PlaceholderAPI does not expose the expected API.");
        }
        return false;
    }

    public String apply(Player player, String text) {
        if (setPlaceholders == null) {
            return text;
        }
        try {
            String result = (String) setPlaceholders.invoke(null, player, text);
            if (result != null) {
                return result;
            }
            warnInvocationFailure();
        } catch (IllegalAccessException exception) {
            warnInvocationFailure();
        } catch (InvocationTargetException exception) {
            warnInvocationFailure();
        } catch (ClassCastException exception) {
            warnInvocationFailure();
        }
        return text;
    }

    private void warnInvocationFailure() {
        if (!warned) {
            ConsoleMessages.warning(plugin, "WARNING PlaceholderAPI expansion failed; unresolved placeholders will remain.");
            warned = true;
        }
    }
}
