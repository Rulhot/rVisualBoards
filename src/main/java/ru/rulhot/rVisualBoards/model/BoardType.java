package ru.rulhot.rVisualBoards.model;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public record BoardType(@NotNull String id, @NotNull BoardLayout layout, @NotNull List<TopDefinition> tops) {

    public static final @NotNull String FOLDER = "tops";

    public BoardType {
        tops = List.copyOf(tops);
    }

    public @NotNull List<String> topIds() {
        return tops.stream().map(TopDefinition::id).toList();
    }
}
