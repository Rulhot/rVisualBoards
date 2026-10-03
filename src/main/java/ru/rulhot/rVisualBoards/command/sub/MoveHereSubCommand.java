package ru.rulhot.rVisualBoards.command.sub;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import lombok.RequiredArgsConstructor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rVisualBoards.command.SubCommand;
import ru.rulhot.rVisualBoards.manager.ConfigManager;
import ru.rulhot.rVisualBoards.model.PlacedBoard;
import ru.rulhot.rVisualBoards.service.BoardService;
import ru.rulhot.rVisualBoards.service.BoardService.EditResult;

import java.util.Map;

@RequiredArgsConstructor
public final class MoveHereSubCommand implements SubCommand {

    private static final @NotNull String NAME = "movehere";

    private final @NotNull ConfigManager config;
    private final @NotNull BoardService boards;

    @Override
    public @NotNull LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal(NAME)
                .then(SubCommand.board(boards)
                        .executes(context -> {
                            move(context.getSource().getSender(), SubCommand.boardId(context));
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private void move(@NotNull CommandSender sender, @NotNull String id) {
        Player player = SubCommand.player(config, sender);
        if (player == null || !SubCommand.isReady(config, sender)) {
            return;
        }
        switch (boards.moveTo(id, player)) {
            case EditResult.Edited(PlacedBoard board) ->
                    sender.sendMessage(config.formatCommandMessage("movehere.success", board.placeholders()));
            case EditResult.NotFound() ->
                    sender.sendMessage(config.formatCommandMessage("general.board-not-found", Map.of("id", id)));
        }
    }
}
