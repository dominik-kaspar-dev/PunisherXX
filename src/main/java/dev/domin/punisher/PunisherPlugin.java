package dev.domin.punisher;

import dev.domin.punisher.command.PBanCommand;
import dev.domin.punisher.command.PKickCommand;
import dev.domin.punisher.command.PMuteCommand;
import dev.domin.punisher.command.PUnbanCommand;
import dev.domin.punisher.command.PUnmuteCommand;
import dev.domin.punisher.command.PunishCommand;
import dev.domin.punisher.command.WarnCommand;
import dev.domin.punisher.listener.PlayerChatListener;
import dev.domin.punisher.listener.PlayerLoginListener;
import dev.domin.punisher.service.PunishmentService;
import dev.domin.punisher.storage.PunishmentStore;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class PunisherPlugin extends JavaPlugin {
    private static final String CONFIG_FILE_NAME = "config.yml";
    private static final String CONFIG_VERSION_PATH = "config-version";

    private PunishmentStore punishmentStore;
    private PunishmentService punishmentService;

    @Override
    public void onEnable() {
        reloadPluginConfig();

        punishmentStore = new PunishmentStore(this);
        punishmentStore.load();

        punishmentService = new PunishmentService(this, punishmentStore);

        bindCommand("punish", new PunishCommand(this, punishmentService));
        bindCommand("warn", new WarnCommand(this, punishmentService));
        bindCommand("pban", new PBanCommand(this, punishmentService));
        bindCommand("pkick", new PKickCommand(this, punishmentService));
        bindCommand("pmute", new PMuteCommand(this, punishmentService));
        bindCommand("punban", new PUnbanCommand(this, punishmentService));
        bindCommand("punmute", new PUnmuteCommand(this, punishmentService));

        getServer().getPluginManager().registerEvents(new PlayerLoginListener(punishmentService), this);
        getServer().getPluginManager().registerEvents(new PlayerChatListener(punishmentService), this);
        getLogger().info("PunisherXX enabled.");
    }

    @Override
    public void onDisable() {
        if (punishmentStore == null) {
            return;
        }

        try {
            punishmentStore.save();
        } catch (IOException exception) {
            getLogger().severe("Failed to save punishment data: " + exception.getMessage());
        }
    }

    private void bindCommand(String name, CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Command not found in plugin.yml: " + name);
            return;
        }

        command.setExecutor(executor);
        if (executor instanceof TabCompleter tabCompleter) {
            command.setTabCompleter(tabCompleter);
        }
    }

    public void reloadPluginConfig() {
        ensureLatestGeneratedConfig();
        reloadConfig();
    }

    private void ensureLatestGeneratedConfig() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("Could not create plugin data folder for config.yml.");
            return;
        }

        File configFile = new File(getDataFolder(), CONFIG_FILE_NAME);
        String bundledVersion = readBundledConfigVersion();
        if (bundledVersion == null || bundledVersion.isBlank()) {
            // Fallback keeps startup resilient even if embedded config parsing fails.
            saveDefaultConfig();
            return;
        }

        if (!configFile.exists()) {
            saveResource(CONFIG_FILE_NAME, false);
            return;
        }

        String existingVersion = YamlConfiguration.loadConfiguration(configFile)
                .getString(CONFIG_VERSION_PATH, "unknown");
        if (bundledVersion.equals(existingVersion)) {
            return;
        }

        File backupFile = findAvailableBackupFile(sanitizeVersion(existingVersion));
        try {
            Files.move(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            saveResource(CONFIG_FILE_NAME, true);
            getLogger().info("config.yml updated from version " + existingVersion
                    + " to " + bundledVersion
                    + ". Previous file renamed to " + backupFile.getName() + ".");
        } catch (IOException exception) {
            getLogger().severe("Failed to migrate config.yml: " + exception.getMessage());
        }
    }

    private String readBundledConfigVersion() {
        try (InputStream resource = getResource(CONFIG_FILE_NAME)) {
            if (resource == null) {
                getLogger().severe("Embedded config.yml is missing from the plugin jar.");
                return null;
            }

            YamlConfiguration bundled = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resource, StandardCharsets.UTF_8)
            );
            return bundled.getString(CONFIG_VERSION_PATH);
        } catch (IOException exception) {
            getLogger().severe("Failed to read embedded config.yml: " + exception.getMessage());
            return null;
        }
    }

    private File findAvailableBackupFile(String existingVersion) {
        String baseName = "__old_config_[" + existingVersion + "]__";
        File candidate = new File(getDataFolder(), baseName + ".yml");
        int index = 1;
        while (candidate.exists()) {
            candidate = new File(getDataFolder(), baseName + "_" + index + ".yml");
            index++;
        }
        return candidate;
    }

    private String sanitizeVersion(String version) {
        if (version == null || version.isBlank()) {
            return "unknown";
        }

        String sanitized = version.replaceAll("[^a-zA-Z0-9._-]", "_");
        return sanitized.isBlank() ? "unknown" : sanitized;
    }
}
