package ru.rulhot.rVisualBoards.hologram;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed interface ButtonAction permits ButtonAction.Slot, ButtonAction.PeriodTab, ButtonAction.Cycle {

    @NotNull String serialize();

    static @Nullable ButtonAction parse(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        if (raw.startsWith(Slot.PREFIX)) {
            Integer index = parseIndex(raw.substring(Slot.PREFIX.length()));
            return index == null ? null : new Slot(index);
        }
        if (raw.startsWith(PeriodTab.PREFIX)) {
            Integer index = parseIndex(raw.substring(PeriodTab.PREFIX.length()));
            return index == null ? null : new PeriodTab(index);
        }
        for (Cycle cycle : Cycle.values()) {
            if (cycle.serialize().equals(raw)) {
                return cycle;
            }
        }
        return null;
    }

    private static @Nullable Integer parseIndex(@NotNull String raw) {
        try {
            int index = Integer.parseInt(raw);
            return index < 0 ? null : index;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    record Slot(int index) implements ButtonAction {

        private static final @NotNull String PREFIX = "slot:";

        @Override
        public @NotNull String serialize() {
            return PREFIX + index;
        }
    }

    record PeriodTab(int index) implements ButtonAction {

        private static final @NotNull String PREFIX = "period:";

        @Override
        public @NotNull String serialize() {
            return PREFIX + index;
        }
    }

    enum Cycle implements ButtonAction {
        PREVIOUS("previous", -1),
        NEXT("next", 1);

        private final @NotNull String key;
        private final int step;

        Cycle(@NotNull String key, int step) {
            this.key = key;
            this.step = step;
        }

        public int step() {
            return step;
        }

        @Override
        public @NotNull String serialize() {
            return key;
        }
    }
}
