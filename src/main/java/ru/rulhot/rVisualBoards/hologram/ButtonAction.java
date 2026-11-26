package ru.rulhot.rVisualBoards.hologram;

import org.jetbrains.annotations.NotNull;
import ru.rulhot.rVisualBoards.model.BoardLayout.Clicks;
import ru.rulhot.rVisualBoards.model.BoardLayout.Clicks.MouseKey;

public sealed interface ButtonAction
        permits ButtonAction.Slot, ButtonAction.PeriodTab, ButtonAction.Cycle, ButtonAction.Table, ButtonAction.Navigation {

    static @NotNull MouseKey keyOf(@NotNull Clicks clicks, @NotNull ButtonAction action) {
        return switch (action) {
            case Navigation nav -> switch (nav) {
                case TOP_LEFT, TOP_LABEL, TOP_RIGHT -> clicks.tops();
                case PERIOD_LEFT, PERIOD_LABEL, PERIOD_RIGHT -> clicks.periods();
                case PAGE_LEFT, PAGE_RIGHT -> clicks.arrows();
            };
            case Slot slot -> clicks.tops();
            case Cycle cycle -> clicks.arrows();
            case PeriodTab tab -> clicks.periods();
            case Table table -> clicks.table();
        };
    }

    enum Navigation implements ButtonAction {
        TOP_LEFT, TOP_LABEL, TOP_RIGHT, PERIOD_LEFT, PERIOD_LABEL, PERIOD_RIGHT, PAGE_LEFT, PAGE_RIGHT;

        boolean isLabel() {
            return this == TOP_LABEL || this == PERIOD_LABEL;
        }
    }

    record Slot(int index) implements ButtonAction {
    }

    record PeriodTab(int index) implements ButtonAction {
    }

    enum Cycle implements ButtonAction {
        PREVIOUS(-1),
        NEXT(1);

        private final int step;

        Cycle(int step) {
            this.step = step;
        }

        public int step() {
            return step;
        }
    }

    enum Table implements ButtonAction {
        NEXT_PERIOD
    }
}
