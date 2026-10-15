package dev.pollinations.minecraft;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;

/** Pollinations for Minecraft servers. */
public final class PollinationsPlugin extends JavaPlugin {

    private static final String IMAGE_FOLDER = "images";

    private PollinationsClient client;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        PollinationsSettings settings = PollinationsSettings.from(getConfig());
        client = new PollinationsClient(settings, runnable -> Bukkit.getScheduler().runTaskAsynchronously(this, runnable));

        PollinationsCommand handler = new PollinationsCommand(this, client);
        PluginCommand command = getCommand("pollinations");
        if (command != null) {
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }
        if (settings.villagerChatEnabled()) {
            getServer().getPluginManager().registerEvents(new VillagerChatListener(this, client), this);
        }
        if (settings.apiKey().isEmpty()) {
            getLogger().warning("No API key configured, so requests will be rejected with 401. "
                    + "Get one at https://enter.pollinations.ai/keys, then run: /pollinations key <api-key>");
        }
        getLogger().info(() -> "Pollinations ready: text " + settings.textModel()
                + ", image " + settings.imageModel());
    }

    @Override
    public void onDisable() {
        if (client != null) {
            client.close();
        }
    }

    /** Re-reads config.yml and rebuilds the HTTP client. */
    public void reloadSettings() {
        reloadConfig();
        client.applySettings(PollinationsSettings.from(getConfig()));
    }

    /** Hands the image to a player as a filled map. Server thread only. */
    public void giveMap(Player player, BufferedImage image, String prompt) {
        World world = player.getWorld();
        MapView view = Bukkit.createMap(world);
        view.getRenderers().forEach(view::removeRenderer);
        view.addRenderer(new MapImageRenderer(image));
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);

        ItemStack map = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) map.getItemMeta();
        meta.setMapView(view);
        map.setItemMeta(meta);

        var leftovers = player.getInventory().addItem(map);
        leftovers.values().forEach(drop -> world.dropItemNaturally(player.getLocation(), drop));

        String saved = saveImage(image, prompt);
        player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .deserialize("<gold>[Pollinations] <white>Map ready <gray>(also saved as <white>" + saved + "<gray>)"));
    }

    /** Writes the PNG next to the config, so console runs keep their result. */
    public String saveImage(BufferedImage image, String prompt) {
        File folder = new File(getDataFolder(), IMAGE_FOLDER);
        if (!folder.exists() && !folder.mkdirs()) {
            getLogger().warning("Could not create " + folder);
            return "(not saved)";
        }
        String name = "poll-" + Instant.now().toString().replaceAll("[:.]", "-")
                + "-" + slug(prompt) + ".png";
        File target = new File(folder, name);
        try {
            ImageIO.write(image, "png", target);
            return IMAGE_FOLDER + "/" + name;
        } catch (IOException error) {
            getLogger().warning("Could not save the image: " + error.getMessage());
            return "(not saved)";
        }
    }

    private static String slug(String prompt) {
        String cleaned = prompt.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return cleaned.isEmpty() ? "image" : cleaned.substring(0, Math.min(40, cleaned.length()));
    }
}
