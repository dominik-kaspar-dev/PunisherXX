package dev.domin.punisher.storage;

import dev.domin.punisher.model.BanEntry;
import dev.domin.punisher.model.MuteEntry;
import dev.domin.punisher.model.WarningEntry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class PunishmentStore {
    private final JavaPlugin plugin;
    private final File dataFile;

    private final Map<UUID, BanEntry> bans = new HashMap<>();
    private final Map<UUID, MuteEntry> mutes = new HashMap<>();
    private final Map<UUID, List<WarningEntry>> warnings = new HashMap<>();

    public PunishmentStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
    }

    public synchronized void load() {
        ensureDataFileExists();

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        bans.clear();
        mutes.clear();
        warnings.clear();

        ConfigurationSection bansSection = yaml.getConfigurationSection("bans");
        if (bansSection != null) {
            for (String uuidKey : bansSection.getKeys(false)) {
                UUID uuid = parseUuid(uuidKey);
                if (uuid == null) {
                    continue;
                }

                String path = "bans." + uuidKey;
                BanEntry entry = new BanEntry(
                        yaml.getString(path + ".player-name", "Unknown"),
                        yaml.getString(path + ".reason", "No reason provided."),
                        yaml.getString(path + ".actor", "Console"),
                        yaml.getLong(path + ".created-at", System.currentTimeMillis()),
                        yaml.getLong(path + ".expires-at", 0L)
                );
                bans.put(uuid, entry);
            }
        }

        ConfigurationSection mutesSection = yaml.getConfigurationSection("mutes");
        if (mutesSection != null) {
            for (String uuidKey : mutesSection.getKeys(false)) {
                UUID uuid = parseUuid(uuidKey);
                if (uuid == null) {
                    continue;
                }

                String path = "mutes." + uuidKey;
                MuteEntry entry = new MuteEntry(
                        yaml.getString(path + ".player-name", "Unknown"),
                        yaml.getString(path + ".reason", "No reason provided."),
                        yaml.getString(path + ".actor", "Console"),
                        yaml.getLong(path + ".created-at", System.currentTimeMillis()),
                        yaml.getLong(path + ".expires-at", 0L)
                );
                mutes.put(uuid, entry);
            }
        }

        ConfigurationSection warningsSection = yaml.getConfigurationSection("warnings");
        if (warningsSection != null) {
            for (String uuidKey : warningsSection.getKeys(false)) {
                UUID uuid = parseUuid(uuidKey);
                if (uuid == null) {
                    continue;
                }

                List<Map<?, ?>> rawWarnings = yaml.getMapList("warnings." + uuidKey);
                List<WarningEntry> parsedWarnings = new ArrayList<>();

                for (Map<?, ?> raw : rawWarnings) {
                    Object reasonValue = raw.containsKey("reason") ? raw.get("reason") : "Staff warning.";
                    Object actorValue = raw.containsKey("actor") ? raw.get("actor") : "Console";
                    String reason = String.valueOf(reasonValue);
                    String actor = String.valueOf(actorValue);
                    long createdAt = toLong(raw.get("created-at"), System.currentTimeMillis());
                    long expiresAt = toLong(raw.get("expires-at"), 0L);
                    parsedWarnings.add(new WarningEntry(reason, actor, createdAt, expiresAt));
                }

                if (!parsedWarnings.isEmpty()) {
                    warnings.put(uuid, parsedWarnings);
                }
            }
        }

        if (cleanupExpiredInternal()) {
            saveQuietly();
        }
    }

    public synchronized Optional<BanEntry> getActiveBan(UUID uuid) {
        BanEntry entry = bans.get(uuid);
        if (entry == null) {
            return Optional.empty();
        }

        if (entry.isExpired(System.currentTimeMillis())) {
            bans.remove(uuid);
            saveQuietly();
            return Optional.empty();
        }

        return Optional.of(entry);
    }

    public synchronized void setBan(UUID uuid, BanEntry banEntry) {
        bans.put(uuid, banEntry);
        saveQuietly();
    }

    public synchronized Optional<MuteEntry> getActiveMute(UUID uuid) {
        MuteEntry entry = mutes.get(uuid);
        if (entry == null) {
            return Optional.empty();
        }

        if (entry.isExpired(System.currentTimeMillis())) {
            mutes.remove(uuid);
            saveQuietly();
            return Optional.empty();
        }

        return Optional.of(entry);
    }

    public synchronized void setMute(UUID uuid, MuteEntry muteEntry) {
        mutes.put(uuid, muteEntry);
        saveQuietly();
    }

    public synchronized boolean removeMute(UUID uuid) {
        MuteEntry removed = mutes.remove(uuid);
        if (removed != null) {
            saveQuietly();
            return true;
        }
        return false;
    }

    public synchronized boolean removeBan(UUID uuid) {
        BanEntry removed = bans.remove(uuid);
        if (removed != null) {
            saveQuietly();
            return true;
        }
        return false;
    }

    public synchronized void addWarning(UUID uuid, WarningEntry warningEntry) {
        warnings.computeIfAbsent(uuid, ignored -> new ArrayList<>()).add(warningEntry);
        saveQuietly();
    }

    public synchronized List<WarningEntry> getActiveWarnings(UUID uuid) {
        List<WarningEntry> rawWarnings = warnings.getOrDefault(uuid, List.of());
        if (rawWarnings.isEmpty()) {
            return List.of();
        }

        long now = System.currentTimeMillis();
        List<WarningEntry> activeWarnings = rawWarnings.stream().filter(entry -> !entry.isExpired(now)).toList();

        if (activeWarnings.size() != rawWarnings.size()) {
            if (activeWarnings.isEmpty()) {
                warnings.remove(uuid);
            } else {
                warnings.put(uuid, new ArrayList<>(activeWarnings));
            }
            saveQuietly();
        }

        return activeWarnings;
    }

    public synchronized int getActiveWarningCount(UUID uuid) {
        return getActiveWarnings(uuid).size();
    }

    public synchronized void clearWarnings(UUID uuid) {
        if (warnings.remove(uuid) != null) {
            saveQuietly();
        }
    }

    public synchronized void cleanupExpired() {
        if (cleanupExpiredInternal()) {
            saveQuietly();
        }
    }

    private boolean cleanupExpiredInternal() {
        boolean changed = false;
        long now = System.currentTimeMillis();

        Iterator<Map.Entry<UUID, BanEntry>> banIterator = bans.entrySet().iterator();
        while (banIterator.hasNext()) {
            Map.Entry<UUID, BanEntry> entry = banIterator.next();
            if (entry.getValue().isExpired(now)) {
                banIterator.remove();
                changed = true;
            }
        }

        Iterator<Map.Entry<UUID, MuteEntry>> muteIterator = mutes.entrySet().iterator();
        while (muteIterator.hasNext()) {
            Map.Entry<UUID, MuteEntry> entry = muteIterator.next();
            if (entry.getValue().isExpired(now)) {
                muteIterator.remove();
                changed = true;
            }
        }

        Iterator<Map.Entry<UUID, List<WarningEntry>>> warnIterator = warnings.entrySet().iterator();
        while (warnIterator.hasNext()) {
            Map.Entry<UUID, List<WarningEntry>> entry = warnIterator.next();
            List<WarningEntry> activeWarnings = entry.getValue().stream().filter(warning -> !warning.isExpired(now)).toList();
            if (activeWarnings.size() != entry.getValue().size()) {
                changed = true;
            }
            if (activeWarnings.isEmpty()) {
                warnIterator.remove();
            } else {
                entry.setValue(new ArrayList<>(activeWarnings));
            }
        }

        return changed;
    }

    public synchronized void save() throws IOException {
        ensureDataFileExists();

        YamlConfiguration yaml = new YamlConfiguration();

        for (Map.Entry<UUID, BanEntry> entry : bans.entrySet()) {
            String path = "bans." + entry.getKey();
            BanEntry ban = entry.getValue();
            yaml.set(path + ".player-name", ban.playerName());
            yaml.set(path + ".reason", ban.reason());
            yaml.set(path + ".actor", ban.actor());
            yaml.set(path + ".created-at", ban.createdAt());
            yaml.set(path + ".expires-at", ban.expiresAt());
        }

        for (Map.Entry<UUID, MuteEntry> entry : mutes.entrySet()) {
            String path = "mutes." + entry.getKey();
            MuteEntry mute = entry.getValue();
            yaml.set(path + ".player-name", mute.playerName());
            yaml.set(path + ".reason", mute.reason());
            yaml.set(path + ".actor", mute.actor());
            yaml.set(path + ".created-at", mute.createdAt());
            yaml.set(path + ".expires-at", mute.expiresAt());
        }

        for (Map.Entry<UUID, List<WarningEntry>> entry : warnings.entrySet()) {
            String path = "warnings." + entry.getKey();
            List<Map<String, Object>> serialized = new ArrayList<>();
            for (WarningEntry warning : entry.getValue()) {
                Map<String, Object> data = new HashMap<>();
                data.put("reason", warning.reason());
                data.put("actor", warning.actor());
                data.put("created-at", warning.createdAt());
                data.put("expires-at", warning.expiresAt());
                serialized.add(data);
            }
            yaml.set(path, serialized);
        }

        yaml.save(dataFile);
    }

    private void saveQuietly() {
        try {
            save();
        } catch (IOException exception) {
            plugin.getLogger().severe("Failed to save data.yml: " + exception.getMessage());
        }
    }

    private void ensureDataFileExists() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder.");
        }
        if (!dataFile.exists()) {
            try {
                if (!dataFile.createNewFile()) {
                    plugin.getLogger().warning("Could not create data.yml file.");
                }
            } catch (IOException exception) {
                plugin.getLogger().severe("Failed to create data.yml: " + exception.getMessage());
            }
        }
    }

    private UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            plugin.getLogger().warning("Skipping invalid UUID key in data.yml: " + raw);
            return null;
        }
    }

    private long toLong(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string) {
            try {
                return Long.parseLong(string);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
