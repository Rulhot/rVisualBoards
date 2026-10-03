package ru.rulhot.rVisualBoards.manager;

import lombok.RequiredArgsConstructor;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rVisualBoards.hologram.BoardHologram;
import ru.rulhot.rVisualBoards.hologram.ButtonAction;
import ru.rulhot.rVisualBoards.hologram.FontMetrics;
import ru.rulhot.rVisualBoards.hologram.HologramContext;
import ru.rulhot.rVisualBoards.hologram.HologramKeys;
import ru.rulhot.rVisualBoards.hologram.TopSelections;
import ru.rulhot.rVisualBoards.model.BoardType;
import ru.rulhot.rVisualBoards.model.PlacedBoard;
import ru.rulhot.rVisualBoards.model.Settings;
import ru.rulhot.rVisualBoards.service.LeaderboardService;
import ru.rulhot.rVisualBoards.storage.BoardRepository;
import ru.rulhot.rVisualBoards.util.SchedulerUtil;
import ru.rulhot.rVisualBoards.util.logger.Logger;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public final class HologramManager {

    private final @NotNull Plugin plugin;
    private final @NotNull SchedulerUtil scheduler;
    private final @NotNull HologramKeys keys;
    private final @NotNull ConfigManager config;
    private final @NotNull TopManager tops;
    private final @NotNull LeaderboardService leaderboards;
    private final @NotNull BoardRepository boards;

    private final @NotNull TopSelections selections = new TopSelections();
    private final @NotNull Map<String, BoardHologram> holograms = new ConcurrentHashMap<>();
    private final @NotNull Map<UUID, Long> lastClicks = new ConcurrentHashMap<>();

    private volatile @Nullable Settings settings;

    public void start() {
        Settings current = config.settings();
        if (current == null) {
            Logger.error("Табло не запущены: конфигурация ещё не загружена");
            return;
        }
        settings = current;
        for (PlacedBoard board : boards.all()) {
            spawn(current, board);
        }
        Logger.debug("Табло запущены: boards=" + holograms.size());
    }

    public void stop() {
        settings = null;
        for (BoardHologram hologram : holograms.values()) {
            hologram.stop();
        }
        holograms.clear();
        lastClicks.clear();
    }

    public void shutdown() {
        settings = null;
        for (BoardHologram hologram : holograms.values()) {
            hologram.shutdown();
        }
        holograms.clear();
        lastClicks.clear();
        selections.clear();
    }

    public int size() {
        return holograms.size();
    }

    public void respawn(@NotNull PlacedBoard board) {
        Settings current = settings;
        if (current != null) {
            spawn(current, board);
        }
    }

    public void despawn(@NotNull String boardId) {
        BoardHologram previous = holograms.remove(boardId);
        if (previous != null) {
            previous.stop();
        }
    }

    public void loadWorld(@NotNull World world) {
        Settings current = settings;
        if (current == null) {
            return;
        }
        for (PlacedBoard board : boards.all()) {
            if (board.world().equals(world.getName()) && !holograms.containsKey(board.id())) {
                spawn(current, board);
            }
        }
    }

    public void unloadWorld(@NotNull World world) {
        for (BoardHologram hologram : List.copyOf(holograms.values())) {
            if (hologram.worldName().equals(world.getName()) && holograms.remove(hologram.id(), hologram)) {
                hologram.stop();
            }
        }
    }

    public void press(@NotNull Player player, @NotNull String boardId, @NotNull ButtonAction action) {
        Settings current = settings;
        BoardHologram hologram = holograms.get(boardId);
        if (current == null || hologram == null) {
            return;
        }
        UUID viewerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastClicks.get(viewerId);
        if (last != null && now - last < current.clicks().cooldownMillis()) {
            return;
        }
        lastClicks.put(viewerId, now);
        if (!hologram.select(player, action)) {
            return;
        }
        Sound sound = current.clicks().sound();
        if (sound != null) {
            player.playSound(sound);
        }
    }

    public boolean scroll(@NotNull Player player, int direction) {
        Settings current = settings;
        if (current == null || !current.scroll().enabled()) {
            return false;
        }
        int delta = direction * current.scroll().step();
        for (BoardHologram hologram : holograms.values()) {
            if (hologram.scroll(player, delta)) {
                return true;
            }
        }
        return false;
    }

    public void forget(@NotNull UUID viewerId) {
        selections.forget(viewerId);
        lastClicks.remove(viewerId);
        for (BoardHologram hologram : holograms.values()) {
            hologram.forget(viewerId);
        }
        leaderboards.forget(viewerId);
    }

    private void spawn(@NotNull Settings current, @NotNull PlacedBoard board) {
        World world = Bukkit.getWorld(board.world());
        if (world == null) {
            Logger.warn("Табло пропущено, мир не загружен: board=" + board.id() + ", world=" + board.world());
            return;
        }
        BoardType type = tops.find(board.type());
        if (type == null) {
            Logger.warn("Табло пропущено, нет файла tops/" + board.type() + ".yml: board=" + board.id()
                    + " (выберите другой: /rvb edit " + board.id() + " type <вид>)");
            return;
        }
        HologramContext context = new HologramContext(plugin, scheduler, keys, selections, leaderboards, current,
                type.layout(), new FontMetrics(current.advanced().charWidths()));
        BoardHologram hologram = new BoardHologram(context, board, world, type.tops());
        BoardHologram previous = holograms.put(board.id(), hologram);
        if (previous != null) {
            previous.stop();
        }
        hologram.start();
    }
}
