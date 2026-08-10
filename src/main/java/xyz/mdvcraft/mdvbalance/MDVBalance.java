package xyz.mdvcraft.mdvbalance;

import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.mdvcraft.mdvbalance.command.MDVBalanceCommand;
import xyz.mdvcraft.mdvbalance.spawner.SpawnerLimiter;
import xyz.mdvcraft.mdvbalance.tutorial.TutorialListener;
import xyz.mdvcraft.mdvbalance.tutorial.TutorialService;
import xyz.mdvcraft.mdvbalance.tutorial.TutorialStorage;
import xyz.mdvcraft.mdvbalance.util.ColorUtil;

import java.sql.SQLException;

public final class MDVBalance extends JavaPlugin {
    private TutorialStorage tutorialStorage;
    private TutorialService tutorialService;
    private SpawnerLimiter spawnerLimiter;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        tutorialStorage = new TutorialStorage(this);
        try {
            tutorialStorage.open();
        } catch (SQLException ex) {
            getLogger().severe("No se pudo abrir tutorial.db: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        tutorialService = new TutorialService(this, tutorialStorage);
        spawnerLimiter = new SpawnerLimiter(this);

        tutorialService.enable();
        spawnerLimiter.enable();

        getServer().getPluginManager().registerEvents(new TutorialListener(this, tutorialService), this);
        getServer().getPluginManager().registerEvents(spawnerLimiter, this);

        MDVBalanceCommand command = new MDVBalanceCommand(this);
        PluginCommand pluginCommand = getCommand("mdvbalance");
        if (pluginCommand == null) {
            throw new IllegalStateException("No se encontró mdvbalance en plugin.yml");
        }
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);

        getLogger().info("MDVBalance 1.0.0 habilitado. Tutorial=" + tutorialService.enabled()
                + ", Spawners=" + spawnerLimiter.enabled()
                + ", MaxPorSpawner=" + spawnerLimiter.maxAlive());
    }

    @Override
    public void onDisable() {
        if (tutorialService != null) tutorialService.shutdown();
        if (spawnerLimiter != null) spawnerLimiter.shutdown();
        if (tutorialStorage != null) tutorialStorage.close();
        getLogger().info("MDVBalance deshabilitado.");
    }

    public void reloadPlugin() {
        reloadConfig();
        if (tutorialService != null) tutorialService.reload();
        if (spawnerLimiter != null) spawnerLimiter.reload();
    }

    public void message(CommandSender sender, String key, String... replacements) {
        String prefix = getConfig().getString("messages.prefix", "");
        String text = getConfig().getString("messages." + key, "&c" + key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        sender.sendMessage(ColorUtil.color(prefix + text));
    }

    public void debug(String message) {
        if (getConfig().getBoolean("debug", false)) getLogger().info("[DEBUG] " + message);
    }

    public void spawnerDebug(String message) {
        if (getConfig().getBoolean("spawners.debug", false)) getLogger().info("[SPAWNER] " + message);
    }

    public TutorialService tutorials() {
        return tutorialService;
    }

    public SpawnerLimiter spawners() {
        return spawnerLimiter;
    }
}
