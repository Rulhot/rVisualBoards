package ru.rulhot.rVisualBoards.hologram;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

final class HologramPart {

    private static final byte FADED_OPACITY = 5;
    private static final byte FULL_OPACITY = -1;

    private final @NotNull PacketDisplay display;
    private final double height;
    private float scaleX;
    private float scaleY;
    private float scaleZ;
    private @Nullable Color background;
    private double offsetX;
    private double zoom = 1D;
    private boolean faded;

    HologramPart(@NotNull PacketDisplay display, float scaleX, float scaleY, float scaleZ, double height,
                 @Nullable Color background) {
        this.display = display;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.scaleZ = scaleZ;
        this.height = height;
        this.background = background;
    }

    int entityId() {
        return display.entityId();
    }

    void text(@NotNull Component text) {
        display.send(List.of(PacketDisplay.text(text)));
    }

    void moveTo(@NotNull Location location) {
        display.teleport(location);
    }

    void recolor(@NotNull Color color) {
        background = color;
        display.send(List.of(PacketDisplay.background(color)));
    }

    boolean rescale(float scale) {
        if (scale == scaleX && scale == scaleY && scale == scaleZ) {
            return false;
        }
        scaleX = scale;
        scaleY = scale;
        scaleZ = scale;
        return true;
    }

    void resetPose() {
        offsetX = 0D;
        zoom = 1D;
        faded = false;
    }

    void shift(double offsetX) {
        this.offsetX = offsetX;
    }

    void zoom(double zoom) {
        this.zoom = zoom;
    }

    void fade() {
        faded = true;
    }

    void apply(int durationTicks) {
        float factor = (float) zoom;
        float lift = (float) (-(zoom - 1D) * height / 2D);
        Color current = background;
        display.send(List.of(
                PacketDisplay.interpolationDelay(0),
                PacketDisplay.interpolationDuration(durationTicks),
                PacketDisplay.translation((float) offsetX, lift, 0F),
                PacketDisplay.scale(scaleX * factor, scaleY * factor, scaleZ),
                current == null
                        ? PacketDisplay.opacity(faded ? FADED_OPACITY : FULL_OPACITY)
                        : PacketDisplay.background(faded
                        ? Color.fromARGB(0, current.getRed(), current.getGreen(), current.getBlue())
                        : current)));
    }
}
