package xyz.mdvcraft.mdvbalance.tutorial.integration;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import xyz.mdvcraft.mdvbalance.MDVBalance;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.UUID;

public final class EssentialsHomesHook {
    private final MDVBalance plugin;
    private boolean apiLogged;
    private boolean fallbackLogged;

    public EssentialsHomesHook(MDVBalance plugin) {
        this.plugin = plugin;
    }

    public int getHomeCount(Player player, String userdataPath) {
        Integer apiCount = readFromApi(player);
        if (apiCount != null) return apiCount;
        return readFromYaml(player.getUniqueId(), userdataPath);
    }

    private Integer readFromApi(Player player) {
        Plugin essentials = Bukkit.getPluginManager().getPlugin("Essentials");
        if (essentials == null || !essentials.isEnabled()) return null;
        try {
            Method getUser = essentials.getClass().getMethod("getUser", UUID.class);
            Object user = getUser.invoke(essentials, player.getUniqueId());
            if (user == null) return 0;
            Method getHomes = user.getClass().getMethod("getHomes");
            Object rawHomes = getHomes.invoke(user);
            if (rawHomes instanceof Collection<?> homes) {
                if (!apiLogged) {
                    plugin.getLogger().info("Integración EssentialsX disponible para comprobar hogares.");
                    apiLogged = true;
                }
                return homes.size();
            }
        } catch (Throwable ex) {
            if (!fallbackLogged) {
                plugin.getLogger().warning("No se pudo consultar EssentialsX por API; se usará userdata como respaldo.");
                fallbackLogged = true;
            }
        }
        return null;
    }

    private int readFromYaml(UUID uuid, String userdataPath) {
        String path = (userdataPath == null || userdataPath.isBlank())
                ? "plugins/Essentials/userdata"
                : userdataPath;
        File file = new File(path, uuid + ".yml");
        if (!file.exists()) return 0;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection homes = yaml.getConfigurationSection("homes");
        return homes == null ? 0 : homes.getKeys(false).size();
    }
}
