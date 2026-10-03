package ru.rulhot.rVisualBoards.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rVisualBoards.manager.HologramManager;

@RequiredArgsConstructor
public final class HologramScrollListener implements Listener {

    private static final int HOTBAR_SIZE = 9;
    private static final int HALF_HOTBAR = HOTBAR_SIZE / 2;
    private static final int DOWN = 1;
    private static final int UP = -1;

    private final @NotNull HologramManager holograms;

    @EventHandler(ignoreCancelled = true)
    public void onItemHeld(@NotNull PlayerItemHeldEvent event) {
        int shift = Math.floorMod(event.getNewSlot() - event.getPreviousSlot(), HOTBAR_SIZE);
        if (shift == 0) {
            return;
        }
        if (holograms.scroll(event.getPlayer(), shift <= HALF_HOTBAR ? DOWN : UP)) {
            event.setCancelled(true);
        }
    }
}
