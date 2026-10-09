package ru.rulhot.rVisualBoards.listener;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rVisualBoards.hologram.HitboxRegistry;
import ru.rulhot.rVisualBoards.manager.HologramManager;
import ru.rulhot.rVisualBoards.util.SchedulerUtil;

import java.util.UUID;

@RequiredArgsConstructor
public final class HologramClickListener extends PacketListenerAbstract {

    private final @NotNull HologramManager holograms;
    private final @NotNull SchedulerUtil scheduler;

    @Override
    public void onPacketReceive(@NotNull PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) {
            return;
        }
        WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
        HitboxRegistry.Target target = holograms.hitboxes().find(packet.getEntityId());
        if (target == null) {
            return;
        }
        event.setCancelled(true);
        User user = event.getUser();
        UUID viewerId = user.getUUID();
        if (viewerId == null || !viewerId.equals(target.viewerId()) || !isClick(packet)) {
            return;
        }
        Player player = Bukkit.getPlayer(viewerId);
        if (player == null) {
            return;
        }
        scheduler.runFor(player, () -> holograms.press(player, target.boardId(), target.action()));
    }

    private static boolean isClick(@NotNull WrapperPlayClientInteractEntity packet) {
        return switch (packet.getAction()) {
            case ATTACK -> true;
            case INTERACT -> packet.getHand() == InteractionHand.MAIN_HAND;
            case INTERACT_AT -> false;
        };
    }
}
