package ru.rulhot.rVisualBoards.hologram;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rVisualBoards.hologram.HologramView.Group;
import ru.rulhot.rVisualBoards.hologram.HologramView.GroupKind;
import ru.rulhot.rVisualBoards.model.Settings;
import ru.rulhot.rVisualBoards.model.Settings.AnimationType;
import ru.rulhot.rVisualBoards.model.TopDefinition;
import ru.rulhot.rVisualBoards.util.SchedulerUtil;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

final class SwitchAnimator {

    private static final double MIN_ZOOM = 0.01D;

    private final @NotNull HologramContext context;
    private final @NotNull Location origin;
    private final @NotNull HologramRenderer renderer;
    private final @NotNull Settings.Animation animation;
    private final double scale;

    SwitchAnimator(@NotNull HologramContext context, @NotNull Location origin, @NotNull HologramRenderer renderer,
                   double scale) {
        this.context = context;
        this.origin = origin.clone();
        this.renderer = renderer;
        this.animation = context.settings().animation();
        this.scale = scale;
    }

    void play(@NotNull HologramView view, @NotNull TopDefinition top, @NotNull String viewerName, boolean rowsOnly) {
        int generation = view.nextGeneration();
        renderer.renderButtons(view, top);
        if (!animation.enabled()) {
            renderer.renderTable(view, top, viewerName);
            return;
        }
        AnimationType type = pickType();
        List<Group> groups = rowsOnly ? rowGroups(view) : view.groups();
        int duration = animation.durationTicks();
        view.markBusy(delay(groups.size() - 1, type) + duration * 2L + 1L);
        for (int step = 0; step < groups.size(); step++) {
            Group group = groups.get(step);
            long start = delay(step, type);
            schedule(view, generation, start, () -> leave(group, type));
            schedule(view, generation, start + duration, () -> {
                renderer.renderGroup(view, group, top, viewerName);
                enter(group, type);
            });
            schedule(view, generation, start + duration + 1L, () -> settle(group));
        }
    }

    private static @NotNull List<Group> rowGroups(@NotNull HologramView view) {
        return view.groups().stream()
                .filter(group -> group.kind() == GroupKind.ROW || group.kind() == GroupKind.STANDING
                        || group.kind() == GroupKind.TITLE)
                .toList();
    }

    private @NotNull AnimationType pickType() {
        List<AnimationType> types = animation.types();
        return types.get(ThreadLocalRandom.current().nextInt(types.size()));
    }

    private long delay(int step, @NotNull AnimationType type) {
        if (!type.staggered() || step <= 0) {
            return 0L;
        }
        return (long) step * animation.staggerTicks();
    }

    private void schedule(@NotNull HologramView view, int generation, long delayTicks, @NotNull Runnable action) {
        if (delayTicks <= 0L) {
            runIfCurrent(view, generation, action);
            return;
        }
        SchedulerUtil scheduler = context.scheduler();
        scheduler.runAtLater(origin, () -> runIfCurrent(view, generation, action), delayTicks);
    }

    private void runIfCurrent(@NotNull HologramView view, int generation, @NotNull Runnable action) {
        if (view.isCurrent(generation)) {
            action.run();
        }
    }

    private void leave(@NotNull Group group, @NotNull AnimationType type) {
        for (HologramPart part : group.parts()) {
            pose(part, type, true);
            part.apply(animation.durationTicks());
        }
    }

    private void enter(@NotNull Group group, @NotNull AnimationType type) {
        for (HologramPart part : group.parts()) {
            pose(part, type, false);
            part.apply(0);
        }
    }

    private void settle(@NotNull Group group) {
        for (HologramPart part : group.parts()) {
            part.resetPose();
            part.apply(animation.durationTicks());
        }
    }

    private void pose(@NotNull HologramPart part, @NotNull AnimationType type, boolean leaving) {
        double distance = animation.distance() * scale;
        part.resetPose();
        part.fade();
        switch (type) {
            case SLIDE_LEFT, CASCADE -> part.shift(leaving ? -distance : distance);
            case SLIDE_RIGHT -> part.shift(leaving ? distance : -distance);
            case ZOOM -> part.zoom(MIN_ZOOM);
        }
    }
}
