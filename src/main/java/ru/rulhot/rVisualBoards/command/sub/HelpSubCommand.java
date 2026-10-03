package ru.rulhot.rVisualBoards.command.sub;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rVisualBoards.command.SubCommand;
import ru.rulhot.rVisualBoards.manager.ConfigManager;

@RequiredArgsConstructor
public final class HelpSubCommand implements SubCommand {

    private static final @NotNull String NAME = "help";

    private final @NotNull ConfigManager config;

    @Override
    public @NotNull LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal(NAME)
                .executes(context -> {
                    context.getSource().getSender().sendMessage(config.getCommandMessage("general.help"));
                    return Command.SINGLE_SUCCESS;
                });
    }
}
