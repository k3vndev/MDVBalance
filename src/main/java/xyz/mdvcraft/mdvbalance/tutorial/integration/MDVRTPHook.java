package xyz.mdvcraft.mdvbalance.tutorial.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import xyz.mdvcraft.mdvbalance.MDVBalance;
import xyz.mdvcraft.mdvbalance.tutorial.TutorialService;

import java.lang.reflect.Method;
import java.util.Locale;

public final class MDVRTPHook {
    private static final String EVENT_CLASS = "xyz.mdvcraft.mdvrtp.api.event.MDVRandomTeleportSuccessEvent";

    private final MDVBalance plugin;
    private final TutorialService tutorialService;
    private final Listener dynamicListener = new Listener() {};
    private boolean bound;

    private final NamespacedKey lastWorldKey = NamespacedKey.fromString("mdvrtp:last_success_world");
    private final NamespacedKey lastSourceKey = NamespacedKey.fromString("mdvrtp:last_success_source");
    private final NamespacedKey lastEpochKey = NamespacedKey.fromString("mdvrtp:last_success_epoch");

    public MDVRTPHook(MDVBalance plugin, TutorialService tutorialService) {
        this.plugin = plugin;
        this.tutorialService = tutorialService;
    }

    @SuppressWarnings("unchecked")
    public boolean bind() {
        if (bound) return true;
        Plugin mdvrtp = Bukkit.getPluginManager().getPlugin("MDVRTP");
        if (mdvrtp == null || !mdvrtp.isEnabled()) return false;
        try {
            Class<?> raw = Class.forName(EVENT_CLASS, true, mdvrtp.getClass().getClassLoader());
            if (!Event.class.isAssignableFrom(raw)) return false;
            Class<? extends Event> eventClass = (Class<? extends Event>) raw;
            EventExecutor executor = (listener, event) -> handleDynamicEvent(event);
            Bukkit.getPluginManager().registerEvent(
                    eventClass,
                    dynamicListener,
                    EventPriority.MONITOR,
                    executor,
                    plugin,
                    true
            );
            bound = true;
            plugin.getLogger().info("Integración MDVRTP enlazada al evento de RTP exitoso.");
            return true;
        } catch (Throwable ex) {
            plugin.getLogger().warning("No se pudo enlazar el evento de MDVRTP: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return false;
        }
    }

    private void handleDynamicEvent(Event event) {
        if (!(event instanceof PlayerEvent playerEvent)) return;
        try {
            Method getTo = event.getClass().getMethod("getTo");
            Method getSource = event.getClass().getMethod("getSource");
            Object rawTo = getTo.invoke(event);
            Object rawSource = getSource.invoke(event);
            if (!(rawTo instanceof Location to) || to.getWorld() == null) return;
            String source = rawSource == null ? "" : rawSource.toString().toUpperCase(Locale.ROOT);
            tutorialService.onRtpSuccess(playerEvent.getPlayer(), to.getWorld().getName(), source);
        } catch (Throwable ex) {
            plugin.debug("No se pudo procesar MDVRandomTeleportSuccessEvent: " + ex.getClass().getSimpleName());
        }
    }

    public boolean matchesLastSuccessfulRtp(Player player, String worldName, long notBeforeEpoch, java.util.Set<String> allowedSources) {
        if (player == null || worldName == null || lastWorldKey == null || lastEpochKey == null) return false;
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        String lastWorld = pdc.get(lastWorldKey, PersistentDataType.STRING);
        Long epoch = pdc.get(lastEpochKey, PersistentDataType.LONG);
        if (lastWorld == null || epoch == null) return false;
        if (!lastWorld.equalsIgnoreCase(worldName) || epoch < notBeforeEpoch) return false;
        if (allowedSources == null || allowedSources.isEmpty() || lastSourceKey == null) return true;
        String source = pdc.get(lastSourceKey, PersistentDataType.STRING);
        return source != null && allowedSources.contains(source.toUpperCase(Locale.ROOT));
    }

    public boolean isBound() {
        return bound;
    }
}
