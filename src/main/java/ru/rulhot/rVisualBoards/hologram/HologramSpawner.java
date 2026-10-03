package ru.rulhot.rVisualBoards.hologram;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
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
    private static final @NotNull Display.Brightness FULL_BRIGHT = new Display.Brightness(MAX_LIGHT, MAX_LIGHT);

    private final @NotNull HologramContext context;
    private final @NotNull String boardId;
    private final @NotNull BoardFrame frame;
    private final @NotNull BoardGeometry geometry;
    private final @NotNull List<Column> columns;
    private final @NotNull BoardLayout.Table table;
    private final @NotNull BoardLayout.Buttons buttons;
    private final @NotNull BoardLayout.Tabs tabs;
    private final @NotNull BoardLayout.Colors colors;
    private final @NotNull BoardLayout.Spacing spacing;

    HologramSpawner(@NotNull HologramContext context, @NotNull String boardId, @NotNull BoardFrame frame,
                    @NotNull BoardGeometry geometry) {
        this.context = context;
        this.boardId = boardId;
        this.frame = frame;
        this.geometry = geometry;
        this.columns = context.layout().columns().ordered();
        this.table = context.layout().table();
        this.buttons = context.layout().buttons();
        this.tabs = context.layout().tabs();
        this.colors = context.layout().colors();
        this.spacing = context.layout().spacing();
    }

    @NotNull HologramView spawnView(@NotNull UUID viewerId, @NotNull String topId, @NotNull Period period) {
        double textDepth = textDepth();
        List<Group> groups = new ArrayList<>();

        List<HologramPart> titleParts = new ArrayList<>();
        addPanel(titleParts, 0D, geometry.titleBottom(), 0D, table.width(), spacing.titleHeight(), colors.title());
        HologramPart title = text(0D,
                BoardGeometry.textBottom(geometry.titleBottom(), spacing.titleHeight(), table.titleScale()),
                textDepth, table.titleScale(), TextDisplay.TextAlignment.CENTER);
        titleParts.add(title);
        HologramPart subtitle = null;
        if (spacing.subtitleHeight() > 0D) {
            subtitle = text(0D, BoardGeometry.textBottom(geometry.titleBottom() - spacing.subtitleHeight(),
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
            addPanel(headerParts, column.center(), geometry.headerBottom(), 0D, column.width(),
                    spacing.headerHeight(), colors.header());
            addPanel(columnParts, column.center(), geometry.tableBottom(), 0D, column.width(),
                    geometry.tableTop() - geometry.tableBottom(), colors.column());
            HologramPart header = text(column.center(), headerTextBottom, textDepth, table.textScale(),
                    TextDisplay.TextAlignment.CENTER);
            headers.add(header);
            headerParts.add(header);
        }
        groups.add(new Group(GroupKind.HEADERS, 0, List.copyOf(headerParts)));
        groups.add(new Group(GroupKind.COLUMNS, 0, List.copyOf(columnParts)));

        List<List<Cell>> rows = new ArrayList<>();
        for (int slot = 1; slot <= geometry.rows(); slot++) {
            List<Cell> row = cells(geometry.rowBottom(slot));
            rows.add(row);
            groups.add(new Group(GroupKind.ROW, slot, parts(row)));
        }

        List<Cell> standing = List.of();
        if (table.personalRow()) {
            standing = cells(geometry.standingBottom());
            List<HologramPart> standingParts = new ArrayList<>();
            for (Column column : columns) {
                addPanel(standingParts, column.center(), geometry.standingBottom() - spacing.gap(), 0D,
                        column.width(), geometry.line() + spacing.gap() * 2D, colors.personalRow());
            }
            standingParts.addAll(parts(standing));
            groups.add(new Group(GroupKind.STANDING, 0, List.copyOf(standingParts)));
        }

        return new HologramView(viewerId, topId, period, title, subtitle, headers, rows, standing, spawnButtons(), groups);
    }

    @NotNull List<Interaction> spawnHitboxes() {
        List<Interaction> hitboxes = new ArrayList<>();
        for (int index = 0; index < geometry.boxCount(); index++) {
            hitboxes.add(hitbox(geometry.box(index), actionAt(index)));
        }
        return hitboxes;
    }

    private @NotNull List<Button> spawnButtons() {
        List<Button> result = new ArrayList<>();
        for (int index = 0; index < geometry.boxCount(); index++) {
            result.add(button(actionAt(index), geometry.box(index)));
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

    private @NotNull Button button(@NotNull ButtonAction action, @NotNull BoardGeometry.Box box) {
        Color fill = switch (action) {
            case ButtonAction.Slot slot -> colors.button();
            case ButtonAction.Cycle cycle -> colors.arrow();
            case ButtonAction.PeriodTab tab -> colors.tab();
        };
        double scale = action instanceof ButtonAction.PeriodTab ? tabs.textScale() : buttons.textScale();
        HologramPart panel = panel(box.x(), box.bottom(), spacing.depthStep(), box.width(), box.height(), fill);
        double labelBottom = BoardGeometry.textBottom(box.bottom(), box.height(), scale);
        HologramPart label = text(box.x(), labelBottom, textDepth(), scale, TextDisplay.TextAlignment.CENTER);
        return new Button(action, box, panel, label);
    }

    private @NotNull List<Cell> cells(double bottom) {
        List<Cell> row = new ArrayList<>();
        for (Column column : columns) {
            row.add(new Cell(text(column.center(), bottom, textDepth(), table.textScale(), column.align())));
        }
        return List.copyOf(row);
    }

    private @NotNull List<HologramPart> parts(@NotNull List<Cell> cells) {
        return cells.stream().map(Cell::part).toList();
    }

    private double textDepth() {
        return spacing.depthStep() * 2D;
    }

    private @NotNull HologramPart text(double x, double y, double depth, double scale,
                                       @NotNull TextDisplay.TextAlignment alignment) {
        TextDisplay display = frame.world().spawn(frame.point(x, y, depth), TextDisplay.class, spawned -> {
            prepareText(spawned);
            spawned.setAlignment(alignment);
            spawned.setShadowed(table.shadow());
            spawned.setBackgroundColor(TRANSPARENT);
        });
        float size = (float) (scale * frame.scale());
        HologramPart part = new HologramPart(display, size, size, size, BoardGeometry.LINE * size, null);
        part.apply(0);
        return part;
    }

    private void addPanel(@NotNull List<HologramPart> sink, double x, double y, double depth,
                          double width, double height, @NotNull Color color) {
        if (color.getAlpha() != 0) {
            sink.add(panel(x, y, depth, width, height, color));
        }
    }

    private @NotNull HologramPart panel(double x, double y, double depth, double width, double height,
                                        @NotNull Color color) {
        int spaces = Math.max(1, (int) Math.round((width / BoardGeometry.PIXEL - 1D) / SPACE_PIXELS));
        int lines = Math.max(1, (int) Math.round((height / BoardGeometry.PIXEL - 1D) / BoardGeometry.LINE_PIXELS));
        double scale = frame.scale();
        float scaleX = (float) (width * scale / ((spaces * SPACE_PIXELS + 1D) * BoardGeometry.PIXEL));
        float scaleY = (float) (height * scale / ((lines * BoardGeometry.LINE_PIXELS + 1D) * BoardGeometry.PIXEL));
        Component filler = Component.text(String.join(LINE_BREAK, Collections.nCopies(lines, SPACE.repeat(spaces))));
        TextDisplay display = frame.world().spawn(frame.point(x, y, depth), TextDisplay.class, spawned -> {
            prepareText(spawned);
            spawned.text(filler);
            spawned.setShadowed(false);
        });
        HologramPart part = new HologramPart(display, scaleX, scaleY, 1F, height * scale, color);
        part.apply(0);
        return part;
    }

    private @NotNull Interaction hitbox(@NotNull BoardGeometry.Box box, @NotNull ButtonAction action) {
        float size = (float) (box.width() * frame.scale());
        float height = (float) (box.height() * frame.scale());
        return frame.world().spawn(frame.point(box.x(), box.bottom(), 0D), Interaction.class, interaction -> {
            interaction.setPersistent(false);
            interaction.setInteractionWidth(size);
            interaction.setInteractionHeight(height);
            interaction.setResponsive(true);
            PersistentDataContainer container = interaction.getPersistentDataContainer();
            container.set(context.keys().board(), PersistentDataType.STRING, boardId);
            container.set(context.keys().action(), PersistentDataType.STRING, action.serialize());
        });
    }

    private void prepareText(@NotNull TextDisplay display) {
        display.setLineWidth(LINE_WIDTH);
        display.setDefaultBackground(false);
        display.setSeeThrough(false);
        display.setPersistent(false);
        display.setVisibleByDefault(false);
        display.setBillboard(Display.Billboard.FIXED);
        display.setViewRange((float) (context.settings().viewRadius() / VANILLA_VIEW_DISTANCE));
        if (table.fullBright()) {
            display.setBrightness(FULL_BRIGHT);
        }
        display.getPersistentDataContainer().set(context.keys().board(), PersistentDataType.STRING, boardId);
    }
}
