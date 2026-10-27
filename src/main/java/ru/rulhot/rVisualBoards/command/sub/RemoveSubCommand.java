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
    private static final @NotNull String CONFIRM = "confirm";

    private final @NotNull ConfigManager config;
    private final @NotNull BoardService boards;

    @Override
    public @NotNull LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal(NAME)
                .executes(context -> SubCommand.usage(config, context, "remove.usage"))
                .then(SubCommand.board(boards)
                        .executes(context -> {
                            askConfirm(context.getSource().getSender(), SubCommand.boardId(context));
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.literal(CONFIRM).executes(context -> {
                            remove(context.getSource().getSender(), SubCommand.boardId(context));
                            return Command.SINGLE_SUCCESS;
                        })));
    }

    private void askConfirm(@NotNull CommandSender sender, @NotNull String id) {
        if (boards.find(id) == null) {
            config.send(sender, "board-not-found", Map.of("id", id));
            return;
        }
        config.send(sender, "remove.confirm", Map.of("id", id));
    }

    private void remove(@NotNull CommandSender sender, @NotNull String id) {
        if (!SubCommand.isReady(config, sender)) {
            return;
        }
        switch (boards.remove(id)) {
            case RemoveResult.Removed(PlacedBoard board) ->
                    config.send(sender, "remove.success", board.placeholders());
            case RemoveResult.NotFound() ->
                    config.send(sender, "board-not-found", Map.of("id", id));
        }
    }
}
