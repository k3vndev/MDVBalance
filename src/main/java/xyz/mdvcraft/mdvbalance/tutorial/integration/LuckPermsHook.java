package xyz.mdvcraft.mdvbalance.tutorial.integration;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;
import xyz.mdvcraft.mdvbalance.MDVBalance;

import java.util.Locale;

public final class LuckPermsHook {
    private final MDVBalance plugin;
    private LuckPerms luckPerms;

    public LuckPermsHook(MDVBalance plugin) {
        this.plugin = plugin;
        refresh();
    }

    public void refresh() {
        try {
            luckPerms = LuckPermsProvider.get();
            plugin.getLogger().info("Integración LuckPerms disponible para el objetivo de raza.");
        } catch (IllegalStateException ex) {
            luckPerms = null;
            plugin.getLogger().warning("LuckPerms no está disponible; se usará el permiso fallback del objetivo de raza.");
        }
    }

    public boolean hasGroup(Player player, String groupName, String permissionFallback) {
        if (player == null) return false;
        String expected = groupName == null ? "" : groupName.trim().toLowerCase(Locale.ROOT);

        if (luckPerms != null && !expected.isBlank()) {
            try {
                User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
                for (Group group : user.getInheritedGroups(user.getQueryOptions())) {
                    if (group.getName().equalsIgnoreCase(expected)) return true;
                }
            } catch (Throwable ex) {
                plugin.debug("LuckPerms group check falló para " + player.getName() + ": " + ex.getClass().getSimpleName());
            }
        }

        return permissionFallback != null && !permissionFallback.isBlank() && player.hasPermission(permissionFallback);
    }
}
