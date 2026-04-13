package dev.domin.punisher.command;

import dev.domin.punisher.service.PunishmentService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PunishCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final PunishmentService punishmentService;

    public PunishCommand(JavaPlugin plugin, PunishmentService punishmentService) {
        this.plugin = plugin;
        this.punishmentService = punishmentService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            return false;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            String reloadPermission = punishmentService.permission("reload", "punisher.reload");
            if (!sender.hasPermission(reloadPermission)) {
                sender.sendMessage(punishmentService.message("no-permission", Map.of()));
                return true;
            }

            plugin.reloadConfig();
            punishmentService.reload();
            sender.sendMessage(punishmentService.message("reloaded", Map.of()));
            return true;
        }

        if (args.length < 2) {
            return false;
        }

        String type = args[0].toLowerCase(Locale.ROOT);
        if (!hasPunishPermission(sender, type)) {
            sender.sendMessage(punishmentService.message("no-permission", Map.of()));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        String playerName = punishmentService.playerName(target);

        String reason = plugin.getConfig().getString("defaults.punish-reason", "No reason provided.");
        if (args.length >= 3) {
            reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        }

        boolean success = punishmentService.executePunishType(sender, type, target, reason);
        if (!success) {
            sender.sendMessage(punishmentService.message("punish-not-found", Map.of(
                    "%type%", type
            )));
            return true;
        }

        sender.sendMessage(punishmentService.message("punish-executed", Map.of(
                "%type%", type,
                "%player%", playerName,
                "%reason%", reason,
                "%actor%", punishmentService.actorName(sender)
        )));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            ConfigurationSection section = plugin.getConfig().getConfigurationSection("punish");
            if (section != null) {
                options.addAll(section.getKeys(false));
            }
            options.add("reload");
            return filterByPrefix(options, args[0]);
        }

        if (args.length == 2) {
            List<String> players = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            return filterByPrefix(players, args[1]);
        }

        return Collections.emptyList();
    }

    private boolean hasPunishPermission(CommandSender sender, String type) {
        String basePermission = punishmentService.permission("punish", "punisher.punish");
        if (!sender.hasPermission(basePermission)) {
            return false;
        }

        String typePermission = plugin.getConfig().getString("punish." + type + ".permission");
        if (typePermission == null || typePermission.isBlank()) {
            String template = punishmentService.permission("punish-type-template", "punisher.punish.%type%");
            typePermission = template.replace("%type%", type);
        }

        return sender.hasPermission(typePermission);
    }

    private List<String> filterByPrefix(List<String> values, String input) {
        String lowerInput = input.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lowerInput))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}
