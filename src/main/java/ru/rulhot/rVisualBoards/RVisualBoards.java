package ru.rulhot.rVisualBoards;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rVisualBoards.command.BoardCommand;
import ru.rulhot.rVisualBoards.command.sub.CreateSubCommand;
import ru.rulhot.rVisualBoards.command.sub.EditSubCommand;
import ru.rulhot.rVisualBoards.command.sub.HelpSubCommand;
import ru.rulhot.rVisualBoards.command.sub.ListSubCommand;
import ru.rulhot.rVisualBoards.command.sub.MoveHereSubCommand;
import ru.rulhot.rVisualBoards.command.sub.ReloadSubCommand;
import ru.rulhot.rVisualBoards.command.sub.RemoveSubCommand;
import ru.rulhot.rVisualBoards.hologram.HologramKeys;
import ru.rulhot.rVisualBoards.listener.HologramInteractListener;
import ru.rulhot.rVisualBoards.listener.HologramScrollListener;
import ru.rulhot.rVisualBoards.listener.PlayerQuitListener;
import ru.rulhot.rVisualBoards.listener.WorldListener;
import ru.rulhot.rVisualBoards.manager.ConfigManager;
import ru.rulhot.rVisualBoards.manager.HologramManager;
import ru.rulhot.rVisualBoards.manager.TopManager;
import ru.rulhot.rVisualBoards.model.Settings;
import ru.rulhot.rVisualBoards.service.BoardService;
import ru.rulhot.rVisualBoards.service.LeaderboardService;
import ru.rulhot.rVisualBoards.service.ReloadService;
import ru.rulhot.rVisualBoards.storage.BoardRepository;
import ru.rulhot.rVisualBoards.util.Metrics;
import ru.rulhot.rVisualBoards.util.SchedulerUtil;
import ru.rulhot.rVisualBoards.util.logger.Logger;

import java.util.List;

public final class RVisualBoards extends JavaPlugin {

    private static final int BSTATS_ID = 34351;

    private @Nullable ReloadService reloadService;
    private @Nullable Metrics metrics;

    @Override
    public void onEnable() {
        Logger.setup(this);
        SchedulerUtil scheduler = new SchedulerUtil(this);
        HologramKeys keys = HologramKeys.create(this);

        ConfigManager config = new ConfigManager(this);
        TopManager tops = new TopManager(this, getFile());
        BoardRepository boards = new BoardRepository(this, scheduler);
        LeaderboardService leaderboards = new LeaderboardService(scheduler);
        HologramManager holograms = new HologramManager(this, scheduler, keys, config, tops, leaderboards, boards);
        BoardService boardService = new BoardService(config, boards, holograms);
        ReloadService reloads = new ReloadService(scheduler, config, tops, boards, leaderboards, holograms);
        reloadService = reloads;

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new HologramInteractListener(keys, holograms), this);
        pluginManager.registerEvents(new HologramScrollListener(holograms), this);
        pluginManager.registerEvents(new PlayerQuitListener(holograms), this);
        pluginManager.registerEvents(new WorldListener(holograms), this);

        BoardCommand command = new BoardCommand(config, List.of(
                new HelpSubCommand(config),
                new CreateSubCommand(config, boardService, tops),
                new MoveHereSubCommand(config, boardService),
                new RemoveSubCommand(config, boardService),
                new EditSubCommand(config, boardService, tops),
                new ListSubCommand(config, boardService),
                new ReloadSubCommand(config, reloads, scheduler)));
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Settings.Command settings = config.readCommand();
            event.registrar().register(command.build(settings.name()), settings.aliases());
        });

        if (config.isMetrics()) {
            metrics = new Metrics(this, BSTATS_ID);
        }
        reloads.start();
    }

    @Override
    public void onDisable() {
        Metrics currentMetrics = metrics;
        metrics = null;
        if (currentMetrics != null) {
            currentMetrics.shutdown();
        }
        ReloadService current = reloadService;
        reloadService = null;
        if (current != null) {
            current.shutdown();
        }
    }
}
