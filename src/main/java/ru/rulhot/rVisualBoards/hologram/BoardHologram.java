package ru.rulhot.rVisualBoards.hologram;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.User;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rVisualBoards.hologram.HologramView.Button;
import ru.rulhot.rVisualBoards.hologram.HologramView.Cell;
import ru.rulhot.rVisualBoards.model.LeaderboardSnapshot;
import ru.rulhot.rVisualBoards.model.LeaderboardSnapshot.Entry;
import ru.rulhot.rVisualBoards.model.Settings;
import ru.rulhot.rVisualBoards.model.PlacedBoard;
import ru.rulhot.rVisualBoards.model.TopDefinition;
import ru.rulhot.rVisualBoards.model.TopDefinition.FormatKey;
import ru.rulhot.rVisualBoards.model.TopDefinition.Period;
import ru.rulhot.rVisualBoards.util.MessageUtil;
import ru.rulhot.rVisualBoards.util.SchedulerUtil;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BoardHologram {

    private static final double KEEP_MARGIN = 4D;
    private static final double ORPHAN_MARGIN = 1D;
    private static final long FIRST_TICK_DELAY = 1L;
    private static final double NORMAL_ZOOM = 1D;
    private static final int MAX_NEW_VIEWS_PER_TICK = 3;
    private static final long CATCH_UP_DELAY_TICKS = 1L;

    private final @NotNull HologramContext context;
    private final @NotNull PlacedBoard board;

    private final @NotNull List<TopDefinition> tops;
    private final @NotNull Map<String, TopDefinition> topsById;
    private final @NotNull BoardFrame frame;
    private final @NotNull Location origin;
    private final @NotNull BoardGeometry geometry;
    private final @NotNull HologramSpawner spawner;
    private final @NotNull HologramRenderer renderer;
    private final @NotNull SwitchAnimator animator;

    private final @NotNull Map<UUID, HologramView> views = new ConcurrentHashMap<>();
    private final @NotNull Location viewerPosition = new Location(null, 0D, 0D, 0D);

    private volatile @Nullable SchedulerUtil.Task viewerTask;
    private volatile @Nullable SchedulerUtil.Task hoverTask;

    private volatile boolean stopped;
    private boolean spawned;
    private boolean catchingUp;

    public BoardHologram(@NotNull HologramContext context, @NotNull PlacedBoard board, @NotNull World world,
                         @NotNull List<TopDefinition> tops) {
        this.context = context;
        this.board = board;
        this.tops = List.copyOf(tops);
        Map<String, TopDefinition> indexed = new LinkedHashMap<>();
        for (TopDefinition top : this.tops) {
            indexed.put(top.id(), top);
        }
        this.topsById = Map.copyOf(indexed);
        this.frame = new BoardFrame(world, board);
        this.origin = frame.origin();
        int periodCount = this.tops.stream().mapToInt(top -> top.periods().size()).max().orElse(0);
        this.geometry = BoardGeometry.of(context.layout(), this.tops.size(), periodCount);
        this.spawner = new HologramSpawner(context, board.id(), geometry);
        this.renderer = new HologramRenderer(context, geometry, this.tops);
        this.animator = new SwitchAnimator(context, origin, renderer, frame.scale());
    }

    public @NotNull String id() {
        return board.id();
    }

    public @NotNull String worldName() {
        return board.world();
    }

    public void start() {
        SchedulerUtil scheduler = context.scheduler();
        Settings settings = context.settings();
        viewerTask = scheduler.runAtTimer(origin, this::tick, FIRST_TICK_DELAY,
                settings.advanced().viewerCheckIntervalTicks());
        if (settings.hover().enabled()) {
            hoverTask = scheduler.runAtTimer(origin, this::hoverTick, FIRST_TICK_DELAY,
                    settings.advanced().hoverCheckIntervalTicks());
        }
    }

    public void stop() {
        if (!markStopped()) {
            return;
        }
        if (!origin.isWorldLoaded()) {
            clearState();
            return;
        }
        context.scheduler().runAt(origin, this::clearState);
    }

    public void shutdown() {
        markStopped();
        clearState();
    }

    public @Nullable Selected select(@NotNull Player player, @NotNull ButtonAction action) {
        HologramView view = views.get(player.getUniqueId());
        TopDefinition current = view == null ? null : topsById.get(view.topId());
        if (stopped || current == null) {
            return null;
        }
        TopDefinition target = resolveTop(view, current, action);
        Period period = target == null ? null : resolvePeriod(view, current, target, action);
        if (target == null || period == null || (target == current && period == view.period())) {
            return null;
        }
        UUID viewerId = player.getUniqueId();
        String viewerName = player.getName();
        boolean rowsOnly = target == current;
        context.selections().put(viewerId, board.id(), target.id(), period);
        context.leaderboards().request(target.board(), period);
        context.scheduler().runAt(origin, () -> switchTo(view, target, period, viewerName, rowsOnly));
        if (context.layout().table().personalRow()) {
            context.leaderboards().fetchStanding(viewerId, viewerName, target.board(), period)
                    .whenComplete((ignored, error) -> refreshStanding(viewerId));
        }
        return new Selected(target, target != current);
    }

    public @Nullable RowClick clickRow(@NotNull Player player) {
        HologramView view = views.get(player.getUniqueId());
        TopDefinition top = view == null ? null : topsById.get(view.topId());
        if (stopped || top == null || top.rowClick().isEmpty() || view.isAnimating()) {
            return null;
        }
        Location eye = player.getEyeLocation();
        BoardFrame.Point point = frame.world().equals(eye.getWorld())
                ? view.frame().project(eye.getX(), eye.getY(), eye.getZ(), eye.getYaw(), eye.getPitch(),
                context.settings().scroll().distance())
                : null;
        int slot = point == null ? BoardGeometry.NO_ROW : geometry.rowAt(point.x(), point.y());
        LeaderboardSnapshot snapshot = context.leaderboards().snapshot(top.board(), view.period());
        Entry entry = slot == BoardGeometry.NO_ROW || snapshot == null
                ? null
                : snapshot.entry(view.scrollOffset() + slot);
        return entry == null ? null : new RowClick(top, entry);
    }

    public boolean scroll(@NotNull Player player, int delta) {
        HologramView view = views.get(player.getUniqueId());
        TopDefinition top = view == null ? null : topsById.get(view.topId());
        if (stopped || top == null || view.isAnimating() || !renderer.canScroll(view, top, delta)
                || !isLookingAtRows(player, view.frame(), context.settings().scroll().distance())) {
            return false;
        }
        context.scheduler().runAt(origin, () -> scrollView(view, delta));
        return true;
    }

    public void refreshStanding(@NotNull UUID viewerId) {
        if (stopped) {
            return;
        }
        context.scheduler().runAt(origin, () -> {
            HologramView view = views.get(viewerId);
            Player player = Bukkit.getPlayer(viewerId);
            TopDefinition top = view == null ? null : topsById.get(view.topId());
            if (stopped || view == null || player == null || top == null || view.isAnimating()) {
                return;
            }
            renderer.renderStanding(view, top, player.getName());
        });
    }

    public void forget(@NotNull UUID viewerId) {
        if (stopped || !views.containsKey(viewerId)) {
            return;
        }
        context.scheduler().runAt(origin, () -> removeView(viewerId));
    }

    private boolean markStopped() {
        if (stopped) {
            return false;
        }
        stopped = true;
        cancel(viewerTask);
        cancel(hoverTask);
        viewerTask = null;
        hoverTask = null;
        return true;
    }

    private @Nullable TopDefinition resolveTop(@NotNull HologramView view, @NotNull TopDefinition current,
                                               @NotNull ButtonAction action) {
        return switch (action) {
            case ButtonAction.Slot slot -> {
                int index = view.buttonOffset() + slot.index();
                yield index < tops.size() ? tops.get(index) : null;
            }
            case ButtonAction.Cycle cycle -> {
                int index = Math.max(0, tops.indexOf(current));
                yield tops.get(Math.floorMod(index + cycle.step(), tops.size()));
            }
            case ButtonAction.PeriodTab tab -> current;
        };
    }

    private static @Nullable Period resolvePeriod(@NotNull HologramView view, @NotNull TopDefinition current,
                                                  @NotNull TopDefinition target, @NotNull ButtonAction action) {
        if (!(action instanceof ButtonAction.PeriodTab tab)) {
            return target.periodOr(view.period());
        }
        List<Period> periods = current.periods();
        return tab.index() < periods.size() ? periods.get(tab.index()) : null;
    }

    private void switchTo(@NotNull HologramView view, @NotNull TopDefinition top, @NotNull Period period,
                          @NotNull String viewerName, boolean rowsOnly) {
        if (stopped || views.get(view.viewerId()) != view) {
            return;
        }
        view.select(top.id(), period);
        view.scrollOffset(0);
        animator.play(view, top, viewerName, rowsOnly);
    }

    private void scrollView(@NotNull HologramView view, int delta) {
        TopDefinition top = topsById.get(view.topId());
        if (stopped || views.get(view.viewerId()) != view || top == null || view.isAnimating()
                || !renderer.canScroll(view, top, delta)) {
            return;
        }
        renderer.scroll(view, top, delta);
    }

    private boolean isLookingAtRows(@NotNull Player player, @NotNull BoardFrame viewFrame, double maxDistance) {
        Location eye = player.getEyeLocation();
        if (!frame.world().equals(eye.getWorld())) {
            return false;
        }
        BoardFrame.Point point = viewFrame.project(eye.getX(), eye.getY(), eye.getZ(), eye.getYaw(), eye.getPitch(),
                maxDistance);
        return point != null && geometry.isOnRows(point.x(), point.y());
    }

    private void tick() {
        if (stopped || !frame.isLoaded()) {
            return;
        }
        if (!spawned) {
            removeOrphans();
            spawned = true;
        }
        updateViews();
    }

    private void updateViews() {
        double radius = context.settings().viewRadius();
        double radiusSquared = radius * radius;
        double keepRadius = radius + KEEP_MARGIN;
        int created = 0;
        boolean pending = false;
        Set<UUID> present = new HashSet<>();
        for (Player player : frame.world().getPlayers()) {
            if (!Bukkit.isOwnedByCurrentRegion(player)) {
                continue;
            }
            Location position = player.getLocation(viewerPosition);
            double deltaX = position.getX() - origin.getX();
            double deltaY = position.getY() - origin.getY();
            double deltaZ = position.getZ() - origin.getZ();
            if (Math.abs(deltaX) > keepRadius || Math.abs(deltaY) > keepRadius || Math.abs(deltaZ) > keepRadius) {
                continue;
            }
            UUID viewerId = player.getUniqueId();
            HologramView view = views.get(viewerId);
            if (view == null) {
                if (deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ > radiusSquared) {
                    continue;
                }
                if (created >= MAX_NEW_VIEWS_PER_TICK) {
                    pending = true;
                    continue;
                }
                view = createView(player);
                if (view == null) {
                    continue;
                }
                created++;
            }
            present.add(viewerId);
            TopDefinition top = topsById.get(view.topId());
            if (top == null) {
                continue;
            }
            context.leaderboards().request(top.board(), view.period());
            if (context.layout().table().personalRow()) {
                context.leaderboards().watch(viewerId, player.getName(), top.board(), view.period());
            }
            if (!view.isAnimating()) {
                if (renderer.refreshRows(view, top)) {
                    wave(view);
                }
                renderer.renderStanding(view, top, player.getName());
            }
        }
        for (UUID viewerId : List.copyOf(views.keySet())) {
            if (!present.contains(viewerId)) {
                removeView(viewerId);
            }
        }
        if (pending && !catchingUp) {
            catchingUp = true;
            context.scheduler().runAtLater(origin, this::catchUp, CATCH_UP_DELAY_TICKS);
        }
    }

    private void wave(@NotNull HologramView view) {
        Settings.Effects effects = context.settings().effects();
        if (!effects.wave()) {
            return;
        }
        int generation = view.generation();
        double height = effects.waveHeight() * frame.scale();
        int duration = effects.waveDurationTicks();
        List<List<Cell>> rows = view.rows();
        for (int index = 0; index < rows.size(); index++) {
            List<HologramPart> parts = rows.get(index).stream().map(Cell::part).toList();
            long delay = FIRST_TICK_DELAY + (long) index * effects.waveStaggerTicks();
            context.scheduler().runAtLater(origin, () -> {
                if (view.isCurrent(generation) && !view.isAnimating()) {
                    bounce(parts, height, duration);
                }
            }, delay);
            context.scheduler().runAtLater(origin, () -> {
                parts.forEach(part -> part.bounce(0D));
                if (!view.isDetached() && !view.isAnimating()) {
                    bounce(parts, 0D, duration);
                }
            }, delay + duration);
        }
    }

    private static void bounce(@NotNull List<HologramPart> parts, double height, int duration) {
        for (HologramPart part : parts) {
            part.bounce(height);
            part.apply(duration);
        }
    }

    private void catchUp() {
        catchingUp = false;
        tick();
    }

    private @Nullable HologramView createView(@NotNull Player player) {
        User viewer = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (viewer == null) {
            return null;
        }
        UUID viewerId = player.getUniqueId();
        TopSelections.Selection remembered = context.selections().get(viewerId, board.id());
        TopDefinition top = remembered == null ? null : topsById.get(remembered.topId());
        if (top == null) {
            top = defaultTop();
        }
        Period period = remembered == null ? top.defaultPeriod() : top.periodOr(remembered.period());
        HologramView view = spawner.spawnView(viewer, frame, viewerId, top.id(), period);
        views.put(viewerId, view);
        context.leaderboards().request(top.board(), period);
        renderer.renderButtons(view, top);
        renderer.renderTable(view, top, player.getName());
        if (context.layout().table().personalRow()) {
            context.leaderboards().fetchStanding(viewerId, player.getName(), top.board(), period)
                    .whenComplete((ignored, error) -> refreshStanding(viewerId));
        }
        animator.appear(view);
        return view;
    }

    private @NotNull TopDefinition defaultTop() {
        String preferred = board.defaultTop();
        TopDefinition top = preferred == null ? null : topsById.get(preferred);
        return top == null ? tops.getFirst() : top;
    }

    private void hoverTick() {
        if (stopped) {
            return;
        }
        Settings.Hover hover = context.settings().hover();
        for (HologramView view : views.values()) {
            BoardFrame.Point point = lookPoint(view, hover.distance());
            int button = point == null || !hover.buttons()
                    ? BoardGeometry.NO_BUTTON
                    : geometry.buttonAt(point.x(), point.y());
            if (button != view.hoveredButton()) {
                zoom(buttonParts(view, view.hoveredButton()), NORMAL_ZOOM, hover.durationTicks());
                zoom(buttonParts(view, button), hover.scale(), hover.durationTicks());
                view.hoveredButton(button);
                if (button != BoardGeometry.NO_BUTTON) {
                    hoverFeedback(view, FormatKey.HINT_BUTTON, null);
                }
            }
            int hinted = point == null ? BoardGeometry.NO_ROW : geometry.rowAt(point.x(), point.y());
            if (hinted != view.hintedRow()) {
                view.hintedRow(hinted);
                Entry entry = hinted == BoardGeometry.NO_ROW ? null : entryAt(view, hinted);
                if (entry != null) {
                    hoverFeedback(view, FormatKey.HINT_ROW, entry);
                }
            }
            if (view.isAnimating()) {
                view.hoveredRow(BoardGeometry.NO_ROW);
                continue;
            }
            int row = point == null || !hover.rows() || hover.rowScale() == NORMAL_ZOOM
                    ? BoardGeometry.NO_ROW
                    : geometry.rowAt(point.x(), point.y());
            if (row != view.hoveredRow()) {
                zoom(rowParts(view, view.hoveredRow()), NORMAL_ZOOM, hover.durationTicks());
                zoom(rowParts(view, row), hover.rowScale(), hover.durationTicks());
                view.hoveredRow(row);
            }
        }
    }

    private @Nullable Entry entryAt(@NotNull HologramView view, int slot) {
        TopDefinition top = topsById.get(view.topId());
        LeaderboardSnapshot snapshot = top == null ? null : context.leaderboards().snapshot(top.board(), view.period());
        return snapshot == null ? null : snapshot.entry(view.scrollOffset() + slot);
    }

    private void hoverFeedback(@NotNull HologramView view, @NotNull FormatKey hintKey, @Nullable Entry entry) {
        Player player = Bukkit.getPlayer(view.viewerId());
        TopDefinition top = topsById.get(view.topId());
        if (player == null || top == null) {
            return;
        }
        Sound sound = context.settings().sounds().hover();
        if (sound != null && entry == null) {
            player.playSound(sound);
        }
        String hint = top.format(hintKey);
        if (!hint.isBlank()) {
            player.sendActionBar(MessageUtil.parseText(hint, entry == null
                    ? Map.of()
                    : Map.of("name", entry.name(), "place", entry.place())));
        }
    }

    private @Nullable BoardFrame.Point lookPoint(@NotNull HologramView view, double maxDistance) {
        Player player = Bukkit.getPlayer(view.viewerId());
        if (player == null || !Bukkit.isOwnedByCurrentRegion(player)) {
            return null;
        }
        Location position = player.getLocation(viewerPosition);
        return view.frame().project(position.getX(), position.getY() + player.getEyeHeight(),
                position.getZ(), position.getYaw(), position.getPitch(), maxDistance);
    }

    private static @NotNull List<HologramPart> buttonParts(@NotNull HologramView view, int index) {
        List<Button> buttons = view.buttons();
        return index < 0 || index >= buttons.size() ? List.of() : buttons.get(index).parts();
    }

    private static @NotNull List<HologramPart> rowParts(@NotNull HologramView view, int slot) {
        List<List<Cell>> rows = view.rows();
        return slot < 1 || slot > rows.size() ? List.of() : rows.get(slot - 1).stream().map(Cell::part).toList();
    }

    private static void zoom(@NotNull List<HologramPart> parts, double zoom, int durationTicks) {
        for (HologramPart part : parts) {
            part.zoom(zoom);
            part.apply(durationTicks);
        }
    }

    private void removeOrphans() {
        double halfHeight = geometry.height() * frame.scale() / 2D;
        double horizontal = geometry.width() * frame.scale() / 2D + ORPHAN_MARGIN;
        Location center = origin.clone().add(0D, halfHeight, 0D);
        for (Entity entity : frame.world().getNearbyEntities(center, horizontal, halfHeight + ORPHAN_MARGIN,
                horizontal, this::isOwned)) {
            entity.remove();
        }
    }

    private boolean isOwned(@NotNull Entity entity) {
        String owner = entity.getPersistentDataContainer().get(context.keys().board(), PersistentDataType.STRING);
        return board.id().equals(owner);
    }

    private void removeView(@NotNull UUID viewerId) {
        HologramView view = views.remove(viewerId);
        if (view == null) {
            return;
        }
        view.destroy();
    }

    private void clearState() {
        spawned = false;
        for (HologramView view : views.values()) {
            view.destroy();
        }
        views.clear();
    }

    private static void cancel(@Nullable SchedulerUtil.Task task) {
        if (task != null) {
            task.cancel();
        }
    }

    public record RowClick(@NotNull TopDefinition top, @NotNull Entry entry) {
    }

    public record Selected(@NotNull TopDefinition top, boolean topChanged) {
    }
}
