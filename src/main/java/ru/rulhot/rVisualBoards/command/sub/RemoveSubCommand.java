package ru.rulhot.rVisualBoards.command.sub;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import lombok.RequiredArgsConstructor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rVisualBoards.command.SubCommand;
import ru.rulhot.rVisualBoards.manager.ConfigManager;
import ru.rulhot.rVisualBoards.model.PlacedBoard;
import ru.rulhot.rVisualBoards.service.BoardService;
import ru.rulhot.rVisualBoards.service.BoardService.RemoveResult;

import java.util.Map;

@RequiredArgsConstructor
public final class RemoveSubCommand implements SubCommand {

    private static final @NotNull String NAME = "remove";

    private final @NotNull ConfigManager config;
    private final @NotNull BoardService boards;

    @Override
    public @NotNull LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal(NAME)
                .then(SubCommand.board(boards)
                        .executes(context -> {
                            remove(context.getSource().getSender(), SubCommand.boardId(context));
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private void remove(@NotNull CommandSender sender, @NotNull String id) {
        switch (boards.remove(id)) {
            case RemoveResult.Removed(PlacedBoard board) ->
                    config.send(sender, "remove.success", board.placeholders());
            case RemoveResult.NotFound() ->
                    config.send(sender, "general.board-not-found", Map.of("id", id));
        }
    }
}
