package ru.rulhot.rVisualBoards.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rVisualBoards.manager.HologramManager;

@RequiredArgsConstructor
public final class PlayerQuitListener implements Listener {

    private final @NotNull HologramManager holograms;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(@NotNull PlayerQuitEvent event) {
        holograms.forget(event.getPlayer().getUniqueId());
    }
}
