package ru.rulhot.rVisualBoards.hologram;

import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rVisualBoards.model.PlacedBoard;

final class BoardFrame {

    private static final double PARALLEL_EPSILON = 1.0E-6D;
    private static final int CHUNK_SHIFT = 4;
    private static final float FULL_TURN = 360F;
    private static final float PROTOCOL_ANGLE_STEP = FULL_TURN / 256F;

    private final @NotNull Location origin;
    private final double rightX;
    private final double rightZ;
    private final double normalX;
    private final double normalZ;
    private final double scale;

    BoardFrame(@NotNull World world, @NotNull PlacedBoard board) {
        this(world, board.x(), board.y(), board.z(), board.yaw(), board.scale());
    }

    private BoardFrame(@NotNull World world, double x, double y, double z, float yaw, double scale) {
        float exact = Math.round(normalize(yaw) / PROTOCOL_ANGLE_STEP) * PROTOCOL_ANGLE_STEP;
        this.origin = new Location(world, x, y, z, exact, 0F);
        this.scale = scale;
        double radians = Math.toRadians(exact);
        this.rightX = Math.cos(radians);
        this.rightZ = Math.sin(radians);
        this.normalX = -Math.sin(radians);
        this.normalZ = Math.cos(radians);
    }

    private static float normalize(float yaw) {
        return ((yaw % FULL_TURN) + FULL_TURN) % FULL_TURN;
    }

    @NotNull Location origin() {
        return origin.clone();
    }

    @NotNull World world() {
        return origin.getWorld();
    }

    boolean isLoaded() {
        if (!origin.isWorldLoaded()) {
            return false;
        }
        return world().isChunkLoaded(origin.getBlockX() >> CHUNK_SHIFT, origin.getBlockZ() >> CHUNK_SHIFT);
    }

    double scale() {
        return scale;
    }

    @NotNull Location point(double x, double y, double depth) {
        double sideways = x * scale;
        double forward = depth * scale;
        return origin.clone().add(rightX * sideways + normalX * forward, y * scale,
                rightZ * sideways + normalZ * forward);
    }

    @Nullable Point project(double eyeX, double eyeY, double eyeZ, float yaw, float pitch, double maxDistance) {
        double offsetX = eyeX - origin.getX();
        double offsetY = eyeY - origin.getY();
        double offsetZ = eyeZ - origin.getZ();
        double side = offsetX * normalX + offsetZ * normalZ;
        if (side <= 0D) {
            return null;
        }
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        double horizontal = Math.cos(pitchRadians);
        double directionX = -horizontal * Math.sin(yawRadians);
        double directionY = -Math.sin(pitchRadians);
        double directionZ = horizontal * Math.cos(yawRadians);
        double denominator = directionX * normalX + directionZ * normalZ;
        if (Math.abs(denominator) < PARALLEL_EPSILON) {
            return null;
        }
        double distance = -side / denominator;
        if (distance <= 0D || distance > maxDistance) {
            return null;
        }
        double hitX = offsetX + directionX * distance;
        double hitZ = offsetZ + directionZ * distance;
        return new Point((hitX * rightX + hitZ * rightZ) / scale, (offsetY + directionY * distance) / scale);
    }

    record Point(double x, double y) {
    }
}
