package ru.rulhot.rVisualBoards.hologram;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.player.User;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.entity.TextDisplay;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rVisualBoards.hologram.HologramView.Button;
import ru.rulhot.rVisualBoards.hologram.HologramView.Cell;
import ru.rulhot.rVisualBoards.hologram.HologramView.Group;
import ru.rulhot.rVisualBoards.hologram.HologramView.GroupKind;
import ru.rulhot.rVisualBoards.model.BoardLayout;
import ru.rulhot.rVisualBoards.model.BoardLayout.Column;
import ru.rulhot.rVisualBoards.model.TopDefinition.Period;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

final class HologramSpawner {

    private static final int LINE_WIDTH = 100_000;
    private static final double SPACE_PIXELS = 4D;
    private static final double VANILLA_VIEW_DISTANCE = 64D;
    private static final int MAX_LIGHT = 15;
    private static final @NotNull String SPACE = " ";
    private static final @NotNull String LINE_BREAK = "\n";
    private static final @NotNull Color TRANSPARENT = Color.fromARGB(0, 0, 0, 0);

    private final @NotNull HologramContext context;
    private final @NotNull String boardId;
    private final @NotNull BoardGeometry geometry;
    private final @NotNull List<Column> columns;
    private final @NotNull BoardLayout.Table table;
    private final @NotNull BoardLayout.Buttons buttons;
    private final @NotNull BoardLayout.Tabs tabs;
    private final @NotNull BoardLayout.Colors colors;
    private final @NotNull BoardLayout.Spacing spacing;

    HologramSpawner(@NotNull HologramContext context, @NotNull String boardId, @NotNull BoardGeometry geometry) {
        this.context = context;
        this.boardId = boardId;
        this.geometry = geometry;
        this.columns = context.layout().columns().ordered();
        this.table = context.layout().table();
        this.buttons = context.layout().buttons();
        this.tabs = context.layout().tabs();
        this.colors = context.layout().colors();
        this.spacing = context.layout().spacing();
    }

    @NotNull HologramView spawnView(@NotNull User viewer, @NotNull BoardFrame frame, @NotNull UUID viewerId, @NotNull String topId,
                                    @NotNull Period period) {
        double textDepth = textDepth();
        List<Group> groups = new ArrayList<>();

        List<HologramPart> titleParts = new ArrayList<>();
        addPanel(viewer, frame, titleParts, 0D, geometry.titleBottom(), 0D, table.width(), spacing.titleHeight(), colors.title());
        HologramPart title = text(viewer, frame, 0D,
                BoardGeometry.textBottom(geometry.titleBottom(), spacing.titleHeight(), table.titleScale()),
                textDepth, table.titleScale(), TextDisplay.TextAlignment.CENTER);
        titleParts.add(title);
        HologramPart subtitle = null;
        if (spacing.subtitleHeight() > 0D) {
            subtitle = text(viewer, frame, 0D, BoardGeometry.textBottom(geometry.titleBottom() - spacing.subtitleHeight(),
                    spacing.subtitleHeight(), table.textScale()), textDepth, table.textScale(),
                    TextDisplay.TextAlignment.CENTER);
            titleParts.add(subtitle);
        }
        groups.add(new Group(GroupKind.TITLE, 0, List.copyOf(titleParts)));

        double headerTextBottom = BoardGeometry.textBottom(geometry.headerBottom(), spacing.headerHeight(),
                table.textScale());
        List<HologramPart> headers = new ArrayList<>();
        List<HologramPart> headerParts = new ArrayList<>();
        List<HologramPart> columnParts = new ArrayList<>();
        for (Column column : columns) {
            addPanel(viewer, frame, headerParts, column.center(), geometry.headerBottom(), 0D, column.width(),
                    spacing.headerHeight(), colors.header());
            addPanel(viewer, frame, columnParts, column.center(), geometry.tableBottom(), 0D, column.width(),
                    geometry.tableTop() - geometry.tableBottom(), colors.column());
            HologramPart header = text(viewer, frame, column.center(), headerTextBottom, textDepth, table.textScale(),
                    TextDisplay.TextAlignment.CENTER);
            headers.add(header);
            headerParts.add(header);
        }
        groups.add(new Group(GroupKind.HEADERS, 0, List.copyOf(headerParts)));
        groups.add(new Group(GroupKind.COLUMNS, 0, List.copyOf(columnParts)));

        List<List<Cell>> rows = new ArrayList<>();
        for (int slot = 1; slot <= geometry.rows(); slot++) {
            List<Cell> row = cells(viewer, frame, geometry.rowBottom(slot));
            rows.add(row);
            groups.add(new Group(GroupKind.ROW, slot, parts(row)));
        }

        List<Cell> standing = List.of();
        if (table.personalRow()) {
            standing = cells(viewer, frame, geometry.standingBottom());
            List<HologramPart> standingParts = new ArrayList<>();
            for (Column column : columns) {
                addPanel(viewer, frame, standingParts, column.center(), geometry.standingBottom() - spacing.gap(), 0D,
                        column.width(), geometry.line() + spacing.gap() * 2D, colors.personalRow());
            }
            standingParts.addAll(parts(standing));
            groups.add(new Group(GroupKind.STANDING, 0, List.copyOf(standingParts)));
        }

        return new HologramView(viewer, frame, viewerId, topId, period, title, subtitle, headers, rows, standing,
                spawnButtons(viewer, frame), groups, spawnHitboxes(viewer, frame, viewerId), context.hitboxes());
    }

