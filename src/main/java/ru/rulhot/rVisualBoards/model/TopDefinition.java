package ru.rulhot.rVisualBoards.model;

import net.kyori.adventure.sound.Sound;
import org.bukkit.Color;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record TopDefinition(
        @NotNull String id,
        @NotNull String board,
        @NotNull List<Period> periods,
        @NotNull String label,
        @NotNull String title,
        @Nullable Color buttonColor,
        @NotNull Map<FormatKey, String> formats,
        @NotNull Map<String, String> placeholders,
        @NotNull Map<Period, String> periodNames,
        @NotNull List<String> rowClick,
        @Nullable Sound switchSound
) {

    public static final @NotNull String FORMATS = "formats";
    public static final @NotNull String PLACEHOLDERS = "placeholders";
    public static final @NotNull String TOPS = "tops";
    public static final @NotNull String BOARD = "board";
    public static final @NotNull String PERIODS = "periods";
    public static final @NotNull String PERIOD_NAMES = "period-names";
    public static final @NotNull String LABEL = "label";
    public static final @NotNull String TITLE = "title";
    public static final @NotNull String BUTTON_COLOR = "button-color";
    public static final @NotNull String ROW_CLICK = "row-click";
    public static final @NotNull String SWITCH_SOUND = "switch-sound";


    public static final @NotNull List<Period> DEFAULT_PERIODS =
            List.of(Period.ALLTIME, Period.MONTHLY, Period.WEEKLY, Period.DAILY);

    public TopDefinition {
        periods = periods.isEmpty() ? DEFAULT_PERIODS : List.copyOf(new LinkedHashSet<>(periods));
        formats = Map.copyOf(formats);
        placeholders = Map.copyOf(placeholders);
        periodNames = Map.copyOf(periodNames);
        rowClick = List.copyOf(rowClick);

    }

    public @NotNull String format(@NotNull FormatKey key) {
        return formats.getOrDefault(key, "");
    }

    public @NotNull Period defaultPeriod() {
        return periods.getFirst();
    }

    public @NotNull Period periodOr(@NotNull Period preferred) {
        return periods.contains(preferred) ? preferred : defaultPeriod();
    }

    public @NotNull String periodName(@NotNull Period period) {
        return periodNames.getOrDefault(period, period.key());
    }

    public enum Period {
        ALLTIME,
        HOURLY,
        DAILY,
        WEEKLY,
        MONTHLY,
        YEARLY;

        public @NotNull String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum FormatKey {
        HEADER_PLACE("header-place"),
        HEADER_NAME("header-name"),
        HEADER_VALUE("header-value"),
        ROW_PLACE("row-place"),
        ROW_NAME("row-name"),
        ROW_VALUE("row-value"),
        EMPTY_PLACE("empty-place"),
        EMPTY_NAME("empty-name"),
        EMPTY_VALUE("empty-value"),
        PERSONAL_PLACE("personal-place"),
        PERSONAL_PLACE_UNRANKED("personal-place-unranked"),
        PERSONAL_NAME("personal-name"),
        PERSONAL_VALUE("personal-value"),
        BUTTON("button"),
        BUTTON_SELECTED("button-selected"),
        ARROW_LEFT("arrow-left"),
        ARROW_RIGHT("arrow-right"),
        TAB("tab"),
        TAB_SELECTED("tab-selected"),
        SUBTITLE("subtitle"),
        FOOTER("footer"),
        HINT_ROW("hint-row"),
        HINT_BUTTON("hint-button");

        private final @NotNull String path;

        FormatKey(@NotNull String path) {
            this.path = path;
        }

        public @NotNull String path() {
            return path;
        }
    }
}
