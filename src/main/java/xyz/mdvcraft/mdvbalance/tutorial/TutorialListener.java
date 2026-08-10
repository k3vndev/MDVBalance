package xyz.mdvcraft.mdvbalance.tutorial;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import xyz.mdvcraft.mdvbalance.MDVBalance;

public final class TutorialListener implements Listener {
    private final MDVBalance plugin;
    private final TutorialService tutorials;

    public TutorialListener(MDVBalance plugin, TutorialService tutorials) {
        this.plugin = plugin;
        this.tutorials = tutorials;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        boolean firstJoin = !event.getPlayer().hasPlayedBefore();
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> tutorials.handleJoin(event.getPlayer(), firstJoin), 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        tutorials.handleQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!tutorials.isChatFilterEnabled()) return;
        if (tutorials.isChatBypass(event.getPlayer().getUniqueId())) return;

        event.viewers().removeIf((Audience audience) ->
                audience instanceof Player viewer && tutorials.isChatHidden(viewer.getUniqueId()));
    }
}
