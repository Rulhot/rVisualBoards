package ru.rulhot.rVisualBoards.command.sub;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rVisualBoards.command.SubCommand;
import ru.rulhot.rVisualBoards.manager.ConfigManager;
import ru.rulhot.rVisualBoards.manager.TopManager;
import ru.rulhot.rVisualBoards.model.BoardType;
import ru.rulhot.rVisualBoards.model.PlacedBoard;
import ru.rulhot.rVisualBoards.model.PlacedBoard.Direction;
import ru.rulhot.rVisualBoards.service.BoardService;
import ru.rulhot.rVisualBoards.service.BoardService.EditResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;

@RequiredArgsConstructor
public final class EditSubCommand implements SubCommand {

    private static final @NotNull String NAME = "edit";
    private static final @NotNull String DEGREES = "degrees";
    private static final @NotNull String FACTOR = "factor";
    private static final @NotNull String BLOCKS = "blocks";
    private static final @NotNull String TOP = "top";
    private static final double FULL_TURN = 360D;
    private static final double MIN_SHIFT = 0.01D;
    private static final double MAX_SHIFT = 16D;
    private static final double TELEPORT_DISTANCE = 3D;

    private final @NotNull ConfigManager config;
    private final @NotNull BoardService boards;
    private final @NotNull TopManager tops;

    @Override
    public @NotNull LiteralArgumentBuilder<CommandSourceStack> node() {
        RequiredArgumentBuilder<CommandSourceStack, String> board = SubCommand.board(boards)
                .executes(context -> done(() -> info(sender(context), SubCommand.boardId(context))))
                .then(rotate())
                .then(face())
                .then(scale())
                .then(move())
                .then(Commands.literal("tp").executes(context -> done(() -> teleport(context))))
                .then(defaultTop())
                .then(Commands.literal(TYPE).then(SubCommand.type(tops)
                        .executes(context -> done(() -> setType(context)))));
        return Commands.literal(NAME).then(board);
    }

    private @NotNull LiteralArgumentBuilder<CommandSourceStack> rotate() {
        return Commands.literal("rotate")
                .then(Commands.argument(DEGREES, DoubleArgumentType.doubleArg(0D, FULL_TURN))
                        .executes(context -> done(() -> edit(context, "edit.rotated",
                                current -> current.rotatedTo(DoubleArgumentType.getDouble(context, DEGREES))))));
    }

    private @NotNull LiteralArgumentBuilder<CommandSourceStack> face() {
        return Commands.literal("face").executes(context -> done(() -> {
            Player player = SubCommand.player(config, sender(context));
            if (player == null) {
                return;
            }
            float viewerYaw = player.getLocation().getYaw();
            boolean snapToGrid = boards.snapToGrid();
            edit(context, "edit.rotated", current -> current.facing(viewerYaw, snapToGrid));
        }));
    }

    private @NotNull LiteralArgumentBuilder<CommandSourceStack> scale() {
        return Commands.literal("scale")
                .then(Commands.argument(FACTOR, DoubleArgumentType.doubleArg(PlacedBoard.MIN_SCALE, PlacedBoard.MAX_SCALE))
                        .executes(context -> done(() -> edit(context, "edit.scaled",
                                current -> current.withScale(DoubleArgumentType.getDouble(context, FACTOR))))));
    }

    private @NotNull LiteralArgumentBuilder<CommandSourceStack> move() {
        LiteralArgumentBuilder<CommandSourceStack> move = Commands.literal("move");
        for (Direction direction : Direction.values()) {
            move.then(Commands.literal(direction.key())
                    .then(Commands.argument(BLOCKS, DoubleArgumentType.doubleArg(MIN_SHIFT, MAX_SHIFT))
                            .executes(context -> done(() -> edit(context, "edit.moved",
                                    current -> current.shifted(direction,
                                            DoubleArgumentType.getDouble(context, BLOCKS)))))));
        }
        return move;
    }

    private @NotNull LiteralArgumentBuilder<CommandSourceStack> defaultTop() {
        return Commands.literal("default-top")
                .then(Commands.argument(TOP, StringArgumentType.word())
                        .suggests(SubCommand.suggest(this::suggestBoardTops))
                        .executes(context -> done(() -> setDefaultTop(context))));
    }

