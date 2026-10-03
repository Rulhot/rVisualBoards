package ru.rulhot.rVisualBoards.util.logger;

import lombok.experimental.UtilityClass;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rVisualBoards.util.logger.impl.LegacyLogger;

@UtilityClass
public class Logger {

    private volatile @Nullable ILogger delegate;
    private volatile boolean debugEnabled;

    public void setup(@NotNull Plugin plugin) {
        setup(new LegacyLogger(plugin.getComponentLogger()));
    }

    public void setup(@NotNull ILogger logger) {
        delegate = logger;
    }

    public void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }

    public void info(@NotNull String message) {
        ILogger current = delegate;
        if (current != null) {
            current.info(message);
        }
    }

    public void warn(@NotNull String message) {
        ILogger current = delegate;
        if (current != null) {
            current.warn(message);
        }
    }

    public void error(@NotNull String message) {
        ILogger current = delegate;
        if (current != null) {
            current.error(message);
        }
    }

    public void error(@NotNull String message, @NotNull Throwable throwable) {
        ILogger current = delegate;
        if (current != null) {
            current.error(message, throwable);
        }
    }

    public void debug(@NotNull String message) {
        ILogger current = delegate;
        if (current != null && debugEnabled) {
            current.debug(message);
        }
    }
}
