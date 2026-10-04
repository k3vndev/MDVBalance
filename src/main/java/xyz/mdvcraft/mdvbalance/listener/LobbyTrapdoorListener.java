package xyz.mdvcraft.mdvbalance.listener;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import xyz.mdvcraft.mdvbalance.MDVBalance;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class LobbyTrapdoorListener implements Listener {
  private final MDVBalance plugin;
  private Set<String> regionIds = Set.of();
  private Set<String> exactBlockNames = Set.of();
  private Set<String> blockNameSuffixes = Set.of();

  public LobbyTrapdoorListener(MDVBalance plugin) {
    this.plugin = plugin;
    reload();
  }

  public void reload() {
    regionIds = configuredValues("lobby-trapdoors.regions", List.of("lobby"));
    exactBlockNames = configuredValues("lobby-trapdoors.blocks.exact", List.of());
    blockNameSuffixes = configuredValues("lobby-trapdoors.blocks.suffixes", List.of("_TRAPDOOR"));
  }

  @EventHandler
  public void onPlayerInteract(PlayerInteractEvent event) {
    Action action = event.getAction();
    if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK)
      return;

    Block block = event.getClickedBlock();
    if (block == null || !matchesBlock(block.getType().name()))
      return;

    boolean inLobby = WorldGuard.getInstance()
        .getPlatform()
        .getRegionContainer()
        .createQuery()
        .getApplicableRegions(BukkitAdapter.adapt(block.getLocation()))
        .getRegions()
        .stream()
        .map(ProtectedRegion::getId)
        .map(id -> id.toLowerCase(Locale.ROOT))
        .anyMatch(regionIds::contains);

    if (inLobby)
      event.setCancelled(true);
  }

  private boolean matchesBlock(String blockName) {
    String normalizedName = blockName.toLowerCase(Locale.ROOT);
    return exactBlockNames.contains(normalizedName)
        || blockNameSuffixes.stream().anyMatch(normalizedName::endsWith);
  }

  private Set<String> configuredValues(String path, List<String> defaults) {
    List<String> configured = plugin.getConfig().isSet(path)
        ? plugin.getConfig().getStringList(path)
        : defaults;
    return configured.stream()
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .map(value -> value.toLowerCase(Locale.ROOT))
        .collect(Collectors.toUnmodifiableSet());
  }
}
