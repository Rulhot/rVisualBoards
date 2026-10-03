package ru.rulhot.rVisualBoards.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rVisualBoards.manager.HologramManager;

@RequiredArgsConstructor
public final class WorldListener implements Listener {

    private final @NotNull HologramManager holograms;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLoad(@NotNull WorldLoadEvent event) {
        holograms.loadWorld(event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUnload(@NotNull WorldUnloadEvent event) {
        holograms.unloadWorld(event.getWorld());
    }
}
