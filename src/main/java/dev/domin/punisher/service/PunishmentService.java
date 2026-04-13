package dev.domin.punisher.service;

import dev.domin.punisher.model.BanEntry;
import dev.domin.punisher.model.MuteEntry;
import dev.domin.punisher.model.WarningEntry;
import dev.domin.punisher.storage.PunishmentStore;
import dev.domin.punisher.util.DurationParser;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class PunishmentService {
    private final JavaPlugin plugin;
    private final PunishmentStore store;

    public PunishmentService(JavaPlugin plugin, PunishmentStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public void reload() {
        store.cleanupExpired();
    }

    public String permission(String key, String fallback) {
        return plugin.getConfig().getString("permissions." + key, fallback);
    }

    public String actorName(CommandSender sender) {
        return sender == null ? "Console" : sender.getName();
    }

    public String playerName(OfflinePlayer target) {
        return target.getName() != null ? target.getName() : target.getUniqueId().toString();
    }

    public String message(String key, Map<String, String> placeholders) {
        String raw = plugin.getConfig().getString("messages." + key, key);
        return color(applyPlaceholders(raw, placeholders));
    }

    public Optional<BanEntry> getActiveBan(UUID uuid) {
        return store.getActiveBan(uuid);
    }

    public Optional<MuteEntry> getActiveMute(UUID uuid) {
        return store.getActiveMute(uuid);
    }

    public boolean executePunishType(CommandSender sender, String type, OfflinePlayer target, String reason) {
        List<String> actions = resolvePunishActions(type);
        if (actions.isEmpty()) {
            return false;
        }

        String targetName = playerName(target);
        String actor = actorName(sender);

        for (String action : actions) {
            runAction(action, target, targetName, reason, actor);
        }

        return true;
    }

    public void banPlayer(UUID uuid, String playerName, Duration duration, String reason, String actor) {
        long now = System.currentTimeMillis();
        long expiresAt = (duration == null || duration.isZero()) ? 0L : now + duration.toMillis();

        store.setBan(uuid, new BanEntry(playerName, reason, actor, now, expiresAt));

        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            String kickMessage = message("ban-message", basePlaceholders(playerName, reason, actor, uuid, duration));
            online.kickPlayer(kickMessage);
        }
    }

    public void kickPlayer(Player player, String reason, String actor) {
        String playerName = player.getName();
        String kickMessage = message("kick-message", basePlaceholders(playerName, reason, actor, player.getUniqueId(), null));
        player.kickPlayer(kickMessage);
    }

    public void mutePlayer(UUID uuid, String playerName, Duration duration, String reason, String actor) {
        long now = System.currentTimeMillis();
        long expiresAt = (duration == null || duration.isZero()) ? 0L : now + duration.toMillis();

        store.setMute(uuid, new MuteEntry(playerName, reason, actor, now, expiresAt));

        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            online.sendMessage(message("mute-notify", basePlaceholders(playerName, reason, actor, uuid, duration)));
        }
    }

    public int warnPlayer(UUID uuid, String playerName, Duration duration, String reason, String actor) {
        long now = System.currentTimeMillis();
        long expiresAt = (duration == null || duration.isZero()) ? 0L : now + duration.toMillis();

        store.addWarning(uuid, new WarningEntry(reason, actor, now, expiresAt));

        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            online.sendMessage(message("warn-notify", basePlaceholders(playerName, reason, actor, uuid, duration)));
        }

        int activeWarnings = store.getActiveWarningCount(uuid);
        applyWarningEscalation(uuid, playerName, activeWarnings, actor);
        return activeWarnings;
    }

    public void handleAsyncLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        Optional<BanEntry> activeBan = store.getActiveBan(uuid);
        if (activeBan.isEmpty()) {
            return;
        }

        BanEntry ban = activeBan.get();
        Duration remaining = ban.isPermanent()
                ? Duration.ZERO
                : Duration.ofMillis(Math.max(0L, ban.expiresAt() - System.currentTimeMillis()));

        String banScreen = message("ban-screen", basePlaceholders(
                event.getName(),
                ban.reason(),
                ban.actor(),
                uuid,
                remaining
        ));

        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, banScreen);
    }

    public void handleAsyncChat(AsyncChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Optional<MuteEntry> activeMute = store.getActiveMute(uuid);
        if (activeMute.isEmpty()) {
            return;
        }

        MuteEntry mute = activeMute.get();
        Duration remaining = mute.isPermanent()
                ? Duration.ZERO
                : Duration.ofMillis(Math.max(0L, mute.expiresAt() - System.currentTimeMillis()));

        event.setCancelled(true);
        event.getPlayer().sendMessage(message("mute-blocked", basePlaceholders(
                event.getPlayer().getName(),
                mute.reason(),
                mute.actor(),
                uuid,
                remaining
        )));
    }

    private void applyWarningEscalation(UUID uuid, String playerName, int warningCount, String actor) {
        Escalation escalation = findEscalation(warningCount);
        if (escalation == null) {
            return;
        }

        if (store.getActiveBan(uuid).isPresent()) {
            return;
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%player%", playerName);
        placeholders.put("%count%", String.valueOf(warningCount));
        String reason = applyPlaceholders(escalation.reason(), placeholders);

        banPlayer(uuid, playerName, escalation.duration(), reason, actor);

        if (plugin.getConfig().getBoolean("warnings.reset-on-ban", false)) {
            store.clearWarnings(uuid);
        }
    }

    private Escalation findEscalation(int warningCount) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("warnings.escalation");
        if (section == null) {
            return null;
        }

        Escalation best = null;

        for (String key : section.getKeys(false)) {
            int threshold;
            try {
                threshold = Integer.parseInt(key);
            } catch (NumberFormatException ignored) {
                continue;
            }

            String root = "warnings.escalation." + key;
            String reason = "Reached " + threshold + " active warnings.";
            Duration duration;

            if (section.isConfigurationSection(key)) {
                duration = DurationParser.parseDuration(plugin.getConfig().getString(root + ".duration", "7d"));
                reason = plugin.getConfig().getString(root + ".reason", reason);
            } else {
                duration = DurationParser.parseDuration(plugin.getConfig().getString(root));
            }

            if (duration == null || warningCount < threshold) {
                continue;
            }

            if (best == null || threshold > best.threshold()) {
                best = new Escalation(threshold, duration, reason);
            }
        }

        return best;
    }

    private void runAction(String action, OfflinePlayer target, String playerName, String reason, String actor) {
        if (action == null || action.isBlank()) {
            return;
        }

        Map<String, String> placeholders = basePlaceholders(playerName, reason, actor, target.getUniqueId(), null);
        String resolvedAction = applyPlaceholders(action, placeholders).trim();
        String lowerAction = resolvedAction.toLowerCase(Locale.ROOT);

        if (lowerAction.startsWith("pban") || lowerAction.startsWith("logic-ban")) {
            handleBanAction(resolvedAction, target, playerName, reason, actor);
            return;
        }
        if (lowerAction.startsWith("pkick") || lowerAction.startsWith("logic-kick")) {
            handleKickAction(resolvedAction, target, reason, actor);
            return;
        }
        if (lowerAction.startsWith("pmute") || lowerAction.startsWith("logic-mute")) {
            handleMuteAction(resolvedAction, target, playerName, reason, actor);
            return;
        }
        if (lowerAction.startsWith("pwarn") || lowerAction.startsWith("logic-warn")) {
            handleWarnAction(resolvedAction, target, playerName, reason, actor);
            return;
        }

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolvedAction);
    }

    private void handleBanAction(String action, OfflinePlayer target, String playerName, String fallbackReason, String actor) {
        String payload = action.toLowerCase(Locale.ROOT).startsWith("pban")
                ? action.substring("pban".length()).trim()
                : action.substring("logic-ban".length()).trim();
        if (payload.isBlank()) {
            plugin.getLogger().warning("Invalid ban action (missing duration): " + action);
            return;
        }

        String[] split = payload.split("\\s+", 2);
        Duration duration = DurationParser.parseDuration(split[0]);
        if (duration == null) {
            plugin.getLogger().warning("Invalid ban duration in action: " + action);
            return;
        }

        String reason = split.length > 1 && !split[1].isBlank() ? split[1] : fallbackReason;
        banPlayer(target.getUniqueId(), playerName, duration, reason, actor);
    }

    private void handleKickAction(String action, OfflinePlayer target, String fallbackReason, String actor) {
        String payload = action.toLowerCase(Locale.ROOT).startsWith("pkick")
                ? action.substring("pkick".length()).trim()
                : action.substring("logic-kick".length()).trim();
        String reason = payload.isBlank() ? fallbackReason : payload;

        Player online = Bukkit.getPlayer(target.getUniqueId());
        if (online != null) {
            kickPlayer(online, reason, actor);
        }
    }

    private void handleMuteAction(String action, OfflinePlayer target, String playerName, String fallbackReason, String actor) {
        String payload = action.toLowerCase(Locale.ROOT).startsWith("pmute")
                ? action.substring("pmute".length()).trim()
                : action.substring("logic-mute".length()).trim();
        if (payload.isBlank()) {
            plugin.getLogger().warning("Invalid mute action (missing duration): " + action);
            return;
        }

        String[] split = payload.split("\\s+", 2);
        Duration duration = DurationParser.parseDuration(split[0]);
        if (duration == null) {
            plugin.getLogger().warning("Invalid mute duration in action: " + action);
            return;
        }

        String reason = split.length > 1 && !split[1].isBlank() ? split[1] : fallbackReason;
        mutePlayer(target.getUniqueId(), playerName, duration, reason, actor);
    }

    private void handleWarnAction(String action, OfflinePlayer target, String playerName, String fallbackReason, String actor) {
        String payload = action.toLowerCase(Locale.ROOT).startsWith("pwarn")
                ? action.substring("pwarn".length()).trim()
                : action.substring("logic-warn".length()).trim();
        Duration duration = DurationParser.parseDuration(plugin.getConfig().getString("warnings.default-duration", "30d"));
        if (duration == null) {
            duration = Duration.ofDays(30);
        }

        String reason = fallbackReason;
        if (!payload.isBlank()) {
            String[] split = payload.split("\\s+", 2);
            Duration maybeDuration = DurationParser.parseDuration(split[0]);
            if (maybeDuration != null) {
                duration = maybeDuration;
                if (split.length > 1 && !split[1].isBlank()) {
                    reason = split[1];
                }
            } else {
                reason = payload;
            }
        }

        warnPlayer(target.getUniqueId(), playerName, duration, reason, actor);
    }

    private List<String> resolvePunishActions(String type) {
        String root = "punish." + type;
        List<String> actions = plugin.getConfig().getStringList(root + ".actions");
        if (!actions.isEmpty()) {
            return actions;
        }

        if (plugin.getConfig().isList(root)) {
            return plugin.getConfig().getStringList(root);
        }

        return new ArrayList<>();
    }

    private Map<String, String> basePlaceholders(String player, String reason, String actor, UUID uuid, Duration duration) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%player%", safe(player));
        placeholders.put("%reason%", safe(reason));
        placeholders.put("%actor%", safe(actor));
        placeholders.put("%uuid%", uuid == null ? "" : uuid.toString());

        String durationText = duration == null
                ? ""
                : DurationParser.formatDuration(duration);
        placeholders.put("%duration%", durationText);
        return placeholders;
    }

    private String applyPlaceholders(String input, Map<String, String> placeholders) {
        String result = safe(input);
        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            result = result.replace(placeholder.getKey(), safe(placeholder.getValue()));
        }
        return result;
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', safe(input));
    }

    private String safe(String input) {
        return input == null ? "" : input;
    }

    private record Escalation(int threshold, Duration duration, String reason) {
    }
}
