package ru.rulhot.rVisualBoards.hologram;

import org.jetbrains.annotations.NotNull;
import ru.rulhot.rVisualBoards.model.BoardLayout;

import java.util.ArrayList;
import java.util.List;

record BoardGeometry(
        double width,
        double line,
        int rows,
        int buttonCount,
        int arrowCount,
        int tabCount,
        double buttonWidth,
        @NotNull List<Box> boxes,
        double standingBottom,
        double tableBottom,
        double rowsBottom,
        double tableTop,
        double headerBottom,
        double tabsBottom,
        double titleBottom,
        double height
) {

    static final double PIXEL = 0.025D;
    static final double LINE_PIXELS = 10D;
    static final double LINE = PIXEL * LINE_PIXELS;
    static final int NO_BUTTON = -1;
    static final int NO_ROW = 0;

    private static final double MIN_BUTTON_WIDTH = 0.05D;
    private static final int ARROW_COUNT = 2;
    private static final int MIN_TABS = 2;

    BoardGeometry {
        boxes = List.copyOf(boxes);
    }

    static @NotNull BoardGeometry of(@NotNull BoardLayout layout, int topCount, int periodCount) {
        BoardLayout.Table table = layout.table();
        BoardLayout.Buttons buttons = layout.buttons();
        BoardLayout.Tabs tabs = layout.tabs();
        BoardLayout.Spacing spacing = layout.spacing();
        double line = LINE * table.textScale();
        int count = buttons.enabled() && topCount > 1 ? Math.min(topCount, buttons.visible()) : 0;
        int arrowCount = count > 0 && buttons.arrows() ? ARROW_COUNT : 0;
        int tabCount = tabs.enabled() && periodCount >= MIN_TABS ? periodCount : 0;
        double arrowsWidth = arrowCount * (buttons.arrowWidth() + spacing.buttonsGap());
        double buttonWidth = count == 0 ? 0D : Math.max(MIN_BUTTON_WIDTH,
                (table.width() - arrowsWidth - spacing.buttonsGap() * (count - 1)) / count);
        double buttonsBottom = spacing.padding();
        double standingBottom = count == 0
                ? buttonsBottom
                : buttonsBottom + buttons.height() + spacing.buttonsMargin();
        double tableBottom = table.personalRow() ? standingBottom + line + spacing.buttonsMargin() : standingBottom;
        double rowsBottom = tableBottom + spacing.gap();
        double tableTop = rowsBottom + line * table.rows() + spacing.gap();
        double headerBottom = tableTop + spacing.gap();
        double headerTop = headerBottom + spacing.headerHeight();
        double tabsBottom = headerTop + spacing.gap();
        double titleBottom = (tabCount > 0 ? tabsBottom + tabs.height() + spacing.gap() : headerTop + spacing.gap())
                + spacing.subtitleHeight();
        double height = titleBottom + spacing.titleHeight() + spacing.padding();

        List<Box> boxes = new ArrayList<>();
        double used = buttonWidth * count + spacing.buttonsGap() * (count - 1);
        for (int index = 0; index < count; index++) {
            double x = -used / 2D + buttonWidth / 2D + index * (buttonWidth + spacing.buttonsGap());
            boxes.add(new Box(x, buttonsBottom, buttonWidth, buttons.height()));
        }
        if (arrowCount > 0) {
            double arrowX = table.width() / 2D - buttons.arrowWidth() / 2D;
            boxes.add(new Box(-arrowX, buttonsBottom, buttons.arrowWidth(), buttons.height()));
            boxes.add(new Box(arrowX, buttonsBottom, buttons.arrowWidth(), buttons.height()));
        }
        if (tabCount > 0) {
            double tabWidth = (table.width() - spacing.buttonsGap() * (tabCount - 1)) / tabCount;
            for (int index = 0; index < tabCount; index++) {
                double x = -table.width() / 2D + tabWidth / 2D + index * (tabWidth + spacing.buttonsGap());
                boxes.add(new Box(x, tabsBottom, tabWidth, tabs.height()));
            }
        }
        return new BoardGeometry(table.width(), line, table.rows(), count, arrowCount, tabCount, buttonWidth, boxes,
                standingBottom, tableBottom, rowsBottom, tableTop, headerBottom, tabsBottom, titleBottom, height);
    }

    static double textBottom(double boxBottom, double boxHeight, double scale) {
        return boxBottom + (boxHeight - LINE * scale) / 2D;
    }

    static int windowStart(int start, int selected, int visible) {
        if (selected < start) {
            return selected;
        }
        return selected >= start + visible ? selected - visible + 1 : start;
    }

    double rowBottom(int slot) {
        return rowsBottom + (rows - slot) * line;
    }

    int rowAt(double x, double y) {
        if (Math.abs(x) > width / 2D || y < rowsBottom) {
            return NO_ROW;
        }
        int slot = rows - (int) ((y - rowsBottom) / line);
        return slot < 1 ? NO_ROW : slot;
    }

    boolean isOnRows(double x, double y) {
        return Math.abs(x) <= width / 2D && y >= tableBottom && y <= tableTop;
    }

    int boxCount() {
        return boxes.size();
    }

    @NotNull Box box(int index) {
        return boxes.get(index);
    }

    int buttonAt(double x, double y) {
        for (int index = 0; index < boxes.size(); index++) {
            if (boxes.get(index).contains(x, y)) {
                return index;
            }
        }
        return NO_BUTTON;
    }

    record Box(double x, double bottom, double width, double height) {

        boolean contains(double pointX, double pointY) {
            return Math.abs(pointX - x) <= width / 2D && pointY >= bottom && pointY <= bottom + height;
        }
    }
}