    private void info(@NotNull CommandSender sender, @NotNull String id) {
        PlacedBoard board = board(sender, id);
        if (board == null) {
            return;
        }
        Map<String, Object> placeholders = new HashMap<>(board.placeholders());
        List<String> boardTops = boardTops(board);
        placeholders.put("tops", tops.find(board.type()) == null
                ? config.formatCommandMessage("edit.no-file", board.placeholders())
                : String.join(LIST_SEPARATOR, boardTops));
        placeholders.put("default-top", board.defaultTop() != null ? board.defaultTop()
                : boardTops.isEmpty() ? "" : boardTops.getFirst());
        sender.sendMessage(config.formatCommandMessage("edit.info", placeholders));
        sender.sendMessage(config.formatCommandMessage("edit.help", Map.of("id", id)));
    }

    private void teleport(@NotNull CommandContext<CommandSourceStack> context) {
        CommandSender sender = sender(context);
        Player player = SubCommand.player(config, sender);
        PlacedBoard board = player == null ? null : board(sender, SubCommand.boardId(context));
        if (player == null || board == null) {
            return;
        }
        World world = Bukkit.getWorld(board.world());
        if (world == null) {
            sender.sendMessage(config.formatCommandMessage("edit.world-not-loaded", Map.of("world", board.world())));
            return;
        }
        double distance = TELEPORT_DISTANCE * board.scale();
        player.teleportAsync(new Location(world, board.frontX(distance), board.y(), board.frontZ(distance),
                board.viewerYaw(), 0F));
        sender.sendMessage(config.formatCommandMessage("edit.teleported", board.placeholders()));
    }

    private void setDefaultTop(@NotNull CommandContext<CommandSourceStack> context) {
        CommandSender sender = sender(context);
        String id = SubCommand.boardId(context);
        String topId = PlacedBoard.normalizeId(StringArgumentType.getString(context, TOP));
        PlacedBoard board = board(sender, id);
        if (board == null) {
            return;
        }
        if (!boardTops(board).contains(topId)) {
            sender.sendMessage(config.formatCommandMessage("edit.top-missing", Map.of("id", id, "top", topId)));
            return;
        }
        editThen(context, edited -> edited.withDefaultTop(topId),
                edited -> config.formatCommandMessage("edit.default-top", Map.of("id", id, "top", topId)));
    }

    private void setType(@NotNull CommandContext<CommandSourceStack> context) {
        String typeId = SubCommand.typeId(config, tops, context);
        BoardType type = typeId == null ? null : tops.find(typeId);
        if (type == null) {
            return;
        }
        edit(context, "edit.type", current -> {
            String defaultTop = current.defaultTop();
            boolean kept = defaultTop != null && type.topIds().contains(defaultTop);
            return current.withType(type.id()).withDefaultTop(kept ? defaultTop : null);
        });
    }

    private void edit(@NotNull CommandContext<CommandSourceStack> context, @NotNull String message,
                      @NotNull UnaryOperator<PlacedBoard> change) {
        editThen(context, change, edited -> config.formatCommandMessage(message, edited.placeholders()));
    }

    private void editThen(@NotNull CommandContext<CommandSourceStack> context,
                          @NotNull UnaryOperator<PlacedBoard> change,
                          @NotNull Function<PlacedBoard, Component> reply) {
        CommandSender sender = sender(context);
        if (!SubCommand.isReady(config, sender)) {
            return;
        }
        String id = SubCommand.boardId(context);
        switch (boards.edit(id, change)) {
            case EditResult.Edited(PlacedBoard board) -> sender.sendMessage(reply.apply(board));
            case EditResult.NotFound() -> notFound(sender, id);
        }
    }

    private @Nullable PlacedBoard board(@NotNull CommandSender sender, @NotNull String id) {
        PlacedBoard board = boards.find(id);
        if (board == null) {
            notFound(sender, id);
        }
        return board;
    }

    private @NotNull List<String> boardTops(@NotNull PlacedBoard board) {
        BoardType type = tops.find(board.type());
        return type == null ? List.of() : type.topIds();
    }

    private @NotNull List<String> suggestBoardTops(@NotNull CommandContext<CommandSourceStack> context) {
        PlacedBoard board = boards.find(SubCommand.boardId(context));
        return board == null ? List.of() : boardTops(board);
    }

    private void notFound(@NotNull CommandSender sender, @NotNull String id) {
        sender.sendMessage(config.formatCommandMessage("general.board-not-found", Map.of("id", id)));
    }

    private static @NotNull CommandSender sender(@NotNull CommandContext<CommandSourceStack> context) {
        return context.getSource().getSender();
    }

    private static int done(@NotNull Runnable action) {
        action.run();
        return Command.SINGLE_SUCCESS;
    }
}
