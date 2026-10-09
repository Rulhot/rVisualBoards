package ru.rulhot.rVisualBoards.hologram;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
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

    private final @NotNull User viewer;
    private final int entityId;
    private final @NotNull BoardGeometry.Box box;
    private final @NotNull ButtonAction action;

    PacketHitbox(@NotNull User viewer, @NotNull Location location, float width, float height,
                 @NotNull BoardGeometry.Box box, @NotNull ButtonAction action) {
        this.viewer = viewer;
        this.entityId = SpigotReflectionUtil.generateEntityId();
        this.box = box;
        this.action = action;
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

    @NotNull BoardGeometry.Box box() {
        return box;
    }

    @NotNull ButtonAction action() {
        return action;
    }

    void teleport(@NotNull Location location) {
        viewer.sendPacket(new WrapperPlayServerEntityTeleport(
                entityId,
                new Vector3d(location.getX(), location.getY(), location.getZ()),
                location.getYaw(),
                location.getPitch(),
                false));
    }
}
