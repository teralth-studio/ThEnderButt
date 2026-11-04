package testudio.ThEnderButt.util;

import org.bukkit.ChatColor;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConsoleMessages {
    private static final String PREFIX = "&8[&9Th&bEnderButt&8] ";
    private static boolean starting;
    private static boolean startupIssue;

    private ConsoleMessages() {
    }

    public static void beginStartup() {
        starting = true;
        startupIssue = false;
    }

    public static boolean hasStartupIssue() {
        return startupIssue;
    }

    public static void finishStartup() {
        starting = false;
    }

    public static void printBanner(JavaPlugin plugin) {
        ConsoleCommandSender console = plugin.getServer().getConsoleSender();
        console.sendMessage(color("&8----------------------------------------------------------"));
        console.sendMessage(color("&9Th&bEnderButt &rby TeStudio"));
        console.sendMessage(color("&8----------------------------------------------------------"));
    }

    public static void info(JavaPlugin plugin, String message) {
        send(plugin, message);
    }

    public static void warning(JavaPlugin plugin, String message) {
        if (starting) {
            startupIssue = true;
        }
        send(plugin, "&e" + withSeverity("WARNING", message));
    }

    public static void error(JavaPlugin plugin, String message) {
        if (starting) {
            startupIssue = true;
        }
        send(plugin, "&c" + withSeverity("ERROR", message));
    }

    private static String withSeverity(String severity, String message) {
        String normalized = message == null ? "" : message.trim();
        return normalized.equals(severity) || normalized.startsWith(severity + " ")
                ? normalized : severity + " " + normalized;
    }

    private static void send(JavaPlugin plugin, String message) {
        plugin.getServer().getConsoleSender().sendMessage(color(PREFIX + message));
    }

    private static String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
