package ru.rulhot.rVisualBoards.hologram;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public record HologramKeys(@NotNull NamespacedKey board, @NotNull NamespacedKey action) {

    public static @NotNull HologramKeys create(@NotNull Plugin plugin) {
        return new HologramKeys(new NamespacedKey(plugin, "board"), new NamespacedKey(plugin, "action"));
    }
}
