package ru.rulhot.rVisualBoards.listener;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rVisualBoards.manager.HologramManager;

@RequiredArgsConstructor
public final class ViewerListener implements Listener {

    private final @NotNull HologramManager holograms;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(@NotNull PlayerQuitEvent event) {
        holograms.forget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(@NotNull PlayerPostRespawnEvent event) {
        holograms.resend(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(@NotNull PlayerChangedWorldEvent event) {
        holograms.resend(event.getPlayer().getUniqueId());
    }
}
