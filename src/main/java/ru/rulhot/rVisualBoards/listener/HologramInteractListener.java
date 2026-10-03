package ru.rulhot.rVisualBoards.listener;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rVisualBoards.hologram.ButtonAction;
import ru.rulhot.rVisualBoards.hologram.HologramKeys;
import ru.rulhot.rVisualBoards.manager.HologramManager;

@RequiredArgsConstructor
public final class HologramInteractListener implements Listener {

    private final @NotNull HologramKeys keys;
    private final @NotNull HologramManager holograms;

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(@NotNull PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!isButton(entity)) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        press(event.getPlayer(), entity);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAttack(@NotNull PrePlayerAttackEntityEvent event) {
        Entity entity = event.getAttacked();
        if (!isButton(entity)) {
            return;
        }
        event.setCancelled(true);
        press(event.getPlayer(), entity);
    }

    private boolean isButton(@NotNull Entity entity) {
        return entity instanceof Interaction
                && entity.getPersistentDataContainer().has(keys.board(), PersistentDataType.STRING);
    }

    private void press(@NotNull Player player, @NotNull Entity entity) {
        PersistentDataContainer container = entity.getPersistentDataContainer();
        String boardId = container.get(keys.board(), PersistentDataType.STRING);
        ButtonAction action = ButtonAction.parse(container.get(keys.action(), PersistentDataType.STRING));
        if (boardId == null || action == null) {
            return;
        }
        holograms.press(player, boardId, action);
    }
}
