package ru.rulhot.rVisualBoards.command.sub;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rVisualBoards.command.SubCommand;
import ru.rulhot.rVisualBoards.hologram.BoardHologram.Preview;
import ru.rulhot.rVisualBoards.manager.ConfigManager;
import ru.rulhot.rVisualBoards.manager.HologramManager;
import ru.rulhot.rVisualBoards.service.BoardService;
import ru.rulhot.rVisualBoards.util.SchedulerUtil;

import java.util.Locale;

@RequiredArgsConstructor
public final class PreviewSubCommand implements SubCommand {
    private final ConfigManager config;
    private final BoardService boards;
    private final HologramManager holograms;
    private final SchedulerUtil scheduler;

    @Override
    public @NotNull LiteralArgumentBuilder<CommandSourceStack> node() {
        var board = SubCommand.board(boards).executes(ctx -> preview(ctx, Preview.REFRESH));
        for (Preview mode : Preview.values()) {
            board.then(Commands.literal(mode.name().toLowerCase(Locale.ROOT)).executes(ctx -> preview(ctx, mode)));
        }
        return Commands.literal("preview").executes(ctx -> SubCommand.usage(config, ctx, "preview.usage")).then(board);
    }

    private int preview(CommandContext<CommandSourceStack> ctx, Preview mode) {
        Player player = SubCommand.player(config, ctx.getSource().getSender());
        if (player == null || !SubCommand.isReady(config, player)) return 0;
        holograms.preview(SubCommand.boardId(ctx), player.getUniqueId(), mode, shown -> {
            if (!shown) scheduler.runFor(player, () -> config.send(player, "preview.unavailable"));
        });
        return 1;
    }
}
