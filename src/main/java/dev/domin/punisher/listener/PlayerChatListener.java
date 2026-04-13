package dev.domin.punisher.listener;

import dev.domin.punisher.service.PunishmentService;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class PlayerChatListener implements Listener {
    private final PunishmentService punishmentService;

    public PlayerChatListener(PunishmentService punishmentService) {
        this.punishmentService = punishmentService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        punishmentService.handleAsyncChat(event);
    }
}
