package ru.rulhot.rVisualBoards.manager;

import lombok.RequiredArgsConstructor;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.command.ConsoleCommandSender;
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
import ru.rulhot.rVisualBoards.util.logger.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public final class ConfigManager {

    private static final @NotNull String MESSAGES_FILE = "messages.yml";
    private static final @NotNull String MESSAGE_PREFIX = "prefix";
    private static final @NotNull String PREFIX_TOKEN = "{prefix}";
    private static final @NotNull String COMMAND_TOKEN = "{command}";
    private static final @NotNull Pattern HEX_TAG = Pattern.compile("</?#[0-9a-fA-F]{6}>");
    private static final @NotNull Map<String, String> CONSOLE_SYMBOLS = Map.of("»", ">", "•", "*");
    private static final @NotNull Set<String> PRESERVED_SETTINGS = Set.of(Advanced.CHAR_WIDTHS);

    private final @NotNull JavaPlugin plugin;

    private volatile @Nullable Settings settings;
    private volatile @NotNull Messages messages = Messages.EMPTY;
    private volatile @NotNull String command = Settings.Command.DEFAULT_NAME;

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

    public @NotNull Startup readStartup() {
        ConfigFile file = ConfigFile.load(plugin, Settings.FILE, PRESERVED_SETTINGS);
        if (file == null) {
            return new Startup(Settings.Command.DEFAULT, Settings.DEFAULT_METRICS, Settings.DEFAULT_UPDATE_CHECK);
        }
        Settings.Command parsed = parseCommand(file);
        command = parsed.name();
        return new Startup(parsed, file.bool(Settings.METRICS, Settings.DEFAULT_METRICS),
                file.bool(Settings.UPDATE_CHECK, Settings.DEFAULT_UPDATE_CHECK));
    }

    public @NotNull Component message(@NotNull String key, @NotNull Map<String, ?> placeholders) {
        Messages current = messages;
        return MessageUtil.parseText(raw(current, key).replace(PREFIX_TOKEN, current.prefix()), placeholders);
    }

    public void send(@NotNull Audience audience, @NotNull String key) {
        send(audience, key, Map.of());
    }

    public void send(@NotNull Audience audience, @NotNull String key, @NotNull Map<String, ?> placeholders) {
        Messages current = messages;
        String raw = raw(current, key);
        if (raw.isEmpty()) {
            return;
        }
        if (!(audience instanceof ConsoleCommandSender)) {
            audience.sendMessage(MessageUtil.parseText(raw.replace(PREFIX_TOKEN, current.prefix()), placeholders));
            return;
        }
        String console = HEX_TAG.matcher(raw.replace(PREFIX_TOKEN, "[" + plugin.getName() + "] ")).replaceAll("");
        for (Map.Entry<String, String> symbol : CONSOLE_SYMBOLS.entrySet()) {
            console = console.replace(symbol.getKey(), symbol.getValue());
        }
        audience.sendMessage(MessageUtil.parseText(console, placeholders));
    }

    private @NotNull String raw(@NotNull Messages current, @NotNull String key) {
        return current.raw(key).replace(COMMAND_TOKEN, command);
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
                        file.bool(Animation.APPEAR, Animation.DEFAULT_APPEAR),
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
                        + Advanced.MAX_CHAR_WIDTH + ", пропускаю");
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
        Map<String, String> entries = new HashMap<>();
        collectMessages(file, "", entries);
        return new Messages(file.string(MESSAGE_PREFIX, ""), entries);
    }

    private static void collectMessages(@NotNull ConfigFile file, @NotNull String path,
                                        @NotNull Map<String, String> sink) {
        for (String key : file.keys(path)) {
            String child = path.isEmpty() ? key : ConfigFile.join(path, key);
            if (file.isSection(child)) {
                collectMessages(file, child, sink);
                continue;
            }
            sink.put(child, file.string(child, ""));
        }
    }

    public record Startup(@NotNull Settings.Command command, boolean metrics, boolean updateCheck) {
    }

    public record Snapshot(@NotNull Settings settings, @NotNull Messages messages) {
    }

    public record Messages(@NotNull String prefix, @NotNull Map<String, String> entries) {

        private static final @NotNull Messages EMPTY = new Messages("", Map.of());

        public Messages {
            entries = Map.copyOf(entries);
        }

        private @NotNull String raw(@NotNull String key) {
            String value = entries.get(key);
            if (value == null) {
                Logger.warn("В messages.yml нет ключа '" + key + "'");
                return "";
            }
            return value;
        }
    }
}
