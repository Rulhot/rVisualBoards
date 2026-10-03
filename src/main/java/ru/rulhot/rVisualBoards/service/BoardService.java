package ru.rulhot.rVisualBoards.service;

import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rVisualBoards.manager.ConfigManager;
import ru.rulhot.rVisualBoards.manager.HologramManager;
import ru.rulhot.rVisualBoards.model.PlacedBoard;
import ru.rulhot.rVisualBoards.model.Settings;
import ru.rulhot.rVisualBoards.storage.BoardRepository;

import java.util.List;
import java.util.function.UnaryOperator;

@RequiredArgsConstructor
public final class BoardService {

    private final @NotNull ConfigManager config;
    private final @NotNull BoardRepository repository;
    private final @NotNull HologramManager holograms;

    public @NotNull CreateResult create(@NotNull String id, @NotNull String type, @NotNull Player player) {
        if (!PlacedBoard.isValidId(id)) {
            return new CreateResult.InvalidId();
        }
        Location location = player.getLocation();
        PlacedBoard board = PlacedBoard.create(id, type, player.getWorld().getName(), location.getX(),
                location.getY(), location.getZ(), location.getYaw(), snapToGrid());
        if (!repository.insert(board)) {
            return new CreateResult.AlreadyExists();
        }
        holograms.respawn(board);
        return new CreateResult.Created(board);
    }

    public @NotNull EditResult moveTo(@NotNull String id, @NotNull Player player) {
        Location location = player.getLocation();
        String world = player.getWorld().getName();
        boolean snapToGrid = snapToGrid();
        return edit(id, board -> board.placedAt(world, location.getX(),
                location.getY(), location.getZ(), location.getYaw(), snapToGrid));
    }

    public @NotNull EditResult edit(@NotNull String id, @NotNull UnaryOperator<PlacedBoard> edit) {
        PlacedBoard current = repository.find(id);
        if (current == null) {
            return new EditResult.NotFound();
        }
        PlacedBoard edited = edit.apply(current);
        if (!repository.replace(edited)) {
            return new EditResult.NotFound();
        }
        holograms.respawn(edited);
        return new EditResult.Edited(edited);
    }

    public @NotNull RemoveResult remove(@NotNull String id) {
        PlacedBoard removed = repository.delete(id);
        if (removed == null) {
            return new RemoveResult.NotFound();
        }
        holograms.despawn(id);
        return new RemoveResult.Removed(removed);
    }

    public @Nullable PlacedBoard find(@NotNull String id) {
        return repository.find(id);
    }

    public @NotNull List<PlacedBoard> boards() {
        return repository.all();
    }

    public @NotNull List<String> ids() {
        return repository.ids();
    }

    public boolean snapToGrid() {
        Settings settings = config.settings();
        return settings == null ? Settings.DEFAULT_SNAP_TO_GRID : settings.snapToGrid();
    }

    public sealed interface CreateResult {

        record Created(@NotNull PlacedBoard board) implements CreateResult {
        }

        record InvalidId() implements CreateResult {
        }

        record AlreadyExists() implements CreateResult {
        }
    }

    public sealed interface EditResult {

        record Edited(@NotNull PlacedBoard board) implements EditResult {
        }

        record NotFound() implements EditResult {
        }
    }

    public sealed interface RemoveResult {

        record Removed(@NotNull PlacedBoard board) implements RemoveResult {
        }

        record NotFound() implements RemoveResult {
        }
    }
}
