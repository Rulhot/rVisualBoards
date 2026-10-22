package ru.rulhot.rVisualBoards.command.sub;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rVisualBoards.command.SubCommand;
import ru.rulhot.rVisualBoards.manager.ConfigManager;
import ru.rulhot.rVisualBoards.service.ReloadService;
import ru.rulhot.rVisualBoards.service.ReloadService.Result;
import ru.rulhot.rVisualBoards.util.SchedulerUtil;

import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public final class ReloadSubCommand implements SubCommand {

    private static final @NotNull String NAME = "reload";

    private final @NotNull ConfigManager config;
    private final @NotNull ReloadService reloads;
    private final @NotNull SchedulerUtil scheduler;

    @Override
    public @NotNull LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal(NAME)
                .executes(context -> {
                    reload(context.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                });
    }

    private void reload(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            reloads.reload().thenAccept(result ->
                    scheduler.runGlobal(() -> reply(sender, result)));
            return;
        }
        UUID playerId = player.getUniqueId();
        reloads.reload().thenAccept(result -> {
            Player online = Bukkit.getPlayer(playerId);
            if (online == null) {
                return;
            }
            scheduler.runFor(online, () -> reply(online, result));
        });
    }

    private void reply(@NotNull CommandSender sender, @NotNull Result result) {
        switch (result) {
            case Result.Success(int boards, int types) ->
                    config.send(sender, "reload.done", Map.of("boards", boards, "types", types));
            case Result.Busy() -> config.send(sender, "reload.busy");
            case Result.Failed() -> config.send(sender, "reload.failed");
        }
    }
}
