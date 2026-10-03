package ru.rulhot.rVisualBoards.manager;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rVisualBoards.model.BoardLayout;
import ru.rulhot.rVisualBoards.model.BoardLayout.Buttons;
import ru.rulhot.rVisualBoards.model.BoardLayout.Colors;
import ru.rulhot.rVisualBoards.model.BoardLayout.Column;
import ru.rulhot.rVisualBoards.model.BoardLayout.Columns;
import ru.rulhot.rVisualBoards.model.BoardLayout.Spacing;
import ru.rulhot.rVisualBoards.model.BoardLayout.Tabs;
import ru.rulhot.rVisualBoards.model.BoardLayout.Table;
import ru.rulhot.rVisualBoards.model.BoardType;
import ru.rulhot.rVisualBoards.model.PlacedBoard;
import ru.rulhot.rVisualBoards.model.TopDefinition;
import ru.rulhot.rVisualBoards.model.TopDefinition.FormatKey;
import ru.rulhot.rVisualBoards.model.TopDefinition.Period;
import ru.rulhot.rVisualBoards.util.logger.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

@RequiredArgsConstructor
public final class TopManager {

    private static final @NotNull String EXTENSION = ".yml";
    private static final @NotNull String SEPARATOR = "/";

    private final @NotNull JavaPlugin plugin;
    private final @NotNull File jar;

    private volatile @NotNull Map<String, BoardType> types = Map.of();

    public @Nullable List<BoardType> read() {
        Path folder = plugin.getDataFolder().toPath().resolve(BoardType.FOLDER);
        if (Files.notExists(folder)) {
            saveBundled();
        }
        List<Path> files;
        try (Stream<Path> listed = Files.list(folder)) {
            files = listed.filter(path -> path.getFileName().toString().endsWith(EXTENSION)).sorted().toList();
        } catch (IOException exception) {
            Logger.error("Не удалось прочитать папку " + BoardType.FOLDER, exception);
            return null;
        }
        Map<String, BoardType> result = new LinkedHashMap<>();
        for (Path path : files) {
            String fileName = path.getFileName().toString();
            String name = BoardType.FOLDER + SEPARATOR + fileName;
            String id = PlacedBoard.normalizeId(fileName.substring(0, fileName.length() - EXTENSION.length()));
            if (!PlacedBoard.isValidId(id) || result.containsKey(id)) {
                Logger.warn("[" + name + "] имя файла — латиница, цифры, _ и - (до 32 символов) и не повторяется, файл пропущен");
                continue;
            }
            ConfigFile file = ConfigFile.read(path, name);
            if (file == null) {
                return null;
            }
            List<TopDefinition> tops = parseTops(file);
            if (!tops.isEmpty()) {
                result.put(id, new BoardType(id, parseLayout(file), tops));
            }
        }
        if (result.isEmpty()) {
            Logger.warn("В папке " + BoardType.FOLDER + " нет ни одного файла с топами, табло не появятся");
        }
        return List.copyOf(result.values());
    }

    private void saveBundled() {
        try (JarFile file = new JarFile(jar)) {
            file.stream().map(JarEntry::getName)
                    .filter(name -> name.startsWith(BoardType.FOLDER + SEPARATOR) && name.endsWith(EXTENSION))
                    .forEach(name -> plugin.saveResource(name, false));
        } catch (IOException exception) {
            Logger.error("Не удалось достать папку " + BoardType.FOLDER + " из jar плагина", exception);
        }
    }

    public void apply(@NotNull List<BoardType> loaded) {
        Map<String, BoardType> indexed = new LinkedHashMap<>();
        for (BoardType type : loaded) {
            indexed.put(type.id(), type);
        }
        types = Collections.unmodifiableMap(indexed);
    }

    public @Nullable BoardType find(@NotNull String id) {
        return types.get(id);
    }

    public @NotNull List<String> ids() {
        return List.copyOf(types.keySet());
    }

