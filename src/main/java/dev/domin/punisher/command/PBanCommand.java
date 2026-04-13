package dev.domin.punisher.command;

import dev.domin.punisher.service.PunishmentService;
import dev.domin.punisher.util.DurationParser;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PBanCommand implements CommandExecutor, TabCompleter {
    private static final List<String> COMMON_DURATIONS = List.of("1h", "12h", "1d", "7d", "30d", "perm");

    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final PunishmentService punishmentService;

    public PBanCommand(org.bukkit.plugin.java.JavaPlugin plugin, PunishmentService punishmentService) {
        this.plugin = plugin;
        this.punishmentService = punishmentService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String banPermission = punishmentService.permission("ban", "punisher.ban");
        if (!sender.hasPermission(banPermission)) {
            sender.sendMessage(punishmentService.message("no-permission", Map.of()));
            return true;
        }

        if (args.length < 2) {
            return false;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        String playerName = punishmentService.playerName(target);

        Duration duration = DurationParser.parseDuration(args[1]);
        if (duration == null) {
            sender.sendMessage(punishmentService.message("invalid-duration", Map.of()));
            return true;
        }

        String reason = plugin.getConfig().getString("defaults.ban-reason", "Punished by staff.");
        if (args.length >= 3) {
            reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        }

        punishmentService.banPlayer(target.getUniqueId(), playerName, duration, reason, punishmentService.actorName(sender));

        sender.sendMessage(punishmentService.message("ban-applied", Map.of(
                "%player%", playerName,
                "%reason%", reason,
                "%duration%", DurationParser.formatDuration(duration),
                "%actor%", punishmentService.actorName(sender)
        )));

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterByPrefix(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[0]);
        }
        if (args.length == 2) {
            return filterByPrefix(COMMON_DURATIONS, args[1]);
        }
        return Collections.emptyList();
    }

    private List<String> filterByPrefix(List<String> values, String input) {
        String lowerInput = input.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lowerInput))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}
