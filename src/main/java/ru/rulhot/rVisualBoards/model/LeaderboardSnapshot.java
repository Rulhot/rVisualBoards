package ru.rulhot.rVisualBoards.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Pattern;

public record LeaderboardSnapshot(long version, @NotNull List<Entry> entries, boolean complete) {

    public static final int UNRANKED = 0;

    private static final @NotNull Pattern NON_DIGITS = Pattern.compile("[^0-9]");

    public LeaderboardSnapshot {
        entries = List.copyOf(entries);
    }

    public static int parsePosition(@NotNull String raw) {
        String digits = NON_DIGITS.matcher(raw).replaceAll("");
        if (digits.isEmpty()) {
            return UNRANKED;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException exception) {
            return UNRANKED;
        }
    }

    public @Nullable Entry entry(int place) {
        int index = place - 1;
        if (index < 0 || index >= entries.size()) {
            return null;
        }
        return entries.get(index);
    }

    public int filledPlaces() {
        return entries.size();
    }

    public boolean confirmsPlace(int place, @NotNull String playerName) {
        if (place <= UNRANKED) {
            return false;
        }
        Entry entry = entry(place);
        if (entry == null) {
            return !complete;
        }
        return entry.name().equalsIgnoreCase(playerName);
    }

    public record Key(@NotNull String id, @NotNull TopDefinition.Period period) {
    }

    public record Entry(int place, @NotNull String prefix, @NotNull String name, @NotNull String value) {
    }

    public record Standing(@NotNull String position, @NotNull String value, boolean ranked) {

        public static final @NotNull Standing UNKNOWN = new Standing("0", "0", false);
    }
}
