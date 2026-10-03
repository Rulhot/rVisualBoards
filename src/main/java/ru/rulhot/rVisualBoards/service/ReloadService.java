package ru.rulhot.rVisualBoards.service;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rVisualBoards.manager.ConfigManager;
import ru.rulhot.rVisualBoards.manager.HologramManager;
import ru.rulhot.rVisualBoards.manager.TopManager;
import ru.rulhot.rVisualBoards.model.BoardType;
import ru.rulhot.rVisualBoards.model.Settings;
import ru.rulhot.rVisualBoards.model.TopDefinition;
import ru.rulhot.rVisualBoards.storage.BoardRepository;
import ru.rulhot.rVisualBoards.util.SchedulerUtil;
import ru.rulhot.rVisualBoards.util.logger.Logger;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@RequiredArgsConstructor
public final class ReloadService {

    private final @NotNull SchedulerUtil scheduler;
    private final @NotNull ConfigManager config;
    private final @NotNull TopManager tops;
    private final @NotNull BoardRepository boards;
    private final @NotNull LeaderboardService leaderboards;
    private final @NotNull HologramManager holograms;

    private final @NotNull AtomicBoolean reloading = new AtomicBoolean();
    private volatile boolean shutdown;

    public void start() {
        load().thenAccept(result -> {
            if (result instanceof Result.Failed) {
                Logger.error("Табло не запущены из-за ошибки конфигурации: исправьте её и выполните /rvb reload");
            }
        });
    }

    public @NotNull CompletableFuture<Result> reload() {
        return load();
    }

    public void shutdown() {
        shutdown = true;
        holograms.shutdown();
        leaderboards.stop();
        boards.flush();
    }

    private @NotNull CompletableFuture<Result> load() {
        if (!reloading.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(new Result.Busy());
        }
        CompletableFuture<Result> result = new CompletableFuture<>();
        scheduler.runAsync(() -> {
            Loaded loaded = read();
            if (loaded == null || shutdown) {
                finish(result, new Result.Failed());
                return;
            }
            scheduler.runGlobal(() -> {
                try {
                    finish(result, apply(loaded));
                } catch (RuntimeException exception) {
                    Logger.error("Не удалось применить конфигурацию", exception);
                    finish(result, new Result.Failed());
                }
            });
        });
        return result;
    }

    private @Nullable Loaded read() {
        try {
            if (!boards.isLoaded() && !boards.load()) {
                return null;
            }
            ConfigManager.Snapshot snapshot = config.read();
            List<BoardType> types = tops.read();
            if (snapshot == null || types == null) {
                return null;
            }
            return new Loaded(snapshot, types);
        } catch (RuntimeException exception) {
            Logger.error("Не удалось загрузить конфигурацию", exception);
            return null;
        }
    }

    private @NotNull Result apply(@NotNull Loaded loaded) {
        if (shutdown) {
            return new Result.Failed();
        }
        holograms.stop();
        leaderboards.stop();
        config.apply(loaded.config());
        tops.apply(loaded.types());
        Settings settings = loaded.config().settings();
        Logger.setDebugEnabled(settings.debug());
        List<TopDefinition> allTops = loaded.types().stream().flatMap(type -> type.tops().stream()).toList();
        int places = loaded.types().stream()
                .mapToInt(type -> settings.scroll().loadedPlaces(type.layout().table().rows()))
                .max()
                .orElse(0);
        leaderboards.start(settings, allTops, places);
        holograms.start();
        Logger.debug("Конфигурация применена: boards=" + holograms.size() + ", types=" + loaded.types().size());
        return new Result.Success(holograms.size(), loaded.types().size());
    }

    private void finish(@NotNull CompletableFuture<Result> future, @NotNull Result result) {
        reloading.set(false);
        future.complete(result);
    }

    public sealed interface Result {

        record Success(int boards, int types) implements Result {
        }

        record Busy() implements Result {
        }

        record Failed() implements Result {
        }
    }

    private record Loaded(@NotNull ConfigManager.Snapshot config, @NotNull List<BoardType> types) {
    }
}
