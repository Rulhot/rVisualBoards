package ru.rulhot.rVisualBoards.util;

import lombok.experimental.UtilityClass;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

@UtilityClass
public class PlayerCommands {

    private final @NotNull String CONSOLE_PREFIX = "console:";
    private final @NotNull String PLAYER_PREFIX = "player:";
    private final @NotNull String SLASH = "/";
    private final @NotNull Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,32}");

    public boolean isSafeName(@NotNull String name) {
        return SAFE_NAME.matcher(name).matches();
    }

    public void run(@NotNull SchedulerUtil scheduler, @NotNull Player player, @NotNull String line) {
        String trimmed = line.trim();
        if (trimmed.regionMatches(true, 0, CONSOLE_PREFIX, 0, CONSOLE_PREFIX.length())) {
            runConsole(scheduler, trimmed.substring(CONSOLE_PREFIX.length()));
            return;
        }
        if (trimmed.regionMatches(true, 0, PLAYER_PREFIX, 0, PLAYER_PREFIX.length())) {
            trimmed = trimmed.substring(PLAYER_PREFIX.length());
        }
        String command = strip(trimmed);
        if (!command.isEmpty()) {
            player.performCommand(command);
        }
    }

    public void runConsole(@NotNull SchedulerUtil scheduler, @NotNull String line) {
        String command = strip(line);
        if (!command.isEmpty()) {
            scheduler.runGlobal(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
        }
    }

    private @NotNull String strip(@NotNull String line) {
        String trimmed = line.trim();
        return trimmed.startsWith(SLASH) ? trimmed.substring(SLASH.length()) : trimmed;
    }
}
