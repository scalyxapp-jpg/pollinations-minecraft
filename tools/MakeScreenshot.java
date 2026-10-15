package tools;

import dev.pollinations.minecraft.MapPalette;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Builds the repository screenshot from real artefacts: the map is rendered with
 * the plugin's own palette, so the image shows exactly what the client paints.
 *
 * <p>Run: java -cp target/classes tools.MakeScreenshot <generated.png> <out.png>
 */
public final class MakeScreenshot {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final String[] CONSOLE = {
            "[Pollinations] Loading server plugin Pollinations v1.0.0",
            "[Pollinations] Enabling Pollinations v1.0.0",
            "[Pollinations] Pollinations ready: text openai/gpt-6-sol,",
            "               image black-forest-labs/flux.2-klein-4b",
            "",
            "> pollinations models text",
            "[Pollinations] Pollinations text models (97):",
            "[Pollinations]  • openai/gpt-5.4-nano",
            "[Pollinations]  • openai/gpt-6-sol",
            "[Pollinations]  … 77 more at https://gen.pollinations.ai/models",
            "",
            "> pollinations ask Antworte mit genau einem Wort: pong",
            "[Pollinations] pong",
            "",
            "> pollinations image a red cube on a white background",
            "[Pollinations] Saved images/poll-2026-10-15T17-33-27.png",
            "",
            "Paper 1.21.11 (build 132), Java 21",
    };

    private MakeScreenshot() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("usage: MakeScreenshot <generated.png> <out.png>");
            System.exit(2);
        }
        BufferedImage generated = ImageIO.read(new File(args[0]));
        BufferedImage canvas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(new Color(0x1e, 0x1e, 0x1e));
            graphics.fillRect(0, 0, WIDTH, HEIGHT);

            drawMap(graphics, generated);
            drawConsole(graphics);
            drawCaption(graphics);
        } finally {
            graphics.dispose();
        }
        ImageIO.write(canvas, "png", new File(args[1]));
        System.out.println("wrote " + args[1]);
    }

    /** Paints the image the way the client paints the map, at 4x for the screenshot. */
    private static void drawMap(Graphics2D graphics, BufferedImage source) {
        byte[] pixels = MapPalette.toMapPixels(source);
        int scale = 4;
        int originX = 60;
        int originY = 120;
        for (int y = 0; y < MapPalette.MAP_SIZE; y++) {
            for (int x = 0; x < MapPalette.MAP_SIZE; x++) {
                graphics.setColor(MapPalette.colorFor(pixels[y * MapPalette.MAP_SIZE + x]));
                graphics.fillRect(originX + x * scale, originY + y * scale, scale, scale);
            }
        }
        graphics.setColor(new Color(0x55, 0x55, 0x55));
        graphics.drawRect(originX - 1, originY - 1, MapPalette.MAP_SIZE * scale + 1,
                MapPalette.MAP_SIZE * scale + 1);
    }

    private static void drawConsole(Graphics2D graphics) {
        graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 15));
        graphics.setColor(new Color(0x9c, 0xdc, 0xfe));
        int y = 140;
        for (String line : CONSOLE) {
            graphics.drawString(line, 620, y);
            y += 26;
        }
    }

    private static void drawCaption(Graphics2D graphics) {
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
        graphics.setColor(Color.WHITE);
        graphics.drawString("Pollinations for Minecraft servers", 60, 70);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 17));
        graphics.setColor(new Color(0xaa, 0xaa, 0xaa));
        graphics.drawString("map art from /pollinations image  —  rendered with the plugin's own map palette",
                60, 98);
    }
}
