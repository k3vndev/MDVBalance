package xyz.mdvcraft.mdvbalance.listener;

import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import xyz.mdvcraft.mdvbalance.MDVBalance;
import xyz.mdvcraft.mdvbalance.util.ColorUtil;

public class DeathListener implements Listener {
  private final MDVBalance plugin;

  public DeathListener(MDVBalance plugin) {
    this.plugin = plugin;
  }

  @EventHandler
  public void onPlayerDeath(PlayerDeathEvent event) {
  }

  @EventHandler
  public void onPlayerRespawn(PlayerRespawnEvent event) {
    if (!plugin.getConfig().getBoolean("death-listener.enabled", true))
      return;

    Player player = event.getPlayer();
    int maxDeaths = Math.max(0, plugin.getConfig().getInt("death-listener.max-deaths", 1));
    // getStatistic ya refleja la muerte que acaba de ocurrir en este respawn.
    int deathCount = player.getStatistic(Statistic.DEATHS);
    if (deathCount > maxDeaths)
      return;

    String message = plugin.getConfig().getString("death-listener.message", "");
    if (message == null || message.isBlank())
      return;
    player.sendMessage(ColorUtil.color(message.stripTrailing()));
  }
}
