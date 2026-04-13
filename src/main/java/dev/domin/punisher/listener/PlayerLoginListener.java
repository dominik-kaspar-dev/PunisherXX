package dev.domin.punisher.listener;

import dev.domin.punisher.service.PunishmentService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

public class PlayerLoginListener implements Listener {
    private final PunishmentService punishmentService;

    public PlayerLoginListener(PunishmentService punishmentService) {
        this.punishmentService = punishmentService;
    }

    @EventHandler
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        punishmentService.handleAsyncLogin(event);
    }
}
