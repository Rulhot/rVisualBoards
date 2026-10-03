package ru.rulhot.rVisualBoards.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rVisualBoards.manager.ConfigManager;
import ru.rulhot.rVisualBoards.manager.TopManager;
import ru.rulhot.rVisualBoards.model.PlacedBoard;
import ru.rulhot.rVisualBoards.service.BoardService;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

public interface SubCommand {

    @NotNull String BOARD = "board";
    @NotNull String TYPE = "type";
    @NotNull String LIST_SEPARATOR = ", ";

    @NotNull LiteralArgumentBuilder<CommandSourceStack> node();

    static @NotNull RequiredArgumentBuilder<CommandSourceStack, String> board(@NotNull BoardService boards) {
        return Commands.argument(BOARD, StringArgumentType.word()).suggests(suggest(context -> boards.ids()));
    }

    static @NotNull String boardId(@NotNull CommandContext<CommandSourceStack> context) {
        return PlacedBoard.normalizeId(StringArgumentType.getString(context, BOARD));
    }

    static @NotNull RequiredArgumentBuilder<CommandSourceStack, String> type(@NotNull TopManager tops) {
        return Commands.argument(TYPE, StringArgumentType.word()).suggests(suggest(context -> tops.ids()));
    }

    static @Nullable String typeId(@NotNull ConfigManager config, @NotNull TopManager tops,
                                   @NotNull CommandContext<CommandSourceStack> context) {
        String type = PlacedBoard.normalizeId(StringArgumentType.getString(context, TYPE));
        if (tops.find(type) != null) {
            return type;
        }
        context.getSource().getSender().sendMessage(config.formatCommandMessage("general.type-missing",
                Map.of("type", type, "types", String.join(LIST_SEPARATOR, tops.ids()))));
        return null;
    }

    static @NotNull SuggestionProvider<CommandSourceStack> suggest(
            @NotNull Function<CommandContext<CommandSourceStack>, Collection<String>> options) {
        return (context, builder) -> {
            String prefix = builder.getRemainingLowerCase();
            for (String option : options.apply(context)) {
                if (option.startsWith(prefix)) {
                    builder.suggest(option);
                }
            }
            return builder.buildFuture();
        };
    }

    static @Nullable Player player(@NotNull ConfigManager config, @NotNull CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(config.getCommandMessage("general.player-only"));
        return null;
    }

    static boolean isReady(@NotNull ConfigManager config, @NotNull CommandSender sender) {
        if (config.settings() != null) {
            return true;
        }
        sender.sendMessage(config.getCommandMessage("general.not-ready"));
        return false;
    }
}
