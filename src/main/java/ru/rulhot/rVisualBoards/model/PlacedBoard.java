package ru.rulhot.rVisualBoards.model;

import lombok.With;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@With
public record PlacedBoard(
        @NotNull String id,
        @NotNull String type,
        @NotNull String world,
        double x,
        double y,
        double z,
        float yaw,
        double scale,
        @Nullable String defaultTop
) {

    public static final int MAX_ID_LENGTH = 32;
    public static final double DEFAULT_SCALE = 1D;
    public static final double MIN_SCALE = 0.25D;
    public static final double MAX_SCALE = 4D;

    private static final @NotNull Pattern ID_PATTERN = Pattern.compile("[a-z0-9_-]{1," + MAX_ID_LENGTH + "}");
    private static final float HALF_TURN = 180F;
    private static final float QUARTER_TURN = 90F;
    private static final float FULL_TURN = 360F;
    private static final double BLOCK_CENTER = 0.5D;
    private static final int NUMBER_SCALE = 1;

    public static @NotNull String normalizeId(@NotNull String raw) {
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isValidId(@NotNull String id) {
        return ID_PATTERN.matcher(id).matches();
    }

    public static boolean isValidScale(double scale) {
        return scale >= MIN_SCALE && scale <= MAX_SCALE;
    }

    public static @NotNull PlacedBoard create(@NotNull String id, @NotNull String type, @NotNull String world,
                                              double x, double y, double z, float viewerYaw, boolean snapToGrid) {
        return new PlacedBoard(id, type, world, x, y, z, 0F, DEFAULT_SCALE, null)
                .placedAt(world, x, y, z, viewerYaw, snapToGrid);
    }

    public @NotNull PlacedBoard placedAt(@NotNull String targetWorld, double targetX, double targetY, double targetZ,
                                         float viewerYaw, boolean snapToGrid) {
        float boardYaw = boardYaw(viewerYaw, snapToGrid);
        if (!snapToGrid) {
            return new PlacedBoard(id, type, targetWorld, targetX, targetY, targetZ, boardYaw, scale, defaultTop);
        }
        return new PlacedBoard(id, type, targetWorld, blockCenter(targetX), Math.floor(targetY), blockCenter(targetZ),
                boardYaw, scale, defaultTop);
    }

    public @NotNull PlacedBoard facing(float viewerYaw, boolean snapToGrid) {
        return withYaw(boardYaw(viewerYaw, snapToGrid));
    }

    public @NotNull PlacedBoard rotatedTo(double degrees) {
        return withYaw(normalizeYaw((float) degrees));
    }

    public @NotNull PlacedBoard shifted(@NotNull Direction direction, double distance) {
        double radians = Math.toRadians(yaw);
        double rightX = Math.cos(radians);
        double rightZ = Math.sin(radians);
        double frontX = -Math.sin(radians);
        double frontZ = Math.cos(radians);
        return switch (direction) {
            case UP -> moved(0D, distance, 0D);
            case DOWN -> moved(0D, -distance, 0D);
            case RIGHT -> moved(rightX * distance, 0D, rightZ * distance);
            case LEFT -> moved(-rightX * distance, 0D, -rightZ * distance);
            case FRONT -> moved(frontX * distance, 0D, frontZ * distance);
            case BACK -> moved(-frontX * distance, 0D, -frontZ * distance);
        };
    }

    public double frontX(double distance) {
        return x - Math.sin(Math.toRadians(yaw)) * distance;
    }

    public double frontZ(double distance) {
        return z + Math.cos(Math.toRadians(yaw)) * distance;
    }

    public float viewerYaw() {
        return normalizeYaw(yaw + HALF_TURN);
    }

    public @NotNull Map<String, Object> placeholders() {
        return Map.of(
                "id", id,
                "type", type,
                "world", world,
                "x", number(x),
                "y", number(y),
                "z", number(z),
                "yaw", number(yaw),
                "scale", number(scale));
    }

    static float boardYaw(float viewerYaw, boolean snapToGrid) {
        float yaw = viewerYaw + HALF_TURN;
        if (snapToGrid) {
            yaw = Math.round(yaw / QUARTER_TURN) * QUARTER_TURN;
        }
        return normalizeYaw(yaw);
    }

    private @NotNull PlacedBoard moved(double deltaX, double deltaY, double deltaZ) {
        return new PlacedBoard(id, type, world, x + deltaX, y + deltaY, z + deltaZ, yaw, scale, defaultTop);
    }

    private static float normalizeYaw(float yaw) {
        return ((yaw % FULL_TURN) + FULL_TURN) % FULL_TURN;
    }

    private static double blockCenter(double coordinate) {
        return Math.floor(coordinate) + BLOCK_CENTER;
    }

    private static @NotNull String number(double value) {
        return BigDecimal.valueOf(value).setScale(NUMBER_SCALE, RoundingMode.HALF_UP).toPlainString();
    }

    public enum Direction {
        UP,
        DOWN,
        LEFT,
        RIGHT,
        FRONT,
        BACK;

        public @NotNull String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
