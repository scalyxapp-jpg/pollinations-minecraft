package dev.pollinations.minecraft;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Converts an image into Minecraft map pixels.
 *
 * <p>A map pixel is a byte: the base colour index times four, plus one of four
 * brightness shades. The base colours and the shade multipliers mirror the
 * client's own map palette, so the result looks the way a painted map does.
 */
public final class MapPalette {

    /** A map is 128x128 pixels, whatever the source image size. */
    public static final int MAP_SIZE = 128;

    /** The four brightness multipliers the client applies, over 255. */
    private static final int[] SHADE_FACTORS = {180, 220, 255, 135};

    private static final int[][] BASE_COLORS = {
            {0, 0, 0}, // 0: unused, the client draws nothing here
            {127, 178, 56}, {247, 233, 163}, {199, 199, 199}, {255, 0, 0},
            {160, 160, 255}, {167, 167, 167}, {0, 124, 0}, {255, 255, 255},
            {164, 168, 184}, {151, 109, 77}, {112, 112, 112}, {64, 64, 255},
            {143, 119, 72}, {255, 252, 245}, {216, 127, 51}, {178, 76, 216},
            {102, 153, 216}, {229, 229, 51}, {127, 204, 25}, {242, 127, 165},
            {76, 76, 76}, {153, 153, 153}, {76, 127, 153}, {127, 63, 178},
            {51, 76, 178}, {102, 76, 51}, {102, 127, 51}, {153, 51, 51},
            {25, 25, 25}, {250, 238, 77}, {92, 219, 213}, {74, 128, 255},
            {0, 217, 58}, {129, 86, 49}, {112, 2, 0}, {209, 177, 161},
            {159, 82, 36}, {149, 87, 108}, {112, 108, 138}, {186, 133, 35},
            {103, 117, 53}, {160, 77, 78}, {57, 41, 35}, {135, 107, 98},
            {87, 92, 92}, {122, 73, 88}, {76, 62, 92}, {76, 50, 35},
            {76, 82, 42}, {142, 60, 46}, {37, 22, 16}, {189, 48, 49},
            {148, 63, 97}, {92, 25, 29}, {22, 126, 134}, {58, 142, 140},
            {86, 44, 62}, {20, 180, 133}, {100, 100, 100}, {216, 175, 147},
            {168, 168, 129}, {72, 72, 72},
    };

    /** Every reachable map byte, with the colour the client paints for it. */
    private static final byte[] PALETTE_BYTES;
    private static final int[] PALETTE_RGB;

    static {
        // Base colour 0 is the client's "paint nothing" slot, so it is skipped.
        int entries = (BASE_COLORS.length - 1) * SHADE_FACTORS.length;
        PALETTE_BYTES = new byte[entries];
        PALETTE_RGB = new int[entries];
        int index = 0;
        for (int base = 1; base < BASE_COLORS.length; base++) {
            int[] color = BASE_COLORS[base];
            for (int shade = 0; shade < SHADE_FACTORS.length; shade++) {
                int factor = SHADE_FACTORS[shade];
                int r = color[0] * factor / 255;
                int g = color[1] * factor / 255;
                int b = color[2] * factor / 255;
                PALETTE_BYTES[index] = (byte) (base * 4 + shade);
                PALETTE_RGB[index] = (r << 16) | (g << 8) | b;
                index++;
            }
        }
    }

    private MapPalette() {
    }

    /** Scales the image to map size and returns one palette byte per pixel. */
    public static byte[] toMapPixels(BufferedImage source) {
        BufferedImage scaled = scale(source);
        byte[] pixels = new byte[MAP_SIZE * MAP_SIZE];
        for (int y = 0; y < MAP_SIZE; y++) {
            for (int x = 0; x < MAP_SIZE; x++) {
                pixels[y * MAP_SIZE + x] = nearest(scaled.getRGB(x, y));
            }
        }
        return pixels;
    }

    private static BufferedImage scale(BufferedImage source) {
        BufferedImage target = new BufferedImage(MAP_SIZE, MAP_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, MAP_SIZE, MAP_SIZE, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    /** Nearest palette entry by squared RGB distance. */
    private static byte nearest(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int best = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < PALETTE_RGB.length; i++) {
            int candidate = PALETTE_RGB[i];
            int dr = r - ((candidate >> 16) & 0xFF);
            int dg = g - ((candidate >> 8) & 0xFF);
            int db = b - (candidate & 0xFF);
            int distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return PALETTE_BYTES[best];
    }
}
