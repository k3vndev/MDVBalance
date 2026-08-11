package xyz.mdvcraft.mdvbalance.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.mdvcraft.mdvbalance.MDVBalance;
import xyz.mdvcraft.mdvbalance.tutorial.TutorialProgress;
import xyz.mdvcraft.mdvbalance.tutorial.TutorialStep;
import xyz.mdvcraft.mdvbalance.util.ColorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class MDVBalanceCommand implements CommandExecutor, TabCompleter {
    private final MDVBalance plugin;

    public MDVBalanceCommand(MDVBalance plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length >= 2 && args[0].equalsIgnoreCase("tutorial") && args[1].equalsIgnoreCase("signal")) {
            return handleSignal(sender, args);
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        if (!sender.hasPermission("mdvbalance.admin")) {
            plugin.message(sender, "no-permission");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadPlugin();
            plugin.message(sender, "reloaded");
            return true;
        }

        if (args[0].equalsIgnoreCase("tutorial")) return handleTutorialAdmin(sender, args);

        if (args[0].equalsIgnoreCase("spawners")) {
            sender.sendMessage(ColorUtil.color("&6MDVBalance &7• Spawners trackeados: &e"
                    + plugin.spawners().trackedSpawnerCount() + " &7• Entidades: &e"
                    + plugin.spawners().trackedEntityCount() + " &7• Máximo por spawner: &e"
                    + plugin.spawners().maxAlive()));
            return true;
        }

        if (args[0].equalsIgnoreCase("serverlist")) {
            String mode = plugin.serverList().maintenanceActive() ? "&cMANTENIMIENTO" : "&aNORMAL";
            sender.sendMessage(ColorUtil.color("&6MDVBalance &7• ServerList: &e"
                    + (plugin.serverList().enabled() ? "activo" : "desactivado")
                    + " &7• Modo: " + mode
                    + " &7• Whitelist detectada: &e" + plugin.serverList().whitelistDetected()));
            return true;
        }

        sendHelp(sender);
        return true;
    }

    private boolean handleSignal(CommandSender sender, String[] args) {
        if (!(sender instanceof ConsoleCommandSender)) {
            plugin.message(sender, "console-only-signal");
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage("Uso: mdvbalance tutorial signal <jugador> <signal>");
            return true;
        }
        Player player = Bukkit.getPlayerExact(args[2]);
        if (player == null) {
            plugin.message(sender, "player-not-found");
            return true;
        }
        String signal = args[3];
        if (!plugin.tutorials().acceptsSignal(player, signal)) {
            plugin.message(sender, "signal-ignored");
            return true;
        }
        plugin.tutorials().signal(player, signal);
        plugin.message(sender, "signal-accepted", "{signal}", signal, "{player}", player.getName());
        return true;
    }

    private boolean handleTutorialAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mdvbalance.tutorial.admin")) {
            plugin.message(sender, "no-permission");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(ColorUtil.color("&e/mdvbalance tutorial start|reset|skip|status <jugador>"));
            return true;
        }
        Player player = Bukkit.getPlayerExact(args[2]);
        if (player == null) {
            plugin.message(sender, "player-not-found");
            return true;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "start" -> {
                plugin.tutorials().startFresh(player, true);
                plugin.message(sender, "tutorial-started", "{player}", player.getName());
            }
            case "reset" -> {
                plugin.tutorials().reset(player);
                plugin.message(sender, "tutorial-reset", "{player}", player.getName());
            }
            case "skip" -> {
                plugin.tutorials().skip(player);
                plugin.message(sender, "tutorial-skipped", "{player}", player.getName());
            }
            case "status" -> sendStatus(sender, player);
            default -> sender.sendMessage(ColorUtil.color("&e/mdvbalance tutorial start|reset|skip|status <jugador>"));
        }
        return true;
    }

    private void sendStatus(CommandSender sender, Player player) {
        Optional<TutorialProgress> optional = plugin.tutorials().getProgress(player);
        if (optional.isEmpty()) {
            sender.sendMessage(ColorUtil.color("&6MDVBalance &7• &f" + player.getName() + " &7no tiene registro de tutorial."));
            return;
        }
        TutorialProgress progress = optional.get();
        if (progress.completed()) {
            sender.sendMessage(ColorUtil.color("&6MDVBalance &7• &f" + player.getName() + " &aCOMPLETADO"));
            return;
        }
        TutorialStep step = progress.currentStep();
        sender.sendMessage(ColorUtil.color("&6MDVBalance &7• &f" + player.getName()
                + " &7está en &e" + (step == null ? "?" : step.configKey())
                + " &7(&e" + progress.step() + "/" + TutorialStep.TOTAL + "&7)"));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ColorUtil.color("&6&lMDVBalance &7- comandos"));
        sender.sendMessage(ColorUtil.color("&e/mdvbalance reload"));
        sender.sendMessage(ColorUtil.color("&e/mdvbalance tutorial start <jugador>"));
        sender.sendMessage(ColorUtil.color("&e/mdvbalance tutorial reset <jugador>"));
        sender.sendMessage(ColorUtil.color("&e/mdvbalance tutorial skip <jugador>"));
        sender.sendMessage(ColorUtil.color("&e/mdvbalance tutorial status <jugador>"));
        sender.sendMessage(ColorUtil.color("&e/mdvbalance spawners &7- estadísticas del tracker"));
        sender.sendMessage(ColorUtil.color("&e/mdvbalance serverlist &7- estado del MOTD/mantenimiento"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return match(args[0], List.of("help", "reload", "tutorial", "spawners", "serverlist"));
        if (args.length == 2 && args[0].equalsIgnoreCase("tutorial")) {
            return match(args[1], List.of("start", "reset", "skip", "status"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("tutorial")) {
            List<String> players = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            return match(args[2], players);
        }
        return List.of();
    }

    private List<String> match(String input, List<String> values) {
        String lower = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String value : values) if (value.toLowerCase(Locale.ROOT).startsWith(lower)) out.add(value);
        return out;
    }
}
