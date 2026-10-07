package ru.rulhot.rVisualBoards.hologram;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rVisualBoards.hologram.HologramView.Button;
import ru.rulhot.rVisualBoards.hologram.HologramView.Cell;
import ru.rulhot.rVisualBoards.hologram.HologramView.Group;
import ru.rulhot.rVisualBoards.model.BoardLayout;
import ru.rulhot.rVisualBoards.model.BoardLayout.Column;
import ru.rulhot.rVisualBoards.model.LeaderboardSnapshot;
import ru.rulhot.rVisualBoards.model.LeaderboardSnapshot.Entry;
import ru.rulhot.rVisualBoards.model.LeaderboardSnapshot.Key;
import ru.rulhot.rVisualBoards.model.LeaderboardSnapshot.Standing;
import ru.rulhot.rVisualBoards.model.TopDefinition;
import ru.rulhot.rVisualBoards.model.TopDefinition.FormatKey;
import ru.rulhot.rVisualBoards.model.TopDefinition.Period;
import ru.rulhot.rVisualBoards.util.MessageUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class HologramRenderer {

    private static final @NotNull List<FormatKey> HEADER_KEYS =
            List.of(FormatKey.HEADER_PLACE, FormatKey.HEADER_NAME, FormatKey.HEADER_VALUE);
    private static final @NotNull List<FormatKey> ROW_KEYS =
            List.of(FormatKey.ROW_PLACE, FormatKey.ROW_NAME, FormatKey.ROW_VALUE);
    private static final @NotNull List<FormatKey> EMPTY_KEYS =
            List.of(FormatKey.EMPTY_PLACE, FormatKey.EMPTY_NAME, FormatKey.EMPTY_VALUE);

    private static final @NotNull String PLACE = "place";
    private static final @NotNull String PREFIX = "prefix";
    private static final @NotNull String NAME = "name";
    private static final @NotNull String VALUE = "value";
    private static final @NotNull String POSITION = "position";
    private static final @NotNull String LABEL = "label";
    private static final @NotNull String PERIOD = "period";
    private static final @NotNull Color TRANSPARENT = Color.fromARGB(0, 0, 0, 0);
    private static final long NO_VERSION = 0L;

    private final @NotNull HologramContext context;
    private final @NotNull BoardFrame frame;
    private final @NotNull List<TopDefinition> tops;
    private final @NotNull List<Column> columns;
    private final @NotNull BoardLayout.Table table;
    private final @NotNull BoardLayout.Spacing spacing;
    private final @NotNull BoardLayout.Colors colors;
    private final @NotNull BoardGeometry geometry;
    private final @NotNull Map<Key, RowCache> rowCaches = new HashMap<>();

    HologramRenderer(@NotNull HologramContext context, @NotNull BoardFrame frame, @NotNull BoardGeometry geometry,
                     @NotNull List<TopDefinition> tops) {
        this.context = context;
        this.frame = frame;
        this.geometry = geometry;
        this.tops = List.copyOf(tops);
        this.columns = context.layout().columns().ordered();
        this.table = context.layout().table();
        this.spacing = context.layout().spacing();
        this.colors = context.layout().colors();
    }

    void renderTable(@NotNull HologramView view, @NotNull TopDefinition top, @NotNull String viewerName) {
        renderTitle(view, top);
        renderHeaders(view, top);
        renderRows(view, top);
        view.resetStanding();
        renderStanding(view, top, viewerName);
    }

    void renderGroup(@NotNull HologramView view, @NotNull Group group, @NotNull TopDefinition top,
                     @NotNull String viewerName) {
        switch (group.kind()) {
            case TITLE -> renderTitle(view, top);
            case HEADERS -> renderHeaders(view, top);
            case ROW -> {
                LeaderboardSnapshot snapshot = snapshot(view, top);
                renderRow(view, top, snapshot, group.slot());
                if (group.slot() == view.rows().size()) {
                    view.markRendered(key(view, top), versionOf(snapshot));
                }
            }
            case STANDING -> {
                view.resetStanding();
                renderStanding(view, top, viewerName);
            }
            case COLUMNS -> {
            }
        }
    }

    void refreshRows(@NotNull HologramView view, @NotNull TopDefinition top) {
        if (view.isRendered(key(view, top), versionOf(snapshot(view, top)))) {
            return;
        }
        renderRows(view, top);
    }

    boolean canScroll(@NotNull HologramView view, @NotNull TopDefinition top, int delta) {
        return clampOffset(view.scrollOffset() + delta, snapshot(view, top)) != view.scrollOffset();
    }

    void scroll(@NotNull HologramView view, @NotNull TopDefinition top, int delta) {
        view.scrollOffset(view.scrollOffset() + delta);
        renderRows(view, top);
    }

    void renderStanding(@NotNull HologramView view, @NotNull TopDefinition top, @NotNull String viewerName) {
        List<Cell> cells = view.standing();
        if (cells.isEmpty()) {
            return;
        }
        Standing standing = context.leaderboards().standing(view.viewerId(), top.board(), view.period());
        if (standing == null) {
            standing = Standing.UNKNOWN;
        }
        Key key = key(view, top);
        if (view.isStandingRendered(standing, key)) {
            return;
        }
        Map<String, Object> placeholders = Map.of(
                NAME, viewerName,
                VALUE, standing.value(),
                POSITION, standing.position());
        List<FormatKey> keys = List.of(
                standing.ranked() ? FormatKey.PERSONAL_PLACE : FormatKey.PERSONAL_PLACE_UNRANKED,
                FormatKey.PERSONAL_NAME,
                FormatKey.PERSONAL_VALUE);
        for (int index = 0; index < cells.size(); index++) {
            setCell(cells.get(index), columns.get(index), geometry.standingBottom(),
                    cellText(MessageUtil.parseText(top.format(keys.get(index)), placeholders)));
        }
        view.markStandingRendered(standing, key);
    }

    void renderButtons(@NotNull HologramView view, @NotNull TopDefinition selected) {
        view.reveal(Math.max(0, tops.indexOf(selected)), geometry.buttonCount());
        double labelScale = labelScale(selected);
        double tabScale = tabScale(selected);
        for (Button button : view.buttons()) {
            switch (button.action()) {
                case ButtonAction.Cycle cycle -> button.label().text(MessageUtil.parseText(selected.format(
                        cycle == ButtonAction.Cycle.NEXT ? FormatKey.ARROW_RIGHT : FormatKey.ARROW_LEFT)));
                case ButtonAction.Slot slot -> renderSlot(view, selected, button, slot, labelScale);
                case ButtonAction.PeriodTab tab -> renderTab(view, selected, button, tab, tabScale);
            }
        }
    }

    private void renderSlot(@NotNull HologramView view, @NotNull TopDefinition selected, @NotNull Button button,
                            @NotNull ButtonAction.Slot slot, double labelScale) {
        TopDefinition top = tops.get(view.buttonOffset() + slot.index());
        boolean active = top.id().equals(selected.id());
        button.label().text(label(top, selected, active));
        fitLabel(button, labelScale);
        Color selectedColor = selected.buttonColor() == null ? colors.buttonSelected() : selected.buttonColor();
        recolor(button, active ? selectedColor : colors.button());
    }

    private void renderTab(@NotNull HologramView view, @NotNull TopDefinition selected, @NotNull Button button,
                           @NotNull ButtonAction.PeriodTab tab, double tabScale) {
        List<Period> periods = selected.periods();
        if (tab.index() >= periods.size()) {
            button.label().text(Component.empty());
            recolor(button, TRANSPARENT);
            return;
        }
        Period period = periods.get(tab.index());
        boolean active = period == view.period();
        button.label().text(tabLabel(selected, period, active));
        fitLabel(button, tabScale);
        recolor(button, active ? colors.tabSelected() : colors.tab());
    }

    private static void recolor(@NotNull Button button, @NotNull Color color) {
        HologramPart panel = button.panel();
        if (panel != null) {
            panel.recolor(color);
        }
    }

    private double labelScale(@NotNull TopDefinition selected) {
        double baseScale = context.layout().buttons().textScale();
        double scale = baseScale;
        for (TopDefinition top : tops) {
            Component label = label(top, selected, top.id().equals(selected.id()));
            scale = Math.min(scale, fittedScale(context.fontMetrics().width(label), geometry.buttonWidth(), baseScale));
        }
        return scale;
    }

    private double tabScale(@NotNull TopDefinition selected) {
        double baseScale = context.layout().tabs().textScale();
        if (geometry.tabCount() == 0) {
            return baseScale;
        }
        double width = geometry.box(geometry.buttonCount() + geometry.arrowCount()).width();
        double scale = baseScale;
        for (Period period : selected.periods()) {
            Component label = tabLabel(selected, period, true);
            scale = Math.min(scale, fittedScale(context.fontMetrics().width(label), width, baseScale));
        }
        return scale;
    }

    private @NotNull Component label(@NotNull TopDefinition top, @NotNull TopDefinition selected, boolean active) {
        return MessageUtil.parseText(selected.format(active ? FormatKey.BUTTON_SELECTED : FormatKey.BUTTON),
                Map.of(LABEL, top.label()));
    }

    private @NotNull Component tabLabel(@NotNull TopDefinition top, @NotNull Period period, boolean active) {
        return MessageUtil.parseText(top.format(active ? FormatKey.TAB_SELECTED : FormatKey.TAB),
                Map.of(PERIOD, top.periodName(period)));
    }

    private void fitLabel(@NotNull Button button, double scale) {
        HologramPart label = button.label();
        if (!label.rescale((float) (scale * frame.scale()))) {
            return;
        }
        label.apply(0);
        BoardGeometry.Box box = button.box();
        label.moveTo(frame.point(box.x(), BoardGeometry.textBottom(box.bottom(), box.height(), scale),
                textDepth()));
    }

    private void renderTitle(@NotNull HologramView view, @NotNull TopDefinition top) {
        Map<String, String> placeholders = Map.of(PERIOD, top.periodName(view.period()));
        view.title().text(MessageUtil.parseText(top.title(), placeholders));
        HologramPart subtitle = view.subtitle();
        if (subtitle != null) {
            subtitle.text(MessageUtil.parseText(top.format(FormatKey.SUBTITLE), placeholders));
        }
    }

    private void renderHeaders(@NotNull HologramView view, @NotNull TopDefinition top) {
        List<HologramPart> headers = view.headers();
        for (int index = 0; index < headers.size(); index++) {
            headers.get(index).text(MessageUtil.parseText(top.format(HEADER_KEYS.get(index))));
        }
    }

    private void renderRows(@NotNull HologramView view, @NotNull TopDefinition top) {
        LeaderboardSnapshot snapshot = snapshot(view, top);
        view.scrollOffset(clampOffset(view.scrollOffset(), snapshot));
        for (int slot = 1; slot <= view.rows().size(); slot++) {
            renderRow(view, top, snapshot, slot);
        }
        view.markRendered(key(view, top), versionOf(snapshot));
    }

    private void renderRow(@NotNull HologramView view, @NotNull TopDefinition top,
                           @Nullable LeaderboardSnapshot snapshot, int slot) {
        List<Cell> row = view.rows().get(slot - 1);
        int place = view.scrollOffset() + slot;
        List<CellText> texts = rowTexts(top, view.period(), snapshot, place);
        double bottom = geometry.rowBottom(slot);
        for (int index = 0; index < row.size(); index++) {
            setCell(row.get(index), columns.get(index), bottom, texts.get(index));
        }
    }

    private @NotNull List<CellText> rowTexts(@NotNull TopDefinition top, @NotNull Period period,
                                             @Nullable LeaderboardSnapshot snapshot, int place) {
        Key key = new Key(top.id(), period);
        long version = versionOf(snapshot);
        RowCache cache = rowCaches.get(key);
        if (cache == null || cache.version() != version) {
            cache = new RowCache(version, new HashMap<>());
            rowCaches.put(key, cache);
        }
        return cache.rows().computeIfAbsent(place, ignored -> buildRow(top, snapshot, place));
    }

    private @NotNull List<CellText> buildRow(@NotNull TopDefinition top, @Nullable LeaderboardSnapshot snapshot,
                                             int place) {
        Entry entry = snapshot == null ? null : snapshot.entry(place);
        Map<String, Object> placeholders = Map.of(
                PLACE, place,
                PREFIX, entry == null ? Component.empty() : MessageUtil.parseExternal(entry.prefix()),
                NAME, entry == null ? "" : entry.name(),
                VALUE, entry == null ? "" : entry.value());
        List<FormatKey> keys = entry == null ? EMPTY_KEYS : ROW_KEYS;
        List<CellText> texts = new ArrayList<>();
        for (FormatKey key : keys) {
            texts.add(cellText(MessageUtil.parseText(top.format(key), placeholders)));
        }
        return List.copyOf(texts);
    }

    private @NotNull CellText cellText(@NotNull Component text) {
        return new CellText(text, context.fontMetrics().width(text));
    }

    private void setCell(@NotNull Cell cell, @NotNull Column column, double bottom, @NotNull CellText cellText) {
        if (!cell.show(cellText.text())) {
            return;
        }
        HologramPart part = cell.part();
        part.text(cellText.text());
        double baseScale = table.textScale();
        double scale = fittedScale(cellText.pixels(), column.width(), baseScale);
        if (part.rescale((float) (scale * frame.scale()))) {
            part.apply(0);
        }
        double half = cellText.pixels() * BoardGeometry.PIXEL * scale / 2D;
        double x = switch (column.align()) {
            case LEFT -> column.from() + spacing.cellPadding() + half;
            case RIGHT -> column.to() - spacing.cellPadding() - half;
            default -> column.center();
        };
        double y = bottom + (BoardGeometry.LINE * baseScale - BoardGeometry.LINE * scale) / 2D;
        if (cell.moveTo(x, y)) {
            part.moveTo(frame.point(x, y, textDepth()));
        }
    }

    private double fittedScale(int pixels, double width, double baseScale) {
        double available = Math.max(BoardGeometry.PIXEL, width - spacing.cellPadding() * 2D);
        double natural = pixels * BoardGeometry.PIXEL * baseScale;
        return natural > available ? baseScale * available / natural : baseScale;
    }

    private double textDepth() {
        return spacing.depthStep() * 2D;
    }

    private int clampOffset(int offset, @Nullable LeaderboardSnapshot snapshot) {
        int filled = snapshot == null ? 0 : snapshot.filledPlaces();
        return Math.clamp(offset, 0, Math.max(0, filled - geometry.rows()));
    }

    private @Nullable LeaderboardSnapshot snapshot(@NotNull HologramView view, @NotNull TopDefinition top) {
        return context.leaderboards().snapshot(top.board(), view.period());
    }

    private static @NotNull Key key(@NotNull HologramView view, @NotNull TopDefinition top) {
        return new Key(top.id(), view.period());
    }

    private static long versionOf(@Nullable LeaderboardSnapshot snapshot) {
        return snapshot == null ? NO_VERSION : snapshot.version();
    }

    private record CellText(@NotNull Component text, int pixels) {
    }

    private record RowCache(long version, @NotNull Map<Integer, List<CellText>> rows) {
    }
}
