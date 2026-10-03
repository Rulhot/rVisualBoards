package ru.rulhot.rVisualBoards.hologram;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rVisualBoards.model.TopDefinition.Period;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TopSelections {

    private final @NotNull Map<UUID, Map<String, Selection>> selections = new ConcurrentHashMap<>();

    @Nullable Selection get(@NotNull UUID viewerId, @NotNull String boardId) {
        Map<String, Selection> boards = selections.get(viewerId);
        return boards == null ? null : boards.get(boardId);
    }

    void put(@NotNull UUID viewerId, @NotNull String boardId, @NotNull String topId, @NotNull Period period) {
        selections.computeIfAbsent(viewerId, key -> new ConcurrentHashMap<>()).put(boardId, new Selection(topId, period));
    }

    public void forget(@NotNull UUID viewerId) {
        selections.remove(viewerId);
    }

    public void clear() {
        selections.clear();
    }

    record Selection(@NotNull String topId, @NotNull Period period) {
    }
}
