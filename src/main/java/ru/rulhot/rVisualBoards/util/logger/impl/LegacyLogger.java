package ru.rulhot.rVisualBoards.util.logger.impl;

import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rVisualBoards.util.logger.ILogger;

@RequiredArgsConstructor
public final class LegacyLogger implements ILogger {

    private static final @NotNull MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final @NotNull String INFO_PREFIX = "";
    private static final @NotNull String WARN_PREFIX = "<yellow>";
    private static final @NotNull String ERROR_PREFIX = "<red>";
    private static final @NotNull String DEBUG_PREFIX = "<gray>[debug] ";

    private final @NotNull ComponentLogger logger;

    @Override
    public void info(@NotNull String message) {
        logger.info(format(INFO_PREFIX, message));
    }

    @Override
    public void warn(@NotNull String message) {
        logger.warn(format(WARN_PREFIX, message));
    }

    @Override
    public void error(@NotNull String message) {
        logger.error(format(ERROR_PREFIX, message));
    }

    @Override
    public void error(@NotNull String message, @NotNull Throwable throwable) {
        logger.error(format(ERROR_PREFIX, message), throwable);
    }

    @Override
    public void debug(@NotNull String message) {
        logger.info(format(DEBUG_PREFIX, message));
    }

    private @NotNull Component format(@NotNull String prefix, @NotNull String message) {
        return MINI_MESSAGE.deserialize(prefix + message);
    }
}
