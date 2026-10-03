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

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public final class ListSubCommand implements SubCommand {

    private static final @NotNull String NAME = "list";

    private final @NotNull ConfigManager config;
    private final @NotNull BoardService boards;

    @Override
    public @NotNull LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal(NAME)
                .executes(context -> {
                    list(context.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                });
    }

    private void list(@NotNull CommandSender sender) {
        List<PlacedBoard> placed = boards.boards();
        if (placed.isEmpty()) {
            sender.sendMessage(config.getCommandMessage("list.empty"));
            return;
        }
        sender.sendMessage(config.formatCommandMessage("list.header", Map.of("count", placed.size())));
        for (PlacedBoard board : placed) {
            sender.sendMessage(config.formatCommandMessage("list.entry", board.placeholders()));
        }
    }
}
