package testudio.ThEnderButt.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.ChatColor;
import testudio.ThEnderButt.config.PluginSettings;
import testudio.ThEnderButt.integration.PlaceholderApiHook;

public final class EnderButtItemService {
    private final PluginSettings settings;
    private final PlaceholderApiHook placeholderApi;

    public EnderButtItemService(PluginSettings settings, PlaceholderApiHook placeholderApi) {
        this.settings = settings;
        this.placeholderApi = placeholderApi;
    }

    public ItemStack create(Player player) {
        ItemStack item = new ItemStack(settings.getMaterial(), settings.getQuantity());
        ItemMeta meta = item.getItemMeta();
        String displayName = placeholderApi.apply(player, settings.getItemName());
        meta.setDisplayName(color(displayName));
        List<String> lore = new ArrayList<String>();
        for (String line : settings.getItemLore()) {
            lore.add(color(placeholderApi.apply(player, line)));
        }
        if (!lore.isEmpty()) {
            meta.setLore(lore);
        }
        if (settings.isItemGlowEnabled()) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
        }
        if (settings.isItemHideFlagsEnabled()) {
            meta.addItemFlags(ItemFlag.values());
        }
        item.setItemMeta(meta);
        return item;
    }

    public boolean isPluginItem(ItemStack item, Player player) {
        if (item == null || item.getType() != settings.getMaterial() || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        String expectedName = color(placeholderApi.apply(player, settings.getItemName()));
        if (!meta.hasDisplayName() || !expectedName.equals(meta.getDisplayName())) {
            return false;
        }
        List<String> configuredLore = settings.getItemLore();
        if (configuredLore.isEmpty()) {
            return !meta.hasLore() || meta.getLore().isEmpty();
        }
        List<String> expectedLore = new ArrayList<String>();
        for (String line : configuredLore) {
            expectedLore.add(color(placeholderApi.apply(player, line)));
        }
        return meta.hasLore() && expectedLore.equals(meta.getLore());
    }

    public boolean hasItem(Player player) {
        for (ItemStack item : storageItems(player.getInventory())) {
            if (isPluginItem(item, player)) {
                return true;
            }
        }
        return isPluginItem(player.getItemOnCursor(), player);
    }

    public void removeItems(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isPluginItem(contents[slot], player)) {
                inventory.setItem(slot, null);
            }
        }
        if (isPluginItem(player.getItemOnCursor(), player)) {
            player.setItemOnCursor(null);
        }
    }

    public boolean give(Player player) {
        if (hasItem(player)) {
            return true;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack item = create(player);
        int configuredSlot = settings.getSlot();
        ItemStack current = inventory.getItem(configuredSlot);
        if (current == null || current.getType() == Material.AIR) {
            inventory.setItem(configuredSlot, item);
            return true;
        }
        ItemStack[] contents = inventory.getContents();
        int available = 0;
        for (int slot = 0; slot < Math.min(36, contents.length); slot++) {
            if (contents[slot] == null || contents[slot].getType() == Material.AIR) {
                available += item.getMaxStackSize();
            } else if (contents[slot].isSimilar(item)) {
                available += Math.max(0, contents[slot].getMaxStackSize() - contents[slot].getAmount());
            }
        }
        if (available < item.getAmount()) {
            return false;
        }
        Map<Integer, ItemStack> leftovers = inventory.addItem(item);
        return leftovers.isEmpty();
    }

    private List<ItemStack> storageItems(PlayerInventory inventory) {
        ItemStack[] contents = inventory.getContents();
        List<ItemStack> storage = new ArrayList<ItemStack>();
        for (int slot = 0; slot < Math.min(36, contents.length); slot++) {
            storage.add(contents[slot]);
        }
        return storage;
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
