package ru.rulhot.rVisualBoards.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rVisualBoards.manager.HologramManager;

@RequiredArgsConstructor
public final class HologramRowClickListener implements Listener {

    private final @NotNull HologramManager holograms;

    @EventHandler(ignoreCancelled = true)
    public void onSwing(@NotNull PlayerAnimationEvent event) {
        if (event.getAnimationType() == PlayerAnimationType.ARM_SWING) {
            holograms.clickRow(event.getPlayer());
        }
    }
}
