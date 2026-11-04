package testudio.ThEnderButt.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;
import testudio.ThEnderButt.config.PluginSettings;
import testudio.ThEnderButt.item.EnderButtItemService;
import testudio.ThEnderButt.util.ConsoleMessages;


public final class InventoryProtectionListener implements Listener {
    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private final EnderButtItemService items;
    private final Map<UUID, UUID> dropOwners = new HashMap<UUID, UUID>();
    private boolean handSwapWarningSent;

    public InventoryProtectionListener(JavaPlugin plugin, PluginSettings settings, EnderButtItemService items) {
        this.plugin = plugin;
        this.settings = settings;
        this.items = items;
    }

    public void registerHandSwapProtection() {
        if (!settings.isProtectionEnabled("prevent-move", true)) {
            return;
        }
        try {
            Class<?> eventClass = Class.forName(
                    "org.bukkit.event.player.PlayerSwapHandItemsEvent", false,
                    plugin.getClass().getClassLoader());
            Class<? extends Event> swapEventClass = eventClass.asSubclass(Event.class);
            EventExecutor executor = (listener, event) -> protectHandSwap(event);
            plugin.getServer().getPluginManager().registerEvent(
                    swapEventClass, this, EventPriority.HIGHEST, executor, plugin, true);
        } catch (ClassNotFoundException exception) {
            return;
        } catch (ClassCastException exception) {
            ConsoleMessages.warning(plugin, "WARNING Hand-swap protection is unavailable on this server.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        boolean currentItem = items.isPluginItem(event.getCurrentItem(), player);
        boolean cursorItem = items.isPluginItem(event.getCursor(), player);
        boolean hotbarItem = isPluginItemInHotbar(event, player);
        if (!currentItem && !cursorItem && !hotbarItem) {
            return;
        }
        if (settings.isProtectionEnabled("prevent-move", true)) {
            event.setCancelled(true);
            return;
        }
        InventoryType topType = event.getView().getTopInventory().getType();
        if (settings.isProtectionEnabled("prevent-modify", true)
                && (topType == InventoryType.ANVIL || topType == InventoryType.ENCHANTING)) {
            event.setCancelled(true);
            return;
        }
        if (settings.isProtectionEnabled("prevent-destroy", true)
                && (topType == InventoryType.ANVIL || topType == InventoryType.ENCHANTING
                || topType == InventoryType.WORKBENCH || topType == InventoryType.CRAFTING)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        if (!items.isPluginItem(event.getOldCursor(), player)) {
            return;
        }
        if (settings.isProtectionEnabled("prevent-move", true)
                || settings.isProtectionEnabled("prevent-modify", true)
                || settings.isProtectionEnabled("prevent-destroy", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!items.isPluginItem(event.getItemDrop().getItemStack(), event.getPlayer())) {
            return;
        }
        if (settings.isProtectionEnabled("prevent-drop", true)) {
            event.setCancelled(true);
            return;
        }
        dropOwners.put(event.getItemDrop().getUniqueId(), event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(PlayerPickupItemEvent event) {
        UUID owner = dropOwners.get(event.getItem().getUniqueId());
        if (owner == null && !items.isPluginItem(event.getItem().getItemStack(), event.getPlayer())) {
            return;
        }
        if (settings.isProtectionEnabled("prevent-pickup", true)
                && (owner == null || !owner.equals(event.getPlayer().getUniqueId()))) {
            event.setCancelled(true);
            return;
        }
        dropOwners.remove(event.getItem().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDespawn(ItemDespawnEvent event) {
        dropOwners.remove(event.getEntity().getUniqueId());
    }

    private boolean isPluginItemInHotbar(InventoryClickEvent event, Player player) {
        int hotbarButton = event.getHotbarButton();
        if (hotbarButton < 0 || hotbarButton >= 9) {
            return false;
        }
        ItemStack item = player.getInventory().getItem(hotbarButton);
        return items.isPluginItem(item, player);
    }

    private void protectHandSwap(Event event) {
        if (!(event instanceof PlayerEvent) || !(event instanceof Cancellable)) {
            return;
        }
        Player player = ((PlayerEvent) event).getPlayer();
        try {
            ItemStack mainHand = (ItemStack) event.getClass().getMethod("getMainHandItem").invoke(event);
            ItemStack offHand = (ItemStack) event.getClass().getMethod("getOffHandItem").invoke(event);
            if (items.isPluginItem(mainHand, player) || items.isPluginItem(offHand, player)) {
                ((Cancellable) event).setCancelled(true);
            }
        } catch (ReflectiveOperationException exception) {
            if (!handSwapWarningSent) {
                ConsoleMessages.warning(plugin, "WARNING Unable to protect the launcher from hand swapping.");
                handSwapWarningSent = true;
            }
        }
    }
}
