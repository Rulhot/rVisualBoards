package ru.rulhot.rVisualBoards.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rVisualBoards.util.logger.Logger;

import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public final class SchedulerUtil {

    private static final long MILLIS_PER_TICK = 50L;

    private final @NotNull Plugin plugin;

    public void runAsync(@NotNull Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, handle -> guarded(task));
    }

    public @NotNull Task runAsyncTimer(@NotNull Runnable task, long delayTicks, long periodTicks) {
        return new Task(Bukkit.getAsyncScheduler().runAtFixedRate(
                plugin,
                handle -> guarded(task),
                ticks(delayTicks) * MILLIS_PER_TICK,
                ticks(periodTicks) * MILLIS_PER_TICK,
                TimeUnit.MILLISECONDS));
    }

    public void runGlobal(@NotNull Runnable task) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> guarded(task));
    }

    public void runAt(@NotNull Location location, @NotNull Runnable task) {
        Bukkit.getRegionScheduler().execute(plugin, location, () -> guarded(task));
    }

    public void runAtLater(@NotNull Location location, @NotNull Runnable task, long delayTicks) {
        Bukkit.getRegionScheduler().runDelayed(plugin, location, handle -> guarded(task), ticks(delayTicks));
    }

    public @NotNull Task runAtTimer(@NotNull Location location, @NotNull Runnable task, long delayTicks, long periodTicks) {
        return new Task(Bukkit.getRegionScheduler().runAtFixedRate(
                plugin,
                location,
                handle -> guarded(task),
                ticks(delayTicks),
                ticks(periodTicks)));
    }

    public void runFor(@NotNull Entity entity, @NotNull Runnable task) {
        entity.getScheduler().run(plugin, handle -> guarded(task), null);
    }

    public void runFor(@NotNull CommandSender sender, @NotNull Runnable task) {
        if (sender instanceof Entity entity) {
            runFor(entity, task);
            return;
        }
        runGlobal(task);
    }

    private static long ticks(long value) {
        return Math.max(1L, value);
    }

    private static void guarded(@NotNull Runnable task) {
        try {
            task.run();
        } catch (RuntimeException exception) {
            Logger.error("Ошибка в задаче планировщика", exception);
        }
    }

    public static final class Task {

        private final @NotNull ScheduledTask handle;

        private Task(@NotNull ScheduledTask handle) {
            this.handle = handle;
        }

        public void cancel() {
            handle.cancel();
        }
    }
}
