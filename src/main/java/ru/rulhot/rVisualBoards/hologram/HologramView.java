package ru.rulhot.rVisualBoards.hologram;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rVisualBoards.model.LeaderboardSnapshot.Key;
import ru.rulhot.rVisualBoards.model.LeaderboardSnapshot.Standing;
import ru.rulhot.rVisualBoards.model.TopDefinition.Period;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

final class HologramView {

    private static final long MILLIS_PER_TICK = 50L;
    private static final long BUSY_MARGIN_TICKS = 2L;

    private final @NotNull UUID viewerId;
    private final @NotNull HologramPart title;
    private final @Nullable HologramPart subtitle;
    private final @NotNull List<HologramPart> headers;
    private final @NotNull List<List<Cell>> rows;
    private final @NotNull List<Cell> standing;
    private final @NotNull List<Button> buttons;
    private final @NotNull List<Group> groups;
    private final @NotNull List<Entity> entities;
    private final @NotNull AtomicInteger generation = new AtomicInteger();

    private volatile @NotNull String topId;
    private volatile @NotNull Period period;
    private volatile long busyUntilMillis;
    private volatile boolean detached;
    private @Nullable Key renderedKey;
    private long renderedVersion = -1L;
    private int renderedOffset;
    private volatile int scrollOffset;
    private volatile int buttonOffset;
    private @Nullable Standing renderedStanding;
    private @Nullable Key renderedStandingKey;
    private int hoveredButton = BoardGeometry.NO_BUTTON;
    private int hoveredRow = BoardGeometry.NO_ROW;

    HologramView(@NotNull UUID viewerId, @NotNull String topId, @NotNull Period period, @NotNull HologramPart title,
                 @Nullable HologramPart subtitle, @NotNull List<HologramPart> headers, @NotNull List<List<Cell>> rows, @NotNull List<Cell> standing,
                 @NotNull List<Button> buttons, @NotNull List<Group> groups) {
        this.viewerId = viewerId;
        this.topId = topId;
        this.period = period;
        this.title = title;
        this.subtitle = subtitle;
        this.headers = List.copyOf(headers);
        this.rows = List.copyOf(rows);
        this.standing = List.copyOf(standing);
        this.buttons = List.copyOf(buttons);
        this.groups = List.copyOf(groups);
        List<Entity> all = new ArrayList<>();
        for (Group group : groups) {
            for (HologramPart part : group.parts()) {
                all.add(part.display());
            }
        }
        for (Button button : buttons) {
            for (HologramPart part : button.parts()) {
                all.add(part.display());
            }
        }
        this.entities = List.copyOf(all);
    }

    @NotNull UUID viewerId() {
        return viewerId;
    }

    @NotNull String topId() {
        return topId;
    }

    @NotNull Period period() {
        return period;
    }

    void select(@NotNull String topId, @NotNull Period period) {
        this.topId = topId;
        this.period = period;
    }

    @NotNull HologramPart title() {
        return title;
    }

    @Nullable HologramPart subtitle() {
        return subtitle;
    }

    @NotNull List<HologramPart> headers() {
        return headers;
    }

    @NotNull List<List<Cell>> rows() {
        return rows;
    }

    @NotNull List<Cell> standing() {
        return standing;
    }

    @NotNull List<Button> buttons() {
        return buttons;
    }

    @NotNull List<Group> groups() {
        return groups;
    }

    @NotNull List<Entity> entities() {
        return entities;
    }

    boolean intact() {
        for (Entity entity : entities) {
            if (!entity.isValid()) {
                return false;
            }
        }
        return true;
    }

    int nextGeneration() {
        return generation.incrementAndGet();
    }

    boolean isCurrent(int expected) {
        return !detached && generation.get() == expected;
    }

    void detach() {
        detached = true;
        generation.incrementAndGet();
    }

    void markBusy(long ticks) {
        busyUntilMillis = System.currentTimeMillis() + (ticks + BUSY_MARGIN_TICKS) * MILLIS_PER_TICK;
    }

    boolean isAnimating() {
        return System.currentTimeMillis() < busyUntilMillis;
    }

    boolean isRendered(@NotNull Key key, long version) {
        return key.equals(renderedKey) && version == renderedVersion && scrollOffset == renderedOffset;
    }

    void markRendered(@NotNull Key key, long version) {
        renderedKey = key;
        renderedVersion = version;
        renderedOffset = scrollOffset;
    }

    int scrollOffset() {
        return scrollOffset;
    }

    void scrollOffset(int offset) {
        scrollOffset = offset;
    }

    boolean isStandingRendered(@NotNull Standing standing, @NotNull Key key) {
        return standing.equals(renderedStanding) && key.equals(renderedStandingKey);
    }

    void markStandingRendered(@NotNull Standing standing, @NotNull Key key) {
        renderedStanding = standing;
        renderedStandingKey = key;
    }

    void resetStanding() {
        renderedStanding = null;
        renderedStandingKey = null;
    }

    int buttonOffset() {
        return buttonOffset;
    }

    void reveal(int topIndex, int visible) {
        buttonOffset = BoardGeometry.windowStart(buttonOffset, topIndex, visible);
    }

    int hoveredButton() {
        return hoveredButton;
    }

    void hoveredButton(int index) {
        hoveredButton = index;
    }

    int hoveredRow() {
        return hoveredRow;
    }

    void hoveredRow(int slot) {
        hoveredRow = slot;
    }

    enum GroupKind {
        TITLE,
        HEADERS,
        COLUMNS,
        ROW,
        STANDING
    }

    record Group(@NotNull GroupKind kind, int slot, @NotNull List<HologramPart> parts) {
    }

    record Button(@NotNull ButtonAction action, @NotNull BoardGeometry.Box box, @Nullable HologramPart panel,
                  @NotNull HologramPart label) {

        @NotNull List<HologramPart> parts() {
            return panel == null ? List.of(label) : List.of(panel, label);
        }
    }

    static final class Cell {

        private final @NotNull HologramPart part;
        private @Nullable Component shown;
        private double x = Double.NaN;
        private double y = Double.NaN;

        Cell(@NotNull HologramPart part) {
            this.part = part;
        }

        @NotNull HologramPart part() {
            return part;
        }

        boolean show(@NotNull Component text) {
            if (text == shown) {
                return false;
            }
            shown = text;
            return true;
        }

        boolean moveTo(double x, double y) {
            if (x == this.x && y == this.y) {
                return false;
            }
            this.x = x;
            this.y = y;
            return true;
        }
    }
}
