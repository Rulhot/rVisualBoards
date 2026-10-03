package ru.rulhot.rVisualBoards.hologram;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public final class FontMetrics {

    private static final int DEFAULT_ADVANCE = 6;
    private static final @NotNull String LINE_BREAK = "\n";

    private final @NotNull Map<Integer, Integer> overrides;

    public FontMetrics(@NotNull Map<Integer, Integer> overrides) {
        this.overrides = Map.copyOf(overrides);
    }

    public int width(@NotNull Component component) {
        String plain = PlainTextComponentSerializer.plainText().serialize(component);
        int widest = 0;
        for (String line : plain.split(LINE_BREAK, -1)) {
            widest = Math.max(widest, lineWidth(line));
        }
        return widest;
    }

    private int lineWidth(@NotNull String line) {
        return line.codePoints().map(this::advance).sum();
    }

    private int advance(int codePoint) {
        Integer override = overrides.get(codePoint);
        if (override != null) {
            return override;
        }
        return switch (codePoint) {
            case '!', '\'', ',', '.', ':', ';', '|', 'i' -> 2;
            case '`', 'l' -> 3;
            case ' ', '"', '(', ')', '*', 'I', '[', ']', 't', '{', '}' -> 4;
            case '<', '>', 'f', 'k' -> 5;
            case '@', '~' -> 7;
            default -> DEFAULT_ADVANCE;
        };
    }
}
