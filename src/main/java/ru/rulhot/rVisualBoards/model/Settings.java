package ru.rulhot.rVisualBoards.model;

import net.kyori.adventure.sound.Sound;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rVisualBoards.util.logger.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public record Settings(
        int updateIntervalSeconds,
        double viewRadius,
        boolean snapToGrid,
        boolean debug,
        @NotNull Clicks clicks,
        @NotNull Hover hover,
        @NotNull Scroll scroll,
        @NotNull Animation animation,
        @NotNull Advanced advanced
) {

    public static final @NotNull String FILE = "config.yml";

    public static final @NotNull String UPDATE_INTERVAL = "update-interval-seconds";
    public static final @NotNull String VIEW_RADIUS = "view-radius";
    public static final @NotNull String SNAP_TO_GRID = "snap-to-grid";
    public static final @NotNull String DEBUG = "debug";
    public static final @NotNull String METRICS = "metrics";

    public static final int DEFAULT_UPDATE_INTERVAL = 10;
    public static final double DEFAULT_VIEW_RADIUS = 32D;
    public static final boolean DEFAULT_SNAP_TO_GRID = true;
    public static final boolean DEFAULT_DEBUG = false;
    public static final boolean DEFAULT_METRICS = true;

    public static final int MIN_UPDATE_INTERVAL = 1;
    public static final int MAX_UPDATE_INTERVAL = 86_400;
    public static final double MIN_VIEW_RADIUS = 4D;
    public static final double MAX_VIEW_RADIUS = 128D;

    private static final double MIN_REACH = 0.5D;
    private static final double MAX_REACH = 32D;
    private static final int TICKS_PER_SECOND = 20;

    public Settings {
        updateIntervalSeconds = bounded(UPDATE_INTERVAL, updateIntervalSeconds, MIN_UPDATE_INTERVAL,
                MAX_UPDATE_INTERVAL, DEFAULT_UPDATE_INTERVAL);
        viewRadius = bounded(VIEW_RADIUS, viewRadius, MIN_VIEW_RADIUS, MAX_VIEW_RADIUS, DEFAULT_VIEW_RADIUS);
    }

    public long updateIntervalTicks() {
        return (long) updateIntervalSeconds * TICKS_PER_SECOND;
    }

    public record Command(@NotNull String name, @NotNull List<String> aliases) {

        public static final @NotNull String NAME = "command.name";
        public static final @NotNull String ALIASES = "command.aliases";

        private static final @NotNull Pattern LABEL = Pattern.compile("[a-z0-9_-]{1,32}");

        public static final @NotNull String DEFAULT_NAME = "rvb";
        public static final @NotNull List<String> DEFAULT_ALIASES = List.of("rvisualboards");
        public static final @NotNull Command DEFAULT = new Command(DEFAULT_NAME, DEFAULT_ALIASES);

        public Command {
            name = name.trim().toLowerCase(Locale.ROOT);
            if (!LABEL.matcher(name).matches()) {
                warn(NAME, "\"" + name + "\" не подходит (латиница, цифры, _ и -), используется " + DEFAULT_NAME);
                name = DEFAULT_NAME;
            }
            List<String> valid = new ArrayList<>();
            for (String alias : aliases) {
                String label = alias.trim().toLowerCase(Locale.ROOT);
                if (!LABEL.matcher(label).matches()) {
                    warn(ALIASES, "\"" + alias + "\" не подходит (латиница, цифры, _ и -), пропущено");
                    continue;
                }
                if (!label.equals(name) && !valid.contains(label)) {
                    valid.add(label);
                }
            }
            aliases = List.copyOf(valid);
        }
    }

    public record Clicks(int cooldownMillis, @Nullable Sound sound) {

        public static final @NotNull String COOLDOWN = "clicks.cooldown-millis";
        public static final @NotNull String SOUND = "clicks.sound";

        public static final int DEFAULT_COOLDOWN = 300;
        public static final int MIN_COOLDOWN = 0;
        public static final int MAX_COOLDOWN = 60_000;

        public Clicks {
            cooldownMillis = bounded(COOLDOWN, cooldownMillis, MIN_COOLDOWN, MAX_COOLDOWN, DEFAULT_COOLDOWN);
        }
    }

    public record Hover(boolean buttons, boolean rows, double scale, double rowScale, double distance,
                        int durationTicks) {

        public static final @NotNull String BUTTONS = "hover.buttons";
        public static final @NotNull String ROWS = "hover.rows";
        public static final @NotNull String SCALE = "hover.scale";
        public static final @NotNull String ROW_SCALE = "hover.row-scale";
        public static final @NotNull String DISTANCE = "hover.distance";
        public static final @NotNull String DURATION = "hover.duration-ticks";

        public static final boolean DEFAULT_ENABLED = true;
        public static final double DEFAULT_SCALE = 1.15D;
        public static final double DEFAULT_ROW_SCALE = 1.1D;
        public static final double DEFAULT_DISTANCE = 6D;
        public static final int DEFAULT_DURATION = 3;

        public static final double MIN_ZOOM = 1D;
        public static final double MAX_ZOOM = 3D;
        public static final int MIN_DURATION = 1;
        public static final int MAX_DURATION = 40;

        public Hover {
            scale = bounded(SCALE, scale, MIN_ZOOM, MAX_ZOOM, DEFAULT_SCALE);
            rowScale = bounded(ROW_SCALE, rowScale, MIN_ZOOM, MAX_ZOOM, DEFAULT_ROW_SCALE);
            distance = bounded(DISTANCE, distance, MIN_REACH, MAX_REACH, DEFAULT_DISTANCE);
            durationTicks = bounded(DURATION, durationTicks, MIN_DURATION, MAX_DURATION, DEFAULT_DURATION);
        }

        public boolean enabled() {
            return buttons || rows;
        }
    }

    public record Scroll(boolean enabled, int maxPlaces, int step, double distance) {

        public static final @NotNull String ENABLED = "scroll.enabled";
        public static final @NotNull String MAX_PLACES = "scroll.max-places";
        public static final @NotNull String STEP = "scroll.step";
        public static final @NotNull String DISTANCE = "scroll.distance";

        public static final boolean DEFAULT_ENABLED = true;
        public static final int DEFAULT_MAX_PLACES = 100;
        public static final int DEFAULT_STEP = 1;
        public static final double DEFAULT_DISTANCE = 8D;

        public static final int MIN_MAX_PLACES = 1;
        public static final int MAX_MAX_PLACES = 100;
        public static final int MIN_STEP = 1;
        public static final int MAX_STEP = 30;

        public Scroll {
            maxPlaces = bounded(MAX_PLACES, maxPlaces, MIN_MAX_PLACES, MAX_MAX_PLACES, DEFAULT_MAX_PLACES);
            step = bounded(STEP, step, MIN_STEP, MAX_STEP, DEFAULT_STEP);
            distance = bounded(DISTANCE, distance, MIN_REACH, MAX_REACH, DEFAULT_DISTANCE);
        }

        public int loadedPlaces(int rows) {
            return enabled ? Math.max(rows, maxPlaces) : rows;
        }
    }

    public record Animation(
            boolean enabled,
            @NotNull List<AnimationType> types,
            int durationTicks,
            int staggerTicks,
            double distance
    ) {

        public static final @NotNull String ENABLED = "animation.enabled";
        public static final @NotNull String TYPES = "animation.types";
        public static final @NotNull String DURATION = "animation.duration-ticks";
        public static final @NotNull String STAGGER = "animation.stagger-ticks";
        public static final @NotNull String DISTANCE = "animation.distance";

        public static final boolean DEFAULT_ENABLED = true;
        public static final @NotNull List<AnimationType> DEFAULT_TYPES = List.of(AnimationType.CASCADE);
        public static final int DEFAULT_DURATION = 6;
        public static final int DEFAULT_STAGGER = 1;
        public static final double DEFAULT_DISTANCE = 0.6D;

        public static final int MIN_DURATION = 1;
        public static final int MAX_DURATION = 60;
        public static final int MIN_STAGGER = 0;
        public static final int MAX_STAGGER = 20;
        public static final double MIN_DISTANCE = 0D;
        public static final double MAX_DISTANCE = 5D;

        public Animation {
            types = types.isEmpty() ? DEFAULT_TYPES : List.copyOf(types);
            durationTicks = bounded(DURATION, durationTicks, MIN_DURATION, MAX_DURATION, DEFAULT_DURATION);
            staggerTicks = bounded(STAGGER, staggerTicks, MIN_STAGGER, MAX_STAGGER, DEFAULT_STAGGER);
            distance = bounded(DISTANCE, distance, MIN_DISTANCE, MAX_DISTANCE, DEFAULT_DISTANCE);
        }
    }

    public enum AnimationType {
        SLIDE_LEFT(false),
        SLIDE_RIGHT(false),
        ZOOM(false),
        CASCADE(true);

        private final boolean staggered;

        AnimationType(boolean staggered) {
            this.staggered = staggered;
        }

        public boolean staggered() {
            return staggered;
        }
    }

    public record Advanced(
            int viewerCheckIntervalTicks,
            int hoverCheckIntervalTicks,
            @NotNull Placeholders placeholders,
            @NotNull Map<Integer, Integer> charWidths
    ) {

        public static final @NotNull String VIEWER_CHECK_INTERVAL = "advanced.viewer-check-interval-ticks";
        public static final @NotNull String HOVER_CHECK_INTERVAL = "advanced.hover-check-interval-ticks";
        public static final @NotNull String CHAR_WIDTHS = "advanced.char-widths";

        public static final int DEFAULT_VIEWER_CHECK_INTERVAL = 20;
        public static final int DEFAULT_HOVER_CHECK_INTERVAL = 2;

        public static final int MIN_VIEWER_CHECK_INTERVAL = 5;
        public static final int MAX_VIEWER_CHECK_INTERVAL = 1_200;
        public static final int MIN_HOVER_CHECK_INTERVAL = 1;
        public static final int MAX_HOVER_CHECK_INTERVAL = 200;
        public static final int MIN_CHAR_WIDTH = 0;
        public static final int MAX_CHAR_WIDTH = 64;

        public Advanced {
            charWidths = Map.copyOf(charWidths);
            viewerCheckIntervalTicks = bounded(VIEWER_CHECK_INTERVAL, viewerCheckIntervalTicks,
                    MIN_VIEWER_CHECK_INTERVAL, MAX_VIEWER_CHECK_INTERVAL, DEFAULT_VIEWER_CHECK_INTERVAL);
            hoverCheckIntervalTicks = bounded(HOVER_CHECK_INTERVAL, hoverCheckIntervalTicks,
                    MIN_HOVER_CHECK_INTERVAL, MAX_HOVER_CHECK_INTERVAL, DEFAULT_HOVER_CHECK_INTERVAL);
        }
    }

    public record Placeholders(
            @NotNull String name,
            @NotNull String prefix,
            @NotNull String value,
            @NotNull String position,
            @NotNull String personalValue,
            @NotNull String emptyName
    ) {

        public static final @NotNull String NAME = "advanced.placeholders.name";
        public static final @NotNull String PREFIX = "advanced.placeholders.prefix";
        public static final @NotNull String VALUE = "advanced.placeholders.value";
        public static final @NotNull String POSITION = "advanced.placeholders.position";
        public static final @NotNull String PERSONAL_VALUE = "advanced.placeholders.personal-value";
        public static final @NotNull String EMPTY_NAME = "advanced.placeholders.empty-name";

        public static final @NotNull String DEFAULT_NAME = "%ajlb_lb_{board}_{place}_{type}_name%";
        public static final @NotNull String DEFAULT_PREFIX = "%ajlb_lb_{board}_{place}_{type}_prefix%";
        public static final @NotNull String DEFAULT_VALUE = "%ajlb_lb_{board}_{place}_{type}_value%";
        public static final @NotNull String DEFAULT_POSITION = "%ajlb_position_{board}_{type}%";
        public static final @NotNull String DEFAULT_PERSONAL_VALUE = "%ajlb_value_{board}_{type}%";
        public static final @NotNull String DEFAULT_EMPTY_NAME = "---";

        public Placeholders {
            name = required(NAME, name, DEFAULT_NAME);
            value = required(VALUE, value, DEFAULT_VALUE);
            position = required(POSITION, position, DEFAULT_POSITION);
            personalValue = required(PERSONAL_VALUE, personalValue, DEFAULT_PERSONAL_VALUE);
        }

        private static @NotNull String required(@NotNull String path, @NotNull String value, @NotNull String fallback) {
            if (!value.isBlank()) {
                return value;
            }
            warn(path, "шаблон не может быть пустым, используется " + fallback);
            return fallback;
        }
    }

    private static int bounded(@NotNull String path, int value, int min, int max, int fallback) {
        if (value >= min && value <= max) {
            return value;
        }
        warn(path, value + " вне диапазона " + min + ".." + max + ", используется " + fallback);
        return fallback;
    }

    private static double bounded(@NotNull String path, double value, double min, double max, double fallback) {
        if (value >= min && value <= max) {
            return value;
        }
        warn(path, value + " вне диапазона " + min + ".." + max + ", используется " + fallback);
        return fallback;
    }

    private static void warn(@NotNull String path, @NotNull String problem) {
        Logger.warn("[" + FILE + "] " + path + ": " + problem);
    }
}
