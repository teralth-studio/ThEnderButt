package testudio.ThEnderButt.integration;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import testudio.ThEnderButt.config.PluginSettings;
import testudio.ThEnderButt.util.ConsoleMessages;

public final class AjParkourHook {
    public interface ParkourStateListener {
        void onStateChange(Player player);
    }

    private static final String API_CLASS = "us.ajg0702.parkour.api.AjPakour";
    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private Method playerInParkour;
    private boolean integrationFailed;
    private boolean warned;
    private boolean eventsRegistered;

    public AjParkourHook(JavaPlugin plugin, PluginSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    public boolean initialize() {
        playerInParkour = null;
        integrationFailed = false;
        if (!settings.isParkourEnabled() || !Bukkit.getPluginManager().isPluginEnabled("ajParkour")) {
            return true;
        }
        Plugin parkourPlugin = Bukkit.getPluginManager().getPlugin("ajParkour");
        if (parkourPlugin == null) {
            return true;
        }
        try {
            Class<?> api = Class.forName(API_CLASS, false, parkourPlugin.getClass().getClassLoader());
            playerInParkour = api.getMethod("playerInParkour", Player.class);
            if (!eventsRegistered) {
                registerStateEvents(parkourPlugin, "us.ajg0702.parkour.api.events.PlayerStartParkourEvent");
                registerStateEvents(parkourPlugin, "us.ajg0702.parkour.api.events.PlayerEndParkourEvent");
                eventsRegistered = true;
            }
            warned = false;
            return true;
        } catch (ClassNotFoundException exception) {
            failInitialization("ajParkour API classes were not found.");
        } catch (NoSuchMethodException exception) {
            failInitialization("ajParkour does not expose the expected API.");
        }
        return false;
    }

    public boolean isPlayerInParkour(Player player) {
        if (!settings.isParkourEnabled() || !Bukkit.getPluginManager().isPluginEnabled("ajParkour")) {
            return false;
        }
        if (integrationFailed) {
            return true;
        }
        if (playerInParkour == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(playerInParkour.invoke(null, player));
        } catch (IllegalAccessException exception) {
            failAtRuntime();
        } catch (InvocationTargetException exception) {
            failAtRuntime();
        }
        return true;
    }

    private void registerStateEvents(Plugin parkourPlugin, String eventName) throws ClassNotFoundException {
        Class<?> eventClass = Class.forName(eventName, false, parkourPlugin.getClass().getClassLoader());
        if (!Event.class.isAssignableFrom(eventClass)) {
            throw new ClassNotFoundException("ajParkour event type is incompatible.");
        }
        @SuppressWarnings("unchecked")
        Class<? extends Event> typedEventClass = (Class<? extends Event>) eventClass;
        Listener listener = new Listener() {
        };
        EventExecutor executor = (registeredListener, event) -> {
            try {
                Object eventPlayer = event.getClass().getMethod("getPlayer").invoke(event);
                if (eventPlayer instanceof Player) {
                    plugin.getServer().getScheduler().runTask(plugin,
                            () -> notifyStateListener((Player) eventPlayer));
                }
            } catch (NoSuchMethodException exception) {
                failAtRuntime();
            } catch (IllegalAccessException exception) {
                failAtRuntime();
            } catch (InvocationTargetException exception) {
                failAtRuntime();
            }
        };
        Bukkit.getPluginManager().registerEvent(typedEventClass, listener, EventPriority.MONITOR, executor, plugin, true);
    }

    private ParkourStateListener stateListener;

    public void setStateListener(ParkourStateListener stateListener) {
        this.stateListener = stateListener;
    }

    private void notifyStateListener(Player player) {
        if (stateListener != null) {
            stateListener.onStateChange(player);
        }
    }

    private void failInitialization(String message) {
        integrationFailed = true;
        ConsoleMessages.warning(plugin, "WARNING " + message);
    }

    private void failAtRuntime() {
        integrationFailed = true;
        if (!warned) {
            ConsoleMessages.warning(plugin, "WARNING ajParkour integration failed; pearl use is blocked for safety.");
            warned = true;
        }
    }
}
