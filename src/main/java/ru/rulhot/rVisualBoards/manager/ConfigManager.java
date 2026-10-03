package ru.rulhot.rVisualBoards.manager;

import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rVisualBoards.model.Settings;
import ru.rulhot.rVisualBoards.model.Settings.Advanced;
import ru.rulhot.rVisualBoards.model.Settings.Animation;
import ru.rulhot.rVisualBoards.model.Settings.AnimationType;
import ru.rulhot.rVisualBoards.model.Settings.Clicks;
import ru.rulhot.rVisualBoards.model.Settings.Hover;
import ru.rulhot.rVisualBoards.model.Settings.Placeholders;
import ru.rulhot.rVisualBoards.model.Settings.Scroll;
import ru.rulhot.rVisualBoards.util.MessageUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
public final class ConfigManager {

    private static final @NotNull String MESSAGES_FILE = "messages.yml";
    private static final @NotNull String MESSAGE_PREFIX = "prefix";
    private static final @NotNull String PREFIX_TOKEN = "{prefix}";
    private static final @NotNull Set<String> PRESERVED_SETTINGS = Set.of(Advanced.CHAR_WIDTHS);

    private final @NotNull JavaPlugin plugin;

    private volatile @Nullable Settings settings;
    private volatile @NotNull Messages messages = Messages.EMPTY;

    public @Nullable Snapshot read() {
        ConfigFile settingsFile = ConfigFile.load(plugin, Settings.FILE, PRESERVED_SETTINGS);
        ConfigFile messagesFile = ConfigFile.load(plugin, MESSAGES_FILE, Set.of());
        if (settingsFile == null || messagesFile == null) {
            return null;
        }
        return new Snapshot(parseSettings(settingsFile), parseMessages(messagesFile));
    }

    public void apply(@NotNull Snapshot snapshot) {
        messages = snapshot.messages();
        settings = snapshot.settings();
    }

    public @Nullable Settings settings() {
        return settings;
    }

    public @NotNull Settings.Command readCommand() {
        ConfigFile file = ConfigFile.load(plugin, Settings.FILE, PRESERVED_SETTINGS);
        return file == null ? Settings.Command.DEFAULT : parseCommand(file);
    }

    public boolean isMetrics() {
        ConfigFile file = ConfigFile.load(plugin, Settings.FILE, PRESERVED_SETTINGS);
        return file == null
                ? Settings.DEFAULT_METRICS
                : file.bool(Settings.METRICS, Settings.DEFAULT_METRICS);
    }

    public @NotNull Component getCommandMessage(@NotNull String key) {
        return MessageUtil.parseText(messages.raw(key));
    }

    public @NotNull Component formatCommandMessage(@NotNull String key, @NotNull Map<String, ?> placeholders) {
        return MessageUtil.parseText(messages.raw(key), placeholders);
    }

