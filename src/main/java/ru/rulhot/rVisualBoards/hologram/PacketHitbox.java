package ru.rulhot.rVisualBoards.hologram;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

final class PacketHitbox {

    private static final int WIDTH = 8;
    private static final int HEIGHT = 9;
    private static final int RESPONSE = 10;

    private final int entityId;

    PacketHitbox(@NotNull User viewer, @NotNull Location location, float width, float height) {
        this.entityId = SpigotReflectionUtil.generateEntityId();
        viewer.sendPacket(new WrapperPlayServerSpawnEntity(
                entityId,
                UUID.randomUUID(),
                EntityTypes.INTERACTION,
                new com.github.retrooper.packetevents.protocol.world.Location(location.getX(), location.getY(),
                        location.getZ(), location.getYaw(), location.getPitch()),
                location.getYaw(),
                0,
                null));
        viewer.sendPacket(new WrapperPlayServerEntityMetadata(entityId, List.of(
                new EntityData<>(WIDTH, EntityDataTypes.FLOAT, width),
                new EntityData<>(HEIGHT, EntityDataTypes.FLOAT, height),
                new EntityData<>(RESPONSE, EntityDataTypes.BOOLEAN, true))));
    }

    int entityId() {
        return entityId;
    }
}
