package ru.rulhot.rVisualBoards.util;

import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@UtilityClass
public class MessageUtil {

    private final @NotNull MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private final @NotNull String TAG_PREFIX = "rvb_";
    private final @NotNull String EXTERNAL_SEPARATOR = " ";
    private final @NotNull Pattern MINI_TAG = Pattern.compile("</?[a-zA-Z#!][^<>]*>");

    public @NotNull Component parseText(@NotNull String text) {
        return MINI_MESSAGE.deserialize(text);
    }

    public @NotNull Component parseText(@NotNull String template, @NotNull Map<String, ?> placeholders) {
        if (placeholders.isEmpty()) {
            return MINI_MESSAGE.deserialize(template);
        }
        String prepared = template;
        List<TagResolver> resolvers = new ArrayList<>(placeholders.size());
        for (Map.Entry<String, ?> entry : placeholders.entrySet()) {
            String tag = TAG_PREFIX + entry.getKey().toLowerCase(Locale.ROOT);
            prepared = prepared.replace("{" + entry.getKey() + "}", "<" + tag + ">");
            resolvers.add(entry.getValue() instanceof ComponentLike component
                    ? Placeholder.component(tag, component)
                    : Placeholder.unparsed(tag, String.valueOf(entry.getValue())));
        }
        return MINI_MESSAGE.deserialize(prepared, TagResolver.resolver(resolvers));
    }

    public @NotNull Component parseExternal(@NotNull String raw) {
        if (raw.isBlank()) {
            return Component.empty();
        }
        String text = raw.endsWith(EXTERNAL_SEPARATOR) ? raw : raw + EXTERNAL_SEPARATOR;
        boolean section = text.indexOf(LegacyComponentSerializer.SECTION_CHAR) >= 0;
        if (!section && MINI_TAG.matcher(text).find()) {
            return MINI_MESSAGE.deserialize(text);
        }
        LegacyComponentSerializer serializer = section
                ? LegacyComponentSerializer.legacySection()
                : LegacyComponentSerializer.legacyAmpersand();
        return serializer.deserialize(text);
    }
}
