package testudio.ThEnderButt.listener;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import testudio.ThEnderButt.config.MessageService;
import testudio.ThEnderButt.config.PluginSettings;
import testudio.ThEnderButt.integration.AjParkourHook;
import testudio.ThEnderButt.integration.PlaceholderApiHook;
import testudio.ThEnderButt.item.EnderButtItemService;
import testudio.ThEnderButt.util.ConsoleMessages;

public final class PlayerListener implements Listener {
    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private final MessageService messages;
    private final PlaceholderApiHook placeholderApi;
    private final AjParkourHook ajParkour;
    private final EnderButtItemService items;
    private final Map<UUID, Long> cooldowns = new HashMap<UUID, Long>();
    private final Map<UUID, EnderPearl> mountedPearls = new HashMap<UUID, EnderPearl>();
    private final Map<UUID, BukkitTask> passengerTasks = new HashMap<UUID, BukkitTask>();
    private final Set<UUID> mountFallbackAttempted = new HashSet<UUID>();
    private final Set<UUID> restoreAfterRestriction = new HashSet<UUID>();

    public PlayerListener(JavaPlugin plugin, PluginSettings settings, MessageService messages,
                          PlaceholderApiHook placeholderApi, AjParkourHook ajParkour,
                          EnderButtItemService items) {
        this.plugin = plugin;
        this.settings = settings;
        this.messages = messages;
        this.placeholderApi = placeholderApi;
        this.ajParkour = ajParkour;
        this.items = items;
        ajParkour.setStateListener(this::refreshRestriction);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            refreshRestriction(player);
            if (!isRestricted(player) && settings.shouldGiveOnJoin()) {
                giveToPlayer(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (settings.shouldGiveOnRespawn()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                refreshRestriction(player);
                if (!isRestricted(player)) {
                    giveToPlayer(player);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        refreshRestriction(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = event.getItem();
        if (!items.isPluginItem(held, player)) {
            return;
        }
        event.setCancelled(true);

        if (!settings.isEnabledWorld(player.getWorld())) {
            refreshRestriction(player);
            messages.send(player, "world-disabled", placeholderApi, null);
            return;
        }
        if (ajParkour.isPlayerInParkour(player)) {
            refreshRestriction(player);
            return;
        }
        String permission = settings.getItemPermission();
        if (!permission.isEmpty() && !player.hasPermission(permission)) {
            messages.send(player, "no-permission", placeholderApi, null);
            return;
        }
        long remaining = getCooldownRemaining(player);
        if (remaining > 0) {
            Map<String, String> replacements = new HashMap<String, String>();
            replacements.put("time", String.valueOf(remaining));
            messages.send(player, "cooldown", placeholderApi, replacements);
            return;
        }

        final ItemStack launcher = held.clone();
        final int launcherCount = countLaunchers(player);
        EnderPearl pearl = player.launchProjectile(EnderPearl.class);
        UUID playerId = player.getUniqueId();
        mountedPearls.put(playerId, pearl);
        maintainPassenger(player, pearl);
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis()
                + settings.getCooldownSeconds() * 1000L);
        reconcileLauncher(player, launcher, launcherCount, settings.shouldConsumeOnUse());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (settings.isProtectionEnabled("keep-on-death", true)) {
            Iterator<ItemStack> drops = event.getDrops().iterator();
            while (drops.hasNext()) {
                if (items.isPluginItem(drops.next(), player)) {
                    drops.remove();
                }
            }
        }
        removeMountedPearl(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (settings.isItemUnbreakable() && items.isPluginItem(event.getItem(), event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player
                && restoreAfterRestriction.contains(event.getPlayer().getUniqueId())) {
            refreshRestriction((Player) event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (settings.shouldGiveUponClear()) {
            scheduleClearGrant(event.getMessage(), event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        if (settings.shouldGiveUponClear()) {
            Player sender = event.getSender() instanceof Player ? (Player) event.getSender() : null;
            scheduleClearGrant(event.getCommand(), sender);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (!(player.getVehicle() instanceof EnderPearl)
                || !isTrackedPearl(player, (EnderPearl) player.getVehicle())) {
            return;
        }
        if (!settings.isDismountAllowed()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void preventVehicleExit(VehicleExitEvent event) {
        if (!(event.getVehicle() instanceof EnderPearl) || !(event.getExited() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getExited();
        EnderPearl pearl = (EnderPearl) event.getVehicle();
        if (isTrackedPearl(player, pearl) && !settings.isDismountAllowed()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVehicleExit(VehicleExitEvent event) {
        if (event.isCancelled() || !(event.getVehicle() instanceof EnderPearl)
                || !(event.getExited() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getExited();
        EnderPearl pearl = (EnderPearl) event.getVehicle();
        UUID playerId = player.getUniqueId();
        if (!settings.isDismountAllowed() || !isTrackedPearl(player, pearl)) {
            return;
        }
        finishDismount(player, pearl);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPearlHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof EnderPearl)) {
            return;
        }
        Iterator<Map.Entry<UUID, EnderPearl>> iterator = mountedPearls.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, EnderPearl> entry = iterator.next();
            if (entry.getValue().getUniqueId().equals(event.getEntity().getUniqueId())) {
                UUID playerId = entry.getKey();
                iterator.remove();
                cancelPassengerTask(playerId);
                mountFallbackAttempted.remove(playerId);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        removeMountedPearl(event.getPlayer());
        cooldowns.remove(playerId);
        restoreAfterRestriction.remove(playerId);
    }

    public void cleanup() {
        for (EnderPearl pearl : mountedPearls.values()) {
            if (pearl.isValid()) {
                pearl.remove();
            }
        }
        for (BukkitTask task : passengerTasks.values()) {
            task.cancel();
        }
        mountedPearls.clear();
        passengerTasks.clear();
        mountFallbackAttempted.clear();
        cooldowns.clear();
        restoreAfterRestriction.clear();
    }

    public void refreshAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshRestriction(player);
        }
    }

    private int countLaunchers(Player player) {
        int count = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < Math.min(36, contents.length); slot++) {
            ItemStack item = contents[slot];
            if (items.isPluginItem(item, player)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void reconcileLauncher(final Player player, final ItemStack launcher,
                                   final int previousCount, final boolean consume) {
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }
                int currentCount = countLaunchers(player);
                if (consume) {
                    if (currentCount == previousCount) {
                        removeLauncher(player);
                    }
                } else if (currentCount < previousCount) {
                    restoreLaunchers(player, launcher, previousCount - currentCount);
                }
                player.updateInventory();
            }
        });
    }

    private void removeLauncher(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < Math.min(36, contents.length); slot++) {
            ItemStack item = contents[slot];
            if (!items.isPluginItem(item, player)) {
                continue;
            }
            if (item.getAmount() <= 1) {
                player.getInventory().setItem(slot, null);
            } else {
                item.setAmount(item.getAmount() - 1);
                player.getInventory().setItem(slot, item);
            }
            return;
        }
    }

    private void restoreLaunchers(Player player, ItemStack launcher, int amount) {
        ItemStack restored = launcher.clone();
        restored.setAmount(amount);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(restored);
        if (!leftovers.isEmpty()) {
            messages.send(player, "inventory-full", placeholderApi, null);
        }
    }

    private long getCooldownRemaining(Player player) {
        Long expiresAt = cooldowns.get(player.getUniqueId());
        if (expiresAt == null) {
            return 0L;
        }
        long remainingMillis = expiresAt - System.currentTimeMillis();
        if (remainingMillis <= 0) {
            cooldowns.remove(player.getUniqueId());
            return 0L;
        }
        return (remainingMillis + 999L) / 1000L;
    }

    private void refreshRestriction(Player player) {
        boolean restricted = isRestricted(player);
        if (restricted) {
            if (items.hasItem(player)) {
                restoreAfterRestriction.add(player.getUniqueId());
            }
            items.removeItems(player);
            return;
        }
        if (restoreAfterRestriction.contains(player.getUniqueId())) {
            if (items.give(player)) {
                restoreAfterRestriction.remove(player.getUniqueId());
            } else {
                messages.send(player, "inventory-full", placeholderApi, null);
            }
        }
    }

    private boolean isRestricted(Player player) {
        return !settings.isEnabledWorld(player.getWorld()) || ajParkour.isPlayerInParkour(player);
    }

    private void removeMountedPearl(Player player) {
        UUID playerId = player.getUniqueId();
        EnderPearl pearl = mountedPearls.remove(playerId);
        cancelPassengerTask(playerId);
        if (pearl != null && pearl.isValid()) {
            pearl.remove();
        }
    }

    private void maintainPassenger(final Player player, final EnderPearl pearl) {
        final UUID playerId = player.getUniqueId();
        mountFallbackAttempted.remove(playerId);
        cancelPassengerTask(playerId);
        final boolean[] wasMounted = {false};
        final boolean[] warningSent = {false};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !pearl.isValid() || !isTrackedPearl(player, pearl)) {
                    cancelPassengerTask(playerId);
                    return;
                }
                if (isPassenger(player, pearl)) {
                    wasMounted[0] = true;
                    return;
                }
                if (wasMounted[0]) {
                    if (settings.isDismountAllowed()) {
                        finishDismount(player, pearl);
                        return;
                    }
                    attachPassenger(player, pearl);
                    return;
                }
                if (attachPassenger(player, pearl)) {
                    wasMounted[0] = true;
                } else if (!warningSent[0]) {
                    warningSent[0] = true;
                    ConsoleMessages.warning(plugin,
                            "WARNING Spigot has not accepted the Ender Pearl mount; retrying.");
                }
            }
        }, 1L, 1L);
        passengerTasks.put(playerId, task);
    }

    private boolean attachPassenger(Player player, EnderPearl pearl) {
        if (!isPassenger(player, pearl)) {
            pearl.setPassenger(player);
        }
        if (isPassenger(player, pearl)) {
            return true;
        }
        UUID playerId = player.getUniqueId();
        return mountFallbackAttempted.add(playerId) && forceLegacyMount(player, pearl);
    }

    private boolean forceLegacyMount(Player player, EnderPearl pearl) {
        try {
            Object playerHandle = getHandle(player);
            Object pearlHandle = getHandle(pearl);
            Field vehicleField = findField(playerHandle.getClass(), "vehicle");
            Field passengerField = findField(pearlHandle.getClass(), "passenger");
            vehicleField.setAccessible(true);
            passengerField.setAccessible(true);
            Object currentVehicle = vehicleField.get(playerHandle);
            Object currentPassenger = passengerField.get(pearlHandle);
            if ((currentVehicle != null && currentVehicle != pearlHandle)
                    || (currentPassenger != null && currentPassenger != playerHandle)) {
                return false;
            }
            vehicleField.set(playerHandle, pearlHandle);
            passengerField.set(pearlHandle, playerHandle);
            sendLegacyMountPacket(playerHandle, pearlHandle);
            return isPassenger(player, pearl);
        } catch (ReflectiveOperationException exception) {
            ConsoleMessages.error(plugin, "ERROR Spigot 1.8 could not establish the Ender Pearl mount.");
            return false;
        }
    }

    private Object getHandle(Object entity) throws ReflectiveOperationException {
        Method getHandle = entity.getClass().getMethod("getHandle");
        return getHandle.invoke(entity);
    }

    private Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    private void sendLegacyMountPacket(Object playerHandle, Object pearlHandle)
            throws ReflectiveOperationException {
        String nmsPackage = playerHandle.getClass().getPackage().getName();
        Class<?> packetType = Class.forName(nmsPackage + ".PacketPlayOutAttachEntity");
        Constructor<?> packetConstructor = null;
        for (Constructor<?> constructor : packetType.getConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            if (parameters.length == 3 && parameters[0] == int.class
                    && parameters[1].isInstance(playerHandle)
                    && parameters[2].isInstance(pearlHandle)) {
                packetConstructor = constructor;
                break;
            }
        }
        if (packetConstructor == null) {
            throw new NoSuchMethodException("PacketPlayOutAttachEntity constructor");
        }
        Object packet = packetConstructor.newInstance(0, playerHandle, pearlHandle);
        Field connectionField = findField(playerHandle.getClass(), "playerConnection");
        connectionField.setAccessible(true);
        Object connection = connectionField.get(playerHandle);
        Method sendPacket = null;
        for (Method method : connection.getClass().getMethods()) {
            if (method.getName().equals("sendPacket") && method.getParameterTypes().length == 1
                    && method.getParameterTypes()[0].isInstance(packet)) {
                sendPacket = method;
                break;
            }
        }
        if (sendPacket == null) {
            throw new NoSuchMethodException("PlayerConnection.sendPacket");
        }
        sendPacket.invoke(connection, packet);
    }

    private boolean isPassenger(Player player, EnderPearl pearl) {
        org.bukkit.entity.Entity passenger = pearl.getPassenger();
        org.bukkit.entity.Entity vehicle = player.getVehicle();
        return passenger != null && passenger.getUniqueId().equals(player.getUniqueId())
                && vehicle != null && vehicle.getUniqueId().equals(pearl.getUniqueId());
    }

    private void finishDismount(Player player, EnderPearl pearl) {
        UUID playerId = player.getUniqueId();
        mountedPearls.remove(playerId);
        cancelPassengerTask(playerId);
        mountFallbackAttempted.remove(playerId);
        if (settings.isProtectionEnabled("destroy-on-dismount", true)) {
            pearl.remove();
        }
    }

    private void cancelPassengerTask(UUID playerId) {
        BukkitTask task = passengerTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    private boolean isTrackedPearl(Player player, EnderPearl pearl) {
        EnderPearl trackedPearl = mountedPearls.get(player.getUniqueId());
        return trackedPearl != null && trackedPearl.getUniqueId().equals(pearl.getUniqueId());
    }

    private void giveToPlayer(Player player) {
        if (!items.give(player)) {
            messages.send(player, "inventory-full", placeholderApi, null);
        }
    }

    private void scheduleClearGrant(String command, Player defaultTarget) {
        String[] parts = command.trim().split("\\s+");
        if (parts.length == 0) {
            return;
        }
        String commandName = parts[0];
        if (commandName.startsWith("/")) {
            commandName = commandName.substring(1);
        }
        int namespaceSeparator = commandName.indexOf(':');
        if (namespaceSeparator >= 0) {
            commandName = commandName.substring(namespaceSeparator + 1);
        }
        if (!commandName.equalsIgnoreCase("clear")) {
            return;
        }

        Player target = defaultTarget;
        if (parts.length > 1) {
            target = Bukkit.getPlayerExact(parts[1]);
        }
        if (target == null) {
            return;
        }
        Player clearedPlayer = target;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (clearedPlayer.isOnline() && !isRestricted(clearedPlayer)) {
                giveToPlayer(clearedPlayer);
            }
        }, 1L);
    }
}
