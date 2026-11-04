package testudio.ThEnderButt.config;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import testudio.ThEnderButt.util.ConsoleMessages;

public final class PluginSettings {
    private final JavaPlugin plugin;
    private boolean worldListValid = true;

    public PluginSettings(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void validate() {
        FileConfiguration config = plugin.getConfig();
        validateNumber(config.get("item.slot"), "item.slot", 0, 35, 8);
        validateMaterial(config.getString("item.material", "ENDER_PEARL"));
        validateNumber(config.get("item.quantity"), "item.quantity", 1,
                Math.min(64, getMaterial().getMaxStackSize()), 1);
        validateNumber(config.get("item.cooldown"), "item.cooldown", 0, Integer.MAX_VALUE, 3);
        Object worlds = config.get("enabled-worlds");
        worldListValid = worlds == null || isStringList(worlds);
        if (!worldListValid) {
            ConsoleMessages.warning(plugin, "WARNING Invalid enabled-worlds; no worlds will be enabled.");
        }
    }

    public int getSlot() {
        return boundedInt("item.slot", 8, 0, 35);
    }

    public int getQuantity() {
        return boundedInt("item.quantity", 1, 1, Math.min(64, getMaterial().getMaxStackSize()));
    }

    public Material getMaterial() {
        String value = plugin.getConfig().getString("item.material", "ENDER_PEARL");
        Material material = value == null ? null : Material.getMaterial(value.toUpperCase(Locale.ENGLISH));
        return material == null || material == Material.AIR ? Material.ENDER_PEARL : material;
    }

    public String getItemName() {
        String name = plugin.getConfig().getString("item.name", "&b&lEnder Pearl Launcher");
        return name == null ? "" : name;
    }

    public List<String> getItemLore() {
        List<String> lore = plugin.getConfig().getStringList("item.lore");
        return lore == null ? Collections.<String>emptyList() : lore;
    }

    public boolean isEnabledWorld(World world) {
        if (!worldListValid) {
            return false;
        }
        List<String> worlds = plugin.getConfig().getStringList("enabled-worlds");
        return worlds.isEmpty() || worlds.contains(world.getName());
    }

    public boolean isParkourEnabled() {
        return plugin.getConfig().getBoolean("addons.aj-parkour", true);
    }

    public String getItemPermission() {
        String permission = plugin.getConfig().getString("item.permission", "");
        return permission == null ? "" : permission.trim();
    }

    public int getCooldownSeconds() {
        return boundedInt("item.cooldown", 3, 0, Integer.MAX_VALUE);
    }

    public boolean isProtectionEnabled(String key, boolean defaultValue) {
        return plugin.getConfig().getBoolean("protection." + key, defaultValue);
    }

    public boolean isDismountAllowed() {
        return plugin.getConfig().getBoolean("protection.allow-dismount", true);
    }

    public boolean shouldGiveOnJoin() {
        return plugin.getConfig().getBoolean("give.on-join", true);
    }

    public boolean shouldGiveOnRespawn() {
        return plugin.getConfig().getBoolean("give.on-respawn", true);
    }

    public boolean shouldGiveUponClear() {
        return plugin.getConfig().getBoolean("give.upon-clear", true);
    }

    public boolean shouldConsumeOnUse() {
        return plugin.getConfig().getBoolean("protection.consume-on-use", false);
    }

    public boolean isItemGlowEnabled() {
        return plugin.getConfig().getBoolean("item.glow", true);
    }

    public boolean isItemHideFlagsEnabled() {
        return plugin.getConfig().getBoolean("item.hide-flags", true);
    }

    public boolean isItemUnbreakable() {
        return plugin.getConfig().getBoolean("item.unbreakable", true);
    }

    public boolean isPlaceholderApiEnabled() {
        return plugin.getConfig().getBoolean("addons.placeholderapi", true);
    }

    private int boundedInt(String path, int fallback, int minimum, int maximum) {
        Object value = plugin.getConfig().get(path);
        if (!(value instanceof Number)) {
            return fallback;
        }
        int number = ((Number) value).intValue();
        if (number < minimum || number > maximum) {
            return fallback;
        }
        return number;
    }

    private void validateNumber(Object value, String path, int minimum, int maximum, int fallback) {
        if (!(value instanceof Number)) {
            ConsoleMessages.warning(plugin, "WARNING Invalid " + path + "; using " + fallback + ".");
            return;
        }
        int number = ((Number) value).intValue();
        if (number < minimum || number > maximum) {
            ConsoleMessages.warning(plugin, "WARNING Invalid " + path + "; using " + fallback + ".");
        }
    }

    private void validateMaterial(String value) {
        Material material = value == null ? null : Material.getMaterial(value.toUpperCase(Locale.ENGLISH));
        if (material == null || material == Material.AIR) {
            ConsoleMessages.warning(plugin, "WARNING Invalid item.material; using ENDER_PEARL.");
        }
    }

    private boolean isStringList(Object value) {
        if (!(value instanceof List)) {
            return false;
        }
        for (Object world : (List<?>) value) {
            if (!(world instanceof String)) {
                return false;
            }
        }
        return true;
    }
}
