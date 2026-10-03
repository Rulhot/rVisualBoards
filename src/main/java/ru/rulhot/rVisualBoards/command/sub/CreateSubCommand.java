package ru.rulhot.rVisualBoards.command.sub;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import lombok.RequiredArgsConstructor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rVisualBoards.command.SubCommand;
import ru.rulhot.rVisualBoards.manager.ConfigManager;
import ru.rulhot.rVisualBoards.manager.TopManager;
import ru.rulhot.rVisualBoards.model.PlacedBoard;
import ru.rulhot.rVisualBoards.service.BoardService;
import ru.rulhot.rVisualBoards.service.BoardService.CreateResult;

import java.util.Map;

@RequiredArgsConstructor
public final class CreateSubCommand implements SubCommand {

    private static final @NotNull String NAME = "create";
    private static final @NotNull String ID = "id";

    private final @NotNull ConfigManager config;
    private final @NotNull BoardService boards;
    private final @NotNull TopManager tops;

    @Override
    public @NotNull LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal(NAME)
                .then(Commands.argument(ID, StringArgumentType.word())
                        .executes(context -> {
                            context.getSource().getSender().sendMessage(config.formatCommandMessage("create.no-type",
                                    Map.of("id", id(context), "types", String.join(LIST_SEPARATOR, tops.ids()))));
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(SubCommand.type(tops)
                                .executes(context -> {
                                    create(context);
                                    return Command.SINGLE_SUCCESS;
                                })));
    }

    private void create(@NotNull CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        Player player = SubCommand.player(config, sender);
        if (player == null || !SubCommand.isReady(config, sender)) {
            return;
        }
        String type = SubCommand.typeId(config, tops, context);
        if (type == null) {
            return;
        }
        String id = id(context);
        switch (boards.create(id, type, player)) {
            case CreateResult.Created(PlacedBoard board) ->
                    sender.sendMessage(config.formatCommandMessage("create.success", board.placeholders()));
            case CreateResult.InvalidId() -> sender.sendMessage(config.getCommandMessage("create.invalid-id"));
            case CreateResult.AlreadyExists() ->
                    sender.sendMessage(config.formatCommandMessage("create.exists", Map.of("id", id)));
        }
    }

    private static @NotNull String id(@NotNull CommandContext<CommandSourceStack> context) {
        return PlacedBoard.normalizeId(StringArgumentType.getString(context, ID));
    }
}
