package ru.rulhot.rVisualBoards.hologram;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

final class HologramPart {

    private static final byte FADED_OPACITY = 5;
    private static final byte FULL_OPACITY = -1;

    private final @NotNull TextDisplay display;
    private final double height;
    private float scaleX;
    private float scaleY;
    private float scaleZ;
    private @Nullable Color background;
    private double offsetX;
    private double zoom = 1D;
    private boolean faded;

    HologramPart(@NotNull TextDisplay display, float scaleX, float scaleY, float scaleZ, double height,
                 @Nullable Color background) {
        this.display = display;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.scaleZ = scaleZ;
        this.height = height;
        this.background = background;
    }

    @NotNull TextDisplay display() {
        return display;
    }

    void text(@NotNull Component text) {
        display.text(text);
    }

    void recolor(@NotNull Color color) {
        background = color;
        display.setBackgroundColor(color);
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
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(durationTicks);
        float factor = (float) zoom;
        float lift = (float) (-(zoom - 1D) * height / 2D);
        display.setTransformation(new Transformation(
                new Vector3f((float) offsetX, lift, 0F),
                new AxisAngle4f(),
                new Vector3f(scaleX * factor, scaleY * factor, scaleZ),
                new AxisAngle4f()));
        Color current = background;
        if (current != null) {
            display.setBackgroundColor(faded
                    ? Color.fromARGB(0, current.getRed(), current.getGreen(), current.getBlue())
                    : current);
            return;
        }
        display.setTextOpacity(faded ? FADED_OPACITY : FULL_OPACITY);
    }
}
