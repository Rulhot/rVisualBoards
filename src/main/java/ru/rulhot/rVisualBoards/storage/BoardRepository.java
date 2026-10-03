package ru.rulhot.rVisualBoards.storage;

import lombok.RequiredArgsConstructor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rVisualBoards.model.PlacedBoard;
import ru.rulhot.rVisualBoards.util.SchedulerUtil;
import ru.rulhot.rVisualBoards.util.logger.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public final class BoardRepository {

    private static final @NotNull String FILE = "data/boards.yml";
    private static final @NotNull String ROOT = "boards";
    private static final @NotNull String TYPE = "type";
    private static final @NotNull String WORLD = "world";
    private static final @NotNull String X = "x";
    private static final @NotNull String Y = "y";
    private static final @NotNull String Z = "z";
    private static final @NotNull String YAW = "yaw";
    private static final @NotNull String SCALE = "scale";
    private static final @NotNull String DEFAULT_TOP = "default-top";

    private final @NotNull JavaPlugin plugin;
    private final @NotNull SchedulerUtil scheduler;

    private final @NotNull Map<String, PlacedBoard> boards = new ConcurrentHashMap<>();
    private final @NotNull Object fileLock = new Object();

    private volatile boolean loaded;

    public boolean isLoaded() {
        return loaded;
    }

    public boolean load() {
        synchronized (fileLock) {
            File file = file();
            if (file.exists()) {
                YamlConfiguration yaml = read(file);
                if (yaml == null) {
                    return false;
                }
                readInto(yaml.getConfigurationSection(ROOT));
            }
            loaded = true;
            return true;
        }
    }

    private @Nullable YamlConfiguration read(@NotNull File file) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
            return yaml;
        } catch (InvalidConfigurationException exception) {
            Logger.error("Ошибка в " + FILE + ", табло не загружены: " + exception.getMessage());
            return null;
        } catch (IOException exception) {
            Logger.error("Не удалось прочитать " + FILE + ", табло не загружены", exception);
            return null;
        }
    }

    public @Nullable PlacedBoard find(@NotNull String id) {
        return boards.get(id);
    }

    public @NotNull List<PlacedBoard> all() {
        return boards.values().stream()
                .sorted(Comparator.comparing(PlacedBoard::id))
                .toList();
    }

    public @NotNull List<String> ids() {
        return boards.keySet().stream().sorted().toList();
    }

    public boolean insert(@NotNull PlacedBoard board) {
        if (boards.putIfAbsent(board.id(), board) != null) {
            return false;
        }
        saveAsync();
        return true;
    }

    public boolean replace(@NotNull PlacedBoard board) {
        if (boards.replace(board.id(), board) == null) {
            return false;
        }
        saveAsync();
        return true;
    }

    public @Nullable PlacedBoard delete(@NotNull String id) {
        PlacedBoard removed = boards.remove(id);
        if (removed != null) {
            saveAsync();
        }
        return removed;
    }

    public void flush() {
        write();
    }

    private void saveAsync() {
        scheduler.runAsync(this::write);
    }

    private void readInto(@Nullable ConfigurationSection root) {
        boards.clear();
        if (root == null) {
            return;
        }
        for (String rawId : root.getKeys(false)) {
            String id = PlacedBoard.normalizeId(rawId);
            ConfigurationSection section = root.getConfigurationSection(rawId);
            String world = section == null ? null : section.getString(WORLD);
            if (!PlacedBoard.isValidId(id) || section == null || world == null || world.isBlank()) {
                Logger.warn("[" + FILE + "] запись повреждена (неверный ID или нет мира), пропущена: board=" + rawId);
                continue;
            }
            String defaultTop = section.getString(DEFAULT_TOP);
            boards.put(id, new PlacedBoard(
                    id,
                    section.getString(TYPE, "").toLowerCase(Locale.ROOT),
                    world,
                    section.getDouble(X),
                    section.getDouble(Y),
                    section.getDouble(Z),
                    (float) section.getDouble(YAW),
                    readScale(id, section),
                    defaultTop == null ? null : defaultTop.toLowerCase(Locale.ROOT)));
        }
    }

    private static double readScale(@NotNull String id, @NotNull ConfigurationSection section) {
        double scale = section.getDouble(SCALE, PlacedBoard.DEFAULT_SCALE);
        if (PlacedBoard.isValidScale(scale)) {
            return scale;
        }
        Logger.warn("[" + FILE + "] размер вне диапазона " + PlacedBoard.MIN_SCALE + ".." + PlacedBoard.MAX_SCALE
                + ", используется " + PlacedBoard.DEFAULT_SCALE + ": board=" + id);
        return PlacedBoard.DEFAULT_SCALE;
    }

    private void write() {
        synchronized (fileLock) {
            if (!loaded) {
                return;
            }
            YamlConfiguration yaml = new YamlConfiguration();
            ConfigurationSection root = yaml.createSection(ROOT);
            for (PlacedBoard board : all()) {
                ConfigurationSection section = root.createSection(board.id());
                section.set(TYPE, board.type());
                section.set(WORLD, board.world());
                section.set(X, board.x());
                section.set(Y, board.y());
                section.set(Z, board.z());
                section.set(YAW, (double) board.yaw());
                section.set(SCALE, board.scale());
                section.set(DEFAULT_TOP, board.defaultTop());
            }
            File file = file();
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                Logger.error("Не удалось создать папку " + parent.getPath());
                return;
            }
            try {
                yaml.save(file);
            } catch (IOException exception) {
                Logger.error("Не удалось сохранить " + FILE, exception);
            }
        }
    }

    private @NotNull File file() {
        return new File(plugin.getDataFolder(), FILE);
    }
}
