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
import ru.rulhot.rVisualBoards.model.BoardLayout.Clicks.MouseKey;
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
    private static final long INTERACT_SWING_MILLIS = 500L;

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
    private final @NotNull Map<UUID, Long> interacts = new ConcurrentHashMap<>();

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
        interacts.clear();
    }

    public synchronized void shutdown() {
        settings = null;
        for (BoardHologram hologram : holograms.values()) {
            hologram.shutdown();
        }
        holograms.clear();
        lastClicks.clear();
        interacts.clear();
        selections.clear();
        hitboxes.clear();
    }

    public @NotNull HitboxRegistry hitboxes() {
        return hitboxes;
    }

    public void preview(String id, UUID viewer, BoardHologram.Preview mode, java.util.function.Consumer<Boolean> result) {
        BoardHologram board = holograms.get(id);
        if (board == null) result.accept(false);
        else board.preview(viewer, mode, result);
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

    public void press(@NotNull Player player, @NotNull String boardId, @NotNull ButtonAction action,
                      @NotNull MouseKey pressed) {
        Settings current = settings;
        BoardHologram hologram = holograms.get(boardId);
        if (current == null || hologram == null || !hologram.accepts(action, pressed)) {
            return;
        }
        if (isCoolingDown(player.getUniqueId(), current)) {
            return;
        }
        lastClicks.put(player.getUniqueId(), System.currentTimeMillis());
        BoardHologram.Selected selected = hologram.select(player, action);
        if (selected != null) {
            playSelectSound(player, current, selected);
        }
    }

    public void markInteract(@NotNull UUID viewerId) {
        interacts.put(viewerId, System.currentTimeMillis());
    }

    public void swing(@NotNull Player player) {
        Settings current = settings;
        UUID viewerId = player.getUniqueId();
        Long interacted = interacts.remove(viewerId);
        if (interacted != null && System.currentTimeMillis() - interacted < INTERACT_SWING_MILLIS) {
            return;
        }
        if (current == null || isCoolingDown(viewerId, current)) {
            return;
        }
        for (BoardHologram hologram : holograms.values()) {
            BoardHologram.RowClick click = hologram.clickRow(player);
            if (click != null) {
                clickRow(player, current, click);
                return;
            }
        }
        for (BoardHologram hologram : holograms.values()) {
            BoardHologram.Selected selected = hologram.flipPeriod(player);
            if (selected != null) {
                lastClicks.put(viewerId, System.currentTimeMillis());
                playSelectSound(player, current, selected);
                return;
            }
        }
    }

    private static void playSelectSound(@NotNull Player player, @NotNull Settings current,
                                        @NotNull BoardHologram.Selected selected) {
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

    private void clickRow(@NotNull Player player, @NotNull Settings current, @NotNull BoardHologram.RowClick click) {
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

    public void scroll(@NotNull Player player, int direction) {
        Settings current = settings;
        if (current == null || !current.scroll().enabled()) {
            return;
        }
        int delta = direction * current.scroll().step();
        for (BoardHologram hologram : holograms.values()) {
            if (hologram.scroll(player, delta)) {
                return;
            }
        }
    }

    public void forget(@NotNull UUID viewerId) {
        selections.forget(viewerId);
        lastClicks.remove(viewerId);
        interacts.remove(viewerId);
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
