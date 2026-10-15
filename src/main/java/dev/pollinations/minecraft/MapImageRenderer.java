package dev.pollinations.minecraft;

import java.awt.image.BufferedImage;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

/** Paints one generated image onto a map, once. */
public final class MapImageRenderer extends MapRenderer {

    private final byte[] pixels;
    private boolean painted;

    public MapImageRenderer(BufferedImage image) {
        // A framed image never changes, so it only has to be painted once.
        super(false);
        this.pixels = MapPalette.toMapPixels(image);
    }

    @Override
    public void render(MapView view, MapCanvas canvas, Player player) {
        if (painted) {
            return;
        }
        for (int y = 0; y < MapPalette.MAP_SIZE; y++) {
            for (int x = 0; x < MapPalette.MAP_SIZE; x++) {
                canvas.setPixel(x, y, pixels[y * MapPalette.MAP_SIZE + x]);
            }
        }
        painted = true;
    }
}
