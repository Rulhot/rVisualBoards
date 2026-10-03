package ru.rulhot.rVisualBoards.service;

import lombok.RequiredArgsConstructor;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rVisualBoards.model.LeaderboardSnapshot;
import ru.rulhot.rVisualBoards.model.LeaderboardSnapshot.Entry;
import ru.rulhot.rVisualBoards.model.LeaderboardSnapshot.Key;
import ru.rulhot.rVisualBoards.model.LeaderboardSnapshot.Standing;
import ru.rulhot.rVisualBoards.model.Settings;
import ru.rulhot.rVisualBoards.model.Settings.Placeholders;
import ru.rulhot.rVisualBoards.model.TopDefinition;
import ru.rulhot.rVisualBoards.model.TopDefinition.Period;
import ru.rulhot.rVisualBoards.util.SchedulerUtil;
import ru.rulhot.rVisualBoards.util.logger.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RequiredArgsConstructor
public final class LeaderboardService {

    private static final @NotNull String BOARD_TOKEN = "{board}";
    private static final @NotNull String TYPE_TOKEN = "{type}";
    private static final @NotNull String PLACE_TOKEN = "{place}";
    private static final char PLACEHOLDER_MARK = '%';
    private static final long FIRST_REFRESH_DELAY_TICKS = 1L;
    private static final int PERSONAL_PLACE = 0;
    private static final long MIN_ACTIVE_MILLIS = 60_000L;
    private static final long MILLIS_PER_SECOND = 1_000L;
    private static final int ACTIVE_INTERVALS = 3;

    private final @NotNull SchedulerUtil scheduler;

    private final @NotNull Map<Key, LeaderboardSnapshot> snapshots = new ConcurrentHashMap<>();
    private final @NotNull Map<UUID, Map<Key, Standing>> standings = new ConcurrentHashMap<>();
    private final @NotNull Map<UUID, Demand> demands = new ConcurrentHashMap<>();
    private final @NotNull Map<Key, Long> requested = new ConcurrentHashMap<>();
    private final @NotNull Set<Key> loading = ConcurrentHashMap.newKeySet();
    private final @NotNull AtomicLong versions = new AtomicLong();

    private volatile @Nullable Source source;
    private volatile @Nullable SchedulerUtil.Task task;

    public void start(@NotNull Settings settings, @NotNull List<TopDefinition> tops, int places) {
        stop();
        Set<String> boards = new HashSet<>();
        Set<Key> defaults = new HashSet<>();
        for (TopDefinition top : tops) {
            boards.add(top.board());
            defaults.add(new Key(top.board(), top.defaultPeriod()));
        }
        long activeMillis = Math.max(MIN_ACTIVE_MILLIS,
                settings.updateIntervalSeconds() * MILLIS_PER_SECOND * ACTIVE_INTERVALS);
        source = new Source(settings.advanced().placeholders(), Set.copyOf(boards), Set.copyOf(defaults), places,
                activeMillis);
        task = scheduler.runAsyncTimer(this::refresh, FIRST_REFRESH_DELAY_TICKS, settings.updateIntervalTicks());
    }

    public void stop() {
        source = null;
        SchedulerUtil.Task current = task;
        task = null;
        if (current != null) {
            current.cancel();
        }
        snapshots.clear();
        standings.clear();
        demands.clear();
        requested.clear();
    }

    public @Nullable LeaderboardSnapshot snapshot(@NotNull String board, @NotNull Period period) {
        return snapshots.get(new Key(board, period));
    }

    public @Nullable Standing standing(@NotNull UUID viewerId, @NotNull String board, @NotNull Period period) {
        Map<Key, Standing> entries = standings.get(viewerId);
        return entries == null ? null : entries.get(new Key(board, period));
    }

    public void request(@NotNull String board, @NotNull Period period) {
        Key key = new Key(board, period);
        requested.put(key, System.currentTimeMillis());
        if (snapshots.containsKey(key) || !loading.add(key)) {
            return;
        }
        scheduler.runAsync(() -> {
            try {
                Source current = source;
                if (current != null && current.boards().contains(board)) {
                    refreshBoard(current, key);
                }
            } finally {
                loading.remove(key);
            }
        });
    }

    public void watch(@NotNull UUID viewerId, @NotNull String viewerName, @NotNull String board,
                      @NotNull Period period) {
        demands.computeIfAbsent(viewerId, id -> new Demand(viewerName, ConcurrentHashMap.newKeySet()))
                .keys().add(new Key(board, period));
    }

    public void forget(@NotNull UUID viewerId) {
        standings.remove(viewerId);
        demands.remove(viewerId);
    }