    static @NotNull List<TopDefinition> parseTops(@NotNull ConfigFile file) {
        Map<FormatKey, String> defaults = parseFormats(file, TopDefinition.FORMATS, Map.of());
        List<Period> periods = file.enumList(TopDefinition.PERIODS, Period.class, TopDefinition.DEFAULT_PERIODS);
        Map<Period, String> periodNames = parsePeriodNames(file);
        Map<String, TopDefinition> result = new LinkedHashMap<>();
        for (String key : file.keys(TopDefinition.TOPS)) {
            TopDefinition top = parseTop(file, key, defaults, periods, periodNames);
            if (top != null && result.putIfAbsent(top.id(), top) != null) {
                file.warn(ConfigFile.join(TopDefinition.TOPS, key), "топ с таким ID уже есть, пропущен");
            }
        }
        if (result.isEmpty()) {
            Logger.warn("[" + file.name() + "] в секции " + TopDefinition.TOPS + " нет ни одного топа, файл пропущен");
        }
        return List.copyOf(result.values());
    }

    static @NotNull BoardLayout parseLayout(@NotNull ConfigFile file) {
        return new BoardLayout(
                parseTable(file),
                parseButtons(file),
                parseTabs(file),
                parseColumns(file),
                parseColors(file),
                parseSpacing(file));
    }

    private static @Nullable TopDefinition parseTop(@NotNull ConfigFile file, @NotNull String key,
                                                   @NotNull Map<FormatKey, String> defaults,
                                                   @NotNull List<Period> periods,
                                                   @NotNull Map<Period, String> periodNames) {
        String path = ConfigFile.join(TopDefinition.TOPS, key);
        if (!file.isSection(path)) {
            file.warn(path, "ожидается секция с настройками топа, пропущен");
            return null;
        }
        String board = file.string(ConfigFile.join(path, TopDefinition.BOARD), "").trim();
        if (board.isEmpty()) {
            file.warn(ConfigFile.join(path, TopDefinition.BOARD), "не указан борд ajLeaderboards, топ пропущен");
            return null;
        }
        String id = key.toLowerCase(Locale.ROOT);
        return new TopDefinition(
                id,
                board,
                file.enumList(ConfigFile.join(path, TopDefinition.PERIODS), Period.class, periods),
                file.string(ConfigFile.join(path, TopDefinition.LABEL), id),
                file.string(ConfigFile.join(path, TopDefinition.TITLE), ""),
                file.optionalColor(ConfigFile.join(path, TopDefinition.BUTTON_COLOR)),
                parseFormats(file, ConfigFile.join(path, TopDefinition.FORMATS), defaults),
                periodNames);
    }

    private static @NotNull Map<Period, String> parsePeriodNames(@NotNull ConfigFile file) {
        Map<Period, String> names = new EnumMap<>(Period.class);
        for (Period period : Period.values()) {
            names.put(period, file.string(ConfigFile.join(TopDefinition.PERIOD_NAMES, period.key()), period.key()));
        }
        return names;
    }

    private static @NotNull Map<FormatKey, String> parseFormats(@NotNull ConfigFile file, @NotNull String path,
                                                              @NotNull Map<FormatKey, String> fallback) {
        Map<FormatKey, String> formats = new EnumMap<>(FormatKey.class);
        for (FormatKey key : FormatKey.values()) {
            formats.put(key, file.string(ConfigFile.join(path, key.path()), fallback.getOrDefault(key, "")));
        }
        return formats;
    }

    private static @NotNull Table parseTable(@NotNull ConfigFile file) {
        return new Table(
                file.decimal(Table.WIDTH, Table.DEFAULT_WIDTH, Table.MIN_WIDTH, Table.MAX_WIDTH),
                file.integer(Table.ROWS, Table.DEFAULT_ROWS, Table.MIN_ROWS, Table.MAX_ROWS),
                scale(file, Table.TEXT_SCALE, Table.DEFAULT_TEXT_SCALE),
                scale(file, Table.TITLE_SCALE, Table.DEFAULT_TITLE_SCALE),
                file.bool(Table.PERSONAL_ROW, Table.DEFAULT_PERSONAL_ROW),
                file.bool(Table.SHADOW, Table.DEFAULT_SHADOW),
                file.bool(Table.FULL_BRIGHT, Table.DEFAULT_FULL_BRIGHT));
    }

    private static @NotNull Buttons parseButtons(@NotNull ConfigFile file) {
        return new Buttons(
                file.bool(Buttons.ENABLED, Buttons.DEFAULT_ENABLED),
                file.integer(Buttons.VISIBLE, Buttons.DEFAULT_VISIBLE, Buttons.MIN_VISIBLE, Buttons.MAX_VISIBLE),
                file.bool(Buttons.ARROWS, Buttons.DEFAULT_ARROWS),
                height(file, Buttons.HEIGHT, Buttons.DEFAULT_HEIGHT),
                scale(file, Buttons.TEXT_SCALE, Buttons.DEFAULT_TEXT_SCALE),
                height(file, Buttons.ARROW_WIDTH, Buttons.DEFAULT_ARROW_WIDTH));
    }