    private @NotNull List<PacketHitbox> spawnHitboxes(@NotNull User viewer, @NotNull BoardFrame frame,
                                                      @NotNull UUID viewerId) {
        List<PacketHitbox> hitboxes = new ArrayList<>();
        for (int index = 0; index < geometry.boxCount(); index++) {
            BoardGeometry.Box box = geometry.box(index);
            ButtonAction action = actionAt(index);
            PacketHitbox hitbox = new PacketHitbox(viewer, frame.point(box.x(), box.bottom(), 0D),
                    (float) (box.width() * frame.scale()), (float) (box.height() * frame.scale()), box, action);
            context.hitboxes().register(hitbox.entityId(), new HitboxRegistry.Target(boardId, action, viewerId));
            hitboxes.add(hitbox);
        }
        return hitboxes;
    }

    private @NotNull List<Button> spawnButtons(@NotNull User viewer, @NotNull BoardFrame frame) {
        List<Button> result = new ArrayList<>();
        for (int index = 0; index < geometry.boxCount(); index++) {
            result.add(button(viewer, frame, actionAt(index), geometry.box(index)));
        }
        return result;
    }

    private @NotNull ButtonAction actionAt(int index) {
        if (index < geometry.buttonCount()) {
            return new ButtonAction.Slot(index);
        }
        int arrow = index - geometry.buttonCount();
        if (arrow < geometry.arrowCount()) {
            return arrow == 0 ? ButtonAction.Cycle.PREVIOUS : ButtonAction.Cycle.NEXT;
        }
        return new ButtonAction.PeriodTab(arrow - geometry.arrowCount());
    }

    private @NotNull Button button(@NotNull User viewer, @NotNull BoardFrame frame, @NotNull ButtonAction action,
                                   @NotNull BoardGeometry.Box box) {
        Color fill = switch (action) {
            case ButtonAction.Slot slot -> colors.button();
            case ButtonAction.Cycle cycle -> colors.arrow();
            case ButtonAction.PeriodTab tab -> colors.tab();
        };
        double scale = action instanceof ButtonAction.PeriodTab ? tabs.textScale() : buttons.textScale();
        HologramPart panel = panel(viewer, frame, box.x(), box.bottom(), spacing.depthStep(), box.width(), box.height(), fill);
        double labelBottom = BoardGeometry.textBottom(box.bottom(), box.height(), scale);
        HologramPart label = text(viewer, frame, box.x(), labelBottom, textDepth(), scale, TextDisplay.TextAlignment.CENTER);
        return new Button(action, box, panel, label);
    }

    private @NotNull List<Cell> cells(@NotNull User viewer, @NotNull BoardFrame frame, double bottom) {
        List<Cell> row = new ArrayList<>();
        for (Column column : columns) {
            row.add(new Cell(text(viewer, frame, column.center(), bottom, textDepth(), table.textScale(), column.align())));
        }
        return List.copyOf(row);
    }

    private @NotNull List<HologramPart> parts(@NotNull List<Cell> cells) {
        return cells.stream().map(Cell::part).toList();
    }

    private double textDepth() {
        return spacing.depthStep() * 2D;
    }

    private @NotNull HologramPart text(@NotNull User viewer, @NotNull BoardFrame frame, double x, double y, double depth, double scale,
                                       @NotNull TextDisplay.TextAlignment alignment) {
        List<EntityData<?>> state = baseState(table.shadow(), alignment);
        state.add(PacketDisplay.background(TRANSPARENT));
        PacketDisplay display = new PacketDisplay(viewer, frame.point(x, y, depth), state);
        float size = (float) (scale * frame.scale());
        HologramPart part = new HologramPart(display, size, size, size, BoardGeometry.LINE * size, null);
        part.apply(0);
        return part;
    }

    private void addPanel(@NotNull User viewer, @NotNull BoardFrame frame, @NotNull List<HologramPart> sink, double x, double y, double depth,
                          double width, double height, @NotNull Color color) {
        if (color.getAlpha() != 0) {
            sink.add(panel(viewer, frame, x, y, depth, width, height, color));
        }
    }

    private @NotNull HologramPart panel(@NotNull User viewer, @NotNull BoardFrame frame, double x, double y, double depth, double width,
                                        double height, @NotNull Color color) {
        int spaces = Math.max(1, (int) Math.round((width / BoardGeometry.PIXEL - 1D) / SPACE_PIXELS));
        int lines = Math.max(1, (int) Math.round((height / BoardGeometry.PIXEL - 1D) / BoardGeometry.LINE_PIXELS));
        double scale = frame.scale();
        float scaleX = (float) (width * scale / ((spaces * SPACE_PIXELS + 1D) * BoardGeometry.PIXEL));
        float scaleY = (float) (height * scale / ((lines * BoardGeometry.LINE_PIXELS + 1D) * BoardGeometry.PIXEL));
        Component filler = Component.text(String.join(LINE_BREAK, Collections.nCopies(lines, SPACE.repeat(spaces))));
        List<EntityData<?>> state = baseState(false, TextDisplay.TextAlignment.CENTER);
        state.add(PacketDisplay.text(filler));
        PacketDisplay display = new PacketDisplay(viewer, frame.point(x, y, depth), state);
        HologramPart part = new HologramPart(display, scaleX, scaleY, 1F, height * scale, color);
        part.apply(0);
        return part;
    }

    private @NotNull List<EntityData<?>> baseState(boolean shadowed, @NotNull TextDisplay.TextAlignment alignment) {
        List<EntityData<?>> state = new ArrayList<>();
        state.add(PacketDisplay.lineWidth(LINE_WIDTH));
        state.add(PacketDisplay.flags(shadowed, alignment));
        state.add(PacketDisplay.viewRange((float) (context.settings().viewRadius() / VANILLA_VIEW_DISTANCE)));
        if (table.fullBright()) {
            state.add(PacketDisplay.brightness(MAX_LIGHT, MAX_LIGHT));
        }
        return state;
    }
}
