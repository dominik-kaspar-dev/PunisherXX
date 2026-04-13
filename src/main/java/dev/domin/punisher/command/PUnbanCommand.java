package dev.domin.punisher.command;

import dev.domin.punisher.service.PunishmentService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PUnbanCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final PunishmentService punishmentService;

    public PUnbanCommand(JavaPlugin plugin, PunishmentService punishmentService) {
        this.plugin = plugin;
        this.punishmentService = punishmentService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String unbanPermission = punishmentService.permission("unban", "punisher.unban");
        if (!sender.hasPermission(unbanPermission)) {
            sender.sendMessage(punishmentService.message("no-permission", Map.of()));
            return true;
        }

        if (args.length < 1) {
            return false;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        String playerName = punishmentService.playerName(target);

        boolean removed = punishmentService.unbanPlayer(target.getUniqueId());
        if (!removed) {
            sender.sendMessage(punishmentService.message("unban-not-found", Map.of(
                    "%player%", playerName
            )));
            return true;
        }

        sender.sendMessage(punishmentService.message("unban-applied", Map.of(
                "%player%", playerName,
                "%actor%", punishmentService.actorName(sender)
        )));
        plugin.getLogger().fine("Unbanned player " + playerName + " by " + punishmentService.actorName(sender));
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
