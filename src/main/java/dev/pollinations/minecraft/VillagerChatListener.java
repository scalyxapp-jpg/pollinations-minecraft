package dev.pollinations.minecraft;

import dev.pollinations.minecraft.PollinationsClient.Message;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Villagers with a custom name answer players who speak near them.
 *
 * <p>Chat arrives off the server thread, so the villager is looked up on the
 * server thread first and the request itself runs asynchronously.
 */
public final class VillagerChatListener implements Listener {

    private static final MiniMessage MESSAGES = MiniMessage.miniMessage();

    private final PollinationsPlugin plugin;
    private final PollinationsClient client;
    private final Map<UUID, Long> lastReply = new HashMap<>();
    private final Map<String, Deque<Message>> history = new HashMap<>();

    public VillagerChatListener(PollinationsPlugin plugin, PollinationsClient client) {
        this.plugin = plugin;
        this.client = client;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!event.getPlayer().hasPermission("pollinations.villager")) {
            return;
        }
        Player player = event.getPlayer();
        String asked = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (asked.isBlank() || asked.startsWith("/")) {
            return;
        }
        // Entity access belongs on the server thread.
        plugin.getServer().getScheduler().runTask(plugin, () -> replyIfNearby(player, asked));
    }

    private void replyIfNearby(Player player, String asked) {
        PollinationsSettings settings = client.settings();
        if (!settings.villagerChatEnabled()) {
            return;
        }
        Villager villager = nearestNamedVillager(player, settings.villagerRadius());
        if (villager == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long readyAt = lastReply.getOrDefault(player.getUniqueId(), 0L);
        if (now < readyAt) {
            long seconds = (readyAt - now + 999) / 1000;
            player.sendMessage(MESSAGES.deserialize("<gray>" + villagerName(villager)
                    + " is still thinking (" + seconds + "s)."));
            return;
        }
        lastReply.put(player.getUniqueId(), now + settings.villagerCooldownSeconds() * 1000L);

        String name = villagerName(villager);
        List<Message> conversation = conversationWith(player, villager, settings, name, asked);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> client.ask(conversation)
                .thenAccept(answer -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    remember(player, villager, asked, answer, settings);
                    player.sendMessage(MESSAGES.deserialize("<green>" + name + "<gray>: <white>"
                            + answer.replace("<", "\\<")));
                    player.playSound(Sound.sound(org.bukkit.Sound.ENTITY_VILLAGER_YES,
                            Sound.Source.NEUTRAL, 1f, 1f));
                }))
                .exceptionally(failure -> {
                    Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                    plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage(
                            MESSAGES.deserialize("<red>" + cause.getMessage().replace("<", "\\<"))));
                    return null;
                }));
    }

    private List<Message> conversationWith(Player player, Villager villager, PollinationsSettings settings,
            String name, String asked) {
        List<Message> conversation = new ArrayList<>();
        conversation.add(new Message("system",
                settings.villagerPrompt().replace("%name%", name)));
        Deque<Message> earlier = history.get(historyKey(player, villager));
        if (earlier != null) {
            conversation.addAll(earlier);
        }
        conversation.add(new Message("user", player.getName() + ": " + asked));
        return conversation;
    }

    private void remember(Player player, Villager villager, String asked, String answer,
            PollinationsSettings settings) {
        if (settings.historyTurns() == 0) {
            return;
        }
        Deque<Message> earlier = history.computeIfAbsent(historyKey(player, villager),
                key -> new ArrayDeque<>());
        earlier.addLast(new Message("user", asked));
        earlier.addLast(new Message("assistant", answer));
        while (earlier.size() > settings.historyTurns() * 2) {
            earlier.removeFirst();
        }
    }

    private Villager nearestNamedVillager(Player player, double radius) {
        return player.getWorld().getNearbyEntities(player.getLocation(), radius, radius, radius).stream()
                .filter(Villager.class::isInstance)
                .map(Villager.class::cast)
                .filter(candidate -> nameOf(candidate) != null)
                .min((left, right) -> Double.compare(left.getLocation().distanceSquared(player.getLocation()),
                        right.getLocation().distanceSquared(player.getLocation())))
                .orElse(null);
    }

    private static String nameOf(Villager villager) {
        Component custom = villager.customName();
        if (custom == null) {
            return null;
        }
        String plain = PlainTextComponentSerializer.plainText().serialize(custom).trim();
        return plain.isEmpty() ? null : plain;
    }

    private static String villagerName(Villager villager) {
        String name = nameOf(villager);
        return name == null ? "Villager" : name;
    }

    private static String historyKey(Player player, Villager villager) {
        return player.getUniqueId() + ":" + villager.getUniqueId();
    }
}