    static @NotNull Settings parseSettings(@NotNull ConfigFile file) {
        return new Settings(
                file.integer(Settings.UPDATE_INTERVAL, Settings.DEFAULT_UPDATE_INTERVAL),
                file.decimal(Settings.VIEW_RADIUS, Settings.DEFAULT_VIEW_RADIUS),
                file.bool(Settings.SNAP_TO_GRID, Settings.DEFAULT_SNAP_TO_GRID),
                file.bool(Settings.DEBUG, Settings.DEFAULT_DEBUG),
                new Clicks(
                        file.integer(Clicks.COOLDOWN, Clicks.DEFAULT_COOLDOWN),
                        file.sound(Clicks.SOUND)),
                new Hover(
                        file.bool(Hover.BUTTONS, Hover.DEFAULT_ENABLED),
                        file.bool(Hover.ROWS, Hover.DEFAULT_ENABLED),
                        file.decimal(Hover.SCALE, Hover.DEFAULT_SCALE),
                        file.decimal(Hover.ROW_SCALE, Hover.DEFAULT_ROW_SCALE),
                        file.decimal(Hover.DISTANCE, Hover.DEFAULT_DISTANCE),
                        file.integer(Hover.DURATION, Hover.DEFAULT_DURATION)),
                new Scroll(
                        file.bool(Scroll.ENABLED, Scroll.DEFAULT_ENABLED),
                        file.integer(Scroll.MAX_PLACES, Scroll.DEFAULT_MAX_PLACES),
                        file.integer(Scroll.STEP, Scroll.DEFAULT_STEP),
                        file.decimal(Scroll.DISTANCE, Scroll.DEFAULT_DISTANCE)),
                new Animation(
                        file.bool(Animation.ENABLED, Animation.DEFAULT_ENABLED),
                        file.enumList(Animation.TYPES, AnimationType.class, Animation.DEFAULT_TYPES),
                        file.integer(Animation.DURATION, Animation.DEFAULT_DURATION),
                        file.integer(Animation.STAGGER, Animation.DEFAULT_STAGGER),
                        file.decimal(Animation.DISTANCE, Animation.DEFAULT_DISTANCE)),
                new Advanced(
                        file.integer(Advanced.VIEWER_CHECK_INTERVAL, Advanced.DEFAULT_VIEWER_CHECK_INTERVAL),
                        file.integer(Advanced.HOVER_CHECK_INTERVAL, Advanced.DEFAULT_HOVER_CHECK_INTERVAL),
                        new Placeholders(
                                file.string(Placeholders.NAME, Placeholders.DEFAULT_NAME),
                                file.string(Placeholders.PREFIX, Placeholders.DEFAULT_PREFIX),
                                file.string(Placeholders.VALUE, Placeholders.DEFAULT_VALUE),
                                file.string(Placeholders.POSITION, Placeholders.DEFAULT_POSITION),
                                file.string(Placeholders.PERSONAL_VALUE, Placeholders.DEFAULT_PERSONAL_VALUE),
                                file.string(Placeholders.EMPTY_NAME, Placeholders.DEFAULT_EMPTY_NAME)),
                        parseCharWidths(file)));
    }

    private static @NotNull Map<Integer, Integer> parseCharWidths(@NotNull ConfigFile file) {
        Map<Integer, Integer> widths = new HashMap<>();
        for (String symbols : file.keys(Advanced.CHAR_WIDTHS)) {
            String path = ConfigFile.join(Advanced.CHAR_WIDTHS, symbols);
            Integer width = file.optionalInteger(path);
            if (width == null) {
                continue;
            }
            if (width < Advanced.MIN_CHAR_WIDTH || width > Advanced.MAX_CHAR_WIDTH) {
                file.warn(path, "ширина должна быть от " + Advanced.MIN_CHAR_WIDTH + " до "
                        + Advanced.MAX_CHAR_WIDTH + ", символы пропущены");
                continue;
            }
            symbols.codePoints().forEach(codePoint -> widths.put(codePoint, width));
        }
        return widths;
    }

    static @NotNull Settings.Command parseCommand(@NotNull ConfigFile file) {
        return new Settings.Command(
                file.string(Settings.Command.NAME, Settings.Command.DEFAULT_NAME),
                file.stringList(Settings.Command.ALIASES, Settings.Command.DEFAULT_ALIASES));
    }

    static @NotNull Messages parseMessages(@NotNull ConfigFile file) {
        String prefix = file.string(MESSAGE_PREFIX, "");
        Map<String, String> entries = new HashMap<>();
        collectMessages(file, "", prefix, entries);
        return new Messages(entries);
    }

    private static void collectMessages(@NotNull ConfigFile file, @NotNull String path, @NotNull String prefix,
                                        @NotNull Map<String, String> sink) {
        for (String key : file.keys(path)) {
            String child = path.isEmpty() ? key : ConfigFile.join(path, key);
            if (file.isSection(child)) {
                collectMessages(file, child, prefix, sink);
                continue;
            }
            sink.put(child, file.string(child, "").replace(PREFIX_TOKEN, prefix));
        }
    }

    public record Snapshot(@NotNull Settings settings, @NotNull Messages messages) {
    }

    public record Messages(@NotNull Map<String, String> entries) {

        private static final @NotNull Messages EMPTY = new Messages(Map.of());

        public Messages {
            entries = Map.copyOf(entries);
        }

        private @NotNull String raw(@NotNull String key) {
            return entries.getOrDefault(key, key);
        }
    }
}
