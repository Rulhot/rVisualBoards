package ru.rulhot.rVisualBoards.hologram;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rVisualBoards.model.BoardLayout;
import ru.rulhot.rVisualBoards.model.Settings;
import ru.rulhot.rVisualBoards.service.LeaderboardService;
import ru.rulhot.rVisualBoards.util.SchedulerUtil;

public record HologramContext(
        @NotNull Plugin plugin,
        @NotNull SchedulerUtil scheduler,
        @NotNull HologramKeys keys,
        @NotNull TopSelections selections,
        @NotNull LeaderboardService leaderboards,
        @NotNull Settings settings,
        @NotNull BoardLayout layout,
        @NotNull FontMetrics fontMetrics
) {
}