    public @NotNull CompletableFuture<Void> fetchStanding(@NotNull UUID viewerId, @NotNull String viewerName,
                                                          @NotNull String board, @NotNull Period period) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (source == null) {
            future.complete(null);
            return future;
        }
        scheduler.runAsync(() -> {
            try {
                Source current = source;
                if (current != null && current.boards().contains(board)) {
                    loadStanding(current, viewerId, viewerName, new Key(board, period));
                }
            } finally {
                future.complete(null);
            }
        });
        return future;
    }

    private void refresh() {
        Source current = source;
        if (current == null) {
            return;
        }
        for (Key key : current.defaults()) {
            refreshBoard(current, key);
        }
        refreshRequested(current);
        refreshStandings(current);
    }

    private void refreshRequested(@NotNull Source current) {
        long now = System.currentTimeMillis();
        for (Map.Entry<Key, Long> entry : requested.entrySet()) {
            Key key = entry.getKey();
            if (!current.boards().contains(key.id()) || current.defaults().contains(key)) {
                continue;
            }
            if (now - entry.getValue() > current.activeMillis()) {
                requested.remove(key, entry.getValue());
                snapshots.remove(key);
                continue;
            }
            refreshBoard(current, key);
        }
    }

    private void refreshStandings(@NotNull Source current) {
        for (UUID viewerId : List.copyOf(demands.keySet())) {
            Demand demand = demands.remove(viewerId);
            if (demand == null) {
                continue;
            }
            if (Bukkit.getPlayer(viewerId) == null) {
                standings.remove(viewerId);
                continue;
            }
            for (Key key : demand.keys()) {
                if (current.boards().contains(key.id())) {
                    loadStanding(current, viewerId, demand.viewerName(), key);
                }
            }
        }
    }

    private void refreshBoard(@NotNull Source current, @NotNull Key key) {
        Placeholders placeholders = current.placeholders();
        List<Entry> entries = new ArrayList<>();
        boolean complete = false;
        for (int place = 1; place <= current.places(); place++) {
            String name = resolve(null, fill(placeholders.name(), key, place)).trim();
            if (name.isEmpty() || name.equals(placeholders.emptyName()) || isUnresolved(name)) {
                complete = true;
                break;
            }
            String value = resolve(null, fill(placeholders.value(), key, place)).trim();
            String prefix = placeholders.prefix().isBlank()
                    ? ""
                    : resolve(null, fill(placeholders.prefix(), key, place));
            entries.add(new Entry(place, isUnresolved(prefix) ? "" : prefix, name, value));
        }
        LeaderboardSnapshot previous = snapshots.get(key);
        if (previous != null && previous.complete() == complete && previous.entries().equals(entries)) {
            return;
        }
        snapshots.put(key, new LeaderboardSnapshot(versions.incrementAndGet(), entries, complete));
        Logger.debug("Обновлён борд " + key.id() + " за период " + key.period().key());
    }

    private void loadStanding(@NotNull Source current, @NotNull UUID viewerId, @NotNull String viewerName,
                              @NotNull Key key) {
        Placeholders placeholders = current.placeholders();
        OfflinePlayer player = Bukkit.getOfflinePlayer(viewerId);
        String rawPosition = resolve(player, fill(placeholders.position(), key, PERSONAL_PLACE));
        String value = resolve(player, fill(placeholders.personalValue(), key, PERSONAL_PLACE)).trim();
        boolean known = !value.isEmpty() && !value.equals(placeholders.emptyName()) && !isUnresolved(value);
        int parsed = LeaderboardSnapshot.parsePosition(rawPosition);
        int position = known && isConfirmed(key, parsed, viewerName) ? parsed : LeaderboardSnapshot.UNRANKED;
        Standing standing = new Standing(String.valueOf(position), known ? value : Standing.UNKNOWN.value(),
                position > LeaderboardSnapshot.UNRANKED);
        standings.computeIfAbsent(viewerId, id -> new ConcurrentHashMap<>()).put(key, standing);
    }

    private boolean isConfirmed(@NotNull Key key, int position, @NotNull String viewerName) {
        LeaderboardSnapshot snapshot = snapshots.get(key);
        if (snapshot == null) {
            return position > LeaderboardSnapshot.UNRANKED;
        }
        return snapshot.confirmsPlace(position, viewerName);
    }

    private boolean isUnresolved(@NotNull String text) {
        return text.indexOf(PLACEHOLDER_MARK) >= 0;
    }

    private @NotNull String fill(@NotNull String template, @NotNull Key key, int place) {
        return template
                .replace(BOARD_TOKEN, key.id())
                .replace(TYPE_TOKEN, key.period().key())
                .replace(PLACE_TOKEN, String.valueOf(place));
    }

    private @NotNull String resolve(@Nullable OfflinePlayer player, @NotNull String text) {
        try {
            String result = PlaceholderAPI.setPlaceholders(player, text);
            return result == null ? "" : result;
        } catch (RuntimeException exception) {
            Logger.debug("Ошибка PlaceholderAPI для '" + text + "': " + exception.getMessage());
            return "";
        }
    }

    private record Source(
            @NotNull Placeholders placeholders,
            @NotNull Set<String> boards,
            @NotNull Set<Key> defaults,
            int places,
            long activeMillis
    ) {
    }

    private record Demand(@NotNull String viewerName, @NotNull Set<Key> keys) {
    }
}
