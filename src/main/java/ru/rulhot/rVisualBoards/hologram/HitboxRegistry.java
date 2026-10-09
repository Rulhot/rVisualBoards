package ru.rulhot.rVisualBoards.hologram;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HitboxRegistry {

    private final @NotNull Map<Integer, Target> targets = new ConcurrentHashMap<>();

    void register(int entityId, @NotNull Target target) {
        targets.put(entityId, target);
    }

    void remove(int entityId) {
        targets.remove(entityId);
    }

    public @Nullable Target find(int entityId) {
        return targets.get(entityId);
    }

    public void clear() {
        targets.clear();
    }

    public record Target(@NotNull String boardId, @NotNull ButtonAction action, @NotNull UUID viewerId) {
    }
}
