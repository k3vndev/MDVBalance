package xyz.mdvcraft.mdvbalance.serverlist;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;
import xyz.mdvcraft.mdvbalance.MDVBalance;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Controla la respuesta del ping de la lista de servidores directamente con la API de Paper.
 * No requiere ServerListPlus ni ProtocolLib.
 */
public final class ServerListService implements Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final MDVBalance plugin;

    private boolean registered;
    private boolean enabled;
    private boolean detectWhitelist;
    private volatile boolean whitelistEnabled;
    private BukkitTask whitelistWatcher;

    public ServerListService(MDVBalance plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        if (!registered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            registered = true;
        }
        reload();
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("modules.server-list", true)
                && plugin.getConfig().getBoolean("server-list.enabled", true);
        detectWhitelist = plugin.getConfig().getBoolean("server-list.maintenance.detect-whitelist", true);

        stopWatcher();
        refreshWhitelistState();

        if (enabled && detectWhitelist) {
            long interval = Math.max(1L,
                    plugin.getConfig().getLong("server-list.maintenance.whitelist-check-interval-ticks", 20L));
            whitelistWatcher = plugin.getServer().getScheduler().runTaskTimer(
                    plugin,
                    this::refreshWhitelistState,
                    interval,
                    interval
            );
        }
    }

    public void shutdown() {
        stopWatcher();
    }

    private void stopWatcher() {
        if (whitelistWatcher != null) {
            whitelistWatcher.cancel();
            whitelistWatcher = null;
        }
    }

    private void refreshWhitelistState() {
        whitelistEnabled = plugin.getServer().hasWhitelist();
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean maintenanceActive() {
        return enabled && detectWhitelist && whitelistEnabled;
    }

    public boolean whitelistDetected() {
        return whitelistEnabled;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerListPing(PaperServerListPingEvent event) {
        if (!enabled) return;

        boolean maintenance = detectWhitelist && whitelistEnabled;
        String section = maintenance ? "server-list.maintenance" : "server-list.normal";

        applyMotd(event, section);
        applyHover(event, section, maintenance);
        applyVersionText(event, section, maintenance);
    }

    private void applyMotd(PaperServerListPingEvent event, String section) {
        boolean useServerProperties = plugin.getConfig().getBoolean(section + ".motd.use-server-properties", false);
        if (useServerProperties) return;

        List<String> lines = plugin.getConfig().getStringList(section + ".motd.lines");
        if (lines.isEmpty()) return;

        List<String> rendered = new ArrayList<>(lines.size());
        for (String line : lines) rendered.add(placeholders(line, event));
        Component motd = LEGACY.deserialize(String.join("\n", rendered));
        event.motd(motd);
    }

    private void applyHover(PaperServerListPingEvent event, String section, boolean maintenance) {
        if (!plugin.getConfig().getBoolean(section + ".hover.enabled", true)) return;

        List<String> lines = plugin.getConfig().getStringList(section + ".hover.lines");
        if (lines.isEmpty()) return;

        if (plugin.getConfig().getBoolean(section + ".hover.replace-player-list", true)) {
            event.getListedPlayers().clear();
        }

        String mode = maintenance ? "maintenance" : "normal";
        for (int i = 0; i < lines.size(); i++) {
            String rendered = placeholders(lines.get(i), event);
            String colored = ChatColor.translateAlternateColorCodes('&', rendered);
            UUID id = stableHoverUuid(mode, i);
            event.getListedPlayers().add(new PaperServerListPingEvent.ListedPlayerInfo(colored, id));
        }
    }

    private void applyVersionText(PaperServerListPingEvent event, String section, boolean maintenance) {
        if (!plugin.getConfig().getBoolean(section + ".version.enabled", false)) return;

        String fallback = maintenance ? "MANTENIMIENTO" : plugin.getServer().getMinecraftVersion();
        String text = plugin.getConfig().getString(section + ".version.text", fallback);
        if (text == null || text.isBlank()) return;

        text = placeholders(text, event);
        event.setVersion(ChatColor.translateAlternateColorCodes('&', text));
    }

    private String placeholders(String input, PaperServerListPingEvent event) {
        if (input == null) return "";

        int online = Math.max(0, event.getNumPlayers());
        int max = Math.max(0, event.getMaxPlayers());
        return input
                .replace("{online}", Integer.toString(online))
                .replace("{players}", Integer.toString(online))
                .replace("{max}", Integer.toString(max))
                .replace("{minecraft_version}", plugin.getServer().getMinecraftVersion())
                .replace("{whitelist}", Boolean.toString(whitelistEnabled).toLowerCase(Locale.ROOT));
    }

    private UUID stableHoverUuid(String mode, int index) {
        String seed = "MDVBalance:server-list:" + mode + ":" + index;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }
}
