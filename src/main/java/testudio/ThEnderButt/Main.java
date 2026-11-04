package testudio.ThEnderButt;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import testudio.ThEnderButt.command.EnderButtCommand;
import testudio.ThEnderButt.config.MessageService;
import testudio.ThEnderButt.config.PluginSettings;
import testudio.ThEnderButt.integration.AjParkourHook;
import testudio.ThEnderButt.integration.PlaceholderApiHook;
import testudio.ThEnderButt.item.EnderButtItemService;
import testudio.ThEnderButt.listener.InventoryProtectionListener;
import testudio.ThEnderButt.listener.PlayerListener;
import testudio.ThEnderButt.util.ConsoleMessages;

public final class Main extends JavaPlugin {
    private static final int BSTATS_ID = 35121;

    private PluginSettings settings;
    private MessageService messages;
    private PlaceholderApiHook placeholderApi;
    private AjParkourHook ajParkour;
    private EnderButtItemService items;
    private PlayerListener playerListener;
    private Metrics metrics;

    @Override
    public void onEnable() {
        ConsoleMessages.beginStartup();
        ConsoleMessages.printBanner(this);
        ConsoleMessages.info(this, "&rStarting plugin...");
        ConsoleMessages.info(this, "&rJava version detected: &e" + System.getProperty("java.version"));

        saveDefaultConfig();
        settings = new PluginSettings(this);
        settings.validate();
        messages = new MessageService(this);
        if (!messages.load()) {
            failStartup();
            setEnabled(false);
            return;
        }

        placeholderApi = new PlaceholderApiHook(this, settings);
        ajParkour = new AjParkourHook(this, settings);
        initializeOptionalPlugin("PlaceholderAPI", settings.isPlaceholderApiEnabled(), placeholderApi::initialize);
        initializeOptionalPlugin("ajParkour", settings.isParkourEnabled(), ajParkour::initialize);

        items = new EnderButtItemService(settings, placeholderApi);
        playerListener = new PlayerListener(this, settings, messages, placeholderApi, ajParkour, items);
        Bukkit.getPluginManager().registerEvents(playerListener, this);
        InventoryProtectionListener inventoryProtectionListener =
                new InventoryProtectionListener(this, settings, items);
        Bukkit.getPluginManager().registerEvents(inventoryProtectionListener, this);
        inventoryProtectionListener.registerHandSwapProtection();

        PluginCommand command = getCommand("enderbutt");
        if (command == null) {
            ConsoleMessages.error(this, "ERROR Command registration failed.");
            failStartup();
            setEnabled(false);
            return;
        }
        EnderButtCommand commandHandler = new EnderButtCommand(this, settings, messages, placeholderApi, ajParkour, items);
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);

        metrics = new Metrics(this, BSTATS_ID);
        if (ConsoleMessages.hasStartupIssue()) {
            failStartup();
        } else {
            ConsoleMessages.info(this, "&aPlugin started successfully!");
        }
        ConsoleMessages.finishStartup();
    }

    @Override
    public void onDisable() {
        if (playerListener != null) {
            playerListener.cleanup();
        }
        ConsoleMessages.info(this, "&cPlugin disabled!");
    }

    public boolean reloadPlugin() {
        reloadConfig();
        settings.validate();
        boolean messagesLoaded = messages.load();
        placeholderApi.initialize();
        ajParkour.initialize();
        playerListener.refreshAllPlayers();
        return messagesLoaded;
    }

    private void initializeOptionalPlugin(String pluginName, boolean configured, IntegrationInitializer initializer) {
        if (!configured || !Bukkit.getPluginManager().isPluginEnabled(pluginName)) {
            return;
        }
        ConsoleMessages.info(this, "&rDetected " + pluginName + ", initializing...");
        if (initializer.initialize()) {
            ConsoleMessages.info(this, "&a" + pluginName + " initialized successfully");
        }
    }

    private interface IntegrationInitializer {
        boolean initialize();
    }

    private void failStartup() {
        ConsoleMessages.error(this, "ERROR Plugin did not start correctly.");
        ConsoleMessages.finishStartup();
    }

    public PluginSettings getSettings() {
        return settings;
    }

    public MessageService getMessages() {
        return messages;
    }

    public EnderButtItemService getItems() {
        return items;
    }
}
