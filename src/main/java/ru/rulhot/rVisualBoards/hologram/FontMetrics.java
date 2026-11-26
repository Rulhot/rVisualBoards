package ru.rulhot.rVisualBoards.hologram;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.flattener.FlattenerListener;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Map;

public final class FontMetrics {

    private static final int DEFAULT_ADVANCE = 6;

    private final @NotNull Map<Integer, Integer> overrides;

    public FontMetrics(@NotNull Map<Integer, Integer> overrides) {
        this.overrides = Map.copyOf(overrides);
    }

    public int width(@NotNull Component component) {
        WidthCounter counter = new WidthCounter();
        ComponentFlattener.basic().flatten(component, counter);
        return Math.max(counter.widest, counter.line);
    }

    private final class WidthCounter implements FlattenerListener {
        private final ArrayDeque<Boolean> bold = new ArrayDeque<>();
        private int widest;
        private int line;

        @Override
        public void pushStyle(@NotNull Style style) {
            bold.push(switch (style.decoration(TextDecoration.BOLD)) {
                case TRUE -> true;
                case FALSE -> false;
                case NOT_SET -> Boolean.TRUE.equals(bold.peek());
            });
        }

        @Override
        public void popStyle(@NotNull Style style) {
            bold.pop();
        }

        @Override
        public void component(@NotNull String text) {
            int extra = Boolean.TRUE.equals(bold.peek()) ? 1 : 0;
            text.codePoints().forEach(c -> {
                if (c == '\n') {
                    widest = Math.max(widest, line);
                    line = 0;
                } else {
                    line += advance(c) + extra;
                }
            });
        }
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
