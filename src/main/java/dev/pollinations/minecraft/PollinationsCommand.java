package dev.pollinations.minecraft;

import dev.pollinations.minecraft.PollinationsClient.Message;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

/** Handles {@code /pollinations}. */
public final class PollinationsCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MESSAGES = MiniMessage.miniMessage();
    private static final List<String> SUBCOMMANDS = List.of("ask", "image", "models", "reload", "key");

    private final PollinationsPlugin plugin;
    private final PollinationsClient client;

    public PollinationsCommand(PollinationsPlugin plugin, PollinationsClient client) {
        this.plugin = plugin;
        this.client = client;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            usage(sender, label);
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "ask" -> ask(sender, label, join(args, 1), "pollinations.ask");
            case "image" -> image(sender, label, join(args, 1), "pollinations.image");
            case "models" -> models(sender, args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "text",
                    "pollinations.models");
            case "reload" -> reload(sender);
            case "key" -> key(sender, args);
            default -> usage(sender, label);
        }
        return true;
    }

    private void ask(CommandSender sender, String label, String prompt, String permission) {
        if (!sender.hasPermission(permission)) {
            error(sender, "You are not allowed to do that.");
            return;
        }
        if (prompt.isBlank()) {
            error(sender, "Usage: /" + label + " ask <question>");
            return;
        }
        sender.sendMessage(MESSAGES.deserialize("<gray>Asking <white>" + escape(client.settings().textModel()) + "<gray>…"));
        inBackground(() -> client.ask(List.of(
                new Message("system", "You are a helpful assistant answering a Minecraft player. Be brief."),
                new Message("user", prompt)))
                .thenAccept(answer -> sync(() -> reply(sender,
                        "<gold>[Pollinations] <white>" + escape(answer))))
                .exceptionally(failure -> report(sender, failure)));
    }

    private void image(CommandSender sender, String label, String prompt, String permission) {
        if (!sender.hasPermission(permission)) {
            error(sender, "You are not allowed to do that.");
            return;
        }
        if (prompt.isBlank()) {
            error(sender, "Usage: /" + label + " image <prompt>");
            return;
        }
        sender.sendMessage(MESSAGES.deserialize("<gray>Drawing <white>" + escape(prompt) + "<gray>…"));
        inBackground(() -> client.image(prompt)
                .thenAccept(image -> {
                    if (sender instanceof org.bukkit.entity.Player player) {
                        // World access has to happen on the server thread.
                        sync(() -> plugin.giveMap(player, image, prompt));
                    } else {
                        // The console has no inventory, so the file is the whole result.
                        sync(() -> {
                            String saved = plugin.saveImage(image, prompt);
                            reply(sender, "<green>Saved <white>" + escape(saved));
                        });
                    }
                })
                .exceptionally(failure -> report(sender, failure)));
    }

    private void models(CommandSender sender, String kind, String permission) {
        if (!sender.hasPermission(permission)) {
            error(sender, "You are not allowed to do that.");
            return;
        }
        boolean images = kind.startsWith("image");
        inBackground(() -> client.listModels(images)
                .thenAccept(names -> sync(() -> {
                    reply(sender, "<gold>Pollinations " + (images ? "image" : "text")
                            + " models <gray>(" + names.size() + ")<white>:");
                    int shown = Math.min(names.size(), 20);
                    for (int i = 0; i < shown; i++) {
                        reply(sender, "<gray> • <white>" + escape(names.get(i)));
                    }
                    if (names.size() > shown) {
                        reply(sender, "<gray> … " + (names.size() - shown)
                                + " more at <white>https://gen.pollinations.ai/models");
                    }
                }))
                .exceptionally(failure -> report(sender, failure)));
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("pollinations.reload")) {
            error(sender, "You are not allowed to do that.");
            return;
        }
        plugin.reloadSettings();
        sender.sendMessage(MESSAGES.deserialize("<green>Pollinations config reloaded."));
    }

    private void key(CommandSender sender, String[] args) {
        if (!sender.hasPermission("pollinations.reload")) {
            error(sender, "You are not allowed to do that.");
            return;
        }
        if (args.length < 2) {
            error(sender, "Usage: /pollinations key <api-key>  (blank clears it)");
            return;
        }
        String key = args[1].equals("-") ? "" : args[1];
        plugin.getConfig().set("api-key", key);
        plugin.saveConfig();
        plugin.reloadSettings();
        sender.sendMessage(MESSAGES.deserialize(key.isEmpty()
                ? "<green>API key cleared. Requests now use the anonymous tier."
                : "<green>API key saved."));
    }

    /** Shows one line for a failed request, never a stack trace. */
    private Void report(CommandSender sender, Throwable failure) {
        Throwable cause = failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null
                ? failure.getCause()
                : failure;
        sync(() -> error(sender, cause.getMessage() == null ? cause.toString() : cause.getMessage()));
        return null;
    }

    private void usage(CommandSender sender, String label) {
        sender.sendMessage(MESSAGES.deserialize("<gold>Pollinations <gray>— /" + label + " <ask|image|models|reload|key>"));
        sender.sendMessage(MESSAGES.deserialize("<gray>  ask <text> <dark_gray>— answer from <white>"
                + escape(client.settings().textModel())));
        sender.sendMessage(MESSAGES.deserialize("<gray>  image <prompt> <dark_gray>— image as a filled map, from <white>"
                + escape(client.settings().imageModel())));
        sender.sendMessage(MESSAGES.deserialize("<gray>  models [text|image] <dark_gray>— live model ids"));
        sender.sendMessage(MESSAGES.deserialize("<gray>  reload <dark_gray>— reread config.yml"));
        sender.sendMessage(MESSAGES.deserialize("<gray>  key <api-key> <dark_gray>— store a key without editing files"));
    }

    private void error(CommandSender sender, String message) {
        sender.sendMessage(MESSAGES.deserialize("<red>" + escape(message)));
    }

    /** Console and RCON senders only see a command's own output, so a result
     *  that arrives later is also written to the log. */
    private void reply(CommandSender sender, String text) {
        sender.sendMessage(MESSAGES.deserialize(text));
        if (!(sender instanceof org.bukkit.entity.Player)) {
            String forLog = PlainTextComponentSerializer.plainText().serialize(MESSAGES.deserialize(text))
                    .replace("[Pollinations] ", "");
            plugin.getLogger().info(forLog);
        }
    }

    private void inBackground(Runnable work) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, work);
    }

    private void sync(Runnable work) {
        plugin.getServer().getScheduler().runTask(plugin, work);
    }

    private static String join(String[] args, int from) {
        return from >= args.length ? "" : String.join(" ", java.util.Arrays.copyOfRange(args, from, args.length));
    }

    /** MiniMessage would otherwise read a player's prompt as formatting. */
    private static String escape(String text) {
        return text.replace("<", "\\<");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> matches = new ArrayList<>();
            for (String candidate : SUBCOMMANDS) {
                if (candidate.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    matches.add(candidate);
                }
            }
            return matches;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("models")) {
            return List.of("text", "image");
        }
        return List.of();
    }
}
