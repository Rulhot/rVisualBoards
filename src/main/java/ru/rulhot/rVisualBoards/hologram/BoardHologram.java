package ru.rulhot.rVisualBoards.hologram;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.User;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rVisualBoards.hologram.HologramView.Button;
import ru.rulhot.rVisualBoards.hologram.HologramView.Cell;
import ru.rulhot.rVisualBoards.model.Settings;
import ru.rulhot.rVisualBoards.model.PlacedBoard;
import ru.rulhot.rVisualBoards.model.TopDefinition;
import ru.rulhot.rVisualBoards.model.TopDefinition.Period;
import ru.rulhot.rVisualBoards.util.SchedulerUtil;

import java.util.ArrayList;
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

    private final @NotNull List<Interaction> hitboxes = new ArrayList<>();
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
        this.spawner = new HologramSpawner(context, board.id(), frame, geometry);
        this.renderer = new HologramRenderer(context, frame, geometry, this.tops);
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
        context.scheduler().runAt(origin, this::despawnAll);
    }

    public void shutdown() {
        markStopped();
        for (Interaction hitbox : hitboxes) {
            if (Bukkit.isOwnedByCurrentRegion(hitbox)) {
                removeEntity(hitbox);
            }
        }
        clearState();
    }

    public boolean select(@NotNull Player player, @NotNull ButtonAction action) {
        HologramView view = views.get(player.getUniqueId());
        TopDefinition current = view == null ? null : topsById.get(view.topId());
        if (stopped || current == null) {
            return false;
        }
        TopDefinition target = resolveTop(view, current, action);
        Period period = target == null ? null : resolvePeriod(view, current, target, action);
        if (target == null || period == null || (target == current && period == view.period())) {
            return false;
        }
        UUID viewerId = player.getUniqueId();
        String viewerName = player.getName();
        boolean rowsOnly = target == current;
        view.select(target.id(), period);
        context.selections().put(viewerId, board.id(), target.id(), period);
        context.leaderboards().request(target.board(), period);
        context.scheduler().runAt(origin, () -> switchTo(view, target, viewerName, rowsOnly));
        if (context.layout().table().personalRow()) {
            context.leaderboards().fetchStanding(viewerId, viewerName, target.board(), period)
                    .whenComplete((ignored, error) -> refreshStanding(viewerId));
        }
        return true;
    }

    public boolean scroll(@NotNull Player player, int delta) {
        HologramView view = views.get(player.getUniqueId());
        TopDefinition top = view == null ? null : topsById.get(view.topId());
        if (stopped || top == null || view.isAnimating() || !renderer.canScroll(view, top, delta)
                || !isLookingAtRows(player, context.settings().scroll().distance())) {
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

    private void switchTo(@NotNull HologramView view, @NotNull TopDefinition top, @NotNull String viewerName,
                          boolean rowsOnly) {
        if (stopped || views.get(view.viewerId()) != view) {
            return;
        }
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

    private boolean isLookingAtRows(@NotNull Player player, double maxDistance) {
        Location eye = player.getEyeLocation();
        if (!frame.world().equals(eye.getWorld())) {
            return false;
        }
        BoardFrame.Point point = frame.project(eye.getX(), eye.getY(), eye.getZ(), eye.getYaw(), eye.getPitch(),
                maxDistance);
        return point != null && geometry.isOnRows(point.x(), point.y());
    }

    private void tick() {
        if (stopped || !frame.isLoaded()) {
            return;
        }
        if (!spawned || !hitboxesIntact()) {
            despawnAll();
            removeOrphans();
            hitboxes.addAll(spawner.spawnHitboxes());
            spawned = true;
        }
        updateViews();
    }

    private boolean hitboxesIntact() {
        for (Interaction hitbox : hitboxes) {
            if (!hitbox.isValid()) {
                return false;
            }
        }
        return true;
    }

    private void updateViews() {
        double radius = context.settings().viewRadius();
        double radiusSquared = radius * radius;
        double keepRadius = radius + KEEP_MARGIN;
        int created = 0;
        boolean pending = false;
        Set<UUID> present = new HashSet<>();
        for (Player player : frame.world().getPlayers()) {
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
                renderer.refreshRows(view, top);
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
        HologramView view = spawner.spawnView(viewer, viewerId, top.id(), period);
        views.put(viewerId, view);
        context.leaderboards().request(top.board(), period);
        renderer.renderButtons(view, top);
        renderer.renderTable(view, top, player.getName());
        if (context.layout().table().personalRow()) {
            context.leaderboards().fetchStanding(viewerId, player.getName(), top.board(), period)
                    .whenComplete((ignored, error) -> refreshStanding(viewerId));
        }
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
            BoardFrame.Point point = lookPoint(view.viewerId(), hover.distance());
            int button = point == null || !hover.buttons()
                    ? BoardGeometry.NO_BUTTON
                    : geometry.buttonAt(point.x(), point.y());
            if (button != view.hoveredButton()) {
                zoom(buttonParts(view, view.hoveredButton()), NORMAL_ZOOM, hover.durationTicks());
                zoom(buttonParts(view, button), hover.scale(), hover.durationTicks());
                view.hoveredButton(button);
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

    private @Nullable BoardFrame.Point lookPoint(@NotNull UUID viewerId, double maxDistance) {
        Player player = Bukkit.getPlayer(viewerId);
        if (player == null || !Bukkit.isOwnedByCurrentRegion(player)) {
            return null;
        }
        Location position = player.getLocation(viewerPosition);
        return frame.project(position.getX(), position.getY() + player.getEyeHeight(),
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

    private void despawnAll() {
        for (Interaction hitbox : hitboxes) {
            removeEntity(hitbox);
        }
        clearState();
    }

    private void clearState() {
        spawned = false;
        hitboxes.clear();
        for (HologramView view : views.values()) {
            view.destroy();
        }
        views.clear();
    }

    private static void removeEntity(@NotNull Entity entity) {
        if (entity.isValid()) {
            entity.remove();
        }
    }

    private static void cancel(@Nullable SchedulerUtil.Task task) {
        if (task != null) {
            task.cancel();
        }
    }
}
