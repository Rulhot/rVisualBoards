package ru.rulhot.rVisualBoards.hologram;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.TextDisplay;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

final class PacketDisplay {

    private static final int INTERPOLATION_DELAY = 8;
    private static final int INTERPOLATION_DURATION = 9;
    private static final int TRANSLATION = 11;
    private static final int SCALE = 12;
    private static final int BRIGHTNESS = 16;
    private static final int VIEW_RANGE = 17;
    private static final int TEXT = 23;
    private static final int LINE_WIDTH = 24;
    private static final int BACKGROUND = 25;
    private static final int OPACITY = 26;
    private static final int FLAGS = 27;

    private static final int FLAG_SHADOW = 0x01;
    private static final int FLAG_ALIGN_LEFT = 0x08;
    private static final int FLAG_ALIGN_RIGHT = 0x10;
    private static final int BLOCK_LIGHT_SHIFT = 4;
    private static final int SKY_LIGHT_SHIFT = 20;

    private final @NotNull User viewer;
    private final int entityId;

    PacketDisplay(@NotNull User viewer, @NotNull Location location, @NotNull List<EntityData<?>> state) {
        this.viewer = viewer;
        this.entityId = SpigotReflectionUtil.generateEntityId();
        viewer.sendPacket(new WrapperPlayServerSpawnEntity(
                entityId,
                UUID.randomUUID(),
                EntityTypes.TEXT_DISPLAY,
                new com.github.retrooper.packetevents.protocol.world.Location(location.getX(), location.getY(),
                        location.getZ(), location.getYaw(), location.getPitch()),
                location.getYaw(),
                0,
                null));
        send(state);
    }

    int entityId() {
        return entityId;
    }

    void send(@NotNull List<EntityData<?>> state) {
        viewer.sendPacket(new WrapperPlayServerEntityMetadata(entityId, state));
    }

    void teleport(@NotNull Location location) {
        viewer.sendPacket(new WrapperPlayServerEntityTeleport(
                entityId,
                new Vector3d(location.getX(), location.getY(), location.getZ()),
                location.getYaw(),
                location.getPitch(),
                false));
    }

    static @NotNull EntityData<?> interpolationDelay(int ticks) {
        return new EntityData<>(INTERPOLATION_DELAY, EntityDataTypes.INT, ticks);
    }

    static @NotNull EntityData<?> interpolationDuration(int ticks) {
        return new EntityData<>(INTERPOLATION_DURATION, EntityDataTypes.INT, ticks);
    }

    static @NotNull EntityData<?> translation(float x, float y, float z) {
        return new EntityData<>(TRANSLATION, EntityDataTypes.VECTOR3F, new Vector3f(x, y, z));
    }

    static @NotNull EntityData<?> scale(float x, float y, float z) {
        return new EntityData<>(SCALE, EntityDataTypes.VECTOR3F, new Vector3f(x, y, z));
    }

    static @NotNull EntityData<?> brightness(int blockLight, int skyLight) {
        return new EntityData<>(BRIGHTNESS, EntityDataTypes.INT,
                blockLight << BLOCK_LIGHT_SHIFT | skyLight << SKY_LIGHT_SHIFT);
    }

    static @NotNull EntityData<?> viewRange(float range) {
        return new EntityData<>(VIEW_RANGE, EntityDataTypes.FLOAT, range);
    }

    static @NotNull EntityData<?> text(@NotNull Component text) {
        return new EntityData<>(TEXT, EntityDataTypes.ADV_COMPONENT, text);
    }

    static @NotNull EntityData<?> lineWidth(int width) {
        return new EntityData<>(LINE_WIDTH, EntityDataTypes.INT, width);
    }

    static @NotNull EntityData<?> background(@NotNull Color color) {
        return new EntityData<>(BACKGROUND, EntityDataTypes.INT, color.asARGB());
    }

    static @NotNull EntityData<?> opacity(byte opacity) {
        return new EntityData<>(OPACITY, EntityDataTypes.BYTE, opacity);
    }

    static @NotNull EntityData<?> flags(boolean shadowed, @NotNull TextDisplay.TextAlignment alignment) {
        int flags = shadowed ? FLAG_SHADOW : 0;
        flags |= switch (alignment) {
            case LEFT -> FLAG_ALIGN_LEFT;
            case RIGHT -> FLAG_ALIGN_RIGHT;
            case CENTER -> 0;
        };
        return new EntityData<>(FLAGS, EntityDataTypes.BYTE, (byte) flags);
    }
}
