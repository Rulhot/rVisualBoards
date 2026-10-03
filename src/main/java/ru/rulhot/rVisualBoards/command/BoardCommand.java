package ru.rulhot.rVisualBoards.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rVisualBoards.manager.ConfigManager;

import java.util.List;

@RequiredArgsConstructor
public final class BoardCommand {

    private static final @NotNull String PERMISSION = "rvisualboards.admin";

    private final @NotNull ConfigManager config;
    private final @NotNull List<SubCommand> subCommands;

    public @NotNull LiteralCommandNode<CommandSourceStack> build(@NotNull String label) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(label)
                .requires(source -> source.getSender().hasPermission(PERMISSION))
                .executes(context -> {
                    context.getSource().getSender().sendMessage(config.getCommandMessage("general.help"));
                    return Command.SINGLE_SUCCESS;
                });
        for (SubCommand subCommand : subCommands) {
            root.then(subCommand.node());
        }
        return root.build();
    }
}