    private static @NotNull Tabs parseTabs(@NotNull ConfigFile file) {
        return new Tabs(
                file.bool(Tabs.ENABLED, Tabs.DEFAULT_ENABLED),
                height(file, Tabs.HEIGHT, Tabs.DEFAULT_HEIGHT),
                scale(file, Tabs.TEXT_SCALE, Tabs.DEFAULT_TEXT_SCALE));
    }

    private static @NotNull Columns parseColumns(@NotNull ConfigFile file) {
        return new Columns(
                parseColumn(file, Columns.PLACE, Columns.DEFAULT_PLACE),
                parseColumn(file, Columns.NAME, Columns.DEFAULT_NAME),
                parseColumn(file, Columns.VALUE, Columns.DEFAULT_VALUE));
    }

    private static @NotNull Column parseColumn(@NotNull ConfigFile file, @NotNull String path,
                                               @NotNull Column fallback) {
        Column column = new Column(
                file.decimal(ConfigFile.join(path, Columns.FROM), fallback.from()),
                file.decimal(ConfigFile.join(path, Columns.TO), fallback.to()),
                file.enumValue(ConfigFile.join(path, Columns.ALIGN), TextDisplay.TextAlignment.class, fallback.align()));
        if (column.to() > column.from()) {
            return column;
        }
        file.warn(path, "to (" + column.to() + ") должно быть больше from (" + column.from()
                + "), используются границы по умолчанию");
        return fallback;
    }

    private static @NotNull Colors parseColors(@NotNull ConfigFile file) {
        return new Colors(
                file.color(Colors.TITLE, Colors.DEFAULT_TITLE),
                file.color(Colors.HEADER, Colors.DEFAULT_HEADER),
                file.color(Colors.COLUMN, Colors.DEFAULT_COLUMN),
                file.color(Colors.PERSONAL_ROW, Colors.DEFAULT_PERSONAL_ROW),
                file.color(Colors.BUTTON, Colors.DEFAULT_BUTTON),
                file.color(Colors.BUTTON_SELECTED, Colors.DEFAULT_BUTTON_SELECTED),
                file.color(Colors.ARROW, Colors.DEFAULT_ARROW),
                file.color(Colors.TAB, Colors.DEFAULT_TAB),
                file.color(Colors.TAB_SELECTED, Colors.DEFAULT_TAB_SELECTED));
    }

    private static @NotNull Spacing parseSpacing(@NotNull ConfigFile file) {
        return new Spacing(
                spacing(file, Spacing.PADDING, Spacing.DEFAULT_PADDING),
                height(file, Spacing.TITLE_HEIGHT, Spacing.DEFAULT_TITLE_HEIGHT),
                spacing(file, Spacing.SUBTITLE_HEIGHT, Spacing.DEFAULT_SUBTITLE_HEIGHT),
                height(file, Spacing.HEADER_HEIGHT, Spacing.DEFAULT_HEADER_HEIGHT),
                spacing(file, Spacing.GAP, Spacing.DEFAULT_GAP),
                spacing(file, Spacing.CELL_PADDING, Spacing.DEFAULT_CELL_PADDING),
                spacing(file, Spacing.BUTTONS_MARGIN, Spacing.DEFAULT_BUTTONS_MARGIN),
                spacing(file, Spacing.BUTTONS_GAP, Spacing.DEFAULT_BUTTONS_GAP),
                file.decimal(Spacing.DEPTH_STEP, Spacing.DEFAULT_DEPTH_STEP, BoardLayout.MIN_SPACING,
                        Spacing.MAX_DEPTH_STEP));
    }

    private static double scale(@NotNull ConfigFile file, @NotNull String path, double fallback) {
        return file.decimal(path, fallback, BoardLayout.MIN_SCALE, BoardLayout.MAX_SCALE);
    }

    private static double height(@NotNull ConfigFile file, @NotNull String path, double fallback) {
        return file.decimal(path, fallback, BoardLayout.MIN_HEIGHT, BoardLayout.MAX_HEIGHT);
    }

    private static double spacing(@NotNull ConfigFile file, @NotNull String path, double fallback) {
        return file.decimal(path, fallback, BoardLayout.MIN_SPACING, BoardLayout.MAX_SPACING);
    }
}
