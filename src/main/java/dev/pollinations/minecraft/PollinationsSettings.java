package dev.pollinations.minecraft;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Everything the plugin reads from config.yml, resolved once so the rest of the
 * code never touches raw config paths.
 */
public record PollinationsSettings(
        String apiKey,
        String textModel,
        String imageModel,
        int imageSize,
        int requestTimeoutSeconds,
        boolean villagerChatEnabled,
        int villagerCooldownSeconds,
        int villagerRadius,
        String villagerPrompt,
        int historyTurns) {

    static PollinationsSettings from(FileConfiguration config) {
        return new PollinationsSettings(
                trimmed(config.getString("api-key", "")),
                trimmed(config.getString("text-model", "openai/gpt-6-sol")),
                trimmed(config.getString("image-model", "black-forest-labs/flux.2-klein-4b")),
                // The API rejects anything under 256 px per side.
                clamp(config.getInt("image-size", 512), 256, 1280),
                clamp(config.getInt("request-timeout-seconds", 60), 5, 300),
                config.getBoolean("villager-chat.enabled", true),
                clamp(config.getInt("villager-chat.cooldown-seconds", 5), 0, 3600),
                clamp(config.getInt("villager-chat.radius", 6), 1, 32),
                config.getString("villager-chat.prompt",
                        "You are %name%, a villager in a Minecraft world. Answer the player in one short sentence."),
                clamp(config.getInt("villager-chat.history-turns", 4), 0, 20));
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
