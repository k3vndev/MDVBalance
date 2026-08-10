package xyz.mdvcraft.mdvbalance.tutorial;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import xyz.mdvcraft.mdvbalance.MDVBalance;
import xyz.mdvcraft.mdvbalance.tutorial.integration.EssentialsHomesHook;
import xyz.mdvcraft.mdvbalance.tutorial.integration.LuckPermsHook;
import xyz.mdvcraft.mdvbalance.tutorial.integration.MDVRTPHook;
import xyz.mdvcraft.mdvbalance.util.ColorUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TutorialService {
    private final MDVBalance plugin;
    private final TutorialStorage storage;
    private final LuckPermsHook luckPermsHook;
    private final EssentialsHomesHook essentialsHomesHook;

    private final ConcurrentHashMap<UUID, TutorialProgress> active = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> chatHidden = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastReminder = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, BossBar> bossBars = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> chatBypass = new ConcurrentHashMap<>();

    private volatile boolean chatFilterEnabled;
    private volatile String chatBypassPermission;

    private MDVRTPHook mdvrtpHook;
    private BukkitTask stateTask;

    public TutorialService(MDVBalance plugin, TutorialStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.luckPermsHook = new LuckPermsHook(plugin);
        this.essentialsHomesHook = new EssentialsHomesHook(plugin);
    }

    public void enable() {
        refreshChatConfig();
        mdvrtpHook = new MDVRTPHook(plugin, this);
        mdvrtpHook.bind();
        restartTask();
    }

    public void reload() {
        refreshChatConfig();
        luckPermsHook.refresh();
        if (mdvrtpHook != null && !mdvrtpHook.isBound()) mdvrtpHook.bind();
        restartTask();
        for (Player player : Bukkit.getOnlinePlayers()) {
            String bypassPermission = chatBypassPermission;
            chatBypass.put(player.getUniqueId(), bypassPermission != null && !bypassPermission.isBlank() && player.hasPermission(bypassPermission));

            TutorialProgress progress = active.get(player.getUniqueId());
            if (progress != null && !progress.completed()) showObjective(player, progress, false);
        }
    }

    private void restartTask() {
        if (stateTask != null) stateTask.cancel();
        if (!enabled()) return;
        long interval = Math.max(10L, plugin.getConfig().getLong("tutorial.state-check-interval-ticks", 20L));
        stateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    public void shutdown() {
        if (stateTask != null) {
            stateTask.cancel();
            stateTask = null;
        }
        for (BossBar bar : bossBars.values()) bar.removeAll();
        bossBars.clear();
        active.clear();
        chatHidden.clear();
        lastReminder.clear();
        chatBypass.clear();
    }

    private void refreshChatConfig() {
        chatFilterEnabled = plugin.getConfig().getBoolean("tutorial.chat-filter.enabled", true);
        chatBypassPermission = plugin.getConfig().getString("tutorial.chat-filter.bypass-permission", "mdvbalance.tutorial.chat-bypass");
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("modules.tutorial", true)
                && plugin.getConfig().getBoolean("tutorial.enabled", true);
    }

    public void handleJoin(Player player, boolean firstJoin) {
        if (!enabled() || player == null) return;
        String bypassPermission = chatBypassPermission;
        chatBypass.put(player.getUniqueId(), bypassPermission != null && !bypassPermission.isBlank() && player.hasPermission(bypassPermission));
        Optional<TutorialProgress> stored = storage.load(player.getUniqueId());
        if (stored.isPresent()) {
            TutorialProgress progress = stored.get();
            if (!progress.completed() && progress.currentStep() != null) {
                active.put(player.getUniqueId(), progress);
                showObjective(player, progress, false);
            }
            return;
        }

        boolean onlyNew = plugin.getConfig().getBoolean("tutorial.only-new-players", true);
        if (!onlyNew || firstJoin) startFresh(player, true);
    }

    public void handleQuit(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        BossBar bar = bossBars.remove(uuid);
        if (bar != null) bar.removeAll();
        chatHidden.remove(uuid);
        lastReminder.remove(uuid);
        chatBypass.remove(uuid);
        active.remove(uuid);
    }

    public void startFresh(Player player, boolean sendStartMessage) {
        if (player == null || !enabled()) return;
        long now = System.currentTimeMillis();
        TutorialProgress progress = new TutorialProgress(
                player.getUniqueId(), player.getName(), 1, false, now, now, now
        );
        storage.save(progress);
        active.put(player.getUniqueId(), progress);
        showObjective(player, progress, sendStartMessage);
    }

    public void reset(Player player) {
        if (player == null) return;
        storage.delete(player.getUniqueId());
        removeBossBar(player.getUniqueId());
        active.remove(player.getUniqueId());
        chatHidden.remove(player.getUniqueId());
        lastReminder.remove(player.getUniqueId());
        startFresh(player, true);
    }

    public void skip(Player player) {
        if (player == null) return;
        long now = System.currentTimeMillis();
        TutorialProgress current = active.get(player.getUniqueId());
        long started = current == null ? now : current.startedAt();
        long objectiveStarted = current == null ? now : current.objectiveStartedAt();
        TutorialProgress completed = new TutorialProgress(
                player.getUniqueId(), player.getName(), TutorialStep.TOTAL + 1, true,
                started, objectiveStarted, now
        );
        storage.save(completed);
        active.remove(player.getUniqueId());
        chatHidden.remove(player.getUniqueId());
        lastReminder.remove(player.getUniqueId());
        removeBossBar(player.getUniqueId());
    }

    public Optional<TutorialProgress> getProgress(Player player) {
        if (player == null) return Optional.empty();
        TutorialProgress cached = active.get(player.getUniqueId());
        if (cached != null) return Optional.of(cached);
        return storage.load(player.getUniqueId());
    }

    public boolean isChatHidden(UUID uuid) {
        return Boolean.TRUE.equals(chatHidden.get(uuid));
    }

    public boolean isChatFilterEnabled() {
        return chatFilterEnabled;
    }

    public boolean isChatBypass(UUID uuid) {
        return Boolean.TRUE.equals(chatBypass.get(uuid));
    }

    public void signal(Player player, String signal) {
        if (player == null || signal == null) return;
        TutorialProgress progress = active.get(player.getUniqueId());
        if (progress == null || progress.currentStep() != TutorialStep.SUPPLIES) return;
        String expected = plugin.getConfig().getString("tutorial.objectives.supplies.signal", "suministros");
        if (!expected.equalsIgnoreCase(signal.trim())) return;
        advance(player, TutorialStep.SUPPLIES);
    }

    public boolean acceptsSignal(Player player, String signal) {
        if (player == null || signal == null) return false;
        TutorialProgress progress = active.get(player.getUniqueId());
        if (progress == null) return false;

        // Si PlayerKits2 dispara la señal inmediatamente después de elegir raza,
        // no dependemos de esperar al próximo tick de validación del tutorial.
        if (progress.currentStep() == TutorialStep.RACE) {
            String group = plugin.getConfig().getString("tutorial.objectives.race.luckperms-group", "aventurero");
            String fallback = plugin.getConfig().getString("tutorial.objectives.race.permission-fallback", "group.aventurero");
            if (luckPermsHook.hasGroup(player, group, fallback)) {
                advance(player, TutorialStep.RACE);
                progress = active.get(player.getUniqueId());
            }
        }

        if (progress == null || progress.currentStep() != TutorialStep.SUPPLIES) return false;
        String expected = plugin.getConfig().getString("tutorial.objectives.supplies.signal", "suministros");
        return expected.equalsIgnoreCase(signal.trim());
    }

    public void onRtpSuccess(Player player, String worldName, String source) {
        if (player == null || worldName == null) return;
        TutorialProgress progress = active.get(player.getUniqueId());
        if (progress == null || progress.currentStep() != TutorialStep.SURVIVAL) return;
        String requiredWorld = plugin.getConfig().getString("tutorial.objectives.survival.world", "world");
        if (!requiredWorld.equalsIgnoreCase(worldName)) return;
        if (!allowedRtpSources().contains(source == null ? "" : source.toUpperCase(Locale.ROOT))) return;
        advance(player, TutorialStep.SURVIVAL);
    }

    private void tick() {
        if (!enabled()) return;
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            String bypassPermission = chatBypassPermission;
            chatBypass.put(player.getUniqueId(), bypassPermission != null && !bypassPermission.isBlank() && player.hasPermission(bypassPermission));

            TutorialProgress progress = active.get(player.getUniqueId());
            if (progress == null || progress.completed() || progress.currentStep() == null) continue;

            tryAutoComplete(player, progress);

            TutorialProgress after = active.get(player.getUniqueId());
            if (after == null || after.completed() || after.currentStep() == null) continue;
            maybeSendReminder(player, after, now);
        }
    }

    private void tryAutoComplete(Player player, TutorialProgress progress) {
        TutorialStep step = progress.currentStep();
        if (step == null) return;
        switch (step) {
            case RACE -> {
                String group = plugin.getConfig().getString("tutorial.objectives.race.luckperms-group", "aventurero");
                String fallback = plugin.getConfig().getString("tutorial.objectives.race.permission-fallback", "group.aventurero");
                if (luckPermsHook.hasGroup(player, group, fallback)) advance(player, TutorialStep.RACE);
            }
            case SUPPLIES -> {
                // Se completa exclusivamente mediante la señal de claim exitoso de PlayerKits2.
            }
            case SURVIVAL -> {
                String world = plugin.getConfig().getString("tutorial.objectives.survival.world", "world");
                if (mdvrtpHook != null && mdvrtpHook.matchesLastSuccessfulRtp(
                        player, world, progress.objectiveStartedAt(), allowedRtpSources())) {
                    advance(player, TutorialStep.SURVIVAL);
                }
            }
            case HOME -> {
                int minimum = Math.max(1, plugin.getConfig().getInt("tutorial.objectives.home.minimum-homes", 1));
                String path = plugin.getConfig().getString("tutorial.objectives.home.essentials-userdata-path", "plugins/Essentials/userdata");
                if (essentialsHomesHook.getHomeCount(player, path) >= minimum) advance(player, TutorialStep.HOME);
            }
        }
    }

    private Set<String> allowedRtpSources() {
        List<String> configured = plugin.getConfig().getStringList("tutorial.objectives.survival.allowed-sources");
        Set<String> out = new HashSet<>();
        for (String source : configured) {
            if (source != null && !source.isBlank()) out.add(source.trim().toUpperCase(Locale.ROOT));
        }
        if (out.isEmpty()) out.addAll(Set.of("COMMAND", "SIGN", "PORTAL"));
        return out;
    }

    private void advance(Player player, TutorialStep expected) {
        TutorialProgress current = active.get(player.getUniqueId());
        if (current == null || current.completed() || current.currentStep() != expected) return;

        long now = System.currentTimeMillis();
        if (current.step() >= TutorialStep.TOTAL) {
            complete(player, current, now);
            return;
        }

        playConfiguredSound(player, "tutorial.sounds.objective-complete");

        TutorialProgress next = current.advance(player.getName(), now);
        storage.save(next);
        active.put(player.getUniqueId(), next);
        showObjective(player, next, true);
    }

    private void complete(Player player, TutorialProgress current, long now) {
        TutorialProgress completed = current.complete(player.getName(), now);
        storage.save(completed);
        active.remove(player.getUniqueId());
        chatHidden.remove(player.getUniqueId());
        lastReminder.remove(player.getUniqueId());

        List<String> lines = plugin.getConfig().getStringList("tutorial.completion.message");
        sendLines(player, lines, current);
        playConfiguredSound(player, "tutorial.sounds.tutorial-complete");

        BossBar bar = bossBars.computeIfAbsent(player.getUniqueId(), uuid -> createBossBar());
        if (bar != null) {
            bar.setTitle(ColorUtil.color("&6&l✦ TUTORIAL COMPLETADO ✦"));
            bar.setProgress(1.0D);
            if (!bar.getPlayers().contains(player)) bar.addPlayer(player);
            bar.setVisible(true);
            long ticks = Math.max(1L, plugin.getConfig().getLong("tutorial.completion.bossbar-seconds", 3L) * 20L);
            Bukkit.getScheduler().runTaskLater(plugin, () -> removeBossBar(player.getUniqueId()), ticks);
        }
    }

    private void showObjective(Player player, TutorialProgress progress, boolean sendStartMessage) {
        TutorialStep step = progress.currentStep();
        if (step == null) return;

        String base = "tutorial.objectives." + step.configKey();
        boolean hide = plugin.getConfig().getBoolean(base + ".hide-player-chat", false)
                && chatFilterEnabled;
        chatHidden.put(player.getUniqueId(), hide);
        lastReminder.put(player.getUniqueId(), System.currentTimeMillis());

        if (plugin.getConfig().getBoolean("tutorial.bossbar.enabled", true)) {
            BossBar bar = bossBars.computeIfAbsent(player.getUniqueId(), uuid -> createBossBar());
            if (bar != null) {
                String title = plugin.getConfig().getString(base + ".title", step.configKey());
                String format = plugin.getConfig().getString("tutorial.bossbar.format", "&6&l✦ {objective} &7({step}/{total})");
                bar.setTitle(ColorUtil.color(formatText(format, player, progress, title)));
                bar.setProgress(Math.max(0D, Math.min(1D, progress.step() / (double) TutorialStep.TOTAL)));
                if (!bar.getPlayers().contains(player)) bar.addPlayer(player);
                bar.setVisible(true);
            }
        } else {
            removeBossBar(player.getUniqueId());
        }

        if (sendStartMessage) {
            sendLines(player, plugin.getConfig().getStringList(base + ".start-message"), progress);
        }
    }

    private BossBar createBossBar() {
        try {
            BarColor color = BarColor.valueOf(plugin.getConfig().getString("tutorial.bossbar.color", "YELLOW").toUpperCase(Locale.ROOT));
            BarStyle style = BarStyle.valueOf(plugin.getConfig().getString("tutorial.bossbar.style", "SOLID").toUpperCase(Locale.ROOT));
            return Bukkit.createBossBar("", color, style);
        } catch (IllegalArgumentException ex) {
            return Bukkit.createBossBar("", BarColor.YELLOW, BarStyle.SOLID);
        }
    }

    private void maybeSendReminder(Player player, TutorialProgress progress, long now) {
        TutorialStep step = progress.currentStep();
        if (step == null) return;
        String base = "tutorial.objectives." + step.configKey() + ".reminder";
        if (!plugin.getConfig().getBoolean(base + ".enabled", true)) return;
        long intervalMs = Math.max(5L, plugin.getConfig().getLong(base + ".interval-seconds", 30L)) * 1000L;
        long last = lastReminder.getOrDefault(player.getUniqueId(), progress.objectiveStartedAt());
        if (now - last < intervalMs) return;
        lastReminder.put(player.getUniqueId(), now);
        List<String> lines = plugin.getConfig().getStringList(base + ".message");
        sendLines(player, lines, progress);
    }

    private void playConfiguredSound(Player player, String base) {
        if (player == null || base == null || base.isBlank()) return;
        if (!plugin.getConfig().getBoolean(base + ".enabled", true)) return;

        String configured = plugin.getConfig().getString(base + ".sound", "minecraft:block.note_block.pling");
        if (configured == null || configured.isBlank()) return;

        float volume = (float) Math.max(0.0D, plugin.getConfig().getDouble(base + ".volume", 1.0D));
        float pitch = (float) Math.max(0.0D, plugin.getConfig().getDouble(base + ".pitch", 1.0D));

        String sound = configured.trim().toLowerCase(Locale.ROOT);
        if (sound.indexOf(':') < 0) sound = "minecraft:" + sound;
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    private void sendLines(Player player, List<String> lines, TutorialProgress progress) {
        if (lines == null || lines.isEmpty()) return;
        TutorialStep step = progress == null ? null : progress.currentStep();
        String objective = step == null ? "Tutorial" : plugin.getConfig().getString(
                "tutorial.objectives." + step.configKey() + ".title", step.configKey());
        for (String line : new ArrayList<>(lines)) {
            player.sendMessage(ColorUtil.color(formatText(line, player, progress, objective)));
        }
    }

    private String formatText(String text, Player player, TutorialProgress progress, String objective) {
        if (text == null) return "";
        int step = progress == null ? TutorialStep.TOTAL : Math.min(progress.step(), TutorialStep.TOTAL);
        return text
                .replace("{player}", player == null ? "" : player.getName())
                .replace("{objective}", objective == null ? "" : objective)
                .replace("{step}", String.valueOf(step))
                .replace("{total}", String.valueOf(TutorialStep.TOTAL));
    }

    private void removeBossBar(UUID uuid) {
        BossBar bar = bossBars.remove(uuid);
        if (bar != null) bar.removeAll();
    }
}
