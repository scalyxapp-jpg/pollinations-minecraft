package dev.pollinations.minecraft;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.imageio.ImageIO;

/**
 * Talks to the Pollinations API. Every call is asynchronous and runs off the
 * server thread; nothing here touches the world.
 */
public final class PollinationsClient implements AutoCloseable {

    /** Overridable so a test server can point at a local stub. */
    private static final String BASE_URL =
            System.getProperty("pollinations.base-url", "https://gen.pollinations.ai");

    private static final String TEXT_PATH = "/v1/chat/completions";
    private static final String IMAGE_PATH = "/image/";

    private final Gson gson = new Gson();
    private final Executor executor;
    private volatile PollinationsSettings settings;
    private HttpClient http;

    public PollinationsClient(PollinationsSettings settings, Executor executor) {
        this.executor = executor;
        applySettings(settings);
    }

    public PollinationsSettings settings() {
        return settings;
    }

    /** Rebuilds the HTTP client so a reloaded timeout takes effect. */
    public void applySettings(PollinationsSettings updated) {
        this.settings = updated;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(30, updated.requestTimeoutSeconds())))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** One turn of a conversation. */
    public record Message(String role, String content) {
    }

    /** A failure the player should see, with no stack trace or internals. */
    public static final class PollinationsException extends RuntimeException {
        public PollinationsException(String message) {
            super(message);
        }
    }

    /** Sends a conversation to the configured text model. */
    public CompletableFuture<String> ask(List<Message> messages) {
        PollinationsSettings current = settings;
        JsonObject body = new JsonObject();
        body.addProperty("model", current.textModel());
        JsonArray array = new JsonArray();
        for (Message message : messages) {
            JsonObject entry = new JsonObject();
            entry.addProperty("role", message.role());
            entry.addProperty("content", message.content());
            array.add(entry);
        }
        body.add("messages", array);

        HttpRequest request = request(TEXT_PATH)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body), StandardCharsets.UTF_8))
                .build();

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    if (response.statusCode() / 100 != 2) {
                        throw new PollinationsException(describeError(response.statusCode(), response.body()));
                    }
                    return readMessage(response.body());
                });
    }

    /** Renders a prompt to a square image at the configured size. */
    public CompletableFuture<BufferedImage> image(String prompt) {
        PollinationsSettings current = settings;
        int size = current.imageSize();
        String query = "?model=" + encode(current.imageModel())
                + "&width=" + size
                + "&height=" + size
                + "&nologo=true";

        HttpRequest request = request(IMAGE_PATH + encode(prompt) + query).GET().build();

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> {
                    if (response.statusCode() / 100 != 2) {
                        throw new PollinationsException(
                                describeError(response.statusCode(), new String(response.body(), StandardCharsets.UTF_8)));
                    }
                    try {
                        BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.body()));
                        if (image == null) {
                            throw new PollinationsException("The model did not return an image");
                        }
                        return image;
                    } catch (java.io.IOException error) {
                        throw new PollinationsException("Could not read the image: " + error.getMessage());
                    }
                });
    }

    /** Lists the live model ids of one kind, so nobody has to guess an id. */
    public CompletableFuture<List<String>> listModels(boolean images) {
        HttpRequest request = request(images ? "/image/models" : "/text/models").GET().build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    if (response.statusCode() / 100 != 2) {
                        throw new PollinationsException(describeError(response.statusCode(), response.body()));
                    }
                    JsonArray models = gson.fromJson(response.body(), JsonArray.class);
                    if (models == null) {
                        throw new PollinationsException("The model list was empty");
                    }
                    List<String> names = new ArrayList<>();
                    for (JsonElement model : models) {
                        if (model.isJsonObject() && model.getAsJsonObject().has("name")) {
                            names.add(model.getAsJsonObject().get("name").getAsString());
                        }
                    }
                    return names;
                });
    }

    @Override
    public void close() {
        // HttpClient has no close() before Java 21; nothing to release.
    }

    private HttpRequest.Builder request(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("accept", "*/*")
                .timeout(Duration.ofSeconds(settings.requestTimeoutSeconds()));
        String key = settings.apiKey();
        if (!key.isEmpty()) {
            builder.header("authorization", "Bearer " + key);
        }
        return builder;
    }

    private String readMessage(String body) {
        JsonObject root = gson.fromJson(body, JsonObject.class);
        JsonArray choices = root == null ? null : root.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new PollinationsException("The model returned no answer");
        }
        JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
        String content = message == null ? null : message.get("content").getAsString();
        if (content == null || content.isBlank()) {
            throw new PollinationsException("The model returned an empty answer");
        }
        return content.trim();
    }

    /**
     * Turns an error response into one line. The API reports limits and missing
     * models as {@code {"error":{"message":"..."}}}, and a plugin should pass
     * that on instead of a stack trace.
     */
    private String describeError(int status, String body) {
        try {
            JsonObject root = gson.fromJson(body, JsonObject.class);
            if (root != null && root.has("error")) {
                JsonElement error = root.get("error");
                if (error.isJsonObject() && error.getAsJsonObject().has("message")) {
                    return error.getAsJsonObject().get("message").getAsString();
                }
                if (error.isJsonPrimitive()) {
                    return error.getAsString();
                }
            }
        } catch (RuntimeException ignored) {
            // fall through to the raw status
        }
        return "Pollinations answered HTTP " + status;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
