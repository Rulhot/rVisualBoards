package ru.rulhot.rVisualBoards.manager;

import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rVisualBoards.util.logger.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ConfigFile {

    private static final @NotNull String VERSION_KEY = "config-version";
    private static final @NotNull String PATH_SEPARATOR = ".";
    private static final @NotNull String TEMPORARY_SUFFIX = ".tmp";
    private static final @NotNull String BACKUP_SUFFIX = ".bak";
    private static final @NotNull String SOUND_KEY = "key";
    private static final @NotNull String SOUND_VOLUME = "volume";
    private static final @NotNull String SOUND_PITCH = "pitch";
    private static final float MIN_VOLUME = 0F;
    private static final float MAX_VOLUME = 1F;
    private static final float MIN_PITCH = 0.5F;
    private static final float MAX_PITCH = 2F;
    private static final int RGB_LENGTH = 6;
    private static final int ARGB_LENGTH = 8;
    private static final int HEX_RADIX = 16;
    private static final @NotNull String OPAQUE_ALPHA = "FF";
    private static final @NotNull String HEX_PREFIX = "#";

    private final @NotNull String name;
    private final @NotNull YamlConfiguration yaml;

    private ConfigFile(@NotNull String name, @NotNull YamlConfiguration yaml) {
        this.name = name;
        this.yaml = yaml;
    }

    public static @Nullable ConfigFile load(@NotNull JavaPlugin plugin, @NotNull String name,
                                            @NotNull Set<String> preserved) {
        String defaults = bundled(plugin, name);
        if (defaults == null) {
            return null;
        }
        return load(plugin.getDataFolder().toPath().resolve(name), name, defaults, preserved);
    }

    static @Nullable ConfigFile load(@NotNull Path file, @NotNull String name, @NotNull String defaultsText,
                                     @NotNull Set<String> preserved) {
        try {
            if (Files.notExists(file)) {
                Files.createDirectories(file.toAbsolutePath().getParent());
                Files.writeString(file, defaultsText, StandardCharsets.UTF_8);
            }
            YamlConfiguration defaults = new YamlConfiguration();
            defaults.options().parseComments(true);
            defaults.loadFromString(defaultsText);
            YamlConfiguration current = new YamlConfiguration();
            current.loadFromString(Files.readString(file, StandardCharsets.UTF_8));
            if (isOutdated(current, defaults, preserved)) {
                Files.copy(file, file.resolveSibling(file.getFileName() + BACKUP_SUFFIX),
                        StandardCopyOption.REPLACE_EXISTING);
                writeAtomically(file, merge(current, defaults, preserved).saveToString());
                Logger.info("Файл " + name + " обновлён: добавлены новые ключи, ваши значения сохранены");
                current.loadFromString(Files.readString(file, StandardCharsets.UTF_8));
            }
            return new ConfigFile(name, current);
        } catch (InvalidConfigurationException exception) {
            Logger.error("Ошибка в " + name + ", исправьте файл и выполните /rvb reload: " + exception.getMessage());
            return null;
        } catch (IOException exception) {
            Logger.error("Не удалось прочитать или записать " + name, exception);
            return null;
        }
    }

    static @Nullable ConfigFile read(@NotNull Path file, @NotNull String name) {
        try {
            return parse(Files.readString(file, StandardCharsets.UTF_8), name);
        } catch (IOException exception) {
            Logger.error("Не удалось прочитать " + name, exception);
            return null;
        }
    }

    static @Nullable ConfigFile parse(@NotNull String text, @NotNull String name) {
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(text);
            return new ConfigFile(name, yaml);
        } catch (InvalidConfigurationException exception) {
            Logger.error("Ошибка в " + name + ", исправьте файл и выполните /rvb reload: " + exception.getMessage());
            return null;
        }
    }

    private static void writeAtomically(@NotNull Path file, @NotNull String text) throws IOException {
        Path temporary = file.resolveSibling(file.getFileName() + TEMPORARY_SUFFIX);
        Files.writeString(temporary, text, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static @Nullable String bundled(@NotNull JavaPlugin plugin, @NotNull String name) {
        try (InputStream stream = plugin.getResource(name)) {
            if (stream == null) {
                Logger.error("В jar плагина нет файла " + name + ", пересоберите плагин");
                return null;
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            Logger.error("Не удалось прочитать " + name + " из jar плагина", exception);
            return null;
        }
    }

    public @NotNull String name() {
        return name;
    }

    public @NotNull Set<String> keys(@NotNull String path) {
        ConfigurationSection section = yaml.getConfigurationSection(path);
        return section == null ? Set.of() : section.getKeys(false);
    }

    public boolean isSection(@NotNull String path) {
        return yaml.isConfigurationSection(path);
    }

    public @NotNull String string(@NotNull String path, @NotNull String fallback) {
        Object raw = yaml.get(path);
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof ConfigurationSection || raw instanceof List<?>) {
            warn(path, "ожидается строка, используется \"" + fallback + "\"");
            return fallback;
        }
        return String.valueOf(raw);
    }

    public boolean bool(@NotNull String path, boolean fallback) {
        Object raw = yaml.get(path);
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Boolean value) {
            return value;
        }
        String text = String.valueOf(raw).trim();
        if (text.equalsIgnoreCase(Boolean.TRUE.toString())) {
            return true;
        }
        if (text.equalsIgnoreCase(Boolean.FALSE.toString())) {
            return false;
        }
        warn(path, "ожидается true или false, используется " + fallback);
        return fallback;
    }

    public double decimal(@NotNull String path, double fallback) {
        Object raw = yaml.get(path);
        if (raw == null) {
            return fallback;
        }
        Double value = parseDecimal(raw);
        if (value != null) {
            return value;
        }
        warn(path, "неверное число \"" + raw + "\", используется " + fallback);
        return fallback;
    }

    public double decimal(@NotNull String path, double fallback, double min, double max) {
        double value = decimal(path, fallback);
        if (value >= min && value <= max) {
            return value;
        }
        warn(path, value + " вне диапазона " + min + ".." + max + ", используется " + fallback);
        return fallback;
    }

    public int integer(@NotNull String path, int fallback) {
        Integer value = optionalInteger(path);
        return value == null ? fallback : value;
    }

    public int integer(@NotNull String path, int fallback, int min, int max) {
        int value = integer(path, fallback);
        if (value >= min && value <= max) {
            return value;
        }
        warn(path, value + " вне диапазона " + min + ".." + max + ", используется " + fallback);
        return fallback;
    }

    public @Nullable Integer optionalInteger(@NotNull String path) {
        Object raw = yaml.get(path);
        if (raw == null) {
            return null;
        }
        Double value = parseDecimal(raw);
        if (value != null && value == Math.rint(value) && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
            return value.intValue();
        }
        warn(path, "ожидается целое число, а не \"" + raw + "\", используется значение по умолчанию");
        return null;
    }

    public @NotNull Color color(@NotNull String path, @NotNull Color fallback) {
        Color parsed = optionalColor(path);
        return parsed == null ? fallback : parsed;
    }

    public @Nullable Color optionalColor(@NotNull String path) {
        Object raw = yaml.get(path);
        if (raw == null) {
            return null;
        }
        Color parsed = parseColor(String.valueOf(raw));
        if (parsed == null) {
            warn(path, "неверный цвет \"" + raw + "\", ожидается #AARRGGBB или #RRGGBB");
        }
        return parsed;
    }

    public <E extends Enum<E>> @NotNull E enumValue(@NotNull String path, @NotNull Class<E> type, @NotNull E fallback) {
        Object raw = yaml.get(path);
        if (raw == null) {
            return fallback;
        }
        E parsed = parseEnum(type, String.valueOf(raw));
        if (parsed != null) {
            return parsed;
        }
        warn(path, "неизвестное значение \"" + raw + "\", используется " + fallback.name());
        return fallback;
    }

    public @NotNull List<String> stringList(@NotNull String path, @NotNull List<String> fallback) {
        if (!yaml.isList(path)) {
            if (yaml.contains(path)) {
                warn(path, "ожидается список, используется " + fallback);
            }
            return fallback;
        }
        return List.copyOf(yaml.getStringList(path));
    }

    public <E extends Enum<E>> @NotNull List<E> enumList(@NotNull String path, @NotNull Class<E> type,
                                                        @NotNull List<E> fallback) {
        if (!yaml.isList(path)) {
            if (yaml.contains(path)) {
                warn(path, "ожидается список, используется " + fallback);
            }
            return fallback;
        }
        List<E> values = new ArrayList<>();
        for (String raw : yaml.getStringList(path)) {
            E parsed = parseEnum(type, raw);
            if (parsed == null) {
                warn(path, "неизвестное значение \"" + raw + "\", пропущено");
                continue;
            }
            values.add(parsed);
        }
        if (values.isEmpty()) {
            warn(path, "в списке нет ни одного верного значения, используется " + fallback);
            return fallback;
        }
        return List.copyOf(values);
    }

    public @Nullable Sound sound(@NotNull String path) {
        String raw = string(join(path, SOUND_KEY), "").trim();
        if (raw.isEmpty()) {
            return null;
        }
        float volume = (float) Math.clamp(decimal(join(path, SOUND_VOLUME), MAX_VOLUME), MIN_VOLUME, MAX_VOLUME);
        float pitch = (float) Math.clamp(decimal(join(path, SOUND_PITCH), 1D), MIN_PITCH, MAX_PITCH);
        try {
            return Sound.sound(Key.key(raw.toLowerCase(Locale.ROOT)), Sound.Source.MASTER, volume, pitch);
        } catch (InvalidKeyException exception) {
            warn(join(path, SOUND_KEY), "неверный ключ звука \"" + raw + "\", звук отключён");
            return null;
        }
    }

    public void warn(@NotNull String path, @NotNull String problem) {
        Logger.warn("[" + name + "] " + path + ": " + problem);
    }

    public static @NotNull String join(@NotNull String parent, @NotNull String child) {
        return parent + PATH_SEPARATOR + child;
    }

    static @Nullable Double parseDecimal(@NotNull Object raw) {
        if (raw instanceof Number number) {
            return Double.isFinite(number.doubleValue()) ? number.doubleValue() : null;
        }
        if (!(raw instanceof String text)) {
            return null;
        }
        try {
            double value = Double.parseDouble(text.trim().replace(',', '.'));
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    static @Nullable Color parseColor(@NotNull String raw) {
        String hex = raw.trim();
        if (hex.startsWith(HEX_PREFIX)) {
            hex = hex.substring(HEX_PREFIX.length());
        }
        if (hex.length() == RGB_LENGTH) {
            hex = OPAQUE_ALPHA + hex;
        }
        if (hex.length() != ARGB_LENGTH) {
            return null;
        }
        try {
            return Color.fromARGB((int) Long.parseLong(hex, HEX_RADIX));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static <E extends Enum<E>> @Nullable E parseEnum(@NotNull Class<E> type, @NotNull String raw) {
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean isOutdated(@NotNull YamlConfiguration current, @NotNull YamlConfiguration defaults,
                                      @NotNull Set<String> preserved) {
        if (current.getInt(VERSION_KEY, 0) < defaults.getInt(VERSION_KEY, 0)) {
            return true;
        }
        for (String key : defaults.getKeys(true)) {
            if (!isInsidePreserved(key, preserved) && !current.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private static @NotNull YamlConfiguration merge(@NotNull YamlConfiguration current,
                                                    @NotNull YamlConfiguration defaults,
                                                    @NotNull Set<String> preserved) {
        YamlConfiguration merged = new YamlConfiguration();
        merged.options().parseComments(true);
        merged.options().width(Integer.MAX_VALUE);
        merged.options().setHeader(defaults.options().getHeader());
        for (String key : defaults.getKeys(true)) {
            if (key.equals(VERSION_KEY) || isInsidePreserved(key, preserved)) {
                continue;
            }
            if (preserved.contains(key)) {
                copyPreserved(current, defaults, merged, key);
            } else if (defaults.isConfigurationSection(key)) {
                merged.createSection(key);
            } else {
                boolean userValue = current.contains(key) && !current.isConfigurationSection(key);
                merged.set(key, userValue ? current.get(key) : defaults.get(key));
            }
            copyComments(defaults, merged, key);
        }
        merged.set(VERSION_KEY, defaults.getInt(VERSION_KEY, 0));
        copyComments(defaults, merged, VERSION_KEY);
        return merged;
    }

    private static boolean isInsidePreserved(@NotNull String key, @NotNull Set<String> preserved) {
        for (String root : preserved) {
            if (key.startsWith(root + PATH_SEPARATOR)) {
                return true;
            }
        }
        return false;
    }

    private static void copyPreserved(@NotNull YamlConfiguration current, @NotNull YamlConfiguration defaults,
                                      @NotNull YamlConfiguration merged, @NotNull String key) {
        YamlConfiguration source = current.contains(key) ? current : defaults;
        ConfigurationSection section = source.getConfigurationSection(key);
        if (section == null) {
            merged.set(key, source.get(key));
            return;
        }
        copySection(section, merged.createSection(key));
    }

    private static void copySection(@NotNull ConfigurationSection from, @NotNull ConfigurationSection to) {
        for (String key : from.getKeys(false)) {
            if (key.isEmpty()) {
                Logger.warn("Ключ с точкой в секции " + from.getCurrentPath() + " не поддерживается, запись пропущена");
                continue;
            }
            ConfigurationSection child = from.getConfigurationSection(key);
            if (child != null) {
                copySection(child, to.createSection(key));
            } else {
                to.set(key, from.get(key));
            }
            to.setComments(key, from.getComments(key));
            to.setInlineComments(key, from.getInlineComments(key));
        }
    }

    private static void copyComments(@NotNull YamlConfiguration from, @NotNull YamlConfiguration to,
                                     @NotNull String key) {
        to.setComments(key, from.getComments(key));
        to.setInlineComments(key, from.getInlineComments(key));
    }
}
