package dev.pollinations.minecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The map renderer has to survive any image a model returns. */
class MapPaletteTest {

    private static BufferedImage solid(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    @Test
    void alwaysReturnsOnePixelPerMapPixel() {
        byte[] pixels = MapPalette.toMapPixels(solid(512, 512, Color.WHITE));

        assertEquals(MapPalette.MAP_SIZE * MapPalette.MAP_SIZE, pixels.length);
    }

    @Test
    void scalesDownALargerImage() {
        // Half red, half blue, at four times the map size.
        BufferedImage image = solid(512, 512, Color.RED);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(256, 0, 256, 512);
        graphics.dispose();

        byte[] pixels = MapPalette.toMapPixels(image);
        Set<Byte> leftHalf = new HashSet<>();
        Set<Byte> rightHalf = new HashSet<>();
        for (int y = 0; y < MapPalette.MAP_SIZE; y++) {
            for (int x = 0; x < MapPalette.MAP_SIZE; x++) {
                (x < MapPalette.MAP_SIZE / 2 ? leftHalf : rightHalf).add(pixels[y * MapPalette.MAP_SIZE + x]);
            }
        }

        assertTrue(leftHalf.size() > 0 && rightHalf.size() > 0);
        // Red and blue must not collapse into the same palette entry.
        assertTrue(java.util.Collections.disjoint(leftHalf, rightHalf),
                "the two halves should map to different palette entries");
    }

    @Test
    void mapsPlainColoursToTheMatchingPaletteEntry() {
        // Palette entry 28 is red (153,51,51): base index 28, shade 2.
        byte red = MapPalette.toMapPixels(solid(256, 256, new Color(153, 51, 51)))[0];
        assertEquals(28 * 4 + 2, Byte.toUnsignedInt(red), "a red image should use the red palette entry");

        // Entry 29 is black (25,25,25).
        byte black = MapPalette.toMapPixels(solid(256, 256, new Color(25, 25, 25)))[0];
        assertEquals(29 * 4 + 2, Byte.toUnsignedInt(black), "a black image should use the black palette entry");
    }

    @Test
    void neverUsesTheTransparentSlot() {
        // Palette byte 0 paints nothing, which would leave holes in the map.
        byte[] pixels = MapPalette.toMapPixels(solid(256, 256, new Color(1, 2, 3)));

        for (byte pixel : pixels) {
            assertNotEquals(0, pixel);
        }
    }
}
