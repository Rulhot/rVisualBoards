package ru.rulhot.rVisualBoards.model;

import org.bukkit.Color;
import org.bukkit.entity.TextDisplay;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record BoardLayout(
        @NotNull Table table,
        @NotNull Buttons buttons,
        @NotNull Tabs tabs,
        @NotNull Columns columns,
        @NotNull Colors colors,
        @NotNull Spacing spacing
) {

    public static final double MIN_SCALE = 0.1D;
    public static final double MAX_SCALE = 5D;
    public static final double MIN_HEIGHT = 0.05D;
    public static final double MAX_HEIGHT = 2D;
    public static final double MIN_SPACING = 0D;
    public static final double MAX_SPACING = 2D;

    public record Table(
            double width,
            int rows,
            double textScale,
            double titleScale,
            boolean personalRow,
            boolean shadow,
            boolean fullBright
    ) {

        public static final @NotNull String WIDTH = "table.width";
        public static final @NotNull String ROWS = "table.rows";
        public static final @NotNull String TEXT_SCALE = "table.text-scale";
        public static final @NotNull String TITLE_SCALE = "table.title-scale";
        public static final @NotNull String PERSONAL_ROW = "table.personal-row";
        public static final @NotNull String SHADOW = "table.shadow";
        public static final @NotNull String FULL_BRIGHT = "table.full-bright";

        public static final double DEFAULT_WIDTH = 5.5D;
        public static final int DEFAULT_ROWS = 15;
        public static final double DEFAULT_TEXT_SCALE = 1D;
        public static final double DEFAULT_TITLE_SCALE = 1D;
        public static final boolean DEFAULT_PERSONAL_ROW = false;
        public static final boolean DEFAULT_SHADOW = true;
        public static final boolean DEFAULT_FULL_BRIGHT = true;

        public static final double MIN_WIDTH = 0.5D;
        public static final double MAX_WIDTH = 20D;
        public static final int MIN_ROWS = 1;
        public static final int MAX_ROWS = 30;
    }

    public record Buttons(
            boolean enabled,
            int visible,
            boolean arrows,
            double height,
            double textScale,
            double arrowWidth
    ) {

        public static final @NotNull String ENABLED = "buttons.enabled";
        public static final @NotNull String VISIBLE = "buttons.visible";
        public static final @NotNull String ARROWS = "buttons.arrows";
        public static final @NotNull String HEIGHT = "buttons.height";
        public static final @NotNull String TEXT_SCALE = "buttons.text-scale";
        public static final @NotNull String ARROW_WIDTH = "buttons.arrow-width";

        public static final boolean DEFAULT_ENABLED = true;
        public static final int DEFAULT_VISIBLE = 3;
        public static final boolean DEFAULT_ARROWS = true;
        public static final double DEFAULT_HEIGHT = 0.3D;
        public static final double DEFAULT_TEXT_SCALE = 1D;
        public static final double DEFAULT_ARROW_WIDTH = 0.2D;

        public static final int MIN_VISIBLE = 1;
        public static final int MAX_VISIBLE = 30;
    }

    public record Tabs(boolean enabled, double height, double textScale) {

        public static final @NotNull String ENABLED = "tabs.enabled";
        public static final @NotNull String HEIGHT = "tabs.height";
        public static final @NotNull String TEXT_SCALE = "tabs.text-scale";

        public static final boolean DEFAULT_ENABLED = true;
        public static final double DEFAULT_HEIGHT = 0.24D;
        public static final double DEFAULT_TEXT_SCALE = 0.8D;
    }

    public record Columns(@NotNull Column place, @NotNull Column name, @NotNull Column value) {

        public static final @NotNull String PLACE = "columns.place";
        public static final @NotNull String NAME = "columns.name";
        public static final @NotNull String VALUE = "columns.value";
        public static final @NotNull String FROM = "from";
        public static final @NotNull String TO = "to";
        public static final @NotNull String ALIGN = "align";

        public static final @NotNull Column DEFAULT_PLACE = new Column(-2.75D, -2.2D, TextDisplay.TextAlignment.RIGHT);
        public static final @NotNull Column DEFAULT_NAME = new Column(-2.15D, 0.6D, TextDisplay.TextAlignment.CENTER);
        public static final @NotNull Column DEFAULT_VALUE = new Column(0.65D, 2.75D, TextDisplay.TextAlignment.RIGHT);

        public @NotNull List<Column> ordered() {
            return List.of(place, name, value);
        }
    }

    public record Column(double from, double to, @NotNull TextDisplay.TextAlignment align) {

        public double center() {
            return (from + to) / 2D;
        }

        public double width() {
            return to - from;
        }
    }

    public record Colors(
            @NotNull Color title,
            @NotNull Color header,
            @NotNull Color column,
            @NotNull Color personalRow,
            @NotNull Color button,
            @NotNull Color buttonSelected,
            @NotNull Color arrow,
            @NotNull Color tab,
            @NotNull Color tabSelected
    ) {

        public static final @NotNull String TITLE = "colors.title";
        public static final @NotNull String HEADER = "colors.header";
        public static final @NotNull String COLUMN = "colors.column";
        public static final @NotNull String PERSONAL_ROW = "colors.personal-row";
        public static final @NotNull String BUTTON = "colors.button";
        public static final @NotNull String BUTTON_SELECTED = "colors.button-selected";
        public static final @NotNull String ARROW = "colors.arrow";
        public static final @NotNull String TAB = "colors.tab";
        public static final @NotNull String TAB_SELECTED = "colors.tab-selected";

        public static final @NotNull Color DEFAULT_TITLE = Color.fromARGB(0x70000000);
        public static final @NotNull Color DEFAULT_HEADER = Color.fromARGB(0x80000000);
        public static final @NotNull Color DEFAULT_COLUMN = Color.fromARGB(0x5A000000);
        public static final @NotNull Color DEFAULT_PERSONAL_ROW = Color.fromARGB(0x5A000000);
        public static final @NotNull Color DEFAULT_BUTTON = Color.fromARGB(0x66000000);
        public static final @NotNull Color DEFAULT_BUTTON_SELECTED = Color.fromARGB(0xB3402709);
        public static final @NotNull Color DEFAULT_ARROW = Color.fromARGB(0x66000000);
        public static final @NotNull Color DEFAULT_TAB = Color.fromARGB(0x40000000);
        public static final @NotNull Color DEFAULT_TAB_SELECTED = Color.fromARGB(0x99000000);
    }

    public record Spacing(
            double padding,
            double titleHeight,
            double subtitleHeight,
            double headerHeight,
            double gap,
            double cellPadding,
            double buttonsMargin,
            double buttonsGap,
            double depthStep
    ) {

        public static final @NotNull String PADDING = "spacing.padding";
        public static final @NotNull String TITLE_HEIGHT = "spacing.title-height";
        public static final @NotNull String SUBTITLE_HEIGHT = "spacing.subtitle-height";
        public static final @NotNull String HEADER_HEIGHT = "spacing.header-height";
        public static final @NotNull String GAP = "spacing.gap";
        public static final @NotNull String CELL_PADDING = "spacing.cell-padding";
        public static final @NotNull String BUTTONS_MARGIN = "spacing.buttons-margin";
        public static final @NotNull String BUTTONS_GAP = "spacing.buttons-gap";
        public static final @NotNull String DEPTH_STEP = "spacing.depth-step";

        public static final double DEFAULT_PADDING = 0.1D;
        public static final double DEFAULT_TITLE_HEIGHT = 0.26D;
        public static final double DEFAULT_SUBTITLE_HEIGHT = 0D;
        public static final double DEFAULT_HEADER_HEIGHT = 0.31D;
        public static final double DEFAULT_GAP = 0.03D;
        public static final double DEFAULT_CELL_PADDING = 0.05D;
        public static final double DEFAULT_BUTTONS_MARGIN = 0.24D;
        public static final double DEFAULT_BUTTONS_GAP = 0.13D;
        public static final double DEFAULT_DEPTH_STEP = 0.01D;

        public static final double MAX_DEPTH_STEP = 0.1D;
    }
}
