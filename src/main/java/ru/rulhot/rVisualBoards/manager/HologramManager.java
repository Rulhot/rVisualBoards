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
import ru.rulhot.rVisualBoards.hologram.HitboxRegistry;
import ru.rulhot.rVisualBoards.hologram.HologramContext;
import ru.rulhot.rVisualBoards.hologram.HologramKeys;
import ru.rulhot.rVisualBoards.hologram.TopSelections;
import ru.rulhot.rVisualBoards.model.BoardType;
import ru.rulhot.rVisualBoards.model.PlacedBoard;
import ru.rulhot.rVisualBoards.model.Settings;
import ru.rulhot.rVisualBoards.service.LeaderboardService;
import ru.rulhot.rVisualBoards.storage.BoardRepository;
import ru.rulhot.rVisualBoards.util.PlayerCommands;
import ru.rulhot.rVisualBoards.util.SchedulerUtil;
import ru.rulhot.rVisualBoards.util.logger.Logger;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public final class HologramManager {

    private static final @NotNull String NAME_TOKEN = "{name}";
    private static final @NotNull String PLACE_TOKEN = "{place}";
    private static final @NotNull String PLAYER_TOKEN = "{player}";
    private static final @NotNull String TOP_TOKEN = "{top}";

    private final @NotNull Plugin plugin;
    private final @NotNull SchedulerUtil scheduler;
    private final @NotNull HologramKeys keys;
    private final @NotNull ConfigManager config;
    private final @NotNull TopManager tops;
    private final @NotNull LeaderboardService leaderboards;
    private final @NotNull BoardRepository boards;

    private final @NotNull TopSelections selections = new TopSelections();
    private final @NotNull HitboxRegistry hitboxes = new HitboxRegistry();
    private final @NotNull Map<String, BoardHologram> holograms = new ConcurrentHashMap<>();
    private final @NotNull Map<UUID, Long> lastClicks = new ConcurrentHashMap<>();

    private volatile @Nullable Settings settings;

    public synchronized void start() {
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

    public synchronized void stop() {
        settings = null;
        for (BoardHologram hologram : holograms.values()) {
            hologram.stop();
        }
        holograms.clear();
        lastClicks.clear();
    }

    public synchronized void shutdown() {
        settings = null;
        for (BoardHologram hologram : holograms.values()) {
            hologram.shutdown();
        }
        holograms.clear();
        lastClicks.clear();
        selections.clear();
        hitboxes.clear();
    }

    public @NotNull HitboxRegistry hitboxes() {
        return hitboxes;
    }

    public int size() {
        return holograms.size();
    }

    public synchronized void respawn(@NotNull PlacedBoard board) {
        Settings current = settings;
        PlacedBoard latest = boards.find(board.id());
        if (current != null && latest != null) {
            spawn(current, latest);
        }
    }

    public synchronized void despawn(@NotNull String boardId) {
        BoardHologram previous = holograms.remove(boardId);
        if (previous != null) {
            previous.stop();
        }
    }

    public synchronized void loadWorld(@NotNull World world) {
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

    public synchronized void unloadWorld(@NotNull World world) {
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
        if (isCoolingDown(player.getUniqueId(), current)) {
            return;
        }
        lastClicks.put(player.getUniqueId(), System.currentTimeMillis());
        BoardHologram.Selected selected = hologram.select(player, action);
        if (selected == null) {
            return;
        }
        Sound sound = current.clicks().sound();
        if (selected.topChanged()) {
            Sound topSound = selected.top().switchSound();
            Sound switchSound = topSound != null ? topSound : current.sounds().switchTop();
            sound = switchSound != null ? switchSound : sound;
        }
        if (sound != null) {
            player.playSound(sound);
        }
    }

    public void clickRow(@NotNull Player player) {
        Settings current = settings;
        if (current == null || isCoolingDown(player.getUniqueId(), current)) {
            return;
        }
        BoardHologram.RowClick click = null;
        for (BoardHologram hologram : holograms.values()) {
            click = hologram.clickRow(player);
            if (click != null) {
                break;
            }
        }
        if (click == null) {
            return;
        }
        lastClicks.put(player.getUniqueId(), System.currentTimeMillis());
        String name = click.entry().name();
        if (!PlayerCommands.isSafeName(name) || !PlayerCommands.isSafeName(player.getName())) {
            Logger.debug("Клик по строке пропущен, странный ник: name=" + name + ", player=" + player.getName());
            return;
        }
        for (String line : click.top().rowClick()) {
            PlayerCommands.run(scheduler, player, line
                    .replace(NAME_TOKEN, name)
                    .replace(PLACE_TOKEN, String.valueOf(click.entry().place()))
                    .replace(PLAYER_TOKEN, player.getName())
                    .replace(TOP_TOKEN, click.top().id()));
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

    public void resend(@NotNull UUID viewerId) {
        for (BoardHologram hologram : holograms.values()) {
            hologram.forget(viewerId);
        }
    }

    private boolean isCoolingDown(@NotNull UUID viewerId, @NotNull Settings current) {
        Long last = lastClicks.get(viewerId);
        return last != null && System.currentTimeMillis() - last < current.clicks().cooldownMillis();
    }

    private void spawn(@NotNull Settings current, @NotNull PlacedBoard board) {
        try {
            spawnUnchecked(current, board);
        } catch (RuntimeException exception) {
            Logger.error("Табло '" + board.id() + "' не запущено из-за ошибки, остальные работают", exception);
        }
    }

    private void spawnUnchecked(@NotNull Settings current, @NotNull PlacedBoard board) {
        if (board.hidden()) {
            despawn(board.id());
            return;
        }
        World world = Bukkit.getWorld(board.world());
        if (world == null) {
            Logger.warn("Табло '" + board.id() + "': мир '" + board.world() + "' не загружен, пропускаю");
            return;
        }
        BoardType type = tops.find(board.type());
        if (type == null) {
            Logger.warn("Табло '" + board.id() + "': нет файла tops/" + board.type() + ".yml, пропускаю. "
                    + "Выбери другой вид через edit " + board.id() + " type");
            return;
        }
        HologramContext context = new HologramContext(plugin, scheduler, keys, selections, leaderboards, current,
                type.layout(), new FontMetrics(current.advanced().charWidths()), hitboxes);
        BoardHologram hologram = new BoardHologram(context, board, world, type.tops());
        BoardHologram previous = holograms.put(board.id(), hologram);
        if (previous != null) {
            previous.stop();
        }
        hologram.start();
    }
}
