package dev.domin.punisher.command;

import dev.domin.punisher.service.PunishmentService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PKickCommand implements CommandExecutor, TabCompleter {
    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final PunishmentService punishmentService;

    public PKickCommand(org.bukkit.plugin.java.JavaPlugin plugin, PunishmentService punishmentService) {
        this.plugin = plugin;
        this.punishmentService = punishmentService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String kickPermission = punishmentService.permission("kick", "punisher.kick");
        if (!sender.hasPermission(kickPermission)) {
            sender.sendMessage(punishmentService.message("no-permission", Map.of()));
            return true;
        }

        if (args.length < 1) {
            return false;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(punishmentService.message("player-required", Map.of()));
            return true;
        }

        String reason = plugin.getConfig().getString("defaults.kick-reason", "Removed by staff.");
        if (args.length >= 2) {
            reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        }

        punishmentService.kickPlayer(target, reason, punishmentService.actorName(sender));

        sender.sendMessage(punishmentService.message("kick-applied", Map.of(
                "%player%", target.getName(),
                "%reason%", reason,
                "%actor%", punishmentService.actorName(sender)
        )));

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterByPrefix(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[0]);
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
