package dev.domin.punisher;

import dev.domin.punisher.command.PBanCommand;
import dev.domin.punisher.command.PKickCommand;
import dev.domin.punisher.command.PMuteCommand;
import dev.domin.punisher.command.PunishCommand;
import dev.domin.punisher.command.WarnCommand;
import dev.domin.punisher.listener.PlayerChatListener;
import dev.domin.punisher.listener.PlayerLoginListener;
import dev.domin.punisher.service.PunishmentService;
import dev.domin.punisher.storage.PunishmentStore;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;

public class PunisherPlugin extends JavaPlugin {
    private PunishmentStore punishmentStore;
    private PunishmentService punishmentService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        punishmentStore = new PunishmentStore(this);
        punishmentStore.load();

        punishmentService = new PunishmentService(this, punishmentStore);

        bindCommand("punish", new PunishCommand(this, punishmentService));
        bindCommand("warn", new WarnCommand(this, punishmentService));
        bindCommand("pban", new PBanCommand(this, punishmentService));
        bindCommand("pkick", new PKickCommand(this, punishmentService));
        bindCommand("pmute", new PMuteCommand(this, punishmentService));

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
}
