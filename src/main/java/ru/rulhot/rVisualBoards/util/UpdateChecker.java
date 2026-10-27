package ru.rulhot.rVisualBoards.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rVisualBoards.util.logger.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public final class UpdateChecker {

    private static final @NotNull URI LATEST_RELEASE =
            URI.create("https://api.github.com/repos/Rulhot/rVisualBoards/releases/latest");
    private static final @NotNull String DOWNLOAD_PAGE = "https://github.com/Rulhot/rVisualBoards/releases/latest";
    private static final @NotNull String USER_AGENT = "rVisualBoards/";
    private static final @NotNull String TAG = "tag_name";
    private static final @NotNull Duration TIMEOUT = Duration.ofSeconds(10L);
    private static final int HTTP_OK = 200;
    private static final int MAX_BODY_BYTES = 1024 * 1024;
    private static final @NotNull Pattern NOT_VERSION = Pattern.compile("[^0-9.]");
    private static final @NotNull Pattern SEPARATOR = Pattern.compile("\\.");

    private final @NotNull String currentVersion;

    public void check() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        HttpRequest request = HttpRequest.newBuilder(LATEST_RELEASE)
                .timeout(TIMEOUT)
                .header("User-Agent", USER_AGENT + currentVersion)
                .GET()
                .build();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).whenComplete((response, error) -> {
            try {
                if (error != null) {
                    Logger.warn("Не удалось проверить обновления: " + error.getMessage());
                    return;
                }
                report(response);
            } finally {
                client.shutdown();
            }
        });
    }

    private void report(@NotNull HttpResponse<InputStream> response) {
        String body;
        try (InputStream stream = response.body()) {
            if (response.statusCode() != HTTP_OK) {
                Logger.warn("Не удалось проверить обновления: GitHub ответил кодом " + response.statusCode());
                return;
            }
            byte[] bytes = stream.readNBytes(MAX_BODY_BYTES + 1);
            if (bytes.length > MAX_BODY_BYTES) {
                Logger.warn("Не удалось проверить обновления: ответ GitHub слишком большой");
                return;
            }
            body = new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            Logger.warn("Не удалось проверить обновления: " + exception.getMessage());
            return;
        }
        String latestVersion = parseTag(body);
        if (latestVersion == null) {
            Logger.warn("Не удалось проверить обновления: в ответе GitHub нет версии");
            return;
        }
        if (!isNewer(latestVersion, currentVersion)) {
            return;
        }
        Logger.warn("Доступна новая версия " + latestVersion + " (установлена " + currentVersion + "): "
                + DOWNLOAD_PAGE);
    }

    private static @Nullable String parseTag(@NotNull String body) {
        try {
            JsonElement tag = JsonParser.parseString(body).getAsJsonObject().get(TAG);
            return tag == null || tag.isJsonNull() ? null : tag.getAsString();
        } catch (JsonParseException | IllegalStateException | UnsupportedOperationException exception) {
            return null;
        }
    }

    static boolean isNewer(@NotNull String latest, @NotNull String current) {
        String[] latestParts = SEPARATOR.split(NOT_VERSION.matcher(latest).replaceAll(""));
        String[] currentParts = SEPARATOR.split(NOT_VERSION.matcher(current).replaceAll(""));
        for (int i = 0; i < Math.max(latestParts.length, currentParts.length); i++) {
            int difference = Integer.compare(part(latestParts, i), part(currentParts, i));
            if (difference != 0) {
                return difference > 0;
            }
        }
        return false;
    }

    private static int part(@NotNull String @NotNull [] parts, int index) {
        if (index >= parts.length || parts[index].isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[index]);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}
